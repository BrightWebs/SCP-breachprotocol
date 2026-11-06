package com.breachprotocol.network;

import com.breachprotocol.BreachProtocol;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> client: everything the phone displays. */
public record CareerSyncPayload(CompoundTag tag) implements CustomPacketPayload {
    public static final Type<CareerSyncPayload> TYPE = new Type<>(BreachProtocol.id("career_sync"));
    public static final StreamCodec<ByteBuf, CareerSyncPayload> STREAM_CODEC =
            ByteBufCodecs.COMPOUND_TAG.map(CareerSyncPayload::new, CareerSyncPayload::tag);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
