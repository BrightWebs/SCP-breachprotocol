"""Original art for the Administrator's Terminal: casing, screen, keyboard (32x32 each)."""
import os, random
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src/main/resources/assets/breachprotocol/textures/block")
os.makedirs(OUT, exist_ok=True)
random.seed(7)

# casing: graphite with brushed noise, a seam and screws
img = Image.new("RGBA", (32, 32))
px = img.load()
for y in range(32):
    for x in range(32):
        v = 46 + random.randint(-4, 4) + (2 if (y % 5 == 0) else 0)
        px[x, y] = (v, v + 2, v + 6, 255)
d = ImageDraw.Draw(img)
d.rectangle([0, 0, 31, 31], outline=(30, 32, 38, 255))
d.line([(0, 16), (31, 16)], fill=(36, 38, 44, 255))
for sx, sy in ((3, 3), (28, 3), (3, 28), (28, 28)):
    d.point((sx, sy), fill=(110, 116, 124, 255))
img.save(os.path.join(OUT, "admin_terminal_case.png"))

# bezel strip around the screen, with the power light
img = Image.new("RGBA", (32, 32), (26, 28, 34, 255))
d = ImageDraw.Draw(img)
d.rectangle([0, 0, 31, 31], outline=(60, 64, 72, 255))
d.rectangle([2, 2, 29, 29], outline=(18, 19, 23, 255))
d.rectangle([26, 28, 28, 29], fill=(90, 255, 140, 255))
img.save(os.path.join(OUT, "admin_terminal_bezel.png"))

# the screen: a phosphor desktop (title bar, app grid, a status line), drawn to read at block size
img = Image.new("RGBA", (32, 32), (4, 14, 8, 255))
d = ImageDraw.Draw(img)
for y in range(0, 32, 2):
    d.line([(0, y), (31, y)], fill=(6, 20, 11, 255))
d.rectangle([0, 0, 31, 3], fill=(18, 80, 42, 255))
d.rectangle([1, 1, 9, 2], fill=(150, 255, 190, 255))
d.point((29, 1), fill=(255, 110, 110, 255))
# app tiles
cols = [(92, 255, 138), (63, 208, 255), (255, 179, 71), (255, 90, 90), (190, 130, 255), (92, 255, 138)]
i = 0
for row in range(2):
    for c in range(3):
        x0, y0 = 3 + c * 9, 7 + row * 9
        d.rectangle([x0, y0, x0 + 6, y0 + 6], outline=(40, 140, 80, 255), fill=(8, 34, 18, 255))
        col = cols[i % len(cols)]
        d.rectangle([x0 + 2, y0 + 2, x0 + 4, y0 + 4], fill=col + (255,))
        i += 1
# task bar
d.rectangle([0, 27, 31, 31], fill=(10, 40, 22, 255))
d.rectangle([1, 28, 5, 30], fill=(92, 255, 138, 255))
d.rectangle([24, 28, 30, 30], fill=(40, 140, 80, 255))
img.save(os.path.join(OUT, "admin_terminal_screen.png"))

# keyboard top: rows of keys
img = Image.new("RGBA", (32, 32), (34, 36, 42, 255))
d = ImageDraw.Draw(img)
for row in range(4):
    for k in range(10):
        x0, y0 = 1 + k * 3, 2 + row * 7
        d.rectangle([x0, y0, x0 + 1, y0 + 4], fill=(70, 74, 82, 255))
d.rectangle([7, 28, 24, 30], fill=(70, 74, 82, 255))
img.save(os.path.join(OUT, "admin_terminal_keys.png"))
print("terminal textures generated")
