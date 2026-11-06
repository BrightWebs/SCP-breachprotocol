package com.breachprotocol.client;

import com.breachprotocol.BreachProtocol;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

/**
 * The Map app's picture: the terrain around you read from the loaded world (top block colours,
 * shaded by height like a paper map) and redrawn in the phone's phosphor green.
 */
public final class PhoneMap {
    private PhoneMap() {}

    public static final int SIZE = 128;
    public static final ResourceLocation TEXTURE = BreachProtocol.id("phone_map");
    private static DynamicTexture texture;
    private static int centerX = Integer.MIN_VALUE, centerZ, builtZoom;
    private static long builtAt;

    public static int centerX() { return centerX; }
    public static int centerZ() { return centerZ; }

    /** Rebuilds when you've moved, changed zoom, or every few seconds. Returns false with no world. */
    public static boolean update(int zoom) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) return false;
        int px = mc.player.getBlockX(), pz = mc.player.getBlockZ();
        long now = System.currentTimeMillis();
        boolean moved = Math.abs(px - centerX) > 2 * zoom || Math.abs(pz - centerZ) > 2 * zoom;
        if (texture != null && !moved && zoom == builtZoom && now - builtAt < 4000) return true;
        if (texture == null) {
            texture = new DynamicTexture(SIZE, SIZE, false);
            mc.getTextureManager().register(TEXTURE, texture);
        }
        centerX = px;
        centerZ = pz;
        builtZoom = zoom;
        builtAt = now;
        NativeImage img = texture.getPixels();
        if (img == null) return false;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int[] prevRow = new int[SIZE];
        for (int j = 0; j < SIZE; j++) {
            for (int i = 0; i < SIZE; i++) {
                int x = px + (i - SIZE / 2) * zoom, z = pz + (j - SIZE / 2) * zoom;
                int argb;
                if (!level.hasChunk(x >> 4, z >> 4)) {
                    argb = 0xFF020604;
                    prevRow[i] = Integer.MIN_VALUE;
                } else {
                    int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                    pos.set(x, y, z);
                    BlockState s = level.getBlockState(pos);
                    MapColor mc2 = s.getMapColor(level, pos);
                    int rgb = mc2 == MapColor.NONE ? 0x101010 : mc2.col;
                    // shade by slope against the row above (north), like a paper map
                    float shade = 1.0F;
                    if (prevRow[i] != Integer.MIN_VALUE) shade = y > prevRow[i] ? 1.18F : y < prevRow[i] ? 0.78F : 1.0F;
                    prevRow[i] = y;
                    boolean water = !s.getFluidState().isEmpty();
                    argb = Terminal.map(0xFF000000 | scale(rgb, water ? 0.55F : shade));
                    if (water) argb = 0xFF000000 | ((argb & 0xFEFEFE) >> 1) | 0x101810;
                }
                img.setPixelRGBA(i, j, abgr(argb));
            }
        }
        texture.upload();
        return true;
    }

    private static int scale(int rgb, float k) {
        int r = Math.min(255, (int) (((rgb >> 16) & 255) * k)), g = Math.min(255, (int) (((rgb >> 8) & 255) * k)), b = Math.min(255, (int) ((rgb & 255) * k));
        return r << 16 | g << 8 | b;
    }

    /** NativeImage stores ABGR. */
    private static int abgr(int argb) {
        return (argb & 0xFF00FF00) | ((argb & 0xFF) << 16) | ((argb >> 16) & 0xFF);
    }
}
