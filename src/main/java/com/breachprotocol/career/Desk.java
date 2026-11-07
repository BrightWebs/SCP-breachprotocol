package com.breachprotocol.career;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.network.DialoguePayload;
import com.breachprotocol.registry.ModBlocks;
import com.breachprotocol.world.SiteTower;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Opens the Administrator's desktop (from the Administrator's Terminal block). Site towers built
 * before the terminal existed get one on the Administrator's desk, in the free spot beside the
 * monitor; nothing else in the office is touched.
 */
@EventBusSubscriber(modid = BreachProtocol.MODID)
public final class Desk {
    private Desk() {}

    public static void open(ServerPlayer p) {
        Career.sync(p);
        CompoundTag t = new CompoundTag();
        t.putBoolean("desktop", true);
        PacketDistributor.sendToPlayer(p, new DialoguePayload(t));
    }

    /** Where the terminal stands in a tower built around this site's centre. */
    public static BlockPos deskSpot(Sites.Site s) {
        BlockPos o = new BlockPos(s.x - SiteTower.T / 2, s.y, s.z - SiteTower.T / 2);
        return o.offset(16, SiteTower.y(SiteTower.TOP) + 2, 25);
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 100 != 37) return;
        ServerLevel ow = event.getServer().overworld();
        for (Sites.Site s : Sites.get(event.getServer()).sites) {
            if (s.name.equals("Site-01") || !s.dim.equals("minecraft:overworld")) continue;
            Forces.Data fd = Forces.data(event.getServer());
            if (fd.terminals.contains(s.name)) continue;
            BlockPos at = deskSpot(s);
            if (!ow.isLoaded(at) || ow.getNearestPlayer(at.getX(), at.getY(), at.getZ(), 48, false) == null) continue;
            // only on a tower's Administrator desk, and only if that spot is still empty
            String desk = BuiltInRegistries.BLOCK.getKey(ow.getBlockState(at.below()).getBlock()).getPath();
            if (!desk.equals("office_desk")) continue;
            fd.terminals.add(s.name);
            fd.setDirty();
            if (!ow.getBlockState(at).isAir()) continue;
            ow.setBlock(at, ModBlocks.ADMIN_TERMINAL.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH), 3);
        }
    }
}
