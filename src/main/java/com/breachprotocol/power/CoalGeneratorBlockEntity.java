package com.breachprotocol.power;

import com.breachprotocol.registry.ModBlockEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

public class CoalGeneratorBlockEntity extends PowerBlockEntity implements PowerBlock.TickingPower {
    public static final int RATE = 40;
    private int burn, burnMax;
    private final ItemStackHandler fuel = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.getBurnTime(RecipeType.SMELTING) > 0;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    public CoalGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.COAL_GENERATOR.get(), pos, state, 64_000, 0, 160);
    }

    public ItemStackHandler fuel() {
        return fuel;
    }

    @Override
    public void serverTick() {
        if (burn <= 0 && energy.getEnergyStored() < energy.getMaxEnergyStored()) {
            ItemStack s = fuel.getStackInSlot(0);
            int t = s.isEmpty() ? 0 : s.getBurnTime(RecipeType.SMELTING);
            if (t > 0) {
                burn = burnMax = t;
                ItemStack remainder = s.getCraftingRemainingItem();
                fuel.extractItem(0, 1, false);
                if (!remainder.isEmpty() && fuel.getStackInSlot(0).isEmpty()) fuel.setStackInSlot(0, remainder);
            }
        }
        boolean lit = burn > 0;
        if (lit) {
            burn--;
            energy.generate(RATE);
        }
        for (Direction d : Direction.values()) pushTo(d, 160);
        BlockState st = getBlockState();
        if (st.getValue(CoalGeneratorBlock.LIT) != lit) level.setBlock(worldPosition, st.setValue(CoalGeneratorBlock.LIT, lit), Block.UPDATE_ALL);
    }

    @Override
    public Component status() {
        ItemStack s = fuel.getStackInSlot(0);
        return Component.literal("Coal Generator: " + PowerBlock.fe(energy.getEnergyStored()) + " stored, "
                + (burn > 0 ? RATE + " FE/t" : "idle") + ", fuel: " + (s.isEmpty() ? "none" : s.getCount() + "x " + s.getHoverName().getString()))
                .withStyle(burn > 0 ? ChatFormatting.GOLD : ChatFormatting.GRAY);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider r) {
        super.saveAdditional(tag, r);
        tag.put("Fuel", fuel.serializeNBT(r));
        tag.putInt("Burn", burn);
        tag.putInt("BurnMax", burnMax);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider r) {
        super.loadAdditional(tag, r);
        fuel.deserializeNBT(r, tag.getCompound("Fuel"));
        burn = tag.getInt("Burn");
        burnMax = tag.getInt("BurnMax");
    }
}
