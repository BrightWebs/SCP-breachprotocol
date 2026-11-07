package com.breachprotocol.compat;

import com.breachprotocol.BreachProtocol;
import com.google.gson.JsonElement;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.lang.reflect.*;
import java.util.Map;

/**
 * Plugs the Foundation into Minecraft Comes Alive's own dialogue system (its data-driven
 * questions and answers): a "bp" condition that dialogue results can test (is this villager on our
 * payroll, has the player the clearance, has the villager seen something), and a "bp" action that
 * runs our code (and can say a line composed on the spot into MCA's own conversation screen).
 * All by reflection: nothing here loads unless MCA is installed.
 */
public final class McaHooks {
    private McaHooks() {}

    private static Condition condition;
    private static Action action;

    public interface Condition { boolean test(String key, Entity villager, ServerPlayer player); }

    public interface Action { void run(String key, Entity villager, ServerPlayer player); }

    /** Registers the "bp" condition and action with MCA (call once, before data packs load). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void install(Condition c, Action a) {
        if (!McaCompat.present()) return;
        condition = c;
        action = a;
        try {
            ClassLoader cl = McaCompat.class.getClassLoader();
            Class<?> gp = Class.forName("net.conczin.mca.entity.interaction.gifts.GiftPredicate", true, cl);
            Class<?> condFactory = Class.forName("net.conczin.mca.entity.interaction.gifts.GiftPredicate$Factory", true, cl);
            Class<?> condIface = Class.forName("net.conczin.mca.entity.interaction.gifts.GiftPredicate$Condition", true, cl);
            Map conds = (Map) gp.getField("CONDITION_TYPES").get(null);
            conds.put("bp", Proxy.newProxyInstance(cl, new Class<?>[]{condFactory}, (proxy, m, args) -> {
                if (m.isDefault()) return InvocationHandler.invokeDefault(proxy, m, args);
                if (!m.getName().equals("parse")) return basic(proxy, m, args);
                String key = ((JsonElement) args[0]).getAsString();
                return Proxy.newProxyInstance(cl, new Class<?>[]{condIface}, (p2, m2, a2) -> {
                    if (m2.isDefault()) return InvocationHandler.invokeDefault(p2, m2, a2);
                    if (!m2.getName().equals("test")) return basic(p2, m2, a2);
                    try { return condition.test(key, (Entity) a2[0], (ServerPlayer) a2[2]) ? 1.0F : 0.0F; }
                    catch (Throwable t) { BreachProtocol.LOGGER.error("Foundation dialogue condition {} failed", key, t); return 0.0F; }
                });
            }));
            Class<?> acts = Class.forName("net.conczin.mca.resources.data.dialogue.Actions", true, cl);
            Class<?> actFactory = Class.forName("net.conczin.mca.resources.data.dialogue.Actions$Factory", true, cl);
            Class<?> actIface = Class.forName("net.conczin.mca.resources.data.dialogue.Actions$Action", true, cl);
            Map types = (Map) acts.getField("TYPES").get(null);
            types.put("bp", Proxy.newProxyInstance(cl, new Class<?>[]{actFactory}, (proxy, m, args) -> {
                if (m.isDefault()) return InvocationHandler.invokeDefault(proxy, m, args);
                if (!m.getName().equals("parse")) return basic(proxy, m, args);
                String key = ((JsonElement) args[0]).getAsString();
                return Proxy.newProxyInstance(cl, new Class<?>[]{actIface}, (p2, m2, a2) -> {
                    if (m2.isDefault()) return InvocationHandler.invokeDefault(p2, m2, a2);
                    if (!m2.getName().equals("trigger")) return basic(p2, m2, a2);
                    try { action.run(key, (Entity) a2[0], (ServerPlayer) a2[1]); }
                    catch (Throwable t) { BreachProtocol.LOGGER.error("Foundation dialogue action {} failed", key, t); }
                    return null;
                });
            }));
            BreachProtocol.LOGGER.info("Breach Protocol: hooked into Minecraft Comes Alive's dialogue");
        } catch (Throwable t) {
            BreachProtocol.LOGGER.error("Breach Protocol: couldn't hook into Minecraft Comes Alive's dialogue", t);
        }
    }

    private static Object basic(Object proxy, Method m, Object[] args) {
        return switch (m.getName()) {
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            case "toString" -> "BreachProtocol hook";
            default -> null;
        };
    }

    // ================================================================== saying things in MCA's screen

    private static Constructor<?> questionResponse;
    private static Method sendToPlayer;

    /** Puts a line in the villager's speech area of MCA's interaction screen. */
    public static void say(ServerPlayer p, Component text) {
        try {
            if (questionResponse == null) {
                Class<?> qr = Class.forName("net.conczin.mca.network.s2c.InteractionDialogueQuestionResponse");
                questionResponse = qr.getConstructor(Component.class, boolean.class);
                Class<?> net = Class.forName("net.conczin.mca.network.Network");
                for (Method m : net.getMethods()) if (m.getName().equals("sendToPlayer") && m.getParameterCount() == 2) sendToPlayer = m;
            }
            sendToPlayer.invoke(null, questionResponse.newInstance(text, false), p);
        } catch (Throwable t) {
            p.sendSystemMessage(text);
        }
    }

