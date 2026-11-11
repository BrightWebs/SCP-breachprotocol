package com.breachprotocol.career;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.compat.Unleashed;
import com.breachprotocol.compat.UnleashedApi;
import com.breachprotocol.entity.FoundationStaff;
import com.breachprotocol.entity.O5MemberEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.*;

/**
 * Dominion: what the Administrator, and above all SCP-Ω, can do to the living things around them.
 *
 * <p><b>Aura</b> (SCP-Ω): Unmasked (everything behaves as it would to anyone), Sovereign (no anomaly
 * will raise a hand to you; the default) or Dread (nothing living turns on you, and hostile things
 * back away).</p>
 *
 * <p><b>Commands</b>, given by holding the command key and looking at a creature: Calm (it will never
 * attack you again), Follow, Stay, Hunt (it goes after whatever you point at next, whatever it is),
 * Release (back to its own nature), and End (it dies; nothing is exempt for SCP-Ω). The Administrator
 * commands Foundation staff and anomalies, and may only end anomalies. Orders survive saving: they
 * live in the creature's own data.</p>
 *
 * <p><b>The implant</b>, abilities on the console: blink, a stasis pulse, revealing every anomaly
 * nearby, stilling everything around you, summoning the secretary (walking, or at once), and for
 * SCP-Ω, judgement on every hostile thing nearby.</p>
 */
@EventBusSubscriber(modid = BreachProtocol.MODID)
public final class Dominion {
    private Dominion() {}

    public static final int UNMASKED = 0, SOVEREIGN = 1, DREAD = 2;

    private static final String CALM = "bp_dom_calm", FOLLOW = "bp_dom_follow", STAY = "bp_dom_stay", HUNT = "bp_dom_hunt";

    /** 0 = none, 1 = the Administrator, 2 = SCP-Ω. */
    public static int level(ServerPlayer p) {
        if (Career.isAscended(p)) return 2;
        return Career.rank(p) == Rank.ADMINISTRATOR ? 1 : 0;
    }

    /** How anomalies treat you: 0 as anyone, 1 they leave you be (SCP-Ω's default), 2 they fear you. */
    public static int auraAnomalies(Player p) {
        CompoundTag t = p.getPersistentData();
        return t.contains("bp_aura_a") ? t.getInt("bp_aura_a") : t.contains("bp_aura") ? Math.min(1, t.getInt("bp_aura")) : SOVEREIGN;
    }

    /** How every other creature treats you: 0 as anyone (the default), 1 they leave you be, 2 they fear you. */
    public static int auraCreatures(Player p) {
        CompoundTag t = p.getPersistentData();
        return t.contains("bp_aura_c") ? t.getInt("bp_aura_c") : t.getInt("bp_aura") == DREAD ? DREAD : UNMASKED;
    }

    public static int aura(Player p, LivingEntity e) { return Unleashed.isAnomaly(e) ? auraAnomalies(p) : auraCreatures(p); }

    /** SCP-Ω's anomaly immunity (see CareerEvents) applies only when anomalies are told to leave them be. */
    public static boolean sovereign(Player p) { return auraAnomalies(p) != UNMASKED; }

    /**
     * The anomalies leave this one be: the Administrator or SCP-Ω, with anomalies told to leave them be (the
     * default; "as anyone" in the console's Temperament lets them come). SCP Unleashed's own targeting (every
     * anomaly that picks its prey itself: SCP-049's surgery, the statues, the hunters) is told too.
     */
    public static boolean spared(Player p) {
        return p != null && !p.isSpectator() && (Career.isAscended(p) || Career.rank(p) == Rank.ADMINISTRATOR) && sovereign(p);
    }

    // ================================================================== the console

