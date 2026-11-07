package com.breachprotocol.career;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

/**
 * What everyone at the Foundation is talking about: the world's recent events, newest first.
 * Council and phone decisions, breaches, rifts, deployments, the Garden, promotions. NPCs read it to
 * decide what they say, and how they feel about it.
 */
public final class WorldNews {
    private WorldNews() {}

    public enum Kind { DECISION, BREACH, RIFT_CLOSED, RIFT_LOST, MTF, SHYGUY_CALMED, SHYGUY_KILLED, BANISHED, KNOWLEDGE, PROMOTION, SEAT_VOTE, AGING, TREATMENT, DEATH, HIRE, ATTACK, FORCES }

    /** One event. {@code mood} is how good it was for the Foundation (-10..10), {@code supporters} the Overseer seats that backed it. */
    public record Item(Kind kind, String id, String headline, int mood, long time, String who, Set<Integer> supporters) {}

    public static final class Data extends SavedData {
        final LinkedList<Item> items = new LinkedList<>();
        public static final SavedData.Factory<Data> FACTORY = new SavedData.Factory<>(Data::new, Data::load, null);

        static Data load(CompoundTag t, HolderLookup.Provider p) {
            Data d = new Data();
            for (Tag e : t.getList("items", Tag.TAG_COMPOUND)) {
                CompoundTag c = (CompoundTag) e;
                Set<Integer> sup = new HashSet<>();
                for (int s : c.getIntArray("sup")) sup.add(s);
                try {
                    d.items.add(new Item(Kind.valueOf(c.getString("k")), c.getString("id"), c.getString("h"), c.getInt("m"), c.getLong("t"), c.getString("w"), sup));
                } catch (IllegalArgumentException ignored) {}
            }
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag t, HolderLookup.Provider p) {
            ListTag l = new ListTag();
            for (Item i : items) {
                CompoundTag c = new CompoundTag();
                c.putString("k", i.kind().name());
                c.putString("id", i.id());
                c.putString("h", i.headline());
                c.putInt("m", i.mood());
                c.putLong("t", i.time());
                c.putString("w", i.who());
                c.putIntArray("sup", i.supporters().stream().mapToInt(Integer::intValue).toArray());
                l.add(c);
            }
            t.put("items", l);
            return t;
        }
    }

    private static Data data(MinecraftServer s) { return s.overworld().getDataStorage().computeIfAbsent(Data.FACTORY, "breachprotocol_news"); }

    public static void record(MinecraftServer s, Kind kind, String id, String headline, int mood, String who, Set<Integer> supporters) {
        Data d = data(s);
        d.items.addFirst(new Item(kind, id, headline, Math.max(-10, Math.min(10, mood)), s.overworld().getGameTime(), who, supporters == null ? Set.of() : supporters));
        while (d.items.size() > 40) d.items.removeLast();
        d.setDirty();
    }

    public static void record(MinecraftServer s, Kind kind, String id, String headline, int mood, String who) { record(s, kind, id, headline, mood, who, null); }

    /** Events from the last {@code days} Minecraft days, newest first. */
    public static List<Item> recent(MinecraftServer s, int days) {
        long now = s.overworld().getGameTime();
        List<Item> out = new ArrayList<>();
        for (Item i : data(s).items) if (now - i.time() <= 24000L * days) out.add(i);
        return out;
    }

    public static Item latest(MinecraftServer s, Kind kind) {
        for (Item i : data(s).items) if (i.kind() == kind) return i;
        return null;
    }

    public static Item latest(MinecraftServer s) { return data(s).items.isEmpty() ? null : data(s).items.getFirst(); }

    /** The overall mood of the last few days (-10..10). */
    public static int morale(MinecraftServer s) {
        int sum = 0, n = 0;
        for (Item i : recent(s, 3)) { sum += i.mood(); n++; }
        return n == 0 ? 0 : Math.max(-10, Math.min(10, sum * 2 / Math.max(1, n)));
    }

