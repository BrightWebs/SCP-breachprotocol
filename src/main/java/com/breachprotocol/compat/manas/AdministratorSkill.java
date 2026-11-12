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

/**
 * Administrator: the Administrator's own Unique skill, in Manas Reborn's list beside Shapeshift. Use it and your
 * body fights by itself (career.Autopilot): it hunts what you've marked hostile, flies and blinks to it, and
 * uses everything you have, SCP powers and every Manas skill, the way Manas Reborn's bosses do. Use it again
 * to take yourself back.
 */
public class AdministratorSkill extends Skill {
    public AdministratorSkill() { super(Skill.SkillType.UNIQUE); }

    public ResourceLocation getSkillIcon() { return ResourceLocation.fromNamespaceAndPath(BreachProtocol.MODID, "textures/skill/administrator.png"); }

    public int getModes(ManasSkillInstance instance) { return 1; }

    public int nextMode(LivingEntity e, ManasSkillInstance instance, int mode, boolean reverse) { return 0; }

    public String getModeId(ManasSkillInstance instance, int mode) { return "autopilot"; }

    public Component getModeName(ManasSkillInstance instance, int mode) {
        return Component.literal("Autopilot").withStyle(ChatFormatting.LIGHT_PURPLE);
    }

    public double getManaCost(LivingEntity e, ManasSkillInstance instance, int mode) { return 0; }

    public double getAuraCost(LivingEntity e, ManasSkillInstance instance, int mode) { return 0; }

    public boolean canIgnoreCoolDown(ManasSkillInstance instance, LivingEntity e, int mode) { return true; }

    /** Not something a creature (or the autopilot itself) reaches for in a fight. */
    public int getMobWeight(ManasSkillInstance instance, Mob m, int mode) { return 0; }

    public void onPressed(ManasSkillInstance instance, LivingEntity e, int key, int mode) {
        if (e instanceof ServerPlayer p && key >= 0) com.breachprotocol.career.Autopilot.action(p, "toggle");
    }
}
