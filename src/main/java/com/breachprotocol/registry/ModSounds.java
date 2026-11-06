package com.breachprotocol.registry;

import com.breachprotocol.BreachProtocol;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, BreachProtocol.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> BREACH_ALARM = reg("breach.alarm");
    public static final DeferredHolder<SoundEvent, SoundEvent> PHONE_RING = reg("phone.ring");
    public static final DeferredHolder<SoundEvent, SoundEvent> PHONE_MESSAGE = reg("phone.message");
    public static final DeferredHolder<SoundEvent, SoundEvent> ASCENSION = reg("ascension");

    private static DeferredHolder<SoundEvent, SoundEvent> reg(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(BreachProtocol.id(name)));
    }

    private ModSounds() {}
}
