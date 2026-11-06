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

    public static Sites get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(Sites::new, Sites::load, null), NAME);
    }

    private static Sites load(CompoundTag t, HolderLookup.Provider provider) {
        Sites s = new Sites();
        for (Tag e : t.getList("sites", Tag.TAG_COMPOUND)) s.sites.add(Site.load((CompoundTag) e));
        return s;
    }

    @Override
    public CompoundTag save(CompoundTag t, HolderLookup.Provider provider) {
        ListTag l = new ListTag();
        for (Site s : sites) l.add(s.save());
        t.put("sites", l);
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
