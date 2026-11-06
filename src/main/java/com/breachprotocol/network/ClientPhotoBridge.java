package com.breachprotocol.network;

/** Keeps client-only classes out of the common network setup; only ever called on the client. */
final class ClientPhotoBridge {
    private ClientPhotoBridge() {}

    static void received(int img, byte[] png) {
        com.breachprotocol.client.PhotoCache.received(img, png);
    }
}
