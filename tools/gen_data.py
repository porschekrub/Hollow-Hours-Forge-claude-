#!/usr/bin/env python3
"""Static data for Hollow Hours: damage types (+ tags), crafting recipes, item models."""
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, "..", "hollow-hours", "src", "main", "resources")
DATA = os.path.join(RES, "data")
ASSETS = os.path.join(RES, "assets", "hollowhours")


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)


def damage_types():
    for name, exhaustion in (("ghost_hit", 0.1), ("ghost_kill", 0.0), ("curse", 0.0)):
        write(os.path.join(DATA, "hollowhours", "damage_type", name + ".json"),
              {"message_id": "hollowhours." + name, "exhaustion": exhaustion, "scaling": "never"})
    tags = {
        "bypasses_shield": ["ghost_hit", "ghost_kill", "curse"],
        "bypasses_armor": ["ghost_kill", "curse"],
        "bypasses_enchantments": ["ghost_kill", "curse"],
        "bypasses_resistance": ["ghost_kill"],
        "bypasses_effects": ["ghost_kill"],
        "no_knockback": ["ghost_kill", "curse"],
        "no_impact": ["curse"],
        "is_explosion": [],
    }
    for tag, names in tags.items():
        if not names:
            continue
        write(os.path.join(DATA, "minecraft", "tags", "damage_type", tag + ".json"),
              {"replace": False, "values": ["hollowhours:" + n for n in names]})


def shaped(name, pattern, key, result, count=1):
    k = {}
    for c, v in key.items():
        k[c] = {"tag": v[1:]} if v.startswith("#") else {"item": v}
    write(os.path.join(DATA, "hollowhours", "recipes", name + ".json"),
          {"type": "minecraft:crafting_shaped", "category": "misc", "pattern": pattern, "key": k,
           "result": {"item": "hollowhours:" + result, "count": count}})


def shapeless(name, items, result, count=1):
    ing = [{"tag": v[1:]} if v.startswith("#") else {"item": v} for v in items]
    write(os.path.join(DATA, "hollowhours", "recipes", name + ".json"),
          {"type": "minecraft:crafting_shapeless", "category": "misc", "ingredients": ing,
           "result": {"item": "hollowhours:" + result, "count": count}})


def recipes():
    shaped("board_wood", ["PSP", "SNS", "PSP"], {"P": "#minecraft:planks", "S": "minecraft:stick", "N": "minecraft:iron_nugget"}, "board_wood", 8)
    shaped("board_stone", ["CBC", "BCB", "CBC"], {"C": "minecraft:cobblestone", "B": "minecraft:stone_bricks"}, "board_stone", 8)
    shaped("board_iron", ["IBI", "BIB", "IBI"], {"I": "minecraft:iron_ingot", "B": "minecraft:iron_bars"}, "board_iron", 8)
    shaped("battery", ["N", "R", "C"], {"N": "minecraft:iron_nugget", "R": "minecraft:redstone", "C": "minecraft:copper_ingot"}, "battery", 1)
    shaped("flashlight", [" G ", "ITI", " I "], {"G": "minecraft:glass", "I": "minecraft:iron_ingot", "T": "minecraft:redstone_torch"}, "flashlight", 1)
    shaped("closet_item", ["PPP", "PCP", "PPP"], {"P": "#minecraft:planks", "C": "minecraft:chest"}, "closet_item", 1)
    shapeless("repair_wood", ["#minecraft:planks", "#minecraft:planks", "minecraft:stick", "minecraft:iron_nugget"], "repair_wood", 1)
    shapeless("repair_iron", ["minecraft:iron_ingot", "minecraft:iron_ingot", "minecraft:iron_nugget", "minecraft:iron_nugget", "minecraft:stick"], "repair_iron", 1)
    shapeless("repair_stone", ["minecraft:cobblestone", "minecraft:cobblestone", "minecraft:clay_ball", "minecraft:gravel"], "repair_stone", 2)
    shapeless("repair_glass", ["minecraft:glass_pane", "minecraft:sand", "minecraft:clay_ball"], "repair_glass", 2)
    shapeless("ghost_guide", ["minecraft:book", "minecraft:ink_sac", "minecraft:soul_sand"], "ghost_guide", 1)


def item_models():
    simple = ["ghost_guide", "battery", "board_wood", "board_stone", "board_iron", "repair_wood", "repair_iron", "repair_stone", "repair_glass", "closet_item"]
    for s in simple:
        write(os.path.join(ASSETS, "models", "item", s + ".json"), {"parent": "minecraft:item/generated", "textures": {"layer0": "hollowhours:item/" + s}})
    write(os.path.join(ASSETS, "models", "item", "flashlight.json"), {
        "parent": "minecraft:item/handheld", "textures": {"layer0": "hollowhours:item/flashlight"},
        "overrides": [{"predicate": {"hollowhours:on": 1}, "model": "hollowhours:item/flashlight_on"}]})
    write(os.path.join(ASSETS, "models", "item", "flashlight_on.json"), {"parent": "minecraft:item/handheld", "textures": {"layer0": "hollowhours:item/flashlight_on"}})
    eggs = ["ghost_%d" % n for n in range(1, 6)] + ["stalker_%d" % n for n in range(1, 4)]
    for e in eggs:
        write(os.path.join(ASSETS, "models", "item", e + "_spawn_egg.json"), {"parent": "minecraft:item/template_spawn_egg"})


if __name__ == "__main__":
    damage_types()
    recipes()
    item_models()
    print("data written")
