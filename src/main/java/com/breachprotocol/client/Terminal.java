package com.breachprotocol.client;

import net.minecraft.client.gui.GuiGraphics;

/**
 * The phone's green phosphor terminal look: black glass, green text, boxed panels, square edges.
 * {@link #map} turns any colour into the matching terminal shade, keeping red for danger and amber
 * for warnings, so every app follows the theme.
 */
public final class Terminal {
    private Terminal() {}

    public static final int BG = 0xFF030805;
    public static final int PANEL = 0xFF06120A;
    public static final int GREEN = 0xFF5CFF8A;
    public static final int BRIGHT = 0xFFB8FFCC;
    public static final int MID = 0xFF35B860;
    public static final int DIM = 0xFF2A7A44;
    public static final int LINE = 0xFF1F5A33;
    public static final int RED = 0xFFFF5A5A;
    public static final int AMBER = 0xFFFFB347;
    /** Selected-row fill (dark text goes on top). */
    public static final int SELECT = 0xFF5CE68A;
    public static final int ON_SELECT = 0xFF04140A;

    /** Recolour anything into the terminal palette by brightness. Alpha is kept. */
    public static int map(int c) {
        int a = c >>> 24, r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        float lum = (0.3F * r + 0.59F * g + 0.11F * b) / 255F;
        boolean saturated = max - min > 60;
        if (saturated && r > g + 50 && r > b + 50) {                // reds: danger
            float k = Math.max(0.35F, Math.min(1F, max / 230F));
            return a << 24 | rgb(255 * k, 80 * k, 80 * k);
        }
        if (saturated && r > b + 70 && g > b + 30 && r >= g * 0.95F) { // oranges and yellows: warnings
            float k = Math.max(0.35F, Math.min(1F, max / 230F));
            return a << 24 | rgb(255 * k, 179 * k, 71 * k);
        }
        float k = (float) Math.pow(lum, 0.85);                     // everything else: phosphor green
        if (saturated) k = Math.max(k, 0.28F);                      // coloured fills read as dim green panels
        return a << 24 | rgb(92 * k + 3 * (1 - k), 255 * k + 8 * (1 - k), 138 * k + 5 * (1 - k));
    }

    private static int rgb(float r, float g, float b) {
        return Math.min(255, (int) r) << 16 | Math.min(255, (int) g) << 8 | Math.min(255, (int) b);
    }

    /** 1px outline. */
    public static void box(GuiGraphics g, int x, int y, int w, int h, int c) {
        g.fill(x, y, x + w, y + 1, c);
        g.fill(x, y + h - 1, x + w, y + h, c);
        g.fill(x, y, x + 1, y + h, c);
        g.fill(x + w - 1, y, x + w, y + h, c);
    }

    /** Dashed horizontal rule. */
    public static void dashes(GuiGraphics g, int x, int y, int w, int c) {
        for (int i = 0; i < w; i += 4) g.fill(x + i, y, x + Math.min(w, i + 2), y + 1, c);
    }

    /** Faint CRT scanlines over an area. */
    public static void scanlines(GuiGraphics g, int x, int y, int w, int h) {
        for (int i = 0; i < h; i += 2) g.fill(x, y + i, x + w, y + i + 1, 0x14000000);
    }
}
