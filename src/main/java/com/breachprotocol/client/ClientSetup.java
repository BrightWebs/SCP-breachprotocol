package com.breachprotocol.client;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.client.model.*;
import com.breachprotocol.client.render.GateGuardianRenderer;
import com.breachprotocol.client.render.O5MemberRenderer;
import com.breachprotocol.client.render.Scp0492Renderer;
import com.breachprotocol.client.render.Scp106Renderer;
import com.breachprotocol.client.render.ScpRenderer;
import com.breachprotocol.network.ClientBlinkState;
import com.breachprotocol.network.EyesClosedPayload;
import com.breachprotocol.registry.ModEntities;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ClientSetup {
    private ClientSetup() {}

    @EventBusSubscriber(modid = BreachProtocol.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModBus {
        @SubscribeEvent
        public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event) {
            event.registerLayerDefinition(Scp173Model.LAYER, Scp173Model::createBodyLayer);
            event.registerLayerDefinition(Scp096Model.LAYER, Scp096Model::createBodyLayer);
            event.registerLayerDefinition(Scp049Model.LAYER, Scp049Model::createBodyLayer);
            event.registerLayerDefinition(Scp106Model.LAYER, Scp106Model::createBodyLayer);
            event.registerLayerDefinition(Scp999Model.LAYER, Scp999Model::createBodyLayer);
            event.registerLayerDefinition(GateGuardianModel.LAYER, GateGuardianModel::createBodyLayer);
        }

        @SubscribeEvent
        public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(ModEntities.SCP_173.get(),
                    ctx -> new ScpRenderer<>(ctx, new Scp173Model(ctx.bakeLayer(Scp173Model.LAYER)), 0.6F, 1.3F, "scp_173"));
            event.registerEntityRenderer(ModEntities.SCP_096.get(),
                    ctx -> new ScpRenderer<>(ctx, new Scp096Model(ctx.bakeLayer(Scp096Model.LAYER)), 0.5F, 1.0F, "scp_096"));
            event.registerEntityRenderer(ModEntities.SCP_049.get(),
                    ctx -> new ScpRenderer<>(ctx, new Scp049Model(ctx.bakeLayer(Scp049Model.LAYER)), 0.5F, 1.0F, "scp_049"));
            event.registerEntityRenderer(ModEntities.SCP_999.get(),
                    ctx -> new ScpRenderer<>(ctx, new Scp999Model(ctx.bakeLayer(Scp999Model.LAYER)), 0.7F, 1.0F, "scp_999"));
            event.registerEntityRenderer(ModEntities.SCP_106.get(), Scp106Renderer::new);
            event.registerEntityRenderer(ModEntities.SCP_049_2.get(), Scp0492Renderer::new);
            event.registerEntityRenderer(ModEntities.GATE_GUARDIAN.get(), GateGuardianRenderer::new);
            event.registerEntityRenderer(ModEntities.O5_MEMBER.get(), O5MemberRenderer::new);
        }

        @SubscribeEvent
        public static void keys(RegisterKeyMappingsEvent event) {
            event.register(KeyBindings.CLOSE_EYES);
        }

        @SubscribeEvent
        public static void gui(RegisterGuiLayersEvent event) {
            event.registerAboveAll(BreachProtocol.id("blink"), BlinkHud::render);
        }
    }

    @EventBusSubscriber(modid = BreachProtocol.MODID, value = Dist.CLIENT)
    public static final class GameBus {
        private static boolean lastHeld = false;

        @SubscribeEvent
        public static void tick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.getConnection() == null) {
                lastHeld = false;
                ClientBlinkState.threat = false;
                ClientBlinkState.closed = false;
                return;
            }
            boolean held = KeyBindings.CLOSE_EYES.isDown() && mc.screen == null;
            if (held != lastHeld) {
                lastHeld = held;
                PacketDistributor.sendToServer(new EyesClosedPayload(held));
            }
        }
    }
}
