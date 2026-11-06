package com.breachprotocol.compat;

import com.breachprotocol.BreachProtocol;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Calls into SCP Unleashed's public methods at runtime. Nothing from SCP Unleashed is compiled
 * into or shipped with Breach Protocol; if a method isn't there (a different SCP Unleashed
 * version), the call reports failure instead of crashing.
 */
public final class UnleashedApi {
    private UnleashedApi() {}

    private static final String PKG = "net.rai.scp_unleashed.";
    private static final Map<String, Object> CACHE = new HashMap<>();
    private static final Object MISSING = new Object();

    private static Class<?> cls(String name) {
        Object c = CACHE.computeIfAbsent("c:" + name, k -> {
            try {
                return Class.forName(PKG + name);
            } catch (Throwable t) {
                BreachProtocol.LOGGER.warn("SCP Unleashed class {} not found - feature disabled", name);
                return MISSING;
            }
        });
        return c == MISSING ? null : (Class<?>) c;
    }

    private static Method method(String clsName, String name, Class<?>... args) {
        String key = "m:" + clsName + "#" + name + args.length;
        Object m = CACHE.computeIfAbsent(key, k -> {
            Class<?> c = cls(clsName);
            if (c == null) return MISSING;
            try {
                Method mm = c.getMethod(name, args);
                mm.setAccessible(true);
                return mm;
            } catch (Throwable t) {
                BreachProtocol.LOGGER.warn("SCP Unleashed method {}.{} not found - feature disabled", clsName, name);
                return MISSING;
            }
        });
        return m == MISSING ? null : (Method) m;
    }

    private static Object call(Method m, Object target, Object... args) {
        if (m == null) return null;
        try {
            return m.invoke(target, args);
        } catch (Throwable t) {
            BreachProtocol.LOGGER.warn("SCP Unleashed call {} failed: {}", m.getName(), t.toString());
            return null;
        }
    }

    // ------------------------------------------------------------------ SCP-096

    public static boolean isShyGuy(Entity e) {
        Class<?> c = cls("scp096.Scp096ShyGuy");
        return c != null && c.isInstance(e);
    }

    /** Sends SCP-096 into a rage against {@code target}. Returns false if it couldn't. */
    public static boolean enrage096(Entity shyGuy, LivingEntity target) {
        if (!isShyGuy(shyGuy) || !(shyGuy.level() instanceof ServerLevel sl)) return false;
        Method m = method("scp096.Scp096ShyGuy", "forceRage", ServerLevel.class, LivingEntity.class);
        if (m == null) return false;
        call(m, shyGuy, sl, target);
        return true;
    }

    public static boolean isBagged(Entity shyGuy) {
        Object r = call(method("scp096.Scp096ShyGuy", "isBagged"), shyGuy);
        return r instanceof Boolean b && b;
    }

    // ------------------------------------------------------------------ MTF

    public static final String[] MTF_TIERS = {"Faint", "Wavering", "Volatile", "Severe", "Abyssal"};

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object tier(String name) {
        Class<?> c = cls("mtf.MtfTier");
        if (c == null || !c.isEnum()) return null;
        try {
            return Enum.valueOf((Class) c, name);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Calls a Mobile Task Force squad by helicopter. Returns their outcome name, or null. */
    public static String callMtf(ServerPlayer p, String tierName) {
        Object tier = tier(tierName);
        Class<?> tierCls = cls("mtf.MtfTier");
        if (tier == null || tierCls == null) return null;
        Object out = call(method("mtf.MtfMissions", "call", ServerPlayer.class, tierCls), null, p, tier);
        return out == null ? null : out.toString();
    }

    public static long mtfCooldown(ServerPlayer p) {
        Object r = call(method("mtf.MtfMissions", "cooldownLeft", ServerPlayer.class), null, p);
        return r instanceof Long l ? l : 0L;
    }

    public static boolean mtfReport(ServerPlayer p) {
        Method m = method("mtf.MtfMissions", "report", ServerPlayer.class);
        call(m, null, p);
        return m != null;
    }

    public static boolean mtfExtract(ServerPlayer p) {
        Object r = call(method("mtf.MtfMissions", "requestExtraction", ServerPlayer.class), null, p);
        return r instanceof Boolean b && b;
    }

    // ------------------------------------------------------------------ orbital array

    private static Object orbitalArray(MinecraftServer server) {
        return call(method("orbital.OrbitalArray", "get", MinecraftServer.class), null, server);
    }

    /** {parts installed, 1 if complete, shots ready}, or null when unavailable. */
    public static int[] orbitalStatus(ServerPlayer p) {
        Object arr = orbitalArray(p.server);
        if (arr == null) return null;
        Object count = call(method("orbital.OrbitalArray", "count", java.util.UUID.class), arr, p.getUUID());
        Object complete = call(method("orbital.OrbitalArray", "complete", java.util.UUID.class), arr, p.getUUID());
        Object shots = call(method("orbital.OrbitalArray", "shots", java.util.UUID.class), arr, p.getUUID());
        return new int[]{count instanceof Integer i ? i : 0, complete instanceof Boolean b && b ? 1 : 0, shots instanceof Integer s ? s : 0};
    }

    /** Fires the player's satellite at {@code target}. Only when their array is complete and charged. */
    public static boolean orbitalStrike(ServerPlayer p, BlockPos target) {
        int[] st = orbitalStatus(p);
        if (st == null || st[1] == 0 || st[2] <= 0) return false;
        Method m = method("orbital.OrbitalFiring", "call", ServerLevel.class, BlockPos.class, ServerPlayer.class);
        if (m == null) return false;
        Object strike = call(m, null, p.serverLevel(), target, p);
        if (strike == null) return false;
        call(method("orbital.OrbitalArray", "fired", java.util.UUID.class, long.class), orbitalArray(p.server), p.getUUID(), p.level().getGameTime());
        return true;
    }

    // ------------------------------------------------------------------ network hubs

    public static int networkHubCount(MinecraftServer server) {
        Object ledger = call(method("nethub.NetHubLedger", "get", MinecraftServer.class), null, server);
        Object size = ledger == null ? null : call(method("nethub.NetHubLedger", "size"), ledger);
        return size instanceof Integer i ? i : -1;
    }

    // ------------------------------------------------------------------ D-Class

    public static boolean isDClass(Entity e) {
        Class<?> c = cls("dclass.DClass");
        return c != null && c.isInstance(e);
    }

    public static String dclassDesignation(Entity e) {
        Object r = call(method("dclass.DClass", "designation"), e);
        return r instanceof String s && !s.isEmpty() ? s : e.getName().getString();
    }
}
