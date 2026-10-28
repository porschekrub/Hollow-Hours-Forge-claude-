#!/usr/bin/env python3
"""en_us.json + th_th.json for Hollow Hours. Every key the code uses is listed here; the script fails if one is
missing a translation."""
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, "..", "hollow-hours")
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "hollowhours", "lang")
guide = json.load(open(os.path.join(HERE, "data", "guide_text_base.json")))

L = {}


def k(key, en, th):
    L[key] = (en, th)


def many(prefix, pairs):
    for i, (en, th) in enumerate(pairs):
        k(prefix + str(i), en, th)


# ----------------------------------------------------------------------------- names
GHOSTS = [("The Wailing Dweller", "ผีร่ำไห้ใต้เหมือง", "wail"), ("Sunken Widow", "เจ้าสาวจมน้ำ", "widow"), ("Ashen Screamer", "ผีเถ้ากรีดร้อง", "ash"),
          ("Smiling Void", "ผียิ้มในความมืด", "void"), ("The Antlered Hunger", "ผีหิวเขากวาง", "stag")]
STALKERS = [("The Lanky One", "ผอมสูง"), ("Pale Child", "เด็กซีด"), ("The Veil", "ผ้าคลุม")]
for i, (en, th, short) in enumerate(GHOSTS, 1):
    k("entity.hollowhours.ghost_%d" % i, en, th)
    k("item.hollowhours.ghost_%d_spawn_egg" % i, "Summon: " + en, "เรียก: " + th)
    k("hollowhours.ghost.short.%d" % i, short.capitalize(), th.replace("ผี", "") if i != 2 else "เจ้าสาว")
for i, (en, th) in enumerate(STALKERS, 1):
    k("entity.hollowhours.stalker_%d" % i, en, th)
    k("item.hollowhours.stalker_%d_spawn_egg" % i, "Summon: " + en, "เรียก: " + th)
k("entity.hollowhours.closet", "Hiding Closet", "ตู้ซ่อนตัว")
k("entity.hollowhours.hide_seat", "...", "...")
k("entity.hollowhours.boards", "Boards", "ไม้กั้น")
k("itemGroup.hollowhours", "Hollow Hours", "Hollow Hours ผีหลอน")
k("item.hollowhours.ghost_guide", "Ghost Guide", "คู่มือผีหลอน")
k("item.hollowhours.flashlight", "Flashlight", "ไฟฉาย")
k("item.hollowhours.battery", "Battery", "ถ่านไฟฉาย")
k("item.hollowhours.board_wood", "Board (Wood)", "ไม้กั้น (ไม้)")
k("item.hollowhours.board_stone", "Board (Stone)", "ไม้กั้น (หิน)")
k("item.hollowhours.board_iron", "Board (Iron)", "ไม้กั้น (เหล็ก)")
k("item.hollowhours.repair_wood", "Wood Repair Kit", "ชุดซ่อมไม้")
k("item.hollowhours.repair_iron", "Iron Repair Kit", "ชุดซ่อมเหล็ก")
k("item.hollowhours.repair_stone", "Stone Mortar", "ปูนซ่อมหิน")
k("item.hollowhours.repair_glass", "Glass Sealant", "ยาแนวกระจก")
k("item.hollowhours.closet_item", "Hiding Closet", "ตู้ซ่อนตัว")
k("item.hollowhours.ghost_guide.tip", "Right-click: who is awake, how to survive, talk to them...", "คลิกขวา: ผีตนไหนตื่นอยู่ วิธีเอาตัวรอด คุยกับมัน...")
k("item.hollowhours.flashlight.charge", "Battery: %s:%s", "แบตเหลือ: %s:%s")
k("item.hollowhours.flashlight.tip", "Right-click: on/off · Sneak + right-click: new battery", "คลิกขวา: เปิด/ปิด · ย่อ + คลิกขวา: เปลี่ยนถ่าน")
k("item.hollowhours.battery.tip", "5 minutes of light. Right-click to load a flashlight.", "ส่องได้ 5 นาที คลิกขวาเพื่อใส่ไฟฉาย")
k("item.hollowhours.board.tip", "Right-click a closed door or a window to nail it shut", "คลิกขวาที่ประตูที่ปิดอยู่หรือกระจกเพื่อตอกปิด")
k("item.hollowhours.board.tip.0", "Weakest: most ghosts can smash it", "อ่อนสุด: ผีส่วนใหญ่พังได้")
k("item.hollowhours.board.tip.1", "Strong: only stronger ghosts get through", "แข็ง: เฉพาะผีที่แรงพอถึงจะพังได้")
k("item.hollowhours.board.tip.2", "Strongest: only the most brutal ghosts", "แข็งที่สุด: เฉพาะผีตัวโหดที่สุด")
k("item.hollowhours.repair.tip.wood", "Fixes cracked wooden doors, trapdoors, gates and wooden boards", "ซ่อมประตูไม้ ประตูกล รั้ว และไม้กั้นไม้ที่ร้าว")
k("item.hollowhours.repair.tip.iron", "Fixes cracked iron doors, trapdoors and iron boards", "ซ่อมประตูเหล็ก ประตูกลเหล็ก และไม้กั้นเหล็กที่ร้าว")
k("item.hollowhours.repair.tip.stone", "Fixes cracked stone boards", "ซ่อมไม้กั้นหินที่ร้าว")
k("item.hollowhours.repair.tip.glass", "Seals cracked glass and panes", "ซ่อมกระจกที่ร้าว")
k("item.hollowhours.closet_item.tip", "Place it, then right-click it to hide inside. Hit it to pick it up.", "วางแล้วคลิกขวาเพื่อเข้าไปซ่อน ตีเพื่อเก็บคืน")

k("death.attack.hollowhours.ghost_hit", "%1$s was torn apart by something in the dark", "%1$s ถูกบางสิ่งในความมืดฉีกร่าง")
k("death.attack.hollowhours.ghost_hit.player", "%1$s was torn apart by %2$s", "%1$s ถูก %2$s ฉีกร่าง")
k("death.attack.hollowhours.ghost_kill", "%1$s was taken", "%1$s ถูกพาตัวไป")
k("death.attack.hollowhours.ghost_kill.player", "%1$s was taken by %2$s", "%1$s ถูก %2$s พาตัวไป")
k("death.attack.hollowhours.curse", "%1$s was consumed by a curse", "%1$s ถูกคำสาปกลืนกิน")
k("death.attack.hollowhours.curse.player", "%1$s was consumed by a curse", "%1$s ถูกคำสาปกลืนกิน")

