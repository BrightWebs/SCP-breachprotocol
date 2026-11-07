package com.breachprotocol.entity;

import com.breachprotocol.career.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;

/**
 * The conversation engine: keeps a conversation going for as long as you want it to (until you say
 * goodbye). After everything someone says it works out what kind of thing it was (a question about
 * you, an invitation, a yes-or-no, a statement) and offers replies that fit; your reply gets a
 * reaction composed from who they are (personality), how they feel about you (hearts), how they
 * regard you (respect) and what is going on (news, their work, their family, the orders you gave
 * them), and then they carry on: the next beat of what they were saying, a question for you, or
 * something new on their mind. What you tell them is remembered and comes back later.
 */
final class Converse {
    private Converse() {}

    // ================================================================== attitudes and who likes them

    /** The spirit of an answer. */
    enum Att { HOPE, GRIM, DUTY, CARE, JOKE, DOUBT, NONE }

    private static int likes(Personality pe, Att a) {
        if (a == Att.NONE) return 0;
        return switch (pe) {
            case FRIENDLY -> a == Att.CARE || a == Att.HOPE ? 1 : a == Att.GRIM ? -1 : 0;
            case UPBEAT -> a == Att.HOPE || a == Att.JOKE ? 1 : a == Att.GRIM || a == Att.DOUBT ? -1 : 0;
            case PLAYFUL -> a == Att.JOKE ? 1 : a == Att.DUTY ? -1 : 0;
            case GLOOMY -> a == Att.GRIM || a == Att.DOUBT ? 1 : a == Att.HOPE ? -1 : 0;
            case SENSITIVE -> a == Att.CARE ? 1 : a == Att.JOKE || a == Att.GRIM ? -1 : 0;
            case GREEDY -> a == Att.DOUBT ? 1 : a == Att.DUTY ? -1 : 0;
            case ODD -> a == Att.JOKE || a == Att.DOUBT ? 1 : 0;
            case CRABBY -> a == Att.GRIM || a == Att.DUTY ? 1 : a == Att.JOKE || a == Att.HOPE ? -1 : 0;
            case EXTROVERTED -> a == Att.JOKE || a == Att.CARE ? 1 : 0;
            case INTROVERTED -> a == Att.DUTY ? 1 : a == Att.JOKE ? -1 : 0;
            case RELAXED -> a == Att.JOKE || a == Att.HOPE ? 1 : a == Att.DUTY ? -1 : 0;
            case ANXIOUS -> a == Att.CARE ? 1 : a == Att.GRIM ? -1 : 0;
            case PEACEFUL -> a == Att.CARE || a == Att.HOPE ? 1 : a == Att.DUTY ? -1 : 0;
            case FLIRTY -> a == Att.JOKE || a == Att.CARE ? 1 : 0;
        };
    }

    // ================================================================== state

    /** One of your possible replies: what you say, its spirit, what it does, and what they'll say back (if fixed). */
    record Reply(String label, Att att, String code, String reaction) {
        Reply(String label, Att att, String code) { this(label, att, code, ""); }
    }

    /** Something they've got on their mind: things to say in order, then a question for you. */
    record Thread(String kind, List<String> beats, String question, List<Reply> answers) {}

    private static final class State {
        int entity = -1;
        String last = "";
        Thread thread;
        int beat;
        List<Reply> asked;      // replies to the engine's own question, if that's what they just said
        List<Reply> offered = new ArrayList<>();
        final Set<String> used = new HashSet<>();
        int gained, turns;
    }

    /** One conversation per person you talk to: come back to them and it picks up where it left off. */
    private static final Map<String, State> ST = new HashMap<>();

    private static State st(ServerPlayer p, FoundationStaff s) {
        return ST.computeIfAbsent(p.getUUID() + "/" + s.getUUID(), k -> {
            State x = new State();
            x.entity = s.getId();
            // what they've already talked to you about today (kept with them, so it survives a restart)
            long day = s.level().getDayTime() / 24000;
            String u = Memory.get(s, p, "cv_used");
            if (u.startsWith(day + ":")) x.used.addAll(Arrays.asList(u.substring(u.indexOf(':') + 1).split(",")));
            return x;
        });
    }

    private static void used(ServerPlayer p, FoundationStaff s, State x, String kind) {
        x.used.add(kind);
        long day = s.level().getDayTime() / 24000;
        Memory.put(s, p, "cv_used", day + ":" + String.join(",", x.used));
        Memory.count(s, p, "cv_talked_" + kind);
    }

    /** Called with every line a member of staff says to you (as you see it). */
    static void heard(ServerPlayer p, FoundationStaff s, String line) {
        State x = st(p, s);
        x.last = line;
        x.asked = null;
    }

    /** They say it, and it's a question with these answers. */
    private static void ask(ServerPlayer p, FoundationStaff s, String q, List<Reply> answers) {
        Talk.say(p, s, q);
        st(p, s).asked = answers;
    }

    private static void say(ServerPlayer p, FoundationStaff s, String line) { Talk.say(p, s, line); }

    // ================================================================== the replies under whatever they said

