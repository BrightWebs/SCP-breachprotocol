package com.breachprotocol.item;

import com.breachprotocol.blink.BlinkData;
import com.breachprotocol.registry.ModAttachments;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/** SCP-500: cures everything. Every effect, full health, fire, and a little extra. */
public class Scp500Item extends ConsumableItem {
    public Scp500Item(Properties props) {
        super(props, UseAnim.EAT, 16);
    }

    @Override
    protected void consume(ItemStack stack, Level level, LivingEntity user) {
        user.removeAllEffects();
        user.setHealth(user.getMaxHealth());
        user.clearFire();
        user.setTicksFrozen(0);
        user.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 20 * 120, 1));
        if (user instanceof Player player) {
            player.getFoodData().eat(20, 1.0F);
            BlinkData data = player.getData(ModAttachments.BLINK);
            data.meter = BlinkData.MAX;
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
