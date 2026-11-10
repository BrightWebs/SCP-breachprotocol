package com.breachprotocol.career;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.entity.projectile.windcharge.WindCharge;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A creature's own moves, for whoever wears its form (Shapeshift): the race key uses the one
 * selected, crouch + the race key moves on to the next. Each has its own cooldown.
 */
public final class CreatureMoves {
    private CreatureMoves() {}

    public record Move(String name, int cooldown, Consumer<ServerPlayer> use) {}

    /** The moves this kind of creature has (a fallback for any creature: a pounce). */
    public static List<Move> of(ServerLevel l, EntityType<?> type) {
        List<Move> m = new ArrayList<>();
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
        switch (id) {
            case "minecraft:ghast" -> m.add(new Move("Fireball", 30, p -> fire(p, new LargeFireball(p.level(), p, look(p), 1))));
            case "minecraft:blaze" -> m.add(new Move("Fire volley", 25, CreatureMoves::volley));
            case "minecraft:wither" -> { m.add(new Move("Wither skull", 12, p -> fire(p, new WitherSkull(p.level(), p, look(p))))); m.add(new Move("Blue skull", 60, p -> { WitherSkull s = new WitherSkull(p.level(), p, look(p)); s.setDangerous(true); fire(p, s); })); }
            case "minecraft:ender_dragon" -> { m.add(new Move("Dragon's breath", 40, p -> fire(p, new DragonFireball(p.level(), p, look(p))))); m.add(new Move("Wing buffet", 40, p -> shock(p, 7, 8F, 2.2))); }
            case "minecraft:creeper" -> m.add(new Move("Detonate", 80, p -> p.level().explode(p, p.getX(), p.getY(), p.getZ(), 3F, Level.ExplosionInteraction.MOB)));
            case "minecraft:enderman" -> m.add(new Move("Teleport", 10, CreatureMoves::blink));
            case "minecraft:skeleton", "minecraft:pillager", "minecraft:piglin" -> m.add(new Move("Arrow", 12, p -> arrow(p, null)));
            case "minecraft:stray" -> m.add(new Move("Frost arrow", 12, p -> arrow(p, new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 1))));
            case "minecraft:bogged" -> m.add(new Move("Poison arrow", 12, p -> arrow(p, new MobEffectInstance(MobEffects.POISON, 100, 0))));
            case "minecraft:shulker" -> m.add(new Move("Homing bullet", 25, CreatureMoves::bullet));
            case "minecraft:evoker" -> { m.add(new Move("Fangs", 30, CreatureMoves::fangLine)); m.add(new Move("Fang ring", 50, CreatureMoves::fangRing)); }
            case "minecraft:warden" -> { m.add(new Move("Sonic boom", 60, CreatureMoves::sonic)); m.add(new Move("Darkness", 100, p -> around(p, 12, e -> e.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 200, 0))))); }
            case "minecraft:snow_golem" -> m.add(new Move("Snowball", 4, p -> fire(p, new Snowball(p.level(), p), 1.6F)));
            case "minecraft:witch" -> { m.add(new Move("Harming potion", 30, p -> potion(p, Potions.HARMING))); m.add(new Move("Poison potion", 30, p -> potion(p, Potions.POISON))); m.add(new Move("Slowness potion", 30, p -> potion(p, Potions.SLOWNESS))); }
            case "minecraft:guardian", "minecraft:elder_guardian" -> { m.add(new Move("Laser", 40, p -> beam(p, id.contains("elder") ? 9F : 6F))); if (id.contains("elder")) m.add(new Move("Curse", 200, p -> around(p, 30, e -> e.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 3000, 2))))); }
            case "minecraft:iron_golem" -> m.add(new Move("Fling", 25, p -> { LivingEntity t = target(p, 4); if (t != null) { PowerKills.hurt(p, t, p.damageSources().playerAttack(p), 12F); t.push(0, 1.1, 0); t.hurtMarked = true; p.level().playSound(null, t.blockPosition(), SoundEvents.IRON_GOLEM_ATTACK, SoundSource.PLAYERS, 1F, 1F); } }));
            case "minecraft:breeze" -> m.add(new Move("Wind charge", 15, p -> { WindCharge w = new WindCharge(p, p.level(), p.getX(), p.getEyeY(), p.getZ()); fire(p, w, 1.5F); }));
            case "minecraft:ravager" -> m.add(new Move("Roar", 60, p -> shock(p, 5, 6F, 1.6)));
            case "minecraft:bee" -> m.add(new Move("Sting", 20, p -> { LivingEntity t = target(p, 4); if (t != null) { PowerKills.hurt(p, t, p.damageSources().playerAttack(p), 2F); t.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 0)); } }));
            case "minecraft:chicken" -> m.add(new Move("Egg", 6, p -> fire(p, new ThrownEgg(p.level(), p), 1.5F)));
            case "minecraft:wolf" -> m.add(new Move("Howl", 200, p -> { p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 400, 1)); p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 400, 0)); p.level().playSound(null, p.blockPosition(), SoundEvents.WOLF_HOWL, SoundSource.PLAYERS, 1.5F, 1F); }));
            case "minecraft:dolphin", "minecraft:squid", "minecraft:glow_squid" -> m.add(new Move("Dash", 20, p -> dash(p, 2.4)));
            case "minecraft:phantom" -> m.add(new Move("Dive", 30, p -> dash(p, 3.0)));
            default -> {}
        }
        if (m.isEmpty()) scp(id, m);
        if (m.isEmpty()) family(id, m);
        if (m.isEmpty()) {
            Entity sample = null;
            try { sample = type.create(l); } catch (Throwable ignored) {}
            if (sample instanceof RangedAttackMob) m.add(new Move("Shot", 15, p -> arrow(p, null)));
            if (sample != null) sample.discard();
        }
        m.add(new Move("Pounce", 30, p -> { dash(p, 1.6); LivingEntity t = target(p, 5); if (t != null) PowerKills.hurt(p, t, p.damageSources().playerAttack(p), (float) Math.max(4, p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE))); }));
        return m;
    }

    /** Not creatures, though they're entities: skulls, eggs, seats, statues... */
    public static boolean creature(String path) {
        for (String k : new String[]{"skull", "egg", "seat", "statue", "part", "projectile", "image", "trophy", "corpse", "head"}) if (path.contains(k)) return false;
        return true;
    }

    private static Move power(String name, String id) { return new Move(name, 20, p -> Powers.castAs(p, id)); }

    /** SCP Unleashed's anomalies: what each one does, by its file. */
    private static void scp(String id, List<Move> m) {
        if (!id.startsWith("scp_unleashed:")) return;
        String k = id.substring(14);
        switch (k) {
            case "scp173" -> { m.add(power("Blink", "blink")); m.add(new Move("Snap", 20, p -> { LivingEntity t = target(p, 6); if (t != null) { PowerKills.hurt(p, t, p.damageSources().playerAttack(p), 40F); p.level().playSound(null, t.blockPosition(), SoundEvents.SKELETON_HURT, SoundSource.PLAYERS, 1.4F, 0.5F); } })); }
            case "scp096_shy_guy" -> { m.add(power("Rage", "rage")); m.add(power("Scream", "scream")); }
            case "scp106_old_man" -> { m.add(power("The Pocket", "pocket")); m.add(power("Corrosion", "corrode")); m.add(new Move("Sink through walls", 30, CreatureMoves::blink)); }
            case "scp682" -> { m.add(power("Roar", "roar")); m.add(new Move("Maul", 25, p -> shock(p, 4, 18F, 2.0))); }
            case "scp457" -> { m.add(power("Immolate", "immolate")); m.add(new Move("Fire breath", 30, p -> breath(p, "fire"))); }
            case "scp999_blob" -> m.add(power("Embrace", "embrace"));
            case "scp001_gate_guardian" -> { m.add(power("Smite", "smite")); m.add(power("Repel", "repel")); }
            case "scp049" -> { m.add(new Move("The Cure", 40, p -> { LivingEntity t = target(p, 4); if (t != null) { t.addEffect(new MobEffectInstance(MobEffects.WITHER, 200, 3)); PowerKills.hurt(p, t, p.damageSources().indirectMagic(p, p), 20F); } })); m.add(new Move("Diagnose", 60, p -> around(p, 16, e -> e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 300, 0))))); }
            case "scp049_2", "scp008_zombie", "scp035_host" -> m.add(infect());
            case "scp939" -> { m.add(new Move("Lure", 60, p -> pull(p, 16))); m.add(new Move("Bite", 15, p -> bite(p, 14F, null))); }
            case "scp035_mask" -> { m.add(new Move("Decay", 40, p -> around(p, 6, e -> e.addEffect(new MobEffectInstance(MobEffects.WITHER, 200, 1))))); m.add(power("Corrosion", "corrode")); }
            case "scp076" -> { m.add(new Move("Thrown blade", 10, p -> arrow(p, null))); m.add(new Move("Ground blades", 40, CreatureMoves::fangLine)); }
            case "scp343" -> { m.add(power("Judgement", "smite")); m.add(power("Grace", "embrace")); }
            case "scp066" -> m.add(new Move("Beethoven", 60, p -> { around(p, 10, e -> { e.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 200, 0)); PowerKills.hurt(p, e, p.damageSources().indirectMagic(p, p), 6F); }); p.level().playSound(null, p.blockPosition(), SoundEvents.RAID_HORN.value(), SoundSource.PLAYERS, 3F, 0.5F); }));
            case "scp111_snail", "scp1762_dragon", "scp1762_drake", "scp1762_wyrm", "scp1762_wyvern" -> { m.add(new Move("Fire breath", 30, p -> breath(p, "fire"))); m.add(new Move("Dive", 30, p -> dash(p, 2.4))); }
            case "scp548_ice_spider" -> { m.add(new Move("Frost breath", 30, p -> breath(p, "ice"))); m.add(new Move("Web", 30, CreatureMoves::web)); }
            case "scp538_shadow_spider" -> { m.add(new Move("Shadow bite", 15, p -> bite(p, 8F, new MobEffectInstance(MobEffects.BLINDNESS, 100, 0)))); m.add(new Move("Fade", 60, CreatureMoves::phase)); }
            case "scp017_shadow", "scp280_shade", "scp205_devil", "scp215_phantom" -> { m.add(new Move("Engulf", 20, p -> bite(p, 12F, new MobEffectInstance(MobEffects.DARKNESS, 100, 0)))); m.add(new Move("Fade", 60, CreatureMoves::phase)); }
            case "scp087_face", "scp087_hunter", "scp513_stalker", "scp372" -> { m.add(new Move("Fade", 40, CreatureMoves::phase)); m.add(new Move("Dread", 60, p -> around(p, 12, e -> { e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 0)); e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1)); }))); }
            case "scp098_crab" -> m.add(new Move("Pincer", 15, p -> bite(p, 10F, new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2))));
            case "scp149_fly", "scp553_butterfly", "scp3199" -> { m.add(new Move("Sting", 15, p -> bite(p, 4F, new MobEffectInstance(MobEffects.POISON, 120, 1)))); m.add(new Move("Dart", 20, p -> dash(p, 2.0))); }
            case "scp075_snail" -> m.add(new Move("Corrosive glob", 30, p -> potion(p, Potions.HARMING)));
            case "scp018_ball" -> m.add(new Move("Ricochet", 15, p -> dash(p, 3.2)));
            case "scp323" -> { m.add(new Move("Hunger", 15, p -> bite(p, 12F, new MobEffectInstance(MobEffects.HUNGER, 200, 2)))); m.add(new Move("Howl", 200, p -> { p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 400, 1)); p.level().playSound(null, p.blockPosition(), SoundEvents.WOLF_HOWL, SoundSource.PLAYERS, 1.5F, 0.6F); })); }
            case "scp327_siren" -> m.add(new Move("Song", 60, p -> pull(p, 20)));
            case "scp469_angel" -> { m.add(new Move("Wing blades", 15, p -> volleyArrows(p, 5))); m.add(new Move("Dive", 30, p -> dash(p, 2.6))); }
            case "scp407_titan", "scp610_colossus", "scp610_siege_brute", "scp354_brute", "scp016_brute", "scp019_brute", "scp439_brute", "theodore_amalgamation" -> { m.add(new Move("Slam", 30, p -> shock(p, 5, 12F, 1.8))); m.add(new Move("Roar", 60, p -> shock(p, 8, 4F, 1.0))); }
            case "scp610_spitter", "scp610_spewer", "scp016_slinger", "scp610_bile" -> m.add(new Move("Acid spit", 20, p -> potion(p, Potions.POISON)));
            case "scarlet_king", "daevite_god", "yaldabaoth", "mekhane" -> { m.add(power("Smite", "smite")); m.add(power("Judgement", "judgement")); m.add(power("Roar", "roar")); }
            case "daevite_overseer", "scarlet_cultist", "scarlet_daughter_eyes", "scarlet_daughter_moon", "scarlet_daughter_thorns", "scarlet_daughter_bride", "scp1730_2", "scp1730_2_contained" -> { m.add(new Move("Crimson bolt", 20, p -> spell(p, 10F))); m.add(new Move("Fangs", 40, CreatureMoves::fangLine)); }
            case "mtf_operative", "chaos_insurgent", "serpents_hand_agent", "z0004_crew", "z0004_captain" -> m.add(new Move("Fire", 8, p -> arrow(p, null)));
            default -> {
                if (k.startsWith("scp610_") || k.startsWith("scp003_") || k.startsWith("scp016_")) m.add(infect());
                else if (k.startsWith("scp354_") || k.startsWith("scarlet_")) { m.add(new Move("Maul", 20, p -> bite(p, 10F, null))); m.add(new Move("Roar", 60, p -> shock(p, 6, 4F, 1.2))); }
            }
        }
    }

    /** Everyone else (Ice and Fire, Manas Reborn, any other mod's creatures): by what they are. */
    private static void family(String id, List<Move> m) {
        String k = id.substring(id.indexOf(':') + 1);
        boolean dragon = k.contains("dragon") || k.contains("wyvern") || k.contains("drake") || k.contains("wyrm");
        if (dragon) {
            String kind = k.contains("ice") ? "ice" : k.contains("lightning") ? "lightning" : "fire";
            m.add(new Move(kind.equals("ice") ? "Frost breath" : kind.equals("lightning") ? "Lightning breath" : "Fire breath", 30, p -> breath(p, kind)));
            m.add(new Move("Wing buffet", 40, p -> shock(p, 7, 8F, 2.2)));
            m.add(new Move("Roar", 80, p -> shock(p, 10, 2F, 1.0)));
            return;
        }
        if (k.contains("hydra")) { m.add(new Move("Poison breath", 25, p -> breath(p, "poison"))); m.add(new Move("Bite", 15, p -> bite(p, 10F, null))); return; }
        if (k.contains("sea_serpent") || k.contains("serpent")) { m.add(new Move("Water breath", 30, p -> breath(p, "water"))); m.add(new Move("Bite", 15, p -> bite(p, 12F, null))); return; }
        if (k.contains("cockatrice") || k.contains("gorgon") || k.contains("basilisk") || k.contains("medusa")) { m.add(new Move("Petrifying gaze", 60, CreatureMoves::petrify)); m.add(new Move("Peck", 15, p -> bite(p, 6F, null))); return; }
        if (k.contains("siren")) { m.add(new Move("Song", 60, p -> pull(p, 20))); return; }
        if (k.contains("lich") || k.contains("necro") || k.contains("wizard") || k.contains("mage") || k.contains("sorcer") || k.contains("shaman") || k.contains("cultist") || k.contains("illusion")) { m.add(new Move("Spell bolt", 20, p -> spell(p, 8F))); m.add(new Move("Fangs", 40, CreatureMoves::fangLine)); return; }
        if (k.contains("troll") || k.contains("cyclops") || k.contains("giant") || k.contains("golem") || k.contains("ogre") || k.contains("titan") || k.contains("colossus") || k.contains("hulk") || k.contains("brute")) { m.add(new Move("Slam", 30, p -> shock(p, 5, 12F, 1.8))); m.add(new Move("Fling", 25, p -> { LivingEntity t = target(p, 5); if (t != null) { PowerKills.hurt(p, t, p.damageSources().playerAttack(p), 10F); t.push(0, 1.1, 0); t.hurtMarked = true; } })); return; }
        if (k.contains("myrmex") || k.contains("scorpion") || k.contains("wasp") || k.contains("hornet") || k.contains("sting") || k.contains("ant")) { m.add(new Move("Sting", 15, p -> bite(p, 6F, new MobEffectInstance(MobEffects.POISON, 160, 1)))); return; }
        if (k.contains("spider") || k.contains("arachn")) { m.add(new Move("Web", 30, CreatureMoves::web)); m.add(new Move("Venom bite", 15, p -> bite(p, 6F, new MobEffectInstance(MobEffects.POISON, 120, 0)))); return; }
        if (k.contains("ghost") || k.contains("wraith") || k.contains("spirit") || k.contains("shade") || k.contains("spectre") || k.contains("specter") || k.contains("phantom") || k.contains("shadow")) { m.add(new Move("Phase", 40, CreatureMoves::phase)); m.add(new Move("Fright", 60, p -> around(p, 10, e -> e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 80, 0))))); return; }
        if (k.contains("pixie") || k.contains("fairy") || k.contains("sprite")) { m.add(new Move("Lift", 30, p -> { LivingEntity t = target(p, 16); if (t != null) t.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 60, 1)); })); m.add(new Move("Flit", 20, CreatureMoves::blink)); return; }
        if (k.contains("stymphalian") || k.contains("bird") || k.contains("harpy") || k.contains("griffin") || k.contains("gryph") || k.contains("eagle") || k.contains("hawk")) { m.add(new Move("Feather volley", 25, p -> volleyArrows(p, 5))); m.add(new Move("Dive", 30, p -> dash(p, 2.6))); return; }
        if (k.contains("death_worm") || k.contains("worm") || k.contains("burrow")) { m.add(new Move("Quake", 40, p -> shock(p, 6, 8F, 0.6))); m.add(new Move("Lunge", 20, p -> { dash(p, 2.0); LivingEntity t = target(p, 5); if (t != null) PowerKills.hurt(p, t, p.damageSources().playerAttack(p), 10F); })); return; }
        if (k.contains("archer") || k.contains("skeleton") || k.contains("ranger") || k.contains("hunter") || k.contains("marksman") || k.contains("slinger") || k.contains("gunner") || k.contains("soldier")) { m.add(new Move("Shot", 10, p -> arrow(p, null))); return; }
        if (k.contains("slime") || k.contains("blob") || k.contains("ooze") || k.contains("glob") || k.contains("spitter") || k.contains("bile")) { m.add(new Move("Acid spit", 20, p -> potion(p, Potions.POISON))); return; }
        if (k.contains("zombie") || k.contains("ghoul") || k.contains("thrall") || k.contains("husk") || k.contains("shambler") || k.contains("undead") || k.contains("dread")) { m.add(infect()); m.add(new Move("Roar", 80, p -> shock(p, 6, 2F, 0.8))); return; }
        if (k.contains("wolf") || k.contains("hound") || k.contains("lion") || k.contains("tiger") || k.contains("bear") || k.contains("boar") || k.contains("beast") || k.contains("dire")) { m.add(new Move("Maul", 15, p -> bite(p, 10F, null))); m.add(new Move("Howl", 200, p -> { p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 400, 1)); p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 400, 0)); p.level().playSound(null, p.blockPosition(), SoundEvents.WOLF_HOWL, SoundSource.PLAYERS, 1.5F, 0.8F); })); return; }
        if (k.contains("hippocampus") || k.contains("fish") || k.contains("shark") || k.contains("eel") || k.contains("whale") || k.contains("megalodon")) { m.add(new Move("Dash", 20, p -> dash(p, 2.4))); m.add(new Move("Bite", 15, p -> bite(p, 10F, null))); }
    }

    // ---------------------------------------------------------------- using them

    /** The race key, in a creature's form. */
    public static void use(ServerPlayer p, EntityType<?> type, boolean next) {
        CompoundTag pd = p.getPersistentData();
        List<Move> moves = of(p.serverLevel(), type);
        int i = Math.floorMod(pd.getInt("bp_shape_move"), moves.size());
        if (next) {
            i = (i + 1) % moves.size();
            pd.putInt("bp_shape_move", i);
            p.displayClientMessage(Component.literal("◆ " + moves.get(i).name() + "  (" + (i + 1) + "/" + moves.size() + ")").withStyle(ChatFormatting.LIGHT_PURPLE), true);
            p.playNotifySound(SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.4F, 1.6F);
            return;
        }
        Move m = moves.get(i);
        long now = p.level().getGameTime(), ready = pd.getCompound("bp_shape_cd").getLong(m.name());
        if (now < ready) { p.displayClientMessage(Component.literal(m.name() + " · " + String.format(java.util.Locale.ROOT, "%.1f", (ready - now) / 20.0) + "s").withStyle(ChatFormatting.GRAY), true); return; }
        CompoundTag cd = pd.getCompound("bp_shape_cd");
        cd.putLong(m.name(), now + m.cooldown());
        pd.put("bp_shape_cd", cd);
        try { m.use().accept(p); } catch (Throwable t) { com.breachprotocol.BreachProtocol.LOGGER.debug("Creature move {}: {}", m.name(), t.toString()); }
        if (moves.size() > 1) p.displayClientMessage(Component.literal("◆ " + m.name() + "  ·  crouch + race key: next").withStyle(ChatFormatting.LIGHT_PURPLE), true);
    }

    // ---------------------------------------------------------------- the moves themselves

    private static Vec3 look(ServerPlayer p) { return p.getLookAngle(); }

    private static void fire(ServerPlayer p, Projectile pr) { fire(p, pr, 1.4F); }

    private static void fire(ServerPlayer p, Projectile pr, float speed) {
        Vec3 d = look(p);
        pr.setOwner(p);
        pr.setPos(p.getX() + d.x * 1.2, p.getEyeY() - 0.1 + d.y * 1.2, p.getZ() + d.z * 1.2);
        if (pr instanceof AbstractHurtingProjectile h) h.setDeltaMovement(d.scale(speed));
        else pr.shoot(d.x, d.y, d.z, speed, 0.5F);
        p.level().addFreshEntity(pr);
        p.level().playSound(null, p.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.8F, 1.1F);
    }

    private static void volley(ServerPlayer p) {
        for (int i = 0; i < 3; i++) {
            Vec3 d = look(p).add((p.getRandom().nextDouble() - 0.5) * 0.12, (p.getRandom().nextDouble() - 0.5) * 0.12, (p.getRandom().nextDouble() - 0.5) * 0.12);
            SmallFireball f = new SmallFireball(p.level(), p, d.normalize());
            f.setPos(p.getX() + d.x, p.getEyeY() - 0.1, p.getZ() + d.z);
            f.setDeltaMovement(d.normalize().scale(1.3));
            p.level().addFreshEntity(f);
        }
        p.level().playSound(null, p.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1F, 1F);
    }

    private static void arrow(ServerPlayer p, MobEffectInstance tip) {
        ItemStack ammo = new ItemStack(tip == null ? Items.ARROW : Items.TIPPED_ARROW);
        Arrow a = new Arrow(p.level(), p, ammo, null);
        if (tip != null) a.addEffect(tip);
        a.pickup = AbstractArrow.Pickup.DISALLOWED;
        a.shootFromRotation(p, p.getXRot(), p.getYRot(), 0, 3.0F, 0.5F);
        p.level().addFreshEntity(a);
        p.level().playSound(null, p.blockPosition(), SoundEvents.SKELETON_SHOOT, SoundSource.PLAYERS, 1F, 1F);
    }

    private static void potion(ServerPlayer p, net.minecraft.core.Holder<net.minecraft.world.item.alchemy.Potion> what) {
        ThrownPotion t = new ThrownPotion(p.level(), p);
        t.setItem(PotionContents.createItemStack(Items.SPLASH_POTION, what));
        t.shootFromRotation(p, p.getXRot(), p.getYRot(), -10, 0.8F, 0.5F);
        p.level().addFreshEntity(t);
        p.level().playSound(null, p.blockPosition(), SoundEvents.WITCH_THROW, SoundSource.PLAYERS, 1F, 1F);
    }

    private static LivingEntity target(ServerPlayer p, double range) {
        Vec3 eye = p.getEyePosition(), d = look(p), end = eye.add(d.scale(range));
        HitResult block = p.pick(range, 0, false);
        double max = block.getType() == HitResult.Type.MISS ? range * range : block.getLocation().distanceToSqr(eye);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(p, eye, end, p.getBoundingBox().expandTowards(d.scale(range)).inflate(1), e -> e instanceof LivingEntity && e != p && e.isAlive(), max);
        return hit != null && hit.getEntity() instanceof LivingEntity le ? le : null;
    }

    private static void bullet(ServerPlayer p) {
        LivingEntity t = target(p, 32);
        if (t == null) { p.displayClientMessage(Component.literal("Look at something to send it after.").withStyle(ChatFormatting.GRAY), true); return; }
        ShulkerBullet b = new ShulkerBullet(p.level(), p, t, net.minecraft.core.Direction.Axis.Y);
        b.setPos(p.getX(), p.getEyeY(), p.getZ());
        p.level().addFreshEntity(b);
        p.level().playSound(null, p.blockPosition(), SoundEvents.SHULKER_SHOOT, SoundSource.PLAYERS, 1F, 1F);
    }

    private static void blink(ServerPlayer p) {
        HitResult h = p.pick(48, 0, false);
        Vec3 to = h.getType() == HitResult.Type.BLOCK ? h.getLocation().subtract(look(p).scale(0.6)) : p.getEyePosition().add(look(p).scale(24));
        ServerLevel l = p.serverLevel();
        l.sendParticles(ParticleTypes.PORTAL, p.getX(), p.getY() + 1, p.getZ(), 40, 0.4, 0.8, 0.4, 0.4);
        p.teleportTo(to.x, to.y, to.z);
        p.fallDistance = 0;
        l.playSound(null, BlockPos.containing(to), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1F, 1F);
        l.sendParticles(ParticleTypes.PORTAL, to.x, to.y + 1, to.z, 40, 0.4, 0.8, 0.4, 0.4);
    }

    private static void fangLine(ServerPlayer p) {
        Vec3 d = look(p).multiply(1, 0, 1).normalize();
        for (int i = 1; i <= 16; i++) {
            Vec3 at = p.position().add(d.scale(i * 1.25));
            p.level().addFreshEntity(new EvokerFangs(p.level(), at.x, p.getY(), at.z, p.getYRot() * net.minecraft.util.Mth.DEG_TO_RAD, i, p));
        }
        p.level().playSound(null, p.blockPosition(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 1F, 1F);
    }

    private static void fangRing(ServerPlayer p) {
        for (int ring = 0; ring < 2; ring++) for (int i = 0; i < (ring == 0 ? 6 : 10); i++) {
            double a = i * Math.PI * 2 / (ring == 0 ? 6 : 10), r = ring == 0 ? 1.6 : 3.0;
            p.level().addFreshEntity(new EvokerFangs(p.level(), p.getX() + Math.cos(a) * r, p.getY(), p.getZ() + Math.sin(a) * r, (float) a, ring * 4, p));
        }
        p.level().playSound(null, p.blockPosition(), SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.PLAYERS, 1F, 1F);
    }

    private static void sonic(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        Vec3 eye = p.getEyePosition(), d = look(p);
        for (int i = 1; i <= 20; i++) { Vec3 q = eye.add(d.scale(i)); l.sendParticles(ParticleTypes.SONIC_BOOM, q.x, q.y, q.z, 1, 0, 0, 0, 0); }
        for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new AABB(eye, eye.add(d.scale(20))).inflate(1.2), e -> e != p && e.isAlive())) {
            Vec3 rel = e.position().add(0, e.getBbHeight() / 2, 0).subtract(eye);
            double along = rel.dot(d);
            if (along < 0 || rel.subtract(d.scale(along)).length() > 1.5 + e.getBbWidth() / 2) continue;
            PowerKills.hurt(p, e, l.damageSources().sonicBoom(p), 10F);
            e.push(d.x * 2.5, 0.5, d.z * 2.5);
            e.hurtMarked = true;
        }
        l.playSound(null, p.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 2F, 1F);
    }

    private static void beam(ServerPlayer p, float dmg) {
        LivingEntity t = target(p, 16);
        if (t == null) { p.displayClientMessage(Component.literal("Look at something to burn.").withStyle(ChatFormatting.GRAY), true); return; }
        ServerLevel l = p.serverLevel();
        Vec3 a = p.getEyePosition(), b = t.position().add(0, t.getBbHeight() / 2, 0);
        for (int i = 0; i <= 20; i++) { Vec3 q = a.lerp(b, i / 20.0); l.sendParticles(ParticleTypes.BUBBLE, q.x, q.y, q.z, 1, 0, 0, 0, 0); }
        PowerKills.hurt(p, t, l.damageSources().indirectMagic(p, p), dmg);
        l.playSound(null, p.blockPosition(), SoundEvents.GUARDIAN_ATTACK, SoundSource.PLAYERS, 1F, 1F);
    }

    private static void shock(ServerPlayer p, double r, float dmg, double push) {
        ServerLevel l = p.serverLevel();
        around(p, r, e -> {
            PowerKills.hurt(p, e, p.damageSources().playerAttack(p), dmg);
            Vec3 away = e.position().subtract(p.position()).multiply(1, 0, 1).normalize().scale(push);
            e.push(away.x, 0.5, away.z);
            e.hurtMarked = true;
        });
        l.sendParticles(ParticleTypes.EXPLOSION, p.getX(), p.getY() + 1, p.getZ(), 6, r / 2, 0.5, r / 2, 0);
        l.playSound(null, p.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 1.5F, 0.9F);
    }

    /** Everything around that's hostile by your rules (friends are left be). */
    private static void around(ServerPlayer p, double r, Consumer<LivingEntity> what) {
        for (LivingEntity e : p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(r), e -> e != p && e.isAlive() && Targets.hostile(p, e))) what.accept(e);
    }

    private static Move infect() { return new Move("Infectious bite", 15, p -> bite(p, 8F, new MobEffectInstance(MobEffects.POISON, 160, 1))); }

    /** A bite (or claw, or touch) on whatever's in front of you. */
    private static void bite(ServerPlayer p, float dmg, MobEffectInstance after) {
        LivingEntity t = target(p, 5);
        if (t == null) { dash(p, 1.2); return; }
        PowerKills.hurt(p, t, p.damageSources().playerAttack(p), dmg);
        if (after != null) t.addEffect(new MobEffectInstance(after));
        p.level().playSound(null, t.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1F, 0.8F);
    }

    /** Breath down a cone in front of you: fire, ice, lightning, water or poison. */
    private static void breath(ServerPlayer p, String kind) {
        ServerLevel l = p.serverLevel();
        Vec3 eye = p.getEyePosition(), d = look(p);
        var part = switch (kind) { case "ice" -> ParticleTypes.SNOWFLAKE; case "lightning" -> ParticleTypes.ELECTRIC_SPARK; case "water" -> ParticleTypes.SPLASH; case "poison" -> ParticleTypes.SNEEZE; default -> ParticleTypes.FLAME; };
        for (int i = 1; i <= 14; i++) { Vec3 q = eye.add(d.scale(i)); l.sendParticles(part, q.x, q.y, q.z, 6, i * 0.08, i * 0.08, i * 0.08, 0.02); }
        for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new AABB(eye, eye.add(d.scale(14))).inflate(4), e -> e != p && e.isAlive() && Targets.hostile(p, e))) {
            Vec3 rel = e.position().add(0, e.getBbHeight() / 2, 0).subtract(eye);
            double along = rel.dot(d);
            if (along < 0 || along > 14 || rel.subtract(d.scale(along)).length() > 1 + along * 0.35) continue;
            switch (kind) {
                case "ice" -> { PowerKills.hurt(p, e, l.damageSources().freeze(), 8F); e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 160, 3)); e.setTicksFrozen(e.getTicksRequiredToFreeze() + 140); }
                case "lightning" -> { net.minecraft.world.entity.LightningBolt b = EntityType.LIGHTNING_BOLT.create(l); if (b != null) { b.moveTo(e.getX(), e.getY(), e.getZ()); b.setCause(p); l.addFreshEntity(b); } PowerKills.credit(p, e); }
                case "water" -> { PowerKills.hurt(p, e, l.damageSources().drown(), 6F); e.push(d.x * 1.8, 0.3, d.z * 1.8); e.hurtMarked = true; }
                case "poison" -> { PowerKills.hurt(p, e, l.damageSources().indirectMagic(p, p), 4F); e.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 2)); }
                default -> { PowerKills.hurt(p, e, l.damageSources().onFire(), 8F); e.igniteForSeconds(6); }
            }
        }
        l.playSound(null, p.blockPosition(), kind.equals("ice") ? SoundEvents.POWDER_SNOW_BREAK : kind.equals("lightning") ? SoundEvents.TRIDENT_THUNDER.value() : kind.equals("water") ? SoundEvents.GENERIC_SPLASH : SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.2F, 0.8F);
    }

    /** A gaze that turns flesh to stone: held fast, blind and weak. */
    private static void petrify(ServerPlayer p) {
        LivingEntity t = target(p, 24);
        if (t == null) { p.displayClientMessage(Component.literal("Meet something's eyes.").withStyle(ChatFormatting.GRAY), true); return; }
        PowerKills.credit(p, t);
        t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 9));
        t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 4));
        t.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 200, 4));
        t.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 120, 0));
        if (t instanceof net.minecraft.world.entity.Mob mob) { mob.getNavigation().stop(); mob.setTarget(null); }
        p.serverLevel().sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState()), t.getX(), t.getY() + t.getBbHeight() / 2, t.getZ(), 40, t.getBbWidth() / 2, t.getBbHeight() / 2, t.getBbWidth() / 2, 0.1);
        p.level().playSound(null, t.blockPosition(), SoundEvents.STONE_PLACE, SoundSource.PLAYERS, 1.5F, 0.6F);
    }

    /** A song (or a voice) that draws everything hostile to you. */
    private static void pull(ServerPlayer p, double r) {
        around(p, r, e -> { Vec3 to = p.position().subtract(e.position()).normalize().scale(1.2); e.push(to.x, 0.3, to.z); e.hurtMarked = true; e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1)); });
        p.level().playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 2F, 0.7F);
    }

    private static void spell(ServerPlayer p, float dmg) {
        LivingEntity t = target(p, 24);
        if (t == null) { arrow(p, new MobEffectInstance(MobEffects.HARM, 1, 0)); return; }
        ServerLevel l = p.serverLevel();
        Vec3 a = p.getEyePosition(), b = t.position().add(0, t.getBbHeight() / 2, 0);
        for (int i = 0; i <= 20; i++) { Vec3 q = a.lerp(b, i / 20.0); l.sendParticles(ParticleTypes.WITCH, q.x, q.y, q.z, 2, 0.05, 0.05, 0.05, 0); }
        PowerKills.hurt(p, t, l.damageSources().indirectMagic(p, p), dmg);
        l.playSound(null, p.blockPosition(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 1F, 1.2F);
    }

    private static void volleyArrows(ServerPlayer p, int n) {
        for (int i = 0; i < n; i++) {
            Arrow a = new Arrow(p.level(), p, new ItemStack(Items.ARROW), null);
            a.pickup = AbstractArrow.Pickup.DISALLOWED;
            a.shootFromRotation(p, p.getXRot(), p.getYRot() + (i - n / 2) * 6F, 0, 2.6F, 1F);
            p.level().addFreshEntity(a);
        }
        p.level().playSound(null, p.blockPosition(), SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 1F, 1F);
    }

    /** Webs where you're looking: for a few seconds only. */
    private static void web(ServerPlayer p) {
        HitResult h = p.pick(20, 0, false);
        BlockPos at = BlockPos.containing(h.getLocation().subtract(look(p).scale(0.5)));
        ServerLevel l = p.serverLevel();
        List<BlockPos> placed = new ArrayList<>();
        for (BlockPos q : BlockPos.betweenClosed(at.offset(-1, 0, -1), at.offset(1, 1, 1)))
            if (l.getBlockState(q).isAir()) { l.setBlock(q, net.minecraft.world.level.block.Blocks.COBWEB.defaultBlockState(), 3); placed.add(q.immutable()); }
        l.playSound(null, at, SoundEvents.SPIDER_AMBIENT, SoundSource.PLAYERS, 1F, 1.3F);
        l.getServer().tell(new net.minecraft.server.TickTask(l.getServer().getTickCount() + 100, () -> { for (BlockPos q : placed) if (l.getBlockState(q).is(net.minecraft.world.level.block.Blocks.COBWEB)) l.removeBlock(q, false); }));
    }

    /** Gone from sight for a moment, and somewhere else. */
    private static void phase(ServerPlayer p) {
        p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 80, 0, false, false, true));
        blink(p);
    }

    private static void dash(ServerPlayer p, double s) {
        Vec3 d = look(p).scale(s);
        p.setDeltaMovement(d.x, Math.max(0.3, d.y), d.z);
        p.hurtMarked = true;
        p.fallDistance = 0;
        p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8F, 1.2F);
    }
}
