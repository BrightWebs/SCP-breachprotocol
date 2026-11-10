package com.breachprotocol.career;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.compat.UnleashedApi;
import com.breachprotocol.entity.FoundationStaff;
import com.breachprotocol.entity.O5MemberEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.lang.reflect.Method;
import java.util.*;

/**
 * Who the Foundation's guards shoot, and the site alert when outsiders come.
 *
 * <ul>
 * <li>Hostile mobs (creepers first) anywhere near a guard.</li>
 * <li>Any organisation's people (SCP Unleashed's Chaos Insurgency, Serpent's Hand, Daevites and
 *     the rest) unless the Administrator has invited that organisation.</li>
 * <li>Players the Administrator has marked kill-on-sight, and anyone who attacks the staff.</li>
 * </ul>
 * When outsiders reach a site, the site goes on alert: every guard there converges on them, and
 * the people at the site (and the Administrator) are told, until they're dead or gone.
 */
@EventBusSubscriber(modid = BreachProtocol.MODID)
public final class Defence {
    private Defence() {}

    /** Organisations the Administrator can invite (SCP Unleashed faction ids). Others are always hostile. */
    public static final String[] ORGS = {"scp_unleashed:chaos_insurgency", "scp_unleashed:serpents_hand", "scp_unleashed:daevites"};
    public static final String[] ORG_NAMES = {"The Chaos Insurgency", "The Serpent's Hand", "The Daevites"};
    /** SCP Unleashed entities that are always one of these organisations, bonded or not. */
    private static final Map<String, String> ORG_OF_TYPE = Map.of(
            "scp_unleashed:chaos_insurgent", "scp_unleashed:chaos_insurgency",
            "scp_unleashed:chaos_helicopter", "scp_unleashed:chaos_insurgency",
            "scp_unleashed:serpents_hand_agent", "scp_unleashed:serpents_hand",
            "scp_unleashed:serpents_hand_seal", "scp_unleashed:serpents_hand");

    public static final class Data extends SavedData {
        public final Set<String> invited = new HashSet<>();
        /** Players (by name, lower case) the guards shoot on sight. */
        public final Set<String> hostile = new HashSet<>();
        public static final SavedData.Factory<Data> FACTORY = new SavedData.Factory<>(Data::new, Data::load, null);

        static Data load(CompoundTag t, HolderLookup.Provider p) {
            Data d = new Data();
            for (Tag x : t.getList("invited", Tag.TAG_STRING)) d.invited.add(x.getAsString());
            for (Tag x : t.getList("hostile", Tag.TAG_STRING)) d.hostile.add(x.getAsString());
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag t, HolderLookup.Provider p) {
            ListTag a = new ListTag(), b = new ListTag();
            for (String s : invited) a.add(StringTag.valueOf(s));
            for (String s : hostile) b.add(StringTag.valueOf(s));
            t.put("invited", a);
            t.put("hostile", b);
            return t;
        }
    }

    public static Data data(MinecraftServer s) { return s.overworld().getDataStorage().computeIfAbsent(Data.FACTORY, "breachprotocol_defence"); }

    // ================================================================== who's an enemy

    private static Method factionOf, factionId;

    /** The SCP Unleashed organisation an entity belongs to ("" = none). */
    public static String orgOf(Entity e) {
        String type = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString();
        String fixed = ORG_OF_TYPE.get(type);
        if (fixed != null) return fixed;
        try {
            if (factionOf == null) factionOf = UnleashedApi.method("faction.Factions", "factionOf", Entity.class);
            Object f = UnleashedApi.call(factionOf, null, e);
            if (f == null) return "";
            if (factionId == null) factionId = f.getClass().getMethod("id");
            Object id = factionId.invoke(f);
            return id instanceof ResourceLocation rl ? rl.toString() : "";
        } catch (Throwable t) {
            return "";
        }
    }

    public static boolean invited(MinecraftServer s, String org) { return data(s).invited.contains(org); }

    /** An outsider: someone from an organisation that wasn't invited, or a kill-on-sight player. */
    public static boolean intruder(MinecraftServer s, Entity e) {
        if (e instanceof Player p) return !p.isCreative() && !p.isSpectator() && data(s).hostile.contains(p.getGameProfile().getName().toLowerCase(Locale.ROOT));
        String org = orgOf(e);
        return !org.isEmpty() && !invited(s, org) && !Policy.truce(s, org);
    }

    private static boolean topBrass(Player p) { return Career.rank(p) == Rank.ADMINISTRATOR || Career.isAscended(p); }

