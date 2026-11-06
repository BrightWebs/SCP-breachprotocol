package com.breachprotocol.client;

import com.breachprotocol.network.ClientCareerState;
import com.breachprotocol.network.PhoneActionPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/** The Foundation Phone. Tabs: Home, Messages, Tasks, Calls, Orders. Contents depend on rank. */
public class PhoneScreen extends Screen {
    private static final int W = 214, H = 262;
    private static final int BEZEL = 0xFF16181D, SCREEN = 0xFF08131A, ACCENT = 0xFF3FD0FF, DIM = 0xFF6F8A96;
    private enum Tab { HOME, MSGS, TASKS, CALLS, ORDERS }

    private static Tab tab = Tab.HOME;
    private int scroll;
    private int seenVersion = -1;
    private int left, top, sx, sy, sw, sh, cy, cb, cursor, contentHeight;

    private record Line(FormattedCharSequence text, int x, int y, int color) {}
    private record Bar(int x, int y, int w, float pct, int color) {}
    private final List<Line> lines = new ArrayList<>();
    private final List<Bar> bars = new ArrayList<>();

    public PhoneScreen() {
        super(Component.literal("Foundation Phone"));
    }

    private static CompoundTag t() {
        return ClientCareerState.tag;
    }

    private static void send(String action, int arg) {
        PacketDistributor.sendToServer(new PhoneActionPayload(action, arg));
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        sx = left + 8;
        sy = top + 16;
        sw = W - 16;
        sh = H - 32;
        cy = sy + 30;
        cb = sy + sh - 4;
        lines.clear();
        bars.clear();
        seenVersion = ClientCareerState.version;

        Tab[] tabs = Tab.values();
        int tw = sw / tabs.length;
        for (int i = 0; i < tabs.length; i++) {
            Tab tb = tabs[i];
            Button b = Button.builder(Component.literal(tabLabel(tb)).withStyle(tb == tab ? ChatFormatting.AQUA : ChatFormatting.WHITE), btn -> {
                tab = tb;
                scroll = 0;
                if (tb == Tab.MSGS) send("read", 0);
                rebuildWidgets();
            }).bounds(sx + i * tw, sy + 11, tw, 15).build();
            addRenderableWidget(b);
        }

        cursor = cy - scroll;
        switch (tab) {
            case HOME -> buildHome();
            case MSGS -> buildMessages();
            case TASKS -> buildTasks();
            case CALLS -> buildCalls();
            case ORDERS -> buildOrders();
        }
        contentHeight = cursor - (cy - scroll);
    }

    private String tabLabel(Tab tb) {
        return switch (tb) {
            case HOME -> "Home";
            case MSGS -> {
                int u = unread();
                yield u > 0 ? "Msg " + u : "Msg";
            }
            case TASKS -> "Tasks";
            case CALLS -> t().contains("callText") ? "CALL!" : "Calls";
            case ORDERS -> "Orders";
        };
    }

    private int unread() {
        int n = 0;
        for (Tag e : t().getList("messages", Tag.TAG_COMPOUND)) if (!((CompoundTag) e).getBoolean("r")) n++;
        return n;
    }

    // ------------------------------------------------------------------ layout helpers

    private void text(Component c, int color) {
        for (FormattedCharSequence s : font.split(c, sw - 10)) {
            lines.add(new Line(s, sx + 5, cursor, color));
            cursor += 10;
        }
    }

    private void text(String s, int color) {
        text(Component.literal(s), color);
    }

    private void gap(int n) {
        cursor += n;
    }

    private boolean visible(int h) {
        return cursor >= cy && cursor + h <= cb;
    }

    private void button(String label, String tooltip, Button.OnPress press, boolean active) {
        if (visible(14)) {
            Button b = Button.builder(Component.literal(label), press).bounds(sx + 5, cursor, sw - 10, 14).build();
            if (tooltip != null) b.setTooltip(Tooltip.create(Component.literal(tooltip)));
            b.active = active;
            addRenderableWidget(b);
        }
        cursor += 16;
    }

    private void buttonPair(String a, Button.OnPress pa, String b, Button.OnPress pb) {
        if (visible(14)) {
            int half = (sw - 14) / 2;
            addRenderableWidget(Button.builder(Component.literal(a), pa).bounds(sx + 5, cursor, half, 14).build());
            addRenderableWidget(Button.builder(Component.literal(b), pb).bounds(sx + 9 + half, cursor, half, 14).build());
        }
        cursor += 16;
    }

    private void bar(float pct, int color) {
        bars.add(new Bar(sx + 5, cursor, sw - 10, Mth.clamp(pct, 0, 1), color));
        cursor += 8;
    }

