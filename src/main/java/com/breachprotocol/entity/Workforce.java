package com.breachprotocol.entity;

import com.breachprotocol.career.*;
import com.breachprotocol.career.Calendar;
import com.breachprotocol.network.DialoguePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;

/**
 * The Staff app (phone and terminal): everyone who works for the Foundation, by site and
 * department, with their file; the open posts and shortlists (hire directly, no meeting needed);
 * the org chart; the registry; and the pulse of the workforce. Everything it does goes through the
 * same systems as the world: a note you send is remembered, a hire walks to their post.
 */
public final class Workforce {
    private Workforce() {}

    static boolean hires(ServerPlayer p) { return Management.mayAttend(p); }

    static boolean appoints(ServerPlayer p) { return Management.mayAppoint(p); }

    private static int deptOrder(Registry.Rec e) {
        String j = e.job.toLowerCase(Locale.ROOT);
        return j.contains("secretary") ? 0 : j.contains("security") || j.contains("mtf") || j.contains("task") || j.contains("nu-7") || j.contains("epsilon") || j.contains("alpha") ? 1
                : j.contains("research") ? 2 : j.contains("medical") ? 3 : j.contains("technician") ? 4 : 5;
    }

    /** The whole directory, as the app shows it. */
    public static void send(ServerPlayer p) {
        MinecraftServer s = p.server;
        long now = s.overworld().getGameTime();
        Registry reg = Registry.get(s);
        Management.Data md = Management.data(s);
        Set<UUID> heads = new HashSet<>(md.heads.values());
        Map<UUID, String> headTitle = new HashMap<>();
        for (Map.Entry<String, UUID> e : md.heads.entrySet()) {
            String d = e.getKey().substring(e.getKey().indexOf('|') + 1);
            try { headTitle.put(e.getValue(), Management.Dept.valueOf(d).title); } catch (IllegalArgumentException ignored) {}
        }
        List<Registry.Rec> staff = new ArrayList<>();
        for (Registry.Rec e : reg.people.values()) if (e.status.equals("active") && !e.player && e.alive()) staff.add(e);
        staff.sort(Comparator.comparing((Registry.Rec e) -> e.site).thenComparing(e -> heads.contains(e.id) ? 0 : 1).thenComparingInt(Workforce::deptOrder).thenComparing(e -> e.name));
        ListTag people = new ListTag();
        int births = 0, deaths = 0, weddings = 0, hiresN = 0;
        for (Registry.Rec e : staff) {
            CompoundTag t = new CompoundTag();
            t.putString("id", e.id.toString());
            t.putString("name", e.name);
            t.putString("job", e.job);
            t.putString("site", e.site.isEmpty() ? "Unassigned" : e.site);
            t.putInt("age", e.age(now));
            t.putBoolean("f", e.female);
            if (headTitle.containsKey(e.id)) t.putString("head", headTitle.get(e.id));
            t.putString("rel", e.rel);
            Registry.Rec sp = Registry.find(s, e.spouse);
            if (sp != null) t.putString("spouse", sp.name + (sp.alive() ? "" : " (late)"));
            t.putInt("kids", Registry.children(s, e.id).size());
            List<String> tr = new ArrayList<>();
            for (Traits x : e.traitSet()) if (!x.about.isEmpty() && x != Traits.INFERTILE) tr.add(x.label);
            t.putString("traits", String.join(", ", tr));
            t.putString("pers", Personality.values()[Math.min(13, (int) (Math.max(0, Registry.gene(e.genome, 20)) * 14))].label);
            if (e.due > 0) t.putBoolean("expecting", true);
            FoundationStaff f = Lifecycle.entity(s, e.id);
            if (f != null) {
                t.putBoolean("here", true);
                t.putInt("hearts", Convo.trust(f, p));
                t.putInt("respect", Social.respect(f, p));
                t.putString("pers", Personality.of(f).label);
                t.putInt("years", Math.min(Talk.years(f), Math.max(1, f.age() - 21)));
                String state = !f.illness().isEmpty() ? "Ill: " + f.illness() : Lifecycle.bereaved(f) || Social.widowedRecently(f) ? "Grieving" : Talk.anomalyNear(f, 16) != null ? "In danger" : Seats.seated(f) ? "Seated" : "On duty";
                t.putString("state", state);
                t.putInt("dist", (int) Math.sqrt(f.distanceToSqr(p)));
                if (Social.partner(f, p)) t.putBoolean("partner", true);
                if (Lifecycle.marriedTo(f, p)) t.putBoolean("spouseOfYou", true);
            } else t.putString("state", "Away");
            long day = s.overworld().getDayTime() / 24000;
            t.putBoolean("noted", e.notes.contains(p.getUUID() + "@" + day));
            people.add(t);
        }
        // the week's life events
        for (String l : reg.log) {
            int d;
            try { d = Integer.parseInt(l.substring(4, l.indexOf(':'))); } catch (Exception ex) { continue; }
            if (s.overworld().getDayTime() / 24000 + 1 - d > 7) break;
            if (l.contains("welcomed")) births++;
            else if (l.contains("married")) weddings++;
            else if (l.contains("died") || l.contains("killed") || l.contains("terminated")) deaths++;
            else if (l.contains("hired")) hiresN++;
        }
        ListTag vac = new ListTag();
        for (Management.Vacancy v : Management.open(s)) {
            CompoundTag t = new CompoundTag();
            t.putInt("id", v.id);
            t.putString("what", v.what());
            t.putString("site", v.site.isEmpty() ? "Unassigned" : v.site);
            t.putString("kind", v.kind);
            t.putString("state", v.state);
            t.putInt("attend", v.attend);
            t.putString("when", Calendar.when(v.at));
            t.putString("left", v.left);
            ListTag cs = new ListTag();
            for (Tag x : v.cands) {
                CompoundTag c = (CompoundTag) x, o = new CompoundTag();
                o.putString("name", c.getString("name"));
                o.putInt("age", c.getInt("age"));
                o.putInt("stars", Math.max(1, c.getInt("comp") / 2));
                o.putString("bg", c.getString("bg"));
                o.putString("pers", Personality.values()[Math.min(13, (int) (Person.geneOf(c.getUUID("id"), 20) * 14))].label);
                if (c.contains("family")) o.putString("family", c.getString("family"));
                o.putBoolean("internal", c.getBoolean("internal"));
                if (c.getBoolean("vetted")) o.putString("vet", c.getBoolean("flagged") ? "FLAGGED: ties to " + c.getString("mole") : "Clear");
                List<String> tr = new ArrayList<>();
                for (Traits t2 : Traits.read(c.getString("traits"))) if (!t2.about.isEmpty()) tr.add(t2.label);
                if (c.getBoolean("vetted")) o.putString("med", tr.isEmpty() ? "Nothing of note" : String.join(", ", tr));
                cs.add(o);
            }
            t.put("cands", cs);
            vac.add(t);
        }
        // the org chart: who heads what, where, and how many they have
        ListTag org = new ListTag();
        Map<String, int[]> counts = new TreeMap<>();
        for (Registry.Rec e : staff) counts.computeIfAbsent(e.site.isEmpty() ? "Unassigned" : e.site, k -> new int[6])[deptOrder(e)]++;
        for (Map.Entry<String, int[]> e : counts.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putString("site", e.getKey());
            t.putIntArray("n", e.getValue());
            CompoundTag hd = new CompoundTag();
            for (Management.Dept d : Management.Dept.values()) {
                UUID h = md.heads.get(e.getKey() + "|" + d.name());
                if (h != null) { hd.putString(d.name(), md.headNames.getOrDefault(e.getKey() + "|" + d.name(), "")); hd.putString(d.name() + "_id", h.toString()); }
            }
            t.put("heads", hd);
            org.add(t);
        }
        ListTag sites = new ListTag();
        for (Sites.Site x : Sites.get(s).sites) sites.add(StringTag.valueOf(x.name));
        ListTag log = new ListTag();
        for (String l : Registry.recentLog(s, 40)) log.add(StringTag.valueOf(l));
        CompoundTag out = new CompoundTag();
        out.putBoolean("staffdir", true);
        out.put("people", people);
        out.put("vac", vac);
        out.put("org", org);
        out.put("sites", sites);
        out.put("log", log);
        out.putBoolean("hires", hires(p));
        out.putBoolean("appoints", appoints(p));
        out.putInt("births", births);
        out.putInt("deaths", deaths);
        out.putInt("weddings", weddings);
        out.putInt("hired", hiresN);
        int morale = WorldNews.morale(s);
        out.putInt("morale", Math.max(0, Math.min(100, 50 + morale * 8 + (Career.data(p).stability - 50) / 3)));
        PacketDistributor.sendToPlayer(p, new DialoguePayload(out));
    }

