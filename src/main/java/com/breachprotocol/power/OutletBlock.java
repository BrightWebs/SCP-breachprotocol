package com.breachprotocol.power;

import com.breachprotocol.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Facility Power Outlet: draws 8 FE/t from the grid and, while it has power, emits a full
 * redstone signal on every side. Put it next to a Containment Field Generator, keycard door,
 * lamps... When the grid runs dry, everything it feeds goes dark. Including the containment.
 */
public class OutletBlock extends PowerBlock {
    public static final MapCodec<OutletBlock> CODEC = simpleCodec(OutletBlock::new);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public OutletBlock(Properties props) {
        super(props, ModBlockEntities.OUTLET);
        registerDefaultState(stateDefinition.any().setValue(POWERED, false));
    }

    @Override
    protected MapCodec<? extends OutletBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(POWERED);
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction dir) {
        return state.getValue(POWERED) ? 15 : 0;
    }
}
