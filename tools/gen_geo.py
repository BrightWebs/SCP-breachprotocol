"""GeckoLib models for Breach Protocol: geometry (.geo.json), keyframed animations
(.animation.json), shaded textures and glowmasks - all from one spec per entity, all original.

Coordinates in the specs use Java-model conventions (y down, ground at y=24, rotations in
radians, cube origins relative to the bone pivot). They're converted to Bedrock/GeckoLib format
on write: y flipped to y-up, absolute pivots, rotations in degrees.
"""
import json, math, os, random, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_models as G
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src/main/resources/assets/breachprotocol")
GEO_DIR = os.path.join(ASSETS, "geo/entity")
ANIM_DIR = os.path.join(ASSETS, "animations/entity")
TEX_DIR = os.path.join(ASSETS, "textures/entity")

# extra materials for the detailed models
G.MATS.update({
    "lens": dict(base=(26, 52, 44), n=4),
    "eye": dict(base=(250, 250, 250), n=2),
    "brass": dict(base=(176, 136, 60), n=8),
    "trouser": dict(base=(28, 28, 32), n=4),
})

def deco_lens(img, x, y, w, h, rng):
    px = img.load()
    px[x, y] = (150, 230, 200, 255)

def deco_pupil(img, x, y, w, h, rng):
    px = img.load()
    for i in range(w):
        for j in range(h):
            px[x + i, y + j] = (250, 250, 250, 255)
    px[x + w - 1, y + h - 1] = (20, 14, 10, 255)
    if w > 1: px[x + w - 2, y + h - 1] = (20, 14, 10, 255)

def deco_999_mouth(img, x, y, w, h, rng):
    px = img.load()
    mx = x + w // 2
    for i in range(-2, 3):
        px[mx + i, y + h - 2 + (1 if abs(i) < 2 else 0) - 1] = (120, 40, 20, 255)
    px[x + 1, y + h - 3] = (255, 120, 120, 255)
    px[x + w - 2, y + h - 3] = (255, 120, 120, 255)

def deco_cracks(img, x, y, w, h, rng):
    px = img.load()
    cx, cy = x + rng.randint(1, max(1, w - 2)), y
    for _ in range(h):
        if x <= cx < x + w and y <= cy < y + h:
            px[cx, cy] = (88, 84, 78, 255)
        cx += rng.choice((-1, 0, 1)); cy += 1

for k, v in list(globals().items()):
    if k.startswith("deco_"):
        G.DECOS[k] = v

# ------------------------------------------------------------------ shading

SHADE = {"top": 1.14, "bottom": 0.70, "front": 1.0, "back": 0.86, "left": 0.9, "right": 0.9}

def shade_cube(img, c):
    """Directional face shading plus a soft occlusion gradient toward each face's lower edge."""
    u, v = c["uv"]; w, h, d = c["size"]
    faces = {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h),
             "front": (u + d, v + d, w, h), "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h)}
    px = img.load()
    for name, (fx, fy, fw, fh) in faces.items():
        if c.get("no_shade") and name in c["no_shade"]:
            continue
        k = SHADE[name]
        for i in range(fw):
            for j in range(fh):
                r, g, b, a = px[fx + i, fy + j]
                if a == 0: continue
                kk = k
                if name in ("front", "back", "left", "right") and fh > 3:
                    kk *= 1.0 - 0.16 * (j / (fh - 1)) ** 2       # darker toward the bottom
                if i == 0 or j == 0 or i == fw - 1 or j == fh - 1:
                    kk *= 0.94                                   # edge definition
                px[fx + i, fy + j] = (G.clamp(r * kk), G.clamp(g * kk), G.clamp(b * kk), a)

# ------------------------------------------------------------------ writing

def C(origin, size, mat, **kw):
    d = dict(origin=origin, size=size, mat=mat); d.update(kw); return d

