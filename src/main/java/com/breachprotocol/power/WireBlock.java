package com.breachprotocol.power;

import com.breachprotocol.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.capabilities.Capabilities;

import java.util.HashMap;
import java.util.Map;

/** Power wire. Connects to anything with an energy capability, in all six directions. */
public class WireBlock extends PowerBlock {
    public static final MapCodec<WireBlock> CODEC = simpleCodec(p -> new WireBlock(p, 1));
    private static final Map<BlockState, VoxelShape> SHAPES = new HashMap<>();
    private final int tier;

    public WireBlock(Properties props, int tier) {
        super(props, tier == 2 ? ModBlockEntities.HEAVY_WIRE : ModBlockEntities.COPPER_WIRE);
        this.tier = tier;
        BlockState s = stateDefinition.any();
        for (BooleanProperty p : PipeBlock.PROPERTY_BY_DIRECTION.values()) s = s.setValue(p, false);
        registerDefaultState(s);
    }

    public int tier() {
        return tier;
    }

    @Override
    protected MapCodec<? extends WireBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(PipeBlock.NORTH, PipeBlock.EAST, PipeBlock.SOUTH, PipeBlock.WEST, PipeBlock.UP, PipeBlock.DOWN);
    }

    private BlockState connect(BlockState state, Level level, BlockPos pos) {
        for (Direction d : Direction.values()) {
            boolean c = level.getBlockState(pos.relative(d)).getBlock() instanceof WireBlock
                    || level.getCapability(Capabilities.EnergyStorage.BLOCK, pos.relative(d), d.getOpposite()) != null;
            state = state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(d), c);
        }
        return state;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return connect(defaultBlockState(), ctx.getLevel(), ctx.getClickedPos());
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos npos) {
        if (level instanceof Level l) {
            boolean c = neighbor.getBlock() instanceof WireBlock
                    || l.getCapability(Capabilities.EnergyStorage.BLOCK, npos, dir.getOpposite()) != null;
            return state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(dir), c);
        }
        return state;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        // machines' block entities may appear after the wire; re-check once placed
        if (!level.isClientSide) {
            BlockState now = connect(state, level, pos);
            if (now != state) level.setBlock(pos, now, Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPES.computeIfAbsent(state, s -> {
            double a = tier == 2 ? 5 : 6, b = 16 - a;
            VoxelShape shape = Block.box(a, a, a, b, b, b);
            if (s.getValue(PipeBlock.NORTH)) shape = Shapes.or(shape, Block.box(a, a, 0, b, b, a));
            if (s.getValue(PipeBlock.SOUTH)) shape = Shapes.or(shape, Block.box(a, a, b, b, b, 16));
            if (s.getValue(PipeBlock.WEST)) shape = Shapes.or(shape, Block.box(0, a, a, a, b, b));
            if (s.getValue(PipeBlock.EAST)) shape = Shapes.or(shape, Block.box(b, a, a, 16, b, b));
            if (s.getValue(PipeBlock.DOWN)) shape = Shapes.or(shape, Block.box(a, 0, a, b, a, b));
            if (s.getValue(PipeBlock.UP)) shape = Shapes.or(shape, Block.box(a, b, a, b, 16, b));
            return shape;
        });
    }
}
