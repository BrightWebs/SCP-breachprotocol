package com.breachprotocol.world;

import com.breachprotocol.compat.Unleashed;
import com.breachprotocol.career.O5;
import com.breachprotocol.entity.O5MemberEntity;
import com.breachprotocol.registry.ModBlocks;
import com.breachprotocol.registry.ModEntities;
import com.breachprotocol.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Site-01: the Administrator's sanctum. One per world, carved deep under the overworld at a
 * seed-derived spot 1.4-2km from spawn the first time someone becomes The Administrator.
 * Built from SCP Unleashed's facility blocks, laid out around one grand hall:
 *
 * <pre>
 *   z 0-18   containment wing (two cells) | O5 Council chamber | archive
 *   z 18-26  the Grand Hall  -------------------------------->  Gate Hall (tall) | blast gate | Ascension chamber
 *   z 26-44  armory | medical bay | quarters | link corridor | command centre
 *   z 44-50  south wing hall
 *   z 50-71  research lab | server room | MTF barracks | the Administrator's office
 * </pre>
 */
public final class Site01 {
    private Site01() {}

    public static final int SX = 66, SY = 10, SZ = 72;
    /** 3 = rebuilt from SCP Unleashed's facility blocks. Older sites are rebuilt on the next visit. */
    private static final int VERSION = 3;

    // ------------------------------------------------------------------ saved state

    public static class Data extends SavedData {
        public boolean built;
        /** Layout version (see VERSION). */
        public int version;

        public static final SavedData.Factory<Data> FACTORY = new SavedData.Factory<>(Data::new, Data::load, null);

        public static Data load(CompoundTag tag, HolderLookup.Provider provider) {
            Data d = new Data();
            d.built = tag.getBoolean("built");
            d.version = tag.getInt("version");
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
            tag.putBoolean("built", built);
            tag.putInt("version", version);
            return tag;
        }
    }

    public static BlockPos location(ServerLevel overworld) {
        Random r = new Random(overworld.getSeed() ^ 0x5C0001L);
        double ang = r.nextDouble() * Math.PI * 2;
        int dist = 1400 + r.nextInt(600);
        return new BlockPos((int) (Math.cos(ang) * dist), -34, (int) (Math.sin(ang) * dist));
    }

    public static BlockPos arrival(BlockPos origin) {
        return origin.offset(5, 0, 22);
    }

    public static boolean isInside(Player p) {
        if (!(p.level() instanceof ServerLevel sl) || sl.dimension() != Level.OVERWORLD) return false;
        BlockPos o = location(sl.getServer().overworld());
        return new AABB(o.getX(), o.getY() - 2, o.getZ(), o.getX() + SX + 1, o.getY() + SY + 1, o.getZ() + SZ + 1).contains(p.position());
    }

    public static BlockPos ensureBuilt(ServerLevel overworld) {
        BlockPos o = location(overworld);
        Data data = overworld.getDataStorage().computeIfAbsent(Data.FACTORY, "breachprotocol_site01");
        if (!data.built || data.version < VERSION) {
            // first visit, or an older layout: (re)build the whole site
            new Builder(overworld, o).build();
            data.built = true;
            data.version = VERSION;
            data.setDirty();
        }
        registerSite(overworld, o);
        return o;
    }

    /** Site-01 is Foundation ground: its cells count as containment. */
    private static void registerSite(ServerLevel l, BlockPos o) {
        com.breachprotocol.career.Sites sites = com.breachprotocol.career.Sites.get(l.getServer());
        if (sites.sites.stream().anyMatch(s -> s.name.equals("Site-01"))) return;
        com.breachprotocol.career.Sites.Site s = new com.breachprotocol.career.Sites.Site();
        s.name = "Site-01";
        s.ownerName = "The Administrator";
        s.dim = l.dimension().location().toString();
        s.x = o.getX() + SX / 2;
        s.y = o.getY();
        s.z = o.getZ() + SZ / 2;
        s.radius = 48;
        sites.sites.add(s);
        sites.setDirty();
    }

    // ================================================================== the build

    private static final class Builder extends FacilityBuilder {
        private static final int Y = -1;   // floor blocks at y=-1, you stand at y=0

