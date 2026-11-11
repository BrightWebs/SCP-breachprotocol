package com.breachprotocol.career;

import com.breachprotocol.BreachProtocol;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Who put each block on Foundation ground (sites only): so a facility review can tell your own building from
 * what others, or mobs, put there. Breaking a block forgets it.
 */
@EventBusSubscriber(modid = BreachProtocol.MODID)
public final class Placed extends SavedData {
    /** Placed by something that isn't a player (an enderman, a dispenser...). */
    public static final UUID NOT_A_PLAYER = new UUID(0, 0);

    /** dimension -> position -> who placed it. */
    private final Map<String, Map<Long, UUID>> by = new HashMap<>();

    public static final SavedData.Factory<Placed> FACTORY = new SavedData.Factory<>(Placed::new, Placed::load, null);

    public static Placed get(MinecraftServer s) { return s.overworld().getDataStorage().computeIfAbsent(FACTORY, "breachprotocol_placed"); }

    /** Who placed the block at this spot, or null if nobody's on record (it was there before records began, or came by itself). */
    public static UUID who(Level l, BlockPos p) {
        if (l.getServer() == null) return null;
        Map<Long, UUID> m = get(l.getServer()).by.get(l.dimension().location().toString());
        return m == null ? null : m.get(p.asLong());
    }

    public static void forget(Level l, BlockPos p) {
        if (l.getServer() == null) return;
        Placed d = get(l.getServer());
        Map<Long, UUID> m = d.by.get(l.dimension().location().toString());
        if (m != null && m.remove(p.asLong()) != null) d.setDirty();
    }

    private static void note(Level l, BlockPos p, Entity e) {
        if (l.isClientSide() || l.getServer() == null || Sites.at(l, p) == null) return;
        Placed d = get(l.getServer());
        d.by.computeIfAbsent(l.dimension().location().toString(), k -> new HashMap<>())
                .put(p.asLong(), e instanceof net.minecraft.world.entity.player.Player pl ? pl.getUUID() : NOT_A_PLAYER);
        d.setDirty();
    }

    @SubscribeEvent
    public static void placed(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof Level l)) return;
        if (event instanceof BlockEvent.EntityMultiPlaceEvent multi)
            for (BlockSnapshot s : multi.getReplacedBlockSnapshots()) note(l, s.getPos(), event.getEntity());
        else note(l, event.getPos(), event.getEntity());
    }

    @SubscribeEvent
    public static void broken(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof Level l && !l.isClientSide()) forget(l, event.getPos());
    }

    private static Placed load(CompoundTag t, HolderLookup.Provider pr) {
        Placed d = new Placed();
        for (String dim : t.getAllKeys()) {
            CompoundTag dt = t.getCompound(dim);
            Map<Long, UUID> m = new HashMap<>();
            for (String who : dt.getAllKeys()) {
                UUID u;
                try { u = UUID.fromString(who); } catch (IllegalArgumentException e) { continue; }
                for (long pos : dt.getLongArray(who)) m.put(pos, u);
            }
            d.by.put(dim, m);
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag t, HolderLookup.Provider pr) {
        for (Map.Entry<String, Map<Long, UUID>> e : by.entrySet()) {
            Map<UUID, java.util.List<Long>> per = new HashMap<>();
            e.getValue().forEach((pos, u) -> per.computeIfAbsent(u, k -> new java.util.ArrayList<>()).add(pos));
            CompoundTag dt = new CompoundTag();
            per.forEach((u, l) -> dt.put(u.toString(), new LongArrayTag(l)));
            t.put(e.getKey(), dt);
        }
        return t;
    }
}
