/*
 * Breach Protocol - how a member of staff looks (skin, face, eyes, hair, uniform).
 *
 * Adapted from Minecraft Comes Alive Reborn (VillagerLayer, SkinLayer, FaceLayer, HairLayer,
 * ClothingLayer, ColorPalette, EyeTextureLayers), Copyright (C) Conczin and the MCA Reborn
 * contributors, licensed under the GNU General Public License v3.0.
 * https://github.com/Luke100000/minecraft-comes-alive
 * Modifications: genes come from each staff member; clothing is the Foundation uniform of their
 * department; hair greys with age.
 */
package com.breachprotocol.client.mca;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.entity.Person;
import net.minecraft.world.entity.Mob;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class McaLooks {
    private McaLooks() {}

    static ResourceLocation tex(String path) { return BreachProtocol.id("textures/entity/mca/" + path); }

    // ================================================================== colour maps

    /** A 2D colour map: skin by melanin and hemoglobin, hair by eumelanin and pheomelanin. */
    static final class Palette {
        private final ResourceLocation id;
        private int w = 1, h = 1;
        private int[] colours;

        Palette(ResourceLocation id) { this.id = id; }

        int colour(float u, float v) {
            if (colours == null) load();
            int x = (int) Math.floor(Mth.clamp(v * (w - 1), 0, w - 1));
            int y = (int) Math.floor(Mth.clamp(u * (h - 1), 0, h - 1));
            return colours[y * h + x];
        }

        private void load() {
            colours = new int[]{0xFFFFFFFF};
            try {
                var res = Minecraft.getInstance().getResourceManager().getResource(id);
                if (res.isEmpty()) return;
                try (InputStream in = res.get().open(); NativeImage img = NativeImage.read(in)) {
                    w = img.getWidth();
                    h = img.getHeight();
                    int[] c = new int[w * h];
                    for (int y = 0; y < h; y++)
                        for (int x = 0; x < w; x++) {
                            int abgr = img.getPixelRGBA(x, y);
                            c[y * h + x] = FastColor.ARGB32.color(255, FastColor.ABGR32.red(abgr), FastColor.ABGR32.green(abgr), FastColor.ABGR32.blue(abgr));
                        }
                    colours = c;
                }
            } catch (Exception e) {
                BreachProtocol.LOGGER.warn("Couldn't read colour map {}", id, e);
            }
        }
    }

    static final Palette SKIN = new Palette(tex("colormap/villager_skin.png"));
    static final Palette HAIR = new Palette(tex("colormap/villager_hair.png"));

    // ================================================================== eyes

    private static final int BLUE = 0xFF3A98E8, GREEN = 0xFF4CB346, HAZEL = 0xFFC29B35, BROWN = 0xFF7C4825, DETAILS_TINT = 0xFF808080;

    static int eyeColour(Person e) {
        float c = Mth.frac(e.faceGene());
        int col = c < 0.35F ? FastColor.ARGB32.lerp(c / 0.35F, BLUE, GREEN) : c < 0.70F ? FastColor.ARGB32.lerp((c - 0.35F) / 0.35F, GREEN, HAZEL) : FastColor.ARGB32.lerp((c - 0.70F) / 0.30F, HAZEL, BROWN);
        float f = 0.5F + Mth.clamp(e.eyeBrightness(), 0, 1);
        int a = col >>> 24, r = Mth.clamp(Math.round(((col >> 16) & 255) * f), 0, 255), g = Mth.clamp(Math.round(((col >> 8) & 255) * f), 0, 255), b = Mth.clamp(Math.round((col & 255) * f), 0, 255);
        return a << 24 | r << 16 | g << 8 | b;
    }

    enum EyePart { SCLERA, IRIS, DETAILS }

    private static final Map<String, ResourceLocation> EYES = new HashMap<>();

    private static boolean sclera(int a, int r, int g, int b) {
        if (a == 1) return true;
        if (a != 255) return false;
        int min = Math.min(r, Math.min(g, b)), max = Math.max(r, Math.max(g, b));
        return min >= 160 && max - min <= 32;
    }

    /** One part of a face texture (whites, iris, details), split out so the iris can be coloured. */
    static ResourceLocation eyePart(ResourceLocation face, EyePart part) {
        return EYES.computeIfAbsent(face + "#" + part, k -> {
            try {
                var res = Minecraft.getInstance().getResourceManager().getResource(face);
                if (res.isEmpty()) return face;
                try (InputStream in = res.get().open(); NativeImage src = NativeImage.read(in)) {
                    NativeImage out = new NativeImage(src.getWidth(), src.getHeight(), true);
                    for (int x = 0; x < src.getWidth(); x++)
                        for (int y = 0; y < src.getHeight(); y++) {
                            int px = src.getPixelRGBA(x, y);
                            int a = FastColor.ABGR32.alpha(px);
                            if (a == 0) continue;
                            int r = FastColor.ABGR32.red(px), g = FastColor.ABGR32.green(px), b = FastColor.ABGR32.blue(px);
                            boolean s = sclera(a, r, g, b);
                            int max = Math.max(r, Math.max(g, b));
                            boolean take = switch (part) { case SCLERA -> s; case IRIS -> !s && max >= 32; case DETAILS -> !s && max < 32; };
                            if (take) out.setPixelRGBA(x, y, px);
                        }
                    ResourceLocation id = BreachProtocol.id("dynamic/eye/" + part.name().toLowerCase(Locale.ROOT) + "/" + face.getPath().replace('/', '_'));
                    Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(out));
                    return id;
                }
            } catch (Exception ex) {
                BreachProtocol.LOGGER.warn("Couldn't split face texture {}", face, ex);
                return face;
            }
        });
    }

    // ================================================================== the layers

    abstract static class Layer<T extends Mob & Person> extends RenderLayer<T, HumanoidModel<T>> {
        final McaStaffModel<T> model;

        Layer(RenderLayerParent<T, HumanoidModel<T>> parent, McaStaffModel<T> model) {
            super(parent);
            this.model = model;
        }

        void visibility() { model.setAllVisible(true); }

        abstract void draw(PoseStack pose, MultiBufferSource buf, int light, int overlay, T e, float partial);

        @Override
        public void render(PoseStack pose, MultiBufferSource buf, int light, T e, float limb, float amount, float partial, float age, float yaw, float pitch) {
            if (e.isInvisible()) return;
            model.copyVisibility(getParentModel());
            getParentModel().copyPropertiesTo(model);
            model.setupAnim(e, limb, amount, age, yaw, pitch);
            visibility();
            draw(pose, buf, light, LivingEntityRenderer.getOverlayCoords(e, 0), e, partial);
        }

        void put(PoseStack pose, MultiBufferSource buf, int light, int overlay, ResourceLocation t, int colour, boolean translucent) {
            model.renderToBuffer(pose, buf.getBuffer(translucent ? RenderType.itemEntityTranslucentCull(t) : model.renderType(t)), light, overlay, colour);
        }
    }

    /** Skin, tinted from the skin colour map by their genes. */
    static final class Skin<T extends Mob & Person> extends Layer<T> {
        Skin(RenderLayerParent<T, HumanoidModel<T>> p, McaStaffModel<T> m) { super(p, m.hideWears()); }

        @Override
        void draw(PoseStack pose, MultiBufferSource buf, int light, int overlay, T e, float partial) {
            ResourceLocation t = tex("skin/" + (e.isFemale() ? "female" : "male") + "/" + e.skinIndex() + ".png");
            put(pose, buf, light, overlay, t, SKIN.colour(e.melanin(), e.hemoglobin()), false);
        }
    }

    /** The face: whites, coloured irises, details, and a blink now and then. */
    static final class Face<T extends Mob & Person> extends Layer<T> {
        private static final int FACES = 11;

        Face(RenderLayerParent<T, HumanoidModel<T>> p, McaStaffModel<T> m) { super(p, m.hideWears()); }

        @Override
        void visibility() { model.setAllVisible(false); model.head.visible = true; }

        @Override
        void draw(PoseStack pose, MultiBufferSource buf, int light, int overlay, T e, float partial) {
            int time = e.tickCount / 2 + (int) (e.hemoglobin() * 65536);
            if (time % 50 == 1 || time % 57 == 1 || e.isDeadOrDying() || e.isSleeping()) {
                put(pose, buf, light, overlay, tex("face/blink.png"), 0xFFFFFFFF, true);
                return;
            }
            if (e instanceof com.breachprotocol.entity.O5MemberEntity && !clearedForCouncil()) {
                put(pose, buf, light, overlay, BreachProtocol.id("textures/entity/staff/uniform/redacted.png"), 0xFFFFFFFF, true);
                return;
            }
            ResourceLocation f = tex("face/" + Math.min(FACES - 1, (int) (e.faceGene() * FACES)) + ".png");
            put(pose, buf, light, overlay, eyePart(f, EyePart.SCLERA), 0xFFFFFFFF, true);
            put(pose, buf, light, overlay, eyePart(f, EyePart.DETAILS), DETAILS_TINT, true);
            put(pose, buf, light, overlay, eyePart(f, EyePart.IRIS), eyeColour(e), true);
        }
    }

    /** The Foundation uniform of their department. */
    static final class Clothes<T extends Mob & Person> extends Layer<T> {
        Clothes(RenderLayerParent<T, HumanoidModel<T>> p, McaStaffModel<T> m) { super(p, m); }

        @Override
        void draw(PoseStack pose, MultiBufferSource buf, int light, int overlay, T e, float partial) {
            put(pose, buf, light, overlay, BreachProtocol.id(e.uniform()), 0xFFFFFFFF, false);
        }
    }

    /** Hair: one of MCA's styles, coloured from the hair colour map, greying with age. */
    static final class Hair<T extends Mob & Person> extends Layer<T> {
        private static final int STYLES = 40;

        Hair(RenderLayerParent<T, HumanoidModel<T>> p, McaStaffModel<T> m) { super(p, m); }

        @Override
        void visibility() {
            model.setAllVisible(true);
            model.leftLeg.visible = model.rightLeg.visible = model.leftLegwear.visible = model.rightLegwear.visible = false;
            for (var part : model.breastParts()) part.visible = false;
        }

        @Override
        void draw(PoseStack pose, MultiBufferSource buf, int light, int overlay, T e, float partial) {
            int grey = e.greyHair();
            int colour = grey != 0 ? grey : HAIR.colour(e.eumelanin(), e.pheomelanin());
            put(pose, buf, light, overlay, tex("hair/" + (e.isFemale() ? "female" : "male") + "/" + e.hairIndex(STYLES) + ".png"), colour, false);
        }
    }

    /** Only the Council (and above) may look an Overseer in the face; everyone else sees a classified blur. */
    static boolean clearedForCouncil() {
        var t = com.breachprotocol.network.ClientCareerState.tag;
        return t != null && (t.getBoolean("ascended") || t.getInt("rankId") >= com.breachprotocol.career.Rank.OVERSEER.ordinal());
    }

    /** Adds the look layers to a renderer, in MCA's order. */
    public static <T extends Mob & Person> void attachTo(java.util.List<RenderLayer<T, HumanoidModel<T>>> layers, RenderLayerParent<T, HumanoidModel<T>> r, net.minecraft.client.renderer.entity.EntityRendererProvider.Context ctx) {
        layers.add(0, new Skin<>(r, new McaStaffModel<>(ctx.bakeLayer(McaStaffModel.BODY))));
        layers.add(1, new Face<>(r, new McaStaffModel<>(ctx.bakeLayer(McaStaffModel.FACE))));
        layers.add(2, new Clothes<>(r, new McaStaffModel<>(ctx.bakeLayer(McaStaffModel.CLOTHES))));
        layers.add(3, new Hair<>(r, new McaStaffModel<>(ctx.bakeLayer(McaStaffModel.HAIR))));
    }
}
