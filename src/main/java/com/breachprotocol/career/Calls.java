package com.breachprotocol.career;

import com.breachprotocol.compat.Unleashed;
import com.breachprotocol.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.util.*;

/**
 * Phone calls. Before O5 they are rare (one: the Council's invitation). As O5-13 you are the
 * tiebreaker in deadlocked votes. As The Administrator, the Council calls you for orders.
 * Every choice moves Site Stability, and some have teeth.
 */
public final class Calls {
    private Calls() {}

    public interface Effect {
        /** @return the caller's response, shown as a message */
        String apply(ServerPlayer p, CareerData d);
    }

    public record Option(String label, Effect effect) {}

    /**
     * The option that leaves the Foundation best off (the most stability for the least risk), for
     * when the Administrator tells the Council to decide for themselves.
     */
    public static int best(String id, net.minecraft.server.level.ServerPlayer p) {
        return switch (id) {
            case "termination_096", "declassify_207" -> 1;
            case "gate_stirs" -> Career.isAscended(p) ? 1 : 0;
            default -> 0;
        };
    }

    /** A short subject line for a calendar entry. */
    public static String title(Template t) {
        return switch (t.id()) {
            case "o5_offer" -> "A seat on the Council";
            case "termination_096" -> "SCP-096 termination order";
            case "mtf_expand" -> "Permanent Epsilon-11 posting";
            case "budget" -> "Quarterly budget";
            case "insurgency_spy" -> "Insurgency spy in the ranks";
            case "cafeteria_999" -> "SCP-999 in the cafeteria";
            case "cell_173" -> "SCP-173 cell cleaning";
            case "femur_breaker" -> "SCP-106 recontainment";
            case "goc_jointop" -> "Joint operation with the GOC";
            case "declassify_207" -> "Declassifying SCP-207";
            case "network_code" -> "Hostile code in the network";
            case "gate_stirs" -> "The Gate is stirring";
            case "dclass_supply" -> "D-Class supply";
            case "global_lockdown" -> "Global lockdown";
            case "w_attack" -> "Answering this week's attack";
            case "w_losses" -> "This week's losses";
            case "w_breach" -> "The breach";
            case "w_stability" -> "Emergency measures";
            case "w_rift" -> "The lost rift crew";
            default -> t.id().startsWith("w_short_") ? "Under-strength garrison" : t.id().startsWith("c_") ? Containment.title(t.id()) : t.id().startsWith("m_") ? Motions.title(t.id()) : "Council business";
        };
    }

    /** A topic this Overseer would bring to you (their own business first). */
    public static String topicFor(int seat, boolean admin, java.util.Random r) {
        List<Template> pool = councilPool(admin);
        List<Template> own = pool.stream().filter(t -> t.caller() == seat).toList();
        List<Template> from = own.isEmpty() || r.nextInt(3) == 0 ? pool : own;
        return from.isEmpty() ? "" : from.get(r.nextInt(from.size())).id();
    }

    /** Why that option is the best one, as the Council would put it. */
    public static String bestReason(String id) {
        return switch (id) {
            case "termination_096" -> "Every attempt to kill it has made things worse. We keep it contained.";
            case "mtf_expand" -> "More task forces means fewer breaches. Approve.";
            case "budget" -> "Containment first. Research is worthless if the walls fail.";
            case "insurgency_spy" -> "Turning a spy is a gamble. An amnestic sweep is certain.";
            case "cafeteria_999" -> "It lifts morale at no risk. Approve.";
            case "cell_173" -> "A dirty cell is a weak cell. It gets cleaned, under full protocol.";
            case "femur_breaker" -> "It is the only procedure that has ever worked. Authorise it.";
            case "goc_jointop" -> "Sharing intelligence keeps the Coalition on our side of the table.";
            case "declassify_207" -> "Nothing is gained by opening that file. It stays sealed.";
            case "network_code" -> "We cannot study what is already inside our network. Purge it.";
            case "gate_stirs" -> "Seal the levels. We do not provoke the Gate.";
            case "dclass_supply" -> "Death row only. Widening the net costs more than it gives.";
            case "global_lockdown" -> "A global lockdown is costly, but it is the only option that guarantees containment.";
            default -> "It is the course that leaves the Foundation strongest.";
        };
    }

