package com.breachprotocol.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/** A marker effect whose behaviour lives elsewhere (containment, eyedrops). */
public class SimpleEffect extends MobEffect {
    public SimpleEffect(MobEffectCategory category, int color) {
        super(category, color);
    }
}
