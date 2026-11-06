"""Tiny software renderer: draws each generated model (rest pose) from the same spec, for previews."""
import math, os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_models as G
from PIL import Image, ImageDraw

def rot(rx, ry, rz):
    cx, sx, cy, sy, cz, sz = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
    Rx = [[1, 0, 0], [0, cx, -sx], [0, sx, cx]]
    Ry = [[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]]
    Rz = [[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]]
    def mm(a, b): return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]
    return mm(mm(Rz, Ry), Rx)

def apply(M, t, p):
    return tuple(sum(M[i][k] * p[k] for k in range(3)) + t[i] for i in range(3))

def compose(parent, R, piv):
    PM, Pt = parent
    t = apply(PM, Pt, piv)
    M = [[sum(PM[i][k] * R[k][j] for k in range(3)) for j in range(3)] for i in range(3)]
    return (M, t)

def render(model, yaw=-28, pitch=12, size=360):
    G.build(model)
    tex = Image.open(os.path.join(G.TEX_DIR, model["id"] + ".png")).convert("RGBA").load()
    parts = {p["name"]: p for p in model["parts"]}
    xf = {}
    def get(p):
        if p["name"] in xf: return xf[p["name"]]
        parent = get(parts[p["parent"]]) if p.get("parent") else ([[1, 0, 0], [0, 1, 0], [0, 0, 1]], (0, 0, 0))
        xf[p["name"]] = compose(parent, rot(*p.get("rot", (0, 0, 0))), p["pivot"])
        return xf[p["name"]]
    cy_, sy_, cp, sp = math.cos(math.radians(yaw)), math.sin(math.radians(yaw)), math.cos(math.radians(pitch)), math.sin(math.radians(pitch))
    def to_cam(p):  # model px -> world (x, 24-y, -z) -> camera
        x, y, z = p[0], 24 - p[1], -p[2]
        x, z = x * cy_ + z * sy_, -x * sy_ + z * cy_
        y, z = y * cp - z * sp, y * sp + z * cp
        return (x, y, z)
    quads = []
    for p in model["parts"]:
        M, t = get(p)
        for c in p["cubes"]:
            u, v = c["uv"]; w, h, d = c["size"]; ox, oy, oz = c["origin"]; inf = c.get("inflate", 0)
            x0, y0, z0 = ox - inf, oy - inf, oz - inf
            sx, sy, sz = (w + 2 * inf) / w, (h + 2 * inf) / h, (d + 2 * inf) / d
            faces = {  # name: (region u,v,cols,rows, fn(i,j)->3 corners origin/du/dv in model px)
                "front": (u + d, v + d, w, h, lambda i, j: ((x0 + i * sx, y0 + j * sy, z0), (sx, 0, 0), (0, sy, 0))),
                "back": (u + 2 * d + w, v + d, w, h, lambda i, j: ((x0 + (w - i) * sx, y0 + j * sy, z0 + d * sz), (-sx, 0, 0), (0, sy, 0))),
                "right": (u, v + d, d, h, lambda i, j: ((x0, y0 + j * sy, z0 + (d - i) * sz), (0, 0, -sz), (0, sy, 0))),
                "left": (u + d + w, v + d, d, h, lambda i, j: ((x0 + w * sx, y0 + j * sy, z0 + i * sz), (0, 0, sz), (0, sy, 0))),
                "top": (u + d, v, w, d, lambda i, j: ((x0 + i * sx, y0, z0 + (d - j) * sz), (sx, 0, 0), (0, 0, -sz))),
                "bottom": (u + d + w, v, w, d, lambda i, j: ((x0 + i * sx, y0 + h * sy, z0 + j * sz), (sx, 0, 0), (0, 0, sz))),
            }
            center = apply(M, t, (x0 + w * sx / 2, y0 + h * sy / 2, z0 + d * sz / 2))
            for name, (fu, fv, cols, rows, fn) in faces.items():
                for i in range(cols):
                    for j in range(rows):
                        col = tex[fu + i, fv + j]
                        if col[3] < 20: continue
                        o, du, dv = fn(i, j)
                        pts = [o, tuple(o[k] + du[k] for k in range(3)), tuple(o[k] + du[k] + dv[k] for k in range(3)), tuple(o[k] + dv[k] for k in range(3))]
                        cam = [to_cam(apply(M, t, q)) for q in pts]
                        ax = [cam[1][k] - cam[0][k] for k in range(3)]; bx = [cam[3][k] - cam[0][k] for k in range(3)]
                        n = (ax[1] * bx[2] - ax[2] * bx[1], ax[2] * bx[0] - ax[0] * bx[2], ax[0] * bx[1] - ax[1] * bx[0])
                        mid = [sum(q[k] for q in cam) / 4 for k in range(3)]
                        cc = to_cam(center)
                        outward = sum(n[k] * (mid[k] - cc[k]) for k in range(3))
                        if outward < 0: n = tuple(-a for a in n)
                        if n[2] <= 0: continue  # back-facing
                        ln = math.sqrt(sum(a * a for a in n)) or 1
                        light = 0.55 + 0.45 * max(0, (n[0] * -0.4 + n[1] * 0.7 + n[2] * 0.6) / ln)
                        quads.append((mid[2], cam, tuple(int(col[k] * light) for k in range(3))))
    quads.sort(key=lambda q: q[0])
    xs = [p[0] for q in quads for p in q[1]]; ys = [p[1] for q in quads for p in q[1]]
    span = max(max(xs) - min(xs), max(ys) - min(ys)); s = size * 0.85 / span
    cx, cy = (max(xs) + min(xs)) / 2, (max(ys) + min(ys)) / 2
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0)); dr = ImageDraw.Draw(img)
    for _, cam, col in quads:
        dr.polygon([(size / 2 + (p[0] - cx) * s, size / 2 - (p[1] - cy) * s) for p in cam], fill=col + (255,))
    return img, span

if __name__ == "__main__":
    ims = []
    for m in G.MODELS:
        img, span = render(m)
        ims.append((m["id"], img, span))
    W = 360 * len(ims)
    sheet = Image.new("RGBA", (W, 400), (34, 36, 42, 255))
    d = ImageDraw.Draw(sheet)
    for i, (name, img, span) in enumerate(ims):
        sheet.paste(img, (i * 360, 0), img)
        d.text((i * 360 + 150, 375), name.upper().replace("_", "-"), fill=(230, 230, 230, 255))
    out = sys.argv[1] if len(sys.argv) > 1 else "/tmp/models.png"
    sheet.save(out)
    print("saved", out)
