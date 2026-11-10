package com.breachprotocol.career;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Your phone answers to your implant. Every phone has an owner (its ID): the first person to carry it.
 * Recall Phone brings yours back whatever happened to it: lying on the ground anywhere (it lifts and
 * flies to you through walls, from any distance or dimension), in another player's pocket (it tears
 * itself loose and flies to you), or out of reach entirely (in a chest, burnt, despawned: a new one is
 * issued to you, the same as the last one you held, and the lost copy is wiped wherever it turns up).
 */
@EventBusSubscriber(modid = BreachProtocol.MODID)
public final class PhoneRecall {
    private PhoneRecall() {}

    private static final String OWNER = "bp_owner", OWNER_NAME = "bp_owner_n", GEN = "bp_gen",
            WHERE = "bp_phone_at", DIM = "bp_phone_dim", CUR_GEN = "bp_phone_gen", COPY = "bp_phone_copy";

    private static CompoundTag data(ItemStack s) { return s.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag(); }

    private static int gen(ItemStack s) { return data(s).getInt(GEN); }

    private static int curGen(ServerPlayer p) { return p.getPersistentData().getInt(CUR_GEN); }

    /** An old copy of a phone that's since been replaced: its owner's later phone is the real one. */
    private static boolean stale(MinecraftServer srv, ItemStack s) {
        UUID o = owner(s);
        if (o == null) return false;
        ServerPlayer ow = srv.getPlayerList().getPlayer(o);
        return ow != null && gen(s) < curGen(ow);
    }

    private static boolean phone(ItemStack s) { return !s.isEmpty() && s.is(ModItems.PHONE.get()); }

    private static UUID owner(ItemStack s) {
        CompoundTag t = s.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return t.hasUUID(OWNER) ? t.getUUID(OWNER) : null;
    }

