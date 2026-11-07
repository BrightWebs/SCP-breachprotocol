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
        boolean hud, ask = true;
        String mood = "";
        int moodC;
        java.util.List<String> info = new java.util.ArrayList<>();
        int hearts = -1;
        /** MCA's interaction card (name, profession, mood, personality, traits, the icons and their tooltips). */
        CompoundTag card;
        long touched;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    private static State st(ServerPlayer p) { return STATES.computeIfAbsent(p.getUUID(), k -> new State()); }

    /** The Overseer seat this player is talking to face to face (0 = none). */
    public static int talkingSeat(ServerPlayer p) {
        State s = STATES.get(p.getUUID());
        if (s == null || !s.session.startsWith("npc:") || System.currentTimeMillis() - s.touched > 120_000) return 0;
        try {
            net.minecraft.world.entity.Entity e = p.serverLevel().getEntity(Integer.parseInt(s.session.substring(4)));
            return e instanceof O5MemberEntity m && m.getSeat() != null ? m.getSeat().seat : 0;
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    /** The player with a conversation open with this mob (and active in the last two minutes), or null. */
    public static ServerPlayer talkingTo(net.minecraft.world.entity.Mob m) {
        if (!(m.level() instanceof net.minecraft.server.level.ServerLevel l)) return null;
        String key = "npc:" + m.getId();
        long now = System.currentTimeMillis();
        for (ServerPlayer p : l.players()) {
            State s = STATES.get(p.getUUID());
            if (s != null && s.session.equals(key) && now - s.touched < 120_000 && p.distanceToSqr(m) < 32 * 32) return p;
        }
        return null;
    }

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
        s.ask = true;
        s.mood = "";
        s.moodC = 0;
        s.info = new java.util.ArrayList<>();
        s.hearts = -1;
        s.card = null;
    }

    /** How the person you're talking to seems right now (shown under their portrait). */
    public static void mood(ServerPlayer p, String mood, int colour) { st(p).mood = mood; st(p).moodC = colour; }

    /** In an overlay conversation: whether the choices open the window by themselves (else the player presses the answer key). */
    public static void ask(ServerPlayer p, boolean on) { st(p).ask = on; }

    /** This conversation runs as an overlay; the window opens only for choices. */
    public static void hud(ServerPlayer p, boolean on) { st(p).hud = on; }

    public static void entity(ServerPlayer p, int id) { st(p).entity = id; }

    public static void subtitle(ServerPlayer p, String sub) { st(p).subtitle = sub; }

    public static boolean hudOpen(ServerPlayer p) { State s = STATES.get(p.getUUID()); return s != null && s.hud && !s.session.isEmpty(); }

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

    public static boolean hasAnswers(ServerPlayer p) {
        for (CompoundTag o : st(p).options) if (o.getBoolean("answer")) return true;
        return false;
    }

    public static void clearOptions(ServerPlayer p) { st(p).options.clear(); st(p).input = ""; }

    /** An answer to what they just said (shown under their words, MCA-style), rather than a topic button. */
    public static void answer(ServerPlayer p, String label, String action, String hover) {
        option(p, label, action, hover, 0xFFFFFF);
        st(p).options.get(st(p).options.size() - 1).putBoolean("answer", true);
    }

    /** The facts shown down the left of the interaction screen (role, personality, traits), and trust in hearts. */
    /** The left side of the screen laid out exactly as Minecraft Comes Alive does it. */
    public static void card(ServerPlayer p, CompoundTag card) { st(p).card = card; }

    public static void info(ServerPlayer p, java.util.List<String> lines, int hearts) {
        st(p).info = new java.util.ArrayList<>(lines);
        st(p).hearts = hearts;
    }

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
        s.touched = System.currentTimeMillis();
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
        t.putBoolean("ask", s.ask);
        t.putString("mood", s.mood);
        ListTag inf = new ListTag();
        for (String i : s.info) inf.add(net.minecraft.nbt.StringTag.valueOf(i));
        t.put("info", inf);
        t.putInt("hearts", s.hearts);
        if (s.card != null) t.put("card", s.card);
        t.putInt("moodC", s.moodC);
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
