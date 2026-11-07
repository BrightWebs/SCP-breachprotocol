package com.breachprotocol.entity;

import com.breachprotocol.career.WorldNews;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;

/**
 * What a member of staff remembers about you: the secrets they've told you and how you answered,
 * how you responded to their stories, promises. They bring it up the next time you talk.
 */
final class Memory {
    private Memory() {}

    static CompoundTag of(FoundationStaff s, ServerPlayer p) {
        String k = "bp_mem_" + p.getUUID();
        CompoundTag root = s.getPersistentData();
        if (!root.contains(k)) root.put(k, new CompoundTag());
        return root.getCompound(k);
    }

    static void put(FoundationStaff s, ServerPlayer p, String key, String value) {
        CompoundTag m = of(s, p);
        m.putString(key, value);
        m.putLong("t_" + key, s.level().getGameTime());
    }

    static String get(FoundationStaff s, ServerPlayer p, String key) { return of(s, p).getString(key); }

    static void count(FoundationStaff s, ServerPlayer p, String key) { CompoundTag m = of(s, p); m.putInt(key, m.getInt(key) + 1); }

    // ================================================================== secrets and your answers

    record Reply(String label, String reaction, String remember, int trust) {}

    record Secret(String id, String text, List<Reply> replies) {}

    /** The secrets this person could tell you, in the order they'd tell them. */
    static List<Secret> secrets(FoundationStaff s, ServerPlayer p) {
        List<Secret> out = new ArrayList<>();
        WorldNews.Item d = WorldNews.latest(p.server, WorldNews.Kind.DECISION);
        out.add(new Secret("door", "There's a room on the lowest basement level that isn't on any plan. I've seen the door. I've never seen it open.", List.of(
                new Reply("I'll look into it.", "Be careful. And if anyone asks, you didn't hear it from me.", "investigate", 1),
                new Reply("Some doors are best left shut.", "...You know what's behind it. Don't you.", "leave", 0),
                new Reply("Who else knows about it?", "Me. A janitor who retired early. And now you.", "asked", 1))));
        out.add(new Secret("volunteers", "Some of the D-Class volunteered. Think about what that means about where they came from.", List.of(
                new Reply("Their files are sealed for a reason.", "Then I'll stop asking. For now.", "sealed", 0),
                new Reply("Find out who recruited them.", "I'll ask around. Quietly.", "recruiter", 1),
                new Reply("That's horrifying.", "It is. Thank you for saying so. Nobody here does.", "horrified", 2))));
        out.add(new Secret("fountain", "I've seen O5 members walk past and not age a day in ten years. They say it's the Fountain. SCP-006. I believe it.", List.of(
                new Reply("It's true.", "...I knew it. I won't tell anyone. Who'd believe me?", "true", 2),
                new Reply("That's nonsense.", "If you say so. They still look younger than me.", "denied", -1),
                new Reply("Keep that to yourself.", "Of course. Consider it forgotten.", "quiet", 1))));
        out.add(new Secret("guardian", "One of the guards swears the Gate Guardian looked at them. Not at the door. At them.", List.of(
                new Reply("It looks at everyone.", "That's not as comforting as you think it is.", "everyone", 0),
                new Reply("Tell them to stay away from the Gate.", "I will. They already do. They don't sleep much.", "warned", 1),
                new Reply("Bring them to me.", "They'll be terrified. But I'll bring them.", "summoned", 1))));
        if (d != null && !d.supporters().isEmpty())
            out.add(new Secret("vote", "The vote on whether " + d.headline() + "? It wasn't close. O5-" + d.supporters().iterator().next() + " argued for it like their life depended on it. Maybe it did.", List.of(
                    new Reply("That stays between us.", "Obviously. I like my memories un-amnesticised.", "between", 1),
                    new Reply("I'll remember that.", "I thought you might.", "noted", 0))));
        if (Talk.topBrass(p))
            out.add(new Secret("garden", "People say you've been past the Gate. That you've seen the Garden. If that's true... was it beautiful?", List.of(
                    new Reply("It was beautiful.", "...Thank you. I needed to hear that something is.", "beautiful", 2),
                    new Reply("I can't talk about it.", "I understand. I shouldn't have asked.", "silent", 0),
                    new Reply("It was terrifying.", "Oh. Then I'm glad you came back.", "terrifying", 1))));
        return out;
    }

    /** What they say the next time, remembering how you answered. */
    static String recall(FoundationStaff s, ServerPlayer p, Random r) {
        List<String> lines = new ArrayList<>();
        switch (get(s, p, "s_door")) {
            case "investigate" -> lines.add("Did you ever find out what's behind that door on the lowest level? You said you'd look into it.");
            case "leave" -> lines.add("I walked past that door again. I didn't stop. You were right.");
            default -> {}
        }
        switch (get(s, p, "s_volunteers")) {
            case "recruiter" -> lines.add("About the D-Class volunteers: I asked around, like you said. Nobody will give me a name. That's an answer in itself.");
            case "horrified" -> lines.add("I keep thinking about what you said about the volunteers. It helped, someone else saying it.");
            default -> {}
        }
        if (get(s, p, "s_fountain").equals("true")) lines.add("I haven't told a soul about the Fountain. I looked at O5-1 differently at the briefing, though.");
        if (get(s, p, "s_guardian").equals("summoned")) lines.add("The guard who saw the Guardian? They still want to talk to you. They're scared stiff.");
        switch (get(s, p, "s_garden")) {
            case "beautiful" -> lines.add("I think about what you said, that the Garden was beautiful. On bad nights especially.");
            case "terrifying" -> lines.add("I've been thinking about the Garden. Terrifying, you said. I'm glad I've never seen it.");
            default -> {}
        }
        if (of(s, p).getInt("listened") >= 2) lines.add("You always listen. Not many people around here do.");
        if (of(s, p).getInt("promised") > 0 && r.nextBoolean()) lines.add("You said you'd see to what I needed. I haven't forgotten.");
        if (get(s, p, "story_reply").equals("hard")) lines.add("Last time you said it must have been hard. It was. Still is, some days.");
        return lines.isEmpty() ? "" : lines.get(r.nextInt(lines.size()));
    }
}