    /** How a decision is talked about around the site. */
    public static String headline(String template, String option) {
        String k = template + "|" + option.toLowerCase(Locale.ROOT);
        if (template.startsWith("w_short_")) {
            String site = template.substring(8);
            return option.toLowerCase(Locale.ROOT).startsWith("replace") ? "the Council sent reinforcements to bring " + site + " back to strength" : site + " was left short of guards until the next review";
        }
        return switch (k) {
            case "w_attack|double the garrison" -> "the Council doubled the garrison after the attack";
            case "w_attack|send alpha-1 after them" -> "Alpha-1 was sent to hunt down the attackers";
            case "w_attack|open talks with them" -> "the Council opened ceasefire talks with the attackers";
            case "w_losses|a memorial, and new hires" -> "there's to be a memorial for this week's dead, and new hires to fill their posts";
            case "w_losses|tighten safety protocols" -> "safety protocols were tightened at every site";
            case "w_losses|keep working. we can't stop" -> "the Council told everyone to keep working through the losses";
            case "w_breach|rebuild the containment chamber" -> "the chamber that breached is being rebuilt";
            case "w_breach|discipline the shift on duty" -> "the shift on duty during the breach was disciplined";
            case "w_breach|double the guard on that cell" -> "Epsilon-11 now stands guard on the cell that breached";
            case "w_stability|lock down every site for a day" -> "every site was locked down for a day";
            case "w_stability|amnestic sweep of the region" -> "there was an amnestic sweep of the whole region";
            case "w_stability|ride it out" -> "the Council chose to ride out the instability";
            case "w_rift|only mtf close rifts from now on" -> "only task forces may close rifts from now on";
            case "w_rift|better equipment for the crews" -> "rift crews are getting better equipment";
            case "w_rift|d-class are expendable" -> "the Council called the lost rift crew expendable";
            case "termination_096|sign it" -> "the Council signed the termination order on SCP-096";
            case "termination_096|refuse" -> "the termination order on SCP-096 was refused";
            case "mtf_expand|approve" -> "Epsilon-11 is being stationed with us permanently";
            case "mtf_expand|deny" -> "the request for a permanent Epsilon-11 posting was denied";
            case "budget|fund containment" -> "the money went to containment, not research";
            case "budget|fund research" -> "research got the funding and containment got the bill";
            case "insurgency_spy|amnestic sweep" -> "there was an amnestic sweep for an Insurgency spy";
            case "insurgency_spy|turn the spy" -> "the Council tried to turn an Insurgency spy";
            case "cafeteria_999|approve" -> "SCP-999 is allowed in the staff cafeteria now";
            case "cafeteria_999|deny" -> "SCP-999 was kept out of the cafeteria";
            case "cell_173|approve cleaning" -> "they cleaned SCP-173's cell, three D-Class and everyone watching";
            case "cell_173|postpone" -> "SCP-173's cell cleaning was put off again";
            case "femur_breaker|authorise it" -> "the femur breaker was used on SCP-106";
            case "femur_breaker|find another way" -> "the Council refused the femur breaker for SCP-106";
            case "goc_jointop|share intel" -> "we traded intel with the Global Occult Coalition";
            case "goc_jointop|refuse" -> "the Council turned down the Coalition";
            case "declassify_207|declassify" -> "SCP-207's files were declassified";
            case "declassify_207|keep it sealed" -> "SCP-207 stays sealed";
            case "network_code|purge" -> "they purged the anomalous code from the network";
            case "network_code|study it" -> "someone decided to study the code living in our network";
            case "gate_stirs|seal the levels" -> "Site-01's lower levels were sealed after the Gate Guardian moved";
            case "gate_stirs|ask it" -> "someone actually spoke to the Gate Guardian";
            case "dclass_supply|death row only" -> "D-Class recruitment stays death row only";
            case "dclass_supply|widen the net" -> "the D-Class net has been widened";
            case "global_lockdown|global lockdown" -> "every site went into global lockdown";
            case "global_lockdown|let sites handle it" -> "the sites were left to handle the activity themselves";
            case "global_lockdown|send alpha-1" -> "Alpha-1 was sent out";
            default -> {
                Calls.Template t = Calls.get(template);
                String about = t != null ? Calls.title(t) : Calls.title(new Calls.Template(template, 0, false, "", java.util.List.of()));
                yield "the Council ruled on " + (about.equals("Council business") ? "a matter before it" : "the matter of " + Character.toLowerCase(about.charAt(0)) + about.substring(1)) + ": " + option.toLowerCase(Locale.ROOT);
            }
        };
    }

    /** The one-line gist of an event, for staff who mention it in passing. */
    public static String gist(Item i) { return i.headline(); }

    public static String ago(MinecraftServer s, Item i) { return ago(s, i.time()); }

    /** Natural time words: "a few minutes ago", "this morning", "yesterday", "three days ago", "last week". */
    public static String ago(MinecraftServer srv, long time) {
        long now = srv.overworld().getGameTime(), t = now - time;
        if (t < 1200) return "a few minutes ago";
        if (t < 6000) return "within the hour";
        long dayNow = srv.overworld().getDayTime();
        if (t < 24000 && (dayNow % 24000) >= t) {
            int h = (int) (((dayNow - t) % 24000) / 1000 + 6) % 24;
            return h < 12 ? "this morning" : h < 18 ? "this afternoon" : "this evening";
        }
        long days = Math.max(1, t / 24000);
        if (days == 1) return "yesterday";
        if (days < 7) return new String[]{"", "", "two", "three", "four", "five", "six"}[(int) days] + " days ago";
        if (days < 14) return "last week";
        return days / 7 + " weeks ago";
    }

    /** What a decision was about, for "On the SCP-096 termination order, ..." */
    public static String topic(Item i) {
        String tpl = i.id().contains("|") ? i.id().substring(0, i.id().indexOf('|')) : i.id();
        Calls.Template t = Calls.get(tpl);
        String title = t != null ? Calls.title(t) : tpl.startsWith("w_short_") ? "Under-strength garrison" : Calls.title(new Calls.Template(tpl, 0, false, "", java.util.List.of()));
        return title.equals("Council business") ? "the last matter before the Council" : "the " + Character.toLowerCase(title.charAt(0)) + title.substring(1);
    }
}
