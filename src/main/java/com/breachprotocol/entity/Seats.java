package com.breachprotocol.entity;

import com.breachprotocol.compat.UnleashedApi;
import com.breachprotocol.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;

import java.lang.reflect.Method;

/**
 * Lets any NPC sit on SCP Unleashed seating: office chairs and their other "props" seats
 * (props.SeatBlock), and couches, cafeteria chairs and benches (furnishing.SeatHost). Seat height
 * comes from the block itself, the facing from its FACING property.
 */
public final class Seats {
    private Seats() {}

    private static Class<?> propSeat, hostSeat;
    private static Method propHeight, propSeats, hostHeight;
    private static boolean looked;

    private static void look() {
        if (looked) return;
        looked = true;
        try {
            propSeat = UnleashedApi.cls("props.SeatBlock");
            hostSeat = UnleashedApi.cls("furnishing.SeatHost");
            if (propSeat != null) { propHeight = propSeat.getMethod("seatHeight", BlockState.class); propSeats = propSeat.getMethod("seats", BlockState.class); }
            if (hostSeat != null) hostHeight = hostSeat.getMethod("seatHeight", BlockState.class);
        } catch (Throwable ignored) {}
    }

    /** Is this block something you can sit on? */
    public static boolean isSeat(BlockState s) {
        look();
        Object b = s.getBlock();
        try {
            if (propSeat != null && propSeat.isInstance(b)) return propSeats == null || (Boolean) propSeats.invoke(b, s);
            if (hostSeat != null && hostSeat.isInstance(b)) {
                String path = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
                return !path.contains("examination");
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static double height(BlockState s) {
        look();
        Object b = s.getBlock();
        try {
            if (propSeat != null && propSeat.isInstance(b)) return ((Number) propHeight.invoke(b, s)).doubleValue();
            if (hostSeat != null && hostSeat.isInstance(b)) return ((Number) hostHeight.invoke(b, s)).doubleValue();
        } catch (Throwable ignored) {}
        return 9.0 / 16.0;
    }

    /** The chair block a seat entity stands in. */
    public static BlockPos chairOf(Entity seat) {
        return BlockPos.containing(seat.getX(), seat.getY() + 0.05, seat.getZ());
    }

    /** Someone (player or NPC) is already sitting here. */
    public static boolean occupied(Level l, BlockPos pos) {
        for (Entity e : l.getEntities((Entity) null, new AABB(pos).inflate(0.1), e -> e.isVehicle() && e.getType() != null && !(e instanceof Mob))) {
            if (chairOf(e).equals(pos) || e.blockPosition().equals(pos)) return true;
        }
        return false;
    }

    /** Sits {@code mob} on the chair at {@code pos}. */
    public static boolean sit(Mob mob, BlockPos pos) {
        Level l = mob.level();
        BlockState s = l.getBlockState(pos);
        if (!isSeat(s) || occupied(l, pos) || mob.isPassenger()) return false;
        Direction f = s.hasProperty(BlockStateProperties.HORIZONTAL_FACING) ? s.getValue(BlockStateProperties.HORIZONTAL_FACING) : Direction.NORTH;
        NpcSeat seat = ModEntities.NPC_SEAT.get().create(l);
        if (seat == null) return false;
        seat.moveTo(pos.getX() + 0.5 - f.getStepX() * 0.1, pos.getY() + height(s), pos.getZ() + 0.5 - f.getStepZ() * 0.1, f.toYRot(), 0);
        l.addFreshEntity(seat);
        if (mob.startRiding(seat, true)) {
            mob.setYRot(f.toYRot());
            mob.setYHeadRot(f.toYRot());
            mob.setYBodyRot(f.toYRot());
            mob.getNavigation().stop();
            return true;
        }
        seat.discard();
        return false;
    }

    public static boolean seated(Entity e) {
        return e.getVehicle() instanceof NpcSeat;
    }

    /** Gets up from the chair, if seated. */
    public static void stand(Mob mob) {
        Entity v = mob.getVehicle();
        if (!(v instanceof NpcSeat)) return;
        mob.stopRiding();
        v.discard();
    }
}
