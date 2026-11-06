package com.breachprotocol.client.render;

import com.breachprotocol.BreachProtocol;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;

/** Generic renderer: one model, one texture, optional uniform scale. */
public class ScpRenderer<T extends Mob, M extends EntityModel<T>> extends MobRenderer<T, M> {
    private final ResourceLocation texture;
    private final float scale;

    public ScpRenderer(EntityRendererProvider.Context ctx, M model, float shadow, float scale, String textureName) {
        super(ctx, model, shadow * scale);
        this.texture = BreachProtocol.id("textures/entity/" + textureName + ".png");
        this.scale = scale;
    }

    @Override
    protected void scale(T entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(scale, scale, scale);
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return texture;
    }
}
