package com.breachprotocol.world;

import com.breachprotocol.block.ContainmentGeneratorBlock;
import com.breachprotocol.block.KeycardReaderBlock;
import com.breachprotocol.career.O5;
import com.breachprotocol.entity.GateGuardianEntity;
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
 * a two-cell containment wing - and at the far end, the Gate, its Guardian, and the Altar.
 */
public final class Site01 {
    private Site01() {}

    public static final int SX = 66, SY = 10, SZ = 44;
    private static final int FLAGS = Block.UPDATE_CLIENTS;

    // ------------------------------------------------------------------ saved state

    public static class Data extends SavedData {
        public boolean built;

        public static final SavedData.Factory<Data> FACTORY = new SavedData.Factory<>(Data::new, Data::load, null);

        public static Data load(CompoundTag tag, HolderLookup.Provider provider) {
            Data d = new Data();
            d.built = tag.getBoolean("built");
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
            tag.putBoolean("built", built);
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
            data.setDirty();
        }
        return o;
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

        BlockState wall = b(ModBlocks.CONTAINMENT_WALL.get());
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

        // --- containment wing: two cells, each with its own powered generator and a level-5 reader
        fill(l, o, 4, 0, 4, 16, 5, 17, air);
        fill(l, o, 9, 0, 18, 11, 3, 18, air);
        fill(l, o, 4, -1, 4, 16, -1, 17, b(Blocks.SMOOTH_STONE));
        lights(l, o, 6, 14, 12, 16, 6, 4);
        for (int cx : new int[]{5, 11}) {
            fill(l, o, cx, 0, 5, cx + 4, 3, 9, wall);
            fill(l, o, cx + 1, 0, 6, cx + 3, 2, 8, air);
            set(l, o, cx + 2, 3, 7, b(Blocks.SEA_LANTERN));
            door(l, o, cx + 2, 0, 9, Direction.SOUTH);
            set(l, o, cx + 3, 1, 10, ModBlocks.KEYCARD_READER.get().defaultBlockState()
                    .setValue(KeycardReaderBlock.FACING, Direction.SOUTH).setValue(KeycardReaderBlock.LEVEL, 5));
            set(l, o, cx + 2, -2, 7, b(Blocks.REDSTONE_BLOCK));
            set(l, o, cx + 2, -1, 7, ModBlocks.CONTAINMENT_GENERATOR.get().defaultBlockState().setValue(ContainmentGeneratorBlock.ACTIVE, true));
        }

        // --- archive
        fill(l, o, 38, 0, 4, 52, 5, 15, air);
        fill(l, o, 44, 0, 16, 46, 3, 17, air);
        fill(l, o, 38, -1, 4, 52, -1, 15, b(Blocks.DARK_OAK_PLANKS));
        fill(l, o, 38, 0, 4, 38, 3, 15, b(Blocks.BOOKSHELF));
        fill(l, o, 52, 0, 4, 52, 3, 15, b(Blocks.BOOKSHELF));
        fill(l, o, 39, 0, 4, 51, 3, 4, b(Blocks.BOOKSHELF));
        for (int x = 41; x <= 49; x += 4) set(l, o, x, 0, 9, b(Blocks.LECTERN));
        lights(l, o, 40, 50, 6, 14, 6, 4);
        chest(l, o, 45, 0, 5, Direction.SOUTH,
                st(ModItems.SITE_TERMINAL.get(), 1), st(ModItems.PHONE.get(), 1), st(ModItems.KEYCARD_1.get(), 1), st(ModItems.KEYCARD_2.get(), 1),
                st(ModItems.KEYCARD_3.get(), 1), st(ModItems.KEYCARD_4.get(), 1), st(ModItems.KEYCARD_5.get(), 2), st(Items.BOOK, 32), st(Items.PAPER, 64));

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
                st(ModItems.SCP_500.get(), 6), st(Items.GOLDEN_APPLE, 16), st(Items.ENCHANTED_GOLDEN_APPLE, 2),
                st(ModItems.EYEDROPS.get(), 16), st(ModItems.SCP_207.get(), 2), st(Items.MILK_BUCKET, 1));
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
                st(ModItems.CONTAINMENT_GENERATOR.get(), 4), st(ModItems.CONTAINMENT_WALL.get(), 64), st(ModItems.CONTAINMENT_WALL.get(), 64),
                st(ModItems.KEYCARD_READER.get(), 4), st(Items.IRON_DOOR, 8), st(Items.REDSTONE_BLOCK, 8), st(Items.LEVER, 8),
                st(Items.BEACON, 1), st(Items.DIAMOND_BLOCK, 4), st(Items.IRON_BLOCK, 16), st(Items.OBSIDIAN, 32));

        // --- the Guardian, standing before the Gate
        GateGuardianEntity g = ModEntities.GATE_GUARDIAN.get().create(l);
        if (g != null) {
            g.moveTo(o.getX() + 49.5, o.getY() + 0.2, o.getZ() + 22.5, 90F, 0);
            g.setYHeadRot(90F);
            g.setYBodyRot(90F);
            l.addFreshEntity(g);
        }
    }
}
