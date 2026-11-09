package com.breachprotocol.mixin;

import com.breachprotocol.career.Flight;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Flying at SCP-Ω's speeds trips the server's "moved too quickly" check, which drags you back where
 * it last saw you, over and over. For SCP-Ω in flight the server trusts the client on speed, the way
 * it always trusts the owner of a single-player world.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class FlightMoveMixin {
    @Shadow public ServerPlayer player;

    @Inject(method = "isSingleplayerOwner", at = @At("HEAD"), cancellable = true)
    private void breachprotocol$fast(CallbackInfoReturnable<Boolean> cir) {
        if (Flight.trusted(player)) cir.setReturnValue(true);
    }
}