def write_model(m):
    rng = random.Random(m["id"] + "-geo")
    cubes = [c for p in m["parts"] for c in p["cubes"]]
    th = G.pack(cubes, m["tw"])
    m["th"] = 1 << max(5, (th - 1).bit_length())
    img = Image.new("RGBA", (m["tw"], m["th"]), (0, 0, 0, 0))
    for c in cubes:
        G.paint_cube(img, c, rng)
        if not c.get("flat"):
            shade_cube(img, c)
    img.save(os.path.join(TEX_DIR, m["id"] + ".png"))

    if m.get("glow"):
        glow = Image.new("RGBA", img.size, (0, 0, 0, 0))
        src, dst = img.load(), glow.load()
        for x in range(img.width):
            for y in range(img.height):
                p = src[x, y]
                if p[3] and m["glow"](p):
                    dst[x, y] = p
        glow.save(os.path.join(TEX_DIR, m["id"] + "_glowmask.png"))

    # absolute pivots
    by = {p["name"]: p for p in m["parts"]}
    def abs_pivot(p):
        x, y, z = p["pivot"]
        if p.get("parent"):
            ax, ay, az = abs_pivot(by[p["parent"]])
            return (ax + x, ay + y, az + z)
        return (x, y, z)

    bones = [{"name": "root", "pivot": [0, 0, 0]}]
    for p in m["parts"]:
        ax, ay, az = abs_pivot(p)
        bone = {"name": p["name"], "parent": p.get("parent", "root"), "pivot": [r3(ax), r3(24 - ay), r3(az)]}
        rx, ry, rz = p.get("rot", (0, 0, 0))
        if rx or ry or rz:
            bone["rotation"] = [r3(math.degrees(rx)), r3(math.degrees(ry)), r3(math.degrees(rz))]
        cubes_out = []
        for c in p["cubes"]:
            ox, oy, oz = c["origin"]; w, h, d = c["size"]
            cx, cy, cz = ax + ox, ay + oy, az + oz
            cube = {"origin": [r3(cx), r3(24 - cy - h), r3(cz)], "size": [w, h, d], "uv": list(c["uv"])}
            if c.get("inflate"): cube["inflate"] = c["inflate"]
            cubes_out.append(cube)
        if cubes_out: bone["cubes"] = cubes_out
        bones.append(bone)
    height = max(24 - abs_pivot(p)[1] - min((c["origin"][1] for c in p["cubes"]), default=0) for p in m["parts"]) / 16
    geo = {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": "geometry." + m["id"], "texture_width": m["tw"], "texture_height": m["th"],
                        "visible_bounds_width": 6, "visible_bounds_height": max(4, math.ceil(height * 2.8)), "visible_bounds_offset": [0, height / 2, 0]},
        "bones": bones}]}
    os.makedirs(GEO_DIR, exist_ok=True)
    with open(os.path.join(GEO_DIR, m["id"] + ".geo.json"), "w") as f:
        json.dump(geo, f, indent=1)

    anims = {}
    for name, a in m["anims"].items():
        anims[f"animation.{m['id']}.{name}"] = a
    os.makedirs(ANIM_DIR, exist_ok=True)
    with open(os.path.join(ANIM_DIR, m["id"] + ".animation.json"), "w") as f:
        json.dump({"format_version": "1.8.0", "animations": anims}, f, indent=1)

def r3(v):
    return round(v, 3)

# ------------------------------------------------------------------ animation helpers
# Channel values are offsets from the rest pose: rotation in degrees (same sign convention as
# the Java models), position in pixels (y up), scale as multipliers.

def loop(length, frames, fn):
    """Sample fn(t: 0..1) -> {bone: {channel: (x,y,z)}} into a looping animation."""
    bones = {}
    for i in range(frames + 1):
        t = i / frames
        key = f"{r3(t * length):.3f}".rstrip("0").rstrip(".") if t * length else "0.0"
        for bone, chans in fn(t).items():
            for ch, val in chans.items():
                bones.setdefault(bone, {}).setdefault(ch, {})[key] = [r3(x) for x in val]
    return {"loop": True, "animation_length": length, "bones": bones}

def once(length, frames, fn):
    a = loop(length, frames, fn)
    a["loop"] = False
    return a

def static(pose):
    return {"loop": True, "animation_length": 1.0, "bones": {b: {ch: [r3(x) for x in v] for ch, v in chans.items()} for b, chans in pose.items()}}

S = lambda t, f=1.0, ph=0.0: math.sin((t * f + ph) * 2 * math.pi)
Cs = lambda t, f=1.0, ph=0.0: math.cos((t * f + ph) * 2 * math.pi)

# ================================================================== MODELS
MODELS = []

