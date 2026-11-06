package com.breachprotocol.item;

import com.breachprotocol.blink.BlinkData;
import com.breachprotocol.registry.ModAttachments;
import com.breachprotocol.registry.ModEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/** Eyedrops: refills your blink meter and stops it draining for 30 seconds. */
public class EyedropsItem extends ConsumableItem {
    public EyedropsItem(Properties props) {
        super(props, UseAnim.DRINK, 20);
    }

    @Override
    protected void consume(ItemStack stack, Level level, LivingEntity user) {
        user.addEffect(new MobEffectInstance(ModEffects.h(ModEffects.CLEAR_EYES), 20 * 30, 0));
        if (user instanceof Player player) {
            player.getData(ModAttachments.BLINK).meter = BlinkData.MAX;
        }
    }
}
