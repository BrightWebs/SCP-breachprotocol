package com.breachprotocol.garden;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.career.SkullKing;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.animal.Panda;
import net.minecraft.world.entity.animal.Ocelot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Sitting in the Garden in True Form, the creatures come to it. Sit still a while and whatever's near enough to see
 * you (three at most) wanders over: a parrot flies to its shoulder and then hops down onto its hand; cats, foxes and
 * wolves settle beside it; the rest come to be fed, each what it eats (bamboo for a panda, seeds for a chicken, a
 * carrot for a rabbit...), tossed down in front of them; they eat, they're healed and glad of it (hearts), and stay
 * sitting round it. Stand up and the parrot's let fly, and they all get up and go back to what they were doing.
 */
@EventBusSubscriber(modid = BreachProtocol.MODID)
public final class Companions {
    private Companions() {}

    private enum Role { PARROT, BESIDE, FEED }

    private static final class Mate {
        final Mob mob; final Role role; final int slot;
        int stage, timer;
        ItemEntity food;
        Mate(Mob mob, Role role, int slot) { this.mob = mob; this.role = role; this.slot = slot; }
    }

    private static final class Sit {
        int still;
        Vec3 at;
        final List<Mate> mates = new ArrayList<>();
    }

    private static final Map<UUID, Sit> SITS = new HashMap<>();
    private static final Set<UUID> TAKEN = new HashSet<>();

    // where things go on and round it, in its own pixels (sitting cross-legged, facing +z, +x its left)
    private static final Vec3 SHOULDER = new Vec3(-15, 33.5, 10), HAND = new Vec3(6.8, 12.8, 9.6);
    private static final Vec3[] SPOTS = {new Vec3(34, 0, 26), new Vec3(-4, 0, 42), new Vec3(40, 0, 2), new Vec3(-30, 0, 32)};