    /** Adds replies that fit the last thing they said (the topic page calls this when there are none). */
    static void replies(ServerPlayer p, FoundationStaff s) {
        State x = st(p, s);
        List<Reply> out = new ArrayList<>();
        Random r = new Random();
        String last = x.last == null ? "" : x.last.trim();
        String tail = lastSentence(last).toLowerCase(Locale.ROOT);
        boolean q = last.endsWith("?") || last.endsWith("?)");
        if (x.asked != null) out.addAll(x.asked);
        else if (q && (tail.contains("how are you") || tail.contains("how about you") || tail.contains("are you okay") || tail.contains("are you alright") || tail.contains("how are things with you") || tail.contains("how's your"))) {
            out.add(new Reply("Good, thanks.", Att.HOPE, "me_good"));
            out.add(new Reply("Tired, if I'm honest.", Att.CARE, "me_tired"));
            out.add(new Reply("It's been a hard week.", Att.GRIM, "me_hard"));
            out.add(new Reply("Better for seeing you.", Att.CARE, "me_flatter"));
        } else if (q && (tail.contains("coffee") || tail.contains("lunch") || tail.contains("canteen") || tail.contains("seat") || tail.contains("come find me") || tail.contains("would you like") || tail.contains("want to") || tail.contains("tell me another"))) {
            out.add(new Reply(Talk.pick(r, "I'd like that.", "Sure. Why not.", "Go on, then."), Att.CARE, "accept"));
            out.add(new Reply(Talk.pick(r, "Not today. Maybe another time.", "I can't, sorry."), Att.DUTY, "decline"));
        } else if (q && (tail.contains("is that bad") || tail.contains("isn't it") || tail.contains("is it real") || tail.contains("do you think") || tail.contains("right?") || tail.contains("don't you"))) {
            out.add(new Reply("No. You're fine.", Att.HOPE, "reassure"));
            out.add(new Reply("Honestly? Yes.", Att.GRIM, "confirm"));
            out.add(new Reply("Hard to say.", Att.DOUBT, "unsure"));
        } else if (q) {
            out.add(new Reply("Yes.", Att.NONE, "yes"));
            out.add(new Reply("No.", Att.NONE, "no"));
            out.add(new Reply("Why do you ask?", Att.DOUBT, "whyask"));
        } else {
            // a statement: draw them out, or take it somewhere
            if (x.thread != null && x.beat < x.thread.beats().size()) out.add(new Reply(Talk.pick(r, "Go on.", "And?", "Tell me more."), Att.NONE, "more"));
            else if (last.length() > 20) out.add(new Reply(Talk.pick(r, "Go on.", "What do you mean?"), Att.NONE, "elaborate"));
            if (last.length() > 20) out.add(new Reply("Why do you say that?", Att.DOUBT, "why"));
            if (last.length() > 20) {
                out.add(new Reply(Talk.pick(r, "I know what you mean.", "I feel the same.", "Fair enough."), Att.CARE, "agree"));
                out.add(new Reply(Talk.pick(r, "I don't see it that way.", "I'm not sure about that."), Att.DOUBT, "disagree"));
            }
            out.add(new Reply(Talk.pick(r, "What's on your mind?", "Talk to me. Anything.", "What else is going on?"), Att.NONE, "else"));
        }
        out = byRank(p, s, out, q, r);
        while (out.size() > 5) out.remove(out.size() - 1);
        x.offered = out;
        for (int i = 0; i < out.size(); i++) Talk.ans(p, s, out.get(i).label(), "cv_" + i, hover(out.get(i)));
    }

    /** How you'd say it, given who you are; and what someone of your rank can offer them. */
    private static List<Reply> byRank(ServerPlayer p, FoundationStaff s, List<Reply> in, boolean question, Random r) {
        Scene.Tier t = Scene.tier(p);
        boolean high = t.ordinal() >= Scene.Tier.DIRECTOR.ordinal(), top = t.ordinal() >= Scene.Tier.COUNCIL.ordinal(), d = t == Scene.Tier.DCLASS;
        List<Reply> out = new ArrayList<>();
        for (Reply rp : in) {
            String l = switch (rp.code()) {
                case "more" -> d ? "...Go on." : high ? "Continue." : null;
                case "elaborate" -> high ? "Explain." : d ? "What d'you mean?" : null;
                case "why" -> high ? "On what grounds?" : d ? "Why's that?" : null;
                case "agree" -> top ? "You're right. Noted." : d ? "Yeah. Same here." : null;
                case "disagree" -> top ? "I'll be the judge of that." : high ? "I don't agree." : d ? "If you say so..." : null;
                case "else" -> top ? "Speak freely. What's on your mind?" : high ? "Anything I should know about?" : d ? "Can I ask you something?" : null;
                case "yes" -> top ? "Yes." : d ? "Yeah." : null;
                case "no" -> d ? "Nah." : null;
                case "me_good" -> d ? "Still alive. That's good, here." : top ? "Well enough. Thank you for asking." : null;
                case "me_tired" -> d ? "Tired. They had us up at four." : top ? "Tired. The Council sat late." : null;
                case "me_hard" -> d ? "They ran another test. I don't want to talk about it." : top ? "It's been a hard week. Decisions with costs." : null;
                case "accept" -> top ? "I'll make time." : null;
                case "decline" -> top ? "Duty calls. Another time." : d ? "Can't. They count us at six." : null;
                default -> null;
            };
            out.add(l == null ? rp : new Reply(l, rp.att(), rp.code(), rp.reaction()));
        }
        if (question || Memory.get(s, p, "cv_promise").equals("open")) return out;
        // what your rank lets you offer
        boolean troubled = !s.illness().isEmpty() || Social.widowedRecently(s) || WorldNews.morale(p.server) < 0;
        if (high && !s.illness().isEmpty()) out.add(0, new Reply("Go to Medical. That's an order.", Att.CARE, "act:medical", "Yes, " + Career.address(p) + ". Thank you. I'll go now."));
        else if (high && troubled && r.nextBoolean()) out.add(0, new Reply("Take the rest of the day. I'll clear it.", Att.CARE, "act:dayoff", "I- thank you, " + Career.address(p) + ". I won't forget this."));
        else if (top) out.add(new Reply("The Council will hear about this.", Att.DUTY, "promise", "...They will? Thank you. I didn't think anyone up there listened."));
        else if (high) out.add(new Reply("Leave it with me. I'll look into it.", Att.DUTY, "promise", "Thank you, Director. Really."));
        else if (d) out.add(new Reply("Don't tell anyone I asked.", Att.NONE, "mem:dsecret=1", "Tell who? Nobody asks me anything."));
        else out.add(new Reply("Should I report this to someone?", Att.DUTY, "mem:report=1", "Maybe. Quietly. You didn't hear it from me."));
        return out;
    }

    private static String hover(Reply r) {
        return switch (r.att()) {
            case HOPE -> "Hopeful"; case GRIM -> "Grim"; case DUTY -> "Matter-of-fact"; case CARE -> "Warm";
            case JOKE -> "Light-hearted"; case DOUBT -> "Sceptical"; case NONE -> "";
        };
    }

    private static String lastSentence(String s) {
        String t = s.replaceAll("\\([^)]*\\)\\s*$", "").trim();
        int i = Math.max(t.lastIndexOf(". ", t.length() - 2), Math.max(t.lastIndexOf("! ", t.length() - 2), t.lastIndexOf("? ", t.length() - 2)));
        return i < 0 ? t : t.substring(i + 2);
    }

