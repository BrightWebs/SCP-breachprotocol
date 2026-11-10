package com.breachprotocol.career;

import com.breachprotocol.compat.Unleashed;
import com.breachprotocol.compat.UnleashedApi;
import com.breachprotocol.event.BreachAlerts;
import com.breachprotocol.network.CareerSyncPayload;
import com.breachprotocol.registry.ModAttachments;
import com.breachprotocol.registry.ModItems;
import com.breachprotocol.registry.ModSounds;
import com.breachprotocol.world.Site01;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;
import java.util.function.Supplier;

/** The career system: XP, promotions, missions, messages and calls. Server side only. */
public final class Career {
    private Career() {}

    public static final int MAX_MISSIONS = 3;
    public static final long MINUTE = 1200L;

    public static CareerData data(Player p) {
        return p.getData(ModAttachments.CAREER);
    }

    public static boolean isAscended(Player p) {
        return data(p).ascended;
    }

    public static Rank rank(Player p) {
        return data(p).getRank();
    }

    /**
     * How the Council addresses you. Class-D are numbers; staff have names; the Council's own
     * members and the Administrator are "sir" to most of the Foundation.
     */
    /** How the person you're talking to has been asked to address you ("sir", "maam", "title", "name"); set while they speak. */
    public static final ThreadLocal<String> ADDRESS = new ThreadLocal<>();

    public static String address(Player p) {
        CareerData d = data(p);
        String name = p.getGameProfile().getName();
        String asked = ADDRESS.get();
        if (asked != null && !asked.isEmpty() && !asked.equals("title")) return switch (asked) { case "maam" -> "ma'am"; case "name" -> name; default -> "sir"; };
        if (d.ascended) return "asked".equals(asked) ? "Master" : asked != null && asked.equals("title") ? "Master" : "sir";
        if (asked != null && asked.equals("title")) return switch (d.getRank()) {
            case OVERSEER -> "Overseer";
            case ADMINISTRATOR -> "Administrator";
            default -> address0(p, d, name);
        };
        return address0(p, d, name);
    }

    private static String address0(Player p, CareerData d, String name) {
        return switch (d.getRank()) {
            case CLASS_D -> personnelId(p);
            case LEVEL_1 -> name;
            case LEVEL_2 -> "Dr. " + name;
            case LEVEL_3 -> "Officer " + name;
            case LEVEL_4 -> "Specialist " + name;
            case LEVEL_5 -> "Director " + name;
            case OVERSEER, ADMINISTRATOR -> "sir"; // unless asked otherwise (see ADDRESS)
        };
    }

    /** Personnel file number: Class-D get a D-number; staff keep the same digits on a Foundation number. */
    public static String personnelId(Player p) {
        int n = 1000 + Math.abs(p.getUUID().hashCode() % 9000);
        return (data(p).getRank() == Rank.CLASS_D && !data(p).ascended ? "D-" : "FDN-") + n;
    }

    // ------------------------------------------------------------------ messages

