package com.breachprotocol.client.render;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.entity.O5MemberEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/** Overseers sit at the council table: humanoid model, forced into a seated pose. */
public class O5MemberRenderer extends HumanoidMobRenderer<O5MemberEntity, O5MemberRenderer.Model> {
    private static final ResourceLocation TEXTURE = BreachProtocol.id("textures/entity/o5_member.png");

    public O5MemberRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new Model(ctx.bakeLayer(ModelLayers.ZOMBIE)), 0.4F);
    }

    @Override
    public ResourceLocation getTextureLocation(O5MemberEntity entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(O5MemberEntity entity, PoseStack poseStack, float partialTick) {
        poseStack.translate(0, -0.5, 0);
    }

    public static class Model extends HumanoidModel<O5MemberEntity> {
        public Model(ModelPart root) {
            super(root);
        }

        @Override
        public void setupAnim(O5MemberEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
            this.riding = true;
            super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
            this.rightArm.xRot = -0.6F;
            this.leftArm.xRot = -0.6F;
        }
    }
}
