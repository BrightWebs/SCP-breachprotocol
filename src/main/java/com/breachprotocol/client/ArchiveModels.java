package com.breachprotocol.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.breachprotocol.BreachProtocol;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A slowly turning model of the anomaly a dossier describes, drawn by SCP Unleashed's own entity
 * renderer (like the player preview in the inventory). One display copy per dossier, never spawned.
 */
public final class ArchiveModels {
    private ArchiveModels() {}

    private static final Map<String, LivingEntity> CACHE = new HashMap<>();
    private static final Object NONE = new Object();
    private static final Map<String, Object> FAILED = new HashMap<>();

    /** Remembers each dossier's designation so the number can be used when the file id doesn't match. */
    private static final Map<String, String> DESIGNATION = new HashMap<>();

    public static void designation(String docId, String designation) {
        DESIGNATION.putIfAbsent(docId, designation);
    }

    /**
     * Candidate SCP Unleashed entities for a dossier, best first. "scp_096" or "SCP-096" -> scp096_shy_guy;
     * "scp_001_day" -> scp001_day_*; "z_0004" -> z0004_*; "scp_001_scarlet" -> scarlet_*.
     */
    private static List<EntityType<?>> candidates(String docId) {
        ResourceLocation doc = ResourceLocation.tryParse(docId);
        String path = doc == null ? docId : doc.getPath();
        path = path.substring(path.lastIndexOf('/') + 1).replace(".json", "");
        List<String> prefixes = new ArrayList<>();
        Matcher m = Pattern.compile("^scp_?(\\d+)(?:_([a-z]+))?.*$").matcher(path);
        Matcher z = Pattern.compile("^z_?(\\d+).*$").matcher(path);
        if (path.equals("scp_001_scarlet")) prefixes.add("scarlet_");
        if (m.matches()) {
            if (m.group(2) != null && !m.group(2).equals("j")) prefixes.add("scp" + m.group(1) + "_" + m.group(2));
            prefixes.add("scp" + m.group(1));
        } else if (z.matches()) {
            prefixes.add("z" + z.group(1));
        }
        String des = DESIGNATION.getOrDefault(docId, "");
        Matcher dm = Pattern.compile("(\\d{3,4})").matcher(des);
        if (dm.find()) prefixes.add("scp" + dm.group(1));
        List<EntityType<?>> out = new ArrayList<>();
        // last resort: any SCP Unleashed creature whose id carries the same number (scp173, scp_173_statue ...)
        String num = m.matches() ? m.group(1) : dm.reset().find() ? dm.group(1) : null;
        if (num != null) prefixes.add("#" + num.replaceFirst("^0+(?=\\d{3})", ""));
        for (String prefix : prefixes) {
            List<EntityType<?>> found = new ArrayList<>();
            for (EntityType<?> t : BuiltInRegistries.ENTITY_TYPE) {
                ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(t);
                if (!"scp_unleashed".equals(id.getNamespace())) continue;
                String p = id.getPath();
                boolean match = prefix.startsWith("#") ? p.matches("^(scp_?)" + prefix.substring(1) + "(_.*)?$")
                        : prefix.endsWith("_") ? p.startsWith(prefix) : p.equals(prefix) || p.startsWith(prefix + "_");
                if (!match) continue;
                // scp001 alone shouldn't pick up scp001_day_* etc.; scp049 shouldn't pick scp049_2 first
                if ((prefix.equals("scp001") || prefix.equals("#001")) && p.startsWith("scp001_")) continue;
                if (p.contains("portal") || p.contains("seat") || p.contains("restraint") || p.contains("egg") || p.contains("bolt")
                        || p.contains("thrown") || p.contains("shot") || p.contains("image") || p.contains("field") || p.contains("note")) continue;
                found.add(t);
            }
            found.sort(Comparator.comparingInt(t -> BuiltInRegistries.ENTITY_TYPE.getKey(t).getPath().length()));
            for (EntityType<?> t : found) if (!out.contains(t)) out.add(t);
        }
        return out;
    }

