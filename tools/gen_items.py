"""Original 16x16 item/block art, effect icons and the mod logo."""
import os, random, math
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
A = os.path.join(ROOT, "src/main/resources/assets/breachprotocol/textures")
rng = random.Random(173)

def new(w=16, h=16): return Image.new("RGBA", (w, h), (0, 0, 0, 0))
def save(img, sub, name): os.makedirs(os.path.join(A, sub), exist_ok=True); img.save(os.path.join(A, sub, name + ".png"))
def shade(c, k): return tuple(max(0, min(255, int(v * k))) for v in c[:3]) + (255,)

# ------------------------------------------------------------------ keycards
CARD_COLORS = {1: (200, 200, 196), 2: (90, 150, 210), 3: (230, 190, 60), 4: (220, 120, 40), 5: (40, 40, 44)}
STRIPE = {1: (120, 120, 120), 2: (30, 70, 140), 3: (150, 30, 30), 4: (110, 30, 120), 5: (200, 160, 40)}
for lvl, col in CARD_COLORS.items():
    im = new(); d = ImageDraw.Draw(im)
    d.rectangle([1, 4, 14, 12], fill=shade(col, 0.55))          # outline
    d.rectangle([2, 5, 13, 11], fill=col + (255,))
    d.rectangle([2, 5, 13, 6], fill=STRIPE[lvl] + (255,))        # clearance band
    d.rectangle([3, 8, 5, 10], fill=(214, 178, 74, 255))         # chip
    d.point([(4, 9)], fill=(150, 116, 40, 255))
    for i in range(lvl):                                          # level pips
        d.point([(8 + i, 9)], fill=(255, 255, 255, 255) if lvl == 5 else (30, 30, 30, 255))
    d.line([(7, 11), (12, 11)], fill=shade(col, 0.75))
    save(im, "item", f"keycard_{lvl}")

# ------------------------------------------------------------------ SCP-500 pill
im = new(); d = ImageDraw.Draw(im)
for i in range(10):
    x, y = 3 + i, 12 - i
    c = (196, 24, 30) if i < 5 else (236, 230, 226)
    d.ellipse([x - 2, y - 2, x + 2, y + 2], fill=c + (255,))
d.ellipse([4, 7, 6, 9], fill=(255, 120, 120, 255))
d.ellipse([10, 3, 11, 4], fill=(255, 255, 255, 255))
save(im, "item", "scp_500")

# ------------------------------------------------------------------ SCP-207 bottle (unbranded)
im = new(); d = ImageDraw.Draw(im)
d.rectangle([7, 1, 8, 3], fill=(180, 180, 180, 255))                      # cap
d.rectangle([6, 4, 9, 5], fill=(70, 30, 20, 220))                         # neck
d.polygon([(5, 6), (10, 6), (11, 9), (11, 14), (4, 14), (4, 9)], fill=(58, 24, 14, 235))
d.rectangle([4, 9, 11, 11], fill=(170, 24, 28, 255))                      # label
d.line([(5, 10), (10, 10)], fill=(240, 230, 220, 255))
d.line([(5, 7), (5, 8)], fill=(150, 100, 80, 200))
d.line([(4, 15), (11, 15)], fill=(40, 16, 10, 255))
save(im, "item", "scp_207")

# ------------------------------------------------------------------ eyedrops
im = new(); d = ImageDraw.Draw(im)
d.polygon([(7, 1), (9, 1), (9, 5), (7, 5)], fill=(235, 235, 240, 255))
d.rectangle([5, 6, 11, 14], fill=(180, 220, 240, 200))
d.rectangle([5, 9, 11, 12], fill=(255, 255, 255, 255))
d.line([(6, 10), (10, 10)], fill=(40, 120, 200, 255))
d.point([(8, 11)], fill=(40, 120, 200, 255))
d.rectangle([5, 15, 11, 15], fill=(120, 150, 170, 255))
save(im, "item", "eyedrops")

# ------------------------------------------------------------------ incident log (guidebook-ish)
im = new(); d = ImageDraw.Draw(im)
d.rectangle([3, 2, 13, 14], fill=(40, 44, 52, 255))
d.rectangle([4, 3, 12, 13], fill=(226, 222, 210, 255))
for y in range(5, 12, 2): d.line([(5, y), (11, y)], fill=(120, 120, 120, 255))
d.rectangle([5, 4, 7, 6], fill=(170, 30, 30, 255))
save(im, "item", "site_terminal")

