package com.breachprotocol.client;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.network.ClientCareerState;
import com.breachprotocol.network.PhoneActionPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;

/**
 * The Foundation Phone, drawn like a real smartphone: a home screen with a clock, app icons,
 * a dock and notification badges, and full-screen apps you open by tapping and leave with the
 * back arrow or the home bar. Everything is drawn immediate-mode in phone-local coordinates and
 * scaled to fit the window.
 */
public class PhoneScreen extends Screen {
    // phone geometry (phone-local pixels)
    private static final int PW = 172, PH = 324, BZ = 8;          // body + bezel
    private static final int SX = BZ, SY = BZ + 6, SW = PW - BZ * 2, SH = PH - BZ * 2 - 12;
    private static final int STATUS = 12, NAVBAR = 12, HEADER = 20;

    private static final ResourceLocation WALLPAPER = tex("wallpaper");
    private static ResourceLocation tex(String n) { return BreachProtocol.id("textures/gui/phone/" + n + ".png"); }

    private enum View { HOME, MESSAGES, THREAD, MISSIONS, CALL, COMMAND, PERSONNEL, CONTAINMENT, SITE }

    private record App(String id, String label, View view, int accent) {}
    private static final App MESSAGES = new App("app_messages", "Messages", View.MESSAGES, 0xFF2FAF55);
    private static final App MISSIONS = new App("app_missions", "Missions", View.MISSIONS, 0xFFE8882A);
    private static final App PHONE = new App("app_phone", "Phone", View.CALL, 0xFF27B35A);
    private static final App COMMAND = new App("app_command", "Command", View.COMMAND, 0xFFC8303A);
    private static final App PERSONNEL = new App("app_personnel", "Personnel", View.PERSONNEL, 0xFF3D6CD0);
    private static final App CONTAIN = new App("app_containment", "Containment", View.CONTAINMENT, 0xFF1E95C2);
    private static final App SITE = new App("app_site01", "Site-01", View.SITE, 0xFF7A35C4);

    private View view = View.HOME;
    private String thread = "";
    private int scroll, contentHeight, viewportHeight;
    private boolean scrollToBottom;
    private String hover = "";

    private float scale = 1;
    private int ox, oy;

    private record Hit(int x, int y, int w, int h, Runnable action) {}
    private final List<Hit> hits = new ArrayList<>();
    private int mouseLX, mouseLY;

    public PhoneScreen() {
        super(Component.literal("Foundation Phone"));
        if (t().contains("callText")) view = View.CALL;
    }

    private static CompoundTag t() { return ClientCareerState.tag; }
    private static int rank() { return t().getInt("rank"); }

    private static void send(String action, int arg) {
        PacketDistributor.sendToServer(new PhoneActionPayload(action, arg));
    }

