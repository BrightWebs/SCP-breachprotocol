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

    public static boolean banished(ServerPlayer p) {
        Long until = data(p.server).banished.get(p.getUUID().toString());
        return until != null && p.server.overworld().getGameTime() < until;
    }

    /** The choices shown when the Ascendant right-clicks the Guardian. */
    public static void menu(ServerPlayer p) {
        MutableComponent c = Component.literal("Bring someone through the Gate:").withStyle(ChatFormatting.LIGHT_PURPLE);
        p.sendSystemMessage(c);
        int shown = 0;
        for (ServerPlayer q : p.server.getPlayerList().getPlayers()) {
            if (q == p) continue;
            String n = q.getGameProfile().getName();
            boolean full = data(p.server).full.contains(q.getUUID().toString());
            MutableComponent row = Component.literal("  " + n + "  ").withStyle(ChatFormatting.WHITE)
                    .append(button("Escort", "/gate escort " + n, "One trip in and out, while they stay with you", ChatFormatting.AQUA)).append(" ")
                    .append(full ? button("Revoke access", "/gate revoke " + n, "The Guardian will no longer spare them", ChatFormatting.RED)
                            : button("Full access", "/gate grant " + n, "The Guardian spares them always", ChatFormatting.GOLD));
            p.sendSystemMessage(row);
            shown++;
        }
        if (shown == 0) p.sendSystemMessage(Component.literal("  Nobody else is online.").withStyle(ChatFormatting.DARK_GRAY));
        Escort e = ESCORTS.get(p.getUUID());
        if (e != null) {
            ServerPlayer g = p.server.getPlayerList().getPlayer(e.guest());
            p.sendSystemMessage(Component.literal("  Escorting: " + (g == null ? "(offline)" : g.getGameProfile().getName()) + "  ").withStyle(ChatFormatting.GRAY)
                    .append(button("End escort", "/gate end", "Their pass ends now", ChatFormatting.RED)));
        }
    }

    private static MutableComponent button(String label, String cmd, String hover, ChatFormatting colour) {
        return Component.literal("[" + label + "]").withStyle(s -> s.withColor(colour).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(hover))));
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
                    return 1;
                })))
                .then(Commands.literal("end").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    Escort e = ESCORTS.remove(p.getUUID());
                    if (e != null) release(p.server.getPlayerList().getPlayer(e.guest()));
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
            if (covered.contains(g.getUUID())) continue;
            boolean full = d.full.contains(g.getUUID().toString()) && !banished(g);
            if (full && guardianDistance(g) <= REACH) protect(g);
            else release(g);
        }
    }
}
