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

import java.util.HashMap;
import java.util.Map;
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

    /** Dossier "scp_unleashed:scp_096" -> entity "scp096_shy_guy"; "scp_001_day" -> "scp001_day_*"; "z_0004" -> "z0004_*". */
    private static EntityType<?> typeFor(String docId) {
        ResourceLocation doc = ResourceLocation.tryParse(docId);
        if (doc == null) return null;
        String prefix;
        Matcher m = Pattern.compile("^scp_(\\d+)(?:_([a-z]+))?$").matcher(doc.getPath());
        Matcher z = Pattern.compile("^z_(\\d+)$").matcher(doc.getPath());
        if (m.matches()) prefix = "scp" + m.group(1) + (m.group(2) == null ? "" : "_" + m.group(2));
        else if (z.matches()) prefix = "z" + z.group(1);
        else return null;
        EntityType<?> best = null;
        int bestLen = Integer.MAX_VALUE;
        for (EntityType<?> t : BuiltInRegistries.ENTITY_TYPE) {
            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(t);
            if (!doc.getNamespace().equals(id.getNamespace())) continue;
            String p = id.getPath();
            if (!(p.equals(prefix) || p.startsWith(prefix + "_"))) continue;
            if (m.matches() && m.group(2) == null && p.length() > prefix.length() && Character.isDigit(p.charAt(prefix.length() + 1)) && !p.startsWith(prefix + "_2")) continue;
            if (p.contains("portal") || p.contains("seat") || p.contains("restraint")) continue;
            if (p.length() < bestLen) { best = t; bestLen = p.length(); }
        }
        return best;
    }

    private static LivingEntity entity(String docId) {
        if (FAILED.containsKey(docId)) return null;
        LivingEntity cached = CACHE.get(docId);
        if (cached != null) return cached;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        try {
            EntityType<?> t = typeFor(docId);
            Entity e = t == null ? null : t.create(mc.level);
            if (e instanceof LivingEntity le) {
                CACHE.put(docId, le);
                return le;
            }
        } catch (Throwable ignored) {}
        FAILED.put(docId, NONE);
        return null;
    }

    public static boolean has(String docId) {
        return entity(docId) != null;
    }

    /** Draws the model turning in place, centred in the box. Returns false if there's no model. */
    public static boolean draw(GuiGraphics g, String docId, int x, int y, int w, int h) {
        LivingEntity le = entity(docId);
        if (le == null) return false;
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
            CACHE.remove(docId);
            FAILED.put(docId, NONE);
            return false;
        }
    }
}
