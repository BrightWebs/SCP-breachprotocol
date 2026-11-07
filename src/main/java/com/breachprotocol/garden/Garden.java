package com.breachprotocol.garden;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.career.Career;
import com.breachprotocol.career.GatePass;
import com.breachprotocol.career.O5;
import com.breachprotocol.career.Rank;
import com.breachprotocol.compat.UnleashedApi;
import com.breachprotocol.registry.ModBlocks;
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
import net.minecraft.world.level.levelgen.Heightmap;
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
 * about 1,000 blocks across. Their own rolling land runs out past your render distance, and at the
 * edge it simply ends in a sheer cliff over nothing.
 *
 * <ul>
 * <li>Arrive at SCP Unleashed's arch. A path leads south to a plaza between the
 *     two trees: the Tree of Life on your left (east), the Tree of Knowledge of Good and Evil on your
 *     right (west). One river crosses the whole Garden, edge to edge, and falls off at both ends.</li>
 * <li>An orchard of every fruit: apples, pears, peaches, plums, figs, pomegranates, olives, dates and
 *     grapes, hanging ripe under the leaves (more fruit trees all across the land). Right-click a fruit to
 *     pick it and eat it; a bud is left that ripens again.</li>
 * <li>Fruit of Life: Immortal for three Minecraft days. Come back for more.</li>
 * <li>Fruit of Knowledge: O5 and above gain great knowledge. Anyone below O5 is cast out at once and
 *     the Gate Guardian keeps them away for a full real-life day.</li>
 * <li>Everything is at peace: nothing can be hurt here, nothing hunts. Animals graze. Beds work.
 *     Fall off the edge and the Garden puts you back by the arch.</li>
 * </ul>
 */
@EventBusSubscriber(modid = BreachProtocol.MODID)
public final class Garden {
    private Garden() {}

