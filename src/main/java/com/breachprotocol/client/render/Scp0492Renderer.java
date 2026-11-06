package com.breachprotocol.client.render;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.entity.Scp0492Entity;
import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

public class Scp0492Renderer extends HumanoidMobRenderer<Scp0492Entity, Scp0492Renderer.Model> {
    private static final ResourceLocation TEXTURE = BreachProtocol.id("textures/entity/scp_049_2.png");

    public Scp0492Renderer(EntityRendererProvider.Context ctx) {
        super(ctx, new Model(ctx.bakeLayer(ModelLayers.ZOMBIE)), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(Scp0492Entity entity) {
        return TEXTURE;
    }

    /** Vanilla humanoid with the shambling outstretched arms. */
    public static class Model extends HumanoidModel<Scp0492Entity> {
        public Model(ModelPart root) {
            super(root);
        }

        @Override
        public void setupAnim(Scp0492Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
            super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
            AnimationUtils.animateZombieArms(this.leftArm, this.rightArm, entity.isAggressive(), this.attackTime, ageInTicks);
        }
    }
}
