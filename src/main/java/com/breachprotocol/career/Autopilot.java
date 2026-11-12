package com.breachprotocol.career;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.network.FlightPosePayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Autopilot: the Administrator's body fights by itself, the way Manas Reborn's bosses do.
 *
 * <p>It hunts what your own rules call hostile (Targets): anything already coming for you first, then whatever
 * it can see out in the open (on the surface, in plain sight; not things down in caves it can't see). It flies
 * to them (your client steers: see client.Autopilot), blinks onto them when they're far, strikes with your
 * bare hands (SCP-096's blow), throws your SCP powers at them, and uses every Manas Reborn skill you have that
 * a creature could use in a fight (the same skills its bosses pick from: those with a combat weight, aimed at
 * an enemy or a spot), each when it's ready.</p>
 */
@EventBusSubscriber(modid = BreachProtocol.MODID)
public final class Autopilot {
    private Autopilot() {}

    private static final double SEEK = 48;      // how far it looks for a fight
    private static final double BLINK_AT = 14;  // further than this, and visible: blink
    private static final double REACH = 4.2;    // bare-hand reach

    /** How it goes about a fight (sent to the client, which does the moving). */
    static final int CHASE = 0, RAM = 1, DIVE = 2, TORNADO = 3;

    private static final class State {
        int mode;            // 0 hunt: anything hostile; 1 guard: only what comes for you
        int tactic, sentTactic = -1;
        long tacticUntil, nextTornado, nextRam, nextDive;
        boolean running;     // the run's gears are on (for the ram and the tornado)
        UUID target;
        int sent = -2;
        long nextBlink, nextBlow, nextPower, nextSkill;
        Object held; int heldMode; long releaseAt;   // a Manas skill being held
    }

    private static final Map<UUID, State> ON = new HashMap<>();

    public static boolean on(ServerPlayer p) { return ON.containsKey(p.getUUID()); }

    /** "toggle" or "toggle:mode" (the Administrator skill's mode: 0 Hunt, 1 Guard), from the skill or the phone. */
    public static void action(ServerPlayer p, String a) {
        if (!a.startsWith("toggle")) return;
        int mode = a.startsWith("toggle:") ? ("1".equals(a.substring(7)) ? 1 : 0) : 0;
        State had = ON.get(p.getUUID());
        if (had != null && had.mode == mode) { stop(p, "Autopilot off. You have yourself back."); return; }
        if (!Vault.chief(p)) { p.displayClientMessage(Component.literal("Only the Administrator's body fights by itself.").withStyle(ChatFormatting.GRAY), true); return; }
        State st = had != null ? had : new State();
        st.mode = mode;
        st.target = null;
        ON.put(p.getUUID(), st);
        p.displayClientMessage(Component.literal(mode == 0 ? "AUTOPILOT  ·  HUNT  ·  anything you've marked hostile" : "AUTOPILOT  ·  GUARD  ·  only what comes for you").withStyle(ChatFormatting.LIGHT_PURPLE), true);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 1.6F);
    }

    private static void stop(ServerPlayer p, String why) {
        State s = ON.remove(p.getUUID());
        if (s != null) { release(p, s); run(p, s, false); }
        send(p, -1, 11);
        if (why != null) {
            p.displayClientMessage(Component.literal(why).withStyle(ChatFormatting.GRAY), true);
            p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.8F, 1.6F);
        }
    }

    private static void send(ServerPlayer p, int target, int state) {
        PacketDistributor.sendToPlayer(p, new FlightPosePayload(target, state));
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        if (ON.isEmpty()) return;
        MinecraftServer srv = event.getServer();
        for (UUID id : new ArrayList<>(ON.keySet())) {
            ServerPlayer p = srv.getPlayerList().getPlayer(id);
            State s = ON.get(id);
            if (p == null) { ON.remove(id); continue; }
            if (!p.isAlive() || p.isSpectator() || !Vault.chief(p)) { stop(p, null); continue; }
            try { think(p, s); } catch (Throwable t) { BreachProtocol.LOGGER.debug("Autopilot: {}", t.toString()); }
        }
    }

    // ================================================================== choosing

    /** Out in the open: under the sky, or at least up at the surface (not somewhere down in the rock). */
    private static boolean onSurface(ServerLevel l, LivingEntity e) {
        BlockPos at = e.blockPosition();
        if (l.canSeeSky(at.above())) return true;
        int top = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
        return e.getY() >= top - 3;
    }

    private static boolean comingFor(ServerPlayer p, LivingEntity e) {
        return e instanceof Mob m && m.getTarget() == p || e.getLastHurtMob() == p;
    }

    /** Coming for you: has you as its target, or has hurt you in the last few seconds. */
    private static boolean onYou(ServerPlayer p, LivingEntity e) {
        return e instanceof Mob m && m.getTarget() == p || p.getLastHurtByMob() == e && p.tickCount - p.getLastHurtByMobTimestamp() < 200;
    }

    /**
     * Fair game. Guard: only what's coming for you. Hunt: anything hostile by your rules that's either coming
     * for you or out in the open where you can see it.
     */
    private static boolean fair(ServerPlayer p, State s, LivingEntity e) {
        if (e == p || !e.isAlive() || e.isRemoved() || e.isSpectator() || e.distanceToSqr(p) > SEEK * SEEK) return false;
        if (s.mode == 1) return onYou(p, e) && !Targets.yours(p, e);
        if (!Targets.hostile(p, e)) return false;
        if (comingFor(p, e)) return true;
        return onSurface(p.serverLevel(), e) && p.hasLineOfSight(e);
    }

    private static LivingEntity choose(ServerPlayer p, State s) {
        ServerLevel l = p.serverLevel();
        LivingEntity cur = s.target == null ? null : l.getEntity(s.target) instanceof LivingEntity le ? le : null;
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(SEEK), e -> fair(p, s, e))) {
            double score = e.distanceToSqr(p) - (comingFor(p, e) ? 4000 : 0) - (e == cur ? 600 : 0); // whatever's on you first; and finish what you start
            if (score < bestScore) { bestScore = score; best = e; }
        }
        return best;
    }

    // ================================================================== fighting

    private static void think(ServerPlayer p, State s) {
        ServerLevel l = p.serverLevel();
        long now = l.getGameTime();
        if (s.held != null && now >= s.releaseAt) release(p, s);
        LivingEntity t = null;
        if (now % 4 == 0 || s.target == null) {
            t = choose(p, s);
            s.target = t == null ? null : t.getUUID();
        } else if (l.getEntity(s.target) instanceof LivingEntity le && le.isAlive()) t = le;
        else s.target = null;
        int tid = t == null ? -1 : t.getId();
        if (tid != s.sent) { s.sent = tid; send(p, tid, 10); }
        if (t == null) { tactic(p, s, CHASE, now); run(p, s, false); return; }

        double d = p.distanceTo(t);
        boolean fast = Dominion.level(p) >= 2; // the run's gears and the flight are SCP-Ω's
        // how to go about it: a tornado for a crowd, a run straight through, a dive from on high, or just in after it
        if (now >= s.tacticUntil || s.tactic == CHASE && now % 20 == 0) {
            int crowd = l.getEntitiesOfClass(LivingEntity.class, t.getBoundingBox().inflate(10), e -> fair(p, s, e)).size();
            int next = CHASE;
            long until = now + 40;
            if (fast && crowd >= 3 && now >= s.nextTornado) { next = TORNADO; until = now + 200; s.nextTornado = now + 500; }
            else if (fast && d > 9 && t.onGround() && now >= s.nextRam && l.random.nextInt(3) == 0) { next = RAM; until = now + 80; s.nextRam = now + 160; }
            else if (d > 6 && p.getAbilities().mayfly && now >= s.nextDive && l.random.nextInt(3) == 0) { next = DIVE; until = now + 120; s.nextDive = now + 200; }
            if (next != s.tactic || now >= s.tacticUntil) { tactic(p, s, next, now); s.tacticUntil = until; }
        }
        run(p, s, s.tactic == RAM || s.tactic == TORNADO);

        // the run straight through: on reaching it, the sonic stop and a blow
        if (s.tactic == RAM && d <= 2.8) {
            Flight.wave(p, 4.6);
            p.swing(InteractionHand.MAIN_HAND, true);
            Toggles.shyBlow(p, t, 40F, 2.2, 400);
            tactic(p, s, CHASE, now);
            s.tacticUntil = now + 30;
            s.nextBlow = now + 8;
        }
        // the dive: coming down on it from above
        if (s.tactic == DIVE && d <= 3.2 && p.getY() >= t.getY() - 0.5) {
            impact(p, t);
            tactic(p, s, CHASE, now);
            s.tacticUntil = now + 30;
            s.nextBlow = now + 8;
        }
        // blinking is for when it's truly far (it has faster ways to close a gap)
        if (s.tactic == CHASE && d > 40 && now >= s.nextBlink && (onSurface(l, t) || comingFor(p, t))) {
            blink(p, t);
            s.nextBlink = now + 200;
            s.nextBlow = now + 3;
            d = p.distanceTo(t);
        }
        // bare hands, close in
        if (d <= REACH && now >= s.nextBlow && s.tactic != TORNADO) {
            p.swing(InteractionHand.MAIN_HAND, true);
            Toggles.shyBlow(p, t, (float) Math.max(12, p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) * 2), 0.5, 200);
            s.nextBlow = now + 8;
        }
        // an SCP power, every so often
        if (now >= s.nextPower) { if (power(p, t, d)) s.nextPower = now + 40 + l.random.nextInt(30); else s.nextPower = now + 10; }
        // a Manas Reborn skill, whenever one's ready
        if (s.held == null && now >= s.nextSkill) { if (skill(p, s, t, now)) s.nextSkill = now + 20 + l.random.nextInt(20); else s.nextSkill = now + 20; }
    }

    private static void tactic(ServerPlayer p, State s, int t, long now) {
        s.tactic = t;
        if (t != s.sentTactic) { s.sentTactic = t; send(p, t, 12); }
    }

    /** The run's top gear on (fire underfoot; laps spin a tornado) or off, as the tactic needs. */
    private static void run(ServerPlayer p, State s, boolean on) {
        if (on == s.running) return;
        s.running = on;
        try { Flight.action(p, new String[]{"pow", "run", "gear", on ? "8" : "0"}); } catch (Throwable ignored) {}
    }

    /** Coming down out of the sky onto it: a crater's worth of blow, everything near thrown off its feet. */
    private static void impact(ServerPlayer p, LivingEntity t) {
        ServerLevel l = p.serverLevel();
        Vec3 at = t.position();
        l.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 0.5, at.z, 1, 0, 0, 0, 0);
        l.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 2.5F, 0.6F);
        l.playSound(null, at.x, at.y, at.z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 2F, 0.8F);
        net.minecraft.world.level.block.state.BlockState under = l.getBlockState(t.blockPosition().below());
        if (!under.isAir()) l.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, under), at.x, at.y + 0.1, at.z, 80, 2, 0.2, 2, 0.4);
        p.swing(InteractionHand.MAIN_HAND, true);
        Toggles.shyBlow(p, t, 60F, 1.6, 400);
        for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, t.getBoundingBox().inflate(5), e -> e != t && e != p && Targets.hostile(p, e))) {
            Vec3 away = e.position().subtract(at).multiply(1, 0, 1);
            away = away.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : away.normalize();
            PowerKills.hurt(p, e, l.damageSources().playerAttack(p), 14F);
            e.push(away.x * 1.4, 0.7, away.z * 1.4);
            e.hurtMarked = true;
        }
        p.fallDistance = 0;
    }

    /** Onto it, a couple of blocks short, facing it. */
    private static void blink(ServerPlayer p, LivingEntity t) {
        ServerLevel l = p.serverLevel();
        Vec3 from = p.position();
        Vec3 dir = from.subtract(t.position()).multiply(1, 0, 1);
        dir = dir.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : dir.normalize();
        Vec3 to = t.position().add(dir.scale(t.getBbWidth() / 2 + 1.8));
        // somewhere you'll fit
        for (int up = 0; up < 4; up++) {
            Vec3 q = to.add(0, up, 0);
            if (l.noCollision(p, p.getBoundingBox().move(q.subtract(p.position())))) { to = q; break; }
        }
        l.sendParticles(ParticleTypes.REVERSE_PORTAL, from.x, from.y + 1, from.z, 30, 0.3, 0.6, 0.3, 0.05);
        l.playSound(null, from.x, from.y, from.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.7F, 1.4F);
        Vec3 look = t.getEyePosition().subtract(to.add(0, p.getEyeHeight(), 0));
        float yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z)), pitch = (float) -Math.toDegrees(Math.atan2(look.y, look.horizontalDistance()));
        p.teleportTo(l, to.x, to.y, to.z, yaw, pitch);
        p.fallDistance = 0;
        l.sendParticles(ParticleTypes.REVERSE_PORTAL, to.x, to.y + 1, to.z, 30, 0.3, 0.6, 0.3, 0.05);
    }

    /** One of your SCP powers that suits the moment: something that hits what's around you, when it's close enough. */
    private static boolean power(ServerPlayer p, LivingEntity t, double d) {
        int lv = Dominion.level(p);
        List<Powers.Power> fit = new ArrayList<>();
        for (Powers.Power pw : Powers.ALL) {
            if (pw.minLevel() > lv) continue;
            switch (pw.id()) {
                case "smite", "corrode", "roar", "immolate", "tesla", "judgement" -> { if (d <= pw.rDef()) fit.add(pw); }
                case "repel" -> { if (d <= 4) fit.add(pw); }
                default -> {}
            }
        }
        if (fit.isEmpty()) return false;
        Powers.Power pw = fit.get(p.getRandom().nextInt(fit.size()));
        Powers.castAs(p, pw.id());
        return true;
    }

    // ================================================================== Manas Reborn's skills

    private static boolean manasReady, manasBroken;
    private static Method skillsFrom, learned, getModes, onCoolDown, canActivate, mobWeight, mobTargetType, mobHold, skillOf;
    private static Method holdWithTarget, hold, releaseNew, stopHold, onRelease, targetOf;

    private static boolean manas() {
        if (manasReady) return true;
        if (manasBroken) return false;
        try {
            Class<?> api = Class.forName("io.github.manasmods.manascore.skill.api.SkillAPI");
            Class<?> skills = Class.forName("io.github.manasmods.manascore.skill.api.Skills");
            Class<?> inst = Class.forName("io.github.manasmods.manascore.skill.api.ManasSkillInstance");
            skillsFrom = api.getMethod("getSkillsFrom", LivingEntity.class);
            learned = skills.getMethod("getLearnedSkills");
            getModes = inst.getMethod("getModes");
            onCoolDown = inst.getMethod("onCoolDown", int.class);
            canActivate = inst.getMethod("canActivateSkill", LivingEntity.class, int.class);
            skillOf = inst.getMethod("getSkill");
            mobWeight = find(inst, "getMobWeight", Mob.class, int.class);
            mobTargetType = find(inst, "getMobTargetType", Mob.class, int.class);
            mobHold = find(inst, "getMobHoldTime", Mob.class, int.class);
            try {
                Class<?> st = Class.forName("io.github.manasmods.manascore.skill.api.SkillTarget");
                targetOf = st.getMethod("of", LivingEntity.class);
                holdWithTarget = skills.getMethod("startHoldSkill", inst, int.class, int.class, st);
            } catch (Throwable ignored) {}
            hold = find(skills, "startHoldSkill", inst, int.class, int.class);
            releaseNew = find(skills, "handleSkillRelease", inst, int.class, int.class, boolean.class);
            stopHold = find(skills, "stopHoldSkill", inst, int.class);
            onRelease = find(inst, "onRelease", LivingEntity.class, int.class, int.class, int.class);
            manasReady = mobWeight != null && (holdWithTarget != null || hold != null);
            if (!manasReady) manasBroken = true;
        } catch (Throwable t) {
            manasBroken = true;
            BreachProtocol.LOGGER.info("Autopilot: Manas Reborn's skills aren't reachable ({}); it fights with SCP powers only", t.toString());
        }
        return manasReady;
    }

    private static Method find(Class<?> c, String name, Class<?>... args) {
        try { return c.getMethod(name, args); } catch (Throwable t) { return null; }
    }

    /**
     * Picks a skill the way Manas Reborn's own fighters do: one a creature could use in a fight (a combat weight
     * above nothing), aimed at an enemy or a spot, off cooldown and affordable; weighted by that weight. Held for
     * as long as a creature would hold it, then let go.
     */
    private static boolean skill(ServerPlayer p, State s, LivingEntity t, long now) {
        if (!(t instanceof Mob judge) || !manas()) return false; // the weighing asks about a creature; the one you're fighting will do
        try {
            Object store = skillsFrom.invoke(null, p);
            List<Object[]> picks = new ArrayList<>();
            int total = 0;
            for (Object inst : (java.util.Collection<?>) learned.invoke(store)) {
                Object sk = skillOf.invoke(inst);
                if (sk != null && sk.getClass().getName().startsWith("com.breachprotocol")) continue; // the Shapeshift isn't a weapon
                int modes = Math.max(1, (int) getModes.invoke(inst));
                for (int m = 0; m < modes; m++) {
                    if ((boolean) onCoolDown.invoke(inst, m) || !(boolean) canActivate.invoke(inst, p, m)) continue;
                    int w = (int) mobWeight.invoke(inst, judge, m);
                    if (w <= 0) continue;
                    String kind = mobTargetType == null ? "ENEMY" : String.valueOf(mobTargetType.invoke(inst, judge, m));
                    if (!kind.equals("ENEMY") && !kind.equals("POSITION")) continue;
                    picks.add(new Object[]{inst, m, w});
                    total += w;
                }
            }
            if (picks.isEmpty()) return false;
            int roll = p.getRandom().nextInt(total);
            Object[] pick = picks.get(0);
            for (Object[] c : picks) { roll -= (int) c[2]; if (roll < 0) { pick = c; break; } }
            Object inst = pick[0];
            int mode = (int) pick[1];
            int holdFor = mobHold == null ? 0 : Math.max(0, Math.min(80, (int) mobHold.invoke(inst, judge, mode)));
            boolean started;
            if (holdWithTarget != null) started = (boolean) holdWithTarget.invoke(store, inst, -1, mode, targetOf.invoke(null, t));
            else started = (boolean) hold.invoke(store, inst, -1, mode);
            if (!started) return false;
            s.held = inst;
            s.heldMode = mode;
            s.releaseAt = now + Math.max(1, holdFor);
            return true;
        } catch (Throwable e) {
            BreachProtocol.LOGGER.debug("Autopilot skill: {}", e.toString());
            return false;
        }
    }

    private static void release(ServerPlayer p, State s) {
        if (s.held == null) return;
        Object inst = s.held;
        s.held = null;
        try {
            Object store = skillsFrom.invoke(null, p);
            if (releaseNew != null) releaseNew.invoke(store, inst, -1, s.heldMode, false);
            else {
                if (onRelease != null) onRelease.invoke(inst, p, -1, s.heldMode, 0);
                if (stopHold != null) stopHold.invoke(store, inst, s.heldMode);
            }
        } catch (Throwable e) {
            BreachProtocol.LOGGER.debug("Autopilot release: {}", e.toString());
        }
    }
}
