package com.breachprotocol.career;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.entity.FoundationStaff;
import com.breachprotocol.registry.ModEntities;
import com.breachprotocol.world.Site01;
import com.breachprotocol.world.SiteTower;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
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
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.*;

/**
 * The armed personnel at each site: who is stationed there, how many the Administrator wants of
 * each unit, losses replaced, and the O5 Council's own review every three days.
 */
@EventBusSubscriber(modid = BreachProtocol.MODID)
public final class Forces {
    private Forces() {}

    /** The units you can station at a site. */
    public enum Unit {
        SECURITY("Site Security", "Officer", "pistol", 26, 0.65F, "Pistols. Patrols, doors and perimeter."),
        MARKSMAN("Security Marksmen", "Marksman", "sniper_rifle", 24, 0.9F, "Sniper rifles. Hold the high ground, pick off creepers early."),
        EPSILON_11("MTF Epsilon-11 \"Nine-Tailed Fox\"", "Agent", "assault_rifle", 34, 0.8F, "Assault rifles. The Foundation's internal security force."),
        NU_7("MTF Nu-7 \"Hammer Down\"", "Sgt.", "machine_gun", 42, 0.7F, "Machine guns. Heavy infantry for open war."),
        ALPHA_1("MTF Alpha-1 \"Red Right Hand\"", "Operative", "combat_shotgun", 50, 0.85F, "The Council's own. Answers only to the O5 and the Administrator.");

        public final String title, prefix, gun, about;
        public final int health;
        public final float accuracy;

        Unit(String title, String prefix, String gun, int health, float accuracy, String about) {
            this.title = title; this.prefix = prefix; this.gun = gun; this.health = health; this.accuracy = accuracy; this.about = about;
        }

        public static Unit of(String s) {
            for (Unit u : values()) if (u.name().equals(s)) return u;
            return null;
        }
    }

    /** The Council's review: every three days. */
    public static final long REVIEW = 24000L * 3;
    public static final int MAX_PER_UNIT = 24;
    /** What the Council keeps at a site nobody has given orders for. */
    private static final Map<Unit, Integer> BASELINE = Map.of(Unit.SECURITY, 6, Unit.MARKSMAN, 2, Unit.EPSILON_11, 4);

    public static final class Station {
        public final Map<Unit, Integer> target = new EnumMap<>(Unit.class);
        public final Map<UUID, Unit> members = new LinkedHashMap<>();
        public boolean auto = true;
        public boolean ordered;
        public final Deque<String> losses = new ArrayDeque<>();

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            CompoundTag tg = new CompoundTag();
            for (Map.Entry<Unit, Integer> e : target.entrySet()) tg.putInt(e.getKey().name(), e.getValue());
            t.put("target", tg);
            ListTag m = new ListTag();
            for (Map.Entry<UUID, Unit> e : members.entrySet()) {
                CompoundTag x = new CompoundTag();
                x.putUUID("id", e.getKey());
                x.putString("u", e.getValue().name());
                m.add(x);
            }
            t.put("members", m);
            t.putBoolean("auto", auto);
            t.putBoolean("ordered", ordered);
            ListTag l = new ListTag();
            for (String s : losses) l.add(net.minecraft.nbt.StringTag.valueOf(s));
            t.put("losses", l);
            return t;
        }

        static Station load(CompoundTag t) {
            Station s = new Station();
            CompoundTag tg = t.getCompound("target");
            for (String k : tg.getAllKeys()) { Unit u = Unit.of(k); if (u != null) s.target.put(u, tg.getInt(k)); }
            for (Tag x : t.getList("members", Tag.TAG_COMPOUND)) {
                CompoundTag c = (CompoundTag) x;
                Unit u = Unit.of(c.getString("u"));
                if (u != null && c.hasUUID("id")) s.members.put(c.getUUID("id"), u);
            }
            s.auto = !t.contains("auto") || t.getBoolean("auto");
            s.ordered = t.getBoolean("ordered");
            for (Tag x : t.getList("losses", Tag.TAG_STRING)) s.losses.add(x.getAsString());
            return s;
        }

