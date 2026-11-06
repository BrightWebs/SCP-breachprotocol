package com.breachprotocol.client;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.client.render.O5MemberRenderer;
import com.breachprotocol.client.render.GeoScpRenderer;
import com.breachprotocol.registry.ModEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public final class ClientSetup {
    private ClientSetup() {}

    @EventBusSubscriber(modid = BreachProtocol.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModBus {
        @SubscribeEvent
        public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
            // name, scale, shadow, turns head, glowmask, full-bright
            event.registerEntityRenderer(ModEntities.GATE_GUARDIAN.get(), ctx -> new GeoScpRenderer<>(ctx, "gate_guardian", 2.6F, 0.0F, false, true, true));
            event.registerEntityRenderer(ModEntities.O5_MEMBER.get(), O5MemberRenderer::new);
        }

    }
}
