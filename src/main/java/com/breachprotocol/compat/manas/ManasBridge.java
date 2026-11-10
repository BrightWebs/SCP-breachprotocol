package com.breachprotocol.compat.manas;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.career.Career;
import io.github.manasmods.manas_reborn.ability.SkillHelper;
import io.github.manasmods.manas_reborn.ability.mimicry.DetectedForm;
import io.github.manasmods.manas_reborn.ability.mimicry.MimicBlockHelper;
import io.github.manasmods.manas_reborn.ability.mimicry.MimicBlockPart;
import io.github.manasmods.manas_reborn.ability.mimicry.MimicFormHelper;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.List;

/**
 * Everything that touches ManasCore and Manas Reborn directly. Only ever loaded when both are installed
 * (career.Shapeshift checks first), so Breach Protocol runs as before without them.
 */
public final class ManasBridge {
    private ManasBridge() {}

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(BreachProtocol.MODID, "shapeshift");
    /** Our layer in Manas Reborn's mimicry: what we put on, we take off, without touching anyone else's. */
    private static final ResourceLocation SOURCE = ResourceLocation.fromNamespaceAndPath(BreachProtocol.MODID, "shapeshift");
    private static final String FORM = "bp_shape_form";
    private static ShapeshiftSkill SKILL;

    public static void register(RegisterEvent event) {
        event.register(SkillAPI.getSkillRegistryKey(), helper -> {
            SKILL = new ShapeshiftSkill();
            helper.register(ID, SKILL);
        });
    }

    public static boolean has(ServerPlayer p) {
        try { return SkillAPI.getSkillsFrom(p).getSkill(ID).isPresent(); } catch (Throwable t) { return false; }
    }

    /** The Administrator has it; it's learned the first time they're seen without it. */
    public static void grant(ServerPlayer p) {
        if (SKILL == null || has(p)) return;
        try { SkillHelper.learnSkill(p, SKILL); } catch (Throwable t) { BreachProtocol.LOGGER.warn("Couldn't grant Shapeshift: {}", t.toString()); }
    }

    // ---------------------------------------------------------------- using it

    static void pressed(ServerPlayer p, int mode) {
        boolean crouch = p.isShiftKeyDown();
        BreachProtocol.LOGGER.info("Shapeshift used by {} (mode {}, {})", p.getGameProfile().getName(), mode, crouch ? "crouching" : "standing");
        // Mimic: become what you're looking at; crouch to come back. Shapeshift: the menu of your forms (with the way back in it); crouch to come back at once
        if (mode == 0) { if (crouch) revert(p); else copy(p); }
        else { if (crouch && shifted(p)) revert(p); else menu(p); }
    }

    private static void menu(ServerPlayer p) {
        p.getPersistentData().putLong("bp_shape_open", p.level().getGameTime());
        Career.sync(p);
    }

    private static boolean shifted(ServerPlayer p) { return p.getPersistentData().contains(FORM); }

    private static void revert(ServerPlayer p) {
        if (!shifted(p)) { say(p, "You're already yourself.", ChatFormatting.GRAY); return; }
        try { MimicFormHelper.clearSource(p, SOURCE); } catch (Throwable t) { BreachProtocol.LOGGER.debug("Shapeshift revert: {}", t.toString()); }
        p.getPersistentData().remove(FORM);
        giveBack(p);
        puff(p, false);
        Career.sync(p);
        say(p, "You're yourself again.", ChatFormatting.LIGHT_PURPLE);
    }

