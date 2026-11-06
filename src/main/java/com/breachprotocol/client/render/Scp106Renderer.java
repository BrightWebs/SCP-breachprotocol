package com.breachprotocol.client.render;

import com.breachprotocol.client.model.Scp106Model;
import com.breachprotocol.entity.Scp106Entity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

/** Sinks SCP-106 into the floor while it phases. */
public class Scp106Renderer extends ScpRenderer<Scp106Entity, Scp106Model> {
    public Scp106Renderer(EntityRendererProvider.Context ctx) {
        super(ctx, new Scp106Model(ctx.bakeLayer(Scp106Model.LAYER)), 0.5F, 1.0F, "scp_106");
    }

    @Override
    protected void scale(Scp106Entity entity, PoseStack poseStack, float partialTick) {
        float sink = entity.sinkProgress(partialTick);
        poseStack.translate(0, -sink * 2.1F, 0);
        super.scale(entity, poseStack, partialTick);
    }
}
