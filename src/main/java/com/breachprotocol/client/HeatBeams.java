package com.breachprotocol.client;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.world.FlightState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.Map;

/** Heat vision as you see it: two burning beams from the eyes to wherever they land, and the eyes alight. */
@EventBusSubscriber(modid = BreachProtocol.MODID, value = Dist.CLIENT)
public final class HeatBeams {
    private HeatBeams() {}

    private static final double[] RANGE = {16, 32, 48, 64, 96};
    private static final float[] WIDTH = {0.025F, 0.045F, 0.08F, 0.12F, 0.22F};

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || FlightState.HEAT.isEmpty() && FlightState.EYES.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buf.getBuffer(RenderType.lightning());
        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = ps.last().pose();
        float flick = 0.85F + (float) Math.sin((mc.level.getGameTime() + pt) * 1.7) * 0.15F;
        java.util.Set<Integer> who = new java.util.HashSet<>(FlightState.HEAT.keySet());
        who.addAll(FlightState.EYES);
        for (int id : who) {
            Entity e = mc.level.getEntity(id);
            if (!(e instanceof Player p) || p.isInvisible()) continue;
            Integer beaming = FlightState.HEAT.get(id);
            int tier = beaming == null ? 0 : Math.max(0, Math.min(4, beaming));
            // from where the body is actually drawn this frame (the renderer works from xOld, not xo; at speed the two
            // are a whole tick apart, which left the glow trailing behind the face)
            Vec3 eye = new Vec3(net.minecraft.util.Mth.lerp(pt, p.xOld, p.getX()), net.minecraft.util.Mth.lerp(pt, p.yOld, p.getY()) + p.getEyeHeight(),
                    net.minecraft.util.Mth.lerp(pt, p.zOld, p.getZ()));
            Vec3 dir = p.getViewVector(pt);
            boolean me = p == mc.player && mc.options.getCameraType().isFirstPerson();
            Vec3 side = new Vec3(-dir.z, 0, dir.x);
            side = side.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : side.normalize();
            Vec3 up = side.cross(dir).normalize();
            // the eyes: just in front of the face, a little apart
            // on the skin: the eyes are the face's 3rd and 6th columns, 5th and 6th rows. The player model is drawn at
            // 15/16 scale, so a skin pixel is 0.0586 blocks; the head turns about the neck, 0.214 below the eye height.
            final double PX = 0.9375 / 16;
            Vec3 neck = eye.subtract(0, 0.214, 0);
            Vec3 face = neck.add(up.scale(3 * PX)).add(dir.scale(4 * PX + 0.012));
            Vec3 eyeL = face.add(side.scale(1.5 * PX)), eyeR = face.subtract(side.scale(1.5 * PX));
            // as the Skull King: violet, and from its eye sockets (its eyes already burn; no glow of a man's)
            boolean king = FlightState.SKULL.containsKey(id) && com.breachprotocol.client.skull.SkullRenderer.form(p, pt) >= 0.5F;
            Vec3[] sock = king ? com.breachprotocol.client.skull.SkullRenderer.SOCKETS.get(id) : null;
            if (sock != null) { eyeL = sock[0]; eyeR = sock[1]; }
            float cr = 1F, cg = king ? 0.13F : 0.1F, cb = king ? 0.88F : 0.02F;      // the glow round it
            float kr = 1F, kg = king ? 0.5F : 0.55F, kb = king ? 1F : 0.35F;       // the core
            if (king && beaming == null) continue;
            if (beaming == null) {
                if (!me) for (Vec3 ey : new Vec3[]{eyeL, eyeR}) {
                    glow(m, vc, ey, cam, 0.03F, 1F, 0.75F, 0.6F, 1F);
                    glow(m, vc, ey, cam, 0.07F, 1F, 0.12F, 0.05F, 0.5F * flick);
                }
                continue;
            }
            // where it lands (below 80% it stops at the first block)
            double range = Math.max(64, mc.options.getEffectiveRenderDistance() * 16.0);   // as far as you can see
            Vec3 end = eye.add(dir.scale(range));
            if (tier < 3) {
                BlockHitResult hit = mc.level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, p));
                if (hit.getType() != HitResult.Type.MISS) end = hit.getLocation();
            }
            float w = WIDTH[tier] * flick;
            if (!me && !king) {
                for (Vec3 ey : new Vec3[]{eyeL, eyeR}) {
                    glow(m, vc, ey, cam, king ? 0.06F : 0.03F, 1F, king ? 0.88F : 0.75F, king ? 1F : 0.6F, 1F);                        // the lit pixel
                    glow(m, vc, ey, cam, (king ? 0.16F : 0.11F) + tier * 0.02F, cr, cg, cb, 0.65F);    // its bloom
                }
            }
            // in first person the beams come from just below your view, so they don't fill your sight
            // in first person they start right at your eyes (just under and either side of the view, a hair ahead),
            // threads there and swelling as they go, so they read as coming out of you
            Vec3 sL = me ? eye.add(dir.scale(0.2)).add(side.scale(0.055)).subtract(up.scale(0.045)) : eyeL;
            Vec3 sR = me ? eye.add(dir.scale(0.2)).subtract(side.scale(0.055)).subtract(up.scale(0.045)) : eyeR;
            float s0 = me ? 0.04F : 0.45F;     // how thick at the eye, of the full width
            for (Vec3 s : new Vec3[]{sL, sR}) {
                beam(m, vc, s, end, cam, w * 3.2F * s0, w * 3.2F, cr, cg, cb, 0.35F);   // the glow round it
                beam(m, vc, s, end, cam, w * s0, w, kr, kg, kb, 0.95F);                 // the core
                beam(m, vc, s, end, cam, w * 0.35F * s0, w * 0.35F, 1F, king ? 0.88F : 1F, king ? 1F : 0.85F, 1F);   // the white-hot heart
            }
            glow(m, vc, end, cam, 0.25F + tier * 0.18F, 1F, king ? 0.13F : 0.3F, king ? 0.88F : 0.05F, 0.8F);
        }
        ps.popPose();
        buf.endBatch(RenderType.lightning());
    }

    /** A flat ribbon from a to b, turned to face the camera. */
    /**
     * A beam from a to b, thickening from w0 to w1: in short pieces, each turned to face the camera on its own (one
     * flat ribbon twists edge-on near the eye), more of them near the start where it's closest.
     */
    private static void beam(Matrix4f m, VertexConsumer vc, Vec3 a, Vec3 b, Vec3 cam, float w0, float w1, float r, float g, float bl, float al) {
        Vec3 d = b.subtract(a);
        if (d.lengthSqr() < 1e-6) return;
        int N = 14;
        Vec3 prevP = null, prevN = null;
        for (int i = 0; i <= N; i++) {
            float t = (i / (float) N) * (i / (float) N);
            Vec3 p = a.add(d.scale(t));
            Vec3 n = d.cross(p.subtract(cam));
            if (n.lengthSqr() < 1e-10) n = d.cross(new Vec3(0, 1, 0));
            if (n.lengthSqr() < 1e-10) n = new Vec3(1, 0, 0);
            float w = w0 + (w1 - w0) * (float) Math.sqrt(t);
            n = n.normalize().scale(w);
            if (prevP != null) quad(m, vc, prevP.add(prevN), prevP.subtract(prevN), p.subtract(n), p.add(n), r, g, bl, al);
            prevP = p; prevN = n;
        }
    }

    /** A bright square facing the camera. */
    private static void glow(Matrix4f m, VertexConsumer vc, Vec3 at, Vec3 cam, float s, float r, float g, float b, float a) {
        Vec3 view = at.subtract(cam).normalize();
        Vec3 x = view.cross(new Vec3(0, 1, 0));
        if (x.lengthSqr() < 1e-6) x = new Vec3(1, 0, 0);
        x = x.normalize().scale(s);
        Vec3 y = x.cross(view).normalize().scale(s);
        quad(m, vc, at.add(x).add(y), at.subtract(x).add(y), at.subtract(x).subtract(y), at.add(x).subtract(y), r, g, b, a);
    }

    private static void quad(Matrix4f m, VertexConsumer vc, Vec3 p1, Vec3 p2, Vec3 p3, Vec3 p4, float r, float g, float b, float a) {
        for (Vec3 v : new Vec3[]{p1, p2, p3, p4, p4, p3, p2, p1}) // both sides
            vc.addVertex(m, (float) v.x, (float) v.y, (float) v.z).setColor(r, g, b, a);
    }
}
