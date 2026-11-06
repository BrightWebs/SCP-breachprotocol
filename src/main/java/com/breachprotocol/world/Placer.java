package com.breachprotocol.world;

import com.breachprotocol.BreachProtocol;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Places a block the way a player does: the block chooses its own placement state, then its
 * setPlacedBy runs, which is what builds the other parts of multi-block furniture and machines
 * (SCP Unleashed desks, tables, bunks, tanks, machines, beds, doors). Then it's turned to face the
 * requested way. Plain setBlock would leave half-built blocks.
 */
public final class Placer {
    private Placer() {}

    /** Places {@code block} at {@code pos} facing {@code facing}. Returns false if it doesn't fit there. */
    public static boolean place(ServerLevel l, BlockPos pos, Block block, Direction facing) {
        return place(l, pos, block, facing, Direction.UP);
    }

    /** Hung from the block above (ceiling lamps). */
    public static boolean ceiling(ServerLevel l, BlockPos pos, Block block) {
        return place(l, pos, block, null, Direction.DOWN);
    }

    /** Mounted on the wall behind it; {@code out} is the way it faces (signs, panels). */
    public static boolean wall(ServerLevel l, BlockPos pos, Block block, Direction out) {
        return place(l, pos, block, out, out);
    }

    /**
     * @param clickedFace the face of the neighbouring block that was "clicked": UP = standing on the
     *                    floor, DOWN = hanging from the ceiling, a horizontal face = on a wall.
     */
    public static boolean place(ServerLevel l, BlockPos pos, Block block, Direction facing, Direction clickedFace) {
        if (block == Blocks.AIR) return false;
        try {
            if (!l.getBlockState(pos).canBeReplaced()) l.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            ItemStack stack = new ItemStack(block.asItem());
            BlockPos support = pos.relative(clickedFace.getOpposite());
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(support).relative(clickedFace, 0.5), clickedFace, support, false);
            BlockPlaceContext ctx = new BlockPlaceContext(l, null, InteractionHand.MAIN_HAND, stack, hit);
            BlockState st = ctx.getClickedPos().equals(pos) ? block.getStateForPlacement(ctx) : null;
            if (st == null) {
                BreachProtocol.LOGGER.debug("No room to place {} at {}", block, pos);
                return false;
            }
            st = face(st, facing);
            l.setBlock(pos, st, Block.UPDATE_ALL);
            block.setPlacedBy(l, pos, st, null, stack);
            return true;
        } catch (Throwable t) {
            BreachProtocol.LOGGER.warn("Could not place {} at {}: {}", block, pos, t.toString());
            return false;
        }
    }

    /** Sets the block's horizontal facing, if it has one. */
    public static BlockState face(BlockState st, Direction facing) {
        if (facing == null || !facing.getAxis().isHorizontal()) return st;
        for (Property<?> p : st.getProperties()) {
            if (p instanceof DirectionProperty dp && (p.getName().equals("facing") || p.getName().equals("horizontal_facing")) && dp.getPossibleValues().contains(facing)) {
                return st.setValue(dp, facing);
            }
        }
        return st;
    }
}
