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
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || FlightState.HEAT.isEmpty()) return;
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
        for (Map.Entry<Integer, Integer> en : FlightState.HEAT.entrySet()) {
            Entity e = mc.level.getEntity(en.getKey());
            if (!(e instanceof Player p) || p.isInvisible()) continue;
            int tier = Math.max(0, Math.min(4, en.getValue()));
            Vec3 eye = p.getEyePosition(pt), dir = p.getViewVector(pt);
            boolean me = p == mc.player && mc.options.getCameraType().isFirstPerson();
            Vec3 side = new Vec3(-dir.z, 0, dir.x);
            side = side.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : side.normalize();
            Vec3 up = side.cross(dir).normalize();
            // the eyes: just in front of the face, a little apart
            // on the skin: the eyes are the face's 3rd and 6th columns, 5th and 6th rows (a pixel is 1/16 of a block)
            Vec3 face = eye.add(dir.scale(0.255)).add(up.scale(0.0625));
            Vec3 eyeL = face.add(side.scale(0.094)), eyeR = face.subtract(side.scale(0.094));
            // where it lands (below 80% it stops at the first block)
            double range = RANGE[tier];
            Vec3 end = eye.add(dir.scale(range));
            if (tier < 3) {
                BlockHitResult hit = mc.level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, p));
                if (hit.getType() != HitResult.Type.MISS) end = hit.getLocation();
            }
            float w = WIDTH[tier] * flick;
            if (!me) {
                for (Vec3 ey : new Vec3[]{eyeL, eyeR}) {
                    glow(m, vc, ey, cam, 0.035F, 1F, 0.75F, 0.6F, 1F);                       // the lit pixel
                    glow(m, vc, ey, cam, 0.08F + tier * 0.015F, 1F, 0.12F, 0.05F, 0.55F);    // its bloom
                }
            }
            // in first person the beams come from just below your view, so they don't fill your sight
            Vec3 sL = me ? eye.add(dir.scale(0.6)).add(side.scale(0.12)).subtract(up.scale(0.12)) : eyeL;
            Vec3 sR = me ? eye.add(dir.scale(0.6)).subtract(side.scale(0.12)).subtract(up.scale(0.12)) : eyeR;
            for (Vec3 s : new Vec3[]{sL, sR}) {
                beam(m, vc, s, end, cam, w * 3.2F, 1F, 0.1F, 0.02F, 0.35F);   // the glow round it
                beam(m, vc, s, end, cam, w, 1F, 0.55F, 0.35F, 0.95F);         // the core
                if (tier >= 3) beam(m, vc, s, end, cam, w * 0.35F, 1F, 1F, 0.9F, 1F);
            }
            glow(m, vc, end, cam, 0.25F + tier * 0.18F, 1F, 0.3F, 0.05F, 0.8F);
        }
        ps.popPose();
        buf.endBatch(RenderType.lightning());
    }

    /** A flat ribbon from a to b, turned to face the camera. */
    private static void beam(Matrix4f m, VertexConsumer vc, Vec3 a, Vec3 b, Vec3 cam, float w, float r, float g, float bl, float al) {
        Vec3 d = b.subtract(a);
        Vec3 view = a.add(b).scale(0.5).subtract(cam);
        Vec3 n = d.cross(view);
        if (n.lengthSqr() < 1e-8) return;
        n = n.normalize().scale(w);
        quad(m, vc, a.add(n), a.subtract(n), b.subtract(n), b.add(n), r, g, bl, al);
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
