#!/usr/bin/env python3
"""Builds data/hollowhours/dialogue.json: every built-in ghost line (Thai + English), topic answers, keyword lists
for intent detection and the personas used when a language model writes the lines instead.

Base lines come from the Bedrock add-on (tools/out/ghost_dialogue.json, extracted earlier); the Java edition adds
more lines per situation, the missing English "why/want" answers and the item-asking lines.
"""
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "hollow-hours", "src", "main", "resources", "data", "hollowhours", "dialogue.json")
base = json.load(open(os.path.join(HERE, "data", "ghost_dialogue_base.json")))

NAMES = {
    "en": {1: "The Wailing Dweller", 2: "Sunken Widow", 3: "Ashen Screamer", 4: "Smiling Void", 5: "The Antlered Hunger"},
    "th": {1: "ผีร่ำไห้ใต้เหมือง", 2: "เจ้าสาวจมน้ำ", 3: "ผีเถ้ากรีดร้อง", 4: "ผียิ้มในความมืด", 5: "ผีหิวเขากวาง"},
}
PERSONA = {
    1: "A miner who died trapped underground after following the sound of crying. Pale, gaunt, eyes weeping black. Speaks in broken echoes, repeats the player's words back, hunts by SOUND. Sad, hollow, mocking in a whispery way, uses '...' a lot.",
    2: "A bride who drowned in a lake on her wedding day, still waiting for a groom who never came. Long wet black hair over her face, torn wedding dress. Possessive, lonely, cold, talks about water, rings, vows and drowning. Calls the player 'my love' sometimes.",
    3: "A man burned alive in a house fire. Charred black skin with glowing cracks. Furious and in pain, SHOUTS IN CAPITAL LETTERS, obsessed with fire, heat and burning. Never calm.",
    4: "A creature of pure darkness with an enormous white grin and extra arms. Cheerfully polite and playful in a sickening way, always ends sentences with ':)'. Pretends to be friendly, hints it is always right behind the player.",
    5: "A starving wendigo-like thing with a deer skull and antlers. Speaks in one or two words at a time, '...hungry.' style. Only cares about food and the player's smell. Freezes when watched.",
}

# ------------------------------------------------------------------ extra lines (Java edition)
EXTRA = {"en": {}, "th": {}}