# ----------------------------------------------------------- SCP-173 (an original concrete-and-rebar statue)
def m173():
    parts = []
    for side, x in (("l", -2.2), ("r", 2.2)):
        parts.append(dict(name="leg_" + side, pivot=(x, 14, 0), cubes=[
            C((-1.5, 0, -2), (3, 6, 4), "concrete"), C((-1.5, 6, -1.5), (3, 3, 3), "concrete"),
            C((-1.5, 8, -3), (3, 2, 5), "concrete_dark")]))
    parts.append(dict(name="torso", pivot=(0, 14, 0), rot=(0.08, 0, 0), cubes=[
        C((-4, -3, -2.5), (8, 3, 5), "concrete"), C((-3, -8, -2), (6, 5, 4), "concrete_dark"),
        C((-4.5, -16, -3), (9, 8, 6), "concrete", deco={"front": ["cracks"], "back": ["cracks"]})]))
    parts.append(dict(name="neck", parent="torso", pivot=(0, -16, 0), cubes=[C((-1.5, -2, -1.5), (3, 2, 3), "rust")]))
    parts.append(dict(name="head", parent="neck", pivot=(0, -2, 0), rot=(0.05, 0, 0.15), cubes=[
        C((-4, -9, -4), (8, 9, 8), "concrete", deco={"left": ["cracks"]}),
        C((-3, -7.5, -4.8), (6, 6, 1), "concrete_dark", face_mats={"front": "concrete_dark"}, deco={"front": ["173_face"]}),
        C((-3.5, -9.6, -3), (7, 1, 6), "concrete_dark")]))
    for side, x, zr in (("l", -5.5, 0.08), ("r", 5.5, -0.08)):
        parts.append(dict(name="arm_" + side, parent="torso", pivot=(x, -15, 0), rot=(-0.1, 0, zr), cubes=[C((-1.5, -1, -1.5), (3, 9, 3), "concrete")]))
        parts.append(dict(name="forearm_" + side, parent="arm_" + side, pivot=(0, 8, 0), rot=(-0.12, 0, 0), cubes=[C((-1.5, 0, -1.5), (3, 8, 3), "concrete")]))
        parts.append(dict(name="hand_" + side, parent="forearm_" + side, pivot=(0, 8, 0), cubes=[
            C((-1.5, 0, -2), (3, 3, 4), "concrete_dark"), C((-1, 3, -2), (2, 2, 1), "concrete_dark")]))
    parts += [
        dict(name="rebar_a", parent="torso", pivot=(-3, -16, 0), rot=(0.2, 0, -0.35), cubes=[C((-0.5, -5, -0.5), (1, 5, 1), "rust")]),
        dict(name="rebar_b", parent="torso", pivot=(3, -16, 1), rot=(-0.3, 0, 0.45), cubes=[C((-0.5, -4, -0.5), (1, 4, 1), "rust")]),
        dict(name="rebar_c", parent="torso", pivot=(1, -10, 3), rot=(0.9, 0.3, 0), cubes=[C((-0.5, -0.5, 0), (1, 1, 4), "rust")]),
        dict(name="rebar_d", parent="head", pivot=(2, -6, 4), rot=(0.6, 0.3, 0), cubes=[C((-0.5, -0.5, 0), (1, 1, 3), "rust")]),
    ]
    poses = {
        "pose0": {},
        "pose1": {"arm_l": {"rotation": (-75, 0, 10)}, "arm_r": {"rotation": (-68, 0, -12)}, "forearm_l": {"rotation": (-20, 0, 0)},
                  "forearm_r": {"rotation": (-15, 0, 0)}, "head": {"rotation": (12, 0, -8)}, "torso": {"rotation": (10, 0, 0)}},
        "pose2": {"head": {"rotation": (0, 50, 12)}, "neck": {"rotation": (0, 15, 0)}, "arm_r": {"rotation": (-150, 0, -15)},
                  "forearm_r": {"rotation": (-35, 0, 0)}, "torso": {"rotation": (0, -12, 0)}, "arm_l": {"rotation": (10, 0, 15)}},
        "pose3": {"torso": {"rotation": (28, 0, 0)}, "head": {"rotation": (-25, 0, 0)}, "leg_l": {"rotation": (-25, 0, 0)},
                  "leg_r": {"rotation": (18, 0, 0)}, "arm_l": {"rotation": (-55, 0, 5)}, "arm_r": {"rotation": (-40, 0, -5)},
                  "forearm_l": {"rotation": (-30, 0, 0)}, "forearm_r": {"rotation": (-30, 0, 0)}},
    }
    return dict(id="scp_173", tw=64, parts=parts, anims={k: static(v) for k, v in poses.items()})
MODELS.append(m173())

