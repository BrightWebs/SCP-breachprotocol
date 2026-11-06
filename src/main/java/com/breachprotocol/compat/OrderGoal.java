package com.breachprotocol.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;
import java.util.UUID;

/**
 * Phone orders for SCP Unleashed's D-Class: FOLLOW the commanding player, or HOLD a position.
 * Added to each D-Class when it joins the world; it does nothing unless an order is set.
 */
public class OrderGoal extends Goal {
    public static final String ORDER = "breachprotocol_order", OWNER = "breachprotocol_order_owner", HOLD = "breachprotocol_hold";
    private final PathfinderMob mob;

    public OrderGoal(PathfinderMob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    public static String order(Entity e) {
        return e.getPersistentData().getString(ORDER);
    }

    public static void set(Entity e, String order, UUID owner) {
        CompoundTag pd = e.getPersistentData();
        if (order.equals("release")) {
            pd.remove(ORDER);
            pd.remove(OWNER);
            pd.remove(HOLD);
            return;
        }
        pd.putString(ORDER, order);
        pd.putUUID(OWNER, owner);
        pd.putLong(HOLD, e.blockPosition().asLong());
    }

    @Override
    public boolean canUse() {
        return !order(mob).isEmpty();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void tick() {
        CompoundTag pd = mob.getPersistentData();
        if ("follow".equals(order(mob)) && pd.hasUUID(OWNER) && mob.level() instanceof ServerLevel sl) {
            Entity owner = sl.getEntity(pd.getUUID(OWNER));
            if (owner == null) return;
            mob.getLookControl().setLookAt(owner, 30F, 30F);
            if (mob.distanceToSqr(owner) > 9 && mob.tickCount % 10 == 0) mob.getNavigation().moveTo(owner, 1.1);
            if (mob.distanceToSqr(owner) > 48 * 48) mob.teleportTo(owner.getX(), owner.getY(), owner.getZ());
        } else if ("hold".equals(order(mob))) {
            BlockPos hold = BlockPos.of(pd.getLong(HOLD));
            if (mob.blockPosition().distSqr(hold) > 4 && mob.tickCount % 10 == 0) {
                mob.getNavigation().moveTo(hold.getX() + 0.5, hold.getY(), hold.getZ() + 0.5, 1.0);
            } else if (mob.blockPosition().distSqr(hold) <= 4) {
                mob.getNavigation().stop();
            }
        }
    }
}
