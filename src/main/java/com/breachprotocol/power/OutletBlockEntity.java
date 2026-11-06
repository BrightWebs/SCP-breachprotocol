package com.breachprotocol.power;

import com.breachprotocol.registry.ModBlockEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class OutletBlockEntity extends PowerBlockEntity implements PowerBlock.TickingPower {
    public static final int DRAW = 8;
    /** Ticks of grace before the lights go out, so momentary dips don't trip a breach. */
    private int grace;

    public OutletBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.OUTLET.get(), pos, state, 4000, 512, 0);
    }

    @Override
    public void serverTick() {
        boolean fed = energy.consume(DRAW) == DRAW;
        grace = fed ? 20 : Math.max(0, grace - 1);
        boolean on = grace > 0;
        BlockState st = getBlockState();
        if (st.getValue(OutletBlock.POWERED) != on) {
            level.setBlock(worldPosition, st.setValue(OutletBlock.POWERED, on), Block.UPDATE_ALL);
            level.updateNeighborsAt(worldPosition, st.getBlock());
        }
    }

    @Override
    public Component status() {
        boolean on = getBlockState().getValue(OutletBlock.POWERED);
        return Component.literal("Power Outlet: " + (on ? "LIVE" : "NO POWER") + " (" + PowerBlock.fe(energy.getEnergyStored()) + " buffered, draws " + DRAW + " FE/t)")
                .withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED);
    }
}
