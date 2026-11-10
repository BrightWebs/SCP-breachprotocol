package com.breachprotocol.career;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.compat.Unleashed;
import com.breachprotocol.compat.UnleashedApi;
import com.breachprotocol.entity.FoundationStaff;
import com.breachprotocol.world.Site01;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.*;

/**
 * Breach response: armed staff who go out, find a breached anomaly, secure it and bring it in, so
 * the Administrator doesn't have to do it all personally.
 *
 * <p><b>Who goes.</b> You choose in reply to the breach message (Site Security, Epsilon-11, Nu-7, or
 * "leave it to me"). If you haven't answered in thirty seconds, the chain of command decides, as the
 * Foundation would: a Safe breach is the Site Director's, who sends Site Security; a Euclid breach is
 * the Security Director's, who calls in MTF Epsilon-11, the Foundation's recontainment specialists; a
 * Keter breach goes to the O5 Council, which authorises Nu-7 with Epsilon-11 behind them. The team is
 * whoever of that unit is stationed at the site and free, with the rest called in.</p>
 *
 * <p><b>What they do with it.</b> Once it's secured: back to its own cell; or a designated empty cell
 * at that site; or, for a Keter with nowhere to go, termination (the Council's authority) unless it
 * can't die; or one of Site-01's reserved cells; and if there's truly nowhere, they build an improvised
 * holding cell on site out of containment panels, register it, and lock it in. If the team is wiped
 * out, the next tier up is sent.</p>
 */
@EventBusSubscriber(modid = BreachProtocol.MODID)
public final class Response {
    private Response() {}

    private static final String SECURED = "bp_secured";

    /** Tiers, lowest first: each is what the one below escalates to. */
    private static final Forces.Unit[] TIERS = {Forces.Unit.SECURITY, Forces.Unit.EPSILON_11, Forces.Unit.NU_7};

    private enum Phase { WAITING, HUNTING, ESCORTING, TERMINATING, DONE }

    private static final class Team {
        final UUID target;
        final Forces.Unit unit;
        final String by;
        final List<UUID> members = new ArrayList<>();
        Phase phase = Phase.WAITING;
        Forces.Unit escalate;
        String site = "", plan = "";
        BlockPos dest;
        int close, stuck, lost, size;
        long huntStart;
        boolean begged;
        double best = Double.MAX_VALUE;
        long since;

        Team(UUID target, Forces.Unit unit, String by, int size) { this.target = target; this.unit = unit; this.by = by; this.size = size; }
    }

    private static final Map<UUID, Team> TEAMS = new HashMap<>();
    /** Breaches waiting for your answer, and when the chain of command stops waiting. */
    private static final Map<UUID, Long> PENDING = new HashMap<>();

    // ================================================================== the breach, and who decides

    static int severity(Containment.Entry e) {
        String c = e.cls.toLowerCase(Locale.ROOT);
        if (c.contains("safe")) return 0;
        if (c.contains("euclid")) return 1;
        return 2; // Keter, Apollyon, unclassified: the Council's problem
    }

    /** The orders the breach message offers. */
    static Career.Reply[] orders(Containment.Entry e) {
        String id = e.id.toString(), d = e.designation;
        return new Career.Reply[]{
                new Career.Reply("Send Site Security.", "resp:SECURITY:" + id, "Site Security is moving on " + d + ". They'll report when it's secured."),
                new Career.Reply("Send Epsilon-11.", "resp:EPSILON_11:" + id, "Nine-Tailed Fox is on its way. Recontainment is what they do."),
                new Career.Reply("Send Nu-7.", "resp:NU_7:" + id, "Hammer Down is deploying. Whatever's in their way won't be."),
                new Career.Reply("I'll handle it myself.", "resp:SELF:" + id, "Understood. Nobody moves on it without your word. Follow the arrow.")};
    }

    /** A breach just happened: if you don't answer, the chain of command will. */
    static void breached(MinecraftServer srv, Containment.Entry e) {
        if (TEAMS.containsKey(e.id)) return;
        PENDING.put(e.id, srv.overworld().getGameTime() + 600);
    }

