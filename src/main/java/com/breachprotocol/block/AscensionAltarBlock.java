package com.breachprotocol.block;

import com.breachprotocol.career.Career;
import com.breachprotocol.career.CareerEvents;
import com.breachprotocol.career.Rank;
import com.breachprotocol.compat.Unleashed;
import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Altar beyond the Gate. The Administrator who swears before it, pays its price (see
 * career.Altar) and offers SCP-500 stops being human: they become SCP-Ω, The Ascendant - immortal,
 * and master of every anomaly.
 */
public class AscensionAltarBlock extends Block {
    public static final MapCodec<AscensionAltarBlock> CODEC = simpleCodec(AscensionAltarBlock::new);
    private static final VoxelShape SHAPE = Shapes.or(Block.box(2, 0, 2, 14, 4, 14), Block.box(4, 4, 4, 12, 12, 12), Block.box(1, 12, 1, 15, 16, 15));

    public AscensionAltarBlock(Properties props) {
        super(props);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp)) return ItemInteractionResult.FAIL;
        if (!Career.isAscended(sp) && Career.rank(sp) == Rank.ADMINISTRATOR && stack.is(Unleashed.scp500())) {
            // the cure, offered once the Altar's price is paid
            if (com.breachprotocol.career.Altar.offer(sp)) {
                stack.consume(1, sp);
                CareerEvents.beginRitual(sp);
            }
            return ItemInteractionResult.SUCCESS;
        }
        com.breachprotocol.career.Altar.touched(sp);
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer sp) com.breachprotocol.career.Altar.touched(sp);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        for (int i = 0; i < 3; i++) {
            level.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.5 + (random.nextDouble() - 0.5), pos.getY() + 1.1 + random.nextDouble(),
                    pos.getZ() + 0.5 + (random.nextDouble() - 0.5), 0, 0.05, 0);
        }
        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5, 0, 0.02, 0);
        }
    }
}
