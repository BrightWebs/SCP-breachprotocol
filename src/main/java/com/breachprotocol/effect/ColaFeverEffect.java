package com.breachprotocol.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

public class ColaFeverEffect extends MobEffect {
    public ColaFeverEffect() {
        super(MobEffectCategory.NEUTRAL, 0x8B2A16);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        int interval = Math.max(10, 80 >> amplifier);
        return duration % interval == 0;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (!entity.level().isClientSide) {
            entity.hurt(entity.damageSources().magic(), 1.0F);
        }
        return true;
    }
}