    // ------------------------------------------------------------------ tabs

    private void buildHome() {
        CompoundTag t = t();
        boolean ascended = t.getBoolean("ascended");
        int rank = t.getInt("rank");
        text(t.getString("personnel").isEmpty() ? "Connecting..." : "Personnel " + t.getString("personnel"), DIM);
        text(Component.literal(t.getString("title")).withStyle(ChatFormatting.BOLD), ascended ? 0xFFFF77FF : 0xFFFFFFFF);
        gap(3);
        int next = t.getInt("nextXp"), cur = t.getInt("curXp"), xp = t.getInt("xp");
        if (ascended) {
            text("Object class: Apollyon. Immortal. Every anomaly answers to you.", 0xFFFFB0FF);
            text("Sneak + right-click an SCP (empty hand) to make it kneel.", DIM);
        } else if (next > 0) {
            text("Clearance XP: " + xp + " / " + next, 0xFFB8E6F0);
            bar((xp - cur) / (float) Math.max(1, next - cur), ACCENT);
            if (rank == 5) text("Next: a seat on the O5 Council.", DIM);
            if (rank == 6) text("Next: The Founder's Trial.", DIM);
        } else {
            text("You run the Foundation.", 0xFFE0B0FF);
        }
        if (rank >= 5) {
            gap(3);
            int st = t.getInt("stability");
            text("Site Stability: " + st + "%", st < 25 ? 0xFFFF5555 : st > 85 ? 0xFF55FF88 : 0xFFE0E0E0);
            bar(st / 100F, st < 25 ? 0xFFE03030 : st > 85 ? 0xFF40D070 : 0xFFE0C040);
        }
        gap(4);
        text("Active missions: " + t.getList("missions", Tag.TAG_COMPOUND).size() + "   Unread: " + unread(), 0xFFCCCCCC);
        if (t.contains("site")) text("Site-01: " + t.getString("site"), 0xFFE0B0FF);
        gap(6);
        text("Unlocks", ACCENT);
        String[][] unlocks = {{"4", "Requisition Supplies"}, {"5", "Site Lockdown, stability reports"}, {"6", "Council votes, MTF, Amnestics"},
                {"7", "Command the Council, Alpha-1, Site-01"}};
        for (String[] u : unlocks) {
            boolean have = rank >= Integer.parseInt(u[0]);
            String who = switch (u[0]) { case "4" -> "L4"; case "5" -> "L5"; case "6" -> "O5"; default -> "Admin"; };
            text((have ? "✔ " : "✖ ") + who + ": " + u[1], have ? 0xFF7CE38B : 0xFF7A7A7A);
        }
    }

    private void buildMessages() {
        ListTag msgs = t().getList("messages", Tag.TAG_COMPOUND);
        if (msgs.isEmpty()) text("No messages.", DIM);
        long now = t().getLong("now");
        for (Tag e : msgs) {
            CompoundTag m = (CompoundTag) e;
            long ago = Math.max(0, (now - m.getLong("t")) / 1200);
            text(Component.literal(m.getString("s")).withStyle(ChatFormatting.BOLD), m.getBoolean("r") ? 0xFF8FCFE0 : ACCENT);
            text(m.getString("b"), 0xFFDDDDDD);
            text(ago == 0 ? "just now" : ago + " min ago", 0xFF5A6A70);
            gap(5);
        }
    }

    private void buildTasks() {
        ListTag ms = t().getList("missions", Tag.TAG_COMPOUND);
        long now = t().getLong("now");
        if (ms.isEmpty()) {
            text(t().getBoolean("ascended") ? "No one gives you orders anymore." : t().getInt("rank") == 7
                    ? "The Administrator assigns, not receives. Use Orders and answer the Council's calls."
                    : "No assignments. The Council will be in touch.", DIM);
        }
        for (int i = 0; i < ms.size(); i++) {
            CompoundTag m = ms.getCompound(i);
            String type = m.getString("type");
            text(Component.literal(type.replace('_', ' ')).withStyle(ChatFormatting.BOLD), type.equals("FOUNDERS_TRIAL") ? 0xFFFF77FF : 0xFFFFD060);
            text(m.getString("desc"), 0xFFDDDDDD);
            String prog = "Progress " + m.getInt("progress") + "/" + m.getInt("target");
            if (m.getBoolean("hasPos")) prog += "   @ " + m.getInt("x") + ", " + m.getInt("z");
            text(prog, 0xFFB8E6F0);
            long exp = m.getLong("expires");
            if (exp < Long.MAX_VALUE / 2) {
                long sec = Math.max(0, (exp - now) / 20);
                text("Due in " + sec / 60 + "m " + sec % 60 + "s   (from O5-" + m.getInt("giver") + ")", 0xFF7A8A90);
            }
            final int idx = i;
            if (type.equals("SUPPLY")) {
                buttonPair("Submit", b -> send("submit", idx), "Abandon", b -> send("abandon", idx));
            } else if (!type.equals("FOUNDERS_TRIAL")) {
                button("Abandon", "Costs XP and stability.", b -> send("abandon", idx), true);
            }
            gap(5);
        }
    }

