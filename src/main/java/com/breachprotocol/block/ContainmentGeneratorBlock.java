package com.breachprotocol.block;

import com.breachprotocol.compat.Unleashed;
import com.breachprotocol.event.BreachAlerts;
import com.breachprotocol.registry.ModEffects;
import com.breachprotocol.registry.ModSounds;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * Containment Field Generator.
 * <p>
 * While it receives redstone power, every anomaly within {@link #RADIUS} blocks is held in
 * stasis (the Contained effect). Cut the power, and everything it was holding breaks free:
 * a site-wide breach alarm sounds, a title card names the escaped anomalies, and they get a
 * burst of speed. Build your facility's power carefully.
 */
public class ContainmentGeneratorBlock extends Block {
    public static final MapCodec<ContainmentGeneratorBlock> CODEC = simpleCodec(ContainmentGeneratorBlock::new);
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    /** Remaining alarm repeats after a breach. */
    public static final IntegerProperty ALARM = IntegerProperty.create("alarm", 0, 6);
    public static final int RADIUS = 8;
    private static final int FIELD_INTERVAL = 10;
    private static final int ALARM_INTERVAL = 50;

    public ContainmentGeneratorBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(ACTIVE, false).setValue(ALARM, 0));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE, ALARM);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(ACTIVE, ctx.getLevel().hasNeighborSignal(ctx.getClickedPos()));
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        if (!level.isClientSide && !old.is(this)) {
            level.scheduleTick(pos, this, FIELD_INTERVAL);
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean moved) {
        if (level.isClientSide) return;
        boolean powered = level.hasNeighborSignal(pos);
        boolean active = state.getValue(ACTIVE);
        if (powered == active) return;

        if (powered) {
            level.setBlock(pos, state.setValue(ACTIVE, true).setValue(ALARM, 0), Block.UPDATE_CLIENTS);
            level.playSound(null, pos, ModSounds.FIELD_HUM.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
            level.scheduleTick(pos, this, 1);
        } else {
            List<String> escaped = releaseAll((ServerLevel) level, pos);
            int alarm = escaped.isEmpty() ? 0 : 6;
            level.setBlock(pos, state.setValue(ACTIVE, false).setValue(ALARM, alarm), Block.UPDATE_CLIENTS);
            if (!escaped.isEmpty()) {
                BreachAlerts.announce((ServerLevel) level, pos, escaped);
                level.scheduleTick(pos, this, ALARM_INTERVAL);
            }
        }
    }

    private static AABB field(BlockPos pos) {
        return new AABB(pos).inflate(RADIUS);
    }

    /** Frees everything held by this generator and returns their designations. */
    private static List<String> releaseAll(ServerLevel level, BlockPos pos) {
        List<String> names = new ArrayList<>();
        for (Mob mob : level.getEntitiesOfClass(Mob.class, field(pos), Unleashed::isAnomaly)) {
            if (ModEffects.has(mob, ModEffects.CONTAINED)) {
                mob.removeEffect(ModEffects.h(ModEffects.CONTAINED));
                mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 30, 0));
                String d = Unleashed.designation(mob);
                if (!names.contains(d)) names.add(d);
            }
        }
        return names;
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(ACTIVE)) {
            List<Mob> held = level.getEntitiesOfClass(Mob.class, field(pos), Unleashed::canBeContained);
            for (Mob mob : held) {
                if (!ModEffects.has(mob, ModEffects.CONTAINED)) com.breachprotocol.career.Career.onContained(level, pos, mob);
                Unleashed.contain(mob, FIELD_INTERVAL * 4);
                if (random.nextInt(3) == 0) {
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK, mob.getX(), mob.getY() + mob.getBbHeight() * random.nextFloat(), mob.getZ(),
                            3, mob.getBbWidth() * 0.6, 0.2, mob.getBbWidth() * 0.6, 0.05);
                }
            }
            // Visualise the field boundary with a slowly orbiting ring of sparks.
            double a = (level.getGameTime() % 80) / 80.0 * Math.PI * 2;
            for (int i = 0; i < 4; i++) {
                double ang = a + i * Math.PI / 2;
                level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5 + Math.cos(ang) * RADIUS, pos.getY() + 1.0,
                        pos.getZ() + 0.5 + Math.sin(ang) * RADIUS, 1, 0, 0.1, 0, 0);
            }
            if (!held.isEmpty() && level.getGameTime() % 40 == 0) {
                level.playSound(null, pos, ModSounds.FIELD_HUM.get(), SoundSource.BLOCKS, 0.5F, 1.0F);
            }
            level.scheduleTick(pos, this, FIELD_INTERVAL);
        } else if (state.getValue(ALARM) > 0) {
            level.playSound(null, pos, ModSounds.BREACH_ALARM.get(), SoundSource.BLOCKS, 6.0F, 1.0F);
            level.setBlock(pos, state.setValue(ALARM, state.getValue(ALARM) - 1), Block.UPDATE_CLIENTS);
            level.scheduleTick(pos, this, ALARM_INTERVAL);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        // Breaking a running generator is also a breach.
        if (!level.isClientSide && !newState.is(this) && state.getValue(ACTIVE)) {
            List<String> escaped = releaseAll((ServerLevel) level, pos);
            if (!escaped.isEmpty()) BreachAlerts.announce((ServerLevel) level, pos, escaped);
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
