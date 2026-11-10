package io.github.manasmods.manas_reborn.ability.mimicry;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/** Compile-time stand-in (never packaged). */
public class MimicBlockHelper {
    public static DetectedForm detectParts(Level l, BlockPos pos, BlockState st) { throw new AbstractMethodError(); }
    public static int getFormBlocksTall(List<MimicBlockPart> parts) { throw new AbstractMethodError(); }
    public static boolean canApplyBlock(ServerPlayer p, int tall) { throw new AbstractMethodError(); }
    public static boolean applyBlock(ServerPlayer p, List<MimicBlockPart> parts, CompoundTag be, float accuracy, ResourceLocation source, int priority) { throw new AbstractMethodError(); }
    public static CompoundTag captureBlockEntityData(ServerLevel l, BlockPos pos) { throw new AbstractMethodError(); }
}
