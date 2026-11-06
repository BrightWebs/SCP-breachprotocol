package com.breachprotocol.blink;

import com.breachprotocol.registry.ModAttachments;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** "Can this player actually see that thing right now?" - shared by SCP-173 and SCP-096. */
public final class Observation {
    private Observation() {}

    /** Player's eyes are usable: not blinking, not holding them shut, not blinded. */
    public static boolean canSee(Player player) {
        if (player.isSpectator() || !player.isAlive()) return false;
        if (player.hasEffect(MobEffects.BLINDNESS)) return false;
        return !player.getData(ModAttachments.BLINK).eyesClosed();
    }

    /**
     * True when {@code target} is inside the player's view cone and there's a clear line of sight.
     * In darkness (light < 4) the player only notices things within 4 blocks, unless they have
     * Night Vision - so cutting the lights really is dangerous.
     */
    public static boolean isWatching(Player player, Entity target, double maxDistance, double minDot) {
        if (!canSee(player)) return false;
        Vec3 eye = player.getEyePosition();
        Vec3 center = target.position().add(0, target.getBbHeight() * 0.5, 0);
        Vec3 to = center.subtract(eye);
        double dist = to.length();
        if (dist > maxDistance) return false;
        if (dist > 4 && !player.hasEffect(MobEffects.NIGHT_VISION)
                && target.level().getMaxLocalRawBrightness(target.blockPosition()) < 4) {
            return false;
        }
        // Widen the cone for big targets that are close (you see 173 even at the edge of view).
        double dot = player.getViewVector(1.0F).dot(to.normalize());
        double cone = minDot - Math.min(0.25, target.getBbWidth() / Math.max(dist, 0.5) * 0.5);
        if (dot < cone) return false;
        return player.hasLineOfSight(target);
    }
}
