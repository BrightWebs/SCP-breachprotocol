"""Writes lang, sounds.json, blockstates, block/item models, loot tables and recipes."""
import json, os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src/main/resources")
AS = os.path.join(RES, "assets/breachprotocol")
DA = os.path.join(RES, "data/breachprotocol")
M = "breachprotocol"

def w(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)

# ------------------------------------------------------------------ sounds.json
sounds = {
    "scp173.scrape": ["scp173/scrape1", "scp173/scrape2"], "scp173.snap": ["scp173/snap"],
    "scp096.cry": ["scp096/cry"], "scp096.scream": ["scp096/scream"],
    "scp049.ambient": ["scp049/ambient"], "scp106.emerge": ["scp106/emerge"], "scp106.laugh": ["scp106/laugh"],
    "scp999.giggle": ["scp999/giggle"], "breach.alarm": ["breach/alarm"],
    "keycard.accept": ["keycard/accept"], "keycard.deny": ["keycard/deny"], "field.hum": ["field/hum"],
    "phone.ring": ["phone/ring"], "phone.message": ["phone/message"], "ascension": ["ascension"], "guardian.voice": ["guardian/voice"],
}
w(f"{AS}/sounds.json", {k: {"subtitle": f"subtitles.{M}.{k}", "sounds": [f"{M}:{s}" for s in v]} for k, v in sounds.items()})

