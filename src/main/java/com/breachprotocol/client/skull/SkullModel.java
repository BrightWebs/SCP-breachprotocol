package com.breachprotocol.client.skull;

import java.util.ArrayList;
import java.util.List;

/**
 * The Skull King, built the way its preview was: pixel units (16 a block), y up, facing +z; boxes and feather
 * planes hung on pivots, each pivot turned X, then Y, then Z. One instance per player drawn as it (each has its
 * own pose).
 */
public final class SkullModel {
    /** A pivot: where it sits on its parent, how it's turned, and what hangs on it. */
    public static final class Node {
        public float x, y, z, rx, ry, rz, sx = 1, sy = 1, sz = 1;
        /** A feather's own: the bone it grows from (0 arm, 1 forearm, 2 hand, 3 finger), where it points spread, and folded. */
        public int b;
        public float a, fold;
        public final List<Node> kids = new ArrayList<>();
        public final List<Part> parts = new ArrayList<>();
        Node(float x, float y, float z) { this.x = x; this.y = y; this.z = z; }
        public void rot(double a, double b, double c) { rx = (float) a; ry = (float) b; rz = (float) c; }
    }

    public enum Kind { BOX, PLANE, OCTA, FLAME, SOUL, EYE }

    /** A box (w,h,d) at (x,y,z) on its pivot, its own turn, a texture per face (+x,-x,+y,-y,+z,-z); or a flat feather; or a flame. */
    public static final class Part {
        public final Kind kind;
        public final float w, h, d, x, y, z, rx, ry, rz;
        public final Mat[] faces;
        public final int order;          // when it appears as the body assembles itself
        public float flameSize, phase;
        Part(Kind kind, float w, float h, float d, float x, float y, float z, float rx, float ry, float rz, Mat[] faces, int order) {
            this.kind = kind; this.w = w; this.h = h; this.d = d; this.x = x; this.y = y; this.z = z; this.rx = rx; this.ry = ry; this.rz = rz;
            this.faces = faces; this.order = order;
        }
    }

    /** What a surface is made of: its texture, whether it lights itself, and its glow layer if it has one. */
    public enum Mat {
        BONE("bone", false, null), BONE_D("bone_dark", false, null), GOLD("gold", true, null), RUBY("ruby", true, null), GEM("gem", true, null),
        FACE("skull_face", false, "skull_face_glow"), JAW("jaw", false, null), EMBER("ember", true, null), CRACK("crack", false, "crack_glow"),
        HORN("horn", false, null), MUZZLE("muzzle", false, null), VANE("vane", false, null), VANE_BURN("vane_burn", true, "vane_burn");
        public final String tex, glow;
        public final boolean bright;
        Mat(String tex, boolean bright, String glow) { this.tex = tex; this.bright = bright; this.glow = glow; }
    }

    public final Node root = new Node(0, 0, 0);
    public int partCount;

    // the pivots the pose moves
    public Node pelvis, spine, chest, neck, head, jaw, crown;
    public final Leg[] legs = new Leg[2];      // [0] left (+x), [1] right (-x)
    public final Arm[] arms = new Arm[2];
    public final Wing[] wings = new Wing[2];
    public Node halo, diamond;
    public final List<Part> crownFire = new ArrayList<>();

    public static final float THIGH = 10, SHIN = 10, HIP_Y = 22.6F, CW = 23, CH = 16, CD = 13;

    public static final class Leg { public Node hip, thigh, shin, foot; public float tx, sx; public int side; }
    public static final class Arm { public Node sh, upper, fore, hand, grip, claws; public int side; }
    public static final class Wing { public Node root, upper, fore, wrist, hand, digit; public final List<Node> feathers = new ArrayList<>(); public final List<Part> flames = new ArrayList<>(); public int side; }

