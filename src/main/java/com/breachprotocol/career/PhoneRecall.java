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
 * Your phone answers to your implant. A phone you carry is marked as yours; wherever it ends up on the
 * ground (dropped, thrown, left where you died, after /kill), it's remembered, and the Recall Phone
 * power pulls it back: it lifts, and flies to you through walls, from any distance, from any dimension.
 */
@EventBusSubscriber(modid = BreachProtocol.MODID)
public final class PhoneRecall {
    private PhoneRecall() {}

    private static final String OWNER = "bp_owner", WHERE = "bp_phone_at", DIM = "bp_phone_dim";

    private static boolean phone(ItemStack s) { return !s.isEmpty() && s.is(ModItems.PHONE.get()); }

    private static UUID owner(ItemStack s) {
        CompoundTag t = s.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return t.hasUUID(OWNER) ? t.getUUID(OWNER) : null;
    }

    /** Phones in your inventory are yours (checked once a second). */
    @SubscribeEvent
    public static void mark(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p.tickCount % 20 != 7) return;
        UUID me = p.getUUID();
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (phone(s) && !me.equals(owner(s))) CustomData.update(DataComponents.CUSTOM_DATA, s, t -> t.putUUID(OWNER, me));
        }
    }

    /** A phone of yours lands somewhere: where, remembered (on you, so it outlives the server restarting). */
    @SubscribeEvent
    public static void dropped(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof ItemEntity it) || !phone(it.getItem())) return;
        UUID who = owner(it.getItem());
        if (who == null || !(event.getLevel() instanceof ServerLevel l)) return;
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
        for (ItemStack s : p.getInventory().items) if (phone(s)) { tell(p, "Your phone's already on you."); return false; }
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
            tell(p, "No signal. Your phone isn't lying anywhere it can be pulled from (in a chest, or someone else has it).");
            return false;
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
