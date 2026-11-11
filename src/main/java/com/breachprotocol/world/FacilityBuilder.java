package com.breachprotocol.world;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.career.Sites;
import com.breachprotocol.compat.Unleashed;
import com.breachprotocol.compat.UnleashedApi;
import com.breachprotocol.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared toolkit for building Foundation facilities out of SCP Unleashed's own blocks: zone palettes
 * (floor, wall bottom / middle / top, ceiling, window), rooms, corridors, their sliding doors with
 * keycard scanners and exit buttons, furniture rows, tables, signs, chests and creative power.
 * Used by the Site Tower and Site-01.
 */
public abstract class FacilityBuilder {
    protected record CellSpec(String entity, String name, String objectClass, String line1, String line2, int clearance, String extra) {}

    /** A zone's block set: floor, marked floor, wall bottom / middle / top, ceiling, window. */
    protected record Pal(String floor, String marked, String bottom, String middle, String top, String ceiling, String window) {
        static Pal of(String set) {
            return new Pal(set + "_floor", set + "_floor_marked", set + "_wall_bottom", set + "_wall_middle", set + "_wall_top", set + "_ceiling", set + "_wall_window");
        }

        Pal floor(String f) { return new Pal(f, marked, bottom, middle, top, ceiling, window); }
    }

    protected static final Pal EZ = Pal.of("ez"), LCZP = Pal.of("lcz"), HCZP = Pal.of("hcz"), OFFICE = Pal.of("office"), RESEARCH = Pal.of("research"),
            MEDICAL = Pal.of("medical"), POWER = Pal.of("power"), GREEN = Pal.of("greenhouse"), CELL = Pal.of("containment_room");

    protected static final Direction N = Direction.NORTH, S = Direction.SOUTH, E = Direction.EAST, W = Direction.WEST;

    // ================================================================== building context

    protected final ServerLevel l;
    protected final BlockPos o;
    protected final Map<String, BlockState> cache = new HashMap<>();
    protected static final int FLAGS = Block.UPDATE_CLIENTS;

    protected FacilityBuilder(ServerLevel l, BlockPos o) {
        this.l = l;
        this.o = o;
    }


    // ------------------------------------------------------------------ block states

