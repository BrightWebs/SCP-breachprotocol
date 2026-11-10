package com.breachprotocol.client;

import com.breachprotocol.BreachProtocol;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import org.joml.Quaternionf;

import java.util.HashSet;
import java.util.Set;

/** A tall anomaly squeezing through a cell door (career.Walkback) is drawn bent double under the lintel. */
@EventBusSubscriber(modid = BreachProtocol.MODID, value = Dist.CLIENT)
public final class Stoop {
    private Stoop() {}

    private static final Set<Integer> BENT = new HashSet<>();

    @SubscribeEvent
    public static void pre(RenderLivingEvent.Pre<?, ?> event) {
        LivingEntity e = event.getEntity();
        if (e instanceof Player || e.getPose() != Pose.CROUCHING || e.getBbHeight() <= 2.0F) return;
        if (!BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getNamespace().equals("scp_unleashed")) return;
        float yaw = Mth.rotLerp(event.getPartialTick(), e.yBodyRotO, e.yBodyRot) * Mth.DEG_TO_RAD;
        float bend = 62F * Mth.DEG_TO_RAD;
        var ps = event.getPoseStack();
        ps.pushPose();
        // hinge at the hips: the upper body folds forward and down to clear a two-block door
        float hips = e.getBbHeight() * 0.42F;
        ps.translate(0, hips, 0);
        ps.mulPose(new Quaternionf().rotateAxis(bend, Mth.cos(yaw), 0, Mth.sin(yaw)));
        ps.translate(0, -hips, 0);
        ps.translate(0, -e.getBbHeight() * 0.12F, 0);
        BENT.add(e.getId());
    }

    @SubscribeEvent
    public static void post(RenderLivingEvent.Post<?, ?> event) {
        if (BENT.remove(event.getEntity().getId())) event.getPoseStack().popPose();
    }
}
