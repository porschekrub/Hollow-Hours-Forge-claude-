"""The last chapter (2.12): texts for the final hunt, the map, the mountain, the boss, the rift and the credits."""
import collections
import json
import os

ROOT = os.path.join(os.path.dirname(__file__), '..', 'hollow-hours', 'src', 'main', 'resources')
L = os.path.join(ROOT, 'assets', 'hollowhours', 'lang')

T = {
    "entity.hollowhours.weeping": ("The Thing That Weeps Under the Mountain", "สิ่งที่ร้องไห้ใต้ภูเขา"),
    "entity.hollowhours.rift": ("Rift", "รอยแยก"),
    "item.hollowhours.mountain_map": ("Old Map of Mist Hollow", "แผนที่เก่าของหุบหมอก"),
    # countdown
    "hollowhours.endgame.crying": ("Somewhere far off, under a mountain, something is crying...", "ที่ไหนสักแห่งไกลออกไป ใต้ภูเขา... มีบางอย่างกำลังร้องไห้"),
    "hollowhours.endgame.msg.crying": ("Do you hear it too? The crying. It comes from the mountains, every night louder.",
                                       "ได้ยินเหมือนกันไหม เสียงร้องไห้ มันดังมาจากทางภูเขา ดังขึ้นทุกคืน"),
    "hollowhours.endgame.msg.tonight": ("Tonight. All five of them. Together.", "คืนนี้ ทั้งห้าตน มาพร้อมกัน"),
    "hollowhours.endgame.tonight": ("Tonight, all five will come", "คืนนี้ ทั้งห้าตนจะมาพร้อมกัน"),
    # the hunt
    "hollowhours.endgame.hunt.title": ("ALL FIVE ARE HERE", "พวกมันมาครบทั้งห้าตน"),
    "hollowhours.endgame.hunt.sub": ("Knock out every one of them - they stay down now", "ทำให้ผีสลบให้ครบทั้ง 5 ตน - คราวนี้สลบแล้วจะไม่ฟื้น"),
    "hollowhours.endgame.msg.hunt": ("They're all here. Knock them ALL out. This time, once they're down, they stay down.",
                                     "มันมากันครบแล้ว ทำให้มันสลบให้หมดทุกตน คราวนี้ถ้าสลบแล้ว มันจะไม่ฟื้นขึ้นมาอีก"),
    "hollowhours.endgame.down": ("%s is down for good! (%s/5)", "%s สลบไม่ฟื้นแล้ว! (%s/5)"),
    # the map
    "hollowhours.endgame.map.title": ("They remember who they were", "พวกเขาจำได้แล้วว่าตัวเองเคยเป็นใคร"),
    "hollowhours.endgame.map.sub": ("Follow the map to the mountain", "ตามแผนที่ไปที่ภูเขา"),
    "hollowhours.endgame.msg.map": ("The old map of Mist Hollow. The X is the mine, under the mountain. Bring weapons. Bring friends.",
                                    "แผนที่เก่าของหุบหมอก กากบาทคือเหมืองใต้ภูเขา เอาอาวุธไปด้วย ชวนเพื่อนไปด้วย"),
    "hollowhours.endgame.map.dist": ("The mountain: %s blocks %s", "ภูเขา: อีก %s บล็อก %s"),
    "hollowhours.endgame.map.here": ("The mine is here. Find the way down.", "เหมืองอยู่ตรงนี้แล้ว หาทางลงไปข้างล่าง"),
    "hollowhours.endgame.sign.0": ("MIST HOLLOW", "หุบหมอก"),
    "hollowhours.endgame.sign.1": ("DO NOT DIG", "ห้ามขุดลึก"),
    "hollowhours.endgame.sign.2": ("DEEPER THAN THE", "เกินศาลกวางขาว"),
    "hollowhours.endgame.sign.3": ("WHITE DEER SHRINE", "เด็ดขาด"),
    # the boss
    "hollowhours.endgame.boss.sub": ("It has been crying for forty years", "มันร้องไห้มาสี่สิบปีแล้ว"),
    "hollowhours.endgame.boss.phase2": ("It cries harder... the whole mountain shakes", "มันร้องไห้หนักขึ้น... ภูเขาทั้งลูกสั่นสะเทือน"),
    "hollowhours.endgame.boss.phase3": ("The ceiling is coming down!", "เพดานกำลังถล่ม!"),
    "hollowhours.endgame.won.title": ("The crying stops", "เสียงร้องไห้เงียบลงแล้ว"),
    "hollowhours.endgame.won.sub": ("for the first time in forty years", "เป็นครั้งแรกในรอบสี่สิบปี"),
    "hollowhours.endgame.freed.1": ("Thongdee the miner can rest now.", "ทองดี คนขุดแร่ ได้พักผ่อนเสียที"),
    "hollowhours.endgame.freed.2": ("Buakhao stops waiting by the lake.", "บัวขาว ไม่ต้องรออยู่ริมทะเลสาบอีกแล้ว"),
    "hollowhours.endgame.freed.3": ("Grandma Khamsaeng's fire goes out at last.", "ไฟที่เผายายคำแสง มอดลงแล้วในที่สุด"),
    "hollowhours.endgame.freed.4": ("Somebody finally looks at Yim. He smiles - for real this time.", "ในที่สุดก็มีคนหันมามองยิ้ม เขายิ้ม... คราวนี้ยิ้มจริงๆ"),
    "hollowhours.endgame.freed.5": ("Hunter Boon isn't hungry any more.", "พรานบุญ ไม่หิวอีกแล้ว"),
    "hollowhours.endgame.rift": ("Where it sank, the world has torn open. White light pours out. Step in.",
                                 "ตรงที่มันจมลงไป โลกฉีกออกเป็นรอยแยก แสงสีขาวทะลักออกมา เดินเข้าไปสิ"),
    "hollowhours.endgame.msg.peace": ("It's over. The crying stopped. No more ghosts. Go outside - it's a nice day.",
                                      "จบแล้ว เสียงร้องไห้หยุดแล้ว ไม่มีผีอีกต่อไป ออกไปข้างนอกเถอะ วันนี้อากาศดีนะ"),
    # credits
    "hollowhours.credits.title": ("HOLLOW HOURS", "HOLLOW HOURS"),
    "hollowhours.credits.end": ("THE END", "จบบริบูรณ์"),
    "hollowhours.credits.epilogue.1": ("Forty years ago, Mist Hollow dug too deep.", "สี่สิบปีก่อน หมู่บ้านหุบหมอกขุดลึกเกินไป"),
    "hollowhours.credits.epilogue.2": ("Something under the mountain cried, and five people answered.", "บางอย่างใต้ภูเขาร้องไห้ และมีคนห้าคนตอบรับเสียงนั้น"),
    "hollowhours.credits.epilogue.3": ("For forty years they were its hands.", "สี่สิบปีที่พวกเขาต้องเป็นมือของมัน"),
    "hollowhours.credits.epilogue.4": ("Tonight, somebody finally made it stop.", "คืนนี้ ในที่สุดก็มีคนทำให้มันหยุดได้"),
    "hollowhours.credits.epilogue.5": ("The house on the cheap land is just a house again.", "บ้านบนที่ดินราคาถูกหลังนั้น กลับมาเป็นแค่บ้านธรรมดาอีกครั้ง"),
    "hollowhours.credits.five": ("The Five", "ทั้งห้าคน"),
    "hollowhours.credits.p1.name": ("Thongdee", "ทองดี"),
    "hollowhours.credits.p1.was": ("the hardest-working miner in the village", "คนขุดแร่ที่ขยันที่สุดในหมู่บ้าน"),
    "hollowhours.credits.p2.name": ("Buakhao", "บัวขาว"),
    "hollowhours.credits.p2.was": ("the bride who waited by the lake", "เจ้าสาวที่รอเขาอยู่ริมทะเลสาบ"),
    "hollowhours.credits.p3.name": ("Grandma Khamsaeng", "ยายคำแสง"),
    "hollowhours.credits.p3.was": ("the village healer who told them to close the mine", "หมอยาประจำหมู่บ้าน ผู้เตือนให้ปิดเหมือง"),
    "hollowhours.credits.p4.name": ("Yim", "ยิ้ม"),
    "hollowhours.credits.p4.was": ("the boy nobody ever looked at", "เด็กชายที่ไม่เคยมีใครหันมามอง"),
    "hollowhours.credits.p5.name": ("Hunter Boon", "พรานบุญ"),
    "hollowhours.credits.p5.was": ("who was hungry enough to eat the white deer", "นายพรานผู้หิวจนกินกวางขาว"),
    "hollowhours.credits.boss": ("The Thing That Weeps Under the Mountain", "สิ่งที่ร้องไห้ใต้ภูเขา"),
    "hollowhours.credits.boss.was": ("It cried with a little girl's voice. It is quiet now.", "มันร้องไห้ด้วยเสียงของเด็กผู้หญิงคนหนึ่ง ตอนนี้มันเงียบแล้ว"),
    "hollowhours.credits.survivors": ("Still here", "ผู้ที่รอดมาจนถึงวันนี้"),
    "hollowhours.credits.made": ("Made by", "สร้างโดย"),
    "hollowhours.credits.team": ("and the Hollow Hours team", "และทีม Hollow Hours"),
    "hollowhours.credits.thanks": ("Thank you for playing", "ขอบคุณที่เล่นจนจบ"),
    "hollowhours.credits.peace": ("The world is quiet now. Stay as long as you like.", "โลกนี้สงบแล้ว จะอยู่ต่ออีกนานแค่ไหนก็ได้"),
    "hollowhours.credits.hint": ("Hold Space to speed up  ·  Esc to skip", "กด Space ค้างเพื่อเร่ง  ·  Esc เพื่อข้าม"),
    # sounds
    "subtitles.hollowhours.boss.cry": ("Crying under the mountain", "เสียงร้องไห้ใต้ภูเขา"),
    "subtitles.hollowhours.boss.scream": ("Something screams", "บางอย่างกรีดร้อง"),
    "subtitles.hollowhours.boss.slam": ("A huge hand slams down", "มือยักษ์ฟาดลงพื้น"),
    "subtitles.hollowhours.boss.rise": ("The ground breaks open", "พื้นแตกออก"),
    "subtitles.hollowhours.boss.defeat": ("The crying stops", "เสียงร้องไห้หยุดลง"),
    "subtitles.hollowhours.boss.release": ("Something is set free", "บางอย่างได้รับอิสรภาพ"),
    "subtitles.hollowhours.boss.windup": ("It draws back", "มันง้างมือ"),
    "subtitles.hollowhours.boss.sweep": ("Something sweeps the floor", "มือกวาดไปตามพื้น"),
    "subtitles.hollowhours.boss.grab": ("Grabbed!", "โดนจับ!"),
    "subtitles.hollowhours.boss.tear": ("A black tear falls", "น้ำตาสีดำหยดลงมา"),
    "subtitles.hollowhours.boss.hurt": ("It groans", "มันคราง"),
    "subtitles.hollowhours.rift.hum": ("The rift hums", "รอยแยกส่งเสียงหึ่ง"),
    "subtitles.hollowhours.ending.boom": ("BOOM", "ตูม!"),
}

for lang, i in (("en_us", 0), ("th_th", 1)):
    p = os.path.join(L, lang + '.json')
    d = json.load(open(p), object_pairs_hook=collections.OrderedDict)
    for k, v in T.items():
        d[k] = v[i]
    json.dump(d, open(p, 'w'), ensure_ascii=False, indent=1)
print('endgame texts:', len(T))
