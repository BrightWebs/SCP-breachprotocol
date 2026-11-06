package com.breachprotocol.client;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.client.render.O5MemberRenderer;
import com.breachprotocol.client.render.Scp0492Renderer;
import com.breachprotocol.client.render.GeoScpRenderer;
import com.breachprotocol.client.render.Scp106GeoRenderer;
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
        public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
            // name, scale, shadow, turns head, glowmask, full-bright
            event.registerEntityRenderer(ModEntities.SCP_173.get(), ctx -> new GeoScpRenderer<>(ctx, "scp_173", 1.0F, 0.6F, false, false, false));
            event.registerEntityRenderer(ModEntities.SCP_096.get(), ctx -> new GeoScpRenderer<>(ctx, "scp_096", 1.0F, 0.5F, false, false, false));
            event.registerEntityRenderer(ModEntities.SCP_049.get(), ctx -> new GeoScpRenderer<>(ctx, "scp_049", 1.0F, 0.5F, true, true, false));
            event.registerEntityRenderer(ModEntities.SCP_999.get(), ctx -> new GeoScpRenderer<>(ctx, "scp_999", 1.1F, 0.6F, false, false, false));
            event.registerEntityRenderer(ModEntities.SCP_106.get(), Scp106GeoRenderer::new);
            event.registerEntityRenderer(ModEntities.GATE_GUARDIAN.get(), ctx -> new GeoScpRenderer<>(ctx, "gate_guardian", 2.6F, 0.0F, false, true, true));
            event.registerEntityRenderer(ModEntities.SCP_049_2.get(), Scp0492Renderer::new);
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
