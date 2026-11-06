package com.breachprotocol.career;

import com.breachprotocol.compat.UnleashedApi;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The phone camera. A photo remembers what was in frame. Catching SCP-096's face counts as seeing it
 * (SCP Unleashed's sighting rules), and the photo becomes a cognitohazard: opening it, or the
 * printed Face-Down Photograph, goes through SCP Unleashed's own photo handling.
 */
public final class Photos {
    private Photos() {}

    public static void take(ServerPlayer p) {
        CareerData d = Career.data(p);
        Vec3 eye = p.getEyePosition(), look = p.getViewVector(1.0F);
        List<String> subjects = new ArrayList<>();
        Entity hazard = null;
        for (Entity e : p.level().getEntities(p, p.getBoundingBox().inflate(40), en -> en instanceof LivingEntity)) {
            Vec3 to = e.position().add(0, e.getBbHeight() * 0.7, 0).subtract(eye);
            if (to.length() > 40 || look.dot(to.normalize()) < 0.93 || !p.hasLineOfSight(e)) continue;
            String name = UnleashedApi.isDClass(e) ? UnleashedApi.dclassDesignation(e) : e.getName().getString();
            if (!subjects.contains(name)) subjects.add(name);
            if (hazard == null && UnleashedApi.isShyGuy(e) && !UnleashedApi.isBagged(e) && faceInShot(p, e)) hazard = e;
        }
        CareerData.Photo photo = new CareerData.Photo();
        photo.id = d.nextPhotoId++;
        photo.time = p.level().getGameTime();
        photo.subjects = subjects.isEmpty() ? "Nothing of note" : String.join(", ", subjects);
        photo.hazard = hazard != null;
        photo.hazardEntity = hazard == null ? "" : hazard.getUUID().toString();
        photo.x = p.getBlockX();
        photo.z = p.getBlockZ();
        photo.author = p.getName().getString();
        d.photos.add(0, photo);
        while (d.photos.size() > CareerData.MAX_PHOTOS) d.photos.remove(d.photos.size() - 1);
        d.dirty = true;
        p.level().playSound(null, p.blockPosition(), SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.PLAYERS, 0.9F, 1.6F);
        p.displayClientMessage(Component.literal(photo.hazard ? "Photo saved. You saw its face through the lens." : "Photo saved: " + photo.subjects)
                .withStyle(photo.hazard ? ChatFormatting.RED : ChatFormatting.GRAY), true);
        // SCP Unleashed's dossier: viewing its face "directly, through a camera or in a photograph" triggers it.
        if (hazard != null && !Career.isAscended(p)) UnleashedApi.reportSighting(hazard, p);
    }

    /** SCP Unleashed's own geometry: 096's face (Scp096Sight.faceOf) turned towards the lens. */
    public static boolean faceInShot(ServerPlayer p, Entity shyGuy) {
        Vec3 face = UnleashedApi.faceOf(shyGuy), facing = UnleashedApi.facingOf(shyGuy);
        if (face == null || facing == null) return false;
        Vec3 eye = p.getEyePosition(), toEye = eye.subtract(face).normalize();
        if (facing.normalize().dot(toEye) < 0.3) return false;
        if (p.getViewVector(1.0F).dot(face.subtract(eye).normalize()) < 0.93) return false;
        return UnleashedApi.clearSight(p.serverLevel(), eye, face);
    }

    private static CareerData.Photo find(ServerPlayer p, int id) {
        for (CareerData.Photo ph : Career.data(p).photos) if (ph.id == id) return ph;
        return null;
    }

    /** Looking at a photo of 096's face. SCP Unleashed handles it exactly as its own Face-Down Photograph. */
    public static void view(ServerPlayer viewer) {
        if (Career.isAscended(viewer)) {
            viewer.displayClientMessage(Component.literal("The face in the photo looks away from you.").withStyle(ChatFormatting.LIGHT_PURPLE), true);
            return;
        }
        UnleashedApi.viewFacePhoto(viewer);
    }

    public static void open(ServerPlayer p, int id) {
        CareerData.Photo ph = find(p, id);
        if (ph != null && ph.hazard) view(p);
    }

    public static void delete(ServerPlayer p, int id) {
        Career.data(p).photos.removeIf(ph -> ph.id == id);
        Career.data(p).dirty = true;
    }

    /** Prints a photo of 096's face as SCP Unleashed's Face-Down Photograph. Ordinary photos stay digital. */
    public static void print(ServerPlayer p, int id) {
        CareerData.Photo ph = find(p, id);
        if (ph == null) return;
        if (!ph.hazard) {
            p.displayClientMessage(Component.literal("Only cognitohazard photos are printed. Send ordinary ones instead.").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        ItemStack s = UnleashedApi.facePhotoItem();
        if (s.isEmpty()) return;
        if (!p.getInventory().add(s)) p.drop(s, false);
        p.displayClientMessage(Component.literal("Printed. Keep it face down.").withStyle(ChatFormatting.RED), true);
    }

    /** Sends a copy to the nearest other player within 16 blocks; it lands in their gallery. */
    public static void share(ServerPlayer p, int id) {
        CareerData.Photo ph = find(p, id);
        if (ph == null) return;
        ServerPlayer nearest = null;
        double best = 16 * 16;
        for (ServerPlayer o : p.serverLevel().players()) {
            if (o != p && o.distanceToSqr(p) < best) { best = o.distanceToSqr(p); nearest = o; }
        }
        if (nearest == null) {
            p.displayClientMessage(Component.literal("Nobody within 16 blocks to send it to.").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        CareerData od = Career.data(nearest);
        CareerData.Photo copy = CareerData.Photo.load(ph.save());
        copy.id = od.nextPhotoId++;
        od.photos.add(0, copy);
        od.dirty = true;
        Career.message(nearest, p.getName().getString(), "Sent you a photo: " + copy.subjects);
        p.displayClientMessage(Component.literal("Sent to " + nearest.getName().getString() + ".").withStyle(ChatFormatting.GRAY), true);
    }

    /** A framed Face-Down Photograph is face up. Looking right at it counts as viewing it. */
    public static void checkFrames(ServerPlayer p) {
        Vec3 eye = p.getEyePosition(), look = p.getViewVector(1.0F);
        long now = p.level().getGameTime();
        for (ItemFrame f : p.level().getEntitiesOfClass(ItemFrame.class, p.getBoundingBox().inflate(6))) {
            if (!UnleashedApi.isFacePhoto(f.getItem())) continue;
            Vec3 to = f.position().subtract(eye);
            if (look.dot(to.normalize()) > 0.95 && p.hasLineOfSight(f)) {
                CompoundTag pd = p.getPersistentData();
                if (now - pd.getLong("breachprotocol_frame_seen") < 20 * 30) continue;
                pd.putLong("breachprotocol_frame_seen", now);
                view(p);
            }
        }
    }
}
