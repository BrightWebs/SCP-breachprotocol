package com.breachprotocol.client;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.world.FlightState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Glowing eyes (heat vision) drawn as part of the head itself: on the skin's eye pixels, moving with the head
 * however the body's posed (leaning into a run, lying in flight, turning to look), so they never trail behind.
 * Where they were drawn this frame (in the world) is kept for the beams to start from.
 */
public class EyeGlowLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    /** Each player's two eyes (world), and the frame they were drawn on. */
    public static final Map<Integer, Vec3[]> EYES = new ConcurrentHashMap<>();
    public static final Map<Integer, Long> FRAME = new ConcurrentHashMap<>();
    public static long frame;

    public EyeGlowLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) { super(parent); }

    @Override
    public void render(PoseStack ps, MultiBufferSource buf, int light, AbstractClientPlayer p, float limb, float limbAmt, float pt, float age, float yaw, float pitch) {
        Integer beaming = FlightState.HEAT.get(p.getId());
        if (beaming == null && !FlightState.EYES.contains(p.getId()) || p.isInvisible()) return;
        Minecraft mc = Minecraft.getInstance();
        boolean gui = mc.screen != null && buf != mc.renderBuffers().bufferSource();
        ps.pushPose();
        getParentModel().head.translateAndRotate(ps);
        Matrix4f m = ps.last().pose();
        // on the skin, the eyes are the face's 3rd and 6th columns, its 5th and 6th rows; the face is at z = -4 pixels
        final float PX = 1 / 16F;
        float flick = 0.85F + (float) Math.sin((p.tickCount + pt) * 1.7) * 0.15F;
        int tier = beaming == null ? 0 : beaming;
        VertexConsumer vc = buf.getBuffer(RenderType.lightning());
        Vec3[] at = new Vec3[2];
        for (int i = 0; i < 2; i++) {
            float x = (i == 0 ? -1.5F : 1.5F) * PX, y = -3 * PX, z = -4.02F * PX;
            quad(vc, m, x, y, z, 0.6F * PX, 1.1F * PX, 1F, 0.8F, 0.65F, 1F);                                   // the lit pixel
            float b = (1.6F + tier * 0.35F) * PX * (beaming == null ? 1 : 1.25F);
            quad(vc, m, x, y, z - 0.002F, b, b, 1F, 0.12F, 0.05F, (beaming == null ? 0.45F : 0.6F) * flick);   // its bloom
            Vector3f w = m.transformPosition(new Vector3f(x, y, z - 0.05F));
            Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
            at[i] = new Vec3(w.x + cam.x, w.y + cam.y, w.z + cam.z);
        }
        if (!gui) { EYES.put(p.getId(), at); FRAME.put(p.getId(), frame); }
        ps.popPose();
    }

    private static void quad(VertexConsumer vc, Matrix4f m, float x, float y, float z, float hw, float hh, float r, float g, float b, float a) {
        float[][] c = {{-hw, -hh}, {hw, -hh}, {hw, hh}, {-hw, hh}};
        for (int s = 0; s < 2; s++) for (int i = 0; i < 4; i++) {
            float[] q = c[s == 0 ? i : 3 - i];
            vc.addVertex(m, x + q[0], y + q[1], z).setColor(r, g, b, a);
        }
    }

    @EventBusSubscriber(modid = BreachProtocol.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Reg {
        @SubscribeEvent
        @SuppressWarnings({"unchecked", "rawtypes"})
        public static void layers(EntityRenderersEvent.AddLayers event) {
            for (PlayerSkin.Model skin : event.getSkins()) {
                if (event.getSkin(skin) instanceof LivingEntityRenderer r) r.addLayer(new EyeGlowLayer(r));
            }
        }
    }
}