    // ================================================================== your reply, their reaction, and on they go

    static boolean handle(ServerPlayer p, FoundationStaff s, String what) {
        if (!what.startsWith("cv_")) return false;
        State x = st(p, s);
        int n;
        try { n = Integer.parseInt(what.substring(3)); } catch (NumberFormatException e) { return false; }
        if (n < 0 || n >= x.offered.size()) { Convo.menu(p, s); return true; }
        Reply rp = x.offered.get(n);
        x.turns++;
        Random r = new Random();
        Personality pe = Personality.of(s);
        Talk.you(p, rp.label());
        boolean carryOn = true;
        switch (rp.code()) {
            case "more" -> { if (x.thread == null || x.beat >= x.thread.beats().size()) { say(p, s, elaborate(p, s, r)); carryOn = false; break; }
                say(p, s, x.thread.beats().get(x.beat++)); carryOn = false; if (x.beat >= x.thread.beats().size()) askThread(p, s, x); }
            case "elaborate" -> { say(p, s, elaborate(p, s, r)); carryOn = false; maybeContinue(p, s, x, r, 70); }
            case "why" -> { say(p, s, reason(p, s, r)); carryOn = false; maybeContinue(p, s, x, r, 60); }
            case "else" -> { carryOn = false; start(p, s, x, r, true); }
            default -> {
                // an answer with a spirit: they react to it (and to you)
                int like = likes(pe, rp.att());
                String react = !rp.reaction().isEmpty() ? rp.reaction() : reaction(p, s, rp, like, r);
                say(p, s, react);
                apply(p, s, x, rp, like);
            }
        }
        if (carryOn) maybeContinue(p, s, x, r, chatty(pe));
        Convo.menu(p, s);
        return true;
    }

    /** How likely they are to keep talking unprompted. */
    private static int chatty(Personality pe) {
        return switch (pe) { case EXTROVERTED, FRIENDLY, UPBEAT, FLIRTY, PLAYFUL -> 85; case INTROVERTED, CRABBY -> 35; case ANXIOUS, GLOOMY -> 55; default -> 65; };
    }

    /** What your reply does: hearts (they like people who think like them), memory and real actions. */
    private static void apply(ServerPlayer p, FoundationStaff s, State x, Reply rp, int like) {
        if (like > 0 && x.gained < 2) { Convo.setTrust(s, p, Convo.trust(s, p) + 1); x.gained++; Talk.note(p, "(" + s.getName().getString() + " likes that.)"); }
        if (like < 0 && (Personality.of(s) == Personality.CRABBY || Personality.of(s) == Personality.SENSITIVE)) Convo.setTrust(s, p, Convo.trust(s, p) - 1);
        String c = rp.code();
        if (c.startsWith("mem:")) {
            String[] kv = c.substring(4).split("=", 2);
            Memory.put(s, p, "cv_" + kv[0], kv.length > 1 ? kv[1] : rp.att().name());
        }
        switch (c) {
            case "promise" -> { Memory.put(s, p, "cv_promise", "open"); Memory.put(s, p, "cv_promise_what", lastSentence(x.last)); }
            case "me_good", "me_tired", "me_hard" -> Memory.put(s, p, "cv_you_mood", c.substring(3));
            case "kept" -> { Memory.put(s, p, "cv_promise", "kept"); Convo.setTrust(s, p, Convo.trust(s, p) + 2); }
            case "broken" -> { Memory.put(s, p, "cv_promise", "broken"); Convo.setTrust(s, p, Convo.trust(s, p) - 1); }
            case "pending" -> Memory.put(s, p, "cv_promise", "open");
            case "act:work" -> s.order("post", p);
            case "act:stay" -> s.order("stay", p);
            case "act:follow" -> s.order("follow", p);
            case "act:dayoff" -> s.dayOff();
            case "act:medical" -> s.toMedical();
            default -> {}
        }
    }

    private static void maybeContinue(ServerPlayer p, FoundationStaff s, State x, Random r, int chance) {
        if (r.nextInt(100) >= chance) return;
        if (x.thread != null && x.beat < x.thread.beats().size()) { say(p, s, x.thread.beats().get(x.beat++)); if (x.beat >= x.thread.beats().size() && r.nextBoolean()) askThread(p, s, x); return; }
        if (x.thread != null && x.beat >= x.thread.beats().size() && x.thread.question() != null && !x.used.contains("q:" + x.thread.kind())) { askThread(p, s, x); return; }
        start(p, s, x, r, false);
    }

    private static void askThread(ServerPlayer p, FoundationStaff s, State x) {
        if (x.thread.question() == null) return;
        used(p, s, x, "q:" + x.thread.kind());
        ask(p, s, x.thread.question(), x.thread.answers());
    }

    /** Something new on their mind. */
    private static void start(ServerPlayer p, FoundationStaff s, State x, Random r, boolean asked) {
        Thread t = Threads.pick(p, s, x.used, r);
        if (t == null) {
            say(p, s, Talk.pick(r, "Honestly? I've run out of things to say. It's nice, just standing here a minute.", "Nothing much. It's quiet. Quiet is good."));
            return;
        }
        used(p, s, x, t.kind());
        x.thread = t;
        x.beat = 0;
        if (asked && Social.respect(s, p) >= 8 && Convo.trust(s, p) < 6) say(p, s, Clearance.RAW + Talk.pick(r, "If you don't mind me saying, " + Career.address(p) + "...", "Since you ask...", "Well. There is something."));
        if (t.beats().isEmpty()) askThread(p, s, x);
        else {
            say(p, s, t.beats().get(x.beat++));
            if (x.beat >= t.beats().size() && t.question() != null) askThread(p, s, x);
        }
    }

    // ================================================================== reactions, composed

