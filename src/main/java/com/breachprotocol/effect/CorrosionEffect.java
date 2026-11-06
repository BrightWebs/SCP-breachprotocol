package com.breachprotocol.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

public class CorrosionEffect extends MobEffect {
    public CorrosionEffect() {
        super(MobEffectCategory.HARMFUL, 0x2A1E14);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % Math.max(10, 40 >> amplifier) == 0;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (!entity.level().isClientSide) {
            entity.hurt(entity.damageSources().wither(), 1.0F);
        }
        return true;
    }
}