    private void click() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.6F, 0.25F));
    }

    private void open(View v) {
        view = v;
        scroll = 0;
        if (v == View.MESSAGES || v == View.THREAD) send("read", 0);
        if (v == View.THREAD) scrollToBottom = true;
    }

    // ================================================================ drawing helpers

    private static void rect(GuiGraphics g, int x, int y, int w, int h, int c) { g.fill(x, y, x + w, y + h, c); }

    /** Rounded rectangle (corner radius r, in pixels). */
    private static void rrect(GuiGraphics g, int x, int y, int w, int h, int r, int c) {
        r = Math.min(r, Math.min(w, h) / 2);
        g.fill(x + r, y, x + w - r, y + h, c);
        for (int i = 0; i < r; i++) {
            int inset = r - (int) Math.round(Math.sqrt(r * r - (r - i - 0.5) * (r - i - 0.5)));
            g.fill(x + inset, y + i, x + r, y + i + 1, c);
            g.fill(x + w - r, y + i, x + w - inset, y + i + 1, c);
            g.fill(x + inset, y + h - i - 1, x + r, y + h - i, c);
            g.fill(x + w - r, y + h - i - 1, x + w - inset, y + h - i, c);
        }
        g.fill(x, y + r, x + r, y + h - r, c);
        g.fill(x + w - r, y + r, x + w, y + h - r, c);
    }

    private static void circle(GuiGraphics g, int cx, int cy, int r, int c) {
        for (int dy = -r; dy < r; dy++) {
            int dx = (int) Math.round(Math.sqrt(r * r - (dy + 0.5) * (dy + 0.5)));
            g.fill(cx - dx, cy + dy, cx + dx, cy + dy + 1, c);
        }
    }

    private void text(GuiGraphics g, String s, int x, int y, int c) { g.drawString(font, s, x, y, c, false); }
    private void textC(GuiGraphics g, String s, int cx, int y, int c) { g.drawString(font, s, cx - font.width(s) / 2, y, c, false); }

    private String ellipsize(String s, int w) {
        if (font.width(s) <= w) return s;
        while (s.length() > 1 && font.width(s + "...") > w) s = s.substring(0, s.length() - 1);
        return s + "...";
    }

    /** Draws wrapped text and returns its height. */
    private int wrap(GuiGraphics g, String s, int x, int y, int w, int c) {
        List<FormattedCharSequence> lines = font.split(Component.literal(s), w);
        for (int i = 0; i < lines.size(); i++) g.drawString(font, lines.get(i), x, y + i * 10, c, false);
        return lines.size() * 10;
    }

    private int wrapHeight(String s, int w) { return font.split(Component.literal(s), w).size() * 10; }

    private void hit(int x, int y, int w, int h, Runnable r) { hits.add(new Hit(x, y, w, h, r)); }
    private boolean over(int x, int y, int w, int h) { return mouseLX >= x && mouseLX < x + w && mouseLY >= y && mouseLY < y + h; }

    /** A tappable pill button. */
    private void pill(GuiGraphics g, int x, int y, int w, int h, String label, int color, boolean enabled, Runnable action) {
        boolean hov = enabled && over(x, y, w, h);
        rrect(g, x, y, w, h, h / 2, enabled ? (hov ? brighten(color) : color) : 0xFF3A4048);
        textC(g, ellipsize(label, w - 8), x + w / 2, y + (h - 8) / 2, enabled ? 0xFFFFFFFF : 0xFF8A9098);
        if (enabled) hit(x, y, w, h, action);
    }

    private static int brighten(int c) {
        int a = c >>> 24, r = Math.min(255, ((c >> 16) & 255) + 30), gr = Math.min(255, ((c >> 8) & 255) + 30), b = Math.min(255, (c & 255) + 30);
        return a << 24 | r << 16 | gr << 8 | b;
    }

    private static int seatColor(String sender) {
        int seat = 7;
        try {
            if (sender.startsWith("O5-")) seat = Integer.parseInt(sender.substring(3, sender.indexOf(' ') > 0 ? sender.indexOf(' ') : sender.length()));
        } catch (Exception ignored) {}
        int[] pal = {0xFF8B1E2A, 0xFF2E7D4F, 0xFF3A5BA8, 0xFF7B4BB0, 0xFFB0732B, 0xFF2C8C96, 0xFF9E2E5A, 0xFF5D6B2F, 0xFF4B4FA8, 0xFF8A5A2B, 0xFF6A6A72, 0xFF2D6E9E, 0xFF5A2D8C};
        return pal[Math.floorMod(seat - 1, pal.length)];
    }

    private void avatar(GuiGraphics g, String sender, int cx, int cy, int r) {
        circle(g, cx, cy, r, seatColor(sender));
        String label = sender.startsWith("O5-") ? sender.substring(3, Math.max(4, sender.indexOf(' ') > 0 ? sender.indexOf(' ') : 4)) : "?";
        textC(g, label, cx, cy - 4, 0xFFFFFFFF);
    }

    private static String nick(String sender) {
        int q = sender.indexOf('"');
        return q >= 0 ? sender.substring(q + 1, sender.lastIndexOf('"') > q ? sender.lastIndexOf('"') : sender.length()) : sender;
    }

    private static String seatOf(String sender) {
        int sp = sender.indexOf(' ');
        return sp > 0 ? sender.substring(0, sp) : sender;
    }

    // ================================================================ data helpers

    private static int unread() {
        int n = 0;
        for (Tag e : t().getList("messages", Tag.TAG_COMPOUND)) if (!((CompoundTag) e).getBoolean("r")) n++;
        return n;
    }

    private static String clock() {
        long day = (t().getLong("now") + 6000) % 24000;
        return String.format("%02d:%02d", day / 1000, (day % 1000) * 60 / 1000);
    }

    private List<App> gridApps() {
        List<App> list = new ArrayList<>(List.of(PERSONNEL, CONTAIN));
        if (rank() >= 4) list.add(0, COMMAND);
        if (rank() >= 7) list.add(SITE);
        return list;
    }

    private int badge(App a) {
        if (a == MESSAGES) return unread();
        if (a == MISSIONS) return t().getList("missions", Tag.TAG_COMPOUND).size();
        if (a == PHONE) return t().contains("callText") ? 1 : 0;
        return 0;
    }

    // ================================================================ render

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        scale = Math.min(1.25F, (height - 16) / (float) PH);
        ox = (int) ((width - PW * scale) / 2);
        oy = (int) ((height - PH * scale) / 2);
        mouseLX = (int) ((mouseX - ox) / scale);
        mouseLY = (int) ((mouseY - oy) / scale);
        hits.clear();
        hover = "";

        g.pose().pushPose();
        g.pose().translate(ox, oy, 0);
        g.pose().scale(scale, scale, 1);

        // body
        rrect(g, -2, -2, PW + 4, PH + 4, 22, 0xFF050506);
        rrect(g, 0, 0, PW, PH, 20, 0xFF1B1D23);
        rect(g, -3, 70, 2, 26, 0xFF2A2D35);            // volume rocker
        rect(g, PW + 1, 84, 2, 34, 0xFF2A2D35);        // power button
        rrect(g, SX, SY, SW, SH, 14, 0xFF000000);

        // screen content, clipped to the display
        scissor(g, SX, SY, SW, SH);
        switch (view) {
            case HOME -> drawHome(g);
            default -> drawApp(g);
        }
        drawStatusBar(g);
        drawNavBar(g);
        g.disableScissor();

        rrect(g, PW / 2 - 20, SY + 3, 40, 8, 4, 0xFF000000);  // camera notch (over status bar)
        circle(g, PW / 2 + 12, SY + 7, 2, 0xFF1A2230);

        if (!hover.isEmpty()) {
            int hh = wrapHeight(hover, SW - 16) + 8;
            rrect(g, SX + 4, SY + SH - NAVBAR - hh - 4, SW - 8, hh, 6, 0xEE10141A);
            wrap(g, hover, SX + 8, SY + SH - NAVBAR - hh, SW - 16, 0xFFCFE6EE);
        }
        g.pose().popPose();
    }

    private void scissor(GuiGraphics g, int x, int y, int w, int h) {
        g.enableScissor(ox + (int) (x * scale), oy + (int) (y * scale), ox + (int) ((x + w) * scale), oy + (int) ((y + h) * scale));
    }

    private void drawStatusBar(GuiGraphics g) {
        boolean dark = view == View.HOME || view == View.CALL;
        if (!dark) rect(g, SX, SY, SW, STATUS, 0xFF0E1117);
        text(g, clock(), SX + 10, SY + 2, 0xFFFFFFFF);
        // signal + battery
        int x = SX + SW - 32;
        for (int i = 0; i < 4; i++) rect(g, x + i * 3, SY + 8 - i * 2, 2, 2 + i * 2, 0xFFFFFFFF);
        rrect(g, x + 14, SY + 3, 13, 6, 1, 0xFFBFC6CE);
        rect(g, x + 15, SY + 4, 9, 4, 0xFF5BE36F);
        rect(g, x + 27, SY + 5, 1, 2, 0xFFBFC6CE);
    }

    private void drawNavBar(GuiGraphics g) {
        int y = SY + SH - NAVBAR + 4;
        boolean hov = view != View.HOME && over(SX + SW / 2 - 24, y - 4, 48, NAVBAR);
        rrect(g, SX + SW / 2 - 22, y, 44, 3, 1, hov ? 0xFFFFFFFF : 0xCCFFFFFF);
        if (view != View.HOME) hit(SX + SW / 2 - 30, y - 5, 60, NAVBAR, () -> open(View.HOME));
    }

    // ---------------------------------------------------------------- home screen

    private void drawHome(GuiGraphics g) {
        g.blit(WALLPAPER, SX, SY, 0, 0, SW, SH, 156, 300);
        // clock
        g.pose().pushPose();
        g.pose().translate(SX + SW / 2F, SY + 24, 0);
        g.pose().scale(3, 3, 1);
        String c = clock();
        g.drawString(font, c, -font.width(c) / 2, 0, 0xFFFFFFFF, false);
        g.pose().popPose();
        long day = t().getLong("now") / 24000 + 1;
        textC(g, "Site Day " + day + "  ·  " + (t().getBoolean("ascended") ? "SCP-Ω" : t().getString("personnel")), SX + SW / 2, SY + 54, 0xFFB8C6D6);

        // notification card
        int ny = SY + 68;
        if (t().contains("callText")) {
            rrect(g, SX + 8, ny, SW - 16, 26, 8, over(SX + 8, ny, SW - 16, 26) ? 0xEE1F6E3A : 0xDD17502C);
            text(g, "☎ Incoming call", SX + 14, ny + 4, 0xFFB8FFCC);
            text(g, ellipsize(t().getString("callFrom"), SW - 30), SX + 14, ny + 14, 0xFFFFFFFF);
            hit(SX + 8, ny, SW - 16, 26, () -> open(View.CALL));
        } else {
            ListTag msgs = t().getList("messages", Tag.TAG_COMPOUND);
            if (!msgs.isEmpty()) {
                CompoundTag m = msgs.getCompound(0);
                rrect(g, SX + 8, ny, SW - 16, 26, 8, over(SX + 8, ny, SW - 16, 26) ? 0xEE2A3340 : 0xCC1A2029);
                text(g, ellipsize(seatOf(m.getString("s")) + " " + nick(m.getString("s")), SW - 30), SX + 14, ny + 4, m.getBoolean("r") ? 0xFF9FB0C0 : 0xFF7FE0FF);
                text(g, ellipsize(m.getString("b"), SW - 30), SX + 14, ny + 14, 0xFFE0E6EC);
                final String sender = m.getString("s");
                hit(SX + 8, ny, SW - 16, 26, () -> { thread = sender; open(View.THREAD); });
            }
        }

        // app grid
        List<App> apps = gridApps();
        int cols = 3, cell = (SW - 16) / cols;
        for (int i = 0; i < apps.size(); i++) {
            int x = SX + 8 + (i % cols) * cell + (cell - 32) / 2, y = SY + 106 + (i / cols) * 54;
            drawIcon(g, apps.get(i), x, y, true);
        }

        // dock
        int dy = SY + SH - NAVBAR - 50;
        rrect(g, SX + 8, dy, SW - 16, 46, 14, 0x66FFFFFF);
        App[] dock = {PHONE, MESSAGES, MISSIONS};
        int dc = (SW - 16) / 3;
        for (int i = 0; i < dock.length; i++) drawIcon(g, dock[i], SX + 8 + i * dc + (dc - 32) / 2, dy + 7, false);
    }

    private void drawIcon(GuiGraphics g, App a, int x, int y, boolean label) {
        boolean hov = over(x, y, 32, 32);
        if (hov) rrect(g, x - 2, y - 2, 36, 36, 9, 0x44FFFFFF);
        g.blit(tex(a.id()), x, y, 0, 0, 32, 32, 32, 32);
        int b = badge(a);
        if (b > 0) {
            circle(g, x + 30, y + 3, 6, 0xFFE8262E);
            textC(g, b > 9 ? "9+" : String.valueOf(b), x + 30, y - 1, 0xFFFFFFFF);
        }
        if (label) textC(g, a.label(), x + 16, y + 36, 0xFFFFFFFF);
        hit(x - 4, y - 4, 40, label ? 50 : 40, () -> open(a.view()));
    }

    // ---------------------------------------------------------------- app chrome

    private App appFor(View v) {
        return switch (v) {
            case MESSAGES, THREAD -> MESSAGES;
            case MISSIONS -> MISSIONS;
            case CALL -> PHONE;
            case COMMAND -> COMMAND;
            case PERSONNEL -> PERSONNEL;
            case CONTAINMENT -> CONTAIN;
            default -> SITE;
        };
    }

    private void drawApp(GuiGraphics g) {
        App app = appFor(view);
        rect(g, SX, SY, SW, SH, view == View.CALL ? 0xFF0B1410 : 0xFF0E1117);
        int top = SY + STATUS;
        if (view != View.CALL) {
            rect(g, SX, top, SW, HEADER, app.accent());
            String title = view == View.THREAD ? nick(thread) : app.label();
            textC(g, ellipsize(title, SW - 50), SX + SW / 2, top + 6, 0xFFFFFFFF);
            boolean hov = over(SX, top, 26, HEADER);
            text(g, "‹", SX + 8, top + 5, hov ? 0xFFFFFFFF : 0xDDFFFFFF);
            text(g, "Back", SX + 14, top + 6, hov ? 0xFFFFFFFF : 0x99FFFFFF);
            hit(SX, top, 40, HEADER, () -> open(view == View.THREAD ? View.MESSAGES : View.HOME));
            top += HEADER;
        }
        final int bottom = SY + SH - NAVBAR, vtop = top;
        viewportHeight = bottom - top;
        scissor(g, SX, top, SW, viewportHeight);
        int y0 = top + 4 - scroll;
        int end = switch (view) {
            case MESSAGES -> drawMessages(g, y0);
            case THREAD -> drawThread(g, y0);
            case MISSIONS -> drawMissions(g, y0);
            case CALL -> drawCall(g, top);
            case COMMAND -> drawCommand(g, y0);
            case PERSONNEL -> drawPersonnel(g, y0);
            case CONTAINMENT -> drawContainment(g, y0);
            default -> drawSite(g, y0);
        };
        contentHeight = end - y0 + 6;
        if (scrollToBottom) {
            scroll = Math.max(0, contentHeight - viewportHeight);
            scrollToBottom = false;
        }
        scroll = Mth.clamp(scroll, 0, Math.max(0, contentHeight - viewportHeight));
        // hits outside the viewport must not fire
        hits.removeIf(h -> h.y() + h.h() < vtop || h.y() > bottom);
        scissor(g, SX, SY, SW, SH);
        if (contentHeight > viewportHeight) {
            int thumb = Math.max(14, viewportHeight * viewportHeight / contentHeight);
            int pos = (int) ((viewportHeight - thumb) * (scroll / (float) Math.max(1, contentHeight - viewportHeight)));
            rrect(g, SX + SW - 4, top + pos, 2, thumb, 1, 0x66FFFFFF);
        }
    }

    private void empty(GuiGraphics g, int y, String title, String sub) {
        textC(g, title, SX + SW / 2, y + 30, 0xFFE0E6EC);
        wrapCentered(g, sub, y + 44);
    }

    private void wrapCentered(GuiGraphics g, String s, int y) {
        List<FormattedCharSequence> lines = font.split(Component.literal(s), SW - 24);
        for (int i = 0; i < lines.size(); i++) {
            g.drawString(font, lines.get(i), SX + SW / 2 - font.width(lines.get(i)) / 2, y + i * 10, 0xFF8090A0, false);
        }
    }

    // ---------------------------------------------------------------- Messages

    private record ThreadInfo(String sender, String last, long time, int unread) {}

    private List<ThreadInfo> threads() {
        LinkedHashMap<String, ThreadInfo> map = new LinkedHashMap<>();
        for (Tag e : t().getList("messages", Tag.TAG_COMPOUND)) {
            CompoundTag m = (CompoundTag) e;
            String s = m.getString("s");
            ThreadInfo prev = map.get(s);
            int un = (prev == null ? 0 : prev.unread()) + (m.getBoolean("r") ? 0 : 1);
            map.put(s, prev == null ? new ThreadInfo(s, m.getString("b"), m.getLong("t"), un) : new ThreadInfo(s, prev.last(), prev.time(), un));
        }
        return new ArrayList<>(map.values());
    }

    private String ago(long time) {
        long min = Math.max(0, (t().getLong("now") - time) / 1200);
        return min == 0 ? "now" : min < 60 ? min + "m" : (min / 60) + "h";
    }

    private int drawMessages(GuiGraphics g, int y) {
        List<ThreadInfo> list = threads();
        if (list.isEmpty()) { empty(g, y, "No messages", "The Council will be in touch."); return y + 80; }
        for (ThreadInfo th : list) {
            int h = 32;
            if (over(SX, y, SW, h)) rect(g, SX, y, SW, h, 0xFF1A202A);
            avatar(g, th.sender(), SX + 18, y + 16, 11);
            text(g, ellipsize(nick(th.sender()), SW - 80), SX + 34, y + 5, 0xFFFFFFFF);
            text(g, ago(th.time()), SX + SW - 8 - font.width(ago(th.time())), y + 5, 0xFF7A8794);
            text(g, ellipsize(th.last(), SW - 56), SX + 34, y + 17, th.unread() > 0 ? 0xFFDDE7F0 : 0xFF8794A0);
            if (th.unread() > 0) circle(g, SX + SW - 12, y + 21, 3, 0xFF2F9BFF);
            rect(g, SX + 34, y + h - 1, SW - 34, 1, 0xFF20262E);
            hit(SX, y, SW, h, () -> { thread = th.sender(); open(View.THREAD); });
            y += h;
        }
        return y;
    }

    private int drawThread(GuiGraphics g, int y) {
        ListTag msgs = t().getList("messages", Tag.TAG_COMPOUND);
        List<CompoundTag> mine = new ArrayList<>();
        for (Tag e : msgs) if (((CompoundTag) e).getString("s").equals(thread)) mine.add((CompoundTag) e);
        Collections.reverse(mine);  // oldest first
        textC(g, thread, SX + SW / 2, y + 2, 0xFF6F7C88);
        y += 16;
        int maxW = SW - 44;
        for (CompoundTag m : mine) {
            String body = m.getString("b");
            int tw = Math.min(maxW, font.width(body));
            int h = wrapHeight(body, maxW) + 8;
            avatar(g, thread, SX + 12, y + h - 8, 7);
            rrect(g, SX + 22, y, tw + 10, h, 7, 0xFF262D38);
            wrap(g, body, SX + 27, y + 4, maxW, 0xFFEFF3F7);
            y += h + 2;
            text(g, ago(m.getLong("t")), SX + 24, y, 0xFF5E6A76);
            y += 12;
        }
        return y;
    }

    // ---------------------------------------------------------------- Missions

    private int drawMissions(GuiGraphics g, int y) {
        ListTag ms = t().getList("missions", Tag.TAG_COMPOUND);
        long now = t().getLong("now");
        if (ms.isEmpty()) {
            empty(g, y, "No assignments", t().getBoolean("ascended") ? "No one gives you orders anymore."
                    : rank() >= 7 ? "The Administrator assigns, not receives. Use Command and answer the Council's calls."
                    : "Stand by. Orders arrive every few minutes.");
            return y + 90;
        }
        for (int i = 0; i < ms.size(); i++) {
            CompoundTag m = ms.getCompound(i);
            String type = m.getString("type");
            int chip = switch (type) {
                case "RECOVER" -> 0xFFD0393E; case "TERMINATE" -> 0xFF8E3BC4; case "CARE" -> 0xFFE58A2A;
                case "SUPPLY" -> 0xFF2F8FD8; default -> 0xFFB04FE0;
            };
            String desc = m.getString("desc");
            int x = SX + 6, w = SW - 12;
            int h = 30 + wrapHeight(desc, w - 12) + (type.equals("FOUNDERS_TRIAL") ? 0 : 20);
            rrect(g, x, y, w, h, 8, 0xFF19202A);
            String label = type.replace('_', ' ');
            rrect(g, x + 6, y + 5, font.width(label) + 8, 11, 5, chip);
            text(g, label, x + 10, y + 7, 0xFFFFFFFF);
            text(g, "O5-" + m.getInt("giver"), x + w - 6 - font.width("O5-" + m.getInt("giver")), y + 7, 0xFF7A8794);
            int dy = y + 20 + wrap(g, desc, x + 6, y + 20, w - 12, 0xFFE2E8EE);
            float pct = m.getInt("progress") / (float) Math.max(1, m.getInt("target"));
            rrect(g, x + 6, dy + 2, w - 12, 4, 2, 0xFF2A323D);
            rrect(g, x + 6, dy + 2, Math.max(4, (int) ((w - 12) * pct)), 4, 2, chip);
            String info = m.getInt("progress") + "/" + m.getInt("target");
            if (m.getBoolean("hasPos")) info += "   ⌖ " + m.getInt("x") + ", " + m.getInt("z");
            long exp = m.getLong("expires");
            if (exp < Long.MAX_VALUE / 2) { long s = Math.max(0, (exp - now) / 20); info += "   ⏱ " + s / 60 + ":" + String.format("%02d", s % 60); }
            text(g, ellipsize(info, w - 12), x + 6, dy + 9, 0xFF94A3B0);
            final int idx = i;
            if (type.equals("SUPPLY")) {
                pill(g, x + 6, y + h - 17, (w - 18) / 2, 13, "Submit", 0xFF2E9E57, true, () -> send("submit", idx));
                pill(g, x + 12 + (w - 18) / 2, y + h - 17, (w - 18) / 2, 13, "Abandon", 0xFF5A3036, true, () -> send("abandon", idx));
            } else if (!type.equals("FOUNDERS_TRIAL")) {
                pill(g, x + 6, y + h - 17, w - 12, 13, "Abandon", 0xFF5A3036, true, () -> send("abandon", idx));
            }
            y += h + 6;
        }
        return y;
    }

    // ---------------------------------------------------------------- Phone / calls

    private int drawCall(GuiGraphics g, int top) {
        CompoundTag t = t();
        int cx = SX + SW / 2;
        if (!t.contains("callText")) {
            int y = top + 30;
            circle(g, cx, y + 20, 20, 0xFF1C3A28);
            textC(g, "☎", cx, y + 16, 0xFF7CFF9A);
            textC(g, "No incoming calls", cx, y + 52, 0xFFE0E6EC);
            List<FormattedCharSequence> lines = font.split(Component.literal(rank() >= 7
                    ? "The Council calls you for orders every few minutes. Convene them any time from Command."
                    : rank() >= 6 ? "As O5-13 you break the Council's tied votes. They'll call."
                    : "The Overseers don't call staff. Rise to the Council and they will."), SW - 24);
            for (int i = 0; i < lines.size(); i++) g.drawString(font, lines.get(i), cx - font.width(lines.get(i)) / 2, y + 66 + i * 10, 0xFF8090A0, false);
            return y + 120;
        }
        String from = t.getString("callFrom");
        long ms = System.currentTimeMillis();
        int y = top + 14;
        for (int i = 0; i < 3; i++) {
            float p = ((ms / 1400F) + i / 3F) % 1F;
            int r = 18 + (int) (p * 22);
            int alpha = (int) ((1 - p) * 70);
            circle(g, cx, y + 22, r, alpha << 24 | 0x3AE07A);
        }
        circle(g, cx, y + 22, 18, seatColor(from));
        textC(g, seatOf(from).replace("O5-", ""), cx, y + 18, 0xFFFFFFFF);
        textC(g, ellipsize(nick(from), SW - 16), cx, y + 50, 0xFFFFFFFF);
        textC(g, seatOf(from) + "  ·  incoming secure call", cx, y + 61, 0xFF8AE0A8);
        y += 76;
        String body = t.getString("callText");
        int bh = wrapHeight(body, SW - 24) + 10;
        rrect(g, SX + 6, y, SW - 12, bh, 8, 0xFF16241C);
        wrap(g, body, SX + 12, y + 5, SW - 24, 0xFFE8F2EC);
        y += bh + 8;
        ListTag opts = t.getList("callOptions", Tag.TAG_STRING);
        for (int i = 0; i < opts.size(); i++) {
            int color = i == 0 ? 0xFF2EA65A : i == opts.size() - 1 ? 0xFFC0383F : 0xFF3C6FB8;
            final int idx = i;
            pill(g, SX + 10, y, SW - 20, 16, opts.getString(i), color, true, () -> { send("answer", idx); open(View.HOME); });
            y += 20;
        }
        return y;
    }

    // ---------------------------------------------------------------- Command

    private int drawCommand(GuiGraphics g, int y) {
        ListTag dirs = t().getList("directives", Tag.TAG_COMPOUND);
        boolean inSite = t().getBoolean("inSite");
        List<CompoundTag> shown = new ArrayList<>();
        for (Tag e : dirs) {
            CompoundTag d = (CompoundTag) e;
            String id = d.getString("id");
            if (id.equals("site01") || id.equals("return")) continue;   // those live in the Site-01 app
            shown.add(d);
        }
        if (shown.isEmpty()) { empty(g, y, "No authority", "Orders unlock at Level 4 clearance."); return y + 80; }
        int cols = 2, gap = 6, tw = (SW - 12 - gap) / cols, th = 46;
        for (int i = 0; i < shown.size(); i++) {
            CompoundTag d = shown.get(i);
            int x = SX + 6 + (i % cols) * (tw + gap), ty = y + (i / cols) * (th + gap);
            long cd = d.getLong("cd");
            boolean ready = cd <= 0;
            boolean hov = over(x, ty, tw, th);
            rrect(g, x, ty, tw, th, 8, ready ? (hov ? 0xFF2A1A1E : 0xFF221519) : 0xFF16181C);
            rrect(g, x + 5, ty + 5, 14, 14, 4, ready ? 0xFFC8303A : 0xFF3A3D44);
            textC(g, glyph(d.getString("id")), x + 12, ty + 8, 0xFFFFFFFF);
            List<FormattedCharSequence> lines = font.split(Component.literal(d.getString("label")), tw - 10);
            for (int l = 0; l < Math.min(2, lines.size()); l++) g.drawString(font, lines.get(l), x + 5, ty + 22 + l * 10, ready ? 0xFFF0E6E8 : 0xFF6F757E, false);
            if (!ready) {
                String s = cd / 60 + ":" + String.format("%02d", cd % 60);
                text(g, s, x + tw - 5 - font.width(s), ty + 8, 0xFFE8A03A);
            } else {
                final String id = d.getString("id");
                hit(x, ty, tw, th, () -> send("dir:" + id, 0));
            }
            if (hov) hover = d.getString("desc");
        }
        return y + ((shown.size() + 1) / cols) * (th + gap);
    }

    private static String glyph(String id) {
        return switch (id) {
            case "requisition" -> "▣"; case "lockdown" -> "⚿"; case "amnestic" -> "✧"; case "mtf" -> "⚔";
            case "convene" -> "☎"; case "alpha1" -> "☠"; case "fortify" -> "⛨"; case "global" -> "⬢";
            default -> "★";
        };
    }

    // ---------------------------------------------------------------- Personnel

    private int drawPersonnel(GuiGraphics g, int y) {
        CompoundTag t = t();
        boolean asc = t.getBoolean("ascended");
        int r = rank();
        int x = SX + 6, w = SW - 12;
        // ID card
        rrect(g, x, y, w, 88, 8, 0xFFEDEFF2);
        int band = asc ? 0xFF9B4BE0 : r >= 7 ? 0xFF7A35C4 : r >= 6 ? 0xFF8B1E2A : 0xFF2C3440;
        rrect(g, x, y, w, 16, 8, band);
        rect(g, x, y + 8, w, 8, band);
        text(g, "SCP FOUNDATION", x + 6, y + 4, 0xFFFFFFFF);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            rect(g, x + 5, y + 21, 28, 28, 0xFFB8BEC6);
            PlayerFaceRenderer.draw(g, mc.player.getSkin().texture(), x + 6, y + 22, 26);
            text(g, ellipsize(mc.player.getName().getString(), w - 44), x + 38, y + 22, 0xFF111418);
        }
        text(g, t.getString("personnel"), x + 38, y + 33, 0xFF55606C);
        List<FormattedCharSequence> title = font.split(Component.literal(t.getString("title")), w - 12);
        for (int i = 0; i < Math.min(2, title.size()); i++) g.drawString(font, title.get(i), x + 6, y + 54 + i * 10, asc ? 0xFF8A2BE2 : 0xFF1C2430, false);
        String clr = asc ? "Ω" : r >= 6 ? "O5" : String.valueOf(Math.max(0, Math.min(5, r)));
        g.pose().pushPose();
        g.pose().translate(x + w - 8, y + 22, 0);
        g.pose().scale(2, 2, 1);
        g.drawString(font, clr, -font.width(clr), 0, band, false);
        g.pose().popPose();
        text(g, "clearance", x + w - 6 - font.width("clearance"), y + 41, 0xFF7A8490);
        y += 96;

        int next = t.getInt("nextXp"), cur = t.getInt("curXp"), xp = t.getInt("xp");
        if (!asc && next > 0) {
            text(g, "Clearance XP", x, y, 0xFFB8C6D6);
            String v = xp + " / " + next;
            text(g, v, x + w - font.width(v), y, 0xFF7FD6FF);
            rrect(g, x, y + 11, w, 5, 2, 0xFF27303A);
            rrect(g, x, y + 11, Math.max(5, (int) (w * Mth.clamp((xp - cur) / (float) Math.max(1, next - cur), 0, 1))), 5, 2, 0xFF3FB8FF);
            y += 22;
            if (r == 5) { text(g, "Next: a seat on the O5 Council", x, y, 0xFF8794A0); y += 12; }
            if (r == 6) { text(g, "Next: The Founder's Trial", x, y, 0xFF8794A0); y += 12; }
        }
        if (r >= 5) {
            int st = t.getInt("stability");
            int col = st < 25 ? 0xFFE03A3A : st > 85 ? 0xFF41D07A : 0xFFE0B43A;
            text(g, "Site Stability", x, y, 0xFFB8C6D6);
            text(g, st + "%", x + w - font.width(st + "%"), y, col);
            rrect(g, x, y + 11, w, 5, 2, 0xFF27303A);
            rrect(g, x, y + 11, Math.max(5, w * st / 100), 5, 2, col);
            y += 24;
        }
        text(g, "Access", x, y, 0xFFB8C6D6);
        y += 12;
        String[][] unlocks = {{"4", "Requisition supplies"}, {"5", "Site lockdown, stability"}, {"6", "Council votes, MTF, amnestics"}, {"7", "Command the Council, Site-01"}};
        for (String[] u : unlocks) {
            boolean have = r >= Integer.parseInt(u[0]);
            circle(g, x + 4, y + 4, 3, have ? 0xFF41D07A : 0xFF3A424C);
            text(g, u[1], x + 12, y, have ? 0xFFE2E8EE : 0xFF66717C);
            y += 12;
        }
        return y;
    }

    // ---------------------------------------------------------------- Containment

    private int drawContainment(GuiGraphics g, int y) {
        ListTag list = t().getList("nearby", Tag.TAG_COMPOUND);
        text(g, "Anomalies within 64m", SX + 8, y, 0xFF8AA4B4);
        y += 14;
        if (list.isEmpty()) { empty(g, y - 20, "All quiet", "No anomalies detected nearby."); return y + 60; }
        for (Tag e : list) {
            CompoundTag a = (CompoundTag) e;
            boolean contained = a.getBoolean("c");
            boolean containable = a.getBoolean("k");
            int x = SX + 6, w = SW - 12;
            rrect(g, x, y, w, 26, 7, 0xFF16202A);
            int dot = contained ? 0xFF3FD0FF : containable ? 0xFFE8323C : 0xFF8A96A2;
            circle(g, x + 10, y + 13, 4, dot);
            text(g, a.getString("d"), x + 20, y + 4, 0xFFFFFFFF);
            String cls = a.getString("o");
            int cc = cls.equals("Safe") ? 0xFF3AA860 : cls.equals("Euclid") ? 0xFFC8A030 : 0xFFC0383F;
            rrect(g, x + 20 + font.width(a.getString("d")) + 4, y + 3, font.width(cls) + 6, 10, 4, cc);
            text(g, cls, x + 23 + font.width(a.getString("d")) + 4, y + 4, 0xFFFFFFFF);
            String status = contained ? "CONTAINED" : containable ? "NOT CONTAINED" : a.getString("n").isEmpty() ? "-" : a.getString("n");
            text(g, ellipsize(status, w - 60), x + 20, y + 15, contained ? 0xFF7FD6FF : containable ? 0xFFFF7A7A : 0xFF9AA6B2);
            String dist = a.getInt("m") + "m";
            text(g, dist, x + w - 6 - font.width(dist), y + 15, 0xFF7A8794);
            y += 30;
        }
        return y;
    }

    // ---------------------------------------------------------------- Site-01

    private int drawSite(GuiGraphics g, int y) {
        int x = SX + 6, w = SW - 12;
        rrect(g, x, y, w, 70, 10, 0xFF1D1230);
        textC(g, "SITE-01", SX + SW / 2, y + 10, 0xFFE2C8FF);
        textC(g, "The Administrator's Sanctum", SX + SW / 2, y + 22, 0xFFA088C8);
        textC(g, t().getString("site"), SX + SW / 2, y + 38, 0xFFFFFFFF);
        textC(g, t().getBoolean("inSite") ? "You are inside Site-01" : "Classified location", SX + SW / 2, y + 52, 0xFF8A7AA8);
        y += 78;
        boolean in = t().getBoolean("inSite");
        pill(g, x, y, w, 18, in ? "Return to the surface" : "Travel to Site-01", 0xFF7A35C4, true, () -> send(in ? "dir:return" : "dir:site01", 0));
        y += 26;
        return y + wrap(g, "Beyond the Gate waits the Ascension Altar. Offer it SCP-500.", x, y, w, 0xFF8A7AA8);
    }

    // ================================================================ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int lx = (int) ((mouseX - ox) / scale), ly = (int) ((mouseY - oy) / scale);
        if (button == 0) {
            for (int i = hits.size() - 1; i >= 0; i--) {
                Hit h = hits.get(i);
                if (lx >= h.x() && lx < h.x() + h.w() && ly >= h.y() && ly < h.y() + h.h()) {
                    click();
                    h.action().run();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = Mth.clamp(scroll - (int) (scrollY * 16), 0, Math.max(0, contentHeight - viewportHeight));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 259 && view != View.HOME) { // backspace = back
            open(view == View.THREAD ? View.MESSAGES : View.HOME);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
