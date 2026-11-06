package com.breachprotocol.client;

import com.breachprotocol.compat.UnleashedApi;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;

import java.util.*;

/**
 * The phone's Archive reads SCP Unleashed's own anomaly dossiers (AnomalyDocuments / DossierContent)
 * and applies its own clearance rule (DossierClearance): Safe and Euclid need Level 2, Keter 3,
 * Thaumiel 4, anything stranger 5, and Omni opens everything. Passages SCP Unleashed marks as
 * redacted stay blacked out unless you hold Omni-level clearance (Omni keycard, O5 or above).
 */
public final class ArchiveFiles {
    private ArchiveFiles() {}

    public record Section(String heading, String text) {}

    public record Entry(String id, String designation, String title, String objectClass, String classKey, int required, List<Section> sections) {}

    private static List<Entry> cache = List.of();
    private static int cacheSize = -1;

    @SuppressWarnings("unchecked")
    public static List<Entry> all() {
        Object mapObj = UnleashedApi.call(UnleashedApi.method("archive.document.AnomalyDocuments", "client"), null);
        if (!(mapObj instanceof Map<?, ?> map)) return List.of();
        if (map.size() == cacheSize) return cache;
        List<Entry> out = new ArrayList<>();
        Class<?> docCls = UnleashedApi.cls("archive.document.AnomalyDocument");
        for (Map.Entry<?, ?> e : new TreeMap<>((Map<ResourceLocation, Object>) map).entrySet()) {
            Object doc = e.getValue();
            Object listed = UnleashedApi.call(UnleashedApi.method("archive.document.AnomalyDocument", "isListed"), doc);
            if (Boolean.FALSE.equals(listed)) continue;
            Object content = UnleashedApi.call(UnleashedApi.method("archive.document.DossierContent", "client", docCls), null, doc);
            if (content == null) continue;
            Object cls = UnleashedApi.call(UnleashedApi.method("archive.document.AnomalyDocument", "Classification"), doc);
            Object req = UnleashedApi.call(UnleashedApi.method("archive.document.DossierClearance", "required", docCls), null, doc);
            List<Section> sections = new ArrayList<>();
            Object secs = UnleashedApi.call(UnleashedApi.method("archive.document.DossierContent", "Sections"), content);
            if (secs instanceof List<?> l) {
                for (Object s : l) {
                    Object h = UnleashedApi.call(UnleashedApi.method("archive.document.DossierContent$Section", "Heading"), s);
                    Object t = UnleashedApi.call(UnleashedApi.method("archive.document.DossierContent$Section", "Text"), s);
                    String head = h instanceof Optional<?> o && o.isPresent() ? resolve(String.valueOf(o.get())) : "";
                    sections.add(new Section(head, t == null ? "" : String.valueOf(t)));
                }
            }
            out.add(new Entry(e.getKey().toString(),
                    str(UnleashedApi.call(UnleashedApi.method("archive.document.DossierContent", "Designation"), content)),
                    str(UnleashedApi.call(UnleashedApi.method("archive.document.DossierContent", "Title"), content)),
                    str(UnleashedApi.call(UnleashedApi.method("archive.document.DossierContent", "ObjectClass"), content)),
                    cls instanceof StringRepresentable sr ? sr.getSerializedName() : "unknown",
                    req instanceof Integer i ? i : 5, sections));
        }
        cache = out;
        cacheSize = map.size();
        return out;
    }

    private static String str(Object o) {
        return o == null ? "" : resolve(String.valueOf(o));
    }

    /** Some fields are language keys rather than text. */
    private static String resolve(String s) {
        if (!s.contains(" ") && s.contains(".")) return Component.translatable(s).getString();
        return s;
    }

    public static Entry get(String id) {
        for (Entry e : all()) if (e.id().equals(id)) return e;
        return null;
    }

    /** DossierClearance.clears: Omni (6) opens anything, otherwise you need at least the required level. */
    public static boolean clears(int have, int required) {
        return have >= 6 || have >= required;
    }

    /** A run of dossier text: plain, or redacted (SCP Unleashed's %r ... %r marks). Bold marks are dropped. */
    public record Run(String text, boolean redacted) {}

    public static List<Run> runs(String text) {
        List<Run> out = new ArrayList<>();
        String t = text.replace("**", "").replace("%%", "%");
        boolean red = false;
        int i = 0;
        while (true) {
            int j = t.indexOf("%r", i);
            String part = j < 0 ? t.substring(i) : t.substring(i, j);
            if (!part.isEmpty()) out.add(new Run(part, red));
            if (j < 0) break;
            red = !red;
            i = j + 2;
        }
        return out;
    }

    public static int classColor(String key) {
        return switch (key) {
            case "safe" -> 0xFF3AA860;
            case "euclid" -> 0xFFC8A030;
            case "keter" -> 0xFFC0383F;
            case "thaumiel" -> 0xFF3FB8FF;
            case "apollyon", "z_class" -> 0xFF8A1020;
            default -> 0xFF6F7C88;
        };
    }
}