    public record Template(String id, int caller, boolean adminOnly, String text, List<Option> options) {}

    private static final Map<String, Template> TEMPLATES = new LinkedHashMap<>();
    private static final List<Template> RANDOM_POOL = new ArrayList<>();

    private static void add(Template t, boolean random) {
        TEMPLATES.put(t.id(), t);
        if (random) RANDOM_POOL.add(t);
    }

    private static Option opt(String label, Effect e) {
        return new Option(label, e);
    }

    private static String stab(ServerPlayer p, int n, String text) {
        Career.changeStability(p, n);
        return text + "  [Stability " + (n >= 0 ? "+" : "") + n + "]";
    }

    static {
        add(new Template("o5_offer", 7, false,
                "The seat of O5-13 is empty again. The Council has voted, six to six, and the seat breaks its own tie: it chose you. Will you take it?",
                List.of(
                        opt("Take the seat", (p, d) -> {
                            d.o5OfferPending = false;
                            Career.promote(p, Rank.OVERSEER);
                            return "Then sit down, Thirteen. We have work.";
                        }),
                        opt("Not yet", (p, d) -> {
                            d.o5OfferPending = false;
                            d.xp = Math.min(d.xp, Rank.OVERSEER.xpRequired - 3);
                            return "Few people refuse. Earn it again, and we'll ask again.";
                        }))), false);

        add(new Template("termination_096", 11, false,
                "A termination order for SCP-096 is on my desk. The last attempt cost us forty personnel. I need a signature.",
                List.of(
                        opt("Sign it", (p, d) -> stab(p, -6, "Signed. Nu-7 went in. 096 came out. The order has been... filed.")),
                        opt("Refuse", (p, d) -> stab(p, 3, "Good. Some things are meant to be contained, not killed.")))), true);

        add(new Template("mtf_expand", 8, false,
                "I want to station Epsilon-11 'Nine-Tailed Fox' at your location permanently. It's expensive.",
                List.of(
                        opt("Approve", (p, d) -> {
                            com.breachprotocol.compat.UnleashedApi.deployMtfNow(p, "Volatile");
                            return stab(p, 3, "An Epsilon-11 Squad is inbound to you. Don't get in their way.");
                        }),
                        opt("Deny", (p, d) -> stab(p, -2, "Your funeral. Possibly literally.")))), true);

        add(new Template("budget", 12, false,
                "We're short. I can pull funding from research and put it into containment, or the other way round.",
                List.of(
                        opt("Fund containment", (p, d) -> stab(p, 6, "Walls are cheaper than funerals.")),
                        opt("Fund research", (p, d) -> {
                            Career.budget(p, Unleashed.item("amnestic_canister", Items.GOLDEN_APPLE), 3);
                            Career.addXp(p, 40, "research grant");
                            return stab(p, -3, "Research sent you a gift basket. Containment sent a complaint.");
                        }))), true);

        add(new Template("insurgency_spy", 9, false,
                "There's a Chaos Insurgency agent at your site. I know who it is. Do we sweep everyone with amnestics, or turn the spy?",
                List.of(
                        opt("Amnestic sweep", (p, d) -> {
                            p.removeAllEffects();
                            return stab(p, 3, "Done. Nobody remembers anything, including the spy.");
                        }),
                        opt("Turn the spy", (p, d) -> {
                            Career.addXp(p, 60, "intelligence op");
                            return p.getRandom().nextBoolean() ? stab(p, 8, "The spy is ours now. The Insurgency is eating its own.")
                                    : stab(p, -8, "The spy ran with half our site schematics. I did warn you.");
                        }))), true);

        add(new Template("cafeteria_999", 2, false,
                "Proposal: let SCP-999 roam the staff cafeteria. Morale has never been lower.",
                List.of(
                        opt("Approve", (p, d) -> {
                            // ours, out of its cell for a visit; or one that's loose close by; or one somebody has to go and find
                            Career.Source src = Career.source(p, Unleashed.SCP_999, 40, 80);
                            if (src == null) return stab(p, 5, "Approved. We'll find it a place.");
                            return switch (src.kind()) {
                                case 0 -> src.mob() != null && com.breachprotocol.career.Containment.escort(p, src.mob(), 20 * 150)
                                        ? stab(p, 5, "Staff are bringing SCP-999 up from " + src.where() + ". It's already giggling. It goes back to its cell in a few minutes.")
                                        : stab(p, 5, "SCP-999 is in " + src.where() + ". Staff will walk it to the cafeteria from there.");
                                case 1 -> {
                                    if (src.mob() != null) src.mob().getNavigation().moveTo(p, 1.0);
                                    yield stab(p, 5, "There's one loose " + src.where() + ", just by you. It's already wobbling your way.");
                                }
                                default -> stab(p, 4, "Approved, but we don't hold a 999. One was seen " + src.where() + ". Someone has to go and bring it in.");
                            };
                        }),
                        opt("Deny", (p, d) -> stab(p, -1, "...I'll tell it you said no. It will be sad.")))), true);

        add(new Template("cell_173", 6, false,
                "SCP-173's cell needs cleaning. Three D-Class, two minutes, everyone watching. Or we postpone again.",
                List.of(
                        opt("Approve cleaning", (p, d) -> {
                            Career.addXp(p, 20, "maintenance");
                            // a real crew goes in, with the two watchers calling their blinks
                            int n = Observation.cleanNow(p.server);
                            return stab(p, 2, n > 0 ? "Cleaning crew is going in now. The watchers will call their blinks. I'll tell you how many come out."
                                    : "Cell clean. All three D-Class kept their eyes open. Mostly.");
                        }),
                        opt("Postpone", (p, d) -> {
                            if (p.getRandom().nextFloat() < 0.35F && p.level().dimension() == Level.OVERWORLD && !d.ascended) {
                                Career.breachNear(p, Unleashed.SCP_173);
                                return stab(p, -5, "The waste built up. Something cracked. It's out, and it's near you.");
                            }
                            return stab(p, -2, "Postponed. The smell is getting worse.");
                        }))), true);

        add(new Template("femur_breaker", 7, false,
                "SCP-106 is restless. The femur breaker procedure would calm it. It requires a live volunteer.",
                List.of(
                        opt("Authorise it", (p, d) -> stab(p, 8, "It's done. We don't speak about it.")),
                        opt("Find another way", (p, d) -> {
                            if (p.getRandom().nextFloat() < 0.4F && p.level().dimension() == Level.OVERWORLD && !d.ascended) {
                                Career.breachNear(p, Unleashed.SCP_106);
                                return stab(p, -5, "There was no other way. It's coming up through the floor near you.");
                            }
                            return stab(p, -3, "We found a way. For now.");
                        }))), true);

        add(new Template("goc_jointop", 4, false,
                "The Global Occult Coalition wants a joint operation. They're offering one of their own medical anomalies in exchange for intel.",
                List.of(
                        opt("Share intel", (p, d) -> {
                            Career.budget(p, Unleashed.scp500(), 1);
                            return stab(p, 3, "A fair trade. They sent the pill. I'd keep it safe.");
                        }),
                        opt("Refuse", (p, d) -> {
                            Career.addXp(p, 50, "operational secrecy");
                            return stab(p, -3, "They took it badly. Expect friction.");
                        }))), true);

        add(new Template("declassify_207", 10, false,
                "Research wants SCP-207's files declassified. If I open them, samples go out to the teams.",
                List.of(
                        opt("Declassify", (p, d) -> {
                            Career.budget(p, Unleashed.item("scp207_bottle", Items.HONEY_BOTTLE), 2);
                            return stab(p, -2, "Two bottles found their way to your office. Do not drink both.");
                        }),
                        opt("Keep it sealed", (p, d) -> stab(p, 2, "Sealed. History will thank you. Or forget you.")))), true);

        add(new Template("network_code", 3, false,
                "> ANOMALOUS CODE DETECTED IN SITE NETWORK. SELF-REPLICATING. PURGE OR STUDY?",
                List.of(
                        opt("Purge", (p, d) -> stab(p, 3, "> PURGE COMPLETE. 14 TERMINALS LOST.")),
                        opt("Study it", (p, d) -> {
                            Career.addXp(p, 70, "cyber-anomaly research");
                            return p.getRandom().nextFloat() < 0.25F ? stab(p, -7, "> IT HAS LEARNED TO USE THE DOOR CONTROLS.")
                                    : stab(p, 1, "> IT IS... COOPERATIVE. NOTED.");
                        }))), true);

        add(new Template("gate_stirs", 1, true,
                "The Gate Guardian moved, Administrator. Only a little. Do we seal Site-01's lower levels, or do we finally ask it what it wants?",
                List.of(
                        opt("Seal the levels", (p, d) -> stab(p, 5, "Sealed. It went still again.")),
                        opt("Ask it", (p, d) -> {
                            Career.addXp(p, 100, "first contact");
                            return d.ascended ? stab(p, 10, "It said one word: \"MASTER\". It meant you.")
                                    : stab(p, -2, "It said one word: \"PREPARE\". We are not sure for what.");
                        }))), true);

        add(new Template("dclass_supply", 11, true,
                "We're running out of D-Class. Recruit from death row only, as always, or widen the net?",
                List.of(
                        opt("Death row only", (p, d) -> stab(p, 2, "The usual paperwork, then.")),
                        opt("Widen the net", (p, d) -> {
                            Career.budget(p, Items.IRON_INGOT, 16);
                            return stab(p, -6, "Staffing solved. The Ethics Committee would like a word.");
                        }))), true);

        add(new Template("global_lockdown", 7, true,
                "Three sites report anomalous activity at once. A global lockdown freezes everything, including us. Your call.",
                List.of(
                        opt("Global lockdown", (p, d) -> {
                            Directives.containAround(p, 128, 20 * 120);
                            return stab(p, 6, "Every cell is sealed. Nobody moves.");
                        }),
                        opt("Let sites handle it", (p, d) -> {
                            Career.addXp(p, 40, "delegation");
                            return stab(p, -4, "They're handling it. Badly, but they're handling it.");
                        }),
                        opt("Send Alpha-1", (p, d) -> {
                            com.breachprotocol.compat.UnleashedApi.deployMtfNow(p, "Abyssal");
                            return stab(p, 3, "The Red Right Hand is moving. The Alpha-1 Detachment is coming to you.");
                        }))), true);
    }

