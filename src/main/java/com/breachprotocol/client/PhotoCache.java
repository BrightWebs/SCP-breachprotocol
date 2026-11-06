package com.breachprotocol.client;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.network.PhoneActionPayload;
import com.breachprotocol.network.PhotoRequestPayload;
import com.breachprotocol.network.PhotoUploadPayload;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Real photos. The shutter grabs the frame the world was drawn into (before the HUD and phone are
 * drawn on top), crops the viewfinder square, shrinks it to a small PNG and uploads it. Pictures
 * are cached on disk and shown as textures in the Gallery.
 */
@EventBusSubscriber(modid = BreachProtocol.MODID, value = Dist.CLIENT)
public final class PhotoCache {
    private PhotoCache() {}

    private static final Map<Integer, ResourceLocation> TEXTURES = new HashMap<>();
    private static final Set<Integer> REQUESTED = new HashSet<>();
    private static final Set<Integer> MISSING = new HashSet<>();

    /** Pending capture: viewfinder square in framebuffer pixels. */
    private static int[] pending;
    public static long flashUntil;

    private static Path dir() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("breachprotocol").resolve("photos");
    }

    private static Path file(int img) {
        return dir().resolve(Integer.toUnsignedString(img) + ".png");
    }

    /** Asks for a capture of the given framebuffer square on the next frame. */
    public static void shoot(int x, int y, int size) {
        pending = new int[]{x, y, size};
    }

    @SubscribeEvent
    public static void beforeHud(RenderGuiEvent.Pre event) {
        capture();
    }

    /** With the HUD hidden (F1) there's no HUD event, so capture before the screen draws instead. */
    @SubscribeEvent
    public static void beforeScreen(ScreenEvent.Render.Pre event) {
        if (Minecraft.getInstance().options.hideGui) capture();
    }

    private static void capture() {
        int[] r = pending;
        if (r == null) return;
        pending = null;
        Minecraft mc = Minecraft.getInstance();
        try (NativeImage shot = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            int size = Math.max(16, Math.min(r[2], Math.min(shot.getWidth(), shot.getHeight())));
            int x = Math.max(0, Math.min(shot.getWidth() - size, r[0])), y = Math.max(0, Math.min(shot.getHeight() - size, r[1]));
            byte[] png = null;
            for (int out : new int[]{128, 112, 96, 80, 64}) {
                try (NativeImage small = new NativeImage(out, out, false)) {
                    shot.resizeSubRectTo(x, y, size, size, small);
                    png = small.asByteArray();
                }
                if (png.length <= PhotoUploadPayload.MAX_BYTES) break;
            }
            if (png == null || png.length > PhotoUploadPayload.MAX_BYTES) return;
            int img;
            do img = new Random().nextInt(); while (img == 0);
            Files.createDirectories(dir());
            Files.write(file(img), png);
            load(img, png);
            PacketDistributor.sendToServer(new PhotoUploadPayload(img, png));
            PacketDistributor.sendToServer(new PhoneActionPayload("photo_take", img));
            flashUntil = System.currentTimeMillis() + 180;
        } catch (Throwable t) {
            BreachProtocol.LOGGER.error("Photo capture failed", t);
            PacketDistributor.sendToServer(new PhoneActionPayload("photo_take", 0));
        }
    }

    private static void load(int img, byte[] png) {
        try {
            NativeImage image = NativeImage.read(png);
            ResourceLocation rl = BreachProtocol.id("photo/" + Integer.toUnsignedString(img));
            Minecraft.getInstance().getTextureManager().register(rl, new DynamicTexture(image));
            TEXTURES.put(img, rl);
        } catch (Exception e) {
            MISSING.add(img);
        }
    }

    /** The texture for a photo, or null while it loads (or if it was never uploaded). */
    public static ResourceLocation texture(int img) {
        if (img == 0 || MISSING.contains(img)) return null;
        ResourceLocation rl = TEXTURES.get(img);
        if (rl != null) return rl;
        try {
            Path f = file(img);
            if (Files.exists(f)) {
                load(img, Files.readAllBytes(f));
                return TEXTURES.get(img);
            }
        } catch (Exception ignored) {}
        if (REQUESTED.add(img)) PacketDistributor.sendToServer(new PhotoRequestPayload(img));
        return null;
    }

    public static boolean missing(int img) {
        return img == 0 || MISSING.contains(img);
    }

    public static void received(int img, byte[] png) {
        if (png.length == 0) { MISSING.add(img); return; }
        try {
            Files.createDirectories(dir());
            Files.write(file(img), png);
        } catch (Exception ignored) {}
        load(img, png);
    }
}