    private static String reaction(ServerPlayer p, FoundationStaff s, Reply rp, int like, Random r) {
        String a = Career.address(p);
        int rs = Social.respect(s, p), t = Convo.trust(s, p);
        Personality pe = Personality.of(s);
        String core = switch (rp.code()) {
            case "me_good" -> Talk.pick(r, "Good. Someone should be.", "Glad to hear it.", "That's something, at least.");
            case "me_tired" -> Talk.pick(r, "Get some sleep. If they let you.", "You look it. No offence.", "Everyone is. It's the lighting. And the anomalies.");
            case "me_hard" -> Talk.pick(r, "I'm sorry. If there's anything I can do from down here, say so.", "It has been. For all of us. You're carrying more of it than most.");
            case "me_flatter" -> Convo.trust(s, p) >= 4 ? Talk.pick(r, "(They laugh.) Smooth.", "Oh, stop it. ...Don't actually stop.") : "...Right. Thank you. I think.";
            case "accept" -> Talk.pick(r, "Good! I'll hold you to that.", "Great. It's a date. I mean- it's a plan.", "I'll save you a seat.");
            case "decline" -> Talk.pick(r, "Another time, then.", "Of course. You're busy.", "No, I understand.");
            case "reassure" -> Talk.pick(r, "...Okay. Thank you. I needed someone to say that.", "Really? Okay. Okay.");
            case "confirm" -> Talk.pick(r, "I knew it. Thank you for not lying to me.", "...Right. Well. At least I know.");
            case "unsure" -> Talk.pick(r, "That's the Foundation answer.", "Hard to say. Everything is, here.");
            case "yes" -> Talk.pick(r, "Right. Good.", "I thought so.", "Okay.");
            case "no" -> Talk.pick(r, "Oh. Okay.", "Fair enough.", "No? Huh.");
            case "whyask" -> Talk.pick(r, "No reason. Just making conversation. People don't, much, down here.", "Curiosity. It's a professional hazard.");
            case "agree" -> like >= 0 ? Talk.pick(r, "Exactly. Nobody else seems to see it.", "Right? Thank you.") : Talk.pick(r, "Do you? Huh.", "Hm. Maybe.");
            case "disagree" -> like > 0 ? "Fair. I could be wrong. I usually am." : Talk.pick(r, "Well, that's your view.", "We'll see who's right.");
            default -> "";
        };
        if (core.isEmpty()) core = like > 0 ? agreeLine(pe, r) : like < 0 ? disagreeLine(pe, r) : Talk.pick(r, "Hm. Maybe so.", "I suppose that's one way to see it.", "Right.");
        // disagreement with someone who outranks them comes out politely, or not at all
        if (like < 0 && rs >= 8) core = Talk.pick(r, "With respect, " + a + ", I'm not sure I agree. ", "If you say so, " + a + ". ") + (r.nextBoolean() ? "" : core);
        else if (like > 0 && t >= 7) core = core + Talk.pick(r, "", " This is why I like talking to you.", " You get it.");
        return core;
    }

    private static String agreeLine(Personality pe, Random r) {
        return switch (pe) {
            case GLOOMY -> "Finally, someone realistic.";
            case UPBEAT -> "Yes! That's exactly it!";
            case PLAYFUL -> "Ha! Exactly. Put that on a mug.";
            case CRABBY -> "...Hm. You're not as daft as you look.";
            case SENSITIVE -> "That's kind. Thank you.";
            case GREEDY -> "See, you understand how things really work.";
            case ODD -> "Yes. The vents agree, too.";
            case INTROVERTED -> "Mm. Yes.";
            case ANXIOUS -> "Oh, good. Good. I thought it was just me.";
            default -> Talk.pick(r, "Exactly.", "That's what I think too.", "You're right.");
        };
    }

    private static String disagreeLine(Personality pe, Random r) {
        return switch (pe) {
            case GLOOMY -> "That's a nice thought. It won't survive the week.";
            case UPBEAT -> "Oh, don't say that. It's not that bad.";
            case PLAYFUL -> "Wow. Okay. Tough crowd.";
            case CRABBY -> "No. Wrong.";
            case SENSITIVE -> "...That's a hard thing to hear.";
            case GREEDY -> "Easy to say when you're not on my pay grade.";
            case ODD -> "Hm. That's not what the walls said.";
            case INTROVERTED -> "...Hm.";
            case ANXIOUS -> "Please don't say that. I'll be thinking about it all night.";
            case PEACEFUL -> "Maybe. Let's not argue about it.";
            default -> Talk.pick(r, "I don't think so.", "Hm. Not how I see it.");
        };
    }

    /** "Go on." with nothing more planned: they say more about how they feel and why. */
    private static String elaborate(ServerPlayer p, FoundationStaff s, Random r) {
        if (!s.illness().isEmpty()) return "It's this " + s.illness() + ". It sits on everything. Hard to think about anything else.";
        if (Social.widowedRecently(s)) return "I just keep expecting " + Social.family(s).getString("spouse") + " to walk round the corner.";
        WorldNews.Item n = WorldNews.latest(p.server);
        if (n != null && p.server.overworld().getGameTime() - n.time() < 24000 * 2 && !Talk.mine(n, p) && Clearance.knows(Scene.tier(p), n))
            return "It's everything lately. " + Scene.cap(n.headline()) + ". " + (n.mood() >= 0 ? "Good news, sure. It just makes you wonder what's next." : "You start to wonder whose turn is next.");
        return switch (s.role()) {
            case RESEARCHER -> "I've been staring at the same test logs for days. When an anomaly does nothing, you start to wonder what it's waiting for.";
            case GUARD -> "You stand at a door for eight hours, you start to notice things. Footsteps that don't match anyone. That kind of thing.";
            case MEDIC -> "I see what comes out of the containment wings. You don't forget it. You just file it.";
            case TECHNICIAN -> "Everything here runs on power and luck. I'm in charge of one of those.";
            case JANITOR -> "People don't look at the cleaner. So the cleaner sees everything.";
            case CLERK -> "I process the paperwork for every incident. I know how many people we lose. Nobody else counts.";
            case SECRETARY -> "I see every file that crosses your desk before you do. Some days I wish I didn't.";
        };
    }