# ----------------------------------------------------------------------------- whispers, fake chat, moods, events
many("hollowhours.whisper.", [
    ("§4Don't cry...", "§4อย่าร้องไห้..."), ("§c That crying... isn't yours ", "§c เสียงร้องไห้นั่น...ไม่ใช่ของคุณ "), ("§4It's smiling", "§4มันกำลังยิ้มอยู่"),
    ("§8§k|||§r §c It stopped crying §8§k|||", "§8§k|||§r §c มันหยุดร้องแล้ว §8§k|||"), ("§4Behind you", "§4ข้างหลัง"), ("§c Don't blink ", "§c อย่ากระพริบตา "),
    ("§4Turn around...", "§4หันหลังสิ..."), ("§c It's right there ", "§c มันอยู่ตรงนั้น "), ("§4Don't look at it", "§4อย่ามองมัน"), ("§c It has seen you", "§c มันเห็นคุณแล้ว"),
    ("§4Run...", "§4วิ่งสิ..."), ("§8§k||||§r §4It's behind you §8§k||||", "§8§k||||§r §4มันอยู่ข้างหลังคุณ §8§k||||"), ("§c Nowhere to hide ", "§c ไม่มีที่ให้ซ่อน "),
    ("§4Quiet...", "§4เงียบ ๆ ไว้..."), ("§c Don't turn around ", "§c อย่าหันไป "),
])
many("hollowhours.fakechat.", [("help", "ช่วยด้วย"), ("is anyone there?", "มีใครอยู่ไหม"), ("behind you", "ข้างหลังคุณ"),
                               ("why did you leave me down there", "ทำไมทิ้งฉันไว้ข้างล่างนั่น"), ("i can see you", "ฉันเห็นนายนะ")])
many("hollowhours.ownvoice.", [("...where am I?", "...ฉันอยู่ที่ไหน"), ("let me in", "ให้ฉันเข้าไป"), ("it's me. open the door.", "ฉันเอง เปิดประตูหน่อย"),
                               ("I'm right behind you", "ฉันอยู่ข้างหลังนาย")])
k("hollowhours.mood.calm", "§8The night feels quiet... for now.", "§8คืนนี้เงียบผิดปกติ... ตอนนี้")
k("hollowhours.mood.restless", "§4Something is restless tonight.", "§4คืนนี้มีบางอย่างกระวนกระวาย")
k("hollowhours.mood.haunted", "§4§lThe dead are awake tonight.", "§4§lคืนนี้คนตายไม่ยอมหลับ")
k("hollowhours.mood.normal", "", "")
k("hollowhours.event.blackout", "§8...who turned off the lights?", "§8...ใครปิดไฟ?")
k("hollowhours.event.cold", "§bYour breath fogs. It's suddenly so cold.", "§bลมหายใจเป็นไอ จู่ ๆ ก็หนาวจัด")
many("hollowhours.glitch.", [("§4§lRUN", "§4§lวิ่ง"), ("§4§lBEHIND YOU", "§4§lอยู่ข้างหลังคุณ"), ("§4§lIT FOUND YOU", "§4§lมันเจอคุณแล้ว"), ("§4§lSTOP CRYING", "§4§lหยุดร้องไห้")])

# ----------------------------------------------------------------------------- chase, search, hiding, knocking, breach
many("hollowhours.chase.lost.", [("§8...it can't see you... don't make a sound", "§8...มันมองไม่เห็นคุณแล้ว... อย่าส่งเสียง"),
                                 ("§8...quiet... it's listening", "§8...เงียบไว้... มันกำลังฟัง"), ("§8...it lost you...", "§8...มันคลาดกับคุณ...")])
many("hollowhours.search.sniff.", [("§8...sniff... sniff...", "§8...ฟึด... ฟึด..."), ("§8...I can smell you...", "§8...ฉันได้กลิ่นเธอ..."),
                                   ("§8...come out...", "§8...ออกมาเถอะ..."), ("§8...you're near...", "§8...เธออยู่แถวนี้...")])
many("hollowhours.search.found.", [("§4§lFOUND YOU", "§4§lเจอแล้ว"), ("§4§lthere you are...", "§4§lเจอตัวแล้ว..."), ("§4§lyou can't hide from me", "§4§lซ่อนไม่พ้นหรอก")])
many("hollowhours.search.giveup.", [("§8...it can't find you...", "§8...มันหาคุณไม่เจอ..."), ("§8...it's gone... §4this time", "§8...มันจากไปแล้ว... §4คราวนี้"),
                                    ("§8...the footsteps slowly fade...", "§8...เสียงฝีเท้าค่อย ๆ เงียบหายไป...")])
k("hollowhours.search.heard", "§4§lIt heard you!", "§4§lมันได้ยินคุณ!")
k("hollowhours.search.seen", "§4§lIt sees you!", "§4§lมันเห็นคุณแล้ว!")
k("hollowhours.hide.heard", "§4§lIt heard you!", "§4§lมันได้ยินเสียงคุณ!")
k("hollowhours.hide.closet", "§7Hiding in the closet... §8stay still (sneak to get out)", "§7ซ่อนในตู้... §8อยู่นิ่ง ๆ (กดย่อเพื่อออก)")
k("hollowhours.hide.bed", "§7Under the bed... §8stay still (sneak to get out)", "§7มุดอยู่ใต้เตียง... §8อยู่นิ่ง ๆ (กดย่อเพื่อออก)")
k("hollowhours.hide.gasp", "§c§l...GASP!", "§c§l...เฮือก!")
k("hollowhours.hide.light", "§c§lTurn off the flashlight! §r§7the light leaks out", "§c§lปิดไฟฉาย! §r§7แสงรั่วออกไปข้างนอก")
k("hollowhours.hide.idle", "§8Hiding... (sneak to get out)", "§8ซ่อนอยู่... (กดย่อเพื่อออก)")
k("hollowhours.hud.closet", "breath", "ลมหายใจ")
k("hollowhours.hud.bed", "breath", "ลมหายใจ")
many("hollowhours.knock.door.", [("§8knock... knock...", "§8ก๊อก... ก๊อก..."), ("§8...let me in...", "§8...ให้ฉันเข้าไป..."), ("§8...I know you're in there...", "§8...ฉันรู้ว่าเธออยู่ข้างใน..."),
                                 ("§8...open the door...", "§8...เปิดประตูหน่อย..."), ("§8...it's cold out here...", "§8...ข้างนอกหนาวจัง..."), ("§4don't open it", "§4อย่าเปิด"),
                                 ("§8...knock knock knock knock...", "§8...ก๊อก ก๊อก ก๊อก ก๊อก..."), ("§8...hello?", "§8...สวัสดี?")])
many("hollowhours.knock.window.", [("§8tap... tap...", "§8ก๊อก... ก๊อก... (ที่กระจก)"), ("§8...I can see you through the glass...", "§8...ฉันเห็นเธอผ่านกระจก..."),
                                   ("§4Something is at the window", "§4มีบางอย่างอยู่ที่หน้าต่าง"), ("§8...look at me...", "§8...มองฉันสิ...")])
