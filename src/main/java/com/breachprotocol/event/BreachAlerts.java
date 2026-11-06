package com.breachprotocol.event;

import com.breachprotocol.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;

import java.util.List;

/** Site-wide containment breach announcement. */
public final class BreachAlerts {
    public static final double ALERT_RADIUS = 160;

    private BreachAlerts() {}

    public static void announce(ServerLevel level, BlockPos origin, List<String> designations) {
        String names = String.join(", ", designations);
        Component title = Component.translatable("message.breachprotocol.breach.title").withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
        Component subtitle = Component.translatable("message.breachprotocol.breach.subtitle", names).withStyle(ChatFormatting.GOLD);
        Component chat = Component.literal("[SITE ALERT] ").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
                .append(Component.translatable("message.breachprotocol.breach.chat", names, origin.getX(), origin.getY(), origin.getZ())
                        .withStyle(ChatFormatting.RED));
        for (ServerPlayer p : level.players()) {
            if (p.blockPosition().closerThan(origin, ALERT_RADIUS)) {
                p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
                p.connection.send(new ClientboundSetTitleTextPacket(title));
                p.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
                p.sendSystemMessage(chat);
            }
        }
        level.playSound(null, origin, ModSounds.BREACH_ALARM.get(), SoundSource.BLOCKS, 6.0F, 1.0F);
        com.breachprotocol.career.Career.onBreach(level, origin, designations);
    }
}