    /** "Why do you say that?" */
    private static String reason(ServerPlayer p, FoundationStaff s, Random r) {
        int morale = WorldNews.morale(p.server);
        if (Talk.anomalyNear(s, 24) != null) return "Because there's something loose and I can see it from here. That's why.";
        if (s.age() >= 60) return "Because I've been here " + Math.min(Talk.years(s), s.age() - 21) + " years. You start to see the pattern.";
        if (morale < 0) return "Because the last few weeks have been bad, and everyone's pretending they haven't.";
        return switch (Personality.of(s)) {
            case GLOOMY -> "Experience. Mostly bad experience.";
            case ODD -> "Instinct. And a dream I had. Mostly the dream.";
            case CRABBY -> "Because it's true. Do I need another reason?";
            case GREEDY -> "Because nobody here gets paid for what they actually do.";
            case ANXIOUS -> "I don't know! That's the problem. I never know.";
            default -> Talk.pick(r, "Just a feeling. You get them, working here.", "Something about this week. It's too quiet, or not quiet enough.");
        };
    }

    // ================================================================== what's on their mind

    static final class Threads {
        private Threads() {}

        static Thread pick(ServerPlayer p, FoundationStaff s, Set<String> used, Random r) {
            List<Thread> all = new ArrayList<>();
            add(all, used, order(p, s, r));
            add(all, used, recall(p, s, r));
            add(all, used, life(p, s, r));
            add(all, used, news(p, s, r));
            add(all, used, work(p, s, r));
            add(all, used, aboutYou(p, s, r));
            add(all, used, family(p, s, r));
            add(all, used, colleague(p, s, r));
            add(all, used, hobby(p, s, r));
            add(all, used, place(p, s, r));
            add(all, used, rumour(p, s, r));
            add(all, used, small(p, s, r));
            if (all.isEmpty()) return null;
            // the first ones are the most pressing: favour them
            int i = Math.min(all.size() - 1, (int) (Math.pow(r.nextDouble(), 1.8) * all.size()));
            return all.get(i);
        }

        private static void add(List<Thread> all, Set<String> used, Thread t) { if (t != null && !used.contains(t.kind())) all.add(t); }

        private static Reply rep(String label, Att a, String code) { return new Reply(label, a, code); }

        /** The orders you gave them, and what they make of them. */
        static Thread order(ServerPlayer p, FoundationStaff s, Random r) {
            String o = Memory.get(s, p, "last_order");
            if (o.isEmpty()) return null;
            long t = Memory.of(s, p).getLong("t_last_order");
            long ago = s.level().getGameTime() - t;
            if (ago > 24000) return null;
            int min = (int) (ago / 1200);
            String since = min < 1 ? "a moment ago" : min + " minute" + (min == 1 ? "" : "s") + " ago";
            return switch (o) {
                case "stay" -> new Thread("order", List.of("You told me to wait here " + since + ". So I've been waiting. Watching the corridor."),
                        "Do you need me here much longer?", List.of(rep("A bit longer. Stay put.", Att.DUTY, "act:stay"), rep("No. Back to work.", Att.DUTY, "act:work"), rep("Come with me instead.", Att.CARE, "act:follow")));
                case "follow" -> new Thread("order", List.of("I've been following you since " + since + ". Not complaining. Just... where are we going?"),
                        "Should I keep following?", List.of(rep("Yes. Stay close.", Att.DUTY, "act:follow"), rep("You can go back to your post.", Att.DUTY, "act:work"), rep("Wait here for me.", Att.CARE, "act:stay")));
                case "sit" -> new Thread("order", List.of("Thanks for letting me sit down. My feet were about to file a complaint."),
                        "Should I get back to it?", List.of(rep("Take your time.", Att.CARE, "act:stay"), rep("Yes, back to work.", Att.DUTY, "act:work")));
                default -> null;
            };
        }

        /** Something you told them before. */
        static Thread recall(ServerPlayer p, FoundationStaff s, Random r) {
            String note = Memory.get(s, p, "wf_note");
            if (!note.isEmpty()) {
                Memory.put(s, p, "wf_note", "");
                String a = Career.address(p);
                return switch (note) {
                    case "commend" -> new Thread("note", List.of("I got your commendation, " + a + ". I- I've pinned it above my desk. Nobody's ever done that before."), "Did you mean it?",
                            List.of(rep("Every word.", Att.CARE, "mem:commend=meant"), rep("You earned it.", Att.DUTY, "mem:commend=earned")));
                    case "thanks" -> new Thread("note", List.of("Thank you for the note. It's a small thing. It wasn't small to me."), null, List.of());
                    case "concern" -> new Thread("note", List.of("You wrote to ask if I was alright. I'm... getting there. It helped, knowing someone noticed."), "Do you really want to know how I am?",
                            List.of(rep("I do. Tell me.", Att.CARE, "mem:concern=listen"), rep("Only if you want to.", Att.CARE, "mem:concern=gentle")));
                    default -> new Thread("note", List.of("I received your formal warning, " + a + ". Understood. It won't happen again."), null, List.of());
                };
            }
            if (Memory.get(s, p, "cv_promise").equals("open") && s.level().getGameTime() - Memory.of(s, p).getLong("t_cv_promise") > 24000) {
                String what = Memory.get(s, p, "cv_promise_what");
                return new Thread("promise", List.of("You said you'd see to something for me." + (what.endsWith("?") || what.isEmpty() ? " I haven't forgotten. I don't think you have either." : " When I said \"" + (what.length() > 60 ? what.substring(0, 57) + "..." : what) + "\".")),
                        "Did anything come of it?", List.of(new Reply("It's been dealt with.", Att.HOPE, "kept", "Thank you. You don't know what that means down here."),
                        new Reply("Not yet. I haven't forgotten.", Att.DUTY, "pending", "Okay. I'll trust you."),
                        new Reply("I'm sorry. It wasn't possible.", Att.GRIM, "broken", "...Right. I understand. I suppose.")));
            }
            String mood = Memory.get(s, p, "cv_you_mood");
            if (!mood.isEmpty() && s.level().getGameTime() - Memory.of(s, p).getLong("t_cv_you_mood") > 6000) {
                Memory.put(s, p, "cv_you_mood", "");
                String b = switch (mood) {
                    case "tired" -> "Last time you said you were tired. I've been meaning to ask: did you get any sleep?";
                    case "hard" -> "You said it had been a hard week. I've thought about that. Is it any better?";
                    default -> "You seemed in good spirits last time. It rubbed off. Thank you for that.";
                };
                return new Thread("recall", List.of(b), "How are you now?", List.of(rep("Better, thanks to people like you.", Att.CARE, "mem:recall=better"), rep("Same as ever.", Att.DUTY, "mem:recall=same"), rep("Worse, honestly.", Att.GRIM, "mem:recall=worse")));
            }
            for (String[] m : new String[][]{
                    {"hobby", "teach", "You asked me to show you " + HOBBIES[(int) (s.gene(45) * HOBBIES.length)] + " sometime. The offer stands. I've been practising so I don't embarrass myself."},
                    {"fam", "yes", "You said someone's waiting for you outside. I hope they know how lucky they are that you come home."},
                    {"fam", "no", "You said there's no one outside, just the job. I've been thinking about that. You're not alone in here, you know."},
                    {"myth", "yes", "You were so sure the Council's real. I can't stop thinking about it. Thirteen people. Somewhere above us."},
                    {"news", "hope", "You said we'd come back from it. We did, a bit. I think of that when it gets bad."},
                    {"col", "care", "I told the others what you said. About looking out for each other. It's caught on."}}) {
                if (Memory.get(s, p, "cv_" + m[0]).equals(m[1]) && Memory.get(s, p, "cv_back_" + m[0]).isEmpty()
                        && s.level().getGameTime() - Memory.of(s, p).getLong("t_cv_" + m[0]) > 12000) {
                    Memory.put(s, p, "cv_back_" + m[0], "1");
                    return new Thread("recall_" + m[0], List.of(m[2]), null, List.of());
                }
            }
            String work = Memory.get(s, p, "cv_top");
            if (!work.isEmpty() && Memory.get(s, p, "cv_top_back").isEmpty()) {
                Memory.put(s, p, "cv_top_back", "1");
                String b = switch (work) {
                    case "CARE" -> "You told me it gets lonely, where you are. I keep thinking about that. If it helps: people down here notice you.";
                    case "GRIM" -> "You said it was heavy. The decisions. I believe you. I wouldn't want them.";
                    case "JOKE" -> "'Mostly paperwork', you said. I told the canteen. They didn't believe me either.";
                    default -> "You said it's worth it. I've been holding onto that this week.";
                };
                return new Thread("recall", List.of(b), null, List.of());
            }
            return null;
        }

