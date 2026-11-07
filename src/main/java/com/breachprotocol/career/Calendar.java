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
public final class Calendar {
    private Calendar() {}

    /** Ticks in a Minecraft hour. Day-time 0 is 06:00. */
    public static final int HOUR = 1000, DAY = 24000;
    public static final int MAX = 8;

    static long now(ServerPlayer p) { return p.server.overworld().getDayTime(); }

    public static String when(long at) {
        long day = at / DAY + 1;
        int tod = (int) (at % DAY);
        int h = (tod / HOUR + 6) % 24, m = (tod % HOUR) * 60 / HOUR;
        return "Day " + day + ", " + String.format("%02d:%02d", h, m);
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
        Random r = new Random();
        // a request for a meeting every couple of days
        if (d.nextRequest == 0) d.nextRequest = n + DAY / 2;
        if (n >= d.nextRequest) {
            d.nextRequest = n + 2L * DAY + r.nextInt(DAY);
            long open = d.appts.stream().filter(a -> !a.confirmed).count();
            if (open < 3 && d.appts.size() < MAX) {
                CareerData.Appt a = new CareerData.Appt();
                a.id = d.nextApptId++;
                a.seat = 1 + r.nextInt(12);
                a.topic = Calls.topicFor(a.seat, admin(d), r);
                a.at = r.nextBoolean() ? inHours(p, 2 + r.nextInt(6)) : at(p, new int[]{9, 11, 14, 16, 19}[r.nextInt(5)], r.nextInt(2));
                a.confirmed = false;
                if (!a.topic.isEmpty()) {
                    d.appts.add(a);
                    d.dirty = true;
                    p.displayClientMessage(Component.literal("▣ " + O5.seat(a.seat).sender() + " requests a meeting: " + Calls.title(Calls.get(a.topic)) + " · " + when(a.at)).withStyle(ChatFormatting.AQUA), true);
                    p.playNotifySound(ModSounds.PHONE_MESSAGE.get(), SoundSource.PLAYERS, 0.6F, 1.2F);
                }
            }
        }
        // meetings that are due ring you; requests nobody answered lapse
        for (var it = d.appts.iterator(); it.hasNext(); ) {
            CareerData.Appt a = it.next();
            if (a.at > n) continue;
            if (!a.confirmed) { it.remove(); d.dirty = true; continue; }
            if (d.call != null) continue; // after the current call
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
            case "appt_accept" -> find(d, arg).ifPresent(a -> a.confirmed = true);
            case "appt_now" -> find(d, arg).ifPresent(a -> {
                if (d.call != null) return;
                d.appts.remove(a);
                Calls.start(p, a.topic, a.seat);
            });
            case "appt_later" -> find(d, arg).ifPresent(a -> { a.at = Math.max(a.at, now(p)) + 6L * HOUR; a.confirmed = true; });
            case "appt_decline", "appt_cancel" -> find(d, arg).ifPresent(d.appts::remove);
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

    private static java.util.Optional<CareerData.Appt> find(CareerData d, int id) {
        return d.appts.stream().filter(a -> a.id == id).findFirst();
    }
}
