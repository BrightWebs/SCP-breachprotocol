package com.breachprotocol.registry;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.effect.ColaFeverEffect;
import com.breachprotocol.effect.CorrosionEffect;
import com.breachprotocol.effect.RadiationEffect;
import com.breachprotocol.effect.SimpleEffect;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, BreachProtocol.MODID);

    /** Applied by Containment Field Generators. Anomalies with this effect cannot act. */
    public static final DeferredHolder<MobEffect, MobEffect> CONTAINED = EFFECTS.register("contained",
            () -> new SimpleEffect(MobEffectCategory.NEUTRAL, 0x5AC8FF));

    /** SCP-207. Every bottle stacks: faster, and it hurts more often. */
    public static final DeferredHolder<MobEffect, MobEffect> COLA_FEVER = EFFECTS.register("cola_fever",
            () -> new ColaFeverEffect()
                    .addAttributeModifier(Attributes.MOVEMENT_SPEED, BreachProtocol.id("effect.cola_fever.speed"), 0.18, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
                    .addAttributeModifier(Attributes.ATTACK_SPEED, BreachProtocol.id("effect.cola_fever.haste"), 0.10, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));

    /** SCP-106's touch. Rots you, slows you. SCP-999 can clean it off. */
    public static final DeferredHolder<MobEffect, MobEffect> CORROSION = EFFECTS.register("corrosion",
            () -> new CorrosionEffect()
                    .addAttributeModifier(Attributes.MOVEMENT_SPEED, BreachProtocol.id("effect.corrosion.slow"), -0.2, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));

    /** Eyedrops: your blink meter stops draining. */
    public static final DeferredHolder<MobEffect, MobEffect> CLEAR_EYES = EFFECTS.register("clear_eyes",
            () -> new SimpleEffect(MobEffectCategory.BENEFICIAL, 0x7FD3FF));

    /** Reactor radiation. Milk or SCP-500 cures it. */
    public static final DeferredHolder<MobEffect, MobEffect> RADIATION = EFFECTS.register("radiation", RadiationEffect::new);

    /**
     * Unwraps a DeferredHolder to the real registry holder, so effect-map lookups always use the
     * same key object Minecraft does (e.g. after an entity is loaded from disk).
     */
    public static Holder<MobEffect> h(DeferredHolder<MobEffect, MobEffect> holder) {
        return holder.getDelegate();
    }

    public static boolean has(LivingEntity entity, DeferredHolder<MobEffect, MobEffect> effect) {
        return entity.hasEffect(h(effect));
    }

    private ModEffects() {}
}
