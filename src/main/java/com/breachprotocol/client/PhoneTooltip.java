package com.breachprotocol.client;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** A phone says whose it is: every phone has an owner, its ID. */
@EventBusSubscriber(modid = BreachProtocol.MODID, value = Dist.CLIENT)
public final class PhoneTooltip {
    private PhoneTooltip() {}

    @SubscribeEvent
    public static void tooltip(ItemTooltipEvent event) {
        if (!event.getItemStack().is(ModItems.PHONE.get())) return;
        CompoundTag t = event.getItemStack().getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!t.hasUUID("bp_owner")) {
            event.getToolTip().add(Component.literal("Unassigned: the first to carry it owns it").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        String id = t.getUUID("bp_owner").toString();
        event.getToolTip().add(Component.literal("Personal phone · " + (t.getString("bp_owner_n").isEmpty() ? "assigned" : t.getString("bp_owner_n"))).withStyle(ChatFormatting.AQUA));
        event.getToolTip().add(Component.literal("ID " + id.substring(0, 8).toUpperCase(java.util.Locale.ROOT) + "-" + t.getInt("bp_gen")).withStyle(ChatFormatting.DARK_GRAY));
    }
}