    /** The tail: its vertebrae, root first (each one's turn is set by the tail's physics), and the blade at the end. */
    public final List<Node> tail = new ArrayList<>();
    public static final int TAIL = 18;
    /** Everything that hangs off the head (not drawn in first person: you're looking out of it). */
    public final java.util.Set<Node> headNodes = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    /** Everything on the wings (their feathers hang loose: they don't decide where the ground is). */
    public final java.util.Set<Node> wingNodes = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    /** The crown and all on it; and each tail vertebra's place along the tail (for the change, which grows them in turn). */
    public final java.util.Set<Node> crownNodes = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    public final java.util.Map<Node, Integer> tailIndex = new java.util.IdentityHashMap<>();

    // ------------------------------------------------------------------ building

    private Node pivot(Node parent, double x, double y, double z) { Node n = new Node((float) x, (float) y, (float) z); parent.kids.add(n); return n; }
    private Node pivot(Node parent, double x, double y, double z, double rx, double ry, double rz) { Node n = pivot(parent, x, y, z); n.rot(rx, ry, rz); return n; }

    private Part box(Node n, double w, double h, double d, double x, double y, double z, Mat m) { return box(n, w, h, d, x, y, z, 0, 0, 0, m, m, m, m, m, m); }
    private Part boxR(Node n, double w, double h, double d, double x, double y, double z, double rx, double ry, double rz, Mat m) { return box(n, w, h, d, x, y, z, rx, ry, rz, m, m, m, m, m, m); }
    private Part box(Node n, double w, double h, double d, double x, double y, double z, double rx, double ry, double rz, Mat... f) {
        Part p = new Part(Kind.BOX, (float) w, (float) h, (float) d, (float) x, (float) y, (float) z, (float) rx, (float) ry, (float) rz, f, partCount++);
        n.parts.add(p);
        return p;
    }
    private Part special(Node n, Kind k, double w, double h, double x, double y, double z, Mat m) {
        Part p = new Part(k, (float) w, (float) h, 0, (float) x, (float) y, (float) z, 0, 0, 0, new Mat[]{m}, partCount++);
        n.parts.add(p);
        return p;
    }

    private void bone(Node parent, double len, double thick, Mat m, Mat knob) {
        box(parent, thick, len - thick, thick * 0.8, 0, -len / 2, 0, m);
        box(parent, thick + 1.4, thick * 0.9, thick + 0.6, 0, -thick * 0.45, 0, knob);
        box(parent, thick + 1.2, thick * 0.9, thick + 0.6, 0, -len + thick * 0.45, 0, knob);
    }
    private void bone(Node parent, double len, double thick) { bone(parent, len, thick, Mat.BONE, Mat.BONE_D); }

    private final java.util.Random rnd = new java.util.Random(7);

