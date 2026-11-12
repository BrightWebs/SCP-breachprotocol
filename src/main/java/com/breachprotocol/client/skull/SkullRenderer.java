package com.breachprotocol.client.skull;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.client.skull.SkullModel.Kind;
import com.breachprotocol.client.skull.SkullModel.Mat;
import com.breachprotocol.client.skull.SkullModel.Node;
import com.breachprotocol.client.skull.SkullModel.Part;
import com.breachprotocol.world.FlightState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SCP-Ω's other shape, the Skull King, drawn in place of the player who has taken it: the bones assembling
 * themselves from the feet up while it grows, then the whole thing, moving the way its wearer moves (walking,
 * running, bounding on all fours when flat out, hovering on beating wings, laid out flat in flight), holding what
 * they hold. Its fire, its gems and its eyes light themselves, day or night.
 */
@EventBusSubscriber(modid = BreachProtocol.MODID, value = Dist.CLIENT)
public final class SkullRenderer {
    private SkullRenderer() {}

    /** How long the change takes, and the change back (ticks); the server's own numbers. */
    public static final int GROW = 70, SHRINK = 24;

    /** Where each Skull King's eye sockets were drawn this frame (world), for heat vision. */
    public static final Map<Integer, Vec3[]> SOCKETS = new ConcurrentHashMap<>();

    private static final Map<Mat, RenderType> SOLID = new HashMap<>(), GLOW = new HashMap<>();
    private static final ResourceLocation FLAME = tex("flame"), SOUL = tex("soul_flame");

    private static ResourceLocation tex(String n) { return ResourceLocation.fromNamespaceAndPath(BreachProtocol.MODID, "textures/entity/skull_king/" + n + ".png"); }

    private static RenderType solid(Mat m) { return SOLID.computeIfAbsent(m, k -> RenderType.entityCutoutNoCull(tex(k.tex))); }
    private static RenderType glow(Mat m) { return GLOW.computeIfAbsent(m, k -> RenderType.eyes(tex(k.glow))); }

    /** Each one's own body, and the little it remembers between frames. */
    private static final class Rig {
        final SkullModel model = new SkullModel();
        float open, lastAttack;
        boolean leftPunch;
        long lastFrame = -1;
        float lastT;
        // the tail: each vertebra's direction in the world (turn, tilt) and how fast it's swinging
        boolean tailSet;
        float lastYaw, drag, turn, restNow, swayNow, sitK, cyc;
    }

    private static final Map<Integer, Rig> RIGS = new HashMap<>();

    /** How far into the shape this player is: 0 not at all, 1 wholly. */
    public static float form(Player p, float pt) {
        long[] s = FlightState.SKULL.get(p.getId());
        if (s == null || p.level() == null) return 0;
        float age = (p.level().getGameTime() - s[1]) + pt;
        return switch ((int) s[0]) {
            case 30 -> Mth.clamp(age / GROW, 0, 1);
            case 31 -> 1;
            case 32 -> Mth.clamp(1 - age / SHRINK, 0, 1);
            default -> 0;
        };
    }

    public static boolean inForm(Player p) { return form(p, 0) >= 0.5F; }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void render(RenderPlayerEvent.Pre event) {
        Player p = event.getEntity();
        if (FlightState.SKULL.isEmpty() || !FlightState.SKULL.containsKey(p.getId())) return;
        float pt = event.getPartialTick();
        float f = form(p, pt);
        long[] s = FlightState.SKULL.get(p.getId());
        if (f <= 0 && s[0] == 32) { FlightState.SKULL.remove(p.getId()); RIGS.remove(p.getId()); SOCKETS.remove(p.getId()); return; }
        // the bones come in from the feet up over the whole change; the man is gone once half of them are there
        boolean growing = s[0] == 30 && f < 1, shrinking = s[0] == 32;
        float reveal = shrinking ? f : Mth.clamp(f * 1.15F, 0, 1);
        if (f >= 0.5F || shrinking) event.setCanceled(true);
        MultiBufferSource buf = event.getMultiBufferSource();
        boolean real = buf == Minecraft.getInstance().renderBuffers().bufferSource();
        try {
            Rig rig = RIGS.computeIfAbsent(p.getId(), k -> new Rig());
            draw(p, rig, pt, event.getPoseStack(), buf, event.getPackedLight(), growing || shrinking ? reveal : 1, real, false);
        } catch (Throwable t) {
            BreachProtocol.LOGGER.debug("Skull King: {}", t.toString());
        }
    }

