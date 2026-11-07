package com.breachprotocol.registry;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.career.CareerData;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, BreachProtocol.MODID);

    /** Rank, XP, phone messages, missions. Saved, and survives death. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<CareerData>> CAREER =
            ATTACHMENTS.register("career", () -> AttachmentType.serializable(() -> new CareerData()).copyOnDeath().build());

    /** Garden chunks already shaped (edge trimmed, river carved, fruit trees planted). */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> GARDEN_DONE =
            ATTACHMENTS.register("garden_done", () -> AttachmentType.builder(() -> false).serialize(com.mojang.serialization.Codec.BOOL).build());

    private ModAttachments() {}
}
