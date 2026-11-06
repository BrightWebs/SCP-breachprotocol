package com.breachprotocol.career;

/**
 * The Overseer Council. Seat numbers and nicknames follow the SCP wiki's "O5 Command Dossier"
 * (CC BY-SA 3.0), where every seat has several contradictory identities; one is picked per seat.
 * The roles and dialogue below are original to this mod.
 */
public enum O5 {
    O5_1(1, "The Founder", "Reluctant founder. Older than he looks. Watches the Gate."),
    O5_2(2, "The Gardener", "Tends to the Foundation's more benevolent assets."),
    O5_3(3, "The Ghost in the Machine", "Keeps the records. Speaks only through terminals."),
    O5_4(4, "The Ambassador", "Handles every organisation that isn't us."),
    O5_5(5, "Blackbird", "Watches the Groups of Interest."),
    O5_6(6, "Cowboy", "Ex-field agent. Runs site security."),
    O5_7(7, "The Cardinal", "The Council's tactician, and its leader in all but name."),
    O5_8(8, "Dogwood", "Expands the Mobile Task Forces."),
    O5_9(9, "The Oracle", "Intelligence. Knows things before they happen."),
    O5_10(10, "The Archivist", "Keeper of the timeline and of K-Class records."),
    O5_11(11, "The Mailman", "Signs termination orders. Runs disinformation."),
    O5_12(12, "The Accountant", "Makes sure the numbers add up."),
    O5_13(13, "The Tiebreaker", "The deciding vote. The seat that passes between hands.");

    public final int seat;
    public final String nickname;
    public final String role;

    O5(int seat, String nickname, String role) {
        this.seat = seat;
        this.nickname = nickname;
        this.role = role;
    }

    public String sender() {
        return "O5-" + seat + " \"" + nickname + "\"";
    }

    public static O5 seat(int n) {
        return values()[Math.max(1, Math.min(13, n)) - 1];
    }
}
