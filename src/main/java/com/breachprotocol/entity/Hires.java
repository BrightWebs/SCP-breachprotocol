package com.breachprotocol.entity;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.career.WorldNews;
import com.breachprotocol.registry.ModEntities;
import com.breachprotocol.world.Site01;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Iterator;

/**
 * When someone at a site dies, the Foundation hires their replacement. A day later a new face turns
 * up at the same post: same department, young, at the start of their own story.
 */
@EventBusSubscriber(modid = BreachProtocol.MODID)
public final class Hires {
    private Hires() {}

    public static final class Data extends SavedData {
        final ListTag pending = new ListTag();
        public static final SavedData.Factory<Data> FACTORY = new SavedData.Factory<>(Data::new, Data::load, null);

        static Data load(CompoundTag t, HolderLookup.Provider p) {
            Data d = new Data();
            d.pending.addAll(t.getList("pending", Tag.TAG_COMPOUND));
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag t, HolderLookup.Provider p) {
            t.put("pending", pending);
            return t;
        }
    }

    private static Data data(net.minecraft.server.MinecraftServer s) { return s.overworld().getDataStorage().computeIfAbsent(Data.FACTORY, "breachprotocol_hires"); }

    public static void schedule(ServerLevel l, BlockPos post, FoundationStaff.Role role) {
        Data d = data(l.getServer());
        CompoundTag t = new CompoundTag();
        t.putString("dim", l.dimension().location().toString());
        t.putLong("pos", post.asLong());
        t.putInt("role", role.ordinal());
        t.putLong("due", l.getServer().overworld().getGameTime() + FoundationStaff.YEAR);
        d.pending.add(t);
        d.setDirty();
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 200 != 0) return;
        Data d = data(event.getServer());
        if (d.pending.isEmpty()) return;
        long now = event.getServer().overworld().getGameTime();
        for (Iterator<Tag> it = d.pending.iterator(); it.hasNext(); ) {
            CompoundTag t = (CompoundTag) it.next();
            if (now < t.getLong("due")) continue;
            ServerLevel l = null;
            for (ServerLevel s : event.getServer().getAllLevels()) if (s.dimension().location().equals(ResourceLocation.tryParse(t.getString("dim")))) l = s;
            BlockPos p = BlockPos.of(t.getLong("pos"));
            if (l == null) { it.remove(); continue; }
            if (!l.isLoaded(p)) continue;
            FoundationStaff s = ModEntities.STAFF.get().create(l);
            if (s == null) { it.remove(); continue; }
            FoundationStaff.Role role = FoundationStaff.Role.values()[Math.floorMod(t.getInt("role"), FoundationStaff.Role.values().length)];
            s.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, l.random.nextFloat() * 360F, 0);
            s.setup(role, p);
            if (Site01.isInside(l, p)) s.getPersistentData().putInt(Site01.GEN_KEY, Site01.data(l.getServer()).gen);
            l.addFreshEntity(s);
            WorldNews.record(event.getServer(), WorldNews.Kind.HIRE, "hire", "a new " + role.title.toLowerCase(java.util.Locale.ROOT) + " hire, " + s.getName().getString() + ", started today", 1, "");
            it.remove();
            d.setDirty();
        }
    }
}
