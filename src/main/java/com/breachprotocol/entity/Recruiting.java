package com.breachprotocol.entity;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.career.*;
import com.breachprotocol.compat.McaCompat;
import com.breachprotocol.compat.Unleashed;
import com.breachprotocol.registry.ModEntities;
import com.breachprotocol.world.SiteTower;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.*;

/**
 * With Minecraft Comes Alive installed: the village and the Foundation. Villagers near a site who
 * have no job (or have just come of age) set off to the Foundation to look for work. They walk to
 * the tower, wait in the lobby and apply; Personnel puts them on shortlists, or you hire them on
 * the spot. They keep who they were: name, looks (MCA's genes), personality, traits and their family
 * in the village. Anyone turned away or dismissed walks back home as the villager they were.
 * Sneak-and-right-click a villager to talk to them as the Foundation: ask what they've seen, offer
 * them a job, or (with the clearance) give them amnestics. Without MCA none of this runs.
 */
@EventBusSubscriber(modid = BreachProtocol.MODID)
public final class Recruiting {
    private Recruiting() {}

    public static final class Data extends SavedData {
        final Map<UUID, CompoundTag> villagers = new HashMap<>();
        public static final SavedData.Factory<Data> FACTORY = new SavedData.Factory<>(Data::new, Data::load, null);

        static Data load(CompoundTag t, HolderLookup.Provider p) {
            Data d = new Data();
            CompoundTag v = t.getCompound("v");
            for (String k : v.getAllKeys()) try { d.villagers.put(UUID.fromString(k), v.getCompound(k)); } catch (IllegalArgumentException ignored) {}
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag t, HolderLookup.Provider p) {
            CompoundTag v = new CompoundTag();
            for (Map.Entry<UUID, CompoundTag> e : villagers.entrySet()) v.put(e.getKey().toString(), e.getValue());
            t.put("v", v);
            return t;
        }
    }

    static Data data(MinecraftServer s) { return s.overworld().getDataStorage().computeIfAbsent(Data.FACTORY, "breachprotocol_villagers"); }

    /** Was this person a villager (so they have a home to go back to)? */
    static boolean fromVillage(MinecraftServer s, UUID id) { return data(s).villagers.containsKey(id); }

    private static Sites.Site site(MinecraftServer s, String name) { for (Sites.Site x : Sites.get(s).sites) if (x.name.equals(name)) return x; return null; }

    private static boolean tower(ServerLevel l, Sites.Site s) {
        BlockPos d = Secretary.origin(s).offset(16, SiteTower.y(SiteTower.TOP) + 1, 25);
        return l.isLoaded(d) && BuiltInRegistries.BLOCK.getKey(l.getBlockState(d).getBlock()).getPath().equals("office_desk");
    }

    /** Where job seekers walk to: the tower's front door, or the middle of the site. */
    static BlockPos entrance(ServerLevel l, Sites.Site s) {
        if (tower(l, s)) return Secretary.origin(s).offset(16, 1, SiteTower.T + 3);
        return new BlockPos(s.x, s.y, s.z);
    }

    /** Where they wait to be seen: just inside the lobby. */
    static BlockPos lobby(ServerLevel l, Sites.Site s, int i) {
        if (tower(l, s)) return Secretary.origin(s).offset(10 + (i % 4) * 3, 1, SiteTower.T - 4);
        return new BlockPos(s.x + i % 3, s.y, s.z + 2);
    }

    private static boolean jobless(Villager v) {
        VillagerProfession pr = v.getVillagerData().getProfession();
        return pr == VillagerProfession.NONE || pr == VillagerProfession.NITWIT;
    }

    private static void tellDeciders(MinecraftServer s, String msg) {
        for (ServerPlayer p : s.getPlayerList().getPlayers()) if (Management.mayAttend(p)) p.displayClientMessage(Component.literal("▣ " + msg).withStyle(ChatFormatting.AQUA), false);
    }

