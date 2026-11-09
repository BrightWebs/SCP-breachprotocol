package com.breachprotocol.career;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.compat.Unleashed;
import com.breachprotocol.entity.FoundationStaff;
import com.breachprotocol.entity.O5MemberEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * What your powers treat as hostile. Every power that acts "all around you" (and every power left
 * on auto) touches only what this says is hostile:
 *
 * <ul>
 * <li><b>The world decides</b> (the default): monsters, anything hunting you, and anomalies loose
 *     from containment, but never players, Foundation staff, Overseers, or anything that follows
 *     you or that you've calmed or tamed.</li>
 * <li><b>Everything is hostile</b>: everything in range, players too.</li>
 * </ul>
 *
 * Either way, a kind of creature, or a particular player, can be marked <b>hostile</b> (always hit)
 * or <b>spared</b> (never hit), and the mark wins. And whatever you're looking at when you use a
 * power by hand is hit too, spared or not: that's how you touch one thing deliberately.
 */
@EventBusSubscriber(modid = BreachProtocol.MODID)
public final class Targets {
    private Targets() {}

    public static final byte WORLD = 0, HOSTILE = 1, SPARED = 2;
    private static final String ALL = "bp_tgt_all", TYPES = "bp_tgt_types", PLAYERS = "bp_tgt_players";

    public static boolean everything(ServerPlayer p) { return p.getPersistentData().getBoolean(ALL); }

    private static byte typeMark(ServerPlayer p, LivingEntity e) {
        return p.getPersistentData().getCompound(TYPES).getByte(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString());
    }

    private static byte playerMark(ServerPlayer p, UUID id) {
        return p.getPersistentData().getCompound(PLAYERS).getCompound(id.toString()).getByte("v");
    }

    /** Is this, by your rules, something your powers should hit? */
    public static boolean hostile(ServerPlayer p, LivingEntity e) {
        if (e == p || !e.isAlive()) return false;
        if (e instanceof Player q) {
            if (q.isSpectator() || q.isCreative()) return false;
            byte v = playerMark(p, q.getUUID());
            return v == HOSTILE || v != SPARED && everything(p);
        }
        byte v = typeMark(p, e);
        if (v == HOSTILE) return true;
        if (v == SPARED) return false;
        if (yours(p, e)) return false;
        if (everything(p)) return true;
        return e instanceof Enemy || e instanceof Mob m && m.getTarget() == p || Unleashed.isAnomaly(e) && e instanceof Mob am && !Unleashed.isContained(am);
    }

    /** Your own people and creatures: spared unless you mark their kind hostile, or look straight at them. */
    public static boolean yours(ServerPlayer p, LivingEntity e) {
        String me = p.getUUID().toString();
        CompoundTag t = e.getPersistentData();
        return e instanceof FoundationStaff || e instanceof O5MemberEntity || me.equals(t.getString("bp_dom_follow")) || me.equals(t.getString("bp_dom_calm"))
                || e instanceof OwnableEntity o && p.getUUID().equals(o.getOwnerUUID());
    }

    /** Everything hostile within r; plus, if given, whatever you're looking at. */
    public static List<LivingEntity> around(ServerPlayer p, int r, LivingEntity looked) {
        List<LivingEntity> out = new ArrayList<>(p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(r),
                e -> e.distanceToSqr(p) <= (double) r * r + 4 && hostile(p, e)));
        if (looked != null && looked != p && looked.isAlive() && !out.contains(looked)) out.add(looked);
        return out;
    }

    // ------------------------------------------------------------------ settings, from the console

    /** "pow:mode:0|1", "pow:type:v:namespace:path", "pow:player:v:uuid". */
    public static void action(ServerPlayer p, String a) {
        String[] s = a.split(":", 4);
        CompoundTag pd = p.getPersistentData();
        try {
            switch (s[1]) {
                case "mode" -> pd.putBoolean(ALL, "1".equals(s[2]));
                case "type" -> {
                    byte v = Byte.parseByte(s[2]);
                    ResourceLocation id = ResourceLocation.tryParse(s[3]);
                    if (id == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(id)) return;
                    CompoundTag t = pd.getCompound(TYPES);
                    if (v == WORLD) t.remove(id.toString()); else t.putByte(id.toString(), v);
                    pd.put(TYPES, t);
                }
                case "player" -> {
                    byte v = Byte.parseByte(s[2]);
                    UUID id = UUID.fromString(s[3]);
                    CompoundTag t = pd.getCompound(PLAYERS);
                    if (v == WORLD) t.remove(id.toString());
                    else {
                        CompoundTag e = new CompoundTag();
                        e.putByte("v", v);
                        ServerPlayer q = p.server.getPlayerList().getPlayer(id);
                        e.putString("n", q != null ? q.getGameProfile().getName() : t.getCompound(id.toString()).getString("n"));
                        t.put(id.toString(), e);
                    }
                    pd.put(PLAYERS, t);
                }
                default -> { return; }
            }
        } catch (RuntimeException ignored) { return; }
        Career.sync(p);
    }

    public static void addToSync(ServerPlayer p, CompoundTag t) {
        CompoundTag pd = p.getPersistentData(), out = new CompoundTag();
        out.putBoolean("all", pd.getBoolean(ALL));
        out.put("types", pd.getCompound(TYPES).copy());
        out.put("players", pd.getCompound(PLAYERS).copy());
        t.put("tgt", out);
    }

    // ------------------------------------------------------------------ settings outlive death

    private static final String[] KEEP = {"bp_aura", "bp_tgt_", "bp_auto", "bp_pcd_"};

    @SubscribeEvent
    public static void clone(PlayerEvent.Clone event) {
        CompoundTag from = event.getOriginal().getPersistentData(), to = event.getEntity().getPersistentData();
        for (String k : from.getAllKeys()) for (String pre : KEEP) if (k.startsWith(pre) && !to.contains(k)) {
            Tag v = from.get(k);
            if (v != null) to.put(k, v.copy());
        }
    }
}
