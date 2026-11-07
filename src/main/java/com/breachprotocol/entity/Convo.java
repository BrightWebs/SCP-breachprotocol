package com.breachprotocol.entity;

import com.breachprotocol.career.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/**
 * The branching conversation with staff. Every topic opens a page of follow-ups; what they say is
 * built from what is actually going on: the site they work at, attacks and the guards stationed
 * there, the Council's decisions (and who voted for what), deaths, illness, the weather, the time,
 * your rank and what you're wearing, and how much they've come to trust you.
 */
final class Convo {
    private Convo() {}

    private static final int TOPIC = 0, DEEP = 0x9FD8FF, SECRET = 0xD8A8FF, BACK = 0x9AA4B2;

    // ================================================================== trust

    private static String tkey(ServerPlayer p) { return "bp_trust_" + p.getUUID(); }

    static int trust(FoundationStaff s, ServerPlayer p) { return s.getPersistentData().getInt(tkey(p)); }

    /** Talking about something new builds trust, once per topic per day. */
    private static void bump(FoundationStaff s, ServerPlayer p, String topic) {
        long day = s.level().getDayTime() / 24000;
        String k = "bp_tt_" + p.getUUID() + "_" + topic;
        if (s.getPersistentData().getLong(k) == day + 1) return;
        s.getPersistentData().putLong(k, day + 1);
        s.getPersistentData().putInt(tkey(p), Math.min(10, trust(s, p) + 1));
    }

