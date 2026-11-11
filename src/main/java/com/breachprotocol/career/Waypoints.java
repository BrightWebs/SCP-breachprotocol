package com.breachprotocol.career;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Waypoints, from the phone's Map: saved where you stand or wherever you tap on the map, named, renamed,
 * guided to, and (for the Administrator, SCP-Omega and operators) travelled to in an instant, across worlds.
 */
public final class Waypoints {
    private Waypoints() {}

    private static final String KEY = "bp_wp";
    private static final int MAX = 64;

    private static ListTag list(ServerPlayer p) { return p.getPersistentData().getList(KEY, Tag.TAG_COMPOUND); }

    public static boolean mayTravel(ServerPlayer p) { return Vault.chief(p) || p.hasPermissions(2); }

    private static String clean(String n) {
        n = n.replaceAll("[\\p{Cntrl}]", "").trim();
        return n.length() > 32 ? n.substring(0, 32) : n;
    }

    private static void add(ServerPlayer p, String name, String dim, int x, int y, int z) {
        ListTag l = list(p);
        if (l.size() >= MAX) { say(p, "Your map holds " + MAX + " waypoints. Delete one first.", ChatFormatting.RED); return; }
        CompoundTag w = new CompoundTag();
        w.putString("n", clean(name).isEmpty() ? "Waypoint " + (l.size() + 1) : clean(name));
        w.putString("d", dim);
        w.putInt("x", x); w.putInt("y", y); w.putInt("z", z);
        w.putInt("c", COLOURS[l.size() % COLOURS.length]);
        l.add(w);
        p.getPersistentData().put(KEY, l);
        p.playNotifySound(SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.PLAYERS, 0.6F, 1.2F);
        say(p, "Waypoint saved: " + w.getString("n"), ChatFormatting.GREEN);
        Career.sync(p);
    }

    private static final int[] COLOURS = {0x5BE36F, 0x7FD6FF, 0xFFC85A, 0xFF7A7A, 0xC890FF, 0x7FFFD4, 0xFFA0D0, 0xE0E0E0};

    /** "here:name", "at:x:z:name", "del:i", "name:i:new name", "tp:i". */
    public static void action(ServerPlayer p, String a) {
        try {
            if (a.startsWith("here:")) { add(p, a.substring(5), p.level().dimension().location().toString(), p.getBlockX(), p.getBlockY(), p.getBlockZ()); return; }
            if (a.startsWith("at:")) {
                String[] s = a.split(":", 4);
                int x = Integer.parseInt(s[1]), z = Integer.parseInt(s[2]);
                ServerLevel l = p.serverLevel();
                int y = l.hasChunk(x >> 4, z >> 4) ? l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) : p.getBlockY();
                add(p, s.length > 3 ? s[3] : "", l.dimension().location().toString(), x, y, z);
                return;
            }
            String[] s = a.split(":", 3);
            int i = Integer.parseInt(s[1]);
            ListTag l = list(p);
            if (i < 0 || i >= l.size()) return;
            CompoundTag w = l.getCompound(i);
            switch (s[0]) {
                case "del" -> { l.remove(i); p.getPersistentData().put(KEY, l); say(p, "Waypoint deleted: " + w.getString("n"), ChatFormatting.GRAY); }
                case "name" -> { if (s.length > 2 && !clean(s[2]).isEmpty()) { w.putString("n", clean(s[2])); p.getPersistentData().put(KEY, l); } }
                case "tp" -> travel(p, w);
                default -> {}
            }
            Career.sync(p);
        } catch (RuntimeException ignored) {}
    }

    private static void travel(ServerPlayer p, CompoundTag w) {
        if (!mayTravel(p)) { say(p, "Only the Administrator travels this way. Use Guide.", ChatFormatting.GRAY); return; }
        ResourceLocation rl = ResourceLocation.tryParse(w.getString("d"));
        ServerLevel to = rl == null ? null : p.server.getLevel(ResourceKey.create(Registries.DIMENSION, rl));
        if (to == null) { say(p, "That world isn't there any more.", ChatFormatting.RED); return; }
        int x = w.getInt("x"), y = w.getInt("y"), z = w.getInt("z");
        // somewhere to stand: up from the saved height until there's room
        BlockPos at = new BlockPos(x, y, z);
        to.getChunk(x >> 4, z >> 4);
        for (int k = 0; k < 32 && !(to.getBlockState(at).isAir() && to.getBlockState(at.above()).isAir()); k++) at = at.above();
        ServerLevel from = p.serverLevel();
        from.sendParticles(ParticleTypes.REVERSE_PORTAL, p.getX(), p.getY() + 1, p.getZ(), 40, 0.4, 0.8, 0.4, 0.1);
        from.playSound(null, p.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.7F, 0.8F);
        p.teleportTo(to, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, p.getYRot(), p.getXRot());
        p.fallDistance = 0;
        to.sendParticles(ParticleTypes.PORTAL, at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, 50, 0.4, 0.8, 0.4, 0.4);
        to.playSound(null, at, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.7F, 1.2F);
        say(p, "You're at " + w.getString("n") + ".", ChatFormatting.LIGHT_PURPLE);
    }

    public static void addToSync(ServerPlayer p, CompoundTag t) {
        t.put("waypoints", list(p).copy());
        t.putBoolean("wpTravel", mayTravel(p));
        t.putString("dimHere", p.level().dimension().location().toString());
    }

    private static void say(ServerPlayer p, String s, ChatFormatting c) { p.displayClientMessage(Component.literal(s).withStyle(c), true); }
}