k("hollowhours.knock.seen", "§4§lIt sees you!", "§4§lมันเห็นคุณแล้ว!")
k("hollowhours.knock.breakin", "§4§lIt's breaking in!", "§4§lมันจะพังเข้ามา!")
many("hollowhours.breach.wall.", [("§4It can't break the door... §cso it's smashing the wall!", "§4มันพังประตูไม่ได้... §cมันหันไปทุบผนังแทน!"),
                                  ("§4You hear the wall being smashed...", "§4ได้ยินเสียงทุบผนัง...")])
many("hollowhours.breach.coming.", [("§8...it's looking for a way in...", "§8...มันกำลังหาทางเข้ามา..."), ("§8...it knows the way...", "§8...มันรู้ทาง..."),
                                    ("§4...it won't give up...", "§4...มันไม่ยอมแพ้...")])
many("hollowhours.breach.bash.", [("§4It's breaking in", "§4มันกำลังพังเข้ามา"), ("§c...BANG!", "§c...ปัง!"), ("§4Run!", "§4วิ่ง!"), ("§8...the door won't hold...", "§8...ประตูจะไม่ไหวแล้ว...")])
many("hollowhours.breach.inside.", [("§4§lIt's inside", "§4§lมันเข้ามาแล้ว"), ("§4§lIt's in the house", "§4§lมันอยู่ในบ้าน"), ("§c§lRUN!", "§c§lวิ่ง!")])
many("hollowhours.breach.stuck.", [("§8...it's stuck...", "§8...มันตัวติด..."), ("§4It's struggling free", "§4มันกำลังดิ้นออกมา"), ("§8...it's smashing the window frame...", "§8...มันทุบกรอบหน้าต่าง...")])
k("hollowhours.breach.topple", "§4It's smashing the pillar you're standing on!", "§4มันกำลังทุบเสาที่คุณยืนอยู่!")
k("hollowhours.breach.giveup", "§8...it couldn't get in... §4tonight", "§8...มันพังเข้ามาไม่ได้... §4คืนนี้")

# ----------------------------------------------------------------------------- boards / repair / flashlight / closet
k("hollowhours.boards.hint", "§7Boards nail shut doors and windows §8(right-click a door / glass)", "§7ไม้กั้นใช้ตอกปิดประตูหรือกระจก §8(คลิกขวาที่ประตู/กระจก)")
k("hollowhours.boards.nailed", "§7This door is boarded up §8(sneak + right-click with an empty hand = pry a board off)", "§7ประตูถูกตอกไม้กั้นไว้ §8(ย่อ + คลิกขวามือเปล่า = งัดไม้ออก)")
k("hollowhours.boards.closer", "§7Get closer", "§7เข้าไปใกล้อีกหน่อย")
k("hollowhours.boards.closefirst", "§7Close the door first, then nail the boards", "§7ปิดประตูก่อน แล้วค่อยตอกไม้กั้น")
k("hollowhours.boards.full", "§7This door already has 4 boards", "§7ประตูบานนี้ตอกไม้กั้นครบ 4 แผ่นแล้ว")
k("hollowhours.boards.glassdone", "§7This glass is already boarded", "§7กระจกตรงนี้ตอกไม้ไว้แล้ว")
k("hollowhours.boards.pry", "§7Pried off one board §8(%s left)", "§7งัดไม้กั้นออก 1 แผ่น §8(เหลือ %s)")
k("hollowhours.boards.pry.all", "§7All boards pried off", "§7งัดไม้กั้นออกหมดแล้ว")
k("hollowhours.boards.pry.glass", "§7Pried the boards off the window §8(they broke)", "§7งัดไม้ที่กระจกออกแล้ว §8(ไม้หัก)")
k("hollowhours.boards.done.door", "§aNailed a %s board §7(%s/4)", "§aตอกไม้กั้น%sแล้ว §7(%s/4 แผ่น)")
k("hollowhours.boards.done.glass", "§aBoarded up %s window pane(s)", "§aตอกไม้ปิดกระจกแล้ว %s บาน")
k("hollowhours.boards.mat.wood", "wood", "ไม้")
k("hollowhours.boards.mat.stone", "stone", "หิน")
k("hollowhours.boards.mat.iron", "iron", "เหล็ก")
k("hollowhours.repair.none", "§7Nothing to repair here", "§7ตรงนี้ไม่มีอะไรต้องซ่อม")
k("hollowhours.repair.wrong", "§7Wrong kit: use %s", "§7ใช้ของซ่อมผิดชนิด: ต้องใช้ %s")
k("hollowhours.repair.done.door", "§aRepaired", "§aซ่อมเรียบร้อย")
k("hollowhours.repair.done.glass", "§aGlass sealed", "§aยาแนวกระจกเรียบร้อย")
k("hollowhours.repair.crumble", "§7The cracked block crumbles to nothing", "§7ของที่ร้าวแตกสลายไป")
k("item.hollowhours.repair_wood", "Wood Repair Kit", "ชุดซ่อมไม้")
k("hollowhours.flash.dead", "§cThe flashlight is dead §8(sneak + right-click: new battery)", "§cถ่านหมด §8(ย่อ + คลิกขวา: เปลี่ยนถ่าน)")
k("hollowhours.flash.full", "§7The battery is still full", "§7ถ่านยังเต็มอยู่")
k("hollowhours.flash.nobattery", "§7No batteries", "§7ไม่มีถ่านสำรอง")
k("hollowhours.flash.reloaded", "§aNew battery in", "§aใส่ถ่านใหม่แล้ว")
k("hollowhours.closet.noroom", "§7Can't place it here (needs 2 blocks of free height)", "§7วางตรงนี้ไม่ได้ (ต้องมีที่ว่างสูง 2 ช่อง)")
k("hollowhours.sprint.ready", "§7You got your breath back", "§7หายเหนื่อยแล้ว")
k("hollowhours.sprint.tired", "§7Too tired to sprint", "§7เหนื่อยเกินกว่าจะวิ่งสุดแรง")

