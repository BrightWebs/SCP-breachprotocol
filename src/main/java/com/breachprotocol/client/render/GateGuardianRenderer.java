package com.breachprotocol.client.render;

import com.breachprotocol.client.model.GateGuardianModel;
import com.breachprotocol.entity.GateGuardianEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.BlockPos;

/** Huge and self-lit: the Guardian glows in the dark. */
public class GateGuardianRenderer extends ScpRenderer<GateGuardianEntity, GateGuardianModel> {
    public GateGuardianRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new GateGuardianModel(ctx.bakeLayer(GateGuardianModel.LAYER)), 0.0F, 2.6F, "gate_guardian");
    }

    @Override
    protected int getBlockLightLevel(GateGuardianEntity entity, BlockPos pos) {
        return 15;
    }
}
