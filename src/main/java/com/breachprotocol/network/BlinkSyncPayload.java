package com.breachprotocol.network;

import com.breachprotocol.BreachProtocol;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> client: blink meter for the HUD. */
public record BlinkSyncPayload(float meter, boolean closed, boolean threat) implements CustomPacketPayload {
    public static final Type<BlinkSyncPayload> TYPE = new Type<>(BreachProtocol.id("blink_sync"));
    public static final StreamCodec<ByteBuf, BlinkSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, BlinkSyncPayload::meter,
            ByteBufCodecs.BOOL, BlinkSyncPayload::closed,
            ByteBufCodecs.BOOL, BlinkSyncPayload::threat,
            BlinkSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
