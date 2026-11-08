package com.breachprotocol.entity;

import com.breachprotocol.career.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import com.breachprotocol.compat.Unleashed;
import java.util.*;

/**
 * The branching conversation with staff. Every topic opens a page of follow-ups; what they say is
 * built from what is actually going on: the site they work at, attacks and the guards stationed
 * there, the Council's decisions (and who voted for what), deaths, illness, the weather, the time,
 * your rank and what you're wearing, and how much they've come to trust you.
 */
final class Convo {
    private Convo() {}

    private static final int TOPIC = 0, DEEP = 0x9FD8FF, SECRET = 0xD8A8FF, BACK = 0xC8D0DA;

    // ================================================================== trust

    private static String tkey(ServerPlayer p) { return "bp_trust_" + p.getUUID(); }

    static int trust(FoundationStaff s, ServerPlayer p) { return s.getPersistentData().getInt(tkey(p)); }

    static void setTrust(FoundationStaff s, ServerPlayer p, int v) { s.getPersistentData().putInt(tkey(p), Math.max(0, Math.min(10, v))); }

    /** Talking about something new builds trust, once per topic per day. */
    private static void bump(FoundationStaff s, ServerPlayer p, String topic) {
        long day = s.level().getDayTime() / 24000;
        String k = "bp_tt_" + p.getUUID() + "_" + topic;
        if (s.getPersistentData().getLong(k) == day + 1) return;
        s.getPersistentData().putLong(k, day + 1);
        s.getPersistentData().putInt(tkey(p), Math.min(10, trust(s, p) + (Social.admirer(s) ? 2 : 1))); // admirers warm quickly
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
        mood(p, s);
        List<String> info = new ArrayList<>();
        String head = Management.title(s);
        info.add((head != null ? head : s.unit() != null ? s.unit().title : s.role().title) + " · age " + s.age());
        Personality pe = Personality.of(s);
        info.add("Personality: " + pe.label + " - " + pe.about);
        int rs = Social.respect(s, p);
        info.add("Respect: " + Social.bar(rs) + " " + Social.respectWord(rs));
        List<String> traits = new ArrayList<>();
        int yrs = Math.min(Talk.years(s), Math.max(1, s.age() - 21));
        if (yrs >= 15) traits.add("veteran (" + yrs + " years)");
        if (s.elder()) traits.add("elderly");
        if (!s.illness().isEmpty()) traits.add("ill with " + s.illness());
        if (!Memory.recall(s, p, new Random(s.getId())).isEmpty()) traits.add("remembers you");
        traits.addAll(Social.familyTraits(s));
        for (Traits x : s.traits()) if (!x.about.isEmpty() && (x != Traits.INFERTILE || trust(s, p) >= 8)) traits.add(x.label.toLowerCase(Locale.ROOT));
        if (Social.partner(s, p)) traits.add("your partner");
        if (Social.slighted(s, p)) traits.add("insolent");
        if (Memory.get(s, p, "reprimanded").equals("1")) traits.add("reprimanded");
        if (!traits.isEmpty()) info.add("Traits: " + String.join(", ", traits));
        info.add(Scene.cap(bond(t)) + " · " + Scene.of(p, s).place.name());
        Registry.Rec rec = Registry.find(s.getServer(), s.getUUID());
        String[] nm = s.getName().getString().split(" ");
        info.add("Native of " + (rec != null && rec.note.contains("village") ? "the village" : rec != null && !rec.site.isEmpty() ? rec.site : siteName(s)));
        if (nm.length > 1) info.add("Surname: " + nm[nm.length - 1]);
        Dialogue.info(p, info, t);
        Dialogue.card(p, card(p, s, t));
    }

