"""Original phone UI art: app icons and wallpaper."""
import math, os, random
from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src/main/resources/assets/breachprotocol/textures/gui/phone")
os.makedirs(OUT, exist_ok=True)
S = 4  # supersample for smooth shapes

def icon(name, top, bottom, draw_glyph):
    big = Image.new("RGBA", (32 * S, 32 * S), (0, 0, 0, 0))
    grad = Image.new("RGBA", big.size)
    gp = grad.load()
    for y in range(big.height):
        k = y / (big.height - 1)
        c = tuple(int(top[i] * (1 - k) + bottom[i] * k) for i in range(3))
        for x in range(big.width):
            gp[x, y] = c + (255,)
    mask = Image.new("L", big.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, big.width - 1, big.height - 1], radius=8 * S, fill=255)
    big.paste(grad, (0, 0), mask)
    shine = Image.new("RGBA", big.size, (0, 0, 0, 0))
    ImageDraw.Draw(shine).rounded_rectangle([0, 0, big.width - 1, big.height // 2], radius=8 * S, fill=(255, 255, 255, 34))
    shine.putalpha(Image.composite(shine.getchannel("A"), Image.new("L", big.size, 0), mask))
    big = Image.alpha_composite(big, shine)
    d = ImageDraw.Draw(big)
    draw_glyph(d, S)
    small = big.resize((32, 32), Image.LANCZOS)
    px = small.load()
    for y in range(32):
        for x in range(32):
            r, g, b, a = px[x, y]
            if a < 48:
                px[x, y] = (0, 0, 0, 0)  # stray near-transparent edge pixels show up as coloured dots in-game
    small.save(os.path.join(OUT, name + ".png"))

W = (255, 255, 255, 255)
def messages(d, s):
    d.rounded_rectangle([6 * s, 7 * s, 26 * s, 21 * s], radius=6 * s, fill=W)
    d.polygon([(10 * s, 20 * s), (9 * s, 26 * s), (15 * s, 21 * s)], fill=W)
def missions(d, s):
    d.rounded_rectangle([8 * s, 6 * s, 24 * s, 27 * s], radius=2 * s, fill=W)
    d.rectangle([12 * s, 4 * s, 20 * s, 8 * s], fill=(90, 60, 20, 255))
    for i, y in enumerate((12, 17, 22)):
        d.line([(11 * s, y * s), (13 * s, (y + 2) * s), (16 * s, (y - 2) * s)], fill=(230, 120, 30, 255), width=2 * s)
        d.line([(18 * s, y * s), (22 * s, y * s)], fill=(160, 160, 160, 255), width=s)
def phone(d, s):
    d.arc([5 * s, 5 * s, 27 * s, 27 * s], 100, 170, fill=W, width=0)
    pts = [(8, 9), (12, 7), (15, 12), (13, 14), (18, 19), (20, 17), (25, 20), (23, 24), (19, 25), (8, 14)]
    d.polygon([(x * s, y * s) for x, y in pts], fill=W)
def command(d, s):
    cx, cy, r1, r2 = 16 * s, 17 * s, 10 * s, 4 * s
    pts = []
    for i in range(10):
        a = -math.pi / 2 + i * math.pi / 5
        r = r1 if i % 2 == 0 else r2
        pts.append((cx + math.cos(a) * r, cy + math.sin(a) * r))
    d.polygon(pts, fill=W)
def personnel(d, s):
    d.rounded_rectangle([5 * s, 8 * s, 27 * s, 24 * s], radius=3 * s, fill=W)
    d.ellipse([8 * s, 11 * s, 14 * s, 17 * s], fill=(60, 110, 200, 255))
    d.rectangle([8 * s, 18 * s, 14 * s, 21 * s], fill=(60, 110, 200, 255))
    for y in (12, 16, 20):
        d.line([(17 * s, y * s), (24 * s, y * s)], fill=(150, 160, 175, 255), width=s)
def containment(d, s):
    pts = [(16 * s + math.cos(math.radians(a)) * 11 * s, 16 * s + math.sin(math.radians(a)) * 11 * s) for a in range(30, 390, 60)]
    d.polygon(pts, outline=W, width=2 * s)
    d.ellipse([12 * s, 12 * s, 20 * s, 20 * s], fill=W)
def site01(d, s):
    d.rectangle([9 * s, 8 * s, 23 * s, 27 * s], fill=W)
    d.rectangle([12 * s, 12 * s, 20 * s, 27 * s], fill=(80, 30, 120, 255))
    d.ellipse([14 * s, 16 * s, 18 * s, 20 * s], fill=(255, 210, 90, 255))
def settings_dummy(d, s):
    pass

icon("app_messages", (90, 220, 110), (30, 150, 60), messages)
icon("app_missions", (255, 180, 60), (220, 110, 20), missions)
icon("app_phone", (100, 230, 120), (20, 160, 70), phone)
icon("app_command", (240, 80, 80), (150, 20, 30), command)
icon("app_personnel", (110, 160, 250), (40, 80, 190), personnel)
icon("app_containment", (90, 220, 240), (20, 120, 170), containment)
icon("app_site01", (190, 110, 255), (90, 30, 160), site01)

# wallpaper 156 x 300
rng = random.Random(5)
w, h = 156, 300
wp = Image.new("RGBA", (w, h))
p = wp.load()
for y in range(h):
    for x in range(w):
        k = y / h
        r = int(10 + 22 * k); g = int(18 + 10 * k); b = int(34 + 30 * (1 - abs(0.5 - x / w)))
        p[x, y] = (r, g, b, 255)
grid = Image.new("RGBA", (w, h), (0, 0, 0, 0))
d = ImageDraw.Draw(grid)
for row in range(-1, 40):
    for col in range(-1, 12):
        cx = col * 16 + (8 if row % 2 else 0); cy = row * 14
        pts = [(cx + math.cos(math.radians(a)) * 8, cy + math.sin(math.radians(a)) * 8) for a in range(30, 390, 60)]
        d.polygon(pts, outline=(255, 255, 255, 14))
wp = Image.alpha_composite(wp, grid)
glow = Image.new("RGBA", (w, h), (0, 0, 0, 0))
ImageDraw.Draw(glow).ellipse([18, 160, 138, 270], fill=(150, 40, 50, 46))
wp = Image.alpha_composite(wp, glow.filter(ImageFilter.GaussianBlur(18)))
# Original Foundation emblem: an octagonal containment-vault door with a locking wheel
E = 8
cx, cy, R = 78 * E, 214 * E, 34 * E
emb = Image.new("RGBA", (w * E, h * E), (0, 0, 0, 0))
d = ImageDraw.Draw(emb)
col = (226, 230, 236, 150)
dim = (226, 230, 236, 70)
octa = lambda r: [(cx + r * math.cos(math.radians(22.5 + k * 45)), cy + r * math.sin(math.radians(22.5 + k * 45))) for k in range(8)]
d.polygon(octa(R), outline=col, width=5 * E)
d.polygon(octa(R - 9 * E), outline=dim, width=2 * E)
for k in range(8):  # bolts
    a = math.radians(k * 45)
    bx, by = cx + (R - 4.5 * E) * math.cos(a), cy + (R - 4.5 * E) * math.sin(a)
    d.ellipse([bx - 2 * E, by - 2 * E, bx + 2 * E, by + 2 * E], fill=col)
rw = 13 * E  # locking wheel: rim, four spokes, hub
d.ellipse([cx - rw, cy - rw, cx + rw, cy + rw], outline=col, width=3 * E)
for k in range(4):
    a = math.radians(45 + k * 90)
    d.line([(cx, cy), (cx + (rw + 5 * E) * math.cos(a), cy + (rw + 5 * E) * math.sin(a))], fill=col, width=3 * E)
d.ellipse([cx - 4 * E, cy - 4 * E, cx + 4 * E, cy + 4 * E], fill=(170, 30, 36, 190))
emb = emb.resize((w, h), Image.LANCZOS)
wp = Image.alpha_composite(wp, emb)
wp.save(os.path.join(OUT, "wallpaper.png"))
print("phone art generated")

# ---- apps added for the SCP Unleashed addon
def camera(d, s):
    d.rounded_rectangle([5 * s, 10 * s, 27 * s, 25 * s], radius=3 * s, fill=W)
    d.rectangle([11 * s, 7 * s, 18 * s, 11 * s], fill=W)
    d.ellipse([11 * s, 12 * s, 21 * s, 22 * s], fill=(40, 46, 56, 255))
    d.ellipse([14 * s, 15 * s, 18 * s, 19 * s], fill=(120, 200, 255, 255))
def gallery(d, s):
    d.rounded_rectangle([6 * s, 7 * s, 26 * s, 25 * s], radius=3 * s, fill=W)
    d.polygon([(8 * s, 23 * s), (14 * s, 15 * s), (18 * s, 20 * s), (21 * s, 17 * s), (24 * s, 23 * s)], fill=(110, 60, 170, 255))
    d.ellipse([18 * s, 10 * s, 22 * s, 14 * s], fill=(240, 180, 60, 255))
def archive(d, s):
    d.rectangle([7 * s, 6 * s, 25 * s, 26 * s], fill=W)
    for y in (10, 14, 18):
        d.rectangle([10 * s, y * s, 22 * s, (y + 2) * s], fill=(20, 20, 24, 255) if y == 14 else (150, 150, 150, 255))
    d.rectangle([10 * s, 21 * s, 17 * s, 23 * s], fill=(150, 150, 150, 255))
def settings(d, s):
    cx, cy = 16 * s, 16 * s
    for i in range(8):
        a = i * math.pi / 4
        d.line([(cx, cy), (cx + math.cos(a) * 11 * s, cy + math.sin(a) * 11 * s)], fill=W, width=4 * s)
    d.ellipse([cx - 8 * s, cy - 8 * s, cx + 8 * s, cy + 8 * s], fill=W)
    d.ellipse([cx - 4 * s, cy - 4 * s, cx + 4 * s, cy + 4 * s], fill=(70, 78, 90, 255))
def network(d, s):
    d.ellipse([7 * s, 7 * s, 25 * s, 25 * s], outline=W, width=2 * s)
    d.line([(16 * s, 7 * s), (16 * s, 25 * s)], fill=W, width=2 * s)
    d.arc([10 * s, 7 * s, 22 * s, 25 * s], 0, 360, fill=W, width=2 * s)
    d.line([(7 * s, 16 * s), (25 * s, 16 * s)], fill=W, width=2 * s)
icon("app_camera", (70, 78, 92), (30, 34, 42), camera)
icon("app_gallery", (170, 110, 240), (90, 40, 150), gallery)
icon("app_archive", (220, 175, 70), (140, 100, 20), archive)
icon("app_settings", (130, 140, 155), (60, 66, 78), settings)
icon("app_network", (70, 200, 180), (20, 110, 110), network)
print("addon app icons generated")
