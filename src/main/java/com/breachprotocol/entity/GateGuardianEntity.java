package com.breachprotocol.entity;

import com.breachprotocol.career.Career;
import com.breachprotocol.career.Rank;
import com.breachprotocol.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * SCP-001, after Dr. Clef's proposal: a burning, winged guardian before an immense gate.
 * It cannot be harmed. It warns trespassers once, then strikes them down. The Administrator
 * may pass. The Ascendant is its master, and it kneels.
 */
public class GateGuardianEntity extends PathfinderMob implements Anomaly , GeoEntity {
    private static final EntityDataAccessor<Boolean> KNEEL = SynchedEntityData.defineId(GateGuardianEntity.class, EntityDataSerializers.BOOLEAN);
    private static final double WARN = 36, STRIKE = 16;
    private final Map<UUID, Long> lastSpoke = new HashMap<>();

    public GateGuardianEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.setInvulnerable(true);
        this.setNoGravity(true);
        this.setPersistenceRequired();
        this.noCulling = true;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 10000)
                .add(Attributes.MOVEMENT_SPEED, 0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(KNEEL, false);
    }

    public boolean isKneeling() {
        return this.entityData.get(KNEEL);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        setDeltaMovement(Vec3.ZERO);
        if (level().isClientSide) {
            if (random.nextInt(2) == 0) {
                level().addParticle(ParticleTypes.FLAME, getRandomX(1.2), getY() + random.nextDouble() * getBbHeight(), getRandomZ(1.2), 0, 0.03, 0);
            }
            return;
        }
        if (tickCount % 10 != 0 || !(level() instanceof ServerLevel server)) return;

        boolean kneel = false;
        long now = server.getGameTime();
        for (ServerPlayer p : server.players()) {
            double d = p.distanceTo(this);
            if (d > WARN || p.isSpectator()) continue;
            boolean ascended = Career.isAscended(p);
            Rank rank = Career.rank(p);
            if (ascended) {
                kneel = true;
                speak(p, now, "MASTER. THE GATE IS YOURS.", ChatFormatting.GOLD, 600);
            } else if (rank == Rank.ADMINISTRATOR || p.isCreative()) {
                speak(p, now, "THE FOUNDER'S HEIR. YOU MAY PASS. BEYOND LIES WHAT HE FEARED.", ChatFormatting.YELLOW, 1200);
            } else if (d > STRIKE) {
                speak(p, now, "TURN BACK.", ChatFormatting.RED, 60);
            } else {
                smite(server, p);
            }
        }
        this.entityData.set(KNEEL, kneel);
        // nothing hostile lives in its sight
        for (Monster m : server.getEntitiesOfClass(Monster.class, getBoundingBox().inflate(12), m -> !(m instanceof Anomaly) && !com.breachprotocol.compat.Unleashed.isUnleashedAnomaly(m))) {
            server.sendParticles(ParticleTypes.FLAME, m.getX(), m.getY() + 1, m.getZ(), 20, 0.3, 0.6, 0.3, 0.05);
            m.hurt(damageSources().mobAttack(this), 1000F);
        }
    }

    private void speak(ServerPlayer p, long now, String text, ChatFormatting color, int cooldown) {
        long last = lastSpoke.getOrDefault(p.getUUID(), -100000L);
        if (now - last < cooldown) return;
        lastSpoke.put(p.getUUID(), now);
        p.displayClientMessage(Component.literal(text).withStyle(color, ChatFormatting.BOLD), true);
        p.serverLevel().playSound(null, getX(), getY() + 3, getZ(), ModSounds.GUARDIAN_VOICE.get(), SoundSource.HOSTILE, 3.0F, 1.0F);
    }

    private void smite(ServerLevel server, ServerPlayer p) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(server);
        if (bolt != null) {
            bolt.moveTo(p.position());
            bolt.setVisualOnly(true);
            server.addFreshEntity(bolt);
        }
        p.displayClientMessage(Component.literal("YOU WERE WARNED.").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), true);
        p.igniteForSeconds(10);
        p.hurt(damageSources().mobAttack(this), 1000F);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(source, amount);
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean canBeContained() {
        return false;
    }

    @Override
    public String designation() {
        return "SCP-001";
    }

    @Override
    public String objectClass() {
        return "Keter";
    }

    @Override
    public Component statusNote() {
        return Component.literal(isKneeling() ? "(kneeling)" : "(guarding the Gate)").withStyle(ChatFormatting.GOLD);
    }

    // ---------------------------------------------------------------- GeckoLib
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }

    private static final RawAnimation IDLE_ANIM = RawAnimation.begin().thenLoop("animation.gate_guardian.idle");
    private static final RawAnimation KNEEL_ANIM = RawAnimation.begin().thenLoop("animation.gate_guardian.kneel");

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 20, state -> state.setAndContinue(isKneeling() ? KNEEL_ANIM : IDLE_ANIM)));
    }
}
