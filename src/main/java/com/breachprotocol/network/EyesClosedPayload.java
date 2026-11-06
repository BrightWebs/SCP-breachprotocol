package com.breachprotocol.network;

import com.breachprotocol.BreachProtocol;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> server: player is holding / released the Close Eyes key. */
public record EyesClosedPayload(boolean closed) implements CustomPacketPayload {
    public static final Type<EyesClosedPayload> TYPE = new Type<>(BreachProtocol.id("eyes_closed"));
    public static final StreamCodec<ByteBuf, EyesClosedPayload> STREAM_CODEC =
            ByteBufCodecs.BOOL.map(EyesClosedPayload::new, EyesClosedPayload::closed);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
