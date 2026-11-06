package com.breachprotocol.client;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.compat.UnleashedApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * The phone camera as a viewfinder you walk around with. Opening Camera closes the phone; you can
 * move and look freely, left-click takes a photo of the square, right-click puts the camera away.
 * The HUD is hidden while it's up, and the photo never includes the viewfinder itself.
 */
@EventBusSubscriber(modid = BreachProtocol.MODID, value = Dist.CLIENT)
public final class CameraMode {
    private CameraMode() {}

    private static boolean active;
    private static long lastShot, startedAt;
    private static int shots;

    public static boolean active() {
        return active;
    }

    public static void start() {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(null);
        active = true;
        startedAt = System.currentTimeMillis();
        shots = 0;
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.SPYGLASS_USE, 1.4F, 0.6F));
    }

    public static void stop() {
        if (!active) return;
        active = false;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.SPYGLASS_STOP_USING, 1.4F, 0.6F));
    }

    /** The viewfinder square in GUI pixels: x, y, side. */
    private static int[] frame(int w, int h) {
        int side = (int) (Math.min(w, h) * 0.72F);
        return new int[]{(w - side) / 2, (h - side) / 2, side};
    }

    private static void shoot() {
        long now = System.currentTimeMillis();
        if (now - lastShot < 700) return;
        lastShot = now;
        Minecraft mc = Minecraft.getInstance();
        int[] f = frame(mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
        double gs = mc.getWindow().getGuiScale();
        PhotoCache.shoot((int) (f[0] * gs), (int) (f[1] * gs), (int) (f[2] * gs));
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, 1.8F));
        shots++;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onClick(InputEvent.InteractionKeyMappingTriggered event) {
        if (!active) return;
        if (event.isAttack()) shoot();
        else if (event.isUseItem()) stop();
        event.setSwingHand(false);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (active && (mc.player == null || !mc.player.isAlive() || !com.breachprotocol.career.Clearance.hasPhone(mc.player))) active = false;
    }

    /** Any screen opening (inventory, pause, chat) puts the camera away. */
    @SubscribeEvent
    public static void onScreen(ScreenEvent.Opening event) {
        active = false;
    }

    /** Hide the normal HUD while the viewfinder is up. */
    @SubscribeEvent
    public static void hideHud(RenderGuiLayerEvent.Pre event) {
        if (active) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void draw(RenderGuiEvent.Post event) {
        if (!active) return;
        Minecraft mc = Minecraft.getInstance();
        GuiGraphics g = event.getGuiGraphics();
        Font font = mc.font;
        int w = g.guiWidth(), h = g.guiHeight();
        int[] f = frame(w, h);
        int x = f[0], y = f[1], s = f[2];
        int dim = 0x99000000, ink = Terminal.GREEN, faint = Terminal.DIM;
        // darken outside the square
        g.fill(0, 0, w, y, dim);
        g.fill(0, y + s, w, h, dim);
        g.fill(0, y, x, y + s, dim);
        g.fill(x + s, y, w, y + s, dim);
        // corners, thirds, centre mark
        int k = Math.max(10, s / 10);
        g.fill(x, y, x + k, y + 1, ink); g.fill(x, y, x + 1, y + k, ink);
        g.fill(x + s - k, y, x + s, y + 1, ink); g.fill(x + s - 1, y, x + s, y + k, ink);
        g.fill(x, y + s - 1, x + k, y + s, ink); g.fill(x, y + s - k, x + 1, y + s, ink);
        g.fill(x + s - k, y + s - 1, x + s, y + s, ink); g.fill(x + s - 1, y + s - k, x + s, y + s, ink);
        for (int i = 1; i < 3; i++) {
            g.fill(x + s * i / 3, y, x + s * i / 3 + 1, y + s, 0x2230FF70);
            g.fill(x, y + s * i / 3, x + s, y + s * i / 3 + 1, 0x2230FF70);
        }
        g.fill(w / 2 - 4, h / 2, w / 2 + 5, h / 2 + 1, ink);
        g.fill(w / 2, h / 2 - 4, w / 2 + 1, h / 2 + 5, ink);
        // header and footer, terminal style
        boolean blink = (System.currentTimeMillis() / 500) % 2 == 0;
        g.drawString(font, "FOUNDATION // CAMERA", x, y - 12, ink, false);
        String right = (blink ? "● " : "  ") + "LIVE";
        g.drawString(font, right, x + s - font.width(right), y - 12, blink ? Terminal.RED : faint, false);
        String help = "[ LMB ] CAPTURE    [ RMB ] PUT AWAY";
        g.drawString(font, help, w / 2 - font.width(help) / 2, y + s + 5, faint, false);
        if (shots > 0) {
            String saved = "[ OK ] " + shots + " SAVED TO GALLERY";
            g.drawString(font, saved, w / 2 - font.width(saved) / 2, y + s + 16, ink, false);
        }
        // SCP-096's face in the square
        String face = faceInView();
        if (face != null) {
            String a = "[ !! ] FACE IN FRAME: " + face, b = "CAPTURING THIS MEANS SEEING IT.";
            int bw = Math.max(font.width(a), font.width(b)) + 12;
            g.fill(w / 2 - bw / 2, y + 6, w / 2 + bw / 2, y + 32, 0xE0200608);
            Terminal.box(g, w / 2 - bw / 2, y + 6, bw, 26, Terminal.RED);
            g.drawString(font, a, w / 2 - font.width(a) / 2, y + 10, Terminal.RED, false);
            g.drawString(font, b, w / 2 - font.width(b) / 2, y + 21, 0xFFFF9C9C, false);
        }
        if (System.currentTimeMillis() < PhotoCache.flashUntil) g.fill(x, y, x + s, y + s, 0xCCFFFFFF);
    }

    /** Is SCP-096's face (SCP Unleashed's Scp096Sight.faceOf) in the square, turned toward you? */
    public static String faceInView() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return null;
        Vec3 eye = mc.player.getEyePosition(), look = mc.player.getViewVector(1.0F);
        for (Entity e : mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(40), UnleashedApi::isShyGuy)) {
            Vec3 face = UnleashedApi.faceOf(e), facing = UnleashedApi.facingOf(e);
            if (face == null || facing == null) continue;
            if (look.dot(face.subtract(eye).normalize()) < 0.93) continue;
            if (facing.normalize().dot(eye.subtract(face).normalize()) > 0.3) return e.getType().getDescription().getString();
        }
        return null;
    }
}
