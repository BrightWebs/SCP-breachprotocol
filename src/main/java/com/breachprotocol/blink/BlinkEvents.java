package com.breachprotocol.blink;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.entity.Scp173Entity;
import com.breachprotocol.network.BlinkSyncPayload;
import com.breachprotocol.registry.ModAttachments;
import com.breachprotocol.registry.ModEffects;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The blink meter only runs while an SCP-173 instance is within 32 blocks. When it empties you
 * blink for 6 ticks - which is all 173 needs. Holding the Close Eyes key (default B) keeps your
 * eyes shut: safe from SCP-096's face, fatal near SCP-173. Opening your eyes refills the meter.
 */
@EventBusSubscriber(modid = BreachProtocol.MODID)
public final class BlinkEvents {
    private static final double THREAT_RANGE = 32;

    private BlinkEvents() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        BlinkData d = player.getData(ModAttachments.BLINK);

        if (player.tickCount % 5 == 0) {
            d.threat = !player.isSpectator() && !player.level().getEntitiesOfClass(Scp173Entity.class,
                    player.getBoundingBox().inflate(THREAT_RANGE), e -> e.isAlive()).isEmpty();
        }

        if (d.blinkTicks > 0) {
            if (--d.blinkTicks == 0) d.meter = BlinkData.MAX;
        } else if (d.holdingClosed) {
            d.meter = BlinkData.MAX;
        } else if (d.threat && !player.isCreative()) {
            if (!ModEffects.has(player, ModEffects.CLEAR_EYES)) {
                d.meter -= BlinkData.DRAIN_PER_TICK;
            }
            if (d.meter <= 0) {
                d.meter = 0;
                d.blinkTicks = BlinkData.BLINK_TICKS;
            }
        } else {
            d.meter = Math.min(BlinkData.MAX, d.meter + 2.5F);
        }

        boolean closed = d.eyesClosed();
        if (Math.abs(d.meter - d.sentMeter) >= 0.5F || closed != d.sentClosed || d.threat != d.sentThreat) {
            d.sentMeter = d.meter;
            d.sentClosed = closed;
            d.sentThreat = d.threat;
            PacketDistributor.sendToPlayer(player, new BlinkSyncPayload(d.meter, closed, d.threat));
        }
    }

    /** Voluntary blink / eye hold from the client key. */
    public static void setEyesHeld(ServerPlayer player, boolean held) {
        BlinkData d = player.getData(ModAttachments.BLINK);
        d.holdingClosed = held;
        if (!held && d.blinkTicks == 0) d.meter = BlinkData.MAX;
    }
}
