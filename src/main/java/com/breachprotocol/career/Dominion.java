package com.breachprotocol.career;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.compat.Unleashed;
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

    public static int aura(Player p) {
        CompoundTag t = p.getPersistentData();
        return t.contains("bp_aura") ? t.getInt("bp_aura") : SOVEREIGN;
    }

    /** SCP-Ω's anomaly immunity (see CareerEvents) applies only when not unmasked. */
    public static boolean sovereign(Player p) { return aura(p) != UNMASKED; }

    // ================================================================== the console

    public static void action(ServerPlayer p, String action, int arg) {
        int lv = level(p);
        if (lv == 0) { p.displayClientMessage(Component.literal("Nothing answers you.").withStyle(ChatFormatting.DARK_GRAY), true); return; }
        String[] a = action.split(":");
        if (a.length < 2) return;
        switch (a[1]) {
            case "aura" -> {
                if (lv < 2 || a.length < 3) return;
                int n = Math.max(0, Math.min(2, Integer.parseInt(a[2])));
                p.getPersistentData().putInt("bp_aura", n);
                String[] say = {"You let them see you as one of them. Whatever wants to fight you, will.",
                        "The anomalies remember what you are. None of them will raise a hand to you.",
                        "Everything living knows what you are. It keeps its distance."};
                p.displayClientMessage(Component.literal(say[n]).withStyle(ChatFormatting.LIGHT_PURPLE), true);
                p.serverLevel().sendParticles(n == DREAD ? ParticleTypes.SCULK_SOUL : n == SOVEREIGN ? ParticleTypes.END_ROD : ParticleTypes.ASH, p.getX(), p.getY() + 1, p.getZ(), 40, 0.8, 1, 0.8, 0.04);
                if (n != UNMASKED) for (Mob m : p.serverLevel().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(32), m -> m.getTarget() == p && turnsFrom(p, m))) m.setTarget(null);
            }
            case "calm", "follow", "stay", "release", "end" -> {
                Entity e = p.serverLevel().getEntity(arg);
                if (e instanceof LivingEntity le) order(p, le, a[1], null);
            }
            case "hunt" -> {
                if (a.length < 3) return;
                Entity e = p.serverLevel().getEntity(arg), v = p.serverLevel().getEntity(Integer.parseInt(a[2]));
                if (e instanceof LivingEntity le && v instanceof LivingEntity victim) order(p, le, "hunt", victim);
            }
            case "all" -> { if (a.length >= 3) all(p, a[2]); }
            case "ability" -> { if (a.length >= 3) ability(p, a[2]); }
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
        e.hurt(l.damageSources().genericKill(), Float.MAX_VALUE);
        if (e.isAlive()) { e.setHealth(0); e.die(l.damageSources().genericKill()); }
        if (e.isAlive()) e.discard();
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

    // ================================================================== the implant

    public record Ability(String id, String name, String about, int minLevel, int cooldown) {}

    public static final List<Ability> ABILITIES = List.of(
            new Ability("call", "Call your secretary", "She walks to you (and takes the lift), as fast as she can.", 1, 20 * 30),
            new Ability("summon", "Summon your secretary", "She's at your side at once.", 1, 20 * 60),
            new Ability("blink", "Blink", "Step to wherever you're looking, up to 64 blocks.", 1, 20 * 8),
            new Ability("reveal", "Reveal", "Every anomaly within 96 blocks glows for 30 seconds.", 1, 20 * 20),
            new Ability("stasis", "Stasis pulse", "Everything within 16 blocks is held still for 10 seconds.", 1, 20 * 30),
            new Ability("still", "Still the room", "Everything within 32 blocks that means you harm calms down.", 1, 20 * 15),
            new Ability("judgement", "Judgement", "Every hostile creature within 24 blocks dies.", 2, 20 * 60));

    static void ability(ServerPlayer p, String id) {
        Ability ab = ABILITIES.stream().filter(x -> x.id().equals(id)).findFirst().orElse(null);
        if (ab == null || level(p) < ab.minLevel()) return;
        ServerLevel l = p.serverLevel();
        long now = l.getGameTime(), ready = p.getPersistentData().getLong("bp_cd_" + id);
        if (now < ready) { p.displayClientMessage(Component.literal(ab.name() + " is recharging: " + (ready - now) / 20 + "s.").withStyle(ChatFormatting.GRAY), true); return; }
        boolean used = switch (id) {
            case "call", "summon" -> secretary(p, id.equals("summon"));
            case "blink" -> {
                HitResult hit = p.pick(64, 0, false);
                if (hit.getType() == HitResult.Type.MISS) yield false;
                Vec3 to = hit.getLocation().subtract(p.getLookAngle().scale(0.6));
                l.sendParticles(ParticleTypes.REVERSE_PORTAL, p.getX(), p.getY() + 1, p.getZ(), 40, 0.3, 0.8, 0.3, 0.1);
                p.teleportTo(to.x, Math.max(to.y, l.getMinBuildHeight()), to.z);
                p.fallDistance = 0;
                l.sendParticles(ParticleTypes.PORTAL, to.x, to.y + 1, to.z, 50, 0.3, 0.8, 0.3, 0.6);
                l.playSound(null, to.x, to.y, to.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.7F, 1.4F);
                yield true;
            }
            case "reveal" -> {
                int n = 0;
                for (Mob m : l.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(96), Unleashed::isAnomaly)) {
                    m.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20 * 30, 0, false, false));
                    n++;
                }
                l.sendParticles(ParticleTypes.SONIC_BOOM, p.getX(), p.getY() + 1, p.getZ(), 1, 0, 0, 0, 0);
                tell(p, n == 0 ? "Nothing anomalous within reach." : n + " anomal" + (n == 1 ? "y" : "ies") + " revealed.");
                yield true;
            }
            case "stasis" -> {
                for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(16), e -> e != p && !(e instanceof Player))) {
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 9, false, false));
                    e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 4, false, false));
                    if (e instanceof Mob m) { m.setTarget(null); m.getNavigation().stop(); }
                    l.sendParticles(ParticleTypes.SNOWFLAKE, e.getX(), e.getY() + e.getBbHeight() / 2, e.getZ(), 12, 0.3, 0.5, 0.3, 0.01);
                }
                l.playSound(null, p.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1F, 0.6F);
                yield true;
            }
            case "still" -> { all(p, "calm"); yield true; }
            case "judgement" -> {
                int n = 0;
                for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(24), e -> e instanceof Enemy || e instanceof Mob m && m.getTarget() == p)) {
                    if (e instanceof Player) continue;
                    l.sendParticles(ParticleTypes.END_ROD, e.getX(), e.getY() + 8, e.getZ(), 30, 0.1, 4, 0.1, 0.0);
                    end(p, e);
                    n++;
                }
                tell(p, n == 0 ? "Nothing here deserved it." : "Judgement. " + n + " fell.");
                yield true;
            }
            default -> false;
        };
        if (used) p.getPersistentData().putLong("bp_cd_" + id, now + ab.cooldown());
    }

    /** Your secretary, wherever she is: walking to you, or here at once. */
    private static boolean secretary(ServerPlayer p, boolean now) {
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
            if (level(p) < 2 || aura(p) != DREAD) continue;
            ServerLevel l = p.serverLevel();
            for (Mob m : l.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(12), m -> m instanceof Enemy && !m.getPersistentData().contains(FOLLOW))) {
                if (m.getTarget() == p) m.setTarget(null);
                Vec3 away = m.position().subtract(p.position()).normalize().scale(8).add(m.position());
                m.getNavigation().moveTo(away.x, away.y, away.z, 1.3);
            }
            if (p.getRandom().nextInt(3) == 0) l.sendParticles(ParticleTypes.SCULK_SOUL, p.getX(), p.getY() + 0.2, p.getZ(), 2, 0.6, 0.1, 0.6, 0.01);
        }
    }

    private static void keep(ServerLevel l, Mob m) {
        CompoundTag t = m.getPersistentData();
        // the hunt comes first
        if (t.hasUUID(HUNT)) {
            Entity v = l.getEntity(t.getUUID(HUNT));
            if (!(v instanceof LivingEntity victim) || !victim.isAlive()) { t.remove(HUNT); }
            else {
                m.setTarget(victim);
                double d = m.distanceToSqr(victim);
                if (d > 4) m.getNavigation().moveTo(victim, 1.3);
                else if (m.getAttribute(Attributes.ATTACK_DAMAGE) == null && m.tickCount % 20 < 10) {
                    // creatures with no attack of their own still go for it
                    victim.hurt(l.damageSources().mobAttack(m), 4F);
                    m.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                }
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

    private static boolean turnsFrom(ServerPlayer p, LivingEntity e) {
        return level(p) >= 2 && aura(p) == DREAD;
    }

    // ================================================================== the client's view

    public static void addToSync(ServerPlayer p, CompoundTag t) {
        t.putInt("dom", level(p));
        t.putInt("aura", aura(p));
        CompoundTag cd = new CompoundTag();
        for (Ability a : ABILITIES) cd.putLong(a.id(), p.getPersistentData().getLong("bp_cd_" + a.id()));
        t.put("cd", cd);
    }
}
