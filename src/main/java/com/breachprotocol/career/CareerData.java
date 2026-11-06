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
        /** Sent by this player (player-to-player texts). */
        public boolean out;
    }

    /** Another player whose number you have, or who asked for yours. */
    public static class Contact {
        public String uuid = "", name = "";
        public long time;

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putString("u", uuid);
            t.putString("n", name);
            t.putLong("t", time);
            return t;
        }

        static Contact load(CompoundTag t) {
            Contact c = new Contact();
            c.uuid = t.getString("u");
            c.name = t.getString("n");
            c.time = t.getLong("t");
            return c;
        }
    }

    public final List<Contact> contacts = new ArrayList<>();
    public final List<Contact> requests = new ArrayList<>();

    /** A pending phone call: a decision the caller wants from you. */
    public static class Call {
        public String templateId = "";
        public int caller;
        public long expires;
    }

    /** A photo taken with the phone camera. */
    public static class Photo {
        public int id;
        /** Picture id (breachprotocol_photos/<img>.png), 0 = none. */
        public int img;
        public long time;
        public String subjects = "";
        public boolean hazard;
        public String hazardEntity = "";
        public int x, z;
        public String author = "";

        public CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putInt("id", id);
            t.putInt("img", img);
            t.putLong("time", time);
            t.putString("subjects", subjects);
            t.putBoolean("hazard", hazard);
            t.putString("hazardEntity", hazardEntity);
            t.putInt("x", x);
            t.putInt("z", z);
            t.putString("author", author);
            return t;
        }

        public static Photo load(CompoundTag t) {
            Photo p = new Photo();
            p.id = t.getInt("id");
            p.img = t.getInt("img");
            p.time = t.getLong("time");
            p.subjects = t.getString("subjects");
            p.hazard = t.getBoolean("hazard");
            p.hazardEntity = t.getString("hazardEntity");
            p.x = t.getInt("x");
            p.z = t.getInt("z");
            p.author = t.getString("author");
            return p;
        }
    }

    public static final int MAX_PHOTOS = 24;
    public final List<Photo> photos = new ArrayList<>();
    public int nextPhotoId = 1;

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
    public int testCounter;
    /** Next time the Council may add an assignment about an anomaly loose near you. */
    public long nextContext;

    public String nextTestNumber() {
        dirty = true;
        return "T-" + (1100 + testCounter++);
    }
    /** Rifts this player's phone has already alerted them about (entity ids, this session). */
    public final transient java.util.Set<Integer> seenRifts = new java.util.HashSet<>();

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
        ListTag ph = new ListTag();
        for (Photo p : photos) ph.add(p.save());
        t.put("photos", ph);
        t.putInt("nextPhoto", nextPhotoId);
        t.putInt("rank", rank);
        t.putInt("xp", xp);
        t.putBoolean("commendations", true);
        t.putBoolean("ascended", ascended);
        t.putInt("stability", stability);
        t.putInt("tests", testCounter);
        ListTag msgs = new ListTag();
        for (Message m : messages) {
            CompoundTag mt = new CompoundTag();
            mt.putString("s", m.sender);
            mt.putString("b", m.body);
            mt.putLong("t", m.time);
            mt.putBoolean("r", m.read);
            if (m.out) mt.putBoolean("o", true);
            msgs.add(mt);
        }
        t.put("messages", msgs);
        ListTag cs = new ListTag();
        for (Contact c : contacts) cs.add(c.save());
        t.put("contacts", cs);
        ListTag rq = new ListTag();
        for (Contact c : requests) rq.add(c.save());
        t.put("requests", rq);
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
        photos.clear();
        for (Tag e : t.getList("photos", Tag.TAG_COMPOUND)) photos.add(Photo.load((CompoundTag) e));
        nextPhotoId = Math.max(1, t.getInt("nextPhoto"));
        rank = t.getInt("rank");
        xp = t.getInt("xp");
        if (!t.getBoolean("commendations")) xp = Rank.values()[Math.max(0, Math.min(Rank.values().length - 1, t.getInt("rank")))].xpRequired; // old XP saves
        ascended = t.getBoolean("ascended");
        stability = t.contains("stability") ? t.getInt("stability") : 70;
        testCounter = t.getInt("tests");
        messages.clear();
        for (Tag e : t.getList("messages", Tag.TAG_COMPOUND)) {
            CompoundTag mt = (CompoundTag) e;
            Message m = new Message();
            m.sender = mt.getString("s");
            m.body = mt.getString("b");
            m.time = mt.getLong("t");
            m.read = mt.getBoolean("r");
            m.out = mt.getBoolean("o");
            messages.add(m);
        }
        contacts.clear();
        for (Tag e : t.getList("contacts", Tag.TAG_COMPOUND)) contacts.add(Contact.load((CompoundTag) e));
        requests.clear();
        for (Tag e : t.getList("requests", Tag.TAG_COMPOUND)) requests.add(Contact.load((CompoundTag) e));
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
