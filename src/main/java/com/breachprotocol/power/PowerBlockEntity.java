package com.breachprotocol.power;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * Base for every grid block. Uses NeoForge's standard energy capability (FE), so the grid
 * also works with other mods' cables, machines and batteries.
 */
public abstract class PowerBlockEntity extends BlockEntity {
    protected final EnergyBuffer energy;

    protected PowerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int capacity, int maxIn, int maxOut) {
        super(type, pos, state);
        this.energy = new EnergyBuffer(capacity, maxIn, maxOut, this::setChanged);
    }

    /** Capability exposed to neighbours. {@code side} may be null. */
    public IEnergyStorage getEnergy(Direction side) {
        return energy;
    }

    public EnergyBuffer buffer() {
        return energy;
    }

    /** Pushes up to {@code max} FE out of {@code side} into whatever is there. Returns FE sent. */
    protected int pushTo(Direction side, int max) {
        if (level == null || energy.getEnergyStored() <= 0 || max <= 0) return 0;
        IEnergyStorage target = neighbour(level, worldPosition, side);
        if (target == null || !target.canReceive()) return 0;
        int offered = Math.min(max, energy.getEnergyStored());
        int accepted = target.receiveEnergy(offered, false);
        if (accepted > 0) energy.consume(accepted);
        return accepted;
    }

    public static IEnergyStorage neighbour(Level level, BlockPos pos, Direction side) {
        return level.getCapability(Capabilities.EnergyStorage.BLOCK, pos.relative(side), side.getOpposite());
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Energy", energy.getEnergyStored());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energy.set(tag.getInt("Energy"));
    }
}
