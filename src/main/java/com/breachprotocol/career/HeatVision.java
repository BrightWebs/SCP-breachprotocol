package com.breachprotocol.career;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.network.DialoguePayload;
import com.breachprotocol.network.FlightPosePayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Heat vision: the Administrator skill's third mode. Picked at a strength, the way Manas Reborn's skills have
 * their stages; on until used again; your eyes burn its colour while it's on. Whatever you look at takes it.
 * <ul>
 * <li><b>5%</b>: a warm thread. It sets alight what it touches; it hurts, a little.</li>
 * <li><b>15%</b>: a burning beam. Real harm, further; the ground where it lands catches.</li>
 * <li><b>50%</b>: a lance. It goes straight through everything living in its line, each one struck with a boom.</li>
 * <li><b>80%</b>: it cuts. Through stone and wood, boiling water away, melting ice and snow, setting it all alight.</li>
 * <li><b>100%</b>: annihilation. A wide beam that bores a tunnel through anything breakable and blows apart where it ends.</li>
 * </ul>
 * Blocks are never cut on Foundation ground, nor in the Vault.
 */
@EventBusSubscriber(modid = BreachProtocol.MODID)
public final class HeatVision {
    private HeatVision() {}

    public static final int[] PERCENT = {5, 15, 50, 80, 100};
    private static final double[] RANGE = {16, 32, 48, 64, 96};
    private static final float[] DAMAGE = {3, 9, 18, 32, 90};
    private static final int[] BURN = {3, 5, 6, 8, 12};

    private static final Map<UUID, Integer> ON = new HashMap<>();
    private static final Map<UUID, Integer> LAST = new HashMap<>();
    private static final Map<UUID, Map<Integer, Long>> BOOMED = new HashMap<>();

    public static boolean on(Player p) { return ON.containsKey(p.getUUID()); }

    /** The skill used: on or off (at the strength last chosen). Crouching: the strengths, to choose from. */
    public static void pressed(ServerPlayer p, boolean crouching) {
        if (crouching || !on(p) && !LAST.containsKey(p.getUUID())) { pick(p); return; }
        if (on(p)) off(p);
        else start(p, LAST.get(p.getUUID()));
    }

    /** The strength picker on the player's screen. */
    private static void pick(ServerPlayer p) {
        CompoundTag t = new CompoundTag();
        t.putBoolean("heatPick", true);
        t.putInt("heatNow", ON.getOrDefault(p.getUUID(), -1));
        PacketDistributor.sendToPlayer(p, new DialoguePayload(t));
    }

    /** "on:N" (0..4) or "off", from the picker. */
    public static void action(ServerPlayer p, String a) {
        if (a.equals("off")) { off(p); return; }
        if (a.startsWith("on:")) try { start(p, Math.max(0, Math.min(4, Integer.parseInt(a.substring(3))))); } catch (NumberFormatException ignored) {}
    }