    /** MCA's left side, item for item: name, profession, mood, personality, traits; hearts, marriage, genes, parents, analysis; and ours below. */
    static net.minecraft.nbt.CompoundTag card(ServerPlayer p, FoundationStaff s, int t) {
        net.minecraft.nbt.CompoundTag c = new net.minecraft.nbt.CompoundTag();
        String head = Management.title(s);
        c.putString("name", s.getName().getString());
        c.putString("job", (head != null ? head : s.unit() != null ? s.unit().title : s.role().title) + " · age " + s.age());
        Personality pe = Personality.of(s);
        c.putString("pers", pe.label);
        c.putString("persAbout", pe.about);
        net.minecraft.nbt.ListTag tr = new net.minecraft.nbt.ListTag();
        for (Traits x : s.traits()) {
            if (x.about.isEmpty() && x != Traits.BISEXUAL && x != Traits.HOMOSEXUAL) continue;
            if (x == Traits.INFERTILE && t < 8) continue;
            net.minecraft.nbt.CompoundTag o = new net.minecraft.nbt.CompoundTag();
            o.putString("n", x.label);
            o.putString("d", x.about.isEmpty() ? x.label : x.about);
            tr.add(o);
        }
        c.put("traits", tr);
        // hearts on MCA's scale (0 to 100); black when they've turned on you, gold at the top
        c.putInt("hearts", t * 10);
        c.putBoolean("black", Social.slighted(s, p));
        // marriage, from the registry
        Registry.Rec e = Registry.find(s.getServer(), s.getUUID());
        String ms = "notMarried", sp = "";
        if (e != null) {
            Registry.Rec o = Registry.find(s.getServer(), e.spouse != null ? e.spouse : e.partner);
            sp = o == null ? "" : o.name;
            if (e.rel.equals("married")) ms = o != null && o.player ? "marriedToPlayer" : "married";
            else if (e.rel.equals("engaged")) ms = "engaged";
            else if (e.rel.equals("dating")) ms = "promised";
            else if (e.rel.equals("widowed")) ms = "widow";
            Registry.Rec mo = Registry.find(s.getServer(), e.mother), fa = Registry.find(s.getServer(), e.father);
            if (mo != null || fa != null) { c.putString("father", fa == null ? "Unknown" : fa.name); c.putString("mother", mo == null ? "Unknown" : mo.name); }
        }
        if (Social.partner(s, p) && ms.equals("notMarried")) { ms = "promised"; sp = p.getGameProfile().getName(); }
        c.putString("marriage", ms);
        c.putString("spouse", sp);
        // genes, as MCA lists them
        net.minecraft.nbt.ListTag gs = new net.minecraft.nbt.ListTag();
        String[] gn = {"Size", "Width", "Breast", "Melanin", "Hemoglobin", "Eumelanin", "Pheomelanin", "Skin", "Face", "Eye brightness"};
        float[] gv = {s.gene(9), s.gene(10), s.isFemale() ? s.bustSize() : 0, s.melanin(), s.hemoglobin(), s.eumelanin(), s.pheomelanin(), s.gene(1), s.gene(7), s.gene(12)};
        for (int i = 0; i < gn.length; i++) gs.add(net.minecraft.nbt.StringTag.valueOf(gn[i] + ": " + (int) (gv[i] * 100) + "%"));
        c.put("genes", gs);
        // the last social interaction's odds, like MCA's analysis icon
        String an = Memory.get(s, p, "soc_analysis");
        if (!an.isEmpty()) {
            net.minecraft.nbt.ListTag al = new net.minecraft.nbt.ListTag();
            for (String x : an.split(";")) al.add(net.minecraft.nbt.StringTag.valueOf(x));
            c.put("analysis", al);
        }
        // and what the Foundation adds
        net.minecraft.nbt.ListTag ex = new net.minecraft.nbt.ListTag();
        int rs = Social.respect(s, p);
        ex.add(net.minecraft.nbt.StringTag.valueOf("Respect: " + Social.bar(rs) + " " + Social.respectWord(rs)));
        List<String> status = new ArrayList<>();
        int yrs = Math.min(Talk.years(s), Math.max(1, s.age() - 21));
        if (yrs >= 15) status.add("veteran (" + yrs + " years)");
        if (!s.illness().isEmpty()) status.add("ill with " + s.illness());
        if (!Memory.recall(s, p, new Random(s.getId())).isEmpty()) status.add("remembers you");
        if (Social.slighted(s, p)) status.add("insolent");
        if (Memory.get(s, p, "reprimanded").equals("1")) status.add("reprimanded");
        int kids = e == null ? 0 : Registry.children(s.getServer(), e.id).size();
        if (kids > 0) status.add(kids + (kids == 1 ? " child" : " children"));
        if (!status.isEmpty()) ex.add(net.minecraft.nbt.StringTag.valueOf(Scene.cap(String.join(", ", status))));
        ex.add(net.minecraft.nbt.StringTag.valueOf(Scene.cap(bond(t)) + " · " + Scene.of(p, s).place.name()));
        c.put("extra", ex);
        // MCA's gift icon: people who like you sometimes have something for you
        long day = s.level().getDayTime() / 24000;
        if (t >= 6 && !Memory.get(s, p, "gift_claimed").equals(String.valueOf(day)) && Math.floorMod((s.getUUID().hashCode() ^ day * 31), 3) == 0) c.putBoolean("gift", true);
        net.minecraft.nbt.ListTag foot = new net.minecraft.nbt.ListTag();
        String[] nm = s.getName().getString().split(" ");
        foot.add(net.minecraft.nbt.StringTag.valueOf("Native of " + (e != null && e.note.contains("village") ? "the village" : e != null && !e.site.isEmpty() ? e.site : siteName(s))));
        if (nm.length > 1) foot.add(net.minecraft.nbt.StringTag.valueOf("Surname: " + nm[nm.length - 1]));
        c.put("foot", foot);
        return c;
    }

