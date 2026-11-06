package com.breachprotocol.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;

/** SCP-049-2: the doctor's "cured" patients. Never burn, never drown-convert, never babies. */
public class Scp0492Entity extends Zombie implements Anomaly {
    public Scp0492Entity(EntityType<? extends Zombie> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Zombie.createAttributes()
                .add(Attributes.MAX_HEALTH, 30)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.ATTACK_DAMAGE, 5);
    }

    @Override
    protected boolean isSunSensitive() {
        return false;
    }

    @Override
    protected boolean convertsInWater() {
        return false;
    }

    @Override
    public void setBaby(boolean baby) {
        super.setBaby(false);
    }

    @Override
    public boolean canBeContained() {
        return false; // instances, not the anomaly itself
    }

    @Override
    public String designation() {
        return "SCP-049-2";
    }

    @Override
    public String objectClass() {
        return "Euclid";
    }
}