# ------------------------------------------------------------------ Foundation Phone
im = new(); d = ImageDraw.Draw(im)
d.rectangle([4, 1, 11, 14], fill=(22, 24, 30, 255))
d.rectangle([5, 3, 10, 11], fill=(20, 70, 90, 255))
d.rectangle([5, 3, 10, 4], fill=(63, 208, 255, 255))
d.line([(6, 6), (9, 6)], fill=(200, 240, 255, 255)); d.line([(6, 8), (8, 8)], fill=(120, 190, 210, 255))
d.point([(7, 12)], fill=(90, 90, 100, 255)); d.point([(8, 12)], fill=(90, 90, 100, 255))
d.point([(10, 2)], fill=(255, 60, 60, 255))
d.line([(4, 1), (4, 14)], fill=(50, 54, 62, 255))
save(im, "item", "phone")

# ------------------------------------------------------------------ blocks
def metal(base=(92, 98, 106), n=8):
    im = new(); px = im.load()
    for x in range(16):
        for y in range(16):
            k = rng.randint(-n, n)
            px[x, y] = tuple(max(0, min(255, c + k)) for c in base) + (255,)
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, 15, 15], outline=shade(base, 0.6))
    for (x, y) in ((1, 1), (14, 1), (1, 14), (14, 14)): d.point([(x, y)], fill=shade(base, 1.4))
    return im

for on in (False, True):
    im = metal(); d = ImageDraw.Draw(im)
    for i in range(0, 16, 4):  # hazard band
        d.polygon([(i, 13), (i + 2, 13), (i + 4, 15), (i + 2, 15)], fill=(230, 190, 40, 255))
        d.polygon([(i + 2, 13), (i + 4, 13), (i + 6, 15), (i + 4, 15)], fill=(30, 30, 30, 255))
    core = (90, 200, 255) if on else (40, 60, 80)
    d.rectangle([4, 3, 11, 10], fill=(28, 30, 34, 255))
    d.rectangle([5, 4, 10, 9], fill=core + (255,))
    if on:
        d.rectangle([6, 5, 9, 8], fill=(220, 250, 255, 255))
    d.line([(3, 11), (12, 11)], fill=(60, 64, 70, 255))
    save(im, "block", "containment_generator_side" + ("_on" if on else ""))
    top = metal((80, 86, 94)); d = ImageDraw.Draw(top)
    d.ellipse([3, 3, 12, 12], fill=(28, 30, 34, 255))
    d.ellipse([5, 5, 10, 10], fill=core + (255,))
    save(top, "block", "containment_generator_top" + ("_on" if on else ""))

for on in (False, True):
    im = metal((70, 74, 80)); d = ImageDraw.Draw(im)
    d.rectangle([3, 2, 12, 13], fill=(34, 36, 40, 255))
    d.rectangle([5, 4, 10, 5], fill=(10, 10, 12, 255))          # card slot
    d.rectangle([5, 8, 10, 11], fill=(20, 40, 30, 255))         # display
    d.line([(6, 9), (9, 9)], fill=(60, 230, 120, 255) if on else (60, 120, 90, 255))
    d.point([(11, 3)], fill=(60, 255, 120, 255) if on else (255, 40, 40, 255))
    save(im, "block", "keycard_reader" + ("_on" if on else ""))
save(metal((70, 74, 80)), "block", "keycard_reader_side")

# corrosion goo (translucent)
im = new(); px = im.load()
for x in range(16):
    for y in range(16):
        r = math.hypot(x - 7.5, y - 7.5) + rng.uniform(-1.6, 1.6)
        if r < 7.5:
            k = rng.randint(-6, 6)
            px[x, y] = (34 + k, 24 + k, 16 + k, 225 if r < 6 else 150)
            if rng.random() < 0.06: px[x, y] = (96, 70, 40, 230)
save(im, "block", "corrosion")

# reinforced containment wall
im = new(); px = im.load()
for x in range(16):
    for y in range(16):
        k = rng.randint(-7, 7)
        px[x, y] = (178 + k, 176 + k, 168 + k, 255)
d = ImageDraw.Draw(im)
d.rectangle([0, 0, 15, 15], outline=(120, 118, 112, 255))
d.line([(0, 7), (15, 7)], fill=(140, 138, 130, 255))
for x in (3, 11): d.point([(x, 3), (x, 11)], fill=(100, 100, 98, 255))
save(im, "block", "containment_wall")