    // ================================================================== the walk to work

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        MinecraftServer s = event.getServer();
        if (s.getTickCount() % 40 != 17 || !McaCompat.present()) return;
        long now = s.overworld().getGameTime();
        Random r = new Random();
        boolean scan = s.getTickCount() % 1200 == 17;
        for (Sites.Site site : Sites.get(s).sites) {
            ServerLevel l = null;
            for (ServerLevel x : s.getAllLevels()) if (x.dimension().location().toString().equals(site.dim)) l = x;
            if (l == null) continue;
            BlockPos centre = new BlockPos(site.x, site.y, site.z);
            if (!l.isLoaded(centre)) continue;
            AABB area = new AABB(centre).inflate(192, 64, 192);
            List<Villager> vs = l.getEntitiesOfClass(Villager.class, area, McaCompat::isVillager);
            int seeking = 0;
            BlockPos door = entrance(l, site);
            for (Villager v : vs) {
                CompoundTag pd = v.getPersistentData();
                if (!pd.getString("bp_seek").equals(site.name)) continue;
                seeking++;
                // on their way: MCA villagers walk by their brain's walk target
                v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(door, 0.6F, 2));
                if (v.blockPosition().distSqr(door) < 7 * 7) arrive(l, site, v);
                else if (now - pd.getLong("bp_seek_t") > 24000) { pd.remove("bp_seek"); pd.putLong("bp_seek_no", now + 24000 * 2); }
            }
            if (!scan) continue;
            for (Villager v : vs) {
                if (seeking >= 2) break;
                CompoundTag pd = v.getPersistentData();
                if (pd.contains("bp_seek") || now < pd.getLong("bp_seek_no") || v.isBaby() || !McaCompat.ageState(v).equals("ADULT") || !jobless(v)) continue;
                if (r.nextInt(100) >= 10) continue;
                pd.putString("bp_seek", site.name);
                pd.putLong("bp_seek_t", now);
                seeking++;
                tellDeciders(s, v.getName().getString() + " has set off from the village for " + site.name + ", looking for work.");
            }
        }
        // walk-ins nobody saw go home after a day
        for (ServerLevel l : s.getAllLevels())
            for (FoundationStaff f : l.getEntities(ModEntities.STAFF.get(), e -> e.getPersistentData().contains("bp_walkin") && !e.getPersistentData().contains("bp_cand")))
                if (now - f.getPersistentData().getLong("bp_walkin_t") > 24000) {
                    tellDeciders(s, f.getName().getString() + " waited a day in the lobby and went home. Personnel kept their details.");
                    goHome(f);
                }
    }

    /** At the door: they become a Foundation applicant (keeping everything MCA knew about them) and wait in the lobby. */
    static FoundationStaff arrive(ServerLevel l, Sites.Site site, Villager v) {
        MinecraftServer s = l.getServer();
        v.getPersistentData().remove("bp_seek");
        int waiting = 0;
        for (FoundationStaff f : l.getEntities(ModEntities.STAFF.get(), e -> site.name.equals(e.getPersistentData().getString("bp_walkin")))) waiting++;
        FoundationStaff f = convert(l, v, FoundationStaff.Role.CLERK, lobby(l, site, waiting));
        if (f == null) return null;
        f.getPersistentData().putString("bp_walkin", site.name);
        f.getPersistentData().putLong("bp_walkin_t", s.overworld().getGameTime());
        // a post going? they go straight onto the shortlist
        for (Management.Vacancy vac : Management.open(s)) {
            if (!vac.site.equals(site.name) || vac.kind.equals("head") || vac.state.equals("running")) continue;
            vac.cands.add(0, Management.candidateFrom(s, f, vac.role));
            Management.data(s).setDirty();
            break;
        }
        WorldNews.record(s, WorldNews.Kind.HIRE, "walkin", f.getName().getString() + " walked in from the village to ask for a job at " + site.name, 1, "");
        tellDeciders(s, f.getName().getString() + " has arrived at " + site.name + " and is waiting in the lobby to apply. Talk to them, or hire them from the Staff app.");
        return f;
    }

    /** The villager becomes Foundation staff (an applicant for now), with the same identity. */
    static FoundationStaff convert(ServerLevel l, Villager v, FoundationStaff.Role role, BlockPos wait) {
        MinecraftServer s = l.getServer();
        UUID id = v.getUUID();
        String name = v.getName().getString();
        boolean female = McaCompat.female(v);
        // their genes, as MCA had them
        StringBuilder g = new StringBuilder();
        for (int i = 0; i < Registry.GENES; i++) {
            float val = switch (i) {
                case 1 -> McaCompat.gene(v, "SKIN"); case 2 -> McaCompat.gene(v, "MELANIN"); case 4 -> McaCompat.gene(v, "HEMOGLOBIN");
                case 5 -> McaCompat.gene(v, "EUMELANIN"); case 6 -> McaCompat.gene(v, "PHEOMELANIN"); case 7 -> McaCompat.gene(v, "FACE");
                case 8 -> McaCompat.gene(v, "BREAST"); case 9 -> McaCompat.gene(v, "SIZE"); case 10 -> McaCompat.gene(v, "WIDTH"); case 12 -> McaCompat.gene(v, "EYE_BRIGHTNESS");
                case 20 -> personalityGene(McaCompat.personality(v), id);
                default -> -1;
            };
            if (val < 0) val = Person.geneOf(id, i);
            g.append(String.format("%02x", Math.min(255, Math.max(0, (int) (val * 255)))));
        }
        String genome = g + "|D";
        EnumSet<Traits> traits = EnumSet.noneOf(Traits.class);
        for (String t : McaCompat.traits(v)) {
            Traits m = switch (t) {
                case "left_handed" -> Traits.LEFT_HANDED; case "color_blind" -> Traits.COLOUR_BLIND; case "heterochromia" -> Traits.HETEROCHROMIA;
                case "athletic" -> Traits.ATHLETIC; case "weak" -> Traits.WEAK; case "tough" -> Traits.TOUGH; case "lactose_intolerance" -> Traits.LACTOSE;
                case "diabetes" -> Traits.DIABETIC; case "vegetarian" -> Traits.VEGETARIAN; case "infertile" -> Traits.INFERTILE; case "albinism" -> Traits.ALBINISM;
                case "dwarfism" -> Traits.DWARFISM; case "bisexual" -> Traits.BISEXUAL; case "homosexual" -> Traits.HOMOSEXUAL;
                default -> null;
            };
            if (m != null) traits.add(m);
        }
        int age = 20 + (int) (Person.geneOf(id, 30) * 22);
        // the registry, with their village family
        Registry reg = Registry.get(s);
        Registry.Rec e = reg.people.get(id);
        if (e == null) {
            e = new Registry.Rec();
            e.id = id;
            reg.people.put(id, e);
        }
        e.name = name;
        e.female = female;
        e.born = s.overworld().getGameTime() - (long) age * Registry.YEAR;
        e.status = "applicant";
        e.job = "villager";
        e.genome = genome;
        e.traits = Traits.write(traits);
        e.note = "came from the village";
        McaCompat.Family fam = McaCompat.family(l, v);
        if (fam != null) {
            e.father = link(s, fam.father(), fam.fatherName(), false);
            e.mother = link(s, fam.mother(), fam.motherName(), true);
            UUID sp = link(s, fam.partner(), fam.partnerName(), !female);
            if (sp != null) { e.spouse = sp; e.rel = "married"; Registry.Rec o = reg.people.get(sp); if (o != null) { o.spouse = id; o.rel = "married"; } }
            for (Map.Entry<UUID, String> k : fam.children().entrySet()) {
                UUID kid = link(s, k.getKey(), k.getValue(), false);
                Registry.Rec kr = reg.people.get(kid);
                if (kr != null) { if (female) kr.mother = id; else kr.father = id; }
            }
        }
        reg.setDirty();
        // keep the villager exactly as they were, for if they ever go home
        CompoundTag nbt = new CompoundTag();
        if (!v.save(nbt)) return null;
        data(s).villagers.put(id, nbt);
        data(s).setDirty();
        BlockPos at = v.blockPosition();
        float yaw = v.getYRot();
        v.discard();
        FoundationStaff f = ModEntities.STAFF.get().create(l);
        if (f == null) return null;
        f.setUUID(id);
        f.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0);
        l.addFreshEntity(f);
        f.setSex(female);
        f.applicant(role, name, age, wait);
        f.setSex(female);
        f.setGenome(genome, traits);
        // their family is the village family (not one invented for them)
        CompoundTag ff = new CompoundTag();
        Registry.Rec sp = Registry.find(s, e.spouse);
        ff.putBoolean("married", sp != null);
        if (sp != null) ff.putString("spouse", sp.name.split(" ")[0]);
        ff.putBoolean("parents", e.mother != null || e.father != null);
        ff.putString("cover", "the village");
        f.getPersistentData().put("bp_fam", ff);
        f.getPersistentData().putBoolean("bp_famreg", true);
        return f;
    }

    private static UUID link(MinecraftServer s, UUID id, String name, boolean female) {
        if (id == null) return null;
        Registry reg = Registry.get(s);
        Registry.Rec e = reg.people.get(id);
        if (e == null) {
            e = new Registry.Rec();
            e.id = id;
            e.name = name.isEmpty() ? "a villager" : name;
            e.female = female;
            e.born = s.overworld().getGameTime() - (long) (25 + new Random().nextInt(30)) * Registry.YEAR;
            e.status = "offsite";
            e.job = "lives in the village";
            e.genome = Registry.genomeOf(id);
            reg.people.put(id, e);
        }
        return id;
    }

    private static float personalityGene(String mca, UUID id) {
        try { return (Personality.valueOf(mca.toUpperCase(Locale.ROOT)).ordinal() + 0.5F) / 14F; } catch (IllegalArgumentException e) { return Person.geneOf(id, 20); }
    }

    /** Back to the village, as the villager they were. */
    static void goHome(FoundationStaff f) {
        MinecraftServer s = f.getServer();
        CompoundTag nbt = data(s).villagers.remove(f.getUUID());
        data(s).setDirty();
        Registry.Rec e = Registry.find(s, f.getUUID());
        if (e != null && !e.status.equals("dead")) { e.status = "offsite"; e.job = "lives in the village"; Registry.dirty(s); }
        BlockPos at = f.blockPosition();
        Seats.stand(f);
        f.discard();
        if (nbt == null || !(f.level() instanceof ServerLevel l)) return;
        restore(l, nbt, at);
    }

    /** Brings back the villager they were (after a dismissal, say), standing here. */
    static void restore(ServerLevel l, UUID id, BlockPos at) {
        CompoundTag nbt = data(l.getServer()).villagers.remove(id);
        data(l.getServer()).setDirty();
        if (nbt != null) restore(l, nbt, at);
    }

    private static void restore(ServerLevel l, CompoundTag nbt, BlockPos at) {
        MinecraftServer s = l.getServer();
        nbt.remove("Pos");
        Entity v = EntityType.loadEntityRecursive(nbt, l, x -> { x.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, x.getYRot(), 0); return x; });
        if (v != null) {
            v.getPersistentData().remove("bp_seek");
            v.getPersistentData().putLong("bp_seek_no", s.overworld().getGameTime() + 24000L * 3);
            l.addFreshEntity(v);
        }
    }

    /** Hire a walk-in (or someone on file who is on site) straight into a job. */
    static boolean hireWalkIn(ServerPlayer p, FoundationStaff f, FoundationStaff.Role role) {
        String site = f.getPersistentData().getString("bp_walkin");
        if (site.isEmpty()) site = Registry.of(f).site;
        Management.Vacancy v = Management.direct(p, site, role);
        if (v == null) return false;
        v.cands.clear();
        v.cands.add(Management.candidateFrom(p.server, f, role.ordinal()));
        f.getPersistentData().remove("bp_walkin");
        Management.decide(p.server, v, v.cands.getCompound(0));
        return true;
    }

    // ================================================================== talking to villagers, as the Foundation

    @SubscribeEvent
    public static void interact(PlayerInteractEvent.EntityInteract event) {
        if (!event.getEntity().isShiftKeyDown() || !McaCompat.isVillager(event.getTarget())) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        if (Career.rank(p) == Rank.CLASS_D && !Career.isAscended(p)) { p.displayClientMessage(Component.literal("They won't talk to someone in an orange jumpsuit.").withStyle(ChatFormatting.GRAY), true); return; }
        Entity v = event.getTarget();
        Dialogue.begin(p, "npc:" + v.getId(), v.getName().getString(), "Villager", v.getId());
        String a = Career.address(p);
        Talk.say(p, v, Clearance.RAW + switch (McaCompat.personality(v)) {
            case "crabby" -> "What do you want? You're one of those people from the big building.";
            case "anxious" -> "Oh! You're- you're from the Foundation place. Is something wrong?";
            case "friendly", "extroverted", "upbeat" -> "Hello! You're from the Foundation, aren't you? We see your lights at night.";
            case "flirty" -> "Well, hello. They don't usually send someone so... official.";
            default -> "Can I help you? You're from the facility up the road.";
        });
        menu(p, v);
        Dialogue.send(p, true);
    }

    private static void menu(ServerPlayer p, Entity v) {
        Dialogue.clearOptions(p);
        List<String> info = new ArrayList<>();
        info.add("Villager" + (v instanceof Villager vv && !jobless(vv) ? " · " + vv.getVillagerData().getProfession().name() : " · no job"));
        String pe = McaCompat.personality(v);
        if (!pe.isEmpty()) info.add("Personality: " + Scene.cap(pe));
        info.add("Age: " + Scene.cap(McaCompat.ageState(v).toLowerCase(Locale.ROOT)));
        if (v.getPersistentData().contains("bp_seek")) info.add("Looking for work at " + v.getPersistentData().getString("bp_seek"));
        Dialogue.info(p, info, -1);
        Talk.ans(p, v, "Have you seen anything strange around here?", "vil_strange", "Villagers notice things: they'll tell you where");
        if (Career.rank(p).atLeast(Rank.LEVEL_3) || Talk.topBrass(p)) Talk.ans(p, v, "Anyone suspicious come through lately?", "vil_intruders", "Armed strangers, people asking questions");
        if (Management.mayAttend(p) && McaCompat.ageState(v).equals("ADULT")) Talk.ans(p, v, "We're hiring. Would you like a job with us?", "vil_job", "They'll walk to the site and apply");
        if (Career.rank(p).atLeast(Rank.LEVEL_4) || Talk.topBrass(p)) Talk.ans(p, v, "(Administer Class-A amnestics)", "vil_amnestic", "They forget whatever they saw. Raises site stability a little");
        Talk.opt(p, v, "Goodbye", "bye", "", 0xC8D0DA);
    }

    static void talk(ServerPlayer p, Entity v, String what) {
        if (!Dialogue.active(p, "npc:" + v.getId())) Dialogue.begin(p, "npc:" + v.getId(), v.getName().getString(), "Villager", v.getId());
        Random r = new Random();
        switch (what) {
            case "vil_strange" -> {
                Talk.you(p, "Have you seen anything strange around here?");
                Mob near = null;
                double bd = 160 * 160;
                for (Mob m : v.level().getEntitiesOfClass(Mob.class, v.getBoundingBox().inflate(160), Unleashed::isAnomaly))
                    if (!Unleashed.isContained(m) && m.distanceToSqr(v) < bd) { bd = m.distanceToSqr(v); near = m; }
                if (near == null) Talk.say(p, v, Clearance.RAW + Talk.pick(r, "Strange? No. Should I have?", "Just your trucks. And the humming at night."));
                else {
                    double dx = near.getX() - v.getX(), dz = near.getZ() - v.getZ();
                    String dir = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? "east" : "west") : (dz > 0 ? "south" : "north");
                    Talk.say(p, v, Clearance.RAW + "There's something out to the " + dir + ", maybe " + (int) Math.sqrt(bd) + " blocks. " + Talk.pick(r, "My cousin saw it and hasn't slept since.", "It doesn't move when you look at it. That's the worst part.", "The animals won't go that way."));
                    Talk.note(p, "(They point " + dir + ". Something anomalous is there: " + Unleashed.designation(near) + ".)");
                }
            }
            case "vil_intruders" -> {
                Talk.you(p, "Anyone suspicious come through lately?");
                WorldNews.Item at = WorldNews.latest(p.server, WorldNews.Kind.ATTACK);
                Talk.say(p, v, Clearance.RAW + (at != null && p.server.overworld().getGameTime() - at.time() < 24000 * 2
                        ? "Men with guns, two nights ago. Not your sort. They asked the way to the facility. We didn't tell them. Much."
                        : Talk.pick(r, "Only you lot. And a man selling 'protective charms'. He seemed odd.", "No. It's been quiet. We like quiet.")));
            }
            case "vil_job" -> {
                Talk.you(p, "We're hiring. Would you like a job with us?");
                boolean jobless = v instanceof Villager vv && jobless(vv);
                String pe = McaCompat.personality(v);
                int chance = (jobless ? 75 : 25) + (pe.equals("anxious") || pe.equals("peaceful") ? -25 : pe.equals("greedy") || pe.equals("extroverted") ? 15 : 0);
                if (r.nextInt(100) < chance) {
                    Sites.Site best = null;
                    double bd = Double.MAX_VALUE;
                    for (Sites.Site s : Sites.get(p.server).sites) { double d = v.blockPosition().distSqr(new BlockPos(s.x, s.y, s.z)); if (d < bd) { bd = d; best = s; } }
                    if (best == null) { Talk.say(p, v, Clearance.RAW + "I'd like that. Where would I even go?"); break; }
                    v.getPersistentData().putString("bp_seek", best.name);
                    v.getPersistentData().putLong("bp_seek_t", p.server.overworld().getGameTime());
                    Talk.say(p, v, Clearance.RAW + Talk.pick(r, "A real job? Yes! I'll head up to " + best.name + " now.", pe.equals("greedy") ? "Does it pay? It pays. I'm coming." : "I've always wondered what goes on up there. All right. I'll come."));
                    Talk.note(p, "(They set off for " + best.name + ". They'll wait in the lobby when they arrive.)");
                } else Talk.say(p, v, Clearance.RAW + Talk.pick(r, "Me? Work up there? No thank you. I've heard the screaming.", "I've got my own work. Thank you, though.", "Maybe one day."));
            }
            case "vil_amnestic" -> {
                Talk.you(p, "(You offer them a drink.)");
                long day = p.server.overworld().getDayTime() / 24000;
                if (v.getPersistentData().getLong("bp_amn") == day + 1) Talk.note(p, "(They've already had some today. Any more would be dangerous.)");
                else {
                    v.getPersistentData().putLong("bp_amn", day + 1);
                    Talk.say(p, v, Clearance.RAW + "Oh, thank you... I... sorry, what was I saying? Have we met?");
                    Career.changeStability(p, 1);
                    Talk.note(p, "(Whatever they saw, they don't remember it now.)");
                }
            }
            case "bye" -> { Talk.you(p, "Goodbye."); Talk.say(p, v, Clearance.RAW + "Goodbye, then."); Dialogue.send(p, false); Dialogue.close(p); return; }
            default -> {}
        }
        menu(p, v);
        Dialogue.send(p, true);
    }

    // ================================================================== walk-ins: what they say, and the job offer

    static boolean walkIn(FoundationStaff s) { return s.getPersistentData().contains("bp_walkin"); }

    static void openWalkIn(ServerPlayer p, FoundationStaff s) {
        Talk.say(p, s, Clearance.RAW + Talk.pick(new Random(), "Hello. I'm here about a job? I came up from the village. I don't mind what it is.", "Hi! They said to wait here. I'd like to work for you. Whatever you do."));
        walkInTopics(p, s);
    }

    static void walkInTopics(ServerPlayer p, FoundationStaff s) {
        List<String> info = new ArrayList<>();
        info.add("Walk-in applicant · from the village");
        info.add("Personality: " + Personality.of(s).label);
        List<String> tr = new ArrayList<>();
        for (Traits t : s.traits()) if (!t.about.isEmpty()) tr.add(t.label);
        if (!tr.isEmpty()) info.add("Traits: " + String.join(", ", tr));
        Registry.Rec e = Registry.find(p.server, s.getUUID());
        if (e != null && e.spouse != null) { Registry.Rec sp = Registry.find(p.server, e.spouse); if (sp != null) info.add("Married to " + sp.name); }
        Dialogue.info(p, info, -1);
        if (Management.mayAttend(p)) {
            String[] labels = {"Researcher", "Security", "Medical", "Technician", "Administration", "Janitorial"};
            for (int i = 0; i < labels.length; i++) Talk.opt(p, s, "Hire: " + labels[i], "walk_hire_" + i, "Give them the job here and now", 0x80E0A0);
            Talk.opt(p, s, "Not today. Go home.", "walk_home", "They walk back to the village", 0xE08080);
        } else Talk.note(p, "(Only a Site Director or above can hire.)");
        Talk.opt(p, s, "Family", "family_tree", "Their family in the village", 0xF0A8C8);
        Talk.opt(p, s, "Goodbye", "bye", "", 0xC8D0DA);
    }

    static boolean handleWalkIn(ServerPlayer p, FoundationStaff s, String what) {
        if (!walkIn(s) || !what.startsWith("walk_")) return false;
        if (!Management.mayAttend(p)) { Convo.menu(p, s); return true; }
        if (what.equals("walk_home")) {
            Talk.you(p, "Not today, I'm afraid. Thank you for coming.");
            Talk.say(p, s, Clearance.RAW + "Oh. All right. Thank you for seeing me.");
            Dialogue.send(p, true);
            Dialogue.close(p);
            goHome(s);
            return true;
        }
        int i = Integer.parseInt(what.substring(10));
        FoundationStaff.Role role = FoundationStaff.Role.values()[Math.max(0, Math.min(5, i))];
        Talk.you(p, "You're hired. " + role.title + ".");
        Talk.say(p, s, Clearance.RAW + Talk.pick(new Random(), "Really? Thank you! I won't let you down.", "Oh! Thank you. My mother won't believe it. Well. She won't be told."));
        if (!hireWalkIn(p, s, role)) Talk.note(p, "(Personnel couldn't find them a desk at this site.)");
        Dialogue.clearOptions(p);
        Talk.opt(p, s, "Goodbye", "bye", "", 0xC8D0DA);
        return true;
    }
}
