package com.breachprotocol.entity;

import com.breachprotocol.career.Defence;
import com.breachprotocol.compat.UnleashedApi;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;
import java.util.EnumSet;
import java.util.function.Predicate;

/**
 * An armed guard's combat: find the nearest threat (creepers first), keep a safe distance from it,
 * get a clear line of fire and shoot with SCP Unleashed's own guns. During a site alert, guards
 * with nothing to shoot move up to where the intruders were last seen.
 */
public class GuardGoal extends Goal {
    private final FoundationStaff g;
    private LivingEntity target;
    private int scan, repath, strafe;
    private boolean retreating;
    /** Ticks spent unable to see or reach the target; and a target given up on, until when. */
    private int stuck;
    private java.util.UUID ignored;
    private long ignoreUntil;

    private static Method fireAt, clearShot, range;
    private static boolean looked;

    public GuardGoal(FoundationStaff g) {
        this.g = g;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private boolean armed() { return g.role() == FoundationStaff.Role.GUARD; }

    private double reach() {
        Defence.Alert a = Defence.alertNear(g);
        return a != null || com.breachprotocol.career.Policy.lockdown(g.getServer()) ? 48 : 30;
    }

    private LivingEntity find() {
        double r = reach();
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (LivingEntity c : g.level().getEntitiesOfClass(LivingEntity.class, g.getBoundingBox().inflate(r, 16, r), e -> e.isAlive() && e != g)) {
            if (!Defence.threat(g, c)) continue;
            double d = g.distanceTo(c);
            if (d > r) continue;
            boolean sees = g.getSensing().hasLineOfSight(c);
            if (c.getUUID().equals(ignored) && g.level().getGameTime() < ignoreUntil) continue;
            if (!sees && (d > 16 || !reachable(c))) continue; // behind a wall with no way round: not ours to chase
            // creepers first, then whoever is closest; intruders before stray mobs
            double score = d - (c instanceof Creeper ? 40 : 0) - (Defence.intruder(g.getServer(), c) ? 20 : 0) + (sees ? 0 : 12);
            if (score < bestScore) { bestScore = score; best = c; }
        }
        return best;
    }

    /** Whether there is a walkable way to them. */
    private boolean reachable(LivingEntity c) {
        net.minecraft.world.level.pathfinder.Path path = g.getNavigation().createPath(c, 1);
        return path != null && path.canReach();
    }

    @Override
    public boolean canUse() {
        if (!armed()) return false;
        if (--scan > 0) return false;
        scan = 8 + g.getRandom().nextInt(4);
        target = find();
        if (target != null) return true;
        // the site is under attack: move up
        Defence.Alert a = Defence.alertNear(g);
        return a != null && g.blockPosition().distSqr(a.where) > 14 * 14;
    }

    @Override
    public boolean canContinueToUse() {
        if (target != null) return target.isAlive() && g.distanceTo(target) < reach() + 10 && Defence.threat(g, target);
        if (stuck == 0 && ignored != null && g.level().getGameTime() < ignoreUntil && Defence.alertNear(g) == null) return false;
        Defence.Alert a = Defence.alertNear(g);
        return a != null && g.blockPosition().distSqr(a.where) > 10 * 10;
    }

    @Override
    public void start() {
        if (Seats.seated(g)) Seats.stand(g);
        repath = 0;
        g.setAggressive(true);
        if (target != null) g.shout(target);
    }

    @Override
    public void stop() {
        target = null;
        retreating = false;
        g.setAggressive(false);
        g.setSprinting(false);
        g.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() { return true; }

    @Override
    public void tick() {
        if (target == null) {
            Defence.Alert a = Defence.alertNear(g);
            if (a == null) return;
            if (--repath <= 0) {
                repath = 15;
                g.setSprinting(true);
                g.getNavigation().moveTo(a.where.getX() + 0.5, a.where.getY(), a.where.getZ() + 0.5, 1.6);
            }
            if (g.tickCount % 10 == 0) { LivingEntity t = find(); if (t != null) { target = t; g.shout(t); } }
            return;
        }
        double d = g.distanceTo(target);
        boolean sees = g.getSensing().hasLineOfSight(target);
        // only aim at what they can see; otherwise look where they're going
        if (sees) { g.getLookControl().setLookAt(target, 60, 60); stuck = 0; }
        else if (++stuck > 100 || stuck % 20 == 0 && !reachable(target)) {
            // can't see it and can't get to it: give up on it for a while
            ignored = target.getUUID();
            ignoreUntil = g.level().getGameTime() + 400;
            target = null;
            stuck = 0;
            g.getNavigation().stop();
            g.setSprinting(false);
            return;
        }
        boolean creeper = target instanceof Creeper;
        double keep = creeper ? 11 : 5;
        double engage = Math.min(range(), creeper ? 34 : 28);

        if (d < keep) {
            // too close (a creeper above all): back off
            if (!retreating || g.getNavigation().isDone()) {
                Vec3 away = DefaultRandomPos.getPosAway(g, 12, 6, target.position());
                if (away != null) g.getNavigation().moveTo(away.x, away.y, away.z, creeper ? 1.7 : 1.3);
                retreating = true;
            }
            g.setSprinting(creeper);
        } else if (!sees || d > engage) {
            retreating = false;
            if (--repath <= 0) {
                repath = 10;
                g.getNavigation().moveTo(target, d > 16 ? 1.5 : 1.2);
            }
            g.setSprinting(d > 16);
        } else {
            retreating = false;
            g.setSprinting(false);
            // hold the line, with a small side-step now and then
            if (g.getNavigation().isDone() && --strafe <= 0) {
                strafe = 30 + g.getRandom().nextInt(40);
                Vec3 side = target.position().subtract(g.position()).normalize().cross(new Vec3(0, 1, 0)).scale(g.getRandom().nextBoolean() ? 2 : -2);
                g.getNavigation().moveTo(g.getX() + side.x, g.getY(), g.getZ() + side.z, 0.8);
            } else if (!g.getNavigation().isDone() && g.getNavigation().getPath() != null && g.getNavigation().getPath().getNodeCount() > 4) {
                g.getNavigation().stop();
            }
        }
        if (sees && d <= engage + 2) fire(d);
    }

    // ================================================================== shooting

    private static void lookup() {
        if (looked) return;
        looked = true;
        Class<?> ammo = UnleashedApi.cls("guns.npc.NpcAmmoSource");
        if (ammo != null) fireAt = UnleashedApi.method("guns.npc.NpcGunner", "fireAt", LivingEntity.class, ItemStack.class, LivingEntity.class, ammo, float.class);
        clearShot = UnleashedApi.method("guns.npc.NpcGunner", "clearShot", LivingEntity.class, LivingEntity.class, Predicate.class);
        range = UnleashedApi.method("guns.npc.NpcGunner", "range", ItemStack.class);
    }

    private double range() {
        lookup();
        Object r = UnleashedApi.call(range, null, g.getMainHandItem());
        return r instanceof Double v && v > 0 ? v : 24;
    }

    private void fire(double d) {
        lookup();
        MinecraftServer s = g.getServer();
        if (s == null) return;
        ItemStack gun = g.getMainHandItem();
        Predicate<Entity> friend = e -> e != target && Defence.friendly(s, e);
        if (clearShot != null && !Boolean.TRUE.equals(UnleashedApi.call(clearShot, null, g, target, friend))) {
            // someone of ours is in the way: shift sideways
            if (g.getNavigation().isDone()) {
                Vec3 side = target.position().subtract(g.position()).normalize().cross(new Vec3(0, 1, 0)).scale(g.getRandom().nextBoolean() ? 3 : -3);
                g.getNavigation().moveTo(g.getX() + side.x, g.getY(), g.getZ() + side.z, 1.0);
            }
            return;
        }
        float acc = g.unit() != null ? g.unit().accuracy : 0.65F;
        if (fireAt != null && !gun.isEmpty()) {
            if (Boolean.TRUE.equals(UnleashedApi.call(fireAt, null, g, gun, target, null, acc))) g.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            return;
        }
        // no SCP Unleashed guns: a plain shot every second
        if (g.tickCount % 20 == 0 && g.level() instanceof ServerLevel l) {
            target.hurt(g.damageSources().mobAttack(g), 5);
            l.playSound(null, g.blockPosition(), SoundEvents.CROSSBOW_SHOOT, SoundSource.HOSTILE, 1F, 1.6F);
            g.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        }
    }
}
