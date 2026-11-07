package com.breachprotocol.network;

import com.breachprotocol.BreachProtocol;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> client: the conversation window (who, what was said, the choices). */
public record DialoguePayload(CompoundTag tag) implements CustomPacketPayload {
    public static final Type<DialoguePayload> TYPE = new Type<>(BreachProtocol.id("dialogue"));
    public static final StreamCodec<ByteBuf, DialoguePayload> STREAM_CODEC = ByteBufCodecs.COMPOUND_TAG.map(DialoguePayload::new, DialoguePayload::tag);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