    /** First person: you see out of the skull, and looking down, the body you're in. */
    @SubscribeEvent
    public static void firstPerson(net.neoforged.neoforge.client.event.RenderLevelStageEvent event) {
        if (event.getStage() != net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_ENTITIES || FlightState.SKULL.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        Player p = mc.player;
        if (p == null || !mc.options.getCameraType().isFirstPerson() || p.isSpectator() || p.isSleeping() || !FlightState.SKULL.containsKey(p.getId())) return;
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float f = form(p, pt);
        long[] s = FlightState.SKULL.get(p.getId());
        if (f < 0.5F && s[0] != 32 || f <= 0) return;
        float reveal = s[0] == 32 ? f : s[0] == 30 && f < 1 ? Mth.clamp(f * 1.15F, 0, 1) : 1;
        Vec3 cam = event.getCamera().getPosition();
        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        ps.translate(Mth.lerp(pt, p.xOld, p.getX()) - cam.x, Mth.lerp(pt, p.yOld, p.getY()) - cam.y, Mth.lerp(pt, p.zOld, p.getZ()) - cam.z);
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        try {
            draw(p, RIGS.computeIfAbsent(p.getId(), k -> new Rig()), pt, ps, buf, mc.getEntityRenderDispatcher().getPackedLightCoords(p, pt), reveal, true, true);
        } catch (Throwable t) {
            BreachProtocol.LOGGER.debug("Skull King (first person): {}", t.toString());
        }
        ps.popPose();
        buf.endBatch();
    }

    /** No man's arm floating in front of you: in this shape your fists are its fists. */
    @SubscribeEvent
    public static void hands(net.neoforged.neoforge.client.event.RenderHandEvent event) {
        Player p = Minecraft.getInstance().player;
        if (p != null && FlightState.SKULL.containsKey(p.getId()) && form(p, event.getPartialTick()) >= 0.5F) event.setCanceled(true);
    }

    // ================================================================== moving it

    private static final int IDLE = 0, GALLOP = 1, HOVER = 2, FLY = 3, PROWL = 4;

    private static void draw(Player p, Rig rig, float pt, PoseStack ps, MultiBufferSource buf, int light, float reveal, boolean real, boolean fp) {
        Minecraft mc = Minecraft.getInstance();
        SkullModel m = rig.model;
        float t = (p.tickCount + pt) / 20F;
        // drawn in a screen (the inventory's little figure, turned to face you): show it, but don't let it move the tail
        boolean gui = mc.screen != null && pt == 1.0F && !fp;
        float dt = gui ? 0 : rig.lastFrame < 0 ? 0.05F : Mth.clamp(t - rig.lastT, 0, 0.2F);
        if (!gui) { rig.lastT = t; rig.lastFrame = 1; }

        double dx = p.getX() - p.xo, dy = p.getY() - p.yo, dz = p.getZ() - p.zo;
        double h = Math.sqrt(dx * dx + dz * dz);
        int st = com.breachprotocol.client.SuperFlight.style(p);
        boolean lying = st == 2 || p.isFallFlying();
        boolean flying = st == 1 || !p.onGround() && p.getAbilities().flying && !p.isInWater();
        // from the third running gear up (a block and a half a tick), down on all fours
        int move = lying ? FLY : flying ? HOVER : p.onGround() && (h > 1.2 || st == 6) ? GALLOP : IDLE;
        // crouching: moving, a lion's prowl; still, it sits down
        boolean crouch = p.isCrouching() && p.onGround() && move == IDLE;
        if (crouch && h > 0.015) move = PROWL;
        if (!gui) rig.sitK += ((crouch && move != PROWL ? 1 : 0) - rig.sitK) * Math.min(1, dt * 2.2F);
        float sitK = rig.sitK;

        float bodyYaw = move == FLY ? Mth.rotLerp(pt, p.yRotO, p.getYRot()) : Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot);
        float headYaw = Mth.rotLerp(pt, p.yHeadRotO, p.yHeadRot);
        float net = Mth.clamp(Mth.wrapDegrees(headYaw - bodyYaw), -70, 70) * Mth.DEG_TO_RAD;
        float pitch = Mth.clamp(Mth.lerp(pt, p.xRotO, p.getXRot()), -60, 60) * Mth.DEG_TO_RAD;

        // every joint back to its rest first, so no pose (the sit's crossed legs, say) is left behind in the next one
        for (SkullModel.Leg lg : m.legs) { lg.thigh.rot(lg.tx, 0, lg.side * -0.04); lg.shin.rot(lg.sx, 0, lg.side * 0.04); lg.foot.rot(-(lg.tx + lg.sx), 0, 0); }
        for (SkullModel.Arm ar : m.arms) { ar.upper.rot(-0.3, 0, ar.side * -0.14); ar.fore.rot(-0.15, 0, ar.side * 0.05); }
        m.jaw.rot(0.18, 0, 0);
        // ---- the resting body: breathing, the jaw working, the head turning about
        Node chest = m.chest, head = m.head, neck = m.neck, spine = m.spine;
        float br = Mth.sin(t * 1.6F);
        chest.sx = 1 + br * 0.012F; chest.sy = 1 + br * 0.01F; chest.sz = 1 + br * 0.02F;
        chest.rot(0.16 + br * 0.02, 0, 0);
        head.rot(-0.2 + Mth.sin(t * 0.7F) * 0.05, Mth.sin(t * 0.45F) * 0.06, 0);
        m.jaw.rx = 0.18F + Math.max(0, Mth.sin(t * 0.9F)) * 0.12F;
        neck.rot(0.1, 0, 0);

        SkullModel.Leg L = m.legs[0], R = m.legs[1];
        SkullModel.Arm A = m.arms[0], B = m.arms[1];   // A: its left, B: its right
        // which fist is the main hand
        boolean leftHanded = p.getMainArm() == HumanoidArm.LEFT;
        SkullModel.Arm main = leftHanded ? A : B, off = leftHanded ? B : A;

        // ---- walking and running: from how far the legs have gone, like any player
        float pos = p.walkAnimation.position(pt), amt = Mth.clamp(p.walkAnimation.speed(pt), 0, 1);
        boolean run = p.isSprinting();
        float leg = amt * (run ? 0.95F : 0.55F), armSwing = amt * (run ? 0.95F : 0.45F), lean = amt * (run ? 0.32F : 0.05F), bob = amt * (run ? 1.3F : 0.6F);
        float ph = pos * 0.6662F * 0.75F;
        for (int i = 0; i < 2; i++) {
            SkullModel.Leg lg = m.legs[i];
            float sgn = i == 0 ? 1 : -1;
            float sw = Mth.sin(ph) * sgn;
            lg.thigh.rx = lg.tx - sw * leg;
            float lift = Math.max(0, Mth.cos(ph) * sgn) * leg;
            lg.shin.rx = lg.sx + lift * 1.3F;
            lg.foot.rx = -(lg.thigh.rx + lg.shin.rx);
        }
        float lift = Math.abs(Mth.sin(ph)) * bob;
        spine.rot(0.14 + lean, 0, 0);
        A.upper.rot(-0.3 + Mth.sin(ph) * armSwing - lean, 0, 0.14);
        B.upper.rot(-0.3 - Mth.sin(ph) * armSwing - lean, 0, -0.14);
        A.fore.rx = B.fore.rx = -0.15F - (run ? 0.9F * amt : 0);
        if (amt < 0.05F) { A.upper.rx += Mth.sin(t * 0.8F) * 0.05F; B.upper.rx -= Mth.sin(t * 0.8F) * 0.05F; }
        chest.ry = 0;

        float wingO = p.onGround() ? 0 : 0.6F, beat = p.onGround() ? 0 : Mth.sin(t * 2.4F) * 0.6F, amp = 1, tilt = 0;
        boolean wingFly = false;
        float rigLiftPx = 0, rigTilt = 0;

        if (move == GALLOP) {
            // flat out on the ground: down on all fours like an animal, the fists for front feet
            float g = pos * 0.6662F * 0.5F;
            float flex = Mth.sin(g) * 0.12F;
            spine.rx = 0.9F + flex; chest.rx = 0.42F + flex; neck.rx = -0.85F; head.rx = -0.45F;
            float tiltBody = spine.rx + chest.rx;
            for (int i = 0; i < 2; i++) {
                SkullModel.Leg lg = m.legs[i];
                float q = g + (i == 0 ? 0 : 0.25F), sw = Mth.sin(q);
                lg.thigh.rx = -0.55F - sw * 0.75F;
                lg.shin.rx = 0.55F + Math.max(0, Mth.cos(q)) * 1.1F;
                lg.foot.rx = -(lg.thigh.rx + lg.shin.rx) + Math.max(0, -sw) * 0.5F;
            }
            for (int i = 0; i < 2; i++) {
                SkullModel.Arm ar = m.arms[i];
                float q = g + Mth.PI + (i == 0 ? 0 : 0.25F), sw = Mth.sin(q);
                ar.upper.rot(-tiltBody - sw * 0.8 + 0.15, 0, ar.side * -0.1);
                ar.fore.rx = -0.15F - Math.max(0, Mth.cos(q)) * 1.2F;
            }
            lift = 0;
            wingO = 0; beat = 0;
        } else if (move == HOVER) {
            // upright in the air, the legs hanging, the wings beating down to hold it up
            float fq = t * 3.4F, b = Mth.sin(fq - 0.35F * Mth.sin(fq));
            rigLiftPx = b * -1.6F + Mth.sin(t * 0.5F) * 2;
            for (SkullModel.Leg lg : m.legs) { lg.thigh.rx = 0.15F + Mth.sin(t * 1.3F + lg.hip.x) * 0.06F; lg.shin.rx = 0.45F; lg.foot.rx = 0.35F; }
            A.upper.rot(-0.15, 0, 0.45); B.upper.rot(-0.15, 0, -0.45); A.fore.rx = B.fore.rx = -0.25F;
            // drifting along: lean into it
            spine.rx = 0.14F + (float) Mth.clamp(h * 0.5, 0, 0.35);
            wingO = 1; beat = b; amp = 2.6F; tilt = 0.55F;
            lift = 0;
        } else if (move == FLY) {
            // laid out flat like a superhero, a fist out in front, legs straight back, the wings driving up and down
            float fq = t * 2.8F, b = Mth.sin(fq - 0.35F * Mth.sin(fq));
            rigLiftPx = b * -1.2F;
            float dive = (float) Mth.clamp(Math.atan2(-dy, Math.max(0.05, h)), -1.0, 1.0);
            rigTilt = 1.35F + dive * 0.6F;
            spine.rx = 0; chest.rx = 0; neck.rx = -0.6F; head.rx = -0.45F;
            for (SkullModel.Leg lg : m.legs) { lg.thigh.rx = 0.05F; lg.shin.rx = 0.12F; lg.foot.rx = 0.6F; }
            main.upper.rot(-2.9, 0, main.side * 0.05); main.fore.rx = 0;
            off.upper.rot(-0.15, 0, off.side * 0.12); off.fore.rx = -0.2F;
            wingO = 1; beat = b; amp = 2.2F; tilt = -0.15F; wingFly = true;
            lift = 0;
        }

        // ---- the head follows where they look
        neck.ry = -net * 0.3F; head.ry -= net * 0.7F;
        if (move != FLY) { neck.rx += pitch * 0.3F; head.rx += pitch * 0.7F; } else head.rx += pitch * 0.4F;

        // ---- hands: fists one after the other, a swung weapon, a drawn bow, a raised shield, eating
        float atk = p.getAttackAnim(pt);
        ItemStack mainItem = p.getMainHandItem(), offItem = p.getOffhandItem();
        if (atk > 0 && rig.lastAttack == 0 || atk < rig.lastAttack - 0.3F) rig.leftPunch = !rig.leftPunch;
        rig.lastAttack = atk;
        if (atk > 0 && move != FLY) {
            SkullModel.Arm swingArm = p.swingingArm == InteractionHand.OFF_HAND ? off : main;
            if (mainItem.isEmpty() && p.swingingArm != InteractionHand.OFF_HAND) {
                SkullModel.Arm which = rig.leftPunch ? off : main, other = which == main ? off : main;
                float thrust = atk < 0.35F ? atk / 0.35F : 1 - (atk - 0.35F) / 0.65F;
                which.upper.rot(-0.35 - 1.25 * thrust, 0, which.side * -0.1);
                which.fore.rx = -0.6F + 0.55F * thrust;
                if (move != GALLOP) { other.upper.rot(-0.7, 0, other.side * -0.18); other.fore.rx = -1.0F; }
                chest.ry = (which == main ? 1 : -1) * 0.22F * thrust * (main == B ? 1 : -1);
                if (move != GALLOP) spine.rx = Math.max(spine.rx, 0.22F);
            } else {
                float k = atk;
                float a = k < 0.35F ? -2.6F * (k / 0.35F) : -2.6F + 3.0F * Math.min(1, (k - 0.35F) / 0.2F);
                swingArm.upper.rx = a;
                swingArm.upper.rz = swingArm.side * (0.14F - (k < 0.55F ? 0.3F : 0));
                swingArm.fore.rx = -0.4F;
            }
        }
        if (p.isUsingItem() && move != FLY) {
            boolean usingMain = p.getUsedItemHand() == InteractionHand.MAIN_HAND;
            SkullModel.Arm u = usingMain ? main : off, o = u == main ? off : main;
            float mir = u == B ? 1 : -1;   // the poses are written for the right hand
            UseAnim anim = p.getUseItem().getUseAnimation();
            switch (anim) {
                case BOW, CROSSBOW, SPEAR -> {
                    u.upper.rot(-1.55, 0.15 * mir, -0.1 * mir); u.fore.rx = -0.05F;
                    o.upper.rot(-1.45, -0.5 * mir, 0.2 * mir); o.fore.rx = -0.7F - Mth.sin(t * 1.5F) * 0.15F;
                }
                case BLOCK -> { u.upper.rot(-1.1, -0.5 * mir, -0.1 * mir); u.fore.rx = -0.9F; o.upper.rx = -0.35F; }
                case EAT, DRINK -> { u.upper.rot(-1.3 + Mth.sin(t * 14) * 0.08, -0.5 * mir, 0); u.fore.rx = -1.1F; }
                case SPYGLASS, TOOT_HORN -> { u.upper.rot(-1.6, -0.4 * mir, 0); u.fore.rx = -1.3F; }
                default -> {}
            }
        }

        float dropPx = 0;
        if (move == PROWL) {
            if (!gui) rig.cyc += dt * 0.85F;
            dropPx = prowl(m, rig.cyc);
            wingO = 0; beat = 0; lift = 0;
        }
        if (sitK > 0.002F) {
            // sitting down cross-legged, a fist on the knee, the jaw on the other fist; settling into it, and out
            float sk = sitK * sitK * (3 - 2 * sitK);
            lr(spine, 0.3F, 0, 0, sk); lr(chest, 0.2F + br * 0.015F, -0.12F, 0, sk); lr(neck, 0, 0, 0, sk); lr(head, -0.47F + Mth.sin(t * 0.5F) * 0.015F, 0.06F, 0, sk);
            lr(L.thigh, -1.753F, 0, 0.376F, sk); lr(L.shin, -0.198F, 0.138F, -2.274F, sk); lr(L.foot, 1.826F, 0.663F, -0.531F, sk);
            lr(R.thigh, -1.857F, 0, -0.382F, sk); lr(R.shin, 0.049F, 0, 1.897F, sk); lr(R.foot, 1.68F, -0.305F, 0.251F, sk);
            lr(A.upper, 1.113F, -0.886F, 1.866F, sk); lr(A.fore, -1.973F, 0, 0, sk);
            lr(B.upper, -1.072F, 1.013F, 0, sk); lr(B.fore, -1.882F, 0, 0, sk);
            m.jaw.rx += (0.05F - m.jaw.rx) * sk;
            dropPx = 21 * sk; lift *= 1 - sk;
            if (sk > 0.5F) { wingO = 0; beat = 0; }
        }

        // ---- the wings
        float k = 1 - (float) Math.pow(1 - 0.12, dt * 60);
        rig.open += (wingO - rig.open) * k;
        poseWing(m.wings[0], rig.open, beat, amp, tilt, wingFly);
        poseWing(m.wings[1], rig.open, beat, amp, tilt, wingFly);

        // ---- the crown's fire, the halo turning, the gem swaying
        m.halo.ry = t * 1.5F; m.halo.y = 11 + Mth.sin(t * 2) * 0.6F;
        m.diamond.ry = Mth.sin(t * 0.8F) * 0.25F;

        // ---- the tail, by its physics
        float restP = move == FLY ? -1.35F : move == GALLOP ? -0.35F : move == PROWL ? -0.55F : -0.7F * (1 - sitK) - 0.3F * sitK;
        float walkSway = move == PROWL ? Mth.sin(t * 1.6F) * 0.22F : (1 - sitK) * (amt > 0.15F || move == GALLOP ? (move == GALLOP ? 0.14F : 0.1F * amt) * Mth.sin(ph * 0.5F) : 0) + Mth.sin(t * 0.9F) * 0.04F;
        tail(rig, dt, -bodyYaw * Mth.DEG_TO_RAD, move == FLY ? rigTilt : 0, restP, walkSway, dx, dy, dz, t, sitK);

        // ================================================================== laying it out
        float scale = p.getScale();
        float S = 0.85F / 16F * scale / 1.75F;
        Map<Node, Matrix4f> mats = new IdentityHashMap<>();
        Matrix4f rootM = new Matrix4f();
        if (move == FLY) {
            // about the middle of the body, at the middle of the (low) box the flier has
            rootM.translate(0, p.getBbHeight() / 2F / S, 0).rotateX(rigTilt).translate(0, -30 + rigLiftPx, 0);
        } else rootM.translate(0, lift + rigLiftPx - dropPx, 0);
        walk(m.root, rootM, mats);
        if (move == GALLOP || move == PROWL || sitK > 0.02F) {
            // on the ground, never in it: every corner of every bone (not the loose feathers) is checked, and the
            // lowest one rests exactly on top of the ground, legs crossed, tail, paws and all
            float low = Float.MAX_VALUE;
            Vector3f v = new Vector3f();
            for (Map.Entry<Node, Matrix4f> en : mats.entrySet()) {
                if (m.wingNodes.contains(en.getKey())) continue;
                for (Part part : en.getKey().parts) {
                    if (part.kind != Kind.BOX) continue;
                    Matrix4f pm = new Matrix4f(en.getValue()).translate(part.x, part.y, part.z).rotateXYZ(part.rx, part.ry, part.rz);
                    for (int c = 0; c < 8; c++) low = Math.min(low, pm.transformPosition(v.set((c & 4) != 0 ? part.w / 2 : -part.w / 2, (c & 2) != 0 ? part.h / 2 : -part.h / 2, (c & 1) != 0 ? part.d / 2 : -part.d / 2)).y);
                }
            }
            mats.clear();
            rootM.translateLocal(0, -low, 0);
            walk(m.root, rootM, mats);
        }

        // model space to entity space: turned to face its way, at its size
        // the game lowers a crouching player's body by two of its pixels; this one crouches in its own way, standing on the ground
        float crouchLift = !fp && p.isCrouching() ? scale * 2 / 16F : 0;
        Matrix4f E = new Matrix4f().translate(0, crouchLift, 0).rotateY(-bodyYaw * Mth.DEG_TO_RAD).scale(S);
        Vector3f shift = new Vector3f();
        if (fp) {
            // in first person you look out of its head: the body's laid out so the skull's where your eyes are
            Vector3f ho = E.transformPosition(mats.get(m.head).transformPosition(new Vector3f()));
            Vector3f up = E.transformDirection(new Vector3f(0, 6.9F, 3));
            shift.set(-ho.x - up.x, (p.getEyeHeight() - ho.y - up.y) * (move == PROWL ? 0 : 1 - sitK), -ho.z - up.z);
            ps.pushPose();
            ps.translate(shift.x, shift.y, shift.z);
        }
        PoseStack.Pose base = ps.last();
        Matrix4f B4 = new Matrix4f(base.pose()).mul(E);
        Matrix3f B3 = new Matrix3f(base.normal()).mul(new Matrix3f(E).normal());
        int overlay = LivingEntityRenderer.getOverlayCoords(p, 0);
        Quaternionf cam = mc.getEntityRenderDispatcher().cameraOrientation();
        Vector3f camR = cam.transform(new Vector3f(1, 0, 0)), camU = cam.transform(new Vector3f(0, 1, 0));
        float N = m.partCount;
        Vec3[] sockets = new Vec3[2];
        int si = 0;

        for (Map.Entry<Node, Matrix4f> en : mats.entrySet()) {
            Node n = en.getKey();
            boolean hidden = fp && m.headNodes.contains(n);
            for (Part part : n.parts) {
                if (hidden && part.kind != Kind.EYE) continue;
                // assembling: each piece swells into place in its turn, feet first
                float grow = reveal >= 1 ? 1 : Mth.clamp((reveal * (N + 24) - part.order) / 24F, 0, 1);
                if (grow <= 0) continue;
                Matrix4f M = new Matrix4f(B4).mul(en.getValue());
                switch (part.kind) {
                    case BOX, PLANE, OCTA -> {
                        Matrix4f pm = new Matrix4f().translate(part.x, part.y, part.z).rotateXYZ(part.rx, part.ry, part.rz).scale(grow);
                        Matrix4f full = new Matrix4f(M).mul(pm);
                        Matrix3f nrm = new Matrix3f(B3).mul(new Matrix3f(new Matrix4f(en.getValue()).mul(pm)).normal());
                        if (part.kind == Kind.BOX) {
                            for (int f = 0; f < 6; f++) {
                                Mat mat = part.faces[f];
                                face(buf.getBuffer(solid(mat)), full, nrm, part, f, mat.bright ? LightTexture.FULL_BRIGHT : light, overlay, 255);
                                if (mat.glow != null) face(buf.getBuffer(glow(mat)), full, nrm, part, f, LightTexture.FULL_BRIGHT, overlay, 255);
                            }
                        } else if (part.kind == Kind.PLANE) {
                            Mat mat = part.faces[0];
                            plane(buf.getBuffer(solid(mat)), full, nrm, part, light, overlay);
                            plane(buf.getBuffer(glow(mat)), full, nrm, part, LightTexture.FULL_BRIGHT, overlay);
                        } else octa(buf.getBuffer(solid(part.faces[0])), full, nrm, part, overlay);
                    }
                    case FLAME, EYE -> {
                        Vector3f c = en.getValue().transformPosition(new Vector3f(part.x, part.y, part.z));
                        Vector3f ce = E.transformPosition(new Vector3f(c));            // entity space (world axes, from the feet)
                        float fk, w, hh, rise;
                        if (part.kind == Kind.EYE) {
                            fk = 0.8F + Mth.sin(t * 9 + part.x) * 0.2F;
                            w = 2.2F * fk * 1.15F; hh = 4.2F * fk * 1.15F; rise = 0;
                            if (si < 2) sockets[si++] = new Vec3(ce.x + shift.x, ce.y + shift.y - crouchLift, ce.z + shift.z);
                            if (hidden) continue;
                        } else {
                            fk = 0.75F + Mth.sin(t * 9 + part.phase) * 0.2F + Mth.sin(t * 15 + part.phase * 2) * 0.08F;
                            boolean wingFlame = part.flameSize == 2.6F;
                            float sz = part.flameSize;
                            if (wingFlame) { w = 2.6F * fk; hh = 4.2F * fk; rise = 0; }
                            else { w = sz * 0.55F * fk; hh = sz * fk; rise = sz * 0.4F * fk; }
                        }
                        w *= grow * S; hh *= grow * S; rise *= S;
                        billboard(buf.getBuffer(RenderType.eyes(part.kind == Kind.EYE ? SOUL : FLAME)), base.pose(), ce, camR, camU, w, hh, rise, overlay);
                    }
                }
            }
        }
        // ---- what's in its hands, in its fists
        if (reveal >= 0.85F) {
            hand(p, main, mainItem, main == A, mats, base, E, buf, light);
            hand(p, off, offItem, off == A, mats, base, E, buf, light);
        }
        if (real && !gui) {
            if (sockets[0] != null && sockets[1] != null) {
                Vec3 at = new Vec3(Mth.lerp(pt, p.xOld, p.getX()), Mth.lerp(pt, p.yOld, p.getY()), Mth.lerp(pt, p.zOld, p.getZ()));
                SOCKETS.put(p.getId(), new Vec3[]{at.add(sockets[0]), at.add(sockets[1])});
            }
            if (!fp) embers(p, reveal);
        }
        if (fp) ps.popPose();
    }

