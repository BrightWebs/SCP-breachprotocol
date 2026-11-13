package com.breachprotocol.client.skull;

import com.breachprotocol.BreachProtocol;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.SkeletonModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * The first part of True Form's change: the man burning away. Three shells, drawn on the player's own model as it's
 * posed: his own skin (read back from the game), the red muscle under it, and the bare Minecraft skeleton under that.
 * Each burns off pixel by pixel along a blotchy front, charring just ahead of it and glowing at the edge, the skin
 * first, then the muscle; then the skeleton shudders, swells and crumbles as the true form comes up through it.
 */
final class BurningMan {
    private BurningMan() {}

    private static final class Layers {
        int[] skin, muscle, bone;                 // the source pixels (ABGR), 64x64 / 64x64 / 64x32
        float[] thrSkin, thrMuscle, thrBone;      // where each pixel burns: 0 first, 1 last
        DynamicTexture skinT, skinG, muscT, muscG, boneT, boneG;
        ResourceLocation skinL, skinGL, muscL, muscGL, boneL, boneGL;
        int lastS = -1, lastM = -1, lastB = -1;
        ResourceLocation from;
    }

    private static final Map<UUID, Layers> LAYERS = new HashMap<>();
    private static SkeletonModel<?> skeleton;
    private static int[] BONE;

    static void forget(UUID id) {
        Layers l = LAYERS.remove(id);
        if (l != null) for (DynamicTexture t : new DynamicTexture[]{l.skinT, l.skinG, l.muscT, l.muscG, l.boneT, l.boneG}) if (t != null) t.close();
    }

