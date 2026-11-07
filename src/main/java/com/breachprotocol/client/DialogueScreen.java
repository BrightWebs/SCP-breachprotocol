package com.breachprotocol.client;

import com.breachprotocol.network.DialogueActionPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The conversation window. The person you're talking to stands in a framed portrait (they follow
 * your cursor), with their mood beneath them. What's said scrolls as speech bubbles: theirs on the
 * left, yours on the right, narration in the middle. New lines type themselves out with a soft
 * voice; click (or press space) to finish a line. Choices are numbered: click them or press 1-9.
 */
public class DialogueScreen extends Screen {
    private CompoundTag data;
    private int px, py, pw, ph, logX, logY, logW, logH, optY, portraitW;
    private int scroll;
    private EditBox box;
    private String draft = "";

    // the typewriter: lines before {@code shown} are complete; line {@code shown} is {@code chars} in
    private int shown;
    private float chars;
    private long lastFrame;
    private int lastBlip;
    private float opened;

    private record Opt(int x, int y, int w, int h, int index) {}
    private final List<Opt> opts = new ArrayList<>();

    private DialogueScreen(CompoundTag data) {
        super(Component.literal(data.getString("title")));
        this.data = data;
        // a fresh window types out only the newest line
        this.shown = Math.max(0, lines().size() - 1);
    }

    /** A window update from the server. */
    public static void received(CompoundTag tag) {
        Minecraft mc = Minecraft.getInstance();
        if (tag.getBoolean("desktop")) { mc.setScreen(new PhoneScreen(true)); return; }
        if (tag.getBoolean("staffdir")) { PhoneScreen.STAFF = tag; return; }
        if (tag.getBoolean("tree")) {
            if (mc.screen instanceof FamilyTreeScreen f) f.update(tag);
            else mc.setScreen(new FamilyTreeScreen(tag, mc.screen));
            return;
        }
        if (tag.getBoolean("close")) {
            if (mc.screen instanceof com.breachprotocol.client.mca.McaInteractScreen m && m.data().getString("session").equals(tag.getString("session"))) m.closeQuietly();
            if (DialogueHud.hud != null && DialogueHud.hud.getString("session").equals(tag.getString("session"))) DialogueHud.hud = null;
            if (mc.screen instanceof DialogueScreen d && d.data.getString("session").equals(tag.getString("session")))
                d.closeQuietly();
            return;
        }
        if (tag.getBoolean("hud")) {
            // the overlay carries the session; the window only while there's a choice to make
            DialogueHud.hud = tag;
            boolean choice = !tag.getList("options", Tag.TAG_COMPOUND).isEmpty();
            if (mc.screen instanceof DialogueScreen d) {
                if (choice) d.update(tag);
                else d.closeQuietly();
            } else if (choice && tag.getBoolean("ask") && mc.screen == null) {
                mc.setScreen(new DialogueScreen(tag));
            }
            return;
        }
        // talking to someone face to face: the MCA-style interaction screen
        if (tag.getString("session").startsWith("npc:")) {
            if (mc.screen instanceof com.breachprotocol.client.mca.McaInteractScreen m) m.update(tag);
            else if (tag.getBoolean("open") && (mc.screen == null || mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen || mc.screen instanceof DialogueScreen))
                mc.setScreen(new com.breachprotocol.client.mca.McaInteractScreen(tag));
            return;
        }
        if (mc.screen instanceof DialogueScreen d) {
            d.update(tag);
        } else if (tag.getBoolean("open") && (mc.screen == null || mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen)) {
            mc.setScreen(new DialogueScreen(tag));
        }
    }

    private void update(CompoundTag tag) {
        if (box != null) draft = box.getValue();
        boolean same = data.getString("session").equals(tag.getString("session"));
        if (!same) draft = "";
        int before = lines().size();
        String lastBefore = before == 0 ? "" : lines().getCompound(before - 1).getString("text");
        data = tag;
        int after = lines().size();
        // the log keeps the latest 14 lines: work out how many are new
        int fresh = 0;
        if (!same) fresh = 1;
        else {
            for (int i = after - 1; i >= 0; i--) {
                if (lines().getCompound(i).getString("text").equals(lastBefore)) break;
                fresh++;
            }
            if (fresh == after && before > 0) fresh = Math.max(1, after - before);
        }
        if (fresh > 0) { shown = Math.max(0, after - fresh); chars = 0; }
        scroll = 0;
        rebuildWidgets();
    }

    static DialogueScreen open(CompoundTag tag) { return new DialogueScreen(tag); }

    @Override
    public boolean isPauseScreen() { return false; }

    private ListTag options() { return data.getList("options", Tag.TAG_COMPOUND); }

    private ListTag lines() { return data.getList("lines", Tag.TAG_COMPOUND); }

