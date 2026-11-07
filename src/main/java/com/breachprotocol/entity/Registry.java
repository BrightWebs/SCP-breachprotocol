package com.breachprotocol.entity;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

/**
 * The Foundation's personnel registry (MCA's civil registry and family tree in one): everyone who
 * works for the Foundation, and everyone they love outside it. Births, marriages, deaths, hires and
 * appointments are logged. Each record carries a genome (so children look like their parents) and
 * traits (some of them inherited).
 */
public final class Registry extends SavedData {
    public static final int YEAR = FoundationStaff.YEAR, GENES = 24;

    public static final class Rec {
        public UUID id;
        public String name = "", status = "offsite", job = "", site = "", rel = "single", genome = "", traits = "", note = "", cause = "";
        public boolean female, player;
        public long born, died, due, relSince;
        public UUID mother, father, spouse, partner; // partner: dating or engaged, before marriage

        public int age(long now) { return (int) Math.max(0, ((died > 0 ? died : now) - born) / YEAR); }

        public boolean alive() { return died == 0; }

        public EnumSet<Traits> traitSet() { return Traits.read(traits); }

        public boolean onStaff() { return status.equals("active"); }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", id);
            t.putString("name", name); t.putString("status", status); t.putString("job", job); t.putString("site", site); t.putString("rel", rel);
            t.putString("genome", genome); t.putString("traits", traits); t.putString("note", note); t.putString("cause", cause);
            t.putBoolean("female", female); t.putBoolean("player", player);
            t.putLong("born", born); t.putLong("died", died); t.putLong("due", due); t.putLong("relSince", relSince);
            if (mother != null) t.putUUID("mother", mother);
            if (father != null) t.putUUID("father", father);
            if (spouse != null) t.putUUID("spouse", spouse);
            if (partner != null) t.putUUID("partner", partner);
            return t;
        }

