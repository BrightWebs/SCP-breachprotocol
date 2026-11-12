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
public class AdministratorSkill extends Skill implements io.github.manasmods.manas_reborn.ability.subclass.IWheelChoice {
    public AdministratorSkill() { super(Skill.SkillType.UNIQUE); }

    public ResourceLocation getSkillIcon() { return ResourceLocation.fromNamespaceAndPath(BreachProtocol.MODID, "textures/skill/administrator.png"); }

    public int getModes(ManasSkillInstance instance) { return 3; }

    public int nextMode(LivingEntity e, ManasSkillInstance instance, int mode, boolean reverse) { return reverse ? (mode + 2) % 3 : (mode + 1) % 3; }

    public String getModeId(ManasSkillInstance instance, int mode) { return mode == 0 ? "autopilot" : mode == 1 ? "heat_vision" : "true_form"; }

    public Component getModeName(ManasSkillInstance instance, int mode) {
        if (mode == 2) return Component.literal("True Form").withStyle(ChatFormatting.DARK_PURPLE);
        return mode == 1 ? Component.literal("Heat Vision").withStyle(ChatFormatting.RED) : Component.literal("Autopilot").withStyle(ChatFormatting.LIGHT_PURPLE);
    }

    public double getManaCost(LivingEntity e, ManasSkillInstance instance, int mode) { return 0; }

    public double getAuraCost(LivingEntity e, ManasSkillInstance instance, int mode) { return 0; }

    public boolean canIgnoreCoolDown(ManasSkillInstance instance, LivingEntity e, int mode) { return true; }

    /** Not something a creature (or the autopilot itself) reaches for in a fight. */
    public int getMobWeight(ManasSkillInstance instance, Mob m, int mode) { return 0; }

    /** Autopilot: use it, on or off; crouch and use it, Hunt or Guard. Heat Vision: hold it to burn (see onHeld). */
    public void onPressed(ManasSkillInstance instance, LivingEntity e, int key, int mode) {
        if (!(e instanceof ServerPlayer p) || key < 0) return;
        if (mode == 2) { com.breachprotocol.career.SkullKing.toggle(p); return; }   // the change, and back
        if (mode == 1) {
            if (p.isShiftKeyDown()) com.breachprotocol.career.HeatVision.eyes(p);     // crouch-use: the eyes glow (or stop)
            else com.breachprotocol.career.HeatVision.holding(p, tier(instance));
        }
        else com.breachprotocol.career.Autopilot.action(p, p.isShiftKeyDown() ? "switch" : "toggle");
    }

    /** Heat Vision, for as long as the key is held: the beams come, and keep coming. */
    public boolean onHeld(ManasSkillInstance instance, LivingEntity e, int heldTicks, int mode) {
        if (mode != 1) return false;
        if (e instanceof ServerPlayer p && !p.isShiftKeyDown()) com.breachprotocol.career.HeatVision.holding(p, tier(instance));
        return true;
    }

    /** Let go: they stop. */
    public void onRelease(ManasSkillInstance instance, LivingEntity e, int key, int mode, int held) {
        if (mode == 1 && e instanceof ServerPlayer p) com.breachprotocol.career.HeatVision.release(p);
    }

    public void onHeldStop(ManasSkillInstance instance, LivingEntity e, int key, int mode) {
        if (mode == 1 && e instanceof ServerPlayer p) com.breachprotocol.career.HeatVision.release(p);
    }

    // ---------------------------------------------------------------- the heat, on Manas Reborn's choice wheel (as Spirit Envoy's spirits are)

    private static int tier(ManasSkillInstance instance) {
        try { return Math.max(0, Math.min(4, instance.getOrCreateTag().getInt("heat"))); } catch (Throwable t) { return 1; }
    }

    private static final String[] NAMES = {"5%  ·  Warm Thread", "15%  ·  Burning Beam", "50%  ·  Lance", "80%  ·  Cutter", "100%  ·  Annihilation"};
    private static final int[] COLOURS = {0xFFC070, 0xFF9040, 0xFF5A20, 0xFF2A20, 0xFF1060};
    private static final String[][] DETAILS = {
            {"Sets alight what it touches.", "Hurts, a little. 16 blocks."},
            {"Real harm, twice the reach.", "The ground where it lands catches."},
            {"Straight through every living thing in its line,", "a boom on each one. 48 blocks."},
            {"Cuts stone and wood, boils water away,", "melts ice and snow. 64 blocks."},
            {"Bores a tunnel through anything breakable", "and blows apart where it ends. 96 blocks."}};

    public Component getWheelChoiceTitle() { return Component.literal("Heat Vision").withStyle(ChatFormatting.RED); }

    public java.util.List<Component> getWheelChoices(ManasSkillInstance instance) {
        java.util.List<Component> out = new java.util.ArrayList<>();
        for (int i = 0; i < NAMES.length; i++) out.add(Component.literal(NAMES[i]));
        return out;
    }

    public int getWheelChoice(ManasSkillInstance instance) { return tier(instance); }

    public void setWheelChoice(ManasSkillInstance instance, LivingEntity e, int choice) {
        try {
            instance.getOrCreateTag().putInt("heat", Math.max(0, Math.min(4, choice)));
            instance.markDirty();
        } catch (Throwable ignored) {}
        if (e instanceof ServerPlayer p)
            p.displayClientMessage(Component.literal("HEAT VISION  ·  " + NAMES[Math.max(0, Math.min(4, choice))]).withStyle(ChatFormatting.RED), true);
    }

    public int getWheelChoiceColor(ManasSkillInstance instance, int choice) { return COLOURS[Math.max(0, Math.min(4, choice))]; }

    public net.minecraft.world.item.ItemStack getWheelChoiceIcon(ManasSkillInstance instance, int choice) {
        net.minecraft.world.item.Item[] it = {net.minecraft.world.item.Items.TORCH, net.minecraft.world.item.Items.BLAZE_POWDER, net.minecraft.world.item.Items.BLAZE_ROD,
                net.minecraft.world.item.Items.MAGMA_CREAM, net.minecraft.world.item.Items.NETHER_STAR};
        return new net.minecraft.world.item.ItemStack(it[Math.max(0, Math.min(4, choice))]);
    }

    public java.util.List<Component> getWheelChoiceDetails(ManasSkillInstance instance, LivingEntity e, int choice) {
        java.util.List<Component> out = new java.util.ArrayList<>();
        for (String l : DETAILS[Math.max(0, Math.min(4, choice))]) out.add(Component.literal(l).withStyle(ChatFormatting.GRAY));
        return out;
    }
}
