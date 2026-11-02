"""3D versions of every item the two mods add: built from each item's own 16x16 picture, thick in the middle and thin at
the edges ("puffy") or an even slab (books, the phone). The inventory icon stays the same (front view).
Spawn eggs keep their two tinted layers; the grave compass gets a 3D model for each needle position.
Usage: python3 items3d.py path/to/minecraft-client.jar (for the vanilla spawn egg / compass pictures)"""
import io, json, os, sys, zipfile
from PIL import Image
from food3d import distances, DISPLAY

ROOT = os.path.join(os.path.dirname(__file__), '..')
HH = os.path.join(ROOT, 'hollow-hours', 'src', 'main', 'resources', 'assets', 'hollowhours')
HB = os.path.join(ROOT, 'helper-buddy', 'src', 'main', 'resources', 'assets', 'helperbuddy')

# item -> (texture, profile, thickness): "puffy" = thick in the middle, "slab" = the same all over
HH_ITEMS = {
    "ghost_guide": ("ghost_guide", "slab", 3), "phone": ("phone", "slab", 1.5), "phone_charging": ("phone_charging", "slab", 1.5),
    "charger": ("charger", "puffy", 3),
    "board_wood": ("board_wood", "puffy", 2), "board_stone": ("board_stone", "puffy", 2), "board_iron": ("board_iron", "puffy", 2),
    "repair_wood": ("repair_wood", "puffy", 2.5), "repair_iron": ("repair_iron", "puffy", 2.5), "repair_stone": ("repair_stone", "puffy", 3),
    "repair_glass": ("repair_glass", "puffy", 3), "closet_item": ("closet_item", "puffy", 3), "restorer": ("restorer", "puffy", 4),
    "canteen": ("canteen", "puffy", 4), "canteen_empty": ("canteen_empty", "puffy", 4),
}
HB_ITEMS = {"command_book": ("command_book", "slab", 3), "helper_contract": ("helper_contract", "puffy", 2.5)}
# vanilla buckets and bottles: item -> ([(texture, tint index or None), ...] bottom layer first, thickest it gets)
VANILLA = {b: ([(b, None)], 5) for b in ("bucket", "water_bucket", "lava_bucket", "milk_bucket", "powder_snow_bucket", "cod_bucket",
                                          "salmon_bucket", "pufferfish_bucket", "tropical_fish_bucket", "axolotl_bucket", "tadpole_bucket")}
VANILLA.update({p: ([("potion_overlay", 0), (p, None)], 4) for p in ("potion", "splash_potion", "lingering_potion")})
VANILLA.update({b: ([(b, None)], 4) for b in ("glass_bottle", "honey_bottle", "experience_bottle")})


