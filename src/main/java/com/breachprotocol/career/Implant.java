package com.breachprotocol.career;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.compat.UnleashedApi;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * The Administrator's implant (and SCP-Ω, which needs no implant) keeps them breathing: under water,
 * and in the vacuum of SCP Unleashed's space (the Moon and beyond), through SCP Unleashed's own
 * "shelter" hook, the same one its pressurised vehicles use.
 */
@EventBusSubscriber(modid = BreachProtocol.MODID)
public final class Implant {
    private Implant() {}

    static boolean breathes(LivingEntity e) {
        return e instanceof ServerPlayer p && (Career.rank(p) == Rank.ADMINISTRATOR || Career.isAscended(p));
    }

    private static boolean registered;

    @SubscribeEvent
    public static void starting(ServerStartingEvent event) {
        if (registered) return;
        try {
            Class<?> shelter = UnleashedApi.cls("space.SpaceAir$Shelter");
            Method reg = UnleashedApi.method("space.SpaceAir", "registerShelter", shelter);
            if (shelter == null || reg == null) return;
            Object hook = Proxy.newProxyInstance(Implant.class.getClassLoader(), new Class<?>[]{shelter}, (proxy, m, args) -> {
                if (m.getName().equals("shelters") && args != null && args.length == 1) return args[0] instanceof LivingEntity le && breathes(le);
                if (m.getName().equals("hashCode")) return System.identityHashCode(proxy);
                if (m.getName().equals("equals")) return proxy == args[0];
                if (m.getName().equals("toString")) return "Breach Protocol implant";
                return null;
            });
            reg.invoke(null, hook);
            registered = true;
        } catch (Throwable t) {
            BreachProtocol.LOGGER.warn("Couldn't give the implant its air in space: {}", t.toString());
        }
    }

    /** Under water: the lungs never run short. */
    @SubscribeEvent
    public static void air(PlayerTickEvent.Post event) {
        Player p = event.getEntity();
        if (p.level().isClientSide() || p.tickCount % 5 != 0 || !breathes(p)) return;
        if (p.getAirSupply() < p.getMaxAirSupply()) p.setAirSupply(p.getMaxAirSupply());
    }

    @SubscribeEvent
    public static void drowning(LivingIncomingDamageEvent event) {
        if (breathes(event.getEntity()) && event.getSource().is(DamageTypes.DROWN)) event.setCanceled(true);
    }
}