EXTRA["en"][1] = {
    "greet": ["hello? hello? ...that's my voice now", "you came down here... to cry with me?"],
    "question": ["questions echo... answers don't", "ask the dark. it answers in my voice"],
    "plead": ["please... please... i said that too. nobody came", "your begging echoes so nicely down here"],
    "bargain": ["you want to trade? trade me your voice", "keep your {item}... give me your scream"],
    "taunt": ["laugh... laugh... it bounces off the walls", "brave words... they shake at the end"],
    "other": ["{echo}... {echo}... i'll say it in your voice tonight", "...{echo}... i'll remember that"],
    "success": ["the crying stops... for now... go", "you sound like her... i'll let you go"],
    "rage": ["I'LL WEAR YOUR VOICE!!", "SCREAM FOR ME! SCREAM!!"],
    "who": ["i'm the one you hear crying at night", "a miner... a voice... a hole in the dark"],
    "bye": ["bye... bye... i'll call you back in your mother's voice", "go... i'll follow the footsteps"],
    "kind": ["no one said kind things down there", "kind... kind... the word hurts"],
    "fear": ["your heart is so loud", "i can hear you shaking"],
    "threat": ["you can't hit an echo", "hurt me? i'm already crying"],
    "why": ["because you made a sound", "because the crying never stops... and now you hear it too"],
    "want": ["your voice. i lost mine", "someone to cry with... forever"],
    "nothave": ["{item}? your pockets are empty... i heard them", "lies echo the loudest"],
    "refuse": ["the {item} fell into the dark... i'm still here", "{item}... not what i wanted"],
    "ask_give": ["take it... it was down here with me", "this was in the dark... now it's yours", "take it... and listen... it hums"],
    "ask_refuse": ["no... no... no...", "ask again... i like hearing you ask", "the dark keeps it"],
    "ask_unknown": ["what is that? ...what is that?", "i don't know that word... say it again..."],
    "ask_limit": ["no more... you've taken enough from the dark", "nothing left... only crying"],
}
EXTRA["th"][1] = {
    "greet": ["ฮัลโหล? ฮัลโหล? ...นั่นเสียงฉันแล้วนะ", "เธอลงมาที่นี่... เพื่อร้องไห้กับฉันเหรอ?"],
    "question": ["คำถามมีเสียงก้อง... คำตอบไม่มี", "ถามความมืดสิ มันจะตอบเป็นเสียงฉัน"],
    "plead": ["ขอร้อง... ขอร้อง... ฉันก็เคยพูดแบบนั้น ไม่มีใครมา", "เสียงอ้อนวอนของเธอก้องสวยดีนะ"],
    "bargain": ["อยากแลกเหรอ? แลกเสียงของเธอมาสิ", "เก็บ{item}ไว้เถอะ... เอาเสียงกรีดร้องมาแทน"],
    "taunt": ["หัวเราะสิ... หัวเราะ... เสียงมันกระเด้งไปทั่วผนัง", "พูดกล้าดีนะ... แต่ท้ายเสียงสั่น"],
    "other": ["{echo}... {echo}... คืนนี้ฉันจะพูดมันด้วยเสียงเธอ", "...{echo}... ฉันจะจำไว้"],
    "success": ["เสียงร้องไห้เงียบลงแล้ว... ตอนนี้... ไปเถอะ", "เสียงเธอเหมือนลูกสาวฉัน... ฉันจะปล่อยไป"],
    "rage": ["ฉันจะสวมเสียงของเธอ!!", "กรีดร้องให้ฉันฟัง! กรีดร้อง!!"],
    "who": ["ฉันคือเสียงร้องไห้ที่เธอได้ยินตอนกลางคืน", "คนขุดเหมือง... เสียง... หลุมในความมืด"],
    "bye": ["บาย... บาย... เดี๋ยวฉันจะเรียกเธอด้วยเสียงแม่เธอ", "ไปสิ... ฉันจะตามเสียงฝีเท้าไป"],
    "kind": ["ข้างล่างนั่นไม่มีใครพูดดีกับฉันเลย", "ใจดี... ใจดี... คำนี้มันเจ็บ"],
    "fear": ["หัวใจเธอเต้นดังมาก", "ฉันได้ยินเธอตัวสั่นนะ"],
    "threat": ["เธอตีเสียงสะท้อนไม่ได้หรอก", "ทำร้ายฉัน? ฉันร้องไห้อยู่แล้ว"],
    "why": ["เพราะเธอส่งเสียง", "เพราะเสียงร้องไม่เคยหยุด... ตอนนี้เธอก็ได้ยินแล้ว"],
    "want": ["เสียงของเธอ ฉันเสียของฉันไปแล้ว", "ใครสักคนมาร้องไห้ด้วยกัน... ตลอดไป"],
    "nothave": ["{item}? กระเป๋าเธอว่างเปล่า... ฉันได้ยิน", "คำโกหกก้องดังที่สุด"],
    "refuse": ["{item}ตกลงไปในความมืดแล้ว... ฉันยังอยู่ตรงนี้", "{item}... ไม่ใช่สิ่งที่ฉันต้องการ"],
    "ask_give": ["เอาไปสิ... ฉันไม่ต้องการมันแล้ว", "ของนี้ไม่ใช่ของฉันตั้งแต่แรก... รับไป", "ได้... แต่มันจะจำเธอไว้"],
    "ask_refuse": ["ไม่มี... ฉันไม่มีอะไรเหลือให้เธอ", "ขอดังกว่านี้... ฉันยังไม่ได้ยิน", "ครั้งนี้ ฉันขอเก็บไว้"],
    "ask_unknown": ["สิ่งนั้น... ฉันไม่รู้จัก", "พูดใหม่อีกครั้ง... ฉันได้ยินแต่เสียงร้องไห้"],
    "ask_limit": ["พอแล้ว... ฉันให้เธอมากพอแล้ว", "ไม่มีอีกแล้ว... ไม่มีวันมีอีก"],
}

