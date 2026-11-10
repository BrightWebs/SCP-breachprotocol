package com.breachprotocol.career;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.entity.FoundationStaff;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.*;

/**
 * Whoever is following you comes with you: staff and units told to follow (an Epsilon-11 detail, your
 * secretary, anyone), and creatures you've ordered to follow. Teleport, step through to Site-01 and
 * back, change dimension: a moment later they're at your side. Until you release them (send them back
 * to their post, or Release).
 */
@EventBusSubscriber(modid = BreachProtocol.MODID)
public final class Escort {
    private Escort() {}

    private record Seen(ResourceKey<Level> dim, Vec3 at) {}

    /** Each player's followers, as last seen, and where the player was a tick ago. */
    private static final Map<UUID, Map<UUID, ResourceKey<Level>>> FOLLOWERS = new HashMap<>();
    private static final Map<UUID, Seen> LAST = new HashMap<>();

    private static boolean follows(Entity e, ServerPlayer p) {
        if (e instanceof FoundationStaff f) return p.getUUID().equals(f.following());
        return e instanceof Mob && p.getUUID().toString().equals(e.getPersistentData().getString("bp_dom_follow"));
    }

    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        ServerLevel l = p.serverLevel();
        Seen last = LAST.put(p.getUUID(), new Seen(l.dimension(), p.position()));
        // once a second: who's following, near you
        if (p.tickCount % 20 == 0) {
            Map<UUID, ResourceKey<Level>> mine = FOLLOWERS.computeIfAbsent(p.getUUID(), k -> new HashMap<>());
            mine.entrySet().removeIf(en -> {
                ServerLevel fl = p.server.getLevel(en.getValue());
                Entity e = fl == null ? null : fl.getEntity(en.getKey());
                return e != null && (!e.isAlive() || !follows(e, p)); // gone, or released (unloaded ones are kept)
            });
            for (Entity e : l.getEntities((Entity) null, p.getBoundingBox().inflate(64, 32, 64), e -> e != p && e.isAlive() && follows(e, p)))
                mine.put(e.getUUID(), l.dimension());
        }
        // you just jumped: another dimension, or further than anyone could walk in a tick
        if (last == null) return;
        boolean jumped = last.dim() != l.dimension() || last.at().distanceToSqr(p.position()) > 24 * 24;
        if (!jumped) return;
        Map<UUID, ResourceKey<Level>> mine = FOLLOWERS.get(p.getUUID());
        if (mine == null || mine.isEmpty()) return;
        int i = 0;
        for (Map.Entry<UUID, ResourceKey<Level>> en : new ArrayList<>(mine.entrySet())) {
            ServerLevel from = p.server.getLevel(en.getValue());
            Entity e = from == null ? null : from.getEntity(en.getKey());
            if (e == null || !e.isAlive() || !follows(e, p)) continue;
            double a = i++ * 2.4;
            Vec3 at = p.position().add(Math.cos(a) * 2, 0, Math.sin(a) * 2);
            if (from != l) {
                Entity moved = e.changeDimension(new DimensionTransition(l, at, Vec3.ZERO, p.getYRot(), 0, DimensionTransition.DO_NOTHING));
                if (moved != null) mine.put(moved.getUUID(), l.dimension());
            } else e.teleportTo(at.x, at.y, at.z);
            l.sendParticles(ParticleTypes.PORTAL, at.x, at.y + 1, at.z, 12, 0.3, 0.6, 0.3, 0.2);
            if (e instanceof Mob m) m.getNavigation().stop();
        }
    }
}
