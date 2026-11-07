package com.breachprotocol.career;

import com.breachprotocol.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;

import java.util.Random;

/**
 * Meetings with the Council, on the phone's calendar. Overseers ask for a meeting every few days
 * (you accept, take it right now, push it back or decline); you can book any of them yourself, or
 * just call them. At the meeting time they ring you. Only things that can't wait (breaches, rifts,
 * attacks) still arrive as messages.
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = com.breachprotocol.BreachProtocol.MODID)
public final class Calendar {
    private Calendar() {}

    /** Ticks in a Minecraft hour. Day-time 0 is 06:00. */
    public static final int HOUR = 1000, DAY = 24000;
    public static final int MAX = 8;

    static long now(ServerPlayer p) { return p.server.overworld().getDayTime(); }

    public static final String[] WEEKDAYS = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};

    /** "Tuesday (day 9), 10:00". Day 1 of the world is a Monday. */
    public static String when(long at) {
        long day = at / DAY;
        int tod = (int) (at % DAY);
        int h = (tod / HOUR + 6) % 24, m = (tod % HOUR) * 60 / HOUR;
        return WEEKDAYS[(int) (day % 7)] + " (day " + (day + 1) + "), " + String.format("%02d:%02d", h, m);
    }

    public static String who(CareerData.Appt a) { return a.seat == 0 ? "the O5 Council" : O5.seat(a.seat).sender(); }

    public static String about(CareerData.Appt a) {
        if (a.seat == 0) return "weekly session";
        Calls.Template t = Calls.get(a.topic);
        return t == null ? "Council business" : Calls.title(t);
    }

    /** This week's (or next) Council session on the calendar. */
    public static CareerData.Appt weekly(CareerData d) {
        return d.appts.stream().filter(a -> a.seat == 0).findFirst().orElse(null);
    }

    /** The next weekly session time after {@code from} (skipping the week the player skipped). */
    static long nextWeekly(CareerData d, long from) {
        long day = from / DAY;
        for (int i = 0; i < 21; i++) {
            long dd = day + i;
            if (dd % 7 != d.weeklyDow || dd / 7 == d.skipWeek) continue;
            long t = dd * DAY + (long) ((d.weeklyHour - 6 + 24) % 24) * HOUR;
            if (t > from) return t;
        }
        return from + 7L * DAY;
    }

    private static String secretaryName(ServerPlayer p) {
        Forces.Data fd = Forces.data(p.server);
        for (String n : fd.secretaryNames.values()) return n;
        return null;
    }

    /** The weekly Council session: kept on the calendar, and a text from the secretary if you're late. */
    static void weeklyTick(ServerPlayer p, CareerData d, long n) {
        if (!admin(d)) return;
        CareerData.Appt w = weekly(d);
        if (w == null) {
            w = new CareerData.Appt();
            w.id = d.nextApptId++;
            w.seat = 0;
            w.topic = "council";
            w.at = nextWeekly(d, n);
            w.confirmed = false; // on the calendar either way; the secretary checks it with you
            d.appts.add(w);
            d.dirty = true;
            return;
        }
        if (Council.inSession(p) && n >= w.at - 2 * HOUR) { // you're at the table: this week's session is happening
            d.appts.remove(w);
            d.dirty = true;
            return;
        }
        if (n < w.at) return;
        String sec = secretaryName(p);
        if (!w.texted && n >= w.at + HOUR / 4) {
            w.texted = true;
            d.dirty = true;
            String body = "Administrator, the Council has been seated since " + when(w.at).substring(when(w.at).lastIndexOf(' ') + 1)
                    + " and they're waiting for you. Shall I tell them you're on your way? Your calendar has the options: on my way, tomorrow, or skip this week.";
            if (sec != null) Career.message(p, sec + " (Secretary)", body);
            else Career.message(p, O5.O5_7, "We are seated and waiting for you, Administrator.");
        }
        if (n >= w.at + 3L * HOUR) {
            d.appts.remove(w);
            d.dirty = true;
            WorldNews.record(p.server, WorldNews.Kind.DECISION, "council_absent", "the Council met without the Administrator this week", -1, "the Council");
            Career.changeStability(p, -2);
            if (sec != null) Career.message(p, sec + " (Secretary)", "The Council went ahead without you. I've put next week's session on your calendar.");
        }
    }

    /** Keep, move or skip this week's session (from the secretary, the calendar, or a reply). */
    public static void week(ServerPlayer p, String what) {
        CareerData d = Career.data(p);
        CareerData.Appt w = weekly(d);
        if (w == null) return;
        switch (what) {
            case "keep" -> w.confirmed = true;
            case "otw" -> { w.at = Math.max(w.at, now(p)) + HOUR; w.texted = true; w.confirmed = true; }
            case "next" -> { w.at += DAY; w.texted = false; w.confirmed = true; }
            case "skip" -> { d.skipWeek = (w.at / DAY) / 7; d.appts.remove(w); }
            default -> { return; }
        }
        d.dirty = true;
        p.displayClientMessage(Component.literal(switch (what) {
            case "keep" -> "▣ This week's Council session is confirmed.";
            case "otw" -> "▣ They'll hold the session for you. Get to Site-01.";
            case "next" -> "▣ Council session moved to " + when(w.at) + ".";
            default -> "▣ No Council session this week.";
        }).withStyle(ChatFormatting.AQUA), true);
        Career.sync(p);
    }

    /** The next o'clock at least {@code hours} from now. */
    static long inHours(ServerPlayer p, int hours) {
        long t = now(p) + (long) hours * HOUR;
        return (t / HOUR + 1) * HOUR;
    }

    /** The next time the clock reads {@code hour}:00 (at least an hour away), {@code days} days on. */
    static long at(ServerPlayer p, int hour, int days) {
        long n = now(p);
        long dayStart = n / DAY * DAY;
        long t = dayStart + (long) ((hour - 6 + 24) % 24) * HOUR + (long) days * DAY;
        while (t < n + HOUR) t += DAY;
        return t;
    }

    private static boolean council(CareerData d) { return d.ascended || d.getRank().atLeast(Rank.OVERSEER); }

    private static boolean admin(CareerData d) { return d.ascended || d.getRank() == Rank.ADMINISTRATOR; }

    // ================================================================== ticking (from Career)

    static void tick(ServerPlayer p, CareerData d, long gameNow) {
        if (!council(d)) return;
        long n = now(p);
        weeklyTick(p, d, n);
        Random r = new Random();
        // nobody rings you in the middle of a Council meeting: it all waits until you're done
        boolean meeting = Council.inSession(p);
        int faceToFace = com.breachprotocol.entity.Dialogue.talkingSeat(p);
        if (meeting) {
            if (d.nextRequest > 0 && n >= d.nextRequest) d.nextRequest = n + HOUR;
            return;
        }
        // every couple of days an Overseer puts something on the next Council session's agenda
        if (d.nextRequest == 0) d.nextRequest = n + DAY / 2;
        if (n >= d.nextRequest) {
            d.nextRequest = n + 2L * DAY + r.nextInt(DAY);
            int seat = 1 + r.nextInt(12);
            if (seat == faceToFace) seat = seat % 12 + 1;
            String topic = Calls.topicFor(seat, admin(d), r);
            long day = n / DAY;
            Long last = d.agendaUsed.get(topic);
            if (!topic.isEmpty() && !d.agenda.contains(topic) && d.agenda.size() < 4 && (last == null || day - last >= 21)) {
                d.agenda.add(topic);
                d.dirty = true;
                CareerData.Appt w = weekly(d);
                p.displayClientMessage(Component.literal("▣ " + O5.seat(seat).sender() + " added \"" + Calls.title(Calls.get(topic)) + "\" to the Council agenda" + (w != null ? " for " + when(w.at) : "") + ".").withStyle(ChatFormatting.AQUA), true);
                p.playNotifySound(ModSounds.PHONE_MESSAGE.get(), SoundSource.PLAYERS, 0.5F, 1.3F);
            }
        }
        // meetings that are due ring you; requests nobody answered lapse
        for (var it = d.appts.iterator(); it.hasNext(); ) {
            CareerData.Appt a = it.next();
            if (a.seat == 0 || a.at > n) continue;
            if (!a.confirmed) { it.remove(); d.dirty = true; continue; }
            if (d.call != null) continue; // after the current call
            if (a.seat == faceToFace) continue; // you're talking to them in person; they'll ring after
            it.remove();
            d.dirty = true;
            Calls.start(p, a.topic, a.seat);
            p.displayClientMessage(Component.literal("☎ Your " + when(a.at).substring(when(a.at).indexOf(',') + 2) + " meeting: " + O5.seat(a.seat).sender() + " is calling.").withStyle(ChatFormatting.GREEN), true);
        }
        // a call you placed: they pick up
        if (d.call != null && d.call.outgoing && gameNow >= d.call.pickupAt) {
            d.call.outgoing = false;
            d.call.expires = gameNow + 20 * 180;
            d.dirty = true;
            p.playNotifySound(ModSounds.PHONE_MESSAGE.get(), SoundSource.PLAYERS, 0.5F, 0.8F);
        }
    }

    // ================================================================== phone actions

    public static void action(ServerPlayer p, String action, int arg) {
        CareerData d = Career.data(p);
        if (!council(d)) return;
        Random r = new Random();
        switch (action) {
            case "dial" -> {
                int seat = Math.max(1, Math.min(12, arg));
                if (d.call != null) return;
                String topic = Calls.topicFor(seat, admin(d), r);
                if (topic.isEmpty()) return;
                d.call = new CareerData.Call();
                d.call.templateId = topic;
                d.call.caller = seat;
                d.call.outgoing = true;
                d.call.pickupAt = p.level().getGameTime() + 40 + r.nextInt(30);
                d.call.expires = d.call.pickupAt + 20 * 180;
            }
            case "hangup" -> {
                if (d.call == null) return;
                d.lastCallFrom = O5.seat(d.call.caller).sender();
                d.lastCallText = d.call.outgoing ? "Call cancelled." : "You ended the call. We'll take it up another time.";
                d.lastCallAt = p.level().getGameTime();
                d.call = null;
            }
            case "week_keep", "week_otw", "week_next", "week_skip" -> { week(p, action.substring(5)); return; }
            case "appt_accept" -> find(d, arg).ifPresent(a -> a.confirmed = true);
            case "appt_now" -> find(d, arg).ifPresent(a -> {
                if (a.seat == 0) { Directives.run(p, "site01"); return; }
                if (d.call != null) return;
                d.appts.remove(a);
                Calls.start(p, a.topic, a.seat);
            });
            case "appt_later" -> find(d, arg).ifPresent(a -> { a.at = Math.max(a.at, now(p)) + (a.seat == 0 ? DAY : 6L * HOUR); a.confirmed = true; a.texted = false; });
            case "appt_decline", "appt_cancel" -> find(d, arg).ifPresent(a -> {
                if (a.seat == 0) d.skipWeek = (a.at / DAY) / 7;
                d.appts.remove(a);
            });
            case "sched" -> {
                // arg = seat * 10 + slot
                int seat = Math.max(1, Math.min(12, arg / 10)), slot = arg % 10;
                if (d.appts.size() >= MAX) {
                    p.displayClientMessage(Component.literal("Your calendar is full.").withStyle(ChatFormatting.RED), true);
                    return;
                }
                CareerData.Appt a = new CareerData.Appt();
                a.id = d.nextApptId++;
                a.seat = seat;
                a.topic = Calls.topicFor(seat, admin(d), r);
                a.at = switch (slot) {
                    case 0 -> inHours(p, 1);
                    case 1 -> inHours(p, 3);
                    case 2 -> at(p, 18, 0);
                    case 3 -> at(p, 8, 1);
                    default -> at(p, 14, 1);
                };
                a.confirmed = true;
                if (a.topic.isEmpty()) return;
                d.appts.add(a);
                p.displayClientMessage(Component.literal("▣ Booked: " + O5.seat(seat).sender() + " · " + when(a.at)).withStyle(ChatFormatting.AQUA), true);
            }
            default -> { return; }
        }
        d.dirty = true;
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void commands(net.neoforged.neoforge.event.RegisterCommandsEvent event) {
        event.getDispatcher().register(net.minecraft.commands.Commands.literal("council_week")
                .then(net.minecraft.commands.Commands.argument("what", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    String w = com.mojang.brigadier.arguments.StringArgumentType.getString(c, "what");
                    week(p, w);
                    // the secretary acknowledges it in the briefing
                    net.minecraft.world.entity.Entity e = null;
                    for (java.util.UUID id : Forces.data(p.server).secretaries.values()) { e = p.serverLevel().getEntity(id); if (e != null) break; }
                    if (e instanceof com.breachprotocol.entity.FoundationStaff f && f.distanceTo(p) < 32) {
                        com.breachprotocol.entity.Dialogue.line(p, "You", switch (w) { case "keep" -> "Confirm it."; case "next" -> "Move it to the next day."; default -> "Skip it this week."; }, 0xB8E07A);
                        com.breachprotocol.entity.Dialogue.line(p, f.getName().getString(), switch (w) { case "keep" -> "Confirmed. I'll tell O5-7."; case "next" -> "Moved. I'll let the Council know."; default -> "I'll send your apologies."; }, 0xF0A8C8);
                        CareerData d = Career.data(p);
                        CareerData.Appt wk = weekly(d);
                        if (wk != null) wk.confirmed = true;
                        Secretary.brief(p, f, "none");
                    }
                    return 1;
                })));
    }

    private static java.util.Optional<CareerData.Appt> find(CareerData d, int id) {
        return d.appts.stream().filter(a -> a.id == id).findFirst();
    }
}
