package com.breachprotocol.entity;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.career.*;
import com.breachprotocol.compat.Unleashed;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.*;

/**
 * Talking to the people of the Foundation.
 *
 * <p>Every staff member is a person: a name, a department, years of service, a temperament (bold,
 * careful, cynical or earnest) and a life story told a piece at a time. What they say depends on who
 * you are (your rank, whether you're armed, hurt, or something more than human), where they are and
 * what is happening around them (an anomaly loose nearby, night, rain), and on the world's news: every
 * decision of the Council and on the phone, breaches, rifts, the Garden, promotions. They remember the
 * news and have opinions about it. The Overseers remember how they voted.
 *
 * <p>Right-click a staff member or an Overseer to talk. You can also give orders, if your rank allows:
 * follow me, wait here, back to your post, take a seat, and each department's own work.
 */
@EventBusSubscriber(modid = BreachProtocol.MODID)
public final class Talk {
    private Talk() {}

    // ================================================================== who they are

    public enum Temper { BOLD, CAREFUL, CYNICAL, EARNEST }

    public static Temper temper(Entity e) {
        if (e instanceof FoundationStaff fs) return Personality.of(fs).temper;
        return Temper.values()[Math.floorMod(e.getUUID().getLeastSignificantBits(), 4)];
    }

    public static int years(Entity e) { return 2 + (int) Math.floorMod(e.getUUID().getMostSignificantBits(), 19L); }

    private static int storyIndex(Entity e, int n) { return (int) Math.floorMod(e.getUUID().getMostSignificantBits() >>> 7, (long) n); }

    /** Life stories, three chapters each, told one at a time to each person who asks. */
    private static final Map<FoundationStaff.Role, String[][]> STORIES = new EnumMap<>(FoundationStaff.Role.class);

    static {
        STORIES.put(FoundationStaff.Role.RESEARCHER, new String[][]{
                {"I was a marine biologist. Then a fishing trawler pulled up something that sang, and two men in suits offered me a job.",
                        "My first assignment was SCP-999. Everyone's first assignment is 999. They want you to like it here before they show you the rest.",
                        "I write up every test as if someone will read it in a hundred years. Someone might. I'd like them to know we were careful."},
                {"Physics. I was going to win a Nobel. Instead I signed a non-disclosure agreement that would make a lawyer cry.",
                        "The worst day was when the numbers stopped making sense. Not wrong. Just... a different kind of right.",
                        "I keep a photo of my sister in my coat. She thinks I work for a pharmaceutical company. In a way I do."},
                {"I came in as a D-Class. Clerical error. By the time they found it I'd written three papers on SCP-173's movement patterns.",
                        "They gave me a lab coat instead of a jumpsuit. Same people, different side of the glass.",
                        "I still blink twice before I open any door. Old habits keep you alive."},
                {"Linguistics. The Foundation needed someone to translate a book that rewrites itself when you look away.",
                        "I'm on chapter forty. It's been on chapter forty for six years.",
                        "Some nights I think the book is translating me. Don't put that in my file."}});
        STORIES.put(FoundationStaff.Role.GUARD, new String[][]{
                {"Twelve years in the army. Thought I'd seen everything. First night here I saw a man walk through a wall. Backwards.",
                        "You learn the rules fast. Never look at 096. Never turn your back on 173. Never ask what's behind the blue door.",
                        "I've got a daughter. She thinks I guard a bank. I tell her the vault is very, very important."},
                {"I applied to be a mall cop. Wrong building. They kept me anyway.",
                        "Turns out I'm good under pressure. Turns out pressure is a lot of what we do.",
                        "When the alarms go, everyone runs one way. We run the other. Somebody has to."},
                {"Ex-police. Got too close to a case with a smiling man who didn't cast a shadow. The Foundation found me before he did.",
                        "I've done four breaches. Lost friends in two. You don't forget the names. You shouldn't.",
                        "I volunteer for the night shift. Quieter. Mostly."},
                {"My father was security here too. He never talked about it. Now I understand why.",
                        "I found his badge number on a memorial wall on B3. Nobody told me there was a wall.",
                        "I touch it every time I go down there. For luck. His and mine."}});
        STORIES.put(FoundationStaff.Role.MEDIC, new String[][]{
                {"Trauma surgeon. A hospital I worked at got a patient whose blood ran backwards. The Foundation took him. And me.",
                        "Most of what I treat here is ordinary. Broken bones, burns, shock. It's what causes them that isn't.",
                        "I keep SCP-500 counted down to the pill. Every one I hand out is a life I don't get to save later."},
                {"Field medic, three tours. I don't flinch. That's why they hired me.",
                        "I flinched once. SCP-049 called me 'colleague'. I still don't know what he meant.",
                        "Wash your hands. I'm serious. Some of what's down here spreads."},
                {"I was a vet. Animals. The Foundation needed someone for the things that are almost animals.",
                        "SCP-529 lets me check her heartbeat. She has one. Just the one half of her does.",
                        "I talk to every patient like they can hear me. Some of them can hear a great deal more."},
                {"Nursing school, then a hospice. I'm good with endings. That's not a joke.",
                        "Here, endings aren't always endings. That took some getting used to.",
                        "If you ever come through my door, I'll do everything I can. Everything."}});
        STORIES.put(FoundationStaff.Role.TECHNICIAN, new String[][]{
                {"Power engineer. I keep the lights on. Down here, the lights are containment.",
                        "SCP-106 doesn't like strong light, and 173 doesn't like being watched. Power cuts are how sites die.",
                        "The creative supply in the plant? Don't ask me how it works. I just know it doesn't stop."},
                {"I fixed elevators. One day an elevator took me somewhere it shouldn't. The Foundation was waiting at the bottom.",
                        "Now I fix everything. Doors, scanners, the satellite terminal. I've rewired a door that screamed.",
                        "Tip: if a scanner won't take your card, check which side of the jamb it's on. The jamb carries the signal."},
                {"Network admin. Ever met code that writes back? I have.",
                        "The server room is where I'm happiest. Cold, quiet, and nothing in there is alive. I check.",
                        "If the network ever asks you a question, don't answer. Call me."},
                {"Rocket engineer, believe it or not. The launch deck on the roof is mine.",
                        "We put a weapon in orbit. I helped. Some nights I watch the satellite go over and wonder who it's for.",
                        "Keep the pads fuelled. The day you need them, you'll need them now."}});
        STORIES.put(FoundationStaff.Role.CLERK, new String[][]{
                {"I process paperwork. Termination orders, transfer orders, requisitions for a thousand litres of hydrochloric acid.",
                        "I've signed for things that don't have names. I just write 'item' and the number.",
                        "Everything here has a file. Even you. Especially you."},
                {"I used to be an accountant. The Foundation's books are... creative. Things go in and don't come out.",
                        "Did you know we pay rent on a building that doesn't exist? Every month. On time.",
                        "If the numbers ever balance, the Accountant gets nervous. So do I."},
                {"Records. I read everything before it's sealed. You learn what not to remember.",
                        "Amnestics are in the drawer under mine. I've never needed one. I think.",
                        "If I ever start forgetting your name, tell someone."},
                {"Reception. I'm the first face you see and the last one you'd suspect.",
                        "I've turned away two Insurgency agents and a man who was mostly bees.",
                        "Smile, sign the visitor log, and never let anyone hold the door for you."}});
        STORIES.put(FoundationStaff.Role.JANITOR, new String[][]{
                {"Twenty-two years cleaning this place. Nobody notices the janitor. That's the point.",
                        "I've mopped up things that moved. I've mopped up things that mopped back.",
                        "Best advice I can give you: if a stain looks like a face, don't scrub it."},
                {"I was D-Class. Did my month. They offered me this or the other option. I like this better.",
                        "I know every vent in this building. I know which ones hum.",
                        "Leave the B3 bathrooms alone. Just trust me."},
                {"My grandmother cleaned the first site the Foundation ever built. She told me stories I thought were stories.",
                        "They weren't. I've met two of them.",
                        "I keep her keyring. It opens doors that aren't on any plan."},
                {"I'm saving up for a little place by the sea. No anomalies. Just water.",
                        "Then again, the sea is where half of them come from.",
                        "Maybe a little place by a lake."}});
        STORIES.put(FoundationStaff.Role.SECRETARY, new String[][]{
                {"I was a legal secretary in Geneva. A client of ours had a briefcase nobody was allowed to open. I opened it.",
                        "It was empty. Two days later, two very polite men offered me a job. I've wondered ever since what I didn't see.",
                        "Now I keep the Administrator's diary. Some days I'm the only person on Earth who knows where you'll be."},
                {"I applied for an ordinary office job. The interview was in a room with no doors. I still don't know how I got in.",
                        "They said I was the first applicant who didn't panic. I said I'd worked in a hospital at night. That was true.",
                        "I file the Council's correspondence. You learn a great deal about thirteen people from how they sign their letters."},
                {"My mother was a Foundation translator. She never said so. I found her badge after the funeral.",
                        "I asked to be posted wherever she was. They gave me her old desk. Her coffee ring is still on it.",
                        "I don't think she'd approve of everything I see here. I think she'd approve of the people."}});
    }

