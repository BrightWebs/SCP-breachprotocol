package com.breachprotocol.block;

import com.breachprotocol.item.KeycardItem;
import com.breachprotocol.registry.ModSounds;
import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Wall-mounted reader. Swipe a keycard of the required clearance (or higher) and it emits a
 * 3 second redstone pulse, so it drives iron doors, pistons, sliding walls, anything.
 * Sneak + use with a keycard to program it to that card's level (you can only program upwards).
 */
public class KeycardReaderBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<KeycardReaderBlock> CODEC = simpleCodec(KeycardReaderBlock::new);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final IntegerProperty LEVEL = IntegerProperty.create("level", 1, 5);

    private static final VoxelShape NORTH = Block.box(4, 3, 14, 12, 13, 16);
    private static final VoxelShape SOUTH = Block.box(4, 3, 0, 12, 13, 2);
    private static final VoxelShape EAST = Block.box(0, 3, 4, 2, 13, 12);
    private static final VoxelShape WEST = Block.box(14, 3, 4, 16, 13, 12);
    private static final int PULSE_TICKS = 60;

    public KeycardReaderBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(POWERED, false).setValue(LEVEL, 1));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED, LEVEL);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction face = ctx.getClickedFace();
        Direction facing = face.getAxis().isHorizontal() ? face : ctx.getHorizontalDirection().getOpposite();
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> SOUTH;
            case EAST -> EAST;
            case WEST -> WEST;
            default -> NORTH;
        };
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof KeycardItem card)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }
        int required = state.getValue(LEVEL);
        if (player.isShiftKeyDown()) {
            if (card.getLevel() >= required) {
                level.setBlock(pos, state.setValue(LEVEL, card.getLevel()), Block.UPDATE_ALL);
                player.displayClientMessage(Component.translatable("message.breachprotocol.reader_programmed", card.getLevel()).withStyle(ChatFormatting.AQUA), true);
                level.playSound(null, pos, ModSounds.KEYCARD_ACCEPT.get(), SoundSource.BLOCKS, 0.8F, 0.7F);
            } else {
                deny(level, pos, player, required);
            }
            return ItemInteractionResult.SUCCESS;
        }
        if (state.getValue(POWERED)) {
            return ItemInteractionResult.SUCCESS;
        }
        if (card.getLevel() >= required) {
            BlockState on = state.setValue(POWERED, true);
            level.setBlock(pos, on, Block.UPDATE_ALL);
            updateNeighbours(on, level, pos);
            level.scheduleTick(pos, this, PULSE_TICKS);
            level.playSound(null, pos, ModSounds.KEYCARD_ACCEPT.get(), SoundSource.BLOCKS, 0.8F, 1.0F);
            player.displayClientMessage(Component.translatable("message.breachprotocol.access_granted").withStyle(ChatFormatting.GREEN), true);
        } else {
            deny(level, pos, player, required);
        }
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            player.displayClientMessage(Component.translatable("message.breachprotocol.reader_info", state.getValue(LEVEL)).withStyle(ChatFormatting.GRAY), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static void deny(Level level, BlockPos pos, Player player, int required) {
        level.playSound(null, pos, ModSounds.KEYCARD_DENY.get(), SoundSource.BLOCKS, 0.8F, 1.0F);
        player.displayClientMessage(Component.translatable("message.breachprotocol.access_denied", required).withStyle(ChatFormatting.RED), true);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(POWERED)) {
            BlockState off = state.setValue(POWERED, false);
            level.setBlock(pos, off, Block.UPDATE_ALL);
            updateNeighbours(off, level, pos);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!moved && !state.is(newState.getBlock()) && state.getValue(POWERED)) {
            updateNeighbours(state, level, pos);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    private void updateNeighbours(BlockState state, Level level, BlockPos pos) {
        level.updateNeighborsAt(pos, this);
        level.updateNeighborsAt(pos.relative(state.getValue(FACING).getOpposite()), this);
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(POWERED) ? 15 : 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(POWERED) && state.getValue(FACING) == direction ? 15 : 0;
    }
}
