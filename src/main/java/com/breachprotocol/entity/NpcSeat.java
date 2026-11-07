package com.breachprotocol.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The invisible seat an NPC rides when it sits on an SCP Unleashed chair, couch or bench. Unlike
 * SCP Unleashed's own seat (which is never saved), this one is saved with its rider, so a seated
 * Overseer is still seated after the world reloads. It goes away when its rider stands or the
 * chair is broken.
 */
public class NpcSeat extends Entity {
    public NpcSeat(EntityType<? extends NpcSeat> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        setNoGravity(true);
        setInvisible(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        if (tickCount > 5 && (!isVehicle() || !Seats.isSeat(level().getBlockState(Seats.chairOf(this))))) {
            ejectPassengers();
            discard();
        }
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().isEmpty();
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float partialTick) {
        return Vec3.ZERO;
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction callback) {
        super.positionRider(passenger, callback);
        passenger.setYBodyRot(getYRot());
        float off = Mth.wrapDegrees(passenger.getYRot() - getYRot());
        float c = Mth.clamp(off, -70F, 70F);
        passenger.setYRot(passenger.getYRot() + c - off);
        passenger.yRotO += c - off;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) { return false; }

    @Override
    public boolean isPickable() { return false; }

    @Override
    public boolean shouldBeSaved() { return isVehicle() && super.shouldBeSaved(); }
}
