package com.breachprotocol.network;

import com.breachprotocol.career.Career;
import com.breachprotocol.career.Clearance;
import com.breachprotocol.career.Rank;
import com.breachprotocol.compat.OrderGoal;
import com.breachprotocol.compat.UnleashedApi;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** The phone's Command and Network apps, driving SCP Unleashed's MTF, satellite and D-Class. */
public final class Command {
    private Command() {}

    /** Which MTF threat tiers you may call, by clearance. */
    public static List<String> mtfTiers(ServerPlayer p) {
        int c = Clearance.effective(p);
        Rank r = Career.rank(p);
        if (r == Rank.ADMINISTRATOR || Career.isAscended(p)) return List.of(UnleashedApi.MTF_TIERS);
        if (c >= 6) return List.of("Faint", "Wavering", "Volatile", "Severe");
        if (c >= 5) return List.of("Faint", "Wavering", "Volatile");
        if (c >= 4) return List.of("Faint");
        return List.of();
    }

    public static void callMtf(ServerPlayer p, String tier) {
        if (!mtfTiers(p).contains(tier)) return;
        String out = UnleashedApi.callMtf(p, tier);
        String msg = out == null ? "MTF line is down (SCP Unleashed did not answer)." : switch (out) {
            case "Called" -> "MTF inbound. Threat tier: " + tier + ".";
            case "Busy" -> "A squad is already deployed or on cooldown.";
            case "NoSky" -> "The helicopter needs open sky above you.";
            default -> "The request failed.";
        };
        p.displayClientMessage(Component.literal(msg).withStyle("Called".equals(out) ? ChatFormatting.AQUA : ChatFormatting.RED), true);
    }

    public static void orbitalStrike(ServerPlayer p) {
        if (Clearance.effective(p) < 5) return;
        Vec3 eye = p.getEyePosition();
        HitResult hit = p.level().clip(new ClipContext(eye, eye.add(p.getViewVector(1.0F).scale(256)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
        if (!(hit instanceof BlockHitResult bh) || hit.getType() == HitResult.Type.MISS) {
            p.displayClientMessage(Component.literal("No target under your crosshair (256m).").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        boolean ok = UnleashedApi.orbitalStrike(p, bh.getBlockPos());
        p.displayClientMessage(Component.literal(ok ? "Orbital strike authorised. Get clear." : "Satellite not ready: finish your array or wait for it to recharge.")
                .withStyle(ok ? ChatFormatting.GOLD : ChatFormatting.RED), true);
    }

    public static void orderDClass(ServerPlayer p, int entityId, String order) {
        if (Clearance.effective(p) < 4) return;
        Entity e = p.level().getEntity(entityId);
        if (e == null || !UnleashedApi.isDClass(e) || e.distanceToSqr(p) > 64 * 64) return;
        OrderGoal.set(e, order, p.getUUID());
        if (e instanceof Mob mob) mob.getNavigation().stop();
        p.displayClientMessage(Component.literal(UnleashedApi.dclassDesignation(e) + ": " + switch (order) {
            case "follow" -> "following you.";
            case "hold" -> "holding position.";
            default -> "released to normal duties.";
        }).withStyle(ChatFormatting.GRAY), true);
    }

    /** Extra phone data for the clearance-gated apps. */
    public static void addToSync(ServerPlayer p, CompoundTag t) {
        int clr = Clearance.effective(p);
        t.putInt("clr", clr);
        t.putInt("fused", Clearance.fused(Clearance.phoneOf(p)));
        t.putInt("carried", Clearance.carried(p));
        t.putInt("rankClr", Clearance.fromRank(p));
        if (clr >= 4) {
            ListTag tiers = new ListTag();
            for (String s : mtfTiers(p)) tiers.add(StringTag.valueOf(s));
            t.put("mtfTiers", tiers);
            t.putLong("mtfCd", UnleashedApi.mtfCooldown(p) / 20);
            ListTag ds = new ListTag();
            for (Entity e : p.level().getEntities(p, p.getBoundingBox().inflate(48), UnleashedApi::isDClass)) {
                if (ds.size() >= 12) break;
                CompoundTag dt = new CompoundTag();
                dt.putInt("id", e.getId());
                dt.putString("name", UnleashedApi.dclassDesignation(e));
                dt.putString("order", OrderGoal.order(e));
                dt.putInt("m", (int) Math.sqrt(e.distanceToSqr(p)));
                ds.add(dt);
            }
            t.put("dclass", ds);
        }
        if (clr >= 5) {
            int[] orb = UnleashedApi.orbitalStatus(p);
            if (orb != null) {
                t.putInt("orbParts", orb[0]);
                t.putBoolean("orbReady", orb[1] == 1);
                t.putInt("orbShots", orb[2]);
            }
            t.putInt("hubs", UnleashedApi.networkHubCount(p.server));
        }
    }
}
