package com.breachprotocol.career;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.entity.Dialogue;
import com.breachprotocol.entity.FoundationStaff;
import com.breachprotocol.entity.Seats;
import com.breachprotocol.registry.ModEntities;
import com.breachprotocol.world.SiteTower;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.*;

/**
 * The Administrator's secretary. She works in the Administrator's office at each site tower; when
 * the Administrator sits at the desk she comes over and briefs them (messages, calendar, the
 * weekly Council session, site security). She keeps the weekly Council meeting (Tuesdays unless
 * told otherwise) and texts the Administrator if they're late. When she retires or dies, the O5
 * Council appoints her successor.
 */
@EventBusSubscriber(modid = BreachProtocol.MODID)
public final class Secretary {
    private Secretary() {}

    private static final Set<UUID> BRIEFED = new HashSet<>();

    private static boolean topBrass(ServerPlayer p) { return Career.rank(p) == Rank.ADMINISTRATOR || Career.isAscended(p); }

    public static BlockPos origin(Sites.Site s) { return new BlockPos(s.x - SiteTower.T / 2, s.y, s.z - SiteTower.T / 2); }

    static BlockPos deskChair(Sites.Site s) { return origin(s).offset(16, SiteTower.y(SiteTower.TOP) + 1, 26); }

    /** Towers with the new core (lift) and the reception, where her desk is. */
    static final Set<String> RECEPTION = new HashSet<>();
    /** When each tower was last patched (this session). */
    private static final Map<String, Long> PATCHED = new HashMap<>();

    public static BlockPos post(Sites.Site s) {
        return RECEPTION.contains(s.name) ? origin(s).offset(SiteTower.secretaryChair()) : origin(s).offset(20, SiteTower.y(SiteTower.TOP) + 1, 21);
    }

    static boolean isTower(ServerLevel l, Sites.Site s) {
        BlockPos desk = Desk.deskSpot(s).below();
        return l.isLoaded(desk) && BuiltInRegistries.BLOCK.getKey(l.getBlockState(desk).getBlock()).getPath().equals("office_desk");
    }

    static FoundationStaff find(ServerLevel l, Sites.Site s) {
        UUID id = Forces.data(l.getServer()).secretaries.get(s.name);
        if (id == null) return null;
        Entity e = l.getEntity(id);
        return e instanceof FoundationStaff f && f.isAlive() ? f : null;
    }

    // ================================================================== deliveries

    public static boolean has(ServerPlayer p) { return !Forces.data(p.server).secretaries.isEmpty(); }

    /** She takes delivery; it goes in the chest by the Administrator's desk. */
    public static boolean deliver(ServerPlayer p, net.minecraft.world.item.ItemStack stack) {
        Forces.Data d = Forces.data(p.server);
        if (d.secretaries.isEmpty() || stack.isEmpty()) return false;
        d.deliveries.add(stack.copy());
        d.setDirty();
        p.displayClientMessage(Component.literal("▣ Your secretary has taken delivery of " + stack.getCount() + "× " + stack.getHoverName().getString() + ".").withStyle(ChatFormatting.AQUA), true);
        return true;
    }

    static BlockPos officeChest(Sites.Site s) { return origin(s).offset(20, SiteTower.y(SiteTower.TOP) + 1, 26); }

