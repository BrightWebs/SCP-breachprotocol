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

    private static boolean warned;

    /** Theirs if they're the Administrator (it's granted to every one of them): no asking Manas, whose storage differs between versions. */
    public static boolean has(ServerPlayer p) {
        return SKILL != null && (com.breachprotocol.career.Career.rank(p) == com.breachprotocol.career.Rank.ADMINISTRATOR || com.breachprotocol.career.Career.isAscended(p));
    }

    /** Whether Manas has it on record yet (only to decide whether to hand it over). */
    private static boolean learned(ServerPlayer p) {
        try { return SkillAPI.getSkillsFrom(p).getSkill(ID).isPresent(); }
        catch (Throwable t) {
            if (!warned) { warned = true; BreachProtocol.LOGGER.warn("Shapeshift: couldn't read Manas skill storage ({}); granting once per session instead", t.toString()); }
            return p.getPersistentData().getBoolean("bp_shape_granted");
        }
    }

    /** The Administrator has it; it's learned the first time they're seen without it. */
    public static void grant(ServerPlayer p) {
        if (SKILL == null || learned(p)) return;
        try { SkillHelper.learnSkill(p, SKILL); p.getPersistentData().putBoolean("bp_shape_granted", true); } catch (Throwable t) { BreachProtocol.LOGGER.warn("Couldn't grant Shapeshift: {}", t.toString()); }
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
        p.getPersistentData().remove(TRAITS);
        p.getPersistentData().remove(TYPE);
        undisguise(p);
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
            // what the body brings with it
            CompoundTag tr = new CompoundTag();
            tr.putBoolean("fire", e.fireImmune());
            tr.putBoolean("water", e.canBreatheUnderwater());
            double dmg = 0;
            @SuppressWarnings("unchecked") EntityType<? extends LivingEntity> lt = (EntityType<? extends LivingEntity>) e.getType();
            if (net.minecraft.world.entity.ai.attributes.DefaultAttributes.hasSupplier(lt)) {
                var sup = net.minecraft.world.entity.ai.attributes.DefaultAttributes.getSupplier(lt);
                if (sup.hasAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)) dmg = sup.getBaseValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
            }
            if (e.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) != null) dmg = Math.max(dmg, e.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE));
            tr.putDouble("dmg", dmg);
            p.getPersistentData().put(TRAITS, tr);
            p.getPersistentData().putString(TYPE, id);
            p.getPersistentData().putInt("bp_shape_move", 0);
            disguise(p, e);
            done(p, e.getName().getString(), "mobs", id);
            readout(p, e, lent, tr);
        } catch (Throwable t) { BreachProtocol.LOGGER.warn("Shapeshift (creature): {}", t.toString()); say(p, "It wouldn't take.", ChatFormatting.RED); }
    }

    private static final String LENT = "bp_shape_lent", KNOWN_SKILLS = "bp_shape_skills", TRAITS = "bp_shape_traits", TYPE = "bp_shape_type";

    /**
     * Appraisal sees the creature: Manas Reborn reads its "fake" health, spiritual health, armor, aura and
     * magicules before the real ones, so they're set to the creature's while you wear it (and cleared after).
     */
    private static void disguise(ServerPlayer p, LivingEntity e) {
        Object mine = existence(p);
        if (mine == null) return;
        Object theirs = existence(e);
        double shp = num(theirs, "getSpiritualHealth"), aura = num(theirs, "getAura"), mana = num(theirs, "getMana");
        call(mine, "setFakeHealth", float.class, Math.max(1F, e.getHealth()));
        call(mine, "setFakeSpiritualHealth", double.class, Math.max(1, shp));
        call(mine, "setFakeArmor", double.class, Math.max(0.001, e.getArmorValue()));
        call(mine, "setFakeAura", double.class, Math.max(1, aura));
        call(mine, "setFakeMana", double.class, Math.max(1, mana));
        call(mine, "markEnergyDirty", null, null);
        call(mine, "markDirty", null, null);
    }

    private static void undisguise(ServerPlayer p) {
        Object mine = existence(p);
        if (mine == null) return;
        call(mine, "setFakeHealth", float.class, 0F);
        call(mine, "setFakeSpiritualHealth", double.class, 0D);
        call(mine, "setFakeArmor", double.class, 0D);
        call(mine, "setFakeAura", double.class, 0D);
        call(mine, "setFakeMana", double.class, 0D);
        call(mine, "markEnergyDirty", null, null);
        call(mine, "markDirty", null, null);
    }

    private static Object existence(LivingEntity e) {
        try { return Class.forName("io.github.manasmods.manas_reborn.storage.ManasStorages").getMethod("getExistenceFrom", LivingEntity.class).invoke(null, e); }
        catch (Throwable t) { return null; }
    }

    private static double num(Object o, String m) {
        if (o == null) return 0;
        try { return ((Number) o.getClass().getMethod(m).invoke(o)).doubleValue(); } catch (Throwable t) { return 0; }
    }

    private static void call(Object o, String m, Class<?> arg, Object v) {
        try {
            if (arg == null) o.getClass().getMethod(m).invoke(o);
            else o.getClass().getMethod(m, arg).invoke(o, v);
        } catch (Throwable t) { BreachProtocol.LOGGER.debug("Shapeshift disguise {}: {}", m, t.toString()); }
    }

    /** The race key in a creature's form: its own move (crouch: the next one). */
    private static void move(ServerPlayer p, boolean next) {
        CompoundTag pd = p.getPersistentData();
        if (!pd.contains(FORM) || !pd.contains(TYPE)) return;
        ResourceLocation rl = ResourceLocation.tryParse(pd.getString(TYPE));
        EntityType<?> t = rl == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(rl).orElse(null);
        if (t != null) com.breachprotocol.career.CreatureMoves.use(p, t, next);
    }

    /** Everything the form gave you, said once, in chat. */
    private static void readout(ServerPlayer p, LivingEntity e, List<String> lent, CompoundTag tr) {
        List<String> parts = new java.util.ArrayList<>();
        double dmg = Math.max(tr.getDouble("dmg"), p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE));
        parts.add("hits for " + String.format(java.util.Locale.ROOT, "%.0f", dmg));
        if (tr.getBoolean("fire")) parts.add("fireproof");
        if (tr.getBoolean("water")) parts.add("breathes water");
        try { if (MimicFormHelper.isFlyingForm(p)) parts.add("flies"); } catch (Throwable ignored) {}
        p.sendSystemMessage(Component.literal("◆ " + e.getName().getString().toUpperCase(java.util.Locale.ROOT)).withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD)
                .append(Component.literal("  " + String.join(" · ", parts)).withStyle(ChatFormatting.GRAY)));
        if (lent.isEmpty()) p.sendSystemMessage(Component.literal("   no skills of its own").withStyle(ChatFormatting.DARK_GRAY));
        else {
            List<String> names = new java.util.ArrayList<>();
            for (String id : lent) { String path = id.substring(id.indexOf(':') + 1).replace('_', ' '); names.add(Character.toUpperCase(path.charAt(0)) + path.substring(1)); }
            p.sendSystemMessage(Component.literal("   its skills, yours while you wear it: ").withStyle(ChatFormatting.GRAY).append(Component.literal(String.join(", ", names)).withStyle(ChatFormatting.LIGHT_PURPLE)));
        }
    }

    /** While in a creature's form: its fireproof hide and water-breathing, kept up quietly. */
    public static void traits(ServerPlayer p) {
        CompoundTag pd = p.getPersistentData();
        if (!pd.contains(FORM) || !pd.contains(TRAITS)) return;
        CompoundTag tr = pd.getCompound(TRAITS);
        if (tr.getBoolean("fire")) p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE, 80, 0, true, false, true));
        if (tr.getBoolean("water")) p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.WATER_BREATHING, 80, 0, true, false, true));
    }

    /** A blow from the form (sent by the client when you attack): as hard as the creature hits, or you do, whichever is more. */
    private static void hit(ServerPlayer p, int id) {
        CompoundTag pd = p.getPersistentData();
        if (!pd.contains(FORM)) return;
        long now = p.level().getGameTime();
        if (now - pd.getLong("bp_shape_hit") < 8) return;
        Entity t = p.serverLevel().getEntity(id);
        if (!(t instanceof LivingEntity le) || !le.isAlive() || t == p) return;
        double reach = 4.5 + le.getBbWidth() / 2 + p.getBbWidth() / 2;
        if (p.distanceToSqr(le) > reach * reach * 1.4) return;
        pd.putLong("bp_shape_hit", now);
        float dmg = (float) Math.max(pd.getCompound(TRAITS).getDouble("dmg"), p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE));
        if (le.hurt(p.damageSources().playerAttack(p), dmg)) {
            Vec3 away = le.position().subtract(p.position()).multiply(1, 0, 1).normalize().scale(0.45);
            le.push(away.x, 0.15, away.z);
            le.hurtMarked = true;
            p.serverLevel().playSound(null, le.blockPosition(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.8F, 1.0F);
        }
    }

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
        if (a.startsWith("move:")) { move(p, a.endsWith("1")); return; }
        if (a.startsWith("hit:")) { try { hit(p, Integer.parseInt(a.substring(4))); } catch (NumberFormatException ignored) {} return; }
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
        p.getPersistentData().remove(TRAITS);
        p.getPersistentData().remove(TYPE);
        undisguise(p);
        giveBack(p);
    }

    /** In the form; and it goes at the top of your list of forms ("mobs" or "blocks") for the menu. */
    private static void done(ServerPlayer p, String what, String list, String id) {
        CompoundTag pd = p.getPersistentData();
        pd.putString(FORM, what);
        String key = "bp_shape_" + list;
        net.minecraft.nbt.ListTag old = pd.getList(key, net.minecraft.nbt.Tag.TAG_STRING), now = new net.minecraft.nbt.ListTag();
        now.add(net.minecraft.nbt.StringTag.valueOf(id));
        for (net.minecraft.nbt.Tag t : old) if (!t.getAsString().equals(id) && now.size() < 6000) now.add(t);
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
