package com.breachprotocol.entity;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.career.Career;
import com.breachprotocol.career.WorldNews;
import com.breachprotocol.registry.ModEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.*;

/**
 * Life going on (MCA's marriage and procreation managers, pregnancy, aging and the family tree),
 * at Foundation pace: a staff year is a Minecraft day. Colleagues fall for each other, date, get
 * engaged and marry; couples have children (nine months is most of a day), who grow up outside
 * with the family and, at twenty-two, may apply to the Foundation themselves. Parents outside grow
 * old and die, and someone inside grieves. You can do all of it too, with someone who loves you.
 */
@EventBusSubscriber(modid = BreachProtocol.MODID)
public final class Lifecycle {
    private Lifecycle() {}

    public static final long PREGNANCY = FoundationStaff.YEAR * 3 / 4, STAGE = FoundationStaff.YEAR * 2;

    private static final String[] GIRLS = {"Amelia", "Isla", "Ava", "Mia", "Ivy", "Freya", "Lily", "Rosie", "Nora", "Zara", "Elena", "Hana", "Maya", "Clara", "Iris", "June"};
    private static final String[] BOYS = {"Oliver", "Theo", "Arlo", "Leo", "Finn", "Noah", "Elias", "Jude", "Milo", "Ezra", "Kai", "Felix", "Oscar", "Rowan", "Silas", "Max"};

    static String surname(String full) {
        String[] p = full.replace("\"", "").trim().split(" ");
        return p.length == 0 ? "" : p[p.length - 1];
    }

    // ================================================================== family on record (from the first conversation)

    /** Makes sure their family (spouse, children, parents) exists in the registry. */
    static Registry.Rec ensure(FoundationStaff s) {
        Registry.Rec me = Registry.of(s);
        if (s.getPersistentData().getBoolean("bp_famreg")) return me;
        s.getPersistentData().putBoolean("bp_famreg", true);
        CompoundTag f = Social.family(s);
        MinecraftServer srv = s.getServer();
        Random g = new Random(s.getUUID().getMostSignificantBits());
        String last = surname(s.getName().getString());
        if (f.getBoolean("married") && me.spouse == null) {
            Registry.Rec sp;
            if (f.hasUUID("spouseId") && s.level() instanceof ServerLevel sl && sl.getEntity(f.getUUID("spouseId")) instanceof FoundationStaff mate) sp = Registry.of(mate);
            else sp = Registry.outsider(srv, f.getString("spouse") + " " + last, !s.isFemale(), Math.max(24, s.age() + g.nextInt(7) - 3), "offsite", "works for " + f.getString("cover"));
            me.spouse = sp.id; sp.spouse = me.id;
            me.rel = sp.rel = "married";
            if (f.getLong("widowed") > 0) { me.rel = "widowed"; sp.died = f.getLong("widowed"); sp.status = "dead"; sp.cause = f.getString("loss"); }
            for (net.minecraft.nbt.Tag k : f.getList("kids", 8)) {
                String[] kk = k.getAsString().split(":");
                boolean girl = kk.length > 1 && kk[1].equals("f");
                int age = kk.length > 2 ? Integer.parseInt(kk[2]) : 6;
                Registry.Rec mother = s.isFemale() ? me : sp, father = s.isFemale() ? sp : me;
                Registry.Rec c = Registry.born(srv, mother, father, kk[0] + " " + surname(father.name), girl);
                c.born = srv.overworld().getGameTime() - (long) age * Registry.YEAR;
                if (age >= 22) { c.status = "offsite"; c.job = "graduate"; }
            }
        }
        if (f.getBoolean("parents") && me.mother == null) {
            Registry.Rec mum = Registry.outsider(srv, Talk.pick(g, "Margaret", "Helen", "Rosa", "Anne", "Mei", "Fatima", "Irene") + " " + last, true, s.age() + 24 + g.nextInt(8), "offsite", "retired");
            Registry.Rec dad = Registry.outsider(srv, Talk.pick(g, "George", "Peter", "Ahmed", "Luis", "Kenji", "Robert", "Walter") + " " + last, false, s.age() + 26 + g.nextInt(8), "offsite", "retired");
            mum.spouse = dad.id; dad.spouse = mum.id; mum.rel = dad.rel = "married";
            me.mother = mum.id; me.father = dad.id;
        }
        Registry.dirty(srv);
        return me;
    }

    // ================================================================== marriage

