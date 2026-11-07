package com.breachprotocol.career;

import com.breachprotocol.compat.UnleashedApi;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Sending someone else to deal with a rift. A D-Class team (Level 4 and up) is cheap and sometimes
 * doesn't come back; an MTF extraction team (O5 and up) always gets it done. When the team arrives,
 * the rift is drawn out with SCP Unleashed's own extraction (Extractable.createFragment + collapse)
 * and the fragment is delivered to you.
 */
@EventBusSubscriber(modid = com.breachprotocol.BreachProtocol.MODID)
public final class RiftTeams {
    private RiftTeams() {}

    private record Job(UUID player, int riftId, String dim, boolean mtf, long due, int x, int z) {}

    private static final List<Job> JOBS = new ArrayList<>();

    public static void send(ServerPlayer p, int riftId, boolean mtf) {
        Rank r = Career.rank(p);
        if (mtf ? !r.atLeast(Rank.OVERSEER) && !Career.isAscended(p) : !r.atLeast(Rank.LEVEL_4) && !Career.isAscended(p)) {
            p.displayClientMessage(Component.literal(mtf ? "Only the Council can send an MTF extraction team." : "Level 4 can assign D-Class to rift duty.").withStyle(ChatFormatting.RED), true);
            return;
        }
        Entity rift = p.serverLevel().getEntity(riftId);
        if (rift == null || !UnleashedApi.isRift(rift)) return;
        for (Job j : JOBS) if (j.riftId == riftId) { p.displayClientMessage(Component.literal("A team is already on its way.").withStyle(ChatFormatting.GRAY), true); return; }
        int dist = (int) p.distanceTo(rift);
        long travel = 20L * (mtf ? 30 + dist / 6 : 45 + dist / 4);
        JOBS.add(new Job(p.getUUID(), riftId, p.level().dimension().location().toString(), mtf, p.server.overworld().getGameTime() + travel, rift.getBlockX(), rift.getBlockZ()));
        Career.message(p, mtf ? O5.O5_8 : O5.O5_5, mtf
                ? "Extraction team dispatched to the rift at " + rift.getBlockX() + ", " + rift.getBlockZ() + ". ETA " + travel / 20 + " seconds."
                : "Two D-Class with a Rift Extractor are walking to " + rift.getBlockX() + ", " + rift.getBlockZ() + ". I'd give it " + travel / 20 + " seconds. Maybe.");
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (JOBS.isEmpty() || server.getTickCount() % 20 != 0) return;
        long now = server.overworld().getGameTime();
        Iterator<Job> it = JOBS.iterator();
        while (it.hasNext()) {
            Job j = it.next();
            if (now < j.due) continue;
            it.remove();
            ServerPlayer p = server.getPlayerList().getPlayer(j.player);
            ServerLevel level = null;
            for (ServerLevel l : server.getAllLevels()) if (l.dimension().location().toString().equals(j.dim)) level = l;
            Entity rift = level == null ? null : level.getEntity(j.riftId);
            if (p == null) continue;
            if (rift == null || !UnleashedApi.isRift(rift)) {
                Career.message(p, j.mtf ? O5.O5_8 : O5.O5_5, "The team reached " + j.x + ", " + j.z + ". The rift was already gone.");
                continue;
            }
            if (!j.mtf && p.getRandom().nextFloat() < 0.3F) {
                Career.message(p, O5.O5_5, "We lost the D-Class team at the rift near " + j.x + ", " + j.z + ". It's still open. Send another, or go yourself.");
                Career.changeStability(p, -1);
                continue;
            }
            ItemStack frag = UnleashedApi.extractRift(rift);
            if (!frag.isEmpty() && !p.getInventory().add(frag)) p.drop(frag, false);
            Career.message(p, j.mtf ? O5.O5_8 : O5.O5_5, (j.mtf ? "Extraction team" : "The D-Class team") + " drew out the rift at " + j.x + ", " + j.z + "."
                    + (frag.isEmpty() ? "" : " The fragment has been sent to you."));
            Career.changeStability(p, 2);
            Career.commend(p);
            Career.sync(p);
        }
    }

    /** Rift ids with a team on the way, for the phone. */
    public static void addToSync(ServerPlayer p, CompoundTag t) {
        net.minecraft.nbt.ListTag l = new net.minecraft.nbt.ListTag();
        for (Job j : JOBS) if (j.player.equals(p.getUUID())) l.add(net.minecraft.nbt.IntTag.valueOf(j.riftId));
        t.put("riftTeams", l);
    }
}