    /** Mimic: whatever you're looking at, creature first, then the block. */
    private static void copy(ServerPlayer p) {
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle(), end = eye.add(look.scale(48));
        HitResult block = p.pick(48, 0, false);
        double max = block.getType() == HitResult.Type.MISS ? 48 * 48 : block.getLocation().distanceToSqr(eye);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(p, eye, end, p.getBoundingBox().expandTowards(look.scale(48)).inflate(1), e -> e instanceof LivingEntity && e != p && e.isAlive(), max);
        if (hit != null && hit.getEntity() instanceof LivingEntity le) {
            List<CompoundTag> sk = com.breachprotocol.career.PowerKills.skillsOf(le);
            rememberSkills(p, BuiltInRegistries.ENTITY_TYPE.getKey(le.getType()).toString(), sk);
            becomeCreature(p, le, sk);
            return;
        }
        if (block instanceof BlockHitResult bh && block.getType() == HitResult.Type.BLOCK) {
            ServerLevel l = p.serverLevel();
            BlockState st = l.getBlockState(bh.getBlockPos());
            if (st.isAir() || st.getRenderShape() == RenderShape.INVISIBLE) { say(p, "There's nothing there to become.", ChatFormatting.GRAY); return; }
            try {
                DetectedForm f = MimicBlockHelper.detectParts(l, bh.getBlockPos(), st);
                List<MimicBlockPart> parts = f.parts();
                if (!MimicBlockHelper.canApplyBlock(p, MimicBlockHelper.getFormBlocksTall(parts))) { say(p, "Not here: there's no room to stand as that.", ChatFormatting.RED); return; }
                CompoundTag be = MimicBlockHelper.captureBlockEntityData(l, f.primaryPos());
                clear(p);
                if (MimicBlockHelper.applyBlock(p, parts, be == null ? new CompoundTag() : be, 1.0F, SOURCE, 100))
                    done(p, st.getBlock().getName().getString(), "blocks", BuiltInRegistries.BLOCK.getKey(st.getBlock()).toString());
            } catch (Throwable t) { BreachProtocol.LOGGER.warn("Shapeshift (block): {}", t.toString()); say(p, "It wouldn't take.", ChatFormatting.RED); }
            return;
        }
        say(p, "Look at a creature or a block, then use it.", ChatFormatting.GRAY);
    }

    /** Become it: its form, and (while you wear it) every Manas skill it has. Skills seen on a real one are remembered for the menu. */
    private static void becomeCreature(ServerPlayer p, LivingEntity e, List<CompoundTag> skills) {
        try {
            if (!MimicFormHelper.canApplyForm(p, e)) { say(p, "That form won't hold.", ChatFormatting.RED); return; }
            clear(p);
            if (!MimicFormHelper.applyForm(p, e, 1.0F, true, SOURCE, 100, true)) { say(p, "It wouldn't take.", ChatFormatting.RED); return; }
            String id = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString();
            List<String> lent = com.breachprotocol.career.PowerKills.lend(p, skills);
            net.minecraft.nbt.ListTag l = new net.minecraft.nbt.ListTag();
            for (String x : lent) l.add(net.minecraft.nbt.StringTag.valueOf(x));
            p.getPersistentData().put(LENT, l);
            done(p, e.getName().getString(), "mobs", id);
            if (!lent.isEmpty()) say(p, "You are " + e.getName().getString() + ", and everything it can do, you can: " + lent.size() + (lent.size() == 1 ? " skill." : " skills."), ChatFormatting.LIGHT_PURPLE);
        } catch (Throwable t) { BreachProtocol.LOGGER.warn("Shapeshift (creature): {}", t.toString()); say(p, "It wouldn't take.", ChatFormatting.RED); }
    }

    private static final String LENT = "bp_shape_lent", KNOWN_SKILLS = "bp_shape_skills";

    private static void giveBack(ServerPlayer p) {
        CompoundTag pd = p.getPersistentData();
        if (!pd.contains(LENT)) return;
        List<String> ids = new java.util.ArrayList<>();
        for (net.minecraft.nbt.Tag t : pd.getList(LENT, net.minecraft.nbt.Tag.TAG_STRING)) ids.add(t.getAsString());
        pd.remove(LENT);
        com.breachprotocol.career.PowerKills.reclaim(p, ids);
    }

    /** What a kind of creature could do, last time you copied a real one. */
    private static List<CompoundTag> knownSkills(ServerPlayer p, String id) {
        List<CompoundTag> out = new java.util.ArrayList<>();
        for (net.minecraft.nbt.Tag t : p.getPersistentData().getCompound(KNOWN_SKILLS).getList(id, net.minecraft.nbt.Tag.TAG_COMPOUND)) out.add((CompoundTag) t);
        return out;
    }

