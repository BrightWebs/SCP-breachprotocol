package com.breachprotocol.client;

import com.breachprotocol.network.ClientCareerState;
import com.breachprotocol.network.PhoneActionPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.*;

/**
 * Shapeshift's menu (crouch and use the skill in its Shapeshift mode): every creature and every block
 * there is, searchable. Pick one and you're it.
 */
public class ShapeshiftScreen extends Screen {
    private record Hit(int x, int y, int w, int h, Runnable run) {}
    private record Mob(EntityType<?> type, String id, String name) {}
    private record Blk(Block block, String id, String name, ItemStack icon) {}

    private static final int BG = 0xF20A0710, PANEL = 0xFF140E1C, LINE = 0x40FFFFFF, TEXT = 0xFFEAE4F6, MUTED = 0xFF8A82A0, DIM = 0xFF5A5470, AC = 0xFFC890FF;

    private final List<Hit> hits = new ArrayList<>();
    private static int tab = 0;
    /** Your forms (the ones you've been before), or everything there is. */
    private static boolean all = false;
    private String query = "";
    private int scroll = 0;
    private Mob hover;
    private static List<Mob> mobs;
    private static List<Blk> blocks;
    private static final Map<EntityType<?>, LivingEntity> MODELS = new HashMap<>();

    public ShapeshiftScreen() { super(Component.literal("Shapeshift")); }

    /** The Shapeshift key (J by default): straight to the menu. */
    public static final net.minecraft.client.KeyMapping KEY = new net.minecraft.client.KeyMapping("key.breachprotocol.shapeshift", com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, "key.categories.breachprotocol");

    static void key() {
        Minecraft mc = Minecraft.getInstance();
        while (KEY.consumeClick()) {
            if (mc.screen != null || mc.player == null) continue;
            if (!ClientCareerState.tag.getBoolean("shapeHas")) {
                mc.player.displayClientMessage(Component.literal("You don't have Shapeshift (the Administrator's, with Manas Reborn).").withStyle(net.minecraft.ChatFormatting.GRAY), true);
                continue;
            }
            mc.setScreen(new ShapeshiftScreen());
        }
    }

