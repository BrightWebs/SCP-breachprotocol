package com.breachprotocol.career;

import com.breachprotocol.BreachProtocol;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * Shapeshift, the Administrator's skill in Manas Reborn's list: only when ManasCore and Manas Reborn
 * are installed. This class touches none of their classes (compat.manas.ManasBridge does, and is only
 * loaded once we know they're here).
 */
public final class Shapeshift {
    private Shapeshift() {}

    private static Boolean present;

    public static boolean present() {
        if (present == null) present = ModList.get() != null && ModList.get().isLoaded("manascore") && ModList.get().isLoaded("manas_reborn");
        return present;
    }

    /** Registration happens on the mod bus. */
    @EventBusSubscriber(modid = BreachProtocol.MODID, bus = EventBusSubscriber.Bus.MOD)
    public static final class Reg {
        @SubscribeEvent
        public static void register(RegisterEvent event) {
            if (!present()) return;
            try { com.breachprotocol.compat.manas.ManasBridge.register(event); }
            catch (Throwable t) { present = false; BreachProtocol.LOGGER.warn("Shapeshift couldn't be registered with Manas Reborn: {}", t.toString()); }
        }
    }

    @EventBusSubscriber(modid = BreachProtocol.MODID)
    public static final class Game {
        /** Become Administrator and it's yours. */
        @SubscribeEvent
        public static void tick(ServerTickEvent.Post event) {
            if (!present()) return;
            if (event.getServer().getTickCount() % 20 == 7)
                for (ServerPlayer p : event.getServer().getPlayerList().getPlayers())
                    if (disguised(p)) try { com.breachprotocol.compat.manas.ManasBridge.traits(p); } catch (Throwable t) { return; }
            if (event.getServer().getTickCount() % 100 != 41) return;
            for (ServerPlayer p : event.getServer().getPlayerList().getPlayers())
                if (Vault.chief(p)) try { com.breachprotocol.compat.manas.ManasBridge.grant(p); } catch (Throwable t) { present = false; return; }
        }
    }

    /** One form, put at the top of your forms ("mobs" or "blocks"). */
    public static void addForm(ServerPlayer p, String list, String id) {
        String key = "bp_shape_" + list;
        net.minecraft.nbt.ListTag old = p.getPersistentData().getList(key, net.minecraft.nbt.Tag.TAG_STRING), now = new net.minecraft.nbt.ListTag();
        now.add(net.minecraft.nbt.StringTag.valueOf(id));
        for (net.minecraft.nbt.Tag t : old) if (!t.getAsString().equals(id) && now.size() < 6000) now.add(t);
        p.getPersistentData().put(key, now);
        Career.sync(p);
    }

    /** Every living creature and every block, among your forms. Returns how many. */
    public static int unlockAll(ServerPlayer p) {
        net.minecraft.nbt.ListTag mobs = new net.minecraft.nbt.ListTag(), blocks = new net.minecraft.nbt.ListTag();
        for (var t : net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE) {
            if (t == net.minecraft.world.entity.EntityType.PLAYER) continue;
            @SuppressWarnings("unchecked") var lt = (net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.LivingEntity>) t;
            boolean living;
            try { living = net.minecraft.world.entity.ai.attributes.DefaultAttributes.hasSupplier(lt); } catch (Throwable e) { living = false; }
            if (living && !com.breachprotocol.career.CreatureMoves.creature(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(t).getPath())) living = false;
            if (living) mobs.add(net.minecraft.nbt.StringTag.valueOf(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(t).toString()));
        }
        for (var b : net.minecraft.core.registries.BuiltInRegistries.BLOCK)
            if (b.asItem() != net.minecraft.world.item.Items.AIR) blocks.add(net.minecraft.nbt.StringTag.valueOf(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(b).toString()));
        p.getPersistentData().put("bp_shape_mobs", mobs);
        p.getPersistentData().put("bp_shape_blocks", blocks);
        Career.sync(p);
        return mobs.size() + blocks.size();
    }

    /** "shape:mob:id" / "shape:block:id", from the menu. */
    public static void action(ServerPlayer p, String a) {
        if (!present()) return;
        if (a.equals("all")) {
            if (!p.hasPermissions(2)) return;
            int n = unlockAll(p);
            p.displayClientMessage(net.minecraft.network.chat.Component.literal("Every form is yours (" + n + ").").withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE), true);
            return;
        }
        try { com.breachprotocol.compat.manas.ManasBridge.action(p, a); } catch (Throwable t) { BreachProtocol.LOGGER.warn("Shapeshift: {}", t.toString()); }
    }

    /** In another form: nothing about them (the Administrator's light included) gives them away. */
    public static boolean disguised(ServerPlayer p) { return p.getPersistentData().contains("bp_shape_form"); }

    public static void addToSync(ServerPlayer p, CompoundTag t) {
        CompoundTag pd = p.getPersistentData();
        if (pd.contains("bp_shape_open")) t.putLong("shapeOpen", pd.getLong("bp_shape_open"));
        if (!present()) return;
        boolean has;
        try { has = com.breachprotocol.compat.manas.ManasBridge.has(p); } catch (Throwable e) { has = false; }
        t.putBoolean("shapeHas", has);
        if (!has) return;
        t.putString("shapeForm", pd.getString("bp_shape_form"));
        t.putBoolean("shapeCreature", pd.contains("bp_shape_type"));
        if (pd.contains("bp_shape_mobs")) t.put("shapeMobs", pd.get("bp_shape_mobs").copy());
        if (pd.contains("bp_shape_blocks")) t.put("shapeBlocks", pd.get("bp_shape_blocks").copy());
    }
}