        Builder(ServerLevel l, BlockPos o) { super(l, o); }

        void build() {
            for (int cx = (o.getX() - 2) >> 4; cx <= (o.getX() + SX + 2) >> 4; cx++)
                for (int cz = (o.getZ() - 2) >> 4; cz <= (o.getZ() + SZ + 2) >> 4; cz++)
                    l.getChunk(cx, cz);
            // the old Overseers, if this is a rebuild
            for (O5MemberEntity m : l.getEntitiesOfClass(O5MemberEntity.class, new AABB(o.getX() - 2, o.getY() - 4, o.getZ() - 2, o.getX() + SX + 2, o.getY() + SY + 4, o.getZ() + SZ + 2)))
                m.discard();
            fill(-1, -2, -1, SX, SY, SZ, "reinforced_containment_wall");

            hall();
            southHall();
            gateAndAltar();
            containment();
            council();
            archive();
            armory();
            medical();
            quarters();
            link();
            command();
            lab();
            servers();
            barracks();
            office();
        }

        // ---------------------------------------------------------------- the Grand Hall

        private void hall() {
            room(EZ, 0, 18, 40, 26, Y, true);
            for (int x = 1; x < 40; x++) {
                set(x, Y, 22, "facility_carpet_blue");
                set(x, Y, 21, EZ.marked() + "[facing=north]");
                set(x, Y, 23, EZ.marked() + "[facing=south]");
            }
            // the Foundation emblem at the west end, planters and benches down the sides
            scpSign(1, Y + 3, 23, E);
            plate(1, Y + 2, 21, E, "SITE-01", "Office of the", "Administrator");
            for (int x : new int[]{4, 16, 24, 37}) {
                set(x, Y + 1, 19, "ez_planter[facing=south]");
                set(x, Y + 1, 25, "ez_planter[facing=north]");
            }
            for (int i = 0; i < 3; i++) {
                set(12 + i, Y + 1, 19, "ez_waiting_chairs[facing=south]");
                set(30 + i, Y + 1, 19, "ez_waiting_chairs[facing=south]");
                set(12 + i, Y + 1, 25, "ez_waiting_chairs[facing=north]");
                set(28 + i, Y + 1, 25, "ez_waiting_chairs[facing=north]");
            }
            set(1, Y + 1, 19, "potted_plant[facing=east]");
            set(1, Y + 1, 25, "potted_plant[facing=east]");
            set(20, Y + 3, 19, "wall_clock[facing=south]");
            set(38, Y + 3, 19, "ez_directory_sign[facing=south]");
        }

        // ---------------------------------------------------------------- the Gate and the Altar

        private void gateAndAltar() {
            Pal arch = new Pal("scp5001_arch_floor", "scp5001_arch_floor_seam", "scp5001_arch_graphite_panel", "scp5001_arch_panel",
                    "scp5001_arch_panel_seam", "scp5001_arch_ceiling", "scp5001_arch_glass");
            // Gate Hall: tall, dark steel, the hall opens into it
            room(HCZP, 40, 16, 54, 28, Y, 8, false);
            for (int z = 19; z <= 25; z++) for (int yy = Y + 1; yy <= Y + 4; yy++) set(40, yy, z, "minecraft:air");
            for (int x = 41; x < 54; x++) set(x, Y, 22, "hcz_floor_arrow[facing=east]");
            for (int x = 42; x <= 52; x += 5) for (int z = 18; z <= 26; z += 4) light(x, Y + 8, z, false);
            for (int x = 42; x <= 52; x += 5) { stack(x, Y + 1, 17, "hcz_pipe_manifold", S, 6); stack(x, Y + 1, 27, "hcz_pipe_manifold", N, 6); }
            sign2("facility_sign_heavy_containment", 41, Y + 6, 17, S, "none", false);
            // Ascension chamber
            room(arch, 54, 12, SX - 1, 32, Y, 8, false);
            for (int x = 56; x < SX - 2; x += 3) for (int z = 14; z <= 30; z += 4) set(x, Y + 9, z, "scp5001_arch_ceiling_light");
            // pillars down both sides
            for (int z = 14; z <= 30; z += 4) for (int yy = Y + 1; yy <= Y + 8; yy++) { set(56, yy, z, "scp5001_arch_graphite_panel"); set(SX - 3, yy, z, "scp5001_arch_graphite_panel"); }
            // the dais: two steps up to the Altar
            fill(57, Y + 1, 18, 63, Y + 1, 26, "scp5001_arch_floor_hex");
            for (int z = 18; z <= 26; z++) set(57, Y + 1, z, "scp5001_arch_stairs[facing=east,half=bottom,shape=straight]");
            fill(58, Y + 2, 20, 62, Y + 2, 24, "scp5001_arch_floor_glyph");
            for (int z = 20; z <= 24; z++) set(58, Y + 2, z, "scp5001_arch_stairs[facing=east,half=bottom,shape=straight]");
            set(60, Y + 3, 22, ModBlocks.ASCENSION_ALTAR.get().defaultBlockState());
            for (int[] c : new int[][]{{59, 21}, {61, 21}, {59, 23}, {61, 23}}) set(c[0], Y + 3, c[1], Blocks.END_ROD.defaultBlockState());
            for (int x = 55; x < SX - 1; x++) { set(x, Y, 22, "scp5001_arch_floor_glyph"); }
            for (int z = 14; z <= 30; z += 8) set(SX - 2, Y + 4, z, "scp5001_arch_panel_glyph[glyph=" + (z % 3) + "]");
            // the Gate: a blast gate, Level 5
            gate("blast_gate", 4, 4, 54, 20, Y, W, 5);
            plate(53, Y + 2, 25, W, "THE GATE", "Level 5", "Administrator", "only");

        }

