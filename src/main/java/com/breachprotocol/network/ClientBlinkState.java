package com.breachprotocol.network;

/** Plain client-side mirror of the server's blink data (no client classes, safe to load anywhere). */
public final class ClientBlinkState {
    public static float meter = 100F;
    public static boolean closed = false;
    public static boolean threat = false;

    private ClientBlinkState() {}
}