    /** Each pivot's place in model space, parent first. */
    private static void walk(Node n, Matrix4f parent, Map<Node, Matrix4f> out) {
        Matrix4f m = new Matrix4f(parent).translate(n.x, n.y, n.z).rotateXYZ(n.rx, n.ry, n.rz).scale(n.sx, n.sy, n.sz);
        out.put(n, m);
        for (Node k : n.kids) walk(k, m, out);
    }

    private static void lr(Node n, float x, float y, float z, float k) { n.rx += (x - n.rx) * k; n.ry += (y - n.ry) * k; n.rz += (z - n.rz) * k; }

    /** Two bones from base to target, in the body's side plane (y up, z forward); angles from straight down, + swung back. */
    private static float[] ik2(float by, float bz, float ty, float tz, float L1, float L2, float parentPitch, boolean kneeForward) {
        float dy = ty - by, dz = tz - bz;
        float d = (float) Math.hypot(dy, dz);
        d = Mth.clamp(d, Math.abs(L1 - L2) + 0.05F, L1 + L2 - 0.05F);
        float at = (float) Math.atan2(-dz, -dy);
        float b = (float) Math.acos(Mth.clamp((L1 * L1 + d * d - L2 * L2) / (2 * L1 * d), -1, 1));
        float bend = Mth.PI - (float) Math.acos(Mth.clamp((L1 * L1 + L2 * L2 - d * d) / (2 * L1 * L2), -1, 1));
        float a1 = kneeForward ? at - b : at + b, a2 = kneeForward ? a1 + bend : a1 - bend;
        return new float[]{a1 - parentPitch, a2 - a1, a2};
    }

