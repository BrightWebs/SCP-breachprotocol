package com.breachprotocol.client;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.network.ClientCareerState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;

/**
 * The Gate Guardian's "turn back" warnings (SCP Unleashed's warning overlay and its warning sounds)
 * are not for the Administrator or the Ascendant. Everyone else still gets them.
 */
@EventBusSubscriber(modid = BreachProtocol.MODID, value = Dist.CLIENT)
public final class GateWarningFilter {
    private GateWarningFilter() {}

    private static boolean exempt() {
        var t = ClientCareerState.tag;
        return t != null && (t.getBoolean("ascended") || t.getInt("rankId") >= com.breachprotocol.career.Rank.ADMINISTRATOR.ordinal());
    }

    @SubscribeEvent
    public static void layer(RenderGuiLayerEvent.Pre event) {
        if (event.getName().getNamespace().equals("scp_unleashed") && event.getName().getPath().equals("scp001_gate_warning") && exempt()) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void sound(PlaySoundEvent event) {
        if (event.getSound() == null || !exempt()) return;
        var id = event.getSound().getLocation();
        if (id.getNamespace().equals("scp_unleashed") && id.getPath().startsWith("scp001_gate.warn")) event.setSound(null);
    }
}
