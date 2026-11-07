package com.breachprotocol.entity;

import com.breachprotocol.network.DialoguePayload;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;

/**
 * The conversation window, server side. A conversation has a title, the person you're talking to
 * (shown turning in the window), what's been said so far, and the choices you can click. Choices
 * are commands (the same ones as before), run as you when clicked; typed text replaces {text}.
 */
public final class Dialogue {
    private Dialogue() {}

    private static final class State {
        String session = "", title = "", subtitle = "";
        int entity = -1;
        final Deque<CompoundTag> lines = new ArrayDeque<>();
        final List<CompoundTag> options = new ArrayList<>();
        String input = "";
        boolean hud;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    private static State st(ServerPlayer p) { return STATES.computeIfAbsent(p.getUUID(), k -> new State()); }

    /** Starts a new conversation (clears the history unless it's the same session). */
    public static void begin(ServerPlayer p, String session, String title, String subtitle, int entityId) {
        State s = st(p);
        if (!s.session.equals(session)) s.lines.clear();
        s.session = session;
        s.title = title;
        s.subtitle = subtitle;
        s.entity = entityId;
        s.options.clear();
        s.input = "";
        s.hud = false;
    }

    /** This conversation runs as an overlay; the window opens only for choices. */
    public static void hud(ServerPlayer p, boolean on) { st(p).hud = on; }

    public static void entity(ServerPlayer p, int id) { st(p).entity = id; }

    public static void subtitle(ServerPlayer p, String sub) { st(p).subtitle = sub; }

    public static boolean active(ServerPlayer p, String session) { return st(p).session.equals(session); }

    /** Something said. {@code colour} is an RGB colour for the speaker's name. */
    public static void line(ServerPlayer p, String who, String text, int colour) {
        State s = st(p);
        CompoundTag t = new CompoundTag();
        t.putString("who", who);
        t.putString("text", text);
        t.putInt("c", colour);
        s.lines.addLast(t);
        while (s.lines.size() > 14) s.lines.removeFirst();
    }

    public static void clearOptions(ServerPlayer p) { st(p).options.clear(); st(p).input = ""; }

    public static void option(ServerPlayer p, String label, String action, String hover, int colour) {
        CompoundTag t = new CompoundTag();
        t.putString("label", label);
        t.putString("action", action);
        t.putString("hover", hover);
        t.putInt("c", colour);
        st(p).options.add(t);
    }

    /** A text box: typed text is sent with {@code action} (where {text} is replaced). */
    public static void input(ServerPlayer p, String action) { st(p).input = action; }

    /** Sends the window. {@code open}: open it if it isn't already (otherwise it just updates if open). */
    public static void send(ServerPlayer p, boolean open) {
        State s = st(p);
        CompoundTag t = new CompoundTag();
        t.putString("session", s.session);
        t.putString("title", s.title);
        t.putString("subtitle", s.subtitle);
        t.putInt("entity", s.entity);
        ListTag l = new ListTag();
        l.addAll(s.lines);
        t.put("lines", l);
        ListTag o = new ListTag();
        o.addAll(s.options);
        t.put("options", o);
        t.putString("input", s.input);
        t.putBoolean("open", open);
        t.putBoolean("hud", s.hud);
        PacketDistributor.sendToPlayer(p, new DialoguePayload(t));
    }

    public static void close(ServerPlayer p) {
        CompoundTag t = new CompoundTag();
        t.putBoolean("close", true);
        t.putString("session", st(p).session);
        PacketDistributor.sendToPlayer(p, new DialoguePayload(t));
        st(p).session = "";
    }

    /** A choice was clicked. */
    public static void action(ServerPlayer p, String action, String text) {
        if (action.equals("close")) { if (!st(p).session.isEmpty()) close(p); return; }
        if (!action.startsWith("/")) return;
        String clean = text == null ? "" : text.replaceAll("[\\p{Cntrl}]", "").trim();
        if (clean.length() > 200) clean = clean.substring(0, 200);
        String cmd = action.substring(1).replace("{text}", clean);
        p.server.getCommands().performPrefixedCommand(p.createCommandSourceStack().withSuppressedOutput(), cmd);
    }
}
