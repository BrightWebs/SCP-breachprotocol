package com.breachprotocol.career;

import com.breachprotocol.entity.Dialogue;
import com.breachprotocol.world.Site01;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;

/**
 * Motions from the floor of the Council, and what the Council makes of the Altar.
 *
 * <p><b>Succession.</b> An Overseer may move that they succeed the Administrator, once every three
 * days, at the end of a sitting. The chair has priority: if the Administrator presides, or can be
 * reached, they allow the vote or deny it. An Administrator who hasn't been seen for three days can be
 * voted out in their absence (they find out when they return). SCP-Ω can't be voted out at all.
 * The Council votes on records: anomalies held at the mover's sites, stability, loose anomalies put
 * down, service; set against the incumbent's.</p>
 *
 * <p><b>The Altar.</b> Once the Administrator has sworn before it, the Council talks about it.</p>
 */
public final class Motions {
    private Motions() {}

    /** Administrators asked for leave to hold a vote, and the sitting that's waiting on them. */
    private static final Map<UUID, Council.Agenda> ASKING = new HashMap<>();

    public static ServerPlayer admin(MinecraftServer s) { return Containment.player(s, Site01.data(s).admin); }

    private static long day(MinecraftServer s) { return s.overworld().getDayTime() / 24000; }

    static boolean canMove(ServerPlayer p) {
        if (Career.rank(p) != Rank.OVERSEER || Career.isAscended(p)) return false;
        Long last = Career.data(p).agendaUsed.get("m_succession");
        return last == null || day(p.server) - last >= 3;
    }

    static int score(ServerPlayer p) {
        CareerData d = Career.data(p);
        return Containment.heldBy(p) * 2 + d.stability / 10 + Math.min(8, d.anomalyKills) + Math.min(6, d.xp / 10);
    }

    static String hint(ServerPlayer p) {
        return "Your record: " + Containment.heldBy(p) + " held at your sites, stability " + Career.data(p).stability + "%, " + Career.data(p).anomalyKills
                + " loose anomalies put down. The Council votes on records, not speeches.";
    }

    static Council.Agenda awaitingAnswerFrom(ServerPlayer q) { return ASKING.get(q.getUUID()); }

    static void forget(Council.Agenda a) { ASKING.values().removeIf(x -> x == a); }

    // ================================================================== raising the motion

    static void raise(Council.Agenda a, ServerPlayer mover) {
        MinecraftServer srv = mover.server;
        CareerData md = Career.data(mover);
        md.agendaUsed.put("m_succession", day(srv));
        md.dirty = true;
        Site01.Data sd = Site01.data(srv);
        String name = mover.getGameProfile().getName();
        a.says(mover, "I move that I succeed the Administrator.");
        a.sayChair("A motion of succession. It's been a long time since anyone said those words at this table.");
        if (!sd.admin.isEmpty() && sd.admin.equals(sd.ascendant)) {
            a.queue(50, () -> a.sayAs(a.chairs().contains(1) ? 1 : a.chairs().isEmpty() ? 7 : a.chairs().get(0),
                    "You would vote to replace SCP-Ω? There is nothing in the bylaws for deposing a god, Thirteen. The motion is out of order."));
            a.queue(30, a::finish);
            return;
        }
        if (sd.admin.isEmpty()) {
            a.queue(40, () -> a.announce(Component.literal("The head of the table is empty. The Council votes.").withStyle(ChatFormatting.GOLD)));
            vote(a, mover, null);
            return;
        }
        ServerPlayer incumbent = admin(srv);
        if (incumbent != null) {
            ASKING.put(incumbent.getUUID(), a);
            if (a.presided() && a.chairPlayer() == incumbent) {
                a.queue(40, () -> {
                    a.sayChair("Administrator, the motion is against your chair. The chair has priority: you may allow the vote, or deny it.");
                    Dialogue.clearOptions(incumbent);
                    Dialogue.option(incumbent, "Allow the vote", "/council motion allow", "The Council votes on " + name + "'s record against yours. You could lose the chair.", 0xFFC94D);
                    Dialogue.option(incumbent, "Deny the motion", "/council motion deny", "The chair has priority. The motion falls.", 0xFF8A8F);
                    Dialogue.send(incumbent, true);
                    a.hold(45);
                });
            } else {
                a.queue(40, () -> {
                    a.sayChair("The Administrator isn't in the chamber, but can be reached. The chair has priority. We ask for leave.");
                    MutableComponent m = Component.literal("[O5 COUNCIL] ").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
                            .append(Component.literal(name + " has moved that they succeed you. The Council asks your leave to vote.  ").withStyle(ChatFormatting.RED))
                            .append(button("[Allow the vote]", "/council motion allow", "The Council votes on their record against yours", ChatFormatting.GOLD))
                            .append(Component.literal("  "))
                            .append(button("[Deny it]", "/council motion deny", "The chair has priority. The motion falls.", ChatFormatting.RED));
                    incumbent.sendSystemMessage(m);
                    incumbent.playNotifySound(net.minecraft.sounds.SoundEvents.BELL_BLOCK, net.minecraft.sounds.SoundSource.PLAYERS, 1F, 0.5F);
                    a.hold(60);
                });
            }
            return;
        }
        long away = (srv.overworld().getGameTime() - sd.adminSeen) / 24000;
        if (away < 3) {
            a.queue(40, () -> a.sayChair("The Administrator was among us " + (away == 0 ? "today" : away == 1 ? "yesterday" : away + " days ago")
                    + ". A motion against an absent Administrator needs three days of silence from the chair. The motion is tabled."));
            a.queue(30, a::finish);
            return;
        }
        a.queue(40, () -> a.sayChair("The Administrator hasn't been seen in " + away + " days. The bylaws allow it. The Council votes in their absence."));
        vote(a, mover, null);
    }