    // ================================================================== reading the moment

    static boolean armed(Player p) {
        ItemStack s = p.getMainHandItem();
        if (s.isEmpty()) return false;
        String id = BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
        return id.contains("sword") || id.contains("axe") || id.contains("rifle") || id.contains("pistol") || id.contains("shotgun") || id.contains("gun")
                || id.contains("bow") || id.contains("grenade") || id.contains("trident") || id.contains("spear");
    }

    static Mob anomalyNear(Entity e, double r) {
        for (Mob m : e.level().getEntitiesOfClass(Mob.class, e.getBoundingBox().inflate(r), Unleashed::isAnomaly))
            if (!Unleashed.isContained(m)) return m;
        return null;
    }

    static boolean night(Level l) { long t = l.getDayTime() % 24000; return t > 13000 && t < 23000; }

    static String pick(Random r, String... s) { return s[r.nextInt(s.length)]; }

    /** How they're doing, given everything. */
    public static String howAreThings(FoundationStaff s, ServerPlayer p) {
        Random r = new Random();
        Mob loose = anomalyNear(s, 24);
        MinecraftServer srv = p.server;
        int morale = WorldNews.morale(srv) + Career.data(p).stability / 20 - 2;
        Temper t = temper(s);
        if (loose != null) {
            String what = Unleashed.designation(loose);
            return switch (s.role()) {
                case GUARD -> pick(r, what + " is out. Stay behind me.", "We've got " + what + " loose on this floor. Weapons free.", "Eyes on " + what + ". Nobody moves until it's contained.");
                case MEDIC -> pick(r, "Is that " + what + "? Get everyone behind the doors. I'll handle the wounded.", "I need you to keep " + what + " away from my patients.");
                default -> pick(r, "Is that " + what + "?! Why is it out?", "I'm not paid enough to be this close to " + what + ".", "Please tell me someone's coming for " + what + ".");
            };
        }
        if (Career.isAscended(p)) return pick(r, "I'm... fine. I'm fine. Please don't look at me like that.", "Everything is very good. Is that what you want to hear?");
        if (!s.illness().isEmpty()) return pick(r, "Honestly? I've got " + s.illness() + ". Medical says it'll pass. Medical says a lot of things.",
                "Not well. " + Character.toUpperCase(s.illness().charAt(0)) + s.illness().substring(1) + ". If you've got any SCP-500 spare...",
                "I've had " + s.illness() + " for days. Still came in. Somebody has to.");
        if (s.age() >= 80) return pick(r, "At my age every morning is a small anomaly.", "I'm " + s.age() + ". I've outlived three site directors and a Keter.",
                "The Fountain? I've thought about it. I'm not sure I want to be young again.");
        if (s.age() >= 65 && r.nextInt(3) == 0) return pick(r, "My knees know when a breach is coming. They're quiet today.", "Retirement? Nobody retires from the Foundation. We just slow down.",
                "I remember when this site was a hole in the ground.");
        if (p.getHealth() < 6) return s.role() == FoundationStaff.Role.MEDIC ? "Never mind me, look at you. Hold still." : "You're bleeding. Medical's on the second floor.";
        if (armed(p) && Career.rank(p).ordinal() < Rank.LEVEL_3.ordinal()) return "Why are you armed? Put that away before security sees it.";
        WorldNews.Item last = WorldNews.latest(srv);
        String news = last != null && srv.overworld().getGameTime() - last.time() < 24000 ? last.headline() : null;
        if (news != null && mine(last, p)) {
            if (r.nextBoolean()) return toYou(last, p, t, r);
            news = null;
        }
        if (news != null && r.nextBoolean()) {
            return (morale >= 0 ? pick(r, "Better, honestly. ", "Not bad. ") : pick(r, "Tense. ", "Honestly? Rattled. "))
                    + "Everyone's talking about how " + news + " " + WorldNews.ago(srv, last) + ".";
        }
        String pl = Openers.placeLine(Scene.of(p, s));
        if (!pl.isEmpty() && r.nextInt(5) < 2) return pl;
        if (night(s.level())) return pick(r, "Long shift. They always feel longer at night.", "Tired. Nights are when things move.");
        if (s.level().isRaining()) return pick(r, "Rain on the roof. I like it. It's normal.", "Weather's grim. Good day to stay inside.");
        return switch (t) {
            case BOLD -> morale >= 0 ? pick(r, "Never better. Site's holding.", "Good. We've got this.") : pick(r, "We'll get through it. We always do.", "Bad week. Doesn't matter. Back to work.");
            case CAREFUL -> morale >= 0 ? pick(r, "Quiet. I don't trust quiet, but I'll take it.", "Fine, as long as nothing changes.") : pick(r, "I've checked my exits twice today.", "Not great. I keep watching the doors.");
            case CYNICAL -> morale >= 0 ? pick(r, "Nothing's on fire. Give it an hour.", "Same as always. Which is to say, nobody's dead yet.") : pick(r, "Oh, wonderful. Can't you tell?", "Ask me again after the next breach.");
            case EARNEST -> morale >= 0 ? pick(r, "Good! We did something right this week.", "Hopeful, actually. It feels like we're winning.") : pick(r, "Worried. I just want everyone to go home safe.", "Hard week. But I believe in what we're doing.");
        };
    }

