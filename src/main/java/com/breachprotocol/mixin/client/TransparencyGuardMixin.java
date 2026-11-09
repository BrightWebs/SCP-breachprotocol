package com.breachprotocol.mixin.client;

import com.breachprotocol.BreachProtocol;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;

/**
 * A guard for a start-up crash in another library: with "Fabulous!" graphics, Minecraft sets up its
 * transparency shaders during the first resource load, and Veil (a rendering library several mods use)
 * hooks that moment before its own renderer exists, so the game crashes at the loading screen. If
 * that set-up fails, graphics drop to Fancy (what Minecraft itself does when Fabulous isn't supported)
 * and the game carries on loading.
 */
@Mixin(LevelRenderer.class)
public abstract class TransparencyGuardMixin {
    @WrapMethod(method = "initTransparency", require = 0)
    private void breachprotocol$fancyInstead(Operation<Void> original) {
        try {
            original.call();
        } catch (RuntimeException e) {
            BreachProtocol.LOGGER.warn("Fabulous graphics couldn't start ({}); switching to Fancy.", e.toString());
            Minecraft mc = Minecraft.getInstance();
            mc.options.graphicsMode().set(GraphicsStatus.FANCY);
            mc.options.save();
        }
    }
}