    /** Where a foot is through its step (u 0..1): {along the stride, off the ground}. Planted, then swung forward in an arc. */
    private static float[] step(float u, float stride, float lift, float duty) {
        if (u < duty) return new float[]{stride * (0.5F - u / duty), 0};
        float k = (u - duty) / (1 - duty), e = k * k * (3 - 2 * k);
        return new float[]{stride * (-0.5F + e), lift * Mth.sin(Mth.PI * k)};
    }

    /**
     * Crouching, moving: a lion's walk. The back long and level, the head low; every foot set down, carried back
     * under the body, lifted and swung forward (back left, front left, back right, front right), the legs worked out
     * from where the feet are: back knees forward, feet set behind the hips, on its toes; front legs straight under
     * the shoulders when they bear weight. Returns how far the body's lowered (pixels).
     */
    private static float prowl(SkullModel m, float cyc) {
        float DROP = 2.5F, duty = 0.62F;
        float bob = Mth.sin(cyc * Mth.PI * 4) * 0.35F;
        float ground = DROP - bob;
        m.spine.rot(0.98 + Mth.sin(cyc * Mth.PI * 4 + 0.6F) * 0.03, 0, 0);
        m.chest.rot(0.42, Mth.sin(cyc * Mth.PI * 2) * 0.06, Mth.sin(cyc * Mth.PI * 2) * 0.03);
        m.neck.rot(-0.95, 0, 0);
        m.head.rot(-0.2 + Mth.sin(cyc * Mth.PI * 4) * 0.02, -Mth.sin(cyc * Mth.PI * 2) * 0.07, 0);
        for (int i = 0; i < 2; i++) {
            SkullModel.Leg lg = m.legs[i];
            float u = ((cyc + (i == 0 ? 0 : 0.5F)) % 1 + 1) % 1;
            float[] f = step(u, 16, 5, duty);
            float[] r = ik2(lg.hip.y, lg.hip.z, ground + 5.2F + f[1], -4.5F + f[0], 10, 10, 0, true);
            lg.thigh.rot(r[0], 0, lg.side * -0.06);
            lg.shin.rot(r[1], 0, 0);
            float toe = u < duty ? 0.35F : 0.35F + 0.5F * Mth.sin(Mth.PI * (u - duty) / (1 - duty));
            lg.foot.rot(toe - r[2], 0, 0);
        }
        // where the shoulders are, now the chest's tipped forward
        Map<Node, Matrix4f> at = new IdentityHashMap<>();
        walk(m.root, new Matrix4f(), at);
        float pitch = m.spine.rx + m.chest.rx;
        for (int i = 0; i < 2; i++) {
            SkullModel.Arm ar = m.arms[i];
            float u = ((cyc + (i == 0 ? 0.25F : 0.75F)) % 1 + 1) % 1;
            float[] f = step(u, 16, 5.5F, duty);
            Vector3f sh = at.get(ar.upper).transformPosition(new Vector3f());
            float[] r = ik2(sh.y, sh.z, ground + f[1] + 0.3F, sh.z + 1 + f[0], 14, 20, pitch, false);
            ar.upper.rot(r[0], 0, ar.side * -0.06);
            ar.fore.rot(r[1], 0, 0);
        }
        return DROP - bob;
    }

