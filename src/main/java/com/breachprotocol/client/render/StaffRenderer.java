package com.breachprotocol.client.render;

import com.breachprotocol.client.mca.McaLooks;
import com.breachprotocol.client.mca.McaStaffModel;
import com.breachprotocol.entity.FoundationStaff;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Foundation personnel, drawn the Minecraft Comes Alive way: a player-shaped body built up in
 * layers (skin, face and eyes, the department's uniform, hair), every person with their own genes.
 */
public class StaffRenderer extends HumanoidMobRenderer<FoundationStaff, HumanoidModel<FoundationStaff>> {
    public StaffRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new McaStaffModel(ctx.bakeLayer(McaStaffModel.BODY)), 0.45F);
        McaLooks.attachTo(this, ctx);
    }

    public List<RenderLayer<FoundationStaff, HumanoidModel<FoundationStaff>>> layersList() { return layers; }

    @Override
    protected void scale(FoundationStaff e, PoseStack pose, float partial) {
        float h = e.heightScale(), w = e.widthScale();
        pose.scale(w, h, w);
    }

    /** The body itself is drawn by the skin layer (it needs the colour), so the base pass draws nothing. */
    @Override
    protected RenderType getRenderType(FoundationStaff e, boolean body, boolean translucent, boolean outline) { return null; }

    @Override
    public ResourceLocation getTextureLocation(FoundationStaff e) {
        return com.breachprotocol.BreachProtocol.id("textures/entity/mca/skin/" + (e.isFemale() ? "female" : "male") + "/" + e.skinIndex() + ".png");
    }
}