    /** Agenda items the Council can debate in session (the Administrator gets the admin-only ones too). */
    public static List<Template> councilPool(boolean admin) {
        return RANDOM_POOL.stream().filter(t -> admin || !t.adminOnly()).toList();
    }

    public static Template get(String id) {
        return TEMPLATES.get(id);
    }

    public static String framing(CareerData d) {
        if (d.getRank() == Rank.ADMINISTRATOR) return "Administrator, we need your orders. ";
        if (d.getRank() == Rank.OVERSEER) return "The vote is tied 6-6. You are the tiebreaker. ";
        return "";
    }

    public static void start(ServerPlayer p, String id) {
        Template t = TEMPLATES.get(id);
        if (t != null) start(p, id, t.caller());
    }

    /** {@code seat} calls you about {@code id}. */
    public static void start(ServerPlayer p, String id, int seat) {
        Template t = TEMPLATES.get(id);
        if (t == null) return;
        CareerData d = Career.data(p);
        d.call = new CareerData.Call();
        d.call.templateId = id;
        d.call.caller = seat;
        d.call.expires = p.level().getGameTime() + (id.equals("o5_offer") ? 20 * 60 * 10 : 20 * 150);
        d.dirty = true;
        p.displayClientMessage(Component.literal("☎ INCOMING CALL: ").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                .append(Component.literal(O5.seat(seat).sender()).withStyle(ChatFormatting.WHITE))
                .append(Component.literal("  · open your phone").withStyle(ChatFormatting.GRAY)), true);
        p.playNotifySound(ModSounds.PHONE_RING.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    public static void startRandom(ServerPlayer p) {
        CareerData d = Career.data(p);
        boolean admin = d.getRank() == Rank.ADMINISTRATOR;
        List<Template> pool = RANDOM_POOL.stream().filter(t -> admin || !t.adminOnly()).toList();
        if (pool.isEmpty()) return;
        start(p, pool.get(p.getRandom().nextInt(pool.size())).id());
    }

    public static void answer(ServerPlayer p, int option) {
        CareerData d = Career.data(p);
        if (d.call == null) return;
        Template t = TEMPLATES.get(d.call.templateId);
        CareerData.Call call = d.call;
        d.call = null;
        d.dirty = true;
        if (t == null || option < 0 || option >= t.options().size()) return;
        int before = d.stability;
        String reply = t.options().get(option).effect().apply(p, d);
        if (!t.id().equals("o5_offer"))
            WorldNews.record(p.server, WorldNews.Kind.DECISION, t.id() + "|" + t.options().get(option).label(), WorldNews.headline(t.id(), t.options().get(option).label()),
                    d.stability - before, p.getGameProfile().getName(), java.util.Set.of(t.caller()));
        // the answer is given on the call itself, not sent as a message afterwards
        d.lastCallFrom = O5.seat(call.caller).sender();
        d.lastCallText = reply;
        d.lastCallAt = p.level().getGameTime();
        if (d.getRank().atLeast(Rank.OVERSEER) && !t.id().equals("o5_offer")) Career.addXp(p, 25, "decision made");
        Career.sync(p);
    }

    public static void timeout(ServerPlayer p) {
        CareerData d = Career.data(p);
        if (d.call == null) return;
        int caller = d.call.caller;
        boolean offer = d.call.templateId.equals("o5_offer");
        d.call = null;
        d.dirty = true;
        if (offer) {
            d.o5OfferPending = false;
            Career.message(p, O5.seat(caller), "You didn't answer. We'll call again when the seat is still empty.");
            return;
        }
        d.lastCallFrom = O5.seat(caller).sender();
        d.lastCallText = "Missed call. The Council proceeded without you.";
        d.lastCallAt = p.level().getGameTime();
        p.displayClientMessage(Component.literal("☎ Missed call from " + O5.seat(caller).sender()).withStyle(ChatFormatting.RED), true);
        Career.changeStability(p, -3);
    }

    private static final String[][] FLAVOR = {
            {"9", "I dreamt of a door that opens from the wrong side. Check your locks tonight."},
            {"3", "> REMINDER: BLINK METERS ARE MANDATORY WITHIN 32 METRES OF SCP-173."},
            {"12", "Reminder: generators run on redstone, and redstone runs on budget. Don't waste either."},
            {"5", "Someone is watching your site from the treeline. Probably one of ours. Probably."},
            {"6", "Tip from the field: a carved pumpkin on your head has saved more lives than any rifle."},
            {"2", "SCP-999 can wash 106's corrosion right off you. Remember that when the floor turns black."},
            {"10", "The archives show three timelines where this site fell. In all of them, the lights went out first."},
            {"4", "The Coalition asked about you by name today. I told them nothing. You're welcome."},
            {"1", "Do you know why we keep them, rather than kill them? One day you'll understand. Maybe soon."},
            {"11", "If anyone asks, the incident at your site was a gas leak. There is always a gas leak."},
            {"8", "Epsilon-11 reports your containment walls are holding. 096 tested one. It held."},
            {"7", "Keep your generators powered and your exits clear. The rest is improvisation."},
    };

    public static void flavor(ServerPlayer p) {
        CareerData d = Career.data(p);
        // the Council only messages its own about things that can't wait; the rest goes on the calendar
        if (d.ascended || d.getRank().atLeast(Rank.OVERSEER)) return;
        if (d.ascended) {
            Career.message(p, O5.seat(1 + p.getRandom().nextInt(12)), "Master. Your will is being carried out at every site.");
            return;
        }
        String[] f = FLAVOR[p.getRandom().nextInt(FLAVOR.length)];
        Career.message(p, O5.seat(Integer.parseInt(f[0])), f[1]);
    }

    /** Used by directive code to restore a player's eyes. */
    static void clearBlink(ServerPlayer p) {
        p.removeEffect(MobEffects.DARKNESS);
        p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 20 * 60, 0));
    }
}