    /** "id[prop=value,...]": SCP Unleashed ids by default, "minecraft:..." for vanilla. Unknown properties are ignored. */
    protected BlockState st(String spec) {
        return cache.computeIfAbsent(spec, k -> {
            String id = k, props = "";
            int b = k.indexOf('[');
            if (b >= 0) { id = k.substring(0, b); props = k.substring(b + 1, k.length() - 1); }
            Block blk;
            if (id.contains(":")) blk = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(id)).orElse(Blocks.AIR);
            else blk = Unleashed.block(id, Blocks.AIR);
            if (blk == Blocks.AIR && !id.endsWith("air")) blk = fallback(id);
            BlockState s = blk.defaultBlockState();
            if (!props.isEmpty()) for (String kv : props.split(",")) {
                String[] p = kv.split("=");
                if (p.length != 2) continue;
                Property<?> pr = blk.getStateDefinition().getProperty(p[0]);
                if (pr != null) s = with(s, pr, p[1]);
            }
            return s;
        });
    }

    protected static <T extends Comparable<T>> BlockState with(BlockState s, Property<T> p, String v) {
        return p.getValue(v).map(x -> s.setValue(p, x)).orElse(s);
    }

    protected static Block fallback(String id) {
        if (id.contains("glass")) return Blocks.GLASS;
        if (id.contains("stairs")) return Blocks.SMOOTH_QUARTZ_STAIRS;
        if (id.contains("ceiling")) return Blocks.SMOOTH_QUARTZ;
        if (id.contains("floor") || id.contains("tile") || id.contains("carpet") || id.contains("plate")) return Blocks.LIGHT_GRAY_CONCRETE;
        if (id.contains("wall") || id.contains("concrete") || id.contains("brick") || id.contains("block")) return Blocks.WHITE_CONCRETE;
        if (id.contains("light") || id.contains("lamp")) return Blocks.SEA_LANTERN;
        return Blocks.AIR;
    }

    protected static String f(Direction d) { return d.getSerializedName(); }

    // ------------------------------------------------------------------ primitives

    protected BlockPos at(int x, int y, int z) { return o.offset(x, y, z); }

    protected void set(int x, int y, int z, String spec) { place(at(x, y, z), st(spec)); }

    protected void set(int x, int y, int z, BlockState s) { place(at(x, y, z), s); }

    /**
     * Patch mode: the whole plan is walked, but only what's missing is put back. A spot that should hold a
     * block and is empty gets it; anything that's there (the plan's or someone else's) is left alone; nothing
     * is cleared; and it's placed quietly, so nothing beside it pops off.
     */
    protected boolean patch;
    protected int patched;

    /** True if it was placed (always, except when patching around something already there). */
    protected boolean place(BlockPos p, BlockState s) {
        if (patch) {
            if (s.isAir() || !l.getBlockState(p).isAir()) return false;
            l.setBlock(p, s, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS);
            patched++;
            return true;
        }
        l.setBlock(p, s, FLAGS);
        return true;
    }

    /** Placed the way a player would (multi-part furniture), unless patching and something's already there. */
    protected boolean placeLikePlayer(BlockPos p, Block blk, Direction facing) {
        if (patch && !l.getBlockState(p).isAir()) return true;
        boolean ok = Placer.place(l, p, blk, facing);
        if (ok && patch) patched++;
        return ok;
    }

    protected void fill(int x1, int y1, int z1, int x2, int y2, int z2, String spec) {
        BlockState s = st(spec);
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++)
                    place(at(x, y, z), s);
    }

    protected void setIfAir(int x, int y, int z, String spec) { if (l.getBlockState(at(x, y, z)).isAir()) set(x, y, z, spec); }

    protected void air(int x1, int y1, int z1, int x2, int y2, int z2) { fill(x1, y1, z1, x2, y2, z2, "minecraft:air"); }

    /** Machines and two-part props: placed like a player places them so every part is built. */
    protected void put(int x, int y, int z, String id, Block fallback, Direction facing) {
        Block blk = Unleashed.block(id, fallback);
        if (!placeLikePlayer(at(x, y, z), blk, facing) && fallback != Blocks.AIR) placeLikePlayer(at(x, y, z), fallback, facing);
    }

    /** Two-tall props (server racks, lockers, screens, coat racks): half=lower / upper. */
    protected void tall(int x, int y, int z, String id, Direction facing) {
        set(x, y, z, id + "[facing=" + f(facing) + ",half=lower]");
        set(x, y + 1, z, id + "[facing=" + f(facing) + ",half=upper]");
    }

    /** Stacked props with part=bottom/middle/top (or single). */
    protected void stack(int x, int y, int z, String id, Direction facing, int n) {
        for (int i = 0; i < n; i++) {
            String part = n == 1 ? "single" : i == 0 ? "bottom" : i == n - 1 ? "top" : "middle";
            set(x, y + i, z, id + "[facing=" + f(facing) + ",part=" + part + "]");
        }
    }

    /** Stacked props made of separate _bottom / _middle / _top blocks. */
    protected void stackBlocks(int x, int y, int z, String base, Direction facing, int n) {
        for (int i = 0; i < n; i++) {
            String part = i == 0 ? "_bottom" : i == n - 1 ? "_top" : "_middle";
            set(x, y + i, z, base + part + "[facing=" + f(facing) + "]");
        }
    }

    /** A fluorescent fitting hung from the ceiling. */
    protected void light(int x, int y, int z, boolean alongX) {
        set(x, y, z, "fluorescent_light[face=ceiling,facing=" + (alongX ? "east" : "north") + ",lit=true]");
    }

    /** A 2-wide facility sign on a wall (or hung from the ceiling): the left half at x,z, the right half to its right. */
    protected void sign2(String id, int x, int y, int z, Direction facing, String arrow, boolean hung) {
        BlockPos p = at(x, y, z), q = p.relative(facing.getCounterClockWise());
        String props = "facing=" + f(facing) + ",mount=" + (hung ? "ceiling" : "wall") + ",arrow=" + arrow;
        place(p, st(id + "[" + props + ",part=left]"));
        place(q, st(id + "[" + props + ",part=right]"));
    }

    /** SCP Unleashed's 2-wide SCP Foundation wall sign. */
    protected void scpSign(int x, int y, int z, Direction facing) {
        BlockPos p = at(x, y, z);
        place(p, st("scp_sign[facing=" + f(facing) + ",part=left]"));
        place(p.relative(facing.getCounterClockWise()), st("scp_sign[facing=" + f(facing) + ",part=right]"));
    }

    /** A plain name plate on a wall (white glowing text on dark wood). */
    protected void plate(int x, int y, int z, Direction facing, String... lines) {
        BlockPos p = at(x, y, z);
        if (!place(p, Blocks.DARK_OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, facing))) return; // there already: its words stay
        if (l.getBlockEntity(p) instanceof SignBlockEntity sb) {
            SignText t = new SignText();
            for (int i = 0; i < lines.length && i < 4; i++) t = t.setMessage(i, Component.literal(lines[i]));
            sb.setText(t.setHasGlowingText(true).setColor(DyeColor.WHITE), true);
            sb.setChanged();
        }
    }

    protected static ItemStack ui(String id, ItemLike fallback, int n) { return new ItemStack(Unleashed.item(id, fallback.asItem()), n); }

    protected void chest(int x, int y, int z, Direction facing, ItemStack... items) {
        BlockPos p = at(x, y, z);
        if (!place(p, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing))) return; // there already: never restocked
        if (l.getBlockEntity(p) instanceof ChestBlockEntity c) for (int i = 0; i < items.length && i < 27; i++) c.setItem(i, items[i]);
    }

    /** A Creative Outlet wired to machines with SCP Unleashed's creative-tier wire. */
    protected void power(int x, int y, int z, int[]... machines) {
        if (!place(at(x, y, z), st("creative_outlet")) && patch) return; // wired already
        for (int[] m : machines) UnleashedApi.wire(l, at(x, y, z), at(m[0], m[1], m[2]));
    }

    // ------------------------------------------------------------------ rooms

    /**
     * A room whose walls are the ring x0..x1 / z0..z1 (inclusive), with floor at Y, four blocks of
     * clear height and the ceiling at Y+5. Walls are the zone's bottom / middle / middle / top pieces.
     */
    protected void room(Pal p, int x0, int z0, int x1, int z1, int Y, boolean lights) { room(p, x0, z0, x1, z1, Y, 4, lights); }

    /** A room with {@code h} blocks of clear height (ceiling at Y+h+1). */
    protected void room(Pal p, int x0, int z0, int x1, int z1, int Y, int h, boolean lights) {
        rooms.add(new RoomRec(p, x0, z0, x1, z1, Y, h));
        fill(x0, Y, z0, x1, Y, z1, p.floor());
        fill(x0, Y + h + 1, z0, x1, Y + h + 1, z1, p.ceiling());
        air(x0 + 1, Y + 1, z0 + 1, x1 - 1, Y + h, z1 - 1);
        for (int x = x0; x <= x1; x++) { column(p, x, z0, Y, h); column(p, x, z1, Y, h); }
        for (int z = z0; z <= z1; z++) { column(p, x0, z, Y, h); column(p, x1, z, Y, h); }
        if (lights) lights(x0, z0, x1, z1, Y + h - 4);
    }

    private record RoomRec(Pal p, int x0, int z0, int x1, int z1, int Y, int h) {}

    private final java.util.List<RoomRec> rooms = new java.util.ArrayList<>();
    private final java.util.Set<BlockPos> openings = new java.util.HashSet<>();

    /** An intentional gap in a wall (an archway, a turnstile's headroom). */
    protected void opening(int x, int y, int z) {
        openings.add(at(x, y, z));
        set(x, y, z, "minecraft:air");
    }

    /**
     * The last pass: every room's walls must be solid. Anything that ended up standing in a wall line
     * (a prop or an accidental gap) is replaced with the wall, so nothing can be seen through. Doors,
     * windows, glass, turnstiles and intentional openings stay.
     */
    protected void seal() {
        for (RoomRec r : rooms) {
            for (int x = r.x0(); x <= r.x1(); x++) for (int z = r.z0(); z <= r.z1(); z++) {
                if (x != r.x0() && x != r.x1() && z != r.z0() && z != r.z1()) continue;
                for (int yy = r.Y() + 1; yy <= r.Y() + r.h(); yy++) {
                    BlockPos p = at(x, yy, z);
                    if (openings.contains(p)) continue;
                    BlockState st = l.getBlockState(p);
                    if (structural(st, p)) continue;
                    int lv = yy - r.Y();
                    set(x, yy, z, lv == 1 ? r.p().bottom() : lv == r.h() ? r.p().top() : r.p().middle());
                }
            }
        }
    }

    private boolean structural(BlockState st, BlockPos p) {
        if (st.isAir()) return false;
        if (st.isSolidRender(l, p)) return true;
        String path = BuiltInRegistries.BLOCK.getKey(st.getBlock()).getPath();
        return path.contains("door") || path.contains("window") || path.contains("glass") || path.contains("turnstile") || path.contains("gate")
                || path.contains("pane") || path.contains("stairs") || path.contains("slab");
    }

    protected void column(Pal p, int x, int z, int Y, int h) {
        set(x, Y + 1, z, p.bottom());
        for (int yy = Y + 2; yy < Y + h; yy++) set(x, yy, z, p.middle());
        set(x, Y + h, z, p.top());
    }

    /** Fluorescent fittings every 4 blocks, kept off the walls. */
    protected void lights(int x0, int z0, int x1, int z1, int Y) {
        boolean alongX = x1 - x0 >= z1 - z0;
        int w = x1 - x0 - 1, d = z1 - z0 - 1;
        int sx = x0 + 1 + (w % 4 == 0 ? 2 : (w % 4) / 2 + 1), sz = z0 + 1 + (d % 4 == 0 ? 2 : (d % 4) / 2 + 1);
        if (w <= 4) sx = x0 + (x1 - x0) / 2;
        if (d <= 4) sz = z0 + (z1 - z0) / 2;
        for (int x = sx; x < x1 - 1 || x == sx; x += 4)
            for (int z = sz; z < z1 - 1 || z == sz; z += 4)
                light(x, Y + 4, z, alongX);
    }

    /** Windows into a room wall (zone window blocks). */
    protected void windows(Pal p, int x0, int z0, int x1, int z1, int Y) {
        String axis = z0 == z1 ? "x" : "z";
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++)
            for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++)
                for (int yy = Y + 2; yy <= Y + 3; yy++) set(x, yy, z, p.window() + "[axis=" + axis + "]");
    }

    /** A corridor between walls z=8 and z=14, edged with marked floor and lit down the middle. */
    protected void corridor(Pal p, int x0, int x1, int Y) {
        room(p, x0, 8, x1, 14, Y, false);
        for (int x = x0 + 1; x < x1; x++) {
            set(x, Y, 9, p.marked() + "[facing=north]");
            set(x, Y, 13, p.marked() + "[facing=south]");
        }
        for (int x = x0 + 3; x < x1 - 1; x += 5) light(x, Y + 4, 11, true);
    }

    // ------------------------------------------------------------------ doors

    /**
     * An SCP Unleashed sliding door w wide and h tall standing in a wall, its lowest cells at Y+1.
     * {@code a, b} is the door's min corner on the wall (x for walls along x, z for walls along z);
     * {@code out} is the side it faces. Returns the jamb to the door's right (seen from outside),
     * which is where scanners and buttons go.
     */
    protected BlockPos door(String kind, int w, int h, int x, int z, int Y, Direction out) {
        Direction right = out.getCounterClockWise();
        int ox = x, oz = z;
        if (right.getStepX() < 0) ox = x + w - 1;
        if (right.getStepZ() < 0) oz = z + w - 1;
        BlockPos origin = at(ox, Y + 1, oz);
        Block blk = Unleashed.block(kind, Blocks.AIR);
        if (blk == Blocks.AIR) {
            // no such door: leave an open doorway
            for (int c = 0; c < w; c++) for (int r = 0; r < h; r++) {
                BlockPos q = origin.relative(right, c).above(r);
                place(q, Blocks.AIR.defaultBlockState());
                openings.add(q);
            }
            return origin.relative(right, w);
        }
        if (patch && !l.getBlockState(origin).isAir()) return origin.relative(right, w); // the door's there: left as it is (open or shut)
        for (int c = 0; c < w; c++)
            for (int r = 0; r < h; r++)
                place(origin.relative(right, c).above(r), st(kind + "[facing=" + f(out) + ",open=false,part=" + (c + r * w) + "]"));
        try {
            BlockState s = l.getBlockState(origin);
            blk.setPlacedBy(l, origin, s, null, new ItemStack(blk.asItem()));
        } catch (Throwable t) {
            BreachProtocol.LOGGER.debug("Door {} at {}: {}", kind, origin, t.toString());
        }
        return origin.relative(right, w);
    }

    /** A hand-opened Entrance Zone door (ez_room_door, 2x2). */
    protected void ezDoor(int x, int z, int Y, Direction out) { door("ez_room_door", 2, 2, x, z, Y, out); }

    /** A secured door: keycard scanner outside, exit button inside, both on the jamb (which powers the door). */
    protected void gate(String kind, int w, int h, int x, int z, int Y, Direction out, int level) {
        BlockPos jamb = door(kind, w, h, x, z, Y, out).above();
        place(jamb.relative(out), st("keycard_scanner[facing=" + f(out) + ",level=" + Math.max(1, Math.min(5, level)) + ",status=idle]"));
        place(jamb.relative(out.getOpposite()), st("door_button[facing=" + f(out.getOpposite()) + ",powered=false]"));
    }

    /** A powered door with a door button on each side (no keycard). */
    protected void buttoned(String kind, int w, int h, int x, int z, int Y, Direction out) {
        BlockPos jamb = door(kind, w, h, x, z, Y, out).above();
        place(jamb.relative(out), st("door_button[facing=" + f(out) + ",powered=false]"));
        place(jamb.relative(out.getOpposite()), st("door_button[facing=" + f(out.getOpposite()) + ",powered=false]"));
    }

    // ------------------------------------------------------------------ furniture

    /** A row of desks along x at z (facing = the side the person sits), chairs behind, a monitor on alternate desks. */
    protected void desksX(String desk, int x0, int x1, int z, int Y, Direction facing, String top) {
        for (int x = x0; x <= x1; x++) {
            set(x, Y + 1, z, desk + "[facing=" + f(facing) + "," + joins(facing, W, x > x0, x < x1) + "]");
            if (top != null && (x - x0) % 2 == 0) set(x, Y + 2, z, top.replace("$", f(facing)));
            else if ((x - x0) % 4 == 1) set(x, Y + 2, z, "paper_stack[facing=" + f(facing) + ",variant=" + Math.floorMod(x * 7 + z, 6) + "]");
            if ((x - x0) % 2 == 0 || x0 == x1) set(x, Y + 1, z + facing.getStepZ(), "office_chair[facing=" + f(facing.getOpposite()) + "]");
        }
    }

    /** The same, along z. */
    protected void desksZ(String desk, int x, int z0, int z1, int Y, Direction facing, String top) {
        for (int z = z0; z <= z1; z++) {
            set(x, Y + 1, z, desk + "[facing=" + f(facing) + "," + joins(facing, N, z > z0, z < z1) + "]");
            if (top != null && (z - z0) % 2 == 0) set(x, Y + 2, z, top.replace("$", f(facing)));
            if ((z - z0) % 2 == 0 || z0 == z1) set(x + facing.getStepX(), Y + 1, z, "office_chair[facing=" + f(facing.getOpposite()) + "]");
        }
    }

    /**
     * The left / right connection flags of a run of desks, counters or couches: SCP Unleashed's
     * "left" is the side clockwise of the facing (a north-facing desk's left joins the block to its east).
     * {@code back} is the run's lower end (west for a run along x, north along z).
     */
    protected static String joins(Direction facing, Direction back, boolean hasBack, boolean hasFront) {
        boolean cwIsBack = facing.getClockWise() == back;
        return "left=" + (cwIsBack ? hasBack : hasFront) + ",right=" + (cwIsBack ? hasFront : hasBack);
    }

    /**
     * A reception counter along x at z. Its tall front panel faces {@code front}, towards the people
     * coming in; the clerks sit behind it facing them, with their terminals turned towards the clerk.
     * {@code seats} are the x positions of the clerks.
     */
    protected void counterX(int x0, int x1, int z, int Y, Direction front, int screen, int... seats) {
        for (int x = x0; x <= x1; x++)
            set(x, Y + 1, z, "reception_desk[facing=" + f(front) + "," + joins(front, W, x > x0, x < x1) + ",shape=straight]");
        Direction clerk = front.getOpposite();
        for (int x : seats) {
            set(x, Y + 2, z, "computer_terminal[facing=" + f(clerk) + ",lit=true,screen=" + screen + "]");
            set(x, Y + 1, z + clerk.getStepZ(), "office_chair[facing=" + f(front) + "]");
        }
    }

    /** The same, along z at x. */
    protected void counterZ(int x, int z0, int z1, int Y, Direction front, int screen, int... seats) {
        for (int z = z0; z <= z1; z++)
            set(x, Y + 1, z, "reception_desk[facing=" + f(front) + "," + joins(front, N, z > z0, z < z1) + ",shape=straight]");
        Direction clerk = front.getOpposite();
        for (int z : seats) {
            set(x, Y + 2, z, "computer_terminal[facing=" + f(clerk) + ",lit=true,screen=" + screen + "]");
            set(x + clerk.getStepX(), Y + 1, z, "office_chair[facing=" + f(front) + "]");
        }
    }

    /** A joined table with chairs only along its two long sides (none at the ends), for narrow rooms. */
    protected void sideTable(String table, String chair, int x0, int z0, int x1, int z1, int Y, int chairStep) {
        table(table, null, x0, z0, x1, z1, Y, chairStep);
        if (x1 - x0 >= z1 - z0) {
            for (int x = x0; x <= x1; x += chairStep) { set(x, Y + 1, z0 - 1, chair + "[facing=south]"); set(x, Y + 1, z1 + 1, chair + "[facing=north]"); }
        } else {
            for (int z = z0; z <= z1; z += chairStep) { set(x0 - 1, Y + 1, z, chair + "[facing=east]"); set(x1 + 1, Y + 1, z, chair + "[facing=west]"); }
        }
    }

    /** A joined table (conference / cafeteria) over x0..x1, z0..z1, with chairs around it facing in. */
    protected void table(String table, String chair, int x0, int z0, int x1, int z1, int Y, int chairStep) {
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++)
                set(x, Y + 1, z, table + "[north=" + (z > z0) + ",south=" + (z < z1) + ",west=" + (x > x0) + ",east=" + (x < x1) + "]");
        if (chair == null) return;
        for (int x = x0; x <= x1; x += chairStep) {
            set(x, Y + 1, z0 - 1, chair + "[facing=south]");
            set(x, Y + 1, z1 + 1, chair + "[facing=north]");
        }
        for (int z = z0; z <= z1; z += chairStep) {
            set(x0 - 1, Y + 1, z, chair + "[facing=east]");
            set(x1 + 1, Y + 1, z, chair + "[facing=west]");
        }
    }

    /** A couch along x or z, facing the room. */
    protected void couch(String id, int x, int z, int Y, int n, boolean alongX, Direction facing) {
        for (int i = 0; i < n; i++) {
            int xx = alongX ? x + i : x, zz = alongX ? z : z + i;
            set(xx, Y + 1, zz, id + "[facing=" + f(facing) + "," + joins(facing, alongX ? W : N, i > 0, i < n - 1) + ",shape=straight]");
        }
    }

    /**
     * A containment cell assigned to one SCP. Walls are the ring x0..x1 / z0..z1; the door is in the
     * wall facing the corridor (out = N for south cells, S for north cells). Keycard scanner outside at
     * the SCP's clearance, exit button inside, observation windows where the lore allows.
     */
    protected void cell(CellSpec c, int x0, int z0, int x1, int z1, int Y, Direction out) {
        Pal p = c.extra().equals("heavy") ? new Pal("containment_room_floor", "containment_room_floor_marked", "reinforced_containment_wall", "reinforced_containment_wall",
                "reinforced_containment_wall", "containment_room_ceiling", "containment_room_wall_window") : CELL;
        if (c.extra().equals("room")) p = p.floor("office_floor");
        if (c.extra().equals("bio")) p = p.floor("containment_room_floor_grate");
        room(p, x0, z0, x1, z1, Y, !c.extra().equals("dark"));
        int wallZ = out == N ? z0 : z1, inZ = out == N ? z1 - 1 : z0 + 1, cx = x0 + (x1 - x0 - 1) / 2;
        // door: 2 wide in the middle of the corridor wall; the scanner is on the jamb to its right
        gate("containment_room_door", 2, 2, cx, wallZ, Y, out, c.clearance());
        int outZ = wallZ + out.getStepZ(), side = out == N ? cx + 2 : cx - 1;
        // a hazard line just inside the door
        for (int x = x0 + 1; x < x1; x++) set(x, Y, wallZ - out.getStepZ(), "containment_room_floor_marked[facing=" + f(out) + "]");
        // windows either side (none for SCP-096 and SCP-939)
        if (!c.extra().equals("dark")) {
            windows(p, x0 + 1, wallZ, x0 + 1, wallZ, Y);
            windows(p, x1 - 1, wallZ, x1 - 1, wallZ, Y);
        }
        // hazard sign over the door; name plate and alarm light on the other side from the scanner
        int hz = out == N ? cx + 1 : cx;
        place(at(hz, Y + 4, outZ), st("containment_room_hazard_sign[facing=" + f(out) + ",half=left,variant=0]"));
        place(at(hz, Y + 4, outZ).relative(out.getCounterClockWise()), st("containment_room_hazard_sign[facing=" + f(out) + ",half=right,variant=0]"));
        plate(side, Y + 2, outZ, out, c.name(), c.objectClass().toUpperCase(), c.line1(), c.line2());
        set(side, Y + 3, outZ, "containment_room_alarm_light[facing=" + f(out) + ",powered=false]");
        if (c.extra().equals("fire")) set(x0 + 1, Y + 1, outZ, "fire_extinguisher[facing=" + f(out) + ",wall=true]");
        // inside
        set(cx, Y, (z0 + z1) / 2, "containment_floor_drain");
        set(cx, Y + 4, inZ, "containment_room_sedation_vent[facing=" + f(out) + "]");
        switch (c.extra()) {
            case "soft" -> { for (int x = x0 + 1; x < x1; x++) stack(x, Y + 1, inZ, "containment_room_padded_panel", out, 3); }   // below the sedation vent
            case "room" -> {
                put(x0 + 1, Y + 1, inZ + out.getStepZ() * 2, "lcz_cell_bunk", Blocks.WHITE_BED, E);
                set(x1 - 1, Y + 1, inZ, "office_desk[facing=" + f(out) + ",left=false,right=false]");
                set(x1 - 1, Y + 2, inZ, "desk_lamp[facing=" + f(out) + ",lit=true]");
                set(x1 - 1, Y + 1, inZ + out.getStepZ(), "office_chair[facing=" + f(out.getOpposite()) + "]");
                set(x1 - 1, Y + 1, inZ + out.getStepZ() * 4, "binder_shelf[facing=west]");
                set(x0 + 1, Y + 1, inZ + out.getStepZ() * 5, "potted_plant[facing=east]");
            }
            case "bio" -> { set(x0 + 1, Y + 1, inZ, "biohazard_bin[facing=" + f(out) + "]"); set(x1 - 1, Y + 1, inZ, "biohazard_bin[facing=" + f(out) + "]"); }
            case "field" -> {
                int fz = inZ + out.getStepZ() * 2;
                List<int[]> em = new ArrayList<>();
                for (int x = x0 + 1; x < x1; x += 2) {
                    set(x, Y, fz, "forcefield_emitter");
                    set(x, Y + 5, fz, "forcefield_emitter");
                    em.add(new int[]{x, Y, fz});
                    em.add(new int[]{x, Y + 5, fz});
                }
                power(x1 - 1, Y + 1, wallZ - out.getStepZ(), em.toArray(new int[0][]));
            }
            default -> {}
        }
        if (!c.entity().isEmpty()) Sites.addCell(l, c.name() + " chamber", c.entity(), at(x0, Y, z0), at(x1, Y + 5, z1));
    }

}