# ----------------------------------------------------------------------------- soul
k("hollowhours.soul.bar", "§7Soul %s §f%s%%", "§7วิญญาณ %s §f%s%%")
k("hollowhours.soul.drained", "§5Your soul is drained", "§5วิญญาณถูกสูบ")
k("hollowhours.soul.healed", "§bYour soul recovers", "§bวิญญาณฟื้นคืน")
k("hollowhours.soul.slept", "§bA night of sleep mends your soul", "§bนอนหลับทั้งคืน วิญญาณฟื้นคืน")
k("hollowhours.soul.tier.1", "§dYour soul feels thin. You get hurt more easily.", "§dวิญญาณของคุณบางลง คุณเจ็บง่ายขึ้น")
k("hollowhours.soul.tier.2", "§5Your soul is hollow. Your body grows weak and slow.", "§5วิญญาณของคุณกลวง ร่างกายอ่อนแรงและเชื่องช้า")
k("hollowhours.soul.tier.3", "§4§lYour soul is almost gone. Everything you hold is rotting.", "§4§lวิญญาณของคุณใกล้หมดแล้ว ทุกอย่างที่ถือกำลังผุพัง")
k("hollowhours.soul.tiername.0", "normal", "ปกติ")
k("hollowhours.soul.tiername.1", "shaken", "สั่นคลอน")
k("hollowhours.soul.tiername.2", "hollow", "กลวง")
k("hollowhours.soul.tiername.3", "almost gone", "ใกล้สลาย")
k("hollowhours.soul.gone.title", "§4§lYOUR SOUL IS EMPTY", "§4§lวิญญาณของคุณว่างเปล่า")
k("hollowhours.soul.gone.sub", "§8it's coming for the rest", "§8มันกำลังมาเอาส่วนที่เหลือ")
k("hollowhours.soul.rot", "§8Your gear is rotting...", "§8ของของคุณกำลังผุพัง...")
k("hollowhours.soul.rot.broke", "§4Something you held rotted away", "§4ของที่คุณถือผุพังจนแตก")

# ----------------------------------------------------------------------------- curses
CURSES = {
    "soul": ("Soul Leech", "ดูดพลังชีวิต", "§4A little more of your soul is drained...", "§4วิญญาณของคุณถูกสูบไปอีกนิด..."),
    "blood": ("Blood Drinker", "ดื่มเลือด", "§4The cursed thing drinks your blood", "§4ของสาปดื่มเลือดของคุณ"),
    "starve": ("Hunger", "หิวโหย", "§4Hunger gnaws you from the inside", "§4ความหิวกัดกินจากข้างใน"),
    "blind": ("Blinded Eyes", "ดวงตามืดบอด", "§4The whole world goes dark", "§4โลกทั้งใบมืดลง"),
    "rot": ("Rotting Flesh", "เนื้อเน่า", "§4Your flesh begins to rot", "§4เนื้อของคุณเริ่มเน่า"),
    "drown": ("Cursed Water", "จมน้ำสาป", "§4Cursed water floods your lungs", "§4น้ำสาปท่วมปอดคุณ"),
    "nightmare": ("Nightmare", "ฝันร้าย", "§4It sees you", "§4มันเห็นคุณแล้ว"),
    "burn": ("Hellfire", "ไฟนรก", "§4Hellfire crawls up your body", "§4ไฟนรกลามขึ้นตัว"),
    "lightning": ("Judgement", "ฟ้าลงทัณฑ์", "§4The sky punishes you", "§4ฟ้าลงโทษ"),
    "recoil": ("Recoil", "แรงสะท้อน", "§4The curse throws you back", "§4แรงสาปผลักคุณกระเด็น"),
    "xp": ("Forgetting", "ลืมเลือน", "§4Your memories are drained away", "§4ความทรงจำของคุณถูกดูดไป"),
    "wither": ("Death's Touch", "สัมผัสความตาย", "§4Death touches you", "§4ความตายสัมผัสคุณ"),
    "thief": ("Dark Hands", "มือมืด", "§4Something slips out of your hands", "§4บางอย่างหลุดมือคุณไป"),
    "turn": ("Twisted Neck", "บิดคอ", "§4Someone turns your head", "§4ใครบางคนหมุนคอคุณ"),
    "freeze": ("Grave Cold", "หนาวจากหลุมศพ", "§4The cold of the grave", "§4ความหนาวจากหลุมศพ"),
    "stun": ("Stiff Body", "ร่างแข็งทื่อ", "§4Your body goes stiff", "§4ร่างกายแข็งทื่อ"),
    "sky": ("Pulled Skyward", "ดึงขึ้นฟ้า", "§4The curse pulls you into the sky", "§4แรงสาปดึงคุณขึ้นฟ้า"),
    "call": ("The Calling", "ปลุกผี", "§4Using it... woke something up", "§4การใช้มัน...ปลุกบางสิ่งขึ้นมา"),
    "foodrot": ("Rotten Food", "อาหารเน่า", "§4Your food rots", "§4อาหารของคุณเน่าเปื่อย"),
    "summon": ("The Dead Rise", "คนตายตื่น", "§4The dead awaken", "§4คนตายตื่นขึ้น"),
    "strip": ("Stripped", "ถอดเกราะ", "§4Your armor falls off you", "§4เกราะหลุดจากตัวคุณ"),
    "scramble": ("Shaking Hands", "มือสั่นสลับของ", "§4Your hands shake, your items swap places", "§4มือของคุณสั่น ของในมือสลับที่"),
    "bleed": ("Endless Bleeding", "เลือดไหลไม่หยุด", "§4A wound opens... the bleeding won't stop", "§4แผลเปิดออก... เลือดไหลไม่หยุด"),
    "weight": ("Carried Soul", "แบกวิญญาณ", "§4Something heavy clings to your back", "§4มีบางอย่างหนักอึ้งเกาะหลังคุณ"),
    "hollow": ("Soul Drain", "ดูดวิญญาณ", "§5§lYour soul is pulled out of your body...", "§5§lวิญญาณของคุณถูกดึงออกจากร่าง..."),
    "echo": ("Voices", "เสียงในหัว", "§4Whispers rise all around you...", "§4เสียงกระซิบดังขึ้นรอบตัว..."),
    "fragile": ("Brittle Armor", "เกราะผุ", "§4Your armor crumbles", "§4เกราะของคุณผุกร่อน"),
    "rust": ("Rust", "สนิมกิน", "§4Rust creeps over your things", "§4สนิมคืบคลานไปทั่วของของคุณ"),
    "ghostlight": ("Ghost Light", "แสงวิญญาณ", "§bYour body glows... it can see you through walls", "§bร่างของคุณเรืองแสง... มันเห็นคุณทะลุกำแพง"),
    "coldhands": ("Numb Hands", "มือชา", "§4Your hands go numb, you can't hold anything", "§4มือของคุณชาจนจับอะไรไม่ได้"),
    "eatlight": ("Light Eater", "กินแสง", "§8Darkness swallows the light around you...", "§8ความมืดกลืนกินแสงรอบตัว..."),
    "doom": ("Countdown", "นับถอยหลัง", "§4§lSomething is counting down...", "§4§lบางอย่างกำลังนับถอยหลัง..."),
    "pact": ("Blood Pact", "สัญญาเลือด", "§4Your body heals... your soul pays for it", "§4ร่างกายหายดี... แต่วิญญาณต้องจ่ายแทน"),
    "pursuit": ("Its Anger", "ผีโกรธ", "§4§lIt's angry!", "§4§lมันโกรธ!"),
    "worms": ("Burrowers", "หนอนชอนไช", "§4Something burrows out of the ground", "§4มีบางอย่างชอนไชออกมาจากพื้น"),
    "phantoms": ("Black Wings", "ปีกดำ", "§4Wings beat above your head...", "§4เสียงปีกกระพือเหนือหัว..."),
    "vex": ("Vengeful Spirits", "วิญญาณร้าย", "§4Spirits break out of it", "§4วิญญาณร้ายหลุดออกมาจากของ"),
    "web": ("Webs", "ใยแมงมุม", "§7Sticky webs wrap your legs", "§7ใยเหนียว ๆ พันขาคุณไว้"),
    "warp": ("Twisted Space", "บิดมิติ", "§5The world bends...", "§5โลกบิดเบี้ยว..."),
    "tremor": ("Tremor", "มือสั่น", "§4Your hands shake too hard to hold anything", "§4มือสั่นจนจับอะไรไม่อยู่"),
}
for key, (en, th, men, mth) in CURSES.items():
    k("hollowhours.curse." + key, en, th)
    k("hollowhours.curse.msg." + key, men, mth)
