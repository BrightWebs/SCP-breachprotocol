package com.breachprotocol.mixin.client;

import com.breachprotocol.client.SuperFlight;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The flier's body: no walking legs in mid-air, but a pose (see SuperFlight.pose). */
@Mixin(HumanoidModel.class)
public abstract class FlightModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void breachprotocol$fly(LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (entity instanceof Player p) SuperFlight.pose((HumanoidModel<?>) (Object) this, p, ageInTicks, headPitch);
    }
}
