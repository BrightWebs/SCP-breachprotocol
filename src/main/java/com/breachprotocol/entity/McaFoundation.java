package com.breachprotocol.entity;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.career.*;
import com.breachprotocol.compat.McaCompat;
import com.breachprotocol.compat.McaHooks;
import com.breachprotocol.compat.Unleashed;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.*;

/**
 * The Foundation inside Minecraft Comes Alive. With MCA installed, the people who work for the
 * Foundation are MCA villagers: they keep MCA's whole life (homes, marriages, children, moods,
 * hearts, its interaction screen), and the Foundation adds a job, a post, a site, and a branch of
 * conversation ("The facility") that knows who you are and what they've seen.
 *
 * <p>Ordinary villagers notice things. Something anomalous passes the village, and they remember it
 * the way people do in the Foundation's world: half-believed, explained away ("a gas leak, they
 * said"), sometimes forgotten after a visit from men in white coats. What they'll tell you depends
 * on your clearance; what they believe depends on who they are.
 */
@EventBusSubscriber(modid = BreachProtocol.MODID)
public final class McaFoundation {
    private McaFoundation() {}

    public static void install() { McaHooks.install(McaFoundation::condition, McaFoundation::action); }

    // ================================================================== employees (villagers on the payroll)

    /** An MCA villager by id, wherever they're loaded. */
    static Entity villager(MinecraftServer s, UUID id) {
        if (id == null || !McaCompat.present()) return null;
        for (ServerLevel l : s.getAllLevels()) { Entity e = l.getEntity(id); if (e != null && e.isAlive() && McaCompat.isVillager(e)) return e; }
        return null;
    }

    static boolean employee(Entity v) { return v.getPersistentData().contains("bp_emp_role"); }

    static FoundationStaff.Role role(Entity v) { return FoundationStaff.Role.values()[Math.floorMod(v.getPersistentData().getInt("bp_emp_role"), FoundationStaff.Role.values().length)]; }

    static String site(Entity v) { return v.getPersistentData().getString("bp_emp_site"); }

    /** Takes a villager onto the payroll: a job, a post to go to in working hours, a file in the registry. */
    static void employ(Entity v, FoundationStaff.Role role, String site, BlockPos post) {
        CompoundTag pd = v.getPersistentData();
        pd.putInt("bp_emp_role", role.ordinal());
        pd.putString("bp_emp_site", site);
        pd.putLong("bp_emp_post", post.asLong());
        pd.putLong("bp_emp_since", v.level().getGameTime());
        pd.remove("bp_walkin");
        pd.remove("bp_cand");
        pd.remove("bp_seek");
        MinecraftServer s = v.getServer();
        Registry.Rec e = record(v);
        e.status = "active";
        e.job = role.title;
        e.site = site;
        e.note = "";
        Registry.dirty(s);
    }

    static void dismiss(Entity v) {
        CompoundTag pd = v.getPersistentData();
        for (String k : List.of("bp_emp_role", "bp_emp_site", "bp_emp_post", "bp_emp_since", "bp_emp_off")) pd.remove(k);
        Registry.Rec e = record(v);
        e.status = "offsite";
        e.job = "lives in the village";
        e.note = "worked for the Foundation once; doesn't remember it";
        Registry.dirty(v.getServer());
    }

    /** Their registry file (created from what MCA knows about them). */
    static Registry.Rec record(Entity v) {
        MinecraftServer s = v.getServer();
        Registry reg = Registry.get(s);
        Registry.Rec e = reg.people.get(v.getUUID());
        if (e == null) {
            e = new Registry.Rec();
            e.id = v.getUUID();
            e.born = s.overworld().getGameTime() - (long) (20 + (int) (Person.geneOf(v.getUUID(), 30) * 22)) * Registry.YEAR;
            e.status = "offsite";
            e.job = "lives in the village";
            e.genome = Registry.genomeOf(v.getUUID());
            reg.people.put(e.id, e);
        }
        e.name = v.getName().getString();
        e.female = McaCompat.female(v);
        e.mca = true;
        reg.setDirty();
        return e;
    }

