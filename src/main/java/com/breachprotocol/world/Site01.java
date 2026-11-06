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

import java.util.Random;

/**
 * Site-01: the Administrator's sanctum. One per world, carved deep under the overworld at a
 * seed-derived spot 1.4-2km from spawn the first time someone becomes The Administrator.
 * Council chamber with the twelve Overseers, armory, medical bay, archive, quarters,
 * a two-cell containment wing - and at the far end, the Gate and the Altar.
 */
public final class Site01 {
    private Site01() {}

    public static final int SX = 66, SY = 10, SZ = 72;
    private static final int FLAGS = Block.UPDATE_CLIENTS;

    // ------------------------------------------------------------------ saved state

    public static class Data extends SavedData {
        public boolean built;
        /** 2 = has the south wing (Administrator's office, lab, server room, barracks). */
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
        if (!data.built) {
            build(overworld, o);
            data.built = true;
            data.version = 2;
            data.setDirty();
        } else if (data.version < 2) {
            buildSouthWing(overworld, o);
            data.version = 2;
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

    // ------------------------------------------------------------------ helpers

    private static void fill(ServerLevel l, BlockPos o, int x1, int y1, int z1, int x2, int y2, int z2, BlockState s) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++)
                    l.setBlock(o.offset(x, y, z), s, FLAGS);
    }

    private static void set(ServerLevel l, BlockPos o, int x, int y, int z, BlockState s) {
        l.setBlock(o.offset(x, y, z), s, FLAGS);
    }

    private static BlockState b(Block block) {
        return block.defaultBlockState();
    }

    /** Furniture and machines: placed like a player places them, so multi-block parts are built. */
    private static void put(ServerLevel l, BlockPos o, int x, int y, int z, String id, Block fallback) {
        put(l, o, x, y, z, id, fallback, null);
    }

    private static void put(ServerLevel l, BlockPos o, int x, int y, int z, String id, Block fallback, Direction facing) {
        Block blk = Unleashed.block(id, fallback);
        if (!Placer.place(l, o.offset(x, y, z), blk, facing) && fallback != Blocks.AIR)
            Placer.place(l, o.offset(x, y, z), fallback, facing); // never leave a half-built block
    }

    /** A ceiling lamp, hung from the block above. */
    private static void lamp(ServerLevel l, BlockPos o, int x, int y, int z, String id) {
        Block blk = Unleashed.block(id, Blocks.SEA_LANTERN);
        if (!Placer.ceiling(l, o.offset(x, y, z), blk)) l.setBlock(o.offset(x, y, z), Blocks.SEA_LANTERN.defaultBlockState(), Block.UPDATE_CLIENTS);
    }

