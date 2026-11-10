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
        if (a[1].equals("run") && a.length >= 4 && (a[2].equals("gear") || a[2].equals("ram"))) {
            if (Dominion.level(p) < 2) { stopRun(p); return; }
            if (a[2].equals("gear")) {
                int g = parse(a[3]);
                if (g <= 0) stopRun(p);
                else {
                    Integer was = RUN.put(p.getUUID(), Math.min(6, g));
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
                    if (Targets.yours(p, e) || e instanceof ServerPlayer q && q.isCreative()) continue;
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
            if (Targets.yours(p, e) || e instanceof ServerPlayer q && (q.isCreative() || !Targets.hostile(p, q))) continue;
            PowerKills.hurt(p, e, l.damageSources().playerAttack(p), 40F);
            Vec3 side = e.position().subtract(p.position()).normalize();
            e.push(dir.x * 2.5 + side.x, 0.8, dir.z * 2.5 + side.z);
            e.hurtMarked = true;
            l.sendParticles(ParticleTypes.EXPLOSION, e.getX(), e.getY() + e.getBbHeight() / 2, e.getZ(), 1, 0, 0, 0, 0);
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
