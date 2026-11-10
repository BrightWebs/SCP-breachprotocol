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
            if (!present() || event.getServer().getTickCount() % 100 != 41) return;
            for (ServerPlayer p : event.getServer().getPlayerList().getPlayers())
                if (Vault.chief(p)) try { com.breachprotocol.compat.manas.ManasBridge.grant(p); } catch (Throwable t) { present = false; return; }
        }
    }

    /** "shape:mob:id" / "shape:block:id", from the menu. */
    public static void action(ServerPlayer p, String a) {
        if (!present()) return;
        try { com.breachprotocol.compat.manas.ManasBridge.action(p, a); } catch (Throwable t) { BreachProtocol.LOGGER.warn("Shapeshift: {}", t.toString()); }
    }

    public static void addToSync(ServerPlayer p, CompoundTag t) {
        if (p.getPersistentData().contains("bp_shape_open")) t.putLong("shapeOpen", p.getPersistentData().getLong("bp_shape_open"));
    }
}
