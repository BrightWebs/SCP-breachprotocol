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

    /** "pow:fly:gear:n", "pow:fly:boom:n", "pow:fly:impact". */
    public static void action(ServerPlayer p, String[] a) {
        if (a.length >= 4 && a[2].equals("lie")) {
            if (a[3].equals("1") && Dominion.level(p) >= 2 && p.getAbilities().flying) com.breachprotocol.world.FlightState.LYING.add(p.getUUID());
            else com.breachprotocol.world.FlightState.LYING.remove(p.getUUID());
            return;
        }
        if (a.length < 3 || Dominion.level(p) < 2 || !p.getAbilities().flying) { if (a.length >= 3 && a[2].equals("gear")) GEAR.remove(p.getUUID()); return; }
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
                if (GEAR.getOrDefault(p.getUUID(), 0) < 5 || a.length < 4 || !p.mayBuild()) return;
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
            e.hurt(l.damageSources().playerAttack(p), 40F);
            Vec3 side = e.position().subtract(p.position()).normalize();
            e.push(dir.x * 2.5 + side.x, 0.8, dir.z * 2.5 + side.z);
            e.hurtMarked = true;
            l.sendParticles(ParticleTypes.EXPLOSION, e.getX(), e.getY() + e.getBbHeight() / 2, e.getZ(), 1, 0, 0, 0, 0);
        }
    }

    /** SCP-Ω in flight: the server takes their word for where they are (see mixin.FlightMoveMixin). */
    public static boolean trusted(ServerPlayer p) {
        return p != null && p.getAbilities().flying && Dominion.level(p) >= 2;
    }

    private static int parse(String s) { try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 0; } }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        MinecraftServer srv = event.getServer();
        // lying flat ends when the flight does
        com.breachprotocol.world.FlightState.LYING.removeIf(id -> {
            ServerPlayer p = srv.getPlayerList().getPlayer(id);
            return p == null || !p.getAbilities().flying || Dominion.level(p) < 2;
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
