package com.breachprotocol.client;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.network.ClientCareerState;
import com.breachprotocol.network.PhoneActionPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** In another form, your attacks still land: what's under the crosshair (or just in front of you) is sent to the server as a blow from the form. */
@EventBusSubscriber(modid = BreachProtocol.MODID, value = Dist.CLIENT)
public final class ShapeAttack {
    private ShapeAttack() {}

    private static net.minecraft.client.KeyMapping race;
    private static boolean raceWas;

    /** Manas Reborn's race key: in a creature's form, the creature's own moves (crouch: the next one). */
    @SubscribeEvent
    public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (race == null) for (net.minecraft.client.KeyMapping k : mc.options.keyMappings) if (k.getName().equals("manas_reborn.keybinding.race_ability")) { race = k; break; }
        if (race == null) return;
        boolean down = race.isDown() && mc.screen == null;
        if (down && !raceWas && ClientCareerState.tag.getBoolean("shapeCreature"))
            PacketDistributor.sendToServer(new PhoneActionPayload("shape:move:" + (mc.player.isShiftKeyDown() ? 1 : 0), 0));
        raceWas = down;
    }

    @SubscribeEvent
    public static void attack(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isAttack()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || ClientCareerState.tag.getString("shapeForm").isEmpty()) return;
        Entity target = mc.hitResult instanceof EntityHitResult eh ? eh.getEntity() : null;
        if (target == null) {
            Entity cam = mc.getCameraEntity() == null ? mc.player : mc.getCameraEntity();
            Vec3 eye = cam.getEyePosition(), look = cam.getViewVector(1F), end = eye.add(look.scale(5));
            EntityHitResult hit = ProjectileUtil.getEntityHitResult(cam, eye, end, cam.getBoundingBox().expandTowards(look.scale(5)).inflate(1), e -> e instanceof LivingEntity && e != mc.player && e.isAlive(), 25);
            if (hit != null) target = hit.getEntity();
        }
        if (target instanceof LivingEntity) PacketDistributor.sendToServer(new PhoneActionPayload("shape:hit:" + target.getId(), 0));
    }
}
