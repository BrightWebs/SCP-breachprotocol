package com.breachprotocol.career;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.compat.Unleashed;
import com.breachprotocol.compat.UnleashedApi;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.lang.reflect.Method;

/**
 * The Administrator's override. What an anomaly does that nobody else can undo, the Administrator
 * (and SCP-Ω) can: crouch and use an empty hand on it.
 *
 * <ul>
 * <li><b>Anomalous objects</b>, each with SCP Unleashed's own way of ending it: SCP-1762's box calls
 *     its paper dragons home and shuts; SCP-019's pot and SCP-244's vase close; SCP-076's coffin lid
 *     goes down; SCP-256's typewriter is wiped; SCP-098's hive caves in; SCP-020's mould dies on the
 *     spot you touch. Anything else that has a lid or a door left open is shut.</li>
 * <li><b>Anomalous creatures</b>: calmed by their own means where they have one (SCP-529, SCP-860's
 *     watchers, SCP-019's drones, SCP-001's jitters), SCP-939's lure ends, SCP-343 stays.</li>
 * <li><b>People</b>: anyone touched is cured of every anomalous affliction at once, through SCP
 *     Unleashed's own cure (the same one SCP-500 uses).</li>
 * </ul>
 *
 * The Orders tab of the console also has the world-wide ones: end SCP-107's storm, clear SCP-020's
 * mould around you, cure everyone near you.
 */
@EventBusSubscriber(modid = BreachProtocol.MODID)
public final class AdminOverride {
    private AdminOverride() {}

    static boolean chief(ServerPlayer p) { return Career.rank(p) == Rank.ADMINISTRATOR || Career.isAscended(p); }

    private static void say(ServerPlayer p, String s) { p.displayClientMessage(Component.literal(s).withStyle(ChatFormatting.LIGHT_PURPLE), true); }

    private static Method method(Object o, String name, Class<?>... args) {
        for (Class<?> c = o.getClass(); c != null && c != Object.class; c = c.getSuperclass())
            try { Method m = c.getDeclaredMethod(name, args); m.setAccessible(true); return m; } catch (NoSuchMethodException ignored) {}
        return null;
    }

    private static boolean call(Object o, String name, Class<?>[] types, Object... args) {
        Method m = method(o, name, types);
        if (m == null) return false;
        try { m.invoke(o, args); return true; } catch (Throwable t) { BreachProtocol.LOGGER.warn("Override {} failed: {}", name, t.toString()); return false; }
    }

    private static String cls(Object o) { return o.getClass().getSimpleName(); }

    // ================================================================== objects

