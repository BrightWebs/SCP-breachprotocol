package com.breachprotocol.item;

import com.breachprotocol.power.PowerBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

/** Reads any energy block - ours or another mod's. */
public class MultimeterItem extends Item {
    public MultimeterItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (ctx.getLevel().isClientSide || ctx.getPlayer() == null) return InteractionResult.SUCCESS;
        var be = ctx.getLevel().getBlockEntity(ctx.getClickedPos());
        if (be instanceof PowerBlock.TickingPower tp) {
            ctx.getPlayer().displayClientMessage(tp.status(), true);
            return InteractionResult.SUCCESS;
        }
        IEnergyStorage e = ctx.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, ctx.getClickedPos(), ctx.getClickedFace());
        ctx.getPlayer().displayClientMessage(e == null
                ? Component.literal("No energy storage here.").withStyle(ChatFormatting.GRAY)
                : Component.literal("Energy: " + PowerBlock.fe(e.getEnergyStored()) + " / " + PowerBlock.fe(e.getMaxEnergyStored())).withStyle(ChatFormatting.AQUA), true);
        return InteractionResult.SUCCESS;
    }
}
