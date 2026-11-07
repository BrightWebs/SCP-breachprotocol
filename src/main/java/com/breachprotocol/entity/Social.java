package com.breachprotocol.entity;

import com.breachprotocol.career.*;
import com.breachprotocol.compat.Unleashed;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.*;

/**
 * The social side of staff, after Minecraft Comes Alive Reborn, set in the Foundation.
 *
 * <ul>
 *   <li><b>Hearts</b> (trust) are personal: how much they like you.</li>
 *   <li><b>Respect</b> is professional: fixed by your rank, the same from everyone of their level,
 *       until someone forgets themselves. The insolent can be reprimanded, reclassified as Class-D
 *       or, from the Council, terminated; security carries it out.</li>
 *   <li><b>Admirers</b>: a few people quietly like you. They start warm, warm faster, go to pieces
 *       when you talk to them and look away when you pass. One day they may say so.</li>
 *   <li><b>Socialising</b>: chat, jokes, compliments, comfort, flirting, hugs. Whether it lands
 *       depends on who they are, their mood, how well they know you and how often you've tried today.</li>
 *   <li><b>Family</b>: spouses (sometimes on the same site), children, parents and the cover story
 *       they've been told. A death on site leaves someone grieving.</li>
 * </ul>
 */
@EventBusSubscriber(modid = com.breachprotocol.BreachProtocol.MODID)
public final class Social {
    private Social() {}

    static final int SOCIAL_C = 0xF0A8C8, DISCIPLINE_C = 0xE06060;

    // ================================================================== respect

    /** Their own standing in the Foundation (clearance level). */
    static int level(FoundationStaff s) {
        if (Management.headOf(s) != null) return 4;
        if (s.unit() != null) return 3;
        return switch (s.role()) {
            case JANITOR, CLERK -> 1;
            case TECHNICIAN, RESEARCHER, MEDIC -> 2;
            case GUARD -> 3;
            case SECRETARY -> 4;
        };
    }

    private static final int[] BY_RANK = {1, 3, 4, 5, 6, 8, 9, 10};

    /** Professional respect, 0..10: your rank sets it; only an insolent tongue lowers it. */
    static int respect(FoundationStaff s, ServerPlayer p) {
        Rank r = Career.rank(p);
        int v = Career.isAscended(p) ? 10 : BY_RANK[r.ordinal()];
        if (r.clearance < level(s)) v -= 1; // they outrank you
        if (slighted(s, p)) v = Math.min(v, 2);
        return Math.max(0, Math.min(10, v));
    }

    static String respectWord(int v) {
        return v >= 10 ? "Reverent" : v >= 9 ? "Deferential" : v >= 7 ? "Respectful" : v >= 5 ? "Courteous" : v >= 3 ? "Indifferent" : v >= 1 ? "Dismissive" : "Contemptuous";
    }

