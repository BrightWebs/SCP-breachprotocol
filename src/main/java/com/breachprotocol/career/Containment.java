package com.breachprotocol.career;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.compat.Unleashed;
import com.breachprotocol.event.BreachAlerts;
import com.breachprotocol.world.Site01;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.*;

/**
 * The containment registry: every anomaly that has ever been on Foundation ground, where it is held,
 * for how long, and how often it got out. The Foundation watches its own cells, so nobody has to
 * report a breach for it to count:
 *
 * <ul>
 * <li>Every ten seconds each loaded anomaly inside a declared site is checked. A held anomaly that is
 *     loose on two checks in a row has breached: alarms, the news, a message to its site's owner and
 *     the Council. Back in a cell, it's recontained.</li>
 * <li>A room can be designated as a cell from the phone. An empty designated cell takes the first
 *     anomaly put into it.</li>
 * <li>The Council's containment orders (terminate, recapture, transfer to Site-01) are carried out
 *     here the next time the anomaly is in a loaded area.</li>
 * <li>Staff talk about what's held at their site, the secretary briefs on it, and the Altar counts it.</li>
 * </ul>
 */
@EventBusSubscriber(modid = BreachProtocol.MODID)
public final class Containment extends SavedData {
    public static final String NAME = "breachprotocol_containment";

    public static final int HELD = 0, LOOSE = 1, NEUTRALISED = 2;

    public static final class Entry {
        public UUID id;
        public String type = "", designation = "", dk = "", cls = "", site = "", cell = "", order = "", killer = "";
        public int status = LOOSE, breaches, pending, x, y, z;
        public long since, first, seen;
        /** Never held by the Foundation: it wandered onto a site by itself. */
        public boolean wild;

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putUUID("u", id);
            t.putString("ty", type); t.putString("d", designation); t.putString("dk", dk); t.putString("c", cls);
            t.putString("s", site); t.putString("ce", cell); t.putString("o", order); t.putString("k", killer);
            t.putInt("st", status); t.putInt("b", breaches); t.putInt("p", pending);
            t.putIntArray("xyz", new int[]{x, y, z});
            t.putLong("since", since); t.putLong("first", first); t.putLong("seen", seen);
            t.putBoolean("w", wild);
            return t;
        }

        static Entry load(CompoundTag t) {
            Entry e = new Entry();
            e.id = t.getUUID("u");
            e.type = t.getString("ty"); e.designation = t.getString("d"); e.dk = t.getString("dk"); e.cls = t.getString("c");
            e.site = t.getString("s"); e.cell = t.getString("ce"); e.order = t.getString("o"); e.killer = t.getString("k");
            e.status = t.getInt("st"); e.breaches = t.getInt("b"); e.pending = t.getInt("p");
            int[] p = t.getIntArray("xyz");
            if (p.length == 3) { e.x = p[0]; e.y = p[1]; e.z = p[2]; }
            e.since = t.getLong("since"); e.first = t.getLong("first"); e.seen = t.getLong("seen");
            e.wild = t.getBoolean("w");
            return e;
        }

