package com.breachprotocol.client;

import com.breachprotocol.BreachProtocol;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Council sessions as an overlay: you keep looking around from your chair while the debate scrolls
 * in the corner. The clickable window only opens when the Council asks you to decide (or when you
 * press the answer key).
 */
@EventBusSubscriber(modid = BreachProtocol.MODID, value = Dist.CLIENT)
public final class DialogueHud {
    private DialogueHud() {}

    static CompoundTag hud;

    public static final KeyMapping ANSWER = new KeyMapping("key.breachprotocol.answer", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.categories.breachprotocol");

    @EventBusSubscriber(modid = BreachProtocol.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Keys {
        @SubscribeEvent
        public static void register(RegisterKeyMappingsEvent event) { event.register(ANSWER); }
    }

    static boolean pending() { return hud != null && !hud.getList("options", Tag.TAG_COMPOUND).isEmpty(); }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        while (ANSWER.consumeClick()) if (mc.screen == null && pending()) mc.setScreen(DialogueScreen.open(hud));
        if (mc.player == null) hud = null;
    }

    @SubscribeEvent
    public static void render(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (hud == null || mc.screen != null || mc.options.hideGui) return;
        GuiGraphics g = event.getGuiGraphics();
        Font f = mc.font;
        int w = Math.min(260, g.guiWidth() / 2 - 10), x = 6, y = 6;
        List<FormattedCharSequence> lines = new ArrayList<>();
        List<Integer> colours = new ArrayList<>();
        ListTag ls = hud.getList("lines", Tag.TAG_COMPOUND);
        for (int i = Math.max(0, ls.size() - 6); i < ls.size(); i++) {
            CompoundTag l = ls.getCompound(i);
            String who = l.getString("who");
            Component c = who.isEmpty()
                    ? Component.literal(l.getString("text")).withColor(0xA8B0BC).withStyle(s -> s.withItalic(true))
                    : Component.literal(who + ": ").withColor(l.getInt("c") == 0 ? 0xE0C060 : l.getInt("c")).append(Component.literal(l.getString("text")).withColor(0xEDEDED));
            for (FormattedCharSequence s : f.split(c, w - 12)) { lines.add(s); colours.add(0); }
        }
        while (lines.size() > 12) lines.remove(0);
        boolean ask = pending();
        int h = 26 + lines.size() * 10 + (ask ? 14 : 0);
        g.fill(x, y, x + w, y + h, 0xB0101216);
        g.fill(x, y, x + w, y + 22, 0xC01C2028);
        g.drawString(f, hud.getString("title"), x + 6, y + 3, 0xFFFFFF, false);
        g.drawString(f, hud.getString("subtitle"), x + 6, y + 12, 0x9AA4B2, false);
        int yy = y + 25;
        for (FormattedCharSequence s : lines) { g.drawString(f, s, x + 6, yy, 0xFFFFFF, false); yy += 10; }
        if (ask) g.drawString(f, (hud.getString("session").equals("council") ? "The Council awaits you. " : "") + "Press [" + ANSWER.getTranslatedKeyMessage().getString() + "] to reply.", x + 6, yy + 2, 0xFFC94D, false);
    }
}