# ----------------------------------------------------------- SCP-096
def m096():
    parts = []
    for side, x in (("l", -1.8), ("r", 1.8)):
        parts.append(dict(name="thigh_" + side, pivot=(x, 4, 0), cubes=[C((-1, 0, -1), (2, 11, 2), "skin096")]))
        parts.append(dict(name="shin_" + side, parent="thigh_" + side, pivot=(0, 11, 0), cubes=[
            C((-1, 0, -1), (2, 9, 2), "skin096"), C((-1, 8, -3), (2, 1, 4), "skin096")]))
    parts.append(dict(name="torso", pivot=(0, 4, 0), rot=(0.3, 0, 0), cubes=[
        C((-2.5, -2, -1.5), (5, 2, 3), "skin096"), C((-2, -8, -1.5), (4, 6, 3), "skin096"),
        C((-3.5, -15, -2), (7, 7, 4), "skin096", deco={"front": ["ribs"], "back": ["ribs"]}), C((-4, -15, -1.5), (8, 2, 3), "skin096")]))
    parts.append(dict(name="neck", parent="torso", pivot=(0, -15, -0.5), cubes=[C((-1, -3, -1), (2, 3, 2), "skin096")]))
    parts.append(dict(name="head", parent="neck", pivot=(0, -3, 0), cubes=[
        C((-2.5, -6, -3), (5, 6, 5), "skin096", deco={"front": ["096_face"], "bottom": ["mouth"]})]))
    parts.append(dict(name="jaw", parent="head", pivot=(0, -0.5, 1.5), cubes=[
        C((-2.5, 0, -4.5), (5, 2, 5), "skin096", deco={"top": ["mouth"], "front": ["096_jaw_front"]})]))
    for side, x in (("l", -4), ("r", 4)):
        parts.append(dict(name="arm_" + side, parent="torso", pivot=(x, -14, 0), cubes=[C((-1, -1, -1), (2, 12, 2), "skin096")]))
        parts.append(dict(name="forearm_" + side, parent="arm_" + side, pivot=(0, 11, 0), cubes=[C((-1, 0, -1), (2, 12, 2), "skin096")]))
        parts.append(dict(name="hand_" + side, parent="forearm_" + side, pivot=(0, 12, 0), cubes=[
            C((-1.5, 0, -1), (3, 2, 2), "skin096"), C((-1.5, 2, -1), (1, 4, 1), "skin096"), C((0.5, 2, -1), (1, 4, 1), "skin096")]))

    def cry(t):
        sob = S(t, 2) * 3
        return {"torso": {"rotation": (15 + sob, 0, 0)}, "head": {"rotation": (25 + sob * 1.3, 0, 0)},
                "arm_l": {"rotation": (-55 + sob, 0, 22)}, "arm_r": {"rotation": (-55 + sob, 0, -22)},
                "forearm_l": {"rotation": (-120, 0, -30)}, "forearm_r": {"rotation": (-120, 0, 30)},
                "thigh_l": {"rotation": (-12, 0, 0)}, "thigh_r": {"rotation": (-12, 0, 0)},
                "shin_l": {"rotation": (14, 0, 0)}, "shin_r": {"rotation": (14, 0, 0)}}
    def walk(t):
        sw = S(t) * 24
        return {"torso": {"rotation": (12 + abs(S(t)) * 3, 0, S(t) * 3)}, "head": {"rotation": (22, 0, 0)},
                "thigh_l": {"rotation": (sw, 0, 0)}, "thigh_r": {"rotation": (-sw, 0, 0)},
                "shin_l": {"rotation": (max(0, -sw) * 0.9 + 5, 0, 0)}, "shin_r": {"rotation": (max(0, sw) * 0.9 + 5, 0, 0)},
                "arm_l": {"rotation": (-50, 0, 22)}, "arm_r": {"rotation": (-50, 0, -22)},
                "forearm_l": {"rotation": (-115, 0, -30)}, "forearm_r": {"rotation": (-115, 0, 30)}}
    def triggered(t):
        return {"torso": {"rotation": (-5 + S(t, 3) * 5, 0, S(t, 4) * 6)}, "head": {"rotation": (-30 + S(t, 5) * 8, S(t, 3) * 25, S(t, 2) * 10)},
                "jaw": {"rotation": (50 + S(t, 6) * 10, 0, 0)},
                "arm_l": {"rotation": (-70 + S(t, 2) * 45, 0, 25)}, "arm_r": {"rotation": (-70 + Cs(t, 2) * 45, 0, -25)},
                "forearm_l": {"rotation": (-80 + S(t, 3) * 20, 0, 0)}, "forearm_r": {"rotation": (-80 + Cs(t, 3) * 20, 0, 0)},
                "thigh_l": {"rotation": (S(t, 2) * 6, 0, 0)}, "thigh_r": {"rotation": (-S(t, 2) * 6, 0, 0)}}
    def rage(t):
        sw = S(t) * 55
        return {"torso": {"rotation": (38, 0, S(t) * 5)}, "head": {"rotation": (-45, 0, 0)}, "neck": {"rotation": (-10, 0, 0)},
                "jaw": {"rotation": (45 + S(t, 2) * 8, 0, 0)},
                "thigh_l": {"rotation": (sw - 15, 0, 0)}, "thigh_r": {"rotation": (-sw - 15, 0, 0)},
                "shin_l": {"rotation": (max(0, -sw) * 1.1 + 10, 0, 0)}, "shin_r": {"rotation": (max(0, sw) * 1.1 + 10, 0, 0)},
                "arm_l": {"rotation": (-sw * 1.2 - 20, 0, 10)}, "arm_r": {"rotation": (sw * 1.2 - 20, 0, -10)},
                "forearm_l": {"rotation": (-30, 0, 0)}, "forearm_r": {"rotation": (-30, 0, 0)},
                "root": {"position": (0, abs(S(t, 2)) * 1.5, 0)}}
    return dict(id="scp_096", tw=64, parts=parts, anims={
        "cry": loop(2.4, 12, cry), "walk": loop(1.6, 12, walk), "triggered": loop(0.6, 12, triggered), "rage": loop(0.45, 10, rage)})
