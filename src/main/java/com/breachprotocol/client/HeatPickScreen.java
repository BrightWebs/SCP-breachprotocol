package com.breachprotocol.client;

import com.breachprotocol.network.PhoneActionPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Heat vision's strengths, side by side like a Manas skill's stages: pick one and it's on. */
public class HeatPickScreen extends Screen {
    private static final int[] PCT = {5, 15, 50, 80, 100};
    private static final String[] NAME = {"Warm thread", "Burning beam", "Lance", "Cutter", "Annihilation"};
    private static final String[][] WHAT = {
            {"Sets alight what it touches.", "Hurts, a little. 16 blocks."},
            {"Real harm, twice the reach.", "The ground it lands on catches."},
            {"Straight through every living", "thing in its line. Boom on each."},
            {"Cuts stone and wood. Boils water,", "melts ice and snow. 64 blocks."},
            {"Bores a tunnel through anything", "breakable, and blows apart."}};
    private static final int[] COL = {0xFFFFB060, 0xFFFF8030, 0xFFFF4A20, 0xFFFF2020, 0xFFFF0060};
    private final int now;
    private int hover = -1;

    public HeatPickScreen(int now) { super(Component.literal("Heat Vision")); this.now = now; }

    private int cw() { return Math.min(108, (width - 40) / 5 - 6); }
    private int x0() { return width / 2 - (cw() * 5 + 6 * 4) / 2; }
    private int y0() { return height / 2 - 50; }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0xB0100004);
        g.drawCenteredString(font, "HEAT VISION", width / 2, y0() - 34, 0xFFFF6040);
        g.drawCenteredString(font, "How hot? It stays on until you use the skill again.", width / 2, y0() - 20, 0xFFB09088);
        hover = -1;
        int cw = cw(), ch = 100;
        for (int i = 0; i < 5; i++) {
            int x = x0() + i * (cw + 6), y = y0();
            boolean h = mx >= x && mx < x + cw && my >= y && my < y + ch;
            if (h) hover = i;
            int c = COL[i];
            g.fill(x, y, x + cw, y + ch, h ? 0xE0301008 : 0xD0180806);
            int rim = h || i == now ? c : 0xFF5A2A20;
            g.fill(x, y, x + cw, y + 1, rim); g.fill(x, y + ch - 1, x + cw, y + ch, rim); g.fill(x, y, x + 1, y + ch, rim); g.fill(x + cw - 1, y, x + cw, y + ch, rim);
            // the heat, as a bar
            int bar = (int) ((cw - 12) * PCT[i] / 100.0);
            g.fill(x + 6, y + 40, x + cw - 6, y + 44, 0xFF2A1410);
            g.fill(x + 6, y + 40, x + 6 + Math.max(2, bar), y + 44, c);
            g.pose().pushPose();
            g.pose().translate(x + cw / 2F, y + 10, 0);
            g.pose().scale(2F, 2F, 1F);
            g.drawCenteredString(font, PCT[i] + "%", 0, 0, c);
            g.pose().popPose();
            g.drawCenteredString(font, NAME[i], x + cw / 2, y + 50, 0xFFF0D8D0);
            g.pose().pushPose();
            g.pose().translate(x + cw / 2F, y + 66, 0);
            g.pose().scale(0.7F, 0.7F, 1F);
            for (int k = 0; k < 2; k++) g.drawCenteredString(font, WHAT[i][k], 0, k * 11, 0xFFB8A098);
            g.pose().popPose();
            if (i == now) g.drawCenteredString(font, "ON", x + cw / 2, y + ch - 12, c);
        }
        int bx = width / 2 - 40, by = y0() + 112;
        boolean ho = now >= 0 && mx >= bx && mx < bx + 80 && my >= by && my < by + 16;
        if (now >= 0) {
            g.fill(bx, by, bx + 80, by + 16, ho ? 0xFF4A2A2A : 0xFF2A1616);
            g.drawCenteredString(font, "Turn it off", width / 2, by + 4, 0xFFE0C0C0);
        }
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (hover >= 0) { send("heat:on:" + hover); onClose(); return true; }
        int bx = width / 2 - 40, by = y0() + 112;
        if (now >= 0 && mx >= bx && mx < bx + 80 && my >= by && my < by + 16) { send("heat:off"); onClose(); return true; }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int sc, int mods) {
        if (key >= 49 && key <= 53) { send("heat:on:" + (key - 49)); onClose(); return true; } // 1..5
        return super.keyPressed(key, sc, mods);
    }

    private static void send(String a) { PacketDistributor.sendToServer(new PhoneActionPayload(a, 0)); }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {}

    @Override
    public boolean isPauseScreen() { return false; }
}