    static void marry(MinecraftServer srv, Registry.Rec a, Registry.Rec b, String where) {
        a.spouse = b.id; b.spouse = a.id;
        a.partner = b.partner = null;
        a.rel = b.rel = "married";
        a.relSince = b.relSince = srv.overworld().getGameTime();
        Registry.log(srv, a.name + " and " + b.name + " were married" + where + ".");
        // the old flavour record follows the registry
        for (Registry.Rec x : List.of(a, b)) {
            FoundationStaff e = entity(srv, x.id);
            if (e == null) continue;
            CompoundTag f = Social.family(e);
            Registry.Rec o = x == a ? b : a;
            f.putBoolean("married", true);
            f.putString("spouse", o.name);
            if (!o.player && entity(srv, o.id) != null) f.putUUID("spouseId", o.id);
        }
        Registry.dirty(srv);
    }

    static FoundationStaff entity(MinecraftServer srv, UUID id) {
        if (id == null) return null;
        for (ServerLevel l : srv.getAllLevels()) if (l.getEntity(id) instanceof FoundationStaff f && f.isAlive()) return f;
        return null;
    }

    private static boolean free(Registry.Rec r) { return r.alive() && r.spouse == null && r.partner == null; }

    private static boolean compatible(MinecraftServer srv, Registry.Rec a, Registry.Rec b, long now) {
        if (a == b || !free(a) || !free(b)) return false;
        int aa = a.age(now), ab = b.age(now);
        if (aa < 22 || ab < 22 || Math.abs(aa - ab) > 12) return false;
        if (!Traits.attracted(a.female, a.traitSet(), b.female) || !Traits.attracted(b.female, b.traitSet(), a.female)) return false;
        return !Registry.related(srv, a, b);
    }

    // ================================================================== the minute-by-minute of life

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        MinecraftServer srv = event.getServer();
        if (srv.getTickCount() % 1200 != 600) return;
        long now = srv.overworld().getGameTime();
        Random r = new Random();
        Registry reg = Registry.get(srv);
        // everyone on site is on record
        Map<String, List<FoundationStaff>> bySite = new HashMap<>();
        for (ServerLevel l : srv.getAllLevels())
            for (FoundationStaff s : l.getEntities(ModEntities.STAFF.get(), e -> e.isAlive() && !e.getPersistentData().contains("bp_cand"))) {
                ensure(s);
                Workforce.deliver(s);
                Registry.Rec e = Registry.find(srv, s.getUUID());
                bySite.computeIfAbsent(e.site, k -> new ArrayList<>()).add(s);
            }