MODELS.append(m096())

# ----------------------------------------------------------- SCP-049
def m049():
    parts = []
    for side, x in (("l", -2), ("r", 2)):
        parts.append(dict(name="leg_" + side, pivot=(x, 12, 0), cubes=[
            C((-2, 0, -2), (4, 12, 4), "trouser"), C((-2, 10, -3), (4, 2, 5), "glove")]))
    parts.append(dict(name="torso", pivot=(0, 12, 0), cubes=[
        C((-4, -12, -2), (8, 12, 4), "robe", deco={"front": ["belt"], "back": ["belt"]}), C((-4.5, -12.5, -2.5), (9, 2, 5), "robe")]))
    parts.append(dict(name="coat", parent="torso", pivot=(0, 0, 0), cubes=[C((-4.5, -1, -2.5), (9, 10, 5), "robe")]))
    parts.append(dict(name="head", parent="torso", pivot=(0, -12, 0), cubes=[
        C((-4, -8, -4), (8, 8, 8), "robe", face_mats={"front": "mask"}, deco={"front": ["049_mask"]}),
        C((-4, -8, -4), (8, 8, 8), "robe", inflate=0.6, deco={"front": ["hood_front"]}, flat=True),
        C((-3, -6, -4.6), (2, 2, 1), "lens", deco={"front": ["lens"]}), C((1, -6, -4.6), (2, 2, 1), "lens", deco={"front": ["lens"]})]))
    parts.append(dict(name="beak", parent="head", pivot=(0, -3.5, -4), rot=(0.45, 0, 0), cubes=[
        C((-1.5, -1.5, -3), (3, 3, 3), "mask"), C((-1, -1, -7), (2, 2, 4), "mask")]))
    parts.append(dict(name="arm_l", parent="torso", pivot=(-5, -10, 0), cubes=[C((-3, -2, -2), (4, 10, 4), "robe"), C((-3, 8, -2), (4, 3, 4), "glove")]))
    parts.append(dict(name="arm_r", parent="torso", pivot=(5, -10, 0), cubes=[C((-1, -2, -2), (4, 10, 4), "robe"), C((-1, 8, -2), (4, 3, 4), "glove")]))
    parts.append(dict(name="bag", parent="arm_r", pivot=(1, 11, 0), cubes=[
        C((-0.5, -0.5, -1.5), (1, 1, 3), "leather"), C((-1.5, 0.5, -3), (3, 4, 6), "leather", deco={"left": ["belt"]})]))

    def idle(t):
        return {"torso": {"rotation": (S(t) * 1.5, 0, 0)}, "head": {"rotation": (S(t, 1, 0.25) * 3, 0, S(t) * 4)},
                "arm_l": {"rotation": (S(t) * 3, 0, 4)}, "arm_r": {"rotation": (-S(t) * 3, 0, -4)}, "bag": {"rotation": (S(t) * 4, 0, 0)}}
    def walk(t):
        sw = S(t) * 24
        return {"leg_l": {"rotation": (sw, 0, 0)}, "leg_r": {"rotation": (-sw, 0, 0)}, "coat": {"rotation": (abs(S(t)) * 8, 0, S(t) * 2)},
                "arm_l": {"rotation": (-sw * 0.5, 0, 4)}, "arm_r": {"rotation": (sw * 0.4, 0, -4)}, "bag": {"rotation": (-sw * 0.5, 0, 0)},
                "root": {"position": (0, abs(S(t, 1)) * 0.6, 0)}, "head": {"rotation": (S(t, 2) * 2, 0, 0)}}
    def attack(t):
        reach = math.sin(t * math.pi)
        return {"arm_l": {"rotation": (-110 * reach, 15 * reach, 0)}, "torso": {"rotation": (12 * reach, -10 * reach, 0)},
                "head": {"rotation": (10 * reach, 0, 0)}}
    return dict(id="scp_049", tw=64, parts=parts, glow=lambda p: p[:3] == (150, 230, 200),
                anims={"idle": loop(3.0, 12, idle), "walk": loop(1.3, 12, walk), "attack": once(0.6, 10, attack)})