    public static void action(ServerPlayer p, String action, int arg) {
        int lv = level(p);
        if (lv == 0) { p.displayClientMessage(Component.literal("Nothing answers you.").withStyle(ChatFormatting.DARK_GRAY), true); return; }
        String[] a = action.split(":");
        if (a.length < 2) return;
        switch (a[1]) {
            case "aura" -> {
                // dom:aura:a|c:n  (anomalies / everything else)
                if (a.length < 4) return;
                boolean anomalies = a[2].equals("a");
                if (!anomalies && lv < 2) return; // the Administrator chooses for the anomalies; the rest of the living is SCP-Ω's
                int n = Math.max(0, Math.min(2, Integer.parseInt(a[3])));
                p.getPersistentData().putInt(anomalies ? "bp_aura_a" : "bp_aura_c", n);
                CareerEvents.shield(p); // at once, not at the next second
                String what = anomalies ? "The anomalies" : "Every other living thing";
                String[] say = {what + " see you as one of them. Whatever wants to fight you, will.",
                        what + " remember what you are. None of them will raise a hand to you.",
                        what + " know what you are, and keep their distance."};
                p.displayClientMessage(Component.literal(say[n]).withStyle(ChatFormatting.LIGHT_PURPLE), true);
                p.serverLevel().sendParticles(n == DREAD ? ParticleTypes.SCULK_SOUL : n == SOVEREIGN ? ParticleTypes.END_ROD : ParticleTypes.ASH, p.getX(), p.getY() + 1, p.getZ(), 40, 0.8, 1, 0.8, 0.04);
                if (n != UNMASKED) for (Mob m : p.serverLevel().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(32), m -> m.getTarget() == p && turnsFrom(p, m))) m.setTarget(null);
            }
            case "calm", "follow", "stay", "cell", "release", "end" -> {
                Entity e = p.serverLevel().getEntity(arg);
                if (e instanceof LivingEntity le) order(p, le, a[1], null);
            }
            case "hunt" -> {
                if (a.length < 3) return;
                Entity e = p.serverLevel().getEntity(arg), v = p.serverLevel().getEntity(Integer.parseInt(a[2]));
                if (e instanceof LivingEntity le && v instanceof LivingEntity victim) order(p, le, "hunt", victim);
            }
            case "all" -> { if (a.length >= 3) all(p, a[2]); }
            default -> {}
        }
        Career.sync(p);
    }

    /** May this player command that creature at all? */
    static boolean commands(ServerPlayer p, LivingEntity e, String what) {
        if (e instanceof Player) return false;
        int lv = level(p);
        if (lv >= 2) return true;
        boolean ours = e instanceof FoundationStaff || e instanceof O5MemberEntity || Unleashed.isAnomaly(e);
        if (what.equals("end")) return Unleashed.isAnomaly(e);
        return ours;
    }

    private static String name(LivingEntity e) {
        return Unleashed.isAnomaly(e) ? Unleashed.designation(e) : e.getName().getString();
    }

    static void order(ServerPlayer p, LivingEntity e, String what, LivingEntity victim) {
        ServerLevel l = p.serverLevel();
        if (!commands(p, e, what)) {
            p.displayClientMessage(Component.literal(level(p) == 1 && what.equals("end") ? "Only anomalies can be ordered terminated." : "That is not yours to command.").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        CompoundTag t = e.getPersistentData();
        String me = p.getUUID().toString();
        switch (what) {
            case "calm" -> {
                t.putString(CALM, me);
                if (e instanceof Mob m) { m.setTarget(null); m.setLastHurtByMob(null); }
                if (e instanceof NeutralMob nm) nm.stopBeingAngry();
                l.sendParticles(ParticleTypes.HAPPY_VILLAGER, e.getX(), e.getY() + e.getBbHeight() + 0.3, e.getZ(), 10, 0.4, 0.3, 0.4, 0);
                tell(p, name(e) + " is calm. It won't raise a hand to you again.");
            }
            case "follow" -> {
                clear(t);
                t.putString(CALM, me);
                if (e instanceof FoundationStaff f) f.order("follow", p);
                else t.putString(FOLLOW, me);
                if (e instanceof Mob m) m.setTarget(null);
                l.sendParticles(ParticleTypes.END_ROD, e.getX(), e.getY() + e.getBbHeight() + 0.3, e.getZ(), 12, 0.3, 0.3, 0.3, 0.02);
                tell(p, name(e) + " falls in behind you.");
            }
            case "stay" -> {
                clear(t);
                t.putString(CALM, me);
                if (e instanceof FoundationStaff f) f.order("stay", p);
                else {
                    t.putLong(STAY, e.blockPosition().asLong());
                    if (e instanceof PathfinderMob pm) pm.restrictTo(e.blockPosition(), 2);
                    if (e instanceof Mob m) m.getNavigation().stop();
                }
                tell(p, name(e) + " stays where it is.");
            }
            case "hunt" -> {
                if (victim == null || victim == e || victim == p) return;
                clear(t);
                t.putString(CALM, me);
                t.putUUID(HUNT, victim.getUUID());
                t.putString(FOLLOW, me); // and when it's done, it comes back to you
                if (e instanceof Mob m) m.setTarget(victim);
                l.sendParticles(ParticleTypes.ANGRY_VILLAGER, e.getX(), e.getY() + e.getBbHeight() + 0.3, e.getZ(), 6, 0.3, 0.2, 0.3, 0);
                l.sendParticles(ParticleTypes.SCULK_SOUL, victim.getX(), victim.getY() + 1, victim.getZ(), 14, 0.4, 0.6, 0.4, 0.02);
                tell(p, name(e) + " turns towards " + name(victim) + ".");
            }
            case "cell" -> {
                if (e instanceof Mob mob && Walkback.start(p.server, mob)) tell(p, name(e) + " turns and walks back to its cell.");
                else tell(p, name(e) + " has no cell of its own to go back to.");
                return;
            }
            case "release" -> {
                clear(t);
                t.remove(CALM);
                if (e instanceof FoundationStaff f) f.order("post", p);
                if (e instanceof PathfinderMob pm) pm.clearRestriction();
                tell(p, name(e) + " is released to its own nature.");
            }
            case "end" -> {
                end(p, e);
                return;
            }
            default -> { return; }
        }
        ACTIVE.add(e.getUUID());
    }

    private static void clear(CompoundTag t) { t.remove(FOLLOW); t.remove(STAY); t.remove(HUNT); }

    /** It dies. No exemptions for SCP-Ω, whatever mod it came from. */
    static void end(ServerPlayer p, LivingEntity e) {
        ServerLevel l = p.serverLevel();
        String n = name(e);
        Vec3 at = e.position();
        l.sendParticles(ParticleTypes.SOUL, at.x, at.y + e.getBbHeight() / 2, at.z, 30, e.getBbWidth() / 2, e.getBbHeight() / 2, e.getBbWidth() / 2, 0.05);
        l.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y + e.getBbHeight() / 2, at.z, 40, e.getBbWidth() / 2, e.getBbHeight() / 2, e.getBbWidth() / 2, 0.2);
        l.playSound(null, at.x, at.y, at.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 0.6F, 1.6F);
        // the player's kill: it counts as theirs (stats, loot, other mods' rewards)
        PowerKills.credit(p, e);
        var src = PowerKills.as(p, net.minecraft.world.damagesource.DamageTypes.GENERIC_KILL);
        e.hurt(src, Float.MAX_VALUE);
        if (e.isAlive()) { e.setHealth(0); e.die(src); }
        if (e.isAlive() && !(e instanceof net.minecraft.world.entity.player.Player)) e.discard();
        tell(p, n + " is no more.");
    }

    private static void tell(ServerPlayer p, String s) {
        p.displayClientMessage(Component.literal(s).withStyle(ChatFormatting.LIGHT_PURPLE), true);
    }

    // ================================================================== orders for everything around you

    static void all(ServerPlayer p, String what) {
        ServerLevel l = p.serverLevel();
        List<LivingEntity> near = l.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(32), e -> e != p && !(e instanceof Player) && e.isAlive());
        int n = 0;
        switch (what) {
            case "calm" -> {
                for (LivingEntity e : near) if (e instanceof Mob m && (m.getTarget() == p || e instanceof Enemy || Unleashed.isAnomaly(e)) && commands(p, e, "calm")) {
                    e.getPersistentData().putString(CALM, p.getUUID().toString());
                    m.setTarget(null);
                    if (e instanceof NeutralMob nm) nm.stopBeingAngry();
                    ACTIVE.add(e.getUUID());
                    n++;
                }
                l.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 80, 4, 1, 4, 0.02);
                tell(p, n == 0 ? "Nothing here meant you harm." : n + " creature" + (n == 1 ? "" : "s") + " fall" + (n == 1 ? "s" : "") + " still.");
            }
            case "recall" -> {
                for (ServerLevel lv : p.server.getAllLevels())
                    for (Entity e : lv.getAllEntities())
                        if (e instanceof Mob m && p.getUUID().toString().equals(m.getPersistentData().getString(FOLLOW))) {
                            if (lv == l) m.teleportTo(p.getX() + (l.random.nextDouble() - 0.5) * 3, p.getY(), p.getZ() + (l.random.nextDouble() - 0.5) * 3);
                            n++;
                        }
                tell(p, n == 0 ? "Nothing follows you." : n + " follower" + (n == 1 ? "" : "s") + " called to your side.");
            }
            case "release" -> {
                for (ServerLevel lv : p.server.getAllLevels())
                    for (Entity e : lv.getAllEntities())
                        if (e instanceof LivingEntity le && p.getUUID().toString().equals(le.getPersistentData().getString(CALM))) {
                            clear(le.getPersistentData());
                            le.getPersistentData().remove(CALM);
                            if (le instanceof PathfinderMob pm) pm.clearRestriction();
                            n++;
                        }
                tell(p, n == 0 ? "Nothing is under your command." : n + " released to their own nature.");
            }
            default -> {}
        }
    }

    /** Your secretary, wherever she is: walking to you, or here at once. */
    public static boolean secretary(ServerPlayer p, boolean now) {
        FoundationStaff sec = null;
        for (ServerLevel lv : p.server.getAllLevels())
            for (Entity e : lv.getAllEntities())
                if (e instanceof FoundationStaff f && f.isAlive() && f.role() == FoundationStaff.Role.SECRETARY) { sec = f; break; }
        if (sec == null) {
            p.displayClientMessage(Component.literal(Secretary.has(p) ? "Your secretary is out of reach (her floor isn't loaded)." : "You don't have a secretary yet.").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        String her = sec.getName().getString();
        if (now || sec.level() != p.level() || sec.distanceTo(p) > 160) {
            if (sec.level() != p.level()) {
                p.displayClientMessage(Component.literal(her + " is in another dimension. She can't come through.").withStyle(ChatFormatting.GRAY), true);
                return false;
            }
            p.serverLevel().sendParticles(ParticleTypes.REVERSE_PORTAL, sec.getX(), sec.getY() + 1, sec.getZ(), 30, 0.3, 0.8, 0.3, 0.1);
            Vec3 at = p.position().add(p.getLookAngle().multiply(1.5, 0, 1.5));
            sec.teleportTo(at.x, p.getY(), at.z);
            sec.getLookControl().setLookAt(p);
            sec.order("follow", p);
            p.serverLevel().sendParticles(ParticleTypes.PORTAL, at.x, p.getY() + 1, at.z, 40, 0.3, 0.8, 0.3, 0.5);
            p.sendSystemMessage(Component.literal(her + ": " + pick(p, "You called, " + Career.address(p) + "?", "I'm here, " + Career.address(p) + ". What do you need?", "Right here. I brought my notes.")).withStyle(ChatFormatting.LIGHT_PURPLE));
        } else {
            sec.travel(p.blockPosition(), false);
            sec.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 60, 1, false, false)); // she hurries
            p.sendSystemMessage(Component.literal(her + " is on her way to you.").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
        return true;
    }

    private static String pick(ServerPlayer p, String... s) { return s[p.getRandom().nextInt(s.length)]; }

    // ================================================================== keeping orders

    /** Creatures carrying an order: checked twice a second. */
    private static final Set<UUID> ACTIVE = new HashSet<>();

    @SubscribeEvent
    public static void joined(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        CompoundTag t = event.getEntity().getPersistentData();
        if (t.contains(CALM) || t.contains(FOLLOW) || t.contains(STAY) || t.contains(HUNT)) ACTIVE.add(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        MinecraftServer srv = event.getServer();
        if (srv.getTickCount() % 10 != 0) return;
        if (!ACTIVE.isEmpty()) {
            for (ServerLevel l : srv.getAllLevels()) {
                for (Iterator<UUID> it = ACTIVE.iterator(); it.hasNext(); ) {
                    Entity en = l.getEntity(it.next());
                    if (en == null) continue;
                    if (!(en instanceof Mob m) || !m.isAlive()) { it.remove(); continue; }
                    keep(l, m);
                }
            }
        }
        // Dread: hostile things back away from SCP-Ω
        if (srv.getTickCount() % 20 == 0) for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
            if (level(p) < 2 || auraAnomalies(p) != DREAD && auraCreatures(p) != DREAD) continue;
            ServerLevel l = p.serverLevel();
            for (Mob m : l.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(12), m -> aura(p, m) == DREAD && (m instanceof Enemy || Unleashed.isAnomaly(m)) && !m.getPersistentData().contains(FOLLOW))) {
                if (m.getTarget() == p) m.setTarget(null);
                Vec3 away = m.position().subtract(p.position()).normalize().scale(8).add(m.position());
                m.getNavigation().moveTo(away.x, away.y, away.z, 1.3);
            }
            if (p.getRandom().nextInt(3) == 0) l.sendParticles(ParticleTypes.SCULK_SOUL, p.getX(), p.getY() + 0.2, p.getZ(), 2, 0.6, 0.1, 0.6, 0.01);
        }
    }

    /**
     * A hunt, carried out the way this creature kills: SCP-096 is provoked (its crying, then the charge),
     * SCP-173 snaps a neck the moment it's on them, SCP-106 drags them into the pocket dimension; anything
     * else is walked onto its prey and strikes with its own attack (or, with none, with its weight).
     */
    private static void hunt(ServerLevel l, Mob m, LivingEntity victim) {
        String id = Unleashed.idOf(m) == null ? "" : Unleashed.idOf(m).getPath();
        double reach = Math.pow(m.getBbWidth() / 2 + victim.getBbWidth() / 2 + 1.6, 2);
        double d = m.distanceToSqr(victim);
        if (UnleashedApi.isShyGuy(m)) {
            // it has to have "seen" them: then its own rage does the rest
            Object keys = UnleashedApi.call(UnleashedApi.method("scp096.Scp096ShyGuy", "targetKeys"), m);
            if (!(keys instanceof java.util.Set<?> set) || !set.contains(victim.getUUID()))
                UnleashedApi.call(UnleashedApi.method("scp096.Scp096ShyGuy", "provoke", ServerLevel.class, LivingEntity.class), m, l, victim);
            return;
        }
        m.setTarget(victim);
        if (d > reach) { m.getNavigation().moveTo(victim, 1.35); return; }
        if (m.tickCount % 20 >= 10) return; // one blow a second
        if (id.startsWith("scp173")) {
            l.playSound(null, victim.blockPosition(), SoundEvents.SKELETON_HURT, SoundSource.HOSTILE, 1.3F, 0.5F);
            victim.hurt(l.damageSources().mobAttack(m), Float.MAX_VALUE);
        } else if (id.startsWith("scp106")) {
            var pass = UnleashedApi.method("scp106.Scp106Doorways", "pass", ServerLevel.class, BlockPos.class, LivingEntity.class);
            if (pass != null) UnleashedApi.call(pass, null, l, victim.blockPosition(), victim);
            if (victim.level() == l && victim.isAlive()) victim.hurt(l.damageSources().mobAttack(m), 8F);
        } else if (m.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
            m.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            m.doHurtTarget(victim);
        } else {
            m.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            victim.hurt(l.damageSources().mobAttack(m), 4F);
        }
    }

    private static void keep(ServerLevel l, Mob m) {
        CompoundTag t = m.getPersistentData();
        // the hunt comes first
        if (t.hasUUID(HUNT)) {
            Entity v = l.getEntity(t.getUUID(HUNT));
            if (!(v instanceof LivingEntity victim) || !victim.isAlive()) {
                t.remove(HUNT);
                ServerPlayer master = Containment.player(l.getServer(), t.getString(FOLLOW));
                if (master != null) tell(master, name(m) + (v == null || v.isAlive() ? " lost its prey" : " got its prey") + ", and comes back to you.");
            }
            else {
                hunt(l, m, victim);
                return;
            }
        }
        if (t.contains(STAY)) {
            BlockPos at = BlockPos.of(t.getLong(STAY));
            if (m.blockPosition().distSqr(at) > 9) m.getNavigation().moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 1.0);
            return;
        }
        String f = t.getString(FOLLOW);
        if (!f.isEmpty()) {
            ServerPlayer master = Containment.player(l.getServer(), f);
            if (master == null || master.level() != l) return;
            if (m.getTarget() == master) m.setTarget(null);
            double d = m.distanceToSqr(master);
            if (d > 32 * 32) m.teleportTo(master.getX() + (l.random.nextDouble() - 0.5) * 3, master.getY(), master.getZ() + (l.random.nextDouble() - 0.5) * 3);
            else if (d > 16) m.getNavigation().moveTo(master, 1.2);
        }
    }

    /** A creature you've calmed, or that follows you, never turns on you; with Dread, nothing living does. */
    @SubscribeEvent
    public static void target(LivingChangeTargetEvent event) {
        if (!(event.getNewAboutToBeSetTarget() instanceof ServerPlayer p)) return;
        LivingEntity e = event.getEntity();
        if (p.getUUID().toString().equals(e.getPersistentData().getString(CALM)) || turnsFrom(p, e)) event.setCanceled(true);
    }

    /** With an aura, things leave SCP-Ω be (anomalies are handled by CareerEvents too). */
    private static boolean turnsFrom(ServerPlayer p, LivingEntity e) {
        return level(p) >= 2 && aura(p, e) != UNMASKED;
    }

    // ================================================================== the client's view

    public static void addToSync(ServerPlayer p, CompoundTag t) {
        t.putInt("dom", level(p));
        t.putInt("auraA", auraAnomalies(p));
        t.putInt("auraC", auraCreatures(p));
        Powers.addToSync(p, t);
    }
}