    /** Whether a guard should shoot this. */
    public static boolean threat(FoundationStaff guard, LivingEntity c) {
        Boolean team = Response.threatFor(guard, c); // a breach team's quarry: shot only on a termination order
        if (team != null) return team;
        if (c == guard || !c.isAlive() || c instanceof FoundationStaff || c instanceof O5MemberEntity || c.isInvisible() && !(c instanceof Creeper)) return false;
        MinecraftServer s = guard.getServer();
        if (s == null) return false;
        if (c instanceof Player p) {
            if (p.isCreative() || p.isSpectator()) return false;
            if (intruder(s, p)) return true;
            // fired on us: shoot back (never at the Administrator)
            return !topBrass(p) && c == guard.getLastHurtByMob() && guard.tickCount - guard.getLastHurtByMobTimestamp() < 200;
        }
        if (UnleashedApi.isShyGuy(c)) return false; // never look at it, never shoot it
        if (c instanceof Creeper) return true;
        if (intruder(s, c)) return true;
        if (c == guard.getLastHurtByMob() && guard.tickCount - guard.getLastHurtByMobTimestamp() < 200) return true;
        String ns = BuiltInRegistries.ENTITY_TYPE.getKey(c.getType()).getNamespace();
        if (c instanceof NeutralMob nm && !(c instanceof Mob m && m.getTarget() != null)) return false;
        if (c instanceof Enemy && !ns.equals("scp_unleashed")) return true;
        // anything (an escaped SCP included) going after our people
        if (c instanceof Mob m && m.getTarget() != null && (m.getTarget() instanceof FoundationStaff || m.getTarget() instanceof O5MemberEntity
                || m.getTarget() instanceof Player p && !intruder(s, p))) return true;
        return false;
    }

    /** Whether a shot at someone near this entity could hit them (they're on our side). */
    public static boolean friendly(MinecraftServer s, Entity e) {
        if (e instanceof FoundationStaff || e instanceof O5MemberEntity) return true;
        if (e instanceof Player p) return !intruder(s, p);
        return false;
    }

    // ================================================================== site alerts

    public static final class Alert {
        public final String site;
        public BlockPos where;
        public int hostiles, kills, quiet;
        public String org = "";
        public final long since;

        Alert(String site, BlockPos where, long since) { this.site = site; this.where = where; this.since = since; }
    }

    private static final Map<String, Alert> ALERTS = new HashMap<>();

    public static Alert alertAt(String site) { return ALERTS.get(site); }

