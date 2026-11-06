package com.breachprotocol.career;

import com.breachprotocol.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;

import java.util.UUID;

/**
 * Player phones. Standing within 8 blocks of someone you can ask for their number; once they
 * accept, you're in each other's contacts and can text from the Phone and Messages apps.
 */
public final class Contacts {
    private Contacts() {}

    public static final double RANGE = 8;
    public static final int MAX_TEXT = 120;

    /** A stable phone number for a player, e.g. "555-0142". */
    public static String number(UUID id) {
        return String.format("555-%04d", Math.floorMod(id.getLeastSignificantBits() ^ id.getMostSignificantBits(), 10000));
    }

    private static boolean has(CareerData d, UUID id) {
        String s = id.toString();
        return d.contacts.stream().anyMatch(c -> c.uuid.equals(s));
    }

    private static void say(ServerPlayer p, String msg, ChatFormatting color) {
        p.displayClientMessage(Component.literal(msg).withStyle(color), true);
    }

    private static CareerData.Contact of(ServerPlayer p) {
        CareerData.Contact c = new CareerData.Contact();
        c.uuid = p.getUUID().toString();
        c.name = p.getGameProfile().getName();
        c.time = p.level().getGameTime();
        return c;
    }

    /** Ask a nearby player (by entity id) for their number. */
    public static void request(ServerPlayer p, int entityId) {
        if (!(p.level().getEntity(entityId) instanceof ServerPlayer target) || target == p || target.distanceTo(p) > RANGE) {
            say(p, "[ NO SIGNAL ] They need to be within 8 blocks.", ChatFormatting.RED);
            return;
        }
        if (!Clearance.hasPhone(target)) {
            say(p, "[ NO DEVICE ] " + target.getGameProfile().getName() + " isn't carrying a phone.", ChatFormatting.RED);
            return;
        }
        CareerData td = Career.data(target);
        if (has(Career.data(p), target.getUUID())) {
            say(p, "[ OK ] Already in your contacts.", ChatFormatting.GREEN);
            return;
        }
        td.requests.removeIf(c -> c.uuid.equals(p.getUUID().toString()));
        td.requests.add(0, of(p));
        while (td.requests.size() > 8) td.requests.remove(td.requests.size() - 1);
        td.dirty = true;
        target.sendSystemMessage(Component.literal("[ PHONE ] ").withStyle(ChatFormatting.GREEN)
                .append(Component.literal(p.getGameProfile().getName() + " wants to exchange numbers. Open Phone > Requests.").withStyle(ChatFormatting.GRAY)));
        target.playNotifySound(ModSounds.PHONE_RING.get(), SoundSource.PLAYERS, 0.6F, 1.4F);
        say(p, "[ SENT ] Request sent to " + target.getGameProfile().getName() + ".", ChatFormatting.GREEN);
        Career.sync(target);
    }

    public static void accept(ServerPlayer p, int index) {
        CareerData d = Career.data(p);
        if (index < 0 || index >= d.requests.size()) return;
        CareerData.Contact req = d.requests.remove(index);
        d.dirty = true;
        ServerPlayer other = p.server.getPlayerList().getPlayer(UUID.fromString(req.uuid));
        if (other == null) {
            say(p, "[ NO SIGNAL ] " + req.name + " went offline. Ask again later.", ChatFormatting.RED);
            return;
        }
        CareerData od = Career.data(other);
        if (!has(d, other.getUUID())) d.contacts.add(of(other));
        if (!has(od, p.getUUID())) od.contacts.add(of(p));
        od.dirty = true;
        say(p, "[ OK ] " + other.getGameProfile().getName() + " added. " + number(other.getUUID()), ChatFormatting.GREEN);
        other.sendSystemMessage(Component.literal("[ PHONE ] ").withStyle(ChatFormatting.GREEN)
                .append(Component.literal(p.getGameProfile().getName() + " accepted. Their number is " + number(p.getUUID()) + ".").withStyle(ChatFormatting.GRAY)));
        other.playNotifySound(ModSounds.PHONE_MESSAGE.get(), SoundSource.PLAYERS, 0.8F, 1.2F);
        Career.sync(other);
    }

    public static void decline(ServerPlayer p, int index) {
        CareerData d = Career.data(p);
        if (index >= 0 && index < d.requests.size()) {
            d.requests.remove(index);
            d.dirty = true;
        }
    }

    public static void remove(ServerPlayer p, int index) {
        CareerData d = Career.data(p);
        if (index >= 0 && index < d.contacts.size()) {
            d.contacts.remove(index);
            d.dirty = true;
        }
    }

    /** Text a contact. Both phones keep the conversation (thread "@Name"). */
    public static void text(ServerPlayer p, int index, String body) {
        CareerData d = Career.data(p);
        if (index < 0 || index >= d.contacts.size()) return;
        body = body.strip();
        if (body.isEmpty()) return;
        if (body.length() > MAX_TEXT) body = body.substring(0, MAX_TEXT);
        CareerData.Contact c = d.contacts.get(index);
        ServerPlayer other = p.server.getPlayerList().getPlayer(UUID.fromString(c.uuid));
        if (other == null) {
            say(p, "[ NO SIGNAL ] " + c.name + " is offline.", ChatFormatting.RED);
            return;
        }
        String me = p.getGameProfile().getName();
        c.name = other.getGameProfile().getName();
        d.addMessage("@" + c.name, body, p.level().getGameTime());
        CareerData.Message sent = d.messages.get(0);
        sent.out = true;
        sent.read = true;
        if (!has(Career.data(other), p.getUUID())) {
            say(p, "[ BLOCKED ] " + c.name + " removed your number.", ChatFormatting.RED);
            return;
        }
        Career.message(other, "@" + me, body);
        Career.sync(other);
    }

    public static void addToSync(ServerPlayer p, CompoundTag t) {
        CareerData d = Career.data(p);
        t.putString("myNumber", number(p.getUUID()));
        ListTag cs = new ListTag();
        for (CareerData.Contact c : d.contacts) {
            CompoundTag ct = new CompoundTag();
            ct.putString("n", c.name);
            try {
                UUID id = UUID.fromString(c.uuid);
                ct.putString("num", number(id));
                ct.putBoolean("on", p.server.getPlayerList().getPlayer(id) != null);
            } catch (IllegalArgumentException ignored) {}
            cs.add(ct);
        }
        t.put("contactList", cs);
        ListTag rq = new ListTag();
        for (CareerData.Contact c : d.requests) {
            CompoundTag ct = new CompoundTag();
            ct.putString("n", c.name);
            rq.add(ct);
        }
        t.put("requestList", rq);
        ListTag near = new ListTag();
        for (ServerPlayer o : p.serverLevel().players()) {
            if (o == p || o.isSpectator() || o.distanceTo(p) > RANGE) continue;
            CompoundTag nt = new CompoundTag();
            nt.putInt("id", o.getId());
            nt.putString("n", o.getGameProfile().getName());
            nt.putBoolean("phone", Clearance.hasPhone(o));
            nt.putBoolean("known", has(d, o.getUUID()));
            nt.putInt("m", (int) o.distanceTo(p));
            near.add(nt);
        }
        t.put("nearPlayers", near);
    }
}
