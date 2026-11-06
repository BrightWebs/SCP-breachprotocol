package com.breachprotocol.compat;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.career.Career;
import com.breachprotocol.career.Clearance;
import com.breachprotocol.career.Photos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = BreachProtocol.MODID)
public final class AddonEvents {
    private AddonEvents() {}

    /** Every SCP Unleashed D-Class can take phone orders. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide) return;
        if (event.getEntity() instanceof PathfinderMob mob && UnleashedApi.isDClass(mob)) {
            mob.goalSelector.addGoal(0, new OrderGoal(mob));
        }
    }

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p.isSpectator()) return;
        if (p.tickCount % 10 == 0) Photos.checkFrames(p);
        // SCP-001-1 knows its master: the Gate Guardian holds its blades for the Ascendant.
        if (p.tickCount % 40 == 0 && Career.isAscended(p)) {
            for (Entity e : p.level().getEntities(p, p.getBoundingBox().inflate(96), UnleashedApi::isGateGuardian)) {
                UnleashedApi.calmGuardian(e, 200);
            }
        }
    }

    /** A rift that was drawn out (extraction complete) counts as an assignment for whoever extracted it. */
    @SubscribeEvent
    public static void onLeave(net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide || !UnleashedApi.isRift(event.getEntity())) return;
        Entity rift = event.getEntity();
        if (UnleashedApi.riftExtraction(rift) < 0.98F) return;
        ServerPlayer best = null;
        double bd = 32 * 32;
        for (net.minecraft.world.entity.player.Player pl : event.getLevel().players()) {
            if (pl instanceof ServerPlayer sp && sp.distanceToSqr(rift) < bd) { bd = sp.distanceToSqr(rift); best = sp; }
        }
        if (best == null) return;
        Career.message(best, com.breachprotocol.career.O5.O5_9, "Rift contained. The fragment is yours. Good work.");
        Career.changeStability(best, 3);
        Career.commend(best);
        Career.sync(best);
    }

    /** Swipe an SCP Unleashed keycard scanner with your phone: it presents the fused keycard. */
    @SubscribeEvent
    public static void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack held = event.getItemStack();
        if (!Clearance.isPhone(held)) return;
        BlockState state = event.getLevel().getBlockState(event.getPos());
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (!Unleashed.MODID.equals(id.getNamespace()) || !id.getPath().equals("keycard_scanner")) return;
        Item card = Clearance.fusedCard(held);
        if (card == null) return; // no fused card: let the scanner show what level it needs
        ItemInteractionResult r = state.useItemOn(new ItemStack(card), event.getLevel(), event.getEntity(), event.getHand(), event.getHitVec());
        event.setCanceled(true);
        event.setCancellationResult(r.result());
    }
}