    static boolean topBrass(ServerPlayer p) { return Career.rank(p) == Rank.ADMINISTRATOR || Career.isAscended(p); }

    /** Whether a piece of news is about the person they're talking to (gossip about them stays between staff). */
    static boolean mine(WorldNews.Item i, ServerPlayer p) {
        return i.who().equals(p.getGameProfile().getName()) || topBrass(p) && i.who().contains("Administrator");
    }

    /** What they say to your face about something you did. */
    static String toYou(WorldNews.Item i, ServerPlayer p, Temper t, Random r) {
        String a = topBrass(p) ? "Administrator" : Career.address(p);
        return switch (i.kind()) {
            case SHYGUY_KILLED -> pick(r, "The containment team for 096 asked me to pass on their thanks, " + a + ".", "096's cell is empty for the first time in decades. Thank you, " + a + ".");
            case SHYGUY_CALMED -> pick(r, "096's handlers say it's never been this calm. Whatever you did, " + a + ", it worked.", "Containment on 096 reports no incidents since your visit, " + a + ".");
            case PROMOTION -> pick(r, "Congratulations on the appointment, " + a + ".", "It's an honour to serve under you, " + a + ".");
            case SEAT_VOTE -> "Welcome to the Council, " + a + ".";
            case KNOWLEDGE -> pick(r, "You've been to the Garden, " + a + ". I won't ask.", "Whatever you learned in the Garden, " + a + ", we're glad you're back.");
            case BANISHED -> "I heard the Gate turned you away. I'm sorry, " + a + ".";
            case TREATMENT -> "Thank you for looking after our people, " + a + ".";
            case DECISION -> i.mood() >= 0 ? pick(r, "Your ruling is already paying off, " + a + ".", "The site's steadier since your decision, " + a + ".")
                    : pick(r, "We're carrying out your ruling, " + a + ". It's been a hard few days, but we'll manage.", "Your orders are being followed, " + a + ".");
            default -> i.mood() >= 0 ? "Thank you, " + a + ". People have noticed." : "It's been a difficult stretch, " + a + ". We're with you.";
        };
    }

    /** Their opinion of a piece of news. */
    static String opinion(Temper t, int mood, Random r) {
        if (mood > 2) return switch (t) {
            case BOLD -> pick(r, "About time.", "That's how you run a Foundation.");
            case CAREFUL -> pick(r, "Good. I'll sleep better.", "Sensible. Very sensible.");
            case CYNICAL -> pick(r, "Something went right. Mark the calendar.", "Fine. I'm sure there's a catch.");
            case EARNEST -> pick(r, "That's the right call. I'm glad.", "That gives me hope.");
        };
        if (mood < -2) return switch (t) {
            case BOLD -> pick(r, "We'll deal with what comes.", "Bad call. We'll make it work anyway.");
            case CAREFUL -> pick(r, "I don't like it. I don't like it at all.", "Someone should have thought that through.");
            case CYNICAL -> pick(r, "Of course they did.", "Brilliant. Who's cleaning that up? Us.");
            case EARNEST -> pick(r, "I hope they know what they're doing.", "People are going to get hurt.");
        };
        return switch (t) {
            case BOLD -> "We'll see.";
            case CAREFUL -> "Time will tell.";
            case CYNICAL -> "Doesn't change my shift.";
            case EARNEST -> "I trust the Council. Mostly.";
        };
    }

    public static String rumour(Entity s, ServerPlayer p) {
        Random r = new Random();
        List<WorldNews.Item> recent = WorldNews.recent(p.server, 5);
        if (recent.isEmpty()) return pick(r, "Nothing worth repeating. Which around here is good news.", "Quiet week. Don't jinx it.");
        List<WorldNews.Item> others = recent.stream().filter(x -> !mine(x, p)).toList();
        if (others.isEmpty()) return toYou(recent.get(0), p, temper(s), r) + " Other than that, it's been quiet.";
        WorldNews.Item i = others.get(r.nextInt(Math.min(3, others.size())));
        String when = WorldNews.ago(p.server, i);
        String lead = pick(r, "Did you hear? ", "Word is ", "Between us: ", "You didn't hear it from me, but ");
        return lead + i.headline() + " (" + when + "). " + opinion(temper(s), i.mood(), r);
    }

    public static String councilView(Entity s, ServerPlayer p) {
        Random r = new Random();
        WorldNews.Item d = WorldNews.latest(p.server, WorldNews.Kind.DECISION);
        if (d == null) return "The Council hasn't decided anything we've heard about lately. Which means they've decided plenty we haven't.";
        boolean overruled = d.who().contains("over the Council");
        if (topBrass(p) && d.who().contains("Administrator"))
            return "Your decision on " + WorldNews.topic(d) + ", " + WorldNews.ago(p.server, d) + ": " + d.headline() + ". " + (d.mood() >= 0 ? "From where I stand, it was the right one." : "It's been hard on the site, but we'll see it through.");
        String a = "Last I heard, " + d.headline() + ", " + WorldNews.ago(p.server, d) + ". ";
        if (overruled) a += "And the Administrator overruled the lot of them. ";
        return a + opinion(temper(s), d.mood(), r);
    }

