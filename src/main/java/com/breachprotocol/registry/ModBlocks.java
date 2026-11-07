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

    public static final DeferredBlock<com.breachprotocol.block.AdminTerminalBlock> ADMIN_TERMINAL = BLOCKS.register("admin_terminal",
            () -> new com.breachprotocol.block.AdminTerminalBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(2.5F, 1200F).noOcclusion().lightLevel(s -> 7).sound(SoundType.METAL)));

    // fruit hanging from the Garden's trees
    public static final DeferredBlock<com.breachprotocol.block.HangingFruitBlock> FRUIT_APPLE = fruit("fruit_apple", () -> net.minecraft.world.item.Items.APPLE, 10, 0);
    public static final DeferredBlock<com.breachprotocol.block.HangingFruitBlock> FRUIT_PEAR = fruit("fruit_pear", () -> ModItems.PEAR.get(), 10, 0);
    public static final DeferredBlock<com.breachprotocol.block.HangingFruitBlock> FRUIT_PEACH = fruit("fruit_peach", () -> ModItems.PEACH.get(), 10, 0);
    public static final DeferredBlock<com.breachprotocol.block.HangingFruitBlock> FRUIT_PLUM = fruit("fruit_plum", () -> ModItems.PLUM.get(), 10, 0);
    public static final DeferredBlock<com.breachprotocol.block.HangingFruitBlock> FRUIT_FIG = fruit("fruit_fig", () -> ModItems.FIG.get(), 10, 0);
    public static final DeferredBlock<com.breachprotocol.block.HangingFruitBlock> FRUIT_POMEGRANATE = fruit("fruit_pomegranate", () -> ModItems.POMEGRANATE.get(), 10, 0);
    public static final DeferredBlock<com.breachprotocol.block.HangingFruitBlock> FRUIT_OLIVE = fruit("fruit_olive", () -> ModItems.OLIVE.get(), 10, 0);
    public static final DeferredBlock<com.breachprotocol.block.HangingFruitBlock> FRUIT_DATE = fruit("fruit_date", () -> ModItems.DATE.get(), 10, 0);
    public static final DeferredBlock<com.breachprotocol.block.HangingFruitBlock> FRUIT_GRAPES = fruit("fruit_grapes", () -> ModItems.GRAPES.get(), 10, 0);
    public static final DeferredBlock<com.breachprotocol.block.HangingFruitBlock> FRUIT_LIFE = fruit("fruit_life", () -> ModItems.FRUIT_OF_LIFE.get(), 60, 12);
    public static final DeferredBlock<com.breachprotocol.block.HangingFruitBlock> FRUIT_KNOWLEDGE = fruit("fruit_knowledge", () -> ModItems.FRUIT_OF_KNOWLEDGE.get(), 60, 6);

    private static DeferredBlock<com.breachprotocol.block.HangingFruitBlock> fruit(String id, java.util.function.Supplier<net.minecraft.world.item.Item> item, int regrow, int light) {
        return BLOCKS.register(id, () -> new com.breachprotocol.block.HangingFruitBlock(item, regrow, BlockBehaviour.Properties.of().mapColor(MapColor.PLANT)
                .strength(0.2F).noCollission().noOcclusion().randomTicks().sound(SoundType.SWEET_BERRY_BUSH).noLootTable()
                .lightLevel(s -> light).pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)));
    }

    private ModBlocks() {}
}
