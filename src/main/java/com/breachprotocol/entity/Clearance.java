package com.breachprotocol.entity;

import com.breachprotocol.career.WorldNews;
import net.minecraft.server.level.ServerPlayer;

import java.util.regex.Pattern;

/**
 * Need-to-know. Most of the Foundation has never seen an Overseer: to junior staff and D-Class the
 * O5 Council is a rumour (thirteen people who decide everything, if they exist at all), and who
 * voted for what, seat changes, the Garden and the Administrator's own business are above their
 * clearance. Staff at Level 3-4 know the Council exists, but not who sits on it. From Site Director
 * up, people speak plainly. Everything a member of staff says passes through here first.
 */
final class Clearance {
    private Clearance() {}

    /** Prefix for a line that is deliberately about the rumour itself (left as written). */
    static final String RAW = "\u0001";

    private static final Pattern SEAT = Pattern.compile("O5-\\d+(\\s+\"[^\"]*\")?");

    /** Can someone of this tier know about this event at all? */
    static boolean knows(Scene.Tier tier, WorldNews.Item i) {
        if (tier.ordinal() >= Scene.Tier.DIRECTOR.ordinal()) return true;
        return switch (i.kind()) {
            case SEAT_VOTE, KNOWLEDGE, BANISHED, PROMOTION -> false;
            case SHYGUY_CALMED, SHYGUY_KILLED -> tier.ordinal() >= Scene.Tier.STAFF.ordinal();
            default -> true;
        };
    }

    /** The line as someone of this rank would be told it. */
    static String filter(ServerPlayer p, String line) {
        if (line.startsWith(RAW)) return line.substring(1);
        Scene.Tier t = Scene.tier(p);
        if (t.ordinal() >= Scene.Tier.DIRECTOR.ordinal()) return line;
        String out = line;
        if (t == Scene.Tier.STAFF) {
            // they know there is a Council; not who sits on it
            out = SEAT.matcher(out).replaceAll("one of the Overseers");
            return out;
        }
        // juniors and D-Class: there's no "Council" they'd name, only the people upstairs
        out = SEAT.matcher(out).replaceAll("someone very senior");
        out = out.replace("the O5 Council", "the people upstairs").replace("The O5 Council", "The people upstairs")
                .replace("the Council's", "upstairs'").replace("The Council's", "Upstairs'")
                .replace("the Council", "the higher-ups").replace("The Council", "The higher-ups")
                .replace("the Overseers", "the higher-ups").replace("Overseers", "higher-ups").replace("Overseer", "higher-up")
                .replace(" the O5", " the higher-ups").replace("O5", "the higher-ups");
        return out;
    }
}
