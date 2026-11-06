package com.breachprotocol.career;

import com.breachprotocol.compat.Unleashed;
import com.breachprotocol.compat.UnleashedApi;
import com.breachprotocol.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * Clearance uses SCP Unleashed's own keycard rules (DossierClearance / KeycardItem): Level 1-5,
 * Omni = 6. A card fused into the phone stays in the phone and the phone swipes SCP Unleashed
 * keycard scanners with it. Effective clearance is the best of fused card, carried cards, and rank
 * (O5 and above count as 6, like Omni).
 */
public final class Clearance {
    private Clearance() {}

    public static final String FUSED = "breachprotocol_fused";
    public static final String FUSED_CARD = "breachprotocol_fused_card";

    private static CompoundTag data(ItemStack phone) {
        CustomData d = phone.get(DataComponents.CUSTOM_DATA);
        return d == null ? new CompoundTag() : d.copyTag();
    }

    public static int fused(ItemStack phone) {
        return phone.isEmpty() ? 0 : data(phone).getInt(FUSED);
    }

    /** The SCP Unleashed keycard item fused into this phone, or null. */
    public static Item fusedCard(ItemStack phone) {
        if (phone.isEmpty()) return null;
        String id = data(phone).getString(FUSED_CARD);
        if (id.isEmpty()) return null;
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl == null ? null : BuiltInRegistries.ITEM.getOptional(rl).orElse(null);
    }

    /** Keycard level of a stack by SCP Unleashed's rules: 1-5, Omni = 6, else 0. */
    public static int cardLevel(ItemStack s) {
        if (s.isEmpty() || s.is(ModItems.PHONE.get())) return 0;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(s.getItem());
        if (!Unleashed.MODID.equals(id.getNamespace())) return 0;
        return UnleashedApi.clearanceOf(s);
    }

    public static boolean isPhone(ItemStack s) {
        return s.is(ModItems.PHONE.get());
    }

    public static boolean hasPhone(Player p) {
        return !phoneOf(p).isEmpty();
    }

    public static ItemStack phoneOf(Player p) {
        if (isPhone(p.getMainHandItem())) return p.getMainHandItem();
        if (isPhone(p.getOffhandItem())) return p.getOffhandItem();
        for (ItemStack s : p.getInventory().items) if (isPhone(s)) return s;
        return ItemStack.EMPTY;
    }

    /** Best keycard you're carrying (what SCP Unleashed's DossierClearance.highest counts). */
    public static int carried(Player p) {
        int best = cardLevel(p.getOffhandItem());
        for (ItemStack s : p.getInventory().items) best = Math.max(best, cardLevel(s));
        return best;
    }

    public static int fromRank(Player p) {
        CareerData d = Career.data(p);
        if (d.ascended) return 6;
        Rank r = d.getRank();
        return r.atLeast(Rank.OVERSEER) ? 6 : r.clearance;
    }

    /** 0..5 = Level N, 6 = Omni / O5 / The Administrator / Ascendant. */
    public static int effective(Player p) {
        return Math.max(Math.max(fused(phoneOf(p)), carried(p)), fromRank(p));
    }

    // ------------------------------------------------------------------ linked devices

    public static final String LINKS = "breachprotocol_links";

    /** SCP Unleashed devices that can be linked into the phone, and the feature each one unlocks. */
    public static final String[][] DEVICES = {
            {"orbital_designator", "orbital"},
            {"mtf_signal_radio", "mtf"},
            {"rift_detector", "rifts"},
            {"camera_tablet", "cameras"},
            {"facility_tablet", "network"}};

    public static java.util.Set<String> links(ItemStack phone) {
        java.util.Set<String> out = new java.util.HashSet<>();
        if (phone.isEmpty()) return out;
        for (net.minecraft.nbt.Tag e : data(phone).getList(LINKS, net.minecraft.nbt.Tag.TAG_STRING)) out.add(e.getAsString());
        return out;
    }

    /** Is this feature linked into the phone you're carrying? */
    public static boolean linked(Player p, String feature) {
        return links(phoneOf(p)).contains(feature);
    }

    /** Links an SCP Unleashed device from your inventory into the phone (the device goes into the phone). */
    public static void link(ServerPlayer p, int deviceIndex) {
        if (deviceIndex < 0 || deviceIndex >= DEVICES.length) return;
        ItemStack phone = phoneOf(p);
        if (phone.isEmpty()) return;
        String[] dev = DEVICES[deviceIndex];
        if (links(phone).contains(dev[1])) return;
        Item item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath(Unleashed.MODID, dev[0])).orElse(null);
        if (item == null) return;
        ItemStack found = ItemStack.EMPTY;
        for (ItemStack s : p.getInventory().items) if (s.is(item)) { found = s; break; }
        if (found.isEmpty() && p.getOffhandItem().is(item)) found = p.getOffhandItem();
        if (found.isEmpty()) {
            p.displayClientMessage(Component.literal("You need a ").append(new ItemStack(item).getHoverName()).append(" in your inventory.").withStyle(ChatFormatting.RED), true);
            return;
        }
        Component name = found.getHoverName();
        if (!p.getAbilities().instabuild) found.shrink(1);
        CustomData.update(DataComponents.CUSTOM_DATA, phone, t -> {
            net.minecraft.nbt.ListTag l = t.getList(LINKS, net.minecraft.nbt.Tag.TAG_STRING);
            l.add(net.minecraft.nbt.StringTag.valueOf(dev[1]));
            t.put(LINKS, l);
        });
        p.getInventory().setChanged();
        p.level().playSound(null, p.blockPosition(), SoundEvents.SMITHING_TABLE_USE, SoundSource.PLAYERS, 0.8F, 1.6F);
        p.displayClientMessage(Component.literal("Linked to your phone: ").withStyle(ChatFormatting.GREEN).append(name), true);
    }

    /** Fuses the best carried keycard that beats the one already in the phone. */
    public static void fuse(ServerPlayer p) {
        ItemStack phone = phoneOf(p);
        if (phone.isEmpty()) return;
        int current = fused(phone);
        int slot = -1, best = current;
        for (int i = 0; i < p.getInventory().items.size(); i++) {
            int l = cardLevel(p.getInventory().items.get(i));
            if (l > best) {
                best = l;
                slot = i;
            }
        }
        if (slot < 0) {
            p.displayClientMessage(Component.literal(current > 0 ? "You aren't carrying a keycard better than the one in your phone." : "You aren't carrying an SCP Unleashed keycard.").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        ItemStack card = p.getInventory().items.get(slot);
        String cardId = BuiltInRegistries.ITEM.getKey(card.getItem()).toString();
        Component cardName = card.getHoverName();
        card.shrink(1);
        final int level = best;
        CustomData.update(DataComponents.CUSTOM_DATA, phone, t -> {
            t.putInt(FUSED, level);
            t.putString(FUSED_CARD, cardId);
        });
        p.getInventory().setChanged();
        p.level().playSound(null, p.blockPosition(), SoundEvents.SMITHING_TABLE_USE, SoundSource.PLAYERS, 0.8F, 1.4F);
        p.displayClientMessage(Component.literal("Fused into your phone: ").withStyle(ChatFormatting.AQUA).append(cardName), true);
    }
}
