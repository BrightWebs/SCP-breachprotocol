package com.breachprotocol.item;

import com.breachprotocol.career.Career;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** The Foundation Phone. What it can do depends on who you are. */
public class PhoneItem extends Item {
    public PhoneItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) {
            com.breachprotocol.client.ClientPhone.open();
        } else if (player instanceof ServerPlayer sp) {
            Career.sync(sp);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Secure line to O5 Command.").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Messages, missions, calls and orders.").withStyle(ChatFormatting.DARK_GRAY));
    }
}
