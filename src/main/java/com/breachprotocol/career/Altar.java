package com.breachprotocol.career;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.compat.Unleashed;
import com.breachprotocol.entity.Dialogue;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * The Altar beyond the Gate, in Site-01's sealed chamber. It is not on any floor plan; the staff who
 * know about it don't say so. Only the Administrator is answered, and what it answers with is a
 * price: the Altar's Trial.
 *
 * <ol>
 * <li>Swear before it. From then on it watches the containment registry and the Administrator's hands.</li>
 * <li>Hold {@link #HOLD} anomalies in Foundation cells at the same time (any site, the registry's count).</li>
 * <li>Put down {@link #KILLS} anomalies with your own hand while they're loose.</li>
 * <li>Return with SCP-500. Offer it, and stop being a person.</li>
 * </ol>
 *
 * The Council notices (see Motions): an Administrator who has sworn is talked about at the table.
 */
@EventBusSubscriber(modid = BreachProtocol.MODID)
public final class Altar {
    private Altar() {}

    public static final int HOLD = 6, KILLS = 4;
    private static final int VOICE = 0xC9A2FF;

    public static boolean held(ServerPlayer p) { return Containment.heldCount(p.server) >= HOLD; }

    public static boolean culled(ServerPlayer p) { return Career.data(p).trialKills >= KILLS; }

    public static boolean ready(ServerPlayer p) { return Career.data(p).trial == 1 && held(p) && culled(p); }

    @SubscribeEvent
    public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("altar")
                .then(Commands.literal("swear").executes(c -> { swear(c.getSource().getPlayerOrException()); return 1; }))
                .then(Commands.literal("ask").executes(c -> { ask(c.getSource().getPlayerOrException()); return 1; }))
                .then(Commands.literal("leave").executes(c -> { Dialogue.close(c.getSource().getPlayerOrException()); return 1; })));
    }

    /** Someone touched it. */
    public static void touched(ServerPlayer p) {
        CareerData d = Career.data(p);
        if (d.ascended) {
            p.displayClientMessage(Component.literal("You are already everything the altar can make you.").withStyle(ChatFormatting.LIGHT_PURPLE), true);
            return;
        }
        if (Career.rank(p) != Rank.ADMINISTRATOR) {
            // it isn't for them, and it lets them know
            p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 120, 0, false, false));
            p.playNotifySound(SoundEvents.WARDEN_HEARTBEAT, SoundSource.AMBIENT, 1F, 0.6F);
            p.displayClientMessage(Component.literal("The altar does not answer you. Something behind your eyes remembers your name.").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC), true);
            return;
        }
        open(p);
    }

    private static void open(ServerPlayer p) {
        CareerData d = Career.data(p);
        Dialogue.begin(p, "altar", "The Altar", "Beyond the Gate · Classified", -1);
        Dialogue.hud(p, false);
        p.playNotifySound(SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.AMBIENT, 0.25F, 0.5F);
        if (d.trial == 0) {
            Dialogue.line(p, "The Altar", "You came down past the Gate with a chair still warm behind you. Thirteen men and women argue upstairs about what to keep in the dark. You want to be the dark.", VOICE);
            Dialogue.line(p, "The Altar", "I do not give. I trade. Swear, and I will name the price.", VOICE);
            Dialogue.option(p, "Swear before the Altar", "/altar swear", "Begin the Altar's Trial. There is no unswearing.", 0xC9A2FF);
            Dialogue.option(p, "Step back from it", "/altar leave", "Not yet", 0xC0C0C0);
        } else {
            progress(p);
        }
        Dialogue.send(p, true);
    }

    private static void swear(ServerPlayer p) {
        CareerData d = Career.data(p);
        if (Career.rank(p) != Rank.ADMINISTRATOR || d.ascended || d.trial != 0) { Dialogue.close(p); return; }
        d.trial = 1;
        d.trialKills = 0;
        d.dirty = true;
        Dialogue.clearOptions(p);
        Dialogue.line(p, "You", "I swear.", 0xD08CFF);
        Dialogue.line(p, "The Altar", "Then hear the price. Hold " + HOLD + " of them in your cells at once, so that I know you can keep what you take. Put " + KILLS
                + " of the loose ones down with your own hand, so that I know you can end what you can't keep. Then bring me the cure for all things.", VOICE);
        Dialogue.line(p, "The Altar", "Your Council will feel it. They always do.", VOICE);
        Dialogue.option(p, "What do you ask of me again?", "/altar ask", "The terms of the Trial", 0xC9A2FF);
        Dialogue.option(p, "Leave", "/altar leave", "", 0xC0C0C0);
        Dialogue.send(p, true);
        p.playNotifySound(SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, SoundSource.BLOCKS, 0.8F, 0.5F);
        WorldNews.record(p.server, WorldNews.Kind.KNOWLEDGE, "altar_sworn", "the Administrator went beyond the Gate and came back changed", 0, p.getGameProfile().getName());
        Career.message(p, O5.O5_1, "You went to it. I can tell from here. I won't ask what it wanted. I know what it wanted from me, and I said no. Be better at this than I was.");
    }

    private static void ask(ServerPlayer p) {
        if (Career.data(p).trial != 1) { Dialogue.close(p); return; }
        Dialogue.clearOptions(p);
        progress(p);
        Dialogue.send(p, true);
    }

    private static void progress(ServerPlayer p) {
        CareerData d = Career.data(p);
        int held = Containment.heldCount(p.server);
        Dialogue.line(p, "The Altar", "You hold " + held + " of " + HOLD + "." + (held >= HOLD ? " Enough." : " Not enough. Your cells are listed in the registry; so are your failures."), VOICE);
        Dialogue.line(p, "The Altar", "You have ended " + Math.min(d.trialKills, KILLS) + " of " + KILLS + " loose ones by your own hand." + (d.trialKills >= KILLS ? " Enough." : " The ones in cells don't count. Neither do the ones your soldiers kill."), VOICE);
        if (ready(p)) {
            Dialogue.line(p, "The Altar", Unleashed.hasScp500() ? "Now. The cure. Hold it out to me." : "Now. The cure for all things. Bring it.", VOICE);
        } else {
            Dialogue.line(p, "The Altar", "Come back when the price is paid. I am not going anywhere. Neither, it turns out, are you.", VOICE);
        }
        Dialogue.option(p, "Leave", "/altar leave", "", 0xC0C0C0);
    }

    /** Offering SCP-500: only once the price is paid. @return true if the ritual may begin */
    public static boolean offer(ServerPlayer p) {
        CareerData d = Career.data(p);
        if (d.trial != 1) {
            p.displayClientMessage(Component.literal("The altar wants an oath before it wants anything else.").withStyle(ChatFormatting.DARK_PURPLE), true);
            open(p);
            return false;
        }
        if (!ready(p)) {
            p.displayClientMessage(Component.literal("Not yet. The price isn't paid.").withStyle(ChatFormatting.DARK_PURPLE), true);
            open(p);
            return false;
        }
        Dialogue.close(p);
        d.trial = 2;
        d.dirty = true;
        return true;
    }
}
