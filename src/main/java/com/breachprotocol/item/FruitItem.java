package com.breachprotocol.item;

import com.breachprotocol.garden.Garden;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** A fruit of the Garden. The two trees' fruits do far more than feed you. */
public class FruitItem extends Item {
    public enum Kind { PLAIN, LIFE, KNOWLEDGE }

    private final Kind kind;

    public FruitItem(Kind kind, int food, float sat, Properties p) {
        super(p.food(kind == Kind.PLAIN ? new FoodProperties.Builder().nutrition(food).saturationModifier(sat).build()
                : new FoodProperties.Builder().nutrition(food).saturationModifier(sat).alwaysEdible().build()));
        this.kind = kind;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity who) {
        ItemStack out = super.finishUsingItem(stack, level, who);
        if (!level.isClientSide && who instanceof ServerPlayer p) {
            switch (kind) {
                case LIFE -> Garden.ateLife(p);
                case KNOWLEDGE -> Garden.ateKnowledge(p);
                default -> { if (Garden.inside(level)) p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 4, 0)); }
            }
        }
        return out;
    }

    @Override
    public boolean isFoil(ItemStack stack) { return kind != Kind.PLAIN; }
}
