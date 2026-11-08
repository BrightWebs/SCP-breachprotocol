package com.breachprotocol.career;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.entity.O5MemberEntity;
import com.breachprotocol.entity.Seats;
import com.breachprotocol.world.Site01;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.breachprotocol.entity.Dialogue;
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
        Site01.Data sd = Site01.data(event.getServer());
        for (ServerPlayer p : event.getServer().getPlayerList().getPlayers()) {
            if (Site01.isAdministrator(p) && now - sd.adminSeen > 1200) { sd.adminSeen = now; sd.setDirty(); }
            Motions.checkDeposed(p);
            Session s = SESSIONS.get(p.getUUID());
            if (s != null) {
                if (s.done || p.level() != ow) { s.close(); SESSIONS.remove(p.getUUID()); continue; }
                s.tick(now);
                continue;
            }
            if (p.level() == ow && Site01.isInside(p) && Site01.needsRebuild(p.server)) Site01.ensureBuilt(ow);
            if (p.level() == ow && Site01.isInside(p) && !sd.returning.isEmpty()) returnSeats(ow);
            if (p.level() != ow || !Site01.isInside(p)) continue;
            Rank r = Career.rank(p);
            int seat = seatOf(ow, p);
            boolean admin = r == Rank.ADMINISTRATOR || Career.isAscended(p);
            boolean holder = seat > 0 && p.getUUID().toString().equals(sd.holders.get(seat));
            // the Administrator has priority: an Overseer who sits down while the Administrator presides joins that session
            if (holder && !admin && PRESIDING != null && !PRESIDING.done) { PRESIDING.join(p); continue; }
            if (now < COOLDOWN.getOrDefault(p.getUUID(), 0L)) continue;
            if (seat == 0 && admin) {
                // an Overseer's sitting gives way to the Administrator's
                for (Session o : new ArrayList<>(SESSIONS.values())) if (o instanceof Agenda a && !a.admin && !a.done) a.defer();
                start(p, new Agenda(p, true));
            } else if (holder) start(p, new Agenda(p, false));
            else if (r == Rank.OVERSEER && !admin && !sd.holders.containsValue(p.getUUID().toString()) && inChamber(ow, p) && sd.holders.size() < 12)
                start(p, new SeatVote(p));
        }
    }

    /** The Administrator's sitting, while one is under way: Overseers join it rather than starting their own. */
    private static Agenda PRESIDING;

    /**
     * A player gives up their Council chair (they became the Administrator, or ascended): the Overseer
     * they displaced walks back to it, or a successor is appointed if that Overseer is gone.
     */
    public static void vacate(ServerPlayer p) {
        Site01.Data d = Site01.data(p.server);
        String me = p.getUUID().toString();
        boolean any = false;
        for (Iterator<Map.Entry<Integer, String>> it = d.holders.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, String> e = it.next();
            if (!e.getValue().equals(me)) continue;
            d.returning.add(e.getKey());
            it.remove();
            any = true;
        }
        if (!any) return;
        d.setDirty();
        if (d.built) returnSeats(p.server.overworld());
    }

    /** Seats given back: their Overseers walk back to the table (when Site-01 is loaded). */
    static void returnSeats(ServerLevel ow) {
        Site01.Data d = Site01.data(ow.getServer());
        if (d.returning.isEmpty() || !d.built) return;
        BlockPos o = Site01.origin(ow);
        for (Iterator<Integer> it = d.returning.iterator(); it.hasNext(); ) {
            int n = it.next();
            BlockPos chair = o.offset(Site01.seatChair(n));
            if (!ow.isLoaded(chair)) continue;
            O5MemberEntity m = null;
            for (O5MemberEntity e : ow.getEntitiesOfClass(O5MemberEntity.class, new AABB(o).inflate(120)))
                if (e.getSeat() != null && e.getSeat().seat == n) m = e;
            String line;
            if (m != null) {
                m.reseat();
                line = O5.seat(n).sender() + " walks back to the Council table and takes their chair again.";
                WorldNews.record(ow.getServer(), WorldNews.Kind.SEAT_VOTE, "return" + n, O5.seat(n).sender() + " returned to the Council", 1, "", Set.of(n));
            } else {
                // the one who was voted out is gone: the Foundation appoints a successor to the seat
                m = com.breachprotocol.registry.ModEntities.O5_MEMBER.get().create(ow);
                if (m == null) continue;
                m.setSeat(O5.seat(n));
                m.setHome(chair, false);
                BlockPos hall = Site01.arrival(o);
                m.moveTo(hall.getX() + 0.5, hall.getY(), hall.getZ() + 0.5, 0, 0);
                m.setPersistenceRequired();
                m.getPersistentData().putInt(Site01.GEN_KEY, d.gen);
                ow.addFreshEntity(m);
                line = "A successor arrives for the empty chair of O5-" + n + ". The Council is twelve again.";
                WorldNews.record(ow.getServer(), WorldNews.Kind.SEAT_VOTE, "successor" + n, "a successor took the empty chair of O5-" + n, 1, "", Set.of(n));
            }
            for (ServerPlayer q : ow.players()) if (q.blockPosition().closerThan(o.offset(27, 0, 9), 80))
                q.sendSystemMessage(Component.literal(line).withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
            it.remove();
        }
        d.setDirty();
    }

    /** Whether this player is sitting in a Council session right now. */
    public static boolean inSession(ServerPlayer p) {
        Session s = SESSIONS.get(p.getUUID());
        return s != null && !s.done;
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
                    ServerPlayer q = c.getSource().getPlayerOrException();
                    Session s = SESSIONS.get(q.getUUID());
                    if (s != null) s.decide(IntegerArgumentType.getInteger(c, "option"));
                    else if (PRESIDING != null && PRESIDING.guests.containsKey(q.getUUID())) PRESIDING.guestDecide(q, IntegerArgumentType.getInteger(c, "option"));
                    return 1;
                })))
                .then(Commands.literal("stand").executes(c -> {
                    ServerPlayer q = c.getSource().getPlayerOrException();
                    Session s = SESSIONS.get(q.getUUID());
                    if (s != null) s.decide(-1);
                    else if (PRESIDING != null && PRESIDING.guests.containsKey(q.getUUID())) PRESIDING.guestDecide(q, -1);
                    return 1;
                }))
                .then(Commands.literal("motion").then(Commands.argument("what", StringArgumentType.word()).executes(c -> {
                    ServerPlayer q = c.getSource().getPlayerOrException();
                    String what = StringArgumentType.getString(c, "what");
                    Agenda a = SESSIONS.get(q.getUUID()) instanceof Agenda own ? own : null;
                    if (a == null && PRESIDING != null && PRESIDING.guests.containsKey(q.getUUID())) a = PRESIDING;
                    if (a == null) a = Motions.awaitingAnswerFrom(q);
                    if (a != null) a.motion(q, what);
                    return 1;
                })))
                .then(Commands.literal("best").executes(c -> {
                    Session s = SESSIONS.get(c.getSource().getPlayerOrException().getUUID());
                    if (s != null) s.best();
                    return 1;
                }))
                .then(Commands.literal("say").then(Commands.argument("text", StringArgumentType.greedyString()).executes(c -> {
                    Session s = SESSIONS.get(c.getSource().getPlayerOrException().getUUID());
                    if (s != null) s.say(StringArgumentType.getString(c, "text"));
                    return 1;
                }))));
    }

    // ================================================================== sessions

    abstract static class Session {
        final ServerPlayer p;
        final ServerLevel l;
        final BlockPos o;
        final Map<Integer, O5MemberEntity> members = new TreeMap<>();
        final Deque<long[]> waits = new ArrayDeque<>();
        final Deque<Runnable> steps = new ArrayDeque<>();
        long nextAt;
        boolean done, awaiting, noCooldown;
        /** Overseers seated in their own chairs while the Administrator presides: they vote, the chair rules. */
        final Map<UUID, ServerPlayer> guests = new LinkedHashMap<>();
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
            if (Dialogue.active(p, "council")) Dialogue.close(p);
            for (ServerPlayer g : guests.values()) if (Dialogue.active(g, "council")) Dialogue.close(g);
            guests.clear();
            if (PRESIDING == this) PRESIDING = null;
            if (!noCooldown) COOLDOWN.put(p.getUUID(), l.getGameTime() + 20 * 20);
        }

        List<ServerPlayer> viewers() {
            List<ServerPlayer> v = new ArrayList<>();
            v.add(p);
            v.addAll(guests.values());
            return v;
        }

        static boolean windowed(ServerPlayer q) { return Dialogue.active(q, "council"); }

        void push(ServerPlayer q) { if (windowed(q)) Dialogue.send(q, false); }

        /** The chair who speaks for the Council (O5-7 if seated). */
        int lead() {
            List<Integer> s = seated();
            return s.contains(7) ? 7 : s.isEmpty() ? 7 : s.get(0);
        }

        abstract void begin();
        abstract void onDecide(int option);
        abstract void onTimeout();
        void heard(String text) {}

        void tick(long now) {
            // guests who stand up, or log off, leave the sitting
            for (Iterator<ServerPlayer> it = guests.values().iterator(); it.hasNext(); ) {
                ServerPlayer g = it.next();
                if (g.hasDisconnected() || Council.seatOf(l, g) <= 0) {
                    if (windowed(g)) Dialogue.close(g);
                    it.remove();
                }
            }
            if (mustSit() && Council.seatOf(l, p) < 0) {
                tell(Component.literal("The session is suspended.").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
                done = true;
                return;
            }
            if (awaiting) {
                if (now >= deadline) { awaiting = false; Dialogue.clearOptions(p); onTimeout(); }
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
            Dialogue.clearOptions(p);
            onDecide(option);
        }

        /** "Decide for yourselves, as long as it's best for the Foundation." */
        void best() {
            if (!awaiting) return;
            awaiting = false;
            Dialogue.clearOptions(p);
            onBest();
        }

        void onBest() { onTimeout(); }

        /** The player speaks to the Council (typed in the window). */
        void say(String text) {
            if (!awaiting || text.isBlank()) return;
            awaiting = false;
            Dialogue.clearOptions(p);
            heard(text);
        }

        void push() { push(p); }

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

        /** Others nearby read it in chat; the player at the table sees it in the council window. */
        void tell(Component c) {
            List<ServerPlayer> v = viewers();
            for (ServerPlayer q : audience()) if (!v.contains(q) || !windowed(q)) q.sendSystemMessage(c);
            for (ServerPlayer q : v) if (windowed(q)) { Dialogue.line(q, "", c.getString(), 0); push(q); }
        }

        void speak(int seat, String text) {
            O5 who = O5.seat(seat);
            O5MemberEntity m = members.get(seat);
            Component c = Component.literal(who.sender() + ": ").withStyle(ChatFormatting.DARK_RED).append(Component.literal(text).withStyle(ChatFormatting.GRAY));
            List<ServerPlayer> v = viewers();
            for (ServerPlayer q : audience()) if (!v.contains(q) || !windowed(q)) q.sendSystemMessage(c);
            for (ServerPlayer q : v) if (windowed(q)) {
                if (m != null) Dialogue.entity(q, m.getId());
                Dialogue.line(q, who.sender(), text, 0xE05050);
                push(q);
            }
            if (m != null) {
                m.getLookControl().setLookAt(p, 30, 30);
                m.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            }
            l.playSound(null, m != null ? m.blockPosition() : p.blockPosition(), SoundEvents.VILLAGER_AMBIENT, SoundSource.NEUTRAL, 0.25F, 0.6F);
        }

        void playerSays(String text) { says(p, text); }

        /** A player at the table speaks: the others in the window see their name, they see "You". */
        void says(ServerPlayer who, String text) {
            String name = Career.rank(who) == Rank.ADMINISTRATOR || Career.isAscended(who) ? "The Administrator" : "O5-13 (" + who.getName().getString() + ")";
            Component c = Component.literal(name + ": ").withStyle(ChatFormatting.LIGHT_PURPLE).append(Component.literal(text).withStyle(ChatFormatting.WHITE));
            List<ServerPlayer> v = viewers();
            for (ServerPlayer q : audience()) if (!v.contains(q) || !windowed(q)) q.sendSystemMessage(c);
            for (ServerPlayer q : v) if (windowed(q)) { Dialogue.line(q, q == who ? "You" : name, text, 0xD08CFF); push(q); }
        }

        /** Opens the council window for the player at the table. */
        void window(String subtitle) {
            Dialogue.begin(p, "council", "The O5 Council · Site-01", subtitle, -1);
            Dialogue.hud(p, true);
            Dialogue.send(p, true);
        }

        /** Turns the player in their chair to look down the table. */
        void faceTable() {
            net.minecraft.world.phys.Vec3 c = net.minecraft.world.phys.Vec3.atCenterOf(o.offset(29, 1, 9));
            p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, c);
        }

        /** Ready for a choice: reopens the window if the player closed it. */
        void reopen() {
            if (!Dialogue.active(p, "council")) { Dialogue.begin(p, "council", "The O5 Council · Site-01", "In session", -1); Dialogue.hud(p, true); }
            Dialogue.clearOptions(p);
        }

        void choices(boolean input) {
            if (input) Dialogue.input(p, "/council say {text}");
            Dialogue.send(p, true);
        }

        void title(String t, String sub) {
            p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 50, 20));
            p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(t).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)));
            p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(sub).withStyle(ChatFormatting.GRAY)));
        }
    }

    // ---------------------------------------------------------------- the agenda

    static final class Agenda extends Session {
        final boolean admin;
        final List<Calls.Template> pool;
        final Set<String> used = new HashSet<>();
        Calls.Template item;
        int caller, items;
        int[] votes;
        final Map<Integer, Integer> stance = new HashMap<>();

        /** The Administrator's sitting covers the whole week's agenda in one go (see AgendaBuilder). */
        final List<Calls.Template> agenda;
        final List<String> covered = new ArrayList<>();

        Agenda(ServerPlayer p, boolean admin) {
            super(p);
            this.admin = admin;
            this.pool = Calls.councilPool(admin);
            // the Administrator's sitting covers the week; an Overseer's takes up what's live at the sites, then ordinary business
            this.agenda = new ArrayList<>(admin ? AgendaBuilder.build(p) : AgendaBuilder.forOverseer(p));
        }

        int total() { return agenda != null ? agenda.size() : Math.min(3, pool.size()); }

        /** Votes the guests cast on the current item. */
        final Map<UUID, Integer> guestVotes = new HashMap<>();
        /** After the agenda: motions from the floor. */
        boolean motionStage;
        /** A succession motion waiting for the Administrator's leave (or a vote). */
        ServerPlayer mover;

        @Override
        void close() {
            super.close();
            if (agenda != null && !covered.isEmpty()) AgendaBuilder.done(p, covered);
            Motions.forget(this);
            // the Administrator hears what the Council decided without them
            if (!admin && !covered.isEmpty()) {
                ServerPlayer chief = Motions.admin(p.server);
                if (chief != null && chief != p) {
                    StringBuilder b = new StringBuilder("The Council sat with O5-13 (" + p.getGameProfile().getName() + ") while you were away: ");
                    for (int i = 0; i < covered.size(); i++) {
                        Calls.Template t = Calls.get(covered.get(i));
                        b.append(i == 0 ? "" : "; ").append(t != null ? Calls.title(t) : Calls.title(new Calls.Template(covered.get(i), 0, false, "", List.of())));
                    }
                    Career.message(chief, O5.O5_7, b + ". The chair has priority: raise anything you want overturned at your next sitting.");
                }
            }
        }

        /** The Administrator sits at the head while this Overseer's session runs: the Council rises for the chair. */
        void defer() {
            awaiting = false;
            Dialogue.clearOptions(p);
            speak(lead(), "The Administrator has entered the chamber. This sitting defers to the chair. Thirteen, keep your seat: you'll vote in the Administrator's session.");
            noCooldown = true;
            done = true;
        }

        /** An Overseer sits down in their own chair while the Administrator presides. */
        void join(ServerPlayer g) {
            if (guests.containsKey(g.getUUID()) || g == p) return;
            guests.put(g.getUUID(), g);
            Dialogue.begin(g, "council", "The O5 Council · Site-01", "In session · the Administrator presides · you vote", -1);
            Dialogue.hud(g, true);
            Dialogue.send(g, true);
            CareerData gd = Career.data(g);
            if (!gd.inducted) {
                speak(lead(), "Let the record show that " + g.getGameProfile().getName() + " takes a chair at this table for the first time, in the presence of the Administrator.");
                List<Integer> s = seated();
                if (s.contains(1)) speak(1, "Welcome, Thirteen. Sit straight. The Administrator is watching how you vote, and so am I.");
                gd.inducted = true;
                gd.dirty = true;
            } else {
                speak(lead(), "The Council recognises O5-13, " + g.getGameProfile().getName() + ". The Administrator presides; you have a vote.");
            }
            if (awaiting && !motionStage && item != null) promptGuest(g);
        }

        void promptGuest(ServerPlayer g) {
            Dialogue.clearOptions(g);
            if (item == null) return;
            for (int i = 0; i < item.options().size(); i++)
                Dialogue.option(g, "Vote: " + item.options().get(i).label(), "/council decide " + i, "Your vote counts; the Administrator rules", 0x7FE0FF);
            Dialogue.option(g, "Abstain", "/council stand", "Leave it to the chair", 0xC0C0C0);
            Dialogue.send(g, true);
        }

        void guestDecide(ServerPlayer g, int option) {
            if (!awaiting || guestVotes.containsKey(g.getUUID())) return;
            Dialogue.clearOptions(g);
            if (motionStage) { if (option < 0) { says(g, "No motion from me."); push(g); } return; }
            guestVotes.put(g.getUUID(), option);
            if (option < 0 || item == null || option >= votes.length) { says(g, "I abstain."); return; }
            votes[option]++;
            says(g, "I vote \"" + item.options().get(option).label() + "\".");
            tell(tally());
        }

        @Override
        void begin() {
            gather();
            if (admin) PRESIDING = this;
            title("THE COUNCIL IS IN SESSION", admin ? "The Administrator presides" : "O5-13 takes their seat");
            window(admin ? "In session · the Administrator presides" : "In session · O5-13 is seated");
            faceTable();
            p.playNotifySound(SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 0.8F, 0.6F);
            List<Integer> s = seated();
            int chair = s.contains(7) ? 7 : s.isEmpty() ? 7 : s.get(0);
            CareerData pd = Career.data(p);
            if (!admin && !pd.inducted) {
                // the first sitting in a new chair: the Council acknowledges its newest member
                int chairNo = -1;
                for (Map.Entry<Integer, String> e : Site01.data(p.server).holders.entrySet()) if (e.getValue().equals(p.getUUID().toString())) chairNo = e.getKey();
                final int cn = chairNo;
                then(50, () -> speak(chair, "Before business. Let the record show that " + p.getGameProfile().getName() + " takes the chair " + (cn > 0 ? "of O5-" + cn : "at this table") + " today, and sits as O5-13."));
                if (s.contains(1)) then(60, () -> speak(1, "Welcome, Thirteen. Every one of us sat down in that chair for the first time once. Most of us have regretted it since. Vote with your conscience; we'll argue with it."));
                if (s.contains(3)) then(50, () -> speak(3, "> NEW MEMBER LOGGED. VOTING RIGHTS: FULL. SEAT BINDING: UNTIL REMOVAL OR DEATH."));
                then(40, () -> tell(Component.literal("The Council acknowledges O5-13.").withStyle(ChatFormatting.GOLD)));
                pd.inducted = true;
                pd.dirty = true;
            }
            then(50, () -> speak(chair, admin ? "The Administrator is seated. This session of the O5 Council is called to order. We have " + total() + " item" + (total() == 1 ? "" : "s") + " before us."
                    : "Thirteen is seated. We have a quorum. " + (total() == 0 ? "Nothing pressing on the books." : total() + " item" + (total() == 1 ? "" : "s") + " before us. Let's begin.")));
            if (agenda != null && !agenda.isEmpty()) {
                StringBuilder b = new StringBuilder("On the agenda: ");
                for (int i = 0; i < agenda.size(); i++) b.append(i == 0 ? "" : "; ").append(i + 1).append(". ").append(Calls.title(agenda.get(i)));
                then(40, () -> tell(Component.literal(b + ".").withStyle(ChatFormatting.GRAY)));
            }
            next();
        }

        void next() {
            if (agenda != null ? items >= agenda.size() : items >= 3 || pool.isEmpty()) {
                then(0, this::motions);
                return;
            }
            items++;
            guestVotes.clear();
            if (agenda != null) {
                item = agenda.get(items - 1);
            } else {
                List<Calls.Template> fresh = pool.stream().filter(t -> !used.contains(t.id())).toList();
                item = (fresh.isEmpty() ? pool : fresh).get(rnd.nextInt((fresh.isEmpty() ? pool : fresh).size()));
            }
            used.add(item.id());
            List<Integer> s = new ArrayList<>(seated());
            if (s.isEmpty()) { done = true; return; }
            caller = s.contains(item.caller()) ? item.caller() : s.get(rnd.nextInt(s.size()));
            votes = new int[item.options().size()];
            stance.clear();
            final int itemNo = items;
            then(70, () -> {
                Dialogue.subtitle(p, "Agenda item " + itemNo + " of " + total() + " · " + Calls.title(item) + (admin ? "" : " · O5-13 is seated"));
                tell(Component.literal("— Item " + itemNo + " —").withStyle(ChatFormatting.DARK_GRAY));
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
            reopen();
            for (int i = 0; i < item.options().size(); i++) {
                String label = item.options().get(i).label();
                Dialogue.option(p, (admin ? "Rule: " : "Vote: ") + label, "/council decide " + i,
                        admin ? "Overrule the Council if need be: your ruling stands" : "Cast your vote", 0x7FE0FF);
            }
            Dialogue.option(p, admin ? "Let the vote stand" : "Abstain", "/council stand",
                    admin ? "The Council's majority decides" : "Leave it to the Council", 0xC0C0C0);
            if (admin) Dialogue.option(p, "Decide for yourselves: whatever is best", "/council best",
                    "The Council chooses the option that leaves the Foundation best off", 0xFFC94D);
            choices(true);
            for (ServerPlayer g : guests.values()) promptGuest(g);
            await(admin ? 60 : 40);
        }

        @Override
        void onBest() {
            playerSays("Decide among yourselves. Whatever leaves the Foundation better off.");
            int best = Calls.best(item.id(), p);
            if (best < 0 || best >= votes.length) best = 0;
            final int b = best;
            List<Integer> s = seated();
            int lead = s.contains(7) ? 7 : s.isEmpty() ? caller : s.get(0);
            then(50, () -> speak(lead, "Understood, Administrator. The Council has weighed it. " + Calls.bestReason(item.id())));
            then(30, () -> {
                // the Council falls in behind the best course
                for (int n : s) stance.put(n, b);
                Arrays.fill(votes, 0);
                votes[b] = s.size();
                tell(tally());
            });
            resolve(best, false, true);
        }

        @Override
        void onTimeout() {
            if (motionStage) { adjourn(); return; }
            tell(Component.literal(admin ? "The Administrator lets the vote stand." : "Thirteen abstains.").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
            resolve(majority(), false);
        }

        @Override
        void onDecide(int option) {
            if (motionStage) { adjourn(); return; }
            if (option < 0 || option >= votes.length) { onTimeout(); return; }
            if (admin) {
                playerSays(item.options().get(option).label() + ". That is my ruling.");
                resolve(option, option != majority(), false);
            } else {
                playerSays("I vote \"" + item.options().get(option).label() + "\".");
                votes[option]++;
                int best = 0;
                for (int i = 1; i < votes.length; i++) if (votes[i] > votes[best] || votes[i] == votes[best] && i == option) best = i;
                resolve(best, false, false);
            }
        }

        void resolve(int option, boolean overruled) { resolve(option, overruled, false); }

        void resolve(int option, boolean overruled, boolean delegated) {
            Calls.Option chosen = item.options().get(option);
            covered.add(item.id());
            for (ServerPlayer g : guests.values()) { Dialogue.clearOptions(g); push(g); }
            tell(Component.literal("Decided: " + chosen.label()).withStyle(ChatFormatting.GOLD));
            if (overruled) {
                List<Integer> s = seated();
                int who = s.isEmpty() ? caller : s.get(rnd.nextInt(s.size()));
                then(40, () -> speak(who, rnd.nextBoolean() ? "As you command, Administrator." : "Noted for the record. We'll make it work."));
            }
            then(50, () -> {
                String reply;
                int before = Career.data(p).stability;
                try {
                    reply = chosen.effect().apply(p, Career.data(p));
                } catch (Throwable t) {
                    reply = "It's done.";
                }
                speak(caller, reply);
                Set<Integer> sup = new HashSet<>();
                for (Map.Entry<Integer, Integer> en : stance.entrySet()) if (en.getValue() == option) sup.add(en.getKey());
                if (sup.isEmpty() && !overruled) sup.add(caller);
                WorldNews.record(p.server, WorldNews.Kind.DECISION, item.id() + "|" + chosen.label(), WorldNews.headline(item.id(), chosen.label()),
                        Career.data(p).stability - before, overruled ? "the Administrator, over the Council" : delegated ? "the Council, at the Administrator's direction" : admin ? "the Council" : "the Council, with O5-13", sup);
                Career.sync(p);
            });
            then(80, () -> {});
            next();
        }

        // ------------------------------------------------------------ motions from the floor

        /** The agenda is done: before adjourning, the floor may raise motions. */
        void motions() {
            boolean ownMotion = !admin && Motions.canMove(p);
            List<ServerPlayer> movers = new ArrayList<>();
            if (admin) for (ServerPlayer g : guests.values()) if (Motions.canMove(g)) movers.add(g);
            if (!ownMotion && movers.isEmpty()) { adjourn(); return; }
            motionStage = true;
            speak(lead(), "That concludes the agenda. Are there any motions before we adjourn?");
            reopen();
            if (ownMotion) {
                Dialogue.option(p, "Move that I succeed the Administrator", "/council motion succession", Motions.hint(p), 0xD08CFF);
                Dialogue.option(p, "No motions. Adjourn.", "/council stand", "", 0xC0C0C0);
            } else {
                Dialogue.option(p, "Adjourn the session", "/council stand", "Overseers may still raise motions", 0xC0C0C0);
            }
            choices(false);
            for (ServerPlayer g : movers) {
                Dialogue.clearOptions(g);
                Dialogue.option(g, "Move that I succeed the Administrator", "/council motion succession", Motions.hint(g) + " The Administrator presides and may deny it.", 0xD08CFF);
                Dialogue.option(g, "No motion", "/council stand", "", 0xC0C0C0);
                Dialogue.send(g, true);
            }
            await(45);
        }

        void adjourn() {
            awaiting = false;
            motionStage = false;
            for (ServerPlayer g : guests.values()) { Dialogue.clearOptions(g); push(g); }
            then(0, () -> {
                speak(lead(), "This session is adjourned.");
                done = true;
            });
        }

        /** /council motion succession | allow | deny */
        void motion(ServerPlayer who, String what) {
            switch (what) {
                case "succession" -> {
                    if (!motionStage || mover != null || !Motions.canMove(who)) return;
                    awaiting = false;
                    for (ServerPlayer v : viewers()) Dialogue.clearOptions(v);
                    mover = who;
                    Motions.raise(this, who);
                }
                case "allow", "deny" -> Motions.answer(this, who, what.equals("allow"));
                default -> {}
            }
        }

        // the Motions helper drives these
        void sayChair(String text) { speak(lead(), text); }
        void sayAs(int seat, String text) { speak(seat, text); }
        void announce(Component c) { tell(c); }
        void queue(long after, Runnable r) { then(after, r); }
        void hold(int seconds) { await(seconds); }
        void finish() { adjourn(); }
        List<Integer> chairs() { return seated(); }
        Random dice() { return rnd; }
        ServerPlayer chairPlayer() { return p; }
        boolean presided() { return admin; }

        @Override
        void heard(String text) {
            if (motionStage) { says(p, text); return; }
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
            window("A seat must be made");
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
                reopen();
                Dialogue.subtitle(p, "Seat vote · your vote counts twice");
                for (int n : top()) Dialogue.option(p, "Unseat O5-" + n + " \"" + O5.seat(n).nickname + "\" (" + tally.getOrDefault(n, 0) + ")",
                        "/council decide " + n, "Your vote counts twice", 0x7FE0FF);
                Dialogue.option(p, "Leave it to the Council", "/council stand", "Don't vote", 0xC0C0C0);
                choices(false);
                await(45);
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
        void heard(String text) { playerSays(text); conclude(); }

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
                WorldNews.record(p.server, WorldNews.Kind.SEAT_VOTE, "seat" + gone, "O5-" + gone + " was voted off the Council to make room for " + p.getGameProfile().getName(), 0, p.getGameProfile().getName(), Set.of(gone));
                speak(gone, rnd.nextBoolean() ? "So be it. I'll be around. The Foundation doesn't let anyone truly leave." : "The vote is the vote. Take good care of my chair, Thirteen.");
            });
            then(40, () -> {
                O5MemberEntity m = members.get(gone);
                Site01.Data d = Site01.data(p.server);
                d.holders.put(gone, p.getUUID().toString());
                d.setDirty();
                if (m != null) m.unseat();
                int lead = seated().contains(7) ? 7 : seated().isEmpty() ? gone : seated().get(0);
                speak(lead, "Thirteen, the chair of O5-" + gone + " is yours. Sit, and the Council sits with you: we begin the moment you do.");
                CareerData cd = Career.data(p);
                cd.inducted = false;
                cd.dirty = true;
                noCooldown = true;
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
