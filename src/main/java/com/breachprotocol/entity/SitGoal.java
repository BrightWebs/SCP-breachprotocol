package com.breachprotocol.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Now and then an NPC walks to a free chair nearby and sits for a while, then gets up again. With
 * a home seat (the Overseers' council chairs) it goes back to that one and stays much longer.
 */
public class SitGoal extends Goal {
    private final PathfinderMob mob;
    private final Supplier<BlockPos> home;
    private final float chance;
    private final int minSit, maxSit, radius;
    private final Predicate<BlockPos> allowed;
    private BlockPos target;
    private int travel;
    private long standAt = -1;

    public SitGoal(PathfinderMob mob, Supplier<BlockPos> home, float chance, int minSit, int maxSit, int radius, Predicate<BlockPos> allowed) {
        this.mob = mob;
        this.home = home;
        this.chance = chance;
        this.minSit = minSit;
        this.maxSit = maxSit;
        this.radius = radius;
        this.allowed = allowed;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    /** Forces a stand-up time (used when an NPC is seated by a script). */
    public void seatedFor(int ticks) { standAt = mob.level().getGameTime() + ticks; }

    @Override
    public boolean canUse() {
        long now = mob.level().getGameTime();
        if (Seats.seated(mob)) {
            if (standAt < 0) standAt = now + minSit + mob.getRandom().nextInt(Math.max(1, maxSit - minSit));
            if (now >= standAt && !SitGoal.holdSeated(mob)) {
                Seats.stand(mob);
                standAt = -1;
            }
            return false;
        }
        if (mob.isPassenger() || mob.getTarget() != null) return false;
        if (mob.getRandom().nextFloat() >= chance) return false;
        BlockPos h = home == null ? null : home.get();
        if (h != null && !Seats.occupied(mob.level(), h) && h.closerToCenterThan(mob.position(), 32)) target = h;
        else target = find();
        return target != null;
    }

    /** Overseers stay seated while the Council is in session. */
    public static boolean holdSeated(PathfinderMob mob) {
        return mob instanceof O5MemberEntity o && o.holdSeat();
    }

    private BlockPos find() {
        BlockPos c = mob.blockPosition();
        BlockPos best = null;
        double bd = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-radius, -2, -radius), c.offset(radius, 2, radius))) {
            if (!Seats.isSeat(mob.level().getBlockState(p))) continue;
            if (allowed != null && !allowed.test(p)) continue;
            if (Seats.occupied(mob.level(), p)) continue;
            double d = p.distSqr(c) + mob.getRandom().nextInt(16);
            if (d < bd) { bd = d; best = p.immutable(); }
        }
        return best;
    }

    @Override
    public void start() {
        travel = 0;
        mob.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 0.8);
    }

    @Override
    public boolean canContinueToUse() {
        return target != null && travel < 300 && !mob.isPassenger() && !Seats.occupied(mob.level(), target);
    }

    @Override
    public void tick() {
        travel++;
        if (target == null) return;
        double d = mob.position().distanceToSqr(target.getX() + 0.5, target.getY(), target.getZ() + 0.5);
        if (d < 2.6) {
            if (Seats.sit(mob, target)) {
                standAt = mob.level().getGameTime() + minSit + mob.getRandom().nextInt(Math.max(1, maxSit - minSit));
            }
            target = null;
        } else if (travel % 40 == 0) {
            mob.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 0.8);
        }
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
        target = null;
    }
}
