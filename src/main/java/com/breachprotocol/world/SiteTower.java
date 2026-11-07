package com.breachprotocol.world;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.career.Sites;
import com.breachprotocol.compat.Unleashed;
import com.breachprotocol.compat.UnleashedApi;
import com.breachprotocol.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A Foundation site built from SCP Unleashed's own facility blocks: each zone uses its matching
 * floor, wall (bottom / middle / top), ceiling and window set, the way SCP Unleashed's generated
 * facilities do. Lighting is their fluorescent fittings. Doors are their sliding doors: Entrance
 * Zone doors open by hand; secured doors open from a keycard scanner on the outside and a door
 * button on the inside, both mounted on the door jamb so they power the door through the wall.
 *
 * <pre>
 *  Roof  launch deck (rocket and satellite platforms on creative power), stair penthouse
 *  9     Administrator's office (your office) and private quarters
 *  8     O5 Council chamber
 *  7     Launch / mission control
 *  6     Server hall, network operations, communications
 *  5     Department offices, Site Director, conference, break room
 *  4     Archive hall, records office, document vault
 *  3     Armory, MTF barracks, security control, briefing
 *  2     Medical ward, treatment, pharmacy, medical reception
 *  1     Research labs A and B, greenhouse lab, research office, hazmat store
 *  G     Lobby with reception and waiting area, security checkpoint, security office, cafeteria
 *  B1    Light Containment: ten Safe / Euclid cells, D-Class holding, LCZ control, power plant
 *  B2    Heavy Containment: twelve cells, HCZ security
 *  B3    Keter wing: twelve cells, maintenance
 *  B4    Deep containment: twelve cells, hazmat store
 *        (every containment level has cells on both sides of its corridor)
 * </pre>
 * Every floor is joined by one stairwell in the north-east corner, opening onto a central corridor.
 */
public final class SiteTower extends FacilityBuilder {

    /** Tower footprint is 0..T on x and z; storeys are 6 blocks (floor, 4 clear, ceiling). */
    public static final int T = 32, STOREY = 6, TOP = 9, ROOF = 10;
    /** Basements reach further: x BX0..BX1. */
    private static final int BX0 = -8, BX1 = 41, NZ = 10, LOWEST = -4;


    private static CellSpec c(String e, String n, String cls, String a, String b, int lvl, String extra) { return new CellSpec(e, n, cls, a, b, lvl, extra); }

    // B1 Light Containment: Safe and low-risk Euclid
    private static final List<CellSpec> B1_S = List.of(
            c(Unleashed.SCP_173, "SCP-173", "Euclid", "Never alone.", "Eyes on it.", 3, ""),
            c(Unleashed.SCP_131, "SCP-131", "Safe", "Friendly.", "Keep shut.", 2, "soft"),
            c(Unleashed.SCP_066, "SCP-066", "Euclid", "Ear protection", "required.", 3, "soft"),
            c(Unleashed.SCP_999, "SCP-999", "Safe", "Visits", "encouraged.", 2, "soft"),
            c("scp105_iris", "SCP-105", "Safe", "Resident.", "Be kind.", 2, "room"),
            c("scp529_cat", "SCP-529", "Safe", "Half a cat.", "Don't lift.", 2, "soft"));
    private static final List<CellSpec> B1_N = List.of(
            c("scp343", "SCP-343", "Safe", "Polite at", "all times.", 2, "room"),
            c("scp553_butterfly", "SCP-553", "Safe", "Glass wings.", "Gloves on.", 2, "soft"),
            c("scp1762_dragon", "SCP-1762", "Safe", "Paper only.", "No flame.", 2, "soft"),
            c("scp007", "SCP-007", "Safe", "Do not", "disturb.", 2, ""));
    // B2 Heavy Containment: Euclid and the worst of them
    private static final List<CellSpec> B2_S = List.of(
            c(Unleashed.SCP_049, "SCP-049", "Euclid", "Hazmat at", "all times.", 4, ""),
            c(Unleashed.SCP_457, "SCP-457", "Euclid", "No flammables", "on this floor.", 4, "fire"),
            c("scp008_zombie", "SCP-008", "Euclid", "Biohazard.", "Decon after.", 4, "bio"),
            c(Unleashed.SCP_939, "SCP-939", "Keter", "Ignore voices", "you know.", 5, "dark"),
            c(Unleashed.SCP_106, "SCP-106", "Keter", "Forcefield.", "Never alone.", 5, "field"),
            c(Unleashed.SCP_096, "SCP-096", "Euclid", "NO CAMERAS.", "NO WINDOWS.", 4, "dark"),
            c("scp087_face", "SCP-087", "Euclid", "Do not follow", "it down.", 4, "dark"));
    private static final List<CellSpec> B2_N = List.of(
            c("scp035_host", "SCP-035", "Keter", "Do not touch", "the mask.", 5, ""),
            c("scp111_snail", "SCP-111", "Euclid", "Fire-proof", "gear only.", 3, "fire"),
            c("scp178_watcher", "SCP-178", "Euclid", "Remove the", "glasses.", 3, ""),
            c("scp205_devil", "SCP-205", "Euclid", "Keep lamps", "lit.", 3, ""),
            c("scp513_stalker", "SCP-513", "Euclid", "Don't ring", "the bell.", 3, "dark"));
    // B3 Keter wing
    private static final List<CellSpec> B3_S = List.of(
            c(Unleashed.SCP_682, "SCP-682", "Keter", "Do not speak", "to it.", 5, "heavy"),
            c("scp076", "SCP-076", "Keter", "Sarcophagus", "stays shut.", 5, "heavy"),
            c("scp017_shadow", "SCP-017", "Keter", "Lights on.", "Always.", 5, ""),
            c("scp280_shade", "SCP-280", "Keter", "Never let", "it go dark.", 5, "dark"),
            c("scp303_doorman", "SCP-303", "Keter", "Don't open", "the door.", 5, ""),
            c("scp323", "SCP-323", "Keter", "Skull stays", "locked away.", 5, "heavy"),
            c("scp352", "SCP-352", "Keter", "Cut hair", "on sight.", 5, ""));
    private static final List<CellSpec> B3_N = List.of(
            c("scp372", "SCP-372", "Euclid", "Look straight", "ahead.", 4, ""),
            c("scp469_angel", "SCP-469", "Keter", "No pain", "inside.", 5, ""),
            c("scp538_shadow_spider", "SCP-538", "Keter", "Shadows are", "dangerous.", 5, "dark"),
            c("scp428", "SCP-428", "Keter", "Avoid eye", "contact.", 5, ""),
            c("scp3199", "SCP-3199", "Keter", "Destroy all", "eggs.", 5, "bio"));
    // B4 deep containment
    private static final List<CellSpec> B4_S = List.of(
            c("scp058_heart", "SCP-058", "Keter", "Do not", "listen.", 5, "heavy"),
            c("scp082_fernand", "SCP-082", "Keter", "Never let", "him hungry.", 5, "room"),
            c("scp098_crab", "SCP-098", "Keter", "Count every", "crab.", 5, "bio"),
            c("scp149_fly", "SCP-149", "Keter", "Sealed hive.", "No skin.", 5, "bio"),
            c("scp439_queen", "SCP-439", "Keter", "Hive queen.", "Burn breaches.", 5, "bio"),
            c("scp016_brute", "SCP-016", "Keter", "Biohazard 4.", "Full suit.", 5, "bio"),
            c("scp027_vermin_god", "SCP-027", "Keter", "Vermin seal.", "No food.", 5, ""));
    private static final List<CellSpec> B4_N = List.of(
            c("scp019_brute", "SCP-019", "Keter", "The vase", "stays sealed.", 5, "heavy"),
            c("scp009_statue", "SCP-009", "Keter", "No liquids", "near it.", 5, ""),
            c("scp548_ice_spider", "SCP-548", "Euclid", "Keep it", "frozen.", 4, ""),
            c("scp327_siren", "SCP-327", "Euclid", "Ignore the", "song.", 4, ""),
            c("scp2845", "SCP-2845", "Keter", "Sigils hold", "it. Repair.", 5, "heavy"));

    private SiteTower(ServerLevel l, BlockPos o) { super(l, o); }

