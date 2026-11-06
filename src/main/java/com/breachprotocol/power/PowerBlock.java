package com.breachprotocol.power;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.function.Supplier;

/** A block whose block entity ticks on the server and can describe itself. */
public abstract class PowerBlock extends BaseEntityBlock {
    private final Supplier<? extends BlockEntityType<? extends TickingPower>> type;

    protected PowerBlock(Properties props, Supplier<? extends BlockEntityType<? extends TickingPower>> type) {
        super(props);
        this.type = type;
    }

    public interface TickingPower {
        void serverTick();

        Component status();
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return type.get().create(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> t) {
        if (level.isClientSide || t != type.get()) return null;
        return (l, p, s, be) -> ((TickingPower) be).serverTick();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof TickingPower tp) {
            player.displayClientMessage(tp.status(), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    public static String fe(int value) {
        if (value >= 1_000_000) return String.format("%.2fM FE", value / 1_000_000.0);
        if (value >= 1_000) return String.format("%.1fk FE", value / 1_000.0);
        return value + " FE";
    }
}
