package com.breachprotocol.career;

import com.breachprotocol.compat.Unleashed;
import com.breachprotocol.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * Clearance comes from SCP Unleashed's keycards. Carry one and it counts; fuse it into the phone
 * and it can never be lost. Your effective clearance is the best of: fused card, carried cards,
 * and your career rank. Apps above your clearance simply don't appear.
 */
public final class Clearance {
    private Clearance() {}

    public static final String FUSED = "breachprotocol_fused";

    public static int fused(ItemStack phone) {
        CustomData d = phone.get(DataComponents.CUSTOM_DATA);
        return d == null ? 0 : d.copyTag().getInt(FUSED);
    }

    public static void setFused(ItemStack phone, int level) {
        CustomData.update(DataComponents.CUSTOM_DATA, phone, t -> t.putInt(FUSED, level));
    }

    /** SCP Unleashed keycard level of a stack (omni counts as 5), or 0. */
    public static int cardLevel(ItemStack s) {
        if (s.isEmpty()) return 0;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(s.getItem());
        if (!Unleashed.MODID.equals(id.getNamespace())) return 0;
        String p = id.getPath();
        if (p.equals("keycard_omni")) return 5;
        if (p.startsWith("keycard_level_")) {
            try {
                return Integer.parseInt(p.substring("keycard_level_".length()));
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    public static ItemStack phoneOf(Player p) {
        if (p.getMainHandItem().is(ModItems.PHONE.get())) return p.getMainHandItem();
        if (p.getOffhandItem().is(ModItems.PHONE.get())) return p.getOffhandItem();
        for (ItemStack s : p.getInventory().items) if (s.is(ModItems.PHONE.get())) return s;
        return ItemStack.EMPTY;
    }

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

    /** 0..5 = Level N, 6 = O5 / The Administrator / Ascendant. */
    public static int effective(Player p) {
        return Math.max(Math.max(fused(phoneOf(p)), carried(p)), fromRank(p));
    }

    /** Consumes the best carried keycard that beats the fused one and fuses it into the phone. */
    public static void fuse(ServerPlayer p) {
        ItemStack phone = phoneOf(p);
        if (phone.isEmpty()) return;
        int current = fused(phone);
        ItemStack bestStack = ItemStack.EMPTY;
        int best = current;
        for (ItemStack s : p.getInventory().items) {
            int l = cardLevel(s);
            if (l > best) {
                best = l;
                bestStack = s;
            }
        }
        if (bestStack.isEmpty()) {
            p.displayClientMessage(Component.literal("No keycard better than your fused Level " + current + " card.").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        bestStack.shrink(1);
        setFused(phone, best);
        p.level().playSound(null, p.blockPosition(), SoundEvents.SMITHING_TABLE_USE, SoundSource.PLAYERS, 0.8F, 1.4F);
        p.displayClientMessage(Component.literal("Level " + best + " keycard fused into your phone.").withStyle(ChatFormatting.AQUA), true);
    }
}
