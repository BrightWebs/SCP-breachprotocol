package com.breachprotocol.client;

import com.breachprotocol.career.Powers;
import com.breachprotocol.network.ClientCareerState;
import com.breachprotocol.network.PhoneActionPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * The console (Shift + the answer key, V by default), for the Administrator and SCP-Ω, in the
 * mod's terminal style:
 *
 * <ul>
 * <li><b>Temperament</b>: how anomalies, and every other living thing, treat you: as anyone, leaving
 *     you be, or fearing you (SCP-Ω).</li>
 * <li><b>Powers</b>: every power you hold, where it comes from, its range, and the two slots it can
 *     go in; where the slots sit on screen.</li>
 * <li><b>Targets</b>: what your powers count as hostile: the world's rule or everything, and any
 *     kind of creature, or any player, marked hostile or spared.</li>
 * <li><b>Orders</b>: for everything around you at once.</li>
 * </ul>
 */
public class CommandScreen extends Screen {
    private record Hit(int x, int y, int w, int h, Runnable run) {}

    private final List<Hit> hits = new ArrayList<>();
    private static int tab = 1;
    private static String selected = "";

    public CommandScreen() { super(Component.literal("Console")); }

    private static CompoundTag t() { return ClientCareerState.tag; }

    private static void send(String action) { PacketDistributor.sendToServer(new PhoneActionPayload(action, 0)); }

    @Override
    public boolean isPauseScreen() { return false; }

    // palette: the mod's terminal look, purple for SCP-Ω, gold for the Administrator
    private int accent() { return t().getInt("dom") >= 2 ? 0xFFB070FF : 0xFFE0B040; }

