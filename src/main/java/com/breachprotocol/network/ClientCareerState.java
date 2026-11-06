package com.breachprotocol.network;

import net.minecraft.nbt.CompoundTag;

/** Client mirror of the player's career (plain data, safe on both sides). */
public final class ClientCareerState {
    public static CompoundTag tag = new CompoundTag();
    /** Bumped on every sync so open screens know to rebuild. */
    public static int version = 0;

    private ClientCareerState() {}
}
