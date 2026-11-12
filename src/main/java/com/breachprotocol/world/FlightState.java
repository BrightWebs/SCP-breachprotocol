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
    /** Who has heat vision on, and at what strength (0..4: 5%, 15%, 50%, 80%, 100%). */
    public static final java.util.Map<Integer, Integer> HEAT = new ConcurrentHashMap<>();
    /** Whose eyes glow (heat vision's crouch-use), beam or no beam. */
    public static final Set<Integer> EYES = ConcurrentHashMap.newKeySet();
    /** Who has taken SCP-Ω's other shape, the Skull King: {state (30 changing, 31 changed, 32 changing back), the game time it began}. */
    public static final java.util.Map<Integer, long[]> SKULL = new ConcurrentHashMap<>();

    /** Is this player wholly in True Form (the Skull King)? Either side. */
    public static boolean trueForm(Player p) {
        if (p == null) return false;
        if (!p.level().isClientSide) return p instanceof net.minecraft.server.level.ServerPlayer sp && com.breachprotocol.career.SkullKing.whole(sp);
        long[] s = SKULL.get(p.getId());
        return s != null && (s[0] == 31 || s[0] == 30 && p.level().getGameTime() - s[1] >= 70);
    }

    /**
     * In True Form, holding crouch against a wall, it takes hold and climbs (walk into it to go up; just crouch to
     * cling); without crouch, a wall's only a wall. It keeps hold until its feet are over the top.
     */
    public static boolean clinging(Player p) {
        if (!p.isShiftKeyDown() || p.getAbilities().flying || p.isSpectator() || p.isPassenger() || p.isInWater() || !trueForm(p)) return false;
        net.minecraft.world.phys.AABB bb = p.getBoundingBox();
        bb = new net.minecraft.world.phys.AABB(bb.minX - 0.12, bb.minY + 0.05, bb.minZ - 0.12, bb.maxX + 0.12, bb.maxY - 0.1, bb.maxZ + 0.12);
        return !p.level().noCollision(p, bb);
    }

    public static boolean lying(Player p) {
        if (!p.level().isClientSide) return LYING.contains(p.getUUID()) && p.getAbilities().flying && !p.isPassenger();
        if (p.getUUID().equals(localId)) return localLying;
        // someone else, as the server describes them: flat out in mid-air (not crawling under a ledge, not swimming)
        return p.getPose() == Pose.SWIMMING && !p.onGround() && !p.isInWater() && !p.isPassenger();
    }
}