# ascension altar
alt = new(); px = alt.load()
for x in range(16):
    for y in range(16):
        k = rng.randint(-8, 8)
        px[x, y] = (238 + min(17, k), 226 + k, 196 + k, 255)
d = ImageDraw.Draw(alt)
d.rectangle([0, 0, 15, 15], outline=(214, 170, 60, 255))
for y in (4, 11): d.line([(0, y), (15, y)], fill=(214, 170, 60, 255))
d.rectangle([6, 6, 9, 9], fill=(170, 80, 255, 255)); d.point([(7, 7)], fill=(255, 230, 255, 255))
save(alt, "block", "ascension_altar_side")
top = new(); px = top.load()
for x in range(16):
    for y in range(16):
        r = math.hypot(x - 7.5, y - 7.5)
        px[x, y] = (255, 240, 200, 255) if r > 5 else ((190, 110, 255, 255) if r > 2 else (255, 255, 255, 255))
ImageDraw.Draw(top).rectangle([0, 0, 15, 15], outline=(214, 170, 60, 255))
save(top, "block", "ascension_altar_top")

# ------------------------------------------------------------------ effect icons 18x18
def icon(draw_fn, name):
    im = new(18, 18); draw_fn(ImageDraw.Draw(im)); save(im, "mob_effect", name)
icon(lambda d: (d.rectangle([2, 2, 15, 15], outline=(90, 200, 255, 255), width=2), d.rectangle([6, 6, 11, 11], fill=(200, 240, 255, 255))), "contained")
icon(lambda d: (d.polygon([(7, 2), (11, 2), (12, 6), (12, 15), (6, 15), (6, 6)], fill=(70, 28, 16, 255)), d.rectangle([6, 8, 12, 10], fill=(190, 30, 30, 255))), "cola_fever")
icon(lambda d: (d.ellipse([2, 6, 15, 15], fill=(36, 26, 18, 255)), d.ellipse([6, 2, 10, 8], fill=(36, 26, 18, 255)), d.point([(5, 9), (11, 11)], fill=(110, 80, 40, 255))), "corrosion")
icon(lambda d: (d.ellipse([2, 5, 15, 12], fill=(240, 240, 240, 255)), d.ellipse([6, 5, 11, 12], fill=(40, 120, 200, 255)), d.ellipse([8, 7, 9, 9], fill=(10, 10, 10, 255))), "clear_eyes")

# ------------------------------------------------------------------ logo 128x128 (original emblem)
L = Image.new("RGBA", (128, 128), (0, 0, 0, 0)); d = ImageDraw.Draw(L)
hexa = [(64 + 58 * math.cos(math.radians(a)), 64 + 58 * math.sin(math.radians(a))) for a in range(30, 390, 60)]
d.polygon(hexa, fill=(26, 28, 32, 255), outline=(230, 190, 40, 255))
inner = [(64 + 46 * math.cos(math.radians(a)), 64 + 46 * math.sin(math.radians(a))) for a in range(30, 390, 60)]
d.polygon(inner, outline=(230, 190, 40, 255))
for a in range(0, 360, 30):
    x1 = 64 + 50 * math.cos(math.radians(a)); y1 = 64 + 50 * math.sin(math.radians(a))
    x2 = 64 + 56 * math.cos(math.radians(a + 12)); y2 = 64 + 56 * math.sin(math.radians(a + 12))
    d.line([(x1, y1), (x2, y2)], fill=(230, 190, 40, 255), width=3)
d.ellipse([28, 46, 100, 82], fill=(236, 234, 226, 255))
d.ellipse([48, 46, 80, 82], fill=(170, 26, 30, 255))
d.ellipse([58, 56, 70, 72], fill=(12, 12, 12, 255))
d.rectangle([24, 62, 104, 66], fill=(26, 28, 32, 255))  # blink line through the eye
d.ellipse([61, 57, 64, 60], fill=(255, 255, 255, 255))
L.save(os.path.join(ROOT, "src/main/resources/breachprotocol.png"))
print("items/blocks/icons/logo generated")

# ======================================================================= POWER GRID ART
def noise_fill(base, n=8, size=16):
    im = new(size, size); px = im.load()
    for x in range(size):
        for y in range(size):
            k = rng.randint(-n, n)
            px[x, y] = tuple(max(0, min(255, c + k)) for c in base) + (255,)
    return im