    /** The alert at the site this guard is posted to (if any). */
    public static Alert alertNear(Entity e) {
        for (Alert a : ALERTS.values()) if (a.where.closerThan(e.blockPosition(), 96)) return a;
        return null;
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        MinecraftServer s = event.getServer();
        if (s.getTickCount() % 40 != 13) return;
        Sites all = Sites.get(s);
        for (Sites.Site site : all.sites) {
            ServerLevel l = s.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, ResourceLocation.parse(site.dim)));
            if (l == null) continue;
            BlockPos c = new BlockPos(site.x, site.y, site.z);
            if (!l.hasChunkAt(c) || l.getNearestPlayer(c.getX(), c.getY(), c.getZ(), 192, false) == null) continue;
            int r = site.radius + 24;
            List<LivingEntity> in = l.getEntitiesOfClass(LivingEntity.class, new AABB(c).inflate(r, 48, r), e -> e.isAlive() && intruder(s, e));
            Alert a = ALERTS.get(site.name);
            if (!in.isEmpty()) {
                LivingEntity first = in.get(0);
                if (a == null) {
                    a = new Alert(site.name, first.blockPosition(), l.getGameTime());
                    ALERTS.put(site.name, a);
                    a.org = first instanceof Player p ? p.getGameProfile().getName() : orgName(orgOf(first));
                    raise(l, site, a, in.size());
                }
                a.where = first.blockPosition();
                a.hostiles = in.size();
                a.quiet = 0;
            } else if (a != null && ++a.quiet >= 15) {
                ALERTS.remove(site.name);
                stand(l, site, a);
            }
        }
    }

    public static String orgName(String org) {
        for (int i = 0; i < ORGS.length; i++) if (ORGS[i].equals(org)) return ORG_NAMES[i];
        if (org.isEmpty()) return "Hostiles";
        String p = org.substring(org.indexOf(':') + 1).replace('_', ' ');
        return "The " + Character.toUpperCase(p.charAt(0)) + p.substring(1);
    }

    private static List<ServerPlayer> told(ServerLevel l, Sites.Site site) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer q : l.getServer().getPlayerList().getPlayers()) {
            boolean near = q.level() == l && q.blockPosition().closerThan(new BlockPos(site.x, site.y, site.z), site.radius + 96);
            if (near || topBrass(q) || site.owner.equals(q.getUUID().toString())) out.add(q);
        }
        return out;
    }

    private static void raise(ServerLevel l, Sites.Site site, Alert a, int n) {
        Component msg = Component.literal("⚠ SECURITY ALERT · " + site.name + ": ").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                .append(Component.literal(a.org + " on the grounds (" + n + " hostile" + (n == 1 ? "" : "s") + "). All security to the perimeter. Shoot on sight.").withStyle(ChatFormatting.RED));
        for (ServerPlayer q : told(l, site)) {
            q.sendSystemMessage(msg);
            q.playNotifySound(SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 0.9F, 1F);
        }
        WorldNews.record(l.getServer(), WorldNews.Kind.ATTACK, "attack", a.org.toLowerCase(Locale.ROOT).startsWith("the ") ? a.org.substring(0, 1).toLowerCase(Locale.ROOT) + a.org.substring(1) + " attacked " + site.name : a.org + " attacked " + site.name, -3, "");
        Forces.underAttack(l, site, a);
    }

    private static void stand(ServerLevel l, Sites.Site site, Alert a) {
        Component msg = Component.literal("✔ " + site.name + ": all clear. ").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                .append(Component.literal(a.kills > 0 ? "Security neutralised " + a.kills + " hostile" + (a.kills == 1 ? "" : "s") + "." : "The intruders have withdrawn.").withStyle(ChatFormatting.GREEN));
        for (ServerPlayer q : told(l, site)) q.sendSystemMessage(msg);
        WorldNews.record(l.getServer(), WorldNews.Kind.ATTACK, "repelled", "security at " + site.name + " drove off " + a.org.replaceFirst("^The ", "the ") + (a.kills > 0 ? ", killing " + a.kills : ""), 3, "");
    }

    @SubscribeEvent
    public static void died(LivingDeathEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel l)) return;
        if (event.getSource().getEntity() instanceof FoundationStaff && intruder(l.getServer(), event.getEntity())) {
            Alert a = alertNear(event.getEntity());
            if (a != null) a.kills++;
        }
    }

    // ================================================================== the computer's controls

    public static void toggleInvite(ServerPlayer p, int i) {
        if (!topBrass(p) || i < 0 || i >= ORGS.length) return;
        Data d = data(p.server);
        String org = ORGS[i];
        boolean now = !d.invited.remove(org);
        if (now) d.invited.add(org);
        d.setDirty();
        WorldNews.record(p.server, WorldNews.Kind.DECISION, "org_" + i, now ? ORG_NAMES[i].replaceFirst("^The ", "the ") + " were invited onto Foundation ground" : ORG_NAMES[i].replaceFirst("^The ", "the ") + " were declared hostile again", now ? -1 : 1, p.getGameProfile().getName());
    }

    public static void toggleHostile(ServerPlayer p, int i) {
        if (!topBrass(p)) return;
        List<String> names = people(p);
        if (i < 0 || i >= names.size()) return;
        Data d = data(p.server);
        String n = names.get(i).toLowerCase(Locale.ROOT);
        if (!d.hostile.remove(n)) d.hostile.add(n);
        d.setDirty();
    }

    /** Other players, sorted, as the computer lists them. */
    public static List<String> people(ServerPlayer p) {
        List<String> out = new ArrayList<>();
        for (ServerPlayer q : p.server.getPlayerList().getPlayers()) if (q != p) out.add(q.getGameProfile().getName());
        for (String h : data(p.server).hostile) if (out.stream().noneMatch(x -> x.equalsIgnoreCase(h))) out.add(h);
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    public static void addToSync(ServerPlayer p, CompoundTag t) {
        Data d = data(p.server);
        ListTag orgs = new ListTag();
        for (int i = 0; i < ORGS.length; i++) {
            CompoundTag o = new CompoundTag();
            o.putString("n", ORG_NAMES[i]);
            o.putBoolean("inv", d.invited.contains(ORGS[i]));
            orgs.add(o);
        }
        t.put("orgs", orgs);
        ListTag ppl = new ListTag();
        for (String n : people(p)) {
            CompoundTag o = new CompoundTag();
            o.putString("n", n);
            o.putBoolean("kos", d.hostile.contains(n.toLowerCase(Locale.ROOT)));
            ppl.add(o);
        }
        t.put("people", ppl);
        ListTag al = new ListTag();
        for (Alert a : ALERTS.values()) {
            CompoundTag o = new CompoundTag();
            o.putString("site", a.site);
            o.putString("who", a.org);
            o.putInt("n", a.hostiles);
            o.putInt("k", a.kills);
            al.add(o);
        }
        t.put("alerts", al);
    }
}
