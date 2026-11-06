package com.breachprotocol.compat;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.career.Photos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.PathfinderMob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = BreachProtocol.MODID)
public final class AddonEvents {
    private AddonEvents() {}

    /** Every SCP Unleashed D-Class can take phone orders. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide) return;
        if (event.getEntity() instanceof PathfinderMob mob && UnleashedApi.isDClass(mob)) {
            mob.goalSelector.addGoal(0, new OrderGoal(mob));
        }
    }

    /** Framed cognitohazard photographs. */
    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer p && p.tickCount % 10 == 0 && !p.isSpectator()) Photos.checkFrames(p);
    }
}
