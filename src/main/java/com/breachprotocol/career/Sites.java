package com.breachprotocol.career;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Foundation sites are places you declare. Standing where you're building, open the phone and declare
 * a site: everything within its radius counts as Foundation ground. An anomaly only counts as held in
 * a cell when the cell is inside a site; anywhere else it's loose. Saved with the world.
 */
public final class Sites extends SavedData {
    public static final String NAME = "breachprotocol_sites";
    public static final int[] RADII = {32, 48, 64, 96};

    public static final class Site {
        public String name = "", owner = "", ownerName = "", dim = "minecraft:overworld";
        public int x, y, z, radius = 48;

        boolean contains(Level l, BlockPos p) {
            if (!l.dimension().location().toString().equals(dim)) return false;
            long dx = p.getX() - x, dz = p.getZ() - z;
            return dx * dx + dz * dz <= (long) radius * radius;
        }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putString("n", name);
            t.putString("o", owner);
            t.putString("on", ownerName);
            t.putString("d", dim);
            t.putInt("x", x);
            t.putInt("y", y);
            t.putInt("z", z);
            t.putInt("r", radius);
            return t;
        }

        static Site load(CompoundTag t) {
            Site s = new Site();
            s.name = t.getString("n");
            s.owner = t.getString("o");
            s.ownerName = t.getString("on");
            s.dim = t.getString("d");
            s.x = t.getInt("x");
            s.y = t.getInt("y");
            s.z = t.getInt("z");
            s.radius = t.getInt("r");
            return s;
        }
    }

    public final List<Site> sites = new ArrayList<>();

    /** A containment chamber assigned to one SCP (entity id path), e.g. the SCP-173 chamber. */
    public static final class Cell {
        public String name = "", entity = "", dim = "minecraft:overworld";
        public int x0, y0, z0, x1, y1, z1;

        boolean holds(Level l, BlockPos p) {
            return l.dimension().location().toString().equals(dim) && p.getX() >= x0 && p.getX() <= x1 && p.getY() >= y0 && p.getY() <= y1 && p.getZ() >= z0 && p.getZ() <= z1;
        }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putString("n", name); t.putString("e", entity); t.putString("d", dim);
            t.putIntArray("b", new int[]{x0, y0, z0, x1, y1, z1});
            return t;
        }

        static Cell load(CompoundTag t) {
            Cell c = new Cell();
            c.name = t.getString("n"); c.entity = t.getString("e"); c.dim = t.getString("d");
            int[] b = t.getIntArray("b");
            if (b.length == 6) { c.x0 = b[0]; c.y0 = b[1]; c.z0 = b[2]; c.x1 = b[3]; c.y1 = b[4]; c.z1 = b[5]; }
            return c;
        }
    }

    public final List<Cell> cells = new ArrayList<>();

    public static void addCell(Level l, String name, String entity, BlockPos a, BlockPos b) {
        if (l.getServer() == null) return;
        Cell c = new Cell();
        c.name = name; c.entity = entity; c.dim = l.dimension().location().toString();
        c.x0 = Math.min(a.getX(), b.getX()); c.y0 = Math.min(a.getY(), b.getY()); c.z0 = Math.min(a.getZ(), b.getZ());
        c.x1 = Math.max(a.getX(), b.getX()); c.y1 = Math.max(a.getY(), b.getY()); c.z1 = Math.max(a.getZ(), b.getZ());
        Sites all = get(l.getServer());
        all.cells.add(c);
        all.setDirty();
    }

    /** The chamber this position is in, or null. */
    @Nullable
    public static Cell cellAt(Level l, BlockPos p) {
        if (l.getServer() == null) return null;
        for (Cell c : get(l.getServer()).cells) if (c.holds(l, p)) return c;
        return null;
    }

    public static Sites get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(Sites::new, Sites::load, null), NAME);
    }

    private static Sites load(CompoundTag t, HolderLookup.Provider provider) {
        Sites s = new Sites();
        for (Tag e : t.getList("sites", Tag.TAG_COMPOUND)) s.sites.add(Site.load((CompoundTag) e));
        for (Tag e : t.getList("cells", Tag.TAG_COMPOUND)) s.cells.add(Cell.load((CompoundTag) e));
        return s;
    }

    @Override
    public CompoundTag save(CompoundTag t, HolderLookup.Provider provider) {
        ListTag l = new ListTag();
        for (Site s : sites) l.add(s.save());
        t.put("sites", l);
        ListTag cl = new ListTag();
        for (Cell c : cells) cl.add(c.save());
        t.put("cells", cl);
        return t;
    }

    /** The site covering a position, or null when it's open ground. */
    @Nullable
    public static Site at(Level level, BlockPos pos) {
        if (level.getServer() == null) return null;
        for (Site s : get(level.getServer()).sites) if (s.contains(level, pos)) return s;
        return null;
    }

    /** Level 3 and up may declare sites. Site numbers follow the Foundation's style: Site-12, Site-19 ... */
    public static void declare(ServerPlayer p, int radiusIndex) {
        if (!Career.rank(p).atLeast(Rank.LEVEL_3) && !Career.isAscended(p)) {
            p.displayClientMessage(Component.literal("Only Level 3 staff and above can declare a site.").withStyle(ChatFormatting.RED), true);
            return;
        }
        int radius = RADII[Math.max(0, Math.min(RADII.length - 1, radiusIndex))];
        Sites all = get(p.server);
        for (Site s : all.sites) {
            if (!s.dim.equals(p.level().dimension().location().toString())) continue;
            long dx = p.getBlockX() - s.x, dz = p.getBlockZ() - s.z;
            if (dx * dx + dz * dz < (long) (radius + s.radius) * (radius + s.radius)) {
                p.displayClientMessage(Component.literal("Too close to " + s.name + ". Sites can't overlap.").withStyle(ChatFormatting.RED), true);
                return;
            }
        }
        Site s = new Site();
        int number = 10 + Math.floorMod(p.getBlockX() * 31 + p.getBlockZ() * 17 + all.sites.size() * 7, 90);
        s.name = "Site-" + number;
        s.owner = p.getUUID().toString();
        s.ownerName = p.getGameProfile().getName();
        s.dim = p.level().dimension().location().toString();
        s.x = p.getBlockX();
        s.y = p.getBlockY();
        s.z = p.getBlockZ();
        s.radius = radius;
        all.sites.add(s);
        all.setDirty();
        Career.message(p, O5.O5_6, s.name + " is on the books: " + radius + "m around " + s.x + ", " + s.z + ". Build your cells inside it. Anything held outside a site isn't contained, it's just stuck.");
    }

    /** Owners (and O5 and above) can decommission a site. */
    public static void remove(ServerPlayer p, int index) {
        Sites all = get(p.server);
        if (index < 0 || index >= all.sites.size()) return;
        Site s = all.sites.get(index);
        if (!s.owner.equals(p.getUUID().toString()) && !Career.rank(p).atLeast(Rank.OVERSEER)) {
            p.displayClientMessage(Component.literal("That isn't your site.").withStyle(ChatFormatting.RED), true);
            return;
        }
        all.sites.remove(index);
        all.setDirty();
        p.displayClientMessage(Component.literal(s.name + " decommissioned.").withStyle(ChatFormatting.GRAY), true);
    }

    public static void addToSync(ServerPlayer p, CompoundTag t) {
        Site here = at(p.level(), p.blockPosition());
        t.putString("siteHere", here == null ? "" : here.name);
        ListTag l = new ListTag();
        List<Site> all = get(p.server).sites;
        for (int i = 0; i < all.size(); i++) {
            Site s = all.get(i);
            CompoundTag st = new CompoundTag();
            st.putInt("i", i);
            st.putString("n", s.name);
            st.putString("on", s.ownerName);
            st.putBoolean("mine", s.owner.equals(p.getUUID().toString()));
            st.putBoolean("here", s == here);
            st.putBoolean("dim", s.dim.equals(p.level().dimension().location().toString()));
            st.putInt("x", s.x);
            st.putInt("z", s.z);
            st.putInt("r", s.radius);
            l.add(st);
        }
        t.put("sites", l);
    }
}
