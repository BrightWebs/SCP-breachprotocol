package com.breachprotocol.garden;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.career.Career;
import com.breachprotocol.career.GatePass;
import com.breachprotocol.career.O5;
import com.breachprotocol.career.Rank;
import com.breachprotocol.compat.UnleashedApi;
import com.breachprotocol.registry.ModEffects;
import com.breachprotocol.registry.ModItems;
import com.breachprotocol.world.Placer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.decoration.GlowItemFrame;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The Garden beyond SCP Unleashed's Gate (their dimension scp_unleashed:scp001_garden), made finite:
 * one island, about 150 blocks across, floating in the golden evening of their sky.
 *
 * <ul>
 * <li>Arrive at SCP Unleashed's arch on the north shore. A path leads south to a plaza between the
 *     two trees: the Tree of Life on your left (east), the Tree of Knowledge of Good and Evil on your
 *     right (west). One river crosses the whole island and falls off both edges.</li>
 * <li>An orchard of every fruit: apples, pears, peaches, plums, figs, pomegranates, olives, dates and
 *     grapes, hanging from the branches. Right-click to pick; fruit grows back.</li>
 * <li>Fruit of Life: Immortal for three Minecraft days. Come back for more.</li>
 * <li>Fruit of Knowledge: O5 and above gain great knowledge. Anyone below O5 is cast out at once and
 *     the Gate Guardian keeps them away for a full real-life day.</li>
 * <li>Everything is at peace: nothing can be hurt here, nothing hunts. Animals graze. Beds work.
 *     Fall off the edge and the Garden puts you back on the shore.</li>
 * </ul>
 */
@EventBusSubscriber(modid = BreachProtocol.MODID)
public final class Garden {
    private Garden() {}

