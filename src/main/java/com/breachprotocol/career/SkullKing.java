package com.breachprotocol.career;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.network.FlightPosePayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * SCP-Ω's True Form (the Skull King). Tap it and the Administrator comes apart into bone, from the feet up,
 * growing as it goes, the crown catching fire, until the thing stands there whole and roars: everything hostile
 * around it is thrown back, and every creature that hears it is afraid, and runs. Tap again and it's a man again.
 */
@EventBusSubscriber(modid = BreachProtocol.MODID)
public final class SkullKing {
    private SkullKing() {}

    private static final String TAG = "bp_skull";
    private static final ResourceLocation SIZE = ResourceLocation.fromNamespaceAndPath(BreachProtocol.MODID, "skull_king_size");
    /** How much bigger than a man it stands (on top of 1). */
    private static final double GROWN = 0.75;
    private static final int GROW = 70, SHRINK = 24, ROAR = 22, FEAR = 20 * 12;

    /** Who's in the shape, and the game time the change began (the change is done GROW ticks later). */
    private static final Map<UUID, Long> FORM = new HashMap<>();
    private static final Map<UUID, Long> LEAVING = new HashMap<>();
    /** Frightened creatures: until when, and what they're running from. */
    private static final Map<Mob, Fear> AFRAID = new HashMap<>();

    private record Fear(long until, UUID from, Vec3 at) {}

    public static boolean in(ServerPlayer p) { return FORM.containsKey(p.getUUID()); }
    public static boolean whole(ServerPlayer p) { Long s = FORM.get(p.getUUID()); return s != null && p.serverLevel().getGameTime() - s >= GROW; }

    public static void toggle(ServerPlayer p) {
        if (FORM.containsKey(p.getUUID())) revert(p, true); else begin(p);
    }

    private static void send(ServerPlayer p, int state) { PacketDistributor.sendToPlayersTrackingEntityAndSelf(p, new FlightPosePayload(p.getId(), state)); }

    private static void tell(ServerPlayer p, String s) { p.displayClientMessage(Component.literal(s).withStyle(ChatFormatting.LIGHT_PURPLE), true); }

    private static void begin(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        FORM.put(p.getUUID(), l.getGameTime());
        LEAVING.remove(p.getUUID());
        p.getPersistentData().putBoolean(TAG, true);
        send(p, 30);
        l.playSound(null, p.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.5F, 0.5F);
        l.playSound(null, p.blockPosition(), SoundEvents.BONE_BLOCK_BREAK, SoundSource.PLAYERS, 1.5F, 0.5F);
        tell(p, "SCP-Ω: True Form.");
    }

    private static void revert(ServerPlayer p, boolean loud) {
        FORM.remove(p.getUUID());
        p.getPersistentData().remove(TAG);
        size(p, 0);
        if (!loud) { send(p, 33); return; }
        LEAVING.put(p.getUUID(), p.serverLevel().getGameTime());
        send(p, 32);
        ServerLevel l = p.serverLevel();
        l.sendParticles(ParticleTypes.LARGE_SMOKE, p.getX(), p.getY() + 1.5, p.getZ(), 60, 0.8, 1.2, 0.8, 0.03);
        l.sendParticles(ParticleTypes.SOUL, p.getX(), p.getY() + 1.5, p.getZ(), 20, 0.6, 1, 0.6, 0.05);
        l.playSound(null, p.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1F, 0.5F);
        l.playSound(null, p.blockPosition(), SoundEvents.BONE_BLOCK_BREAK, SoundSource.PLAYERS, 1.2F, 0.7F);
        tell(p, "The bones fall away.");
    }

    private static void size(ServerPlayer p, double amount) {
        var a = p.getAttribute(Attributes.SCALE);
        if (a == null) return;
        if (amount <= 0) a.removeModifier(SIZE);
        else a.addOrUpdateTransientModifier(new AttributeModifier(SIZE, amount, AttributeModifier.Operation.ADD_VALUE));
    }

