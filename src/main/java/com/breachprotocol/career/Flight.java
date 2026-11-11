package com.breachprotocol.career;

import com.breachprotocol.BreachProtocol;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The server's side of SCP-Ω's flight (the flying itself is the client's: see client.SuperFlight).
 * What everyone else sees and hears: the boom when you break the sound barrier, the contrail behind
 * you at speed, and the crack when you hit something hard.
 */
@EventBusSubscriber(modid = BreachProtocol.MODID)
public final class Flight {
    private Flight() {}

    private static final Map<UUID, Integer> GEAR = new HashMap<>();
    /** Where each player was at the end of the last tick: their real speed, for the server's movement check. */
    private static final Map<UUID, Vec3> LAST = new HashMap<>();
    /** On foot: sprint gears, and who's running with a shoulder down. */
    private static final Map<UUID, Integer> RUN = new HashMap<>();
    private static final java.util.Set<UUID> RAM = new java.util.HashSet<>();
    private static final net.minecraft.resources.ResourceLocation STEP = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(BreachProtocol.MODID, "sprint_step");

    private static void step(ServerPlayer p, boolean on) {
        var a = p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.STEP_HEIGHT);
        if (a == null) return;
        if (on && !a.hasModifier(STEP)) a.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(STEP, 0.65, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
        else if (!on) a.removeModifier(STEP);
    }

    private static void ramPose(ServerPlayer p, boolean on) {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntity(p, new com.breachprotocol.network.FlightPosePayload(p.getId(), on ? 2 : 0));
    }

    /** Sixth gear: the ninja run (arms swept back), shown to everyone watching. */
    private static void ninjaPose(ServerPlayer p, boolean on) {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntity(p, new com.breachprotocol.network.FlightPosePayload(p.getId(), on ? 3 : 4));
    }

    private static void stopRun(ServerPlayer p) {
        Integer was = RUN.remove(p.getUUID());
        LOOPS.remove(p.getUUID());
        HEADING.remove(p.getUUID());
        if (was != null && was >= 6) ninjaPose(p, false);
        if (RAM.remove(p.getUUID())) ramPose(p, false);
        step(p, false);
    }

    /** "pow:fly:gear:n", "pow:fly:boom:n", "pow:fly:impact". */
    public static void action(ServerPlayer p, String[] a) {
        if (a.length >= 4 && a[2].equals("lie")) {
            if (a[3].equals("1") && Dominion.level(p) >= 2 && p.getAbilities().flying) com.breachprotocol.world.FlightState.LYING.add(p.getUUID());
            else com.breachprotocol.world.FlightState.LYING.remove(p.getUUID());
            return;
        }
        if (a.length >= 4 && a[2].equals("charge")) {
            boolean on = a[3].equals("1") && Dominion.level(p) >= 2;
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntity(p, new com.breachprotocol.network.FlightPosePayload(p.getId(), on ? 1 : 0));
            if (on) {
                ServerLevel l = p.serverLevel();
                l.playSound(p, p.getX(), p.getY(), p.getZ(), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 1F, 0.6F);
                l.sendParticles(ParticleTypes.CRIT, p.getX(), p.getY() + 0.05, p.getZ(), 12, 0.4, 0.02, 0.4, 0.1);
            }
            return;
        }
        if (a[1].equals("run") && a.length >= 4 && a[2].equals("wave")) {
            // a dead stop from the ninja run: everything that speed was carrying keeps going, ahead of you
            if (Dominion.level(p) >= 2 && RUN.getOrDefault(p.getUUID(), 0) >= 2) wave(p, Math.max(0.6, Math.min(8.0, parse(a[3]) / 100.0)));
            return;
        }
        if (a[1].equals("run") && a.length >= 4 && (a[2].equals("gear") || a[2].equals("ram"))) {
            if (Dominion.level(p) < 2) { stopRun(p); return; }
            if (a[2].equals("gear")) {
                int g = parse(a[3]);
                if (g <= 0) stopRun(p);
                else {
                    Integer was = RUN.put(p.getUUID(), Math.min(8, g));
                    step(p, true); // step assist: up whole blocks without a jump
                    boolean ninja = g >= 6, wasNinja = was != null && was >= 6;
                    if (ninja != wasNinja) ninjaPose(p, ninja);
                }
            } else {
                boolean on = a[3].equals("1") && RUN.getOrDefault(p.getUUID(), 0) >= 5;
                if (on) RAM.add(p.getUUID()); else RAM.remove(p.getUUID());
                ramPose(p, on);
            }
            return;
        }
        boolean runner = a[1].equals("run") && RUN.containsKey(p.getUUID());
        if (a.length < 3 || Dominion.level(p) < 2 || !p.getAbilities().flying && !runner) { if (a.length >= 3 && a[2].equals("gear")) GEAR.remove(p.getUUID()); return; }
        ServerLevel l = p.serverLevel();
        Vec3 at = p.position().add(0, 1, 0);
        switch (a[2]) {
            case "gear" -> {
                int g = a.length > 3 ? parse(a[3]) : 0;
                if (g <= 0) GEAR.remove(p.getUUID()); else GEAR.put(p.getUUID(), Math.min(6, g));
            }
            case "boom" -> {
                int g = a.length > 3 ? parse(a[3]) : 2;
                // heard for a long way; you hear your own on your side
                l.playSound(p, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 3F + g, 0.55F);
                l.playSound(p, at.x, at.y, at.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 2.5F, 0.8F);
                for (ServerPlayer q : l.players()) {
                    if (q == p) continue;
                    l.sendParticles(q, ParticleTypes.SONIC_BOOM, true, at.x, at.y, at.z, 1, 0, 0, 0, 0);
                    l.sendParticles(q, ParticleTypes.CLOUD, true, at.x, at.y, at.z, 40, 1.2, 1.2, 1.2, 0.15);
                    l.sendParticles(q, ParticleTypes.EXPLOSION, true, at.x, at.y, at.z, 2, 0.5, 0.5, 0.5, 0);
                }
            }
            case "tear" -> {
                // gear five: SCP-096's way through things. Whatever's in front of you goes (bedrock and the like excepted)
                if ((runner ? RAM.contains(p.getUUID()) ? 5 : 0 : GEAR.getOrDefault(p.getUUID(), 0)) < 5 || a.length < 4 || !p.mayBuild()) return;
                int n = 0;
                for (String k : a[3].split(",")) {
                    if (++n > 128) break;
                    long v;
                    try { v = Long.parseLong(k); } catch (NumberFormatException e) { continue; }
                    net.minecraft.core.BlockPos q = net.minecraft.core.BlockPos.of(v);
                    if (q.distToCenterSqr(p.position()) > 24 * 24 || !l.mayInteract(p, q)) continue;
                    var st = l.getBlockState(q);
                    if (st.isAir() || st.getDestroySpeed(l, q) < 0) continue;
                    l.destroyBlock(q, false, p);
                }
                if (p.tickCount % 3 == 0) l.playSound(p, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.2F, 0.8F);
            }
            case "launch" -> {
                // the shockwave of a take-off: everything near is thrown back, nothing is broken
                l.playSound(p, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 2F, 0.7F);
                l.playSound(null, at.x, at.y, at.z, SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 2F, 0.6F);
                Vec3 feet = p.position();
                for (int i = 0; i < 40; i++) {
                    double t = i * Math.PI * 2 / 40;
                    l.sendParticles(ParticleTypes.CLOUD, feet.x + Math.cos(t) * 1.2, feet.y + 0.1, feet.z + Math.sin(t) * 1.2, 0, Math.cos(t), 0.02, Math.sin(t), 0.6);
                }
                l.sendParticles(ParticleTypes.EXPLOSION_EMITTER, feet.x, feet.y + 0.2, feet.z, 1, 0, 0, 0, 0);
                for (net.minecraft.world.entity.LivingEntity e : l.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, p.getBoundingBox().inflate(7), e -> e != p && e.isAlive() && !e.isSpectator())) {
                    if (e instanceof ServerPlayer q && q.isCreative()) continue;
                    if (!Targets.hostile(p, e)) { nudge(p, e, p.getLookAngle()); continue; }
                    double d = Math.max(1, e.distanceTo(p));
                    Vec3 away = e.position().subtract(feet).multiply(1, 0, 1).normalize().scale(2.6 / Math.sqrt(d));
                    e.push(away.x, 0.5, away.z);
                    e.hurtMarked = true;
                }
            }
            case "impact" -> {
                l.playSound(p, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.5F, 1.2F);
                for (ServerPlayer q : l.players()) if (q != p) l.sendParticles(q, ParticleTypes.EXPLOSION, true, at.x, at.y, at.z, 3, 0.6, 0.6, 0.6, 0);
            }
            default -> {}
        }
    }

    /** At gear five, anything in your way is hit as if by a train, and thrown aside. Your own people are spared. */
    private static void ram(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        Vec3 dir = p.getLookAngle();
        var box = p.getBoundingBox().expandTowards(dir.scale(3)).inflate(1.0);
        for (net.minecraft.world.entity.LivingEntity e : l.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, box, e -> e != p && e.isAlive() && !e.isSpectator())) {
            if (!Targets.hostile(p, e)) { nudge(p, e, dir); continue; }
            PowerKills.hurt(p, e, l.damageSources().playerAttack(p), 40F);
            Vec3 side = e.position().subtract(p.position()).normalize();
            e.push(dir.x * 2.5 + side.x, 0.8, dir.z * 2.5 + side.z);
            e.hurtMarked = true;
            l.sendParticles(ParticleTypes.EXPLOSION, e.getX(), e.getY() + e.getBbHeight() / 2, e.getZ(), 1, 0, 0, 0, 0);
        }
    }

    // ---------------------------------------------------------------- gear seven: a trail of fire

    private record Ember(UUID owner, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dim, Vec3 at, long until) {}

    private static final java.util.List<Ember> EMBERS = new java.util.ArrayList<>();
    private static final Map<UUID, Vec3> TRAIL = new HashMap<>();

    /** A friend in the way: a gentle shove aside, nothing more. */
    private static void nudge(ServerPlayer p, net.minecraft.world.entity.LivingEntity e, Vec3 dir) {
        if (e instanceof ServerPlayer q && q.isCreative()) return;
        Vec3 away = e.position().subtract(p.position()).multiply(1, 0, 1);
        if (away.lengthSqr() < 1.0E-4) away = new Vec3(-dir.z, 0, dir.x);
        away = away.normalize().scale(0.35);
        e.push(away.x, 0.12, away.z);
        e.hurtMarked = true;
    }

    private static boolean spare(ServerPlayer p, net.minecraft.world.entity.LivingEntity e) {
        return e == p || !e.isAlive() || e.isSpectator() || !Targets.hostile(p, e);
    }

    /** Fire along the ground behind you, and anything you run into burns. */
    private static void blaze(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        Vec3 now = p.position(), was = TRAIL.put(p.getUUID(), now);
        if (was == null || was.distanceToSqr(now) > 400) return;
        long t = l.getGameTime();
        double len = was.distanceTo(now);
        int n = Math.max(1, (int) Math.ceil(len));
        for (int i = 0; i < n; i++) {
            Vec3 q = was.lerp(now, (i + 0.5) / n);
            EMBERS.add(new Ember(p.getUUID(), l.dimension(), q, t + 60));
            l.sendParticles(ParticleTypes.FLAME, q.x, q.y + 0.1, q.z, 3, 0.25, 0.05, 0.25, 0.01);
        }
        if (EMBERS.size() > 3000) EMBERS.subList(0, EMBERS.size() - 3000).clear();
        // what you run into
        var box = new net.minecraft.world.phys.AABB(was, now).inflate(p.getBbWidth() / 2 + 0.6, 0, p.getBbWidth() / 2 + 0.6).expandTowards(0, p.getBbHeight(), 0);
        for (var e : l.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, box, e -> e != p && e.isAlive() && !e.isSpectator())) {
            if (spare(p, e)) { nudge(p, e, now.subtract(was)); continue; }
            if (e.invulnerableTime > 10) continue;
            // 096's blow, at that speed: a burst, everything they have sealed, damage nothing resists, and on fire
            e.igniteForSeconds(6);
            Toggles.shyBlow(p, e, 40F, 2.4, 400);
            double cy = e.getY() + e.getBbHeight() / 2;
            l.sendParticles(ParticleTypes.EXPLOSION, e.getX(), cy, e.getZ(), 2, 0.4, 0.4, 0.4, 0);
            l.sendParticles(ParticleTypes.LAVA, e.getX(), cy, e.getZ(), 6, 0.3, 0.3, 0.3, 0);
            l.playSound(null, e.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.9F, 1.1F);
        }
    }

    private static void embers(MinecraftServer srv) {
        if (EMBERS.isEmpty()) return;
        long t = srv.overworld().getGameTime();
        EMBERS.removeIf(e -> e.until() < t);
        boolean burn = srv.getTickCount() % 5 == 0;
        for (Ember e : EMBERS) {
            ServerLevel l = srv.getLevel(e.dim());
            if (l == null) continue;
            long left = e.until() - t;
            if ((t + e.at().hashCode()) % 3 == 0) l.sendParticles(left > 20 ? ParticleTypes.FLAME : ParticleTypes.SMALL_FLAME, e.at().x, e.at().y + 0.15, e.at().z, 1, 0.3, 0.1, 0.3, 0.01);
            if (left < 15 && (t + e.at().hashCode()) % 4 == 0) l.sendParticles(ParticleTypes.SMOKE, e.at().x, e.at().y + 0.2, e.at().z, 1, 0.2, 0.1, 0.2, 0.01);
            if (!burn) continue;
            ServerPlayer owner = srv.getPlayerList().getPlayer(e.owner());
            var box = new net.minecraft.world.phys.AABB(e.at().x - 0.6, e.at().y - 0.2, e.at().z - 0.6, e.at().x + 0.6, e.at().y + 1.0, e.at().z + 0.6);
            for (var v : l.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, box, v -> owner != null && !spare(owner, v))) {
                v.igniteForSeconds(4);
                PowerKills.hurt(owner, v, l.damageSources().inFire(), 3F);
            }
        }
    }

    // ================================================================== the sonic stop

    /** A wall of air going on at the speed you stopped from, through whatever's ahead. */
    private static final class Wave {
        final UUID owner;
        final ServerLevel l;
        final Vec3 from, dir;
        final double speed, reach;
        double gone;
        final java.util.Set<Integer> hit = new java.util.HashSet<>();
        Wave(UUID owner, ServerLevel l, Vec3 from, Vec3 dir, double speed) {
            this.owner = owner; this.l = l; this.from = from; this.dir = dir; this.speed = speed;
            this.reach = 8 + speed * 11; // gear 2: about 19 blocks; gear 8: about 70
        }
    }

    private static final java.util.List<Wave> WAVES = new java.util.ArrayList<>();

    private static void wave(ServerPlayer p, double speed) {
        ServerLevel l = p.serverLevel();
        float yaw = p.getYRot() * net.minecraft.util.Mth.DEG_TO_RAD;
        Vec3 dir = new Vec3(-net.minecraft.util.Mth.sin(yaw), 0, net.minecraft.util.Mth.cos(yaw));
        Vec3 at = p.position().add(0, 1.0, 0);
        // you stop dead; the boom goes on without you
        p.setDeltaMovement(0, Math.min(0, p.getDeltaMovement().y), 0);
        p.hurtMarked = true;
        // the faster you were going, the bigger the bang
        float k = (float) Math.min(1.0, speed / 5.6);
        l.playSound(null, at.x, at.y, at.z, SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 1.2F + k, 0.9F - k * 0.4F);
        if (speed >= 1.5) l.playSound(null, at.x, at.y, at.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.0F + k * 2, 1.1F - k * 0.35F);
        if (speed >= 2.8) l.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 2.0F, 0.6F);
        Vec3 front = at.add(dir.scale(1.5));
        l.sendParticles(speed >= 2.8 ? ParticleTypes.EXPLOSION_EMITTER : ParticleTypes.EXPLOSION, front.x, front.y, front.z, speed >= 2.8 ? 1 : 3, 0.3, 0.3, 0.3, 0);
        l.sendParticles(speed >= 2.1 ? ParticleTypes.GUST_EMITTER_LARGE : ParticleTypes.GUST_EMITTER_SMALL, front.x, front.y, front.z, 1, 0, 0, 0, 0);
        // the shock ring thrown off where you stood
        int ring = 12 + (int) (k * 24);
        for (int i = 0; i < ring; i++) {
            double a = i * Math.PI * 2 / ring;
            l.sendParticles(ParticleTypes.CLOUD, at.x, p.getY() + 0.1, at.z, 0, Math.cos(a), 0.02, Math.sin(a), 0.3 + k * 0.6);
        }
        // dust kicked up off the ground under you
        net.minecraft.world.level.block.state.BlockState under = l.getBlockState(p.blockPosition().below());
        if (!under.isAir()) l.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, under), p.getX(), p.getY() + 0.1, p.getZ(), 40, 1.2, 0.1, 1.2, 0.3);
        WAVES.add(new Wave(p.getUUID(), l, at, dir, speed));
    }

    private static void waves(MinecraftServer srv) {
        if (WAVES.isEmpty()) return;
        WAVES.removeIf(w -> {
            ServerPlayer owner = srv.getPlayerList().getPlayer(w.owner);
            if (owner == null) return true;
            double to = Math.min(w.reach, w.gone + w.speed);
            for (double d = w.gone; d < to; d += 1.0) {
                Vec3 q = w.from.add(w.dir.scale(d));
                double r = 1.6 + d * 0.07;            // it spreads as it goes
                double fall = 1 - 0.55 * d / w.reach; // and weakens a little
                if ((int) d % 3 == 0) {
                    w.l.sendParticles(ParticleTypes.SONIC_BOOM, q.x, q.y, q.z, 1, 0, 0, 0, 0);
                    for (int i = 0; i < 6; i++) {
                        double a = i * Math.PI / 3 + d;
                        Vec3 side = new Vec3(-w.dir.z, 0, w.dir.x).scale(Math.cos(a) * r);
                        w.l.sendParticles(ParticleTypes.CLOUD, q.x + side.x, q.y + Math.sin(a) * r * 0.6, q.z + side.z, 0, w.dir.x, 0.02, w.dir.z, w.speed * 0.25);
                    }
                }
                // a wall stops it, with a bang (nothing is broken)
                net.minecraft.core.BlockPos bp = net.minecraft.core.BlockPos.containing(q);
                if (w.l.getBlockState(bp).isCollisionShapeFullBlock(w.l, bp) && w.l.getBlockState(bp.above()).isCollisionShapeFullBlock(w.l, bp.above())) {
                    Vec3 b = q.subtract(w.dir.scale(0.6));
                    w.l.sendParticles(ParticleTypes.EXPLOSION_EMITTER, b.x, b.y, b.z, 1, 0, 0, 0, 0);
                    w.l.playSound(null, b.x, b.y, b.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 2.5F, 0.7F);
                    var st = w.l.getBlockState(bp);
                    w.l.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, st), b.x, b.y, b.z, 30, 0.8, 0.8, 0.8, 0.4);
                    hitAround(owner, w, q, r, fall);
                    return true;
                }
                hitAround(owner, w, q, r, fall);
            }
            w.gone = to;
            if (w.gone >= w.reach) {
                Vec3 e = w.from.add(w.dir.scale(w.reach));
                w.l.sendParticles(ParticleTypes.GUST_EMITTER_SMALL, e.x, e.y, e.z, 1, 0, 0, 0, 0);
                return true;
            }
            return false;
        });
    }

    /** What the wave catches: the hostile are struck and thrown on ahead of it; friends only rocked; loose things blown along. */
    private static void hitAround(ServerPlayer owner, Wave w, Vec3 q, double r, double fall) {
        var box = new net.minecraft.world.phys.AABB(q, q).inflate(r, r * 0.8, r);
        for (net.minecraft.world.entity.Entity e : w.l.getEntities(owner, box, e -> e.isAlive() && !e.isSpectator())) {
            if (!w.hit.add(e.getId())) continue;
            Vec3 throwAt = w.dir.scale(w.speed * 0.55 * fall + 0.6).add(0, 0.45 + w.speed * 0.06, 0);
            if (e instanceof net.minecraft.world.entity.LivingEntity v) {
                if (spare(owner, v)) {
                    if (!(v instanceof ServerPlayer sp && sp.isCreative())) { v.push(w.dir.x * 0.5, 0.25, w.dir.z * 0.5); v.hurtMarked = true; }
                    continue;
                }
                float dmg = (float) ((4 + w.speed * 8) * fall); // gear 2, point-blank: 12; gear 8: about 50
                v.invulnerableTime = 0;
                Toggles.strike(v);
                PowerKills.hurt(owner, v, w.l.damageSources().sonicBoom(owner), dmg);
                v.push(throwAt.x, throwAt.y, throwAt.z);
                v.hurtMarked = true;
                double cy = v.getY() + v.getBbHeight() / 2;
                w.l.sendParticles(ParticleTypes.EXPLOSION, v.getX(), cy, v.getZ(), 1, 0.2, 0.2, 0.2, 0);
            } else if (e instanceof net.minecraft.world.entity.item.ItemEntity || e instanceof net.minecraft.world.entity.projectile.Projectile
                    || e instanceof net.minecraft.world.entity.item.FallingBlockEntity || e instanceof net.minecraft.world.entity.vehicle.AbstractMinecart || e instanceof net.minecraft.world.entity.vehicle.Boat) {
                e.push(throwAt.x, throwAt.y * 0.6, throwAt.z);
                e.hurtMarked = true;
            }
        }
    }

    // ================================================================== the tornado

    /** Each runner's last few seconds of path: where, and how far the way they were going turned. */
    private record Step(Vec3 at, double turn) {}

    private static final Map<UUID, java.util.ArrayDeque<Step>> LOOPS = new HashMap<>();
    private static final Map<UUID, Double> HEADING = new HashMap<>();

    /** A tornado spun up by running circles: as wide as the circles, taller with every lap, to the clouds. */
    private static final class Twister {
        final UUID owner;
        final ServerLevel l;
        Vec3 centre;
        double radius, height, ceiling, speed;
        int fire;      // laps run at the top gear: the fire gets into it
        long fed;
        int age;
        Twister(UUID owner, ServerLevel l, Vec3 centre, double radius) { this.owner = owner; this.l = l; this.centre = centre; this.radius = radius; }
        /** Width at a height (0..1): narrow at the ground, opening out above. */
        double r(double f) { return radius * (0.35 + 1.15 * Math.pow(f, 1.4)); }
    }

    private static final java.util.List<Twister> TWISTERS = new java.util.ArrayList<>();

    /** Watches a runner's path; a lap closed at speed spins up (or feeds) a tornado where its middle was. */
    private static void circling(ServerPlayer p, int gear) {
        Vec3 now = p.position();
        java.util.ArrayDeque<Step> path = LOOPS.computeIfAbsent(p.getUUID(), k -> new java.util.ArrayDeque<>());
        Vec3 last = path.isEmpty() ? null : path.peekLast().at();
        if (last == null) { path.add(new Step(now, 0)); return; }
        Vec3 d = now.subtract(last).multiply(1, 0, 1);
        if (d.lengthSqr() < 0.09) return; // not really moving
        double h = Math.atan2(d.z, d.x);
        Double was = HEADING.put(p.getUUID(), h);
        double turn = was == null ? 0 : net.minecraft.util.Mth.wrapDegrees(Math.toDegrees(h - was)) * Mth_DEG;
        path.add(new Step(now, turn));
        while (path.size() > 140) path.poll();
        if (gear < 4 || path.size() < 8) return;
        double sum = 0;
        for (Step s : path) sum += s.turn();
        if (Math.abs(sum) < Math.PI * 2) return;
        // a lap: is it round enough (any rough circle will do)?
        double cx = 0, cz = 0;
        for (Step s : path) { cx += s.at().x; cz += s.at().z; }
        cx /= path.size(); cz /= path.size();
        double mean = 0;
        for (Step s : path) mean += Math.hypot(s.at().x - cx, s.at().z - cz);
        mean /= path.size();
        double var = 0;
        for (Step s : path) { double e = Math.hypot(s.at().x - cx, s.at().z - cz) - mean; var += e * e; }
        double spread = Math.sqrt(var / path.size());
        path.clear();
        path.add(new Step(now, 0));
        if (mean < 2.5 || spread > mean * 0.5) return;
        feed(p, new Vec3(cx, now.y, cz), Math.min(48, mean), RUN_SPEED[Math.min(gear, RUN_SPEED.length - 1)], gear >= 8);
    }

    private static final double Mth_DEG = Math.PI / 180.0;
    /** The run's speeds, blocks a tick, gear by gear (as the client runs them). */
    private static final double[] RUN_SPEED = {0, 0.6, 1.0, 1.5, 2.1, 2.8, 3.6, 4.6, 5.6};

    private static void feed(ServerPlayer p, Vec3 centre, double radius, double speed, boolean ablaze) {
        ServerLevel l = p.serverLevel();
        Twister t = null;
        for (Twister x : TWISTERS)
            if (x.owner.equals(p.getUUID()) && x.l == l && x.centre.distanceTo(centre) < x.radius * 1.5 + 12) { t = x; break; }
        int ground = l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(centre.x), (int) Math.floor(centre.z));
        double base = Math.min(centre.y, ground);
        double ceiling = l.dimensionType().hasCeiling() ? 100 : Math.max(48, 200 - base); // the overworld's clouds are at 192
        if (t == null) {
            t = new Twister(p.getUUID(), l, new Vec3(centre.x, base, centre.z), radius);
            t.height = 10 + speed * 4;
            TWISTERS.add(t);
            l.playSound(null, centre.x, base, centre.z, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 3F, 0.5F);
        } else {
            t.centre = new Vec3(t.centre.x + (centre.x - t.centre.x) * 0.4, base, t.centre.z + (centre.z - t.centre.z) * 0.4);
            t.radius += (radius - t.radius) * 0.5;
            t.height += 14 + speed * 9; // a handful of laps at the top gear reaches the clouds
        }
        t.ceiling = ceiling;
        t.height = Math.min(t.height, ceiling);
        t.speed = Math.max(t.speed * 0.8, speed);
        if (ablaze) t.fire = Math.min(6, t.fire + 1);
        t.fed = l.getGameTime();
    }

    private static void twisters(MinecraftServer srv) {
        if (TWISTERS.isEmpty()) return;
        TWISTERS.removeIf(t -> {
            ServerPlayer owner = srv.getPlayerList().getPlayer(t.owner);
            long idle = t.l.getGameTime() - t.fed;
            // left alone it winds down: lower, thinner, then gone
            if (idle > 240 || owner == null) {
                t.height *= 0.975;
                t.radius *= 0.99;
                if (t.fire > 0 && t.l.getGameTime() % 40 == 0) t.fire--;
            }
            if (t.height < 5 || t.radius < 1.2) { hush(t); return true; }
            t.age++;
            spin(t);
            if (owner != null) catchIn(owner, t);
            if (t.age % 30 == 0) {
                // its roar, kept on its own channel so it can be cut off the moment it's gone (and quieter as it dies)
                t.l.playSound(null, t.centre.x, t.centre.y + 4, t.centre.z, SoundEvents.ELYTRA_FLYING, SoundSource.WEATHER, (float) Math.min(6, 0.5 + t.height / 40), 0.5F);
                if (t.fire >= 1) t.l.playSound(null, t.centre.x, t.centre.y + 2, t.centre.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 2F, 0.6F);
            }
            return false;
        });
    }

    /** Gone: its wind stops with it, for everyone who could hear it (unless another of yours is still blowing nearby). */
    private static void hush(Twister t) {
        for (Twister o : TWISTERS) if (o != t && o.l == t.l && o.height >= 5 && o.centre.distanceToSqr(t.centre) < 160 * 160) return;
        var stop = new net.minecraft.network.protocol.game.ClientboundStopSoundPacket(SoundEvents.ELYTRA_FLYING.getLocation(), SoundSource.WEATHER);
        for (ServerPlayer q : t.l.players()) if (q.distanceToSqr(t.centre) < 256 * 256) q.connection.send(stop);
    }

    /** The funnel itself: wind spiralling up it, debris at its foot, cloud at its crown, and fire in it once it's burning. */
    private static void spin(Twister t) {
        ServerLevel l = t.l;
        var rnd = l.random;
        int n = (int) Math.min(170, 30 + t.height * 0.7 + t.radius);
        boolean burning = t.fire >= 1;
        java.util.List<ServerPlayer> see = new java.util.ArrayList<>();
        for (ServerPlayer q : l.players()) if (q.distanceToSqr(t.centre) < 480 * 480) see.add(q);
        if (see.isEmpty()) return;
        double spinRate = 0.35 + t.speed * 0.08;
        for (int i = 0; i < n; i++) {
            double f = rnd.nextDouble();
            double y = t.centre.y + f * t.height;
            double a = rnd.nextDouble() * Math.PI * 2;
            double r = t.r(f) * (0.75 + rnd.nextDouble() * 0.3);
            double x = t.centre.x + Math.cos(a) * r, z = t.centre.z + Math.sin(a) * r;
            // round and up: the swirl the way the laps went
            double vx = -Math.sin(a) * spinRate * (1 + r * 0.05), vz = Math.cos(a) * spinRate * (1 + r * 0.05), vy = 0.12 + t.speed * 0.03;
            net.minecraft.core.particles.ParticleOptions kind = burning && f < 0.8 && rnd.nextInt(3) == 0 ? ParticleTypes.FLAME
                    : f > 0.85 && rnd.nextInt(3) == 0 ? ParticleTypes.CAMPFIRE_COSY_SMOKE : rnd.nextInt(4) == 0 ? ParticleTypes.POOF : ParticleTypes.CLOUD;
            for (ServerPlayer q : see) l.sendParticles(q, kind, true, x, y, z, 0, vx, vy, vz, 1.0);
        }
        // its foot: dust and debris torn off the ground
        net.minecraft.core.BlockPos foot = net.minecraft.core.BlockPos.containing(t.centre).below();
        net.minecraft.world.level.block.state.BlockState under = l.getBlockState(foot);
        if (!under.isAir() && t.age % 2 == 0) {
            double a = rnd.nextDouble() * Math.PI * 2, r = t.r(0) * 1.2;
            for (ServerPlayer q : see)
                l.sendParticles(q, new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, under), true, t.centre.x + Math.cos(a) * r, t.centre.y + 0.3, t.centre.z + Math.sin(a) * r, 8, 1, 0.3, 1, 0.3);
        }
        // a burning one sets the ground round its foot alight (never on Foundation ground)
        if (burning && t.age % 4 == 0) {
            double a = rnd.nextDouble() * Math.PI * 2, r = t.r(0) * (0.6 + rnd.nextDouble() * 0.9);
            net.minecraft.core.BlockPos at = net.minecraft.core.BlockPos.containing(t.centre.x + Math.cos(a) * r, t.centre.y + 2, t.centre.z + Math.sin(a) * r);
            for (int k = 0; k < 5 && at.getY() > l.getMinBuildHeight(); k++, at = at.below()) {
                if (!l.getBlockState(at).isAir()) break;
                if (l.getBlockState(at.below()).isFaceSturdy(l, at.below(), net.minecraft.core.Direction.UP) && Sites.at(l, at) == null) {
                    l.setBlockAndUpdate(at, net.minecraft.world.level.block.BaseFireBlock.getState(l, at));
                    break;
                }
            }
        }
        if (burning && t.age % 3 == 0) for (ServerPlayer q : see) {
            double f = rnd.nextDouble() * 0.6;
            l.sendParticles(q, ParticleTypes.LAVA, true, t.centre.x, t.centre.y + f * t.height, t.centre.z, 2, t.r(f) * 0.5, 1, t.r(f) * 0.5, 0);
        }
        // the crown: where it meets the cloud
        if (t.height >= t.ceiling - 1 && t.age % 2 == 0) for (ServerPlayer q : see)
            l.sendParticles(q, ParticleTypes.CAMPFIRE_COSY_SMOKE, true, t.centre.x, t.centre.y + t.height, t.centre.z, 6, t.r(1) * 0.8, 1.5, t.r(1) * 0.8, 0.01);
    }

    /** Whatever it catches: the hostile are dragged round and up and battered (and burnt, once it's burning); friends only pushed clear; loose things taken up. */
    private static void catchIn(ServerPlayer owner, Twister t) {
        // it draws in from well beyond its own wall, the further and harder the faster you ran it up
        double suck = 6 + t.speed * 5;            // gear 4: about 16 blocks out; gear 8: about 34
        double pull = 0.05 + t.speed * 0.045;     // and how hard
        double out = t.r(1) + suck;
        var box = new net.minecraft.world.phys.AABB(t.centre.x - out, t.centre.y - 3, t.centre.z - out, t.centre.x + out, t.centre.y + t.height, t.centre.z + out);
        for (net.minecraft.world.entity.Entity e : t.l.getEntities(owner, box, e -> e.isAlive() && !e.isSpectator())) {
            double f = net.minecraft.util.Mth.clamp((e.getY() - t.centre.y) / t.height, 0, 1);
            double dx = e.getX() - t.centre.x, dz = e.getZ() - t.centre.z, d = Math.max(0.5, Math.hypot(dx, dz));
            double reach = t.r(f) + 3;
            boolean loose = e instanceof net.minecraft.world.entity.item.ItemEntity || e instanceof net.minecraft.world.entity.ExperienceOrb
                    || e instanceof net.minecraft.world.entity.projectile.Projectile || e instanceof net.minecraft.world.entity.item.FallingBlockEntity
                    || e instanceof net.minecraft.world.entity.vehicle.AbstractMinecart || e instanceof net.minecraft.world.entity.vehicle.Boat
                    || e instanceof net.minecraft.world.entity.item.PrimedTnt;
            if (!(e instanceof net.minecraft.world.entity.LivingEntity) && !loose) continue;
            if (e instanceof net.minecraft.world.entity.LivingEntity v && spare(owner, v)) {
                if (d <= reach && !(v instanceof ServerPlayer sp && sp.isCreative()) && t.age % 5 == 0) { v.push(dx / d * 0.4, 0.1, dz / d * 0.4); v.hurtMarked = true; }
                continue;
            }
            if (d > reach) {
                // outside the wall: dragged across the ground toward it, harder the closer they get, swinging round as they come
                double k = pull * (1 - (d - reach) / Math.max(1, out - reach)) + pull * 0.25;
                Vec3 v0 = e.getDeltaMovement();
                e.setDeltaMovement(v0.x - dx / d * k - dz / d * k * 0.35, Math.max(v0.y, 0.04 + k * 0.4), v0.z - dz / d * k + dx / d * k * 0.35);
                e.hurtMarked = true;
                continue;
            }
            // round (with the laps), in toward the wall of wind, and up; flung out at the top
            double tx = -dz / d, tz = dx / d, want = t.r(f) * 0.8;
            double in = (want - d) * 0.06;
            double up = f > 0.92 ? -0.05 : 0.22 + t.speed * 0.02;
            Vec3 v0 = e.getDeltaMovement();
            Vec3 v1 = new Vec3(tx * (0.55 + t.speed * 0.08) + dx / d * in, up, tz * (0.55 + t.speed * 0.08) + dz / d * in);
            if (f > 0.92) v1 = v1.add(dx / d * 0.8, 0, dz / d * 0.8);
            e.setDeltaMovement(v0.scale(0.3).add(v1.scale(0.7)));
            e.hurtMarked = true;
            e.fallDistance = 0;
            if (e instanceof net.minecraft.world.entity.LivingEntity v) {
                if (t.fire >= 1) v.igniteForSeconds(8); // in a burning funnel, alight the whole time
                if (t.age % 10 == 0) {
                    v.invulnerableTime = 0;
                    PowerKills.hurt(owner, v, t.l.damageSources().flyIntoWall(), (float) (2 + t.speed * 1.5));
                    if (t.fire >= 1) {
                        v.invulnerableTime = 0;
                        PowerKills.hurt(owner, v, t.l.damageSources().onFire(), 3F + t.fire * 1.5F);
                        t.l.sendParticles(ParticleTypes.FLAME, v.getX(), v.getY() + v.getBbHeight() / 2, v.getZ(), 8, 0.3, 0.4, 0.3, 0.02);
                    }
                }
            }
        }
    }

    private static int parse(String s) { try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 0; } }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        MinecraftServer srv = event.getServer();
        // on a server, the "moved too quickly" check measures each move against the speed it last knew you had.
        // A flier's (or runner's) real speed is kept here, so a fast but honest move, or two arriving together
        // over a laggy connection, isn't mistaken for cheating and dragged back
        for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
            Vec3 now = p.position(), was = LAST.put(p.getUUID(), now);
            if (was != null && (p.getAbilities().flying || RUN.containsKey(p.getUUID())) && Dominion.level(p) >= 2 && !p.isPassenger()
                    && was.distanceToSqr(now) < 400) // not a teleport
                p.setDeltaMovement(now.subtract(was));
        }
        embers(srv);
        waves(srv);
        twisters(srv);
        if (srv.getTickCount() % 200 == 0) LAST.keySet().removeIf(id -> srv.getPlayerList().getPlayer(id) == null);
        // lying flat ends when the flight does
        com.breachprotocol.world.FlightState.LYING.removeIf(id -> {
            ServerPlayer p = srv.getPlayerList().getPlayer(id);
            return p == null || !p.getAbilities().flying || Dominion.level(p) < 2;
        });
        // runners: the dust trail behind the fast ones; the ram
        RUN.entrySet().removeIf(en -> {
            ServerPlayer p = srv.getPlayerList().getPlayer(en.getKey());
            if (p == null) { RAM.remove(en.getKey()); return true; }
            if (p.getAbilities().flying || Dominion.level(p) < 2) { RAM.remove(en.getKey()); ramPose(p, false); if (en.getValue() >= 6) ninjaPose(p, false); step(p, false); return true; }
            if (RAM.contains(p.getUUID())) ram(p);
            if (en.getValue() >= 6) p.fallDistance = 0; // off the top of a wall and down again, unhurt
            if (en.getValue() >= 7) blaze(p); else TRAIL.remove(p.getUUID());
            circling(p, en.getValue());
            if (en.getValue() >= 2) {
                ServerLevel l = p.serverLevel();
                for (ServerPlayer q : l.players())
                    if (q != p && q.distanceToSqr(p) < 160 * 160) l.sendParticles(q, ParticleTypes.CLOUD, true, p.getX(), p.getY() + 0.2, p.getZ(), 2, 0.2, 0.05, 0.2, 0.01);
            }
            return false;
        });
        if (GEAR.isEmpty()) return;
        GEAR.entrySet().removeIf(en -> {
            ServerPlayer p = srv.getPlayerList().getPlayer(en.getKey());
            if (p == null || !p.getAbilities().flying) return true; // a graze of the ground isn't a landing
            if (en.getValue() >= 5) ram(p);
            if (en.getValue() < 2) return false;
            // the contrail, for everyone but the flier (theirs is drawn on their own screen)
            ServerLevel l = p.serverLevel();
            for (ServerPlayer q : l.players())
                if (q != p && q.distanceToSqr(p) < 256 * 256)
                    l.sendParticles(q, ParticleTypes.CLOUD, true, p.getX(), p.getY() + 0.9, p.getZ(), 2, 0.15, 0.15, 0.15, 0.01);
            return false;
        });
    }
}
