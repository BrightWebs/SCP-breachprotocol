package com.breachprotocol.client;

import com.breachprotocol.network.DialogueActionPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The conversation window: the person you're talking to on the left (they turn to follow your
 * cursor), what has been said on the right, and the choices as buttons along the bottom.
 */
public class DialogueScreen extends Screen {
    private CompoundTag data;
    private int px, py, pw, ph, logX, logY, logW, logH;
    private int scroll; // lines scrolled up from the bottom
    private EditBox box;
    private String draft = "";

    private DialogueScreen(CompoundTag data) {
        super(Component.literal(data.getString("title")));
        this.data = data;
    }

    /** A window update from the server. */
    public static void received(CompoundTag tag) {
        Minecraft mc = Minecraft.getInstance();
        if (tag.getBoolean("desktop")) { mc.setScreen(new PhoneScreen(true)); return; }
        if (tag.getBoolean("close")) {
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
        if (mc.screen instanceof DialogueScreen d) {
            d.update(tag);
        } else if (tag.getBoolean("open") && (mc.screen == null || mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen)) {
            mc.setScreen(new DialogueScreen(tag));
        }
    }

    private void update(CompoundTag tag) {
        if (box != null) draft = box.getValue();
        boolean sameSession = data.getString("session").equals(tag.getString("session"));
        if (!sameSession) draft = "";
        data = tag;
        scroll = 0;
        rebuildWidgets();
    }

    static DialogueScreen open(CompoundTag tag) { return new DialogueScreen(tag); }

    @Override
    public boolean isPauseScreen() { return false; }

    private ListTag options() { return data.getList("options", Tag.TAG_COMPOUND); }

    @Override
    protected void init() {
        pw = Math.min(width - 16, 460);
        int nOpt = options().size();
        int rows = (nOpt + 1) / 2;
        boolean input = !data.getString("input").isEmpty();
        int optH = rows * 22 + (input ? 24 : 0) + 8;
        ph = Math.min(height - 16, Math.max(170, 120 + optH));
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        int portrait = data.getInt("entity") >= 0 ? 96 : 0;
        logX = px + 10 + portrait;
        logY = py + 30;
        logW = pw - 20 - portrait;
        logH = ph - 30 - optH - 8;

        int bw = (pw - 26) / 2;
        int by = py + ph - optH;
        for (int i = 0; i < nOpt; i++) {
            CompoundTag o = options().getCompound(i);
            int col = i % 2, row = i / 2;
            boolean lastAlone = nOpt % 2 == 1 && i == nOpt - 1;
            int x = px + 10 + col * (bw + 6);
            int w = lastAlone ? pw - 20 : bw;
            String action = o.getString("action");
            Button b = Button.builder(Component.literal(o.getString("label")).withColor(o.contains("c") && o.getInt("c") != 0 ? o.getInt("c") : 0xFFFFFF),
                            btn -> choose(action))
                    .bounds(x, by + row * 22, w, 20).build();
            String hover = o.getString("hover");
            if (!hover.isEmpty()) b.setTooltip(Tooltip.create(Component.literal(hover)));
            addRenderableWidget(b);
        }
        box = null;
        if (input) {
            int iy = by + rows * 22;
            box = new EditBox(font, px + 10, iy, pw - 90, 20, Component.literal("Say something"));
            box.setMaxLength(200);
            box.setHint(Component.literal("Say something to them…").withColor(0x808080));
            box.setValue(draft);
            addRenderableWidget(box);
            addRenderableWidget(Button.builder(Component.literal("Say"), b -> sendText()).bounds(px + pw - 76, iy, 66, 20).build());
            setInitialFocus(box);
        }
    }

    private void choose(String action) {
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
        if (box != null && box.isFocused() && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)) {
            sendText();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        scroll = Math.max(0, scroll + (sy > 0 ? 2 : -2));
        return true;
    }

    @Override
    public void onClose() {
        // closing the window during a council session only hides it (the session goes on in the corner)
        if (!data.getBoolean("hud")) PacketDistributor.sendToServer(new DialogueActionPayload("close", ""));
        super.onClose();
    }

    private void closeQuietly() { super.onClose(); }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick); // background, panel and buttons
        g.drawString(font, data.getString("title"), px + 10, py + 4, 0xFFFFFF, false);
        g.drawString(font, data.getString("subtitle"), px + 10, py + 14, 0x9AA4B2, false);

        // portrait
        int id = data.getInt("entity");
        if (id >= 0 && minecraft != null && minecraft.level != null) {
            Entity e = minecraft.level.getEntity(id);
            g.fill(px + 8, py + 30, px + 98, logY + logH, 0xFF181B21);
            if (e instanceof LivingEntity le) {
                InventoryScreen.renderEntityInInventoryFollowsMouse(g, px + 8, py + 30, px + 98, logY + logH,
                        Math.max(24, Math.min(46, logH / 3)), 0.0625F, mouseX, mouseY, le);
            }
        }

        // transcript, newest at the bottom
        g.fill(logX - 2, logY - 2, logX + logW + 2, logY + logH + 2, 0xFF0B0D10);
        List<FormattedCharSequence> all = new ArrayList<>();
        List<Integer> gaps = new ArrayList<>();
        ListTag lines = data.getList("lines", Tag.TAG_COMPOUND);
        for (int i = 0; i < lines.size(); i++) {
            CompoundTag l = lines.getCompound(i);
            String who = l.getString("who");
            Component c = who.isEmpty()
                    ? Component.literal(l.getString("text")).withColor(0xA8B0BC).withStyle(s -> s.withItalic(true))
                    : Component.literal(who + ": ").withColor(l.getInt("c") == 0 ? 0xE0C060 : l.getInt("c"))
                    .append(Component.literal(l.getString("text")).withColor(0xEDEDED));
            all.addAll(font.split(c, logW - 4));
            gaps.add(all.size());
        }
        int fit = Math.max(1, logH / 10);
        int maxScroll = Math.max(0, all.size() - fit);
        scroll = Math.min(scroll, maxScroll);
        int end = all.size() - scroll, start = Math.max(0, end - fit);
        int y = logY + logH - (end - start) * 10;
        for (int i = start; i < end; i++) {
            g.drawString(font, all.get(i), logX, y, 0xFFFFFF, false);
            y += 10;
        }
        if (start > 0) g.drawString(font, "▲", logX + logW - 8, logY, 0x707070, false);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fillGradient(0, 0, width, height, 0x60000000, 0x90000000);
        g.fill(px - 1, py - 1, px + pw + 1, py + ph + 1, 0xFF6B6F78);
        g.fill(px, py, px + pw, py + ph, 0xF0101216);
        g.fill(px, py, px + pw, py + 24, 0xFF1C2028);
    }
}