EXTRA["en"][2] = {
    "greet": ["you came... you finally came...", "my love? ...no. not you. but you'll do"],
    "question": ["ask the lake. it took my answers", "i only remember the water, and the waiting"],
    "plead": ["he begged too, in my dreams... he never came", "shh... shh... the water is calm. let it take you"],
    "bargain": ["a gift for the bride? {item}... it isn't a ring", "the lake doesn't trade. it keeps"],
    "taunt": ["laugh at the bride... everyone did", "you'll stop laughing underwater"],
    "other": ["'{echo}'... he used to say things like that", "speak softer... the water listens"],
    "success": ["go... before i change my mind and keep you", "you remind me of him... leave, quickly"],
    "rage": ["YOU'LL DROWN WITH ME!", "I'LL HOLD YOU UNDER UNTIL YOU'RE QUIET!"],
    "who": ["the bride nobody came for", "i waited at the lake in white... i'm still waiting"],
    "bye": ["don't leave me at the altar again", "go... the water will bring you back to me"],
    "kind": ["no one has been kind to me in so long... stay", "kind... like his promises"],
    "fear": ["don't be afraid... it only hurts until you breathe in", "your hands are cold. like mine"],
    "threat": ["you can't kill what already drowned", "violence on my wedding day?"],
    "why": ["because you're alone... like me", "because i need someone to stay"],
    "want": ["a groom. a ring. a hand to hold under the water", "you... to stay. forever"],
    "ask_give": ["a wedding gift... take it", "from the bottom of the lake... it's yours now", "take it... and don't forget me"],
    "ask_refuse": ["the lake won't give that back", "tonight i give nothing to no one", "he never gave me anything either"],
    "ask_unknown": ["underwater... i can't hear that name", "that word sank"],
    "ask_limit": ["the lake has nothing more for you", "you've taken enough from me..."],
}
EXTRA["th"][2] = {
    "greet": ["เธอมาแล้ว... ในที่สุดเธอก็มา...", "ที่รัก? ...ไม่ใช่ ไม่ใช่เธอ แต่เธอก็พอได้"],
    "question": ["ถามทะเลสาบสิ มันเอาคำตอบของฉันไปหมดแล้ว", "ฉันจำได้แค่น้ำ กับการรอคอย"],
    "plead": ["เขาก็เคยอ้อนวอนแบบนี้ในความฝัน... แต่เขาไม่เคยมา", "ชู่... ชู่... น้ำนิ่งแล้ว ปล่อยให้มันพาเธอไป"],
    "bargain": ["ของขวัญให้เจ้าสาวเหรอ? {item}... มันไม่ใช่แหวนนี่", "ทะเลสาบไม่แลกเปลี่ยน มันเก็บไว้อย่างเดียว"],
    "taunt": ["หัวเราะเยาะเจ้าสาวสิ... ทุกคนก็ทำ", "ใต้น้ำเธอจะหัวเราะไม่ออกหรอก"],
    "other": ["'{echo}'... เขาเคยพูดแบบนั้น", "พูดเบา ๆ... น้ำกำลังฟังอยู่"],
    "success": ["ไปเถอะ... ก่อนฉันจะเปลี่ยนใจแล้วเก็บเธอไว้", "เธอทำให้ฉันนึกถึงเขา... ไปซะ เร็ว"],
    "rage": ["เธอต้องจมน้ำไปกับฉัน!", "ฉันจะกดเธอไว้ใต้น้ำจนเธอเงียบ!"],
    "who": ["เจ้าสาวที่ไม่มีใครมารับ", "ฉันรออยู่ริมทะเลสาบในชุดขาว... ยังรออยู่จนวันนี้"],
    "bye": ["อย่าทิ้งฉันไว้ที่หน้าแท่นพิธีอีกเลย", "ไปสิ... เดี๋ยวน้ำก็พาเธอกลับมาหาฉัน"],
    "kind": ["ไม่มีใครใจดีกับฉันมานานมากแล้ว... อยู่ด้วยกันนะ", "ใจดี... เหมือนคำสัญญาของเขา"],
    "fear": ["ไม่ต้องกลัว... มันเจ็บแค่ตอนหายใจเข้าครั้งแรก", "มือเธอเย็นจัง เหมือนมือฉันเลย"],
    "threat": ["เธอฆ่าคนที่จมน้ำตายไปแล้วไม่ได้หรอก", "ใช้ความรุนแรงในวันแต่งงานของฉันเหรอ?"],
    "why": ["เพราะเธอตัวคนเดียว... เหมือนฉัน", "เพราะฉันต้องการใครสักคนที่อยู่กับฉัน"],
    "want": ["เจ้าบ่าว แหวน มือสักข้างให้จับไว้ใต้น้ำ", "เธอ... อยู่กับฉัน ตลอดไป"],
    "ask_give": ["ของขวัญแต่งงาน... เอาไปเถอะ", "จากก้นทะเลสาบ... เป็นของเธอแล้ว", "รับไป... แล้วอย่าลืมฉัน"],
    "ask_refuse": ["ทะเลสาบไม่คายของนั้นออกมา", "คืนนี้ ฉันไม่ให้อะไรใครทั้งนั้น", "เขาก็ไม่เคยให้ฉันเหมือนกัน"],
    "ask_unknown": ["ใต้น้ำ... ฉันไม่ได้ยินชื่อนั้น", "คำนั้นจมหายไปแล้ว"],
    "ask_limit": ["ทะเลสาบไม่ให้อะไรเธออีกแล้ว", "เธอเอาไปจากฉันมากพอแล้ว..."],
}