        // ---------------------------------------------------------------- north side

        private void containment() {
            room(CELL, 0, 10, 16, 18, Y, true);
            CellSpec a = new CellSpec("", "CELL 01-A", "Unassigned", "Reserved for", "the Administrator", 5, "");
            CellSpec b = new CellSpec("", "CELL 01-B", "Unassigned", "Reserved for", "the Administrator", 5, "");
            cell(a, 0, 0, 8, 10, Y, S);
            cell(b, 8, 0, 16, 10, Y, S);
            gate("containment_room_door", 2, 2, 7, 18, Y, S, 4);
            plate(5, Y + 2, 19, S, "CONTAINMENT", "Level 4");
            set(1, Y + 2, 13, "containment_room_status_panel[facing=east,powered=true]");
            set(15, Y + 2, 13, "fire_extinguisher[facing=west,wall=true]");
            chest(15, Y + 1, 16, W,
                    ui("reinforced_containment_wall", Items.POLISHED_DEEPSLATE, 64), ui("reinforced_containment_wall", Items.POLISHED_DEEPSLATE, 64),
                    ui("forcefield_emitter", Items.BEACON, 4), ui("keycard_scanner", Items.STONE_BUTTON, 4), ui("containment_door", Items.IRON_DOOR, 4),
                    ui("rift_extractor", Items.SPYGLASS, 1), ui("mtf_signal_radio", Items.COMPASS, 1), ui("scramble_goggles", Items.CARVED_PUMPKIN, 1),
                    ui("scp096_bag", Items.LEATHER, 1), ui("stasis_pod_abyssal", Items.GLASS_BOTTLE, 2));
        }