    private boolean typing() { return shown < lines().size(); }

    /** Finishes the line being typed (and any after it). */
    private void skip() { shown = lines().size(); chars = 0; }

    @Override
    protected void init() {
        pw = Math.min(width - 16, 520);
        int nOpt = options().size();
        int rows = (nOpt + 1) / 2;
        boolean input = !data.getString("input").isEmpty();
        int optH = rows * 20 + (input ? 24 : 0) + 10;
        ph = Math.min(height - 12, Math.max(190, 150 + optH));
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        portraitW = data.getInt("entity") >= 0 ? 104 : 0;
        logX = px + 12 + portraitW;
        logY = py + 34;
        logW = pw - 24 - portraitW;
        logH = ph - 34 - optH - 8;
        optY = py + ph - optH + 2;
        box = null;
        if (input) {
            int iy = optY + rows * 20 + 2;
            box = new EditBox(font, px + 12, iy, pw - 96, 18, Component.literal("Say something"));
            box.setMaxLength(200);
            box.setHint(Component.literal("Say something to them... (Enter to send)").withColor(0x707884));
            box.setValue(draft);
            addRenderableWidget(box);
            setInitialFocus(box);
        }
    }

    private void choose(int i) {
        if (i < 0 || i >= options().size()) return;
        if (typing()) { skip(); return; }
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.4F, 0.3F));
        String action = options().getCompound(i).getString("action");
        PacketDistributor.sendToServer(new DialogueActionPayload(action, box == null ? "" : box.getValue()));
    }

    private void sendText() {
        if (box == null || box.getValue().isBlank()) return;
        String t = box.getValue();
        box.setValue("");
        draft = "";
        PacketDistributor.sendToServer(new DialogueActionPayload(data.getString("input"), t));
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (box != null && box.isFocused() && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)) { sendText(); return true; }
        if (box == null || !box.isFocused()) {
            if (key == GLFW.GLFW_KEY_SPACE && typing()) { skip(); return true; }
            if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_9) { choose(key - GLFW.GLFW_KEY_1); return true; }
            if (key >= GLFW.GLFW_KEY_KP_1 && key <= GLFW.GLFW_KEY_KP_9) { choose(key - GLFW.GLFW_KEY_KP_1); return true; }
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (Opt o : opts) if (mx >= o.x && mx < o.x + o.w && my >= o.y && my < o.y + o.h) { choose(o.index); return true; }
        if (typing() && mx >= logX && mx < logX + logW && my >= logY && my < logY + logH) { skip(); return true; }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        scroll = Math.max(0, scroll + (sy > 0 ? 12 : -12));
        return true;
    }

    @Override
    public void onClose() {
        // closing the window during a council session only hides it (the session goes on in the corner)
        if (!data.getBoolean("hud")) PacketDistributor.sendToServer(new DialogueActionPayload("close", ""));
        super.onClose();
    }

    private void closeQuietly() { super.onClose(); }

    // ================================================================== drawing

    private static int alpha(int c, float a) { return ((int) (Mth.clamp(a, 0, 1) * ((c >>> 24) & 255)) << 24) | (c & 0xFFFFFF); }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fillGradient(0, 0, width, height, 0x50000000, 0x98000000);
        float a = opened;
        // the panel: a dark glass card with a thin rim and a header band
        g.fill(px - 2, py - 2, px + pw + 2, py + ph + 2, alpha(0xFF3A4150, a));
        g.fill(px - 1, py - 1, px + pw + 1, py + ph + 1, alpha(0xFF0C0F14, a));
        g.fillGradient(px, py, px + pw, py + ph, alpha(0xF2141820, a), alpha(0xF20B0E12, a));
        g.fillGradient(px, py, px + pw, py + 28, alpha(0xFF222A36, a), alpha(0xFF171C25, a));
        g.fill(px, py + 28, px + pw, py + 29, alpha(0xFF4C5A70, a));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        long now = System.currentTimeMillis();
        float dt = lastFrame == 0 ? 0 : Math.min(0.1F, (now - lastFrame) / 1000F);
        lastFrame = now;
        opened = Math.min(1, opened + dt * 6);
        super.render(g, mouseX, mouseY, partialTick);

        // header
        g.drawString(font, data.getString("title"), px + 12, py + 5, 0xFFFFFF, true);
        String sub = data.getString("subtitle");
        g.drawString(font, font.plainSubstrByWidth(sub, pw - 24), px + 12, py + 16, 0x9AA4B2, false);

        drawPortrait(g, mouseX, mouseY);
        drawLog(g, dt);
        drawOptions(g, mouseX, mouseY);
    }

    private void drawPortrait(GuiGraphics g, int mouseX, int mouseY) {
        int id = data.getInt("entity");
        if (id < 0 || minecraft == null || minecraft.level == null) return;
        int x0 = px + 10, y0 = logY - 2, x1 = x0 + portraitW - 8, y1 = logY + logH + 2;
        g.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, 0xFF3A4150);
        g.fillGradient(x0, y0, x1, y1, 0xFF1E2530, 0xFF0F1318);
        // a soft spotlight
        int cx = (x0 + x1) / 2;
        for (int i = 0; i < 6; i++) g.fill(cx - 30 + i * 4, y1 - 14 - i, cx + 30 - i * 4, y1 - 13 - i, 0x10FFFFFF);
        Entity e = minecraft.level.getEntity(id);
        if (e instanceof LivingEntity le) {
            Component name = le.getCustomName();
            boolean vis = le.isCustomNameVisible();
            le.setCustomName(null);
            le.setCustomNameVisible(false);
            try {
                InventoryScreen.renderEntityInInventoryFollowsMouse(g, x0, y0 + 4, x1, y1 - 22,
                        Math.max(22, Math.min(44, (y1 - y0) / 3)), 0.0625F, mouseX, mouseY, le);
            } finally {
                le.setCustomName(name);
                le.setCustomNameVisible(vis);
            }
        }
        String mood = data.getString("mood");
        if (!mood.isEmpty()) {
            int mc = data.getInt("moodC") == 0 ? 0xC8D0DA : data.getInt("moodC");
            int mw = font.width(mood) + 10;
            int mx = cx - mw / 2, my = y1 - 16;
            g.fill(mx, my, mx + mw, my + 12, 0xC0000000);
            g.fill(mx, my + 11, mx + mw, my + 12, 0xFF000000 | mc);
            g.drawCenteredString(font, mood, cx, my + 2, mc);
        }
    }

    private record Bubble(List<FormattedCharSequence> lines, String who, int colour, int kind, int width) {}

    private void drawLog(GuiGraphics g, float dt) {
        g.fill(logX - 2, logY - 2, logX + logW + 2, logY + logH + 2, 0xFF080A0E);
        ListTag ls = lines();
        // advance the typewriter
        if (typing()) {
            CompoundTag cur = ls.getCompound(shown);
            String text = cur.getString("text");
            boolean you = cur.getString("who").equals("You");
            if (you || cur.getString("who").isEmpty() && text.length() > 90) chars = text.length();
            else chars += dt * 62;
            int c = (int) chars;
            if (!you && c / 3 != lastBlip / 3 && c < text.length() && Character.isLetter(text.charAt(Math.min(c, text.length() - 1)))) {
                lastBlip = c;
                float pitch = 0.85F + (Math.floorMod(cur.getString("who").hashCode(), 7)) * 0.09F + (float) Math.random() * 0.08F;
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_HAT.value(), pitch, 0.08F));
            }
            if (c >= text.length()) { shown++; chars = 0; lastBlip = 0; }
        }
        // lay out every bubble (partial text for the one being typed)
        int maxW = (int) (logW * 0.78F);
        List<Bubble> bs = new ArrayList<>();
        int total = 0;
        for (int i = 0; i < Math.min(ls.size(), shown + 1); i++) {
            CompoundTag l = ls.getCompound(i);
            String who = l.getString("who"), text = l.getString("text");
            if (i == shown) text = text.substring(0, Math.min(text.length(), (int) chars));
            if (text.isEmpty() && i == shown) continue;
            int kind = who.isEmpty() ? 0 : who.equals("You") ? 2 : 1;
            int colour = l.getInt("c") == 0 ? 0xE0C060 : l.getInt("c");
            Component c = kind == 0 ? Component.literal(text).withStyle(s -> s.withItalic(true)) : Component.literal(text);
            List<FormattedCharSequence> wrapped = font.split(c, kind == 0 ? logW - 30 : maxW - 12);
            int w = 0;
            for (FormattedCharSequence f : wrapped) w = Math.max(w, font.width(f));
            if (kind == 1) w = Math.max(w, font.width(who));
            int h = wrapped.size() * 10 + (kind == 1 ? 18 : 8) + 4;
            bs.add(new Bubble(wrapped, who, colour, kind, w));
            total += h;
        }
        int maxScroll = Math.max(0, total - logH + 4);
        scroll = Mth.clamp(scroll, 0, maxScroll);
        g.enableScissor(logX - 1, logY - 1, logX + logW + 1, logY + logH + 1);
        int y = logY + logH - total + scroll - 2;
        if (total < logH) y = logY + 2; // fill from the top until the log is full
        for (Bubble b : bs) {
            int h = b.lines.size() * 10 + (b.kind == 1 ? 18 : 8);
            switch (b.kind) {
                case 2 -> { // you: right, green
                    int bx = logX + logW - b.width - 14;
                    g.fill(bx, y, bx + b.width + 10, y + h, 0xFF1D3B24);
                    g.fill(bx + b.width + 9, y + 2, bx + b.width + 10, y + h - 2, 0xFF5BE36F);
                    int ty = y + 4;
                    for (FormattedCharSequence f : b.lines) { g.drawString(font, f, bx + 5, ty, 0xE8FFE8, false); ty += 10; }
                }
                case 1 -> { // them: left, their colour as an accent
                    int bx = logX + 4;
                    g.fill(bx, y, bx + b.width + 12, y + h, 0xFF1B212B);
                    g.fill(bx, y + 2, bx + 2, y + h - 2, 0xFF000000 | b.colour);
                    g.drawString(font, b.who, bx + 6, y + 4, b.colour, false);
                    int ty = y + 15;
                    for (FormattedCharSequence f : b.lines) { g.drawString(font, f, bx + 6, ty, 0xEDEDED, false); ty += 10; }
                }
                default -> { // narration: centred, quiet
                    int ty = y + 4;
                    for (FormattedCharSequence f : b.lines) { g.drawCenteredString(font, f, logX + logW / 2, ty, 0x98A2B0); ty += 10; }
                }
            }
            y += h + 4;
        }
        g.disableScissor();
        if (scroll < maxScroll) g.drawString(font, "▲", logX + logW - 9, logY + 1, 0x707884, false);
        if (scroll > 0) g.drawString(font, "▼", logX + logW - 9, logY + logH - 9, 0x707884, false);
        if (typing() && System.currentTimeMillis() / 400 % 2 == 0) g.drawString(font, "▸ click to skip", logX + logW - 74, logY + logH - 9, 0x505866, false);
    }

    private void drawOptions(GuiGraphics g, int mouseX, int mouseY) {
        opts.clear();
        ListTag os = options();
        int n = os.size();
        if (n == 0) return;
        boolean wait = typing();
        int bw = (pw - 30) / 2;
        String tip = "";
        for (int i = 0; i < n; i++) {
            CompoundTag o = os.getCompound(i);
            int col = i % 2, row = i / 2;
            boolean alone = n % 2 == 1 && i == n - 1;
            int x = px + 12 + col * (bw + 6), y = optY + row * 20, w = alone ? pw - 24 : bw, h = 18;
            boolean hov = !wait && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
            int c = o.getInt("c") == 0 ? 0xFFFFFF : o.getInt("c");
            float a = wait ? 0.35F : 1F;
            g.fill(x, y, x + w, y + h, alpha(hov ? 0xFF2A3444 : 0xFF181D26, a));
            g.fill(x, y + h - 1, x + w, y + h, alpha(hov ? 0xFF000000 | c : 0xFF2E3644, a));
            if (hov) g.fill(x, y, x + 2, y + h, 0xFF000000 | c);
            if (i < 9) {
                g.fill(x + 4, y + 3, x + 15, y + 15, alpha(0xFF0B0E12, a));
                g.drawCenteredString(font, String.valueOf(i + 1), x + 10, y + 5, alpha(0xFF000000 | 0x9AA4B2, a));
            }
            String label = font.plainSubstrByWidth(o.getString("label"), w - 26);
            g.drawString(font, label, x + 20, y + 5, alpha(0xFF000000 | (hov ? brighten(c) : c), a), false);
            if (!wait) opts.add(new Opt(x, y, w, h, i));
            if (hov) {
                String full = o.getString("label");
                tip = !label.equals(full) ? full + (o.getString("hover").isEmpty() ? "" : "\n" + o.getString("hover")) : o.getString("hover");
            }
        }
        if (box != null) {
            int bx = px + pw - 80, by = box.getY();
            boolean hov = mouseX >= bx && mouseX < bx + 68 && mouseY >= by && mouseY < by + 18;
            g.fill(bx, by, bx + 68, by + 18, hov ? 0xFF2E5A3A : 0xFF1D3B24);
            g.drawCenteredString(font, "Say", bx + 34, by + 5, 0xE8FFE8);
        }
        if (!tip.isEmpty()) {
            List<FormattedCharSequence> tl = new ArrayList<>();
            for (String part : tip.split("\n")) tl.addAll(font.split(Component.literal(part), 220));
            g.renderTooltip(font, tl, mouseX, mouseY);
        }
    }

    private static int brighten(int c) {
        int r = Math.min(255, ((c >> 16) & 255) + 30), gr = Math.min(255, ((c >> 8) & 255) + 30), b = Math.min(255, (c & 255) + 30);
        return r << 16 | gr << 8 | b;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (box != null) {
            int bx = px + pw - 80, by = box.getY();
            if (mx >= bx && mx < bx + 68 && my >= by && my < by + 18) { sendText(); return true; }
        }
        return super.mouseReleased(mx, my, button);
    }
}