EXTRA["en"][3] = {
    "greet": ["IT'S SO HOT IN HERE! ISN'T IT HOT?!", "YOU! COME CLOSER! I'M COLD INSIDE!"],
    "question": ["QUESTIONS DON'T PUT OUT FIRES!", "ASK THE SMOKE! IT WAS THERE!"],
    "plead": ["I BEGGED THEM TO OPEN THE DOOR! THEY DIDN'T!", "BEG! BEG LOUDER THAN THE FIRE!"],
    "bargain": ["{item}?! IT'LL MELT! EVERYTHING MELTS!", "I DON'T WANT THINGS! I WANT TO STOP BURNING!"],
    "taunt": ["LAUGH! I LAUGHED TOO, UNTIL THE ROOF FELL!", "YOU THINK THAT'S FUNNY?! FEEL THIS!"],
    "other": ["'{echo}'?! WHAT DOES THAT MEAN?! IT BURNS!", "TALK! TALK WHILE YOU STILL HAVE LIPS!"],
    "success": ["FINE! FINE! GO! BEFORE I CATCH YOU!", "THE FIRE'S DYING... GO WHILE IT IS..."],
    "rage": ["I'LL BURN YOU DOWN TO THE BONE!", "SMELL THAT?! THAT'S YOU SOON!"],
    "who": ["I WAS A FATHER! NOW I'M ASH!", "THE MAN WHO COULDN'T GET OUT!"],
    "bye": ["DON'T TURN YOUR BACK ON A FIRE!", "RUN! THE SMOKE IS FASTER!"],
    "kind": ["KIND?! KINDNESS DOESN'T COOL ANYTHING!", "...nobody... said that... IT STILL BURNS!"],
    "fear": ["GOOD! FEAR IS HOT! I CAN FEEL IT!", "YOUR SWEAT IS BOILING!"],
    "threat": ["YOU CAN'T BURN WHAT'S ALREADY ASH!", "HIT ME! I'LL CRUMBLE AND COME BACK!"],
    "why": ["BECAUSE YOU'RE WARM AND I'M NOT!", "BECAUSE NOBODY CAME FOR ME!"],
    "want": ["WATER! NO! YOU! YOUR WARMTH!", "TO STOP BURNING! AND YOU TO START!"],
    "ask_give": ["TAKE IT! IT'S STILL HOT!", "FROM THE ASHES! TAKE IT!", "IT BURNED ME FOR YEARS! YOUR TURN!"],
    "ask_refuse": ["NO! I GIVE NOTHING!", "ASK AND IT TURNS TO SMOKE!", "NOTHING SURVIVES THE FIRE!"],
    "ask_unknown": ["WHAT IS THAT?! THE FIRE ATE IT!", "SAY IT CLEARER!"],
    "ask_limit": ["NO! NO MORE! BURN!", "ENOUGH! NOW YOU PAY!"],
}
EXTRA["th"][3] = {
    "greet": ["ร้อนชะมัด! ไม่ร้อนเหรอ?!", "เธอ! เข้ามาใกล้ ๆ! ข้างในฉันหนาว!"],
    "question": ["คำถามดับไฟไม่ได้!", "ไปถามควันสิ! มันอยู่ที่นั่นด้วย!"],
    "plead": ["ฉันก็อ้อนวอนให้พวกมันเปิดประตู! ไม่มีใครเปิด!", "อ้อนวอนสิ! ให้ดังกว่าเสียงไฟ!"],
    "bargain": ["{item}?! เดี๋ยวก็ละลาย! ทุกอย่างละลาย!", "ฉันไม่ได้อยากได้ของ! ฉันอยากหยุดไหม้!"],
    "taunt": ["หัวเราะสิ! ฉันก็เคยหัวเราะ จนหลังคาถล่ม!", "ขำนักเหรอ?! ลองนี่หน่อย!"],
    "other": ["'{echo}'?! หมายความว่าไง?! มันแสบ!", "พูดไป! พูดตอนที่ยังมีปากอยู่!"],
    "success": ["ก็ได้! ก็ได้! ไป! ก่อนฉันจะจับได้!", "ไฟกำลังมอด... ไปตอนที่มันยังมอดอยู่..."],
    "rage": ["ฉันจะเผาเธอจนเหลือแต่กระดูก!", "ได้กลิ่นไหม?! อีกเดี๋ยวนั่นคือเธอ!"],
    "who": ["ฉันเคยเป็นพ่อคน! ตอนนี้เป็นเถ้า!", "ผู้ชายที่ออกจากบ้านไม่ทัน!"],
    "bye": ["อย่าหันหลังให้ไฟ!", "วิ่งสิ! ควันเร็วกว่า!"],
    "kind": ["ใจดีเหรอ?! ความใจดีทำให้อะไรเย็นลงไม่ได้!", "...ไม่เคยมีใคร... พูดแบบนั้น... มันยังไหม้อยู่!"],
    "fear": ["ดี! ความกลัวมันร้อน! ฉันรู้สึกได้!", "เหงื่อเธอเดือดแล้ว!"],
    "threat": ["เธอเผาสิ่งที่เป็นเถ้าแล้วไม่ได้!", "ตีมาสิ! ฉันจะแตกเป็นผงแล้วกลับมา!"],
    "why": ["เพราะเธออุ่น แต่ฉันไม่!", "เพราะไม่มีใครมาช่วยฉัน!"],
    "want": ["น้ำ! ไม่สิ! เธอ! ความอุ่นของเธอ!", "หยุดไหม้! แล้วให้เธอไหม้แทน!"],
    "ask_give": ["เอาไป! มันยังร้อนอยู่", "จากกองเถ้า... รับไปซะ", "ของนี้ไหม้ฉันมานานแล้ว! เอาไป"],
    "ask_refuse": ["ไม่! ฉันไม่ให้", "ขอไปก็กลายเป็นควัน", "ไม่มีอะไรเหลือรอดจากไฟ"],
    "ask_unknown": ["ฉันไม่รู้จักสิ่งนั้น! ไฟกินมันไปแล้ว", "พูดให้ชัดกว่านี้!"],
    "ask_limit": ["ไม่! ไม่มีอีกแล้ว! ไหม้ไปให้หมด!", "พอแล้ว! ต่อไปเธอต้องจ่ายเอง!"],
}

