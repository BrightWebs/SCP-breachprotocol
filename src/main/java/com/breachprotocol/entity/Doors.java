package com.breachprotocol.entity;

import com.breachprotocol.BreachProtocol;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.lang.reflect.Method;
import java.util.*;

/**
 * Staff and SCP Unleashed's sliding doors. A door is keyed if a keycard scanner sits on its jamb
 * (its level is the door's level); otherwise anyone may open it. People plan their routes through the
 * doors their clearance opens, open them as they reach them, and the doors close behind them.
 */
@EventBusSubscriber(modid = BreachProtocol.MODID)
public final class Doors {
    private Doors() {}

    private static Class<?> panel;
    private static boolean looked;

    private static Class<?> panel() {
        if (!looked) {
            looked = true;
            try { panel = Class.forName("net.rai.scp_unleashed.door.PanelDoorBlock"); } catch (Throwable t) { panel = null; }
        }
        return panel;
    }

    /** One of SCP Unleashed's panel doors (sliding, room, single...). */
    public static boolean isDoor(BlockState st) {
        Class<?> c = panel();
        return c != null && c.isInstance(st.getBlock());
    }

    public static boolean isOpen(BlockState st) {
        for (Property<?> p : st.getProperties()) if (p.getName().equals("open") && st.getValue(p) instanceof Boolean b) return b;
        return false;
    }

    private static final Map<Long, int[]> LEVEL = new HashMap<>(); // pos -> {level, expires (tick)}

    /** The clearance a door asks for: the level of a keycard scanner on its frame, or 0. */
    public static int level(BlockGetter l, BlockPos pos, long now) {
        int[] c = LEVEL.get(pos.asLong());
        if (c != null && c[1] > now) return c[0];
        int lvl = 0;
        for (BlockPos q : BlockPos.betweenClosed(pos.offset(-2, -1, -2), pos.offset(2, 2, 2))) {
            BlockState s = l.getBlockState(q);
            if (!BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath().equals("keycard_scanner")) continue;
            for (Property<?> p : s.getProperties()) if (p.getName().equals("level") && s.getValue(p) instanceof Integer i) lvl = Math.max(lvl, i);
        }
        LEVEL.put(pos.asLong(), new int[]{lvl, (int) now + 2400});
        if (LEVEL.size() > 8192) LEVEL.clear();
        return lvl;
    }

    /** May this person go through this door? */
    public static boolean may(Mob m, BlockGetter l, BlockPos pos) {
        // a containment cell's door is nobody's business without an order to go in (that's how breaches start)
        if (!cellAllowed(m) && inCell(m, pos, 1)) return false;
        return clearance(m) >= level(l, pos, m.level().getGameTime());
    }

    /** Staff with business inside a cell (a response team, a cleaning crew): allowed through its door. */
    public static boolean cellAllowed(Mob m) { return m.getPersistentData().getBoolean("bp_cell_ok"); }

    /** Inside a registered containment cell (or within {@code margin} blocks of one: its door, its walls). */
    public static boolean inCell(Mob m, BlockPos p, int margin) {
        var srv = m.level().getServer();
        if (srv == null) return false;
        String dim = m.level().dimension().location().toString();
        for (com.breachprotocol.career.Sites.Cell c : com.breachprotocol.career.Sites.get(srv).cells)
            if (c.dim.equals(dim) && p.getX() >= c.x0 - margin && p.getX() <= c.x1 + margin && p.getY() >= c.y0 - margin && p.getY() <= c.y1 + margin
                    && p.getZ() >= c.z0 - margin && p.getZ() <= c.z1 + margin) return true;
        return false;
    }

    /** A person's clearance (staff by role and standing; MCA villagers on the payroll by their job). */
    public static int clearance(Mob m) {
        if (m instanceof FoundationStaff f) return f.clearance();
        if (McaFoundation.employee(m)) return switch (McaFoundation.role(m)) {
            case JANITOR -> 2; case CLERK, TECHNICIAN -> 2; case RESEARCHER, MEDIC, GUARD -> 3; case SECRETARY -> 5;
        };
        return 0;
    }

    // ================================================================== opening and closing

    private static Method originOf, masterEntity, hold;

    private static Object master(Level l, BlockPos pos, BlockState st) {
        try {
            Block b = st.getBlock();
            if (originOf == null) for (Method m : panel().getMethods()) {
                if (m.getName().equals("originOf") && m.getParameterCount() == 2) originOf = m;
                if (m.getName().equals("masterEntity") && m.getParameterCount() == 3) masterEntity = m;
            }
            return masterEntity.invoke(b, l, pos, st);
        } catch (Throwable t) { return null; }
    }

    private static void hold(Object be, boolean on) {
        try {
            if (hold == null) hold = be.getClass().getMethod("hold", boolean.class);
            hold.invoke(be, on);
        } catch (Throwable ignored) {}
    }

    private record Held(ServerLevel level, BlockPos pos, long since) {}

    private static final Map<Long, Held> HELD = new HashMap<>();

    /** Opens a door for someone walking through it (and remembers to close it after them). */
    public static void open(ServerLevel l, BlockPos pos) {
        BlockState st = l.getBlockState(pos);
        if (!isDoor(st) || isOpen(st) && !HELD.containsKey(pos.asLong())) return;
        Object be = master(l, pos, st);
        if (be == null) return;
        hold(be, true);
        HELD.put(pos.asLong(), new Held(l, pos.immutable(), l.getGameTime()));
    }

    /** Doors along the next stretch of a path, opened as they come within reach. */
    public static void along(Mob m) {
        if (!(m.level() instanceof ServerLevel l)) return;
        Path path = m.getNavigation().getPath();
        if (path == null || path.isDone()) {
            nearby(m, l);
            return;
        }
        for (int i = path.getNextNodeIndex(); i < Math.min(path.getNodeCount(), path.getNextNodeIndex() + 3); i++) {
            Node n = path.getNode(i);
            BlockPos p = new BlockPos(n.x, n.y, n.z);
            if (m.blockPosition().distSqr(p) > 9) continue;
            for (BlockPos q : new BlockPos[]{p, p.above()}) if (isDoor(l.getBlockState(q)) && may(m, l, q)) open(l, q);
        }
    }

    /** For people whose own pathfinding can't see through our doors (MCA villagers): open a door they're standing at. */
    public static void nearby(Mob m, ServerLevel l) {
        for (BlockPos q : BlockPos.betweenClosed(m.blockPosition().offset(-1, 0, -1), m.blockPosition().offset(1, 1, 1)))
            if (isDoor(l.getBlockState(q)) && may(m, l, q)) open(l, q.immutable());
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        if (HELD.isEmpty() || event.getServer().getTickCount() % 10 != 0) return;
        for (Iterator<Held> it = HELD.values().iterator(); it.hasNext(); ) {
            Held h = it.next();
            long age = h.level().getGameTime() - h.since();
            if (age < 50) continue;
            // nobody in the doorway: let it close
            boolean busy = !h.level().getEntitiesOfClass(Mob.class, new net.minecraft.world.phys.AABB(h.pos()).inflate(1.6)).isEmpty();
            if (busy && age < 400) continue;
            BlockState st = h.level().getBlockState(h.pos());
            if (isDoor(st)) { Object be = master(h.level(), h.pos(), st); if (be != null) hold(be, false); }
            it.remove();
        }
    }

    /** For direction-sensitive callers. */
    static Direction facing(BlockState st) {
        for (Property<?> p : st.getProperties()) if (p.getName().equals("facing") && st.getValue(p) instanceof Direction d) return d;
        return Direction.NORTH;
    }
}
