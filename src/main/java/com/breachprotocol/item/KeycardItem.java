package com.breachprotocol.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class KeycardItem extends Item {
    private final int level;

    public KeycardItem(int level, Properties props) {
        super(props);
        this.level = level;
    }

    public int getLevel() {
        return level;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.breachprotocol.keycard.role." + level).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.breachprotocol.keycard.use").withStyle(ChatFormatting.DARK_GRAY));
    }
}