    public SkullModel() {
        Mat B = Mat.BONE, BD = Mat.BONE_D, H = Mat.HORN;
        // ---- legs: the feet flat on the ground, four clawed toes forward
        for (int i = 0; i < 2; i++) {
            int side = i == 0 ? 1 : -1;
            Leg lg = new Leg();
            lg.side = side;
            lg.tx = -0.12F; lg.sx = 0.18F;
            lg.hip = pivot(root, side * 4.5, HIP_Y, 0);
            lg.thigh = pivot(lg.hip, 0, 0, 0, lg.tx, 0, side * -0.04);
            bone(lg.thigh, THIGH, 4);
            box(lg.thigh, 3.6, 3, 1.6, 0, -THIGH + 0.4, 2.2, BD);
            lg.shin = pivot(lg.thigh, 0, -THIGH, 0, lg.sx, 0, side * 0.04);
            box(lg.shin, 2.2, SHIN - 1, 2.2, -1.1, -SHIN / 2, 0.3, B);
            box(lg.shin, 1.6, SHIN - 1, 1.6, 1.2, -SHIN / 2, -0.5, BD);
            box(lg.shin, 4.6, 2, 4.4, 0, -SHIN + 0.6, 0, BD);
            lg.foot = pivot(lg.shin, 0, -SHIN, 0, -(lg.tx + lg.sx), 0, 0);
            double G = -2.3;
            box(lg.foot, 5.4, 1.8, 4.2, 0, G + 0.9, 1.2, BD);
            box(lg.foot, 2.6, 1.6, 2.6, 0, G + 0.8, -1.8, B);
            box(lg.foot, 1.4, 1.2, 2.2, 0, G + 0.6, -3.6, H);
            double[][] toes = {{-2.1, -0.28, 3.6}, {-0.7, -0.09, 4.4}, {0.7, 0.09, 4.4}, {2.1, 0.28, 3.6}};
            for (double[] tz : toes) {
                Node t = pivot(lg.foot, tz[0], G + 0.7, 3, 0, tz[1], 0);
                double L = tz[2];
                box(t, 1.5, 1.4, L, 0, 0, L / 2, B);
                box(t, 1.9, 1.7, 1.5, 0, 0.15, L, BD);
                Node t2 = pivot(t, 0, 0, L + 0.4);
                box(t2, 1.3, 1.2, 2.2, 0, 0, 1.1, B);
                Node c = pivot(t2, 0, 0.2, 2.1, 0.6, 0, 0);
                box(c, 1.1, 1, 2.2, 0, -0.1, 1, H);
            }
            legs[i] = lg;
        }
        // ---- pelvis, spine
        pelvis = pivot(root, 0, HIP_Y + 1, 0);
        box(pelvis, 12, 4, 7, 0, 0.5, 0, B);
        box(pelvis, 4, 5, 4, 6, 1.5, -0.5, BD);
        box(pelvis, 4, 5, 4, -6, 1.5, -0.5, BD);
        // ---- the tail: a long whip of vertebrae back from the pelvis, a bone blade at its end
        {
            Node p = pivot(pelvis, 0, 0, -3.5, -0.7, 0, 0);
            for (int i = 0; i < TAIL; i++) {
                double k = i / (double) TAIL, t = 3.4 - 2.3 * k, L = 2.7;
                box(p, t, t, L, 0, 0, -L / 2, B);
                box(p, t + 0.9, t + 0.6, 0.9, 0, 0, -0.2, BD);
                if (i % 2 == 0 && i < TAIL - 2) box(p, 0.8, 1.2 + t * 0.5, 0.9, 0, t / 2 + 0.5, -L / 2, H);
                tail.add(p);
                p = pivot(p, 0, 0, -L, 0.05, 0, 0);
            }
            box(p, 0.8, 3.2, 4.2, 0, 0, -1.6, H);
            box(p, 0.8, 1.6, 2.4, 0, 0, -4.4, H);
            box(p, 1.4, 1.4, 1.4, 0, 0, 0, BD);
            tail.add(p);
        }
        spine = pivot(pelvis, 0, 2.5, -1, 0.14, 0, 0);
        for (int k = 0; k < 4; k++) { box(spine, 3.2, 1.6, 3.2, 0, k * 2.1 + 0.8, 0, B); box(spine, 1.2, 1.2, 2.2, 0, k * 2.1 + 0.8, -2.2, BD); }
        // ---- the ribcage round an ember heart
        chest = pivot(spine, 0, 8.2, 0, 0.16, 0, 0);
        box(chest, 4, CH, 4, 0, CH / 2, -CD / 2 + 1.5, BD);
        box(chest, 3.4, CH - 3, 2, 0, CH / 2, CD / 2 - 0.6, B);
        for (int k = 0; k < 6; k++) {
            double y = 2 + k * 2.4, wk = CW - (k < 2 ? 6 : k < 4 ? 2 : 0) - (k == 5 ? 3 : 0);
            box(chest, wk, 1.5, 1.6, 0, y, CD / 2 - 0.6, B);
            box(chest, 1.6, 1.5, CD, wk / 2 - 0.8, y, 0, B);
            box(chest, 1.6, 1.5, CD, -wk / 2 + 0.8, y, 0, B);
            box(chest, wk, 1.5, 1.6, 0, y, -CD / 2 + 0.8, BD);
        }
        box(chest, CW + 1, 3, CD + 1, 0, CH + 0.5, 0, Mat.CRACK);
        box(chest, 5, 5, 5, 0, 8, 0, Mat.EMBER);
        // ---- arms, blocky fists
        for (int i = 0; i < 2; i++) {
            int side = i == 0 ? 1 : -1;
            Arm a = new Arm();
            a.side = side;
            a.sh = pivot(chest, side * 14.5, CH - 1, 0.5);
            box(a.sh, 10, 8, 10, side * 0.5, 1.5, 0, 0, 0, 0, B, B, Mat.CRACK, BD, B, B);
            box(a.sh, 8, 2, 8, side * 0.5, 6, 0, BD);
            double[][] spikes = {{-3, 4}, {0, 6}, {3, 4}};
            for (double[] sp : spikes) boxR(a.sh, 1.8, sp[1], 1.8, side * 2, 6.5 + sp[1] / 2, sp[0], sp[0] * 0.08, 0, -side * 0.4, H);
            a.upper = pivot(a.sh, side * 1.5, -2.5, 0, -0.3, 0, side * -0.14);
            bone(a.upper, 14, 6);
            a.fore = pivot(a.upper, 0, -14, 0, -0.15, 0, side * 0.05);
            box(a.fore, 3, 13, 3, -1.6, -6.5, 0, B);
            box(a.fore, 2.6, 13, 2.6, 1.7, -6.5, -0.3, BD);
            box(a.fore, 8, 3, 7, 0, -12.6, 0, BD);
            a.hand = pivot(a.fore, 0, -14, 0);
            box(a.hand, 9, 6, 8, 0, -3, 0, B);
            a.grip = pivot(a.hand, 0, -5.2, 1.2);
            // claws, out of the fist only when it climbs (scaled to nothing otherwise)
            a.claws = pivot(a.hand, 0, -5.4, 3.4);
            a.claws.sx = a.claws.sy = a.claws.sz = 0;
            for (int c = 0; c < 4; c++) {
                Node cl = pivot(a.claws, -3.3 + c * 2.2, 0, 0, 0.45, 0, 0);
                box(cl, 1.2, 1.1, 2.6, 0, 0, 1.2, H);
                Node tip = pivot(cl, 0, 0, 2.5, 0.7, 0, 0);
                box(tip, 0.9, 0.9, 1.8, 0, -0.1, 0.8, H);
            }
            arms[i] = a;
        }
        // ---- the head: a horned skull, crowned
        neck = pivot(chest, 0, CH - 1, CD / 2 - 2, 0.1, 0, 0);
        box(neck, 3, 4, 3, 0, 1.5, 0.5, BD);
        head = pivot(neck, 0, 3, 1.5, -0.2, 0, 0);
        head.sx = head.sy = head.sz = 1.15F;
        box(head, 12, 9, 11, 0, 4.5, 0, 0, 0, 0, B, B, Mat.CRACK, BD, Mat.FACE, B);
        box(head, 14, 2.5, 3, 0, 6.6, 5.2, BD);
        box(head, 8, 4, 4, 0, 1.2, 6.5, 0, 0, 0, B, B, B, BD, Mat.MUZZLE, B);
        jaw = pivot(head, 0, 0.5, -2, 0.18, 0, 0);
        box(jaw, 11, 2.5, 10, 0, -1.2, 4.5, 0, 0, 0, BD, BD, BD, BD, Mat.JAW, BD);
        for (int sd : new int[]{1, -1}) {
            Node t = pivot(jaw, sd * 4.6, -0.5, 8.5, -0.3, 0, sd * -0.3);
            box(t, 1.6, 4, 1.6, 0, 2, 0, H);
            Node t2 = pivot(t, 0, 4, 0, -0.5, 0, sd * 0.2);
            box(t2, 1.2, 3, 1.2, 0, 1.5, 0, H);
        }
        for (int sd : new int[]{1, -1}) {
            Node p = pivot(head, sd * 5.6, 7.5, 0.5, 0, 0, sd * -0.9);
            double[][] segs = {{3.2, 4, -0.35}, {2.8, 4, -0.45}, {2.3, 3.6, -0.5}, {1.8, 3.2, -0.5}, {1.2, 3, -0.4}};
            for (double[] sg : segs) { box(p, sg[0], sg[1], sg[0], 0, sg[1] / 2, 0, H); p = pivot(p, 0, sg[1], 0, sg[2], 0, sd * 0.12); }
        }
        // the eye sockets' own flames (and where heat vision comes from in this form)
        for (int sd : new int[]{1, -1}) special(head, Kind.EYE, 2.2, 4.2, sd * 2.9, 6, 6.2, null);
        // the crown
        crown = pivot(head, 0, 9.4, 0);
        box(crown, 13, 2, 12, 0, 0, 0, Mat.GOLD);
        int[] spikeH = {3, 5, 7, 5, 3};
        for (int i = 0; i < 5; i++) {
            double x = (i - 2) * 2.8, h = spikeH[i];
            box(crown, 1.6, h, 1.2, x, 1 + h / 2, 5.6, Mat.GOLD);
            box(crown, 1.2, h * 0.7, 1.2, x, 1 + h * 0.35, -5.6, Mat.GOLD);
        }
        box(crown, 1.6, 1.6, 0.8, -4.2, 0.2, 6.3, Mat.RUBY);
        box(crown, 1.6, 1.6, 0.8, 4.2, 0.2, 6.3, Mat.RUBY);
        box(crown, 1.2, 1.2, 0.8, -6.3, 0.2, 3, Mat.RUBY);
        box(crown, 1.2, 1.2, 0.8, 6.3, 0.2, 3, Mat.RUBY);
        diamond = pivot(crown, 0, 0.6, 6.4);
        diamond.sx = 1; diamond.sy = 1.35F; diamond.sz = 0.7F;
        special(diamond, Kind.OCTA, 1.6, 1.6, 0, 0, 0, Mat.GEM);
        box(crown, 2.6, 0.8, 0.8, 0, -0.6, 6.3, Mat.GOLD);
        for (int k = 0; k < 5; k++) { Part f = special(crown, Kind.FLAME, 2.8, 0, (k - 2) * 0.7, 2.6 + k % 2 * 0.4, 5.6, null); f.flameSize = 2.8F; f.phase = rnd.nextFloat() * 6; crownFire.add(f); }
        for (int k = 0; k < 9; k++) { Part f = special(crown, Kind.FLAME, 3.4, 0, (rnd.nextFloat() - .5) * 8, 1.6, (rnd.nextFloat() - .5) * 8, null); f.flameSize = 3.4F; f.phase = rnd.nextFloat() * 6; crownFire.add(f); }
        halo = pivot(crown, 0, 11, 1);
        box(halo, 2.2, 2.2, 2.2, 0, 0, 0, Mat.GEM);
        // ---- the wings: bare bone, black feathers burning at their ends
        for (int i = 0; i < 2; i++) wings[i] = wing(i == 0 ? 1 : -1);
        mark(neck);
        for (Wing w : wings) markWing(w.root);
        markCrown(crown);
        for (int i = 0; i < tail.size(); i++) tailIndex.put(tail.get(i), i);
    }

