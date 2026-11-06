package com.breachprotocol.registry;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.entity.GateGuardianEntity;
import com.breachprotocol.entity.O5MemberEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, BreachProtocol.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<GateGuardianEntity>> GATE_GUARDIAN = ENTITIES.register("gate_guardian",
            () -> EntityType.Builder.of(GateGuardianEntity::new, MobCategory.MISC).sized(1.6F, 5.4F).fireImmune()
                    .clientTrackingRange(12).build("gate_guardian"));
    public static final DeferredHolder<EntityType<?>, EntityType<O5MemberEntity>> O5_MEMBER = ENTITIES.register("o5_member",
            () -> EntityType.Builder.of(O5MemberEntity::new, MobCategory.MISC).sized(0.6F, 1.8F)
                    .clientTrackingRange(8).build("o5_member"));

    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(GATE_GUARDIAN.get(), GateGuardianEntity.createAttributes().build());
        event.put(O5_MEMBER.get(), O5MemberEntity.createAttributes().build());
    }

    private ModEntities() {}
}
