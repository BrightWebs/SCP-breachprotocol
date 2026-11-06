package com.breachprotocol.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import com.breachprotocol.registry.ModEffects;

import java.util.List;

/** Uranium fuel rod (~20 minutes of reactor time). Mildly radioactive to carry unless you wear a full set of armor. */
public class FuelRodItem extends Item {
    public FuelRodItem(Properties props) {
        super(props);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!level.isClientSide && level.getGameTime() % 100 == 0 && entity instanceof LivingEntity le) {
            int armor = 0;
            for (ItemStack a : le.getArmorSlots()) if (!a.isEmpty()) armor++;
            if (armor < 4) le.addEffect(new MobEffectInstance(ModEffects.h(ModEffects.RADIATION), 120, 0));
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Load into a Fission Reactor Core. Radioactive: wear full armor.").withStyle(ChatFormatting.GREEN));
    }
}
