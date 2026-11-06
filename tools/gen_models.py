"""Generates entity model Java classes + matching original textures for Breach Protocol.

Each model is described once (parts, cubes, materials). UVs are packed automatically,
so geometry and texture can never drift out of sync.
"""
import os, random, math
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JAVA_DIR = os.path.join(ROOT, "src/main/java/com/breachprotocol/client/model")
TEX_DIR = os.path.join(ROOT, "src/main/resources/assets/breachprotocol/textures/entity")

# ----------------------------------------------------------------- materials
def clamp(c): return max(0, min(255, int(c)))

def noisy(base, n, rng):
    d = rng.randint(-n, n)
    return (clamp(base[0] + d), clamp(base[1] + d), clamp(base[2] + d), 255)

def mat_fill(img, x, y, w, h, base, n, rng, pores=None, streak=None):
    px = img.load()
    for i in range(w):
        for j in range(h):
            px[x + i, y + j] = noisy(base, n, rng)
            if pores and rng.random() < pores[0]:
                px[x + i, y + j] = noisy(pores[1], 6, rng)
    if streak:  # vertical stains running down
        col, chance = streak
        for i in range(w):
            if rng.random() < chance:
                length = rng.randint(1, max(1, h))
                start = rng.randint(0, max(0, h - length))
                for j in range(start, start + length):
                    px[x + i, y + j] = noisy(col, 8, rng)

MATS = {
    "concrete": dict(base=(150, 146, 134), n=12, pores=(0.08, (105, 102, 95)), streak=((112, 70, 45), 0.12)),
    "concrete_dark": dict(base=(118, 114, 106), n=10, pores=(0.1, (85, 82, 76))),
    "rust": dict(base=(116, 60, 30), n=14),
    "skin096": dict(base=(222, 216, 210), n=7, pores=(0.05, (196, 186, 182))),
    "robe": dict(base=(34, 31, 36), n=5, streak=((24, 22, 26), 0.25)),
    "mask": dict(base=(228, 224, 208), n=4),
    "glove": dict(base=(22, 21, 24), n=3),
    "leather": dict(base=(92, 56, 30), n=8),
    "corrode": dict(base=(30, 24, 20), n=6, pores=(0.12, (66, 46, 28)), streak=((14, 10, 8), 0.3)),
    "corrode_skin": dict(base=(58, 48, 40), n=8, pores=(0.15, (36, 28, 22))),
    "goo": dict(base=(246, 142, 40), n=9, pores=(0.06, (255, 186, 92))),
    "gown": dict(base=(150, 184, 196), n=7, streak=((120, 150, 160), 0.15)),
    "grey_skin": dict(base=(126, 134, 120), n=9, pores=(0.08, (98, 104, 92))),
    "dark": dict(base=(16, 14, 14), n=3),
    "radiant": dict(base=(255, 236, 170), n=10, pores=(0.12, (255, 250, 225))),
    "robe_gold": dict(base=(232, 178, 64), n=10, streak=((255, 220, 120), 0.2)),
    "fire": dict(base=(255, 128, 20), n=30, pores=(0.25, (255, 214, 80)), streak=((220, 50, 10), 0.3)),
    "blade": dict(base=(255, 246, 210), n=6, pores=(0.2, (255, 170, 40))),
    "hilt": dict(base=(90, 60, 30), n=6),
    "suit": dict(base=(20, 20, 24), n=3),
}

# --------------------------------------------------------- face decorators
def deco_173_face(img, x, y, w, h, rng):
    """Original faceplate: crack + two rust drips + a green slash. (Not based on any existing artwork.)"""
    px = img.load()
    for j in range(h):
        px[x + 1, y + j] = noisy((150, 34, 26), 10, rng) if j < h - rng.randint(0, 2) else px[x + 1, y + j]
        px[x + w - 2, y + j] = noisy((150, 34, 26), 10, rng) if j < h - 1 else px[x + w - 2, y + j]
    for i in range(w):
        jj = y + h // 2 + (1 if i % 3 == 0 else 0)
        px[x + i, jj] = noisy((44, 96, 48), 10, rng)
    cx = x + w // 2
    for j in range(h):
        px[min(x + w - 1, cx + (j % 2)), y + j] = (70, 66, 60, 255)