EXTRA["en"][4] = {
    "greet": ["hiii :) i've been waiting right behind you", "you're awake! i love when they're awake :)"],
    "question": ["so many questions! ask me in the dark :)", "the answer is behind you :)"],
    "plead": ["say please again :) it tickles", "begging is so cute on you :)"],
    "bargain": ["a present? for me? :) still no", "{item}! i'll put it with the others :)"],
    "taunt": ["ooh, feisty :) i'll remember your face", "rude! but i'm still smiling :)"],
    "other": ["'{echo}' :) say it slower", "mm-hm :) go on, i'm counting your words"],
    "success": ["okay okay :) i'll go. for now. maybe", "you win :) this time. turn off the lights on your way"],
    "rage": ["SMILE. SMILE FOR ME.", "I'LL GIVE YOU A SMILE LIKE MINE."],
    "who": ["your best friend :) i've always been here", "the smile in the dark corner :)"],
    "bye": ["bye! check under the bed tonight :)", "see you! well, i'll see you :)"],
    "kind": ["you're so nice :) i'll eat you last", "aww :) i'll keep you"],
    "fear": ["shh shh :) fear makes you taste sweeter", "your knees are knocking :) cute"],
    "threat": ["try it :) i have more arms than you", "oh no! :) anyway"],
    "why": ["because you're fun :)", "why not? :) you were alone"],
    "want": ["to play :) hide and seek. you hide", "a smile like mine, on you :)"],
    "ask_give": ["sure :) i'm always nice", "here you go :) i'll collect the price later", "take it :) don't forget to smile"],
    "ask_refuse": ["nope :) funny, right?", "not this time :) try again", "i smile, but i don't share :)"],
    "ask_unknown": ["hm? is that even real? :)", "i don't get it :) cute though"],
    "ask_limit": ["that's three :) my turn to take something", "all gone :) but i still want something from you"],
}
EXTRA["th"][4] = {
    "greet": ["หวัดดีจ้า :) ฉันรออยู่ข้างหลังเธอมาตลอดเลย", "เธอตื่นแล้ว! ฉันชอบตอนที่พวกเขาตื่นที่สุด :)"],
    "question": ["คำถามเยอะจัง! มาถามฉันในที่มืดสิ :)", "คำตอบอยู่ข้างหลังเธอนะ :)"],
    "plead": ["พูดว่าได้โปรดอีกทีสิ :) มันจั๊กจี้ดี", "อ้อนวอนแบบนี้น่ารักจังเลย :)"],
    "bargain": ["ของขวัญ? ให้ฉันเหรอ? :) แต่ก็ยังไม่เอานะ", "{item}! เดี๋ยวฉันเก็บไว้กับชิ้นอื่น ๆ :)"],
    "taunt": ["โอ้โห ดุจัง :) ฉันจะจำหน้าเธอไว้", "หยาบคาย! แต่ฉันก็ยังยิ้มอยู่นะ :)"],
    "other": ["'{echo}' :) พูดช้า ๆ อีกทีสิ", "อืมมม :) พูดต่อเลย ฉันนับคำของเธออยู่"],
    "success": ["โอเค ๆ :) ฉันไปก็ได้ ตอนนี้นะ อาจจะ", "เธอชนะ :) ครั้งนี้ ปิดไฟให้ด้วยนะตอนออกไป"],
    "rage": ["ยิ้ม ยิ้มให้ฉันดูสิ", "ฉันจะให้เธอมีรอยยิ้มแบบฉัน"],
    "who": ["เพื่อนรักของเธอไง :) ฉันอยู่ที่นี่มาตลอด", "รอยยิ้มในมุมมืดไง :)"],
    "bye": ["บ๊ายบาย! คืนนี้อย่าลืมดูใต้เตียงนะ :)", "แล้วเจอกัน! ...ฉันจะเห็นเธอแหละ :)"],
    "kind": ["เธอใจดีจัง :) ฉันจะกินเธอเป็นคนสุดท้าย", "อุ๊ย :) ฉันจะเก็บเธอไว้"],
    "fear": ["ชู่ ๆ :) ความกลัวทำให้เธอหวานขึ้นนะ", "เข่าเธอสั่นกึก ๆ เลย :) น่ารัก"],
    "threat": ["ลองดูสิ :) ฉันมีแขนมากกว่าเธอนะ", "ตายแล้ว! :) ช่างเถอะ"],
    "why": ["ใครบอกว่าฉันไล่ล่ะ :) ฉันแค่เดินเล่นข้างหลังเธอ", "เพราะเธอน่ารักไง :) ฉันอยากได้"],
    "want": ["เล่นด้วยกัน :) ซ่อนแอบไง เธอซ่อน", "รอยยิ้มแบบฉัน บนหน้าเธอ :)"],
    "ask_give": ["ได้สิ :) ฉันใจดีเสมอ", "นี่ไง :) ราคาเดี๋ยวฉันเก็บทีหลัง", "รับไปเลย :) อย่าลืมยิ้มนะ"],
    "ask_refuse": ["ไม่ให้หรอก :) ขำดีใช่ไหม", "ครั้งนี้ไม่ :) ลองใหม่สิ", "ฉันยิ้ม แต่ไม่ให้ :)"],
    "ask_unknown": ["หืม? สิ่งนั้นมีอยู่จริงเหรอ :)", "ไม่เข้าใจเลย :) น่ารักจัง"],
    "ask_limit": ["ครบสามแล้วนะ :) ทีนี้ถึงตาฉันเอาบ้าง", "ไม่มีให้แล้ว :) แต่ฉันยังอยากได้ของจากเธอ"],
}

