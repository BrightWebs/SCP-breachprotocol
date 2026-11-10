package io.github.manasmods.manascore.skill.api;

/** Compile-time stand-in for ManasCore's class (never packaged; the real one is used in game). */
public class ManasSkill {
    public ManasSkillInstance createDefaultInstance() { throw new AbstractMethodError(); }
    public net.minecraft.resources.ResourceLocation getRegistryName() { throw new AbstractMethodError(); }
}
