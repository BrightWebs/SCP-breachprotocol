package com.breachprotocol.client;

import com.breachprotocol.BreachProtocol;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Quaternionf;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Running ablaze leaves blurs behind: see-through after-images of the runner strung out along the way they came,
 * each a step further back and fainter, mid-stride where they were, gone in a blink once you slow.
 */
@EventBusSubscriber(modid = BreachProtocol.MODID, value = Dist.CLIENT)
public final class AfterImages {
    private AfterImages() {}

    /** Where the runner was, and how, one tick at a time. */
    private record Snap(Vec3 pos, float bodyYaw, float headYaw, float headPitch, float limbPos, float limbSpeed, float age, boolean wall, float face) {}

    private static final int KEEP = 10;          // ticks of path kept
    private static final double SPACING = 1.6;   // blocks between images
    private static final int IMAGES = 6;

    private static final Map<Integer, Deque<Snap>> PATHS = new HashMap<>();
    /** How long since each runner stopped running ablaze (the trail fades out over a few ticks). */
    private static final Map<Integer, Integer> FADE = new HashMap<>();

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) { if (mc.level == null) { PATHS.clear(); FADE.clear(); } return; }
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (Player p : mc.level.players()) {
            int id = p.getId();
            seen.add(id);
            if (SuperFlight.ablaze(p)) {
                FADE.put(id, 0);
                Deque<Snap> d = PATHS.computeIfAbsent(id, k -> new ArrayDeque<>());
                float face = (p == mc.player ? p.getYRot() : p.getYHeadRot()) * Mth.DEG_TO_RAD;
                d.addFirst(new Snap(p.position(), p.yBodyRot, p.getYHeadRot(), p.getXRot(), p.walkAnimation.position(), p.walkAnimation.speed(), p.tickCount,
                        SuperFlight.wallRunning(p), face));
                while (d.size() > KEEP) d.removeLast();
            } else if (PATHS.containsKey(id)) {
                int f = FADE.merge(id, 1, Integer::sum);
                Deque<Snap> d = PATHS.get(id);
                if (!d.isEmpty()) d.removeLast(); // the tail catches up with you
                if (f > 6 || d.isEmpty()) { PATHS.remove(id); FADE.remove(id); }
            }
        }
        PATHS.keySet().retainAll(seen);
        FADE.keySet().retainAll(seen);
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || PATHS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Vec3 cam = event.getCamera().getPosition();
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        PoseStack ps = event.getPoseStack();
        for (Map.Entry<Integer, Deque<Snap>> e : PATHS.entrySet()) {
            if (!(mc.level.getEntity(e.getKey()) instanceof AbstractClientPlayer p) || p.isInvisible()) continue;
            if (!(mc.getEntityRenderDispatcher().getRenderer(p) instanceof PlayerRenderer r)) continue;
            Snap[] path = e.getValue().toArray(new Snap[0]);
            if (path.length < 2) continue;
            int fade = FADE.getOrDefault(e.getKey(), 0);
            Vec3 now = p.getPosition(pt);
            try {
                draw(ps, buffers, r, p, path, now, cam, fade);
            } catch (Throwable t) {
                BreachProtocol.LOGGER.debug("After-images: {}", t.toString());
            }
        }
        buffers.endBatch();
    }

    /** Lays the images along the path, SPACING apart, measured back from where the runner is now. */
    private static void draw(PoseStack ps, MultiBufferSource buffers, PlayerRenderer r, AbstractClientPlayer p, Snap[] path, Vec3 now, Vec3 cam, int fade) {
        PlayerModel<AbstractClientPlayer> model = r.getModel();
        RenderType type = RenderType.entityTranslucent(p.getSkin().texture());
        boolean firstPerson = p == Minecraft.getInstance().player && Minecraft.getInstance().options.getCameraType().isFirstPerson();
        double want = SPACING, walked = 0;
        Vec3 prev = now;
        int drawn = 0;
        for (int i = 0; i < path.length && drawn < IMAGES; i++) {
            Vec3 at = path[i].pos();
            double seg = prev.distanceTo(at);
            while (seg > 1e-4 && walked + seg >= want && drawn < IMAGES) {
                double f = (want - walked) / seg;
                Vec3 spot = prev.add(at.subtract(prev).scale(f));
                Snap s = path[i];
                float alpha = (0.42F - drawn * 0.06F) * (1F - fade / 7F);
                if (alpha > 0.02F && !(firstPerson && spot.distanceToSqr(cam) < 2.5)) one(ps, buffers, model, type, p, s, spot, cam, alpha);
                drawn++;
                want += SPACING;
            }
            walked += seg;
            prev = at;
        }
    }

    private static void one(PoseStack ps, MultiBufferSource buffers, PlayerModel<AbstractClientPlayer> model, RenderType type, AbstractClientPlayer p,
                            Snap s, Vec3 spot, Vec3 cam, float alpha) {
        ps.pushPose();
        ps.translate(spot.x - cam.x, spot.y - cam.y, spot.z - cam.z);
        float yaw = s.bodyYaw() * Mth.DEG_TO_RAD;
        if (s.wall()) {
            ps.translate(-Mth.sin(s.face()) * 0.35, 0.2, Mth.cos(s.face()) * 0.35);
            ps.mulPose(new Quaternionf().rotateAxis(-1.3F, Mth.cos(s.face()), 0, Mth.sin(s.face())));
        } else {
            ps.mulPose(new Quaternionf().rotateAxis(0.64F, Mth.cos(yaw), 0, Mth.sin(yaw))); // the ninja run's lean, flat out
        }
        // as the living-entity renderer stands a model up
        ps.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180F - s.bodyYaw()));
        ps.scale(-1F, -1F, 1F);
        ps.scale(0.9375F, 0.9375F, 0.9375F);
        ps.translate(0, -1.501F, 0);
        model.attackTime = 0;
        model.crouching = false;
        model.riding = false;
        model.young = false;
        model.setAllVisible(true);
        model.prepareMobModel(p, s.limbPos(), s.limbSpeed(), 0);
        model.setupAnim(p, s.limbPos(), s.limbSpeed(), s.age(), Mth.wrapDegrees(s.headYaw() - s.bodyYaw()), s.headPitch());
        // a little of the fire in it
        int a = Mth.clamp((int) (alpha * 255), 0, 255);
        int color = a << 24 | 0xFFD8B8;
        model.renderToBuffer(ps, buffers.getBuffer(type), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, color);
        ps.popPose();
    }
}
