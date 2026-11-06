package com.breachprotocol.career;

import com.breachprotocol.entity.Anomaly;
import com.breachprotocol.registry.ModEffects;
import com.breachprotocol.registry.ModItems;
import com.breachprotocol.world.Site01;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/** Orders you can give from your phone. What you can order depends on who you are. */
public final class Directives {
    private Directives() {}

    public record Directive(String id, String label, String description, Rank minRank, int cooldownSeconds) {}

    public static final List<Directive> ALL = List.of(
            new Directive("requisition", "Requisition Supplies", "Request a supply crate from the Accountant.", Rank.LEVEL_4, 20 * 60),
            new Directive("lockdown", "Site Lockdown", "Every anomaly within 64m is held in place for 60 seconds.", Rank.LEVEL_5, 15 * 60),
            new Directive("amnestic", "Amnestic Protocol", "Clears harmful effects from all personnel within 32m.", Rank.OVERSEER, 10 * 60),
            new Directive("mtf", "Deploy MTF Epsilon-11", "Three Nine-Tailed Fox operatives deploy to your position.", Rank.OVERSEER, 20 * 60),
            new Directive("convene", "Convene the Council", "Call the O5 Council to you now and decide something.", Rank.ADMINISTRATOR, 5 * 60),
            new Directive("alpha1", "Mobilise Alpha-1", "The Red Right Hand: two elite guards, loyal only to you.", Rank.ADMINISTRATOR, 15 * 60),
            new Directive("fortify", "Order: Fortify All Sites", "Every site reinforces containment. Stability +8.", Rank.ADMINISTRATOR, 20 * 60),
            new Directive("global", "Global Recontainment", "Every anomaly within 128m is held for 5 minutes.", Rank.ADMINISTRATOR, 30 * 60),
            new Directive("site01", "Travel to Site-01", "Teleport to your secret sanctum.", Rank.ADMINISTRATOR, 10),
            new Directive("return", "Return from Site-01", "Go back to where you were.", Rank.ADMINISTRATOR, 10));

    public static List<Directive> available(CareerData d) {
        List<Directive> out = new ArrayList<>();
        for (Directive dir : ALL) if (d.getRank().atLeast(dir.minRank())) out.add(dir);
        return out;
    }

