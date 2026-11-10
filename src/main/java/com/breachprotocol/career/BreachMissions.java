package com.breachprotocol.career;

import com.breachprotocol.BreachProtocol;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.UUID;

/**
 * Every breach is a mission on the phone of everyone who's told about it, whether you deal with it
 * yourself or someone's been sent: where the anomaly is right now (the Guide arrow follows it), and
 * what's being done about it. It closes itself when the anomaly is back in a cell or dead.
 */
@EventBusSubscriber(modid = BreachProtocol.MODID)
public final class BreachMissions {
    private BreachMissions() {}

    private static boolean told(ServerPlayer q, Containment.Entry e, MinecraftServer srv) {
        if (Career.rank(q) == Rank.ADMINISTRATOR || Career.rank(q).atLeast(Rank.OVERSEER) || Career.isAscended(q)) return true;
        for (Sites.Site s : Sites.get(srv).sites) if (s.name.equals(e.site) && s.owner.equals(q.getUUID().toString())) return true;
        return false;
    }

    /** A breach: it goes on the list. */
    static void open(MinecraftServer srv, Containment.Entry e) {
        for (ServerPlayer q : srv.getPlayerList().getPlayers()) {
            if (!told(q, e, srv)) continue;
            CareerData d = Career.data(q);
            if (find(d, e.id) != null) continue;
            Mission m = new Mission();
            m.type = Mission.Type.BREACH;
            m.designation = e.designation;
            m.item = e.id.toString();
            m.giver = 6;
            m.target = 1;
            m.expires = Long.MAX_VALUE;
            m.hasPos = true;
            m.x = e.x; m.y = e.y; m.z = e.z;
            m.description = e.designation + " is out of " + (e.cell.isEmpty() ? "containment" : e.cell) + (e.site.isEmpty() ? "" : " at " + e.site) + ". Get it back in a cell.";
            m.status = "Unanswered. If you don't give an order, the chain of command will.";
            d.missions.add(0, m);
            d.dirty = true;
            Career.sync(q);
        }
    }

    static Mission find(CareerData d, UUID id) {
        String s = id.toString();
        for (Mission m : d.missions) if (m.type == Mission.Type.BREACH && m.item.equals(s)) return m;
        return null;
    }

    /** What's being done about it, on everyone's copy of the mission. */
    static void status(MinecraftServer srv, UUID id, String status) {
        for (ServerPlayer q : srv.getPlayerList().getPlayers()) {
            Mission m = find(Career.data(q), id);
            if (m == null || m.status.equals(status)) continue;
            m.status = status;
            Career.data(q).dirty = true;
            Career.sync(q);
        }
    }

    /** The team can't do it: they need you. The arrow comes up on your screen, pointing at it. */
    static void beg(MinecraftServer srv, UUID id, String from, String text, Career.Reply... replies) {
        Mob mob = Containment.loaded(srv, id);
        for (ServerPlayer q : srv.getPlayerList().getPlayers()) {
            Mission m = find(Career.data(q), id);
            if (m == null) continue;
            m.status = "!They can't bring it in. They need you.";
            Career.message(q, from, text, replies);
            if (Career.rank(q) == Rank.ADMINISTRATOR || Career.isAscended(q)) guide(q, m, mob);
        }
    }

    /** Puts the Guide arrow on the anomaly (the client starts it when it sees the request). */
    static void guide(ServerPlayer q, Mission m, Mob mob) {
        CompoundTag g = new CompoundTag();
        g.putInt("x", mob != null ? mob.getBlockX() : m.x);
        g.putInt("y", mob != null ? mob.getBlockY() : m.y);
        g.putInt("z", mob != null ? mob.getBlockZ() : m.z);
        g.putInt("eid", mob != null && mob.level() == q.level() ? mob.getId() : -1);
        g.putString("label", m.designation);
        g.putInt("n", q.getPersistentData().getInt("bp_guide_n") + 1);
        q.getPersistentData().putInt("bp_guide_n", g.getInt("n"));
        q.getPersistentData().put("bp_guide_req", g);
        Career.sync(q);
    }

    /** The Guide arrow onto anything (a rift, a spot): the client starts it when it sees the request. */
    public static void guideTo(ServerPlayer q, int x, int y, int z, int eid, String label) {
        CompoundTag g = new CompoundTag();
        g.putInt("x", x); g.putInt("y", y); g.putInt("z", z);
        g.putInt("eid", eid);
        g.putString("label", label);
        g.putInt("n", q.getPersistentData().getInt("bp_guide_n") + 1);
        q.getPersistentData().putInt("bp_guide_n", g.getInt("n"));
        q.getPersistentData().put("bp_guide_req", g);
        Career.sync(q);
    }

    /** "guide_ent:<entity id>" from a reply: the arrow onto it, following it while it's in reach. */
    public static void guideEntity(ServerPlayer q, int eid, String label) {
        net.minecraft.world.entity.Entity e = q.serverLevel().getEntity(eid);
        if (e == null) return;
        guideTo(q, e.getBlockX(), e.getBlockY(), e.getBlockZ(), eid, label);
    }

    public static void addToSync(ServerPlayer p, CompoundTag t) {
        if (p.getPersistentData().contains("bp_guide_req")) t.put("guideReq", p.getPersistentData().getCompound("bp_guide_req").copy());
    }

    /** Twice a second... no: every two seconds, the missions follow the anomaly, and close when it's dealt with. */
    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        MinecraftServer srv = event.getServer();
        if (srv.getTickCount() % 40 != 13) return;
        for (ServerPlayer q : srv.getPlayerList().getPlayers()) {
            CareerData d = Career.data(q);
            boolean changed = false;
            for (Mission m : d.missions) {
                if (m.type != Mission.Type.BREACH || m.done()) continue;
                UUID id;
                try { id = UUID.fromString(m.item); } catch (IllegalArgumentException ex) { m.progress = m.target; changed = true; continue; }
                Containment.Entry e = null;
                for (Containment.Entry x : Containment.all(srv)) if (x.id.equals(id)) e = x;
                if (e == null || e.status != Containment.LOOSE) {
                    m.status = e != null && e.status == Containment.NEUTRALISED ? "Neutralised." : "Contained.";
                    m.progress = m.target;
                    changed = true;
                    continue;
                }
                Mob mob = Containment.loaded(srv, id);
                if (mob != null && (mob.getBlockX() != m.x || mob.getBlockZ() != m.z || m.eid != mob.getId())) {
                    m.x = mob.getBlockX(); m.y = mob.getBlockY(); m.z = mob.getBlockZ();
                    m.eid = mob.level() == q.level() ? mob.getId() : -1;
                    changed = true;
                }
            }
            if (changed) { d.dirty = true; Career.completeFinished(q); Career.sync(q); }
        }
    }
}