    private static String hearts(int t) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 5; i++) b.append(t >= (i + 1) * 2 ? "●" : t >= i * 2 + 1 ? "◐" : "○");
        return b.toString();
    }

    private static String bond(int t) {
        return t >= 9 ? "confidant" : t >= 7 ? "trusted" : t >= 4 ? "friendly" : t >= 2 ? "acquainted" : "stranger";
    }

    static void subtitle(ServerPlayer p, FoundationStaff s) {
        int t = trust(s, p);
        Dialogue.subtitle(p, Talk.staffSubtitle(s) + " · " + hearts(t) + " " + bond(t));
    }

    // ================================================================== context

    private static Sites.Site site(FoundationStaff s) { return Sites.at(s.level(), s.blockPosition()); }

    private static String siteName(FoundationStaff s) {
        Sites.Site x = site(s);
        return x == null ? "this site" : x.name;
    }

    private static String outfit(ServerPlayer p) {
        ItemStack head = p.getItemBySlot(EquipmentSlot.HEAD), chest = p.getItemBySlot(EquipmentSlot.CHEST);
        String a = (BuiltInRegistries.ITEM.getKey(head.getItem()) + " " + BuiltInRegistries.ITEM.getKey(chest.getItem())).toLowerCase(Locale.ROOT);
        if (a.contains("hazmat")) return "hazmat";
        if (a.contains("mtf") || a.contains("tactical") || a.contains("ballistic")) return "mtf";
        if (a.contains("dclass") || a.contains("jumpsuit")) return "dclass";
        if (a.contains("netherite") || a.contains("diamond")) return "armour";
        if (head.isEmpty() && chest.isEmpty()) return "plain";
        return "other";
    }

    private static int armedHere(FoundationStaff s) {
        Sites.Site x = site(s);
        return x == null ? 0 : Forces.data(s.getServer()).station(x.name).members.size();
    }

    private static Sites.Cell nearestCell(FoundationStaff s) {
        Sites.Cell best = null;
        double bd = 64 * 64;
        for (Sites.Cell c : Sites.get(s.getServer()).cells) {
            if (c.entity.isEmpty() || !s.level().dimension().location().toString().equals(c.dim)) continue;
            double d = s.blockPosition().distSqr(new BlockPos((c.x0 + c.x1) / 2, (c.y0 + c.y1) / 2, (c.z0 + c.z1) / 2));
            if (d < bd) { bd = d; best = c; }
        }
        return best;
    }

    private static List<WorldNews.Item> news(ServerPlayer p) {
        List<WorldNews.Item> out = new ArrayList<>();
        for (WorldNews.Item i : WorldNews.recent(p.server, 8)) if (!Talk.mine(i, p)) out.add(i);
        return out;
    }

    private static String colleague(FoundationStaff s) {
        List<String> n = new ArrayList<>();
        for (FoundationStaff o : s.level().getEntitiesOfClass(FoundationStaff.class, s.getBoundingBox().inflate(48))) if (o != s) n.add(o.getName().getString());
        return n.isEmpty() ? "someone in the canteen" : n.get(new Random().nextInt(n.size()));
    }

    // ================================================================== the pages

    static void menu(ServerPlayer p, FoundationStaff s) {
        Dialogue.clearOptions(p);
        subtitle(p, s);
        Talk.opt(p, s, "How are things?", "how", "Ask how they're doing", TOPIC);
        Talk.opt(p, s, "Tell me about yourself", "story", "Their life, one chapter at a time", TOPIC);
        Talk.opt(p, s, "What's the news?", "news", "What people are talking about", TOPIC);
        Talk.opt(p, s, "Your work", "work", "What they're doing and what they need", TOPIC);
        Talk.opt(p, s, "The Council", "council", "Their view of the O5 and their decisions", TOPIC);
        Talk.opt(p, s, "Site security", "safe", "Guards, attacks and threats at " + siteName(s), TOPIC);
        if (trust(s, p) >= 6) Talk.opt(p, s, "Off the record...", "secret", "They trust you enough to tell you what they shouldn't", SECRET);
        Talk.opt(p, s, "Orders", "orders", "Tell them what to do", Talk.ORDER_C);
        Talk.opt(p, s, "Goodbye", "bye", "End the conversation", BACK);
    }

    private static void page(ServerPlayer p, FoundationStaff s, String[][] opts) {
        Dialogue.clearOptions(p);
        subtitle(p, s);
        for (String[] o : opts) Talk.opt(p, s, o[0], o[1], o.length > 2 ? o[2] : "", DEEP);
        Talk.opt(p, s, "Something else", "menu", "Other topics", BACK);
        Talk.opt(p, s, "Goodbye", "bye", "End the conversation", BACK);
    }

    /** Handles a choice. Returns false if it isn't one of the conversation pages. */
    static boolean handle(ServerPlayer p, FoundationStaff s, String what) {
        Random r = new Random();
        Talk.Temper t = Talk.temper(s);
        String a = Career.address(p);
        int trust = trust(s, p);
        switch (what) {
            case "menu" -> { menu(p, s); return true; }

            // ------------------------------------------------------------ how are things
            case "how" -> {
                Talk.you(p, "How are things?");
                String o = outfit(p);
                String extra = switch (o) {
                    case "hazmat" -> r.nextInt(3) == 0 ? " ...Why are you in a hazmat suit? Should I be worried?" : "";
                    case "mtf" -> r.nextInt(3) == 0 ? " Nice kit. Task force issue?" : "";
                    case "dclass" -> Career.rank(p).atLeast(Rank.LEVEL_3) && r.nextInt(2) == 0 ? " Also, you know you're wearing a D-Class jumpsuit, right?" : "";
                    default -> "";
                };
                Talk.say(p, s, Talk.howAreThings(s, p) + extra);
                bump(s, p, "how");
                int mood = WorldNews.morale(p.server) + Career.data(p).stability / 20 - 2;
                page(p, s, new String[][]{
                        {mood >= 0 ? "Why so upbeat?" : "What's got everyone on edge?", "how_why", "The reason behind the mood"},
                        {"Is there anything you need?", "how_need", "Ask what would help them"},
                        {"How are you holding up, really?", "how_real", "Ask them honestly"}});
                return true;
            }
            case "how_why" -> {
                Talk.you(p, "What's behind the mood around here?");
                List<WorldNews.Item> n = news(p);
                WorldNews.Item worst = null, best = null;
                for (WorldNews.Item i : n) { if (worst == null || i.mood() < worst.mood()) worst = i; if (best == null || i.mood() > best.mood()) best = i; }
                Defence.Alert al = site(s) == null ? null : Defence.alertAt(site(s).name);
                String line;
                if (al != null) line = "Are you joking? " + al.org + " are on the grounds right now. " + (al.kills > 0 ? "We've dropped " + al.kills + " of them, but they keep coming." : "Listen. You can hear the shooting.");
                else if (worst != null && worst.mood() < 0 && (best == null || -worst.mood() >= best.mood()))
                    line = "Mostly that " + worst.headline() + ". " + WorldNews.ago(p.server, worst) + ", and nobody's stopped talking about it. " + Talk.opinion(t, worst.mood(), r);
                else if (best != null && best.mood() > 0)
                    line = "Honestly? Because " + best.headline() + ". It's not often the good news outweighs the bad here.";
                else line = Talk.pick(r, "Nothing in particular. That's the strange part.", "It's the waiting. Nothing's happened, and everyone's waiting for it to.");
                Talk.say(p, s, line);
                bump(s, p, "how_why");
                page(p, s, new String[][]{{"Is the site safe?", "safe", "Guards and threats"}, {"What do you make of it?", "news_think", "Their opinion"}});
                return true;
            }
            case "how_need" -> {
                Talk.you(p, "Is there anything you need?");
                String line = switch (s.role()) {
                    case GUARD -> armedHere(s) < 6 ? "More people. There are " + armedHere(s) + " of us armed at " + siteName(s) + ". That's not a defence, that's a guest list." : "A night's sleep. We've got the numbers. It's the hours that hurt.";
                    case MEDIC -> sickNear(s) > 0 ? sickNear(s) + " of our people are ill right now. If you can get SCP-500 down here, I'll put it to use." : "Supplies are fine. Keep it that way: don't get shot.";
                    case RESEARCHER -> "Test time with something that isn't trying to kill me. And another D-Class rotation, if the Council will sign it.";
                    case TECHNICIAN -> "Spare outlets. Every time someone builds a new wing, they forget it needs power.";
                    case CLERK -> "For people to file their incident reports on time. I know. I'm dreaming.";
                    case JANITOR -> "A mop that doesn't dissolve. Last one went into the HCZ and came back as a stick.";
                };
                Talk.say(p, s, line);
                bump(s, p, "how_need");
                Dialogue.clearOptions(p);
                subtitle(p, s);
                Talk.opt(p, s, "I'll see to it.", "promise", "Promise to help (they'll remember)", DEEP);
                Talk.opt(p, s, "That's not my problem.", "dismiss", "Brush it off", 0xFF9A9A);
                Talk.opt(p, s, "Something else", "menu", "Other topics", BACK);
                return true;
            }
            case "promise" -> {
                Talk.you(p, "I'll see to it.");
                s.getPersistentData().putInt(tkey(p), Math.min(10, trust + 1));
                if (s.role() == FoundationStaff.Role.GUARD && Talk.topBrass(p)) {
                    Sites.Site x = site(s);
                    if (x != null) {
                        Forces.order(p.server, x.name, Forces.Unit.SECURITY, Math.max(0, 6 - armedHere(s)));
                        Talk.say(p, s, "...You mean it. I can see it on the board already: reinforcements on their way. Thank you, Administrator.");
                    } else Talk.say(p, s, "Thank you. People promise. You actually look like you mean it.");
                } else Talk.say(p, s, Talk.pick(r, "Thank you. Really.", "I'll hold you to that.", "People promise. You actually look like you mean it."));
                menu(p, s);
                return true;
            }
            case "dismiss" -> {
                Talk.you(p, "That's not my problem.");
                s.getPersistentData().putInt(tkey(p), Math.max(0, trust - 2));
                Talk.say(p, s, switch (t) {
                    case BOLD -> "Fine. I'll manage. I always do.";
                    case CAREFUL -> "...Understood. Sorry to bother you.";
                    case CYNICAL -> "Of course it isn't. It never is.";
                    case EARNEST -> "Oh. Right. I just thought... never mind.";
                });
                menu(p, s);
                return true;
            }
            case "how_real" -> {
                Talk.you(p, "How are you holding up, really?");
                String line;
                if (!s.illness().isEmpty()) line = "Not well, if I'm honest. I've had " + s.illness() + " for a while now. I keep working. What else is there?";
                else if (s.elder()) line = "I'm " + s.age() + ". Most people I started with are names on a wall now. I'm still here. I'm not sure if that's luck.";
                else if (trust < 3) line = Talk.pick(r, "Fine. Really.", "I'm alright. Thanks for asking. People don't.");
                else line = switch (t) {
                    case BOLD -> "Tired. Don't tell the others. Someone has to look like they've got it together.";
                    case CAREFUL -> "I count the exits in every room. Every room. Even the canteen. That's how I'm holding up.";
                    case CYNICAL -> "I've stopped telling my family when I'll be home. It's kinder.";
                    case EARNEST -> "Some days are hard. But then someone like you stops and asks, and it's a little easier.";
                };
                Talk.say(p, s, line);
                bump(s, p, "how_real");
                page(p, s, new String[][]{{"Tell me about yourself", "story", "Their life"}});
                return true;
            }

            // ------------------------------------------------------------ their life
            case "story" -> {
                Talk.you(p, "Tell me about yourself.");
                Talk.say(p, s, Talk.story(s, s.role(), p));
                bump(s, p, "story");
                page(p, s, new String[][]{{"And then what happened?", "story_more", "The next chapter"},
                        {"How long have you been here?", "story_years", "Their years with the Foundation"},
                        {"Do you have family?", "story_family", "Life outside the walls"}});
                return true;
            }
            case "story_more" -> {
                Talk.you(p, "And then what happened?");
                Talk.say(p, s, Talk.story(s, s.role(), p));
                page(p, s, new String[][]{{"And then?", "story_more", "Keep listening"}, {"Do you have family?", "story_family", "Life outside"}});
                return true;
            }
            case "story_years" -> {
                Talk.you(p, "How long have you been with the Foundation?");
                int y = Math.min(Talk.years(s), Math.max(1, s.age() - 21));
                Talk.say(p, s, y + " year" + (y == 1 ? "" : "s") + ". " + (y > 12 ? "Long enough to remember when " + siteName(s) + " was half the size." : y > 5 ? "Long enough to stop flinching at the alarms. Mostly." : "Not long. Still learning which doors not to open."));
                page(p, s, new String[][]{{"And before that?", "story", "Their life before"}});
                return true;
            }
            case "story_family" -> {
                Talk.you(p, "Do you have family?");
                String line = trust < 2 ? "That's... personal. Ask me again when we know each other better."
                        : switch (Math.floorMod(s.getUUID().hashCode(), 5)) {
                    case 0 -> "A daughter. She's nine. She thinks I test cosmetics. I've never been so glad to be called boring.";
                    case 1 -> "My parents. They're old now. I visit when I can, and I don't tell them anything, and they don't ask.";
                    case 2 -> "I had a husband. The Foundation has a word for what happened. I don't use it.";
                    case 3 -> "No. This place is the family. That's either very sad or very Foundation. Probably both.";
                    default -> "A brother. He works in insurance. We compare horror stories at Christmas. He thinks he wins.";
                };
                Talk.say(p, s, line);
                bump(s, p, "story_family");
                page(p, s, new String[][]{{"How are you holding up, really?", "how_real", "Ask them honestly"}});
                return true;
            }

            // ------------------------------------------------------------ the news
            case "news", "news_next" -> {
                Talk.you(p, what.equals("news") ? "What's the news?" : "What else is going on?");
                List<WorldNews.Item> n = news(p);
                String k = "bp_newsi_" + p.getUUID();
                int i = what.equals("news") ? 0 : s.getPersistentData().getInt(k) + 1;
                if (n.isEmpty()) { Talk.say(p, s, Talk.pick(r, "Nothing worth repeating. Around here that's good news.", "Quiet. Don't jinx it.")); menu(p, s); return true; }
                if (i >= n.size()) { Talk.say(p, s, "That's everything I've heard. You know as much as I do now. Well. Almost."); i = 0; s.getPersistentData().putInt(k, -1); page(p, s, new String[][]{}); return true; }
                s.getPersistentData().putInt(k, i);
                WorldNews.Item it = n.get(i);
                Talk.say(p, s, Talk.pick(r, "Did you hear? ", "Word is ", "So, ") + it.headline() + ". That was " + WorldNews.ago(p.server, it) + ".");
                bump(s, p, "news");
                page(p, s, new String[][]{{"What do you think about it?", "news_think", "Their opinion"}, {"Who told you that?", "news_who", "Where they heard it"},
                        {"Tell me more", "news_more", "The details"}, {"What else is going on?", "news_next", "The next story"}});
                return true;
            }
            case "news_think", "news_more", "news_who" -> {
                List<WorldNews.Item> n = news(p);
                int i = Math.max(0, s.getPersistentData().getInt("bp_newsi_" + p.getUUID()));
                WorldNews.Item it = n.isEmpty() ? null : n.get(Math.min(i, n.size() - 1));
                if (it == null) { Talk.say(p, s, "About what? Nothing's happened."); menu(p, s); return true; }
                switch (what) {
                    case "news_think" -> { Talk.you(p, "What do you think about it?"); Talk.say(p, s, Talk.opinion(t, it.mood(), r) + " " + feeling(t, it, r)); }
                    case "news_who" -> { Talk.you(p, "Who told you that?"); Talk.say(p, s, trust >= 3 ? "I heard it from " + colleague(s) + ". Don't say I said so." : "People talk. I listen. That's all I'll say."); }
                    default -> { Talk.you(p, "Tell me more."); Talk.say(p, s, detail(p, it, r)); }
                }
                page(p, s, new String[][]{{"What else is going on?", "news_next", "The next story"}, {"What do you think about it?", "news_think", "Their opinion"}});
                return true;
            }

            // ------------------------------------------------------------ work
            case "work" -> {
                Talk.you(p, "What are you working on?");
                Talk.say(p, s, work(p, s, r));
                bump(s, p, "work");
                String[] job = switch (s.role()) {
                    case MEDIC -> new String[]{"Patch me up", "job", "Heal your wounds"};
                    case CLERK -> new String[]{"Any assignments for me?", "job", "Your open assignments"};
                    case RESEARCHER -> new String[]{"Look at what I'm holding", "job", "Ask about the item in your hand"};
                    case TECHNICIAN -> new String[]{"Check the power for me", "job", "A report on the site's power"};
                    case GUARD -> new String[]{"How's the perimeter?", "safe", "Guards and threats"};
                    case JANITOR -> new String[]{"Clean this up", "job", "Sweeps up dropped items nearby"};
                };
                page(p, s, new String[][]{{"Any problems?", "work_problems", "What's getting in their way"}, job});
                return true;
            }
            case "work_problems" -> {
                Talk.you(p, "Any problems?");
                Sites.Site x = site(s);
                Forces.Station st = x == null ? null : Forces.data(p.server).station(x.name);
                String line;
                if (st != null && !st.losses.isEmpty() && r.nextBoolean()) line = "We lost " + st.losses.peekFirst() + ". People are shaken. They act like they aren't.";
                else if (Career.data(p).stability < 45) line = "Stability's at " + Career.data(p).stability + "%. Everything's a problem when the numbers are that low.";
                else if (Talk.night(s.level())) line = "Night shift. Half the lights on this floor flicker, and the other half are worse.";
                else line = Talk.pick(r, "Nothing I can't handle.", "Paperwork. Always paperwork.", "Nothing new. Which means something is about to be.");
                Talk.say(p, s, line);
                page(p, s, new String[][]{{"Is there anything you need?", "how_need", "Ask what would help"}});
                return true;
            }

            // ------------------------------------------------------------ the Council
            case "council" -> {
                Talk.you(p, "What do you make of the Council?");
                Talk.say(p, s, Talk.councilView(s, p));
                bump(s, p, "council");
                page(p, s, new String[][]{{"Do you agree with them?", "council_agree", "Their honest view"}, {"What would you have done?", "council_instead", "The road not taken"},
                        {"Do you trust the O5?", "council_trust", "Faith in the Council"}, {"And the Administrator?", "council_admin", "What they think of the Administrator"}});
                return true;
            }
            case "council_agree" -> {
                Talk.you(p, "Do you agree with them?");
                WorldNews.Item d = WorldNews.latest(p.server, WorldNews.Kind.DECISION);
                Talk.say(p, s, d == null ? "They haven't decided anything I'd have an opinion on." : (d.mood() >= 0 == (t != Talk.Temper.CYNICAL) ? "Yes. " : "No. ") + Talk.opinion(t, d.mood(), r)
                        + (d.supporters().isEmpty() ? "" : " I heard it was O5-" + d.supporters().iterator().next() + " who pushed for it."));
                page(p, s, new String[][]{{"What would you have done?", "council_instead", "The road not taken"}});
                return true;
            }
            case "council_instead" -> {
                Talk.you(p, "What would you have done?");
                WorldNews.Item d = WorldNews.latest(p.server, WorldNews.Kind.DECISION);
                String line = d == null ? "Nothing. That's usually the wisest thing anyone does." : switch (t) {
                    case BOLD -> "Gone further. Half measures get people killed.";
                    case CAREFUL -> "Waited. Gathered more data. You can't take back a decision like that.";
                    case CYNICAL -> "Whatever was cheapest. That's what they'd have done eventually anyway.";
                    case EARNEST -> "Asked the people who have to live with it. Us.";
                };
                Talk.say(p, s, line);
                page(p, s, new String[][]{{"Do you trust the O5?", "council_trust", "Faith in the Council"}});
                return true;
            }
            case "council_trust" -> {
                Talk.you(p, "Do you trust the O5?");
                Talk.say(p, s, Career.rank(p).atLeast(Rank.OVERSEER) && trust < 6 ? "I trust the Council completely, " + a + ". Of course." : switch (t) {
                    case BOLD -> "I trust them to make the hard calls. Somebody has to.";
                    case CAREFUL -> "I trust them the way you trust a bridge. You don't look down.";
                    case CYNICAL -> "Thirteen people nobody's ever met deciding who lives. What's not to trust?";
                    case EARNEST -> "I have to. If they're not doing the right thing, then what are we doing here?";
                });
                page(p, s, new String[][]{{"And the Administrator?", "council_admin", "What they think of the Administrator"}});
                return true;
            }
            case "council_admin" -> {
                Talk.you(p, "And the Administrator?");
                Talk.say(p, s, Talk.topBrass(p) ? (trust >= 5 ? "You want the truth? People are afraid of you. I was. Then you stopped and talked to me. I'm less afraid now." : "You're the Administrator. Whatever you decide, we carry out. That's how it works.")
                        : Talk.pick(r, "Nobody's sure the Administrator is one person. Some say they've seen them. I'd rather not.", "If the Administrator is real, I hope they're kind. That's all."));
                menu(p, s);
                return true;
            }

            // ------------------------------------------------------------ security
            case "safe" -> {
                Talk.you(p, "Is the site safe?");
                Sites.Site x = site(s);
                Defence.Alert al = x == null ? null : Defence.alertAt(x.name);
                int armed = armedHere(s);
                String line;
                if (al != null) line = "No! " + al.org + " are inside the perimeter: " + al.hostiles + " of them last count. Security's engaging. Stay behind me.";
                else if (x == null) line = "We're not on site ground here. Out here, nobody's watching your back.";
                else {
                    WorldNews.Item atk = WorldNews.latest(p.server, WorldNews.Kind.ATTACK);
                    line = armed + " armed personnel at " + x.name + ". " + (armed >= 10 ? "That's a proper garrison." : armed >= 4 ? "Enough to hold the doors." : "Not enough, if you ask me.")
                            + (atk != null && p.server.overworld().getGameTime() - atk.time() < 72000 ? " And " + atk.headline() + " " + WorldNews.ago(p.server, atk) + ", so everyone's jumpy." : "")
                            + (Talk.night(s.level()) ? " Creepers come out at night. The guards shoot them on sight." : "");
                }
                Talk.say(p, s, line);
                bump(s, p, "safe");
                page(p, s, new String[][]{{"Who are we worried about?", "safe_who", "The organisations at our door"}});
                return true;
            }
            case "safe_who" -> {
                Talk.you(p, "Who are we worried about?");
                StringBuilder b = new StringBuilder();
                for (int i = 0; i < Defence.ORGS.length; i++)
                    if (!Defence.invited(p.server, Defence.ORGS[i])) b.append(b.length() == 0 ? "" : ", ").append(Defence.ORG_NAMES[i]);
                Talk.say(p, s, b.length() == 0 ? "Officially? Nobody. Every organisation out there has been invited onto site ground. I hope the Administrator knows what they're doing."
                        : b + ". Any of them sets foot on site ground uninvited, they don't leave. Those are the orders.");
                menu(p, s);
                return true;
            }

            // ------------------------------------------------------------ off the record
            case "secret" -> {
                Talk.you(p, "Off the record?");
                if (trust < 6) { Talk.say(p, s, "I don't know you well enough for that."); menu(p, s); return true; }
                Talk.say(p, s, secret(p, s, r));
                bump(s, p, "secret");
                menu(p, s);
                return true;
            }
            default -> { return false; }
        }
    }

    private static int sickNear(FoundationStaff s) {
        int n = 0;
        for (FoundationStaff o : s.level().getEntitiesOfClass(FoundationStaff.class, s.getBoundingBox().inflate(64))) if (!o.illness().isEmpty()) n++;
        return n;
    }

    private static String feeling(Talk.Temper t, WorldNews.Item it, Random r) {
        return switch (it.kind()) {
            case DEATH -> "I knew them a little. Everyone knew everyone a little.";
            case ATTACK -> it.mood() >= 0 ? "Good. Let them come back and try again." : "They got too close this time.";
            case BREACH -> "Every breach is someone's mistake. Usually someone already dead.";
            case FORCES -> "More guns on site. I won't complain.";
            case HIRE -> "New faces. I give them a month before they stop smiling.";
            default -> "";
        };
    }

    private static String detail(ServerPlayer p, WorldNews.Item it, Random r) {
        String who = it.who().isEmpty() ? "" : " It came from " + it.who() + ".";
        String sup = it.supporters().isEmpty() ? "" : " The ones who backed it: " + it.supporters().stream().map(x -> "O5-" + x).reduce((a, b) -> a + ", " + b).orElse("") + ".";
        return "It was " + WorldNews.ago(p.server, it) + ". " + (it.mood() > 2 ? "It's done a lot of good." : it.mood() < -2 ? "It cost us." : "Hard to say yet if it mattered.") + who + sup;
    }

    private static String work(ServerPlayer p, FoundationStaff s, Random r) {
        return switch (s.role()) {
            case RESEARCHER -> {
                Sites.Cell c = nearestCell(s);
                yield c == null ? "Writing up old tests. Nothing in containment near here to work on."
                        : "Observation on the " + c.name + ". " + Talk.pick(r, "It hasn't moved in three days. That's what worries me.", "Its readings changed this morning. Nobody else noticed.", "Routine. I like routine. Routine means it's still in there.");
            }
            case GUARD -> {
                Defence.Alert al = site(s) == null ? null : Defence.alertAt(site(s).name);
                yield al != null ? "Holding the line against " + al.org + ". You should be inside." : "Perimeter rounds. " + (s.unit() != null ? "I'm with " + s.unit().title + ". " : "") + Talk.pick(r, "Creepers are the worst of it, most nights.", "Quiet so far. Quiet is good.", "We shoot first out here. Questions are for the researchers.");
            }
            case MEDIC -> sickNear(s) > 0 ? sickNear(s) + " patients, at the moment. I'm doing what I can without SCP-500." : "Inventory, and keeping the infirmary ready. Ready is most of the job.";
            case TECHNICIAN -> "Keeping the lights on. Literally. The containment doors don't open without power, and they don't close without it either.";
            case CLERK -> "Assignments, transfers, and " + Talk.pick(r, "eleven incident reports nobody signed.", "the personnel files for the new hires.", "the casualty paperwork. There's always casualty paperwork.");
            case JANITOR -> "Cleaning. You'd be amazed what ends up on the floor around here. Some of it's still moving.";
        };
    }

    private static String secret(ServerPlayer p, FoundationStaff s, Random r) {
        WorldNews.Item d = WorldNews.latest(p.server, WorldNews.Kind.DECISION);
        List<String> pool = new ArrayList<>();
        if (d != null && !d.supporters().isEmpty()) pool.add("The vote on whether " + d.headline() + "? It wasn't close. O5-" + d.supporters().iterator().next() + " argued for it like their life depended on it. Maybe it did.");
        pool.add("There's a room on the lowest basement level that isn't on any plan. I've seen the door. I've never seen it open.");
        pool.add("Some of the D-Class volunteered. Think about what that means about where they came from.");
        pool.add("I've seen O5 members walk past and not age a day in ten years. They say it's the Fountain. SCP-006. I believe it.");
        pool.add("One of the guards swears the Gate Guardian looked at them. Not at the door. At them.");
        if (Talk.topBrass(p)) pool.add("People say you've been past the Gate. That you've seen the Garden. If that's true... was it beautiful?");
        return pool.get(r.nextInt(pool.size()));
    }
}
