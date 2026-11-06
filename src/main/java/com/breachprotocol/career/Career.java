package com.breachprotocol.career;

import com.breachprotocol.entity.Anomaly;
import com.breachprotocol.event.BreachAlerts;
import com.breachprotocol.network.CareerSyncPayload;
import com.breachprotocol.registry.ModAttachments;
import com.breachprotocol.registry.ModEffects;
import com.breachprotocol.registry.ModEntities;
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

    public static String personnelId(Player p) {
        return "D-" + (1000 + Math.abs(p.getUUID().hashCode() % 9000));
    }

    // ------------------------------------------------------------------ messages

    public static void message(ServerPlayer p, String sender, String body) {
        CareerData d = data(p);
        d.addMessage(sender, body, p.level().getGameTime());
        p.sendSystemMessage(Component.literal("☎ ").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(sender + ": ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(body.length() > 90 ? body.substring(0, 88) + "..." : body).withStyle(ChatFormatting.GRAY)));
        p.playNotifySound(ModSounds.PHONE_MESSAGE.get(), SoundSource.PLAYERS, 0.8F, 1.0F);
    }

    public static void message(ServerPlayer p, O5 from, String body) {
        message(p, from.sender(), body);
    }

    // ------------------------------------------------------------------ xp + ranks

    public static void addXp(ServerPlayer p, int amount, String reason) {
        CareerData d = data(p);
        if (d.getRank() == Rank.ADMINISTRATOR && amount > 0) {
            // The Administrator's "xp" is the Foundation's stability instead.
            changeStability(p, Math.max(1, amount / 30));
            return;
        }
        d.xp = Math.max(d.getRank().xpRequired, d.xp + amount);
        d.dirty = true;
        p.displayClientMessage(Component.literal((amount >= 0 ? "+" : "") + amount + " Clearance XP  ").withStyle(amount >= 0 ? ChatFormatting.GREEN : ChatFormatting.RED)
                .append(Component.literal(reason).withStyle(ChatFormatting.GRAY)), true);
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
            case LEVEL_1 -> message(p, O5.O5_3, "> PERSONNEL FILE UPDATED. " + personnelId(p) + " RECLASSIFIED: STAFF. CLEARANCE 1. YOUR D-CLASS TERMINATION DATE HAS BEEN... POSTPONED.");
            case LEVEL_3 -> message(p, O5.O5_6, "You've got a steady hand. Security is yours now. Keep the doors shut and the lights on.");
            case LEVEL_5 -> message(p, O5.O5_7, "Site Director. Your site, your responsibility. The Council will be watching. Lockdown authority has been added to your phone.");
            case OVERSEER -> {
                message(p, O5.O5_7, "Welcome to the Council, Thirteen. When we are divided, you decide. Expect calls.");
                broadcast(p.server, Component.literal("[O5 COMMAND] ").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
                        .append(Component.literal(p.getName().getString() + " has taken the seat of O5-13.").withStyle(ChatFormatting.RED)));
            }
            case ADMINISTRATOR -> {
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
        return switch (level) {
            case 1 -> ModItems.KEYCARD_1.get();
            case 2 -> ModItems.KEYCARD_2.get();
            case 3 -> ModItems.KEYCARD_3.get();
            case 4 -> ModItems.KEYCARD_4.get();
            default -> ModItems.KEYCARD_5.get();
        };
    }

    public static void give(ServerPlayer p, Item item, int count) {
        ItemStack s = new ItemStack(item, count);
        if (!p.getInventory().add(s)) p.drop(s, false);
    }

    public static void broadcast(net.minecraft.server.MinecraftServer server, Component c) {
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) sp.sendSystemMessage(c);
    }

    // ------------------------------------------------------------------ hooks from gameplay

    /** A containment field just took hold of an anomaly. */
    public static void onContained(ServerLevel level, BlockPos generator, Mob mob) {
        if (!(mob instanceof Anomaly a)) return;
        long now = level.getGameTime();
        CompoundTag pd = mob.getPersistentData();
        boolean fresh = now - pd.getLong("bp_last_contained") > 5 * MINUTE || !pd.contains("bp_last_contained");
        pd.putLong("bp_last_contained", now);
        if (!fresh) return;
        int reward = switch (a.objectClass()) {
            case "Safe" -> 10;
            case "Keter" -> 60;
            default -> 30;
        };
        for (ServerPlayer p : level.players()) {
            if (p.blockPosition().closerThan(generator, 48)) {
                addXp(p, reward, a.designation() + " contained");
                for (Mission m : data(p).missions) {
                    if (m.type == Mission.Type.RECOVER && !m.done() && m.designation.equals(a.designation())) {
                        m.progress++;
                        data(p).dirty = true;
                    }
                }
                completeFinished(p);
            }
        }
    }

    public static void onKill(ServerPlayer p, LivingEntity victim) {
        if (!(victim instanceof Anomaly a)) return;
        if ("SCP-049-2".equals(a.designation())) {
            addXp(p, 5, "SCP-049-2 neutralised");
            for (Mission m : data(p).missions) {
                if (m.type == Mission.Type.TERMINATE && !m.done()) m.progress++;
            }
            data(p).dirty = true;
            completeFinished(p);
        } else {
            addXp(p, 50, a.designation() + " neutralised");
            if (!isAscended(p)) changeStability(p, -3); // the Foundation contains, it doesn't destroy
        }
    }

    public static void onHug(ServerPlayer p) {
        boolean any = false;
        for (Mission m : data(p).missions) {
            if (m.type == Mission.Type.CARE && !m.done()) {
                m.progress++;
                any = true;
            }
        }
        if (any) {
            data(p).dirty = true;
            completeFinished(p);
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

    public static Supplier<? extends EntityType<? extends Mob>> typeFor(String designation) {
        return switch (designation) {
            case "SCP-173" -> ModEntities.SCP_173;
            case "SCP-096" -> ModEntities.SCP_096;
            case "SCP-106" -> ModEntities.SCP_106;
            case "SCP-999" -> ModEntities.SCP_999;
            case "SCP-049-2" -> ModEntities.SCP_049_2;
            default -> ModEntities.SCP_049;
        };
    }

    /** SCPs the Council would trust you with at your rank. */
    public static List<String> recoverableFor(Rank r) {
        List<String> list = new ArrayList<>();
        if (r.atLeast(Rank.LEVEL_2)) list.add("SCP-049");
        if (r.atLeast(Rank.LEVEL_3)) list.add("SCP-173");
        if (r.atLeast(Rank.LEVEL_4)) list.add("SCP-106");
        if (r.atLeast(Rank.LEVEL_5)) list.add("SCP-096");
        if (list.isEmpty()) list.add("SCP-049");
        return list;
    }

    public static void assignMission(ServerPlayer p) {
        CareerData d = data(p);
        Rank r = d.getRank();
        if (r == Rank.ADMINISTRATOR || d.ascended) return;
        RandomSource rnd = p.getRandom();
        ServerLevel level = p.serverLevel();
        boolean overworld = level.dimension() == Level.OVERWORLD;
        List<Mission.Type> pool = new ArrayList<>(List.of(Mission.Type.SUPPLY, Mission.Type.SUPPLY));
        if (overworld) {
            pool.add(Mission.Type.TERMINATE);
            if (r.atLeast(Rank.LEVEL_1)) pool.add(Mission.Type.CARE);
            if (r.atLeast(Rank.LEVEL_2)) { pool.add(Mission.Type.RECOVER); pool.add(Mission.Type.RECOVER); }
        }
        Mission m = new Mission();
        m.type = pool.get(rnd.nextInt(pool.size()));
        m.expires = level.getGameTime() + 25 * MINUTE;
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
                message(p, O5.O5_12, "The numbers don't add up, " + personnelId(p) + ". I need " + m.target + " " + it.getDescription().getString() + ". Submit them from your phone.");
            }
            case TERMINATE -> {
                m.target = 3 + tier;
                m.giver = 11;
                m.xp = 30 + m.target * 8;
                Vec3 spot = surfaceSpot(level, p.blockPosition(), 30, 55, rnd);
                if (spot == null) return;
                for (int i = 0; i < m.target; i++) spawnAt(level, ModEntities.SCP_049_2.get(), spot.add(rnd.nextInt(5) - 2, 0, rnd.nextInt(5) - 2));
                setPos(m, spot);
                m.description = "Terminate " + m.target + " SCP-049-2 instances wandering near " + m.x + ", " + m.z + ".";
                message(p, O5.O5_11, "A group of 049's former patients got out. Termination order signed. Coordinates on your phone.");
            }
            case CARE -> {
                m.target = 3;
                m.giver = 2;
                m.xp = 50 + tier * 5;
                boolean have = !level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(64), e -> e.getType() == ModEntities.SCP_999.get()).isEmpty();
                if (!have) {
                    Vec3 spot = surfaceSpot(level, p.blockPosition(), 10, 20, rnd);
                    if (spot == null) return;
                    spawnAt(level, ModEntities.SCP_999.get(), spot);
                    setPos(m, spot);
                }
                m.description = "Morale protocol: give SCP-999 three hugs (right-click it). It needs it. So do you.";
                message(p, O5.O5_2, "SCP-999 has been asking for you. I'm told that's not possible, but here we are. Go and see it.");
            }
            case RECOVER -> {
                List<String> options = recoverableFor(r);
                m.designation = options.get(rnd.nextInt(options.size()));
                m.giver = rnd.nextBoolean() ? 6 : 8;
                m.xp = switch (m.designation) {
                    case "SCP-096" -> 280;
                    case "SCP-106" -> 240;
                    case "SCP-173" -> 160;
                    default -> 120;
                };
                Vec3 spot = surfaceSpot(level, p.blockPosition(), 45, 75, rnd);
                if (spot == null) return;
                spawnAt(level, typeFor(m.designation).get(), spot);
                setPos(m, spot);
                m.description = "Recover " + m.designation + ", last seen near " + m.x + ", " + m.z + ". Bring it inside a powered Containment Field.";
                message(p, O5.seat(m.giver), m.designation + " has surfaced outside containment near " + m.x + ", " + m.z + ". Bring it home. "
                        + switch (m.designation) {
                    case "SCP-173" -> "Do not blink.";
                    case "SCP-096" -> "Do NOT look at its face. Wear a pumpkin if you have to.";
                    case "SCP-106" -> "It will find you before you find it.";
                    default -> "And don't let it touch you.";
                });
            }
            default -> {}
        }
        d.missions.add(m);
        d.dirty = true;
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

    public static Mob spawnAt(ServerLevel level, EntityType<? extends Mob> type, Vec3 pos) {
        Mob mob = type.create(level);
        if (mob == null) return null;
        mob.moveTo(pos.x, pos.y, pos.z, level.random.nextFloat() * 360F, 0);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.EVENT, null);
        level.addFreshEntity(mob);
        return mob;
    }

    /** Spawns an uncontained SCP near the player and sounds the alarm. */
    public static void breachNear(ServerPlayer p, String designation) {
        ServerLevel level = p.serverLevel();
        Vec3 spot = surfaceSpot(level, p.blockPosition(), 25, 45, p.getRandom());
        if (spot == null) return;
        spawnAt(level, typeFor(designation).get(), spot);
        BreachAlerts.announce(level, BlockPos.containing(spot), List.of(designation));
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
                default -> "Mission complete.";
            });
            addXp(p, m.xp, "mission complete");
            changeStability(p, 2);
        }
        d.dirty = true;
    }

    public static boolean submitSupply(ServerPlayer p, int index) {
        CareerData d = data(p);
        if (index < 0 || index >= d.missions.size()) return false;
        Mission m = d.missions.get(index);
        if (m.type != Mission.Type.SUPPLY) return false;
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
        addXp(p, -10, "mission abandoned");
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
                d.xp = Math.max(r.xpRequired, d.xp - 10);
                changeStability(p, -3);
            }
        }
        // the trial
        for (Mission m : d.missions) {
            if (m.type == Mission.Type.FOUNDERS_TRIAL) {
                Set<String> held = new HashSet<>();
                for (Mob mob : p.level().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(64), e -> e instanceof Anomaly a && a.canBeContained())) {
                    if (ModEffects.has(mob, ModEffects.CONTAINED)) held.add(((Anomaly) mob).designation());
                }
                if (held.size() != m.progress) {
                    m.progress = held.size();
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
        if (r.atLeast(Rank.OVERSEER)) {
            if (d.nextCall == 0) d.nextCall = now + 2 * MINUTE;
            if (now >= d.nextCall && d.call == null) {
                Calls.startRandom(p);
                d.nextCall = now + (r == Rank.ADMINISTRATOR ? 4 + p.getRandom().nextInt(3) : 7 + p.getRandom().nextInt(4)) * MINUTE;
            }
        }
        if (d.call != null && now > d.call.expires) Calls.timeout(p);
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
                    message(p, O5.O5_12, "Stability is excellent. The budget surplus is in your inventory.");
                    give(p, ModItems.EYEDROPS.get(), 2);
                    give(p, Items.DIAMOND, 2);
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
        t.putInt("nextXp", next == null ? -1 : next.xpRequired);
        t.putInt("curXp", r.xpRequired);
        t.putLong("now", p.level().getGameTime());
        if (d.call != null) {
            Calls.Template tpl = Calls.get(d.call.templateId);
            if (tpl != null) {
                t.putString("callFrom", O5.seat(d.call.caller).sender());
                t.putString("callText", Calls.framing(d) + tpl.text());
                ListTag opts = new ListTag();
                for (Calls.Option o : tpl.options()) opts.add(StringTag.valueOf(o.label()));
                t.put("callOptions", opts);
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
        if (r == Rank.ADMINISTRATOR) {
            BlockPos s = Site01.location(p.server.overworld());
            t.putString("site", s.getX() + ", " + s.getY() + ", " + s.getZ());
            t.putBoolean("inSite", Site01.isInside(p));
        }
        PacketDistributor.sendToPlayer(p, new CareerSyncPayload(t));
    }

    public static void effect(ServerPlayer p, MobEffectInstance e) {
        p.addEffect(e);
    }
}
