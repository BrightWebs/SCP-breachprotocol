package com.breachprotocol.network;

import com.breachprotocol.BreachProtocol;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> server: a choice picked in the conversation window (and any typed text). */
public record DialogueActionPayload(String action, String text) implements CustomPacketPayload {
    public static final Type<DialogueActionPayload> TYPE = new Type<>(BreachProtocol.id("dialogue_action"));
    public static final StreamCodec<ByteBuf, DialogueActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, DialogueActionPayload::action,
            ByteBufCodecs.STRING_UTF8, DialogueActionPayload::text,
            DialogueActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
