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
        if (m.isEmpty()) {
            Entity sample = null;
            try { sample = type.create(l); } catch (Throwable ignored) {}
            if (sample instanceof RangedAttackMob) m.add(new Move("Shot", 15, p -> arrow(p, null)));
            if (sample != null) sample.discard();
        }
        m.add(new Move("Pounce", 30, p -> { dash(p, 1.6); LivingEntity t = target(p, 5); if (t != null) PowerKills.hurt(p, t, p.damageSources().playerAttack(p), (float) Math.max(4, p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE))); }));
        return m;
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

    private static void dash(ServerPlayer p, double s) {
        Vec3 d = look(p).scale(s);
        p.setDeltaMovement(d.x, Math.max(0.3, d.y), d.z);
        p.hurtMarked = true;
        p.fallDistance = 0;
        p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8F, 1.2F);
    }
}
