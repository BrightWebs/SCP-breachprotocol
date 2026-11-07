"""Original uniforms for Foundation personnel (64x64 humanoid layout, zombie/player UV). Three looks per department."""
import os, random
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src/main/resources/assets/breachprotocol/textures/entity/staff")
os.makedirs(OUT, exist_ok=True)

def rect(img, x, y, w, h, c):
    for i in range(w):
        for j in range(h):
            img.putpixel((x + i, y + j), c)

def noisy(img, x, y, w, h, c, rnd, amt=8):
    for i in range(w):
        for j in range(h):
            n = rnd.randint(-amt, amt)
            img.putpixel((x + i, y + j), tuple(max(0, min(255, v + n)) for v in c[:3]) + (255,))

def box(img, u, v, w, h, d, c, rnd, amt=8):
    """Paint every face of a cuboid in the standard skin layout."""
    for (x, y, ww, hh) in [(u + d, v, w, d), (u + d + w, v, w, d), (u, v + d, d, h), (u + d, v + d, w, h), (u + d + w, v + d, d, h), (u + 2 * d + w, v + d, w, h)]:
        noisy(img, x, y, ww, hh, c, rnd, amt)

def shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c[:3]) + (255,)

SKIN = [(224, 186, 150, 255), (176, 124, 88, 255), (110, 74, 50, 255)]
HAIR = [(60, 40, 26, 255), (24, 20, 18, 255), (150, 110, 60, 255)]

def face(img, skin, hair, rnd, glasses=False, cap=None):
    box(img, 0, 0, 8, 8, 8, skin, rnd, 4)
    rect(img, 8, 0, 8, 8, hair)            # top
    rect(img, 8, 8, 8, 2, hair)            # fringe
    rect(img, 0, 8, 8, 3, hair); rect(img, 16, 8, 8, 3, hair); rect(img, 24, 8, 8, 8, hair)  # sides, back
    rect(img, 9, 12, 2, 1, (250, 250, 250, 255)); rect(img, 13, 12, 2, 1, (250, 250, 250, 255))
    img.putpixel((10, 12), (40, 50, 70, 255)); img.putpixel((13, 12), (40, 50, 70, 255))
    rect(img, 11, 14, 2, 1, shade(skin, 0.8))
    rect(img, 10, 15, 4, 1, shade(skin, 0.7))
    if glasses:
        for x in (9, 13):
            rect(img, x, 11, 2, 1, (30, 30, 30, 255)); rect(img, x, 13, 2, 1, (30, 30, 30, 255))
        img.putpixel((11, 12), (30, 30, 30, 255)); img.putpixel((12, 12), (30, 30, 30, 255))
    if cap:
        for (x, y, w, h) in [(40, 0, 8, 8), (40, 8, 8, 3), (32, 8, 8, 3), (48, 8, 8, 3), (56, 8, 8, 3)]:
            rect(img, x, y, w, h, cap)

def extras(img, top, front, skin, rnd):
    """Model extras painted in the free lower half: three chest builds (for women) and eyelids (for blinking)."""
    for (u, v, w, h, d) in [(0, 32, 6, 3, 1), (0, 40, 6, 3, 2), (0, 48, 6, 4, 3)]:
        box(img, u, v, w, h, d, top, rnd, 5)
        # the front: the shirt/blouse showing, shaded so it reads as a shape
        for j in range(h):
            k = 1.08 - 0.12 * j
            for i in range(w):
                c = front if (front and 2 <= i <= 3) else top
                img.putpixel((u + d + i, v + d + j), shade(c, k))
    box(img, 24, 32, 6, 1, 1, skin, rnd, 2)

FHAIR_ALL = [(70, 44, 28, 255), (22, 18, 16, 255), (168, 92, 52, 255)]

def feminine(img, skin, hair, rnd, cap=None):
    """Long hair framing the face, lashes, lips."""
    if not cap:
        rect(img, 0, 8, 8, 8, hair); rect(img, 16, 8, 8, 8, hair); rect(img, 24, 8, 8, 8, hair)
        rect(img, 8, 8, 8, 1, hair); rect(img, 8, 9, 5, 1, hair)
        rect(img, 8, 10, 1, 6, hair); rect(img, 15, 10, 1, 6, hair)
        rect(img, 32, 20, 8, 5, hair)                   # down her back
    else:
        rect(img, 24, 11, 8, 5, hair)                   # tied back under the cap
        rect(img, 32, 20, 4, 3, hair)
    for ex in (9, 13):
        rect(img, ex, 11, 2, 1, (30, 24, 22, 255))
    rect(img, 11, 15, 2, 1, (176, 84, 92, 255))

