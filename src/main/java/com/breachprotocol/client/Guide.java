package com.breachprotocol.client;

import com.breachprotocol.BreachProtocol;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * Guidance. Pick a mission (Missions, Map) or an anomaly (Containment) and press Guide: an arrow at the middle-right
 * of your screen turns toward it, with the distance and whether it's above or below you.
 */
@EventBusSubscriber(modid = BreachProtocol.MODID, value = Dist.CLIENT)
public final class Guide {
    private Guide() {}

    private static boolean active;
    private static int tx, ty, tz;
    /** When guiding to a creature: its entity id, followed live while it's loaded. -1 = a fixed spot. */
    private static int entityId = -1;
    private static String label = "";
    private static long arrivedAt;

    public static void start(int x, int y, int z, String name) {
        active = true;
        tx = x; ty = y; tz = z;
        label = name;
        arrivedAt = 0;
        entityId = -1;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.8F, 0.4F));
    }

    /** Guide to a creature; the arrow follows it while it's in range of your client. */
    public static void startEntity(int id, int x, int y, int z, String name) {
        start(x, y, z, name);
        entityId = id;
    }

    public static boolean guidingEntity(int id) {
        return active && entityId == id;
    }

    public static void stop() {
        active = false;
    }

    public static boolean guiding(int x, int z) {
        return active && tx == x && tz == z;
    }

    public static boolean active() {
        return active;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        active = false;
    }

    @SubscribeEvent
    public static void draw(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!active || mc.player == null || CameraMode.active() || mc.options.hideGui) return;
        GuiGraphics g = event.getGuiGraphics();
        Font font = mc.font;
        if (entityId >= 0 && mc.level != null) {
            net.minecraft.world.entity.Entity e = mc.level.getEntity(entityId);
            if (e != null && e.isAlive()) { tx = e.getBlockX(); ty = e.getBlockY(); tz = e.getBlockZ(); }
        }
        double dx = tx + 0.5 - mc.player.getX(), dz = tz + 0.5 - mc.player.getZ();
        int dy = ty - mc.player.getBlockY();
        int dist = (int) Math.sqrt(dx * dx + dz * dz);
        long now = System.currentTimeMillis();
        if (dist <= (entityId >= 0 ? 6 : 4) && Math.abs(dy) <= 4) {
            if (arrivedAt == 0) {
                arrivedAt = now;
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.6F, 0.6F));
            }
            if (now - arrivedAt > 3000) { active = false; return; }
        } else {
            arrivedAt = 0;
        }

        int w = 82, h = 90, x = g.guiWidth() - w - 6, y = g.guiHeight() / 2 - h / 2;
        g.fill(x, y, x + w, y + h, 0xC0020804);
        Terminal.box(g, x, y, w, h, Terminal.MID);
        g.drawString(font, "GUIDE", x + 5, y + 4, Terminal.DIM, false);
        String lb = label.toUpperCase();
        while (font.width(lb) * 0.75F > w - 10 && lb.length() > 1) lb = lb.substring(0, lb.length() - 1);
        g.pose().pushPose();
        g.pose().translate(x + 5, y + 14, 0);
        g.pose().scale(0.75F, 0.75F, 1);
        g.drawString(font, lb, 0, 0, Terminal.GREEN, false);
        g.pose().popPose();

        int cx = x + w / 2, cy = y + 42;
        if (arrivedAt != 0) {
            String a = "[ ARRIVED ]";
            g.drawString(font, a, cx - font.width(a) / 2, cy - 4, Terminal.BRIGHT, false);
        } else {
            // bearing relative to where you're facing; 0 = straight ahead
            float target = (float) Math.toDegrees(Math.atan2(-dx, dz));
            float rel = Mth.wrapDegrees(target - mc.player.getYRot());
            boolean ahead = Math.abs(rel) < 12;
            int ink = ahead ? Terminal.BRIGHT : Terminal.GREEN;
            g.pose().pushPose();
            g.pose().translate(cx, cy, 0);
            g.pose().mulPose(Axis.ZP.rotationDegrees(rel));
            g.fill(-2, -2, 2, 13, ink);                       // shaft
            for (int i = 0; i < 9; i++) g.fill(-(9 - i), -3 - i, 9 - i, -2 - i, ink); // head
            g.pose().popPose();
            // ring
            for (int i = 0; i < 48; i++) {
                double a = i * Math.PI * 2 / 48;
                int rx = cx + (int) Math.round(Math.cos(a) * 18), ry = cy + (int) Math.round(Math.sin(a) * 18);
                g.fill(rx, ry, rx + 1, ry + 1, Terminal.LINE);
            }
        }
        String d = dist >= 1000 ? String.format("%.1fKM", dist / 1000F) : dist + "M";
        g.drawString(font, d, cx - font.width(d) / 2, y + 63, Terminal.GREEN, false);
        // up high or underground?
        boolean underground = false;
        if (mc.level != null && mc.level.hasChunk(tx >> 4, tz >> 4)) {
            int surface = mc.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, tx, tz);
            underground = ty < surface - 3;
        }
        String v1, v2;
        int vc;
        if (dy > 3) { v1 = "\u25B2 HIGHER"; v2 = dy + "M UP"; vc = Terminal.AMBER; }
        else if (dy < -3) { v1 = underground ? "\u25BC UNDERGROUND" : "\u25BC LOWER"; v2 = (-dy) + "M DOWN"; vc = underground ? Terminal.RED : Terminal.AMBER; }
        else if (underground) { v1 = "\u25BC UNDERGROUND"; v2 = "SAME DEPTH"; vc = Terminal.RED; }
        else { v1 = "SAME LEVEL"; v2 = ""; vc = Terminal.DIM; }
        boolean show = dy <= 3 && dy >= -3 || (now / 450) % 2 == 0;
        g.pose().pushPose();
        g.pose().translate(cx, y + 73, 0);
        g.pose().scale(0.75F, 0.75F, 1);
        if (show) g.drawString(font, v1, -font.width(v1) / 2, 0, vc, false);
        if (!v2.isEmpty()) g.drawString(font, v2, -font.width(v2) / 2, 10, Terminal.DIM, false);
        g.pose().popPose();
    }
}
