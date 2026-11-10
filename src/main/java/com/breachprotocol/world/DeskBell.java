package com.breachprotocol.world;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.entity.FoundationStaff;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The bell on the Executive Secretary's counter. Ring it (or any bell within a few blocks of her
 * desk) and she drops what she's doing and hurries to whoever rang it, wherever in the building she
 * is: up or down in the lift if she has to.
 */
@EventBusSubscriber(modid = BreachProtocol.MODID)
public final class DeskBell {
    private DeskBell() {}

    /** Who last rang for her, so a second ring while she's already on her way doesn't re-send her. */
    private static final Map<UUID, Long> RUNG = new HashMap<>();

    @SubscribeEvent
    public static void ring(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !(p.level() instanceof ServerLevel l)) return;
        BlockPos at = event.getPos();
        if (!(l.getBlockState(at).getBlock() instanceof BellBlock)) return;
        FoundationStaff sec = null;
        double best = Double.MAX_VALUE;
        // her desk is where her post is: the bell has to be at it
        for (FoundationStaff f : l.getEntitiesOfClass(FoundationStaff.class, new AABB(at).inflate(160, 96, 160),
                f -> f.isAlive() && f.role() == FoundationStaff.Role.SECRETARY && f.post() != null && f.post().distSqr(at) < 8 * 8)) {
            double d = f.distanceToSqr(p);
            if (d < best) { best = d; sec = f; }
        }
        if (sec == null) return;
        long now = l.getGameTime();
        Long last = RUNG.get(sec.getUUID());
        String her = sec.getName().getString();
        if (last != null && now - last < 60) return; // the bell is still ringing
        RUNG.put(sec.getUUID(), now);
        if (sec.distanceToSqr(p) < 4 * 4) {
            sec.getLookControl().setLookAt(p);
            p.displayClientMessage(Component.literal(her + ": Yes? I'm right here.").withStyle(ChatFormatting.LIGHT_PURPLE), true);
            return;
        }
        // she hurries: up from her chair, quick on her feet, the lift if it's another floor
        sec.travel(p.blockPosition(), false);
        sec.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 40, 1, false, false));
        p.displayClientMessage(Component.literal(her + " heard the bell. She's coming.").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), true);
    }
}