        static Thread news(ServerPlayer p, FoundationStaff s, Random r) {
            Scene sc = Scene.of(p, s);
            List<WorldNews.Item> n = sc.news();
            if (n.isEmpty()) return null;
            WorldNews.Item i = n.get(r.nextInt(Math.min(3, n.size())));
            boolean good = i.mood() >= 0;
            String follow = switch (i.kind()) {
                case DEATH -> Talk.pick(r, "Someone has to clear out their desk. I keep thinking about that part.", "We'll get a memo. Two lines. That's what we get.");
                case ATTACK -> good ? "They didn't get far. Security earned their coffee." : "They got closer than anyone's admitting.";
                case BREACH -> "Every breach starts with someone saying 'it's probably fine'.";
                case DECISION -> good ? "Whatever the Council's doing, it's working. For now." : "I don't pretend to understand the Council. I just live with what they decide.";
                case HIRE -> "New faces. They always look so hopeful the first week.";
                case FORCES, MTF -> "More guns on site. I can't decide if that makes me feel safer.";
                case TREATMENT -> "Somebody up there still cares about us. Good to know.";
                default -> good ? "Good news, for once." : "It's been that kind of week.";
            };
            String q = good ? Talk.pick(r, "Do you think it'll last?", "Feels like things are turning around. Don't you think?") : Talk.pick(r, "Do you think it'll happen again?", "What do you make of it?");
            List<Reply> a = good ? List.of(rep("It'll last. We've earned it.", Att.HOPE, "mem:news=hope"), rep("Enjoy it while it does.", Att.GRIM, "mem:news=grim"), rep("We'll keep working for it.", Att.DUTY, "mem:news=duty"))
                    : Scene.tier(p).ordinal() >= Scene.Tier.DIRECTOR.ordinal()
                    ? List.of(rep("It won't happen again. I'll see to it.", Att.DUTY, "promise"), rep("We'll come back from it.", Att.HOPE, "mem:news=hope"), rep("Are you holding up alright?", Att.CARE, "mem:news=care"), rep("We knew the risks.", Att.GRIM, "mem:news=grim"))
                    : Scene.tier(p) == Scene.Tier.DCLASS
                    ? List.of(rep("Nobody tells us anything.", Att.DOUBT, "mem:news=doubt"), rep("Better them than me.", Att.GRIM, "mem:news=grim"), rep("Are you okay?", Att.CARE, "mem:news=care"))
                    : List.of(rep("We'll come back from it.", Att.HOPE, "mem:news=hope"), rep("Probably. It's the Foundation.", Att.GRIM, "mem:news=grim"), rep("Are you holding up alright?", Att.CARE, "mem:news=care"), rep("That's the job.", Att.DUTY, "mem:news=duty"));
            return new Thread("news", List.of(sc.relay(i), follow), q, a);
        }