def frame(im, col=(52, 56, 62)):
    d = ImageDraw.Draw(im); d.rectangle([0, 0, 15, 15], outline=col + (255,))
    for (x, y) in ((1, 1), (14, 1), (1, 14), (14, 14)): d.point([(x, y)], fill=(150, 156, 164, 255))
    return im

# coal generator
side = frame(metal((96, 100, 108))); save(side, "block", "coal_generator_side")
top = frame(metal((86, 90, 98))); d = ImageDraw.Draw(top)
for x in (4, 7, 10): d.rectangle([x, 4, x + 1, 11], fill=(30, 32, 36, 255))
save(top, "block", "coal_generator_top")
for lit in (False, True):
    im = frame(metal((96, 100, 108))); d = ImageDraw.Draw(im)
    d.rectangle([3, 6, 12, 13], fill=(26, 26, 28, 255))
    if lit:
        for x in range(4, 12):
            for y in range(9, 13):
                im.putpixel((x, y), (255, rng.randint(90, 190), 20, 255))
    d.line([(3, 8), (12, 8)], fill=(70, 70, 76, 255))
    d.rectangle([5, 2, 10, 4], fill=(30, 50, 40, 255))
    d.line([(6, 3), (6 + (3 if lit else 0), 3)], fill=(90, 255, 120, 255) if lit else (40, 90, 60, 255))
    save(im, "block", "coal_generator_front" + ("_on" if lit else ""))

# wires
w = noise_fill((214, 118, 50), 10); d = ImageDraw.Draw(w)
for y in range(0, 16, 4): d.line([(0, y), (15, y)], fill=(176, 92, 36, 255))
save(w, "block", "copper_wire")
w = noise_fill((58, 60, 66), 6); d = ImageDraw.Draw(w)
for x in range(0, 16, 4): d.polygon([(x, 0), (x + 2, 0), (x + 4, 15), (x + 2, 15)], fill=(220, 180, 40, 255))
save(w, "block", "heavy_wire")

# battery: output face + 5 charge-gauge sides
out = frame(metal((70, 74, 82))); d = ImageDraw.Draw(out)
d.rectangle([4, 4, 11, 11], fill=(24, 26, 30, 255)); d.rectangle([6, 6, 9, 9], fill=(230, 60, 60, 255))
d.line([(7, 5), (8, 5)], fill=(255, 255, 255, 255)); d.line([(7, 10), (8, 10)], fill=(255, 255, 255, 255))
save(out, "block", "battery_output")
for c in range(5):
    im = frame(metal((70, 74, 82))); d = ImageDraw.Draw(im)
    d.rectangle([5, 2, 10, 13], fill=(20, 22, 26, 255))
    for i in range(4):
        on = i < c
        y0 = 11 - i * 3
        d.rectangle([6, y0, 9, y0 + 1], fill=((80, 230, 120) if c > 1 else (240, 180, 40)) + (255,) if on else (40, 44, 50, 255))
    save(im, "block", f"battery_side_{c}")
save(frame(metal((64, 68, 76))), "block", "battery_top")

# outlet
for on in (False, True):
    im = frame(metal((180, 182, 176), 5), (120, 120, 116)); d = ImageDraw.Draw(im)
    d.rectangle([4, 3, 11, 12], fill=(232, 232, 226, 255), outline=(150, 150, 146, 255))
    d.rectangle([6, 5, 6, 7], fill=(40, 40, 40, 255)); d.rectangle([9, 5, 9, 7], fill=(40, 40, 40, 255))
    d.rectangle([7, 9, 8, 10], fill=(40, 40, 40, 255))
    d.point([(11, 13)], fill=(60, 255, 100, 255) if on else (120, 30, 30, 255))
    save(im, "block", "power_outlet" + ("_on" if on else ""))

# reactor: 4 heat stages
glow = [(30, 70, 40), (60, 220, 90), (240, 200, 40), (255, 70, 30)]
for h in range(4):
    im = frame(metal((62, 72, 66))); d = ImageDraw.Draw(im)
    d.rectangle([3, 3, 12, 12], fill=(18, 22, 20, 255))
    for x in (5, 8, 11):
        d.line([(x - 1, 4), (x - 1, 11)], fill=glow[h] + (255,))
    for i in range(0, 16, 4):
        d.polygon([(i, 14), (i + 2, 14), (i + 3, 15), (i + 1, 15)], fill=(230, 190, 40, 255))
    save(im, "block", f"fission_reactor_side_{h}")
    top = frame(metal((56, 64, 60))); d = ImageDraw.Draw(top)
    d.ellipse([3, 3, 12, 12], fill=(20, 24, 22, 255)); d.ellipse([5, 5, 10, 10], fill=glow[h] + (255,))
    d.line([(7, 3), (8, 12)], fill=(70, 80, 74, 255))
    save(top, "block", f"fission_reactor_top_{h}")

