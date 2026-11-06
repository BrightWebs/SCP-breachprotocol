package com.breachprotocol.network;

import com.breachprotocol.career.Calls;
import com.breachprotocol.career.Career;
import com.breachprotocol.career.CareerData;
import com.breachprotocol.career.Directives;
import net.minecraft.server.level.ServerPlayer;

/** Server-side handling of phone buttons. */
public final class PhoneActions {
    private PhoneActions() {}

    public static void handle(ServerPlayer p, String action, int arg) {
        // (Inventory.contains(ItemStack) compares components, so it missed phones with a fused card.)
        if (!com.breachprotocol.career.Clearance.hasPhone(p)) return;
        try {
            run(p, action, arg);
        } catch (Throwable t) {
            com.breachprotocol.BreachProtocol.LOGGER.error("Phone action {} failed", action, t);
            notify(p, "The phone glitched. (Logged: " + t.getClass().getSimpleName() + ")");
        }
        Career.sync(p);
    }

    private static void run(ServerPlayer p, String action, int arg) {
        CareerData d = Career.data(p);
        switch (action) {
            case "read" -> {
                d.messages.forEach(m -> m.read = true);
                d.dirty = true;
            }
            case "answer" -> Calls.answer(p, arg);
            case "submit" -> Career.submitSupply(p, arg);
            case "abandon" -> Career.abandon(p, arg);
            case "fuse" -> com.breachprotocol.career.Clearance.fuse(p);
            case "photo_take" -> com.breachprotocol.career.Photos.take(p, arg);
            case "photo_open" -> com.breachprotocol.career.Photos.open(p, arg);
            case "photo_delete" -> com.breachprotocol.career.Photos.delete(p, arg);
            case "photo_print" -> com.breachprotocol.career.Photos.print(p, arg);
            case "photo_share" -> com.breachprotocol.career.Photos.share(p, arg);
            case "mtf_report" -> com.breachprotocol.compat.UnleashedApi.mtfReport(p);
            case "mtf_extract" -> Command.extract(p);
            case "orbital_strike" -> Command.orbitalStrike(p);
            default -> {
                if (action.startsWith("dir:")) Directives.run(p, action.substring(4));
                else if (action.startsWith("mtf:")) Command.callMtf(p, action.substring(4));
                else if (action.startsWith("dorder:")) Command.orderDClass(p, arg, action.substring(7));
            }
        }
    }

    static void notify(ServerPlayer p, String msg) {
        p.displayClientMessage(net.minecraft.network.chat.Component.literal(msg).withStyle(net.minecraft.ChatFormatting.GRAY), true);
    }
}
