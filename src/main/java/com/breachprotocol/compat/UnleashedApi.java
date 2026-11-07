package com.breachprotocol.compat;

import com.breachprotocol.BreachProtocol;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Calls into SCP Unleashed's public methods at runtime. Nothing from SCP Unleashed is compiled
 * into or shipped with Breach Protocol; if a method isn't there (a different SCP Unleashed
 * version), the call reports failure instead of crashing.
 */
public final class UnleashedApi {
    private UnleashedApi() {}

    private static final String PKG = "net.rai.scp_unleashed.";
    private static final Map<String, Object> CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Object MISSING = new Object();

    public static Class<?> cls(String name) {
        String key = "c:" + name;
        Object c = CACHE.get(key);
        if (c == null) {
            try {
                c = Class.forName(PKG + name);
            } catch (Throwable t) {
                BreachProtocol.LOGGER.warn("SCP Unleashed class {} not found - feature disabled", name);
                c = MISSING;
            }
            CACHE.put(key, c);
        }
        return c == MISSING ? null : (Class<?>) c;
    }

    public static Method method(String clsName, String name, Class<?>... args) {
        String key = "m:" + clsName + "#" + name + java.util.Arrays.toString(args);
        Object m = CACHE.get(key);
        if (m == null) {
            Class<?> c = cls(clsName);
            m = MISSING;
            if (c != null) {
                try {
                    m = c.getMethod(name, args);
                } catch (Throwable t) {
                    BreachProtocol.LOGGER.warn("SCP Unleashed method {}.{} not found - feature disabled", clsName, name);
                }
            }
            CACHE.put(key, m);
        }
        return m == MISSING ? null : (Method) m;
    }

    public static Object call(Method m, Object target, Object... args) {
        if (m == null) return null;
        try {
            return m.invoke(target, args);
        } catch (Throwable t) {
            BreachProtocol.LOGGER.warn("SCP Unleashed call {} failed: {}", m.getName(), t.toString());
            return null;
        }
    }

    // ------------------------------------------------------------------ SCP-096

    public static boolean isShyGuy(Entity e) {
        Class<?> c = cls("scp096.Scp096ShyGuy");
        return c != null && c.isInstance(e);
    }

    /** SCP-096's face position and the direction it faces (Scp096Sight), or null. */
    public static Vec3 faceOf(Entity shyGuy) {
        return isShyGuy(shyGuy) ? (Vec3) call(method("scp096.Scp096Sight", "faceOf", cls("scp096.Scp096ShyGuy")), null, shyGuy) : null;
    }

    public static Vec3 facingOf(Entity shyGuy) {
        return isShyGuy(shyGuy) ? (Vec3) call(method("scp096.Scp096Sight", "facingOf", cls("scp096.Scp096ShyGuy")), null, shyGuy) : null;
    }

    /** Scp096Sight.clear: nothing blocks the line between two points. */
    public static boolean clearSight(ServerLevel l, Vec3 a, Vec3 b) {
        Object r = call(method("scp096.Scp096Sight", "clear", ServerLevel.class, Vec3.class, Vec3.class), null, l, a, b);
        return r == null ? l.clip(new net.minecraft.world.level.ClipContext(a, b, net.minecraft.world.level.ClipContext.Block.VISUAL, net.minecraft.world.level.ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty())).getType() == net.minecraft.world.phys.HitResult.Type.MISS : (Boolean) r;
    }

    /** The player saw 096's face (here: through the phone camera). SCP Unleashed decides what happens. */
    public static boolean reportSighting(Entity shyGuy, ServerPlayer p) {
        Object r = call(method("scp096.Scp096ShyGuy", "reportSighting", ServerLevel.class, ServerPlayer.class), shyGuy, p.serverLevel(), p);
        return r instanceof Boolean b && b;
    }

    /** SCP Unleashed's own "looked at a photo of 096's face" handling (Scp105Photo096Sight.view). */
    public static boolean viewFacePhoto(ServerPlayer p) {
        Method m = method("scp105.photo096.Scp105Photo096Sight", "view", ServerPlayer.class);
        if (m == null) return false;
        call(m, null, p);
        return true;
    }

