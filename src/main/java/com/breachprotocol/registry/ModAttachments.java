package com.breachprotocol.registry;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.blink.BlinkData;
import com.breachprotocol.career.CareerData;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, BreachProtocol.MODID);

    /** Transient (not saved): blinking resets on relog, which is fine. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<BlinkData>> BLINK =
            ATTACHMENTS.register("blink", () -> AttachmentType.builder(() -> new BlinkData()).build());

    /** Rank, XP, phone messages, missions. Saved, and survives death. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<CareerData>> CAREER =
            ATTACHMENTS.register("career", () -> AttachmentType.serializable(() -> new CareerData()).copyOnDeath().build());

    private ModAttachments() {}
}
