package com.breachprotocol.career;

import com.breachprotocol.compat.Unleashed;
import com.breachprotocol.compat.UnleashedApi;
import com.breachprotocol.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The phone camera. A photo remembers what was in frame. If SCP-096's face was in it, the photo is a
 * cognitohazard: whoever opens it - in the gallery, as a printed photograph, or by looking at a
 * framed copy - becomes one of 096's targets.
 */
public final class Photos {
    private Photos() {}

    public static final String TAG = "breachprotocol_photo";

    public static void take(ServerPlayer p) {
        CareerData d = Career.data(p);
        Vec3 eye = p.getEyePosition(), look = p.getViewVector(1.0F);
        List<String> subjects = new ArrayList<>();
        String hazard = "";
        for (Entity e : p.level().getEntities(p, p.getBoundingBox().inflate(40), en -> en instanceof LivingEntity)) {
            Vec3 to = e.position().add(0, e.getBbHeight() * 0.7, 0).subtract(eye);
            double dist = to.length();
            if (dist > 40 || look.dot(to.normalize()) < 0.93 || !p.hasLineOfSight(e)) continue;
            String name = Unleashed.isAnomaly(e) ? Unleashed.designation(e) : UnleashedApi.isDClass(e) ? UnleashedApi.dclassDesignation(e) : e.getName().getString();
            if (!subjects.contains(name)) subjects.add(name);
            if (hazard.isEmpty() && UnleashedApi.isShyGuy(e) && !UnleashedApi.isBagged(e)) {
                Vec3 facing = Vec3.directionFromRotation(0, ((LivingEntity) e).getYHeadRot());
                if (facing.dot(to.scale(-1).normalize()) > 0.25) hazard = e.getUUID().toString();
            }
        }
        CareerData.Photo photo = new CareerData.Photo();
        photo.id = d.nextPhotoId++;
        photo.time = p.level().getGameTime();
        photo.subjects = subjects.isEmpty() ? "Nothing of note" : String.join(", ", subjects);
        photo.hazard = !hazard.isEmpty();
        photo.hazardEntity = hazard;
        photo.x = p.getBlockX();
        photo.z = p.getBlockZ();
        photo.author = p.getName().getString();
        d.photos.add(0, photo);
        while (d.photos.size() > CareerData.MAX_PHOTOS) d.photos.remove(d.photos.size() - 1);
        d.dirty = true;
        p.level().playSound(null, p.blockPosition(), SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.PLAYERS, 0.9F, 1.6F);
        p.displayClientMessage(Component.literal(photo.hazard ? "Photo saved. The camera flagged a face." : "Photo saved: " + photo.subjects)
                .withStyle(photo.hazard ? ChatFormatting.RED : ChatFormatting.GRAY), true);
        if (photo.hazard) Career.addXp(p, 20, "documented SCP-096");
    }

    private static CareerData.Photo find(ServerPlayer p, int id) {
        for (CareerData.Photo ph : Career.data(p).photos) if (ph.id == id) return ph;
        return null;
    }

    /** Looking at the photo. A hazardous one marks the viewer for SCP-096. */
    public static void view(ServerPlayer viewer, boolean hazard, String entity) {
        if (!hazard) return;
        if (Career.isAscended(viewer)) {
            viewer.displayClientMessage(Component.literal("The face in the photo looks away from you.").withStyle(ChatFormatting.LIGHT_PURPLE), true);
            return;
        }
        Entity shyGuy = null;
        try {
            UUID id = UUID.fromString(entity);
            for (ServerLevel l : viewer.server.getAllLevels()) {
                shyGuy = l.getEntity(id);
                if (shyGuy != null) break;
            }
        } catch (IllegalArgumentException ignored) {}
        viewer.displayClientMessage(Component.literal("You saw its face.").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), true);
        if (shyGuy == null || !UnleashedApi.enrage096(shyGuy, viewer)) {
            viewer.sendSystemMessage(Component.literal("Somewhere far away, something starts screaming.").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
        }
    }

    public static void open(ServerPlayer p, int id) {
        CareerData.Photo ph = find(p, id);
        if (ph != null) view(p, ph.hazard, ph.hazardEntity);
    }

    public static void delete(ServerPlayer p, int id) {
        Career.data(p).photos.removeIf(ph -> ph.id == id);
        Career.data(p).dirty = true;
    }

    /** Prints the photo as a physical photograph you can hand to someone or frame. */
    public static void print(ServerPlayer p, int id) {
        CareerData.Photo ph = find(p, id);
        if (ph == null) return;
        ItemStack s = new ItemStack(ModItems.PHOTOGRAPH.get());
        CustomData.update(DataComponents.CUSTOM_DATA, s, t -> t.put(TAG, ph.save()));
        if (!p.getInventory().add(s)) p.drop(s, false);
        p.displayClientMessage(Component.literal("Photograph printed.").withStyle(ChatFormatting.GRAY), true);
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

    public static CompoundTag photoTag(ItemStack s) {
        CustomData d = s.get(DataComponents.CUSTOM_DATA);
        return d == null ? null : d.copyTag().getCompound(TAG);
    }

    /** Framed hazardous photographs mark anyone who looks right at them from close by. */
    public static void checkFrames(ServerPlayer p) {
        Vec3 eye = p.getEyePosition(), look = p.getViewVector(1.0F);
        for (ItemFrame f : p.level().getEntitiesOfClass(ItemFrame.class, p.getBoundingBox().inflate(6))) {
            ItemStack s = f.getItem();
            if (!s.is(ModItems.PHOTOGRAPH.get())) continue;
            CompoundTag t = photoTag(s);
            if (t == null || !t.getBoolean("hazard")) continue;
            Vec3 to = f.position().subtract(eye);
            if (look.dot(to.normalize()) > 0.95 && p.hasLineOfSight(f)) {
                String key = "breachprotocol_seen_" + t.getString("hazardEntity");
                if (!p.getPersistentData().getBoolean(key)) {
                    p.getPersistentData().putBoolean(key, true);
                    view(p, true, t.getString("hazardEntity"));
                }
            }
        }
    }
}