# ------------------------------------------------------------------ lang
lang = {
    "itemGroup.breachprotocol": "SCP: Breach Protocol",
    "entity.breachprotocol.scp_173": "SCP-173",
    "entity.breachprotocol.scp_096": "SCP-096",
    "entity.breachprotocol.scp_049": "SCP-049",
    "entity.breachprotocol.scp_049_2": "SCP-049-2",
    "entity.breachprotocol.scp_049_2.named": "SCP-049-2 (formerly %s)",
    "entity.breachprotocol.scp_106": "SCP-106",
    "entity.breachprotocol.scp_999": "SCP-999",
    "entity.breachprotocol.scp_049.line.0": "I can sense the illness in you. Please, stay where you are.",
    "entity.breachprotocol.scp_049.line.1": "There is no need for fear. I am a physician.",
    "entity.breachprotocol.scp_049.line.2": "The disease spreads with every step you take.",
    "entity.breachprotocol.scp_049.line.3": "Hold still. The treatment is very quick.",
    "entity.breachprotocol.scp_049.line.4": "You do not yet understand how sick you are.",
    "entity.breachprotocol.scp_049.line.5": "Come, let me help you.",
    "entity.breachprotocol.scp_049.cured": "There. Another patient cured.",
    "item.breachprotocol.keycard_1": "Level 1 Keycard",
    "item.breachprotocol.keycard_2": "Level 2 Keycard",
    "item.breachprotocol.keycard_3": "Level 3 Keycard",
    "item.breachprotocol.keycard_4": "Level 4 Keycard",
    "item.breachprotocol.keycard_5": "Level 5 Keycard",
    "tooltip.breachprotocol.keycard.role.1": "Janitorial / D-Class supervision",
    "tooltip.breachprotocol.keycard.role.2": "Junior Researcher",
    "tooltip.breachprotocol.keycard.role.3": "Security Officer",
    "tooltip.breachprotocol.keycard.role.4": "Containment Specialist",
    "tooltip.breachprotocol.keycard.role.5": "Site Director",
    "tooltip.breachprotocol.keycard.use": "Use on a reader. Sneak-use to program it.",
    "item.breachprotocol.scp_500": "SCP-500",
    "item.breachprotocol.scp_500.desc": "One pill cures any ailment.",
    "item.breachprotocol.scp_207": "SCP-207",
    "item.breachprotocol.scp_207.desc": "Speed that stacks with every bottle. So does the damage.",
    "item.breachprotocol.eyedrops": "Eyedrops",
    "item.breachprotocol.eyedrops.desc": "Stops blinking for 30 seconds.",
    "item.breachprotocol.site_terminal": "Site Terminal",
    "item.breachprotocol.site_terminal.desc": "Live containment status within 64 blocks.",
    "item.breachprotocol.scp_173_spawn_egg": "SCP-173 Spawn Egg",
    "item.breachprotocol.scp_096_spawn_egg": "SCP-096 Spawn Egg",
    "item.breachprotocol.scp_049_spawn_egg": "SCP-049 Spawn Egg",
    "item.breachprotocol.scp_049_2_spawn_egg": "SCP-049-2 Spawn Egg",
    "item.breachprotocol.scp_106_spawn_egg": "SCP-106 Spawn Egg",
    "item.breachprotocol.scp_999_spawn_egg": "SCP-999 Spawn Egg",
    "block.breachprotocol.keycard_reader": "Keycard Reader",
    "block.breachprotocol.containment_generator": "Containment Field Generator",
    "block.breachprotocol.containment_wall": "Reinforced Containment Wall",
    "block.breachprotocol.corrosion": "Corrosion",
    "effect.breachprotocol.contained": "Contained",
    "effect.breachprotocol.cola_fever": "SCP-207 Exposure",
    "effect.breachprotocol.corrosion": "Corrosion",
    "effect.breachprotocol.clear_eyes": "Clear Eyes",
    "key.categories.breachprotocol": "SCP: Breach Protocol",
    "key.breachprotocol.close_eyes": "Close Eyes (hold)",
    "hud.breachprotocol.blink": "BLINK",
    "hud.breachprotocol.eyes_closed": "Your eyes are closed.",
    "message.breachprotocol.access_granted": "ACCESS GRANTED",
    "message.breachprotocol.access_denied": "ACCESS DENIED - Level %s clearance required",
    "message.breachprotocol.reader_info": "Keycard Reader - Level %s clearance",
    "message.breachprotocol.reader_programmed": "Reader programmed to Level %s",
    "message.breachprotocol.scp207.warn": "SCP-207 exposure level %s. Your heart is racing.",
    "message.breachprotocol.scp096.seen": "You saw its face.",
    "message.breachprotocol.breach.title": "CONTAINMENT BREACH",
    "message.breachprotocol.breach.subtitle": "%s has breached containment",
    "message.breachprotocol.breach.chat": "%s breached containment near %s, %s, %s. All personnel evacuate.",
    "message.breachprotocol.terminal.header": "=== SITE TERMINAL : CONTAINMENT STATUS ===",
    "message.breachprotocol.terminal.none": " No anomalies detected within 64m.",
    "message.breachprotocol.terminal.contained": "CONTAINED",
    "message.breachprotocol.terminal.loose": "NOT CONTAINED",
    "message.breachprotocol.terminal.warning": "WARNING: %s anomalies outside containment.",
    "message.breachprotocol.terminal.173_observed": "(observed)",
    "message.breachprotocol.terminal.173_unobserved": "(UNOBSERVED)",
    "message.breachprotocol.terminal.096_calm": "(docile)",
    "message.breachprotocol.terminal.096_triggered": "(DISTRESSED)",
    "message.breachprotocol.terminal.096_rage": "(PURSUING %s TARGETS)",
    "message.breachprotocol.terminal.106_phased": "(field destabilised)",
    "entity.breachprotocol.gate_guardian": "SCP-001 \"The Gate Guardian\"",
    "entity.breachprotocol.o5_member": "O5 Council Member",
    "item.breachprotocol.phone": "Foundation Phone",
    "item.breachprotocol.gate_guardian_spawn_egg": "SCP-001 Gate Guardian Spawn Egg",
    "item.breachprotocol.o5_member_spawn_egg": "O5 Council Member Spawn Egg",
    "block.breachprotocol.ascension_altar": "Ascension Altar",
}
subs = {
    "scp173.scrape": "Concrete scrapes", "scp173.snap": "Neck snaps", "scp096.cry": "Something weeps",
    "scp096.scream": "SCP-096 screams", "scp049.ambient": "SCP-049 speaks", "scp106.emerge": "Something seeps through",
    "scp106.laugh": "SCP-106 chuckles", "scp999.giggle": "SCP-999 giggles", "breach.alarm": "Breach alarm",
    "keycard.accept": "Keycard accepted", "keycard.deny": "Keycard denied", "field.hum": "Containment field hums",
    "phone.ring": "Phone rings", "phone.message": "Phone buzzes", "ascension": "Something ascends", "guardian.voice": "The Gate Guardian speaks",
}
for k, v in subs.items(): lang[f"subtitles.{M}.{k}"] = v
w(f"{AS}/lang/en_us.json", lang)

