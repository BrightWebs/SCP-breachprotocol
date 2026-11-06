package com.breachprotocol.power;

import com.breachprotocol.item.FuelRodItem;
import com.breachprotocol.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Fission Reactor Core. Load a Uranium Fuel Rod and it makes 600 FE/t, and heat.
 * Cool it with water source blocks and ice touching (or within 1 block of) the core.
 * Redstone signal = SCRAM (control rods in, no power, cools down).
 * Let it pass 1000 degrees and it melts down.
 */
public class ReactorBlock extends PowerBlock {
    public static final MapCodec<ReactorBlock> CODEC = simpleCodec(ReactorBlock::new);
    /** 0 off, 1 running, 2 hot, 3 critical. Drives the texture glow. */
    public static final IntegerProperty HEAT = IntegerProperty.create("heat", 0, 3);

    public ReactorBlock(Properties props) {
        super(props, ModBlockEntities.REACTOR);
        registerDefaultState(stateDefinition.any().setValue(HEAT, 0));
    }

    @Override
    protected MapCodec<? extends ReactorBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(HEAT);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof ReactorBlockEntity be)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (stack.getItem() instanceof FuelRodItem) {
            if (!level.isClientSide) {
                ItemStack rest = be.rods().insertItem(0, stack.copy(), false);
                player.setItemInHand(hand, rest);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (stack.isEmpty() && player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                ItemStack out = be.rods().extractItem(0, 64, false);
                if (!out.isEmpty()) player.setItemInHand(hand, out);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource r) {
        int h = state.getValue(HEAT);
        if (h >= 2) {
            for (int i = 0; i < h; i++)
                level.addParticle(h == 3 ? ParticleTypes.LAVA : ParticleTypes.CLOUD, pos.getX() + r.nextDouble(), pos.getY() + 1.0, pos.getZ() + r.nextDouble(), 0, 0.05, 0);
        }
        if (h >= 1 && r.nextInt(4) == 0) {
            level.addParticle(ParticleTypes.GLOW, pos.getX() + r.nextDouble(), pos.getY() + r.nextDouble(), pos.getZ() + r.nextDouble(), 0, 0, 0);
        }
    }
}
