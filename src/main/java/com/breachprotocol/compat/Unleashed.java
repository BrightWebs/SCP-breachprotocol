package com.breachprotocol.compat;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bridge to SCP Unleashed (Breach Protocol is an addon for it). Everything here works by registry
 * ID, tag and SCP Unleashed's own data at runtime - no code or assets from SCP Unleashed are
 * included in this mod. IDs checked against SCP Unleashed 0.0.5.2.
 */
public final class Unleashed {
    private Unleashed() {}

    public static final String MODID = "scp_unleashed";

    // SCP Unleashed entity IDs used by the career system
    public static final String SCP_049 = "scp049", SCP_049_2 = "scp049_2", SCP_066 = "scp066", SCP_096 = "scp096_shy_guy",
            SCP_106 = "scp106_old_man", SCP_131 = "scp131_pod", SCP_173 = "scp173", SCP_457 = "scp457", SCP_682 = "scp682",
            SCP_939 = "scp939", SCP_999 = "scp999_blob", DCLASS = "dclass";

    private static TagKey<EntityType<?>> tag(String path) {
        return TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(MODID, path));
    }

    /** People in SCP Unleashed (D-Class, Theodore, clones, MTF). */
    public static final TagKey<EntityType<?>> PERSON_LIKE = tag("person_like");
    /** Things SCP Unleashed's stasis can't hold (bosses, SCP-106, SCP-343, ...). */
    public static final TagKey<EntityType<?>> STASIS_IMMUNE = tag("stasis_immune");

    // ------------------------------------------------------------------ identification

    @Nullable
    public static ResourceLocation idOf(Entity e) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
    }

    public static boolean is(Entity e, String path) {
        ResourceLocation id = idOf(e);
        return id != null && MODID.equals(id.getNamespace()) && id.getPath().equals(path);
    }

    /** A creature from SCP Unleashed that counts as an anomaly (people, squads and vehicles don't). */
    public static boolean isUnleashedAnomaly(Entity e) {
        ResourceLocation id = idOf(e);
        if (id == null || !MODID.equals(id.getNamespace())) return false;
        if (e.getType().is(PERSON_LIKE)) return false;
        String p = id.getPath();
        if (p.contains("mtf") || p.contains("chaos") || p.contains("intro_") || p.contains("seat") || p.contains("helicopter")
                || p.contains("grenade") || p.equals("rocket") || p.equals("drop_pod") || p.equals("satellite_booster") || p.equals("orbital_strike")
                || p.equals("serpents_hand_agent")) {
            return false;
        }
        return p.startsWith("scp") || p.startsWith("scarlet_") || p.startsWith("daevite_") || p.startsWith("z0") || p.startsWith("theodore_")
                || p.startsWith("stranger") || p.equals("mekhane") || p.equals("yaldabaoth");
    }

    public static boolean isAnomaly(Entity e) {
        return isUnleashedAnomaly(e);
    }

    /** SCP Unleashed's own name for it (from its language files), e.g. "SCP-096". */
    public static Component name(Entity e) {
        return e.getType().getDescription();
    }

    /** Translation key of an entity type, for the client to resolve. */
    public static String nameKey(String path) {
        return "entity." + MODID + "." + path;
    }

    public static String designation(Entity e) {
        ResourceLocation id = idOf(e);
        return id == null ? e.getName().getString() : designation(id.getPath());
    }

    /** A readable designation built from the ID: "scp096_shy_guy" -> "SCP-096", "scp049_2" -> "SCP-049-2". */
    public static String designation(String path) {
        Matcher m = Pattern.compile("^scp(\\d+)(?:_(.*))?$").matcher(path);
        if (m.matches()) {
            String suffix = m.group(2);
            if (suffix != null && suffix.matches("\\d+")) return "SCP-" + m.group(1) + "-" + suffix;
            return "SCP-" + m.group(1);
        }
        StringBuilder b = new StringBuilder();
        for (String w : path.split("_")) if (!w.isEmpty()) b.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
        return b.toString().trim();
    }

    /**
     * The SCP Unleashed anomaly document that covers this entity:
     * "scp096_shy_guy" -> scp_096, "scp001_day_crawler" -> scp_001_day, "z0004_ship" -> z_0004,
     * "scarlet_king" -> scp_001_scarlet.
     */
    @Nullable
    public static ResourceLocation documentId(String path) {
        Matcher m = Pattern.compile("^scp(\\d+)(?:_([a-z]+).*)?$").matcher(path);
        if (m.matches()) {
            String num = m.group(1);
            if (m.group(2) != null) {
                ResourceLocation sub = ResourceLocation.fromNamespaceAndPath(MODID, "scp_" + num + "_" + m.group(2));
                if (UnleashedApi.document(sub) != null) return sub;
            }
            return ResourceLocation.fromNamespaceAndPath(MODID, "scp_" + num);
        }
        Matcher z = Pattern.compile("^z(\\d+).*$").matcher(path);
        if (z.matches()) return ResourceLocation.fromNamespaceAndPath(MODID, "z_" + z.group(1));
        if (path.startsWith("scarlet_")) return ResourceLocation.fromNamespaceAndPath(MODID, "scp_001_scarlet");
        return null;
    }

    /** Object class from SCP Unleashed's anomaly document, e.g. "Euclid". "Unknown" if it has none. */
    public static String objectClass(Entity e) {
        ResourceLocation id = idOf(e);
        if (id == null) return "Unknown";
        ResourceLocation doc = documentId(id.getPath());
        String c = doc == null ? null : UnleashedApi.documentClass(UnleashedApi.document(doc));
        if (c == null) return "Unknown";
        if (c.equals("z_class")) return "Z-Class";
        return c.substring(0, 1).toUpperCase(Locale.ROOT) + c.substring(1);
    }

    public static ChatFormatting classColor(String cls) {
        return switch (cls) {
            case "Safe" -> ChatFormatting.GREEN;
            case "Euclid" -> ChatFormatting.YELLOW;
            case "Thaumiel" -> ChatFormatting.AQUA;
            case "Apollyon", "Z-Class" -> ChatFormatting.DARK_RED;
            case "Unknown", "Neutralized" -> ChatFormatting.GRAY;
            default -> ChatFormatting.RED;
        };
    }

    /** Can it be held in a cell at all? SCP Unleashed's stasis_immune tag says what can't. */
    public static boolean canBeContained(Entity e) {
        return isUnleashedAnomaly(e) && !e.getType().is(STASIS_IMMUNE);
    }

    // ------------------------------------------------------------------ containment

    /** SCP Unleashed's cell-building blocks. */
    public static boolean isCellBlock(BlockState s) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(s.getBlock());
        if (!MODID.equals(id.getNamespace())) return false;
        String p = id.getPath();
        return p.contains("containment") || p.startsWith("reinforced_") || p.contains("forcefield") || p.contains("one_way_glass")
                || p.startsWith("hcz_") || p.startsWith("lcz_") || p.contains("vault") || p.endsWith("_door") || p.contains("shutter");
    }

    /**
     * Is this anomaly contained? SCP-096 with its hood on is (SCP Unleashed's own procedure). Anything
     * else has to be in a cell inside a declared Foundation site: near a working SCP Unleashed
     * forcefield, or boxed in on all four sides and above by SCP Unleashed containment blocks.
     * (Sealing it in a stasis pod also counts; that's reported by SCP Unleashed's capture hook.)
     */
    public static boolean isContained(Mob mob) {
        if (UnleashedApi.isShyGuy(mob) && UnleashedApi.isBagged(mob)) return true;
        Level l = mob.level();
        // a cell only counts on Foundation ground: inside a declared site
        if (com.breachprotocol.career.Sites.at(l, mob.blockPosition()) == null) return false;
        Vec3 c = mob.position().add(0, Math.min(1.0, mob.getBbHeight() / 2), 0);
        if (UnleashedApi.forcefieldNear(l, c, 4)) return true;
        BlockPos start = BlockPos.containing(c);
        for (Direction d : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP}) {
            boolean walled = false;
            BlockPos.MutableBlockPos q = start.mutable();
            for (int i = 0; i < 12; i++) {
                q.move(d);
                BlockState s = l.getBlockState(q);
                if (s.isAir() || !s.isCollisionShapeFullBlock(l, q) && !isCellBlock(s)) continue;
                walled = isCellBlock(s);
                break;
            }
            if (!walled) return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ lookups

    public static Optional<EntityType<?>> entity(String path) {
        return BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.fromNamespaceAndPath(MODID, path));
    }

    public static Item item(String path, Item fallback) {
        return BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath(MODID, path)).orElse(fallback);
    }

    public static Block block(String path, Block fallback) {
        return BuiltInRegistries.BLOCK.getOptional(ResourceLocation.fromNamespaceAndPath(MODID, path)).orElse(fallback);
    }

    /** SCP Unleashed keycard: levels 1-5, 6 = Omni. */
    public static Item keycard(int level) {
        if (level >= 6) return item("keycard_omni", Items.PAPER);
        return item("keycard_level_" + Math.max(1, Math.min(5, level)), Items.PAPER);
    }

    public static Item scp500() {
        return item("scp500_pill", Items.ENCHANTED_GOLDEN_APPLE);
    }

    public static boolean hasScp500() {
        return scp500() != Items.ENCHANTED_GOLDEN_APPLE;
    }

    public static Block cellWall() {
        return block("reinforced_containment_wall", Blocks.POLISHED_DEEPSLATE);
    }
}
