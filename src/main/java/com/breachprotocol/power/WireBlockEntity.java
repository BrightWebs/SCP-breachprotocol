package com.breachprotocol.power;

import com.breachprotocol.registry.ModBlockEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * Wires equalise with neighbouring wires and push into any other receiver. That's enough to
 * carry power across a whole facility without a central network manager.
 */
public class WireBlockEntity extends PowerBlockEntity implements PowerBlock.TickingPower {
    private final int rate;
    private int moved;

    public WireBlockEntity(BlockPos pos, BlockState state) {
        this(pos, state, state.getBlock() instanceof WireBlock w && w.tier() == 2 ? 8192 : 512);
    }

    private WireBlockEntity(BlockPos pos, BlockState state, int rate) {
        super(state.getBlock() instanceof WireBlock w && w.tier() == 2 ? ModBlockEntities.HEAVY_WIRE.get() : ModBlockEntities.COPPER_WIRE.get(),
                pos, state, rate * 2, rate, rate);
        this.rate = rate;
    }

    @Override
    public void serverTick() {
        moved = 0;
        if (energy.getEnergyStored() <= 0) return;
        Direction[] dirs = Direction.values();
        int start = (int) (level.getGameTime() % 6);
        for (int i = 0; i < 6; i++) {
            Direction d = dirs[(start + i) % 6];
            if (energy.getEnergyStored() <= 0) break;
            if (level.getBlockEntity(worldPosition.relative(d)) instanceof WireBlockEntity other) {
                int diff = energy.getEnergyStored() - other.energy.getEnergyStored();
                if (diff > 1) {
                    int send = Math.min(rate, diff / 2);
                    int got = other.energy.receiveEnergy(send, false);
                    energy.consume(got);
                    moved += got;
                }
            } else {
                IEnergyStorage t = neighbour(level, worldPosition, d);
                if (t != null && t.canReceive()) moved += pushTo(d, rate);
            }
        }
    }

    @Override
    public Component status() {
        return Component.literal("Wire: " + PowerBlock.fe(energy.getEnergyStored()) + " buffered, moving " + moved + " FE/t (max " + rate + ")")
                .withStyle(ChatFormatting.YELLOW);
    }
}
