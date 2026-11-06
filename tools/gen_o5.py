"""Original looks for the thirteen Overseers (64x64 humanoid layout). Faces stay hidden, as in the lore."""
import os, random
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src/main/resources/assets/breachprotocol/textures/entity/o5")
os.makedirs(OUT, exist_ok=True)

def box(img, u, v, w, h, d, faces):
    """faces: dict of top,bottom,right,front,left,back -> colour or callable(img, x, y, w, h)."""
    rects = {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h),
             "front": (u + d, v + d, w, h), "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h)}
    for k, (x, y, ww, hh) in rects.items():
        f = faces.get(k, faces.get("all"))
        if f is None:
            continue
        if callable(f):
            f(img, x, y, ww, hh)
        else:
            for i in range(ww):
                for j in range(hh):
                    img.putpixel((x + i, y + j), f)

def shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c[:3]) + (255,)

def noisy(c, amt=10, seed=0):
    rnd = random.Random(seed)
    def f(img, x, y, w, h):
        for i in range(w):
            for j in range(h):
                n = rnd.randint(-amt, amt)
                img.putpixel((x + i, y + j), tuple(max(0, min(255, v + n)) for v in c[:3]) + (255,))
    return f

def px(img, x, y, c):
    img.putpixel((x, y), c)

def rect(img, x, y, w, h, c):
    for i in range(w):
        for j in range(h):
            img.putpixel((x + i, y + j), c)

# head front = (8,8) 8x8; hat front = (40,8); body front = (20,20) 8x12; arm front = (44,20) 4x12; leg front = (4,20) 4x12
def make(seat, suit, shirt, tie, legs, skin=(196, 152, 120, 255), face="shadow", extra=None, hood=None, hair=None):
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    s = seat
    box(img, 16, 16, 8, 12, 4, {"all": noisy(shade(suit, 0.85), 6, s), "front": noisy(suit, 6, s + 1)})
    box(img, 40, 16, 4, 12, 4, {"all": noisy(shade(suit, 0.8), 6, s + 2), "front": noisy(suit, 6, s + 3), "bottom": skin})
    box(img, 0, 16, 4, 12, 4, {"all": noisy(shade(legs, 0.85), 5, s + 4), "front": noisy(legs, 5, s + 5)})
    # shoes
    for x0 in (0, 4, 8, 12):
        rect(img, x0, 30, 4, 2, (24, 20, 18, 255))
    # shirt + tie on the jacket front
    rect(img, 22, 20, 4, 5, shirt)
    if tie:
        rect(img, 23, 21, 2, 6, tie)
        px(img, 23, 20, shade(tie, 0.8)); px(img, 24, 20, shade(tie, 0.8))
    # cuffs and hands
    rect(img, 44, 30, 4, 2, skin)
    # head
    hcol = hood if hood else skin
    box(img, 0, 0, 8, 8, 8, {"all": noisy(shade(hcol, 0.9), 4, s + 6)})
    if hair:
        box(img, 0, 0, 8, 8, 8, {"top": hair, "back": hair})
        rect(img, 0, 8, 8, 2, hair); rect(img, 16, 8, 8, 2, hair); rect(img, 8, 8, 8, 1, hair)
    # the face: never seen clearly
    if face == "shadow":
        rect(img, 9, 10, 6, 5, (18, 16, 20, 255))
        px(img, 10, 12, (90, 80, 70, 255)); px(img, 13, 12, (90, 80, 70, 255))
    elif face == "visor":
        rect(img, 8, 10, 8, 3, (20, 40, 26, 255)); rect(img, 9, 11, 6, 1, (92, 255, 138, 255))
        rect(img, 9, 13, 6, 3, (16, 18, 20, 255))
    elif face == "blindfold":
        rect(img, 8, 11, 8, 2, (235, 232, 222, 255))
        rect(img, 10, 14, 4, 1, (120, 80, 70, 255))
    elif face == "mask_white":
        rect(img, 9, 9, 6, 6, (236, 234, 228, 255)); px(img, 10, 11, (10, 10, 12, 255)); px(img, 13, 11, (10, 10, 12, 255))
    elif face == "mask_split":
        rect(img, 9, 9, 3, 6, (236, 234, 228, 255)); rect(img, 12, 9, 3, 6, (14, 14, 16, 255))
        px(img, 10, 11, (14, 14, 16, 255)); px(img, 13, 11, (236, 234, 228, 255))
    elif face == "beak":
        rect(img, 9, 9, 6, 6, (22, 22, 26, 255)); px(img, 10, 11, (240, 200, 60, 255)); px(img, 13, 11, (240, 200, 60, 255))
        rect(img, 11, 13, 2, 2, (60, 58, 66, 255))
    elif face == "glasses":
        rect(img, 9, 10, 6, 5, (60, 50, 46, 255))
        for gx in (9, 12):
            rect(img, gx, 11, 3, 2, (180, 200, 210, 255))
        px(img, 12, 11, (30, 30, 30, 255))
    if extra:
        extra(img)
    img.save(os.path.join(OUT, "o5_%d.png" % seat))

GOLD = (220, 180, 60, 255)
def founder(img):          # long black coat, gold Foundation pin, white hair
    rect(img, 20, 30, 8, 2, (18, 18, 20, 255)); px(img, 21, 22, GOLD); px(img, 21, 23, GOLD)
def gardener(img):         # flower in the lapel
    px(img, 26, 21, (230, 90, 140, 255)); px(img, 26, 22, (90, 170, 80, 255))
