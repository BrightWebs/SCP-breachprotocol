package com.breachprotocol.client;

import com.breachprotocol.network.ClientCareerState;
import com.breachprotocol.network.PhoneActionPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;

/**
 * The Vault, from the power slot: every cell and what's in it (turning in 3D, following your cursor),
 * with pull out / put back; and the storeroom board, all twenty double chests on one scrolling wall.
 */
public class VaultScreen extends Screen {
    private record Hit(int x, int y, int w, int h, Runnable run) {}

    private final List<Hit> hits = new ArrayList<>();
    private static int tab = 0, selected = 0;
    private int listScroll = 0, boardScroll = 0, ticks = 0;
    private int boardX, boardY, boardW, boardH, listX, listY, listW, listH;

    private static final int BG = 0xF20A0710, PANEL = 0xFF140E1C, LINE = 0x40FFFFFF, TEXT = 0xFFEAE4F6, MUTED = 0xFF8A82A0, DIM = 0xFF5A5470,
            AC = 0xFFB890FF, OK = 0xFF7FE0A0, BAD = 0xFFE07070;

    public VaultScreen() { super(Component.literal("The Vault")); }

    /** Opened from one of the storeroom's chests: the board, at that chest. */
    private static int jump = -1;

    public static VaultScreen atChest(int k) { tab = 1; jump = k; return new VaultScreen(); }

    /** The server says a storeroom chest was opened: open the board there. */
    private static long lastOpen = Long.MIN_VALUE;