    private static UUID uuid(String s) { try { return UUID.fromString(s); } catch (IllegalArgumentException e) { return null; } }

    /** An action from the app ("wf:..."). */
    public static void action(ServerPlayer p, String a) {
        MinecraftServer s = p.server;
        String[] x = a.split(":");
        String cmd = x[0];
        try {
            switch (cmd) {
                case "refresh" -> {}
                case "note" -> note(p, uuid(x[1]), x[2]);
                case "summon" -> {
                    FoundationStaff f = Lifecycle.entity(s, uuid(x[1]));
                    if (f == null) toast(p, "They're not on site right now.");
                    else if (f.distanceTo(p) > 160) toast(p, f.getName().getString() + " is too far away to come. (" + (int) f.distanceTo(p) + " m)");
                    else { f.order("follow", p); Memory.put(f, p, "last_order", "follow"); toast(p, f.getName().getString() + " is on the way to you."); }
                }
                case "release" -> {
                    FoundationStaff f = Lifecycle.entity(s, uuid(x[1]));
                    if (f != null) { f.order("post", p); Memory.put(f, p, "last_order", "post"); toast(p, f.getName().getString() + " is going back to work."); }
                }
                case "tree" -> { Registry.Rec e = Registry.find(s, uuid(x[1])); if (e != null) { FamilyTree.send(p, e); return; } }
                case "head" -> {
                    if (!appoints(p)) { toast(p, "Only the Council appoints heads of department."); break; }
                    FoundationStaff f = Lifecycle.entity(s, uuid(x[1]));
                    if (f == null) { toast(p, "They need to be on site to accept."); break; }
                    Management.Dept d = Management.Dept.of(f.role());
                    if (d == null) break;
                    Management.appoint(s, Registry.of(f).site, d, f, "the Administrator");
                    toast(p, f.getName().getString() + " is now " + d.title + ".");
                }
                case "dismiss" -> {
                    if (!hires(p)) break;
                    FoundationStaff f = Lifecycle.entity(s, uuid(x[1]));
                    if (f == null) { toast(p, "They need to be on site."); break; }
                    Registry.Rec e = Registry.of(f);
                    e.status = "offsite";
                    e.job = "former " + e.job.toLowerCase(Locale.ROOT);
                    e.note = "dismissed (Class-A amnestics, a generous pension)";
                    Registry.log(s, e.name + " was dismissed by " + (Talk.topBrass(p) ? "the Administrator" : p.getGameProfile().getName()) + ". Standard amnestic exit.");
                    WorldNews.record(s, WorldNews.Kind.HIRE, "dismissed", e.name + " was let go. They won't remember why", -1, "");
                    toast(p, e.name + " has been dismissed. Personnel will open the post.");
                    f.leaveService();
                }
                case "post" -> {
                    if (!hires(p)) break;
                    FoundationStaff.Role role = FoundationStaff.Role.values()[Integer.parseInt(x[2])];
                    Management.Vacancy v = Management.direct(p, x[1], role);
                    toast(p, v == null ? "No post for that at " + x[1] + " (nobody of that role works there to copy a desk from)." : "Personnel has a shortlist of three for " + v.what() + ".");
                }
                case "pick" -> {
                    Management.Vacancy v = Management.byId(s, Integer.parseInt(x[1]));
                    int i = Integer.parseInt(x[2]);
                    if (v == null || i < 0 || i >= v.cands.size() || v.state.equals("done")) break;
                    if (v.kind.equals("head") ? !appoints(p) : !hires(p)) { toast(p, "That isn't your decision to make."); break; }
                    String n = v.cands.getCompound(i).getString("name");
                    Management.decide(s, v, v.cands.getCompound(i));
                    toast(p, n + (v.kind.equals("head") ? " has been appointed." : " is hired and on the way to their post."));
                }
                case "vet" -> {
                    Management.Vacancy v = Management.byId(s, Integer.parseInt(x[1]));
                    int i = Integer.parseInt(x[2]);
                    if (v == null || i < 0 || i >= v.cands.size()) break;
                    CompoundTag c = v.cands.getCompound(i);
                    if (!c.getBoolean("vetted")) {
                        c.putBoolean("vetted", true);
                        c.putBoolean("flagged", !c.getString("mole").isEmpty() && new Random().nextInt(100) < (Talk.topBrass(p) ? 90 : 70));
                        Management.data(s).setDirty();
                    }
                }
                case "attend", "delegate", "now", "cancel" -> {
                    Management.Vacancy v = Management.byId(s, Integer.parseInt(x[1]));
                    if (v == null || !hires(p)) break;
                    switch (cmd) {
                        case "attend" -> { v.attend = 1; if (v.state.equals("direct") || v.kind.equals("head") && v.state.equals("open")) { v.state = "booked"; } v.at = Management.slot(s); toast(p, "The applicants will be in your office " + Calendar.when(v.at) + "."); }
                        case "delegate" -> { v.attend = 2; if (v.state.equals("direct")) { v.state = "booked"; v.at = Management.slot(s); } toast(p, "The head of department will decide."); }
                        case "now" -> { v.attend = 1; v.state = "booked"; v.at = s.overworld().getDayTime(); toast(p, "The applicants are on their way up."); }
                        case "cancel" -> { v.state = "done"; toast(p, "The posting is withdrawn."); }
                    }
                    Management.data(s).setDirty();
                }
                default -> {}
            }
        } catch (RuntimeException e) {
            com.breachprotocol.BreachProtocol.LOGGER.error("Staff app action {} failed", a, e);
        }
        send(p);
    }