    /** "UNIT:uuid" (or "SELF:uuid") from a reply. */
    public static void ordered(ServerPlayer p, String spec) {
        int c = spec.indexOf(':');
        if (c < 0) return;
        UUID id;
        try { id = UUID.fromString(spec.substring(c + 1)); } catch (IllegalArgumentException ex) { return; }
        PENDING.remove(id);
        String u = spec.substring(0, c);
        if (u.equals("SELF")) {
            Team t = TEAMS.remove(id);
            if (t != null) standDown(p.server, t);
            BreachMissions.status(p.server, id, "You're handling it yourself.");
            Mission mm = BreachMissions.find(Career.data(p), id);
            if (mm != null) BreachMissions.guide(p, mm, Containment.loaded(p.server, id));
            return;
        }
        if (u.equals("GUIDE")) {
            Mission m = BreachMissions.find(Career.data(p), id);
            if (m != null) BreachMissions.guide(p, m, Containment.loaded(p.server, id));
            BreachMissions.status(p.server, id, "!The Administrator is on the way. The team is holding it where it is.");
            return;
        }
        if (u.equals("RETRY")) {
            Team t = TEAMS.get(id);
            if (t != null) { t.huntStart = p.server.overworld().getGameTime(); t.begged = false; }
            BreachMissions.status(p.server, id, (t != null ? t.unit.title : "The team") + " is trying again.");
            return;
        }
        Forces.Unit unit = Forces.Unit.of(u);
        if (unit == null) return;
        Containment.Entry e = entry(p.server, id);
        if (e != null && e.status == Containment.LOOSE) dispatch(p.server, e, unit, "your order");
    }

    private static Containment.Entry entry(MinecraftServer srv, UUID id) {
        for (Containment.Entry e : Containment.all(srv)) if (e.id.equals(id)) return e;
        return null;
    }

    /** A team goes out after this anomaly. */
    public static void dispatch(MinecraftServer srv, Containment.Entry e, Forces.Unit unit, String by) {
        Team old = TEAMS.remove(e.id);
        if (old != null) standDown(srv, old);
        int size = switch (unit) { case SECURITY -> 3; case NU_7 -> 5; default -> 4; };
        Team t = new Team(e.id, unit, by, size);
        t.site = e.site;
        t.since = srv.overworld().getGameTime();
        TEAMS.put(e.id, t);
        BreachMissions.status(srv, e.id, unit.title + " is on it" + (by.equals("your order") ? "." : ", sent by " + (by.startsWith("The ") ? "the" + by.substring(3) : by) + "."));
    }