    private void markCrown(Node n) { crownNodes.add(n); for (Node k : n.kids) markCrown(k); }

    private void markWing(Node n) { wingNodes.add(n); for (Node k : n.kids) markWing(k); }

    private void mark(Node n) { headNodes.add(n); for (Node k : n.kids) mark(k); }

    /** A real wing's skeleton along its top edge (arm, forearm, hand, one long finger), the feathers hanging down from it in rows. */
    private Wing wing(int side) {
        Wing w = new Wing();
        w.side = side;
        w.root = pivot(chest, side * 5.5, CH - 2.5, -CD / 2 - 2.6);
        box(w.root, 4, 4, 4, 0, 0, 0, Mat.BONE_D);
        w.upper = pivot(w.root, 0, 0, 0);
        double UL = 12, FL = 15, HL = 11, DL = 9;
        box(w.upper, UL, 3, 3, side * UL / 2, 0, 0, Mat.BONE);
        box(w.upper, 4, 3.6, 3.6, side * UL, 0, 0, Mat.BONE_D);
        w.fore = pivot(w.upper, side * UL, 0, 0);
        box(w.fore, FL, 2.4, 2.4, side * FL / 2, 0, 0.6, Mat.BONE);
        box(w.fore, FL, 1.6, 1.6, side * FL / 2, 0, -0.8, Mat.BONE_D);
        w.wrist = pivot(w.fore, side * FL, 0, 0);
        box(w.wrist, 4, 4, 3.4, 0, 0, 0, Mat.BONE_D);
        Node hook = pivot(w.wrist, 0, 1.6, 0, 0, 0, side * 0.3);
        box(hook, 1.6, 4, 1.6, 0, 2, 0, Mat.HORN);
        Node hook2 = pivot(hook, 0, 4, 0, 0, 0, side * -1.1);
        box(hook2, 1.2, 3, 1.2, 0, 1.5, 0, Mat.HORN);
        w.hand = pivot(w.wrist, 0, 0, 0);
        box(w.hand, HL, 2.4, 2.2, side * HL / 2, 0, 0, Mat.BONE);
        box(w.hand, 2.8, 3, 3, side * HL, 0, 0, Mat.BONE_D);
        w.digit = pivot(w.hand, side * HL, 0, 0, 0, 0, side * -0.12);
        box(w.digit, DL, 1.8, 1.8, side * DL / 2, 0, 0, Mat.BONE);
        box(w.digit, 3, 1.4, 1.4, side * (DL + 1), 0, 0, Mat.HORN);
        // tertials along the arm, secondaries along the forearm, the long primaries from the hand and finger
        for (int i = 0; i < 4; i++) feather(w, w.upper, 0, 2 + i * 3.2, 11 + i * 1.2, -1.72 + i * 0.03, -0.9 - i * 0.02, 4.6, i % 2 == 1);
        for (int i = 0; i < 6; i++) feather(w, w.fore, 1, 1 + i * 2.8, 16 + i * 1.3, -1.62 + i * 0.035, -0.9 - i * 0.02, 5, i % 2 == 0);
        for (int i = 0; i < 5; i++) feather(w, w.hand, 2, 1 + i * 2.4, 25 + i * 2.2, -1.4 + i * 0.16, -1.1 - i * 0.03, 5.2, true);
        for (int i = 0; i < 4; i++) feather(w, w.digit, 3, 1 + i * 2.6, 33 - i * 1.5, -0.62 + i * 0.13, -1.3 - i * 0.03, 5.2, true);
        // coverts: a short row over the roots of the long ones
        for (int i = 0; i < 4; i++) feather(w, w.upper, 0, 1.5 + i * 3, 6.5, -1.66, -0.3, 4.2, false);
        for (int i = 0; i < 5; i++) feather(w, w.fore, 1, 1 + i * 3.2, 8, -1.58, -0.3, 4.4, false);
        for (int i = 0; i < 4; i++) feather(w, w.hand, 2, 1 + i * 2.8, 10, -1.25 + i * 0.1, -0.4, 4.4, false);
        return w;
    }

