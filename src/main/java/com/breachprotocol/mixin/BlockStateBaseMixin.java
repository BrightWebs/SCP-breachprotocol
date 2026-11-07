package com.breachprotocol.mixin;

import com.breachprotocol.world.FalseOcclusion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * SCP Unleashed's furniture (couches, desks, tables, cabinets...) occludes like a full block although
 * its model doesn't fill the face, so the face of the block next to it (usually the floor under it)
 * isn't drawn and you see straight through the floor. Those blocks hide no neighbouring faces now.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateBaseMixin {
    @Inject(method = "getFaceOcclusionShape", at = @At("HEAD"), cancellable = true)
    private void breachprotocol$noFalseOcclusion(BlockGetter level, BlockPos pos, Direction direction, CallbackInfoReturnable<VoxelShape> cir) {
        if (FalseOcclusion.applies((BlockState) (Object) this, level, pos)) cir.setReturnValue(Shapes.empty());
    }
}
