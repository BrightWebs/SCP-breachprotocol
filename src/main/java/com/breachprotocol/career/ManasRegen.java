package com.breachprotocol.career;

import com.breachprotocol.BreachProtocol;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.lang.reflect.Method;

/**
 * With Manas Reborn: the Administrator (and SCP-Ω) never runs dry. Aura, magicules and spiritual
 * health all come back by a share of their maximum, a fifth of it every second, so however large
 * the pools grow, they're full again in about five seconds.
 */
@EventBusSubscriber(modid = BreachProtocol.MODID)
public final class ManasRegen {
    private ManasRegen() {}

    private static final double PER_HALF_SECOND = 0.10;

    private static boolean looked, ok;
    private static Method existence, maxAura, maxMana, maxShp, getAura, setAura, getMana, setMana, getShp, setShp, dirty;

    private static boolean look() {
        if (looked) return ok;
        looked = true;
        if (!ModList.get().isLoaded("manas_reborn")) return false;
        try {
            Class<?> st = Class.forName("io.github.manasmods.manas_reborn.storage.ManasStorages");
            Class<?> ex = Class.forName("io.github.manasmods.manas_reborn.storage.ep.IExistence");
            Class<?> en = Class.forName("io.github.manasmods.manas_reborn.util.EnergyHelper");
            Class<?> ef = Class.forName("io.github.manasmods.manas_reborn.storage.effect.EffectStorage");
            existence = st.getMethod("getExistenceFrom", LivingEntity.class);
            maxAura = en.getMethod("getMaxAura", LivingEntity.class);
            maxMana = en.getMethod("getMaxMana", LivingEntity.class);
            maxShp = ef.getMethod("getRendingMaxSHP", LivingEntity.class);
            getAura = ex.getMethod("getAura"); setAura = ex.getMethod("setAura", double.class);
            getMana = ex.getMethod("getMana"); setMana = ex.getMethod("setMana", double.class);
            getShp = ex.getMethod("getSpiritualHealth"); setShp = ex.getMethod("setSpiritualHealth", double.class);
            dirty = ex.getMethod("markEnergyDirty");
            ok = true;
        } catch (Throwable t) {
            BreachProtocol.LOGGER.warn("Manas Reborn is here but its energy couldn't be reached: {}", t.toString());
        }
        return ok;
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 10 != 5 || !look()) return;
        for (ServerPlayer p : event.getServer().getPlayerList().getPlayers()) {
            if (!p.isAlive() || !Vault.chief(p)) continue;
            try {
                Object x = existence.invoke(null, p);
                if (x == null) continue;
                boolean changed = fill(x, getAura, setAura, (double) maxAura.invoke(null, p));
                changed |= fill(x, getMana, setMana, (double) maxMana.invoke(null, p));
                changed |= fill(x, getShp, setShp, (double) maxShp.invoke(null, p));
                if (changed) dirty.invoke(x);
            } catch (Throwable t) {
                ok = false;
                BreachProtocol.LOGGER.warn("Manas Reborn energy regeneration stopped: {}", t.toString());
                return;
            }
        }
    }

    private static boolean fill(Object x, Method get, Method set, double max) throws Exception {
        if (max <= 0) return false;
        double now = (double) get.invoke(x);
        if (now >= max) return false;
        set.invoke(x, Math.min(max, now + Math.max(1, max * PER_HALF_SECOND)));
        return true;
    }
}
