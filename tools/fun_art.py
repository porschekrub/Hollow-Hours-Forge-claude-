"""Playground / living-room blocks and balls: textures, block + item models, blockstates, loot tables, recipes and
mining tags. Every model is drawn facing north (its front toward -z); blockstates turn it."""
from PIL import Image
import json, math, os, random

R = os.path.join(os.path.dirname(__file__), '..', 'hollow-hours', 'src', 'main', 'resources')
A = os.path.join(R, 'assets', 'hollowhours')
D = os.path.join(R, 'data')
rng = random.Random(7)


def save_png(img, *p):
    path = os.path.join(A, *p)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def save_json(obj, root, *p):
    path = os.path.join(root, *p)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        json.dump(obj, f, indent=1)


def tex(fn, w=16, h=16):
    im = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    p = im.load()
    for y in range(h):
        for x in range(w):
            c = fn(x, y)
            if c is not None:
                p[x, y] = c if len(c) == 4 else (*c, 255)
    return im


def jit(c, k=10):
    d = rng.randint(-k, k)
    return tuple(max(0, min(255, v + d)) for v in c)


# ---------------------------------------------------------------- textures
T = {}
T['swing_wood'] = tex(lambda x, y: jit((150, 104, 62) if (x + (y // 4) * 3) % 7 else (128, 86, 50), 6))
T['swing_rope'] = tex(lambda x, y: (205, 180, 130) if (x + y) % 3 else (170, 145, 100))
T['swing_seat'] = tex(lambda x, y: jit((200, 50, 45) if 1 <= y <= 14 else (150, 30, 30), 5))
T['slide_chute'] = tex(lambda x, y: jit((235, 70, 40) if 2 <= x <= 13 else (255, 205, 40), 6))
T['slide_frame'] = tex(lambda x, y: jit((40, 110, 220), 6))
T['slide_ladder'] = tex(lambda x, y: (250, 210, 40) if (x in (1, 2, 13, 14) or y % 4 == 0) else None)
T['trampoline_mat'] = tex(lambda x, y: (22, 22, 28) if (x + y) % 2 else (34, 34, 40))
T['trampoline_pad'] = tex(lambda x, y: jit((40, 120, 230) if y % 8 < 6 else (250, 220, 60), 5))
T['metal_pole'] = tex(lambda x, y: (150 - abs(x - 8) * 6, 154 - abs(x - 8) * 6, 162 - abs(x - 8) * 6))
T['backboard'] = tex(lambda x, y: (200, 30, 30) if (x in (0, 15) or y in (0, 15)) else
                     (220, 40, 40) if ((x in (5, 10) and 7 <= y <= 12) or (y in (7, 12) and 5 <= x <= 10)) else (245, 245, 250))
T['rim'] = tex(lambda x, y: (255, 110, 20) if (x + y) % 4 else (220, 80, 10))
T['net'] = tex(lambda x, y: (240, 240, 240) if (x % 4 == 0 or (y + x // 4) % 4 == 0) else None)
T['goal_post'] = tex(lambda x, y: (245, 245, 245) if (y // 3) % 2 else (225, 225, 230))
T['arcade_side'] = tex(lambda x, y: (255, 60, 200) if 5 <= (x + y) % 16 <= 6 else (60, 230, 255) if (x + y) % 16 == 9 else jit((44, 18, 72), 4))
T['arcade_lower'] = tex(lambda x, y: (20, 20, 24) if 6 <= x <= 9 and 4 <= y <= 9 else (240, 200, 40) if 7 <= x <= 8 and 5 <= y <= 8 and y != 6 else jit((44, 18, 72), 4))
T['arcade_panel'] = tex(lambda x, y: (40, 200, 255) if (x - 10) ** 2 + (y - 5) ** 2 <= 2 else (255, 210, 40) if (x - 13) ** 2 + (y - 7) ** 2 <= 2
                        else (60, 230, 90) if (x - 10) ** 2 + (y - 10) ** 2 <= 2 else jit((24, 24, 30), 3))
T['arcade_marquee'] = tex(lambda x, y: ((255, 230, 60) if (y in (5, 6, 9, 10) and x % 3 != 2) or (x % 3 == 0 and 5 <= y <= 10) else (255, 60, 190))
                          if 2 <= y <= 13 else (20, 10, 30))
T['tv_body'] = tex(lambda x, y: jit((34, 34, 38), 3))
T['tv_stand'] = tex(lambda x, y: (70, 70, 76) if (x + y) % 5 else (56, 56, 60))
T['tv_off'] = tex(lambda x, y: (10, 10, 12) if (x == 0 or x == 15 or y == 0 or y == 9) else
                  (34, 40, 48) if x - y in (3, 4) and y < 6 else (18, 20, 24) if y < 10 else (34, 34, 38))
for k, im in T.items():
    save_png(im, 'textures', 'block', k + '.png')


def animated(name, frames, ft):
    sheet = Image.new('RGBA', (16, 16 * len(frames)))
    for i, f in enumerate(frames):
        sheet.paste(f, (0, 16 * i))
    save_png(sheet, 'textures', 'block', name + '.png')
    with open(os.path.join(A, 'textures', 'block', name + '.png.mcmeta'), 'w') as f:
        json.dump({"animation": {"frametime": ft, "interpolate": False}}, f)


# the TV: a little cartoon (a sun rising over hills, a bird flying by), 16 frames; the screen is the top 10 rows
def tv_frame(i):
    def px(x, y):
        if y >= 10:
            return (34, 34, 38)
        if x in (0, 15) or y in (0, 9):
            return (10, 10, 12)
        sky = (90 + i * 4, 170 + i * 2, 250) if i < 12 else (255, 160 - (i - 12) * 10, 90)
        sun_y = 7 - i / 2.5
        if (x - 11) ** 2 + (y - sun_y) ** 2 <= 2.2:
            return (255, 230, 80)
        hill = 6 + round(1.2 * math.sin((x + i * 0.5) * 0.7))
        if y >= hill:
            return (60, 180, 70) if (x + y) % 4 else (50, 150, 60)
        bx = 1 + i % 14
        if y == 3 and x in (bx, bx + 2) or (y == 2 and x == bx + 1):
            return (40, 40, 50)
        return sky
    return tex(px)


animated('tv_on', [tv_frame(i) for i in range(16)], 4)


# the arcade: a ship dodging falling ghosts
def arcade_frame(i):
    ship = 3 + (i % 8 if i % 16 < 8 else 7 - i % 8)

    def px(x, y):
        if x in (0, 15) or y in (0, 15):
            return (60, 30, 90)
        if (x * 7 + y * 3 + i) % 23 == 0:
            return (200, 200, 255)
        for gx, off in ((4, 0), (10, 5), (7, 10)):
            gy = (i + off) % 14
            if gx <= x <= gx + 1 and gy <= y <= gy + 1:
                return (240, 240, 250)
        if y in (12, 13) and ship <= x <= ship + 2 and not (y == 12 and x != ship + 1):
            return (80, 255, 120)
        return (6, 4, 18)
    return tex(px)


animated('arcade_screen', [arcade_frame(i) for i in range(16)], 3)

# balls (item textures)
basket = tex(lambda x, y: (25, 15, 10) if x in (7, 8) or y in (7, 8) or abs(x - 7.5) + abs(y - 7.5) == 11 else jit((235, 110, 30), 6))
save_png(basket, 'textures', 'item', 'basketball.png')
soccer = tex(lambda x, y: (25, 25, 30) if ((x // 5 + y // 5) % 2 == 0 and 1 <= x % 5 <= 3 and 1 <= y % 5 <= 3) else (245, 245, 245))
save_png(soccer, 'textures', 'item', 'soccer_ball.png')


# ---------------------------------------------------------------- models
def f(t, uv=None):
    d = {"texture": "#" + t}
    if uv:
        d["uv"] = uv
    return d


def default_uv(frm, to, d):
    """the uv Minecraft would pick from the element's position, moved inside the 16x16 texture: parts taller or
    wider than a block would otherwise read from whatever lies next to the texture in the atlas"""
    (x0, y0, z0), (x1, y1, z1) = frm, to
    u0, v0, u1, v1 = {"north": (16 - x1, 16 - y1, 16 - x0, 16 - y0), "south": (x0, 16 - y1, x1, 16 - y0),
                      "west": (z0, 16 - y1, z1, 16 - y0), "east": (16 - z1, 16 - y1, 16 - z0, 16 - y0),
                      "up": (x0, z0, x1, z1), "down": (x0, 16 - z1, x1, 16 - z0)}[d]

    def fit(a, b):
        if b - a >= 16:
            return 0, 16
        shift = -16 * math.floor(a / 16)
        a, b = a + shift, b + shift
        if b > 16:
            a, b = 16 - (b - a), 16
        return round(a, 3), round(b, 3)

    u0, u1 = fit(u0, u1)
    v0, v1 = fit(v0, v1)
    return [u0, v0, u1, v1]


def el(frm, to, t, faces=None, rot=None, uv=None):
    """an element; t = texture for every face unless faces={dir: tex} overrides some"""
    fs = {}
    for d in ("north", "south", "east", "west", "up", "down"):
        name = (faces or {}).get(d, t)
        if name is None:
            continue
        fs[d] = f(name, (uv or {}).get(d) or default_uv(frm, to, d))
    e = {"from": frm, "to": to, "faces": fs}
    if rot:
        e["rotation"] = rot
    return e


def plane(frm, to, t, axis):
    """a zero-thickness panel (both sides drawn)"""
    faces = {"z": ("north", "south"), "x": ("east", "west"), "y": ("up", "down")}[axis]
    return {"from": frm, "to": to, "faces": {d: f(t, default_uv(frm, to, d)) for d in faces}}


def model(textures, elements, particle, render_type=None, ao=True):
    m = {"parent": "block/block", "textures": {**textures, "particle": particle}, "elements": elements}
    if render_type:
        m["render_type"] = render_type
    if not ao:
        m["ambientocclusion"] = False
    return m


W = "hollowhours:block/"
models = {}

# ---- swing: an A-frame (legs leaning out front and back), the top beam, and (when nobody sits on it) the seat
BEAM = 29.6
frame = []
for x0 in (0, 14):
    for ang in (22.5, -22.5):
        # long enough to reach the floor once tilted: 31 / cos(22.5)
        frame.append(el([x0, -2.6, 7], [x0 + 2, 31, 9], "wood", rot={"origin": [x0 + 1, 31, 8], "axis": "x", "angle": ang}))
frame.append(el([0, BEAM, 7], [16, BEAM + 2, 9], "wood"))
SEAT_Y = BEAM - 1.45 * 16


def seat_parts(y0, top):
    return [el([2, y0, 6], [14, y0 + 1, 10], "seat"),
            el([2.5, y0 + 1, 7.75], [3, top, 8.25], "rope"),
            el([13, y0 + 1, 7.75], [13.5, top, 8.25], "rope")]


swing_tex = {"wood": W + "swing_wood", "seat": W + "swing_seat", "rope": W + "swing_rope"}
models['swing'] = model(swing_tex, frame + seat_parts(SEAT_Y, BEAM), W + "swing_wood")
models['swing_frame'] = model(swing_tex, frame, W + "swing_wood")
models['swing_seat'] = model(swing_tex, seat_parts(0, 1.45 * 16), W + "swing_seat")

# ---- slide: ladder at the back, a platform, and the chute down to the front
slide = [
    el([2, 0, 14], [3, 17, 15], "frame"), el([13, 0, 14], [14, 17, 15], "frame"),
    el([2, 15, 10], [14, 16, 16], "frame"),
    el([3, 7.5, -9.3], [13, 8.5, 13.3], "chute", rot={"origin": [8, 8, 2], "axis": "x", "angle": -45}),
    el([2, 7.5, -9.3], [3, 10.5, 13.3], "frame", rot={"origin": [8, 8, 2], "axis": "x", "angle": -45}),
    el([13, 7.5, -9.3], [14, 10.5, 13.3], "frame", rot={"origin": [8, 8, 2], "axis": "x", "angle": -45}),
    el([2, 0, 1], [3, 4, 2], "frame"), el([13, 0, 1], [14, 4, 2], "frame"),
]
for y in (3, 7, 11):
    slide.append(el([3, y, 14.25], [13, y + 1, 14.75], "ladder"))
models['slide'] = model({"chute": W + "slide_chute", "frame": W + "slide_frame", "ladder": W + "slide_frame"}, slide, W + "slide_chute")

# ---- trampoline
tramp = [el([2, 5.5, 2], [14, 6, 14], "mat")]
for frm, to in (([0, 5, 0], [16, 7, 2]), ([0, 5, 14], [16, 7, 16]), ([0, 5, 2], [2, 7, 14]), ([14, 5, 2], [16, 7, 14])):
    tramp.append(el(frm, to, "pad"))
for x, z in ((1, 1), (13.5, 1), (1, 13.5), (13.5, 13.5)):
    tramp.append(el([x, 0, z], [x + 1.5, 5, z + 1.5], "leg"))
models['trampoline'] = model({"mat": W + "trampoline_mat", "pad": W + "trampoline_pad", "leg": W + "metal_pole"}, tramp, W + "trampoline_pad")

# ---- basketball hoop: pole at the back, arm, backboard, orange rim 1.5 blocks up (center 4 px in front), net
hoop = [
    el([7, 0, 12], [9, 28, 14], "pole"), el([4, 0, 10], [12, 1, 16], "pole"),
    el([7, 22, 10], [9, 24, 12], "pole"),
    el([0, 18, 9], [16, 31, 10], "pole", faces={"north": "board", "south": "board"}),
    el([2.5, 23.5, -1.5], [13.5, 24, -1], "rim"), el([2.5, 23.5, 8.5], [13.5, 24, 9], "rim"),
    el([2.5, 23.5, -1], [3, 24, 8.5], "rim"), el([13, 23.5, -1], [13.5, 24, 8.5], "rim"),
    plane([3.5, 17, -0.5], [12.5, 23.5, -0.5], "net", "z"), plane([3.5, 17, 8], [12.5, 23.5, 8], "net", "z"),
    plane([3.5, 17, -0.5], [3.5, 23.5, 8], "net", "x"), plane([12.5, 17, -0.5], [12.5, 23.5, 8], "net", "x"),
]
models['basketball_hoop'] = model({"pole": W + "metal_pole", "board": W + "backboard", "rim": W + "rim", "net": W + "net"}, hoop,
                                  W + "backboard", render_type="minecraft:cutout")

# ---- soccer goal: posts + crossbar in front, nets at the back, top and sides
goal = [
    el([0, 0, 0], [1.5, 14, 1.5], "post"), el([14.5, 0, 0], [16, 14, 1.5], "post"), el([0, 12.5, 0], [16, 14, 1.5], "post"),
    el([0, 0, 14.5], [16, 1, 16], "post"), el([0, 0, 1.5], [1, 1, 14.5], "post"), el([15, 0, 1.5], [16, 1, 14.5], "post"),
    plane([0.5, 1, 15.5], [15.5, 13.5, 15.5], "net", "z"), plane([0.5, 13.5, 1.5], [15.5, 13.5, 15.5], "net", "y"),
    plane([0.75, 1, 1.5], [0.75, 13.5, 15.5], "net", "x"), plane([15.25, 1, 1.5], [15.25, 13.5, 15.5], "net", "x"),
]
models['soccer_goal'] = model({"post": W + "goal_post", "net": W + "net"}, goal, W + "goal_post", render_type="minecraft:cutout")

# ---- arcade machine
arcade = [
    el([1, 0, 5], [15, 11, 16], "side", faces={"north": "lower"}),
    el([0, 10, 1], [16, 12, 9], "panel_trim", faces={"up": "panel"}),
    el([4, 12, 3], [5, 14.5, 4], "panel_trim"),
    el([3.5, 14.5, 2.5], [5.5, 16.5, 4.5], "ball"),
    el([1, 12, 8], [15, 23, 16], "side", faces={"north": "screen"}),
    el([1, 23, 4], [15, 27, 16], "side", faces={"north": "marquee"}),
]
models['arcade'] = model({"side": W + "arcade_side", "lower": W + "arcade_lower", "panel": W + "arcade_panel", "panel_trim": W + "tv_body",
                          "ball": W + "swing_seat", "screen": W + "arcade_screen", "marquee": W + "arcade_marquee"}, arcade, W + "arcade_side")

# ---- TV on a stand
def tv(screen):
    return model({"body": W + "tv_body", "stand": W + "tv_stand", "screen": W + screen}, [
        el([3, 0, 6], [13, 1, 11], "stand"), el([7, 1, 8], [9, 3, 9.5], "stand"),
        el([0, 3, 7], [16, 13, 10], "body", faces={"north": "screen"}, uv={"north": [0, 0, 16, 10]}),
    ], W + "tv_body")


models['tv'] = tv("tv_off")
models['tv_on'] = tv("tv_on")

for k, m in models.items():
    save_json(m, A, 'models', 'block', k + '.json')

# block items use the block model (shrunk a bit more for the tall ones)
for name, scale in (("swing", 0.45), ("slide", 0.55), ("trampoline", 0.625), ("basketball_hoop", 0.4), ("soccer_goal", 0.6), ("arcade", 0.5), ("tv", 0.625)):
    save_json({"parent": "hollowhours:block/" + name, "display": {
        "gui": {"rotation": [30, 225, 0], "translation": [0, -2 if scale < 0.5 else 0, 0], "scale": [scale] * 3},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.25] * 3},
        "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [scale] * 3},
        "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [scale * 0.5] * 3},
        "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [scale * 0.5] * 3},
        "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [scale * 0.5] * 3},
    }}, A, 'models', 'item', name + '.json')


# balls: a rounded voxel ball 8 px across
def ball(name):
    t = "hollowhours:item/" + name
    return {"textures": {"t": t, "particle": t}, "elements": [
        el([4, 5, 5], [12, 11, 11], "t"), el([5, 4, 5], [11, 12, 11], "t"), el([5, 5, 4], [11, 11, 12], "t")],
        "display": {
            "gui": {"rotation": [30, 45, 0], "translation": [0, 0, 0], "scale": [1.5] * 3},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.6] * 3},
            "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
            "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 2, 1], "scale": [0.7] * 3},
            "thirdperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 2, 1], "scale": [0.7] * 3},
            "firstperson_righthand": {"rotation": [0, 0, 0], "translation": [1, 2, 0], "scale": [0.9] * 3},
            "firstperson_lefthand": {"rotation": [0, 0, 0], "translation": [1, 2, 0], "scale": [0.9] * 3},
        }}


save_json(ball("basketball"), A, 'models', 'item', 'basketball.json')
save_json(ball("soccer_ball"), A, 'models', 'item', 'soccer_ball.json')

# ---------------------------------------------------------------- blockstates
YROT = {"north": 0, "east": 90, "south": 180, "west": 270}


def facing_states(fn):
    return {"variants": {k: v for d in YROT for k, v in fn(d).items()}}


for name in ("slide", "trampoline", "basketball_hoop", "soccer_goal", "arcade"):
    save_json(facing_states(lambda d, n=name: {"facing=" + d: {"model": "hollowhours:block/" + n, "y": YROT[d]}}), A, 'blockstates', name + '.json')
save_json(facing_states(lambda d: {
    "facing=%s,occupied=false" % d: {"model": "hollowhours:block/swing", "y": YROT[d]},
    "facing=%s,occupied=true" % d: {"model": "hollowhours:block/swing_frame", "y": YROT[d]}}), A, 'blockstates', 'swing.json')
save_json(facing_states(lambda d: {
    "facing=%s,lit=false" % d: {"model": "hollowhours:block/tv", "y": YROT[d]},
    "facing=%s,lit=true" % d: {"model": "hollowhours:block/tv_on", "y": YROT[d]}}), A, 'blockstates', 'tv.json')

# ---------------------------------------------------------------- loot tables, tags, recipes
BLOCKS = ["swing", "slide", "trampoline", "basketball_hoop", "soccer_goal", "arcade", "tv"]
for name in BLOCKS:
    save_json({"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "hollowhours:" + name}],
                                                     "conditions": [{"condition": "minecraft:survives_explosion"}]}]},
              D, 'hollowhours', 'loot_tables', 'blocks', name + '.json')
save_json({"replace": False, "values": ["hollowhours:swing", "hollowhours:arcade"]}, D, 'minecraft', 'tags', 'blocks', 'mineable', 'axe.json')
save_json({"replace": False, "values": ["hollowhours:slide", "hollowhours:basketball_hoop", "hollowhours:soccer_goal", "hollowhours:tv"]},
          D, 'minecraft', 'tags', 'blocks', 'mineable', 'pickaxe.json')


def shaped(name, pattern, key, count=1):
    k = {c: ({"tag": v[1:]} if v.startswith('#') else {"item": v}) for c, v in key.items()}
    save_json({"type": "minecraft:crafting_shaped", "category": "misc", "pattern": pattern, "key": k,
               "result": {"item": "hollowhours:" + name, "count": count}}, D, 'hollowhours', 'recipes', name + '.json')


def shapeless(name, items):
    save_json({"type": "minecraft:crafting_shapeless", "category": "misc",
               "ingredients": [{"tag": v[1:]} if v.startswith('#') else {"item": v} for v in items],
               "result": {"item": "hollowhours:" + name}}, D, 'hollowhours', 'recipes', name + '.json')


shaped("swing", ["FFF", "C C", "FSF"], {"F": "#minecraft:wooden_fences", "C": "minecraft:chain", "S": "#minecraft:wooden_slabs"})
shaped("slide", ["L  ", "LI ", "LII"], {"L": "minecraft:ladder", "I": "minecraft:iron_ingot"})
shaped("trampoline", ["WWW", "ISI"], {"W": "#minecraft:wool", "I": "minecraft:iron_ingot", "S": "minecraft:slime_ball"})
shaped("basketball_hoop", ["GGG", "ISI", " I "], {"G": "minecraft:glass_pane", "I": "minecraft:iron_ingot", "S": "minecraft:string"})
shaped("soccer_goal", ["III", "S S", "I I"], {"I": "minecraft:iron_ingot", "S": "minecraft:string"})
shaped("arcade", ["PGP", "PRP", "PBP"], {"P": "#minecraft:planks", "G": "minecraft:glass_pane", "R": "minecraft:redstone", "B": "#minecraft:buttons"})
shaped("tv", ["IGI", "IRI", " I "], {"I": "minecraft:iron_ingot", "G": "minecraft:glass_pane", "R": "minecraft:redstone"})
shapeless("basketball", ["minecraft:leather", "minecraft:orange_dye", "minecraft:slime_ball"])
shapeless("soccer_ball", ["minecraft:leather", "minecraft:white_dye", "minecraft:black_dye", "minecraft:slime_ball"])
print('ok')
