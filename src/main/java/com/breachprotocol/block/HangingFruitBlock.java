package com.breachprotocol.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.function.Supplier;

/**
 * A fruit hanging from the leaves of a Garden tree. Ripe, it shows the fruit; right-click to pick it
 * (it goes straight into your hand to eat) and a small green bud is left, which ripens again with
 * time. It hangs from the leaves above it and falls if they go.
 */
public class HangingFruitBlock extends Block {
    public static final BooleanProperty RIPE = BooleanProperty.create("ripe");
    private static final VoxelShape SHAPE = Block.box(4, 3, 4, 12, 16, 12);

    private final Supplier<Item> fruit;
    private final int regrowChance;

    public HangingFruitBlock(Supplier<Item> fruit, int regrowChance, Properties p) {
        super(p);
        this.fruit = fruit;
        this.regrowChance = regrowChance;
        registerDefaultState(stateDefinition.any().setValue(RIPE, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(RIPE); }

    @Override
    protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) { return SHAPE; }

    @Override
    protected VoxelShape getCollisionShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) { return net.minecraft.world.phys.shapes.Shapes.empty(); }

    @Override
    protected InteractionResult useWithoutItem(BlockState s, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!s.getValue(RIPE)) return InteractionResult.PASS;
        if (!level.isClientSide) {
            ItemStack got = new ItemStack(fruit.get());
            if (!player.getInventory().add(got)) player.drop(got, false);
            level.setBlock(pos, s.setValue(RIPE, false), Block.UPDATE_ALL);
            // it grows back: about two minutes for ordinary fruit, ten for the two Trees
            level.scheduleTick(pos, this, regrowChance >= 60 ? 12000 : 1800 + level.random.nextInt(1200));
            level.playSound(null, pos, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.BLOCKS, 0.9F, 1.1F + level.random.nextFloat() * 0.2F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void tick(BlockState s, ServerLevel level, BlockPos pos, RandomSource r) {
        if (!s.getValue(RIPE)) {
            level.setBlock(pos, s.setValue(RIPE, true), Block.UPDATE_CLIENTS);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.4, pos.getZ() + 0.5, 3, 0.2, 0.2, 0.2, 0);
        }
    }

    @Override
    protected boolean isRandomlyTicking(BlockState s) { return !s.getValue(RIPE); }

    @Override
    protected void randomTick(BlockState s, ServerLevel level, BlockPos pos, RandomSource r) {
        // fruit picked before this version (or whose schedule was lost) still comes back
        if (!s.getValue(RIPE) && r.nextInt(Math.max(1, regrowChance / 4)) == 0) level.setBlock(pos, s.setValue(RIPE, true), Block.UPDATE_CLIENTS);
    }

    @Override
    protected boolean canSurvive(BlockState s, LevelReader l, BlockPos pos) {
        BlockState above = l.getBlockState(pos.above());
        return !above.isAir() && !above.liquid();
    }

    @Override
    protected BlockState updateShape(BlockState s, Direction d, BlockState n, LevelAccessor l, BlockPos pos, BlockPos np) {
        return d == Direction.UP && !canSurvive(s, l, pos) ? Blocks.AIR.defaultBlockState() : super.updateShape(s, d, n, l, pos, np);
    }
}