    /** Eases v to a stop at lo, instead of a hard wall. */
    private static float soft(float v, float lo) { float k = 0.18F; return v > lo + k ? v : lo + k * (float) Math.exp((v - lo - k) / k); }

    /** A wing, folded (0) to spread (1), beating; flat out in flight the beat is up and down above the back. Never through the body. */
    private static void poseWing(SkullModel.Wing w, float open, float beat, float amp, float tilt, boolean fly) {
        int s = w.side;
        float flapY = fly ? beat * 0.75F : 0, inPlane = fly ? 0 : 1;
        float o = open * (1 - 0.3F * Math.max(0, -beat) * Math.min(1, amp - 0.5F));
        float ry = 1.35F - 1.2F * open - beat * 0.18F * amp * open * inPlane + flapY + (fly ? 0.4F * open : 0);
        ry = soft(ry, 0.22F);
        w.root.rot(0.1 * open + 0.45 * (1 - open) + tilt, s * ry, -s * 0.12 * (1 - open));
        float ua = -1.45F + 1.0F * o + beat * 0.25F * o * amp * inPlane + (inPlane > 0 && amp > 1.5F ? 0.2F * o : 0);
        if (o > 0.5F) ua = soft(ua, -0.7F);
        w.upper.rz = s * ua;
        float fr = 1.9F * o + 2.75F * (1 - o) + beat * 0.1F * o - 0.55F * o;
        w.fore.rz = s * fr;
        float foreAbs = ua + fr;
        float handAbs = 0.3F * o - 1.35F * (1 - o) - beat * 0.12F * o;
        w.hand.rz = s * (handAbs - foreAbs);
        float[] abs = {ua, foreAbs, handAbs, handAbs - 0.12F};
        // every feather points where it should whatever its bone's doing: spread, the fan; folded, all hanging straight down
        for (Node fp : w.feathers) {
            float want = fp.a * o + fp.fold * (1 - o) - beat * 0.08F * o * (fp.b / 3F);
            fp.rz = s * (want - abs[fp.b]);
        }
    }

