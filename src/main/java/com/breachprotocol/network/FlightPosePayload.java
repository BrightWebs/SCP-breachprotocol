package com.breachprotocol.network;

import com.breachprotocol.BreachProtocol;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> the players watching: a flier has dropped into (or come out of) the take-off stance. */
public record FlightPosePayload(int entity, int state) implements CustomPacketPayload {
    public static final Type<FlightPosePayload> TYPE = new Type<>(BreachProtocol.id("flight_pose"));
    public static final StreamCodec<ByteBuf, FlightPosePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, FlightPosePayload::entity,
            ByteBufCodecs.VAR_INT, FlightPosePayload::state,
            FlightPosePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