    @SubscribeEvent
    public static void onBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !p.isShiftKeyDown() || event.getHand() != InteractionHand.MAIN_HAND
                || !p.getMainHandItem().isEmpty() || !chief(p)) return;
        ServerLevel l = p.serverLevel();
        BlockPos pos = event.getPos();
        BlockState st = l.getBlockState(pos);
        BlockEntity be = l.getBlockEntity(pos);
        String done = null;
        if (be != null) {
            switch (cls(be)) {
                case "Scp1762BoxBlockEntity" -> { if (call(be, "recall", new Class[]{ServerLevel.class}, l)) done = "The paper dragons fold themselves back into the box. The lid comes down."; }
                case "Scp019PotBlockEntity" -> { if (call(be, "close", new Class[0])) done = "The pot's lid seals. Whatever was crawling out of it is gone back in."; }
                case "Scp244VaseBlockEntity" -> { if (call(be, "close", new Class[0])) done = "The vase closes. The cold stops pouring out."; }
                case "Scp076CoffinBlockEntity" -> { if (call(be, "closeLid", new Class[0])) done = "The stone lid grinds shut."; }
                case "Scp256TypewriterBlockEntity" -> { if (call(be, "clear", new Class[0])) done = "The typewriter's page goes blank."; }
                case "Scp098HiveBlockEntity" -> { if (call(be, "collapse", new Class[]{ServerLevel.class}, l)) done = "The hive caves in on itself."; }
                default -> {}
            }
        }
        // SCP-020's mould, on the spot you touch
        if (done == null) {
            Method clear = UnleashedApi.method("scp020.Scp020Infection", "clear", ServerLevel.class, BlockPos.class);
            if (clear != null) {
                int n = 0;
                for (BlockPos q : BlockPos.betweenClosed(pos.offset(-2, -2, -2), pos.offset(2, 2, 2))) {
                    Object r = UnleashedApi.call(clear, null, l, q.immutable());
                    if (r instanceof Integer i) n += i;
                }
                if (n > 0) done = "The mould withers and flakes away.";
            }
        }
        // anything anomalous left open
        if (done == null && Unleashed.MODID.equals(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(st.getBlock()).getNamespace())) {
            for (Property<?> pr : st.getProperties())
                if (pr instanceof BooleanProperty b && pr.getName().equals("open") && st.getValue(b)) {
                    l.setBlock(pos, st.setValue(b, false), 3);
                    done = "Closed.";
                }
        }
        if (done == null) return;
        l.playSound(null, pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.8F, 1.4F);
        l.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 16, 0.3, 0.3, 0.3, 0.02);
        say(p, done);
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    // ================================================================== creatures and people

    @SubscribeEvent
    public static void onEntity(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !p.isShiftKeyDown() || event.getHand() != InteractionHand.MAIN_HAND
                || !p.getMainHandItem().isEmpty() || !chief(p)) return;
        Entity e = event.getTarget();
        String done = null;
        switch (cls(e)) {
            case "Scp529Cat", "Scp860Watcher", "Scp019Drone", "Scp001Jitter" -> { if (call(e, "calm", new Class[0])) done = "It settles, quiet."; }
            case "Scp939" -> { if (call(e, "endLure", new Class[]{boolean.class}, false)) done = "The voices it was borrowing stop."; }
            case "Scp343God" -> { if (call(e, "cancelLeaving", new Class[0])) done = "\"Very well. I'll stay a while.\""; }
            case "Scp280Eyes" -> { if (call(e, "close", new Class[]{boolean.class}, true)) done = "The eyes in the dark close."; }
            default -> {}
        }
        if (done == null && e instanceof LivingEntity le && cure(le, p)) done = le.getName().getString() + " is clear of anything anomalous.";
        if (done == null) return;
        ServerLevel l = p.serverLevel();
        l.playSound(null, e.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1F, 1.2F);
        l.sendParticles(ParticleTypes.END_ROD, e.getX(), e.getY() + e.getBbHeight() / 2, e.getZ(), 16, 0.3, 0.4, 0.3, 0.02);
        say(p, done);
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    /** SCP Unleashed's own cure: every anomalous affliction it knows, lifted. */
    static boolean cure(LivingEntity who, Entity by) {
        Method m = UnleashedApi.method("anomaly.AnomalyCure", "cure", LivingEntity.class, Entity.class);
        return m != null && UnleashedApi.call(m, null, who, by) instanceof Boolean b && b;
    }

    // ================================================================== world-wide, from the console

    /** "ovr:storm", "ovr:mould", "ovr:cure". */
    public static void action(ServerPlayer p, String a) {
        if (!chief(p)) return;
        ServerLevel l = p.serverLevel();
        switch (a) {
            case "storm" -> {
                Method m = UnleashedApi.method("scp107.Scp107Storm", "end", net.minecraft.server.MinecraftServer.class, boolean.class);
                say(p, m != null && UnleashedApi.call(m, null, p.server, true) instanceof Boolean ok && ok ? "The anomalous rain stops." : "There's no anomalous storm to stop.");
            }
            case "mould" -> {
                Method m = UnleashedApi.method("scp020.Scp020Infection", "clear", ServerLevel.class, BlockPos.class);
                int n = 0;
                if (m != null) for (BlockPos q : BlockPos.betweenClosed(p.blockPosition().offset(-16, -8, -16), p.blockPosition().offset(16, 8, 16)))
                    if (UnleashedApi.call(m, null, l, q.immutable()) instanceof Integer i) n += i;
                say(p, n > 0 ? "SCP-020's mould dies back for sixteen blocks around you." : "No mould here.");
            }
            case "cure" -> {
                int n = 0;
                for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new AABB(p.blockPosition()).inflate(32), e -> !Unleashed.isAnomaly(e)))
                    if (cure(e, p)) n++;
                say(p, n == 0 ? "Nobody near you is afflicted." : n + " cured of everything anomalous.");
            }
            default -> {}
        }
    }
}