def ghost(img):            # hood over the hat layer
    for x in range(32, 64):
        for y in range(0, 16):
            pass
    box(img, 32, 0, 8, 8, 8, {"top": (30, 34, 36, 255), "back": (30, 34, 36, 255), "right": (30, 34, 36, 255), "left": (30, 34, 36, 255)})
    rect(img, 40, 8, 8, 1, (30, 34, 36, 255))
def ambassador(img):       # diplomatic sash
    for i in range(10):
        px(img, 20 + min(7, i * 7 // 9), 20 + i, (170, 30, 40, 255))
def blackbird(img):        # feathered collar
    rect(img, 20, 20, 8, 1, (8, 8, 10, 255)); rect(img, 44, 20, 4, 1, (8, 8, 10, 255))
def cowboy(img):           # hat brim on the hat layer, bandana
    box(img, 32, 0, 8, 8, 8, {"top": (110, 76, 40, 255)})
    rect(img, 40, 8, 8, 2, (110, 76, 40, 255)); rect(img, 32, 8, 8, 2, (110, 76, 40, 255)); rect(img, 48, 8, 8, 2, (110, 76, 40, 255)); rect(img, 56, 8, 8, 2, (110, 76, 40, 255))
    rect(img, 22, 20, 4, 2, (150, 40, 36, 255))
def cardinal(img):         # crimson hood and robe trim, gold cross-free crest
    box(img, 32, 0, 8, 8, 8, {"top": (130, 16, 24, 255), "back": (130, 16, 24, 255), "right": (130, 16, 24, 255), "left": (130, 16, 24, 255)})
    rect(img, 40, 8, 8, 1, (130, 16, 24, 255)); px(img, 24, 24, GOLD)
def dogwood(img):          # medals and epaulettes
    for mx, c in ((21, (200, 60, 50, 255)), (22, GOLD), (21, (60, 90, 170, 255))):
        pass
    px(img, 21, 22, (200, 60, 50, 255)); px(img, 22, 22, GOLD); px(img, 21, 23, (60, 90, 170, 255))
    rect(img, 44, 20, 4, 1, GOLD)
def oracle(img):           # white robe hem
    rect(img, 20, 30, 8, 2, (230, 228, 220, 255))
def archivist(img):        # cardigan buttons
    for y in (22, 25, 28):
        px(img, 24, y, (90, 60, 40, 255))
def mailman(img):          # cap and red trim
    box(img, 32, 0, 8, 8, 8, {"top": (30, 44, 90, 255)})
    rect(img, 40, 8, 8, 2, (30, 44, 90, 255)); rect(img, 40, 9, 8, 1, (12, 12, 16, 255))
    rect(img, 20, 31, 8, 1, (170, 30, 36, 255))
def accountant(img):       # pinstripes and a pocket square
    for x in (20, 23, 26):
        for y in range(25, 32, 1):
            img.putpixel((x, y), (120, 124, 132, 255))
    px(img, 26, 21, (240, 240, 240, 255))
def tiebreaker(img):       # half black, half white suit
    rect(img, 24, 20, 4, 12, (230, 228, 222, 255)); rect(img, 22, 20, 2, 5, (14, 14, 16, 255))

make(1, (24, 24, 28, 255), (220, 220, 215, 255), (30, 30, 34, 255), (26, 26, 30, 255), face="shadow", hair=(220, 220, 220, 255), extra=founder)
make(2, (40, 78, 46, 255), (230, 226, 210, 255), (120, 160, 80, 255), (60, 52, 40, 255), face="shadow", hair=(110, 80, 50, 255), extra=gardener)
make(3, (30, 34, 36, 255), (20, 40, 26, 255), None, (24, 26, 28, 255), face="visor", hood=(30, 34, 36, 255), extra=ghost)
make(4, (30, 40, 80, 255), (235, 235, 240, 255), (180, 150, 60, 255), (28, 34, 64, 255), face="shadow", hair=(40, 30, 24, 255), extra=ambassador)
make(5, (14, 14, 18, 255), (40, 40, 46, 255), (14, 14, 18, 255), (14, 14, 18, 255), face="beak", hood=(14, 14, 18, 255), extra=blackbird)
make(6, (120, 86, 52, 255), (200, 190, 160, 255), None, (60, 70, 96, 255), face="shadow", hair=(90, 64, 40, 255), extra=cowboy)
make(7, (130, 16, 24, 255), (240, 236, 230, 255), (90, 10, 16, 255), (100, 12, 20, 255), face="shadow", hood=(130, 16, 24, 255), extra=cardinal)
make(8, (78, 86, 52, 255), (190, 180, 140, 255), (60, 64, 40, 255), (70, 76, 46, 255), face="shadow", hair=(50, 40, 30, 255), extra=dogwood)
make(9, (232, 230, 222, 255), (210, 206, 196, 255), None, (220, 218, 210, 255), face="blindfold", hair=(180, 170, 160, 255), extra=oracle)
make(10, (110, 92, 70, 255), (220, 214, 196, 255), (70, 40, 30, 255), (70, 60, 50, 255), face="glasses", hair=(150, 150, 150, 255), extra=archivist)
make(11, (30, 44, 90, 255), (220, 220, 225, 255), (170, 30, 36, 255), (28, 36, 70, 255), face="shadow", extra=mailman)
make(12, (78, 80, 86, 255), (235, 235, 235, 255), (40, 70, 120, 255), (66, 68, 74, 255), face="glasses", hair=(40, 34, 30, 255), extra=accountant)
make(13, (14, 14, 16, 255), (236, 234, 228, 255), (120, 120, 120, 255), (14, 14, 16, 255), face="mask_split", hood=(20, 20, 22, 255), extra=tiebreaker)
print("o5 textures generated")
