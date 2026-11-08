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
            case "impact" -> {
                l.playSound(p, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.5F, 1.2F);
                for (ServerPlayer q : l.players()) if (q != p) l.sendParticles(q, ParticleTypes.EXPLOSION, true, at.x, at.y, at.z, 3, 0.6, 0.6, 0.6, 0);
            }
            default -> {}
        }
    }

    private static int parse(String s) { try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 0; } }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        if (GEAR.isEmpty()) return;
        MinecraftServer srv = event.getServer();
        GEAR.entrySet().removeIf(en -> {
            ServerPlayer p = srv.getPlayerList().getPlayer(en.getKey());
            if (p == null || !p.getAbilities().flying || p.onGround()) return true;
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