    /** One feather, solid: a quill, then the vane in blocks (narrow at the root, full in the middle, narrowing to a burning tip). */
    private void feather(Wing w, Node bone, int b, double x, double len, double a, double z, double width, boolean fire) {
        int side = w.side;
        Node fp = pivot(bone, side * x, -0.6, z);
        fp.b = b; fp.a = (float) a; fp.fold = (float) (-1.5 - x * 0.004 - b * 0.03);
        box(fp, len * 0.9, 0.9, 1.4, side * len * 0.45, 0, 0, Mat.HORN);
        double[][] segs = {{0, 0.22, 0.55}, {0.22, 0.72, 1}, {0.72, 0.9, 0.72}, {0.9, 1, 0.38}};
        for (int i = 0; i < segs.length; i++) {
            double l = (segs[i][1] - segs[i][0]) * len;
            box(fp, l, width * segs[i][2], 0.8, side * (segs[i][0] * len + l / 2), 0, 0, i < 2 ? Mat.VANE : Mat.VANE_BURN);
        }
        w.feathers.add(fp);
        if (fire) { Part fl = special(fp, Kind.FLAME, 2.6, 0, side * len, 0, 0.2, null); fl.flameSize = 2.6F; fl.phase = rnd.nextFloat() * 6; w.flames.add(fl); }
    }
}