def power_bank_textures():
    """The charger is a power bank with its cable; the phone gets a 'charging' screen."""
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    p = im.load()
    for y in range(4, 15):
        for x in range(6, 13):
            edge = x in (6, 12) or y in (4, 14)
            corner = (x in (6, 12)) and (y in (4, 14))
            if corner:
                continue
            p[x, y] = (40, 42, 48, 255) if edge else (62, 66, 74, 255)
    for x in range(7, 12):
        p[x, 5] = (85, 90, 100, 255)                                  # top bevel
    for i, x in enumerate((8, 9, 10, 11)):
        p[x, 12] = (60, 200, 255, 255) if i < 3 else (30, 60, 80, 255)  # charge LEDs (3 of 4 lit)
    p[9, 4] = (20, 20, 22, 255)                                       # the port
    # the white cable: out of the top, over to the left and down to the plug
    for (x, y) in [(9, 3), (9, 2), (8, 1), (7, 1), (6, 1), (5, 1), (4, 2), (3, 3), (3, 4), (3, 5), (3, 6), (3, 7), (3, 8), (3, 9)]:
        p[x, y] = (232, 232, 236, 255)
    for (x, y) in [(2, 10), (3, 10), (4, 10), (2, 11), (3, 11), (4, 11)]:
        p[x, y] = (200, 200, 206, 255)                                # plug
    p[3, 12] = (150, 150, 156, 255)
    im.save(os.path.join(HH, 'textures', 'item', 'charger.png'))
    ph = Image.open(os.path.join(HH, 'textures', 'item', 'phone.png')).convert('RGBA')
    q = ph.load()
    # the screen goes dark with a big green battery and bolt
    xs = [x for x in range(16) for y in range(16) if q[x, y][3] > 0]
    ys = [y for x in range(16) for y in range(16) if q[x, y][3] > 0]
    x0, x1, y0, y1 = min(xs) + 1, max(xs) - 1, min(ys) + 2, max(ys) - 3
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            q[x, y] = (12, 16, 20, 255)
    cx, cy = (x0 + x1) // 2, (y0 + y1) // 2
    for y in range(cy - 3, cy + 4):
        for x in range(cx - 2, cx + 3):
            if x in (cx - 2, cx + 2) or y in (cy - 3, cy + 3):
                q[x, y] = (90, 220, 110, 255)
    q[cx, cy - 4] = (90, 220, 110, 255)
    for (x, y) in [(cx, cy - 2), (cx - 1, cy - 1), (cx, cy - 1), (cx, cy), (cx + 1, cy), (cx, cy + 1), (cx, cy + 2)]:
        q[x, y] = (255, 230, 80, 255)
    ph.save(os.path.join(HH, 'textures', 'item', 'phone_charging.png'))


def mask_of(im):
    return [[im.getpixel((x, y))[3] > 16 for x in range(16)] for y in range(16)]


def filled(mask):
    """the shape with its holes filled in (a glass bottle is only an outline; its inside is still bottle)"""
    out = [[True] * 16 for _ in range(16)]
    todo = [(x, y) for x in range(16) for y in (0, 15)] + [(x, y) for x in (0, 15) for y in range(16)]
    while todo:
        x, y = todo.pop()
        if 0 <= x < 16 and 0 <= y < 16 and out[y][x] and not mask[y][x]:
            out[y][x] = False
            todo += [(x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)]
    return out


def elements(mask, profile, thick, tex="#t", tint=None, grow=0.0):
    d = distances(mask)

    def depth(x, y):
        if profile == "slab":
            return thick
        return min(thick, 0.75 + 0.75 * d[y][x]) if thick > 1 else thick

    out = []
    for py in range(16):
        x = 0
        while x < 16:
            if not mask[py][x]:
                x += 1
                continue
            t = depth(x, py)
            x1 = x
            while x1 + 1 < 16 and mask[py][x1 + 1] and depth(x1 + 1, py) == t:
                x1 += 1
            t += grow
            run = [x, py, x1 + 1, py + 1]

            def face(uv):
                f = {"uv": uv, "texture": tex}
                if tint is not None:
                    f["tintindex"] = tint
                return f
            out.append({"from": [x, 15 - py, round(8 - t / 2, 3)], "to": [x1 + 1, 16 - py, round(8 + t / 2, 3)], "faces": {
                "south": face(run), "north": face([x1 + 1, py, x, py + 1]), "up": face(run), "down": face(run),
                "west": face([x, py, x + 1, py + 1]), "east": face([x1, py, x1 + 1, py + 1])}})
            x = x1 + 1
    return out


def model(texture_ref, mask, profile, thick):
    return {"gui_light": "front", "textures": {"t": texture_ref, "particle": texture_ref},
            "elements": elements(mask, profile, thick), "display": DISPLAY}


def save(path, m):
    with open(path, 'w') as f:
        json.dump(m, f, separators=(',', ':'))


