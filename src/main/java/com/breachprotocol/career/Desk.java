package com.breachprotocol.career;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.network.DialoguePayload;
import com.breachprotocol.world.Site01;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Set;

/**
 * The Administrator's computer: any Foundation monitor or terminal at a site (the one on the
 * Administrator's desk included) opens the Foundation desktop for the Administrator. Sneak to use
 * the device normally.
 */
@EventBusSubscriber(modid = BreachProtocol.MODID)
public final class Desk {
    private Desk() {}

    private static final Set<String> SCREENS = Set.of("crt_monitor", "computer_terminal", "security_monitor", "wall_monitor");

    @SubscribeEvent
    public static void click(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide || event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(event.getEntity() instanceof ServerPlayer p) || p.isShiftKeyDown()) return;
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(event.getLevel().getBlockState(event.getPos()).getBlock());
        if (!id.getNamespace().equals("scp_unleashed") || !SCREENS.contains(id.getPath())) return;
        if (Sites.at(event.getLevel(), event.getPos()) == null && !Site01.isInside(event.getLevel(), event.getPos())) return;
        if (Career.rank(p) != Rank.ADMINISTRATOR && !Career.isAscended(p)) {
            if (Career.rank(p).atLeast(Rank.OVERSEER))
                p.displayClientMessage(Component.literal("ACCESS RESTRICTED · Administrator credentials required.").withStyle(ChatFormatting.RED), true);
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        open(p);
    }

    public static void open(ServerPlayer p) {
        Career.sync(p);
        CompoundTag t = new CompoundTag();
        t.putBoolean("desktop", true);
        PacketDistributor.sendToPlayer(p, new DialoguePayload(t));
    }
}
