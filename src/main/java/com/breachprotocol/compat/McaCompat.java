package com.breachprotocol.compat;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.fml.ModList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Optional bridge to Minecraft Comes Alive Reborn (mod id "mca"). Everything here is reflection, so
 * Breach Protocol runs exactly the same without MCA; with it, MCA's villagers can come to the
 * Foundation for work, bringing their names, looks (genes), personalities, traits and families.
 */
public final class McaCompat {
    private McaCompat() {}

    private static Boolean present;
    private static Class<?> villagerClass, geneticsClass, familyTreeClass;

    public static boolean present() {
        if (present == null) {
            present = ModList.get() != null && ModList.get().isLoaded("mca");
            if (present) {
                try {
                    villagerClass = Class.forName("net.conczin.mca.entity.VillagerEntityMCA");
                    geneticsClass = Class.forName("net.conczin.mca.entity.ai.Genetics");
                    familyTreeClass = Class.forName("net.conczin.mca.server.world.data.FamilyTree");
                } catch (Throwable t) {
                    present = false;
                }
            }
        }
        return present;
    }

    public static boolean isVillager(Entity e) { return e != null && present() && villagerClass.isInstance(e); }

    private static Object call(Object o, String name, Object... args) {
        if (o == null) return null;
        try {
            for (Method m : o.getClass().getMethods())
                if (m.getName().equals(name) && m.getParameterCount() == args.length) return m.invoke(o, args);
        } catch (Throwable ignored) {}
        return null;
    }

    private static final Map<String, Object> TYPES = new HashMap<>();

    /** One of MCA's genes (Size, Width, Breast, Melanin, Hemoglobin, Eumelanin, Pheomelanin, Skin, Face, EyeBrightness), or -1. */
    public static float gene(Entity e, String field) {
        Object gen = call(e, "getGenetics");
        if (gen == null) return -1;
        try {
            Object type = TYPES.computeIfAbsent(field, k -> {
                try { Field f = geneticsClass.getField(k); return f.get(null); } catch (Throwable t) { return null; }
            });
            if (type == null) return -1;
            Object v = call(gen, "getGene", type);
            return v instanceof Float f ? f : -1;
        } catch (Throwable t) { return -1; }
    }

    public static boolean female(Entity e) {
        Object g = call(call(e, "getGenetics"), "getGender");
        return g != null && g.toString().equalsIgnoreCase("FEMALE");
    }

    /** "friendly", "crabby"... (MCA's personality ids), or "". */
    public static String personality(Entity e) {
        Object pe = call(call(e, "getVillagerBrain"), "getPersonality");
        Object id = call(pe, "getId");
        if (id == null) return "";
        String s = id.toString();
        return s.substring(s.indexOf(':') + 1).toLowerCase(Locale.ROOT);
    }

    /** "BABY", "TODDLER", "CHILD", "TEEN", "ADULT" (or "ADULT" if unknown). */
    public static String ageState(Entity e) {
        Object a = call(e, "getAgeState");
        return a == null ? "ADULT" : a.toString();
    }

    /** MCA trait ids the villager has (e.g. "left_handed", "color_blind"). */
    public static Set<String> traits(Entity e) {
        Set<String> out = new HashSet<>();
        Object tr = call(e, "getTraits");
        Object list = call(tr, "getTraits");
        if (list instanceof Collection<?> c) for (Object t : c) {
            Object id = call(t, "id");
            if (id == null) id = call(t, "getId");
            String s = String.valueOf(id == null ? t : id);
            out.add(s.substring(s.indexOf(':') + 1).toLowerCase(Locale.ROOT));
        }
        return out;
    }

    /** Their family in MCA's family tree: father, mother, partner (each id + name), children's ids and names. */
    public record Family(UUID father, String fatherName, UUID mother, String motherName, UUID partner, String partnerName, Map<UUID, String> children) {}

    public static Family family(ServerLevel l, Entity e) {
        try {
            Object tree = familyTreeClass.getMethod("get", ServerLevel.class).invoke(null, l);
            Object node = call(tree, "getOrCreate", e);
            if (node == null) return null;
            UUID f = (UUID) call(node, "father"), m = (UUID) call(node, "mother"), pa = (UUID) call(node, "partner");
            Map<UUID, String> kids = new LinkedHashMap<>();
            Object ch = call(node, "children");
            if (ch instanceof Collection<?> c) for (Object k : c) if (k instanceof UUID u) kids.put(u, name(tree, u));
            return new Family(valid(f), name(tree, f), valid(m), name(tree, m), valid(pa), name(tree, pa), kids);
        } catch (Throwable t) { return null; }
    }

    private static UUID valid(UUID u) { return u == null || u.getMostSignificantBits() == 0 && u.getLeastSignificantBits() == 0 ? null : u; }

    private static String name(Object tree, UUID u) {
        if (valid(u) == null) return "";
        Object opt = call(tree, "getOrEmpty", u);
        if (opt instanceof Optional<?> o && o.isPresent()) { Object n = call(o.get(), "getName"); return n == null ? "" : n.toString(); }
        return "";
    }
}
