"""Fighting back (2.11): item names, messages and recipes (English / Thai)."""
import collections, json, os

ROOT = os.path.join(os.path.dirname(__file__), '..', 'hollow-hours', 'src', 'main', 'resources')
L = os.path.join(ROOT, 'assets', 'hollowhours', 'lang')
REC = os.path.join(ROOT, 'data', 'hollowhours', 'recipes')

T = {
    "item.hollowhours.pistol": ("Pistol", "ปืนพก"),
    "item.hollowhours.rifle": ("Assault Rifle", "ปืนไรเฟิลจู่โจม"),
    "item.hollowhours.shotgun": ("Shotgun", "ปืนลูกซอง"),
    "item.hollowhours.sniper": ("Sniper Rifle", "ปืนสไนเปอร์"),
    "item.hollowhours.grenade": ("Hand Grenade", "ระเบิดมือ"),
    "entity.hollowhours.grenade": ("Hand Grenade", "ระเบิดมือ"),
    "item.hollowhours.pistol_ammo": ("Pistol Rounds", "กระสุนปืนพก"),
    "item.hollowhours.rifle_ammo": ("Rifle Rounds", "กระสุนไรเฟิล"),
    "item.hollowhours.shotgun_shell": ("Shotgun Shells", "กระสุนลูกซอง"),
    "item.hollowhours.sniper_ammo": ("Sniper Rounds", "กระสุนสไนเปอร์"),
    "item.hollowhours.combat_knife": ("Combat Knife", "มีดทหาร"),
    "item.hollowhours.katana": ("Warrior's Blade", "ดาบนักรบ"),
    "item.hollowhours.battle_axe": ("Battle Axe", "ขวานรบ"),
    "item.hollowhours.stun_baton": ("Stun Baton", "กระบองช็อตไฟฟ้า"),
    "item.hollowhours.spear": ("Spear", "หอก"),
    "hollowhours.armor.set.tactical": ("Tactical set", "ชุดยุทธวิธี"),
    "hollowhours.armor.set.heavy": ("Heavy soldier set", "ชุดทหารหนัก"),
    "hollowhours.armor.set.hunter": ("Ghost hunter set", "ชุดนักล่าผี"),
    "hollowhours.armor.bonus": ("Full set: ghost hits -%s%%, breaking free +%s%%", "ใส่ครบชุด: ผีตีเบาลง %s%% ดิ้นหลุดง่ายขึ้น %s%%"),
    "hollowhours.weapon.poise": ("Knocks ghosts out: %s%%", "ทำผีสลบ: %s%%"),
    "hollowhours.weapon.melee.hint": ("Hold right-click: guard (just as it swings = parry). Sprint + a full swing = heavy blow.",
                                      "คลิกขวาค้าง: ตั้งการ์ด (กดตอนผีจะตีพอดี = ปัดป้อง) วิ่ง + ฟันเต็มแรง = ฟันหนัก"),
    "hollowhours.weapon.gunstats": ("Damage %s · vs ghosts %s", "ความแรง %s · ใส่ผี %s"),
    "hollowhours.gun.ammo": ("Ammo: %s / %s", "กระสุน: %s / %s"),
    "hollowhours.gun.uses": ("Uses: %s", "ใช้: %s"),
    "hollowhours.gun.hint": ("Right-click: shoot · R: reload · Sneak: aim", "คลิกขวา: ยิง · R: รีโหลด · ย่อ: เล็ง"),
    "hollowhours.gun.hint.auto": ("Hold right-click: shoot · R: reload · Sneak: aim", "คลิกขวาค้าง: ยิงรัว · R: รีโหลด · ย่อ: เล็ง"),
    "hollowhours.gun.noammo": ("No %s!", "ไม่มี%s!"),
    "hollowhours.gun.reloading": ("Reloading...", "กำลังรีโหลด..."),
    "hollowhours.gun.pressreload": ("Press %s to reload", "กด %s เพื่อรีโหลด"),
    "hollowhours.grenade.hint": ("Right-click: throw. Goes off after 2.5 s. Knocks ghosts hard.", "คลิกขวา: ขว้าง ระเบิดใน 2.5 วินาที ทำผีสลบได้แรง"),
    "hollowhours.ko.down": ("The ghost is knocked out! It gets up again in about %s s.", "ผีสลบแล้ว! อีกประมาณ %s วินาทีมันจะฟื้น"),
    "hollowhours.combat.parry": ("Parry!", "ปัดป้องได้!"),
    "hollowhours.struggle.title": ("IT'S GOT YOU - BREAK FREE!", "ผีจับตัวไว้ - ดิ้นให้หลุด!"),
    "hollowhours.struggle.hint": ("Mash %s or %s!", "กดรัวๆ %s หรือ %s!"),
    "hollowhours.struggle.won": ("BROKE FREE!", "หลุดแล้ว!"),
    "hollowhours.struggle.lost": ("...too weak", "ดิ้นไม่หลุด..."),
    "hollowhours.fit.up": ("Stronger! %s/100", "แข็งแรงขึ้น! %s/100"),
    "hollowhours.fit.milestone": ("You feel much stronger (%s/100): more health, harder hits, easier to break free.",
                                  "รู้สึกแข็งแรงขึ้นมาก (%s/100): เลือดมากขึ้น ตีแรงขึ้น ดิ้นหลุดจากผีง่ายขึ้น"),
    "hollowhours.skill.melee.up": ("Melee skill level %s! (harder hits)", "ทักษะอาวุธระยะประชิด เลเวล %s! (ตีแรงขึ้น)"),
    "hollowhours.skill.gun.up": ("Gun skill level %s! (faster reloads, steadier aim)", "ทักษะการยิง เลเวล %s! (รีโหลดไวขึ้น เล็งนิ่งขึ้น)"),
    "hollowhours.phone.strength": ("Strength %s/100", "ความแข็งแรง %s/100"),
    "hollowhours.phone.skills": ("Melee Lv %s · Guns Lv %s", "ระยะประชิด Lv %s · ปืน Lv %s"),
    "key.hollowhours.dodge": ("Dodge (roll away)", "หลบหลีก (กลิ้งหลบ)"),
    "key.hollowhours.reload": ("Reload", "รีโหลดกระสุน"),
    "subtitles.hollowhours.gun.pistol": ("Pistol shot", "เสียงปืนพก"),
    "subtitles.hollowhours.gun.rifle": ("Rifle fire", "เสียงปืนไรเฟิล"),
    "subtitles.hollowhours.gun.shotgun": ("Shotgun blast", "เสียงปืนลูกซอง"),
    "subtitles.hollowhours.gun.sniper": ("Sniper shot", "เสียงปืนสไนเปอร์"),
    "subtitles.hollowhours.gun.grenade": ("Grenade explodes", "ระเบิดมือระเบิด"),
    "subtitles.hollowhours.gun.reload": ("Reloading", "รีโหลดกระสุน"),
    "subtitles.hollowhours.gun.cock": ("Gun cocked", "ขึ้นลำกล้อง"),
    "subtitles.hollowhours.gun.empty": ("Empty click", "กระสุนหมด"),
}
ARMOR_PARTS = {"helmet": ("Helmet", "หมวก"), "chestplate": ("Vest", "เสื้อเกราะ"), "leggings": ("Pants", "กางเกง"), "boots": ("Boots", "รองเท้า")}
ARMOR_SETS = {"tactical": ("Tactical", "ยุทธวิธี"), "heavy": ("Heavy Soldier", "ทหารหนัก"), "hunter": ("Ghost Hunter", "นักล่าผี")}
for s, (se, st) in ARMOR_SETS.items():
    for k, (ke, kt) in ARMOR_PARTS.items():
        T["item.hollowhours.%s_%s" % (s, k)] = ("%s %s" % (se, ke), "%s%s" % (kt, st))


