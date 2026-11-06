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


    public static final DeferredItem<BlockItem> ASCENSION_ALTAR = ITEMS.register("ascension_altar",
            () -> new BlockItem(ModBlocks.ASCENSION_ALTAR.get(), new Item.Properties().rarity(Rarity.EPIC)));

    public static final DeferredItem<DeferredSpawnEggItem> O5_MEMBER_EGG = ITEMS.register("o5_member_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.O5_MEMBER, 0x111114, 0x8B0000, new Item.Properties()));

    private ModItems() {}
}
