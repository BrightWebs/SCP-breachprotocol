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
 * The Altar beyond the Gate. The Administrator who offers SCP-500 here stops being human:
 * they become SCP-Ω, The Ascendant - immortal, and master of every anomaly.
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
        if (Career.isAscended(sp)) {
            sp.displayClientMessage(Component.literal("You are already everything the altar can make you.").withStyle(ChatFormatting.LIGHT_PURPLE), true);
            return ItemInteractionResult.SUCCESS;
        }
        if (Career.rank(sp) != Rank.ADMINISTRATOR) {
            sp.displayClientMessage(Component.literal("The altar does not answer you.").withStyle(ChatFormatting.DARK_GRAY), true);
            return ItemInteractionResult.SUCCESS;
        }
        if (!stack.is(Unleashed.scp500())) {
            sp.displayClientMessage(Component.literal("The altar wants the cure for all things. Offer it SCP-500.").withStyle(ChatFormatting.GOLD), true);
            return ItemInteractionResult.SUCCESS;
        }
        stack.consume(1, sp);
        CareerEvents.beginRitual(sp);
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            player.displayClientMessage(Component.literal(Career.rank(player) == Rank.ADMINISTRATOR
                    ? "Offer SCP-500 to become immortal." : "The altar does not answer you.").withStyle(ChatFormatting.GOLD), true);
        }
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
