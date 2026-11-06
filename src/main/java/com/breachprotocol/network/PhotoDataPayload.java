package com.breachprotocol.network;

import com.breachprotocol.BreachProtocol;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> client: a photo's picture. Empty when the server doesn't have it. */
public record PhotoDataPayload(int img, byte[] png) implements CustomPacketPayload {
    public static final Type<PhotoDataPayload> TYPE = new Type<>(BreachProtocol.id("photo_data"));
    public static final StreamCodec<ByteBuf, PhotoDataPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PhotoDataPayload::img,
            ByteBufCodecs.byteArray(PhotoUploadPayload.MAX_BYTES), PhotoDataPayload::png,
            PhotoDataPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
