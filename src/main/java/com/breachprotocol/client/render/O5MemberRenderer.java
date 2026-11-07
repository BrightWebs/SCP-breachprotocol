package com.breachprotocol.client.render;

import com.breachprotocol.client.mca.McaLooks;
import com.breachprotocol.client.mca.McaStaffModel;
import com.breachprotocol.entity.O5MemberEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * The Overseers, on the same MCA-style bodies as everyone else, each in their own suit. Their
 * faces are classified: below the Council, you see them blurred out.
 */
public class O5MemberRenderer extends HumanoidMobRenderer<O5MemberEntity, HumanoidModel<O5MemberEntity>> {
    public O5MemberRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new McaStaffModel<>(ctx.bakeLayer(McaStaffModel.BODY)), 0.45F);
        McaLooks.attachTo(layers, this, ctx);
    }

    @Override
    protected void scale(O5MemberEntity e, PoseStack pose, float partial) {
        float h = e.heightScale(), w = e.widthScale();
        pose.scale(w, h, w);
    }

    @Override
    protected RenderType getRenderType(O5MemberEntity e, boolean body, boolean translucent, boolean outline) { return null; }

    @Override
    public ResourceLocation getTextureLocation(O5MemberEntity e) {
        return com.breachprotocol.BreachProtocol.id("textures/entity/mca/skin/" + (e.isFemale() ? "female" : "male") + "/" + e.skinIndex() + ".png");
    }
}