    /** With MCA, our own civilian staff become MCA villagers (same name, look and file). Armed units and the secretary stay as they are. */
    static boolean toVillager(FoundationStaff f) {
        if (!McaCompat.present() || f.unit() != null || f.role() == FoundationStaff.Role.GUARD || f.role() == FoundationStaff.Role.SECRETARY || f.post() == null) return false;
        if (!(f.level() instanceof ServerLevel l)) return false;
        CompoundTag pd = f.getPersistentData();
        if (pd.contains("bp_cand") || pd.contains("bp_walkin") || pd.contains("bp_leaving") || Dialogue.talkingTo(f) != null) return false;
        Mob v = McaHooks.create(l, f.isFemale());
        if (v == null) return false;
        UUID id = f.getUUID();
        String name = f.getName().getString();
        BlockPos post = f.post();
        Sites.Site st = Sites.at(l, post);
        String site = st == null ? Registry.of(f).site : st.name;
        double x = f.getX(), y = f.getY(), z = f.getZ();
        float yaw = f.getYRot();
        float[] g = {f.gene(1), f.melanin(), f.hemoglobin(), f.eumelanin(), f.pheomelanin(), f.gene(7), f.bustSize(), f.gene(9), f.gene(10), f.gene(12)};
        FoundationStaff.Role role = f.role();
        Seats.stand(f);
        f.discard();
        v.setUUID(id);
        v.moveTo(x, y, z, yaw, 0);
        v.finalizeSpawn(l, l.getCurrentDifficultyAt(v.blockPosition()), MobSpawnType.CONVERSION, null);
        l.addFreshEntity(v);
        McaHooks.setGender(v, f.isFemale());
        McaHooks.setName(v, name);
        String[] genes = {"SKIN", "MELANIN", "HEMOGLOBIN", "EUMELANIN", "PHEOMELANIN", "FACE", "BREAST", "SIZE", "WIDTH", "EYE_BRIGHTNESS"};
        for (int i = 0; i < genes.length; i++) McaHooks.setGene(v, genes[i], g[i]);
        v.setPersistenceRequired();
        employ(v, role, site, post);
        return true;
    }

