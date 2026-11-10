package com.breachprotocol.career;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.compat.Unleashed;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.*;

/**
 * SCP-173's containment, as the Foundation runs it. Two D-Class stand in its cell with their eyes on
 * it at all times; while anyone is watching, it does not move. Once a week the cell is cleaned
 * (the excrement and blood it leaves), a crew in with it and the two watchers calling their blinks.
 * A blink out of turn costs a life. Watchers who die are replaced from the D-Class block.
 */
@EventBusSubscriber(modid = BreachProtocol.MODID)
public final class Observation {
    private Observation() {}

    private static final String WATCH = "bp_watch", ROLE = "bp_watch_role", FROZEN = "bp_watched", CLEANED = "bp_cleaned_t";
    private static final long WEEK = 24000L * 7, CLEANING = 20 * 120;

    /** Cleanings under way: the 173, and when it ends. */
    private static final Map<UUID, Long> CLEANING_UNTIL = new HashMap<>();

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        MinecraftServer srv = event.getServer();
        if (srv.getTickCount() % 10 != 3) return;
        for (Containment.Entry e : Containment.all(srv)) {
            if (e.status != Containment.HELD || !e.type.equals(Unleashed.SCP_173)) continue;
            Mob m = Containment.loaded(srv, e.id);
            if (m == null) continue;
            Sites.Cell cell = null;
            for (Sites.Cell c : Sites.get(srv).cells) if (c.name.equals(e.cell) && c.dim.equals(m.level().dimension().location().toString())) cell = c;
            if (cell == null) continue;
            try { watch((ServerLevel) m.level(), e, m, cell); } catch (Throwable t) { BreachProtocol.LOGGER.warn("SCP-173 watch: {}", t.toString()); }
        }
    }

    private static void watch(ServerLevel l, Containment.Entry e, Mob m, Sites.Cell cell) {
        AABB box = new AABB(cell.x0, cell.y0, cell.z0, cell.x1 + 1, cell.y1 + 1, cell.z1 + 1);
        String id = m.getUUID().toString();
        List<LivingEntity> crew = l.getEntitiesOfClass(LivingEntity.class, box.inflate(1), x -> x.isAlive() && id.equals(x.getPersistentData().getString(WATCH)));
        List<LivingEntity> watchers = new ArrayList<>(), cleaners = new ArrayList<>();
        for (LivingEntity x : crew) (x.getPersistentData().getString(ROLE).equals("clean") ? cleaners : watchers).add(x);
        long now = l.getGameTime();
        // two watchers, always
        while (watchers.size() < 2) {
            LivingEntity d = dclass(l, e, m, cell, watchers.size() == 0 ? 0 : 1, "watch");
            if (d == null) break;
            watchers.add(d);
            if (watchers.size() == 2 && l.random.nextInt(3) == 0) say(l, m, "A new D-Class is walked into SCP-173's cell. \"Eyes on it. Call your blinks.\"");
        }
        // the weekly cleaning
        CompoundTag pd = m.getPersistentData();
        if (!pd.contains(CLEANED)) pd.putLong(CLEANED, now);
        Long until = CLEANING_UNTIL.get(m.getUUID());
        if (until == null && now - pd.getLong(CLEANED) >= WEEK) until = begin(l, m);
        boolean blink = false;
        if (until != null) {
            if (now >= until) {
                CLEANING_UNTIL.remove(m.getUUID());
                pd.putLong(CLEANED, now);
                for (LivingEntity c : cleaners) c.discard();
                cleaners.clear();
                say(l, m, "SCP-173's cell is clean. The crew backs out, eyes forward, and the door shuts.");
                tellOwner(l, e, "SCP-173's cell has been cleaned. " + (pd.getInt("bp_clean_lost") == 0 ? "No casualties." : pd.getInt("bp_clean_lost") + " D-Class lost."));
                pd.remove("bp_clean_lost");
            } else {
                while (cleaners.size() < 1) {
                    LivingEntity c = dclass(l, e, m, cell, 2, "clean");
                    if (c == null) break;
                    cleaners.add(c);
                }
                // the crew works the floor
                for (LivingEntity c : cleaners) if (l.random.nextInt(4) == 0) {
                    double x = cell.x0 + 1.5 + l.random.nextDouble() * Math.max(1, cell.x1 - cell.x0 - 2), z = cell.z0 + 1.5 + l.random.nextDouble() * Math.max(1, cell.z1 - cell.z0 - 2);
                    c.teleportTo(x, cell.y0 + 1, z);
                    l.sendParticles(ParticleTypes.SPLASH, x, cell.y0 + 1.1, z, 8, 0.4, 0.05, 0.4, 0.05);
                    l.playSound(null, c.blockPosition(), SoundEvents.BUCKET_EMPTY, SoundSource.NEUTRAL, 0.4F, 1.2F);
                }
                if (l.random.nextInt(14) == 0) say(l, m, l.random.nextBoolean() ? "\"Blinking.\" \"Eyes on it.\"" : "\"Blinking.\" \"...Clear.\"");
                // a blink out of turn: once in a long while, during a cleaning, both close their eyes at once
                if (!cleaners.isEmpty() && l.random.nextInt(600) == 0) blink = true;
            }
        }
        // eyes on it
        for (LivingEntity w : watchers) face(w, m);
        for (LivingEntity c : cleaners) face(c, m);
        if (blink) {
            LivingEntity victim = cleaners.get(0);
            Vec3 at = victim.position().add(victim.getLookAngle().multiply(0.6, 0, 0.6));
            m.teleportTo(at.x, victim.getY(), at.z);
            l.playSound(null, victim.blockPosition(), SoundEvents.SKELETON_HURT, SoundSource.HOSTILE, 1.2F, 0.5F);
            victim.hurt(l.damageSources().mobAttack(m), Float.MAX_VALUE);
            pd.putInt("bp_clean_lost", pd.getInt("bp_clean_lost") + 1);
            say(l, m, "Both watchers blinked at once. A crack, and the cleaner is on the floor. \"EYES ON IT!\"");
        }
        boolean watched = !watchers.isEmpty() || !cleaners.isEmpty();
        if (watched) {
            m.setNoAi(true);
            m.setDeltaMovement(Vec3.ZERO);
            pd.putBoolean(FROZEN, true);
        } else if (pd.getBoolean(FROZEN)) {
            m.setNoAi(false);
            pd.remove(FROZEN);
        }
    }

    /** The cleaning begins (once a week, or when the Council approves it). */
    private static Long begin(ServerLevel l, Mob m) {
        long until = l.getGameTime() + CLEANING;
        CLEANING_UNTIL.put(m.getUUID(), until);
        say(l, m, "Cleaning protocol for SCP-173 is under way. A crew goes in with mops and buckets; the watchers don't take their eyes off it.");
        return until;
    }

    /** From the Council's approval: clean SCP-173's cells now. */
    public static int cleanNow(MinecraftServer srv) {
        int n = 0;
        for (Containment.Entry e : Containment.all(srv)) {
            if (e.status != Containment.HELD || !e.type.equals(Unleashed.SCP_173)) continue;
            Mob m = Containment.loaded(srv, e.id);
            if (m == null || CLEANING_UNTIL.containsKey(m.getUUID())) continue;
            begin((ServerLevel) m.level(), m);
            n++;
        }
        return n;
    }

    /** A D-Class from the block, put in place: watchers in opposite corners facing it, the crew anywhere. */
    private static LivingEntity dclass(ServerLevel l, Containment.Entry e, Mob m, Sites.Cell cell, int slot, String role) {
        var type = Unleashed.entity(Unleashed.DCLASS);
        if (type.isEmpty()) return null;
        Entity d = type.get().create(l);
        if (!(d instanceof LivingEntity le)) return null;
        double x = slot == 0 ? cell.x0 + 1.5 : slot == 1 ? cell.x1 - 0.5 : (cell.x0 + cell.x1) / 2.0 + 0.5;
        double z = slot == 0 ? cell.z0 + 1.5 : slot == 1 ? cell.z1 - 0.5 : (cell.z0 + cell.z1) / 2.0 + 0.5;
        le.moveTo(x, cell.y0 + 1, z, 0, 0);
        le.getPersistentData().putString(WATCH, m.getUUID().toString());
        le.getPersistentData().putString(ROLE, role);
        if (le instanceof Mob dm) { dm.setNoAi(true); dm.setPersistenceRequired(); }
        l.addFreshEntity(le);
        return le;
    }

    private static void face(LivingEntity w, Mob m) {
        Vec3 d = m.getEyePosition().subtract(w.getEyePosition());
        float yaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90), pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        w.setYRot(yaw); w.setYHeadRot(yaw); w.setYBodyRot(yaw); w.setXRot(pitch);
    }

    private static void say(ServerLevel l, Mob m, String s) {
        for (ServerPlayer q : l.players()) if (q.distanceToSqr(m) < 32 * 32) q.sendSystemMessage(Component.literal(s).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }

    private static void tellOwner(ServerLevel l, Containment.Entry e, String s) {
        for (Sites.Site st : Sites.get(l.getServer()).sites)
            if (st.name.equals(e.site)) { ServerPlayer p = Containment.player(l.getServer(), st.owner); if (p != null) Career.message(p, "Containment", s); }
    }
}