    /** A note from you: they read it (now, or when they're next on site), and it stays with them. */
    static void note(ServerPlayer p, UUID id, String kind) {
        MinecraftServer s = p.server;
        Registry.Rec e = Registry.find(s, id);
        if (e == null) return;
        long day = s.overworld().getDayTime() / 24000;
        String stamp = p.getUUID() + "@" + day;
        if (e.notes.contains(stamp)) { toast(p, "You've already written to " + e.name + " today."); return; }
        e.notes = (e.notes.isEmpty() ? "" : e.notes + ";") + stamp + "=" + kind;
        Registry.dirty(s);
        FoundationStaff f = Lifecycle.entity(s, id);
        if (f != null) deliver(f);
        toast(p, switch (kind) {
            case "commend" -> "Commendation sent to " + e.name + ".";
            case "thanks" -> "Thank-you note sent to " + e.name + ".";
            case "concern" -> "A note of concern sent to " + e.name + ".";
            default -> "Formal warning issued to " + e.name + ".";
        });
    }

    /** Notes waiting for them take effect: hearts, memory, and they'll bring it up. */
    static void deliver(FoundationStaff f) {
        MinecraftServer s = f.getServer();
        Registry.Rec e = Registry.find(s, f.getUUID());
        if (e == null || e.notes.isEmpty()) return;
        List<String> keep = new ArrayList<>();
        for (String n : e.notes.split(";")) {
            int at = n.indexOf('@'), eq = n.indexOf('=');
            if (at < 0) continue;
            if (eq < 0 || n.endsWith("=done")) { keep.add(n.replaceAll("=.*$", "") + "=done"); continue; }
            String who = n.substring(0, at), kind = n.substring(eq + 1);
            CompoundTag root = f.getPersistentData();
            String mk = "bp_mem_" + who;
            if (!root.contains(mk)) root.put(mk, new CompoundTag());
            root.getCompound(mk).putString("wf_note", kind);
            root.getCompound(mk).putLong("t_wf_note", s.overworld().getGameTime());
            int t = root.getInt("bp_trust_" + who);
            int dt = switch (kind) { case "commend" -> 2; case "thanks", "concern" -> 1; default -> -2; };
            root.putInt("bp_trust_" + who, Math.max(0, Math.min(10, t + dt)));
            keep.add(n.substring(0, eq) + "=done");
        }
        // only today's stamps are needed to stop duplicates
        long day = s.overworld().getDayTime() / 24000;
        keep.removeIf(n -> !n.contains("@" + day));
        e.notes = String.join(";", keep);
        Registry.dirty(s);
    }

    private static void toast(ServerPlayer p, String msg) {
        p.displayClientMessage(net.minecraft.network.chat.Component.literal("▣ " + msg).withStyle(net.minecraft.ChatFormatting.AQUA), true);
    }
}
