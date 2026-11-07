"""Original 16x16 fruit icons for the Garden, plus the Immortal effect icon (18x18)."""
import os, math
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ITEM = os.path.join(ROOT, "src/main/resources/assets/breachprotocol/textures/item")
EFF = os.path.join(ROOT, "src/main/resources/assets/breachprotocol/textures/mob_effect")
os.makedirs(ITEM, exist_ok=True); os.makedirs(EFF, exist_ok=True)

def blob(img, cx, cy, rx, ry, base, light, dark, outline=True):
    w, h = img.size
    for x in range(w):
        for y in range(h):
            d = ((x + 0.5 - cx) / rx) ** 2 + ((y + 0.5 - cy) / ry) ** 2
            if d <= 1.0:
                lx, ly = (x + 0.5 - (cx - rx * 0.35)) / rx, (y + 0.5 - (cy - ry * 0.35)) / ry
                c = light if lx * lx + ly * ly < 0.18 else dark if d > 0.62 and (x > cx or y > cy) else base
                img.putpixel((x, y), c)
    if outline:
        px = img.load()
        edge = []
        for x in range(w):
            for y in range(h):
                if px[x, y][3] == 0 and any(0 <= x + a < w and 0 <= y + b < h and px[x + a, y + b][3] == 255 and px[x + a, y + b][:3] != (0, 0, 0)
                                            for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                    edge.append((x, y))
        for (x, y) in edge:
            px[x, y] = tuple(max(0, v // 3) for v in dark[:3]) + (255,)

def stem(img, pts, c=(92, 62, 30, 255)):
    for p in pts: img.putpixel(p, c)

def leaf(img, pts, c=(70, 150, 60, 255), d=(40, 100, 40, 255)):
    for i, p in enumerate(pts): img.putpixel(p, c if i % 2 == 0 else d)

def new(): return Image.new("RGBA", (16, 16), (0, 0, 0, 0))

def save(img, name): img.save(os.path.join(ITEM, name + ".png"))

# fig: purple teardrop
i = new(); blob(i, 8, 9.5, 4.2, 4.8, (110, 50, 110, 255), (170, 110, 170, 255), (70, 25, 70, 255)); blob(i, 8, 5, 2, 2.4, (110, 50, 110, 255), (150, 90, 150, 255), (70, 25, 70, 255))
stem(i, [(8, 1), (8, 2)]); leaf(i, [(9, 1), (10, 1), (10, 2)]); save(i, "fig")
# pomegranate: deep red with crown
i = new(); blob(i, 8, 9, 5.2, 5, (180, 30, 40, 255), (240, 110, 110, 255), (110, 15, 25, 255))
for p in [(7, 3), (8, 2), (9, 3), (6, 4), (10, 4)]: i.putpixel(p, (130, 20, 30, 255))
save(i, "pomegranate")
# olives: two small green ovals on a twig
i = new(); stem(i, [(3, 3), (4, 4), (5, 5), (6, 6), (7, 6), (8, 6), (9, 5), (10, 4)]); leaf(i, [(11, 3), (12, 2), (12, 3), (13, 2)], (120, 150, 110, 255), (90, 120, 80, 255))
blob(i, 6.5, 10, 2.4, 3, (110, 140, 40, 255), (170, 190, 90, 255), (70, 90, 20, 255)); blob(i, 11, 9, 2.4, 3, (60, 80, 30, 255), (110, 130, 70, 255), (35, 50, 15, 255)); save(i, "olive")
# grapes: purple cluster
i = new(); stem(i, [(8, 1), (8, 2), (9, 2)]); leaf(i, [(10, 1), (11, 1), (11, 2), (12, 2)])
for (x, y) in [(6, 5), (9, 5), (12, 5), (5, 8), (8, 8), (11, 8), (6.5, 11), (9.5, 11), (8, 13.5)]:
    blob(i, x + 0.5, y + 0.5, 1.8, 1.8, (90, 50, 140, 255), (160, 120, 210, 255), (55, 25, 90, 255), outline=False)
save(i, "grapes")
# dates: brown oblong pair
i = new(); blob(i, 6, 9, 2.4, 4.6, (130, 70, 30, 255), (190, 120, 70, 255), (80, 40, 15, 255)); blob(i, 10.5, 8, 2.4, 4.6, (110, 55, 25, 255), (170, 105, 60, 255), (70, 35, 12, 255)); save(i, "date")
# pear: yellow-green
i = new(); blob(i, 8, 10.5, 4.6, 4.2, (190, 200, 70, 255), (235, 240, 150, 255), (120, 140, 40, 255)); blob(i, 8, 6, 2.4, 3, (190, 200, 70, 255), (230, 235, 140, 255), (120, 140, 40, 255))
stem(i, [(8, 1), (8, 2)]); leaf(i, [(9, 2), (10, 1), (10, 2)]); save(i, "pear")
# peach: orange-pink with a crease
i = new(); blob(i, 8, 9, 5, 5, (245, 160, 110, 255), (255, 210, 170, 255), (200, 90, 70, 255))
for y in range(5, 13): i.putpixel((8, y), (215, 110, 80, 255))
leaf(i, [(8, 3), (9, 2), (10, 2), (10, 3)]); save(i, "peach")
# plum: deep blue-purple
i = new(); blob(i, 8, 9, 4.6, 5, (80, 40, 110, 255), (150, 110, 190, 255), (45, 20, 70, 255)); stem(i, [(8, 3), (8, 2)]); save(i, "plum")
# fruit of life: radiant golden-white with a halo
i = new()
for x in range(16):
    for y in range(16):
        d = math.hypot(x + 0.5 - 8, y + 0.5 - 8.5)
        if 6.2 < d < 7.2 and (x + y) % 2 == 0: i.putpixel((x, y), (255, 240, 160, 170))
blob(i, 8, 8.5, 4.4, 4.6, (255, 215, 90, 255), (255, 255, 225, 255), (220, 150, 30, 255))
leaf(i, [(8, 3), (9, 2), (10, 2), (11, 2), (10, 3)], (120, 220, 120, 255), (80, 180, 90, 255)); save(i, "fruit_of_life")
# fruit of knowledge: dark crimson with a bite and a dark leaf
i = new(); blob(i, 8, 9, 5, 5, (150, 10, 30, 255), (220, 70, 80, 255), (80, 0, 15, 255))
for (x, y) in [(12, 7), (13, 7), (12, 8), (13, 8), (13, 9), (12, 6), (13, 6)]: i.putpixel((x, y), (0, 0, 0, 0))
for (x, y) in [(11, 7), (11, 8), (12, 9)]: i.putpixel((x, y), (240, 220, 190, 255))
stem(i, [(8, 3), (8, 2)]); leaf(i, [(9, 2), (10, 1), (11, 1), (10, 2)], (40, 80, 40, 255), (20, 50, 25, 255)); save(i, "fruit_of_knowledge")
# Immortal effect icon (18x18): a golden ring around a green sprout
e = Image.new("RGBA", (18, 18), (0, 0, 0, 0))
for x in range(18):
    for y in range(18):
        d = math.hypot(x + 0.5 - 9, y + 0.5 - 9)
        if 6.6 < d < 8.2: e.putpixel((x, y), (255, 210, 80, 255) if (x + y) % 3 else (255, 245, 180, 255))
for y in range(6, 14): e.putpixel((9, y), (90, 170, 70, 255))
for p in [(8, 7), (7, 6), (6, 6), (10, 8), (11, 7), (12, 7)]: e.putpixel(p, (120, 210, 100, 255))
e.save(os.path.join(EFF, "immortal.png"))
print("ok")
