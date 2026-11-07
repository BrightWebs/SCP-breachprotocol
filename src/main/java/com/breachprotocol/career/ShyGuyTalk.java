package com.breachprotocol.career;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.compat.UnleashedApi;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import com.breachprotocol.entity.Dialogue;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Talking to SCP-096. The Administrator (or the Ascendant) can right-click a Shy Guy with an empty
 * hand and choose:
 * <ul>
 * <li><b>"Calm down, buddy."</b> It calms at once, and that Shy Guy never panics at your face
 *     again: SCP Unleashed's sighting check is told you've already been seen, so your look never
 *     starts its distress. (It still reacts to everyone else.)</li>
 * <li><b>"Die."</b> It decomposes on the spot: it slumps, rots away in ash and smoke, and is gone.</li>
 * </ul>
 */
@EventBusSubscriber(modid = BreachProtocol.MODID)
public final class ShyGuyTalk {
    private ShyGuyTalk() {}

    private static final String KEY = "bp_calm_for";
    private static final Map<Integer, Integer> DYING = new HashMap<>();   // entity id -> ticks left

    private static boolean allowed(ServerPlayer p) {
        return Career.rank(p) == Rank.ADMINISTRATOR || Career.isAscended(p);
    }

    @SubscribeEvent
    public static void interact(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide || event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(event.getEntity() instanceof ServerPlayer p) || !allowed(p) || !p.getMainHandItem().isEmpty() || p.isShiftKeyDown()) return;
        Entity e = event.getTarget();
        if (!UnleashedApi.isShyGuy(e)) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        int id = e.getId();
        boolean calm = calmFor(e).contains(p.getUUID().toString());
        Dialogue.begin(p, "shyguy:" + id, "SCP-096 · \"The Shy Guy\"", "Euclid · it trembles at the sight of you", id);
        Dialogue.line(p, "", calm ? "SCP-096 sits quietly. It knows your face." : "SCP-096 is in front of you, hands over its face, shaking.", 0);
        Dialogue.option(p, "\"Calm down, buddy.\"", "/shyguy calm " + id, "It calms, and never panics at your face again", 0x7FE0FF);
        Dialogue.option(p, "\"Die.\"", "/shyguy die " + id, "It decomposes where it stands", 0xFF5050);
        Dialogue.option(p, "Leave it be", "close", "Walk away", 0x9AA4B2);
        Dialogue.send(p, true);
    }

    private static MutableComponent button(String label, String cmd, String hover, ChatFormatting colour) {
        return Component.literal("[" + label + "]").withStyle(s -> s.withColor(colour).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(hover))));
    }

    @SubscribeEvent
    public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("shyguy")
                .then(Commands.literal("calm").then(Commands.argument("id", IntegerArgumentType.integer()).executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    Entity e = p.serverLevel().getEntity(IntegerArgumentType.getInteger(c, "id"));
                    if (!allowed(p) || e == null || !UnleashedApi.isShyGuy(e) || e.distanceTo(p) > 24) return 0;
                    calm(p, e);
                    return 1;
                })))
                .then(Commands.literal("die").then(Commands.argument("id", IntegerArgumentType.integer()).executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    Entity e = p.serverLevel().getEntity(IntegerArgumentType.getInteger(c, "id"));
                    if (!allowed(p) || e == null || !UnleashedApi.isShyGuy(e) || e.distanceTo(p) > 24 || DYING.containsKey(e.getId())) return 0;
                    Dialogue.line(p, "You", "Die.", 0xD08CFF);
                    Dialogue.line(p, "", "SCP-096 goes still. Its skin greys and sloughs away where it stands.", 0);
                    Dialogue.clearOptions(p);
                    Dialogue.option(p, "Leave", "close", "Close", 0x9AA4B2);
                    Dialogue.send(p, false);
                    DYING.put(e.getId(), 60);
                    WorldNews.record(p.server, WorldNews.Kind.SHYGUY_KILLED, "096", "the Administrator spoke one word to SCP-096 and it rotted where it stood", 2, p.getGameProfile().getName());
                    if (e instanceof LivingEntity le) le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 9, false, false));
                    calm(null, e);
                    return 1;
                }))));
    }

    private static Set<String> calmFor(Entity e) {
        Set<String> out = new HashSet<>();
        for (Tag t : e.getPersistentData().getList(KEY, Tag.TAG_STRING)) out.add(t.getAsString());
        return out;
    }

    private static void calm(ServerPlayer p, Entity e) {
        ServerLevel l = (ServerLevel) e.level();
        UnleashedApi.call(UnleashedApi.method("scp096.Scp096ShyGuy", "forceCalm", ServerLevel.class), e, l);
        if (p == null) return;
        ListTag list = e.getPersistentData().getList(KEY, Tag.TAG_STRING);
        if (!calmFor(e).contains(p.getUUID().toString())) list.add(StringTag.valueOf(p.getUUID().toString()));
        e.getPersistentData().put(KEY, list);
        Dialogue.line(p, "You", "Calm down, buddy. It's just me.", 0xD08CFF);
        WorldNews.record(p.server, WorldNews.Kind.SHYGUY_CALMED, "096", "SCP-096 let the Administrator look at its face, and stayed calm", 4, p.getGameProfile().getName());
        Dialogue.line(p, "", "SCP-096 stops shaking. It lowers its hands. It will not fear your face again.", 0);
        Dialogue.clearOptions(p);
        Dialogue.option(p, "Leave it be", "close", "Close", 0x9AA4B2);
        Dialogue.send(p, false);
        l.sendParticles(ParticleTypes.HEART, e.getX(), e.getY() + e.getBbHeight() + 0.3, e.getZ(), 4, 0.3, 0.2, 0.3, 0);
        shield(e);
    }

    private static Field lastSeen;

    /** Tells SCP Unleashed this Shy Guy has "just seen" each calmed player, so their look starts nothing. */
    @SuppressWarnings("unchecked")
    private static void shield(Entity e) {
        try {
            if (lastSeen == null) {
                lastSeen = UnleashedApi.cls("scp096.Scp096ShyGuy").getDeclaredField("LastSeen");
                lastSeen.setAccessible(true);
            }
            Map<UUID, Integer> seen = (Map<UUID, Integer>) lastSeen.get(e);
            for (String s : calmFor(e)) seen.put(UUID.fromString(s), Integer.MAX_VALUE);
        } catch (Throwable ignored) {}
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        int t = event.getServer().getTickCount();
        // the dying: ash, smoke and rot, then nothing
        for (Iterator<Map.Entry<Integer, Integer>> it = DYING.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Integer> en = it.next();
            Entity e = null;
            for (ServerLevel l : event.getServer().getAllLevels()) if ((e = l.getEntity(en.getKey())) != null) break;
            if (e == null || !e.isAlive()) { it.remove(); continue; }
            ServerLevel l = (ServerLevel) e.level();
            int left = en.getValue() - 1;
            en.setValue(left);
            l.sendParticles(ParticleTypes.ASH, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), 12, 0.4, e.getBbHeight() * 0.4, 0.4, 0.02);
            l.sendParticles(ParticleTypes.SMOKE, e.getX(), e.getY() + 0.3, e.getZ(), 4, 0.4, 0.2, 0.4, 0.01);
            if (left % 10 == 0) l.playSound(null, e.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 1F, 0.5F);
            if (left <= 0) {
                l.sendParticles(ParticleTypes.LARGE_SMOKE, e.getX(), e.getY() + 1, e.getZ(), 40, 0.5, 1, 0.5, 0.03);
                l.sendParticles(ParticleTypes.SOUL, e.getX(), e.getY() + 1, e.getZ(), 15, 0.4, 0.8, 0.4, 0.04);
                l.playSound(null, e.blockPosition(), SoundEvents.ZOMBIE_VILLAGER_CURE, SoundSource.HOSTILE, 0.6F, 0.4F);
                e.kill();
                if (e.isAlive()) e.discard();
                it.remove();
            }
        }
        if (t % 10 != 0) return;
        // keep calmed Shy Guys calm toward the people who calmed them
        Method active = UnleashedApi.method("scp096.Scp096ShyGuy", "active");
        Object set = UnleashedApi.call(active, null);
        if (!(set instanceof Collection<?> guys)) return;
        Method forget = UnleashedApi.method("scp096.Scp096ShyGuy", "forget", UUID.class);
        Method count = UnleashedApi.method("scp096.Scp096ShyGuy", "targetCount");
        Method calmNow = UnleashedApi.method("scp096.Scp096ShyGuy", "isCalm");
        for (Object o : new ArrayList<>(guys)) {
            if (!(o instanceof Entity e) || e.level().isClientSide) continue;
            Set<String> friends = calmFor(e);
            if (friends.isEmpty()) continue;
            shield(e);
            for (String s : friends) UnleashedApi.call(forget, e, UUID.fromString(s));
            if (Integer.valueOf(0).equals(UnleashedApi.call(count, e)) && Boolean.FALSE.equals(UnleashedApi.call(calmNow, e)))
                UnleashedApi.call(UnleashedApi.method("scp096.Scp096ShyGuy", "forceCalm", ServerLevel.class), e, e.level());
        }
    }
}
