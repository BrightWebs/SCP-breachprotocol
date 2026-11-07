package com.breachprotocol.registry;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.entity.FoundationStaff;
import com.breachprotocol.entity.NpcSeat;
import com.breachprotocol.entity.O5MemberEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, BreachProtocol.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<O5MemberEntity>> O5_MEMBER = ENTITIES.register("o5_member",
            () -> EntityType.Builder.of(O5MemberEntity::new, MobCategory.MISC).sized(0.6F, 1.8F).eyeHeight(1.62F)
                    .vehicleAttachment(net.minecraft.world.entity.player.Player.DEFAULT_VEHICLE_ATTACHMENT)
                    .clientTrackingRange(8).build("o5_member"));

    public static final DeferredHolder<EntityType<?>, EntityType<FoundationStaff>> STAFF = ENTITIES.register("foundation_staff",
            () -> EntityType.Builder.of(FoundationStaff::new, MobCategory.MISC).sized(0.6F, 1.8F).eyeHeight(1.62F)
                    .vehicleAttachment(net.minecraft.world.entity.player.Player.DEFAULT_VEHICLE_ATTACHMENT)
                    .clientTrackingRange(8).build("foundation_staff"));

    public static final DeferredHolder<EntityType<?>, EntityType<NpcSeat>> NPC_SEAT = ENTITIES.register("npc_seat",
            () -> EntityType.Builder.<NpcSeat>of(NpcSeat::new, MobCategory.MISC).sized(0.25F, 0.25F).noSummon().fireImmune()
                    .clientTrackingRange(10).updateInterval(20).build("npc_seat"));

    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(O5_MEMBER.get(), O5MemberEntity.createAttributes().build());
        event.put(STAFF.get(), FoundationStaff.createAttributes().build());
    }

    private ModEntities() {}
}