    // ================================================================== what they notice

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        MinecraftServer s = event.getServer();
        if (!McaCompat.present() || s.getTickCount() % 40 != 23) return;
        long now = s.overworld().getGameTime(), tod = s.overworld().getDayTime() % 24000;
        boolean work = tod >= 1000 && tod <= 11000; // 07:00 to 17:00
        boolean scan = s.getTickCount() % 200 == 23;
        for (ServerLevel l : s.getAllLevels()) {
            for (Entity e : l.getAllEntities()) {
                if (!McaCompat.isVillager(e) || !(e instanceof Villager v)) continue;
                CompoundTag pd = v.getPersistentData();
                // the working day: to their post, and home again (MCA's own schedule takes them home)
                if (employee(v) && work && now >= pd.getLong("bp_emp_off") && !pd.contains("bp_cand")) {
                    BlockPos post = BlockPos.of(pd.getLong("bp_emp_post"));
                    if (v.blockPosition().distSqr(post) > 36) v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(post, 0.6F, 3));
                }
                if (pd.contains("bp_walkin") || pd.contains("bp_cand")) {
                    BlockPos w = BlockPos.of(pd.getLong("bp_wait"));
                    if (pd.contains("bp_wait") && v.blockPosition().distSqr(w) > 9) v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(w, 0.6F, 1));
                }
                if (!scan) continue;
                // something anomalous in plain sight
                for (Mob m : l.getEntitiesOfClass(Mob.class, v.getBoundingBox().inflate(24), Unleashed::isAnomaly)) {
                    if (Unleashed.isContained(m) || !v.hasLineOfSight(m)) continue;
                    boolean fresh = now - pd.getLong("bp_saw_t") > 6000;
                    pd.putString("bp_saw", Unleashed.designation(m));
                    pd.putLong("bp_saw_t", now);
                    pd.putInt("bp_saw_x", m.getBlockX());
                    pd.putInt("bp_saw_z", m.getBlockZ());
                    if (fresh && pd.hasUUID("bp_informant")) {
                        ServerPlayer p = s.getPlayerList().getPlayer(pd.getUUID("bp_informant"));
                        if (p != null) Career.message(p, v.getName().getString() + " (informant)", "I saw it. The thing you told me to watch for. Near " + m.getBlockX() + ", " + m.getBlockZ() + ". I didn't go closer. You said not to.");
                    }
                    break;
                }
                Sites.Site site = null;
                for (Sites.Site x : Sites.get(s).sites) if (x.dim.equals(l.dimension().location().toString()) && v.blockPosition().distSqr(new BlockPos(x.x, x.y, x.z)) < 220 * 220) site = x;
                if (site != null && Defence.alertAt(site.name) != null) pd.putLong("bp_attack_t", now);
            }
        }
    }

    // ================================================================== conditions (for MCA's dialogue files)

    static int tier(ServerPlayer p) {
        Scene.Tier t = Scene.tier(p);
        return t.ordinal(); // 0 D-Class, 1 junior, 2 staff, 3 director, 4 council, 5 administrator
    }

    static boolean condition(String key, Entity v, ServerPlayer p) {
        long now = v.level().getGameTime();
        CompoundTag pd = v.getPersistentData();
        if (key.startsWith("route_")) return key.substring(6).equals(route(v, p));
        return switch (key) {
            case "employee" -> employee(v);
            case "walkin" -> pd.contains("bp_walkin") || pd.contains("bp_cand");
            case "boss" -> employee(v) && p != null && tier(p) >= 2;
            case "colleague" -> employee(v) && p != null && tier(p) < 2;
            case "civilian" -> p == null || tier(p) <= 0;
            case "junior" -> p != null && tier(p) >= 1;
            case "staff" -> p != null && tier(p) >= 2;
            case "director" -> p != null && tier(p) >= 3;
            case "council" -> p != null && tier(p) >= 4;
            case "saw" -> now - pd.getLong("bp_saw_t") < 24000 * 3 && !pd.getBoolean("bp_saw_cover");
            case "covered" -> now - pd.getLong("bp_saw_t") < 24000 * 3 && pd.getBoolean("bp_saw_cover");
            case "amnestic" -> now - pd.getLong("bp_amn_t") < 24000 * 4;
            case "attack" -> now - pd.getLong("bp_attack_t") < 24000 * 2;
            case "near_site" -> nearSite(v) != null;
            case "informant" -> pd.hasUUID("bp_informant");
            default -> false;
        };
    }

    /** Which branch of "The facility" this conversation takes: exactly one applies. */
    static String route(Entity v, ServerPlayer p) {
        CompoundTag pd = v.getPersistentData();
        int t = p == null ? 0 : tier(p);
        if (pd.contains("bp_walkin") || pd.contains("bp_cand")) return t >= 3 ? "applicant_hire" : "applicant";
        if (employee(v)) return t >= 2 ? "boss" : "colleague";
        if (t >= 3) return "director";
        if (t >= 1) return "staff";
        return "civilian";
    }

    static Sites.Site nearSite(Entity v) {
        MinecraftServer s = v.getServer();
        Sites.Site best = null;
        double bd = 260 * 260;
        for (Sites.Site x : Sites.get(s).sites) {
            if (!x.dim.equals(v.level().dimension().location().toString())) continue;
            double d = v.blockPosition().distSqr(new BlockPos(x.x, x.y, x.z));
            if (d < bd) { bd = d; best = x; }
        }
        return best;
    }

    // ================================================================== actions: what they say, composed

    private static void say(ServerPlayer p, Entity v, String line) {
        Personality pe = personality(v);
        String voiced = pe.voice(line, p.getGameProfile().getName(), new Random());
        McaHooks.say(p, Component.literal(voiced));
    }

    private static void note(ServerPlayer p, String text) { p.displayClientMessage(Component.literal(text).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), false); }

    static Personality personality(Entity v) {
        try { return Personality.valueOf(McaCompat.personality(v).toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException e) { return Personality.RELAXED; }
    }

    private static String pick(Random r, String... o) { return o[r.nextInt(o.length)]; }

    private static String dir(Entity v, int x, int z) {
        double dx = x - v.getX(), dz = z - v.getZ();
        return Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? "east" : "west") : (dz > 0 ? "south" : "north");
    }

    static void action(String key, Entity v, ServerPlayer p) {
        Random r = new Random();
        CompoundTag pd = v.getPersistentData();
        Personality pe = personality(v);
        long now = v.level().getGameTime();
        String a = Career.address(p);
        int hearts = McaHooks.hearts(v, p);
        boolean saw = condition("saw", v, p), amn = condition("amnestic", v, p);
        String what = pd.getString("bp_saw");
        Sites.Site site = nearSite(v);
        String sn = site == null ? "the facility" : site.name;
        switch (key) {
            // ---------------------------------------------------------- openings, by who you are
            case "intro_civilian" -> say(p, v, pick(r, "You're one of the people from up the hill, aren't you? The 'pharmaceutical plant'.", "You've got that look. The facility look.", "I've seen your trucks. Three in the morning, every week."));
            case "intro_staff" -> say(p, v, pick(r, "Foundation badge. All right. What does the Foundation want with me?", "You're from " + sn + ". Is something wrong?", "An official visit. Should I be nervous?"));
            case "intro_director" -> say(p, v, pick(r, "You're someone important up there. People step aside when you walk through the square.", "The one in charge. I've heard about you. Nobody says your name, though.", a + "? They told us someone senior might come."));
            case "intro_boss" -> say(p, v, switch (pe) {
                case CRABBY -> "Yes, " + a + "? I'm on my break. Supposedly.";
                case ANXIOUS -> "Oh! " + a + ". Is this about my work? Is it bad?";
                case UPBEAT, EXTROVERTED, FRIENDLY -> "Morning, " + a + "! Good to see you down here.";
                default -> a + ". What do you need?";
            });
            case "intro_colleague" -> say(p, v, pick(r, "Oh, hey. You're on " + site(v) + " staff too? Rough week.", "Colleague! Don't tell anyone in the village, they think I sort parcels."));
            case "intro_applicant" -> say(p, v, pick(r, "I came about the job. I don't mind what it is. I just... want to know what's up there.", "Hello! Personnel said to wait. Are you the one who decides?"));

            // ---------------------------------------------------------- what they've seen (half-believed)
            case "strange" -> {
                if (amn) say(p, v, pick(r, "Strange? No. I've had headaches, though. And there's a day last week I can't account for. Funny, that.", "Nothing. I'm sure of it. Why does everyone keep asking me that?"));
                else if (saw && tier(p) >= 3) {
                    say(p, v, "Since it's you asking: something, out to the " + dir(v, pd.getInt("bp_saw_x"), pd.getInt("bp_saw_z")) + ". Near the old path. It didn't move like an animal. I haven't told anyone else. Who would believe me?");
                    note(p, "(They saw " + what + ", near " + pd.getInt("bp_saw_x") + ", " + pd.getInt("bp_saw_z") + ".)");
                } else if (saw) say(p, v, switch (pe) {
                    case GLOOMY -> "I saw something by the treeline that I'd rather not describe. I'm telling myself it was a bear. It wasn't a bear.";
                    case ODD -> "Oh, the thing? Out " + dir(v, pd.getInt("bp_saw_x"), pd.getInt("bp_saw_z")) + "? It looked at me. I waved. Was that wrong?";
                    case ANXIOUS, SENSITIVE -> "I- no. Maybe. I think I was hallucinating. I haven't been sleeping well. Please don't make me think about it.";
                    case CRABBY -> "Yes, I saw something. No, I don't want to talk about it. Yes, I'm sure it was real.";
                    case UPBEAT, PLAYFUL -> "Something huge went past the well last night! Everyone says it was a cow. Cows don't do that.";
                    default -> pick(r, "There was... something. Out to the " + dir(v, pd.getInt("bp_saw_x"), pd.getInt("bp_saw_z")) + ". Probably a trick of the light.", "I thought I saw something. Then I decided I didn't. That seemed healthier.");
                });
                else if (condition("covered", v, p)) say(p, v, "Only the gas leak. Your people said it was a gas leak. So it was a gas leak.");
                else say(p, v, pick(r, "Strange? Only you lot.", "No. It's been quiet. Is it supposed to be loud?", "The dogs barked at nothing on Tuesday. That's about it."));
            }
            case "suspicious" -> say(p, v, condition("attack", v, p) ? "Men with guns came through two nights ago. Not your sort. Asked the way to " + sn + ". We didn't tell them. Much."
                    : pick(r, "A man in a long coat was selling 'protective charms' last week. He seemed to know too much.", "Nobody. Unless you count the woman who keeps photographing your fence.", "No strangers. We'd notice. We notice everything here."));
            case "facility" -> say(p, v, hearts >= 30 ? pick(r, "Honestly? People think it's a prison. Or a lab. My uncle says it's where the government keeps the bad weather.", "The children dare each other to touch the fence. Nobody ever does twice.")
                    : pick(r, "A pharmaceutical plant, they say. Biggest one I've ever seen with no lorries of medicine leaving it.", "We don't talk about up there. It's bad luck.", "Good jobs, I hear. Nobody who takes one ever says what they do."));
            case "cover" -> {
                if (!saw) { say(p, v, "Gas leak? What gas leak?"); break; }
                pd.putBoolean("bp_saw_cover", true);
                say(p, v, switch (pe) {
                    case GLOOMY, CRABBY -> "A gas leak. Right. And I'm the Queen of Spain. ...Fine. Gas leak.";
                    case ODD -> "Gas can do that? Make things walk? Science is amazing.";
                    default -> pick(r, "Oh! A gas leak. That... actually explains a lot. Thank you. I thought I was losing my mind.", "A gas leak. Yes. That must be it. That's what I'll tell everyone.");
                });
                Career.changeStability(p, 1);
            }
            case "amnestic" -> {
                if (tier(p) < 3 && !Career.rank(p).atLeast(Rank.LEVEL_4)) { say(p, v, "Why are you offering me a drink? I don't know you."); break; }
                long day = v.level().getDayTime() / 24000;
                if (pd.getLong("bp_amn_day") == day + 1) { note(p, "(They've had a dose today. Any more would be dangerous.)"); say(p, v, "No more, thank you. I feel... strange enough."); break; }
                pd.putLong("bp_amn_day", day + 1);
                pd.putLong("bp_amn_t", now);
                pd.remove("bp_saw"); pd.remove("bp_saw_t"); pd.remove("bp_saw_cover");
                say(p, v, pick(r, "Oh, thank you... that's lovely... I'm sorry, have we met? I feel like we were talking about something.", "Mm. Sweet. What... what was I saying? Never mind. It's gone."));
                note(p, "(Class-A amnestic. Whatever they saw, they've lost the last day or so.)");
                Career.changeStability(p, 1);
            }
            case "informant" -> {
                if (tier(p) < 3) { say(p, v, "Watch for what? You're not making sense."); break; }
                if (hearts < 10) { say(p, v, "Spy for you? I barely know you."); McaHooks.reward(v, p, -2); break; }
                pd.putUUID("bp_informant", p.getUUID());
                say(p, v, pe == Personality.ANXIOUS ? "I- all right. If I see anything, I'll... tell you. Not anyone else. Just you." : "Keep my eyes open? I already do. Fine. If anything strange comes past the village, you'll hear about it first.");
                note(p, "(They'll message you when they see something anomalous near the village.)");
            }
            case "job" -> {
                if (tier(p) < 3) { say(p, v, "A job? You'd have to ask someone more senior. No offence."); break; }
                if (employee(v)) { say(p, v, "I already work for you. Remember?"); break; }
                boolean jobless = v instanceof Villager vv && (vv.getVillagerData().getProfession() == net.minecraft.world.entity.npc.VillagerProfession.NONE || vv.getVillagerData().getProfession() == net.minecraft.world.entity.npc.VillagerProfession.NITWIT);
                int chance = (jobless ? 70 : 20) + Math.max(0, hearts) / 4 + (pe == Personality.GREEDY || pe == Personality.EXTROVERTED ? 15 : pe == Personality.ANXIOUS || pe == Personality.PEACEFUL ? -25 : 0);
                if (r.nextInt(100) < chance && site != null) {
                    pd.putString("bp_seek", site.name);
                    pd.putLong("bp_seek_t", now);
                    say(p, v, pe == Personality.GREEDY ? "Does it pay? It pays. I'm coming." : pick(r, "A real job? Up there? ...Yes. I'll walk up to " + site.name + " now.", "I've always wondered what goes on up there. All right."));
                    note(p, "(They set off for " + site.name + ". They'll wait in the lobby.)");
                } else say(p, v, pick(r, "Work up there? No thank you. I've heard the sirens.", "I've got my own work. Thank you, though.", "Maybe one day. Not today."));
            }

            // ---------------------------------------------------------- employees, to their boss
            case "emp_report" -> {
                Scene.Tier t = Scene.tier(p);
                List<String> parts = new ArrayList<>();
                parts.add(role(v).title + " at " + site(v) + ".");
                WorldNews.Item n = null;
                for (WorldNews.Item i : WorldNews.recent(p.server, 3)) if (Clearance.knows(t, i) && !Talk.mine(i, p)) { n = i; break; }
                if (n != null) parts.add("Latest on site: " + n.headline() + " " + WorldNews.ago(p.server, n) + ".");
                Defence.Alert al = Defence.alertAt(site(v));
                parts.add(al != null ? al.org + " on the grounds. We're keeping our heads down." : "No incidents on my shift.");
                if (saw) parts.add("And I saw " + what + " out of containment, near " + pd.getInt("bp_saw_x") + ", " + pd.getInt("bp_saw_z") + ". I reported it.");
                say(p, v, Clearance.filter(p, String.join(" ", parts)));
            }
            case "emp_how" -> say(p, v, hearts >= 40 ? pick(r, "Honestly? I love it. I hated sorting turnips.", "Best decision I ever made. Don't tell my mother I said that.")
                    : hearts < 0 ? pick(r, "It's work. It pays. I don't sleep much.", "Some days I miss the village. The village didn't have sirens.")
                    : pick(r, "Good. Strange, but good.", "I'm learning. Mostly which doors not to open."));
            case "emp_post" -> { pd.remove("bp_emp_off"); say(p, v, "Back to it, then."); }
            case "emp_dayoff" -> { pd.putLong("bp_emp_off", now + 24000); McaHooks.mood(v, 10); say(p, v, pick(r, "A day off? Thank you! I'm going to sleep for twelve hours.", "Really? I'll go home and see the family. Thank you, " + a + ".")); }
            case "emp_commend" -> { McaHooks.reward(v, p, 8); McaHooks.mood(v, 5); say(p, v, pick(r, "Thank you, " + a + ". That means a lot coming from you.", "I- thank you. I'll keep it up.")); }
            case "emp_dismiss" -> {
                if (tier(p) < 3) { say(p, v, "You can't fire me. You're not my boss."); break; }
                dismiss(v);
                McaHooks.reward(v, p, -20);
                say(p, v, pick(r, "...Oh. I see. I'll go home, then.", "Just like that? After everything I've seen up there?"));
                note(p, "(They'll be given the standard amnestic exit. The village will think they were let go from the plant.)");
                pd.putLong("bp_amn_t", now);
            }

            // ---------------------------------------------------------- applicants (a villager waiting to be seen)
            case "app_about" -> say(p, v, "I'm from the village. I've done a bit of everything. " + switch (pe) {
                case GREEDY -> "And I'm told your pay is very good.";
                case ANXIOUS -> "I'm a hard worker. I worry a lot. It makes me careful.";
                case EXTROVERTED -> "Everyone knows me. I could get anyone to do anything.";
                default -> "I learn quickly.";
            });
            case "app_why" -> say(p, v, saw ? "Because I saw something I can't explain, and I want to know I'm not mad." : pick(r, "Because the village is small and the world is big, and you seem to be where the world keeps its secrets.", "Good wages. And honestly? Curiosity."));
            case "app_breach" -> say(p, v, pe == Personality.ANXIOUS || pe == Personality.SENSITIVE ? "Run? No- hide. Hide and wait for someone with a gun." : "Get everyone out of the room, shut the door, and find someone who knows what they're doing.");
            default -> {
                if (key.startsWith("hire_")) {
                    if (tier(p) < 3) { say(p, v, "I think I need to speak to someone who can actually hire me."); break; }
                    FoundationStaff.Role role = switch (key.substring(5)) {
                        case "security" -> FoundationStaff.Role.GUARD; case "medical" -> FoundationStaff.Role.MEDIC; case "technician" -> FoundationStaff.Role.TECHNICIAN;
                        case "admin" -> FoundationStaff.Role.CLERK; case "janitor" -> FoundationStaff.Role.JANITOR; default -> FoundationStaff.Role.RESEARCHER;
                    };
                    if (hire(p, v, role)) say(p, v, pick(r, "I'm hired? Thank you! When do I start? Now? Now. Right.", "Thank you. I won't let you down. ...What does a " + role.title.toLowerCase(Locale.ROOT) + " actually do?"));
                    else say(p, v, "Personnel says there's no desk for me. Maybe next time.");
                } else if (key.equals("reject")) {
                    pd.remove("bp_walkin"); pd.remove("bp_cand"); pd.remove("bp_wait");
                    pd.putLong("bp_seek_no", now + 24000L * 3);
                    say(p, v, pick(r, "Oh. All right. Thank you for seeing me.", "I understand. I'll go home."));
                }
            }
        }
    }

    /** Hire a villager into a role at the nearest site (the post comes from colleagues already working there). */
    static boolean hire(ServerPlayer p, Entity v, FoundationStaff.Role role) {
        String siteName = v.getPersistentData().getString("bp_walkin");
        if (siteName.isEmpty()) { Sites.Site s = nearSite(v); if (s == null) return false; siteName = s.name; }
        Management.Vacancy vac = Management.direct(p, siteName, role);
        if (vac == null) return false;
        vac.state = "done";
        Management.data(p.server).setDirty();
        employ(v, role, siteName, BlockPos.of(vac.pos));
        WorldNews.record(p.server, WorldNews.Kind.HIRE, "hire", v.getName().getString() + " from the village joined " + siteName + " as " + role.title.toLowerCase(Locale.ROOT), 1, "");
        Registry.log(p.server, v.getName().getString() + " was hired from the village as " + role.title.toLowerCase(Locale.ROOT) + " at " + siteName + ".");
        return true;
    }
}