    private static float wrap(float a) { return (float) Mth.wrapDegrees(a * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD; }

    /**
     * The tail, as it first was: drooping back from the pelvis (lifted level in a gallop, straight back in flight),
     * a slow sway running down it, livelier on the move. Sitting, it curls round beside the legs to the front.
     * The pose it's in eases to the next, so nothing jumps.
     */
    private static void tail(Rig rig, float dt, float yawNow, float pitch0, float restP, float sway, double dx, double dy, double dz, float t, float sitK) {
        if (!rig.tailSet) { rig.restNow = restP; rig.tailSet = true; }
        if (dt > 0) rig.restNow += (restP - rig.restNow) * (1 - (float) Math.exp(-4 * dt));
        boolean moving = Math.sqrt(dx * dx + dz * dz) > 0.03;
        for (int i = 0; i < SkullModel.TAIL; i++) {
            Node v = rig.model.tail.get(i);
            float base = i == 0 ? rig.restNow : (i > 13 ? 0.05F + 0.12F * sitK : 0.05F * (1 - sitK));
            v.rx = base + (i == 0 ? 0 : Mth.sin(t * 1.4F - i * 0.35F) * 0.03F);
            v.ry = Mth.sin(t * 1.1F - i * 0.4F) * (0.05F + i * 0.004F) * (moving ? 1.6F : 1) * (1 - sitK) + 0.17F * sitK
                    + (i == SkullModel.TAIL - 1 ? Mth.sin(t * 1.3F) * 0.25F * sitK : 0);
            v.rz = 0;
        }
    }

    // ================================================================== drawing it

    // a box's corners, and which four make each face (+x, -x, +y, -y, +z, -z), wound outward
    private static final int[][] FACES = {{5, 4, 6, 7}, {0, 1, 3, 2}, {3, 7, 6, 2}, {0, 4, 5, 1}, {1, 5, 7, 3}, {4, 0, 2, 6}};
    private static final float[][] FN = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
    private static final float[][] UV = {{0, 1}, {1, 1}, {1, 0}, {0, 0}};

    private static void face(VertexConsumer vc, Matrix4f m, Matrix3f n, Part b, int f, int light, int overlay, int alpha) {
        float hx = b.w / 2, hy = b.h / 2, hz = b.d / 2;
        Vector3f nn = n.transform(new Vector3f(FN[f][0], FN[f][1], FN[f][2])).normalize();
        Vector3f v = new Vector3f();
        for (int i = 0; i < 4; i++) {
            int c = FACES[f][i];
            v.set((c & 4) != 0 ? hx : -hx, (c & 2) != 0 ? hy : -hy, (c & 1) != 0 ? hz : -hz);
            m.transformPosition(v);
            vc.addVertex(v.x, v.y, v.z).setColor(255, 255, 255, alpha).setUv(UV[i][0], UV[i][1]).setOverlay(overlay).setLight(light).setNormal(nn.x, nn.y, nn.z);
        }
    }

    /** A feather: flat, both sides. */
    private static void plane(VertexConsumer vc, Matrix4f m, Matrix3f n, Part b, int light, int overlay) {
        float hx = b.w / 2, hy = b.h / 2;
        float[][] c = {{-hx, -hy}, {hx, -hy}, {hx, hy}, {-hx, hy}};
        float[][] uv = {{0, 1}, {1, 1}, {1, 0}, {0, 0}};
        Vector3f nn = n.transform(new Vector3f(0, 0, 1)).normalize();
        Vector3f v = new Vector3f();
        for (int side = 0; side < 2; side++) for (int i = 0; i < 4; i++) {
            int j = side == 0 ? i : 3 - i;
            v.set(c[j][0], c[j][1], side == 0 ? 0.01F : -0.01F);
            m.transformPosition(v);
            float s = side == 0 ? 1 : -1;
            vc.addVertex(v.x, v.y, v.z).setColor(255, 255, 255, 255).setUv(uv[j][0], uv[j][1]).setOverlay(overlay).setLight(light).setNormal(nn.x * s, nn.y * s, nn.z * s);
        }
    }

    /** The crown's diamond. */
    private static void octa(VertexConsumer vc, Matrix4f m, Matrix3f n, Part b, int overlay) {
        float r = b.w;
        Vector3f[] pts = {new Vector3f(r, 0, 0), new Vector3f(-r, 0, 0), new Vector3f(0, r, 0), new Vector3f(0, -r, 0), new Vector3f(0, 0, r), new Vector3f(0, 0, -r)};
        int[][] tris = {{0, 2, 4}, {4, 2, 1}, {1, 2, 5}, {5, 2, 0}, {4, 3, 0}, {1, 3, 4}, {5, 3, 1}, {0, 3, 5}};
        for (int[] tr : tris) {
            Vector3f a = new Vector3f(pts[tr[0]]), bb = new Vector3f(pts[tr[1]]), c = new Vector3f(pts[tr[2]]);
            Vector3f nn = n.transform(new Vector3f(a).add(bb).add(c)).normalize();
            Vector3f[] q = {a, bb, c, c};
            float[][] uv = {{0, 1}, {1, 1}, {0.5F, 0}, {0.5F, 0}};
            for (int i = 0; i < 4; i++) {
                Vector3f v = m.transformPosition(new Vector3f(q[i]));
                vc.addVertex(v.x, v.y, v.z).setColor(255, 255, 255, 255).setUv(uv[i][0], uv[i][1]).setOverlay(overlay).setLight(LightTexture.FULL_BRIGHT).setNormal(nn.x, nn.y, nn.z);
            }
        }
    }

    /** A flame, turned to whoever's looking. */
    private static void billboard(VertexConsumer vc, Matrix4f base, Vector3f c, Vector3f r, Vector3f u, float w, float h, float rise, int overlay) {
        float hw = w / 2, hh = h / 2;
        float[][] corner = {{-hw, -hh}, {hw, -hh}, {hw, hh}, {-hw, hh}};
        float[][] uv = {{0, 1}, {1, 1}, {1, 0}, {0, 0}};
        for (int i = 0; i < 4; i++) {
            Vector3f v = new Vector3f(c).add(new Vector3f(r).mul(corner[i][0])).add(new Vector3f(u).mul(corner[i][1] + rise));
            base.transformPosition(v);
            vc.addVertex(v.x, v.y, v.z).setColor(255, 255, 255, 255).setUv(uv[i][0], uv[i][1]).setOverlay(overlay).setLight(LightTexture.FULL_BRIGHT).setNormal(0, 1, 0);
        }
    }

    /** What it holds sits in its fist the way it sits in any player's hand, at the size of its hand. */
    private static void hand(Player p, SkullModel.Arm arm, ItemStack stack, boolean left, Map<Node, Matrix4f> mats, PoseStack.Pose base, Matrix4f E, MultiBufferSource buf, int light) {
        if (stack.isEmpty()) return;
        PoseStack ps = new PoseStack();
        ps.last().pose().set(base.pose());
        ps.last().normal().set(base.normal());
        Matrix4f at = new Matrix4f(E).mul(mats.get(arm.hand)).translate(0, -6, 4);
        ps.last().pose().mul(at);
        ps.last().normal().mul(new Matrix3f(at).normal());
        // into the game's own arm space (y down the arm, facing -z), at this fist's size (36 of its pixels to the game's 16)
        ps.mulPose(Axis.XP.rotationDegrees(180));
        ps.scale(36, 36, 36);
        ps.mulPose(Axis.XP.rotationDegrees(-90));
        ps.mulPose(Axis.YP.rotationDegrees(180));
        Minecraft.getInstance().getEntityRenderDispatcher().getItemInHandRenderer().renderItem(p, stack,
                left ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, left, ps, buf, light);
    }

    /** Embers off it, and flames licking up round its feet. */
    private static void embers(Player p, float reveal) {
        var l = p.level();
        if (Minecraft.getInstance().isPaused()) return;
        var rnd = l.random;
        float sc = p.getScale();
        if (rnd.nextInt(3) == 0) l.addParticle(ParticleTypes.SMALL_FLAME, p.getX() + (rnd.nextDouble() - 0.5) * sc, p.getY() + rnd.nextDouble() * sc * 1.6, p.getZ() + (rnd.nextDouble() - 0.5) * sc, 0, 0.02, 0);
    }
}
