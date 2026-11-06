package com.breachprotocol.registry;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.block.AscensionAltarBlock;
import com.breachprotocol.block.ContainmentGeneratorBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(BreachProtocol.MODID);

    /** Holds any SCP Unleashed anomaly in stasis while it has a redstone signal. */
    public static final DeferredBlock<ContainmentGeneratorBlock> CONTAINMENT_GENERATOR = BLOCKS.register("containment_generator",
            () -> new ContainmentGeneratorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(8.0F, 1200.0F).sound(SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops()
                    .lightLevel(state -> state.getValue(ContainmentGeneratorBlock.ACTIVE) ? 11 : 2)));

    /** Site-01's blast-proof walls. */
    public static final DeferredBlock<Block> CONTAINMENT_WALL = BLOCKS.registerSimpleBlock("containment_wall",
            BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(6.0F, 1200.0F)
                    .sound(SoundType.STONE).requiresCorrectToolForDrops());

    public static final DeferredBlock<AscensionAltarBlock> ASCENSION_ALTAR = BLOCKS.register("ascension_altar",
            () -> new AscensionAltarBlock(BlockBehaviour.Properties.of().mapColor(MapColor.GOLD)
                    .strength(-1.0F, 3600000.0F).noLootTable().noOcclusion().lightLevel(s -> 15).sound(SoundType.AMETHYST)));

    private ModBlocks() {}
}
