package com.breachprotocol.client;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.network.ClientCareerState;
import com.breachprotocol.network.PhoneActionPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;
import java.util.function.IntPredicate;

/**
 * The Foundation Phone. A home screen with only the apps your clearance allows, three hardware
 * buttons (Back, Home, Recents), and full-screen apps. Clearance comes from SCP Unleashed keycards
 * (carried or fused) and your career rank. Drawn immediate-mode in phone-local pixels and scaled.
 */
public class PhoneScreen extends Screen {
    private static final int PW = 176, PH = 344, BZ = 7;
    private static final int SX = BZ, SY = BZ, SW = PW - BZ * 2, SH = PH - BZ * 2;
    private static final int STATUS = 14, NAV = 22, HEADER = 22;
    private static final int TEAL = 0xFF3FD0FF, AMBER = 0xFFF2A93B, RED = 0xFFE5484D, BG = 0xFF0B1118, CARD = 0xFF141B24, MUTED = 0xFF8FA3B5;

    private static final ResourceLocation WALLPAPER = tex("wallpaper");
    private static ResourceLocation tex(String n) { return BreachProtocol.id("textures/gui/phone/" + n + ".png"); }

    private enum View { HOME, RECENTS, MESSAGES, THREAD, MISSIONS, CALL, CAMERA, GALLERY, PHOTO, ARCHIVE, FILE, CONTAINMENT, PERSONNEL, SETTINGS, COMMAND, NETWORK, SITE }

    private record App(String icon, String label, View view, int accent, IntPredicate allowed) {}

    private static final App PHONE = new App("app_phone", "Phone", View.CALL, 0xFF1F8F4C, c -> true);
    private static final App MESSAGES = new App("app_messages", "Messages", View.MESSAGES, 0xFF22994A, c -> true);
    private static final App MISSIONS = new App("app_missions", "Missions", View.MISSIONS, 0xFFC46A1C, c -> true);
    private static final App CAMERA = new App("app_camera", "Camera", View.CAMERA, 0xFF2C3440, c -> true);
    private static final App GALLERY = new App("app_gallery", "Gallery", View.GALLERY, 0xFF6B3FA0, c -> true);
    private static final App ARCHIVE = new App("app_archive", "Archive", View.ARCHIVE, 0xFF8A6A1E, c -> c >= 1);
    private static final App CONTAIN = new App("app_containment", "Containment", View.CONTAINMENT, 0xFF156E8F, c -> c >= 2);
    private static final App PERSONNEL = new App("app_personnel", "Personnel", View.PERSONNEL, 0xFF2E56A6, c -> true);
    private static final App COMMAND = new App("app_command", "Command", View.COMMAND, 0xFF9B2730, c -> c >= 4);
    private static final App NETWORK = new App("app_network", "Network", View.NETWORK, 0xFF1A8A80, c -> c >= 5);
    private static final App SITE = new App("app_site01", "Site-01", View.SITE, 0xFF7A35C4, c -> false);
    private static final App SETTINGS = new App("app_settings", "Settings", View.SETTINGS, 0xFF4A525E, c -> true);
    private static final List<App> GRID = List.of(CAMERA, GALLERY, ARCHIVE, CONTAIN, PERSONNEL, COMMAND, NETWORK, SITE, SETTINGS);
    private static final List<App> DOCK = List.of(PHONE, MESSAGES, MISSIONS, CAMERA);

    private static final Deque<App> RECENT = new ArrayDeque<>();
    private final Deque<View> back = new ArrayDeque<>();
    private View view = View.HOME;
    private String thread = "";
    private int photoId = -1;
    private String fileId = "";
    private int commandTab;
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
    private static int clr() { return t().getInt("clr"); }
    private static int rank() { return t().getInt("rank"); }

    private static boolean allowed(App a) {
        if (a == SITE) return rank() >= 7;
        return a.allowed().test(clr());
    }

    private static void send(String action, int arg) {
        PacketDistributor.sendToServer(new PhoneActionPayload(action, arg));
    }