def main(jar):
    power_bank_textures()
    z = zipfile.ZipFile(jar)

    def vanilla(name):
        return Image.open(io.BytesIO(z.read('assets/minecraft/textures/item/%s.png' % name))).convert('RGBA').crop((0, 0, 16, 16))

    for base, ns, items in ((HH, "hollowhours", HH_ITEMS), (HB, "helperbuddy", HB_ITEMS)):
        for item, (tex, profile, thick) in items.items():
            im = Image.open(os.path.join(base, 'textures', 'item', tex + '.png')).convert('RGBA')
            m = model(ns + ":item/" + tex, mask_of(im), profile, thick)
            path = os.path.join(base, 'models', 'item', item + '.json')
            if item == "canteen":
                m["overrides"] = [{"predicate": {"hollowhours:empty": 1}, "model": "hollowhours:item/canteen_empty"}]
            if item == "phone":
                m["overrides"] = [{"predicate": {"hollowhours:charging": 1}, "model": "hollowhours:item/phone_charging"}]
            save(path, m)

    # vanilla buckets and bottles (the potion's colour layer keeps its tint; the glass sits a hair outside it)
    out = os.path.join(ROOT, 'hollow-hours', 'src', 'main', 'resources', 'assets', 'minecraft', 'models', 'item')
    for item, (layers, thick) in VANILLA.items():
        ims = [vanilla(t) for t, _ in layers]
        shape = filled([[any(im.getpixel((x, y))[3] > 16 for im in ims) for x in range(16)] for y in range(16)])
        tex = {"l%d" % i: "minecraft:item/" + t for i, (t, _) in enumerate(layers)}
        tex["particle"] = tex["l%d" % (len(layers) - 1)]
        els = []
        for i, (_, tint) in enumerate(layers):
            els += elements(shape, "puffy", thick, "#l%d" % i, tint, grow=0.05 * i)
        save(os.path.join(out, item + '.json'), {"gui_light": "front", "textures": tex, "elements": els, "display": DISPLAY})

    # spawn eggs: the egg (tint 0) and its spots (tint 1, a hair proud of the egg)
    em = mask_of(vanilla('spawn_egg'))
    # (the spots layer follows the egg's shape, a hair bigger; where the spots picture is see-through, the egg shows)
    egg_model = {"gui_light": "front", "textures": {"egg": "minecraft:item/spawn_egg", "spots": "minecraft:item/spawn_egg_overlay",
                                                    "particle": "minecraft:item/spawn_egg"},
                 "elements": elements(em, "puffy", 4, "#egg", 0) + elements(em, "puffy", 4, "#spots", 1, grow=0.05), "display": DISPLAY}
    for base, ns in ((HH, "hollowhours"), (HB, "helperbuddy")):
        save(os.path.join(base, 'models', 'item', 'spawn_egg_3d.json'), egg_model)
        for f in os.listdir(os.path.join(base, 'models', 'item')):
            if f.endswith('_spawn_egg.json'):
                save(os.path.join(base, 'models', 'item', f), {"parent": ns + ":item/spawn_egg_3d"})

    # the grave compass: one 3D model per needle position
    for i in range(32):
        im = vanilla('compass_%02d' % i)
        save(os.path.join(HH, 'models', 'item', 'grave_compass_%02d.json' % i), model("minecraft:item/compass_%02d" % i, mask_of(im), "puffy", 2.5))
    # the needle angle picks the picture, like the vanilla compass
    overrides = [{"predicate": {"angle": 0.0}, "model": "hollowhours:item/grave_compass_16"}]
    for j in range(31):
        overrides.append({"predicate": {"angle": 0.015625 + 0.03125 * j}, "model": "hollowhours:item/grave_compass_%02d" % ((17 + j) % 32)})
    overrides.append({"predicate": {"angle": 0.984375}, "model": "hollowhours:item/grave_compass_16"})
    comp_base = model("minecraft:item/compass_16", mask_of(vanilla('compass_16')), "puffy", 2.5)
    comp_base["overrides"] = overrides
    save(os.path.join(HH, 'models', 'item', 'grave_compass.json'), comp_base)
    print('3D item models written')


if __name__ == '__main__':
    main(sys.argv[1])