MARKS = [("Crying", "เสียงร่ำไห้", "§7Crying echoes inside your head", "§7เสียงร้องไห้ดังอยู่ในหัวคุณ"),
         ("Bride's Tears", "น้ำตาเจ้าสาว", "§3Ice-cold water pours out of your mouth", "§3น้ำเย็นเฉียบไหลออกมาจากปากคุณ"),
         ("Ashes", "เถ้าถ่าน", "§6Hot ash rains down on you", "§6เถ้าร้อน ๆ ร่วงลงบนตัวคุณ"),
         ("The Smile", "รอยยิ้ม", "§5Someone giggles right in your ear :)", "§5ใครบางคนหัวเราะคิกคักข้างหู :)"),
         ("Hunger", "ความหิวกระหาย", "§fA hunger that isn't yours...", "§fความหิวที่ไม่ใช่ของคุณ...")]
for i, (en, th, men, mth) in enumerate(MARKS, 1):
    k("hollowhours.mark.%d" % i, en, th)
    k("hollowhours.mark.msg.%d" % i, men, mth)
k("hollowhours.curse.mark", "✝ Cursed", "✝ ของต้องสาป")
k("hollowhours.curse.uses", "Uses left: %s/%s", "ใช้ได้อีก: %s/%s")
k("hollowhours.curse.name", "Curse: %s", "คำสาป: %s")
k("hollowhours.curse.ghostmark", "Mark of %s (%s)", "ตรา%s (%s)")
k("hollowhours.curse.shatter", "§4§lThe cursed thing shatters", "§4§lของต้องสาปแตกสลาย")
k("hollowhours.ask.noghost", "No ghost is awake in this world.", "ไม่มีผีตื่นอยู่ในโลกนี้")
k("hollowhours.ask.wait", "(wait 2 seconds before asking again)", "(รอ 2 วินาทีก่อนขอใหม่)")
k("hollowhours.ask.sealed", "§4§l✝ CURSED ✝", "§4§l✝ ของต้องสาป ✝")
k("hollowhours.ask.got", "The ghost gave you %s ×%s. It is cursed. (%s requests left)", "ผีให้ %s ×%s มา มันเป็นของต้องสาป (ขอได้อีก %s ครั้ง)")
k("hollowhours.ask.refused", "It refused. (%s requests left)", "มันไม่ให้ (ขอได้อีก %s ครั้ง)")

# ----------------------------------------------------------------------------- talk
k("hollowhours.talk.noghost", "No ghost like that is awake right now. You can only talk to a ghost while it is here.", "ตอนนี้ไม่มีผีตนนั้นตื่นอยู่ คุยได้เฉพาะตอนที่ผีอยู่ในโลกเท่านั้น")
k("hollowhours.talk.wait", "(wait 2 seconds before talking again)", "(รอ 2 วินาทีก่อนพูดต่อ)")
k("hollowhours.talk.took", "The ghost takes your %s.", "ผีรับ%sของคุณไป")
k("hollowhours.talk.angry", "§4§lIT IS ENRAGED", "§4§lมันโมโหแล้ว")
k("hollowhours.speech.unknown", "???", "???")

# ----------------------------------------------------------------------------- betrayal + death
OFFER = [
    ("...shh... tell me where your friend is... and I might let you go...", "...ชู่... บอกมาสิว่าเพื่อนเธออยู่ไหน... แล้วฉันอาจจะปล่อยเธอไป..."),
    ("Who did you leave behind, my love? Tell me... and you can go home.", "เธอทิ้งใครไว้ข้างหลังล่ะที่รัก? บอกฉันสิ... แล้วเธอจะได้กลับบ้าน"),
    ("TELL ME WHERE YOUR FRIEND IS! TELL ME AND I'LL LET YOU BREATHE!", "บอกมาว่าเพื่อนแกอยู่ไหน! บอกมาแล้วฉันจะให้แกหายใจต่อ!"),
    ("Let's play a game :) pick a friend for me... and you can leave :)", "มาเล่นเกมกันไหม :) เลือกเพื่อนมาให้ฉันสักคน... แล้วเธอไปได้เลย :)"),
    ("...which one... tastes better... you... or them...", "...คนไหน... อร่อยกว่า... เธอ... หรือมัน..."),
]
for i, (en, th) in enumerate(OFFER, 1):
    k("hollowhours.betray.offer.%d" % i, en, th)
k("hollowhours.betray.title", "Sell out a friend?", "ขายเพื่อน?")
k("hollowhours.betray.hint", "Choose a friend for it to hunt... or refuse and die.", "เลือกเพื่อนให้มันไปล่า... หรือปฏิเสธแล้วตาย")
k("hollowhours.betray.refuse", "I won't sell my friend", "ไม่ขายเพื่อน")
many("hollowhours.betray.refused.", [("Loyal... how sweet. Then you die.", "ซื่อสัตย์... น่ารักจัง งั้นก็ตายซะ"),
                                     ("Wrong answer.", "ตอบผิด"), ("Then I'll take you instead.", "งั้นฉันเอาเธอแทนก็ได้")])