    static String bar(int v) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 5; i++) b.append(v >= (i + 1) * 2 ? "■" : v >= i * 2 + 1 ? "▪" : "□");
        return b.toString();
    }

    // ================================================================== insolence and discipline

    /** Some people resent rank. Never the secretary, never armed units (they answer to their own chain). */
    static boolean insolentType(FoundationStaff s) {
        if (s.role() == FoundationStaff.Role.SECRETARY || s.unit() != null || admirer(s)) return false;
        Personality pe = Personality.of(s);
        float g = s.gene(33);
        return switch (pe) { case CRABBY, GREEDY -> g < 0.35F; case GLOOMY, ODD, RELAXED -> g < 0.12F; default -> g < 0.04F; };
    }

    private static String rankKey(ServerPlayer p) { return Career.isAscended(p) ? "ASC" : Career.rank(p).name(); }

    /** They've disrespected you (and it hasn't been dealt with). */
    static boolean slighted(FoundationStaff s, ServerPlayer p) { return !Memory.get(s, p, "slight").isEmpty(); }

    /** Insolent and hasn't yet had their say at your current rank. */
    private static boolean dueToSneer(FoundationStaff s, ServerPlayer p) {
        if (!insolentType(s) || slighted(s, p)) return false;
        String done = Memory.get(s, p, "sneered");
        return !done.equals(rankKey(p)) && !Memory.get(s, p, "disciplined").equals(rankKey(p));
    }

    private static Openers.Opener sneer(ServerPlayer p, FoundationStaff s, Random r) {
        String a = Career.address(p);
        boolean before = !Memory.get(s, p, "sneered").isEmpty();
        Memory.put(s, p, "sneered", rankKey(p));
        Memory.put(s, p, "slight", "1");
        String text = before ? Talk.pick(r, "Promoted again? Congratulations. I'm sure you earned it. Somehow.", "New rank, same face. Do I salute now?")
                : switch (Personality.of(s)) {
            case CRABBY -> Talk.pick(r, "Oh, look who it is. Come to tell me how to do my job, " + a + "?", "What. I'm busy. Some of us actually work.");
            case GREEDY -> "Unless you've brought my overtime, " + a + ", I'm busy.";
            case GLOOMY -> "Rank doesn't mean much down here. You'll learn. Everyone does, eventually.";
            case ODD -> "You walk like someone who's never seen the inside of a cell. I have. Three times.";
            case RELAXED -> "Oh, it's you. Don't expect me to stand up.";
            default -> "Funny. They'll promote anyone these days.";
        };
        return new Openers.Opener(Clearance.RAW + text, List.of(
                new Memory.Reply("Watch your tone.", "", "!soc:tone", 0),
                new Memory.Reply("I'll let that one go.", Talk.pick(r, "...Generous of you.", "Hm. Fine."), "!soc:forgive", 1),
                new Memory.Reply("(Discipline them)", "", "!soc:discipline", 0)));
    }

    /** The disciplinary options open to your rank. */
    static void disciplinePage(ServerPlayer p, FoundationStaff s) {
        Dialogue.clearOptions(p);
        Convo.subtitle(p, s);
        Rank r = Career.rank(p);
        boolean top = Talk.topBrass(p);
        if (r.clearance > level(s) || top) Talk.ans(p, s, "This is a formal reprimand. It goes in your file.", "soc_reprimand", "Their record, their pay, their pride");
        else Talk.ans(p, s, "I'll be reporting this to your supervisor.", "soc_report", "You don't outrank them; their supervisor does");
        if (r.ordinal() >= Rank.LEVEL_5.ordinal() || top)
            Talk.ans(p, s, "Security. Take them. They're Class-D as of now.", "soc_reclassify", "Clearance revoked: security escorts them out and they become D-Class");
        if (r.ordinal() >= Rank.OVERSEER.ordinal() || top)
            Talk.ans(p, s, "Security. Termination order. Effective immediately.", "soc_terminate", "Council authority: security takes them away, and they don't come back");
        Talk.ans(p, s, "...Forget it.", "soc_forgive", "Let it go");
        Convo.topics(p, s);
    }

    // ================================================================== admirers

    /** Quietly likes you. Mostly women; always adults of working age; never someone married. */
    static boolean admirer(FoundationStaff s) {
        if (s.age() > 52 || married(s)) return false;
        float g = s.gene(37);
        return s.isFemale() ? g < 0.24F : g < 0.03F;
    }

    static boolean partner(FoundationStaff s, ServerPlayer p) { return Memory.get(s, p, "partner").equals("1"); }

    private static boolean shy(FoundationStaff s, ServerPlayer p) { return admirer(s) && !partner(s, p) && !Memory.get(s, p, "admire").equals("over"); }

    /** Admirers start warm. */
    static void seed(FoundationStaff s, ServerPlayer p) {
        String k = "bp_seed_" + p.getUUID();
        if (s.getPersistentData().getBoolean(k)) return;
        s.getPersistentData().putBoolean(k, true);
        if (admirer(s)) Convo.setTrust(s, p, Math.max(Convo.trust(s, p), 4 + (int) (s.gene(38) * 3)));
    }

    private static String she(FoundationStaff s) { return s.isFemale() ? "she" : "he"; }

    private static String her(FoundationStaff s) { return s.isFemale() ? "her" : "his"; }

    /** As you pass: a glance, then they look anywhere else. */
    private static void passBy(ServerPlayer p, FoundationStaff s, Random r) {
        long now = s.level().getGameTime();
        String k = "bp_pass_" + p.getUUID();
        if (now < s.getPersistentData().getLong(k)) return;
        s.getPersistentData().putLong(k, now + 1800 + r.nextInt(1800));
        Vec3 away = s.position().scale(2).subtract(p.position());
        s.getLookControl().setLookAt(away.x, s.getEyeY(), away.z);
        if (r.nextInt(4) == 0) s.getNavigation().stop();
        String n = s.getName().getString();
        String line = partner(s, p) ? Talk.pick(r, n + " catches your eye and smiles.", n + " gives you a small wave from across the room.")
                : Talk.pick(r, n + " glances up as you pass, then quickly looks away.", n + " fumbles " + her(s) + " clipboard as you walk by.",
                n + " suddenly finds the floor very interesting.", n + " straightens " + her(s) + " uniform when " + she(s) + " sees you.",
                n + " starts to wave, then pretends " + she(s) + " was fixing " + her(s) + " hair.", n + " goes a little red and pretends to read a memo.");
        p.displayClientMessage(Component.literal(line).withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC), true);
    }

    // ================================================================== how they sound (before personality)

    /** Respect, nerves and grief colour a line before their personality does. */
    static String colour(ServerPlayer p, FoundationStaff s, String line, Random r) {
        if (line.length() < 12) return line;
        if (widowedRecently(s) && r.nextInt(4) == 0) return line + " ...Sorry. I keep forgetting to breathe.";
        if (shy(s, p) && r.nextInt(100) < 45)
            return Talk.pick(r, "Oh! I- ", "Um. ", "S-sorry, ") + Personality.lower(line) + Talk.pick(r, "", " ...Sorry, I'm rambling.", " (" + Scene.cap(she(s)) + " tucks " + her(s) + " hair behind " + her(s) + " ear.)", " Ha. Um.");
        if (Lifecycle.bereaved(s) && r.nextInt(5) == 0) return line + " ...Sorry. My " + s.getPersistentData().getString("bp_loss_rel") + " died this week. I'm not myself.";
        if (partner(s, p) && r.nextInt(100) < 25) return line + Talk.pick(r, " ...I missed you today.", " Come find me after your shift?", "");
        int rs = respect(s, p);
        if (rs <= 2 && r.nextInt(100) < 50) return Talk.pick(r, "*sigh* ", "Ugh. ", "") + line + Talk.pick(r, " Happy?", " Whatever.", "");
        if (rs >= 9 && Convo.trust(s, p) < 5 && r.nextInt(100) < 30) return Talk.pick(r, "Of course. ", "Yes, " + Career.address(p) + ". ", "Certainly. ") + line;
        return line;
    }

    // ================================================================== socialising

    record Act(String id, String label, String hover) {}

    private static final Act[] ACTS = {
            new Act("chat", "Chat", "Small talk"), new Act("joke", "Tell a joke", "Foundation humour"),
            new Act("compliment", "Compliment", "Something nice about them or their work"), new Act("comfort", "Comfort", "When they're having a hard time"),
            new Act("flirt", "Flirt", "Only if you know each other well"), new Act("hug", "Hug", "Only if you're close")};

    /** Like MCA's Interact menu: the interactions take the place of the topic buttons, with a way back. */
    static void socialPage(ServerPlayer p, FoundationStaff s) {
        Dialogue.clearOptions(p);
        Convo.subtitle(p, s);
        int t = Convo.trust(s, p);
        boolean dclass = Scene.tier(p) == Scene.Tier.DCLASS;
        for (Act a : ACTS) {
            if (a.id.equals("flirt") && (t < 5 && !admirer(s) || dclass)) continue;
            if (a.id.equals("hug") && t < 6 && !partner(s, p)) continue;
            int tired = fatigue(s, p, a.id);
            Talk.opt(p, s, a.label, "soc_" + a.id, a.hover + (tired > 0 ? " (tried " + tired + "x today)" : ""), SOCIAL_C);
        }
        if (t < 5 && !admirer(s) && !dclass) Talk.opt(p, s, "Flirt", "soc_locked", "Get to know them better first (5 hearts)", 0x808080);
        if (t < 6 && !partner(s, p)) Talk.opt(p, s, "Hug", "soc_locked", "You're not that close yet (6 hearts)", 0x808080);
        Lifecycle.options(p, s);
        Talk.opt(p, s, "Back", "menu", "Back to the topics", 0xC8D0DA);
    }

    private static String fkey(FoundationStaff s, ServerPlayer p, String act) { return "bp_soc_" + p.getUUID() + "_" + act; }

    private static int fatigue(FoundationStaff s, ServerPlayer p, String act) {
        long day = s.level().getDayTime() / 24000;
        CompoundTag t = s.getPersistentData();
        return t.getLong(fkey(s, p, act) + "_d") == day ? t.getInt(fkey(s, p, act)) : 0;
    }

    private static void tire(FoundationStaff s, ServerPlayer p, String act) {
        long day = s.level().getDayTime() / 24000;
        CompoundTag t = s.getPersistentData();
        t.putInt(fkey(s, p, act), fatigue(s, p, act) + 1);
        t.putLong(fkey(s, p, act) + "_d", day);
    }

    private static boolean troubled(FoundationStaff s) {
        if (!s.illness().isEmpty() || widowedRecently(s) || Talk.anomalyNear(s, 20) != null) return true;
        WorldNews.Item d = WorldNews.latest(s.getServer(), WorldNews.Kind.DEATH);
        return d != null && s.getServer().overworld().getGameTime() - d.time() < 24000;
    }

    private static final String[] JOKES = {
            "Why did 173 fail its job interview? It kept blinking first. Wait. No. It didn't blink at all. That's the joke.",
            "How many D-Class does it take to change a lightbulb? Doesn't matter. There's a fresh batch at the end of the month.",
            "I asked 682 what it wanted for lunch. It said 'everything, and also your death.' Picky eater.",
            "What's 999's favourite exercise? The tickle-press.",
            "Ethics Committee walks into a bar. The bar is reclassified as Keter.",
            "I'd tell you the joke about 096, but you'd have to see its face to get it.",
            "Why don't we let 914 make the coffee? Because on 'Very Fine' it starts making you."};

    private static final String[] COMPLIMENTS = {"Good work on the last containment report.", "You keep this place running. People notice.",
            "You handled that breach drill better than anyone.", "Your notes are the only ones I actually read."};

    private static final String[] FLIRTS = {"You make this place a lot less grim.", "Is it the lighting down here, or do you always look this good?",
            "If I'd known you worked on this floor, I'd have transferred sooner.", "Save me a seat in the canteen?"};

    /** Tries an interaction. */
    static void act(ServerPlayer p, FoundationStaff s, String id) {
        Random r = new Random();
        Personality pe = Personality.of(s);
        int t = Convo.trust(s, p), tired = fatigue(s, p, id), rs = respect(s, p);
        boolean sad = troubled(s), shy = shy(s, p), mine = partner(s, p);
        String n = s.getName().getString();
        // what you said
        switch (id) {
            case "chat" -> Talk.you(p, Talk.pick(r, "How's your day going?", "Busy shift?", "Anything good happen today?", "Did you hear the canteen's doing a curry night?"));
            case "joke" -> Talk.you(p, JOKES[r.nextInt(JOKES.length)]);
            case "compliment" -> Talk.you(p, COMPLIMENTS[r.nextInt(COMPLIMENTS.length)]);
            case "comfort" -> Talk.you(p, Talk.pick(r, "Hey. You're not alone in this.", "Whatever it is, you don't have to carry it by yourself.", "You're doing better than you think."));
            case "flirt" -> Talk.you(p, FLIRTS[r.nextInt(FLIRTS.length)]);
            case "hug" -> Talk.note(p, "(You hug " + n + ".)");
            default -> {}
        }
        tire(s, p, id);
        // will it land?
        int chance = 55 + pe.affinity(id) * 10 + (t - 5) * 6 - tired * 25;
        if (sad) chance += id.equals("comfort") ? 25 : id.equals("joke") || id.equals("flirt") ? -30 : -10;
        else if (id.equals("comfort")) chance -= 20; // comfort for what?
        if (shy && (id.equals("flirt") || id.equals("compliment") || id.equals("hug"))) chance += 40;
        if (mine) chance += 50;
        boolean polite = false;
        if (rs >= 8 && t < 6 && (id.equals("joke") || id.equals("compliment"))) { chance += 20; polite = true; } // they laugh at the boss's jokes
        boolean ok = r.nextInt(100) < chance;
        String line = ok ? good(id, pe, s, p, shy, mine, polite, r) : bad(id, pe, s, p, tired, sad, r);
        Talk.say(p, s, Clearance.RAW + line);
        int dt = ok ? (polite ? 0 : id.equals("hug") || id.equals("flirt") ? 2 : 1) : (id.equals("flirt") || id.equals("hug") ? -2 : -1);
        if (shy && ok) dt += 1;
        Convo.setTrust(s, p, Math.max(0, Math.min(10, t + dt)));
        Memory.count(s, p, "soc_" + id + (ok ? "_ok" : "_no"));
        Memory.put(s, p, "soc_last", id + (ok ? ":ok" : ":no"));
        if (ok && id.equals("comfort") && sad && !s.illness().isEmpty() && r.nextInt(3) == 0) Talk.say(p, s, Clearance.RAW + "...I might actually go and see Medical. Thank you.");
        if (dt != 0) Talk.note(p, dt > 0 ? "(" + n + " likes you a little more.)" : "(That didn't go down well.)");
        socialPage(p, s);
    }

    private static String good(String id, Personality pe, FoundationStaff s, ServerPlayer p, boolean shy, boolean mine, boolean polite, Random r) {
        String a = Career.address(p);
        if (polite) return switch (id) {
            case "joke" -> Talk.pick(r, "Ha. Ha ha. Very good, " + a + ".", "(A careful, polite laugh.) Very funny, " + a + ".");
            default -> Talk.pick(r, "Thank you, " + a + ". That means a great deal.", "That's very kind of you, " + a + ".");
        };
        if (shy) return switch (id) {
            case "flirt" -> Talk.pick(r, "I- you- ...really? Oh. Oh no, I'm blushing, aren't I.", "(" + Scene.cap(she(s)) + " laughs, much too loudly.) Sorry! I mean. Yes. I'd like that.");
            case "compliment" -> Talk.pick(r, "You noticed? I- thank you. I'm going to think about that all day.", "Oh. Um. Thank you. You're very- you're also- thank you.");
            case "hug" -> "(" + Scene.cap(she(s)) + " freezes, then hugs you back, tight.) ...Sorry. Hi.";
            case "joke" -> "(" + Scene.cap(she(s)) + " laughs, and covers " + her(s) + " mouth.) That was terrible. Tell me another one.";
            default -> Talk.pick(r, "Better now. I mean- it's fine. It's good! Thank you for asking.", "You want to talk to me? I mean, yes. Hi.");
        };
        if (mine) return switch (id) {
            case "hug" -> "(" + Scene.cap(she(s)) + " leans into you.) Long day. Better now.";
            case "flirt" -> Talk.pick(r, "Careful. People will talk.", "Smooth. You know you don't have to try so hard anymore.");
            default -> Talk.pick(r, "You always know what to say.", "I'm glad it's you I get to see today.");
        };
        return switch (id) {
            case "chat" -> switch (pe) {
                case EXTROVERTED -> "Oh, busy! Let me tell you about the vending machine on B3. Actually, let me tell you about everyone.";
                case INTROVERTED -> "Quiet. Good.";
                case CRABBY -> "...Fine. It was fine. Thank you for asking, I suppose.";
                case GLOOMY -> "Nobody died on my floor. I'll call that a good day.";
                case ODD -> "I counted the ceiling tiles. There's one more than yesterday. I like it.";
                default -> Talk.pick(r, "Better now someone's asked. Thanks.", "Not bad, honestly. The coffee's hot for once.", "Quiet shift. Don't jinx it.");
            };
            case "joke" -> switch (pe) {
                case PLAYFUL -> "Ha! Okay, that's going on the breakroom whiteboard.";
                case ODD -> "(They laugh a beat too late, then keep laughing.) Oh, that's very good.";
                case RELAXED -> "Heh. Yeah. Classic.";
                default -> Talk.pick(r, "Ha! Don't let the Ethics Committee hear that.", "(They snort.) That's awful. I love it.");
            };
            case "compliment" -> switch (pe) {
                case GREEDY -> "Thanks. Put it in writing. Preferably on a bonus form.";
                case SENSITIVE -> "Really? I- thank you. I needed to hear that today.";
                case FLIRTY -> "Flattery will get you everywhere, " + a + ".";
                default -> Talk.pick(r, "That's kind of you. Thank you.", "Huh. Thank you. Doesn't happen often around here.");
            };
            case "comfort" -> Talk.pick(r, "...Thank you. I've been holding that in all week.", "It helps. It really does. Thank you.", "Nobody's said that to me in a long time.");
            case "flirt" -> switch (pe) {
                case FLIRTY -> "Oh, you're good. I'll keep that seat warm.";
                default -> Talk.pick(r, "(They laugh, a little surprised.) Well. That's the nicest thing I've heard on this floor.", "Careful. I might start believing you.");
            };
            case "hug" -> Talk.pick(r, "(They hug you back.) I didn't know I needed that.", "Oh! Okay. Okay, yes. Thank you.");
            default -> "Thanks.";
        };
    }

    private static String bad(String id, Personality pe, FoundationStaff s, ServerPlayer p, int tired, boolean sad, Random r) {
        if (tired >= 2) return Talk.pick(r, "Again? I do have work to do.", "We've done this. Twice. Today.");
        if (sad && (id.equals("joke") || id.equals("flirt"))) return Talk.pick(r, "...Not today. Please.", "Someone died. Read the room.");
        return switch (id) {
            case "chat" -> pe == Personality.INTROVERTED ? "...I'd rather not." : Talk.pick(r, "Can we do this later? I'm in the middle of something.", "Busy. Sorry.");
            case "joke" -> switch (pe) {
                case CRABBY -> "No.";
                case GLOOMY -> "People died for that anomaly, you know.";
                case SENSITIVE -> "That's... not funny. I knew someone in that cell block.";
                default -> Talk.pick(r, "(Silence.)", "I don't get it.", "Is that a Foundation joke or an Insurgency one?");
            };
            case "compliment" -> pe == Personality.CRABBY ? "Don't butter me up. What do you want?" : Talk.pick(r, "...Thanks? Is this an evaluation?", "Okay. Is there something you need?");
            case "comfort" -> sad ? "I'm fine. I don't want to talk about it." : "Comfort me? I'm fine. Are you okay?";
            case "flirt" -> Talk.pick(r, "That's... not appropriate. We work together.", "I'm going to pretend you didn't say that.", "HR has a form for this. I'd rather not fill it in.");
            case "hug" -> Talk.pick(r, "(They step back.) Personal space, please.", "Whoa. Let's keep it professional.");
            default -> "No.";
        };
    }

    // ================================================================== family

    private static final String[] WOMEN = {"Anna", "Claire", "Maria", "Sophie", "Grace", "Leah", "Nadia", "Emma", "Ruth", "Ines", "Hana", "Olivia"};
    private static final String[] MEN = {"Daniel", "Marcus", "Tom", "Samuel", "Leo", "Owen", "Ravi", "Jonas", "Felix", "Hugo", "Adam", "Mateo"};
    private static final String[] COVERS = {"a pharmaceutical company", "an insurance firm", "a shipping company's accounts department", "a government weather station",
            "a paper mill", "a medical supplies wholesaler", "a data centre in the middle of nowhere"};

    /** Their family, worked out the first time anyone asks and kept. */
    static CompoundTag family(FoundationStaff s) {
        CompoundTag root = s.getPersistentData();
        if (root.contains("bp_fam")) return root.getCompound("bp_fam");
        CompoundTag f = new CompoundTag();
        root.put("bp_fam", f);
        Random g = new Random(s.getUUID().getLeastSignificantBits() ^ 0x5CA1AB1EL);
        int age = s.age();
        boolean wed = age >= 25 && s.gene(40) < (age >= 35 ? 0.6F : 0.35F) && s.gene(37) >= (s.isFemale() ? 0.24F : 0.03F);
        f.putString("cover", COVERS[g.nextInt(COVERS.length)]);
        f.putBoolean("parents", age < 55 && g.nextInt(4) > 0);
        if (wed) {
            f.putBoolean("married", true);
            // perhaps someone on this site, if there is an unattached colleague
            FoundationStaff mate = null;
            if (g.nextInt(3) == 0)
                for (FoundationStaff o : s.level().getEntitiesOfClass(FoundationStaff.class, s.getBoundingBox().inflate(80))) {
                    if (o == s || o.isFemale() == s.isFemale() || o.getPersistentData().contains("bp_fam") || Math.abs(o.age() - age) > 12 || o.age() < 25 || admirer(o) || o.role() == FoundationStaff.Role.SECRETARY) continue;
                    mate = o;
                    break;
                }
            if (mate != null) {
                f.putString("spouse", mate.getName().getString());
                f.putUUID("spouseId", mate.getUUID());
                CompoundTag m = new CompoundTag();
                m.putBoolean("married", true);
                m.putString("spouse", s.getName().getString());
                m.putUUID("spouseId", s.getUUID());
                m.putString("cover", f.getString("cover"));
                m.putBoolean("parents", mate.age() < 55);
                mate.getPersistentData().put("bp_fam", m);
            } else f.putString("spouse", (s.isFemale() ? MEN : WOMEN)[g.nextInt(12)]);
            int kids = age < 28 ? 0 : g.nextInt(4);
            ListTag k = new ListTag();
            for (int i = 0; i < kids; i++) {
                boolean girl = g.nextBoolean();
                k.add(StringTag.valueOf((girl ? WOMEN : MEN)[g.nextInt(12)] + ":" + (girl ? "f" : "m") + ":" + Math.max(1, Math.min(age - 24, 2 + g.nextInt(Math.max(1, age - 25))))));
            }
            f.put("kids", k);
            if (mate != null) mate.getPersistentData().getCompound("bp_fam").put("kids", k.copy());
        }
        return f;
    }

    static boolean married(FoundationStaff s) {
        CompoundTag r = s.getPersistentData();
        return r.contains("bp_fam") && r.getCompound("bp_fam").getBoolean("married");
    }

    private static boolean spouseHere(CompoundTag f) { return f.hasUUID("spouseId"); }

    static boolean widowedRecently(FoundationStaff s) {
        CompoundTag r = s.getPersistentData();
        if (!r.contains("bp_fam")) return false;
        long w = r.getCompound("bp_fam").getLong("widowed");
        return w > 0 && s.level().getGameTime() - w < 24000 * 4;
    }

    static List<String> familyTraits(FoundationStaff s) {
        List<String> out = new ArrayList<>();
        CompoundTag r = s.getPersistentData();
        if (!r.contains("bp_fam")) return out;
        CompoundTag f = r.getCompound("bp_fam");
        if (f.getLong("widowed") > 0) out.add("widow" + (s.isFemale() ? "" : "er"));
        else if (f.getBoolean("married")) out.add(spouseHere(f) ? "married to " + f.getString("spouse") : "married");
        int k = f.getList("kids", 8).size();
        if (k > 0) out.add(k + (k == 1 ? " child" : " children"));
        return out;
    }

    /** "Do you have family?", from their actual family. */
    static String familyLine(FoundationStaff s, ServerPlayer p, Random r) {
        CompoundTag f = family(s);
        String cover = f.getString("cover");
        ListTag kids = f.getList("kids", 8);
        StringBuilder b = new StringBuilder();
        if (f.getLong("widowed") > 0) {
            b.append("I had ").append(s.isFemale() ? "a husband" : "a wife").append(". ").append(f.getString("spouse")).append(". ")
                    .append(f.getString("loss").isEmpty() ? "" : "They told me " + (s.isFemale() ? "he" : "she") + " " + f.getString("loss") + ". ")
                    .append("The Foundation has a word for it. I don't use it.");
        } else if (f.getBoolean("married")) {
            String sp = f.getString("spouse");
            if (spouseHere(f)) b.append("I'm married to ").append(sp).append(". Yes, here. We met in a decontamination shower, which is less romantic than it sounds. ");
            else b.append("My ").append(s.isFemale() ? "husband" : "wife").append(", ").append(sp).append(". ").append(Scene.cap(s.isFemale() ? "he" : "she")).append(" thinks I work for ").append(cover).append(". ");
        } else if (f.getBoolean("parents")) {
            b.append("My parents. They think I work for ").append(cover).append(". I visit when I'm allowed, and I lie about the weather. ");
        } else b.append(Talk.pick(r, "No. This place is the family. That's either very sad or very Foundation.", "Not anymore. It's easier, in this job.")).append(' ');
        if (!kids.isEmpty()) {
            String[] k = kids.getString(r.nextInt(kids.size())).split(":");
            boolean girl = k.length > 1 && k[1].equals("f");
            int age = k.length > 2 ? Integer.parseInt(k[2]) : 6;
            b.append(kids.size() == 1 ? "One " : kids.size() + " kids. My ").append(girl ? "daughter" : "son").append(kids.size() == 1 ? ", " : " ").append(k[0]).append(", is ").append(age).append(". ")
                    .append(age < 13 ? Talk.pick(r, "Draws me pictures of monsters. I put them on my desk and never tell " + (girl ? "her" : "him") + " how close " + (girl ? "she" : "he") + " gets.", "Thinks my job is boring. Best thing anyone's ever thought about me.")
                    : Talk.pick(r, "Wants to know what I really do. One day the answer will be a Class-A amnestic.", "Old enough to notice I come home quiet."));
        }
        return b.toString().trim();
    }

    /** One of ours died: their spouse on site grieves, their partner (you) is told. */
    static void died(FoundationStaff s, String how) {
        MinecraftServer srv = s.getServer();
        if (srv == null) return;
        // the registry
        Registry.Rec rec = Registry.of(s);
        long now = srv.overworld().getGameTime();
        if (how.contains("reclassified")) { rec.status = "dclass"; Registry.log(srv, rec.name + " " + how + "."); }
        else {
            rec.status = how.contains("terminated") ? "terminated" : "dead";
            rec.died = now;
            rec.cause = how;
            Registry.log(srv, rec.name + " " + how + ", aged " + s.age() + ".");
        }
        Registry.Rec sp = Registry.find(srv, rec.spouse);
        if (sp != null && rec.died > 0) sp.rel = "widowed";
        if (rec.partner != null) { Registry.Rec pa = Registry.find(srv, rec.partner); if (pa != null) { pa.partner = null; pa.rel = "single"; } }
        Registry.dirty(srv);
        CompoundTag r = s.getPersistentData();
        if (r.contains("bp_fam")) {
            CompoundTag f = r.getCompound("bp_fam");
            if (f.hasUUID("spouseId") && s.level() instanceof ServerLevel sl && sl.getEntity(f.getUUID("spouseId")) instanceof FoundationStaff w) {
                CompoundTag wf = family(w);
                wf.putLong("widowed", w.level().getGameTime());
                wf.putString("loss", how);
                for (ServerPlayer q : sl.players())
                    if (q.distanceTo(w) < 24) q.displayClientMessage(Component.literal(w.getName().getString() + " has just heard about " + s.getName().getString() + ".").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), false);
            }
        }
        for (String k : r.getAllKeys()) {
            if (!k.startsWith("bp_mem_") || !r.getCompound(k).getString("partner").equals("1")) continue;
            try {
                ServerPlayer q = srv.getPlayerList().getPlayer(UUID.fromString(k.substring(7)));
                if (q != null) q.sendSystemMessage(Component.literal("Word reaches you: " + s.getName().getString() + " " + how + ". You were the first person they would have wanted told.").withStyle(ChatFormatting.LIGHT_PURPLE));
            } catch (IllegalArgumentException ignored) {}
        }
    }

    // ================================================================== openers of our own

    /** A first line from who they are to you, or null to let the usual openers speak. Crises come first. */
    static Openers.Opener opener(ServerPlayer p, FoundationStaff s) {
        Sites.Site site = Sites.at(s.level(), s.blockPosition());
        if (Talk.anomalyNear(s, 24) != null || site != null && Defence.alertAt(site.name) != null) return null;
        Random r = new Random();
        String a = Career.address(p);
        if (widowedRecently(s) && !Memory.get(s, p, "condoled").equals(String.valueOf(family(s).getLong("widowed")))) {
            CompoundTag f = family(s);
            Memory.put(s, p, "condoled", String.valueOf(f.getLong("widowed")));
            return new Openers.Opener(Clearance.RAW + "I... sorry. I'm not really here today. " + f.getString("spouse") + " was my " + (s.isFemale() ? "husband" : "wife") + ".", List.of(
                    new Memory.Reply("I'm so sorry.", "Thank you. Everyone keeps saying that. You sound like you mean it.", "", 2),
                    new Memory.Reply("Take the rest of the week. That's an order.", "I- thank you. I don't know where I'll go. Somewhere quiet.", "!dayoff", 2),
                    new Memory.Reply("Do you want to talk about it?", "Not yet. But thank you for asking. Ask me again?", "condolence", 1),
                    new Memory.Reply("We all lose people here. Back to work.", "...Yes. Of course. Back to work.", "!work", -3)));
        }
        if (dueToSneer(s, p)) return sneer(p, s, r);
        int t = Convo.trust(s, p);
        if (shy(s, p)) {
            if (t >= 8 && Memory.get(s, p, "admire").isEmpty()) {
                String rank = Career.isAscended(p) || Talk.topBrass(p) ? "who you are" : "my superior";
                return new Openers.Opener(Clearance.RAW + "Can I... say something? Before I lose my nerve? I- I like you. More than I should, with you being " + rank + ". There. I said it. Oh god, I said it.", List.of(
                        new Memory.Reply("I feel the same way.", "You- really? (" + Scene.cap(she(s)) + " laughs, close to tears.) Okay. Okay! I have no idea what we do now.", "!soc:together", 2),
                        new Memory.Reply("I'm flattered. But I can't.", "No, of course. I knew that. I just- had to say it. Thank you for being kind about it.", "!soc:letdown", -1),
                        new Memory.Reply("Let's keep this professional.", "Yes. Absolutely. Sorry. Forget I said anything. Please.", "!soc:professional", -2)));
            }
            if (p.getPersistentData().getBoolean("bp_heard_" + s.getUUID()) && Memory.get(s, p, "heard").isEmpty()) {
                Memory.put(s, p, "heard", "1");
                return new Openers.Opener(Clearance.RAW + "Did... did someone say something to you? About me? Because whatever they said, it's- it's probably true. Oh no.", List.of(
                        new Memory.Reply("Maybe. Is it true?", "(" + Scene.cap(she(s)) + " can't look at you.) ...Maybe.", "", 2),
                        new Memory.Reply("Nobody said anything.", "Oh. Good! Great. Forget I asked.", "", 1)));
            }
            return new Openers.Opener(Clearance.RAW + Talk.pick(r, "Oh! H-hello, " + a + ". I wasn't- hi.", "You're talking to me? I mean. Hi. Hello.", "Oh, it's you! I mean- it's you. Hi.", "I was just- reports. Doing reports. Hi, " + a + "."), List.of(
                    new Memory.Reply("Are you alright? You seem flustered.", "Me? No! I'm fine. It's just warm in here. Is it warm in here?", "flustered", 1),
                    new Memory.Reply("Hello to you too.", "(" + Scene.cap(she(s)) + " smiles, and goes red.) Hi.", "", 1),
                    new Memory.Reply("How are things?", "", "→how", 0)));
        }
        if (partner(s, p) && r.nextInt(2) == 0)
            return new Openers.Opener(Clearance.RAW + Talk.pick(r, "There you are. I saved you a coffee. It's cold now. It's the thought.", "Hey, you. I was hoping you'd come by.", "You look tired. Are they working you too hard up there?"), List.of(
                    new Memory.Reply("Good to see you.", "You too. Always.", "", 1),
                    new Memory.Reply("I can't stay long.", "You never can. Go on. Be careful.", "", 0),
                    new Memory.Reply("How was your day?", "", "→how", 0)));
        // gossip: someone has noticed who likes you
        if (t >= 4 && r.nextInt(100) < 15 && !admirer(s)) {
            for (FoundationStaff o : s.level().getEntitiesOfClass(FoundationStaff.class, s.getBoundingBox().inflate(64))) {
                if (o == s || !shy(o, p) || p.getPersistentData().getBoolean("bp_heard_" + o.getUUID())) continue;
                p.getPersistentData().putBoolean("bp_heard_" + o.getUUID(), true);
                String on = o.getName().getString();
                return new Openers.Opener(Clearance.RAW + "Can I tell you something? Between us? I think " + on + " has a thing for you. " + Scene.cap(she(o)) + " goes red every time you walk past. It's honestly painful to watch.", List.of(
                        new Memory.Reply("Really?", "Really. Don't tell " + (o.isFemale() ? "her" : "him") + " I said anything.", "gossip", 1),
                        new Memory.Reply("That's none of your business.", "Fine. Fine! Just trying to help.", "", -1),
                        new Memory.Reply("I'll talk to " + (o.isFemale() ? "her" : "him") + ".", "Be nice. " + Scene.cap(she(o)) + "'s sweet.", "gossip", 1)));
            }
        }
        return lore(p, s, r);
    }

    /** Things the Foundation is like, brought up when the world gives a reason. */
    private static Openers.Opener lore(ServerPlayer p, FoundationStaff s, Random r) {
        MinecraftServer srv = s.getServer();
        long now = srv.overworld().getGameTime();
        Scene.Tier tier = Scene.tier(p);
        WorldNews.Item br = WorldNews.latest(srv, WorldNews.Kind.BREACH);
        if (br != null && now - br.time() < 24000 * 2 && r.nextInt(100) < 18 && Memory.get(s, p, "amn").isEmpty()) {
            Memory.put(s, p, "amn", "1");
            return new Openers.Opener(Clearance.RAW + "There was a breach? ...Huh. They gave half our corridor Class-B amnestics afterwards. I've lost most of a Tuesday. I keep finding notes in my own handwriting.", List.of(
                    new Memory.Reply("It's standard procedure.", "I know. I signed the form. Apparently.", "", 0),
                    new Memory.Reply("What do the notes say?", "Mostly 'DON'T OPEN THE NOTES.' I opened them anyway.", "", 1),
                    new Memory.Reply("Do you want to see Medical?", "Would they even tell me if something was wrong?", "!medical", 1)));
        }
        if (tier.ordinal() <= Scene.Tier.STAFF.ordinal() && r.nextInt(100) < 6 && Memory.get(s, p, "ethics").isEmpty()) {
            Memory.put(s, p, "ethics", "1");
            return new Openers.Opener(Clearance.RAW + "Someone told me the Ethics Committee signed off on the last D-Class rotation. I didn't think the Ethics Committee was real. Is it real?", List.of(
                    new Memory.Reply("It's real.", "Then I have a few complaints for it.", "", 1),
                    new Memory.Reply("Don't believe everything you hear.", "That's what someone on the Ethics Committee would say.", "", 0),
                    new Memory.Reply("Above my pay grade.", "Everything is. Even the ceiling.", "", 0)));
        }
        if (tier == Scene.Tier.DCLASS && r.nextInt(100) < 20 && Memory.get(s, p, "thirty").isEmpty()) {
            Memory.put(s, p, "thirty", "1");
            return new Openers.Opener(Clearance.RAW + (s.role() == FoundationStaff.Role.GUARD ? "Thirty days, D-Class. Do your thirty days and you walk out of here. That's what they tell you, isn't it?"
                    : "Thirty days, they say. Then a release. I've never met anyone who's done thirty days. Then again, they wouldn't remember me."), List.of(
                    new Memory.Reply("Is it true?", "(They look away.) Do your tests. Keep your head down.", "", 1),
                    new Memory.Reply("I'll make it.", "Then I hope I never see you again. I mean that kindly.", "", 1)));
        }
        return null;
    }

    /** Opener reply codes ("!soc:..."). True if handled (the window has been built). */
    static boolean code(ServerPlayer p, FoundationStaff s, String code) {
        Random r = new Random();
        switch (code) {
            case "!soc:tone" -> {
                boolean stubborn = (Personality.of(s) == Personality.CRABBY || Personality.of(s) == Personality.GREEDY) && s.gene(34) < 0.5F;
                if (stubborn) {
                    Talk.say(p, s, Clearance.RAW + Talk.pick(r, "Or what?", "My tone is fine. My pay isn't.", "Noted. Filed. Ignored."));
                    disciplinePage(p, s);
                } else {
                    Talk.say(p, s, Clearance.RAW + Talk.pick(r, "...Apologies, " + Career.address(p) + ". Long shift. It won't happen again.", "You're right. That was out of line. Sorry."));
                    resolve(s, p);
                    Convo.menu(p, s);
                }
                return true;
            }
            case "!soc:forgive" -> { resolve(s, p); return false; }
            case "!soc:discipline" -> { disciplinePage(p, s); return true; }
            case "!soc:together" -> {
                Memory.put(s, p, "partner", "1");
                Memory.put(s, p, "admire", "together");
                WorldNews.record(s.getServer(), WorldNews.Kind.HIRE, "romance", s.getName().getString() + " and " + p.getGameProfile().getName() + " are said to be seeing each other", 1, "");
                return false;
            }
            case "!soc:letdown" -> { Memory.put(s, p, "admire", "over"); return false; }
            case "!soc:professional" -> { Memory.put(s, p, "admire", "over"); return false; }
            default -> { return false; }
        }
    }

    private static void resolve(FoundationStaff s, ServerPlayer p) {
        Memory.put(s, p, "slight", "");
        Memory.put(s, p, "disciplined", rankKey(p));
    }

    /** The disciplinary page's choices. */
    static boolean handle(ServerPlayer p, FoundationStaff s, String what) {
        Random r = new Random();
        switch (what) {
            case "socialise" -> { Talk.you(p, Talk.pick(r, "Got a minute?", "How are you, really?", "Mind if I sit with you?")); Talk.say(p, s, Talk.pick(r, "Sure. What's on your mind?", "For you? Of course.", "I've got a minute.")); socialPage(p, s); return true; }
            case "soc_locked" -> { Talk.note(p, "(Not yet. Get to know them better first.)"); socialPage(p, s); return true; }
            case "discipline" -> { Talk.you(p, "About what you said."); disciplinePage(p, s); return true; }
            case "soc_forgive" -> { Talk.you(p, "...Forget it."); Talk.say(p, s, Clearance.RAW + "Thank you."); resolve(s, p); Convo.menu(p, s); return true; }
            case "soc_report" -> {
                Talk.you(p, "I'll be reporting this to your supervisor.");
                Talk.say(p, s, Clearance.RAW + Talk.pick(r, "Go ahead. They've heard worse from me.", "...Fine. Sorry. Don't. Please."));
                Convo.setTrust(s, p, Math.max(0, Convo.trust(s, p) - 1));
                resolve(s, p);
                Convo.menu(p, s);
                return true;
            }
            case "soc_reprimand" -> {
                Talk.you(p, "This is a formal reprimand. It goes in your file.");
                Talk.say(p, s, Clearance.RAW + switch (Personality.of(s)) {
                    case CRABBY -> "...Understood. " + Career.address(p) + ".";
                    case GREEDY -> "There goes the bonus. Understood.";
                    default -> Talk.pick(r, "Yes, " + Career.address(p) + ". I'm sorry. It won't happen again.", "Understood. I was out of line.");
                });
                Memory.put(s, p, "reprimanded", "1");
                Convo.setTrust(s, p, Math.max(0, Convo.trust(s, p) - 2));
                resolve(s, p);
                Convo.menu(p, s);
                return true;
            }
            case "soc_reclassify", "soc_terminate" -> {
                boolean kill = what.equals("soc_terminate");
                Rank rk = Career.rank(p);
                boolean top = Talk.topBrass(p);
                if (!top && rk.ordinal() < (kill ? Rank.OVERSEER : Rank.LEVEL_5).ordinal()) { Talk.say(p, s, Clearance.RAW + "You can't do that. You don't have the authority."); Convo.menu(p, s); return true; }
                Talk.you(p, kill ? "Security. Termination order. Effective immediately." : "Security. Take them. They're Class-D as of now.");
                CompoundTag f = family(s);
                String plea = f.getBoolean("married") || !f.getList("kids", 8).isEmpty()
                        ? "Wait- no, please, I have a family! " + (f.getList("kids", 8).isEmpty() ? f.getString("spouse") : "My kids") + "- please!"
                        : Talk.pick(r, "You can't be serious. Over a comment?", "Wait. Wait! I'm sorry! I take it back!", "...You'd really do it. Of course you would.");
                Talk.say(p, s, Clearance.RAW + plea);
                Dialogue.clearOptions(p);
                Talk.opt(p, s, "(Watch security take them)", "bye", "End the conversation", 0xC8D0DA);
                escort(p, s, kill);
                return true;
            }
            default -> {}
        }
        if (what.startsWith("soc_")) {
            String id = what.substring(4);
            for (Act a : ACTS) if (a.id.equals(id)) { act(p, s, id); return true; }
        }
        return false;
    }

    // ================================================================== security carries it out

    private record Escort(UUID offender, UUID guard, UUID by, boolean kill, long start, String dim) {}

    private static final List<Escort> ESCORTS = new ArrayList<>();

    private static void escort(ServerPlayer p, FoundationStaff s, boolean kill) {
        FoundationStaff g = null;
        double bd = 64 * 64;
        for (FoundationStaff o : s.level().getEntitiesOfClass(FoundationStaff.class, s.getBoundingBox().inflate(64)))
            if (o != s && (o.role() == FoundationStaff.Role.GUARD || o.unit() != null) && o.distanceToSqr(s) < bd) { bd = o.distanceToSqr(s); g = o; }
        s.getPersistentData().putBoolean("bp_escorted", true);
        s.getNavigation().stop();
        if (g != null) p.displayClientMessage(Component.literal(g.getName().getString() + " is on the way.").withStyle(ChatFormatting.DARK_AQUA), true);
        else p.displayClientMessage(Component.literal("Security has been called.").withStyle(ChatFormatting.DARK_AQUA), true);
        ESCORTS.add(new Escort(s.getUUID(), g == null ? null : g.getUUID(), p.getUUID(), kill, s.level().getGameTime(), s.level().dimension().location().toString()));
    }

    private static void finish(MinecraftServer srv, ServerLevel l, Escort e, FoundationStaff s, FoundationStaff g) {
        ServerPlayer by = srv.getPlayerList().getPlayer(e.by());
        String who = by == null ? "a superior" : Talk.topBrass(by) ? "the Administrator" : by.getGameProfile().getName();
        String n = s.getName().getString(), job = s.role().title;
        Vec3 at = s.position();
        if (e.kill()) {
            WorldNews.record(srv, WorldNews.Kind.DEATH, "terminated", n + " (" + job + ") was terminated by order of " + who + " for insubordination", -2, "");
            tell(l, at, "Security leads " + n + " away. " + (s.isFemale() ? "She" : "He") + " does not come back.");
            died(s, "was terminated");
        } else {
            String d = Names.dclass(srv, l.random);
            Unleashed.entity(Unleashed.DCLASS).ifPresent(type -> {
                Entity dc = type.create(l);
                if (dc == null) return;
                dc.moveTo(at.x, at.y, at.z, s.getYRot(), 0);
                if (dc instanceof Mob m) m.setPersistenceRequired();
                dc.setCustomName(Component.literal(d).withStyle(ChatFormatting.GOLD));
                l.addFreshEntity(dc);
            });
            WorldNews.record(srv, WorldNews.Kind.DEATH, "reclassified", n + " (" + job + ") was reclassified as Class-D (" + d + ") by order of " + who + " for insubordination", -1, "");
            tell(l, at, n + "'s clearance has been revoked. " + (s.isFemale() ? "She" : "He") + " is " + d + " now.");
            died(s, "was reclassified as Class-D");
        }
        if (g != null) g.order("post", null);
        s.leaveService();
    }

    private static void tell(ServerLevel l, Vec3 at, String text) {
        for (ServerPlayer q : l.players()) if (q.position().distanceTo(at) < 48) q.sendSystemMessage(Component.literal(text).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        MinecraftServer srv = event.getServer();
        long now = srv.overworld().getGameTime();
        // escorts under way
        for (Iterator<Escort> it = ESCORTS.iterator(); it.hasNext(); ) {
            Escort e = it.next();
            ServerLevel l = null;
            for (ServerLevel x : srv.getAllLevels()) if (x.dimension().location().toString().equals(e.dim())) l = x;
            if (l == null || !(l.getEntity(e.offender()) instanceof FoundationStaff s) || !s.isAlive()) { it.remove(); continue; }
            FoundationStaff g = e.guard() == null ? null : l.getEntity(e.guard()) instanceof FoundationStaff gg && gg.isAlive() ? gg : null;
            long t = now - e.start();
            if (g == null) {
                s.getNavigation().stop();
                if (t > 200) { finish(srv, l, e, s, null); it.remove(); }
                continue;
            }
            double d = g.distanceTo(s);
            String k = "bp_esc_at";
            if (d > 2.2 && !s.getPersistentData().contains(k)) {
                if (now % 10 == 0) g.getNavigation().moveTo(s, 1.25);
                s.getNavigation().stop();
                s.getLookControl().setLookAt(g, 30, 30);
                if (t > 20 * 40) { finish(srv, l, e, s, g); it.remove(); }
            } else {
                if (!s.getPersistentData().contains(k)) {
                    s.getPersistentData().putLong(k, now);
                    tell(l, s.position(), g.getName().getString() + " takes " + s.getName().getString() + " by the arm.");
                    ServerPlayer by = srv.getPlayerList().getPlayer(e.by());
                    Vec3 away = by == null ? s.position().add(10, 0, 0) : s.position().add(s.position().subtract(by.position()).normalize().scale(14));
                    g.getNavigation().moveTo(away.x, away.y, away.z, 0.8);
                }
                if (now % 5 == 0) s.getNavigation().moveTo(g, 0.9);
                if (now - s.getPersistentData().getLong(k) > 20 * 7) { finish(srv, l, e, s, g); it.remove(); }
            }
        }
        // admirers notice you
        if (now % 40 != 0) return;
        Random r = new Random();
        for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
            if (!Dialogue.active(p, "")) continue; // talking to someone already
            for (FoundationStaff s : p.level().getEntitiesOfClass(FoundationStaff.class, p.getBoundingBox().inflate(7))) {
                if (!admirer(s) || Memory.get(s, p, "admire").equals("over") || Dialogue.talkingTo(s) != null || !s.hasLineOfSight(p)) continue;
                if (Convo.trust(s, p) < 2 && !partner(s, p)) continue;
                passBy(p, s, r);
            }
        }
    }

    // ================================================================== overheard

    /** Couples, admirers and the disciplined give staff something to talk about. Null for the usual chatter. */
    static String[] chatter(FoundationStaff a, FoundationStaff b, List<ServerPlayer> ears, Random r) {
        CompoundTag fa = a.getPersistentData().getCompound("bp_fam");
        if (fa.hasUUID("spouseId") && fa.getUUID("spouseId").equals(b.getUUID()) && r.nextInt(2) == 0) {
            String[][] c = {{"Did you eat today?", "I had a coffee. That counts."}, {"Home on time tonight?", "If nothing gets out. So, no."},
                    {"The kids called.", "Did they ask what we do again?"}, {"Be careful on B3.", "You too. I mean it."}};
            return c[r.nextInt(c.length)];
        }
        for (ServerPlayer q : ears) {
            if (admirer(a) && Memory.get(a, q, "admire").isEmpty() && r.nextInt(3) == 0) {
                String n = q.getGameProfile().getName();
                return r.nextBoolean() ? new String[]{"Did you see? " + n + " said hello to me this morning.", "You're blushing. You're actually blushing."}
                        : new String[]{"Don't look now, but " + n + " is right there.", "Why are you hiding behind a clipboard?"};
            }
            if (partner(a, q) && r.nextInt(3) == 0) return new String[]{"Is it true? You and " + q.getGameProfile().getName() + "?", "...No comment. (She's smiling.)".replace("She", a.isFemale() ? "She" : "He")};
        }
        WorldNews.Item d = WorldNews.latest(a.getServer(), WorldNews.Kind.DEATH);
        if (d != null && (d.id().equals("reclassified") || d.id().equals("terminated")) && a.getServer().overworld().getGameTime() - d.time() < 24000 && r.nextInt(2) == 0)
            return r.nextBoolean() ? new String[]{"Did you hear? " + d.headline() + ".", "Keep your head down. And your mouth shut."}
                    : new String[]{"I worked with them, you know.", "Then you know better than to say it out loud."};
        return null;
    }
}
