package com.breachprotocol.entity;

import com.breachprotocol.registry.ModEntities;
import com.breachprotocol.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * SCP-049, the plague doctor. Slow and polite, and convinced you carry "the pestilence".
 * Its touch withers. Anyone (player or villager) who dies by its hand is "cured" -
 * they rise again as SCP-049-2 and follow the doctor.
 */
public class Scp049Entity extends Monster implements Anomaly {
    private static final int LINES = 6;
    private int speechCooldown = 60;

    public Scp049Entity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 25;
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 140)
                .add(Attributes.ARMOR, 8)
                .add(Attributes.MOVEMENT_SPEED, 0.24)
                .add(Attributes.ATTACK_DAMAGE, 7)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6)
                .add(Attributes.FOLLOW_RANGE, 40);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, false));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.7));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this, Scp0492Entity.class));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, AbstractVillager.class, true));
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && target instanceof LivingEntity living) {
            living.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 1), this);
            living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1), this);
        }
        return hit;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!level().isClientSide && getTarget() instanceof Player p && speechCooldown-- <= 0 && distanceToSqr(p) < 20 * 20) {
            speak(p);
            speechCooldown = 160 + random.nextInt(120);
        }
    }

    private void speak(Player p) {
        Component line = Component.literal("SCP-049: ").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.translatable("entity.breachprotocol.scp_049.line." + random.nextInt(LINES)).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        p.displayClientMessage(line, true);
        playSound(ModSounds.SCP049_AMBIENT.get(), 1.0F, 1.0F);
    }

    /** Called from the death event when something dies to this doctor. */
    public void cure(LivingEntity victim) {
        if (!(level() instanceof ServerLevel server)) return;
        Scp0492Entity risen = ModEntities.SCP_049_2.get().create(server);
        if (risen == null) return;
        risen.moveTo(victim.getX(), victim.getY(), victim.getZ(), victim.getYRot(), 0);
        risen.finalizeSpawn(server, server.getCurrentDifficultyAt(risen.blockPosition()), MobSpawnType.CONVERSION, null);
        risen.setCustomName(Component.translatable("entity.breachprotocol.scp_049_2.named", victim.getName()));
        risen.setPersistenceRequired();
        server.addFreshEntityWithPassengers(risen);
        for (Player p : server.players()) {
            if (p.distanceToSqr(this) < 24 * 24) {
                p.displayClientMessage(Component.literal("SCP-049: ").withStyle(ChatFormatting.DARK_GRAY)
                        .append(Component.translatable("entity.breachprotocol.scp_049.cured").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)), true);
            }
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public String designation() {
        return "SCP-049";
    }

    @Override
    public String objectClass() {
        return "Euclid";
    }
}