    /** Their mood, from what's happening around them and their temperament. */
    static void mood(ServerPlayer p, FoundationStaff s) {
        Sites.Site x = site(s);
        Talk.Temper tp = Talk.temper(s);
        int morale = WorldNews.morale(p.server) + Career.data(p).stability / 20 - 2;
        WorldNews.Item death = WorldNews.latest(p.server, WorldNews.Kind.DEATH);
        String m;
        int c;
        if (x != null && Defence.alertAt(x.name) != null) { m = "Alarmed"; c = 0xFF6060; }
        else if (Talk.anomalyNear(s, 20) != null) { m = "Frightened"; c = 0xFF9060; }
        else if (Talk.joyNear(s, 16) != null) { m = "Delighted (SCP-999)"; c = 0x80E0A0; }
        else if (!s.illness().isEmpty()) { m = "Unwell"; c = 0xB0C890; }
        else if (death != null && p.server.overworld().getGameTime() - death.time() < 24000) { m = "Grieving"; c = 0x90A8C8; }
        else if (morale < 0) {
            m = switch (tp) { case BOLD -> "Steeled"; case CAREFUL -> "Anxious"; case CYNICAL -> "Sour"; case EARNEST -> "Worried"; };
            c = 0xE0B060;
        } else {
            m = switch (tp) { case BOLD -> "Confident"; case CAREFUL -> "Calm"; case CYNICAL -> "Wry"; case EARNEST -> "Hopeful"; };
            c = 0x80E0A0;
        }
        if (trust(s, p) >= 8 && c == 0x80E0A0) m = "Glad to see you";
        if (c == 0x80E0A0 || c == 0xE0B060) {
            if (Social.partner(s, p)) { m = "Happy to see you"; c = 0xF0A0C0; }
            else if (Social.admirer(s) && !Memory.get(s, p, "admire").equals("over") && trust(s, p) >= 2) { m = "Flustered"; c = 0xF0A0C0; }
            if (Social.slighted(s, p)) { m = "Insolent"; c = 0xC07050; }
        }
        if (Social.widowedRecently(s) && c != 0xFF6060) { m = "Grieving"; c = 0x90A8C8; }
        Dialogue.mood(p, m, c);
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
        Scene.Tier tier = Scene.tier(p);
        for (WorldNews.Item i : WorldNews.recent(p.server, 8)) if (!Talk.mine(i, p) && Clearance.knows(tier, i)) out.add(i);
        return out;
    }

    private static String colleague(FoundationStaff s) {
        List<String> n = new ArrayList<>();
        for (FoundationStaff o : s.level().getEntitiesOfClass(FoundationStaff.class, s.getBoundingBox().inflate(48))) if (o != s) n.add(o.getName().getString());
        return n.isEmpty() ? "someone in the canteen" : n.get(new Random().nextInt(n.size()));
    }

    // ================================================================== the opening exchange

    private static final Map<UUID, List<Memory.Reply>> OPENING = new HashMap<>();

    /** They speak first; you answer from the replies that fit what they said. */
    static void open(ServerPlayer p, FoundationStaff s) {
        if (s.getPersistentData().getBoolean("bp_escorted")) {
            Talk.note(p, "(" + s.getName().getString() + " won't look at you.)");
            Dialogue.clearOptions(p);
            Talk.opt(p, s, "Goodbye", "bye", "End the conversation", BACK);
            return;
        }
        if (Management.candidate(s)) { Dialogue.clearOptions(p); Management.openInterview(p, s); return; }
        if (Recruiting.walkIn(s)) { Dialogue.clearOptions(p); Recruiting.openWalkIn(p, s); return; }
        Social.seed(s, p);
        PAGE.put(p.getUUID(), "root");
        Openers.Opener op = Social.opener(p, s);
        if (op == null) op = Openers.of(p, s);
        Talk.say(p, s, op.text());
        OPENING.put(p.getUUID(), op.replies());
        Dialogue.clearOptions(p);
        subtitle(p, s);
        for (int i = 0; i < op.replies().size(); i++) Talk.ans(p, s, op.replies().get(i).label(), "op_" + i, "");
        topics(p, s);
    }

    // ================================================================== the pages

    static void menu(ServerPlayer p, FoundationStaff s) {
        Dialogue.clearOptions(p);
        topics(p, s);
    }