        static Rec load(CompoundTag t) {
            Rec r = new Rec();
            r.id = t.getUUID("id");
            r.name = t.getString("name"); r.status = t.getString("status"); r.job = t.getString("job"); r.site = t.getString("site"); r.rel = t.getString("rel");
            r.genome = t.getString("genome"); r.traits = t.getString("traits"); r.note = t.getString("note"); r.cause = t.getString("cause");
            r.female = t.getBoolean("female"); r.player = t.getBoolean("player");
            r.born = t.getLong("born"); r.died = t.getLong("died"); r.due = t.getLong("due"); r.relSince = t.getLong("relSince");
            if (t.hasUUID("mother")) r.mother = t.getUUID("mother");
            if (t.hasUUID("father")) r.father = t.getUUID("father");
            if (t.hasUUID("spouse")) r.spouse = t.getUUID("spouse");
            if (t.hasUUID("partner")) r.partner = t.getUUID("partner");
            return r;
        }
    }

    final Map<UUID, Rec> people = new LinkedHashMap<>();
    final LinkedList<String> log = new LinkedList<>();

    public static final SavedData.Factory<Registry> FACTORY = new SavedData.Factory<>(Registry::new, Registry::load, null);

    static Registry load(CompoundTag t, HolderLookup.Provider p) {
        Registry r = new Registry();
        for (Tag x : t.getList("people", Tag.TAG_COMPOUND)) { Rec e = Rec.load((CompoundTag) x); r.people.put(e.id, e); }
        for (Tag x : t.getList("log", Tag.TAG_STRING)) r.log.add(x.getAsString());
        return r;
    }

    @Override
    public CompoundTag save(CompoundTag t, HolderLookup.Provider p) {
        ListTag l = new ListTag();
        for (Rec e : people.values()) l.add(e.save());
        t.put("people", l);
        ListTag g = new ListTag();
        for (String s : log) g.add(StringTag.valueOf(s));
        t.put("log", g);
        return t;
    }

    public static Registry get(MinecraftServer s) { return s.overworld().getDataStorage().computeIfAbsent(FACTORY, "breachprotocol_registry"); }

    public static Rec find(MinecraftServer s, UUID id) { return id == null ? null : get(s).people.get(id); }

    /** An entry in the civil log. */
    public static void log(MinecraftServer s, String text) {
        Registry r = get(s);
        long day = s.overworld().getDayTime() / 24000 + 1;
        r.log.addFirst("Day " + day + ": " + text);
        while (r.log.size() > 120) r.log.removeLast();
        r.setDirty();
    }

    public static List<String> recentLog(MinecraftServer s, int n) { List<String> l = get(s).log; return new ArrayList<>(l.subList(0, Math.min(n, l.size()))); }

    // ================================================================== genomes

    static String genomeOf(Person p) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < GENES; i++) b.append(String.format("%02x", Math.min(255, (int) (p.gene(i) * 255))));
        return b.toString();
    }

    static String genomeOf(UUID id) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < GENES; i++) b.append(String.format("%02x", Math.min(255, (int) (Person.geneOf(id, i) * 255))));
        return b.toString();
    }

    static float gene(String genome, int i) {
        if (genome == null || genome.length() < (i + 1) * 2) return -1;
        try { return Integer.parseInt(genome.substring(i * 2, i * 2 + 2), 16) / 255F; } catch (NumberFormatException e) { return -1; }
    }

    /** MCA-style inheritance: each gene a blend of the parents', with a little mutation. Personality (20) is their own. */
    static String mix(String a, String b, UUID self, Random r) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < GENES; i++) {
            float ga = gene(a, i), gb = gene(b, i), own = Person.geneOf(self, i);
            float v;
            if (i == 20 || ga < 0 || gb < 0) v = own;
            else {
                float w = r.nextFloat();
                v = ga * w + gb * (1 - w);
                v = v * 0.9F + own * 0.1F; // a touch of their own
            }
            out.append(String.format("%02x", Math.min(255, Math.max(0, (int) (v * 255)))));
        }
        return out.toString();
    }

    // ================================================================== records

    /** The record for a member of staff (created the first time they're seen). */
    public static Rec of(FoundationStaff s) {
        MinecraftServer srv = s.getServer();
        Registry r = get(srv);
        Rec e = r.people.get(s.getUUID());
        long now = srv.overworld().getGameTime();
        if (e == null) {
            e = new Rec();
            e.id = s.getUUID();
            e.genome = s.genome().isEmpty() ? genomeOf(s) : s.genome();
            e.traits = Traits.write(s.traits());
            r.people.put(e.id, e);
        }
        e.name = s.getName().getString();
        e.female = s.isFemale();
        e.born = now - (long) s.age() * YEAR - YEAR / 2;
        if (e.alive() && !e.status.equals("applicant")) e.status = "active";
        e.job = s.unit() != null ? s.unit().title : s.role().title;
        com.breachprotocol.career.Sites.Site site = com.breachprotocol.career.Sites.at(s.level(), s.blockPosition());
        if (site != null) e.site = site.name;
        r.setDirty();
        return e;
    }

    /** You, in the registry (so you can marry, and have family). */
    public static Rec player(ServerPlayer p) {
        Registry r = get(p.server);
        Rec e = r.people.get(p.getUUID());
        if (e == null) {
            e = new Rec();
            e.id = p.getUUID();
            e.player = true;
            e.born = p.server.overworld().getGameTime() - 35L * YEAR;
            e.genome = genomeOf(p.getUUID());
            r.people.put(e.id, e);
        }
        e.name = p.getGameProfile().getName();
        e.status = "active";
        e.job = com.breachprotocol.career.Career.rank(p).title;
        r.setDirty();
        return e;
    }

    /** Someone outside the Foundation (a spouse, a parent, a child), of a given age. */
    public static Rec outsider(MinecraftServer s, String name, boolean female, int age, String status, String job) {
        Registry r = get(s);
        Rec e = new Rec();
        e.id = UUID.randomUUID();
        e.name = name;
        e.female = female;
        e.born = s.overworld().getGameTime() - (long) age * YEAR - new Random().nextInt(YEAR);
        e.status = status;
        e.job = job;
        e.genome = genomeOf(e.id);
        e.traits = Traits.write(Traits.natural(e.id));
        r.people.put(e.id, e);
        r.setDirty();
        return e;
    }

    /** A baby: genome and traits from both parents. */
    public static Rec born(MinecraftServer s, Rec mother, Rec father, String name, boolean female) {
        Registry r = get(s);
        Random rnd = new Random();
        Rec e = new Rec();
        e.id = UUID.randomUUID();
        e.name = name;
        e.female = female;
        e.born = s.overworld().getGameTime();
        e.status = "child";
        e.mother = mother.id;
        e.father = father == null ? null : father.id;
        e.genome = mix(mother.genome, father == null ? mother.genome : father.genome, e.id, rnd);
        e.traits = Traits.write(Traits.inherit(mother.traitSet(), father == null ? EnumSet.noneOf(Traits.class) : father.traitSet(), rnd));
        e.note = "lives with the family";
        r.people.put(e.id, e);
        r.setDirty();
        return e;
    }

    // ================================================================== the family tree

    public static List<Rec> children(MinecraftServer s, UUID id) {
        List<Rec> out = new ArrayList<>();
        for (Rec e : get(s).people.values()) if (id.equals(e.mother) || id.equals(e.father)) out.add(e);
        out.sort(Comparator.comparingLong(e -> e.born));
        return out;
    }

    public static List<Rec> parents(MinecraftServer s, Rec e) {
        List<Rec> out = new ArrayList<>();
        Rec m = find(s, e.mother), f = find(s, e.father);
        if (m != null) out.add(m);
        if (f != null) out.add(f);
        return out;
    }

    public static List<Rec> siblings(MinecraftServer s, Rec e) {
        List<Rec> out = new ArrayList<>();
        if (e.mother == null && e.father == null) return out;
        for (Rec o : get(s).people.values())
            if (o != e && (e.mother != null && e.mother.equals(o.mother) || e.father != null && e.father.equals(o.father))) out.add(o);
        return out;
    }

    /** Close relatives can't marry. */
    public static boolean related(MinecraftServer s, Rec a, Rec b) {
        if (a.id.equals(b.mother) || a.id.equals(b.father) || b.id.equals(a.mother) || b.id.equals(a.father)) return true;
        if (siblings(s, a).contains(b)) return true;
        for (Rec pa : parents(s, a)) for (Rec pb : parents(s, b)) if (pa == pb || siblings(s, pa).contains(pb)) return true;
        return false;
    }

    public static void dirty(MinecraftServer s) { get(s).setDirty(); }
}
