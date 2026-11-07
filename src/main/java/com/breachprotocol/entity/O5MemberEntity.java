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
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * One of the twelve Overseers in Site-01's council chamber. Each has a home chair at the council
 * table and spends most of their time in it (really seated, on SCP Unleashed's office chair), getting
 * up now and then to stretch their legs. An Overseer who has given up their seat to a player is
 * "unseated": they wander the site and sit wherever they like. Talk to them.
 */
public class O5MemberEntity extends PathfinderMob {
    private static final EntityDataAccessor<Integer> SEAT = SynchedEntityData.defineId(O5MemberEntity.class, EntityDataSerializers.INT);

    private static final String[] LINES_STAFF = {
            "You should not be here. How did you get in?",
            "Your clearance is noted. And insufficient.",
            "Leave, and we'll consider this a misunderstanding."};

    private BlockPos home;
    private boolean unseated;
    private long holdUntil;

    public O5MemberEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.setInvulnerable(true);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 100)
                .add(Attributes.MOVEMENT_SPEED, 0.22);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SEAT, 7);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        // seated most of the time: back to their chair quickly, minutes at a time
        this.goalSelector.addGoal(1, new SitGoal(this, () -> unseated ? null : home, 0.04F, 20 * 120, 20 * 360, 10,
                pos -> com.breachprotocol.world.Site01.mayUseChair(this, pos)));
        this.goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 12F, 1.0F));
        this.goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 0.6, 0.004F));
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));
    }

    /** Their council chair, and whether they still hold it. */
    public void setHome(BlockPos chair, boolean unseated) {
        this.home = chair;
        this.unseated = unseated;
        restrict();
    }

    public BlockPos home() { return home; }

    public boolean isUnseated() { return unseated; }

    public void unseat() {
        unseated = true;
        Seats.stand(this);
        restrict();
    }

    /** Keep them seated (during a council session) for a while. */
    public void holdSeated(int ticks) { holdUntil = level().getGameTime() + ticks; }

    public boolean holdSeat() { return level().getGameTime() < holdUntil; }

    private void restrict() {
        if (home != null) restrictTo(home, unseated ? 40 : 9);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!level().isClientSide && tickCount % 200 == 0) restrict();
        if (!level().isClientSide && tickCount % 20 == 1 && com.breachprotocol.world.Site01.stale(this)) { Seats.stand(this); discard(); }
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
            if (unseated && !Career.isAscended(player)) {
                line = switch (random.nextInt(3)) {
                    case 0 -> "I gave my chair to someone younger. The Foundation endures. So do I.";
                    case 1 -> "Off the Council, not out of the Foundation. I still read every report.";
                    default -> "The vote was fair. Mostly. Don't let it go to your head.";
                };
            } else if (Career.isAscended(player)) {
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
        if (home != null) { tag.putInt("HomeX", home.getX()); tag.putInt("HomeY", home.getY()); tag.putInt("HomeZ", home.getZ()); }
        tag.putBoolean("Unseated", unseated);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Seat")) this.entityData.set(SEAT, tag.getInt("Seat"));
        if (tag.contains("HomeX")) home = new BlockPos(tag.getInt("HomeX"), tag.getInt("HomeY"), tag.getInt("HomeZ"));
        unseated = tag.getBoolean("Unseated");
        restrict();
    }
}
