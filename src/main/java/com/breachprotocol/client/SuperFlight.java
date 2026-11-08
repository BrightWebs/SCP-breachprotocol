package com.breachprotocol.client;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.network.ClientCareerState;
import com.breachprotocol.network.PhoneActionPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Quaternionf;

/**
 * SCP-Ω's flight, the way it looks in films rather than Minecraft's creative hover.
 *
 * <ul>
 * <li>Take off and land as usual (double-tap jump; touching down ends it).</li>
 * <li>You fly where you look: forward is the way you face, up and down included. Jump climbs, sneak
 *     drops. You have weight: you build up speed, carry it through turns, and drift to a hover when
 *     you let go.</li>
 * <li>Hold sprint to go faster. <b>Double-tap sprint</b> to shift up a gear, again and again: the
 *     second gear breaks the sound barrier (a boom everyone hears, a vapour cone, the screen shakes),
 *     and every gear after that is faster still. Let go of forward and you drop back down the gears.</li>
 * <li>The camera banks into turns, the view stretches with speed, and to anyone watching you lie
 *     flat into the direction you're flying.</li>
 * </ul>
 */
@EventBusSubscriber(modid = BreachProtocol.MODID, value = Dist.CLIENT)
public final class SuperFlight {
    private SuperFlight() {}

    /** Blocks per tick: cruising, sprinting, then each gear. Gear 2 is the sound barrier. */
    private static final double[] SPEED = {0.75, 1.4, 2.3, 3.6, 4.8, 6.0, 7.4};
    private static final int MAX_GEAR = SPEED.length - 2;

    private static Vec3 vel = Vec3.ZERO, before = Vec3.ZERO;
    private static int gear, idle, lastSprintTap = -100, ticks, shake, punch;
    private static boolean sprintWas, zeroed, flying;
    private static float roll, rollWas, lastYaw;
    private static WindSound wind;

    static boolean eligible(LocalPlayer p) {
        return p.getAbilities().flying && p.getAbilities().mayfly && !p.isCreative() && !p.isSpectator() && !p.isPassenger() && !p.isFallFlying()
                && ClientCareerState.tag.getInt("dom") >= 2;
    }

    public static boolean on() { return flying; }

    private static void send(String a) { PacketDistributor.sendToServer(new PhoneActionPayload(a, 0)); }

    @SubscribeEvent
    public static void pre(PlayerTickEvent.Pre event) {
        if (!(event.getEntity() instanceof LocalPlayer p)) return;
        Minecraft mc = Minecraft.getInstance();
        ticks++;
        boolean now = eligible(p);
        if (!now) {
            if (flying) land(p);
            return;
        }
        if (!flying) { flying = true; vel = p.getDeltaMovement(); gear = 0; }
        // vanilla's own flying pushes nothing: the motion is all ours
        p.getAbilities().setFlyingSpeed(0F);
        zeroed = true;

        var in = p.input;
        float fwd = in.forwardImpulse, side = in.leftImpulse;
        boolean up = in.jumping, down = in.shiftKeyDown;
        boolean sprint = mc.options.keySprint.isDown();

        // double-tap sprint: up a gear
        if (sprint && !sprintWas && mc.screen == null) {
            if (ticks - lastSprintTap <= 7 && fwd > 0) shift(p, Math.min(MAX_GEAR, Math.max(1, gear + 1)));
            lastSprintTap = ticks;
        }
        sprintWas = sprint;
        // let go of forward and the gears unwind
        if (fwd <= 0) { if (++idle > 12 && gear > 0) { shift(p, 0); } } else idle = 0;

        Vec3 look = p.getLookAngle();
        Vec3 right = new Vec3(-look.z, 0, look.x).normalize();
        double top = gear > 0 ? SPEED[gear + 1] : sprint ? SPEED[1] : SPEED[0];
        Vec3 want = look.scale(fwd * top).add(right.scale(-side * SPEED[0] * 0.8)).add(0, (up ? 0.6 : 0) - (down ? 0.6 : 0), 0);
        boolean input = fwd != 0 || side != 0 || up || down;
        // weight: the faster you go, the slower you turn and the longer you take to stop
        double k = input ? Mth.clamp(0.16 - vel.length() * 0.012, 0.045, 0.16) : 0.09;
        vel = vel.add(want.subtract(vel).scale(k));
        if (!input && vel.lengthSqr() < 0.0025) vel = new Vec3(0, Math.sin(ticks * 0.08) * 0.012, 0); // a hover that breathes
        p.setDeltaMovement(vel);
        p.setSprinting(false);
        before = p.position();

        // effects on your own screen
        double sp = vel.length();
        if (gear >= 2 && mc.level != null) {
            Vec3 back = p.position().add(0, 0.9, 0).subtract(vel.normalize().scale(1.2));
            for (int i = 0; i < 2; i++) mc.level.addParticle(ParticleTypes.CLOUD, back.x, back.y, back.z, -vel.x * 0.05, -vel.y * 0.05, -vel.z * 0.05);
        }
        if (sp > 1.2 && mc.level != null && ticks % 2 == 0) {
            Vec3 a = p.position().add(look.scale(3)).add((Math.random() - 0.5) * 3, Math.random() * 2, (Math.random() - 0.5) * 3);
            mc.level.addParticle(ParticleTypes.WHITE_ASH, a.x, a.y, a.z, -vel.x, -vel.y, -vel.z);
        }
        if (wind == null || wind.isStopped()) { wind = new WindSound(p); mc.getSoundManager().play(wind); }
        // the camera leans into turns
        float yawRate = Mth.wrapDegrees(p.getYRot() - lastYaw);
        lastYaw = p.getYRot();
        rollWas = roll;
        roll = Mth.lerp(0.15F, roll, Mth.clamp(yawRate * (float) Math.min(1.0, sp) * 1.6F - side * 6F, -28F, 28F));
        if (shake > 0) shake--;
        if (punch > 0) punch--;
    }

