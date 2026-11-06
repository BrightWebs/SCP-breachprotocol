package com.breachprotocol.entity;

import com.breachprotocol.blink.Observation;
import com.breachprotocol.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * SCP-096. Docile and weeping until someone sees its face. Then: a long, screaming wind-up,
 * and it will cross any distance and tear through doors, glass and wood to reach everyone who
 * looked. It ignores everyone else. Carved pumpkins on your head and closed eyes keep you safe.
 */
public class Scp096Entity extends Monster implements Anomaly {
    public static final int CALM = 0, TRIGGERED = 1, RAGE = 2;
    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(Scp096Entity.class, EntityDataSerializers.INT);
    private static final int WINDUP_TICKS = 110;
    private static final double SIGHT_RANGE = 64;
    private static final double GIVE_UP_RANGE = 160;

    private final Set<UUID> targets = new LinkedHashSet<>();
    private int windup;
    private int cryCooldown;
    private int smashCooldown;
    private int repathCooldown;

    public Scp096Entity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 50;
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 600)
                .add(Attributes.ARMOR, 6)
                .add(Attributes.MOVEMENT_SPEED, 0.28)
                .add(Attributes.ATTACK_DAMAGE, 1000)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 128)
                .add(Attributes.STEP_HEIGHT, 1.5);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(STATE, CALM);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.45) {
            @Override
            public boolean canUse() {
                return getRageState() == CALM && super.canUse();
            }
        });
    }

    public int getRageState() {
        return this.entityData.get(STATE);
    }

    private void setRageState(int s) {
        this.entityData.set(STATE, s);
    }

    // ---------------------------------------------------------------- seeing the face

    /** Player is in front of 096, looking right at its head, with nothing in between. */
    private boolean seesFace(Player p) {
        if (p.isCreative() || !Observation.canSee(p)) return false;
        if (com.breachprotocol.career.Career.isAscended(p)) return false; // it averts its face from its master
        if (p.getItemBySlot(EquipmentSlot.HEAD).is(Items.CARVED_PUMPKIN)) return false;
        Vec3 head = new Vec3(getX(), getEyeY(), getZ());
        Vec3 toPlayer = p.getEyePosition().subtract(head);
        double dist = toPlayer.length();
        if (dist > SIGHT_RANGE || dist < 0.1) return false;
        Vec3 facing = Vec3.directionFromRotation(getXRot(), getYHeadRot());
        if (facing.dot(toPlayer.normalize()) < 0.15) return false; // its face must point at you
        if (dist > 4 && level().getMaxLocalRawBrightness(blockPosition()) < 4 && !p.hasEffect(MobEffects.NIGHT_VISION)) return false;
        double angle = Math.atan2(0.45, dist) + 0.04;
        Vec3 look = p.getViewVector(1.0F);
        return look.dot(head.subtract(p.getEyePosition()).normalize()) > Math.cos(angle) && p.hasLineOfSight(this);
    }

    private void trigger(Player p) {
        if (com.breachprotocol.career.Career.isAscended(p)) return;
        boolean isNew = targets.add(p.getUUID());
        if (!isNew) return;
        p.displayClientMessage(Component.translatable("message.breachprotocol.scp096.seen").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), true);
        p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0));
        if (getRageState() == CALM) {
            setRageState(TRIGGERED);
            windup = WINDUP_TICKS;
            getNavigation().stop();
            level().playSound(null, getX(), getY(), getZ(), ModSounds.SCP096_SCREAM.get(), SoundSource.HOSTILE, 4.0F, 1.0F);
        }
    }

    // ---------------------------------------------------------------- tick

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide || !(level() instanceof ServerLevel server)) return;

        if (!Anomaly.isContained(this) && tickCount % 2 == 0) {
            for (Player p : server.players()) {
                if (p.distanceToSqr(this) < SIGHT_RANGE * SIGHT_RANGE && seesFace(p)) trigger(p);
            }
        }

        switch (getRageState()) {
            case CALM -> {
                if (cryCooldown-- <= 0) {
                    playSound(ModSounds.SCP096_CRY.get(), 1.0F, 0.9F + random.nextFloat() * 0.2F);
                    cryCooldown = 70 + random.nextInt(40);
                }
            }
            case TRIGGERED -> {
                getNavigation().stop();
                setDeltaMovement(0, getDeltaMovement().y, 0);
                if (windup % 40 == 0 && windup > 0) {
                    playSound(ModSounds.SCP096_SCREAM.get(), 3.0F, 0.8F + random.nextFloat() * 0.3F);
                }
                if (--windup <= 0) setRageState(RAGE);
            }
            case RAGE -> rageTick(server);
            default -> {}
        }
    }

    private void rageTick(ServerLevel server) {
        if (Anomaly.isContained(this)) return;
        LivingEntity victim = nearestTarget(server);
        if (victim == null) {
            calmDown();
            return;
        }
        setTarget(victim);
        getLookControl().setLookAt(victim, 60F, 60F);
        double d2 = distanceToSqr(victim);
        if (repathCooldown-- <= 0) {
            boolean moving = getNavigation().moveTo(victim, 2.6);
            if (!moving || getNavigation().isDone()) {
                getMoveControl().setWantedPosition(victim.getX(), victim.getY(), victim.getZ(), 2.6);
            }
            repathCooldown = 6;
        }
        if (smashCooldown-- <= 0) {
            smashAhead(victim);
            smashCooldown = 4;
        }
        if (d2 < 3.2 * 3.2 && getSensing().hasLineOfSight(victim)) {
            swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            doHurtTarget(victim);
        }
        if (tickCount % 30 == 0) playSound(ModSounds.SCP096_SCREAM.get(), 3.0F, 1.1F);
    }

    /** Rips through doors, glass, wood, leaves - anything with blast resistance under 6. */
    private void smashAhead(LivingEntity victim) {
        if (!level().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) return;
        Vec3 dir = victim.position().subtract(position()).multiply(1, 0, 1);
        if (dir.lengthSqr() < 0.01) return;
        dir = dir.normalize();
        for (double reach = 0.6; reach <= 1.4; reach += 0.8) {
            BlockPos base = BlockPos.containing(getX() + dir.x * reach, getY(), getZ() + dir.z * reach);
            for (int dy = 0; dy < 3; dy++) {
                BlockPos bp = base.above(dy);
                BlockState bs = level().getBlockState(bp);
                if (bs.isAir()) continue;
                float hardness = bs.getDestroySpeed(level(), bp);
                if (hardness >= 0 && bs.getBlock().getExplosionResistance() < 6.0F && !bs.getCollisionShape(level(), bp).isEmpty()) {
                    level().destroyBlock(bp, true, this);
                }
            }
        }
    }

    private LivingEntity nearestTarget(ServerLevel server) {
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        Iterator<UUID> it = targets.iterator();
        while (it.hasNext()) {
            UUID id = it.next();
            Entity e = server.getEntity(id);
            if (!(e instanceof LivingEntity le) || !le.isAlive() || (le instanceof Player pl && (pl.isSpectator() || pl.isCreative()))
                    || le.distanceToSqr(this) > GIVE_UP_RANGE * GIVE_UP_RANGE) {
                it.remove();
                continue;
            }
            double d = le.distanceToSqr(this);
            if (d < bestD) {
                bestD = d;
                best = le;
            }
        }
        return best;
    }

    private void calmDown() {
        targets.clear();
        setTarget(null);
        getNavigation().stop();
        setRageState(CALM);
        cryCooldown = 40;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean r = super.hurt(source, amount);
        if (r && !level().isClientSide && source.getEntity() instanceof Player p && !p.isCreative()) {
            trigger(p); // attacking it counts as provoking it
        }
        return r;
    }

    @Override
    public void setTarget(LivingEntity target) {
        // Only ever targets people who saw its face.
        if (target == null || targets.contains(target.getUUID())) super.setTarget(target);
    }

    @Override
    protected boolean isImmobile() {
        return super.isImmobile() || Anomaly.isContained(this) || getRageState() == TRIGGERED;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return null;
    }

    // ---------------------------------------------------------------- save

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("RageState", getRageState());
        tag.putInt("Windup", windup);
        ListTag list = new ListTag();
        for (UUID id : targets) list.add(NbtUtils.createUUID(id));
        tag.put("Targets", list);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setRageState(tag.getInt("RageState"));
        windup = tag.getInt("Windup");
        targets.clear();
        for (Tag t : tag.getList("Targets", Tag.TAG_INT_ARRAY)) targets.add(NbtUtils.loadUUID(t));
    }

    @Override
    public String designation() {
        return "SCP-096";
    }

    @Override
    public String objectClass() {
        return "Euclid";
    }

    @Override
    public Component statusNote() {
        return switch (getRageState()) {
            case TRIGGERED -> Component.translatable("message.breachprotocol.terminal.096_triggered").withStyle(ChatFormatting.GOLD);
            case RAGE -> Component.translatable("message.breachprotocol.terminal.096_rage", targets.size()).withStyle(ChatFormatting.DARK_RED);
            default -> Component.translatable("message.breachprotocol.terminal.096_calm").withStyle(ChatFormatting.GRAY);
        };
    }
}