    // ================================================================== the team at work

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        MinecraftServer srv = event.getServer();
        if (srv.getTickCount() % 10 != 0) return;
        long now = srv.overworld().getGameTime();
        // nobody answered: the chain of command decides
        for (Iterator<Map.Entry<UUID, Long>> it = PENDING.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Long> en = it.next();
            if (now < en.getValue()) continue;
            it.remove();
            Containment.Entry e = entry(srv, en.getKey());
            if (e == null || e.status != Containment.LOOSE || TEAMS.containsKey(e.id)) continue;
            int sev = severity(e);
            String who = sev == 0 ? "The Site Director" : sev == 1 ? "The Security Director" : "The O5 Council";
            Forces.Unit u = TIERS[sev];
            dispatch(srv, e, u, who);
            tellAll(srv, e, sev == 2 ? O5.O5_6.sender() : (sev == 0 ? "Site Director" : "Security Director"),
                    who + " has sent " + u.title + " after " + e.designation + (sev == 2 ? ", with Epsilon-11 in support. Termination is authorised if there's nowhere to hold it." : ". They'll report when it's secured."));
            if (sev == 2) { Team t = TEAMS.get(e.id); if (t != null) t.size += 2; }
        }
        List<Team> escalations = new ArrayList<>();
        for (Iterator<Map.Entry<UUID, Team>> it = TEAMS.entrySet().iterator(); it.hasNext(); ) {
            Team t = it.next().getValue();
            try {
                if (!step(srv, t, now)) { it.remove(); standDown(srv, t); if (t.escalate != null) escalations.add(t); }
            } catch (Throwable ex) {
                BreachProtocol.LOGGER.warn("Breach response failed: {}", ex.toString());
                it.remove();
                standDown(srv, t);
            }
        }
        for (Team t : escalations) {
            Containment.Entry e = entry(srv, t.target);
            if (e != null && e.status == Containment.LOOSE) dispatch(srv, e, t.escalate, "escalation");
        }
    }

    /** @return false when the team is done (or gone). */
    private static boolean step(MinecraftServer srv, Team t, long now) {
        Containment.Entry e = entry(srv, t.target);
        Mob m = Containment.loaded(srv, t.target);
        if (e == null || t.phase == Phase.DONE) return false;
        if (m == null) {
            if (e.status == Containment.NEUTRALISED) return false;
            return now - t.since < 24000; // not loaded: they wait for it (a day at most)
        }
        if (Unleashed.isContained(m) && t.phase != Phase.ESCORTING) {
            report(srv, t, e, e.designation + " is back in containment. " + t.unit.title + " stands down" + losses(t) + ".");
            return false;
        }
        ServerLevel l = (ServerLevel) m.level();
        if (t.phase == Phase.WAITING) {
            if (!muster(l, t, e, m)) return true;
            t.phase = Phase.HUNTING;
        }
        List<FoundationStaff> team = alive(l, t);
        if (team.isEmpty()) {
            // wiped out: the next tier goes in
            int tier = Arrays.asList(TIERS).indexOf(t.unit);
            if (t.phase == Phase.ESCORTING) release(m);
            if (tier >= 0 && tier < TIERS.length - 1) {
                Forces.Unit next = TIERS[tier + 1];
                report(srv, t, e, t.unit.title + " was lost trying to bring in " + e.designation + ". " + next.title + " is going in.");
                BreachMissions.status(srv, e.id, t.unit.title + " was lost. " + next.title + " is going in.");
                t.escalate = next;
                return false;
            }
            BreachMissions.beg(srv, e.id, "O5-6", "We lost Nu-7 to " + e.designation + ". It's still loose. Nothing we have can bring it in. Administrator, it's you or nobody.",
                    new Career.Reply("I'm going.", "resp:GUIDE:" + e.id, "Follow the arrow. We'll keep everyone clear of it."));
            return false;
        }
        switch (t.phase) {
            case HUNTING -> hunt(l, t, e, m, team);
            case TERMINATING -> terminate(l, t, e, m, team, now);
            case ESCORTING -> { if (escort(l, t, e, m, team)) return false; }
            default -> {}
        }
        return true;
    }

    private static List<FoundationStaff> alive(ServerLevel l, Team t) {
        List<FoundationStaff> out = new ArrayList<>();
        for (Iterator<UUID> it = t.members.iterator(); it.hasNext(); ) {
            Entity x = l.getEntity(it.next());
            if (x instanceof FoundationStaff f && f.isAlive()) out.add(f);
            else if (x == null || !x.isAlive()) { if (x != null || l.getServer().overworld().getGameTime() - t.since > 200) { it.remove(); t.lost++; } }
        }
        return out;
    }

    /** The team assembles: stationed members first, the rest called in near the site's entrance. */
    private static boolean muster(ServerLevel l, Team t, Containment.Entry e, Mob m) {
        Sites.Site site = null;
        for (Sites.Site s : Sites.get(l.getServer()).sites) if (s.name.equals(e.site)) site = s;
        Set<UUID> busy = new HashSet<>();
        for (Team o : TEAMS.values()) busy.addAll(o.members);
        if (site != null) for (UUID id : Forces.members(l.getServer(), site.name, t.unit)) {
            if (t.members.size() >= t.size) break;
            if (busy.contains(id)) continue;
            if (l.getEntity(id) instanceof FoundationStaff f && f.isAlive()) t.members.add(id);
        }
        while (t.members.size() < t.size) {
            FoundationStaff g = null;
            if (site != null && site.dim.equals(l.dimension().location().toString())) g = Forces.call(l, site, t.unit);
            if (g == null) {
                // no site of ours here: they come in from the edge of sight
                g = com.breachprotocol.registry.ModEntities.STAFF.get().create(l);
                if (g == null) break;
                Vec3 from = m.position().add(new Vec3(1, 0, 0).yRot(l.random.nextFloat() * 6.28F).scale(28));
                BlockPos at = l.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(from));
                g.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
                g.setupUnit(t.unit, at);
                l.addFreshEntity(g);
            }
            t.members.add(g.getUUID());
        }
        // a response team may go where it has to, cells included
        for (UUID id : t.members) if (l.getEntity(id) instanceof FoundationStaff f) f.getPersistentData().putBoolean("bp_cell_ok", true);
        return !t.members.isEmpty();
    }

    /** Find it, close in, get hands on it. */
    private static void hunt(ServerLevel l, Team t, Containment.Entry e, Mob m, List<FoundationStaff> team) {
        long now = l.getGameTime();
        if (t.huntStart == 0) t.huntStart = now;
        // three minutes and they still can't lay hands on it: they ask for you
        if (!t.begged && now - t.huntStart > 20 * 180) {
            t.begged = true;
            String id = e.id.toString();
            BreachMissions.beg(l.getServer(), e.id, t.unit == Forces.Unit.SECURITY ? "Site Security" : t.unit.prefix + ", " + t.unit.title.replaceAll(" \".*", ""),
                    "We can't get near " + e.designation + ". We've been on it three minutes and it's beyond us. Administrator, we need you here. Please.",
                    new Career.Reply("Hold it there. I'm coming.", "resp:GUIDE:" + id, "Yes, sir. Follow the arrow. We'll keep it pinned."),
                    new Career.Reply("Keep at it.", "resp:RETRY:" + id, "...Understood. Going back in."),
                    new Career.Reply("Pull out. I'll handle it.", "resp:SELF:" + id, "Pulling back. It's yours."));
        }
        boolean near = false;
        for (FoundationStaff f : team) {
            double d = f.distanceToSqr(m);
            if (d > 90 * 90) {
                // a long way off: they've caught up by the time anyone would notice
                Vec3 at = m.position().add(new Vec3(1, 0, 0).yRot(l.random.nextFloat() * 6.28F).scale(18));
                BlockPos b = l.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(at));
                f.teleportTo(b.getX() + 0.5, b.getY(), b.getZ() + 0.5);
            } else f.getNavigation().moveTo(m, 1.25);
            if (d < 3.2 * 3.2) near = true;
        }
        double d = team.get(0).distanceToSqr(m);
        if (d < t.best - 1) { t.best = d; t.stuck = 0; } else t.stuck++;
        if (near) t.close++; else t.close = Math.max(0, t.close - 1);
        // two seconds with hands on it (or half a minute cornering it): it's theirs
        if (t.close >= 4 || t.stuck > 60 && d < 10 * 10) secure(l, t, e, m, team);
    }

    /** It's caught. Now: where does it go? */
    private static void secure(ServerLevel l, Team t, Containment.Entry e, Mob m, List<FoundationStaff> team) {
        MinecraftServer srv = l.getServer();
        String dim = l.dimension().location().toString();
        Sites.Cell cell = null;
        // its own cell
        for (Sites.Cell c : Sites.get(srv).cells) if (c.name.equals(e.cell) && c.dim.equals(dim) && c.entity.equals(e.type)) cell = c;
        // or a designated empty one at its site
        if (cell == null) {
            Sites.Site site = Sites.at(l, m.blockPosition());
            for (Sites.Cell c : Sites.get(srv).cells) {
                if (!c.entity.isEmpty() || !c.dim.equals(dim)) continue;
                Sites.Site cs = Sites.at(l, new BlockPos((c.x0 + c.x1) / 2, c.y0 + 1, (c.z0 + c.z1) / 2));
                if (site == null || cs == site) { cell = c; c.entity = e.type; Sites.get(srv).setDirty(); break; }
            }
        }
        if (cell != null) {
            hold(m);
            t.plan = cell.name;
            t.dest = new BlockPos((cell.x0 + cell.x1) / 2, cell.y0 + 1, (cell.z0 + cell.z1) / 2);
            t.phase = Phase.ESCORTING;
            t.stuck = 0;
            t.best = Double.MAX_VALUE;
            report(srv, t, e, t.unit.title + " has " + e.designation + " secured. Bringing it to " + cell.name + ".");
            BreachMissions.status(srv, e.id, "Secured by " + t.unit.title + ". Being taken to " + cell.name + ".");
            return;
        }
        // nowhere to hold it, and it's Keter: the Council's authority is to end it, if it can be ended
        if (severity(e) == 2 && !undying(m)) {
            t.phase = Phase.TERMINATING;
            t.since = l.getGameTime();
            report(srv, t, e, "There's no cell for " + e.designation + ". Termination authorised. " + t.unit.title + " is opening fire.");
            BreachMissions.status(srv, e.id, t.unit.title + " is terminating it.");
            return;
        }
        // Site-01's reserved cells
        if (l.dimension() == net.minecraft.world.level.Level.OVERWORLD && Site01.data(srv).built) {
            Containment.order(srv, e.id, "transfer");
            if (e.status == Containment.HELD && e.site.equals("Site-01")) {
                report(srv, t, e, t.unit.title + " secured " + e.designation + " and handed it to Site-01" + losses(t) + ".");
                t.phase = Phase.DONE;
                return;
            }
        }
        // truly nowhere: they build somewhere
        improvise(l, t, e, m);
    }

    private static boolean undying(Mob m) {
        String p = Unleashed.idOf(m) == null ? "" : Unleashed.idOf(m).getPath();
        return UnleashedApi.isShyGuy(m) || p.startsWith("scp682") || p.startsWith("scp106") || p.startsWith("scp173") || p.startsWith("scp096");
    }

    private static void terminate(ServerLevel l, Team t, Containment.Entry e, Mob m, List<FoundationStaff> team, long now) {
        for (FoundationStaff f : team) {
            if (f.distanceToSqr(m) > 12 * 12) f.getNavigation().moveTo(m, 1.2);
            f.setTarget(m);
        }
        // a minute of fire and it's still standing: it can't be killed, so it gets a cell built round it
        if (now - t.since > 1200 && m.isAlive()) {
            report(l.getServer(), t, e, e.designation + " won't die. " + t.unit.title + " is building a cell round it instead.");
            improvise(l, t, e, m);
        }
    }

    /** Hold it fast: it goes where the team takes it. */
    private static void hold(Mob m) {
        m.getPersistentData().putBoolean(SECURED, true);
        m.setNoAi(true);
        m.setTarget(null);
        m.getNavigation().stop();
    }

    private static void release(Mob m) {
        m.getPersistentData().remove(SECURED);
        m.setNoAi(false);
    }

    /** Walk it in. @return true when it's in. */
    private static boolean escort(ServerLevel l, Team t, Containment.Entry e, Mob m, List<FoundationStaff> team) {
        FoundationStaff lead = team.get(0);
        // it comes along behind the lead, held
        Vec3 behind = lead.position().subtract(lead.getLookAngle().multiply(1.6, 0, 1.6));
        if (m.position().distanceToSqr(behind) > 0.5) m.teleportTo(behind.x, lead.getY(), behind.z);
        m.setDeltaMovement(Vec3.ZERO);
        lead.getNavigation().moveTo(t.dest.getX() + 0.5, t.dest.getY(), t.dest.getZ() + 0.5, 1.0);
        for (int i = 1; i < team.size(); i++) team.get(i).getNavigation().moveTo(lead, 1.1);
        double d = lead.distanceToSqr(t.dest.getX() + 0.5, t.dest.getY(), t.dest.getZ() + 0.5);
        if (d < t.best - 1) { t.best = d; t.stuck = 0; } else t.stuck++;
        if (d < 4 * 4 || t.stuck > 90) {
            // through the door and shut in
            m.teleportTo(t.dest.getX() + 0.5, t.dest.getY(), t.dest.getZ() + 0.5);
            release(m);
            m.setPersistenceRequired();
            l.playSound(null, t.dest, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 1F, 0.8F);
            report(l.getServer(), t, e, e.designation + " is in " + t.plan + ". " + t.unit.title + " stands down" + losses(t) + ".");
            return true;
        }
        return false;
    }

    /**
     * Nowhere to put it: the team builds a holding cell. On the site if it's on one (by the site's
     * edge), otherwise where it stands; containment panels on every side, a floor and a ceiling,
     * registered as a cell for it.
     */
    private static void improvise(ServerLevel l, Team t, Containment.Entry e, Mob m) {
        MinecraftServer srv = l.getServer();
        int n = 1;
        for (Sites.Cell c : Sites.get(srv).cells) if (c.name.startsWith("HOLDING-")) n++;
        String name = "HOLDING-" + n;
        int w = Math.max(5, (int) Math.ceil(m.getBbWidth()) + 4), h = Math.max(5, (int) Math.ceil(m.getBbHeight()) + 3);
        BlockPos c = m.blockPosition();
        BlockPos a = c.offset(-w / 2, -1, -w / 2), b = a.offset(w - 1, h - 1, w - 1);
        var wall = Unleashed.block("containment_room_wall_middle", net.minecraft.world.level.block.Blocks.IRON_BLOCK).defaultBlockState();
        var floor = Unleashed.block("containment_room_floor", net.minecraft.world.level.block.Blocks.SMOOTH_STONE).defaultBlockState();
        var ceil = Unleashed.block("containment_room_ceiling", net.minecraft.world.level.block.Blocks.SMOOTH_STONE).defaultBlockState();
        var lamp = Unleashed.block("containment_room_lamp", net.minecraft.world.level.block.Blocks.SEA_LANTERN).defaultBlockState();
        for (BlockPos q : BlockPos.betweenClosed(a, b)) {
            boolean edge = q.getX() == a.getX() || q.getX() == b.getX() || q.getZ() == a.getZ() || q.getZ() == b.getZ();
            if (q.getY() == a.getY()) l.setBlock(q, floor, 3);
            else if (q.getY() == b.getY()) l.setBlock(q, ceil, 3);
            else if (edge) l.setBlock(q, wall, 3);
            else if (!l.getBlockState(q).isAir()) l.setBlock(q, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        }
        l.setBlock(new BlockPos(c.getX(), b.getY() - 1, c.getZ()), lamp, 3);
        Sites.addCell(l, name, e.type, a, b);
        m.teleportTo(c.getX() + 0.5, a.getY() + 1, c.getZ() + 0.5);
        release(m);
        m.setPersistenceRequired();
        l.sendParticles(ParticleTypes.CLOUD, c.getX() + 0.5, c.getY() + 1, c.getZ() + 0.5, 30, w / 2.0, h / 2.0, w / 2.0, 0.02);
        l.playSound(null, c, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 1F, 0.6F);
        t.phase = Phase.DONE;
        report(srv, t, e, "There was nowhere to hold " + e.designation + ", so " + t.unit.title + " built a cell round it: " + name + " at " + c.getX() + ", " + c.getZ() + ". It's held" + losses(t) + ". Find it a proper home when you can.");
    }

    private static String losses(Team t) { return t.lost == 0 ? ", no losses" : ", " + t.lost + " lost"; }

    /** Back to their posts. */
    private static void standDown(MinecraftServer srv, Team t) {
        for (ServerLevel l : srv.getAllLevels())
            for (UUID id : t.members) if (l.getEntity(id) instanceof FoundationStaff f && f.isAlive()) { f.setTarget(null); f.getPersistentData().remove("bp_cell_ok"); f.order("post", null); }
        Mob m = Containment.loaded(srv, t.target);
        if (m != null && m.getPersistentData().getBoolean(SECURED)) release(m);
    }

    private static void report(MinecraftServer srv, Team t, Containment.Entry e, String text) {
        tellAll(srv, e, t.unit == Forces.Unit.SECURITY ? "Site Security" : t.unit.title.contains("\"") ? t.unit.title.substring(0, t.unit.title.indexOf('"')).trim() : t.unit.title, text);
    }

    private static void tellAll(MinecraftServer srv, Containment.Entry e, String from, String text) {
        for (ServerPlayer q : srv.getPlayerList().getPlayers())
            if (Career.rank(q) == Rank.ADMINISTRATOR || Career.rank(q).atLeast(Rank.OVERSEER) || Career.isAscended(q))
                Career.message(q, from, text);
    }

    /** Team members shoot the anomaly only if the order is to terminate it; while capturing they hold fire. */
    public static Boolean threatFor(FoundationStaff g, LivingEntity c) {
        Team t = TEAMS.get(c.getUUID());
        if (t == null || !t.members.contains(g.getUUID())) return null;
        return t.phase == Phase.TERMINATING;
    }

    /** Anything left held by a team when the server stopped is let go when it loads. */
    @SubscribeEvent
    public static void joined(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Mob m) || !m.getPersistentData().getBoolean(SECURED)) return;
        if (!TEAMS.containsKey(m.getUUID())) release(m);
    }
}