        static Thread work(ServerPlayer p, FoundationStaff s, Random r) {
            Scene sc = Scene.of(p, s);
            Sites.Cell c = sc.cell();
            String cell = c == null ? "the containment wing" : "the " + c.name;
            List<String> b = switch (s.role()) {
                case RESEARCHER -> List.of(Talk.pick(r, "I'm three weeks into a test log on " + cell + ". Nothing's changed. That's the worrying part.", "We ran another round of tests near " + cell + " this morning. I'm still writing it up."),
                        "Half my job is noticing what hasn't happened. The other half is writing it down so nobody else has to notice.");
                case GUARD -> List.of(Talk.pick(r, "Quiet shift on " + sc.here() + ". I've checked every door twice.", "Eight hours on this post. I could tell you where every crack in the floor is."),
                        "They train us to shoot. Nobody trains you to stand still for this long.");
                case MEDIC -> List.of("Two in the infirmary this morning. Nothing serious. Nothing anomalous, anyway.", "I keep SCP-500 on the list of things I'd sell my soul for. Some days it's at the top.");
                case TECHNICIAN -> List.of("The power on " + sc.here() + " keeps dipping. I've told them. Again.", "If the lights go, the doors go. If the doors go, everything goes. So I watch the lights.");
                case JANITOR -> List.of("Someone tracked something black and sticky all down the corridor. I'm not asking what it was.", "You'd be amazed what people leave behind after an incident. Shoes, mostly.");
                case CLERK -> List.of("Forty incident reports this week. Forty. And every one of them says 'no further action'.", "I file what happens. I don't get to decide what it means.");
                case SECRETARY -> List.of("Your calendar's under control. Your inbox is... less so.", "The Overseers' offices call more when something's wrong. They've been calling a lot.");
            };
            Scene.Tier tier = Scene.tier(p);
            if (tier == Scene.Tier.DCLASS)
                return new Thread("work", b, "Do you ever wonder what they're really testing on you lot?", List.of(rep("Every day.", Att.GRIM, "mem:work=grim"),
                        rep("Not my place to wonder.", Att.DUTY, "mem:work=duty"), rep("Better than a cell. Just.", Att.JOKE, "mem:work=joke"), rep("I just want to make my thirty days.", Att.HOPE, "mem:work=hope")));
            if (tier.ordinal() >= Scene.Tier.COUNCIL.ordinal())
                return new Thread("work", b, "May I ask you something? Does it look different from up there? All of this?", List.of(rep("It does. I've seen the numbers. You matter.", Att.HOPE, "mem:work=hope"),
                        rep("From up there it's all risk. You're the ones who carry it.", Att.CARE, "mem:work=care"), rep("That's what the Council is for.", Att.DUTY, "mem:work=duty"),
                        rep("Mostly it looks like paperwork.", Att.JOKE, "mem:work=joke"), rep("I'll make sure your section gets what it needs.", Att.DUTY, "promise")));
            String q = Social.respect(s, p) >= 8 ? "May I ask you something? Does it look different from up there? All of this?" : "Do you ever wonder if any of it does any good?";
            return new Thread("work", b, q, List.of(rep("Every day you're here, it does.", Att.HOPE, "mem:work=hope"), rep("Some days I wonder.", Att.DOUBT, "mem:work=doubt"),
                    rep("It's the job. We do it.", Att.DUTY, "mem:work=duty"), rep("It looks like paperwork, mostly.", Att.JOKE, "mem:work=joke")));
        }

        /** They ask about you: your life, your rank. */
        static Thread aboutYou(ServerPlayer p, FoundationStaff s, Random r) {
            int rs = Social.respect(s, p), t = Convo.trust(s, p);
            if (!Memory.get(s, p, "cv_top").isEmpty()) return null;
            if (rs >= 8) {
                String what = Talk.topBrass(p) ? "being the Administrator" : Career.rank(p).ordinal() >= Rank.OVERSEER.ordinal() ? "sitting on the Council" : "running the site";
                return new Thread("you", List.of(t >= 5 ? "Can I ask you something personal?" : "Forgive me. I've always wondered something."), "What's it actually like, " + what + "?",
                        List.of(rep("Heavy. Every decision costs someone.", Att.GRIM, "mem:top=GRIM"), rep("Worth it. Most days.", Att.HOPE, "mem:top=HOPE"),
                                rep("Lonely, sometimes.", Att.CARE, "mem:top=CARE"), rep("Mostly paperwork.", Att.JOKE, "mem:top=JOKE")));
            }
            return new Thread("you", List.of(), Talk.pick(r, "So what brought you to the Foundation? Everyone's got a story.", "How long have you been here, anyway?"),
                    List.of(rep("I saw something I shouldn't have.", Att.GRIM, "mem:top=GRIM"), rep("I wanted to protect people.", Att.HOPE, "mem:top=HOPE"),
                            rep("They made me an offer I couldn't refuse.", Att.JOKE, "mem:top=JOKE"), rep("Long enough.", Att.DUTY, "mem:top=DUTY")));
        }

        /** Their life right now: a baby on the way, a new one at home, an engagement, a loss. */
        static Thread life(ServerPlayer p, FoundationStaff s, Random r) {
            Registry.Rec me = Lifecycle.ensure(s);
            long now = s.level().getGameTime();
            if (me.due > 0 && !me.player) return new Thread("life", List.of(Convo.trust(s, p) >= 3 ? "Can I tell you something? I'm expecting. " + (me.due - now < Lifecycle.PREGNANCY / 3 ? "Any day now, actually." : "Early days.") : "I'm- well. Expecting. Don't tell the canteen."),
                    "Do you think this is any place to raise a family?", List.of(rep("It's the safest place in the world.", Att.HOPE, "mem:baby=safe"), rep("Nowhere is safe. That's why we're here.", Att.GRIM, "mem:baby=grim"), rep("Congratulations. Truly.", Att.CARE, "mem:baby=care")));
            for (Registry.Rec k : Registry.children(s.getServer(), me.id))
                if (k.alive() && now - k.born < FoundationStaff.YEAR) return new Thread("life", List.of("We had the baby. " + k.name.split(" ")[0] + ". I haven't slept in a week. I've never been happier."),
                        "Do you want to see a picture?", List.of(rep("Of course!", Att.CARE, "mem:pic=yes"), rep("Maybe later.", Att.DUTY, "mem:pic=no")));
            if (me.rel.equals("engaged") && !me.player && me.partner != null) {
                Registry.Rec o = Registry.find(s.getServer(), me.partner);
                if (o != null && !o.player) return new Thread("life", List.of("I'm engaged! To " + o.name + ". Yes, the one from " + (o.job.isEmpty() ? "upstairs" : o.job) + ". Don't make that face."),
                        "Will you come to the wedding?", List.of(rep("I wouldn't miss it.", Att.CARE, "mem:wed=yes"), rep("If the Council lets me.", Att.DUTY, "mem:wed=maybe")));
            }
            if (Lifecycle.bereaved(s)) return new Thread("life", List.of("My " + s.getPersistentData().getString("bp_loss_rel") + " died. " + s.getPersistentData().getString("bp_loss_who") + ". At home. I couldn't be there. They thought I was at a conference."),
                    null, List.of());
            return null;
        }

