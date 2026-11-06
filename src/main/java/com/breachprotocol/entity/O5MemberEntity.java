package com.breachprotocol.entity;

import com.breachprotocol.career.Career;
import com.breachprotocol.career.O5;
import com.breachprotocol.career.Rank;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** One of the twelve Overseers, seated in Site-01's council chamber. Talk to them. */
public class O5MemberEntity extends PathfinderMob {
    private static final EntityDataAccessor<Integer> SEAT = SynchedEntityData.defineId(O5MemberEntity.class, EntityDataSerializers.INT);

    private static final String[] LINES_STAFF = {
            "You should not be here. How did you get in?",
            "Your clearance is noted. And insufficient.",
            "Leave, and we'll consider this a misunderstanding."};

    public O5MemberEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.setInvulnerable(true);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 100)
                .add(Attributes.MOVEMENT_SPEED, 0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SEAT, 7);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 16F, 1.0F));
        this.goalSelector.addGoal(2, new RandomLookAroundGoal(this));
    }

    public O5 getSeat() {
        return O5.seat(this.entityData.get(SEAT));
    }

    public void setSeat(O5 seat) {
        this.entityData.set(SEAT, seat.seat);
        setCustomName(Component.literal(seat.sender()).withStyle(ChatFormatting.DARK_RED));
        setCustomNameVisible(true);
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!level().isClientSide && hand == InteractionHand.MAIN_HAND) {
            O5 me = getSeat();
            String line;
            if (Career.isAscended(player)) {
                line = "...Master. Forgive us for ever trying to contain you.";
            } else if (Career.rank(player) == Rank.ADMINISTRATOR) {
                line = switch (me) {
                    case O5_1 -> "The Gate was always the point, you know. Everything else was rehearsal.";
                    case O5_7 -> "Administrator. The Council awaits your orders.";
                    case O5_12 -> "The numbers add up, Administrator. For the first time in decades.";
                    case O5_11 -> "Every termination order now carries your signature. Sign carefully.";
                    case O5_3 -> "> ADMINISTRATOR RECOGNISED. ALL RECORDS UNSEALED.";
                    default -> "Administrator. " + me.role;
                };
            } else if (Career.rank(player) == Rank.OVERSEER) {
                line = "Thirteen. You're early for the meeting. " + me.role;
            } else {
                line = LINES_STAFF[random.nextInt(LINES_STAFF.length)];
            }
            player.sendSystemMessage(Component.literal(me.sender() + ": ").withStyle(ChatFormatting.DARK_RED)
                    .append(Component.literal(line).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)));
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Seat", this.entityData.get(SEAT));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Seat")) this.entityData.set(SEAT, tag.getInt("Seat"));
    }
}
