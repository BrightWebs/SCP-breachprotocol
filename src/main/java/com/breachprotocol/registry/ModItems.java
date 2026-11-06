package com.breachprotocol.registry;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.item.*;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(BreachProtocol.MODID);

    public static final DeferredItem<KeycardItem> KEYCARD_1 = keycard(1, Rarity.COMMON);
    public static final DeferredItem<KeycardItem> KEYCARD_2 = keycard(2, Rarity.COMMON);
    public static final DeferredItem<KeycardItem> KEYCARD_3 = keycard(3, Rarity.UNCOMMON);
    public static final DeferredItem<KeycardItem> KEYCARD_4 = keycard(4, Rarity.RARE);
    public static final DeferredItem<KeycardItem> KEYCARD_5 = keycard(5, Rarity.EPIC);

    public static final DeferredItem<Scp500Item> SCP_500 = ITEMS.register("scp_500",
            () -> new Scp500Item(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC)));
    public static final DeferredItem<Scp207Item> SCP_207 = ITEMS.register("scp_207",
            () -> new Scp207Item(new Item.Properties().stacksTo(16).rarity(Rarity.RARE)));
    public static final DeferredItem<EyedropsItem> EYEDROPS = ITEMS.register("eyedrops",
            () -> new EyedropsItem(new Item.Properties().stacksTo(16)));
    public static final DeferredItem<SiteTerminalItem> SITE_TERMINAL = ITEMS.register("site_terminal",
            () -> new SiteTerminalItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<PhoneItem> PHONE = ITEMS.register("phone",
            () -> new PhoneItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<BlockItem> ASCENSION_ALTAR = ITEMS.register("ascension_altar",
            () -> new BlockItem(ModBlocks.ASCENSION_ALTAR.get(), new Item.Properties().rarity(Rarity.EPIC)));

    public static final DeferredItem<BlockItem> KEYCARD_READER = ITEMS.registerSimpleBlockItem(ModBlocks.KEYCARD_READER);
    public static final DeferredItem<BlockItem> CONTAINMENT_GENERATOR = ITEMS.registerSimpleBlockItem(ModBlocks.CONTAINMENT_GENERATOR);
    public static final DeferredItem<BlockItem> CONTAINMENT_WALL = ITEMS.registerSimpleBlockItem(ModBlocks.CONTAINMENT_WALL);

    // ---------------------------------------------------------------- power grid
    public static final DeferredItem<BlockItem> COAL_GENERATOR = ITEMS.registerSimpleBlockItem(ModBlocks.COAL_GENERATOR);
    public static final DeferredItem<BlockItem> COPPER_WIRE = ITEMS.registerSimpleBlockItem(ModBlocks.COPPER_WIRE);
    public static final DeferredItem<BlockItem> HEAVY_WIRE = ITEMS.registerSimpleBlockItem(ModBlocks.HEAVY_WIRE);
    public static final DeferredItem<BlockItem> BATTERY = ITEMS.registerSimpleBlockItem(ModBlocks.BATTERY);
    public static final DeferredItem<BlockItem> POWER_OUTLET = ITEMS.registerSimpleBlockItem(ModBlocks.POWER_OUTLET);
    public static final DeferredItem<BlockItem> FISSION_REACTOR = ITEMS.registerSimpleBlockItem(ModBlocks.FISSION_REACTOR);
    public static final DeferredItem<BlockItem> COOLANT_CELL = ITEMS.registerSimpleBlockItem(ModBlocks.COOLANT_CELL);
    public static final DeferredItem<BlockItem> URANIUM_ORE = ITEMS.registerSimpleBlockItem(ModBlocks.URANIUM_ORE);
    public static final DeferredItem<BlockItem> DEEPSLATE_URANIUM_ORE = ITEMS.registerSimpleBlockItem(ModBlocks.DEEPSLATE_URANIUM_ORE);
    public static final DeferredItem<BlockItem> REACTOR_SLAG = ITEMS.registerSimpleBlockItem(ModBlocks.REACTOR_SLAG);
    public static final DeferredItem<Item> RAW_URANIUM = ITEMS.registerSimpleItem("raw_uranium");
    public static final DeferredItem<Item> URANIUM_INGOT = ITEMS.registerSimpleItem("uranium_ingot");
    public static final DeferredItem<FuelRodItem> FUEL_ROD = ITEMS.register("fuel_rod",
            () -> new FuelRodItem(new Item.Properties().durability(24000).rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<Item> DEPLETED_FUEL_ROD = ITEMS.registerSimpleItem("depleted_fuel_rod");
    public static final DeferredItem<MultimeterItem> MULTIMETER = ITEMS.register("multimeter",
            () -> new MultimeterItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<DeferredSpawnEggItem> SCP_173_EGG = ITEMS.register("scp_173_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.SCP_173, 0x96928A, 0x8E2A1E, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> SCP_096_EGG = ITEMS.register("scp_096_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.SCP_096, 0xDED8D2, 0x5C1A1A, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> SCP_049_EGG = ITEMS.register("scp_049_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.SCP_049, 0x221F24, 0xE4E0D0, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> SCP_049_2_EGG = ITEMS.register("scp_049_2_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.SCP_049_2, 0x96B8C4, 0x7E8678, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> SCP_106_EGG = ITEMS.register("scp_106_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.SCP_106, 0x1E1814, 0x42301C, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> SCP_999_EGG = ITEMS.register("scp_999_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.SCP_999, 0xF68E28, 0xFFBA5C, new Item.Properties()));

    public static final DeferredItem<DeferredSpawnEggItem> GATE_GUARDIAN_EGG = ITEMS.register("gate_guardian_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.GATE_GUARDIAN, 0xFFE9A0, 0xFF6A00, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> O5_MEMBER_EGG = ITEMS.register("o5_member_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.O5_MEMBER, 0x111114, 0x8B0000, new Item.Properties()));

    private static DeferredItem<KeycardItem> keycard(int level, Rarity rarity) {
        return ITEMS.register("keycard_" + level, () -> new KeycardItem(level, new Item.Properties().stacksTo(1).rarity(rarity)));
    }

    private ModItems() {}
}
