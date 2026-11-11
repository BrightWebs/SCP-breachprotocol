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
                float alpha = (0.42F - drawn * 0.06F) * (1F - fade / 7F);
                if (alpha > 0.02F && !(firstPerson && spot.distanceToSqr(cam) < 2.5)) one(ps, buffers, p, path[i], spot, cam, alpha);
                drawn++;
                want += SPACING;
            }
            walked += seg;
            prev = at;
        }
    }

    /** True while an after-image is being drawn (no name over it, no shadow under it). */
    static boolean drawing;

    /**
     * One image: the runner drawn the way they're drawn right now (their skin as it is, hat or not, armour, or
     * whatever creature they've become), but posed where and how they were, and see-through.
     */
    private static void one(PoseStack ps, MultiBufferSource buffers, AbstractClientPlayer p, Snap s, Vec3 spot, Vec3 cam, float alpha) {
        var disp = Minecraft.getInstance().getEntityRenderDispatcher();
        // borrow the pose they had then, and give it back after
        float body = p.yBodyRot, bodyO = p.yBodyRotO, head = p.yHeadRot, headO = p.yHeadRotO, pitch = p.getXRot(), pitchO = p.xRotO;
        float[] walk = Walk.get(p);
        p.yBodyRot = p.yBodyRotO = s.bodyYaw();
        p.yHeadRot = p.yHeadRotO = s.headYaw();
        p.setXRot(s.headPitch());
        p.xRotO = s.headPitch();
        Walk.set(p, s.limbPos(), s.limbSpeed(), s.limbSpeed());
        boolean shadows = Minecraft.getInstance().options.entityShadows().get();
        drawing = true;
        try {
            disp.setRenderShadow(false);
            disp.render(p, spot.x - cam.x, spot.y - cam.y, spot.z - cam.z, s.bodyYaw(), 1.0F, ps, Ghost.of(buffers, alpha), LightTexture.FULL_BRIGHT);
        } finally {
            drawing = false;
            disp.setRenderShadow(shadows);
            p.yBodyRot = body; p.yBodyRotO = bodyO; p.yHeadRot = head; p.yHeadRotO = headO;
            p.setXRot(pitch); p.xRotO = pitchO;
            if (walk != null) Walk.set(p, walk[0], walk[1], walk[2]);
        }
    }

    @SubscribeEvent
    public static void noName(net.neoforged.neoforge.client.event.RenderNameTagEvent event) {
        if (drawing) event.setCanRender(net.neoforged.neoforge.common.util.TriState.FALSE);
    }

    /** The walk cycle's own state (position, speed, last speed), read and set by name. */
    private static final class Walk {
        private static java.lang.reflect.Field pos, speed, old;
        private static boolean broken;

        private static boolean ready() {
            if (pos != null) return true;
            if (broken) return false;
            try {
                Class<?> c = net.minecraft.world.entity.WalkAnimationState.class;
                pos = c.getDeclaredField("position"); pos.setAccessible(true);
                speed = c.getDeclaredField("speed"); speed.setAccessible(true);
                old = c.getDeclaredField("speedOld"); old.setAccessible(true);
                return true;
            } catch (Throwable t) { broken = true; pos = null; return false; }
        }

        static float[] get(Player p) {
            if (!ready()) return null;
            try { return new float[]{pos.getFloat(p.walkAnimation), speed.getFloat(p.walkAnimation), old.getFloat(p.walkAnimation)}; } catch (Throwable t) { return null; }
        }

        static void set(Player p, float position, float sp, float spOld) {
            if (!ready()) return;
            try { pos.setFloat(p.walkAnimation, position); speed.setFloat(p.walkAnimation, sp); old.setFloat(p.walkAnimation, spOld); } catch (Throwable ignored) {}
        }
    }

    /**
     * Buffers that make whatever is drawn into them see-through: each textured layer is swapped for its
     * translucent twin, every vertex's alpha scaled down and warmed a touch; anything that can't be made
     * see-through (glints, lines) is left out.
     */
    private static final class Ghost {
        private static final Map<RenderType, RenderType> TWIN = new HashMap<>();
        private static final RenderType NONE = RenderType.lines();
        private static java.lang.reflect.Method state, cutout;
        private static java.lang.reflect.Field texture;

        static MultiBufferSource of(MultiBufferSource base, float alpha) {
            return rt -> {
                RenderType t = twin(rt);
                if (t == NONE) return Nothing.INSTANCE;
                return new Fade(base.getBuffer(t), alpha);
            };
        }

        private static RenderType twin(RenderType rt) {
            return TWIN.computeIfAbsent(rt, k -> {
                try {
                    if (state == null) {
                        Class<?> comp = Class.forName("net.minecraft.client.renderer.RenderType$CompositeRenderType");
                        state = comp.getDeclaredMethod("state"); state.setAccessible(true);
                        texture = Class.forName("net.minecraft.client.renderer.RenderType$CompositeState").getDeclaredField("textureState"); texture.setAccessible(true);
                        cutout = Class.forName("net.minecraft.client.renderer.RenderStateShard$EmptyTextureStateShard").getDeclaredMethod("cutoutTexture"); cutout.setAccessible(true);
                    }
                    if (!state.getDeclaringClass().isInstance(k)) return NONE;
                    Object st = state.invoke(k);
                    Object tex = texture.get(st);
                    @SuppressWarnings("unchecked")
                    java.util.Optional<net.minecraft.resources.ResourceLocation> loc = (java.util.Optional<net.minecraft.resources.ResourceLocation>) cutout.invoke(tex);
                    return loc.map(RenderType::entityTranslucent).orElse(NONE);
                } catch (Throwable t) {
                    BreachProtocol.LOGGER.debug("After-images: {} can't be made see-through: {}", k, t.toString());
                    return NONE;
                }
            });
        }
    }

    /** Scales every vertex's alpha (and warms the colour a little: the fire in it). */
    private record Fade(com.mojang.blaze3d.vertex.VertexConsumer in, float alpha) implements com.mojang.blaze3d.vertex.VertexConsumer {
        @Override public com.mojang.blaze3d.vertex.VertexConsumer addVertex(float x, float y, float z) { in.addVertex(x, y, z); return this; }
        @Override public com.mojang.blaze3d.vertex.VertexConsumer setColor(int r, int g, int b, int a) {
            in.setColor(r, Math.round(g * 0.85F), Math.round(b * 0.72F), Math.round(a * alpha)); return this;
        }
        @Override public com.mojang.blaze3d.vertex.VertexConsumer setUv(float u, float v) { in.setUv(u, v); return this; }
        @Override public com.mojang.blaze3d.vertex.VertexConsumer setUv1(int u, int v) { in.setUv1(u, v); return this; }
        @Override public com.mojang.blaze3d.vertex.VertexConsumer setUv2(int u, int v) { in.setUv2(u, v); return this; }
        @Override public com.mojang.blaze3d.vertex.VertexConsumer setNormal(float x, float y, float z) { in.setNormal(x, y, z); return this; }
        @Override public void addVertex(float x, float y, float z, int color, float u, float v, int overlay, int light, float nx, float ny, float nz) {
            int a = Math.round((color >>> 24) * alpha), r = (color >> 16) & 255, g = Math.round(((color >> 8) & 255) * 0.85F), b = Math.round((color & 255) * 0.72F);
            in.addVertex(x, y, z, a << 24 | r << 16 | g << 8 | b, u, v, overlay, light, nx, ny, nz);
        }
    }

    /** Swallows what can't be drawn see-through. */
    private static final class Nothing implements com.mojang.blaze3d.vertex.VertexConsumer {
        static final Nothing INSTANCE = new Nothing();
        @Override public com.mojang.blaze3d.vertex.VertexConsumer addVertex(float x, float y, float z) { return this; }
        @Override public com.mojang.blaze3d.vertex.VertexConsumer setColor(int r, int g, int b, int a) { return this; }
        @Override public com.mojang.blaze3d.vertex.VertexConsumer setUv(float u, float v) { return this; }
        @Override public com.mojang.blaze3d.vertex.VertexConsumer setUv1(int u, int v) { return this; }
        @Override public com.mojang.blaze3d.vertex.VertexConsumer setUv2(int u, int v) { return this; }
        @Override public com.mojang.blaze3d.vertex.VertexConsumer setNormal(float x, float y, float z) { return this; }
    }
}
