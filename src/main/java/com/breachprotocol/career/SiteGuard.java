package com.breachprotocol.career;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.entity.NpcSeat;
import com.breachprotocol.entity.Seats;
import com.breachprotocol.world.Site01;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/**
 * Site-01 cannot be altered by anyone but the Administrator (no breaking, no explosions), and the
 * chairs of the Council belong to their owners: the head chair to the Administrator, each Overseer
 * chair to its Overseer. A chair someone is already sitting in can't be taken.
 */
@EventBusSubscriber(modid = BreachProtocol.MODID)
public final class SiteGuard {
    private SiteGuard() {}

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel l) || !Site01.isInside(l, event.getPos())) return;
        if (event.getPlayer() != null && Site01.isAdministrator(event.getPlayer())) return;
        event.setCanceled(true);
        if (event.getPlayer() != null)
            event.getPlayer().displayClientMessage(Component.literal("Site-01 is sealed. Only the Administrator can alter it.").withStyle(ChatFormatting.DARK_PURPLE), true);
    }

    @SubscribeEvent
    public static void onExplode(ExplosionEvent.Detonate event) {
        Level l = event.getLevel();
        if (l.isClientSide) return;
        event.getAffectedBlocks().removeIf(p -> Site01.isInside(l, p));
    }

    @SubscribeEvent
    public static void onUseChair(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide || !(event.getEntity() instanceof ServerPlayer p)) return;
        BlockPos pos = event.getPos();
        if (!Seats.isSeat(event.getLevel().getBlockState(pos))) return;
        String refuse = null;
        for (Entity e : event.getLevel().getEntitiesOfClass(NpcSeat.class, new AABB(pos).inflate(0.1)))
            if (e.isVehicle() && Seats.chairOf(e).equals(pos)) refuse = e.getFirstPassenger() != null ? e.getFirstPassenger().getName().getString() + " is sitting there." : "Someone is sitting there.";
        if (refuse == null && event.getLevel() instanceof ServerLevel sl && sl.dimension() == Level.OVERWORLD) {
            int seat = Site01.councilSeatAt(sl, pos);
            if (seat == 0 && Career.rank(p) != Rank.ADMINISTRATOR && !Career.isAscended(p)) refuse = "That chair is the Administrator's.";
            else if (seat > 0 && !p.getUUID().toString().equals(Site01.data(p.server).holders.get(seat)))
                refuse = "That is the chair of " + O5.seat(seat).sender() + ".";
        }
        if (refuse != null) {
            p.displayClientMessage(Component.literal(refuse).withStyle(ChatFormatting.GRAY), true);
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.CONSUME);
        }
    }
}
