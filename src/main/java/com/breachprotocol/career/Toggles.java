package com.breachprotocol.career;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.compat.Unleashed;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Powers you switch on with one tap and off with the next.
 *
 * <ul>
 * <li><b>Time stop</b> (the stasis pod): everything within range stops where it is, creatures,
 *     players, arrows in the air, and stays stopped, anything that wanders in included, until you
 *     tap again. You and whatever follows you move freely.</li>
 * <li><b>Rage</b> (SCP-096): what 096 is once it's seen a face. Nothing hurts you, nothing pushes you
 *     back, nothing slows you; you run faster, step up whole blocks, and when you sprint into a wall
 *     you go through it (anything no harder than 096 itself can tear: never obsidian or bedrock). Your
 *     blows land like his.</li>
 * </ul>
 */
@EventBusSubscriber(modid = BreachProtocol.MODID)
public final class Toggles {
    private Toggles() {}

    private static final String FROZEN = "bp_frozen";
    private static final ResourceLocation RAGE_SPEED = ResourceLocation.fromNamespaceAndPath(BreachProtocol.MODID, "rage_speed"),
            RAGE_STEP = ResourceLocation.fromNamespaceAndPath(BreachProtocol.MODID, "rage_step");
    /** SCP-096's own limit (Scp096Rampage.HardnessCap): obsidian holds, stone doesn't. */
    private static final float TEAR = 30F;

    private static final class Stop {
        final ServerLevel level;
        final Vec3 centre;
        final int r;
        final Map<Integer, Vec3> held = new HashMap<>();      // where each frozen thing is pinned
        final Map<Integer, Vec3> flight = new HashMap<>();    // projectiles: their speed, to give back
        Stop(ServerLevel l, Vec3 c, int r) { level = l; centre = c; this.r = r; }
    }

    private static final Map<UUID, Stop> STOPS = new HashMap<>();
    private static final java.util.Set<UUID> RAGING = new java.util.HashSet<>();

    public static boolean on(ServerPlayer p, String id) {
        return switch (id) { case "stasis" -> STOPS.containsKey(p.getUUID()); case "rage" -> RAGING.contains(p.getUUID()); case "skullking" -> SkullKing.in(p); default -> false; };
    }

    public static boolean raging(Entity e) { return e != null && RAGING.contains(e.getUUID()); }

    public static void toggle(ServerPlayer p, String id, int r) {
        switch (id) {
            case "stasis" -> { if (STOPS.containsKey(p.getUUID())) resume(p); else stop(p, r); }
            case "rage" -> { if (RAGING.contains(p.getUUID())) calm(p); else rage(p); }
            case "skullking" -> SkullKing.toggle(p);
            default -> { return; }
        }
        Career.sync(p);
    }

    private static void tell(ServerPlayer p, String s) { p.displayClientMessage(Component.literal(s).withStyle(ChatFormatting.LIGHT_PURPLE), true); }

