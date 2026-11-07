/*
 * Breach Protocol - talking to a member of staff or an Overseer.
 *
 * Layout and behaviour adapted from Minecraft Comes Alive Reborn's InteractScreen and
 * AbstractDynamicScreen, Copyright (C) Conczin and the MCA Reborn contributors, GPL-3.0.
 * Modifications: Foundation topics, clearance-aware facts, trust as hearts, typewriter speech.
 */
package com.breachprotocol.client.mca;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.network.DialogueActionPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The interaction screen, MCA-style: you keep looking at them in the world. Who they are runs down
 * the left (name, job, mood, personality, traits, and your standing as hearts); topics and actions
 * are buttons down the right; what they say sits in the middle, with your possible answers beneath.
 */
public class McaInteractScreen extends Screen {
    private static final ResourceLocation ICONS = BreachProtocol.id("textures/gui/mca_icons.png");
    private static final float ICON_SCALE = 1.5F;

    private CompoundTag data;
    private int mouseX, mouseY, timeSinceClick;
    private String hoverAnswer;
    private boolean giftMode;
    private String giftAction = "";
    private int shown;
    private float chars;
    private long lastFrame;
    private int lastBlip;
    private net.minecraft.client.gui.components.EditBox box;

    public McaInteractScreen(CompoundTag data) {
        super(Component.literal(data.getString("title")));
        this.data = data;
        this.shown = Math.max(0, lines().size() - 1);
    }

    public CompoundTag data() { return data; }

    public void update(CompoundTag tag) {
        int before = lines().size();
        String last = before == 0 ? "" : lines().getCompound(before - 1).getString("text");
        data = tag;
        int fresh = 0;
        for (int i = lines().size() - 1; i >= 0; i--) {
            if (lines().getCompound(i).getString("text").equals(last)) break;
            fresh++;
        }
        if (fresh > 0) { shown = Math.max(0, lines().size() - fresh); chars = 0; }
        giftMode = false;
        rebuildWidgets();
    }

    private ListTag lines() { return data.getList("lines", Tag.TAG_COMPOUND); }

