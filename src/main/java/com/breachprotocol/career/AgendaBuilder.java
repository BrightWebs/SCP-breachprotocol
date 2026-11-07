package com.breachprotocol.career;

import net.minecraft.server.level.ServerPlayer;

import java.util.*;

/**
 * The weekly Council session's agenda, all of it debated in one sitting. It is built fresh each
 * week from what actually happened (attacks, deaths, breaches, losses, a falling stability), the
 * items the Overseers asked to add during the week, and the one standing item: SCP-173's cell has
 * to be cleaned every seven days. Ordinary business isn't repeated within three weeks.
 */
public final class AgendaBuilder {
    private AgendaBuilder() {}

    private static Calls.Option opt(String label, Calls.Effect e) { return new Calls.Option(label, e); }

    private static String stab(ServerPlayer p, int n, String text) {
        Career.changeStability(p, n);
        return text + "  [Stability " + (n >= 0 ? "+" : "") + n + "]";
    }

    /** The full agenda for a session starting now. */
    public static List<Calls.Template> build(ServerPlayer p) {
        CareerData d = Career.data(p);
        long since = d.lastSession > 0 ? d.lastSession : p.server.overworld().getGameTime() - 7L * 24000;
        List<WorldNews.Item> week = new ArrayList<>();
        for (WorldNews.Item i : WorldNews.recent(p.server, 8)) if (i.time() >= since) week.add(i);
        List<Calls.Template> out = new ArrayList<>();

        // ------------------------------------------------------------ what happened this week
        WorldNews.Item attack = week.stream().filter(i -> i.kind() == WorldNews.Kind.ATTACK && i.mood() < 0).findFirst().orElse(null);
        if (attack != null) {
            String site = attack.headline().replaceAll(".* attacked ", "");
            out.add(new Calls.Template("w_attack", 6, true,
                    "First item. " + Character.toUpperCase(attack.headline().charAt(0)) + attack.headline().substring(1) + " " + WorldNews.ago(p.server, attack)
                            + ". Our people held, but they will come back. How do we answer?",
                    List.of(opt("Double the garrison", (q, x) -> {
                                Forces.order(q.server, site, Forces.Unit.NU_7, 4);
                                Forces.order(q.server, site, Forces.Unit.SECURITY, 4);
                                return stab(q, 4, "Nu-7 and fresh security are on their way to " + site + ". Let them try again.");
                            }),
                            opt("Send Alpha-1 after them", (q, x) -> {
                                Forces.order(q.server, site, Forces.Unit.ALPHA_1, 3);
                                return stab(q, 3, "Three Alpha-1 operatives are on their way to " + site + ". They'll hunt anything that comes back.");
                            }),
                            opt("Open talks with them", (q, x) -> {
                                String org = attackerOrg(attack.headline());
                                if (!org.isEmpty()) Policy.set(q.server, d2 -> d2.truce.put(org, Policy.days(q.server, 3)));
                                return stab(q, -4, "Talks. Three days of ceasefire: our guards hold fire unless fired on. The field teams think we've gone soft.");
                            }))));
        }
        long deaths = week.stream().filter(i -> i.kind() == WorldNews.Kind.DEATH).count();
        if (deaths >= 2) {
            WorldNews.Item one = week.stream().filter(i -> i.kind() == WorldNews.Kind.DEATH).findFirst().get();
            out.add(new Calls.Template("w_losses", 2, true,
                    "We lost " + deaths + " of our people this week. Most recently, " + one.headline() + ". The staff are asking what we'll do about it.",
                    List.of(opt("A memorial, and new hires", (q, x) -> stab(q, 3, "The memorial is tomorrow at noon. The new hires start next week.")),
                            opt("Tighten safety protocols", (q, x) -> {
                                Policy.set(q.server, d2 -> d2.safetyUntil = Policy.days(q.server, 7));
                                return stab(q, 2, "New protocols at every site for a week: fewer people fall ill, fewer accidents. Expect complaints.");
                            }),
                            opt("Keep working. We can't stop", (q, x) -> stab(q, -2, "Understood. They'll work. They won't forget.")))));
        }
        WorldNews.Item breach = week.stream().filter(i -> i.kind() == WorldNews.Kind.BREACH).findFirst().orElse(null);
        if (breach != null) {
            out.add(new Calls.Template("w_breach", 9, true,
                    "The breach " + WorldNews.ago(p.server, breach) + ": " + breach.headline() + ". It should never have happened. What do we change?",
                    List.of(opt("Rebuild the containment chamber", (q, x) -> stab(q, 4, "Engineering has the new specification. It'll hold this time.")),
                            opt("Discipline the shift on duty", (q, x) -> stab(q, 1, "Three reprimands, one demotion. The others are watching.")),
                            opt("Double the guard on that cell", (q, x) -> {
                                Sites.Site s = Sites.get(q.server).sites.isEmpty() ? null : Sites.get(q.server).sites.get(0);
                                if (s != null) Forces.order(q.server, s.name, Forces.Unit.EPSILON_11, 2);
                                return stab(q, 3, "Epsilon-11 will stand watch on that door from now on.");
                            }))));
        }
        if (d.stability < 45) {
            out.add(new Calls.Template("w_stability", 7, true,
                    "Site stability is at " + d.stability + "%. If it falls much further, we will lose a site. I'm asking for emergency measures.",
                    List.of(opt("Lock down every site for a day", (q, x) -> {
                                Policy.set(q.server, d2 -> d2.lockdownUntil = Policy.days(q.server, 1));
                                return stab(q, 8, "Lockdown in effect for a day: every guard on high alert, shooting at twice the range. The world will notice nothing.");
                            }),
                            opt("Amnestic sweep of the region", (q, x) -> stab(q, 5, "The sweep starts tonight. A lot of people are going to have a very ordinary week.")),
                            opt("Ride it out", (q, x) -> stab(q, -3, "Then we pray. I don't, usually.")))));
        }
        for (Sites.Site s : Sites.get(p.server).sites) {
            Forces.Station st = Forces.data(p.server).station(s.name);
            int want = 0, have = st.members.size();
            for (Forces.Unit u : Forces.Unit.values()) want += st.wanted(u);
            if (want - have >= 4) {
                final String name = s.name;
                final int miss = want - have;
                out.add(new Calls.Template("w_short_" + name, 8, true,
                        name + " is " + miss + " armed personnel short of strength. My task forces can cover it, at a price.",
                        List.of(opt("Replace them now", (q, x) -> { Forces.fill(q.server, name); return stab(q, 2, miss + " personnel are on their way to " + name + "."); }),
                                opt("Wait for the next review", (q, x) -> stab(q, -1, "As you wish. " + name + " will make do.")))));
                break;
            }
        }
        if (week.stream().anyMatch(i -> i.kind() == WorldNews.Kind.RIFT_LOST)) {
            out.add(new Calls.Template("w_rift", 9, true,
                    "A rift took one of our D-Class crews this week. The others won't go near a rift now without a task force.",
                    List.of(opt("Only MTF close rifts from now on", (q, x) -> {
                                Policy.set(q.server, d2 -> d2.riftMtfOnly = true);
                                return stab(q, 2, "Rifts are task force work from today. Any crew you send will be an MTF team.");
                            }),
                            opt("Better equipment for the crews", (q, x) -> {
                                Policy.set(q.server, d2 -> d2.crewGear = true);
                                return stab(q, 1, "The crews get new tethers. Far fewer of them will be lost.");
                            }),
                            opt("D-Class are expendable", (q, x) -> stab(q, -2, "That's... the official position. It won't help morale.")))));
        }

        // ------------------------------------------------------------ what the Overseers added this week
        for (String id : new ArrayList<>(d.agenda)) {
            Calls.Template t = Calls.get(id);
            if (t != null && out.stream().noneMatch(x -> x.id().equals(id))) out.add(t);
        }

        // ------------------------------------------------------------ the standing item: SCP-173's cell, every seven days
        Calls.Template peanut = Calls.get("cell_173");
        if (peanut != null && out.stream().noneMatch(x -> x.id().equals("cell_173")))
            out.add(new Calls.Template("cell_173", peanut.caller(), peanut.adminOnly(),
                    "Standing item, as every week: SCP-173's cell needs its seven-day cleaning. Three D-Class, eyes open, nobody blinks. Do we proceed?",
                    peanut.options()));

        // ------------------------------------------------------------ and fresh business, if the week was quiet
        Random r = new Random();
        long day = p.server.overworld().getDayTime() / 24000;
        List<Calls.Template> fresh = new ArrayList<>();
        for (Calls.Template t : Calls.councilPool(true)) {
            if (t.id().equals("cell_173") || out.stream().anyMatch(x -> x.id().equals(t.id()))) continue;
            Long last = d.agendaUsed.get(t.id());
            if (last != null && day - last < 21) continue;
            fresh.add(t);
        }
        Collections.shuffle(fresh, r);
        int room = Math.max(0, 5 - out.size());
        out.addAll(fresh.subList(0, Math.min(room, Math.min(2, fresh.size()))));
        return out;
    }

    private static String attackerOrg(String headline) {
        String h = headline.toLowerCase(java.util.Locale.ROOT);
        for (int i = 0; i < Defence.ORGS.length; i++) if (h.contains(Defence.ORG_NAMES[i].toLowerCase(java.util.Locale.ROOT).replaceFirst("^the ", ""))) return Defence.ORGS[i];
        return "";
    }

    /** After the session: what was covered isn't brought up again for a while. */
    public static void done(ServerPlayer p, List<String> covered) {
        CareerData d = Career.data(p);
        long day = p.server.overworld().getDayTime() / 24000;
        for (String id : covered) d.agendaUsed.put(id, day);
        d.agenda.removeAll(covered);
        d.lastSession = p.server.overworld().getGameTime();
        d.dirty = true;
    }
}