    private static List<String> known(String key) {
        List<String> out = new ArrayList<>();
        for (net.minecraft.nbt.Tag t : ClientCareerState.tag.getList(key, net.minecraft.nbt.Tag.TAG_STRING)) out.add(t.getAsString());
        return out;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ---------------------------------------------------------------- opened by the server

    private static long lastOpen = Long.MIN_VALUE;

    static void watch() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !ClientCareerState.tag.contains("shapeOpen")) return;
        long at = ClientCareerState.tag.getLong("shapeOpen");
        if (at == lastOpen) return;
        lastOpen = at;
        if (Math.abs(mc.level.getGameTime() - at) > 60) return;
        if (mc.screen == null) mc.setScreen(new ShapeshiftScreen());
    }

    // ---------------------------------------------------------------- what can be chosen

    private static void gather() {
        if (mobs != null) return;
        Minecraft mc = Minecraft.getInstance();
        mobs = new ArrayList<>();
        for (EntityType<?> t : BuiltInRegistries.ENTITY_TYPE) {
            if (t == EntityType.PLAYER) continue;
            Entity e = null;
            try { e = t.create(mc.level); } catch (Throwable ignored) {}
            if (!(e instanceof LivingEntity le) || e instanceof Player) continue;
            MODELS.put(t, le);
            mobs.add(new Mob(t, BuiltInRegistries.ENTITY_TYPE.getKey(t).toString(), t.getDescription().getString()));
        }
        mobs.sort(Comparator.comparing(Mob::name, String.CASE_INSENSITIVE_ORDER));
        blocks = new ArrayList<>();
        for (Block b : BuiltInRegistries.BLOCK) {
            ItemStack icon = new ItemStack(b.asItem());
            if (icon.isEmpty() || icon.is(Items.AIR)) continue;
            blocks.add(new Blk(b, BuiltInRegistries.BLOCK.getKey(b).toString(), b.getName().getString(), icon));
        }
        blocks.sort(Comparator.comparing(Blk::name, String.CASE_INSENSITIVE_ORDER));
    }

    private boolean matches(String name, String id) {
        if (query.isEmpty()) return true;
        String q = query.toLowerCase(Locale.ROOT);
        return name.toLowerCase(Locale.ROOT).contains(q) || id.contains(q);
    }

    private static void send(String a) { PacketDistributor.sendToServer(new PhoneActionPayload(a, 0)); }

    // ---------------------------------------------------------------- drawing

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        gather();
        hits.clear();
        int pw = Math.min(440, width - 16), ph = Math.min(290, height - 16), x = (width - pw) / 2, y = (height - ph) / 2;
        g.fill(x, y, x + pw, y + ph, BG);
        frame(g, x, y, pw, ph, AC);
        g.fill(x + 1, y + 1, x + pw - 1, y + 26, PANEL);
        g.fill(x + 1, y + 26, x + pw - 1, y + 27, AC & 0x60FFFFFF);
        g.drawString(font, "SHAPESHIFT", x + 10, y + 6, AC, false);
        String form = ClientCareerState.tag.getString("shapeForm");
        PowerKeys.small(g, font, form.isEmpty() ? "YOU ARE YOURSELF  ·  CHOOSE A FORM" : "YOU ARE " + form.toUpperCase(Locale.ROOT), x + 10, y + 17, form.isEmpty() ? MUTED : AC);
        String[] tabs = {"Polymorph", "Mimic"};
        int tx = x + pw - 10;
        for (int i = tabs.length - 1; i >= 0; i--) {
            int w = font.width(tabs[i]) + 14;
            tx -= w;
            boolean on = tab == i, hov = in(mx, my, tx, y + 6, w, 15);
            g.fill(tx, y + 6, tx + w, y + 21, on ? 0x50402060 : hov ? 0x30FFFFFF : 0);
            if (on) g.fill(tx, y + 20, tx + w, y + 21, AC);
            g.drawString(font, tabs[i], tx + 7, y + 10, on ? TEXT : MUTED, false);
            final int n = i;
            hits.add(new Hit(tx, y + 6, w, 15, () -> { tab = n; scroll = 0; }));
            tx -= 4;
        }
        // your forms / everything, search, and the way back
        int sy = y + 32;
        String[] views = {"Your forms", "Everything"};
        int vx = x + 10;
        for (int i = 0; i < 2; i++) {
            int w = font.width(views[i]) + 12;
            boolean on = all == (i == 1), hov = in(mx, my, vx, sy, w, 14);
            g.fill(vx, sy, vx + w, sy + 14, on ? 0x50402060 : hov ? 0x30FFFFFF : PANEL);
            frame(g, vx, sy, w, 14, on ? AC : LINE);
            g.drawString(font, views[i], vx + 6, sy + 3, on ? TEXT : MUTED, false);
            final boolean v = i == 1;
            hits.add(new Hit(vx, sy, w, 14, () -> { all = v; scroll = 0; }));
            vx += w + 3;
        }
        int sw = x + pw - 10 - vx - 3;
        if (!form.isEmpty()) {
            String back = "Change back";
            int bw = font.width(back) + 12, bx = x + pw - 10 - bw;
            boolean hov = in(mx, my, bx, sy, bw, 14);
            g.fill(bx, sy, bx + bw, sy + 14, hov ? 0x60C04040 : 0x30C04040);
            frame(g, bx, sy, bw, 14, 0xFFE07070);
            g.drawString(font, back, bx + 6, sy + 3, TEXT, false);
            hits.add(new Hit(bx, sy, bw, 14, () -> { send("shape:revert"); onClose(); }));
            sw -= bw + 3;
        }
        g.fill(vx, sy, vx + sw, sy + 14, PANEL);
        frame(g, vx, sy, sw, 14, LINE);
        g.drawString(font, query.isEmpty() ? "Search..." : query + ((System.currentTimeMillis() / 500) % 2 == 0 ? "_" : ""), vx + 4, sy + 3, query.isEmpty() ? DIM : TEXT, false);
        int cy = sy + 20, ch = y + ph - cy - 8;
        if (tab == 0) creatures(g, x + 10, cy, pw - 20, ch, mx, my);
        else blockGrid(g, x + 10, cy, pw - 20, ch, mx, my);
    }

    private void creatures(GuiGraphics g, int x, int y, int w, int h, int mx, int my) {
        List<Mob> list = new ArrayList<>();
        if (all) { for (Mob m : mobs) if (matches(m.name(), m.id())) list.add(m); }
        else {
            Map<String, Mob> byId = new HashMap<>();
            for (Mob m : mobs) byId.put(m.id(), m);
            for (String id : known("shapeMobs")) { Mob m = byId.get(id); if (m != null && matches(m.name(), m.id())) list.add(m); }
        }
        int lw = w - 150, rh = 14, rows = (h - 4) / rh;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, list.size() - rows)));
        g.fill(x, y, x + lw, y + h, PANEL);
        frame(g, x, y, lw, h, LINE);
        hover = null;
        for (int r = 0; r < rows && scroll + r < list.size(); r++) {
            Mob m = list.get(scroll + r);
            int ry = y + 2 + r * rh;
            boolean hov = in(mx, my, x + 2, ry, lw - 4, rh);
            if (hov) { g.fill(x + 2, ry, x + lw - 2, ry + rh, 0x40402868); hover = m; }
            g.drawString(font, m.name(), x + 6, ry + 3, hov ? TEXT : MUTED, false);
            String ns = m.id().substring(0, m.id().indexOf(':'));
            if (!ns.equals("minecraft")) PowerKeys.small(g, font, ns.toUpperCase(Locale.ROOT), x + lw - 8 - (int) (font.width(ns) * 0.75F), ry + 4, DIM);
            hits.add(new Hit(x + 2, ry, lw - 4, rh, () -> { send("shape:mob:" + m.id()); onClose(); }));
        }
        if (list.isEmpty()) {
            if (!all && query.isEmpty()) {
                g.drawString(font, "No creature forms yet.", x + 6, y + 6, MUTED, false);
                PowerKeys.small(g, font, "USE MIMIC MODE ON ONE, OR PICK FROM EVERYTHING", x + 6, y + 18, DIM);
            } else g.drawString(font, "Nothing by that name.", x + 6, y + 6, DIM, false);
        }
        // the preview
        int px = x + lw + 6, pw = w - lw - 6;
        g.fill(px, y, px + pw, y + h, 0xFF0C0914);
        frame(g, px, y, pw, h, 0x50C890FF);
        Mob show = hover != null ? hover : list.isEmpty() ? null : list.get(Math.min(scroll, list.size() - 1));
        if (show != null) {
            LivingEntity e = MODELS.get(show.type());
            if (e != null) {
                float bh = Math.max(0.3F, e.getBbHeight()), bw = Math.max(0.3F, e.getBbWidth());
                int scale = (int) Math.max(4, Math.min(60, (h - 40) * 0.8F / Math.max(bh, bw * 0.9F)));
                try { InventoryScreen.renderEntityInInventoryFollowsMouse(g, px + 2, y + 2, px + pw - 2, y + h - 24, scale, 0.0625F, mx, my, e); } catch (Throwable ignored) {}
            }
            String n = show.name();
            g.drawString(font, font.plainSubstrByWidth(n, pw - 8), px + 4, y + h - 20, TEXT, false);
            PowerKeys.small(g, font, "CLICK TO BECOME IT", px + 4, y + h - 9, AC);
        }
    }

    private void blockGrid(GuiGraphics g, int x, int y, int w, int h, int mx, int my) {
        List<Blk> list = new ArrayList<>();
        if (all) { for (Blk b : blocks) if (matches(b.name(), b.id())) list.add(b); }
        else {
            Map<String, Blk> byId = new HashMap<>();
            for (Blk b : blocks) byId.put(b.id(), b);
            for (String id : known("shapeBlocks")) { Blk b = byId.get(id); if (b != null && matches(b.name(), b.id())) list.add(b); }
        }
        int cols = Math.max(1, (w - 4) / 20), rows = (h - 4) / 20;
        int maxRow = Math.max(0, (list.size() + cols - 1) / cols - rows);
        scroll = Math.max(0, Math.min(scroll, maxRow));
        g.fill(x, y, x + w, y + h, PANEL);
        frame(g, x, y, w, h, LINE);
        Blk tip = null;
        for (int r = 0; r < rows; r++) for (int c = 0; c < cols; c++) {
            int i = (scroll + r) * cols + c;
            if (i >= list.size()) break;
            Blk b = list.get(i);
            int sx = x + 3 + c * 20, syy = y + 3 + r * 20;
            boolean hov = in(mx, my, sx, syy, 18, 18);
            g.fill(sx, syy, sx + 18, syy + 18, hov ? 0x60402868 : 0xFF0C0914);
            g.renderItem(b.icon(), sx + 1, syy + 1);
            if (hov) tip = b;
            hits.add(new Hit(sx, syy, 18, 18, () -> { send("shape:block:" + b.id()); onClose(); }));
        }
        if (list.isEmpty()) {
            if (!all && query.isEmpty()) {
                g.drawString(font, "No object forms yet.", x + 6, y + 6, MUTED, false);
                PowerKeys.small(g, font, "USE MIMIC MODE ON ONE, OR PICK FROM EVERYTHING", x + 6, y + 18, DIM);
            } else g.drawString(font, "Nothing by that name.", x + 6, y + 6, DIM, false);
        }
        if (tip != null) g.renderTooltip(font, Component.literal(tip.name()), mx, my);
    }

    // ---------------------------------------------------------------- bits

    private void frame(GuiGraphics g, int x, int y, int w, int h, int c) {
        g.fill(x, y, x + w, y + 1, c); g.fill(x, y + h - 1, x + w, y + h, c);
        g.fill(x, y, x + 1, y + h, c); g.fill(x + w - 1, y, x + w, y + h, c);
    }

    private static boolean in(int mx, int my, int x, int y, int w, int h) { return mx >= x && mx < x + w && my >= y && my < y + h; }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (mx >= h.x && mx < h.x + h.w && my >= h.y && my < h.y + h.h) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.4F, 0.25F));
                h.run.run();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        scroll = Math.max(0, scroll - (int) Math.signum(sy) * (Screen.hasShiftDown() ? 8 : 1));
        return true;
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (query.length() < 32 && (Character.isLetterOrDigit(c) || c == ' ' || c == '_' || c == '-' || c == ':')) { query += c; scroll = 0; return true; }
        return super.charTyped(c, mods);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_BACKSPACE) { if (!query.isEmpty()) query = query.substring(0, query.length() - 1); scroll = 0; return true; }
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        if (key == GLFW.GLFW_KEY_TAB) { tab = 1 - tab; scroll = 0; return true; }
        return true; // letters are for the search
    }
}