    /** A physical copy: SCP Unleashed's Face-Down Photograph. */
    public static ItemStack facePhotoItem() {
        Object r = call(method("scp105.photo096.Scp105Photo096Item", "create"), null);
        return r instanceof ItemStack s ? s : ItemStack.EMPTY;
    }

    public static boolean isFacePhoto(ItemStack s) {
        Object r = call(method("scp105.photo096.Scp105Photo096Item", "is", ItemStack.class), null, s);
        return r instanceof Boolean b && b;
    }

    public static boolean isBagged(Entity shyGuy) {
        Object r = call(method("scp096.Scp096ShyGuy", "isBagged"), shyGuy);
        return r instanceof Boolean b && b;
    }

    // ------------------------------------------------------------------ MTF

    /** MtfTier names in order. Abyssal is the Alpha-1 Detachment. */
    public static final String[] MTF_TIERS = {"Faint", "Wavering", "Volatile", "Severe", "Abyssal"};

    /** Translation key of a tier's squad name, e.g. "mtf.scp_unleashed.tier.fireteam". */
    public static String tierNameKey(String tierName) {
        Object t = tier(tierName);
        Object k = t == null ? null : call(method("mtf.MtfTier", "key"), t);
        return k == null ? "" : "mtf.scp_unleashed.tier." + k;
    }

    public static int tierColour(String tierName) {
        Object t = tier(tierName);
        Object c = t == null ? null : call(method("mtf.MtfTier", "colour"), t);
        return c instanceof Integer i ? i : 0xFFFFFF;
    }

    /** Which tier an unstable fragment calls (MtfTier.fromFragment), or null. */
    public static String tierOfFragment(ItemStack s) {
        if (s.isEmpty()) return null;
        Object t = call(method("mtf.MtfTier", "fromFragment", ItemStack.class), null, s);
        return t == null ? null : ((Enum<?>) t).name();
    }

    public static boolean mtfBusy(ServerPlayer p) {
        Object r = call(method("mtf.MtfMissions", "busy", java.util.UUID.class), null, p.getUUID());
        return r instanceof Boolean b && b;
    }

