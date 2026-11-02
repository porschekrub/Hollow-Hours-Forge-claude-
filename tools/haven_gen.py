"""The haven: the dimension the rescue helicopter flies you to. Lush, rich and safe - cherry hills, golden meadows,
flower woods, a fruit jungle, white beaches and a coral lagoon; more ore, more fruit, more animals, no monsters
(and no structures, so no pillagers). Built from the game's own biome data (pass the client jar), with:
  - the monster rooms (spawners) taken out and every monster spawn removed,
  - extra ores and extra fruit/flowers appended (our own placed features, so the feature order never clashes),
  - the overworld terrain, with the surface rules taught that our beach/lagoon are sand like theirs.
Usage: python3 haven_gen.py path/to/minecraft-client.jar"""
import copy, io, json, os, sys, zipfile

ROOT = os.path.join(os.path.dirname(__file__), '..', 'hollow-hours', 'src', 'main', 'resources', 'data', 'hollowhours')

# our biome -> the vanilla biome it starts from
BIOMES = {
    "haven_blossom": "cherry_grove", "haven_meadow": "meadow", "haven_flower_woods": "flower_forest",
    "haven_fruit_jungle": "sparse_jungle", "haven_beach": "beach", "haven_lagoon": "warm_ocean",
}
LAND = ("haven_blossom", "haven_meadow", "haven_flower_woods", "haven_fruit_jungle")

# where each biome goes (overworld climate noise): sea, then the coast, then the land split by temperature/humidity
PARAMS = {
    "haven_lagoon": dict(continentalness=[-1.2, -0.19]),
    "haven_beach": dict(continentalness=[-0.19, -0.11]),
    "haven_blossom": dict(continentalness=[-0.11, 1.0], temperature=[-1.0, -0.15]),
    "haven_meadow": dict(continentalness=[-0.11, 1.0], temperature=[-0.15, 0.2], humidity=[-1.0, 0.0]),
    "haven_flower_woods": dict(continentalness=[-0.11, 1.0], temperature=[-0.15, 0.2], humidity=[0.0, 1.0]),
    "haven_fruit_jungle": dict(continentalness=[-0.11, 1.0], temperature=[0.2, 1.0]),
}

ORES = [  # (name, vanilla configured ore, count, min y, max y)
    ("haven_ore_coal", "ore_coal", 30, 0, 160), ("haven_ore_iron", "ore_iron", 28, -48, 96), ("haven_ore_copper", "ore_copper_large", 10, -16, 96),
    ("haven_ore_gold", "ore_gold", 12, -64, 40), ("haven_ore_redstone", "ore_redstone", 8, -64, 16), ("haven_ore_lapis", "ore_lapis", 6, -48, 32),
    ("haven_ore_diamond", "ore_diamond_small", 10, -64, 16), ("haven_ore_emerald", "ore_emerald", 14, -16, 160),
]
PLANTS = [  # (name, vanilla configured feature, how: ("count", n) or ("rarity", 1 in n), heightmap)
    ("haven_flowers", "flower_flower_forest", ("count", 4), "MOTION_BLOCKING"),
    ("haven_melons", "patch_melon", ("rarity", 3), "WORLD_SURFACE_WG"),
    ("haven_pumpkins", "patch_pumpkin", ("rarity", 4), "WORLD_SURFACE_WG"),
    ("haven_berries", "patch_berry_bush", ("rarity", 3), "WORLD_SURFACE_WG"),
    ("haven_sugar_cane", "patch_sugar_cane", ("count", 3), "WORLD_SURFACE_WG"),
]
TREES = ("haven_trees", "trees_flower_forest", 3)
NO = {"minecraft:monster_room", "minecraft:monster_room_deep"}

CREATURES = [("sheep", 12, 2, 4), ("pig", 10, 2, 4), ("chicken", 10, 2, 4), ("cow", 9, 2, 4), ("rabbit", 6, 2, 3), ("horse", 4, 2, 4)]
EXTRA = {"haven_fruit_jungle": [("parrot", 12, 1, 2), ("panda", 2, 1, 2)], "haven_beach": [("turtle", 6, 2, 5)],
         "haven_meadow": [("donkey", 2, 1, 2)], "haven_flower_woods": [("fox", 4, 2, 3)]}


def save(obj, *p):
    path = os.path.join(ROOT, *p)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        json.dump(obj, f, indent=1)


