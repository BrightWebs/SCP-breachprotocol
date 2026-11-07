package com.breachprotocol.entity;

import com.breachprotocol.career.*;
import com.breachprotocol.world.Site01;
import com.breachprotocol.world.SiteTower;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;

import java.util.*;

/**
 * Everything a member of staff knows about the moment they're talking to you in: who you are to
 * them (your rank), where you both are (the floor, the zone, the site), what's going on (attacks,
 * anomalies, the news, with when it happened), and how they feel about you. Every line they say is
 * built from this, so it always fits.
 */
final class Scene {
    /** How they see you. */
    enum Tier { DCLASS, JUNIOR, STAFF, DIRECTOR, COUNCIL, ADMIN }

    /** Where you are. {@code kind} drives what people remark on. */
    record Place(String name, String kind, int storey) {}

    final ServerPlayer p;
    final FoundationStaff s;
    final Tier tier;
    final Place place;
    final Sites.Site site;
    final Defence.Alert alert;
    final Mob loose;
    final Talk.Temper temper;
    final int morale, trust;
    final Random r = new Random();

    private Scene(ServerPlayer p, FoundationStaff s) {
        this.p = p;
        this.s = s;
        this.tier = tier(p);
        this.site = Sites.at(s.level(), s.blockPosition());
        this.place = place(s, site);
        this.alert = site == null ? null : Defence.alertAt(site.name);
        this.loose = Talk.anomalyNear(s, 24);
        this.temper = Talk.temper(s);
        this.morale = WorldNews.morale(p.server) + Career.data(p).stability / 20 - 2;
        this.trust = Convo.trust(s, p);
    }

    static Scene of(ServerPlayer p, FoundationStaff s) { return new Scene(p, s); }

    static Tier tier(ServerPlayer p) {
        if (Career.isAscended(p) || Career.rank(p) == Rank.ADMINISTRATOR) return Tier.ADMIN;
        return switch (Career.rank(p)) {
            case CLASS_D -> Tier.DCLASS;
            case LEVEL_1, LEVEL_2 -> Tier.JUNIOR;
            case LEVEL_3, LEVEL_4 -> Tier.STAFF;
            case LEVEL_5 -> Tier.DIRECTOR;
            case OVERSEER -> Tier.COUNCIL;
            default -> Tier.ADMIN;
        };
    }

    boolean high() { return tier.ordinal() >= Tier.DIRECTOR.ordinal(); }

    boolean brass() { return tier == Tier.COUNCIL || tier == Tier.ADMIN; }

    String addr() { return Career.address(p); }

    String pick(String... o) { return o[r.nextInt(o.length)]; }

    // ================================================================== where

    private static final String[] FLOORS = {"the D-Class block", "Deep Containment", "Keter Containment", "Heavy Containment", "Light Containment",
            "the lobby", "Research", "Medical", "Security", "the Archive", "the offices", "the server floor", "Launch Control", "the Council floor", "the Administrator's office", "the roof"};
    private static final String[] KINDS = {"dclass", "deep", "keter", "heavy", "light", "lobby", "research", "medical", "security", "archive", "offices", "servers", "launch", "councilfloor", "adminoffice", "roof"};

    static Place place(FoundationStaff s, Sites.Site site) {
        if (com.breachprotocol.garden.Garden.inside(s.level())) return new Place("the Garden", "garden", 0);
        BlockPos b = s.blockPosition();
        if (Site01.isInside(s.level(), b)) {
            BlockPos o = Site01.origin(s.getServer().overworld());
            BlockPos rel = b.subtract(o);
            if (rel.getX() >= 17 && rel.getX() <= 37 && rel.getZ() >= 1 && rel.getZ() <= 17) return new Place("the Council chamber", "chamber", 0);
            return new Place("Site-01", "site01", 0);
        }
        if (site == null) return new Place("out here", "outside", 0);
        int ox = site.x - SiteTower.T / 2, oz = site.z - SiteTower.T / 2;
        boolean inTower = b.getX() >= ox - 8 && b.getX() <= ox + 41 && b.getZ() >= oz - 10 && b.getZ() <= oz + SiteTower.T;
        if (!inTower || b.getY() < site.y - 31) return new Place("the grounds of " + site.name, "grounds", 0);
        int storey = Math.floorDiv(b.getY() - site.y, SiteTower.STOREY);
        int i = Math.max(0, Math.min(FLOORS.length - 1, storey + 5));
        return new Place(FLOORS[i], KINDS[i], storey);
    }

    /** "here in Heavy Containment", "here on the Research floor", "out here". */
    String here() {
        return switch (place.kind) {
            case "outside" -> "out here";
            case "grounds" -> "out on the grounds";
            case "research", "medical", "security", "councilfloor" -> "here on the " + place.name.replace("the ", "") + " floor";
            case "dclass", "lobby", "archive", "offices", "servers", "adminoffice", "roof", "chamber", "garden" -> "here in " + place.name;
            default -> "down here in " + place.name;
        };
    }

    /** The nearest containment cell to them, if any is close. */
    Sites.Cell cell() {
        Sites.Cell best = null;
        double bd = 24 * 24;
        for (Sites.Cell c : Sites.get(p.server).cells) {
            if (c.entity.isEmpty() || !s.level().dimension().location().toString().equals(c.dim)) continue;
            double d = s.blockPosition().distSqr(new BlockPos((c.x0 + c.x1) / 2, (c.y0 + c.y1) / 2, (c.z0 + c.z1) / 2));
            if (d < bd) { bd = d; best = c; }
        }
        return best;
    }

    // ================================================================== when, and what

    /** Natural time words: "a few minutes ago", "this morning", "yesterday", "three days ago", "last week". */
    static String when(MinecraftServer srv, long time) { return WorldNews.ago(srv, time); }

    String when(WorldNews.Item i) { return when(p.server, i.time()); }

    /** An event as a sentence: "The Council signed the termination order on SCP-096 six days ago." */
    String event(WorldNews.Item i) { return cap(i.headline()) + " " + when(i) + "."; }

    static String cap(String s) { return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1); }

    /** What they'd have heard (staff of different rank hear different things). */
    List<WorldNews.Item> news() {
        List<WorldNews.Item> out = new ArrayList<>();
        for (WorldNews.Item i : WorldNews.recent(p.server, 10)) if (!Talk.mine(i, p) && Clearance.knows(tier, i)) out.add(i);
        return out;
    }

    /** How they pass news on, by who you are to them. */
    String relay(WorldNews.Item i) {
        String e = event(i);
        return switch (tier) {
            case DCLASS -> pick("They don't tell your lot much. But: ", "You didn't hear this from me. ", "Word goes round the block eventually. ") + e;
            case JUNIOR -> pick("Did you hear? ", "So, apparently: ", "Get this. ") + e;
            case STAFF -> pick("You'll have seen the bulletin. ", "In case you missed it: ", "Word is ") + e;
            case DIRECTOR -> pick("For your report, Director: ", "Director, you'll want to know: ") + e;
            default -> pick("If I may, " + addr() + ": ", "For the record, " + addr() + ": ", "You'll know already, but: ") + e;
        };
    }
}