    // ================================================================== the change

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        MinecraftServer srv = event.getServer();
        for (Iterator<Map.Entry<UUID, Long>> it = LEAVING.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Long> en = it.next();
            ServerPlayer p = srv.getPlayerList().getPlayer(en.getKey());
            if (p == null || p.serverLevel().getGameTime() - en.getValue() > SHRINK) it.remove();
        }
        for (UUID id : new ArrayList<>(FORM.keySet())) {
            ServerPlayer p = srv.getPlayerList().getPlayer(id);
            if (p == null) { FORM.remove(id); continue; }
            if (!p.isAlive()) { revert(p, false); continue; }
            ServerLevel l = p.serverLevel();
            long age = l.getGameTime() - FORM.get(id);
            if (age <= GROW) changing(p, l, (int) age);
            else if (age % 20 == 0) {
                // smouldering where it stands
                l.sendParticles(ParticleTypes.SMOKE, p.getX(), p.getY() + p.getBbHeight(), p.getZ(), 2, 0.3, 0.1, 0.3, 0.01);
            }
        }
        if (!AFRAID.isEmpty() && srv.getTickCount() % 5 == 0) fearTick(srv);
    }

    private static void changing(ServerPlayer p, ServerLevel l, int age) {
        float k = age / (float) GROW;
        // it grows as the bones come, slowly, then all at once
        double ease = k < 0.5 ? 2 * k * k : 1 - Math.pow(-2 * k + 2, 2) / 2;
        size(p, GROWN * ease);
        double h = p.getBbHeight();
        // bone, cracking and setting, from the feet up
        if (age % 5 == 0) {
            l.playSound(null, p.blockPosition(), age % 10 == 0 ? SoundEvents.BONE_BLOCK_PLACE : SoundEvents.SKELETON_STEP, SoundSource.PLAYERS, 1.2F, 0.4F + k * 0.4F);
            double y = p.getY() + h * k;
            for (int i = 0; i < 10; i++) {
                double a = (age * 0.6) + i * Math.PI / 5, r = 0.5 + 0.3 * p.getScale();
                l.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, p.getX() + Math.cos(a) * r, y, p.getZ() + Math.sin(a) * r, 1, 0, 0.05, 0, 0.01);
            }
            l.sendParticles(ParticleTypes.ASH, p.getX(), y, p.getZ(), 12, 0.6, 0.3, 0.6, 0.02);
        }
        if (age == GROW / 2) {
            // the crown catches
            l.playSound(null, p.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.2F, 0.5F);
            l.sendParticles(ParticleTypes.FLAME, p.getX(), p.getY() + h, p.getZ(), 40, 0.3, 0.2, 0.3, 0.06);
        }
        if (age == GROW - ROAR) {
            // the wings open: a downdraft
            l.playSound(null, p.blockPosition(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 2F, 0.6F);
            l.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.2, p.getZ(), 40, 1.5, 0.1, 1.5, 0.1);
        }
        if (age == GROW) roar(p, l);
    }

    // ================================================================== the roar

    private static void roar(ServerPlayer p, ServerLevel l) {
        Vec3 me = p.position();
        for (SoundEvent s : new SoundEvent[]{SoundEvents.RAVAGER_ROAR, SoundEvents.ENDER_DRAGON_GROWL, SoundEvents.WARDEN_ROAR})
            l.playSound(null, me.x, me.y, me.z, s, SoundSource.PLAYERS, 3F, 0.55F);
        // the shockwave: a ring of it, rolling out
        for (int ring = 1; ring <= 4; ring++) {
            double r = ring * 3.5;
            for (int i = 0; i < 24 * ring; i++) {
                double a = i * Math.PI * 2 / (24 * ring);
                l.sendParticles(ring % 2 == 0 ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.POOF, me.x + Math.cos(a) * r, me.y + 0.3, me.z + Math.sin(a) * r, 1, Math.cos(a) * 0.3, 0.02, Math.sin(a) * 0.3, 0.2);
            }
        }
        l.sendParticles(ParticleTypes.SONIC_BOOM, me.x, me.y + p.getBbHeight() * 0.8, me.z, 1, 0, 0, 0, 0);
        l.sendParticles(ParticleTypes.EXPLOSION_EMITTER, me.x, me.y + 0.5, me.z, 1, 0, 0, 0, 0);
        int thrown = 0, scared = 0;
        double R = 24;
        for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(R), e -> e != p && e.isAlive() && e.distanceToSqr(p) <= R * R)) {
            if (Targets.yours(p, e)) continue;
            boolean foe = Targets.hostile(p, e);
            Vec3 away = e.position().subtract(me).multiply(1, 0, 1);
            double d = Math.max(0.5, away.length());
            away = away.scale(1 / d);
            if (foe) {
                // thrown back, the nearer the harder
                double push = 3.2 * (1 - d / (R + 4)) + 0.6;
                e.push(away.x * push, 0.55 + push * 0.12, away.z * push);
                e.hurtMarked = true;
                PowerKills.credit(p, e);
                thrown++;
            }
            if (e instanceof Mob m) {
                // and afraid: it drops whatever it was after and runs, for a while
                AFRAID.put(m, new Fear(l.getGameTime() + FEAR, p.getUUID(), me));
                m.setTarget(null);
                flee(m, me);
                m.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, FEAR, 1, false, true));
                m.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, FEAR / 2, 1, false, false));
                scared++;
            } else if (e instanceof ServerPlayer q && foe) {
                q.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 20 * 8, 0));
                q.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20 * 10, 1));
                q.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 * 4, 1));
            }
        }
        tell(p, thrown + scared == 0 ? "True Form." : "True Form. " + scared + " creature" + (scared == 1 ? "" : "s") + " flee" + (scared == 1 ? "s" : "") + ".");
    }

    private static void flee(Mob m, Vec3 from) {
        Vec3 away = m.position().subtract(from).multiply(1, 0, 1);
        if (away.lengthSqr() < 1e-3) away = new Vec3(m.getRandom().nextDouble() - 0.5, 0, m.getRandom().nextDouble() - 0.5);
        Vec3 to = m.position().add(away.normalize().scale(20));
        m.getNavigation().moveTo(to.x, to.y, to.z, 1.6);
    }

    private static void fearTick(MinecraftServer srv) {
        for (Iterator<Map.Entry<Mob, Fear>> it = AFRAID.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Mob, Fear> en = it.next();
            Mob m = en.getKey();
            Fear f = en.getValue();
            if (!m.isAlive() || m.isRemoved() || m.level().getGameTime() > f.until()) { it.remove(); continue; }
            m.setTarget(null);
            if (m.getNavigation().isDone()) flee(m, f.at());
            if (m.level() instanceof ServerLevel sl && m.getRandom().nextInt(4) == 0)
                sl.sendParticles(ParticleTypes.SOUL, m.getX(), m.getY() + m.getBbHeight() + 0.3, m.getZ(), 1, 0.1, 0.05, 0.1, 0.01);
        }
    }

    /** Nothing frightened turns on the one it's frightened of. */
    @SubscribeEvent
    public static void target(LivingChangeTargetEvent event) {
        if (AFRAID.isEmpty() || !(event.getEntity() instanceof Mob m)) return;
        Fear f = AFRAID.get(m);
        if (f != null && event.getNewAboutToBeSetTarget() != null) event.setCanceled(true);
    }

    /** Crouched, it sits or prowls low: its eyes are down where its skull is (so first person sees from there too). */
    @SubscribeEvent
    public static void size(net.neoforged.neoforge.event.entity.EntityEvent.Size event) {
        if (!(event.getEntity() instanceof net.minecraft.world.entity.player.Player p) || event.getPose() != net.minecraft.world.entity.Pose.CROUCHING) return;
        if (!com.breachprotocol.world.FlightState.trueForm(p)) return;
        event.setNewSize(event.getNewSize().withEyeHeight(0.79F * p.getScale()));
    }

    /** Who's on a wall (their client says so; the server keeps them from taking a fall for it). */
    private static final Map<UUID, Long> CLIMBING = new HashMap<>();

    public static void climb(ServerPlayer p, boolean on) {
        if (on && !whole(p)) return;
        if (on) CLIMBING.put(p.getUUID(), Long.MAX_VALUE); else CLIMBING.computeIfPresent(p.getUUID(), (k, v) -> p.serverLevel().getGameTime() + 20);
        PacketDistributor.sendToPlayersTrackingEntity(p, new FlightPosePayload(p.getId(), on ? 40 : 41));
    }

    @SubscribeEvent
    public static void climbers(ServerTickEvent.Post event) {
        if (CLIMBING.isEmpty()) return;
        for (Iterator<Map.Entry<UUID, Long>> it = CLIMBING.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Long> en = it.next();
            ServerPlayer p = event.getServer().getPlayerList().getPlayer(en.getKey());
            if (p == null || p.serverLevel().getGameTime() > en.getValue()) { it.remove(); continue; }
            p.resetFallDistance();
        }
    }

    // ================================================================== staying changed

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !p.getPersistentData().getBoolean(TAG)) return;
        FORM.put(p.getUUID(), p.serverLevel().getGameTime() - GROW - 1);
        size(p, GROWN);
        send(p, 31);
    }

    @SubscribeEvent
    public static void left(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) { FORM.remove(p.getUUID()); LEAVING.remove(p.getUUID()); }
    }

    @SubscribeEvent
    public static void travelled(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && FORM.containsKey(p.getUUID())) send(p, whole(p) ? 31 : 30);
    }

    @SubscribeEvent
    public static void respawned(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) { FORM.remove(p.getUUID()); p.getPersistentData().remove(TAG); size(p, 0); }
    }

    /** Anyone who comes into sight of it sees it as it is. */
    @SubscribeEvent
    public static void seen(PlayerEvent.StartTracking event) {
        if (!(event.getTarget() instanceof ServerPlayer p) || !(event.getEntity() instanceof ServerPlayer watcher) || !FORM.containsKey(p.getUUID())) return;
        PacketDistributor.sendToPlayer(watcher, new FlightPosePayload(p.getId(), whole(p) ? 31 : 30));
    }
}