def placed():
    for name, ore, n, y0, y1 in ORES:
        save({"feature": "minecraft:" + ore, "placement": [
            {"type": "minecraft:count", "count": n}, {"type": "minecraft:in_square"},
            {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "min_inclusive": {"absolute": y0}, "max_inclusive": {"absolute": y1}}},
            {"type": "minecraft:biome"}]}, 'worldgen', 'placed_feature', name + '.json')
    for name, feat, (how, n), hm in PLANTS:
        first = {"type": "minecraft:count", "count": n} if how == "count" else {"type": "minecraft:rarity_filter", "chance": n}
        save({"feature": "minecraft:" + feat, "placement": [first, {"type": "minecraft:in_square"},
                                                             {"type": "minecraft:heightmap", "heightmap": hm}, {"type": "minecraft:biome"}]},
             'worldgen', 'placed_feature', name + '.json')
    name, feat, n = TREES
    save({"feature": "minecraft:" + feat, "placement": [
        {"type": "minecraft:count", "count": n}, {"type": "minecraft:in_square"},
        {"type": "minecraft:surface_water_depth_filter", "max_water_depth": 0},
        {"type": "minecraft:heightmap", "heightmap": "OCEAN_FLOOR"}, {"type": "minecraft:biome"}]}, 'worldgen', 'placed_feature', name + '.json')


def spawn(kind, w, a, b):
    return {"type": "minecraft:" + kind, "weight": w, "minCount": a, "maxCount": b}


def biome(name, base):
    b = copy.deepcopy(base)
    feats = [[f for f in step if f not in NO] for step in b["features"]]
    while len(feats) < 11:
        feats.append([])
    feats[6] += ["hollowhours:" + o[0] for o in ORES]
    if name in LAND:
        feats[9] += ["hollowhours:" + p[0] for p in PLANTS] + ["hollowhours:" + TREES[0]]
    b["features"] = feats
    sp = b["spawners"]
    sp["monster"] = []
    sp["ambient"] = []
    if name != "haven_lagoon":
        sp["creature"] = [spawn(*c) for c in CREATURES + EXTRA.get(name, [])]
    else:
        sp["creature"] = []
        sp["water_creature"] = [spawn("dolphin", 2, 1, 2), spawn("squid", 3, 1, 4)]
        sp["water_ambient"] = [spawn("tropical_fish", 25, 8, 8), spawn("pufferfish", 15, 1, 3)]
    b["spawn_costs"] = {}
    b["creature_spawn_probability"] = 0.14
    b["temperature"] = max(0.5, b["temperature"])   # never snow
    return b


def teach_surface(rule):
    """wherever the overworld surface rules name a vanilla biome, name ours too"""
    if isinstance(rule, dict):
        if rule.get("type") == "minecraft:biome" and isinstance(rule.get("biome_is"), list):
            for ours, theirs in BIOMES.items():
                if "minecraft:" + theirs in rule["biome_is"]:
                    rule["biome_is"].append("hollowhours:" + ours)
        for v in rule.values():
            teach_surface(v)
    elif isinstance(rule, list):
        for v in rule:
            teach_surface(v)


def main(jar):
    z = zipfile.ZipFile(jar)

    def vanilla(p):
        return json.loads(z.read('data/minecraft/' + p))

    placed()
    for name, base in BIOMES.items():
        save(biome(name, vanilla('worldgen/biome/%s.json' % base)), 'worldgen', 'biome', name + '.json')

    noise = vanilla('worldgen/noise_settings/overworld.json')
    teach_surface(noise["surface_rule"])
    save(noise, 'worldgen', 'noise_settings', 'haven.json')

    dim = vanilla('dimension_type/overworld.json')
    dim["has_raids"] = False
    save(dim, 'dimension_type', 'haven.json')

    points = []
    for name, p in PARAMS.items():
        par = {"temperature": [-1.0, 1.0], "humidity": [-1.0, 1.0], "continentalness": [-1.2, 1.0], "erosion": [-1.0, 1.0],
               "weirdness": [-1.0, 1.0], "depth": 0.0, "offset": 0.0}
        par.update(p)
        points.append({"biome": "hollowhours:" + name, "parameters": par})
    save({"type": "hollowhours:haven", "generator": {"type": "minecraft:noise", "settings": "hollowhours:haven",
                                                      "biome_source": {"type": "minecraft:multi_noise", "biomes": points}}},
         'dimension', 'haven.json')
    print('haven written:', ', '.join(BIOMES))


if __name__ == '__main__':
    main(sys.argv[1])