    @SubscribeEvent
    public static void post(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof LocalPlayer p) || !flying) return;
        // what actually happened: a wall takes the speed out of you
        Vec3 moved = p.position().subtract(before);
        if (vel.lengthSqr() > 0.01 && moved.lengthSqr() < vel.lengthSqr() * 0.36) {
            if (vel.length() > 2.5) {
                shake = 12;
                p.playSound(SoundEvents.GENERIC_EXPLODE.value(), 0.8F, 1.3F);
                send("pow:fly:impact");
                shift(p, 0);
            }
            vel = moved;
        }
    }

    private static void shift(LocalPlayer p, int g) {
        int was = gear;
        gear = g;
        if (g == was) return;
        send("pow:fly:gear:" + g);
        if (g > was && g >= 2) boom(p, g);
        else if (g > was) p.playSound(SoundEvents.FIREWORK_ROCKET_LAUNCH, 0.7F, 0.8F);
    }

    /** Through the sound barrier. */
    private static void boom(LocalPlayer p, int g) {
        Minecraft mc = Minecraft.getInstance();
        shake = 14;
        punch = 10;
        p.playSound(SoundEvents.GENERIC_EXPLODE.value(), 1.2F, 0.5F);
        p.playSound(SoundEvents.WARDEN_SONIC_BOOM, 1.0F, 0.9F + g * 0.05F);
        send("pow:fly:boom:" + g);
        if (mc.level == null) return;
        // the vapour cone: a ring round you, square to the way you're going
        Vec3 dir = p.getLookAngle(), c = p.position().add(0, 0.9, 0).add(dir.scale(0.5));
        Vec3 a = Math.abs(dir.y) > 0.9 ? new Vec3(1, 0, 0) : dir.cross(new Vec3(0, 1, 0)).normalize(), b = dir.cross(a).normalize();
        for (int i = 0; i < 48; i++) {
            double t = i * Math.PI * 2 / 48;
            Vec3 o = a.scale(Math.cos(t) * 1.6).add(b.scale(Math.sin(t) * 1.6));
            mc.level.addParticle(ParticleTypes.CLOUD, c.x + o.x, c.y + o.y, c.z + o.z, o.x * 0.08 - dir.x * 0.3, o.y * 0.08 - dir.y * 0.3, o.z * 0.08 - dir.z * 0.3);
        }
        mc.level.addParticle(ParticleTypes.SONIC_BOOM, c.x, c.y, c.z, 0, 0, 0);
        mc.level.addParticle(ParticleTypes.EXPLOSION, c.x - dir.x * 2, c.y - dir.y * 2, c.z - dir.z * 2, 0, 0, 0);
    }

    private static void land(LocalPlayer p) {
        flying = false;
        if (gear != 0) { gear = 0; send("pow:fly:gear:0"); }
        if (zeroed) { p.getAbilities().setFlyingSpeed(0.05F); zeroed = false; }
        vel = Vec3.ZERO;
        roll = rollWas = 0;
    }

    // ================================================================== how it feels

    @SubscribeEvent
    public static void fov(ComputeFovModifierEvent event) {
        if (!flying) return;
        double sp = vel.length();
        float stretch = (float) Math.min(0.55, sp * 0.07) + (punch > 0 ? punch * 0.025F : 0);
        event.setNewFovModifier(1F + stretch);
    }

    @SubscribeEvent
    public static void camera(ViewportEvent.ComputeCameraAngles event) {
        if (!flying) return;
        float pt = (float) event.getPartialTick();
        float r = Mth.lerp(pt, rollWas, roll);
        if (shake > 0) {
            float m = shake / 14F * 2.2F;
            event.setPitch(event.getPitch() + (float) (Math.random() - 0.5) * m);
            event.setYaw(event.getYaw() + (float) (Math.random() - 0.5) * m);
            r += (float) (Math.random() - 0.5) * m * 2;
        }
        event.setRoll(event.getRoll() + r);
    }

    @SubscribeEvent
    public static void hud(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!flying || mc.options.hideGui || mc.player == null) return;
        GuiGraphics g = event.getGuiGraphics();
        String s = gear == 0 ? (vel.length() > 1.0 ? "FLYING FAST" : "FLYING") : "GEAR " + gear + (gear >= 2 ? "  ·  SUPERSONIC" : "");
        String gs = gear < MAX_GEAR ? "DOUBLE-TAP SPRINT: GEAR UP" : "TOP SPEED";
        int x = g.guiWidth() / 2, y = g.guiHeight() - 62;
        g.drawString(mc.font, s, x - mc.font.width(s) / 2, y, gear >= 2 ? 0xFFFFE0A0 : 0xFFE0E0F0, true);
        PowerKeys.small(g, mc.font, gs, x - (int) (mc.font.width(gs) * 0.375F), y + 10, 0xFFA898C0);
    }

    /** Lying into the flight, for anyone watching (you included, in third person). */
    @SubscribeEvent
    public static void lean(RenderPlayerEvent.Pre event) {
        Player p = event.getEntity();
        if (p.isFallFlying() || p.onGround() || p.isPassenger() || p.isInWater()) return;
        float pt = event.getPartialTick();
        Vec3 d = new Vec3(p.getX() - p.xo, p.getY() - p.yo, p.getZ() - p.zo);
        double h = d.horizontalDistance();
        // your own flight we know; someone else's we judge by how they move (other players' abilities aren't sent to us)
        boolean mine = p == Minecraft.getInstance().player;
        if (mine ? !flying : h < 0.5) return;
        if (h < 0.25) return;
        float yaw = Mth.rotLerp(pt, p.yRotO, p.getYRot()) * Mth.DEG_TO_RAD;
        float tilt = (float) Mth.clamp((h - 0.25) / 0.9, 0, 1) * 82F;
        // diving tips you further over, climbing stands you up
        tilt = Mth.clamp(tilt - (float) Math.toDegrees(Math.atan2(d.y, Math.max(0.05, h))) * 0.6F, 0F, 100F) * Mth.DEG_TO_RAD;
        var ps = event.getPoseStack();
        ps.pushPose();
        ps.translate(0, p.getBbHeight() / 2, 0);
        ps.mulPose(new Quaternionf().rotateAxis(tilt, Mth.cos(yaw), 0, Mth.sin(yaw)));
        ps.translate(0, -p.getBbHeight() / 2, 0);
        LEANED.add(p.getId());
    }

    private static final java.util.Set<Integer> LEANED = new java.util.HashSet<>();

    @SubscribeEvent
    public static void unlean(RenderPlayerEvent.Post event) {
        if (LEANED.remove(event.getEntity().getId())) event.getPoseStack().popPose();
    }

    /** The wind, louder the faster you go. */
    private static final class WindSound extends AbstractTickableSoundInstance {
        private final LocalPlayer p;

        WindSound(LocalPlayer p) {
            super(SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, net.minecraft.client.resources.sounds.SoundInstance.createUnseededRandom());
            this.p = p;
            looping = true;
            delay = 0;
            volume = 0.01F;
        }

        @Override
        public void tick() {
            if (!flying || p.isRemoved()) { stop(); return; }
            x = p.getX(); y = p.getY(); z = p.getZ();
            float sp = (float) vel.length();
            volume = Mth.clamp(sp * 0.35F, 0F, 1F);
            pitch = 0.8F + Mth.clamp(sp * 0.08F, 0F, 0.6F);
        }

        @Override
        public boolean canStartSilent() { return true; }
    }
}