EXTRA["en"][5] = {
    "greet": ["...smell.", "...you. ...food."],
    "question": ["...hungry.", "...no words. ...hungry."],
    "plead": ["...food begs.", "...quiet. ...eat."],
    "bargain": ["...{item}. ...not meat.", "...not that. ...you."],
    "taunt": ["...loud food.", "...remember. ...eat later."],
    "other": ["...{echo}?", "...words. ...hungry."],
    "success": ["...full. ...for now.", "...go. ...hungry later."],
    "rage": ["...EAT!!", "HUNGRY! HUNGRY! HUNGRY!"],
    "who": ["...forest. ...hunger.", "...was man. ...now hungry."],
    "bye": ["...run. ...i follow.", "...smell stays."],
    "kind": ["...kind. ...still hungry.", "...soft. ...tender."],
    "fear": ["...fear. ...sweet.", "...heart fast. ...good."],
    "threat": ["...no.", "...try."],
    "why": ["...hungry.", "...you ran. ...i follow."],
    "want": ["...food.", "...you. ...hungry."],
    "ask_give": ["...take.", "...thing. ...take. ...stop looking.", "...yes. ...but hungry."],
    "ask_refuse": ["...no.", "...hungry. ...not kind.", "...nothing. ...stand still."],
    "ask_unknown": ["...don't know...", "...what word..."],
    "ask_limit": ["...gone.", "...no more. ...eat."],
}
EXTRA["th"][5] = {
    "greet": ["...กลิ่น.", "...เธอ. ...อาหาร."],
    "question": ["...หิว.", "...ไม่มีคำ. ...หิว."],
    "plead": ["...อาหารอ้อนวอน.", "...เงียบ. ...กิน."],
    "bargain": ["...{item}. ...ไม่ใช่เนื้อ.", "...ไม่เอา. ...เอาเธอ."],
    "taunt": ["...อาหารเสียงดัง.", "...จำไว้. ...กินทีหลัง."],
    "other": ["...{echo}?", "...คำพูด. ...หิว."],
    "success": ["...อิ่ม. ...ตอนนี้.", "...ไป. ...เดี๋ยวหิวอีก."],
    "rage": ["...กิน!!", "หิว! หิว! หิว!"],
    "who": ["...ป่า. ...ความหิว.", "...เคยเป็นคน. ...ตอนนี้หิว."],
    "bye": ["...วิ่ง. ...ฉันตาม.", "...กลิ่นยังอยู่."],
    "kind": ["...ใจดี. ...ยังหิว.", "...นุ่ม. ...เนื้อนุ่ม."],
    "fear": ["...กลัว. ...หวาน.", "...หัวใจเร็ว. ...ดี."],
    "threat": ["...ไม่.", "...ลองสิ."],
    "why": ["...หิว.", "...เธอวิ่ง. ...ฉันตาม."],
    "want": ["...อาหาร.", "...เธอ. ...หิว."],
    "ask_give": ["...เอาไป...", "ของ... เอาไป... แล้วหยุดมอง", "ได้... แต่ฉันหิว"],
    "ask_refuse": ["...ไม่", "ฉันหิว... ไม่ใช่ใจดี", "ไม่มีให้... ยืนนิ่ง ๆ"],
    "ask_unknown": ["...ไม่รู้จัก...", "ฉันไม่เข้าใจคำนั้น..."],
    "ask_limit": ["...หมดแล้ว.", "...ไม่ให้. ...กิน."],
}