    private static Component button(String text, String cmd, String hover, ChatFormatting c) {
        return Component.literal(text).withStyle(st -> st.withColor(c).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(hover))));
    }

    /** The Administrator's answer to a request for leave. */
    static void answer(Council.Agenda a, ServerPlayer who, boolean allow) {
        if (ASKING.get(who.getUUID()) != a || a.mover == null) return;
        ASKING.remove(who.getUUID());
        Dialogue.clearOptions(who);
        if (Dialogue.active(who, "council")) Dialogue.send(who, false);
        ServerPlayer mover = a.mover;
        if (!allow) {
            a.announce(Component.literal("The Administrator denies the motion. The chair has priority.").withStyle(ChatFormatting.GOLD));
            a.queue(40, () -> a.sayChair("So noted. The motion falls."));
            Career.message(mover, O5.O5_7, "The Administrator denied your motion. Build your record, Thirteen. The chair can't deny the Council forever.");
            a.queue(30, a::finish);
            return;
        }
        a.announce(Component.literal("The Administrator allows the vote.").withStyle(ChatFormatting.GOLD));
        if (a.chairPlayer() != who) who.sendSystemMessage(Component.literal("You allowed the vote. The Council is voting now.").withStyle(ChatFormatting.GRAY));
        vote(a, mover, who);
    }

    // ================================================================== the vote

    private static void vote(Council.Agenda a, ServerPlayer mover, ServerPlayer incumbent) {
        MinecraftServer srv = mover.server;
        int ms = score(mover), is = incumbent != null ? score(incumbent) : Site01.data(srv).admin.isEmpty() ? 0 : 10;
        double prob = Math.max(0.05, Math.min(0.92, 0.3 + 0.05 * (ms - is)));
        Random r = a.dice();
        List<Integer> seats = new ArrayList<>(a.chairs());
        if (seats.isEmpty()) { a.queue(20, a::finish); return; }
        Map<Integer, Boolean> ballot = new HashMap<>();
        for (int n : seats) ballot.put(n, r.nextDouble() < prob);
        CareerData md = Career.data(mover);
        int held = Containment.heldBy(mover);
        String name = mover.getGameProfile().getName();
        List<Integer> speakers = new ArrayList<>(seats);
        Collections.shuffle(speakers, r);
        for (int i = 0; i < Math.min(3, speakers.size()); i++) {
            int who = speakers.get(i);
            boolean yes = ballot.get(who);
            String why = yes ? pick(r,
                    held > 0 ? "Thirteen holds " + held + " anomal" + (held == 1 ? "y" : "ies") + " in their own cells. I'll follow someone who can keep what they catch." : "Thirteen has kept their site standing. That's more than most.",
                    "Stability under Thirteen's hand is " + md.stability + "%. I've seen worse at the head of this table.",
                    incumbent == null && !Site01.data(srv).admin.isEmpty() ? "The head of this table should be someone who turns up." : "The Foundation needs a fresh hand. I vote yes.",
                    md.anomalyKills > 0 ? "Thirteen has put down " + md.anomalyKills + " loose anomal" + (md.anomalyKills == 1 ? "y" : "ies") + " personally. That's not nothing." : "Thirteen has never flinched at this table. That counts.")
                    : pick(r,
                    "Thirteen has barely warmed that chair. The head of the table takes longer.",
                    "Records aren't everything. Ask the dead who had good ones.",
                    incumbent != null ? "The Administrator has kept this Foundation standing. I won't vote against that." : "I don't vote against the chair while it's empty and can't answer.",
                    held == 0 ? "Not one anomaly sits in a cell of Thirteen's. Come back with a full wing." : "Ambition is a fine thing in an Overseer. In an Administrator it's a breach waiting to happen.");
            a.queue(70, () -> a.sayAs(who, (yes ? "Aye. " : "Nay. ") + why));
        }
        int ayes = (int) ballot.values().stream().filter(b -> b).count(), nays = seats.size() - ayes;
        boolean carried = ayes > seats.size() / 2;
        a.queue(50, () -> a.announce(Component.literal("VOTE ON SUCCESSION  ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal("Aye: " + ayes + "  ·  Nay: " + nays).withStyle(ChatFormatting.YELLOW))));
        if (carried) a.queue(50, () -> install(a, mover, incumbent));
        else {
            a.queue(50, () -> {
                a.sayChair("The motion fails. O5-13 keeps their chair" + (Site01.data(srv).admin.isEmpty() ? ", and the head of the table stays empty." : ", and the Administrator keeps theirs."));
                Career.changeStability(mover, -2);
                if (incumbent != null) Career.message(incumbent, O5.O5_7, "The Council voted. " + name + "'s motion failed, " + ayes + " to " + nays + ". The chair is still yours.");
            });
            a.queue(30, a::finish);
        }
    }

    private static void install(Council.Agenda a, ServerPlayer mover, ServerPlayer incumbent) {
        MinecraftServer srv = mover.server;
        Site01.Data sd = Site01.data(srv);
        String name = mover.getGameProfile().getName();
        if (incumbent != null) {
            CareerData id = Career.data(incumbent);
            Site01.releaseAdministrator(incumbent);
            id.setRank(Rank.OVERSEER);
            id.inducted = false;
            id.dirty = true;
            Career.message(incumbent, O5.O5_7, "The Council has voted. The head of the table belongs to " + name + " now. You are an Overseer again; your chair will have to be made, like anyone's.");
            Career.sync(incumbent);
        } else if (!sd.admin.isEmpty()) {
            // the absent Administrator finds out when they come back (see checkDeposed)
            sd.admin = "";
            sd.setDirty();
        }
        a.sayChair("The motion carries. " + name + ", the head of the table is yours. The chair you leave goes back to the one you took it from.");
        Career.promote(mover, Rank.ADMINISTRATOR);
        WorldNews.record(srv, WorldNews.Kind.PROMOTION, "succession", "the Council voted " + name + " in as the new Administrator", 4, name);
        a.queue(40, a::finish);
    }

    /** An Administrator who was voted out while away learns it the next time they're about. */
    static void checkDeposed(ServerPlayer p) {
        CareerData d = Career.data(p);
        if (d.getRank() != Rank.ADMINISTRATOR || d.ascended) return;
        Site01.Data sd = Site01.data(p.server);
        String me = p.getUUID().toString();
        if (sd.admin.isEmpty()) { Site01.claimAdministrator(p); return; }
        if (sd.admin.equals(me)) return;
        d.setRank(Rank.OVERSEER);
        d.inducted = false;
        d.dirty = true;
        ServerPlayer now = admin(p.server);
        String who = now != null ? now.getGameProfile().getName() : "another Overseer";
        Career.message(p, O5.O5_7, "While you were away, the Council voted on a motion of succession. It carried. " + who + " holds the head of the table now. You are O5-13 again, and you'll need a chair.");
        p.sendSystemMessage(Component.literal("You are no longer the Administrator.").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
        Career.sync(p);
    }

    private static String pick(Random r, String... s) { return s[r.nextInt(s.length)]; }

    // ================================================================== the Council and the Altar

    private static final Map<String, String> TITLES = Map.of("m_altar", "The Administrator and the Altar", "m_altar_o5", "Word from beyond the Gate");

    public static String title(String id) { return TITLES.getOrDefault(id, "A motion"); }

    private static String stab(ServerPlayer p, int n, String text) {
        Career.changeStability(p, n);
        return text + "  [Stability " + (n >= 0 ? "+" : "") + n + "]";
    }

    /** Agenda items about the Altar: the Council knows when the Administrator has sworn before it. */
    static List<Calls.Template> agenda(ServerPlayer p, boolean admin) {
        List<Calls.Template> out = new ArrayList<>();
        CareerData d = Career.data(p);
        if (admin && d.trial == 1 && !d.agendaUsed.containsKey("m_altar")) {
            out.add(new Calls.Template("m_altar", 1, true,
                    "Administrator. Before the business of the week. We know where you went. Beyond the Gate. Some of us have stood in front of that thing; one of us said no to it. The Council has a right to ask why.",
                    List.of(new Calls.Option("It is not the Council's concern", (q, x) -> stab(q, -2, "Understood. The Council will remember it wasn't consulted.")),
                            new Calls.Option("So that none of you ever have to", (q, x) -> stab(q, 3, "...Then may it cost you less than it would have cost me.")),
                            new Calls.Option("Then vote: stand with me, or don't", (q, x) -> {
                                boolean with = Career.data(q).stability >= 50;
                                return stab(q, with ? 5 : -4, with ? "The Council stands with you. Eight to four. Don't make us regret it."
                                        : "Seven to five against. We won't stop you. We wanted it on the record that we tried.");
                            }))));
        }
        if (!admin && !d.agendaUsed.containsKey("m_altar_o5")) {
            WorldNews.Item sworn = null;
            for (WorldNews.Item i : WorldNews.recent(p.server, 30)) if (i.kind() == WorldNews.Kind.KNOWLEDGE && i.id().equals("altar_sworn")) sworn = i;
            if (sworn != null) {
                out.add(new Calls.Template("m_altar_o5", 1, false,
                        "Something you should know, Thirteen. The Administrator went beyond the Gate " + WorldNews.ago(p.server, sworn) + ", and swore before the Altar. It asks a price. I've seen the price. What does this Council do?",
                        List.of(new Calls.Option("Stand with the Administrator", (q, x) -> {
                                    ServerPlayer chief = admin(q.server);
                                    if (chief != null) Career.message(chief, O5.O5_1, "The Council met without you and voted to stand with you. Whatever the Altar asked. O5-13 carried it.");
                                    return stab(q, 2, "Then we stand with them. And we watch.");
                                }),
                                new Calls.Option("Demand an explanation", (q, x) -> {
                                    ServerPlayer chief = admin(q.server);
                                    if (chief != null) Career.message(chief, O5.O5_7, "The Council demands an explanation of your oath at the Altar, Administrator. Raise it at your next sitting.");
                                    return stab(q, -1, "A formal demand goes to the Administrator. They won't like it.");
                                }),
                                new Calls.Option("Prepare for what comes after", (q, x) -> {
                                    WorldNews.record(q.server, WorldNews.Kind.DECISION, "after", "the Council quietly drew up plans for a Foundation without an Administrator", 0, "the Council");
                                    return stab(q, 1, "Contingencies. The Foundation has one for everything. Now it has one for this.");
                                }))));
            }
        }
        return out;
    }
}