    private static void sound(ServerLevel l, Vec3 at, String unleashed, SoundEvent fallback, float vol, float pitch) {
        SoundEvent s = BuiltInRegistries.SOUND_EVENT.getOptional(ResourceLocation.fromNamespaceAndPath(Unleashed.MODID, unleashed)).orElse(fallback);
        l.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, vol, pitch);
    }

    // ================================================================== time stop

    private static void stop(ServerPlayer p, int r) {
        ServerLevel l = p.serverLevel();
        STOPS.put(p.getUUID(), new Stop(l, p.position(), r));
        l.playSound(null, p.blockPosition(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 1.2F, 0.5F);
        l.playSound(null, p.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1F, 0.5F);
        for (int i = 0; i < 64; i++) {
            double a = i * Math.PI * 2 / 64;
            l.sendParticles(ParticleTypes.SNOWFLAKE, p.getX() + Math.cos(a) * r, p.getY() + 1, p.getZ() + Math.sin(a) * r, 1, 0, 0.4, 0, 0);
        }
        tell(p, "Time stops. Tap again to let it run.");
    }

    private static void resume(ServerPlayer p) {
        Stop s = STOPS.remove(p.getUUID());
        if (s == null) return;
        release(s);
        s.level.playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1F, 1.4F);
        tell(p, "Time runs again.");
    }

    private static void release(Stop s) {
        for (int id : s.held.keySet()) {
            Entity e = s.level.getEntity(id);
            if (e != null) thaw(e, s.flight.get(id));
        }
        s.held.clear();
        s.flight.clear();
    }

    private static void thaw(Entity e, Vec3 speed) {
        CompoundTag t = e.getPersistentData();
        if (t.contains(FROZEN)) {
            byte was = t.getByte(FROZEN);
            t.remove(FROZEN);
            if (e instanceof Mob m) m.setNoAi(was == 2);
            if (e instanceof Projectile) e.setNoGravity(was == 2);
        }
        if (speed != null) { e.setDeltaMovement(speed); e.hurtMarked = true; }
    }

    private static boolean exempt(ServerPlayer p, Entity e) {
        if (e == p || e.isSpectator()) return true;
        if (e instanceof Player q && q.isCreative()) return true;
        return p.getUUID().toString().equals(e.getPersistentData().getString("bp_dom_follow"))
                || e.getVehicle() == p || p.getVehicle() == e;
    }

    private static void hold(Stop s, ServerPlayer p) {
        AABB box = new AABB(s.centre, s.centre).inflate(s.r);
        double r2 = (double) s.r * s.r;
        for (Entity e : s.level.getEntities((Entity) null, box, e -> e.isAlive() && (e instanceof LivingEntity || e instanceof Projectile) && !exempt(p, e))) {
            if (e.distanceToSqr(s.centre) > r2 && !s.held.containsKey(e.getId())) continue;
            if (!s.held.containsKey(e.getId())) {
                // caught: remember how it was, then stop it
                s.held.put(e.getId(), e.position());
                if (e instanceof Projectile) s.flight.put(e.getId(), e.getDeltaMovement());
                CompoundTag t = e.getPersistentData();
                if (!t.contains(FROZEN)) t.putByte(FROZEN, (byte) (e instanceof Mob m && m.isNoAi() || e instanceof Projectile && e.isNoGravity() ? 2 : 1));
                if (e instanceof Mob m) { m.setNoAi(true); m.getNavigation().stop(); }
                if (e instanceof Projectile) e.setNoGravity(true);
            }
            Vec3 at = s.held.get(e.getId());
            e.setDeltaMovement(Vec3.ZERO);
            if (e.position().distanceToSqr(at) > 0.0004) {
                if (e instanceof ServerPlayer q) q.connection.teleport(at.x, at.y, at.z, q.getYRot(), q.getXRot());
                else e.setPos(at.x, at.y, at.z);
            }
            e.hurtMarked = true;
        }
        if (s.level.getGameTime() % 10 == 0) for (int id : s.held.keySet()) {
            Entity e = s.level.getEntity(id);
            if (e != null) s.level.sendParticles(ParticleTypes.SNOWFLAKE, e.getX(), e.getY() + e.getBbHeight() / 2, e.getZ(), 1, e.getBbWidth() / 2, e.getBbHeight() / 3, e.getBbWidth() / 2, 0);
        }
    }

    /** Anything left frozen by a server that stopped mid-stasis wakes when it loads. */
    @SubscribeEvent
    public static void joined(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        Entity e = event.getEntity();
        if (!e.getPersistentData().contains(FROZEN)) return;
        for (Stop s : STOPS.values()) if (s.held.containsKey(e.getId())) return;
        thaw(e, null);
    }

    // ================================================================== rage

    private static void rage(ServerPlayer p) {
        RAGING.add(p.getUUID());
        var speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && !speed.hasModifier(RAGE_SPEED)) speed.addTransientModifier(new AttributeModifier(RAGE_SPEED, 0.7, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        var step = p.getAttribute(Attributes.STEP_HEIGHT);
        if (step != null && !step.hasModifier(RAGE_STEP)) step.addTransientModifier(new AttributeModifier(RAGE_STEP, 0.65, AttributeModifier.Operation.ADD_VALUE));
        sound(p.serverLevel(), p.position(), "scp096_triggered", SoundEvents.WARDEN_ROAR, 1.3F, 1F);
        p.serverLevel().sendParticles(ParticleTypes.SONIC_BOOM, p.getX(), p.getY() + 1, p.getZ(), 1, 0, 0, 0, 0);
        tell(p, "Rage. Nothing stops you. Tap again to come back.");
    }

    private static void calm(ServerPlayer p) {
        RAGING.remove(p.getUUID());
        var speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(RAGE_SPEED);
        var step = p.getAttribute(Attributes.STEP_HEIGHT);
        if (step != null) step.removeModifier(RAGE_STEP);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.PLAYER_BREATH, SoundSource.PLAYERS, 1F, 0.7F);
        tell(p, "The rage passes.");
    }

    private static void rageTick(ServerPlayer p) {
        // nothing lingers on you: poison, slowness, wither, the lot
        if (p.tickCount % 10 == 0) {
            List<MobEffectInstance> bad = new ArrayList<>();
            for (MobEffectInstance e : p.getActiveEffects()) if (!e.getEffect().value().isBeneficial()) bad.add(e);
            for (MobEffectInstance e : bad) p.removeEffect(e.getEffect());
            p.clearFire();
        }
        p.setAirSupply(p.getMaxAirSupply());
        p.fallDistance = 0;
        // sprinting into a wall: through it
        if (!p.isSprinting() || p.isSpectator() || !p.mayBuild()) return;
        ServerLevel l = p.serverLevel();
        Vec3 look = p.getLookAngle().multiply(1, 0, 1);
        if (look.lengthSqr() < 1e-4) return;
        look = look.normalize();
        AABB ahead = p.getBoundingBox().move(look.scale(0.7)).inflate(0.05, 0, 0.05);
        int torn = 0;
        for (BlockPos q : BlockPos.betweenClosed(BlockPos.containing(ahead.minX, ahead.minY + 0.05, ahead.minZ), BlockPos.containing(ahead.maxX, ahead.maxY, ahead.maxZ))) {
            if (torn >= 6) break;
            BlockState st = l.getBlockState(q);
            if (st.isAir() || st.getCollisionShape(l, q).isEmpty()) continue;
            float hard = st.getDestroySpeed(l, q);
            if (hard < 0 || hard > TEAR) continue;
            if (p.gameMode.destroyBlock(q.immutable())) {
                torn++;
                l.sendParticles(ParticleTypes.EXPLOSION, q.getX() + 0.5, q.getY() + 0.5, q.getZ() + 0.5, 1, 0, 0, 0, 0);
            }
        }
        if (torn > 0) l.playSound(null, p.blockPosition(), SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.PLAYERS, 0.8F, 0.7F);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void hurt(LivingIncomingDamageEvent event) {
        LivingEntity v = event.getEntity();
        if (raging(v) && !event.getSource().is(net.minecraft.world.damagesource.DamageTypes.FELL_OUT_OF_WORLD)
                && !event.getSource().is(net.minecraft.world.damagesource.DamageTypes.GENERIC_KILL)) {
            event.setCanceled(true);
            return;
        }
        blow(event);
    }

    private static boolean REDEALING;
    private static final net.minecraft.resources.ResourceKey<net.minecraft.world.damagesource.DamageType> CAUGHT =
            net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath("scp_unleashed", "scp096_caught"));
    private static final String[] SEALS = {"anti_skill", "anti_magic", "energy_blockade", "silence"};

    /**
     * The Administrator's (and SCP-Omega's) bare blows land the way SCP-096's do: as its own "caught" damage,
     * which nothing turns aside (armor, enchantments, resistances, invulnerability), and with whatever Manas
     * Reborn gives the one struck (skills, magic, energy) sealed off first. In Rage, four times as hard and
     * thrown back.
     */
    private static void blow(LivingIncomingDamageEvent event) {
        if (REDEALING) return;
        var src = event.getSource();
        if (!(src.getDirectEntity() instanceof ServerPlayer p) || src.getEntity() != p || !src.is(net.minecraft.world.damagesource.DamageTypes.PLAYER_ATTACK) || !Vault.chief(p)) return;
        LivingEntity v = event.getEntity();
        boolean rage = raging(p);
        float amount = rage ? Math.max(event.getAmount() * 4F, 20F) : event.getAmount();
        if (!caughtExists(p)) { seal(p, v, rage ? 600 : 200); if (rage) event.setAmount(amount); return; } // no SCP Unleashed: an ordinary (if harder) blow
        event.setCanceled(true);
        shyBlow(p, v, amount, rage ? 1.6 : 0.4, rage ? 600 : 200);
    }

    private static boolean caughtExists(ServerPlayer p) {
        return p.serverLevel().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE).getHolder(CAUGHT).isPresent();
    }

    /** Manas Reborn's skills, magic and energy sealed off the one struck. */
    private static void seal(ServerPlayer p, LivingEntity v, int dur) {
        for (String id : SEALS) {
            var h = net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.fromNamespaceAndPath("manas_reborn", id));
            h.ifPresent(e -> v.addEffect(new net.minecraft.world.effect.MobEffectInstance(e, dur, 0, false, true, true), p));
        }
    }

    /** A blow the way SCP-096 deals it: their gifts sealed first, then its own caught damage, which nothing turns aside, and thrown back. */
    /** SCP-096's strike as SCP Unleashed plays it: both strike sounds layered, a double sweep and a burst of smoke on the one struck. */
    public static void strike(LivingEntity v) {
        if (!(v.level() instanceof net.minecraft.server.level.ServerLevel l)) return;
        double x = v.getX(), y = v.getY() + v.getBbHeight() * 0.5, z = v.getZ();
        for (String id : new String[]{"scp096_strike", "scp096_strike_impact"}) {
            net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.getOptional(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("scp_unleashed", id))
                    .ifPresent(snd -> l.playSound(null, x, y, z, snd, net.minecraft.sounds.SoundSource.HOSTILE, 1.6F, 0.9F + l.random.nextFloat() * 0.15F));
        }
        l.sendParticles(net.minecraft.core.particles.ParticleTypes.SWEEP_ATTACK, x, y, z, 2, 0.3, 0.2, 0.3, 0.0);
        l.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF, x, y, z, 10, 0.3, 0.5, 0.3, 0.02);
    }

    public static void shyBlow(ServerPlayer p, LivingEntity v, float amount, double kb, int sealTicks) {
        seal(p, v, sealTicks);
        PowerKills.credit(p, v);
        strike(v);
        v.invulnerableTime = 0;
        REDEALING = true;
        try { v.hurt(caughtExists(p) ? PowerKills.as(p, CAUGHT) : p.damageSources().playerAttack(p), amount); } finally { REDEALING = false; }
        Vec3 push = v.position().subtract(p.position()).multiply(1, 0, 1).normalize().scale(kb);
        v.push(push.x, kb > 1 ? 0.45 : 0.2, push.z);
        v.hurtMarked = true;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void pushed(LivingKnockBackEvent event) {
        if (raging(event.getEntity())) event.setCanceled(true);
    }

    // ================================================================== upkeep

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        MinecraftServer srv = event.getServer();
        for (Iterator<Map.Entry<UUID, Stop>> it = STOPS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Stop> en = it.next();
            ServerPlayer p = srv.getPlayerList().getPlayer(en.getKey());
            if (p == null || !p.isAlive() || p.serverLevel() != en.getValue().level || Dominion.level(p) < 1) {
                release(en.getValue());
                it.remove();
                if (p != null) Career.sync(p);
                continue;
            }
            hold(en.getValue(), p);
        }
        for (Iterator<UUID> it = RAGING.iterator(); it.hasNext(); ) {
            ServerPlayer p = srv.getPlayerList().getPlayer(it.next());
            if (p == null) { it.remove(); continue; }
            if (!p.isAlive() || Dominion.level(p) < 2) { it.remove(); calmQuiet(p); Career.sync(p); continue; }
            rageTick(p);
        }
    }

    private static void calmQuiet(ServerPlayer p) {
        var speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(RAGE_SPEED);
        var step = p.getAttribute(Attributes.STEP_HEIGHT);
        if (step != null) step.removeModifier(RAGE_STEP);
    }

    @SubscribeEvent
    public static void left(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        Stop s = STOPS.remove(p.getUUID());
        if (s != null) release(s);
        if (RAGING.remove(p.getUUID())) calmQuiet(p);
    }

    @SubscribeEvent
    public static void stopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
        for (Stop s : STOPS.values()) release(s);
        STOPS.clear();
        RAGING.clear();
    }

    public static void addToSync(ServerPlayer p, CompoundTag t) {
        CompoundTag on = new CompoundTag();
        if (on(p, "stasis")) on.putBoolean("stasis", true);
        if (on(p, "rage")) on.putBoolean("rage", true);
        if (on(p, "skullking")) on.putBoolean("skullking", true);
        t.put("tog", on);
    }
}
