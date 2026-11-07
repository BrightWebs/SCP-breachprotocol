package com.breachprotocol.entity;

import java.util.UUID;

/**
 * Anyone drawn with the MCA-style body: staff and Overseers. Their looks come from genes fixed by
 * who they are, so every client sees the same person.
 */
public interface Person {
    UUID getUUID();

    boolean isFemale();

    /** Her figure, 0 (slight) to 1 (full). */
    float bustSize();

    boolean isTalking();

    default boolean isWaving() { return false; }

    default boolean isFrightened() { return false; }

    /** The clothing layer (uniform or suit), as a texture path in this mod. */
    String uniform();

    /** White or grey hair (ARGB), or 0 for their natural colour. */
    int greyHair();

    default float gene(int salt) { return geneOf(getUUID(), salt); }

    /** A gene fixed by identity alone (for anyone without parents on record). */
    static float geneOf(UUID id, int salt) {
        long x = id.getMostSignificantBits() * 31 + id.getLeastSignificantBits() * (salt * 2654435761L + 1);
        x ^= x >>> 33; x *= 0xff51afd7ed558ccdL; x ^= x >>> 33; x *= 0xc4ceb9fe1a85ec53L; x ^= x >>> 33;
        return ((x >>> 11) & 0xFFFFFF) / (float) 0xFFFFFF;
    }

    private float heritage() { return gene(3) * 1.5F - 0.25F; }

    private static float clamp01(float f) { return Math.max(0, Math.min(1, f)); }

    default float melanin() { return clamp01((gene(2) - 0.5F) * 0.35F + heritage() * 0.4F + 0.1F); }

    default float hemoglobin() { return clamp01(((gene(4) - 0.5F) * 0.35F + heritage() * 0.4F + 0.1F) * 0.5F); }

    default float eumelanin() { return gene(5); }

    default float pheomelanin() { return gene(6); }

    default float faceGene() { return gene(7); }

    default float eyeBrightness() { return gene(12); }

    default int skinIndex() { return Math.min(4, (int) (gene(1) * 5)); }

    default int hairIndex(int count) { return Math.min(count - 1, (int) (gene(11) * count)); }

    default float heightScale() { return (isFemale() ? 0.94F : 0.97F) + gene(9) * 0.09F; }

    default float widthScale() { return (isFemale() ? 0.95F : 1.0F) * (0.94F + gene(10) * 0.12F); }
}
