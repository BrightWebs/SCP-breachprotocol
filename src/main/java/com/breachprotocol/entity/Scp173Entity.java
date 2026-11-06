package com.breachprotocol.entity;

import com.breachprotocol.blink.Observation;
import com.breachprotocol.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * SCP-173. Cannot move while any player is watching it. The moment nobody is (blinking,
 * looking away, lights out) it closes distance in a lunge and snaps necks.
 */
public class Scp173Entity extends Monster implements Anomaly {
    private static final EntityDataAccessor<Boolean> FROZEN = SynchedEntityData.defineId(Scp173Entity.class, EntityDataSerializers.BOOLEAN);
    private static final double OBSERVE_RANGE = 64;
    private float lockedYaw;
    private boolean wasFrozen = false;
    private int scrapeCooldown;

    public Scp173Entity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 30;
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 250)
                .add(Attributes.ARMOR, 20)
                .add(Attributes.ARMOR_TOUGHNESS, 8)
                .add(Attributes.MOVEMENT_SPEED, 0.42)
                .add(Attributes.ATTACK_DAMAGE, 50)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 48)
                .add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(FROZEN, true);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.6, true));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.6));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    public boolean isFrozen() {
        return this.entityData.get(FROZEN);
    }

    private boolean computeFrozen() {
        if (Anomaly.isContained(this)) return true;
        for (Player p : level().players()) {
            if (p.distanceToSqr(this) < OBSERVE_RANGE * OBSERVE_RANGE && Observation.isWatching(p, this, OBSERVE_RANGE, 0.55)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void aiStep() {
        if (!level().isClientSide) {
            boolean frozen = computeFrozen();
            this.entityData.set(FROZEN, frozen);
            if (frozen) {
                if (!wasFrozen) lockedYaw = getYRot();
                getNavigation().stop();
                setDeltaMovement(0, Math.min(0, getDeltaMovement().y), 0);
            } else if (wasFrozen) {
                lunge();
            }
            wasFrozen = frozen;
        }
        super.aiStep();
        if (isFrozen()) {
            float yaw = level().isClientSide ? getYRot() : lockedYaw;
            setYRot(yaw);
            this.yBodyRot = this.yBodyRotO = yaw;
            this.yHeadRot = this.yHeadRotO = yaw;
        } else if (!level().isClientSide && scrapeCooldown-- <= 0 && getDeltaMovement().horizontalDistanceSqr() > 0.002) {
            playSound(ModSounds.SCP173_SCRAPE.get(), 1.2F, 0.8F + random.nextFloat() * 0.3F);
            scrapeCooldown = 8;
        }
    }

    /** The instant it is unobserved, it covers ground in one jump toward its target. */
    private void lunge() {
        LivingEntity target = getTarget();
        if (target == null || distanceToSqr(target) > 24 * 24) return;
        Vec3 dir = target.position().subtract(position()).multiply(1, 0, 1);
        double dist = dir.length();
        if (dist < 2.2) return;
        dir = dir.normalize();
        for (double step = Math.min(4.0, dist - 1.4); step >= 1.0; step -= 1.0) {
            double nx = getX() + dir.x * step, nz = getZ() + dir.z * step;
            Vec3 delta = new Vec3(nx - getX(), 0, nz - getZ());
            BlockPos below = BlockPos.containing(nx, getY() - 0.5, nz);
            BlockState floor = level().getBlockState(below);
            if (level().noCollision(this, getBoundingBox().move(delta)) && floor.isFaceSturdy(level(), below, Direction.UP)) {
                float yaw = (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90F;
                moveTo(nx, getY(), nz, yaw, getXRot());
                lockedYaw = yaw;
                level().playSound(null, getX(), getY(), getZ(), ModSounds.SCP173_SCRAPE.get(), SoundSource.HOSTILE, 1.5F, 0.7F);
                return;
            }
        }
    }

    @Override
    protected boolean isImmobile() {
        return super.isImmobile() || isFrozen();
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        if (isFrozen()) return false;
        boolean hit = super.doHurtTarget(target);
        if (hit) {
            level().playSound(null, target.getX(), target.getY(), target.getZ(), ModSounds.SCP173_SNAP.get(), SoundSource.HOSTILE, 1.6F, 1.0F);
        }
        return hit;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // Reinforced concrete: only heavy damage gets through.
        return super.hurt(source, source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION) ? amount * 2F : amount * 0.5F);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.STONE_HIT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.STONE_BREAK;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        // Statues don't have footsteps.
    }

    @Override
    public String designation() {
        return "SCP-173";
    }

    @Override
    public String objectClass() {
        return "Euclid";
    }

    @Override
    public net.minecraft.network.chat.Component statusNote() {
        return net.minecraft.network.chat.Component.translatable(isFrozen()
                ? "message.breachprotocol.terminal.173_observed" : "message.breachprotocol.terminal.173_unobserved");
    }
}
