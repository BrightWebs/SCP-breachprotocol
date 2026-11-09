package com.breachprotocol.mixin;

import com.breachprotocol.world.FlightState;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Flying flat out, you lie along your flight: the lying pose, and the low hitbox that comes with it. */
@Mixin(Player.class)
public abstract class PlayerPoseMixin {
    @Inject(method = "updatePlayerPose", at = @At("HEAD"), cancellable = true)
    private void breachprotocol$flat(CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (FlightState.lying(self)) {
            self.setPose(Pose.SWIMMING);
            ci.cancel();
        }
    }
}
