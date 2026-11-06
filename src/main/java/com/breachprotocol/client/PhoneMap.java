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

    public static final int SIZE = 256;
    public static final ResourceLocation TEXTURE = BreachProtocol.id("phone_map");
    private static DynamicTexture texture;
    private static int centerX = Integer.MIN_VALUE, centerZ;
    private static float builtZoom;
    private static long builtAt;

    public static int centerX() { return centerX; }
    public static int centerZ() { return centerZ; }

    /** Rebuilds when you've moved, changed zoom, or every few seconds. Returns false with no world. */
    public static boolean update(float zoom) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) return false;
        int px = mc.player.getBlockX(), pz = mc.player.getBlockZ();
        long now = System.currentTimeMillis();
        boolean moved = Math.abs(px - centerX) > Math.max(1, 2 * zoom) || Math.abs(pz - centerZ) > Math.max(1, 2 * zoom);
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
                int x = px + (int) Math.floor((i - SIZE / 2) * zoom), z = pz + (int) Math.floor((j - SIZE / 2) * zoom);
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
                    if (prevRow[i] != Integer.MIN_VALUE) {
                        int d = y - prevRow[i];
                        shade = d > 1 ? 1.28F : d > 0 ? 1.14F : d < -1 ? 0.66F : d < 0 ? 0.82F : 1.0F;
                    }
                    prevRow[i] = y;
                    boolean water = !s.getFluidState().isEmpty();
                    if (water) {
                        // deeper water reads darker
                        int depth = 0;
                        BlockPos.MutableBlockPos q = pos.mutable();
                        while (depth < 12 && !level.getBlockState(q.move(0, -1, 0)).getFluidState().isEmpty()) depth++;
                        argb = 0xFF000000 | rgbOf(10, (int) (70 - depth * 3.5F), (int) (52 - depth * 2.5F));
                    } else {
                        argb = Terminal.map(0xFF000000 | scale(rgb, shade));
                    }
                }
                img.setPixelRGBA(i, j, abgr(argb));
            }
        }
        texture.upload();
        return true;
    }

    private static int rgbOf(int r, int g, int b) {
        return Math.max(0, Math.min(255, r)) << 16 | Math.max(0, Math.min(255, g)) << 8 | Math.max(0, Math.min(255, b));
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
