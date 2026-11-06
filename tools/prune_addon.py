"""Breach Protocol is an addon for SCP Unleashed: removes resources for content that mod already provides.
Run after the generators (gen_items / gen_data / gen_sounds / gen_geo)."""
import json, os, glob
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src/main/resources")
A = os.path.join(RES, "assets/breachprotocol"); D = os.path.join(RES, "data/breachprotocol")
KEEP_BLOCKS = {"containment_generator", "containment_wall", "ascension_altar"}
KEEP_ITEMS = {"phone", "site_terminal", "photograph", "gate_guardian_spawn_egg", "o5_member_spawn_egg"} | KEEP_BLOCKS
KEEP_ENTITIES = {"gate_guardian", "o5_member"}
KEEP_SOUNDS = {"breach.alarm", "field.hum", "phone.ring", "phone.message", "ascension", "guardian.voice", "camera.shutter"}

def stem(p): return os.path.basename(p).split(".")[0]
def rm(p):
    if os.path.isfile(p): os.remove(p)

for p in glob.glob(f"{A}/blockstates/*.json"):
    if stem(p) not in KEEP_BLOCKS: rm(p)
for p in glob.glob(f"{A}/models/block/*.json"):
    if not any(stem(p).startswith(b) for b in KEEP_BLOCKS): rm(p)
for p in glob.glob(f"{A}/models/item/*.json"):
    if stem(p) not in KEEP_ITEMS: rm(p)
for p in glob.glob(f"{A}/textures/block/*.png"):
    if not any(stem(p).startswith(b) for b in KEEP_BLOCKS): rm(p)
for p in glob.glob(f"{A}/textures/item/*.png"):
    if stem(p) not in KEEP_ITEMS: rm(p)
for p in glob.glob(f"{A}/textures/entity/*.png"):
    if not any(stem(p).startswith(e) for e in KEEP_ENTITIES): rm(p)
for p in glob.glob(f"{A}/textures/mob_effect/*.png"):
    if stem(p) != "contained": rm(p)
for p in glob.glob(f"{A}/geo/entity/*") + glob.glob(f"{A}/animations/entity/*"):
    if stem(p) not in KEEP_ENTITIES: rm(p)
for d in ("scp173", "scp096", "scp049", "scp106", "scp999", "keycard"):
    for p in glob.glob(f"{A}/sounds/{d}/*"): rm(p)
    if os.path.isdir(f"{A}/sounds/{d}"): os.rmdir(f"{A}/sounds/{d}")
sj = json.load(open(f"{A}/sounds.json"))
json.dump({k: v for k, v in sj.items() if k in KEEP_SOUNDS}, open(f"{A}/sounds.json", "w"), indent=2)
for p in glob.glob(f"{D}/loot_table/blocks/*.json"):
    if stem(p) not in KEEP_BLOCKS: rm(p)
for p in glob.glob(f"{D}/recipe/*.json"):
    if stem(p) not in {"containment_generator", "containment_wall", "phone", "site_terminal"}: rm(p)
for p in glob.glob(f"{D}/worldgen/**/*.json", recursive=True) + glob.glob(f"{D}/neoforge/**/*.json", recursive=True): rm(p)
for p in glob.glob(f"{RES}/data/c/tags/**/*.json", recursive=True): rm(p)
pick = os.path.join(RES, "data/minecraft/tags/block/mineable/pickaxe.json")
json.dump({"replace": False, "values": ["breachprotocol:containment_generator", "breachprotocol:containment_wall"]}, open(pick, "w"), indent=2)
json.dump({"replace": False, "values": ["breachprotocol:containment_generator", "breachprotocol:containment_wall"]},
          open(os.path.join(RES, "data/minecraft/tags/block/needs_iron_tool.json"), "w"), indent=2)
# remove now-empty dirs
for dp, dn, fn in sorted(os.walk(RES), key=lambda x: -len(x[0])):
    if not dn and not fn: os.rmdir(dp)
lang = json.load(open(f"{A}/lang/en_us.json"))
dead = ("entity.breachprotocol.scp_", "item.breachprotocol.keycard", "tooltip.breachprotocol.keycard", "item.breachprotocol.scp_",
        "item.breachprotocol.eyedrops", "block.breachprotocol.keycard", "block.breachprotocol.corrosion", "effect.breachprotocol.cola",
        "effect.breachprotocol.corrosion", "effect.breachprotocol.clear", "effect.breachprotocol.radiation", "key.", "hud.",
        "message.breachprotocol.access", "message.breachprotocol.reader", "message.breachprotocol.scp207", "message.breachprotocol.scp096",
        "subtitles.breachprotocol.scp", "subtitles.breachprotocol.keycard", "message.breachprotocol.terminal.173", "message.breachprotocol.terminal.096",
        "message.breachprotocol.terminal.106")
power = ("coal_generator", "copper_wire", "heavy_wire", "battery", "power_outlet", "fission_reactor", "coolant_cell", "uranium", "reactor_slag",
         "fuel_rod", "multimeter", "goggles", "paper_bag", "chocolate", "scp999_gel", "scp173_residue")
lang = {k: v for k, v in lang.items() if not k.startswith(dead) and not any(p in k for p in power)}
lang["itemGroup.breachprotocol"] = "Breach Protocol (SCP Unleashed addon)"
json.dump(lang, open(f"{A}/lang/en_us.json", "w"), indent=2, ensure_ascii=False)
print("pruned")