        static Thread family(ServerPlayer p, FoundationStaff s, Random r) {
            if (Convo.trust(s, p) < 3 || !Memory.get(s, p, "cv_fam").isEmpty()) return null;
            CompoundTag f = Social.family(s);
            String b;
            if (f.getBoolean("married") && f.hasUUID("spouseId")) b = f.getString("spouse") + " and I had our anniversary last week. We celebrated by both working a double shift. Very romantic.";
            else if (f.getBoolean("married")) b = f.getString("spouse") + " called last night. Asked how work was. I said 'boring'. " + (s.isFemale() ? "He" : "She") + " believed me. " + (s.isFemale() ? "He" : "She") + " always does.";
            else if (!f.getList("kids", 8).isEmpty()) b = "My kid asked me what I do at work. I said I keep monsters in boxes. They laughed. I didn't.";
            else if (f.getBoolean("parents")) b = "My mother keeps asking when I'll get a normal job. I tell her soon. I've been telling her soon for years.";
            else b = "I don't have anyone outside, really. That's not a complaint. It makes this job easier.";
            return new Thread("family", List.of(b), "Do you have anyone waiting for you out there?",
                    List.of(rep("I do.", Att.CARE, "mem:fam=yes"), rep("No. Just the job.", Att.DUTY, "mem:fam=no"), rep("That's classified.", Att.JOKE, "mem:fam=classified")));
        }

        static Thread colleague(ServerPlayer p, FoundationStaff s, Random r) {
            List<FoundationStaff> near = new ArrayList<>();
            for (FoundationStaff o : s.level().getEntitiesOfClass(FoundationStaff.class, s.getBoundingBox().inflate(40))) if (o != s) near.add(o);
            if (near.isEmpty()) return null;
            FoundationStaff o = near.get(r.nextInt(near.size()));
            String n = o.getName().getString();
            String b = !o.illness().isEmpty() ? n + " has been coughing all week. " + Scene.cap(o.isFemale() ? "she" : "he") + " says it's nothing. It's never nothing here."
                    : switch (Personality.of(o)) {
                case ODD -> n + " talks to the vending machine. I'd worry, except I think it talks back.";
                case CRABBY -> n + " told the new hire to 'stop breathing so loud'. Classic.";
                case PLAYFUL -> n + " replaced every mug in the break room with SCP-294 printouts. We had to drink out of beakers.";
                case UPBEAT -> n + " brought cake in for no reason. Nobody trusts it. We ate it anyway.";
                case ANXIOUS -> n + " checks the containment board every ten minutes. I've started doing it too.";
                default -> n + " covered my shift last week. I owe " + (o.isFemale() ? "her" : "him") + " one.";
            };
            return new Thread("colleague", List.of(b), "Do you know " + n + "?", List.of(rep("A little.", Att.NONE, "mem:col=" + n), rep("Not yet.", Att.NONE, "mem:col=no"), rep("Look out for each other.", Att.CARE, "mem:col=care")));
        }

        private static final String[] HOBBIES = {"chess", "running the perimeter road", "baking", "crosswords", "birdwatching from the roof", "painting", "the guitar", "gardening"};

        static Thread hobby(ServerPlayer p, FoundationStaff s, Random r) {
            String h = HOBBIES[(int) (s.gene(45) * HOBBIES.length)];
            return new Thread("hobby", List.of("I've taken up " + h + ". Well. I've been 'taking it up' for about a year.", "It helps. You need something that isn't the Foundation, or the Foundation becomes everything."),
                    "Do you have anything like that?", List.of(rep("Not anymore. No time.", Att.DUTY, "mem:hobby=none"), rep("I should. Teach me sometime?", Att.CARE, "mem:hobby=teach"), rep("Paperwork counts, right?", Att.JOKE, "mem:hobby=paper")));
        }

        static Thread place(ServerPlayer p, FoundationStaff s, Random r) {
            String pl = Openers.placeLine(Scene.of(p, s));
            if (pl.isEmpty()) return null;
            return new Thread("place", List.of(pl), Talk.night(s.level()) ? "You're up late too. Can't sleep?" : null,
                    List.of(rep("Never can.", Att.GRIM, "mem:sleep=never"), rep("Work doesn't stop.", Att.DUTY, "mem:sleep=work"), rep("I wanted to see how everyone's doing.", Att.CARE, "mem:sleep=care")));
        }

        /** Rumours, from where they stand: below clearance the Council is a myth. */
        static Thread rumour(ServerPlayer p, FoundationStaff s, Random r) {
            Scene.Tier t = Scene.tier(p);
            if (t.ordinal() <= Scene.Tier.JUNIOR.ordinal())
                return new Thread("rumour", List.of("People say there's a council above the directors. Thirteen of them. Nobody's ever met one.", "If they're real, they decide whether we live or die. If they're not, someone's doing a great impression."),
                        "Do you think they exist?", List.of(rep("I'm sure of it.", Att.GRIM, "mem:myth=yes"), rep("It's a story to keep us in line.", Att.DOUBT, "mem:myth=no"), rep("Better not to ask.", Att.DUTY, "mem:myth=hush")));
            if (t == Scene.Tier.STAFF)
                return new Thread("rumour", List.of("Somebody in Records swears the Ethics Committee overruled an Overseer last month. I didn't know anyone could."),
                        "Do you think anyone watches the watchers?", List.of(rep("Someone has to.", Att.HOPE, "mem:ethics=yes"), rep("Nobody. That's the point.", Att.GRIM, "mem:ethics=no")));
            return null;
        }

        static Thread small(ServerPlayer p, FoundationStaff s, Random r) {
            String[][] o = {{"The coffee machine on this floor is either broken or anomalous. It only makes tea now.", "Have you tried it?"},
                    {"I heard SCP-999 got into the canteen again. Morale went up forty per cent. Cleaning went up four hundred.", "Have you ever met it?"},
                    {"They've changed the alarm tone again. It's worse. I think that's on purpose.", null},
                    {Talk.night(s.level()) ? "Night shift. The lights hum differently at night, I swear." : s.level().isRaining() ? "Listen to that rain. Sounds almost normal." : "Good weather up top, apparently. I wouldn't know.", null}};
            String[] c = o[r.nextInt(o.length)];
            return new Thread("small", List.of(c[0]), c[1], List.of(rep("Yes. Once.", Att.NONE, "yes"), rep("Not yet.", Att.NONE, "no"), rep("I'd rather not.", Att.JOKE, "no")));
        }
    }
}
