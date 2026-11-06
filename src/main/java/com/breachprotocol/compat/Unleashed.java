package com.breachprotocol.compat;

import com.breachprotocol.entity.Anomaly;
import com.breachprotocol.registry.ModEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;

/**
 * Bridge to SCP Unleashed (Breach Protocol is an addon for it). Everything here works purely by
 * registry ID at runtime - no code or assets from SCP Unleashed are included in this mod.
 */
public final class Unleashed {
    private Unleashed() {}

    public static final String MODID = "scp_unleashed";

    /** Object classes as given on the SCP wiki, for the anomalies the career system cares about. */
    private static final Map<String, String> CLASSES = Map.ofEntries(
            Map.entry("scp999", "Safe"), Map.entry("scp131_pod", "Safe"), Map.entry("scp343", "Safe"), Map.entry("scp105", "Safe"),
            Map.entry("scp066", "Euclid"), Map.entry("scp173", "Euclid"), Map.entry("scp096", "Euclid"), Map.entry("scp049", "Euclid"),
            Map.entry("scp049_2", "Euclid"), Map.entry("scp008_zombie", "Euclid"), Map.entry("scp457", "Euclid"),
            Map.entry("scp106", "Keter"), Map.entry("scp682", "Keter"), Map.entry("scp939", "Keter"), Map.entry("scp610_crawler", "Keter"),
            Map.entry("scp610_stalker", "Keter"), Map.entry("scp610_hulk", "Keter"), Map.entry("scp001", "Thaumiel"));

    // ------------------------------------------------------------------ identification

    @Nullable
    public static ResourceLocation idOf(Entity e) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
    }

    /** A creature from SCP Unleashed that counts as an anomaly (people like D-Class don't). */
    public static boolean isUnleashedAnomaly(Entity e) {
        ResourceLocation id = idOf(e);
        if (id == null || !MODID.equals(id.getNamespace())) return false;
        String p = id.getPath();
        if (p.equals("dclass") || p.contains("mtf") || p.contains("guard") || p.contains("insurgent") || p.contains("scientist") || p.contains("operative")) {
            return false;
        }
        return p.startsWith("scp") || p.startsWith("scarlet_") || p.startsWith("daevite_") || p.startsWith("theodore") || p.startsWith("stranger");
    }

    /** Our own anomalies (the Gate Guardian) or any of SCP Unleashed's. */
    public static boolean isAnomaly(Entity e) {
        return e instanceof Anomaly || isUnleashedAnomaly(e);
    }

    public static String designation(Entity e) {
        if (e instanceof Anomaly a) return a.designation();
        ResourceLocation id = idOf(e);
        return id == null ? "Unknown" : designation(id.getPath());
    }

    /** "scp173" -> "SCP-173", "scp049_2" -> "SCP-049-2", "scp610_crawler" -> "SCP-610 (crawler)". */
    public static String designation(String path) {
        if (!path.startsWith("scp")) return pretty(path);
        String rest = path.substring(3);
        int us = rest.indexOf('_');
        String num = us < 0 ? rest : rest.substring(0, us);
        String suffix = us < 0 ? "" : rest.substring(us + 1);
        if (suffix.isEmpty()) return "SCP-" + num;
        if (suffix.chars().allMatch(Character::isDigit)) return "SCP-" + num + "-" + suffix;
        return "SCP-" + num + " (" + suffix.replace('_', ' ') + ")";
    }

    private static String pretty(String path) {
        StringBuilder b = new StringBuilder();
        for (String w : path.split("_")) if (!w.isEmpty()) b.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
        return b.toString().trim();
    }

    public static String objectClass(Entity e) {
        if (e instanceof Anomaly a) return a.objectClass();
        ResourceLocation id = idOf(e);
        if (id == null) return "Euclid";
        String p = id.getPath();
        if (CLASSES.containsKey(p)) return CLASSES.get(p);
        if (p.startsWith("scarlet_") || p.startsWith("daevite_") || p.startsWith("scp610")) return "Keter";
        return "Euclid";
    }

    public static ChatFormatting classColor(String cls) {
        return switch (cls) {
            case "Safe" -> ChatFormatting.GREEN;
            case "Euclid" -> ChatFormatting.YELLOW;
            case "Thaumiel" -> ChatFormatting.AQUA;
            default -> ChatFormatting.RED;
        };
    }

    public static boolean canBeContained(Entity e) {
        if (e instanceof Anomaly a) return a.canBeContained();
        return isUnleashedAnomaly(e);
    }

    @Nullable
    public static Component statusNote(Entity e) {
        return e instanceof Anomaly a ? a.statusNote() : null;
    }

    // ------------------------------------------------------------------ lookups

    public static Optional<EntityType<?>> entity(String path) {
        return BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.fromNamespaceAndPath(MODID, path));
    }

    public static Item item(String path, Item fallback) {
        return BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath(MODID, path)).orElse(fallback);
    }

    public static Item keycard(int level) {
        return item("keycard_level_" + Math.max(1, Math.min(5, level)), Items.PAPER);
    }

    public static Item scp500() {
        return item("scp500_pill", Items.ENCHANTED_GOLDEN_APPLE);
    }

    // ------------------------------------------------------------------ containment

    /**
     * Holds any anomaly in stasis: applies our Contained effect, and (for SCP Unleashed creatures,
     * whose AI we can't reach) switches their AI off until the effect ends. See ContainmentEvents.
     */
    public static void contain(Mob mob, int ticks) {
        mob.addEffect(new MobEffectInstance(ModEffects.h(ModEffects.CONTAINED), ticks, 0, false, false, true));
    }

    public static boolean isContained(LivingEntity e) {
        return ModEffects.has(e, ModEffects.CONTAINED);
    }
}
