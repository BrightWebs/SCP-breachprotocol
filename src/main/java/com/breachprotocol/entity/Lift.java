package com.breachprotocol.entity;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.career.Career;
import com.breachprotocol.career.Clearance;
import com.breachprotocol.career.Secretary;
import com.breachprotocol.career.Sites;
import com.breachprotocol.world.SiteTower;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.*;

/**
 * The tower lift. Step into the car, press the panel, and pick a floor: it lists every floor from
 * B5 to the roof and only goes where your clearance allows (the Council and the Administrator go
 * anywhere). Staff use it too, to get to work, to lunch and to bed.
 */
@EventBusSubscriber(modid = BreachProtocol.MODID)
public final class Lift {
    private Lift() {}

    /** The tower whose footprint (with its basements and residence) contains this spot, or null. */
    public static Sites.Site towerAt(ServerLevel l, BlockPos p) {
        if (l.dimension() != net.minecraft.world.level.Level.OVERWORLD) return null;
        for (Sites.Site s : Sites.get(l.getServer()).sites) {
            if (s.name.equals("Site-01") || !s.dim.equals("minecraft:overworld")) continue;
            BlockPos o = Secretary.origin(s);
            int dx = p.getX() - o.getX(), dz = p.getZ() - o.getZ(), dy = p.getY() - o.getY();
            if (dx >= -10 && dx <= SiteTower.RX1 + 4 && dz >= -12 && dz <= SiteTower.T + 6 && dy >= SiteTower.y(SiteTower.LOWEST_FLOOR) - 1 && dy <= SiteTower.y(SiteTower.ROOF) + 6) return s;
        }
        return null;
    }

    public static int storey(BlockPos origin, int y) {
        return Math.max(SiteTower.LOWEST_FLOOR, Math.min(SiteTower.ROOF, Math.floorDiv(y - origin.getY(), SiteTower.STOREY)));
    }

    /** Is there a lift in this tower (an older tower may not have been refitted yet)? */
    public static boolean hasLift(ServerLevel l, BlockPos origin) {
        BlockPos p = origin.offset(SiteTower.liftPanel(0));
        return l.isLoaded(p) && BuiltInRegistries.BLOCK.getKey(l.getBlockState(p).getBlock()).getPath().equals("door_button");
    }

    static boolean cleared(ServerPlayer p, int s) {
        return Talk.topBrass(p) || Career.rank(p).atLeast(com.breachprotocol.career.Rank.OVERSEER) || Clearance.effective(p) >= SiteTower.floorClearance(s);
    }

    // ================================================================== the panel