    private static LivingEntity entity(String docId) {
        if (FAILED.containsKey(docId)) return null;
        LivingEntity cached = CACHE.get(docId);
        if (cached != null) return cached;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        for (EntityType<?> t : candidates(docId)) {
            try {
                Entity e = t.create(mc.level);
                if (e instanceof LivingEntity le) {
                    CACHE.put(docId, le);
                    BreachProtocol.LOGGER.info("Archive preview: {} -> {}", docId, BuiltInRegistries.ENTITY_TYPE.getKey(t));
                    return le;
                }
            } catch (Throwable ex) {
                BreachProtocol.LOGGER.warn("Archive preview: could not create {} for {}: {}", BuiltInRegistries.ENTITY_TYPE.getKey(t), docId, ex.toString());
            }
        }
        BreachProtocol.LOGGER.info("Archive preview: no creature for {} (tried {})", docId, candidates(docId));
        PROBLEM.put(docId, candidates(docId).isEmpty() ? "NOT A CREATURE - NO MODEL" : "MODEL UNAVAILABLE");
        FAILED.put(docId, NONE);
        return null;
    }

    private static final Map<String, LivingEntity> BY_TYPE = new HashMap<>();

    /** A display copy of an entity type (never the live one, so its in-world pose isn't touched). */
    private static LivingEntity ofType(String typeId) {
        if (FAILED.containsKey("type:" + typeId)) return null;
        LivingEntity le = BY_TYPE.get(typeId);
        if (le != null) return le;
        Minecraft mc = Minecraft.getInstance();
        ResourceLocation rl = ResourceLocation.tryParse(typeId);
        if (mc.level == null || rl == null) return null;
        try {
            EntityType<?> t = BuiltInRegistries.ENTITY_TYPE.getOptional(rl).orElse(null);
            Entity e = t == null ? null : t.create(mc.level);
            if (e instanceof LivingEntity l) {
                BY_TYPE.put(typeId, l);
                return l;
            }
        } catch (Throwable ex) {
            BreachProtocol.LOGGER.warn("Containment preview: could not create {}: {}", typeId, ex.toString());
        }
        FAILED.put("type:" + typeId, NONE);
        return null;
    }

    public static boolean hasType(String typeId) {
        return ofType(typeId) != null;
    }

    /** Turning model of an entity type, centred in the box. */
    public static boolean drawType(GuiGraphics g, String typeId, int x, int y, int w, int h) {
        LivingEntity le = ofType(typeId);
        return le != null && render(g, le, x, y, w, h, "type:" + typeId);
    }

    public static boolean has(String docId) {
        return entity(docId) != null;
    }

    /** Draws the model turning in place, centred in the box. Returns false if there's no model. */
    public static boolean draw(GuiGraphics g, String docId, int x, int y, int w, int h) {
        LivingEntity le = entity(docId);
        return le != null && render(g, le, x, y, w, h, docId);
    }

    private static final Map<String, String> PROBLEM = new HashMap<>();

    /** Short reason shown in the preview box when nothing can be drawn. */
    public static String lastProblem(String docId) {
        return PROBLEM.getOrDefault(docId, Minecraft.getInstance().level == null ? "LOADING" : "NO SPECIMEN MODEL");
    }

    private static boolean render(GuiGraphics g, LivingEntity le, int x, int y, int w, int h, String key) {
        try {
            float rot = (System.currentTimeMillis() % 14000L) / 14000F * 360F;
            le.yBodyRot = rot; le.yBodyRotO = rot;
            le.setYRot(rot); le.yRotO = rot;
            le.yHeadRot = rot; le.yHeadRotO = rot;
            le.setXRot(0); le.xRotO = 0;
            float bh = Math.max(0.3F, le.getBbHeight()), bw = Math.max(0.3F, le.getBbWidth());
            float scale = Math.min(h * 0.78F / bh, w * 0.6F / bw);
            Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI).rotateX((float) Math.toRadians(-8));
            InventoryScreen.renderEntityInInventory(g, x + w / 2F, y + h / 2F, scale, new Vector3f(0, bh / 2F, 0), pose, null, le);
            return true;
        } catch (Throwable t) {
            BreachProtocol.LOGGER.warn("Preview failed to draw {}", key, t);
            PROBLEM.put(key, "MODEL FAILED TO RENDER");
            CACHE.remove(key);
            FAILED.put(key, NONE);
            return false;
        }
    }
}
