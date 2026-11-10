package com.breachprotocol.compat.manas;

import com.breachprotocol.BreachProtocol;
import io.github.manasmods.manas_reborn.ability.skill.Skill;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import java.util.List;

/**
 * Shapeshift: the Administrator's Unique skill, in Manas Reborn's own list. Two modes, switched the way
 * any Manas skill's modes are:
 * <ul>
 * <li><b>Mimic</b>: use it and become, at once, whatever you're looking at, creature or block. Crouch
 *     and use it to come back.</li>
 * <li><b>Shapeshift</b>: use it for the menu: your forms (or everything), Polymorph for creatures,
 *     Mimic for objects, searchable, with the way back in it. Crouch and use it to come back at once.</li>
 * </ul>
 * The forms are Manas Reborn's own (Polymorph's for creatures, Mimicry's for blocks).
 *
 * <p>The methods below are matched to Manas's by name and signature; they override them at run time.</p>
 */
public class ShapeshiftSkill extends Skill {
    public ShapeshiftSkill() { super(Skill.SkillType.UNIQUE); }

    public ResourceLocation getSkillIcon() { return ResourceLocation.fromNamespaceAndPath(BreachProtocol.MODID, "textures/skill/shapeshift.png"); }

    public int getModes(ManasSkillInstance instance) { return 2; }

    public List<Integer> getModeLearningList(ManasSkillInstance instance) { return List.of(0, 1); }

    public String getModeId(ManasSkillInstance instance, int mode) { return mode == 0 ? "mimic" : "shapeshift"; }

    public Component getModeName(ManasSkillInstance instance, int mode) {
        return Component.literal(mode == 0 ? "Mimic" : "Shapeshift").withStyle(mode == 0 ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.DARK_PURPLE);
    }

    public double getManaCost(LivingEntity e, ManasSkillInstance instance, int mode) { return 0; }

    public double getAuraCost(LivingEntity e, ManasSkillInstance instance, int mode) { return 0; }

    public boolean canIgnoreCoolDown(ManasSkillInstance instance, LivingEntity e, int mode) { return true; }

    public int getMobWeight(ManasSkillInstance instance, Mob m, int mode) { return 0; }

    public void onPressed(ManasSkillInstance instance, LivingEntity e, int key, int mode) {
        if (e instanceof ServerPlayer p) ManasBridge.pressed(p, mode);
    }
}
