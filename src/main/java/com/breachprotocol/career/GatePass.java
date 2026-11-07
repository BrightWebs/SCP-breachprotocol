package com.breachprotocol.career;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.compat.UnleashedApi;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.*;

/**
 * The Ascendant can bring someone past the Gate Guardian. Right-click the Guardian to pick a player:
 * <ul>
 * <li><b>Escort</b> (one player at a time, one trip): while they stay within 16 blocks of you, the
 *     Guardian spares them, in and back out. Once they have been in and leave the Gate's reach, the
 *     pass is spent.</li>
 * <li><b>Full access</b>: the Guardian spares them whenever they come, alone or not, until revoked.</li>
 * </ul>
 * The Guardian spares whoever is invulnerable, so a guest is made invulnerable only while near the
 * Gate and under a pass, and never otherwise.
 */
@EventBusSubscriber(modid = BreachProtocol.MODID)
public final class GatePass {
    private GatePass() {}

    public static final String TAG = "bp_gate_pass";
    private static final double NEAR = 16, REACH = 96, INSIDE = 50, OUT = 80;

    private record Escort(UUID guest, boolean entered) {}

    /** Ascendant -> their current one-trip guest. */
    private static final Map<UUID, Escort> ESCORTS = new HashMap<>();

    public static class Data extends SavedData {
        final Set<String> full = new HashSet<>();
        /** Players banished from the Garden, and when they may be let back in (game time). */
        public final Map<String, Long> banished = new HashMap<>();

        public static final SavedData.Factory<Data> FACTORY = new SavedData.Factory<>(Data::new, Data::load, null);

        static Data load(CompoundTag tag, HolderLookup.Provider p) {
            Data d = new Data();
            for (Tag t : tag.getList("full", Tag.TAG_STRING)) d.full.add(t.getAsString());
            CompoundTag b = tag.getCompound("banished");
            for (String k : b.getAllKeys()) d.banished.put(k, b.getLong(k));
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider p) {
            ListTag l = new ListTag();
            for (String s : full) l.add(StringTag.valueOf(s));
            tag.put("full", l);
            CompoundTag b = new CompoundTag();
            banished.forEach(b::putLong);
            tag.put("banished", b);
            return tag;
        }
    }

    public static Data data(MinecraftServer s) {
        return s.overworld().getDataStorage().computeIfAbsent(Data.FACTORY, "breachprotocol_gate");
    }

    /** Banished for eating from the Tree of Knowledge (real time, so it lasts a real day). */
    public static boolean banished(ServerPlayer p) {
        Long until = data(p.server).banished.get(p.getUUID().toString());
        return until != null && System.currentTimeMillis() < until;
    }

    /** Cast out: every pass is gone, and the Guardian keeps them away until {@code untilMs}. */
    public static void banish(ServerPlayer p, long untilMs) {
        Data d = data(p.server);
        String id = p.getUUID().toString();
        d.banished.put(id, untilMs);
        d.full.remove(id);
        d.setDirty();
        ESCORTS.values().removeIf(e -> e.guest().equals(p.getUUID()));
        release(p);
    }

    /** Lifts a banishment early (command). */
    public static void pardon(ServerPlayer p) {
        data(p.server).banished.remove(p.getUUID().toString());
        data(p.server).setDirty();
    }

    /** The window shown when the Ascendant right-clicks the Guardian. */
    public static void menu(ServerPlayer p) { menu(p, -1); }

