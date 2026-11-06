package com.breachprotocol.client;

import com.breachprotocol.network.ClientBlinkState;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** Blink meter (top-center, only near SCP-173) and the eyes-closed blackout. */
public final class BlinkHud {
    private static float closedFade = 0F;

    private BlinkHud() {}

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        int w = g.guiWidth(), h = g.guiHeight();

        float target = ClientBlinkState.closed ? 1F : 0F;
        closedFade = Mth.lerp(0.5F, closedFade, target);
        if (closedFade > 0.02F) {
            int alpha = (int) (closedFade * 250);
            g.fill(0, 0, w, h, alpha << 24);
            if (ClientBlinkState.closed && KeyBindings.CLOSE_EYES.isDown()) {
                Component txt = Component.translatable("hud.breachprotocol.eyes_closed");
                g.drawCenteredString(mc.font, txt, w / 2, h / 2 + 20, 0x55FFFFFF);
            }
        }

        if (!ClientBlinkState.threat) return;
        int barW = 102, x = w / 2 - barW / 2, y = 6;
        float pct = Mth.clamp(ClientBlinkState.meter / 100F, 0F, 1F);
        int fillW = (int) ((barW - 2) * pct);
        int color = pct > 0.5F ? 0xFFE0E0E0 : pct > 0.25F ? 0xFFF0B040 : 0xFFE03030;
        g.fill(x, y, x + barW, y + 7, 0xC0000000);
        g.fill(x + 1, y + 1, x + 1 + fillW, y + 6, color);
        for (int i = 1; i < 10; i++) g.fill(x + 1 + i * 10, y + 1, x + 2 + i * 10, y + 6, 0x60000000);
        g.drawCenteredString(mc.font, Component.translatable("hud.breachprotocol.blink"), w / 2, y + 9, 0xFFB0B0B0);
    }
}
