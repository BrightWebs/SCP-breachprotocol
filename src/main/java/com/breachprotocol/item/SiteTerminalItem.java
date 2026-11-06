package com.breachprotocol.item;

import com.breachprotocol.compat.Unleashed;
import com.breachprotocol.registry.ModEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.Comparator;
import java.util.List;

/** Handheld site terminal: a live containment status report for every anomaly within 64 blocks. */
public class SiteTerminalItem extends Item {
    private static final double RANGE = 64;

    public SiteTerminalItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            List<Mob> found = level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(RANGE), Unleashed::isAnomaly);
            found.sort(Comparator.comparingDouble(player::distanceToSqr));
            player.sendSystemMessage(Component.translatable("message.breachprotocol.terminal.header").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            if (found.isEmpty()) {
                player.sendSystemMessage(Component.translatable("message.breachprotocol.terminal.none").withStyle(ChatFormatting.GRAY));
            }
            int loose = 0;
            for (Mob mob : found) {
                boolean contained = ModEffects.has(mob, ModEffects.CONTAINED);
                if (!contained && Unleashed.canBeContained(mob)) loose++;
                String cls = Unleashed.objectClass(mob);
                MutableComponent status = contained
                        ? Component.translatable("message.breachprotocol.terminal.contained").withStyle(ChatFormatting.AQUA)
                        : Component.translatable("message.breachprotocol.terminal.loose").withStyle(ChatFormatting.RED);
                MutableComponent line = Component.literal(" " + Unleashed.designation(mob) + " ").withStyle(ChatFormatting.WHITE)
                        .append(Component.literal("[" + cls + "] ").withStyle(Unleashed.classColor(cls)))
                        .append(status)
                        .append(Component.literal(String.format("  %.0fm", Math.sqrt(player.distanceToSqr(mob)))).withStyle(ChatFormatting.DARK_GRAY));
                Component extra = Unleashed.statusNote(mob);
                if (extra != null) line.append(Component.literal("  ")).append(extra);
                player.sendSystemMessage(line);
            }
            if (loose > 0) {
                player.sendSystemMessage(Component.translatable("message.breachprotocol.terminal.warning", loose).withStyle(ChatFormatting.DARK_RED));
            }
            player.getCooldowns().addCooldown(this, 40);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.breachprotocol.site_terminal.desc").withStyle(ChatFormatting.GRAY));
    }
}