def deco_096_face(img, x, y, w, h, rng):
    px = img.load()
    px[x + 1, y + 2] = (30, 26, 26, 255); px[x + w - 2, y + 2] = (30, 26, 26, 255)
    px[x + 1, y + 3] = (70, 60, 60, 255); px[x + w - 2, y + 3] = (70, 60, 60, 255)
    for i in range(1, w - 1):
        px[x + i, y + h - 1] = (90, 30, 30, 255)

def deco_096_jaw_front(img, x, y, w, h, rng):
    px = img.load()
    for i in range(w):
        px[x + i, y] = (238, 232, 210, 255) if i % 2 == 0 else (200, 190, 170, 255)

def deco_mouth(img, x, y, w, h, rng):
    px = img.load()
    for i in range(w):
        for j in range(h):
            px[x + i, y + j] = noisy((92, 18, 22), 10, rng)

def deco_ribs(img, x, y, w, h, rng):
    px = img.load()
    for j in range(2, h - 3, 2):
        for i in range(1, w - 1):
            px[x + i, y + j] = noisy((196, 186, 180), 4, rng)

def deco_049_mask(img, x, y, w, h, rng):
    px = img.load()
    # hood shadow frame
    for i in range(w):
        px[x + i, y] = noisy((34, 31, 36), 4, rng)
    for j in range(h):
        px[x, y + j] = noisy((34, 31, 36), 4, rng); px[x + w - 1, y + j] = noisy((34, 31, 36), 4, rng)
    # goggle lenses with brass rims
    for (lx, ly) in ((x + 1, y + 2), (x + w - 4, y + 2)):
        for i in range(3):
            for j in range(3):
                px[lx + i, ly + j] = (150, 118, 52, 255)
        px[lx + 1, ly + 1] = (18, 34, 28, 255)
    px[x + 2, y + 3] = (120, 190, 160, 255)

def deco_hood_front(img, x, y, w, h, rng):
    px = img.load()
    for i in range(1, w - 1):
        for j in range(1, h):
            px[x + i, y + j] = (0, 0, 0, 0)

