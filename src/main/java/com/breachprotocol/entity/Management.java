package com.breachprotocol.entity;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.career.*;
import com.breachprotocol.career.Calendar;
import com.breachprotocol.world.SiteTower;
import com.breachprotocol.registry.ModEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.*;

/**
 * The management layer under the Council. Every site has department heads (Security, Research,
 * Medical, Engineering, Personnel). When someone leaves (dies, retires, is reclassified), their head
 * opens a vacancy, draws up a shortlist and holds interviews in the Administrator's office: the
 * applicants walk in, wait their turn and sit across the desk. You can sit in and choose yourself,
 * or let the head decide. When a head is lost, the O5 Council appoints the successor at the weekly
 * session (or you interview the shortlist yourself). Your secretary keeps you informed.
 * Applicants include the grown children of staff; a few are not who they say they are.
 */
@EventBusSubscriber(modid = BreachProtocol.MODID)
public final class Management {
    private Management() {}

    public enum Dept {
        SECURITY("Security", "Head of Security", FoundationStaff.Role.GUARD),
        RESEARCH("Research", "Director of Research", FoundationStaff.Role.RESEARCHER),
        MEDICAL("Medical", "Chief Medical Officer", FoundationStaff.Role.MEDIC),
        ENGINEERING("Engineering", "Chief Engineer", FoundationStaff.Role.TECHNICIAN),
        PERSONNEL("Personnel", "Head of Personnel", FoundationStaff.Role.CLERK);

        public final String name, title;
        public final FoundationStaff.Role role;

        Dept(String name, String title, FoundationStaff.Role role) { this.name = name; this.title = title; this.role = role; }

        static Dept of(FoundationStaff.Role r) {
            return switch (r) {
                case GUARD -> SECURITY; case RESEARCHER -> RESEARCH; case MEDIC -> MEDICAL; case TECHNICIAN -> ENGINEERING;
                case CLERK, JANITOR -> PERSONNEL; case SECRETARY -> null;
            };
        }
    }

    // ================================================================== data

    public static final class Vacancy {
        int id, role, attend; // attend: 0 not decided, 1 you'll sit in, 2 let them handle it
        String site = "", dim = "minecraft:overworld", kind = "staff", dept = "", state = "open", left = "";
        long pos, at, created, started;
        ListTag cands = new ListTag();

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putInt("id", id); t.putInt("role", role); t.putInt("attend", attend);
            t.putString("site", site); t.putString("dim", dim); t.putString("kind", kind); t.putString("dept", dept); t.putString("state", state); t.putString("left", left);
            t.putLong("pos", pos); t.putLong("at", at); t.putLong("created", created); t.putLong("started", started);
            t.put("cands", cands);
            return t;
        }

        static Vacancy load(CompoundTag t) {
            Vacancy v = new Vacancy();
            v.id = t.getInt("id"); v.role = t.getInt("role"); v.attend = t.getInt("attend");
            v.site = t.getString("site"); v.dim = t.getString("dim"); v.kind = t.getString("kind"); v.dept = t.getString("dept"); v.state = t.getString("state"); v.left = t.getString("left");
            v.pos = t.getLong("pos"); v.at = t.getLong("at"); v.created = t.getLong("created"); v.started = t.getLong("started");
            v.cands = t.getList("cands", Tag.TAG_COMPOUND);
            return v;
        }

        FoundationStaff.Role role() { return FoundationStaff.Role.values()[Math.floorMod(role, FoundationStaff.Role.values().length)]; }

