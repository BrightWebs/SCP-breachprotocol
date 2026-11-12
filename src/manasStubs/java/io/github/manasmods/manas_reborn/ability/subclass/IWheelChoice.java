package io.github.manasmods.manas_reborn.ability.subclass;

import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Compile-time stand-in (never packaged): Manas Reborn's choice wheel, as Spirit Envoy uses it. */
public interface IWheelChoice {
    Component getWheelChoiceTitle();
    List<Component> getWheelChoices(ManasSkillInstance instance);
    int getWheelChoice(ManasSkillInstance instance);
    void setWheelChoice(ManasSkillInstance instance, LivingEntity entity, int choice);
    default int getWheelChoiceColor(ManasSkillInstance instance, int choice) { return 0xFFFFFF; }
    default boolean hasCustomWheel() { return false; }
    default ItemStack getWheelChoiceIcon(ManasSkillInstance instance, int choice) { return ItemStack.EMPTY; }
    default List<Component> getWheelChoiceDetails(ManasSkillInstance instance, LivingEntity entity, int choice) { return List.of(); }
    default boolean canPickWheelChoice(ManasSkillInstance instance, LivingEntity entity, int choice) { return true; }
}