    public static void run(ServerPlayer p, String id) {
        CareerData d = Career.data(p);
        Directive dir = ALL.stream().filter(x -> x.id().equals(id)).findFirst().orElse(null);
        if (dir == null || !d.getRank().atLeast(dir.minRank())) return;
        long now = p.level().getGameTime();
        long ready = d.cooldowns.getOrDefault(id, 0L);
        if (now < ready) {
            p.displayClientMessage(Component.literal(dir.label() + " is on cooldown (" + (ready - now) / 20 + "s).").withStyle(ChatFormatting.RED), true);
            return;
        }
        boolean ok = switch (id) {
            case "requisition" -> {
                Rank r = d.getRank();
                Career.give(p, Items.BREAD, 8);
                Career.give(p, Items.IRON_INGOT, 8);
                Career.give(p, ModItems.EYEDROPS.get(), 2);
                if (r.atLeast(Rank.OVERSEER)) Career.give(p, ModItems.SCP_500.get(), 1);
                if (r == Rank.ADMINISTRATOR) Career.give(p, Items.DIAMOND, 4);
                Career.message(p, O5.O5_12, "Crate delivered. It's coming out of someone's budget. Not yours.");
                yield true;
            }
            case "lockdown" -> {
                int n = containAround(p, 64, 20 * 60);
                Career.message(p, O5.O5_6, "Lockdown engaged. " + n + " anomalies frozen in place.");
                yield true;
            }
            case "amnestic" -> {
                for (ServerPlayer o : p.serverLevel().players()) {
                    if (o.distanceToSqr(p) < 32 * 32) {
                        for (var e : new ArrayList<>(o.getActiveEffects())) {
                            if (!e.getEffect().value().isBeneficial()) o.removeEffect(e.getEffect());
                        }
                        Calls.clearBlink(o);
                    }
                }
                Career.message(p, O5.O5_11, "Amnestics administered. Whatever just happened, didn't.");
                yield true;
            }
            case "mtf" -> {
                spawnMtf(p, 3, "MTF Epsilon-11 \"Nine-Tailed Fox\"", false);
                Career.message(p, O5.O5_8, "Nine-Tailed Fox is on the ground at your position.");
                yield true;
            }
            case "convene" -> {
                if (d.call != null) {
                    p.displayClientMessage(Component.literal("Answer the current call first.").withStyle(ChatFormatting.RED), true);
                    yield false;
                }
                Calls.startRandom(p);
                yield true;
            }
            case "alpha1" -> {
                spawnMtf(p, 2, "MTF Alpha-1 \"Red Right Hand\"", true);
                Career.message(p, O5.O5_7, "Alpha-1 answers only to you now. Even we can't recall them.");
                yield true;
            }
            case "fortify" -> {
                Career.changeStability(p, 8);
                Career.message(p, O5.O5_6, "Every site is reinforcing. It'll hold.");
                yield true;
            }
            case "global" -> {
                int n = containAround(p, 128, 20 * 300);
                Career.message(p, O5.O5_7, "Global Recontainment. " + n + " anomalies locked down. The world is quiet for five minutes.");
                yield true;
            }
            case "site01" -> {
                if (Site01.isInside(p)) yield false;
                d.hasReturn = true;
                d.returnDim = p.level().dimension().location().toString();
                d.returnX = p.getX();
                d.returnY = p.getY();
                d.returnZ = p.getZ();
                ServerLevel ow = p.server.overworld();
                BlockPos site = Site01.ensureBuilt(ow);
                BlockPos arrive = Site01.arrival(site);
                p.teleportTo(ow, arrive.getX() + 0.5, arrive.getY(), arrive.getZ() + 0.5, -90F, 0F);
                p.displayClientMessage(Component.literal("Welcome home, Administrator.").withStyle(ChatFormatting.LIGHT_PURPLE), true);
                yield true;
            }
            case "return" -> {
                if (!d.hasReturn) yield false;
                ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(d.returnDim));
                ServerLevel lvl = p.server.getLevel(key);
                if (lvl == null) lvl = p.server.overworld();
                p.teleportTo(lvl, d.returnX, d.returnY, d.returnZ, p.getYRot(), p.getXRot());
                d.hasReturn = false;
                yield true;
            }
            default -> false;
        };
        if (ok) {
            d.cooldowns.put(id, now + dir.cooldownSeconds() * 20L);
            d.dirty = true;
            Career.sync(p);
        }
    }

    /** Freeze anomalies around the player. Returns how many. */
    public static int containAround(ServerPlayer p, double radius, int ticks) {
        int n = 0;
        for (Mob m : p.level().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(radius), e -> e instanceof Anomaly a && a.canBeContained())) {
            m.addEffect(new MobEffectInstance(ModEffects.h(ModEffects.CONTAINED), ticks, 0, false, false, true));
            n++;
        }
        return n;
    }

    public static void spawnMtf(ServerPlayer p, int count, String name, boolean elite) {
        ServerLevel level = p.serverLevel();
        for (int i = 0; i < count; i++) {
            IronGolem g = EntityType.IRON_GOLEM.create(level);
            if (g == null) continue;
            double ang = Math.PI * 2 * i / count;
            g.moveTo(p.getX() + Math.cos(ang) * 2.5, p.getY(), p.getZ() + Math.sin(ang) * 2.5, p.getYRot(), 0);
            g.setPlayerCreated(true);
            g.setCustomName(Component.literal(name).withStyle(elite ? ChatFormatting.DARK_RED : ChatFormatting.BLUE));
            g.setPersistenceRequired();
            if (elite) {
                g.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, MobEffectInstance.INFINITE_DURATION, 2, false, false));
                g.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, MobEffectInstance.INFINITE_DURATION, 2, false, false));
                g.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, MobEffectInstance.INFINITE_DURATION, 1, false, false));
            }
            level.addFreshEntity(g);
        }
    }
}
