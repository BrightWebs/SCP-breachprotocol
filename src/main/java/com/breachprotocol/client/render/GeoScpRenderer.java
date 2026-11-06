package com.breachprotocol.client.render;

import com.breachprotocol.BreachProtocol;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * One renderer for every animated anomaly. Looks up geo/entity/NAME.geo.json,
 * animations/entity/NAME.animation.json and textures/entity/NAME.png (+ _glowmask.png).
 */
public class GeoScpRenderer<T extends Mob & GeoAnimatable> extends GeoEntityRenderer<T> {
    private final boolean fullBright;

    public GeoScpRenderer(EntityRendererProvider.Context ctx, String name, float scale, float shadow, boolean turnsHead, boolean glowmask, boolean fullBright) {
        super(ctx, new DefaultedEntityGeoModel<>(BreachProtocol.id(name), turnsHead));
        this.fullBright = fullBright;
        this.shadowRadius = shadow * scale;
        withScale(scale);
        if (glowmask) addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    protected int getBlockLightLevel(T entity, BlockPos pos) {
        return fullBright ? 15 : super.getBlockLightLevel(entity, pos);
    }
}
