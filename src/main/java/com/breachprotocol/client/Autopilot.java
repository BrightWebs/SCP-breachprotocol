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
        double d = to.length();
        int tactic = FlightState.autoTactic;
        if (tactic != lastTactic) { lastTactic = tactic; diving = false; }
        switch (tactic) {
            case 1 -> ram(p, t);
            case 2 -> dive(p, t, d);
            case 3 -> circle(p, t);
            default -> chase(p, t, to, eye, aim, d);
        }
    }

    private static int lastTactic = -1;
    private static boolean diving;

    private static void fly(LocalPlayer p, boolean on) {
        if (!p.getAbilities().mayfly) return;
        if (p.getAbilities().flying != on) { p.getAbilities().flying = on; p.onUpdateAbilities(); }
    }

    private static void push(LocalPlayer p, Vec3 want, double grip) {
        Vec3 now = p.getDeltaMovement();
        p.setDeltaMovement(now.add(want.subtract(now).scale(grip)));
    }

    /** In after it: flying if you can, keeping just inside arm's reach and circling a little. */
    private static void chase(LocalPlayer p, LivingEntity t, Vec3 to, Vec3 eye, Vec3 aim, double d) {
        double keep = Math.max(2.4, t.getBbWidth() / 2 + 2.0);
        orbit += 0.04F;
        Vec3 flat = new Vec3(to.x, 0, to.z);
        Vec3 dir = flat.lengthSqr() < 1e-4 ? Vec3.ZERO : flat.normalize();
        Vec3 side = new Vec3(-dir.z, 0, dir.x).scale(Mth.sin(orbit) * 0.25);
        boolean canFly = p.getAbilities().mayfly;
        if (canFly && (d > 6 || !p.onGround())) fly(p, true);
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
        push(p, want, 0.45);
        if (!canFly && p.onGround() && p.horizontalCollision) p.jumpFromGround();
        p.setSprinting(d > keep + 2);
    }

    /** Down on the ground and straight at it, flat out: the run's top gear. */
    private static void ram(LocalPlayer p, LivingEntity t) {
        fly(p, false);
        Vec3 flat = new Vec3(t.getX() - p.getX(), 0, t.getZ() - p.getZ());
        if (flat.lengthSqr() < 1e-4) return;
        double speed = Math.min(4.6, 0.6 + flat.length() * 0.35);
        Vec3 v = flat.normalize().scale(speed);
        push(p, new Vec3(v.x, Math.min(p.getDeltaMovement().y, 0.1), v.z), 0.6);
        if (p.onGround() && p.horizontalCollision) p.jumpFromGround();
        p.setSprinting(true);
    }

    /** Up into the sky above it, then straight down onto it. */
    private static void dive(LocalPlayer p, LivingEntity t, double d) {
        fly(p, true);
        Vec3 above = t.position().add(0, 18, 0);
        if (!diving) {
            Vec3 to = above.subtract(p.position());
            if (to.length() < 3) diving = true;
            else { push(p, to.normalize().scale(Math.min(2.2, to.length() * 0.3)), 0.5); return; }
        }
        // the drop: faster and faster, straight at it
        Vec3 to = t.position().add(0, t.getBbHeight() * 0.5, 0).subtract(p.position());
        p.setXRot(Mth.approach(p.getXRot(), 80F, 12F));
        push(p, to.normalize().scale(3.2), 0.6);
    }

    /** Round and round it on the ground at the top gear: the laps spin up a tornado that drags the crowd in. */
    private static void circle(LocalPlayer p, LivingEntity t) {
        fly(p, false);
        double r = 7.5;
        Vec3 c = new Vec3(t.getX(), p.getY(), t.getZ());
        Vec3 out = p.position().subtract(c).multiply(1, 0, 1);
        if (out.lengthSqr() < 1e-4) out = new Vec3(1, 0, 0);
        double dist = out.length();
        out = out.normalize();
        Vec3 tangent = new Vec3(-out.z, 0, out.x);
        Vec3 v = tangent.scale(3.0).add(out.scale((r - dist) * 0.5));
        push(p, new Vec3(v.x, Math.min(p.getDeltaMovement().y, 0.1), v.z), 0.6);
        if (p.onGround() && p.horizontalCollision) p.jumpFromGround();
        p.setSprinting(true);
        // face the way you're running (the tornado's in the middle of it)
        p.setYRot((float) Math.toDegrees(Math.atan2(-tangent.x, tangent.z)));
    }

    @SubscribeEvent
    public static void hud(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!FlightState.autoOn || mc.options.hideGui || mc.player == null) return;
        GuiGraphics g = event.getGuiGraphics();
        LivingEntity t = target(mc);
        String[] how = {"", "  ·  RUNNING IT DOWN", "  ·  DIVING", "  ·  TORNADO"};
        String s = t == null ? "AUTOPILOT  ·  WATCHING" : "AUTOPILOT  ·  " + t.getDisplayName().getString().toUpperCase() + "  ·  " + (int) mc.player.distanceTo(t) + "m"
                + how[Math.max(0, Math.min(3, FlightState.autoTactic))];
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