    private static final int BG = 0xF20A0710, PANEL = 0xFF140E1C, LINE = 0x40FFFFFF, TEXT = 0xFFEAE4F6, MUTED = 0xFF8A82A0, DIM = 0xFF5A5470;

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        hits.clear();
        int dom = t().getInt("dom");
        int pw = Math.min(420, width - 16), ph = Math.min(290, height - 16), x = (width - pw) / 2, y = (height - ph) / 2;
        int ac = accent();
        // frame
        g.fill(x, y, x + pw, y + ph, BG);
        frame(g, x, y, pw, ph, ac);
        g.fill(x + 1, y + 1, x + pw - 1, y + 26, PANEL);
        g.fill(x + 1, y + 26, x + pw - 1, y + 27, ac & 0x60FFFFFF);
        String title = dom >= 2 ? "SCP-Ω  ·  DOMINION" : "ADMINISTRATOR  ·  IMPLANT";
        g.drawString(font, title, x + 10, y + 6, ac, false);
        small(g, dom >= 2 ? "EVERYTHING THAT LIVES ANSWERS TO YOU" : "FOUNDATION STAFF AND ANOMALIES ANSWER TO YOU", x + 10, y + 17, MUTED);
        // tabs
        String[] tabs = {"Temperament", "Powers", "Targets", "Orders"};
        int tx = x + pw - 10;
        for (int i = tabs.length - 1; i >= 0; i--) {
            int w = font.width(tabs[i]) + 14;
            tx -= w;
            boolean on = tab == i, hov = in(mx, my, tx, y + 6, w, 15);
            g.fill(tx, y + 6, tx + w, y + 21, on ? ac & 0x50FFFFFF | 0x50000000 : hov ? 0x30FFFFFF : 0x00000000);
            if (on) g.fill(tx, y + 20, tx + w, y + 21, ac);
            g.drawString(font, tabs[i], tx + 7, y + 10, on ? TEXT : MUTED, false);
            final int n = i;
            hits.add(new Hit(tx, y + 6, w, 15, () -> tab = n));
            tx -= 4;
        }
        int cx = x + 10, cy = y + 34, cw = pw - 20, ch = ph - 44;
        switch (tab) {
            case 0 -> temperament(g, cx, cy, cw, ch, mx, my, dom);
            case 2 -> targets(g, cx, cy, cw, ch, mx, my);
            case 3 -> orders(g, cx, cy, cw, ch, mx, my);
            default -> powers(g, cx, cy, cw, ch, mx, my, dom);
        }
    }

    // ---------------------------------------------------------------- temperament

    private void temperament(GuiGraphics g, int x, int y, int w, int h, int mx, int my, int dom) {
        if (dom < 1) {
            locked(g, x, y, w, "Temperament belongs to the Administrator.", "Anything can fight a person.");
            return;
        }
        String[] names = {"As anyone", "Leave me be", "Fear me"};
        String[] glyph = {"○", "◐", "●"};
        String[][] about = {
                {"Anomalies treat you as they'd treat anyone: the hostile ones will come for you.", "Anomalies remember what you are. None of them will raise a hand to you.", "Anomalies know what you are, and back away when you come near."},
                {"Every other creature behaves as it would to anyone.", "Nothing living turns on you. The wild leaves you be.", "Hostile creatures know what you are, and keep their distance."}};
        String[] rows = {"ANOMALIES", "EVERY OTHER LIVING THING"};
        String[] keys = {"a", "c"};
        int[] cur = {t().getInt("auraA"), t().getInt("auraC")};
        int yy = y + 2;
        for (int r = 0; r < 2; r++) {
            if (r == 1 && dom < 2) {
                // the rest of the living answers only to SCP-Ω
                small(g, rows[r], x, yy, MUTED);
                yy += 9;
                wrap(g, "Every other creature sees a person, and anything can fight a person. Ascend, and choose what may.", x, yy, w, 0xFF8A8098);
                yy += 26;
                continue;
            }
            small(g, rows[r], x, yy, MUTED);
            yy += 9;
            int bw = (w - 8) / 3;
            for (int i = 0; i < 3; i++) {
                int bx = x + i * (bw + 4);
                boolean on = cur[r] == i, hov = in(mx, my, bx, yy, bw, 30);
                int col = i == 0 ? 0xFF707088 : i == 1 ? 0xFF9A70E0 : 0xFF6040A0;
                g.fill(bx, yy, bx + bw, yy + 30, on ? col & 0x60FFFFFF | 0x60000000 : hov ? 0x20FFFFFF : PANEL);
                frame(g, bx, yy, bw, 30, on ? col : LINE);
                g.pose().pushPose();
                g.pose().translate(bx + 14, yy + 15, 0);
                g.pose().scale(1.6F, 1.6F, 1F);
                g.drawString(font, glyph[i], -font.width(glyph[i]) / 2, -4, on ? col | 0xFF000000 : MUTED, false);
                g.pose().popPose();
                g.drawString(font, names[i], bx + 26, yy + 11, on ? TEXT : MUTED, false);
                final String act = "dom:aura:" + keys[r] + ":" + i;
                hits.add(new Hit(bx, yy, bw, 30, () -> send(act)));
            }
            yy += 34;
            wrap(g, about[r][Math.max(0, Math.min(2, cur[r]))], x, yy, w, 0xFFB8A8D8);
            yy += 26;
        }
        small(g, dom >= 2 ? "WHATEVER YOU CHOOSE, YOU CANNOT DIE. RIGHT-CLICKING AN SCP STILL TALKS TO IT." : "LEFT BE, NO ANOMALY CAN END YOU. RIGHT-CLICKING AN SCP STILL TALKS TO IT.", x, y + h - 10, DIM);
    }

    // ---------------------------------------------------------------- powers

    private void powers(GuiGraphics g, int x, int y, int w, int h, int mx, int my, int dom) {
        int lw = 150;
        // the list
        List<Powers.Power> all = Powers.ALL;
        if (selected.isEmpty() || Powers.get(selected) == null) { Powers.Power s0 = PowerKeys.slot(0); selected = s0 != null ? s0.id() : all.get(0).id(); }
        // a list that scrolls (the wheel, over it) when there are more powers than fit
        int rowH = 14, fit = Math.max(1, h / rowH);
        powScroll = Math.max(0, Math.min(powScroll, Math.max(0, all.size() - fit)));
        listX = x; listY = y; listW = lw; listH = h;
        for (int i = powScroll; i < all.size() && i < powScroll + fit; i++) {
            Powers.Power p = all.get(i);
            int ry = y + (i - powScroll) * rowH;
            boolean own = dom >= p.minLevel(), on = p.id().equals(selected), hov = in(mx, my, x, ry, lw, rowH);
            int col = 0xFF000000 | p.colour();
            if (on) g.fill(x, ry, x + lw, ry + rowH, col & 0x40FFFFFF | 0x40000000);
            else if (hov) g.fill(x, ry, x + lw, ry + rowH, 0x18FFFFFF);
            if (on) g.fill(x, ry, x + 2, ry + rowH, col);
            g.drawString(font, p.glyph(), x + 6, ry + (rowH - 8) / 2, own ? col : DIM, false);
            g.drawString(font, p.name(), x + 18, ry + (rowH - 8) / 2, own ? (on ? TEXT : 0xFFC8C0D8) : DIM, false);
            String tag = slotTag(p);
            if (!tag.isEmpty()) small(g, tag, x + lw - 4 - (int) (font.width(tag) * 0.75F), ry + (rowH - 6) / 2, col);
            else if (!own) small(g, "Ω", x + lw - 10, ry + (rowH - 6) / 2, DIM);
            final String id = p.id();
            hits.add(new Hit(x, ry, lw, rowH, () -> selected = id));
        }
        if (all.size() > fit) {
            int th = Math.max(10, h * fit / all.size()), ty = y + (h - th) * powScroll / Math.max(1, all.size() - fit);
            g.fill(x + lw + 2, y, x + lw + 3, y + h, 0x20FFFFFF);
            g.fill(x + lw + 1, ty, x + lw + 4, ty + th, 0x80C8A0FF);
            if (powScroll > 0) small(g, "▲", x + lw - 10, y - 7, DIM);
            if (powScroll < all.size() - fit) small(g, "▼", x + lw - 10, y + h + 1, DIM);
        }
        g.fill(x + lw + 6, y, x + lw + 7, y + h, LINE);
        // the detail
        Powers.Power p = Powers.get(selected);
        if (p == null) return;
        int dx = x + lw + 14, dw = w - lw - 14, yy = y;
        int col = 0xFF000000 | p.colour();
        boolean own = dom >= p.minLevel();
        g.pose().pushPose();
        g.pose().translate(dx + 14, yy + 14, 0);
        g.pose().scale(2.6F, 2.6F, 1F);
        g.drawString(font, p.glyph(), -font.width(p.glyph()) / 2, -4, own ? col : DIM, false);
        g.pose().popPose();
        g.drawString(font, p.name(), dx + 34, yy + 4, own ? TEXT : MUTED, false);
        small(g, p.source().toUpperCase(java.util.Locale.ROOT), dx + 34, yy + 15, col);
        yy += 32;
        yy = wrap(g, p.about(), dx, yy, dw, 0xFFC8C0D8) + 4;
        small(g, "NO RECHARGE" + (p.rMax() > 0 ? "  ·  RANGE " + p.rMin() + "-" + p.rMax() + "M" : "") + "  ·  "
                + switch (p.aim()) { case AROUND -> "ALL AROUND YOU"; case LOOK_POINT -> "WHERE YOU LOOK"; case LOOK_TARGET -> "WHAT YOU LOOK AT"; case TOGGLE -> p.rMax() > 0 ? "AROUND YOU, UNTIL YOU TAP AGAIN" : "YOU, UNTIL YOU TAP AGAIN"; default -> "YOU"; }, dx, yy, MUTED);
        yy += 12;
        if (!own) {
            wrap(g, "Only SCP-Ω can take this from the anomaly it belongs to.", dx, yy, dw, DIM);
            return;
        }
        // range
        if (p.rMax() > 0) {
            int r = PowerKeys.range(p);
            small(g, "RANGE", dx, yy, MUTED);
            String rv = r + "m";
            g.drawString(font, rv, dx + dw - font.width(rv), yy - 1, TEXT, false);
            yy += 9;
            int bx = dx + 18, bw = dw - 36;
            button(g, dx, yy, 14, 12, "-", false, 0xFF2A2236, mx, my, () -> PowerKeys.setRange(p, PowerKeys.range(p) - PowerKeys.step(p)));
            button(g, dx + dw - 14, yy, 14, 12, "+", false, 0xFF2A2236, mx, my, () -> PowerKeys.setRange(p, PowerKeys.range(p) + PowerKeys.step(p)));
            g.fill(bx, yy + 5, bx + bw, yy + 7, 0xFF2A2236);
            int fillW = (int) (bw * (r - p.rMin()) / (float) Math.max(1, p.rMax() - p.rMin()));
            g.fill(bx, yy + 5, bx + fillW, yy + 7, col);
            g.fill(bx + fillW - 1, yy + 2, bx + fillW + 2, yy + 10, TEXT);
            hits.add(new Hit(bx, yy, bw, 12, () -> {
                double f = (lastX - bx) / (double) bw;
                PowerKeys.setRange(p, p.rMin() + (int) Math.round(f * (p.rMax() - p.rMin())));
            }));
            yy += 18;
        }
        // slots
        small(g, "PUT IT IN A SLOT", dx, yy, MUTED);
        yy += 9;
        int sw = (dw - 4) / 2;
        for (int i = 0; i < 2; i++) {
            final int n = i;
            Powers.Power in = PowerKeys.slot(i);
            boolean here = in == p;
            String key = PowerKeys.SLOTS[i].getTranslatedKeyMessage().getString();
            button(g, dx + i * (sw + 4), yy, sw, 16, (here ? "✓ " : "") + "Slot " + (i + 1) + " [" + key + "]", here, col, mx, my, () -> PowerKeys.setSlot(n, p));
        }
        yy += 20;
        // left on
        if (Powers.automatic(p)) {
            boolean on = PowerKeys.auto(p);
            button(g, dx, yy, dw, 15, on ? "● Automatic: on" + (p.rMax() > 0 ? ", " + PowerKeys.range(p) + "m" : "") : "○ Automatic: off", on, col, mx, my, () -> PowerKeys.setAuto(p, !PowerKeys.auto(p)));
            yy += 17;
            small(g, p.id().equals("repel") ? "A SHIELD: NOTHING HOSTILE STAYS INSIDE THE CIRCLE." : p.id().equals("embrace") ? "FIRES WHEN YOU DROP BELOW 60% HEALTH." : "FIRES BY ITSELF WHENEVER IT'S READY AND SOMETHING HOSTILE IS IN RANGE.", dx, yy, MUTED);
            yy += 10;
        } else if (p.aim() == Powers.Aim.TOGGLE) {
            boolean on = PowerKeys.active(p);
            g.fill(dx, yy, dx + dw, yy + 15, on ? col & 0x50FFFFFF | 0x50000000 : PANEL);
            frame(g, dx, yy, dw, 15, on ? col : LINE);
            String st = on ? "● ON NOW: TAP ITS KEY AGAIN TO STOP" : "○ OFF: TAP ITS KEY TO START, TAP AGAIN TO STOP";
            small(g, st, dx + dw / 2 - (int) (font.width(st) * 0.375F), yy + 5, on ? TEXT : MUTED);
            yy += 19;
        } else {
            small(g, "BY HAND ONLY: IT NEEDS YOU TO AIM IT.", dx, yy + 2, DIM);
            yy += 14;
        }
        // the slots as they'll look, and where they sit
        small(g, "ON SCREEN", dx, yy, MUTED);
        yy += 9;
        PowerKeys.drawSlot(g, font, 0, dx, yy, 34);
        PowerKeys.drawSlot(g, font, 1, dx + 38, yy, 34);
        String[] where = {"Bottom right", "Bottom left", "Right side", "Top right", "Top left", "Hidden"};
        int ax = dx + 82, aw = dw - 82, abw = (aw - 4) / 2;
        int cur = PowerKeys.hidden() ? 5 : PowerKeys.anchor();
        for (int i = 0; i < 6; i++) {
            final int n = i;
            button(g, ax + (i % 2) * (abw + 2), yy + (i / 2) * 15 - 1, abw, 13, where[i], cur == i, 0xFF3A3050, mx, my, () -> {
                if (n == 5) PowerKeys.setHidden(true);
                else { PowerKeys.setHidden(false); PowerKeys.setAnchor(n); }
            });
        }
        small(g, "TAP: USE  ·  SHIFT+TAP: AUTO ON/OFF  ·  HOLD+SCROLL: SWAP  ·  HOLD+SHIFT+SCROLL: RANGE", x, y + h - 4, DIM);
    }

    private static String slotTag(Powers.Power p) {
        String s = "";
        for (int i = 0; i < 2; i++) if (PowerKeys.slot(i) == p) s += (s.isEmpty() ? "" : " ") + PowerKeys.SLOTS[i].getTranslatedKeyMessage().getString();
        return s;
    }

    // ---------------------------------------------------------------- targets

    private static int scroll, powScroll;
    private int listX, listY, listW, listH;
    private static String query = "";

    private static CompoundTag tgt() { return t().getCompound("tgt"); }

    private static void mark(boolean player, String key, byte v) {
        send("pow:" + (player ? "player" : "type") + ":" + v + ":" + key);
        // show it at once; the server's sync confirms it
        CompoundTag tg = tgt(), m = tg.getCompound(player ? "players" : "types");
        if (v == 0) m.remove(key);
        else if (player) { CompoundTag e = m.getCompound(key); e.putByte("v", v); m.put(key, e); }
        else m.putByte(key, v);
        tg.put(player ? "players" : "types", m);
        t().put("tgt", tg);
    }

    private void targets(GuiGraphics g, int x, int y, int w, int h, int mx, int my) {
        boolean all = tgt().getBoolean("all");
        // the rule
        String[][] modes = {
                {"◐", "The world decides", "Monsters, whatever hunts you, loose anomalies."},
                {"●", "Everything is hostile", "All of it, players too, unless you spare it."}};
        int bw = (w - 4) / 2;
        for (int i = 0; i < 2; i++) {
            int bx = x + i * (bw + 4);
            boolean on = all == (i == 1), hov = in(mx, my, bx, y, bw, 28);
            int col = i == 0 ? 0xFF9A90B8 : 0xFFE05050;
            g.fill(bx, y, bx + bw, y + 28, on ? col & 0x50FFFFFF | 0x50000000 : hov ? 0x20FFFFFF : PANEL);
            frame(g, bx, y, bw, 28, on ? col : LINE);
            g.drawString(font, modes[i][0], bx + 7, y + 10, on ? col : MUTED, false);
            g.drawString(font, modes[i][1], bx + 20, y + 5, on ? TEXT : MUTED, false);
            small(g, modes[i][2], bx + 20, y + 17, on ? 0xFFC8C0D8 : DIM);
            final String act = "pow:mode:" + i;
            final boolean to = i == 1;
            hits.add(new Hit(bx, y, bw, 28, () -> { send(act); CompoundTag tg = tgt(); tg.putBoolean("all", to); t().put("tgt", tg); }));
        }
        int top = y + 34, bottom = y + h - 12, rowH = 13;
        int lw = (int) (w * 0.62F), rx = x + lw + 8, rw = w - lw - 8;
        // ---- creatures
        small(g, "CREATURES", x, top + 3, MUTED);
        int sx = x + 48, sw = lw - 48;
        g.fill(sx, top, sx + sw, top + 11, 0xFF0E0A14);
        frame(g, sx, top, sw, 11, query.isEmpty() ? LINE : accent());
        small(g, query.isEmpty() ? "TYPE TO SEARCH" : query + ((System.currentTimeMillis() / 400) % 2 == 0 ? "_" : ""), sx + 4, top + 3, query.isEmpty() ? DIM : TEXT);
        List<String> rows = creatureRows();
        int listTop = top + 15, fit = (bottom - listTop) / rowH;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, rows.size() - fit)));
        CompoundTag types = tgt().getCompound("types");
        for (int i = 0; i < fit && i + scroll < rows.size(); i++) {
            String k = rows.get(i + scroll);
            int ry = listTop + i * rowH;
            var type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(net.minecraft.resources.ResourceLocation.parse(k));
            String nm = type.getDescription().getString();
            boolean near = PowerKeys.nearby().contains(k);
            if (in(mx, my, x, ry, lw, rowH)) g.fill(x, ry, x + lw, ry + rowH, 0x14FFFFFF);
            g.drawString(font, font.plainSubstrByWidth(nm, lw - 96), x + 4, ry + 3, 0xFFC8C0D8, false);
            if (near) small(g, "NEARBY", x + 4 + Math.min(font.width(nm), lw - 96) + 4, ry + 4, 0xFF70C090);
            byte v = types.getByte(k);
            pill(g, x + lw - 84, ry + 1, 82, v, v == 0 ? (byNature(k, type, all) ? "HOSTILE" : "LEFT ALONE") : null, mx, my, () -> mark(false, k, (byte) ((v + 1) % 3)));
        }
        if (rows.isEmpty()) small(g, query.isEmpty() ? "NOTHING SEEN YET." : "NO CREATURE CALLED THAT.", x + 4, listTop + 4, DIM);
        if (rows.size() > fit) {
            int th = Math.max(10, (bottom - listTop) * fit / rows.size()), ty = listTop + (bottom - listTop - th) * scroll / Math.max(1, rows.size() - fit);
            g.fill(x + lw + 2, listTop, x + lw + 3, bottom, 0x20FFFFFF);
            g.fill(x + lw + 1, ty, x + lw + 4, ty + th, accent());
        }
        // ---- players
        small(g, "PLAYERS", rx, top + 3, MUTED);
        CompoundTag pm = tgt().getCompound("players");
        List<String[]> ps = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        java.util.Set<String> listed = new java.util.HashSet<>();
        if (mc.getConnection() != null) for (var info : mc.getConnection().getOnlinePlayers()) {
            if (mc.player != null && info.getProfile().getId().equals(mc.player.getUUID())) continue;
            ps.add(new String[]{info.getProfile().getId().toString(), info.getProfile().getName(), "1"});
            listed.add(info.getProfile().getId().toString());
        }
        for (String k : pm.getAllKeys()) if (!listed.contains(k)) ps.add(new String[]{k, pm.getCompound(k).getString("n"), "0"});
        int py = listTop;
        for (String[] q : ps) {
            if (py + rowH > bottom) break;
            byte v = pm.getCompound(q[0]).getByte("v");
            g.drawString(font, font.plainSubstrByWidth(q[1].isEmpty() ? "Someone" : q[1], rw - 70), rx + 2, py + 3, q[2].equals("1") ? 0xFFC8C0D8 : DIM, false);
            pill(g, rx + rw - 64, py + 1, 64, v, v == 0 ? (all ? "HOSTILE" : "LEFT ALONE") : null, mx, my, () -> mark(true, q[0], (byte) ((v + 1) % 3)));
            py += rowH;
        }
        if (ps.isEmpty()) wrap(g, "No one else is here.", rx + 2, listTop + 3, rw, DIM);
        small(g, "MARKS WIN.  WHAT YOU LOOK AT WHEN YOU USE A POWER BY HAND IS HIT, SPARED OR NOT.", x, y + h - 4, DIM);
    }

    /** One mark: unmarked (what the rule says, dim), hostile (red) or spared (green). Click to cycle. */
    private void pill(GuiGraphics g, int x, int y, int w, byte v, String byRule, int mx, int my, Runnable cycle) {
        int col = v == 1 ? 0xFFE05050 : v == 2 ? 0xFF60C080 : 0xFF5A5470;
        String label = v == 1 ? "■ HOSTILE" : v == 2 ? "■ SPARED" : "· " + byRule;
        boolean hov = in(mx, my, x, y, w, 11);
        g.fill(x, y, x + w, y + 11, v == 0 ? (hov ? 0x30FFFFFF : 0x00000000) : col & 0x40FFFFFF | 0x40000000);
        frame(g, x, y, w, 11, hov ? 0x90FFFFFF : col);
        small(g, label, x + w / 2 - (int) (font.width(label) * 0.375F), y + 3, v == 0 ? MUTED : col);
        hits.add(new Hit(x, y, w, 11, cycle));
    }

    /** What the rule alone makes of a kind of creature (roughly: the server decides per creature). */
    private static boolean byNature(String k, net.minecraft.world.entity.EntityType<?> type, boolean all) {
        if (k.startsWith("breachprotocol:")) return false;
        if (all) return true;
        return type.getCategory() == net.minecraft.world.entity.MobCategory.MONSTER || k.startsWith("scp_unleashed:scp");
    }

    /** Marked first, then nearby, then everything else you've seen, or everything that matches the search. */
    private static List<String> creatureRows() {
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
        CompoundTag types = tgt().getCompound("types");
        String q = query.trim().toLowerCase(java.util.Locale.ROOT);
        java.util.function.Predicate<String> ok = k -> {
            var id = net.minecraft.resources.ResourceLocation.tryParse(k);
            if (id == null || !net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.containsKey(id)) return false;
            if (k.equals("breachprotocol:npc_seat")) return false;
            return q.isEmpty() || k.contains(q) || net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(id).getDescription().getString().toLowerCase(java.util.Locale.ROOT).contains(q);
        };
        for (String k : types.getAllKeys()) if (ok.test(k)) out.add(k);
        for (String k : PowerKeys.nearby()) if (ok.test(k)) out.add(k);
        for (String k : PowerKeys.seen()) if (ok.test(k)) out.add(k);
        if (!q.isEmpty()) for (var e : net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE) {
            String k = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e).toString();
            if ((e.getCategory() != net.minecraft.world.entity.MobCategory.MISC || k.startsWith("scp_unleashed:") || k.startsWith("breachprotocol:") || k.startsWith("mca:")) && ok.test(k)) out.add(k);
        }
        return new ArrayList<>(out);
    }


    // ---------------------------------------------------------------- orders

    private void orders(GuiGraphics g, int x, int y, int w, int h, int mx, int my) {
        String[][] orders = {
                {"Still everything nearby", "Everything within 32 blocks that means you harm calms down, for good.", "dom:all:calm"},
                {"Recall my followers", "Everything that follows you comes to your side, wherever it is in this world.", "dom:all:recall"},
                {"Release everything", "Every creature you've calmed, sent or kept is released to its own nature.", "dom:all:release"},
                {"End the storm", "SCP-107's rain, whatever it's raining, stops.", "ovr:storm"},
                {"Kill the mould", "SCP-020 dies back for sixteen blocks around you.", "ovr:mould"},
                {"Cure everyone here", "Everyone within 32 blocks is cleared of every anomalous affliction.", "ovr:cure"}};
        int yy = y + 2;
        for (String[] o : orders) {
            boolean hov = in(mx, my, x, yy, w, 26);
            g.fill(x, yy, x + w, yy + 26, hov ? 0x20FFFFFF : PANEL);
            frame(g, x, yy, w, 26, hov ? accent() : LINE);
            g.drawString(font, o[0], x + 10, yy + 4, TEXT, false);
            small(g, o[1], x + 10, yy + 16, MUTED);
            final String act = o[2];
            hits.add(new Hit(x, yy, w, 26, () -> { send(act); onClose(); }));
            yy += 29;
        }
        yy += 4;
        small(g, "ONE CREATURE AT A TIME: HOLD " + DialogueHud.ANSWER.getTranslatedKeyMessage().getString().toUpperCase(java.util.Locale.ROOT)
                + " AND LOOK AT IT. SCROLL TO CHOOSE AN ORDER, LEFT-CLICK TO GIVE IT.", x, yy, MUTED);
        small(g, "CALM · FOLLOW · STAY · HUNT · RELEASE · END", x, yy + 10, DIM);
        small(g, "CROUCH + EMPTY HAND ON AN ANOMALY: UNDO WHAT IT DID. ON A PERSON: CURE THEM.", x, yy + 20, DIM);
    }

    // ---------------------------------------------------------------- pieces

    private void locked(GuiGraphics g, int x, int y, int w, String head, String body) {
        g.drawString(font, head, x, y + 10, TEXT, false);
        wrap(g, body, x, y + 24, w, MUTED);
    }

    private void frame(GuiGraphics g, int x, int y, int w, int h, int c) {
        g.fill(x, y, x + w, y + 1, c); g.fill(x, y + h - 1, x + w, y + h, c);
        g.fill(x, y, x + 1, y + h, c); g.fill(x + w - 1, y, x + w, y + h, c);
    }

    private int wrap(GuiGraphics g, String s, int x, int y, int w, int c) {
        for (FormattedCharSequence l : font.split(Component.literal(s), w)) { g.drawString(font, l, x, y, c, false); y += 10; }
        return y;
    }

    private void small(GuiGraphics g, String s, int x, int y, int c) { PowerKeys.small(g, font, s, x, y, c); }

    private void button(GuiGraphics g, int x, int y, int w, int h, String label, boolean on, int colour, int mx, int my, Runnable run) {
        boolean hov = in(mx, my, x, y, w, h);
        g.fill(x, y, x + w, y + h, on ? colour & 0x70FFFFFF | 0x70000000 : hov ? 0x30FFFFFF : PANEL);
        frame(g, x, y, w, h, on ? colour | 0xFF000000 : hov ? 0x80FFFFFF : LINE);
        String l = font.width(label) > w - 6 ? font.plainSubstrByWidth(label, w - 10) + "…" : label;
        g.drawString(font, l, x + w / 2 - font.width(l) / 2, y + (h - 8) / 2, on ? TEXT : 0xFFC8C0D8, false);
        hits.add(new Hit(x, y, w, h, run));
    }

    private static boolean in(int mx, int my, int x, int y, int w, int h) { return mx >= x && mx < x + w && my >= y && my < y + h; }

    private double lastX;

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        lastX = mx;
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
        Powers.Power p = Powers.get(selected);
        if (tab == 2) { scroll = Math.max(0, scroll - (int) Math.signum(sy) * (Screen.hasShiftDown() ? 5 : 1)); return true; }
        if (tab == 1 && mx >= listX && mx < listX + listW + 6 && my >= listY && my < listY + listH) { powScroll = Math.max(0, powScroll - (int) Math.signum(sy)); return true; }
        if (tab == 1 && p != null && p.rMax() > 0) { PowerKeys.setRange(p, PowerKeys.range(p) + (sy > 0 ? 1 : -1) * PowerKeys.step(p)); return true; }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (tab == 2 && query.length() < 24 && (Character.isLetterOrDigit(c) || c == ' ' || c == '_' || c == '-')) { query += c; scroll = 0; return true; }
        return super.charTyped(c, mods);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (tab == 2 && key == org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE) { if (!query.isEmpty()) query = query.substring(0, query.length() - 1); scroll = 0; return true; }
        // on the Targets tab letters are for the search, the answer key's included: Esc closes
        if (tab == 2 && key != org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) return super.keyPressed(key, scan, mods);
        if (DialogueHud.ANSWER.matches(key, scan)) { CommandStance.suppress = true; onClose(); return true; }
        if (PowerKeys.CONSOLE.matches(key, scan)) { onClose(); return true; }
        return super.keyPressed(key, scan, mods);
    }
}