    public static String story(Entity s, FoundationStaff.Role role, ServerPlayer p) {
        String[][] pool = STORIES.getOrDefault(role, STORIES.get(FoundationStaff.Role.CLERK));
        String[] st = pool[storyIndex(s, pool.length)];
        String key = "bp_story_" + p.getUUID();
        int ch = s.getPersistentData().getInt(key);
        String line = st[Math.min(ch, st.length - 1)];
        if (ch < st.length) s.getPersistentData().putInt(key, ch + 1);
        if (ch >= st.length) line = "That's about all there is to me. Ask me again in a few years. If there's anything left to tell.";
        return line;
    }

    /** A greeting that knows who you are, what's happening, and what you just did. */
    public static String greeting(FoundationStaff s, ServerPlayer p, String base) {
        WorldNews.Item last = WorldNews.latest(p.server);
        if (last != null && p.server.overworld().getGameTime() - last.time() < 6000 && last.who().equals(p.getGameProfile().getName()) && new Random().nextBoolean()) {
            return base + " " + toYou(last, p, temper(s), new Random());
        }
        return base;
    }

    // ================================================================== the window

    static final int STAFF_C = 0x66CCFF, O5_C = 0xE05050, YOU_C = 0xB8E07A, ORDER_C = 0xFFC94D;

    private static String sess(Entity e) { return "npc:" + e.getId(); }

    static void say(ServerPlayer p, Entity who, String line) {
        // staff only say what your clearance allows (the Overseers themselves speak freely to those they meet)
        if (who instanceof FoundationStaff fs) {
            boolean raw = line.startsWith(Clearance.RAW);
            line = Clearance.filter(p, line);
            if (!raw) {
                Random vr = new Random();
                line = Social.colour(p, fs, line, vr);                       // respect, nerves, grief
                line = Personality.of(fs).voice(line, Career.address(p), vr); // and each in their own voice
            }
        }
        else if (line.startsWith(Clearance.RAW)) line = line.substring(1);
        Dialogue.line(p, who.getName().getString(), line, who instanceof O5MemberEntity ? O5_C : STAFF_C);
        if (who instanceof Mob m) m.getLookControl().setLookAt(p, 30, 30);
    }

    static void you(ServerPlayer p, String line) { Dialogue.line(p, "You", line, YOU_C); }

    static void note(ServerPlayer p, String line) { Dialogue.line(p, "", line, 0); }

    static void opt(ServerPlayer p, Entity e, String label, String what, String hover, int c) {
        Dialogue.option(p, label, "/talk " + e.getId() + " " + what, hover, c);
    }

    /** A reply to what they just said. */
    static void ans(ServerPlayer p, Entity e, String label, String what, String hover) {
        Dialogue.answer(p, label, "/talk " + e.getId() + " " + what, hover);
    }

    static String staffSubtitle(FoundationStaff s) {
        return s.role().title + " · age " + s.age() + " · " + Math.min(years(s), Math.max(1, s.age() - 21)) + " years with the Foundation"
                + (s.illness().isEmpty() ? "" : " · unwell");
    }

    private static String o5Subtitle(O5MemberEntity m) {
        return "Overseer Council · age " + m.age() + (m.isUnseated() ? " · no longer seated" : "");
    }

    private static void ensure(ServerPlayer p, Entity e) {
        if (Dialogue.active(p, sess(e))) return;
        if (e instanceof FoundationStaff s) Dialogue.begin(p, sess(e), s.getName().getString(), staffSubtitle(s), e.getId());
        else if (e instanceof O5MemberEntity m) Dialogue.begin(p, sess(e), m.getSeat() == null ? m.getName().getString() : m.getSeat().sender(), o5Subtitle(m), e.getId());
    }

    /** An Overseer's facts for the left of the screen. */
    static void o5Info(ServerPlayer p, O5MemberEntity m) {
        O5 me = m.getSeat();
        java.util.List<String> info = new java.util.ArrayList<>();
        info.add((m.isUnseated() ? "Former Overseer" : "Overseer, seat " + me.seat) + " · age " + m.age());
        info.add("Personality: " + me.role);
        if (m.age() > 100) info.add("Traits: long-lived (SCP-006)");
        Dialogue.info(p, info, -1);
    }

    /** The topic buttons down the right for an Overseer (without clearing the answers). */
    static void mainMenuButtons(ServerPlayer p, O5MemberEntity e) {
        opt(p, e, "How did you vote?", "vote", "Their vote on the latest decision", 0);
        opt(p, e, "Your work", "story", "Their role on the Council", 0);
        opt(p, e, "What worries you?", "worry", "The Council's concerns", 0);
        if (mayCommand(p, e)) opt(p, e, "Orders", "orders", "Tell them what to do", ORDER_C);
        opt(p, e, "Goodbye", "bye", "End the conversation", 0xC8D0DA);
    }

    static void mainMenu(ServerPlayer p, Entity e) {
        if (e instanceof FoundationStaff fs) { Convo.menu(p, fs); return; }
        if (e instanceof O5MemberEntity m) { Dialogue.clearOptions(p); o5Info(p, m); mainMenuButtons(p, m); return; }
        Dialogue.clearOptions(p);
        if (e instanceof FoundationStaff) {
            opt(p, e, "How are things?", "how", "Ask how they're doing", 0);
            opt(p, e, "Tell me about yourself", "story", "Ask about their life", 0);
            opt(p, e, "Heard anything?", "rumour", "What people are saying", 0);
            opt(p, e, "What do you make of the Council?", "council", "Their view of the latest decision", 0);
            opt(p, e, "Orders", "orders", "Tell them what to do", ORDER_C);
        } else {
            opt(p, e, "How did you vote?", "vote", "Their vote on the latest decision", 0);
            opt(p, e, "Your work on the Council", "story", "Their role on the Council", 0);
            opt(p, e, "What worries you?", "worry", "The Council's concerns", 0);
            if (mayCommand(p, e)) opt(p, e, "Orders", "orders", "Tell them what to do", ORDER_C);
        }
        opt(p, e, "Goodbye", "bye", "End the conversation", 0xC8D0DA);
    }

