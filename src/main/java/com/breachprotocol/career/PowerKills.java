package com.breachprotocol.career;

import com.breachprotocol.BreachProtocol;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Kills made with powers are yours. Every power's blow is credited to the player who used it (the
 * damage carries them as its attacker, and the creature remembers them as the last to hurt it), so
 * the game, and other mods, count it as their kill: stats, loot, and Manas Reborn's EP.
 *
 * <p>With Manas Reborn, a creature killed by a power gives its killer all of its EP: whatever Manas
 * gives on its own, topped up to 100%.</p>
 */
@EventBusSubscriber(modid = BreachProtocol.MODID)
public final class PowerKills {
    private PowerKills() {}

    private static final String BY = "bp_pk", WHEN = "bp_pk_t";

    /** A power is about to strike this: it's the player's blow. */
    public static void credit(ServerPlayer p, LivingEntity e) {
        if (e == p) return;
        e.setLastHurtByPlayer(p);
        e.setLastHurtByMob(p);
        CompoundTag t = e.getPersistentData();
        t.putString(BY, p.getUUID().toString());
        t.putLong(WHEN, e.level().getGameTime());
    }

    /** Damage of the given kind, with the player as the one who dealt it. */
    public static DamageSource as(ServerPlayer p, ResourceKey<DamageType> type) {
        ServerLevel l = p.serverLevel();
        return new DamageSource(l.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(type), p);
    }

    /** Ordinary power damage: magic, the player's. */
    public static DamageSource magic(ServerPlayer p) { return p.serverLevel().damageSources().indirectMagic(p, p); }

    /** Strike: credit, then hurt. */
    public static boolean hurt(ServerPlayer p, LivingEntity e, DamageSource src, float amount) {
        credit(p, e);
        return e.hurt(src, amount);
    }

    // ================================================================== Manas Reborn's EP

    private static Boolean manas;
    private static Method maxEP, increaseEP;

    private static boolean manas() {
        if (manas == null) {
            manas = false;
            if (ModList.get().isLoaded("manas_reborn")) try {
                Class<?> h = Class.forName("io.github.manasmods.manas_reborn.util.EnergyHelper");
                maxEP = h.getMethod("getMaxEP", LivingEntity.class);
                increaseEP = h.getMethod("increaseMaxEP", LivingEntity.class, double.class);
                manas = true;
            } catch (Throwable t) {
                BreachProtocol.LOGGER.warn("Manas Reborn is here but its EP couldn't be reached: {}", t.toString());
            }
        }
        return manas;
    }

    private static double ep(LivingEntity e) {
        try { return ((Number) maxEP.invoke(null, e)).doubleValue(); } catch (Throwable t) { return 0; }
    }

    private record Pending(ServerPlayer killer, double victimEP, double before) {}

    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    /** Before anything else hears of the death: whose kill it is, the creature's EP, and the killer's. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void dying(LivingDeathEvent event) {
        LivingEntity v = event.getEntity();
        if (v.level().isClientSide() || !manas()) return;
        ServerPlayer killer = powerKiller(v, event.getSource());
        if (killer == null) return;
        PENDING.put(v.getUUID(), new Pending(killer, ep(v), ep(killer)));
    }

    /** After everyone (Manas Reborn included) has had their say: make it up to 100% of the creature's EP. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void died(LivingDeathEvent event) {
        Pending pd = PENDING.remove(event.getEntity().getUUID());
        if (pd == null || event.isCanceled() || !pd.killer().isAlive() && pd.killer().isRemoved()) return;
        double gained = ep(pd.killer()) - pd.before();
        double owed = pd.victimEP() - Math.max(0, gained);
        if (owed > 0.5) try { increaseEP.invoke(null, pd.killer(), owed); } catch (Throwable ignored) {}
    }

    /** The player whose power (or Rage, or ram) brought this down, if one did in the last few seconds. */
    private static ServerPlayer powerKiller(LivingEntity v, DamageSource src) {
        CompoundTag t = v.getPersistentData();
        if (!t.contains(BY) || v.level().getGameTime() - t.getLong(WHEN) > 200) return null;
        try {
            ServerPlayer p = v.getServer().getPlayerList().getPlayer(UUID.fromString(t.getString(BY)));
            return p != null && p != v ? p : null;
        } catch (IllegalArgumentException e) { return null; }
    }

    static ResourceKey<DamageType> KILL = DamageTypes.GENERIC_KILL;
}
