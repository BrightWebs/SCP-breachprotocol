package com.breachprotocol.mixin;

import com.breachprotocol.career.Clearance;
import com.breachprotocol.network.ClientCareerState;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * SCP Unleashed reads a document's clearance only off the keycards you carry. Your clearance counts too: the one
 * on your phone (a fused card, your rank's level), and for the Administrator, O5 and SCP-Ω, Omni: nothing redacted.
 */
@Pseudo
@Mixin(targets = "net.rai.scp_unleashed.archive.document.DossierClearance", remap = false)
public abstract class DossierClearanceMixin {
    @Inject(method = "highest(Lnet/minecraft/world/entity/player/Player;)I", at = @At("RETURN"), cancellable = true, require = 0, remap = false)
    private static void breachprotocol$yourClearance(Player reader, CallbackInfoReturnable<Integer> cir) {
        if (reader == null) return;
        int mine;
        try {
            // on the reader's own screen, what the server last told it; on the server, worked out
            mine = reader.level().isClientSide() ? (reader.isLocalPlayer() ? ClientCareerState.tag.getInt("clr") : 0) : Clearance.effective(reader);
        } catch (Throwable t) {
            return;
        }
        if (mine > cir.getReturnValue()) cir.setReturnValue(mine);
    }
}