    public static final ResourceKey<Level> KEY = ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("scp_unleashed", "scp001_garden"));
    private static final int VERSION = 1;
    /** Island centre (the arch is at the origin, on the north shore). */
    private static final int CX = 0, CZ = 46;
    private static final int LIFE_X = 30, KNOW_X = -30, TREES_Z = 62, PLAZA_Z = 60;
    public static final String FRUIT_TAG = "bp_fruit";
    private static final long REAL_DAY_MS = 24L * 60 * 60 * 1000;

    public static boolean inside(Level l) { return l != null && l.dimension() == KEY; }

    // ================================================================== saved state

    public static class Data extends SavedData {
        int version, ground = 72;
        public static final SavedData.Factory<Data> FACTORY = new SavedData.Factory<>(Data::new, Data::load, null);

        static Data load(CompoundTag t, HolderLookup.Provider p) {
            Data d = new Data();
            d.version = t.getInt("version");
            d.ground = t.contains("ground") ? t.getInt("ground") : 72;
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag t, HolderLookup.Provider p) {
            t.putInt("version", version);
            t.putInt("ground", ground);
            return t;
        }
    }

    private static Data data(ServerLevel g) { return g.getDataStorage().computeIfAbsent(Data.FACTORY, "breachprotocol_garden"); }

    // ================================================================== building

    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        ServerLevel g = event.getServer().getLevel(KEY);
        if (g == null) return;
        try {
            Data d = data(g);
            if (d.version >= VERSION) return;
            long t0 = System.currentTimeMillis();
            new Builder(g).build(d);
            d.version = VERSION;
            d.setDirty();
            BreachProtocol.LOGGER.info("Built the Garden in {} ms", System.currentTimeMillis() - t0);
        } catch (Throwable t) {
            BreachProtocol.LOGGER.error("Could not build the Garden", t);
        }
    }

    private static final class Builder {
        final ServerLevel l;
        final Random rnd = new Random(0x6A7DE7L);
        int S;  // the island's surface level (grass); you stand at S+1
        final int Y0 = 0;

        Builder(ServerLevel l) { this.l = l; }

        static double rim(double ang) { return 76 + 7 * Math.sin(3 * ang) + 4 * Math.sin(5 * ang + 1.3); }

        static double riverZ(int x) { return 92 + 7 * Math.sin(x / 15.0); }

        boolean onIsland(int x, int z) {
            double dx = x - CX, dz = z - CZ;
            return Math.hypot(dx, dz) <= rim(Math.atan2(dz, dx));
        }

        double edgeDist(int x, int z) {
            double dx = x - CX, dz = z - CZ;
            return rim(Math.atan2(dz, dx)) - Math.hypot(dx, dz);
        }

        boolean archZone(int x, int z) { return Math.abs(x) <= 14 && z >= -7 && z <= 7; }

        int height(int x, int z) {
            double flat = 1;
            flat = Math.min(flat, Math.max(0, (Math.hypot(x, z) - 16) / 10));
            flat = Math.min(flat, Math.max(0, (Math.abs(z - riverZ(x)) - 5) / 6));
            flat = Math.min(flat, Math.max(0, (Math.hypot(x, z - PLAZA_Z) - 10) / 8));
            flat = Math.min(flat, Math.max(0, (Math.hypot(x - LIFE_X, z - TREES_Z) - 9) / 6));
            flat = Math.min(flat, Math.max(0, (Math.hypot(x - KNOW_X, z - TREES_Z) - 9) / 6));
            if (Math.abs(x) <= 3 && z > 4 && z < 120) flat = Math.min(flat, 0.3);
            flat = Math.min(flat, Math.max(0, edgeDist(x, z) / 6));
            double n = 1.6 * Math.sin(x * 0.11) + 1.3 * Math.cos(z * 0.09) + 0.9 * Math.sin((x + z) * 0.07) + 0.6;
            return (int) Math.round(Math.max(-1, Math.min(3, n)) * flat);
        }

        void set(int x, int y, int z, BlockState s) { l.setBlock(new BlockPos(x, y, z), s, Block.UPDATE_CLIENTS); }

        BlockState get(int x, int y, int z) { return l.getBlockState(new BlockPos(x, y, z)); }

        void build(Data d) {
            // SCP Unleashed's own record of the Garden: keep their arch where it already is, or build it now
            Object theirs = null;
            Class<?> built = UnleashedApi.cls("scp001gate.Scp001GateGarden$Built");
            boolean ready = false;
            int ground = 72;
            try {
                if (built != null) {
                    Object factory = built.getField("DataFactory").get(null);
                    String name = (String) built.getField("DataName").get(null);
                    @SuppressWarnings("unchecked")
                    SavedData.Factory<SavedData> f = (SavedData.Factory<SavedData>) factory;
                    theirs = l.getDataStorage().computeIfAbsent(f, name);
                    ready = built.getField("Ready").getBoolean(theirs);
                    if (ready) ground = built.getField("Ground").getInt(theirs);
                }
            } catch (Throwable t) {
                BreachProtocol.LOGGER.warn("Garden: couldn't read SCP Unleashed's garden record: {}", t.toString());
            }
            S = ground - 1;
            d.ground = ground;
            for (int cx = (CX - 96) >> 4; cx <= (CX + 96) >> 4; cx++)
                for (int cz = (CZ - 96) >> 4; cz <= (CZ + 96) >> 4; cz++)
                    l.getChunk(cx, cz);
            terrain();
            if (!ready && theirs != null) {
                try {
                    Method arch = UnleashedApi.cls("scp001gate.Scp001GateGarden").getMethod("buildArch", ServerLevel.class, BlockPos.class);
                    arch.invoke(null, l, new BlockPos(0, ground, 0));
                    built.getField("Ready").setBoolean(theirs, true);
                    built.getField("Ground").setInt(theirs, ground);
                    ((SavedData) theirs).setDirty();
                } catch (Throwable t) {
                    BreachProtocol.LOGGER.warn("Garden: couldn't build SCP Unleashed's arch: {}", t.toString());
                }
            }
            river();
            paths();
            treeOfLife(LIFE_X, TREES_Z);
            treeOfKnowledge(KNOW_X, TREES_Z);
            orchard();
            arbors();
            restGlade(-22, 20);
            flowers();
            animals();
        }

        // ---------------------------------------------------------------- the island

        void terrain() {
            BlockState air = Blocks.AIR.defaultBlockState();
            for (int x = CX - 92; x <= CX + 92; x++) {
                for (int z = CZ - 92; z <= CZ + 92; z++) {
                    boolean arch = archZone(x, z);
                    if (!onIsland(x, z)) {
                        // whatever the old endless Garden left here goes
                        for (int y = Math.max(Y0, S - 50); y <= S + 50; y++) if (!get(x, y, z).isAir()) set(x, y, z, air);
                        continue;
                    }
                    int top = S + height(x, z);
                    double edge = edgeDist(x, z);
                    int depth = (int) (4 + Math.min(edge, 40) * 0.55 + 2 * Math.sin(x * 0.3) * Math.cos(z * 0.27));
                    int bottom = Math.max(Y0 + 2, top - depth);
                    for (int y = Math.max(Y0, S - 50); y <= S + 50; y++) {
                        if (arch && y >= S) continue;          // SCP Unleashed's arch stands here
                        BlockState s;
                        if (y > top || y < bottom) s = air;
                        else if (y == top) s = Blocks.GRASS_BLOCK.defaultBlockState();
                        else if (y >= top - 3) s = Blocks.DIRT.defaultBlockState();
                        else if (y == bottom) s = rnd.nextInt(3) == 0 ? Blocks.ROOTED_DIRT.defaultBlockState() : Blocks.STONE.defaultBlockState();
                        else s = rnd.nextInt(9) == 0 ? Blocks.ANDESITE.defaultBlockState() : rnd.nextInt(14) == 0 ? Blocks.GRANITE.defaultBlockState() : Blocks.STONE.defaultBlockState();
                        if (s.isAir() && get(x, y, z).isAir()) continue;
                        set(x, y, z, s);
                    }
                    if (rnd.nextInt(7) == 0 && bottom > Y0 + 1) set(x, bottom - 1, z, Blocks.HANGING_ROOTS.defaultBlockState());
                    if (edge < 1.5 && rnd.nextInt(3) == 0) {
                        // vines down the cliff face
                        for (Direction side : Direction.Plane.HORIZONTAL) {
                            int ox = x + side.getStepX(), oz = z + side.getStepZ();
                            if (onIsland(ox, oz)) continue;
                            BlockState vine = Blocks.VINE.defaultBlockState().setValue(net.minecraft.world.level.block.VineBlock.getPropertyForFace(side.getOpposite()), true);
                            int len = 2 + rnd.nextInt(6);
                            for (int k = 1; k <= len && top - k > bottom; k++) set(ox, top - k, oz, vine);
                        }
                    }
                }
            }
        }

        void river() {
            BlockState water = Blocks.WATER.defaultBlockState();
            for (int x = CX - 92; x <= CX + 92; x++) {
                double rz = riverZ(x);
                for (int z = (int) Math.floor(rz - 5); z <= (int) Math.ceil(rz + 5); z++) {
                    if (!onIsland(x, z)) continue;
                    double dz = Math.abs(z - rz);
                    if (dz <= 2.6) {
                        int deep = dz <= 1.5 ? 3 : 2;
                        for (int y = S; y > S - deep; y--) set(x, y, z, water);
                        set(x, S - deep, z, rnd.nextInt(4) == 0 ? Blocks.CLAY.defaultBlockState() : rnd.nextInt(3) == 0 ? Blocks.GRAVEL.defaultBlockState() : Blocks.SAND.defaultBlockState());
                        for (int y = S + 1; y <= S + 4; y++) set(x, y, z, Blocks.AIR.defaultBlockState());
                        if (edgeDist(x, z) < 3) l.scheduleTick(new BlockPos(x, S, z), Fluids.WATER, 2);
                        if (dz <= 1.0 && rnd.nextInt(9) == 0) set(x, S - deep + 1, z, Blocks.SEAGRASS.defaultBlockState());
                    } else if (dz <= 4) {
                        set(x, S, z, rnd.nextInt(5) == 0 ? Blocks.MUD.defaultBlockState() : Blocks.SAND.defaultBlockState());
                        if (rnd.nextInt(6) == 0) set(x, S + 1, z, rnd.nextBoolean() ? Blocks.SUGAR_CANE.defaultBlockState() : Blocks.SHORT_GRASS.defaultBlockState());
                        if (dz > 3 && rnd.nextInt(10) == 0) {
                            set(x, S + 1, z, Blocks.SUGAR_CANE.defaultBlockState());
                            set(x, S + 2, z, Blocks.SUGAR_CANE.defaultBlockState());
                        }
                    }
                }
                if (Math.abs(x) % 7 == 3 && rnd.nextInt(2) == 0 && onIsland(x, (int) rz)) set(x, S + 1, (int) Math.round(rz), Blocks.LILY_PAD.defaultBlockState());
            }
        }

        void paths() {
            BlockState path = Blocks.DIRT_PATH.defaultBlockState();
            // from the arch south to the plaza, then on to the bridge and the south meadow
            for (int z = 7; z <= 118; z++) {
                for (int x = -1; x <= 1; x++) {
                    if (!onIsland(x, z)) continue;
                    double rz = riverZ(x);
                    if (Math.abs(z - rz) <= 4.5) continue;
                    int top = topAt(x, z);
                    if (get(x, top, z).is(Blocks.GRASS_BLOCK)) set(x, top, z, path);
                    set(x, top + 1, z, Blocks.AIR.defaultBlockState());
                }
            }
            // the plaza between the trees: a ring of moss and stone
            for (int x = -9; x <= 9; x++) for (int z = PLAZA_Z - 9; z <= PLAZA_Z + 9; z++) {
                double d = Math.hypot(x, z - PLAZA_Z);
                if (d > 8.5) continue;
                int top = S;
                set(x, top, z, d > 7 ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState() : d > 6 ? Blocks.MOSS_BLOCK.defaultBlockState() : path);
                set(x, top + 1, z, Blocks.AIR.defaultBlockState());
            }
            // paths from the plaza to each tree
            for (int x = -24; x <= 24; x++) for (int z = TREES_Z - 1; z <= TREES_Z + 1; z++) {
                if (Math.abs(x) < 8) continue;
                if (get(x, S, z).is(Blocks.GRASS_BLOCK)) set(x, S, z, path);
            }
            // a bridge over the river
            int rz = (int) Math.round(riverZ(0));
            for (int z = rz - 4; z <= rz + 4; z++) {
                for (int x = -2; x <= 2; x++) {
                    if (Math.abs(x) == 2) set(x, S + 1, z, Blocks.SPRUCE_FENCE.defaultBlockState()
                            .setValue(BlockStateProperties.NORTH, z > rz - 4).setValue(BlockStateProperties.SOUTH, z < rz + 4));
                    else set(x, S + 1, z, Blocks.SPRUCE_SLAB.defaultBlockState());
                }
            }
            for (int x : new int[]{-2, 2}) for (int z : new int[]{rz - 4, rz + 4}) set(x, S + 2, z, Blocks.LANTERN.defaultBlockState());
        }

        int topAt(int x, int z) {
            for (int y = S + 6; y >= S - 3; y--) if (!get(x, y, z).isAir() && !get(x, y, z).canBeReplaced()) return y;
            return S;
        }

        // ---------------------------------------------------------------- the two trees

        void disc(int cx, int cz, int r, BlockState s) {
            for (int x = cx - r; x <= cx + r; x++) for (int z = cz - r; z <= cz + r; z++)
                if (Math.hypot(x - cx, z - cz) <= r + 0.3) set(x, S, z, s);
        }

        /** A great tree: a 3x3 trunk, eight rising branches, a broad crown. */
        List<BlockPos> grandTree(int bx, int bz, BlockState log, BlockState[] leaves, BlockState glow, int trunk) {
            List<BlockPos> crown = new ArrayList<>();
            BlockPos base = new BlockPos(bx, S + 1, bz);
            for (int y = -3; y < trunk; y++)
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                    if (y > trunk - 4 && Math.abs(dx) + Math.abs(dz) == 2) continue;
                    l.setBlock(base.offset(dx, y, dz), log, Block.UPDATE_CLIENTS);
                }
            // roots
            for (Direction side : Direction.Plane.HORIZONTAL)
                for (int step = 2; step <= 4; step++)
                    l.setBlock(base.relative(side, step).below(step == 4 ? 1 : 0), log.setValue(RotatedPillarBlock.AXIS, side.getAxis()), Block.UPDATE_CLIENTS);
            int[][] arms = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}};
            for (int i = 0; i < arms.length; i++) {
                int rise = trunk - 6 + i % 3 * 2, len = i < 4 ? 6 : 4;
                BlockPos end = base.above(rise);
                for (int step = 1; step <= len; step++) {
                    end = base.offset(arms[i][0] * step, rise + step / 2, arms[i][1] * step);
                    l.setBlock(end, log.setValue(RotatedPillarBlock.AXIS, arms[i][0] != 0 && arms[i][1] == 0 ? Direction.Axis.X : Direction.Axis.Z), Block.UPDATE_CLIENTS);
                }
                crown.addAll(blob(end.above(1), 4 + i % 2, 3, leaves, glow));
            }
            crown.addAll(blob(base.above(trunk + 2), 7, 4, leaves, glow));
            crown.addAll(blob(base.above(trunk - 1), 9, 3, leaves, glow));
            return crown;
        }

        List<BlockPos> blob(BlockPos c, int r, int tall, BlockState[] leaves, BlockState glow) {
            List<BlockPos> out = new ArrayList<>();
            for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) for (int dy = -tall; dy <= tall; dy++) {
                double shape = (dx * dx + dz * dz) / (double) (r * r) + (dy * dy) / (double) (tall * tall);
                if (shape > 1.0 || rnd.nextFloat() < 0.06 * shape) continue;
                BlockPos at = c.offset(dx, dy, dz);
                if (!l.getBlockState(at).canBeReplaced()) continue;
                BlockState s = glow != null && shape < 0.5 && rnd.nextInt(40) == 0 ? glow : leaves[rnd.nextInt(leaves.length)];
                l.setBlock(at, s, Block.UPDATE_CLIENTS);
                out.add(at);
            }
            return out;
        }

        static BlockState leaf(Block b) {
            BlockState s = b.defaultBlockState();
            return s.hasProperty(LeavesBlock.PERSISTENT) ? s.setValue(LeavesBlock.PERSISTENT, true) : s;
        }

        void treeOfLife(int x, int z) {
            disc(x, z, 7, Blocks.MOSS_BLOCK.defaultBlockState());
            disc(x, z, 4, Blocks.GRASS_BLOCK.defaultBlockState());
            List<BlockPos> crown = grandTree(x, z, Blocks.BIRCH_LOG.defaultBlockState(),
                    new BlockState[]{leaf(Blocks.FLOWERING_AZALEA_LEAVES), leaf(Blocks.CHERRY_LEAVES), leaf(Blocks.AZALEA_LEAVES)}, Blocks.SHROOMLIGHT.defaultBlockState(), 15);
            hangFruit(crown, new ItemStack(ModItems.FRUIT_OF_LIFE.get()), 8);
            for (int i = 0; i < 40; i++) {
                double a = rnd.nextDouble() * Math.PI * 2, d = 3 + rnd.nextDouble() * 4;
                int fx = x + (int) Math.round(Math.cos(a) * d), fz = z + (int) Math.round(Math.sin(a) * d);
                if (get(fx, S + 1, fz).isAir()) set(fx, S + 1, fz, (i % 3 == 0 ? Blocks.LILY_OF_THE_VALLEY : i % 3 == 1 ? Blocks.WHITE_TULIP : Blocks.OXEYE_DAISY).defaultBlockState());
            }
            sign(x - 5, z, Direction.WEST, "THE TREE", "OF LIFE", "", "Eat, and live.");
        }

        void treeOfKnowledge(int x, int z) {
            disc(x, z, 7, Blocks.PODZOL.defaultBlockState());
            List<BlockPos> crown = grandTree(x, z, Blocks.DARK_OAK_LOG.defaultBlockState(),
                    new BlockState[]{leaf(Blocks.DARK_OAK_LEAVES), leaf(Blocks.MANGROVE_LEAVES), leaf(Blocks.DARK_OAK_LEAVES)}, null, 14);
            hangFruit(crown, new ItemStack(ModItems.FRUIT_OF_KNOWLEDGE.get()), 7);
            for (int i = 0; i < 30; i++) {
                double a = rnd.nextDouble() * Math.PI * 2, d = 3 + rnd.nextDouble() * 4;
                int fx = x + (int) Math.round(Math.cos(a) * d), fz = z + (int) Math.round(Math.sin(a) * d);
                if (get(fx, S + 1, fz).isAir()) set(fx, S + 1, fz, (i % 2 == 0 ? Blocks.RED_TULIP : Blocks.FERN).defaultBlockState());
            }
            sign(x + 5, z, Direction.EAST, "Of the tree of", "knowledge, thou", "shalt not eat.", "- Genesis 2:17");
        }

        void sign(int x, int z, Direction facing, String... lines) {
            BlockPos p = new BlockPos(x, S + 1, z);
            int rot = switch (facing) { case SOUTH -> 0; case WEST -> 4; case NORTH -> 8; default -> 12; };
            l.setBlock(p, Blocks.OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, rot), Block.UPDATE_CLIENTS);
            if (l.getBlockEntity(p) instanceof SignBlockEntity sb) {
                SignText t = new SignText();
                for (int i = 0; i < lines.length && i < 4; i++) t = t.setMessage(i, Component.literal(lines[i]));
                sb.setText(t.setColor(DyeColor.BLACK), true);
                sb.setText(t.setColor(DyeColor.BLACK), false);
                sb.setChanged();
            }
        }

        /** Fruit hangs under the leaves: invisible, fixed glow frames that can be picked and regrow. */
        void hangFruit(List<BlockPos> crown, ItemStack fruit, int count) {
            List<BlockPos> under = new ArrayList<>();
            for (BlockPos p : crown) {
                BlockPos below = p.below();
                if (l.getBlockState(below).isAir() && l.getBlockState(p).getBlock() instanceof LeavesBlock) under.add(below);
            }
            java.util.Collections.shuffle(under, rnd);
            under.sort(java.util.Comparator.comparingInt(BlockPos::getY));
            int placed = 0;
            List<BlockPos> used = new ArrayList<>();
            for (BlockPos p : under) {
                if (placed >= count) break;
                if (p.getY() > S + 22) continue;
                boolean close = false;
                for (BlockPos q : used) if (q.distSqr(p) < 5) close = true;
                if (close) continue;
                if (frame(p, fruit)) { used.add(p); placed++; }
            }
        }

        boolean frame(BlockPos p, ItemStack fruit) {
            try {
                GlowItemFrame f = new GlowItemFrame(l, p, Direction.DOWN);
                CompoundTag t = f.saveWithoutId(new CompoundTag());
                t.putBoolean("Fixed", true);
                t.putBoolean("Invisible", true);
                f.load(t);
                f.setItem(fruit.copy(), false);
                f.addTag(FRUIT_TAG);
                f.getPersistentData().putString("bp_fruit_item", BuiltInRegistries.ITEM.getKey(fruit.getItem()).toString());
                return l.addFreshEntity(f);
            } catch (Throwable t) {
                return false;
            }
        }

        // ---------------------------------------------------------------- the orchard

        record Species(Block log, Block[] leaves, Item fruit, int trunk, boolean palm) {}

        void orchard() {
            Species[] kinds = {
                    new Species(Blocks.OAK_LOG, new Block[]{Blocks.OAK_LEAVES}, Items.APPLE, 5, false),
                    new Species(Blocks.BIRCH_LOG, new Block[]{Blocks.BIRCH_LEAVES}, ModItems.PEAR.get(), 6, false),
                    new Species(Blocks.CHERRY_LOG, new Block[]{Blocks.CHERRY_LEAVES}, ModItems.PEACH.get(), 5, false),
                    new Species(Blocks.DARK_OAK_LOG, new Block[]{Blocks.DARK_OAK_LEAVES, Blocks.OAK_LEAVES}, ModItems.PLUM.get(), 5, false),
                    new Species(Blocks.JUNGLE_LOG, new Block[]{Blocks.JUNGLE_LEAVES}, ModItems.FIG.get(), 5, false),
                    new Species(Blocks.ACACIA_LOG, new Block[]{Blocks.ACACIA_LEAVES}, ModItems.POMEGRANATE.get(), 4, false),
                    new Species(Blocks.OAK_LOG, new Block[]{Blocks.AZALEA_LEAVES}, ModItems.OLIVE.get(), 4, false),
                    new Species(Blocks.JUNGLE_LOG, new Block[]{Blocks.JUNGLE_LEAVES}, ModItems.DATE.get(), 8, true)};
            List<int[]> spots = new ArrayList<>();
            int tries = 0;
            while (spots.size() < 30 && tries++ < 4000) {
                int x = CX - 70 + rnd.nextInt(141), z = CZ - 70 + rnd.nextInt(141);
                if (!onIsland(x, z) || edgeDist(x, z) < 8 || Math.hypot(x, z) < 20) continue;
                if (Math.abs(x) < 6 && z > 4 && z < 122) continue;
                if (Math.hypot(x, z - PLAZA_Z) < 13 || Math.hypot(x - LIFE_X, z - TREES_Z) < 15 || Math.hypot(x - KNOW_X, z - TREES_Z) < 15) continue;
                if (Math.abs(z - riverZ(x)) < 7 || Math.hypot(x + 22, z - 20) < 9) continue;
                boolean close = false;
                for (int[] s : spots) if (Math.hypot(s[0] - x, s[1] - z) < 10) close = true;
                if (!close) spots.add(new int[]{x, z});
            }
            for (int i = 0; i < spots.size(); i++) {
                Species sp = kinds[i % kinds.length];
                int[] s = spots.get(i);
                fruitTree(s[0], s[1], sp);
            }
        }

        void fruitTree(int x, int z, Species sp) {
            int top = topAt(x, z);
            BlockState log = sp.log().defaultBlockState();
            int h = sp.trunk() + rnd.nextInt(2);
            for (int y = 1; y <= h; y++) set(x, top + y, z, log);
            BlockState[] lv = new BlockState[sp.leaves().length];
            for (int i = 0; i < lv.length; i++) lv[i] = leaf(sp.leaves()[i]);
            List<BlockPos> crown = new ArrayList<>();
            BlockPos crownAt = new BlockPos(x, top + h, z);
            if (sp.palm()) {
                // fronds from the top, drooping
                for (Direction d : Direction.Plane.HORIZONTAL) for (int k = 1; k <= 3; k++) {
                    BlockPos p = crownAt.relative(d, k).below(k == 3 ? 1 : 0);
                    l.setBlock(p, lv[0], Block.UPDATE_CLIENTS);
                    crown.add(p);
                    BlockPos q = p.relative(d.getClockWise());
                    if (k == 2) { l.setBlock(q, lv[0], Block.UPDATE_CLIENTS); crown.add(q); }
                }
                l.setBlock(crownAt.above(), lv[0], Block.UPDATE_CLIENTS);
            } else {
                crown.addAll(blob(crownAt, 3, 2, lv, null));
                crown.addAll(blob(crownAt.above(2), 2, 1, lv, null));
            }
            if (sp.log() == Blocks.JUNGLE_LOG && !sp.palm()) {
                // cocoa on the trunk too
                for (Direction d : Direction.Plane.HORIZONTAL) if (rnd.nextBoolean())
                    set(x + d.getStepX(), top + 2, z + d.getStepZ(), Blocks.COCOA.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, d.getOpposite()).setValue(BlockStateProperties.AGE_2, 2));
            }
            hangFruit(crown, new ItemStack(sp.fruit()), sp.palm() ? 3 : 4);
        }

        /** Grape vines on wooden arbours along the path south of the river. */
        void arbors() {
            int rz = (int) Math.round(riverZ(0));
            for (int a = 0; a < 3; a++) {
                int z0 = rz + 8 + a * 6;
                if (!onIsland(4, z0 + 3)) continue;
                for (int side : new int[]{-1, 1}) {
                    int x0 = side < 0 ? -6 : 3;
                    int[][] posts = {{x0, z0}, {x0 + 3, z0}, {x0, z0 + 3}, {x0 + 3, z0 + 3}};
                    for (int[] p : posts) { set(p[0], S + 1, p[1], Blocks.OAK_FENCE.defaultBlockState()); set(p[0], S + 2, p[1], Blocks.OAK_FENCE.defaultBlockState()); }
                    List<BlockPos> roof = new ArrayList<>();
                    for (int x = x0; x <= x0 + 3; x++) for (int z = z0; z <= z0 + 3; z++) {
                        BlockPos p = new BlockPos(x, S + 3, z);
                        l.setBlock(p, leaf(Blocks.OAK_LEAVES), Block.UPDATE_CLIENTS);
                        roof.add(p);
                    }
                    hangFruit(roof, new ItemStack(ModItems.GRAPES.get()), 3);
                }
            }
        }

        /** A quiet glade near the arch with beds to sleep in. */
        void restGlade(int x, int z) {
            disc(x, z, 5, Blocks.MOSS_BLOCK.defaultBlockState());
            for (int dx = -5; dx <= 5; dx++) for (int dz = -5; dz <= 5; dz++) for (int y = S + 1; y <= S + 4; y++)
                if (Math.hypot(dx, dz) <= 5.3) set(x + dx, y, z + dz, Blocks.AIR.defaultBlockState());
            Placer.place(l, new BlockPos(x - 2, S + 1, z - 2), Blocks.WHITE_BED, Direction.NORTH);
            Placer.place(l, new BlockPos(x + 2, S + 1, z - 2), Blocks.WHITE_BED, Direction.NORTH);
            Placer.place(l, new BlockPos(x - 2, S + 1, z + 2), Blocks.WHITE_BED, Direction.SOUTH);
            Placer.place(l, new BlockPos(x + 2, S + 1, z + 2), Blocks.WHITE_BED, Direction.SOUTH);
            set(x, S + 1, z, Blocks.OAK_FENCE.defaultBlockState());
            set(x, S + 2, z, Blocks.LANTERN.defaultBlockState());
            for (int[] h : new int[][]{{x - 4, z}, {x + 4, z}}) set(h[0], S + 1, h[1], Blocks.HAY_BLOCK.defaultBlockState());
        }

        void flowers() {
            Block[] small = {Blocks.POPPY, Blocks.DANDELION, Blocks.CORNFLOWER, Blocks.ALLIUM, Blocks.AZURE_BLUET, Blocks.OXEYE_DAISY,
                    Blocks.LILY_OF_THE_VALLEY, Blocks.PINK_TULIP, Blocks.WHITE_TULIP, Blocks.ORANGE_TULIP, Blocks.BLUE_ORCHID};
            Block[] tall = {Blocks.PEONY, Blocks.ROSE_BUSH, Blocks.LILAC, Blocks.SUNFLOWER};
            for (int x = CX - 80; x <= CX + 80; x++) for (int z = CZ - 80; z <= CZ + 80; z++) {
                if (!onIsland(x, z)) continue;
                int top = topAt(x, z);
                if (!get(x, top, z).is(Blocks.GRASS_BLOCK) || !get(x, top + 1, z).isAir()) continue;
                int r = rnd.nextInt(1000);
                if (r < 110) set(x, top + 1, z, small[rnd.nextInt(small.length)].defaultBlockState());
                else if (r < 330) set(x, top + 1, z, (rnd.nextInt(5) == 0 ? Blocks.FERN : Blocks.SHORT_GRASS).defaultBlockState());
                else if (r < 345 && get(x, top + 2, z).isAir()) {
                    Block t = tall[rnd.nextInt(tall.length)];
                    set(x, top + 1, z, t.defaultBlockState().setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
                    set(x, top + 2, z, t.defaultBlockState().setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
                } else if (r < 375) set(x, top + 1, z, Blocks.PINK_PETALS.defaultBlockState().setValue(BlockStateProperties.FLOWER_AMOUNT, 1 + rnd.nextInt(4))
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.from2DDataValue(rnd.nextInt(4))));
                else if (r < 380) set(x, top + 1, z, Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(BlockStateProperties.AGE_3, 3));
                else if (r < 382) set(x, top + 1, z, Blocks.MELON.defaultBlockState());
            }
        }

        // ---------------------------------------------------------------- the animals

        void animals() {
            Object[][] herd = {{EntityType.SHEEP, 12}, {EntityType.GOAT, 4}, {EntityType.COW, 5}, {EntityType.PIG, 4}, {EntityType.CHICKEN, 7},
                    {EntityType.RABBIT, 6}, {EntityType.HORSE, 3}, {EntityType.FOX, 2}, {EntityType.CAT, 2}, {EntityType.LLAMA, 2},
                    {EntityType.ARMADILLO, 2}, {EntityType.PARROT, 4}, {EntityType.BEE, 6}, {EntityType.PANDA, 1}, {EntityType.DONKEY, 2}};
            for (Object[] h : herd) {
                EntityType<?> type = (EntityType<?>) h[0];
                for (int i = 0; i < (Integer) h[1]; i++) {
                    BlockPos p = randomGround();
                    if (p == null) continue;
                    Entity e = type.create(l);
                    if (e == null) continue;
                    e.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, rnd.nextFloat() * 360F, 0);
                    if (e instanceof Mob m) m.setPersistenceRequired();
                    if (e instanceof Sheep s) s.setColor(DyeColor.byId(rnd.nextInt(3) == 0 ? rnd.nextInt(16) : 0));
                    l.addFreshEntity(e);
                }
            }
            // life in the river
            Object[][] river = {{EntityType.SALMON, 6}, {EntityType.TROPICAL_FISH, 6}, {EntityType.FROG, 4}};
            for (Object[] h : river) {
                EntityType<?> type = (EntityType<?>) h[0];
                for (int i = 0; i < (Integer) h[1]; i++) {
                    int x = CX - 60 + rnd.nextInt(121);
                    int z = (int) Math.round(riverZ(x)) + (type == EntityType.FROG ? (rnd.nextBoolean() ? 3 : -3) : 0);
                    if (!onIsland(x, z)) continue;
                    Entity e = type.create(l);
                    if (e == null) continue;
                    e.moveTo(x + 0.5, type == EntityType.FROG ? S + 1 : S - 0.5, z + 0.5, rnd.nextFloat() * 360F, 0);
                    if (e instanceof Mob m) m.setPersistenceRequired();
                    l.addFreshEntity(e);
                }
            }
        }

        BlockPos randomGround() {
            for (int t = 0; t < 60; t++) {
                int x = CX - 65 + rnd.nextInt(131), z = CZ - 65 + rnd.nextInt(131);
                if (!onIsland(x, z) || edgeDist(x, z) < 5 || Math.abs(z - riverZ(x)) < 5 || archZone(x, z)) continue;
                int top = topAt(x, z);
                if (get(x, top + 1, z).isSolid()) continue;
                return new BlockPos(x, top + 1, z);
            }
            return null;
        }
    }

    // ================================================================== the fruit

    public static void ateLife(ServerPlayer p) {
        p.addEffect(new MobEffectInstance(ModEffects.IMMORTAL, 24000 * 3, 0, false, true, true));
        p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 30, 1));
        p.serverLevel().sendParticles(ParticleTypes.TOTEM_OF_UNDYING, p.getX(), p.getY() + 1, p.getZ(), 60, 0.5, 1, 0.5, 0.3);
        p.playNotifySound(SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.6F, 1.4F);
        title(p, "THE TREE OF LIFE", "Death will pass you by for three days", ChatFormatting.GOLD);
    }

    public static void ateKnowledge(ServerPlayer p) {
        Rank r = Career.rank(p);
        if (r.atLeast(Rank.OVERSEER) || Career.isAscended(p)) {
            p.giveExperienceLevels(30);
            p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 20 * 600, 0));
            for (Entity e : p.level().getEntities(p, p.getBoundingBox().inflate(160), e -> e instanceof LivingEntity))
                ((LivingEntity) e).addEffect(new MobEffectInstance(MobEffects.GLOWING, 20 * 60, 0));
            for (int i = 0; i < 3; i++) Career.commend(p);
            p.playNotifySound(SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1F, 0.6F);
            title(p, "YOUR EYES ARE OPENED", "You know good and evil", ChatFormatting.DARK_RED);
            Career.message(p, O5.O5_1, "So you've eaten it. Now you see what we see, and you can never stop seeing it. Use it well.");
            return;
        }
        // below the Council: cast out
        GatePass.banish(p, System.currentTimeMillis() + REAL_DAY_MS);
        title(p, "CAST OUT", "The Guardian will not let you return for a day", ChatFormatting.RED);
        p.playNotifySound(SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.5F, 1.6F);
        p.sendSystemMessage(Component.literal("You took what was not yours. The Gate is closed to you for one full day.").withStyle(ChatFormatting.RED));
        p.server.execute(() -> leave(p));
    }

    private static void title(ServerPlayer p, String t, String sub, ChatFormatting colour) {
        p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
        p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(t).withStyle(colour, ChatFormatting.BOLD)));
        p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(sub).withStyle(ChatFormatting.GRAY)));
    }

    /** Out through SCP Unleashed's own exit (back in front of the Gate). */
    public static void leave(ServerPlayer p) {
        if (!inside(p.level())) return;
        try {
            Method m = UnleashedApi.cls("scp001gate.Scp001GateGarden").getMethod("leave", ServerPlayer.class);
            m.invoke(null, p);
        } catch (Throwable t) {
            ServerLevel ow = p.server.overworld();
            BlockPos s = ow.getSharedSpawnPos();
            p.teleportTo(ow, s.getX() + 0.5, s.getY(), s.getZ() + 0.5, p.getYRot(), 0);
        }
    }

    // ================================================================== peace in the Garden

    @SubscribeEvent
    public static void noHarm(LivingIncomingDamageEvent event) {
        if (!inside(event.getEntity().level()) || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void noHunting(LivingChangeTargetEvent event) {
        if (inside(event.getEntity().level()) && event.getNewAboutToBeSetTarget() != null) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void sleep(CanPlayerSleepEvent event) {
        if (inside(event.getEntity().level())) event.setProblem(null);
    }

    /** Immortal: the Fruit of Life turns death away while it lasts. */
    @SubscribeEvent
    public static void immortal(LivingDeathEvent event) {
        LivingEntity e = event.getEntity();
        if (e.level().isClientSide || !e.hasEffect(ModEffects.IMMORTAL)) return;
        event.setCanceled(true);
        e.setHealth(e.getMaxHealth());
        e.clearFire();
        if (e.getY() < e.level().getMinBuildHeight()) e.teleportTo(e.getX(), e.level().getMinBuildHeight() + 100, e.getZ());
        if (e.level() instanceof ServerLevel sl) sl.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, e.getX(), e.getY() + 1, e.getZ(), 40, 0.4, 0.8, 0.4, 0.3);
        e.level().playSound(null, e.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.5F, 1.5F);
    }

    @SubscribeEvent
    public static void pick(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof ItemFrame f) || !f.getTags().contains(FRUIT_TAG)) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (event.getLevel().isClientSide) return;
        ItemStack fruit = f.getItem();
        if (fruit.isEmpty()) {
            event.getEntity().displayClientMessage(Component.literal("Not ripe yet.").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        ItemStack give = fruit.copyWithCount(1);
        if (!event.getEntity().getInventory().add(give)) event.getEntity().drop(give, false);
        f.setItem(ItemStack.EMPTY, false);
        boolean special = fruit.is(ModItems.FRUIT_OF_LIFE.get()) || fruit.is(ModItems.FRUIT_OF_KNOWLEDGE.get());
        f.getPersistentData().putLong("bp_regrow", event.getLevel().getGameTime() + (special ? 24000 : 20 * 300));
        event.getLevel().playSound(null, f.blockPosition(), SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.BLOCKS, 0.8F, 1.1F);
    }

    @SubscribeEvent
    public static void noBreakingFruit(AttackEntityEvent event) {
        if (event.getTarget() instanceof ItemFrame f && f.getTags().contains(FRUIT_TAG)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        MinecraftServer s = event.getServer();
        if (s.getTickCount() % 10 != 0) return;
        ServerLevel g = s.getLevel(KEY);
        if (g == null) return;
        int ground = data(g).ground;
        for (ServerPlayer p : g.players()) {
            // the Garden doesn't let you fall away
            if (p.getY() < ground - 50) {
                p.teleportTo(g, 0.5, ground + 1, 7.5, 180F, 0);
                p.fallDistance = 0;
                p.displayClientMessage(Component.literal("The Garden catches you.").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC), true);
            }
            if (p.isSleeping()) p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, 1, true, false));
            if (GatePass.banished(p)) leave(p);
        }
        if (s.getTickCount() % 100 != 0) return;
        AABB box = new AABB(-100, 0, -60, 100, 256, 160);
        long now = g.getGameTime();
        for (Entity e : g.getEntities((Entity) null, box, e -> true)) {
            if (e instanceof ItemFrame f && f.getTags().contains(FRUIT_TAG)) {
                if (f.getItem().isEmpty() && now >= f.getPersistentData().getLong("bp_regrow")) {
                    Item it = BuiltInRegistries.ITEM.get(ResourceLocation.parse(f.getPersistentData().getString("bp_fruit_item")));
                    if (it != Items.AIR) f.setItem(new ItemStack(it), false);
                }
            } else if (e instanceof Mob m && m.getY() < ground - 50) {
                m.teleportTo(0.5 + m.getRandom().nextInt(20) - 10, ground + 1, 30.5);
                m.fallDistance = 0;
            }
        }
    }
}