        /** The twelve Overseers around a ring table; the Administrator's seat at its head. */
        private void council() {
            room(OFFICE.floor("facility_carpet_blue"), 16, 0, 38, 18, Y, true);
            ezDoor(26, 18, Y, S);
            plate(29, Y + 2, 19, S, "O5 COUNCIL");
            int x0 = 21, x1 = 33, z0 = 6, z1 = 12;
            for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
                if (!(x == x0 || x == x1 || z == z0 || z == z1)) continue;
                boolean side = x == x0 || x == x1, end = z == z0 || z == z1;
                set(x, Y + 1, z, "conference_table[north=" + (side && z > z0) + ",south=" + (side && z < z1) + ",west=" + (end && x > x0) + ",east=" + (end && x < x1) + "]");
            }
            List<int[]> seats = new ArrayList<>();
            for (int x = x0 + 1; x <= x1 - 1; x += 2) { seats.add(new int[]{x, z0 - 1}); seats.add(new int[]{x, z1 + 1}); }
            double cx = o.getX() + (x0 + x1) / 2.0 + 0.5, cz = o.getZ() + (z0 + z1) / 2.0 + 0.5;
            for (int i = 0; i < seats.size() && i < 12; i++) {
                int[] s = seats.get(i);
                boolean north = s[1] < z0;
                set(s[0], Y + 1, s[1], "office_chair[facing=" + (north ? "south" : "north") + "]");
                set(s[0], Y + 2, north ? z0 : z1, "crt_monitor[facing=" + (north ? "north" : "south") + ",lit=true,screen=" + (i % 10) + "]");
                O5MemberEntity m = ModEntities.O5_MEMBER.get().create(l);
                if (m != null) {
                    m.setSeat(O5.seat(i + 1));
                    double ex = o.getX() + s[0] + 0.5, ez = o.getZ() + s[1] + 0.5;
                    float yaw = (float) (Mth.atan2(cz - ez, cx - ex) * Mth.RAD_TO_DEG) - 90F;
                    m.moveTo(ex, o.getY() + 0.5, ez, yaw, 0);
                    m.setYHeadRot(yaw);
                    m.setYBodyRot(yaw);
                    m.setPersistenceRequired();
                    l.addFreshEntity(m);
                }
            }
            // the Administrator's seat at the head of the table, under the emblem
            set(x0 - 1, Y + 1, (z0 + z1) / 2, "office_chair[facing=east]");
            scpSign(17, Y + 3, 10, E);
            for (int[] p : new int[][]{{17, 1}, {37, 1}, {17, 17}, {37, 17}}) set(p[0], Y + 1, p[1], "ez_planter[facing=south]");
            for (int z = 3; z <= 15; z += 6) set(37, Y + 2, z, "wall_monitor[facing=west,hanging=false,lit=true,screen=" + z % 10 + "]");
        }

        private void archive() {
            room(OFFICE.floor("facility_carpet_blue"), 38, 0, 54, 16, Y, true);
            ezDoor(44, 16, Y, S);
            plate(43, Y + 2, 17, S, "ARCHIVE");
            for (int z : new int[]{3, 4, 8, 9}) {
                Direction f = z == 3 || z == 8 ? N : S;
                for (int x = 40; x <= 52; x++) if (x != 46) stackBlocks(x, Y + 1, z, "office_records_cabinet", f, 3);
            }
            for (int z = 1; z <= 15; z++) if (z < 3 || z > 9) set(39, Y + 1, z, "binder_shelf[facing=east]");
            desksX("office_desk", 40, 43, 13, Y, N, "desk_lamp[facing=$,lit=true]");
            put(49, Y + 1, 13, "archive", Blocks.LECTERN, N);
            put(51, Y + 1, 13, "archive_database_terminal", Blocks.LECTERN, N);
            chest(53, Y + 1, 15, W,
                    ui("facility_tablet", Items.MAP, 1), new ItemStack(ModItems.PHONE.get(), 1), new ItemStack(Unleashed.keycard(1)), new ItemStack(Unleashed.keycard(2)),
                    new ItemStack(Unleashed.keycard(3)), new ItemStack(Unleashed.keycard(4)), new ItemStack(Unleashed.keycard(5), 2), new ItemStack(Unleashed.keycard(6)),
                    new ItemStack(Items.BOOK, 32), new ItemStack(Items.PAPER, 64));
        }

        // ---------------------------------------------------------------- south side

