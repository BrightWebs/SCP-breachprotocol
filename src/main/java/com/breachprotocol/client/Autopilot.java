package com.breachprotocol.client;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.world.FlightState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The autopilot's hands on your own client: the server picks the fight (career.Autopilot) and does the
 * striking, blinking and casting; here the body turns to face it and flies to it, keeping just out of its
 * way, circling a little so it isn't a sitting target. With nothing to fight, you have yourself back.
 */
@EventBusSubscriber(modid = BreachProtocol.MODID, value = Dist.CLIENT)
public final class Autopilot {
    private Autopilot() {}

    private static boolean steering, wasSteering, released;
    private static float orbit;

    /** Is the autopilot moving the body this tick? */
    public static boolean steering() { return steering; }

    /** True once, the tick after it lets go (so flight picks up from the speed you're at). */
    public static boolean justReleased() { boolean r = released; released = false; return r; }

    private static LivingEntity target(Minecraft mc) {
        if (!FlightState.autoOn || FlightState.autoTarget < 0 || mc.level == null) return null;
        Entity e = mc.level.getEntity(FlightState.autoTarget);
        return e instanceof LivingEntity le && le.isAlive() ? le : null;
    }

    @SubscribeEvent
    public static void steer(PlayerTickEvent.Pre event) {
        if (!(event.getEntity() instanceof LocalPlayer p)) return;
        Minecraft mc = Minecraft.getInstance();
        LivingEntity t = target(mc);
        steering = t != null && !p.isPassenger() && !p.isSpectator();
        if (!steering) {
            if (wasSteering) released = true;
            wasSteering = false;
            return;
        }
        wasSteering = true;
        // face it, smoothly
        Vec3 eye = p.getEyePosition(), aim = t.position().add(0, t.getBbHeight() * 0.6, 0);
        Vec3 to = aim.subtract(eye);
        float wantYaw = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
        float wantPitch = (float) -Math.toDegrees(Math.atan2(to.y, to.horizontalDistance()));
        p.setYRot(Mth.approachDegrees(p.getYRot(), wantYaw, 25F));
        p.setXRot(Mth.approach(p.getXRot(), Mth.clamp(wantPitch, -89F, 89F), 15F));
        p.yHeadRot = p.getYRot();
        // get there: through the air if you can fly, on foot (sprinting, hopping obstacles) if not
        double d = to.length();
        double keep = Math.max(2.4, t.getBbWidth() / 2 + 2.0); // just inside arm's reach
        orbit += 0.04F;
        Vec3 flat = new Vec3(to.x, 0, to.z);
        Vec3 dir = flat.lengthSqr() < 1e-4 ? Vec3.ZERO : flat.normalize();
        Vec3 side = new Vec3(-dir.z, 0, dir.x).scale(Mth.sin(orbit) * 0.25);
        boolean canFly = p.getAbilities().mayfly;
        if (canFly && !p.getAbilities().flying && (d > 6 || !p.onGround())) { p.getAbilities().flying = true; p.onUpdateAbilities(); }
        Vec3 want;
        if (d > keep + 0.5) {
            double speed = Math.min(d * 0.25, canFly ? 1.8 : 0.5);
            Vec3 v = to.normalize().scale(speed);
            want = canFly ? v.add(side) : new Vec3(v.x, p.getDeltaMovement().y, v.z);
        } else if (d < keep - 0.8) {
            Vec3 v = to.normalize().scale(-0.25);
            want = canFly ? v : new Vec3(v.x, p.getDeltaMovement().y, v.z);
        } else {
            want = canFly ? side.add(0, (aim.y - 0.6 - eye.y) * 0.1, 0) : new Vec3(side.x, p.getDeltaMovement().y, side.z);
        }
        Vec3 now = p.getDeltaMovement();
        p.setDeltaMovement(now.add(want.subtract(now).scale(0.45)));
        if (!canFly && p.onGround() && p.horizontalCollision) p.jumpFromGround();
        p.setSprinting(d > keep + 2);
    }

    @SubscribeEvent
    public static void hud(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!FlightState.autoOn || mc.options.hideGui || mc.player == null) return;
        GuiGraphics g = event.getGuiGraphics();
        LivingEntity t = target(mc);
        String s = t == null ? "AUTOPILOT  ·  WATCHING" : "AUTOPILOT  ·  " + t.getDisplayName().getString().toUpperCase() + "  ·  " + (int) mc.player.distanceTo(t) + "m";
        int x = g.guiWidth() / 2, y = 6;
        int w = mc.font.width(s);
        g.fill(x - w / 2 - 6, y - 3, x + w / 2 + 6, y + 11, 0x80140A22);
        g.drawString(mc.font, s, x - w / 2, y, t == null ? 0xFFB8A8D8 : 0xFFE0B0FF, true);
        if (t != null) {
            float hp = Mth.clamp(t.getHealth() / Math.max(1F, t.getMaxHealth()), 0F, 1F);
            g.fill(x - 50, y + 13, x + 50, y + 15, 0x80301030);
            g.fill(x - 50, y + 13, x - 50 + (int) (100 * hp), y + 15, 0xFFC060FF);
        }
    }
}
