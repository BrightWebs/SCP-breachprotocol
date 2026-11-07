package com.breachprotocol.entity;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/** While someone has a conversation open with them, they stop what they're doing and face that person. */
public class ListenGoal extends Goal {
    private final Mob mob;
    private ServerPlayer who;

    public ListenGoal(Mob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    /** Told to follow you: they walk with you while you talk, rather than standing still. */
    private boolean walking(ServerPlayer p) {
        boolean follows = mob instanceof FoundationStaff s && s.following(p) || mob instanceof O5MemberEntity o && o.leader() != null && o.leader().equals(p.getUUID());
        return follows && mob.distanceToSqr(p) > 9;
    }

    @Override
    public boolean canUse() {
        who = Dialogue.talkingTo(mob);
        return who != null && !walking(who) && !mob.getPersistentData().getBoolean("bp_escorted");
    }

    @Override
    public boolean canContinueToUse() {
        return who != null && who.isAlive() && Dialogue.talkingTo(mob) == who && !walking(who) && !mob.getPersistentData().getBoolean("bp_escorted");
    }

    @Override
    public void start() { mob.getNavigation().stop(); }

    @Override
    public void stop() { who = null; }

    @Override
    public boolean requiresUpdateEveryTick() { return true; }

    @Override
    public void tick() {
        mob.getNavigation().stop();
        mob.getLookControl().setLookAt(who, 30, 30);
        if (!Seats.seated(mob)) {
            // turn the whole body toward them, not just the head
            double dx = who.getX() - mob.getX(), dz = who.getZ() - mob.getZ();
            float yaw = (float) (net.minecraft.util.Mth.atan2(dz, dx) * (180F / Math.PI)) - 90F;
            mob.setYRot(net.minecraft.util.Mth.rotateIfNecessary(mob.getYRot(), yaw, 20F));
            mob.yBodyRot = mob.getYRot();
        }
    }
}
