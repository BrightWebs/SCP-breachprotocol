package com.breachprotocol.network;

import com.breachprotocol.BreachProtocol;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> server: send me the picture for a photo in my gallery. */
public record PhotoRequestPayload(int img) implements CustomPacketPayload {
    public static final Type<PhotoRequestPayload> TYPE = new Type<>(BreachProtocol.id("photo_request"));
    public static final StreamCodec<ByteBuf, PhotoRequestPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PhotoRequestPayload::img, PhotoRequestPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
