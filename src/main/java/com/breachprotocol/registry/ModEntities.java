package com.breachprotocol.registry;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.entity.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, BreachProtocol.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<Scp173Entity>> SCP_173 = ENTITIES.register("scp_173",
            () -> EntityType.Builder.of(Scp173Entity::new, MobCategory.MONSTER).sized(0.9F, 2.4F).fireImmune()
                    .clientTrackingRange(10).build("scp_173"));
    public static final DeferredHolder<EntityType<?>, EntityType<Scp096Entity>> SCP_096 = ENTITIES.register("scp_096",
            () -> EntityType.Builder.of(Scp096Entity::new, MobCategory.MONSTER).sized(0.7F, 2.7F)
                    .clientTrackingRange(10).build("scp_096"));
    public static final DeferredHolder<EntityType<?>, EntityType<Scp049Entity>> SCP_049 = ENTITIES.register("scp_049",
            () -> EntityType.Builder.of(Scp049Entity::new, MobCategory.MONSTER).sized(0.6F, 1.95F)
                    .clientTrackingRange(8).build("scp_049"));
    public static final DeferredHolder<EntityType<?>, EntityType<Scp0492Entity>> SCP_049_2 = ENTITIES.register("scp_049_2",
            () -> EntityType.Builder.<Scp0492Entity>of(Scp0492Entity::new, MobCategory.MONSTER).sized(0.6F, 1.95F)
                    .clientTrackingRange(8).build("scp_049_2"));
    public static final DeferredHolder<EntityType<?>, EntityType<Scp106Entity>> SCP_106 = ENTITIES.register("scp_106",
            () -> EntityType.Builder.of(Scp106Entity::new, MobCategory.MONSTER).sized(0.6F, 1.9F).fireImmune()
                    .clientTrackingRange(10).build("scp_106"));
    public static final DeferredHolder<EntityType<?>, EntityType<Scp999Entity>> SCP_999 = ENTITIES.register("scp_999",
            () -> EntityType.Builder.of(Scp999Entity::new, MobCategory.CREATURE).sized(1.0F, 0.85F)
                    .clientTrackingRange(8).build("scp_999"));

    public static final DeferredHolder<EntityType<?>, EntityType<GateGuardianEntity>> GATE_GUARDIAN = ENTITIES.register("gate_guardian",
            () -> EntityType.Builder.of(GateGuardianEntity::new, MobCategory.MISC).sized(1.6F, 5.4F).fireImmune()
                    .clientTrackingRange(12).build("gate_guardian"));
    public static final DeferredHolder<EntityType<?>, EntityType<O5MemberEntity>> O5_MEMBER = ENTITIES.register("o5_member",
            () -> EntityType.Builder.of(O5MemberEntity::new, MobCategory.MISC).sized(0.6F, 1.8F)
                    .clientTrackingRange(8).build("o5_member"));

    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(SCP_173.get(), Scp173Entity.createAttributes().build());
        event.put(SCP_096.get(), Scp096Entity.createAttributes().build());
        event.put(SCP_049.get(), Scp049Entity.createAttributes().build());
        event.put(SCP_049_2.get(), Scp0492Entity.createAttributes().build());
        event.put(SCP_106.get(), Scp106Entity.createAttributes().build());
        event.put(SCP_999.get(), Scp999Entity.createAttributes().build());
        event.put(GATE_GUARDIAN.get(), GateGuardianEntity.createAttributes().build());
        event.put(O5_MEMBER.get(), O5MemberEntity.createAttributes().build());
    }

    private ModEntities() {}
}
