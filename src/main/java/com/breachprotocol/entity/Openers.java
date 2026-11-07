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
                    r("Get to safety. Now.", guard ? "Not while " + what + " is on my floor." : "Going! Don't have to tell me twice.", guard ? "" : "!flee", 0),
                    r("I'll deal with it myself.", "You're either very brave or very senior. I hope you're both.", "", 1)));
        }
        // an attack on the site
        Sites.Site site = Sites.at(s.level(), s.blockPosition());
        Defence.Alert al = site == null ? null : Defence.alertAt(site.name);
        if (al != null) {
            return new Opener(guard ? al.org + " on the grounds! Stay behind me, " + a + "!" : "We're under attack! " + al.org + "! What do we do?", List.of(
                    r("Hold the line.", guard ? "Holding! They won't get past this floor." : "I... I'll try. I'm not exactly trained for this.", "!hold", 1),
                    r("Get inside and lock the doors.", guard ? "Get inside, " + a + ". I'll cover you." : "Inside. Right. Going now.", guard ? "" : "!shelter", 0),
                    r("Who sent them?", al.org + ". Who else? They've wanted this site for years.", "", 0)));
        }
        if (Career.isAscended(p)) {
            return new Opener(Talk.howAreThings(s, p), List.of(
                    r("Breathe. You're safe with me.", "...Thank you. I... thank you. I'm sorry.", "reassured", 2),
                    r("Back to work.", "Yes. Of course. Right away.", "!work", 0),
                    r("You should be afraid.", "...I am.", "feared", -2)));
        }
        if (!s.illness().isEmpty()) {
            return new Opener(Talk.howAreThings(s, p), List.of(
                    r("Get to Medical.", t == Talk.Temper.CYNICAL ? "Fine. Medical. Where they gave me this." : "You're right. I'm going now.", "!medical", 0),
                    r("I'll find you some SCP-500.", "You'd do that? ...Thank you. I'll hold you to it.", "promised500", 2),
                    r("Take the rest of the day off.", "I... yes. Thank you. I'll sit down for a while.", "!dayoff", 1)));
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
                    r("Mind your own business.", "Fine. Security can mind it for me.", "!report", -2)));
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
        // otherwise: who you are to them, and where you both are
        Scene sc = Scene.of(p, s);
        String place = placeLine(sc);
        return switch (sc.tier) {
            case DCLASS -> guard
                    ? new Opener("Eyes down, " + a + ". What do you want?" + (place.isEmpty() ? "" : " " + place), List.of(
                    r("Nothing, sir.", "Then move along.", "", 0),
                    r("When do I get out of here?", "When the Council says so. Your file says thirty days. I've never seen anyone do thirty days.", "", 0),
                    r("I need something to eat.", "", "!food", 0)))
                    : s.role() == FoundationStaff.Role.RESEARCHER
                    ? new Opener("Ah. Subject " + a + ". You're on my list." + (place.isEmpty() ? "" : " " + place), List.of(
                    r("What's the test?", "", "→dtest", 0),
                    r("I'm not doing it.", "Everyone says that. Then they do it. The alternative is worse, and you know it.", "", -1),
                    r("Please. I have a family.", "...So do I. I'll put you on the easy ones this week. That's all I can do.", "", 2)))
                    : new Opener("Careful. If they see me talking to you, we both get a write-up." + (place.isEmpty() ? "" : " " + place), List.of(
                    r("I just need someone to talk to.", "...Alright. A minute. Then you go back.", "", 2),
                    r("Got anything to eat?", "", "!food", 0),
                    r("Never mind.", "Probably for the best.", "", 0)));
            case JUNIOR -> new Opener(sc.pick("Hey, " + a + ". ", "Oh, hi. ", a + "! ") + (place.isEmpty() ? Talk.howAreThings(s, p) : place), List.of(
                    r("Busy day?", sc.pick("Every day's a busy day. Some are just busier.", "Two breach drills and a coffee machine that bites. So, yes."), "", 0),
                    r("Heard anything good?", "", "→news", 0),
                    r("Catch you later.", "See you around.", "", 0)));
            case STAFF -> new Opener(a + ". " + (place.isEmpty() ? Talk.howAreThings(s, p) : place), List.of(
                    r("Status?", "", "→report", 0),
                    r("Anything I should know?", "", "→news", 0),
                    r("Carry on.", "Will do.", "", 0)));
            case DIRECTOR -> new Opener("Director. " + (place.isEmpty() ? "All quiet " + sc.here() + "." : place), List.of(
                    r("Full report.", "", "→report", 0),
                    r("Any problems?", "", "→work_problems", 0),
                    r("Carry on.", "Director.", "", 0)));
            default -> new Opener(sc.pick("Administrator! We didn't know you were coming. ", "Administrator. ", a + ". ") + (place.isEmpty() ? "Everything is in order " + sc.here() + "." : place), List.of(
                    r("At ease.", "Thank you, " + a + ".", "", 1),
                    r("Report.", "", "→report", 0),
                    r("How are things, really?", "", "→how_real", 0)));
        };
    }

    /** What they say about where you both are (empty when there's nothing to say). */
    static String placeLine(Scene sc) {
        FoundationStaff s = sc.s;
        Sites.Cell c = sc.cell();
        String scp = c == null ? "" : Unleashed.designation(c.entity.contains(":") ? c.entity.substring(c.entity.indexOf(':') + 1) : c.entity);
        return switch (sc.place.kind()) {
            case "dclass" -> s.role() == FoundationStaff.Role.GUARD ? "Headcount's right on the block. Everyone's where they should be." : "The block's quiet today. Nobody's cried since breakfast.";
            case "deep", "keter", "heavy" -> !scp.isEmpty() ? scp + " is in the cell behind that door. " + sc.pick("I don't turn my back on it.", "It's been quiet. That's when I worry.", "Readings are normal. Whatever normal means for " + scp + ".")
                    : "Nobody's ever calm " + sc.here() + ". The walls hum.";
            case "light" -> !scp.isEmpty() ? "Light Containment. " + scp + " is next door. The safe ones, supposedly." : "Light Containment. The safe ones, supposedly.";
            case "lobby" -> "Visitors in and out all day. Half of them shouldn't be here.";
            case "research" -> sc.pick("Two tests booked this afternoon. Both on the Safe list, thankfully.", "The lab's running a long observation. Don't touch the red folders.");
            case "medical" -> {
                int sick = 0;
                for (FoundationStaff o : s.level().getEntitiesOfClass(FoundationStaff.class, s.getBoundingBox().inflate(64))) if (!o.illness().isEmpty()) sick++;
                yield sick == 0 ? "The ward's empty. Let's keep it that way." : sick + " of our people are on the ward. We're short of SCP-500.";
            }
            case "security" -> {
                int armed = sc.site == null ? 0 : Forces.data(sc.p.server).station(sc.site.name).members.size();
                yield armed + " armed personnel on " + (sc.site == null ? "site" : sc.site.name) + " right now. " + (armed < 6 ? "Not enough." : "Enough, if nothing big comes.");
            }
            case "archive" -> "Every dossier you're cleared for is in that terminal. Some you aren't.";
            case "servers" -> "Server temperature's normal. Mostly.";
            case "launch" -> "Launch Control's quiet. It should always be quiet.";
            case "councilfloor", "chamber" -> "This floor makes everyone whisper.";
            case "adminoffice" -> "The Administrator's office. I always straighten my collar before coming up.";
            case "site01" -> "Site-01. Older than the Foundation, some say.";
            case "garden" -> "Is this... the Garden? I don't think I should be here.";
            case "outside" -> "We're off site ground. Nobody's watching our backs out here.";
            default -> "";
        };
    }
}