    /**
     * Once a second: a phone with no owner becomes yours (its ID is you); yours is remembered as it is
     * (so a lost one can be replaced exactly); a replaced copy, in anyone's hands, goes dead.
     */
    @SubscribeEvent
    public static void mark(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p.tickCount % 20 != 7) return;
        UUID me = p.getUUID();
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!phone(s)) continue;
            UUID o = owner(s);
            if (o == null) {
                int g = curGen(p);
                String n = p.getGameProfile().getName();
                CustomData.update(DataComponents.CUSTOM_DATA, s, t -> { t.putUUID(OWNER, me); t.putString(OWNER_NAME, n); t.putInt(GEN, g); });
            } else if (stale(p.server, s)) {
                inv.setItem(i, ItemStack.EMPTY);
                p.displayClientMessage(Component.literal("The phone's screen goes black. It's been wiped by its owner.").withStyle(ChatFormatting.GRAY), true);
            } else if (o.equals(me)) {
                p.getPersistentData().put(COPY, s.save(p.registryAccess()));
            }
        }
    }

    /** A phone of yours lands somewhere: where, remembered (on you, so it outlives the server restarting). */
    @SubscribeEvent
    public static void dropped(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof ItemEntity it) || !phone(it.getItem())) return;
        UUID who = owner(it.getItem());
        if (who == null || !(event.getLevel() instanceof ServerLevel l)) return;
        if (stale(l.getServer(), it.getItem())) { event.setCanceled(true); return; } // a wiped copy: gone
        ServerPlayer p = l.getServer().getPlayerList().getPlayer(who);
        CompoundTag t = p != null ? p.getPersistentData() : null;
        if (t == null) { LOST.put(who, new Spot(l.dimension(), it.blockPosition())); return; }
        t.putLong(WHERE, it.blockPosition().asLong());
        t.putString(DIM, l.dimension().location().toString());
    }

    private record Spot(ResourceKey<Level> dim, BlockPos at) {}

    /** Phones dropped while their owner was offline (a death on log-out, say). */
    private static final Map<UUID, Spot> LOST = new HashMap<>();

    private record Flight(ResourceKey<Level> dim, UUID item, UUID to, long until) {}

    private static final Map<UUID, Flight> FLYING = new HashMap<>();

    public static boolean recall(ServerPlayer p) {
        for (ItemStack s : p.getInventory().items) if (phone(s) && p.getUUID().equals(owner(s))) { tell(p, "Your phone's already on you."); return false; }
        for (Flight f : FLYING.values()) if (f.to().equals(p.getUUID())) { tell(p, "It's already on its way."); return false; }
        MinecraftServer srv = p.server;
        // where it was last seen on the ground
        Spot spot = LOST.remove(p.getUUID());
        CompoundTag t = p.getPersistentData();
        if (spot == null && t.contains(WHERE)) {
            ResourceLocation id = ResourceLocation.tryParse(t.getString(DIM));
            if (id != null) spot = new Spot(ResourceKey.create(Registries.DIMENSION, id), BlockPos.of(t.getLong(WHERE)));
        }
        ItemEntity found = null;
        // loaded and nearby first, then the spot it was last seen (loading that bit of the world if need be)
        for (ServerLevel l : srv.getAllLevels()) {
            for (Entity e : l.getAllEntities()) if (e instanceof ItemEntity it && it.isAlive() && phone(it.getItem()) && p.getUUID().equals(owner(it.getItem()))) { found = it; break; }
            if (found != null) break;
        }
        if (found == null && spot != null) {
            ServerLevel l = srv.getLevel(spot.dim());
            if (l != null) {
                l.getChunk(spot.at().getX() >> 4, spot.at().getZ() >> 4); // wake it
                for (ItemEntity it : l.getEntitiesOfClass(ItemEntity.class, new AABB(spot.at()).inflate(24), it -> phone(it.getItem()) && p.getUUID().equals(owner(it.getItem()))))
                    { found = it; break; }
            }
        }
        if (found == null) {
            // in someone's pocket: it tears itself out of it
            for (ServerPlayer q : srv.getPlayerList().getPlayers()) {
                if (q == p) continue;
                var inv = q.getInventory();
                for (int i = 0; i < inv.getContainerSize() && found == null; i++) {
                    ItemStack s = inv.getItem(i);
                    if (!phone(s) || !p.getUUID().equals(owner(s)) || stale(srv, s)) continue;
                    inv.setItem(i, ItemStack.EMPTY);
                    found = new ItemEntity(q.serverLevel(), q.getX(), q.getEyeY() - 0.3, q.getZ(), s.copy());
                    q.serverLevel().addFreshEntity(found);
                    q.displayClientMessage(Component.literal("The phone in your pocket tears itself loose and flies off. It wasn't yours.").withStyle(ChatFormatting.GRAY), true);
                    q.serverLevel().playSound(null, q.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.8F, 0.6F);
                }
                if (found != null) break;
            }
        }
        if (found == null) {
            // in a chest, burnt, despawned, gone: a new one, exactly as yours was; the old copy is dead wherever it is
            int g = curGen(p) + 1;
            p.getPersistentData().putInt(CUR_GEN, g);
            ItemStack fresh = t.contains(COPY) ? ItemStack.parse(p.registryAccess(), t.getCompound(COPY)).orElse(new ItemStack(ModItems.PHONE.get())) : new ItemStack(ModItems.PHONE.get());
            if (!phone(fresh)) fresh = new ItemStack(ModItems.PHONE.get());
            String n = p.getGameProfile().getName();
            CustomData.update(DataComponents.CUSTOM_DATA, fresh, d -> { d.putUUID(OWNER, p.getUUID()); d.putString(OWNER_NAME, n); d.putInt(GEN, g); });
            t.remove(WHERE);
            if (!p.getInventory().add(fresh)) p.drop(fresh, false);
            p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.9F, 1.4F);
            p.serverLevel().sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 1, p.getZ(), 20, 0.3, 0.4, 0.3, 0.05);
            tell(p, "Your phone couldn't be reached, so it's been reissued to you. The lost one is wiped.");
            return true;
        }
        found.setNoGravity(true);
        found.setPickUpDelay(32767);
        found.setUnlimitedLifetime();
        FLYING.put(found.getUUID(), new Flight(found.level().dimension(), found.getUUID(), p.getUUID(), p.serverLevel().getGameTime() + 20 * 40));
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8F, 1.6F);
        tell(p, "Your phone is coming back to you.");
        return true;
    }

    private static void tell(ServerPlayer p, String s) { p.displayClientMessage(Component.literal(s).withStyle(ChatFormatting.LIGHT_PURPLE), true); }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        if (FLYING.isEmpty()) return;
        MinecraftServer srv = event.getServer();
        Map<UUID, Flight> moved = new HashMap<>();
        for (Iterator<Map.Entry<UUID, Flight>> it = FLYING.entrySet().iterator(); it.hasNext(); ) {
            Flight f = it.next().getValue();
            ServerLevel l = srv.getLevel(f.dim());
            ServerPlayer p = srv.getPlayerList().getPlayer(f.to());
            Entity e = l == null ? null : l.getEntity(f.item());
            if (!(e instanceof ItemEntity item) || !item.isAlive()) { it.remove(); continue; }
            if (p == null || !p.isAlive() || l.getGameTime() > f.until()) { land(item); it.remove(); continue; }
            Vec3 to = p.getEyePosition().subtract(0, 0.5, 0);
            // another world, or a long way off: it crosses over and comes the rest of the way in sight
            if (item.level() != p.level() || item.distanceToSqr(to) > 48 * 48) {
                Vec3 from = item.level() == p.level() ? item.position() : to.add(p.getLookAngle().scale(-1));
                Vec3 dir = from.subtract(to);
                Vec3 at = to.add(dir.lengthSqr() < 1e-4 ? new Vec3(0, 6, 0) : dir.normalize().scale(32)).add(0, 4, 0);
                ItemEntity again = new ItemEntity(p.serverLevel(), at.x, at.y, at.z, item.getItem().copy());
                again.setNoGravity(true);
                again.setPickUpDelay(32767);
                again.setUnlimitedLifetime();
                l.sendParticles(ParticleTypes.REVERSE_PORTAL, item.getX(), item.getY() + 0.3, item.getZ(), 20, 0.2, 0.3, 0.2, 0.05);
                item.discard();
                p.serverLevel().addFreshEntity(again);
                it.remove();
                moved.put(again.getUUID(), new Flight(p.serverLevel().dimension(), again.getUUID(), f.to(), f.until()));
                continue;
            }
            Vec3 gap = to.subtract(item.position());
            double d = gap.length();
            if (d < 1.3) {
                // into your hand, or at your feet if your pockets are full
                ItemStack s = item.getItem().copy();
                if (p.getInventory().add(s)) item.discard();
                else { land(item); item.setPickUpDelay(0); }
                p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.6F, 1.3F);
                p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.6F);
                tell(p, "Your phone is back.");
                p.getPersistentData().remove(WHERE);
                it.remove();
                continue;
            }
            // through anything in the way, quicker as it closes in
            item.noPhysics = true;
            item.setDeltaMovement(gap.normalize().scale(Math.min(1.6, 0.35 + d * 0.12)));
            item.hurtMarked = true;
            l.sendParticles(ParticleTypes.ELECTRIC_SPARK, item.getX(), item.getY() + 0.25, item.getZ(), 2, 0.05, 0.05, 0.05, 0.01);
            if (l.getGameTime() % 4 == 0) l.sendParticles(ParticleTypes.END_ROD, item.getX(), item.getY() + 0.25, item.getZ(), 1, 0, 0, 0, 0);
        }
        FLYING.putAll(moved);
    }

    private static void land(ItemEntity item) {
        item.setNoGravity(false);
        item.noPhysics = false;
        item.setPickUpDelay(10);
    }
}
