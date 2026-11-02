"""Phone chat texts (English / Thai) written into both language files: the replies of Mom, Dad and your best friend
(calm before the alert, scared after it; 3 lines for each kind of message), their story messages, and the apps' UI."""
import collections, json, os

L = os.path.join(os.path.dirname(__file__), '..', 'hollow-hours', 'src', 'main', 'resources', 'assets', 'hollowhours', 'lang')

R = {  # who -> phase -> category -> [(en, th) x3]
 "mom": {
  "calm": {
   "greet": [("Hi sweetie! Did you eat yet?", "ว่าไงลูก กินข้าวหรือยัง"), ("Hello love ❤ how's your day?", "หวัดดีจ้ะ ❤ วันนี้เป็นไงบ้าง"), ("Hi! Don't forget to drink water.", "หวัดดีลูก อย่าลืมดื่มน้ำเยอะๆ นะ")],
   "love": [("Love you more! ❤", "แม่รักลูกมากกว่าอีก ❤"), ("Aww, miss you too. Come visit soon.", "แม่ก็คิดถึงนะ ว่างๆ กลับมาหาแม่บ้าง"), ("You're my favourite person, you know that?", "ลูกเป็นคนที่แม่รักที่สุดเลยรู้มั้ย")],
   "scared": [("Scared? Of what, silly? Turn on a light and you'll be fine.", "กลัวอะไรลูก เปิดไฟสิ เดี๋ยวก็หาย"), ("Nothing to be afraid of. I'm right here if you need me.", "ไม่มีอะไรน่ากลัวหรอก มีอะไรโทรหาแม่ได้ตลอดนะ"), ("Did you watch a horror movie again? :)", "ดูหนังผีอีกแล้วใช่มั้ย :)")],
   "where": [("I'm at home, making dinner.", "แม่อยู่บ้าน กำลังทำกับข้าว"), ("Just at the market, back soon.", "อยู่ตลาดจ้ะ เดี๋ยวก็กลับ"), ("Home, as always. Come over this weekend?", "อยู่บ้านเหมือนเดิม เสาร์อาทิตย์มากินข้าวด้วยกันมั้ย")],
   "other": [("Haha okay sweetie.", "555 จ้าลูก"), ("Mm-hm. Make sure you sleep early!", "อืมๆ อย่าลืมนอนเร็วๆ นะ"), ("That's nice! Tell me more later, I'm cooking.", "ดีจังเลย เดี๋ยวค่อยเล่าให้ฟังนะ แม่ทำกับข้าวอยู่")]},
  "danger": {
   "greet": [("Thank God you answered!! Are you safe??", "ขอบคุณพระเจ้าที่ลูกตอบ!! ปลอดภัยใช่มั้ย"), ("Hi baby, are you inside? Doors locked?", "ลูก อยู่ในบ้านใช่มั้ย ล็อกประตูแล้วนะ"), ("Hello? Please tell me you're okay.", "ฮัลโหล บอกแม่ทีว่าลูกไม่เป็นไร")],
   "love": [("I love you so much. Please stay alive for me.", "แม่รักลูกมาก อยู่รอดให้แม่ด้วยนะ"), ("I love you too. We'll see each other again, I promise.", "แม่ก็รักลูก เราจะได้เจอกันอีกแน่นอน แม่สัญญา"), ("Mom loves you. Keep the lights low and stay hidden.", "แม่รักลูกนะ หรี่ไฟไว้แล้วซ่อนตัวดีๆ")],
   "scared": [("I'm scared too. Breathe slowly. Don't let it see you.", "แม่ก็กลัว หายใจช้าๆ นะลูก อย่าให้มันเห็นเด็ดขาด"), ("Hide somewhere small and keep quiet, okay? Mom's praying for you.", "หาที่ซ่อนแคบๆ แล้วเงียบไว้นะ แม่สวดมนต์ให้อยู่"), ("Don't cry, baby. Lock the door and stay away from the windows.", "อย่าร้องนะลูก ล็อกประตู แล้วอยู่ห่างหน้าต่างไว้")],
   "where": [("I'm hiding in the basement. Something keeps walking around the house.", "แม่ซ่อนอยู่ใต้ถุนบ้าน มีอะไรเดินวนรอบบ้านไม่หยุดเลย"), ("Still at home. The power went out an hour ago.", "ยังอยู่บ้าน ไฟดับไปชั่วโมงนึงแล้ว"), ("Don't come here! It's not safe on the roads.", "อย่ามาที่นี่นะ! ถนนไม่ปลอดภัย")],
   "other": [("Please be careful, my love.", "ระวังตัวด้วยนะลูก"), ("Keep your phone charged. I need to know you're okay.", "ชาร์จแบตไว้นะ แม่ต้องรู้ว่าลูกยังไม่เป็นอะไร"), ("Did you eat? Drink water. Stay inside.", "กินข้าวหรือยัง ดื่มน้ำด้วย อยู่แต่ในบ้านนะ")]}},
 "dad": {
  "calm": {
   "greet": [("Hey kiddo.", "ว่าไงลูก"), ("Hi. Everything good?", "หวัดดี ทุกอย่างโอเคมั้ย"), ("Hey champ, what's up?", "ว่าไงไอ้หนู มีอะไร")],
   "love": [("Love you too, kid.", "พ่อก็รักลูกนะ"), ("Proud of you. Always.", "พ่อภูมิใจในตัวลูกเสมอ"), ("Miss you too. Let's go fishing soon.", "พ่อก็คิดถึง ว่างๆ ไปตกปลากัน")],
   "scared": [("Nothing out there a flashlight can't fix.", "ไม่มีอะไรที่ไฟฉายแก้ไม่ได้หรอก"), ("Just a noise. Check the door and get some sleep.", "แค่เสียงเฉยๆ เช็กประตูแล้วนอนซะ"), ("You're tougher than you think.", "ลูกเก่งกว่าที่คิดนะ")],
   "where": [("At work. Call you tonight.", "อยู่ที่ทำงาน คืนนี้โทรหานะ"), ("In the garage, fixing the car again.", "อยู่ในโรงรถ ซ่อมรถอีกแล้ว"), ("Driving home. Can't text long.", "ขับรถกลับบ้านอยู่ พิมพ์นานไม่ได้")],
   "other": [("Okay.", "โอเค"), ("Got it. Don't stay up too late.", "รับทราบ อย่านอนดึกล่ะ"), ("Ha. Good one.", "ฮ่าๆ เข้าท่า")]},
  "danger": {
   "greet": [("I'm here. Talk to me. Are you safe?", "พ่ออยู่นี่ พูดกับพ่อสิ ปลอดภัยมั้ย"), ("Good, you're alive. Stay where you are.", "ดีที่ยังรอด อยู่ตรงนั้นแหละ อย่าไปไหน"), ("Hey. Keep your voice down and your lights off.", "เบาเสียงไว้ แล้วปิดไฟด้วย")],
   "love": [("Love you, kid. I'll find a way to you.", "พ่อรักลูก พ่อจะหาทางไปหาให้ได้"), ("Proud of you. Keep going.", "พ่อภูมิใจในตัวลูก สู้ต่อไป"), ("I love you. Whatever happens, keep moving.", "พ่อรักลูก ไม่ว่าจะเกิดอะไรขึ้น อย่าหยุด")],
   "scared": [("Listen: block the doors, board the windows, keep a light ready.", "ฟังนะ ขวางประตู ตอกไม้ปิดหน้าต่าง เตรียมไฟฉายไว้"), ("If it sees you, run. Don't look back. Hide in a closet if you have to.", "ถ้ามันเห็น วิ่ง อย่าหันกลับไป ถ้าจำเป็นให้ซ่อนในตู้"), ("Fear is fine. Panic isn't. Breathe and think.", "กลัวได้ แต่ห้ามตื่นตูม หายใจลึกๆ แล้วคิด")],
   "where": [("The roads are blocked. I'm trying to get to you.", "ถนนถูกปิดหมด พ่อกำลังหาทางไปหาอยู่"), ("Holed up at work. Something is out in the parking lot.", "พ่อติดอยู่ที่ทำงาน มีอะไรอยู่ในลานจอดรถ"), ("I can't get to you yet. Hold on.", "พ่อยังไปหาไม่ได้ อดทนไว้นะ")],
   "other": [("Copy.", "รับทราบ"), ("Keep your battery up. Text me every day.", "ประหยัดแบตไว้ ทักพ่อทุกวันนะ"), ("Stay quiet. Stay hidden.", "เงียบไว้ ซ่อนตัวไว้")]}},
 "friend": {
  "calm": {
   "greet": [("yooo", "โย่ววว"), ("heyy what's up", "หวัดดี ทำไรอยู่"), ("sup lol", "ว่าไง 555")],
   "love": [("aww love u too bro", "รักมึงเหมือนกันเพื่อน 555"), ("miss u too man, gaming tonight?", "คิดถึงเหมือนกัน คืนนี้เล่นเกมกันมั้ย"), ("ur the goat fr", "มึงเจ๋งสุดจริงๆ")],
   "scared": [("lmao scared of what, creepers?", "555 กลัวอะไร ครีปเปอร์เหรอ"), ("bro it's just the wind", "ลมเฉยๆ มั้งเพื่อน"), ("ur such a baby lol jk, wanna call?", "ปอดแหกจริง 555 ล้อเล่น โทรคุยมั้ย")],
   "where": [("home, bored outta my mind", "อยู่บ้าน เบื่อสุดๆ"), ("at the mine, found diamonds lol", "อยู่ในเหมือง เจอเพชรด้วย 555"), ("building my castle, come see", "สร้างปราสาทอยู่ มาดูดิ")],
   "other": [("lol", "555"), ("fr fr", "จริงดิ"), ("bro what lmao", "อะไรของมึง 555")]},
  "danger": {
   "greet": [("dude ur alive!! thank god", "มึงยังรอด!! ขอบคุณพระเจ้า"), ("hey hey are u ok??", "เฮ้ยๆ เป็นไรมั้ย"), ("bro answer faster i thought u were dead", "ตอบเร็วๆ ดิ นึกว่ามึงตายไปแล้ว")],
   "love": [("if we make it i'm buying u food forever", "ถ้ารอดไปได้ กูเลี้ยงข้าวมึงตลอดชีวิต"), ("ur my best friend man, don't die ok", "มึงคือเพื่อนสนิทกูนะ อย่าตายล่ะ"), ("love u bro. stay alive.", "รักมึงนะเพื่อน อยู่รอดให้ได้")],
   "scared": [("me too man. i'm shaking", "กูก็กลัว ตัวสั่นไปหมดแล้ว"), ("turn off ur lights. i think they find u by light", "ปิดไฟซะ กูว่ามันหาเราจากแสง"), ("don't look at it. whatever u do don't look at it", "อย่ามองมัน ไม่ว่ายังไงก็อย่ามองมัน")],
   "where": [("hiding in my closet. it's in my street", "ซ่อนอยู่ในตู้ มันอยู่ในซอยบ้านกู"), ("on my roof. i can see them in the trees", "อยู่บนหลังคา เห็นพวกมันอยู่ในป่า"), ("basement. phone at 4%%", "ใต้ดิน แบตเหลือ 4%%")],
   "other": [("can't talk long, battery", "คุยนานไม่ได้ แบตจะหมด"), ("shh it's close", "ชู่ว มันอยู่ใกล้ๆ"), ("stay alive ok?", "อยู่รอดนะ โอเคมั้ย")]}},
}
STORY = {
 "mom": {"story1": ("Did you get the emergency alert?! Lock every door and stay inside. Mom loves you.", "ได้รับแจ้งเตือนฉุกเฉินมั้ยลูก! ล็อกประตูทุกบานแล้วอยู่แต่ในบ้านนะ แม่รักลูก"),
         "story2": ("The power is out here. I can hear scratching at the door... I'm so scared.", "ที่นี่ไฟดับแล้ว แม่ได้ยินเสียงข่วนประตู... แม่กลัวมาก"),
         "final": ("Baby... we didn't make it. I love you. Don't let it see you. Never.", "ลูก... พวกเราไม่รอดแล้ว แม่รักลูกนะ อย่าให้มันเห็นเด็ดขาด")},
 "dad": {"story1": ("Saw the alert. Stay inside, board the windows, don't open the door for anyone. I'll text when I can.", "เห็นแจ้งเตือนแล้ว อยู่ในบ้าน ตอกไม้ปิดหน้าต่าง อย่าเปิดประตูให้ใคร ว่างแล้วพ่อจะทักไป"),
         "story2": ("The roads are blocked. I can't reach you yet. Save your food and stay hidden.", "ถนนถูกปิดหมด พ่อยังไปหาไม่ได้ ประหยัดอาหารไว้แล้วซ่อนตัวนะ"),
         "final": ("We're not going to make it. Listen: never let it see you. Promise me. I love you.", "พวกเราไม่รอดแล้ว ฟังพ่อนะ อย่าให้มันเห็นเด็ดขาด สัญญากับพ่อ พ่อรักลูก")},
 "friend": {"story1": ("BRO did u see the alert?? something is walking outside my house rn", "เฮ้ย เห็นแจ้งเตือนมั้ย!? ตอนนี้มีอะไรเดินอยู่หน้าบ้านกู"),
            "story2": ("it's in my street man. i'm hiding in the closet. if i stop answering... u know", "มันอยู่ในซอยบ้านกูแล้ว กูซ่อนในตู้อยู่ ถ้ากูเงียบไป... มึงก็รู้นะ"),
            "final": ("we didnt make it man. its here. DONT let it see u. ever.", "พวกเราไม่รอดแล้วเพื่อน มันมาแล้ว อย่าให้มันเห็นมึงเด็ดขาด")},
}
FIRST = {"dad": ("Hey kiddo. Settled in at the new place? Call me if you need anything.", "ว่าไงลูก บ้านใหม่เป็นไงบ้าง มีอะไรโทรหาพ่อได้เลยนะ")}
NAMES = {"mom": ("Mom", "แม่"), "dad": ("Dad", "พ่อ"), "friend": ("Best friend", "เพื่อนสนิท")}
UI = {
 "hollowhours.phone.app.chat": ("Chat", "แชท"),
 "hollowhours.phone.app.map": ("Location", "ตำแหน่ง"),
 "hollowhours.phone.app.rescue": ("Rescue", "กู้ภัย"),
 "hollowhours.chat.nosignal": ("No signal...", "ไม่มีสัญญาณ..."),
 "hollowhours.chat.offline": ("Offline", "ออฟไลน์"),
 "hollowhours.chat.empty": ("Say hi!", "ทักทายกันหน่อย!"),
 "hollowhours.chat.you": ("You: %s", "คุณ: %s"),
 "hollowhours.chat.you.short": ("Me", "ฉัน"),
 "hollowhours.chat.location": ("◉ My location: %s, %s, %s (tap to go there)", "◉ ตำแหน่ง: %s, %s, %s (แตะเพื่อนำทาง)"),
 "hollowhours.chat.failed": ("✗ Not delivered", "✗ ส่งไม่สำเร็จ"),
 "hollowhours.chat.typing": ("typing%s", "กำลังพิมพ์%s"),
 "hollowhours.chat.hint": ("Type a message...", "พิมพ์ข้อความ..."),
 "hollowhours.chat.notice.player.title": ("New message", "ข้อความใหม่"),
 "hollowhours.chat.notice.player": ("%s: %s", "%s: %s"),
 "hollowhours.chat.notice.location.title": ("Location shared", "แชร์ตำแหน่ง"),
 "hollowhours.chat.notice.location": ("%s sent you their location. Open Chat and tap it to navigate.", "%s ส่งตำแหน่งมาให้ เปิดแอปแชทแล้วแตะเพื่อนำทาง"),
 "hollowhours.map.home": ("Home", "บ้าน"),
 "hollowhours.map.heli": ("Helicopter", "เฮลิคอปเตอร์"),
 "hollowhours.map.grave": ("My grave", "หลุมศพของฉัน"),
 "hollowhours.map.facing": ("Facing %s", "หันไปทาง%s"),
 "hollowhours.map.dir.n": ("north", "เหนือ"), "hollowhours.map.dir.ne": ("north-east", "ตะวันออกเฉียงเหนือ"),
 "hollowhours.map.dir.e": ("east", "ตะวันออก"), "hollowhours.map.dir.se": ("south-east", "ตะวันออกเฉียงใต้"),
 "hollowhours.map.dir.s": ("south", "ใต้"), "hollowhours.map.dir.sw": ("south-west", "ตะวันตกเฉียงใต้"),
 "hollowhours.map.dir.w": ("west", "ตะวันตก"), "hollowhours.map.dir.nw": ("north-west", "ตะวันตกเฉียงเหนือ"),
 "hollowhours.map.elsewhere": ("other world", "อีกโลก"),
 "hollowhours.map.none": ("Nowhere saved yet. Friends can send you their location in Chat.", "ยังไม่มีที่บันทึกไว้ เพื่อนส่งตำแหน่งมาให้ได้ในแอปแชท"),
 "hollowhours.map.stop": ("Stop navigating", "หยุดนำทาง"),
 "hollowhours.map.arrived": ("You've arrived: %s", "ถึงแล้ว: %s"),
 "hollowhours.map.nav.elsewhere": ("%s is in another world", "%s อยู่อีกโลกหนึ่ง"),
 "hollowhours.rescue.app.nosignal": ("Searching for a signal...", "กำลังหาสัญญาณ..."),
 "hollowhours.rescue.app.here": ("IT'S HERE!", "มาแล้ว!"),
 "hollowhours.rescue.app.board": ("Get on board before it leaves! (right-click the helicopter)", "รีบขึ้นก่อนมันจะไป! (คลิกขวาที่เฮลิคอปเตอร์)"),
 "hollowhours.rescue.app.arrives": ("The rescue helicopter arrives in", "เฮลิคอปเตอร์กู้ภัยจะมาถึงในอีก"),
 "hollowhours.rescue.app.left": ("%sd %sh %sm", "%s วัน %s ชม. %s น."),
 "hollowhours.rescue.app.when": ("Day %s at %s", "วันที่ %s เวลา %s น."),
 "hollowhours.rescue.app.ready": ("It only waits a few minutes. Be ready.", "รอได้แค่ไม่กี่นาที เตรียมตัวให้พร้อม"),
 "hollowhours.rescue.app.nonews": ("No news yet... keep your phone charged.", "ยังไม่มีข่าว... ชาร์จแบตไว้ให้ดี"),
 "hollowhours.rescue.app.harsh": ("The ghosts are angrier: x%s, blood moon x%s", "ผีโหดขึ้น %s เท่า คืนพระจันทร์สีเลือด %s เท่า"),
 "hollowhours.rescue.app.run": ("RUN!", "หนีเร็ว!"),
 "hollowhours.rescue.app.runmap": ("They found the haven. The helicopter is waiting: tap below to navigate.", "ผีบุกสวรรค์แล้ว เฮลิคอปเตอร์รออยู่ แตะด้านล่างเพื่อนำทาง"),
 "hollowhours.rescue.app.safe": ("No ghosts here. Rest, eat, build... for now.", "ที่นี่ไม่มีผี พักผ่อน กิน สร้างบ้านได้... แค่ตอนนี้"),
 "hollowhours.rescue.app.nobody": ("Nobody is coming anymore.", "จะไม่มีใครมาช่วยอีกแล้ว"),
 "hollowhours.rescue.app.navigate": ("Navigate to the helicopter", "นำทางไปเฮลิคอปเตอร์"),
}

for lang, i in (("en_us", 0), ("th_th", 1)):
    p = os.path.join(L, lang + '.json')
    d = json.load(open(p), object_pairs_hook=collections.OrderedDict)
    for who, phases in R.items():
        for phase, cats in phases.items():
            for cat, lines in cats.items():
                assert len(lines) == 3, (who, phase, cat)
                for n, l in enumerate(lines):
                    d["hollowhours.chat.%s.%s.%s.%d" % (who, phase, cat, n)] = l[i]
    for who, parts in STORY.items():
        for part, l in parts.items():
            d["hollowhours.chat.%s.%s" % (who, part)] = l[i]
        d["hollowhours.chat.name." + who] = NAMES[who][i]
        d["hollowhours.chat.notice.%s.title" % who] = NAMES[who][i]
        d["hollowhours.chat.notice." + who] = ("New message from " + NAMES[who][0]) if i == 0 else ("มีข้อความใหม่จาก" + NAMES[who][1])
    d["hollowhours.chat.dad.hello"] = FIRST["dad"][i]
    for k, v in UI.items():
        d[k] = v[i]
    json.dump(d, open(p, 'w'), ensure_ascii=False, indent=1)
print('chat texts written')
