package com.breachprotocol.registry;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.block.AscensionAltarBlock;
import com.breachprotocol.block.ContainmentGeneratorBlock;
import com.breachprotocol.block.CorrosionBlock;
import com.breachprotocol.block.KeycardReaderBlock;
import com.breachprotocol.block.RadioactiveBlock;
import com.breachprotocol.power.*;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(BreachProtocol.MODID);

    public static final DeferredBlock<KeycardReaderBlock> KEYCARD_READER = BLOCKS.register("keycard_reader",
            () -> new KeycardReaderBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(5.0F, 1200.0F).sound(SoundType.METAL).noOcclusion().requiresCorrectToolForDrops()));

    public static final DeferredBlock<ContainmentGeneratorBlock> CONTAINMENT_GENERATOR = BLOCKS.register("containment_generator",
            () -> new ContainmentGeneratorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(8.0F, 1200.0F).sound(SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops()
                    .lightLevel(state -> state.getValue(ContainmentGeneratorBlock.ACTIVE) ? 11 : 2)));

    /** Blast-proof facility wall: SCP-096 cannot tear through it. */
    public static final DeferredBlock<Block> CONTAINMENT_WALL = BLOCKS.registerSimpleBlock("containment_wall",
            BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(6.0F, 1200.0F)
                    .sound(SoundType.STONE).requiresCorrectToolForDrops());

    public static final DeferredBlock<CorrosionBlock> CORROSION = BLOCKS.register("corrosion",
            () -> new CorrosionBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK)
                    .noCollission().noOcclusion().strength(0.2F).sound(SoundType.SLIME_BLOCK)
                    .replaceable().noLootTable().pushReaction(PushReaction.DESTROY)));

    public static final DeferredBlock<AscensionAltarBlock> ASCENSION_ALTAR = BLOCKS.register("ascension_altar",
            () -> new AscensionAltarBlock(BlockBehaviour.Properties.of().mapColor(MapColor.GOLD)
                    .strength(-1.0F, 3600000.0F).noLootTable().noOcclusion().lightLevel(s -> 15).sound(SoundType.AMETHYST)));

    // ---------------------------------------------------------------- power grid
    public static final DeferredBlock<CoalGeneratorBlock> COAL_GENERATOR = BLOCKS.register("coal_generator",
            () -> new CoalGeneratorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(4.0F, 6.0F)
                    .sound(SoundType.METAL).requiresCorrectToolForDrops().lightLevel(s -> s.getValue(CoalGeneratorBlock.LIT) ? 10 : 0)));
    public static final DeferredBlock<WireBlock> COPPER_WIRE = BLOCKS.register("copper_wire",
            () -> new WireBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(0.5F).sound(SoundType.WOOL).noOcclusion(), 1));
    public static final DeferredBlock<WireBlock> HEAVY_WIRE = BLOCKS.register("heavy_wire",
            () -> new WireBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(1.0F).sound(SoundType.METAL).noOcclusion(), 2));
    public static final DeferredBlock<BatteryBlock> BATTERY = BLOCKS.register("battery",
            () -> new BatteryBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 6.0F)
                    .sound(SoundType.METAL).requiresCorrectToolForDrops().lightLevel(s -> s.getValue(BatteryBlock.CHARGE) * 2)));
    public static final DeferredBlock<OutletBlock> POWER_OUTLET = BLOCKS.register("power_outlet",
            () -> new OutletBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0F, 6.0F)
                    .sound(SoundType.METAL).requiresCorrectToolForDrops().lightLevel(s -> s.getValue(OutletBlock.POWERED) ? 6 : 0)));
    public static final DeferredBlock<ReactorBlock> FISSION_REACTOR = BLOCKS.register("fission_reactor",
            () -> new ReactorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN).strength(10.0F, 1200.0F)
                    .sound(SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops().lightLevel(s -> s.getValue(ReactorBlock.HEAT) * 5)));
    public static final DeferredBlock<Block> COOLANT_CELL = BLOCKS.registerSimpleBlock("coolant_cell",
            BlockBehaviour.Properties.of().mapColor(MapColor.ICE).strength(3.0F).sound(SoundType.GLASS).friction(0.98F).requiresCorrectToolForDrops());
    public static final DeferredBlock<RadioactiveBlock> URANIUM_ORE = BLOCKS.register("uranium_ore",
            () -> new RadioactiveBlock(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(3.0F, 3.0F)
                    .requiresCorrectToolForDrops().lightLevel(s -> 3), 1));
    public static final DeferredBlock<RadioactiveBlock> DEEPSLATE_URANIUM_ORE = BLOCKS.register("deepslate_uranium_ore",
            () -> new RadioactiveBlock(BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE).strength(4.5F, 3.0F)
                    .sound(SoundType.DEEPSLATE).requiresCorrectToolForDrops().lightLevel(s -> 3), 1));
    public static final DeferredBlock<RadioactiveBlock> REACTOR_SLAG = BLOCKS.register("reactor_slag",
            () -> new RadioactiveBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_GREEN).strength(2.0F, 6.0F)
                    .sound(SoundType.BASALT).lightLevel(s -> 9), 4));

    private ModBlocks() {}
}
