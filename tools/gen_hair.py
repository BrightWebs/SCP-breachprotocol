"""Original hairstyle, facial hair and glasses overlays for staff (64x64, standard head/body UV).
Drawn in light greys so the game can tint each person's hair their own colour."""
import os, random, zlib
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src/main/resources/assets/breachprotocol/textures/entity/staff/hair")
os.makedirs(OUT, exist_ok=True)

def px(img, x, y, rnd, a=255, base=215):
    v = max(120, min(255, base + rnd.randint(-28, 22)))
    img.putpixel((x, y), (v, v, v, a))

def fill(img, x, y, w, h, rnd, a=255, base=215):
    for i in range(w):
        for j in range(h):
            px(img, x + i, y + j, rnd, a, base)

# head faces: top (8,0) front (8,8) right (0,8) left (16,8) back (24,8); body back (32,20)
def style(name, top=True, front=0, frame=0, sides=0, back=0, body=0, bodyw=8, curls=False, part=False, bun=False, capped=False):
    rnd = random.Random(zlib.crc32(name.encode()))
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    if top and not capped: fill(img, 8, 0, 8, 8, rnd)
    if not capped:
        fill(img, 8, 8, 8, front, rnd)
        if part: fill(img, 8, 8 + front, 3, 1, rnd)
        if frame:
            fill(img, 8, 8 + front, 1, frame, rnd); fill(img, 15, 8 + front, 1, frame, rnd)
    s0 = 3 if capped else 0
    if sides > s0:
        fill(img, 0, 8 + s0, 8, sides - s0, rnd); fill(img, 16, 8 + s0, 8, sides - s0, rnd)
    if back > s0: fill(img, 24, 8 + s0, 8, back - s0, rnd)
    if curls:
        for (x, y) in [(8, 8 + front), (11, 8 + front), (14, 8 + front), (0, 8 + sides), (3, 8 + sides), (6, 8 + sides), (16, 8 + sides), (19, 8 + sides), (22, 8 + sides), (25, 8 + back), (29, 8 + back)]:
            if y < 16: px(img, x, y, rnd)
    if bun:
        fill(img, 27, 9 if not capped else 11, 2, 2, rnd, base=170)
    if body:
        x0 = 32 + (8 - bodyw) // 2
        fill(img, x0, 20, bodyw, body, rnd)
    img.save(os.path.join(OUT, name + ".png"))

STYLES = {
    "short":    dict(front=1, sides=3, back=5),
    "buzz":     dict(front=1, sides=2, back=3),
    "sidepart": dict(front=1, sides=3, back=5, part=True),
    "curly":    dict(front=2, sides=5, back=7, curls=True),
    "long":     dict(front=1, frame=6, sides=8, back=8, body=6),
    "bob":      dict(front=2, frame=4, sides=6, back=6),
    "ponytail": dict(front=1, sides=3, back=5, body=8, bodyw=2),
    "bun":      dict(front=1, sides=3, back=5, bun=True),
}
for n, kw in STYLES.items():
    style(n, **kw)
    style(n + "_cap", capped=True, **kw)

def face_overlay(name, cells, alpha=255, base=200, untinted=None):
    rnd = random.Random(zlib.crc32(name.encode()))
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    for (x, y) in cells:
        if untinted: img.putpixel((x, y), untinted)
        else: px(img, x, y, rnd, alpha, base)
    img.save(os.path.join(OUT, name + ".png"))

full = [(x, 13) for x in (8, 9, 14, 15)] + [(x, 14) for x in range(8, 16)] + [(x, 15) for x in (8, 9, 14, 15)] + [(x, 15) for x in (10, 13)]
full += [(x, 13) for x in range(0, 2)] + [(x, 13) for x in range(22, 24)] + [(x, 14) for x in range(0, 3)] + [(x, 14) for x in range(21, 24)]
face_overlay("beard_full", full)
face_overlay("beard_stubble", [c for i, c in enumerate(full) if i % 2 == 0], alpha=150)
face_overlay("moustache", [(10, 14), (11, 14), (12, 14), (13, 14)])
g = (24, 24, 28, 255)
face_overlay("glasses", [(9, 11), (10, 11), (13, 11), (14, 11), (9, 13), (10, 13), (13, 13), (14, 13), (8, 12), (11, 12), (12, 12), (15, 12)], untinted=g)
print("hair overlays generated", len(os.listdir(OUT)))
