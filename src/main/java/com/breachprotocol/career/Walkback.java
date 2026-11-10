package com.breachprotocol.career;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.entity.Doors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.*;

/**
 * "Back to your cell": the anomaly walks there, on its own feet, through the doors on its way. If the
 * cell's door is lower than it is (SCP-096 is over three blocks tall; a cell door is two), it bends
 * double at the door and squeezes through, slowly. Its own urges are set aside for the walk.
 */
@EventBusSubscriber(modid = BreachProtocol.MODID)
public final class Walkback {
    private Walkback() {}

    private static final class Walk {
        final UUID id;
        final Vec3 dest;
        final AABB cell;
        final boolean hadAi;
        Path path;
        int node, stuck, repath, squeeze;
        double best = Double.MAX_VALUE;
        long started;
        Walk(UUID id, Vec3 dest, AABB cell, boolean hadAi) { this.id = id; this.dest = dest; this.cell = cell; this.hadAi = hadAi; }
    }

    private static final Map<UUID, Walk> WALKS = new HashMap<>();

    /** Sends it home. False if it has no cell of its own in this world. */
    public static boolean start(MinecraftServer srv, Mob m) {
        Containment.Entry e = null;
        for (Containment.Entry x : Containment.all(srv)) if (x.id.equals(m.getUUID())) e = x;
        if (e == null || e.cell.isEmpty()) return false;
        Sites.Cell cell = null;
        for (Sites.Cell c : Sites.get(srv).cells) if (c.name.equals(e.cell) && c.dim.equals(m.level().dimension().location().toString())) cell = c;
        if (cell == null) return false;
        AABB box = new AABB(cell.x0, cell.y0, cell.z0, cell.x1 + 1, cell.y1 + 1, cell.z1 + 1);
        Vec3 dest = new Vec3((cell.x0 + cell.x1) / 2.0 + 0.5, cell.y0 + 1, (cell.z0 + cell.z1) / 2.0 + 0.5);
        m.getPersistentData().remove("bp_dom_follow");
        m.getPersistentData().remove("bp_dom_stay");
        m.setTarget(null);
        m.getNavigation().stop();
        Walk w = new Walk(m.getUUID(), dest, box, !m.isNoAi());
        w.started = m.level().getGameTime();
        WALKS.put(m.getUUID(), w);
        m.setNoAi(true); // its own urges wait: it's walking home
        return true;
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        if (WALKS.isEmpty()) return;
        MinecraftServer srv = event.getServer();
        WALKS.values().removeIf(w -> {
            Mob m = Containment.loaded(srv, w.id);
            if (m == null) return srv.overworld().getGameTime() - w.started > 24000;
            try { return step((ServerLevel) m.level(), m, w); } catch (Throwable t) { finish(m, w); return true; }
        });
    }

    /** @return true when it's home. */
    private static boolean step(ServerLevel l, Mob m, Walk w) {
        double d = m.position().distanceToSqr(w.dest);
        if (d < 1.2 || w.cell.contains(m.position().add(0, 0.5, 0)) && w.squeeze == 0 && d < 9) { finish(m, w); return true; }
        // the last stretch: through the door, bent double if it has to be
        boolean nearCell = w.cell.inflate(3).contains(m.position());
        if (w.squeeze > 0 || nearCell && w.stuck > 30) {
            if (w.squeeze == 0) {
                if (m.getBbHeight() > 2.0F) m.setPose(Pose.CROUCHING); // stooping under the lintel
                for (BlockPos q : BlockPos.betweenClosed(BlockPos.containing(w.cell.inflate(1).getMinPosition()), BlockPos.containing(w.cell.inflate(1).getMaxPosition())))
                    if (Doors.isDoor(l.getBlockState(q)) && q.distToCenterSqr(m.position()) < 36) Doors.open(l, q.immutable());
            }
            w.squeeze++;
            m.noPhysics = true;
            Vec3 dir = w.dest.subtract(m.position());
            Vec3 stepv = dir.lengthSqr() > 0.01 ? dir.normalize().scale(0.07) : Vec3.ZERO;
            m.setPos(m.getX() + stepv.x, m.getY() + stepv.y * 0.5, m.getZ() + stepv.z);
            face(m, dir);
            if (w.squeeze % 10 == 0) l.sendParticles(ParticleTypes.POOF, m.getX(), m.getY() + 1.5, m.getZ(), 2, 0.3, 0.2, 0.3, 0.01);
            return false;
        }
        // walking: a route for a body this size, followed step by step
        if (w.path == null || w.path.isDone() || --w.repath <= 0) {
            w.path = m.getNavigation().createPath(BlockPos.containing(w.dest), 2);
            w.node = 0;
            w.repath = 60;
        }
        Vec3 next = w.dest;
        if (w.path != null && !w.path.isDone()) {
            while (w.node < w.path.getNodeCount() && m.position().distanceToSqr(Vec3.atBottomCenterOf(w.path.getNodePos(w.node))) < 0.6) w.node++;
            if (w.node < w.path.getNodeCount()) next = Vec3.atBottomCenterOf(w.path.getNodePos(w.node));
            // doors on the way open for it
            for (int i = w.node; i < Math.min(w.path.getNodeCount(), w.node + 3); i++) {
                BlockPos p = w.path.getNodePos(i);
                for (BlockPos q : new BlockPos[]{p, p.above()}) if (Doors.isDoor(l.getBlockState(q))) Doors.open(l, q);
            }
        }
        Vec3 dir = next.subtract(m.position());
        Vec3 flat = new Vec3(dir.x, 0, dir.z);
        double speed = 0.16;
        Vec3 mv = flat.lengthSqr() > 1e-4 ? flat.normalize().scale(speed) : Vec3.ZERO;
        boolean blocked = m.horizontalCollision;
        m.move(MoverType.SELF, new Vec3(mv.x, blocked && m.onGround() ? 0.42 : 0, mv.z));
        face(m, flat);
        if (d < w.best - 0.5) { w.best = d; w.stuck = 0; } else w.stuck++;
        // nowhere to go and nobody watching for a long while: it's found its way back
        if (w.stuck > 600) { m.teleportTo(w.dest.x, w.dest.y, w.dest.z); finish(m, w); return true; }
        return false;
    }

    private static void face(Mob m, Vec3 dir) {
        if (dir.horizontalDistanceSqr() < 1e-4) return;
        float yaw = (float) (Math.toDegrees(Math.atan2(dir.z, dir.x)) - 90);
        m.setYRot(yaw); m.setYHeadRot(yaw); m.setYBodyRot(yaw);
    }

    private static void finish(Mob m, Walk w) {
        m.noPhysics = false;
        m.setPose(Pose.STANDING);
        if (w.hadAi) m.setNoAi(false);
        m.getNavigation().stop();
        m.setPersistenceRequired();
    }

    /** Walking home right now (for the client: it's drawn stooping). */
    public static boolean walking(UUID id) { return WALKS.containsKey(id); }
}
