package io.github.manasmods.manascore.skill.api;

/** Compile-time stand-in (never packaged). */
public class ManasSkillInstance {
    public ManasSkill getSkill() { throw new AbstractMethodError(); }
    public net.minecraft.nbt.CompoundTag getOrCreateTag() { throw new AbstractMethodError(); }
    public void markDirty() { throw new AbstractMethodError(); }
}
