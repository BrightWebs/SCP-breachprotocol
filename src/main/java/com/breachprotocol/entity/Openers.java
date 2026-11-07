package com.breachprotocol.entity;

import com.breachprotocol.career.*;
import com.breachprotocol.compat.Unleashed;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;

import java.util.*;

/**
 * The first thing someone says when you walk up to them, chosen from the moment (an anomaly loose
 * nearby, an attack, your wounds, their illness, what they remember about you, the news), each
 * with replies that fit it. Replies starting with "→" lead on into a topic.
 */
final class Openers {
    private Openers() {}

    record Opener(String text, List<Memory.Reply> replies) {}

    private static Memory.Reply r(String label, String reaction, String remember, int trust) { return new Memory.Reply(label, reaction, remember, trust); }

    static Opener of(ServerPlayer p, FoundationStaff s) {
        Random rnd = new Random();
        Talk.Temper t = Talk.temper(s);
        String a = Career.address(p);
        boolean guard = s.role() == FoundationStaff.Role.GUARD, medic = s.role() == FoundationStaff.Role.MEDIC;

        // something loose nearby comes first
        Mob loose = Talk.anomalyNear(s, 24);
        if (loose != null) {
            String what = Unleashed.designation(loose);
            return new Opener(Talk.howAreThings(s, p), List.of(
                    r("Stay calm. It's being handled.", guard ? "Copy. Holding position." : "...Okay. Okay. If you say so.", "", 1),
                    r("Get to safety. Now.", guard ? "Not while " + what + " is on my floor." : "Going! Don't have to tell me twice.", "", 0),
                    r("I'll deal with it myself.", "You're either very brave or very senior. I hope you're both.", "", 1)));
        }
        // an attack on the site
        Sites.Site site = Sites.at(s.level(), s.blockPosition());
        Defence.Alert al = site == null ? null : Defence.alertAt(site.name);
        if (al != null) {
            return new Opener(guard ? al.org + " on the grounds! Stay behind me, " + a + "!" : "We're under attack! " + al.org + "! What do we do?", List.of(
                    r("Hold the line.", guard ? "Holding! They won't get past this floor." : "I... I'll try. I'm not exactly trained for this.", "", 1),
                    r("Get inside and lock the doors.", "Doors. Right. Locking them now.", "", 0),
                    r("Who sent them?", al.org + ". Who else? They've wanted this site for years.", "", 0)));
        }
        if (Career.isAscended(p)) {
            return new Opener(Talk.howAreThings(s, p), List.of(
                    r("Breathe. You're safe with me.", "...Thank you. I... thank you. I'm sorry.", "reassured", 2),
                    r("Back to work.", "Yes. Of course. Right away.", "", 0),
                    r("You should be afraid.", "...I am.", "feared", -2)));
        }
        if (!s.illness().isEmpty()) {
            return new Opener(Talk.howAreThings(s, p), List.of(
                    r("Get to Medical.", t == Talk.Temper.CYNICAL ? "Medical. Right. Where they gave me this." : "I will. After my shift.", "", 0),
                    r("I'll find you some SCP-500.", "You'd do that? ...Thank you.", "promised500", 2),
                    r("Take the rest of the day off.", "I... yes. Thank you. I didn't know you could do that.", "", 1)));
        }
        if (p.getHealth() < 6) {
            return new Opener(Talk.howAreThings(s, p), medic ? List.of(
                    r("Patch me up.", "Hold still. There. You'll live.", "!heal", 1),
                    r("It's nothing.", "It's a lot of something. Sit down.", "!heal", 0),
                    r("Look after the others first.", "...That's noble and stupid. Sit. I'll do both.", "!heal", 2))
                    : List.of(r("It's nothing.", "It's a lot of something. Go to Medical.", "", 0),
                    r("I've had worse.", "That's not the reassurance you think it is.", "", 0),
                    r("Where's the nearest medic?", "Medical's on the second floor. Go.", "", 0)));
        }
        if (Talk.armed(p) && Career.rank(p).ordinal() < Rank.LEVEL_3.ordinal()) {
            return new Opener(Talk.howAreThings(s, p), List.of(
                    r("Orders.", "Whose orders? ...Never mind. I don't want to know.", "", 0),
                    r("Sorry. Putting it away.", "Thank you. Some of us don't like guns in the corridors.", "", 1),
                    r("Mind your own business.", "Fine. Security can mind it for me.", "", -2)));
        }
        // what they remember about you
        String recall = Memory.recall(s, p, rnd);
        if (!recall.isEmpty() && rnd.nextInt(3) > 0) {
            return new Opener(recall, List.of(
                    r("I remember.", "I thought you would. It stays with you, doesn't it.", "", 1),
                    r("Not here. Someone might hear.", "...Right. Sorry. Later.", "", 0),
                    r("Tell me more.", "There's not much more to tell. I just wanted you to know I hadn't forgotten.", "", 1)));
        }
        // a death since you last spoke
        WorldNews.Item death = WorldNews.latest(p.server, WorldNews.Kind.DEATH);
        if (death != null && p.server.overworld().getGameTime() - death.time() < 24000 && rnd.nextBoolean()) {
            return new Opener("Did you hear? " + Character.toUpperCase(death.headline().charAt(0)) + death.headline().substring(1) + ". " + WorldNews.ago(p.server, death) + ".", List.of(
                    r("I'm sorry.", "Thank you. Everyone keeps saying it's the job. It doesn't help.", "", 1),
                    r("They'll be remembered.", "I hope so. There's a wall on B1 with a lot of names on it.", "", 1),
                    r("It's the job.", t == Talk.Temper.CYNICAL ? "Yeah. That's what they say at every funeral." : "...I suppose it is.", "", -1)));
        }
        // news about you
        WorldNews.Item last = WorldNews.latest(p.server);
        if (last != null && Talk.mine(last, p) && p.server.overworld().getGameTime() - last.time() < 12000) {
            return new Opener(Talk.toYou(last, p, t, rnd), List.of(
                    r("Just doing my job.", "If everyone did their job like that, I'd sleep better.", "", 1),
                    r("Thank you.", "No, thank you.", "", 1),
                    r("It was the Council's call too.", "Maybe. But it was your name on it.", "", 0)));
        }
        // the elders
        if (s.age() >= 65 && rnd.nextInt(3) == 0) {
            return new Opener(Talk.howAreThings(s, p), List.of(
                    r("You've earned a rest.", "Maybe. But who'd keep an eye on the young ones?", "", 1),
                    r("We still need you.", "...That's kind. It's nice to be needed at my age.", "", 2),
                    r("Have you thought about the Fountain?", s.age() > 75 ? "Every day. I haven't decided yet." : "Not yet. Ask me in ten years.", "", 0)));
        }
        // the top brass walks in
        if (Talk.topBrass(p) && rnd.nextInt(3) == 0) {
            return new Opener(Talk.pick(rnd, "Administrator! We didn't know you were coming.", "Administrator. Everything is in order. I think.", "Administrator. Is there something you need from me?"), List.of(
                    r("At ease.", "Thank you, Administrator.", "", 1),
                    r("Just passing through.", "Of course. Mind the wet floor by the lifts.", "", 0),
                    r("How are things, really?", "", "→how_real", 0)));
        }
        // otherwise: how things are, and what they make of the news
        String line = Talk.howAreThings(s, p);
        boolean newsy = line.contains("talking about");
        return new Opener(line, newsy ? List.of(
                r("What do you make of it?", "", "→news_think", 0),
                r("Who's saying that?", "Everyone. The canteen, the lifts, the smoking area nobody admits exists.", "", 0),
                r("Let's not gossip.", "Fair enough. Old habit.", "", 0))
                : List.of(r("Glad to hear it.", t == Talk.Temper.CYNICAL ? "Don't get used to it." : "Me too.", "", 0),
                r("Anything I can do?", "", "→how_need", 1),
                r("Keep at it.", Talk.pick(rnd, "Always do.", "Will do, " + a + ".", "That's the plan."), "", 0)));
    }
}
