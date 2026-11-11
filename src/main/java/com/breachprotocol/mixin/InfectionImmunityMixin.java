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
 * SCP Unleashed's infections (SCP-008, -020, -049's pestilence, -098, -129, -253, -323, -427, -439, -538, -610 ...)
 * all ask InfectionImmunity first. The Administrator and SCP-Ω, left be, can't be infected.
 */
@Pseudo
@Mixin(targets = "net.rai.scp_unleashed.anomaly.InfectionImmunity", remap = false)
public abstract class InfectionImmunityMixin {
    @Inject(method = "isImmune(Lnet/minecraft/world/entity/Entity;)Z", at = @At("RETURN"), cancellable = true, require = 0, remap = false)
    private static void breachprotocol$immune(Entity target, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue() || !(target instanceof Player p) || p.level().isClientSide()) return;
        try { if (Dominion.spared(p)) cir.setReturnValue(true); } catch (Throwable ignored) {}
    }
}
