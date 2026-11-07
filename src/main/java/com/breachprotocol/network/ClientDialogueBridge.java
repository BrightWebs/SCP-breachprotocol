package com.breachprotocol.network;

/** Keeps the client screen out of common code; only ever called on the client. */
final class ClientDialogueBridge {
    private ClientDialogueBridge() {}

    static void received(net.minecraft.nbt.CompoundTag tag) {
        com.breachprotocol.client.DialogueScreen.received(tag);
    }
}
