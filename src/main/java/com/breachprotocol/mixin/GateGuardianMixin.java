package com.breachprotocol.mixin;

import com.breachprotocol.career.GatePass;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * SCP Unleashed's Gate Guardian, with its masters (the Administrator, SCP-Ω): it doesn't warn them off, and it
 * never brings its blades down beside them (on whatever is with them, or near them), so it never swings at them.
 */
@Pseudo
@Mixin(targets = "net.rai.scp_unleashed.scp001gate.Scp001GateGuardian", remap = false)
public abstract class GateGuardianMixin {
    @Inject(method = "strikeTick(Lnet/minecraft/server/level/ServerLevel;Ljava/util/List;)V", at = @At("HEAD"), require = 0, remap = false)
    private void breachprotocol$spareMasters(ServerLevel world, List<Entity> doomed, CallbackInfo ci) {
        if (doomed != null && !doomed.isEmpty()) {
            try { doomed.removeIf(GatePass::besideMaster); } catch (UnsupportedOperationException ignored) {}
        }
    }

    @Inject(method = "warn(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/server/level/ServerPlayer;D)V", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void breachprotocol$noWarning(ServerLevel world, ServerPlayer user, double distance, CallbackInfo ci) {
        if (GatePass.isMaster(user)) ci.cancel();
    }
}
