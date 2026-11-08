package com.breachprotocol.career;

import com.breachprotocol.compat.Unleashed;
import com.breachprotocol.compat.UnleashedApi;
import com.breachprotocol.entity.FoundationStaff;
import com.breachprotocol.entity.O5MemberEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Powers: the abilities of SCP Unleashed's own anomalies, taken by the Administrator's implant (and,
 * all of them, by SCP-Ω). Where SCP Unleashed exposes the real thing it's used as-is (the Gate
 * Guardian's smite and repel, SCP-106's pocket dimension and corrosion, the ZENITH orbital strike,
 * SCP-999 itself); otherwise the power is built from that anomaly's own particles and sounds.
 *
 * <p>Each has a range, chosen per power on the client (Powers tab, or holding a power key and
 * scrolling with Shift), clamped here to its limits. Powers are cast from two power slots with keys
 * of the player's choosing (see client.PowerKeys).</p>
 */
@EventBusSubscriber(modid = com.breachprotocol.BreachProtocol.MODID)
public final class Powers {
    private Powers() {}

    /** How a power picks what it acts on. */
    public enum Aim { SELF, AROUND, LOOK_POINT, LOOK_TARGET }

    public record Power(String id, String name, String source, String glyph, int colour, String about, int minLevel, int cooldown,
                        int rMin, int rMax, int rDef, Aim aim) {}

    public static final List<Power> ALL = List.of(
            new Power("smite", "Smite", "SCP-001-1, the Gate Guardian", "☄", 0xFFB040, "Strike down every hostile thing around you, the way the Guardian burns those who come too close to the Gate.", 2, 20 * 12, 4, 24, 10, Aim.AROUND),
            new Power("repel", "Repel", "SCP-001-1, the Gate Guardian", "↯", 0xFFD070, "Hurl everything hostile around you back, the way the Guardian keeps the unworthy from the Gate. Left on, it's a shield nothing hostile gets through.", 1, 20 * 6, 3, 16, 16, Aim.AROUND),
            new Power("pocket", "The Pocket", "SCP-106, the Old Man", "◌", 0x6A8A40, "Drag the creature you're looking at down into SCP-106's pocket dimension.", 2, 20 * 20, 4, 32, 16, Aim.LOOK_TARGET),
            new Power("corrode", "Corrosion", "SCP-106, the Old Man", "☣", 0x5A7A30, "Black corrosion spreads under everything hostile around you, rotting it where it stands.", 2, 20 * 14, 3, 14, 7, Aim.AROUND),
            new Power("orbital", "Orbital Strike", "ZENITH satellite", "⊕", 0x60C8FF, "Call the ZENITH satellite down on the spot you're looking at.", 2, 20 * 45, 16, 160, 64, Aim.LOOK_POINT),
            new Power("roar", "Roar", "SCP-682, the Hard-to-Destroy Reptile", "◉", 0xC05030, "A roar that throws everything hostile around you off its feet and leaves it staggering.", 2, 20 * 10, 4, 20, 10, Aim.AROUND),
            new Power("scream", "Scream", "SCP-096, the Shy Guy", "☢", 0xE0E0E0, "096's scream. Everything hostile that hears it runs, and doesn't stop.", 2, 20 * 20, 6, 32, 16, Aim.AROUND),
            new Power("immolate", "Immolate", "SCP-457, the Burning Man", "♨", 0xFF7A20, "Everything hostile around you catches fire from the inside.", 2, 20 * 12, 3, 16, 8, Aim.AROUND),
            new Power("tesla", "Tesla Arc", "Tesla gate", "⚡", 0x80D8FF, "Lightning leaps from you to the nearest hostile things, more of them the further it reaches.", 1, 20 * 5, 4, 24, 12, Aim.AROUND),
            new Power("embrace", "Embrace", "SCP-999, the Tickle Monster", "❤", 0xFFA040, "Joy: you, your people and anything that follows you are healed and steadied.", 1, 20 * 30, 4, 24, 10, Aim.AROUND),
            new Power("stasis", "Stasis Pulse", "Stasis pod", "❄", 0xA0E8FF, "Everything hostile around you is held still for ten seconds.", 1, 20 * 30, 4, 24, 12, Aim.AROUND),
            new Power("reveal", "Reveal", "The registry", "◎", 0xC8A0FF, "Every anomaly within reach glows through walls for thirty seconds.", 1, 20 * 20, 16, 128, 96, Aim.AROUND),
            new Power("blink", "Blink", "SCP-173, the Sculpture", "✸", 0xC0C0C0, "Step to wherever you're looking. You don't cross the space between.", 1, 20 * 6, 8, 64, 32, Aim.LOOK_POINT),
            new Power("still", "Still", "The Administrator", "☮", 0x7FE0A0, "Everything around you that means you harm calms down, for good.", 1, 20 * 15, 8, 48, 24, Aim.AROUND),
            new Power("judgement", "Judgement", "SCP-Ω", "☠", 0xFF4040, "Every hostile creature within reach dies.", 2, 20 * 60, 8, 48, 24, Aim.AROUND),
            new Power("call", "Call Secretary", "Your office", "☎", 0xF0A8C8, "She hurries to you on foot, taking the lift.", 1, 20 * 30, 0, 0, 0, Aim.SELF),
            new Power("summon", "Summon Secretary", "Your office", "✉", 0xF0A8C8, "She's at your side at once.", 1, 20 * 60, 0, 0, 0, Aim.SELF));