    public static final ResourceKey<Level> KEY = ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("scp_unleashed", "scp001_garden"));
    private static final int VERSION = 2;
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

    // ================================================================== the shape of the Garden

    /** The edge of the Garden: about 1,000 blocks across, a little irregular. Beyond it, nothing. */
    public static double edgeRadius(double ang) { return 500 + 18 * Math.sin(3 * ang) + 10 * Math.sin(7 * ang + 1); }

    public static boolean beyondEdge(int x, int z) { return Math.hypot(x, z) > edgeRadius(Math.atan2(z, x)); }

    /** The one river: it crosses the whole Garden from the western edge to the eastern edge. */
    public static double riverZ(int x) { return 92 + 7 * Math.sin(x / 15.0) + 22 * Math.sin(x / 97.0); }

    private static int waterLevel = 71;

    /** Writes blocks either through the level (the centre build) or straight into a chunk as it loads. */
    private interface Writer {
        BlockState get(int x, int y, int z);
        void set(int x, int y, int z, BlockState s);
    }

    private static Writer levelWriter(ServerLevel l) {
        return new Writer() {
            public BlockState get(int x, int y, int z) { return l.getBlockState(new BlockPos(x, y, z)); }
            public void set(int x, int y, int z, BlockState s) { l.setBlock(new BlockPos(x, y, z), s, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE); }
        };
    }

    private static Writer chunkWriter(net.minecraft.world.level.chunk.LevelChunk c) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        return new Writer() {
            public BlockState get(int x, int y, int z) { return c.getBlockState(m.set(x, y, z)); }
            public void set(int x, int y, int z, BlockState s) { c.setBlockState(m.set(x, y, z), s, false); }
        };
    }

    /** Top solid ground (ignoring plants, leaves and logs). */
    private static int ground(Writer w, int x, int z, int from) {
        for (int y = from; y > 1; y--) {
            BlockState s = w.get(x, y, z);
            if (s.isAir() || s.canBeReplaced() || s.is(net.minecraft.tags.BlockTags.LEAVES) || s.is(net.minecraft.tags.BlockTags.LOGS)
                    || s.getBlock() instanceof com.breachprotocol.block.HangingFruitBlock || s.is(Blocks.WATER)) continue;
            return y;
        }
        return 1;
    }

    /** Carves one column of the river valley: water in the channel, sand banks, gentle slopes. */
    private static void riverColumn(Writer w, int x, int z, Random rnd) {
        double dz = Math.abs(z - riverZ(x));
        if (dz > 9) return;
        int W = waterLevel, top = ground(w, x, z, 200);
        BlockState air = Blocks.AIR.defaultBlockState();
        if (dz <= 2.6) {
            int deep = dz <= 1.5 ? 3 : 2;
            for (int y = W + 1; y <= Math.max(top, W) + 14; y++) if (!w.get(x, y, z).isAir()) w.set(x, y, z, air);
            for (int y = top + 1; y < W - deep; y++) w.set(x, y, z, Blocks.DIRT.defaultBlockState());
            for (int y = W; y > W - deep; y--) w.set(x, y, z, Blocks.WATER.defaultBlockState());
            w.set(x, W - deep, z, rnd.nextInt(4) == 0 ? Blocks.CLAY.defaultBlockState() : rnd.nextInt(3) == 0 ? Blocks.GRAVEL.defaultBlockState() : Blocks.SAND.defaultBlockState());
            if (dz <= 1.0 && rnd.nextInt(9) == 0) w.set(x, W - deep + 1, z, Blocks.SEAGRASS.defaultBlockState());
            if (dz <= 1.0 && rnd.nextInt(14) == 0) w.set(x, W + 1, z, Blocks.LILY_PAD.defaultBlockState());
            return;
        }
        boolean sand = dz <= 4;
        int bank = sand ? W : W + (int) ((dz - 4) * 1.1);
        if (top > bank) {
            for (int y = bank + 1; y <= top + 14; y++) if (!w.get(x, y, z).isAir()) w.set(x, y, z, air);
            top = bank;
        } else if (top < W) {
            for (int y = top + 1; y < W; y++) w.set(x, y, z, Blocks.DIRT.defaultBlockState());
            top = W;
        }
        w.set(x, top, z, sand ? (rnd.nextInt(5) == 0 ? Blocks.MUD.defaultBlockState() : Blocks.SAND.defaultBlockState()) : Blocks.GRASS_BLOCK.defaultBlockState());
        if (sand && rnd.nextInt(8) == 0) { w.set(x, top + 1, z, Blocks.SUGAR_CANE.defaultBlockState()); if (rnd.nextBoolean()) w.set(x, top + 2, z, Blocks.SUGAR_CANE.defaultBlockState()); }
    }

    private static BlockState leaf(Block b) {
        BlockState s = b.defaultBlockState();
        return s.hasProperty(LeavesBlock.PERSISTENT) ? s.setValue(LeavesBlock.PERSISTENT, true) : s;
    }

    private record Species(Block log, Block[] leaves, Block fruit, int trunk, boolean palm) {}

    private static Species[] species() {
        return new Species[]{
                new Species(Blocks.OAK_LOG, new Block[]{Blocks.OAK_LEAVES}, ModBlocks.FRUIT_APPLE.get(), 5, false),
                new Species(Blocks.BIRCH_LOG, new Block[]{Blocks.BIRCH_LEAVES}, ModBlocks.FRUIT_PEAR.get(), 6, false),
                new Species(Blocks.CHERRY_LOG, new Block[]{Blocks.CHERRY_LEAVES}, ModBlocks.FRUIT_PEACH.get(), 5, false),
                new Species(Blocks.DARK_OAK_LOG, new Block[]{Blocks.DARK_OAK_LEAVES, Blocks.OAK_LEAVES}, ModBlocks.FRUIT_PLUM.get(), 5, false),
                new Species(Blocks.JUNGLE_LOG, new Block[]{Blocks.JUNGLE_LEAVES}, ModBlocks.FRUIT_FIG.get(), 5, false),
                new Species(Blocks.ACACIA_LOG, new Block[]{Blocks.ACACIA_LEAVES}, ModBlocks.FRUIT_POMEGRANATE.get(), 4, false),
                new Species(Blocks.OAK_LOG, new Block[]{Blocks.AZALEA_LEAVES}, ModBlocks.FRUIT_OLIVE.get(), 4, false),
                new Species(Blocks.JUNGLE_LOG, new Block[]{Blocks.JUNGLE_LEAVES}, ModBlocks.FRUIT_DATE.get(), 8, true)};
    }

    /** A fruit tree whose fruit hangs, ripe, from the underside of its leaves. Fits within 4 blocks of its trunk. */
    private static void fruitTree(Writer w, int x, int top, int z, Species sp, Random rnd) {
        BlockState log = sp.log().defaultBlockState();
        int h = sp.trunk() + rnd.nextInt(2);
        for (int y = 1; y <= h; y++) w.set(x, top + y, z, log);
        BlockState[] lv = new BlockState[sp.leaves().length];
        for (int i = 0; i < lv.length; i++) lv[i] = leaf(sp.leaves()[i]);
        List<int[]> crown = new ArrayList<>();
        int cy = top + h;
        if (sp.palm()) {
            for (Direction d : Direction.Plane.HORIZONTAL) for (int k = 1; k <= 3; k++) {
                int px = x + d.getStepX() * k, pz = z + d.getStepZ() * k, py = cy - (k == 3 ? 1 : 0);
                w.set(px, py, pz, lv[0]);
                crown.add(new int[]{px, py, pz});
                if (k == 2) {
                    Direction c = d.getClockWise();
                    w.set(px + c.getStepX(), py, pz + c.getStepZ(), lv[0]);
                    crown.add(new int[]{px + c.getStepX(), py, pz + c.getStepZ()});
                }
            }
            w.set(x, cy + 1, z, lv[0]);
        } else {
            for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) for (int dy = -2; dy <= 3; dy++) {
                double shape = (dx * dx + dz * dz) / 9.0 + ((dy - 0.5) * (dy - 0.5)) / 7.0;
                if (shape > 1.0 || rnd.nextFloat() < 0.08 * shape) continue;
                if (dx == 0 && dz == 0 && dy <= 0) continue;
                if (!w.get(x + dx, cy + dy, z + dz).canBeReplaced()) continue;
                w.set(x + dx, cy + dy, z + dz, lv[rnd.nextInt(lv.length)]);
                crown.add(new int[]{x + dx, cy + dy, z + dz});
            }
        }
        if (sp.log() == Blocks.JUNGLE_LOG && !sp.palm())
            for (Direction d : Direction.Plane.HORIZONTAL) if (rnd.nextBoolean())
                w.set(x + d.getStepX(), top + 2, z + d.getStepZ(), Blocks.COCOA.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, d.getOpposite()).setValue(BlockStateProperties.AGE_2, 2));
        hangFruit(w, crown, sp.fruit().defaultBlockState(), sp.palm() ? 4 : 7, rnd);
    }

    /** Ripe fruit under the lowest leaves, spread out. */
    private static void hangFruit(Writer w, List<int[]> crown, BlockState fruit, int count, Random rnd) {
        List<int[]> under = new ArrayList<>();
        for (int[] p : crown)
            if (w.get(p[0], p[1] - 1, p[2]).isAir() && w.get(p[0], p[1], p[2]).getBlock() instanceof LeavesBlock) under.add(new int[]{p[0], p[1] - 1, p[2]});
        java.util.Collections.shuffle(under, rnd);
        List<int[]> used = new ArrayList<>();
        for (int[] p : under) {
            if (used.size() >= count) break;
            boolean close = false;
            for (int[] q : used) if (Math.abs(q[0] - p[0]) + Math.abs(q[2] - p[2]) + Math.abs(q[1] - p[1]) < 2) close = true;
            if (close) continue;
            w.set(p[0], p[1], p[2], fruit);
            used.add(p);
        }
    }

    // ================================================================== chunks as they load

    private static final java.util.concurrent.ConcurrentLinkedQueue<long[]> AFTER = new java.util.concurrent.ConcurrentLinkedQueue<>();

    /**
     * Every Garden chunk is shaped once, the first time it loads: beyond the edge it is emptied,
     * the river is carved through it, and now and then a fruit tree is planted. Animals follow on
     * the next tick.
     */
    @SubscribeEvent
    public static void chunkLoad(net.neoforged.neoforge.event.level.ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel l) || !inside(l) || !(event.getChunk() instanceof net.minecraft.world.level.chunk.LevelChunk c)) return;
        try {
            if (Boolean.TRUE.equals(c.getData(com.breachprotocol.registry.ModAttachments.GARDEN_DONE))) return;
            shapeChunk(l, c);
            c.setData(com.breachprotocol.registry.ModAttachments.GARDEN_DONE, true);
            c.setUnsaved(true);
        } catch (Throwable t) {
            BreachProtocol.LOGGER.debug("Garden chunk {}: {}", c.getPos(), t.toString());
        }
    }

    private static void shapeChunk(ServerLevel l, net.minecraft.world.level.chunk.LevelChunk c) {
        Writer w = chunkWriter(c);
        int x0 = c.getPos().getMinBlockX(), z0 = c.getPos().getMinBlockZ();
        Random rnd = new Random(c.getPos().toLong() * 31 + 7);
        BlockState air = Blocks.AIR.defaultBlockState();
        boolean anyInside = false;
        for (int lx = 0; lx < 16; lx++) for (int lz = 0; lz < 16; lz++) {
            int x = x0 + lx, z = z0 + lz;
            if (beyondEdge(x, z)) {
                int top = c.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, lx, lz);
                for (int y = l.getMinBuildHeight(); y <= top; y++) if (!w.get(x, y, z).isAir()) w.set(x, y, z, air);
                continue;
            }
            anyInside = true;
            if (Math.abs(z - riverZ(x)) <= 9) {
                riverColumn(w, x, z, rnd);
                if (Math.abs(z - riverZ(x)) <= 2.6 && edgeRadius(Math.atan2(z, x)) - Math.hypot(x, z) < 2.5) AFTER.add(new long[]{0, x, waterLevel, z});
            }
        }
        if (!anyInside) return;
        double d = Math.hypot(x0 + 8, z0 + 8);
        if (d < 150 || d > edgeRadius(Math.atan2(z0 + 8, x0 + 8)) - 12) return;
        // a fruit tree now and then, and a few animals
        if (rnd.nextFloat() < 0.3F) {
            int tx = x0 + 5 + rnd.nextInt(6), tz = z0 + 5 + rnd.nextInt(6);
            if (Math.abs(tz - riverZ(tx)) > 11) {
                int top = ground(w, tx, tz, 200);
                if (w.get(tx, top, tz).is(Blocks.GRASS_BLOCK) && w.get(tx, top + 1, tz).canBeReplaced()) {
                    Species[] sp = species();
                    fruitTree(w, tx, top, tz, sp[rnd.nextInt(sp.length)], rnd);
                }
            }
        }
        if (rnd.nextFloat() < 0.14F) AFTER.add(new long[]{1, c.getPos().x, rnd.nextInt(), c.getPos().z});
    }

    private static final EntityType<?>[] GRAZERS = {EntityType.SHEEP, EntityType.SHEEP, EntityType.GOAT, EntityType.COW, EntityType.PIG, EntityType.CHICKEN,
            EntityType.RABBIT, EntityType.HORSE, EntityType.FOX, EntityType.LLAMA, EntityType.DONKEY, EntityType.ARMADILLO};

    private static void afterTick(ServerLevel l) {
        for (int i = 0; i < 6 && !AFTER.isEmpty(); i++) {
            long[] job = AFTER.poll();
            if (job[0] == 0) {
                BlockPos p = new BlockPos((int) job[1], (int) job[2], (int) job[3]);
                if (l.isLoaded(p)) l.scheduleTick(p, Fluids.WATER, 2);
            } else {
                int cx = (int) job[1], cz = (int) job[3];
                if (!l.hasChunk(cx, cz)) continue;
                Random rnd = new Random(job[2]);
                EntityType<?> type = GRAZERS[rnd.nextInt(GRAZERS.length)];
                int n = 2 + rnd.nextInt(3);
                for (int k = 0; k < n; k++) {
                    int x = cx * 16 + rnd.nextInt(16), z = cz * 16 + rnd.nextInt(16);
                    int y = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                    if (y <= 2 || Math.abs(z - riverZ(x)) < 4) continue;
                    Entity e = type.create(l);
                    if (e == null) continue;
                    e.moveTo(x + 0.5, y, z + 0.5, rnd.nextFloat() * 360F, 0);
                    if (e instanceof Mob m) m.setPersistenceRequired();
                    if (e instanceof Sheep s) s.setColor(DyeColor.byId(rnd.nextInt(3) == 0 ? rnd.nextInt(16) : 0));
                    l.addFreshEntity(e);
                }
            }
        }
    }

    // ================================================================== the heart of the Garden

    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        ServerLevel g = event.getServer().getLevel(KEY);
        if (g == null) return;
        try {
            Data d = data(g);
            waterLevel = d.ground - 1;
            if (d.version >= VERSION) return;
            long t0 = System.currentTimeMillis();
            new Builder(g).build(d);
            waterLevel = d.ground - 1;
            d.version = VERSION;
            d.setDirty();
            BreachProtocol.LOGGER.info("Built the heart of the Garden in {} ms", System.currentTimeMillis() - t0);
        } catch (Throwable t) {
            BreachProtocol.LOGGER.error("Could not build the Garden", t);
        }
    }

    /**
     * The middle of the Garden, around SCP Unleashed's arch: a shaped meadow blending into the
     * surrounding land, a path south to the plaza between the two trees, an orchard of every fruit,
     * grape arbours past the bridge, a rest glade with beds, flowers, and animals.
     */
    private static final class Builder {
        final ServerLevel l;
        final Writer w;
        final Random rnd = new Random(0x6A7DE7L);
        int S;
        static final int RC = 135, INNER = 80;

        Builder(ServerLevel l) { this.l = l; this.w = levelWriter(l); }

        boolean archZone(int x, int z) { return Math.abs(x) <= 14 && z >= -7 && z <= 7; }

        int shape(int x, int z) {
            double flat = 1;
            flat = Math.min(flat, Math.max(0, (Math.hypot(x, z) - 16) / 10));
            flat = Math.min(flat, Math.max(0, (Math.abs(z - riverZ(x)) - 5) / 6));
            flat = Math.min(flat, Math.max(0, (Math.hypot(x, z - PLAZA_Z) - 10) / 8));
            flat = Math.min(flat, Math.max(0, (Math.hypot(x - LIFE_X, z - TREES_Z) - 9) / 6));
            flat = Math.min(flat, Math.max(0, (Math.hypot(x - KNOW_X, z - TREES_Z) - 9) / 6));
            if (Math.abs(x) <= 3 && z > 4 && z < 125) flat = Math.min(flat, 0.3);
            double n = 1.6 * Math.sin(x * 0.11) + 1.3 * Math.cos(z * 0.09) + 0.9 * Math.sin((x + z) * 0.07) + 0.6;
            return (int) Math.round(Math.max(-1, Math.min(3, n)) * flat);
        }

        void build(Data d) {
            Object theirs = null;
            Class<?> built = UnleashedApi.cls("scp001gate.Scp001GateGarden$Built");
            boolean ready = false;
            int groundY = 72;
            try {
                if (built != null) {
                    @SuppressWarnings("unchecked")
                    SavedData.Factory<SavedData> f = (SavedData.Factory<SavedData>) built.getField("DataFactory").get(null);
                    theirs = l.getDataStorage().computeIfAbsent(f, (String) built.getField("DataName").get(null));
                    ready = built.getField("Ready").getBoolean(theirs);
                    if (ready) groundY = built.getField("Ground").getInt(theirs);
                }
            } catch (Throwable t) {
                BreachProtocol.LOGGER.warn("Garden: couldn't read SCP Unleashed's garden record: {}", t.toString());
            }
            if (!ready) {
                l.getChunk(0, 0);
                int h = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0);
                groundY = h > 10 ? h : 72;
            }
            S = groundY - 1;
            d.ground = groundY;
            waterLevel = S;
            for (int cx = (CX - RC - 16) >> 4; cx <= (CX + RC + 16) >> 4; cx++)
                for (int cz = (CZ - RC - 16) >> 4; cz <= (CZ + RC + 16) >> 4; cz++)
                    l.getChunk(cx, cz);
            // the old hanging-frame fruit, if any, goes
            for (Entity e : l.getEntities((Entity) null, new AABB(CX - RC - 20, 0, CZ - RC - 20, CX + RC + 20, 256, CZ + RC + 20), e -> e.getTags().contains(FRUIT_TAG)))
                e.discard();
            meadow();
            if (!ready && theirs != null) {
                try {
                    Method arch = UnleashedApi.cls("scp001gate.Scp001GateGarden").getMethod("buildArch", ServerLevel.class, BlockPos.class);
                    arch.invoke(null, l, new BlockPos(0, groundY, 0));
                    built.getField("Ready").setBoolean(theirs, true);
                    built.getField("Ground").setInt(theirs, groundY);
                    ((SavedData) theirs).setDirty();
                } catch (Throwable t) {
                    BreachProtocol.LOGGER.warn("Garden: couldn't build SCP Unleashed's arch: {}", t.toString());
                }
            }
            for (int x = CX - RC - 16; x <= CX + RC + 16; x++)
                for (int z = (int) riverZ(x) - 10; z <= (int) riverZ(x) + 10; z++) riverColumn(w, x, z, rnd);
            paths();
            treeOfLife(LIFE_X, TREES_Z);
            treeOfKnowledge(KNOW_X, TREES_Z);
            orchard();
            arbors();
            restGlade(-22, 20);
            flowers();
            animals();
        }

        /** Natural ground height along a direction, just outside the reshaped area. */
        int natural(double ang) {
            int x = CX + (int) Math.round(Math.cos(ang) * (RC + 8)), z = CZ + (int) Math.round(Math.sin(ang) * (RC + 8));
            return ground(w, x, z, 200);
        }

        void meadow() {
            int[] nat = new int[72];
            for (int i = 0; i < nat.length; i++) nat[i] = natural(i * Math.PI * 2 / nat.length);
            BlockState air = Blocks.AIR.defaultBlockState();
            for (int x = CX - RC; x <= CX + RC; x++) for (int z = CZ - RC; z <= CZ + RC; z++) {
                double d = Math.hypot(x - CX, z - CZ);
                if (d > RC) continue;
                double ang = Math.atan2(z - CZ, x - CX);
                double fi = ((ang + Math.PI * 2) % (Math.PI * 2)) / (Math.PI * 2) * nat.length;
                int i0 = (int) fi % nat.length, i1 = (i0 + 1) % nat.length;
                double outer = nat[i0] + (nat[i1] - nat[i0]) * (fi - Math.floor(fi));
                double t = Math.max(0, Math.min(1, (d - INNER) / (RC - INNER)));
                t = t * t * (3 - 2 * t);
                int top = (int) Math.round((S + shape(x, z)) * (1 - t) + outer * t);
                boolean arch = archZone(x, z);
                for (int y = Math.max(2, S - 50); y <= S + 60; y++) {
                    if (arch && y >= S) continue;
                    BlockState s = y > top ? air : y == top ? Blocks.GRASS_BLOCK.defaultBlockState() : y >= top - 3 ? Blocks.DIRT.defaultBlockState()
                            : rnd.nextInt(9) == 0 ? Blocks.ANDESITE.defaultBlockState() : Blocks.STONE.defaultBlockState();
                    if (s.isAir() && w.get(x, y, z).isAir()) continue;
                    w.set(x, y, z, s);
                }
            }
        }

        int topAt(int x, int z) { return ground(w, x, z, S + 30); }

        void paths() {
            BlockState path = Blocks.DIRT_PATH.defaultBlockState();
            for (int z = 7; z <= 125; z++) for (int x = -1; x <= 1; x++) {
                if (Math.abs(z - riverZ(x)) <= 4.5) continue;
                int top = topAt(x, z);
                if (w.get(x, top, z).is(Blocks.GRASS_BLOCK)) w.set(x, top, z, path);
                w.set(x, top + 1, z, Blocks.AIR.defaultBlockState());
            }
            for (int x = -9; x <= 9; x++) for (int z = PLAZA_Z - 9; z <= PLAZA_Z + 9; z++) {
                double d = Math.hypot(x, z - PLAZA_Z);
                if (d > 8.5) continue;
                w.set(x, S, z, d > 7 ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState() : d > 6 ? Blocks.MOSS_BLOCK.defaultBlockState() : path);
                w.set(x, S + 1, z, Blocks.AIR.defaultBlockState());
            }
            for (int x = -24; x <= 24; x++) for (int z = TREES_Z - 1; z <= TREES_Z + 1; z++) {
                if (Math.abs(x) < 8) continue;
                int top = topAt(x, z);
                if (w.get(x, top, z).is(Blocks.GRASS_BLOCK)) w.set(x, top, z, path);
            }
            int rz = (int) Math.round(riverZ(0));
            for (int z = rz - 4; z <= rz + 4; z++) for (int x = -2; x <= 2; x++) {
                if (Math.abs(x) == 2) w.set(x, S + 1, z, Blocks.SPRUCE_FENCE.defaultBlockState()
                        .setValue(BlockStateProperties.NORTH, z > rz - 4).setValue(BlockStateProperties.SOUTH, z < rz + 4));
                else w.set(x, S + 1, z, Blocks.SPRUCE_SLAB.defaultBlockState());
            }
            for (int x : new int[]{-2, 2}) for (int z : new int[]{rz - 4, rz + 4}) w.set(x, S + 2, z, Blocks.LANTERN.defaultBlockState());
        }

        void disc(int cx, int cz, int r, BlockState s) {
            for (int x = cx - r; x <= cx + r; x++) for (int z = cz - r; z <= cz + r; z++)
                if (Math.hypot(x - cx, z - cz) <= r + 0.3) w.set(x, S, z, s);
        }

        List<int[]> grandTree(int bx, int bz, BlockState log, BlockState[] leaves, BlockState glow, int trunk) {
            List<int[]> crown = new ArrayList<>();
            int by = S + 1;
            for (int y = -3; y < trunk; y++)
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                    if (y > trunk - 4 && Math.abs(dx) + Math.abs(dz) == 2) continue;
                    w.set(bx + dx, by + y, bz + dz, log);
                }
            for (Direction side : Direction.Plane.HORIZONTAL)
                for (int step = 2; step <= 4; step++)
                    w.set(bx + side.getStepX() * step, by - (step == 4 ? 1 : 0), bz + side.getStepZ() * step, log.setValue(RotatedPillarBlock.AXIS, side.getAxis()));
            int[][] arms = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}};
            for (int i = 0; i < arms.length; i++) {
                int rise = trunk - 6 + i % 3 * 2, len = i < 4 ? 6 : 4;
                int ex = bx, ey = by + rise, ez = bz;
                for (int step = 1; step <= len; step++) {
                    ex = bx + arms[i][0] * step; ey = by + rise + step / 2; ez = bz + arms[i][1] * step;
                    w.set(ex, ey, ez, log.setValue(RotatedPillarBlock.AXIS, arms[i][0] != 0 && arms[i][1] == 0 ? Direction.Axis.X : Direction.Axis.Z));
                }
                crown.addAll(blob(ex, ey + 1, ez, 4 + i % 2, 3, leaves, glow));
            }
            crown.addAll(blob(bx, by + trunk + 2, bz, 7, 4, leaves, glow));
            crown.addAll(blob(bx, by + trunk - 1, bz, 9, 3, leaves, glow));
            return crown;
        }

        List<int[]> blob(int cx, int cy, int cz, int r, int tall, BlockState[] leaves, BlockState glow) {
            List<int[]> out = new ArrayList<>();
            for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) for (int dy = -tall; dy <= tall; dy++) {
                double sh = (dx * dx + dz * dz) / (double) (r * r) + (dy * dy) / (double) (tall * tall);
                if (sh > 1.0 || rnd.nextFloat() < 0.06 * sh) continue;
                int x = cx + dx, y = cy + dy, z = cz + dz;
                if (!w.get(x, y, z).canBeReplaced()) continue;
                w.set(x, y, z, glow != null && sh < 0.5 && rnd.nextInt(40) == 0 ? glow : leaves[rnd.nextInt(leaves.length)]);
                out.add(new int[]{x, y, z});
            }
            return out;
        }

        void treeOfLife(int x, int z) {
            disc(x, z, 7, Blocks.MOSS_BLOCK.defaultBlockState());
            disc(x, z, 4, Blocks.GRASS_BLOCK.defaultBlockState());
            List<int[]> crown = grandTree(x, z, Blocks.BIRCH_LOG.defaultBlockState(),
                    new BlockState[]{leaf(Blocks.FLOWERING_AZALEA_LEAVES), leaf(Blocks.CHERRY_LEAVES), leaf(Blocks.AZALEA_LEAVES)}, Blocks.SHROOMLIGHT.defaultBlockState(), 15);
            crown.removeIf(p -> p[1] > S + 21);
            hangFruit(w, crown, ModBlocks.FRUIT_LIFE.get().defaultBlockState(), 10, rnd);
            for (int i = 0; i < 40; i++) {
                double a = rnd.nextDouble() * Math.PI * 2, d = 3 + rnd.nextDouble() * 4;
                int fx = x + (int) Math.round(Math.cos(a) * d), fz = z + (int) Math.round(Math.sin(a) * d);
                if (w.get(fx, S + 1, fz).isAir()) w.set(fx, S + 1, fz, (i % 3 == 0 ? Blocks.LILY_OF_THE_VALLEY : i % 3 == 1 ? Blocks.WHITE_TULIP : Blocks.OXEYE_DAISY).defaultBlockState());
            }
            sign(x - 6, z, 4, "THE TREE", "OF LIFE", "", "Eat, and live.");
        }

        void treeOfKnowledge(int x, int z) {
            disc(x, z, 7, Blocks.PODZOL.defaultBlockState());
            List<int[]> crown = grandTree(x, z, Blocks.DARK_OAK_LOG.defaultBlockState(),
                    new BlockState[]{leaf(Blocks.DARK_OAK_LEAVES), leaf(Blocks.MANGROVE_LEAVES), leaf(Blocks.DARK_OAK_LEAVES)}, null, 14);
            crown.removeIf(p -> p[1] > S + 20);
            hangFruit(w, crown, ModBlocks.FRUIT_KNOWLEDGE.get().defaultBlockState(), 9, rnd);
            for (int i = 0; i < 30; i++) {
                double a = rnd.nextDouble() * Math.PI * 2, d = 3 + rnd.nextDouble() * 4;
                int fx = x + (int) Math.round(Math.cos(a) * d), fz = z + (int) Math.round(Math.sin(a) * d);
                if (w.get(fx, S + 1, fz).isAir()) w.set(fx, S + 1, fz, (i % 2 == 0 ? Blocks.RED_TULIP : Blocks.FERN).defaultBlockState());
            }
            sign(x + 6, z, 12, "Of the tree of", "knowledge, thou", "shalt not eat.", "- Genesis 2:17");
        }

        void sign(int x, int z, int rot, String... lines) {
            BlockPos p = new BlockPos(x, S + 1, z);
            l.setBlock(p, Blocks.OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, rot), Block.UPDATE_CLIENTS);
            if (l.getBlockEntity(p) instanceof SignBlockEntity sb) {
                SignText t = new SignText();
                for (int i = 0; i < lines.length && i < 4; i++) t = t.setMessage(i, Component.literal(lines[i]));
                sb.setText(t.setColor(DyeColor.BLACK), true);
                sb.setText(t.setColor(DyeColor.BLACK), false);
                sb.setChanged();
            }
        }

        void orchard() {
            Species[] kinds = species();
            List<int[]> spots = new ArrayList<>();
            int tries = 0;
            while (spots.size() < 44 && tries++ < 6000) {
                int x = CX - 120 + rnd.nextInt(241), z = CZ - 120 + rnd.nextInt(241);
                if (Math.hypot(x - CX, z - CZ) > 122 || Math.hypot(x, z) < 20) continue;
                if (Math.abs(x) < 6 && z > 4 && z < 128) continue;
                if (Math.hypot(x, z - PLAZA_Z) < 13 || Math.hypot(x - LIFE_X, z - TREES_Z) < 15 || Math.hypot(x - KNOW_X, z - TREES_Z) < 15) continue;
                if (Math.abs(z - riverZ(x)) < 11 || Math.hypot(x + 22, z - 20) < 9) continue;
                boolean close = false;
                for (int[] s : spots) if (Math.hypot(s[0] - x, s[1] - z) < 11) close = true;
                if (!close) spots.add(new int[]{x, z});
            }
            for (int i = 0; i < spots.size(); i++) {
                int[] s = spots.get(i);
                int top = topAt(s[0], s[1]);
                if (!w.get(s[0], top, s[1]).is(Blocks.GRASS_BLOCK)) continue;
                fruitTree(w, s[0], top, s[1], kinds[i % kinds.length], rnd);
            }
        }

        void arbors() {
            int rz = (int) Math.round(riverZ(0));
            for (int a = 0; a < 3; a++) {
                int z0 = rz + 12 + a * 6;
                for (int side : new int[]{-1, 1}) {
                    int x0 = side < 0 ? -6 : 3;
                    int top = topAt(x0 + 1, z0 + 1);
                    for (int[] p : new int[][]{{x0, z0}, {x0 + 3, z0}, {x0, z0 + 3}, {x0 + 3, z0 + 3}}) {
                        w.set(p[0], top + 1, p[1], Blocks.OAK_FENCE.defaultBlockState());
                        w.set(p[0], top + 2, p[1], Blocks.OAK_FENCE.defaultBlockState());
                    }
                    List<int[]> roof = new ArrayList<>();
                    for (int x = x0; x <= x0 + 3; x++) for (int z = z0; z <= z0 + 3; z++) {
                        w.set(x, top + 3, z, leaf(Blocks.OAK_LEAVES));
                        roof.add(new int[]{x, top + 3, z});
                    }
                    hangFruit(w, roof, ModBlocks.FRUIT_GRAPES.get().defaultBlockState(), 5, rnd);
                }
            }
        }

        void restGlade(int x, int z) {
            disc(x, z, 5, Blocks.MOSS_BLOCK.defaultBlockState());
            for (int dx = -5; dx <= 5; dx++) for (int dz = -5; dz <= 5; dz++) for (int y = S + 1; y <= S + 5; y++)
                if (Math.hypot(dx, dz) <= 5.3) w.set(x + dx, y, z + dz, Blocks.AIR.defaultBlockState());
            Placer.place(l, new BlockPos(x - 2, S + 1, z - 2), Blocks.WHITE_BED, Direction.NORTH);
            Placer.place(l, new BlockPos(x + 2, S + 1, z - 2), Blocks.WHITE_BED, Direction.NORTH);
            Placer.place(l, new BlockPos(x - 2, S + 1, z + 2), Blocks.WHITE_BED, Direction.SOUTH);
            Placer.place(l, new BlockPos(x + 2, S + 1, z + 2), Blocks.WHITE_BED, Direction.SOUTH);
            w.set(x, S + 1, z, Blocks.OAK_FENCE.defaultBlockState());
            w.set(x, S + 2, z, Blocks.LANTERN.defaultBlockState());
            for (int[] h : new int[][]{{x - 4, z}, {x + 4, z}}) w.set(h[0], S + 1, h[1], Blocks.HAY_BLOCK.defaultBlockState());
        }

        void flowers() {
            Block[] small = {Blocks.POPPY, Blocks.DANDELION, Blocks.CORNFLOWER, Blocks.ALLIUM, Blocks.AZURE_BLUET, Blocks.OXEYE_DAISY,
                    Blocks.LILY_OF_THE_VALLEY, Blocks.PINK_TULIP, Blocks.WHITE_TULIP, Blocks.ORANGE_TULIP, Blocks.BLUE_ORCHID};
            Block[] tall = {Blocks.PEONY, Blocks.ROSE_BUSH, Blocks.LILAC, Blocks.SUNFLOWER};
            for (int x = CX - RC; x <= CX + RC; x++) for (int z = CZ - RC; z <= CZ + RC; z++) {
                if (Math.hypot(x - CX, z - CZ) > RC) continue;
                int top = topAt(x, z);
                if (!w.get(x, top, z).is(Blocks.GRASS_BLOCK) || !w.get(x, top + 1, z).isAir()) continue;
                int r = rnd.nextInt(1000);
                if (r < 110) w.set(x, top + 1, z, small[rnd.nextInt(small.length)].defaultBlockState());
                else if (r < 330) w.set(x, top + 1, z, (rnd.nextInt(5) == 0 ? Blocks.FERN : Blocks.SHORT_GRASS).defaultBlockState());
                else if (r < 345 && w.get(x, top + 2, z).isAir()) {
                    Block t = tall[rnd.nextInt(tall.length)];
                    w.set(x, top + 1, z, t.defaultBlockState().setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
                    w.set(x, top + 2, z, t.defaultBlockState().setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
                } else if (r < 375) w.set(x, top + 1, z, Blocks.PINK_PETALS.defaultBlockState().setValue(BlockStateProperties.FLOWER_AMOUNT, 1 + rnd.nextInt(4))
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.from2DDataValue(rnd.nextInt(4))));
                else if (r < 380) w.set(x, top + 1, z, Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(BlockStateProperties.AGE_3, 3));
                else if (r < 382) w.set(x, top + 1, z, Blocks.MELON.defaultBlockState());
            }
        }

        void animals() {
            Object[][] herd = {{EntityType.SHEEP, 12}, {EntityType.GOAT, 4}, {EntityType.COW, 5}, {EntityType.PIG, 4}, {EntityType.CHICKEN, 7},
                    {EntityType.RABBIT, 6}, {EntityType.HORSE, 3}, {EntityType.FOX, 2}, {EntityType.CAT, 2}, {EntityType.LLAMA, 2},
                    {EntityType.ARMADILLO, 2}, {EntityType.PARROT, 4}, {EntityType.BEE, 6}, {EntityType.PANDA, 1}, {EntityType.DONKEY, 2}};
            for (Object[] h : herd) {
                EntityType<?> type = (EntityType<?>) h[0];
                for (int i = 0; i < (Integer) h[1]; i++) {
                    int x = CX - 100 + rnd.nextInt(201), z = CZ - 100 + rnd.nextInt(201);
                    if (Math.abs(z - riverZ(x)) < 5 || archZone(x, z)) continue;
                    int top = topAt(x, z);
                    Entity e = type.create(l);
                    if (e == null) continue;
                    e.moveTo(x + 0.5, top + 1, z + 0.5, rnd.nextFloat() * 360F, 0);
                    if (e instanceof Mob m) m.setPersistenceRequired();
                    if (e instanceof Sheep s) s.setColor(DyeColor.byId(rnd.nextInt(3) == 0 ? rnd.nextInt(16) : 0));
                    l.addFreshEntity(e);
                }
            }
            Object[][] river = {{EntityType.SALMON, 8}, {EntityType.TROPICAL_FISH, 8}, {EntityType.FROG, 5}};
            for (Object[] h : river) {
                EntityType<?> type = (EntityType<?>) h[0];
                for (int i = 0; i < (Integer) h[1]; i++) {
                    int x = CX - 120 + rnd.nextInt(241);
                    int z = (int) Math.round(riverZ(x)) + (type == EntityType.FROG ? (rnd.nextBoolean() ? 3 : -3) : 0);
                    Entity e = type.create(l);
                    if (e == null) continue;
                    e.moveTo(x + 0.5, type == EntityType.FROG ? S + 1 : S - 0.5, z + 0.5, rnd.nextFloat() * 360F, 0);
                    if (e instanceof Mob m) m.setPersistenceRequired();
                    l.addFreshEntity(e);
                }
            }
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

    /** Beds in the Garden: lie down and sleep in peace (the Garden's own rules for beds don't apply). */
    @SubscribeEvent
    public static void bed(PlayerInteractEvent.RightClickBlock event) {
        if (!inside(event.getLevel()) || !(event.getEntity() instanceof ServerPlayer p)) return;
        BlockState s = event.getLevel().getBlockState(event.getPos());
        if (!(s.getBlock() instanceof net.minecraft.world.level.block.BedBlock)) return;
        BlockPos head = s.getValue(net.minecraft.world.level.block.BedBlock.PART) == net.minecraft.world.level.block.state.properties.BedPart.HEAD ? event.getPos()
                : event.getPos().relative(s.getValue(net.minecraft.world.level.block.BedBlock.FACING));
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!p.isSleeping()) p.startSleeping(head);
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        MinecraftServer s = event.getServer();
        if (s.getTickCount() % 10 != 0) return;
        ServerLevel g = s.getLevel(KEY);
        if (g == null) return;
        int ground = data(g).ground;
        afterTick(g);
        for (ServerPlayer p : g.players()) {
            // the Garden doesn't let you fall away
            if (p.getY() < g.getMinBuildHeight() + 3) {
                p.teleportTo(g, 0.5, ground + 1, 7.5, 180F, 0);
                p.fallDistance = 0;
                p.displayClientMessage(Component.literal("The Garden catches you.").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC), true);
            }
            if (p.isSleeping()) p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, 1, true, false));
            if (GatePass.banished(p)) leave(p);
        }
        if (s.getTickCount() % 200 != 0) return;
        for (Entity e : g.getAllEntities()) {
            if (e instanceof Mob m && m.getY() < g.getMinBuildHeight() + 3) {
                m.teleportTo(0.5 + m.getRandom().nextInt(20) - 10, ground + 1, 30.5);
                m.fallDistance = 0;
            }
        }
    }
}
