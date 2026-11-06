package com.breachprotocol.career;

import com.breachprotocol.compat.Unleashed;
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
import net.minecraft.world.entity.Mob;
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
            new Directive("lockdown", "Site Lockdown", "Every anomaly within 64m that stasis can hold is pinned in place for 60 seconds.", Rank.LEVEL_5, 15 * 60),
            new Directive("amnestic", "Amnestic Protocol", "Clears harmful effects from all personnel within 32m.", Rank.OVERSEER, 10 * 60),
            new Directive("mtf", "Deploy Nu-7 Strike Team", "The Council sends SCP Unleashed's Nu-7 Strike Team to you at once. No fragment needed.", Rank.OVERSEER, 20 * 60),
            new Directive("convene", "Convene the Council", "Call the O5 Council to you now and decide something.", Rank.ADMINISTRATOR, 5 * 60),
            new Directive("alpha1", "Mobilise Alpha-1", "SCP Unleashed's Alpha-1 Detachment, the Red Right Hand, answers to you alone.", Rank.ADMINISTRATOR, 15 * 60),
            new Directive("fortify", "Order: Fortify All Sites", "Every site reinforces containment. Stability +8.", Rank.ADMINISTRATOR, 20 * 60),
            new Directive("global", "Global Recontainment", "Every anomaly within 128m that stasis can hold is pinned for 5 minutes.", Rank.ADMINISTRATOR, 30 * 60),
            new Directive("tower", "Construct a Site Tower", "Builds a full Foundation site here: a ten-storey tower over Light and Heavy Containment. Clear space first. One per Overseer.", Rank.OVERSEER, 60),
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
                Career.give(p, Unleashed.item("amnestic_canister", Items.GOLDEN_APPLE), 2);
                if (r.atLeast(Rank.OVERSEER)) Career.give(p, Unleashed.scp500(), 1);
                if (r.atLeast(Rank.LEVEL_5)) Career.give(p, Unleashed.item("mtf_signal_radio", Items.COMPASS), 1);
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
            case "mtf" -> deploy(p, "Severe", O5.O5_8, "Nu-7 is in the air. They'll find you.");
            case "convene" -> {
                if (d.call != null) {
                    p.displayClientMessage(Component.literal("Answer the current call first.").withStyle(ChatFormatting.RED), true);
                    yield false;
                }
                Calls.startRandom(p);
                yield true;
            }
            case "alpha1" -> deploy(p, "Abyssal", O5.O5_7, "Alpha-1 answers only to you now. Even we can't recall them.");
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
            case "tower" -> {
                if (d.builtTower && !p.getAbilities().instabuild) {
                    p.displayClientMessage(Component.literal("You already commissioned a tower. (Creative can build more.)").withStyle(ChatFormatting.RED), true);
                    yield false;
                }
                if (p.level().dimension() != Level.OVERWORLD) {
                    p.displayClientMessage(Component.literal("Towers are built in the Overworld.").withStyle(ChatFormatting.RED), true);
                    yield false;
                }
                p.displayClientMessage(Component.literal("Construction under way. This takes a moment.").withStyle(ChatFormatting.GOLD), true);
                BlockPos origin = com.breachprotocol.world.SiteTower.build(p.serverLevel(), p.blockPosition(), p.getUUID().toString(), p.getGameProfile().getName());
                d.builtTower = true;
                p.teleportTo(origin.getX() + 15.5, origin.getY() + 1, origin.getZ() + 24.5);
                Career.message(p, O5.O5_6, "Your site is standing. Entrance Zone at ground level, containment below, the Council chamber and your office at the top. Cells are assigned and keyed.");
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

    /** Pins anomalies around the player in place (anything SCP Unleashed's stasis can hold). */
    public static int containAround(ServerPlayer p, double radius, int ticks) {
        int n = 0;
        for (Mob m : p.level().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(radius), Unleashed::canBeContained)) {
            m.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 9, false, true));
            m.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 4, false, true));
            m.setTarget(null);
            n++;
        }
        return n;
    }

    /** Council privilege: an SCP Unleashed squad deployed immediately (MtfMissions.deployNow). */
    private static boolean deploy(ServerPlayer p, String tier, O5 from, String ok) {
        String out = com.breachprotocol.compat.UnleashedApi.deployMtfNow(p, tier);
        if ("Called".equals(out)) {
            Career.message(p, from, ok);
            return true;
        }
        p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.scp_unleashed.mtf.deny." + (out == null ? "failed" : switch (out) {
            case "Busy" -> "busy";
            case "NoSky" -> "no_sky";
            default -> "failed";
        })).withStyle(ChatFormatting.RED), false);
        return false;
    }
}
