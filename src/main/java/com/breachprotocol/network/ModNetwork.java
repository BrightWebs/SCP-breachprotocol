package com.breachprotocol.network;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetwork {
    private ModNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(CareerSyncPayload.TYPE, CareerSyncPayload.STREAM_CODEC, (payload, ctx) ->
                ctx.enqueueWork(() -> {
                    ClientCareerState.tag = payload.tag();
                    ClientCareerState.version++;
                }));
        registrar.playToServer(PhoneActionPayload.TYPE, PhoneActionPayload.STREAM_CODEC, (payload, ctx) ->
                ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof ServerPlayer sp) PhoneActions.handle(sp, payload.action(), payload.arg());
                }));
        registrar.playToServer(PhotoUploadPayload.TYPE, PhotoUploadPayload.STREAM_CODEC, (payload, ctx) ->
                ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof ServerPlayer sp) com.breachprotocol.career.PhotoStore.upload(sp, payload.img(), payload.png());
                }));
        registrar.playToServer(PhotoRequestPayload.TYPE, PhotoRequestPayload.STREAM_CODEC, (payload, ctx) ->
                ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof ServerPlayer sp) com.breachprotocol.career.PhotoStore.request(sp, payload.img());
                }));
        registrar.playToClient(DialoguePayload.TYPE, DialoguePayload.STREAM_CODEC, (payload, ctx) ->
                ctx.enqueueWork(() -> ClientDialogueBridge.received(payload.tag())));
        registrar.playToServer(DialogueActionPayload.TYPE, DialogueActionPayload.STREAM_CODEC, (payload, ctx) ->
                ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof ServerPlayer sp) com.breachprotocol.entity.Dialogue.action(sp, payload.action(), payload.text());
                }));
        registrar.playToClient(FlightPosePayload.TYPE, FlightPosePayload.STREAM_CODEC, (payload, ctx) ->
                ctx.enqueueWork(() -> {
                    int id = payload.entity();
                    if (payload.state() == 10) { com.breachprotocol.world.FlightState.autoOn = true; com.breachprotocol.world.FlightState.autoTarget = id; return; }
                    if (payload.state() >= 20 && payload.state() <= 24) { com.breachprotocol.world.FlightState.HEAT.put(id, payload.state() - 20); return; }
                    if (payload.state() == 25) { com.breachprotocol.world.FlightState.HEAT.remove(id); return; }
                    if (payload.state() == 12) { com.breachprotocol.world.FlightState.autoTactic = id; return; }
                    if (payload.state() == 11) { com.breachprotocol.world.FlightState.autoOn = false; com.breachprotocol.world.FlightState.autoTarget = -1; return; }
                    if (payload.state() == 3) { com.breachprotocol.world.FlightState.NINJA.add(id); return; }
                    if (payload.state() == 4) { com.breachprotocol.world.FlightState.NINJA.remove(id); return; }
                    if (payload.state() == 1) com.breachprotocol.world.FlightState.CHARGING.add(id); else com.breachprotocol.world.FlightState.CHARGING.remove(id);
                    if (payload.state() == 2) com.breachprotocol.world.FlightState.RAMMING.add(id); else com.breachprotocol.world.FlightState.RAMMING.remove(id);
                }));
        registrar.playToClient(PhotoDataPayload.TYPE, PhotoDataPayload.STREAM_CODEC, (payload, ctx) ->
                ctx.enqueueWork(() -> ClientPhotoBridge.received(payload.img(), payload.png())));
    }
}
