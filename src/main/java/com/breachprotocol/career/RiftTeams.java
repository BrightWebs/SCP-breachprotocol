package com.breachprotocol.career;

import com.breachprotocol.compat.Unleashed;
import com.breachprotocol.compat.UnleashedApi;
import com.breachprotocol.entity.FoundationStaff;
import com.breachprotocol.registry.ModEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.*;

/**
 * Sending a crew to deal with a rift. A D-Class team (Level 4 and up) is cheap and sometimes
 * doesn't come back; an MTF extraction team (O5 and up) always gets it done.
 *
 * <p>The crew really turns up: once their travel time is over they appear a short walk from the
 * rift, walk to it, spread around it and work (holding up the rift extractor, sparks drawn out of
 * the rift) until it is drawn out with SCP Unleashed's own extraction (Extractable.createFragment +
 * collapse) and the fragment is sent to you. A D-Class crew that fails is pulled into the rift. If
 * nobody is near enough for the rift's area to be loaded, the job is settled without the show.
 */
@EventBusSubscriber(modid = com.breachprotocol.BreachProtocol.MODID)
public final class RiftTeams {
    private RiftTeams() {}

    public static final String CREW_TAG = "bp_rift_crew";
    private static final int WORK = 20 * 14;

    private static final class Job {
        final UUID player;
        final int riftId;
        final String dim;
        final boolean mtf;
        final long due;
        final int x, z;
        final List<UUID> crew = new ArrayList<>();
        long workStart = -1, doneAt = -1;
        boolean failed;

        Job(UUID player, int riftId, String dim, boolean mtf, long due, int x, int z) {
            this.player = player; this.riftId = riftId; this.dim = dim; this.mtf = mtf; this.due = due; this.x = x; this.z = z;
        }
    }

    private static final List<Job> JOBS = new ArrayList<>();

    public static void send(ServerPlayer p, int riftId, boolean mtf) {
        if (!mtf && Policy.data(p.server).riftMtfOnly && (Career.rank(p).atLeast(Rank.OVERSEER) || Career.isAscended(p))) {
            mtf = true; // the Council's rule: rifts are task force work
            p.displayClientMessage(Component.literal("Council policy: rifts are MTF work. Sending a task force instead.").withStyle(ChatFormatting.AQUA), true);
        }
        Rank r = Career.rank(p);
        if (mtf ? !r.atLeast(Rank.OVERSEER) && !Career.isAscended(p) : !r.atLeast(Rank.LEVEL_4) && !Career.isAscended(p)) {
            p.displayClientMessage(Component.literal(mtf ? "Only the Council can send an MTF extraction team." : "Level 4 can assign D-Class to rift duty.").withStyle(ChatFormatting.RED), true);
            return;
        }
        Entity rift = p.serverLevel().getEntity(riftId);
        if (rift == null || !UnleashedApi.isRift(rift)) return;
        for (Job j : JOBS) if (j.riftId == riftId) { p.displayClientMessage(Component.literal("A team is already on its way.").withStyle(ChatFormatting.GRAY), true); return; }
        int dist = (int) p.distanceTo(rift);
        long travel = 20L * (mtf ? 20 + dist / 8 : 30 + dist / 5);
        JOBS.add(new Job(p.getUUID(), riftId, p.level().dimension().location().toString(), mtf, p.server.overworld().getGameTime() + travel, rift.getBlockX(), rift.getBlockZ()));
        Career.message(p, mtf ? O5.O5_8 : O5.O5_5, mtf
                ? "Extraction team dispatched to the rift at " + rift.getBlockX() + ", " + rift.getBlockZ() + ". ETA " + travel / 20 + " seconds."
                : "Two D-Class with a Rift Extractor are walking to " + rift.getBlockX() + ", " + rift.getBlockZ() + ". I'd give it " + travel / 20 + " seconds. Maybe.");
    }