# coolant cell
im = noise_fill((150, 205, 235), 8); d = ImageDraw.Draw(im)
d.rectangle([0, 0, 15, 15], outline=(80, 120, 150, 255)); d.rectangle([3, 3, 12, 12], outline=(220, 245, 255, 255))
d.line([(4, 8), (11, 8)], fill=(255, 255, 255, 255)); d.line([(8, 4), (8, 11)], fill=(255, 255, 255, 255))
save(im, "block", "coolant_cell")

# ores + slag
def ore(base, name):
    im = noise_fill(base, 9); px = im.load()
    for _ in range(7):
        cx, cy = rng.randint(2, 13), rng.randint(2, 13)
        for (dx, dy) in ((0, 0), (1, 0), (0, 1)):
            px[cx + dx, cy + dy] = (90 + rng.randint(0, 40), 230, 70, 255)
    save(im, "block", name)
ore((126, 126, 126), "uranium_ore"); ore((76, 76, 82), "deepslate_uranium_ore")
im = noise_fill((50, 62, 44), 12); px = im.load()
for _ in range(20): px[rng.randint(0, 15), rng.randint(0, 15)] = (150, 255, 90, 255)
save(im, "block", "reactor_slag")

# items
im = new(); d = ImageDraw.Draw(im)
d.polygon([(4, 6), (9, 3), (13, 7), (11, 12), (5, 12)], fill=(78, 120, 70, 255))
for p in ((7, 6), (10, 8), (6, 10), (9, 10)): d.point([p], fill=(150, 255, 90, 255))
save(im, "item", "raw_uranium")
im = new(); d = ImageDraw.Draw(im)
d.polygon([(2, 10), (6, 6), (14, 6), (10, 10)], fill=(130, 230, 100, 255))
d.polygon([(2, 10), (10, 10), (10, 12), (2, 12)], fill=(70, 160, 60, 255))
d.polygon([(10, 10), (14, 6), (14, 8), (10, 12)], fill=(50, 120, 44, 255))
d.line([(6, 7), (12, 7)], fill=(210, 255, 190, 255))
save(im, "item", "uranium_ingot")
for spent in (False, True):
    im = new(); d = ImageDraw.Draw(im)
    for i in range(10):
        x, y = 3 + i, 12 - i
        d.rectangle([x - 1, y - 1, x + 1, y + 1], fill=(120, 124, 130, 255))
        d.point([(x, y)], fill=(70, 70, 72, 255) if spent else (120, 255, 90, 255))
    d.rectangle([1, 12, 4, 15], fill=(180, 150, 40, 255)); d.rectangle([12, 1, 15, 4], fill=(180, 150, 40, 255))
    save(im, "item", "depleted_fuel_rod" if spent else "fuel_rod")
im = new(); d = ImageDraw.Draw(im)
d.rectangle([4, 2, 11, 13], fill=(230, 180, 40, 255)); d.rectangle([5, 3, 10, 7], fill=(30, 50, 40, 255))
d.line([(6, 5), (9, 5)], fill=(90, 255, 120, 255)); d.ellipse([6, 8, 9, 11], fill=(40, 40, 44, 255))
d.line([(2, 14), (5, 12)], fill=(200, 30, 30, 255)); d.line([(13, 14), (10, 12)], fill=(30, 30, 30, 255))
save(im, "item", "multimeter")
icon(lambda d: (d.ellipse([3, 3, 14, 14], fill=(240, 220, 40, 255)), d.ellipse([7, 7, 10, 10], fill=(20, 20, 20, 255)),
                d.pieslice([3, 3, 14, 14], 240, 300, fill=(20, 20, 20, 255)), d.pieslice([3, 3, 14, 14], 0, 60, fill=(20, 20, 20, 255)),
                d.pieslice([3, 3, 14, 14], 120, 180, fill=(20, 20, 20, 255))), "radiation")
print("power art generated")
