package com.breachprotocol.mixin;

import com.breachprotocol.world.FlightState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * In True Form, a wall is something to climb: walk into it and it climbs, slowly, a hold at a time; hold crouch
 * and it clings where it is. (The game's own climbing, the way it treats a ladder, so it's the same on both sides.)
 */
@Mixin(LivingEntity.class)
public abstract class ClimbMixin {
    @Inject(method = "onClimbable", at = @At("HEAD"), cancellable = true)
    private void breachprotocol$climb(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof Player p && FlightState.clinging(p)) cir.setReturnValue(true);
    }
}
