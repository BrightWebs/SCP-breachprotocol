package com.breachprotocol.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;
import java.util.function.Supplier;

/** Walks after a leader, keeping a couple of blocks back; catches up if left far behind. */
public class FollowGoal extends Goal {
    private final PathfinderMob mob;
    private final Supplier<Entity> leader;
    private int repath;
    private double lastSpeed;

    public FollowGoal(PathfinderMob mob, Supplier<Entity> leader) {
        this.mob = mob;
        this.leader = leader;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        Entity l = leader.get();
        return l != null && l.level() == mob.level() && mob.distanceToSqr(l) > 9;
    }

    @Override
    public boolean canContinueToUse() {
        Entity l = leader.get();
        return l != null && l.level() == mob.level() && mob.distanceToSqr(l) > 4;
    }

    @Override
    public void start() {
        repath = 0;
        if (Seats.seated(mob)) Seats.stand(mob);
    }

    @Override
    public void tick() {
        Entity l = leader.get();
        if (l == null) return;
        mob.getLookControl().setLookAt(l, 30, 30);
        if (mob.distanceToSqr(l) > 20 * 20) {
            mob.teleportTo(l.getX(), l.getY(), l.getZ());
            mob.getNavigation().stop();
            return;
        }
        // keep pace with the leader: walk when they walk, run when they run
        double leaderSpeed = l.getDeltaMovement().horizontalDistance() * 20; // blocks per second
        double d = Math.sqrt(mob.distanceToSqr(l));
        double base = mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) * 20 * 2.15; // their walking pace, roughly
        double want = Math.max(4.3, leaderSpeed * 1.15) + Math.max(0, d - 4) * 0.6;
        double speed = Math.max(1.0, Math.min(2.6, want / Math.max(0.1, base)));
        if (l instanceof net.minecraft.world.entity.player.Player p && p.isSprinting()) speed = Math.max(speed, 1.9);
        mob.setSprinting(speed > 1.5);
        if (--repath <= 0 || Math.abs(speed - lastSpeed) > 0.25) {
            repath = 6;
            lastSpeed = speed;
            mob.getNavigation().moveTo(l, speed);
        }
    }

    @Override
    public void stop() { mob.getNavigation().stop(); mob.setSprinting(false); }
}