    public static void message(ServerPlayer p, String sender, String body) {
        CareerData d = data(p);
        d.addMessage(sender, body, p.level().getGameTime());
        // one notice, not a copy of the message: it's on the phone
        p.displayClientMessage(Component.literal("✉ New message from ").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(sender).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" · open your phone").withStyle(ChatFormatting.GRAY)), true);
        p.playNotifySound(ModSounds.PHONE_MESSAGE.get(), SoundSource.PLAYERS, 0.8F, 1.0F);
    }

    public static void message(ServerPlayer p, O5 from, String body) {
        message(p, from.sender(), body);
    }

    /** An order you can give in answer to a message: what you say, what it does (a phone action, "name#arg"), and what they say back. */
    public record Reply(String label, String action, String answer) {}

    /** A message that asks for your decision: the orders appear under it on the phone. */
    public static void message(ServerPlayer p, String sender, String body, Reply... replies) {
        CareerData d = data(p);
        CareerData.Message m = d.addMessage(sender, body, p.level().getGameTime());
        for (Reply r : replies) if (r != null) m.replies.add(new String[]{r.label(), r.action(), r.answer() == null ? "" : r.answer()});
        p.displayClientMessage(Component.literal("✉ ").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(sender).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(m.replies.isEmpty() ? " · open your phone" : " needs your orders · open your phone").withStyle(ChatFormatting.GRAY)), true);
        p.playNotifySound(ModSounds.PHONE_MESSAGE.get(), SoundSource.PLAYERS, 0.8F, 1.0F);
        sync(p);
    }

    public static void message(ServerPlayer p, O5 from, String body, Reply... replies) { message(p, from.sender(), body, replies); }

    /** You gave an order in reply: it's said, it's done, and they answer. */
    public static void reply(ServerPlayer p, int id, int choice) {
        CareerData d = data(p);
        CareerData.Message m = d.findMessage(id);
        if (m == null || m.answered >= 0 || choice < 0 || choice >= m.replies.size()) return;
        String[] r = m.replies.get(choice);
        m.answered = choice;
        CareerData.Message mine = d.addMessage(m.sender, r[0], p.level().getGameTime());
        mine.out = true;
        mine.read = true;
        String act = r[1];
        int arg = 0, hash = act.indexOf('#');
        if (hash >= 0) { try { arg = Integer.parseInt(act.substring(hash + 1)); } catch (NumberFormatException ignored) {} act = act.substring(0, hash); }
        if (!act.isEmpty() && !act.startsWith("msg_reply")) com.breachprotocol.network.PhoneActions.handle(p, act, arg);
        if (!r[2].isEmpty()) {
            CareerData.Message back = d.addMessage(m.sender, r[2], p.level().getGameTime() + 1);
            back.read = true;
        }
        d.dirty = true;
        sync(p);
    }

    // ------------------------------------------------------------------ xp + ranks

    /**
     * There is no XP. Good work still matters: for the Administrator it steadies the Foundation.
     * Promotions only come from completed assignments (see {@link #commend}).
     */
    public static void addXp(ServerPlayer p, int amount, String reason) {
        CareerData d = data(p);
        if (d.getRank() == Rank.ADMINISTRATOR && amount > 0) changeStability(p, Math.max(1, amount / 30));
    }

    /** A completed assignment goes on your record. Enough of them and the Council promotes you. */
    public static void commend(ServerPlayer p) {
        CareerData d = data(p);
        d.xp++;
        d.dirty = true;
        checkPromotion(p);
    }

    public static void changeStability(ServerPlayer p, int delta) {
        CareerData d = data(p);
        d.stability = Math.max(0, Math.min(100, d.stability + delta));
        d.dirty = true;
    }

    public static void checkPromotion(ServerPlayer p) {
        CareerData d = data(p);
        Rank next = d.getRank().next();
        while (next != null && d.xp >= next.xpRequired) {
            if (next == Rank.OVERSEER) {
                if (!d.o5OfferPending && d.call == null) {
                    d.o5OfferPending = true;
                    Calls.start(p, "o5_offer");
                }
                return;
            }
            if (next == Rank.ADMINISTRATOR) {
                boolean hasTrial = d.missions.stream().anyMatch(m -> m.type == Mission.Type.FOUNDERS_TRIAL);
                if (!hasTrial) {
                    Mission m = new Mission();
                    m.type = Mission.Type.FOUNDERS_TRIAL;
                    m.giver = 1;
                    m.target = 3;
                    m.xp = 0;
                    m.expires = Long.MAX_VALUE;
                    m.description = "The Founder's Trial: hold THREE different anomalies in containment at the same time, within 64 blocks of you.";
                    d.missions.add(0, m);
                    message(p, O5.O5_1, "You have outgrown the Council, Thirteen. Show me you can hold what others cannot. Contain three of them at once, and the Foundation is yours.");
                }
                return;
            }
            promote(p, next);
            next = d.getRank().next();
        }
    }

    public static void promote(ServerPlayer p, Rank r) {
        CareerData d = data(p);
        // there is only ever one Administrator
        if (r == Rank.ADMINISTRATOR && !Site01.claimAdministrator(p)) {
            p.sendSystemMessage(Component.literal("The Foundation already has an Administrator. There can only be one.").withStyle(ChatFormatting.DARK_PURPLE));
            return;
        }
        if (d.getRank() == Rank.ADMINISTRATOR && r != Rank.ADMINISTRATOR) Site01.releaseAdministrator(p);
        d.setRank(r);
        d.xp = Math.max(d.xp, r.xpRequired);
        Component title = Component.literal("PROMOTED").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        Component sub = Component.literal(r.title).withStyle(r.color);
        p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
        p.connection.send(new ClientboundSetTitleTextPacket(title));
        p.connection.send(new ClientboundSetSubtitleTextPacket(sub));
        p.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F, 1.0F);
        if (r.clearance >= 1 && r.atLeast(Rank.LEVEL_1) && !r.atLeast(Rank.OVERSEER)) {
            give(p, keycard(r.clearance), 1);
        }
        switch (r) {
            case LEVEL_1 -> message(p, O5.O5_3, "> PERSONNEL FILE UPDATED. " + p.getGameProfile().getName().toUpperCase() + " RECLASSIFIED: STAFF, " + personnelId(p) + ". CLEARANCE 1. YOUR D-CLASS TERMINATION DATE HAS BEEN... POSTPONED.");
            case LEVEL_3 -> message(p, O5.O5_6, "You've got a steady hand. Security is yours now. Keep the doors shut and the lights on.");
            case LEVEL_5 -> message(p, O5.O5_7, "Site Director. Your site, your responsibility. The Council will be watching. Lockdown authority has been added to your phone.");
            case OVERSEER -> {
                WorldNews.record(p.server, WorldNews.Kind.PROMOTION, "o5", p.getGameProfile().getName() + " was made O5-13", 2, p.getGameProfile().getName());
                message(p, O5.O5_7, "Welcome to the Council, Thirteen. When we are divided, you decide. Expect calls.");
                broadcast(p.server, Component.literal("[O5 COMMAND] ").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
                        .append(Component.literal(p.getName().getString() + " has taken the seat of O5-13.").withStyle(ChatFormatting.RED)));
            }
            case ADMINISTRATOR -> {
                // the head of the table: the Council chair they held goes back to the Overseer they took it from
                Council.vacate(p);
                WorldNews.record(p.server, WorldNews.Kind.PROMOTION, "admin", p.getGameProfile().getName() + " became the Administrator", 5, p.getGameProfile().getName());
                ServerLevel overworld = p.server.overworld();
                p.sendSystemMessage(Component.literal("Site-01 is being prepared...").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
                BlockPos site = Site01.ensureBuilt(overworld);
                message(p, O5.O5_1, "It's yours now. All of it. Site-01 is ready for you at " + site.getX() + ", " + site.getY() + ", " + site.getZ()
                        + ". Your phone can take you there. Beyond the Gate waits the last thing I never dared to do.");
                broadcast(p.server, Component.literal("[O5 COMMAND] ").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD)
                        .append(Component.literal(p.getName().getString() + " is now The Administrator of the SCP Foundation.").withStyle(ChatFormatting.LIGHT_PURPLE)));
            }
            default -> message(p, O5.O5_3, "> PERSONNEL FILE UPDATED. NEW CLEARANCE: " + r.title.toUpperCase() + ". KEYCARD ISSUED.");
        }
        d.nextMission = Math.min(d.nextMission, p.level().getGameTime() + 400);
        sync(p);
    }

    public static Item keycard(int level) {
        return Unleashed.keycard(level);
    }

    /** Budget and Council deliveries: with a secretary, they go to her (and into the chest by your desk), not your pockets. */
    public static void budget(ServerPlayer p, Item item, int count) {
        if ((rank(p) == Rank.ADMINISTRATOR || isAscended(p)) && Secretary.deliver(p, new ItemStack(item, count))) return;
        give(p, item, count);
    }

    public static void give(ServerPlayer p, Item item, int count) {
        ItemStack s = new ItemStack(item, count);
        if (!p.getInventory().add(s)) p.drop(s, false);
    }

    public static void broadcast(net.minecraft.server.MinecraftServer server, Component c) {
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) sp.sendSystemMessage(c);
    }

    // ------------------------------------------------------------------ hooks from gameplay

    /** SCP Unleashed's stasis pod sealed a creature (its capture hook). That's a recovery. */
    public static void onStasis(ServerLevel level, net.minecraft.world.entity.player.Player user, Mob target) {
        if (!(user instanceof ServerPlayer p) || !Unleashed.isAnomaly(target)) return;
        CareerData d = data(p);
        String designation = Unleashed.designation(target);
        boolean any = false;
        Sites.Site site = nearestSite(p);
        for (Mission m : d.missions) {
            if (m.type == Mission.Type.RECOVER && !m.done() && m.stage == 0 && (m.item.isEmpty() ? m.designation.equals(designation) : Unleashed.is(target, m.item))) {
                any = true;
                m.stage = 1;
                m.expires = Math.max(m.expires, level.getGameTime() + 15 * MINUTE);
                if (site != null) {
                    m.x = site.x; m.y = site.y; m.z = site.z; m.hasPos = true;
                    m.description = "Stage 2: deliver " + designation + " to " + site.name + " (" + site.x + ", " + site.z + "). Get inside the site with the pod. Watch its timer.";
                    message(p, O5.seat(m.giver), designation + " is in the pod. Bring it to " + site.name + ". Missions > Guide me points the way. The field won't last forever.");
                } else {
                    m.hasPos = false;
                    m.description = "Stage 2: deliver " + designation + " to a Foundation site. You have none: declare one (Settings, Level 3+), then bring the pod inside it.";
                    message(p, O5.seat(m.giver), designation + " is in the pod, but there's no site to take it to. Declare one, fast.");
                }
            }
        }
        changeStability(p, any ? 3 : 2);
        if (!any) message(p, O5.O5_6, designation + " sealed in stasis. Get it to a cell before the pod runs out.");
        d.dirty = true;
        completeFinished(p);
        sync(p);
    }

    /** A phone photo caught these anomaly types: field documentation. */
    public static void onPhoto(ServerPlayer p, java.util.Set<String> types) {
        CareerData d = data(p);
        for (Mission m : d.missions) {
            if (m.type == Mission.Type.DOCUMENT && !m.done() && types.contains(m.item)) {
                m.progress = m.target;
                d.dirty = true;
            }
        }
        completeFinished(p);
    }

    /** Every 5 seconds: anomalies around the player that are now held in a cell. */
    public static void scanContainment(ServerPlayer p) {
        // an anomaly loose near you, outside every site: the Council hands you something to do about it
        CareerData cd = data(p);
        long now = p.level().getGameTime();
        if (!cd.ascended && cd.getRank() != Rank.ADMINISTRATOR && now >= cd.nextContext && cd.missions.size() <= MAX_MISSIONS && p.level().dimension() == Level.OVERWORLD) {
            Mob loose = looseNear(p, 48);
            if (loose != null) {
                Rank r = cd.getRank();
                Mission.Type type = r == Rank.CLASS_D || r == Rank.LEVEL_1 ? Mission.Type.EXPOSURE : r == Rank.LEVEL_2 ? Mission.Type.DOCUMENT
                        : recoverable(loose) ? Mission.Type.RECOVER : Mission.Type.DOCUMENT;
                assign(p, type, loose);
                cd.nextContext = now + 4 * MINUTE;
            }
        }
        // new rifts nearby: the phone tells you where
        CareerData d = data(p);
        if (Clearance.linked(p, "rifts")) for (net.minecraft.world.entity.Entity r : p.level().getEntities(p, p.getBoundingBox().inflate(96), UnleashedApi::isRift)) {
            if (d.seenRifts.add(r.getId())) {
                String rarity = UnleashedApi.riftLabel(r);
                message(p, O5.O5_9, "Rift detected: " + rarity.substring(0, 1).toUpperCase() + rarity.substring(1) + " rift near " + r.getBlockX() + ", " + r.getBlockZ()
                        + ". Draw it out with a Rift Extractor. Containment > Rifts will guide you.");
                d.dirty = true;
            }
        }
        for (Mob mob : p.level().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(48), Unleashed::isAnomaly)) {
            if (Unleashed.isContained(mob)) onContained(p.serverLevel(), p.blockPosition(), mob);
        }
    }

    /** An anomaly was found held in a cell near {@code where}. */
    public static void onContained(ServerLevel level, BlockPos generator, Mob mob) {
        if (!Unleashed.isAnomaly(mob)) return;
        long now = level.getGameTime();
        CompoundTag pd = mob.getPersistentData();
        boolean fresh = now - pd.getLong("bp_last_contained") > 5 * MINUTE || !pd.contains("bp_last_contained");
        pd.putLong("bp_last_contained", now);
        if (!fresh) return;
        String designation = Unleashed.designation(mob);
        int reward = switch (Unleashed.objectClass(mob)) {
            case "Safe" -> 10;
            case "Keter" -> 60;
            default -> 30;
        };
        for (ServerPlayer p : level.players()) {
            if (p.blockPosition().closerThan(generator, 48)) {
                addXp(p, reward, designation + " contained");
                for (Mission m : data(p).missions) {
                    if (m.type == Mission.Type.RECOVER && !m.done() && (m.item.isEmpty() ? m.designation.equals(designation) : Unleashed.is(mob, m.item))) {
                        m.progress++;
                        data(p).dirty = true;
                    }
                }
                completeFinished(p);
            }
        }
    }

    public static void onKill(ServerPlayer p, LivingEntity victim) {
        if (!Unleashed.isAnomaly(victim)) return;
        String designation = Unleashed.designation(victim);
        if ("SCP-049-2".equals(designation)) {
            addXp(p, 5, "SCP-049-2 neutralised");
            for (Mission m : data(p).missions) {
                if (m.type == Mission.Type.TERMINATE && !m.done()) m.progress++;
            }
            data(p).dirty = true;
            completeFinished(p);
        } else {
            addXp(p, 50, designation + " neutralised");
            if (!isAscended(p)) changeStability(p, -3); // the Foundation contains, it doesn't destroy
        }
    }

    /** A breach was announced. Everyone nearby is told, and told to fix it. */
    public static void onBreach(ServerLevel level, BlockPos origin, List<String> names) {
        for (ServerPlayer p : level.players()) {
            if (!p.blockPosition().closerThan(origin, BreachAlerts.ALERT_RADIUS)) continue;
            CareerData d = data(p);
            if (d.ascended) {
                message(p, O5.O5_7, "Master, " + String.join(", ", names) + " has slipped its cell near " + origin.toShortString() + ". It will not touch you. It may touch the rest of us.");
                continue;
            }
            if (d.getRank() == Rank.ADMINISTRATOR) {
                message(p, O5.O5_7, "Administrator: breach of " + String.join(", ", names) + " near " + origin.toShortString() + ". Awaiting your orders - Lockdown or Global Recontainment are on your phone.");
                changeStability(p, -2);
                continue;
            }
            message(p, O5.O5_7, "CONTAINMENT BREACH: " + String.join(", ", names) + " near " + origin.toShortString() + ". Recontain immediately. That is an order.");
            for (String n : names) {
                if (d.missions.size() >= MAX_MISSIONS + 1) break;
                boolean exists = d.missions.stream().anyMatch(m -> m.type == Mission.Type.RECOVER && m.designation.equals(n) && !m.done());
                if (exists) continue;
                Mission m = new Mission();
                m.type = Mission.Type.RECOVER;
                m.giver = 7;
                m.designation = n;
                m.xp = 80;
                m.expires = level.getGameTime() + 20 * MINUTE;
                m.hasPos = true;
                m.x = origin.getX();
                m.y = origin.getY();
                m.z = origin.getZ();
                m.description = "Recontain " + n + " after the breach. Get it inside a powered containment field.";
                d.missions.add(m);
            }
            d.dirty = true;
        }
    }

    // ------------------------------------------------------------------ missions

    private static final String[][] SUPPLIES = {
            {"minecraft:iron_ingot", "8"}, {"minecraft:coal", "16"}, {"minecraft:redstone", "12"}, {"minecraft:wheat", "16"},
            {"minecraft:bread", "10"}, {"minecraft:gold_ingot", "6"}, {"minecraft:obsidian", "8"}, {"minecraft:glass", "24"},
            {"minecraft:diamond", "3"}, {"minecraft:ender_pearl", "4"}, {"minecraft:smooth_stone", "32"}, {"minecraft:book", "6"}};

    /** SCP Unleashed entities the Council would trust you to recover at your rank. Only ones that exist are offered. */
    public static List<String> recoverableFor(Rank r) {
        List<String> list = new ArrayList<>();
        // SCP Unleashed 0.0.5.2 entity IDs; SCP-106 and SCP-682 are stasis-immune there, so they
        // only count when walled into a real cell.
        if (r.atLeast(Rank.LEVEL_2)) list.addAll(List.of(Unleashed.SCP_049, Unleashed.SCP_131, Unleashed.SCP_066));
        if (r.atLeast(Rank.LEVEL_3)) list.addAll(List.of(Unleashed.SCP_173, Unleashed.SCP_457));
        if (r.atLeast(Rank.LEVEL_4)) list.add(Unleashed.SCP_939);
        if (r.atLeast(Rank.LEVEL_5)) list.add(Unleashed.SCP_096);

        list.removeIf(id -> Unleashed.entity(id).isEmpty());
        if (list.isEmpty()) list.add(Unleashed.SCP_049);
        return list;
    }

    /** Research materials Level 2 staff are asked to bring in (SCP Unleashed items, with sensible counts). */
    private static final String[][] RESEARCH = {
            {"scp_unleashed:energum_dust", "8"}, {"scp_unleashed:scp009_fragment", "2"}, {"scp_unleashed:scp002_specimen", "1"},
            {"scp_unleashed:player_blood_sample", "2"}, {"scp_unleashed:unstable_fragment_faint", "2"}, {"scp_unleashed:energum_alloy_ingot", "2"}};

    /**
     * The Council's next assignment. What you get depends on who you are:
     * Class-D are expendable and get sent at the anomalies; Level 1 keeps the site running; Level 2
     * researchers document, gather dossiers and research material; Level 3+ recover and terminate;
     * the higher you go, the less you're put in front of the thing yourself.
     */
    public static void assignMission(ServerPlayer p) {
        CareerData d = data(p);
        Rank r = d.getRank();
        if (r == Rank.ADMINISTRATOR || d.ascended) return;
        boolean overworld = p.level().dimension() == Level.OVERWORLD;
        List<Mission.Type> pool = new ArrayList<>();
        switch (r) {
            case CLASS_D -> { pool.addAll(List.of(Mission.Type.EXPOSURE, Mission.Type.EXPOSURE, Mission.Type.EXPOSURE, Mission.Type.TERMINATE, Mission.Type.SUPPLY)); }
            case LEVEL_1 -> { pool.addAll(List.of(Mission.Type.SUPPLY, Mission.Type.SUPPLY, Mission.Type.CARE, Mission.Type.EXPOSURE)); }
            case LEVEL_2 -> { pool.addAll(List.of(Mission.Type.DOCUMENT, Mission.Type.DOCUMENT, Mission.Type.FILES, Mission.Type.FILES, Mission.Type.SUPPLY)); }
            case LEVEL_3 -> { pool.addAll(List.of(Mission.Type.RECOVER, Mission.Type.TERMINATE, Mission.Type.TERMINATE, Mission.Type.DOCUMENT)); }
            default -> { pool.addAll(List.of(Mission.Type.RECOVER, Mission.Type.RECOVER, Mission.Type.FILES, Mission.Type.SUPPLY)); }
        }
        if (!overworld) pool.removeIf(t -> t != Mission.Type.SUPPLY && t != Mission.Type.FILES);
        if (pool.isEmpty()) pool.add(Mission.Type.SUPPLY);
        assign(p, pool.get(p.getRandom().nextInt(pool.size())), null);
    }

    /** What will actually hold this creature, by SCP Unleashed's own rules. */
    public static String howToContain(Mob m) {
        boolean pod = UnleashedApi.stasisCanHold(m), cell = Unleashed.canBeContained(m);
        if (UnleashedApi.isShyGuy(m)) return "Get the Containment Hood on it" + (pod ? ", or seal it in a stasis pod," : "") + " and bring it to a site.";
        if (pod) return "Seal it in a stasis pod and deliver it to one of your sites" + (cell ? ", or wall it into a cell inside a site." : ".");
        if (cell) return "Stasis won't take it right now. Wall it into a cell inside one of your sites.";
        return "Nothing we have holds it. Keep it away from staff.";
    }

    public static boolean recoverable(Mob m) {
        return UnleashedApi.stasisCanHold(m) || Unleashed.canBeContained(m) || UnleashedApi.isShyGuy(m);
    }

    /** Nearest declared site in your dimension, or null. */
    @org.jetbrains.annotations.Nullable
    public static Sites.Site nearestSite(ServerPlayer p) {
        Sites.Site best = null;
        double bd = Double.MAX_VALUE;
        String dim = p.level().dimension().location().toString();
        for (Sites.Site s : Sites.get(p.server).sites) {
            if (!s.dim.equals(dim)) continue;
            double d = Math.pow(s.x - p.getX(), 2) + Math.pow(s.z - p.getZ(), 2);
            if (d < bd) { bd = d; best = s; }
        }
        return best;
    }

    /** A loose anomaly near you, outside every site, that no assignment of yours covers yet. */
    @org.jetbrains.annotations.Nullable
    /** Where an anomaly needed for something comes from: 0 one of our cells, 1 one loose nearby, 2 out there somewhere. */
    public record Source(Mob mob, Vec3 pos, String where, int kind) {}

    /**
     * An anomaly of this kind, for a task or an order: the one the Foundation holds, if it holds one (from
     * its cell); otherwise one loose close by; otherwise one out there, min..max blocks away, that someone
     * will have to go and find.
     */
    public static Source source(ServerPlayer p, String type, int min, int max) {
        net.minecraft.server.MinecraftServer srv = p.server;
        for (Containment.Entry e : Containment.all(srv)) {
            if (e.status != Containment.HELD || !e.type.equals(type)) continue;
            Mob m = Containment.loaded(srv, e.id);
            return new Source(m, m != null ? m.position() : new Vec3(e.x + 0.5, e.y, e.z + 0.5), e.where(), 0);
        }
        var et = Unleashed.entity(type);
        if (et.isEmpty()) return null;
        ServerLevel l = p.serverLevel();
        Mob near = null;
        for (Mob m : l.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(96), m -> m.getType() == et.get() && m.isAlive() && !Unleashed.isContained(m)))
            if (near == null || m.distanceToSqr(p) < near.distanceToSqr(p)) near = m;
        if (near != null) return new Source(near, near.position(), "near " + near.getBlockX() + ", " + near.getBlockZ(), 1);
        Vec3 spot = surfaceSpot(l, p.blockPosition(), min, max, p.getRandom());
        if (spot == null) return null;
        net.minecraft.world.entity.Entity made = spawnAt(l, et.get(), spot);
        return new Source(made instanceof Mob mm ? mm : null, spot, "near " + (int) spot.x + ", " + (int) spot.z, 2);
    }

    public static Mob looseNear(ServerPlayer p, double range) {
        CareerData d = data(p);
        Mob best = null;
        double bd = range * range;
        for (Mob m : p.level().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(range), Unleashed::isAnomaly)) {
            if (Sites.at(p.level(), m.blockPosition()) != null || Unleashed.isContained(m)) continue;
            ResourceLocation id = Unleashed.idOf(m);
            if (id == null || d.missions.stream().anyMatch(ms -> ms.item.equals(id.getPath()))) continue;
            if (p.distanceToSqr(m) < bd) { bd = p.distanceToSqr(m); best = m; }
        }
        return best;
    }

    /** Builds one assignment of the given type. {@code focus}: a real anomaly near you to build it around. */
    public static void assign(ServerPlayer p, Mission.Type type, @org.jetbrains.annotations.Nullable Mob focus) {
        CareerData d = data(p);
        Rank r = d.getRank();
        RandomSource rnd = p.getRandom();
        ServerLevel level = p.serverLevel();
        Mission m = new Mission();
        m.type = type;
        m.expires = level.getGameTime() + 25 * MINUTE;
        if (focus == null && (type == Mission.Type.EXPOSURE || type == Mission.Type.DOCUMENT || type == Mission.Type.RECOVER)) focus = looseNear(p, 64);
        switch (type) {
            case EXPOSURE -> {
                String id;
                Vec3 spot;
                if (focus != null) {
                    id = Unleashed.idOf(focus).getPath();
                    spot = focus.position();
                } else {
                    List<String> opts = new ArrayList<>(List.of(Unleashed.SCP_173, Unleashed.SCP_049, Unleashed.SCP_066, Unleashed.SCP_131, Unleashed.SCP_999));
                    opts.removeIf(e -> Unleashed.entity(e).isEmpty());
                    if (opts.isEmpty()) return;
                    id = opts.get(rnd.nextInt(opts.size()));
                    // ours, from its cell, if we hold one; one loose nearby; or one out there to be found
                    Source src = source(p, id, 30, 50);
                    if (src == null) return;
                    spot = src.pos();
                }
                m.item = id;
                m.designation = Unleashed.designation(id);
                m.target = r == Rank.CLASS_D ? 30 + rnd.nextInt(4) * 10 : 20;
                m.giver = 5;
                setPos(m, spot);
                m.description = "Test " + d.nextTestNumber() + ": stand within 8m of " + m.designation + " for " + m.target + " seconds. Survive. Report what you saw.";
                message(p, O5.O5_5, address(p) + ", you're on the testing roster. " + m.designation + ", " + m.x + ", " + m.z + ". Get close, stay close, try to come back.");
            }
            case DOCUMENT -> {
                String id;
                Vec3 spot;
                if (focus != null) {
                    id = Unleashed.idOf(focus).getPath();
                    spot = focus.position();
                } else {
                    List<String> opts = new ArrayList<>(List.of(Unleashed.SCP_173, Unleashed.SCP_049, Unleashed.SCP_066, Unleashed.SCP_131, Unleashed.SCP_457, Unleashed.SCP_999));
                    opts.removeIf(e -> Unleashed.entity(e).isEmpty());
                    if (opts.isEmpty()) return;
                    id = opts.get(rnd.nextInt(opts.size()));
                    // ours, from its cell, if we hold one; one loose nearby; or one out there to be found
                    Source src = source(p, id, 35, 60);
                    if (src == null) return;
                    spot = src.pos();
                }
                m.item = id;
                m.designation = Unleashed.designation(id);
                m.target = 1;
                m.giver = 10;
                setPos(m, spot);
                m.description = "Field documentation: photograph " + m.designation + " with your phone camera for its file. Keep your distance.";
                message(p, O5.O5_10, "The file on " + m.designation + " is thin. It was last seen near " + m.x + ", " + m.z + ". Bring me a photograph." + (id.equals(Unleashed.SCP_096) ? " Not its face." : ""));
            }
            case FILES -> {
                m.item = "scp_unleashed:anomaly_document";
                m.target = r.atLeast(Rank.LEVEL_4) ? 3 : 2;
                m.giver = 10;
                m.description = "Archive work: recover " + m.target + " Anomaly Dossiers from an Archive Terminal and submit them from your phone.";
                message(p, O5.O5_10, "Our records are incomplete. Pull " + m.target + " dossiers from an Archive Terminal and send them up. Unread, preferably.");
            }
            case SUPPLY -> {
                if (r == Rank.LEVEL_2 || r.atLeast(Rank.LEVEL_4) && rnd.nextBoolean()) {
                    List<String[]> opts = new ArrayList<>();
                    for (String[] e : RESEARCH) if (BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(e[0]))) opts.add(e);
                    if (!opts.isEmpty()) {
                        String[] e = opts.get(rnd.nextInt(opts.size()));
                        m.item = e[0];
                        m.target = Integer.parseInt(e[1]);
                        m.giver = 10;
                        Item it = BuiltInRegistries.ITEM.get(ResourceLocation.parse(m.item));
                        m.description = "Research requisition: bring in " + m.target + " x " + it.getDescription().getString() + ". Submit from your phone.";
                        message(p, O5.O5_10, "Research needs material: " + m.target + " " + it.getDescription().getString() + ". The lab is waiting.");
                        break;
                    }
                }
                fill(p, m, r, rnd, level);
            }
            case RECOVER -> {
                if (focus != null && recoverable(focus)) {
                    String id = Unleashed.idOf(focus).getPath();
                    m.designation = Unleashed.designation(id);
                    m.item = id;
                    m.giver = rnd.nextBoolean() ? 6 : 8;
                    setPos(m, focus.position());
                    m.description = "Recover " + m.designation + ", loose near " + m.x + ", " + m.z + ". " + howToContain(focus);
                    message(p, O5.seat(m.giver), m.designation + " is loose near you, outside any site. " + howToContain(focus));
                } else {
                    fill(p, m, r, rnd, level);
                }
            }
            default -> fill(p, m, r, rnd, level);
        }
        if (m.description.isEmpty()) return;
        d.missions.add(m);
        d.dirty = true;
    }

    /** The original assignment types (supply, terminate, care, recover by rank). */
    private static void fill(ServerPlayer p, Mission m, Rank r, RandomSource rnd, ServerLevel level) {
        int tier = r.ordinal();
        switch (m.type) {
            case SUPPLY -> {
                String[] s = SUPPLIES[Math.min(SUPPLIES.length - 1, rnd.nextInt(4 + tier * 2))];
                m.item = s[0];
                m.target = Integer.parseInt(s[1]) + tier * 2;
                m.giver = 12;
                m.xp = 25 + tier * 12;
                Item it = BuiltInRegistries.ITEM.get(ResourceLocation.parse(m.item));
                m.description = "Supply run: deliver " + m.target + " x " + it.getDescription().getString() + ". Submit from your phone.";
                message(p, O5.O5_12, "The numbers don't add up, " + address(p) + ". I need " + m.target + " " + it.getDescription().getString() + ". Submit them from your phone.");
            }
            case TERMINATE -> {
                m.target = 3 + tier;
                m.giver = 11;
                m.xp = 30 + m.target * 8;
                Vec3 spot = surfaceSpot(level, p.blockPosition(), 30, 55, rnd);
                if (spot == null) return;
                var cured = Unleashed.entity(Unleashed.SCP_049_2);
                if (cured.isEmpty()) return;
                for (int i = 0; i < m.target; i++) spawnAt(level, cured.get(), spot.add(rnd.nextInt(5) - 2, 0, rnd.nextInt(5) - 2));
                setPos(m, spot);
                m.description = "Terminate " + m.target + " SCP-049-2 instances wandering near " + m.x + ", " + m.z + ".";
                message(p, O5.O5_11, "A group of 049's former patients got out. Termination order signed. Coordinates on your phone.");
            }
            case CARE -> {
                var blob = Unleashed.entity(Unleashed.SCP_999);
                if (blob.isEmpty()) return;
                m.target = 30;
                m.giver = 2;
                m.xp = 50 + tier * 5;
                Source src = source(p, Unleashed.SCP_999, 40, 80);
                if (src == null) return;
                setPos(m, src.pos());
                switch (src.kind()) {
                    case 0 -> {
                        m.description = "Morale protocol: SCP-999 is in " + src.where() + ". Go down and spend 30 seconds with it. It needs the company. So do you.";
                        message(p, O5.O5_2, "SCP-999 has been asking for you from " + src.where() + ". I'm told that's not possible, but here we are. Go and see it.");
                    }
                    case 1 -> {
                        m.description = "Morale protocol: there's an SCP-999 loose " + src.where() + ". Spend 30 seconds close to it.";
                        message(p, O5.O5_2, "There's an SCP-999 wandering " + src.where() + ". It's harmless, it's lonely, and you look like you need it. Go.");
                    }
                    default -> {
                        m.description = "Morale protocol: we don't hold an SCP-999. One was seen " + src.where() + ". Find it and spend 30 seconds close to it.";
                        message(p, O5.O5_2, "We don't have a 999 of our own. A patrol spotted one " + src.where() + ". Someone has to go and find it. You, preferably.");
                    }
                }
            }
            case RECOVER -> {
                List<String> options = recoverableFor(r);
                String entityId = options.get(rnd.nextInt(options.size()));
                var type = Unleashed.entity(entityId);
                if (type.isEmpty()) return;
                m.designation = Unleashed.designation(entityId);
                m.item = entityId;
                m.giver = rnd.nextBoolean() ? 6 : 8;
                m.xp = switch (entityId) {
                    case Unleashed.SCP_682 -> 400;
                    case Unleashed.SCP_096 -> 280;
                    case Unleashed.SCP_106, Unleashed.SCP_939 -> 240;
                    case Unleashed.SCP_173, Unleashed.SCP_457 -> 160;
                    default -> 120;
                };
                Vec3 spot = surfaceSpot(level, p.blockPosition(), 45, 75, rnd);
                if (spot == null) return;
                net.minecraft.world.entity.Entity spawned = spawnAt(level, type.get(), spot);
                setPos(m, spot);
                m.description = "Recover " + m.designation + ", last seen near " + m.x + ", " + m.z + ". "
                        + (spawned instanceof Mob sm ? howToContain(sm) : "Bring it to one of your sites.");
                message(p, O5.seat(m.giver), m.designation + " has surfaced outside containment near " + m.x + ", " + m.z + ". Bring it home. "
                        + switch (m.designation) {
                    case "SCP-173" -> "Do not blink.";
                    case "SCP-096" -> "Do NOT look at its face. Scramble Goggles, and get the Containment Hood on it.";
                    case "SCP-106" -> "It will find you before you find it.";
                    case "SCP-682" -> "Do not engage. Contain it, or run.";
                    case "SCP-939" -> "If you hear a voice you know out there, it isn't them.";
                    default -> "And don't let it touch you.";
                });
            }
            default -> {}
        }
    }


    private static void setPos(Mission m, Vec3 v) {
        m.hasPos = true;
        m.x = (int) Math.floor(v.x);
        m.y = (int) Math.floor(v.y);
        m.z = (int) Math.floor(v.z);
    }

    public static Vec3 surfaceSpot(ServerLevel level, BlockPos around, int min, int max, RandomSource rnd) {
        for (int i = 0; i < 24; i++) {
            double ang = rnd.nextDouble() * Math.PI * 2;
            double dist = min + rnd.nextDouble() * (max - min);
            int x = around.getX() + (int) (Math.cos(ang) * dist);
            int z = around.getZ() + (int) (Math.sin(ang) * dist);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos below = new BlockPos(x, y - 1, z);
            if (y > level.getMinBuildHeight() + 2 && level.getFluidState(below).isEmpty() && level.getFluidState(below.above()).isEmpty()) {
                return new Vec3(x + 0.5, y, z + 0.5);
            }
        }
        return null;
    }

    public static net.minecraft.world.entity.Entity spawnAt(ServerLevel level, EntityType<?> type, Vec3 pos) {
        net.minecraft.world.entity.Entity e = type.create(level);
        if (e == null) return null;
        e.moveTo(pos.x, pos.y, pos.z, level.random.nextFloat() * 360F, 0);
        if (e instanceof Mob mob) mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.EVENT, null);
        level.addFreshEntity(e);
        return e;
    }

    /** Spawns an uncontained SCP near the player and sounds the alarm. */
    public static void breachNear(ServerPlayer p, String entityId) {
        WorldNews.record(p.server, WorldNews.Kind.BREACH, entityId, Unleashed.designation(entityId) + " breached containment near " + p.getGameProfile().getName(), -6, p.getGameProfile().getName());
        ServerLevel level = p.serverLevel();
        var type = Unleashed.entity(entityId);
        Vec3 spot = surfaceSpot(level, p.blockPosition(), 25, 45, p.getRandom());
        if (spot == null || type.isEmpty()) return;
        spawnAt(level, type.get(), spot);
        BreachAlerts.announce(level, BlockPos.containing(spot), List.of(Unleashed.designation(entityId)));
    }

    public static void completeFinished(ServerPlayer p) {
        CareerData d = data(p);
        Iterator<Mission> it = d.missions.iterator();
        List<Mission> finished = new ArrayList<>();
        while (it.hasNext()) {
            Mission m = it.next();
            if (m.done()) {
                finished.add(m);
                it.remove();
            }
        }
        for (Mission m : finished) {
            if (m.type == Mission.Type.FOUNDERS_TRIAL) {
                promote(p, Rank.ADMINISTRATOR);
                continue;
            }
            message(p, O5.seat(m.giver), switch (m.type) {
                case SUPPLY -> "Received. The books balance, for once.";
                case TERMINATE -> "Confirmed. The doctor's patients are at rest.";
                case CARE -> "Morale readings across the site are up 40 percent. Thank you.";
                case RECOVER -> m.designation + " is back where it belongs. Good work.";
                case EXPOSURE -> "Test logged. You came back. That puts you ahead of the last three.";
                case DOCUMENT -> "Photograph received. The " + m.designation + " file is better for it.";
                case FILES -> "Dossiers received and filed. Thank you for not reading them.";
                default -> "Mission complete.";
            });
            commend(p);
            changeStability(p, 2);
        }
        d.dirty = true;
    }

    public static boolean submitSupply(ServerPlayer p, int index) {
        CareerData d = data(p);
        if (index < 0 || index >= d.missions.size()) return false;
        Mission m = d.missions.get(index);
        if (m.type != Mission.Type.SUPPLY && m.type != Mission.Type.FILES) return false;
        if (p.getAbilities().instabuild) { // Creative: hand-in without the items
            m.progress = m.target;
            completeFinished(p);
            return true;
        }
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(m.item));
        int have = 0;
        for (ItemStack s : p.getInventory().items) if (s.is(item)) have += s.getCount();
        if (have < m.target) {
            p.displayClientMessage(Component.literal("You only have " + have + "/" + m.target + ".").withStyle(ChatFormatting.RED), true);
            return false;
        }
        int left = m.target;
        for (ItemStack s : p.getInventory().items) {
            if (left <= 0) break;
            if (s.is(item)) {
                int take = Math.min(left, s.getCount());
                s.shrink(take);
                left -= take;
            }
        }
        m.progress = m.target;
        completeFinished(p);
        return true;
    }

    public static void abandon(ServerPlayer p, int index) {
        CareerData d = data(p);
        if (index < 0 || index >= d.missions.size()) return;
        Mission m = d.missions.get(index);
        if (m.type == Mission.Type.FOUNDERS_TRIAL) return;
        d.missions.remove(index);
        message(p, O5.seat(m.giver), "Noted. It will be on your record.");
        changeStability(p, -2);
    }

    // ------------------------------------------------------------------ periodic

    public static void tick(ServerPlayer p) {
        CareerData d = data(p);
        long now = p.level().getGameTime();
        Rank r = d.getRank();

        // expiry
        Iterator<Mission> it = d.missions.iterator();
        while (it.hasNext()) {
            Mission m = it.next();
            if (m.expires < now) {
                it.remove();
                message(p, O5.seat(m.giver), "Deadline passed: \"" + m.description + "\" Disappointing.");
                changeStability(p, -3);
            }
        }
        // the trial
        for (Mission m : d.missions) {
            if (m.type == Mission.Type.FOUNDERS_TRIAL) {
                Set<String> held = new HashSet<>();
                for (Mob mob : p.level().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(64), Unleashed::canBeContained)) {
                    if (Unleashed.isContained(mob)) held.add(Unleashed.designation(mob));
                }
                if (held.size() != m.progress) {
                    m.progress = held.size();
                    d.dirty = true;
                }
            }
        }
        if (p.tickCount % 100 < 20) scanContainment(p);
        Sites.Site here = Sites.at(p.level(), p.blockPosition());
        for (Mission m : d.missions) {
            if (m.type != Mission.Type.RECOVER || m.stage != 1 || m.done()) continue;
            boolean carrying = false;
            for (net.minecraft.world.item.ItemStack st : p.getInventory().items) {
                String held = UnleashedApi.podContents(st);
                if (held != null && held.endsWith(":" + m.item)) { carrying = true; break; }
            }
            if (!carrying) {
                String held = UnleashedApi.podContents(p.getOffhandItem());
                carrying = held != null && held.endsWith(":" + m.item);
            }
            if (here != null && carrying) {
                m.progress = m.target;
                d.dirty = true;
                message(p, O5.seat(m.giver), m.designation + " delivered to " + here.name + ". Release it into a cell before the pod gives out.");
            } else if (here == null && m.hasPos) {
                Sites.Site site = nearestSite(p);
                if (site != null && (site.x != m.x || site.z != m.z)) { m.x = site.x; m.y = site.y; m.z = site.z; d.dirty = true; }
            }
        }
        for (Mission m : d.missions) {
            if (m.type != Mission.Type.EXPOSURE || m.done()) continue;
            Mob near = null;
            for (Mob mob : p.level().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(8), mob -> Unleashed.is(mob, m.item) && mob.isAlive())) { near = mob; break; }
            if (near != null) {
                m.progress++;
                setPos(m, near.position());
                d.dirty = true;
                if (m.progress % 10 == 0 && !m.done()) p.displayClientMessage(Component.literal("[ TEST ] " + m.progress + "/" + m.target + "s near " + m.designation).withStyle(ChatFormatting.GOLD), true);
            }
        }
        for (Mission m : d.missions) {
            if (m.type == Mission.Type.CARE && !m.done()) {
                var blob = Unleashed.entity(Unleashed.SCP_999);
                if (blob.isPresent() && !p.level().getEntitiesOfClass(net.minecraft.world.entity.Entity.class, p.getBoundingBox().inflate(4), e -> e.getType() == blob.get()).isEmpty()) {
                    m.progress++;
                    d.dirty = true;
                }
            }
        }
        completeFinished(p);

        // new missions
        if (d.nextMission == 0) d.nextMission = now + 600;
        if (now >= d.nextMission) {
            if (d.missions.size() < MAX_MISSIONS) assignMission(p);
            d.nextMission = now + (4 + p.getRandom().nextInt(4)) * MINUTE;
        }
        // calls (council votes / requests for orders)
        // the Council no longer rings out of the blue: meetings go on the calendar (see Calendar)
        Calendar.tick(p, d, now);
        if (d.call != null && Council.inSession(p)) d.call.expires = Math.max(d.call.expires, now + 20 * 60); // a ringing call waits out the meeting
        if (d.call != null && !d.call.outgoing && now > d.call.expires) Calls.timeout(p);
        // flavour intel
        if (d.nextFlavor == 0) d.nextFlavor = now + 6 * MINUTE;
        if (now >= d.nextFlavor) {
            Calls.flavor(p);
            d.nextFlavor = now + (9 + p.getRandom().nextInt(7)) * MINUTE;
        }
        // stability consequences
        if (r.atLeast(Rank.LEVEL_5)) {
            if (d.nextStabilityEvent == 0) d.nextStabilityEvent = now + 10 * MINUTE;
            if (now >= d.nextStabilityEvent) {
                d.nextStabilityEvent = now + 10 * MINUTE;
                if (d.stability < 25 && p.getRandom().nextFloat() < 0.6F && p.level().dimension() == Level.OVERWORLD && !d.ascended) {
                    List<String> opts = recoverableFor(r);
                    message(p, O5.O5_9, "Site stability is collapsing. I saw this coming. Brace yourself.");
                    breachNear(p, opts.get(p.getRandom().nextInt(opts.size())));
                } else if (d.stability > 85) {
                    message(p, O5.O5_12, "Stability is excellent. The budget surplus has been sent " + (Secretary.has(p) ? "to your secretary." : "to you."));
                    budget(p, Unleashed.item("amnestic_canister", Items.GOLDEN_APPLE), 2);
                    budget(p, Items.DIAMOND, 2);
                }
            }
        }
        if (d.dirty) sync(p);
    }

    // ------------------------------------------------------------------ sync

    public static void sync(ServerPlayer p) {
        CareerData d = data(p);
        d.dirty = false;
        CompoundTag t = d.save();
        Rank r = d.getRank();
        t.putString("title", d.ascended ? "SCP-Ω \"The Ascendant\"" : r.title);
        t.putString("personnel", personnelId(p));
        Rank next = r.next();
        t.putInt("toNext", next == null ? -1 : Math.max(0, next.xpRequired - d.xp));
        t.putLong("now", p.level().getGameTime());
        t.putLong("dayNow", p.server.overworld().getDayTime());
        if (d.call != null) {
            Calls.Template tpl = Calls.get(d.call.templateId);
            if (tpl != null) {
                t.putString("callFrom", O5.seat(d.call.caller).sender());
                t.putString("callText", Calls.framing(d) + tpl.text());
                ListTag opts = new ListTag();
                for (Calls.Option o : tpl.options()) opts.add(StringTag.valueOf(o.label()));
                t.put("callOptions", opts);
                t.putBoolean("callOutgoing", d.call.outgoing);
                t.putInt("callSeat", d.call.caller);
            }
        }
        ListTag dirs = new ListTag();
        for (Directives.Directive dir : Directives.available(d)) {
            CompoundTag dt = new CompoundTag();
            dt.putString("id", dir.id());
            dt.putString("label", dir.label());
            dt.putString("desc", dir.description());
            dt.putLong("cd", Math.max(0, d.cooldowns.getOrDefault(dir.id(), 0L) - p.level().getGameTime()) / 20);
            dirs.add(dt);
        }
        t.put("directives", dirs);
        if (r.atLeast(Rank.OVERSEER) || d.ascended) {
            BlockPos s = Site01.location(p.server.overworld());
            t.putString("site", s.getX() + ", " + s.getY() + ", " + s.getZ());
            t.putBoolean("inSite", Site01.isInside(p));
        }
        ListTag near = new ListTag();
        List<Mob> found = p.level().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(64), Unleashed::isAnomaly);
        found.sort(java.util.Comparator.comparingDouble(p::distanceToSqr));
        for (Mob mob : found.subList(0, Math.min(16, found.size()))) {
            CompoundTag at = new CompoundTag();
            at.putString("d", Unleashed.designation(mob));
            at.putString("o", Unleashed.objectClass(mob));
            at.putString("dk", mob.getType().getDescriptionId());
            at.putBoolean("c", Unleashed.isContained(mob));
            at.putBoolean("k", Unleashed.canBeContained(mob));
            at.putString("n", UnleashedApi.isShyGuy(mob) && UnleashedApi.isBagged(mob) ? "Hooded" : "");
            at.putInt("m", (int) Math.sqrt(p.distanceToSqr(mob)));
            at.putInt("id", mob.getId());
            Sites.Site site = Sites.at(p.level(), mob.blockPosition());
            at.putString("site", site == null ? "" : site.name);
            Sites.Cell cell = Sites.cellAt(p.level(), mob.blockPosition());
            if (cell != null) at.putString("cell", cell.name + (cell.entity.equals(Unleashed.idOf(mob).getPath()) ? "" : " (wrong cell)"));
            at.putString("type", net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString());
            at.putInt("x", mob.getBlockX());
            at.putInt("y", mob.getBlockY());
            at.putInt("z", mob.getBlockZ());
            near.add(at);
        }
        t.put("nearby", near);
        ListTag rifts = new ListTag();
        for (net.minecraft.world.entity.Entity rift : Clearance.linked(p, "rifts") ? p.level().getEntities(p, p.getBoundingBox().inflate(128), UnleashedApi::isRift) : List.<net.minecraft.world.entity.Entity>of()) {
            if (rifts.size() >= 12) break;
            CompoundTag rt = new CompoundTag();
            rt.putInt("id", rift.getId());
            rt.putString("type", net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(rift.getType()).toString());
            rt.putString("dk", rift.getType().getDescriptionId());
            rt.putString("rarity", UnleashedApi.riftLabel(rift));
            rt.putInt("colour", UnleashedApi.riftColour(rift));
            rt.putFloat("ex", UnleashedApi.riftExtraction(rift));
            rt.putBoolean("can", UnleashedApi.riftExtractable(rift));
            rt.putInt("x", rift.getBlockX());
            rt.putInt("y", rift.getBlockY());
            rt.putInt("z", rift.getBlockZ());
            rt.putInt("m", (int) Math.sqrt(p.distanceToSqr(rift)));
            rifts.add(rt);
        }
        t.put("rifts", rifts);
        com.breachprotocol.network.Command.addToSync(p, t);
        Contacts.addToSync(p, t);
        Sites.addToSync(p, t);
        Containment.addToSync(p, t);
        Dominion.addToSync(p, t);
        t.putInt("trial", data(p).trial);
        t.putInt("trialKills", data(p).trialKills);
        if (rank(p) == Rank.ADMINISTRATOR || isAscended(p)) Forces.addToSync(p, t);
        RiftTeams.addToSync(p, t);
        PacketDistributor.sendToPlayer(p, new CareerSyncPayload(t));
    }

    public static void effect(ServerPlayer p, MobEffectInstance e) {
        p.addEffect(e);
    }
}
