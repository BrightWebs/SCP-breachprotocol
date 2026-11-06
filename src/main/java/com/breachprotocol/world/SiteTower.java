package com.breachprotocol.world;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.career.Sites;
import com.breachprotocol.compat.Unleashed;
import com.breachprotocol.compat.UnleashedApi;
import com.breachprotocol.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;

/**
 * A full Foundation site, built on the spot by O5 and above: a ten-storey tower over two underground
 * levels, laid out the way Foundation sites are usually described.
 *
 * <pre>
 * Roof   launch deck: SCP Unleashed rocket and satellite platforms, fuelled, on creative power
 * 9      Administrator's office
 * 8      O5 Council chamber
 * 7      launch control (satellite and orbital array)
 * 6      server and communications
 * 5      department offices
 * 4      archive and records
 * 3      security and MTF armory
 * 2      medical wing
 * 1      research laboratories (powered machines)
 * G      Entrance Zone: lobby, Gate A checkpoint, reception
 * B1     Light Containment Zone (Safe and lower-risk Euclid cells) + power plant
 * B2     Heavy Containment Zone (Keter and dangerous Euclid cells), deepest
 * </pre>
 * Every machine is wired to a Creative Outlet with SCP Unleashed's own creative-tier wire. Each cell
 * is assigned to one SCP (sign, plaque and keycard level by class) and recognised by the phone.
 */
public final class SiteTower {
    private SiteTower() {}

    public static final int W = 31, FLOORS = 10, STOREY = 6;
    private static final int UX0 = -10, UX1 = 40, UZ0 = -10, UZ1 = 40;
    private static final int FLAGS = Block.UPDATE_CLIENTS;

    private record CellSpec(String entity, String name, String objectClass, String line1, String line2, int clearance, boolean window, String extra) {}

    private static final List<CellSpec> LCZ = List.of(
            new CellSpec(Unleashed.SCP_173, "SCP-173", "Euclid", "Never alone with it.", "Eyes on it, always.", 3, true, ""),
            new CellSpec(Unleashed.SCP_131, "SCP-131", "Safe", "Friendly. Curious.", "Keep the door shut.", 2, true, "soft"),
            new CellSpec(Unleashed.SCP_066, "SCP-066", "Euclid", "Ear protection", "required.", 3, true, "soft"),
            new CellSpec(Unleashed.SCP_999, "SCP-999", "Safe", "Visits encouraged.", "Morale use only.", 2, true, "soft"),
            new CellSpec("scp105_iris", "SCP-105", "Safe", "Resident. Treat", "her with respect.", 2, true, "room"),
            new CellSpec("scp529_cat", "SCP-529", "Safe", "Half a cat.", "Do not pick up.", 2, true, "soft"));

    private static final List<CellSpec> HCZ = List.of(
            new CellSpec(Unleashed.SCP_096, "SCP-096", "Euclid", "NO CAMERAS.", "NO WINDOWS.", 4, false, "dark"),
            new CellSpec(Unleashed.SCP_106, "SCP-106", "Keter", "Forcefield sealed.", "Never enter alone.", 5, true, "field"),
            new CellSpec(Unleashed.SCP_682, "SCP-682", "Keter", "Do not speak", "to it.", 5, true, "heavy"),
            new CellSpec(Unleashed.SCP_939, "SCP-939", "Keter", "Ignore voices", "you know.", 4, true, "dark"),
            new CellSpec(Unleashed.SCP_049, "SCP-049", "Euclid", "Hazmat at all", "times inside.", 4, true, ""),
            new CellSpec(Unleashed.SCP_457, "SCP-457", "Euclid", "No flammables", "on this floor.", 4, true, "fire"),
            new CellSpec("scp008_zombie", "SCP-008", "Keter", "Biohazard.", "Full decon after.", 5, true, "bio"),
            new CellSpec("scp035_host", "SCP-035", "Keter", "Do not touch", "the mask.", 5, true, ""));

    // ------------------------------------------------------------------ helpers

    private static BlockState u(String id, Block fallback) { return Unleashed.block(id, fallback).defaultBlockState(); }
    private static BlockState b(Block block) { return block.defaultBlockState(); }

    /** Furniture and machines: placed like a player places them, so multi-block parts are built. */
    private static void put(ServerLevel l, BlockPos o, int x, int y, int z, String id, Block fallback) {
        put(l, o, x, y, z, id, fallback, null);
    }

    private static void put(ServerLevel l, BlockPos o, int x, int y, int z, String id, Block fallback, Direction facing) {
        Block blk = Unleashed.block(id, fallback);
        if (!Placer.place(l, o.offset(x, y, z), blk, facing) && fallback != Blocks.AIR)
            Placer.place(l, o.offset(x, y, z), fallback, facing); // never leave a half-built block
    }

    /** A ceiling lamp, hung from the block above. */
    private static void lamp(ServerLevel l, BlockPos o, int x, int y, int z, String id) {
        Block blk = Unleashed.block(id, Blocks.SEA_LANTERN);
        if (!Placer.ceiling(l, o.offset(x, y, z), blk)) l.setBlock(o.offset(x, y, z), Blocks.SEA_LANTERN.defaultBlockState(), Block.UPDATE_CLIENTS);
    }

