package com.breachprotocol.entity;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.Set;

/** Hands out names nobody else in the world has (staff full names, D-Class numbers). */
public final class Names {
    private Names() {}

    private static final String[] FIRST = {"Amara", "Benedikt", "Chiara", "Dmitri", "Elena", "Farid", "Greta", "Hiroshi", "Ines", "Jonas",
            "Kemi", "Lukas", "Mira", "Nikolai", "Olusegun", "Priya", "Quentin", "Rosa", "Samir", "Tove",
            "Ulrich", "Valentina", "Wen", "Ximena", "Yusuf", "Zofia", "Adele", "Bruno", "Cyrus", "Daria",
            "Emeka", "Freya", "Gideon", "Hana", "Ivo", "Jasmine", "Kofi", "Leona", "Mateo", "Nadia",
            "Oren", "Paloma", "Rafael", "Sigrid", "Tomas", "Uma", "Viktor", "Willa"};
    private static final String[] LAST = {"Mbeki", "Okafor", "Lindqvist", "Tanaka", "Morales", "Kowalski", "Haddad", "Novak", "Reyes", "Ivanova",
            "Everwood", "Harrow", "Vance", "Mercer", "Osei", "Achterberg", "Bianchi", "Castellanos", "Dubois", "Eriksen",
            "Fontaine", "Grünewald", "Halvorsen", "Iwasaki", "Jovanović", "Kaur", "Laine", "Moreau", "Nakamura", "O'Rourke",
            "Petrov", "Quaid", "Rasmussen", "Sato", "Thorne", "Ueda", "Varga", "Whitlock", "Yilmaz", "Zielinski",
            "Abernathy", "Brandt", "Carvalho", "Delacroix", "Eze", "Falk", "Gallagher", "Hosseini", "Ingram", "Jankowski",
            "Keane", "Lund", "Marchetti", "Ndiaye", "Ortega", "Pereira", "Rahman", "Sokolov", "Teague", "Wójcik"};

    private static final String[] WOMEN = {"Amara", "Chiara", "Elena", "Greta", "Ines", "Kemi", "Mira", "Priya", "Rosa", "Tove", "Valentina", "Ximena",
            "Zofia", "Adele", "Daria", "Freya", "Hana", "Jasmine", "Leona", "Nadia", "Paloma", "Sigrid", "Uma", "Willa", "Clara", "Isabelle", "Margot",
            "Naomi", "Celeste", "Vivienne", "Aiko", "Lucia", "Helena", "Ophelia", "Simone", "Talia"};

    /** A woman's first name and a surname, unused anywhere in this world. */
    public static String woman(MinecraftServer s, RandomSource r) {
        Data d = data(s);
        for (int i = 0; ; i++) {
            String n = WOMEN[r.nextInt(WOMEN.length)] + " " + (i > 400 ? (char) ('A' + r.nextInt(26)) + ". " : "") + LAST[r.nextInt(LAST.length)];
            if (d.used.add(n)) { d.setDirty(); return n; }
        }
    }

    public static final class Data extends SavedData {
        final Set<String> used = new HashSet<>();
        public static final SavedData.Factory<Data> FACTORY = new SavedData.Factory<>(Data::new, Data::load, null);

        static Data load(CompoundTag t, HolderLookup.Provider p) {
            Data d = new Data();
            for (Tag x : t.getList("used", Tag.TAG_STRING)) d.used.add(x.getAsString());
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag t, HolderLookup.Provider p) {
            ListTag l = new ListTag();
            for (String s : used) l.add(StringTag.valueOf(s));
            t.put("used", l);
            return t;
        }
    }

    private static Data data(MinecraftServer s) { return s.overworld().getDataStorage().computeIfAbsent(Data.FACTORY, "breachprotocol_names"); }

    /** A first and last name no one else in this world has been given. */
    public static String person(MinecraftServer s, RandomSource r) {
        Data d = data(s);
        for (int i = 0; i < 400; i++) {
            String n = FIRST[r.nextInt(FIRST.length)] + " " + LAST[r.nextInt(LAST.length)];
            if (d.used.add(n)) { d.setDirty(); return n; }
        }
        // every pairing taken: add a middle initial
        for (int i = 0; ; i++) {
            String n = FIRST[r.nextInt(FIRST.length)] + " " + (char) ('A' + r.nextInt(26)) + ". " + LAST[r.nextInt(LAST.length)];
            if (d.used.add(n)) { d.setDirty(); return n; }
        }
    }

    /** A D-Class designation (D-1000 to D-9999) no one else has. */
    public static String dclass(MinecraftServer s, RandomSource r) {
        Data d = data(s);
        for (int i = 0; ; i++) {
            String n = "D-" + (1000 + r.nextInt(9000)) + (i > 2000 ? "-" + i : "");
            if (d.used.add(n)) { d.setDirty(); return n; }
        }
    }

    /** Claims a name that already exists (returns false if someone else has it). */
    public static boolean claim(MinecraftServer s, String n) {
        Data d = data(s);
        if (!d.used.add(n)) return false;
        d.setDirty();
        return true;
    }
}
