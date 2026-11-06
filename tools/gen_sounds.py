"""Synthesizes every Breach Protocol sound from scratch (numpy -> wav -> ogg vorbis, mono)."""
import os, subprocess, tempfile, wave
import numpy as np

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src/main/resources/assets/breachprotocol/sounds")
SR = 44100
rng = np.random.default_rng(106)

def t(sec): return np.arange(int(SR * sec)) / SR
def env(n, a=0.01, r=0.1):
    e = np.ones(n); ai = int(SR * a); ri = int(SR * r)
    if ai: e[:ai] = np.linspace(0, 1, ai)
    if ri: e[-ri:] *= np.linspace(1, 0, ri)
    return e
def lowpass(x, k):
    k = max(1, int(k)); return np.convolve(x, np.ones(k) / k, mode="same")
def norm(x, peak=0.9): return x / (np.max(np.abs(x)) + 1e-9) * peak

def write(name, x):
    path = os.path.join(OUT, name + ".ogg"); os.makedirs(os.path.dirname(path), exist_ok=True)
    pcm = (np.clip(x, -1, 1) * 32767).astype(np.int16)
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as f: tmp = f.name
    with wave.open(tmp, "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR); w.writeframes(pcm.tobytes())
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", tmp, "-c:a", "libvorbis", "-q:a", "4", path], check=True)
    os.remove(tmp)

# SCP-173: concrete grinding on concrete
x = t(0.7); n = rng.standard_normal(len(x))
grind = lowpass(n, 18) * (0.6 + 0.4 * np.sin(2 * np.pi * 23 * x) ** 2) + 0.2 * lowpass(n, 4)
write("scp173/scrape1", norm(grind * env(len(x), 0.05, 0.25)))
x = t(0.5); n = rng.standard_normal(len(x))
write("scp173/scrape2", norm(lowpass(n, 26) * (0.5 + 0.5 * np.abs(np.sin(2 * np.pi * 9 * x))) * env(len(x), 0.03, 0.2)))

# SCP-173: the snap
x = t(0.45); n = rng.standard_normal(len(x))
crack = n * np.exp(-x * 60) + 0.8 * np.sin(2 * np.pi * 70 * x) * np.exp(-x * 14) + 0.4 * lowpass(n, 6) * np.exp(-x * 25)
write("scp173/snap", norm(crack, 0.95))

# SCP-096: sobbing (breathy formant pulses)
x = t(3.2); n = rng.standard_normal(len(x))
breath = lowpass(n, 5) - lowpass(n, 40)
pulses = np.clip(np.sin(2 * np.pi * 1.6 * x + 0.6 * np.sin(2 * np.pi * 0.4 * x)), 0, 1) ** 3
voice = np.sin(2 * np.pi * (290 + 40 * np.sin(2 * np.pi * 1.6 * x)) * x) * 0.25
write("scp096/cry", norm((breath * 0.8 + voice) * pulses * env(len(x), 0.3, 0.6), 0.7))

# SCP-096: scream (rising, harsh, distorted)
x = t(2.6); n = rng.standard_normal(len(x))
f = 180 + 900 * (1 - np.exp(-x * 2.5))
ph = 2 * np.pi * np.cumsum(f) / SR
scream = sum(np.sin(ph * k) / k for k in range(1, 7)) + 0.7 * (lowpass(n, 3) - lowpass(n, 20))
scream = np.tanh(scream * 3) * env(len(x), 0.08, 0.5)
write("scp096/scream", norm(scream, 0.95))

# SCP-049: low, calm, unsettling hum with a bell-like overtone
x = t(2.2)
hum = np.sin(2 * np.pi * 98 * x) + 0.5 * np.sin(2 * np.pi * 147 * x) + 0.25 * np.sin(2 * np.pi * 392 * x) * np.exp(-x * 2)
write("scp049/ambient", norm(hum * env(len(x), 0.4, 0.8) * (0.8 + 0.2 * np.sin(2 * np.pi * 3 * x)), 0.6))

# SCP-106: wet emergence and a low chuckle
x = t(1.4); n = rng.standard_normal(len(x))
squelch = lowpass(n, 30) * (0.5 + 0.5 * np.sin(2 * np.pi * (6 + 12 * x) * x)) * env(len(x), 0.2, 0.5)
write("scp106/emerge", norm(squelch + 0.3 * np.sin(2 * np.pi * 45 * x) * env(len(x), 0.4, 0.6)))
x = t(1.8)
f = 110 - 30 * x
ph = 2 * np.pi * np.cumsum(f) / SR
chuckle = np.tanh(2.5 * (np.sin(ph) + 0.6 * np.sin(2 * ph) + 0.3 * np.sin(3 * ph)))
gate = (np.sin(2 * np.pi * 4.2 * x) > 0.2).astype(float); gate = lowpass(gate, 600)
write("scp106/laugh", norm(chuckle * gate * env(len(x), 0.05, 0.4), 0.8))

# SCP-999: happy little chirps
parts = []
for f0 in (700, 950, 820, 1200):
    x = t(0.12); f = f0 + 500 * x / 0.12
    parts.append(np.sin(2 * np.pi * np.cumsum(f) / SR) * env(len(x), 0.01, 0.05)); parts.append(np.zeros(int(SR * 0.04)))
write("scp999/giggle", norm(np.concatenate(parts), 0.7))

# Breach alarm: two-tone siren with klaxon grit
x = t(2.4)
f = np.where((x * 1.25) % 1 < 0.5, 620, 465)
ph = 2 * np.pi * np.cumsum(f) / SR
siren = np.sign(np.sin(ph)) * 0.4 + np.sin(ph) * 0.6
write("breach/alarm", norm(lowpass(siren, 3) * env(len(x), 0.02, 0.15), 0.85))

# Keycard reader
x = t(0.09); a = np.sin(2 * np.pi * 1320 * x) * env(len(x), 0.002, 0.02)
x2 = t(0.13); b = np.sin(2 * np.pi * 1760 * x2) * env(len(x2), 0.002, 0.05)
write("keycard/accept", norm(np.concatenate([a, np.zeros(1500), b]), 0.6))
x = t(0.35)
write("keycard/deny", norm(np.sign(np.sin(2 * np.pi * 150 * x)) * env(len(x), 0.005, 0.05), 0.45))

# Containment field hum (loop-friendly)
x = t(2.0)
hum = np.sin(2 * np.pi * 60 * x) + 0.4 * np.sin(2 * np.pi * 120 * x) + 0.1 * np.sin(2 * np.pi * 180 * x + np.sin(2 * np.pi * 0.5 * x))
write("field/hum", norm(hum, 0.35))
# Phone: ringtone (two short trills, twice) and message chime
ring = []
for _ in range(2):
    for _ in range(2):
        x = t(0.38)
        tone = (np.sin(2 * np.pi * 1320 * x) + np.sin(2 * np.pi * 1660 * x)) * (np.sin(2 * np.pi * 20 * x) > 0)
        ring.append(tone * env(len(x), 0.005, 0.03)); ring.append(np.zeros(int(SR * 0.08)))
    ring.append(np.zeros(int(SR * 0.5)))
write("phone/ring", norm(np.concatenate(ring), 0.6))
x = t(0.12); a = np.sin(2 * np.pi * 988 * x) * env(len(x), 0.003, 0.08)
x2 = t(0.22); b = np.sin(2 * np.pi * 1480 * x2) * env(len(x2), 0.003, 0.18)
write("phone/message", norm(np.concatenate([a, b]), 0.5))

# Ascension: rising choir of fifths with shimmer
x = t(9.0)
f0 = 110 * 2 ** (x / 9.0 * 2)
ph = 2 * np.pi * np.cumsum(f0) / SR
choir = sum(np.sin(ph * k * r) / (k + 1) for k in (1, 2, 3) for r in (1.0, 1.5, 2.0))
shimmer = np.sin(2 * np.pi * 3520 * x) * (0.5 + 0.5 * np.sin(2 * np.pi * 7 * x)) * (x / 9.0) * 0.3
write("ascension", norm((choir + shimmer) * env(len(x), 2.0, 2.0), 0.85))

# Gate Guardian: an enormous low voice-like drone with a fiery crackle
x = t(2.5); n = rng.standard_normal(len(x))
voice = np.tanh(1.8 * (np.sin(2 * np.pi * 55 * x) + 0.7 * np.sin(2 * np.pi * 82.5 * x) + 0.4 * np.sin(2 * np.pi * 110 * x)))
crackle = (n * (rng.random(len(x)) > 0.995)) * 2 + lowpass(n, 40) * 0.3
write("guardian/voice", norm((voice + crackle) * env(len(x), 0.3, 0.9), 0.9))
print("sounds generated")