    /** A wall-mounted sign or light: mounted on whichever horizontal neighbour is solid. */
    private static void wallPut(ServerLevel l, BlockPos o, int x, int y, int z, String id) {
        Block blk = Unleashed.block(id, Blocks.AIR);
        if (blk == Blocks.AIR) return;
        BlockPos p = o.offset(x, y, z);
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (l.getBlockState(p.relative(d)).isFaceSturdy(l, p.relative(d), d.getOpposite()) && Placer.wall(l, p, blk, d.getOpposite())) return;
        }
        Placer.ceiling(l, p, blk);
    }

    private static void set(ServerLevel l, BlockPos o, int x, int y, int z, BlockState s) { l.setBlock(o.offset(x, y, z), s, FLAGS); }

    private static void fill(ServerLevel l, BlockPos o, int x1, int y1, int z1, int x2, int y2, int z2, BlockState s) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++)
                    l.setBlock(o.offset(x, y, z), s, FLAGS);
    }

    private static ItemStack ui(String id, ItemLike fallback, int n) { return new ItemStack(Unleashed.item(id, fallback.asItem()), n); }
    private static ItemStack st(ItemLike i, int n) { return new ItemStack(i, n); }

    private static void chest(ServerLevel l, BlockPos o, int x, int y, int z, Direction f, ItemStack... items) {
        BlockPos p = o.offset(x, y, z);
        l.setBlock(p, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, f), FLAGS);
        if (l.getBlockEntity(p) instanceof ChestBlockEntity c) for (int i = 0; i < items.length && i < 27; i++) c.setItem(i, items[i]);
    }

    private static void bed(ServerLevel l, BlockPos o, int x, int y, int z, Block bed, Direction f) {
        Placer.place(l, o.offset(x, y, z), bed, f);
    }

    /** An exit button on the inside of a door: you can always get out (mobs can't press buttons). */
    private static void exitButton(ServerLevel l, BlockPos o, int x, int y, int z, Direction facing) {
        l.setBlock(o.offset(x, y, z), Blocks.POLISHED_BLACKSTONE_BUTTON.defaultBlockState()
                .setValue(ButtonBlock.FACE, net.minecraft.world.level.block.state.properties.AttachFace.WALL).setValue(ButtonBlock.FACING, facing), FLAGS);
    }

    private static void ironDoor(ServerLevel l, BlockPos o, int x, int y, int z, Direction f) {
        BlockState d = Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.FACING, f);
        set(l, o, x, y, z, d.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        set(l, o, x, y + 1, z, d.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    /** SCP Unleashed keycard scanner set to a clearance level, facing out from its wall. */
    private static void scanner(ServerLevel l, BlockPos o, int x, int y, int z, Direction f, int level) {
        BlockState s = u("keycard_scanner", Blocks.STONE_BUTTON);
        for (var prop : s.getProperties()) {
            if (prop.getName().equals("level") && prop instanceof net.minecraft.world.level.block.state.properties.IntegerProperty ip && ip.getPossibleValues().contains(level)) s = s.setValue(ip, level);
            if (prop.getName().equals("facing") && prop instanceof net.minecraft.world.level.block.state.properties.DirectionProperty dp && dp.getPossibleValues().contains(f)) s = s.setValue(dp, f);
        }
        set(l, o, x, y, z, s);
    }

    private static void sign(ServerLevel l, BlockPos o, int x, int y, int z, Direction f, String... lines) {
        BlockPos p = o.offset(x, y, z);
        l.setBlock(p, Blocks.DARK_OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, f), FLAGS);
        if (l.getBlockEntity(p) instanceof SignBlockEntity sb) {
            SignText t = new SignText();
            for (int i = 0; i < lines.length && i < 4; i++) t = t.setMessage(i, Component.literal(lines[i]));
            t = t.setHasGlowingText(true).setColor(net.minecraft.world.item.DyeColor.LIME);
            sb.setText(t, true);
            sb.setChanged();
        }
    }

    private static void lights(ServerLevel l, BlockPos o, int x1, int x2, int z1, int z2, int y, int step, String lampId) {
        for (int x = x1; x <= x2; x += step) for (int z = z1; z <= z2; z += step) lamp(l, o, x, y, z, lampId);
    }

    /** A Creative Outlet that powers the listed machines with creative-tier wire. */
    private static void power(ServerLevel l, BlockPos o, int ox, int oy, int oz, int[]... machines) {
        set(l, o, ox, oy, oz, u("creative_outlet", Blocks.REDSTONE_BLOCK));
        BlockPos src = o.offset(ox, oy, oz);
        for (int[] m : machines) UnleashedApi.wire(l, src, o.offset(m[0], m[1], m[2]));
    }

    // ------------------------------------------------------------------ entry point

    /** Builds the site with its ground-floor centre at {@code centre}. Returns the origin (north-west corner). */
    public static BlockPos build(ServerLevel l, BlockPos centre, String ownerUuid, String ownerName) {
        int gy = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, centre.getX(), centre.getZ());
        BlockPos o = new BlockPos(centre.getX() - W / 2, gy, centre.getZ() - W / 2);
        for (int cx = (o.getX() + UX0 - 2) >> 4; cx <= (o.getX() + UX1 + 2) >> 4; cx++)
            for (int cz = (o.getZ() + UZ0 - 2) >> 4; cz <= (o.getZ() + UZ1 + 2) >> 4; cz++)
                l.getChunk(cx, cz);
        underground(l, o);
        tower(l, o);
        roof(l, o);
        String name = register(l, o, ownerUuid, ownerName);
        BreachProtocol.LOGGER.info("Built {} at {}", name, o);
        return o;
    }

    private static String register(ServerLevel l, BlockPos o, String uuid, String owner) {
        Sites all = Sites.get(l.getServer());
        Sites.Site s = new Sites.Site();
        int n = 10 + Math.floorMod(o.getX() * 13 + o.getZ() * 7, 80);
        s.name = "Site-" + n;
        s.owner = uuid;
        s.ownerName = owner;
        s.dim = l.dimension().location().toString();
        s.x = o.getX() + W / 2;
        s.y = o.getY();
        s.z = o.getZ() + W / 2;
        s.radius = 40;
        all.sites.add(s);
        all.setDirty();
        return s.name;
    }

    // ------------------------------------------------------------------ underground

    private static void underground(ServerLevel l, BlockPos o) {
        BlockState shell = b(Unleashed.cellWall()), air = b(Blocks.AIR);
        fill(l, o, UX0, -16, UZ0, UX1, -1, UZ1, shell);

        // ---- B1: Light Containment Zone (west) and the power plant (east). Floor y=-7, rooms -6..-2
        int y = -6;
        fill(l, o, UX0 + 1, y, 13, UX1 - 1, y + 4, 17, air);                         // main corridor
        fill(l, o, UX0 + 1, y - 1, 13, UX1 - 1, y - 1, 17, u("lcz_floor", Blocks.SMOOTH_STONE));
        for (int x = UX0 + 2; x < UX1; x += 4) lamp(l, o, x, y + 4, 15, "lcz_lamp");
        sign(l, o, 3, y + 2, 13, Direction.SOUTH, "LIGHT", "CONTAINMENT", "ZONE", "B1");
        // LCZ cells: three north, three south of the corridor, x -9..21
        for (int i = 0; i < LCZ.size(); i++) {
            boolean north = i % 2 == 0;
            int cx = UX0 + 1 + (i / 2) * 10;
            cell(l, o, LCZ.get(i), cx, y, north ? 3 : 19, north);
        }
        // checkpoint to the plant
        wallPut(l, o, 22, y + 3, 14, "facility_sign_heavy_containment_small");
        // power plant: x 24..39, both sides of the corridor
        fill(l, o, 24, y, 3, 39, y + 4, 27, air);
        fill(l, o, 24, y - 1, 3, 39, y - 1, 27, u("power_floor", Blocks.POLISHED_BLACKSTONE));
        lights(l, o, 26, 38, 5, 25, y + 4, 4, "power_lamp");
        sign(l, o, 24, y + 2, 12, Direction.EAST, "POWER PLANT", "Creative supply", "feeds the whole", "site.");
        for (int z = 5; z <= 25; z += 5) {
            set(l, o, 38, y, z, u("power_generator_housing_bottom", Blocks.IRON_BLOCK));
            set(l, o, 38, y + 1, z, u("power_generator_housing_middle", Blocks.IRON_BLOCK));
            set(l, o, 38, y + 2, z, u("power_generator_housing_top", Blocks.IRON_BLOCK));
        }
        for (int z = 6; z <= 24; z += 6) {
            set(l, o, 25, y, z, u("power_breaker_panel_bottom", Blocks.IRON_BLOCK));
            set(l, o, 25, y + 1, z, u("power_breaker_panel_middle", Blocks.IRON_BLOCK));
            set(l, o, 25, y + 2, z, u("power_breaker_panel_top", Blocks.IRON_BLOCK));
        }
        put(l, o, 31, y, 4, "power_control_console", Blocks.LECTERN);
        for (int x = 26; x <= 37; x += 3) { set(l, o, x, y, 9, u("creative_outlet", Blocks.REDSTONE_BLOCK)); set(l, o, x, y, 21, u("creative_outlet", Blocks.REDSTONE_BLOCK)); }
        for (int x = 26; x <= 37; x++) set(l, o, x, y + 3, 15, u("cable_tray", Blocks.IRON_BARS));
        chest(l, o, 31, y, 26, Direction.NORTH,
                ui("creative_outlet", Items.REDSTONE_BLOCK, 16), ui("creative_energy_source", Items.REDSTONE_BLOCK, 4), ui("creative_wire", Items.STRING, 64),
                ui("wire_coil", Items.STRING, 64), ui("power_cable_spool", Items.STRING, 16), ui("battery", Items.REDSTONE_BLOCK, 8));

        // ---- B2: Heavy Containment Zone. Floor y=-15, rooms -14..-10
        y = -14;
        fill(l, o, UX0 + 1, y, 13, UX1 - 1, y + 4, 17, air);
        fill(l, o, UX0 + 1, y - 1, 13, UX1 - 1, y - 1, 17, u("hcz_floor", Blocks.POLISHED_DEEPSLATE));
        for (int x = UX0 + 2; x < UX1; x += 4) lamp(l, o, x, y + 4, 15, "hcz_lamp");
        sign(l, o, 3, y + 2, 13, Direction.SOUTH, "HEAVY", "CONTAINMENT", "ZONE", "B2");
        for (int i = 0; i < HCZ.size(); i++) {
            boolean north = i % 2 == 0;
            int cx = UX0 + 1 + (i / 2) * 12;
            cell(l, o, HCZ.get(i), cx, y, north ? 3 : 19, north);
        }

        // ladder shaft from the lobby down to B2, at x=2, z=15 (inside the corridors)
        for (int yy = -14; yy <= 0; yy++) {
            set(l, o, 2, yy, 15, b(Blocks.AIR));
            set(l, o, 1, yy, 15, b(Unleashed.cellWall()));
            set(l, o, 2, yy, 15, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.EAST));
        }
    }

    /**
     * One containment cell, assigned to one SCP: 9 wide, 9 deep, opening onto the corridor through an
     * iron door behind a keycard scanner set to the SCP's clearance. Windows where the lore allows.
     */
    private static void cell(ServerLevel l, BlockPos o, CellSpec c, int x0, int y, int z0, boolean north) {
        BlockState air = b(Blocks.AIR);
        int x1 = x0 + 8, z1 = z0 + 8;
        fill(l, o, x0, y, z0, x1, y + 4, z1, air);
        BlockState floor = switch (c.extra()) {
            case "soft" -> u("containment_room_padded_panel", Blocks.WHITE_WOOL);
            case "fire" -> b(Blocks.OBSIDIAN);
            case "bio" -> u("containment_room_floor_grate", Blocks.IRON_BLOCK);
            case "room" -> b(Blocks.SPRUCE_PLANKS);
            default -> u("containment_room_floor", Blocks.SMOOTH_STONE);
        };
        fill(l, o, x0, y - 1, z0, x1, y - 1, z1, floor);
        if (!c.extra().equals("dark")) set(l, o, x0 + 4, y + 4, z0 + 4, u(c.extra().equals("fire") ? "containment_room_lamp_red" : "containment_room_lamp", Blocks.SEA_LANTERN));
        if (c.extra().equals("soft")) for (int x = x0; x <= x1; x++) for (int yy = y; yy <= y + 2; yy++) { set(l, o, x, yy, north ? z0 : z1, u("containment_room_padded_panel", Blocks.WHITE_WOOL)); }
        if (c.extra().equals("room")) {
            bed(l, o, x0 + 1, y, z0 + 1, Blocks.WHITE_BED, Direction.SOUTH);
            set(l, o, x0 + 7, y, z0 + 1, u("binder_shelf", Blocks.BOOKSHELF));
        }
        // the wall facing the corridor
        int wz = north ? z1 + 1 : z0 - 1;
        Direction out = north ? Direction.SOUTH : Direction.NORTH;
        int dx = x0 + 4;
        ironDoor(l, o, dx, y, wz, out);
        scanner(l, o, dx + 1, y + 1, wz + (north ? 1 : -1), out, c.clearance());
        exitButton(l, o, dx + 1, y + 1, wz + (north ? -1 : 1), out.getOpposite());
        if (c.window()) for (int x = x0 + 1; x <= x0 + 2; x++) for (int yy = y + 1; yy <= y + 2; yy++) set(l, o, x, yy, wz, u("reinforced_glass", Blocks.GLASS));
        sign(l, o, dx - 2, y + 2, wz + (north ? 1 : -1), out, c.name(), c.objectClass().toUpperCase(), c.line1(), c.line2());
        wallPut(l, o, dx + 2, y + 3, wz + (north ? 1 : -1), "containment_room_alarm_light");
        // SCP-106: forcefield emitters on the floor and ceiling across the back of the cell, on creative power
        if (c.extra().equals("field")) {
            int fz = north ? z0 + 2 : z1 - 2;
            List<int[]> em = new ArrayList<>();
            for (int x = x0 + 2; x <= x0 + 6; x += 2) {
                set(l, o, x, y - 1, fz, u("forcefield_emitter", Blocks.BEACON));
                set(l, o, x, y + 4, fz, u("forcefield_emitter", Blocks.BEACON));
                em.add(new int[]{x, y - 1, fz});
                em.add(new int[]{x, y + 4, fz});
            }
            power(l, o, x0 + 8, y, north ? z0 + 1 : z1 - 1, em.toArray(new int[0][]));
        }
        // assigned to this SCP: the phone recognises it
        Sites.addCell(l, c.name() + " chamber", c.entity(), o.offset(x0, y - 1, z0), o.offset(x1, y + 4, z1));
    }

    // ------------------------------------------------------------------ the tower

    private static void tower(ServerLevel l, BlockPos o) {
        BlockState air = b(Blocks.AIR), frame = u("facility_concrete", Blocks.LIGHT_GRAY_CONCRETE), slab = u("ez_floor", Blocks.POLISHED_ANDESITE);
        BlockState glass = b(Blocks.LIGHT_GRAY_STAINED_GLASS_PANE), steel = u("facility_corrugated_steel", Blocks.IRON_BLOCK);
        int top = FLOORS * STOREY;
        // clear the plot and a margin
        fill(l, o, -3, 0, -3, W + 2, top + 12, W + 2, air);
        fill(l, o, -3, -1, -3, W + 2, -1, W + 2, u("facility_concrete", Blocks.GRAY_CONCRETE));
        // shell
        for (int f = 0; f < FLOORS; f++) {
            int y = f * STOREY;
            fill(l, o, 0, y, 0, W - 1, y, W - 1, slab);                                 // floor
            for (int yy = y + 1; yy < y + STOREY; yy++) {
                for (int i = 0; i < W; i++) {
                    boolean column = i % 6 == 0;
                    BlockState wall = f == 0 ? u("reinforced_containment_wall", Blocks.POLISHED_DEEPSLATE) : column || yy == y + STOREY - 1 ? frame : glass;
                    if (f > 0 && !column && yy < y + STOREY - 1) wall = glass.setValue(BlockStateProperties.NORTH, true).setValue(BlockStateProperties.SOUTH, true);
                    set(l, o, i, yy, 0, f > 0 && !column && yy < y + STOREY - 1 ? glass.setValue(BlockStateProperties.EAST, true).setValue(BlockStateProperties.WEST, true) : wall);
                    set(l, o, i, yy, W - 1, f > 0 && !column && yy < y + STOREY - 1 ? glass.setValue(BlockStateProperties.EAST, true).setValue(BlockStateProperties.WEST, true) : wall);
                    set(l, o, 0, yy, i, wall);
                    set(l, o, W - 1, yy, i, wall);
                }
            }
            // corridor walls (z=13 and z=17) with 3-wide doorways at the middle
            for (int x = 1; x < W - 1; x++) for (int yy = y + 1; yy < y + STOREY; yy++) {
                boolean door = x >= 14 && x <= 16 && yy <= y + 3;
                set(l, o, x, yy, 13, door ? air : steel);
                set(l, o, x, yy, 17, door ? air : steel);
            }
            for (int x = 3; x < W - 1; x += 5) lamp(l, o, x, y + STOREY - 1, 15, "facility_lamp_white");
            // the ladder: hole in each floor at (2, 15), ladder on the west wall
            for (int yy = y; yy < y + STOREY; yy++) set(l, o, 2, yy, 15, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.EAST));
        }
        fill(l, o, 0, top, 0, W - 1, top, W - 1, slab);                                       // roof slab
        for (int yy = top; yy <= top + 1; yy++) set(l, o, 2, yy, 15, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.EAST));
        for (int i = 0; i < W; i++) { set(l, o, i, top + 1, 0, frame); set(l, o, i, top + 1, W - 1, frame); set(l, o, 0, top + 1, i, frame); set(l, o, W - 1, top + 1, i, frame); }

        lobby(l, o, 0);
        research(l, o, STOREY);
        medical(l, o, STOREY * 2);
        armory(l, o, STOREY * 3);
        archive(l, o, STOREY * 4);
        offices(l, o, STOREY * 5);
        servers(l, o, STOREY * 6);
        launchControl(l, o, STOREY * 7);
        council(l, o, STOREY * 8);
        administrator(l, o, STOREY * 9);
    }

    /** Floor sign by the doorway of a room (north room: z 1..12, south room: z 18..29). */
    private static void roomSign(ServerLevel l, BlockPos o, int y, boolean north, String a, String b2) {
        sign(l, o, 13, y + 2, north ? 14 : 16, north ? Direction.SOUTH : Direction.NORTH, a, b2);
    }

    private static void lobby(ServerLevel l, BlockPos o, int y) {
        BlockState air = b(Blocks.AIR);
        // Gate A: the main entrance on the south face
        fill(l, o, 13, y + 1, W - 1, 17, y + 4, W - 1, air);
        wallPut(l, o, 15, y + 5, W, "facility_sign_gate_a_header");
        set(l, o, 12, y + 1, 29, u("reinforced_containment_wall", Blocks.POLISHED_DEEPSLATE));
        set(l, o, 18, y + 1, 29, u("reinforced_containment_wall", Blocks.POLISHED_DEEPSLATE));
        fill(l, o, 1, y, 18, 29, y, 29, u("ez_floor", Blocks.POLISHED_ANDESITE));
        put(l, o, 15, y + 1, 21, "computer_terminal", Blocks.LECTERN);
        for (int x = 5; x <= 9; x++) put(l, o, x, y + 1, 27, "ez_waiting_chairs", Blocks.OAK_STAIRS);
        for (int x = 21; x <= 25; x++) put(l, o, x, y + 1, 27, "ez_waiting_chairs", Blocks.OAK_STAIRS);
        put(l, o, 3, y + 1, 28, "ez_planter", Blocks.POTTED_FERN); put(l, o, 27, y + 1, 28, "ez_planter", Blocks.POTTED_FERN);
        wallPut(l, o, 15, y + 3, 18, "ez_directory_sign");
        roomSign(l, o, y, false, "ENTRANCE ZONE", "Gate A");
        // north side: security office overlooking the lobby + archive terminal for staff
        put(l, o, 5, y + 1, 4, "crt_monitor", Blocks.AIR);
        put(l, o, 6, y + 1, 4, "crt_monitor", Blocks.AIR);
        put(l, o, 5, y + 1, 5, "corner_desk", Blocks.SPRUCE_PLANKS);
        put(l, o, 24, y + 1, 4, "archive", Blocks.LECTERN);
        roomSign(l, o, y, true, "SECURITY", "Staff only");
        chest(l, o, 27, y + 1, 3, Direction.SOUTH,
                ui("keycard_level_1", Items.PAPER, 8), ui("keycard_level_2", Items.PAPER, 4), ui("keycard_level_3", Items.PAPER, 2),
                st(ModItems.PHONE.get(), 4), ui("facility_tablet", Items.MAP, 2), ui("handheld_radio", Items.COMPASS, 4));
    }

    private static void research(ServerLevel l, BlockPos o, int y) {
        fill(l, o, 1, y, 1, 29, y, 12, u("facility_lab_tile", Blocks.WHITE_CONCRETE));
        fill(l, o, 1, y, 18, 29, y, 29, u("facility_lab_tile", Blocks.WHITE_CONCRETE));
        roomSign(l, o, y, true, "RESEARCH", "Laboratory A");
        roomSign(l, o, y, false, "RESEARCH", "Laboratory B");
        // lab A: processing machines along the north wall, powered
        int[][] a = {{4, y + 1, 2}, {8, y + 1, 2}, {12, y + 1, 2}, {18, y + 1, 2}, {22, y + 1, 2}};
        put(l, o, 4, y + 1, 2, "centrifuge", Blocks.CAULDRON);
        put(l, o, 8, y + 1, 2, "advanced_assembler", Blocks.CRAFTING_TABLE);
        put(l, o, 12, y + 1, 2, "advanced_materials_bench", Blocks.SMITHING_TABLE);
        put(l, o, 18, y + 1, 2, "lab_analyzer", Blocks.LECTERN);
        put(l, o, 22, y + 1, 2, "lab_viral_storage", Blocks.BARREL);
        power(l, o, 14, y + 1, 7, a);
        for (int x = 4; x <= 24; x += 4) set(l, o, x, y + 1, 11, u("chemical_shelf", Blocks.BOOKSHELF));
        // lab B: cloning and specimen storage
        put(l, o, 5, y + 1, 27, "cloning_machine", Blocks.BREWING_STAND);
        power(l, o, 8, y + 1, 24, new int[]{5, y + 1, 27});
        for (int x = 12; x <= 24; x += 3) put(l, o, x, y + 1, 28, "containment_room_specimen_tank", Blocks.GLASS);
        put(l, o, 20, y + 1, 22, "examination_table", Blocks.WHITE_BED);
        chest(l, o, 28, y + 1, 20, Direction.WEST,
                ui("hazmat_hood", Items.LEATHER_HELMET, 4), ui("hazmat_suit", Items.LEATHER_CHESTPLATE, 4), ui("hazmat_trousers", Items.LEATHER_LEGGINGS, 4),
                ui("hazmat_boots", Items.LEATHER_BOOTS, 4), ui("gas_mask", Items.LEATHER_HELMET, 4), ui("energum_dust", Items.GLOWSTONE_DUST, 64),
                ui("rift_extractor", Items.SPYGLASS, 2), ui("rift_detector", Items.COMPASS, 2), ui("stasis_pod_faint", Items.GLASS_BOTTLE, 6),
                ui("stasis_pod_volatile", Items.GLASS_BOTTLE, 4), ui("stasis_pod_severe", Items.GLASS_BOTTLE, 2), ui("scp096_bag", Items.LEATHER, 2), ui("scramble_goggles", Items.CARVED_PUMPKIN, 4));
    }

    private static void medical(ServerLevel l, BlockPos o, int y) {
        fill(l, o, 1, y, 1, 29, y, 12, u("facility_medical_tile", Blocks.WHITE_CONCRETE));
        fill(l, o, 1, y, 18, 29, y, 29, u("facility_medical_tile", Blocks.WHITE_CONCRETE));
        roomSign(l, o, y, true, "MEDICAL", "Treatment");
        roomSign(l, o, y, false, "MEDICAL", "Decontamination");
        for (int x = 3; x <= 27; x += 4) put(l, o, x, y + 1, 3, "examination_table", Blocks.WHITE_BED);
        chest(l, o, 28, y + 1, 10, Direction.WEST,
                new ItemStack(Unleashed.scp500(), 8), ui("scp3008_bandage", Items.PAPER, 32), ui("amnestic_canister", Items.MILK_BUCKET, 8),
                st(Items.GOLDEN_APPLE, 16), ui("scp207_bottle", Items.HONEY_BOTTLE, 4));
        for (int x = 4; x <= 26; x += 6) put(l, o, x, y + 1, 28, "lcz_cell_bunk", Blocks.WHITE_BED);
        wallPut(l, o, 15, y + 4, 23, "facility_sign_decontamination_small");
    }

    private static void armory(ServerLevel l, BlockPos o, int y) {
        fill(l, o, 1, y, 1, 29, y, 12, u("facility_diamond_plate", Blocks.IRON_BLOCK));
        fill(l, o, 1, y, 18, 29, y, 29, u("facility_diamond_plate", Blocks.IRON_BLOCK));
        roomSign(l, o, y, true, "ARMORY", "Level 3");
        roomSign(l, o, y, false, "MTF", "Barracks");
        scanner(l, o, 17, y + 2, 14, Direction.SOUTH, 3);
        chest(l, o, 3, y + 1, 2, Direction.SOUTH,
                ui("assault_rifle", Items.CROSSBOW, 4), ui("combat_shotgun", Items.CROSSBOW, 2), ui("pistol", Items.CROSSBOW, 4), ui("machine_gun", Items.CROSSBOW, 2),
                ui("rifle_rounds", Items.ARROW, 64), ui("rifle_rounds", Items.ARROW, 64), ui("handgun_rounds", Items.ARROW, 64), ui("shotgun_shells", Items.ARROW, 64),
                ui("frag_grenade", Items.TNT, 16), ui("flash_grenade", Items.SNOWBALL, 16), ui("gas_grenade", Items.SPLASH_POTION, 8), ui("incendiary_grenade", Items.FIRE_CHARGE, 8));
        chest(l, o, 7, y + 1, 2, Direction.SOUTH,
                ui("mtf_helmet", Items.IRON_HELMET, 8), ui("mtf_vest", Items.IRON_CHESTPLATE, 8), ui("mtf_signal_radio", Items.COMPASS, 2),
                ui("unstable_fragment_faint", Items.AMETHYST_SHARD, 8), ui("unstable_fragment_wavering", Items.AMETHYST_SHARD, 6), ui("unstable_fragment_volatile", Items.AMETHYST_SHARD, 4),
                ui("unstable_fragment_severe", Items.AMETHYST_SHARD, 2), ui("unstable_fragment_abyssal", Items.AMETHYST_SHARD, 1));
        for (int x = 4; x <= 26; x += 4) bed(l, o, x, y + 1, 28, Blocks.GRAY_BED, Direction.SOUTH);
        wallPut(l, o, 15, y + 4, 2, "facility_sign_armory");
    }

    private static void archive(ServerLevel l, BlockPos o, int y) {
        fill(l, o, 1, y, 1, 29, y, 12, u("facility_carpet_blue", Blocks.BLUE_CARPET));
        fill(l, o, 1, y, 18, 29, y, 29, u("facility_carpet_blue", Blocks.BLUE_CARPET));
        roomSign(l, o, y, true, "ARCHIVE", "Dossiers");
        roomSign(l, o, y, false, "RECORDS", "Personnel files");
        for (int z = 2; z <= 10; z += 2) for (int x = 3; x <= 27; x++) if (x % 6 != 0) { set(l, o, x, y + 1, z, u("binder_shelf", Blocks.BOOKSHELF)); set(l, o, x, y + 2, z, u("binder_shelf", Blocks.BOOKSHELF)); }
        put(l, o, 6, y + 1, 21, "archive", Blocks.LECTERN);
        put(l, o, 10, y + 1, 21, "archive", Blocks.LECTERN);
        put(l, o, 14, y + 1, 21, "archive_database_terminal", Blocks.LECTERN);
        for (int x = 18; x <= 26; x += 2) put(l, o, x, y + 1, 26, "corner_desk", Blocks.SPRUCE_PLANKS);
        chest(l, o, 28, y + 1, 28, Direction.WEST, ui("blank_personnel_file", Items.PAPER, 32), ui("sealed_document", Items.PAPER, 4));
    }

    private static void offices(ServerLevel l, BlockPos o, int y) {
        fill(l, o, 1, y, 1, 29, y, 12, u("facility_carpet_maroon", Blocks.RED_CARPET));
        fill(l, o, 1, y, 18, 29, y, 29, u("facility_carpet_maroon", Blocks.RED_CARPET));
        roomSign(l, o, y, true, "SITE DIRECTOR", "Level 4");
        roomSign(l, o, y, false, "DEPARTMENTS", "Research / Security");
        scanner(l, o, 17, y + 2, 14, Direction.SOUTH, 4);
        for (int[] d : new int[][]{{5, 4}, {12, 4}, {20, 4}, {5, 22}, {12, 22}, {20, 22}, {26, 26}}) {
            put(l, o, d[0], y + 1, d[1], "corner_desk", Blocks.SPRUCE_PLANKS);
            put(l, o, d[0], y + 2, d[1], "crt_monitor", Blocks.AIR);
            put(l, o, d[0] + 1, y + 2, d[1], "desk_lamp", Blocks.AIR);
        }
        for (int[] c : new int[][]{{2, 11}, {28, 11}, {2, 19}, {28, 28}}) put(l, o, c[0], y + 1, c[1], "ez_planter", Blocks.POTTED_FERN);
    }

    private static void servers(ServerLevel l, BlockPos o, int y) {
        fill(l, o, 1, y, 1, 29, y, 12, u("power_floor", Blocks.POLISHED_BLACKSTONE));
        fill(l, o, 1, y, 18, 29, y, 29, u("power_floor", Blocks.POLISHED_BLACKSTONE));
        roomSign(l, o, y, true, "SERVER ROOM", "Network core");
        roomSign(l, o, y, false, "COMMS", "Radio / Uplink");
        put(l, o, 4, y + 1, 3, "network_hub", Blocks.LODESTONE);
        put(l, o, 8, y + 1, 3, "network_router", Blocks.OBSERVER);
        put(l, o, 12, y + 1, 3, "network_router", Blocks.OBSERVER);
        put(l, o, 16, y + 1, 3, "chat_server", Blocks.JUKEBOX);
        put(l, o, 20, y + 1, 3, "computer", Blocks.LECTERN);
        put(l, o, 24, y + 1, 3, "computer", Blocks.LECTERN);
        power(l, o, 14, y + 1, 8, new int[]{4, y + 1, 3}, new int[]{8, y + 1, 3}, new int[]{12, y + 1, 3}, new int[]{16, y + 1, 3}, new int[]{20, y + 1, 3}, new int[]{24, y + 1, 3});
        for (int x = 3; x <= 27; x++) set(l, o, x, y + 4, 6, u("cable_tray", Blocks.IRON_BARS));
        put(l, o, 15, y + 1, 26, "radio_booster_tower", Blocks.LIGHTNING_ROD);
        power(l, o, 12, y + 1, 26, new int[]{15, y + 1, 26});
        chest(l, o, 28, y + 1, 20, Direction.WEST, ui("camera_tablet", Items.MAP, 2), ui("facility_tablet", Items.MAP, 2), ui("handheld_radio", Items.COMPASS, 4));
    }

    private static void launchControl(ServerLevel l, BlockPos o, int y) {
        fill(l, o, 1, y, 1, 29, y, 12, u("power_floor", Blocks.POLISHED_BLACKSTONE));
        fill(l, o, 1, y, 18, 29, y, 29, u("power_floor", Blocks.POLISHED_BLACKSTONE));
        roomSign(l, o, y, true, "LAUNCH CONTROL", "Roof platforms");
        roomSign(l, o, y, false, "ORBITAL", "ZENITH array");
        scanner(l, o, 17, y + 2, 14, Direction.SOUTH, 5);
        for (int x = 4; x <= 26; x += 3) { put(l, o, x, y + 1, 3, "computer_terminal", Blocks.LECTERN); put(l, o, x, y + 2, 2, "crt_monitor", Blocks.AIR); }
        put(l, o, 15, y + 1, 7, "satellite_terminal", Blocks.LECTERN);
        power(l, o, 17, y + 1, 7, new int[]{15, y + 1, 7});
        chest(l, o, 3, y + 1, 28, Direction.EAST,
                ui("orbital_designator", Items.SPYGLASS, 2), ui("orbital_capacitor_ring", Items.IRON_INGOT, 1), ui("orbital_emitter_barrel", Items.IRON_INGOT, 1),
                ui("orbital_focusing_array", Items.IRON_INGOT, 1), ui("orbital_heat_sink", Items.IRON_INGOT, 1), ui("orbital_power_core", Items.IRON_INGOT, 1),
                ui("orbital_targeting_core", Items.IRON_INGOT, 1), ui("satellite", Items.ENDER_EYE, 2), ui("rocket", Items.FIREWORK_ROCKET, 1), ui("rocket_engine", Items.BLAZE_POWDER, 2),
                ui("heavy_fuel_bucket", Items.LAVA_BUCKET, 4));
    }

    private static void council(ServerLevel l, BlockPos o, int y) {
        fill(l, o, 1, y, 1, 29, y, 29, u("facility_carpet_maroon", Blocks.RED_CARPET));
        // one big chamber: open the corridor walls on this floor
        fill(l, o, 1, y + 1, 13, 29, y + STOREY - 1, 13, b(Blocks.AIR));
        fill(l, o, 1, y + 1, 17, 29, y + STOREY - 1, 17, b(Blocks.AIR));
        for (int x = 3; x < W - 1; x += 5) lamp(l, o, x, y + STOREY - 1, 15, "facility_lamp_red");
        sign(l, o, 15, y + 3, 1, Direction.SOUTH, "THE O5", "COUNCIL", "Secure. Contain.", "Protect.");
        for (int x = 9; x <= 21; x++) for (int z = 13; z <= 17; z++) put(l, o, x, y + 1, z, "conference_table", Blocks.DARK_OAK_SLAB);
        for (int x = 9; x <= 21; x += 2) { set(l, o, x, y + 1, 12, Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH)); set(l, o, x, y + 1, 18, Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH)); }
        set(l, o, 22, y + 1, 15, Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST));
        for (int[] c : new int[][]{{2, 2}, {28, 2}, {2, 28}, {28, 28}}) put(l, o, c[0], y + 1, c[1], "ez_planter", Blocks.POTTED_FERN);
    }

    private static void administrator(ServerLevel l, BlockPos o, int y) {
        fill(l, o, 1, y, 1, 29, y, 29, u("facility_carpet_maroon", Blocks.RED_CARPET));
        fill(l, o, 1, y + 1, 13, 29, y + STOREY - 1, 13, b(Blocks.AIR));
        fill(l, o, 1, y + 1, 17, 29, y + STOREY - 1, 17, b(Blocks.AIR));
        for (int x = 3; x < W - 1; x += 5) for (int z = 4; z < W - 1; z += 6) lamp(l, o, x, y + STOREY - 1, z, "facility_lamp_purple");
        sign(l, o, 15, y + 3, 1, Direction.SOUTH, "THE", "ADMINISTRATOR", "", "");
        for (int x = 13; x <= 17; x++) put(l, o, x, y + 1, 6, "corner_desk", Blocks.DARK_OAK_PLANKS);
        put(l, o, 14, y + 2, 6, "crt_monitor", Blocks.AIR);
        put(l, o, 15, y + 2, 6, "computer", Blocks.AIR);
        put(l, o, 16, y + 2, 6, "desk_lamp", Blocks.AIR);
        set(l, o, 15, y + 1, 5, Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
        for (int x = 2; x <= 28; x++) { set(l, o, x, y + 1, 1, u("binder_shelf", Blocks.BOOKSHELF)); set(l, o, x, y + 2, 1, u("binder_shelf", Blocks.BOOKSHELF)); }
        put(l, o, 3, y + 1, 10, "archive", Blocks.LECTERN);
        put(l, o, 3, y + 1, 12, "archive_database_terminal", Blocks.LECTERN);
        for (int x = 10; x <= 20; x++) put(l, o, x, y + 1, 22, "conference_table", Blocks.DARK_OAK_SLAB);
        bed(l, o, 26, y + 1, 26, Blocks.PURPLE_BED, Direction.SOUTH);
        chest(l, o, 28, y + 1, 22, Direction.WEST,
                ui("keycard_omni", Items.PAPER, 2), ui("orbital_designator", Items.SPYGLASS, 1), ui("stasis_pod_abyssal", Items.GLASS_BOTTLE, 3),
                ui("mtf_signal_radio", Items.COMPASS, 1), ui("rift_detector", Items.COMPASS, 1), ui("camera_tablet", Items.MAP, 1), ui("facility_tablet", Items.MAP, 1),
                new ItemStack(Unleashed.scp500(), 8), ui("creative_outlet", Items.REDSTONE_BLOCK, 8), ui("creative_wire", Items.STRING, 32),
                st(ModItems.PHONE.get(), 1));
        for (int[] c : new int[][]{{2, 28}, {28, 2}, {2, 14}, {28, 14}}) put(l, o, c[0], y + 1, c[1], "ez_planter", Blocks.POTTED_FERN);
    }

    // ------------------------------------------------------------------ roof launch deck

    private static void roof(ServerLevel l, BlockPos o) {
        int y = FLOORS * STOREY + 1;
        fill(l, o, 1, y - 1, 1, 29, y - 1, 29, u("power_floor", Blocks.POLISHED_BLACKSTONE));
        for (int x = 2; x <= 28; x++) { set(l, o, x, y - 1, 15, u("power_floor_marked", Blocks.YELLOW_CONCRETE)); }
        sign(l, o, 3, y, 14, Direction.SOUTH, "LAUNCH DECK", "Rocket: east", "Satellite: west", "Fuelled.");
        boolean rocket = UnleashedApi.placeLaunchPlatform(l, o.offset(22, y, 8), "rocket_platform", Direction.SOUTH);
        boolean sat = UnleashedApi.placeLaunchPlatform(l, o.offset(8, y, 8), "satellite_platform", Direction.SOUTH);
        // creative power for the pads (outlets mounted beside them; wired where the pad accepts it)
        set(l, o, 22, y, 22, u("creative_outlet", Blocks.REDSTONE_BLOCK));
        set(l, o, 8, y, 22, u("creative_outlet", Blocks.REDSTONE_BLOCK));
        UnleashedApi.wire(l, o.offset(22, y, 22), o.offset(22, y, 8));
        UnleashedApi.wire(l, o.offset(8, y, 22), o.offset(8, y, 8));
        chest(l, o, 15, y, 26, Direction.NORTH,
                ui("rocket", Items.FIREWORK_ROCKET, 1), ui("satellite", Items.ENDER_EYE, 2), ui("heavy_fuel_bucket", Items.LAVA_BUCKET, 8),
                ui("rocket_platform", Items.IRON_BLOCK, rocket ? 0 : 1), ui("satellite_platform", Items.IRON_BLOCK, sat ? 0 : 1),
                ui("creative_wire", Items.STRING, 32), ui("portable_launch_pad", Items.IRON_BLOCK, 1));
    }
}
