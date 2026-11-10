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
    /** The Administrator's computer: the same system on a wide monitor, with the command apps. */
    private final boolean desktop;
    private final int PW, PH, BZ;
    private final int SX, SY, SW, SH;
    private static final int STATUS = 14, NAV = 22, HEADER = 22;
    private static final int TEAL = 0xFF3FD0FF, AMBER = 0xFFF2A93B, RED = 0xFFE5484D, BG = 0xFF0B1118, CARD = 0xFF141B24, MUTED = 0xFF8FA3B5;

    private static final ResourceLocation WALLPAPER = tex("wallpaper");
    private static ResourceLocation tex(String n) { return BreachProtocol.id("textures/gui/phone/" + n + ".png"); }

    private enum View { HOME, RECENTS, MAP, ANOMALY, MESSAGES, THREAD, MISSIONS, CALL, CAMERA, GALLERY, PHOTO, ARCHIVE, FILE, CONTAINMENT, PERSONNEL, SETTINGS, COMMAND, NETWORK, SITE, FORCES, SECURITY, CALENDAR, STAFF }

    private record App(String icon, String label, View view, int accent, IntPredicate allowed) {}

    private static final App PHONE = new App("app_phone", "Phone", View.CALL, 0xFF1F8F4C, c -> true);
    private static final App MESSAGES = new App("app_messages", "Messages", View.MESSAGES, 0xFF22994A, c -> true);
    private static final App MISSIONS = new App("app_missions", "Missions", View.MISSIONS, 0xFFC46A1C, c -> true);
    private static final App CAMERA = new App("app_camera", "Camera", View.CAMERA, 0xFF2C3440, c -> true);
    private static final App GALLERY = new App("app_gallery", "Gallery", View.GALLERY, 0xFF6B3FA0, c -> true);
    private static final App ARCHIVE = new App("app_archive", "Archive", View.ARCHIVE, 0xFF8A6A1E, c -> c >= 1);
    private static final App CONTAIN = new App("app_containment", "Containment", View.CONTAINMENT, 0xFF156E8F, c -> c >= 2);
    private static final App PERSONNEL = new App("app_personnel", "Personnel", View.PERSONNEL, 0xFF2E56A6, c -> true);
    private static final App STAFF_APP = new App("app_staff", "Staff", View.STAFF, 0xFF2E8F6A, c -> c >= 2);
    private static final App COMMAND = new App("app_command", "Command", View.COMMAND, 0xFF9B2730, c -> c >= 4);
    private static final App NETWORK = new App("app_network", "Network", View.NETWORK, 0xFF1A8A80, c -> c >= 5);
    private static final App SITE = new App("app_site01", "Site-01", View.SITE, 0xFF7A35C4, c -> false);
    private static final App SETTINGS = new App("app_settings", "Settings", View.SETTINGS, 0xFF4A525E, c -> true);
    private static final App MAP = new App("app_map", "Map", View.MAP, 0xFF2E8F4A, c -> true);
    private static final App CALENDAR = new App("app_calendar", "Calendar", View.CALENDAR, 0xFFC0383F, c -> false);
    private static final App FORCES = new App("app_forces", "Recruitment", View.FORCES, 0xFF2E8F4A, c -> false);
    private static final App SECURITY = new App("app_security", "Site Security", View.SECURITY, 0xFFA8323A, c -> false);
    /** Not a phone screen: the implant's console (powers, targets, temperament, orders). */
    private static final App IMPLANT = new App("app_implant", "Implant", View.HOME, 0xFFB070FF, c -> false);
    private static final List<App> GRID = List.of(IMPLANT, CALENDAR, STAFF_APP, FORCES, SECURITY, CAMERA, MAP, GALLERY, ARCHIVE, CONTAIN, PERSONNEL, COMMAND, NETWORK, SITE, SETTINGS);
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

    public PhoneScreen() { this(false); }

    public PhoneScreen(boolean desktop) {
        super(Component.literal(desktop ? "Foundation Terminal" : "Foundation Phone"));
        this.desktop = desktop;
        PW = desktop ? 480 : 176;
        PH = desktop ? 300 : 344;
        BZ = desktop ? 10 : 7;
        SX = BZ;
        SY = BZ;
        SW = PW - BZ * 2;
        SH = PH - BZ * 2;
        if (t().contains("callText")) view = View.CALL;
    }

    private int refreshIn;
    private String seenCall = "";

    @Override
    public void tick() {
        super.tick();
        // the command apps show live numbers
        if (view == View.STAFF && --refreshIn <= 0) { refreshIn = 40; send("wf:refresh", 0); }
        if ((view == View.FORCES || view == View.SECURITY || view == View.CALENDAR || view == View.CALL && t().getBoolean("callOutgoing")) && --refreshIn <= 0) { refreshIn = 20; send("refresh", 0); }
    }

    private static CompoundTag t() { return ClientCareerState.tag; }
    private static int clr() { return t().getInt("clr"); }
    private static int rank() { return t().getInt("rank"); }

    private static boolean allowed(App a) {
        if (a == IMPLANT) return t().getInt("dom") >= 1; // the Administrator and SCP-Ω
        if (a == SITE) return rank() >= 6 || t().getBoolean("ascended"); // the Council and the Administrator
        if (a == FORCES || a == SECURITY) return false; // checked per screen in allowedHere
        if (a == CALENDAR) return rank() >= 6 || t().getBoolean("ascended");
        return a.allowed().test(clr());
    }

    private boolean allowedHere(App a) {
        if (a == FORCES || a == SECURITY) return desktop && (rank() >= 7 || t().getBoolean("ascended"));
        return allowed(a);
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
        if (view == View.STAFF && (!staffSel.isEmpty() || hireSel >= 0)) { staffSel = ""; hireSel = -1; return; }
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

    /** RAW: draw real colours (the phone hardware). ROUND: keep rounded corners (home screen, bars). */
    private static boolean RAW, ROUND;

    private static int col(int c) { return RAW ? c : Terminal.map(c); }

    private static void rect(GuiGraphics g, int x, int y, int w, int h, int c) { g.fill(x, y, x + w, y + h, col(c)); }

    /** Terminal style: square edges (r is ignored except for tiny 1px bevels). */
    private static void rrect(GuiGraphics g, int x, int y, int w, int h, int r, int c) {
        c = col(c);
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
        c = col(c);
        for (int dy = -r; dy < r; dy++) {
            int dx = (int) Math.round(Math.sqrt(r * r - (dy + 0.5) * (dy + 0.5)));
            g.fill(cx - dx, cy + dy, cx + dx, cy + dy + 1, c);
        }
    }

    private static void ring(GuiGraphics g, int cx, int cy, int r, int c) {
        circle(g, cx, cy, r, c);
        circle(g, cx, cy, r - 1, Terminal.BG);
    }

    /** Every line of text is kept inside the screen: anything too long is shortened with "...". */
    private void text(GuiGraphics g, String s, int x, int y, int c) {
        int room = SX + SW - 4 - x;
        String shown = room > 8 ? ellipsize(s, room) : s;
        g.drawString(font, shown, x, y, Terminal.map(c), false);
        // cut short? point at it to read the whole thing
        if (!shown.equals(s) && over(x, y - 1, room, 10)) hover = s;
    }
    private void textC(GuiGraphics g, String s, int cx, int y, int c) {
        int room = 2 * Math.min(cx - SX - 5, SX + SW - 5 - cx);
        if (room > 8) s = ellipsize(s, room);
        g.drawString(font, s, cx - font.width(s) / 2, y, Terminal.map(c), false);
    }

    /** 1:05, 12:30, 2h 05m - never a huge raw number. */
    private static String clock(int seconds) {
        seconds = Math.max(0, seconds);
        if (seconds >= 3600) return seconds / 3600 + "h " + String.format("%02d", seconds % 3600 / 60) + "m";
        return seconds / 60 + ":" + String.format("%02d", seconds % 60);
    }

    private void big(GuiGraphics g, String s, int cx, int y, float k, int c) {
        g.pose().pushPose();
        g.pose().translate(cx, y, 0);
        g.pose().scale(k, k, 1);
        int room = (int) (2 * Math.min(cx - SX - 5, SX + SW - 5 - cx) / k);
        if (room > 8) s = ellipsize(s, room);
        g.drawString(font, s, -font.width(s) / 2, 0, Terminal.map(c), false);
        g.pose().popPose();
    }

    private String ellipsize(String s, int w) {
        if (font.width(s) <= w) return s;
        while (s.length() > 1 && font.width(s + "...") > w) s = s.substring(0, s.length() - 1);
        return s + "...";
    }

    private int wrap(GuiGraphics g, String s, int x, int y, int w, int c) {
        List<FormattedCharSequence> lines = font.split(Component.literal(s), w);
        for (int i = 0; i < lines.size(); i++) g.drawString(font, lines.get(i), x, y + i * 10, Terminal.map(c), false);
        return lines.size() * 10;
    }

    private int wrapHeight(String s, int w) { return font.split(Component.literal(s), w).size() * 10; }

    private void hit(int x, int y, int w, int h, Runnable r) { hits.add(new Hit(x, y, w, h, r)); }
    private boolean over(int x, int y, int w, int h) { return mouseLX >= x && mouseLX < x + w && mouseLY >= y && mouseLY < y + h; }

    private void pill(GuiGraphics g, int x, int y, int w, int h, String label, int color, int textColor, boolean enabled, Runnable action) {
        boolean hov = enabled && over(x, y, w, h);
        int tone = Terminal.map(color);
        boolean danger = ((tone >> 16) & 255) > ((tone >> 8) & 255) + 40;
        int rim = !enabled ? Terminal.LINE : danger ? 0xFFB84848 : hov ? Terminal.BRIGHT : Terminal.MID;
        int fill = !enabled ? 0xFF07110A : danger ? (hov ? 0xFF5A1818 : 0xFF2E0E0E) : (hov ? 0xFF1E6B3A : 0xFF0E3A1E);
        RAW = true;
        rrect(g, x, y, w, h, h / 2, rim);
        rrect(g, x + 1, y + 1, w - 2, h - 2, h / 2 - 1, fill);
        RAW = false;
        String lbl = ellipsize(label, w - 8);
        g.drawString(font, lbl, x + w / 2 - font.width(lbl) / 2, y + (h - 8) / 2, !enabled ? Terminal.DIM : danger ? 0xFFFFB3B3 : Terminal.BRIGHT, false);
        if (!lbl.equals(label) && over(x, y, w, h)) hover = label;
        if (enabled) hit(x, y, w, h, action);
    }

    private void pill(GuiGraphics g, int x, int y, int w, int h, String label, int color, boolean enabled, Runnable action) {
        pill(g, x, y, w, h, label, color, 0xFFFFFFFF, enabled, action);
    }

    private static int brighten(int c) {
        int a = c >>> 24, r = Math.min(255, ((c >> 16) & 255) + 28), gr = Math.min(255, ((c >> 8) & 255) + 28), b = Math.min(255, (c & 255) + 28);
        return a << 24 | r << 16 | gr << 8 | b;
    }

    private void card(GuiGraphics g, int x, int y, int w, int h) {
        RAW = true;
        rrect(g, x, y, w, h, 8, 0xFF163D24);
        rrect(g, x + 1, y + 1, w - 2, h - 2, 7, 0xFF08180E);
        RAW = false;
    }

    /** Rounded box with a 1px rim, in real (already green) colours. */
    private void rbox(GuiGraphics g, int x, int y, int w, int h, int r, int fill, int rim) {
        RAW = true;
        rrect(g, x, y, w, h, r, rim);
        rrect(g, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), fill);
        RAW = false;
    }

    /** A titled panel: ┌─ TITLE ───┐ */
    private void panel(GuiGraphics g, String title, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, Terminal.PANEL);
        Terminal.box(g, x, y + 4, w, h - 4, Terminal.LINE);
        String tt = title.toUpperCase(Locale.ROOT);
        g.fill(x + 5, y, x + 9 + font.width(tt), y + 9, Terminal.PANEL);
        g.drawString(font, tt, x + 7, y, Terminal.MID, false);
    }

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

    private static boolean hasLink(String feature) {
        ListTag l = t().getList("links", Tag.TAG_STRING);
        for (int i = 0; i < l.size(); i++) if (l.getString(i).equals(feature)) return true;
        return false;
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
        if (a == STAFF_APP) return waitingShortlists();
        if (a == MISSIONS) return t().getList("missions", Tag.TAG_COMPOUND).size();
        if (a == PHONE) return t().contains("callText") ? 1 : 0;
        if (a == CALENDAR) {
            int n = 0;
            for (Tag e : t().getList("appts", Tag.TAG_COMPOUND)) {
                CompoundTag c = (CompoundTag) e;
                if (c.getInt("seat") == 0 ? c.getBoolean("tx") : !c.getBoolean("ok")) n++;
            }
            return n;
        }
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
            case CONTAINMENT, ANOMALY -> CONTAIN;
            case PERSONNEL -> PERSONNEL;
            case STAFF -> STAFF_APP;
            case SETTINGS -> SETTINGS;
            case COMMAND -> COMMAND;
            case NETWORK -> NETWORK;
            case SITE -> SITE;
            case MAP -> MAP;
            case FORCES -> FORCES;
            case SECURITY -> SECURITY;
            case CALENDAR -> CALENDAR;
            default -> null;
        };
    }

    // ================================================================ render

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        scale = desktop ? Math.min(2.0F, Math.min((height - 34) / (float) PH, (width - 16) / (float) PW)) : Math.min(1.3F, (height - 12) / (float) PH);
        ox = (int) ((width - PW * scale) / 2);
        oy = (int) ((height - (desktop ? PH + 22 : PH) * scale) / 2);
        mouseLX = (int) ((mouseX - ox) / scale);
        mouseLY = (int) ((mouseY - oy) / scale);
        hits.clear();
        hover = "";
        String ck = t().contains("callText") ? t().getString("callFrom") + "|" + t().getString("callText") : "";
        if (!ck.equals(seenCall)) {
            seenCall = ck;
            if (!ck.isEmpty() && view != View.CALL) { go(View.CALL); scroll = 0; }
        }
        // apps you lose clearance for close themselves
        App cur = appFor(view);
        if (cur != null && !allowedHere(cur)) goHome();

        g.pose().pushPose();
        g.pose().translate(ox, oy, 0);
        g.pose().scale(scale, scale, 1);

        // the hardware keeps its real look
        RAW = true;
        if (desktop) {
            // a monitor on a stand
            rect(g, PW / 2 - 18, PH, 36, 14, 0xFF22262E);
            rrect(g, PW / 2 - 60, PH + 13, 120, 8, 3, 0xFF2A2E37);
            rrect(g, -3, -3, PW + 6, PH + 6, 8, 0xFF2A2E37);
            rrect(g, 0, 0, PW, PH, 6, 0xFF15181E);
            rect(g, SX, SY, SW, SH, Terminal.BG);
            circle(g, PW - 14, PH - 5, 2, 0xFF5BE36F);
        } else {
            rrect(g, -2, -2, PW + 4, PH + 4, 26, 0xFF2A2E37);
            rrect(g, 0, 0, PW, PH, 24, 0xFF1A1D24);
            rect(g, -3, 74, 2, 24, 0xFF2A2E37);
            rect(g, PW + 1, 90, 2, 34, 0xFF2A2E37);
            rrect(g, SX, SY, SW, SH, 18, Terminal.BG);
        }
        RAW = false;

        scissor(g, SX, SY, SW, SH);
        boolean phone = view == View.HOME || view == View.RECENTS;
        ROUND = phone;
        switch (view) {
            case HOME -> drawHome(g);
            case RECENTS -> drawRecents(g);
            default -> drawApp(g);
        }
        ROUND = true;
        drawStatusBar(g);
        drawNavBar(g);
        ROUND = false;

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
    /** The terminal's taskbar: Start, a button per recent app, and the clock. */
    private void drawTaskbar(GuiGraphics g) {
        int y = SY + SH - NAV;
        rect(g, SX, y, SW, NAV, 0xFF06120A);
        rect(g, SX, y, SW, 1, Terminal.MID);
        boolean hs = over(SX + 2, y + 3, 62, NAV - 6);
        rbox(g, SX + 2, y + 3, 62, NAV - 6, 3, hs ? 0xFF1E6B3A : 0xFF0E3A1E, hs ? Terminal.BRIGHT : Terminal.MID);
        g.drawString(font, "■ START", SX + 8, y + 7, Terminal.BRIGHT, false);
        hit(SX + 2, y + 3, 62, NAV - 6, this::goHome);
        int x = SX + 70;
        App cur = appFor(view);
        for (App a : RECENT) {
            if (!allowedHere(a) || x > SX + SW - 140) continue;
            int w = Math.min(90, font.width(a.label()) + 16);
            boolean on = a == cur, hov = over(x, y + 3, w, NAV - 6);
            rbox(g, x, y + 3, w, NAV - 6, 3, on ? 0xFF1E6B3A : hov ? 0xFF0E3A1E : 0xFF07110A, on ? Terminal.BRIGHT : Terminal.LINE);
            g.drawString(font, ellipsize(a.label(), w - 8), x + 4, y + 7, on ? Terminal.BRIGHT : Terminal.GREEN, false);
            hit(x, y + 3, w, NAV - 6, () -> openApp(a));
            x += w + 4;
        }
        String c = clock() + "  ·  DAY " + (t().getLong("now") / 24000 + 1);
        g.drawString(font, c, SX + SW - 8 - font.width(c), y + 7, Terminal.GREEN, false);
    }

    private void drawNavBar(GuiGraphics g) {
        if (desktop) { drawTaskbar(g); return; }
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
        if (desktop) { drawDesktopHome(g); return; }
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
        for (App a : GRID) if (allowedHere(a) && !DOCK.contains(a)) apps.add(a);
        int cols = desktop ? 9 : 4, cell = (SW - 8) / cols;
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

    /** The terminal's desktop: a header, every app as a large icon, and the live board. */
    private void drawDesktopHome(GuiGraphics g) {
        rect(g, SX, SY, SW, SH, Terminal.BG);
        for (int yy = SY; yy < SY + SH; yy += 3) rect(g, SX, yy, SW, 1, 0x22000000);
        int y = SY + STATUS + 6;
        g.drawString(font, "FOUNDATION SECURE TERMINAL", SX + 10, y, Terminal.BRIGHT, false);
        String who = t().getBoolean("ascended") ? "THE ASCENDANT" : "THE ADMINISTRATOR";
        g.drawString(font, "LOGGED IN: " + who, SX + SW - 10 - font.width("LOGGED IN: " + who), y, Terminal.MID, false);
        Terminal.dashes(g, SX + 10, y + 12, SW - 20, Terminal.LINE);
        y += 20;
        List<App> apps = new ArrayList<>();
        for (App a : GRID) if (allowedHere(a)) apps.add(a);
        for (App a : DOCK) if (allowedHere(a) && !apps.contains(a)) apps.add(a);
        int cols = 8, cell = (SW - 20) / cols;
        App hovered = null;
        for (int i = 0; i < apps.size(); i++) {
            int x = SX + 10 + (i % cols) * cell + (cell - 28) / 2, iy = y + (i / cols) * 46;
            if (drawIcon(g, apps.get(i), x, iy, cell)) hovered = apps.get(i);
        }
        y += ((apps.size() + cols - 1) / cols) * 46 + 4;
        // the live board: alerts, open calls, unread messages
        int x = SX + 10, w = SW - 20;
        panel(g, "Status", x, y, w, SY + SH - NAV - y - 6);
        int ly = y + 14;
        ListTag alerts = t().getList("alerts", Tag.TAG_COMPOUND);
        for (int i = 0; i < alerts.size() && ly < SY + SH - NAV - 16; i++) {
            CompoundTag a = alerts.getCompound(i);
            g.drawString(font, "⚠ " + a.getString("site") + ": " + a.getString("who") + " on the grounds (" + a.getInt("n") + ")", x + 8, ly, Terminal.RED, false);
            ly += 11;
        }
        if (alerts.isEmpty()) { g.drawString(font, "All sites secure.", x + 8, ly, Terminal.GREEN, false); ly += 11; }
        if (t().contains("callText")) { g.drawString(font, "☎ " + t().getString("callFrom") + " is on the line.", x + 8, ly, Terminal.AMBER, false); ly += 11; }
        int un = unread();
        if (un > 0) g.drawString(font, un + " unread message" + (un == 1 ? "" : "s") + ".", x + 8, ly, Terminal.MID, false);
        if (hovered != null) hover = hovered.label();
    }

    /** Camera isn't a screen: it puts the phone down and gives you the viewfinder. */
    private void openApp(App a) {
        if (a == CAMERA) {
            CameraMode.start();
            return;
        }
        if (a == IMPLANT) {
            minecraft.setScreen(new CommandScreen());
            return;
        }
        if (a == PHONE) send("refresh", 0);
        if (a == STAFF_APP) { send("wf:refresh", 0); refreshIn = 40; }
        go(a.view());
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
        hit(x - 4, y - 4, 36, cell > 0 ? 42 : 36, () -> openApp(a));
        return hov;
    }

    /** Small (3/4 size) centred text with a soft shadow, for labels on the wallpaper. */
    private void small(GuiGraphics g, String s, int cx, int y, int c) {
        g.pose().pushPose();
        g.pose().translate(cx, y, 0);
        g.pose().scale(0.75F, 0.75F, 1);
        int room = (int) (2 * Math.min(cx - SX - 4, SX + SW - 4 - cx) / 0.75F);
        if (room > 8) s = ellipsize(s, room);
        g.drawString(font, s, -font.width(s) / 2, 0, Terminal.map(c), false);
        g.pose().popPose();
    }

    private void drawRecents(GuiGraphics g) {
        rect(g, SX, SY, SW, SH, 0xF0070A0E);
        textC(g, "Recent apps", SX + SW / 2, SY + 22, MUTED);
        int y = SY + 38;
        List<App> list = new ArrayList<>();
        for (App a : RECENT) if (allowedHere(a)) list.add(a);
        if (list.isEmpty()) textC(g, "Nothing open yet.", SX + SW / 2, y + 40, 0xFF6F7C88);
        for (App a : list) {
            int x = SX + 10, w = SW - 20, h = 34;
            if (y + h > SY + SH - NAV - 4) break;
            rrect(g, x, y, w, h, 10, over(x, y, w, h) ? 0xFF1E2733 : CARD);
            rect(g, x, y, 4, h, a.accent());
            g.blit(tex(a.icon()), x + 8, y + 5, 24, 24, 0, 0, 32, 32, 32, 32);
            text(g, a.label(), x + 38, y + 13, 0xFFFFFFFF);
            hit(x, y, w, h, () -> openApp(a));
            y += h + 6;
        }
    }

    // ---------------------------------------------------------------- app chrome

    private void drawApp(GuiGraphics g) {
        App app = appFor(view);
        int top = SY + STATUS;
        if (!(view == View.CALL && t().contains("callText")) && app != null) {
            g.fill(SX, top, SX + SW, top + HEADER, 0xFF0C2C17);
            g.fill(SX, top + HEADER - 1, SX + SW, top + HEADER, Terminal.MID);
            String title = switch (view) {
                case THREAD -> nick(thread);
                case FILE -> fileTitle();
                case PHOTO -> "Photo";
                case ANOMALY -> "Anomaly";
                default -> app.label();
            };
            g.drawString(font, ellipsize(title, SW - 24), SX + 10, top + 7, Terminal.BRIGHT, false);
            if (desktop) {
                // window buttons: back and close
                boolean hx = over(SX + SW - 20, top + 3, 16, 15), hb = over(SX + SW - 40, top + 3, 16, 15);
                rbox(g, SX + SW - 20, top + 3, 16, 15, 2, hx ? 0xFF5A1818 : 0xFF2E0E0E, 0xFFB84848);
                g.drawString(font, "x", SX + SW - 15, top + 6, 0xFFFFB3B3, false);
                hit(SX + SW - 20, top + 3, 16, 15, this::goHome);
                rbox(g, SX + SW - 40, top + 3, 16, 15, 2, hb ? 0xFF1E6B3A : 0xFF0E3A1E, Terminal.MID);
                g.drawString(font, "<", SX + SW - 35, top + 6, Terminal.BRIGHT, false);
                hit(SX + SW - 40, top + 3, 16, 15, this::goBack);
            }
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
            case ANOMALY -> drawAnomaly(g, y0);
            case PERSONNEL -> drawPersonnel(g, y0);
            case STAFF -> drawStaff(g, y0, top);
            case SETTINGS -> drawSettings(g, y0);
            case COMMAND -> drawCommand(g, y0);
            case NETWORK -> drawNetwork(g, y0);
            case MAP -> drawMap(g, y0);
            case FORCES -> drawForces(g, y0);
            case CALENDAR -> drawCalendar(g, y0);
            case SECURITY -> drawSecurity(g, y0);
            default -> drawSite(g, y0);
        };
    }

    private void empty(GuiGraphics g, int y, String title, String sub) {
        textC(g, title, SX + SW / 2, y + 30, 0xFFE0E6EC);
        List<FormattedCharSequence> lines = font.split(Component.literal(sub), SW - 24);
        for (int i = 0; i < lines.size(); i++) g.drawString(font, lines.get(i), SX + SW / 2 - font.width(lines.get(i)) / 2, y + 44 + i * 10, Terminal.DIM, false);
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
        java.util.Set<String> waiting = new java.util.HashSet<>();
        for (Tag e : t().getList("messages", Tag.TAG_COMPOUND)) {
            CompoundTag m = (CompoundTag) e;
            if (!m.getList("rp", Tag.TAG_COMPOUND).isEmpty() && m.getInt("ans") < 0) waiting.add(m.getString("s"));
        }
        for (ThreadInfo th : map.values()) {
            int h = 32;
            if (over(SX, y, SW, h)) rect(g, SX, y, SW, h, 0xFF19202A);
            avatar(g, th.sender(), SX + 18, y + 16, 11);
            text(g, ellipsize(nick(th.sender()), SW - 80), SX + 34, y + 5, 0xFFFFFFFF);
            text(g, ago(th.time()), SX + SW - 8 - font.width(ago(th.time())), y + 5, 0xFF7A8794);
            if (waiting.contains(th.sender())) text(g, "\u25B6 Waiting for your orders", SX + 34, y + 17, AMBER);
            else text(g, ellipsize(th.last(), SW - 56), SX + 34, y + 17, th.unread() > 0 ? 0xFFDDE7F0 : 0xFF8794A0);
            if (th.unread() > 0) circle(g, SX + SW - 12, y + 21, 3, 0xFF2F9BFF);
            rect(g, SX + 34, y + h - 1, SW - 34, 1, 0xFF1C232C);
            hit(SX, y, SW, h, () -> { thread = th.sender(); go(View.THREAD); });
            y += h;
        }
        return y;
    }

    private String compose = "";
    private boolean composeFocused;

    /** Index of a player thread's contact, or -1 (council threads can't be answered). */
    private static int contactIndex(String th) {
        if (!th.startsWith("@")) return -1;
        ListTag cs = t().getList("contactList", Tag.TAG_COMPOUND);
        for (int i = 0; i < cs.size(); i++) if (cs.getCompound(i).getString("n").equals(th.substring(1))) return i;
        return -1;
    }

    private void sendText() {
        int idx = contactIndex(thread);
        if (idx < 0 || compose.isBlank()) return;
        send("text:" + compose, idx);
        compose = "";
        scrollToBottom = true;
    }

    private int drawThread(GuiGraphics g, int y) {
        List<CompoundTag> mine = new ArrayList<>();
        for (Tag e : t().getList("messages", Tag.TAG_COMPOUND)) if (((CompoundTag) e).getString("s").equals(thread)) mine.add((CompoundTag) e);
        Collections.reverse(mine);
        int x = SX + 6, w = SW - 12, maxW = w - 22;
        small(g, ("SECURE CHANNEL // " + thread).toUpperCase(Locale.ROOT), SX + SW / 2, y, Terminal.DIM);
        y += 12;
        if (thread.startsWith("O5-") && (rank() >= 6 || t().getBoolean("ascended"))) {
            int seat = 0;
            try { seat = Integer.parseInt(seatOf(thread).substring(3)); } catch (Exception ignored) {}
            if (seat >= 1 && seat <= 12) {
                final int st = seat;
                pill(g, x + w / 2 - 50, y, 100, 14, "\u260E Call " + seatOf(thread), 0xFF2EA65A, !t().contains("callText"), () -> { send("dial", st); go(View.CALL); });
                y += 18;
            }
        }
        for (CompoundTag m : mine) {
            boolean out = m.getBoolean("o");
            String body = m.getString("b");
            int lines = wrapHeight(body, maxW), bw = Math.min(maxW, font.width(body)) + 10, h = lines + 8;
            int bx = out ? x + w - bw : x;
            rbox(g, bx, y, bw, h, 7, out ? 0xFF0E3A1E : 0xFF08180E, out ? Terminal.MID : 0xFF163D24);
            List<FormattedCharSequence> ls = font.split(Component.literal(body), maxW);
            for (int i = 0; i < ls.size(); i++) g.drawString(font, ls.get(i), bx + 5, y + 4 + i * 10, out ? Terminal.BRIGHT : Terminal.GREEN, false);
            y += h + 1;
            String meta = (out ? "YOU" : nick(thread).replace("@", "").toUpperCase(Locale.ROOT)) + "  \u00B7  " + ago(m.getLong("t")).toUpperCase(Locale.ROOT);
            int mw = (int) (font.width(meta) * 0.75F);
            small(g, meta, out ? x + w - mw / 2 : x + mw / 2, y, Terminal.DIM);
            y += 10;
            // a message that wants your orders: answer it here
            ListTag rp = m.getList("rp", Tag.TAG_COMPOUND);
            if (!out && !rp.isEmpty() && m.getInt("ans") < 0) {
                small(g, "YOUR ORDERS", x + 22, y, Terminal.MID);
                y += 9;
                for (int i = 0; i < rp.size(); i++) {
                    String label = rp.getCompound(i).getString("l");
                    List<FormattedCharSequence> ls2 = font.split(Component.literal("\u21B3 " + label), w - 14);
                    int bh = ls2.size() * 10 + 6;
                    boolean hov = over(x + 4, y, w - 4, bh);
                    rbox(g, x + 4, y, w - 4, bh, 6, hov ? 0xFF0F3A1C : 0xFF0A2412, hov ? Terminal.BRIGHT : Terminal.MID);
                    for (int k = 0; k < ls2.size(); k++) g.drawString(font, ls2.get(k), x + 10, y + 4 + k * 10, hov ? Terminal.BRIGHT : Terminal.GREEN, false);
                    final int id = m.getInt("id"), choice = i;
                    hit(x + 4, y, w - 4, bh, () -> { send("msg_reply:" + choice, id); scrollToBottom = true; });
                    y += bh + 3;
                }
                y += 4;
            }
        }
        if (contactIndex(thread) >= 0) {
            y += 4;
            boolean hov = over(x, y, w - 30, 16);
            rbox(g, x, y, w - 30, 16, 8, composeFocused ? 0xFF0B2414 : 0xFF08180E, composeFocused ? Terminal.GREEN : hov ? Terminal.MID : 0xFF163D24);
            boolean caret = composeFocused && (System.currentTimeMillis() / 500) % 2 == 0;
            String shown = compose.isEmpty() && !composeFocused ? "TYPE A MESSAGE" : compose;
            while (font.width(shown + "_") > w - 38 && shown.length() > 1) shown = shown.substring(1);
            g.drawString(font, shown + (caret ? "_" : ""), x + 4, y + 4, compose.isEmpty() && !composeFocused ? Terminal.DIM : Terminal.BRIGHT, false);
            hit(x, y, w - 30, 16, () -> composeFocused = true);
            pill(g, x + w - 27, y, 27, 16, ">", Terminal.MID, !compose.isBlank(), this::sendText);
            y += 20;
        } else if (thread.startsWith("@")) {
            small(g, "NOT IN YOUR CONTACTS", SX + SW / 2, y + 4, Terminal.DIM);
            y += 14;
        }
        return y;
    }

    // ---------------------------------------------------------------- Map

    private static final float[] ZOOMS = {0.5F, 1, 2, 4};
    private int zoomIdx = 1;
    /** Point picked on the map (world x, z), or none. */
    private boolean mapTarget;
    private int mapTX, mapTZ;

    private static int missionColor(String type) {
        return switch (type) {
            case "RECOVER" -> RED; case "TERMINATE" -> 0xFFB04FE0; case "CARE" -> AMBER; default -> TEAL;
        };
    }

    private int drawMap(GuiGraphics g, int y) {
        int x = SX + 6, w = SW - 12;
        float zoom = ZOOMS[zoomIdx];
        Minecraft mc = Minecraft.getInstance();
        if (!PhoneMap.update(zoom) || mc.player == null) { empty(g, y, "No signal", "The map needs a loaded world."); return y + 60; }
        final int S = PhoneMap.SIZE;
        RAW = true;
        rrect(g, x - 1, y - 1, w + 2, w + 2, 4, Terminal.MID);
        RAW = false;
        g.blit(PhoneMap.TEXTURE, x, y, w, w, 0, 0, S, S, S, S);
        float k = w / (float) S;
        int cx = PhoneMap.centerX(), cz = PhoneMap.centerZ();
        final int mapX = x, mapY = y;
        java.util.function.IntUnaryOperator toX = wx -> mapX + Math.round(((wx - cx) / zoom + S / 2F) * k);
        java.util.function.IntUnaryOperator toY = wz -> mapY + Math.round(((wz - cz) / zoom + S / 2F) * k);
        // grid every 64 blocks
        int half = (int) (S / 2F * zoom);
        for (int gx = Math.floorDiv(cx - half, 64) * 64; gx <= cx + half; gx += 64) {
            int sx = toX.applyAsInt(gx);
            if (sx > x && sx < x + w) g.fill(sx, y, sx + 1, y + w, 0x1830FF70);
        }
        for (int gz = Math.floorDiv(cz - half, 64) * 64; gz <= cz + half; gz += 64) {
            int sy = toY.applyAsInt(gz);
            if (sy > y && sy < y + w) g.fill(x, sy, x + w, sy + 1, 0x1830FF70);
        }
        String tip = null;
        boolean pulse = (System.currentTimeMillis() / 400) % 2 == 0;
        // pins: missions (always), anomalies (Camera Tablet), rifts (Rift Detector)
        List<int[]> pins = new ArrayList<>(); // x, z, colour, kind(0 mission,1 anomaly,2 rift), index
        ListTag ms = t().getList("missions", Tag.TAG_COMPOUND);
        for (int i = 0; i < ms.size(); i++) if (ms.getCompound(i).getBoolean("hasPos")) pins.add(new int[]{ms.getCompound(i).getInt("x"), ms.getCompound(i).getInt("z"), Terminal.map(missionColor(ms.getCompound(i).getString("type"))), 0, i});
        ListTag an = t().getList("nearby", Tag.TAG_COMPOUND);
        if (hasLink("cameras")) for (int i = 0; i < an.size(); i++) pins.add(new int[]{an.getCompound(i).getInt("x"), an.getCompound(i).getInt("z"), an.getCompound(i).getBoolean("c") ? 0xFF7FD6FF : 0xFFFF5A5A, 1, i});
        ListTag rf = t().getList("rifts", Tag.TAG_COMPOUND);
        for (int i = 0; i < rf.size(); i++) pins.add(new int[]{rf.getCompound(i).getInt("x"), rf.getCompound(i).getInt("z"), 0xFF000000 | rf.getCompound(i).getInt("colour"), 2, i});
        for (int[] pin : pins) {
            int mx = Mth.clamp(toX.applyAsInt(pin[0]), x + 3, x + w - 4), my = Mth.clamp(toY.applyAsInt(pin[1]), y + 3, y + w - 4);
            boolean guided = Guide.guiding(pin[0], pin[1]);
            if (guided && pulse) Terminal.box(g, mx - 5, my - 5, 11, 11, Terminal.BRIGHT);
            if (pin[3] == 2) { // rift: diamond
                for (int d = 0; d < 4; d++) g.fill(mx - (3 - d), my - d, mx + (4 - d), my - d + 1, 0xFF000000);
                for (int d = 0; d < 4; d++) g.fill(mx - (3 - d), my + d, mx + (4 - d), my + d + 1, 0xFF000000);
                for (int d = 0; d < 3; d++) g.fill(mx - (2 - d), my - d, mx + (3 - d), my - d + 1, pin[2]);
                for (int d = 0; d < 3; d++) g.fill(mx - (2 - d), my + d, mx + (3 - d), my + d + 1, pin[2]);
            } else {
                g.fill(mx - 2, my - 2, mx + 3, my + 3, 0xFF000000);
                g.fill(mx - 1, my - 1, mx + 2, my + 2, pin[2]);
                if (pin[3] == 0) g.fill(mx, my + 2, mx + 1, my + 5, pin[2]);
            }
            if (over(mx - 4, my - 4, 9, 9)) {
                tip = switch (pin[3]) {
                    case 0 -> ms.getCompound(pin[4]).getString("type").replace('_', ' ');
                    case 1 -> Component.translatable(an.getCompound(pin[4]).getString("dk")).getString();
                    default -> rf.getCompound(pin[4]).getString("rarity") + " rift";
                } + "  " + pin[0] + ", " + pin[1];
            }
        }
        // picked target
        if (mapTarget) {
            int tx = toX.applyAsInt(mapTX), ty = toY.applyAsInt(mapTZ);
            if (tx >= x && tx < x + w && ty >= y && ty < y + w) {
                int c = Terminal.RED;
                g.fill(tx - 6, ty, tx - 2, ty + 1, c); g.fill(tx + 3, ty, tx + 7, ty + 1, c);
                g.fill(tx, ty - 6, tx + 1, ty - 2, c); g.fill(tx, ty + 3, tx + 1, ty + 7, c);
                Terminal.box(g, tx - 3, ty - 3, 7, 7, c);
            }
        }
        // you
        int px = x + w / 2, py = y + w / 2;
        g.pose().pushPose();
        g.pose().translate(px + 0.5F, py + 0.5F, 0);
        g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(mc.player.getYRot() + 180F));
        for (int i = 0; i < 5; i++) g.fill(-(4 - i), -3 + i, (4 - i) + 1, -2 + i, 0xFF000000);
        for (int i = 0; i < 4; i++) g.fill(-(3 - i), -2 + i, (3 - i) + 1, -1 + i, Terminal.BRIGHT);
        g.pose().popPose();
        g.drawString(font, "N", x + w / 2 - 2, y + 2, Terminal.BRIGHT, true);
        int scaleM = Math.round(64 * zoom);
        g.fill(x + w - 6 - (int) (64 / zoom * k), y + w - 5, x + w - 6, y + w - 4, Terminal.BRIGHT);
        small(g, scaleM + "M", x + w - 6 - (int) (32 / zoom * k), y + w - 13, Terminal.BRIGHT);
        if (tip != null) {
            int tw2 = font.width(tip) + 8;
            g.fill(x + 3, y + 3, x + 3 + Math.min(tw2, w - 6), y + 15, 0xE0020804);
            Terminal.box(g, x + 3, y + 3, Math.min(tw2, w - 6), 12, Terminal.MID);
            g.drawString(font, ellipsize(tip, w - 14), x + 7, y + 5, Terminal.GREEN, false);
        }
        // tap to pick a point
        final int fx = x, fy = y;
        hit(x, y, w, w, () -> {
            mapTarget = true;
            mapTX = cx + Math.round(((mouseLX - fx) / k - S / 2F) * zoom);
            mapTZ = cz + Math.round(((mouseLY - fy) / k - S / 2F) * zoom);
        });
        y += w + 5;
        pill(g, x, y, 22, 14, "-", Terminal.MID, zoomIdx < ZOOMS.length - 1, () -> zoomIdx++);
        pill(g, x + 25, y, 22, 14, "+", Terminal.MID, zoomIdx > 0, () -> zoomIdx--);
        small(g, mc.player.getBlockX() + ", " + mc.player.getBlockY() + ", " + mc.player.getBlockZ(), x + 52 + (int) (font.width(mc.player.getBlockX() + ", " + mc.player.getBlockY() + ", " + mc.player.getBlockZ()) * 0.375F), y + 4, MUTED);
        if (Guide.active()) pill(g, x + w - 44, y, 44, 14, "Stop", RED, true, Guide::stop);
        y += 20;
        // target panel
        if (mapTarget) {
            int d = (int) Math.sqrt(Math.pow(mapTX - mc.player.getX(), 2) + Math.pow(mapTZ - mc.player.getZ(), 2));
            card(g, x, y, w, 46);
            text(g, "Target  " + mapTX + ", " + mapTZ, x + 7, y + 5, 0xFFFFFFFF);
            small(g, d + "M AWAY", x + w - 8 - (int) (font.width(d + "M AWAY") * 0.375F), y + 6, MUTED);
            int hw = (w - 18) / 2;
            final int gx = mapTX, gz = mapTZ;
            int gy = mc.level != null && mc.level.hasChunk(gx >> 4, gz >> 4) ? mc.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, gx, gz) : mc.player.getBlockY();
            pill(g, x + 6, y + 24, hw, 16, Guide.guiding(gx, gz) ? "Guiding" : "Guide here", 0xFF2E9E57, !Guide.guiding(gx, gz), () -> Guide.start(gx, gy, gz, "Map target " + gx + ", " + gz));
            boolean canStrike = clr() >= 5 && hasLink("orbital") && t().getBoolean("orbReady");
            String sl = clr() < 5 ? "Strike: Level 5" : !hasLink("orbital") ? "Need Designator" : t().getBoolean("orbReady") ? "Orbital strike" : "Recharging";
            pill(g, x + 12 + hw, y + 24, hw, 16, sl, 0xFFA8323A, canStrike, () -> send("orbital_at:" + gx + ":" + gz, 0));
            hit(x + w - 14, y + 2, 12, 12, () -> mapTarget = false);
            text(g, "\u00D7", x + w - 11, y + 4, MUTED);
            y += 50;
        } else {
            small(g, "TAP THE MAP TO PICK A TARGET", SX + SW / 2, y, Terminal.DIM);
            y += 10;
        }
        // mission list
        for (int i = 0; i < ms.size(); i++) {
            CompoundTag m = ms.getCompound(i);
            if (m.getBoolean("hasPos")) y = guideRow(g, m, x, y, w);
        }
        return y + 6;
    }

    /** A mission with a location: name, distance, height, and a Guide toggle. */
    private int guideRow(GuiGraphics g, CompoundTag m, int x, int y, int w) {
        Minecraft mc = Minecraft.getInstance();
        int mx = m.getInt("x"), my = m.getInt("y"), mz = m.getInt("z");
        card(g, x, y, w, 24);
        g.fill(x, y, x + 2, y + 24, Terminal.map(missionColor(m.getString("type"))));
        text(g, ellipsize(m.getString("type").replace('_', ' '), w - 60), x + 6, y + 3, Terminal.GREEN);
        String info = mx + ", " + mz;
        if (mc.player != null) {
            int d = (int) Math.sqrt(mc.player.distanceToSqr(mx + 0.5, mc.player.getY(), mz + 0.5));
            int dy = my - mc.player.getBlockY();
            info += "  \u00B7  " + d + "M" + (dy > 3 ? "  \u25B2" + dy : dy < -3 ? "  \u25BC" + (-dy) : "");
        }
        small(g, info, x + 6 + (int) (font.width(info) * 0.375F), y + 14, Terminal.DIM);
        boolean on = Guide.guiding(mx, mz);
        String label = m.getString("type").replace('_', ' ') + " " + mx + ", " + mz;
        pill(g, x + w - 50, y + 5, 46, 14, on ? "Stop" : "Guide", on ? RED : Terminal.MID, true, () -> { if (on) Guide.stop(); else Guide.start(mx, my, mz, label); });
        return y + 27;
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
                case "RECOVER" -> RED; case "TERMINATE" -> 0xFF8E3BC4; case "CARE" -> 0xFFE58A2A; case "SUPPLY" -> 0xFF2F8FD8;
                case "EXPOSURE" -> 0xFFE5484D; case "DOCUMENT" -> 0xFF3FB8FF; case "FILES" -> 0xFFC8A030; case "BREACH" -> 0xFFFF3B3B; default -> 0xFFB04FE0;
            };
            boolean breach = type.equals("BREACH");
            String status = m.getString("status");
            boolean needed = breach && status.startsWith("!");
            if (needed) status = status.substring(1);
            String desc = m.getString("desc");
            boolean trial = type.equals("FOUNDERS_TRIAL");
            int x = SX + 6, w = SW - 12, pad = 7, iw = w - pad * 2;
            // measure first so nothing overlaps
            int descH = wrapHeight(desc, iw);
            List<String> meta = new ArrayList<>();
            if (m.getBoolean("hasPos")) meta.add("\u2316 " + m.getInt("x") + ", " + m.getInt("z"));
            long exp = m.getLong("expires");
            if (exp < Long.MAX_VALUE / 2) { long sec = Math.max(0, (exp - now) / 20); meta.add("\u23F1 " + clock((int) Math.min(Integer.MAX_VALUE, sec))); }
            int statusH = breach ? wrapHeight("\u25B6 " + status, iw) + 4 : 0;
            int h = pad + 16 + descH + 6 + (breach ? statusH : 10) + (meta.isEmpty() ? 0 : 12) + (trial ? 0 : 18) + pad - 2;
            card(g, x, y, w, h);
            rect(g, x, y + 6, 2, h - 12, chip);
            int cy = y + pad;
            String lbl = type.equals("RECOVER") && m.getInt("stage") == 1 ? "DELIVER" : type.replace('_', ' ');
            rrect(g, x + pad, cy, font.width(lbl) + 8, 11, 5, chip);
            text(g, lbl, x + pad + 4, cy + 2, 0xFFFFFFFF);
            String giver = "from O5-" + m.getInt("giver");
            small(g, giver, x + w - pad - (int) (font.width(giver) * 0.375F), cy + 3, 0xFF7A8794);
            cy += 16;
            cy += wrap(g, desc, x + pad, cy, iw, 0xFFE2E8EE) + 6;
            if (breach) {
                // what's being done about it (amber while it's in hand, red when they need you)
                cy += wrap(g, "\u25B6 " + status, x + pad, cy, iw, needed ? 0xFFFF5A5A : AMBER) + 4;
            } else {
                float pct = Mth.clamp(m.getInt("progress") / (float) Math.max(1, m.getInt("target")), 0, 1);
                String prog = m.getInt("progress") + "/" + m.getInt("target");
                int bw = iw - font.width(prog) - 6;
                rrect(g, x + pad, cy + 1, bw, 4, 2, 0xFF2A323D);
                rrect(g, x + pad, cy + 1, Math.max(4, (int) (bw * pct)), 4, 2, chip);
                text(g, prog, x + pad + iw - font.width(prog), cy - 1, 0xFFC8D2DC);
                cy += 10;
            }
            if (!meta.isEmpty()) {
                text(g, ellipsize(String.join("   ", meta), iw), x + pad, cy, 0xFF94A3B0);
                cy += 12;
            }
            final int idx = i;
            if (type.equals("SUPPLY") || type.equals("FILES")) {
                int half = (iw - 4) / 2;
                pill(g, x + pad, cy, half, 14, "Submit", 0xFF2E9E57, true, () -> send("submit", idx));
                pill(g, x + pad + half + 4, cy, half, 14, "Abandon", 0xFF4A2A30, true, () -> send("abandon", idx));
            } else if (!trial && m.getBoolean("hasPos")) {
                int half = (iw - 4) / 2, mx = m.getInt("x"), my2 = m.getInt("y"), mz = m.getInt("z"), eid = breach ? m.getInt("eid") : -1;
                boolean on = eid >= 0 ? Guide.guidingEntity(eid) : Guide.guiding(mx, mz);
                String gl = breach ? m.getString("designation") : type.replace('_', ' ') + " " + mx + ", " + mz;
                boolean deliver = type.equals("RECOVER") && m.getInt("stage") == 1;
                pill(g, x + pad, cy, half, 14, on ? "Stop guide" : deliver ? "Guide to site" : breach ? "Guide me to it" : "Guide me", on ? RED : 0xFF2E9E57, true, () -> {
                    if (on) Guide.stop();
                    else if (eid >= 0) Guide.startEntity(eid, mx, my2, mz, gl);
                    else Guide.start(mx, my2, mz, deliver ? "Deliver to site" : gl);
                });
                pill(g, x + pad + half + 4, cy, half, 14, breach ? "Dismiss" : "Abandon", 0xFF4A2A30, true, () -> send("abandon", idx));
            } else if (!trial) {
                pill(g, x + pad, cy, iw, 14, "Abandon", 0xFF4A2A30, true, () -> send("abandon", idx));
            }
            y += h + 6;
        }
        return y;
    }

    // ---------------------------------------------------------------- Phone (calls)

    private int phoneTab;

    /** Phone app without a council call: your number, contacts, number requests, nearby players. */
    private int drawContacts(GuiGraphics g, int y) {
        CompoundTag t = t();
        int x = SX + 6, w = SW - 12;
        text(g, "YOUR NUMBER", x, y, Terminal.DIM);
        String num = t.getString("myNumber");
        g.drawString(font, num, x + w - font.width(num), y, Terminal.BRIGHT, false);
        y += 13;
        if (!t.getString("lastCallText").isEmpty() && t.getLong("now") - t.getLong("lastCallAt") < 6000) {
            String body = t.getString("lastCallText");
            int bh = wrapHeight(body, w - 12) + 18;
            card(g, x, y, w, bh);
            small(g, ("LAST CALL · " + seatOf(t.getString("lastCallFrom"))).toUpperCase(Locale.ROOT), x + w / 2, y + 4, Terminal.DIM);
            wrap(g, body, x + 6, y + 13, w - 12, 0xFFE8F2EC);
            y += bh + 6;
        }
        ListTag contacts = t.getList("contactList", Tag.TAG_COMPOUND), reqs = t.getList("requestList", Tag.TAG_COMPOUND), near = t.getList("nearPlayers", Tag.TAG_COMPOUND);
        boolean council = rank() >= 6 || t.getBoolean("ascended");
        String[] tabs = council ? new String[]{"Council", "Contacts", "Nearby", "Requests" + (reqs.isEmpty() ? "" : " " + reqs.size())}
                : new String[]{"Contacts", "Nearby", "Requests" + (reqs.isEmpty() ? "" : " " + reqs.size())};
        int nt = tabs.length, tw = (w - 2 * (nt - 1)) / nt, off = council ? 1 : 0;
        if (!council && phoneTab >= 3) phoneTab = 0;
        int sel0 = phoneTab;
        for (int i = 0; i < nt; i++) {
            int bx = x + i * (tw + 2);
            boolean sel = sel0 == i, hov = over(bx, y, tw, 13);
            rbox(g, bx, y, tw, 13, 6, sel ? Terminal.SELECT : 0xFF08180E, sel || hov ? Terminal.SELECT : 0xFF163D24);
            String lb = tabs[i].toUpperCase(Locale.ROOT);
            g.pose().pushPose();
            g.pose().translate(bx + tw / 2F, y + 4, 0);
            g.pose().scale(0.75F, 0.75F, 1);
            g.drawString(font, lb, -font.width(lb) / 2, 0, sel ? Terminal.ON_SELECT : i == nt - 1 && !reqs.isEmpty() ? Terminal.AMBER : Terminal.GREEN, false);
            g.pose().popPose();
            final int idx = i;
            hit(bx, y, tw, 13, () -> { phoneTab = idx; scroll = 0; send("refresh", 0); });
        }
        y += 19;
        if (council && phoneTab == 0) return drawCouncilDirectory(g, x, y, w);
        int tab = phoneTab - off;
        if (tab == 0) {
            if (contacts.isEmpty()) { empty(g, y - 24, "No contacts", "Stand near another player and open Nearby to exchange numbers."); return y + 70; }
            for (int i = 0; i < contacts.size(); i++) {
                CompoundTag c = contacts.getCompound(i);
                boolean hov = over(x, y, w - 16, 22);
                rbox(g, x, y, w, 22, 8, hov ? 0xFF1E6B3A : 0xFF08180E, hov ? Terminal.BRIGHT : 0xFF163D24);
                int ink = Terminal.BRIGHT;
                g.drawString(font, ellipsize(c.getString("n"), w - 50), x + 5, y + 3, ink, false);
                String st = c.getBoolean("on") ? "ONLINE" : "OFFLINE";
                small(g, c.getString("num") + "  \u00B7  " + st, x + 5 + (int) (font.width(c.getString("num") + "  \u00B7  " + st) * 0.375F), y + 13, c.getBoolean("on") ? Terminal.MID : Terminal.DIM);
                final String th = "@" + c.getString("n");
                hit(x, y, w - 16, 22, () -> { thread = th; go(View.THREAD); });
                final int idx = i;
                boolean hx = over(x + w - 14, y + 5, 10, 12);
                g.drawString(font, "x", x + w - 11, y + 7, hx ? Terminal.RED : Terminal.DIM, false);
                hit(x + w - 14, y + 5, 10, 12, () -> send("contact_remove", idx));
                y += 25;
            }
            return y;
        }
        if (tab == 1) {
            if (near.isEmpty()) { empty(g, y - 24, "Nobody in range", "Players within 8 blocks show up here."); return y + 60; }
            for (int i = 0; i < near.size(); i++) {
                CompoundTag n = near.getCompound(i);
                card(g, x, y, w, 24);
                text(g, ellipsize(n.getString("n").toUpperCase(Locale.ROOT), w - 70), x + 5, y + 3, Terminal.GREEN);
                small(g, n.getInt("m") + "M AWAY" + (n.getBoolean("phone") ? "" : "  \u00B7  NO PHONE"), x + 5 + (int) (font.width(n.getInt("m") + "M AWAY" + (n.getBoolean("phone") ? "" : "  \u00B7  NO PHONE")) * 0.375F), y + 14, Terminal.DIM);
                final int id = n.getInt("id");
                boolean known = n.getBoolean("known");
                pill(g, x + w - 56, y + 5, 52, 14, known ? "Saved" : "Add", Terminal.MID, n.getBoolean("phone") && !known, () -> send("contact_req", id));
                y += 27;
            }
            return y;
        }
        if (reqs.isEmpty()) { empty(g, y - 24, "No requests", "When someone asks for your number it waits here."); return y + 60; }
        for (int i = 0; i < reqs.size(); i++) {
            CompoundTag r = reqs.getCompound(i);
            card(g, x, y, w, 38);
            text(g, ellipsize(r.getString("n").toUpperCase(Locale.ROOT), w - 10), x + 5, y + 3, Terminal.BRIGHT);
            small(g, "WANTS TO EXCHANGE NUMBERS", x + 5 + (int) (font.width("WANTS TO EXCHANGE NUMBERS") * 0.375F), y + 13, Terminal.DIM);
            final int idx = i;
            int half = (w - 14) / 2;
            pill(g, x + 5, y + 21, half, 13, "Accept", Terminal.MID, true, () -> send("contact_accept", idx));
            pill(g, x + 9 + half, y + 21, half, 13, "Decline", RED, true, () -> send("contact_decline", idx));
            y += 41;
        }
        return y;
    }

    private int drawCall(GuiGraphics g, int top) {
        CompoundTag t = t();
        int cx = SX + SW / 2;
        if (!t.contains("callText")) return drawContacts(g, top + 6 - scroll);
        if (t.getBoolean("callOutgoing")) {
            rect(g, SX, top, SW, SH, 0xFF0A120E);
            String from = t.getString("callFrom");
            long ms = System.currentTimeMillis();
            int y = top + 30;
            for (int i = 0; i < 3; i++) {
                float p = ((ms / 1100F) + i / 3F) % 1F;
                circle(g, cx, y + 22, 18 + (int) (p * 26), ((int) ((1 - p) * 70)) << 24 | 0x3AE07A);
            }
            circle(g, cx, y + 22, 18, seatColor(from));
            textC(g, seatOf(from).replace("O5-", ""), cx, y + 18, 0xFFFFFFFF);
            textC(g, ellipsize(nick(from), SW - 16), cx, y + 50, 0xFFFFFFFF);
            textC(g, number(t.getInt("callSeat")), cx, y + 61, 0xFF8AE0A8);
            String dots = ".".repeat((int) (ms / 400 % 4));
            textC(g, "Calling" + dots, cx, y + 78, 0xFFB8FFCC);
            pill(g, SX + 20, y + 100, SW - 40, 18, "Hang up", 0xFFC0383F, true, () -> send("hangup", 0));
            return y + 124;
        }
        rect(g, SX, top, SW, SH, 0xFF0A120E);
        if (false) {
            int y = top + 34;
            circle(g, cx, y + 20, 20, 0xFF1C3A28);
            textC(g, "☎", cx, y + 16, 0xFF7CFF9A);
            textC(g, "No incoming calls", cx, y + 52, 0xFFE0E6EC);
            List<FormattedCharSequence> lines = font.split(Component.literal(rank() >= 7
                    ? "The Council calls you for orders every few minutes."
                    : rank() >= 6 ? "As O5-13 you break the Council's tied votes. They'll call."
                    : "The Overseers don't call staff. Rise to the Council and they will."), SW - 24);
            for (int i = 0; i < lines.size(); i++) g.drawString(font, lines.get(i), cx - font.width(lines.get(i)) / 2, y + 66 + i * 10, Terminal.DIM, false);
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
            pill(g, SX + 10, y, SW - 20, 16, opts.getString(i), color, true, () -> { send("answer", idx); scroll = 0; });
            y += 20;
        }
        y += 4;
        pill(g, SX + 40, y, SW - 80, 14, "Hang up", 0xFFC0383F, true, () -> { send("hangup", 0); scroll = 0; });
        return y + 18;
    }

    // ---------------------------------------------------------------- Camera

    // ---------------------------------------------------------------- Gallery

    private CompoundTag photo(int id) {
        for (Tag e : t().getList("photos", Tag.TAG_COMPOUND)) if (((CompoundTag) e).getInt("id") == id) return (CompoundTag) e;
        return null;
    }

    private int drawGallery(GuiGraphics g, int y) {
        ListTag ps = t().getList("photos", Tag.TAG_COMPOUND);
        if (ps.isEmpty()) { empty(g, y, "No photos", "Open Camera: left-click to shoot, right-click to put it away."); return y + 80; }
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
    private String classFilter = "all";
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
        y += 20;
        // object class filter
        String[][] filters = {{"all", "All"}, {"safe", "Safe"}, {"euclid", "Euclid"}, {"keter", "Keter"}, {"thaumiel", "Thaumiel"}, {"other", "Other"}};
        int fx = x, fy = y;
        for (String[] f : filters) {
            int fw = (int) (font.width(f[1]) * 0.75F) + 10;
            if (fx + fw > x + w) { fx = x; fy += 13; }
            boolean sel = classFilter.equals(f[0]), fh = over(fx, fy, fw, 11);
            int tone = f[0].equals("all") || f[0].equals("other") ? Terminal.MID : Terminal.map(ArchiveFiles.classColor(f[0]));
            RAW = true;
            rrect(g, fx, fy, fw, 11, 5, sel ? tone : fh ? 0xFF1E6B3A : 0xFF163D24);
            if (!sel) rrect(g, fx + 1, fy + 1, fw - 2, 9, 4, 0xFF08180E);
            RAW = false;
            g.pose().pushPose();
            g.pose().translate(fx + fw / 2F, fy + 2, 0);
            g.pose().scale(0.75F, 0.75F, 1);
            String ft = f[1].toUpperCase(Locale.ROOT);
            g.drawString(font, ft, -font.width(ft) / 2, 0, sel ? Terminal.ON_SELECT : tone, false);
            g.pose().popPose();
            final String key = f[0];
            hit(fx, fy, fw, 11, () -> { classFilter = key; scroll = 0; });
            fx += fw + 3;
        }
        y = fy + 16;
        if (all.isEmpty()) { empty(g, y - 20, "Archive offline", "SCP Unleashed hasn't sent its dossiers yet. Rejoin the world."); return y + 60; }
        String q = query.toLowerCase(Locale.ROOT).trim();
        String qDigits = q.replaceAll("[^0-9]", "");
        int shown = 0;
        for (ArchiveFiles.Entry e : all) {
            if (!classFilter.equals("all")) {
                String ck = e.classKey();
                boolean main = ck.equals("safe") || ck.equals("euclid") || ck.equals("keter") || ck.equals("thaumiel");
                if (classFilter.equals("other") ? main : !ck.equals(classFilter)) continue;
            }
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
        if (shown == 0) { empty(g, y - 24, "No match", "No " + (classFilter.equals("all") ? "" : classFilter + " ") + "files" + (q.isEmpty() ? "" : " match \"" + query + "\"") + "."); y += 50; }
        return y;
    }

    private int drawFile(GuiGraphics g, int y) {
        ArchiveFiles.Entry e = ArchiveFiles.get(fileId);
        if (e == null) return y;
        int x = SX + 6, w = SW - 12;
        boolean omni = clr() >= 6;
        // the anomaly itself, slowly turning (SCP-096's image is withheld, as in its own dossier)
        boolean shy = e.id().endsWith("scp_096") || e.designation().equals("SCP-096");
        ArchiveModels.designation(e.id(), e.designation());
        {
            int mh = 96;
            rrect(g, x, y, w, mh, 10, 0xFF0F151C);
            for (int gy = y + 8; gy < y + mh; gy += 8) rect(g, x + 4, gy, w - 8, 1, 0x0FFFFFFF);
            rect(g, x + 20, y + mh - 14, w - 40, 1, 0x33FFFFFF);
            if (shy) {
                rect(g, x + w / 2 - 30, y + 30, 60, 24, 0xFF000000);
                small(g, "IMAGE WITHHELD", x + w / 2, y + 60, 0xFFFF8A8F);
            } else if (!ArchiveModels.draw(g, e.id(), x, y + 2, w, mh - 14)) {
                // never an empty gap: say plainly why there's no specimen to show
                small(g, ArchiveModels.lastProblem(e.id()), x + w / 2, y + mh / 2 - 6, MUTED);
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

    private int containTab, anomalyId = -1;

    private static String vertical(int dy) {
        return dy > 3 ? "  \u25B2" + dy + "M" : dy < -3 ? "  \u25BC" + (-dy) + "M" : "";
    }

    private int playerDy(int y) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? 0 : y - mc.player.getBlockY();
    }

    private CompoundTag anomaly(int id) {
        for (Tag tg : t().getList("nearby", Tag.TAG_COMPOUND)) if (((CompoundTag) tg).getInt("id") == id) return (CompoundTag) tg;
        return null;
    }

    private int drawContainment(GuiGraphics g, int y) {
        int x = SX + 6, w = SW - 12;
        ListTag list = t().getList("nearby", Tag.TAG_COMPOUND), rifts = t().getList("rifts", Tag.TAG_COMPOUND);
        ListTag reg = t().getList("registry", Tag.TAG_COMPOUND);
        String[] tabs = {"Nearby " + list.size(), "Registry" + (t().getInt("regLoose") > 0 ? " !" : ""), "Rifts" + (hasLink("rifts") ? " " + rifts.size() : "")};
        int tw = (w - 6) / 3;
        for (int i = 0; i < 3; i++) {
            int bx = x + i * (tw + 3);
            boolean sel = containTab == i, hov = over(bx, y, tw, 14);
            rbox(g, bx, y, tw, 14, 7, sel ? Terminal.SELECT : 0xFF08180E, sel || hov ? Terminal.SELECT : 0xFF163D24);
            String tl = ellipsize(tabs[i], tw - 6);
            g.drawString(font, tl, bx + tw / 2 - font.width(tl) / 2, y + 3, sel ? Terminal.ON_SELECT : Terminal.GREEN, false);
            final int idx = i;
            hit(bx, y, tw, 14, () -> { containTab = idx; scroll = 0; });
        }
        y += 20;
        if (containTab == 2) return drawRifts(g, y, rifts);
        if (containTab == 1) return drawRegistry(g, y, reg);
        String siteHere = t().getString("siteHere");
        small(g, siteHere.isEmpty() ? "NOT AT A SITE  \u00B7  CELLS HERE DON'T COUNT" : "AT " + siteHere.toUpperCase(Locale.ROOT), SX + SW / 2, y - 2, siteHere.isEmpty() ? AMBER : 0xFF5BE36F);
        y += 9;
        if (list.isEmpty()) { empty(g, y - 20, "All quiet", "No anomalies detected within 64m."); return y + 60; }
        // what's loose first, then what's safely held: two lists, not one
        List<CompoundTag> loose = new ArrayList<>(), held = new ArrayList<>();
        for (Tag tg : list) (((CompoundTag) tg).getBoolean("c") ? held : loose).add((CompoundTag) tg);
        List<CompoundTag> ordered = new ArrayList<>(loose);
        ordered.addAll(held);
        for (int i = 0; i < ordered.size(); i++) {
            CompoundTag a = ordered.get(i);
            if (i == 0 && !loose.isEmpty()) { small(g, "\u25B2 LOOSE  \u00B7  " + loose.size(), x + 4 + (int) (font.width("\u25B2 LOOSE  \u00B7  " + loose.size()) * 0.375F), y, 0xFFFF7A7A); y += 10; }
            if (i == loose.size()) { if (i > 0) y += 4; small(g, "\u25A0 CONTAINED  \u00B7  " + held.size(), x + 4 + (int) (font.width("\u25A0 CONTAINED  \u00B7  " + held.size()) * 0.375F), y, 0xFF7FD6FF); y += 10; }
            boolean contained = a.getBoolean("c"), containable = a.getBoolean("k");
            int h = 30, id = a.getInt("id");
            boolean hov = over(x, y, w, h);
            rbox(g, x, y, w, h, 8, hov ? 0xFF123A20 : 0xFF08180E, hov ? Terminal.MID : 0xFF163D24);
            circle(g, x + 10, y + 15, 4, contained ? TEAL : containable ? RED : 0xFF8A96A2);
            String nm = a.contains("dk") ? Component.translatable(a.getString("dk")).getString() : a.getString("d");
            text(g, ellipsize(nm, w - 76), x + 20, y + 5, 0xFFFFFFFF);
            String cls = a.getString("o");
            int cc = cls.equals("Safe") ? 0xFF3AA860 : cls.equals("Euclid") ? 0xFFC8A030 : 0xFFC0383F;
            rrect(g, x + w - 8 - font.width(cls) - 6, y + 4, font.width(cls) + 6, 10, 4, cc);
            text(g, cls, x + w - 5 - font.width(cls) - 6, y + 5, 0xFFFFFFFF);
            String status = !a.getString("cell").isEmpty() ? a.getString("cell") : contained ? (a.getString("n").isEmpty() ? "In cell at " + a.getString("site") : a.getString("n")) : !a.getString("site").isEmpty() ? (containable ? "Loose inside " + a.getString("site") : "Loose, stasis-immune") : containable ? "Loose, outside any site" : "Loose, stasis-immune";
            small(g, status.toUpperCase(Locale.ROOT), x + 20 + (int) (font.width(status.toUpperCase(Locale.ROOT)) * 0.375F), y + 18, contained ? 0xFF7FD6FF : containable ? 0xFFFF7A7A : 0xFF9AA6B2);
            String dist = a.getInt("m") + "m" + vertical(playerDy(a.getInt("y")));
            small(g, dist, x + w - 8 - (int) (font.width(dist) * 0.375F), y + 18, Guide.guidingEntity(id) ? Terminal.BRIGHT : 0xFF7A8794);
            hit(x, y, w, h, () -> { anomalyId = id; go(View.ANOMALY); });
            y += h + 4;
        }
        small(g, "TAP AN ANOMALY FOR GUIDANCE", SX + SW / 2, y + 2, Terminal.DIM);
        return y + 12;
    }

    /** The containment registry: everything held, loose or put down at the Foundation's sites, and designating new cells. */
    private int drawRegistry(GuiGraphics g, int y, ListTag reg) {
        int x = SX + 6, w = SW - 12;
        int held = t().getInt("regHeld"), loose = t().getInt("regLoose"), gone = t().getInt("regGone");
        // the tally
        card(g, x, y, w, 30);
        int cw = w / 3;
        String[][] stat = {{String.valueOf(held), "HELD"}, {String.valueOf(loose), "LOOSE"}, {String.valueOf(t().getInt("regCells")), "CELLS"}};
        int[] col = {0xFF7FD6FF, loose > 0 ? 0xFFFF6B6B : 0xFF7A8794, 0xFFB8C6D6};
        for (int i = 0; i < 3; i++) {
            int cx = x + cw * i + cw / 2;
            g.drawString(font, stat[i][0], cx - font.width(stat[i][0]) / 2, y + 5, Terminal.map(col[i]), false);
            small(g, stat[i][1], cx, y + 19, MUTED);
        }
        y += 34;
        int empty = t().getInt("regEmpty");
        if (empty > 0) { small(g, empty + " DESIGNATED CELL" + (empty == 1 ? "" : "S") + " STAND EMPTY", SX + SW / 2, y, AMBER); y += 9; }
        // designating the room you're standing in
        String siteHere = t().getString("siteHere");
        pill(g, x, y, w, 16, siteHere.isEmpty() ? "Cells only count inside a site" : "Designate this room as a cell", 0xFF156E8F, !siteHere.isEmpty(), () -> send("cell_designate", 0));
        y += 20;
        if (reg.isEmpty()) {
            empty(g, y - 16, "Registry empty", "Anomalies held at your sites are entered automatically. Breaches are detected the same way.");
            return y + 60;
        }
        for (Tag tg : reg) {
            CompoundTag e = (CompoundTag) tg;
            int st = e.getInt("st");
            int h = 30;
            boolean isLoose = st == 1, dead = st == 2;
            rbox(g, x, y, w, h, 8, isLoose ? 0xFF2A0C0E : 0xFF08180E, isLoose ? 0xFF7A2328 : 0xFF163D24);
            circle(g, x + 10, y + 15, 4, isLoose ? RED : dead ? 0xFF5A6470 : TEAL);
            String nm = e.getString("dk").isEmpty() ? e.getString("d") : Component.translatable(e.getString("dk")).getString();
            String title = e.getString("d") + (nm.equals(e.getString("d")) ? "" : " \u00B7 " + nm);
            text(g, ellipsize(title, w - 70), x + 20, y + 5, dead ? 0xFF8A96A2 : 0xFFFFFFFF);
            String cls = e.getString("o");
            if (!cls.isEmpty()) {
                int cc = cls.equals("Safe") ? 0xFF3AA860 : cls.equals("Euclid") ? 0xFFC8A030 : 0xFFC0383F;
                rrect(g, x + w - 8 - font.width(cls) - 6, y + 4, font.width(cls) + 6, 10, 4, cc);
                text(g, cls, x + w - 5 - font.width(cls) - 6, y + 5, 0xFFFFFFFF);
            }
            int days = e.getInt("days"), br = e.getInt("br");
            String ord = e.getString("ord");
            String line = switch (st) {
                case 1 -> (e.getBoolean("w") ? "UNREGISTERED, LOOSE AT " : "LOOSE AT ") + e.getString("site") + " \u00B7 " + (days == 0 ? "TODAY" : days + "D")
                        + (ord.equals("hunt") ? " \u00B7 RECAPTURE TEAM SENT" : ord.equals("terminate") ? " \u00B7 TERMINATION ORDERED" : br > 0 ? " \u00B7 " + br + " BREACH" + (br == 1 ? "" : "ES") : "");
                case 2 -> "NEUTRALISED \u00B7 " + e.getString("k");
                default -> (e.getString("cell").isEmpty() ? e.getString("site") : e.getString("cell")) + " \u00B7 " + (days == 0 ? "TODAY" : days + " DAY" + (days == 1 ? "" : "S"))
                        + (ord.equals("transfer") ? " \u00B7 TRANSFER ORDERED" : br > 0 ? " \u00B7 " + br + " BREACH" + (br == 1 ? "" : "ES") : "");
            };
            line = ellipsize(line.toUpperCase(Locale.ROOT), (int) ((w - 30) / 0.75F));
            small(g, line, x + 20 + (int) (font.width(line) * 0.375F), y + 18, isLoose ? 0xFFFF7A7A : dead ? 0xFF7A8794 : 0xFF7FD6FF);
            if (e.getInt("seen") >= 2 && st == 0) small(g, "LAST CHECKED " + e.getInt("seen") + "D AGO", x + w - 8 - (int) (font.width("LAST CHECKED " + e.getInt("seen") + "D AGO") * 0.375F), y + 25, AMBER);
            y += h + 4;
        }
        small(g, "THE COUNCIL DEBATES BREACHES AND TRANSFERS", SX + SW / 2, y + 2, Terminal.DIM);
        return y + 12;
    }

    private int drawRifts(GuiGraphics g, int y, ListTag rifts) {
        int x = SX + 6, w = SW - 12;
        if (!hasLink("rifts")) {
            empty(g, y - 20, "No rift sensor", "Link a Rift Detector to your phone in Settings to find rifts.");
            pill(g, x, y + 56, w, 16, "Open Settings", 0xFF2E9E57, true, () -> go(View.SETTINGS));
            return y + 80;
        }
        if (rifts.isEmpty()) { empty(g, y - 20, "No rifts", "Nothing open within 128m. You'll get a message when one appears."); return y + 60; }
        for (Tag tg : rifts) {
            CompoundTag r = (CompoundTag) tg;
            int clr = t().getInt("rankClr");
            boolean canD = clr >= 4 || t().getBoolean("ascended"), canMtf = clr >= 5 || t().getBoolean("ascended");
            boolean sent = false;
            for (Tag q : t().getList("riftTeams", Tag.TAG_INT)) if (((net.minecraft.nbt.IntTag) q).getAsInt() == r.getInt("id")) sent = true;
            int h = canD ? 58 : 40, id = r.getInt("id");
            card(g, x, y, w, h);
            int colour = 0xFF000000 | r.getInt("colour");
            RAW = true;
            rrect(g, x + 1, y + 6, 3, h - 12, 1, colour);
            RAW = false;
            String rarity = r.getString("rarity");
            String nm = Component.translatable(r.getString("dk")).getString();
            String title = (rarity.isEmpty() ? "" : rarity.substring(0, 1).toUpperCase(Locale.ROOT) + rarity.substring(1) + " ") + nm;
            g.drawString(font, ellipsize(title, w - 60), x + 9, y + 5, 0xFF000000 | Terminal.map(colour) & 0xFFFFFF | 0xFF000000, false);
            String dist = r.getInt("m") + "m" + vertical(playerDy(r.getInt("y")));
            small(g, dist, x + w - 8 - (int) (font.width(dist) * 0.375F), y + 7, 0xFF7A8794);
            float ex = r.getFloat("ex");
            int bw = w - 70;
            rrect(g, x + 9, y + 19, bw, 4, 2, 0xFF27303A);
            if (ex > 0) rrect(g, x + 9, y + 19, Math.max(4, (int) (bw * ex)), 4, 2, 0xFF5BE36F);
            small(g, !r.getBoolean("can") ? "SETTLING" : ex > 0 ? (int) (ex * 100) + "% DRAWN OUT" : "USE A RIFT EXTRACTOR", x + 9 + (int) (font.width(!r.getBoolean("can") ? "SETTLING" : ex > 0 ? (int) (ex * 100) + "% DRAWN OUT" : "USE A RIFT EXTRACTOR") * 0.375F), y + 27, MUTED);
            boolean on = Guide.guidingEntity(id);
            final int rx = r.getInt("x"), ry = r.getInt("y"), rz = r.getInt("z");
            final String label = title;
            pill(g, x + w - 52, y + 18, 46, 14, on ? "Stop" : "Guide", on ? RED : 0xFF2E9E57, true, () -> { if (on) Guide.stop(); else Guide.startEntity(id, rx, ry, rz, label); });
            if (canD) {
                int half = (w - 22) / 2;
                if (sent) small(g, "TEAM EN ROUTE", x + w / 2, y + 43, 0xFF5BE36F);
                else {
                    pill(g, x + 9, y + 38, half, 14, "Send D-Class", 0xFF2E9E57, true, () -> send("rift_dclass", id));
                    pill(g, x + 13 + half, y + 38, half, 14, "Send MTF", 0xFF2E9E57, canMtf, () -> send("rift_mtf", id));
                }
            }
            y += h + 4;
        }
        return y;
    }

    /** One anomaly: turning 3D view, status, distance, and guidance (plus an orbital strike at Level 5). */
    private int drawAnomaly(GuiGraphics g, int y) {
        CompoundTag a = anomaly(anomalyId);
        int x = SX + 6, w = SW - 12;
        if (a == null) { empty(g, y, "Out of range", "It's no longer within 64m of you."); return y + 70; }
        int mh = 104;
        RAW = true;
        rrect(g, x, y, w, mh, 10, 0xFF163D24);
        rrect(g, x + 1, y + 1, w - 2, mh - 2, 9, 0xFF04100A);
        RAW = false;
        for (int gy = y + 10; gy < y + mh - 4; gy += 10) g.fill(x + 6, gy, x + w - 6, gy + 1, 0x1430FF70);
        g.fill(x + 24, y + mh - 16, x + w - 24, y + mh - 15, 0x4430FF70);
        boolean shy = a.getString("type").endsWith("scp096_shy_guy");
        if (shy) {
            g.fill(x + w / 2 - 30, y + 34, x + w / 2 + 30, y + 58, 0xFF000000);
            small(g, "IMAGE WITHHELD", x + w / 2, y + 64, 0xFFFF8A8F);
        } else if (!ArchiveModels.drawType(g, a.getString("type"), x, y + 2, w, mh - 16)) {
            small(g, "NO IMAGE", x + w / 2, y + mh / 2 - 4, MUTED);
        }
        y += mh + 6;
        String nm = Component.translatable(a.getString("dk")).getString();
        text(g, ellipsize(nm, w - 60), x + 2, y, 0xFFFFFFFF);
        String cls = a.getString("o");
        int cc = cls.equals("Safe") ? 0xFF3AA860 : cls.equals("Euclid") ? 0xFFC8A030 : 0xFFC0383F;
        rrect(g, x + w - font.width(cls) - 8, y - 1, font.width(cls) + 6, 10, 4, cc);
        text(g, cls, x + w - font.width(cls) - 5, y, 0xFFFFFFFF);
        y += 12;
        boolean contained = a.getBoolean("c");
        int dy = playerDy(a.getInt("y"));
        String where = a.getInt("m") + "m away  \u00B7  " + a.getInt("x") + ", " + a.getInt("y") + ", " + a.getInt("z");
        small(g, where.toUpperCase(Locale.ROOT), SX + SW / 2, y, MUTED);
        y += 9;
        String vert = dy > 3 ? "\u25B2 " + dy + "M ABOVE YOU" : dy < -3 ? "\u25BC " + (-dy) + "M BELOW YOU" : "ON YOUR LEVEL";
        small(g, vert + "  \u00B7  " + (contained ? "IN CELL" : "LOOSE"), SX + SW / 2, y, contained ? 0xFF7FD6FF : 0xFFFF7A7A);
        y += 12;
        int id = a.getInt("id");
        boolean on = Guide.guidingEntity(id);
        final int ax = a.getInt("x"), ay = a.getInt("y"), az = a.getInt("z");
        final String label = nm;
        pill(g, x, y, w, 16, on ? "Stop guidance" : "Guide me there", on ? RED : 0xFF2E9E57, true, () -> { if (on) Guide.stop(); else Guide.startEntity(id, ax, ay, az, label); });
        y += 20;
        if (clr() >= 5) {
            boolean ready = t().getBoolean("orbReady") && hasLink("orbital");
            pill(g, x, y, w, 16, hasLink("orbital") ? (ready ? "Orbital strike on it" : "Satellite not ready") : "Orbital: link a ZENITH Designator", 0xFFA8323A, ready,
                    () -> send("orbital_at:" + ax + ":" + az, 0));
            y += 20;
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
        for (int i = 0; i < Math.min(2, title.size()); i++) g.drawString(font, title.get(i), x + 7, y + 52 + i * 10, Terminal.map(asc ? 0xFF8A2BE2 : 0xFF1C2430), false);
        String clrText = clrLabel(clr());
        g.pose().pushPose();
        g.pose().translate(x + w - 8, y + 22, 0);
        g.pose().scale(1.6F, 1.6F, 1);
        g.drawString(font, clrText, -font.width(clrText), 0, Terminal.map(band), false);
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
        if (carried <= fused && t.getBoolean("creative") && fused < 6) {
            card(g, x, y, w, 26);
            text(g, "Creative", x + 8, y + 9, AMBER);
            pill(g, x + w - 96, y + 6, 90, 14, "Fuse " + cardName(fused + 1), AMBER, 0xFF1A1206, true, () -> send("fuse", 0));
            y += 30;
        }
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
        // guide panel position: pick a spot on a little screen
        label(g, "Guide arrow position", x + 2, y);
        y += 12;
        card(g, x, y, w, 62);
        int gw = 72, gh = 44, gx0 = x + 8, gy0 = y + 9;
        RAW = true;
        rrect(g, gx0, gy0, gw, gh, 3, 0xFF2A7A44);
        rrect(g, gx0 + 1, gy0 + 1, gw - 2, gh - 2, 2, 0xFF04100A);
        RAW = false;
        for (int a = 0; a < 9; a++) {
            int cw2 = 18, ch2 = 10, ax = gx0 + 4 + (a % 3) * ((gw - 8 - cw2) / 2), ay = gy0 + 4 + (a / 3) * ((gh - 8 - ch2) / 2);
            boolean sel = Guide.anchor() == a, hv = over(ax, ay, cw2, ch2);
            rbox(g, ax, ay, cw2, ch2, 2, sel ? Terminal.SELECT : hv ? 0xFF1E6B3A : 0xFF08180E, sel || hv ? Terminal.SELECT : 0xFF163D24);
            final int pick = a;
            hit(ax, ay, cw2, ch2, () -> Guide.setAnchor(pick));
        }
        String[] where = {"Top left", "Top", "Top right", "Middle left", "Centre", "Middle right", "Bottom left", "Bottom", "Bottom right"};
        text(g, where[Guide.anchor()], gx0 + gw + 8, gy0 + 8, 0xFFE8EEF4);
        small(g, "WHERE THE ARROW SHOWS", gx0 + gw + 8 + (int) (font.width("WHERE THE ARROW SHOWS") * 0.375F), gy0 + 22, MUTED);
        small(g, "ON YOUR SCREEN", gx0 + gw + 8 + (int) (font.width("ON YOUR SCREEN") * 0.375F), gy0 + 30, MUTED);
        y += 68;

        // Foundation sites
        label(g, "Foundation sites", x + 2, y);
        y += 12;
        String here = t.getString("siteHere");
        card(g, x, y, w, 30);
        text(g, here.isEmpty() ? "Not on Foundation ground" : "You're at " + here, x + 8, y + 5, here.isEmpty() ? AMBER : 0xFF5BE36F);
        small(g, here.isEmpty() ? "CELLS ONLY COUNT INSIDE A SITE" : "CELLS HERE COUNT AS CONTAINMENT", x + 8 + (int) (font.width(here.isEmpty() ? "CELLS ONLY COUNT INSIDE A SITE" : "CELLS HERE COUNT AS CONTAINMENT") * 0.375F), y + 18, MUTED);
        y += 34;
        ListTag siteList = t.getList("sites", Tag.TAG_COMPOUND);
        for (Tag st0 : siteList) {
            CompoundTag st = (CompoundTag) st0;
            card(g, x, y, w, 24);
            text(g, ellipsize(st.getString("n"), w - 60), x + 8, y + 3, st.getBoolean("here") ? 0xFF5BE36F : 0xFFE8EEF4);
            String info = st.getInt("r") + "m @ " + st.getInt("x") + ", " + st.getInt("z") + "  \u00B7  " + st.getString("on");
            small(g, ellipsize(info, (int) ((w - 60) / 0.75F)), x + 8 + (int) (font.width(ellipsize(info, (int) ((w - 60) / 0.75F))) * 0.375F), y + 14, MUTED);
            final int si = st.getInt("i");
            if (st.getBoolean("mine") || rank() >= 6) pill(g, x + w - 46, y + 5, 40, 14, "Remove", RED, true, () -> send("site_remove", si));
            y += 27;
        }
        if (here.isEmpty()) {
            if (rank() >= 3 || t.getBoolean("ascended")) {
                small(g, "DECLARE A SITE CENTRED HERE:", SX + SW / 2, y + 1, MUTED);
                y += 10;
                int bw2 = (w - 9) / 4;
                int[] radii = {32, 48, 64, 96};
                for (int ri = 0; ri < 4; ri++) {
                    final int idx = ri;
                    pill(g, x + ri * (bw2 + 3), y, bw2, 14, radii[ri] + "m", 0xFF2E9E57, true, () -> send("site_declare", idx));
                }
                y += 20;
            } else {
                small(g, "LEVEL 3 CAN DECLARE SITES", SX + SW / 2, y + 1, MUTED);
                y += 12;
            }
        }
        y += 4;

        // linked SCP Unleashed devices
        label(g, "Linked devices", x + 2, y);
        y += 12;
        ListTag lk = t.getList("links", Tag.TAG_STRING), dv = t.getList("devices", Tag.TAG_INT);
        Set<String> links = new HashSet<>();
        for (int i = 0; i < lk.size(); i++) links.add(lk.getString(i));
        Set<Integer> carrying = new HashSet<>();
        for (Tag e : dv) carrying.add(((net.minecraft.nbt.IntTag) e).getAsInt());
        String[][] devs = {
                {"orbital_designator", "orbital", "Orbital strikes from Network and Map"},
                {"mtf_signal_radio", "mtf", "Call MTF squads from Command"},
                {"rift_detector", "rifts", "Rift alerts, tracking and guidance"},
                {"camera_tablet", "cameras", "Anomalies on the Map via security feeds"},
                {"facility_tablet", "network", "Facility network status"}};
        for (int i = 0; i < devs.length; i++) {
            boolean on = links.contains(devs[i][1]), has = carrying.contains(i);
            String name = net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("scp_unleashed", devs[i][0]))
                    .map(it -> new net.minecraft.world.item.ItemStack(it).getHoverName().getString()).orElse(devs[i][0]);
            card(g, x, y, w, 30);
            net.minecraft.world.item.Item it = net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("scp_unleashed", devs[i][0])).orElse(null);
            if (it != null) g.renderItem(new net.minecraft.world.item.ItemStack(it), x + 5, y + 7);
            text(g, ellipsize(name, w - 78), x + 25, y + 5, on ? Terminal.BRIGHT : 0xFFE8EEF4);
            small(g, ellipsize(devs[i][2], (int) ((w - 78) / 0.75F)), x + 25 + (int) (font.width(ellipsize(devs[i][2], (int) ((w - 78) / 0.75F))) * 0.375F), y + 17, MUTED);
            final int idx = i;
            if (on) text(g, "Linked", x + w - 8 - font.width("Linked"), y + 11, 0xFF5BE36F);
            else {
                boolean cr = t.getBoolean("creative");
                pill(g, x + w - 48, y + 8, 42, 14, has || cr ? "Link" : "Need it", 0xFF2E9E57, has || cr, () -> send("link", idx));
            }
            y += 33;
        }
        y += 4;
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
        String[] tabs = {"MTF", "D-Class", "Orders"};
        int tx = SX + 6;
        for (int i = 0; i < tabs.length; i++) {
            int tw = font.width(tabs[i]) + 14;
            final int idx = i;
            pill(g, tx, y, tw, 14, tabs[i], commandTab == i ? TEAL : 0xFF1A222C, commandTab == i ? 0xFF0B1118 : 0xFFC8D2DC, true, () -> { commandTab = idx; scroll = 0; });
            tx += tw + 4;
        }
        y += 20;
        int x = SX + 6, w = SW - 12;
        if (commandTab == 0 && !hasLink("mtf")) {
            empty(g, y - 20, "No MTF uplink", "Link an MTF Signal Radio to your phone in Settings to call squads.");
            pill(g, x, y + 56, w, 16, "Open Settings", 0xFF2E9E57, true, () -> go(View.SETTINGS));
            return y + 80;
        }
        if (commandTab == 0) {
            long cd = t().getLong("mtfCd");
            card(g, x, y, w, 30);
            text(g, "Mobile Task Force", x + 8, y + 5, 0xFFFFFFFF);
            text(g, cd > 0 ? "Regrouping: " + clock((int) cd) : "Command is ready", x + 8, y + 17, cd > 0 ? AMBER : 0xFF5BE36F);
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
            boolean ready = t.getBoolean("orbReady"), complete = t.getBoolean("orbComplete");
            int parts = t.getInt("orbParts"), wait = t.getInt("orbWait");
            card(g, x, y, w, 64);
            circle(g, x + 22, y + 22, 14, 0xFF10222A);
            ring(g, x + 22, y + 22, 14, ready ? 0xFF5BE36F : AMBER);
            circle(g, x + 22, y + 22, 3, ready ? 0xFF5BE36F : AMBER);
            text(g, !complete ? "Array incomplete" : ready ? "Satellite ready" : "Recharging", x + 44, y + 9, ready ? 0xFF5BE36F : AMBER);
            text(g, !complete ? parts + " parts in orbit" : ready ? "Pick a target" : "Ready in " + clock(wait), x + 44, y + 21, 0xFF9FB1C2);
            int half = (w - 16) / 2;
            if (hasLink("orbital")) {
                pill(g, x + 6, y + 42, half, 16, "Crosshair", 0xFFA8323A, ready, () -> send("orbital_strike", 0));
                pill(g, x + 10 + half, y + 42, half, 16, "Map", 0xFF2E9E57, true, () -> go(View.MAP));
            } else {
                pill(g, x + 6, y + 42, w - 12, 16, "Link a ZENITH Designator", 0xFF2E9E57, true, () -> go(View.SETTINGS));
            }
            y += 70;
        }
        label(g, "Facility network", x + 2, y);
        y += 12;
        int hubs = t.getInt("hubs");
        card(g, x, y, w, 30);
        text(g, "Network hubs registered", x + 8, y + 5, 0xFFE8EEF4);
        text(g, hubs == -2 ? "Link a Facility Tablet in Settings" : hubs < 0 ? "unavailable" : String.valueOf(hubs), x + 8, y + 17, hubs > 0 ? TEAL : MUTED);
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
            composeFocused = false;
            staffSearch = false;
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
        if (view == View.THREAD && composeFocused && c >= ' ' && compose.length() < 120) {
            compose += c;
            return true;
        }
        if (view == View.STAFF && staffSearch && c >= ' ' && staffQuery.length() < 24) { staffQuery += c; scroll = 0; return true; }
        if (view == View.ARCHIVE && searchFocused && c >= ' ' && query.length() < 32) {
            query += c;
            scroll = 0;
            return true;
        }
        return super.charTyped(c, mods);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (view == View.THREAD && composeFocused) {
            if (key == 259) { if (!compose.isEmpty()) compose = compose.substring(0, compose.length() - 1); return true; }
            if (key == 257 || key == 335) { sendText(); return true; }        // Enter sends
            if (key == 256) { composeFocused = false; return true; }          // Esc leaves the box
            if (minecraft != null && (minecraft.options.keyInventory.matches(key, scan) || minecraft.options.keyChat.matches(key, scan))) return true;
        }
        if (view == View.STAFF && staffSearch) {
            if (key == 259) { if (!staffQuery.isEmpty()) staffQuery = staffQuery.substring(0, staffQuery.length() - 1); return true; }
            if (key == 256 || key == 257) { staffSearch = false; return true; }
            if (minecraft != null && minecraft.options.keyInventory.matches(key, scan)) return true;
        }
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

    // ---------------------------------------------------------------- Staff (the workforce directory, hiring, org chart, registry)

    /** The directory, as last sent by the server (refreshed while the app is open). */
    public static CompoundTag STAFF = new CompoundTag();
    private int staffTab;
    private String staffSel = "", staffQuery = "", staffSite = "", confirm = "";
    private int hireSel = -1, postSite, postRole;
    private boolean staffSearch;
    private long confirmAt;
    private static final String[] POST_ROLES = {"Researcher", "Security", "Medical", "Technician", "Administration", "Janitorial"};
    private static final int[] POST_ROLE_ORD = {0, 1, 2, 3, 4, 5};

    /** Text cut to a width; point at it to read the rest. */
    private void fit(GuiGraphics g, String s, int x, int y, int w, int c) {
        String shown = ellipsize(s, Math.max(8, w));
        g.drawString(font, shown, x, y, Terminal.map(c), false);
        if (!shown.equals(s) && over(x, y - 1, w, 10)) hover = s;
    }

    /** Small (3/4) left-aligned text, cut to a width. */
    private void tiny(GuiGraphics g, String s, int x, int y, int w, int c) {
        String shown = ellipsize(s, (int) (w / 0.75F));
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.75F, 0.75F, 1);
        g.drawString(font, shown, 0, 0, Terminal.map(c), false);
        g.pose().popPose();
        if (!shown.equals(s) && over(x, y - 1, w, 8)) hover = s;
    }

    private void meter(GuiGraphics g, int x, int y, int w, int v, int max, int c) {
        rect(g, x, y, w, 4, 0xFF10261A);
        rect(g, x, y, Math.max(1, w * Math.max(0, Math.min(max, v)) / max), 4, c);
    }

    private void act(String a) { send("wf:" + a, 0); }

    /** A button that needs a second press within three seconds. */
    private void sure(GuiGraphics g, int x, int y, int w, String label, String key, Runnable r) {
        boolean armed = confirm.equals(key) && System.currentTimeMillis() - confirmAt < 3000;
        pill(g, x, y, w, 14, armed ? "Press again to confirm" : label, 0xFFB03030, true, () -> {
            if (armed) { confirm = ""; r.run(); } else { confirm = key; confirmAt = System.currentTimeMillis(); }
        });
    }

    private List<CompoundTag> people() {
        List<CompoundTag> out = new ArrayList<>();
        String q = staffQuery.toLowerCase(Locale.ROOT);
        for (Tag t : STAFF.getList("people", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            if (!staffSite.isEmpty() && !c.getString("site").equals(staffSite)) continue;
            if (!q.isEmpty() && !(c.getString("name") + " " + c.getString("job") + " " + c.getString("head") + " " + c.getString("site")).toLowerCase(Locale.ROOT).contains(q)) continue;
            out.add(c);
        }
        return out;
    }

    private CompoundTag person(String id) {
        for (Tag t : STAFF.getList("people", Tag.TAG_COMPOUND)) if (((CompoundTag) t).getString("id").equals(id)) return (CompoundTag) t;
        return null;
    }

    private int drawStaff(GuiGraphics g, int y, int top) {
        if (!STAFF.contains("people")) { empty(g, y, "Connecting...", "Fetching the personnel directory."); return y + 80; }
        String[] tabs = {"Staff", "Hire", "Org", "Log"};
        int x, w;
        if (desktop) {
            // a sidebar, like a real terminal program
            int sx = SX + 6, sw = 80, sy = top + 6;
            rect(g, sx - 6, top, sw + 10, SY + SH - NAV - top, 0xFF061009);
            rect(g, sx + sw + 3, top, 1, SY + SH - NAV - top, Terminal.LINE);
            String[] full = {"Directory", "Hiring", "Org chart", "Registry"};
            for (int i = 0; i < full.length; i++) {
                final int idx = i;
                boolean on = staffTab == i;
                rbox(g, sx, sy, sw, 16, 2, on ? Terminal.SELECT : over(sx, sy, sw, 16) ? 0xFF0E3A1E : Terminal.PANEL, on ? Terminal.SELECT : Terminal.LINE);
                g.drawString(font, full[i], sx + 6, sy + 4, on ? Terminal.ON_SELECT : Terminal.BRIGHT, false);
                int badge = i == 1 ? waitingShortlists() : 0;
                if (badge > 0) g.drawString(font, String.valueOf(badge), sx + sw - 6 - font.width(String.valueOf(badge)), sy + 4, Terminal.AMBER, false);
                hit(sx, sy, sw, 16, () -> { staffTab = idx; scroll = 0; staffSel = ""; });
                sy += 20;
            }
            // the pulse, always visible
            sy += 4;
            g.drawString(font, "PULSE", sx, sy, Terminal.MID, false);
            sy += 11;
            int n = STAFF.getList("people", Tag.TAG_COMPOUND).size();
            g.drawString(font, n + " on staff", sx, sy, Terminal.BRIGHT, false);
            sy += 11;
            g.drawString(font, "Morale " + STAFF.getInt("morale") + "%", sx, sy, Terminal.MID, false);
            sy += 10;
            meter(g, sx, sy, sw, STAFF.getInt("morale"), 100, STAFF.getInt("morale") < 35 ? RED : 0xFF5CFF8A);
            sy += 9;
            tiny(g, "7 days: " + STAFF.getInt("births") + " births, " + STAFF.getInt("weddings") + " weddings", sx, sy, sw, MUTED);
            tiny(g, STAFF.getInt("hired") + " hired, " + STAFF.getInt("deaths") + " lost", sx, sy + 8, sw, MUTED);
            x = SX + 96;
            w = SW - 102;
        } else {
            x = SX + 6;
            w = SW - 12;
            int tx = x;
            for (int i = 0; i < tabs.length; i++) {
                int tw = (w - 9) / 4;
                final int idx = i;
                String lbl = tabs[i] + (i == 1 && waitingShortlists() > 0 ? " " + waitingShortlists() : "");
                pill(g, tx, y, tw, 14, lbl, staffTab == i ? TEAL : 0xFF1A222C, staffTab == i ? 0xFF0B1118 : 0xFFC8D2DC, true, () -> { staffTab = idx; scroll = 0; staffSel = ""; });
                tx += tw + 3;
            }
            y += 20;
        }
        return switch (staffTab) {
            case 1 -> drawHiring(g, x, y, w);
            case 2 -> drawOrg(g, x, y, w);
            case 3 -> drawRegistry(g, x, y, w);
            default -> drawDirectory(g, x, y, w, top);
        };
    }

    private int waitingShortlists() {
        int n = 0;
        for (Tag t : STAFF.getList("vac", Tag.TAG_COMPOUND)) if (((CompoundTag) t).getString("state").equals("direct")) n++;
        return n + STAFF.getList("walkins", Tag.TAG_COMPOUND).size();
    }

    private int drawDirectory(GuiGraphics g, int x, int y, int w, int top) {
        CompoundTag sel = staffSel.isEmpty() ? null : person(staffSel);
        if (sel == null) staffSel = "";
        int listW = desktop ? Math.min(176, w / 2) : w;
        if (!desktop && sel != null) {
            pill(g, x, y, 70, 14, "< All staff", 0xFF1A222C, true, () -> staffSel = "");
            return drawPerson(g, sel, x, y + 20, w);
        }
        // search and site filter
        boolean hov = over(x, y, listW, 14);
        rbox(g, x, y, listW, 14, 2, staffSearch ? 0xFF0E3A1E : Terminal.PANEL, staffSearch || hov ? Terminal.MID : Terminal.LINE);
        String q = staffQuery.isEmpty() && !staffSearch ? "Search name, job, site..." : staffQuery + (staffSearch && (System.currentTimeMillis() / 500) % 2 == 0 ? "_" : "");
        fit(g, q, x + 5, y + 3, listW - 10, staffQuery.isEmpty() && !staffSearch ? 0xFF2A7A44 : 0xFFB8FFCC);
        hit(x, y, listW, 14, () -> staffSearch = true);
        y += 18;
        ListTag sites = STAFF.getList("sites", Tag.TAG_STRING);
        int cx = x;
        List<String> chips = new ArrayList<>();
        chips.add("");
        for (Tag t : sites) chips.add(t.getAsString());
        for (String site : chips) {
            String lbl = site.isEmpty() ? "All" : site;
            int cw = Math.min(font.width(lbl) + 10, listW);
            if (cx + cw > x + listW) { cx = x; y += 16; }
            final String f = site;
            pill(g, cx, y, cw, 12, lbl, staffSite.equals(site) ? TEAL : 0xFF1A222C, staffSite.equals(site) ? 0xFF0B1118 : 0xFFC8D2DC, true, () -> { staffSite = f; scroll = 0; });
            cx += cw + 3;
        }
        y += 18;
        List<CompoundTag> list = people();
        if (list.isEmpty()) { fit(g, staffQuery.isEmpty() ? "No staff on record yet." : "Nobody matches.", x, y + 4, listW, MUTED); y += 20; }
        String lastSite = "";
        for (CompoundTag c : list) {
            if (!c.getString("site").equals(lastSite)) {
                lastSite = c.getString("site");
                tiny(g, lastSite.toUpperCase(Locale.ROOT), x + 2, y + 2, listW - 4, 0xFF35B860);
                y += 11;
            }
            boolean on = c.getString("id").equals(staffSel), ho = over(x, y, listW, 22);
            rbox(g, x, y, listW, 22, 2, on ? Terminal.SELECT : ho ? 0xFF0E2A18 : Terminal.PANEL, on ? Terminal.SELECT : Terminal.LINE);
            int tc = on ? 0xFF04140A : 0xFFB8FFCC, sc = on ? 0xFF0A3018 : 0xFF35B860;
            boolean head = c.contains("head");
            int dot = c.getBoolean("here") ? (c.getString("state").startsWith("Ill") || c.getString("state").equals("In danger") ? RED : c.getString("state").equals("Grieving") ? AMBER : 0xFF5CFF8A) : 0xFF2A7A44;
            rect(g, x + 4, y + 4, 3, 3, on ? 0xFF04140A : dot);
            fit(g, (head ? "★ " : "") + c.getString("name"), x + 10, y + 3, listW - 16, tc);
            tiny(g, (head ? c.getString("head") : c.getString("job")) + " · " + c.getInt("age") + (c.getBoolean("spouseOfYou") ? " · your spouse" : c.getBoolean("partner") ? " · ♥" : ""), x + 10, y + 13, listW - 16, sc);
            final String id = c.getString("id");
            hit(x, y, listW, 22, () -> { staffSel = id; confirm = ""; });
            y += 24;
        }
        if (desktop) {
            int dx = x + listW + 8, dw = w - listW - 8;
            if (sel != null) drawPerson(g, sel, dx, top + 6, dw);
            else {
                g.drawString(font, "Select someone", dx, top + 10, Terminal.MID, false);
                wrap(g, "Their file opens here: job, family, traits, how they feel about you. Write to them, call them over, appoint or dismiss them.", dx, top + 24, dw, 0xFF2A7A44);
                drawSpotlight(g, dx, top + 70, dw);
            }
        } else if (staffQuery.isEmpty() && staffSite.isEmpty()) y = drawSpotlight(g, x, y + 4, w);
        return y;
    }

    /** Someone worth noticing today: a long service, a new baby, an illness. One tap to write to them. */
    private int drawSpotlight(GuiGraphics g, int x, int y, int w) {
        ListTag ps = STAFF.getList("people", Tag.TAG_COMPOUND);
        if (ps.isEmpty()) return y;
        CompoundTag pick = null;
        String why = "";
        for (Tag t : ps) { CompoundTag c = (CompoundTag) t; if (c.getBoolean("expecting")) { pick = c; why = "is expecting a baby"; break; } }
        if (pick == null) for (Tag t : ps) { CompoundTag c = (CompoundTag) t; if (c.getString("state").startsWith("Ill")) { pick = c; why = "is unwell (" + c.getString("state").substring(5) + ")"; break; } }
        if (pick == null) for (Tag t : ps) { CompoundTag c = (CompoundTag) t; if (c.getString("state").equals("Grieving")) { pick = c; why = "has lost someone"; break; } }
        if (pick == null) {
            long day = t().getLong("now") / 24000;
            pick = ps.getCompound((int) Math.floorMod(day * 7919, ps.size()));
            why = pick.getInt("years") > 0 ? "has " + pick.getInt("years") + " years with the Foundation" : "keeps " + pick.getString("site") + " running";
        }
        int h = 16 + wrapHeight(pick.getString("name") + " " + why + ".", w - 12) + 20;
        rbox(g, x, y, w, h, 3, Terminal.PANEL, Terminal.MID);
        g.drawString(font, "SPOTLIGHT", x + 6, y + 4, Terminal.AMBER, false);
        int hh = wrap(g, pick.getString("name") + " " + why + ".", x + 6, y + 16, w - 12, 0xFFB8FFCC);
        final String id = pick.getString("id");
        boolean noted = pick.getBoolean("noted");
        String kind = why.startsWith("is unwell") || why.startsWith("has lost") ? "concern" : "commend";
        pill(g, x + 6, y + 18 + hh, w - 12, 14, noted ? "Note sent today" : kind.equals("concern") ? "Send a note of concern" : "Send a commendation", 0xFF2E9E57, !noted, () -> act("note:" + id + ":" + kind));
        return y + h + 6;
    }

    private int drawPerson(GuiGraphics g, CompoundTag c, int x, int y, int w) {
        final String id = c.getString("id");
        boolean here = c.getBoolean("here"), head = c.contains("head");
        rbox(g, x, y, w, 40, 3, Terminal.PANEL, head ? Terminal.AMBER : Terminal.MID);
        fit(g, c.getString("name"), x + 6, y + 5, w - 12, 0xFFFFFFFF);
        fit(g, head ? c.getString("head") : c.getString("job"), x + 6, y + 16, w - 12, head ? AMBER : 0xFF5CFF8A);
        tiny(g, c.getString("site") + " · age " + c.getInt("age") + (c.getInt("years") > 0 ? " · " + c.getInt("years") + " yrs service" : ""), x + 6, y + 28, w - 12, 0xFF35B860);
        y += 44;
        // status line
        String st = c.getString("state") + (here && c.getInt("dist") > 0 ? " · " + c.getInt("dist") + " m away" : here ? "" : " (not on a loaded site)");
        fit(g, "Status: " + st, x, y, w, c.getString("state").startsWith("Ill") || c.getString("state").equals("In danger") ? RED : 0xFFB8FFCC);
        y += 11;
        fit(g, "Personality: " + c.getString("pers"), x, y, w, 0xFFB8FFCC);
        y += 12;
        if (here) {
            int hearts = c.getInt("hearts"), resp = c.getInt("respect");
            g.drawString(font, "Hearts", x, y, Terminal.MID, false);
            StringBuilder hb = new StringBuilder();
            for (int i = 0; i < 5; i++) hb.append(hearts >= (i + 1) * 2 ? "♥" : hearts >= i * 2 + 1 ? "♡" : "·");
            g.drawString(font, hb.toString(), x + 46, y, Terminal.RED, false);
            y += 11;
            g.drawString(font, "Respect", x, y, Terminal.MID, false);
            meter(g, x + 46, y + 2, Math.min(60, w - 50), resp, 10, 0xFF5CFF8A);
            y += 12;
        }
        // family
        List<String> fam = new ArrayList<>();
        if (c.contains("spouse")) fam.add((c.getString("rel").equals("widowed") ? "Widowed: " : "Married to ") + c.getString("spouse"));
        else if (c.getString("rel").equals("dating") || c.getString("rel").equals("engaged")) fam.add(Scene_cap(c.getString("rel")));
        if (c.getInt("kids") > 0) fam.add(c.getInt("kids") + (c.getInt("kids") == 1 ? " child" : " children"));
        if (c.getBoolean("expecting")) fam.add("expecting");
        if (!fam.isEmpty()) { y += wrap(g, "Family: " + String.join(", ", fam), x, y, w, 0xFFB8FFCC) + 1; }
        if (!c.getString("traits").isEmpty()) { y += wrap(g, "Traits: " + c.getString("traits"), x, y, w, 0xFF35B860) + 1; }
        y += 4;
        Terminal.dashes(g, x, y, w, Terminal.LINE);
        y += 6;
        // write to them (they read it now, or when they're next on site, and remember it)
        label(g, "Write to them", x, y);
        y += 11;
        boolean noted = c.getBoolean("noted");
        y = grid(g, x, y, w, desktop ? 4 : 2, new String[]{"Commend", "Thank", desktop ? "Ask after" : "Ask after them", desktop ? "Warn" : "Formal warning"},
                new boolean[]{!noted, !noted, !noted, !noted}, new int[]{0xFF2E9E57, 0xFF2E9E57, 0xFF2E9E57, 0xFFC08020},
                new Runnable[]{() -> act("note:" + id + ":commend"), () -> act("note:" + id + ":thanks"), () -> act("note:" + id + ":concern"), () -> act("note:" + id + ":warning")});
        if (noted) { tiny(g, "Written today. They'll mention it when you meet.", x, y, w, 0xFF2A7A44); y += 10; }
        y += 3;
        label(g, "Actions", x, y);
        y += 11;
        boolean mk = STAFF.getBoolean("appoints") && !head && here;
        y = grid(g, x, y, w, desktop ? 3 : 2, new String[]{"Call them over", "Back to work", "Family tree", "Make head"},
                new boolean[]{here, here, true, mk}, new int[]{0xFF262E38, 0xFF262E38, 0xFF262E38, 0xFFC08020},
                new Runnable[]{() -> act("summon:" + id), () -> act("release:" + id), () -> act("tree:" + id), () -> act("head:" + id)});
        if (STAFF.getBoolean("hires") && here) { sure(g, x, y, w, "Dismiss (amnestic exit)", "dismiss:" + id, () -> { act("dismiss:" + id); staffSel = ""; }); y += 17; }
        return y + 4;
    }

    /** Buttons in a grid of {@code cols}; returns the y below them. */
    private int grid(GuiGraphics g, int x, int y, int w, int cols, String[] labels, boolean[] on, int[] colours, Runnable[] actions) {
        int bw = (w - (cols - 1) * 4) / cols;
        for (int i = 0; i < labels.length; i++) {
            int cx = x + (i % cols) * (bw + 4), cy = y + (i / cols) * 17;
            pill(g, cx, cy, bw, 14, labels[i], colours[i], on[i], actions[i]);
        }
        return y + ((labels.length + cols - 1) / cols) * 17;
    }

    private static String Scene_cap(String s) { return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1); }

    private int drawHiring(GuiGraphics g, int x, int y, int w) {
        boolean can = STAFF.getBoolean("hires");
        // open a post yourself
        ListTag sites = STAFF.getList("sites", Tag.TAG_STRING);
        if (can) {
            rbox(g, x, y, w, 62, 3, Terminal.PANEL, Terminal.MID);
            g.drawString(font, "OPEN A POST", x + 6, y + 5, Terminal.AMBER, false);
            tiny(g, "Personnel sends a shortlist; you choose. No meeting.", x + 6, y + 15, w - 12, 0xFF35B860);
            int bw = (w - 16) / 2;
            String site = sites.isEmpty() ? "No sites" : sites.getString(Math.floorMod(postSite, sites.size()));
            pill(g, x + 6, y + 25, bw, 14, "Site: " + site, 0xFF1A222C, !sites.isEmpty(), () -> postSite++);
            pill(g, x + 10 + bw, y + 25, bw, 14, "Role: " + POST_ROLES[Math.floorMod(postRole, POST_ROLES.length)], 0xFF1A222C, true, () -> postRole++);
            pill(g, x + 6, y + 43, w - 12, 14, "Find applicants", 0xFF2E9E57, !sites.isEmpty(), () -> act("post:" + site + ":" + POST_ROLE_ORD[Math.floorMod(postRole, POST_ROLES.length)]));
            y += 68;
        } else { wrap(g, "Hiring decisions are made by Site Directors and above. You can see what's open.", x, y, w, MUTED); y += 24; }
        // villagers who walked in asking for work (with Minecraft Comes Alive)
        ListTag walk = STAFF.getList("walkins", Tag.TAG_COMPOUND);
        if (!walk.isEmpty()) {
            label(g, "Waiting in the lobby", x, y);
            y += 12;
            String role = POST_ROLES[Math.floorMod(postRole, POST_ROLES.length)];
            for (Tag tw : walk) {
                CompoundTag c = (CompoundTag) tw;
                String id = c.getString("id");
                String body = c.getString("pers") + (c.getString("traits").isEmpty() ? "" : " · " + c.getString("traits"));
                int h = 26 + wrapHeight(body, w - 12) + 18;
                rbox(g, x, y, w, h, 3, Terminal.PANEL, Terminal.AMBER);
                fit(g, c.getString("name") + ", " + c.getInt("age"), x + 6, y + 4, w - 12, 0xFFFFFFFF);
                int hours = c.getInt("waited");
                tiny(g, "From the village · at " + c.getString("site") + " · waiting " + (hours < 1 ? "under an hour" : hours + "h"), x + 6, y + 15, w - 12, AMBER);
                int by = y + 25 + wrap(g, body, x + 6, y + 25, w - 12, 0xFF8FD8A8);
                int bw = (w - 16) / 2;
                pill(g, x + 6, by + 1, bw, 14, "Hire: " + role, 0xFF2E9E57, can, () -> act("walkhire:" + id + ":" + POST_ROLE_ORD[Math.floorMod(postRole, POST_ROLES.length)]));
                pill(g, x + 10 + bw, by + 1, bw, 14, "Send home", 0xFF262E38, can, () -> act("walkhome:" + id));
                y += h + 4;
            }
            tiny(g, "Change the role with the Role button above.", x, y, w, 0xFF2A7A44);
            y += 12;
        } else if (STAFF.getBoolean("mca")) { tiny(g, "Jobless villagers near a site will walk in to apply.", x, y, w, 0xFF2A7A44); y += 12; }
        ListTag vac = STAFF.getList("vac", Tag.TAG_COMPOUND);
        if (vac.isEmpty()) { fit(g, "Every post is filled.", x, y + 4, w, MUTED); return y + 20; }
        label(g, "Open posts", x, y);
        y += 12;
        for (Tag tv : vac) {
            CompoundTag v = (CompoundTag) tv;
            int id = v.getInt("id");
            String state = v.getString("state"), kind = v.getString("kind");
            boolean open = hireSel == id || vac.size() == 1;
            String status = switch (state) {
                case "direct" -> "Shortlist ready: your choice";
                case "running" -> "Interviews under way in your office";
                case "booked" -> (v.getInt("attend") == 1 ? "You're interviewing " : "Interviews ") + v.getString("when");
                default -> kind.equals("head") ? "Council to appoint (or you)" : "Interviews " + v.getString("when") + (v.getInt("attend") == 2 ? ", head decides" : "");
            };
            boolean ho = over(x, y, w, 26);
            rbox(g, x, y, w, 26, 3, open ? 0xFF0E2A18 : ho ? 0xFF0A2214 : Terminal.PANEL, state.equals("direct") ? Terminal.AMBER : Terminal.MID);
            fit(g, Scene_cap(v.getString("what")) + " · " + v.getString("site"), x + 6, y + 4, w - 20, 0xFFFFFFFF);
            tiny(g, status + (v.getString("left").isEmpty() ? "" : " · replacing " + v.getString("left")), x + 6, y + 15, w - 20, state.equals("direct") ? AMBER : 0xFF35B860);
            g.drawString(font, open ? "v" : ">", x + w - 10, y + 9, Terminal.MID, false);
            hit(x, y, w, 26, () -> hireSel = hireSel == id ? -1 : id);
            y += 29;
            if (!open) continue;
            boolean decide = kind.equals("head") ? STAFF.getBoolean("appoints") : can;
            ListTag cs = v.getList("cands", Tag.TAG_COMPOUND);
            for (int i = 0; i < cs.size(); i++) {
                CompoundTag c = cs.getCompound(i);
                List<String> lines = new ArrayList<>();
                lines.add(c.getString("bg"));
                if (c.contains("family")) lines.add(c.getString("family"));
                if (c.contains("vet")) { lines.add("Vetting: " + c.getString("vet")); lines.add("Medical: " + c.getString("med")); }
                int bodyH = 0;
                for (String l : lines) bodyH += wrapHeight(l, w - 18);
                int h = 26 + bodyH + 20;
                rbox(g, x + 4, y, w - 4, h, 2, Terminal.PANEL, c.getString("vet").startsWith("FLAG") ? Terminal.RED : Terminal.LINE);
                fit(g, c.getString("name") + ", " + c.getInt("age"), x + 9, y + 4, w - 60, 0xFFB8FFCC);
                String stars = "★".repeat(c.getInt("stars")) + "☆".repeat(Math.max(0, 5 - c.getInt("stars")));
                g.drawString(font, stars, x + w - 6 - font.width(stars), y + 4, Terminal.AMBER, false);
                tiny(g, c.getString("pers") + (c.getBoolean("internal") ? " · internal candidate" : ""), x + 9, y + 15, w - 18, 0xFF35B860);
                int ly = y + 24;
                for (String l : lines) ly += wrap(g, l, x + 9, ly, w - 18, l.startsWith("Vetting: FLAG") ? RED : 0xFF8FD8A8);
                final int idx = i;
                int bw = (w - 20) / 2;
                pill(g, x + 9, ly + 2, bw, 14, c.contains("vet") ? "Vetted" : "Background check", 0xFF262E38, !c.contains("vet"), () -> act("vet:" + id + ":" + idx));
                if (decide) pill(g, x + 13 + bw, ly + 2, bw, 14, kind.equals("head") ? "Appoint" : "Hire", 0xFF2E9E57, true, () -> { act("pick:" + id + ":" + idx); hireSel = -1; });
                y += h + 4;
            }
            if (can && !state.equals("running")) {
                int bw = (w - 8) / 3;
                pill(g, x + 4, y, bw, 14, "Interview", 0xFF262E38, v.getInt("attend") != 1, () -> act("attend:" + id));
                if (!kind.equals("head")) pill(g, x + 8 + bw, y, bw, 14, "Head decides", 0xFF262E38, v.getInt("attend") != 2 || state.equals("direct"), () -> act("delegate:" + id));
                if (state.equals("direct")) sure(g, x + 12 + bw * 2, y, bw, "Withdraw", "cancel:" + id, () -> act("cancel:" + id));
                else pill(g, x + 12 + bw * 2, y, bw, 14, "Now", 0xFF262E38, true, () -> act("now:" + id));
                y += 18;
            }
            y += 4;
        }
        return y;
    }

    private static final String[] DEPTS = {"SECURITY", "RESEARCH", "MEDICAL", "ENGINEERING", "PERSONNEL"};
    private static final String[] DEPT_TITLES = {"Head of Security", "Director of Research", "Chief Medical Officer", "Chief Engineer", "Head of Personnel"};
    private static final int[] DEPT_COUNT = {1, 2, 3, 4, 5};

    private int drawOrg(GuiGraphics g, int x, int y, int w) {
        // the chain of command, top down
        int bw = Math.min(150, w);
        rbox(g, x + (w - bw) / 2, y, bw, 16, 2, Terminal.PANEL, Terminal.RED);
        g.drawString(font, "O5 COUNCIL", x + w / 2 - font.width("O5 COUNCIL") / 2, y + 4, Terminal.RED, false);
        rect(g, x + w / 2, y + 16, 1, 6, Terminal.LINE);
        y += 22;
        rbox(g, x + (w - bw) / 2, y, bw, 16, 2, Terminal.PANEL, Terminal.AMBER);
        g.drawString(font, "THE ADMINISTRATOR", x + w / 2 - font.width("THE ADMINISTRATOR") / 2, y + 4, Terminal.AMBER, false);
        rect(g, x + w / 2, y + 16, 1, 6, Terminal.LINE);
        y += 26;
        for (Tag t : STAFF.getList("org", Tag.TAG_COMPOUND)) {
            CompoundTag o = (CompoundTag) t;
            int[] n = o.getIntArray("n");
            int total = 0;
            for (int v : n) total += v;
            CompoundTag hd = o.getCompound("heads");
            int h = 16 + DEPTS.length * 20 + 4;
            rbox(g, x, y, w, h, 3, Terminal.PANEL, Terminal.MID);
            fit(g, o.getString("site").toUpperCase(Locale.ROOT), x + 6, y + 4, w - 70, 0xFFFFFFFF);
            String tt = total + " staff";
            g.drawString(font, tt, x + w - 6 - font.width(tt), y + 4, Terminal.MID, false);
            int ry = y + 16;
            for (int i = 0; i < DEPTS.length; i++) {
                String name = hd.getString(DEPTS[i]), hid = hd.getString(DEPTS[i] + "_id");
                int count = n.length > DEPT_COUNT[i] ? n[DEPT_COUNT[i]] : 0;
                boolean ho = !hid.isEmpty() && over(x + 4, ry, w - 8, 18);
                if (ho) rect(g, x + 4, ry, w - 8, 18, 0xFF0E2A18);
                tiny(g, DEPT_TITLES[i] + " (" + count + ")", x + 8, ry + 1, w - 16, 0xFF35B860);
                fit(g, name.isEmpty() ? "vacant" : name, x + 8, ry + 8, w - 16, name.isEmpty() ? AMBER : 0xFFB8FFCC);
                if (!hid.isEmpty()) hit(x + 4, ry, w - 8, 18, () -> { staffTab = 0; staffSel = hid; staffSite = ""; staffQuery = ""; scroll = 0; });
                ry += 20;
            }
            y += h + 6;
        }
        return y;
    }

    private int drawRegistry(GuiGraphics g, int x, int y, int w) {
        label(g, "Personnel registry", x, y);
        y += 12;
        ListTag log = STAFF.getList("log", Tag.TAG_STRING);
        if (log.isEmpty()) { fit(g, "Nothing recorded yet.", x, y, w, MUTED); return y + 14; }
        for (Tag t : log) {
            String l = t.getAsString();
            int c = l.contains("died") || l.contains("killed") || l.contains("terminated") || l.contains("defected") ? 0xFFE08080
                    : l.contains("welcomed") || l.contains("married") || l.contains("engaged") ? 0xFFF0C060 : 0xFFB8FFCC;
            int colon = l.indexOf(':');
            if (colon > 0) {
                tiny(g, l.substring(0, colon).toUpperCase(Locale.ROOT), x, y, w, 0xFF35B860);
                y += 8;
                y += wrap(g, l.substring(colon + 1).trim(), x, y, w, c) + 4;
            } else y += wrap(g, l, x, y, w, c) + 4;
        }
        return y;
    }

    // ---------------------------------------------------------------- Recruitment (computer only)

    private int forceSite;

    private int drawForces(GuiGraphics g, int y) {
        ListTag sites = t().getList("forces", Tag.TAG_COMPOUND);
        int x = SX + 6, w = SW - 12;
        if (sites.isEmpty()) {
            empty(g, y, "No sites", "Declare a site (Settings) or build one, then station personnel here.");
            return y + 90;
        }
        forceSite = Mth.clamp(forceSite, 0, sites.size() - 1);
        // the sites, as tabs
        int tx = x;
        for (int i = 0; i < sites.size(); i++) {
            String n = sites.getCompound(i).getString("n");
            int tw = Math.min(110, font.width(n) + 16);
            final int idx = i;
            boolean alert = sites.getCompound(i).contains("alert");
            pill(g, tx, y, tw, 14, (i == forceSite ? "> " : "") + n, alert ? 0xFFA8323A : 0xFF2E9E57, true, () -> { forceSite = idx; scroll = 0; });
            tx += tw + 4;
            if (tx > x + w - 60) { tx = x; y += 18; }
        }
        y += 20;
        CompoundTag site = sites.getCompound(forceSite);
        int si = site.getInt("i");
        ListTag units = site.getList("units", Tag.TAG_COMPOUND);
        int have = 0, want = 0, coming = 0;
        for (int u = 0; u < units.size(); u++) { have += units.getCompound(u).getInt("have"); want += units.getCompound(u).getInt("want"); coming += units.getCompound(u).getInt("coming"); }

        card(g, x, y, w, 44);
        text(g, site.getString("n") + " · armed personnel", x + 8, y + 5, 0xFFE8EEF4);
        if (site.contains("alert"))
            text(g, "UNDER ATTACK: " + site.getString("alert") + " · " + site.getInt("hostiles") + " hostiles · " + site.getInt("kills") + " killed", x + 8, y + 16, RED);
        else
            text(g, have + " on duty · " + want + " ordered" + (coming > 0 ? " · " + coming + " inbound" : "") + (site.getBoolean("ordered") ? "" : " · Council baseline"), x + 8, y + 16, MUTED);
        long rev = t().getLong("review") / 20;
        text(g, "O5 Council review in " + (rev >= 3600 ? rev / 3600 + "h " + rev % 3600 / 60 + "m" : rev / 60 + "m") + ": they bring every site to strength and replace the dead.", x + 8, y + 27, 0xFF7FD6FF);
        y += 50;
        int bw = (w - 8) / 2;
        pill(g, x, y, bw, 16, "Auto-replace losses: " + (site.getBoolean("auto") ? "ON" : "OFF"), site.getBoolean("auto") ? 0xFF2E9E57 : 0xFF4A525E, true, () -> send("force_auto", si * 16));
        pill(g, x + bw + 8, y, bw, 16, "Bring to full strength", 0xFF2E9E57, true, () -> send("force_fill", si * 16));
        y += 22;

        int cols = desktop ? 2 : 1, cw = (w - (cols - 1) * 6) / cols, ch = 66;
        for (int u = 0; u < units.size(); u++) {
            CompoundTag c = units.getCompound(u);
            int cx = x + (u % cols) * (cw + 6), cy = y + (u / cols) * (ch + 6);
            card(g, cx, cy, cw, ch);
            text(g, c.getString("t"), cx + 7, cy + 5, 0xFFE8EEF4);
            List<FormattedCharSequence> about = font.split(Component.literal(c.getString("a")), cw - 14);
            if (!about.isEmpty()) g.drawString(font, about.get(0), cx + 7, cy + 16, Terminal.DIM, false);
            int h = c.getInt("have"), wn = c.getInt("want"), cm = c.getInt("coming");
            text(g, "On duty " + h + "  ·  Ordered " + wn + (cm > 0 ? "  ·  " + cm + " inbound" : ""), cx + 7, cy + 28, h < wn ? AMBER : 0xFF5BE36F);
            final int arg = si * 16 + u;
            pill(g, cx + 6, cy + 44, 18, 16, "-", 0xFF2E9E57, wn > 0, () -> send("force_dec", arg));
            pill(g, cx + 28, cy + 44, 18, 16, "+", 0xFF2E9E57, wn < 24, () -> send("force_inc", arg));
            int rest = cw - 58;
            pill(g, cx + 50, cy + 44, rest / 2 - 2, 16, "Deploy", 0xFF2E9E57, h + cm < wn, () -> send("force_deploy", arg));
            pill(g, cx + 50 + rest / 2 + 2, cy + 44, rest / 2 - 2, 16, "Stand down", 0xFFA8323A, h > wn, () -> send("force_dismiss", arg));
        }
        y += ((units.size() + cols - 1) / cols) * (ch + 6) + 4;

        ListTag losses = site.getList("losses", Tag.TAG_STRING);
        label(g, "Recent losses", x + 2, y);
        y += 12;
        if (losses.isEmpty()) { text(g, "None. Keep it that way.", x + 4, y, MUTED); y += 12; }
        for (int i = 0; i < losses.size(); i++) { text(g, "† " + losses.getString(i), x + 4, y, 0xFFC8B0B0); y += 11; }
        return y + 4;
    }

    // ---------------------------------------------------------------- Site Security (computer only)

    private int drawSecurity(GuiGraphics g, int y) {
        CompoundTag t = t();
        int x = SX + 6, w = SW - 12;
        ListTag alerts = t.getList("alerts", Tag.TAG_COMPOUND);
        label(g, "Threat board", x + 2, y);
        y += 12;
        if (alerts.isEmpty()) {
            card(g, x, y, w, 22);
            text(g, "All sites secure. Guards are engaging hostile mobs on sight.", x + 8, y + 7, 0xFF5BE36F);
            y += 28;
        }
        for (int i = 0; i < alerts.size(); i++) {
            CompoundTag a = alerts.getCompound(i);
            rbox(g, x, y, w, 24, 4, 0xFF2E0E0E, 0xFFB84848);
            text(g, "⚠ " + a.getString("site") + ": " + a.getString("who") + " · " + a.getInt("n") + " hostiles on the grounds · " + a.getInt("k") + " neutralised", x + 8, y + 8, 0xFFFFB3B3);
            y += 30;
        }

        label(g, "Organisations", x + 2, y);
        y += 12;
        ListTag orgs = t.getList("orgs", Tag.TAG_COMPOUND);
        for (int i = 0; i < orgs.size(); i++) {
            CompoundTag o = orgs.getCompound(i);
            boolean inv = o.getBoolean("inv");
            card(g, x, y, w, 22);
            text(g, o.getString("n"), x + 8, y + 7, 0xFFE8EEF4);
            text(g, inv ? "INVITED · guards hold fire" : "HOSTILE · shot on sight", x + 150, y + 7, inv ? 0xFF5BE36F : RED);
            final int idx = i;
            pill(g, x + w - 110, y + 3, 104, 16, inv ? "Declare hostile" : "Invite", inv ? 0xFFA8323A : 0xFF2E9E57, true, () -> send("def_invite", idx));
            y += 26;
        }
        text(g, "Any other organisation that sets foot on a site is treated as hostile.", x + 4, y, MUTED);
        y += 16;

        label(g, "Personnel and visitors", x + 2, y);
        y += 12;
        ListTag ppl = t.getList("people", Tag.TAG_COMPOUND);
        if (ppl.isEmpty()) { text(g, "No one else is online.", x + 4, y, MUTED); y += 14; }
        for (int i = 0; i < ppl.size(); i++) {
            CompoundTag o = ppl.getCompound(i);
            boolean kos = o.getBoolean("kos");
            card(g, x, y, w, 22);
            text(g, o.getString("n"), x + 8, y + 7, 0xFFE8EEF4);
            text(g, kos ? "KILL ON SIGHT" : "Cleared", x + 150, y + 7, kos ? RED : 0xFF5BE36F);
            final int idx = i;
            pill(g, x + w - 110, y + 3, 104, 16, kos ? "Clear them" : "Kill on sight", kos ? 0xFF2E9E57 : 0xFFA8323A, true, () -> send("def_kos", idx));
            y += 26;
        }
        y += 4;
        label(g, "Standing orders", x + 2, y);
        y += 12;
        for (String line : new String[]{"Creepers are engaged first, from range. Guards back off before they can blow.",
                "Hostile mobs near any guard are shot. Neutral animals are left alone.",
                "Anyone who shoots at our people is shot back. Never the Administrator.",
                "SCP-096 is never looked at or fired on."}) {
            y += wrap(g, "• " + line, x + 4, y, w - 8, 0xFFB8C8D0) + 2;
        }
        return y + 4;
    }

    /** Each Overseer's secure line. */
    private static String number(int seat) { return String.format("0500-%02d-%02d", seat, (seat * 37 + 11) % 100); }

    /** The Council's numbers: tap the phone to ring them, and they pick up. */
    private int drawCouncilDirectory(GuiGraphics g, int x, int y, int w) {
        boolean busy = t().contains("callText");
        for (com.breachprotocol.career.O5 o : com.breachprotocol.career.O5.values()) {
            if (o.seat > 12) continue;
            boolean hov = over(x, y, w, 24);
            rbox(g, x, y, w, 24, 6, hov ? 0xFF0E3A1E : 0xFF08180E, hov ? Terminal.MID : 0xFF163D24);
            avatar(g, o.sender(), x + 12, y + 12, 9);
            text(g, ellipsize(o.nickname, w - 70), x + 26, y + 3, Terminal.BRIGHT);
            small(g, number(o.seat), x + 26 + (int) (font.width(number(o.seat)) * 0.375F), y + 14, Terminal.DIM);
            boolean hc = over(x + w - 26, y + 3, 22, 18);
            rbox(g, x + w - 26, y + 3, 22, 18, 9, busy ? 0xFF07110A : hc ? 0xFF2EA65A : 0xFF1F6E3A, busy ? Terminal.LINE : Terminal.BRIGHT);
            textC(g, "\u260E", x + w - 15, y + 8, busy ? Terminal.DIM : 0xFFFFFFFF);
            final int seat = o.seat;
            if (!busy) hit(x, y, w, 24, () -> { send("dial", seat); scroll = 0; });
            y += 27;
        }
        if (busy) small(g, "FINISH YOUR CURRENT CALL FIRST", SX + SW / 2, y + 2, Terminal.AMBER);
        return y + 12;
    }

    // ---------------------------------------------------------------- Calendar

    private int schedSeat = 7;

    private static final String[] DOW = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};

    private static String when(long at) {
        long day = at / 24000;
        int tod = (int) (at % 24000);
        int h = (tod / 1000 + 6) % 24, m = (tod % 1000) * 60 / 1000;
        return DOW[(int) (day % 7)] + " " + (day + 1) + " · " + String.format("%02d:%02d", h, m);
    }

    private static String apptWho(CompoundTag a) {
        return a.getInt("seat") == 0 ? "The O5 Council" : com.breachprotocol.career.O5.seat(a.getInt("seat")).sender();
    }

    private static String until(long at) {
        long d = at - t().getLong("dayNow");
        if (d <= 0) return "now";
        long hours = d / 1000, mins = d % 1000 * 60 / 1000;
        return hours >= 24 ? "in " + hours / 24 + "d " + hours % 24 + "h" : hours > 0 ? "in " + hours + "h " + mins + "m" : "in " + mins + "m";
    }

    private int drawCalendar(GuiGraphics g, int y) {
        CompoundTag t = t();
        int x = SX + 6, w = SW - 12;
        long now = t.getLong("dayNow");
        ListTag appts = t.getList("appts", Tag.TAG_COMPOUND);
        // the week, with a mark on days that have meetings
        long today = now / 24000;
        int cw = (w - 12) / 7;
        for (int i = 0; i < 7; i++) {
            long day = today + i;
            int bx = x + i * (cw + 2);
            boolean has = false;
            for (int k = 0; k < appts.size(); k++) if (appts.getCompound(k).getLong("at") / 24000 == day) has = true;
            rbox(g, bx, y, cw, 26, 3, i == 0 ? 0xFF0E3A1E : 0xFF08180E, i == 0 ? Terminal.BRIGHT : 0xFF163D24);
            textC(g, String.valueOf(day + 1), bx + cw / 2, y + 4, i == 0 ? Terminal.BRIGHT : Terminal.GREEN);
            small(g, i == 0 ? "NOW" : DOW[(int) (day % 7)].toUpperCase(Locale.ROOT), bx + cw / 2, y + 15, day % 7 == 1 ? AMBER : Terminal.DIM);
            if (has) circle(g, bx + cw - 4, y + 4, 2, AMBER);
        }
        y += 30;
        small(g, "NOW: " + when(now).toUpperCase(Locale.ROOT), SX + SW / 2, y, Terminal.MID);
        y += 12;

        // requests
        List<CompoundTag> reqs = new ArrayList<>(), booked = new ArrayList<>();
        for (int k = 0; k < appts.size(); k++) {
            CompoundTag a = appts.getCompound(k);
            (a.getBoolean("ok") || a.getInt("seat") == 0 ? booked : reqs).add(a);
        }
        booked.sort(Comparator.comparingLong(a -> a.getLong("at")));
        label(g, "Meeting requests", x + 2, y);
        y += 12;
        if (reqs.isEmpty()) { text(g, "None waiting.", x + 4, y, MUTED); y += 14; }
        boolean busy = t.contains("callText");
        for (CompoundTag a : reqs) {
            String from = com.breachprotocol.career.O5.seat(a.getInt("seat")).sender();
            card(g, x, y, w, 56);
            avatar(g, from, x + 12, y + 12, 8);
            text(g, ellipsize(seatOf(from) + " " + nick(from), w - 30), x + 24, y + 4, 0xFFE8EEF4);
            text(g, ellipsize(a.getString("about"), w - 30), x + 24, y + 14, AMBER);
            small(g, ("PROPOSES " + when(a.getLong("at")) + " (" + until(a.getLong("at")) + ")").toUpperCase(Locale.ROOT), x + w / 2, y + 25, Terminal.DIM);
            int id = a.getInt("id"), q = (w - 18) / 4;
            pill(g, x + 4, y + 36, q, 15, "Accept", 0xFF2EA65A, true, () -> send("appt_accept", id));
            pill(g, x + 6 + q, y + 36, q, 15, "Now", 0xFF3C6FB8, !busy, () -> { send("appt_now", id); go(View.CALL); });
            pill(g, x + 8 + 2 * q, y + 36, q, 15, "+6h", 0xFF2E9E57, true, () -> send("appt_later", id));
            pill(g, x + 10 + 3 * q, y + 36, q, 15, "Decline", 0xFFC0383F, true, () -> send("appt_decline", id));
            y += 60;
        }

        // booked
        label(g, "Upcoming", x + 2, y);
        y += 12;
        if (booked.isEmpty()) { text(g, "Nothing booked.", x + 4, y, MUTED); y += 14; }
        for (CompoundTag a : booked) {
            if (a.getInt("seat") == 0) {
                boolean late = a.getBoolean("tx");
                rbox(g, x, y, w, 56, 4, late ? 0xFF2E0E0E : 0xFF0E2A16, late ? 0xFFB84848 : AMBER);
                text(g, (late ? "LATE · " : "") + when(a.getLong("at")) + "  ·  " + until(a.getLong("at")), x + 6, y + 4, late ? 0xFFFFB3B3 : AMBER);
                ListTag ag = t.getList("agendaTitles", Tag.TAG_STRING);
                text(g, ellipsize("Council session, Site-01 · " + (ag.isEmpty() ? "SCP-173 cell + the week's business" : ag.size() + " added: " + ag.getString(0) + (ag.size() > 1 ? "…" : "")), w - 12), x + 6, y + 14, 0xFFE8EEF4);
                int id = a.getInt("id"), half = (w - 14) / 2;
                pill(g, x + 4, y + 24, half, 13, late ? "On my way" : "Keep", 0xFF2EA65A, true, () -> send(late ? "week_otw" : "week_keep", id));
                pill(g, x + 10 + half, y + 24, half, 13, late ? "Tomorrow" : "Next day", 0xFF2E9E57, true, () -> send("week_next", id));
                pill(g, x + 4, y + 40, half, 13, "Skip this week", 0xFFC0383F, true, () -> send("week_skip", id));
                pill(g, x + 10 + half, y + 40, half, 13, "Go now", 0xFF3C6FB8, true, () -> { send("appt_now", id); goHome(); });
                y += 60;
                continue;
            }
            String from = com.breachprotocol.career.O5.seat(a.getInt("seat")).sender();
            card(g, x, y, w, 40);
            text(g, when(a.getLong("at")) + "  ·  " + until(a.getLong("at")), x + 6, y + 4, Terminal.BRIGHT);
            text(g, ellipsize(seatOf(from) + " " + nick(from) + ": " + a.getString("about"), w - 12), x + 6, y + 14, 0xFFC8D8D0);
            int id = a.getInt("id"), half = (w - 14) / 2;
            pill(g, x + 4, y + 24, half, 13, "Take it now", 0xFF3C6FB8, !busy, () -> { send("appt_now", id); go(View.CALL); });
            pill(g, x + 10 + half, y + 24, half, 13, "Cancel", 0xFFC0383F, true, () -> send("appt_cancel", id));
            y += 44;
        }

        // book a meeting
        label(g, "Book a meeting", x + 2, y);
        y += 12;
        int pw = (w - 10) / 6;
        for (int sIdx = 1; sIdx <= 12; sIdx++) {
            int bx = x + ((sIdx - 1) % 6) * (pw + 2), by = y + ((sIdx - 1) / 6) * 16;
            final int seat = sIdx;
            pill(g, bx, by, pw, 14, (schedSeat == sIdx ? ">" : "") + sIdx, schedSeat == sIdx ? 0xFF2EA65A : 0xFF2E9E57, true, () -> schedSeat = seat);
        }
        y += 34;
        text(g, ellipsize("With " + com.breachprotocol.career.O5.seat(schedSeat).sender(), w - 8), x + 4, y, 0xFFE8EEF4);
        y += 12;
        String[] slots = {"In 1 hour", "In 3 hours", "This evening 18:00", "Tomorrow 08:00", "Tomorrow 14:00"};
        for (int k = 0; k < slots.length; k++) {
            final int arg = schedSeat * 10 + k;
            pill(g, x, y, w, 14, slots[k], 0xFF2E9E57, true, () -> send("sched", arg));
            y += 17;
        }
        pill(g, x, y + 2, w, 14, "Call them right now", 0xFF3C6FB8, !busy, () -> { send("dial", schedSeat); go(View.CALL); });
        return y + 22;
    }
}
