package com.breachprotocol.career;

import net.minecraft.ChatFormatting;

/** The Foundation career ladder, from expendable to untouchable. */
public enum Rank {
    CLASS_D("Class-D Personnel", 0, 0, ChatFormatting.GOLD),
    LEVEL_1("Level 1 - Janitorial Staff", 2, 1, ChatFormatting.GRAY),
    LEVEL_2("Level 2 - Junior Researcher", 5, 2, ChatFormatting.BLUE),
    LEVEL_3("Level 3 - Security Officer", 9, 3, ChatFormatting.YELLOW),
    LEVEL_4("Level 4 - Containment Specialist", 14, 4, ChatFormatting.GOLD),
    LEVEL_5("Level 5 - Site Director", 20, 5, ChatFormatting.DARK_GRAY),
    OVERSEER("O5-13 \"The Tiebreaker\"", 27, 5, ChatFormatting.DARK_RED),
    ADMINISTRATOR("The Administrator", 35, 5, ChatFormatting.LIGHT_PURPLE);

    public final String title;
    /** Completed assignments (commendations) the Council wants to see before this rank. */
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
