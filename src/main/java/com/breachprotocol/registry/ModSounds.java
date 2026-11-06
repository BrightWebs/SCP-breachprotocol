package com.breachprotocol.registry;

import com.breachprotocol.BreachProtocol;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, BreachProtocol.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> SCP173_SCRAPE = reg("scp173.scrape");
    public static final DeferredHolder<SoundEvent, SoundEvent> SCP173_SNAP = reg("scp173.snap");
    public static final DeferredHolder<SoundEvent, SoundEvent> SCP096_CRY = reg("scp096.cry");
    public static final DeferredHolder<SoundEvent, SoundEvent> SCP096_SCREAM = reg("scp096.scream");
    public static final DeferredHolder<SoundEvent, SoundEvent> SCP049_AMBIENT = reg("scp049.ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> SCP106_EMERGE = reg("scp106.emerge");
    public static final DeferredHolder<SoundEvent, SoundEvent> SCP106_LAUGH = reg("scp106.laugh");
    public static final DeferredHolder<SoundEvent, SoundEvent> SCP999_GIGGLE = reg("scp999.giggle");
    public static final DeferredHolder<SoundEvent, SoundEvent> BREACH_ALARM = reg("breach.alarm");
    public static final DeferredHolder<SoundEvent, SoundEvent> KEYCARD_ACCEPT = reg("keycard.accept");
    public static final DeferredHolder<SoundEvent, SoundEvent> KEYCARD_DENY = reg("keycard.deny");
    public static final DeferredHolder<SoundEvent, SoundEvent> FIELD_HUM = reg("field.hum");
    public static final DeferredHolder<SoundEvent, SoundEvent> PHONE_RING = reg("phone.ring");
    public static final DeferredHolder<SoundEvent, SoundEvent> PHONE_MESSAGE = reg("phone.message");
    public static final DeferredHolder<SoundEvent, SoundEvent> ASCENSION = reg("ascension");
    public static final DeferredHolder<SoundEvent, SoundEvent> GUARDIAN_VOICE = reg("guardian.voice");

    private static DeferredHolder<SoundEvent, SoundEvent> reg(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(BreachProtocol.id(name)));
    }

    private ModSounds() {}
}
