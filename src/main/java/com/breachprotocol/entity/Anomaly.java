package com.breachprotocol.entity;

import com.breachprotocol.registry.ModEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/** Anything the Foundation tracks. Containment generators and the site terminal work on these. */
public interface Anomaly {
    String designation();

    /** Safe / Euclid / Keter. */
    String objectClass();

    default ChatFormatting objectClassColor() {
        return switch (objectClass()) {
            case "Safe" -> ChatFormatting.GREEN;
            case "Euclid" -> ChatFormatting.YELLOW;
            default -> ChatFormatting.RED;
        };
    }

    /** Whether a containment field affects it at all. */
    default boolean canBeContained() {
        return true;
    }

    /** Optional extra line in the site terminal. */
    @Nullable
    default Component statusNote() {
        return null;
    }

    static boolean isContained(LivingEntity entity) {
        return ModEffects.has(entity, ModEffects.CONTAINED);
    }
}