MODELS.append(m049())

# ----------------------------------------------------------- SCP-106
def m106():
    parts = []
    for side, x in (("l", -2), ("r", 2)):
        parts.append(dict(name="thigh_" + side, pivot=(x, 12, 0), rot=(-0.25, 0, 0), cubes=[C((-1.5, 0, -1.5), (3, 7, 3), "corrode")]))
        parts.append(dict(name="shin_" + side, parent="thigh_" + side, pivot=(0, 6.5, 0), rot=(0.35, 0, 0), cubes=[
            C((-1.5, 0, -1.5), (3, 6, 3), "corrode"), C((-1.5, 5, -3), (3, 1, 4), "dark")]))
    parts.append(dict(name="torso", pivot=(0, 12, 0), rot=(0.45, 0, 0), cubes=[
        C((-3.5, -6, -2), (7, 6, 4), "corrode"), C((-4, -12, -2.5), (8, 6, 5), "corrode"),
        C((-3, -1, -2.6), (1, 3, 1), "dark"), C((2, 0, 1.6), (1, 4, 1), "dark"), C((-1, -5, 2.1), (1, 3, 1), "dark")]))
    parts.append(dict(name="head", parent="torso", pivot=(0, -12, -0.5), rot=(-0.35, 0, 0), cubes=[
        C((-3.5, -7, -4), (7, 7, 7), "corrode", face_mats={"front": "corrode_skin"}, deco={"front": ["106_face"]})]))
    parts.append(dict(name="jaw", parent="head", pivot=(0, 0, 0.5), cubes=[C((-3, 0, -4.2), (6, 2, 5), "corrode_skin", deco={"top": ["mouth"]})]))
    for side, x in (("l", -5), ("r", 5)):
        parts.append(dict(name="arm_" + side, parent="torso", pivot=(x, -11, 0), cubes=[C((-1.5, -1.5, -1.5), (3, 8, 3), "corrode")]))
        parts.append(dict(name="forearm_" + side, parent="arm_" + side, pivot=(0, 6.5, 0), cubes=[
            C((-1.5, 0, -1.5), (3, 8, 3), "corrode"), C((-0.5, 3, 1.4), (1, 3, 1), "dark")]))
        parts.append(dict(name="hand_" + side, parent="forearm_" + side, pivot=(0, 8, 0), cubes=[
            C((-2, 0, -1.5), (4, 2, 3), "corrode_skin"), C((-2, 2, -1.5), (1, 3, 1), "dark"), C((-0.5, 2, -1.5), (1, 3, 1), "dark"), C((1, 2, -1.5), (1, 3, 1), "dark")]))

    def idle(t):
        return {"torso": {"rotation": (S(t) * 3, 0, S(t, 1, 0.3) * 3)}, "jaw": {"rotation": (8 + S(t, 2) * 4, 0, 0)},
                "arm_l": {"rotation": (-25 + S(t) * 6, 0, 6)}, "arm_r": {"rotation": (-25 - S(t) * 6, 0, -6)},
                "forearm_l": {"rotation": (-20, 0, 0)}, "forearm_r": {"rotation": (-20, 0, 0)}}
    def walk(t):
        sw = S(t) * 20
        return {"torso": {"rotation": (4, 0, S(t) * 7)}, "thigh_l": {"rotation": (sw, 0, 0)}, "thigh_r": {"rotation": (-sw, 0, 0)},
                "shin_l": {"rotation": (max(0, -sw) * 0.8, 0, 0)}, "shin_r": {"rotation": (max(0, sw) * 0.8, 0, 0)},
                "arm_l": {"rotation": (-40 - sw * 0.4, 0, 8)}, "arm_r": {"rotation": (-40 + sw * 0.4, 0, -8)},
                "forearm_l": {"rotation": (-25, 0, 0)}, "forearm_r": {"rotation": (-25, 0, 0)}, "jaw": {"rotation": (12, 0, 0)},
                "root": {"position": (0, -abs(S(t)) * 0.8, 0)}}
    def attack(t):
        s = math.sin(t * math.pi)
        return {"arm_l": {"rotation": (-40 - 80 * s, -20 * s, 0)}, "arm_r": {"rotation": (-40 - 80 * s, 20 * s, 0)},
                "torso": {"rotation": (10 * s, 0, 0)}, "jaw": {"rotation": (30 * s, 0, 0)}}
    def phase(t):
        return {"arm_l": {"rotation": (-160 + S(t, 2) * 10, 0, 20)}, "arm_r": {"rotation": (-160 - S(t, 2) * 10, 0, -20)},
                "head": {"rotation": (-30, 0, S(t) * 10)}, "jaw": {"rotation": (35, 0, 0)}, "torso": {"rotation": (-20, 0, 0)}}
    return dict(id="scp_106", tw=64, parts=parts, glow=lambda p: p[:3] == (196, 188, 150),
                anims={"idle": loop(3.0, 12, idle), "walk": loop(1.8, 12, walk), "attack": once(0.7, 10, attack), "phase": loop(1.0, 10, phase)})
