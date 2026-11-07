package com.breachprotocol.registry;

import com.breachprotocol.BreachProtocol;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, BreachProtocol.MODID);

    /** From the Fruit of Life: death passes you by while it lasts. */
    public static final DeferredHolder<MobEffect, MobEffect> IMMORTAL = EFFECTS.register("immortal", () -> new Simple(MobEffectCategory.BENEFICIAL, 0xFFD45A));

    private static final class Simple extends MobEffect {
        Simple(MobEffectCategory c, int colour) { super(c, colour); }
    }

    private ModEffects() {}
}