    private static Vec3 place(ServerPlayer p, Vec3 px) {
        double S = 0.85 / 16 * p.getScale() / 1.75, a = -p.yBodyRot * Mth.DEG_TO_RAD;
        double x = px.x * S, z = px.z * S;
        return p.position().add(x * Math.cos(a) + z * Math.sin(a), px.y * S, -x * Math.sin(a) + z * Math.cos(a));
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        MinecraftServer srv = event.getServer();
        for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
            boolean sitting = Garden.inside(p.level()) && SkullKing.whole(p) && p.isCrouching() && p.onGround();
            Sit s = SITS.get(p.getUUID());
            if (sitting && s != null && s.at != null && s.at.distanceToSqr(p.position()) > 0.04) sitting = false;   // it moved: that's a prowl
            if (!sitting) { if (s != null) { release(p, s); SITS.remove(p.getUUID()); } continue; }
            if (s == null) { s = new Sit(); s.at = p.position(); SITS.put(p.getUUID(), s); }
            s.still++;
            try { tend(p, s); } catch (Throwable t) { BreachProtocol.LOGGER.debug("Garden companions: {}", t.toString()); }
        }
        SITS.keySet().removeIf(id -> srv.getPlayerList().getPlayer(id) == null);
    }

    private static void tend(ServerPlayer p, Sit s) {
        ServerLevel l = p.serverLevel();
        // someone new, now and then, once it's been sitting a little while
        if (s.still > 50 && s.still % 30 == 0 && s.mates.size() < 3) {
            Mob best = null;
            double bd = Double.MAX_VALUE;
            for (Mob a : l.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(14), m -> m instanceof Animal || m instanceof Parrot)) {
                if (!a.isAlive() || TAKEN.contains(a.getUUID()) || a.isLeashed() || a.isPassenger() || a.isVehicle() || !a.hasLineOfSight(p)) continue;
                if (a instanceof Parrot && s.mates.stream().anyMatch(m -> m.role == Role.PARROT)) continue;
                double d = a.distanceToSqr(p);
                if (d < bd) { bd = d; best = a; }
            }
            if (best != null) {
                Role role = best instanceof Parrot ? Role.PARROT : best instanceof TamableAnimal || best instanceof Fox || best instanceof Ocelot ? Role.BESIDE : Role.FEED;
                int slot = 0;
                while (slot < SPOTS.length) { final int sl = slot; if (s.mates.stream().noneMatch(m -> m.slot == sl)) break; slot++; }
                s.mates.add(new Mate(best, role, slot));
                TAKEN.add(best.getUUID());
            }
        }
        for (Mate m : new ArrayList<>(s.mates)) {
            Mob a = m.mob;
            if (!a.isAlive() || a.level() != l) { s.mates.remove(m); TAKEN.remove(a.getUUID()); continue; }
            a.getLookControl().setLookAt(p, 30, 30);
            m.timer++;
            switch (m.role) {
                case PARROT -> parrot(p, l, m);
                case BESIDE -> beside(p, m);
                case FEED -> feed(p, l, m);
            }
        }
    }

    private static void parrot(ServerPlayer p, ServerLevel l, Mate m) {
        Mob a = m.mob;
        Vec3 shoulder = place(p, SHOULDER), hand = place(p, HAND);
        a.setNoGravity(true);
        a.getNavigation().stop();
        a.setDeltaMovement(Vec3.ZERO);
        a.setYRot(p.yBodyRot); a.setYBodyRot(p.yBodyRot); a.setYHeadRot(p.yBodyRot);
        if (m.stage == 0) {
            // flying in to the shoulder
            Vec3 to = shoulder.subtract(a.position());
            double d = to.length();
            if (d < 0.25) { m.stage = 1; m.timer = 0; a.setNoAi(true); l.playSound(null, a.blockPosition(), SoundEvents.PARROT_STEP, SoundSource.NEUTRAL, 0.8F, 1.2F); }
            else a.setPos(a.position().add(to.scale(Math.min(1, 0.35 / d))).add(0, d > 1.5 ? Math.sin(m.timer * 0.5) * 0.04 : 0, 0));
        } else if (m.stage == 1) {
            a.setPos(shoulder);
            if (m.timer > 120) { m.stage = 2; m.timer = 0; }
        } else if (m.stage == 2) {
            // a hop down onto the hand
            double k = Math.min(1, m.timer / 14.0);
            Vec3 at = shoulder.lerp(hand, k).add(0, Math.sin(k * Math.PI) * 0.6, 0);
            a.setPos(at);
            if (k >= 1) { m.stage = 3; l.playSound(null, a.blockPosition(), SoundEvents.PARROT_STEP, SoundSource.NEUTRAL, 0.8F, 1.4F); }
        } else a.setPos(hand);
    }

    private static void beside(ServerPlayer p, Mate m) {
        Mob a = m.mob;
        Vec3 spot = place(p, SPOTS[m.slot]);
        if (m.stage == 0) {
            if (a.position().distanceToSqr(spot) < 0.6 || m.timer > 400) { m.stage = 1; a.getNavigation().stop(); sit(a, true); }
            else if (m.timer % 10 == 1) a.getNavigation().moveTo(spot.x, spot.y, spot.z, 0.9);
        } else { a.getNavigation().stop(); a.setDeltaMovement(a.getDeltaMovement().multiply(0, 1, 0)); }
    }

    private static void feed(ServerPlayer p, ServerLevel l, Mate m) {
        Mob a = m.mob;
        Vec3 spot = place(p, SPOTS[m.slot]);
        switch (m.stage) {
            case 0 -> {
                if (a.position().distanceToSqr(spot) < 0.8 || m.timer > 400) {
                    m.stage = 1; m.timer = 0; a.getNavigation().stop();
                    // tossed down in front of it, from the hand
                    Item food = food(a.getType());
                    Vec3 from = place(p, HAND).add(0, 0.3, 0);
                    ItemEntity it = new ItemEntity(l, from.x, from.y, from.z, new ItemStack(food));
                    it.setPickUpDelay(32767);
                    it.setUnlimitedLifetime();
                    Vec3 to = a.position().subtract(from);
                    it.setDeltaMovement(to.x * 0.12, 0.25, to.z * 0.12);
                    l.addFreshEntity(it);
                    m.food = it;
                    if (a instanceof Panda pd) pd.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.BAMBOO));
                    l.playSound(null, p.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.5F, 0.6F);
                } else if (m.timer % 10 == 1) a.getNavigation().moveTo(spot.x, spot.y, spot.z, 0.9);
            }
            case 1 -> {
                a.getNavigation().stop();
                if (m.food != null && m.food.isAlive()) a.getLookControl().setLookAt(m.food.getX(), m.food.getY(), m.food.getZ());
                if (m.timer % 8 == 0 && m.timer > 20) l.sendParticles(new net.minecraft.core.particles.ItemParticleOption(ParticleTypes.ITEM, new ItemStack(food(a.getType()))), a.getX(), a.getY() + a.getBbHeight() * 0.6, a.getZ(), 4, 0.15, 0.1, 0.15, 0.05);
                if (m.timer % 8 == 0 && m.timer > 20) l.playSound(null, a.blockPosition(), a instanceof Panda ? SoundEvents.PANDA_EAT : SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 0.6F, 1F);
                if (m.timer > 70) {
                    // fed: healed, and happy
                    if (m.food != null) m.food.discard();
                    m.food = null;
                    a.heal(a.getMaxHealth());
                    a.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.REGENERATION, 20 * 30, 0, false, false));
                    l.sendParticles(ParticleTypes.HEART, a.getX(), a.getY() + a.getBbHeight() + 0.3, a.getZ(), 5, 0.3, 0.2, 0.3, 0.02);
                    m.stage = 2; m.timer = 0;
                    sit(a, true);
                }
            }
            default -> { a.getNavigation().stop(); a.setDeltaMovement(a.getDeltaMovement().multiply(0, 1, 0)); }
        }
    }

    private static void sit(Mob a, boolean on) {
        if (a instanceof TamableAnimal t) t.setInSittingPose(on);
        if (a instanceof Fox f) f.setSitting(on);
        if (a instanceof Panda pd) pd.sit(on);
    }

    /** It stands up: the parrot's let fly, and they all go back to what they were doing. */
    private static void release(ServerPlayer p, Sit s) {
        for (Mate m : s.mates) {
            Mob a = m.mob;
            TAKEN.remove(a.getUUID());
            if (m.food != null) m.food.discard();
            if (!a.isAlive()) continue;
            if (m.role == Role.PARROT) {
                a.setNoAi(false);
                a.setNoGravity(false);
                Vec3 away = a.position().subtract(p.position()).multiply(1, 0, 1).normalize();
                a.setDeltaMovement(away.x * 0.4, 0.7, away.z * 0.4);
                a.hasImpulse = true;
                p.serverLevel().playSound(null, a.blockPosition(), SoundEvents.PARROT_FLY, SoundSource.NEUTRAL, 1F, 1F);
            } else sit(a, false);
        }
        s.mates.clear();
    }

    /** What each eats. */
    private static Item food(EntityType<?> t) {
        if (t == EntityType.PANDA) return Items.BAMBOO;
        if (t == EntityType.CHICKEN || t == EntityType.PARROT) return Items.WHEAT_SEEDS;
        if (t == EntityType.PIG || t == EntityType.RABBIT) return Items.CARROT;
        if (t == EntityType.HORSE || t == EntityType.DONKEY || t == EntityType.MULE) return Items.APPLE;
        if (t == EntityType.LLAMA || t == EntityType.TRADER_LLAMA) return Items.HAY_BLOCK;
        if (t == EntityType.FOX) return Items.SWEET_BERRIES;
        if (t == EntityType.WOLF) return Items.BONE;
        if (t == EntityType.CAT || t == EntityType.OCELOT) return Items.COD;
        if (t == EntityType.TURTLE) return Items.SEAGRASS;
        if (t == EntityType.BEE) return Items.DANDELION;
        if (t == EntityType.CAMEL) return Items.CACTUS;
        if (t == EntityType.FROG) return Items.SLIME_BALL;
        if (t == EntityType.SNIFFER) return Items.TORCHFLOWER_SEEDS;
        if (t == EntityType.ARMADILLO) return Items.SPIDER_EYE;
        if (t == EntityType.GOAT || t == EntityType.COW || t == EntityType.SHEEP || t == EntityType.MOOSHROOM) return Items.WHEAT;
        return Items.WHEAT;
    }
}