    private void buildCalls() {
        CompoundTag t = t();
        if (!t.contains("callText")) {
            int rank = t.getInt("rank");
            text("No incoming calls.", DIM);
            gap(4);
            text(rank >= 7 ? "The Council calls you for orders every few minutes. You can also Convene them from Orders."
                    : rank >= 6 ? "As O5-13 you break tied Council votes. They'll call."
                    : "The Overseers don't call staff. Rise to the Council and they will.", 0xFF8A9AA0);
            return;
        }
        text(Component.literal("☎ " + t.getString("callFrom")).withStyle(ChatFormatting.BOLD), 0xFF7CFF9A);
        gap(3);
        text(t.getString("callText"), 0xFFEEEEEE);
        gap(6);
        ListTag opts = t.getList("callOptions", Tag.TAG_STRING);
        for (int i = 0; i < opts.size(); i++) {
            final int idx = i;
            button(opts.getString(i), null, b -> send("answer", idx), true);
        }
    }

    private void buildOrders() {
        ListTag dirs = t().getList("directives", Tag.TAG_COMPOUND);
        if (dirs.isEmpty()) {
            text("Your clearance is too low to issue orders.", DIM);
            text("Requisition unlocks at Level 4.", 0xFF7A8A90);
            return;
        }
        boolean inSite = t().getBoolean("inSite");
        for (Tag e : dirs) {
            CompoundTag d = (CompoundTag) e;
            String id = d.getString("id");
            if (id.equals("site01") && inSite) continue;
            if (id.equals("return") && !inSite) continue;
            long cd = d.getLong("cd");
            String label = d.getString("label") + (cd > 0 ? "  (" + cd / 60 + ":" + String.format("%02d", cd % 60) + ")" : "");
            button(label, d.getString("desc"), b -> send("dir:" + id, 0), cd <= 0);
        }
    }

    // ------------------------------------------------------------------ render

    @Override
    public void tick() {
        if (seenVersion != ClientCareerState.version) rebuildWidgets();
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.fill(left - 2, top - 2, left + W + 2, top + H + 2, 0xFF050506);
        g.fill(left, top, left + W, top + H, BEZEL);
        g.fill(left + W / 2 - 18, top + 6, left + W / 2 + 18, top + 9, 0xFF2A2D35); // speaker
        g.fill(sx, sy, sx + sw, sy + sh, SCREEN);
        g.fill(left + W / 2 - 12, top + H - 11, left + W / 2 + 12, top + H - 5, 0xFF2A2D35); // home bar
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawString(font, "FOUNDATION SECURE LINE", sx + 3, sy + 2, ACCENT, false);
        long now = t().getLong("now");
        long day = (now + 6000) % 24000;
        String clock = String.format("%02d:%02d", day / 1000, (day % 1000) * 60 / 1000);
        g.drawString(font, clock, sx + sw - font.width(clock) - 3, sy + 2, 0xFFFFFFFF, false);
        g.enableScissor(sx, cy, sx + sw, cb);
        for (Line l : lines) {
            if (l.y() + 9 >= cy && l.y() <= cb) g.drawString(font, l.text(), l.x(), l.y(), l.color(), false);
        }
        for (Bar b : bars) {
            g.fill(b.x(), b.y(), b.x() + b.w(), b.y() + 5, 0xFF1E2A30);
            g.fill(b.x(), b.y(), b.x() + (int) (b.w() * b.pct()), b.y() + 5, b.color());
        }
        g.disableScissor();
        if (contentHeight > cb - cy) {
            int track = cb - cy;
            int thumb = Math.max(12, track * track / contentHeight);
            int pos = (int) ((track - thumb) * (scroll / (float) Math.max(1, contentHeight - track)));
            g.fill(sx + sw - 3, cy + pos, sx + sw - 1, cy + pos + thumb, 0xFF3A5560);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int max = Math.max(0, contentHeight - (cb - cy));
        int ns = Mth.clamp(scroll - (int) (scrollY * 14), 0, max);
        if (ns != scroll) {
            scroll = ns;
            rebuildWidgets();
        }
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
