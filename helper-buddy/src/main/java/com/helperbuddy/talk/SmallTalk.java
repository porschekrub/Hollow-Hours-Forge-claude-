package com.helperbuddy.talk;

import java.util.Random;

/** Small talk and a little Minecraft knowledge (pure Java, Thai + English). */
public final class SmallTalk {
    private SmallTalk() {}

    static final Random R = new Random();

    public record Ctx(String name, int hp, int max, int level, String mode, double hour, long day) {}

    static String pick(String... a) {
        return a[R.nextInt(a.length)];
    }

    static final String[][] JOKES = {
            {"ทำไมครีปเปอร์ไม่มีเพื่อน? เพราะมันชอบ 'ระเบิด' อารมณ์ใส่ทุกคนไงครับ 555", "Why don't creepers have friends? They always blow up at people."},
            {"ซอมบี้ไปสมัครงาน ถามว่าเก่งอะไร ตอบว่า 'ผมถนัดเดินตามคนอื่นครับ'", "A zombie went to a job interview. Best skill? 'Following people around.'"},
            {"ผมไม่กลัวมอนสเตอร์หรอกนะ แต่กลัวคุณลืมเอาอาหารมาให้ผมมากกว่า 555", "I'm not scared of monsters. I'm scared you'll forget to feed me."},
            {"เอนเดอร์แมนไม่ชอบให้ใครมองตา เพราะมันขี้อายครับ", "Endermen hate eye contact. They're just shy."},
            {"ทำไมโครงกระดูกไม่ไปปาร์ตี้? เพราะไม่มี 'ใคร' ไปด้วยสักคน (no body)", "Why didn't the skeleton go to the party? It had no body to go with."},
            {"ชาวบ้านพูดแต่ 'ฮึม' ทั้งวัน ผมว่าเขาคงมีความลับเยอะ", "Villagers say 'hmm' all day. They must have a lot of secrets."},
    };

    /** [subject regex, question regex, Thai answer, English answer] */
    static final String[][] KB = {
            {"(เพชร|diamond)", "(หา|ที่ไหน|ยังไง|อยู่|เจอ|where|find|how)", "เพชรอยู่ลึกมาก ดีสุดที่ §eY -59§r (ช่วง -64 ถึง -50) ต้องใช้อีเต้อเหล็กขึ้นไปขุด สั่งผมว่า \"ขุดเพชร\" หรือ \"สร้างเหมืองหาเพชร\" ได้เลย",
                    "Diamonds are deep: best at §eY -59§r (between -64 and -50). You need an iron pickaxe or better. Tell me \"mine diamonds\" or \"build a mine\""},
            {"(เศษโบราณ|ancient debris|netherite|เนเธอไรท์|เนเธอร์ไรท์)", ".", "เศษโบราณอยู่ในเนเธอร์ ช่วง §eY 8-22§r (ดีสุดที่ Y 15) สั่งผม \"ขุดเศษโบราณ\" ได้ ต้องใช้อีเต้อเพชรขึ้นไป",
                    "Ancient debris is in the Nether around §eY 8-22§r (best at 15). Tell me \"mine ancient debris\"; it needs a diamond pickaxe"},
            {"(ทองแดง|copper)", ".", "ทองแดงเจอเยอะช่วง §eY 48§r (ในถ้ำก็เจอ) ใช้อีเต้อหินขึ้นไปก็ขุดได้", "Copper is common around §eY 48§r and in caves; a stone pickaxe is enough"},
            {"(เหล็ก|iron)", "(หา|ที่ไหน|ยังไง|อยู่|เจอ|where|find)", "เหล็กเจอมากที่ §eY 16§r และบนภูเขา (Y 232) ใช้อีเต้อหินขึ้นไปขุด แล้วต้องถลุง (สั่งผมว่า \"ถลุงเหล็ก\" ได้)",
                    "Iron is most common at §eY 16§r and high in mountains. Stone pickaxe or better, then smelt it (tell me \"smelt iron\")"},
            {"(ทอง(?!แดง)|gold)", "(หา|ที่ไหน|ยังไง|อยู่|เจอ|where|find)", "ทองเจอมากที่ §eY -16§r และในภูเขาเมซา ต้องใช้อีเต้อเหล็กขึ้นไป", "Gold is most common at §eY -16§r and in badlands; needs an iron pickaxe"},
            {"(เรดสโตน|redstone)", "(หา|ที่ไหน|ยังไง|อยู่|เจอ|where|find)", "เรดสโตนอยู่ลึกใกล้เพชร ช่วง §eY -59§r ถึง -64", "Redstone is deep, near diamonds: §eY -59§r to -64"},
            {"(ลาพิส|lapis)", ".", "ลาพิสเจอมากที่ §eY 0§r (ช่วง -64 ถึง 64) ใช้อีเต้อหินขึ้นไป", "Lapis is most common at §eY 0§r; stone pickaxe or better"},
            {"(มรกต|emerald)", ".", "มรกตเจอเฉพาะในไบโอมภูเขา (ที่สูงๆ) เท่านั้น", "Emeralds only appear in mountain biomes"},
            {"(ประตูเนเธอร์|nether portal|portal|พอร์ทัล|ไปเนเธอร์|ไปนรก)", ".", "ประตูเนเธอร์: กรอบออบซิเดียนกว้าง 4 สูง 5 (ขั้นต่ำ 10 ก้อน) แล้วจุดไฟ หรือให้ผมสร้างให้: \"สร้างประตูเนเธอร์\" (ส่งออบซิเดียน 10 + หินเหล็กไฟให้ผมก่อน)",
                    "Nether portal: an obsidian frame 4 wide and 5 tall (10 blocks at least), then light it. Or tell me \"build a nether portal\" (give me 10 obsidian + flint and steel)"},
            {"(โต๊ะร่ายมนตร์|enchant)", "(ยังไง|ทำ|เลเวล|30|how)", "โต๊ะร่ายมนตร์เลเวล 30 ต้องมีชั้นหนังสือ §e15 ชั้น§r วางห่างจากโต๊ะ 1 ช่อง", "For level 30 enchantments put §e15 bookshelves§r one block away from the table"},
            {"(เตียง|bed)", "(ทำ|สูตร|คราฟ|craft|how)", "เตียง = ขนแกะสีเดียวกัน 3 + ไม้กระดาน 3 (สั่งผมว่า \"คราฟเตียง\" ก็ได้)", "A bed = 3 wool of one colour + 3 planks (or tell me \"craft a bed\")"},
            {"(ฟาร์ม|farm)", "(ข้าวสาลี|ผัก|ทำ|ยังไง|how)", "ฟาร์มพืช: ไถดินข้างน้ำ (ไม่เกิน 4 ช่อง) แล้วหว่านเมล็ด สั่งผมว่า \"ทำฟาร์ม\" ผมจะไถ ปลูก และเก็บเกี่ยวให้",
                    "Crop farm: till dirt within 4 blocks of water and plant seeds. Tell me \"farm\" and I'll till, plant and harvest"},
            {"(ครีปเปอร์|creeper)", ".", "เจอครีปเปอร์ผมจะตีแล้วถอยออกมา (หรือยิงธนูถ้ามี) ไม่ปล่อยให้มันระเบิดใกล้ๆ หรอก", "Against creepers I hit and back off (or shoot if I have a bow) so they never blow up next to us"},
            {"(กันมอน|มอนสเตอร์|monster)", "(เกิด|กัน|spawn|ยังไง|how)", "มอนสเตอร์เกิดในที่มืด สั่งผมว่า \"ปักคบเพลิง\" ผมจะปักไฟรอบๆ ให้สว่าง", "Monsters spawn in the dark. Tell me \"place torches\" and I'll light up the area"},
    };

