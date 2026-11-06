package com.breachprotocol.registry;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.effect.SimpleEffect;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, BreachProtocol.MODID);

    /** Applied by Containment Field Generators and orders. Anomalies with this effect cannot act. */
    public static final DeferredHolder<MobEffect, MobEffect> CONTAINED = EFFECTS.register("contained",
            () -> new SimpleEffect(MobEffectCategory.NEUTRAL, 0x5AC8FF));

    /** The real registry holder, so effect-map lookups always use the key Minecraft uses. */
    public static Holder<MobEffect> h(DeferredHolder<MobEffect, MobEffect> holder) {
        return holder.getDelegate();
    }

    public static boolean has(LivingEntity entity, DeferredHolder<MobEffect, MobEffect> effect) {
        return entity.hasEffect(h(effect));
    }

    private ModEffects() {}
}
