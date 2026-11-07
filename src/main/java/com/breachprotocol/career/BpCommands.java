package com.breachprotocol.career;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.compat.Unleashed;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Arrays;
import java.util.Locale;

/**
 * Testing commands (operators only, permission level 2). Everything acts on you:
 * <pre>
 * /bp rank &lt;class_d|level_1..level_5|overseer|administrator&gt;   set your rank
 * /bp promote                    next rank, as if the Council promoted you
 * /bp commend [count]            add completed-assignment credit
 * /bp mission &lt;type&gt;              get an assignment now (exposure, document, files, recover, terminate, care, supply)
 * /bp missions clear             drop all assignments
 * /bp call                       ring the phone with a Council call (O5+ decisions)
 * /bp clearance &lt;0-6&gt;            fuse a keycard level into your phone (6 = Omni)
 * /bp link all | clear           link every SCP Unleashed device into your phone, or unlink them
 * /bp stability &lt;0-100&gt;          set Foundation stability
 * /bp site declare [radius]      declare a site here, any rank
 * /bp site clear                 remove every site
 * /bp tower                      build a Foundation Site Tower here
 * /bp ascend                     become SCP-Ω
 * /bp phone                      give yourself a phone
 * /bp reset                      wipe your career back to a fresh Class-D
 * </pre>
 */