    public static Power get(String id) {
        for (Power p : ALL) if (p.id().equals(id)) return p;
        return null;
    }

    // ================================================================== casting

    /** Powers that can be left on: they fire by themselves whenever they're ready and something hostile is in range. */
    public static boolean automatic(Power pw) {
        return switch (pw.id()) { case "pocket", "orbital", "blink", "call", "summon" -> false; default -> true; };
    }

    /** How often an automatic power fires. Repel, left on, is a shield: it keeps everything hostile out of its circle. */
    private static int autoEvery(Power pw) { return pw.id().equals("repel") ? 10 : 20; }

    private static final String AUTO = "bp_auto";

    public static void cast(ServerPlayer p, String id, int range) {
        Power pw = get(id);
        if (pw == null || Dominion.level(p) < pw.minLevel()) return;
        ServerLevel l = p.serverLevel();
        int r = clamp(pw, range);
        // by hand: everything hostile in range, and whatever you're looking at, spared or not
        List<LivingEntity> tg = pw.aim() == Aim.AROUND ? Targets.around(p, r, looked(p, Math.max(r, 8))) : List.of();
        // no recharge: the powers are yours to use as often as you like
        fire(p, l, pw, r, tg, false);
    }

    private static int clamp(Power pw, int range) { return pw.rMax() == 0 ? 0 : Math.max(pw.rMin(), Math.min(pw.rMax(), range)); }

    private static boolean quiet;

    private static boolean fire(ServerPlayer p, ServerLevel l, Power pw, int r, List<LivingEntity> tg, boolean auto) {
        quiet = auto;
        try {
            return run(p, l, pw, r, tg, auto);
        } catch (Throwable t) {
            com.breachprotocol.BreachProtocol.LOGGER.warn("Power {} failed: {}", pw.id(), t.toString());
            return false;
        } finally { quiet = false; }
    }

    /** "pow:auto:id:1:range" turns a power on (or retunes its range), "pow:auto:id:0" off. */
    public static void setAuto(ServerPlayer p, String[] a) {
        Power pw = a.length >= 4 ? get(a[2]) : null;
        if (pw == null || !automatic(pw) || Dominion.level(p) < pw.minLevel()) return;
        CompoundTag t = p.getPersistentData().getCompound(AUTO);
        if ("1".equals(a[3])) {
            int r = clamp(pw, a.length >= 5 ? parse(a[4], pw.rDef()) : pw.rDef());
            boolean was = t.contains(pw.id());
            t.putInt(pw.id(), r);
            if (!was) note(p, pw.name() + " is automatic" + (pw.rMax() > 0 ? " within " + r + " blocks." : "."));
        } else if (t.contains(pw.id())) {
            t.remove(pw.id());
            note(p, pw.name() + " is by hand again.");
        }
        p.getPersistentData().put(AUTO, t);
        Career.sync(p);
    }

