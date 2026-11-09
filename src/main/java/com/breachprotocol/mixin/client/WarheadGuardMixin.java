package com.breachprotocol.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/**
 * A guard for a crash in SCP Unleashed itself (0.0.5.3): its nuclear-warhead screen effect asks its
 * client config whether it's enabled on the very first client tick, before NeoForge has loaded that
 * config, and the game crashes at the loading screen. Asked too early, the answer is now "not yet"
 * (no effect that tick) instead of a crash; once the config is loaded it answers as it always did.
 * Nothing else of SCP Unleashed's is touched, and if the class isn't there this does nothing.
 */
@Pseudo
@Mixin(targets = "net.rai.scp_unleashed.warhead.client.WarheadEffects")
public abstract class WarheadGuardMixin {
    @WrapMethod(method = "enabled", require = 0)
    private static boolean breachprotocol$notYet(Operation<Boolean> original) {
        try {
            return original.call();
        } catch (IllegalStateException early) {
            return false;
        }
    }
}