    /** Crew left over from before a restart (their job is gone) never come back. */
    @SubscribeEvent
    public static void join(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide || !event.getEntity().getTags().contains(CREW_TAG)) return;
        UUID id = event.getEntity().getUUID();
        for (Job j : JOBS) if (j.crew.contains(id)) return;
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (JOBS.isEmpty() || server.getTickCount() % 5 != 0) return;
        long now = server.overworld().getGameTime();
        for (Iterator<Job> it = JOBS.iterator(); it.hasNext(); ) {
            Job j = it.next();
            if (now < j.due) continue;
            ServerLevel level = null;
            for (ServerLevel l : server.getAllLevels()) if (l.dimension().location().toString().equals(j.dim)) level = l;
            ServerPlayer p = server.getPlayerList().getPlayer(j.player);
            Entity rift = level == null ? null : level.getEntity(j.riftId);
            if (level == null || rift == null || !UnleashedApi.isRift(rift)) {
                if (p != null) Career.message(p, j.mtf ? O5.O5_8 : O5.O5_5, "The team reached " + j.x + ", " + j.z + ". The rift was already gone.");
                dismiss(level, j);
                it.remove();
                continue;
            }
            if (j.workStart < 0) {
                arrive(level, j, rift, p);
                j.workStart = now;
                if (j.crew.isEmpty()) j.workStart = now - WORK;   // nobody to show: settle it now
                continue;
            }
            if (j.doneAt < 0) {
                work(level, j, rift, now);
                if (now - j.workStart >= WORK) {
                    finish(level, j, rift, p);
                    j.doneAt = now;
                }
                continue;
            }
            if (now - j.doneAt >= 20 * 5) {
                dismiss(level, j);
                it.remove();
            }
        }
    }

    private static void arrive(ServerLevel l, Job j, Entity rift, ServerPlayer p) {
        if (!l.isLoaded(rift.blockPosition())) return;
        int size = j.mtf ? 3 : 2;
        Random r = new Random();
        double ang = r.nextDouble() * Math.PI * 2;
        for (int i = 0; i < size; i++) {
            Mob m = j.mtf ? ModEntities.STAFF.get().create(l) : Unleashed.entity(Unleashed.DCLASS).map(t -> t.create(l)).filter(e -> e instanceof Mob).map(e -> (Mob) e).orElse(null);
            if (m == null) continue;
            double a = ang + (i - size / 2.0) * 0.3;
            int sx = rift.getBlockX() + (int) Math.round(Math.cos(a) * 12), sz = rift.getBlockZ() + (int) Math.round(Math.sin(a) * 12);
            int sy = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sx, sz);
            if (Math.abs(sy - rift.getBlockY()) > 10) sy = rift.getBlockY();
            m.moveTo(sx + 0.5, sy, sz + 0.5, (float) Math.toDegrees(a) + 90F, 0);
            if (m instanceof FoundationStaff s) {
                s.setup(FoundationStaff.Role.GUARD, rift.blockPosition());
                s.setCustomName(Component.literal("MTF Operative").withStyle(ChatFormatting.DARK_AQUA));
                s.setCustomNameVisible(true);
            } else {
                m.setCustomName(Component.literal("D-Class (rift duty)").withStyle(ChatFormatting.GOLD));
            }
            m.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Unleashed.item("rift_extractor", net.minecraft.world.item.Items.SPYGLASS)));
            m.addTag(CREW_TAG);
            m.setInvulnerable(true);
            l.addFreshEntity(m);
            j.crew.add(m.getUUID());
        }
        if (p != null && !j.crew.isEmpty())
            p.displayClientMessage(Component.literal((j.mtf ? "The extraction team" : "The D-Class team") + " has reached the rift at " + j.x + ", " + j.z + ".").withStyle(ChatFormatting.AQUA), true);
    }

    private static void work(ServerLevel l, Job j, Entity rift, long now) {
        int i = 0;
        Vec3 c = rift.position();
        for (UUID id : j.crew) {
            Entity e = l.getEntity(id);
            if (!(e instanceof PathfinderMob m)) continue;
            double a = (Math.PI * 2 * i++) / Math.max(1, j.crew.size());
            Vec3 spot = c.add(Math.cos(a) * 2.6, 0, Math.sin(a) * 2.6);
            if (m.position().distanceToSqr(spot.x, m.getY(), spot.z) > 1.5) m.getNavigation().moveTo(spot.x, c.y, spot.z, 0.9);
            else {
                m.getNavigation().stop();
                m.getLookControl().setLookAt(c.x, c.y + 0.5, c.z);
                if (now % 20 == 0) m.swing(InteractionHand.MAIN_HAND);
                // energy drawn out of the rift into the extractor
                Vec3 to = m.position().add(0, 1.2, 0).subtract(c.add(0, 0.6, 0));
                l.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y + 0.6, c.z, 0, to.x, to.y, to.z, 0.08);
                l.sendParticles(ParticleTypes.PORTAL, c.x, c.y + 0.6, c.z, 3, 0.3, 0.3, 0.3, 0.2);
            }
        }
        if (now % 40 == 0) l.playSound(null, rift.blockPosition(), SoundEvents.BEACON_AMBIENT, SoundSource.NEUTRAL, 0.8F, 1.6F);
    }

    private static void finish(ServerLevel l, Job j, Entity rift, ServerPlayer p) {
        Vec3 c = rift.position();
        if (!j.mtf && new Random().nextFloat() < (Policy.data(l.getServer()).crewGear ? 0.1F : 0.3F)) {
            j.failed = true;
            // the rift takes them
            for (UUID id : j.crew) {
                Entity e = l.getEntity(id);
                if (e == null) continue;
                l.sendParticles(ParticleTypes.REVERSE_PORTAL, e.getX(), e.getY() + 1, e.getZ(), 40, 0.3, 0.6, 0.3, 0.1);
                e.discard();
            }
            l.playSound(null, rift.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.NEUTRAL, 1F, 0.5F);
            j.crew.clear();
            WorldNews.record(l.getServer(), WorldNews.Kind.RIFT_LOST, "rift", "a D-Class crew was pulled into a rift near " + j.x + ", " + j.z, -4, "");
            if (p != null) {
                Career.message(p, O5.O5_5, "We lost the D-Class team at the rift near " + j.x + ", " + j.z + ". It pulled them in. It's still open. Send another, or go yourself.");
                Career.changeStability(p, -1);
            }
            return;
        }
        ItemStack frag = UnleashedApi.extractRift(rift);
        WorldNews.record(l.getServer(), WorldNews.Kind.RIFT_CLOSED, "rift", (j.mtf ? "an MTF team" : "a D-Class crew") + " closed a rift near " + j.x + ", " + j.z, 3, "");
        l.sendParticles(ParticleTypes.FLASH, c.x, c.y + 0.6, c.z, 1, 0, 0, 0, 0);
        l.sendParticles(ParticleTypes.END_ROD, c.x, c.y + 0.6, c.z, 40, 0.5, 0.5, 0.5, 0.15);
        l.playSound(null, BlockPos.containing(c), SoundEvents.BEACON_DEACTIVATE, SoundSource.NEUTRAL, 1F, 1.2F);
        if (p != null) {
            if (!frag.isEmpty() && !p.getInventory().add(frag)) p.drop(frag, false);
            Career.message(p, j.mtf ? O5.O5_8 : O5.O5_5, (j.mtf ? "Extraction team" : "The D-Class team") + " drew out the rift at " + j.x + ", " + j.z + "."
                    + (frag.isEmpty() ? "" : " The fragment has been sent to you."));
            Career.changeStability(p, 2);
            Career.commend(p);
            Career.sync(p);
        }
        // the crew packs up and heads off
        for (UUID id : j.crew) if (l.getEntity(id) instanceof PathfinderMob m)
            m.getNavigation().moveTo(m.getX() + (m.getX() - c.x) * 4, m.getY(), m.getZ() + (m.getZ() - c.z) * 4, 0.8);
    }

    private static void dismiss(ServerLevel l, Job j) {
        if (l == null) return;
        for (UUID id : j.crew) {
            Entity e = l.getEntity(id);
            if (e == null) continue;
            l.sendParticles(ParticleTypes.POOF, e.getX(), e.getY() + 0.8, e.getZ(), 10, 0.3, 0.5, 0.3, 0.02);
            e.discard();
        }
        j.crew.clear();
    }

    /** Rift ids with a team on the way, for the phone. */
    public static void addToSync(ServerPlayer p, CompoundTag t) {
        ListTag l = new ListTag();
        for (Job j : JOBS) if (j.player.equals(p.getUUID())) l.add(IntTag.valueOf(j.riftId));
        t.put("riftTeams", l);
    }
}