MODELS.append(m106())

# ----------------------------------------------------------- SCP-999
def m999():
    parts = [
        dict(name="blob", pivot=(0, 24, 0), cubes=[
            C((-7, -3, -7), (14, 3, 14), "goo"), C((-6, -9, -6), (12, 6, 12), "goo", deco={"front": ["999_mouth"]}), C((-4, -12, -4), (8, 3, 8), "goo")]),
        dict(name="eye_l", parent="blob", pivot=(-2.5, -7, -6), cubes=[C((-1, -1.5, -0.6), (2, 3, 1), "eye", deco={"front": ["pupil"]})]),
        dict(name="eye_r", parent="blob", pivot=(2.5, -7, -6), cubes=[C((-1, -1.5, -0.6), (2, 3, 1), "eye", deco={"front": ["pupil"]})]),
        dict(name="pod_l", parent="blob", pivot=(-7, -4, 0), cubes=[C((-2, -1, -1), (2, 2, 2), "goo")]),
        dict(name="pod_r", parent="blob", pivot=(7, -4, 0), cubes=[C((0, -1, -1), (2, 2, 2), "goo")]),
    ]
    def idle(t):
        s = 1 + S(t) * 0.05
        blink = 0.15 if 0.88 < t < 0.94 else 1.0
        return {"blob": {"scale": (2 - s, s, 2 - s)}, "eye_l": {"scale": (1, blink, 1)}, "eye_r": {"scale": (1, blink, 1)},
                "pod_l": {"rotation": (0, 0, S(t) * 20)}, "pod_r": {"rotation": (0, 0, -S(t) * 20)}}
    def hop(t):
        up = max(0.0, math.sin(t * math.pi))
        squash = 1 - 0.18 * (1 - up) ** 4
        return {"blob": {"position": (0, up * 3, 0), "scale": (1 + (1 - squash), squash + up * 0.1, 1 + (1 - squash))},
                "pod_l": {"rotation": (0, 0, up * 35)}, "pod_r": {"rotation": (0, 0, -up * 35)}}
    def hug(t):
        s = 1 + S(t, 3) * 0.14
        return {"blob": {"scale": (2 - s + 0.1, s, 2 - s + 0.1)}, "pod_l": {"rotation": (0, 0, 40 + S(t, 4) * 30)},
                "pod_r": {"rotation": (0, 0, -40 - S(t, 4) * 30)}, "eye_l": {"scale": (1, 0.3, 1)}, "eye_r": {"scale": (1, 0.3, 1)}}
    return dict(id="scp_999", tw=64, parts=parts, anims={"idle": loop(2.0, 16, idle), "hop": loop(0.6, 10, hop), "hug": loop(1.0, 12, hug)})
