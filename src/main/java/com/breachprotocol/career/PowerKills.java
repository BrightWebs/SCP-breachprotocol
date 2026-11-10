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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Kills made with powers are yours. Every power's blow is credited to the player who used it (the
 * damage carries them as its attacker, and the creature remembers them as the last to hurt it), so
 * the game, and other mods, count it as their kill: stats, loot, and Manas Reborn's EP.
 *
 * <p>With Manas Reborn, a creature killed by a power gives its killer all of its EP: whatever Manas
 * gives on its own, topped up to 100%. And the Administrator (and SCP-Ω) takes every skill it had:
 * all of them, at once, at the mastery it had them; one you already hold is left as it is.</p>
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

    // ---------------------------------------------------------------- Manas Reborn's skills

    private static Boolean skills;
    private static Method skillsFrom, learned, has, skillOf, temporary, toNBT, fromNBT, learn;

    private static boolean skills() {
        if (skills == null) {
            skills = false;
            try {
                Class<?> api = Class.forName("io.github.manasmods.manascore.skill.api.SkillAPI");
                Class<?> store = Class.forName("io.github.manasmods.manascore.skill.api.Skills");
                Class<?> inst = Class.forName("io.github.manasmods.manascore.skill.api.ManasSkillInstance");
                Class<?> skill = Class.forName("io.github.manasmods.manascore.skill.api.ManasSkill");
                Class<?> helper = Class.forName("io.github.manasmods.manas_reborn.ability.SkillHelper");
                skillsFrom = api.getMethod("getSkillsFrom", LivingEntity.class);
                learned = store.getMethod("getLearnedSkills");
                has = store.getMethod("getSkill", skill);
                skillOf = inst.getMethod("getSkill");
                temporary = inst.getMethod("isTemporarySkill");
                toNBT = inst.getMethod("toNBT");
                fromNBT = inst.getMethod("fromNBT", CompoundTag.class);
                learn = helper.getMethod("learnSkill", LivingEntity.class, inst);
                skills = true;
            } catch (Throwable t) {
                BreachProtocol.LOGGER.warn("Manas Reborn is here but its skills couldn't be reached: {}", t.toString());
            }
        }
        return skills;
    }

    /** The creature's own skills (not the temporary ones), copied as they stand. */
    public static List<CompoundTag> skillsOf(LivingEntity v) {
        List<CompoundTag> out = new ArrayList<>();
        if (!skills()) return out;
        try {
            Object st = skillsFrom.invoke(null, v);
            if (st == null) return out;
            for (Object i : new ArrayList<>((Collection<?>) learned.invoke(st))) {
                if ((boolean) temporary.invoke(i)) continue;
                out.add(((CompoundTag) toNBT.invoke(i)).copy());
            }
        } catch (Throwable t) {
            BreachProtocol.LOGGER.debug("Couldn't read the skills of {}: {}", v, t.toString());
        }
        return out;
    }

    /** Every one of them the killer doesn't already have. */
    private static void take(ServerPlayer p, List<CompoundTag> list) {
        if (list.isEmpty() || !skills()) return;
        int got = 0;
        try {
            Object mine = skillsFrom.invoke(null, p);
            if (mine == null) return;
            for (CompoundTag t : list) {
                try {
                    Object inst = fromNBT.invoke(null, t);
                    if (inst == null) continue;
                    if (((Optional<?>) has.invoke(mine, skillOf.invoke(inst))).isPresent()) continue; // already yours
                    if ((boolean) learn.invoke(null, p, inst)) got++;
                } catch (Throwable one) {
                    BreachProtocol.LOGGER.debug("Skipped a skill: {}", one.toString());
                }
            }
        } catch (Throwable t) {
            BreachProtocol.LOGGER.debug("Couldn't hand over skills: {}", t.toString());
        }
        if (got > 0) p.playNotifySound(net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, net.minecraft.sounds.SoundSource.PLAYERS, 0.7F, 0.6F);
    }

    /** Lend these skills (the ones not already held); returns the ids of those that were lent, to take back later. */
    public static List<String> lend(ServerPlayer p, List<CompoundTag> list) {
        List<String> lent = new ArrayList<>();
        if (list.isEmpty() || !skills()) return lent;
        try {
            Object mine = skillsFrom.invoke(null, p);
            if (mine == null) return lent;
            for (CompoundTag t : list) {
                try {
                    Object inst = fromNBT.invoke(null, t);
                    if (inst == null) continue;
                    Object sk = skillOf.invoke(inst);
                    if (((Optional<?>) has.invoke(mine, sk)).isPresent()) continue;
                    if ((boolean) learn.invoke(null, p, inst)) lent.add(String.valueOf(sk.getClass().getMethod("getRegistryName").invoke(sk)));
                } catch (Throwable one) { BreachProtocol.LOGGER.debug("Skipped lending a skill: {}", one.toString()); }
            }
        } catch (Throwable t) { BreachProtocol.LOGGER.debug("Couldn't lend skills: {}", t.toString()); }
        return lent;
    }

    /** Take back what was lent. */
    public static void reclaim(ServerPlayer p, List<String> ids) {
        if (ids.isEmpty() || !skills()) return;
        try {
            Object mine = skillsFrom.invoke(null, p);
            if (mine == null) return;
            Method forget = mine.getClass().getMethod("forgetSkill", net.minecraft.resources.ResourceLocation.class, net.minecraft.network.chat.MutableComponent.class);
            for (String id : ids) {
                net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.tryParse(id);
                if (rl != null) try { forget.invoke(mine, rl, null); } catch (Throwable one) { BreachProtocol.LOGGER.debug("Couldn't take back {}: {}", id, one.toString()); }
            }
        } catch (Throwable t) { BreachProtocol.LOGGER.debug("Couldn't take back skills: {}", t.toString()); }
    }

    private record Pending(ServerPlayer killer, double victimEP, double before, List<CompoundTag> skills) {}

    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    /** Before anything else hears of the death: whose kill it is, the creature's EP, and the killer's. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void dying(LivingDeathEvent event) {
        LivingEntity v = event.getEntity();
        if (v.level().isClientSide() || !manas()) return;
        ServerPlayer killer = powerKiller(v, event.getSource());
        if (killer == null) return;
        PENDING.put(v.getUUID(), new Pending(killer, ep(v), ep(killer), Vault.chief(killer) ? skillsOf(v) : List.of()));
    }

    /** After everyone (Manas Reborn included) has had their say: make it up to 100% of the creature's EP. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void died(LivingDeathEvent event) {
        Pending pd = PENDING.remove(event.getEntity().getUUID());
        if (pd == null || event.isCanceled() || !pd.killer().isAlive() && pd.killer().isRemoved()) return;
        double gained = ep(pd.killer()) - pd.before();
        double owed = pd.victimEP() - Math.max(0, gained);
        if (owed > 0.5) try { increaseEP.invoke(null, pd.killer(), owed); } catch (Throwable ignored) {}
        take(pd.killer(), pd.skills());
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