# ------------------------------------------------------------------ blockstates + block models
w(f"{AS}/models/block/containment_generator.json", {"parent": "minecraft:block/cube_bottom_top", "textures": {
    "side": f"{M}:block/containment_generator_side", "top": f"{M}:block/containment_generator_top", "bottom": f"{M}:block/containment_wall"}})
w(f"{AS}/models/block/containment_generator_on.json", {"parent": "minecraft:block/cube_bottom_top", "textures": {
    "side": f"{M}:block/containment_generator_side_on", "top": f"{M}:block/containment_generator_top_on", "bottom": f"{M}:block/containment_wall"}})
w(f"{AS}/blockstates/containment_generator.json", {"variants": {
    "active=false": {"model": f"{M}:block/containment_generator"}, "active=true": {"model": f"{M}:block/containment_generator_on"}}})

w(f"{AS}/models/block/containment_wall.json", {"parent": "minecraft:block/cube_all", "textures": {"all": f"{M}:block/containment_wall"}})
w(f"{AS}/blockstates/containment_wall.json", {"variants": {"": {"model": f"{M}:block/containment_wall"}}})

w(f"{AS}/models/block/corrosion.json", {"parent": "minecraft:block/carpet", "render_type": "minecraft:translucent",
                                         "textures": {"wool": f"{M}:block/corrosion", "particle": f"{M}:block/corrosion"}})
w(f"{AS}/blockstates/corrosion.json", {"variants": {"": {"model": f"{M}:block/corrosion"}}})

def reader_model(front):
    return {"parent": "minecraft:block/block", "textures": {"front": f"{M}:block/{front}", "side": f"{M}:block/keycard_reader_side",
                                                            "particle": f"{M}:block/keycard_reader_side"},
            "elements": [{"from": [4, 3, 14], "to": [12, 13, 16], "faces": {
                "north": {"uv": [0, 0, 16, 16], "texture": "#front"},
                "south": {"uv": [4, 3, 12, 13], "texture": "#side"},
                "east": {"uv": [0, 3, 2, 13], "texture": "#side"}, "west": {"uv": [14, 3, 16, 13], "texture": "#side"},
                "up": {"uv": [4, 14, 12, 16], "texture": "#side"}, "down": {"uv": [4, 0, 12, 2], "texture": "#side"}}}]}
w(f"{AS}/models/block/keycard_reader.json", reader_model("keycard_reader"))
w(f"{AS}/models/block/keycard_reader_on.json", reader_model("keycard_reader_on"))
rot = {"north": 0, "east": 90, "south": 180, "west": 270}
variants = {}
for facing, y in rot.items():
    for powered in ("false", "true"):
        v = {"model": f"{M}:block/keycard_reader" + ("_on" if powered == "true" else "")}
        if y: v["y"] = y
        variants[f"facing={facing},powered={powered}"] = v
w(f"{AS}/blockstates/keycard_reader.json", {"variants": variants})

w(f"{AS}/models/block/ascension_altar.json", {"parent": "minecraft:block/block", "textures": {
    "side": f"{M}:block/ascension_altar_side", "top": f"{M}:block/ascension_altar_top", "particle": f"{M}:block/ascension_altar_side"},
    "elements": [
        {"from": [2, 0, 2], "to": [14, 4, 14], "faces": {f: {"texture": "#top" if f in ("up", "down") else "#side"} for f in ("north", "south", "east", "west", "up", "down")}},
        {"from": [4, 4, 4], "to": [12, 12, 12], "faces": {f: {"texture": "#side"} for f in ("north", "south", "east", "west")}},
        {"from": [1, 12, 1], "to": [15, 16, 15], "faces": {f: {"texture": "#top" if f in ("up", "down") else "#side"} for f in ("north", "south", "east", "west", "up", "down")}}]})
w(f"{AS}/blockstates/ascension_altar.json", {"variants": {"": {"model": f"{M}:block/ascension_altar"}}})
w(f"{AS}/models/item/ascension_altar.json", {"parent": f"{M}:block/ascension_altar"})

# ------------------------------------------------------------------ item models
for i in range(1, 6):
    w(f"{AS}/models/item/keycard_{i}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{M}:item/keycard_{i}"}})
for it in ("scp_500", "scp_207", "eyedrops", "site_terminal", "phone"):
    w(f"{AS}/models/item/{it}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{M}:item/{it}"}})
for b in ("containment_generator", "containment_wall"):
    w(f"{AS}/models/item/{b}.json", {"parent": f"{M}:block/{b}"})