    // ================================================================== villager state

    private static Object call(Object o, String name, Object... args) {
        if (o == null) return null;
        try {
            for (Method m : o.getClass().getMethods())
                if (m.getName().equals(name) && m.getParameterCount() == args.length) return m.invoke(o, args);
        } catch (Throwable ignored) {}
        return null;
    }

    /** MCA hearts (-100..100) the villager has for this player. */
    public static int hearts(Entity v, ServerPlayer p) {
        Object mem = call(call(v, "getVillagerBrain"), "getMemoriesForPlayer", p);
        Object h = call(mem, "getHearts");
        return h instanceof Integer i ? i : 0;
    }

    public static void reward(Entity v, ServerPlayer p, int hearts) { call(call(v, "getVillagerBrain"), "rewardHearts", p, hearts); }

    public static void mood(Entity v, int delta) { call(call(v, "getVillagerBrain"), "modifyMoodValue", delta); }

    /** MCA mood name ("happy", "sad", ...). */
    public static String moodName(Entity v) {
        Object m = call(call(v, "getVillagerBrain"), "getMood");
        Object n = call(m, "getName");
        return n == null ? "" : n.toString().toLowerCase(java.util.Locale.ROOT);
    }

    public static void setName(Entity v, String name) { call(v, "setName", name); }

    /** Sets one of MCA's genes by field name (SKIN, MELANIN...). */
    public static void setGene(Entity v, String field, float value) {
        try {
            Object gen = call(v, "getGenetics");
            Class<?> gc = Class.forName("net.conczin.mca.entity.ai.Genetics");
            Object type = gc.getField(field).get(null);
            for (Method m : gen.getClass().getMethods()) if (m.getName().equals("setGene") && m.getParameterCount() == 2) m.invoke(gen, type, value);
        } catch (Throwable ignored) {}
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void setGender(Entity v, boolean female) {
        try {
            Object gen = call(v, "getGenetics");
            Class gender = Class.forName("net.conczin.mca.entity.ai.relationship.Gender");
            Object g = Enum.valueOf(gender, female ? "FEMALE" : "MALE");
            for (Method m : gen.getClass().getMethods()) if (m.getName().equals("setGender") && m.getParameterCount() == 1) m.invoke(gen, g);
        } catch (Throwable ignored) {}
    }

    /** A new MCA villager of this sex (not yet added to the world), or null. */
    public static net.minecraft.world.entity.Mob create(net.minecraft.server.level.ServerLevel l, boolean female) {
        var type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getOptional(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("mca", female ? "female_villager" : "male_villager"));
        if (type.isEmpty()) return null;
        Entity e = type.get().create(l);
        return e instanceof net.minecraft.world.entity.Mob m ? m : null;
    }
}
