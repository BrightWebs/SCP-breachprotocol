package com.breachprotocol.mixin;

import com.breachprotocol.career.Dominion;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * SCP Unleashed asks one question before any anomaly goes for someone: Targeting.isTargetable. The anomalies
 * that pick their prey themselves (SCP-049's surgery, its hold, the hunters) all ask it. The Administrator and
 * SCP-Ω, left be (their Temperament), aren't anyone's prey.
 */
@Pseudo
@Mixin(targets = "net.rai.scp_unleashed.anomaly.Targeting", remap = false)
public abstract class TargetingMixin {
    @Inject(method = "isTargetable(Lnet/minecraft/world/entity/Entity;)Z", at = @At("RETURN"), cancellable = true, require = 0, remap = false)
    private static void breachprotocol$notPrey(Entity subject, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue() || !(subject instanceof Player p) || p.level().isClientSide()) return;
        try { if (Dominion.spared(p)) cir.setReturnValue(false); } catch (Throwable ignored) {}
    }
}