    public static void menu(ServerPlayer p, int guardian) {
        if (!com.breachprotocol.entity.Dialogue.active(p, "gate")) {
            com.breachprotocol.entity.Dialogue.begin(p, "gate", "SCP-001-1 · The Gate Guardian", "It waits for your word, Master", guardian);
            com.breachprotocol.entity.Dialogue.line(p, "", "The Guardian lowers its blades. Who may pass the Gate?", 0);
        }
        com.breachprotocol.entity.Dialogue.clearOptions(p);
        int shown = 0;
        for (ServerPlayer q : p.server.getPlayerList().getPlayers()) {
            if (q == p) continue;
            String n = q.getGameProfile().getName();
            boolean full = data(p.server).full.contains(q.getUUID().toString());
            com.breachprotocol.entity.Dialogue.option(p, "Escort " + n, "/gate escort " + n, "One trip in and out, while they stay with you", 0x7FE0FF);
            if (full) com.breachprotocol.entity.Dialogue.option(p, "Revoke " + n + "'s access", "/gate revoke " + n, "The Guardian will no longer spare them", 0xFF7070);
            else com.breachprotocol.entity.Dialogue.option(p, "Full access for " + n, "/gate grant " + n, "The Guardian spares them always", 0xFFC94D);
            shown++;
        }
        if (shown == 0) com.breachprotocol.entity.Dialogue.line(p, "", "Nobody else is online to bring through.", 0);
        Escort e = ESCORTS.get(p.getUUID());
        if (e != null) {
            ServerPlayer g = p.server.getPlayerList().getPlayer(e.guest());
            com.breachprotocol.entity.Dialogue.option(p, "End " + (g == null ? "the" : g.getGameProfile().getName() + "'s") + " escort", "/gate end", "Their pass ends now", 0xFF7070);
        }
        com.breachprotocol.entity.Dialogue.option(p, "That is all", "close", "Close", 0x9AA4B2);
        com.breachprotocol.entity.Dialogue.send(p, true);
    }

    private static void refresh(ServerPlayer p, String note) {
        if (!com.breachprotocol.entity.Dialogue.active(p, "gate")) return;
        com.breachprotocol.entity.Dialogue.line(p, "", note, 0);
        menu(p);
    }

