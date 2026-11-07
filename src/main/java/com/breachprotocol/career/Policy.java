package com.breachprotocol.career;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

/**
 * Standing rules the Council has decided, which the rest of the mod obeys: who closes rifts, how
 * the crews are equipped, safety protocols, lockdowns, truces.
 */
public final class Policy {
    private Policy() {}

    public static final class Data extends SavedData {
        public boolean riftMtfOnly, crewGear;
        public long safetyUntil, lockdownUntil;
        public final Map<String, Long> truce = new HashMap<>();
        public static final SavedData.Factory<Data> FACTORY = new SavedData.Factory<>(Data::new, Data::load, null);

        static Data load(CompoundTag t, HolderLookup.Provider p) {
            Data d = new Data();
            d.riftMtfOnly = t.getBoolean("mtfOnly");
            d.crewGear = t.getBoolean("gear");
            d.safetyUntil = t.getLong("safety");
            d.lockdownUntil = t.getLong("lockdown");
            CompoundTag tr = t.getCompound("truce");
            for (String k : tr.getAllKeys()) d.truce.put(k, tr.getLong(k));
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag t, HolderLookup.Provider p) {
            t.putBoolean("mtfOnly", riftMtfOnly);
            t.putBoolean("gear", crewGear);
            t.putLong("safety", safetyUntil);
            t.putLong("lockdown", lockdownUntil);
            CompoundTag tr = new CompoundTag();
            truce.forEach(tr::putLong);
            t.put("truce", tr);
            return t;
        }
    }

    public static Data data(MinecraftServer s) { return s.overworld().getDataStorage().computeIfAbsent(Data.FACTORY, "breachprotocol_policy"); }

    private static long now(MinecraftServer s) { return s.overworld().getGameTime(); }

    public static boolean safety(MinecraftServer s) { return s != null && now(s) < data(s).safetyUntil; }

    public static boolean lockdown(MinecraftServer s) { return s != null && now(s) < data(s).lockdownUntil; }

    public static boolean truce(MinecraftServer s, String org) { Long u = data(s).truce.get(org); return u != null && now(s) < u; }

    public static void set(MinecraftServer s, java.util.function.Consumer<Data> f) { Data d = data(s); f.accept(d); d.setDirty(); }

    public static long days(MinecraftServer s, int n) { return now(s) + 24000L * n; }
}