k("hollowhours.betray.timeout", "Too slow.", "ช้าไป")
k("hollowhours.betray.gone", "...that one isn't here anymore. You'll do.", "...คนนั้นไม่อยู่แล้ว เอาเธอนี่แหละ")
k("hollowhours.betray.lied", "You lied to me.", "เธอโกหกฉัน")
k("hollowhours.betray.sold", "...good choice. Run.", "...เลือกดี วิ่งไปซะ")
k("hollowhours.betray.victim.title", "§4§lYOU WERE SOLD OUT", "§4§lคุณถูกเพื่อนขาย")
k("hollowhours.betray.victim.sub", "§c%s told it where you are", "§c%s บอกมันว่าคุณอยู่ที่ไหน")
many("hollowhours.death.big.", [("§4§lIt got you", "§4§lมันได้คุณแล้ว"), ("§4§lI told you not to turn your back", "§4§lบอกแล้วว่าอย่าหันหลัง"),
                                ("§4§lYou didn't make it", "§4§lคุณไม่รอด")])
many("hollowhours.death.small.", [("§4§lIt isn't done with you", "§4§lมันยังไม่หยุด"), ("§c§lIt's waiting right there", "§c§lมันรออยู่ตรงนั้น")])
k("hollowhours.fled.title", "§4§lYou can't leave", "§4§lคุณหนีไม่ได้")
k("hollowhours.fled.sub", "§8it waited for you", "§8มันรอคุณอยู่")

# ----------------------------------------------------------------------------- guide
k("hollowhours.guide.title", "§4§lGHOST GUIDE", "§4§lคู่มือผีหลอน")
k("hollowhours.guide.given", "You found a stained book in your pocket... (Ghost Guide)", "คุณเจอหนังสือเปื้อนเลือดในกระเป๋า... (คู่มือผีหลอน)")
for key, en, th in (("menu", "Status", "สถานะ"), ("talk", "Talk to a ghost", "คุยกับผี"), ("ask", "Ask for an item", "ขอของจากผี"),
                    ("how", "Talking & deals", "วิธีคุยและต่อรอง"), ("house", "Doors & boards", "ประตูและไม้กั้น"), ("hide", "Running & hiding", "หนีและซ่อน"),
                    ("soul", "Light, soul, curses", "ไฟฉาย วิญญาณ คำสาป")):
    k("hollowhours.guide.tab." + key, en, th)
many("hollowhours.guide.epigraph.", [
    ("\"Don't read this out loud... it can hear.\"", "\"อย่าอ่านออกเสียง... มันได้ยิน\""), ("\"Pages are missing. Someone tore them out.\"", "\"หน้าที่หายไป มีคนฉีกออกไปเอง\""),
    ("\"If you hear your name called, don't answer.\"", "\"ถ้าได้ยินเสียงเรียกชื่อ อย่าขานรับ\""), ("\"This book belonged to whoever lived here before you.\"", "\"เล่มนี้เคยเป็นของคนที่อยู่ที่นี่ก่อนคุณ\""),
    ("\"It can read this book too.\"", "\"มันอ่านหนังสือเล่มนี้ได้เหมือนกัน\""), ("\"Count to five... and don't look.\"", "\"นับให้ครบห้า... แล้วอย่าหันไปมอง\""),
    ("\"If you hear knocking, don't open the door.\"", "\"ได้ยินเสียงเคาะ อย่าเปิดประตู\""), ("\"The ink is still wet... but nobody wrote it.\"", "\"หมึกยังไม่แห้ง... ทั้งที่ไม่มีใครเขียน\""),
    ("\"There are bloody fingerprints that aren't yours.\"", "\"มีรอยนิ้วมือเปื้อนเลือดที่ไม่ใช่ของคุณ\""),
])
k("hollowhours.guide.soul", "Soul: %s %s%% (%s)", "วิญญาณ: %s %s%% (%s)")
k("hollowhours.guide.awake", "§4● %s §0— %s", "§4● %s §0— %s")
k("hollowhours.guide.blocks", "%s blocks away", "ห่าง %s บล็อก")
k("hollowhours.guide.otherdim", "in another dimension", "อยู่อีกมิติ")
k("hollowhours.guide.noneawake", "Ghosts awake: none... for now", "ผีที่ตื่นอยู่: ไม่มี... ตอนนี้")
k("hollowhours.guide.flash", "Flashlight: %s left · spare batteries: %s", "ไฟฉาย: เหลือ %s · ถ่านสำรอง %s ก้อน")
k("hollowhours.guide.flash.on", "Flashlight: %s left (ON) · spare batteries: %s", "ไฟฉาย: เหลือ %s (เปิดอยู่) · ถ่านสำรอง %s ก้อน")
k("hollowhours.guide.flash.none", "Flashlight: none · spare batteries: %s", "ไฟฉาย: ไม่มี · ถ่านสำรอง %s ก้อน")
k("hollowhours.guide.menu.footer", "Five ghosts hunt in this world. You can only talk to one while it is awake. In chat you can also start a message with its short name: wail, widow, ash, void, stag.",
  "ผีหลอน 5 ตนล่าอยู่ในโลกนี้ คุยกับมันได้เฉพาะตอนที่มันตื่นอยู่ ในแชตก็คุยได้ โดยขึ้นต้นด้วยชื่อย่อ: wail, widow, ash, void, stag")
k("hollowhours.guide.type", "Type here", "พิมพ์ตรงนี้")
k("hollowhours.guide.send", "Say", "พูด")
k("hollowhours.guide.talk.hint", "please leave me alone...", "ขอร้อง อย่าตามฉันเลย...")
k("hollowhours.guide.ask.hint", "give me 5 diamonds", "ขอเพชร 5 เม็ด")
k("hollowhours.guide.talk.info", "Talking to: %s\nType anything, in Thai or English. Ask it questions (where are you? how did you die?), beg, offer something, or insult it... if you dare.",
  "กำลังคุยกับ: %s\nพิมพ์อะไรก็ได้ ภาษาไทยหรืออังกฤษ ถามมันตรง ๆ (อยู่ไหน? ตายยังไง?) อ้อนวอน เสนอของ หรือด่ามัน... ถ้ากล้า")
k("hollowhours.guide.ask.info", "Ask the nearest ghost for any item, e.g. \"give me 5 diamonds\". Requests left with this ghost: %s/%s.\nHalf the time it gives it to you... cursed. And every request makes the NEXT ghost stronger.",
  "ขอของอะไรก็ได้จากผีที่อยู่ใกล้ที่สุด เช่น \"ขอเพชร 5 เม็ด\" ขอได้อีก %s/%s ครั้ง\nครึ่งหนึ่งมันจะให้... แต่เป็นของต้องสาป และทุกครั้งที่ขอ ผีตัวถัดไปจะแรงขึ้น")
k("hollowhours.guide.talkto", "Talk to %s", "คุยกับ %s")
k("hollowhours.guide.isawake", "§4● Awake right now §0(%s)", "§4● ตื่นอยู่ตอนนี้ §0(%s)")
k("hollowhours.guide.asleep", "○ Asleep... you can't talk to it until it comes", "○ หลับอยู่... คุยไม่ได้จนกว่ามันจะมา")


