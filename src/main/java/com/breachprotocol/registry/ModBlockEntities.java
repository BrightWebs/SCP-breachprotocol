package com.breachprotocol.registry;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.power.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, BreachProtocol.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CoalGeneratorBlockEntity>> COAL_GENERATOR = BLOCK_ENTITIES.register("coal_generator",
            () -> BlockEntityType.Builder.of(CoalGeneratorBlockEntity::new, ModBlocks.COAL_GENERATOR.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WireBlockEntity>> COPPER_WIRE = BLOCK_ENTITIES.register("copper_wire",
            () -> BlockEntityType.Builder.of(WireBlockEntity::new, ModBlocks.COPPER_WIRE.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WireBlockEntity>> HEAVY_WIRE = BLOCK_ENTITIES.register("heavy_wire",
            () -> BlockEntityType.Builder.of(WireBlockEntity::new, ModBlocks.HEAVY_WIRE.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BatteryBlockEntity>> BATTERY = BLOCK_ENTITIES.register("battery",
            () -> BlockEntityType.Builder.of(BatteryBlockEntity::new, ModBlocks.BATTERY.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OutletBlockEntity>> OUTLET = BLOCK_ENTITIES.register("power_outlet",
            () -> BlockEntityType.Builder.of(OutletBlockEntity::new, ModBlocks.POWER_OUTLET.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ReactorBlockEntity>> REACTOR = BLOCK_ENTITIES.register("fission_reactor",
            () -> BlockEntityType.Builder.of(ReactorBlockEntity::new, ModBlocks.FISSION_REACTOR.get()).build(null));

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, COAL_GENERATOR.get(), (be, side) -> be.getEnergy(side));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, COPPER_WIRE.get(), (be, side) -> be.getEnergy(side));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, HEAVY_WIRE.get(), (be, side) -> be.getEnergy(side));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, BATTERY.get(), (be, side) -> be.getEnergy(side));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, OUTLET.get(), (be, side) -> be.getEnergy(side));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, REACTOR.get(), (be, side) -> be.getEnergy(side));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, COAL_GENERATOR.get(), (be, side) -> be.fuel());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, REACTOR.get(), (be, side) -> be.rods());
    }

    private ModBlockEntities() {}
}