w(f"{AS}/models/item/keycard_reader.json", {"parent": f"{M}:block/keycard_reader", "display": {
    "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.9, 0.9, 0.9]}}})
for e in ("scp_173", "scp_096", "scp_049", "scp_049_2", "scp_106", "scp_999", "gate_guardian", "o5_member"):
    w(f"{AS}/models/item/{e}_spawn_egg.json", {"parent": "minecraft:item/template_spawn_egg"})

# ------------------------------------------------------------------ loot tables
for b in ("keycard_reader", "containment_generator", "containment_wall"):
    w(f"{DA}/loot_table/blocks/{b}.json", {"type": "minecraft:block", "pools": [{"rolls": 1, "bonus_rolls": 0,
        "entries": [{"type": "minecraft:item", "name": f"{M}:{b}"}], "conditions": [{"condition": "minecraft:survives_explosion"}]}]})

# mineable tags so pickaxes work and requiresCorrectToolForDrops is satisfied
w(os.path.join(RES, "data/minecraft/tags/block/mineable/pickaxe.json"), {"replace": False, "values": [
    f"{M}:keycard_reader", f"{M}:containment_generator", f"{M}:containment_wall"]})
w(os.path.join(RES, "data/minecraft/tags/block/needs_iron_tool.json"), {"replace": False, "values": [
    f"{M}:keycard_reader", f"{M}:containment_generator", f"{M}:containment_wall"]})

# ------------------------------------------------------------------ recipes
def shaped(name, pattern, key, result, count=1):
    w(f"{DA}/recipe/{name}.json", {"type": "minecraft:crafting_shaped", "category": "misc", "pattern": pattern,
        "key": {k: {"item": v} for k, v in key.items()}, "result": {"id": result, "count": count}})
def shapeless(name, items, result, count=1):
    w(f"{DA}/recipe/{name}.json", {"type": "minecraft:crafting_shapeless", "category": "misc",
        "ingredients": [{"item": i} for i in items], "result": {"id": result, "count": count}})

shaped("keycard_1", ["PPP", "RIG"], {"P": "minecraft:paper", "R": "minecraft:redstone", "I": "minecraft:iron_nugget", "G": "minecraft:gold_nugget"}, f"{M}:keycard_1")
upgrades = {2: "minecraft:lapis_lazuli", 3: "minecraft:gold_ingot", 4: "minecraft:diamond", 5: "minecraft:netherite_scrap"}
for lvl, mat in upgrades.items():
    shapeless(f"keycard_{lvl}", [f"{M}:keycard_{lvl - 1}", mat, "minecraft:redstone"], f"{M}:keycard_{lvl}")
shaped("keycard_reader", ["III", "IGI", "IRI"], {"I": "minecraft:iron_ingot", "G": "minecraft:glass_pane", "R": "minecraft:redstone"}, f"{M}:keycard_reader")
shaped("containment_wall", ["CIC", "IOI", "CIC"], {"C": "minecraft:smooth_stone", "I": "minecraft:iron_ingot", "O": "minecraft:obsidian"}, f"{M}:containment_wall", 8)
shaped("containment_generator", ["IDI", "ERE", "IBI"], {"I": "minecraft:iron_block", "D": "minecraft:diamond",
    "E": "minecraft:ender_pearl", "R": "minecraft:redstone_block", "B": "minecraft:beacon"}, f"{M}:containment_generator")
shaped("eyedrops", [" S ", "GWG", " G "], {"S": "minecraft:slime_ball", "G": "minecraft:glass_pane", "W": "minecraft:water_bucket"}, f"{M}:eyedrops", 3)
shaped("site_terminal", ["IRI", "IGI", "ICI"], {"I": "minecraft:iron_ingot", "R": "minecraft:redstone", "G": "minecraft:glass_pane", "C": "minecraft:compass"}, f"{M}:site_terminal")
shaped("phone", ["IGI", "IRI", "IEI"], {"I": "minecraft:iron_ingot", "G": "minecraft:glass_pane", "R": "minecraft:redstone", "E": "minecraft:ender_pearl"}, f"{M}:phone")
print("data generated")

# ======================================================================= POWER GRID DATA
def cube_all(name, tex=None):
    w(f"{AS}/models/block/{name}.json", {"parent": "minecraft:block/cube_all", "textures": {"all": f"{M}:block/{tex or name}"}})

# coal generator (orientable, lit variants)
for lit in ("", "_on"):
    w(f"{AS}/models/block/coal_generator{lit}.json", {"parent": "minecraft:block/orientable", "textures": {
        "front": f"{M}:block/coal_generator_front{lit}", "side": f"{M}:block/coal_generator_side", "top": f"{M}:block/coal_generator_top"}})
variants = {}
for facing, y in rot.items():
    for lit in ("false", "true"):
        v = {"model": f"{M}:block/coal_generator" + ("_on" if lit == "true" else "")}
        if y: v["y"] = y
        variants[f"facing={facing},lit={lit}"] = v
w(f"{AS}/blockstates/coal_generator.json", {"variants": variants})

# battery: output face on north, gauge sides by charge, rotate like a piston
for c in range(5):
    w(f"{AS}/models/block/battery_{c}.json", {"parent": "minecraft:block/cube", "textures": {
        "particle": f"{M}:block/battery_top", "north": f"{M}:block/battery_output", "south": f"{M}:block/battery_side_{c}",
        "east": f"{M}:block/battery_side_{c}", "west": f"{M}:block/battery_side_{c}", "up": f"{M}:block/battery_top", "down": f"{M}:block/battery_top"}})
brot = {"north": {}, "east": {"y": 90}, "south": {"y": 180}, "west": {"y": 270}, "up": {"x": 270}, "down": {"x": 90}}
variants = {}
for facing, r in brot.items():
    for c in range(5):
        variants[f"charge={c},facing={facing}"] = dict({"model": f"{M}:block/battery_{c}"}, **r)
w(f"{AS}/blockstates/battery.json", {"variants": variants})

# outlet
cube_all("power_outlet"); cube_all("power_outlet_on")
w(f"{AS}/blockstates/power_outlet.json", {"variants": {"powered=false": {"model": f"{M}:block/power_outlet"}, "powered=true": {"model": f"{M}:block/power_outlet_on"}}})

# reactor by heat band
for h in range(4):
    w(f"{AS}/models/block/fission_reactor_{h}.json", {"parent": "minecraft:block/cube_bottom_top", "textures": {
        "side": f"{M}:block/fission_reactor_side_{h}", "top": f"{M}:block/fission_reactor_top_{h}", "bottom": f"{M}:block/fission_reactor_top_0"}})
w(f"{AS}/blockstates/fission_reactor.json", {"variants": {f"heat={h}": {"model": f"{M}:block/fission_reactor_{h}"} for h in range(4)}})

for b in ("coolant_cell", "uranium_ore", "deepslate_uranium_ore", "reactor_slag"):
    cube_all(b)
    w(f"{AS}/blockstates/{b}.json", {"variants": {"": {"model": f"{M}:block/{b}"}}})

# wires: multipart core + six explicit arms
def box(fr, to, tex):
    return {"from": fr, "to": to, "faces": {f: {"texture": tex} for f in ("north", "south", "east", "west", "up", "down")}}
for wire, a in (("copper_wire", 6), ("heavy_wire", 5)):
    b = 16 - a; t = "#wire"
    tex = {"wire": f"{M}:block/{wire}", "particle": f"{M}:block/{wire}"}
    w(f"{AS}/models/block/{wire}_core.json", {"textures": tex, "elements": [box([a, a, a], [b, b, b], t)]})
    arms = {"north": ([a, a, 0], [b, b, a]), "south": ([a, a, b], [b, b, 16]), "west": ([0, a, a], [a, b, b]),
            "east": ([b, a, a], [16, b, b]), "down": ([a, 0, a], [b, a, b]), "up": ([a, b, a], [b, 16, b])}
    for d, (fr, to) in arms.items():
        w(f"{AS}/models/block/{wire}_{d}.json", {"textures": tex, "elements": [box(fr, to, t)]})
    parts = [{"apply": {"model": f"{M}:block/{wire}_core"}}]
    for d in arms:
        parts.append({"when": {d: "true"}, "apply": {"model": f"{M}:block/{wire}_{d}"}})
    w(f"{AS}/blockstates/{wire}.json", {"multipart": parts})
    w(f"{AS}/models/item/{wire}.json", {"textures": tex, "elements": [box([a, a, 0], [b, b, 16], t)],
        "display": {"gui": {"rotation": [30, 45, 0], "scale": [0.9, 0.9, 0.9]}, "ground": {"scale": [0.5, 0.5, 0.5]},
                    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
                    "firstperson_righthand": {"rotation": [0, 45, 0], "scale": [0.4, 0.4, 0.4]}}})

# item models
w(f"{AS}/models/item/coal_generator.json", {"parent": f"{M}:block/coal_generator"})
w(f"{AS}/models/item/battery.json", {"parent": f"{M}:block/battery_2"})
w(f"{AS}/models/item/power_outlet.json", {"parent": f"{M}:block/power_outlet_on"})
w(f"{AS}/models/item/fission_reactor.json", {"parent": f"{M}:block/fission_reactor_1"})
for b in ("coolant_cell", "uranium_ore", "deepslate_uranium_ore", "reactor_slag"):
    w(f"{AS}/models/item/{b}.json", {"parent": f"{M}:block/{b}"})
for it in ("raw_uranium", "uranium_ingot", "fuel_rod", "depleted_fuel_rod", "multimeter"):
    w(f"{AS}/models/item/{it}.json", {"parent": "minecraft:item/handheld" if it in ("fuel_rod", "depleted_fuel_rod") else "minecraft:item/generated",
                                       "textures": {"layer0": f"{M}:item/{it}"}})

# loot
for b in ("coal_generator", "copper_wire", "heavy_wire", "battery", "power_outlet", "fission_reactor", "coolant_cell", "reactor_slag"):
    w(f"{DA}/loot_table/blocks/{b}.json", {"type": "minecraft:block", "pools": [{"rolls": 1, "bonus_rolls": 0,
        "entries": [{"type": "minecraft:item", "name": f"{M}:{b}"}], "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
for b in ("uranium_ore", "deepslate_uranium_ore"):
    w(f"{DA}/loot_table/blocks/{b}.json", {"type": "minecraft:block", "pools": [{"rolls": 1, "bonus_rolls": 0, "entries": [{
        "type": "minecraft:alternatives", "children": [
            {"type": "minecraft:item", "name": f"{M}:{b}", "conditions": [{"condition": "minecraft:match_tool", "predicate": {
                "predicates": {"minecraft:enchantments": [{"enchantments": "minecraft:silk_touch", "levels": {"min": 1}}]}}}]},
            {"type": "minecraft:item", "name": f"{M}:raw_uranium", "functions": [
                {"function": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:ore_drops"},
                {"function": "minecraft:explosion_decay"}]}]}]}]})

# tags (overwrite with the full lists)
pick = ["keycard_reader", "containment_generator", "containment_wall", "coal_generator", "battery", "power_outlet", "fission_reactor",
        "coolant_cell", "uranium_ore", "deepslate_uranium_ore", "reactor_slag", "heavy_wire"]
w(os.path.join(RES, "data/minecraft/tags/block/mineable/pickaxe.json"), {"replace": False, "values": [f"{M}:{b}" for b in pick]})
w(os.path.join(RES, "data/minecraft/tags/block/needs_iron_tool.json"), {"replace": False, "values": [f"{M}:{b}" for b in
    ("keycard_reader", "containment_generator", "containment_wall", "fission_reactor", "uranium_ore", "deepslate_uranium_ore")]})
w(os.path.join(RES, "data/c/tags/item/ingots/uranium.json"), {"replace": False, "values": [f"{M}:uranium_ingot"]})
w(os.path.join(RES, "data/c/tags/item/raw_materials/uranium.json"), {"replace": False, "values": [f"{M}:raw_uranium"]})
w(os.path.join(RES, "data/c/tags/block/ores/uranium.json"), {"replace": False, "values": [f"{M}:uranium_ore", f"{M}:deepslate_uranium_ore"]})

# worldgen: uranium below y=32, a little rarer than iron
w(f"{DA}/worldgen/configured_feature/uranium_ore.json", {"type": "minecraft:ore", "config": {"size": 6, "discard_chance_on_air_exposure": 0.3, "targets": [
    {"target": {"predicate_type": "minecraft:tag_match", "tag": "minecraft:stone_ore_replaceables"}, "state": {"Name": f"{M}:uranium_ore"}},
    {"target": {"predicate_type": "minecraft:tag_match", "tag": "minecraft:deepslate_ore_replaceables"}, "state": {"Name": f"{M}:deepslate_uranium_ore"}}]}})
w(f"{DA}/worldgen/placed_feature/uranium_ore.json", {"feature": f"{M}:uranium_ore", "placement": [
    {"type": "minecraft:count", "count": 5}, {"type": "minecraft:in_square"},
    {"type": "minecraft:height_range", "height": {"type": "minecraft:trapezoid", "min_inclusive": {"above_bottom": 0}, "max_inclusive": {"absolute": 32}}},
    {"type": "minecraft:biome"}]})
w(f"{DA}/neoforge/biome_modifier/add_uranium_ore.json", {"type": "neoforge:add_features", "biomes": "#minecraft:is_overworld",
    "features": f"{M}:uranium_ore", "step": "underground_ores"})

# recipes
for kind, t in (("smelting", 200), ("blasting", 100)):
    for src in ("raw_uranium", "uranium_ore", "deepslate_uranium_ore"):
        w(f"{DA}/recipe/uranium_ingot_{kind}_{src}.json", {"type": f"minecraft:{kind}", "category": "misc", "ingredient": {"item": f"{M}:{src}"},
            "result": {"id": f"{M}:uranium_ingot"}, "experience": 1.0, "cookingtime": t})
shaped("fuel_rod", ["IUI", "IUI", "IUI"], {"I": "minecraft:iron_ingot", "U": f"{M}:uranium_ingot"}, f"{M}:fuel_rod")
shaped("coal_generator", ["III", "IFI", "IRI"], {"I": "minecraft:iron_ingot", "F": "minecraft:furnace", "R": "minecraft:redstone"}, f"{M}:coal_generator")
shaped("copper_wire", ["WWW", "CCC", "WWW"], {"W": "minecraft:black_wool", "C": "minecraft:copper_ingot"}, f"{M}:copper_wire", 16)
shaped("heavy_wire", ["III", "WWW", "III"], {"I": "minecraft:iron_ingot", "W": f"{M}:copper_wire"}, f"{M}:heavy_wire", 3)
shaped("battery", ["IRI", "RCR", "IRI"], {"I": "minecraft:iron_ingot", "R": "minecraft:redstone_block", "C": "minecraft:copper_block"}, f"{M}:battery")
shaped("power_outlet", ["IQI", "RWR", "IQI"], {"I": "minecraft:iron_ingot", "Q": "minecraft:quartz", "R": "minecraft:redstone", "W": f"{M}:copper_wire"}, f"{M}:power_outlet", 2)
shaped("fission_reactor", ["OIO", "URU", "OIO"], {"O": "minecraft:obsidian", "I": "minecraft:iron_block", "U": f"{M}:uranium_ingot", "R": "minecraft:redstone_block"}, f"{M}:fission_reactor")
shaped("coolant_cell", ["GPG", "PBP", "GPG"], {"G": "minecraft:glass", "P": "minecraft:packed_ice", "B": "minecraft:blue_ice"}, f"{M}:coolant_cell", 2)
shaped("multimeter", ["IGI", "IRI", "ICI"], {"I": "minecraft:iron_ingot", "G": "minecraft:glass_pane", "R": "minecraft:redstone", "C": "minecraft:copper_ingot"}, f"{M}:multimeter")

# lang
L = json.load(open(f"{AS}/lang/en_us.json"))
L.update({
    "block.breachprotocol.coal_generator": "Coal Generator", "block.breachprotocol.copper_wire": "Copper Wire",
    "block.breachprotocol.heavy_wire": "Heavy-Duty Wire", "block.breachprotocol.battery": "Site Battery",
    "block.breachprotocol.power_outlet": "Facility Power Outlet", "block.breachprotocol.fission_reactor": "Fission Reactor Core",
    "block.breachprotocol.coolant_cell": "Coolant Cell", "block.breachprotocol.uranium_ore": "Uranium Ore",
    "block.breachprotocol.deepslate_uranium_ore": "Deepslate Uranium Ore", "block.breachprotocol.reactor_slag": "Reactor Slag",
    "item.breachprotocol.raw_uranium": "Raw Uranium", "item.breachprotocol.uranium_ingot": "Uranium Ingot",
    "item.breachprotocol.fuel_rod": "Uranium Fuel Rod", "item.breachprotocol.depleted_fuel_rod": "Depleted Fuel Rod",
    "item.breachprotocol.multimeter": "Multimeter", "effect.breachprotocol.radiation": "Radiation Sickness",
})
w(f"{AS}/lang/en_us.json", L)
print("power data generated")
