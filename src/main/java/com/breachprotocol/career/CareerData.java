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
        /** Sent by this player (player-to-player texts, and your answers to orders). */
        public boolean out;
        /** Messages that ask for a decision: the orders you can give in reply ({label, action, answer}). */
        public int id;
        public final java.util.List<String[]> replies = new java.util.ArrayList<>();
        /** Which order you gave (-1: not answered yet). */
        public int answered = -1;
    }

    public int nextMessageId = 1;

    public Message findMessage(int id) {
        for (Message m : messages) if (m.id == id) return m;
        return null;
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
        /** You dialled them: it rings until {@link #pickupAt}. */
        public boolean outgoing;
        public long pickupAt;
    }

    /** A meeting on the calendar (or a request for one). {@code at} is in world day-time. */
    public static class Appt {
        public int id, seat;
        public String topic = "";
        public long at;
        public boolean confirmed;
        /** The weekly Council session: the secretary has texted that you're late. */
        public boolean texted;

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putInt("id", id);
            t.putInt("seat", seat);
            t.putString("topic", topic);
            t.putLong("at", at);
            t.putBoolean("ok", confirmed);
            t.putBoolean("tx", texted);
            Calls.Template tp = Calls.get(topic);
            t.putString("about", seat == 0 ? "Weekly O5 Council session, Site-01" : tp != null ? Calls.title(tp) : "Council business");
            return t;
        }

        static Appt load(CompoundTag t) {
            Appt a = new Appt();
            a.id = t.getInt("id");
            a.seat = t.getInt("seat");
            a.topic = t.getString("topic");
            a.at = t.getLong("at");
            a.confirmed = t.getBoolean("ok");
            a.texted = t.getBoolean("tx");
            return a;
        }
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
    /** Anomalies this player has put down while they were loose; the Altar's trial (0 none, 1 sworn) and its count since swearing. */
    public int anomalyKills, trial, trialKills;
    /** Welcomed to the Council table (the first session after taking a chair). */
    public boolean inducted;
    public int stability = 70;
    public final List<Message> messages = new ArrayList<>();
    public final List<Mission> missions = new ArrayList<>();
    public Call call;
    public final List<Appt> appts = new ArrayList<>();
    public int nextApptId = 1;
    public long nextRequest;
    /** The weekly Council session: weekday (0 = Monday, so Tuesday = 1) and hour; a week to skip. */
    public int weeklyDow = 1, weeklyHour = 10;
    /** Items the Overseers added for the next session; when each topic was last debated (day); when the last session was (game time). */
    public final List<String> agenda = new ArrayList<>();
    public final Map<String, Long> agendaUsed = new HashMap<>();
    public long lastSession;
    public long skipWeek = -1;
    /** How the last call ended (shown on the phone, not as a message). */
    public String lastCallFrom = "", lastCallText = "";
    public long lastCallAt;
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
    public boolean builtTower;
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

    public Message addMessage(String sender, String body, long time) {
        Message m = new Message();
        m.id = nextMessageId++;
        m.sender = sender;
        m.body = body;
        m.time = time;
        messages.add(0, m);
        while (messages.size() > MAX_MESSAGES) messages.remove(messages.size() - 1);
        dirty = true;
        return m;
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
        t.putInt("anomalyKills", anomalyKills);
        t.putInt("trial", trial);
        t.putInt("trialKills", trialKills);
        t.putBoolean("inducted", inducted);
        t.putInt("stability", stability);
        t.putInt("tests", testCounter);
        t.putBoolean("tower", builtTower);
        ListTag msgs = new ListTag();
        for (Message m : messages) {
            CompoundTag mt = new CompoundTag();
            mt.putString("s", m.sender);
            mt.putString("b", m.body);
            mt.putLong("t", m.time);
            mt.putBoolean("r", m.read);
            if (m.out) mt.putBoolean("o", true);
            mt.putInt("id", m.id);
            if (!m.replies.isEmpty()) {
                ListTag rp = new ListTag();
                for (String[] r : m.replies) {
                    CompoundTag rt = new CompoundTag();
                    rt.putString("l", r[0]); rt.putString("a", r[1]); rt.putString("k", r[2]);
                    rp.add(rt);
                }
                mt.put("rp", rp);
                mt.putInt("ans", m.answered);
            }
            msgs.add(mt);
        }
        t.put("messages", msgs);
        t.putInt("nextMsg", nextMessageId);
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
            c.putBoolean("out", call.outgoing);
            c.putLong("pickup", call.pickupAt);
            t.put("call", c);
        }
        ListTag ap = new ListTag();
        for (Appt a : appts) ap.add(a.save());
        t.put("appts", ap);
        t.putInt("nextAppt", nextApptId);
        t.putLong("nextRequest", nextRequest);
        t.putInt("weeklyDow", weeklyDow);
        t.putInt("weeklyHour", weeklyHour);
        t.putLong("skipWeek", skipWeek);
        ListTag ag = new ListTag();
        for (String a : agenda) ag.add(net.minecraft.nbt.StringTag.valueOf(a));
        t.put("agenda", ag);
        ListTag agt = new ListTag();
        for (String a : agenda) { Calls.Template tp = Calls.get(a); agt.add(net.minecraft.nbt.StringTag.valueOf(tp == null ? a : Calls.title(tp))); }
        t.put("agendaTitles", agt);
        CompoundTag au = new CompoundTag();
        agendaUsed.forEach(au::putLong);
        t.put("agendaUsed", au);
        t.putLong("lastSession", lastSession);
        t.putString("lastCallFrom", lastCallFrom);
        t.putString("lastCallText", lastCallText);
        t.putLong("lastCallAt", lastCallAt);
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
        anomalyKills = t.getInt("anomalyKills");
        trial = t.getInt("trial");
        trialKills = t.getInt("trialKills");
        inducted = t.getBoolean("inducted");
        stability = t.contains("stability") ? t.getInt("stability") : 70;
        testCounter = t.getInt("tests");
        builtTower = t.getBoolean("tower");
        messages.clear();
        nextMessageId = Math.max(1, t.getInt("nextMsg"));
        for (Tag e : t.getList("messages", Tag.TAG_COMPOUND)) {
            CompoundTag mt = (CompoundTag) e;
            Message m = new Message();
            m.sender = mt.getString("s");
            m.body = mt.getString("b");
            m.time = mt.getLong("t");
            m.read = mt.getBoolean("r");
            m.out = mt.getBoolean("o");
            m.id = mt.getInt("id");
            for (Tag r : mt.getList("rp", Tag.TAG_COMPOUND)) {
                CompoundTag rt = (CompoundTag) r;
                m.replies.add(new String[]{rt.getString("l"), rt.getString("a"), rt.getString("k")});
            }
            m.answered = mt.contains("ans") ? mt.getInt("ans") : -1;
            if (m.id == 0) m.id = nextMessageId++;
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
            call.outgoing = c.getBoolean("out");
            call.pickupAt = c.getLong("pickup");
        }
        appts.clear();
        for (Tag e : t.getList("appts", Tag.TAG_COMPOUND)) appts.add(Appt.load((CompoundTag) e));
        nextApptId = Math.max(1, t.getInt("nextAppt"));
        nextRequest = t.getLong("nextRequest");
        agenda.clear();
        for (Tag e : t.getList("agenda", Tag.TAG_STRING)) agenda.add(e.getAsString());
        agendaUsed.clear();
        CompoundTag au = t.getCompound("agendaUsed");
        for (String k : au.getAllKeys()) agendaUsed.put(k, au.getLong(k));
        lastSession = t.getLong("lastSession");
        if (t.contains("weeklyDow")) { weeklyDow = t.getInt("weeklyDow"); weeklyHour = t.getInt("weeklyHour"); skipWeek = t.getLong("skipWeek"); }
        lastCallFrom = t.getString("lastCallFrom");
        lastCallText = t.getString("lastCallText");
        lastCallAt = t.getLong("lastCallAt");
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