    /** Floor height of storey s (0 = ground, negative = basements), relative to the origin. */
    private static int y(int s) { return s * STOREY; }

    // ================================================================== entry point

    /** Builds the site with its ground floor centred on {@code centre}. Returns the origin (north-west corner, ground floor). */
    public static BlockPos build(ServerLevel l, BlockPos centre, String ownerUuid, String ownerName) {
        int gy = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, centre.getX(), centre.getZ());
        BlockPos o = new BlockPos(centre.getX() - T / 2, gy, centre.getZ() - T / 2);
        for (int cx = (o.getX() + BX0 - 4) >> 4; cx <= (o.getX() + BX1 + 4) >> 4; cx++)
            for (int cz = (o.getZ() - NZ - 6) >> 4; cz <= (o.getZ() + T + 6) >> 4; cz++)
                l.getChunk(cx, cz);
        SiteTower b = new SiteTower(l, o);
        b.groundworks();
        b.containmentLevel(-4, HCZP, "DEEP", B4_S, B4_N);
        b.containmentLevel(-3, HCZP, "KETER", B3_S, B3_N);
        b.containmentLevel(-2, HCZP, "HEAVY", B2_S, B2_N);
        b.containmentLevel(-1, LCZP, "LIGHT", B1_S, B1_N);
        b.ground();
        b.research();
        b.medical();
        b.security();
        b.archive();
        b.offices();
        b.servers();
        b.launchControl();
        b.council();
        b.administrator();
        for (int s = 0; s <= TOP; s++) b.facade(s);
        b.roof();
        b.stairwell();
        String name = b.register(ownerUuid, ownerName);
        BreachProtocol.LOGGER.info("Built {} at {}", name, o);
        return o;
    }

    private String register(String uuid, String owner) {
        Sites all = Sites.get(l.getServer());
        Sites.Site s = new Sites.Site();
        int n = 10 + Math.floorMod(o.getX() * 13 + o.getZ() * 7, 80);
        s.name = "Site-" + n;
        s.owner = uuid;
        s.ownerName = owner;
        s.dim = l.dimension().location().toString();
        s.x = o.getX() + T / 2;
        s.y = o.getY();
        s.z = o.getZ() + T / 2;
        s.radius = 40;
        all.sites.add(s);
        all.setDirty();
        return s.name;
    }

    /** Clears the plot, lays the plaza and pours the basement shell. */
    private void groundworks() {
        air(-5, 1, -5, T + 5, y(ROOF) + 8, T + 5);
        fill(-5, 0, -5, T + 5, 0, T + 5, "facility_precast_concrete");
        fill(-5, -1, -5, T + 5, -1, T + 5, "facility_concrete");
        // a walkway from the entrance, edged and lit
        for (int z = T + 1; z <= T + 5; z++) {
            fill(13, 0, z, 19, 0, z, "ez_floor");
            set(12, 0, z, "facility_epoxy_floor_line[facing=east]");
            set(20, 0, z, "facility_epoxy_floor_line[facing=west]");
        }
        for (int x : new int[]{-3, 9, 23, T + 3}) { set(x, 1, T + 3, "ez_planter[facing=south]"); set(x, 1, -3, "ez_planter[facing=north]"); }
        for (int z : new int[]{4, 16, 28}) { set(-3, 1, z, "ez_planter[facing=west]"); set(T + 3, 1, z, "ez_planter[facing=east]"); }
        // basement shell
        fill(BX0 - 1, y(LOWEST) - 1, -NZ - 1, BX1 + 1, -2, T + 1, "facility_concrete");
    }

    // ================================================================== containment levels B1-B4

    /**
     * One containment level: a corridor (z 8..14) with cells on both sides. Seven cells to the south
     * (z 14..32), four to the north (z -10..8) plus one beyond the stairwell, and a zone room
     * (control / security / maintenance / hazmat) beside the stairs. Cells come first, then the
     * corridor is dressed, so nothing overwrites a door.
     */
    private void containmentLevel(int s, Pal zone, String name, List<CellSpec> south, List<CellSpec> north) {
        int Y = y(s);
        fill(BX0, Y, -NZ, BX1, Y + 5, T, "facility_concrete");
        corridor(zone, BX0, BX1, Y);
        for (int i = 0; i < south.size(); i++) cell(south.get(i), BX0 + i * 7, 14, BX0 + i * 7 + 7, T, Y, N);
        if (s == -1) dclassHolding(BX0 + 42, Y);
        for (int i = 0; i < 4 && i < north.size(); i++) cell(north.get(i), BX0 + i * 7, -NZ, BX0 + i * 7 + 7, 8, Y, S);
        if (s == -1) powerPlant(Y);
        else if (north.size() > 4) cell(north.get(4), 32, -NZ, BX1, 8, Y, S);
        zoneRoom(s, zone, Y);

        // corridor dressing: pipes or wall pipes, arrows toward the stairs, zone signs at both ends
        for (int x = BX0 + 1; x < BX1; x++) {
            if (zone == HCZP) { setIfAir(x, Y + 4, 10, "decor_pipe[axis=x]"); setIfAir(x, Y + 4, 12, "decor_pipe[axis=x]"); }
            if ((x - BX0) % 6 == 3 && x != 28 && x != 29) set(x, Y, 11, zone.floor().replace("_floor", "_floor_arrow") + "[facing=" + (x < 28 ? "east" : "west") + "]");
        }
        String zs = s == -1 ? "facility_sign_light_containment" : "facility_sign_heavy_containment";
        sign2(zs, BX0 + 1, Y + 3, 11, E, "none", false);
        sign2(zs, BX1 - 1, Y + 3, 11, W, "none", false);
        plate(BX0 + 1, Y + 2, 12, E, name, "CONTAINMENT", "LEVEL B" + (-s));
        plate(BX1 - 1, Y + 2, 10, W, name, "CONTAINMENT", "LEVEL B" + (-s));
        set(BX0 + 1, Y + 1, 9, "fire_extinguisher[facing=east,wall=true]");
        set(BX1 - 1, Y + 1, 13, "fire_extinguisher[facing=west,wall=true]");
    }

    /** The room beside the stairwell (x 20..26, north side), different on each level. */
    private void zoneRoom(int s, Pal zone, int Y) {
        int x0 = 20, x1 = 26, z0 = -NZ, z1 = 8;
        room(zone, x0, z0, x1, z1, Y, true);
        switch (s) {
            case -1 -> {   // LCZ control
                gate("lcz_room_door", 2, 2, 22, z1, Y, S, 2);
                desksZ("metal_desk", 21, -6, 4, Y, E, "security_monitor[facing=$,lit=true,screen=1]");
                for (int z = -8; z <= 6; z += 2) set(25, Y + 2, z, "wall_monitor[facing=west,hanging=false,lit=true,screen=" + Math.floorMod(z, 10) + "]");
                plate(21, Y + 2, 9, S, "LCZ", "CONTROL");
            }
            case -2 -> {   // HCZ security
                gate("hcz_room_door", 2, 2, 22, z1, Y, S, 3);
                desksZ("metal_desk", 21, -6, 4, Y, E, "security_monitor[facing=$,lit=true,screen=3]");
                for (int z = -8; z <= 6; z += 3) stack(25, Y + 1, z, "hcz_monitor_console", W, 2);
                chest(25, Y + 1, -9, W, ui("mtf_signal_radio", Items.COMPASS, 2), ui("scramble_goggles", Items.CARVED_PUMPKIN, 4), ui("scp096_bag", Items.LEATHER, 2));
                plate(21, Y + 2, 9, S, "HCZ", "SECURITY");
            }
            case -3 -> {   // maintenance
                buttoned("hcz_room_door", 2, 2, 22, z1, Y, S);
                for (int z = -9; z <= 6; z += 3) { stack(21, Y + 1, z, "hcz_pipe_manifold", E, 4); stack(25, Y + 1, z, "hcz_steam_vent", W, 2); }
                for (int z = -8; z <= 5; z += 3) set(25, Y + 3, z, "hcz_valve_panel[facing=west]");
                plate(21, Y + 2, 9, S, "MAINTENANCE");
            }
            default -> {   // hazmat store
                buttoned("hcz_room_door", 2, 2, 22, z1, Y, S);
                for (int z = -9; z <= 6; z++) if (z % 3 != 0) tall(21, Y + 1, z, "locker", E);
                for (int z = -9; z <= 6; z += 3) stack(25, Y + 1, z, "hcz_hazard_crate", W, 2);
                chest(25, Y + 1, 7, W,
                        ui("hazmat_hood", Items.LEATHER_HELMET, 4), ui("hazmat_suit", Items.LEATHER_CHESTPLATE, 4), ui("hazmat_trousers", Items.LEATHER_LEGGINGS, 4),
                        ui("hazmat_boots", Items.LEATHER_BOOTS, 4), ui("gas_mask", Items.LEATHER_HELMET, 4), ui("stasis_pod_severe", Items.GLASS_BOTTLE, 4),
                        ui("stasis_pod_abyssal", Items.GLASS_BOTTLE, 2), ui("rift_extractor", Items.SPYGLASS, 2), ui("rift_detector", Items.COMPASS, 2),
                        ui("energum_dust", Items.GLOWSTONE_DUST, 64));
                plate(21, Y + 2, 9, S, "HAZMAT", "STORE");
            }
        }
    }

    /** B1's power plant, east of the stairwell: generators, breakers, the control console, creative supply. */
    private void powerPlant(int Y) {
        room(POWER, 32, -NZ, BX1, 8, Y, true);
        buttoned("lcz_room_door", 2, 2, 36, 8, Y, S);
        for (int z = -8; z <= 5; z += 3) stackBlocks(BX1 - 1, Y + 1, z, "power_generator_housing", W, 3);
        for (int x = 34; x <= 39; x += 2) stackBlocks(x, Y + 1, -NZ + 1, "power_breaker_panel", S, 3);
        put(36, Y + 1, -3, "power_control_console", Blocks.LECTERN, S);
        for (int z = -7; z <= 4; z += 4) set(33, Y + 1, z, "creative_outlet");
        for (int z = -NZ + 1; z < 8; z++) set(37, Y + 4, z, "cable_tray[orientation=down_z]");
        chest(33, Y + 1, 6, E,
                ui("creative_outlet", Items.REDSTONE_BLOCK, 16), ui("creative_energy_source", Items.REDSTONE_BLOCK, 4), ui("creative_wire", Items.STRING, 64),
                ui("wire_coil", Items.STRING, 64), ui("power_cable_spool", Items.STRING, 16), ui("battery", Items.REDSTONE_BLOCK, 8));
        plate(34, Y + 2, 9, S, "POWER", "PLANT");
    }

    /** D-Class holding: bunks in a locked room (scanner outside, button inside). */
    private void dclassHolding(int x0, int Y) {
        room(LCZP, x0, 14, BX1, T, Y, true);
        gate("lcz_room_door", 2, 2, x0 + 3, 14, Y, N, 1);
        for (int z = 17; z <= 29; z += 3) { put(x0 + 1, Y + 1, z, "lcz_cell_bunk", Blocks.ORANGE_BED, E); put(BX1 - 1, Y + 1, z, "lcz_cell_bunk", Blocks.ORANGE_BED, W); }
        table("cafeteria_table", "cafeteria_chair", x0 + 3, 22, x0 + 4, 24, Y, 2);
        for (int z = 15; z <= 16; z++) tall(BX1 - 1, Y + 1, z, "locker", W);
        plate(x0 + 5, Y + 2, 13, N, "D-CLASS", "HOLDING");
    }

    // ================================================================== G: Entrance Zone

    private void ground() {
        int Y = y(0);
        air(1, Y + 1, 1, T - 1, Y + 4, T - 1);
        room(EZ, 0, 0, 12, 8, Y, true);                      // security office
        room(EZ, 12, 0, 26, 8, Y, true);                     // cafeteria
        room(EZ, 0, 14, T, T, Y, true);                      // lobby
        corridor(EZ, 0, T, Y);

        // ---- lobby
        for (int z = 15; z < T; z++) fill(15, Y, z, 17, Y, z, "facility_carpet_blue");
        for (int z = 15; z < T; z++) { set(14, Y, z, EZ.marked() + "[facing=west]"); set(18, Y, z, EZ.marked() + "[facing=east]"); }
        // security checkpoint into the corridor: turnstiles, and a keyed staff gate
        for (int x = 15; x <= 17; x++) { set(x, Y + 1, 14, "ez_turnstile[facing=south]"); set(x, Y + 2, 14, "minecraft:air"); }
        gate("glass_room_door", 2, 2, 20, 14, Y, S, 1);
        windows(EZ, 9, 14, 13, 14, Y);
        sign2("facility_sign_security_checkpoint", 16, Y + 4, 15, S, "none", false);
        set(19, Y + 3, 15, "facility_sign_entrance_zone_small[facing=south]");
        // reception: a desk facing the door, staff behind
        desksX("reception_desk", 3, 8, 20, Y, N, "computer_terminal[facing=$,lit=true,screen=2]");
        for (int x = 3; x <= 8; x++) set(x, Y + 1, 20, "reception_desk[facing=north,left=" + (x > 3) + ",right=" + (x < 8) + ",shape=straight]");
        scpSign(6, Y + 3, 15, S);
        set(3, Y + 2, 15, "ez_directory_sign[facing=south]");
        set(9, Y + 1, 15, "filing_cabinet[facing=south,open=false]");
        // waiting area
        for (int x = 23; x <= 29; x++) { set(x, Y + 1, 20, "ez_waiting_chairs[facing=south]"); set(x, Y + 1, 24, "ez_waiting_chairs[facing=north]"); }
        set(22, Y + 1, 22, "ez_planter[facing=east]");
        set(30, Y + 1, 22, "ez_planter[facing=west]");
        set(26, Y + 3, 15, "wall_clock[facing=south]");
        for (int[] pp : new int[][]{{1, 15}, {T - 1, 15}, {1, T - 1}, {T - 1, T - 1}, {11, T - 1}, {21, T - 1}}) set(pp[0], Y + 1, pp[1], "ez_planter[facing=south]");
        for (int z = 27; z <= 30; z++) { set(1, Y + 1, z, "potted_plant[facing=east]"); set(T - 1, Y + 1, z, "potted_plant[facing=west]"); }
        set(13, Y + 1, T - 1, "wet_floor_sign[facing=north]");

        // ---- security office
        ezDoor(5, 8, Y, S);
        desksX("metal_desk", 2, 8, 2, Y, S, "security_monitor[facing=$,lit=true,screen=5]");
        for (int z = 4; z <= 6; z++) tall(1, Y + 1, z, "locker", E);
        set(11, Y + 1, 6, "filing_cabinet[facing=west,open=false]");
        chest(11, Y + 1, 2, W,
                ui("keycard_level_1", Items.PAPER, 8), ui("keycard_level_2", Items.PAPER, 4), ui("keycard_level_3", Items.PAPER, 2),
                new ItemStack(ModItems.PHONE.get(), 4), ui("facility_tablet", Items.MAP, 2), ui("handheld_radio", Items.COMPASS, 4));
        set(7, Y + 3, 9, "facility_sign_security_checkpoint_small[facing=south]");
        plate(4, Y + 2, 9, S, "SECURITY", "OFFICE");

        // ---- cafeteria
        ezDoor(18, 8, Y, S);
        table("cafeteria_table", "cafeteria_chair", 14, 2, 15, 3, Y, 1);
        table("cafeteria_table", "cafeteria_chair", 22, 2, 23, 3, Y, 1);
        table("cafeteria_table", "cafeteria_chair", 14, 6, 15, 6, Y, 1);
        table("cafeteria_table", "cafeteria_chair", 22, 6, 23, 6, Y, 1);
        for (int x = 18; x <= 20; x++) set(x, Y + 1, 1, "scp3008_kitchen_base_cabinet[facing=south]");
        set(17, Y + 1, 1, "potted_plant[facing=south]");
        set(21, Y + 1, 1, "potted_plant[facing=south]");
        set(16, Y + 3, 9, "facility_sign_cafeteria_small[facing=south]");

        // the entrance doors and Gate A header are part of the facade
    }

    // ================================================================== 1: Research

    private void research() {
        int Y = y(1);
        air(1, Y + 1, 1, T - 1, Y + 4, T - 1);
        room(GREEN, 0, 0, 9, 8, Y, true);
        room(RESEARCH, 9, 0, 18, 8, Y, true);
        room(RESEARCH, 18, 0, 26, 8, Y, true);
        room(RESEARCH, 0, 14, 16, T, Y, true);
        room(RESEARCH, 16, 14, T, T, Y, true);
        corridor(RESEARCH, 0, T, Y);
        windows(RESEARCH, 2, 14, 5, 14, Y);
        windows(RESEARCH, 27, 14, 30, 14, Y);

        // ---- Lab A: bench islands, machines on the back wall, chemical shelves
        gate("glass_room_door", 2, 2, 7, 14, Y, N, 2);
        desksX("lab_bench_desk", 4, 12, 20, Y, N, null);
        desksX("lab_bench_desk", 4, 12, 21, Y, S, null);
        for (int x = 4; x <= 12; x += 4) { set(x, Y + 2, 20, "crt_monitor[facing=north,lit=true,screen=4]"); set(x, Y + 2, 21, "desk_lamp[facing=south,lit=true]"); }
        for (int z = 16; z <= 30; z += 2) set(1, Y + 1, z, "chemical_shelf[facing=east]");
        int[][] a = {{3, Y + 1, 30}, {7, Y + 1, 30}, {11, Y + 1, 30}, {14, Y + 1, 30}};
        put(3, Y + 1, 30, "centrifuge", Blocks.CAULDRON, N);
        put(7, Y + 1, 30, "advanced_assembler", Blocks.CRAFTING_TABLE, N);
        put(11, Y + 1, 30, "advanced_materials_bench", Blocks.SMITHING_TABLE, N);
        put(14, Y + 1, 30, "lab_analyzer", Blocks.LECTERN, N);
        power(9, Y + 1, 27, a);
        for (int z = 17; z <= 27; z += 5) set(15, Y + 2, z, "research_analyser_panel[facing=west]");
        plate(10, Y + 2, 13, N, "LABORATORY A", "Processing");

        // ---- Lab B: specimen tanks, cloning, examination
        gate("glass_room_door", 2, 2, 23, 14, Y, N, 3);
        for (int x = 19; x <= 29; x += 2) stack(x, Y + 1, 31, "containment_room_specimen_tank", N, 3);
        put(18, Y + 1, 24, "cloning_machine", Blocks.BREWING_STAND, E);
        power(17, Y + 1, 27, new int[]{18, Y + 1, 24});
        put(24, Y + 1, 22, "examination_table", Blocks.WHITE_BED, E);
        set(26, Y + 1, 22, "vitals_monitor[facing=west,lit=true]");
        for (int z = 16; z <= 28; z += 2) set(31, Y + 1, z, "specimen_shelf[facing=west]");
        put(28, Y + 1, 18, "lab_viral_storage", Blocks.BARREL, W);
        power(29, Y + 1, 16, new int[]{28, Y + 1, 18});
        chest(17, Y + 1, 30, E,
                ui("hazmat_hood", Items.LEATHER_HELMET, 4), ui("hazmat_suit", Items.LEATHER_CHESTPLATE, 4), ui("hazmat_trousers", Items.LEATHER_LEGGINGS, 4),
                ui("hazmat_boots", Items.LEATHER_BOOTS, 4), ui("gas_mask", Items.LEATHER_HELMET, 4), ui("energum_dust", Items.GLOWSTONE_DUST, 64));
        plate(26, Y + 2, 13, N, "LABORATORY B", "Biology");

        // ---- Greenhouse lab
        ezDoor(4, 8, Y, S);
        for (int x = 1; x <= 8; x++) stack(x, Y + 1, 1, "greenhouse_grow_rack", S, 2);
        for (int x = 2; x <= 7; x += 2) set(x, Y + 4, 4, "greenhouse_hanging_planter[facing=south]");
        stack(1, Y + 1, 6, "greenhouse_seed_cabinet", E, 2);
        stack(8, Y + 1, 6, "greenhouse_seed_cabinet", W, 2);
        plate(3, Y + 2, 9, S, "GREENHOUSE", "LAB");

        // ---- Research office
        ezDoor(13, 8, Y, S);
        desksX("office_desk", 10, 12, 2, Y, S, "crt_monitor[facing=$,lit=true,screen=6]");
        desksX("office_desk", 15, 17, 2, Y, S, "crt_monitor[facing=$,lit=true,screen=7]");
        for (int x = 10; x <= 17; x++) if (x < 13 || x > 14) set(x, Y + 1, 7, "filing_cabinet[facing=north,open=false]");
        set(10, Y + 3, 5, "office_wall_calendar[facing=east]");
        plate(12, Y + 2, 9, S, "RESEARCH", "OFFICE");

        // ---- Hazmat store
        ezDoor(21, 8, Y, S);
        for (int x = 19; x <= 25; x++) tall(x, Y + 1, 1, "locker", S);
        chest(25, Y + 1, 6, W, ui("stasis_pod_faint", Items.GLASS_BOTTLE, 6), ui("stasis_pod_volatile", Items.GLASS_BOTTLE, 4),
                ui("stasis_pod_severe", Items.GLASS_BOTTLE, 2), ui("scp096_bag", Items.LEATHER, 2), ui("scramble_goggles", Items.CARVED_PUMPKIN, 4),
                ui("rift_extractor", Items.SPYGLASS, 2), ui("rift_detector", Items.COMPASS, 2));
        plate(20, Y + 2, 9, S, "HAZMAT");
    }

    // ================================================================== 2: Medical

    private void medical() {
        int Y = y(2);
        air(1, Y + 1, 1, T - 1, Y + 4, T - 1);
        room(MEDICAL, 0, 0, 13, 8, Y, true);
        room(MEDICAL, 13, 0, 26, 8, Y, true);
        room(MEDICAL, 0, 14, 20, T, Y, true);
        room(MEDICAL, 20, 14, T, T, Y, true);
        corridor(MEDICAL, 0, T, Y);
        for (int x = 1; x < T; x++) if (x % 2 == 0) set(x, Y, 11, "medical_floor_guide[facing=east]");

        // ---- Ward: six bays on the south wall
        ezDoor(9, 14, Y, N);
        for (int x = 2; x <= 17; x += 3) {
            stackBlocks(x, Y + 1, 31, "medical_headwall", N, 3);
            put(x, Y + 1, 29, "examination_table", Blocks.WHITE_BED, S);
            set(x + 1, Y + 1, 31, "vitals_monitor[facing=north,lit=true]");
        }
        for (int x = 4; x <= 19; x += 3) tall(x, Y + 1, 29, "privacy_screen", N);
        for (int z = 17; z <= 23; z += 3) set(1, Y + 2, z, "medical_lightbox[facing=east,lit=true]");
        set(19, Y + 1, 16, "wheelchair[facing=west]");
        set(10, Y + 3, 13, "facility_sign_medical_small[facing=north]");

        // ---- Treatment
        ezDoor(25, 14, Y, N);
        put(26, Y + 1, 23, "examination_table", Blocks.WHITE_BED, E);
        stackBlocks(25, Y + 1, 23, "medical_exam_lamp", E, 3);
        set(28, Y + 1, 23, "vitals_monitor[facing=west,lit=true]");
        for (int z = 17; z <= 30; z += 2) set(31, Y + 1, z, "medical_supply_shelf[facing=west]");
        set(21, Y + 2, 20, "medical_defib_cabinet[facing=east]");
        set(21, Y + 1, 28, "biohazard_bin[facing=east]");
        set(21, Y + 1, 29, "medical_cabinet[facing=east,open=false]");
        set(21, Y + 1, 30, "medical_cabinet[facing=east,open=false]");
        plate(28, Y + 2, 13, N, "TREATMENT");

        // ---- Pharmacy
        ezDoor(6, 8, Y, S);
        for (int x = 1; x <= 12; x++) set(x, Y + 1, 1, "medical_supply_shelf[facing=south]");
        for (int x = 3; x <= 10; x++) set(x, Y + 1, 4, "medical_cabinet[facing=south,open=false]");
        chest(12, Y + 1, 6, W, new ItemStack(Unleashed.scp500(), 8), ui("scp3008_bandage", Items.PAPER, 32), ui("amnestic_canister", Items.MILK_BUCKET, 8),
                new ItemStack(Items.GOLDEN_APPLE, 16), ui("scp207_bottle", Items.HONEY_BOTTLE, 4));
        plate(4, Y + 2, 9, S, "PHARMACY");

        // ---- Medical reception
        ezDoor(19, 8, Y, S);
        for (int x = 15; x <= 23; x++) set(x, Y + 1, 3, "reception_desk[facing=north,left=" + (x > 15) + ",right=" + (x < 23) + ",shape=straight]");
        set(17, Y + 2, 3, "computer_terminal[facing=north,lit=true,screen=8]");
        set(21, Y + 2, 3, "computer_terminal[facing=north,lit=true,screen=8]");
        set(17, Y + 1, 2, "office_chair[facing=south]");
        set(21, Y + 1, 2, "office_chair[facing=south]");
        for (int x = 14; x <= 17; x++) set(x, Y + 1, 6, "ez_waiting_chairs[facing=north]");
        for (int x = 21; x <= 25; x++) set(x, Y + 1, 6, "ez_waiting_chairs[facing=north]");
        set(25, Y + 1, 1, "potted_plant[facing=south]");
        set(17, Y + 3, 9, "facility_sign_medical_small[facing=south]");
    }

    // ================================================================== 3: Security and MTF

    private void security() {
        int Y = y(3);
        air(1, Y + 1, 1, T - 1, Y + 4, T - 1);
        room(EZ, 0, 0, 13, 8, Y, true);
        room(OFFICE.floor("facility_carpet_blue"), 13, 0, 26, 8, Y, true);
        room(HCZP, 0, 14, 16, T, Y, true);
        room(LCZP, 16, 14, T, T, Y, true);
        corridor(EZ, 0, T, Y);

        // ---- Armory (Level 3)
        gate("hcz_room_door", 2, 2, 7, 14, Y, N, 3);
        for (int z = 16; z <= 30; z++) if (z % 4 != 0) tall(1, Y + 1, z, "locker", E);
        for (int x = 3; x <= 13; x += 2) stack(x, Y + 1, 31, "hcz_hazard_crate", N, 2);
        for (int z = 17; z <= 29; z += 3) set(15, Y + 1, z, "steel_shelf[facing=west]");
        chest(6, Y + 1, 24, S,
                ui("assault_rifle", Items.CROSSBOW, 4), ui("combat_shotgun", Items.CROSSBOW, 2), ui("pistol", Items.CROSSBOW, 4), ui("machine_gun", Items.CROSSBOW, 2),
                ui("rifle_rounds", Items.ARROW, 64), ui("rifle_rounds", Items.ARROW, 64), ui("handgun_rounds", Items.ARROW, 64), ui("shotgun_shells", Items.ARROW, 64),
                ui("frag_grenade", Items.TNT, 16), ui("flash_grenade", Items.SNOWBALL, 16), ui("gas_grenade", Items.SPLASH_POTION, 8), ui("incendiary_grenade", Items.FIRE_CHARGE, 8));
        chest(10, Y + 1, 24, S,
                ui("mtf_helmet", Items.IRON_HELMET, 8), ui("mtf_vest", Items.IRON_CHESTPLATE, 8), ui("mtf_signal_radio", Items.COMPASS, 2),
                ui("unstable_fragment_faint", Items.AMETHYST_SHARD, 8), ui("unstable_fragment_wavering", Items.AMETHYST_SHARD, 6), ui("unstable_fragment_volatile", Items.AMETHYST_SHARD, 4),
                ui("unstable_fragment_severe", Items.AMETHYST_SHARD, 2), ui("unstable_fragment_abyssal", Items.AMETHYST_SHARD, 1));
        for (int x = 6; x <= 10; x++) set(x, Y + 1, 25, "metal_desk[facing=north,left=" + (x > 6) + ",right=" + (x < 10) + "]");
        sign2("facility_sign_armory", 8, Y + 4, 13, N, "none", false);

        // ---- MTF barracks
        ezDoor(23, 14, Y, N);
        for (int x = 18; x <= 30; x += 3) { put(x, Y + 1, 30, "lcz_cell_bunk", Blocks.GRAY_BED, N); tall(x + 1, Y + 1, 31, "locker", N); }
        for (int x = 18; x <= 30; x += 3) put(x, Y + 1, 18, "lcz_cell_bunk", Blocks.GRAY_BED, S);
        table("cafeteria_table", "cafeteria_chair", 22, 23, 26, 24, Y, 2);
        plate(25, Y + 2, 13, N, "MTF", "BARRACKS");

        // ---- Security control
        ezDoor(6, 8, Y, S);
        desksX("metal_desk", 2, 11, 2, Y, S, "security_monitor[facing=$,lit=true,screen=9]");
        plate(4, Y + 2, 9, S, "SECURITY", "CONTROL");

        // ---- Briefing room
        ezDoor(19, 8, Y, S);
        table("conference_table", "office_chair", 16, 3, 22, 4, Y, 2);
        set(14, Y + 2, 3, "wall_monitor[facing=east,hanging=false,lit=true,screen=0]");
        set(14, Y + 2, 4, "wall_monitor[facing=east,hanging=false,lit=true,screen=1]");
        plate(17, Y + 2, 9, S, "BRIEFING");
    }

    // ================================================================== 4: Archive

    private void archive() {
        int Y = y(4);
        air(1, Y + 1, 1, T - 1, Y + 4, T - 1);
        Pal arch = OFFICE.floor("facility_carpet_blue");
        room(arch, 0, 0, 13, 8, Y, true);
        room(HCZP, 13, 0, 26, 8, Y, true);
        room(arch, 0, 14, T, T, Y, true);
        corridor(OFFICE, 0, T, Y);

        // ---- Archive hall: cabinet rows either side of a centre aisle, reading desks at the far end
        gate("glass_room_door", 2, 2, 15, 14, Y, N, 2);
        for (int[] row : new int[][]{{17, 1}, {18, 0}, {21, 1}, {22, 0}, {25, 1}, {26, 0}}) {
            Direction face = row[1] == 1 ? N : S;
            for (int x = 2; x <= 13; x++) stackBlocks(x, Y + 1, row[0], "office_records_cabinet", face, 3);
            for (int x = 19; x <= 30; x++) stackBlocks(x, Y + 1, row[0], "office_records_cabinet", face, 3);
        }
        desksX("office_desk", 3, 6, 30, Y, N, "desk_lamp[facing=$,lit=true]");
        desksX("office_desk", 26, 29, 30, Y, N, "desk_lamp[facing=$,lit=true]");
        put(15, Y + 1, 30, "archive_database_terminal", Blocks.LECTERN, N);
        put(17, Y + 1, 30, "archive", Blocks.LECTERN, N);
        plate(13, Y + 2, 13, N, "ARCHIVE", "Dossiers and", "records");

        // ---- Records office
        ezDoor(6, 8, Y, S);
        desksX("office_desk", 3, 5, 2, Y, S, "crt_monitor[facing=$,lit=true,screen=2]");
        desksX("office_desk", 8, 10, 2, Y, S, "crt_monitor[facing=$,lit=true,screen=3]");
        for (int z = 4; z <= 7; z++) set(1, Y + 1, z, "binder_shelf[facing=east]");
        for (int z = 4; z <= 7; z++) set(12, Y + 1, z, "filing_cabinet[facing=west,open=false]");
        chest(12, Y + 1, 2, W, ui("blank_personnel_file", Items.PAPER, 32), ui("sealed_document", Items.PAPER, 4));
        plate(4, Y + 2, 9, S, "RECORDS");

        // ---- Document vault (Level 4)
        gate("hcz_room_door", 2, 2, 19, 8, Y, S, 4);
        for (int x = 14; x <= 25; x++) if (x < 19 || x > 20) { set(x, Y + 1, 1, "steel_shelf[facing=south]"); set(x, Y + 2, 1, "steel_shelf[facing=south]"); }
        for (int x = 15; x <= 24; x += 3) set(x, Y + 1, 5, "filing_cabinet[facing=south,open=false]");
        plate(17, Y + 2, 9, S, "DOCUMENT", "VAULT", "Level 4");
    }

    // ================================================================== 5: Offices

    private void offices() {
        int Y = y(5);
        air(1, Y + 1, 1, T - 1, Y + 4, T - 1);
        room(OFFICE.floor("facility_carpet_blue"), 0, 0, 10, 8, Y, true);
        room(OFFICE, 10, 0, 18, 8, Y, true);
        room(OFFICE, 18, 0, 26, 8, Y, true);
        room(OFFICE, 0, 14, T, T, Y, true);
        corridor(OFFICE, 0, T, Y);

        // ---- open-plan offices: back-to-back desk clusters either side of a centre aisle
        ezDoor(15, 14, Y, N);
        for (int z0 : new int[]{18, 23, 28}) {
            for (int[] span : new int[][]{{3, 7}, {9, 13}, {19, 23}, {25, 29}}) {
                desksX("office_desk", span[0], span[1], z0, Y, N, "crt_monitor[facing=$,lit=true,screen=" + (z0 % 10) + "]");
                desksX("office_desk", span[0], span[1], z0 + 1, Y, S, "crt_monitor[facing=$,lit=true,screen=" + ((z0 + 3) % 10) + "]");
            }
        }
        for (int z : new int[]{17, 22, 27}) { set(1, Y + 1, z, "potted_plant[facing=east]"); set(T - 1, Y + 1, z, "potted_plant[facing=west]"); }
        set(1, Y + 3, 24, "office_wall_calendar[facing=east]");
        set(T - 1, Y + 3, 24, "wall_clock[facing=west]");
        tall(1, Y + 1, 15, "coat_rack", E);
        plate(13, Y + 2, 13, N, "DEPARTMENTS");

        // ---- Site Director (Level 4)
        gate("glass_room_door", 2, 2, 4, 8, Y, S, 4);
        for (int x = 3; x <= 7; x++) set(x, Y + 1, 3, "office_desk[facing=north,left=" + (x > 3) + ",right=" + (x < 7) + "]");
        set(4, Y + 2, 3, "crt_monitor[facing=north,lit=true,screen=1]");
        set(6, Y + 2, 3, "desk_lamp[facing=north,lit=true]");
        set(5, Y + 1, 2, "office_chair[facing=south]");
        set(4, Y + 1, 5, "office_chair[facing=north]");
        set(6, Y + 1, 5, "office_chair[facing=north]");
        for (int z = 2; z <= 6; z++) set(9, Y + 1, z, "binder_shelf[facing=west]");
        set(1, Y + 1, 6, "potted_plant[facing=east]");
        plate(2, Y + 2, 9, S, "SITE", "DIRECTOR");

        // ---- Conference
        ezDoor(13, 8, Y, S);
        table("conference_table", "office_chair", 12, 3, 16, 4, Y, 2);
        set(11, Y + 2, 3, "wall_monitor[facing=east,hanging=false,lit=true,screen=4]");
        plate(12, Y + 2, 9, S, "CONFERENCE");

        // ---- Break room
        ezDoor(21, 8, Y, S);
        couch("couch_blue", 19, 1, Y, 5, true, S);
        table("cafeteria_table", null, 21, 4, 22, 4, Y, 1);
        for (int x = 19; x <= 20; x++) set(x, Y + 1, 7, "scp3008_kitchen_base_cabinet[facing=north]");
        set(25, Y + 1, 1, "potted_plant[facing=south]");
        plate(24, Y + 2, 9, S, "BREAK ROOM");
    }

    // ================================================================== 6: Servers

    private void servers() {
        int Y = y(6);
        air(1, Y + 1, 1, T - 1, Y + 4, T - 1);
        Pal hall = POWER.floor("facility_metal_mesh");
        room(POWER, 0, 0, 13, 8, Y, true);
        room(POWER, 13, 0, 26, 8, Y, false);
        room(hall, 0, 14, T, T, Y, false);
        corridor(POWER, 0, T, Y);

        // ---- server hall: racks in rows, fronts facing cold aisles, cable trays above every row
        gate("hcz_room_door", 2, 2, 15, 14, Y, N, 3);
        int[][] rows = {{17, 0}, {19, 1}, {20, 0}, {22, 1}, {23, 0}, {25, 1}, {26, 0}, {28, 1}};
        for (int[] r : rows) {
            Direction face = r[1] == 0 ? S : N;
            for (int x = 2; x <= 13; x++) { tall(x, Y + 1, r[0], "server_rack", face); set(x, Y + 4, r[0], "cable_tray[orientation=down_x]"); }
            for (int x = 19; x <= 30; x++) { tall(x, Y + 1, r[0], "server_rack", face); set(x, Y + 4, r[0], "cable_tray[orientation=down_x]"); }
        }
        for (int z : new int[]{18, 21, 24, 27, 30}) for (int x = 4; x <= 30; x += 5) light(x, Y + 4, z, true);
        for (int z = 16; z <= 30; z += 4) light(16, Y + 4, z, false);
        set(16, Y + 3, 13, "facility_sign_server_room_small[facing=north]");

        // ---- network operations: desks facing the wall screens
        ezDoor(6, 8, Y, S);
        desksX("metal_desk", 2, 11, 3, Y, S, "computer_terminal[facing=$,lit=true,screen=4]");
        for (int x = 2; x <= 11; x += 2) set(x, Y + 2, 1, "wall_monitor[facing=south,hanging=false,lit=true,screen=" + (x % 10) + "]");
        plate(4, Y + 2, 9, S, "NETWORK", "OPERATIONS");

        // ---- communications: the site's network, powered
        buttoned("hcz_room_door", 2, 2, 19, 8, Y, S);
        put(15, Y + 1, 2, "network_hub", Blocks.LODESTONE, S);
        put(18, Y + 1, 2, "network_router", Blocks.OBSERVER, S);
        put(21, Y + 1, 2, "network_router", Blocks.OBSERVER, S);
        put(24, Y + 1, 2, "chat_server", Blocks.JUKEBOX, S);
        put(15, Y + 1, 6, "computer", Blocks.LECTERN, N);
        put(24, Y + 1, 6, "radio_booster_tower", Blocks.LIGHTNING_ROD, N);
        power(19, Y + 1, 5, new int[]{15, Y + 1, 2}, new int[]{18, Y + 1, 2}, new int[]{21, Y + 1, 2}, new int[]{24, Y + 1, 2}, new int[]{15, Y + 1, 6}, new int[]{24, Y + 1, 6});
        for (int x = 14; x <= 25; x++) set(x, Y + 4, 4, "cable_tray[orientation=down_x]");
        lights(13, 0, 26, 8, Y);
        chest(14, Y + 1, 4, E, ui("camera_tablet", Items.MAP, 2), ui("facility_tablet", Items.MAP, 2), ui("handheld_radio", Items.COMPASS, 4));
        plate(17, Y + 2, 9, S, "COMMS");
    }

    // ================================================================== 7: Launch control

    private void launchControl() {
        int Y = y(7);
        air(1, Y + 1, 1, T - 1, Y + 4, T - 1);
        room(RESEARCH, 0, 0, 13, 8, Y, true);
        room(OFFICE.floor("facility_carpet_blue"), 13, 0, 26, 8, Y, true);
        room(RESEARCH, 0, 14, T, T, Y, true);
        corridor(RESEARCH, 0, T, Y);

        // ---- mission control (Level 5): rows of consoles facing the south windows, the satellite terminal up front
        gate("glass_room_door", 2, 2, 15, 14, Y, N, 5);
        for (int z : new int[]{19, 23}) {
            desksX("metal_desk", 3, 13, z, Y, N, "crt_monitor[facing=$,lit=true,screen=" + (z % 10) + "]");
            desksX("metal_desk", 19, 29, z, Y, N, "crt_monitor[facing=$,lit=true,screen=" + ((z + 5) % 10) + "]");
        }
        put(16, Y + 1, 28, "satellite_terminal", Blocks.LECTERN, N);
        power(18, Y + 1, 29, new int[]{16, Y + 1, 28});
        set(16, Y + 1, 27, "office_chair[facing=south]");
        plate(13, Y + 2, 13, N, "MISSION", "CONTROL", "Level 5");

        // ---- orbital store
        ezDoor(6, 8, Y, S);
        for (int x = 1; x <= 12; x++) set(x, Y + 1, 1, "steel_shelf[facing=south]");
        chest(6, Y + 1, 4, S,
                ui("orbital_designator", Items.SPYGLASS, 2), ui("orbital_capacitor_ring", Items.IRON_INGOT, 1), ui("orbital_emitter_barrel", Items.IRON_INGOT, 1),
                ui("orbital_focusing_array", Items.IRON_INGOT, 1), ui("orbital_heat_sink", Items.IRON_INGOT, 1), ui("orbital_power_core", Items.IRON_INGOT, 1),
                ui("orbital_targeting_core", Items.IRON_INGOT, 1), ui("satellite", Items.ENDER_EYE, 2), ui("rocket", Items.FIREWORK_ROCKET, 1), ui("rocket_engine", Items.BLAZE_POWDER, 2),
                ui("heavy_fuel_bucket", Items.LAVA_BUCKET, 4));
        plate(4, Y + 2, 9, S, "ORBITAL", "STORE");

        // ---- observation lounge
        ezDoor(19, 8, Y, S);
        couch("couch_grey", 15, 1, Y, 4, true, S);
        couch("couch_grey", 21, 1, Y, 4, true, S);
        table("conference_table", null, 18, 4, 21, 4, Y, 1);
        set(14, Y + 1, 7, "potted_plant[facing=east]");
        set(25, Y + 1, 7, "potted_plant[facing=west]");
        plate(17, Y + 2, 9, S, "LOUNGE");
    }

    // ================================================================== 8: O5 Council

    private void council() {
        int Y = y(8);
        air(1, Y + 1, 1, T - 1, Y + 4, T - 1);
        Pal chamber = OFFICE.floor("facility_carpet_blue");
        room(OFFICE, 0, 0, 13, 8, Y, true);
        room(OFFICE, 13, 0, 26, 8, Y, true);
        room(chamber, 0, 8, T, T, Y, true);

        // the council table: a hollow ring with thirteen seats, a monitor at each
        int x0 = 9, x1 = 23, z0 = 17, z1 = 23;
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            boolean ring = x == x0 || x == x1 || z == z0 || z == z1;
            if (!ring) continue;
            boolean nIn = z > z0 && (x == x0 || x == x1), sIn = z < z1 && (x == x0 || x == x1);
            boolean wIn = x > x0 && (z == z0 || z == z1), eIn = x < x1 && (z == z0 || z == z1);
            set(x, Y + 1, z, "conference_table[north=" + nIn + ",south=" + sIn + ",west=" + wIn + ",east=" + eIn + "]");
        }
        int seat = 0;
        for (int x = x0 + 1; x <= x1 - 1 && seat < 13; x += 2) {
            set(x, Y + 1, z0 - 1, "office_chair[facing=south]");
            set(x, Y + 2, z0, "crt_monitor[facing=north,lit=true,screen=" + (seat % 10) + "]");
            seat++;
        }
        for (int x = x0 + 1; x <= x1 - 1 && seat < 13; x += 2) {
            set(x, Y + 1, z1 + 1, "office_chair[facing=north]");
            set(x, Y + 2, z1, "crt_monitor[facing=south,lit=true,screen=" + (seat % 10) + "]");
            seat++;
        }
        set(x1 + 1, Y + 1, (z0 + z1) / 2, "office_chair[facing=west]");
        scpSign(16, Y + 3, 9, S);
        plate(15, Y + 2, 9, S, "THE O5", "COUNCIL", "Secure. Contain.", "Protect.");
        for (int[] pp : new int[][]{{1, 9}, {T - 1, 9}, {1, T - 1}, {T - 1, T - 1}}) set(pp[0], Y + 1, pp[1], "ez_planter[facing=south]");
        for (int x = 4; x <= 28; x += 8) set(x, Y + 3, T - 1, "wall_clock[facing=north]");

        // ---- council archive and lounge
        ezDoor(6, 8, Y, S);
        for (int x = 1; x <= 12; x++) { set(x, Y + 1, 1, "binder_shelf[facing=south]"); set(x, Y + 2, 1, "binder_shelf[facing=south]"); }
        put(4, Y + 1, 5, "archive_database_terminal", Blocks.LECTERN, E);
        plate(4, Y + 2, 9, S, "COUNCIL", "ARCHIVE");
        ezDoor(19, 8, Y, S);
        couch("couch_leather", 15, 1, Y, 5, true, S);
        couch("couch_leather", 21, 1, Y, 4, true, S);
        set(14, Y + 1, 7, "potted_plant[facing=east]");
        plate(17, Y + 2, 9, S, "LOUNGE");
    }

    // ================================================================== 9: Administrator

    private void administrator() {
        int Y = y(9);
        air(1, Y + 1, 1, T - 1, Y + 4, T - 1);
        Pal office = OFFICE.floor("facility_carpet_blue");
        room(office, 0, 0, 13, 8, Y, true);
        room(OFFICE, 13, 0, 26, 8, Y, true);
        room(office, 0, 8, T, T, Y, true);

        // the desk: the Administrator sits with the city at their back, facing the door
        for (int x = 13; x <= 19; x++) set(x, Y + 1, 25, "office_desk[facing=south,left=" + (x > 13) + ",right=" + (x < 19) + "]");
        put(15, Y + 2, 25, "computer", Blocks.AIR, S);
        set(14, Y + 2, 25, "crt_monitor[facing=south,lit=true,screen=0]");
        set(17, Y + 2, 25, "paper_stack[facing=south,variant=2]");
        set(18, Y + 2, 25, "desk_lamp[facing=south,lit=true]");
        set(16, Y + 1, 26, "office_chair[facing=north]");
        set(15, Y + 1, 23, "office_chair[facing=south]");
        set(17, Y + 1, 23, "office_chair[facing=south]");
        chest(20, Y + 1, 26, W,
                ui("keycard_omni", Items.PAPER, 2), ui("orbital_designator", Items.SPYGLASS, 1), ui("stasis_pod_abyssal", Items.GLASS_BOTTLE, 3),
                ui("mtf_signal_radio", Items.COMPASS, 1), ui("rift_detector", Items.COMPASS, 1), ui("camera_tablet", Items.MAP, 1), ui("facility_tablet", Items.MAP, 1),
                new ItemStack(Unleashed.scp500(), 8), ui("creative_outlet", Items.REDSTONE_BLOCK, 8), ui("creative_wire", Items.STRING, 32),
                new ItemStack(ModItems.PHONE.get(), 1));
        // the wall behind the door: the Foundation's emblem and a name plate
        scpSign(16, Y + 3, 9, S);
        plate(15, Y + 2, 9, S, "THE", "ADMINISTRATOR");
        // library along the west wall, seating along the east
        for (int z = 11; z <= 30; z++) if (z != 20) { set(1, Y + 1, z, "binder_shelf[facing=east]"); set(1, Y + 2, z, "binder_shelf[facing=east]"); }
        put(3, Y + 1, 20, "archive_database_terminal", Blocks.LECTERN, E);
        couch("couch_leather", T - 1, 12, Y, 5, false, W);
        couch("couch_leather", 25, 18, Y, 5, true, N);
        table("conference_table", null, 27, 13, 29, 15, Y, 1);
        for (int[] pp : new int[][]{{2, 9 + 1}, {T - 1, 30}, {12, 30}, {20, 30}}) set(pp[0], Y + 1, pp[1], "ez_planter[facing=north]");
        for (int x = 6; x <= 26; x += 20) set(x, Y + 3, 9, "wall_clock[facing=south]");

        // private quarters
        ezDoor(6, 8, Y, S);
        Placer.place(l, at(3, Y + 1, 2), Blocks.WHITE_BED, S);
        set(1, Y + 1, 1, "potted_plant[facing=east]");
        tall(5, Y + 1, 1, "coat_rack", S);
        couch("couch_grey", 8, 1, Y, 4, true, S);
        set(12, Y + 1, 6, "scp3008_living_side_table[facing=west]");
        plate(4, Y + 2, 9, S, "PRIVATE");
        // private archive
        ezDoor(19, 8, Y, S);
        for (int x = 14; x <= 25; x++) { set(x, Y + 1, 1, "binder_shelf[facing=south]"); set(x, Y + 2, 1, "binder_shelf[facing=south]"); }
        desksX("office_desk", 16, 18, 4, Y, S, "desk_lamp[facing=$,lit=true]");
        plate(17, Y + 2, 9, S, "PRIVATE", "ARCHIVE");
    }

    // ================================================================== facade

    /** The outer skin of one storey: concrete piers every 4 blocks, glass between, spandrel bands. */
    private void facade(int s) {
        int Y = y(s);
        boolean full = s == 0 || s == TOP;   // the lobby and the top floor are glazed full height
        for (int i = 0; i <= T; i++) {
            ring(i, 0, Y, full);
            ring(i, T, Y, full);
            ring(0, i, Y, full);
            ring(T, i, Y, full);
        }
        // the floor edge
        for (int i = 0; i <= T; i++) { set(i, Y, 0, "facility_precast_concrete"); set(i, Y, T, "facility_precast_concrete"); set(0, Y, i, "facility_precast_concrete"); set(T, Y, i, "facility_precast_concrete"); }
        if (s == 0) {
            door("ez_door", 2, 3, 14, T, Y, S);
            door("ez_door", 2, 3, 17, T, Y, S);
            fill(13, Y + 4, T, 19, Y + 5, T, "facility_precast_concrete");
            BlockPos p = at(15, Y + 4, T + 1);
            l.setBlock(p, st("facility_sign_gate_a_header[facing=south,mount=wall,part=left]"), FLAGS);
            l.setBlock(p.relative(E), st("facility_sign_gate_a_header[facing=south,mount=wall,part=centre]"), FLAGS);
            l.setBlock(p.relative(E, 2), st("facility_sign_gate_a_header[facing=south,mount=wall,part=right]"), FLAGS);
            // canopy over the entrance
            fill(12, Y + 5, T + 1, 20, Y + 5, T + 3, "facility_precast_concrete");
            for (int x = 13; x <= 19; x += 3) light(x, Y + 4, T + 2, true);
        }
    }

    private void ring(int x, int z, int Y, boolean full) {
        int along = (x == 0 || x == T) ? z : x;
        boolean pier = along % 4 == 0;
        for (int yy = Y + 1; yy <= Y + 5; yy++) {
            boolean glass = !pier && (full ? yy <= Y + 4 : yy >= Y + 2 && yy <= Y + 4);
            set(x, yy, z, glass ? "reinforced_glass" : pier ? "facility_precast_concrete" : "facility_painted_concrete");
        }
    }

    // ================================================================== roof

    private void roof() {
        int Y = y(ROOF);
        fill(0, Y, 0, T, Y, T, "facility_precast_concrete");
        fill(1, Y, 1, T - 1, Y, T - 1, "power_floor");
        for (int i = 0; i <= T; i++) {
            set(i, Y + 1, 0, "facility_precast_concrete"); set(i, Y + 1, T, "facility_precast_concrete");
            set(0, Y + 1, i, "facility_precast_concrete"); set(T, Y + 1, i, "facility_precast_concrete");
        }
        for (int x = 2; x < T - 1; x++) set(x, Y, 12, "power_floor_marked[facing=north]");
        set(16, Y + 1, 12, "facility_precast_concrete");
        set(16, Y + 2, 12, "facility_precast_concrete");
        plate(16, Y + 2, 13, S, "LAUNCH DECK", "Satellite: west", "Rocket: east", "Fuelled.");
        boolean sat = UnleashedApi.placeLaunchPlatform(l, at(8, Y + 1, 21), "satellite_platform", S);
        boolean rocket = UnleashedApi.placeLaunchPlatform(l, at(23, Y + 1, 21), "rocket_platform", S);
        set(8, Y + 1, 30, "creative_outlet");
        set(23, Y + 1, 30, "creative_outlet");
        UnleashedApi.wire(l, at(8, Y + 1, 30), at(8, Y + 1, 21));
        UnleashedApi.wire(l, at(23, Y + 1, 30), at(23, Y + 1, 21));
        chest(16, Y + 1, 30, N,
                ui("rocket", Items.FIREWORK_ROCKET, 1), ui("satellite", Items.ENDER_EYE, 2), ui("heavy_fuel_bucket", Items.LAVA_BUCKET, 8),
                ui("rocket_platform", Items.IRON_BLOCK, rocket ? 0 : 1), ui("satellite_platform", Items.IRON_BLOCK, sat ? 0 : 1),
                ui("creative_wire", Items.STRING, 32), ui("portable_launch_pad", Items.IRON_BLOCK, 1));
    }

    // ================================================================== stairwell

    /**
     * One stairwell from B2 to the roof in the north-east corner (x 26..32, z 0..8): two flights of
     * three steps per storey around a centre wall, a landing at every floor with a door onto the
     * corridor (the roof door opens onto the deck). Floors 8 and 9 are keyed at Level 5.
     */
    private void stairwell() {
        int bottom = y(LOWEST), top = y(ROOF);
        // shaft walls
        for (int yy = bottom; yy <= top + 5; yy++) {
            for (int x = 26; x <= T; x++) { set(x, yy, 0, "facility_painted_concrete"); set(x, yy, 8, "facility_painted_concrete"); }
            for (int z = 0; z <= 8; z++) { set(26, yy, z, "facility_painted_concrete"); set(T, yy, z, "facility_painted_concrete"); }
        }
        for (int s = 0; s <= TOP; s++) for (int yy = y(s) + 2; yy <= y(s) + 4; yy++) for (int z = 2; z <= 6; z += 2) set(T, yy, z, "reinforced_glass");
        air(27, bottom + 1, 1, 31, top + 4, 7);
        fill(27, bottom, 1, 31, bottom, 7, "facility_concrete");
        fill(26, top + 5, 0, T, top + 5, 8, "facility_precast_concrete");
        for (int s = LOWEST; s <= ROOF; s++) {
            int Y = y(s);
            // floor landing (v 0..1 -> z 7..6) and the arrival step from below (u 3..4, v 2 -> x 30..31, z 5)
            fill(27, Y, 6, 31, Y, 7, "facility_concrete");
            fill(30, Y, 5, 31, Y, 5, "facility_concrete");
            set(28, Y + (s == ROOF ? 4 : 5), 7, "fluorescent_light[face=ceiling,facing=east,lit=true]");
            // the floor's own sign beside the door
            String label = switch (s) {
                case -4 -> "B4  DEEP CONT."; case -3 -> "B3  KETER WING";
                case -2 -> "B2  HEAVY CONT."; case -1 -> "B1  LIGHT CONT."; case 0 -> "G  ENTRANCE";
                case 1 -> "1  RESEARCH"; case 2 -> "2  MEDICAL"; case 3 -> "3  SECURITY"; case 4 -> "4  ARCHIVE";
                case 5 -> "5  OFFICES"; case 6 -> "6  SERVERS"; case 7 -> "7  LAUNCH CTRL"; case 8 -> "8  O5 COUNCIL";
                case 9 -> "9  ADMINISTRATOR"; default -> "ROOF";
            };
            plate(30, Y + 2, 7, N, "FLOOR", label);
            set(27, Y + 3, 9, "facility_sign_stairs_small[facing=south]");
            if (s < ROOF) {
                // flight 1 up the west side (x 27..28) toward the north
                for (int k = 1; k <= 3; k++) fill(27, Y + k, 6 - k, 28, Y + k, 6 - k, "facility_concrete_stairs[facing=north,half=bottom,shape=straight]");
                // mid landing (z 1..2) at Y+3
                fill(27, Y + 3, 1, 31, Y + 3, 2, "facility_concrete");
                // flight 2 back down the east side (x 30..31) toward the south
                fill(30, Y + 4, 3, 31, Y + 4, 3, "facility_concrete_stairs[facing=south,half=bottom,shape=straight]");
                fill(30, Y + 5, 4, 31, Y + 5, 4, "facility_concrete_stairs[facing=south,half=bottom,shape=straight]");
                // centre wall between the flights
                for (int yy = Y + 1; yy <= Y + 5; yy++) for (int z = 3; z <= 5; z++) set(29, yy, z, "facility_painted_concrete");
            }
            // the door onto the floor
            if (s == ROOF) door("ez_room_door", 2, 2, 28, 8, Y, S);
            else if (s >= 8) gate("glass_room_door", 2, 2, 28, 8, Y, N, 5);
            else ezDoor(28, 8, Y, S);
        }
    }
}
