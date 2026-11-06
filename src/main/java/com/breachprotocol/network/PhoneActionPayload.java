package com.breachprotocol.network;

import com.breachprotocol.BreachProtocol;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> server: a button pressed on the phone. */
public record PhoneActionPayload(String action, int arg) implements CustomPacketPayload {
    public static final Type<PhoneActionPayload> TYPE = new Type<>(BreachProtocol.id("phone_action"));
    public static final StreamCodec<ByteBuf, PhoneActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, PhoneActionPayload::action,
            ByteBufCodecs.VAR_INT, PhoneActionPayload::arg,
            PhoneActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