    private static void rememberSkills(ServerPlayer p, String id, List<CompoundTag> skills) {
        if (skills.isEmpty()) return;
        CompoundTag all = p.getPersistentData().getCompound(KNOWN_SKILLS);
        net.minecraft.nbt.ListTag l = new net.minecraft.nbt.ListTag();
        l.addAll(skills);
        all.put(id, l);
        p.getPersistentData().put(KNOWN_SKILLS, all);
    }

    /** From the menu: "mob:namespace:path" or "block:namespace:path". */
    public static void action(ServerPlayer p, String a) {
        if (!has(p)) return;
        if (a.equals("revert")) { revert(p); return; }
        int c = a.indexOf(':');
        if (c < 0) return;
        String kind = a.substring(0, c), id = a.substring(c + 1);
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) return;
        if (kind.equals("mob")) {
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(rl).orElse(null);
            if (type == null || type == EntityType.PLAYER) return;
            Entity e = type.create(p.serverLevel());
            if (!(e instanceof LivingEntity le)) { if (e != null) e.discard(); say(p, "That isn't something you can be.", ChatFormatting.RED); return; }
            le.moveTo(p.getX(), p.getY(), p.getZ(), p.getYRot(), 0);
            becomeCreature(p, le, knownSkills(p, rl.toString()));
            le.discard();
        } else if (kind.equals("block")) {
            Block b = BuiltInRegistries.BLOCK.getOptional(rl).orElse(null);
            if (b == null) return;
            BlockState st = b.defaultBlockState();
            try {
                if (!MimicBlockHelper.canApplyBlock(p, 1)) { say(p, "Not here: there's no room to stand as that.", ChatFormatting.RED); return; }
                clear(p);
                if (MimicBlockHelper.applyBlock(p, List.of(new MimicBlockPart(st, 0, 0, 0)), new CompoundTag(), 1.0F, SOURCE, 100)) done(p, b.getName().getString(), "blocks", rl.toString());
            } catch (Throwable t) { BreachProtocol.LOGGER.warn("Shapeshift (block): {}", t.toString()); say(p, "It wouldn't take.", ChatFormatting.RED); }
        }
    }

    private static void clear(ServerPlayer p) {
        if (!shifted(p)) return;
        try { MimicFormHelper.clearSource(p, SOURCE); } catch (Throwable ignored) {}
        giveBack(p);
    }

    /** In the form; and it goes at the top of your list of forms ("mobs" or "blocks") for the menu. */
    private static void done(ServerPlayer p, String what, String list, String id) {
        CompoundTag pd = p.getPersistentData();
        pd.putString(FORM, what);
        String key = "bp_shape_" + list;
        net.minecraft.nbt.ListTag old = pd.getList(key, net.minecraft.nbt.Tag.TAG_STRING), now = new net.minecraft.nbt.ListTag();
        now.add(net.minecraft.nbt.StringTag.valueOf(id));
        for (net.minecraft.nbt.Tag t : old) if (!t.getAsString().equals(id) && now.size() < 400) now.add(t);
        pd.put(key, now);
        puff(p, true);
        say(p, "You are " + what + ". Use Shapeshift again to come back.", ChatFormatting.LIGHT_PURPLE);
        Career.sync(p);
    }

    private static void puff(ServerPlayer p, boolean in) {
        ServerLevel l = p.serverLevel();
        l.sendParticles(in ? ParticleTypes.REVERSE_PORTAL : ParticleTypes.PORTAL, p.getX(), p.getY() + 1, p.getZ(), 40, 0.5, 0.8, 0.5, 0.1);
        l.sendParticles(ParticleTypes.LARGE_SMOKE, p.getX(), p.getY() + 0.5, p.getZ(), 12, 0.4, 0.4, 0.4, 0.02);
        l.playSound(null, BlockPos.containing(p.position()), in ? SoundEvents.ILLUSIONER_MIRROR_MOVE : SoundEvents.ILLUSIONER_PREPARE_MIRROR, SoundSource.PLAYERS, 0.8F, in ? 1.1F : 0.8F);
    }

    private static void say(ServerPlayer p, String s, ChatFormatting c) { p.displayClientMessage(Component.literal(s).withStyle(c), true); }
}