    /** Sends a squad straight away, no fragment (MtfMissions.deployNow). Council privilege. */
    public static String deployMtfNow(ServerPlayer p, String tierName) {
        Object tier = tier(tierName);
        Class<?> tierCls = cls("mtf.MtfTier");
        if (tier == null || tierCls == null) return null;
        Object out = call(method("mtf.MtfMissions", "deployNow", ServerPlayer.class, tierCls), null, p, tier);
        return out == null ? null : out.toString();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object tier(String name) {
        Class<?> c = cls("mtf.MtfTier");
        if (c == null || !c.isEnum()) return null;
        try {
            return Enum.valueOf((Class) c, name);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Calls a Mobile Task Force squad by helicopter. Returns their outcome name, or null. */
    public static String callMtf(ServerPlayer p, String tierName) {
        Object tier = tier(tierName);
        Class<?> tierCls = cls("mtf.MtfTier");
        if (tier == null || tierCls == null) return null;
        Object out = call(method("mtf.MtfMissions", "call", ServerPlayer.class, tierCls), null, p, tier);
        return out == null ? null : out.toString();
    }

    public static long mtfCooldown(ServerPlayer p) {
        Object r = call(method("mtf.MtfMissions", "cooldownLeft", ServerPlayer.class), null, p);
        return r instanceof Long l ? l : 0L;
    }

    public static boolean mtfReport(ServerPlayer p) {
        Method m = method("mtf.MtfMissions", "report", ServerPlayer.class);
        call(m, null, p);
        return m != null;
    }

    public static boolean mtfExtract(ServerPlayer p) {
        Object r = call(method("mtf.MtfMissions", "requestExtraction", ServerPlayer.class), null, p);
        return r instanceof Boolean b && b;
    }

    // ------------------------------------------------------------------ orbital array

    private static Object orbitalArray(MinecraftServer server) {
        return call(method("orbital.OrbitalArray", "get", MinecraftServer.class), null, server);
    }

    /** {parts installed, 1 if complete, seconds until ready}, or null when unavailable. */
    public static int[] orbitalStatus(ServerPlayer p) {
        Object arr = orbitalArray(p.server);
        if (arr == null) return null;
        Object count = call(method("orbital.OrbitalArray", "count", java.util.UUID.class), arr, p.getUUID());
        Object complete = call(method("orbital.OrbitalArray", "complete", java.util.UUID.class), arr, p.getUUID());
        Object readyAt = call(method("orbital.OrbitalArray", "readyAt", java.util.UUID.class), arr, p.getUUID());
        long now = p.server.overworld().getGameTime();
        // readyAt is Long.MIN_VALUE before the first shot: that's "ready now" (and must not overflow)
        int wait = readyAt instanceof Long r && r > now ? (int) Math.min(Integer.MAX_VALUE, (r - now + 19) / 20) : 0;
        return new int[]{count instanceof Integer i ? i : 0, complete instanceof Boolean b && b ? 1 : 0, wait};
    }

    /**
     * Fires the player's orbital array exactly as SCP Unleashed's ZENITH Designator does
     * (OrbitalFiring.use): array check, recharge, dimension, buried targets, cooldown, messages.
     * The phone presents a designator linked to the player. {@code marked} null = aim at the crosshair.
     * Returns SCP Unleashed's outcome name ("Fired", "Recharging", ...) or null if unavailable.
     */
    public static String orbitalFire(ServerPlayer p, @org.jetbrains.annotations.Nullable BlockPos marked) {
        try {
            Class<?> reg = cls("orbital.OrbitalRegistry"), link = cls("orbital.OrbitalLink");
            if (reg == null || link == null) return null;
            Object designatorHolder = reg.getField("Designator").get(null);
            Object linkHolder = reg.getField("Link").get(null);
            net.minecraft.world.item.Item item = (net.minecraft.world.item.Item) ((java.util.function.Supplier<?>) designatorHolder).get();
            @SuppressWarnings("unchecked")
            net.minecraft.core.component.DataComponentType<Object> type = (net.minecraft.core.component.DataComponentType<Object>) ((java.util.function.Supplier<?>) linkHolder).get();
            Object linkValue = link.getConstructor(java.util.UUID.class, String.class).newInstance(p.getUUID(), p.getGameProfile().getName());
            ItemStack designator = new ItemStack(item);
            designator.set(type, linkValue);
            Object out = marked == null
                    ? call(method("orbital.OrbitalFiring", "use", ServerPlayer.class, ItemStack.class), null, p, designator)
                    : call(method("orbital.OrbitalFiring", "use", ServerPlayer.class, ItemStack.class, BlockPos.class), null, p, designator, marked);
            return out == null ? null : out.toString();
        } catch (Throwable t) {
            BreachProtocol.LOGGER.warn("SCP Unleashed orbital firing unavailable: {}", t.toString());
            return null;
        }
    }

    // ------------------------------------------------------------------ stasis pods

    /** Something SCP Unleashed's stasis pod captured: (level, player, creature). */
    public interface StasisListener {
        void captured(ServerLevel level, net.minecraft.world.entity.player.Player user, net.minecraft.world.entity.Mob target);
    }

    /**
     * Registers with SCP Unleashed's public addon hook, StasisPods.addCaptureHook, so we hear the
     * moment a creature is sealed in a stasis pod. Returns false if the hook isn't there.
     */
    public static boolean onStasisCapture(StasisListener listener) {
        Class<?> hook = cls("stasis.StasisPods$CaptureHook");
        Method add = hook == null ? null : method("stasis.StasisPods", "addCaptureHook", hook);
        if (add == null) return false;
        Object proxy = java.lang.reflect.Proxy.newProxyInstance(hook.getClassLoader(), new Class<?>[]{hook}, (obj, m, args) -> {
            if (m.getName().equals("captured") && args != null && args.length >= 3
                    && args[0] instanceof ServerLevel l && args[1] instanceof net.minecraft.world.entity.player.Player u && args[2] instanceof net.minecraft.world.entity.Mob t) {
                try {
                    listener.captured(l, u, t);
                } catch (Throwable ex) {
                    BreachProtocol.LOGGER.error("Stasis capture handling failed", ex);
                }
                return null;
            }
            return switch (m.getName()) {
                case "hashCode" -> System.identityHashCode(obj);
                case "equals" -> obj == args[0];
                case "toString" -> "BreachProtocolStasisHook";
                default -> null;
            };
        });
        call(add, null, proxy);
        return true;
    }

    /**
     * Would SCP Unleashed's stasis pod take this creature right now? Mirrors the checks in
     * StasisPods.refusal that don't depend on the pod: addon/creature vetoes (SCP-001 until it's
     * locked, a full-grown SCP-682, ...), the stasis_immune tag, multipart and unsaveable entities.
     */
    public static boolean stasisCanHold(Entity e) {
        if (!(e instanceof net.minecraft.world.entity.Mob) || !e.isAlive()) return false;
        Object vetoed = call(method("stasis.StasisPods", "isVetoed", Entity.class), null, e);
        if (vetoed instanceof Boolean b && b) return false;
        if (e.getType().is(Unleashed.STASIS_IMMUNE) || e.isMultipartEntity() || !e.getType().canSerialize()) return false;
        return true;
    }

    /** Entity type id sealed inside an SCP Unleashed stasis pod stack, or null if it isn't an occupied pod. */
    public static String podContents(ItemStack s) {
        if (s.isEmpty()) return null;
        Object held = call(method("stasis.StasisPodItem", "contents", ItemStack.class), null, s);
        Object data = held == null ? null : call(method("stasis.StasisContents", "copyData"), held);
        return data instanceof net.minecraft.nbt.CompoundTag t && t.contains("id") ? t.getString("id") : null;
    }

    /** The anomaly dossier an Anomaly Dossier item holds (its language key), or null. */
    public static String dossierOf(ItemStack s) {
        Object doc = call(method("archive.AnomalyDocumentItem", "lookup", ItemStack.class), null, s);
        Object lang = doc == null ? null : call(method("archive.document.AnomalyDocument", "Lang"), doc);
        return lang == null ? null : lang.toString();
    }

    // ------------------------------------------------------------------ power wiring

    /**
     * Strings a creative-tier wire between two SCP Unleashed outlets/machines, the same way their
     * wire coil does (WireCoilItem: a matched WireLink on each end). Returns false if either end
     * isn't an outlet, has no free link, or the classes aren't there.
     */
    public static boolean wire(ServerLevel l, BlockPos a, BlockPos b) {
        try {
            Class<?> outlet = cls("block.entity.OutletBlockEntity"), linkCls = cls("wire.WireLink"), tierCls = cls("wire.WireTier");
            if (outlet == null || linkCls == null || tierCls == null) return false;
            Object A = l.getBlockEntity(a), B = l.getBlockEntity(b);
            if (!outlet.isInstance(A) || !outlet.isInstance(B)) return false;
            Method free = outlet.getMethod("hasFreeLink"), full = outlet.getMethod("isFullBlock"), facing = outlet.getMethod("getFacing"),
                    links = outlet.getMethod("getLinks"), linked = outlet.getMethod("isLinkedTo", BlockPos.class), add = outlet.getMethod("addLink", linkCls);
            if ((Boolean) linked.invoke(A, b)) return true;
            if (!(Boolean) free.invoke(A) || !(Boolean) free.invoke(B)) return false;
            @SuppressWarnings({"unchecked", "rawtypes"}) Object tier = Enum.valueOf((Class) tierCls, "Creative");
            net.minecraft.core.Direction ab = net.minecraft.core.Direction.getNearest(b.getX() - a.getX(), b.getY() - a.getY(), b.getZ() - a.getZ());
            boolean fa = (Boolean) full.invoke(A), fb = (Boolean) full.invoke(B);
            net.minecraft.core.Direction faceA = fa ? ab : (net.minecraft.core.Direction) facing.invoke(A);
            net.minecraft.core.Direction faceB = fb ? ab.getOpposite() : (net.minecraft.core.Direction) facing.invoke(B);
            int slot = (Integer) linkCls.getMethod("freeSlot", java.util.Collection.class, java.util.Collection.class).invoke(null, links.invoke(A), links.invoke(B));
            java.lang.reflect.Constructor<?> ctor = linkCls.getConstructor(BlockPos.class, net.minecraft.core.Direction.class, int.class, tierCls, boolean.class, net.minecraft.core.Direction.class);
            add.invoke(A, ctor.newInstance(b.immutable(), faceB, slot, tier, fb, faceA));
            add.invoke(B, ctor.newInstance(a.immutable(), faceA, slot, tier, fa, faceB));
            return true;
        } catch (Throwable t) {
            BreachProtocol.LOGGER.warn("Could not wire {} to {}: {}", a, b, t.toString());
            return false;
        }
    }

    /**
     * Places an SCP Unleashed launch platform (a multi-block) the way their item does: the placed cell,
     * then setPlacedBy fills in the rest. Then fills its tank with their Heavy Fuel. Returns false on failure.
     */
    public static boolean placeLaunchPlatform(ServerLevel l, BlockPos pos, String id, net.minecraft.core.Direction facing) {
        try {
            net.minecraft.world.level.block.Block block = Unleashed.block(id, net.minecraft.world.level.block.Blocks.AIR);
            if (block == net.minecraft.world.level.block.Blocks.AIR) return false;
            Object layout = block.getClass().getMethod("layout").invoke(block);
            int placePart = (Integer) layout.getClass().getMethod("getPlacePart").invoke(layout);
            net.minecraft.world.level.block.state.properties.IntegerProperty part =
                    (net.minecraft.world.level.block.state.properties.IntegerProperty) block.getClass().getMethod("partProperty").invoke(block);
            net.minecraft.world.level.block.state.BlockState st = block.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, facing).setValue(part, placePart);
            l.setBlock(pos, st, net.minecraft.world.level.block.Block.UPDATE_ALL);
            block.setPlacedBy(l, pos, st, null, ItemStack.EMPTY);
            // fill the master's tank with Heavy Fuel
            BlockPos master = (BlockPos) layout.getClass().getMethod("masterFromPlaced", BlockPos.class, net.minecraft.core.Direction.class).invoke(layout, pos, facing);
            Object be = l.getBlockEntity(master);
            Class<?> oil = cls("oil.OilRegistry");
            if (be != null && oil != null) {
                Object heavy = oil.getField("HeavyFuel").get(null);
                Object tank = be.getClass().getMethod("getFuel").invoke(be);
                if (tank instanceof net.neoforged.neoforge.fluids.capability.templates.FluidTank ft) {
                    net.neoforged.neoforge.fluids.FluidStack fuel = (net.neoforged.neoforge.fluids.FluidStack) heavy.getClass().getMethod("stack", int.class).invoke(heavy, ft.getCapacity());
                    ft.fill(fuel, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
                    ((net.minecraft.world.level.block.entity.BlockEntity) be).setChanged();
                }
            }
            return true;
        } catch (Throwable t) {
            BreachProtocol.LOGGER.warn("Could not place launch platform {}: {}", id, t.toString());
            return false;
        }
    }

    // ------------------------------------------------------------------ rifts

    /** SCP Unleashed rifts (anything implementing anomaly.Extractable). */
    public static boolean isRift(Entity e) {
        Class<?> c = cls("anomaly.Extractable");
        return c != null && c.isInstance(e);
    }

    /** "faint", "wavering", ... (Extractable.getRiftLabel). */
    public static String riftLabel(Entity e) {
        Object r = call(method("anomaly.Extractable", "getRiftLabel"), e);
        return r == null ? "unknown" : r.toString();
    }

    public static int riftColour(Entity e) {
        Object r = call(method("anomaly.Extractable", "getRiftColour"), e);
        return r instanceof Integer i ? i : 0xB070FF;
    }

    /** Extraction progress 0..1 (Extractable.getExtraction). */
    public static float riftExtraction(Entity e) {
        Object r = call(method("anomaly.Extractable", "getExtraction"), e);
        return r instanceof Float f ? f : 0F;
    }

    /** What a finished extraction leaves behind (Extractable.createFragment), then the rift closes (collapse). */
    public static ItemStack extractRift(Entity e) {
        Object frag = call(method("anomaly.Extractable", "createFragment"), e);
        call(method("anomaly.Extractable", "collapse"), e);
        return frag instanceof ItemStack s ? s : ItemStack.EMPTY;
    }

    public static boolean riftExtractable(Entity e) {
        Object r = call(method("anomaly.Extractable", "canBeExtracted"), e);
        return r instanceof Boolean b && b;
    }

    // ------------------------------------------------------------------ network hubs

    public static int networkHubCount(MinecraftServer server) {
        Object ledger = call(method("nethub.NetHubLedger", "get", MinecraftServer.class), null, server);
        Object size = ledger == null ? null : call(method("nethub.NetHubLedger", "size"), ledger);
        return size instanceof Integer i ? i : -1;
    }

    // ------------------------------------------------------------------ keycards & dossiers

    /** DossierClearance.clearanceOf: a keycard's level (1-5, Omni = 6), 0 if not a keycard. */
    public static int clearanceOf(ItemStack s) {
        if (s.isEmpty()) return 0;
        Object r = call(method("archive.document.DossierClearance", "clearanceOf", ItemStack.class), null, s);
        return r instanceof Integer i ? i : 0;
    }

    /** Server-side document for an entity's anomaly, or null. */
    public static Object document(net.minecraft.resources.ResourceLocation docId) {
        return call(method("archive.document.AnomalyDocuments", "lookup", net.minecraft.resources.ResourceLocation.class), null, docId);
    }

    /** DocumentClass serialized name ("safe", "euclid", "keter", ...) of a document. */
    public static String documentClass(Object doc) {
        if (doc == null) return null;
        Object c = call(method("archive.document.AnomalyDocument", "Classification"), doc);
        return c instanceof net.minecraft.util.StringRepresentable sr ? sr.getSerializedName() : null;
    }

    // ------------------------------------------------------------------ SCP-001-1, the Gate Guardian

    public static boolean isGateGuardian(Entity e) {
        Class<?> c = cls("scp001gate.Scp001GateGuardian");
        return c != null && c.isInstance(e);
    }

    /** Scp001GateGuardian.calm(ticks): it holds its blades. */
    public static void calmGuardian(Entity guardian, int ticks) {
        if (isGateGuardian(guardian)) call(method("scp001gate.Scp001GateGuardian", "calm", int.class), guardian, ticks);
    }

    // ------------------------------------------------------------------ forcefields

    public static boolean forcefieldNear(net.minecraft.world.level.Level l, Vec3 pos, double r) {
        Object o = call(method("forcefield.ForcefieldIndex", "anyNear", net.minecraft.world.level.Level.class, Vec3.class, double.class), null, l, pos, r);
        return o instanceof Boolean b && b;
    }

    // ------------------------------------------------------------------ D-Class

    public static boolean isDClass(Entity e) {
        Class<?> c = cls("dclass.DClass");
        return c != null && c.isInstance(e);
    }

    public static String dclassDesignation(Entity e) {
        Object r = call(method("dclass.DClass", "designation"), e);
        return r instanceof String s && !s.isEmpty() ? s : e.getName().getString();
    }
}
