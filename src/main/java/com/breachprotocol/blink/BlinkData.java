package com.breachprotocol.blink;

/** Per-player blink state. Server-authoritative; synced to the owner for the HUD. */
public class BlinkData {
    public static final float MAX = 100F;
    /** Seconds of open eyes before a forced blink, while SCP-173 is near. */
    public static final float SECONDS_TO_BLINK = 9F;
    public static final float DRAIN_PER_TICK = MAX / (SECONDS_TO_BLINK * 20F);
    public static final int BLINK_TICKS = 6;

    public float meter = MAX;
    /** Remaining ticks of an involuntary/voluntary blink. */
    public int blinkTicks = 0;
    /** True while the player holds the "close eyes" key. */
    public boolean holdingClosed = false;
    /** Is a blink-relevant anomaly nearby (drives HUD visibility). */
    public boolean threat = false;

    // last values sent to the client
    public float sentMeter = -1;
    public boolean sentClosed = false;
    public boolean sentThreat = false;

    public boolean eyesClosed() {
        return blinkTicks > 0 || holdingClosed;
    }
}
