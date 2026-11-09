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
import com.breachprotocol.world.FlightState;

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
 * <li><b>Crouch and triple-tap sprint</b>, on the ground or in the air: straight into flight at the top
 *     gear. At the top gear you go through things the way SCP-096 does: blocks in your path are torn
 *     out (bedrock and the like hold), and anything living in your way is thrown aside.</li>
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
    private static boolean sprintWas, zeroed, flying, launchWas;
    private static int taps, firstTap = -100, launchedAt = -100;
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
        // crouch and triple-tap sprint: straight into flight, at full speed
        boolean sprintKey = mc.options.keySprint.isDown();
        if (sprintKey && !launchWas && mc.screen == null) {
            if (ticks - firstTap > 12) { firstTap = ticks; taps = 0; }
            if (++taps >= 3 && mc.options.keyShift.isDown() && canLaunch(p)) { taps = 0; launch(p); }
        }
        launchWas = sprintKey;
        boolean now = eligible(p);
        if (!now) {
            if (flying) land(p);
            return;
        }
        if (!flying) { flying = true; vel = p.getDeltaMovement(); gear = 0; }
        // vanilla's own flying pushes nothing: the motion is all ours
        p.getAbilities().setFlyingSpeed(0F);
        zeroed = true;

        // the keys themselves: the game's own input is cut to a crawl while you lie flat, which would slow you,
        // stand you up, speed you up and lay you down again, over and over
        var o = mc.options;
        boolean free = mc.screen == null;
        float fwd = free ? (o.keyUp.isDown() ? 1F : 0F) - (o.keyDown.isDown() ? 1F : 0F) : 0F;
        float side = free ? (o.keyLeft.isDown() ? 1F : 0F) - (o.keyRight.isDown() ? 1F : 0F) : 0F;
        boolean up = free && o.keyJump.isDown();
        // after a launch, the crouch you launched with doesn't drag you down
        boolean down = free && o.keyShift.isDown() && ticks - launchedAt > 20;
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
        if (gear >= MAX_GEAR) tear(p);
        p.setDeltaMovement(vel);
        p.setSprinting(false);
        // fast enough and you lie along the flight: the pose, and the hitbox with it (the server is told)
        double fast = vel.length();
        boolean lie = FlightState.localLying ? fast > 0.55 : fast > 1.05;
        if (lie != FlightState.localLying) { FlightState.localId = p.getUUID(); FlightState.localLying = lie; send("pow:fly:lie:" + (lie ? 1 : 0)); }
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
            if (gear >= MAX_GEAR && moved.lengthSqr() > 0.04) return; // gear five ploughs on
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
        else if (g > was) {
            // a rush of air, not a firework
            p.playSound(SoundEvents.WIND_CHARGE_BURST.value(), 0.9F, 0.6F);
            p.playSound(SoundEvents.ENDER_DRAGON_FLAP, 0.6F, 1.5F);
            punch = 5;
            shake = 4;
        }
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

    static boolean canLaunch(LocalPlayer p) {
        return p.getAbilities().mayfly && !p.isCreative() && !p.isSpectator() && !p.isPassenger() && ClientCareerState.tag.getInt("dom") >= 2;
    }

    /** From standing to gear five in one go. */
    private static void launch(LocalPlayer p) {
        Vec3 look = p.getLookAngle();
        p.getAbilities().flying = true;
        p.onUpdateAbilities();
        launchedAt = ticks;
        flying = true;
        vel = look.scale(SPEED[MAX_GEAR + 1]);
        // off the ground, never into it: at least a climb, however you're looking
        if (vel.y < 0.9) vel = new Vec3(vel.x, 0.9, vel.z);
        send("pow:fly:launch");
        p.setOnGround(false);
        idle = 0;
        gear = 0;
        shift(p, MAX_GEAR);
        p.playSound(SoundEvents.WIND_CHARGE_BURST.value(), 1.0F, 0.5F);
    }

    /** Gear five: what's in front of you is gone before you reach it (the server does it for real). */
    private static void tear(LocalPlayer p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !p.mayBuild() || ticks - launchedAt < 15) return; // a launch leaves the ground standing
        // only what's ahead of you: skimming over the ground doesn't plough it; diving into it does
        double feet = p.getY() + (vel.y < -0.35 ? -64 : 0.05);
        net.minecraft.world.phys.AABB box = p.getBoundingBox().expandTowards(vel.scale(2.0)).inflate(0.15);
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (net.minecraft.core.BlockPos q : net.minecraft.core.BlockPos.betweenClosed(Mth.floor(box.minX), Mth.floor(box.minY), Mth.floor(box.minZ), Mth.floor(box.maxX), Mth.floor(box.maxY), Mth.floor(box.maxZ))) {
            if (n >= 128) break;
            if (q.getY() + 1 <= feet) continue;
            var st = mc.level.getBlockState(q);
            if (st.isAir() || st.getCollisionShape(mc.level, q).isEmpty() || st.getDestroySpeed(mc.level, q) < 0) continue;
            if (n > 0) sb.append(',');
            sb.append(q.asLong());
            mc.level.addDestroyBlockEffect(q.immutable(), st);
            mc.level.setBlock(q.immutable(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 11);
            n++;
        }
        if (n == 0) return;
        send("pow:fly:tear:" + sb);
        if (ticks % 3 == 0) { p.playSound(SoundEvents.GENERIC_EXPLODE.value(), 0.7F, 0.9F); shake = Math.max(shake, 5); }
    }

    private static void land(LocalPlayer p) {
        flying = false;
        if (FlightState.localLying) { FlightState.localLying = false; send("pow:fly:lie:0"); }
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
        if (mine ? !flying : h < 0.5 && !FlightState.lying(p)) return;
        var ps = event.getPoseStack();
        if (FlightState.lying(p)) {
            // flat already (the lying pose); dip into dives and rise into climbs
            float yaw0 = Mth.rotLerp(pt, p.yRotO, p.getYRot()) * Mth.DEG_TO_RAD;
            float dive = (float) Mth.clamp(Math.atan2(-d.y, Math.max(0.05, h)), -1.3, 1.3);
            ps.pushPose();
            ps.translate(0, 0.3, 0);
            ps.mulPose(new Quaternionf().rotateAxis(dive, Mth.cos(yaw0), 0, Mth.sin(yaw0)));
            ps.translate(0, -0.3, 0);
            LEANED.add(p.getId());
            return;
        }
        if (h < 0.25) return;
        float yaw = Mth.rotLerp(pt, p.yRotO, p.getYRot()) * Mth.DEG_TO_RAD;
        // upright and still slow: a lean into the way you're going (faster, and you lie flat instead)
        float tilt = (float) Mth.clamp((h - 0.25) / 0.8, 0, 1) * 28F;
        tilt = Mth.clamp(tilt - (float) Math.toDegrees(Math.atan2(d.y, Math.max(0.05, h))) * 0.3F, 0F, 40F) * Mth.DEG_TO_RAD;
        ps.pushPose();
        ps.translate(0, p.getBbHeight() / 2, 0);
        ps.mulPose(new Quaternionf().rotateAxis(tilt, Mth.cos(yaw), 0, Mth.sin(yaw)));
        ps.translate(0, -p.getBbHeight() / 2, 0);
        LEANED.add(p.getId());
    }

    private static final java.util.Set<Integer> LEANED = new java.util.HashSet<>();

    // ================================================================== the body

    /** How long each other player has been off the ground and not falling: how we tell they're hovering. */
    private static final java.util.Map<Integer, Integer> AIR = new java.util.HashMap<>();

    /** 0 not flying, 1 upright (hovering, drifting), 2 flat out. */
    static int style(Player p) {
        if (p.isFallFlying() || p.isPassenger() || p.isInWater() || p.isSleeping()) return 0;
        if (FlightState.lying(p)) return 2;
        if (p == Minecraft.getInstance().player) return flying ? 1 : 0;
        return AIR.getOrDefault(p.getId(), 0) > 12 ? 1 : 0;
    }

    @SubscribeEvent
    public static void tickBodies(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { AIR.clear(); return; }
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (Player p : mc.level.players()) {
            seen.add(p.getId());
            if (p != mc.player) {
                boolean hang = !p.onGround() && !p.isInWater() && !p.isFallFlying() && p.getY() - p.yo > -0.3;
                AIR.merge(p.getId(), 1, Integer::sum);
                if (!hang) AIR.put(p.getId(), 0);
            }
            // no walking in mid-air
            if (style(p) > 0) p.walkAnimation.setSpeed(0F);
        }
        AIR.keySet().retainAll(seen);
    }

    /** Called at the end of the model's own posing (mixin.client.FlightModelMixin). */
    public static void pose(net.minecraft.client.model.HumanoidModel<?> m, Player p, float age, float headPitch) {
        int st = style(p);
        if (st == 0) return;
        float sway = Mth.sin(age * 0.07F), sway2 = Mth.sin(age * 0.05F + 1.3F);
        if (st == 2) {
            boolean both = p == Minecraft.getInstance().player ? gear >= 3 : new Vec3(p.getX() - p.xo, p.getY() - p.yo, p.getZ() - p.zo).length() > 3.0;
            // head up, looking where you're going
            m.head.xRot = -1.25F + headPitch * Mth.DEG_TO_RAD * 0.5F;
            // a fist out ahead (both, at the higher gears), the other arm along your side
            m.rightArm.xRot = -Mth.PI + sway * 0.03F;
            m.rightArm.yRot = 0F;
            m.rightArm.zRot = -0.06F;
            if (both) { m.leftArm.xRot = -Mth.PI + sway2 * 0.03F; m.leftArm.yRot = 0F; m.leftArm.zRot = 0.06F; }
            else { m.leftArm.xRot = 0.12F + sway2 * 0.03F; m.leftArm.yRot = 0F; m.leftArm.zRot = -0.1F; }
            // legs straight and together, the left a touch bent
            m.rightLeg.xRot = 0.04F + sway * 0.04F;
            m.leftLeg.xRot = 0.2F + sway2 * 0.04F;
            m.rightLeg.yRot = m.leftLeg.yRot = 0F;
            m.rightLeg.zRot = 0.03F;
            m.leftLeg.zRot = -0.03F;
        } else {
            // hovering: arms loose and a little out, legs hanging, everything drifting slightly
            m.rightArm.xRot = 0.08F + sway * 0.06F;
            m.rightArm.yRot = 0F;
            m.rightArm.zRot = 0.22F + sway2 * 0.04F;
            m.leftArm.xRot = 0.08F - sway * 0.06F;
            m.leftArm.yRot = 0F;
            m.leftArm.zRot = -0.22F - sway2 * 0.04F;
            m.rightLeg.xRot = 0.14F + sway2 * 0.05F;
            m.leftLeg.xRot = -0.04F - sway2 * 0.05F;
            m.rightLeg.yRot = m.leftLeg.yRot = 0F;
            m.rightLeg.zRot = 0.04F;
            m.leftLeg.zRot = -0.04F;
        }
        m.hat.copyFrom(m.head);
    }

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
