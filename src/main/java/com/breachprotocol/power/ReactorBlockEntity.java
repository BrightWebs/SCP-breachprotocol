package com.breachprotocol.power;

import com.breachprotocol.career.Career;
import com.breachprotocol.career.O5;
import com.breachprotocol.item.FuelRodItem;
import com.breachprotocol.registry.ModBlockEntities;
import com.breachprotocol.registry.ModBlocks;
import com.breachprotocol.registry.ModEffects;
import com.breachprotocol.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.items.ItemStackHandler;

public class ReactorBlockEntity extends PowerBlockEntity implements PowerBlock.TickingPower {
    public static final int OUTPUT = 600;
    public static final float MELTDOWN = 1000F, CRITICAL = 800F, HOT = 600F;
    private float heat;
    private float cooling;
    private boolean scram;
    private int lastOut;

    private final ItemStackHandler rods = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.getItem() instanceof FuelRodItem;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    public ReactorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.REACTOR.get(), pos, state, 1_000_000, 0, 4096);
    }

    public ItemStackHandler rods() {
        return rods;
    }

    public float heat() {
        return heat;
    }

    /** Coolant within a 3x3x3 cube around the core. */
    private float measureCooling() {
        float c = 0.25F; // ambient
        for (BlockPos p : BlockPos.betweenClosed(worldPosition.offset(-1, -1, -1), worldPosition.offset(1, 1, 1))) {
            if (p.equals(worldPosition)) continue;
            BlockState s = level.getBlockState(p);
            if (level.getFluidState(p).is(Fluids.WATER) && level.getFluidState(p).isSource()) c += 0.32F;
            else if (s.is(Blocks.BLUE_ICE)) c += 0.9F;
            else if (s.is(Blocks.PACKED_ICE)) c += 0.55F;
            else if (s.is(Blocks.ICE)) c += 0.35F;
            else if (s.is(ModBlocks.COOLANT_CELL.get())) c += 1.3F;
            else if (s.is(Blocks.LAVA) || level.getFluidState(p).is(Fluids.LAVA)) c -= 1.0F;
        }
        return c;
    }

    @Override
    public void serverTick() {
        if (level.getGameTime() % 20 == 0) cooling = measureCooling();
        scram = level.hasNeighborSignal(worldPosition);
        ItemStack rod = rods.getStackInSlot(0);
        boolean running = !scram && !rod.isEmpty() && rod.getDamageValue() < rod.getMaxDamage();
        if (running) {
            energy.generate(OUTPUT);
            heat += 2.2F;
            if (level.getGameTime() % 4 == 0) {
                rod.setDamageValue(rod.getDamageValue() + 4);
                if (rod.getDamageValue() >= rod.getMaxDamage()) rods.setStackInSlot(0, new ItemStack(com.breachprotocol.registry.ModItems.DEPLETED_FUEL_ROD.get()));
                setChanged();
            }
        }
        heat = Math.max(0, heat - cooling * (scram ? 2.5F : 1F));
        lastOut = 0;
        for (Direction d : Direction.values()) lastOut += pushTo(d, 4096);

        int band = !running && heat < 50 ? 0 : heat < HOT ? 1 : heat < CRITICAL ? 2 : 3;
        BlockState st = getBlockState();
        if (st.getValue(ReactorBlock.HEAT) != band) level.setBlock(worldPosition, st.setValue(ReactorBlock.HEAT, band), Block.UPDATE_CLIENTS);

        if (heat >= HOT && level.getGameTime() % 40 == 0 && level instanceof ServerLevel sl) warn(sl);
        if (heat >= CRITICAL && level.getGameTime() % 20 == 0) irradiate(10, 0);
        if (heat >= MELTDOWN && level instanceof ServerLevel sl) meltdown(sl);
    }

    private void warn(ServerLevel sl) {
        boolean critical = heat >= CRITICAL;
        sl.playSound(null, worldPosition, ModSounds.BREACH_ALARM.get(), SoundSource.BLOCKS, critical ? 4F : 2F, critical ? 1.6F : 1.3F);
        for (ServerPlayer p : sl.players()) {
            if (p.blockPosition().closerThan(worldPosition, 48)) {
                p.displayClientMessage(Component.literal((critical ? "REACTOR CRITICAL: " : "REACTOR OVERHEATING: ") + Math.round(heat) + "°  (SCRAM with redstone, add coolant)")
                        .withStyle(critical ? ChatFormatting.DARK_RED : ChatFormatting.GOLD, ChatFormatting.BOLD), true);
            }
        }
    }

    private void irradiate(double radius, int amp) {
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(worldPosition).inflate(radius))) {
            e.addEffect(new MobEffectInstance(ModEffects.h(ModEffects.RADIATION), 20 * 30, amp));
        }
    }

    private void meltdown(ServerLevel sl) {
        BlockPos at = worldPosition;
        heat = 0;
        rods.setStackInSlot(0, ItemStack.EMPTY);
        irradiate(28, 1);
        sl.removeBlock(at, false);
        sl.explode(null, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 9F, true, Level.ExplosionInteraction.BLOCK);
        // radioactive slag left in the crater
        for (int i = 0; i < 14; i++) {
            BlockPos p = at.offset(sl.random.nextInt(9) - 4, sl.random.nextInt(5) - 3, sl.random.nextInt(9) - 4);
            if (sl.getBlockState(p).isAir() && sl.getBlockState(p.below()).isSolid()) sl.setBlockAndUpdate(p, ModBlocks.REACTOR_SLAG.get().defaultBlockState());
        }
        for (ServerPlayer p : sl.players()) {
            if (p.blockPosition().closerThan(at, 160)) {
                p.sendSystemMessage(Component.literal("[SITE ALERT] REACTOR MELTDOWN at " + at.toShortString() + ". Radiation zone established.")
                        .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
                if (p.blockPosition().closerThan(at, 96)) {
                    Career.message(p, O5.O5_12, "That reactor cost more than your entire department. I'm taking it out of site stability.");
                    Career.changeStability(p, -20);
                }
            }
        }
    }

    @Override
    public Component status() {
        ItemStack rod = rods.getStackInSlot(0);
        String fuel = rod.isEmpty() ? "no fuel" : rod.getItem() instanceof FuelRodItem
                ? String.format("fuel %.0f%%", 100F * (rod.getMaxDamage() - rod.getDamageValue()) / Math.max(1, rod.getMaxDamage())) : "spent rod (sneak-use to remove)";
        ChatFormatting c = heat >= CRITICAL ? ChatFormatting.DARK_RED : heat >= HOT ? ChatFormatting.GOLD : ChatFormatting.GREEN;
        return Component.literal(String.format("Reactor: %s  |  %.0f° (cooling %.1f/t)  |  %s  |  %s  |  out %d FE/t",
                scram ? "SCRAM" : "ONLINE", heat, cooling, fuel, PowerBlock.fe(energy.getEnergyStored()), lastOut)).withStyle(c);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider r) {
        super.saveAdditional(tag, r);
        tag.put("Rods", rods.serializeNBT(r));
        tag.putFloat("Heat", heat);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider r) {
        super.loadAdditional(tag, r);
        rods.deserializeNBT(r, tag.getCompound("Rods"));
        heat = tag.getFloat("Heat");
    }
}
