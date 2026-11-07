package com.breachprotocol.entity;

import java.util.*;

/**
 * Inheritable traits, after Minecraft Comes Alive Reborn's (left-handed, colour blind, albinism,
 * dwarfism, lactose intolerance...), plus a few only the Foundation would test for. Each has a chance
 * to occur and a chance to be passed to a child, and most of them do something.
 */
public enum Traits {
    LEFT_HANDED("Left-handed", 0.10F, 0.5F, "Writes with the wrong hand. Shoots with it, too."),
    COLOUR_BLIND("Colour blind", 0.05F, 0.5F, "Can't tell the hazard stripes apart. Bad for engineering."),
    HETEROCHROMIA("Heterochromia", 0.02F, 0.5F, "Two different coloured eyes."),
    ATHLETIC("Athletic", 0.08F, 0.5F, "Faster on their feet."),
    WEAK("Frail", 0.05F, 0.5F, "Gets hurt easily."),
    TOUGH("Tough", 0.06F, 0.5F, "Takes a lot to put down."),
    LACTOSE("Lactose intolerant", 0.10F, 1.0F, "Don't give them milk."),
    DIABETIC("Diabetic", 0.04F, 0.6F, "Sweets are off the menu."),
    VEGETARIAN("Vegetarian", 0.08F, 0.3F, "Won't touch meat."),
    INFERTILE("Infertile", 0.04F, 0.0F, "Can't have children."),
    ALBINISM("Albinism", 0.01F, 1.0F, "Very pale skin and white hair."),
    DWARFISM("Dwarfism", 0.01F, 1.0F, "Shorter than most."),
    ASTHMA("Asthmatic", 0.06F, 0.4F, "Struggles on stairs and in smoke."),
    INSOMNIAC("Insomniac", 0.06F, 0.2F, "Never sleeps. Always on shift."),
    EIDETIC("Eidetic memory", 0.03F, 0.4F, "Forgets nothing. Excellent researcher."),
    AMNESTIC_RESISTANT("Amnestic-resistant", 0.02F, 0.5F, "Amnestics don't take. They remember everything."),
    MEMETIC_RESISTANT("Memetically resistant", 0.02F, 0.3F, "Cognitohazards slide off them."),
    BISEXUAL("Bisexual", 0.05F, 0.0F, ""),
    HOMOSEXUAL("Gay", 0.04F, 0.0F, "");

    public final String label, about;
    public final float chance, inherit;

    Traits(String label, float chance, float inherit, String about) { this.label = label; this.chance = chance; this.inherit = inherit; this.about = about; }

    /** The traits someone was born with (no parents on record): fixed by who they are. */
    public static EnumSet<Traits> natural(UUID id) {
        Random r = new Random(id.getMostSignificantBits() ^ id.getLeastSignificantBits() * 31 ^ 0x7A17L);
        EnumSet<Traits> out = EnumSet.noneOf(Traits.class);
        for (Traits t : values()) if (r.nextFloat() < t.chance) out.add(t);
        if (out.contains(TOUGH) && out.contains(WEAK)) out.remove(WEAK);
        if (out.contains(BISEXUAL) && out.contains(HOMOSEXUAL)) out.remove(BISEXUAL);
        return out;
    }

    /** A child's traits: some passed down from each parent, a few new. */
    public static EnumSet<Traits> inherit(Set<Traits> a, Set<Traits> b, Random r) {
        EnumSet<Traits> out = EnumSet.noneOf(Traits.class);
        for (Traits t : values()) {
            float pass = (a.contains(t) ? t.inherit : 0) + (b.contains(t) ? t.inherit : 0);
            if (r.nextFloat() < Math.min(0.95F, pass) || r.nextFloat() < t.chance * 0.5F) out.add(t);
        }
        if (out.contains(TOUGH) && out.contains(WEAK)) out.remove(WEAK);
        return out;
    }

    public static String write(Set<Traits> s) {
        StringBuilder b = new StringBuilder();
        for (Traits t : s) { if (b.length() > 0) b.append(','); b.append(t.name()); }
        return b.toString();
    }

    public static EnumSet<Traits> read(String s) {
        EnumSet<Traits> out = EnumSet.noneOf(Traits.class);
        if (s == null || s.isEmpty()) return out;
        for (String p : s.split(",")) try { out.add(valueOf(p.trim())); } catch (IllegalArgumentException ignored) {}
        return out;
    }

    /** Would they be attracted to someone of this sex? */
    public static boolean attracted(boolean female, Set<Traits> mine, boolean otherFemale) {
        if (mine.contains(BISEXUAL)) return true;
        return mine.contains(HOMOSEXUAL) == (female == otherFemale);
    }

    /** A gift they can't accept because of who they are, with what they'd say; null if it's fine. */
    public static String refuses(Set<Traits> t, String itemId) {
        String i = itemId.toLowerCase(Locale.ROOT);
        if (t.contains(LACTOSE) && (i.contains("milk") || i.contains("cake") || i.contains("cheese"))) return "That's kind, but I'm lactose intolerant. I'd be in Medical by lunch.";
        if (t.contains(DIABETIC) && (i.contains("cake") || i.contains("cookie") || i.contains("sugar") || i.contains("honey") || i.contains("pie") || i.contains("sweet"))) return "I can't. Diabetic. But thank you for thinking of me.";
        if (t.contains(VEGETARIAN) && (i.contains("beef") || i.contains("porkchop") || i.contains("chicken") || i.contains("mutton") || i.contains("rabbit") || i.contains("cod") || i.contains("salmon") || i.contains("steak"))) return "Oh- I don't eat meat. Sorry. It's a nice thought.";
        return null;
    }
}