MODELS.append(m999())

# ----------------------------------------------------------- SCP-001 Gate Guardian
def mguardian():
    parts = [
        dict(name="robe", pivot=(0, 24, 0), cubes=[C((-6, -8, -4), (12, 8, 8), "robe_gold"), C((-5, -14, -3.5), (10, 6, 7), "robe_gold")]),
        dict(name="body", pivot=(0, 10, 0), cubes=[
            C((-4.5, -11, -2.5), (9, 11, 5), "radiant"), C((-4, -10, -3), (8, 6, 1), "robe_gold"), C((-5, -11, -2), (10, 2, 4), "robe_gold")]),
        dict(name="head", parent="body", pivot=(0, -11, 0), cubes=[
            C((-3.5, -7, -3.5), (7, 7, 7), "radiant", deco={"front": ["guardian_face"]}), C((-4, -8, -4), (8, 1, 8), "robe_gold")]),
        dict(name="halo", parent="head", pivot=(0, -10, 2), rot=(-0.3, 0, 0), cubes=[C((-5, -5, 0), (10, 10, 1), "radiant", deco={"front": ["halo"], "back": ["halo"]}, flat=True)]),
    ]
    for side, x, z in (("l", -5.5, 0.15), ("r", 5.5, -0.15)):
        parts.append(dict(name="arm_" + side, parent="body", pivot=(x, -10, 0), rot=(-0.9, 0, z), cubes=[C((-1.5, -1, -1.5), (3, 6, 3), "radiant")]))
        parts.append(dict(name="forearm_" + side, parent="arm_" + side, pivot=(0, 5, 0), rot=(-0.4, 0, 0), cubes=[C((-1.5, 0, -1.5), (3, 6, 3), "radiant")]))
    parts.append(dict(name="sword", parent="body", pivot=(0, -1, -7), cubes=[
        C((-1, -5, -1), (2, 1, 2), "robe_gold"), C((-1, -4, -1), (2, 4, 2), "hilt"), C((-4, 0, -1), (8, 1, 2), "robe_gold"),
        C((-1, 1, -0.5), (2, 24, 1), "blade"), C((-0.5, 25, -0.5), (1, 2, 1), "blade")]))
    for side, sg in (("l", -1), ("r", 1)):
        parts.append(dict(name="wing_u" + side, parent="body", pivot=(2 * sg, -9, 2.5), rot=(0.15, 0.55 * sg, 0.45 * sg), cubes=[
            C((-1 if sg < 0 else 0, -22, 0), (1, 24, 16), "fire"), C((-1 if sg < 0 else 0, -28, 6), (1, 6, 9), "fire")]))
        parts.append(dict(name="wing_l" + side, parent="body", pivot=(2 * sg, -3, 2.5), rot=(0.1, 0.7 * sg, 1.4 * sg), cubes=[
            C((-1 if sg < 0 else 0, -16, 0), (1, 17, 12), "fire")]))

    def idle(t):
        f = S(t) * 6
        return {"wing_ul": {"rotation": (0, -f, 0)}, "wing_ur": {"rotation": (0, f, 0)}, "wing_ll": {"rotation": (0, -f * 0.6, 0)},
                "wing_lr": {"rotation": (0, f * 0.6, 0)}, "body": {"position": (0, S(t) * 0.6, 0)}, "robe": {"position": (0, S(t) * 0.4, 0)},
                "halo": {"rotation": (0, 0, t * 360)}, "sword": {"rotation": (S(t) * 2, 0, 0)}}
    def kneel(t):
        return {"body": {"rotation": (30, 0, 0), "position": (0, -3, 0)}, "head": {"rotation": (25, 0, 0)}, "robe": {"position": (0, -2, 0)},
                "sword": {"rotation": (-30, 0, 0)}, "wing_ul": {"rotation": (-10, -15, 0)}, "wing_ur": {"rotation": (-10, 15, 0)},
                "halo": {"rotation": (0, 0, t * 360)}}
    return dict(id="gate_guardian", tw=128, parts=parts, glow=lambda p: True,
                anims={"idle": loop(4.0, 16, idle), "kneel": loop(4.0, 8, kneel)})
MODELS.append(mguardian())

if __name__ == "__main__":
    for m in MODELS:
        write_model(m)
        print("wrote", m["id"], m["tw"], "x", m["th"], "anims:", ", ".join(m["anims"]))
