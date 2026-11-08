package com.breachprotocol.entity;

import com.breachprotocol.career.Career;
import com.breachprotocol.career.Rank;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Set;
import java.util.Map;
import java.util.UUID;

/**
 * Foundation personnel who work at your sites: researchers, security, medics, technicians, clerks
 * and janitors. They go about their floor, sit down on the site's chairs now and then, and speak to
 * you when they see you, according to your rank (a D-Class gets ordered about; the Administrator
 * gets everyone on their feet). Right-click one to talk; some can help.
 */
public class FoundationStaff extends PathfinderMob implements Person {
    public enum Role {
        RESEARCHER("Researcher", "Dr."), GUARD("Security", "Officer"), MEDIC("Medical", "Dr."),
        TECHNICIAN("Technician", "Tech"), CLERK("Administration", ""), JANITOR("Janitorial", ""),
        SECRETARY("Executive Secretary", "Ms.");

        public final String title, prefix;

        Role(String title, String prefix) { this.title = title; this.prefix = prefix; }
    }

    private static final EntityDataAccessor<Integer> ROLE = SynchedEntityData.defineId(FoundationStaff.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(FoundationStaff.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> AGE = SynchedEntityData.defineId(FoundationStaff.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> FEMALE = SynchedEntityData.defineId(FoundationStaff.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> TALKING = SynchedEntityData.defineId(FoundationStaff.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> WAVE = SynchedEntityData.defineId(FoundationStaff.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> FRIGHT = SynchedEntityData.defineId(FoundationStaff.class, EntityDataSerializers.BOOLEAN);
    /** Inherited genes and traits ("hexgenome|TRAIT,TRAIT"), for those born to parents on record; empty for everyone else. */
    private static final EntityDataAccessor<String> GENOME = SynchedEntityData.defineId(FoundationStaff.class, EntityDataSerializers.STRING);

    /** One year of a staff member's life passes every Minecraft day. */
    public static final int YEAR = 24000;
    private static final net.minecraft.resources.ResourceLocation ELDER = com.breachprotocol.BreachProtocol.id("elder");

    private double age = 30;
    private int lastYear = -1;
    /** What they're ill with ("" = healthy). */
    private String illness = "";
    private int locketUses;
    private double fountainAt = -100;
    private boolean declined;

    /** Whether they've been given their own (world-unique) name. */
    private boolean named;
    /** The armed unit they belong to (null = ordinary staff). */
    private com.breachprotocol.career.Forces.Unit unit;
    private long lastShout;

    public com.breachprotocol.career.Forces.Unit unit() { return unit; }

    public boolean isFemale() { return entityData.get(FEMALE); }

    /** Her build: 0, 1 or 2. */
    public int bust() { return (int) Math.floorMod(getUUID().getMostSignificantBits() >>> 11, 3L); }

    // ================================================================== looks (from who they are; the same on every client)

    private long look(int shift, long mod) { return Math.floorMod(getUUID().getLeastSignificantBits() >>> shift ^ getUUID().getMostSignificantBits() >>> (shift + 7), mod); }

    /** Her figure, 0 (slight) to 1 (full). */
    public float bustSize() { return gene(8); }

    // ================================================================== genes and traits (MCA's genetics)

    private String genomeCache, traitCache;
    private EnumSet<Traits> traitSet;

    /** The inherited genome (hex), or "" if their genes are their own. */
    public String genome() { String g = entityData.get(GENOME); int i = g.indexOf('|'); return i < 0 ? g : g.substring(0, i); }

    /** Pigments taken straight from an MCA villager's genes (rather than worked out from heritage). */
    public boolean directPigment() { return entityData.get(GENOME).endsWith("|D"); }

    /** Sets inherited genes ("hex", or "hex|D" for an MCA villager's direct pigments) and traits. */
    public void setGenome(String genome, Set<Traits> traits) {
        boolean d = genome.endsWith("|D");
        String hex = d ? genome.substring(0, genome.length() - 2) : genome;
        entityData.set(GENOME, hex + "|" + Traits.write(traits) + (d ? "|D" : ""));
        traitsApplied = false;
    }

    public void setSex(boolean female) { getPersistentData().putBoolean("bp_sex", female); entityData.set(FEMALE, female); }

    public EnumSet<Traits> traits() {
        String g = entityData.get(GENOME);
        if (!g.equals(traitCache) || traitSet == null) {
            traitCache = g;
            String[] parts = g.split("\\|", -1);
            traitSet = parts.length < 2 ? Traits.natural(getUUID()) : Traits.read(parts[1]);
        }
        return traitSet;
    }

    public boolean has(Traits t) { return traits().contains(t); }

    @Override
    public float gene(int salt) {
        String g = genome();
        if (!g.isEmpty() && salt < Registry.GENES) { float v = Registry.gene(g, salt); if (v >= 0) return v; }
        return Person.super.gene(salt);
    }

    @Override
    public float melanin() { return has(Traits.ALBINISM) ? 0.0F : directPigment() ? gene(2) : Person.super.melanin(); }

    @Override
    public float hemoglobin() { return has(Traits.ALBINISM) ? 0.25F : directPigment() ? gene(4) : Person.super.hemoglobin(); }

    @Override
    public float eumelanin() { return has(Traits.ALBINISM) ? 0.02F : Person.super.eumelanin(); }

    @Override
    public float pheomelanin() { return has(Traits.ALBINISM) ? 0.05F : Person.super.pheomelanin(); }

    @Override
    public float heightScale() { return Person.super.heightScale() * (has(Traits.DWARFISM) ? 0.78F : 1F); }

    private boolean traitsApplied;
    private static final net.minecraft.resources.ResourceLocation TRAIT_SPEED = com.breachprotocol.BreachProtocol.id("trait_speed"), TRAIT_HEALTH = com.breachprotocol.BreachProtocol.id("trait_health");

    /** Athletic, tough and frail actually change how they move and how much they can take. */
    private void applyTraits() {
        traitsApplied = true;
        var spd = getAttribute(Attributes.MOVEMENT_SPEED);
        if (spd != null) {
            spd.removeModifier(TRAIT_SPEED);
            double m = (has(Traits.ATHLETIC) ? 0.12 : 0) - (has(Traits.ASTHMA) ? 0.06 : 0);
            if (m != 0) spd.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(TRAIT_SPEED, m, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
        var hp = getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) {
            hp.removeModifier(TRAIT_HEALTH);
            double h = (has(Traits.TOUGH) ? 8 : 0) - (has(Traits.WEAK) ? 6 : 0);
            if (h != 0) hp.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(TRAIT_HEALTH, h, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
        }
    }

    // ================================================================== hiring

    /** Takes up a job (a hired applicant): keeps their own name and age. */
    public void hireAs(Role role, BlockPos post) {
        entityData.set(ROLE, role.ordinal());
        entityData.set(VARIANT, random.nextInt(3));
        this.post = post;
        mode = "post";
        stayAt = null;
        restrictTo(post, role == Role.SECRETARY ? 6 : 12);
        getNavigation().moveTo(post.getX() + 0.5, post.getY(), post.getZ() + 0.5, 1.0);
        getPersistentData().remove("bp_cand");
    }

    /** An applicant: name, age and the uniform of the job they want; they wait where they're put. */
    public void applicant(Role role, String name, int age, BlockPos wait) {
        entityData.set(ROLE, role.ordinal());
        entityData.set(VARIANT, random.nextInt(3));
        setCustomName(Component.literal(name).withStyle(ChatFormatting.WHITE));
        setCustomNameVisible(false);
        named = true;
        gender();
        setAge(age);
        lastYear = age;
        walkTo(wait);
    }

    /** Walk somewhere and wait there. */
    public void walkTo(BlockPos at) {
        Seats.stand(this);
        mode = "stay";
        stayAt = at;
        clearRestriction();
        getNavigation().moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.9);
    }

    /** Go and sit in this chair. */
    public void sitAt(BlockPos chair) {
        mode = "stay";
        stayAt = chair;
        clearRestriction();
        sitTarget = chair;
        sitTries = -40;
    }

    public BlockPos post() { return post; }

    // ================================================================== clearance, doors, the lift, and a day in their life

    /** The doors and floors they're cleared for: by job, and higher for heads, the secretary and armed units. */
    public int clearance() {
        if (unit != null) return 5;
        if (role() == Role.SECRETARY) return 5;
        int base = switch (role()) { case JANITOR -> 2; case CLERK, TECHNICIAN -> 2; case RESEARCHER, MEDIC -> 3; case GUARD -> 3; case SECRETARY -> 5; };
        return Management.headOf(this) != null ? Math.min(5, base + 1) : base;
    }

    @Override
    protected net.minecraft.world.entity.ai.navigation.PathNavigation createNavigation(Level level) { return new StaffNavigation(this, level); }

    /** Where they're headed (possibly via the lift), what they'll do there, and their bed. */
    private BlockPos goal;
    private boolean sleepAtGoal;
    private String activity = "";
    private BlockPos towerOrigin;
    private long towerCheck;

    public String activity() { return activity; }

    private BlockPos tower() {
        if (level().getGameTime() >= towerCheck && level() instanceof net.minecraft.server.level.ServerLevel sl) {
            towerCheck = level().getGameTime() + 400;
            com.breachprotocol.career.Sites.Site t = Lift.towerAt(sl, post != null ? post : blockPosition());
            towerOrigin = t == null ? null : com.breachprotocol.career.Secretary.origin(t);
            if (towerOrigin != null && !Lift.hasLift(sl, towerOrigin)) towerOrigin = null;
        }
        return towerOrigin;
    }

    /** Which lift stop serves this spot (the residence counts as the ground floor). */
    private int stop(BlockPos o, BlockPos p) {
        return p.getX() - o.getX() >= com.breachprotocol.world.SiteTower.RX0 - 6 ? 0 : Lift.storey(o, p.getY());
    }

    /** Go somewhere, taking the lift if it's on another floor. */
    public void travel(BlockPos to, boolean sleep) {
        goal = to;
        sleepAtGoal = sleep;
        seatAtGoal = false;
        bestD = Double.MAX_VALUE;
        stuck = 0;
        clearRestriction();
        Seats.stand(this);
    }

    /** Checked once a second while travelling: how close they've come, and for how long they haven't got closer. */
    private boolean seatAtGoal;
    private double bestD = Double.MAX_VALUE;
    private int stuck;
    private BlockPos leg;

    private void travelTick() {
        if (goal == null) return;
        if (Seats.seated(this)) Seats.stand(this);
        BlockPos o = tower();
        BlockPos target = goal;
        if (o != null) {
            int here = stop(o, blockPosition()), there = stop(o, goal);
            if (here != there) {
                BlockPos car = o.offset(com.breachprotocol.world.SiteTower.liftCar(here));
                double dc = distanceToSqr(car.getX() + 0.5, car.getY(), car.getZ() + 0.5);
                // in the car, or at its door and not getting any closer: ride
                if (dc < 3.0 || dc < 9 && stuck >= 3) {
                    if (!Lift.carry(this, o, there)) { goal = null; return; } // not cleared for that floor
                    bestD = Double.MAX_VALUE;
                    stuck = 0;
                    return;
                }
                target = car;
            }
        }
        if (!target.equals(leg)) { leg = target; bestD = Double.MAX_VALUE; stuck = 0; }
        double d = distanceToSqr(target.getX() + 0.5, target.getY(), target.getZ() + 0.5);
        if (target == goal && d < (sleepAtGoal ? 4 : 6)) { arrive(); return; }
        if (d < bestD - 1.5) { bestD = d; stuck = 0; } else stuck++;
        if (stuck >= 10 && !watched()) {
            // hopelessly stuck (a blocked corridor, furniture someone moved) and nobody's looking: they find their way
            teleportTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5);
            getNavigation().stop();
            bestD = Double.MAX_VALUE;
            stuck = 0;
            if (target == goal) arrive();
            return;
        }
        var nav = getNavigation();
        if (nav.isDone() || stuck == 3 || stuck == 6 || tickCount % 60 == 0) {
            var path = nav.createPath(target, 0);
            if (path != null && !path.canReach() && stuck >= 3 && path.getEndNode() != null && path.getNodeCount() > 1) {
                // no full route yet: walk as far as the planner can see, then plan again from there
                nav.moveTo(path, 0.9);
            } else if (path != null) nav.moveTo(path, 0.9);
            else nav.moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 0.9);
        }
    }

    private void arrive() {
        BlockPos g = goal;
        goal = null;
        stuck = 0;
        if (sleepAtGoal) { sleepIn(g); return; }
        restrictTo(g, activity.equals("work") ? (role() == Role.SECRETARY ? 6 : 12) : 6);
        if (seatAtGoal) {
            seatAtGoal = false;
            BlockPos best = null;
            double bd = 10;
            for (BlockPos q : BlockPos.betweenClosed(g.offset(-3, -1, -3), g.offset(3, 1, 3))) {
                if (!Seats.isSeat(level().getBlockState(q)) || Seats.occupied(level(), q)) continue;
                double dd = q.distSqr(g);
                if (dd < bd) { bd = dd; best = q.immutable(); }
            }
            if (best != null) { sitTarget = best; sitTries = 0; }
        }
    }

    /** Someone close enough to see a person step through a wall. */
    private boolean watched() {
        for (Player p : level().players()) if (!p.isSpectator() && p.distanceToSqr(this) < 24 * 24 && p.hasLineOfSight(this)) return true;
        return false;
    }

    private void sleepIn(BlockPos bed) {
        if (level().getBlockState(bed).getBlock() instanceof net.minecraft.world.level.block.BedBlock && !isSleeping()) {
            getNavigation().stop();
            startSleeping(bed);
        }
    }

    @Override
    public boolean isImmobile() { return isSleeping() || super.isImmobile(); }

    /** Their bed in the residence (assigned once, kept). */
    private BlockPos bed() {
        BlockPos o = tower();
        if (o == null) return null;
        if (getPersistentData().contains("bp_bed")) return BlockPos.of(getPersistentData().getLong("bp_bed"));
        java.util.Set<Long> taken = new java.util.HashSet<>();
        for (FoundationStaff f : level().getEntitiesOfClass(FoundationStaff.class, new net.minecraft.world.phys.AABB(o).inflate(120)))
            if (f.getPersistentData().contains("bp_bed")) taken.add(f.getPersistentData().getLong("bp_bed"));
        for (BlockPos b : com.breachprotocol.world.SiteTower.beds()) {
            BlockPos w = o.offset(b);
            if (taken.contains(w.asLong()) || !(level().getBlockState(w).getBlock() instanceof net.minecraft.world.level.block.BedBlock)) continue;
            getPersistentData().putLong("bp_bed", w.asLong());
            return w;
        }
        return null;
    }

    /** Night-shift guards sleep by day. */
    private boolean nightShift() { return role() == Role.GUARD && gene(50) < 0.4F; }

    /** The hour of the day (0-23). */
    private int hour() { return (int) ((level().getDayTime() % 24000) / 1000 + 6) % 24; }

    /** What they should be doing now: work, lunch, free time or sleep. */
    private String scheduled() {
        int h = hour();
        if (nightShift()) return h >= 18 || h < 6 ? "work" : h >= 8 && h < 16 ? "sleep" : "free";
        boolean sec = role() == Role.SECRETARY;
        if (sec ? h >= 23 || h < 7 : h >= 22 || h < 6) return "sleep";
        if (h == 12 && !sec) return "lunch";
        if (sec ? h >= 8 && h < 18 : h >= 7 && h < 17) return "work";
        return "free";
    }

    private void schedule() {
        if (unit != null || post == null || !"post".equals(mode) || !errand.isEmpty() || Dialogue.talkingTo(this) != null) return;
        if (getPersistentData().contains("bp_cand") || getPersistentData().contains("bp_walkin") || getPersistentData().contains("bp_leaving")) return;
        // trouble keeps everyone at their post
        com.breachprotocol.career.Sites.Site site = com.breachprotocol.career.Sites.at(level(), post);
        if (Talk.anomalyNear(this, 24) != null || site != null && com.breachprotocol.career.Defence.alertAt(site.name) != null) {
            if (isSleeping()) stopSleeping();
            activity = "work";
            return;
        }
        String now = scheduled();
        if (now.equals(activity) && (goal != null || !now.equals("sleep") || isSleeping())) return;
        if (isSleeping() && !now.equals("sleep")) stopSleeping();
        activity = now;
        BlockPos o = tower();
        switch (now) {
            case "work" -> travel(post, false);
            case "lunch" -> { if (o != null) travel(o.offset(14 + random.nextInt(10), 1, 2 + random.nextInt(5)), false); }
            case "free" -> {
                if (o == null) break;
                BlockPos[] spots = {o.offset(16 + random.nextInt(8), 1, 2 + random.nextInt(5)), o.offset(22 + random.nextInt(8), 1, 21 + random.nextInt(3)), o.offset(com.breachprotocol.world.SiteTower.RX0 + 3 + random.nextInt(18), 1, 15 + random.nextInt(3))};
                travel(spots[random.nextInt(spots.length)], false);
            }
            case "sleep" -> { BlockPos b = bed(); if (b != null) travel(b, true); else activity = "work"; }
            default -> {}
        }
    }

    /** Greying with age (white when old). 0 = natural colour. */
    public int greyHair() { return elder() ? 0xFFD8D8DC : age() >= 52 && gene(14) < 0.5F ? 0xFFA6A4A2 : 0; }

    @Override
    public String uniform() { return "textures/entity/staff/uniform/" + role().name().toLowerCase(java.util.Locale.ROOT) + "_" + Math.floorMod(variant(), 3) + ".png"; }

    private static final String[] MEN = {"short", "short", "buzz", "sidepart", "sidepart", "curly", "bun"};
    private static final String[] WOMEN = {"long", "long", "bob", "ponytail", "bun", "curly", "bob"};

    public String hairStyle() { return isFemale() ? WOMEN[(int) look(9, WOMEN.length)] : MEN[(int) look(9, MEN.length)]; }

    private static final int[] HAIR = {0x1A1612, 0x2C1E16, 0x3B2A20, 0x5A3A24, 0x6B4A2E, 0x8C4A2A, 0xB5652E, 0xA88A55, 0xD2B67F, 0xE3D6B8};

    public int hairColour() { return elder() ? 0xC8C8CC : age() >= 52 && look(14, 3) == 0 ? 0x8E8C8A : HAIR[(int) look(17, HAIR.length)]; }

    /** "" or beard_full / beard_stubble / moustache. */
    public String facialHair() {
        if (isFemale()) return "";
        return switch ((int) look(21, 10)) { case 0, 1 -> "beard_stubble"; case 2 -> "beard_full"; case 3 -> "moustache"; default -> ""; };
    }

    public boolean glasses() { return role() != Role.RESEARCHER && look(25, 6) == 0; }

    /** A little taller or shorter than average. */


    public boolean isTalking() { return entityData.get(TALKING); }

    public boolean isWaving() { return entityData.get(WAVE) > 0; }

    /** An anomaly is loose near them (and they aren't armed to deal with it). */
    public boolean isFrightened() { return entityData.get(FRIGHT); }

    /** Women and men by their (first) name; the secretary is always a woman. */
    private void gender() {
        if (getPersistentData().contains("bp_sex")) { entityData.set(FEMALE, getPersistentData().getBoolean("bp_sex")); return; } // known (a villager who joined us)
        if (role() == Role.SECRETARY) { entityData.set(FEMALE, true); return; }
        String n = getCustomName() == null ? "" : getCustomName().getString();
        boolean f = false;
        for (String part : n.split(" ")) if (Names.isWoman(part)) f = true;
        entityData.set(FEMALE, f);
    }

    // ================================================================== doing what they said they'd do

    private void until(long ticks) { backToPostAt = level().getGameTime() + ticks; }

    /** "Going!": away from whatever is loose, then back to work once it's quiet. */
    void flee(net.minecraft.world.phys.Vec3 from) {
        net.minecraft.world.phys.Vec3 to = net.minecraft.world.entity.ai.util.DefaultRandomPos.getPosAway(this, 16, 7, from);
        if (Seats.seated(this)) Seats.stand(this);
        mode = "stay";
        clearRestriction();
        if (to != null) { stayAt = BlockPos.containing(to); getNavigation().moveTo(to.x, to.y, to.z, 1.5); setSprinting(true); }
        until(20 * 60);
    }

    /** "Locking them now": into the middle of the site, and stay there until it's over. */
    void shelter() {
        com.breachprotocol.career.Sites.Site x = com.breachprotocol.career.Sites.at(level(), blockPosition());
        if (x == null) { order("stay", null); return; }
        BlockPos c = new BlockPos(x.x, x.y + 1, x.z);
        if (Seats.seated(this)) Seats.stand(this);
        mode = "stay";
        stayAt = c;
        clearRestriction();
        getNavigation().moveTo(c.getX() + 0.5, c.getY(), c.getZ() + 0.5, 1.4);
        until(20 * 120);
    }

    /** "After my shift" / "I'll go": off to the nearest medic, who treats them when they get there. */
    void toMedical() {
        FoundationStaff best = null;
        double bd = 64 * 64;
        for (FoundationStaff o : level().getEntitiesOfClass(FoundationStaff.class, getBoundingBox().inflate(64)))
            if (o != this && o.role() == Role.MEDIC && distanceToSqr(o) < bd) { bd = distanceToSqr(o); best = o; }
        if (best == null) { tellNearby(name() + ": There's no medic on this floor. I'll manage.", ChatFormatting.GRAY); return; }
        if (Seats.seated(this)) Seats.stand(this);
        errand = "medical";
        errandTarget = best.getId();
        mode = "stay";
        clearRestriction();
        until(20 * 180);
    }

    /** "Thank you": they take the rest of the day off, sitting down somewhere. */
    void dayOff() {
        order("sit", null);
        errand = "rest";
        until(24000);
    }

    /** "Security can mind it for me": the nearest guard comes over and keeps an eye on you for a minute. */
    void callGuard(ServerPlayer p, String why) {
        FoundationStaff g = null;
        double bd = 48 * 48;
        for (FoundationStaff o : level().getEntitiesOfClass(FoundationStaff.class, getBoundingBox().inflate(48)))
            if (o != this && o.role() == Role.GUARD && distanceToSqr(o) < bd) { bd = distanceToSqr(o); g = o; }
        if (g == null) return;
        g.order("follow", p);
        g.until(20 * 60);
        p.displayClientMessage(Component.literal(g.name() + " " + why).withStyle(ChatFormatting.DARK_AQUA), true);
    }

    private void errandTick() {
        if (backToPostAt > 0 && level().getGameTime() >= backToPostAt) {
            backToPostAt = 0;
            errand = "";
            setSprinting(false);
            order("post", null);
            return;
        }
        if (errand.equals("medical")) {
            net.minecraft.world.entity.Entity m = level().getEntity(errandTarget);
            if (!(m instanceof FoundationStaff medic) || !medic.isAlive()) { errand = ""; return; }
            if (distanceToSqr(medic) > 9) { if (tickCount % 20 == 0) getNavigation().moveTo(medic, 1.0); return; }
            errand = "";
            getNavigation().stop();
            if (!illness.isEmpty() && random.nextFloat() < 0.6F) {
                tellNearby(medic.name() + " treats " + name() + " for " + illness + ". " + name() + " looks better already.", ChatFormatting.GRAY);
                illness = "";
                removeEffect(MobEffects.WEAKNESS);
            } else tellNearby(medic.name() + " checks " + name() + " over and sends them back with something to take.", ChatFormatting.GRAY);
            until(20 * 10);
        }
    }

    public boolean following(Player p) { return "follow".equals(mode) && p.getUUID().equals(leader); }

    /** Makes them a member of an armed unit, posted at {@code post}. */
    public void setupUnit(com.breachprotocol.career.Forces.Unit u, BlockPos post) {
        this.unit = u;
        setup(Role.GUARD, post);
        setAge(22 + random.nextInt(22));
        var hp = getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        if (hp != null) hp.setBaseValue(u.health);
        setHealth(u.health);
        arm();
    }

    /** Puts the right gun in their hand (SCP Unleashed's own). */
    public void arm() {
        if (role() != Role.GUARD) return;
        String gun = unit != null ? unit.gun : "pistol";
        var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("scp_unleashed", gun));
        if (item.isPresent()) setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(item.get()));
        setDropChance(net.minecraft.world.entity.EquipmentSlot.MAINHAND, 0F);
    }

    /** A call-out when they open fire. */
    void shout(net.minecraft.world.entity.LivingEntity t) {
        long now = level().getGameTime();
        if (now - lastShout < 200) return;
        lastShout = now;
        String line = t instanceof net.minecraft.world.entity.monster.Creeper ? pick("Creeper! Drop it before it gets close!", "Creeper on the perimeter! Light it up!", "Creeper! Keep your distance!")
                : t instanceof Player ? pick("Hostile on site! Weapons free!", "Intruder! Drop your weapon!", "Contact! Hostile combatant!")
                : com.breachprotocol.career.Defence.intruder(getServer(), t) ? pick("Hostiles on the grounds! Engaging!", "Insurgents! Push them back!", "Contact! They're not on the list!")
                : pick("Contact!", "Hostile, engaging!", "Got one!", "Movement, engaging!");
        for (Player q : level().players()) if (q.distanceTo(this) < 24)
            q.displayClientMessage(Component.literal(name() + ": ").withStyle(ChatFormatting.DARK_AQUA).append(Component.literal(line).withStyle(ChatFormatting.RED)), true);
    }

    private final Map<UUID, Long> greeted = new HashMap<>();
    private BlockPos post;
    /** What they've been told to do: work at their post, follow someone, or wait somewhere. */
    private String mode = "post";
    private UUID leader;
    private BlockPos stayAt, sitTarget;
    /** An errand they're running (going to Medical, taking shelter, fleeing), and when they go back to their post. */
    private String errand = "";
    private int errandTarget = -1;
    private long backToPostAt;
    private int sitTries;
    private long healReady;

    public FoundationStaff(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 24).add(Attributes.MOVEMENT_SPEED, 0.28).add(Attributes.ATTACK_DAMAGE, 4).add(Attributes.FOLLOW_RANGE, 64); // long walks: to the residence and back
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ROLE, 0);
        builder.define(VARIANT, 0);
        builder.define(AGE, 30);
        builder.define(FEMALE, false);
        builder.define(TALKING, false);
        builder.define(WAVE, 0);
        builder.define(FRIGHT, false);
        builder.define(GENOME, "");
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new GuardGoal(this));
        goalSelector.addGoal(1, new ListenGoal(this));
        goalSelector.addGoal(1, new PanicGoal(this, 1.4) {
            @Override
            public boolean canUse() { return role() != Role.GUARD && super.canUse(); }
        });
        goalSelector.addGoal(2, new FollowGoal(this, () -> "follow".equals(mode) && leader != null && level() instanceof net.minecraft.server.level.ServerLevel sl ? sl.getPlayerByUUID(leader) : null));
        goalSelector.addGoal(3, new SitGoal(this, null, 0.006F, 20 * 30, 20 * 150, 8, pos -> !"follow".equals(mode) && goal == null && !isSleeping() && com.breachprotocol.world.Site01.mayUseChair(this, pos)));
        goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 8F, 0.6F));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.6, 0.01F) {
            @Override
            public boolean canUse() { return "post".equals(mode) && goal == null && !isSleeping() && super.canUse(); }
        });
        goalSelector.addGoal(6, new RandomLookAroundGoal(this));
    }

    public Role role() { return Role.values()[Math.floorMod(entityData.get(ROLE), Role.values().length)]; }

    public int variant() { return entityData.get(VARIANT); }

    public int age() { return entityData.get(AGE); }

    public boolean elder() { return age() >= 65; }

    public String illness() { return illness; }

    /** Sets who they are and where they work (they stay within 12 blocks of it). */
    public void setup(Role role, BlockPos post) {
        entityData.set(ROLE, role.ordinal());
        entityData.set(VARIANT, random.nextInt(3));
        this.post = post;
        name(role);
        setAge(role == Role.SECRETARY ? 24 + random.nextInt(10) : 22 + random.nextInt(36));
        restrictTo(post, role == Role.SECRETARY ? 6 : 12);
    }

    /** An order from a player: follow, stay, post (back to work) or sit. */
    public void order(String what, ServerPlayer p) {
        switch (what) {
            case "follow" -> { if (p == null) return; mode = "follow"; leader = p.getUUID(); clearRestriction(); Seats.stand(this); }
            case "stay" -> { mode = "stay"; stayAt = blockPosition(); restrictTo(stayAt, 1); getNavigation().stop(); }
            case "post" -> {
                mode = "post";
                leader = null;
                sitTarget = null;
                setSprinting(false);
                if (post != null) {
                    // the same way they get anywhere: doors they're cleared for, the lift between floors
                    activity = "work";
                    travel(post, false);
                    seatAtGoal = role() == Role.SECRETARY; // back behind her desk
                }
            }
            case "sit" -> {
                mode = "stay";
                BlockPos c = blockPosition(), best = null;
                double bd = Double.MAX_VALUE;
                for (BlockPos q : BlockPos.betweenClosed(c.offset(-8, -2, -8), c.offset(8, 2, 8))) {
                    if (!Seats.isSeat(level().getBlockState(q)) || Seats.occupied(level(), q) || !com.breachprotocol.world.Site01.mayUseChair(this, q)) continue;
                    double d = q.distSqr(c);
                    if (d < bd) { bd = d; best = q.immutable(); }
                }
                sitTarget = best;
                sitTries = 0;
                clearRestriction();
            }
            default -> {}
        }
    }

    private void setAge(double a) {
        age = Math.max(18, a);
        entityData.set(AGE, (int) age);
        var speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(ELDER);
            if (age >= 70) speed.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(ELDER, -0.3,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    private void news(com.breachprotocol.career.WorldNews.Kind k, String headline, int mood) {
        if (level().getServer() != null) com.breachprotocol.career.WorldNews.record(level().getServer(), k, "staff", headline, mood, "");
    }

    private void tellNearby(String text, ChatFormatting colour) {
        for (Player p : level().players()) if (p.distanceTo(this) < 32) p.sendSystemMessage(Component.literal(text).withStyle(colour, ChatFormatting.ITALIC));
    }

    /**
     * Another year older. Illness comes more often with age; the old may choose a Foundation
     * remedy without asking anyone's clearance (SCP-006, the Fountain of Youth; SCP-427, the locket
     * that heals and, worn too long, changes you; SCP-500 for disease), or choose to let nature take
     * its course. Past seventy, every year might be the last.
     */
    private void birthday() {
        String who = name();
        int a = (int) age;
        if (role() == Role.SECRETARY && a >= 58) { retire(); return; }
        if (illness.isEmpty()) {
            float chance = (a < 45 ? 0.02F : a < 65 ? 0.06F : 0.14F) * (com.breachprotocol.career.Policy.safety(getServer()) ? 0.5F : 1F);
            if (random.nextFloat() < chance) {
                String[] kinds = {"a fever", "pneumonia", "a heart condition", "an infection", "a bad cough"};
                illness = kinds[random.nextInt(kinds.length)];
                addEffect(new MobEffectInstance(MobEffects.WEAKNESS, YEAR * 3, 0, false, false));
            }
        } else {
            if (random.nextFloat() < 0.35F) {
                news(com.breachprotocol.career.WorldNews.Kind.TREATMENT, who + " was given SCP-500 for " + illness + " and walked out of Medical cured", 2);
                illness = "";
                removeEffect(MobEffects.WEAKNESS);
            } else if (a >= 70 && random.nextFloat() < 0.25F) { die("of " + illness); return; }
            else if (random.nextFloat() < 0.4F) { illness = ""; removeEffect(MobEffects.WEAKNESS); }
        }
        if (a >= 65 && !declined && random.nextFloat() < 0.5F) {
            Talk.Temper t = Talk.temper(this);
            boolean fountain = t == Talk.Temper.CAREFUL || t == Talk.Temper.CYNICAL && random.nextBoolean();
            if (t == Talk.Temper.EARNEST) {
                declined = true;
                news(com.breachprotocol.career.WorldNews.Kind.AGING, who + ", " + a + ", turned down the Fountain of Youth. \"I've had a good run.\"", 0);
            } else if (fountain && age - fountainAt > 20) {
                fountainAt = age;
                setAge(age - 15);
                news(com.breachprotocol.career.WorldNews.Kind.TREATMENT, who + " drank from SCP-006 and looks fifteen years younger", 1);
                tellNearby(who + " looks younger than yesterday.", ChatFormatting.AQUA);
            } else if (t == Talk.Temper.BOLD) {
                if (wearLocket()) return;
            }
        }
        if (a >= 100 || a >= 72 && random.nextFloat() < (a - 70) * 0.04F) die("peacefully, of old age");
    }

    /** SCP-427: heals and holds back the years. Every use risks turning the wearer into SCP-427-1. */
    public boolean wearLocket() {
        locketUses++;
        if (locketUses >= 2 && random.nextFloat() < locketUses * 0.22F) {
            String who = name();
            news(com.breachprotocol.career.WorldNews.Kind.BREACH, who + " wore SCP-427 too long and became SCP-427-1", -7);
            tellNearby(who + "'s skin splits. Something else stands up.", ChatFormatting.DARK_RED);
            com.breachprotocol.compat.Unleashed.entity("scp427_1").ifPresent(type -> {
                net.minecraft.world.entity.Entity e = type.create(level());
                if (e != null) { e.moveTo(getX(), getY(), getZ(), getYRot(), 0); level().addFreshEntity(e); }
            });
            Seats.stand(this);
            discard();
            return true;
        }
        setAge(age - 12);
        illness = "";
        removeEffect(MobEffects.WEAKNESS);
        news(com.breachprotocol.career.WorldNews.Kind.TREATMENT, name() + " opened SCP-427 again. Twelve years gone from their face", -1);
        return false;
    }

    /** The Administrator's secretary steps down; the Council appoints the next one. */
    private void retire() {
        news(com.breachprotocol.career.WorldNews.Kind.AGING, name() + " retired after years as the Administrator's secretary", 1);
        tellNearby(name() + " has retired. She leaves a neat desk and a long list of handover notes.", ChatFormatting.GRAY);
        com.breachprotocol.career.Secretary.vacancy(this, "retired");
        Seats.stand(this);
        discard();
    }

    /** Leaves the Foundation's service (reclassified or terminated): the post is filled again. */
    void leaveService() {
        if (level() instanceof net.minecraft.server.level.ServerLevel sl) {
            if (role() == Role.SECRETARY) com.breachprotocol.career.Secretary.vacancy(this, "was dismissed");
            else if (unit == null && post != null) Management.staffVacancy(sl, post, role(), name());
        }
        Seats.stand(this);
        discard();
    }

    private void die(String cause) {
        Social.died(this, "died " + cause);
        news(com.breachprotocol.career.WorldNews.Kind.DEATH, name() + " (" + role().title + ") died " + cause + " at " + (int) age, -2);
        tellNearby(name() + " has died " + cause + ", aged " + (int) age + ".", ChatFormatting.GRAY);
        if (level() instanceof net.minecraft.server.level.ServerLevel sl) {
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL, getX(), getY() + 1, getZ(), 12, 0.3, 0.5, 0.3, 0.02);
            if (unit != null) com.breachprotocol.career.Forces.died(this, cause);
            else if (role() == Role.SECRETARY) com.breachprotocol.career.Secretary.vacancy(this, "died " + cause);
            else if (post != null) Management.staffVacancy(sl, post, role(), name());
        }
        Seats.stand(this);
        discard();
    }

    @Override
    public void die(net.minecraft.world.damagesource.DamageSource src) {
        super.die(src);
        if (!(level() instanceof net.minecraft.server.level.ServerLevel sl) || isRemoved() && !isDeadOrDying()) return;
        net.minecraft.world.entity.Entity k = src.getEntity();
        String by = k == null ? "in an incident" : "by " + (k instanceof Player pl ? pl.getGameProfile().getName() : k.getName().getString());
        Social.died(this, "was killed " + by);
        if (unit != null) {
            com.breachprotocol.career.Forces.died(this, "killed " + by);
        } else if (role() == Role.SECRETARY) {
            news(com.breachprotocol.career.WorldNews.Kind.DEATH, name() + ", the Administrator's secretary, was killed " + by, -4);
            com.breachprotocol.career.Secretary.vacancy(this, "was killed " + by);
        } else {
            news(com.breachprotocol.career.WorldNews.Kind.DEATH, name() + " (" + role().title + ") was killed " + by, -3);
            if (post != null) Management.staffVacancy(sl, post, role(), name());
        }
    }

    /** Someone hands them a remedy. Returns true if the offer was understood. */
    /** A remedy handed over from the interaction screen. True if it was one (it's then handled). */
    boolean acceptRemedy(ServerPlayer p, ItemStack held) { return offered(p, held); }

    private boolean offered(ServerPlayer p, ItemStack held) {
        String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
        String line;
        if (id.equals("scp_unleashed:scp500_pill")) {
            if (illness.isEmpty()) line = "I'm not sick. Save it for someone who is.";
            else {
                boolean promised = !Memory.get(this, p, "op_promised500").isEmpty();
                line = promised ? "You remembered. You actually found some. ...I won't forget this." : "SCP-500? For me? ...I can breathe again. Thank you.";
                if (promised) {
                    Memory.put(this, p, "op_promised500", "");
                    Memory.put(this, p, "kept500", "1");
                    getPersistentData().putInt("bp_trust_" + p.getUUID(), Math.min(10, getPersistentData().getInt("bp_trust_" + p.getUUID()) + 3));
                }
                illness = ""; removeEffect(MobEffects.WEAKNESS); held.shrink(1);
            }
        } else if (id.equals("scp_unleashed:scp006_bucket")) {
            if (age < 35) line = "I'm young enough, thanks. Give it to someone who needs the years.";
            else {
                setAge(age - 15);
                line = "It tastes like nothing. Like cold air. ...Oh. Oh, my hands.";
                held.shrink(1);
                p.getInventory().add(new ItemStack(net.minecraft.world.item.Items.BUCKET));
                news(com.breachprotocol.career.WorldNews.Kind.TREATMENT, name() + " was given SCP-006 by " + p.getGameProfile().getName(), 1);
            }
        } else if (id.equals("scp_unleashed:scp427_locket")) {
            say(p, "The locket... just for a moment.");
            if (!wearLocket()) say(p, "There. I feel twenty years younger. Take it back before I want to keep it.");
            return true;
        } else if (held.is(com.breachprotocol.registry.ModItems.FRUIT_OF_LIFE.get())) {
            setAge(age - 25);
            illness = "";
            removeEffect(MobEffects.WEAKNESS);
            line = "Where did you get this? ...I feel like I've just woken up.";
            held.shrink(1);
        } else if (held.is(net.minecraft.world.item.Items.GOLDEN_APPLE)) {
            heal(getMaxHealth());
            line = "For me? That's kind of you.";
            held.shrink(1);
        } else return false;
        say(p, line);
        return true;
    }

    private String name() { return getCustomName() == null ? role().title : getCustomName().getString(); }

    /** Gives them a first and last name no one else in the world has. */
    private void name(Role role) {
        if (!(level() instanceof net.minecraft.server.level.ServerLevel sl)) return;
        String n = role == Role.SECRETARY ? Names.woman(sl.getServer(), random) : Names.person(sl.getServer(), random);
        String prefix = unit != null ? unit.prefix : role.prefix;
        setCustomName(Component.literal((prefix.isEmpty() ? "" : prefix + " ") + n).withStyle(role == Role.GUARD ? ChatFormatting.DARK_AQUA : ChatFormatting.WHITE));
        setCustomNameVisible(false);
        named = true;
        gender();
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!named && !level().isClientSide) name(role());
        if (!level().isClientSide && tickCount % 40 == 3 && role() == Role.GUARD && getMainHandItem().isEmpty()) arm();
        if (!level().isClientSide && tickCount % 5 == 0) errandTick();
        if (!level().isClientSide) {
            if (tickCount % 10 == 0) entityData.set(TALKING, Dialogue.talkingTo(this) != null);
            if (tickCount % 20 == 5) entityData.set(FRIGHT, role() != Role.GUARD && unit == null && Talk.anomalyNear(this, 9) != null);
            int w = entityData.get(WAVE);
            if (w > 0) entityData.set(WAVE, w - 1);
        }
        if (!level().isClientSide && tickCount % 20 == 1 && com.breachprotocol.world.Site01.stale(this)) { Seats.stand(this); discard(); return; }
        if (!level().isClientSide && tickCount % 4 == 0) Doors.along(this);
        if (level().isClientSide || tickCount % 20 != 0) return;
        if (tickCount % 200 == 0 && !getTags().contains(com.breachprotocol.career.RiftTeams.CREW_TAG)) {
            setAge(age + 200.0 / YEAR);
            if ((int) age != lastYear) {
                boolean first = lastYear < 0;
                lastYear = (int) age;
                if (!first) birthday();
                if (isRemoved()) return;
            }
            if (!illness.isEmpty() && random.nextInt(6) == 0 && level() instanceof net.minecraft.server.level.ServerLevel sl)
                sl.sendParticles(net.minecraft.core.particles.ParticleTypes.SNEEZE, getX(), getEyeY(), getZ(), 3, 0.1, 0.1, 0.1, 0.02);
        }
        if (!traitsApplied) applyTraits();
        if (tickCount % 40 == 0 && com.breachprotocol.compat.McaCompat.present() && McaFoundation.toVillager(this)) return; // with MCA, our people are MCA villagers
        if ("stay".equals(mode) && stayAt != null && sitTarget == null && !Seats.seated(this) && tickCount % 40 == 0 && distanceToSqr(stayAt.getX() + 0.5, stayAt.getY(), stayAt.getZ() + 0.5) > 4)
            getNavigation().moveTo(stayAt.getX() + 0.5, stayAt.getY(), stayAt.getZ() + 0.5, 0.9);
        if (sitTarget != null) {
            if (++sitTries > 20 || Seats.seated(this)) sitTarget = null;
            else if (position().distanceToSqr(sitTarget.getX() + 0.5, sitTarget.getY(), sitTarget.getZ() + 0.5) < 2.6) { Seats.sit(this, sitTarget); sitTarget = null; }
            else getNavigation().moveTo(sitTarget.getX() + 0.5, sitTarget.getY(), sitTarget.getZ() + 0.5, 0.9);
        }
        if (post != null && tickCount % 200 == 0 && "post".equals(mode) && goal == null && (activity.isEmpty() || activity.equals("work"))) restrictTo(post, 12);
        if (tickCount % 100 == 0) schedule();
        if (isSleeping()) return;
        travelTick();
        if (tickCount % 400 == 7 && random.nextFloat() < 0.15F) Talk.chatter(this);
        Player near = level().getNearestPlayer(this, 6);
        if (!(near instanceof ServerPlayer p) || p.isSpectator() || !hasLineOfSight(p)) return;
        long now = level().getGameTime();
        Long last = greeted.get(p.getUUID());
        if (last != null && now - last < 20 * 150) return;
        greeted.put(p.getUUID(), now);
        getLookControl().setLookAt(p, 30, 30);
        Rank r = Career.rank(p);
        // the top brass gets everyone on their feet
        if ((r.atLeast(Rank.OVERSEER) || Career.isAscended(p)) && Seats.seated(this)) Seats.stand(this);
        Career.ADDRESS.set(Memory.get(this, p, "addr"));
        try { say(p, Talk.greeting(this, p, greeting(p, r))); } finally { Career.ADDRESS.remove(); }
        if (!Seats.seated(this)) entityData.set(WAVE, 30);
    }

    private String greeting(ServerPlayer p, Rank r) {
        String a = Career.address(p);
        if (Career.isAscended(p)) return pick(Scene.cap(a) + ". Welcome back.", Scene.cap(a) + ". Whatever you need.", "The site is ready for you, " + a + ".", "Good to see you, " + a + ".");
        return switch (r) {
            case CLASS_D -> switch (role()) {
                case GUARD -> pick("Eyes down, " + a + ". Keep moving.", "Hands where I can see them, " + a + ".", "You're out of your block, " + a + ". Back. Now.");
                case RESEARCHER -> pick("Ah, a test subject. Stay there.", a + ", you're on my schedule for later. Hydrate.");
                case MEDIC -> pick("Orange jumpsuit. Sit, I'll check your vitals.", "Still breathing, " + a + "? Good.");
                default -> pick("Mind the wet floor, " + a + ".", "Don't touch anything, " + a + ".");
            };
            case LEVEL_1, LEVEL_2 -> pick("Morning, " + a + ".", "Badge on you, " + a + "? Good.", "Coffee's fresh on the ground floor, " + a + ".");
            case LEVEL_3, LEVEL_4 -> pick("Officer " + a + ". Cell checks are done.", a + ". All quiet on this floor.", "Good to see you, " + a + ".");
            case LEVEL_5 -> pick("Director. The reports are on your desk.", "Director " + a + ". Everything's on schedule.", "Morning, Director.");
            case OVERSEER -> pick(Scene.cap(a) + ". It's... an honour.", "Anything you need, " + a + ".", "We're ready, " + a + ".");
            case ADMINISTRATOR -> pick(Scene.cap(a) + "! Everyone up.", "Good to see you, " + a + ". The site is yours.", Scene.cap(a) + ". We didn't know you were coming.", "Morning, " + a + ".");
        };
    }

    private String pick(String... s) { return s[random.nextInt(s.length)]; }

    private void say(ServerPlayer p, String line) {
        // in the middle of a conversation, it's said there
        if (Dialogue.active(p, "npc:" + getId())) { Talk.say(p, this, line); return; }
        p.sendSystemMessage(Component.literal(name() + ": ").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(line).withStyle(ChatFormatting.GRAY)));
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer p)) return InteractionResult.sidedSuccess(level().isClientSide);
        if (!p.getMainHandItem().isEmpty() && offered(p, p.getMainHandItem())) return InteractionResult.SUCCESS;
        if (isSleeping()) {
            stopSleeping();
            activity = "";
            p.displayClientMessage(Component.literal(name() + " wakes up with a start.").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), true);
        }
        if (!p.isShiftKeyDown()) { Talk.open(p, this); return InteractionResult.SUCCESS; }
        getLookControl().setLookAt(p, 30, 30);
        Rank r = Career.rank(p);
        String line = switch (role()) {
            case MEDIC -> {
                long now = level().getGameTime();
                if (r != Rank.CLASS_D && now >= healReady) {
                    healReady = now + 20 * 120;
                    p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 10, 1));
                    yield "Hold still. There. You'll live, " + Career.address(p) + ".";
                }
                yield r == Rank.CLASS_D ? "Back of the queue, D-Class." : "Come back in a couple of minutes. I'm out of stims.";
            }
            case GUARD -> r.atLeast(Rank.LEVEL_3) ? "Containment's holding. If anything breaches, I'm right behind you." : "Keep your keycard visible.";
            case RESEARCHER -> pick("Never look away from 173. I mean it.", "Stasis pods only hold what they're rated for. Check the label.",
                    "A pumpkin on your head beats a rifle against 096.", "SCP-999 can clean 106's corrosion off you.", "Read the dossier before you open the door.");
            case TECHNICIAN -> pick("Power's stable. The creative supply in the plant feeds the whole site.", "If a door won't open, check the scanner on its jamb.",
                    "Server room's running cool today.");
            case CLERK -> pick("Your assignments are on your phone, " + Career.address(p) + ".", "Paperwork never ends here.", "The archive is on floor four.");
            case JANITOR -> pick("I don't clean the cells on B3. Nobody pays enough.", "Watch your step.", "Someone left 999 in the break room again.");
            case SECRETARY -> r == Rank.ADMINISTRATOR || Career.isAscended(p) ? pick("Administrator. Your briefing is ready whenever you sit down.", "Good to have you back, Administrator.", "Administrator. I've kept your calendar clear where I could.")
                    : pick("The Administrator's office. Do you have an appointment?", "Please wait here. The Administrator is very busy.");
        };
        say(p, line);
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public boolean removeWhenFarAway(double distance) { return false; }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Role", entityData.get(ROLE));
        tag.putInt("Variant", entityData.get(VARIANT));
        if (post != null) { tag.putInt("PostX", post.getX()); tag.putInt("PostY", post.getY()); tag.putInt("PostZ", post.getZ()); }
        tag.putString("Mode", mode);
        tag.putDouble("Age", age);
        tag.putInt("LastYear", lastYear);
        tag.putString("Illness", illness);
        tag.putInt("Locket", locketUses);
        tag.putDouble("Fountain", fountainAt);
        tag.putBoolean("Declined", declined);
        tag.putBoolean("Named", named);
        tag.putString("Errand", errand);
        tag.putLong("BackToPost", backToPostAt);
        if (unit != null) tag.putString("Unit", unit.name());
        if (leader != null) tag.putUUID("Leader", leader);
        if (stayAt != null) { tag.putInt("StayX", stayAt.getX()); tag.putInt("StayY", stayAt.getY()); tag.putInt("StayZ", stayAt.getZ()); }
        tag.putString("Genome", entityData.get(GENOME));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(ROLE, tag.getInt("Role"));
        entityData.set(VARIANT, tag.getInt("Variant"));
        if (tag.contains("PostX")) { post = new BlockPos(tag.getInt("PostX"), tag.getInt("PostY"), tag.getInt("PostZ")); restrictTo(post, 12); }
        if (tag.contains("Mode")) mode = tag.getString("Mode");
        if (tag.contains("Age")) setAge(tag.getDouble("Age"));
        lastYear = tag.contains("LastYear") ? tag.getInt("LastYear") : (int) age;
        illness = tag.getString("Illness");
        locketUses = tag.getInt("Locket");
        fountainAt = tag.contains("Fountain") ? tag.getDouble("Fountain") : -100;
        declined = tag.getBoolean("Declined");
        named = tag.getBoolean("Named");
        if (getCustomName() != null) gender();
        errand = tag.getString("Errand");
        backToPostAt = tag.getLong("BackToPost");
        unit = com.breachprotocol.career.Forces.Unit.of(tag.getString("Unit"));
        var spd = getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        if (spd != null && spd.getBaseValue() < 0.28) spd.setBaseValue(0.28);
        var fr = getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE);
        if (fr != null && fr.getBaseValue() < 64) fr.setBaseValue(64);
        if (tag.hasUUID("Leader")) leader = tag.getUUID("Leader");
        if (tag.contains("StayX")) { stayAt = new BlockPos(tag.getInt("StayX"), tag.getInt("StayY"), tag.getInt("StayZ")); if ("stay".equals(mode)) restrictTo(stayAt, 1); }
        if ("follow".equals(mode)) clearRestriction();
        entityData.set(GENOME, tag.getString("Genome"));
        traitsApplied = false;
    }
}
