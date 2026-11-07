package com.breachprotocol;

import com.breachprotocol.network.ModNetwork;
import com.breachprotocol.registry.*;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(BreachProtocol.MODID)
public class BreachProtocol {
    public static final String MODID = "breachprotocol";
    public static final Logger LOGGER = LoggerFactory.getLogger("Breach Protocol");

    public BreachProtocol(IEventBus modBus, ModContainer container) {
        ModSounds.SOUNDS.register(modBus);
        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        com.breachprotocol.registry.ModEffects.EFFECTS.register(modBus);
        ModEntities.ENTITIES.register(modBus);
        ModAttachments.ATTACHMENTS.register(modBus);
        ModTabs.TABS.register(modBus);

        modBus.addListener(ModEntities::registerAttributes);
        modBus.addListener(ModNetwork::register);
        modBus.addListener((net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent e) -> e.enqueueWork(() -> {
            boolean ok = com.breachprotocol.compat.UnleashedApi.onStasisCapture(com.breachprotocol.career.Career::onStasis);
            LOGGER.info(ok ? "Hooked into SCP Unleashed stasis pods." : "SCP Unleashed stasis hook not found; pods won't count as containment.");
        }));
        LOGGER.info("Breach Protocol (addon for SCP Unleashed) loaded. The Council is in session.");
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