    public static void open(ServerPlayer p, FoundationStaff s) {
        Dialogue.begin(p, sess(s), s.getName().getString(), staffSubtitle(s), s.getId());
        Convo.open(p, s);
        Dialogue.send(p, true);
    }

    public static void openO5(ServerPlayer p, O5MemberEntity m) { openO5(p, m, Career.rank(p) == Rank.ADMINISTRATOR ? "Administrator." : "Yes?"); }

    /** They speak first (their greeting), and you answer. */
    public static void openO5(ServerPlayer p, O5MemberEntity m, String line) {
        O5 me = m.getSeat();
        Dialogue.begin(p, sess(m), me.sender(), o5Subtitle(m), m.getId());
        Dialogue.mood(p, m.isUnseated() ? "Retired" : WorldNews.morale(p.server) < 0 ? "Grave" : "Composed", m.isUnseated() ? 0xB0B8C4 : WorldNews.morale(p.server) < 0 ? 0xE0B060 : 0xE05050);
        say(p, m, line);
        Dialogue.clearOptions(p);
        ans(p, m, topBrass(p) ? "Carry on." : "Of course.", "o5_carry", "");
        ans(p, m, "What's on your mind?", "worry", "Their concerns");
        if (mayCommand(p, m)) ans(p, m, "Walk with me.", "follow", "They come with you");
        o5Info(p, m);
        mainMenuButtons(p, m);
        Dialogue.send(p, true);
    }

    private static final String[][] O5_STORY = {
            {}, {"I founded this. Not alone, and not proudly. Somebody had to.", "I watch the Gate because I am the only one who remembers why it was built."},
            {"I tend the gentle ones. 999, 131, the ones that only want to be loved.", "People forget we contain the kind things too. To keep them safe from us."},
            {"> I KEEP THE RECORDS.", "> I HAVE NOT LEFT THE TERMINAL IN ELEVEN YEARS. DO NOT ASK WHY."},
            {"I talk to everyone who isn't us. The Coalition, the Hand, the Insurgency when I must.", "Diplomacy is containment by other means."},
            {"I watch the groups that watch us.", "Someone is always outside the window. Usually it's mine."},
            {"I ran site security for twenty years before they gave me a chair.", "I still carry a sidearm in here. Old habits."},
            {"They call me the leader. I'm not. I'm the one who counts the votes before they're cast.", "Tactics is just knowing what everyone will do one move before they do."},
            {"Every Mobile Task Force answers to me.", "I've sent a lot of good people into a lot of bad rooms."},
            {"I see things before they happen. Not always clearly.", "I've seen you before, you know. Before you got here."},
            {"I keep the timeline. All the versions of it.", "In most of them, this site is still standing. Most."},
            {"I sign the termination orders, and I write the cover stories.", "There is always a gas leak. Always."},
            {"I make the numbers add up.", "Containment is expensive. Failure is more expensive."}};

    private static String o5Vote(O5MemberEntity m, ServerPlayer p) {
        WorldNews.Item d = WorldNews.latest(p.server, WorldNews.Kind.DECISION);
        if (d == null) return "We haven't voted on anything worth discussing since you last asked.";
        int seat = m.getSeat().seat;
        boolean forIt = d.supporters().contains(seat);
        String reason = forIt ? (d.mood() >= 0 ? "And it was the right call." : "I'd make the same call again, whatever it cost.")
                : (d.mood() >= 0 ? "I was wrong, as it turns out. Don't tell anyone." : "I said so at the time.");
        return "On " + WorldNews.topic(d) + " " + WorldNews.ago(p.server, d) + " (" + d.headline() + "): I " + (forIt ? "argued for it. " : "voted against it. ") + reason
                + (d.who().contains("over the Council") ? " In the end it was the Administrator's call, as it should be." : "");
    }

    private static String o5Worry(ServerPlayer p) {
        Random r = new Random();
        WorldNews.Item breach = WorldNews.latest(p.server, WorldNews.Kind.BREACH);
        if (breach != null && p.server.overworld().getGameTime() - breach.time() < 48000) return "The breach. " + breach.headline() + ". One breach is an accident. Two is a pattern.";
        int st = Career.data(p).stability;
        WorldNews.Item death = WorldNews.latest(p.server, WorldNews.Kind.DEATH);
        if (death != null && p.server.overworld().getGameTime() - death.time() < 24000) return "We lost someone today: " + death.headline() + ". We don't all have the Fountain.";
        if (st < 40) return "Stability is at " + st + "%. We're one bad night from losing a site.";
        if (st > 85) return "Things are too calm. In my experience, that's when something is building.";
        return pick(r, "The Insurgency has been quiet. They are never quiet for long.", "The Gate. Always the Gate.", "Budget. You'd be amazed how many apocalypses come down to budget.");
    }

    // ================================================================== orders

    static boolean mayCommand(ServerPlayer p, Entity who) {
        Rank r = Career.rank(p);
        if (who instanceof FoundationStaff fs && fs.role() == FoundationStaff.Role.SECRETARY) return topBrass(p);
        if (Career.isAscended(p) || r.atLeast(Rank.OVERSEER)) return true;
        if (who instanceof O5MemberEntity) return false;
        FoundationStaff.Role role = ((FoundationStaff) who).role();
        return switch (role) {
            case GUARD, JANITOR, CLERK -> r.atLeast(Rank.LEVEL_3);
            default -> r.atLeast(Rank.LEVEL_4);
        };
    }