        // colleagues fall for each other: dating, then engaged, then married
        for (List<FoundationStaff> staff : bySite.values()) {
            if (staff.size() < 4 || r.nextInt(100) >= 6) continue;
            FoundationStaff a = staff.get(r.nextInt(staff.size()));
            if (Social.admirer(a) || a.role() == FoundationStaff.Role.SECRETARY && r.nextBoolean()) continue;
            Registry.Rec ra = Registry.find(srv, a.getUUID());
            for (FoundationStaff b : staff) {
                Registry.Rec rb = Registry.find(srv, b.getUUID());
                if (Social.admirer(b) || !compatible(srv, ra, rb, now)) continue;
                // people of a temper get on
                if (Personality.of(a).temper != Personality.of(b).temper && r.nextInt(3) == 0) continue;
                ra.partner = rb.id; rb.partner = ra.id;
                ra.rel = rb.rel = "dating";
                ra.relSince = rb.relSince = now;
                WorldNews.record(srv, WorldNews.Kind.HIRE, "romance", a.getName().getString() + " and " + b.getName().getString() + " have been seen having lunch together. Every day", 1, "");
                reg.setDirty();
                break;
            }
        }
        for (Registry.Rec e : new ArrayList<>(reg.people.values())) {
            if (!e.alive()) continue;
            // relationships move on
            if (e.partner != null && !e.player && e.id.compareTo(e.partner) < 0) {
                Registry.Rec o = Registry.find(srv, e.partner);
                if (o == null || !o.alive()) { e.partner = null; e.rel = "single"; }
                else if (!o.player && now - e.relSince > STAGE) {
                    if (e.rel.equals("dating")) {
                        if (r.nextInt(5) == 0) { // it didn't work out
                            e.partner = o.partner = null; e.rel = o.rel = "single";
                            WorldNews.record(srv, WorldNews.Kind.HIRE, "romance", e.name + " and " + o.name + " aren't eating lunch together any more", -1, "");
                        } else {
                            e.rel = o.rel = "engaged"; e.relSince = o.relSince = now;
                            WorldNews.record(srv, WorldNews.Kind.HIRE, "romance", e.name + " and " + o.name + " are engaged", 2, "");
                            Registry.log(srv, e.name + " and " + o.name + " announced their engagement.");
                        }
                    } else if (e.rel.equals("engaged")) {
                        marry(srv, e, o, " in the tower atrium, with half the site in attendance");
                        WorldNews.record(srv, WorldNews.Kind.HIRE, "wedding", e.name + " and " + o.name + " were married. The canteen did a cake", 3, "");
                    }
                }
                reg.setDirty();
            }
            // children
            if (e.female && e.due == 0 && e.spouse != null && e.rel.equals("married") && e.age(now) >= 22 && e.age(now) <= 44 && !e.traitSet().contains(Traits.INFERTILE) && !e.player) {
                Registry.Rec sp = Registry.find(srv, e.spouse);
                boolean wantsMore = r.nextInt(100) < 100 / (Registry.children(srv, e.id).size() * 3 + 2);
                if (sp != null && sp.alive() && !sp.traitSet().contains(Traits.INFERTILE) && !sp.female && !sp.player && r.nextInt(100) < 3 && wantsMore) {
                    e.due = now + PREGNANCY;
                    WorldNews.record(srv, WorldNews.Kind.HIRE, "expecting", e.name + " is expecting", 2, "");
                    reg.setDirty();
                }
            }
            if (e.due > 0 && now >= e.due) birth(srv, e, r);
            // growing up, and growing old outside
            if (e.status.equals("child") && e.age(now) >= 22) {
                e.status = "offsite";
                e.job = "graduate";
                e.note = "may apply to the Foundation";
                Registry.log(srv, e.name + " graduated. " + (e.female ? "She" : "He") + " has been told, very carefully, what " + (e.female ? "her" : "his") + " parents really do.");
                reg.setDirty();
            }
            if (!e.status.equals("active") && !e.player && e.age(now) >= 72 && r.nextInt(100) < (e.age(now) - 66)) {
                e.died = now;
                e.status = "dead";
                e.cause = "peacefully, at home";
                Registry.log(srv, e.name + " died peacefully at home, aged " + e.age(now) + ".");
                for (Registry.Rec k : Registry.children(srv, e.id)) grieve(srv, k, e, e.female ? "mother" : "father");
                Registry.Rec sp = Registry.find(srv, e.spouse);
                if (sp != null) { sp.rel = "widowed"; grieve(srv, sp, e, e.female ? "wife" : "husband"); }
                reg.setDirty();
            }
        }
    }

    private static void birth(MinecraftServer srv, Registry.Rec mum, Random r) {
        Registry.Rec dad = Registry.find(srv, mum.spouse);
        mum.due = 0;
        boolean girl = r.nextBoolean();
        Registry.Rec father = dad != null && !dad.female ? dad : null;
        String last = surname((father != null ? father : mum).name);
        boolean ours = dad != null && dad.player;
        String first = (girl ? GIRLS : BOYS)[r.nextInt(16)];
        Registry.Rec kid = Registry.born(srv, mum, father, first + " " + last, girl);
        if (ours) kid.note = "lives with the family; not yet named";
        Registry.log(srv, mum.name + (dad != null ? " and " + dad.name : "") + " welcomed a " + (girl ? "daughter" : "son") + ", " + kid.name + ".");
        WorldNews.record(srv, WorldNews.Kind.HIRE, "birth", mum.name + (dad != null ? " and " + dad.name : "") + " had a baby " + (girl ? "girl" : "boy"), 3, ours ? dad.name : "");
        FoundationStaff m = entity(srv, mum.id);
        if (m != null) m.dayOff(); // maternity leave
        if (ours) {
            ServerPlayer p = srv.getPlayerList().getPlayer(dad.id);
            if (p != null) {
                p.sendSystemMessage(Component.literal("♥ " + mum.name + " has given birth to your " + (girl ? "daughter" : "son") + ". Go and see them, and choose a name together.").withStyle(ChatFormatting.LIGHT_PURPLE));
                if (m != null) Memory.put(m, p, "baby_unnamed", kid.id.toString());
            }
        }
        Registry.dirty(srv);
    }

    private static void grieve(MinecraftServer srv, Registry.Rec who, Registry.Rec lost, String relation) {
        FoundationStaff e = entity(srv, who.id);
        if (e == null) return;
        e.getPersistentData().putLong("bp_loss", srv.overworld().getGameTime());
        e.getPersistentData().putString("bp_loss_who", lost.name);
        e.getPersistentData().putString("bp_loss_rel", relation);
    }

    /** Lost someone outside in the last few days (a parent, a spouse). */
    static boolean bereaved(FoundationStaff s) {
        long t = s.getPersistentData().getLong("bp_loss");
        return t > 0 && s.level().getGameTime() - t < FoundationStaff.YEAR * 3;
    }

    // ================================================================== you and someone who loves you

    static Registry.Rec rec(FoundationStaff s) { return ensure(s); }

    static boolean engagedTo(FoundationStaff s, ServerPlayer p) { Registry.Rec e = rec(s); return p.getUUID().equals(e.partner) && e.rel.equals("engaged"); }

    static boolean marriedTo(FoundationStaff s, ServerPlayer p) { Registry.Rec e = rec(s); return p.getUUID().equals(e.spouse); }

    /** The relationship options a partner sees under Socialise. */
    static void options(ServerPlayer p, FoundationStaff s) {
        Registry.Rec e = rec(s), me = Registry.player(p);
        long now = p.server.overworld().getGameTime();
        boolean ring = p.getMainHandItem().is(net.minecraft.world.item.Items.DIAMOND);
        if (Social.partner(s, p) && e.spouse == null && !engagedTo(s, p) && me.spouse == null)
            Talk.opt(p, s, "Propose", "life_propose", ring ? "Kneel, with the ring you're holding" : "Hold a diamond (the ring) to propose", ring ? 0xFFD700 : 0x808080);
        if (engagedTo(s, p)) Talk.opt(p, s, now - e.relSince >= FoundationStaff.YEAR / 2 ? "Let's get married" : "The wedding", "life_wed", "Marry them, with the site as witness", 0xFFD700);
        if (marriedTo(s, p)) {
            if (e.due == 0) Talk.opt(p, s, e.female ? "Let's start a family" : "Let's adopt", "life_child", "Children grow up outside with the family, and may join the Foundation one day", 0xF0A8C8);
            Talk.opt(p, s, "Our family", "life_tree", "Your family tree", 0xF0A8C8);
            Talk.opt(p, s, "I think we should separate", "life_divorce", "End the marriage", 0x808080);
        }
        String baby = Memory.get(s, p, "baby_unnamed");
        if (!baby.isEmpty()) { Dialogue.input(p, "/talk " + s.getId() + " life_name_{text}"); }
    }

    static boolean handle(ServerPlayer p, FoundationStaff s, String what) {
        MinecraftServer srv = p.server;
        Registry.Rec e = rec(s), me = Registry.player(p);
        long now = srv.overworld().getGameTime();
        Random r = new Random();
        String n = s.getName().getString();
        switch (what) {
            case "life_propose" -> {
                if (!p.getMainHandItem().is(net.minecraft.world.item.Items.DIAMOND)) { Talk.note(p, "(You'll need a ring. Hold a diamond.)"); Social.socialPage(p, s); return true; }
                Talk.you(p, "(You go down on one knee.) Will you marry me?");
                if (Convo.trust(s, p) < 8 || me.spouse != null) {
                    Talk.say(p, s, Clearance.RAW + "I- oh. I'm sorry. I'm not ready for that. Not yet.");
                    Convo.setTrust(s, p, Convo.trust(s, p) - 1);
                } else {
                    p.getMainHandItem().shrink(1);
                    e.partner = me.id; me.partner = e.id;
                    e.rel = me.rel = "engaged";
                    e.relSince = me.relSince = now;
                    Talk.say(p, s, Clearance.RAW + Talk.pick(r, "Yes. YES. Oh my god. Yes!", "(" + Scene.cap(s.isFemale() ? "she" : "he") + " is crying and laughing at once.) Yes. Of course yes."));
                    Registry.log(srv, n + " and " + me.name + " are engaged.");
                    WorldNews.record(srv, WorldNews.Kind.HIRE, "engaged", n + " and " + me.name + " are engaged", 3, "");
                    Registry.dirty(srv);
                }
                Social.socialPage(p, s);
                return true;
            }
            case "life_wed" -> {
                if (!engagedTo(s, p)) return false;
                if (now - e.relSince < FoundationStaff.YEAR / 2) {
                    Talk.you(p, "When's the wedding?");
                    Talk.say(p, s, Clearance.RAW + "Soon! Give me half a day to get the chapel booked and find something to wear that isn't a lab coat.");
                } else {
                    Talk.you(p, "Let's get married. Today.");
                    marry(srv, e, me, " in a small ceremony on site");
                    WorldNews.record(srv, WorldNews.Kind.HIRE, "wedding", n + " and " + me.name + " were married today", 4, "");
                    Talk.say(p, s, Clearance.RAW + Talk.pick(r, "I do. I really, really do.", "Then it's done. You're stuck with me now."));
                    for (ServerPlayer q : srv.getPlayerList().getPlayers()) q.sendSystemMessage(Component.literal("♥ " + n + " and " + me.name + " are married.").withStyle(ChatFormatting.LIGHT_PURPLE));
                    Convo.setTrust(s, p, 10);
                }
                Social.socialPage(p, s);
                return true;
            }
            case "life_child" -> {
                if (!marriedTo(s, p)) return false;
                Talk.you(p, e.female ? "I think I'm ready. For a family." : "What if we adopted?");
                if (e.female && (e.age(now) > 44 || e.traitSet().contains(Traits.INFERTILE))) {
                    Talk.say(p, s, Clearance.RAW + "...I can't. You know I can't. But we could adopt. If you wanted.");
                } else if (e.female) {
                    e.due = now + PREGNANCY;
                    Talk.say(p, s, Clearance.RAW + "Really? (" + Scene.cap(s.isFemale() ? "she" : "he") + " takes your hand.) Okay. Okay. Let's do it.");
                    Talk.note(p, "(Some time later, " + n + " tells you " + (s.isFemale() ? "she" : "he") + "'s expecting. The baby is due in about three-quarters of a day.)");
                    WorldNews.record(srv, WorldNews.Kind.HIRE, "expecting", n + " is expecting", 2, "");
                    Registry.dirty(srv);
                } else {
                    Registry.Rec kid = Registry.outsider(srv, (r.nextBoolean() ? GIRLS : BOYS)[r.nextInt(16)] + " " + surname(me.name), r.nextBoolean(), 2 + r.nextInt(5), "child", "");
                    kid.father = s.isFemale() ? me.id : e.id;
                    kid.mother = s.isFemale() ? e.id : null;
                    kid.note = "adopted";
                    Talk.say(p, s, Clearance.RAW + "There's a child. The Foundation's orphanage list. " + kid.name + ", " + kid.age(now) + ". Parents lost to an incident. I already said yes. I hope that's okay.");
                    Registry.log(srv, n + " and " + me.name + " adopted " + kid.name + ".");
                    Registry.dirty(srv);
                }
                Social.socialPage(p, s);
                return true;
            }
            case "life_divorce" -> {
                if (!marriedTo(s, p)) return false;
                Talk.you(p, "I think we should separate.");
                Talk.say(p, s, Clearance.RAW + Talk.pick(r, "...I knew it was coming. I didn't think it would hurt this much.", "Is this an order, too?"));
                e.spouse = me.spouse = null;
                e.rel = me.rel = "single";
                Memory.put(s, p, "partner", "");
                Memory.put(s, p, "admire", "over");
                Convo.setTrust(s, p, 1);
                Registry.log(srv, n + " and " + me.name + " separated.");
                Registry.dirty(srv);
                Convo.menu(p, s);
                return true;
            }
            case "life_tree" -> { Talk.you(p, "Show me our family."); FamilyTree.send(p, e); Convo.menu(p, s); return true; }
            case "family_tree" -> { Talk.you(p, "Tell me about your family."); FamilyTree.send(p, e); Convo.menu(p, s); return true; }
            default -> {}
        }
        if (what.startsWith("life_name")) {
            String id = Memory.get(s, p, "baby_unnamed");
            Registry.Rec kid = id.isEmpty() ? null : Registry.find(srv, UUID.fromString(id));
            String typed = what.length() > 10 ? what.substring(10).replaceAll("[^A-Za-z' -]", "").trim() : "";
            if (kid == null || typed.isEmpty()) return true;
            kid.name = Scene.cap(typed.split(" ")[0]) + " " + surname(kid.name);
            kid.note = "lives with the family";
            Memory.put(s, p, "baby_unnamed", "");
            Talk.you(p, "What about " + Scene.cap(typed.split(" ")[0]) + "?");
            Talk.say(p, s, Clearance.RAW + Scene.cap(typed.split(" ")[0]) + ". (" + Scene.cap(s.isFemale() ? "she" : "he") + " smiles.) It's perfect.");
            Registry.log(srv, me.name + "'s child was named " + kid.name + ".");
            Registry.dirty(srv);
            Convo.menu(p, s);
            return true;
        }
        return false;
    }
}