        private void armory() {
            room(HCZP, 0, 26, 15, 44, Y, true);
            gate("hcz_room_door", 2, 2, 7, 26, Y, N, 4);
            sign2("facility_sign_armory", 8, Y + 4, 25, N, "none", false);
            for (int z = 28; z <= 42; z++) if (z % 4 != 3) tall(1, Y + 1, z, "locker", E);
            for (int z = 29; z <= 41; z += 3) set(14, Y + 1, z, "steel_shelf[facing=west]");
            for (int x = 3; x <= 12; x += 3) stack(x, Y + 1, 43, "hcz_hazard_crate", N, 2);
            chest(6, Y + 1, 34, E,
                    ench(Items.NETHERITE_SWORD, Enchantments.SHARPNESS, 5, Enchantments.LOOTING, 3, Enchantments.UNBREAKING, 3),
                    ench(Items.BOW, Enchantments.POWER, 5, Enchantments.INFINITY, 1, Enchantments.FLAME, 1),
                    new ItemStack(Items.ARROW, 64), new ItemStack(Items.SHIELD), ench(Items.CROSSBOW, Enchantments.QUICK_CHARGE, 3, Enchantments.MULTISHOT, 1),
                    new ItemStack(Items.TOTEM_OF_UNDYING, 2), new ItemStack(Items.ENDER_PEARL, 16), new ItemStack(Items.GOLDEN_CARROT, 32));
            chest(6, Y + 1, 36, E,
                    ench(Items.NETHERITE_HELMET, Enchantments.PROTECTION, 4, Enchantments.UNBREAKING, 3),
                    ench(Items.NETHERITE_CHESTPLATE, Enchantments.PROTECTION, 4, Enchantments.UNBREAKING, 3),
                    ench(Items.NETHERITE_LEGGINGS, Enchantments.PROTECTION, 4, Enchantments.UNBREAKING, 3),
                    ench(Items.NETHERITE_BOOTS, Enchantments.PROTECTION, 4, Enchantments.FEATHER_FALLING, 4));
            chest(9, Y + 1, 34, W,
                    ui("assault_rifle", Items.CROSSBOW, 2), ui("combat_shotgun", Items.CROSSBOW, 1), ui("pistol", Items.CROSSBOW, 2), ui("machine_gun", Items.CROSSBOW, 1),
                    ui("rifle_rounds", Items.ARROW, 64), ui("rifle_rounds", Items.ARROW, 64), ui("handgun_rounds", Items.ARROW, 64), ui("shotgun_shells", Items.ARROW, 32),
                    ui("frag_grenade", Items.TNT, 8), ui("flash_grenade", Items.SNOWBALL, 8), ui("gas_grenade", Items.SPLASH_POTION, 4));
            chest(9, Y + 1, 36, W,
                    ui("mtf_helmet", Items.IRON_HELMET, 4), ui("mtf_vest", Items.IRON_CHESTPLATE, 4), ui("mtf_signal_radio", Items.COMPASS, 1),
                    ui("unstable_fragment_volatile", Items.AMETHYST_SHARD, 4), ui("unstable_fragment_severe", Items.AMETHYST_SHARD, 2), ui("unstable_fragment_abyssal", Items.AMETHYST_SHARD, 1));
            for (int x = 6; x <= 9; x++) set(x, Y + 1, 39, "metal_desk[facing=north,left=" + (x > 6) + ",right=" + (x < 9) + "]");
            set(3, Y + 1, 39, "minecraft:anvil");
            set(3, Y + 1, 41, "minecraft:smithing_table");
            set(12, Y + 1, 41, "minecraft:grindstone");
        }