    static void watch() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        CompoundTag v = vault();
        if (!v.contains("openAt")) return;
        long at = v.getLong("openAt");
        if (at == lastOpen) return;
        lastOpen = at;
        if (Math.abs(mc.level.getGameTime() - at) > 60) return; // an old one, from before you logged in
        if (mc.screen == null || mc.screen instanceof VaultScreen) mc.setScreen(atChest(v.getInt("openK")));
    }

    @Override
    protected void init() { send("vault:view"); }

    @Override
    public void tick() { if (++ticks % 40 == 0) send("vault:view"); }

    @Override
    public boolean isPauseScreen() { return false; }

    private static CompoundTag vault() { return ClientCareerState.tag.getCompound("vault"); }

    private static void send(String a) { PacketDistributor.sendToServer(new PhoneActionPayload(a, 0)); }

    // ---------------------------------------------------------------- what the server sent, unpacked once per update

    private CompoundTag parsedFrom;
    private final List<List<Map.Entry<Integer, ItemStack>>> chests = new ArrayList<>();
    private static final Map<String, LivingEntity> MODELS = new HashMap<>();

    private void parse() {
        CompoundTag v = vault();
        if (v == parsedFrom) return;
        parsedFrom = v;
        chests.clear();
        var reg = Minecraft.getInstance().level.registryAccess();
        for (Tag t : v.getList("chests", Tag.TAG_LIST)) {
            List<Map.Entry<Integer, ItemStack>> items = new ArrayList<>();
            for (Tag it : (ListTag) t) {
                CompoundTag c = (CompoundTag) it;
                ItemStack s = ItemStack.parseOptional(reg, c.getCompound("i"));
                if (s.isEmpty()) continue;
                s.setCount(Math.max(1, c.getInt("n")));
                items.add(Map.entry(c.getInt("s"), s));
            }
            chests.add(items);
        }
    }

    private static LivingEntity model(CompoundTag look) {
        if (look.isEmpty()) return null;
        String key = look.getString("id") + look.hashCode();
        if (MODELS.containsKey(key)) return MODELS.get(key);
        LivingEntity le = null;
        try {
            CompoundTag t = look.copy();
            t.remove("Passengers");
            t.remove("UUID");
            Optional<Entity> e = EntityType.create(t, Minecraft.getInstance().level);
            if (e.isPresent() && e.get() instanceof LivingEntity l) le = l;
        } catch (Throwable ignored) {}
        if (MODELS.size() > 64) MODELS.clear();
        MODELS.put(key, le);
        return le;
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        hits.clear();
        parse();
        CompoundTag v = vault();
        int pw = Math.min(470, width - 16), ph = Math.min(300, height - 16), x = (width - pw) / 2, y = (height - ph) / 2;
        g.fill(x, y, x + pw, y + ph, BG);
        frame(g, x, y, pw, ph, AC);
        g.fill(x + 1, y + 1, x + pw - 1, y + 26, PANEL);
        g.fill(x + 1, y + 26, x + pw - 1, y + 27, AC & 0x60FFFFFF);
        g.drawString(font, "THE VAULT", x + 10, y + 6, AC, false);
        boolean inside = v.getBoolean("inside");
        small(g, inside ? "YOU ARE INSIDE  ·  NOTHING HERE BREAKS BUT BY YOUR HAND" : "YOUR OWN DIMENSION  ·  NOTHING HERE BREAKS BUT BY YOUR HAND", x + 10, y + 17, MUTED);
        String[] tabs = {"Cells", "Storeroom"};
        int tx = x + pw - 10;
        for (int i = tabs.length - 1; i >= 0; i--) {
            int w = font.width(tabs[i]) + 14;
            tx -= w;
            boolean on = tab == i, hov = in(mx, my, tx, y + 6, w, 15);
            g.fill(tx, y + 6, tx + w, y + 21, on ? 0x50402060 : hov ? 0x30FFFFFF : 0);
            if (on) g.fill(tx, y + 20, tx + w, y + 21, AC);
            g.drawString(font, tabs[i], tx + 7, y + 10, on ? TEXT : MUTED, false);
            final int n = i;
            hits.add(new Hit(tx, y + 6, w, 15, () -> tab = n));
            tx -= 4;
        }
        if (!v.contains("cells")) {
            centred(g, "Opening the Vault…", x + pw / 2, y + ph / 2 - 4, MUTED);
            return;
        }
        // footer: the things you do with the Vault itself
        int fy = y + ph - 24;
        g.fill(x + 1, fy - 3, x + pw - 1, fy - 2, LINE);
        int bx = x + 10;
        bx = button(g, bx, fy, inside ? "Leave the Vault" : "Enter the Vault", AC, mx, my, () -> { send("vault:go"); onClose(); }) + 4;
        bx = button(g, bx, fy, v.getBoolean("seated") ? "On your throne" : "Take the throne", 0xFFE0B040, mx, my, () -> { send("vault:throne"); onClose(); }) + 4;
        bx = button(g, bx, fy, "Rebuild", 0xFF80C8FF, mx, my, () -> send("vault:rebuild")) + 4;
        boolean trapMode = PowerKeys.vaultTrap();
        bx = button(g, bx, fy, trapMode ? "Key: Trap" : "Key: Open", trapMode ? BAD : MUTED, mx, my, () -> PowerKeys.setVaultTrap(!trapMode)) + 4;
        String note = trapMode ? "A TAP OF THE POWER KEY TRAPS WHAT YOU LOOK AT" : "SHIFT+TAP THE POWER KEY TO SWITCH TO TRAP";
        if (bx + 4 + font.width(note) * 0.75F < x + pw - 10) small(g, note, bx + 4, fy + 6, DIM);
        int cx = x + 10, cy = y + 34, cw = pw - 20, ch = ph - 34 - 30;
        if (tab == 0) cells(g, v, cx, cy, cw, ch, mx, my);
        else storeroom(g, cx, cy, cw, ch, mx, my);
    }

    // ---------------------------------------------------------------- cells

    private void cells(GuiGraphics g, CompoundTag v, int x, int y, int w, int h, int mx, int my) {
        ListTag cells = v.getList("cells", Tag.TAG_COMPOUND);
        int lw = 150;
        listX = x; listY = y; listW = lw; listH = h;
        g.fill(x, y, x + lw, y + h, PANEL);
        frame(g, x, y, lw, h, LINE);
        int held = 0;
        for (int i = 0; i < cells.size(); i++) if (cells.getCompound(i).getBoolean("in")) held++;
        small(g, held + " HELD  ·  " + cells.size() + (cells.size() == 1 ? " CELL" : " CELLS"), x + 6, y + 5, MUTED);
        int rh = 22, top = y + 16, rows = (h - 18) / rh;
        listScroll = Math.max(0, Math.min(listScroll, Math.max(0, cells.size() - rows)));
        if (cells.isEmpty()) {
            wrap(g, "No cells yet. Look at anything (an SCP, a beast, anything living) and press Trap. A cell is built to fit it.", x + 6, top + 4, lw - 12, MUTED);
        }
        selected = Math.max(0, Math.min(selected, Math.max(0, cells.size() - 1)));
        for (int r = 0; r < rows && listScroll + r < cells.size(); r++) {
            int i = listScroll + r, ry = top + r * rh;
            CompoundTag c = cells.getCompound(i);
            boolean on = i == selected, hov = in(mx, my, x + 2, ry, lw - 4, rh - 2), occ = c.getBoolean("in");
            g.fill(x + 2, ry, x + lw - 2, ry + rh - 2, on ? 0x60402868 : hov ? 0x25FFFFFF : 0);
            if (on) g.fill(x + 2, ry, x + 4, ry + rh - 2, AC);
            g.drawString(font, String.format("%02d", i + 1), x + 8, ry + 6, on ? AC : DIM, false);
            g.drawString(font, clip(c.getString("n").isEmpty() ? "Empty cell" : c.getString("n"), lw - 50), x + 26, ry + 3, occ ? TEXT : DIM, false);
            small(g, (occ ? "HELD" : "EMPTY") + (c.getBoolean("w") ? "  ·  TANK" : ""), x + 26, ry + 13, occ ? OK : DIM);
            g.fill(x + lw - 10, ry + 8, x + lw - 6, ry + 12, occ ? OK : 0xFF3A3448);
            final int n = i;
            hits.add(new Hit(x + 2, ry, lw - 4, rh - 2, () -> selected = n));
        }
        if (cells.size() > rows) small(g, "SCROLL FOR MORE", x + 6, y + h - 8, DIM);
        // the selected cell
        int px = x + lw + 8, pw = w - lw - 8;
        g.fill(px, y, px + pw, y + h, PANEL);
        frame(g, px, y, pw, h, LINE);
        int bw = pw - 12, bh = h - 70, by = y + 6, bxx = px + 6;
        // the cell itself, as a backdrop: floor grid and glass
        g.fill(bxx, by, bxx + bw, by + bh, 0xFF0C0914);
        for (int gx = bxx; gx < bxx + bw; gx += 12) g.fill(gx, by, gx + 1, by + bh, 0x14FFFFFF);
        for (int gy = by; gy < by + bh; gy += 12) g.fill(bxx, gy, bxx + bw, gy + 1, 0x14FFFFFF);
        g.fill(bxx, by + bh - 14, bxx + bw, by + bh, 0x30B890FF);
        frame(g, bxx, by, bw, bh, 0x50B890FF);
        boolean trap = true;
        if (!cells.isEmpty()) {
            CompoundTag c = cells.getCompound(selected);
            boolean occ = c.getBoolean("in");
            LivingEntity e = occ ? model(c.getCompound("look")) : null;
            if (e != null) {
                float bbH = Math.max(0.3F, e.getBbHeight()), bbW = Math.max(0.3F, e.getBbWidth());
                int scale = (int) Math.max(4, Math.min(70, (bh - 20) * 0.85F / Math.max(bbH, bbW * 0.9F)));
                try {
                    InventoryScreen.renderEntityInInventoryFollowsMouse(g, bxx + 1, by + 1, bxx + bw - 1, by + bh - 4, scale, 0.0625F, mx, my, e);
                } catch (Throwable t) {
                    centred(g, "(it won't hold still to be seen)", bxx + bw / 2, by + bh / 2, DIM);
                }
            } else centred(g, occ ? "HELD" : "EMPTY", bxx + bw / 2, by + bh / 2 - 4, occ ? MUTED : DIM);
            if (c.getBoolean("w")) g.fill(bxx + 1, by + 1, bxx + bw - 1, by + bh - 1, 0x302060C0);
            small(g, (c.getBoolean("w") ? "TANK " : "CELL ") + String.format("%02d", selected + 1) + (c.getBoolean("w") ? "  ·  AIRLOCK" : ""), bxx + 5, by + 5, AC);
            String sz = c.getString("size");
            small(g, sz, bxx + bw - 5 - (int) (font.width(sz) * 0.75F), by + 5, DIM);
            int iy = by + bh + 6;
            g.drawString(font, clip(occ && !c.getString("n").isEmpty() ? c.getString("n") : "Empty cell", pw - 16), px + 8, iy, occ ? TEXT : MUTED, false);
            small(g, occ ? (c.contains("hp") ? "HEALTH " + c.getString("hp") : "HELD") : "READY FOR THE NEXT ONE  ·  REBUILT TO FIT IF IT MUST BE", px + 8, iy + 11, occ ? OK : DIM);
            int ay = iy + 24;
            final int idx = selected;
            int ax = px + 8;
            if (occ) ax = button(g, ax, ay, "Pull it out", OK, mx, my, () -> send("vault:out:" + idx)) + 4;
            else ax = button(g, ax, ay, "Put it in here", AC, mx, my, () -> send("vault:back:" + idx)) + 4;
            if (!occ) small(g, "LOOK AT IT FIRST", ax + 2, ay + 6, DIM);
            else ax = button(g, ax, ay, "Trap another", BAD, mx, my, () -> send("vault:trap")) + 4;
            trap = false;
        }
        if (trap) {
            centred(g, "Look at it, then:", bxx + bw / 2, by + bh / 2 - 16, MUTED);
            button(g, bxx + bw / 2 - 40, by + bh / 2 - 2, "Trap it", BAD, mx, my, () -> send("vault:trap"));
        } else if (cells.getCompound(selected).getBoolean("in")) {
            // nothing more
        } else {
            button(g, px + pw - 8 - font.width("Trap what I'm looking at") - 12, y + h - 22, "Trap what I'm looking at", BAD, mx, my, () -> send("vault:trap"));
        }
    }

    // ---------------------------------------------------------------- the storeroom

    private ItemStack hover = ItemStack.EMPTY;

    private void storeroom(GuiGraphics g, int x, int y, int w, int h, int mx, int my) {
        hover = ItemStack.EMPTY;
        // the index: twenty chests, how full each is; click to jump
        int iw = 64;
        g.fill(x, y, x + iw, y + h, PANEL);
        frame(g, x, y, iw, h, LINE);
        int total = 0;
        for (var c : chests) total += c.size();
        small(g, total + "/" + (chests.size() * 54), x + 5, y + 5, MUTED);
        int cellW = (iw - 12) / 2, cellH = Math.max(12, Math.min(22, (h - 20) / 10));
        int blockH = 16 + 6 * 18 + 8;
        for (int k = 0; k < chests.size(); k++) {
            int col = k / 10, row = k % 10, cxx = x + 4 + col * (cellW + 4), cyy = y + 16 + row * cellH;
            float fill = chests.get(k).size() / 54F;
            boolean hov = in(mx, my, cxx, cyy, cellW, cellH - 2), cur = (boardScroll + blockH / 3) / blockH == k;
            g.fill(cxx, cyy, cxx + cellW, cyy + cellH - 2, cur ? 0x50402868 : hov ? 0x25FFFFFF : 0x10FFFFFF);
            g.fill(cxx, cyy + cellH - 4, cxx + (int) (cellW * fill), cyy + cellH - 2, fill >= 1 ? BAD : AC);
            small(g, String.format("%02d", k + 1), cxx + 3, cyy + (cellH - 8) / 2, cur ? TEXT : MUTED);
            final int n = k;
            hits.add(new Hit(cxx, cyy, cellW, cellH - 2, () -> boardScroll = n * blockH));
        }
        // the board: every chest on one scrolling wall
        boardX = x + iw + 6; boardY = y; boardW = 9 * 18 + 16; boardH = h;
        g.fill(boardX, boardY, boardX + boardW, boardY + boardH, PANEL);
        frame(g, boardX, boardY, boardW, boardH, LINE);
        int max = Math.max(0, chests.size() * blockH - boardH + 4);
        if (jump >= 0 && !chests.isEmpty()) { boardScroll = jump * blockH; jump = -1; }
        boardScroll = Math.max(0, Math.min(boardScroll, max));
        int gw = 9 * 18, gx = boardX + 5;
        g.enableScissor(boardX + 1, boardY + 1, boardX + boardW - 1, boardY + boardH - 1);
        for (int k = 0; k < chests.size(); k++) {
            int top = boardY + 4 + k * blockH - boardScroll;
            if (top > boardY + boardH || top + blockH < boardY) continue;
            List<Map.Entry<Integer, ItemStack>> items = chests.get(k);
            g.drawString(font, "CHEST " + String.format("%02d", k + 1), gx, top + 2, AC, false);
            String fill = (k < 10 ? "N  ·  " : "S  ·  ") + items.size() + "/54";
            small(g, fill, gx + gw - (int) (font.width(fill) * 0.75F), top + 4, MUTED);
            int sy = top + 14;
            ItemStack[] grid = new ItemStack[54];
            for (var en : items) if (en.getKey() >= 0 && en.getKey() < 54) grid[en.getKey()] = en.getValue();
            for (int s = 0; s < 54; s++) {
                int sx = gx + (s % 9) * 18, syy = sy + (s / 9) * 18;
                g.fill(sx, syy, sx + 17, syy + 17, 0xFF0C0914);
                frame(g, sx, syy, 17, 17, 0x22FFFFFF);
                ItemStack st = grid[s];
                if (st == null) continue;
                g.renderItem(st, sx, syy);
                g.renderItemDecorations(font, st, sx, syy);
                boolean vis = syy >= boardY && syy + 17 <= boardY + boardH;
                if (vis && in(mx, my, sx, syy, 17, 17)) {
                    g.fill(sx, syy, sx + 17, syy + 17, 0x40FFFFFF);
                    hover = st;
                }
                final int kk = k, ss = s;
                if (vis) hits.add(new Hit(sx, syy, 17, 17, () -> send("vault:take:" + kk + ":" + ss + (Screen.hasShiftDown() ? ":all" : ""))));
            }
        }
        g.disableScissor();
        if (max > 0) {
            int track = boardH - 8, knob = Math.max(16, track * boardH / (chests.size() * blockH));
            int ky = boardY + 4 + (int) ((track - knob) * (boardScroll / (float) max));
            g.fill(boardX + boardW - 5, boardY + 4, boardX + boardW - 3, boardY + 4 + track, 0x20FFFFFF);
            g.fill(boardX + boardW - 5, ky, boardX + boardW - 3, ky + knob, AC);
        }
        // your own inventory: click a stack to store it, shift-click to store every stack of that kind
        int ix = boardX + boardW + 6, iwid = x + w - ix;
        g.fill(ix, y, ix + iwid, y + h, PANEL);
        frame(g, ix, y, iwid, h, LINE);
        g.drawString(font, "YOUR INVENTORY", ix + 6, y + 5, AC, false);
        var inv = Minecraft.getInstance().player.getInventory();
        int ggx = ix + (iwid - 9 * 18) / 2, ggy = y + 18;
        for (int r = 0; r < 4; r++) for (int c = 0; c < 9; c++) {
            int slot = r < 3 ? 9 + r * 9 + c : c;
            int sx = ggx + c * 18, sy = ggy + r * 18 + (r == 3 ? 5 : 0);
            g.fill(sx, sy, sx + 17, sy + 17, 0xFF0C0914);
            frame(g, sx, sy, 17, 17, r == 3 ? 0x40B890FF : 0x22FFFFFF);
            ItemStack st = inv.getItem(slot);
            if (st.isEmpty()) continue;
            g.renderItem(st, sx, sy);
            g.renderItemDecorations(font, st, sx, sy);
            if (in(mx, my, sx, sy, 17, 17)) { g.fill(sx, sy, sx + 17, sy + 17, 0x40FFFFFF); hover = st; }
            final int sl = slot;
            hits.add(new Hit(sx, sy, 17, 17, () -> send("vault:inv:" + sl + (Screen.hasShiftDown() ? ":all" : ""))));
        }
        int ty = ggy + 4 * 18 + 10;
        small(g, "CLICK: STORE THAT STACK", ggx, ty, MUTED);
        small(g, "SHIFT+CLICK: EVERY STACK OF IT", ggx, ty + 9, MUTED);
        small(g, "CHEST ITEM: A STACK  ·  SHIFT: ALL", ggx, ty + 18, MUTED);
        small(g, "UP TO 999 OF ANYTHING A SLOT", ggx, ty + 27, DIM);
        int by2 = y + h - 22;
        int nb = button(g, ggx, by2, "Store all", OK, mx, my, () -> send("vault:rest"));
        small(g, "HOTBAR STAYS", nb + 5, by2 + 6, DIM);
        if (!hover.isEmpty()) g.renderTooltip(font, hover, mx, my);
    }

    // ---------------------------------------------------------------- bits

    private int button(GuiGraphics g, int x, int y, String label, int colour, int mx, int my, Runnable run) {
        int w = font.width(label) + 14, h = 16;
        boolean hov = in(mx, my, x, y, w, h);
        g.fill(x, y, x + w, y + h, hov ? (colour & 0x00FFFFFF) | 0x50000000 : 0x18FFFFFF);
        frame(g, x, y, w, h, hov ? colour : (colour & 0x00FFFFFF) | 0x80000000);
        g.drawString(font, label, x + 7, y + 4, hov ? TEXT : colour, false);
        hits.add(new Hit(x, y, w, h, run));
        return x + w;
    }

    private void frame(GuiGraphics g, int x, int y, int w, int h, int c) {
        g.fill(x, y, x + w, y + 1, c);
        g.fill(x, y + h - 1, x + w, y + h, c);
        g.fill(x, y, x + 1, y + h, c);
        g.fill(x + w - 1, y, x + w, y + h, c);
    }

    private void small(GuiGraphics g, String s, int x, int y, int c) { PowerKeys.small(g, font, s, x, y, c); }

    private void centred(GuiGraphics g, String s, int cx, int y, int c) { g.drawString(font, s, cx - font.width(s) / 2, y, c, false); }

    private void wrap(GuiGraphics g, String s, int x, int y, int w, int c) {
        for (var line : font.split(Component.literal(s), w)) { g.drawString(font, line, x, y, c, false); y += 10; }
    }

    private String clip(String s, int w) {
        if (font.width(s) <= w) return s;
        while (s.length() > 1 && font.width(s + "…") > w) s = s.substring(0, s.length() - 1);
        return s + "…";
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
        int d = (int) Math.signum(sy);
        if (tab == 0) { listScroll = Math.max(0, listScroll - d); return true; }
        boardScroll = Math.max(0, boardScroll - d * (Screen.hasShiftDown() ? 132 : 24));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (Minecraft.getInstance().options.keyInventory.matches(key, scan)) { onClose(); return true; }
        return super.keyPressed(key, scan, mods);
    }
}
