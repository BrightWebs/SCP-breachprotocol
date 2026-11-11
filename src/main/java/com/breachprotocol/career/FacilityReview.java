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
        int fixedPut = 0;
        if (pa[0].equals("fix")) {
            long t0 = System.nanoTime();
            fixedPut = SiteTower.patch(l, o);
            BreachProtocol.LOGGER.info("Facility review: {} repaired {} ({} blocks, {} ms)", p.getGameProfile().getName(), s.name, fixedPut, (System.nanoTime() - t0) / 1_000_000);
            p.displayClientMessage(Component.literal(s.name + ": " + (fixedPut == 0 ? "nothing needed doing." : fixedPut + " blocks put right.")).withStyle(ChatFormatting.GREEN), true);
            p.playNotifySound(SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.4F, 1.4F);
        } else if (!pa[0].equals("check")) return;
        // the review itself (after a repair too, so you see where it stands now)
        Map<String, int[]> areas = SiteTower.survey(l, o);
        CompoundTag r = new CompoundTag();
        r.putInt("i", i);
        r.putString("n", s.name);
        r.putInt("put", fixedPut);
        r.putLong("at", System.nanoTime()); // every report is new to the screen, even an identical one
        r.putBoolean("done", pa[0].equals("fix"));
        ListTag rows = new ListTag();
        int m = 0, f = 0, c = 0;
        for (Map.Entry<String, int[]> e : areas.entrySet()) {
            int[] v = e.getValue();
            if (v[0] + v[1] + v[2] == 0) continue;
            CompoundTag row = new CompoundTag();
            row.putString("a", e.getKey());
            row.putInt("m", v[0]);
            row.putInt("f", v[1]);
            row.putInt("c", v[2]);
            rows.add(row);
            m += v[0]; f += v[1]; c += v[2];
        }
        r.put("rows", rows);
        r.putInt("m", m);
        r.putInt("f", f);
        r.putInt("c", c);
        REPORTS.put(p.getUUID(), r);
        Career.sync(p);
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
