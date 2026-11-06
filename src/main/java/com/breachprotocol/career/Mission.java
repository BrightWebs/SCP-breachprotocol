package com.breachprotocol.career;

import net.minecraft.nbt.CompoundTag;

/** One assignment from the Council. */
public class Mission {
    /**
     * EXPOSURE: Class-D testing, stay near a live anomaly and survive. DOCUMENT: photograph it for
     * its file. FILES: recover Anomaly Dossiers from an Archive Terminal and hand them in.
     */
    public enum Type { RECOVER, TERMINATE, CARE, SUPPLY, FOUNDERS_TRIAL, EXPOSURE, DOCUMENT, FILES }

    public Type type;
    public String description = "";
    public int giver;          // O5 seat
    public int target = 1;
    public int progress = 0;
    public int xp;
    public long expires;       // game time
    public int x, y, z;        // point of interest
    public boolean hasPos;
    public String designation = ""; // RECOVER
    public String item = "";        // SUPPLY: item id

    public boolean done() {
        return progress >= target;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("type", type.name());
        t.putString("desc", description);
        t.putInt("giver", giver);
        t.putInt("target", target);
        t.putInt("progress", progress);
        t.putInt("xp", xp);
        t.putLong("expires", expires);
        t.putBoolean("hasPos", hasPos);
        t.putInt("x", x);
        t.putInt("y", y);
        t.putInt("z", z);
        t.putString("designation", designation);
        t.putString("item", item);
        return t;
    }

    public static Mission load(CompoundTag t) {
        Mission m = new Mission();
        try {
            m.type = Type.valueOf(t.getString("type"));
        } catch (IllegalArgumentException e) {
            m.type = Type.SUPPLY;
        }
        m.description = t.getString("desc");
        m.giver = t.getInt("giver");
        m.target = Math.max(1, t.getInt("target"));
        m.progress = t.getInt("progress");
        m.xp = t.getInt("xp");
        m.expires = t.getLong("expires");
        m.hasPos = t.getBoolean("hasPos");
        m.x = t.getInt("x");
        m.y = t.getInt("y");
        m.z = t.getInt("z");
        m.designation = t.getString("designation");
        m.item = t.getString("item");
        return m;
    }
}