        public int count(Unit u) {
            int n = 0;
            for (Unit m : members.values()) if (m == u) n++;
            return n;
        }

        public int wanted(Unit u) { return ordered ? target.getOrDefault(u, 0) : BASELINE.getOrDefault(u, 0); }
    }

    public static final class Data extends SavedData {
        public final Map<String, Station> stations = new LinkedHashMap<>();
        /** Deployments waiting for the site to be loaded: site name -> unit list. */
        public final ListTag pending = new ListTag();
        public long nextReview = -1;
        public static final SavedData.Factory<Data> FACTORY = new SavedData.Factory<>(Data::new, Data::load, null);

        static Data load(CompoundTag t, HolderLookup.Provider p) {
            Data d = new Data();
            CompoundTag st = t.getCompound("stations");
            for (String k : st.getAllKeys()) d.stations.put(k, Station.load(st.getCompound(k)));
            d.pending.addAll(t.getList("pending", Tag.TAG_COMPOUND));
            d.nextReview = t.contains("next") ? t.getLong("next") : -1;
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag t, HolderLookup.Provider p) {
            CompoundTag st = new CompoundTag();
            for (Map.Entry<String, Station> e : stations.entrySet()) st.put(e.getKey(), e.getValue().save());
            t.put("stations", st);
            t.put("pending", pending);
            t.putLong("next", nextReview);
            return t;
        }

        public Station station(String site) { return stations.computeIfAbsent(site, k -> new Station()); }
    }

    public static Data data(MinecraftServer s) { return s.overworld().getDataStorage().computeIfAbsent(Data.FACTORY, "breachprotocol_forces"); }

    private static boolean topBrass(ServerPlayer p) { return Career.rank(p) == Rank.ADMINISTRATOR || Career.isAscended(p); }

    // ================================================================== where they stand

    static ServerLevel level(MinecraftServer s, Sites.Site site) {
        return s.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(site.dim)));
    }

    /** Guard posts at a site: the entrance first, then around the grounds. */
    static List<BlockPos> posts(ServerLevel l, Sites.Site site) {
        List<BlockPos> out = new ArrayList<>();
        if (site.name.equals("Site-01")) {
            BlockPos o = Site01.origin(l);
            BlockPos a = Site01.arrival(o);
            out.add(a);
            for (int[] c : new int[][]{{10, 9}, {30, 22}, {48, 37}, {20, 47}, {50, 47}, {8, 30}, {58, 22}})
                out.add(o.offset(c[0], 0, c[1]).atY(a.getY()));
            return out;
        }
        int half = SiteTower.T / 2;
        int gy = site.y + 1;
        out.add(new BlockPos(site.x, gy, site.z + half + 3));           // the front walk
        out.add(new BlockPos(site.x - 6, gy, site.z + half + 2));
        out.add(new BlockPos(site.x + 6, gy, site.z + half + 2));
        for (int[] c : new int[][]{{-half - 3, -half - 3}, {half + 3, -half - 3}, {-half - 3, half + 3}, {half + 3, half + 3}, {0, -half - 3}, {-half - 3, 0}, {half + 3, 0}})
            out.add(new BlockPos(site.x + c[0], gy, site.z + c[1]));
        out.add(new BlockPos(site.x, gy, site.z));                       // the lobby
        return out;
    }

    // ================================================================== deploying

    /** Orders {@code n} of a unit to a site (they arrive as soon as the site is loaded). */
    public static void order(MinecraftServer s, String site, Unit u, int n) {
        Data d = data(s);
        for (int i = 0; i < n; i++) {
            CompoundTag t = new CompoundTag();
            t.putString("site", site);
            t.putString("u", u.name());
            d.pending.add(t);
        }
        d.setDirty();
    }

    private static int pendingFor(Data d, String site, Unit u) {
        int n = 0;
        for (Tag x : d.pending) { CompoundTag c = (CompoundTag) x; if (c.getString("site").equals(site) && c.getString("u").equals(u.name())) n++; }
        return n;
    }

    /** Orders whatever is missing to bring a site up to strength. Returns how many were sent. */
    public static int fill(MinecraftServer s, String site) {
        Data d = data(s);
        Station st = d.station(site);
        int sent = 0;
        for (Unit u : Unit.values()) {
            int miss = st.wanted(u) - st.count(u) - pendingFor(d, site, u);
            if (miss > 0) { order(s, site, u, miss); sent += miss; }
        }
        return sent;
    }

    private static FoundationStaff spawn(ServerLevel l, Sites.Site site, Station st, Unit u) {
        List<BlockPos> posts = posts(l, site);
        BlockPos post = posts.get(st.members.size() % posts.size());
        if (!site.name.equals("Site-01")) post = l.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, post).getY() > post.getY() + 4 ? post : l.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, post);
        BlockPos from = posts.get(0);
        FoundationStaff g = ModEntities.STAFF.get().create(l);
        if (g == null) return null;
        g.moveTo(from.getX() + 0.5, from.getY(), from.getZ() + 0.5, l.random.nextFloat() * 360F, 0);
        g.setupUnit(u, post);
        l.addFreshEntity(g);
        st.members.put(g.getUUID(), u);
        return g;
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        MinecraftServer s = event.getServer();
        if (s.getTickCount() % 40 != 21) return;
        Data d = data(s);
        long now = s.overworld().getGameTime();
        if (d.nextReview < 0) { d.nextReview = now + REVIEW; d.setDirty(); }
        if (now >= d.nextReview) { d.nextReview = now + REVIEW; d.setDirty(); review(s); }
        if (d.pending.isEmpty()) return;
        Sites all = Sites.get(s);
        Map<String, Integer> arrived = new LinkedHashMap<>();
        for (Iterator<Tag> it = d.pending.iterator(); it.hasNext(); ) {
            CompoundTag t = (CompoundTag) it.next();
            Sites.Site site = all.sites.stream().filter(x -> x.name.equals(t.getString("site"))).findFirst().orElse(null);
            Unit u = Unit.of(t.getString("u"));
            if (site == null || u == null) { it.remove(); continue; }
            ServerLevel l = level(s, site);
            if (l == null) { it.remove(); continue; }
            BlockPos c = new BlockPos(site.x, site.y, site.z);
            if (!l.isLoaded(c) || l.getNearestPlayer(c.getX(), c.getY(), c.getZ(), 128, false) == null) continue;
            Station st = d.station(site.name);
            if (st.count(u) >= MAX_PER_UNIT) { it.remove(); continue; }
            if (spawn(l, site, st, u) != null) arrived.merge(site.name, 1, Integer::sum);
            it.remove();
            if (arrived.values().stream().mapToInt(Integer::intValue).sum() >= 6) break; // a few at a time
        }
        d.setDirty();
        for (Map.Entry<String, Integer> e : arrived.entrySet()) {
            Sites.Site site = all.sites.stream().filter(x -> x.name.equals(e.getKey())).findFirst().orElse(null);
            if (site == null) continue;
            ServerLevel l = level(s, site);
            for (ServerPlayer q : s.getPlayerList().getPlayers())
                if (q.level() == l && q.blockPosition().closerThan(new BlockPos(site.x, site.y, site.z), site.radius + 64) || topBrass(q))
                    q.displayClientMessage(Component.literal(e.getValue() + " armed personnel reported for duty at " + site.name + ".").withStyle(ChatFormatting.DARK_AQUA), true);
        }
    }

    /** The O5 Council's own review: brings every site back to strength and replaces the dead. */
    static void review(MinecraftServer s) {
        int total = 0;
        List<String> where = new ArrayList<>();
        for (Sites.Site site : Sites.get(s).sites) {
            int n = fill(s, site.name);
            if (n > 0) { total += n; where.add(site.name); }
        }
        if (total == 0) return;
        String line = "the O5 Council authorised " + total + " new armed personnel for " + String.join(", ", where);
        WorldNews.record(s, WorldNews.Kind.FORCES, "review", line, 2, "the Council");
        for (ServerPlayer q : s.getPlayerList().getPlayers())
            if (topBrass(q) || Career.rank(q).atLeast(Rank.OVERSEER))
                q.sendSystemMessage(Component.literal("O5 Council: ").withStyle(ChatFormatting.DARK_RED).append(Component.literal("Our review is complete. We have authorised " + total + " new armed personnel (" + String.join(", ", where) + ").").withStyle(ChatFormatting.GRAY)));
    }

    /** One of ours died. */
    public static void died(FoundationStaff g, String cause) {
        MinecraftServer s = g.getServer();
        if (s == null || g.unit() == null) return;
        Data d = data(s);
        for (Map.Entry<String, Station> e : d.stations.entrySet()) {
            Station st = e.getValue();
            if (st.members.remove(g.getUUID()) == null) continue;
            st.losses.addFirst(g.getName().getString() + " (" + g.unit().prefix + ") " + cause);
            while (st.losses.size() > 6) st.losses.removeLast();
            if (st.auto) order(s, e.getKey(), g.unit(), 1);
            d.setDirty();
            WorldNews.record(s, WorldNews.Kind.DEATH, "kia", g.getName().getString() + " of " + g.unit().title + " was killed in the line of duty at " + e.getKey(), -2, "");
            return;
        }
    }

    /** A site is under attack: the Council sends help (once per attack) if the site is short. */
    static void underAttack(ServerLevel l, Sites.Site site, Defence.Alert a) {
        Data d = data(l.getServer());
        Station st = d.station(site.name);
        int have = st.members.size();
        if (have >= 10 && st.count(Unit.EPSILON_11) >= 4) return;
        order(l.getServer(), site.name, Unit.EPSILON_11, 4);
        for (ServerPlayer q : l.getServer().getPlayerList().getPlayers())
            if (topBrass(q) || q.level() == l && q.blockPosition().closerThan(new BlockPos(site.x, site.y, site.z), site.radius + 96))
                q.sendSystemMessage(Component.literal("O5 Council: ").withStyle(ChatFormatting.DARK_RED).append(Component.literal("Epsilon-11 is inbound to " + site.name + ". Hold the line.").withStyle(ChatFormatting.GRAY)));
    }

    /** Removes members that no longer exist (killed while unloaded, or gone with a rebuilt site). */
    public static void prune(MinecraftServer s, String site) {
        Data d = data(s);
        Station st = d.station(site);
        Sites.Site ss = Sites.get(s).sites.stream().filter(x -> x.name.equals(site)).findFirst().orElse(null);
        if (ss == null) return;
        ServerLevel l = level(s, ss);
        if (l == null || !l.isLoaded(new BlockPos(ss.x, ss.y, ss.z))) return;
        // only prune when the whole site is loaded enough to see them
        int seen = 0;
        for (FoundationStaff f : l.getEntitiesOfClass(FoundationStaff.class, new net.minecraft.world.phys.AABB(new BlockPos(ss.x, ss.y, ss.z)).inflate(ss.radius + 64, 96, ss.radius + 64)))
            if (st.members.containsKey(f.getUUID())) seen++;
        if (seen == 0 && !st.members.isEmpty() && l.getNearestPlayer(ss.x, ss.y, ss.z, 48, false) != null) {
            // standing in the middle of the site and none of them are here: they're gone
            st.members.clear();
            d.setDirty();
        }
    }

    // ================================================================== the computer's controls

    private static Sites.Site site(MinecraftServer s, int i) {
        List<Sites.Site> all = Sites.get(s).sites;
        return i >= 0 && i < all.size() ? all.get(i) : null;
    }

    public static void action(ServerPlayer p, String action, int arg) {
        if (!topBrass(p)) return;
        Sites.Site site = site(p.server, arg / 16);
        if (site == null) return;
        Data d = data(p.server);
        Station st = d.station(site.name);
        Unit u = Unit.values()[Math.floorMod(arg % 16, Unit.values().length)];
        if (!st.ordered) { // the first order replaces the Council's baseline with the Administrator's own numbers
            st.ordered = true;
            for (Unit x : Unit.values()) st.target.put(x, BASELINE.getOrDefault(x, 0));
        }
        switch (action) {
            case "force_inc" -> st.target.put(u, Math.min(MAX_PER_UNIT, st.target.getOrDefault(u, 0) + 1));
            case "force_dec" -> st.target.put(u, Math.max(0, st.target.getOrDefault(u, 0) - 1));
            case "force_deploy" -> {
                int miss = st.wanted(u) - st.count(u) - pendingFor(d, site.name, u);
                if (miss > 0) order(p.server, site.name, u, miss);
                p.displayClientMessage(Component.literal(miss > 0 ? miss + " " + u.title + " on their way to " + site.name + "." : site.name + " already has its " + u.title + ".").withStyle(ChatFormatting.DARK_AQUA), true);
            }
            case "force_fill" -> {
                int n = fill(p.server, site.name);
                p.displayClientMessage(Component.literal(n > 0 ? n + " personnel on their way to " + site.name + "." : site.name + " is at full strength.").withStyle(ChatFormatting.DARK_AQUA), true);
            }
            case "force_auto" -> st.auto = !st.auto;
            case "force_dismiss" -> {
                ServerLevel l = level(p.server, site);
                int n = 0;
                if (l != null) for (FoundationStaff f : l.getEntitiesOfClass(FoundationStaff.class, new net.minecraft.world.phys.AABB(new BlockPos(site.x, site.y, site.z)).inflate(site.radius + 64, 96, site.radius + 64)))
                    if (st.members.get(f.getUUID()) == u && st.count(u) > st.wanted(u)) { st.members.remove(f.getUUID()); f.discard(); n++; }
                p.displayClientMessage(Component.literal(n > 0 ? n + " " + u.title + " stood down from " + site.name + "." : "No one to stand down.").withStyle(ChatFormatting.DARK_AQUA), true);
            }
            default -> {}
        }
        d.setDirty();
    }

    public static void addToSync(ServerPlayer p, CompoundTag t) {
        Data d = data(p.server);
        ListTag out = new ListTag();
        List<Sites.Site> all = Sites.get(p.server).sites;
        for (int i = 0; i < all.size(); i++) {
            Sites.Site site = all.get(i);
            Station st = d.station(site.name);
            CompoundTag c = new CompoundTag();
            c.putInt("i", i);
            c.putString("n", site.name);
            c.putBoolean("auto", st.auto);
            c.putBoolean("ordered", st.ordered);
            ListTag us = new ListTag();
            for (Unit u : Unit.values()) {
                CompoundTag x = new CompoundTag();
                x.putString("t", u.title);
                x.putString("a", u.about);
                x.putInt("have", st.count(u));
                x.putInt("want", st.wanted(u));
                x.putInt("coming", pendingFor(d, site.name, u));
                us.add(x);
            }
            c.put("units", us);
            ListTag ls = new ListTag();
            for (String s : st.losses) ls.add(net.minecraft.nbt.StringTag.valueOf(s));
            c.put("losses", ls);
            Defence.Alert a = Defence.alertAt(site.name);
            if (a != null) { c.putString("alert", a.org); c.putInt("hostiles", a.hostiles); c.putInt("kills", a.kills); }
            out.add(c);
        }
        t.put("forces", out);
        t.putLong("review", Math.max(0, d.nextReview - p.server.overworld().getGameTime()));
        Defence.addToSync(p, t);
    }
}
