package com.breachprotocol.entity;

import com.breachprotocol.event.BreachAlerts;
import com.breachprotocol.registry.ModBlocks;
import com.breachprotocol.registry.ModEffects;
import com.breachprotocol.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * SCP-106. Slow, patient, and walls don't matter to it. If it can't reach you, or you get far
 * away, it sinks into the floor and rises again right behind you. Its touch corrodes, and it
 * leaves sticky corrosion wherever it walks. Even a containment field only holds it so long.
 */
public class Scp106Entity extends Monster implements Anomaly {
    /** >0: sinking (ticks left); <0: emerging (ticks left, negated); 0: normal. */
    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(Scp106Entity.class, EntityDataSerializers.INT);
    public static final int SINK_TICKS = 40, EMERGE_TICKS = 25;
    private int ambushCooldown = 200;
    private int phasedOutTicks;
    private int stuckTicks;
    private Vec3 lastPos = Vec3.ZERO;

    public Scp106Entity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 40;
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 300)
                .add(Attributes.ARMOR, 10)
                .add(Attributes.MOVEMENT_SPEED, 0.19)
                .add(Attributes.ATTACK_DAMAGE, 9)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 64);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(PHASE, 0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.6));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 16F));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    public int getPhase() {
        return this.entityData.get(PHASE);
    }

    /** 0 = fully above ground, 1 = fully sunk. Used by the renderer. */
    public float sinkProgress(float partialTick) {
        int p = getPhase();
        if (p > 0) return Math.min(1F, (SINK_TICKS - p + partialTick) / SINK_TICKS);
        if (p < 0) return Math.max(0F, (-p - partialTick) / EMERGE_TICKS);
        return 0F;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide || !(level() instanceof ServerLevel server)) {
            if (getPhase() != 0 && random.nextInt(2) == 0) {
                level().addParticle(ParticleTypes.SQUID_INK, getRandomX(0.8), getY() + 0.1, getRandomZ(0.8), 0, 0.02, 0);
            }
            return;
        }

        int phase = getPhase();
        if (phase > 0) {
            if (phase == 1) emerge(server);
            else entityData.set(PHASE, phase - 1);
            return;
        }
        if (phase < 0) {
            entityData.set(PHASE, phase + 1);
            return;
        }

        if (phasedOutTicks > 0) phasedOutTicks--;
        if (Anomaly.isContained(this)) {
            // The field strains to hold it. Every so often, it doesn't.
            if (random.nextInt(3000) == 0) escapeContainment(server);
            return;
        }

        if (tickCount % 15 == 0) leaveCorrosion();

        LivingEntity target = getTarget();
        if (target != null && target.isAlive()) {
            Vec3 now = position();
            stuckTicks = now.distanceToSqr(lastPos) < 0.01 ? stuckTicks + 1 : 0;
            lastPos = now;
            boolean far = distanceToSqr(target) > 18 * 18;
            boolean stuck = stuckTicks > 40 && distanceToSqr(target) > 3 * 3;
            if (ambushCooldown-- <= 0 && (far || stuck)) beginSink();
        }
    }

    private void beginSink() {
        entityData.set(PHASE, SINK_TICKS);
        getNavigation().stop();
        playSound(ModSounds.SCP106_EMERGE.get(), 1.2F, 0.7F);
        ambushCooldown = 300 + random.nextInt(200);
        stuckTicks = 0;
    }

    private void emerge(ServerLevel server) {
        LivingEntity target = getTarget();
        Vec3 spot = target != null ? findSpotBehind(target) : null;
        if (spot != null) {
            moveTo(spot.x, spot.y, spot.z, target.getYRot(), 0);
            target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0));
        }
        entityData.set(PHASE, -EMERGE_TICKS);
        server.playSound(null, getX(), getY(), getZ(), ModSounds.SCP106_LAUGH.get(), SoundSource.HOSTILE, 2.0F, 1.0F);
        server.sendParticles(ParticleTypes.SQUID_INK, getX(), getY() + 0.2, getZ(), 30, 0.6, 0.1, 0.6, 0.02);
        leaveCorrosion();
    }

    private Vec3 findSpotBehind(LivingEntity target) {
        Vec3 back = target.getLookAngle().multiply(-1, 0, -1);
        if (back.lengthSqr() < 0.01) back = new Vec3(1, 0, 0);
        back = back.normalize();
        for (double dist = 2.5; dist <= 5; dist += 1.0) {
            for (int angle = 0; angle < 360; angle += 45) {
                double rad = Math.toRadians(angle);
                double bx = back.x * Math.cos(rad) - back.z * Math.sin(rad);
                double bz = back.x * Math.sin(rad) + back.z * Math.cos(rad);
                double x = target.getX() + bx * dist, z = target.getZ() + bz * dist;
                for (int dy = 2; dy >= -3; dy--) {
                    BlockPos feet = BlockPos.containing(x, target.getY() + dy, z);
                    if (standable(feet)) return new Vec3(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
                }
            }
        }
        return null;
    }

    private boolean standable(BlockPos feet) {
        BlockState below = level().getBlockState(feet.below());
        return below.isFaceSturdy(level(), feet.below(), Direction.UP)
                && level().getBlockState(feet).getCollisionShape(level(), feet).isEmpty()
                && level().getBlockState(feet.above()).getCollisionShape(level(), feet.above()).isEmpty();
    }

    private void leaveCorrosion() {
        BlockPos feet = blockPosition();
        BlockState here = level().getBlockState(feet);
        BlockState goo = ModBlocks.CORROSION.get().defaultBlockState();
        if (here.isAir() && goo.canSurvive(level(), feet)) {
            level().setBlockAndUpdate(feet, goo);
        }
    }

    private void escapeContainment(ServerLevel server) {
        removeEffect(ModEffects.h(ModEffects.CONTAINED));
        phasedOutTicks = 20 * 45;
        ambushCooldown = 0;
        server.sendParticles(ParticleTypes.SQUID_INK, getX(), getY() + 1, getZ(), 60, 0.5, 1, 0.5, 0.05);
        BreachAlerts.announce(server, blockPosition(), List.of(designation()));
    }

    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        if (phasedOutTicks > 0 && effect.getEffect().is(ModEffects.CONTAINED.getKey())) return false;
        return super.canBeAffected(effect);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && target instanceof LivingEntity living) {
            living.addEffect(new MobEffectInstance(ModEffects.h(ModEffects.CORROSION), 200, 0), this);
            living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1), this);
        }
        return hit;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (getPhase() != 0) return false; // can't hit what's inside the floor
        return super.hurt(source, amount);
    }

    @Override
    protected boolean isImmobile() {
        return super.isImmobile() || getPhase() != 0 || Anomaly.isContained(this);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("PhasedOut", phasedOutTicks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        phasedOutTicks = tag.getInt("PhasedOut");
    }

    @Override
    public String designation() {
        return "SCP-106";
    }

    @Override
    public String objectClass() {
        return "Keter";
    }

    @Override
    public Component statusNote() {
        return phasedOutTicks > 0 ? Component.translatable("message.breachprotocol.terminal.106_phased").withStyle(ChatFormatting.DARK_PURPLE) : null;
    }
}
