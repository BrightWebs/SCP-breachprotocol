package com.breachprotocol.career;

import net.minecraft.ChatFormatting;

/** The Foundation career ladder, from expendable to untouchable. */
public enum Rank {
    CLASS_D("Class-D Personnel", 0, 0, ChatFormatting.GOLD),
    LEVEL_1("Level 1 - Janitorial Staff", 60, 1, ChatFormatting.GRAY),
    LEVEL_2("Level 2 - Junior Researcher", 200, 2, ChatFormatting.BLUE),
    LEVEL_3("Level 3 - Security Officer", 450, 3, ChatFormatting.YELLOW),
    LEVEL_4("Level 4 - Containment Specialist", 850, 4, ChatFormatting.GOLD),
    LEVEL_5("Level 5 - Site Director", 1400, 5, ChatFormatting.DARK_GRAY),
    OVERSEER("O5-13 \"The Tiebreaker\"", 2200, 5, ChatFormatting.DARK_RED),
    ADMINISTRATOR("The Administrator", 3500, 5, ChatFormatting.LIGHT_PURPLE);

    public final String title;
    /** Total XP needed to reach this rank. */
    public final int xpRequired;
    public final int clearance;
    public final ChatFormatting color;

    Rank(String title, int xpRequired, int clearance, ChatFormatting color) {
        this.title = title;
        this.xpRequired = xpRequired;
        this.clearance = clearance;
        this.color = color;
    }

    public Rank next() {
        return this == ADMINISTRATOR ? null : values()[ordinal() + 1];
    }

    public boolean atLeast(Rank other) {
        return ordinal() >= other.ordinal();
    }

    public static Rank byId(int id) {
        Rank[] v = values();
        return v[Math.max(0, Math.min(v.length - 1, id))];
    }
}
