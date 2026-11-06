package com.breachprotocol.power;

import com.breachprotocol.registry.ModBlockEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;

public class BatteryBlockEntity extends PowerBlockEntity implements PowerBlock.TickingPower {
    public static final int CAPACITY = 2_000_000, RATE = 4096;
    private int lastIn, lastOut, inThisTick;

    private final IEnergyStorage inputView = new IEnergyStorage() {
        public int receiveEnergy(int a, boolean sim) { int r = energy.receiveEnergy(a, sim); if (!sim) inThisTick += r; return r; }
        public int extractEnergy(int a, boolean sim) { return 0; }
        public int getEnergyStored() { return energy.getEnergyStored(); }
        public int getMaxEnergyStored() { return energy.getMaxEnergyStored(); }
        public boolean canExtract() { return false; }
        public boolean canReceive() { return true; }
    };
    private final IEnergyStorage outputView = new IEnergyStorage() {
        public int receiveEnergy(int a, boolean sim) { return 0; }
        public int extractEnergy(int a, boolean sim) { return energy.extractEnergy(a, sim); }
        public int getEnergyStored() { return energy.getEnergyStored(); }
        public int getMaxEnergyStored() { return energy.getMaxEnergyStored(); }
        public boolean canExtract() { return true; }
        public boolean canReceive() { return false; }
    };

    public BatteryBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BATTERY.get(), pos, state, CAPACITY, RATE, RATE);
    }

    private Direction out() {
        return getBlockState().getValue(BatteryBlock.FACING);
    }

    @Override
    public IEnergyStorage getEnergy(Direction side) {
        if (side == null) return energy;
        return side == out() ? outputView : inputView;
    }

    @Override
    public void serverTick() {
        lastOut = pushTo(out(), RATE);
        lastIn = inThisTick;
        inThisTick = 0;
        int charge = Math.min(4, (int) Math.ceil(energy.fill() * 4));
        BlockState st = getBlockState();
        if (st.getValue(BatteryBlock.CHARGE) != charge) {
            level.setBlock(worldPosition, st.setValue(BatteryBlock.CHARGE, charge), Block.UPDATE_ALL);
        }
        if (level.getGameTime() % 20 == 0) level.updateNeighbourForOutputSignal(worldPosition, st.getBlock());
    }

    @Override
    public Component status() {
        return Component.literal(String.format("Site Battery: %s / %s (%.0f%%)  in %d FE/t, out %d FE/t",
                PowerBlock.fe(energy.getEnergyStored()), PowerBlock.fe(CAPACITY), energy.fill() * 100, lastIn, lastOut)).withStyle(ChatFormatting.AQUA);
    }
}
