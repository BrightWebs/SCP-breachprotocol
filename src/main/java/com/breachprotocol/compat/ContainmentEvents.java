package com.breachprotocol.compat;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.career.Career;
import com.breachprotocol.registry.ModEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * SCP Unleashed creatures run their own AI, so stasis is enforced by switching their AI off while
 * the Contained effect lasts, and back on when it ends. The flag is remembered in persistent data
 * so a mob that was already NoAI (e.g. a display piece) is never woken up by us.
 */
@EventBusSubscriber(modid = BreachProtocol.MODID)
public final class ContainmentEvents {
    private static final String FROZE = "breachprotocol_froze_ai";

    private ContainmentEvents() {}

    private static boolean isContained(MobEffectEvent e) {
        return e.getEffectInstance() != null && e.getEffectInstance().getEffect().is(ModEffects.CONTAINED.getKey());
    }

    @SubscribeEvent
    public static void onAdded(MobEffectEvent.Added event) {
        if (event.getEntity().level().isClientSide || !isContained(event)) return;
        if (event.getEntity() instanceof Mob mob && Unleashed.isUnleashedAnomaly(mob) && !mob.isNoAi()) {
            mob.setNoAi(true);
            mob.setTarget(null);
            mob.getNavigation().stop();
            mob.getPersistentData().putBoolean(FROZE, true);
        }
    }

    @SubscribeEvent
    public static void onExpired(MobEffectEvent.Expired event) {
        if (isContained(event)) release(event.getEntity());
    }

    @SubscribeEvent
    public static void onRemove(MobEffectEvent.Remove event) {
        if (event.getEffect().is(ModEffects.CONTAINED.getKey())) release(event.getEntity());
    }

    private static void release(net.minecraft.world.entity.LivingEntity e) {
        if (e.level().isClientSide || !(e instanceof Mob mob)) return;
        if (mob.getPersistentData().getBoolean(FROZE)) {
            mob.getPersistentData().remove(FROZE);
            mob.setNoAi(false);
        }
    }

    /** Safety net: a mob we froze whose effect is somehow gone gets its AI back. */
    @SubscribeEvent
    public static void onTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Mob mob) || mob.level().isClientSide || mob.tickCount % 40 != 0) return;
        if (mob.getPersistentData().getBoolean(FROZE) && !Unleashed.isContained(mob)) release(mob);
        // The Ascendant's protection: no anomaly keeps a lock on its master.
        if (mob.getTarget() instanceof Player p && Unleashed.isAnomaly(mob) && Career.isAscended(p)) mob.setTarget(null);
    }
}