    /** The topic buttons down the right of the screen (and the facts down the left). */
    static void topics(ServerPlayer p, FoundationStaff s) {
        if (Management.candidate(s)) { Management.topics(p, s); return; } // an interview
        if (Recruiting.walkIn(s)) { Recruiting.walkInTopics(p, s); return; } // a villager asking for work
        subtitle(p, s);
        if (!Dialogue.hasAnswers(p)) Converse.replies(p, s); // there is always something to say back
        Scene.Tier tier = Scene.tier(p);
        String page = PAGE.getOrDefault(p.getUUID(), "root");
        boolean d = tier == Scene.Tier.DCLASS;
        if (!Memory.recall(s, p, new Random(s.getId())).isEmpty()) Talk.opt(p, s, "You wanted to tell me something?", "recall", "They remember your last conversation", SECRET);
        switch (page) {
            case "talk" -> {
                // MCA's Talk: the things to talk about
                Talk.opt(p, s, "<", "menu_root", "Back", BACK);
                if (!d && s.role() == FoundationStaff.Role.SECRETARY && Talk.topBrass(p)) Talk.opt(p, s, "Brief me", "brief", "Messages, calendar and site status", Talk.ORDER_C);
                if (tier.ordinal() >= Scene.Tier.STAFF.ordinal()) Talk.opt(p, s, tier.ordinal() >= Scene.Tier.DIRECTOR.ordinal() ? "Report" : "Status?", "report", "What's happening where they are, in numbers", Talk.ORDER_C);
                Talk.opt(p, s, "How are things?", "how", "Ask how they're doing", TOPIC);
                Talk.opt(p, s, "About yourself", "story", d ? "Not everyone will tell a D-Class" : "Their life, one chapter at a time", TOPIC);
                if (d) {
                    Talk.opt(p, s, "What happens next?", "dtest", "Your next test, if there is one", TOPIC);
                    Talk.opt(p, s, "Something to eat?", "dfood", "Some staff will slip you food", TOPIC);
                } else {
                    Talk.opt(p, s, "The news", "news", "What people are talking about", TOPIC);
                    Talk.opt(p, s, "Your work", "work", "What they're doing and what they need", TOPIC);
                    Talk.opt(p, s, "The Council", "council", "Their view of the O5 and their decisions", TOPIC);
                    Talk.opt(p, s, "Site security", "safe", "Guards, attacks and threats at " + siteName(s), TOPIC);
                }
                if (trust(s, p) >= (tier.ordinal() >= Scene.Tier.COUNCIL.ordinal() ? 7 : 6)) Talk.opt(p, s, "Off the record...", "secret", "They trust you enough to tell you what they shouldn't", SECRET);
            }
            case "interact" -> {
                // MCA's Interact: doing things with them
                Talk.opt(p, s, "<", "menu_root", "Back", BACK);
                Talk.opt(p, s, "Socialise", "socialise", "Chat, jokes, compliments" + (trust(s, p) >= 5 ? ", flirting" : "") + (trust(s, p) >= 6 ? ", hugs" : ""), Social.SOCIAL_C);
                Dialogue.option(p, "Gift", "gift:" + s.getId(), "Hold something and right-click them to give it", 0xF0A8C8);
                if (!d) Talk.opt(p, s, "Orders", "orders", "Follow, wait, back to work, sit", Talk.ORDER_C);
                Talk.opt(p, s, "Address me as...", "addr_page", "How they should address you", TOPIC);
                if (Social.slighted(s, p)) Talk.opt(p, s, "Discipline", "discipline", "They disrespected you. Deal with it", Social.DISCIPLINE_C);
            }
            default -> {
                Talk.opt(p, s, "Talk", "menu_talk", "News, work, their life, the Council", TOPIC);
                Talk.opt(p, s, "Interact", "menu_interact", "Socialise, gifts, orders", TOPIC);
                if (!d && s.role() == FoundationStaff.Role.SECRETARY && Talk.topBrass(p)) Talk.opt(p, s, "Brief me", "brief", "Messages, calendar and site status", Talk.ORDER_C);
                if (Management.mayAttend(p) && (Management.headOf(s) != null || s.role() == FoundationStaff.Role.SECRETARY && Talk.topBrass(p)))
                    Talk.opt(p, s, "Personnel", "mg_page", "Hiring, interviews and appointments" + (Management.headOf(s) != null ? " in their department" : ""), Talk.ORDER_C);
                if (Social.slighted(s, p)) Talk.opt(p, s, "Discipline", "discipline", "They disrespected you. Deal with it", Social.DISCIPLINE_C);
                Talk.opt(p, s, "Family Tree", "family_tree", "Their family, from grandparents to grandchildren", 0xF0A8C8);
            }
        }
        Talk.opt(p, s, "Goodbye", "bye", "End the conversation", BACK);
    }

    /** Which of MCA's menus is showing (the main one, Talk or Interact). */
    static final Map<UUID, String> PAGE = new HashMap<>();

