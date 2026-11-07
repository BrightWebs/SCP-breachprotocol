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

    public enum Kind { DECISION, BREACH, RIFT_CLOSED, RIFT_LOST, MTF, SHYGUY_CALMED, SHYGUY_KILLED, BANISHED, KNOWLEDGE, PROMOTION, SEAT_VOTE }

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
        return switch (k) {
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
            default -> "the Council decided: " + option.toLowerCase(Locale.ROOT);
        };
    }

    /** The one-line gist of an event, for staff who mention it in passing. */
    public static String gist(Item i) { return i.headline(); }

    public static String ago(MinecraftServer s, Item i) {
        long t = s.overworld().getGameTime() - i.time();
        if (t < 2400) return "just now";
        if (t < 24000) return "earlier today";
        if (t < 48000) return "yesterday";
        return (t / 24000) + " days ago";
    }
}