def paper(s):
    """book colours: the page is light paper, so bright colours become dark ones"""
    s = s.replace("§l§c", "§4§l").replace("§e", "§4").replace("§7", "§0").replace("§8", "§8").replace("§f", "§0").replace("§b", "§3").replace("§c", "§4")
    s = s.replace("•", "▸")
    return s


JAVA_HIDE_EN = "\n".join(l for l in guide["en"]["hide"].split("\n")) if False else None


def guide_pages():
    for lang in ("en", "th"):
        pass
    sprint_en = ("§l§cAll-out sprint§r\n• While running, tap sneak twice quickly = sprint much faster (tap twice again = stop).\n"
                 "• The stamina bar lasts 10 seconds, then refills in 15.\n• Run it empty and you're exhausted for a moment.\n• Sprinting is LOUD: ghosts hear it from far away.\n\n")
    sprint_th = guide["th"]["hide"].split("§l§cผีต้องเห็นหรือได้ยินคุณ§r")[0]
    how_en = guide["en"]["how"].replace("A microphone can't be used by Minecraft add-ons. Your keyboard's voice typing works in the text box.",
                                        "You can also ask it straight questions: where are you? how did you die? will you kill me? what time is it? can you see me?")
    how_th = guide["th"]["how"].replace("• แอดออนใช้ไมโครโฟนของ Minecraft ไม่ได้ แต่ใช้การพิมพ์ด้วยเสียงของคีย์บอร์ดในช่องข้อความได้\n", "")
    extra_en = ("\n\n§l§cWhen it catches you§r\n• It grabs you by the throat and screams in your face.\n"
                "• Sometimes it offers you a deal: tell it where a friend is and it might let you go. You get 10 seconds.\n"
                "• Sell a friend and it hunts them instead. Refuse, or run out of time... and it kills you.")
    extra_th = ("\n\n§l§cเมื่อมันจับคุณได้§r\n• มันจะบีบคอยกคุณขึ้นแล้วกรีดร้องใส่หน้า\n"
                "• บางครั้งมันจะยื่นข้อเสนอ: บอกมาว่าเพื่อนอยู่ไหน แล้วมันอาจจะปล่อยคุณไป มีเวลา 10 วินาที\n"
                "• ถ้าขายเพื่อน มันจะไปล่าเพื่อนแทน ถ้าปฏิเสธหรือหมดเวลา... มันจะฆ่าคุณ")
    house_en = re.sub(r"• Copper kit:[^\n]*\n", "", guide["en"]["house"]).replace("wood (weakest) < copper < iron (strongest)", "wood (weakest) < iron (strongest)")
    house_th = re.sub(r"• ชุดซ่อมทองแดง:[^\n]*\n", "", guide["th"]["house"]).replace("ไม้ (อ่อนสุด) < ทองแดง < เหล็ก (แข็งสุด)", "ไม้ (อ่อนสุด) < เหล็ก (แข็งสุด)")
    k("hollowhours.guide.page.how", paper(how_en + extra_en), paper(how_th + extra_th))
    k("hollowhours.guide.page.house", paper(house_en), paper(house_th))
    k("hollowhours.guide.page.hide", paper(sprint_en + guide["en"]["hide"]), paper(guide["th"]["hide"]))
    soul_en = ("§l§cFlashlight§r\n• Craft: glass top middle · iron ingots left, right and bottom · redstone torch in the centre.\n"
               "• Battery: iron nugget / redstone / copper ingot stacked vertically.\n• Right-click = on/off · sneak + right-click = new battery.\n"
               "• One battery lights for §e5 minutes§r (only while on). The bar under the icon is the battery.\n"
               "• Low battery makes it flicker, and so does a ghost close by.\n• Careful: ghosts see the beam from much further away. Turn it off before you hide.\n\n"
               "§l§cSoul§r\n• Cursed items slowly drain your soul, while you carry them and every time their curse fires.\n"
               "• §dBelow 70%§r: you take 25% more damage · you get hungry.\n• §5Below 40%§r: 60% more damage · slow · weak · very hungry.\n"
               "• §4Below 15%§r: double damage · what you hold and wear rots apart.\n• When your soul is empty... it comes for the rest.\n"
               "• It recovers: slowly on its own (if you carry nothing cursed) · golden apple · sleeping through the night +35 · respawning sets it to at least 40.\n\n"
               "§l§cCursed items§r\n• Better than normal ones, but every use triggers their curse.\n• Each item has its own curse (see its tooltip) and the mark of the ghost who gave it.\n"
               "• The better the item, the stronger the curse.\n• Anything crafted from a cursed item is cursed too.")
    soul_th = ("§l§cไฟฉาย§r\n• คราฟไฟฉาย: กระจกช่องบนกลาง · เหล็กแท่งซ้าย-ขวา-ล่าง · คบเพลิงเรดสโตนตรงกลาง\n• คราฟถ่าน: เศษเหล็ก / เรดสโตน / ทองแดงแท่ง เรียงแนวตั้ง\n"
               "• คลิกขวา = เปิด/ปิด · ย่อ + คลิกขวา = เปลี่ยนถ่าน\n• ถ่าน 1 ก้อนส่องได้ §e5 นาที§r (นับเฉพาะตอนเปิด) แถบใต้ไอคอนคือถ่านที่เหลือ\n"
               "• ถ่านใกล้หมดไฟจะกะพริบ และถ้าผีอยู่ใกล้ ไฟจะกะพริบวูบวาบ\n• ระวัง: ผีเห็นแสงไฟฉายได้ไกลกว่าปกติ ถ้าจะซ่อนตัวให้ปิดไฟก่อน\n\n"
               "§l§cวิญญาณ§r\n• ของต้องสาปค่อย ๆ ดูดวิญญาณ ทั้งตอนพกไว้ และทุกครั้งที่คำสาปทำงาน\n• §dต่ำกว่า 70%§r: โดนตีเจ็บขึ้น 25% · หิวง่าย\n"
               "• §5ต่ำกว่า 40%§r: เจ็บขึ้น 60% · เดินช้า · อ่อนแรง · หิวเร็วมาก\n• §4ต่ำกว่า 15%§r: เจ็บเป็น 2 เท่า · ของในมือและชุดเกราะผุพังจนแตก\n"
               "• วิญญาณหมดเมื่อไหร่... มันจะมาเอาส่วนที่เหลือ\n• ฟื้นได้: รอเฉย ๆ (ถ้าไม่ได้พกของต้องสาป) · แอปเปิลทอง · นอนหลับจนเช้า +35 · เกิดใหม่ได้อย่างน้อย 40\n\n"
               "§l§cของต้องสาป§r\n• ดีกว่าของธรรมดา แต่ทุกครั้งที่ใช้ คำสาปของมันจะทำงาน\n• ของแต่ละอย่างมีคำสาปของตัวเอง (ดูในคำอธิบายไอเทม) และมีตราของผีที่มอบให้\n"
               "• ของยิ่งดี คำสาปยิ่งแรง\n• ของที่คราฟจากของต้องสาปจะต้องสาปไปด้วย")
    k("hollowhours.guide.page.soul", paper(soul_en), paper(soul_th))
    stories_en = ["I dug too deep in the mine... heard crying, and then the rock collapsed.", "I waited at the lake in white. He never came. The water did.",
                  "THE DOOR WAS LOCKED FROM THE OUTSIDE! I SCREAMED UNTIL THE ROOF FELL!", "I'm the one who's always been behind you :) you just never turned around.",
                  "...forest. ...winter. ...hungry. ...ate. ...still hungry."]
    stories_th = ["ฉันขุดลึกเกินไปในเหมือง... ได้ยินเสียงร้องไห้ แล้วหินก็ถล่มลงมา", "ฉันรออยู่ริมทะเลสาบในชุดขาว เขาไม่เคยมา มีแต่น้ำที่มา",
                  "ประตูถูกล็อกจากข้างนอก! ฉันกรีดร้องจนหลังคาถล่ม!", "ฉันคือคนที่อยู่ข้างหลังเธอมาตลอด :) แค่เธอไม่เคยหันมามอง",
                  "...ป่า. ...หน้าหนาว. ...หิว. ...กิน. ...ยังหิว."]
    for i in range(1, 6):
        en = guide["en"]["pages"][str(i)]
        th = guide["th"]["pages"][str(i)]
        en = "§4§lIts story§r\n\"" + stories_en[i - 1] + "\"\n\n" + en
        th = "§4§lเรื่องของมัน§r\n\"" + stories_th[i - 1] + "\"\n\n" + th
        en += "\n\n§4§lWhen it kills§r\n" + ["It holds you by the throat and stabs, slams you into the floor, or turns your head until it snaps.",
                                            "It drowns you on the floor, crushes you in a wedding embrace, or chokes you with its hair.",
                                            "It beats your face in, burns you in its arms, or crushes your skull.",
                                            "Four hands pull you apart, its grin opens and bites, or it turns you upside down.",
                                            "It eats you alive, runs you through with its antlers, or stamps on you."][i - 1]
        th += "\n\n§4§lเวลามันฆ่า§r\n" + ["บีบคอแล้วแทง กระแทกคุณลงพื้น หรือบิดคอจนหัก",
                                          "กดคุณจมน้ำกับพื้น กอดจนกระดูกแหลก หรือรัดคอด้วยเส้นผม",
                                          "ต่อยหน้าไม่ยั้ง กอดเผาทั้งเป็น หรือบีบกะโหลกจนแตก",
                                          "มือทั้งสี่ฉีกคุณออก รอยยิ้มเปิดกว้างแล้วกัด หรือหมุนคุณกลับหัว",
                                          "กินคุณทั้งเป็น ใช้เขาแทงทะลุ หรือกระทืบซ้ำ ๆ"][i - 1]
        k("hollowhours.guide.ghost.%d" % i, paper(en), paper(th))


