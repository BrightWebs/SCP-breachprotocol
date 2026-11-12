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
    private static final ResourceLocation FLAME = tex("flame"), SOUL = tex("soul_flame"), GROUND = tex("ground"), GROUND_GLOW = tex("ground_glow");

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
            draw(p, rig, pt, event.getPoseStack(), buf, event.getPackedLight(), growing || shrinking ? reveal : 1, real);
        } catch (Throwable t) {
            BreachProtocol.LOGGER.debug("Skull King: {}", t.toString());
        }
    }

    // ================================================================== moving it

    private static final int IDLE = 0, GALLOP = 1, HOVER = 2, FLY = 3;

    private static void draw(Player p, Rig rig, float pt, PoseStack ps, MultiBufferSource buf, int light, float reveal, boolean real) {
        Minecraft mc = Minecraft.getInstance();
        SkullModel m = rig.model;
        float t = (p.tickCount + pt) / 20F;
        float dt = rig.lastFrame < 0 ? 0.05F : Mth.clamp(t - rig.lastT, 0, 0.2F);
        rig.lastT = t; rig.lastFrame = 1;

        double dx = p.getX() - p.xo, dy = p.getY() - p.yo, dz = p.getZ() - p.zo;
        double h = Math.sqrt(dx * dx + dz * dz);
        int st = com.breachprotocol.client.SuperFlight.style(p);
        boolean lying = st == 2 || p.isFallFlying();
        boolean flying = st == 1 || !p.onGround() && p.getAbilities().flying && !p.isInWater();
        // from the third running gear up (a block and a half a tick), down on all fours
        int move = lying ? FLY : flying ? HOVER : p.onGround() && (h > 1.2 || st == 6) ? GALLOP : IDLE;

        float bodyYaw = move == FLY ? Mth.rotLerp(pt, p.yRotO, p.getYRot()) : Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot);
        float headYaw = Mth.rotLerp(pt, p.yHeadRotO, p.yHeadRot);
        float net = Mth.clamp(Mth.wrapDegrees(headYaw - bodyYaw), -70, 70) * Mth.DEG_TO_RAD;
        float pitch = Mth.clamp(Mth.lerp(pt, p.xRotO, p.getXRot()), -60, 60) * Mth.DEG_TO_RAD;

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
        neck.ry = net * 0.3F; head.ry += net * 0.7F;
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

        // ---- the wings
        float k = 1 - (float) Math.pow(1 - 0.12, dt * 60);
        rig.open += (wingO - rig.open) * k;
        poseWing(m.wings[0], rig.open, beat, amp, tilt, wingFly);
        poseWing(m.wings[1], rig.open, beat, amp, tilt, wingFly);

        // ---- the crown's fire, the halo turning, the gem swaying
        m.halo.ry = t * 1.5F; m.halo.y = 11 + Mth.sin(t * 2) * 0.6F;
        m.diamond.ry = Mth.sin(t * 0.8F) * 0.25F;

        // ================================================================== laying it out
        float scale = p.getScale();
        float S = 0.85F / 16F * scale / 1.75F;
        Map<Node, Matrix4f> mats = new IdentityHashMap<>();
        Matrix4f rootM = new Matrix4f();
        if (move == FLY) {
            // about the middle of the body, at the middle of the (low) box the flier has
            rootM.translate(0, p.getBbHeight() / 2F / S, 0).rotateX(rigTilt).translate(0, -30 + rigLiftPx, 0);
        } else rootM.translate(0, lift + rigLiftPx, 0);
        walk(m.root, rootM, mats);
        if (move == GALLOP) {
            // stand on whichever's lowest, fist or foot
            float low = Float.MAX_VALUE;
            Vector3f v = new Vector3f();
            for (SkullModel.Leg lg : m.legs) for (float z : new float[]{1.5F, 6}) low = Math.min(low, mats.get(lg.foot).transformPosition(v.set(0, -2.4F, z)).y);
            for (SkullModel.Arm ar : m.arms) for (float z : new float[]{-4, 4}) low = Math.min(low, mats.get(ar.hand).transformPosition(v.set(0, -6, z)).y);
            mats.clear();
            rootM.translateLocal(0, -low, 0);
            walk(m.root, rootM, mats);
        }

        // model space to entity space: turned to face its way, at its size
        Matrix4f E = new Matrix4f().rotateY(-bodyYaw * Mth.DEG_TO_RAD).scale(S);
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
            for (Part part : n.parts) {
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
                            if (si < 2) sockets[si++] = new Vec3(ce.x, ce.y, ce.z);
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
        // ---- the ground it stands on burns
        if (p.onGround() && reveal >= 1) ground(p, ps, buf, scale, t);
        if (real) {
            if (sockets[0] != null && sockets[1] != null) {
                Vec3 at = new Vec3(Mth.lerp(pt, p.xOld, p.getX()), Mth.lerp(pt, p.yOld, p.getY()), Mth.lerp(pt, p.zOld, p.getZ()));
                SOCKETS.put(p.getId(), new Vec3[]{at.add(sockets[0]), at.add(sockets[1])});
            }
            embers(p, reveal);
        }
    }

    /** Each pivot's place in model space, parent first. */
    private static void walk(Node n, Matrix4f parent, Map<Node, Matrix4f> out) {
        Matrix4f m = new Matrix4f(parent).translate(n.x, n.y, n.z).rotateXYZ(n.rx, n.ry, n.rz).scale(n.sx, n.sy, n.sz);
        out.put(n, m);
        for (Node k : n.kids) walk(k, m, out);
    }

    /** A wing, folded (0) to spread (1), beating; flat out in flight the beat is up and down across the body. */
    private static void poseWing(SkullModel.Wing w, float open, float beat, float amp, float tilt, boolean fly) {
        int s = w.side;
        float flapY = fly ? beat * 0.75F : 0, inPlane = fly ? 0 : 1;
        float o = open * (1 - 0.3F * Math.max(0, -beat) * Math.min(1, amp - 0.5F));
        w.root.rot(0.1 * open + 0.45 * (1 - open) + tilt, s * (1.35 - 1.2 * open - beat * 0.18 * amp * open * inPlane + flapY), -s * 0.12 * (1 - open));
        float ua = -1.45F + 0.85F * o + beat * 0.25F * o * amp * inPlane;
        w.upper.rz = s * ua;
        w.fore.rz = s * (1.9F * o + 2.75F * (1 - o) + beat * 0.1F * o);
        float foreAbs = ua + (1.9F * o + 2.75F * (1 - o));
        for (Node fp : w.feathers) fp.rz = s * (-1.45F * o - 0.12F * (1 - o));
        for (int i = 0; i < w.fingers.size(); i++) {
            double want = SkullModel.SPREAD[i] * o + SkullModel.FOLD[i] * (1 - o) - beat * 0.12 * o * (i / 4.0);
            w.fingers.get(i).rz = (float) (s * (want - foreAbs));
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

    /** Cracked obsidian veined with magma, under its feet, lighting itself. */
    private static void ground(Player p, PoseStack ps, MultiBufferSource buf, float scale, float t) {
        float r = 1.6F * scale / 1.75F;
        Matrix4f m = ps.last().pose();
        float y = 0.02F;
        float[][] c = {{-r, -r}, {-r, r}, {r, r}, {r, -r}};
        float[][] uv = {{0, 0}, {0, 1}, {1, 1}, {1, 0}};
        int a = (int) (200 + Mth.sin(t * 2.3F) * 40);
        for (int layer = 0; layer < 2; layer++) {
            VertexConsumer vc = buf.getBuffer(layer == 0 ? RenderType.entityTranslucent(GROUND) : RenderType.eyes(GROUND_GLOW));
            for (int i = 0; i < 4; i++) {
                Vector3f v = m.transformPosition(new Vector3f(c[i][0], y, c[i][1]));
                vc.addVertex(v.x, v.y, v.z).setColor(255, 255, 255, layer == 1 ? a : 255).setUv(uv[i][0], uv[i][1])
                        .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0, 1, 0);
            }
            y += 0.01F;
        }
    }

    /** Embers off it, and flames licking up round its feet. */
    private static void embers(Player p, float reveal) {
        var l = p.level();
        if (Minecraft.getInstance().isPaused()) return;
        var rnd = l.random;
        float sc = p.getScale();
        if (rnd.nextInt(3) == 0) l.addParticle(ParticleTypes.SMALL_FLAME, p.getX() + (rnd.nextDouble() - 0.5) * sc, p.getY() + rnd.nextDouble() * sc * 1.6, p.getZ() + (rnd.nextDouble() - 0.5) * sc, 0, 0.02, 0);
        if (p.onGround() && reveal >= 1 && rnd.nextInt(4) == 0) {
            double a = rnd.nextDouble() * Math.PI * 2, d = rnd.nextDouble() * 1.4 * sc / 1.75;
            l.addParticle(rnd.nextInt(3) == 0 ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME, p.getX() + Math.cos(a) * d, p.getY() + 0.05, p.getZ() + Math.sin(a) * d, 0, 0.03, 0);
        }
    }
}
