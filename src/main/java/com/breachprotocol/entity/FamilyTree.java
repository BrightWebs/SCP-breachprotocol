package com.breachprotocol.entity;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.network.DialoguePayload;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;

/** MCA's family tree: grandparents to grandchildren around one person, sent to a screen you can click through. */
@EventBusSubscriber(modid = BreachProtocol.MODID)
public final class FamilyTree {
    private FamilyTree() {}

    private static void node(ListTag out, Set<UUID> seen, MinecraftServer s, Registry.Rec e, String rel, int row, long now) {
        if (e == null || !seen.add(e.id)) return;
        CompoundTag t = new CompoundTag();
        t.putString("id", e.id.toString());
        t.putString("name", e.name);
        t.putString("rel", rel);
        t.putInt("row", row);
        t.putBoolean("alive", e.alive());
        t.putBoolean("female", e.female);
        t.putBoolean("player", e.player);
        t.putInt("age", e.age(now));
        String where = switch (e.status) {
            case "active" -> e.job + (e.site.isEmpty() ? "" : ", " + e.site);
            case "child" -> e.note.isEmpty() ? "child, at home" : e.note;
            case "dead" -> "died " + (e.cause.isEmpty() ? "" : e.cause);
            case "dclass" -> "reclassified as Class-D";
            case "applicant" -> "applicant";
            default -> e.job.isEmpty() ? "outside the Foundation" : e.job;
        };
        t.putString("job", where);
        List<String> tr = new ArrayList<>();
        for (Traits x : e.traitSet()) if (!x.about.isEmpty()) tr.add(x.label);
        t.putString("traits", String.join(", ", tr));
        t.putString("relState", e.rel);
        out.add(t);
    }

    public static void send(ServerPlayer p, Registry.Rec focus) {
        MinecraftServer s = p.server;
        long now = s.overworld().getGameTime();
        ListTag nodes = new ListTag();
        Set<UUID> seen = new HashSet<>();
        node(nodes, seen, s, focus, "", 0, now);
        Registry.Rec sp = Registry.find(s, focus.spouse);
        node(nodes, seen, s, sp, focus.rel.equals("widowed") ? "late spouse" : sp != null && sp.female ? "wife" : "husband", 0, now);
        Registry.Rec pa = Registry.find(s, focus.partner);
        node(nodes, seen, s, pa, focus.rel, 0, now);
        for (Registry.Rec sib : Registry.siblings(s, focus)) node(nodes, seen, s, sib, sib.female ? "sister" : "brother", 0, now);
        for (Registry.Rec par : Registry.parents(s, focus)) {
            node(nodes, seen, s, par, par.female ? "mother" : "father", -1, now);
            for (Registry.Rec gp : Registry.parents(s, par)) node(nodes, seen, s, gp, gp.female ? "grandmother" : "grandfather", -2, now);
        }
        for (Registry.Rec k : Registry.children(s, focus)) {
            node(nodes, seen, s, k, k.female ? "daughter" : "son", 1, now);
            for (Registry.Rec gk : Registry.children(s, k)) node(nodes, seen, s, gk, gk.female ? "granddaughter" : "grandson", 2, now);
        }
        CompoundTag t = new CompoundTag();
        t.putBoolean("tree", true);
        t.putString("title", "Family of " + focus.name);
        t.put("nodes", nodes);
        PacketDistributor.sendToPlayer(p, new DialoguePayload(t));
    }

    @SubscribeEvent
    public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("familytree").then(Commands.argument("id", StringArgumentType.word()).executes(c -> {
            ServerPlayer p = c.getSource().getPlayerOrException();
            try {
                Registry.Rec e = Registry.find(p.server, UUID.fromString(StringArgumentType.getString(c, "id")));
                if (e != null) send(p, e);
            } catch (IllegalArgumentException ignored) {}
            return 1;
        })));
    }
}