    /** How they address you: sir, ma'am, your title, or your name (per person). */
    static boolean address(ServerPlayer p, FoundationStaff s, String what) {
        switch (what) {
            case "claim_gift" -> {
                long day = s.level().getDayTime() / 24000;
                if (Memory.get(s, p, "gift_claimed").equals(String.valueOf(day)) || trust(s, p) < 6) { menu(p, s); return true; }
                Memory.put(s, p, "gift_claimed", String.valueOf(day));
                Random r = new Random();
                net.minecraft.world.item.Item[] items = {net.minecraft.world.item.Items.COOKIE, net.minecraft.world.item.Items.POPPY, net.minecraft.world.item.Items.BREAD, net.minecraft.world.item.Items.APPLE, net.minecraft.world.item.Items.EMERALD, net.minecraft.world.item.Items.CAKE, net.minecraft.world.item.Items.PAPER};
                net.minecraft.world.item.Item it = items[r.nextInt(items.length)];
                net.minecraft.world.item.ItemStack st = new net.minecraft.world.item.ItemStack(it);
                if (it == net.minecraft.world.item.Items.PAPER) st.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("A note from " + s.getName().getString()));
                if (!p.getInventory().add(st)) p.drop(st, false);
                Talk.you(p, "Is that for me?");
                Talk.say(p, s, Clearance.RAW + switch (net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(it).getPath()) {
                    case "cookie" -> "From the canteen. I saved you one. Don't tell anyone.";
                    case "poppy" -> "I picked it on the way in. It made me think of you. Is that strange?";
                    case "emerald" -> "Hazard pay came through. I wanted to share it. You've earned it more than me.";
                    case "cake" -> "It's not anyone's birthday. I just wanted to.";
                    case "paper" -> "Read it later. Please. Not in front of me.";
                    default -> "It's not much. I just wanted to say thank you.";
                });
                menu(p, s);
                return true;
            }
            case "menu_root", "menu_talk", "menu_interact" -> { PAGE.put(p.getUUID(), what.substring(5)); menu(p, s); return true; }
            case "addr_page" -> {
                Talk.you(p, "About how you address me.");
                Talk.say(p, s, Clearance.RAW + "Of course. How would you like me to address you?");
                Dialogue.clearOptions(p);
                subtitle(p, s);
                Talk.ans(p, s, "\"Sir\" is fine.", "addr_sir", "");
                Talk.ans(p, s, "\"Ma'am\", please.", "addr_maam", "");
                Talk.ans(p, s, "Use my title.", "addr_title", "Administrator, Director, Doctor...");
                Talk.ans(p, s, "Call me by my name.", "addr_name", "They'll need to know you well");
                topics(p, s);
                return true;
            }
            case "addr_sir", "addr_maam", "addr_title", "addr_name" -> {
                String k = what.substring(5);
                if (k.equals("name") && trust(s, p) < 6 && !Social.partner(s, p)) {
                    Talk.you(p, "Call me by my name.");
                    Talk.say(p, s, Clearance.RAW + "I- I couldn't. Not yet. It wouldn't feel right.");
                } else {
                    Memory.put(s, p, "addr", k);
                    Talk.you(p, switch (k) { case "sir" -> "\"Sir\" is fine."; case "maam" -> "\"Ma'am\", please."; case "title" -> "Use my title."; default -> "Call me by my name."; });
                    Career.ADDRESS.set(k);
                    Talk.say(p, s, Clearance.RAW + switch (k) {
                        case "name" -> "All right. " + p.getGameProfile().getName() + ". That's going to take some getting used to.";
                        default -> "Understood, " + Career.address(p) + ".";
                    });
                }
                menu(p, s);
                return true;
            }
            default -> { return false; }
        }
    }

    /** A factual report on where they are: what's contained nearby, who's on duty, who's ill, what's happened. */
    static String report(Scene sc) {
        List<String> out = new ArrayList<>();
        out.add(Scene.cap(sc.place.name()) + (sc.site != null ? ", " + sc.site.name : "") + ".");
        Sites.Cell c = sc.cell();
        if (c != null) out.add("Nearest cell: the " + c.name + (Sites.get(sc.p.server).cells.isEmpty() ? "" : "") + ".");
        if (sc.loose != null) out.add(Unleashed.designation(sc.loose) + " is OUT, within twenty metres.");
        if (sc.site != null) {
            int held = 0, loose = 0;
            for (com.breachprotocol.career.Containment.Entry e : com.breachprotocol.career.Containment.at(sc.p.server, sc.site.name)) {
                if (e.status == com.breachprotocol.career.Containment.HELD) held++; else if (e.status == com.breachprotocol.career.Containment.LOOSE) loose++;
            }
            if (held + loose > 0) out.add("Registry: " + held + " held here" + (loose > 0 ? ", " + loose + " loose." : "."));
        }
        if (sc.site != null) {
            Forces.Station st = Forces.data(sc.p.server).station(sc.site.name);
            int want = 0;
            for (Forces.Unit u : Forces.Unit.values()) want += st.wanted(u);
            out.add(st.members.size() + " of " + want + " armed personnel on duty.");
            if (sc.alert != null) out.add("Under attack: " + sc.alert.org + ", " + sc.alert.hostiles + " hostiles, " + sc.alert.kills + " down.");
        }
        int sick = 0, staff = 0;
        for (FoundationStaff o : sc.s.level().getEntitiesOfClass(FoundationStaff.class, sc.s.getBoundingBox().inflate(48))) { staff++; if (!o.illness().isEmpty()) sick++; }
        out.add(staff + " staff nearby" + (sick > 0 ? ", " + sick + " of them ill." : ", all fit."));
        out.add("Site stability " + Career.data(sc.p).stability + "%.");
        List<WorldNews.Item> n = sc.news();
        if (!n.isEmpty()) out.add("Latest: " + sc.event(n.get(0)));
        return String.join(" ", out);
    }

    static void page(ServerPlayer p, FoundationStaff s, String[][] opts) {
        Dialogue.clearOptions(p);
        subtitle(p, s);
        for (String[] o : opts) Talk.ans(p, s, o[0], o[1], o.length > 2 ? o[2] : "");
        topics(p, s);
    }

    /** Handles a choice. Returns false if it isn't one of the conversation pages. */
    static boolean handle(ServerPlayer p, FoundationStaff s, String what) {
        Random r = new Random();
        Talk.Temper t = Talk.temper(s);
        String a = Career.address(p);
        int trust = trust(s, p);
        if (address(p, s, what) || Recruiting.handleWalkIn(p, s, what) || Management.handle(p, s, what) || Lifecycle.handle(p, s, what) || Social.handle(p, s, what) || Converse.handle(p, s, what)) return true;
        switch (what) {
            case "menu" -> { menu(p, s); return true; }
            case "gift" -> {
                ItemStack held = p.getMainHandItem();
                if (held.isEmpty()) { Talk.say(p, s, "...Your hands are empty."); menu(p, s); return true; }
                Talk.you(p, "Here. This is for you. (" + held.getHoverName().getString() + ")");
                if (s.acceptRemedy(p, held)) { menu(p, s); return true; }
                gift(p, s, held, r);
                menu(p, s);
                return true;
            }
            case "report" -> {
                Scene sc = Scene.of(p, s);
                Talk.you(p, sc.high() ? "Report." : "Status?");
                if (sc.tier.ordinal() < Scene.Tier.STAFF.ordinal()) { Talk.say(p, s, "That's above your clearance. Sorry."); menu(p, s); return true; }
                Talk.say(p, s, report(sc));
                page(p, s, new String[][]{{"Site security", "safe", "Guards and threats"}, {"Any problems?", "work_problems", "What's getting in their way"}});
                return true;
            }
            case "dtest" -> {
                Talk.you(p, "What happens to me next?");
                CareerData cd = Career.data(p);
                String line = !cd.missions.isEmpty() ? "Your file says: " + cd.missions.get(0).description + " Do it, and you might see next month."
                        : s.role() == FoundationStaff.Role.GUARD ? "Next? You go back to your cell. Then you wait. That's what's next."
                        : "Nothing's scheduled for you. Enjoy it. That doesn't happen often.";
                Talk.say(p, s, line);
                menu(p, s);
                return true;
            }
            case "dfood" -> {
                Talk.you(p, "Can I get something to eat?");
                return handle(p, s, "op_food_direct");
            }
            case "op_food_direct" -> {
                long day = s.level().getDayTime() / 24000;
                String k = "bp_fed_" + p.getUUID();
                boolean kind = s.role() != FoundationStaff.Role.GUARD || trust >= 4;
                if (!kind) Talk.say(p, s, "Meals are at the canteen, at the times on the wall. Move along.");
                else if (s.getPersistentData().getLong(k) == day + 1) Talk.say(p, s, "I already gave you something today. Don't push it.");
                else {
                    s.getPersistentData().putLong(k, day + 1);
                    ItemStack food = new ItemStack(trust >= 6 ? net.minecraft.world.item.Items.COOKED_BEEF : net.minecraft.world.item.Items.BREAD, 2);
                    if (!p.getInventory().add(food)) p.drop(food, false);
                    Talk.say(p, s, trust >= 6 ? "Here. From the staff canteen. Don't tell anyone where you got it." : "Here. It's not much. Eat it before someone sees.");
                    s.getPersistentData().putInt(tkey(p), Math.min(10, trust + 1));
                }
                menu(p, s);
                return true;
            }
            case "recall" -> {
                Talk.you(p, "You wanted to tell me something?");
                String m = Memory.recall(s, p, r);
                Talk.say(p, s, m.isEmpty() ? "Hm? No. Just glad to see you." : m);
                menu(p, s);
                return true;
            }
            case "brief", "brief_msg", "brief_cal", "brief_news", "brief_accept", "brief_site", "brief_cells" -> {
                if (s.role() != FoundationStaff.Role.SECRETARY) return false;
                if (!Talk.topBrass(p)) { Talk.say(p, s, "I'm sorry. Briefings are for the Administrator."); menu(p, s); return true; }
                Secretary.brief(p, s, what);
                return true;
            }

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
                    case SECRETARY -> Talk.topBrass(p) ? "For you to answer the Council's requests before they start calling me instead." : "An appointment, if you want to see the Administrator.";
                };
                Talk.say(p, s, line);
                bump(s, p, "how_need");
                Dialogue.clearOptions(p);
                subtitle(p, s);
                Talk.ans(p, s, "I'll see to it.", "promise", "Promise to help (they'll remember)");
                Talk.ans(p, s, "That's not my problem.", "dismiss", "Brush it off");
                topics(p, s);
                return true;
            }
            case "promise" -> {
                Talk.you(p, "I'll see to it.");
                Memory.count(s, p, "promised");
                if (s.role() == FoundationStaff.Role.MEDIC || !s.illness().isEmpty()) Memory.put(s, p, "op_promised500", "1"); // they'll expect SCP-500 from you
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
                page(p, s, new String[][]{{"That must have been hard.", "listen_hard", "They'll remember you listened"}, {"Thank you for telling me.", "listen_thanks", "They'll remember you listened"},
                        {"And then what happened?", "story_more", "The next chapter"},
                        {"How long have you been here?", "story_years", "Their years with the Foundation"},
                        {"Do you have family?", "story_family", "Life outside the walls"}});
                return true;
            }
            case "story_more" -> {
                Talk.you(p, "And then what happened?");
                Talk.say(p, s, Talk.story(s, s.role(), p));
                page(p, s, new String[][]{{"That must have been hard.", "listen_hard", "They'll remember"}, {"That's the job, I suppose.", "listen_job", "They'll remember"},
                        {"And then?", "story_more", "Keep listening"}, {"Do you have family?", "story_family", "Life outside"}});
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
                String line = trust < 2 ? "That's... personal. Ask me again when we know each other better." : Social.familyLine(s, p, r);
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
                Talk.say(p, s, Scene.of(p, s).relay(it));
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
                    case SECRETARY -> new String[]{"Brief me", "brief", "Messages, calendar and site status"};
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
                Scene.Tier ct = Scene.tier(p);
                if (ct == Scene.Tier.DCLASS) { Talk.say(p, s, Clearance.RAW + "The Council? Some of the old-timers in the block talk about thirteen people who decide who lives. I think it's a story to keep you quiet."); menu(p, s); return true; }
                if (ct == Scene.Tier.JUNIOR) {
                    Talk.say(p, s, Clearance.RAW + switch (t) {
                        case CYNICAL -> "The O5? Thirteen faceless people who run everything. It's what they tell new hires so we behave. Nobody I know has ever met one.";
                        case CAREFUL -> "I've heard the name. O5. I don't say it out loud, and I'd suggest you don't either.";
                        case EARNEST -> "People say there's a council of thirteen at the very top. I like to think someone up there is looking out for us.";
                        case BOLD -> "A council of thirteen? Maybe. If they're real, they're welcome to come down and work a night shift.";
                    });
                    page(p, s, new String[][]{{"Do you believe it?", "myth_believe", "Real, or a story?"}, {"Who told you about it?", "myth_who", "Where the rumour comes from"}});
                    return true;
                }
                if (ct == Scene.Tier.STAFF) {
                    Talk.say(p, s, Clearance.RAW + "The Council is real. Orders come down signed 'O5' and nothing else. Who they are, I couldn't tell you, and I'm fairly sure I shouldn't try.");
                    page(p, s, new String[][]{{"Do you agree with their orders?", "council_agree", "Their honest view"}, {"Do you trust them?", "council_trust", "Faith in the Council"}});
                    return true;
                }
                Talk.say(p, s, Talk.councilView(s, p));
                bump(s, p, "council");
                page(p, s, new String[][]{{"Do you agree with them?", "council_agree", "Their honest view"}, {"What would you have done?", "council_instead", "The road not taken"},
                        {"Do you trust the O5?", "council_trust", "Faith in the Council"}, {"And the Administrator?", "council_admin", "What they think of the Administrator"}});
                return true;
            }
            case "myth_believe" -> {
                Talk.you(p, "Do you believe it?");
                Talk.say(p, s, Clearance.RAW + (trust >= 5 ? "...Between us? Yes. A memo once came back with a signature I'd never seen, and my supervisor went white. That was enough." : "Believe it? I believe in my pay cheque and the blast doors. The rest is canteen talk."));
                menu(p, s);
                return true;
            }
            case "myth_who" -> {
                Talk.you(p, "Who told you about it?");
                Talk.say(p, s, Clearance.RAW + "Everyone and no one. It's the first thing you hear at orientation, from the person sitting next to you, never from the people running it.");
                menu(p, s);
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
                Memory.Secret sec = null;
                for (Memory.Secret x : Memory.secrets(s, p)) if (Memory.get(s, p, "s_" + x.id()).isEmpty()) { sec = x; break; }
                if (sec == null) {
                    Talk.say(p, s, "That's everything I know. Really. If I hear anything else, you'll be the first.");
                    menu(p, s);
                    return true;
                }
                Talk.say(p, s, sec.text());
                bump(s, p, "secret");
                Dialogue.clearOptions(p);
                subtitle(p, s);
                for (int i = 0; i < sec.replies().size(); i++) Talk.ans(p, s, sec.replies().get(i).label(), "sr_" + sec.id() + "_" + i, "They'll remember what you said");
                topics(p, s);
                return true;
            }
            case "listen_hard", "listen_thanks", "listen_job" -> {
                Talk.you(p, switch (what) { case "listen_hard" -> "That must have been hard."; case "listen_thanks" -> "Thank you for telling me."; default -> "That's the job, I suppose."; });
                Memory.count(s, p, "listened");
                Memory.put(s, p, "story_reply", what.substring(7));
                s.getPersistentData().putInt(tkey(p), Math.max(0, Math.min(10, trust + (what.equals("listen_job") ? 0 : 1))));
                Talk.say(p, s, switch (what) {
                    case "listen_hard" -> Talk.pick(r, "It was. Thank you for saying it.", "Some days it still is.", "...Yes. It was.");
                    case "listen_thanks" -> Talk.pick(r, "Thank you for asking. People don't.", "It's nice to tell someone.");
                    default -> switch (t) {
                        case CYNICAL -> "That's what they tell us.";
                        case EARNEST -> "I suppose it is. I'd still rather it wasn't.";
                        default -> "Right. The job.";
                    };
                });
                page(p, s, new String[][]{{"And then what happened?", "story_more", "The next chapter"}, {"Do you have family?", "story_family", "Life outside the walls"}});
                return true;
            }
            default -> {
                if (what.startsWith("op_")) {
                    List<Memory.Reply> rs = OPENING.remove(p.getUUID());
                    int n;
                    try { n = Integer.parseInt(what.substring(3)); } catch (NumberFormatException e) { n = -1; }
                    if (rs == null || n < 0 || n >= rs.size()) { menu(p, s); return true; }
                    Memory.Reply rp = rs.get(n);
                    s.getPersistentData().putInt(tkey(p), Math.max(0, Math.min(10, trust + rp.trust())));
                    if (rp.remember().startsWith("→")) {
                        // the reply leads into a topic (which says your line itself)
                        return handle(p, s, rp.remember().substring(1));
                    }
                    Talk.you(p, rp.label());
                    if (!rp.reaction().isEmpty()) Talk.say(p, s, rp.reaction());
                    switch (rp.remember()) {
                        case "!heal" -> {
                            p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.REGENERATION, 20 * 12, 1));
                            p.removeEffect(net.minecraft.world.effect.MobEffects.POISON);
                        }
                        case "!flee" -> { net.minecraft.world.entity.Mob loose = Talk.anomalyNear(s, 24); s.flee(loose != null ? loose.position() : p.position()); }
                        case "!hold" -> s.order("stay", p);
                        case "!shelter" -> s.shelter();
                        case "!work" -> s.order("post", p);
                        case "!medical" -> s.toMedical();
                        case "!dayoff" -> s.dayOff();
                        case "!report" -> s.callGuard(p, "is keeping an eye on you.");
                        case "!food" -> { handle(p, s, "op_food_direct"); return true; }
                        case "" -> {}
                        default -> {
                            if (rp.remember().startsWith("!soc:")) { if (Social.code(p, s, rp.remember())) return true; }
                            else Memory.put(s, p, "op_" + rp.remember(), "1");
                        }
                    }
                    menu(p, s);
                    return true;
                }
                if (!what.startsWith("sr_")) return false;
                int us = what.lastIndexOf('_');
                String id = what.substring(3, us);
                int n;
                try { n = Integer.parseInt(what.substring(us + 1)); } catch (NumberFormatException e) { return false; }
                for (Memory.Secret sec : Memory.secrets(s, p)) {
                    if (!sec.id().equals(id) || n < 0 || n >= sec.replies().size()) continue;
                    Memory.Reply rp = sec.replies().get(n);
                    Talk.you(p, rp.label());
                    Talk.say(p, s, rp.reaction());
                    Memory.put(s, p, "s_" + id, rp.remember());
                    if (rp.remember().equals("summoned")) s.callGuard(p, "has been sent to see you. They saw the Gate Guardian look at them.");
                    if (rp.remember().equals("recruiter")) Memory.put(s, p, "asking", "recruiter");
                    s.getPersistentData().putInt(tkey(p), Math.max(0, Math.min(10, trust + rp.trust())));
                    Dialogue.clearOptions(p);
                    subtitle(p, s);
                    Talk.ans(p, s, "Anything else off the record?", "secret", "Another secret");
                    topics(p, s);
                    return true;
                }
                return false;
            }
        }
    }

    /** A gift: what it means depends on what it is, who they are, and how often you've done it today. */
    private static void gift(ServerPlayer p, FoundationStaff s, ItemStack held, Random r) {
        String id = BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
        String path = id.substring(id.indexOf(':') + 1);
        long day = s.level().getDayTime() / 24000;
        String k = "bp_gift_" + p.getUUID() + "_" + path;
        boolean again = s.getPersistentData().getLong(k) == day + 1;
        int delta;
        String line;
        boolean food = held.has(net.minecraft.core.component.DataComponents.FOOD);
        boolean flower = held.is(net.minecraft.tags.ItemTags.FLOWERS);
        boolean precious = path.contains("diamond") || path.contains("emerald") || path.contains("gold_ingot") || path.contains("amethyst");
        boolean gun = id.startsWith("scp_unleashed:") && (path.contains("pistol") || path.contains("rifle") || path.contains("shotgun") || path.contains("revolver"));
        if (again) { delta = 0; line = "Another one? You don't have to keep giving me things, you know."; }
        else if (flower) { delta = 2; line = s.isFemale() ? "Flowers? In this place? ...Thank you. I'll put them on my desk." : "Flowers. Nobody's ever given me flowers. I don't know what to say."; }
        else if (food) { delta = 1; line = Scene.tier(p) == Scene.Tier.DCLASS ? "You're giving ME food? You need it more than I do. ...Thank you." : Talk.pick(r, "Real food. Not the canteen stuff. Thank you.", "I skipped lunch. You're a lifesaver."); }
        else if (precious) { delta = 2; line = "This is... far too much. I'll keep it somewhere safe."; }
        else if (gun && s.role() == FoundationStaff.Role.GUARD) { delta = 2; line = "Now that's a gift I'll use. Thank you."; s.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, held.copyWithCount(1)); }
        else if (path.contains("dirt") || path.contains("rotten") || path.contains("poisonous") || path.contains("spider_eye")) { delta = -2; line = "...Is this a joke? It's not funny."; }
        else { delta = 0; line = Talk.pick(r, "Oh. Thank you. I'm not sure what I'll do with it.", "That's... thoughtful. I think."); }
        held.shrink(1);
        s.getPersistentData().putLong(k, day + 1);
        s.getPersistentData().putInt(tkey(p), Math.max(0, Math.min(10, trust(s, p) + delta)));
        Memory.count(s, p, "gifts");
        Talk.say(p, s, line);
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
        String sup = it.supporters().isEmpty() || Scene.tier(p).ordinal() < Scene.Tier.DIRECTOR.ordinal() ? "" : " The ones who backed it: " + it.supporters().stream().map(x -> "O5-" + x).reduce((a, b) -> a + ", " + b).orElse("") + ".";
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
            case SECRETARY -> "Your calendar, your correspondence, and keeping the Council from walking in unannounced.";
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