    /** A wall-mounted sign or light: mounted on whichever horizontal neighbour is solid. */
    private static void wallPut(ServerLevel l, BlockPos o, int x, int y, int z, String id) {
        Block blk = Unleashed.block(id, Blocks.AIR);
        if (blk == Blocks.AIR) return;
        BlockPos p = o.offset(x, y, z);
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (l.getBlockState(p.relative(d)).isFaceSturdy(l, p.relative(d), d.getOpposite()) && Placer.wall(l, p, blk, d.getOpposite())) return;
        }
        Placer.ceiling(l, p, blk);
    }

    /** An SCP Unleashed block by id (vanilla fallback if it's ever missing). */
    private static BlockState u(String id, Block fallback) {
        return Unleashed.block(id, fallback).defaultBlockState();
    }

    private static ItemStack ui(String id, net.minecraft.world.level.ItemLike fallback, int n) {
        return new ItemStack(Unleashed.item(id, fallback.asItem()), n);
    }

    private static void chest(ServerLevel l, BlockPos o, int x, int y, int z, Direction facing, ItemStack... items) {
        BlockPos p = o.offset(x, y, z);
        l.setBlock(p, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing), FLAGS);
        BlockEntity be = l.getBlockEntity(p);
        if (be instanceof ChestBlockEntity c) {
            for (int i = 0; i < items.length && i < 27; i++) c.setItem(i, items[i]);
        }
    }

    private static void bed(ServerLevel l, BlockPos o, int x, int y, int z, Block bed, Direction facing) {
        BlockPos foot = o.offset(x, y, z);
        l.setBlock(foot, bed.defaultBlockState().setValue(BedBlock.FACING, facing).setValue(BedBlock.PART, BedPart.FOOT), FLAGS);
        l.setBlock(foot.relative(facing), bed.defaultBlockState().setValue(BedBlock.FACING, facing).setValue(BedBlock.PART, BedPart.HEAD), FLAGS);
    }

    private static void door(ServerLevel l, BlockPos o, int x, int y, int z, Direction facing) {
        BlockState d = Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.FACING, facing);
        l.setBlock(o.offset(x, y, z), d.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), FLAGS);
        l.setBlock(o.offset(x, y + 1, z), d.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), FLAGS);
    }

    private static BlockState stair(Block s, Direction facing) {
        return s.defaultBlockState().setValue(StairBlock.FACING, facing);
    }

    private static ItemStack st(net.minecraft.world.level.ItemLike item, int n) {
        return new ItemStack(item, n);
    }

    private static ItemStack ench(ServerLevel l, net.minecraft.world.level.ItemLike item, Object... pairs) {
        ItemStack s = new ItemStack(item);
        Registry<Enchantment> reg = l.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            @SuppressWarnings("unchecked") ResourceKey<Enchantment> key = (ResourceKey<Enchantment>) pairs[i];
            Holder<Enchantment> h = reg.getHolderOrThrow(key);
            s.enchant(h, (Integer) pairs[i + 1]);
        }
        return s;
    }

    private static void lights(ServerLevel l, BlockPos o, int x1, int x2, int z1, int z2, int y, int step) {
        for (int x = x1; x <= x2; x += step)
            for (int z = z1; z <= z2; z += step)
                set(l, o, x, y, z, b(Blocks.SEA_LANTERN));
    }

    // ------------------------------------------------------------------ the build

    private static void build(ServerLevel l, BlockPos o) {
        // make sure every chunk exists before we carve it
        for (int cx = (o.getX() - 2) >> 4; cx <= (o.getX() + SX + 2) >> 4; cx++)
            for (int cz = (o.getZ() - 2) >> 4; cz <= (o.getZ() + SZ + 2) >> 4; cz++)
                l.getChunk(cx, cz);

        BlockState wall = b(Unleashed.cellWall());
        BlockState air = b(Blocks.AIR);
        fill(l, o, 0, -1, 0, SX, SY - 1, SZ, wall);

        // --- main hall
        fill(l, o, 2, 0, 19, 52, 5, 25, air);
        fill(l, o, 2, -1, 19, 52, -1, 25, b(Blocks.POLISHED_DEEPSLATE));
        fill(l, o, 3, 0, 22, 47, 0, 22, b(Blocks.RED_CARPET));
        lights(l, o, 4, 52, 20, 24, 6, 4);
        fill(l, o, 4, -1, 21, 6, -1, 23, b(Blocks.CHISELED_QUARTZ_BLOCK));

        // --- the gate approach (tall) and the Gate itself
        fill(l, o, 40, 0, 18, 52, 8, 26, air);
        fill(l, o, 40, -1, 18, 52, -1, 26, b(Blocks.POLISHED_BLACKSTONE));
        lights(l, o, 42, 50, 19, 25, 9, 4);
        fill(l, o, 53, 0, 19, 54, 7, 25, b(Blocks.GILDED_BLACKSTONE));
        fill(l, o, 53, 0, 20, 54, 6, 24, air);
        fill(l, o, 53, -1, 20, 54, -1, 24, b(Blocks.GOLD_BLOCK));

        // --- ascension chamber
        fill(l, o, 55, 0, 14, 64, 8, 30, air);
        fill(l, o, 55, -1, 14, 64, -1, 30, b(Blocks.QUARTZ_BLOCK));
        for (int[] c : new int[][]{{56, 15}, {63, 15}, {56, 29}, {63, 29}, {56, 22}, {63, 22}}) {
            fill(l, o, c[0], 0, c[1], c[0], 8, c[1], b(Blocks.CRYING_OBSIDIAN));
        }
        fill(l, o, 58, 0, 20, 62, 0, 24, b(Blocks.QUARTZ_BRICKS));
        fill(l, o, 59, 0, 21, 61, 0, 23, b(Blocks.GOLD_BLOCK));
        set(l, o, 60, 1, 22, b(ModBlocks.ASCENSION_ALTAR.get()));
        for (int[] c : new int[][]{{59, 21}, {61, 21}, {59, 23}, {61, 23}}) set(l, o, c[0], 1, c[1], b(Blocks.END_ROD));
        lights(l, o, 57, 63, 16, 28, 9, 3);

        // --- council chamber
        fill(l, o, 18, 0, 4, 36, 6, 17, air);
        fill(l, o, 18, -1, 4, 36, -1, 17, b(Blocks.POLISHED_BLACKSTONE_BRICKS));
        fill(l, o, 26, 0, 18, 28, 3, 18, air);
        fill(l, o, 23, 0, 8, 31, 0, 13, b(Blocks.POLISHED_BLACKSTONE));
        fill(l, o, 24, 0, 9, 30, 0, 12, b(Blocks.CRIMSON_PLANKS));
        lights(l, o, 20, 34, 6, 16, 7, 4);
        set(l, o, 27, 0, 5, stair(Blocks.QUARTZ_STAIRS, Direction.NORTH));
        set(l, o, 27, 0, 4, b(Blocks.GOLD_BLOCK));
        set(l, o, 27, 1, 4, b(Blocks.GOLD_BLOCK));
        int[][] seats = {{24, 7}, {26, 7}, {28, 7}, {30, 7}, {24, 14}, {26, 14}, {28, 14}, {30, 14}, {22, 9}, {22, 12}, {32, 9}, {32, 12}};
        for (int i = 0; i < seats.length; i++) {
            int[] s = seats[i];
            Direction f = s[1] == 7 ? Direction.NORTH : s[1] == 14 ? Direction.SOUTH : s[0] == 22 ? Direction.WEST : Direction.EAST;
            set(l, o, s[0], 0, s[1], stair(Blocks.POLISHED_BLACKSTONE_STAIRS, f));
            O5MemberEntity m = ModEntities.O5_MEMBER.get().create(l);
            if (m != null) {
                m.setSeat(O5.seat(i + 1));
                double ex = o.getX() + s[0] + 0.5, ez = o.getZ() + s[1] + 0.5;
                float yaw = (float) (Mth.atan2((o.getZ() + 10.5) - ez, (o.getX() + 27.5) - ex) * Mth.RAD_TO_DEG) - 90F;
                m.moveTo(ex, o.getY() + 0.5, ez, yaw, 0);
                m.setYHeadRot(yaw);
                m.setYBodyRot(yaw);
                l.addFreshEntity(m);
            }
        }

        // --- containment wing: two cells walled in SCP Unleashed reinforced containment wall
        fill(l, o, 4, 0, 4, 16, 5, 17, air);
        fill(l, o, 9, 0, 18, 11, 3, 18, air);
        fill(l, o, 4, -1, 4, 16, -1, 17, b(Blocks.SMOOTH_STONE));
        lights(l, o, 6, 14, 12, 16, 6, 4);
        for (int cx : new int[]{5, 11}) {
            fill(l, o, cx, 0, 5, cx + 4, 3, 9, wall);
            fill(l, o, cx + 1, 0, 6, cx + 3, 2, 8, air);
            set(l, o, cx + 2, 3, 7, b(Blocks.SEA_LANTERN));
            door(l, o, cx + 2, 0, 9, Direction.SOUTH);
            // exit from inside the cell
            set(l, o, cx + 3, 1, 8, Blocks.STONE_BUTTON.defaultBlockState().setValue(ButtonBlock.FACE, net.minecraft.world.level.block.state.properties.AttachFace.WALL).setValue(ButtonBlock.FACING, Direction.NORTH));
            set(l, o, cx + 3, 1, 10, Blocks.LEVER.defaultBlockState()
                    .setValue(LeverBlock.FACE, net.minecraft.world.level.block.state.properties.AttachFace.WALL).setValue(LeverBlock.FACING, Direction.SOUTH));
        }

        // --- archive
        fill(l, o, 38, 0, 4, 52, 5, 15, air);
        fill(l, o, 44, 0, 16, 46, 3, 17, air);
        fill(l, o, 38, -1, 4, 52, -1, 15, b(Blocks.DARK_OAK_PLANKS));
        fill(l, o, 38, 0, 4, 38, 3, 15, b(Blocks.BOOKSHELF));
        fill(l, o, 52, 0, 4, 52, 3, 15, b(Blocks.BOOKSHELF));
        fill(l, o, 39, 0, 4, 51, 3, 4, b(Blocks.BOOKSHELF));
        for (int x = 41; x <= 49; x += 4) set(l, o, x, 0, 9, b(Blocks.LECTERN));
        set(l, o, 45, 0, 12, b(Unleashed.block("archive", Blocks.LECTERN))); // SCP Unleashed's Archive Terminal
        lights(l, o, 40, 50, 6, 14, 6, 4);
        chest(l, o, 45, 0, 5, Direction.SOUTH,
                st(Unleashed.item("facility_tablet", Items.MAP), 1), st(ModItems.PHONE.get(), 1), st(Unleashed.keycard(1), 1), st(Unleashed.keycard(2), 1),
                st(Unleashed.keycard(3), 1), st(Unleashed.keycard(4), 1), st(Unleashed.keycard(5), 2), st(Unleashed.keycard(6), 1),
                st(Items.BOOK, 32), st(Items.PAPER, 64));

        // --- armory
        fill(l, o, 4, 0, 27, 14, 4, 40, air);
        fill(l, o, 8, 0, 26, 10, 3, 26, air);
        fill(l, o, 4, -1, 27, 14, -1, 40, b(Blocks.IRON_BLOCK));
        lights(l, o, 6, 12, 29, 39, 5, 4);
        chest(l, o, 5, 0, 30, Direction.EAST,
                ench(l, Items.NETHERITE_SWORD, Enchantments.SHARPNESS, 5, Enchantments.LOOTING, 3, Enchantments.UNBREAKING, 3),
                ench(l, Items.BOW, Enchantments.POWER, 5, Enchantments.INFINITY, 1, Enchantments.FLAME, 1),
                st(Items.ARROW, 64), st(Items.SHIELD, 1), ench(l, Items.CROSSBOW, Enchantments.QUICK_CHARGE, 3, Enchantments.MULTISHOT, 1),
                st(Items.TOTEM_OF_UNDYING, 2), st(Items.ENDER_PEARL, 16), st(Items.GOLDEN_CARROT, 32), st(Items.CARVED_PUMPKIN, 2));
        chest(l, o, 5, 0, 33, Direction.EAST,
                ench(l, Items.NETHERITE_HELMET, Enchantments.PROTECTION, 4, Enchantments.UNBREAKING, 3),
                ench(l, Items.NETHERITE_CHESTPLATE, Enchantments.PROTECTION, 4, Enchantments.UNBREAKING, 3),
                ench(l, Items.NETHERITE_LEGGINGS, Enchantments.PROTECTION, 4, Enchantments.UNBREAKING, 3),
                ench(l, Items.NETHERITE_BOOTS, Enchantments.PROTECTION, 4, Enchantments.FEATHER_FALLING, 4));
        set(l, o, 13, 0, 30, b(Blocks.ANVIL));
        set(l, o, 13, 0, 33, b(Blocks.SMITHING_TABLE));
        set(l, o, 13, 0, 36, b(Blocks.GRINDSTONE));

        // --- medical bay
        fill(l, o, 17, 0, 27, 27, 4, 40, air);
        fill(l, o, 21, 0, 26, 23, 3, 26, air);
        fill(l, o, 17, -1, 27, 27, -1, 40, b(Blocks.WHITE_CONCRETE));
        lights(l, o, 19, 25, 29, 39, 5, 3);
        for (int x = 18; x <= 24; x += 3) bed(l, o, x, 0, 38, Blocks.WHITE_BED, Direction.SOUTH);
        chest(l, o, 26, 0, 30, Direction.WEST,
                st(Unleashed.scp500(), 6), st(Items.GOLDEN_APPLE, 16), st(Items.ENCHANTED_GOLDEN_APPLE, 2),
                st(Unleashed.item("amnestic_canister", Items.MILK_BUCKET), 8), st(Unleashed.item("scp207_bottle", Items.HONEY_BOTTLE), 2), st(Items.MILK_BUCKET, 1));
        set(l, o, 26, 0, 33, b(Blocks.BREWING_STAND));

        // --- quarters
        fill(l, o, 30, 0, 27, 40, 4, 40, air);
        fill(l, o, 34, 0, 26, 36, 3, 26, air);
        fill(l, o, 30, -1, 27, 40, -1, 40, b(Blocks.SPRUCE_PLANKS));
        lights(l, o, 32, 38, 28, 39, 5, 3);
        bed(l, o, 38, 0, 38, Blocks.PURPLE_BED, Direction.SOUTH);
        set(l, o, 31, 0, 28, b(Blocks.CRAFTING_TABLE));
        set(l, o, 32, 0, 28, b(Blocks.FURNACE));
        set(l, o, 33, 0, 28, b(Blocks.BLAST_FURNACE));
        set(l, o, 34, 0, 28, b(Blocks.SMOKER));
        set(l, o, 39, 0, 28, b(Blocks.ENDER_CHEST));
        set(l, o, 35, 0, 34, b(Blocks.ENCHANTING_TABLE));
        for (int x = 33; x <= 37; x++) for (int z = 32; z <= 36; z++) {
            boolean edge = x == 33 || x == 37 || z == 32 || z == 36;
            boolean gap = (x == 35 && z == 32);
            if (edge && !gap) { set(l, o, x, 0, z, b(Blocks.BOOKSHELF)); set(l, o, x, 1, z, b(Blocks.BOOKSHELF)); }
        }
        chest(l, o, 31, 0, 39, Direction.NORTH,
                st(Items.COOKED_BEEF, 64), st(Items.BREAD, 32), st(Items.TORCH, 64), st(Items.FIREWORK_ROCKET, 64), st(Items.ELYTRA, 1),
                ench(l, Items.NETHERITE_PICKAXE, Enchantments.EFFICIENCY, 5, Enchantments.FORTUNE, 3, Enchantments.UNBREAKING, 3),
                ench(l, Items.NETHERITE_AXE, Enchantments.EFFICIENCY, 5, Enchantments.UNBREAKING, 3),
                ench(l, Items.NETHERITE_SHOVEL, Enchantments.EFFICIENCY, 5, Enchantments.SILK_TOUCH, 1), st(Items.LAPIS_LAZULI, 64), st(Items.EXPERIENCE_BOTTLE, 64));
        chest(l, o, 33, 0, 39, Direction.NORTH,
                st(Unleashed.item("reinforced_containment_wall", Items.POLISHED_DEEPSLATE), 64), st(Unleashed.item("reinforced_containment_wall", Items.POLISHED_DEEPSLATE), 64),
                st(Unleashed.item("forcefield_emitter", Items.BEACON), 4), st(Unleashed.item("keycard_scanner", Items.STONE_BUTTON), 4),
                st(Unleashed.item("containment_door", Items.IRON_DOOR), 4), st(Unleashed.item("rift_extractor", Items.SPYGLASS), 1),
                st(Unleashed.item("mtf_signal_radio", Items.COMPASS), 1), st(Unleashed.item("scramble_goggles", Items.CARVED_PUMPKIN), 1),
                st(Unleashed.item("scp096_bag", Items.LEATHER), 1), st(Items.REDSTONE_BLOCK, 8),
                st(Items.BEACON, 1), st(Items.DIAMOND_BLOCK, 4), st(Items.IRON_BLOCK, 16), st(Items.OBSIDIAN, 32));
        buildSouthWing(l, o);
    }

    // ------------------------------------------------------------------ south wing

    /**
     * The Administrator's own wing: a private office behind a Level 5 scanner, a research lab, the
     * server and power room, and MTF barracks - furnished with SCP Unleashed's own blocks and gear.
     */
    private static void buildSouthWing(ServerLevel l, BlockPos o) {
        for (int cx = (o.getX() - 2) >> 4; cx <= (o.getX() + SX + 2) >> 4; cx++)
            for (int cz = (o.getZ() + 40) >> 4; cz <= (o.getZ() + SZ + 2) >> 4; cz++)
                l.getChunk(cx, cz);
        BlockState wall = b(Unleashed.cellWall()), air = b(Blocks.AIR);
        fill(l, o, 0, -1, 45, SX, SY - 1, SZ, wall);

        // corridor from the main hall, and the wing hall
        fill(l, o, 44, 0, 26, 46, 4, 46, air);
        fill(l, o, 44, -1, 26, 46, -1, 46, u("ez_floor", Blocks.POLISHED_ANDESITE));
        fill(l, o, 4, 0, 46, 62, 4, 48, air);
        fill(l, o, 4, -1, 46, 62, -1, 48, u("ez_floor", Blocks.POLISHED_ANDESITE));
        for (int x = 6; x <= 60; x += 6) lamp(l, o, x, 4, 47, "facility_lamp_white");
        for (int z = 28; z <= 44; z += 5) lamp(l, o, 45, 4, z, "facility_lamp_white");
        wallPut(l, o, 45, 3, 26, "facility_sign_entrance_zone_small");

        // --- research lab
        fill(l, o, 4, 0, 50, 20, 4, 69, air);
        fill(l, o, 11, 0, 49, 13, 3, 49, air);
        fill(l, o, 4, -1, 50, 20, -1, 69, u("facility_lab_tile", Blocks.WHITE_CONCRETE));
        for (int x = 6; x <= 18; x += 4) for (int z = 52; z <= 68; z += 5) lamp(l, o, x, 4, z, "facility_lamp_cyan");
        put(l, o, 5, 0, 52, "centrifuge", Blocks.CAULDRON);
        put(l, o, 5, 0, 55, "advanced_assembler", Blocks.CRAFTING_TABLE);
        put(l, o, 5, 0, 58, "advanced_materials_bench", Blocks.SMITHING_TABLE);
        put(l, o, 5, 0, 62, "cloning_machine", Blocks.BREWING_STAND);
        for (int z = 52; z <= 60; z += 4) put(l, o, 19, 0, z, "containment_room_specimen_tank", Blocks.GLASS);
        put(l, o, 12, 0, 63, "examination_table", Blocks.WHITE_BED);
        for (int x = 8; x <= 16; x += 2) set(l, o, x, 0, 68, u("chemical_shelf", Blocks.BOOKSHELF));
        put(l, o, 19, 0, 64, "computer_terminal", Blocks.LECTERN);
        chest(l, o, 19, 0, 67, Direction.WEST,
                ui("hazmat_hood", Items.LEATHER_HELMET, 2), ui("hazmat_suit", Items.LEATHER_CHESTPLATE, 2), ui("hazmat_trousers", Items.LEATHER_LEGGINGS, 2),
                ui("hazmat_boots", Items.LEATHER_BOOTS, 2), ui("gas_mask", Items.LEATHER_HELMET, 2), ui("scramble_goggles", Items.CARVED_PUMPKIN, 2),
                ui("energum_dust", Items.GLOWSTONE_DUST, 32), ui("rift_extractor", Items.SPYGLASS, 1), ui("rift_detector", Items.COMPASS, 1),
                ui("stasis_pod_faint", Items.GLASS_BOTTLE, 4), ui("stasis_pod_volatile", Items.GLASS_BOTTLE, 2), ui("scp096_bag", Items.LEATHER, 1),
                ui("amnestic_canister", Items.MILK_BUCKET, 6));
        wallPut(l, o, 12, 3, 49, "facility_sign_maintenance_small");

        // --- server and power room
        fill(l, o, 23, 0, 50, 40, 4, 58, air);
        fill(l, o, 30, 0, 49, 32, 3, 49, air);
        fill(l, o, 23, -1, 50, 40, -1, 58, u("power_floor", Blocks.POLISHED_BLACKSTONE));
        for (int x = 25; x <= 39; x += 5) lamp(l, o, x, 4, 54, "power_lamp");
        put(l, o, 24, 0, 51, "network_hub", Blocks.LODESTONE);
        put(l, o, 26, 0, 51, "network_router", Blocks.OBSERVER);
        put(l, o, 28, 0, 51, "chat_server", Blocks.JUKEBOX);
        put(l, o, 34, 0, 51, "power_control_console", Blocks.LECTERN);
        set(l, o, 39, 0, 52, u("power_generator_housing_bottom", Blocks.IRON_BLOCK));
        set(l, o, 39, 1, 52, u("power_generator_housing_middle", Blocks.IRON_BLOCK));
        set(l, o, 39, 2, 52, u("power_generator_housing_top", Blocks.IRON_BLOCK));
        set(l, o, 39, 0, 56, u("power_breaker_panel_bottom", Blocks.IRON_BLOCK));
        set(l, o, 39, 1, 56, u("power_breaker_panel_middle", Blocks.IRON_BLOCK));
        set(l, o, 39, 2, 56, u("power_breaker_panel_top", Blocks.IRON_BLOCK));
        for (int x = 24; x <= 38; x++) set(l, o, x, 3, 57, u("cable_tray", Blocks.IRON_BARS));
        wallPut(l, o, 31, 3, 49, "facility_sign_server_room_small");
        chest(l, o, 24, 0, 57, Direction.NORTH,
                ui("facility_tablet", Items.MAP, 1), ui("camera_tablet", Items.MAP, 1), ui("power_cable_spool", Items.STRING, 8));

        // --- MTF barracks
        fill(l, o, 23, 0, 61, 40, 4, 69, air);
        fill(l, o, 23, 0, 59, 25, 3, 60, air);
        fill(l, o, 23, -1, 61, 40, -1, 69, u("facility_diamond_plate", Blocks.IRON_BLOCK));
        for (int x = 25; x <= 39; x += 5) lamp(l, o, x, 4, 65, "facility_lamp_white");
        for (int x = 27; x <= 39; x += 4) bed(l, o, x, 0, 67, Blocks.GRAY_BED, Direction.SOUTH);
        chest(l, o, 24, 0, 62, Direction.EAST,
                ui("assault_rifle", Items.CROSSBOW, 2), ui("combat_shotgun", Items.CROSSBOW, 1), ui("pistol", Items.CROSSBOW, 2), ui("machine_gun", Items.CROSSBOW, 1),
                ui("rifle_rounds", Items.ARROW, 64), ui("rifle_rounds", Items.ARROW, 64), ui("handgun_rounds", Items.ARROW, 64), ui("shotgun_shells", Items.ARROW, 32),
                ui("frag_grenade", Items.TNT, 8), ui("flash_grenade", Items.SNOWBALL, 8), ui("gas_grenade", Items.SPLASH_POTION, 4));
        chest(l, o, 24, 0, 65, Direction.EAST,
                ui("mtf_helmet", Items.IRON_HELMET, 4), ui("mtf_vest", Items.IRON_CHESTPLATE, 4), ui("mtf_signal_radio", Items.COMPASS, 1),
                ui("unstable_fragment_volatile", Items.AMETHYST_SHARD, 4), ui("unstable_fragment_severe", Items.AMETHYST_SHARD, 2), ui("unstable_fragment_abyssal", Items.AMETHYST_SHARD, 1),
                ui("scp3008_bandage", Items.PAPER, 16));
        wallPut(l, o, 24, 3, 60, "facility_sign_armory_small");

        // --- the Administrator's office (Level 5 to enter)
        fill(l, o, 43, 0, 50, 62, 5, 69, air);
        fill(l, o, 43, -1, 50, 62, -1, 69, u("facility_carpet_maroon", Blocks.RED_CARPET));
        door(l, o, 52, 0, 49, Direction.SOUTH);
        BlockState scanner = u("keycard_scanner", Blocks.STONE_BUTTON);
        for (net.minecraft.world.level.block.state.properties.Property<?> prop : scanner.getProperties()) {
            if (prop.getName().equals("level") && prop instanceof net.minecraft.world.level.block.state.properties.IntegerProperty ip && ip.getPossibleValues().contains(5)) scanner = scanner.setValue(ip, 5);
            if (prop.getName().equals("facing") && prop instanceof net.minecraft.world.level.block.state.properties.DirectionProperty dp && dp.getPossibleValues().contains(Direction.NORTH)) scanner = scanner.setValue(dp, Direction.NORTH);
        }
        set(l, o, 53, 1, 48, scanner);
        // the way out: a button inside the office, beside the door
        set(l, o, 53, 1, 50, Blocks.POLISHED_BLACKSTONE_BUTTON.defaultBlockState().setValue(ButtonBlock.FACE, net.minecraft.world.level.block.state.properties.AttachFace.WALL).setValue(ButtonBlock.FACING, Direction.SOUTH));
        wallPut(l, o, 52, 3, 48, "facility_sign_heavy_containment_small");
        for (int x = 45; x <= 61; x += 4) for (int z = 52; z <= 68; z += 4) lamp(l, o, x, 5, z, "facility_lamp_purple");
        // the desk, facing the door
        put(l, o, 51, 0, 64, "corner_desk", Blocks.DARK_OAK_PLANKS);
        put(l, o, 52, 0, 64, "corner_desk", Blocks.DARK_OAK_PLANKS);
        put(l, o, 53, 0, 64, "corner_desk", Blocks.DARK_OAK_PLANKS);
        put(l, o, 51, 1, 64, "crt_monitor", Blocks.AIR);
        put(l, o, 52, 1, 64, "computer", Blocks.AIR);
        put(l, o, 53, 1, 64, "desk_lamp", Blocks.AIR);
        set(l, o, 52, 0, 65, stair(Blocks.DARK_OAK_STAIRS, Direction.SOUTH));
        // records wall
        for (int x = 44; x <= 61; x++) { set(l, o, x, 0, 69, u("binder_shelf", Blocks.BOOKSHELF)); set(l, o, x, 1, 69, u("binder_shelf", Blocks.BOOKSHELF)); }
        put(l, o, 44, 0, 60, "archive", Blocks.LECTERN);
        put(l, o, 44, 0, 62, "archive_database_terminal", Blocks.LECTERN);
        // briefing table
        for (int x = 48; x <= 56; x++) put(l, o, x, 0, 55, "conference_table", Blocks.DARK_OAK_SLAB);
        for (int x = 48; x <= 56; x += 2) { set(l, o, x, 0, 54, stair(Blocks.DARK_OAK_STAIRS, Direction.SOUTH)); set(l, o, x, 0, 56, stair(Blocks.DARK_OAK_STAIRS, Direction.NORTH)); }
        for (int[] c : new int[][]{{44, 51}, {61, 51}, {44, 68}, {61, 68}}) put(l, o, c[0], 0, c[1], "ez_planter", Blocks.POTTED_FERN);
        bed(l, o, 60, 0, 59, Blocks.PURPLE_BED, Direction.SOUTH);
        chest(l, o, 61, 0, 62, Direction.WEST,
                ui("keycard_omni", Items.PAPER, 1), ui("orbital_designator", Items.SPYGLASS, 1), ui("stasis_pod_abyssal", Items.GLASS_BOTTLE, 2),
                ui("mtf_signal_radio", Items.COMPASS, 1), ui("rift_detector", Items.COMPASS, 1), ui("camera_tablet", Items.MAP, 1), ui("facility_tablet", Items.MAP, 1),
                new ItemStack(Unleashed.scp500(), 4), ui("scp207_bottle", Items.HONEY_BOTTLE, 2), ui("amnestic_canister", Items.MILK_BUCKET, 4),
                st(com.breachprotocol.registry.ModItems.PHONE.get(), 1));
    }
}