        String what() {
            return switch (kind) {
                case "head" -> Dept.valueOf(dept).title;
                case "secretary" -> "your secretary";
                default -> "a " + role().title.toLowerCase(Locale.ROOT);
            };
        }
    }

    public static final class Data extends SavedData {
        final Map<String, UUID> heads = new HashMap<>();
        final Map<String, String> headNames = new HashMap<>();
        final List<Vacancy> vac = new ArrayList<>();
        int next = 1;
        public static final SavedData.Factory<Data> FACTORY = new SavedData.Factory<>(Data::new, Data::load, null);

        static Data load(CompoundTag t, HolderLookup.Provider p) {
            Data d = new Data();
            CompoundTag h = t.getCompound("heads");
            for (String k : h.getAllKeys()) { CompoundTag e = h.getCompound(k); d.heads.put(k, e.getUUID("id")); d.headNames.put(k, e.getString("name")); }
            for (Tag x : t.getList("vac", Tag.TAG_COMPOUND)) d.vac.add(Vacancy.load((CompoundTag) x));
            d.next = Math.max(1, t.getInt("next"));
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag t, HolderLookup.Provider p) {
            CompoundTag h = new CompoundTag();
            for (Map.Entry<String, UUID> e : heads.entrySet()) { CompoundTag x = new CompoundTag(); x.putUUID("id", e.getValue()); x.putString("name", headNames.getOrDefault(e.getKey(), "")); h.put(e.getKey(), x); }
            t.put("heads", h);
            ListTag l = new ListTag();
            for (Vacancy v : vac) l.add(v.save());
            t.put("vac", l);
            t.putInt("next", next);
            return t;
        }
    }

    static Data data(MinecraftServer s) { return s.overworld().getDataStorage().computeIfAbsent(Data.FACTORY, "breachprotocol_management"); }

    private static String key(String site, Dept d) { return site + "|" + d.name(); }

    /** "Head of Security" etc. if they head a department; null otherwise. */
    static String title(FoundationStaff s) {
        String d = s.getPersistentData().getString("bp_head");
        if (d.isEmpty()) return null;
        try { return Dept.valueOf(d).title; } catch (IllegalArgumentException e) { return null; }
    }

    static Dept headOf(FoundationStaff s) {
        String d = s.getPersistentData().getString("bp_head");
        try { return d.isEmpty() ? null : Dept.valueOf(d); } catch (IllegalArgumentException e) { return null; }
    }

    /** Who decides on hires for you: Directors and above sit in on interviews; the Council and the Administrator name heads. */
    static boolean mayAttend(ServerPlayer p) { return Talk.topBrass(p) || Career.rank(p).atLeast(Rank.LEVEL_5); }

    static boolean mayAppoint(ServerPlayer p) { return Talk.topBrass(p) || Career.rank(p).atLeast(Rank.OVERSEER); }

    private static long nextSlot(MinecraftServer s) {
        long now = s.overworld().getDayTime(), day = now / 24000;
        long[] slots = {4000, 8000}; // 10:00, 14:00
        for (int d = 0; d < 3; d++) for (long sl : slots) { long t = (day + d) * 24000 + sl; if (t - now >= 5000) return t; }
        return now + 24000;
    }

    /** Day-time "when" to game-time (vacancy times are kept in day-time, like the calendar). */
    private static long dayNow(MinecraftServer s) { return s.overworld().getDayTime(); }

    // ================================================================== applicants

    private static final String[] MOLES = {"the Chaos Insurgency", "the Global Occult Coalition", "the Serpent's Hand", "Marshall, Carter and Dark", "Are We Cool Yet?"};

    private static String background(FoundationStaff.Role role, Random r) {
        return switch (role) {
            case RESEARCHER -> Talk.pick(r, "PhD in theoretical physics; recruited after a paper the Foundation had to bury.", "Transferred from Site-19's xenobiology wing.", "Former university lecturer. Saw something in a dig in Peru.", "Ten years in pharmaceutical research. Very good at not asking questions.");
            case GUARD -> Talk.pick(r, "Ex-military, two tours. Quiet. Steady hands.", "Transferred from Site-17 security after the 2nd floor incident.", "Former police tactical unit. Saw an anomaly on a call and was recruited on the spot.", "Served with a Mobile Task Force reserve unit.");
            case MEDIC -> Talk.pick(r, "Trauma surgeon; ten years in emergency medicine.", "Military medic. Has treated things that weren't in any textbook.", "Epidemiologist, ex-CDC. Asked one question too many about a quarantine.");
            case TECHNICIAN -> Talk.pick(r, "Electrical engineer; kept a reactor running through a blackout.", "Ex-navy submarine systems technician.", "Self-taught. Built a containment-grade lock in a garage. We noticed.");
            case CLERK -> Talk.pick(r, "Records manager from a government archive that doesn't exist.", "Accountant. Found money that wasn't supposed to be found.", "Paralegal; knows how to make paperwork disappear.");
            case JANITOR -> Talk.pick(r, "Cleaned hospitals for fifteen years. Nothing fazes them.", "Former hotel night porter. Very discreet.");
            case SECRETARY -> Talk.pick(r, "Executive assistant to a defence minister. Speaks four languages.", "Ran the private office of a Site Director for six years.", "Former diplomatic secretary. Unflappable.", "Recommended by O5-7's own office.");
        };
    }

    private static CompoundTag candidate(MinecraftServer s, FoundationStaff.Role role, Random r, Set<UUID> taken) {
        CompoundTag c = new CompoundTag();
        long now = s.overworld().getGameTime();
        Registry.Rec grad = null;
        if (role != FoundationStaff.Role.SECRETARY && r.nextInt(100) < 40)
            for (Registry.Rec e : Registry.get(s).people.values())
                if (e.alive() && e.job.equals("graduate") && e.status.equals("offsite") && e.age(now) <= 34 && !taken.contains(e.id)) { grad = e; break; }
        String first, full;
        boolean female;
        UUID id;
        int age;
        String genome = "", traits;
        if (grad != null) {
            id = grad.id; full = grad.name; female = grad.female; age = grad.age(now); genome = grad.genome; traits = grad.traits;
            Registry.Rec m = Registry.find(s, grad.mother), f = Registry.find(s, grad.father);
            c.putString("family", (female ? "Daughter" : "Son") + " of " + (m != null ? m.name : "") + (m != null && f != null ? " and " : "") + (f != null ? f.name : "") + ". Grew up knowing what " + (female ? "her" : "his") + " parents really did.");
        } else {
            id = UUID.randomUUID();
            full = role == FoundationStaff.Role.SECRETARY ? Names.woman(s, net.minecraft.util.RandomSource.create()) : Names.person(s, net.minecraft.util.RandomSource.create());
            first = full.split(" ")[0];
            female = role == FoundationStaff.Role.SECRETARY || Names.isWoman(first);
            age = role == FoundationStaff.Role.SECRETARY ? 24 + r.nextInt(11) : 23 + r.nextInt(24);
            traits = Traits.write(Traits.natural(id));
        }
        taken.add(id);
        EnumSet<Traits> ts = Traits.read(traits);
        int comp = 3 + r.nextInt(7) + (grad != null ? 1 : 0);
        if (role == FoundationStaff.Role.RESEARCHER && ts.contains(Traits.EIDETIC)) comp += 2;
        if (role == FoundationStaff.Role.TECHNICIAN && ts.contains(Traits.COLOUR_BLIND)) comp -= 2;
        if (role == FoundationStaff.Role.GUARD && ts.contains(Traits.ATHLETIC)) comp += 1;
        if (role == FoundationStaff.Role.GUARD && ts.contains(Traits.ASTHMA)) comp -= 1;
        if (ts.contains(Traits.INSOMNIAC)) comp += 1;
        String prefix = role.prefix;
        c.putUUID("id", id);
        c.putString("name", (prefix.isEmpty() ? "" : prefix + " ") + full);
        c.putBoolean("female", female);
        c.putInt("age", age);
        c.putInt("comp", Math.max(1, Math.min(10, comp)));
        c.putString("mole", grad == null && r.nextInt(100) < 8 ? MOLES[r.nextInt(MOLES.length)] : "");
        c.putString("bg", background(role, r));
        c.putString("genome", genome);
        c.putString("traits", traits);
        c.putBoolean("grad", grad != null);
        return c;
    }

    private static Personality personality(CompoundTag c) {
        return Personality.values()[Math.min(13, (int) (Person.geneOf(c.getUUID("id"), 20) * 14))];
    }

    private static void sortBest(Vacancy v) {
        List<CompoundTag> l = new ArrayList<>();
        for (Tag t : v.cands) l.add((CompoundTag) t);
        l.sort((a, b) -> Integer.compare(b.getInt("comp") - (b.getString("mole").isEmpty() ? 0 : 2), a.getInt("comp") - (a.getString("mole").isEmpty() ? 0 : 2)));
        v.cands = new ListTag();
        v.cands.addAll(l);
    }

    // ================================================================== vacancies

    /** Someone left a post: their department head opens a vacancy and books interviews. */
    public static void staffVacancy(ServerLevel l, BlockPos post, FoundationStaff.Role role, String left) {
        MinecraftServer s = l.getServer();
        Data d = data(s);
        Sites.Site site = Sites.at(l, post);
        Vacancy v = new Vacancy();
        v.id = d.next++;
        v.site = site == null ? "" : site.name;
        v.dim = l.dimension().location().toString();
        v.pos = post.asLong();
        v.role = role.ordinal();
        v.kind = role == FoundationStaff.Role.SECRETARY ? "secretary" : "staff";
        v.left = left;
        v.created = s.overworld().getGameTime();
        v.at = nextSlot(s);
        Random r = new Random();
        Set<UUID> taken = new HashSet<>();
        for (int i = 0; i < 3; i++) v.cands.add(candidate(s, role, r, taken));
        sortBest(v);
        d.vac.add(v);
        d.setDirty();
        Dept dept = Dept.of(role);
        String head = dept == null ? null : d.headNames.get(key(v.site, dept));
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            if (!mayAttend(p)) continue;
            String body = (left.isEmpty() ? "We have" : left + " has left. We have") + " three applicants for " + v.what() + (v.site.isEmpty() ? "" : " at " + v.site) + ". Interviews are "
                    + Calendar.when(v.at) + " in your office" + (head != null ? ", run by " + head : "") + ". Would you like to sit in, or let " + (head != null ? "them" : "Personnel") + " handle it? (Ask me, or the head of department.)";
            tell(p, body);
        }
    }

    /** A head of department is gone: the Council appoints. */
    static void headVacancy(MinecraftServer s, String site, Dept dept, String left, BlockPos post, String dim) {
        Data d = data(s);
        for (Vacancy v : d.vac) if (v.kind.equals("head") && v.site.equals(site) && v.dept.equals(dept.name()) && !v.state.equals("done")) return;
        Vacancy v = new Vacancy();
        v.id = d.next++;
        v.site = site;
        v.dim = dim;
        v.pos = post == null ? 0 : post.asLong();
        v.role = dept.role.ordinal();
        v.kind = "head";
        v.dept = dept.name();
        v.left = left;
        v.created = s.overworld().getGameTime();
        v.at = dayNow(s) + 3L * 24000; // the Council's deadline
        // the shortlist: the most senior of the department, and one from outside
        List<FoundationStaff> pool = new ArrayList<>();
        for (ServerLevel l : s.getAllLevels())
            for (FoundationStaff f : l.getEntities(ModEntities.STAFF.get(), e -> e.isAlive() && e.role() == dept.role && e.unit() == null && !e.getPersistentData().contains("bp_cand")))
                if (site.equals(Registry.of(f).site)) pool.add(f);
        pool.sort(Comparator.comparingInt(f -> -Talk.years(f)));
        Random r = new Random();
        for (int i = 0; i < Math.min(2, pool.size()); i++) {
            FoundationStaff f = pool.get(i);
            CompoundTag c = new CompoundTag();
            c.putUUID("id", f.getUUID());
            c.putString("name", f.getName().getString());
            c.putBoolean("female", f.isFemale());
            c.putInt("age", f.age());
            c.putInt("comp", Math.min(10, 3 + Talk.years(f) / 3 + r.nextInt(3)));
            c.putString("mole", "");
            c.putString("bg", Math.min(Talk.years(f), Math.max(1, f.age() - 21)) + " years in " + dept.name + " at " + site + ".");
            c.putBoolean("internal", true);
            c.putString("traits", Traits.write(f.traits()));
            v.cands.add(c);
        }
        v.cands.add(candidate(s, dept.role, r, new HashSet<>()));
        CompoundTag ext = v.cands.getCompound(v.cands.size() - 1);
        ext.putString("bg", "External: " + ext.getString("bg"));
        sortBest(v);
        d.vac.add(v);
        d.setDirty();
        WorldNews.record(s, WorldNews.Kind.DECISION, "head_vacant", "the post of " + dept.title + " at " + site + " is vacant", -1, "");
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            if (mayAppoint(p)) tell(p, "The " + dept.title + " at " + site + (left.isEmpty() ? " post is vacant" : ", " + left + ", is gone") + ". The Council will appoint a successor at the next session, or you can interview the shortlist yourself. Ask me for the names.");
            else if (mayAttend(p)) tell(p, "The Council will appoint a new " + dept.title + " at " + site + ".");
        }
    }

    private static void tell(ServerPlayer p, String body) {
        String sec = null;
        for (String n : Forces.data(p.server).secretaryNames.values()) { sec = n; break; }
        if (sec != null && Talk.topBrass(p)) Career.message(p, sec + " (Secretary)", body);
        else Career.message(p, "Personnel", body);
    }

    static List<Vacancy> open(MinecraftServer s) {
        List<Vacancy> out = new ArrayList<>();
        for (Vacancy v : data(s).vac) if (!v.state.equals("done")) out.add(v);
        return out;
    }

    static Vacancy byId(MinecraftServer s, int id) { for (Vacancy v : data(s).vac) if (v.id == id) return v; return null; }

    // ================================================================== the room

    private static Sites.Site tower(MinecraftServer s, String site) {
        Sites.Site best = null;
        for (Sites.Site x : Sites.get(s).sites) {
            if (x.name.equals("Site-01") || !x.dim.equals("minecraft:overworld")) continue;
            if (x.name.equals(site)) return x;
            if (best == null) best = x;
        }
        return best;
    }

    private static BlockPos at(Sites.Site t, int x, int z) { return Secretary.origin(t).offset(x, SiteTower.y(SiteTower.TOP) + 1, z); }

    private static BlockPos door(Sites.Site t) { return at(t, 16, 11); }

    private static BlockPos waiting(Sites.Site t, int i) { return at(t, 9, 12 + i * 3); }

    private static BlockPos guestChair(Sites.Site t) { return at(t, 15, 23); }

    private static BlockPos headSpot(Sites.Site t) { return at(t, 19, 22); }

    private static ServerPlayer decider(MinecraftServer s, Vacancy v) {
        for (ServerPlayer p : s.getPlayerList().getPlayers()) if (v.kind.equals("head") ? mayAppoint(p) : mayAttend(p)) return p;
        return null;
    }

    /** The applicants arrive and wait; the head of department comes to sit in. */
    private static void begin(MinecraftServer s, Vacancy v) {
        ServerLevel ow = s.overworld();
        Sites.Site t = tower(s, v.site);
        if (t == null || !ow.isLoaded(door(t))) { decide(s, v, null); return; }
        v.state = "running";
        v.started = s.overworld().getGameTime();
        int i = 0;
        for (Tag x : v.cands) {
            CompoundTag c = (CompoundTag) x;
            FoundationStaff f;
            if (c.getBoolean("internal")) {
                f = Lifecycle.entity(s, c.getUUID("id"));
                if (f == null) continue;
                f.getPersistentData().putInt("bp_cand", v.id);
                f.walkTo(waiting(t, i++));
                continue;
            }
            f = ModEntities.STAFF.get().create(ow);
            if (f == null) continue;
            f.setUUID(c.getUUID("id"));
            BlockPos d = door(t);
            f.moveTo(d.getX() + 0.5, d.getY(), d.getZ() + 0.5, 180F, 0);
            f.getPersistentData().putInt("bp_cand", v.id);
            ow.addFreshEntity(f);
            f.applicant(v.role(), c.getString("name"), c.getInt("age"), waiting(t, i++));
            if (!c.getString("genome").isEmpty()) f.setGenome(c.getString("genome"), Traits.read(c.getString("traits")));
        }
        Dept dept = v.kind.equals("head") ? null : Dept.of(v.role());
        UUID hid = dept == null ? null : data(s).heads.get(key(v.site, dept));
        FoundationStaff head = Lifecycle.entity(s, hid);
        if (head != null) head.walkTo(headSpot(t));
        data(s).setDirty();
        ServerPlayer p = decider(s, v);
        if (p != null) {
            tell(p, "The applicants for " + v.what() + " are here. They're waiting in your office" + (head != null ? ", and " + head.getName().getString() + " is sitting in" : "") + ".");
            p.displayClientMessage(Component.literal("▣ Interviews for " + v.what() + " are starting in the Administrator's office.").withStyle(ChatFormatting.AQUA), false);
        }
    }

    private static List<FoundationStaff> present(MinecraftServer s, Vacancy v) {
        List<FoundationStaff> out = new ArrayList<>();
        for (Tag x : v.cands) { FoundationStaff f = Lifecycle.entity(s, ((CompoundTag) x).getUUID("id")); if (f != null && f.getPersistentData().getInt("bp_cand") == v.id) out.add(f); }
        return out;
    }

    /** Someone is chosen (by you, by the head, or by the Council); everyone else goes home. */
    static void decide(MinecraftServer s, Vacancy v, CompoundTag chosen) {
        Random r = new Random();
        String by = "Personnel";
        if (chosen == null) {
            // the department head (or the Council) picks the best they can see; moles are caught some of the time
            for (Tag x : v.cands) {
                CompoundTag c = (CompoundTag) x;
                if (!c.getString("mole").isEmpty() && r.nextInt(100) < 45) { c.putBoolean("caught", true); Registry.log(s, "Vetting flagged an applicant, " + c.getString("name") + ", with ties to " + c.getString("mole") + "."); continue; }
                chosen = c;
                break;
            }
            if (chosen == null) chosen = v.cands.getCompound(0);
            Dept dept = v.kind.equals("head") ? null : Dept.of(v.role());
            String hn = dept == null ? null : data(s).headNames.get(key(v.site, dept));
            by = v.kind.equals("head") ? "the O5 Council" : v.kind.equals("secretary") ? "the O5 Council" : hn != null ? hn : "Personnel";
        } else by = "the Administrator";
        hire(s, v, chosen, by);
        for (FoundationStaff f : present(s, v)) {
            if (f.getUUID().equals(chosen.getUUID("id"))) continue;
            f.getPersistentData().remove("bp_cand");
            if (v.kind.equals("head")) { f.order("post", null); continue; }
            Sites.Site t = tower(s, v.site);
            if (t != null) f.walkTo(door(t));
            f.getPersistentData().putLong("bp_leaving", s.overworld().getGameTime() + 160);
        }
        Dept dept = v.kind.equals("head") ? null : Dept.of(v.role());
        FoundationStaff head = Lifecycle.entity(s, dept == null ? null : data(s).heads.get(key(v.site, dept)));
        if (head != null) head.order("post", null);
        v.state = "done";
        data(s).setDirty();
    }

    private static void hire(MinecraftServer s, Vacancy v, CompoundTag c, String by) {
        String name = c.getString("name");
        UUID id = c.getUUID("id");
        ServerLevel l = null;
        for (ServerLevel x : s.getAllLevels()) if (x.dimension().location().equals(ResourceLocation.tryParse(v.dim))) l = x;
        if (l == null) l = s.overworld();
        BlockPos post = BlockPos.of(v.pos);
        if (v.kind.equals("head")) {
            Dept dept = Dept.valueOf(v.dept);
            FoundationStaff f = Lifecycle.entity(s, id);
            if (f == null && !c.getBoolean("internal")) f = spawnAt(l, post, v.role(), c);
            if (f != null) {
                f.getPersistentData().putString("bp_head", dept.name());
                f.getPersistentData().remove("bp_cand");
                if (!c.getBoolean("internal")) f.hireAs(v.role(), post);
            }
            data(s).heads.put(key(v.site, dept), id);
            data(s).headNames.put(key(v.site, dept), name);
            int yes = 7 + Math.min(6, c.getInt("comp") / 2), no = 13 - yes;
            String votes = by.equals("the Administrator") ? "by the Administrator" : "by the Council, " + yes + " votes to " + no;
            WorldNews.record(s, WorldNews.Kind.DECISION, "appoint", name + " was appointed " + dept.title + " at " + v.site + " " + votes, 2, by.equals("the Administrator") ? "the Administrator" : "the Council");
            Registry.log(s, name + " was appointed " + dept.title + " at " + v.site + ".");
            return;
        }
        if (v.kind.equals("secretary")) {
            Sites.Site t = tower(s, v.site);
            FoundationStaff f = Lifecycle.entity(s, id);
            Forces.Data fd = Forces.data(s);
            if (f != null && t != null) {
                f.hireAs(FoundationStaff.Role.SECRETARY, Secretary.post(t));
                Secretary.install(t, f);
                WorldNews.record(s, WorldNews.Kind.HIRE, "secretary", "the Administrator chose " + name + " as their new secretary", 1, "");
            } else if (t != null) fd.secretaryDue.put(t.name, s.overworld().getGameTime()); // the Council's usual appointment
            fd.setDirty();
            return;
        }
        FoundationStaff f = Lifecycle.entity(s, id);
        if (f == null) f = spawnAt(l, post, v.role(), c);
        else f.hireAs(v.role(), post);
        if (f == null) return;
        f.getPersistentData().remove("bp_cand");
        if (!c.getString("mole").isEmpty()) {
            f.getPersistentData().putString("bp_mole", c.getString("mole"));
            f.getPersistentData().putLong("bp_mole_at", s.overworld().getGameTime() + FoundationStaff.YEAR * (3 + new Random().nextInt(4)));
        }
        Registry.Rec e = Registry.of(f);
        e.status = "active";
        if (c.getBoolean("grad")) e.note = "";
        WorldNews.record(s, WorldNews.Kind.HIRE, "hire", name + " joined " + (v.site.isEmpty() ? "the Foundation" : v.site) + " as " + v.what() + (by.equals("Personnel") ? "" : ", chosen by " + by), 1, "");
        Registry.log(s, name + " was hired as " + v.what() + (v.site.isEmpty() ? "" : " at " + v.site) + " (chosen by " + by + ").");
    }

    private static FoundationStaff spawnAt(ServerLevel l, BlockPos post, FoundationStaff.Role role, CompoundTag c) {
        if (!l.isLoaded(post)) return null;
        FoundationStaff f = ModEntities.STAFF.get().create(l);
        if (f == null) return null;
        f.setUUID(c.getUUID("id"));
        f.moveTo(post.getX() + 0.5, post.getY(), post.getZ() + 0.5, l.random.nextFloat() * 360F, 0);
        l.addFreshEntity(f);
        f.applicant(role, c.getString("name"), c.getInt("age"), post);
        if (!c.getString("genome").isEmpty()) f.setGenome(c.getString("genome"), Traits.read(c.getString("traits")));
        f.hireAs(role, post);
        if (com.breachprotocol.world.Site01.isInside(l, post)) f.getPersistentData().putInt(com.breachprotocol.world.Site01.GEN_KEY, com.breachprotocol.world.Site01.data(l.getServer()).gen);
        return f;
    }

    // ================================================================== the Council's agenda

    /** Head appointments for the weekly session: one option per candidate, and "I'll interview them myself". */
    public static List<Calls.Template> agenda(ServerPlayer p) {
        List<Calls.Template> out = new ArrayList<>();
        for (Vacancy v : open(p.server)) {
            if (!v.kind.equals("head") || !v.state.equals("open")) continue;
            Dept dept = Dept.valueOf(v.dept);
            List<Calls.Option> opts = new ArrayList<>();
            StringBuilder b = new StringBuilder("The post of " + dept.title + " at " + v.site + " is vacant" + (v.left.isEmpty() ? "" : " since we lost " + v.left) + ". The shortlist: ");
            for (int i = 0; i < v.cands.size(); i++) {
                CompoundTag c = v.cands.getCompound(i);
                b.append(i > 0 ? "; " : "").append(c.getString("name")).append(", ").append(c.getInt("age")).append(" (").append(c.getString("bg")).append(")");
                final int idx = i;
                opts.add(new Calls.Option("Appoint " + c.getString("name"), (q, d) -> {
                    Vacancy vv = byId(q.server, v.id);
                    if (vv == null || vv.state.equals("done")) return "That post has already been filled.";
                    decide(q.server, vv, vv.cands.getCompound(idx));
                    return c.getString("name") + " is the new " + dept.title + " at " + v.site + ". I'll inform them.";
                }));
            }
            opts.add(new Calls.Option("I'll interview them myself", (q, d) -> {
                Vacancy vv = byId(q.server, v.id);
                if (vv == null) return "";
                vv.attend = 1;
                vv.state = "booked";
                vv.at = nextSlot(q.server);
                data(q.server).setDirty();
                return "Very well. The shortlist will be in your office " + Calendar.when(vv.at) + ".";
            }));
            out.add(new Calls.Template("appoint_" + v.id, 7, true, b.append(". Whom do we appoint?").toString(), opts));
        }
        return out;
    }

    // ================================================================== ticking

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        MinecraftServer s = event.getServer();
        if (s.getTickCount() % 100 != 40) return;
        Data d = data(s);
        long day = dayNow(s), now = s.overworld().getGameTime();
        for (Vacancy v : new ArrayList<>(d.vac)) {
            switch (v.state) {
                case "open", "booked" -> {
                    if (v.kind.equals("head") && v.state.equals("open")) {
                        if (day >= v.at || decider(s, v) == null && now - v.created > 24000) decide(s, v, null);
                        continue;
                    }
                    if (day < v.at) continue;
                    if (v.attend == 1 && decider(s, v) != null) begin(s, v);
                    else decide(s, v, null);
                }
                case "running" -> {
                    // you have an hour to arrive, and two to decide
                    ServerPlayer p = decider(s, v);
                    Sites.Site t = tower(s, v.site);
                    boolean here = p != null && t != null && p.blockPosition().distSqr(guestChair(t)) < 30 * 30;
                    if (!here && now - v.started > 1500 || now - v.started > 4000) {
                        if (p != null) p.displayClientMessage(Component.literal("▣ The interviews for " + v.what() + " were decided without you.").withStyle(ChatFormatting.GRAY), false);
                        decide(s, v, null);
                    }
                }
                default -> {}
            }
        }
        d.vac.removeIf(v -> v.state.equals("done") && now - v.created > 24000L * 5);
        if (s.getTickCount() % 1200 != 40) return;
        // heads: the founding heads are the most senior; a lost head goes to the Council
        Map<String, List<FoundationStaff>> bySite = new HashMap<>();
        for (ServerLevel l : s.getAllLevels())
            for (FoundationStaff f : l.getEntities(ModEntities.STAFF.get(), e -> e.isAlive() && e.unit() == null && !e.getPersistentData().contains("bp_cand"))) {
                String site = Registry.of(f).site;
                if (!site.isEmpty()) bySite.computeIfAbsent(site, k -> new ArrayList<>()).add(f);
                // applicants who were turned away leave
            }
        for (ServerLevel l : s.getAllLevels())
            for (FoundationStaff f : l.getEntities(ModEntities.STAFF.get(), e -> e.getPersistentData().contains("bp_leaving")))
                if (now > f.getPersistentData().getLong("bp_leaving")) f.discard();
        for (Map.Entry<String, List<FoundationStaff>> e : bySite.entrySet()) {
            for (Dept dept : Dept.values()) {
                String k = key(e.getKey(), dept);
                UUID h = d.heads.get(k);
                if (h != null) {
                    Registry.Rec hr = Registry.find(s, h);
                    if (hr != null && !hr.alive() || hr != null && !hr.status.equals("active")) {
                        d.heads.remove(k);
                        String n = d.headNames.remove(k);
                        BlockPos post = null;
                        for (FoundationStaff f : e.getValue()) if (f.role() == dept.role && f.post() != null) { post = f.post(); break; }
                        headVacancy(s, e.getKey(), dept, n == null ? "" : n, post, "minecraft:overworld");
                        d.setDirty();
                    }
                    continue;
                }
                boolean pending = false;
                for (Vacancy v : d.vac) if (v.kind.equals("head") && v.dept.equals(dept.name()) && v.site.equals(e.getKey()) && !v.state.equals("done")) pending = true;
                if (pending) continue;
                FoundationStaff best = null;
                for (FoundationStaff f : e.getValue()) if (f.role() == dept.role && (best == null || Talk.years(f) > Talk.years(best))) best = f;
                if (best == null) continue;
                best.getPersistentData().putString("bp_head", dept.name());
                d.heads.put(k, best.getUUID());
                d.headNames.put(k, best.getName().getString());
                d.setDirty();
                Registry.log(s, best.getName().getString() + " is " + dept.title + " at " + e.getKey() + ".");
            }
        }
        // moles
        for (ServerLevel l : s.getAllLevels())
            for (FoundationStaff f : l.getEntities(ModEntities.STAFF.get(), e -> e.getPersistentData().contains("bp_mole")))
                if (now > f.getPersistentData().getLong("bp_mole_at")) {
                    String org = f.getPersistentData().getString("bp_mole");
                    WorldNews.record(s, WorldNews.Kind.ATTACK, "mole", "security caught " + f.getName().getString() + " passing files to " + org + ". " + (f.isFemale() ? "She" : "He") + " fled before " + (f.isFemale() ? "she" : "he") + " could be detained", -5, "");
                    Registry.Rec rr = Registry.of(f);
                    rr.status = "defected";
                    rr.note = "defected to " + org;
                    Registry.log(s, f.getName().getString() + " defected to " + org + ".");
                    for (ServerPlayer p : s.getPlayerList().getPlayers()) if (mayAttend(p)) Career.changeStability(p, -5);
                    f.leaveService();
                }
    }

    // ================================================================== talking to applicants

    static boolean candidate(FoundationStaff s) { return s.getPersistentData().contains("bp_cand"); }

    private static CompoundTag cand(MinecraftServer srv, FoundationStaff s) {
        Vacancy v = byId(srv, s.getPersistentData().getInt("bp_cand"));
        if (v == null) return null;
        for (Tag x : v.cands) if (((CompoundTag) x).getUUID("id").equals(s.getUUID())) return (CompoundTag) x;
        return null;
    }

    static void openInterview(ServerPlayer p, FoundationStaff s) {
        Vacancy v = byId(p.server, s.getPersistentData().getInt("bp_cand"));
        CompoundTag c = cand(p.server, s);
        if (v == null || c == null) return;
        Sites.Site t = tower(p.server, v.site);
        if (t != null && !c.getBoolean("internal")) s.sitAt(guestChair(t));
        Personality pe = personality(c);
        String a = Career.address(p);
        Talk.say(p, s, Clearance.RAW + switch (pe) {
            case ANXIOUS -> "H-hello, " + a + ". Thank you for seeing me. I've- I've prepared. Sorry. Hello.";
            case EXTROVERTED, FRIENDLY -> "Good to meet you, " + a + "! Thank you for having me. Quite an office.";
            case CRABBY -> a + ". Let's get on with it.";
            case FLIRTY -> "Thank you for seeing me, " + a + ". I've heard a great deal about you.";
            default -> "Thank you for seeing me, " + a + ".";
        });
        topics(p, s);
    }

    static void topics(ServerPlayer p, FoundationStaff s) {
        Vacancy v = byId(p.server, s.getPersistentData().getInt("bp_cand"));
        CompoundTag c = cand(p.server, s);
        if (v == null || c == null) { Talk.opt(p, s, "Goodbye", "bye", "", 0xC8D0DA); return; }
        List<String> info = new ArrayList<>();
        info.add((c.getBoolean("internal") ? "Shortlisted for " : "Applicant for ") + v.what() + " · age " + c.getInt("age"));
        info.add("Personality: " + personality(c).label);
        info.add("Background: " + c.getString("bg"));
        if (c.contains("family")) info.add(c.getString("family"));
        int asked = Memory.of(s, p).getInt("iv_asked");
        if (asked >= 2) info.add("Impression: " + "★".repeat(Math.max(1, c.getInt("comp") / 2)) + "☆".repeat(5 - Math.max(1, c.getInt("comp") / 2)));
        if (Memory.get(s, p, "iv_check").equals("1")) info.add(c.getBoolean("caught") || !c.getString("mole").isEmpty() && Memory.get(s, p, "iv_flag").equals("1") ? "Vetting: FLAGGED" : "Vetting: clear");
        if (Memory.get(s, p, "iv_med").equals("1")) {
            List<String> tr = new ArrayList<>();
            for (Traits x : Traits.read(c.getString("traits"))) if (!x.about.isEmpty()) tr.add(x.label);
            info.add("Medical: " + (tr.isEmpty() ? "nothing of note" : String.join(", ", tr)));
        }
        Dialogue.info(p, info, -1);
        Dialogue.mood(p, personality(c) == Personality.ANXIOUS ? "Nervous" : "Composed", 0xE0D080);
        int col = 0x9FD8FF;
        if (!Dialogue.hasAnswers(p)) {
            Talk.ans(p, s, "Tell me about yourself.", "iv_about", "Their background");
            Talk.ans(p, s, "Why the Foundation?", "iv_why", "What drives them");
        }
        Talk.opt(p, s, "A Keter breaches on your shift", "iv_breach", "How they'd handle it", col);
        Talk.opt(p, s, "Your greatest weakness?", "iv_weak", "Honesty, and what they'd hide", col);
        Talk.opt(p, s, "Medical file", "iv_med", "Their medical record: traits", col);
        if (mayAttend(p)) Talk.opt(p, s, "Run a background check", "iv_check", "Vetting: who they really answer to", col);
        Dept dept = v.kind.equals("head") ? null : Dept.of(v.role());
        if (dept != null && data(p.server).headNames.containsKey(key(v.site, dept))) Talk.opt(p, s, "Ask " + data(p.server).headNames.get(key(v.site, dept)) + "'s view", "iv_head", "Their department head is sitting in", col);
        Talk.opt(p, s, v.kind.equals("head") ? "You're our new " + Dept.valueOf(v.dept).title + "." : "You're hired.", "iv_hire", "Give them the post", 0x80E0A0);
        Talk.opt(p, s, "Thank you. That will be all.", "iv_reject", "They aren't the one", 0xE08080);
    }

    static boolean handle(ServerPlayer p, FoundationStaff s, String what) {
        if (!what.startsWith("iv_") && !what.startsWith("mg_")) return false;
        if (what.startsWith("mg_")) return manage(p, s, what);
        Vacancy v = byId(p.server, s.getPersistentData().getInt("bp_cand"));
        CompoundTag c = cand(p.server, s);
        if (v == null || c == null) return false;
        Random r = new Random(c.getUUID("id").getLeastSignificantBits() + what.hashCode());
        Personality pe = personality(c);
        int comp = c.getInt("comp");
        String mole = c.getString("mole");
        EnumSet<Traits> ts = Traits.read(c.getString("traits"));
        Memory.of(s, p).putInt("iv_asked", Memory.of(s, p).getInt("iv_asked") + 1);
        Dialogue.clearOptions(p);
        switch (what) {
            case "iv_about" -> {
                Talk.you(p, "Tell me about yourself.");
                Talk.say(p, s, Clearance.RAW + c.getString("bg") + (c.contains("family") ? " My parents both worked here. I grew up with the word 'Foundation' said very quietly at dinner." : "") + " " + switch (pe) {
                    case INTROVERTED -> "That's... most of it.";
                    case EXTROVERTED -> "And I run the five-a-side team wherever I'm posted. Morale matters.";
                    case GREEDY -> "I'm told the hazard pay here is competitive.";
                    default -> "I think I'd be good here.";
                });
            }
            case "iv_why" -> {
                Talk.you(p, "Why the Foundation?");
                String line = !mole.isEmpty() && r.nextInt(2) == 0 ? "I've always admired how much the Foundation... keeps. Locked away. Somebody should know what's in those vaults."
                        : switch (pe) {
                    case UPBEAT, FRIENDLY, PEACEFUL -> "Because somebody has to keep people safe from things they'll never know about. I want that somebody to be me.";
                    case GLOOMY -> "Because I've seen what's out there. I'd rather be on the side holding the door.";
                    case GREEDY -> "Honestly? The pay. And the pension. And, fine, the saving the world part.";
                    case ODD -> "It called me. Not literally. Probably not literally.";
                    case CRABBY -> "Because the alternative is a normal job, and I'd lose my mind.";
                    default -> "Secure. Contain. Protect. I believe in all three.";
                };
                Talk.say(p, s, Clearance.RAW + line);
            }
            case "iv_breach" -> {
                Talk.you(p, "A Keter-class anomaly breaches containment on your shift. What do you do?");
                String line = comp >= 8 ? "Lockdown first, doors sealed on my floor. Clear the civilians, call it in on the secure channel, and I don't play hero: I hold my section until the MTF arrives."
                        : comp >= 5 ? "Raise the alarm, get people out, follow the protocol for that anomaly. I'd check the procedures binder on the way."
                        : "I'd... run? Sorry. I'd get help. I'd find someone senior and do what they say.";
                Talk.say(p, s, Clearance.RAW + line);
            }
            case "iv_weak" -> {
                Talk.you(p, "What's your greatest weakness?");
                String honest = ts.contains(Traits.INSOMNIAC) ? "I don't sleep. Ever. I'll be on shift whether you put me there or not."
                        : ts.contains(Traits.COLOUR_BLIND) ? "I'm colour blind. I've learned the hazard signs by shape. It's never been a problem. Mostly."
                        : ts.contains(Traits.ASTHMA) ? "Asthma. Stairs and I have an understanding."
                        : ts.contains(Traits.WEAK) ? "I'm not physically strong. I make up for it by not being anywhere near the dangerous end."
                        : switch (pe) { case ANXIOUS -> "I worry. About everything. It makes me very thorough."; case CRABBY -> "I don't suffer fools."; case GREEDY -> "I know what I'm worth."; default -> "I care too much. That's the honest answer."; };
                Talk.say(p, s, Clearance.RAW + (!mole.isEmpty() ? "I'm a perfectionist. (A rehearsed answer.)" : honest));
            }
            case "iv_med" -> {
                Talk.you(p, "Let me see your medical file.");
                Memory.put(s, p, "iv_med", "1");
                List<String> tr = new ArrayList<>();
                for (Traits x : ts) if (!x.about.isEmpty()) tr.add(x.label + " (" + x.about + ")");
                Talk.note(p, tr.isEmpty() ? "(The file is unremarkable. Fit for duty.)" : "(The file notes: " + String.join("; ", tr) + ".)");
            }
            case "iv_check" -> {
                Talk.you(p, "I'm running a background check.");
                Memory.put(s, p, "iv_check", "1");
                boolean found = !mole.isEmpty() && new Random().nextInt(100) < (Talk.topBrass(p) ? 90 : Career.rank(p).atLeast(Rank.OVERSEER) ? 80 : 65);
                if (found) {
                    Memory.put(s, p, "iv_flag", "1");
                    Talk.note(p, "(Vetting comes back within the hour: undeclared contact with " + mole + ". The applicant goes very still.)");
                    Talk.say(p, s, Clearance.RAW + Talk.pick(r, "That's- that's a mistake. That was years ago.", "...I'd like to leave now."));
                } else Talk.note(p, "(Vetting comes back clean. Nothing in their file you didn't already know.)");
                Talk.say(p, s, Clearance.RAW + (found ? "" : "Of course. I have nothing to hide."));
            }
            case "iv_head" -> {
                Dept dept = Dept.of(v.role());
                String hn = data(p.server).headNames.get(key(v.site, dept));
                Talk.you(p, "What do you think, " + hn + "?");
                Talk.note(p, "(" + hn + " leans over.) " + (comp >= 8 ? "\"Strongest of the three, if you ask me. I'd take them.\"" : comp >= 5 ? "\"Solid. Not spectacular. They'd learn.\"" : "\"I've got doubts. They'd need a lot of supervision.\"")
                        + (!mole.isEmpty() && r.nextInt(3) == 0 ? " \"Something about their references doesn't sit right with me, though.\"" : ""));
            }
            case "iv_hire" -> {
                Talk.you(p, v.kind.equals("head") ? "You're our new " + Dept.valueOf(v.dept).title + "." : "You're hired.");
                Talk.say(p, s, Clearance.RAW + switch (pe) {
                    case UPBEAT, EXTROVERTED -> "Really? Thank you! You won't regret this!";
                    case CRABBY -> "Good. When do I start?";
                    case ANXIOUS -> "Oh- oh thank you. Thank you. I'll do my absolute best.";
                    default -> "Thank you, " + Career.address(p) + ". I won't let you down.";
                });
                decide(p.server, v, c);
                Talk.opt(p, s, "Goodbye", "bye", "", 0xC8D0DA);
                return true;
            }
            case "iv_reject" -> {
                Talk.you(p, "Thank you. That will be all.");
                Talk.say(p, s, Clearance.RAW + Talk.pick(r, "I understand. Thank you for your time.", "...Right. Thank you."));
                if (!c.getBoolean("internal")) {
                    Sites.Site t = tower(p.server, v.site);
                    if (t != null) s.walkTo(door(t));
                    s.getPersistentData().putLong("bp_leaving", p.server.overworld().getGameTime() + 160);
                } else s.order("post", null);
                s.getPersistentData().remove("bp_cand");
                // turned everyone away: the head picks from who's left, or a new round is booked
                if (present(p.server, v).isEmpty()) {
                    v.cands = new ListTag();
                    Random rr = new Random();
                    for (int i = 0; i < 3; i++) v.cands.add(candidate(p.server, v.role(), rr, new HashSet<>()));
                    sortBest(v);
                    v.state = "booked";
                    v.at = nextSlot(p.server);
                    tell(p, "None of them, then. I've arranged a fresh round of interviews for " + v.what() + ", " + Calendar.when(v.at) + ".");
                }
                data(p.server).setDirty();
                Talk.opt(p, s, "Goodbye", "bye", "", 0xC8D0DA);
                return true;
            }
            default -> { return false; }
        }
        Talk.ans(p, s, "Tell me about yourself.", "iv_about", "Their background");
        Talk.ans(p, s, "Why the Foundation?", "iv_why", "What drives them");
        topics(p, s);
        return true;
    }

    // ================================================================== heads of department and your secretary: what's coming up

    /** "Personnel" for the secretary's briefing: a summary line. */
    public static String summary(MinecraftServer s) {
        List<Vacancy> o = open(s);
        if (o.isEmpty()) return "";
        List<String> parts = new ArrayList<>();
        for (Vacancy v : o) parts.add(v.kind.equals("head") && v.state.equals("open") ? "the Council has to name a new " + v.what() + (v.site.isEmpty() ? "" : " at " + v.site)
                : "interviews for " + v.what() + " " + Calendar.when(v.at) + (v.attend == 1 ? " (you're sitting in)" : ""));
        return "Personnel: " + String.join("; ", parts) + ".";
    }

    /** The personnel page (from your secretary, or a head of department about their own). */
    static void page(ServerPlayer p, FoundationStaff s, Dept only) {
        Dialogue.clearOptions(p);
        List<Vacancy> o = open(p.server);
        int n = 0;
        for (Vacancy v : o) {
            if (only != null && (v.kind.equals("head") || Dept.of(v.role()) != only)) continue;
            n++;
            if (v.kind.equals("head") && v.state.equals("open")) {
                if (mayAppoint(p)) Talk.ans(p, s, "I'll interview the shortlist for " + v.what() + " myself.", "mg_attend_" + v.id, "They'll come to your office");
                continue;
            }
            if (!v.state.equals("open") && !v.state.equals("booked")) continue;
            if (v.attend != 1) Talk.ans(p, s, "I'll sit in on the " + v.what().replace("a ", "") + " interviews (" + Calendar.when(v.at) + ").", "mg_attend_" + v.id, "The applicants will come to your office");
            if (v.attend != 2) Talk.ans(p, s, "Let them handle the " + v.what().replace("a ", "") + " hire.", "mg_delegate_" + v.id, "The head of department chooses");
            Talk.ans(p, s, "Hold the " + v.what().replace("a ", "") + " interviews now.", "mg_now_" + v.id, "Bring the applicants in within the hour");
        }
        if (n == 0) Talk.note(p, "(Nothing pending.)");
        Talk.ans(p, s, "Show me the personnel registry.", "mg_log", "Births, marriages, deaths, hires");
        Convo.topics(p, s);
    }

    static boolean manage(ServerPlayer p, FoundationStaff s, String what) {
        if (!mayAttend(p)) { Talk.say(p, s, "I'm sorry, that's for the Director's office."); Convo.menu(p, s); return true; }
        Dept only = s.role() == FoundationStaff.Role.SECRETARY ? null : headOf(s);
        if (what.equals("mg_page")) {
            Talk.you(p, "What's happening in Personnel?");
            String sum = summary(p.server);
            Talk.say(p, s, Clearance.RAW + (sum.isEmpty() ? "Nothing pending. Every post is filled." : sum));
            page(p, s, only);
            return true;
        }
        if (what.equals("mg_log")) {
            Talk.you(p, "Show me the registry.");
            List<String> l = Registry.recentLog(p.server, 6);
            if (l.isEmpty()) Talk.note(p, "(The registry is empty.)");
            for (String x : l) Talk.note(p, x);
            page(p, s, only);
            return true;
        }
        int us = what.lastIndexOf('_');
        Vacancy v;
        try { v = byId(p.server, Integer.parseInt(what.substring(us + 1))); } catch (NumberFormatException e) { return false; }
        if (v == null) { Convo.menu(p, s); return true; }
        if (what.startsWith("mg_attend_")) {
            Talk.you(p, "I'll sit in on those.");
            v.attend = 1;
            if (v.kind.equals("head")) { v.state = "booked"; v.at = nextSlot(p.server); }
            Talk.say(p, s, Clearance.RAW + "Of course. They'll be in your office " + Calendar.when(v.at) + ". I'll remind you.");
        } else if (what.startsWith("mg_delegate_")) {
            Talk.you(p, "Let them handle it.");
            v.attend = 2;
            Talk.say(p, s, Clearance.RAW + "Understood. I'll let you know who they choose.");
        } else if (what.startsWith("mg_now_")) {
            Talk.you(p, "Bring them in now.");
            v.attend = 1;
            v.at = dayNow(p.server);
            Talk.say(p, s, Clearance.RAW + "Right away. They'll be up within the hour.");
        }
        data(p.server).setDirty();
        page(p, s, only);
        return true;
    }
}
