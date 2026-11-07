package com.breachprotocol.career;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.entity.O5MemberEntity;
import com.breachprotocol.entity.Seats;
import com.breachprotocol.world.Site01;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.*;

/**
 * The O5 Council in session, at Site-01's council table.
 *
 * <ul>
 * <li>The Administrator sits in the head chair: the Council takes their seats and debates the
 *     agenda. Each item is presented, argued by several Overseers, and put to a vote. The
 *     Administrator can rule (their ruling stands), let the vote stand, or interject in chat and
 *     sway the room. Up to three items per sitting; standing up suspends the session.</li>
 * <li>An Overseer player sitting in their own chair joins the debate as a voter (their vote breaks
 *     ties).</li>
 * <li>A newly made Overseer arriving in the chamber with no chair: the Council debates who gives up
 *     their seat, votes (the newcomer's vote counts double), a twelve-sided die breaks a tie, and the
 *     one voted out stands up and wanders Site-01 from then on. Their chair is the newcomer's.</li>
 * </ul>
 */
@EventBusSubscriber(modid = BreachProtocol.MODID)
public final class Council {
    private Council() {}

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Map<UUID, Long> COOLDOWN = new HashMap<>();

    private static final String[][] REASONS = {
            {}, {"We have survived worse by being patient.", "I have seen where both roads lead."},
            {"Think of the lives on the other side of the glass.", "Kindness is a containment procedure too."},
            {"> PROBABILITY MODELS FAVOUR THIS OPTION.", "> HISTORICAL PRECEDENT: 71% SUCCESS."},
            {"The Coalition will be watching how we handle this.", "Our partners will remember what we do here."},
            {"The Groups of Interest are already moving on this.", "Someone outside these walls already knows."},
            {"I've buried people who hesitated.", "Out in the field, this is the only sane call."},
            {"Tactically, it's the only move.", "Three steps ahead, this is where we want to be."},
            {"My task forces can carry it.", "Give the MTF something they can work with."},
            {"I've seen how the other choice ends.", "Trust me. I have seen this one before."},
            {"The records are clear on what happens otherwise.", "We tried the other way once. It's in the archive."},
            {"I'll sign whatever we decide, but sign this.", "The paperwork is cleaner this way."},
            {"The numbers favour it.", "It's the cheaper mistake."}};

    private static final String[] NOMINATE = {"They have served long enough.", "Nothing personal. It's arithmetic.", "They missed the last three sessions.",
            "Fresh blood needs a chair, and they've had theirs the longest.", "They argued against every reform this year.", "They'd do more good in the field."};

