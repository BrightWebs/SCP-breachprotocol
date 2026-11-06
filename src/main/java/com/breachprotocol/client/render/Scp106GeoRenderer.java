package com.breachprotocol.client.render;

import com.breachprotocol.entity.Scp106Entity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

/** SCP-106 sinks into (and rises out of) the floor when it phases. */
public class Scp106GeoRenderer extends GeoScpRenderer<Scp106Entity> {
    public Scp106GeoRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, "scp_106", 1.0F, 0.5F, true, true, false);
    }

    @Override
    public void render(Scp106Entity entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
        poseStack.pushPose();
        poseStack.translate(0, -entity.sinkProgress(partialTick) * 2.1F, 0);
        super.render(entity, yaw, partialTick, poseStack, buffers, light);
        poseStack.popPose();
    }
}
