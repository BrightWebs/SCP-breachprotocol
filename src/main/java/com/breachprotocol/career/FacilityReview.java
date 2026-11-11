package com.breachprotocol.career;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.world.SiteTower;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import com.breachprotocol.world.FacilityBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Facility review, from the phone or the Administrator's Terminal: pick a site, see what's missing or wrong
 * floor by floor against the plan it was built from, and have it put right. Nothing is touched until you say so;
 * what someone changed by hand is listed and left alone.
 */
public final class FacilityReview {
    private FacilityReview() {}

    /** Each player's last review (shown until closed). */
    private static final Map<UUID, CompoundTag> REPORTS = new HashMap<>();

    static boolean may(ServerPlayer p, Sites.Site s) {
        return Vault.chief(p) || s.owner.equals(p.getUUID().toString()) || p.hasPermissions(2);
    }

    public static void action(ServerPlayer p, String a) {
        if (a.equals("close")) { REPORTS.remove(p.getUUID()); Career.sync(p); return; }
        String[] pa = a.split(":");
        if (pa.length != 2) return;
        int i;
        try { i = Integer.parseInt(pa[1]); } catch (NumberFormatException e) { return; }
        List<Sites.Site> all = Sites.get(p.server).sites;
        if (i < 0 || i >= all.size()) return;
        Sites.Site s = all.get(i);
        if (!may(p, s)) { p.displayClientMessage(Component.literal("Only the Administrator or the site's owner can review " + s.name + ".").withStyle(ChatFormatting.RED), true); return; }
        ServerLevel l = p.server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(s.dim)));
        if (l == null) return;
        BlockPos o = Secretary.origin(s);
        BlockPos desk = Desk.deskSpot(s);
        l.getChunk(desk.getX() >> 4, desk.getZ() >> 4);
        if (!Secretary.isTower(l, s)) {
            CompoundTag r = new CompoundTag();
            r.putInt("i", i);
            r.putString("n", s.name);
            r.putBoolean("none", true);
            r.putLong("at", System.nanoTime());
            REPORTS.put(p.getUUID(), r);
            Career.sync(p);
            return;
        }
        String act = pa[0];
        if (!act.equals("check") && !act.equals("fix") && !act.equals("clear") && !act.equals("clearall")) return;
        int put = 0, cleared = 0;
        if (!act.equals("check")) {
            long t0 = System.nanoTime();
            if (!act.equals("fix")) cleared = clear(p, l, s, o, act.equals("clearall"));
            put = SiteTower.patch(l, o);
            BreachProtocol.LOGGER.info("Facility review: {} {} {} ({} cleared, {} put right, {} ms)", p.getGameProfile().getName(), act, s.name, cleared, put, (System.nanoTime() - t0) / 1_000_000);
            String msg = s.name + ": " + (put + cleared == 0 ? "nothing needed doing." : (cleared > 0 ? cleared + " unwanted blocks cleared" + (Vault.has(p.server, p.getUUID()) ? " (to your Vault storeroom)" : "") + ", " : "") + put + " blocks put right.");
            p.displayClientMessage(Component.literal(msg).withStyle(ChatFormatting.GREEN), true);
            p.playNotifySound(SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.4F, 1.4F);
        }
        // the review itself (after a repair too, so you see where it stands now)
        Map<String, int[]> areas = new java.util.LinkedHashMap<>();
        int[] tot = new int[7];
        for (FacilityBuilder.Plan.Diff d : SiteTower.survey(l, o)) {
            int k = d.kind() == 2 ? kind(p, s, l, d) : d.kind();
            tot[k]++;
            int[] v = areas.computeIfAbsent(SiteTower.area(o, d.pos()), x -> new int[4]);
            if (k == MISSING) v[0]++;
            else if (k == BLOCKED) v[1]++;
            else if (k == OTHERS || k == STRAY) v[2]++;
            else if (k == UNKNOWN) v[3]++;
        }
        CompoundTag r = new CompoundTag();
        r.putInt("i", i);
        r.putString("n", s.name);
        r.putInt("put", put);
        r.putInt("cleared", cleared);
        r.putBoolean("done", !act.equals("check"));
        r.putLong("at", System.nanoTime()); // every report is new to the screen, even an identical one
        ListTag rows = new ListTag();
        for (Map.Entry<String, int[]> e : areas.entrySet()) {
            int[] v = e.getValue();
            if (v[0] + v[1] + v[2] + v[3] == 0) continue;
            CompoundTag row = new CompoundTag();
            row.putString("a", e.getKey());
            row.putInt("m", v[0]);
            row.putInt("f", v[1]);
            row.putInt("o", v[2]);
            row.putInt("u", v[3]);
            rows.add(row);
        }
        r.put("rows", rows);
        r.putInt("m", tot[MISSING]);
        r.putInt("f", tot[BLOCKED]);
        r.putInt("yours", tot[YOURS]);
        r.putInt("kept", tot[KEPT]);
        r.putInt("others", tot[OTHERS]);
        r.putInt("stray", tot[STRAY]);
        r.putInt("u", tot[UNKNOWN]);
        REPORTS.put(p.getUUID(), r);
        Career.sync(p);
    }

    // ---------------------------------------------------------------- what's there that the plan doesn't have

    static final int MISSING = 0, BLOCKED = 1, YOURS = 2, KEPT = 3, OTHERS = 4, STRAY = 5, UNKNOWN = 6;

    /** Something that isn't the plan's: yours (kept), the Foundation's own fittings (kept), someone else's, stray, or of unknown origin. */
    private static int kind(ServerPlayer p, Sites.Site s, ServerLevel l, FacilityBuilder.Plan.Diff d) {
        UUID who = Placed.who(l, d.pos());
        if (who != null && (who.equals(p.getUUID()) || who.toString().equals(s.owner))) return YOURS;
        BlockState w = d.world();
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(w.getBlock());
        String path = id.getPath();
        if (id.getNamespace().equals(BreachProtocol.MODID) || path.contains("platform") || path.contains("wire") || path.contains("cable")
                || path.contains("outlet") || path.contains("terminal") || path.contains("launch")) return KEPT;
        if (who != null) return OTHERS;
        if (w.getBlock() instanceof LiquidBlock || w.getBlock() instanceof BaseFireBlock || w.is(Blocks.COBWEB) || w.is(Blocks.SNOW) || w.is(Blocks.VINE)
                || w.is(Blocks.GLOW_LICHEN) || w.is(Blocks.SCULK_VEIN) || w.is(Blocks.POWDER_SNOW)) return STRAY;
        return UNKNOWN;
    }

    /** Clears what's unwanted (someone else's, stray, and with unknownToo whatever nobody's on record for); what it was goes to your Vault storeroom. */
    private static int clear(ServerPlayer p, ServerLevel l, Sites.Site s, BlockPos o, boolean unknownToo) {
        List<ItemStack> got = new ArrayList<>();
        int n = 0;
        for (FacilityBuilder.Plan.Diff d : SiteTower.survey(l, o)) {
            if (d.kind() != 2) continue;
            int k = kind(p, s, l, d);
            if (k != OTHERS && k != STRAY && !(unknownToo && k == UNKNOWN)) continue;
            BlockPos pos = d.pos();
            BlockState w = l.getBlockState(pos);
            if (l.getBlockEntity(pos) instanceof Container c) {
                for (int slot = 0; slot < c.getContainerSize(); slot++) if (!c.getItem(slot).isEmpty()) got.add(c.getItem(slot).copy());
                c.clearContent();
            }
            boolean secondHalf = w.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && w.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER
                    || w.hasProperty(BlockStateProperties.BED_PART) && w.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD;
            if (!secondHalf && !(w.getBlock() instanceof LiquidBlock) && !(w.getBlock() instanceof BaseFireBlock)) {
                ItemStack it = new ItemStack(w.getBlock().asItem());
                if (!it.isEmpty()) got.add(it);
            }
            l.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS);
            Placed.forget(l, pos);
            n++;
        }
        for (ItemStack it : got) {
            ItemStack left = Vault.deposit(p.server, p.getUUID(), it);
            if (!left.isEmpty() && !p.getInventory().add(left)) p.drop(left, false);
        }
        return n;
    }

    public static void addToSync(ServerPlayer p, CompoundTag t) {
        CompoundTag r = REPORTS.get(p.getUUID());
        if (r != null) t.put("facReview", r);
        // which sites this player may review
        ListTag l = t.getList("sites", net.minecraft.nbt.Tag.TAG_COMPOUND);
        List<Sites.Site> all = Sites.get(p.server).sites;
        for (int k = 0; k < l.size(); k++) {
            CompoundTag st = l.getCompound(k);
            int i = st.getInt("i");
            if (i >= 0 && i < all.size() && may(p, all.get(i))) st.putBoolean("rev", true);
        }
    }
}
