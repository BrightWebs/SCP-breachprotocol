package com.breachprotocol.registry;

import com.breachprotocol.BreachProtocol;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, BreachProtocol.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.breachprotocol"))
            .icon(() -> new ItemStack(ModItems.PHONE.get()))
            .displayItems((params, output) -> ModItems.ITEMS.getEntries().forEach(item -> output.accept(item.get())))
            .build());

    private ModTabs() {}
}