    /** Shows the orders choices (or a refusal). Returns false if refused. */
    static boolean orders(ServerPlayer p, Entity who) {
        if (!mayCommand(p, who)) {
            Rank r = Career.rank(p);
            say(p, who, r == Rank.CLASS_D ? "You don't give orders, D-Class. Back to your block." : "With respect, you don't have the clearance to give me orders.");
            mainMenu(p, who);
            return false;
        }
        Dialogue.clearOptions(p);
        if (who instanceof FoundationStaff s) {
            opt(p, who, "Follow me", "follow", "They come with you", ORDER_C);
            opt(p, who, "Wait here", "stay", "They stay where they are", ORDER_C);
            opt(p, who, "Back to your post", "post", "They return to their work", ORDER_C);
            opt(p, who, "Take a seat", "sit", "They sit on the nearest free chair", ORDER_C);
            String[] job = switch (s.role()) {
                case MEDIC -> new String[]{"Patch me up", "job", "Heal your wounds"};
                case CLERK -> new String[]{"Any assignments for me?", "job", "Your open assignments"};
                case RESEARCHER -> new String[]{"Look at what I'm holding", "job", "Ask about the item in your hand"};
                case TECHNICIAN -> new String[]{"Check the power", "job", "A report on the site's power"};
                case GUARD -> new String[]{"Stand guard here", "stay", "They hold this spot"};
                case JANITOR -> new String[]{"Clean this up", "job", "Sweeps up dropped items nearby"};
                case SECRETARY -> new String[]{"Brief me", "brief", "Your messages, calendar and site status"};
            };
            opt(p, who, job[0], job[1], job[2], ORDER_C);
        } else {
            opt(p, who, "Walk with me", "follow", "They come with you", ORDER_C);
            opt(p, who, "Return to your seat", "post", "Back to the council table", ORDER_C);
        }
        opt(p, who, "◀ Back to topics", "menu", "Other things to talk about", 0xC8D0DA);
        return true;
    }