    private void click() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.6F, 0.25F));
    }

    private void go(View v) {
        if (v == view) return;
        back.push(view);
        view = v;
        scroll = 0;
        App a = appFor(v);
        if (a != null) {
            RECENT.remove(a);
            RECENT.push(a);
            while (RECENT.size() > 6) RECENT.removeLast();
        }
        if (v == View.MESSAGES || v == View.THREAD) send("read", 0);
        if (v == View.THREAD) scrollToBottom = true;
    }

    private void goBack() {
        if (back.isEmpty()) {
            if (view == View.HOME) onClose();
            else view = View.HOME;
        } else {
            view = back.pop();
        }
        scroll = 0;
    }

    private void goHome() {
        back.clear();
        view = View.HOME;
        scroll = 0;
    }

    // ================================================================ drawing helpers

    private static void rect(GuiGraphics g, int x, int y, int w, int h, int c) { g.fill(x, y, x + w, y + h, c); }

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

    private static void ring(GuiGraphics g, int cx, int cy, int r, int c) {
        circle(g, cx, cy, r, c);
        circle(g, cx, cy, r - 1, 0xFF06090D);
    }

    private void text(GuiGraphics g, String s, int x, int y, int c) { g.drawString(font, s, x, y, c, false); }
    private void textC(GuiGraphics g, String s, int cx, int y, int c) { g.drawString(font, s, cx - font.width(s) / 2, y, c, false); }

    private void big(GuiGraphics g, String s, int cx, int y, float k, int c) {
        g.pose().pushPose();
        g.pose().translate(cx, y, 0);
        g.pose().scale(k, k, 1);
        g.drawString(font, s, -font.width(s) / 2, 0, c, false);
        g.pose().popPose();
    }

    private String ellipsize(String s, int w) {
        if (font.width(s) <= w) return s;
        while (s.length() > 1 && font.width(s + "...") > w) s = s.substring(0, s.length() - 1);
        return s + "...";
    }

    private int wrap(GuiGraphics g, String s, int x, int y, int w, int c) {
        List<FormattedCharSequence> lines = font.split(Component.literal(s), w);
        for (int i = 0; i < lines.size(); i++) g.drawString(font, lines.get(i), x, y + i * 10, c, false);
        return lines.size() * 10;
    }

    private int wrapHeight(String s, int w) { return font.split(Component.literal(s), w).size() * 10; }

    private void hit(int x, int y, int w, int h, Runnable r) { hits.add(new Hit(x, y, w, h, r)); }
    private boolean over(int x, int y, int w, int h) { return mouseLX >= x && mouseLX < x + w && mouseLY >= y && mouseLY < y + h; }

    private void pill(GuiGraphics g, int x, int y, int w, int h, String label, int color, int textColor, boolean enabled, Runnable action) {
        boolean hov = enabled && over(x, y, w, h);
        rrect(g, x, y, w, h, h / 2, enabled ? (hov ? brighten(color) : color) : 0xFF2A3038);
        textC(g, ellipsize(label, w - 8), x + w / 2, y + (h - 8) / 2, enabled ? textColor : 0xFF7A828C);
        if (enabled) hit(x, y, w, h, action);
    }

    private void pill(GuiGraphics g, int x, int y, int w, int h, String label, int color, boolean enabled, Runnable action) {
        pill(g, x, y, w, h, label, color, 0xFFFFFFFF, enabled, action);
    }

    private static int brighten(int c) {
        int a = c >>> 24, r = Math.min(255, ((c >> 16) & 255) + 28), gr = Math.min(255, ((c >> 8) & 255) + 28), b = Math.min(255, (c & 255) + 28);
        return a << 24 | r << 16 | gr << 8 | b;
    }

    private void card(GuiGraphics g, int x, int y, int w, int h) { rrect(g, x, y, w, h, 8, CARD); }

    private void label(GuiGraphics g, String s, int x, int y) { text(g, s.toUpperCase(Locale.ROOT), x, y, MUTED); }

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
        String lbl = sender.startsWith("O5-") ? sender.substring(3, Math.max(4, sender.indexOf(' ') > 0 ? sender.indexOf(' ') : 4)) : sender.isEmpty() ? "?" : sender.substring(0, 1);
        textC(g, lbl, cx, cy - 4, 0xFFFFFFFF);
    }

    private static String nick(String sender) {
        int q = sender.indexOf('"');
        return q >= 0 ? sender.substring(q + 1, sender.lastIndexOf('"') > q ? sender.lastIndexOf('"') : sender.length()) : sender;
    }

    private static String seatOf(String sender) {
        int sp = sender.indexOf(' ');
        return sp > 0 ? sender.substring(0, sp) : sender;
    }

    private static int unread() {
        int n = 0;
        for (Tag e : t().getList("messages", Tag.TAG_COMPOUND)) if (!((CompoundTag) e).getBoolean("r")) n++;
        return n;
    }

    private static String clock() {
        long day = (t().getLong("now") + 6000) % 24000;
        return String.format("%02d:%02d", day / 1000, (day % 1000) * 60 / 1000);
    }

    private static String clrLabel(int c) {
        if (c < 6) return "CLR " + c;
        return t().getBoolean("ascended") ? "\u03A9" : rank() >= 7 ? "ADMIN" : rank() >= 6 ? "O5" : "OMNI";
    }

    private static int clrColor(int c) {
        return c >= 6 ? 0xFFC58BFF : c >= 4 ? AMBER : TEAL;
    }

    private int badge(App a) {
        if (a == MESSAGES) return unread();
        if (a == MISSIONS) return t().getList("missions", Tag.TAG_COMPOUND).size();
        if (a == PHONE) return t().contains("callText") ? 1 : 0;
        int n = 0;
        if (a == GALLERY) for (Tag e : t().getList("photos", Tag.TAG_COMPOUND)) if (((CompoundTag) e).getBoolean("hazard")) n++;
        return n;
    }

    private App appFor(View v) {
        return switch (v) {
            case MESSAGES, THREAD -> MESSAGES;
            case MISSIONS -> MISSIONS;
            case CALL -> PHONE;
            case CAMERA -> CAMERA;
            case GALLERY, PHOTO -> GALLERY;
            case ARCHIVE, FILE -> ARCHIVE;
            case CONTAINMENT -> CONTAIN;
            case PERSONNEL -> PERSONNEL;
            case SETTINGS -> SETTINGS;
            case COMMAND -> COMMAND;
            case NETWORK -> NETWORK;
            case SITE -> SITE;
            default -> null;
        };
    }

    // ================================================================ render

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (view != View.CAMERA) super.renderBackground(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        scale = Math.min(1.3F, (height - 12) / (float) PH);
        ox = (int) ((width - PW * scale) / 2);
        oy = (int) ((height - PH * scale) / 2);
        mouseLX = (int) ((mouseX - ox) / scale);
        mouseLY = (int) ((mouseY - oy) / scale);
        hits.clear();
        hover = "";
        // apps you lose clearance for close themselves
        App cur = appFor(view);
        if (cur != null && !allowed(cur)) goHome();

        g.pose().pushPose();
        g.pose().translate(ox, oy, 0);
        g.pose().scale(scale, scale, 1);

        rrect(g, -2, -2, PW + 4, PH + 4, 26, 0xFF2A2E37);
        rrect(g, 0, 0, PW, PH, 24, 0xFF1A1D24);
        rect(g, -3, 74, 2, 24, 0xFF2A2E37);
        rect(g, PW + 1, 90, 2, 34, 0xFF2A2E37);
        if (view != View.CAMERA) rrect(g, SX, SY, SW, SH, 18, BG);

        scissor(g, SX, SY, SW, SH);
        switch (view) {
            case HOME -> drawHome(g);
            case RECENTS -> drawRecents(g);
            case CAMERA -> drawCamera(g);
            default -> drawApp(g);
        }
        drawStatusBar(g);
        drawNavBar(g);
        g.disableScissor();

        if (!hover.isEmpty()) {
            int hh = wrapHeight(hover, SW - 16) + 8;
            rrect(g, SX + 4, SY + SH - NAV - hh - 4, SW - 8, hh, 6, 0xF010141A);
            wrap(g, hover, SX + 8, SY + SH - NAV - hh, SW - 16, 0xFFCFE6EE);
        }
        g.pose().popPose();
    }

    private void scissor(GuiGraphics g, int x, int y, int w, int h) {
        g.enableScissor(ox + (int) (x * scale), oy + (int) (y * scale), ox + (int) ((x + w) * scale), oy + (int) ((y + h) * scale));
    }

    private void drawStatusBar(GuiGraphics g) {
        if (view != View.HOME && view != View.CAMERA && view != View.CALL && view != View.RECENTS) rect(g, SX, SY, SW, STATUS, 0xFF0E131A);
        text(g, clock(), SX + 12, SY + 3, 0xFFFFFFFF);
        String c = clrLabel(clr());
        int cw = font.width(c) + 6, cx = SX + SW - 36 - cw;
        rrect(g, cx, SY + 2, cw, 10, 3, clrColor(clr()));
        rrect(g, cx + 1, SY + 3, cw - 2, 8, 2, 0xFF0B1118);
        text(g, c, cx + 3, SY + 3, clrColor(clr()));
        int x = SX + SW - 30;
        rrect(g, x, SY + 4, 14, 7, 2, 0xFFC8D2DC);
        rect(g, x + 1, SY + 5, 10, 5, 0xFF5BE36F);
        rect(g, x + 14, SY + 6, 1, 3, 0xFFC8D2DC);
        for (int i = 0; i < 3; i++) rect(g, x + 18 + i * 3, SY + 9 - i * 2, 2, 2 + i * 2, 0xFFFFFFFF);
    }

    /** The three hardware buttons: Back, Home, Recents. */
    private void drawNavBar(GuiGraphics g) {
        int y = SY + SH - NAV;
        rect(g, SX, y, SW, NAV, 0xFF06090D);
        rect(g, SX, y, SW, 1, 0xFF141B24);
        int third = SW / 3, cy = y + NAV / 2;
        // back
        int bx = SX + third / 2;
        boolean hb = over(SX, y, third, NAV);
        int cb = hb ? 0xFFFFFFFF : 0xFFC8D2DC;
        for (int i = 0; i < 5; i++) {
            rect(g, bx + i - 2, cy - i, 2, 1, cb);
            rect(g, bx + i - 2, cy + i, 2, 1, cb);
        }
        hit(SX, y, third, NAV, this::goBack);
        // home
        int hx = SX + third + third / 2;
        ring(g, hx, cy, 6, over(SX + third, y, third, NAV) ? 0xFFFFFFFF : 0xFFE8EEF4);
        hit(SX + third, y, third, NAV, this::goHome);
        // recents
        int rx = SX + 2 * third + third / 2;
        int cr = over(SX + 2 * third, y, third, NAV) ? 0xFFFFFFFF : 0xFFC8D2DC;
        rect(g, rx - 5, cy - 5, 10, 1, cr); rect(g, rx - 5, cy + 4, 10, 1, cr);
        rect(g, rx - 5, cy - 5, 1, 10, cr); rect(g, rx + 4, cy - 5, 1, 10, cr);
        hit(SX + 2 * third, y, third, NAV, () -> { if (view == View.RECENTS) goBack(); else go(View.RECENTS); });
    }

    // ---------------------------------------------------------------- home

    private void drawHome(GuiGraphics g) {
        g.blit(WALLPAPER, SX, SY, SW, SH, 0, 0, 156, 300, 156, 300);
        big(g, clock(), SX + SW / 2, SY + 24, 3.2F, 0xFFFFFFFF);
        long day = t().getLong("now") / 24000 + 1;
        small(g, "SITE DAY " + day, SX + SW / 2, SY + 52, 0xFF9FB1C2);
        small(g, ellipsize(t().getString("title"), (int) ((SW - 16) / 0.75F)), SX + SW / 2, SY + 60, 0xFFD6E0EA);

        int ny = SY + 72;
        if (t().contains("callText")) {
            rrect(g, SX + 8, ny, SW - 16, 26, 9, over(SX + 8, ny, SW - 16, 26) ? 0xEE1F6E3A : 0xDD17502C);
            text(g, "\u260E Incoming call", SX + 14, ny + 4, 0xFFB8FFCC);
            text(g, ellipsize(t().getString("callFrom"), SW - 30), SX + 14, ny + 14, 0xFFFFFFFF);
            hit(SX + 8, ny, SW - 16, 26, () -> go(View.CALL));
        } else {
            ListTag msgs = t().getList("messages", Tag.TAG_COMPOUND);
            if (!msgs.isEmpty()) {
                CompoundTag m = msgs.getCompound(0);
                rrect(g, SX + 8, ny, SW - 16, 26, 9, over(SX + 8, ny, SW - 16, 26) ? 0xEE2A3340 : 0xD0141A22);
                text(g, ellipsize(seatOf(m.getString("s")) + " " + nick(m.getString("s")), SW - 30), SX + 14, ny + 4, m.getBoolean("r") ? 0xFF9FB0C0 : 0xFF7FE0FF);
                text(g, ellipsize(m.getString("b"), SW - 30), SX + 14, ny + 14, 0xFFE0E6EC);
                final String sender = m.getString("s");
                hit(SX + 8, ny, SW - 16, 26, () -> { thread = sender; go(View.THREAD); });
            }
        }

        List<App> apps = new ArrayList<>();
        for (App a : GRID) if (allowed(a) && !DOCK.contains(a)) apps.add(a);
        int cols = 4, cell = (SW - 8) / cols;
        App hovered = null;
        int hx = 0, hy = 0;
        for (int i = 0; i < apps.size(); i++) {
            int x = SX + 4 + (i % cols) * cell + (cell - 28) / 2, y = SY + 106 + (i / cols) * 44;
            if (drawIcon(g, apps.get(i), x, y, cell)) { hovered = apps.get(i); hx = x; hy = y; }
        }
        small(g, "FOUNDATION SECURE NETWORK", SX + SW / 2, SY + SH - NAV - 58, 0x99C8D2DC);
        int dy = SY + SH - NAV - 44;
        rrect(g, SX + 6, dy, SW - 12, 40, 14, 0x55FFFFFF);
        int dc = (SW - 12) / DOCK.size();
        for (int i = 0; i < DOCK.size(); i++) {
            int x = SX + 6 + i * dc + (dc - 28) / 2;
            if (drawIcon(g, DOCK.get(i), x, dy + 6, 0)) { hovered = DOCK.get(i); hx = x; hy = dy + 6; }
        }
        if (hovered != null) {
            // name bubble above the icon you're pointing at
            String n = hovered.label();
            int bw = font.width(n) + 10, bx = Mth.clamp(hx + 14 - bw / 2, SX + 3, SX + SW - 3 - bw), by = hy - 17;
            rrect(g, bx, by, bw, 13, 6, 0xF0F2F5F8);
            rect(g, hx + 12, by + 13, 4, 1, 0xF0F2F5F8);
            rect(g, hx + 13, by + 14, 2, 1, 0xF0F2F5F8);
            text(g, n, bx + 5, by + 3, 0xFF10161D);
        }
    }

    /** Draws an app icon; returns true while the cursor is on it. {@code cell} > 0 also draws a small label. */
    private boolean drawIcon(GuiGraphics g, App a, int x, int y, int cell) {
        boolean hov = over(x - 2, y - 2, 32, 32);
        if (hov) rrect(g, x - 3, y - 3, 34, 34, 10, 0x40FFFFFF);
        g.blit(tex(a.icon()), x, y, 28, 28, 0, 0, 32, 32, 32, 32);
        int b = badge(a);
        if (b > 0) {
            circle(g, x + 27, y + 2, 5, RED);
            textC(g, b > 9 ? "9+" : String.valueOf(b), x + 27, y - 2, 0xFFFFFFFF);
        }
        if (cell > 0) {
            String lbl = ellipsize(a.label(), (int) ((cell - 2) / 0.75F));
            small(g, lbl, x + 14, y + 31, hov ? 0xFFFFFFFF : 0xFFE6ECF2);
        }
        hit(x - 4, y - 4, 36, cell > 0 ? 42 : 36, () -> go(a.view()));
        return hov;
    }

    /** Small (3/4 size) centred text with a soft shadow, for labels on the wallpaper. */
    private void small(GuiGraphics g, String s, int cx, int y, int c) {
        g.pose().pushPose();
        g.pose().translate(cx, y, 0);
        g.pose().scale(0.75F, 0.75F, 1);
        g.drawString(font, s, -font.width(s) / 2, 0, c, true);
        g.pose().popPose();
    }

    private void drawRecents(GuiGraphics g) {
        rect(g, SX, SY, SW, SH, 0xF0070A0E);
        textC(g, "Recent apps", SX + SW / 2, SY + 22, MUTED);
        int y = SY + 38;
        List<App> list = new ArrayList<>();
        for (App a : RECENT) if (allowed(a)) list.add(a);
        if (list.isEmpty()) textC(g, "Nothing open yet.", SX + SW / 2, y + 40, 0xFF6F7C88);
        for (App a : list) {
            int x = SX + 10, w = SW - 20, h = 34;
            if (y + h > SY + SH - NAV - 4) break;
            rrect(g, x, y, w, h, 10, over(x, y, w, h) ? 0xFF1E2733 : CARD);
            rect(g, x, y, 4, h, a.accent());
            g.blit(tex(a.icon()), x + 8, y + 5, 24, 24, 0, 0, 32, 32, 32, 32);
            text(g, a.label(), x + 38, y + 13, 0xFFFFFFFF);
            hit(x, y, w, h, () -> go(a.view()));
            y += h + 6;
        }
    }

    // ---------------------------------------------------------------- app chrome

    private void drawApp(GuiGraphics g) {
        App app = appFor(view);
        int top = SY + STATUS;
        if (view != View.CALL && app != null) {
            rect(g, SX, top, SW, HEADER, app.accent());
            String title = switch (view) {
                case THREAD -> nick(thread);
                case FILE -> fileTitle();
                case PHOTO -> "Photo";
                default -> app.label();
            };
            text(g, ellipsize(title, SW - 24), SX + 10, top + 7, 0xFFFFFFFF);
            top += HEADER;
        }
        final int bottom = SY + SH - NAV, vtop = top;
        viewportHeight = bottom - top;
        scissor(g, SX, top, SW, viewportHeight);
        int y0 = top + 6 - scroll;
        int end;
        try {
            end = drawView(g, y0, top);
        } catch (Throwable t) {
            com.breachprotocol.BreachProtocol.LOGGER.error("Phone app {} failed to draw", view, t);
            empty(g, y0, "App crashed", t.getClass().getSimpleName() + ". Press Home.");
            end = y0 + 80;
        }
        contentHeight = end - y0 + 8;
        if (scrollToBottom) { scroll = Math.max(0, contentHeight - viewportHeight); scrollToBottom = false; }
        scroll = Mth.clamp(scroll, 0, Math.max(0, contentHeight - viewportHeight));
        hits.removeIf(h -> h.y() + h.h() < vtop || h.y() > bottom);
        g.disableScissor();
        if (contentHeight > viewportHeight) {
            int thumb = Math.max(14, viewportHeight * viewportHeight / contentHeight);
            int pos = (int) ((viewportHeight - thumb) * (scroll / (float) Math.max(1, contentHeight - viewportHeight)));
            rrect(g, SX + SW - 4, top + pos, 2, thumb, 1, 0x66FFFFFF);
        }
    }

    private int drawView(GuiGraphics g, int y0, int top) {
        return switch (view) {
            case MESSAGES -> drawMessages(g, y0);
            case THREAD -> drawThread(g, y0);
            case MISSIONS -> drawMissions(g, y0);
            case CALL -> drawCall(g, top);
            case GALLERY -> drawGallery(g, y0);
            case PHOTO -> drawPhoto(g, y0);
            case ARCHIVE -> drawArchive(g, y0);
            case FILE -> drawFile(g, y0);
            case CONTAINMENT -> drawContainment(g, y0);
            case PERSONNEL -> drawPersonnel(g, y0);
            case SETTINGS -> drawSettings(g, y0);
            case COMMAND -> drawCommand(g, y0);
            case NETWORK -> drawNetwork(g, y0);
            default -> drawSite(g, y0);
        };
    }

    private void empty(GuiGraphics g, int y, String title, String sub) {
        textC(g, title, SX + SW / 2, y + 30, 0xFFE0E6EC);
        List<FormattedCharSequence> lines = font.split(Component.literal(sub), SW - 24);
        for (int i = 0; i < lines.size(); i++) g.drawString(font, lines.get(i), SX + SW / 2 - font.width(lines.get(i)) / 2, y + 44 + i * 10, 0xFF8090A0, false);
    }

    private String ago(long time) {
        long min = Math.max(0, (t().getLong("now") - time) / 1200);
        return min == 0 ? "now" : min < 60 ? min + "m" : (min / 60) + "h";
    }

    // ---------------------------------------------------------------- Messages

    private record ThreadInfo(String sender, String last, long time, int unread) {}

    private int drawMessages(GuiGraphics g, int y) {
        LinkedHashMap<String, ThreadInfo> map = new LinkedHashMap<>();
        for (Tag e : t().getList("messages", Tag.TAG_COMPOUND)) {
            CompoundTag m = (CompoundTag) e;
            String s = m.getString("s");
            ThreadInfo prev = map.get(s);
            int un = (prev == null ? 0 : prev.unread()) + (m.getBoolean("r") ? 0 : 1);
            map.put(s, prev == null ? new ThreadInfo(s, m.getString("b"), m.getLong("t"), un) : new ThreadInfo(s, prev.last(), prev.time(), un));
        }
        if (map.isEmpty()) { empty(g, y, "No messages", "The Council will be in touch."); return y + 80; }
        for (ThreadInfo th : map.values()) {
            int h = 32;
            if (over(SX, y, SW, h)) rect(g, SX, y, SW, h, 0xFF19202A);
            avatar(g, th.sender(), SX + 18, y + 16, 11);
            text(g, ellipsize(nick(th.sender()), SW - 80), SX + 34, y + 5, 0xFFFFFFFF);
            text(g, ago(th.time()), SX + SW - 8 - font.width(ago(th.time())), y + 5, 0xFF7A8794);
            text(g, ellipsize(th.last(), SW - 56), SX + 34, y + 17, th.unread() > 0 ? 0xFFDDE7F0 : 0xFF8794A0);
            if (th.unread() > 0) circle(g, SX + SW - 12, y + 21, 3, 0xFF2F9BFF);
            rect(g, SX + 34, y + h - 1, SW - 34, 1, 0xFF1C232C);
            hit(SX, y, SW, h, () -> { thread = th.sender(); go(View.THREAD); });
            y += h;
        }
        return y;
    }

    private int drawThread(GuiGraphics g, int y) {
        List<CompoundTag> mine = new ArrayList<>();
        for (Tag e : t().getList("messages", Tag.TAG_COMPOUND)) if (((CompoundTag) e).getString("s").equals(thread)) mine.add((CompoundTag) e);
        Collections.reverse(mine);
        textC(g, ellipsize(thread, SW - 12), SX + SW / 2, y, 0xFF6F7C88);
        y += 14;
        int maxW = SW - 44;
        for (CompoundTag m : mine) {
            String body = m.getString("b");
            int tw = Math.min(maxW, font.width(body));
            int h = wrapHeight(body, maxW) + 8;
            avatar(g, thread, SX + 12, y + h - 8, 7);
            rrect(g, SX + 22, y, tw + 10, h, 7, 0xFF232A35);
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
                    : rank() >= 7 ? "The Administrator assigns, not receives. Answer the Council's calls." : "Stand by. Orders arrive every few minutes.");
            return y + 90;
        }
        int toNext = t().getInt("toNext");
        if (toNext > 0 && !t().getBoolean("ascended")) {
            small(g, toNext == 1 ? "ONE MORE ASSIGNMENT UNTIL YOUR REVIEW" : toNext + " MORE ASSIGNMENTS UNTIL YOUR REVIEW", SX + SW / 2, y, MUTED);
            y += 10;
        }
        for (int i = 0; i < ms.size(); i++) {
            CompoundTag m = ms.getCompound(i);
            String type = m.getString("type");
            int chip = switch (type) {
                case "RECOVER" -> RED; case "TERMINATE" -> 0xFF8E3BC4; case "CARE" -> 0xFFE58A2A; case "SUPPLY" -> 0xFF2F8FD8; default -> 0xFFB04FE0;
            };
            String desc = m.getString("desc");
            boolean trial = type.equals("FOUNDERS_TRIAL");
            int x = SX + 6, w = SW - 12, pad = 7, iw = w - pad * 2;
            // measure first so nothing overlaps
            int descH = wrapHeight(desc, iw);
            List<String> meta = new ArrayList<>();
            if (m.getBoolean("hasPos")) meta.add("\u2316 " + m.getInt("x") + ", " + m.getInt("z"));
            long exp = m.getLong("expires");
            if (exp < Long.MAX_VALUE / 2) { long sec = Math.max(0, (exp - now) / 20); meta.add("\u23F1 " + sec / 60 + ":" + String.format("%02d", sec % 60)); }
            int h = pad + 16 + descH + 6 + 10 + (meta.isEmpty() ? 0 : 12) + (trial ? 0 : 18) + pad - 2;
            card(g, x, y, w, h);
            rect(g, x, y + 6, 2, h - 12, chip);
            int cy = y + pad;
            String lbl = type.replace('_', ' ');
            rrect(g, x + pad, cy, font.width(lbl) + 8, 11, 5, chip);
            text(g, lbl, x + pad + 4, cy + 2, 0xFFFFFFFF);
            String giver = "from O5-" + m.getInt("giver");
            small(g, giver, x + w - pad - (int) (font.width(giver) * 0.375F), cy + 3, 0xFF7A8794);
            cy += 16;
            cy += wrap(g, desc, x + pad, cy, iw, 0xFFE2E8EE) + 6;
            float pct = Mth.clamp(m.getInt("progress") / (float) Math.max(1, m.getInt("target")), 0, 1);
            String prog = m.getInt("progress") + "/" + m.getInt("target");
            int bw = iw - font.width(prog) - 6;
            rrect(g, x + pad, cy + 1, bw, 4, 2, 0xFF2A323D);
            rrect(g, x + pad, cy + 1, Math.max(4, (int) (bw * pct)), 4, 2, chip);
            text(g, prog, x + pad + iw - font.width(prog), cy - 1, 0xFFC8D2DC);
            cy += 10;
            if (!meta.isEmpty()) {
                text(g, ellipsize(String.join("   ", meta), iw), x + pad, cy, 0xFF94A3B0);
                cy += 12;
            }
            final int idx = i;
            if (type.equals("SUPPLY")) {
                int half = (iw - 4) / 2;
                pill(g, x + pad, cy, half, 14, "Submit", 0xFF2E9E57, true, () -> send("submit", idx));
                pill(g, x + pad + half + 4, cy, half, 14, "Abandon", 0xFF4A2A30, true, () -> send("abandon", idx));
            } else if (!trial) {
                pill(g, x + pad, cy, iw, 14, "Abandon", 0xFF4A2A30, true, () -> send("abandon", idx));
            }
            y += h + 6;
        }
        return y;
    }

    // ---------------------------------------------------------------- Phone (calls)

    private int drawCall(GuiGraphics g, int top) {
        CompoundTag t = t();
        int cx = SX + SW / 2;
        rect(g, SX, top, SW, SH, 0xFF0A120E);
        if (!t.contains("callText")) {
            int y = top + 34;
            circle(g, cx, y + 20, 20, 0xFF1C3A28);
            textC(g, "☎", cx, y + 16, 0xFF7CFF9A);
            textC(g, "No incoming calls", cx, y + 52, 0xFFE0E6EC);
            List<FormattedCharSequence> lines = font.split(Component.literal(rank() >= 7
                    ? "The Council calls you for orders every few minutes."
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
            circle(g, cx, y + 22, 18 + (int) (p * 22), ((int) ((1 - p) * 70)) << 24 | 0x3AE07A);
        }
        circle(g, cx, y + 22, 18, seatColor(from));
        textC(g, seatOf(from).replace("O5-", ""), cx, y + 18, 0xFFFFFFFF);
        textC(g, ellipsize(nick(from), SW - 16), cx, y + 50, 0xFFFFFFFF);
        textC(g, seatOf(from) + "  ·  secure line", cx, y + 61, 0xFF8AE0A8);
        y += 76;
        String body = t.getString("callText");
        int bh = wrapHeight(body, SW - 24) + 10;
        rrect(g, SX + 6, y, SW - 12, bh, 8, 0xFF15231B);
        wrap(g, body, SX + 12, y + 5, SW - 24, 0xFFE8F2EC);
        y += bh + 8;
        ListTag opts = t.getList("callOptions", Tag.TAG_STRING);
        for (int i = 0; i < opts.size(); i++) {
            int color = i == 0 ? 0xFF2EA65A : i == opts.size() - 1 ? 0xFFC0383F : 0xFF3C6FB8;
            final int idx = i;
            pill(g, SX + 10, y, SW - 20, 16, opts.getString(i), color, true, () -> { send("answer", idx); goHome(); });
            y += 20;
        }
        return y;
    }

    // ---------------------------------------------------------------- Camera

    private void drawCamera(GuiGraphics g) {
        // The screen is see-through: the square viewfinder shows the world behind the phone, and the
        // shutter captures exactly that square.
        int vf = SY + STATUS, bottom = SY + SH - NAV - 58;
        rect(g, SX, SY, SW, STATUS, 0xCC06090D);
        int side = SW - 16, fx = SX + 8, fy = vf + Math.max(4, (bottom - vf - side) / 2);
        rect(g, SX, vf, SW, fy - vf, 0xB006090D);
        rect(g, SX, fy + side, SW, bottom - fy - side, 0xB006090D);
        rect(g, SX, fy, 8, side, 0xB006090D);
        rect(g, fx + side, fy, SX + SW - fx - side, side, 0xB006090D);
        int c = 0xDDFFFFFF, k = 12;
        rect(g, fx, fy, k, 1, c); rect(g, fx, fy, 1, k, c);
        rect(g, fx + side - k, fy, k, 1, c); rect(g, fx + side - 1, fy, 1, k, c);
        rect(g, fx, fy + side - 1, k, 1, c); rect(g, fx, fy + side - k, 1, k, c);
        rect(g, fx + side - k, fy + side - 1, k, 1, c); rect(g, fx + side - 1, fy + side - k, 1, k, c);
        for (int i = 1; i < 3; i++) {
            rect(g, fx + side * i / 3, fy, 1, side, 0x22FFFFFF);
            rect(g, fx, fy + side * i / 3, side, 1, 0x22FFFFFF);
        }
        String face = faceInView();
        if (face != null) {
            rrect(g, fx + 4, fy + 4, side - 8, 28, 8, 0xE03C0C10);
            text(g, "\u26A0 FACE IN FRAME \u00B7 " + face, fx + 9, fy + 8, 0xFFFF8A8F);
            small(g, "Taking this photo means seeing it.", fx + side / 2, fy + 20, 0xFFF3D6D8);
        }
        if (System.currentTimeMillis() < PhotoCache.flashUntil) rect(g, fx, fy, side, side, 0xCCFFFFFF);
        small(g, "PHOTO", SX + SW / 2, fy + side + 6, 0xFFF2C14E);
        rect(g, SX, bottom, SW, 58, 0xF406090D);
        int sy = bottom + 29, mx = SX + SW / 2;
        // last photo thumbnail
        ListTag photos = t().getList("photos", Tag.TAG_COMPOUND);
        int tx = SX + 18, ty = sy - 12;
        rrect(g, tx - 1, ty - 1, 26, 26, 6, 0xFF3A4450);
        if (!photos.isEmpty()) drawPicture(g, photos.getCompound(0), tx, ty, 24, true);
        else rrect(g, tx, ty, 24, 24, 5, 0xFF1E242C);
        hit(tx, ty, 24, 24, () -> go(View.GALLERY));
        boolean hs = over(mx - 18, sy - 18, 36, 36);
        circle(g, mx, sy, 18, 0xFFE8EEF4);
        circle(g, mx, sy, 15, 0xFF06090D);
        circle(g, mx, sy, hs ? 14 : 13, 0xFFE8EEF4);
        final int cfx = fx, cfy = fy, cside = side;
        hit(mx - 18, sy - 18, 36, 36, () -> {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, 1.8F));
            double gs = Minecraft.getInstance().getWindow().getGuiScale();
            PhotoCache.shoot((int) ((ox + cfx * scale) * gs), (int) ((oy + cfy * scale) * gs), (int) (cside * scale * gs));
        });
    }

    /** Client-side warning: is SCP-096's face (SCP Unleashed's Scp096Sight.faceOf) in frame? */
    private static String faceInView() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return null;
        Vec3 eye = mc.player.getEyePosition(), look = mc.player.getViewVector(1.0F);
        for (Entity e : mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(40), com.breachprotocol.compat.UnleashedApi::isShyGuy)) {
            Vec3 face = com.breachprotocol.compat.UnleashedApi.faceOf(e), facing = com.breachprotocol.compat.UnleashedApi.facingOf(e);
            if (face == null || facing == null) continue;
            if (look.dot(face.subtract(eye).normalize()) < 0.93) continue;
            if (facing.normalize().dot(eye.subtract(face).normalize()) > 0.3) return e.getType().getDescription().getString();
        }
        return null;
    }

    // ---------------------------------------------------------------- Gallery

    private CompoundTag photo(int id) {
        for (Tag e : t().getList("photos", Tag.TAG_COMPOUND)) if (((CompoundTag) e).getInt("id") == id) return (CompoundTag) e;
        return null;
    }

    private int drawGallery(GuiGraphics g, int y) {
        ListTag ps = t().getList("photos", Tag.TAG_COMPOUND);
        if (ps.isEmpty()) { empty(g, y, "No photos", "Open Camera to document anomalies."); return y + 80; }
        small(g, ps.size() + (ps.size() == 1 ? " PHOTO" : " PHOTOS"), SX + SW / 2, y, MUTED);
        y += 10;
        int cols = 3, gap = 3, tw = (SW - 12 - gap * (cols - 1)) / cols;
        for (int i = 0; i < ps.size(); i++) {
            CompoundTag p = ps.getCompound(i);
            int x = SX + 6 + (i % cols) * (tw + gap), ty = y + (i / cols) * (tw + gap);
            drawPicture(g, p, x, ty, tw, true);
            if (over(x, ty, tw, tw)) rect(g, x, ty, tw, tw, 0x30FFFFFF);
            final int id = p.getInt("id");
            hit(x, ty, tw, tw, () -> { photoId = id; revealed = false; go(View.PHOTO); });
        }
        return y + ((ps.size() + cols - 1) / cols) * (tw + gap);
    }

    private boolean revealed;

    /**
     * A photo's picture. Cognitohazards are covered unless {@code covered} is false. Photos from
     * before pictures existed (or whose picture never reached the server) get a labelled card.
     */
    private void drawPicture(GuiGraphics g, CompoundTag p, int x, int y, int size, boolean covered) {
        boolean hz = p.getBoolean("hazard");
        ResourceLocation tex = PhotoCache.texture(p.getInt("img"));
        if (tex != null && !(hz && covered)) {
            g.blit(tex, x, y, size, size, 0, 0, 1, 1, 1, 1);
        } else if (hz) {
            rect(g, x, y, size, size, 0xFF2A1215);
            for (int q = 3; q < size; q += 6) rect(g, x, y + q, size, 2, 0x30E5484D);
            rect(g, x, y, size, 1, RED); rect(g, x, y + size - 1, size, 1, RED);
            rect(g, x, y, 1, size, RED); rect(g, x + size - 1, y, 1, size, RED);
            if (size >= 30) {
                textC(g, "\u26A0", x + size / 2, y + size / 2 - 9, 0xFFFF8A8F);
                small(g, "HAZARD", x + size / 2, y + size / 2 + 2, 0xFFFF8A8F);
            }
        } else {
            rect(g, x, y, size, size, 0xFF1E2630);
            if (size >= 30) {
                small(g, PhotoCache.missing(p.getInt("img")) ? "NO IMAGE" : "LOADING", x + size / 2, y + size / 2 - 3, 0xFF6F7C88);
            }
        }
    }

    private int drawPhoto(GuiGraphics g, int y) {
        CompoundTag p = photo(photoId);
        if (p == null) { empty(g, y, "Deleted", ""); return y + 60; }
        int x = SX + 6, w = SW - 12;
        boolean hz = p.getBoolean("hazard");
        drawPicture(g, p, x, y, w, hz && !revealed);
        if (hz && !revealed) {
            rrect(g, x + 8, y + w - 44, w - 16, 36, 8, 0xE01A1012);
            text(g, "\u26A0 Cognitohazard", x + 14, y + w - 40, 0xFFFFD7DA);
            wrap(g, "SCP-096's face. Viewing it counts as seeing it.", x + 14, y + w - 29, w - 28, 0xFFF0D9DB);
        }
        y += w + 6;
        y += wrap(g, p.getString("subjects"), x, y, w, 0xFFE2E8EE) + 2;
        small(g, (p.getString("author") + "  \u00B7  " + p.getInt("x") + ", " + p.getInt("z") + "  \u00B7  " + ago(p.getLong("time")) + " ago").toUpperCase(Locale.ROOT), SX + SW / 2, y, MUTED);
        y += 12;
        int half = (w - 4) / 2;
        if (hz) {
            pill(g, x, y, w, 16, "Delete permanently", RED, true, () -> { send("photo_delete", photoId); goBack(); });
            y += 20;
            pill(g, x, y, half, 16, revealed ? "Viewed" : "Open anyway", 0xFF3A1A1E, 0xFFFFB3B8, !revealed, () -> { revealed = true; send("photo_open", photoId); });
            pill(g, x + half + 4, y, half, 16, "Print face down", 0xFF3C6FB8, true, () -> send("photo_print", photoId));
            y += 20;
        } else {
            pill(g, x, y, half, 16, "Send nearby", 0xFF6B3FA0, true, () -> send("photo_share", photoId));
            pill(g, x + half + 4, y, half, 16, "Delete", 0xFF2A3038, true, () -> { send("photo_delete", photoId); goBack(); });
            y += 20;
        }
        if (hz) {
            pill(g, x, y, w, 16, "Send nearby", 0xFF6B3FA0, true, () -> send("photo_share", photoId));
            y += 20;
        }
        return y;
    }

    // ---------------------------------------------------------------- Archive

    private String fileTitle() {
        ArchiveFiles.Entry e = ArchiveFiles.get(fileId);
        return e == null ? "File" : e.designation();
    }

    private String query = "";
    private boolean searchFocused;

    private int drawArchive(GuiGraphics g, int y) {
        List<ArchiveFiles.Entry> all = ArchiveFiles.all();
        int x = SX + 6, w = SW - 12;
        // search bar
        boolean hov = over(x, y, w, 16);
        rrect(g, x, y, w, 16, 8, searchFocused ? 0xFF233040 : hov ? 0xFF1C2430 : CARD);
        if (searchFocused) rrect(g, x, y + 15, w, 1, 0, TEAL);
        text(g, "\u2315", x + 6, y + 4, MUTED);
        boolean caret = searchFocused && (System.currentTimeMillis() / 500) % 2 == 0;
        text(g, query.isEmpty() && !searchFocused ? "Search SCP number or name" : ellipsize(query, w - 30) + (caret ? "_" : ""), x + 16, y + 4, query.isEmpty() && !searchFocused ? 0xFF6F7C88 : 0xFFE8EEF4);
        if (!query.isEmpty()) {
            text(g, "\u00D7", x + w - 11, y + 4, MUTED);
            hit(x + w - 16, y, 16, 16, () -> { query = ""; scroll = 0; });
        }
        hit(x, y, w - (query.isEmpty() ? 0 : 16), 16, () -> searchFocused = true);
        y += 22;
        if (all.isEmpty()) { empty(g, y - 20, "Archive offline", "SCP Unleashed hasn't sent its dossiers yet. Rejoin the world."); return y + 60; }
        String q = query.toLowerCase(Locale.ROOT).trim();
        String qDigits = q.replaceAll("[^0-9]", "");
        int shown = 0;
        for (ArchiveFiles.Entry e : all) {
            if (!q.isEmpty()) {
                String hay = (e.designation() + " " + e.title() + " " + e.objectClass()).toLowerCase(Locale.ROOT);
                boolean numHit = !qDigits.isEmpty() && e.designation().replaceAll("[^0-9]", "").startsWith(qDigits) && q.replaceAll("[0-9scp\\- ]", "").isEmpty();
                if (!hay.contains(q) && !numHit) continue;
            }
            shown++;
            int h = 30;
            boolean ok = ArchiveFiles.clears(clr(), e.required());
            rrect(g, x, y, w, h, 8, over(x, y, w, h) ? 0xFF1C2430 : CARD);
            text(g, ellipsize(e.designation(), w - 70), x + 8, y + 5, ok ? 0xFFFFFFFF : 0xFF8794A0);
            String cls = e.objectClass().isEmpty() ? e.classKey() : e.objectClass();
            int cw = font.width(cls) + 6;
            rrect(g, x + w - 6 - cw, y + 4, cw, 10, 4, ArchiveFiles.classColor(e.classKey()));
            text(g, cls, x + w - 3 - cw, y + 5, 0xFFFFFFFF);
            text(g, ok ? ellipsize(e.title(), w - 16) : "\u26BF Level " + e.required() + " clearance", x + 8, y + 17, ok ? 0xFF9FB1C2 : 0xFFC9A0A5);
            final String id = e.id();
            if (ok) hit(x, y, w, h, () -> { fileId = id; searchFocused = false; go(View.FILE); });
            y += h + 4;
        }
        if (shown == 0) { empty(g, y - 24, "No match", "Nothing in the archive matches \"" + query + "\"."); y += 50; }
        return y;
    }

    private int drawFile(GuiGraphics g, int y) {
        ArchiveFiles.Entry e = ArchiveFiles.get(fileId);
        if (e == null) return y;
        int x = SX + 6, w = SW - 12;
        boolean omni = clr() >= 6;
        // the anomaly itself, slowly turning (SCP-096's image is withheld, as in its own dossier)
        boolean shy = e.id().endsWith(":scp_096");
        if (shy || ArchiveModels.has(e.id())) {
            int mh = 96;
            rrect(g, x, y, w, mh, 10, 0xFF0F151C);
            for (int gy = y + 8; gy < y + mh; gy += 8) rect(g, x + 4, gy, w - 8, 1, 0x0FFFFFFF);
            rect(g, x + 20, y + mh - 14, w - 40, 1, 0x33FFFFFF);
            if (shy) {
                rect(g, x + w / 2 - 30, y + 30, 60, 24, 0xFF000000);
                small(g, "IMAGE WITHHELD", x + w / 2, y + 60, 0xFFFF8A8F);
            } else {
                ArchiveModels.draw(g, e.id(), x, y + 2, w, mh - 14);
            }
            small(g, e.designation().toUpperCase(Locale.ROOT), x + w / 2, y + mh - 10, MUTED);
            y += mh + 6;
        }
        text(g, ellipsize(e.title(), w), x, y, 0xFFE2E8EE);
        y += 12;
        text(g, "Class: " + e.objectClass() + (omni ? "  \u00B7  unredacted" : ""), x, y, ArchiveFiles.classColor(e.classKey()));
        y += 14;
        for (ArchiveFiles.Section sec : e.sections()) {
            if (!sec.heading().isEmpty()) {
                y += wrap(g, sec.heading().toUpperCase(Locale.ROOT), x, y, w, MUTED) + 2;
            }
            for (String para : sec.text().split("\n")) {
                if (para.isBlank()) continue;
                y = drawRuns(g, ArchiveFiles.runs(para), x, y, w, omni) + 4;
            }
            y += 6;
        }
        return y;
    }

    /** Word-wrapped dossier text where redacted words become black bars (or red text for Omni). */
    private int drawRuns(GuiGraphics g, List<ArchiveFiles.Run> runs, int x0, int y, int w, boolean reveal) {
        int x = x0;
        for (ArchiveFiles.Run r : runs) {
            for (String word : r.text().split("(?<= )")) {
                int ww = font.width(word);
                if (x > x0 && x + font.width(word.stripTrailing()) > x0 + w) { x = x0; y += 10; }
                if (r.redacted() && !reveal) rect(g, x, y, Math.max(2, font.width(word.stripTrailing())), 8, 0xFF000000);
                else text(g, word, x, y, r.redacted() ? 0xFFFF8A8F : 0xFFDFE7EE);
                x += ww;
            }
        }
        return y + 10;
    }

    // ---------------------------------------------------------------- Containment

    private int drawContainment(GuiGraphics g, int y) {
        ListTag list = t().getList("nearby", Tag.TAG_COMPOUND);
        text(g, "Anomalies within 64m \u00B7 cells count", SX + 8, y, MUTED);
        y += 14;
        if (list.isEmpty()) { empty(g, y - 20, "All quiet", "No anomalies detected nearby."); return y + 60; }
        for (Tag tg : list) {
            CompoundTag a = (CompoundTag) tg;
            boolean contained = a.getBoolean("c"), containable = a.getBoolean("k");
            int x = SX + 6, w = SW - 12;
            card(g, x, y, w, 26);
            circle(g, x + 10, y + 13, 4, contained ? TEAL : containable ? RED : 0xFF8A96A2);
            String nm = a.contains("dk") ? Component.translatable(a.getString("dk")).getString() : a.getString("d");
            text(g, ellipsize(nm, w - 70), x + 20, y + 4, 0xFFFFFFFF);
            String cls = a.getString("o");
            int cc = cls.equals("Safe") ? 0xFF3AA860 : cls.equals("Euclid") ? 0xFFC8A030 : 0xFFC0383F;
            rrect(g, x + w - 8 - font.width(cls) - 6, y + 3, font.width(cls) + 6, 10, 4, cc);
            text(g, cls, x + w - 5 - font.width(cls) - 6, y + 4, 0xFFFFFFFF);
            String status = contained ? (a.getString("n").isEmpty() ? "IN CELL" : a.getString("n").toUpperCase(Locale.ROOT)) : containable ? "LOOSE" : "STASIS-IMMUNE, LOOSE";
            text(g, ellipsize(status, w - 60), x + 20, y + 15, contained ? 0xFF7FD6FF : containable ? 0xFFFF7A7A : 0xFF9AA6B2);
            String dist = a.getInt("m") + "m";
            text(g, dist, x + w - 6 - font.width(dist), y + 15, 0xFF7A8794);
            y += 30;
        }
        return y;
    }

    // ---------------------------------------------------------------- Personnel (ID card)

    private int drawPersonnel(GuiGraphics g, int y) {
        CompoundTag t = t();
        boolean asc = t.getBoolean("ascended");
        int r = rank(), x = SX + 6, w = SW - 12;
        rrect(g, x, y, w, 84, 9, 0xFFEDEFF2);
        int band = asc ? 0xFF9B4BE0 : r >= 7 ? 0xFF7A35C4 : r >= 6 ? 0xFF8B1E2A : 0xFF2C3440;
        rrect(g, x, y, w, 16, 9, band);
        rect(g, x, y + 8, w, 8, band);
        text(g, "SCP FOUNDATION", x + 7, y + 4, 0xFFFFFFFF);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            rect(g, x + 6, y + 21, 26, 26, 0xFFB8BEC6);
            PlayerFaceRenderer.draw(g, mc.player.getSkin().texture(), x + 7, y + 22, 24);
            text(g, ellipsize(mc.player.getName().getString(), w - 70), x + 37, y + 22, 0xFF111418);
        }
        text(g, t.getString("personnel"), x + 37, y + 33, 0xFF55606C);
        List<FormattedCharSequence> title = font.split(Component.literal(t.getString("title")), w - 14);
        for (int i = 0; i < Math.min(2, title.size()); i++) g.drawString(font, title.get(i), x + 7, y + 52 + i * 10, asc ? 0xFF8A2BE2 : 0xFF1C2430, false);
        String clrText = clrLabel(clr());
        g.pose().pushPose();
        g.pose().translate(x + w - 8, y + 22, 0);
        g.pose().scale(1.6F, 1.6F, 1);
        g.drawString(font, clrText, -font.width(clrText), 0, band, false);
        g.pose().popPose();
        y += 92;
        int toNext = t.getInt("toNext");
        if (!asc && toNext >= 0) {
            card(g, x, y, w, 28);
            text(g, "Promotion review", x + 8, y + 5, 0xFFE8EEF4);
            small(g, toNext == 0 ? "THE COUNCIL IS CONSIDERING YOU" : "AFTER " + toNext + " MORE ASSIGNMENT" + (toNext == 1 ? "" : "S"), x + w / 2, y + 17, 0xFF7FD6FF);
            y += 34;
        }
        if (r >= 5) {
            int st = t.getInt("stability");
            int col = st < 25 ? RED : st > 85 ? 0xFF41D07A : 0xFFE0B43A;
            text(g, "Site Stability", x, y, 0xFFB8C6D6);
            text(g, st + "%", x + w - font.width(st + "%"), y, col);
            rrect(g, x, y + 11, w, 5, 2, 0xFF27303A);
            rrect(g, x, y + 11, Math.max(5, w * st / 100), 5, 2, col);
            y += 24;
        }
        return y;
    }

    // ---------------------------------------------------------------- Settings (keycard fusing)

    private void keycard(GuiGraphics g, int x, int y, int level) {
        int[] fill = {0xFFC8C8C4, 0xFFC8C8C4, 0xFF5A96D2, 0xFFE6C04A, 0xFFE08A3A, 0xFF2A2A2E, 0xFF14161A};
        int[] stripe = {0xFF787878, 0xFF787878, 0xFF1E468C, 0xFF9B2A2A, 0xFF6E2A82, 0xFFC8A028, 0xFFE8E8F0};
        int l = Mth.clamp(level, 0, 6);
        rrect(g, x, y, 46, 30, 4, fill[l]);
        rect(g, x, y + 4, 46, 5, stripe[l]);
        rrect(g, x + 4, y + 13, 8, 7, 2, 0xFFD6B24A);
        String lv = level >= 6 ? "\u03A9" : String.valueOf(level);
        text(g, lv, x + 38 - font.width(lv), y + 16, l >= 5 ? 0xFFFFFFFF : 0xFF10161D);
    }

    private static String cardName(int level) {
        return level >= 6 ? "Omni Keycard" : "Level " + level + " Keycard";
    }

    private int drawSettings(GuiGraphics g, int y) {
        CompoundTag t = t();
        int x = SX + 6, w = SW - 12;
        int fused = t.getInt("fused"), carried = t.getInt("carried"), rankClr = t.getInt("rankClr");
        label(g, "Clearance", x + 2, y);
        y += 12;
        card(g, x, y, w, 42);
        if (fused > 0) keycard(g, x + 6, y + 6, fused); else rrect(g, x + 6, y + 6, 46, 30, 4, 0xFF222A34);
        text(g, fused > 0 ? "Fused to this phone" : "No card fused", x + 58, y + 8, fused > 0 ? 0xFF5BE36F : MUTED);
        text(g, fused > 0 ? cardName(fused) + " · swipes scanners" : "Fuse a keycard to keep it", x + 58, y + 20, 0xFF9FB1C2);
        y += 48;
        if (carried > fused) {
            rrect(g, x, y, w, 74, 8, 0xFF5A4A1E);
            rrect(g, x + 1, y + 1, w - 2, 72, 7, 0xFF1D1A12);
            keycard(g, x + 6, y + 6, carried);
            text(g, "In your inventory", x + 58, y + 8, AMBER);
            text(g, cardName(carried), x + 58, y + 20, 0xFFE8EEF4);
            pill(g, x + 6, y + 42, w - 12, 16, "Fuse " + cardName(carried), AMBER, 0xFF1A1206, true, () -> send("fuse", 0));
            text(g, "Consumes the card. Can't be undone.", x + 6, y + 61, 0xFFA8987A);
            y += 80;
        }
        card(g, x, y, w, 46);
        text(g, "Effective clearance", x + 8, y + 6, 0xFFE8EEF4);
        text(g, clrLabel(clr()), x + w - 8 - font.width(clrLabel(clr())), y + 6, clrColor(clr()));
        text(g, "Fused " + fused + " · carried " + carried + " · rank " + Math.min(5, rankClr) + (rankClr >= 6 ? "+" : ""), x + 8, y + 20, MUTED);
        text(g, "The highest one counts.", x + 8, y + 31, 0xFF6F7C88);
        y += 54;
        label(g, "Device", x + 2, y);
        y += 12;
        card(g, x, y, w, 36);
        text(g, "Personnel ID", x + 8, y + 6, 0xFFE8EEF4);
        text(g, t.getString("personnel"), x + w - 8 - font.width(t.getString("personnel")), y + 6, MUTED);
        text(g, "SCP Unleashed link", x + 8, y + 21, 0xFFE8EEF4);
        text(g, "Online", x + w - 8 - font.width("Online"), y + 21, 0xFF5BE36F);
        return y + 40;
    }

    // ---------------------------------------------------------------- Command (Level 4+)

    private int drawCommand(GuiGraphics g, int y) {
        String[] tabs = {"MTF", "Personnel", "Orders"};
        int tx = SX + 6;
        for (int i = 0; i < tabs.length; i++) {
            int tw = font.width(tabs[i]) + 14;
            final int idx = i;
            pill(g, tx, y, tw, 14, tabs[i], commandTab == i ? TEAL : 0xFF1A222C, commandTab == i ? 0xFF0B1118 : 0xFFC8D2DC, true, () -> { commandTab = idx; scroll = 0; });
            tx += tw + 4;
        }
        y += 20;
        int x = SX + 6, w = SW - 12;
        if (commandTab == 0) {
            long cd = t().getLong("mtfCd");
            card(g, x, y, w, 30);
            text(g, "Mobile Task Force", x + 8, y + 5, 0xFFFFFFFF);
            text(g, cd > 0 ? "Command regrouping: " + cd / 60 + ":" + String.format("%02d", cd % 60) : "Command is ready", x + 8, y + 17, cd > 0 ? AMBER : 0xFF5BE36F);
            y += 36;
            label(g, "Spend an Unstable Fragment", x + 2, y);
            y += 12;
            boolean busy = t().getBoolean("mtfBusy");
            ListTag tiers = t().getList("mtfTiers", Tag.TAG_COMPOUND);
            for (int i = 0; i < tiers.size(); i++) {
                CompoundTag tt = tiers.getCompound(i);
                String tier = tt.getString("tier");
                int frags = tt.getInt("frags");
                String squad = tt.getString("name").isEmpty() ? tier : Component.translatable(tt.getString("name")).getString();
                int col = 0xFF000000 | ((tt.getInt("colour") >> 1) & 0x7F7F7F); // darkened so white text reads
                pill(g, x, y, w, 16, squad + " \u00B7 " + frags + " " + tier, col, cd <= 0 && !busy && frags > 0, () -> send("mtf:" + tier, 0));
                y += 20;
            }
            if (busy) { text(g, "A squad is already deployed.", x + 2, y, AMBER); y += 12; }
            y += 4;
            pill(g, x, y, (w - 4) / 2, 16, "Squad report", 0xFF262E38, true, () -> send("mtf_report", 0));
            pill(g, x + (w + 4) / 2, y, (w - 4) / 2, 16, "Extraction", 0xFF262E38, true, () -> send("mtf_extract", 0));
            return y + 20;
        }
        if (commandTab == 1) {
            ListTag ds = t().getList("dclass", Tag.TAG_COMPOUND);
            if (ds.isEmpty()) { empty(g, y - 20, "No D-Class nearby", "Personnel within 48m appear here."); return y + 60; }
            for (Tag tg : ds) {
                CompoundTag d = (CompoundTag) tg;
                card(g, x, y, w, 44);
                text(g, ellipsize(d.getString("name"), w - 60), x + 8, y + 5, 0xFFFFFFFF);
                String o = d.getString("order");
                String st = o.isEmpty() ? "ON DUTY" : o.toUpperCase(Locale.ROOT);
                text(g, st, x + w - 8 - font.width(st), y + 5, o.isEmpty() ? 0xFF9FB1C2 : 0xFF5BE36F);
                text(g, d.getInt("m") + "m away", x + 8, y + 15, MUTED);
                int id = d.getInt("id"), bw = (w - 20) / 3;
                pill(g, x + 6, y + 27, bw, 13, "Follow", 0xFF2B5FB8, true, () -> send("dorder:follow", id));
                pill(g, x + 10 + bw, y + 27, bw, 13, "Hold", 0xFF262E38, true, () -> send("dorder:hold", id));
                pill(g, x + 14 + 2 * bw, y + 27, bw, 13, "Release", 0xFF262E38, true, () -> send("dorder:release", id));
                y += 50;
            }
            return y;
        }
        // Orders: the career directives you hold
        ListTag dirs = t().getList("directives", Tag.TAG_COMPOUND);
        boolean any = false;
        for (Tag tg : dirs) {
            CompoundTag d = (CompoundTag) tg;
            String id = d.getString("id");
            if (id.equals("site01") || id.equals("return")) continue;
            any = true;
            long cd = d.getLong("cd");
            String lbl = d.getString("label") + (cd > 0 ? "  " + cd / 60 + ":" + String.format("%02d", cd % 60) : "");
            if (over(x, y, w, 16)) hover = d.getString("desc");
            pill(g, x, y, w, 16, lbl, 0xFF9B2730, cd <= 0, () -> send("dir:" + id, 0));
            y += 20;
        }
        if (!any) empty(g, y - 20, "No orders yet", "Requisition unlocks at Level 4.");
        return y;
    }

    // ---------------------------------------------------------------- Network (Level 5+)

    private int drawNetwork(GuiGraphics g, int y) {
        CompoundTag t = t();
        int x = SX + 6, w = SW - 12;
        label(g, "Orbital array", x + 2, y);
        y += 12;
        if (!t.contains("orbParts")) {
            card(g, x, y, w, 30);
            text(g, "No uplink to SCP Unleashed's array.", x + 8, y + 11, MUTED);
            y += 36;
        } else {
            boolean ready = t.getBoolean("orbReady");
            int shots = t.getInt("orbShots"), parts = t.getInt("orbParts");
            card(g, x, y, w, 64);
            circle(g, x + 22, y + 22, 14, 0xFF10222A);
            ring(g, x + 22, y + 22, 14, ready ? 0xFF5BE36F : AMBER);
            circle(g, x + 22, y + 22, 3, ready ? 0xFF5BE36F : AMBER);
            text(g, ready ? "Array online" : "Array incomplete", x + 44, y + 9, ready ? 0xFF5BE36F : AMBER);
            text(g, parts + " parts · " + shots + " shot" + (shots == 1 ? "" : "s") + " charged", x + 44, y + 21, 0xFF9FB1C2);
            pill(g, x + 6, y + 42, w - 12, 16, "Strike at crosshair (256m)", 0xFFA8323A, ready && shots > 0, () -> send("orbital_strike", 0));
            y += 70;
        }
        label(g, "Facility network", x + 2, y);
        y += 12;
        int hubs = t.getInt("hubs");
        card(g, x, y, w, 30);
        text(g, "Network hubs registered", x + 8, y + 5, 0xFFE8EEF4);
        text(g, hubs < 0 ? "unavailable" : String.valueOf(hubs), x + 8, y + 17, hubs > 0 ? TEAL : MUTED);
        return y + 36;
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
            searchFocused = false;
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
    public boolean charTyped(char c, int mods) {
        if (view == View.ARCHIVE && searchFocused && c >= ' ' && query.length() < 32) {
            query += c;
            scroll = 0;
            return true;
        }
        return super.charTyped(c, mods);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (view == View.ARCHIVE && searchFocused) {
            if (key == 259) { if (!query.isEmpty()) query = query.substring(0, query.length() - 1); return true; }
            if (key == 256 || key == 257) { searchFocused = false; return true; } // Esc / Enter leave the box
            if (minecraft != null && minecraft.options.keyInventory.matches(key, scan)) return true; // typing 'e' must not close
        }
        if (key == 259) { goBack(); return true; } // backspace = Back button
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
