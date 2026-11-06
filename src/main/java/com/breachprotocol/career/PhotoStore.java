package com.breachprotocol.career;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.network.PhotoDataPayload;
import com.breachprotocol.network.PhotoUploadPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.network.PacketDistributor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Photo pictures live in the world folder (breachprotocol_photos/<img>.png) so they can be shared. */
public final class PhotoStore {
    private PhotoStore() {}

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G'};
    private static final Map<UUID, Long> LAST_UPLOAD = new HashMap<>();

    private static Path file(ServerPlayer p, int img) {
        return p.server.getWorldPath(LevelResource.ROOT).resolve("breachprotocol_photos").resolve(Integer.toUnsignedString(img) + ".png");
    }

    public static void upload(ServerPlayer p, int img, byte[] png) {
        if (img == 0 || png.length < 8 || png.length > PhotoUploadPayload.MAX_BYTES) return;
        for (int i = 0; i < PNG.length; i++) if (png[i] != PNG[i]) return;
        long now = System.currentTimeMillis();
        Long last = LAST_UPLOAD.get(p.getUUID());
        if (last != null && now - last < 1500) return;
        LAST_UPLOAD.put(p.getUUID(), now);
        try {
            Path f = file(p, img);
            if (Files.exists(f)) return;
            Files.createDirectories(f.getParent());
            Files.write(f, png);
        } catch (Exception e) {
            BreachProtocol.LOGGER.warn("Could not save photo {}", img, e);
        }
    }

    /** Only photos in the requester's own gallery are sent. */
    public static void request(ServerPlayer p, int img) {
        boolean mine = Career.data(p).photos.stream().anyMatch(ph -> ph.img == img);
        byte[] data = new byte[0];
        if (mine) {
            try {
                Path f = file(p, img);
                if (Files.exists(f)) data = Files.readAllBytes(f);
            } catch (Exception e) {
                BreachProtocol.LOGGER.warn("Could not read photo {}", img, e);
            }
        }
        if (data.length > PhotoUploadPayload.MAX_BYTES) data = new byte[0];
        PacketDistributor.sendToPlayer(p, new PhotoDataPayload(img, data));
    }
}
