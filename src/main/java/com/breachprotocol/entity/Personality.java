package com.breachprotocol.entity;

import java.util.Random;

/**
 * Who someone is, in the manner of Minecraft Comes Alive Reborn's personalities (friendly, gloomy,
 * crabby, odd...), set in the Foundation. A personality shapes how hard they are to talk to, what
 * they enjoy, and above all how they say things: the same news sounds different from every mouth.
 */
public enum Personality {
    FRIENDLY("Friendly", "Easy to get on with. Remembers your name.", Talk.Temper.EARNEST),
    UPBEAT("Upbeat", "Finds the bright side, even on the Keter level.", Talk.Temper.EARNEST),
    PLAYFUL("Playful", "Jokes to cope. Usually at the worst moment.", Talk.Temper.BOLD),
    GLOOMY("Gloomy", "Assumes the worst. Is usually right.", Talk.Temper.CYNICAL),
    SENSITIVE("Sensitive", "Takes things to heart, good and bad.", Talk.Temper.EARNEST),
    GREEDY("Greedy", "Always angling for hazard pay.", Talk.Temper.CYNICAL),
    ODD("Odd", "Has worked here a little too long.", Talk.Temper.CAREFUL),
    CRABBY("Crabby", "Hard to talk to. Harder to impress.", Talk.Temper.CYNICAL),
    EXTROVERTED("Extroverted", "Knows everyone on the site. Talks to all of them.", Talk.Temper.BOLD),
    INTROVERTED("Introverted", "Says little. Means all of it.", Talk.Temper.CAREFUL),
    RELAXED("Relaxed", "Unbothered by sirens, mostly.", Talk.Temper.BOLD),
    ANXIOUS("Anxious", "Counts the exits in every room.", Talk.Temper.CAREFUL),
    PEACEFUL("Peaceful", "Hates conflict. Hates the guns more.", Talk.Temper.CAREFUL),
    FLIRTY("Charming", "Likes people, and likes being liked.", Talk.Temper.BOLD);

    public final String label, about;
    public final Talk.Temper temper;

    Personality(String label, String about, Talk.Temper temper) { this.label = label; this.about = about; this.temper = temper; }

    public static Personality of(Person p) { return values()[Math.min(values().length - 1, (int) (p.gene(20) * values().length))]; }

    // ================================================================== how they say it

    /** Their voice over a line: an opener, an aside or a closer, some of the time. */
    String voice(String line, String you, Random r) {
        if (line.length() < 12 || r.nextInt(100) >= 40) return line;
        String l = line;
        return switch (this) {
            case FRIENDLY -> r.nextBoolean() ? you + ", " + lower(l) : l + " Good to see you, by the way.";
            case UPBEAT -> r.nextBoolean() ? "Oh! " + l : l.replaceAll("\\.$", "!") + " We'll be fine.";
            case PLAYFUL -> l + " " + pick(r, "Anyway. Coffee?", "...Too soon?", "That's what I'd say if I were a mimetic hazard, too.");
            case GLOOMY -> l + " " + pick(r, "Not that it'll last.", "We'll all be in a dossier one day.", "It always gets worse before it gets worse.");
            case SENSITIVE -> l + " " + pick(r, "Sorry. It's been a long week.", "Thank you for listening, really.");
            case GREEDY -> l + " " + pick(r, "Any word on hazard pay?", "They don't pay me enough for this floor.");
            case ODD -> pick(r, "Hm. ", "The vents are humming in B-flat today. ", "Do you hear that? No? ") + l;
            case CRABBY -> pick(r, "Hmph. ", "Fine. ", "If you must know: ") + l;
            case EXTROVERTED -> l + " " + pick(r, "Anyway! How are YOU?", "I was just telling the canteen the same thing.");
            case INTROVERTED -> firstSentence(l) + "...";
            case RELAXED -> pick(r, "Eh. ", "Honestly? ", "Meh. ") + l;
            case ANXIOUS -> "I- " + lower(l) + " " + pick(r, "Sorry.", "Is that bad? That's bad, isn't it.");
            case PEACEFUL -> l + " " + pick(r, "Let's not make it a fight.", "Everyone's doing their best.");
            case FLIRTY -> l + " " + pick(r, "You look sharp today, by the way.", "You make the late shifts bearable.");
        };
    }

    static String lower(String s) { return s.length() > 1 && Character.isUpperCase(s.charAt(0)) && !Character.isUpperCase(s.charAt(1)) ? Character.toLowerCase(s.charAt(0)) + s.substring(1) : s; }

    private static String firstSentence(String s) { int i = s.indexOf(". "); return i > 0 ? s.substring(0, i) : s.replaceAll("[.!?]+$", ""); }

    private static String pick(Random r, String... o) { return o[r.nextInt(o.length)]; }

    // ================================================================== what works on them (for socialising)

    /** How much an interaction suits them: + easier, - harder. */
    int affinity(String interaction) {
        return switch (interaction) {
            case "chat" -> switch (this) { case FRIENDLY, EXTROVERTED, UPBEAT -> 2; case INTROVERTED, CRABBY -> -2; default -> 0; };
            case "joke" -> switch (this) { case PLAYFUL, UPBEAT, RELAXED -> 3; case GLOOMY, CRABBY, SENSITIVE -> -2; case ODD -> 1; default -> 0; };
            case "compliment" -> switch (this) { case SENSITIVE, FLIRTY, GREEDY -> 2; case CRABBY -> -1; default -> 0; };
            case "comfort" -> switch (this) { case SENSITIVE, ANXIOUS, GLOOMY -> 3; case RELAXED -> -1; default -> 1; };
            case "flirt" -> switch (this) { case FLIRTY -> 4; case EXTROVERTED, PLAYFUL -> 1; case CRABBY, INTROVERTED, ANXIOUS -> -3; default -> -1; };
            case "hug" -> switch (this) { case FRIENDLY, SENSITIVE, UPBEAT -> 2; case CRABBY, INTROVERTED -> -3; default -> 0; };
            default -> 0;
        };
    }
}