guide_pages()


# ----------------------------------------------------------------------------- sound subtitles
def subtitles():
    sj = json.load(open(os.path.join(ROOT, "src", "main", "resources", "assets", "hollowhours", "sounds.json")))
    groups = [("ghost.music", "Eerie music", "ดนตรีหลอน"), ("ghost.voice", "A ghost speaks", "ผีพูด"), ("ghost.scream", "Something screams", "บางอย่างกรีดร้อง"),
              ("ghost.kill", "Something terrible happens", "เสียงบางอย่างที่น่ากลัว"), ("ghost.jumpscare", "!!!", "!!!"), ("ghost.knock", "Knocking", "เสียงเคาะ"),
              ("ghost.bang", "Something bangs", "เสียงทุบ"), ("ghost.glass", "Glass cracks", "กระจกร้าว"), ("ghost.board", "Boards creak", "เสียงไม้กั้น"),
              ("ghost.hammer", "Hammering", "เสียงตอกค้อน"), ("ghost.flashlight", "Flashlight clicks", "เสียงไฟฉาย"), ("ghost.closet", "A closet door", "เสียงประตูตู้"),
              ("ghost.pant", "Panting", "เสียงหอบ"), ("ghost.dash", "Footsteps rush", "เสียงวิ่ง"), ("ghost.scare", "Something in the house", "เสียงบางอย่างในบ้าน"),
              ("ghost.stalker", "Something is watching", "บางอย่างกำลังจ้องมอง"), ("ghost.horror", "Your heart pounds", "หัวใจเต้นแรง"),
              ("ghost.betray", "A whisper", "เสียงกระซิบ"), ("ghost.heart", "A heartbeat", "เสียงหัวใจ"), ("ghost.flatline", "A flatline", "เสียงหัวใจหยุดเต้น"),
              ("ghost.repair", "Repairing", "เสียงซ่อม"), ("ghost.g", "Something is near", "บางอย่างอยู่ใกล้ ๆ"), ("ghost.", "A ghostly sound", "เสียงผี")]
    for name in sj:
        for pre, en, th in groups:
            if name.startswith(pre):
                k("subtitles.hollowhours." + name, en, th)
                break


subtitles()


# ----------------------------------------------------------------------------- check against the code
def used_keys():
    keys, prefixes = set(), set()
    for dp, _, fs in os.walk(os.path.join(ROOT, "src", "main", "java")):
        for f in fs:
            if not f.endswith(".java"):
                continue
            s = open(os.path.join(dp, f), encoding="utf-8").read()
            for m in re.finditer(r'"((?:hollowhours|item\.hollowhours|entity\.hollowhours|itemGroup)[a-zA-Z0-9_.]*)"', s):
                key = m.group(1)
                if key.endswith(".") or key.endswith("_"):
                    prefixes.add(key)
                elif key.startswith(("hollowhours.", "item.hollowhours.", "entity.hollowhours.", "itemGroup.")) and key not in ("hollowhours.smoketest",):
                    keys.add(key)
    return keys, prefixes


def main():
    keys, prefixes = used_keys()
    missing = [x for x in sorted(keys) if x not in L]
    for p in sorted(prefixes):
        if not any(x.startswith(p) for x in L):
            missing.append(p + "*")
    os.makedirs(OUT, exist_ok=True)
    en = {key: v[0] for key, v in sorted(L.items())}
    th = {key: v[1] for key, v in sorted(L.items())}
    json.dump(en, open(os.path.join(OUT, "en_us.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    json.dump(th, open(os.path.join(OUT, "th_th.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    print("lang keys:", len(L))
    if missing:
        print("MISSING:", missing)
        sys.exit(1)


if __name__ == "__main__":
    main()
