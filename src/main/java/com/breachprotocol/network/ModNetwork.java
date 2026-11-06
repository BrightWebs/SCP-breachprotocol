package com.breachprotocol.network;

import com.breachprotocol.blink.BlinkEvents;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetwork {
    private ModNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(BlinkSyncPayload.TYPE, BlinkSyncPayload.STREAM_CODEC, (payload, ctx) ->
                ctx.enqueueWork(() -> {
                    ClientBlinkState.meter = payload.meter();
                    ClientBlinkState.closed = payload.closed();
                    ClientBlinkState.threat = payload.threat();
                }));
        registrar.playToClient(CareerSyncPayload.TYPE, CareerSyncPayload.STREAM_CODEC, (payload, ctx) ->
                ctx.enqueueWork(() -> {
                    ClientCareerState.tag = payload.tag();
                    ClientCareerState.version++;
                }));
        registrar.playToServer(PhoneActionPayload.TYPE, PhoneActionPayload.STREAM_CODEC, (payload, ctx) ->
                ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof ServerPlayer sp) PhoneActions.handle(sp, payload.action(), payload.arg());
                }));
        registrar.playToServer(EyesClosedPayload.TYPE, EyesClosedPayload.STREAM_CODEC, (payload, ctx) ->
                ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof ServerPlayer sp) BlinkEvents.setEyesHeld(sp, payload.closed());
                }));
    }
}
