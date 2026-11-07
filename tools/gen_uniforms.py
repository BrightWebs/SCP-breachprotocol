"""Foundation uniforms as clothing layers for the player-shaped (MCA-style) staff model.
64x64, player skin layout; the head is left clear (faces and hair are separate layers), hands bare."""
import os, random
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src/main/resources/assets/breachprotocol/textures/entity/staff/uniform")
os.makedirs(OUT, exist_ok=True)

def put(img, x, y, c):
    img.putpixel((x, y), c)

def noisy(img, x, y, w, h, c, rnd, amt=7):
    for i in range(w):
        for j in range(h):
            n = rnd.randint(-amt, amt)
            put(img, x + i, y + j, tuple(max(0, min(255, v + n)) for v in c[:3]) + (c[3] if len(c) > 3 else 255,))

def faces(u, v, w, h, d):
    # top, bottom, right, front, left, back
    return [(u + d, v, w, d), (u + d + w, v, w, d), (u, v + d, d, h), (u + d, v + d, w, h), (u + d + w, v + d, d, h), (u + 2 * d + w, v + d, w, h)]

def box(img, u, v, w, h, d, c, rnd, skip_bottom=False, bare_rows=0):
    for k, (x, y, ww, hh) in enumerate(faces(u, v, w, h, d)):
        if k == 1 and skip_bottom: continue
        rows = hh - (bare_rows if k >= 2 else 0)
        if rows > 0: noisy(img, x, y, ww, rows, c, rnd)

def shade(c, k): return tuple(max(0, min(255, int(v * k))) for v in c[:3]) + (255,)

BODY, RARM, LARM, RLEG, LLEG = (16, 16), (40, 16), (32, 48), (0, 16), (16, 48)

def uniform(name, top, legs, v, badge=None, under=None, vest=None, cap=None, stripe=None, cross=None, skirt=False):
    rnd = random.Random(1000 + v * 17 + len(name))
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    tone = shade(top, 0.92 + v * 0.06)
    box(img, *BODY, 8, 12, 4, tone, rnd)
    for a in (RARM, LARM): box(img, *a, 4, 12, 4, tone, rnd, skip_bottom=True, bare_rows=2)   # sleeves stop at the wrist
    if skirt:
        for l in (RLEG, LLEG):
            box(img, *l, 4, 12, 4, shade(top, 0.85), rnd)
            for (x, y, w, h) in faces(*l, 4, 12, 4)[2:]:
                noisy(img, x, y + 7, w, 3, (40, 30, 34, 110), rnd, 3)   # sheer stockings
                noisy(img, x, y + 10, w, 2, (20, 18, 22, 255), rnd, 2)  # low heels
    else:
        for l in (RLEG, LLEG):
            box(img, *l, 4, 12, 4, legs, rnd)
            for (x, y, w, h) in faces(*l, 4, 12, 4)[2:]: noisy(img, x, y + 10, w, 2, (26, 22, 20, 255), rnd, 3)  # shoes
    fx, fy = BODY[0] + 4, BODY[1] + 4   # body front face
    if under:
        noisy(img, fx + 3, fy, 2, 5, under, rnd, 3); put(img, fx + 2, fy, under); put(img, fx + 5, fy, under)
    if vest:
        noisy(img, fx, fy + 2, 8, 7, vest, rnd); noisy(img, fx + 16, fy + 2, 8, 7, vest, rnd)
    if stripe:
        noisy(img, fx, fy + 7, 8, 1, stripe, rnd, 2); noisy(img, RARM[0] + 4, RARM[1] + 4 + 6, 4, 1, stripe, rnd, 2)
    if badge: put(img, fx + 1, fy + 2, badge); put(img, fx + 2, fy + 2, badge)
    if cross:
        for (x, y) in [(fx + 5, fy + 2), (fx + 5, fy + 3), (fx + 5, fy + 4), (fx + 4, fy + 3), (fx + 6, fy + 3)]: put(img, x, y, cross)
    noisy(img, fx, fy + 11, 8, 1, shade(top, 0.7), rnd, 2)   # belt
    if cap:
        # a cap on the outer head layer: crown and a brim band
        noisy(img, 40, 0, 8, 8, cap, rnd)
        for (x, y, w, h) in [(32, 8, 8, 3), (40, 8, 8, 3), (48, 8, 8, 3), (56, 8, 8, 3)]: noisy(img, x, y, w, h, cap, rnd)
        noisy(img, 40, 11, 8, 1, shade(cap, 0.6), rnd, 2)
    img.save(os.path.join(OUT, f"{name}_{v}.png"))

for v in range(3):
    uniform("researcher", (232, 234, 236, 255), (52, 56, 66, 255), v, badge=(200, 40, 40, 255), under=(70, 100, 150, 255))
    uniform("guard", (34, 38, 44, 255), (30, 34, 40, 255), v, vest=(58, 64, 52, 255), badge=(220, 200, 80, 255), cap=(28, 30, 34, 255))
    uniform("medic", (120, 180, 200, 255), (110, 170, 190, 255), v, cross=(220, 40, 40, 255))
    uniform("technician", (210, 120, 40, 255), (190, 110, 36, 255), v, stripe=(230, 230, 80, 255), cap=(220, 200, 60, 255))
    uniform("clerk", (80, 84, 92, 255), (60, 62, 70, 255), v, badge=(200, 40, 40, 255), under=(230, 230, 235, 255))
    uniform("janitor", (60, 90, 70, 255), (54, 80, 62, 255), v, stripe=(200, 200, 200, 255))
    uniform("secretary", (38, 48, 78, 255), (38, 48, 78, 255), v, under=(238, 238, 242, 255), badge=(200, 170, 80, 255), skirt=True)
print("uniforms", len(os.listdir(OUT)))
