package com.breachprotocol.career;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.common.util.INBTSerializable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Everything the Foundation knows about one player. Saved with the player, kept through death. */
public class CareerData implements INBTSerializable<CompoundTag> {
    public static final int MAX_MESSAGES = 40;

    public static class Message {
        public String sender, body;
        public long time;
        public boolean read;
    }

    /** A pending phone call: a decision the caller wants from you. */
    public static class Call {
        public String templateId = "";
        public int caller;
        public long expires;
    }

    public boolean recruited;
    public int rank;
    public int xp;
    public boolean ascended;
    public int stability = 70;
    public final List<Message> messages = new ArrayList<>();
    public final List<Mission> missions = new ArrayList<>();
    public Call call;
    public final Map<String, Long> cooldowns = new HashMap<>();
    public long nextMission, nextCall, nextFlavor, nextStabilityEvent;
    public boolean o5OfferPending;
    public int ritualTicks;
    // return point when visiting Site-01
    public boolean hasReturn;
    public String returnDim = "minecraft:overworld";
    public double returnX, returnY, returnZ;

    public transient boolean dirty = true;

    public Rank getRank() {
        return Rank.byId(rank);
    }

    public void setRank(Rank r) {
        rank = r.ordinal();
        dirty = true;
    }

    public void addMessage(String sender, String body, long time) {
        Message m = new Message();
        m.sender = sender;
        m.body = body;
        m.time = time;
        messages.add(0, m);
        while (messages.size() > MAX_MESSAGES) messages.remove(messages.size() - 1);
        dirty = true;
    }

    public int unread() {
        int n = 0;
        for (Message m : messages) if (!m.read) n++;
        return n;
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        return save();
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag t) {
        load(t);
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putBoolean("recruited", recruited);
        t.putInt("rank", rank);
        t.putInt("xp", xp);
        t.putBoolean("ascended", ascended);
        t.putInt("stability", stability);
        ListTag msgs = new ListTag();
        for (Message m : messages) {
            CompoundTag mt = new CompoundTag();
            mt.putString("s", m.sender);
            mt.putString("b", m.body);
            mt.putLong("t", m.time);
            mt.putBoolean("r", m.read);
            msgs.add(mt);
        }
        t.put("messages", msgs);
        ListTag ms = new ListTag();
        for (Mission m : missions) ms.add(m.save());
        t.put("missions", ms);
        if (call != null) {
            CompoundTag c = new CompoundTag();
            c.putString("id", call.templateId);
            c.putInt("caller", call.caller);
            c.putLong("expires", call.expires);
            t.put("call", c);
        }
        CompoundTag cds = new CompoundTag();
        cooldowns.forEach(cds::putLong);
        t.put("cooldowns", cds);
        t.putLong("nextMission", nextMission);
        t.putLong("nextCall", nextCall);
        t.putLong("nextFlavor", nextFlavor);
        t.putLong("nextStab", nextStabilityEvent);
        t.putBoolean("o5Offer", o5OfferPending);
        t.putBoolean("hasReturn", hasReturn);
        t.putString("retDim", returnDim);
        t.putDouble("retX", returnX);
        t.putDouble("retY", returnY);
        t.putDouble("retZ", returnZ);
        return t;
    }

    public void load(CompoundTag t) {
        recruited = t.getBoolean("recruited");
        rank = t.getInt("rank");
        xp = t.getInt("xp");
        ascended = t.getBoolean("ascended");
        stability = t.contains("stability") ? t.getInt("stability") : 70;
        messages.clear();
        for (Tag e : t.getList("messages", Tag.TAG_COMPOUND)) {
            CompoundTag mt = (CompoundTag) e;
            Message m = new Message();
            m.sender = mt.getString("s");
            m.body = mt.getString("b");
            m.time = mt.getLong("t");
            m.read = mt.getBoolean("r");
            messages.add(m);
        }
        missions.clear();
        for (Tag e : t.getList("missions", Tag.TAG_COMPOUND)) missions.add(Mission.load((CompoundTag) e));
        call = null;
        if (t.contains("call")) {
            CompoundTag c = t.getCompound("call");
            call = new Call();
            call.templateId = c.getString("id");
            call.caller = c.getInt("caller");
            call.expires = c.getLong("expires");
        }
        cooldowns.clear();
        CompoundTag cds = t.getCompound("cooldowns");
        for (String k : cds.getAllKeys()) cooldowns.put(k, cds.getLong(k));
        nextMission = t.getLong("nextMission");
        nextCall = t.getLong("nextCall");
        nextFlavor = t.getLong("nextFlavor");
        nextStabilityEvent = t.getLong("nextStab");
        o5OfferPending = t.getBoolean("o5Offer");
        hasReturn = t.getBoolean("hasReturn");
        returnDim = t.getString("retDim");
        returnX = t.getDouble("retX");
        returnY = t.getDouble("retY");
        returnZ = t.getDouble("retZ");
        dirty = true;
    }
}