@EventBusSubscriber(modid = BreachProtocol.MODID)
public final class BpCommands {
    private BpCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("bp").requires(s -> s.hasPermission(2))
                .then(Commands.literal("rank").then(Commands.argument("rank", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(Rank.values()).map(r -> r.name().toLowerCase(Locale.ROOT)), b))
                        .executes(BpCommands::rank)))
                .then(Commands.literal("promote").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    Rank next = Career.rank(p).next();
                    if (next == null) return fail(c, "Already at the top.");
                    Career.promote(p, next);
                    return ok(c, "Promoted to " + next.title);
                }))
                .then(Commands.literal("commend").executes(c -> commend(c, 1))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 100)).executes(c -> commend(c, IntegerArgumentType.getInteger(c, "count")))))
                .then(Commands.literal("mission")
                        .then(Commands.argument("type", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(Mission.Type.values()).filter(t -> t != Mission.Type.FOUNDERS_TRIAL).map(t -> t.name().toLowerCase(Locale.ROOT)), b))
                                .executes(BpCommands::mission)))
                .then(Commands.literal("missions").then(Commands.literal("clear").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    Career.data(p).missions.clear();
                    return done(p, c, "Assignments cleared.");
                })))
                .then(Commands.literal("call").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    Career.data(p).call = null;
                    Calls.startRandom(p);
                    return done(p, c, "The phone is ringing.");
                }))
                .then(Commands.literal("clearance").then(Commands.argument("level", IntegerArgumentType.integer(0, 6)).executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    ItemStack phone = phone(p);
                    int lvl = IntegerArgumentType.getInteger(c, "level");
                    String card = lvl == 0 ? "" : net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(Unleashed.keycard(lvl)).toString();
                    CustomData.update(DataComponents.CUSTOM_DATA, phone, t -> { t.putInt(Clearance.FUSED, lvl); t.putString(Clearance.FUSED_CARD, card); });
                    return done(p, c, "Phone clearance set to " + (lvl >= 6 ? "Omni" : "Level " + lvl) + ".");
                })))
                .then(Commands.literal("link")
                        .then(Commands.literal("all").executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            ItemStack phone = phone(p);
                            CustomData.update(DataComponents.CUSTOM_DATA, phone, t -> {
                                net.minecraft.nbt.ListTag l = new net.minecraft.nbt.ListTag();
                                for (String[] dev : Clearance.DEVICES) l.add(net.minecraft.nbt.StringTag.valueOf(dev[1]));
                                t.put(Clearance.LINKS, l);
                            });
                            return done(p, c, "Every device linked.");
                        }))
                        .then(Commands.literal("clear").executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            CustomData.update(DataComponents.CUSTOM_DATA, phone(p), t -> t.remove(Clearance.LINKS));
                            return done(p, c, "Devices unlinked.");
                        })))
                .then(Commands.literal("stability").then(Commands.argument("value", IntegerArgumentType.integer(0, 100)).executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    Career.data(p).stability = IntegerArgumentType.getInteger(c, "value");
                    return done(p, c, "Stability set.");
                })))
                .then(Commands.literal("site")
                        .then(Commands.literal("declare").executes(c -> site(c, 1))
                                .then(Commands.argument("radius", IntegerArgumentType.integer(16, 256)).executes(c -> site(c, IntegerArgumentType.getInteger(c, "radius")))))
                        .then(Commands.literal("clear").executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            Sites s = Sites.get(p.server);
                            s.sites.clear();
                            s.setDirty();
                            return done(p, c, "All sites removed.");
                        })))
                .then(Commands.literal("tower").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    BlockPos origin = com.breachprotocol.world.SiteTower.build(p.serverLevel(), p.blockPosition(), p.getUUID().toString(), p.getGameProfile().getName());
                    p.teleportTo(origin.getX() + 15.5, origin.getY() + 1, origin.getZ() + 24.5);
                    return done(p, c, "Site tower built at " + origin.toShortString() + ".");
                }))
                .then(Commands.literal("ascend").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    CareerData data = Career.data(p);
                    if (data.getRank() != Rank.ADMINISTRATOR) Career.promote(p, Rank.ADMINISTRATOR);
                    CareerEvents.ascend(p, data);
                    return done(p, c, "You are SCP-Ω.");
                }))
                .then(Commands.literal("phone").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    ItemStack s = new ItemStack(com.breachprotocol.registry.ModItems.PHONE.get());
                    if (!p.getInventory().add(s)) p.drop(s, false);
                    return ok(c, "Phone issued.");
                }))
                .then(Commands.literal("reset").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    p.removeTag(CareerEvents.GATE_SPARED);
                    p.setInvulnerable(false);
                    com.breachprotocol.world.Site01.releaseAdministrator(p);
                    CareerData fresh = new CareerData();
                    fresh.recruited = true;
                    Career.data(p).load(fresh.save());
                    return done(p, c, "Career reset. You are Class-D again.");
                })));
    }

    private static ItemStack phone(ServerPlayer p) throws CommandSyntaxException {
        ItemStack phone = Clearance.phoneOf(p);
        if (phone.isEmpty()) throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(Component.literal("Hold or carry a Foundation Phone first (/bp phone).")).create();
        return phone;
    }

    private static int rank(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(c, "rank").toUpperCase(Locale.ROOT);
        Rank r;
        try { r = Rank.valueOf(name); } catch (IllegalArgumentException e) { return fail(c, "Unknown rank " + name); }
        CareerData d = Career.data(p);
        d.setRank(r);
        d.xp = Math.max(d.xp, r.xpRequired);
        return done(p, c, "Rank set to " + r.title + ".");
    }

    private static int commend(CommandContext<CommandSourceStack> c, int n) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        for (int i = 0; i < n; i++) Career.commend(p);
        return done(p, c, n + " commendation" + (n == 1 ? "" : "s") + " added.");
    }

    private static int mission(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        Mission.Type t;
        try { t = Mission.Type.valueOf(StringArgumentType.getString(c, "type").toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException e) { return fail(c, "Unknown assignment type."); }
        int before = Career.data(p).missions.size();
        Career.assign(p, t, null);
        if (Career.data(p).missions.size() == before) return fail(c, "Couldn't place that assignment here (try the overworld, on open ground).");
        return done(p, c, "Assignment added.");
    }

    private static int site(CommandContext<CommandSourceStack> c, int radius) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        Sites all = Sites.get(p.server);
        Sites.Site s = new Sites.Site();
        s.name = "Site-" + (10 + all.sites.size() * 7 % 90);
        s.owner = p.getUUID().toString();
        s.ownerName = p.getGameProfile().getName();
        s.dim = p.level().dimension().location().toString();
        s.x = p.getBlockX(); s.y = p.getBlockY(); s.z = p.getBlockZ();
        s.radius = radius == 1 ? 48 : radius;
        all.sites.add(s);
        all.setDirty();
        return done(p, c, s.name + " declared, " + s.radius + "m.");
    }

    private static int done(ServerPlayer p, CommandContext<CommandSourceStack> c, String msg) {
        Career.data(p).dirty = true;
        Career.sync(p);
        return ok(c, msg);
    }

    private static int ok(CommandContext<CommandSourceStack> c, String msg) {
        c.getSource().sendSuccess(() -> Component.literal("[BP] " + msg), false);
        return 1;
    }

    private static int fail(CommandContext<CommandSourceStack> c, String msg) {
        c.getSource().sendFailure(Component.literal("[BP] " + msg));
        return 0;
    }
}
