package com.breachprotocol.career;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.compat.Unleashed;
import com.breachprotocol.entity.O5MemberEntity;
import com.breachprotocol.registry.ModItems;
import com.breachprotocol.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = BreachProtocol.MODID)
public final class CareerEvents {
    private CareerEvents() {}

    public static final int RITUAL_TICKS = 200;

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        CareerData d = Career.data(p);
        if (!d.recruited) {
            d.recruited = true;
            Career.give(p, ModItems.PHONE.get(), 1);
            Career.message(p, O5.O5_3, "> NEW PERSONNEL FILE: " + Career.personnelId(p) + ". CLASSIFICATION: CLASS-D. A FOUNDATION PHONE HAS BEEN ISSUED. DO NOT LOSE IT.");
            Career.message(p, O5.O5_7, "You were scheduled for termination. Do something useful and you might not be. Orders will come through your phone.");
            d.nextMission = p.level().getGameTime() + 400;
        }
        if (d.ascended) applyAscendedAbilities(p);
        Career.sync(p);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            if (Career.isAscended(p)) applyAscendedAbilities(p);
            Career.sync(p);
        }
    }

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        CareerData d = Career.data(p);
        if (d.ritualTicks > 0) ritualTick(p, d);
        if (p.tickCount % 20 != 0) return;
        Career.tick(p);
        if (d.ascended) {
            // SCP Unleashed's Gate Guardian spares anything invulnerable (Scp001GateStrike.immortal),
            // and only shoves away things that carry its avoid marker - which SCP-Omega must not have.
            if (p.getTags().contains(GATE_SPARED)) p.removeTag(GATE_SPARED);
            shield(p);
            p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 400, 0, true, false, false));
            p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 1, true, false, false));
            p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 1, true, false, false));
            p.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 60, 0, true, false, false));
            if (!p.getAbilities().mayfly && !p.isSpectator()) applyAscendedAbilities(p);
            p.serverLevel().sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 2, 0.4, 0.6, 0.4, 0.01);
        }
    }

    // ------------------------------------------------------------------ the ritual

    public static void beginRitual(ServerPlayer p) {
        CareerData d = Career.data(p);
        if (d.ritualTicks > 0 || d.ascended) return;
        d.ritualTicks = RITUAL_TICKS;
        p.serverLevel().playSound(null, p.blockPosition(), ModSounds.ASCENSION.get(), SoundSource.PLAYERS, 3.0F, 1.0F);
        p.sendSystemMessage(Component.literal("You stop being a person.").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC));
    }

    private static void ritualTick(ServerPlayer p, CareerData d) {
        d.ritualTicks--;
        int t = RITUAL_TICKS - d.ritualTicks;
        p.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 5, 0, true, false, false));
        p.serverLevel().sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 6 + t / 10, 0.6 + t / 100.0, 1.0, 0.6 + t / 100.0, 0.05);
        p.serverLevel().sendParticles(ParticleTypes.SOUL_FIRE_FLAME, p.getX(), p.getY(), p.getZ(), 3, 1.5, 0.2, 1.5, 0.02);
        if (t == 60) p.sendSystemMessage(Component.literal("Every file in the archive rewrites itself with your name.").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
        if (t == 120) p.sendSystemMessage(Component.literal("Somewhere, every anomaly turns to face you.").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
        if (d.ritualTicks <= 0) ascend(p, d);
    }

    /** SCP Unleashed's gate-avoider tag: avoiders get pushed out of the Gate. Removed from SCP-Omega. */
    public static final String GATE_SPARED = "scp001gate_avoider";

    /**
     * SCP-Ω's invulnerability follows its Temperament. Minecraft creatures never go for an invulnerable
     * player, so while either anomalies or other creatures are set to treat you "as anyone", you aren't
     * invulnerable: they see you, come for you and hit you (and you still can't die: see onDeath). Only
     * when both are set to leave you be, or fear you, are you untouchable again (which is also what SCP
     * Unleashed's Gate Guardian looks for when it decides whom to spare).
     */
    public static void shield(ServerPlayer p) {
        boolean on = Career.isAscended(p) && Dominion.auraAnomalies(p) != 0 && Dominion.auraCreatures(p) != 0;
        if (p.isInvulnerable() != on && !p.isCreative() && !p.isSpectator()) p.setInvulnerable(on);
    }

    public static void ascend(ServerPlayer p, CareerData d) {
        p.removeTag(GATE_SPARED);
        p.setInvulnerable(true);
        d.ascended = true;
        d.stability = 100;
        // SCP-Ω isn't one of the thirteen any more: any chair at the table goes back to its Overseer
        Council.vacate(p);
        com.breachprotocol.world.Site01.Data sd = com.breachprotocol.world.Site01.data(p.server);
        sd.ascendant = p.getUUID().toString();
        sd.setDirty();
        d.missions.clear();
        d.dirty = true;
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(p.serverLevel());
        if (bolt != null) {
            bolt.moveTo(p.position());
            bolt.setVisualOnly(true);
            p.serverLevel().addFreshEntity(bolt);
        }
        p.connection.send(new ClientboundSetTitlesAnimationPacket(20, 100, 40));
        p.connection.send(new ClientboundSetTitleTextPacket(Component.literal("SCP-Ω").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD)));
        p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("The Ascendant - the greatest anomaly").withStyle(ChatFormatting.GOLD)));
        p.setHealth(p.getMaxHealth());
        applyAscendedAbilities(p);
        Career.broadcast(p.server, Component.literal("[FOUNDATION-WIDE] ").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD)
                .append(Component.literal("A new anomaly has been catalogued: SCP-Ω \"The Ascendant\", formerly " + p.getName().getString()
                        + ". Object class: Apollyon. Containment: impossible. It owns us now.").withStyle(ChatFormatting.LIGHT_PURPLE)));
        Career.message(p, O5.O5_1, "So you did it. I always wondered what was past the Gate. Every anomaly answers to you now. Try to be kinder than we were.");
        Career.sync(p);
    }

    public static void applyAscendedAbilities(ServerPlayer p) {
        if (p.isSpectator()) return;
        p.getAbilities().mayfly = true;
        p.onUpdateAbilities();
    }

    // ------------------------------------------------------------------ immortality + mastery

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && Career.isAscended(p)) {
            if (event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return; // /kill and the void still work
            event.setCanceled(true);
            p.setHealth(p.getMaxHealth());
            p.removeAllEffects();
            p.level().broadcastEntityEvent(p, (byte) 35); // totem flash
            p.displayClientMessage(Component.literal("You cannot die.").withStyle(ChatFormatting.LIGHT_PURPLE), true);
            return;
        }
        if (event.getSource().getEntity() instanceof ServerPlayer killer && !event.getEntity().level().isClientSide) {
            Career.onKill(killer, event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onDamage(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (event.getSource().getEntity() instanceof Player attacker && Career.isAscended(attacker)
                && Unleashed.isAnomaly(target) && !(target instanceof O5MemberEntity)) {
            event.setAmount(event.getAmount() * 8F); // nothing in the Foundation can survive its master
        }
        if (target instanceof Player p && Career.isAscended(p) && Dominion.sovereign(p)) {
            // Nothing in the Foundation's care may harm its master (unless they've chosen to go unmasked).
            net.minecraft.world.entity.Entity any = event.getSource().getEntity() != null ? event.getSource().getEntity() : event.getSource().getDirectEntity();
            if (any != null && (Unleashed.isAnomaly(any) || com.breachprotocol.compat.UnleashedApi.isGateGuardian(any) || com.breachprotocol.compat.UnleashedApi.isRift(any)))
                p.getPersistentData().putLong("bp_anomaly_hit", p.level().getGameTime());
            net.minecraft.world.entity.Entity src = event.getSource().getEntity() != null ? event.getSource().getEntity() : event.getSource().getDirectEntity();
            if (src != null && (Unleashed.isAnomaly(src) || com.breachprotocol.compat.UnleashedApi.isGateGuardian(src))) {
                event.setCanceled(true);
                return;
            }
            event.setAmount(event.getAmount() * 0.4F);
        }
    }

    /**
     * Some anomalies finish what they start with a kill() that skips ordinary damage (SCP Unleashed's
     * Gate Guardian smite, SCP-682, ...). If an anomaly just went for SCP-Omega, the death doesn't happen.
     */
    @SubscribeEvent
    public static void onAscendedDeath(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !Career.isAscended(p)) return;
        net.minecraft.world.entity.Entity src = event.getSource().getEntity() != null ? event.getSource().getEntity() : event.getSource().getDirectEntity();
        boolean anomalous = src != null && (Unleashed.isAnomaly(src) || com.breachprotocol.compat.UnleashedApi.isGateGuardian(src) || com.breachprotocol.compat.UnleashedApi.isRift(src));
        long last = p.getPersistentData().getLong("bp_anomaly_hit");
        if (anomalous || p.level().getGameTime() - last <= 5) {
            event.setCanceled(true);
            p.setHealth(p.getMaxHealth());
            p.displayClientMessage(Component.literal("It cannot end you. You are SCP-\u03A9.").withStyle(ChatFormatting.LIGHT_PURPLE), true);
        }
    }

    @SubscribeEvent
    public static void onTarget(LivingChangeTargetEvent event) {
        if (Unleashed.isAnomaly(event.getEntity()) && event.getNewAboutToBeSetTarget() instanceof Player p && Career.isAscended(p) && Dominion.sovereign(p)) {
            event.setCanceled(true);
        }
    }

    /** As SCP-Omega, right-click SCP-001-1, the Gate Guardian: it lowers its blades and lets you into the Garden. */
    @SubscribeEvent
    public static void onGuardian(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide || event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(event.getEntity() instanceof ServerPlayer p) || !Career.isAscended(p)) return;
        if (!com.breachprotocol.compat.UnleashedApi.isGateGuardian(event.getTarget())) return;
        com.breachprotocol.compat.UnleashedApi.calmGuardian(event.getTarget(), 20 * 300);
        p.serverLevel().sendParticles(ParticleTypes.END_ROD, event.getTarget().getX(), event.getTarget().getY() + 6, event.getTarget().getZ(), 60, 1.5, 4, 1.5, 0.04);
        p.displayClientMessage(Component.literal("SCP-001-1 lowers its blades. The way into the Garden is open, Master.").withStyle(ChatFormatting.LIGHT_PURPLE), true);
        GatePass.menu(p, event.getTarget().getId());
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    /** As SCP-Ω, sneak + right-click an anomaly with an empty hand to make it kneel (10 minutes). */
    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide || event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(event.getEntity() instanceof ServerPlayer p) || !Career.isAscended(p) || !p.isShiftKeyDown()) return;
        if (!p.getMainHandItem().isEmpty()) return;
        if (event.getTarget() instanceof Mob mob && Unleashed.canBeContained(mob)) {
            mob.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 20 * 600, 9, false, false));
            mob.setTarget(null);
            p.serverLevel().sendParticles(ParticleTypes.END_ROD, mob.getX(), mob.getY() + 1, mob.getZ(), 30, 0.5, 1, 0.5, 0.05);
            p.displayClientMessage(Component.literal(Unleashed.designation(mob) + " kneels before you.").withStyle(ChatFormatting.LIGHT_PURPLE), true);
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }
}
