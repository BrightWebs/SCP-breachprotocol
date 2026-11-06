package com.breachprotocol.item;

import com.breachprotocol.registry.ModEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * SCP-207: a bottle of cola that makes you dangerously fast.
 * Each extra bottle while the effect is active stacks another level (max V):
 * more speed, faster attacks, and the damage ticks come faster too.
 */
public class Scp207Item extends ConsumableItem {
    public static final int MAX_AMP = 4;

    public Scp207Item(Properties props) {
        super(props, UseAnim.DRINK, 32);
    }

    @Override
    protected void consume(ItemStack stack, Level level, LivingEntity user) {
        MobEffectInstance current = user.getEffect(ModEffects.h(ModEffects.COLA_FEVER));
        int amp = current == null ? 0 : Math.min(MAX_AMP, current.getAmplifier() + 1);
        user.addEffect(new MobEffectInstance(ModEffects.h(ModEffects.COLA_FEVER), 20 * 180, amp));
        if (user instanceof Player p && amp >= 2) {
            p.displayClientMessage(Component.translatable("message.breachprotocol.scp207.warn", amp + 1).withStyle(ChatFormatting.DARK_RED), true);
        }
    }
}
