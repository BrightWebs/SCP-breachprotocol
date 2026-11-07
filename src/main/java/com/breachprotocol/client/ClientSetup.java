package com.breachprotocol.client;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.client.render.O5MemberRenderer;
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
        public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event) {
            event.registerLayerDefinition(com.breachprotocol.client.render.StaffModel.LAYER, com.breachprotocol.client.render.StaffModel::createLayer);
        }

        @SubscribeEvent
        public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(ModEntities.O5_MEMBER.get(), O5MemberRenderer::new);
            event.registerEntityRenderer(ModEntities.STAFF.get(), com.breachprotocol.client.render.StaffRenderer::new);
            event.registerEntityRenderer(ModEntities.NPC_SEAT.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
        }

    }
}
