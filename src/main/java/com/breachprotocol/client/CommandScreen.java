package com.breachprotocol.client;

import com.breachprotocol.career.Dominion;
import com.breachprotocol.network.ClientCareerState;
import com.breachprotocol.network.PhoneActionPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * The Dominion console (tap the command key): your aura (SCP-Ω), the implant's abilities with their
 * cooldowns, and orders for everything around you. Holding the key instead commands one creature.
 */
public class CommandScreen extends Screen {
    private record Hit(int x, int y, int w, int h, Runnable run) {}

    private final List<Hit> hits = new ArrayList<>();
    private int hover = -1;

    public CommandScreen() { super(Component.literal("Dominion")); }

    private static CompoundTag t() { return ClientCareerState.tag; }

    private static void send(String action) { PacketDistributor.sendToServer(new PhoneActionPayload(action, 0)); }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        hits.clear();
        int dom = t().getInt("dom");
        boolean omega = dom >= 2;
        int pw = Math.min(360, width - 20), ph = omega ? 248 : 206, x = (width - pw) / 2, y = (height - ph) / 2;
        int accent = omega ? 0xFFB070FF : 0xFFE0B040;
        g.fill(x, y, x + pw, y + ph, 0xF00C0812);
        g.fill(x, y, x + pw, y + 1, accent);
        g.fill(x, y + ph - 1, x + pw, y + ph, accent);
        g.fill(x, y, x + 1, y + ph, 0x60FFFFFF);
        g.fill(x + pw - 1, y, x + pw, y + ph, 0x60FFFFFF);
        String title = omega ? "SCP-Ω  ·  DOMINION" : "THE ADMINISTRATOR'S IMPLANT";
        g.drawString(font, title, x + 10, y + 8, accent, false);
        String sub = omega ? "Everything that lives answers to you." : "Foundation staff and anomalies answer to you.";
        g.drawString(font, sub, x + 10, y + 19, 0xFF9088A8, false);
        int yy = y + 34;
        // ---- aura
        if (omega) {
            section(g, "AURA", x + 10, yy);
            yy += 11;
            String[] names = {"Unmasked", "Sovereign", "Dread"};
            String[] about = {"Everything behaves as it would to anyone", "No anomaly raises a hand to you", "Nothing living turns on you; hostiles back away"};
            int bw = (pw - 20 - 8) / 3, aura = t().getInt("aura");
            for (int i = 0; i < 3; i++) {
                int bx = x + 10 + i * (bw + 4);
                final int n = i;
                button(g, bx, yy, bw, 18, names[i], aura == i, i == 2 ? 0xFF6A40A0 : i == 1 ? 0xFF8050C0 : 0xFF505060, mx, my, () -> send("dom:aura:" + n));
            }
            String a = about[Math.max(0, Math.min(2, aura))];
            g.drawString(font, a, x + pw / 2 - font.width(a) / 2, yy + 22, 0xFFA898C0, false);
            yy += 36;
        }
        // ---- implant
        section(g, "IMPLANT", x + 10, yy);
        yy += 11;
        CompoundTag cd = t().getCompound("cd");
        long now = Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime();
        List<Dominion.Ability> abs = new ArrayList<>();
        for (Dominion.Ability a : Dominion.ABILITIES) if (dom >= a.minLevel()) abs.add(a);
        int cols = 2, bw = (pw - 20 - 4) / cols;
        for (int i = 0; i < abs.size(); i++) {
            Dominion.Ability a = abs.get(i);
            int bx = x + 10 + (i % cols) * (bw + 4), by = yy + (i / cols) * 22;
            long left = cd.getLong(a.id()) - now;
            String label = left > 0 ? a.name() + "  (" + (left + 19) / 20 + "s)" : a.name();
            boolean judge = a.id().equals("judgement");
            button(g, bx, by, bw, 18, label, false, left > 0 ? 0xFF303038 : judge ? 0xFF802830 : 0xFF2C5070, mx, my, left > 0 ? null : () -> { send("dom:ability:" + a.id()); onClose(); });
            if (mx >= bx && mx < bx + bw && my >= by && my < by + 18) hoverText(g, a.about(), mx, my);
        }
        yy += ((abs.size() + cols - 1) / cols) * 22 + 6;
        // ---- orders
        section(g, "ORDERS", x + 10, yy);
        yy += 11;
        String[][] orders = {{"Still everything nearby", "dom:all:calm"}, {"Recall my followers", "dom:all:recall"}, {"Release everything", "dom:all:release"}};
        int ow = (pw - 20 - 8) / 3;
        for (int i = 0; i < 3; i++) {
            final String act = orders[i][1];
            button(g, x + 10 + i * (ow + 4), yy, ow, 18, orders[i][0], false, 0xFF3A3A4C, mx, my, () -> send(act));
        }
        yy += 26;
        String foot = "Hold " + DialogueHud.ANSWER.getTranslatedKeyMessage().getString() + " and look at a creature to command it.";
        g.drawString(font, foot, x + pw / 2 - font.width(foot) / 2, y + ph - 14, 0xFF7A7090, false);
    }

    private void section(GuiGraphics g, String s, int x, int y) {
        g.drawString(font, s, x, y, 0xFF6A6080, false);
    }

    private void button(GuiGraphics g, int x, int y, int w, int h, String label, boolean on, int colour, int mx, int my, Runnable run) {
        boolean hov = run != null && mx >= x && mx < x + w && my >= y && my < y + h;
        int fill = on ? colour | 0xFF000000 : hov ? brighten(colour) : colour & 0x00FFFFFF | 0xB0000000;
        g.fill(x, y, x + w, y + h, fill);
        g.fill(x, y + h - 1, x + w, y + h, on ? 0xFFFFFFFF : 0x40FFFFFF);
        String l = font.width(label) > w - 6 ? font.plainSubstrByWidth(label, w - 10) + "…" : label;
        g.drawString(font, l, x + w / 2 - font.width(l) / 2, y + (h - 8) / 2, run == null ? 0xFF707078 : 0xFFF0ECFF, false);
        if (run != null) hits.add(new Hit(x, y, w, h, run));
    }

    private static int brighten(int c) {
        int r = Math.min(255, ((c >> 16) & 255) + 40), gg = Math.min(255, ((c >> 8) & 255) + 40), b = Math.min(255, (c & 255) + 40);
        return 0xFF000000 | r << 16 | gg << 8 | b;
    }

    private void hoverText(GuiGraphics g, String s, int mx, int my) {
        g.renderTooltip(font, Component.literal(s), mx, my);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) for (Hit h : hits) if (mx >= h.x && mx < h.x + h.w && my >= h.y && my < h.y + h.h) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.2F, 0.3F));
            h.run.run();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (DialogueHud.ANSWER.matches(key, scan)) { CommandStance.suppress = true; onClose(); return true; }
        return super.keyPressed(key, scan, mods);
    }
}
