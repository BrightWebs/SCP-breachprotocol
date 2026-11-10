package com.breachprotocol.career;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.compat.Unleashed;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.*;

/**
 * The Vault: the Administrator's own dimension. A long containment hall, cells built to measure for
 * whatever is put in them (big enough that nothing suffocates, glass on the corridor side so you can
 * walk past and see them all), and a storeroom of twenty double chests where the Foundation's surplus
 * goes. Reached through the Vault power: see what's held (a 3D view of each), pull it out to your side,
 * trap what you're looking at, browse the chests, step in and out yourself.
 */
@EventBusSubscriber(modid = BreachProtocol.MODID)
public final class Vault {
    private Vault() {}

    public static final ResourceKey<Level> DIM = ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(BreachProtocol.MODID, "vault"));
    private static final int Y = 64, SPACING = 4000, CHESTS = 20;
    /** Placed quietly: no knock-on updates, so nothing pops off its wall or floor while the Vault is being built or patched. */
    private static final int F = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

    // ================================================================== who owns which part of it

    public static final class Cell {
        public UUID entity;
        public String type = "", name = "";
        public int x0, z0, x1, z1, h;
        /** The walls: outer x and z, and which z the glass front is on. */
        public int fx0, fx1, fza, fzb, front;
        /** A tank: full of water, entered through an airlock so none of it reaches the hall. */
        public boolean water;
        public CompoundTag look = new CompoundTag();

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            if (entity != null) t.putUUID("e", entity);
            t.putString("ty", type); t.putString("n", name);
            t.putIntArray("b", new int[]{x0, z0, x1, z1, h, fx0, fx1, fza, fzb, front});
            t.put("look", look);
            t.putBoolean("w", water);
            return t;
        }

        static Cell load(CompoundTag t) {
            Cell c = new Cell();
            if (t.hasUUID("e")) c.entity = t.getUUID("e");
            c.type = t.getString("ty"); c.name = t.getString("n");
            int[] b = t.getIntArray("b");
            if (b.length >= 5) { c.x0 = b[0]; c.z0 = b[1]; c.x1 = b[2]; c.z1 = b[3]; c.h = b[4]; }
            if (b.length == 10 && b[6] > b[5]) { c.fx0 = b[5]; c.fx1 = b[6]; c.fza = b[7]; c.fzb = b[8]; c.front = b[9]; }
            else if (c.x1 > c.x0) {
                // saved before the walls were recorded: they're one block outside the inside, and the front is the side nearest the hall
                c.fx0 = c.x0 - 1; c.fx1 = c.x1 + 1; c.fza = c.z0 - 1; c.fzb = c.z1 + 1;
                c.front = c.z1 < 0 ? c.fzb : c.fza;
            }
            c.look = t.getCompound("look");
            c.water = t.getBoolean("w");
            return c;
        }

        /** A cell with real walls (not one lost to an old save). */
        boolean real() { return fx1 > fx0 + 2 && fzb > fza + 2 && h > 0 && (front == fza || front == fzb) && Math.abs(front) == 4; }

        Vec3 centre() { return new Vec3((x0 + x1) / 2.0 + 0.5, Y + 1, (z0 + z1) / 2.0 + 0.5); }

        /** Into the cell from the hall: +1 for cells north of it (z below the front), so "in" is -1 there. */
        int in() { return front < 0 ? -1 : 1; }

        /** The airlock: three wide, three deep, three high, just inside the outer door. */
        boolean inLock(int x, int y, int z) {
            int d = (z - front) * in();
            return x >= fx0 + 1 && x <= fx0 + 3 && d >= 1 && d <= 3 && y >= Y + 1 && y <= Y + 3;
        }

        BlockPos innerDoor() { return new BlockPos(fx0 + 2, Y + 1, front + 4 * in()); }

        BlockPos outerDoor() { return new BlockPos(fx0 + 1, Y + 1, front); }

        boolean holds(Entity e) { return e.getX() >= x0 && e.getX() <= x1 + 1 && e.getZ() >= z0 && e.getZ() <= z1 + 1 && e.getY() >= Y && e.getY() <= Y + h + 1; }
    }

    public static final class Region {
        public int slot, nextNorth = 2, nextSouth = 2, hallTo = 12, throneX;
        public boolean built, migrated;
        /** The storeroom: twenty chests of fifty-four slots, up to 999 of anything in each. */
        public final ItemStack[] item = new ItemStack[CHESTS * 54];
        public final int[] count = new int[CHESTS * 54];
        public String backDim = "";
        public long backPos;
        public final List<Cell> cells = new ArrayList<>();

        int ox() { return slot * SPACING; }
    }

    public static final class Data extends SavedData {
        final Map<UUID, Region> regions = new HashMap<>();
        int nextSlot = 0;
        long lastSurplus;

        public static final SavedData.Factory<Data> FACTORY = new SavedData.Factory<>(Data::new, Data::load, null);

        static Data load(CompoundTag t, HolderLookup.Provider p) {
            Data d = new Data();
            d.nextSlot = t.getInt("next");
            d.lastSurplus = t.getLong("surplus");
            for (Tag x : t.getList("regions", Tag.TAG_COMPOUND)) {
                CompoundTag c = (CompoundTag) x;
                Region r = new Region();
                r.slot = c.getInt("slot"); r.built = c.getBoolean("built");
                r.nextNorth = c.getInt("nn"); r.nextSouth = c.getInt("ns"); r.hallTo = c.getInt("hall"); r.throneX = c.getInt("tx");
                r.backDim = c.getString("bd"); r.backPos = c.getLong("bp");
                for (Tag y : c.getList("cells", Tag.TAG_COMPOUND)) r.cells.add(Cell.load((CompoundTag) y));
                r.migrated = c.getBoolean("mig");
                for (Tag y : c.getList("store", Tag.TAG_COMPOUND)) {
                    CompoundTag it = (CompoundTag) y;
                    int k = it.getInt("k");
                    ItemStack st = ItemStack.parseOptional(p, it.getCompound("i"));
                    if (k < 0 || k >= r.item.length || st.isEmpty() || it.getInt("n") <= 0) continue;
                    r.item[k] = st.copyWithCount(1);
                    r.count[k] = it.getInt("n");
                }
                d.regions.put(c.getUUID("owner"), r);
            }
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag t, HolderLookup.Provider p) {
            t.putInt("next", nextSlot);
            t.putLong("surplus", lastSurplus);
            ListTag l = new ListTag();
            for (Map.Entry<UUID, Region> e : regions.entrySet()) {
                Region r = e.getValue();
                CompoundTag c = new CompoundTag();
                c.putUUID("owner", e.getKey());
                c.putInt("slot", r.slot); c.putBoolean("built", r.built);
                c.putInt("nn", r.nextNorth); c.putInt("ns", r.nextSouth); c.putInt("hall", r.hallTo); c.putInt("tx", r.throneX);
                c.putString("bd", r.backDim); c.putLong("bp", r.backPos);
                ListTag cl = new ListTag();
                for (Cell x : r.cells) cl.add(x.save());
                c.put("cells", cl);
                c.putBoolean("mig", r.migrated);
                ListTag st = new ListTag();
                for (int k = 0; k < r.item.length; k++) {
                    if (r.item[k] == null || r.count[k] <= 0) continue;
                    CompoundTag it = new CompoundTag();
                    it.putInt("k", k);
                    it.put("i", r.item[k].copyWithCount(1).save(p));
                    it.putInt("n", r.count[k]);
                    st.add(it);
                }
                c.put("store", st);
                l.add(c);
            }
            t.put("regions", l);
            return t;
        }
    }

    public static Data data(MinecraftServer s) { return s.overworld().getDataStorage().computeIfAbsent(Data.FACTORY, "breachprotocol_vault"); }

    static boolean chief(ServerPlayer p) { return Career.rank(p) == Rank.ADMINISTRATOR || Career.isAscended(p); }

    public static ServerLevel level(MinecraftServer s) { return s.getLevel(DIM); }

    private static Region region(ServerPlayer p) {
        Data d = data(p.server);
        Region r = d.regions.computeIfAbsent(p.getUUID(), k -> { Region n = new Region(); n.slot = d.nextSlot++; return n; });
        d.setDirty();
        ServerLevel v = level(p.server);
        if (v != null && !r.built) { build(v, r, false); extendHall(v, r, 12); }
        if (v != null && r.built && !r.migrated) migrate(v, r);
        return r;
    }

    // ================================================================== building it

    private static BlockState b(String unleashed, Block fallback) { return Unleashed.block(unleashed, fallback).defaultBlockState(); }

    private static void forceChunks(ServerLevel v, int x0, int z0, int x1, int z1) {
        for (int cx = x0 >> 4; cx <= x1 >> 4; cx++) for (int cz = z0 >> 4; cz <= z1 >> 4; cz++) v.setChunkForced(cx, cz, true);
    }

    private static void box(ServerLevel v, int x0, int y0, int z0, int x1, int y1, int z1, BlockState s) {
        for (BlockPos q : BlockPos.betweenClosed(x0, y0, z0, x1, y1, z1)) v.setBlock(q, s, F);
    }

    private static BlockState mc(String id) { return net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id)).defaultBlockState(); }

    /** The storeroom (and arrival). On a rebuild the chests keep what's in them. */
    private static void build(ServerLevel v, Region r, boolean rebuild) {
        int o = r.ox();
        forceChunks(v, o - 34, -24, o + 40, 24);
        BlockState floor = b("facility_precast_concrete", Blocks.SMOOTH_STONE), wall = b("facility_concrete", Blocks.LIGHT_GRAY_CONCRETE),
                light = Blocks.SEA_LANTERN.defaultBlockState(), air = Blocks.AIR.defaultBlockState();
        Set<Long> chests = new HashSet<>();
        for (int k = 0; k < CHESTS; k++) for (int h = 0; h < 2; h++) chests.add(chest(o, k, h).asLong());
        for (BlockPos q : BlockPos.betweenClosed(o - 32, Y, -11, o - 4, Y + 7, 11)) {
            boolean edge = q.getX() == o - 32 || q.getX() == o - 4 || q.getZ() == -11 || q.getZ() == 11;
            BlockState st;
            if (q.getY() == Y) st = floor;
            else if (q.getY() == Y + 7) st = (q.getX() - o + 29) % 4 == 0 && (q.getZ() == -8 || q.getZ() == 0 || q.getZ() == 8) ? light : wall;
            else if (edge && !(q.getX() == o - 4 && Math.abs(q.getZ()) <= 3 && q.getY() <= Y + 6)) st = wall;
            else if (chests.contains(q.asLong())) continue;
            else st = air;
            v.setBlock(q, st, F);
        }
        // twenty double chests: ten along each long wall (a chest that's already there keeps its contents)
        for (int k = 0; k < CHESTS; k++) {
            boolean north = k < 10;
            BlockState c = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, north ? net.minecraft.core.Direction.SOUTH : net.minecraft.core.Direction.NORTH);
            v.setBlock(chest(o, k, 0), c.setValue(ChestBlock.TYPE, north ? ChestType.RIGHT : ChestType.LEFT), F);
            v.setBlock(chest(o, k, 1), c.setValue(ChestBlock.TYPE, north ? ChestType.LEFT : ChestType.RIGHT), F);
        }
        r.built = true;
    }

    /** Chest k (0..19), half 0 (west) or 1 (east). */
    private static BlockPos chest(int o, int k, int half) {
        int col = k % 10;
        return new BlockPos(o - 30 + col * 2 + half, Y + 1, k < 10 ? -10 : 10);
    }

    /** A stretch of the corridor, x from xa to xb. */
    private static void hall(ServerLevel v, Region r, int xa, int xb) {
        int o = r.ox();
        BlockState floor = b("facility_precast_concrete", Blocks.SMOOTH_STONE), wall = b("facility_concrete", Blocks.LIGHT_GRAY_CONCRETE), light = Blocks.SEA_LANTERN.defaultBlockState(), air = Blocks.AIR.defaultBlockState();
        for (int x = xa; x <= xb; x++) for (int z = -4; z <= 4; z++) for (int y = 0; y <= 7; y++) {
            BlockState st = y == 0 ? floor : y == 7 ? ((x - o) % 6 == 0 && z == 0 ? light : wall) : Math.abs(z) == 4 ? wall : air;
            v.setBlock(new BlockPos(x, Y + y, z), st, F);
        }
    }

    /** The corridor out past the cells, as long as it needs to be, and the throne room always at its end. */
    private static void extendHall(ServerLevel v, Region r, int to) {
        if (to <= r.hallTo && r.throneX != 0) return;
        int o = r.ox(), end = Math.max(r.hallTo, to);
        List<Entity> inThrone = new ArrayList<>();
        int oldT = r.throneX;
        if (oldT != 0) {
            AABB box = new AABB(oldT, Y, -12, oldT + 27, Y + 14, 13);
            inThrone.addAll(v.getEntities((Entity) null, box, e -> !(e instanceof net.minecraft.world.entity.Display)));
            for (Entity e : v.getEntities((Entity) null, box, e -> e instanceof net.minecraft.world.entity.Display)) { e.ejectPassengers(); e.discard(); }
            inThrone.removeIf(e -> e instanceof net.minecraft.world.entity.item.ItemEntity);
            box(v, oldT, Y, -12, oldT + 26, Y + 13, 12, Blocks.AIR.defaultBlockState());
        }
        forceChunks(v, o - 4, -40, o + end + 40, 40);
        hall(v, r, oldT != 0 ? o + r.hallTo + 1 : o - 3, o + end);
        r.hallTo = end;
        throne(v, r, o + end + 1);
        int dx = oldT == 0 ? 0 : r.throneX - oldT;
        for (Entity e : inThrone) e.teleportTo(e.getX() + dx, e.getY(), e.getZ());
    }

    // ---------------------------------------------------------------- the throne room

    private static final int TL = 26;

    private static BlockPos throneSeat(Region r) { return new BlockPos(r.throneX + 24, Y + 4, 0); }

    private static void throne(ServerLevel v, Region r, int T) {
        r.throneX = T;
        BlockState wall = mc("minecraft:polished_blackstone_bricks"), floor = mc("minecraft:polished_blackstone"), ceil = mc("minecraft:deepslate_tiles"),
                border = mc("minecraft:chiseled_polished_blackstone"), gilded = mc("minecraft:gilded_blackstone"), gold = Blocks.GOLD_BLOCK.defaultBlockState(),
                quartz = Blocks.QUARTZ_PILLAR.defaultBlockState(), cq = Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState(), air = Blocks.AIR.defaultBlockState(),
                carpet = Blocks.RED_CARPET.defaultBlockState(), chain = Blocks.CHAIN.defaultBlockState(),
                soul = Blocks.SOUL_LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true),
                lantern = Blocks.LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true),
                glow = Blocks.LIGHT.defaultBlockState().setValue(net.minecraft.world.level.block.LightBlock.LEVEL, 11);
        var EAST = net.minecraft.core.Direction.EAST;
        BlockState step = mc("minecraft:polished_blackstone_brick_stairs").setValue(net.minecraft.world.level.block.StairBlock.FACING, EAST);
        // the shell
        for (BlockPos q : BlockPos.betweenClosed(T, Y, -12, T + TL, Y + 13, 12)) {
            int x = q.getX() - T, y = q.getY() - Y, z = q.getZ();
            boolean edge = x == 0 || x == TL || Math.abs(z) == 12;
            BlockState st;
            if (y == 0) st = Math.abs(z) == 11 || x == 1 || x == TL - 1 ? border : Math.abs(z) == 2 && x < 20 ? gilded : floor;
            else if (y == 13) st = ceil;
            else if (edge) st = wall;
            else st = air;
            v.setBlock(q, st, F);
        }
        // the doorway from the corridor, framed in gold
        box(v, T, Y + 1, -3, T, Y + 6, 3, air);
        box(v, T, Y + 1, -4, T, Y + 7, -4, gold); box(v, T, Y + 1, 4, T, Y + 7, 4, gold); box(v, T, Y + 7, -4, T, Y + 7, 4, gold);
        // the runner to the dais
        box(v, T + 1, Y + 1, -1, T + 19, Y + 1, 1, carpet);
        // quartz colonnade, banners between, lanterns hung from the ceiling
        for (int x = 4; x <= 19; x += 5) for (int sz = -1; sz <= 1; sz += 2) {
            box(v, T + x, Y + 1, 8 * sz, T + x, Y + 11, 8 * sz, quartz);
            v.setBlock(new BlockPos(T + x, Y + 12, 8 * sz), cq, F);
            v.setBlock(new BlockPos(T + x, Y + 1, 8 * sz), cq, F);
        }
        for (int x = 6; x <= 16; x += 5) for (int sz = -1; sz <= 1; sz += 2) {
            v.setBlock(new BlockPos(T + x, Y + 7, 11 * sz), Blocks.RED_WALL_BANNER.defaultBlockState().setValue(net.minecraft.world.level.block.WallBannerBlock.FACING, sz < 0 ? net.minecraft.core.Direction.SOUTH : net.minecraft.core.Direction.NORTH), F);
            v.setBlock(new BlockPos(T + x, Y + 12, 8 * sz), chain, F);
            v.setBlock(new BlockPos(T + x, Y + 11, 8 * sz), soul, F);
        }
        for (int x : new int[]{7, 14}) {
            box(v, T + x, Y + 10, 0, T + x, Y + 12, 0, chain);
            v.setBlock(new BlockPos(T + x, Y + 9, 0), lantern, F);
        }
        for (int x = 3; x <= 23; x += 5) for (int z = -6; z <= 6; z += 6) v.setBlock(new BlockPos(T + x, Y + 12, z), glow, F);
        // the dais: three steps up, wider at the bottom
        for (int lvl = 0; lvl < 3; lvl++) {
            int x0 = 20 + lvl, half = 5 - lvl, y = Y + 1 + lvl;
            for (int x = x0; x <= 25; x++) for (int z = -half; z <= half; z++) v.setBlock(new BlockPos(T + x, y, z), x == x0 ? step : wall, F);
        }
        for (int sz = -1; sz <= 1; sz += 2) {
            v.setBlock(new BlockPos(T + 21, Y + 2, 5 * sz), Blocks.SOUL_CAMPFIRE.defaultBlockState(), F);
            v.setBlock(new BlockPos(T + 23, Y + 4, 3 * sz), Blocks.LANTERN.defaultBlockState(), F);
        }
        // the throne
        v.setBlock(new BlockPos(T + 24, Y + 4, 0), mc("minecraft:polished_blackstone_stairs").setValue(net.minecraft.world.level.block.StairBlock.FACING, EAST), F);
        for (int sz = -1; sz <= 1; sz += 2) {
            v.setBlock(new BlockPos(T + 24, Y + 4, sz), mc("minecraft:polished_blackstone_wall"), F);
            box(v, T + 25, Y + 4, sz, T + 25, Y + 5, sz, wall);
            v.setBlock(new BlockPos(T + 25, Y + 6, sz), gilded, F);
        }
        box(v, T + 25, Y + 4, 0, T + 25, Y + 6, 0, wall);
        v.setBlock(new BlockPos(T + 25, Y + 7, 0), border, F);
        v.setBlock(new BlockPos(T + 25, Y + 8, 0), gold, F);
        v.setBlock(new BlockPos(T + 25, Y + 9, 0), Blocks.END_ROD.defaultBlockState(), F);
        // behind it, a wall of crying obsidian framed in gold
        for (int z = -5; z <= 5; z++) for (int y = 2; y <= 12; y++)
            v.setBlock(new BlockPos(T + TL, Y + y, z), Math.abs(z) == 5 || y == 2 || y == 12 ? gold : mc("minecraft:crying_obsidian"), F);
    }

    /** A cell made to measure: walls of containment panel, a glass front on the corridor with an iron door, room to spare. */
    private static Cell makeCell(ServerLevel v, Region r, LivingEntity e) {
        int[] need = needs(e);
        boolean water = need[0] == 1;
        int w = need[1], h = need[2];
        boolean north = r.nextNorth <= r.nextSouth;
        int o = r.ox(), x0 = o + (north ? r.nextNorth : r.nextSouth), x1 = x0 + w + 1;
        int zFront = north ? -4 : 4, zBack = north ? -4 - (w + 1) : 4 + (w + 1);
        Cell c = new Cell();
        c.fx0 = x0; c.fx1 = x1; c.fza = Math.min(zFront, zBack); c.fzb = Math.max(zFront, zBack); c.front = zFront;
        c.x0 = x0 + 1; c.x1 = x1 - 1; c.z0 = c.fza + 1; c.z1 = c.fzb - 1; c.h = h;
        c.water = water;
        extendHall(v, r, x1 - o + 3);
        drawCell(v, c);
        if (north) r.nextNorth = x1 - o + 2; else r.nextSouth = x1 - o + 2;
        return c;
    }

    /** Things that live in water and would die out of it. */
    static boolean aquatic(LivingEntity e) {
        if (e instanceof net.minecraft.world.entity.animal.WaterAnimal || e instanceof net.minecraft.world.entity.monster.Guardian) return true;
        if (e.getType().is(net.minecraft.tags.EntityTypeTags.AQUATIC)) return true;
        if (e instanceof Mob m && m.getNavigation() instanceof net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation) return true;
        if (!e.canBreatheUnderwater()) return false;
        // other mods' sea creatures (Manas Reborn's megalodon and the like): they breathe water, and either swim
        // like fish, were caught in the water, or are plainly sea things by name
        if (e instanceof Mob m && m.getNavigation() instanceof net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation && e.isInWater()) return true;
        if (e.isUnderWater()) return true;
        String id = String.valueOf(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType())).toLowerCase(Locale.ROOT) + " " + e.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        for (String k : SEA) if (id.contains(k)) return true;
        return false;
    }

    private static final String[] SEA = {"megalodon", "shark", "fish", "whale", "squid", "octopus", "kraken", "leviathan", "moray", "manta", "stingray", "jellyfish", "dolphin", "orca", "seaserpent", "sea_serpent", "piranha", "angler", "mosasaur", "plesiosaur", "siren", "coelacanth", "barracuda"};

    /** What a creature needs: water or not, and a cell (inside) this wide and deep, and this high. Long things get room for their length, not just their hitbox. */
    static int[] needs(LivingEntity e) {
        boolean water = aquatic(e);
        double bw = e.getBbWidth(), bh = e.getBbHeight(), big = Math.max(bw, bh);
        int w = water ? Math.max(12, (int) Math.ceil(big * 2) + 6) : Math.max(7, (int) Math.ceil(Math.max(bw, bh * 0.8)) + 4);
        int h = water ? Math.max(8, (int) Math.ceil(big) + 4) : Math.max(6, (int) Math.ceil(bh) + 3);
        return new int[]{water ? 1 : 0, w, h};
    }

    /** Does this cell suit what's in it? If not, a new one that does, and the old one taken down. */
    private static boolean fit(ServerLevel v, Region r, Cell c, LivingEntity e) {
        if (!c.real()) return false;
        int[] n = needs(e);
        if ((n[0] == 1) == c.water && c.x1 - c.x0 + 1 >= n[1] && c.z1 - c.z0 + 1 >= n[1] && c.h >= n[2]) return false;
        rebuildFor(v, r, c, e);
        Vec3 at = c.centre();
        e.teleportTo(at.x, at.y, at.z);
        e.setDeltaMovement(Vec3.ZERO);
        e.fallDistance = 0;
        data(v.getServer()).setDirty();
        return true;
    }

    /** The cell keeps its number; the room is taken down and built again, elsewhere along the hall, to suit this. */
    private static void rebuildFor(ServerLevel v, Region r, Cell c, LivingEntity e) {
        demolish(v, r, c);
        Cell nc = makeCell(v, r, e);
        c.fx0 = nc.fx0; c.fx1 = nc.fx1; c.fza = nc.fza; c.fzb = nc.fzb; c.front = nc.front;
        c.x0 = nc.x0; c.x1 = nc.x1; c.z0 = nc.z0; c.z1 = nc.z1; c.h = nc.h; c.water = nc.water;
        Vec3 m = c.centre();
        v.sendParticles(ParticleTypes.END_ROD, m.x, m.y + 1, m.z, 40, (c.x1 - c.x0) / 3.0, c.h / 3.0, (c.z1 - c.z0) / 3.0, 0.02);
        v.playSound(null, BlockPos.containing(m), SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1F, 1.2F);
        data(v.getServer()).setDirty();
    }

    /** A cell taken down: gone back to the void, its stretch of hall made whole, its neighbours across the hall redrawn. */
    private static void demolish(ServerLevel v, Region r, Cell c) {
        if (!c.real()) return;
        for (BlockPos q : BlockPos.betweenClosed(c.fx0, 0, c.fza, c.fx1, Y + c.h + 1, c.fzb))
            if (Math.abs(q.getZ()) > 4 || q.getY() < Y) v.setBlock(q, Blocks.AIR.defaultBlockState(), F);
        hall(v, r, c.fx0, c.fx1);
        for (Cell o : r.cells) if (o != c && o.fx1 >= c.fx0 && o.fx0 <= c.fx1) drawCell(v, o);
    }

    private static void drawCell(ServerLevel v, Cell c) {
        if (!c.real()) return;
        forceChunks(v, c.fx0, c.fza, c.fx1, c.fzb);
        BlockState wall = b("containment_room_wall_middle", Blocks.IRON_BLOCK), floor = b("containment_room_floor", Blocks.SMOOTH_STONE),
                ceil = b("containment_room_ceiling", Blocks.SMOOTH_STONE), glass = Blocks.TINTED_GLASS.defaultBlockState(), lamp = b("containment_room_lamp", Blocks.SEA_LANTERN);
        for (BlockPos q : BlockPos.betweenClosed(c.fx0, Y, c.fza, c.fx1, Y + c.h + 1, c.fzb)) {
            boolean edge = q.getX() == c.fx0 || q.getX() == c.fx1 || q.getZ() == c.fza || q.getZ() == c.fzb;
            BlockState s;
            if (q.getY() == Y) s = floor;
            else if (q.getY() == Y + c.h + 1) s = ceil;
            else if (!edge) s = Blocks.AIR.defaultBlockState();
            else if (q.getZ() == c.front && q.getX() != c.fx0 && q.getX() != c.fx1 && q.getY() <= Y + Math.min(c.h, 6)) s = glass;
            else s = wall;
            v.setBlock(q, s, F);
        }
        v.setBlock(new BlockPos((c.fx0 + c.fx1) / 2, Y + c.h + 1, (c.fza + c.fzb) / 2), lamp, F);
        if (c.water) tank(v, c, wall);
        // the door: a Foundation containment door, Level 5 scanner on the corridor side, exit button inside
        var out = c.front < 0 ? net.minecraft.core.Direction.SOUTH : net.minecraft.core.Direction.NORTH;
        for (int y = 1; y <= 3; y++) { v.setBlock(new BlockPos(c.fx0 + 3, Y + y, c.front), wall, F); v.setBlock(new BlockPos(c.fx0 + 1, Y + 3, c.front), wall, F); v.setBlock(new BlockPos(c.fx0 + 2, Y + 3, c.front), wall, F); }
        new Doors(v).gate(c.fx0 + 1, c.front, out);
    }

    /** The tank: water to the ceiling, a sandy floor with weed in it, lights down low, and the airlock by the door. */
    private static void tank(ServerLevel v, Cell c, BlockState wall) {
        // anything that spilled under the tank before (the void below eats nothing, it just pools) goes
        for (BlockPos q : BlockPos.betweenClosed(c.fx0, 0, c.fza, c.fx1, Y - 1, c.fzb)) if (!v.getBlockState(q).isAir()) v.setBlock(q, Blocks.AIR.defaultBlockState(), F);
        // a floor that stays put: prismarine, not sand (sand falls)
        BlockState water = Blocks.WATER.defaultBlockState(), sand = Blocks.DARK_PRISMARINE.defaultBlockState(), glass = Blocks.TINTED_GLASS.defaultBlockState(),
                lockGlass = Blocks.GLASS.defaultBlockState(), grass = Blocks.SEAGRASS.defaultBlockState(), lantern = Blocks.SEA_LANTERN.defaultBlockState(), air = Blocks.AIR.defaultBlockState();
        boolean flooded = lockFlooded(v, c);
        for (BlockPos q : BlockPos.betweenClosed(c.x0, Y, c.z0, c.x1, Y + c.h, c.z1)) {
            int x = q.getX(), y = q.getY(), z = q.getZ();
            if (c.inLock(x, y, z)) { v.setBlock(q, flooded ? water : air, F); continue; }
            int d = (z - c.front) * c.in();
            boolean lockWall = x >= c.fx0 + 1 && x <= c.fx0 + 4 && d >= 1 && d <= 4 && y >= Y + 1 && y <= Y + 4 && !(x <= c.fx0 + 3 && d <= 3 && y <= Y + 3);
            if (y == Y) { v.setBlock(q, (x + z) % 7 == 0 ? lantern : sand, F); continue; }
            if (lockWall) { v.setBlock(q, y == Y + 4 ? wall : lockGlass, F); continue; }
            boolean weed = y == Y + 1 && Math.floorMod(x * 31 + z * 17, 5) == 0;
            v.setBlock(q, weed ? grass : water, F);
        }
        // the inner door: iron, only you open it, and only with the outer door shut
        BlockPos d = c.innerDoor();
        var face = c.in() < 0 ? net.minecraft.core.Direction.NORTH : net.minecraft.core.Direction.SOUTH;
        BlockState door = Blocks.IRON_DOOR.defaultBlockState().setValue(net.minecraft.world.level.block.DoorBlock.FACING, face);
        v.setBlock(d, door.setValue(net.minecraft.world.level.block.DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER), F);
        v.setBlock(d.above(), door.setValue(net.minecraft.world.level.block.DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER), F);
        // a light in the airlock's ceiling
        v.setBlock(new BlockPos(c.fx0 + 2, Y + 4, c.front + 2 * c.in()), lantern, F);
    }

    private static boolean lockFlooded(ServerLevel v, Cell c) {
        return v.getFluidState(new BlockPos(c.fx0 + 2, Y + 1, c.front + 2 * c.in())).is(net.minecraft.tags.FluidTags.WATER);
    }

    private static boolean open(ServerLevel v, BlockPos pos) {
        BlockState st = v.getBlockState(pos);
        return st.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.OPEN) && st.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.OPEN);
    }

    /** Flood the airlock (the inner door is opening) or drain it (it has shut). */
    private static void lock(ServerLevel v, Cell c, boolean flood) {
        for (int x = c.fx0 + 1; x <= c.fx0 + 3; x++) for (int dd = 1; dd <= 3; dd++) for (int y = Y + 1; y <= Y + 3; y++) {
            BlockPos q = new BlockPos(x, y, c.front + dd * c.in());
            v.setBlock(q, flood ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState(), F);
            if (!flood && v.random.nextInt(3) == 0) v.sendParticles(ParticleTypes.BUBBLE_POP, x + 0.5, y + 0.3, q.getZ() + 0.5, 2, 0.3, 0.2, 0.3, 0.01);
        }
        BlockPos mid = new BlockPos(c.fx0 + 2, Y + 2, c.front + 2 * c.in());
        v.playSound(null, mid, flood ? SoundEvents.BUCKET_EMPTY : SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1F, flood ? 0.6F : 0.7F);
        v.playSound(null, mid, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.5F, 0.6F);
    }

    /** Which tank's inner door is this, if any. */
    private static Cell tankWithDoor(Region r, BlockPos pos) {
        for (Cell c : r.cells) if (c.water && (c.innerDoor().equals(pos) || c.innerDoor().above().equals(pos))) return c;
        return null;
    }

    /** SCP Unleashed's doors, placed the way the facility builder places them. */
    private static final class Doors extends com.breachprotocol.world.FacilityBuilder {
        Doors(ServerLevel v) { super(v, BlockPos.ZERO); }
        void gate(int x, int z, net.minecraft.core.Direction out) { gate("containment_room_door", 2, 2, x, z, Y, out, 5); }
    }

    /** Everything back the way it was built: storeroom (chests keep their contents), corridor, throne room, every cell, and every captive back inside its own. */
    public static void rebuild(ServerPlayer p) {
        ServerLevel v = level(p.server);
        if (v == null || !chief(p)) return;
        int back = repair(v, region(p));
        v.playSound(null, p.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1F, 0.8F);
        say(p, "The Vault is as it was built" + (back > 0 ? ". " + back + (back == 1 ? " captive was" : " captives were") + " put back in " + (back == 1 ? "its cell." : "their cells.") : "."));
        Career.sync(p);
    }

    private static final Set<String> DECOR = Set.of("minecraft:red_carpet", "minecraft:red_banner", "minecraft:chain", "minecraft:lantern", "minecraft:soul_lantern",
            "minecraft:end_rod", "minecraft:soul_campfire", "minecraft:polished_blackstone_wall");

    /** Puts back only what's missing or wrong (a block already right is left alone). Returns how many captives went back in their cells. */
    private static int repair(ServerLevel v, Region r) {
        int o = r.ox();
        build(v, r, true);
        hall(v, r, o - 3, o + r.hallTo);
        throne(v, r, r.throneX == 0 ? o + r.hallTo + 1 : r.throneX);
        int back = 0;
        // cells lost to an old save: one with something alive in it gets a proper cell; an empty one is let go
        for (int i = 0; i < r.cells.size(); i++) {
            Cell c = r.cells.get(i);
            if (c.real()) continue;
            Entity e = c.entity == null ? null : v.getEntity(c.entity);
            if (e instanceof LivingEntity le && le.isAlive()) {
                Cell nc = makeCell(v, r, le);
                c.fx0 = nc.fx0; c.fx1 = nc.fx1; c.fza = nc.fza; c.fzb = nc.fzb; c.front = nc.front;
                c.x0 = nc.x0; c.x1 = nc.x1; c.z0 = nc.z0; c.z1 = nc.z1; c.h = nc.h; c.water = nc.water;
                Vec3 at = c.centre();
                le.teleportTo(at.x, at.y, at.z);
                back++;
            } else r.cells.remove(i--);
        }
        // the hall again, now that nothing false is drawn in it
        hall(v, r, o - 3, o + r.hallTo);
        for (int i = 0; i < r.cells.size(); i++) {
            Cell c = r.cells.get(i);
            drawCell(v, c);
            Entity e = c.entity == null ? null : v.getEntity(c.entity);
            if (e instanceof LivingEntity le && le.isAlive() && fit(v, r, c, le)) { back++; continue; }
            if (e != null && e.isAlive() && !c.holds(e)) { Vec3 at = c.centre(); e.teleportTo(at.x, at.y, at.z); e.setDeltaMovement(Vec3.ZERO); back++; }
        }
        // decorations that were knocked loose before and are lying about
        for (Entity e : v.getEntities((Entity) null, new AABB(o - 34, Y - 2, -60, o + r.hallTo + 40, Y + 30, 60),
                x -> x instanceof net.minecraft.world.entity.item.ItemEntity it && DECOR.contains(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(it.getItem().getItem()).toString()))) e.discard();
        data(v.getServer()).setDirty();
        return back;
    }

    // ================================================================== putting things in, taking them out

    /** What you're looking at, within 32 blocks. */
    private static LivingEntity looked(ServerPlayer p) {
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle(), end = eye.add(look.scale(32));
        HitResult block = p.pick(32, 0, false);
        double max = block.getType() == HitResult.Type.MISS ? 32 * 32 : block.getLocation().distanceToSqr(eye);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(p, eye, end, p.getBoundingBox().expandTowards(look.scale(32)).inflate(1), x -> x instanceof LivingEntity && !(x instanceof Player) && x.isAlive(), max);
        return hit != null && hit.getEntity() instanceof LivingEntity le ? le : null;
    }

    public static void trap(ServerPlayer p) {
        ServerLevel v = level(p.server);
        if (v == null || !chief(p)) return;
        LivingEntity e = looked(p);
        if (e == null) { say(p, "Look at what you want held, then trap it."); return; }
        if (e.isPassenger() || e.isVehicle()) e.ejectPassengers();
        Region r = region(p);
        // an empty cell first: one that already suits it as it is, or else the first empty one, rebuilt to fit
        int[] need = needs(e);
        Cell c = null, empty = null;
        boolean reused = false, rebuilt = false;
        for (Cell x : r.cells) {
            if (!x.real()) continue;
            Entity in = x.entity == null ? null : v.getEntity(x.entity);
            if (in != null && in.isAlive()) continue;
            if (empty == null) empty = x;
            if ((need[0] == 1) == x.water && x.x1 - x.x0 + 1 >= need[1] && x.z1 - x.z0 + 1 >= need[1] && x.h >= need[2]) { c = x; break; }
        }
        if (c != null) reused = true;
        else if (empty != null) { c = empty; rebuildFor(v, r, c, e); reused = rebuilt = true; }
        else c = makeCell(v, r, e);
        c.type = Objects.requireNonNull(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType())).toString();
        c.name = Unleashed.isAnomaly(e) ? Unleashed.designation(e) : e.getName().getString();
        CompoundTag look = new CompoundTag();
        e.saveAsPassenger(look);
        c.look = look;
        ServerLevel from = (ServerLevel) e.level();
        from.sendParticles(ParticleTypes.REVERSE_PORTAL, e.getX(), e.getY() + e.getBbHeight() / 2, e.getZ(), 60, e.getBbWidth() / 2, e.getBbHeight() / 2, e.getBbWidth() / 2, 0.15);
        from.playSound(null, e.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1F, 0.6F);
        Vec3 at = c.centre();
        Entity moved = e.changeDimension(new DimensionTransition(v, at, Vec3.ZERO, e.getYRot(), 0, DimensionTransition.DO_NOTHING));
        if (moved == null) { say(p, "It wouldn't go."); return; }
        if (moved instanceof Mob m) { m.setPersistenceRequired(); m.setTarget(null); }
        c.entity = moved.getUUID();
        if (!reused) r.cells.add(c);
        repair(v, r);
        data(p.server).setDirty();
        v.sendParticles(ParticleTypes.PORTAL, at.x, at.y + 1, at.z, 60, 1, 1, 1, 0.4);
        int no = r.cells.indexOf(c) + 1;
        String kind = c.water ? "a tank, " : "";
        say(p, c.name + " is in your vault, cell " + no + (rebuilt ? " (rebuilt to fit: " + kind + (c.x1 - c.x0 + 1) + " × " + (c.z1 - c.z0 + 1) + " × " + c.h + ")." : "."));
        Career.sync(p);
    }

    public static void release(ServerPlayer p, int index) {
        ServerLevel v = level(p.server);
        Region r = data(p.server).regions.get(p.getUUID());
        if (v == null || r == null || index < 0 || index >= r.cells.size() || !chief(p)) return;
        Cell c = r.cells.get(index);
        Entity e = c.entity == null ? null : v.getEntity(c.entity);
        if (e == null || !e.isAlive()) { say(p, "Cell " + (index + 1) + " is empty. Whatever was in it is gone."); forget(c); data(p.server).setDirty(); Career.sync(p); return; }
        v.sendParticles(ParticleTypes.REVERSE_PORTAL, e.getX(), e.getY() + 1, e.getZ(), 40, 0.6, 0.8, 0.6, 0.15);
        Vec3 at = p.position().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(Math.max(2.5, e.getBbWidth() + 1.5)));
        Entity moved = e.changeDimension(new DimensionTransition(p.serverLevel(), at, Vec3.ZERO, p.getYRot() + 180, 0, DimensionTransition.DO_NOTHING));
        if (moved == null) return;
        p.serverLevel().sendParticles(ParticleTypes.PORTAL, at.x, at.y + 1, at.z, 60, 0.6, 1, 0.6, 0.5);
        p.serverLevel().playSound(null, BlockPos.containing(at), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1F, 1.2F);
        String was = c.name;
        forget(c);
        data(p.server).setDirty();
        say(p, was + " is out, at your side. Cell " + (index + 1) + " stands empty.");
        Career.sync(p);
    }

    private static void forget(Cell c) { c.entity = null; c.name = ""; c.type = ""; c.look = new CompoundTag(); }

    /** "Put it back": what came out of a cell goes back into the same one. */
    public static void restore(ServerPlayer p, int index) {
        ServerLevel v = level(p.server);
        Region r = data(p.server).regions.get(p.getUUID());
        if (v == null || r == null || index < 0 || index >= r.cells.size() || !chief(p)) return;
        Cell c = r.cells.get(index);
        if (c.entity != null) { say(p, "Cell " + (index + 1) + " is occupied."); return; }
        LivingEntity e = looked(p);
        if (e == null) { say(p, "Look at it, then put it back."); return; }
        Vec3 at = c.centre();
        ((ServerLevel) e.level()).sendParticles(ParticleTypes.REVERSE_PORTAL, e.getX(), e.getY() + 1, e.getZ(), 40, 0.6, 0.8, 0.6, 0.15);
        Entity moved = e.changeDimension(new DimensionTransition(v, at, Vec3.ZERO, e.getYRot(), 0, DimensionTransition.DO_NOTHING));
        if (moved == null) return;
        c.entity = moved.getUUID();
        if (!c.real()) repair(v, r);
        else if (moved instanceof LivingEntity ml) fit(v, r, c, ml);
        c.name = Unleashed.isAnomaly(e) ? Unleashed.designation(e) : e.getName().getString();
        CompoundTag look = new CompoundTag();
        moved.saveAsPassenger(look);
        c.look = look;
        data(p.server).setDirty();
        say(p, c.name + " is back in cell " + (index + 1) + ".");
        Career.sync(p);
    }

    /** In and out yourself. */
    public static void go(ServerPlayer p) {
        ServerLevel v = level(p.server);
        if (v == null || !chief(p)) return;
        Region r = region(p);
        if (p.level().dimension() == DIM) {
            ServerLevel back = r.backDim.isEmpty() ? p.server.overworld() : p.server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(r.backDim)));
            if (back == null) back = p.server.overworld();
            BlockPos bp = r.backPos == 0 ? back.getSharedSpawnPos() : BlockPos.of(r.backPos);
            p.teleportTo(back, bp.getX() + 0.5, bp.getY(), bp.getZ() + 0.5, p.getYRot(), p.getXRot());
        } else {
            r.backDim = p.level().dimension().location().toString();
            r.backPos = p.blockPosition().asLong();
            data(p.server).setDirty();
            p.serverLevel().sendParticles(ParticleTypes.REVERSE_PORTAL, p.getX(), p.getY() + 1, p.getZ(), 40, 0.4, 0.8, 0.4, 0.1);
            p.teleportTo(v, r.ox() - 8 + 0.5, Y + 1, 0.5, -90F, 0);
        }
        v.playSound(null, p.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6F, 0.6F);
    }

    // ================================================================== the storeroom

    public static final int CAP = 999;

    private static Container container(ServerLevel v, int o, int k, int half) {
        return v.getBlockEntity(chest(o, k, half)) instanceof ChestBlockEntity c ? c : null;
    }

    /** The old chests' contents move into the storeroom proper (which holds 999 a slot); the chests stay, as the way in. */
    private static void migrate(ServerLevel v, Region r) {
        r.migrated = true;
        for (int k = 0; k < CHESTS; k++) for (int h = 0; h < 2; h++) {
            Container c = container(v, r.ox(), k, h);
            if (c == null) continue;
            for (int i = 0; i < c.getContainerSize(); i++) {
                ItemStack st = c.getItem(i);
                if (st.isEmpty()) continue;
                int idx = k * 54 + h * 27 + i;
                if (r.item[idx] == null) { r.item[idx] = st.copyWithCount(1); r.count[idx] = st.getCount(); }
                else store(r, st.copy());
                c.setItem(i, ItemStack.EMPTY);
            }
            c.setChanged();
        }
        data(v.getServer()).setDirty();
    }

    /** Into the store: onto what's like it first (to 999), then the first free slot. Returns what's left. */
    private static ItemStack store(Region r, ItemStack stack) {
        int left = stack.getCount();
        for (int k = 0; k < r.item.length && left > 0; k++)
            if (r.item[k] != null && r.count[k] < CAP && ItemStack.isSameItemSameComponents(r.item[k], stack)) {
                int n = Math.min(left, CAP - r.count[k]);
                r.count[k] += n; left -= n;
            }
        for (int k = 0; k < r.item.length && left > 0; k++)
            if (r.item[k] == null || r.count[k] <= 0) {
                int n = Math.min(left, CAP);
                r.item[k] = stack.copyWithCount(1); r.count[k] = n; left -= n;
            }
        return left <= 0 ? ItemStack.EMPTY : stack.copyWithCount(left);
    }

    /** Into the storeroom; whatever doesn't fit comes back. */
    public static ItemStack deposit(MinecraftServer s, UUID owner, ItemStack stack) {
        Region r = data(s).regions.get(owner);
        if (level(s) == null || r == null || !r.built || stack.isEmpty()) return stack;
        ItemStack left = store(r, stack);
        data(s).setDirty();
        return left;
    }

    public static boolean has(MinecraftServer s, UUID owner) { Region r = data(s).regions.get(owner); return r != null && r.built && level(s) != null; }

    /** A stack out of a slot (all: as much as your inventory holds). Whatever won't fit stays where it is. */
    public static void take(ServerPlayer p, int chestIndex, int slot, boolean all) {
        Region r = data(p.server).regions.get(p.getUUID());
        if (r == null || !chief(p) || chestIndex < 0 || chestIndex >= CHESTS || slot < 0 || slot >= 54) return;
        int k = chestIndex * 54 + slot;
        if (r.item[k] == null || r.count[k] <= 0) return;
        int moved = 0;
        do {
            int n = Math.min(r.count[k], r.item[k].getMaxStackSize());
            ItemStack give = r.item[k].copyWithCount(n);
            p.getInventory().add(give);
            int got = n - give.getCount();
            if (got <= 0) break;
            r.count[k] -= got; moved += got;
        } while (all && r.count[k] > 0);
        if (r.count[k] <= 0) { r.item[k] = null; r.count[k] = 0; }
        data(p.server).setDirty();
        if (moved > 0) p.playNotifySound(SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.5F, 1.2F);
        else say(p, "Your inventory's full.");
        Career.sync(p);
    }

    /** From your inventory: one stack (slot), every stack of that kind (all), or everything off your hotbar (rest). */
    public static void putSlot(ServerPlayer p, int slot, boolean all) {
        if (!chief(p) || !has(p.server, p.getUUID())) return;
        var inv = p.getInventory();
        if (slot < 0 || slot >= inv.getContainerSize()) return;
        ItemStack want = inv.getItem(slot);
        if (want.isEmpty()) return;
        int moved = 0;
        boolean full = false;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (i != slot && (!all || i >= 36 && i < 40)) continue;
            ItemStack st = inv.getItem(i);
            if (st.isEmpty() || i != slot && !ItemStack.isSameItemSameComponents(st, want)) continue;
            ItemStack left = deposit(p.server, p.getUUID(), st);
            moved += st.getCount() - left.getCount();
            inv.setItem(i, left);
            if (!left.isEmpty()) { full = true; break; }
        }
        if (full) say(p, "The storeroom is full.");
        if (moved > 0) p.playNotifySound(SoundEvents.BUNDLE_INSERT, SoundSource.PLAYERS, 0.6F, 1F);
        Career.sync(p);
    }

    public static void putRest(ServerPlayer p) {
        if (!chief(p) || !has(p.server, p.getUUID())) return;
        var inv = p.getInventory();
        int moved = 0;
        for (int i = 9; i < 36; i++) {
            ItemStack st = inv.getItem(i);
            if (st.isEmpty()) continue;
            ItemStack left = deposit(p.server, p.getUUID(), st);
            moved += st.getCount() - left.getCount();
            inv.setItem(i, left);
            if (!left.isEmpty()) { say(p, "The storeroom is full."); break; }
        }
        if (moved > 0) { p.playNotifySound(SoundEvents.BUNDLE_INSERT, SoundSource.PLAYERS, 0.8F, 0.8F); say(p, moved + " items stored. Your hotbar's untouched."); }
        Career.sync(p);
    }

    public static void put(ServerPlayer p) {
        if (!chief(p) || !has(p.server, p.getUUID())) return;
        ItemStack held = p.getMainHandItem();
        if (held.isEmpty()) return;
        ItemStack left = deposit(p.server, p.getUUID(), held);
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, left);
        say(p, left.isEmpty() ? "Stored." : "The storeroom is full.");
        Career.sync(p);
    }

    // ================================================================== surplus

    private static final String[] SURPLUS = {"minecraft:iron_ingot", "minecraft:gold_ingot", "minecraft:diamond", "minecraft:cooked_beef", "minecraft:bread",
            "minecraft:golden_apple", "minecraft:arrow", "minecraft:redstone", "minecraft:emerald", "minecraft:ender_pearl"};

    /** Once a week the quartermaster ships what the sites have too much of. */
    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        MinecraftServer s = event.getServer();
        if (s.getTickCount() % 10 == 3) keepSafe(s);
        if (s.getTickCount() % 1200 != 77) return;
        Data d = data(s);
        long now = s.overworld().getGameTime();
        if (now - d.lastSurplus < 24000L * 7) return;
        d.lastSurplus = now;
        d.setDirty();
        Random rnd = new Random(now);
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            if (!chief(p) || !has(s, p.getUUID())) continue;
            List<String> got = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.parse(SURPLUS[rnd.nextInt(SURPLUS.length)]));
                ItemStack st = new ItemStack(item, 4 + rnd.nextInt(28));
                st.setCount(Math.min(st.getCount(), st.getMaxStackSize()));
                deposit(s, p.getUUID(), st);
                got.add(st.getCount() + " " + st.getHoverName().getString());
            }
            Career.message(p, O5.O5_12, "The quartermaster's weekly surplus went to your vault: " + String.join(", ", got) + ".");
        }
    }

    // ================================================================== the throne

    private static final Map<UUID, Long> SIT_LATER = new HashMap<>();

    /** Straight to the throne room, and into the seat. */
    public static void toThrone(ServerPlayer p) {
        ServerLevel v = level(p.server);
        if (v == null || !chief(p)) return;
        Region r = region(p);
        if (p.level().dimension() != DIM) { r.backDim = p.level().dimension().location().toString(); r.backPos = p.blockPosition().asLong(); data(p.server).setDirty(); }
        BlockPos seat = throneSeat(r);
        p.teleportTo(v, seat.getX() - 3.5, Y + 1, seat.getZ() + 0.5, 90F, 0);
        SIT_LATER.put(p.getUUID(), p.server.getTickCount() + 6L);
    }

    private static void sit(ServerPlayer p, BlockPos seat) {
        ServerLevel v = p.serverLevel();
        if (p.isPassenger()) p.stopRiding();
        var chair = EntityType.ITEM_DISPLAY.create(v);
        if (chair == null) return;
        chair.moveTo(seat.getX() + 0.5, seat.getY() + 0.55, seat.getZ() + 0.5, 90F, 0);
        chair.addTag("bp_throne");
        v.addFreshEntity(chair);
        p.teleportTo(seat.getX() + 0.5, seat.getY() + 0.5, seat.getZ() + 0.5);
        p.startRiding(chair, true);
        p.setYRot(90F); p.setYHeadRot(90F);
        v.playSound(null, seat, SoundEvents.ARMOR_EQUIP_NETHERITE.value(), SoundSource.PLAYERS, 0.8F, 0.7F);
        v.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, seat.getX() + 0.5, seat.getY() + 1.2, seat.getZ() + 0.5, 20, 0.6, 0.6, 0.6, 0.01);
        say(p, "The Vault is yours. Crouch to stand.");
    }

    /** Captives that slip out of a cell fall back into it, not into the void; empty chairs vanish; anyone who falls is caught. */
    private static void keepSafe(MinecraftServer s) {
        ServerLevel v = level(s);
        if (v == null) return;
        long now = s.getTickCount();
        SIT_LATER.entrySet().removeIf(e -> {
            if (e.getValue() > now) return false;
            ServerPlayer p = s.getPlayerList().getPlayer(e.getKey());
            Region r = p == null ? null : data(s).regions.get(p.getUUID());
            if (p != null && r != null && p.level() == v) sit(p, throneSeat(r));
            return true;
        });
        for (Entity e : v.getEntities(EntityType.ITEM_DISPLAY, x -> x.getTags().contains("bp_throne") && !x.isVehicle())) e.discard();
        // airlocks: never both doors open; a flooded airlock with the hall door open is shut and drained at once; no water in the hall
        for (Region r : data(s).regions.values()) for (Cell c : r.cells) {
            if (!c.water || !v.isLoaded(c.outerDoor())) continue;
            if (!open(v, c.outerDoor())) continue;
            BlockPos in = c.innerDoor();
            BlockState st = v.getBlockState(in);
            if (st.getBlock() instanceof net.minecraft.world.level.block.DoorBlock d && st.getValue(net.minecraft.world.level.block.DoorBlock.OPEN))
                d.setOpen(null, v, st, in, false);
            if (lockFlooded(v, c)) lock(v, c, false);
            for (int x = c.fx0; x <= c.fx0 + 4; x++) for (int y = Y + 1; y <= Y + 3; y++) for (int dd = 0; dd <= 2; dd++) {
                BlockPos q = new BlockPos(x, y, c.front - dd * c.in());
                if (dd > 0 && v.getFluidState(q).is(net.minecraft.tags.FluidTags.WATER)) v.setBlock(q, Blocks.AIR.defaultBlockState(), F);
            }
        }
        if (now % 40 != 3) return;
        for (Map.Entry<UUID, Region> en : data(s).regions.entrySet())
            for (Cell c : en.getValue().cells) {
                Entity e = c.entity == null ? null : v.getEntity(c.entity);
                if (e != null && c.real() && e.getY() < Y - 6) { Vec3 at = c.centre(); e.teleportTo(at.x, at.y, at.z); e.setDeltaMovement(Vec3.ZERO); e.fallDistance = 0; }
            }
        // every twenty seconds: anything in a cell that doesn't suit it (too small, or dry for a sea creature) is moved to one that does
        if (now % 400 == 3) for (Region r : data(s).regions.values())
            for (int i = 0; i < r.cells.size(); i++) {
                Cell c = r.cells.get(i);
                Entity e = c.entity == null ? null : v.getEntity(c.entity);
                if (e instanceof LivingEntity le && le.isAlive()) fit(v, r, c, le);
            }
        for (ServerPlayer p : v.players()) if (p.getY() < Y - 6) {
            Region r = data(s).regions.get(p.getUUID());
            int o = r == null ? (int) Math.round(p.getX() / SPACING) * SPACING : r.ox();
            p.teleportTo(o - 8 + 0.5, Y + 1, 0.5);
            p.setDeltaMovement(Vec3.ZERO); p.fallDistance = 0;
        }
    }

    private static Region regionAt(MinecraftServer s, double x) {
        int slot = (int) Math.round(x / SPACING);
        for (Map.Entry<UUID, Region> e : data(s).regions.entrySet()) if (e.getValue().slot == slot) return e.getValue();
        return null;
    }

    // ================================================================== nothing in here is broken but by you

    private static boolean mayBuild(Entity e) { return e instanceof ServerPlayer p && chief(p); }

    @SubscribeEvent
    public static void breakBlock(net.neoforged.neoforge.event.level.BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof Level l && l.dimension() == DIM && !mayBuild(event.getPlayer())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void placeBlock(net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof Level l && l.dimension() == DIM && !mayBuild(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void explode(net.neoforged.neoforge.event.level.ExplosionEvent.Detonate event) {
        if (event.getLevel().dimension() == DIM) event.getAffectedBlocks().clear();
    }

    @SubscribeEvent
    public static void grief(net.neoforged.neoforge.event.entity.EntityMobGriefingEvent event) {
        if (event.getEntity() != null && event.getEntity().level().dimension() == DIM) event.setCanGrief(false);
    }

    /** The throne, the cell doors and the chests answer to you; to anyone else, nothing here opens. */
    @SubscribeEvent
    public static void use(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p.level().dimension() != DIM) return;
        BlockPos pos = event.getPos();
        BlockState st = p.level().getBlockState(pos);
        boolean chief = chief(p);
        Region r = regionAt(p.server, pos.getX());
        if (r != null && r.throneX != 0 && pos.equals(throneSeat(r))) {
            event.setCanceled(true);
            event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
            if (chief) sit(p, pos); else say(p, "That seat isn't yours.");
            return;
        }
        if (st.getBlock() instanceof net.minecraft.world.level.block.DoorBlock door && st.is(Blocks.IRON_DOOR)) {
            event.setCanceled(true);
            event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
            if (!chief) return;
            boolean opening = !st.getValue(net.minecraft.world.level.block.DoorBlock.OPEN);
            Cell tank = r == null ? null : tankWithDoor(r, pos);
            if (tank != null) {
                ServerLevel v = (ServerLevel) p.level();
                if (opening && open(v, tank.outerDoor())) { say(p, "Shut the outer door first. The airlock won't flood with it open."); return; }
                if (opening) lock(v, tank, true);
                door.setOpen(p, p.level(), st, pos, opening);
                if (!opening) lock(v, tank, false);
                p.swing(event.getHand(), true);
                return;
            }
            door.setOpen(p, p.level(), st, pos, !st.getValue(net.minecraft.world.level.block.DoorBlock.OPEN));
            p.swing(event.getHand(), true);
            return;
        }
        if (st.getBlock() instanceof ChestBlock && r != null) {
            event.setCanceled(true);
            event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
            if (!chief) { say(p, "The storeroom isn't yours."); return; }
            for (int k = 0; k < CHESTS; k++) if (chest(r.ox(), k, 0).equals(pos) || chest(r.ox(), k, 1).equals(pos)) {
                p.getPersistentData().putLong("bp_vault_open", p.level().getGameTime());
                p.getPersistentData().putInt("bp_vault_open_k", k);
                p.playNotifySound(SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.5F, 1F);
                view(p);
                return;
            }
        }
    }

    // ================================================================== the client's view (only while the Vault screen is open)

    public static void view(ServerPlayer p) { p.getPersistentData().putLong("bp_vault_view", p.level().getGameTime() + 20 * 60); region(p); Career.sync(p); }

    public static void addToSync(ServerPlayer p, CompoundTag t) {
        if (p.level().getGameTime() > p.getPersistentData().getLong("bp_vault_view")) return;
        Region r = data(p.server).regions.get(p.getUUID());
        ServerLevel v = level(p.server);
        if (r == null || v == null) return;
        CompoundTag out = new CompoundTag();
        ListTag cells = new ListTag();
        for (Cell c : r.cells) {
            CompoundTag x = new CompoundTag();
            Entity e = c.entity == null ? null : v.getEntity(c.entity);
            x.putString("n", c.name);
            x.putBoolean("in", e != null && e.isAlive());
            if (e instanceof LivingEntity le) x.putString("hp", (int) Math.ceil(le.getHealth()) + " / " + (int) Math.ceil(le.getMaxHealth()));
            x.put("look", c.look);
            x.putString("size", (c.x1 - c.x0 + 1) + " x " + (c.z1 - c.z0 + 1) + " x " + c.h);
            x.putBoolean("w", c.water);
            cells.add(x);
        }
        out.put("cells", cells);
        ListTag chests = new ListTag();
        for (int k = 0; k < CHESTS; k++) {
            ListTag items = new ListTag();
            for (int i = 0; i < 54; i++) {
                int idx = k * 54 + i;
                if (r.item[idx] == null || r.count[idx] <= 0) continue;
                CompoundTag it = new CompoundTag();
                it.putInt("s", i);
                it.put("i", r.item[idx].copyWithCount(1).save(p.registryAccess()));
                it.putInt("n", r.count[idx]);
                items.add(it);
            }
            chests.add(items);
        }
        out.put("chests", chests);
        out.putBoolean("inside", p.level().dimension() == DIM);
        CompoundTag pd = p.getPersistentData();
        if (pd.contains("bp_vault_open")) { out.putLong("openAt", pd.getLong("bp_vault_open")); out.putInt("openK", pd.getInt("bp_vault_open_k")); }
        out.putBoolean("seated", p.getVehicle() != null && p.getVehicle().getTags().contains("bp_throne"));
        t.put("vault", out);
    }

    private static void say(ServerPlayer p, String s) { p.displayClientMessage(Component.literal(s).withStyle(ChatFormatting.LIGHT_PURPLE), true); }

    /** Actions from the Vault screen: "vault:view|trap|go|put", "vault:out:i", "vault:back:i", "vault:take:chest:slot". */
    public static void action(ServerPlayer p, String a) {
        String[] s = a.split(":");
        try {
            switch (s[0]) {
                case "view" -> view(p);
                case "trap" -> trap(p);
                case "go" -> go(p);
                case "put" -> put(p);
                case "inv" -> putSlot(p, Integer.parseInt(s[1]), s.length > 2 && s[2].equals("all"));
                case "rest" -> putRest(p);
                case "rebuild" -> rebuild(p);
                case "throne" -> toThrone(p);
                case "out" -> release(p, Integer.parseInt(s[1]));
                case "back" -> restore(p, Integer.parseInt(s[1]));
                case "take" -> take(p, Integer.parseInt(s[1]), Integer.parseInt(s[2]), s.length > 3 && s[3].equals("all"));
                default -> {}
            }
        } catch (RuntimeException ignored) {}
    }
}