    private ListTag options() { return data.getList("options", Tag.TAG_COMPOUND); }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) { /* the world stays visible */ }

    @Override
    public void tick() { timeSinceClick++; }

    private void send(String action) {
        if (timeSinceClick <= 2) return;
        timeSinceClick = 0;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.3F, 0.25F));
        if (action.startsWith("gift:")) { giftMode = true; giftAction = "/talk " + action.substring(5) + " gift"; rebuildWidgets(); return; }
        PacketDistributor.sendToServer(new DialogueActionPayload(action, ""));
    }

    @Override
    protected void init() {
        if (giftMode) {
            addRenderableWidget(Button.builder(Component.literal("<"), b -> { giftMode = false; rebuildWidgets(); }).bounds(width - 113, 10, 20, 20).build());
            return;
        }
        if (!data.getString("input").isEmpty()) {
            // something to type (a name for the baby, say): Enter sends it
            box = new net.minecraft.client.gui.components.EditBox(font, width / 2 - 80, height - 40, 160, 18, Component.literal("..."));
            box.setMaxLength(24);
            box.setHint(Component.literal("Type, then press Enter").withColor(0xFF808080));
            addRenderableWidget(box);
            setInitialFocus(box);
        } else box = null;
        int row = 0;
        ListTag os = options();
        for (int i = 0; i < os.size(); i++) {
            CompoundTag o = os.getCompound(i);
            if (o.getBoolean("answer")) continue;
            String label = o.getString("label"), action = o.getString("action");
            int c = o.getInt("c") == 0 ? 0xFFFFFF : o.getInt("c");
            String shownLabel = font.plainSubstrByWidth(label, 74);
            Button b = Button.builder(Component.literal(shownLabel).withColor(c), x -> send(action)).bounds(width - 90, 10 + row * 22, 80, 20).build();
            String tip = (shownLabel.equals(label) ? "" : label + "\n") + o.getString("hover");
            if (!tip.isBlank()) b.setTooltip(Tooltip.create(Component.literal(tip.trim())));
            addRenderableWidget(b);
            row++;
        }
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (giftMode) { giftMode = false; rebuildWidgets(); return true; }
            onClose();
            return true;
        }
        if (box != null && box.isFocused()) {
            if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && !box.getValue().isBlank()) {
                PacketDistributor.sendToServer(new DialogueActionPayload(data.getString("input"), box.getValue().trim().replace(' ', '_')));
                box.setValue("");
                return true;
            }
            return box.keyPressed(key, scan, mods) || box.canConsumeInput() || super.keyPressed(key, scan, mods);
        }
        if (key == GLFW.GLFW_KEY_SPACE && shown < lines().size()) { shown = lines().size(); return true; }
        if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_9) {
            List<CompoundTag> a = answers();
            int i = key - GLFW.GLFW_KEY_1;
            if (i < a.size()) { send(a.get(i).getString("action")); return true; }
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (giftMode && button == 1) { PacketDistributor.sendToServer(new DialogueActionPayload(giftAction, "")); giftMode = false; rebuildWidgets(); return true; }
        if (button == 0 && hoverAnswer != null) {
            if (shown < lines().size()) { shown = lines().size(); return true; }
            send(hoverAnswer);
            return true;
        }
        if (button == 0 && shown < lines().size() && Math.abs(x - width / 2.0) < 100 && y < height / 2.0) { shown = lines().size(); return true; }
        return super.mouseClicked(x, y, button);
    }

    @Override
    public void onClose() {
        PacketDistributor.sendToServer(new DialogueActionPayload("close", ""));
        super.onClose();
    }

    public void closeQuietly() { super.onClose(); }

    private List<CompoundTag> answers() {
        List<CompoundTag> out = new ArrayList<>();
        for (int i = 0; i < options().size(); i++) if (options().getCompound(i).getBoolean("answer")) out.add(options().getCompound(i));
        return out;
    }

    // ================================================================== drawing

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        mouseX = mx;
        mouseY = my;
        long now = System.currentTimeMillis();
        float dt = lastFrame == 0 ? 0 : Math.min(0.1F, (now - lastFrame) / 1000F);
        lastFrame = now;
        super.render(g, mx, my, pt);
        drawFacts(g);
        drawHearts(g);
        drawSpeech(g, dt);
    }

    private void drawFacts(GuiGraphics g) {
        int h = 17, y = 28;
        g.renderTooltip(font, giftMode ? Component.literal("Give a gift: hold it and right-click").withColor(0xF0A8C8) : Component.literal(data.getString("title")), 10, y);
        y += 2;
        ListTag info = data.getList("info", Tag.TAG_STRING);
        for (int i = 0; i < info.size(); i++) {
            y += h;
            String line = info.getString(i);
            int c = line.startsWith("Traits") ? 0xC8A8F0 : line.startsWith("Personality") ? 0xFFFFFF : 0xC8D0DA;
            g.renderTooltip(font, Component.literal(line).withColor(c), 10, y);
        }
        String mood = data.getString("mood");
        if (!mood.isEmpty()) {
            y += h;
            g.renderTooltip(font, Component.literal("Mood: " + mood).withColor(data.getInt("moodC") == 0 ? 0xFFFFFF : data.getInt("moodC")), 10, y);
        }
    }

    private void drawHearts(GuiGraphics g) {
        int hearts = data.getInt("hearts");
        if (hearts < 0) return;
        int ix = 20, iy = 30 + 17 * (data.getList("info", Tag.TAG_STRING).size() + 2) + 6;
        g.pose().pushPose();
        g.pose().scale(ICON_SCALE, ICON_SCALE, ICON_SCALE);
        int u = hearts >= 10 ? 112 : hearts <= 0 ? 96 : 80;
        g.blit(ICONS, (int) (ix / ICON_SCALE), (int) (iy / ICON_SCALE), u, 0, 16, 16);
        g.pose().popPose();
        g.drawString(font, hearts * 10 + " hearts", ix + 28, iy + 8, 0xFFFFFF, true);
        if (mouseX > ix && mouseX < ix + 24 && mouseY > iy && mouseY < iy + 24)
            g.renderTooltip(font, Component.literal(hearts >= 10 ? "They'd trust you with anything." : hearts >= 6 ? "They trust you." : hearts >= 3 ? "Friendly." : "They barely know you."), mouseX, mouseY);
    }

    private void drawSpeech(GuiGraphics g, float dt) {
        // what they've said since you last spoke
        ListTag ls = lines();
        int from = ls.size();
        while (from > 0 && !ls.getCompound(from - 1).getString("who").equals("You")) from--;
        if (from == ls.size() && ls.size() > 0) from = ls.size() - 1;
        if (shown < from) shown = from;
        if (shown < ls.size()) {
            String text = ls.getCompound(shown).getString("text");
            chars += dt * 62;
            int c = (int) chars;
            if (c / 3 != lastBlip / 3 && c < text.length() && Character.isLetter(text.charAt(c))) {
                lastBlip = c;
                float pitch = 0.85F + Math.floorMod(ls.getCompound(shown).getString("who").hashCode(), 7) * 0.09F;
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_HAT.value(), pitch, 0.07F));
            }
            if (c >= text.length()) { shown++; chars = 0; lastBlip = 0; }
        }
        List<FormattedCharSequence> q = new ArrayList<>();
        for (int i = from; i < Math.min(ls.size(), shown + 1); i++) {
            CompoundTag l = ls.getCompound(i);
            String text = l.getString("text");
            if (i == shown) text = text.substring(0, Math.min(text.length(), (int) chars));
            if (text.isEmpty()) continue;
            Component line = l.getString("who").isEmpty() ? Component.literal(text).withStyle(s -> s.withItalic(true)).withColor(0xB0B8C4) : Component.literal(text);
            q.addAll(font.split(line, 180));
        }
        List<CompoundTag> answers = answers();
        boolean typing = shown < ls.size();
        int top = height / 2 - 50 - 10 * q.size(), bottom = height / 2 - 30 + 10 * Math.max(1, answers.size());
        if (q.isEmpty() && answers.isEmpty()) return;
        g.fill(width / 2 - 100, top, width / 2 + 100, bottom, 0x88000000);
        String who = ls.isEmpty() ? "" : ls.getCompound(Math.max(0, Math.min(ls.size() - 1, from))).getString("who");
        if (!who.isEmpty() && !who.equals("You")) g.drawCenteredString(font, who, width / 2, top - 11, 0xFFE0C060);
        int i = -q.size();
        for (FormattedCharSequence t : q) {
            i++;
            g.drawString(font, t, width / 2 - font.width(t) / 2, height / 2 - 50 + i * 10, 0xFFFFFFFF);
        }
        g.hLine(width / 2 - 85, width / 2 + 85, height / 2 - 40, 0xAAFFFFFF);
        hoverAnswer = null;
        int y = height / 2 - 35;
        String tip = "";
        for (int k = 0; k < answers.size(); k++) {
            CompoundTag a = answers.get(k);
            String label = (k + 1) + ". " + a.getString("label");
            String shownLabel = font.plainSubstrByWidth(label, 196);
            boolean hover = !typing && mouseX > width / 2 - 100 && mouseX < width / 2 + 100 && mouseY > y - 2 && mouseY < y + 9;
            g.drawCenteredString(font, shownLabel, width / 2, y, typing ? 0x55FFFFFF : hover ? 0xFFD7D784 : 0xCCFFFFFF);
            if (hover) {
                hoverAnswer = a.getString("action");
                tip = (shownLabel.equals(label) ? "" : a.getString("label") + "\n") + a.getString("hover");
            }
            y += 10;
        }
        if (!tip.isBlank()) {
            List<FormattedCharSequence> tl = new ArrayList<>();
            for (String part : tip.trim().split("\n")) tl.addAll(font.split(Component.literal(part), 200));
            g.renderTooltip(font, tl, mouseX, mouseY);
        }
    }
}
