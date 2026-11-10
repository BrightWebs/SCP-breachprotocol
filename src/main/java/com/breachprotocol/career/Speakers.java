package com.breachprotocol.career;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.compat.Unleashed;
import com.breachprotocol.compat.UnleashedApi;
import com.breachprotocol.entity.Dialogue;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.lang.reflect.Method;
import java.util.Random;

/**
 * The anomalies that can speak know who the Administrator is, and know what SCP-Ω is, and say so,
 * each in its own way (the files' own temperament: 343's warm curiosity, Cain's precision, the
 * Doctor's courtesy, Able's respect for strength, 682's contempt). Right-click one with an empty hand.
 * Each will do what it can be asked to do: go back to its cell, walk with you, stay, and its own
 * thing besides. SCP-343 above all: he's never taken an order, but he'll grant a request.
 */
@EventBusSubscriber(modid = BreachProtocol.MODID)
public final class Speakers {
    private Speakers() {}

    private static final Random R = new Random();

    private static String pick(String... s) { return s[R.nextInt(s.length)]; }

    private static String kind(Entity e) {
        var id = Unleashed.idOf(e);
        if (id == null) return "";
        String p = id.getPath();
        if (p.equals("scp343")) return "343";
        if (p.equals("scp049")) return "049";
        if (p.equals("scp073_cain")) return "073";
        if (p.equals("scp076")) return "076";
        if (p.equals("scp682")) return "682";
        if (p.equals("scp939")) return "939";
        if (p.equals("scp035_host")) return "035";
        return "";
    }

    static boolean chief(ServerPlayer p) { return Career.rank(p) == Rank.ADMINISTRATOR || Career.isAscended(p); }