    // ================================================================== ticking

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 5 != 0) return;
        ServerLevel ow = event.getServer().overworld();
        long now = ow.getGameTime();
        for (ServerPlayer p : event.getServer().getPlayerList().getPlayers()) {
            Session s = SESSIONS.get(p.getUUID());
            if (s != null) {
                if (s.done || p.level() != ow) { s.close(); SESSIONS.remove(p.getUUID()); continue; }
                s.tick(now);
                continue;
            }
            if (p.level() == ow && Site01.isInside(p) && Site01.needsRebuild(p.server)) Site01.ensureBuilt(ow);
            if (p.level() != ow || !Site01.isInside(p) || now < COOLDOWN.getOrDefault(p.getUUID(), 0L)) continue;
            Rank r = Career.rank(p);
            Site01.Data d = Site01.data(p.server);
            int seat = seatOf(ow, p);
            boolean admin = r == Rank.ADMINISTRATOR || Career.isAscended(p);
            if (seat == 0 && admin) start(p, new Agenda(p, true));
            else if (seat > 0 && p.getUUID().toString().equals(d.holders.get(seat))) start(p, new Agenda(p, false));
            else if (r == Rank.OVERSEER && !admin && !d.holders.containsValue(p.getUUID().toString()) && inChamber(ow, p) && d.holders.size() < 12)
                start(p, new SeatVote(p));
        }
    }

    private static void start(ServerPlayer p, Session s) {
        SESSIONS.put(p.getUUID(), s);
        s.begin();
    }

    /** The council chair the player is sitting in (0 = head), or -1. */
    static int seatOf(ServerLevel ow, ServerPlayer p) {
        Entity v = p.getVehicle();
        if (v == null) return -1;
        return Site01.councilSeatAt(ow, Seats.chairOf(v));
    }

    static boolean inChamber(ServerLevel ow, ServerPlayer p) {
        BlockPos rel = p.blockPosition().subtract(Site01.origin(ow));
        return rel.getX() >= 17 && rel.getX() <= 37 && rel.getZ() >= 1 && rel.getZ() <= 17 && rel.getY() >= -1 && rel.getY() <= 4;
    }

    // ================================================================== commands and chat

    @SubscribeEvent
    public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("council")
                .then(Commands.literal("decide").then(Commands.argument("option", IntegerArgumentType.integer(0, 12)).executes(c -> {
                    Session s = SESSIONS.get(c.getSource().getPlayerOrException().getUUID());
                    if (s != null) s.decide(IntegerArgumentType.getInteger(c, "option"));
                    return 1;
                })))
                .then(Commands.literal("stand").executes(c -> {
                    Session s = SESSIONS.get(c.getSource().getPlayerOrException().getUUID());
                    if (s != null) s.decide(-1);
                    return 1;
                }))
                .then(Commands.literal("interject").executes(c -> {
                    Session s = SESSIONS.get(c.getSource().getPlayerOrException().getUUID());
                    if (s != null) s.interject();
                    return 1;
                })));
    }

    @SubscribeEvent
    public static void chat(ServerChatEvent event) {
        Session s = SESSIONS.get(event.getPlayer().getUUID());
        if (s == null || !s.listening) return;
        event.setCanceled(true);
        s.heard(event.getRawText());
    }

    // ================================================================== sessions

    private abstract static class Session {
        final ServerPlayer p;
        final ServerLevel l;
        final BlockPos o;
        final Map<Integer, O5MemberEntity> members = new TreeMap<>();
        final Deque<long[]> waits = new ArrayDeque<>();
        final Deque<Runnable> steps = new ArrayDeque<>();
        long nextAt;
        boolean done, awaiting, listening;
        long deadline;
        final Random rnd = new Random();

        Session(ServerPlayer p) {
            this.p = p;
            this.l = p.server.overworld();
            this.o = Site01.origin(l);
            AABB box = new AABB(o).inflate(80);
            for (O5MemberEntity m : l.getEntitiesOfClass(O5MemberEntity.class, box))
                if (m.getSeat() != null) members.put(m.getSeat().seat, m);
        }

        /** Overseers still holding a chair (not given up to a player). */
        List<Integer> seated() {
            Site01.Data d = Site01.data(p.server);
            List<Integer> out = new ArrayList<>();
            for (int n = 1; n <= 12; n++) if (!d.holders.containsKey(n) && members.containsKey(n) && !members.get(n).isUnseated()) out.add(n);
            return out;
        }

        void gather() {
            for (int n : seated()) {
                O5MemberEntity m = members.get(n);
                m.holdSeated(20 * 60 * 6);
                if (!Seats.seated(m) && m.home() != null) {
                    BlockPos h = m.home();
                    m.teleportTo(h.getX() + 0.5, h.getY(), h.getZ() + 0.5);
                    Seats.sit(m, h);
                }
            }
        }

        void close() {
            for (O5MemberEntity m : members.values()) m.holdSeated(0);
            COOLDOWN.put(p.getUUID(), l.getGameTime() + 20 * 20);
        }

        abstract void begin();
        abstract void onDecide(int option);
        abstract void onTimeout();
        void heard(String text) {}

        void tick(long now) {
            if (mustSit() && Council.seatOf(l, p) < 0) {
                tell(Component.literal("The session is suspended.").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
                done = true;
                return;
            }
            if (awaiting) {
                if (!listening && now >= deadline) { awaiting = false; onTimeout(); }
                return;
            }
            while (!steps.isEmpty() && now >= nextAt) {
                Runnable r = steps.poll();
                long[] w = waits.poll();
                nextAt = now + (w == null ? 0 : w[0]);
                r.run();
                if (awaiting || done) return;
            }
        }

        boolean mustSit() { return true; }

        /** Queue a step, then wait {@code after} ticks before the next one. */
        void then(long after, Runnable r) {
            steps.add(r);
            waits.add(new long[]{after});
        }

        void decide(int option) {
            if (!awaiting) return;
            awaiting = false;
            listening = false;
            onDecide(option);
        }

        void interject() {
            if (!awaiting) return;
            listening = true;
            tell(Component.literal("Speak, then. (Type what you say in chat.)").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }

        void await(int seconds) {
            awaiting = true;
            deadline = l.getGameTime() + seconds * 20L;
        }

        List<ServerPlayer> audience() {
            List<ServerPlayer> out = new ArrayList<>();
            BlockPos c = o.offset(27, 0, 9);
            for (ServerPlayer q : l.players()) if (q == p || q.blockPosition().closerThan(c, 48)) out.add(q);
            return out;
        }

        void tell(Component c) { for (ServerPlayer q : audience()) q.sendSystemMessage(c); }

        void speak(int seat, String text) {
            O5 who = O5.seat(seat);
            tell(Component.literal(who.sender() + ": ").withStyle(ChatFormatting.DARK_RED).append(Component.literal(text).withStyle(ChatFormatting.GRAY)));
            O5MemberEntity m = members.get(seat);
            if (m != null) {
                m.getLookControl().setLookAt(p, 30, 30);
                m.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            }
            l.playSound(null, m != null ? m.blockPosition() : p.blockPosition(), SoundEvents.VILLAGER_AMBIENT, SoundSource.NEUTRAL, 0.25F, 0.6F);
        }

        void playerSays(String text) {
            String who = Career.rank(p) == Rank.ADMINISTRATOR || Career.isAscended(p) ? "The Administrator" : "O5-13 (" + p.getName().getString() + ")";
            tell(Component.literal(who + ": ").withStyle(ChatFormatting.LIGHT_PURPLE).append(Component.literal(text).withStyle(ChatFormatting.WHITE)));
        }

        static MutableComponent button(String label, String cmd, String hover, ChatFormatting colour) {
            return Component.literal("[" + label + "]").withStyle(s -> s.withColor(colour).withBold(true)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(hover))));
        }

        void title(String t, String sub) {
            p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 50, 20));
            p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(t).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)));
            p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(sub).withStyle(ChatFormatting.GRAY)));
        }
    }

    // ---------------------------------------------------------------- the agenda

    private static final class Agenda extends Session {
        final boolean admin;
        final List<Calls.Template> pool;
        final Set<String> used = new HashSet<>();
        Calls.Template item;
        int caller, items;
        int[] votes;
        final Map<Integer, Integer> stance = new HashMap<>();

        Agenda(ServerPlayer p, boolean admin) {
            super(p);
            this.admin = admin;
            this.pool = Calls.councilPool(admin);
        }

        @Override
        void begin() {
            gather();
            title("THE COUNCIL IS IN SESSION", admin ? "The Administrator presides" : "O5-13 takes their seat");
            p.playNotifySound(SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 0.8F, 0.6F);
            List<Integer> s = seated();
            int chair = s.contains(7) ? 7 : s.isEmpty() ? 7 : s.get(0);
            then(50, () -> speak(chair, admin ? "The Administrator is seated. This session of the O5 Council is called to order."
                    : "Thirteen is seated. We have a quorum. Let's begin."));
            next();
        }

        void next() {
            if (items >= 3 || pool.isEmpty()) {
                then(0, () -> {
                    List<Integer> s = seated();
                    speak(s.contains(7) ? 7 : s.isEmpty() ? 7 : s.get(0), "That concludes the agenda. This session is adjourned.");
                    done = true;
                });
                return;
            }
            items++;
            List<Calls.Template> fresh = pool.stream().filter(t -> !used.contains(t.id())).toList();
            item = (fresh.isEmpty() ? pool : fresh).get(rnd.nextInt((fresh.isEmpty() ? pool : fresh).size()));
            used.add(item.id());
            List<Integer> s = new ArrayList<>(seated());
            if (s.isEmpty()) { done = true; return; }
            caller = s.contains(item.caller()) ? item.caller() : s.get(rnd.nextInt(s.size()));
            votes = new int[item.options().size()];
            stance.clear();
            then(70, () -> {
                tell(Component.literal("— Item " + items + " —").withStyle(ChatFormatting.DARK_GRAY));
                speak(caller, item.text());
            });
            s.remove((Integer) caller);
            Collections.shuffle(s, rnd);
            for (int i = 0; i < Math.min(3, s.size()); i++) {
                int who = s.get(i);
                then(70, () -> {
                    int st = rnd.nextInt(item.options().size());
                    stance.put(who, st);
                    String[] rs = REASONS[who];
                    speak(who, "I say \"" + item.options().get(st).label() + "\". " + rs[rnd.nextInt(rs.length)]);
                });
            }
            then(40, this::vote);
        }

        void vote() {
            Arrays.fill(votes, 0);
            for (int n : seated()) {
                Integer st = stance.get(n);
                votes[st != null && rnd.nextFloat() < 0.75F ? st : rnd.nextInt(votes.length)]++;
            }
            List<Integer> s = seated();
            speak(s.contains(7) ? 7 : caller, "The Council votes.");
            tell(tally());
            prompt();
        }

        Component tally() {
            MutableComponent c = Component.literal("VOTE  ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
            for (int i = 0; i < votes.length; i++) {
                if (i > 0) c.append(Component.literal("  ·  ").withStyle(ChatFormatting.DARK_GRAY));
                c.append(Component.literal(item.options().get(i).label() + ": " + votes[i]).withStyle(ChatFormatting.YELLOW));
            }
            return c;
        }

        int majority() {
            int best = 0;
            for (int i = 1; i < votes.length; i++) if (votes[i] > votes[best] || votes[i] == votes[best] && stance.get(caller) != null && stance.get(caller) == i) best = i;
            return best;
        }

        void prompt() {
            MutableComponent c = Component.literal(admin ? "Your ruling, Administrator:  " : "Your vote, Thirteen:  ").withStyle(ChatFormatting.LIGHT_PURPLE);
            for (int i = 0; i < item.options().size(); i++)
                c.append(button(item.options().get(i).label(), "/council decide " + i, admin ? "Overrule the Council: your ruling stands" : "Cast your vote", ChatFormatting.AQUA)).append(" ");
            c.append(button(admin ? "Let the vote stand" : "Abstain", "/council stand", admin ? "The Council's majority decides" : "Leave it to the Council", ChatFormatting.GRAY)).append(" ");
            c.append(button("Interject", "/council interject", "Say something to the Council first", ChatFormatting.GOLD));
            p.sendSystemMessage(c);
            await(30);
        }

        @Override
        void onTimeout() {
            tell(Component.literal(admin ? "The Administrator lets the vote stand." : "Thirteen abstains.").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
            resolve(majority(), false);
        }

        @Override
        void onDecide(int option) {
            if (option < 0 || option >= votes.length) { onTimeout(); return; }
            if (admin) {
                playerSays(item.options().get(option).label() + ". That is my ruling.");
                resolve(option, option != majority());
            } else {
                playerSays("I vote \"" + item.options().get(option).label() + "\".");
                votes[option]++;
                int best = 0;
                for (int i = 1; i < votes.length; i++) if (votes[i] > votes[best] || votes[i] == votes[best] && i == option) best = i;
                resolve(best, false);
            }
        }

        void resolve(int option, boolean overruled) {
            Calls.Option chosen = item.options().get(option);
            tell(Component.literal("Decided: " + chosen.label()).withStyle(ChatFormatting.GOLD));
            if (overruled) {
                List<Integer> s = seated();
                int who = s.isEmpty() ? caller : s.get(rnd.nextInt(s.size()));
                then(40, () -> speak(who, rnd.nextBoolean() ? "As you command, Administrator." : "Noted for the record. We'll make it work."));
            }
            then(50, () -> {
                String reply;
                try {
                    reply = chosen.effect().apply(p, Career.data(p));
                } catch (Throwable t) {
                    reply = "It's done.";
                }
                speak(caller, reply);
                Career.sync(p);
            });
            then(80, () -> {});
            next();
        }

        @Override
        void heard(String text) {
            listening = false;
            playerSays(text);
            String low = text.toLowerCase(Locale.ROOT);
            int swayed = -1;
            for (int i = 0; i < item.options().size(); i++) {
                for (String w : item.options().get(i).label().toLowerCase(Locale.ROOT).split(" "))
                    if (w.length() > 3 && low.contains(w)) swayed = i;
            }
            List<Integer> s = new ArrayList<>(seated());
            Collections.shuffle(s, rnd);
            int moved = 0;
            if (swayed >= 0) {
                for (int i = 0; i < votes.length && moved < 3; i++) {
                    while (i != swayed && votes[i] > 0 && moved < 3 && rnd.nextFloat() < 0.6F) { votes[i]--; votes[swayed]++; moved++; }
                }
            }
            final int sw = swayed, mv = moved;
            if (!s.isEmpty()) {
                int a = s.get(0);
                then(40, () -> speak(a, sw >= 0 && mv > 0 ? "A fair point. I'll change my vote." : sw >= 0 ? "With respect, that changes nothing for me." : "Noted."));
            }
            if (s.size() > 1 && mv > 1) {
                int b = s.get(1);
                then(40, () -> speak(b, "Agreed. Put me down for \"" + item.options().get(sw).label() + "\"."));
            }
            then(30, () -> { tell(tally()); prompt(); });
        }
    }

    // ---------------------------------------------------------------- the seat vote

    private static final class SeatVote extends Session {
        final Map<Integer, Integer> tally = new HashMap<>();
        List<Integer> candidates;

        SeatVote(ServerPlayer p) { super(p); }

        @Override
        boolean mustSit() { return false; }

        @Override
        void begin() {
            gather();
            candidates = seated();
            if (candidates.isEmpty()) { done = true; return; }
            title("THE COUNCIL CONVENES", "A seat must be made");
            int lead = candidates.contains(7) ? 7 : candidates.get(0);
            then(60, () -> speak(lead, "Thirteen. Welcome to Site-01. Sit anywhere you like... except you can't. There are no empty chairs."));
            int founder = candidates.contains(1) ? 1 : lead;
            then(70, () -> speak(founder, "This chamber holds twelve chairs and the Administrator's. One of us must make room. We vote, as we always have."));
            List<Integer> speakers = new ArrayList<>(candidates);
            Collections.shuffle(speakers, rnd);
            for (int i = 0; i < Math.min(5, speakers.size()); i++) {
                int who = speakers.get(i);
                then(70, () -> {
                    int pick = nominee(who);
                    tally.merge(pick, 1, Integer::sum);
                    speak(who, "I nominate O5-" + pick + ". " + NOMINATE[rnd.nextInt(NOMINATE.length)]);
                });
            }
            then(50, () -> {
                for (int i = 5; i < speakers.size(); i++) tally.merge(nominee(speakers.get(i)), 1, Integer::sum);
                tell(Component.literal("The rest vote in silence.").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
                show();
                MutableComponent c = Component.literal("Your vote counts twice, Thirteen:  ").withStyle(ChatFormatting.LIGHT_PURPLE);
                List<Integer> top = top();
                for (int n : top) c.append(button("O5-" + n, "/council decide " + n, "Vote to unseat O5-" + n + " (" + O5.seat(n).nickname + ")", ChatFormatting.AQUA)).append(" ");
                c.append(button("Leave it to them", "/council stand", "Don't vote", ChatFormatting.GRAY));
                p.sendSystemMessage(c);
                await(30);
            });
        }

        /** The five most-nominated (or any five) seated Overseers. */
        List<Integer> top() {
            List<Integer> c = new ArrayList<>(candidates);
            c.sort((a, b) -> tally.getOrDefault(b, 0) - tally.getOrDefault(a, 0));
            return new ArrayList<>(c.subList(0, Math.min(5, c.size())));
        }

        int nominee(int voter) {
            List<Integer> c = new ArrayList<>(candidates);
            c.remove((Integer) voter);
            if (c.isEmpty()) return voter;
            // a little herd instinct: popular names attract more votes
            for (Map.Entry<Integer, Integer> e : tally.entrySet()) if (c.contains(e.getKey()) && rnd.nextFloat() < 0.15F * e.getValue()) return e.getKey();
            return c.get(rnd.nextInt(c.size()));
        }

        void show() {
            MutableComponent c = Component.literal("NOMINATIONS  ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
            boolean first = true;
            for (int n : candidates) {
                int v = tally.getOrDefault(n, 0);
                if (v == 0) continue;
                if (!first) c.append(Component.literal("  ·  ").withStyle(ChatFormatting.DARK_GRAY));
                first = false;
                c.append(Component.literal("O5-" + n + ": " + v).withStyle(ChatFormatting.YELLOW));
            }
            tell(c);
        }

        @Override
        void onDecide(int option) {
            int seat = -1;
            for (int n : top()) if (n == option) seat = n;
            if (seat > 0) {
                playerSays("My vote goes to O5-" + seat + ".");
                tally.merge(seat, 2, Integer::sum);
            }
            conclude();
        }

        @Override
        void onTimeout() {
            tell(Component.literal("Thirteen leaves it to the Council.").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
            conclude();
        }

        void conclude() {
            int max = tally.values().stream().max(Integer::compare).orElse(0);
            List<Integer> tied = new ArrayList<>();
            for (int n : candidates) if (tally.getOrDefault(n, 0) == max) tied.add(n);
            int out;
            if (tied.size() > 1) {
                int acct = candidates.contains(12) ? 12 : candidates.get(0);
                int roll = 0;
                while (!tied.contains(roll)) roll = 1 + rnd.nextInt(12);
                final int fr = roll;
                then(50, () -> speak(acct, "A tie between " + names(tied) + ". Then the die decides."));
                then(60, () -> {
                    tell(Component.literal("O5-" + acct + " rolls a twelve-sided die... it shows " + fr + ".").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
                    p.playNotifySound(SoundEvents.BONE_BLOCK_PLACE, SoundSource.BLOCKS, 1F, 1.4F);
                });
                out = roll;
            } else {
                out = tied.isEmpty() ? candidates.get(0) : tied.get(0);
            }
            final int gone = out;
            then(60, () -> {
                tell(Component.literal("O5-" + gone + " \"" + O5.seat(gone).nickname + "\" gives up their seat.").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
                speak(gone, rnd.nextBoolean() ? "So be it. I'll be around. The Foundation doesn't let anyone truly leave." : "The vote is the vote. Take good care of my chair, Thirteen.");
            });
            then(40, () -> {
                O5MemberEntity m = members.get(gone);
                Site01.Data d = Site01.data(p.server);
                d.holders.put(gone, p.getUUID().toString());
                d.setDirty();
                if (m != null) m.unseat();
                int lead = seated().contains(7) ? 7 : seated().isEmpty() ? gone : seated().get(0);
                speak(lead, "Thirteen, the chair of O5-" + gone + " is yours. Sit, and the Council will sit with you.");
                p.sendSystemMessage(Component.literal("Your council chair: the one O5-" + gone + " left, " + side(gone) + ".").withStyle(ChatFormatting.LIGHT_PURPLE));
                done = true;
            });
        }

        static String side(int seat) {
            return seat <= 5 ? "on the left side of the table" : seat <= 10 ? "on the right side of the table" : "at the far end, facing the Administrator";
        }

        static String names(List<Integer> seats) {
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < seats.size(); i++) b.append(i == 0 ? "" : i == seats.size() - 1 ? " and " : ", ").append("O5-").append(seats.get(i));
            return b.toString();
        }
    }
}