    /** Puts waiting deliveries in the office chest (or a new chest beside it if that one is full). */
    private static void stock(ServerLevel l, Sites.Site s, Forces.Data d) {
        if (d.deliveries.isEmpty()) return;
        BlockPos c = officeChest(s);
        List<String> what = new ArrayList<>();
        for (BlockPos at : new BlockPos[]{c, c.west(), c.west(2)}) {
            if (d.deliveries.isEmpty()) break;
            if (l.getBlockState(at).isAir()) l.setBlock(at, net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.ChestBlock.FACING, net.minecraft.core.Direction.WEST), 3);
            if (!(l.getBlockEntity(at) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest)) continue;
            for (Iterator<net.minecraft.world.item.ItemStack> it = d.deliveries.iterator(); it.hasNext(); ) {
                net.minecraft.world.item.ItemStack st = it.next();
                String label = st.getCount() + "× " + st.getHoverName().getString();
                for (int i = 0; i < chest.getContainerSize() && !st.isEmpty(); i++) {
                    net.minecraft.world.item.ItemStack in = chest.getItem(i);
                    if (in.isEmpty()) { chest.setItem(i, st.copy()); st.setCount(0); }
                    else if (net.minecraft.world.item.ItemStack.isSameItemSameComponents(in, st) && in.getCount() < in.getMaxStackSize()) {
                        int m = Math.min(st.getCount(), in.getMaxStackSize() - in.getCount());
                        in.grow(m); st.shrink(m);
                    }
                }
                if (st.isEmpty()) { it.remove(); what.add(label); }
            }
            chest.setChanged();
        }
        d.setDirty();
        if (what.isEmpty()) return;
        String name = d.secretaryNames.getOrDefault(s.name, "Your secretary");
        for (ServerPlayer q : l.getServer().getPlayerList().getPlayers())
            if (topBrass(q)) Career.message(q, name + " (Secretary)", "A delivery came for you from the Council: " + String.join(", ", what) + ". I've put it in the chest by your desk at " + s.name + ".");
    }

    // ================================================================== staffing

    private static FoundationStaff hire(ServerLevel l, Sites.Site s) {
        FoundationStaff f = ModEntities.STAFF.get().create(l);
        if (f == null) return null;
        BlockPos at = post(s);
        f.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 180F, 0);
        f.setup(FoundationStaff.Role.SECRETARY, at);
        f.setPersistenceRequired();
        l.addFreshEntity(f);
        Forces.Data d = Forces.data(l.getServer());
        d.secretaries.put(s.name, f.getUUID());
        d.secretaryNames.put(s.name, f.getName().getString());
        d.secretaryDue.remove(s.name);
        d.setDirty();
        return f;
    }

    /** A new secretary chosen in person at interview: she takes the post. */
    public static void install(Sites.Site s, FoundationStaff f) {
        Forces.Data d = Forces.data(f.getServer());
        d.secretaries.put(s.name, f.getUUID());
        d.secretaryNames.put(s.name, f.getName().getString());
        d.secretaryDue.remove(s.name);
        d.setDirty();
    }

    /** She retired or died: the Council will appoint someone. */
    public static void vacancy(FoundationStaff f, String how) {
        MinecraftServer s = f.getServer();
        if (s == null) return;
        Forces.Data d = Forces.data(s);
        for (Map.Entry<String, UUID> e : new ArrayList<>(d.secretaries.entrySet())) {
            if (!e.getValue().equals(f.getUUID())) continue;
            d.secretaries.remove(e.getKey());
            d.secretaryNames.remove(e.getKey());
            d.secretaryDue.put(e.getKey(), Long.MAX_VALUE / 4); // until the interviews (or the Council) decide
            d.setDirty();
            for (Sites.Site site : Sites.get(s).sites)
                if (site.name.equals(e.getKey()) && f.level() instanceof ServerLevel sl) com.breachprotocol.entity.Management.staffVacancy(sl, post(site), FoundationStaff.Role.SECRETARY, f.getName().getString());
            for (ServerPlayer q : s.getPlayerList().getPlayers())
                if (topBrass(q)) Career.message(q, O5.O5_7, "Your secretary, " + f.getName().getString() + ", has " + how + ". Personnel has three candidates for you to interview in your office, or the Council will choose.");
        }
    }

    // ================================================================== ticking

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        MinecraftServer srv = event.getServer();
        if (srv.getTickCount() % 20 != 9) return;
        ServerLevel ow = srv.overworld();
        long now = ow.getGameTime();
        Forces.Data d = Forces.data(srv);
        for (Sites.Site s : Sites.get(srv).sites) {
            if (s.name.equals("Site-01") || !s.dim.equals("minecraft:overworld")) continue;
            // an older tower: refit it (lift, keyed stairs, reception, residence) the first time someone is near
            BlockPos o = origin(s);
            if (!RECEPTION.contains(s.name) && ow.isLoaded(o.offset(SiteTower.liftPanel(0))) && isTower(ow, s)) {
                if (com.breachprotocol.entity.Lift.hasLift(ow, o)) RECEPTION.add(s.name);
                else if (ow.getNearestPlayer(o.getX() + 16, o.getY(), o.getZ() + 16, 96, false) != null) {
                    SiteTower.refit(ow, o);
                    RECEPTION.add(s.name);
                    BreachProtocol.LOGGER.info("Refitted {}: lift, keyed stairwell, secretary's reception, staff residence", s.name);
                    for (ServerPlayer q : srv.getPlayerList().getPlayers())
                        if (q.distanceToSqr(o.getX(), o.getY(), o.getZ()) < 200 * 200) q.sendSystemMessage(Component.literal("Facilities has finished work on " + s.name + ": a lift, keyed stairwell doors, a reception outside the Administrator's office and a staff residence.").withStyle(ChatFormatting.AQUA));
                    // she moves to her new desk
                    FoundationStaff sec = find(ow, s);
                    if (sec != null) sec.hireAs(FoundationStaff.Role.SECRETARY, post(s));
                }
            }
            // once a day, while someone's near: whatever's missing from the tower goes back, by its own plan (nothing doubled)
            if (RECEPTION.contains(s.name) && ow.isLoaded(o.offset(SiteTower.liftPanel(0))) && isTower(ow, s)
                    && ow.getNearestPlayer(o.getX() + 16, o.getY(), o.getZ() + 16, 96, false) != null) {
                long tNow = ow.getGameTime();
                Long last = PATCHED.get(s.name);
                if (last == null || tNow - last > 24000L) {
                    PATCHED.put(s.name, tNow);
                    int n = SiteTower.patch(ow, o);
                    if (n > 0) BreachProtocol.LOGGER.info("Patched {}: {} missing blocks put back", s.name, n);
                }
            }
            // furniture fixes for towers built before them: once, the first time someone is near
            if (s.layout < SiteTower.LAYOUT && RECEPTION.contains(s.name) && ow.isLoaded(o.offset(SiteTower.liftPanel(0)))
                    && ow.getNearestPlayer(o.getX() + 16, o.getY(), o.getZ() + 16, 96, false) != null && isTower(ow, s)) {
                SiteTower.touchUp(ow, o, s.layout);
                s.layout = SiteTower.LAYOUT;
                Sites.get(srv).setDirty();
                BreachProtocol.LOGGER.info("Touched up {}: furniture layout revision {}", s.name, SiteTower.LAYOUT);
            }
            BlockPos at = post(s);
            if (!ow.isLoaded(at) || ow.getNearestPlayer(at.getX(), at.getY(), at.getZ(), 64, false) == null || !isTower(ow, s)) continue;
            if (d.secretaries.containsKey(s.name)) stock(ow, s, d);
            if (srv.getTickCount() % 600 == 9) SiteTower.tidyCouncil(ow, origin(s)); // papers, not screens, on the council table
            Long due = d.secretaryDue.get(s.name);
            if (!d.secretaries.containsKey(s.name) && (due == null || now >= due)) {
                FoundationStaff f = hire(ow, s);
                if (f != null && due != null) {
                    WorldNews.record(srv, WorldNews.Kind.HIRE, "secretary", "the Council appointed " + f.getName().getString() + " as the Administrator's new secretary", 1, "the Council");
                    for (ServerPlayer q : srv.getPlayerList().getPlayers())
                        if (topBrass(q)) Career.message(q, O5.O5_7, "The Council has appointed " + f.getName().getString() + " as your secretary at " + s.name + ". She comes highly recommended.");
                }
            }
        }
        for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
            if (!topBrass(p)) continue;
            Sites.Site at = deskOf(p);
            if (at == null) {
                if (BRIEFED.remove(p.getUUID()) && Dialogue.hudOpen(p)) Dialogue.close(p);
                continue;
            }
            if (BRIEFED.contains(p.getUUID())) continue;
            FoundationStaff f = find(ow, at);
            if (f == null || f.distanceTo(p) > 40) continue;
            BRIEFED.add(p.getUUID());
            BlockPos stand = deskChair(at).north(2);
            f.getNavigation().moveTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, 1.1);
            Dialogue.begin(p, "npc:" + f.getId(), f.getName().getString(), "Executive Secretary to the Administrator", f.getId());
            Dialogue.hud(p, true);
            Dialogue.ask(p, false);
            brief(p, f, "brief");
        }
    }

    /** The site whose Administrator desk chair this player is sitting in. */
    static Sites.Site deskOf(ServerPlayer p) {
        Entity v = p.getVehicle();
        if (v == null || p.level() != p.server.overworld()) return null;
        BlockPos c = Seats.chairOf(v);
        for (Sites.Site s : Sites.get(p.server).sites) if (!s.name.equals("Site-01") && deskChair(s).equals(c)) return s;
        return null;
    }

    // ================================================================== the briefing

    private static String greeting(ServerPlayer p) {
        int h = (int) ((p.server.overworld().getDayTime() % Calendar.DAY) / Calendar.HOUR + 6) % 24;
        String a = Career.address(p);
        return (h < 12 ? "Good morning, " : h < 18 ? "Good afternoon, " : "Good evening, ") + a + ".";
    }

    public static void brief(ServerPlayer p, FoundationStaff f, String what) {
        CareerData d = Career.data(p);
        String me = f.getName().getString();
        int c = 0xF0A8C8;
        switch (what) {
            case "brief" -> {
                List<String> parts = new ArrayList<>();
                int unread = (int) d.messages.stream().filter(m -> !m.read).count();
                parts.add(unread == 0 ? "No new messages." : unread + " new message" + (unread == 1 ? "" : "s") + ", the latest from " + d.messages.get(0).sender + ".");
                long reqs = d.appts.stream().filter(a -> !a.confirmed).count();
                CareerData.Appt next = d.appts.stream().filter(a -> a.confirmed).min(Comparator.comparingLong(a -> a.at)).orElse(null);
                if (next != null) parts.add("Next on your calendar: " + Calendar.who(next) + ", " + Calendar.when(next.at) + ".");
                if (!Forces.data(p.server).deliveries.isEmpty()) parts.add("There's a delivery from the Council waiting; I'll put it in the chest by your desk.");
                if (reqs > 0) parts.add(reqs + " meeting request" + (reqs == 1 ? "" : "s") + " waiting for your answer.");
                CareerData.Appt weekly = Calendar.weekly(d);
                if (weekly != null) parts.add("The Council sits " + Calendar.when(weekly.at) + ", as usual" + (d.agenda.isEmpty() ? "." : ", with " + d.agenda.size() + " item" + (d.agenda.size() == 1 ? "" : "s") + " added so far, on top of the week's business and Peanut's cell."));
                Defence.Alert al = Defence.alertAt(deskOf(p) == null ? "" : deskOf(p).name);
                if (al != null) parts.add("And security reports " + al.org + " on the grounds. They're handling it.");
                int held = Containment.heldCount(p.server), loose = Containment.looseCount(p.server);
                if (held + loose > 0) parts.add("Containment: " + held + " anomal" + (held == 1 ? "y" : "ies") + " held across the Foundation" + (loose > 0 ? ", and " + loose + " loose. Security has the details." : ", nothing loose."));
                String people = com.breachprotocol.entity.Management.summary(p.server);
                if (!people.isEmpty()) parts.add(people);
                Dialogue.line(p, me, greeting(p) + " " + String.join(" ", parts), c);
            }
            case "brief_msg" -> {
                Dialogue.line(p, "You", "Read me the latest.", 0xB8E07A);
                if (d.messages.isEmpty()) Dialogue.line(p, me, "Nothing in your inbox at all.", c);
                else {
                    CareerData.Message m = d.messages.get(0);
                    m.read = true;
                    d.dirty = true;
                    Dialogue.line(p, me, "From " + m.sender + ": \"" + m.body + "\"", c);
                }
            }
            case "brief_cal" -> {
                Dialogue.line(p, "You", "What's on my calendar?", 0xB8E07A);
                List<CareerData.Appt> list = new ArrayList<>(d.appts);
                list.sort(Comparator.comparingLong(a -> a.at));
                if (list.isEmpty()) Dialogue.line(p, me, "Nothing booked.", c);
                for (int i = 0; i < Math.min(4, list.size()); i++) {
                    CareerData.Appt a = list.get(i);
                    Dialogue.line(p, me, (a.confirmed ? "" : "Request: ") + Calendar.who(a) + ", " + Calendar.when(a.at) + (a.seat == 0 ? "" : " (" + Calendar.about(a) + ")") + ".", c);
                }
            }
            case "brief_accept" -> {
                Dialogue.line(p, "You", "Accept the requests.", 0xB8E07A);
                int n = 0;
                for (CareerData.Appt a : d.appts) if (!a.confirmed) { a.confirmed = true; n++; }
                d.dirty = true;
                Dialogue.line(p, me, n == 0 ? "There's nothing waiting." : "Done. " + n + " meeting" + (n == 1 ? "" : "s") + " confirmed. I'll let them know.", c);
            }
            case "brief_news" -> {
                Dialogue.line(p, "You", "Anything I should know?", 0xB8E07A);
                List<WorldNews.Item> n = WorldNews.recent(p.server, 3);
                if (n.isEmpty()) Dialogue.line(p, me, "It's been quiet. I'll let you know the moment it isn't.", c);
                for (WorldNews.Item i : n) Dialogue.line(p, me, Character.toUpperCase(i.headline().charAt(0)) + i.headline().substring(1) + " (" + WorldNews.ago(p.server, i) + ").", c);
            }
            case "brief_site" -> {
                Dialogue.line(p, "You", "How is the site?", 0xB8E07A);
                Sites.Site s = deskOf(p);
                String name = s == null ? "this site" : s.name;
                int armed = s == null ? 0 : Forces.data(p.server).station(s.name).members.size();
                int held = 0, loose = 0;
                for (Containment.Entry e : Containment.at(p.server, name)) { if (e.status == Containment.HELD) held++; else if (e.status == Containment.LOOSE) loose++; }
                Dialogue.line(p, me, name + ": " + armed + " armed personnel on duty, stability at " + d.stability + "%, " + held + " anomal" + (held == 1 ? "y" : "ies") + " in its cells"
                        + (loose > 0 ? " and " + loose + " loose." : ".") + (Defence.alertAt(name) != null ? " We are under attack." : loose > 0 ? "" : " No incidents."), c);
            }
            case "brief_cells" -> {
                Dialogue.line(p, "You", "What's in containment?", 0xB8E07A);
                List<Containment.Entry> all = Containment.all(p.server);
                all.removeIf(e -> e.status == Containment.NEUTRALISED);
                all.sort(Comparator.comparingInt((Containment.Entry e) -> e.status == Containment.LOOSE ? 0 : 1).thenComparing(e -> e.site));
                if (all.isEmpty()) Dialogue.line(p, me, "The registry is empty. Nothing is held at any of our sites yet. Designate a cell from your phone and put something in it.", c);
                for (int i = 0; i < Math.min(6, all.size()); i++) {
                    Containment.Entry e = all.get(i);
                    long days = Containment.days(p.server, e.since);
                    Dialogue.line(p, me, e.status == Containment.LOOSE ? e.designation + " is loose at " + e.site + ", since " + WorldNews.ago(p.server, e.since) + "." + (e.order.equals("hunt") ? " A recapture team is on it." : e.order.equals("terminate") ? " There's a termination order on it." : "")
                            : e.designation + ", " + e.where() + ", " + (days == 0 ? "since today" : days + " day" + (days == 1 ? "" : "s")) + (e.breaches > 0 ? ", " + e.breaches + " breach" + (e.breaches == 1 ? "" : "es") + " on file" : "") + ".", c);
                }
                if (all.size() > 6) Dialogue.line(p, me, "And " + (all.size() - 6) + " more. The full registry is on your phone, under Containment.", c);
            }
            default -> {}
        }
        // she confirms the weekly session in person
        Dialogue.clearOptions(p);
        CareerData.Appt weekly = Calendar.weekly(d);
        if (weekly != null && (!weekly.confirmed || weekly.texted) && weekly.at - p.server.overworld().getDayTime() < 3L * Calendar.DAY) {
            Dialogue.line(p, me, "The Council is down for " + Calendar.when(weekly.at) + ". Shall I confirm it?", c);
            Dialogue.option(p, "Confirm the Council", "/council_week keep", "Keep this week's session", 0xFFC94D);
            Dialogue.option(p, "Move it to the next day", "/council_week next", "One day later, same time", 0xFFC94D);
            Dialogue.option(p, "Skip this week", "/council_week skip", "No session this week", 0xFFC94D);
        }
        Dialogue.option(p, "Read me the latest message", "/talk " + f.getId() + " brief_msg", "", 0x9FD8FF);
        Dialogue.option(p, "What's on my calendar?", "/talk " + f.getId() + " brief_cal", "", 0x9FD8FF);
        if (d.appts.stream().anyMatch(a -> !a.confirmed)) Dialogue.option(p, "Accept the meeting requests", "/talk " + f.getId() + " brief_accept", "", 0x9FD8FF);
        Dialogue.option(p, "Anything I should know?", "/talk " + f.getId() + " brief_news", "", 0x9FD8FF);
        Dialogue.option(p, "How is the site?", "/talk " + f.getId() + " brief_site", "", 0x9FD8FF);
        Dialogue.option(p, "What's in containment?", "/talk " + f.getId() + " brief_cells", "The registry: what's held where, and what's loose", 0x9FD8FF);
        Dialogue.option(p, "Personnel matters", "/talk " + f.getId() + " mg_page", "Hires, interviews, appointments and the registry", 0x9FD8FF);
        Dialogue.option(p, "That's all, thank you", "/talk " + f.getId() + " bye", "", 0xC8D0DA);
        Dialogue.send(p, true);
    }
}