    /** c: how far through the change (the man's part of it runs 0 .. 0.56). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static void draw(Player player, LivingEntityRenderer renderer, float c, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        if (!(player instanceof AbstractClientPlayer p) || !(renderer.getModel() instanceof PlayerModel model)) return;
        Minecraft mc = Minecraft.getInstance();
        Layers l = layers(p);
        if (l == null) return;
        float skinP = Mth.clamp(c / 0.24F, 0, 1), muscP = Mth.clamp((c - 0.17F) / 0.22F, 0, 1), boneP = Mth.clamp((c - 0.47F) / 0.09F, 0, 1);
        int q;
        if ((q = Math.round(skinP * 120)) != l.lastS) { l.lastS = q; burn(l.skin, l.thrSkin, skinP, l.skinT, l.skinG, 64); }
        if ((q = Math.round(muscP * 120)) != l.lastM) { l.lastM = q; burn(l.muscle, l.thrMuscle, muscP, l.muscT, l.muscG, 64); }
        if ((q = Math.round(boneP * 120)) != l.lastB) { l.lastB = q; burn(l.bone, l.thrBone, boneP, l.boneT, l.boneG, 32); }

        // posed exactly as the game poses a player
        float bodyRot = Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot), headRot = Mth.rotLerp(pt, p.yHeadRotO, p.yHeadRot);
        float pitch = Mth.lerp(pt, p.xRotO, p.getXRot()), age = p.tickCount + pt;
        float walkSpeed = p.walkAnimation.speed(pt), walkPos = p.walkAnimation.position(pt);
        model.attackTime = p.getAttackAnim(pt);
        model.riding = false;
        model.young = false;
        model.prepareMobModel(p, walkPos, walkSpeed, pt);
        model.setupAnim(p, walkPos, walkSpeed, age, headRot - bodyRot, pitch);

        ps.pushPose();
        // it shudders as it burns
        float shake = c < 0.47F ? Mth.sin(age * 1.85F) * 0.016F * Math.min(1, c * 4) : 0;
        ps.translate(shake, 0, 0);
        ps.mulPose(Axis.YP.rotationDegrees(180 - bodyRot));
        ps.scale(-1, -1, 1);
        ps.scale(0.9375F, 0.9375F, 0.9375F);
        ps.translate(0, -1.501F, 0);
        int overlay = OverlayTexture.NO_OVERLAY;
        // the skeleton: the game's own, posed like him; swelling and crumbling at the end
        if (boneP < 1) {
            SkeletonModel sk = skeleton();
            if (sk != null) {
                model.copyPropertiesTo(sk);
                ps.pushPose();
                if (c > 0.47F) { float sw = 1 + (c - 0.47F) * 4; ps.translate(0, 1.5F * (1 - sw), 0); ps.scale(sw, sw, sw); }
                ps.scale(0.94F, 0.94F, 0.94F);
                sk.renderToBuffer(ps, buf.getBuffer(RenderType.entityCutoutNoCull(l.boneL)), light, overlay);
                sk.renderToBuffer(ps, buf.getBuffer(RenderType.eyes(l.boneGL)), 0xF000F0, overlay);
                ps.popPose();
            }
        }
        // the muscle, a little inside the skin
        if (muscP < 1) {
            ps.pushPose();
            ps.scale(0.985F, 0.985F, 0.985F);
            model.renderToBuffer(ps, buf.getBuffer(RenderType.entityCutoutNoCull(l.muscL)), light, overlay);
            model.renderToBuffer(ps, buf.getBuffer(RenderType.eyes(l.muscGL)), 0xF000F0, overlay);
            ps.popPose();
        }
        // his skin
        if (skinP < 1) {
            model.renderToBuffer(ps, buf.getBuffer(RenderType.entityCutoutNoCull(l.skinL)), light, overlay);
            model.renderToBuffer(ps, buf.getBuffer(RenderType.eyes(l.skinGL)), 0xF000F0, overlay);
        }
        ps.popPose();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static SkeletonModel<?> skeleton() {
        if (skeleton == null) try { skeleton = new SkeletonModel(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.SKELETON)); }
        catch (Throwable t) { BreachProtocol.LOGGER.debug("Skeleton model: {}", t.toString()); }
        return skeleton;
    }

    private static Layers layers(AbstractClientPlayer p) {
        ResourceLocation skinLoc = p.getSkin().texture();
        Layers l = LAYERS.get(p.getUUID());
        if (l != null && skinLoc.equals(l.from)) return l;
        if (l != null) forget(p.getUUID());
        l = new Layers();
        l.from = skinLoc;
        try {
            l.skin = readSkin(skinLoc);
            l.muscle = muscle();
            l.bone = bone();
            long seed = p.getUUID().getLeastSignificantBits();
            l.thrSkin = blotches(64, 64, seed);
            l.thrMuscle = blotches(64, 64, seed * 31 + 7);
            l.thrBone = blotches(64, 32, seed * 17 + 3);
            String key = p.getUUID().toString().replace("-", "");
            Minecraft mc = Minecraft.getInstance();
            l.skinT = new DynamicTexture(64, 64, true); l.skinG = new DynamicTexture(64, 64, true);
            l.muscT = new DynamicTexture(64, 64, true); l.muscG = new DynamicTexture(64, 64, true);
            l.boneT = new DynamicTexture(64, 32, true); l.boneG = new DynamicTexture(64, 32, true);
            l.skinL = mc.getTextureManager().register("bp_burn_skin_" + key, l.skinT);
            l.skinGL = mc.getTextureManager().register("bp_burn_skin_glow_" + key, l.skinG);
            l.muscL = mc.getTextureManager().register("bp_burn_muscle_" + key, l.muscT);
            l.muscGL = mc.getTextureManager().register("bp_burn_muscle_glow_" + key, l.muscG);
            l.boneL = mc.getTextureManager().register("bp_burn_bone_" + key, l.boneT);
            l.boneGL = mc.getTextureManager().register("bp_burn_bone_glow_" + key, l.boneG);
        } catch (Throwable t) {
            BreachProtocol.LOGGER.debug("Burning man: {}", t.toString());
            return null;
        }
        LAYERS.put(p.getUUID(), l);
        return l;
    }

    /** The player's own skin, read back from the card (it was downloaded, there's no file to read). */
    private static int[] readSkin(ResourceLocation loc) {
        int[] out = new int[64 * 64];
        try {
            AbstractTexture tex = Minecraft.getInstance().getTextureManager().getTexture(loc);
            RenderSystem.bindTexture(tex.getId());
            // only a plain 64x64 skin (a bigger one would overrun the image it's read into)
            int w = com.mojang.blaze3d.platform.GlStateManager._getTexLevelParameter(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, 0, org.lwjgl.opengl.GL11.GL_TEXTURE_WIDTH);
            int h = com.mojang.blaze3d.platform.GlStateManager._getTexLevelParameter(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, 0, org.lwjgl.opengl.GL11.GL_TEXTURE_HEIGHT);
            if (w != 64 || h != 64) throw new IllegalStateException("skin is " + w + "x" + h);
            try (NativeImage img = new NativeImage(64, 64, false)) {
                img.downloadTexture(0, false);
                for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) out[y * 64 + x] = img.getPixelRGBA(x, y);
            }
        } catch (Throwable t) {
            BreachProtocol.LOGGER.debug("Couldn't read the skin back: {}", t.toString());
            for (int i = 0; i < out.length; i++) out[i] = 0xFF6A94C8;   // a plain skin colour (ABGR)
        }
        return out;
    }

    private static int abgr(int r, int g, int b, int a) { return a << 24 | b << 16 | g << 8 | r; }

    /** Muscle: red and striated over the whole skin layout, with the eyes and teeth showing in the face. */
    private static int[] muscle() {
        int[] out = new int[64 * 64];
        Random r = new Random(11);
        for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) {
            int base = r.nextInt(10) < 3 ? (r.nextBoolean() ? 0xa0242a : 0x6a0e12) : 0x8a1a1e;
            int R = base >> 16 & 255, G = base >> 8 & 255, B = base & 255;
            if (x % 2 == 0) { R = R * 2 / 3; G = G * 2 / 3; B = B * 2 / 3; }
            out[y * 64 + x] = abgr(R, G, B, 255);
        }
        // the face (front of the head: x 8..15, y 8..15): eyes and teeth
        for (int[] e : new int[][]{{9, 12}, {10, 12}, {13, 12}, {14, 12}}) out[e[1] * 64 + e[0]] = abgr(240, 232, 224, 255);
        for (int x = 10; x < 14; x++) out[14 * 64 + x] = abgr(244, 239, 226, 255);
        return out;
    }

    /** The game's own skeleton texture. */
    private static int[] bone() {
        if (BONE == null) {
            BONE = new int[64 * 32];
            try (var in = Minecraft.getInstance().getResourceManager().getResource(ResourceLocation.withDefaultNamespace("textures/entity/skeleton/skeleton.png")).orElseThrow().open();
                 NativeImage img = NativeImage.read(in)) {
                for (int y = 0; y < 32; y++) for (int x = 0; x < 64; x++) BONE[y * 64 + x] = img.getPixelRGBA(x, y);
            } catch (Throwable t) {
                for (int i = 0; i < BONE.length; i++) BONE[i] = abgr(200, 200, 196, 255);
            }
        }
        return BONE;
    }

    /** Where the fire reaches first: a few blotches spreading, roughened. */
    private static float[] blotches(int W, int H, long seed) {
        Random r = new Random(seed);
        float[][] pts = new float[6][3];
        for (float[] q : pts) { q[0] = r.nextFloat() * W; q[1] = r.nextFloat() * H; q[2] = 4 + r.nextFloat() * W * 0.45F; }
        float[] f = new float[W * H];
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            float v = Float.MAX_VALUE;
            for (float[] q : pts) v = Math.min(v, (float) Math.hypot(x - q[0], y - q[1]) / q[2]);
            f[y * W + x] = Math.min(1, v * 0.55F + r.nextFloat() * 0.35F);
        }
        return f;
    }

    /** Burn to p: gone behind the front, charred just ahead of it, glowing at the edge. */
    private static void burn(int[] src, float[] thr, float p, DynamicTexture col, DynamicTexture glow, int H) {
        NativeImage c = col.getPixels(), g = glow.getPixels();
        if (c == null || g == null) return;
        float front = p * 1.25F - 0.1F;
        for (int y = 0; y < H; y++) for (int x = 0; x < 64; x++) {
            int i = y * 64 + x, s = src[i];
            int a = s >>> 24;
            float th = thr[i];
            if (a == 0 || front > th) { c.setPixelRGBA(x, y, 0); g.setPixelRGBA(x, y, 0); continue; }
            float near = th - front, ch = Math.max(0, 1 - near / 0.35F) * (p > 0 ? 1 : 0);
            int R = s & 255, G = s >> 8 & 255, B = s >> 16 & 255;
            c.setPixelRGBA(x, y, abgr((int) (R * (1 - ch * 0.8F)), (int) (G * (1 - ch * 0.85F)), (int) (B * (1 - ch * 0.9F)), a));
            if (p > 0 && near < 0.07F) g.setPixelRGBA(x, y, near < 0.03F ? abgr(255, 220, 90, 255) : abgr(255, 110, 20, 255));
            else g.setPixelRGBA(x, y, 0);
        }
        col.upload();
        glow.upload();
    }
}