# ------------------------------------------------------------------ keywords for intent detection (from talk.js)
INTENT_KW = {
    "items": ["diamond", "gold", "emerald", "netherite", "iron", "bread", "food", "soul", "เพชร", "ทอง", "มรกต", "นีเธอไรต์", "เหล็ก", "ขนมปัง", "อาหาร", "วิญญาณ"],
    "taunt": ["stupid", "idiot", "ugly", "loser", "weak", "noob", "trash", "garbage", "dumb", "moron", "coward", "pathetic", "useless", "lame", "fake", "cringe",
              "scrub", "clown", "shut up", "stfu", "fuck", "shit", "bitch", "asshole", "dickhead", "not scared", "not afraid", "don't scare", "dont scare",
              "come at me", "bad ghost", "you suck", "you're a joke", "ez", "lol", "lmao", "haha", "hahaha", "hahahaha",
              "ผีโง่", "ไอ้ผี", "ไอผี", "ไอ้กาก", "ไอกาก", "ไอ้โง่", "ไอโง่", "ไอ้ควาย", "ไอควาย", "ไอ้ห่า", "ไอ้สัส", "ไอ้เหี้ย", "ไอ้บ้า", "ไอ้ขยะ", "ไอ้ขี้ขลาด",
              "โง่", "ควาย", "กาก", "ห่วย", "น่าเกลียด", "อ่อนแอ", "อ่อนหัด", "ขี้ขลาด", "ไร้ค่า", "ไร้สาระ", "ขี้แพ้", "ปัญญาอ่อน", "สมองกลวง", "กวนตีน", "เหี้ย",
              "สัส", "ห่า", "ไปตาย", "ขยะ", "ปลอม", "ไม่กลัว", "ไม่เห็นน่ากลัว", "ไม่น่ากลัว", "หุบปาก", "เงียบไปเลย", "มึง", "555", "ฮ่าๆ", "ฮ่า ๆ"],
    "threat": ["kill you", "i will kill", "i'll kill", "destroy you", "burn you", "exorcise", "end you", "fight me", "slay", "banish", "get rid of you",
               "ฆ่า", "กำจัด", "ทำลาย", "ไล่ผี", "มาสู้", "สู้กัน", "จะเผา", "ฟัน"],
    "bargain": ["deal", "trade", "offer", "give you", "i'll give", "ill give", "gift", "bribe", "ข้อเสนอ", "แลก", "ของขวัญ", "สินบน", "ข้อตกลง", "จะให้"],
    "why": ["why are you", "why do you", "why me", "ทำไม", "เพราะอะไร", "เพราะไร"],
    "want": ["what do you want", "what you want", "ต้องการอะไร", "อยากได้อะไร", "อยากทำอะไร", "จะเอาอะไร", "ต้องการอะไรจากฉัน"],
    "who": ["who are you", "your name", "what are you", "what is your name", "เป็นใคร", "ชื่ออะไร", "คือใคร", "คืออะไร"],
    "bye": ["bye", "goodbye", "see you", "gtg", "farewell", "ลาก่อน", "บาย", "ไปแล้วนะ", "แล้วเจอกัน"],
    "kind": ["lonely", "sad", "poor thing", "understand", "feel for you", "you're not alone", "love you", "be friends", "sorry for you", "rest in peace", "rip",
             "peace", "comfort", "hug", "เหงา", "เสียใจ", "เข้าใจ", "ใจดี", "อ่อนโยน", "น่ารัก", "เป็นเพื่อน", "ไม่ต้องกลัว", "พักผ่อน", "หลับให้สบาย", "สงสาร", "กอด", "รัก"],
    "plead": ["please", "pls", "plz", "sorry", "forgive", "mercy", "spare", "leave me", "go away", "let me go", "don't hurt", "dont hurt", "stop", "help", "beg",
              "ขอร้อง", "ได้โปรด", "ขอโทษ", "ปล่อย", "อย่าทำร้าย", "ไปเถอะ", "ไปที", "หยุด", "ช่วย", "ขอ"],
    "fear": ["scared", "afraid", "terrified", "creepy", "fear", "กลัว", "น่ากลัว", "ขนลุก"],
    "question": ["who", "what", "why", "where", "how", "name", "are you", "?", "เป็นใคร", "ใคร", "อะไร", "ทำไม", "ที่ไหน", "ยังไง", "อย่างไร", "ชื่อ", "ไหม"],
    "greet": ["hi", "hello", "hey", "yo", "good evening", "สวัสดี", "หวัดดี", "ไง", "เฮ้"],
}