    static boolean m(String re, String t) {
        return Nlu.test(re, t);
    }

    static String hhmm(double hour) {
        int h = (int) Math.floor(hour);
        int mm = (int) Math.floor((hour - h) * 60);
        return String.format("%02d:%02d", h, mm);
    }

    /** A reply to casual talk / questions, or null when there is nothing to say. */
    public static String reply(String t, Ctx c, boolean th) {
        for (String[] k : KB) if (m(k[0], t) && m(k[1], t)) return th ? k[2] : k[3];
        if (m("(ชื่ออะไร|ชื่อไร|เธอคือใคร|นายคือใคร|คุณคือใคร|เป็นใคร|who are you|your name|what are you)", t))
            return th ? "ผมชื่อ §e" + c.name + "§r เป็นผู้ช่วยของคุณ ขุดแร่ ตัดไม้ สร้างเหมือง สร้างบ้านได้หลายแบบ หาอาหาร ทำฟาร์ม คราฟต์ของ และช่วยสู้ ตอนนี้เลเวล " + c.level + " เลือด " + c.hp + "/" + c.max
                    : "I'm §e" + c.name + "§r, your helper. I mine, chop, dig mines, build all kinds of buildings, find food, farm, craft and fight. Level " + c.level + ", health " + c.hp + "/" + c.max;
        if (m("(สบายดีไหม|สบายดีมั้ย|เป็นไงบ้าง|เป็นยังไงบ้าง|how are you|ไหวไหม|ไหวมั้ย|เหนื่อยไหม|เหนื่อยมั้ย|are you ok|are you tired)", t)) {
            boolean low = c.hp < c.max * 0.4;
            if (th) return low ? "ผมเริ่มเหนื่อยแล้ว เลือด " + c.hp + "/" + c.max + " ขออาหารหน่อยนะ" : "ผมสบายดี เลือด " + c.hp + "/" + c.max + " ตอนนี้กำลัง" + c.mode + " พร้อมลุยต่อครับ";
            return low ? "I'm getting tired, health " + c.hp + "/" + c.max + ". Some food would be great" : "I'm fine! Health " + c.hp + "/" + c.max + ", " + c.mode + " right now. Ready for more";
        }
        if (m("(กี่โมง|เวลาเท่าไหร่|เวลาเท่าไร|ตอนนี้เวลา|what time|มืดยัง|สว่างยัง|กลางคืนยัง|ค่ำยัง|เช้ายัง|วันที่เท่าไหร่|วันที่เท่าไร|กี่วันแล้ว|what day)", t)) {
            boolean night = c.hour >= 19 || c.hour < 5.5;
            String phTh = night ? "กลางคืน (มอนสเตอร์ออกมา ระวังด้วย)" : c.hour < 7 ? "เช้ามืด" : c.hour < 17.5 ? "กลางวัน" : "เย็นแล้ว";
            String phEn = night ? "night (monsters are out, be careful)" : c.hour < 7 ? "dawn" : c.hour < 17.5 ? "daytime" : "evening";
            return th ? "ตอนนี้ในเกมเวลา §e" + hhmm(c.hour) + "§r " + phTh + " • วันที่ " + (c.day + 1) + " ของโลก" : "It's §e" + hhmm(c.hour) + "§r in game, " + phEn + " • day " + (c.day + 1);
        }
        if (m("(เล่าเรื่องตลก|มุกตลก|ขำๆ|ตลก|มุก|joke|funny|ทำให้หัวเราะ)", t)) {
            String[] j = JOKES[R.nextInt(JOKES.length)];
            return th ? j[0] : j[1];
        }
        if (m("(เบื่อ|ไม่มีอะไรทำ|ทำอะไรดี|แนะนำหน่อย|ควรทำอะไร|what should i do|i'm bored|im bored|bored)", t)) {
            if (c.hour >= 19 || c.hour < 5.5)
                return th ? "ตอนนี้กลางคืนแล้ว ลองให้ผม \"สร้างบ้านหลบภัย\" แล้วนอนรอเช้า หรือให้ผมขุดแร่ใต้ดินแทน ปลอดภัยกว่า"
                        : "It's night. Let me \"build a safe house\" and sleep it off, or send me mining underground";
            if (c.hp < c.max * 0.5) return th ? "เลือดผมเหลือ " + c.hp + "/" + c.max + " ให้อาหารผมก่อนนะ แล้วค่อยไปลุยกัน" : "My health is " + c.hp + "/" + c.max + ". Feed me first, then let's go";
            return th ? pick("ลองสั่ง \"ตัดไม้ 30 ท่อน แล้วขุดหิน 64 ก้อน แล้วเอามาให้\" ดูไหม", "ลองสร้างอะไรสักอย่าง เช่น \"โรงแรม\" \"ปราสาท\" \"บ้านต้นไม้\" หรือ \"รูปปั้นครีปเปอร์\"",
                    "ไปขุดเพชรกัน! สั่ง \"สร้างเหมืองหาเพชร\"", "ให้ผมทำฟาร์มให้ไหม สั่ง \"ทำฟาร์ม\" ได้เลย")
                    : pick("Try \"chop 30 logs then mine 64 stone then bring it to me\"", "Let's build something: \"a hotel\", \"a castle\", \"a tree house\" or \"a creeper statue\"",
                    "Let's get diamonds! Say \"build a mine for diamonds\"", "Want a farm? Say \"farm\" and I'll start one");
        }
        if (m("(โง่|ห่วย|ไม่เก่ง|ไม่ฉลาด|ใช้ไม่ได้|stupid|dumb|useless)", t))
            return th ? "ขอโทษครับ ผมยังเรียนรู้อยู่ ลองบอกผมอีกที หรือพิมพ์ \"ช่วย\" ดูตัวอย่างคำสั่ง" : "Sorry, I'm still learning. Try saying it another way, or type \"help\" for examples";
        if (m("(รักนะ|รักนาย|รักเธอ|ชอบนาย|ชอบเธอ|love you|like you)", t))
            return th ? pick("ขอบคุณครับ ผมก็ชอบทำงานกับคุณมากเลย", "อิอิ เขินเลย ไปทำงานต่อกันเถอะ") : pick("Thanks! I love working with you too", "Aww. Back to work, partner!");
        if (m("(ขอโทษ|โทษที|sorry)", t)) return th ? "ไม่เป็นไรครับ" : "No problem!";
        if (m("(บาย|ลาก่อน|ไปก่อน|ฝันดี|ไปนอน|นอนก่อน|\\bbye\\b|good night|see you)", t))
            return th ? "แล้วเจอกันนะครับ ผมจะรอตรงนี้ ถ้ามีงานค้างผมทำต่อให้" : "See you! I'll keep working on whatever is left";
        if (m("(คุยด้วย|คุยหน่อย|คุยเป็นเพื่อน|เหงา|lonely|talk to me|chat)", t))
            return th ? "ผมอยู่นี่ครับ คุยได้เลย ถามอะไรเกี่ยวกับเกมก็ได้ เช่น \"เพชรอยู่ตรงไหน\" หรือ \"ทำประตูเนเธอร์ยังไง\""
                    : "I'm here! Ask me anything about the game, like \"where are diamonds\" or \"how do I make a nether portal\"";
        if (m("(ฉลาด|เก่งจัง|เก่งมาก|clever|smart)", t)) return th ? "ขอบคุณครับ ผมพยายามอยู่ ฮ่าๆ" : "Thanks, I try my best!";
        return null;
    }
}
