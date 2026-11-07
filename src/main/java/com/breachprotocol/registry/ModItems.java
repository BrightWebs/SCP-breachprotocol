package com.breachprotocol.registry;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.item.PhoneItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(BreachProtocol.MODID);

    public static final DeferredItem<PhoneItem> PHONE = ITEMS.register("phone",
            () -> new PhoneItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));


    public static final DeferredItem<BlockItem> ADMIN_TERMINAL = ITEMS.register("admin_terminal",
            () -> new BlockItem(ModBlocks.ADMIN_TERMINAL.get(), new Item.Properties().rarity(Rarity.EPIC).stacksTo(1)));
    public static final DeferredItem<BlockItem> ASCENSION_ALTAR = ITEMS.register("ascension_altar",
            () -> new BlockItem(ModBlocks.ASCENSION_ALTAR.get(), new Item.Properties().rarity(Rarity.EPIC)));

    public static final DeferredItem<DeferredSpawnEggItem> O5_MEMBER_EGG = ITEMS.register("o5_member_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.O5_MEMBER, 0x111114, 0x8B0000, new Item.Properties()));

    // fruits of the Garden
    public static final DeferredItem<com.breachprotocol.item.FruitItem> FIG = fruit("fig", 4, 0.5F);
    public static final DeferredItem<com.breachprotocol.item.FruitItem> POMEGRANATE = fruit("pomegranate", 5, 0.6F);
    public static final DeferredItem<com.breachprotocol.item.FruitItem> OLIVE = fruit("olive", 2, 0.4F);
    public static final DeferredItem<com.breachprotocol.item.FruitItem> GRAPES = fruit("grapes", 3, 0.4F);
    public static final DeferredItem<com.breachprotocol.item.FruitItem> DATE = fruit("date", 4, 0.8F);
    public static final DeferredItem<com.breachprotocol.item.FruitItem> PEAR = fruit("pear", 4, 0.5F);
    public static final DeferredItem<com.breachprotocol.item.FruitItem> PEACH = fruit("peach", 4, 0.5F);
    public static final DeferredItem<com.breachprotocol.item.FruitItem> PLUM = fruit("plum", 4, 0.5F);
    public static final DeferredItem<com.breachprotocol.item.FruitItem> FRUIT_OF_LIFE = ITEMS.register("fruit_of_life",
            () -> new com.breachprotocol.item.FruitItem(com.breachprotocol.item.FruitItem.Kind.LIFE, 10, 1.2F, new Item.Properties().stacksTo(4).rarity(Rarity.EPIC)));
    public static final DeferredItem<com.breachprotocol.item.FruitItem> FRUIT_OF_KNOWLEDGE = ITEMS.register("fruit_of_knowledge",
            () -> new com.breachprotocol.item.FruitItem(com.breachprotocol.item.FruitItem.Kind.KNOWLEDGE, 6, 0.8F, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));

    private static DeferredItem<com.breachprotocol.item.FruitItem> fruit(String id, int food, float sat) {
        return ITEMS.register(id, () -> new com.breachprotocol.item.FruitItem(com.breachprotocol.item.FruitItem.Kind.PLAIN, food, sat, new Item.Properties()));
    }

    private ModItems() {}
}