def deco_belt(img, x, y, w, h, rng):
    px = img.load()
    for i in range(w):
        px[x + i, y + h - 2] = noisy((70, 44, 24), 6, rng)
    px[x + w // 2, y + h - 2] = (180, 150, 70, 255)

def deco_106_face(img, x, y, w, h, rng):
    px = img.load()
    for i in range(1, w - 1):
        px[x + i, y + h - 2] = (10, 6, 6, 255)
        if i % 2 == 0: px[x + i, y + h - 3] = (12, 8, 8, 255)
    px[x + 2, y + 2] = (196, 188, 150, 255); px[x + w - 3, y + 2] = (196, 188, 150, 255)
    for i in range(1, w - 1):
        px[x + i, y + 1] = noisy((40, 32, 26), 4, rng)

def deco_999_face(img, x, y, w, h, rng):
    px = img.load()
    ex = (x + w // 2 - 3, x + w // 2 + 2)
    for e in ex:
        for i in range(2):
            for j in range(2):
                px[e + i, y + 2 + j] = (20, 14, 10, 255)
        px[e, y + 2] = (255, 255, 255, 255)
    mx = x + w // 2
    for i in range(-2, 3):
        px[mx + i, y + 6 + (1 if abs(i) < 2 else 0)] = (120, 40, 20, 255)
    px[x + 1, y + 5] = (255, 120, 120, 255); px[x + w - 2, y + 5] = (255, 120, 120, 255)

def deco_0492_face(img, x, y, w, h, rng):
    px = img.load()
    px[x + 2, y + 3] = (230, 230, 220, 255); px[x + 5, y + 3] = (230, 230, 220, 255)
    for i in range(w):
        px[x + i, y + 5] = (40, 30, 30, 255) if i % 2 == 0 else (90, 90, 80, 255)
    for j in range(0, h, 2):
        px[x + 6, y + j] = (40, 30, 30, 255)

def deco_guardian_face(img, x, y, w, h, rng):
    px = img.load()
    for i in range(w):
        for j in range(h):
            px[x + i, y + j] = noisy((255, 244, 214), 8, rng)
    for e in (x + 1, x + w - 3):
        px[e, y + 3] = (255, 255, 255, 255); px[e + 1, y + 3] = (255, 255, 255, 255)
        px[e, y + 4] = (255, 200, 80, 255); px[e + 1, y + 4] = (255, 200, 80, 255)

def deco_halo(img, x, y, w, h, rng):
    px = img.load()
    for i in range(w):
        for j in range(h):
            if i in (0, w - 1) or j in (0, h - 1):
                px[x + i, y + j] = (255, 240, 150, 255)
            else:
                px[x + i, y + j] = (0, 0, 0, 0)

DECOS = {k: v for k, v in globals().items() if k.startswith("deco_")}

# --------------------------------------------------------------- packing

def box_dims(w, h, d):
    return 2 * (w + d), d + h

def pack(cubes, tex_w):
    x = y = shelf = 0
    for c in cubes:
        bw, bh = box_dims(*c["size"])
        if x + bw > tex_w:
            x = 0; y += shelf; shelf = 0
        c["uv"] = (x, y)
        x += bw; shelf = max(shelf, bh)
    return y + shelf

def paint_cube(img, c, rng):
    u, v = c["uv"]; w, h, d = c["size"]
    m = MATS[c["mat"]]
    faces = {
        "top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h),
    }
    for name, (fx, fy, fw, fh) in faces.items():
        fm = MATS[c.get("face_mats", {}).get(name, c["mat"])]
        mat_fill(img, fx, fy, fw, fh, fm["base"], fm["n"], rng, fm.get("pores"), fm.get("streak"))
        for deco in c.get("deco", {}).get(name, []):
            DECOS["deco_" + deco](img, fx, fy, fw, fh, rng)

# --------------------------------------------------------------- java emit

def jf(v):
    s = ("%.3f" % v).rstrip("0").rstrip(".")
    if s == "-0": s = "0"
    return s + "F"

def emit_java(model):
    cls = model["cls"]; ent = model["entity"]
    lines = []
    fields = [p["name"] for p in model["parts"] if p.get("field", True)]
    parts_by = {p["name"]: p for p in model["parts"]}
    def camel(n):
        a = n.split("_"); return a[0] + "".join(x.title() for x in a[1:])
    # constructor child lookups follow parent chain
    def path(p):
        chain = []
        while p:
            chain.append(p["name"]); p = parts_by.get(p.get("parent"))
        return list(reversed(chain))
    ctor = []
    for p in model["parts"]:
        expr = "root" + "".join('.getChild("%s")' % n for n in path(p))
        ctor.append("        this.%s = %s;" % (camel(p["name"]), expr))
    body = []
    body.append("        MeshDefinition mesh = new MeshDefinition();")
    body.append("        PartDefinition root = mesh.getRoot();")
    for p in model["parts"]:
        parent = "root" if not p.get("parent") else camel(p["parent"]) + "Def"
        cb = "CubeListBuilder.create()"
        for c in p["cubes"]:
            u, v = c["uv"]; ox, oy, oz = c["origin"]; w, h, d = c["size"]
            cb += ".texOffs(%d, %d)" % (u, v)
            if c.get("inflate"):
                cb += ".addBox(%s, %s, %s, %d, %d, %d, new CubeDeformation(%s))" % (jf(ox), jf(oy), jf(oz), w, h, d, jf(c["inflate"]))
            else:
                cb += ".addBox(%s, %s, %s, %d, %d, %d)" % (jf(ox), jf(oy), jf(oz), w, h, d)
        px, py, pz = p["pivot"]; rx, ry, rz = p.get("rot", (0, 0, 0))
        pose = "PartPose.offsetAndRotation(%s, %s, %s, %s, %s, %s)" % tuple(jf(a) for a in (px, py, pz, rx, ry, rz))
        body.append('        PartDefinition %sDef = %s.addOrReplaceChild("%s", %s, %s);' % (camel(p["name"]), parent, p["name"], cb, pose))
    body.append("        return LayerDefinition.create(mesh, %d, %d);" % (model["tw"], model["th"]))
    src = f"""// GENERATED by tools/gen_models.py - edit the generator, not this file.
package com.breachprotocol.client.model;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.entity.{ent};
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.util.Mth;

@SuppressWarnings("unused")
public class {cls} extends HierarchicalModel<{ent}> {{
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(BreachProtocol.id("{model['id']}"), "main");
    private final ModelPart root;
{chr(10).join('    private final ModelPart %s;' % camel(p['name']) for p in model['parts'])}

    public {cls}(ModelPart root) {{
        this.root = root;
{chr(10).join(ctor)}
    }}

    public static LayerDefinition createBodyLayer() {{
{chr(10).join(body)}
    }}

    @Override
    public ModelPart root() {{
        return root;
    }}

    @Override
    public void setupAnim({ent} entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {{
        this.root().getAllParts().forEach(ModelPart::resetPose);
{model['anim']}
    }}
}}
"""
    with open(os.path.join(JAVA_DIR, cls + ".java"), "w") as f:
        f.write(src)

def build(model):
    rng = random.Random(model["id"])
    cubes = [c for p in model["parts"] for c in p["cubes"]]
    th = pack(cubes, model["tw"])
    model["th"] = 1 << max(5, (th - 1).bit_length())
    img = Image.new("RGBA", (model["tw"], model["th"]), (0, 0, 0, 0))
    for c in cubes:
        paint_cube(img, c, rng)
    img.save(os.path.join(TEX_DIR, model["id"] + ".png"))
    emit_java(model)

def C(origin, size, mat, **kw):
    d = dict(origin=origin, size=size, mat=mat); d.update(kw); return d

# ================================================================ MODELS
MODELS = []

MODELS.append(dict(id="scp_173", cls="Scp173Model", entity="Scp173Entity", tw=64, parts=[
    dict(name="leg_l", pivot=(-2, 16, 0), cubes=[C((-1.5, 0, -1.5), (3, 8, 3), "concrete")]),
    dict(name="leg_r", pivot=(2, 16, 0), cubes=[C((-1.5, 0, -1.5), (3, 8, 3), "concrete")]),
    dict(name="torso", pivot=(0, 16, 0), rot=(0.1, 0, 0), cubes=[C((-4, -11, -2.5), (8, 11, 5), "concrete")]),
    dict(name="head", parent="torso", pivot=(0, -11, 0), rot=(0.08, 0, 0.14), cubes=[
        C((-3.5, -8, -3.5), (7, 8, 7), "concrete"),
        C((-2.5, -6.5, -4.5), (5, 5, 1), "concrete", face_mats={"front": "concrete_dark"}, deco={"front": ["173_face"]}),
    ]),
    dict(name="arm_l", parent="torso", pivot=(-5.5, -10, 0), rot=(-0.12, 0, 0.1), cubes=[
        C((-1.5, -1, -1.5), (3, 14, 3), "concrete"), C((-1.5, 13, -1.5), (3, 2, 3), "concrete_dark")]),
    dict(name="arm_r", parent="torso", pivot=(5.5, -10, 0), rot=(-0.05, 0, -0.1), cubes=[
        C((-1.5, -1, -1.5), (3, 14, 3), "concrete"), C((-1.5, 13, -1.5), (3, 2, 3), "concrete_dark")]),
    dict(name="rebar_a", parent="torso", pivot=(-3, -11, 0), rot=(0.2, 0, -0.35), cubes=[C((-0.5, -4, -0.5), (1, 4, 1), "rust")]),
    dict(name="rebar_b", parent="torso", pivot=(3, -11, 1), rot=(-0.3, 0, 0.45), cubes=[C((-0.5, -3, -0.5), (1, 3, 1), "rust")]),
    dict(name="rebar_c", parent="torso", pivot=(1, -6, 2.5), rot=(0.9, 0.3, 0), cubes=[C((-0.5, -0.5, 0), (1, 1, 3), "rust")]),
], anim="""        // A statue: it never animates while you can see it. The only tell is the head angle.
        this.head.zRot = 0.14F + (entity.isFrozen() ? 0F : Mth.sin(ageInTicks * 3F) * 0.04F);"""))

MODELS.append(dict(id="scp_096", cls="Scp096Model", entity="Scp096Entity", tw=64, parts=[
    dict(name="leg_l", pivot=(-1.5, 6, 0), cubes=[C((-1, 0, -1), (2, 18, 2), "skin096")]),
    dict(name="leg_r", pivot=(1.5, 6, 0), cubes=[C((-1, 0, -1), (2, 18, 2), "skin096")]),
    dict(name="torso", pivot=(0, 6, 0), rot=(0.25, 0, 0), cubes=[C((-3, -12, -1.5), (6, 12, 3), "skin096", deco={"front": ["ribs"]})]),
    dict(name="head", parent="torso", pivot=(0, -12, -0.5), cubes=[C((-2.5, -6, -2.5), (5, 6, 5), "skin096", deco={"front": ["096_face"], "bottom": ["mouth"]})]),
    dict(name="jaw", parent="head", pivot=(0, 0, 1.5), cubes=[C((-2.5, 0, -4), (5, 2, 4), "skin096", deco={"top": ["mouth"], "front": ["096_jaw_front"]})]),
    dict(name="arm_l", parent="torso", pivot=(-3.8, -11, 0), cubes=[C((-1, -1, -1), (2, 12, 2), "skin096")]),
    dict(name="forearm_l", parent="arm_l", pivot=(0, 11, 0), cubes=[C((-1, 0, -1), (2, 13, 2), "skin096")]),
    dict(name="arm_r", parent="torso", pivot=(3.8, -11, 0), cubes=[C((-1, -1, -1), (2, 12, 2), "skin096")]),
    dict(name="forearm_r", parent="arm_r", pivot=(0, 11, 0), cubes=[C((-1, 0, -1), (2, 13, 2), "skin096")]),
], anim="""        float walk = Mth.cos(limbSwing * 0.6662F) * 1.1F * limbSwingAmount;
        this.legL.xRot = walk;
        this.legR.xRot = -walk;
        int state = entity.getRageState();
        if (state == Scp096Entity.CALM) {
            float sob = Mth.sin(ageInTicks * 0.45F) * 0.06F;
            // hunched over, hands pressed to its face
            this.torso.xRot = 0.45F;
            this.head.xRot = 0.35F + sob;
            this.armL.xRot = -0.55F + sob;
            this.armR.xRot = -0.55F + sob;
            this.armL.zRot = 0.25F;
            this.armR.zRot = -0.25F;
            this.forearmL.xRot = -2.25F;
            this.forearmR.xRot = -2.25F;
            this.forearmL.zRot = -0.35F;
            this.forearmR.zRot = 0.35F;
        } else if (state == Scp096Entity.TRIGGERED) {
            this.torso.xRot = 0.05F + Mth.sin(ageInTicks * 2.1F) * 0.05F;
            this.head.yRot = Mth.sin(ageInTicks * 2.7F) * 0.35F;
            this.head.xRot = -0.35F;
            this.jaw.xRot = 0.95F;
            this.armL.xRot = -1.1F + Mth.sin(ageInTicks * 1.7F) * 0.7F;
            this.armR.xRot = -1.1F + Mth.cos(ageInTicks * 1.9F) * 0.7F;
            this.armL.zRot = 0.4F;
            this.armR.zRot = -0.4F;
            this.forearmL.xRot = -0.6F;
            this.forearmR.xRot = -0.6F;
        } else {
            this.torso.xRot = 0.75F;
            this.head.xRot = -0.65F;
            this.jaw.xRot = 0.85F + Mth.sin(ageInTicks * 0.9F) * 0.1F;
            this.legL.xRot = walk * 1.4F;
            this.legR.xRot = -walk * 1.4F;
            this.armL.xRot = -0.9F - walk * 1.6F;
            this.armR.xRot = -0.9F + walk * 1.6F;
            this.forearmL.xRot = -0.35F;
            this.forearmR.xRot = -0.35F;
        }"""))

MODELS.append(dict(id="scp_049", cls="Scp049Model", entity="Scp049Entity", tw=64, parts=[
    dict(name="leg_l", pivot=(-2, 12, 0), cubes=[C((-2, 0, -2), (4, 12, 4), "robe", face_mats={"bottom": "glove"})]),
    dict(name="leg_r", pivot=(2, 12, 0), cubes=[C((-2, 0, -2), (4, 12, 4), "robe", face_mats={"bottom": "glove"})]),
    dict(name="body", pivot=(0, 0, 0), cubes=[C((-4, 0, -2), (8, 12, 4), "robe", deco={"front": ["belt"], "back": ["belt"]})]),
    dict(name="skirt", parent="body", pivot=(0, 12, 0), cubes=[C((-4.5, -1, -2.5), (9, 10, 5), "robe")]),
    dict(name="head", pivot=(0, 0, 0), cubes=[
        C((-4, -8, -4), (8, 8, 8), "robe", face_mats={"front": "mask"}, deco={"front": ["049_mask"]}),
        C((-4, -8, -4), (8, 8, 8), "robe", inflate=0.45, deco={"front": ["hood_front"]}),
    ]),
    dict(name="beak", parent="head", pivot=(0, -3, -4), rot=(0.4, 0, 0), cubes=[C((-1, -1, -6), (2, 2, 6), "mask")]),
    dict(name="arm_l", pivot=(-5, 2, 0), cubes=[C((-3, -2, -2), (4, 12, 4), "robe", face_mats={"bottom": "glove"})]),
    dict(name="arm_r", pivot=(5, 2, 0), cubes=[C((-1, -2, -2), (4, 12, 4), "robe", face_mats={"bottom": "glove"})]),
    dict(name="bag", parent="arm_r", pivot=(1, 10, 0), cubes=[C((-1.5, 0, -2.5), (3, 4, 5), "leather")]),
], anim="""        float walk = Mth.cos(limbSwing * 0.6662F) * 1.2F * limbSwingAmount;
        this.legL.xRot = walk;
        this.legR.xRot = -walk;
        this.skirt.xRot = Math.abs(walk) * 0.25F;
        this.head.yRot = netHeadYaw * Mth.DEG_TO_RAD;
        this.head.xRot = headPitch * Mth.DEG_TO_RAD;
        this.armR.xRot = -walk * 0.5F;
        if (entity.isAggressive()) {
            this.armL.xRot = -1.45F + Mth.sin(ageInTicks * 0.15F) * 0.08F;
            this.armL.yRot = 0.15F;
        } else {
            this.armL.xRot = walk * 0.5F;
        }
        if (this.attackTime > 0F) {
            this.armL.xRot -= Mth.sin(this.attackTime * Mth.PI) * 0.9F;
        }"""))

MODELS.append(dict(id="scp_106", cls="Scp106Model", entity="Scp106Entity", tw=64, parts=[
    dict(name="leg_l", pivot=(-2, 12, 0), cubes=[C((-2, 0, -2), (4, 12, 4), "corrode")]),
    dict(name="leg_r", pivot=(2, 12, 0), cubes=[C((-2, 0, -2), (4, 12, 4), "corrode")]),
    dict(name="body", pivot=(0, 12, 0), rot=(0.38, 0, 0), cubes=[C((-4, -12, -2), (8, 12, 4), "corrode")]),
    dict(name="head", parent="body", pivot=(0, -12, -0.5), cubes=[C((-4, -7, -4), (8, 7, 8), "corrode", face_mats={"front": "corrode_skin"}, deco={"front": ["106_face"]})]),
    dict(name="arm_l", parent="body", pivot=(-5, -10.5, 0), cubes=[C((-1.5, -1.5, -1.5), (3, 15, 3), "corrode")]),
    dict(name="arm_r", parent="body", pivot=(5, -10.5, 0), cubes=[C((-1.5, -1.5, -1.5), (3, 15, 3), "corrode")]),
], anim="""        float walk = Mth.cos(limbSwing * 0.5F) * 0.9F * limbSwingAmount;
        this.legL.xRot = walk;
        this.legR.xRot = -walk;
        this.head.yRot = netHeadYaw * Mth.DEG_TO_RAD;
        this.head.xRot = headPitch * Mth.DEG_TO_RAD - 0.3F;
        float reach = entity.isAggressive() ? -1.2F : -0.35F;
        this.armL.xRot = reach + Mth.sin(ageInTicks * 0.08F) * 0.1F - walk * 0.4F;
        this.armR.xRot = reach + Mth.cos(ageInTicks * 0.08F) * 0.1F + walk * 0.4F;
        this.armL.zRot = 0.08F;
        this.armR.zRot = -0.08F;
        if (this.attackTime > 0F) {
            float s = Mth.sin(this.attackTime * Mth.PI);
            this.armL.xRot -= s; this.armR.xRot -= s;
        }"""))

MODELS.append(dict(id="scp_999", cls="Scp999Model", entity="Scp999Entity", tw=64, parts=[
    dict(name="blob", pivot=(0, 24, 0), cubes=[C((-6, -9, -6), (12, 9, 12), "goo", deco={"front": ["999_face"]})]),
    dict(name="dome", parent="blob", pivot=(0, -9, 0), cubes=[C((-4, -3, -4), (8, 3, 8), "goo")]),
    dict(name="pod_l", parent="blob", pivot=(-6, -4, 0), cubes=[C((-2, -1, -1), (2, 2, 2), "goo")]),
    dict(name="pod_r", parent="blob", pivot=(6, -4, 0), cubes=[C((0, -1, -1), (2, 2, 2), "goo")]),
], anim="""        float s = 1F + Mth.sin(ageInTicks * 0.15F) * 0.06F + limbSwingAmount * Mth.sin(limbSwing * 0.9F) * 0.12F;
        if (entity.isHugging()) s += Mth.sin(ageInTicks * 0.9F) * 0.12F;
        this.blob.yScale = s;
        this.blob.xScale = 2F - s;
        this.blob.zScale = 2F - s;
        this.podL.zRot = Mth.sin(ageInTicks * 0.3F) * 0.5F;
        this.podR.zRot = -Mth.sin(ageInTicks * 0.3F + 1F) * 0.5F;"""))

MODELS.append(dict(id="gate_guardian", cls="GateGuardianModel", entity="GateGuardianEntity", tw=128, parts=[
    dict(name="robe", pivot=(0, 24, 0), cubes=[C((-5, -14, -3.5), (10, 14, 7), "robe_gold")]),
    dict(name="body", pivot=(0, 10, 0), cubes=[C((-4.5, -11, -2.5), (9, 11, 5), "radiant")]),
    dict(name="head", parent="body", pivot=(0, -11, 0), cubes=[C((-3.5, -7, -3.5), (7, 7, 7), "radiant", deco={"front": ["guardian_face"]})]),
    dict(name="halo", parent="head", pivot=(0, -9, 1.5), rot=(-0.35, 0, 0), cubes=[C((-5, -5, 0), (10, 10, 1), "radiant", deco={"front": ["halo"], "back": ["halo"]})]),
    dict(name="arm_l", parent="body", pivot=(-5.5, -10, 0), rot=(-0.9, 0, 0.15), cubes=[C((-1.5, -1, -1.5), (3, 11, 3), "radiant")]),
    dict(name="arm_r", parent="body", pivot=(5.5, -10, 0), rot=(-0.9, 0, -0.15), cubes=[C((-1.5, -1, -1.5), (3, 11, 3), "radiant")]),
    dict(name="sword", parent="body", pivot=(0, -2, -7), cubes=[
        C((-1, -4, -1), (2, 4, 2), "hilt"), C((-3, 0, -1), (6, 1, 2), "hilt"), C((-1, 1, -0.5), (2, 22, 1), "blade")]),
    dict(name="wing_ul", parent="body", pivot=(-2, -9, 2.5), rot=(0.15, -0.55, -0.45), cubes=[C((-1, -22, 0), (1, 24, 16), "fire")]),
    dict(name="wing_ur", parent="body", pivot=(2, -9, 2.5), rot=(0.15, 0.55, 0.45), cubes=[C((0, -22, 0), (1, 24, 16), "fire")]),
    dict(name="wing_ll", parent="body", pivot=(-2, -3, 2.5), rot=(0.1, -0.7, -1.4), cubes=[C((-1, -16, 0), (1, 17, 12), "fire")]),
    dict(name="wing_lr", parent="body", pivot=(2, -3, 2.5), rot=(0.1, 0.7, 1.4), cubes=[C((0, -16, 0), (1, 17, 12), "fire")]),
], anim="""        float flap = Mth.sin(ageInTicks * 0.06F) * 0.08F;
        this.wingUl.yRot -= flap; this.wingUr.yRot += flap;
        this.wingLl.yRot -= flap * 0.6F; this.wingLr.yRot += flap * 0.6F;
        this.halo.zRot = ageInTicks * 0.02F;
        if (entity.isKneeling()) {
            this.body.xRot = 0.55F;
            this.head.xRot = 0.5F;
            this.robe.y += 2F;
            this.body.y += 3F;
            this.sword.xRot = -0.55F;
        } else {
            this.body.y += Mth.sin(ageInTicks * 0.05F) * 0.6F;
            this.head.yRot = netHeadYaw * Mth.DEG_TO_RAD * 0.3F;
        }"""))

# ----------------------------------------------- SCP-049-2 (vanilla zombie UV layout)
def zombie_texture():
    rng = random.Random("scp_049_2")
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    boxes = [
        dict(uv=(0, 0), size=(8, 8, 8), mat="grey_skin", deco={"front": ["0492_face"]}),
        dict(uv=(16, 16), size=(8, 12, 4), mat="gown"),
        dict(uv=(40, 16), size=(4, 12, 4), mat="grey_skin", face_mats={"top": "gown"}),
        dict(uv=(0, 16), size=(4, 12, 4), mat="grey_skin", face_mats={"top": "gown"}),
    ]
    for b in boxes:
        paint_cube(img, b, rng)
    # short gown sleeves / hem
    px = img.load()
    for x in range(40, 56):
        for y in range(20, 23):
            px[x, y] = noisy(MATS["gown"]["base"], 6, rng)
    for x in range(0, 16):
        for y in range(20, 23):
            px[x, y] = noisy(MATS["gown"]["base"], 6, rng)
    img.save(os.path.join(TEX_DIR, "scp_049_2.png"))

def o5_texture():
    """O5 Overseers: black suit, red tie, face lost in shadow behind a blank white visor."""
    rng = random.Random("o5")
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    for b in [dict(uv=(0, 0), size=(8, 8, 8), mat="suit"), dict(uv=(16, 16), size=(8, 12, 4), mat="suit"),
              dict(uv=(40, 16), size=(4, 12, 4), mat="suit"), dict(uv=(0, 16), size=(4, 12, 4), mat="suit")]:
        paint_cube(img, b, rng)
    px = img.load()
    for i in range(1, 7):                       # visor across the face
        for j in range(3, 5):
            px[8 + i, 8 + j] = (232, 232, 236, 255)
    px[8 + 3, 8 + 4] = (140, 20, 20, 255)
    for j in range(20, 32):                     # shirt + tie
        px[23, j] = (210, 210, 214, 255); px[24, j] = (210, 210, 214, 255)
        if 21 <= j <= 28: px[23, j] = (150, 18, 22, 255); px[24, j] = (150, 18, 22, 255)
    for i in range(44, 48):                     # pale hands
        px[i, 31] = (200, 180, 170, 255)
    img.save(os.path.join(TEX_DIR, "o5_member.png"))

if __name__ == "__main__":
    os.makedirs(JAVA_DIR, exist_ok=True); os.makedirs(TEX_DIR, exist_ok=True)
    for m in MODELS:
        build(m)
        print("built", m["cls"], m["tw"], "x", m["th"])
    zombie_texture()
    o5_texture()
    print("built scp_049_2 + o5 textures")