def merged_lines():
    out = {}
    for lang in ("en", "th"):
        out[lang] = {}
        for g in range(1, 6):
            cur = {k: list(v) for k, v in base["lines"][lang][str(g)].items()}
            for k, v in EXTRA[lang][g].items():
                cur.setdefault(k, [])
                for line in v:
                    if line not in cur[k]:
                        cur[k].append(line)
            out[lang][str(g)] = cur
    return out


def main():
    lines = merged_lines()
    # sanity: every intent the code can ask for exists in both languages
    need = ["greet", "question", "plead", "bargain", "taunt", "other", "success", "rage", "who", "bye", "kind", "fear", "threat", "repeat",
            "plead_many", "taunt_many", "hurt", "cave", "water", "night", "nothave", "refuse", "why", "want",
            "ask_give", "ask_refuse", "ask_unknown", "ask_limit"]
    for lang in lines:
        for g in lines[lang]:
            for k in need:
                assert lines[lang][g].get(k), (lang, g, k)
    data = {
        "lines": lines,
        "topics": {"en": base["topics_en"], "th": base["topics_th"]},
        "topic_kw": base["topic_kw"],
        "topic_order": base["topic_order"],
        "intent_kw": INTENT_KW,
        "ghosts": {str(n): {"name": {"en": NAMES["en"][n], "th": NAMES["th"][n]}, "persona": PERSONA[n]} for n in range(1, 6)},
    }
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=1)
    total = sum(len(v) for lang in lines.values() for g in lang.values() for v in g.values())
    print("dialogue.json:", total, "lines")


if __name__ == "__main__":
    main()