    @SubscribeEvent
    public static void interact(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide || event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(event.getEntity() instanceof ServerPlayer p) || !chief(p) || !p.getMainHandItem().isEmpty() || p.isShiftKeyDown()) return;
        Entity e = event.getTarget();
        String k = kind(e);
        if (k.isEmpty()) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        open(p, e, k);
    }

    private static void open(ServerPlayer p, Entity e, String k) {
        boolean omega = Career.isAscended(p);
        int id = e.getId();
        String title = switch (k) {
            case "343" -> "SCP-343 · \"God\"";
            case "049" -> "SCP-049 · \"The Plague Doctor\"";
            case "073" -> "SCP-073 · \"Cain\"";
            case "076" -> "SCP-076-2 · \"Able\"";
            case "682" -> "SCP-682 · \"The Hard-to-Destroy Reptile\"";
            case "939" -> "SCP-939 · \"With Many Voices\"";
            default -> "SCP-035 · \"The Possessive Mask\"";
        };
        String sub = switch (k) {
            case "343" -> "Safe · he regards you with open interest";
            case "049" -> "Euclid · he inclines his head";
            case "073" -> "Euclid · he looks at you as if reading a file";
            case "076" -> "Keter · he measures you";
            case "682" -> "Keter · it hates you, and knows better than to act on it";
            case "939" -> "Keter · its many voices go quiet";
            default -> "Keter · the mask's smile is fixed on you";
        };
        Dialogue.begin(p, "speak:" + id, title, sub, id);
        String who = Unleashed.designation(e);
        Dialogue.line(p, who, greeting(k, p, omega), 0xF0E6C8);
        if (k.equals("343")) {
            Dialogue.option(p, "\"Bless me.\"", "/bpscp bless " + id, "His gifts, if he thinks you're due them", 0xFFE08A);
            Dialogue.option(p, "\"Heal everyone here.\"", "/bpscp heal " + id, "Every hurt and every affliction nearby, gone", 0x8AFFB0);
            Dialogue.option(p, "\"Judge those who'd hurt my people.\"", "/bpscp judge " + id, "He strikes down what's hunting people here", 0xFF9A6A);
            Dialogue.option(p, "\"Walk with me.\"", "/bpscp walk " + id, "He comes along, as long as you like", 0xA8D8FF);
            Dialogue.option(p, "\"Stay. We'd like you here.\"", "/bpscp stay " + id, "He won't leave on his own", 0xA8D8FF);
            Dialogue.option(p, "\"Go wherever you like.\"", "/bpscp free " + id, "He steps out through the wall", 0xC0C0C0);
            Dialogue.option(p, "Just talk", "/bpscp talk " + id, "His own conversation", 0x9AA4B2);
        } else {
            Dialogue.option(p, "\"Back to your cell.\"", "/bpscp cell " + id, "It goes back where it's held", 0xA8D8FF);
            Dialogue.option(p, "\"Walk with me.\"", "/bpscp walk " + id, "It follows you", 0xA8D8FF);
            Dialogue.option(p, "\"Stay here.\"", "/bpscp stay " + id, "It holds where it is", 0xA8D8FF);
            switch (k) {
                case "073" -> Dialogue.option(p, "\"What's near us, Cain?\"", "/bpscp tell " + id, "Every anomaly within reach, marked for you", 0xFFE08A);
                case "682" -> Dialogue.option(p, "\"Be still.\"", "/bpscp still " + id, "It lies down and seals itself in its own hide", 0xFF9A6A);
                case "939" -> Dialogue.option(p, "\"Stop pretending.\"", "/bpscp mute " + id, "The borrowed voices stop", 0xFF9A6A);
                case "049" -> Dialogue.option(p, "\"Leave your patients be, Doctor.\"", "/bpscp patients " + id, "He calls his cured back to him", 0xFF9A6A);
                default -> {}
            }
            Dialogue.option(p, "Leave", "close", "Walk away", 0x9AA4B2);
        }
        Dialogue.send(p, true);
    }

    private static String greeting(String k, ServerPlayer p, boolean omega) {
        String n = p.getGameProfile().getName();
        return switch (k) {
            case "343" -> omega ? pick("You went through the Gate. I wondered who would. Sit with me a while, I'd like to hear what you saw.",
                    "There you are. You've become something interesting, haven't you? Most people only become tired.",
                    "SCP-Omega. They gave you a number too. Don't let it go to your head. Mine didn't.")
                    : pick("Ah. The Administrator. I wondered when you'd come down to see me yourself.",
                    "You run all of this. That's a lot to carry, " + n + ". Shall I make it lighter?",
                    "Hello, Administrator. Your people keep calling this a cell. I don't mind. I like the company.");
            case "049" -> omega ? "Ah. You are not ill. You are... something else entirely. I bow to it." : pick("Administrator. You keep a clean house. The Pestilence has not reached you.",
                    "Good day, Administrator. You may rest easy; you show no symptoms.");
            case "073" -> omega ? "You're in my records now under a new heading. Very few things I've catalogued have changed this much." : pick("Administrator. Everything here is recorded. I'll tell you anything you need to know.",
                    "I know the floor plan, the staffing, the incident logs. Ask, and I'll answer.");
            case "076" -> omega ? "So. You are the one. I would have fought you. I'm glad I don't have to know how it ends." : "You command the ones who keep me. I respect that. Don't make me test it.";
            case "682" -> omega ? "...You. Even you are disgusting. But you are the one thing here I cannot outlast. Speak." : "You stink of all of them. What do you want, keeper?";
            case "939" -> "(A dozen voices you almost recognise say, at once:) \"We know you. We won't use yours.\"";
            default -> omega ? "The wearer's mouth smiles wider. \"You'd be such a lovely host. I know I'm not allowed.\"" : "\"Administrator. We could do such things together. No? Pity.\"";
        };
    }

    // ================================================================== what they'll do

    @SubscribeEvent
    public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("bpscp")
                .then(Commands.argument("what", StringArgumentType.word()).then(Commands.argument("id", IntegerArgumentType.integer()).executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    Entity e = p.serverLevel().getEntity(IntegerArgumentType.getInteger(c, "id"));
                    if (!chief(p) || e == null || e.distanceTo(p) > 32 || kind(e).isEmpty()) return 0;
                    act(p, e, kind(e), StringArgumentType.getString(c, "what"));
                    return 1;
                }))));
    }

    private static void act(ServerPlayer p, Entity e, String k, String what) {
        ServerLevel l = p.serverLevel();
        String who = Unleashed.designation(e);
        String said, line;
        switch (what) {
            case "bless" -> {
                said = "Bless me.";
                Method ready = UnleashedApi.method("scp343.Scp343Blessings", "ready", ServerPlayer.class);
                Method grant = UnleashedApi.method("scp343.Scp343Blessings", "grant", ServerPlayer.class);
                boolean ok = ready == null || UnleashedApi.call(ready, null, p) instanceof Boolean b && b;
                if (ok && grant != null) { UnleashedApi.call(grant, null, p); line = pick("There. Use it well.", "Of course. You've earned more than that, but this is what I've got on me."); }
                else line = "You still have my last one about you. Be patient, it isn't going anywhere.";
            }
            case "heal" -> {
                said = "Heal everyone here.";
                int n = 0;
                for (LivingEntity x : l.getEntitiesOfClass(LivingEntity.class, new AABB(e.blockPosition()).inflate(24), x -> !Unleashed.isAnomaly(x))) {
                    AdminOverride.cure(x, e);
                    x.heal(x.getMaxHealth());
                    l.sendParticles(ParticleTypes.HAPPY_VILLAGER, x.getX(), x.getY() + 1, x.getZ(), 6, 0.3, 0.5, 0.3, 0);
                    n++;
                }
                line = n <= 1 ? "Done. You were the only one hurting." : "Done. All " + n + " of you. Try to stay that way.";
            }
            case "judge" -> {
                said = "Judge those who'd hurt my people.";
                Method strike = UnleashedApi.method("scp343.Scp343Judgement", "strike", LivingEntity.class);
                int n = 0;
                for (Mob m : l.getEntitiesOfClass(Mob.class, new AABB(e.blockPosition()).inflate(32), m -> m.getTarget() instanceof ServerPlayer
                        || m.getTarget() instanceof com.breachprotocol.entity.FoundationStaff || m.getTarget() != null && m.getTarget().getType().toString().contains("villager"))) {
                    if (strike != null) UnleashedApi.call(strike, null, m); else m.kill();
                    n++;
                }
                line = n == 0 ? "Nobody here means anyone harm. I'd know." : "I don't enjoy that. But they would have.";
            }
            case "walk" -> {
                said = "Walk with me.";
                if (k.equals("343")) UnleashedApi.call(UnleashedApi.method("scp343.Scp343God", "seek", java.util.UUID.class), e, p.getUUID());
                Dominion.action(p, "dom:follow", e.getId());
                line = k.equals("343") ? "Lead on. I've seen it all, but never with you." : k.equals("682") ? "...For now." : k.equals("049") ? "After you, Administrator." : "As you say.";
            }
            case "stay" -> {
                said = k.equals("343") ? "Stay. We'd like you here." : "Stay here.";
                if (k.equals("343")) UnleashedApi.call(UnleashedApi.method("scp343.Scp343God", "cancelLeaving"), e);
                Dominion.action(p, "dom:stay", e.getId());
                line = k.equals("343") ? "Then I'll stay. Someone should keep you company." : k.equals("682") ? "(It lowers itself to the floor, watching you.)" : "Very well.";
            }
            case "free" -> {
                said = "Go wherever you like.";
                Dominion.action(p, "dom:release", e.getId());
                UnleashedApi.call(UnleashedApi.method("scp343.Scp343God", "leaveNow", boolean.class), e, false);
                line = "Thank you. I'll be back. I always am.";
            }
            case "talk" -> {
                Dialogue.close(p);
                UnleashedApi.call(UnleashedApi.method("scp343.Scp343God", "listen", ServerPlayer.class), e, p);
                return;
            }
            case "cell" -> {
                said = "Back to your cell.";
                line = Containment.returnToCell(p.server, e.getUUID()) ? (k.equals("682") ? "(It goes. Every step of the way it looks back at you.)" : "As you wish.") : "(It looks at you. There's no cell of its to go back to.)";
            }
            case "tell" -> {
                said = "What's near us, Cain?";
                int n = 0;
                for (Mob m : l.getEntitiesOfClass(Mob.class, new AABB(e.blockPosition()).inflate(96), Unleashed::isAnomaly))
                    if (m != e) { m.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20 * 60, 0, false, false)); n++; }
                line = n == 0 ? "Nothing anomalous within a hundred metres but me." : n + " within a hundred metres. I've marked them for you. You'll see them through the walls for a minute.";
            }
            case "still" -> {
                said = "Be still.";
                UnleashedApi.call(UnleashedApi.method("scp682.Scp682", "sealFor", int.class), e, 20 * 120);
                Dominion.action(p, "dom:calm", e.getId());
                line = "(It hisses. Then it lies down, and the hide closes over it like armour.)";
            }
            case "mute" -> {
                said = "Stop pretending.";
                UnleashedApi.call(UnleashedApi.method("scp939.Scp939", "endLure", boolean.class), e, false);
                Dominion.action(p, "dom:calm", e.getId());
                line = "(Silence. Then, in your own voice, very quietly: \"Yes.\")";
            }
            case "patients" -> {
                said = "Leave your patients be, Doctor.";
                Dominion.action(p, "dom:calm", e.getId());
                line = "As you say. They are cured; they may rest now.";
            }
            default -> { return; }
        }
        Dialogue.line(p, "You", said, 0xD08CFF);
        Dialogue.line(p, who, line, 0xF0E6C8);
        Dialogue.send(p, false);
        l.playSound(null, e.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 0.6F, 1.2F);
    }
}
