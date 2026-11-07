package com.breachprotocol.client.render;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.entity.FoundationStaff;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/** Foundation personnel: a humanoid in their department's uniform (three looks per department, grey-haired when old). */
public class StaffRenderer extends HumanoidMobRenderer<FoundationStaff, HumanoidModel<FoundationStaff>> {
    private static final ResourceLocation[][] SKINS = new ResourceLocation[FoundationStaff.Role.values().length][3];
    private static final ResourceLocation[][] OLD = new ResourceLocation[FoundationStaff.Role.values().length][3];
    private static final ResourceLocation[][] SKINS_F = new ResourceLocation[FoundationStaff.Role.values().length][3];
    private static final ResourceLocation[][] OLD_F = new ResourceLocation[FoundationStaff.Role.values().length][3];

    static {
        for (FoundationStaff.Role r : FoundationStaff.Role.values())
            for (int v = 0; v < 3; v++)
            {
                SKINS[r.ordinal()][v] = BreachProtocol.id("textures/entity/staff/" + r.name().toLowerCase(java.util.Locale.ROOT) + "_" + v + ".png");
                OLD[r.ordinal()][v] = BreachProtocol.id("textures/entity/staff/" + r.name().toLowerCase(java.util.Locale.ROOT) + "_" + v + "_old.png");
                boolean sec = r == FoundationStaff.Role.SECRETARY;
                SKINS_F[r.ordinal()][v] = sec ? SKINS[r.ordinal()][v] : BreachProtocol.id("textures/entity/staff/" + r.name().toLowerCase(java.util.Locale.ROOT) + "_" + v + "_f.png");
                OLD_F[r.ordinal()][v] = sec ? OLD[r.ordinal()][v] : BreachProtocol.id("textures/entity/staff/" + r.name().toLowerCase(java.util.Locale.ROOT) + "_" + v + "_f_old.png");
            }
    }

    public StaffRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new StaffModel(ctx.bakeLayer(StaffModel.LAYER)), 0.4F);
        addLayer(new StaffLooksLayer(this, new StaffModel(ctx.bakeLayer(StaffModel.OUTER))));
    }

    @Override
    protected void scale(FoundationStaff e, com.mojang.blaze3d.vertex.PoseStack pose, float partial) {
        float k = e.heightScale();
        pose.scale(k, k, k);
    }

    @Override
    public ResourceLocation getTextureLocation(FoundationStaff e) {
        boolean f = e.isFemale();
        return (e.elder() ? (f ? OLD_F : OLD) : (f ? SKINS_F : SKINS))[e.role().ordinal()][Math.floorMod(e.variant(), 3)];
    }
}
