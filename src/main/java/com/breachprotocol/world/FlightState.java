package com.breachprotocol.world;

import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who is flying flat out, lying along their flight, on either side. While they are, their pose (and
 * so their hitbox: a low box, like swimming) is the lying one; see mixin.PlayerPoseMixin.
 */
public final class FlightState {
    private FlightState() {}

    /** The server's view: set by the flier's client ("pow:fly:lie"), cleared when they slow, land or stop flying. */
    public static final Set<UUID> LYING = ConcurrentHashMap.newKeySet();
    /** The client's view of its own player. */
    public static volatile boolean localLying;
    public static volatile UUID localId;
    /** Client: other players (by entity id) down in the take-off stance, as the server reported. */
    public static final Set<Integer> CHARGING = ConcurrentHashMap.newKeySet();
    /** Client: other players (by entity id) running with a shoulder down. */
    public static final Set<Integer> RAMMING = ConcurrentHashMap.newKeySet();
    /** Client: other players (by entity id) in the sixth running gear, the ninja run. */
    public static final Set<Integer> NINJA = ConcurrentHashMap.newKeySet();
    /** Autopilot (career.Autopilot), for this client: whether it's on, and the entity it's going for (-1: none). */
    public static volatile boolean autoOn;
    public static volatile int autoTarget = -1;
    /** How it's going about it: 0 chase, 1 run straight through, 2 dive from above, 3 run circles (a tornado). */
    public static volatile int autoTactic;

    public static boolean lying(Player p) {
        if (!p.level().isClientSide) return LYING.contains(p.getUUID()) && p.getAbilities().flying && !p.isPassenger();
        if (p.getUUID().equals(localId)) return localLying;
        // someone else, as the server describes them: flat out in mid-air (not crawling under a ledge, not swimming)
        return p.getPose() == Pose.SWIMMING && !p.onGround() && !p.isInWater() && !p.isPassenger();
    }
}