    private static void start(ServerPlayer p, int tier) {
        if (!Vault.chief(p)) return;
        ON.put(p.getUUID(), tier);
        LAST.put(p.getUUID(), tier);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(p, new FlightPosePayload(p.getId(), 20 + tier));
        p.displayClientMessage(Component.literal("HEAT VISION  ·  " + PERCENT[tier] + "%").withStyle(tier >= 3 ? ChatFormatting.RED : ChatFormatting.GOLD), true);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.6F, 0.6F + tier * 0.15F);
    }

    private static void off(ServerPlayer p) {
        if (ON.remove(p.getUUID()) == null) return;
        BOOMED.remove(p.getUUID());
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(p, new FlightPosePayload(p.getId(), 25));
        p.displayClientMessage(Component.literal("Heat vision off.").withStyle(ChatFormatting.GRAY), true);
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        if (ON.isEmpty()) return;
        MinecraftServer srv = event.getServer();
        for (UUID id : new ArrayList<>(ON.keySet())) {
            ServerPlayer p = srv.getPlayerList().getPlayer(id);
            if (p == null) { ON.remove(id); continue; }
            if (!p.isAlive() || p.isSpectator() || !Vault.chief(p)) { off(p); continue; }
            try { beam(p, ON.get(id)); } catch (Throwable t) { BreachProtocol.LOGGER.debug("Heat vision: {}", t.toString()); }
        }
        // players who come into view of someone already beaming see it too
        if (srv.getTickCount() % 40 == 0) for (UUID id : ON.keySet()) {
            ServerPlayer p = srv.getPlayerList().getPlayer(id);
            if (p != null) PacketDistributor.sendToPlayersTrackingEntityAndSelf(p, new FlightPosePayload(p.getId(), 20 + ON.get(id)));
        }
    }

    private static boolean mayCut(ServerLevel l, BlockPos at) {
        return l.dimension() != Vault.DIM && Sites.at(l, at) == null;
    }

    private static void beam(ServerPlayer p, int tier) {
        ServerLevel l = p.serverLevel();
        long now = l.getGameTime();
        Vec3 from = p.getEyePosition(), dir = p.getViewVector(1F);
        double range = RANGE[tier];
        boolean cuts = tier >= 3, pierces = tier >= 2;
        // where it stops: the first solid block (below 80%); at 80% and up it goes on through
        double end = range;
        BlockPos stop = null;
        for (double d = 0.5; d <= range; d += 0.5) {
            BlockPos bp = BlockPos.containing(from.add(dir.scale(d)));
            BlockState st = l.getBlockState(bp);
            if (!st.isAir() && !st.getCollisionShape(l, bp).isEmpty() || st.getFluidState().is(Fluids.WATER) && tier < 3) {
                if (!cuts) { end = d; stop = bp; break; }
            }
        }
        Vec3 to = from.add(dir.scale(end));
        // the living in its line
        double width = 0.3 + tier * 0.25;
        AABB box = new AABB(from, to).inflate(width + 1);
        List<LivingEntity> hit = new ArrayList<>();
        for (Entity e : l.getEntities(p, box, e -> e instanceof LivingEntity le && le.isAlive() && !le.isSpectator())) {
            LivingEntity le = (LivingEntity) e;
            if (Targets.yours(p, le) || le instanceof Player q && !Targets.hostile(p, le)) continue;
            AABB bb = le.getBoundingBox().inflate(width);
            if (bb.contains(from) || bb.clip(from, to).isPresent()) hit.add(le);
        }
        hit.sort((a, b) -> Double.compare(a.distanceToSqr(p), b.distanceToSqr(p)));
        if (!pierces && !hit.isEmpty()) {
            LivingEntity first = hit.get(0);
            hit = List.of(first);
            to = first.position().add(0, first.getBbHeight() / 2, 0);
        }
        boolean strike = now % 4 == 0;
        Map<Integer, Long> boomed = BOOMED.computeIfAbsent(p.getUUID(), k -> new HashMap<>());
        for (LivingEntity v : hit) {
            v.igniteForSeconds(BURN[tier]);
            if (!strike) continue;
            v.invulnerableTime = 0;
            if (tier == 4) Toggles.shyBlow(p, v, DAMAGE[tier], 0.8, 200);
            else PowerKills.hurt(p, v, PowerKills.magic(p), DAMAGE[tier]);
            if (tier >= 2 && now - boomed.getOrDefault(v.getId(), -100L) >= 10) {
                boomed.put(v.getId(), now);
                Vec3 c = v.position().add(0, v.getBbHeight() / 2, 0);
                l.sendParticles(tier == 4 ? ParticleTypes.EXPLOSION_EMITTER : ParticleTypes.EXPLOSION, c.x, c.y, c.z, 1, 0, 0, 0, 0);
                l.playSound(null, c.x, c.y, c.z, tier >= 3 ? SoundEvents.WARDEN_SONIC_BOOM : SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.8F + tier * 0.3F, 1.2F - tier * 0.1F);
            }
        }
        if (now % 200 == 0) boomed.clear();
        // what it does to the world
        if (tier == 1 && stop != null && now % 10 == 0) ignite(l, stop, dir);
        if (tier == 2 && stop != null && now % 6 == 0) ignite(l, stop, dir);
        if (tier >= 3) cut(p, l, from, dir, range, tier, now);
        // its sound, and a spray where it lands
        if (now % 8 == 0) l.playSound(null, to.x, to.y, to.z, SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 0.8F + tier * 0.4F, 1.4F - tier * 0.15F);
        l.sendParticles(tier >= 3 ? ParticleTypes.LAVA : ParticleTypes.SMALL_FLAME, to.x, to.y, to.z, 1 + tier, 0.15, 0.15, 0.15, 0.02);
        l.sendParticles(new DustParticleOptions(new Vector3f(1F, 0.15F, 0.05F), 1.2F + tier * 0.4F), to.x, to.y, to.z, 3, 0.1, 0.1, 0.1, 0);
    }

    private static void ignite(ServerLevel l, BlockPos at, Vec3 dir) {
        if (!mayCut(l, at)) return;
        BlockPos front = BlockPos.containing(Vec3.atCenterOf(at).subtract(dir));
        if (l.getBlockState(front).isAir() && BaseFireBlock.canBePlacedAt(l, front, net.minecraft.core.Direction.UP))
            l.setBlockAndUpdate(front, BaseFireBlock.getState(l, front));
    }

    /**
     * 80%: through stone and wood (a block a moment), water boiled off, ice and snow melted, what burns set alight.
     * 100%: a tunnel through anything that can be broken, and where the beam ends, it blows apart.
     */
    private static void cut(ServerPlayer p, ServerLevel l, Vec3 from, Vec3 dir, double range, int tier, long now) {
        int budget = tier == 4 ? 12 : 2;
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        if (side.lengthSqr() < 1e-4) side = new Vec3(1, 0, 0);
        side = side.normalize();
        Vec3 up = dir.cross(side).normalize();
        BlockPos last = null;
        for (double d = 1.0; d <= range && budget > 0; d += 0.5) {
            Vec3 c = from.add(dir.scale(d));
            List<BlockPos> ring = new ArrayList<>();
            ring.add(BlockPos.containing(c));
            if (tier == 4) for (int a = -1; a <= 1; a++) for (int b = -1; b <= 1; b++) if (a != 0 || b != 0) ring.add(BlockPos.containing(c.add(side.scale(a)).add(up.scale(b))));
            for (BlockPos bp : ring) {
                if (budget <= 0) break;
                if (bp.equals(last)) continue;
                BlockState st = l.getBlockState(bp);
                if (st.isAir() || !mayCut(l, bp)) continue;
                if (st.getFluidState().is(Fluids.WATER) && st.getBlock() == Blocks.WATER) {
                    l.setBlockAndUpdate(bp, Blocks.AIR.defaultBlockState());
                    l.sendParticles(ParticleTypes.CLOUD, bp.getX() + 0.5, bp.getY() + 0.5, bp.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0.02);
                    l.playSound(null, bp, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5F, 1.4F);
                    budget--;
                    continue;
                }
                if (st.is(BlockTags.ICE) || st.is(Blocks.SNOW) || st.is(Blocks.SNOW_BLOCK) || st.is(Blocks.POWDER_SNOW)) {
                    l.setBlockAndUpdate(bp, st.is(Blocks.ICE) ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState());
                    l.sendParticles(ParticleTypes.CLOUD, bp.getX() + 0.5, bp.getY() + 0.5, bp.getZ() + 0.5, 3, 0.3, 0.3, 0.3, 0.02);
                    budget--;
                    continue;
                }
                float hard = st.getDestroySpeed(l, bp);
                if (hard < 0 || st.getCollisionShape(l, bp).isEmpty() && !st.ignitedByLava()) continue;
                if (tier == 3 && hard > 6) continue; // 80% cuts, but not the hardest stuff
                if (tier == 3 && now % 3 != 0) { budget = 0; break; }
                l.destroyBlock(bp, tier == 3, p);
                l.sendParticles(ParticleTypes.LAVA, bp.getX() + 0.5, bp.getY() + 0.5, bp.getZ() + 0.5, 2, 0.2, 0.2, 0.2, 0);
                budget--;
                last = bp;
            }
        }
        // what it reaches burns
        Vec3 end = from.add(dir.scale(range));
        if (now % 8 == 0) {
            BlockPos e = BlockPos.containing(end);
            for (int i = 0; i < 4; i++) {
                BlockPos q = e.offset(l.random.nextInt(5) - 2, l.random.nextInt(3) - 1, l.random.nextInt(5) - 2);
                if (mayCut(l, q) && l.getBlockState(q).isAir() && BaseFireBlock.canBePlacedAt(l, q, net.minecraft.core.Direction.UP)) l.setBlockAndUpdate(q, BaseFireBlock.getState(l, q));
            }
        }
        // 100%: where it ends, it blows apart
        if (tier == 4 && now % 12 == 0) {
            Vec3 hitAt = end;
            for (double d = 1.0; d <= range; d += 1.0) {
                BlockPos bp = BlockPos.containing(from.add(dir.scale(d)));
                if (!l.getBlockState(bp).isAir() && l.getBlockState(bp).getDestroySpeed(l, bp) < 0) { hitAt = from.add(dir.scale(d - 1)); break; }
            }
            if (mayCut(l, BlockPos.containing(hitAt)) && hitAt.distanceToSqr(p.position()) > 8 * 8)
                l.explode(p, l.damageSources().explosion(p, p), null, hitAt.x, hitAt.y, hitAt.z, 4F, true, Level.ExplosionInteraction.TNT);
        }
    }
}
