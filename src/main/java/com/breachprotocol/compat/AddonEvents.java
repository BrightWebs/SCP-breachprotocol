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
                UnleashedApi.calmGuardian(e, 400);
                if (p.distanceTo(e) <= 48) praise(p, e);
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

    private static final String[] PRAISE = {
            "The blades are lowered. Pass, Master.",
            "SCP-001-1 bows its head as you approach.",
            "The Guardian kneels. The Garden has waited for you.",
            "\"You were always meant to walk this way.\"",
            "The light around the Gate turns gold at your step.",
            "It speaks without words: welcome home.",
            "Nothing here will raise a hand to you."};

    /** SCP-001-1 greets SCP-Omega: a title when you arrive, praise while you're near, light at its feet. */
    private static void praise(ServerPlayer p, Entity guardian) {
        long now = p.level().getGameTime();
        var pd = p.getPersistentData();
        if (now - pd.getLong("bp_gate_greeted") > 20 * 120) {
            pd.putLong("bp_gate_greeted", now);
            p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(10, 60, 20));
            p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(net.minecraft.network.chat.Component.literal("SCP-001-1 bows").withStyle(net.minecraft.ChatFormatting.GOLD)));
            p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(net.minecraft.network.chat.Component.literal("The Gate opens for its master").withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE)));
            p.level().playSound(null, guardian.blockPosition(), net.minecraft.sounds.SoundEvents.BELL_RESONATE, net.minecraft.sounds.SoundSource.HOSTILE, 3.0F, 0.6F);
            p.level().playSound(null, p.blockPosition(), net.minecraft.sounds.SoundEvents.BEACON_POWER_SELECT, net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 0.8F);
        }
        if (now - pd.getLong("bp_gate_praise") >= 20 * 7) {
            pd.putLong("bp_gate_praise", now);
            int i = (int) ((now / (20 * 7)) % PRAISE.length);
            p.displayClientMessage(net.minecraft.network.chat.Component.literal(PRAISE[i]).withStyle(net.minecraft.ChatFormatting.GOLD, net.minecraft.ChatFormatting.ITALIC), true);
        }
        if (p.level() instanceof net.minecraft.server.level.ServerLevel sl) {
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, guardian.getX(), guardian.getY() + 1, guardian.getZ(), 20, 2.0, 0.5, 2.0, 0.02);
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.GLOW, p.getX(), p.getY() + 1, p.getZ(), 6, 0.4, 0.6, 0.4, 0.01);
        }
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
