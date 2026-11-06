package com.breachprotocol.power;

import net.neoforged.neoforge.energy.EnergyStorage;

/** EnergyStorage that reports changes and allows direct set/insert by its owner. */
public class EnergyBuffer extends EnergyStorage {
    private final Runnable onChange;

    public EnergyBuffer(int capacity, int maxReceive, int maxExtract, Runnable onChange) {
        super(capacity, maxReceive, maxExtract);
        this.onChange = onChange;
    }

    @Override
    public int receiveEnergy(int amount, boolean simulate) {
        int r = super.receiveEnergy(amount, simulate);
        if (r > 0 && !simulate) onChange.run();
        return r;
    }

    @Override
    public int extractEnergy(int amount, boolean simulate) {
        int r = super.extractEnergy(amount, simulate);
        if (r > 0 && !simulate) onChange.run();
        return r;
    }

    /** Owner-side insert that ignores maxReceive (e.g. a generator producing power). */
    public int generate(int amount) {
        int r = Math.min(capacity - energy, amount);
        if (r > 0) {
            energy += r;
            onChange.run();
        }
        return r;
    }

    /** Owner-side drain that ignores maxExtract (e.g. an outlet consuming power). */
    public int consume(int amount) {
        int r = Math.min(energy, amount);
        if (r > 0) {
            energy -= r;
            onChange.run();
        }
        return r;
    }

    public void set(int value) {
        energy = Math.max(0, Math.min(capacity, value));
    }

    public float fill() {
        return capacity == 0 ? 0 : energy / (float) capacity;
    }
}
