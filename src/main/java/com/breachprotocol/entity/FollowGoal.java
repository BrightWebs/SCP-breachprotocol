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
        if (mob.distanceToSqr(l) > 24 * 24) {
            mob.teleportTo(l.getX(), l.getY(), l.getZ());
            mob.getNavigation().stop();
            return;
        }
        if (--repath <= 0) {
            repath = 10;
            mob.getNavigation().moveTo(l, 1.1);
        }
    }

    @Override
    public void stop() { mob.getNavigation().stop(); }
}
