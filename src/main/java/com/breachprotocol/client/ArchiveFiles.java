package com.breachprotocol.client;

import com.breachprotocol.compat.Unleashed;

import java.util.List;

/**
 * The phone's Archive: short, original summaries of the anomalies SCP Unleashed adds.
 * Sections above your clearance are drawn as redaction bars. Only files whose entity is
 * actually registered by the installed SCP Unleashed are listed.
 */
public final class ArchiveFiles {
    public record Section(String title, int clearance, String text) {}

    public record Entry(String id, String designation, String objectClass, List<Section> sections) {
        public boolean present() {
            if (id.equals("omega")) return true;
            if (id.equals("scp500")) return Unleashed.scp500() != net.minecraft.world.item.Items.ENCHANTED_GOLDEN_APPLE;
            return Unleashed.entity(id).isPresent();
        }
    }

    private static Section s(String title, int clr, String text) { return new Section(title, clr, text); }

    public static final List<Entry> ENTRIES = List.of(
            new Entry("scp999", "SCP-999", "Safe", List.of(
                    s("Description", 1, "A friendly orange gelatinous organism. Contact reliably improves mood."),
                    s("Procedures", 2, "May roam its wing freely. Staff on long shifts are encouraged to visit."),
                    s("Notes", 3, "Career note: thirty seconds of care near SCP-999 completes a CARE assignment."))),
            new Entry("scp131_pod", "SCP-131", "Safe", List.of(
                    s("Description", 1, "Small one-eyed teardrop creatures that follow personnel they like."),
                    s("Procedures", 2, "Recoverable at Level 2. Keep away from moving machinery."))),
            new Entry("scp066", "SCP-066", "Euclid", List.of(
                    s("Description", 1, "A mass of braided yarn that greets people with very loud noise."),
                    s("Procedures", 2, "Ear protection required. Recoverable at Level 2."))),
            new Entry("scp173", "SCP-173", "Euclid", List.of(
                    s("Description", 1, "A concrete statue that only moves when unobserved."),
                    s("Procedures", 3, "Never break line of sight. Enter its cell in groups and keep eye contact."),
                    s("Incident log", 4, "Breaches near staff are announced by O5 and must be recontained quickly."))),
            new Entry("scp049", "SCP-049", "Euclid", List.of(
                    s("Description", 1, "A humanoid in plague doctor garb who speaks of curing 'the Pestilence'."),
                    s("Procedures", 2, "Keep personnel at a distance. Recoverable at Level 2."),
                    s("SCP-049-2", 3, "Those it 'cures' rise as hostile instances. Terminate on sight."))),
            new Entry("scp457", "SCP-457", "Euclid", List.of(
                    s("Description", 1, "A sentient fire in a roughly human shape that grows as it burns."),
                    s("Procedures", 3, "No flammable materials in or near its cell."))),
            new Entry("scp106", "SCP-106", "Keter", List.of(
                    s("Description", 1, "An elderly humanoid that passes through solid matter and corrodes it."),
                    s("Procedures", 4, "Recontainment requires a lure and a sealed cell. Level 4 to recover."),
                    s("Pocket dimension", 5, "Victims are dragged to a separate space. Few find their way back."))),
            new Entry("scp939", "SCP-939", "Keter", List.of(
                    s("Description", 1, "Pack predators that lure prey by imitating human voices."),
                    s("Procedures", 4, "Do not answer voices you can't see. Level 4 to recover."))),
            new Entry("scp096", "SCP-096", "Euclid", List.of(
                    s("Description", 1, "A pale, emaciated humanoid. Docile unless its face is seen."),
                    s("Procedures", 3, "No cameras, mirrors or windows facing it. Bagging the head prevents triggers."),
                    s("Photographs", 4, "Any image of its face triggers it against the viewer, wherever they are. Phone photos count. Delete on sight."),
                    s("Notes", 5, "Recovery is a Level 5 assignment. The Administrator is not recognised as prey."))),
            new Entry("scp682", "SCP-682", "Keter", List.of(
                    s("Description", 1, "A large hostile reptile that adapts to whatever harms it."),
                    s("Procedures", 4, "Termination attempts are logged and repeated. None have worked."),
                    s("O5 directive", 6, "The Council treats its recovery as an O5-level assignment."))),
            new Entry("scp500", "SCP-500", "Safe", List.of(
                    s("Description", 1, "Red pills that cure the person who takes one of any disease."),
                    s("Procedures", 3, "Stocked in Site-01 and kept in limited supply."),
                    s("Ascension", 6, "The Ascension Altar at Site-01 accepts one pill as its final offering."))),
            new Entry("omega", "SCP-Ω", "Thaumiel", List.of(
                    s("Description", 1, "[DATA EXPUNGED]"),
                    s("Identity", 6, "The Administrator, after Ascension. Anomalies recognise them as master and will not harm them."),
                    s("Gate Guardian", 6, "The guardian of Site-01 kneels to SCP-Ω and lets them pass."))));

    public static Entry get(String id) {
        for (Entry e : ENTRIES) if (e.id().equals(id)) return e;
        return null;
    }

    private ArchiveFiles() {}
}