def make(name, top, under, legs, rnd, v, badge=None, vest=None, glasses=False, cap=None, stripe=None, cross=None, old=False, female=False):
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    skin = SKIN[v]
    hair = shade(SKIN[v], 0.93)  # no painted hair: hairstyles are separate layers, tinted per person
    face(img, skin, hair, rnd, glasses, cap)
    if female:
        for ex in (9, 13): rect(img, ex, 11, 2, 1, (30, 24, 22, 255))
        rect(img, 11, 15, 2, 1, (176, 84, 92, 255))
        if glasses:
            for x in (9, 13):
                rect(img, x, 11, 2, 1, (30, 30, 30, 255)); rect(img, x, 13, 2, 1, (30, 30, 30, 255))
    if old:
        for (x, y) in [(9, 11), (14, 11), (10, 14), (13, 14)]: img.putpixel((x, y), shade(skin, 0.82))
    box(img, 16, 16, 8, 12, 4, top, rnd)            # body

    box(img, 40, 16, 4, 12, 4, top, rnd)            # arm
    box(img, 0, 16, 4, 12, 4, legs, rnd)            # leg
    rect(img, 44, 30, 4, 2, skin); rect(img, 40, 30, 4, 2, skin)      # hands
    for x in (0, 4, 8, 12): rect(img, x, 30, 4, 2, (26, 22, 20, 255))  # shoes
    if under:
        rect(img, 22, 20, 4, 6, under)              # shirt showing under an open coat / vest
    if vest:
        rect(img, 20, 22, 8, 7, vest); rect(img, 32, 22, 8, 7, vest)
    if stripe:
        rect(img, 20, 27, 8, 1, stripe); rect(img, 44, 26, 4, 1, stripe)
    if badge:
        rect(img, 21, 22, 2, 2, badge)
    if cross:
        rect(img, 25, 22, 1, 3, cross); rect(img, 24, 23, 3, 1, cross)
    extras(img, vest or top, under, skin, rnd)
    img.save(os.path.join(OUT, f"{name}_{v}{'_f' if female else ''}{'_old' if old else ''}.png"))

for v, old, female in [(a, o, f) for f in (False, True) for o in (False, True) for a in range(3)]:
    rnd = random.Random(100 + v + (50 if female else 0))
    make("researcher", (232, 234, 236, 255), (70, 100, 150, 255), (52, 56, 66, 255), rnd, v, badge=(200, 40, 40, 255), glasses=(v != 1), old=old, female=female)
    make("guard", (34, 38, 44, 255), None, (30, 34, 40, 255), rnd, v, vest=(58, 64, 52, 255), badge=(220, 200, 80, 255), cap=(28, 30, 34, 255), old=old, female=female)
    make("medic", (120, 180, 200, 255), None, (110, 170, 190, 255), rnd, v, cross=(220, 40, 40, 255), old=old, female=female)
    make("technician", (210, 120, 40, 255), None, (190, 110, 36, 255), rnd, v, stripe=(230, 230, 80, 255), cap=(220, 200, 60, 255), old=old, female=female)
    make("clerk", (80, 84, 92, 255), (230, 230, 235, 255), (60, 62, 70, 255), rnd, v, badge=(200, 40, 40, 255), old=old, female=female)
    make("janitor", (60, 90, 70, 255), None, (54, 80, 62, 255), rnd, v, stripe=(200, 200, 200, 255), old=old, female=female)
print("ok", len(os.listdir(OUT)))

# ---------------------------------------------------------------- the Administrator's secretary
FHAIR = [(70, 44, 28, 255), (22, 18, 16, 255), (168, 92, 52, 255)]

def secretary(v, old):
    rnd = random.Random(300 + v)
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    skin = SKIN[v]
    hair = shade(skin, 0.93)
    box(img, 0, 0, 8, 8, 8, skin, rnd, 3)
    # long hair: crown, a side-swept fringe, full sides and back
    rect(img, 8, 0, 8, 8, hair)
    rect(img, 8, 8, 8, 1, hair); rect(img, 8, 9, 5, 1, hair); rect(img, 8, 10, 2, 1, hair)
    rect(img, 0, 8, 8, 8, hair); rect(img, 16, 8, 8, 8, hair); rect(img, 24, 8, 8, 8, hair)
    rect(img, 8, 11, 1, 5, hair); rect(img, 15, 10, 1, 6, hair)   # hair framing the face
    # eyes with lashes, soft cheeks, lips
    for ex in (9, 13):
        rect(img, ex, 12, 2, 1, (250, 250, 250, 255))
        rect(img, ex, 11, 2, 1, (30, 24, 22, 255))
    img.putpixel((10, 12), (70, 110, 80, 255) if v == 2 else (60, 70, 100, 255)); img.putpixel((13, 12), (70, 110, 80, 255) if v == 2 else (60, 70, 100, 255))
    img.putpixel((9, 13), shade(skin, 0.95)); img.putpixel((14, 13), shade(skin, 0.95))
    rect(img, 11, 13, 2, 1, shade(skin, 0.9))
    rect(img, 11, 15, 2, 1, (176, 84, 92, 255))
    if old:
        for (x, y) in [(9, 14), (14, 14)]: img.putpixel((x, y), shade(skin, 0.85))
    # a tailored navy suit jacket over a white blouse, a small Foundation pin
    suit = (38, 48, 78, 255)
    box(img, 16, 16, 8, 12, 4, suit, rnd, 5)
    box(img, 40, 16, 4, 12, 4, suit, rnd, 5)
    rect(img, 22, 20, 4, 5, (238, 238, 242, 255)); rect(img, 23, 25, 2, 1, (238, 238, 242, 255))
    img.putpixel((21, 22), (200, 170, 80, 255))
    rect(img, 20, 27, 8, 1, shade(suit, 0.75))          # belt line

    rect(img, 44, 30, 4, 2, skin); rect(img, 40, 30, 4, 2, skin)
    # pencil skirt, stockings, low heels
    box(img, 0, 16, 4, 12, 4, shade(suit, 0.85), rnd, 4)
    for x in (0, 4, 8, 12):
        rect(img, x, 27, 4, 3, shade(skin, 0.82))
        rect(img, x, 30, 4, 2, (20, 18, 22, 255))
    extras(img, suit, (238, 238, 242, 255), skin, rnd)
    img.save(os.path.join(OUT, f"secretary_{v}{'_old' if old else ''}.png"))

for v in range(3):
    for old in (False, True):
        secretary(v, old)
print("secretary textures generated")
