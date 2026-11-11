package com.breachprotocol.mixin;

import com.breachprotocol.career.GatePass;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * SCP Unleashed's Gate strike: its masters (the Administrator, SCP-Ω) are spared by the Gate itself, as
 * anything untouchable is, without being made untouchable (so everything else can still fight them).
 */
@Pseudo
@Mixin(targets = "net.rai.scp_unleashed.scp001gate.Scp001GateStrike", remap = false)
public abstract class GateStrikeMixin {
    @Inject(method = "spared(Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void breachprotocol$masters(Entity body, CallbackInfoReturnable<Boolean> cir) {
        if (body instanceof ServerPlayer sp && GatePass.isMaster(sp)) cir.setReturnValue(true);
    }
}
