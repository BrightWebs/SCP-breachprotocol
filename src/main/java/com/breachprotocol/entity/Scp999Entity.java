package com.breachprotocol.entity;

import com.breachprotocol.registry.ModEffects;
import com.breachprotocol.registry.ModItems;
import com.breachprotocol.registry.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;

/**
 * SCP-999, the tickle monster. Follows people around, soothes them, and its hugs heal.
 * Standing near it slowly regenerates you and scrubs off SCP-106's corrosion. Loves sweets.
 */
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

public class Scp999Entity extends PathfinderMob implements Anomaly , GeoEntity {
    private static final EntityDataAccessor<Integer> HUG = SynchedEntityData.defineId(Scp999Entity.class, EntityDataSerializers.INT);
    private int hugCooldown;

    public Scp999Entity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 60)
                .add(Attributes.MOVEMENT_SPEED, 0.26)
                .add(Attributes.FOLLOW_RANGE, 20);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(HUG, 0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new PanicGoal(this, 1.4));
        this.goalSelector.addGoal(2, new TemptGoal(this, 1.25,
                Ingredient.of(Items.COOKIE, Items.CAKE, Items.PUMPKIN_PIE, Items.SWEET_BERRIES, Items.HONEY_BOTTLE), false));
        this.goalSelector.addGoal(3, new FollowPlayerGoal(this));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    public boolean isHugging() {
        return this.entityData.get(HUG) > 0;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        int hug = this.entityData.get(HUG);
        if (level().isClientSide) {
            if (hug > 0 && random.nextInt(3) == 0) {
                level().addParticle(ParticleTypes.HEART, getRandomX(0.6), getY() + 0.9, getRandomZ(0.6), 0, 0.05, 0);
            }
            return;
        }
        if (hug > 0) this.entityData.set(HUG, hug - 1);
        if (hugCooldown > 0) hugCooldown--;

        if (tickCount % 40 == 0 && level() instanceof ServerLevel server) {
            for (Player p : level().getEntitiesOfClass(Player.class, getBoundingBox().inflate(4))) {
                p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0, true, true));
                if (ModEffects.has(p, ModEffects.CORROSION)) {
                    p.removeEffect(ModEffects.h(ModEffects.CORROSION));
                    server.sendParticles(ParticleTypes.HAPPY_VILLAGER, p.getX(), p.getY() + 1, p.getZ(), 8, 0.3, 0.5, 0.3, 0);
                }
            }
        }
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        boolean treat = held.is(Items.COOKIE) || held.is(Items.CAKE) || held.is(Items.PUMPKIN_PIE) || held.is(Items.HONEY_BOTTLE);
        if (hugCooldown > 0 && !treat) return InteractionResult.PASS;
        if (!level().isClientSide) {
            this.entityData.set(HUG, 40);
            hugCooldown = treat ? 0 : 200;
            playSound(ModSounds.SCP999_GIGGLE.get(), 1.0F, 0.9F + random.nextFloat() * 0.3F);
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1));
            if (player instanceof net.minecraft.server.level.ServerPlayer sp) com.breachprotocol.career.Career.onHug(sp);
            player.removeEffect(MobEffects.POISON);
            player.removeEffect(ModEffects.h(ModEffects.CORROSION));
            if (treat) {
                held.consume(1, player);
                heal(10);
                // A well-fed 999 sometimes leaves something useful behind.
                if (random.nextInt(8) == 0) spawnAtLocation(new ItemStack(ModItems.EYEDROPS.get()));
            }
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public boolean canBeContained() {
        return false; // Safe class: lives in an open room
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.SLIME_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.SLIME_DEATH;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return random.nextInt(4) == 0 ? ModSounds.SCP999_GIGGLE.get() : null;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public String designation() {
        return "SCP-999";
    }

    @Override
    public String objectClass() {
        return "Safe";
    }

    /** Gently trails the nearest player. */
    static class FollowPlayerGoal extends Goal {
        private final Scp999Entity mob;
        private Player target;

        FollowPlayerGoal(Scp999Entity mob) {
            this.mob = mob;
            setFlags(java.util.EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            target = mob.level().getNearestPlayer(mob, 12);
            return target != null && !target.isSpectator() && mob.distanceToSqr(target) > 9;
        }

        @Override
        public boolean canContinueToUse() {
            return target != null && target.isAlive() && mob.distanceToSqr(target) > 6 && mob.distanceToSqr(target) < 256;
        }

        @Override
        public void tick() {
            mob.getLookControl().setLookAt(target, 30F, 30F);
            if (mob.tickCount % 10 == 0) mob.getNavigation().moveTo(target, 1.0);
        }

        @Override
        public void stop() {
            target = null;
            mob.getNavigation().stop();
        }
    }

    // ---------------------------------------------------------------- GeckoLib
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }

    private static final RawAnimation IDLE_ANIM = RawAnimation.begin().thenLoop("animation.scp_999.idle");
    private static final RawAnimation HOP_ANIM = RawAnimation.begin().thenLoop("animation.scp_999.hop");
    private static final RawAnimation HUG_ANIM = RawAnimation.begin().thenLoop("animation.scp_999.hug");

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 4, state -> {
            if (isHugging()) return state.setAndContinue(HUG_ANIM);
            return state.setAndContinue(state.isMoving() ? HOP_ANIM : IDLE_ANIM);
        }));
    }
}
