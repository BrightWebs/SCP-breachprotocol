package com.breachprotocol.network;

import com.breachprotocol.BreachProtocol;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> server: the picture a phone camera just took (a small PNG). */
public record PhotoUploadPayload(int img, byte[] png) implements CustomPacketPayload {
    public static final int MAX_BYTES = 30000;
    public static final Type<PhotoUploadPayload> TYPE = new Type<>(BreachProtocol.id("photo_upload"));
    public static final StreamCodec<ByteBuf, PhotoUploadPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PhotoUploadPayload::img,
            ByteBufCodecs.byteArray(MAX_BYTES), PhotoUploadPayload::png,
            PhotoUploadPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
