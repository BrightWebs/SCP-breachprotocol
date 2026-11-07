package com.breachprotocol.client.render;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.entity.FoundationStaff;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** Each person's own hair (style and colour), beard or moustache, and glasses, over their uniform. */
public class StaffLooksLayer extends RenderLayer<FoundationStaff, HumanoidModel<FoundationStaff>> {
    private final StaffModel outer;

    public StaffLooksLayer(RenderLayerParent<FoundationStaff, HumanoidModel<FoundationStaff>> parent, StaffModel outer) {
        super(parent);
        this.outer = outer;
    }

    private static ResourceLocation tex(String n) { return BreachProtocol.id("textures/entity/staff/hair/" + n + ".png"); }

    private static boolean capped(FoundationStaff e) {
        return e.role() == FoundationStaff.Role.GUARD || e.role() == FoundationStaff.Role.TECHNICIAN;
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, FoundationStaff e, float limbSwing, float amount, float partial, float age, float headYaw, float headPitch) {
        if (e.isInvisible()) return;
        getParentModel().copyPropertiesTo(outer);
        outer.setupAnim(e, limbSwing, amount, age, headYaw, headPitch);
        int overlay = LivingEntityRenderer.getOverlayCoords(e, 0.0F);
        String style = e.hairStyle();
        boolean longHair = style.equals("long") || style.equals("ponytail");
        // hair, tinted their colour
        show(true, longHair);
        draw(pose, buffers, light, overlay, tex(style + (capped(e) ? "_cap" : "")), 0xFF000000 | e.hairColour());
        // beard or moustache, the same colour
        String beard = e.facialHair();
        if (!beard.isEmpty()) { show(true, false); draw(pose, buffers, light, overlay, tex(beard), 0xFF000000 | e.hairColour()); }
        if (e.glasses()) { show(true, false); draw(pose, buffers, light, overlay, tex("glasses"), 0xFFFFFFFF); }
    }

    private void show(boolean head, boolean body) {
        outer.setAllVisible(false);
        outer.head.visible = head;
        outer.body.visible = body;
        for (String k : new String[]{"bust_s", "bust_m", "bust_l"}) outer.body.getChild(k).visible = false;
        outer.head.getChild("lids").visible = false;
    }

    private void draw(PoseStack pose, MultiBufferSource buffers, int light, int overlay, ResourceLocation t, int colour) {
        outer.renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(t)), light, overlay, colour);
    }
}
