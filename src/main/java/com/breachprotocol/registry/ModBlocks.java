package com.breachprotocol.registry;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.block.AscensionAltarBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(BreachProtocol.MODID);


    public static final DeferredBlock<AscensionAltarBlock> ASCENSION_ALTAR = BLOCKS.register("ascension_altar",
            () -> new AscensionAltarBlock(BlockBehaviour.Properties.of().mapColor(MapColor.GOLD)
                    .strength(-1.0F, 3600000.0F).noLootTable().noOcclusion().lightLevel(s -> 15).sound(SoundType.AMETHYST)));

    private ModBlocks() {}
}
