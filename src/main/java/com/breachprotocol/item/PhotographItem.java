package com.breachprotocol.item;

import com.breachprotocol.career.Photos;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
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

/** A printed phone photo. Hand it over, frame it, or look at it - if you dare. */
public class PhotographItem extends Item {
    public PhotographItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack s = player.getItemInHand(hand);
        CompoundTag t = Photos.photoTag(s);
        if (!level.isClientSide && player instanceof ServerPlayer sp && t != null) {
            sp.displayClientMessage(Component.literal("Photo: " + t.getString("subjects")).withStyle(ChatFormatting.GRAY), false);
            Photos.view(sp, t.getBoolean("hazard"), t.getString("hazardEntity"));
        }
        return InteractionResultHolder.sidedSuccess(s, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag t = Photos.photoTag(stack);
        if (t == null || t.isEmpty()) return;
        tooltip.add(Component.literal("Taken by " + t.getString("author") + " near " + t.getInt("x") + ", " + t.getInt("z")).withStyle(ChatFormatting.GRAY));
        if (t.getBoolean("hazard")) {
            tooltip.add(Component.literal("⚠ COGNITOHAZARD - do not look").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
        } else {
            tooltip.add(Component.literal(t.getString("subjects")).withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