    private static int parse(String s, int d) { try { return Integer.parseInt(s); } catch (NumberFormatException e) { return d; } }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        MinecraftServer srv = event.getServer();
        if (srv.getTickCount() % 5 != 0) return;
        for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
            CompoundTag on = p.getPersistentData().getCompound(AUTO);
            if (on.isEmpty() || !p.isAlive() || p.isSpectator()) continue;
            int lvl = Dominion.level(p);
            if (lvl < 1) continue;
            ServerLevel l = p.serverLevel();
            long now = l.getGameTime();
            boolean changed = false;
            for (String id : on.getAllKeys()) {
                Power pw = get(id);
                if (pw == null || lvl < pw.minLevel()) continue;
                if (now < p.getPersistentData().getLong("bp_pcd_" + id)) continue;
                int r = clamp(pw, on.getInt(id));
                List<LivingEntity> tg = Targets.around(p, r, null);
                boolean due = switch (id) {
                    case "embrace" -> p.getHealth() < p.getMaxHealth() * 0.6F;
                    case "reveal" -> !l.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(r), m -> Unleashed.isAnomaly(m) && !m.hasEffect(MobEffects.GLOWING)).isEmpty();
                    default -> !tg.isEmpty();
                };
                if (!due || !fire(p, l, pw, r, tg, true)) continue;
                p.getPersistentData().putLong("bp_pcd_" + id, now + autoEvery(pw));

            }
            if (changed) Career.sync(p);
        }
    }

    private static boolean run(ServerPlayer p, ServerLevel l, Power pw, int r, List<LivingEntity> tg, boolean auto) {
        Vec3 me = p.position();
        if (pw.aim() == Aim.AROUND && tg.isEmpty() && !pw.id().equals("embrace") && !pw.id().equals("reveal")) {
            note(p, "Nothing hostile within " + r + " blocks. Look at something to use it on that.");
            return false;
        }
        switch (pw.id()) {
            case "smite" -> {
                List<LivingEntity> f = tg;
                Object src = UnleashedApi.call(UnleashedApi.method("scp001gate.Scp001GateStrike", "source", ServerLevel.class, Entity.class), null, l, p);
                for (LivingEntity e : f) {
                    Object hit = src instanceof DamageSource ds ? UnleashedApi.call(UnleashedApi.method("scp001gate.Scp001GateStrike", "smite", ServerLevel.class, Entity.class, DamageSource.class), null, l, e, ds) : null;
                    if (!(hit instanceof Integer n) || n == 0 || e.isAlive()) {
                        // the Guardian's own strike spares some things; the implant doesn't
                        UnleashedApi.call(UnleashedApi.method("scp001gate.Scp001GateStrike", "burn", ServerLevel.class, Entity.class), null, l, e);
                        e.hurt(src instanceof DamageSource ds ? ds : l.damageSources().magic(), 40F);
                    }
                    l.sendParticles(ParticleTypes.END_ROD, e.getX(), e.getY() + 6, e.getZ(), 24, 0.1, 3, 0.1, 0);
                }
                UnleashedApi.call(UnleashedApi.method("scp001gate.Scp001GateStrike", "burst", ServerLevel.class, Vec3.class, double.class), null, l, me.add(0, 1, 0), (double) Math.min(r, 8));
                sound(l, p, "scp001_gate.strike_impact", SoundEvents.LIGHTNING_BOLT_THUNDER, 1F);
                note(p, f.isEmpty() ? "The strike falls on nothing." : "Smitten: " + f.size() + ".");
                return true;
            }
            case "repel" -> {
                List<LivingEntity> all = tg;
                var shove = UnleashedApi.method("scp001gate.Scp001GateRepel", "shove", Entity.class, BlockPos.class);
                var flare = UnleashedApi.method("scp001gate.Scp001GateRepel", "flare", ServerLevel.class, Vec3.class, Vec3.class);
                for (LivingEntity e : all) {
                    if (shove != null) UnleashedApi.call(shove, null, e, p.blockPosition());
                    Vec3 push = e.position().subtract(me).normalize().scale(1.6 + (r - e.distanceTo(p)) * 0.12);
                    e.push(push.x, 0.5, push.z);
                    e.hurtMarked = true;
                    if (flare != null) UnleashedApi.call(flare, null, l, me.add(0, 1, 0), e.position().add(0, 1, 0));
                }
                if (auto) sound(l, p, "scp001_gate.warn", SoundEvents.WARDEN_SONIC_BOOM, 0.25F);
                else {
                    ring(l, me, r, ParticleTypes.FLAME);
                    sound(l, p, "scp001_gate.warn", SoundEvents.WARDEN_SONIC_BOOM, 0.8F);
                }
                return true;
            }
            case "pocket" -> {
                LivingEntity t = looked(p, r);
                if (t == null) { note(p, "Look at what you want taken."); return false; }
                UnleashedApi.call(UnleashedApi.method("scp106.Scp106Effects", "pool", ServerLevel.class, Vec3.class), null, l, t.position());
                sound(l, t, "scp106_sink", SoundEvents.SCULK_BLOCK_SPREAD, 1F);
                var pass = UnleashedApi.method("scp106.Scp106Doorways", "pass", ServerLevel.class, BlockPos.class, LivingEntity.class);
                if (pass != null) UnleashedApi.call(pass, null, l, t.blockPosition(), t);
                if (t.level() == l && t.isAlive()) {
                    // SCP-106 didn't take it: the corrosion does
                    t.addEffect(new MobEffectInstance(MobEffects.WITHER, 20 * 8, 3));
                    t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 * 8, 4));
                }
                note(p, t.getName().getString() + " sinks into the floor.");
                return true;
            }
            case "corrode" -> {
                var pool = UnleashedApi.method("scp106.Scp106Effects", "pool", ServerLevel.class, Vec3.class);
                List<LivingEntity> f = tg;
                for (LivingEntity e : f) {
                    if (pool != null) UnleashedApi.call(pool, null, l, e.position());
                    e.addEffect(new MobEffectInstance(MobEffects.WITHER, 20 * 6, 2));
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 * 6, 3));
                    particles(l, "scp106_goo", ParticleTypes.SQUID_INK, e.position().add(0, 0.3, 0), 20, e.getBbWidth() / 2, 0.2);
                }
                sound(l, p, "scp106_corrode", SoundEvents.SCULK_BLOCK_SPREAD, 1F);
                note(p, f.isEmpty() ? "The floor darkens, then clears." : f.size() + " caught in the corrosion.");
                return true;
            }
            case "orbital" -> {
                HitResult hit = p.pick(r, 0, false);
                if (hit.getType() == HitResult.Type.MISS) { note(p, "No fix: look at the ground within " + r + " blocks."); return false; }
                BlockPos at = BlockPos.containing(hit.getLocation());
                if (UnleashedApi.orbitalFire(p, at) == null) { note(p, "Satellite uplink unavailable."); return false; }
                note(p, "ZENITH is firing.");
                return true;
            }
            case "roar" -> {
                for (LivingEntity e : tg) {
                    Vec3 push = e.position().subtract(me).normalize().scale(1.2);
                    e.push(push.x, 0.6, push.z);
                    e.hurtMarked = true;
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 * 6, 2));
                    e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20 * 6, 1));
                    if (e instanceof Mob m) m.setTarget(null);
                }
                ring(l, me, r, ParticleTypes.CLOUD);
                l.sendParticles(ParticleTypes.SONIC_BOOM, me.x, me.y + 1, me.z, 1, 0, 0, 0, 0);
                sound(l, p, "scp682_roar", SoundEvents.RAVAGER_ROAR, 1.2F);
                return true;
            }
            case "scream" -> {
                int n = 0;
                for (LivingEntity e : tg) {
                    if (e instanceof Mob m) {
                        m.setTarget(null);
                        Vec3 away = m.position().subtract(me).normalize().scale(r).add(m.position());
                        m.getNavigation().moveTo(away.x, away.y, away.z, 1.5);
                        m.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 6, 1, false, false));
                        n++;
                    }
                }
                for (LivingEntity q : tg) if (q instanceof ServerPlayer) q.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 20 * 6, 0));
                sound(l, p, "scp096_triggered", SoundEvents.WARDEN_ROAR, 1.2F);
                note(p, n == 0 ? "Nothing here to hear it." : n + " creature" + (n == 1 ? "" : "s") + " flee" + (n == 1 ? "s" : "") + ".");
                return true;
            }
            case "immolate" -> {
                List<LivingEntity> f = tg;
                for (LivingEntity e : f) {
                    e.igniteForSeconds(8);
                    e.hurt(l.damageSources().onFire(), 4F);
                    particles(l, "scp457_flame", ParticleTypes.FLAME, e.position().add(0, e.getBbHeight() / 2, 0), 24, e.getBbWidth() / 2, 0.05);
                }
                sound(l, p, "scp457_ignite", SoundEvents.BLAZE_SHOOT, 1F);
                note(p, f.isEmpty() ? "Nothing here to burn." : f.size() + " burning.");
                return true;
            }
            case "tesla" -> {
                List<LivingEntity> f = new ArrayList<>(tg);
                f.sort(Comparator.comparingDouble(e -> e.distanceToSqr(p)));
                int chain = Math.min(f.size(), 2 + r / 4);
                Vec3 from = p.getEyePosition().subtract(0, 0.4, 0);
                for (int i = 0; i < chain; i++) {
                    LivingEntity e = f.get(i);
                    Vec3 to = e.position().add(0, e.getBbHeight() / 2, 0);
                    line(l, "tesla_spark", ParticleTypes.ELECTRIC_SPARK, from, to);
                    e.hurt(l.damageSources().lightningBolt(), 8F);
                    from = to;
                }
                sound(l, p, "tesla_discharge", SoundEvents.LIGHTNING_BOLT_IMPACT, 1F);
                if (chain == 0) note(p, "The arc finds nothing to hold.");
                return true;
            }
            case "embrace" -> {
                int n = 0;
                for (LivingEntity e : p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(r),
                        e -> e instanceof Player || e instanceof FoundationStaff || e instanceof O5MemberEntity || !e.getPersistentData().getString("bp_dom_follow").isEmpty())) {
                    e.heal(e.getMaxHealth());
                    e.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 10, 1));
                    e.removeEffect(MobEffects.POISON);
                    e.removeEffect(MobEffects.WITHER);
                    l.sendParticles(ParticleTypes.HEART, e.getX(), e.getY() + e.getBbHeight() + 0.3, e.getZ(), 5, 0.4, 0.2, 0.4, 0);
                    n++;
                }
                sound(l, p, "scp999_giggle", SoundEvents.ALLAY_AMBIENT_WITH_ITEM, 1F);
                note(p, "SCP-999's joy reaches " + n + ".");
                return true;
            }
            case "stasis" -> {
                for (LivingEntity e : tg) {
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 9, false, false));
                    e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 4, false, false));
                    if (e instanceof Mob m) { m.setTarget(null); m.getNavigation().stop(); }
                    l.sendParticles(ParticleTypes.SNOWFLAKE, e.getX(), e.getY() + e.getBbHeight() / 2, e.getZ(), 12, 0.3, 0.5, 0.3, 0.01);
                }
                ring(l, me, r, ParticleTypes.SNOWFLAKE);
                l.playSound(null, p.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1F, 0.6F);
                return true;
            }
            case "reveal" -> {
                int n = 0;
                for (Mob m : l.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(r), Unleashed::isAnomaly)) {
                    m.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20 * 30, 0, false, false));
                    n++;
                }
                l.sendParticles(ParticleTypes.SONIC_BOOM, me.x, me.y + 1, me.z, 1, 0, 0, 0, 0);
                note(p, n == 0 ? "Nothing anomalous within " + r + " blocks." : n + " anomal" + (n == 1 ? "y" : "ies") + " revealed.");
                return true;
            }
            case "blink" -> {
                HitResult hit = p.pick(r, 0, false);
                if (hit.getType() == HitResult.Type.MISS) { note(p, "Nothing to step to."); return false; }
                Vec3 to = hit.getLocation().subtract(p.getLookAngle().scale(0.6));
                l.sendParticles(ParticleTypes.REVERSE_PORTAL, me.x, me.y + 1, me.z, 40, 0.3, 0.8, 0.3, 0.1);
                p.teleportTo(to.x, Math.max(to.y, l.getMinBuildHeight()), to.z);
                p.fallDistance = 0;
                l.sendParticles(ParticleTypes.PORTAL, to.x, to.y + 1, to.z, 50, 0.3, 0.8, 0.3, 0.6);
                sound(l, p, "scp173_crack_snap", SoundEvents.ENDERMAN_TELEPORT, 0.8F);
                return true;
            }
            case "still" -> {
                int n = 0;
                for (LivingEntity e : tg) {
                    if (!(e instanceof Mob m)) continue;
                    e.getPersistentData().putString("bp_dom_calm", p.getUUID().toString());
                    m.setTarget(null);
                    if (e instanceof net.minecraft.world.entity.NeutralMob nm) nm.stopBeingAngry();
                    n++;
                }
                ring(l, me, r, ParticleTypes.END_ROD);
                note(p, n == 0 ? "Nothing here meant you harm." : n + " fall still.");
                return true;
            }
            case "judgement" -> {
                List<LivingEntity> f = tg;
                for (LivingEntity e : f) {
                    l.sendParticles(ParticleTypes.END_ROD, e.getX(), e.getY() + 8, e.getZ(), 30, 0.1, 4, 0.1, 0);
                    Dominion.end(p, e);
                }
                note(p, f.isEmpty() ? "Nothing here deserved it." : "Judgement. " + f.size() + " fell.");
                return true;
            }
            case "call" -> { return Dominion.secretary(p, false); }
            case "summon" -> { return Dominion.secretary(p, true); }
            default -> { return false; }
        }
    }

    // ================================================================== effects

    private static LivingEntity looked(ServerPlayer p, int r) {
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle(), end = eye.add(look.scale(r));
        HitResult block = p.pick(r, 0, false);
        double max = block.getType() == HitResult.Type.MISS ? r * r : block.getLocation().distanceToSqr(eye);
        var hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(p, eye, end, p.getBoundingBox().expandTowards(look.scale(r)).inflate(1),
                e -> e instanceof LivingEntity && e != p && e.isAlive(), max);
        return hit != null && hit.getEntity() instanceof LivingEntity le ? le : null;
    }

    private static void note(ServerPlayer p, String s) { if (!quiet) p.displayClientMessage(Component.literal(s).withStyle(ChatFormatting.LIGHT_PURPLE), true); }

    /** One of SCP Unleashed's own sounds, or a vanilla stand-in. */
    private static void sound(ServerLevel l, Entity at, String unleashed, SoundEvent fallback, float vol) {
        SoundEvent s = BuiltInRegistries.SOUND_EVENT.getOptional(ResourceLocation.fromNamespaceAndPath(Unleashed.MODID, unleashed)).orElse(fallback);
        l.playSound(null, at.getX(), at.getY(), at.getZ(), s, SoundSource.PLAYERS, vol, 1F);
    }

    /** One of SCP Unleashed's own particles, or a vanilla stand-in. */
    private static ParticleOptions particle(String unleashed, ParticleOptions fallback) {
        var t = BuiltInRegistries.PARTICLE_TYPE.getOptional(ResourceLocation.fromNamespaceAndPath(Unleashed.MODID, unleashed)).orElse(null);
        return t instanceof SimpleParticleType s ? s : fallback;
    }

    private static void particles(ServerLevel l, String unleashed, ParticleOptions fallback, Vec3 at, int n, double spread, double speed) {
        l.sendParticles(particle(unleashed, fallback), at.x, at.y, at.z, n, spread, spread, spread, speed);
    }

    private static void ring(ServerLevel l, Vec3 c, int r, ParticleOptions pt) {
        int n = Math.min(160, 12 + r * 8);
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n;
            l.sendParticles(pt, c.x + Math.cos(a) * r, c.y + 0.3, c.z + Math.sin(a) * r, 1, 0, 0.1, 0, 0);
        }
    }

    private static void line(ServerLevel l, String unleashed, ParticleOptions fallback, Vec3 a, Vec3 b) {
        ParticleOptions pt = particle(unleashed, fallback);
        int n = (int) Math.max(4, a.distanceTo(b) * 3);
        for (int i = 0; i <= n; i++) {
            Vec3 q = a.lerp(b, i / (double) n);
            l.sendParticles(pt, q.x, q.y, q.z, 1, 0.05, 0.05, 0.05, 0);
        }
    }

    // ================================================================== the client's view

    public static void addToSync(ServerPlayer p, net.minecraft.nbt.CompoundTag t) {
        net.minecraft.nbt.CompoundTag cd = new net.minecraft.nbt.CompoundTag();
        for (Power pw : ALL) cd.putLong(pw.id(), p.getPersistentData().getLong("bp_pcd_" + pw.id()));
        t.put("pcd", cd);
        t.put("auto", p.getPersistentData().getCompound(AUTO).copy());
        Targets.addToSync(p, t);
    }
}
