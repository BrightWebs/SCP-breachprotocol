"""Renders the GeckoLib specs (rest pose or an animation frame) to a preview sheet."""
import math, os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_geo as GG
import preview_models as PM
from PIL import Image, ImageDraw

def frame_pose(m, anim, t):
    a = m["anims"].get(anim)
    if not a: return {}
    out = {}
    for bone, chans in a["bones"].items():
        rot = chans.get("rotation")
        if rot is None: continue
        if isinstance(rot, dict):
            keys = sorted(rot, key=float)
            target = t * a["animation_length"]
            best = min(keys, key=lambda k: abs(float(k) - target))
            rot = rot[best]
        out[bone] = rot
    return out

def posed(m, pose):
    parts = []
    for p in m["parts"]:
        q = dict(p)
        if p["name"] in pose:
            rx, ry, rz = p.get("rot", (0, 0, 0))
            dx, dy, dz = (math.radians(v) for v in pose[p["name"]])
            q["rot"] = (rx + dx, ry + dy, rz + dz)
        parts.append(q)
    return dict(m, parts=parts)

if __name__ == "__main__":
    PM.G.build = lambda model: None            # textures/uv already produced by gen_geo
    PM.G.TEX_DIR = GG.TEX_DIR
    shots = []
    for m in GG.MODELS:
        GG.write_model(m)
        names = list(m["anims"])
        pick = {"scp_173": ["pose0", "pose2"], "scp_096": ["cry", "rage"], "scp_049": ["walk", "attack"],
                "scp_106": ["walk", "phase"], "scp_999": ["idle", "hug"], "gate_guardian": ["idle", "kneel"]}[m["id"]]
        for an in pick:
            img, _ = PM.render(posed(m, frame_pose(m, an, 0.3 if an != "attack" else 0.5)), size=300)
            shots.append((m["id"] + " / " + an, img))
    cols = 6
    rows = (len(shots) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * 300, rows * 320), (34, 36, 42, 255))
    d = ImageDraw.Draw(sheet)
    for i, (name, img) in enumerate(shots):
        x, y = (i % cols) * 300, (i // cols) * 320
        sheet.paste(img, (x, y), img)
        d.text((x + 10, y + 302), name, fill=(230, 230, 230, 255))
    sheet.save(sys.argv[1] if len(sys.argv) > 1 else "/tmp/geo.png")
    print("ok")