def shaped(name, pattern, key, count=1):
    k = {c: ({"item": v} if not v.startswith('#') else {"tag": v[1:]}) for c, v in key.items()}
    return name, {"type": "minecraft:crafting_shaped", "category": "equipment", "pattern": pattern, "key": k,
                  "result": {"item": "hollowhours:" + name, "count": count}}


RECIPES = [
    shaped("pistol", ["III", " RG", "  I"], {"I": "minecraft:iron_ingot", "R": "minecraft:redstone", "G": "minecraft:gunpowder"}),
    shaped("rifle", ["IIB", "TRG", "  I"], {"I": "minecraft:iron_ingot", "B": "minecraft:iron_block", "T": "#minecraft:planks", "R": "minecraft:redstone", "G": "minecraft:gunpowder"}),
    shaped("shotgun", ["III", "PRG", "P  "], {"I": "minecraft:iron_ingot", "P": "#minecraft:planks", "R": "minecraft:redstone", "G": "minecraft:gunpowder"}),
    shaped("sniper", ["  S", "IBI", "PRD"], {"S": "minecraft:spyglass", "I": "minecraft:iron_ingot", "B": "minecraft:iron_block", "P": "#minecraft:planks", "R": "minecraft:redstone", "D": "minecraft:diamond"}),
    shaped("grenade", [" N ", "IGI", " I "], {"N": "minecraft:iron_nugget", "I": "minecraft:iron_ingot", "G": "minecraft:gunpowder"}, 2),
    shaped("pistol_ammo", ["C", "G", "N"], {"C": "minecraft:copper_ingot", "G": "minecraft:gunpowder", "N": "minecraft:iron_nugget"}, 16),
    shaped("rifle_ammo", ["CC", "GN"], {"C": "minecraft:copper_ingot", "G": "minecraft:gunpowder", "N": "minecraft:iron_nugget"}, 24),
    shaped("shotgun_shell", ["P", "G", "N"], {"P": "minecraft:paper", "G": "minecraft:gunpowder", "N": "minecraft:iron_nugget"}, 8),
    shaped("sniper_ammo", ["I", "G", "C"], {"I": "minecraft:iron_ingot", "G": "minecraft:gunpowder", "C": "minecraft:copper_ingot"}, 6),
    shaped("combat_knife", [" I", "S "], {"I": "minecraft:iron_ingot", "S": "minecraft:stick"}),
    shaped("katana", ["  I", " I ", "LS "], {"I": "minecraft:iron_ingot", "L": "minecraft:leather", "S": "minecraft:stick"}),
    shaped("battle_axe", ["II ", "IS ", " S "], {"I": "minecraft:iron_block", "S": "minecraft:stick"}),
    shaped("stun_baton", ["  R", " I ", "S  "], {"R": "minecraft:redstone_block", "I": "minecraft:iron_ingot", "S": "minecraft:stick"}),
    shaped("spear", ["  I", " S ", "S  "], {"I": "minecraft:iron_ingot", "S": "minecraft:stick"}),
]
ARMOR_MAT = {"tactical": ("minecraft:leather", "minecraft:iron_ingot"), "heavy": ("minecraft:iron_ingot", "minecraft:iron_block"),
             "hunter": ("minecraft:amethyst_shard", "minecraft:diamond")}
PATTERNS = {"helmet": ["MXM", "M M"], "chestplate": ["M M", "MXM", "MMM"], "leggings": ["MXM", "M M", "M M"], "boots": ["M M", "X X"]}
for s, (m, x) in ARMOR_MAT.items():
    for part, pat in PATTERNS.items():
        RECIPES.append(shaped("%s_%s" % (s, part), pat, {"M": m, "X": x}))

for lang, i in (("en_us", 0), ("th_th", 1)):
    p = os.path.join(L, lang + '.json')
    d = json.load(open(p), object_pairs_hook=collections.OrderedDict)
    for k, v in T.items():
        d[k] = v[i]
    json.dump(d, open(p, 'w'), ensure_ascii=False, indent=1)
for name, r in RECIPES:
    json.dump(r, open(os.path.join(REC, name + '.json'), 'w'), indent=2)
print('combat texts:', len(T), 'recipes:', len(RECIPES))
