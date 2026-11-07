package com.breachprotocol.block;

import com.breachprotocol.career.Career;
import com.breachprotocol.career.Desk;
import com.breachprotocol.career.Rank;
import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Administrator's terminal: a self-powered workstation (it draws on the site's creative supply,
 * so it is always on). Right-click opens the Foundation desktop: every phone app plus Recruitment
 * and Site Security. Only the Administrator can log in.
 */
public class AdminTerminalBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<AdminTerminalBlock> CODEC = simpleCodec(AdminTerminalBlock::new);

    private static final VoxelShape NORTH = Shapes.or(Block.box(2, 0, 1, 14, 1, 5), Block.box(3, 0, 6, 13, 1, 12), Block.box(7, 1, 9, 9, 4, 11),
            Block.box(1, 4, 7, 15, 14, 10), Block.box(3, 5, 10, 13, 13, 12));
    private static final VoxelShape SOUTH = rotate(NORTH, 2), EAST = rotate(NORTH, 1), WEST = rotate(NORTH, 3);

    public AdminTerminalBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() { return CODEC; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING); }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        // the screen faces whoever placed it
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    /** Rotates a north-facing shape clockwise {@code turns} quarter turns. */
    private static VoxelShape rotate(VoxelShape s, int turns) {
        VoxelShape out = s;
        for (int i = 0; i < turns; i++) {
            VoxelShape[] acc = {Shapes.empty()};
            out.forAllBoxes((x0, y0, z0, x1, y1, z1) -> acc[0] = Shapes.or(acc[0], Shapes.box(1 - z1, y0, x0, 1 - z0, y1, x1)));
            out = acc[0];
        }
        return out;
    }

    @Override
    protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return switch (s.getValue(FACING)) {
            case SOUTH -> SOUTH;
            case EAST -> EAST;
            case WEST -> WEST;
            default -> NORTH;
        };
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState s, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer p) {
            if (Career.rank(p) != Rank.ADMINISTRATOR && !Career.isAscended(p)) {
                p.displayClientMessage(Component.literal("ACCESS DENIED · Administrator credentials required.").withStyle(ChatFormatting.RED), true);
                level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 0.6F, 0.5F);
            } else {
                level.playSound(null, pos, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 0.4F, 1.6F);
                Desk.open(p);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