        private void medical() {
            room(MEDICAL, 15, 26, 28, 44, Y, true);
            ezDoor(21, 26, Y, N);
            set(22, Y + 3, 25, "facility_sign_medical_small[facing=north]");
            for (int x = 17; x <= 25; x += 4) {
                stackBlocks(x, Y + 1, 43, "medical_headwall", N, 3);
                put(x, Y + 1, 41, "examination_table", Blocks.WHITE_BED, S);
                set(x + 1, Y + 1, 43, "vitals_monitor[facing=north,lit=true]");
            }
            for (int x = 19; x <= 23; x += 4) tall(x, Y + 1, 41, "privacy_screen", N);
            for (int z = 28; z <= 36; z += 2) set(27, Y + 1, z, "medical_supply_shelf[facing=west]");
            set(16, Y + 2, 30, "medical_defib_cabinet[facing=east]");
            set(16, Y + 2, 33, "medical_lightbox[facing=east,lit=true]");
            set(16, Y + 1, 36, "biohazard_bin[facing=east]");
            set(20, Y + 1, 33, "wheelchair[facing=east]");
            chest(16, Y + 1, 28, E, new ItemStack(Unleashed.scp500(), 6), new ItemStack(Items.GOLDEN_APPLE, 16), new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 2),
                    ui("amnestic_canister", Items.MILK_BUCKET, 8), ui("scp207_bottle", Items.HONEY_BOTTLE, 2), ui("scp3008_bandage", Items.PAPER, 16));
        }

        private void quarters() {
            room(OFFICE.floor("facility_carpet_blue"), 28, 26, 40, 44, Y, true);
            ezDoor(34, 26, Y, N);
            plate(33, Y + 2, 25, N, "QUARTERS");
            Placer.place(l, at(37, Y + 1, 40), Blocks.WHITE_BED, S);
            set(39, Y + 1, 43, "potted_plant[facing=west]");
            tall(35, Y + 1, 43, "coat_rack", N);
            couch("couch_leather", 29, 31, Y, 5, false, E);
            table("conference_table", null, 31, 32, 31, 34, Y, 1);
            for (int x = 29; x <= 33; x++) { set(x, Y + 1, 43, "binder_shelf[facing=north]"); set(x, Y + 2, 43, "binder_shelf[facing=north]"); }
            set(39, Y + 1, 28, "minecraft:crafting_table");
            set(39, Y + 1, 29, "minecraft:furnace[facing=west]");
            set(39, Y + 1, 30, "minecraft:blast_furnace[facing=west]");
            set(39, Y + 1, 31, "minecraft:smoker[facing=west]");
            set(39, Y + 1, 33, "minecraft:ender_chest[facing=west]");
            set(35, Y + 1, 35, "minecraft:enchanting_table");
            for (int x = 33; x <= 37; x++) for (int z = 33; z <= 37; z++) {
                boolean edge = x == 33 || x == 37 || z == 33 || z == 37;
                if (edge && !(x == 35 && z == 33)) { set(x, Y + 1, z, "minecraft:bookshelf"); set(x, Y + 2, z, "minecraft:bookshelf"); }
            }
            chest(29, Y + 1, 28, E,
                    new ItemStack(Items.COOKED_BEEF, 64), new ItemStack(Items.BREAD, 32), new ItemStack(Items.TORCH, 64), new ItemStack(Items.FIREWORK_ROCKET, 64), new ItemStack(Items.ELYTRA),
                    ench(Items.NETHERITE_PICKAXE, Enchantments.EFFICIENCY, 5, Enchantments.FORTUNE, 3, Enchantments.UNBREAKING, 3),
                    ench(Items.NETHERITE_AXE, Enchantments.EFFICIENCY, 5, Enchantments.UNBREAKING, 3),
                    ench(Items.NETHERITE_SHOVEL, Enchantments.EFFICIENCY, 5, Enchantments.SILK_TOUCH, 1), new ItemStack(Items.LAPIS_LAZULI, 64), new ItemStack(Items.EXPERIENCE_BOTTLE, 64));
        }

        /** From the Gate Hall down to the south wing. */
        private void link() {
            room(EZ, 42, 28, 48, 44, Y, true);
            ezDoor(44, 28, Y, N);
            ezDoor(44, 44, Y, S);
            for (int z = 29; z < 44; z++) set(45, Y, z, "ez_floor_arrow[facing=south]");
            set(43, Y + 3, 29, "facility_sign_entrance_zone_small[facing=south]");
        }

        /** Command centre: the site's eyes and the orbital uplink. */
        private void command() {
            room(RESEARCH, 48, 33, SX - 1, 44, Y, true);
            ezDoor(48, 37, Y, W);
            plate(47, Y + 2, 36, W, "COMMAND", "CENTRE");
            desksX("metal_desk", 51, 61, 35, Y, S, "security_monitor[facing=$,lit=true,screen=$]".replace(",screen=$", ",screen=6"));
            for (int x = 50; x <= 62; x += 2) set(x, Y + 2, 34, "wall_monitor[facing=south,hanging=false,lit=true,screen=" + x % 10 + "]");
            put(56, Y + 1, 40, "satellite_terminal", Blocks.LECTERN, N);
            power(58, Y + 1, 42, new int[]{56, Y + 1, 40});
            set(56, Y + 1, 39, "office_chair[facing=south]");
            put(52, Y + 1, 41, "scp5001_ctrl_holo_table", Blocks.AIR, N);
            chest(63, Y + 1, 42, W, ui("orbital_designator", Items.SPYGLASS, 1), ui("camera_tablet", Items.MAP, 1), ui("facility_tablet", Items.MAP, 1));
        }

        // ---------------------------------------------------------------- south wing

        private void southHall() {
            room(EZ, 0, 44, SX - 1, 50, Y, false);
            for (int x = 1; x < SX - 1; x++) { set(x, Y, 45, EZ.marked() + "[facing=north]"); set(x, Y, 49, EZ.marked() + "[facing=south]"); }
            for (int x = 3; x < SX - 2; x += 5) light(x, Y + 4, 47, true);
        }

        private void lab() {
            room(RESEARCH, 0, 50, 21, SZ - 1, Y, true);
            gate("glass_room_door", 2, 2, 10, 50, Y, N, 3);
            plate(13, Y + 2, 49, N, "RESEARCH", "LABORATORY");
            desksX("lab_bench_desk", 5, 15, 58, Y, N, null);
            desksX("lab_bench_desk", 5, 15, 59, Y, S, null);
            for (int x = 5; x <= 15; x += 5) set(x, Y + 2, 58, "crt_monitor[facing=north,lit=true,screen=4]");
            put(2, Y + 1, 53, "centrifuge", Blocks.CAULDRON, E);
            put(2, Y + 1, 57, "advanced_assembler", Blocks.CRAFTING_TABLE, E);
            put(2, Y + 1, 61, "advanced_materials_bench", Blocks.SMITHING_TABLE, E);
            put(2, Y + 1, 65, "cloning_machine", Blocks.BREWING_STAND, E);
            power(4, Y + 1, 68, new int[]{2, Y + 1, 53}, new int[]{2, Y + 1, 57}, new int[]{2, Y + 1, 61}, new int[]{2, Y + 1, 65});
            for (int z = 53; z <= 63; z += 2) stack(20, Y + 1, z, "containment_room_specimen_tank", W, 3);
            put(12, Y + 1, 65, "examination_table", Blocks.WHITE_BED, E);
            for (int x = 7; x <= 17; x += 2) set(x, Y + 1, SZ - 2, "chemical_shelf[facing=north]");
            chest(19, Y + 1, SZ - 2, W,
                    ui("hazmat_hood", Items.LEATHER_HELMET, 2), ui("hazmat_suit", Items.LEATHER_CHESTPLATE, 2), ui("hazmat_trousers", Items.LEATHER_LEGGINGS, 2),
                    ui("hazmat_boots", Items.LEATHER_BOOTS, 2), ui("gas_mask", Items.LEATHER_HELMET, 2), ui("scramble_goggles", Items.CARVED_PUMPKIN, 2),
                    ui("energum_dust", Items.GLOWSTONE_DUST, 32), ui("rift_extractor", Items.SPYGLASS, 1), ui("rift_detector", Items.COMPASS, 1),
                    ui("stasis_pod_faint", Items.GLASS_BOTTLE, 4), ui("stasis_pod_volatile", Items.GLASS_BOTTLE, 2), ui("scp096_bag", Items.LEATHER, 1));
        }

        private void servers() {
            room(POWER.floor("facility_metal_mesh"), 21, 50, 31, SZ - 1, Y, false);
            gate("hcz_room_door", 2, 2, 25, 50, Y, N, 3);
            set(27, Y + 3, 49, "facility_sign_server_room_small[facing=north]");
            for (int z : new int[]{53, 55, 56, 58, 59, 61, 62, 64, 65, 67}) {
                Direction f = (z == 53 || z == 56 || z == 59 || z == 62 || z == 65) ? S : N;
                for (int x = 23; x <= 29; x++) { tall(x, Y + 1, z, "server_rack", f); set(x, Y + 4, z, "cable_tray[orientation=down_x]"); }
            }
            for (int z : new int[]{54, 57, 60, 63, 66, 69}) for (int x = 23; x <= 29; x += 3) light(x, Y + 4, z, true);
            put(23, Y + 1, 69, "network_hub", Blocks.LODESTONE, N);
            put(25, Y + 1, 69, "network_router", Blocks.OBSERVER, N);
            put(27, Y + 1, 69, "chat_server", Blocks.JUKEBOX, N);
            put(29, Y + 1, 69, "power_control_console", Blocks.LECTERN, N);
            power(26, Y + 1, 70, new int[]{23, Y + 1, 69}, new int[]{25, Y + 1, 69}, new int[]{27, Y + 1, 69});
        }

        private void barracks() {
            room(LCZP, 31, 50, 42, SZ - 1, Y, true);
            ezDoorBtn(36, 50);
            plate(39, Y + 2, 49, N, "MTF", "BARRACKS");
            for (int z = 53; z <= 68; z += 3) { put(32, Y + 1, z, "lcz_cell_bunk", Blocks.GRAY_BED, E); tall(41, Y + 1, z, "locker", W); }
            table("cafeteria_table", "cafeteria_chair", 36, 58, 37, 62, Y, 2);
        }

        private void ezDoorBtn(int x, int z) { buttoned("lcz_room_door", 2, 2, x, z, Y, N); }

        /** The Administrator's office: Level 5, the desk facing the door, records and quarters behind. */
        private void office() {
            room(OFFICE.floor("facility_carpet_blue"), 42, 50, SX - 1, SZ - 1, Y, true);
            gate("glass_room_door", 2, 2, 52, 50, Y, N, 5);
            plate(50, Y + 2, 49, N, "THE", "ADMINISTRATOR");
            for (int x = 49; x <= 57; x++) set(x, Y + 1, 64, "office_desk[facing=south,left=" + (x > 49) + ",right=" + (x < 57) + "]");
            put(53, Y + 2, 64, "computer", Blocks.AIR, S);
            set(51, Y + 2, 64, "crt_monitor[facing=south,lit=true,screen=0]");
            set(55, Y + 2, 64, "paper_stack[facing=south,variant=1]");
            set(56, Y + 2, 64, "desk_lamp[facing=south,lit=true]");
            set(53, Y + 1, 65, "office_chair[facing=north]");
            set(51, Y + 1, 63, "office_chair[facing=south]");
            set(55, Y + 1, 63, "office_chair[facing=south]");
            scpSign(53, Y + 3, SZ - 2, N);
            for (int x = 43; x <= SX - 2; x++) if (x < 52 || x > 54) { set(x, Y + 1, SZ - 2, "binder_shelf[facing=north]"); set(x, Y + 2, SZ - 2, "binder_shelf[facing=north]"); }
            put(43, Y + 1, 60, "archive", Blocks.LECTERN, E);
            put(43, Y + 1, 62, "archive_database_terminal", Blocks.LECTERN, E);
            table("conference_table", "office_chair", 49, 55, 57, 56, Y, 2);
            couch("couch_leather", SX - 2, 58, Y, 5, false, W);
            for (int[] c : new int[][]{{43, 51}, {SX - 2, 51}, {43, SZ - 3}, {SX - 2, SZ - 3}}) set(c[0], Y + 1, c[1], "ez_planter[facing=south]");
            Placer.place(l, at(SX - 3, Y + 1, 66), Blocks.WHITE_BED, S);
            chest(SX - 2, Y + 1, 64, W,
                    ui("keycard_omni", Items.PAPER, 1), ui("orbital_designator", Items.SPYGLASS, 1), ui("stasis_pod_abyssal", Items.GLASS_BOTTLE, 2),
                    ui("mtf_signal_radio", Items.COMPASS, 1), ui("rift_detector", Items.COMPASS, 1), ui("camera_tablet", Items.MAP, 1), ui("facility_tablet", Items.MAP, 1),
                    new ItemStack(Unleashed.scp500(), 4), ui("scp207_bottle", Items.HONEY_BOTTLE, 2), ui("amnestic_canister", Items.MILK_BUCKET, 4),
                    new ItemStack(ModItems.PHONE.get(), 1));
        }

        private ItemStack ench(net.minecraft.world.level.ItemLike item, Object... pairs) {
            ItemStack s = new ItemStack(item);
            Registry<Enchantment> reg = l.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
            for (int i = 0; i + 1 < pairs.length; i += 2) {
                @SuppressWarnings("unchecked") ResourceKey<Enchantment> key = (ResourceKey<Enchantment>) pairs[i];
                Holder<Enchantment> h = reg.getHolderOrThrow(key);
                s.enchant(h, (Integer) pairs[i + 1]);
            }
            return s;
        }
    }
}
