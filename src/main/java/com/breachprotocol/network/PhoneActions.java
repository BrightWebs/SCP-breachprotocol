package com.breachprotocol.network;

import com.breachprotocol.career.Calls;
import com.breachprotocol.career.Career;
import com.breachprotocol.career.CareerData;
import com.breachprotocol.career.Directives;
import com.breachprotocol.registry.ModItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Server-side handling of phone buttons. */
public final class PhoneActions {
    private PhoneActions() {}

    public static void handle(ServerPlayer p, String action, int arg) {
        if (!p.getInventory().contains(new ItemStack(ModItems.PHONE.get()))) return;
        CareerData d = Career.data(p);
        switch (action) {
            case "read" -> {
                d.messages.forEach(m -> m.read = true);
                d.dirty = true;
            }
            case "answer" -> Calls.answer(p, arg);
            case "submit" -> Career.submitSupply(p, arg);
            case "abandon" -> Career.abandon(p, arg);
            default -> {
                if (action.startsWith("dir:")) Directives.run(p, action.substring(4));
            }
        }
        Career.sync(p);
    }
}
