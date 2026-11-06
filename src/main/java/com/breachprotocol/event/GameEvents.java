package com.breachprotocol.event;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.entity.Scp049Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

@EventBusSubscriber(modid = BreachProtocol.MODID)
public final class GameEvents {
    private GameEvents() {}

    /** SCP-049's "cure": its victims get back up. */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) return;
        if (event.getSource().getEntity() instanceof Scp049Entity doctor
                && (victim instanceof Player || victim instanceof AbstractVillager)) {
            doctor.cure(victim);
        }
    }
}
