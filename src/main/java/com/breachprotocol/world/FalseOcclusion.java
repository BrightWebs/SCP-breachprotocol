package com.breachprotocol.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Which blocks claim to hide their neighbours' faces without actually covering them (see BlockStateBaseMixin). */
public final class FalseOcclusion {
    private FalseOcclusion() {}

    private static final Map<Block, Boolean> UNLEASHED = new ConcurrentHashMap<>();

    /** SCP Unleashed blocks that aren't full cubes: furniture, props, machines. Their walls and floors are untouched. */
    public static boolean applies(BlockState state, BlockGetter level, BlockPos pos) {
        if (!UNLEASHED.computeIfAbsent(state.getBlock(), b -> BuiltInRegistries.BLOCK.getKey(b).getNamespace().equals("scp_unleashed"))) return false;
        return state.canOcclude() && !state.isSolidRender(level, pos);
    }
}