        public String where() { return cell.isEmpty() ? site : cell + ", " + site; }
    }

    public final Map<UUID, Entry> entries = new LinkedHashMap<>();

    public static Containment get(MinecraftServer s) {
        return s.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(Containment::new, Containment::load, null), NAME);
    }

    private static Containment load(CompoundTag t, HolderLookup.Provider p) {
        Containment c = new Containment();
        for (Tag e : t.getList("e", Tag.TAG_COMPOUND)) {
            Entry en = Entry.load((CompoundTag) e);
            c.entries.put(en.id, en);
        }
        return c;
    }

    @Override
    public CompoundTag save(CompoundTag t, HolderLookup.Provider p) {
        ListTag l = new ListTag();
        for (Entry e : entries.values()) l.add(e.save());
        t.put("e", l);
        return t;
    }

    // ================================================================== reading the registry

    public static List<Entry> all(MinecraftServer s) { return new ArrayList<>(get(s).entries.values()); }

    public static List<Entry> at(MinecraftServer s, String site) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : get(s).entries.values()) if (e.site.equals(site) && e.status != NEUTRALISED) out.add(e);
        return out;
    }

    /** How many anomalies the Foundation holds right now, across every site. */
    public static int heldCount(MinecraftServer s) {
        int n = 0;
        for (Entry e : get(s).entries.values()) if (e.status == HELD) n++;
        return n;
    }

    /** How many different kinds of anomaly the Foundation holds right now. */
    public static int heldKinds(MinecraftServer s) {
        Set<String> k = new HashSet<>();
        for (Entry e : get(s).entries.values()) if (e.status == HELD) k.add(e.type);
        return k.size();
    }

    public static int looseCount(MinecraftServer s) {
        int n = 0;
        for (Entry e : get(s).entries.values()) if (e.status == LOOSE) n++;
        return n;
    }

    /** Anomalies held at sites this player declared. */
    public static int heldBy(ServerPlayer p) {
        Set<String> mine = new HashSet<>();
        for (Sites.Site s : Sites.get(p.server).sites) if (s.owner.equals(p.getUUID().toString())) mine.add(s.name);
        int n = 0;
        for (Entry e : get(p.server).entries.values()) if (e.status == HELD && mine.contains(e.site)) n++;
        return n;
    }

    public static long days(MinecraftServer s, long since) { return Math.max(0, (s.overworld().getGameTime() - since) / 24000); }

    // ================================================================== watching the cells

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        MinecraftServer srv = event.getServer();
        if (srv.getTickCount() % 200 != 37) return;
        Containment c = get(srv);
        long now = srv.overworld().getGameTime();
        for (ServerLevel l : srv.getAllLevels()) {
            List<Mob> found = new ArrayList<>();
            for (Entity e : l.getAllEntities()) if (e instanceof Mob m && m.isAlive() && Unleashed.isAnomaly(m)) found.add(m);
            for (Mob m : found) {
                c.check(l, m, now);
            }
        }
        // old news: the dead after a fortnight, strays nobody has seen in three days
        c.entries.values().removeIf(e -> e.status == NEUTRALISED && now - e.since > 24000L * 14
                || e.wild && e.status == LOOSE && now - Math.max(e.seen, e.first) > 24000L * 3);
        c.setDirty();
    }

    private void check(ServerLevel l, Mob m, long now) {
        BlockPos pos = m.blockPosition();
        Sites.Site site = Sites.at(l, pos);
        Entry e = entries.get(m.getUUID());
        // out of its cell on the Foundation's own business (an escort): not a breach
        if (e != null && ESCORT.containsKey(e.id)) { e.seen = now; return; }
        if (e == null) {
            if (site == null) return;
            e = new Entry();
            e.id = m.getUUID();
            e.first = now;
            entries.put(e.id, e);
            fill(e, l, m, site);
            boolean held = Unleashed.isContained(m);
            e.status = held ? HELD : LOOSE;
            e.wild = !held;
            e.since = now;
            if (held) {
                WorldNews.record(l.getServer(), WorldNews.Kind.CONTAINED, e.type, e.designation + " was entered in the registry, held in " + e.where(), 2, site.ownerName);
                notifyOwner(l.getServer(), site, O5.O5_10, e.designation + " is on the books: held in " + e.where() + ". The registry will watch it from here.");
            } else {
                notifyOwner(l.getServer(), site, O5.O5_6, "Something unregistered is loose inside " + site.name + ": " + e.designation + ". It isn't in any cell of ours.");
            }
            return;
        }
        e.seen = now;
        // the Council's orders are carried out the moment it can be reached
        if (!e.order.isEmpty() && carryOut(l, m, e)) return;
        // something that was never ours and has wandered off Foundation ground is no longer our business
        if (e.wild && e.status == LOOSE && site == null) { entries.remove(e.id); return; }
        fill(e, l, m, site);
        // an empty designated cell takes what's put in it
        Sites.Cell cell = Sites.cellAt(l, pos);
        if (cell != null && cell.entity.isEmpty() && !e.type.isEmpty()) {
            cell.entity = e.type;
            Sites.get(l.getServer()).setDirty();
            e.cell = cell.name;
        }
        boolean held = Unleashed.isContained(m);
        if (held) {
            e.pending = 0;
            if (e.status != HELD) {
                boolean firstTime = e.wild;
                e.status = HELD;
                e.since = now;
                e.wild = false;
                e.order = "";
                Sites.Site s = site;
                WorldNews.record(l.getServer(), WorldNews.Kind.CONTAINED, e.type, firstTime ? e.designation + " was brought into containment at " + e.where() : e.designation + " was recontained in " + e.where(), 4, s == null ? "" : s.ownerName);
                if (s != null) {
                    notifyOwner(l.getServer(), s, O5.O5_6, firstTime ? e.designation + " is contained: " + e.where() + ". Good work, whoever did it." : e.designation + " is back in " + e.where() + ". Stand down the alarm.");
                    ServerPlayer owner = player(l.getServer(), s.owner);
                    if (owner != null) Career.changeStability(owner, firstTime ? 3 : 2);
                }
            }
        } else if (e.status == HELD) {
            // two checks in a row, so a stumble at a cell door isn't a breach
            if (++e.pending >= 2) breach(l, m, e, site);
        }
    }

    private static void fill(Entry e, ServerLevel l, Mob m, Sites.Site site) {
        net.minecraft.resources.ResourceLocation id = Unleashed.idOf(m);
        e.type = id == null ? "" : id.getPath();
        e.designation = Unleashed.designation(m);
        e.dk = m.getType().getDescriptionId();
        e.cls = Unleashed.objectClass(m);
        if (site != null) e.site = site.name;
        Sites.Cell cell = Sites.cellAt(l, m.blockPosition());
        e.cell = cell != null && cell.entity.equals(e.type) ? cell.name : site != null && Unleashed.isContained(m) ? (e.cell.isEmpty() ? "an improvised cell" : e.cell) : e.cell;
        e.x = m.getBlockX(); e.y = m.getBlockY(); e.z = m.getBlockZ();
    }

    private void breach(ServerLevel l, Mob m, Entry e, Sites.Site site) {
        e.status = LOOSE;
        e.pending = 0;
        e.breaches++;
        e.since = l.getGameTime();
        String from = e.cell.isEmpty() ? "containment" : e.cell;
        String where = site != null ? site.name : "Foundation ground";
        WorldNews.record(l.getServer(), WorldNews.Kind.BREACH, e.type, e.designation + " broke out of " + from + " at " + where, -6, site == null ? "" : site.ownerName);
        BreachAlerts.announce(l, m.blockPosition(), List.of(e.designation));
        Career.Reply[] orders = breachOrders(l.getServer(), e);
        Response.breached(l.getServer(), e); // no answer in thirty seconds, and the chain of command sends someone
        BreachMissions.open(l.getServer(), e); // and it's a mission on everyone's phone who's told
        if (site != null) {
            notifyOwner(l.getServer(), site, O5.O5_6, "BREACH. " + e.designation + " is out of " + from + " at " + site.name + (e.breaches > 1 ? ". That's breach number " + e.breaches + " for it." : ".") + " Get it back in a cell. Your orders?", orders);
            ServerPlayer owner = player(l.getServer(), site.owner);
            if (owner != null) Career.changeStability(owner, -3);
        }
        for (ServerPlayer q : l.getServer().getPlayerList().getPlayers())
            if ((Career.rank(q).atLeast(Rank.OVERSEER) || Career.isAscended(q)) && (site == null || !q.getUUID().toString().equals(site.owner)))
                Career.message(q, O5.O5_6, "Breach at " + where + ": " + e.designation + " is out of " + from + ". The Council will want an answer. What do we do?", orders);
    }

    static ServerPlayer player(MinecraftServer srv, String uuid) {
        try { return uuid.isEmpty() ? null : srv.getPlayerList().getPlayer(UUID.fromString(uuid)); } catch (IllegalArgumentException e) { return null; }
    }

    /** What you can order when something gets out: a recapture team, termination, a cell at Site-01, a lockdown. */
    static Career.Reply[] breachOrders(MinecraftServer srv, Entry e) {
        String id = e.id.toString(), d = e.designation;
        Career.Reply[] teams = Response.orders(e);
        return new Career.Reply[]{teams[0], teams[1], teams[2],
                Site01.data(srv).built ? new Career.Reply("Take it to Site-01. It doesn't get out of there.", "cont:transfer:" + id, "Understood. Transfer to Site-01 the moment we have it.") : null,
                new Career.Reply("Terminate it.", "cont:terminate:" + id, "Termination team authorised. We'll tell you if it dies."),
                new Career.Reply("Lock everything down.", "dir:lockdown", "Lockdown. Every anomaly near you is frozen in place."),
                teams[3]};
    }

    /** "order:uuid" from a message reply: the Council carries it out (Overseers, SCP-Ω, and a site's owner may order it). */
    public static void ordered(ServerPlayer p, String spec) {
        int c = spec.indexOf(':');
        if (c < 0) return;
        String order = spec.substring(0, c);
        if (!order.equals("hunt") && !order.equals("terminate") && !order.equals("transfer")) return;
        UUID id;
        try { id = UUID.fromString(spec.substring(c + 1)); } catch (IllegalArgumentException ex) { return; }
        Entry e = get(p.server).entries.get(id);
        if (e == null) return;
        boolean owner = false;
        for (Sites.Site st : Sites.get(p.server).sites) if (st.name.equals(e.site) && st.owner.equals(p.getUUID().toString())) owner = true;
        if (!owner && !Career.rank(p).atLeast(Rank.OVERSEER) && !Career.isAscended(p) && Career.rank(p) != Rank.ADMINISTRATOR) return;
        order(p.server, id, order);
    }

    private static void notifyOwner(MinecraftServer srv, Sites.Site site, O5 from, String text, Career.Reply... replies) {
        if (site == null || site.owner.isEmpty()) return;
        try {
            ServerPlayer p = srv.getPlayerList().getPlayer(UUID.fromString(site.owner));
            if (p != null) Career.message(p, from, text, replies);
        } catch (IllegalArgumentException ignored) {}
    }

    // ================================================================== the Council's orders

    // ================================================================== escorts: a held anomaly out on Foundation business

    private record Escort(long until, Vec3 back, net.minecraft.resources.ResourceKey<Level> dim) {}

    private static final Map<UUID, Escort> ESCORT = new HashMap<>();

    /** Staff bring a held anomaly to someone for a while, then take it back to exactly where it was. */
    public static boolean escort(ServerPlayer to, Mob m, int ticks) {
        if (m.level() != to.level()) return false;
        ESCORT.put(m.getUUID(), new Escort(m.level().getGameTime() + ticks, m.position(), m.level().dimension()));
        Vec3 at = to.position().add(to.getLookAngle().multiply(2, 0, 2));
        m.getNavigation().stop();
        m.teleportTo(at.x, to.getY(), at.z);
        m.setPersistenceRequired();
        return true;
    }

    @SubscribeEvent
    public static void escorts(ServerTickEvent.Post event) {
        if (ESCORT.isEmpty() || event.getServer().getTickCount() % 20 != 0) return;
        MinecraftServer srv = event.getServer();
        ESCORT.entrySet().removeIf(en -> {
            Escort x = en.getValue();
            ServerLevel l = srv.getLevel(x.dim());
            if (l == null || l.getGameTime() < x.until()) return false;
            Entity e = l.getEntity(en.getKey());
            if (e == null) return l.getGameTime() > x.until() + 24000; // not loaded: it goes back when it is
            e.teleportTo(x.back().x, x.back().y, x.back().z);
            if (e instanceof Mob m) m.getNavigation().stop();
            return true;
        });
    }

    /** An anomaly walks back into its own cell (on the Administrator's word). */
    public static boolean returnToCell(MinecraftServer srv, UUID id) {
        Entry e = get(srv).entries.get(id);
        Mob m = loaded(srv, id);
        if (e == null || m == null || e.cell.isEmpty()) return false;
        for (Sites.Cell c : Sites.get(srv).cells) {
            if (!c.name.equals(e.cell) || !c.dim.equals(m.level().dimension().location().toString())) continue;
            m.getNavigation().stop();
            m.setTarget(null);
            m.teleportTo((c.x0 + c.x1) / 2.0 + 0.5, c.y0 + 1, (c.z0 + c.z1) / 2.0 + 0.5);
            m.getPersistentData().remove("bp_dom_follow");
            m.getPersistentData().remove("bp_dom_stay");
            return true;
        }
        return false;
    }

    /** A held anomaly's body, if it's loaded anywhere. */
    public static Mob loaded(MinecraftServer srv, UUID id) {
        for (ServerLevel l : srv.getAllLevels()) if (l.getEntity(id) instanceof Mob m && m.isAlive()) return m;
        return null;
    }

    /** Gives a standing order for an anomaly ("terminate", "transfer", "hunt"); carried out when it can be reached. */
    public static void order(MinecraftServer s, UUID id, String order) {
        Entry e = get(s).entries.get(id);
        if (e == null) return;
        e.order = order;
        get(s).setDirty();
        // if it's somewhere loaded right now, don't wait for the next check
        for (ServerLevel l : s.getAllLevels()) {
            Entity en = l.getEntity(id);
            if (en instanceof Mob m && m.isAlive()) { get(s).carryOut(l, m, e); return; }
        }
    }

    /** @return true if the anomaly is no longer where it was (killed or moved) */
    private boolean carryOut(ServerLevel l, Mob m, Entry e) {
        MinecraftServer srv = l.getServer();
        switch (e.order) {
            case "terminate" -> {
                e.order = "";
                m.kill();
                if (m.isAlive()) {
                    WorldNews.record(srv, WorldNews.Kind.BREACH, e.type, "the termination team couldn't kill " + e.designation, -4, "");
                    for (ServerPlayer q : srv.getPlayerList().getPlayers())
                        if (Career.rank(q).atLeast(Rank.OVERSEER) || Career.isAscended(q)) Career.message(q, O5.O5_8, "Termination of " + e.designation + " failed. It's still alive. It always is. We'll need a cell.",
                                    new Career.Reply("Then send Epsilon-11 to bring it in.", "resp:EPSILON_11:" + e.id, "Nine-Tailed Fox is on its way."),
                                    Site01.data(srv).built ? new Career.Reply("Put it at Site-01.", "cont:transfer:" + e.id, "Transfer to Site-01 ordered.") : null);
                    return false;
                }
                e.status = NEUTRALISED;
                e.killer = "the Council's termination order";
                e.since = l.getGameTime();
                WorldNews.record(srv, WorldNews.Kind.MTF, e.type, e.designation + " was terminated on the Council's order", 1, "the Council");
                return true;
            }
            case "transfer" -> {
                e.order = "";
                if (l.dimension() != Level.OVERWORLD || !Site01.data(srv).built) return false;
                BlockPos o = Site01.origin(l);
                // CELL 01-A or 01-B, whichever isn't holding something already
                String[] names = {"CELL 01-A", "CELL 01-B"};
                int[] x0 = {0, 8};
                for (int i = 0; i < 2; i++) {
                    String nm = names[i];
                    boolean taken = false;
                    for (Entry other : entries.values()) if (other != e && other.status == HELD && other.cell.equals(nm)) taken = true;
                    if (taken) continue;
                    Sites.Cell cell = null;
                    for (Sites.Cell c : Sites.get(srv).cells) if (c.name.equals(nm)) cell = c;
                    if (cell == null) Sites.addCell(l, nm, e.type, o.offset(x0[i] + 1, -1, 1), o.offset(x0[i] + 7, 4, 9));
                    else { cell.entity = e.type; Sites.get(srv).setDirty(); }
                    m.teleportTo(o.getX() + x0[i] + 4.5, o.getY(), o.getZ() + 5.5);
                    m.setPersistenceRequired();
                    e.cell = nm;
                    e.site = "Site-01";
                    e.status = HELD;
                    e.wild = false;
                    e.since = l.getGameTime();
                    WorldNews.record(srv, WorldNews.Kind.CONTAINED, e.type, e.designation + " was transferred to Site-01, " + nm, 3, "the Council");
                    for (ServerPlayer q : srv.getPlayerList().getPlayers())
                        if (Career.rank(q).atLeast(Rank.OVERSEER) || Career.isAscended(q)) Career.message(q, O5.O5_10, e.designation + " has been moved to Site-01, " + nm + ". It will not get out of there.");
                    return true;
                }
                for (ServerPlayer q : srv.getPlayerList().getPlayers())
                    if (Career.rank(q).atLeast(Rank.OVERSEER) || Career.isAscended(q)) Career.message(q, O5.O5_10, "Both reserved cells at Site-01 are occupied. " + e.designation + " stays where it is.");
                return false;
            }
            case "hunt" -> {
                // a recapture team: the response unit the breach calls for, sent out for real
                e.order = "";
                Response.dispatch(srv, e, new Forces.Unit[]{Forces.Unit.SECURITY, Forces.Unit.EPSILON_11, Forces.Unit.NU_7}[Response.severity(e)], "the Council");
                return false;
            }
            default -> { e.order = ""; return false; }
        }
    }

    // ================================================================== deaths: neutralised anomalies, and the Altar's count

    @SubscribeEvent
    public static void died(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Mob m) || m.level().isClientSide || !Unleashed.isAnomaly(m)) return;
        MinecraftServer srv = m.getServer();
        if (srv == null) return;
        Entity src = event.getSource().getEntity();
        if (src instanceof Projectile pr && pr.getOwner() != null) src = pr.getOwner();
        boolean wasHeld = Unleashed.isContained(m);
        Containment c = get(srv);
        Entry e = c.entries.get(m.getUUID());
        if (e != null) {
            e.status = NEUTRALISED;
            e.order = "";
            e.since = m.level().getGameTime();
            e.killer = src instanceof ServerPlayer sp ? sp.getGameProfile().getName() : src != null ? src.getName().getString() : "unknown causes";
            c.setDirty();
        }
        // the Altar only counts anomalies put down while they were loose
        if (src instanceof ServerPlayer sp && !wasHeld) {
            CareerData d = Career.data(sp);
            d.anomalyKills++;
            if (d.trial == 1) d.trialKills++;
            d.dirty = true;
        }
    }

    // ================================================================== designating a cell

    /** The room the player stands in becomes a numbered containment cell of the site it's in. */
    public static void designate(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        Sites.Site site = Sites.at(l, p.blockPosition());
        if (site == null) { tell(p, "Cells only count inside a declared site.", ChatFormatting.RED); return; }
        if (Sites.cellAt(l, p.blockPosition()) != null) { tell(p, "You're already standing in " + Sites.cellAt(l, p.blockPosition()).name + ".", ChatFormatting.YELLOW); return; }
        // the enclosed room: everything you can walk or see through from here, bounded by solid blocks
        Set<BlockPos> room = new HashSet<>();
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        BlockPos start = p.blockPosition();
        q.add(start);
        room.add(start);
        int x0 = start.getX(), y0 = start.getY(), z0 = start.getZ(), x1 = x0, y1 = y0, z1 = z0;
        while (!q.isEmpty()) {
            BlockPos c = q.poll();
            for (Direction d : Direction.values()) {
                BlockPos n = c.relative(d);
                if (room.contains(n) || closed(l, n)) continue;
                if (room.size() >= 3000 || Math.abs(n.getX() - start.getX()) > 16 || Math.abs(n.getZ() - start.getZ()) > 16 || Math.abs(n.getY() - start.getY()) > 8) {
                    tell(p, "This room isn't sealed. A cell needs solid walls, floor and ceiling all round (doors count when they're shut).", ChatFormatting.RED);
                    return;
                }
                room.add(n);
                q.add(n);
                x0 = Math.min(x0, n.getX()); y0 = Math.min(y0, n.getY()); z0 = Math.min(z0, n.getZ());
                x1 = Math.max(x1, n.getX()); y1 = Math.max(y1, n.getY()); z1 = Math.max(z1, n.getZ());
            }
        }
        int no = 1;
        for (Sites.Cell c : Sites.get(p.server).cells) if (c.name.startsWith(site.name + " Cell ")) no++;
        String name = site.name + " Cell " + no;
        // whatever's in the room now is what the cell is for; otherwise it takes the first thing put in it
        Mob in = null;
        for (Mob m : l.getEntitiesOfClass(Mob.class, new net.minecraft.world.phys.AABB(x0, y0, z0, x1 + 1, y1 + 1, z1 + 1), Unleashed::isAnomaly)) { in = m; break; }
        String type = "";
        if (in != null) { net.minecraft.resources.ResourceLocation rl = Unleashed.idOf(in); type = rl == null ? "" : rl.getPath(); }
        Sites.addCell(l, name, type, new BlockPos(x0, y0, z0), new BlockPos(x1, y1, z1));
        tell(p, name + " designated" + (in != null ? ", holding " + Unleashed.designation(in) : ": empty, it will hold the first anomaly put in it") + ".", ChatFormatting.AQUA);
        WorldNews.record(p.server, WorldNews.Kind.CONTAINED, "cell", "a new containment cell was designated at " + site.name, 1, p.getGameProfile().getName());
        Career.sync(p);
    }

    private static boolean closed(ServerLevel l, BlockPos p) {
        BlockState s = l.getBlockState(p);
        if (s.isAir()) return false;
        if (s.isCollisionShapeFullBlock(l, p) || Unleashed.isCellBlock(s)) return true;
        String path = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
        // shut doors, glass and bars hold; open doors and furniture don't
        if (path.contains("door") || path.contains("gate")) return !s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.OPEN) || !s.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.OPEN);
        return path.contains("glass") || path.contains("bars") || path.contains("window") || path.contains("pane") || path.contains("wall");
    }

    private static void tell(ServerPlayer p, String msg, ChatFormatting c) {
        p.sendSystemMessage(Component.literal(msg).withStyle(c));
    }

    // ================================================================== the phone and the terminal

    public static void addToSync(ServerPlayer p, CompoundTag t) {
        MinecraftServer srv = p.server;
        boolean top = Career.rank(p).atLeast(Rank.OVERSEER) || Career.isAscended(p);
        Set<String> mine = new HashSet<>();
        for (Sites.Site s : Sites.get(srv).sites) if (s.owner.equals(p.getUUID().toString())) mine.add(s.name);
        Sites.Site here = Sites.at(p.level(), p.blockPosition());
        List<Entry> show = new ArrayList<>();
        for (Entry e : get(srv).entries.values()) {
            if (e.status == NEUTRALISED && days(srv, e.since) > 7) continue;
            if (top || mine.contains(e.site) || here != null && here.name.equals(e.site)) show.add(e);
        }
        show.sort(Comparator.comparingInt((Entry e) -> e.status == LOOSE ? 0 : e.status == HELD ? 1 : 2).thenComparing(e -> e.site).thenComparing(e -> e.designation));
        ListTag l = new ListTag();
        int held = 0, loose = 0, gone = 0;
        for (Entry e : show) {
            if (e.status == HELD) held++; else if (e.status == LOOSE) loose++; else gone++;
            if (l.size() >= 40) continue;
            CompoundTag c = new CompoundTag();
            c.putString("d", e.designation);
            c.putString("dk", e.dk);
            c.putString("o", e.cls);
            c.putString("site", e.site);
            c.putString("cell", e.cell);
            c.putInt("st", e.status);
            c.putInt("days", (int) days(srv, e.since));
            c.putInt("seen", (int) days(srv, e.seen == 0 ? e.first : e.seen));
            c.putInt("br", e.breaches);
            c.putString("ord", e.order);
            c.putString("k", e.killer);
            c.putBoolean("w", e.wild);
            c.putBoolean("mine", mine.contains(e.site));
            l.add(c);
        }
        t.put("registry", l);
        t.putInt("regHeld", held);
        t.putInt("regLoose", loose);
        t.putInt("regGone", gone);
        int cells = 0, empty = 0;
        for (Sites.Cell c : Sites.get(srv).cells) {
            Sites.Site s = Sites.at(srv.overworld(), new BlockPos((c.x0 + c.x1) / 2, c.y0, (c.z0 + c.z1) / 2));
            if (s == null || !(top || mine.contains(s.name))) continue;
            cells++;
            if (c.entity.isEmpty()) empty++;
        }
        t.putInt("regCells", cells);
        t.putInt("regEmpty", empty);
    }

    // ================================================================== what the Council hears about (see AgendaBuilder)

    private static final Map<String, String> TITLES = new HashMap<>();

    public static String title(String id) { return TITLES.getOrDefault(id, "Containment"); }

    private static Calls.Option opt(String label, Calls.Effect e) { return new Calls.Option(label, e); }

    private static String stab(ServerPlayer p, int n, String text) {
        Career.changeStability(p, n);
        return text + "  [Stability " + (n >= 0 ? "+" : "") + n + "]";
    }

    /**
     * Agenda items from the registry: what's loose, what keeps getting out, what's been quiet long
     * enough to study, which cells stand empty. At most {@code max} items, the most urgent first.
     */
    public static List<Calls.Template> agenda(ServerPlayer p, int max) {
        MinecraftServer srv = p.server;
        List<Calls.Template> out = new ArrayList<>();
        List<Entry> all = all(srv);
        boolean site01 = Site01.data(srv).built;
        // ---- loose
        for (Entry e : all) {
            if (out.size() >= max) break;
            if (e.status != LOOSE || e.site.isEmpty()) continue;
            String id = "c_loose_" + e.id.toString().substring(0, 8);
            TITLES.put(id, e.designation + " loose at " + e.site);
            final UUID u = e.id;
            final String site = e.site, des = e.designation;
            boolean keter = e.cls.equalsIgnoreCase("Keter");
            String how = e.wild ? des + " has been wandering " + site + " since " + WorldNews.ago(srv, e.since) + ". It has never been in a cell of ours."
                    : des + " broke out of " + (e.cell.isEmpty() ? "containment" : e.cell) + " at " + site + " " + WorldNews.ago(srv, e.since) + (e.breaches > 1 ? ". Its " + ordinal(e.breaches) + " breach." : ".") + " It is still loose.";
            List<Calls.Option> opts = new ArrayList<>();
            opts.add(opt("Send Epsilon-11 to recapture it", (q, x) -> {
                Forces.order(q.server, site, Forces.Unit.EPSILON_11, 3);
                order(q.server, u, "hunt");
                return stab(q, 2, "Nine-Tailed Fox is on its way to " + site + ". They'll drive " + des + " back into a cell.");
            }));
            opts.add(opt(keter ? "Attempt termination" : "Terminate it", (q, x) -> {
                order(q.server, u, "terminate");
                return stab(q, keter ? -2 : 0, keter ? "A termination team is going in. History says it won't work on " + des + ". We try anyway." : "A termination team will put " + des + " down the moment it's in reach. One less file.");
            }));
            opts.add(opt("Lock " + site + " down", (q, x) -> {
                Policy.set(q.server, d2 -> d2.lockdownUntil = Policy.days(q.server, 1));
                return stab(q, 3, site + " is locked down for a day. Every guard on alert. Nothing gets out of the perimeter.");
            }));
            out.add(new Calls.Template(id, 6, false, how + " What do we do?", opts));
        }
        // ---- repeat offenders: move them somewhere they can't get out of
        for (Entry e : all) {
            if (out.size() >= max) break;
            if (e.breaches < 2 || e.status == NEUTRALISED || e.site.equals("Site-01") || !site01) continue;
            String id = "c_transfer_" + e.id.toString().substring(0, 8);
            TITLES.put(id, "Transfer of " + e.designation + " to Site-01");
            final UUID u = e.id;
            final String des = e.designation, site = e.site;
            out.add(new Calls.Template(id, 10, false,
                    des + " has breached " + e.breaches + " times at " + site + ". The cell isn't the problem; the site is. Site-01 has two reserved cells. I move we transfer it.",
                    List.of(opt("Transfer it to Site-01", (q, x) -> {
                                order(q.server, u, "transfer");
                                return stab(q, 4, des + " will be moved to Site-01 under escort. The Administrator's own cells. It won't see daylight again.");
                            }),
                            opt("Reinforce its cell where it is", (q, x) -> {
                                Forces.order(q.server, site, Forces.Unit.SECURITY, 2);
                                return stab(q, 1, "Two more officers on that door, and new locks. " + site + " keeps it.");
                            }),
                            opt("Leave it", (q, x) -> stab(q, -2, "Then we wait for breach number " + (e.breaches + 1) + ".")))));
        }
        // ---- quiet ones: long enough held to be studied
        for (Entry e : all) {
            if (out.size() >= max) break;
            if (e.status != HELD || days(srv, e.since) < 7) continue;
            String id = "c_test_" + e.type;
            CareerData d = Career.data(p);
            Long last = d.agendaUsed.get(id);
            long day = srv.overworld().getDayTime() / 24000;
            if (last != null && day - last < 21) continue;
            TITLES.put(id, "Testing programme for " + e.designation);
            final String des = e.designation, where = e.where();
            long held = days(srv, e.since);
            out.add(new Calls.Template(id, 3, false,
                    des + " has been quiet in " + where + " for " + held + " days. Research wants a testing programme. Quiet anomalies are the ones we understand least.",
                    List.of(opt("Approve testing with D-Class", (q, x) -> {
                                WorldNews.record(q.server, WorldNews.Kind.KNOWLEDGE, "test_" + des, "a testing programme on " + des + " was approved", 2, "the Council");
                                return stab(q, 2, "Testing begins on " + des + " tomorrow. Three D-Class, full observation. We'll learn something.");
                            }),
                            opt("Observation only", (q, x) -> stab(q, 1, "We watch " + des + ". We don't touch. The logs grow longer.")),
                            opt("Leave it alone", (q, x) -> stab(q, 0, "Let sleeping anomalies lie. " + des + " stays as it is.")))));
        }
        // ---- empty cells
        for (Sites.Cell c : Sites.get(srv).cells) {
            if (out.size() >= max) break;
            if (!c.entity.isEmpty() || c.name.startsWith("CELL 01-")) continue;
            String id = "c_empty_" + Integer.toHexString(c.name.hashCode());
            CareerData d = Career.data(p);
            Long last = d.agendaUsed.get(id);
            long day = srv.overworld().getDayTime() / 24000;
            if (last != null && day - last < 14) continue;
            TITLES.put(id, c.name + " stands empty");
            final String nm = c.name;
            out.add(new Calls.Template(id, 12, false,
                    nm + " stands empty. Built, staffed, powered, and holding nothing. Either we fill it or we stop paying for it.",
                    List.of(opt("Keep it ready", (q, x) -> stab(q, 1, nm + " stays ready. Something always comes along.")),
                            opt("Decommission it", (q, x) -> {
                                Sites.get(q.server).cells.removeIf(cc -> cc.name.equals(nm));
                                Sites.get(q.server).setDirty();
                                return stab(q, 0, nm + " is decommissioned. The room is just a room now.");
                            }))));
        }
        return out;
    }

    private static String ordinal(int n) {
        return switch (n) { case 2 -> "second"; case 3 -> "third"; case 4 -> "fourth"; case 5 -> "fifth"; default -> n + "th"; };
    }

    // ================================================================== what staff know (see Converse)

    /** One thing a member of staff at this site would say about its cells, or null. */
    public static String gossip(ServerLevel l, BlockPos at, Random r) {
        Sites.Site s = Sites.at(l, at);
        if (s == null) return null;
        List<Entry> here = at(l.getServer(), s.name);
        if (here.isEmpty()) return null;
        List<Entry> loose = here.stream().filter(e -> e.status == LOOSE).toList();
        if (!loose.isEmpty()) {
            Entry e = loose.get(r.nextInt(loose.size()));
            return e.wild ? e.designation + " is wandering the site and it isn't even ours. Nobody's put it in a cell yet."
                    : e.designation + " is out. Since " + WorldNews.ago(l.getServer(), e.since) + ". I keep checking the corridor behind me.";
        }
        Entry e = here.get(r.nextInt(here.size()));
        long d = days(l.getServer(), e.since);
        return switch (r.nextInt(3)) {
            case 0 -> e.designation + " in " + (e.cell.isEmpty() ? "its cell" : e.cell) + " has been quiet " + (d == 0 ? "since it came in" : "for " + d + " day" + (d == 1 ? "" : "s")) + ". Quiet's the part I don't trust.";
            case 1 -> "We hold " + here.size() + " anomal" + (here.size() == 1 ? "y" : "ies") + " here now. " + (here.size() >= 4 ? "Feels like a proper site." : "Small site. Everyone knows every cell.");
            default -> e.breaches > 0 ? e.designation + " has got out " + e.breaches + " time" + (e.breaches == 1 ? "" : "s") + ". Everyone on that wing sleeps in their boots." : "Cell checks are my favourite part of the day. Nothing in them has ever got out. Yet.";
        };
    }
}