    static void job(ServerPlayer p, FoundationStaff s) {
        switch (s.role()) {
            case MEDIC -> {
                p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 12, 1));
                p.removeEffect(MobEffects.POISON);
                p.removeEffect(MobEffects.WITHER);
                say(p, s, "Hold still. There. Poison's out, and you'll knit up in a minute.");
            }
            case CLERK -> {
                CareerData d = Career.data(p);
                if (d.missions.isEmpty()) say(p, s, "Nothing open on your file. Enjoy it while it lasts.");
                else {
                    say(p, s, "You have " + d.missions.size() + " open assignment" + (d.missions.size() == 1 ? "" : "s") + ":");
                    for (int i = 0; i < Math.min(4, d.missions.size()); i++)
                        note(p, "• " + d.missions.get(i).description);
                }
            }
            case RESEARCHER -> {
                ItemStack h = p.getMainHandItem();
                if (h.isEmpty()) { say(p, s, "Look at what? Your hands are empty."); return; }
                String id = BuiltInRegistries.ITEM.getKey(h.getItem()).toString();
                String n = h.getHoverName().getString();
                String line;
                if (id.contains("fruit_of_life")) line = "Is that... from the Garden? Don't let anyone else see that. Eat it, and death forgets you for three days.";
                else if (id.contains("fruit_of_knowledge")) line = "Put that down. Below the Council, that fruit gets you thrown out of the Garden for good. Above it... it changes you.";
                else if (id.contains("stasis_pod")) line = "A stasis pod. Check its rating against what you're catching. Overload it and the pod just... stops being a pod.";
                else if (id.contains("keycard")) line = "A keycard. Swipe it on the scanner beside the door, not the door itself.";
                else if (id.contains("scp500")) line = "SCP-500. Cures anything. Count them, and don't waste them on a headache.";
                else if (id.contains("rift")) line = "Rift equipment. Rifts don't close themselves. Draw them out slowly, and never alone.";
                else if (id.startsWith("scp_unleashed:")) line = "That's Foundation equipment: " + n + ". There'll be a file on it in the Archive.";
                else line = n + "? Perfectly ordinary. I checked twice.";
                say(p, s, line);
            }
            case TECHNICIAN -> {
                int outlets = 0;
                net.minecraft.core.BlockPos c = s.blockPosition();
                for (net.minecraft.core.BlockPos q : net.minecraft.core.BlockPos.betweenClosed(c.offset(-24, -8, -24), c.offset(24, 8, 24)))
                    if (BuiltInRegistries.BLOCK.getKey(s.level().getBlockState(q).getBlock()).getPath().equals("creative_outlet")) outlets++;
                say(p, s, outlets > 0 ? "Power's good. " + outlets + " creative outlet" + (outlets == 1 ? "" : "s") + " feeding this area, all live." : "No supply in reach of here. Whatever's on this floor is running on borrowed time.");
            }
            case JANITOR -> {
                int n = 0;
                for (ItemEntity it : s.level().getEntitiesOfClass(ItemEntity.class, s.getBoundingBox().inflate(8))) { it.discard(); n++; }
                say(p, s, n == 0 ? "Already spotless. You're welcome." : "Swept up " + n + " pile" + (n == 1 ? "" : "s") + " of junk. People leave the strangest things.");
            }
            default -> {}
        }
    }

    // ================================================================== the command behind the buttons

    @SubscribeEvent
    public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("talk")
                .then(Commands.argument("id", IntegerArgumentType.integer()).then(Commands.argument("what", StringArgumentType.word()).executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    Entity e = p.serverLevel().getEntity(IntegerArgumentType.getInteger(c, "id"));
                    if (e == null || !e.isAlive()) {
                        Dialogue.line(p, "", "They're gone.", 0);
                        Dialogue.clearOptions(p);
                        Dialogue.option(p, "Close", "close", "", 0xC8D0DA);
                        Dialogue.send(p, false);
                        return 0;
                    }
                    if (e.distanceTo(p) > 24) {
                        Dialogue.line(p, "", "They're too far away to hear you. Walk back over to them.", 0);
                        Dialogue.send(p, false);
                        return 0;
                    }
                    try {
                        handle(p, e, StringArgumentType.getString(c, "what"));
                    } catch (Throwable t) {
                        BreachProtocol.LOGGER.error("Conversation step failed", t);
                        Dialogue.line(p, "", "(They lost their train of thought: " + t.getClass().getSimpleName() + ". Try another topic.)", 0);
                        mainMenu(p, e);
                        Dialogue.send(p, false);
                    }
                    return 1;
                }))));
    }

    private static final Map<String, String> ASKED = Map.ofEntries(
            Map.entry("how", "How are things?"), Map.entry("story", "Tell me about yourself."), Map.entry("rumour", "Heard anything?"),
            Map.entry("council", "What do you make of the Council?"), Map.entry("follow", "Follow me."), Map.entry("stay", "Wait here."),
            Map.entry("post", "Back to your post."), Map.entry("sit", "Take a seat."), Map.entry("vote", "How did you vote?"),
            Map.entry("worry", "What worries you?"), Map.entry("bye", "Goodbye."));

    private static void handle(ServerPlayer p, Entity e, String what) {
        ensure(p, e);
        if (e instanceof FoundationStaff fs && Convo.handle(p, fs, what)) {
            Dialogue.send(p, true);
            return;
        }
        String asked = ASKED.get(what);
        if (asked != null) you(p, e instanceof O5MemberEntity && what.equals("post") ? "Return to your seat." : e instanceof O5MemberEntity && what.equals("follow") ? "Walk with me." : asked);
        boolean showOrders = false;
        if (what.equals("bye")) {
            say(p, e, e instanceof O5MemberEntity ? "Administrator." : pick(new Random(), "Take care.", "See you around.", "Mind how you go.", "Stay safe out there."));
            Dialogue.send(p, false);
            Dialogue.close(p);
            return;
        }
        if (e instanceof FoundationStaff s) {
            switch (what) {
                case "how" -> say(p, s, howAreThings(s, p));
                case "story" -> say(p, s, story(s, s.role(), p));
                case "rumour" -> say(p, s, rumour(s, p));
                case "council" -> say(p, s, councilView(s, p));
                case "orders" -> { you(p, "I have orders for you."); showOrders = orders(p, s); }
                case "menu" -> {}
                case "follow", "stay", "post", "sit" -> {
                    if (!mayCommand(p, s)) { orders(p, s); break; }
                    s.order(what, p);
                    say(p, s, switch (what) {
                        case "follow" -> pick(new Random(), "Right behind you.", "Lead the way.", "On you.");
                        case "stay" -> s.role() == FoundationStaff.Role.GUARD ? "Holding this position." : "I'll wait here.";
                        case "post" -> "Back to work, then.";
                        default -> "Don't mind if I do.";
                    });
                    showOrders = true;
                }
                case "job" -> {
                    you(p, switch (s.role()) {
                        case MEDIC -> "Can you patch me up?";
                        case CLERK -> "Any assignments for me?";
                        case RESEARCHER -> "Take a look at what I'm holding.";
                        case TECHNICIAN -> "Check the power for me.";
                        case JANITOR -> "Clean this up, would you?";
                        case SECRETARY -> "Brief me.";
                        case GUARD -> "Stand guard here.";
                    });
                    if (!mayCommand(p, s) && !(s.role() == FoundationStaff.Role.MEDIC && Career.rank(p) != Rank.CLASS_D)) { orders(p, s); break; }
                    job(p, s);
                    showOrders = mayCommand(p, s);
                }
                default -> {}
            }
        } else if (e instanceof O5MemberEntity m && m.getSeat() != null) {
            int seat = m.getSeat().seat;
            switch (what) {
                case "vote" -> { say(p, m, o5Vote(m, p)); o5Page(p, m, new String[][]{{"Why?", "o5_why"}, {"Would you vote the same again?", "o5_again"}}); return; }
                case "story" -> {
                    String key = "bp_story_" + p.getUUID();
                    int ch = m.getPersistentData().getInt(key);
                    String[] st = O5_STORY[Math.max(1, Math.min(12, seat))];
                    say(p, m, st[ch % st.length]);
                    m.getPersistentData().putInt(key, ch + 1);
                    o5Page(p, m, new String[][]{{"And the rest of the Council?", "o5_peers"}, {"Do you ever regret it?", "o5_regret"}, {"Tell me more", "story"}});
                    return;
                }
                case "worry" -> { say(p, m, o5Worry(p)); o5Page(p, m, new String[][]{{"What should I do about it?", "o5_advice"}, {"Is the Council divided?", "o5_divided"}}); return; }
                case "o5_carry" -> {
                    you(p, topBrass(p) ? "Carry on." : "Of course.");
                    say(p, m, topBrass(p) ? pick(new Random(), "Administrator.", "As you were, then.", "We'll be ready on Tuesday.") : "Good.");
                }
                case "o5_why", "o5_again", "o5_peers", "o5_regret", "o5_advice", "o5_divided" -> {
                    you(p, switch (what) {
                        case "o5_why" -> "Why?";
                        case "o5_again" -> "Would you vote the same again?";
                        case "o5_peers" -> "And the rest of the Council?";
                        case "o5_regret" -> "Do you ever regret it?";
                        case "o5_advice" -> "What should I do about it?";
                        default -> "Is the Council divided?";
                    });
                    say(p, m, o5Deep(p, m, seat, what));
                    o5Page(p, m, switch (what) {
                        case "o5_why", "o5_again" -> new String[][]{{"What worries you?", "worry"}, {"Your work on the Council", "story"}};
                        case "o5_peers", "o5_regret" -> new String[][]{{"How did you vote?", "vote"}, {"What worries you?", "worry"}};
                        default -> new String[][]{{"How did you vote?", "vote"}, {"Your work on the Council", "story"}};
                    });
                    return;
                }
                case "orders" -> { you(p, "I have orders for you."); showOrders = orders(p, m); }
                case "follow" -> { if (mayCommand(p, m)) { m.follow(p); say(p, m, "As you wish."); showOrders = true; } }
                case "post" -> { if (mayCommand(p, m)) { m.follow(null); say(p, m, "I'll be at the table."); showOrders = true; } }
                default -> {}
            }
        }
        if (showOrders) orders(p, e);
        else if (!what.equals("orders") || !mayCommand(p, e)) mainMenu(p, e);
        Dialogue.send(p, true);
    }

    /** An Overseer's follow-up page, then send. */
    private static void o5Page(ServerPlayer p, O5MemberEntity m, String[][] opts) {
        Dialogue.clearOptions(p);
        for (String[] o : opts) ans(p, m, o[0], o[1], "");
        o5Info(p, m);
        mainMenuButtons(p, m);
        Dialogue.send(p, true);
    }

    private static final String[] PEER_LINES = {
            "%s? We have disagreed on almost everything for thirty years. I would trust them with my life.",
            "%s keeps their own counsel. I've learned to watch their hands, not their mouth.",
            "%s is the conscience of the table. Somebody has to be.",
            "%s and I don't speak outside session. It's better that way.",
            "%s sees further than the rest of us. That's not always a comfort.",
            "%s would burn the world to save the Foundation. I'd only burn half."};

    /** The deeper answers an Overseer gives, from their seat and the state of things. */
    private static String o5Deep(ServerPlayer p, O5MemberEntity m, int seat, String what) {
        Random r = new Random(m.getUUID().getLeastSignificantBits() ^ p.server.overworld().getDayTime() / 24000);
        WorldNews.Item d = WorldNews.latest(p.server, WorldNews.Kind.DECISION);
        CareerData cd = Career.data(p);
        return switch (what) {
            case "o5_why" -> d == null ? "There was nothing to vote on. Silence is a vote too, of a kind."
                    : d.supporters().contains(seat) ? new String[]{"", "Because I have seen what happens when we hesitate.", "Because the kind choice and the right one were the same, for once.",
                    "> BECAUSE THE MODELS SAID SO. AND BECAUSE I AGREED WITH THEM.", "Because our partners would have read anything else as weakness.", "Because someone outside was waiting for us to do the opposite.",
                    "Because I've buried people who waited.", "Because three moves from now, we need that piece on the board.", "Because my task forces can carry it.",
                    "Because I saw how the other road ends.", "Because the archive tells me we tried the other way once.", "Because the paperwork for the alternative is a confession.",
                    "Because it was the cheaper mistake."}[Math.max(1, Math.min(12, seat))]
                    : "Because I thought it was wrong. I still think so. But the Council decided, and I'll carry it out.";
            case "o5_again" -> d == null ? "Ask me after the next session." : d.mood() >= 0 ? "Yes. Look at the stability figures. It worked." : "...I don't know. Ask me in a year.";
            case "o5_peers" -> {
                int other = 1 + Math.floorMod(seat * 5 + 3, 12);
                if (other == seat) other = other % 12 + 1;
                yield String.format(PEER_LINES[Math.floorMod(seat * 7 + other, PEER_LINES.length)], O5.seat(other).sender());
            }
            case "o5_regret" -> m.age() > 100 ? "I'm " + m.age() + ". I have outlived every regret I ever had, and found new ones. Yes." : seat == 1 ? "Every day. That's how I know I'm still the right person for this chair."
                    : r.nextBoolean() ? "Regret is a luxury for people who get to go home." : "Once. I signed something I shouldn't have. I won't tell you what.";
            case "o5_advice" -> {
                if (cd.stability < 45) yield "Stability is at " + cd.stability + "%. Bring it to Tuesday's session, or use your terminal. Don't wait for the Council to wait for you.";
                Defence.Alert al = null;
                for (Sites.Site s : Sites.get(p.server).sites) if (Defence.alertAt(s.name) != null) al = Defence.alertAt(s.name);
                if (al != null) yield al.org + " are at " + al.site + ". Your terminal can send Nu-7. I'd send Nu-7.";
                for (Sites.Site s : Sites.get(p.server).sites) {
                    Forces.Station st = Forces.data(p.server).station(s.name);
                    int want = 0;
                    for (Forces.Unit u : Forces.Unit.values()) want += st.wanted(u);
                    if (want - st.members.size() >= 3) yield s.name + " is short of guards. Fix it before something finds out.";
                }
                yield cd.agenda.size() >= 2 ? "Read the agenda before Tuesday. " + cd.agenda.size() + " of us have put things on it." : "Nothing. That's the hardest advice to take, I know.";
            }
            default -> {
                long split = d == null ? 0 : d.supporters().size();
                yield d == null ? "Not today." : split <= 4 ? "Yes. The last vote was close, and close votes leave bruises." : "Less than you'd think. We argue in session and close ranks outside it.";
            }
        };
    }

    // ================================================================== overheard

    /** Two staff near each other sometimes talk about the news, and you can overhear them. */
    public static void chatter(FoundationStaff a) {
        if (!(a.level() instanceof net.minecraft.server.level.ServerLevel sl)) return;
        FoundationStaff b = null;
        for (FoundationStaff o : sl.getEntitiesOfClass(FoundationStaff.class, a.getBoundingBox().inflate(5))) if (o != a && !o.getTags().contains(RiftTeams.CREW_TAG)) b = o;
        if (b == null) return;
        List<ServerPlayer> ears = new ArrayList<>();
        for (ServerPlayer q : sl.players()) if (q.distanceTo(a) < 12) ears.add(q);
        if (ears.isEmpty()) return;
        Random r = new Random();
        List<WorldNews.Item> news = WorldNews.recent(sl.getServer(), 4);
        String l1, l2;
        String[] h2;
        ServerPlayer boss = null;
        for (ServerPlayer q : ears) if (topBrass(q)) boss = q;
        if (boss != null && r.nextInt(3) == 0) {
            String[][] hush = {{"Is that the Administrator?", "Don't look. Don't look at it."},
                    {"The Administrator's on this floor.", "...I have a family. Just keep working."},
                    {"Did the Administrator just walk past?", "Eyes on your clipboard. Look busy."},
                    {"I heard what the Administrator did to 096.", "Keep your voice down. They can hear everything."},
                    {"Do you think the Administrator sleeps?", "I think the Administrator doesn't need to."}};
            String[] h = hush[r.nextInt(hush.length)];
            l1 = h[0];
            l2 = h[1];
        } else if ((h2 = Social.chatter(a, b, ears, r)) != null) {
            l1 = h2[0];
            l2 = h2[1];
        } else if (!news.isEmpty() && r.nextInt(3) > 0) {
            WorldNews.Item i = news.get(r.nextInt(Math.min(3, news.size())));
            l1 = pick(r, "Did you hear " + i.headline() + " " + WorldNews.ago(sl.getServer(), i) + "?", "So apparently " + i.headline() + ", " + WorldNews.ago(sl.getServer(), i) + ".", "They're saying " + i.headline() + ".");
            l2 = opinion(temper(b), i.mood(), r);
        } else {
            String[][] small = {{"Coffee machine's broken again.", "Someone said it's anomalous now. It makes tea."},
                    {"You on nights this week?", "Nights. Always nights."},
                    {"Did you finish the incident report?", "Which one? There were three."},
                    {"I swear the hallway on B2 was longer yesterday.", "Don't measure it. Last guy who measured it got transferred."},
                    {"Lunch?", "If 999 is in the cafeteria again, I'm in."}};
            String[] s = small[r.nextInt(small.length)];
            l1 = s[0];
            l2 = s[1];
        }
        a.getLookControl().setLookAt(b, 30, 30);
        b.getLookControl().setLookAt(a, 30, 30);
        for (ServerPlayer q : ears) {
            q.sendSystemMessage(Component.literal(a.getName().getString() + ": ").withStyle(ChatFormatting.DARK_AQUA).append(Component.literal(Clearance.filter(q, l1)).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)));
            q.sendSystemMessage(Component.literal(b.getName().getString() + ": ").withStyle(ChatFormatting.DARK_AQUA).append(Component.literal(Clearance.filter(q, l2)).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)));
        }
    }
}