    @SubscribeEvent
    public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("gate")
                .then(Commands.literal("escort").then(Commands.argument("player", StringArgumentType.word()).executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    ServerPlayer g = p.server.getPlayerList().getPlayerByName(StringArgumentType.getString(c, "player"));
                    if (!Career.isAscended(p) || g == null || g == p) return 0;
                    if (banished(g)) { p.sendSystemMessage(Component.literal(g.getGameProfile().getName() + " ate from the Tree of Knowledge. The Guardian will not let them back yet.").withStyle(ChatFormatting.RED)); return 0; }
                    Escort old = ESCORTS.put(p.getUUID(), new Escort(g.getUUID(), false));
                    if (old != null && !old.guest().equals(g.getUUID())) release(p.server.getPlayerList().getPlayer(old.guest()));
                    p.sendSystemMessage(Component.literal("You are escorting " + g.getGameProfile().getName() + ". Keep them within 16 blocks.").withStyle(ChatFormatting.LIGHT_PURPLE));
                    refresh(p, "You are escorting " + g.getGameProfile().getName() + ". Keep them within 16 blocks.");
                    g.sendSystemMessage(Component.literal(p.getGameProfile().getName() + " will take you past the Gate Guardian. Stay close to them, in and out. One trip.").withStyle(ChatFormatting.LIGHT_PURPLE));
                    return 1;
                })))
                .then(Commands.literal("grant").then(Commands.argument("player", StringArgumentType.word()).executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    ServerPlayer g = p.server.getPlayerList().getPlayerByName(StringArgumentType.getString(c, "player"));
                    if (!Career.isAscended(p) || g == null || g == p) return 0;
                    Data d = data(p.server);
                    d.full.add(g.getUUID().toString());
                    d.banished.remove(g.getUUID().toString());
                    d.setDirty();
                    p.sendSystemMessage(Component.literal(g.getGameProfile().getName() + " may pass the Gate freely.").withStyle(ChatFormatting.GOLD));
                    refresh(p, g.getGameProfile().getName() + " may pass the Gate freely.");
                    g.sendSystemMessage(Component.literal("The Ascendant has given you full access. The Gate Guardian will let you pass.").withStyle(ChatFormatting.GOLD));
                    return 1;
                })))
                .then(Commands.literal("revoke").then(Commands.argument("player", StringArgumentType.word()).executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    ServerPlayer g = p.server.getPlayerList().getPlayerByName(StringArgumentType.getString(c, "player"));
                    if (!Career.isAscended(p) || g == null) return 0;
                    data(p.server).full.remove(g.getUUID().toString());
                    data(p.server).setDirty();
                    release(g);
                    p.sendSystemMessage(Component.literal(g.getGameProfile().getName() + "'s access is revoked.").withStyle(ChatFormatting.RED));
                    refresh(p, g.getGameProfile().getName() + "'s access is revoked.");
                    return 1;
                })))
                .then(Commands.literal("end").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    Escort e = ESCORTS.remove(p.getUUID());
                    if (e != null) release(p.server.getPlayerList().getPlayer(e.guest()));
                    refresh(p, "The escort is over.");
                    return 1;
                })));
    }

    private static void release(ServerPlayer g) {
        if (g == null || !g.getTags().contains(TAG)) return;
        g.removeTag(TAG);
        if (!Career.isAscended(g)) g.setInvulnerable(false);
    }

    private static void protect(ServerPlayer g) {
        if (Career.isAscended(g)) return;
        g.addTag(TAG);
        if (!g.isInvulnerable()) g.setInvulnerable(true);
    }

    private static double guardianDistance(ServerPlayer g) {
        double best = Double.MAX_VALUE;
        for (Entity e : g.level().getEntities(g, g.getBoundingBox().inflate(REACH + 32), UnleashedApi::isGateGuardian)) {
            double dx = e.getX() - g.getX(), dz = e.getZ() - g.getZ();
            best = Math.min(best, Math.sqrt(dx * dx + dz * dz));
        }
        return best;
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        MinecraftServer s = event.getServer();
        if (s.getTickCount() % 10 != 0) return;
        Data d = data(s);
        Set<UUID> covered = new HashSet<>();
        for (Iterator<Map.Entry<UUID, Escort>> it = ESCORTS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Escort> en = it.next();
            ServerPlayer host = s.getPlayerList().getPlayer(en.getKey());
            ServerPlayer g = s.getPlayerList().getPlayer(en.getValue().guest());
            if (g == null) continue;
            double gd = guardianDistance(g);
            boolean close = host != null && host.level() == g.level() && host.distanceTo(g) <= NEAR && Career.isAscended(host);
            if (gd <= REACH && close) { protect(g); covered.add(g.getUUID()); }
            boolean entered = en.getValue().entered() || gd <= INSIDE;
            if (entered != en.getValue().entered()) en.setValue(new Escort(g.getUUID(), true));
            if (gd <= REACH && !close && g.getTags().contains(TAG) && s.getTickCount() % 40 == 0)
                g.displayClientMessage(Component.literal("Stay close to the Ascendant!").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
            if (entered && gd > OUT) {
                it.remove();
                release(g);
                g.sendSystemMessage(Component.literal("You are back past the Gate. Your pass is spent.").withStyle(ChatFormatting.GRAY));
                if (host != null) host.sendSystemMessage(Component.literal(g.getGameProfile().getName() + "'s trip is over.").withStyle(ChatFormatting.GRAY));
            }
        }
        for (ServerPlayer g : s.getPlayerList().getPlayers()) {
            if (banished(g) && !Career.isAscended(g)) {
                release(g);
                // the Guardian pushes the banished away from the Gate
                for (Entity e : g.level().getEntities(g, g.getBoundingBox().inflate(70), UnleashedApi::isGateGuardian)) {
                    net.minecraft.world.phys.Vec3 away = g.position().subtract(e.position()).multiply(1, 0, 1).normalize().scale(1.4).add(0, 0.45, 0);
                    g.setDeltaMovement(away);
                    g.hurtMarked = true;
                    if (s.getTickCount() % 60 == 0)
                        g.displayClientMessage(Component.literal("The Guardian will not let you return.").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
                    break;
                }
                continue;
            }
            if (covered.contains(g.getUUID())) continue;
            boolean full = d.full.contains(g.getUUID().toString()) && !banished(g);
            if (full && guardianDistance(g) <= REACH) protect(g);
            else release(g);
        }
    }
}
