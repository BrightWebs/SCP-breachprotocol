package com.breachprotocol.client;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.career.Powers;
import com.breachprotocol.network.ClientCareerState;
import com.breachprotocol.network.PhoneActionPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * The two power slots. Each has its own key (Z and X by default; rebind them under Controls, Breach
 * Protocol), a power and, per power, a range.
 *
 * <ul>
 * <li><b>Tap</b> a slot's key: use its power.</li>
 * <li><b>Hold</b> it and scroll: change which power is in the slot.</li>
 * <li><b>Hold</b> it, hold Shift and scroll: change the power's range.</li>
 * </ul>
 *
 * The loadout, ranges and where the slots sit on screen are set in the console (Shift + the answer
 * key, Powers tab) and kept in config/breachprotocol-powers.properties.
 */
@EventBusSubscriber(modid = BreachProtocol.MODID, value = Dist.CLIENT)
public final class PowerKeys {
    private PowerKeys() {}

    public static final KeyMapping[] SLOTS = {
            new KeyMapping("key.breachprotocol.power1", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, "key.categories.breachprotocol"),
            new KeyMapping("key.breachprotocol.power2", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, "key.categories.breachprotocol")};

    /** The console on a key of its own (G by default), besides Shift + the answer key and the phone's Implant app. */
    public static final KeyMapping CONSOLE = new KeyMapping("key.breachprotocol.console", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.categories.breachprotocol");

    /** Opens the console, or says plainly why it won't open. */
    static void openConsole() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (dom() >= 1) { mc.setScreen(new CommandScreen()); return; }
        mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("No implant: the console belongs to the Site Administrator and SCP-Ω.")
                .withStyle(net.minecraft.ChatFormatting.GRAY), true);
        mc.player.playSound(SoundEvents.NOTE_BLOCK_BASS.value(), 0.4F, 0.5F);
    }

    @EventBusSubscriber(modid = BreachProtocol.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Keys {
        @SubscribeEvent
        public static void register(RegisterKeyMappingsEvent event) { for (KeyMapping k : SLOTS) event.register(k); event.register(CONSOLE); }
    }

    // ================================================================== the loadout (client-side, saved to config)

    private static final Properties CFG = new Properties();
    private static boolean loaded;

    private static Path file() { return FMLPaths.CONFIGDIR.get().resolve("breachprotocol-powers.properties"); }

    private static void load() {
        if (loaded) return;
        loaded = true;
        try (Reader r = Files.newBufferedReader(file())) { CFG.load(r); } catch (Exception ignored) {}
    }

    static void save() {
        try (Writer w = Files.newBufferedWriter(file())) { CFG.store(w, "Breach Protocol power slots"); } catch (Exception ignored) {}
    }

    static int dom() { return ClientCareerState.tag.getInt("dom"); }

    /** Powers this player has (the implant's, or all of them as SCP-Ω). */
    static List<Powers.Power> owned() {
        List<Powers.Power> out = new ArrayList<>();
        for (Powers.Power p : Powers.ALL) if (dom() >= p.minLevel()) out.add(p);
        return out;
    }

    static Powers.Power slot(int i) {
        load();
        List<Powers.Power> own = owned();
        if (own.isEmpty()) return null;
        Powers.Power p = Powers.get(CFG.getProperty("slot" + i, i == 0 ? "repel" : "tesla"));
        return p != null && own.contains(p) ? p : own.get(Math.min(i, own.size() - 1));
    }

    static void setSlot(int i, Powers.Power p) { load(); CFG.setProperty("slot" + i, p.id()); save(); }

    static int range(Powers.Power p) {
        load();
        try { return Math.max(p.rMin(), Math.min(p.rMax(), Integer.parseInt(CFG.getProperty("range." + p.id(), String.valueOf(p.rDef()))))); }
        catch (NumberFormatException e) { return p.rDef(); }
    }

    static void setRange(Powers.Power p, int r) {
        load();
        int was = range(p);
        CFG.setProperty("range." + p.id(), String.valueOf(Math.max(p.rMin(), Math.min(p.rMax(), r))));
        save();
        if (auto(p) && range(p) != was) PacketDistributor.sendToServer(new PhoneActionPayload("pow:auto:" + p.id() + ":1:" + range(p), 0));
    }

    // ---------------------------------------------------------------- auto (kept on the server, mirrored here)

    /** A tap-on, tap-off power that's on right now (time stop, rage). */
    static boolean active(Powers.Power p) { return p != null && ClientCareerState.tag.getCompound("tog").getBoolean(p.id()); }

    /** The Vault power's mode: a tap opens the Vault screen, or (trap mode) takes whatever you're looking at straight in. */
    static boolean vaultTrap() { load(); return "trap".equals(CFG.getProperty("vault.mode", "open")); }

    static void setVaultTrap(boolean trap) {
        load();
        CFG.setProperty("vault.mode", trap ? "trap" : "open");
        save();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal(trap ? "The Vault: TRAP. A tap takes what you're looking at. Shift+tap to open it instead." : "The Vault: OPEN. A tap opens the Vault. Shift+tap to trap instead.").withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE), true);
            mc.player.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.5F, trap ? 0.7F : 1.3F);
        }
    }

    static boolean auto(Powers.Power p) { return p != null && ClientCareerState.tag.getCompound("auto").contains(p.id()); }

    static void setAuto(Powers.Power p, boolean on) {
        if (p == null || !Powers.automatic(p)) return;
        PacketDistributor.sendToServer(new PhoneActionPayload("pow:auto:" + p.id() + ":" + (on ? 1 : 0) + ":" + range(p), 0));
        // show it at once; the server's sync confirms it
        CompoundTag a = ClientCareerState.tag.getCompound("auto");
        if (on) a.putInt(p.id(), range(p)); else a.remove(p.id());
        ClientCareerState.tag.put("auto", a);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.playSound(on ? SoundEvents.BEACON_ACTIVATE : SoundEvents.BEACON_DEACTIVATE, 0.35F, 1.6F);
    }

    // ---------------------------------------------------------------- creatures you've come across (for the Targets tab)

    private static final java.util.Set<String> SEEN = new java.util.LinkedHashSet<>(), NEARBY = new java.util.HashSet<>();

    static java.util.Set<String> seen() { load(); if (SEEN.isEmpty()) for (String k : CFG.getProperty("seen", "").split(",")) if (!k.isBlank()) SEEN.add(k); return SEEN; }

    static java.util.Set<String> nearby() { return NEARBY; }

    private static void look(Minecraft mc) {
        NEARBY.clear();
        boolean grew = false;
        java.util.Set<String> s = seen();
        for (net.minecraft.world.entity.Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof net.minecraft.world.entity.LivingEntity) || e instanceof net.minecraft.world.entity.player.Player
                    || e instanceof net.minecraft.world.entity.decoration.ArmorStand) continue;
            String k = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString();
            if (e.distanceToSqr(mc.player) < 48 * 48) NEARBY.add(k);
            grew |= s.add(k);
        }
        if (grew) { CFG.setProperty("seen", String.join(",", s)); save(); }
    }

    /** Where the slots sit: 0 bottom right, 1 bottom left, 2 right side, 3 top right, 4 top left. */
    static int anchor() { load(); try { return Integer.parseInt(CFG.getProperty("anchor", "0")); } catch (NumberFormatException e) { return 0; } }

    static void setAnchor(int a) { load(); CFG.setProperty("anchor", String.valueOf(a)); save(); }

    static boolean hidden() { load(); return Boolean.parseBoolean(CFG.getProperty("hidden", "false")); }

    static void setHidden(boolean h) { load(); CFG.setProperty("hidden", String.valueOf(h)); save(); }

    static int step(Powers.Power p) { return p.rMax() >= 100 ? 8 : p.rMax() >= 40 ? 4 : 1; }

    static long cooldownLeft(Powers.Power p) {
        if (true) return 0; // the powers don't recharge
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return 0;
        CompoundTag cd = ClientCareerState.tag.getCompound("pcd");
        return Math.max(0, cd.getLong(p.id()) - mc.level.getGameTime());
    }

    // ================================================================== using the keys

    private static final int HOLD = 6;
    private static final int[] held = new int[2];
    private static final boolean[] adjusted = new boolean[2];
    private static int flash0, flash1;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (flash0 > 0) flash0--;
        if (flash1 > 0) flash1--;
        if (mc.level != null && mc.player != null && mc.player.tickCount % 40 == 0) look(mc);
        if (mc.player != null) VaultScreen.watch();
        while (CONSOLE.consumeClick()) if (mc.screen == null) openConsole();
        for (int i = 0; i < 2; i++) {
            KeyMapping k = SLOTS[i];
            while (k.consumeClick()) { }
            if (mc.player == null || dom() < 1) { held[i] = 0; continue; }
            boolean down = k.isDown() && mc.screen == null;
            if (down) held[i]++;
            else if (held[i] > 0) {
                if (!adjusted[i] && held[i] < HOLD + 4) {
                    Powers.Power p = slot(i);
                    if (Screen.hasShiftDown() && p != null && p.id().equals("vault")) setVaultTrap(!vaultTrap());
                    else if (Screen.hasShiftDown() && p != null && Powers.automatic(p)) setAuto(p, !auto(p));
                    else use(i);
                }
                held[i] = 0;
                adjusted[i] = false;
            }
        }
    }

    private static void use(int i) {
        Powers.Power p = slot(i);
        if (p == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (p.id().equals("vault")) {
            if (vaultTrap()) { PacketDistributor.sendToServer(new PhoneActionPayload("vault:trap", 0)); if (i == 0) flash0 = 8; else flash1 = 8; }
            else mc.setScreen(new VaultScreen());
            return;
        }
        if (cooldownLeft(p) > 0) { mc.player.playSound(SoundEvents.NOTE_BLOCK_BASS.value(), 0.4F, 0.5F); return; }
        PacketDistributor.sendToServer(new PhoneActionPayload("pow:" + p.id() + ":" + range(p), 0));
        if (i == 0) flash0 = 8; else flash1 = 8;
    }

    static int holding() {
        for (int i = 0; i < 2; i++) if (held[i] >= HOLD) return i;
        return -1;
    }

    @SubscribeEvent
    public static void scroll(InputEvent.MouseScrollingEvent event) {
        int i = holding();
        if (i < 0) return;
        event.setCanceled(true);
        adjusted[i] = true;
        int dir = event.getScrollDeltaY() > 0 ? 1 : -1;
        Powers.Power p = slot(i);
        if (p == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (Screen.hasShiftDown()) {
            if (p.rMax() == 0) return;
            setRange(p, range(p) + dir * step(p));
            mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.25F, 1.0F + (range(p) - p.rMin()) / (float) Math.max(1, p.rMax() - p.rMin()));
        } else {
            List<Powers.Power> own = owned();
            int at = own.indexOf(p);
            setSlot(i, own.get(Math.floorMod(at - dir, own.size())));
            mc.player.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.4F, 1.4F);
        }
    }

    // ================================================================== drawing the slots

    @SubscribeEvent
    public static void render(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || dom() < 1 || mc.options.hideGui || hidden()) return;
        GuiGraphics g = event.getGuiGraphics();
        int W = g.guiWidth(), H = g.guiHeight(), s = 40, gap = 6;
        int a = anchor();
        boolean vertical = a == 2;
        int totalW = vertical ? s : s * 2 + gap, totalH = vertical ? s * 2 + gap + 12 : s + 12;
        int x, y;
        switch (a) {
            case 1 -> { x = 8; y = H - totalH - 8; }
            case 2 -> { x = W - totalW - 8; y = H / 2 - totalH / 2; }
            case 3 -> { x = W - totalW - 8; y = 8; }
            case 4 -> { x = 8; y = 8; }
            default -> { x = W / 2 + 91 + 12; y = H - totalH - 4; if (x + totalW > W - 4) { x = W - totalW - 8; y = H - totalH - 30; } }
        }
        for (int i = 0; i < 2; i++) {
            int sx = vertical ? x : x + i * (s + gap), sy = vertical ? y + i * (s + gap + 6) : y;
            drawSlot(g, mc.font, i, sx, sy, s);
        }
        int h = holding();
        if (h >= 0) drawPicker(g, mc.font, h, vertical ? x : x + h * (s + gap), vertical ? y + h * (s + gap + 6) : y, s, W);
    }

    static void drawSlot(GuiGraphics g, Font f, int i, int x, int y, int s) {
        Powers.Power p = slot(i);
        int col = p == null ? 0xFF606060 : 0xFF000000 | p.colour();
        boolean flash = (i == 0 ? flash0 : flash1) > 0, holdingThis = held[i] >= HOLD;
        // body
        g.fill(x, y, x + s, y + s, 0xC0100A16);
        g.fill(x + 1, y + 1, x + s - 1, y + s - 1, flash ? 0x60000000 | (col & 0xFFFFFF) : 0x9018101E);
        boolean on = auto(p) || active(p);
        int edge = holdingThis ? 0xFFFFFFFF : col;
        if (on) {
            // left on: the frame breathes
            float t = (System.currentTimeMillis() % 1600) / 1600F, a = 0.45F + 0.55F * (float) Math.abs(Math.sin(t * Math.PI));
            edge = ((int) (a * 255) << 24) | (col & 0xFFFFFF);
            g.fill(x - 1, y - 1, x + s + 1, y, edge); g.fill(x - 1, y + s, x + s + 1, y + s + 1, edge);
            g.fill(x - 1, y - 1, x, y + s + 1, edge); g.fill(x + s, y - 1, x + s + 1, y + s + 1, edge);
        }
        g.fill(x, y, x + s, y + 1, edge); g.fill(x, y + s - 1, x + s, y + s, edge);
        g.fill(x, y, x + 1, y + s, edge); g.fill(x + s - 1, y, x + s, y + s, edge);
        // corner notches, the mod's terminal look
        g.fill(x + 3, y + 3, x + 7, y + 4, col); g.fill(x + 3, y + 3, x + 4, y + 7, col);
        g.fill(x + s - 7, y + s - 4, x + s - 3, y + s - 3, col); g.fill(x + s - 4, y + s - 7, x + s - 3, y + s - 3, col);
        if (p == null) return;
        // the glyph, big
        g.pose().pushPose();
        g.pose().translate(x + s / 2F, y + s / 2F - 4, 0);
        g.pose().scale(2F, 2F, 1F);
        g.drawString(f, p.glyph(), -f.width(p.glyph()) / 2, -4, col, false);
        g.pose().popPose();
        // key and range
        String key = SLOTS[i].getTranslatedKeyMessage().getString();
        if (key.length() > 3) key = key.substring(0, 3);
        g.drawString(f, key, x + s - 3 - f.width(key), y + 3, 0xFFD8D0E8, false);
        if (p.rMax() > 0) small(g, f, range(p) + "m", x + 3, y + s - 9, 0xFFB8B0C8);
        if (p.id().equals("vault")) {
            String badge = vaultTrap() ? "TRAP" : "OPEN";
            int bw = (int) (f.width(badge) * 0.75F) + 4;
            g.fill(x + s - 2 - bw, y + s - 10, x + s - 2, y + s - 2, vaultTrap() ? 0xFFE07070 : col);
            small(g, f, badge, x + s - bw, y + s - 8, 0xFF0A0710);
        }
        if (on) {
            String badge = active(p) ? "ON" : "AUTO";
            int bw = (int) (f.width(badge) * 0.75F) + 4;
            g.fill(x + s - 2 - bw, y + s - 10, x + s - 2, y + s - 2, col);
            small(g, f, badge, x + s - bw, y + s - 8, 0xFF0A0710);
        }
        // cooldown: a shutter that rises as it recharges
        long left = cooldownLeft(p);
        if (left > 0) {
            int hgt = (int) (s * Math.min(1F, left / (float) p.cooldown()));
            g.fill(x + 1, y + s - 1 - hgt, x + s - 1, y + s - 1, 0xB0000000);
            String sec = String.valueOf((left + 19) / 20);
            g.drawString(f, sec, x + s / 2 - f.width(sec) / 2, y + s / 2 - 4, 0xFFFFFFFF, true);
        }
        // name under the slot
        String nm = f.width(p.name()) * 0.75F > s + 4 ? f.plainSubstrByWidth(p.name(), (int) ((s + 4) / 0.75F)) : p.name();
        small(g, f, nm, x + s / 2 - (int) (f.width(nm) * 0.375F), y + s + 2, col);
    }

    private static void drawPicker(GuiGraphics g, Font f, int i, int sx, int sy, int s, int W) {
        Powers.Power p = slot(i);
        if (p == null) return;
        List<Powers.Power> own = owned();
        int at = own.indexOf(p);
        int w = 200, h = 64, x = Math.max(4, Math.min(W - w - 4, sx + s / 2 - w / 2)), y = sy - h - 6;
        if (y < 4) y = sy + s + 16;
        g.fill(x, y, x + w, y + h, 0xE0100A16);
        g.fill(x, y, x + w, y + 1, 0xFF000000 | p.colour());
        String prev = own.get(Math.floorMod(at - 1, own.size())).name(), next = own.get(Math.floorMod(at + 1, own.size())).name();
        small(g, f, "▲ " + prev, x + 6, y + 4, 0xFF807890);
        g.drawString(f, p.glyph() + " " + p.name(), x + 6, y + 13, 0xFF000000 | p.colour(), false);
        small(g, f, "▼ " + next, x + 6, y + 24, 0xFF807890);
        if (p.rMax() > 0) {
            int r = range(p), bw = w - 12;
            g.fill(x + 6, y + 36, x + 6 + bw, y + 39, 0xFF302838);
            g.fill(x + 6, y + 36, x + 6 + (int) (bw * (r - p.rMin()) / (float) Math.max(1, p.rMax() - p.rMin())), y + 39, 0xFF000000 | p.colour());
            small(g, f, "RANGE " + r + "M  (" + p.rMin() + "-" + p.rMax() + ")", x + 6, y + 42, 0xFFB8B0C8);
        }
        small(g, f, "SCROLL: POWER  ·  SHIFT+SCROLL: RANGE" + (Powers.automatic(p) ? "  ·  SHIFT+TAP: AUTO" : p.id().equals("vault") ? "  ·  SHIFT+TAP: OPEN / TRAP" : ""), x + 6, y + 54, 0xFF6A6080);
    }

    static void small(GuiGraphics g, Font f, String s, int x, int y, int c) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.75F, 0.75F, 1F);
        g.drawString(f, s, 0, 0, c, false);
        g.pose().popPose();
    }
}