    @SubscribeEvent
    public static void press(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel l) || !(event.getEntity() instanceof ServerPlayer p)) return;
        BlockPos pos = event.getPos();
        if (!BuiltInRegistries.BLOCK.getKey(l.getBlockState(pos).getBlock()).getPath().equals("door_button")) return;
        Sites.Site site = towerAt(l, pos);
        if (site == null) return;
        BlockPos o = Secretary.origin(site);
        int s = storey(o, pos.getY() - 1);
        if (!o.offset(SiteTower.liftPanel(s)).equals(pos)) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        menu(p, site, s);
    }

    static void menu(ServerPlayer p, Sites.Site site, int here) {
        Dialogue.begin(p, "lift:" + site.name, "Lift", site.name + " · you are on " + SiteTower.floorName(here).replaceFirst("  ", " "), -1);
        Dialogue.line(p, "", "Select a floor.", 0);
        Dialogue.clearOptions(p);
        for (int s = SiteTower.ROOF; s >= SiteTower.LOWEST_FLOOR; s--) {
            String name = SiteTower.floorName(s);
            int lvl = SiteTower.floorClearance(s);
            boolean ok = cleared(p, s);
            if (s == here) Dialogue.option(p, name + "  (you are here)", "close", "", 0x808080);
            else if (ok) Dialogue.option(p, name, "/lift " + s, lvl > 0 ? "Level " + lvl : "Open to all", 0x9FD8FF);
            else Dialogue.option(p, "🔒 " + name, "/lift " + s, "Requires Level " + lvl + " clearance", 0x806060);
        }
        Dialogue.send(p, true);
    }

    // ================================================================== riding

    private record Ride(UUID who, ServerLevel level, BlockPos origin, int from, int to, long at) {}

    private static final List<Ride> RIDES = new ArrayList<>();

    @SubscribeEvent
    public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("lift").then(Commands.argument("floor", IntegerArgumentType.integer(-10, 20)).executes(c -> {
            ServerPlayer p = c.getSource().getPlayerOrException();
            int to = IntegerArgumentType.getInteger(c, "floor");
            Sites.Site site = towerAt(p.serverLevel(), p.blockPosition());
            if (site == null) return 0;
            BlockPos o = Secretary.origin(site);
            int from = storey(o, p.getBlockY());
            BlockPos car = o.offset(SiteTower.liftCar(from));
            Dialogue.close(p);
            if (p.blockPosition().distSqr(car) > 9) { p.displayClientMessage(Component.literal("Step into the lift car first.").withStyle(ChatFormatting.GRAY), true); return 0; }
            if (!cleared(p, to)) {
                p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 0.8F, 0.6F);
                p.displayClientMessage(Component.literal("ACCESS DENIED · " + SiteTower.floorName(to).replaceFirst("  ", " ") + " requires Level " + SiteTower.floorClearance(to)).withStyle(ChatFormatting.RED), true);
                return 0;
            }
            p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 0.7F, 1.2F);
            p.displayClientMessage(Component.literal("Going to " + SiteTower.floorName(to).replaceFirst("  ", " ") + "...").withStyle(ChatFormatting.AQUA), true);
            RIDES.add(new Ride(p.getUUID(), p.serverLevel(), o, from, to, p.serverLevel().getGameTime() + 20L + Math.abs(to - from) * 6L));
            return 1;
        })));
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        if (RIDES.isEmpty()) return;
        MinecraftServer s = event.getServer();
        for (Iterator<Ride> it = RIDES.iterator(); it.hasNext(); ) {
            Ride r = it.next();
            if (r.level().getGameTime() < r.at()) continue;
            it.remove();
            ServerPlayer p = s.getPlayerList().getPlayer(r.who());
            if (p == null) continue;
            BlockPos from = r.origin().offset(SiteTower.liftCar(r.from())), to = r.origin().offset(SiteTower.liftCar(r.to()));
            if (p.blockPosition().distSqr(from) > 9) continue; // stepped out
            // everyone in the car rides together
            for (Entity e : r.level().getEntities((Entity) null, new AABB(from).inflate(1.5, 2, 1.5), x -> x instanceof LivingEntity)) {
                Vec3 off = e.position().subtract(Vec3.atBottomCenterOf(from));
                e.teleportTo(to.getX() + 0.5 + off.x, to.getY(), to.getZ() + 0.5 + off.z);
            }
            r.level().playSound(null, to, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 0.8F, 1.0F);
            p.displayClientMessage(Component.literal("Floor " + SiteTower.floorName(r.to()).replaceFirst("  ", ": ")).withStyle(ChatFormatting.AQUA), true);
        }
    }

    /** Staff ride without waiting for the panel. False if they aren't cleared for that floor. */
    public static boolean carry(net.minecraft.world.entity.Mob m, BlockPos origin, int to) {
        if (Doors.clearance(m) < SiteTower.floorClearance(to)) return false;
        BlockPos dest = origin.offset(SiteTower.liftCar(to));
        m.getNavigation().stop();
        m.teleportTo(dest.getX() + 0.5, dest.getY(), dest.getZ() + 0.5);
        m.level().playSound(null, dest, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 0.5F, 1.0F);
        return true;
    }
}
