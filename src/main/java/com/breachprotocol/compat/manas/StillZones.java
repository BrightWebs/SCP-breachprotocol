package com.breachprotocol.compat.manas;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.career.Shapeshift;
import com.breachprotocol.career.Vault;
import com.breachprotocol.garden.Garden;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The Garden and the Vault are still places: nobody's Manas Reborn abilities work there (not a player's, not a
 * creature's), except the Administrator's. Hooked into ManasCore's own skill events (activate, hold-release,
 * toggle), each of which is refused there, found by name so nothing of ManasCore's ships with Breach Protocol.
 */
@EventBusSubscriber(modid = BreachProtocol.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class StillZones {
    private StillZones() {}

    private static Object PASS, DENY;
    private static final Map<UUID, Long> TOLD = new HashMap<>();

    @SubscribeEvent
    public static void setup(FMLCommonSetupEvent event) {
        if (!Shapeshift.present()) return;
        event.enqueueWork(() -> {
            try {
                Class<?> result = Class.forName("dev.architectury.event.EventResult");
                PASS = result.getMethod("pass").invoke(null);
                DENY = result.getMethod("interruptFalse").invoke(null);
                Class<?> events = Class.forName("io.github.manasmods.manascore.skill.api.SkillEvents");
                hook(events, "ACTIVATE_SKILL", "SkillActivationEvent");
                hook(events, "RELEASE_SKILL", "SkillReleaseEvent");
                hook(events, "TOGGLE_SKILL", "SkillToggleEvent");
            } catch (Throwable t) {
                BreachProtocol.LOGGER.warn("Couldn't still Manas abilities in the Garden and the Vault: {}", t.toString());
            }
        });
    }

    private static void hook(Class<?> events, String field, String iface) throws Exception {
        Field f = events.getField(field);
        Object ev = f.get(null);
        Class<?> listener = Class.forName("io.github.manasmods.manascore.skill.api.SkillEvents$" + iface);
        Object proxy = Proxy.newProxyInstance(listener.getClassLoader(), new Class<?>[]{listener}, (self, m, args) -> {
            if (m.getDeclaringClass() == Object.class) {
                return switch (m.getName()) { case "hashCode" -> System.identityHashCode(self); case "equals" -> self == args[0]; default -> "StillZones." + iface; };
            }
            if (args != null) for (Object a : args) if (a instanceof LivingEntity e) return stilled(e) ? DENY : PASS;
            return PASS;
        });
        Method register = null;
        for (Method m : ev.getClass().getMethods()) if (m.getName().equals("register") && m.getParameterCount() == 1) { register = m; break; }
        if (register == null) throw new NoSuchMethodException("register");
        register.setAccessible(true);
        register.invoke(ev, proxy);
    }

    /** Is this one's power held still where it stands? */
    public static boolean stilled(LivingEntity e) {
        if (e == null || e.level().isClientSide) return false;
        if (!Garden.inside(e.level()) && e.level().dimension() != Vault.DIM) return false;
        if (e instanceof ServerPlayer p) {
            if (Vault.chief(p)) return false;
            long now = p.level().getGameTime();
            Long last = TOLD.get(p.getUUID());
            if (last == null || now - last > 60) {
                TOLD.put(p.getUUID(), now);
                p.displayClientMessage(Component.literal(Garden.inside(p.level()) ? "Nothing of that works in the Garden. It's a place of peace." : "Nothing of that works in the Administrator's Vault.").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), true);
            }
        }
        return true;
    }
}
