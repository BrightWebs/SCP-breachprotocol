package com.breachprotocol.block;

import com.breachprotocol.registry.ModEffects;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Uranium ore and reactor slag: irradiate anything that lingers nearby. */
public class RadioactiveBlock extends Block {
    public static final MapCodec<RadioactiveBlock> CODEC = simpleCodec(p -> new RadioactiveBlock(p, 2));
    private final int radius;

    public RadioactiveBlock(Properties props, int radius) {
        super(props);
        this.radius = radius;
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(pos).inflate(radius))) {
            e.addEffect(new MobEffectInstance(ModEffects.h(ModEffects.RADIATION), 200, 0));
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource r) {
        if (r.nextInt(6) == 0) {
            level.addParticle(ParticleTypes.GLOW, pos.getX() + r.nextDouble(), pos.getY() + 1.02, pos.getZ() + r.nextDouble(), 0, 0.01, 0);
        }
    }
}
