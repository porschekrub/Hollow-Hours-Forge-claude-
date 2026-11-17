import { world, system, ItemStack } from "@minecraft/server";
import { ActionFormData, ModalFormData } from "@minecraft/server-ui";
import { glitchName } from "./bookname.js";
import { findTopic, TOPIC_LINES, timeText, dirText, doingText } from "./topics.js";
import { isHidden, canSee } from "./hide.js";
import { getSoul, soulTier, soulBar, TIER_NAME } from "./soul.js";
import { flashStatus, fmtCharge } from "./flashlight.js";
import { SIZE, PROFILE } from "./body.js";
import { getLang, isEn, tx, cap, openLangMenu } from "./lang.js";

// language is chosen at runtime (see lang.js): "th" or "en"
function dyn(get) {
  return new Proxy({}, {
    get: (_, k) => get()[k],
    has: (_, k) => k in get(),
    ownKeys: () => Reflect.ownKeys(get()),
    getOwnPropertyDescriptor: (_, k) => { const d = Object.getOwnPropertyDescriptor(get(), k); if (d) d.configurable = true; return d; },
  });
}
const UI2 = false;                     // true when @minecraft/server-ui is 2.x (new dropdown/textField options)                 // "en" or "th" (the edition only changes the reply language)
const SUCCESS_CHANCE = 0.05;             // chance a negotiation attempt works
const RAGE_CHANCE = 0.25;                // chance a failed attempt enrages the ghost
const COOLDOWN = 40;                     // ticks between messages to the same ghost (2 seconds)
const GUIDE_ID = "ghost:ghost_guide";

export const GHOSTS = [
  { n: 1, key: "wail", name: "The Wailing Dweller", color: "§7" },
  { n: 2, key: "widow", name: "Sunken Widow", color: "§3" },
  { n: 3, key: "ash", name: "Ashen Screamer", color: "§6" },
  { n: 4, key: "void", name: "Smiling Void", color: "§5" },
  { n: 5, key: "stag", name: "The Antlered Hunger", color: "§f" },
];

// ================= TEXT =================
const TEXT = {
  en: {
    guideTitle: "§4§lGhost Guide",
    intro: "Five ghosts hunt in this world. This guide tells you who they are and how to talk to them.\n\n§7You can only talk to a ghost while it is here (spawned).",
    btnTalk: "§lTalk to a ghost", btnHow: "How to talk & negotiate", btnBack: "Back", btnHouse: "§4Doors, windows & barricades",
    houseTitle: "Doors, windows & barricades",
    house: [
      "§l§cWhen you hide indoors§r",
      "• Ghosts come to your doors and windows. Sometimes they only knock and vanish. Sometimes they smash their way in.",
      "• Every hit cracks the door more until it bursts apart (no item drops).",
      "• Strength: wood (weakest) < copper < iron (strongest). Barricades too: wood < stone < iron.",
      "• Each ghost has its own strength: most break only wood, some break copper/stone, and rarely a brutal one breaks iron. Enraged ghosts are stronger.",
      "• It always goes for a door or window first, however strong it is. If it can't break it, sometimes it smashes through the wall instead, otherwise it gives up and leaves.",
      "• The harder the block, the longer it takes: dirt and wood break fast, stone, bricks, deepslate and metal blocks hold much longer.",
      "• Glass shatters after 3 hits. A giant ghost squeezes through the hole; sometimes it gets stuck and slowly smashes the frame.",
      "",
      "§l§cIt finds a real way in§r",
      "• It walks, runs up stairs, jumps down and climbs ledges. Each ghost climbs a different height.",
      "• It only breaks what is really in its way: an open path first, then doors, windows and boards. Walls, floors and ceilings only when there is no opening at all, or after it fails at your door.",
      "• It never breaks your stairs, chests or beds.",
      "",
      "§l§cRepair kits (the only way to fix cracks — mining a cracked block makes it crumble)§r",
      "• Wood kit: 2 planks + 1 stick + 1 iron nugget (wooden doors, trapdoors, gates, wood barricades)",
      "• Copper kit: 2 copper ingots + 1 iron nugget + 1 stick",
      "• Iron kit: 2 iron ingots + 2 iron nuggets + 1 stick (iron doors, iron barricades)",
      "• Stone mortar (x2): 2 cobblestone + 1 clay ball + 1 gravel (stone barricades)",
      "• Glass putty (x2): 1 glass pane + 1 sand + 1 clay ball",
      "• Hold the kit and right-click the cracked block.",
      "",
      "§l§cBarricade boards (8 per craft)§r",
      "• Wood: 4 planks + 4 sticks + 1 iron nugget (P S P / S N S / P S P)",
      "• Stone: 5 cobblestone + 4 stone bricks (C B C / B C B / C B C)",
      "• Iron: 5 iron ingots + 4 iron bars (I B I / B I B / I B I)",
      "• Hold boards and right-click a closed door: you hammer one board on. A door takes up to 4 boards.",
      "• Right-click glass: 1 board covers up to 4 panes next to each other.",
      "• A boarded door can't be opened. Sneak + right-click with an empty hand to pry one board off (you get it back).",
      "• The ghost has to smash every board, outermost first, before it can touch the door. Wood < stone < iron.",
    ].join("\n"),
    btnHide: "§8Hiding", hideTitle: "Hiding",
    hide: [
      "§l§cIt has to see you or hear you§r",
      "• If something blocks its view and you make no sound, it loses you after 3 seconds and walks to the last place it saw you.",
      "• It hears: running (far), walking, jumping, hard landings, doors, breaking or placing blocks, hitting things.",
      "• Sneaking is almost silent, and it has to be much closer to spot you.",
      "• While searching it sniffs around and checks hiding spots nearby. If it finds nothing for a while, it leaves.",
      "",
      "§l§cHiding closet§r",
      "• Craft: 8 planks around 1 chest.",
      "• Hold it and right-click the floor to place it. Hit it to pick it up again.",
      "• Right-click the closet to hide inside. You can peek out through the slats. Sneak to get out.",
      "• If it SEES you get in, it will rip the doors open.",
      "",
      "§l§cUnder the bed§r",
      "• Sneak + right-click a bed to slide under it (sneak to get out).",
      "• You only see the floor... and its feet when it walks past.",
      "",
      "§l§cDon't move§r",
      "• While hiding with it close by, turning your camera makes you breathe harder (watch the breath bar).",
      "• Loud breathing gives you away. If the bar fills you gasp, and it knows exactly where you are.",
      "• When it checks your hiding spot, stay perfectly still. Most of the time it moves on.",
    ].join("\n"),
    talkTitle: "Talk to a ghost", talkWho: "Who do you talk to?",
    talkMsg: "What do you say? (voice typing works here)", talkHint: "please leave me alone",
    none: "§7No ghost like that is in the world right now. You can only talk to a ghost while it is spawned.", quiet: "§8...", took: (x) => `§7The ghost takes your ${x}.`, angry: "§4§lIT IS ENRAGED",
    howTitle: "How to talk & negotiate",
    how: (chat) => [
      "§l§cTalking§r",
      "• Use this book, then Talk to a ghost. Pick who, type your message. No distance limit.",
      ...(chat ? ["• Or in chat, start with its short name: §ewail§r, §ewidow§r, §eash§r, §evoid§r, §estag§r. Example: §ewail please let me go§r"] : []),
      "• Only works while that ghost is spawned. It answers in red text at the bottom of your screen, and you hear it speak from where it stands.",
      "• A microphone can't be used by Minecraft add-ons. Your keyboard's voice typing works in the text box.",
      "",
      "§l§cNegotiating§r",
      "• Pleading, offering something, kind words, admitting fear, or any plain request is an attempt: §e5%§r chance it agrees and leaves.",
      "• Offering: say what you give, e.g. §eI give you a diamond§r. The ghost takes 1 from your inventory, even if it still refuses. Bonus on top of the 5%: iron, bread or food +5%, gold +10%, emerald +15%, diamond +20%, netherite +30%.",
      "• Offering something you don't have is a lie: it can't work and may enrage it.",
      "• Taunting (insults, mocking, threats like 'stupid ghost') can never work and counts as a failed attempt.",
      "• Each failed attempt: §e25%§r chance it gets enraged.",
      "• Enraged: §e2x§r speed, §e2x§r damage, smashes doors faster and can break stronger ones.",
      "• Greetings, questions and goodbyes are safe: it answers and nothing is rolled.",
      "• It remembers what you said, notices repeats, and reacts to low health, caves, water and night.",
      "• It needs 2 seconds between your messages.",
      "• If it agrees it sinks away. It still comes back later like normal, and you must negotiate again.",
    ].join("\n"),
    pages: {
      1: "Short name: §ewail§r\n\nA crying thing that copies sounds you know: doors, creepers, even fake chat messages in your own name. While chasing it screams until your screen goes dark.\n\nWhen you talk, it echoes your own words back.\n\n§7Body: tall and thin, about 5 blocks. Climbs ledges up to 4 blocks.",
      2: "Short name: §ewidow§r\n\nA drowned bride. Water drips off her and bubbles follow her. Come close and you slow down like you are drowning.\n\nSad and soft-spoken. She keeps asking about a ring.\n\n§7Body: small, about as tall as you. Walks through doors without crawling and swims well, but is slow at breaking walls. Climbs up to 3 blocks.",
      3: "Short name: §eash§r\n\nBurnt and still burning. Flames and smoke surround it, and if it catches you, you are set on fire.\n\nIt SHOUTS everything it says.\n\n§7Body: short and wide, about 3 blocks. The strongest: smashes walls fast, but only climbs 2 blocks.",
      4: "Short name: §evoid§r\n\nAlways smiling, always polite. Its image twitches, and when you aren't looking it warps behind you, laughing.\n\nThe politest one to talk to. That is not a good sign.\n\n§7Body: very tall, over 6 blocks. Reaches the highest ledges and climbs up to 5 blocks.",
      5: "Short name: §estag§r\n\nSilent and fast. It creeps closer whenever you look away. Never hold eye contact for more than 2 seconds or it lunges and the light dims around you.\n\nIt barely speaks. It only knows hunger.\n\n§7Body: big all over, about 5 blocks with antlers. Leaps up to 4 blocks and drops from high places.",
    },
  },
  th: {
    guideTitle: "§4§lคู่มือผีหลอน",
    intro: "ผีหลอน 5 ตนล่าอยู่ในโลกนี้ คู่มือเล่มนี้บอกว่าพวกมันคือใคร และคุยกับมันยังไง\n\n§7คุยกับผีได้เฉพาะตอนที่มันอยู่ในโลก (เกิดแล้ว) เท่านั้น",
    btnTalk: "§lคุยกับผี", btnHow: "วิธีคุยและต่อรอง", btnBack: "กลับ", btnHouse: "§4ประตู หน้าต่าง และไม้กั้น",
    houseTitle: "ประตู หน้าต่าง และไม้กั้น",
    house: [
      "§l§cเมื่อคุณหลบอยู่ในบ้าน§r",
      "• ผีจะมาที่ประตูหรือหน้าต่าง บางครั้งแค่มาเคาะแล้วหายไป บางครั้งจะทุบพังเข้ามา",
      "• บางทีมันแค่มาเคาะประตูหรือหน้าต่างเฉย ๆ โดยไม่พัง แล้วหายไปหลังเคาะ 7 วินาที",
      "• บางทีจะได้ยินเสียงแปลก ๆ ก่อน... แล้วจะมีบางอย่างมายืนจ้องอยู่ที่หน้าต่าง หรือประตูจะค่อย ๆ เปิดเอง ถ้าคุณมองเห็นมัน มันจะพุ่งเข้ามาหา",
      "• ทุบทีไรประตูยิ่งร้าว จนสุดท้ายระเบิดกระจุย (ไม่ได้ของคืน)",
      "• ความแข็ง: ไม้ (อ่อนสุด) < ทองแดง < เหล็ก (แข็งสุด) ไม้กั้นก็เหมือนกัน: ไม้ < หิน < เหล็ก",
      "• ผีแต่ละตัวแรงไม่เท่ากัน: ส่วนมากพังได้แค่ไม้ บางตัวพังทองแดง/หินได้ และบางครั้งจะเจอตัวโหดที่พังเหล็กได้ (ผีที่โมโหจะแรงขึ้น)",
      "• มันจะไปพังประตูหรือหน้าต่างก่อนเสมอ ไม่ว่าจะแข็งแค่ไหน ถ้าพังไม่ไหว บางครั้งจะหันไปทุบผนังเข้ามาแทน ไม่งั้นจะยอมแพ้แล้วหายไป",
      "• บล็อกยิ่งแข็งยิ่งพังนาน: ดินกับไม้พังเร็ว หิน อิฐ ดีพสเลต และบล็อกโลหะ ทนกว่ามาก",
      "• กระจกโดนทุบ 3 ครั้งแตก ผีตัวใหญ่จะมุดเข้ามาทางช่องที่พัง บางทีตัวติด แล้วจะค่อย ๆ ทุบกรอบออก",
      "",
      "§l§cมันหาทางเข้ามาเองได้§r",
      "• มันเดิน วิ่งขึ้นบันได กระโดดลง และปีนขึ้นที่สูงได้ (ผีแต่ละตัวปีนได้สูงไม่เท่ากัน)",
      "• มันพังเฉพาะสิ่งที่ขวางทางจริง ๆ: ไปทางที่โล่งก่อน ไม่งั้นค่อยพังประตู หน้าต่าง หรือไม้กั้น ส่วนผนัง พื้น และเพดาน จะพังเฉพาะตอนที่ไม่มีช่องให้เข้าเลย หรือพังประตูของคุณไม่ไหว",
      "• มันไม่พังบันได หีบ หรือเตียงของคุณ",
      "",
      "§l§cของซ่อม (ซ่อมได้ทางเดียว ขุดของที่ร้าวทิ้งจะแตกสลาย)§r",
      "• ชุดซ่อมไม้: ไม้กระดาน 2 + ไม้ 1 + เศษเหล็ก 1 (ประตูไม้ ประตูกล รั้ว ไม้กั้นไม้)",
      "• ชุดซ่อมทองแดง: ทองแดงแท่ง 2 + เศษเหล็ก 1 + ไม้ 1",
      "• ชุดซ่อมเหล็ก: เหล็กแท่ง 2 + เศษเหล็ก 2 + ไม้ 1 (ประตูเหล็ก ไม้กั้นเหล็ก)",
      "• ปูนซ่อมหิน (ได้ 2): หินกรวด 2 + ดินเหนียว 1 + กรวด 1 (ไม้กั้นหิน)",
      "• ยาแนวกระจก (ได้ 2): บานกระจก 1 + ทราย 1 + ดินเหนียว 1",
      "• ถือของซ่อมแล้วคลิกขวาที่ของที่ร้าว",
      "",
      "§l§cไม้กั้น (คราฟได้ทีละ 8)§r",
      "• ไม้กั้นไม้: ไม้กระดาน 4 + ไม้ 4 + เศษเหล็ก 1 (ก ม ก / ม ศ ม / ก ม ก)",
      "• ไม้กั้นหิน: หินกรวด 5 + อิฐหิน 4 (ห อ ห / อ ห อ / ห อ ห)",
      "• ไม้กั้นเหล็ก: เหล็กแท่ง 5 + ลูกกรงเหล็ก 4 (ล ก ล / ก ล ก / ล ก ล)",
      "• ถือไม้กั้นแล้วคลิกขวาที่ประตูที่ปิดอยู่: ตัวละครจะเอาค้อนตอกไม้ติดประตูทีละแผ่น ประตู 1 บานตอกได้ 4 แผ่น",
      "• คลิกขวาที่กระจก: ไม้กั้น 1 อันตอกปิดกระจกที่ติดกันได้ 4 บาน",
      "• ประตูที่ตอกไม้แล้วจะเปิดไม่ได้ ย่อ + คลิกขวามือเปล่า เพื่องัดไม้ออกทีละแผ่น (ได้ไม้กั้นคืน)",
      "• ผีต้องพังไม้กั้นทีละแผ่นจากแผ่นนอกสุดก่อน ถึงจะพังประตูได้ ไม้ < หิน < เหล็ก",
    ].join("\n"),
    btnHide: "§8การแอบ", hideTitle: "การแอบ",
    hide: [
      "§l§cวิ่งสุดแรง§r",
      "• ตอนกำลังวิ่ง กดย่อ 2 ครั้งเร็ว ๆ = วิ่งสุดแรง เร็วกว่าวิ่งปกติมาก (กดย่อ 2 ครั้งอีกที = เลิก)",
      "• มีแถบ stamina ด้านล่างจอ วิ่งสุดแรงได้ 10 วินาที แล้วต้องพักให้เต็มใหม่ 15 วินาที",
      "• ใกล้หมดจะเริ่มหอบ ถ้าวิ่งจนหมดเกลี้ยง จะหมดแรงเดินช้าไปครู่หนึ่ง",
      "• วิ่งสุดแรงเสียงดังมาก ผีได้ยินจากไกลกว่าปกติ",
      "",
      "§l§cผีต้องเห็นหรือได้ยินคุณ§r",
      "• ถ้ามีอะไรบังจนมันมองไม่เห็น และคุณไม่ส่งเสียง มันจะคลาดกับคุณใน 3 วินาที แล้วเดินไปดูจุดสุดท้ายที่เห็นคุณ",
      "• เสียงที่มันได้ยิน: วิ่ง (ได้ยินไกลมาก) เดิน กระโดด ตกกระแทกพื้น เปิดปิดประตู ทุบหรือวางบล็อก ตีของ",
      "• ย่อตัวเดินแทบไม่มีเสียง และมันต้องเข้ามาใกล้กว่าเดิมมากถึงจะเห็นคุณ",
      "• ตอนเดินหา มันจะดมกลิ่นและส่องที่ซ่อนแถวนั้น ถ้าหาไม่เจอสักพักจะหายไปเอง",
      "",
      "§l§cตู้ซ่อนตัว§r",
      "• คราฟ: ไม้กระดาน 8 ล้อมหีบ 1 ไว้ตรงกลาง",
      "• ถือแล้วคลิกขวาที่พื้นเพื่อวาง ตีตู้เพื่อเก็บคืน",
      "• คลิกขวาที่ตู้เพื่อเข้าไปซ่อน มองออกได้ทางช่องบานเกล็ด กดย่อเพื่อออก",
      "• ถ้ามัน \"เห็น\" ตอนคุณมุดเข้าไป มันจะมากระชากประตูตู้แน่นอน",
      "",
      "§l§cใต้เตียง§r",
      "• ย่อตัวแล้วคลิกขวาที่เตียง เพื่อมุดไปนอนใต้เตียง (กดย่อเพื่อออก)",
      "• คุณจะเห็นแค่ระดับพื้น... กับเท้าของมันตอนเดินผ่าน",
      "",
      "§l§cอย่าขยับ§r",
      "• ตอนซ่อนแล้วมันอยู่ใกล้ ถ้าคุณหันกล้องหรือขยับ จะหายใจแรงขึ้น (ดูแถบลมหายใจ)",
      "• หายใจแรง = มันได้ยิน ถ้าแถบเต็ม คุณจะหอบเฮือกออกมา แล้วมันจะรู้ทันทีว่าคุณอยู่ตรงไหน",
      "• ถ้ามันมาส่องที่ซ่อนของคุณ อยู่นิ่ง ๆ ไว้ ส่วนใหญ่มันจะเดินจากไป",
    ].join("\n"),
    talkTitle: "คุยกับผี", talkWho: "จะคุยกับตนไหน?",
    talkMsg: "จะพูดว่าอะไร? (พิมพ์ด้วยเสียงได้)", talkHint: "ขอร้อง อย่าตามฉันเลย",
    none: "§7ตอนนี้ไม่มีผีตนนั้นอยู่ในโลก คุยได้เฉพาะตอนที่ผีเกิดแล้วเท่านั้น", quiet: "§8...", took: (x) => `§7ผีรับ${x}ของคุณไป`, angry: "§4§lมันโมโหแล้ว",
    howTitle: "วิธีคุยและต่อรอง",
    how: (chat) => [
      "§l§cการคุย§r",
      "• ใช้สมุดเล่มนี้ แล้วเลือกคุยกับผี เลือกตนที่จะคุย พิมพ์ข้อความ คุยได้ทุกระยะ",
      ...(chat ? ["• หรือพิมพ์ในแชต โดยขึ้นต้นด้วยชื่อย่อของมัน: §ewail§r, §ewidow§r, §eash§r, §evoid§r, §estag§r ตัวอย่าง: §ewail ขอร้อง ปล่อยฉันไปเถอะ§r"] : []),
      "• ใช้ได้เฉพาะตอนที่ผีตนนั้นตื่นอยู่ มันตอบเป็นข้อความสีแดงเลือดที่ค่อย ๆ ขึ้นด้านล่างจอ พร้อมเสียงกระซิบ/ครวญดังมาจากตำแหน่งที่มันยืนอยู่ (ผีแต่ละตนเสียงไม่เหมือนกัน)",
      "• ถามตรง ๆ ได้ มันจะตอบตรงคำถาม เช่น §eอยู่ไหน§r §eตายยังไง§r §eจะฆ่าฉันไหม§r §eกี่โมงแล้ว§r §eเห็นฉันไหม§r §eหนียังไง§r §eมีกี่ตัว§r §eทำอะไรอยู่§r §eมาทำไม§r §eครอบครัว§r §eอายุเท่าไหร่§r",
      "• แอดออนใช้ไมโครโฟนของ Minecraft ไม่ได้ แต่ใช้การพิมพ์ด้วยเสียงของคีย์บอร์ดในช่องข้อความได้",
      "",
      "§l§cการต่อรอง§r",
      "• การอ้อนวอน เสนอสิ่งของ พูดดี ๆ ยอมรับว่ากลัว หรือพูดขอทั่วไป นับเป็นการต่อรอง: โอกาสสำเร็จ §e5%§r มันจะยอมแล้วจากไป",
      "• การเสนอของ: พิมพ์ว่าให้อะไร เช่น §eผมจะให้เพชร§r ผีจะรับของ 1 ชิ้นจากช่องเก็บของไปจริง ๆ แม้มันจะยังไม่ยอม โอกาสเพิ่มจาก 5%: เหล็ก ขนมปัง หรืออาหาร +5% ทอง +10% มรกต +15% เพชร +20% นีเธอไรต์ +30%",
      "• เสนอของที่ไม่มีอยู่ในช่องเก็บของ ถือเป็นการโกหก ไม่มีทางสำเร็จ และอาจทำให้มันโมโห",
      "• การพูดยั่วโมโห (ด่า กวนตีน ล้อเลียน ขู่ เช่น ไอ้ผีโง่ ไอ้กาก) ไม่มีทางสำเร็จ และนับเป็นการต่อรองที่ล้มเหลว",
      "• ถ้าต่อรองไม่สำเร็จ มีโอกาส §e25%§r ที่มันจะโมโห",
      "• ตอนโมโห: วิ่งเร็วขึ้น §e2 เท่า§r ตีแรงขึ้น §e2 เท่า§r ทุบประตูเร็วขึ้นและพังของที่แข็งกว่าได้",
      "• ทักทาย ถามคำถาม และบอกลาปลอดภัย มันจะตอบโดยไม่มีการสุ่ม",
      "• มันจำสิ่งที่คุณพูดได้ รู้ว่าคุณพูดซ้ำ และตอบตามสถานการณ์ เช่น เลือดน้อย อยู่ในถ้ำ อยู่ในน้ำ หรือกลางคืน",
      "• ต้องเว้น 2 วินาทีระหว่างแต่ละข้อความ",
      "• ถ้ามันตกลง มันจะค่อย ๆ จมหายไป แต่จะกลับมาตามเวลาปกติ และต้องต่อรองใหม่",
    ].join("\n"),
    pages: {
      1: "ชื่อย่อ: §ewail§r\n\nผีร้องไห้ที่เลียนเสียงที่คุณคุ้นเคย: ประตู ครีปเปอร์ แม้แต่แชตปลอมที่ใช้ชื่อคุณ ตอนไล่มันกรีดร้องจนจอมืด\n\nเวลาคุยกับมัน มันจะพูดทวนคำของคุณ\n\n§7รูปร่าง: สูงผอม ราว 5 บล็อก ปีนขอบได้สูง 4 บล็อก",
      2: "ชื่อย่อ: §ewidow§r\n\nเจ้าสาวจมน้ำ มีน้ำหยดจากตัวและฟองน้ำตามหลัง ถ้าเข้าใกล้จะช้าลงเหมือนกำลังจมน้ำ\n\nพูดเศร้าและเบา และถามถึงแหวนอยู่เสมอ\n\n§7รูปร่าง: ตัวเล็กพอ ๆ กับคน เดินลอดประตูได้ไม่ต้องคลาน ว่ายน้ำเก่ง แต่พังผนังได้ช้า ปีนได้ 3 บล็อก",
      3: "ชื่อย่อ: §eash§r\n\nร่างไหม้ที่ยังลุกอยู่ มีไฟและควันรอบตัว ถ้ามันจับตัวคุณได้ คุณจะติดไฟ\n\nมันตะโกนทุกคำที่พูด\n\n§7รูปร่าง: เตี้ยล่ำ ราว 3 บล็อก แรงเยอะที่สุด พังผนังเร็ว แต่ปีนได้แค่ 2 บล็อก",
      4: "ชื่อย่อ: §evoid§r\n\nยิ้มตลอดเวลา สุภาพตลอดเวลา ภาพของมันกระตุก และตอนที่คุณไม่ได้มอง มันจะวาร์ปไปข้างหลังพร้อมเสียงหัวเราะ\n\nสุภาพที่สุดในบรรดาทั้งหมด ซึ่งไม่ใช่สัญญาณที่ดี\n\n§7รูปร่าง: สูงเก้งก้างกว่า 6 บล็อก เอื้อมถึงที่สูงที่สุด ปีนได้ถึง 5 บล็อก",
      5: "ชื่อย่อ: §estag§r\n\nเงียบและเร็ว มันคืบเข้ามาทุกครั้งที่คุณมองไปทางอื่น ห้ามสบตานานเกิน 2 วินาที ไม่งั้นมันจะพุ่งใส่และแสงรอบตัวจะมืดลง\n\nแทบไม่พูด รู้จักแต่ความหิว\n\n§7รูปร่าง: ตัวใหญ่ ราว 5 บล็อกรวมเขา กระโดดขึ้นได้ 4 บล็อก และกระโดดลงจากที่สูงได้",
    },
  },
};
const L = dyn(() => TEXT[getLang()] ?? TEXT.en);

// ================= GHOST LINES (rule-based "AI": picks by intent + personality) =================
const LINES = {
  en: {
    1: { greet: ["hello... hello... hello...", "{n}... {n}... is that you?"],
      question: ["i was somebody once. i forgot who", "you ask what you already know"],
      plead: ["beg louder... i love that sound", "crying sounds the same in every voice"],
      bargain: ["{item}? i already lost everything", "nothing you hold is yours to give"],
      taunt: ["i'll keep that laugh. i'll use it later", "say it again. i learn quickly"],
      other: ["{echo}... {echo}...", "'{echo}'... so that's how you sound"],
      success: ["enough crying... i'll be quiet now", "fine... you sound so tired. go"],
      rage: ["STOP TALKING!! I'LL MAKE YOU SCREAM!", "YOUR VOICE... I'LL TAKE IT!"] },
    2: { greet: ["you came... the water is cold", "have you seen my husband?"],
      question: ["i was a bride... the lake was the aisle", "he never answered me either"],
      plead: ["i begged too. it didn't help", "stay... drowning isn't so bad"],
      bargain: ["{item}? i wanted a ring", "bring back my wedding day"],
      taunt: ["you laugh above the water", "cruel... he laughed as well"],
      other: ["{n}... come closer to the water", "i hear you through the water"],
      success: ["...you spoke so kindly. i'll sink away", "thank you... i'll rest in the water"],
      rage: ["YOU'LL DROWN WITH ME!", "NO MORE KIND WORDS!!"] },
    3: { greet: ["WHO WAKES ME?", "{n}..."],
      question: ["I BURNED. THAT'S ALL I KNOW", "ASK THE ASHES"],
      plead: ["I SCREAMED TOO. NOBODY CAME", "MERCY TURNED TO ASH LONG AGO"],
      bargain: ["{item}? IT MELTS. EVERYTHING MELTS", "I WANT NOTHING THAT BURNS"],
      taunt: ["BIG WORDS FOR SOMETHING FLAMMABLE", "LAUGH. I'LL REMEMBER YOUR SMOKE"],
      other: ["LOUDER. I CAN'T HEAR OVER THE FIRE", "YOUR WORDS TURN TO SMOKE"],
      success: ["...ENOUGH. I'LL COOL DOWN. GO", "FINE. I'LL GO BACK TO THE ASH"],
      rage: ["BURN!! BURN WITH ME!!", "I'LL TURN YOUR HOME TO ASH!"] },
    4: { greet: ["hello, friend :) you look delicious", "oh, a guest! how lovely"],
      question: ["i'm the one who always smiles", "i'd tell you, but surprises are fun"],
      plead: ["such manners! still no :)", "oh, begging. my favorite :)"],
      bargain: ["{item}? how generous :) no", "i'll take it... and you too :)"],
      taunt: ["hahaha! do keep going :)", "i'm still smiling. are you?"],
      other: ["how interesting! tell me more :)", "i'm listening. i'm always right behind you"],
      success: ["well, since you asked so nicely... bye :)", "okay! i'll go. don't look back :)"],
      rage: ["no more smiling. no more game.", "I SAID I WAS SMILING!!"] },
    5: { greet: ["...", "...hungry"],
      question: ["hunger.", "...i don't remember. only hunger"],
      plead: ["...hungry.", "no words. only food"],
      bargain: ["{item}... not food...", "...food? ...food?"],
      taunt: ["...", "...i will remember that"],
      other: ["...", "...hungry..."],
      success: ["...tired. i go.", "...later. hungry again"],
      rage: ["HUNGRY!! HUNGRY!!", "...I WILL EAT YOU..."] },
  },
  th: {
    1: { greet: ["สวัสดี... สวัสดี... สวัสดี...", "{n}... {n}... ใช่เธอหรือเปล่า"],
      question: ["ฉันเคยเป็นใครบางคน... ลืมแล้ว", "เธอถามสิ่งที่เธอรู้อยู่แล้ว"],
      plead: ["อ้อนวอนให้ดังกว่านี้... ฉันชอบเสียงนั้น", "เสียงร้องไห้ฟังเหมือนกันทุกเสียง"],
      bargain: ["{item}? ฉันเสียทุกอย่างไปหมดแล้ว", "ของที่เธอถืออยู่ ไม่ใช่ของเธอที่จะให้"],
      taunt: ["ฉันจะเก็บเสียงหัวเราะนั้นไว้ใช้ทีหลัง", "พูดอีกสิ ฉันเรียนรู้เร็ว"],
      other: ["{echo}... {echo}...", "'{echo}'... เสียงเธอเป็นแบบนี้เอง"],
      success: ["พอแล้วกับการร้องไห้... ฉันจะเงียบ", "ก็ได้... เสียงเธอดูเหนื่อยจัง ไปเถอะ"],
      rage: ["เงียบนะ!! ฉันจะทำให้เธอกรีดร้อง!", "เสียงของเธอ... ฉันจะเอาไป!"] },
    2: { greet: ["เธอมาแล้ว... น้ำเย็นจัง", "เธอเห็นสามีฉันไหม"],
      question: ["ฉันเป็นเจ้าสาว... ทะเลสาบคือทางเดินพิธี", "เขาไม่เคยตอบฉันเหมือนกัน"],
      plead: ["ฉันก็เคยอ้อนวอน... ไม่ช่วยอะไรเลย", "อยู่ต่อสิ... จมน้ำไม่ได้แย่ขนาดนั้น"],
      bargain: ["{item}? ฉันอยากได้แหวน", "คืนวันแต่งงานของฉันมาสิ"],
      taunt: ["เธอหัวเราะอยู่เหนือผิวน้ำ", "โหดร้าย... เขาก็หัวเราะเหมือนกัน"],
      other: ["{n}... เข้ามาใกล้น้ำหน่อยสิ", "ฉันได้ยินเธอผ่านผิวน้ำ"],
      success: ["...เธอพูดอ่อนโยนจัง ฉันจะจมหายไป", "ขอบคุณ... ฉันจะไปพักในน้ำ"],
      rage: ["เธอจะจมไปกับฉัน!", "ไม่ต้องมาพูดดี ๆ อีก!!"] },
    3: { greet: ["ใครมาปลุกฉัน?", "{n}..."],
      question: ["ฉันถูกเผา แค่นั้นที่รู้", "ไปถามเถ้าถ่านเอาเอง"],
      plead: ["ฉันก็กรีดร้อง ไม่มีใครมา", "ความเมตตากลายเป็นเถ้าไปนานแล้ว"],
      bargain: ["{item}? มันละลาย ทุกอย่างละลาย", "ฉันไม่ต้องการอะไรที่ไหม้ได้"],
      taunt: ["พูดใหญ่โตสำหรับของที่ติดไฟง่าย", "หัวเราะไปเถอะ ฉันจะจำควันของเธอ"],
      other: ["ดังกว่านี้ ฉันไม่ได้ยินเพราะเสียงไฟ", "คำของเธอกลายเป็นควัน"],
      success: ["...พอแล้ว ฉันจะเย็นลง ไปซะ", "ก็ได้ ฉันจะกลับไปเป็นเถ้า"],
      rage: ["ไหม้ซะ!! ไหม้ไปกับฉัน!!", "ฉันจะเผาบ้านเธอให้เป็นเถ้า!"] },
    4: { greet: ["สวัสดีเพื่อนรัก :) ดูน่ากินจัง", "โอ้ แขกมาเยือน น่ารักจัง"],
      question: ["ฉันคือคนที่ยิ้มอยู่เสมอ", "อยากบอกนะ แต่เซอร์ไพรส์สนุกกว่า"],
      plead: ["มารยาทดีจัง แต่ไม่นะ :)", "โอ้ อ้อนวอน ที่สุดของฉันเลย :)"],
      bargain: ["{item}? ใจกว้างจัง :) ไม่เอา", "ฉันรับไว้... แล้วก็เธอด้วย :)"],
      taunt: ["ฮ่า ๆ ๆ พูดต่อสิ :)", "ฉันยังยิ้มอยู่ แล้วเธอล่ะ?"],
      other: ["น่าสนใจจัง เล่าอีกสิ :)", "ฉันฟังอยู่ ฉันอยู่ข้างหลังเธอเสมอ"],
      success: ["ก็ได้ ในเมื่อขอดี ๆ... ไปล่ะ :)", "โอเค! ฉันไปก็ได้ อย่าหันกลับมานะ :)"],
      rage: ["ไม่ยิ้มแล้ว ไม่เล่นแล้ว", "ฉันบอกว่าฉันยิ้มอยู่ไง!!"] },
    5: { greet: ["...", "...หิว"],
      question: ["หิว.", "...จำไม่ได้ มีแต่ความหิว"],
      plead: ["...หิว.", "ไม่มีคำพูด มีแต่อาหาร"],
      bargain: ["{item}... ไม่ใช่อาหาร...", "...อาหาร? ...อาหาร?"],
      taunt: ["...", "...ฉันจะจำไว้"],
      other: ["...", "...หิว..."],
      success: ["...เหนื่อย ไปล่ะ", "...เดี๋ยวค่อยมา หิวอีกแล้ว"],
      rage: ["หิว!! หิว!!", "...ฉันจะกินเธอ..."] },
  },
};
const EXTRA = {
 "en": {
  "1": {
   "who": [
    "i was a miner. i heard the crying. now i cry",
    "call me what you cry out in the dark"
   ],
   "bye": [
    "don't go... go... go...",
    "bye bye bye bye..."
   ],
   "kind": [
    "you're kind... that hurts more",
    "nobody pities a mimic. thank you"
   ],
   "fear": [
    "good... fear sounds like music",
    "shaking? i can copy that"
   ],
   "threat": [
    "i'll learn your last scream first",
    "come then. i'll be you"
   ],
   "repeat": [
    "{echo}... you said that... said that..."
   ],
   "plead_many": [
    "so much begging. my favorite song"
   ],
   "taunt_many": [
    "you keep laughing. i'm keeping every laugh"
   ],
   "hurt": [
    "you sound hurt... that sounds good"
   ],
   "cave": [
    "it's quiet down here. my voice carries"
   ],
   "water": [
    "i hear splashing. i can splash too"
   ],
   "night": [
    "night is when voices carry farthest"
   ]
  },
  "2": {
   "who": [
    "i was a bride. i drowned before the vows",
    "they called me the widow of the lake"
   ],
   "bye": [
    "don't leave me by the water...",
    "go if you must. the lake keeps its guests"
   ],
   "kind": [
    "your voice is warm. the water isn't",
    "no one spoke kindly to me. not once"
   ],
   "fear": [
    "don't shake. the water will notice",
    "your fear ripples like a lake"
   ],
   "threat": [
    "you can't kill what already drowned",
    "come into the water. let's see"
   ],
   "repeat": [
    "you said that already. the lake remembers"
   ],
   "plead_many": [
    "so many pleas... the lake fills with them"
   ],
   "taunt_many": [
    "you keep mocking my grief"
   ],
   "hurt": [
    "you're bleeding... the water heals nothing"
   ],
   "cave": [
    "even down here i smell water"
   ],
   "water": [
    "you're in the water... now you understand"
   ],
   "night": [
    "it was night when the lake took me"
   ]
  },
  "3": {
   "who": [
    "I WAS A COOK. THE KITCHEN BURNED",
    "ASH. THAT'S MY NAME NOW"
   ],
   "bye": [
    "GO. THE SMOKE FOLLOWS",
    "LEAVE. I'LL BURN WITHOUT AN AUDIENCE"
   ],
   "kind": [
    "KINDNESS BURNS TOO...",
    "NOBODY PUT OUT THE FIRE. NOBODY"
   ],
   "fear": [
    "GOOD. FEAR IS FUEL",
    "YOUR FEAR SMELLS LIKE SMOKE"
   ],
   "threat": [
    "TRY IT. I'M ALREADY BURNING",
    "I'VE DIED ONCE. COME ON"
   ],
   "repeat": [
    "YOU SAID THAT ALREADY. I HEARD IT BURN"
   ],
   "plead_many": [
    "ENOUGH BEGGING. IT SOUNDS LIKE CRACKLING"
   ],
   "taunt_many": [
    "MORE INSULTS... MORE FIRE"
   ],
   "hurt": [
    "YOU BLEED. I SMELL IT FROM HERE"
   ],
   "cave": [
    "DARK. FINALLY, SHADOWS"
   ],
   "water": [
    "WATER. HOW CRUEL. KEEP IT AWAY"
   ],
   "night": [
    "THE FIRE WAS BRIGHTEST AT NIGHT"
   ]
  },
  "4": {
   "who": [
    "i'm the friendly one :) the others are rude",
    "call me whatever makes you smile :)"
   ],
   "bye": [
    "leaving already? see you soon :)",
    "bye bye! i'm right behind you :)"
   ],
   "kind": [
    "aww, you're sweet :) still not leaving",
    "kind words! i'll frame them :)"
   ],
   "fear": [
    "trembling? adorable :)",
    "don't be scared :) see? i'm smiling"
   ],
   "threat": [
    "how brave! how funny :)",
    "oh, violence. i'll smile through it :)"
   ],
   "repeat": [
    "you said that already :) say something new?"
   ],
   "plead_many": [
    "so much begging! encore :)"
   ],
   "taunt_many": [
    "still smiling. running out of insults? :)"
   ],
   "hurt": [
    "you're hurt :) shall i kiss it better?"
   ],
   "cave": [
    "cozy down here, isn't it? just us :)"
   ],
   "water": [
    "a swim? don't mind me, i'll watch :)"
   ],
   "night": [
    "night is my favorite :) shall we play?"
   ]
  },
  "5": {
   "who": [
    "...stag. ...no. ...hunger.",
    "...no name. ...only hunger."
   ],
   "bye": [
    "...stay.",
    "...leave. ...i follow."
   ],
   "kind": [
    "...kind? ...unfamiliar.",
    "...warm. ...hungry."
   ],
   "fear": [
    "...good.",
    "...fear smells... like food."
   ],
   "threat": [
    "...try.",
    "...come. ...i eat."
   ],
   "repeat": [
    "...same. ...again."
   ],
   "plead_many": [
    "...noise. ...enough."
   ],
   "taunt_many": [
    "...i remember. ...every word."
   ],
   "hurt": [
    "...blood. ...hunger."
   ],
   "cave": [
    "...dark. ...my place."
   ],
   "water": [
    "...wet. ...still hungry."
   ],
   "night": [
    "...night. ...hunting."
   ]
  }
 },
 "th": {
  "1": {
   "who": [
    "ฉันเคยเป็นคนขุดแร่ ฉันได้ยินเสียงร้องไห้ ตอนนี้ฉันก็ร้อง",
    "เรียกฉันว่าอะไรก็ได้ที่เธอร้องออกมาในความมืด"
   ],
   "bye": [
    "อย่าไป... ไป... ไป...",
    "ลาก่อน ลาก่อน ลาก่อน..."
   ],
   "kind": [
    "เธอใจดี... มันเจ็บกว่าเดิม",
    "ไม่มีใครสงสารตัวเลียนเสียง ขอบคุณ"
   ],
   "fear": [
    "ดี... ความกลัวฟังเหมือนดนตรี",
    "ตัวสั่นเหรอ ฉันเลียนแบบได้"
   ],
   "threat": [
    "ฉันจะเรียนรู้เสียงกรีดร้องสุดท้ายของเธอก่อน",
    "มาเลย ฉันจะกลายเป็นเธอ"
   ],
   "repeat": [
    "{echo}... เธอพูดไปแล้ว... พูดไปแล้ว..."
   ],
   "plead_many": [
    "อ้อนวอนเยอะจัง เพลงโปรดฉันเลย"
   ],
   "taunt_many": [
    "เธอหัวเราะไม่หยุด ฉันเก็บทุกเสียงไว้"
   ],
   "hurt": [
    "เสียงเธอเหมือนเจ็บ... ฟังดีจัง"
   ],
   "cave": [
    "ข้างล่างนี่เงียบ เสียงฉันก้องไกล"
   ],
   "water": [
    "ได้ยินเสียงน้ำกระเซ็น ฉันกระเซ็นได้เหมือนกัน"
   ],
   "night": [
    "กลางคืนคือเวลาที่เสียงไปไกลที่สุด"
   ]
  },
  "2": {
   "who": [
    "ฉันเป็นเจ้าสาว จมน้ำก่อนได้กล่าวคำสาบาน",
    "เขาเรียกฉันว่าหญิงม่ายแห่งทะเลสาบ"
   ],
   "bye": [
    "อย่าทิ้งฉันไว้ข้างน้ำ...",
    "ไปก็ไป ทะเลสาบเก็บแขกไว้เสมอ"
   ],
   "kind": [
    "เสียงเธออบอุ่น น้ำไม่เป็นอย่างนั้น",
    "ไม่เคยมีใครพูดดีกับฉัน สักครั้ง"
   ],
   "fear": [
    "อย่าสั่น น้ำจะรู้ตัว",
    "ความกลัวของเธอเป็นคลื่นบนผิวน้ำ"
   ],
   "threat": [
    "เธอฆ่าคนที่จมน้ำไปแล้วไม่ได้หรอก",
    "ลงมาในน้ำสิ มาดูกัน"
   ],
   "repeat": [
    "เธอพูดไปแล้ว ทะเลสาบจำได้"
   ],
   "plead_many": [
    "คำอ้อนวอนเยอะแยะ ทะเลสาบเต็มไปหมดแล้ว"
   ],
   "taunt_many": [
    "เธอล้อเลียนความเศร้าของฉันไม่หยุด"
   ],
   "hurt": [
    "เธอเลือดออก... น้ำรักษาอะไรไม่ได้หรอก"
   ],
   "cave": [
    "แม้อยู่ข้างล่างนี่ ฉันก็ได้กลิ่นน้ำ"
   ],
   "water": [
    "เธออยู่ในน้ำ... ตอนนี้เธอเข้าใจฉันแล้ว"
   ],
   "night": [
    "คืนนั้นทะเลสาบพาฉันไป"
   ]
  },
  "3": {
   "who": [
    "ฉันเคยเป็นพ่อครัว ห้องครัวไหม้",
    "เถ้า นั่นคือชื่อฉันตอนนี้"
   ],
   "bye": [
    "ไปสิ ควันจะตามไป",
    "ไปซะ ไม่มีคนดู ฉันก็ไหม้ต่อได้"
   ],
   "kind": [
    "ความใจดีก็ไหม้ได้...",
    "ไม่มีใครดับไฟให้ ไม่มีเลย"
   ],
   "fear": [
    "ดี ความกลัวคือเชื้อเพลิง",
    "ความกลัวของเธอมีกลิ่นควัน"
   ],
   "threat": [
    "ลองดูสิ ฉันไหม้อยู่แล้ว",
    "ฉันตายมาแล้วครั้งหนึ่ง เข้ามาเลย"
   ],
   "repeat": [
    "เธอพูดไปแล้ว ฉันได้ยินมันไหม้"
   ],
   "plead_many": [
    "พอได้แล้ว เสียงอ้อนวอนเหมือนเสียงไฟแตก"
   ],
   "taunt_many": [
    "ด่าอีก... ไฟก็แรงขึ้นอีก"
   ],
   "hurt": [
    "เธอเลือดออก ฉันได้กลิ่นจากตรงนี้"
   ],
   "cave": [
    "มืด ในที่สุดก็มีที่ให้เงาทอดยาว"
   ],
   "water": [
    "น้ำ โหดร้ายจัง เอาไปไกล ๆ"
   ],
   "night": [
    "ไฟสว่างที่สุดตอนกลางคืน"
   ]
  },
  "4": {
   "who": [
    "ฉันคือตนที่เป็นมิตร :) ตนอื่นหยาบคาย",
    "เรียกฉันว่าอะไรก็ได้ที่ทำให้เธอยิ้ม :)"
   ],
   "bye": [
    "จะไปแล้วเหรอ เดี๋ยวเจอกัน :)",
    "บ๊ายบาย! ฉันอยู่ข้างหลังเธอนะ :)"
   ],
   "kind": [
    "โอ๋ น่ารักจัง :) แต่ฉันยังไม่ไป",
    "คำพูดดี ๆ! ฉันจะใส่กรอบไว้ :)"
   ],
   "fear": [
    "ตัวสั่นเหรอ น่ารักจัง :)",
    "ไม่ต้องกลัวนะ :) ดูสิ ฉันยิ้มอยู่"
   ],
   "threat": [
    "กล้าจัง ตลกจัง :)",
    "โอ้ ความรุนแรง ฉันจะยิ้มสู้ :)"
   ],
   "repeat": [
    "เธอพูดไปแล้วนะ :) พูดอะไรใหม่ ๆ สิ"
   ],
   "plead_many": [
    "อ้อนวอนเยอะจัง ขออีกรอบ :)"
   ],
   "taunt_many": [
    "ยังยิ้มอยู่ เธอเริ่มหมดคำด่าแล้วนะ :)"
   ],
   "hurt": [
    "เธอเจ็บอยู่ :) ให้ฉันเป่าให้ไหม"
   ],
   "cave": [
    "ข้างล่างอบอุ่นดีนะ มีแค่เราสองคน :)"
   ],
   "water": [
    "ว่ายน้ำเหรอ ไม่ต้องสนใจฉัน ฉันดูอยู่ :)"
   ],
   "night": [
    "กลางคืนคือที่สุดของฉัน :) เล่นกันไหม"
   ]
  },
  "5": {
   "who": [
    "...กวาง. ...ไม่. ...ความหิว.",
    "...ไม่มีชื่อ. ...มีแต่ความหิว."
   ],
   "bye": [
    "...อยู่ก่อน.",
    "...ไปสิ. ...ฉันตาม."
   ],
   "kind": [
    "...ใจดี? ...ไม่คุ้น.",
    "...อุ่น. ...หิว."
   ],
   "fear": [
    "...ดี.",
    "...กลัว มีกลิ่น... เหมือนอาหาร."
   ],
   "threat": [
    "...ลองสิ.",
    "...มา. ...ฉันกิน."
   ],
   "repeat": [
    "...เหมือนเดิม. ...อีกแล้ว."
   ],
   "plead_many": [
    "...เสียงดัง. ...พอ."
   ],
   "taunt_many": [
    "...จำได้. ...ทุกคำ."
   ],
   "hurt": [
    "...เลือด. ...หิว."
   ],
   "cave": [
    "...มืด. ...ที่ของฉัน."
   ],
   "water": [
    "...เปียก. ...ยังหิว."
   ],
   "night": [
    "...กลางคืน. ...ล่า."
   ]
  }
 }
};
for (const lg of Object.keys(EXTRA)) for (const n of Object.keys(EXTRA[lg])) Object.assign(LINES[lg][n], EXTRA[lg][n]);
const POOLS2 = {
 "en": {
  "1": {
   "nothave": [
    "{item}? you hold nothing... i hear the lie",
    "empty pockets... the lie echoes... lie... lie..."
   ],
   "refuse": [
    "i'll keep your {item}... i still won't stop",
    "{item}... it echoes. it isn't enough"
   ]
  },
  "2": {
   "nothave": [
    "you offer {item} but your hands are empty",
    "liar... the lake knows what you hold"
   ],
   "refuse": [
    "your {item} sinks. so do you",
    "i took your {item}... it was never a ring"
   ]
  },
  "3": {
   "nothave": [
    "YOU HAVE NO {item}. LIAR. LIAR.",
    "EMPTY HANDS AND A LIE. BURN."
   ],
   "refuse": [
    "YOUR {item} IS ASH NOW. I WANT MORE",
    "I TOOK THE {item}. IT DID NOT HELP"
   ]
  },
  "4": {
   "nothave": [
    "you don't have that :) naughty naughty",
    "oh, a fib :) i love fibs"
   ],
   "refuse": [
    "thank you for the {item} :) still no",
    "a lovely {item}! i'll keep it... and you :)"
   ]
  },
  "5": {
   "nothave": [
    "...no {item}. ...lie.",
    "...empty. ...hungry."
   ],
   "refuse": [
    "...{item}. ...not enough. ...hungry.",
    "...took {item}. ...still hungry."
   ]
  }
 },
 "th": {
  "1": {
   "nothave": [
    "{item}? เธอไม่มีอะไรเลย... ฉันได้ยินเสียงโกหก",
    "กระเป๋าว่างเปล่า... คำโกหกก้องอยู่... โกหก... โกหก..."
   ],
   "refuse": [
    "ฉันจะเก็บ{item}ของเธอไว้... แต่ฉันยังไม่หยุด",
    "{item}... เสียงก้อง มันยังไม่พอ"
   ]
  },
  "2": {
   "nothave": [
    "เธอเสนอ{item} แต่มือเธอว่างเปล่า",
    "คนโกหก... ทะเลสาบรู้ว่าเธอถืออะไร"
   ],
   "refuse": [
    "{item}ของเธอจมลงแล้ว เธอก็จะจมด้วย",
    "ฉันรับ{item}ไว้... มันไม่ใช่แหวน"
   ]
  },
  "3": {
   "nothave": [
    "เธอไม่มี{item} โกหก โกหก!",
    "มือเปล่ากับคำโกหก ไหม้ซะ"
   ],
   "refuse": [
    "{item}ของเธอเป็นเถ้าแล้ว ฉันยังอยากได้อีก",
    "ฉันรับ{item}ไว้ มันไม่ช่วยอะไร"
   ]
  },
  "4": {
   "nothave": [
    "เธอไม่มีของนั้นนะ :) ไม่ดีเลย",
    "โอ้ โกหกหน่อย ๆ :) ฉันชอบ"
   ],
   "refuse": [
    "ขอบคุณสำหรับ{item}นะ :) แต่ยังไม่เอา",
    "{item}น่ารักจัง! ฉันเก็บไว้... แล้วก็เธอด้วย :)"
   ]
  },
  "5": {
   "nothave": [
    "...ไม่มี{item}. ...โกหก.",
    "...ว่างเปล่า. ...หิว."
   ],
   "refuse": [
    "...{item}. ...ไม่พอ. ...หิว.",
    "...รับ{item}. ...ยังหิว."
   ]
  }
 }
};
for (const lg of Object.keys(POOLS2)) for (const n of Object.keys(POOLS2[lg])) Object.assign(LINES[lg][n], POOLS2[lg][n]);
const POOLS3 = { th: {
  1: { why: ["เพราะเธอร้องไห้... ฉันตามเสียงนั้นมา", "ฉันไม่ได้ไล่... ฉันแค่เดินตามเสียงเธอ"], want: ["ฉันอยากได้เสียงของเธอ... ทุกเสียง", "อยากให้เธอร้องอีกครั้ง... ฉันจะจำไว้"] },
  2: { why: ["เพราะเธอเดินอยู่ริมน้ำของฉัน", "ฉันเหงา... ไม่มีใครอยู่กับฉันเลย"], want: ["ฉันอยากได้แหวนวงนั้นคืน", "อยากให้ใครสักคนอยู่กับฉัน... ที่ก้นทะเลสาบ"] },
  3: { why: ["เพราะเธอยังไม่ไหม้!", "ฉันไม่ได้ไล่! ฉันแค่อยากอุ่น!"], want: ["ไฟ! ฉันต้องการไฟ!", "อยากให้ทุกอย่างรอบตัวเธอเป็นเถ้า!"] },
  4: { why: ["ใครบอกว่าฉันไล่ล่ะ :) ฉันแค่เดินเล่นข้างหลังเธอ", "เพราะเธอน่ารักไง :) ฉันอยากอยู่ใกล้ ๆ"], want: ["อยากเห็นเธอยิ้มอยู่ตลอดไป :) ในที่ของฉัน", "ฉันอยากได้เธอ :) แค่นี้เอง"] },
  5: { why: ["...หิว.", "...เธอวิ่ง. ...ฉันตาม."], want: ["...อาหาร.", "...เธอ. ...หิว."] },
} };
for (const n of Object.keys(POOLS3.th)) Object.assign(LINES.th[n], POOLS3.th[n]);
const LN = dyn(() => LINES[getLang()] ?? LINES.en);
export const LINE_TABLE = LN;

// ================= intent detection (works for English and Thai input in every edition) =================
const THAI = /[\u0e00-\u0e7f]/;
const esc = (s) => s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
function compile(words) {
  return words.map((w) => (THAI.test(w) || !/^[a-z' ]+$/.test(w)) ? { w, re: null } : { w, re: new RegExp("(^|[^a-z])" + esc(w) + "($|[^a-z])") });
}
const C = {
  items: compile(["diamond", "gold", "emerald", "netherite", "iron", "bread", "food", "soul", "เพชร", "ทอง", "มรกต", "นีเธอไรต์", "เหล็ก", "ขนมปัง", "อาหาร", "วิญญาณ"]),
  // taunting = insults, mockery, "provoking" talk
  taunt: compile(["stupid", "idiot", "ugly", "loser", "weak", "noob", "trash", "garbage", "dumb", "moron", "coward", "pathetic", "useless", "lame", "fake", "cringe", "scrub", "clown", "shut up", "stfu", "fuck", "shit", "bitch", "asshole", "dickhead", "not scared", "not afraid", "don't scare", "dont scare", "come at me", "bad ghost", "you suck", "you're a joke", "ez", "lol", "lmao", "haha", "hahaha", "hahahaha",
    "ผีโง่", "ไอ้ผี", "ไอผี", "ไอ้กาก", "ไอกาก", "ไอ้โง่", "ไอโง่", "ไอ้ควาย", "ไอควาย", "ไอ้ห่า", "ไอ้สัส", "ไอ้เหี้ย", "ไอ้บ้า", "ไอ้ขยะ", "ไอ้ขี้ขลาด", "โง่", "ควาย", "กาก", "ห่วย", "น่าเกลียด", "อ่อนแอ", "อ่อนหัด", "ขี้ขลาด", "ไร้ค่า", "ไร้สาระ", "ขี้แพ้", "ปัญญาอ่อน", "สมองกลวง", "กวนตีน", "เหี้ย", "สัส", "ห่า", "ไปตาย", "ขยะ", "ปลอม", "ไม่กลัว", "ไม่เห็นน่ากลัว", "ไม่น่ากลัว", "หุบปาก", "เงียบไปเลย", "มึง", "555", "ฮ่าๆ", "ฮ่า ๆ"]),
  threat: compile(["kill you", "i will kill", "i'll kill", "destroy you", "burn you", "exorcise", "end you", "fight me", "slay", "banish", "get rid of you", "ฆ่า", "กำจัด", "ทำลาย", "ไล่ผี", "มาสู้", "สู้กัน", "จะเผา", "ฟัน"]),
  bargain: compile(["deal", "trade", "offer", "give you", "i'll give", "ill give", "gift", "bribe", "ข้อเสนอ", "แลก", "ของขวัญ", "สินบน", "ข้อตกลง", "จะให้"]),
  why: compile(["why are you", "why do you", "why me", "ทำไม", "เพราะอะไร", "เพราะไร"]),
  want: compile(["what do you want", "what you want", "ต้องการอะไร", "อยากได้อะไร", "อยากทำอะไร", "จะเอาอะไร", "ต้องการอะไรจากฉัน"]),
  who: compile(["who are you", "your name", "what are you", "what is your name", "เป็นใคร", "ชื่ออะไร", "คือใคร", "คืออะไร"]),
  bye: compile(["bye", "goodbye", "see you", "gtg", "farewell", "ลาก่อน", "บาย", "ไปแล้วนะ", "แล้วเจอกัน"]),
  kind: compile(["lonely", "sad", "poor thing", "understand", "feel for you", "you're not alone", "love you", "be friends", "sorry for you", "rest in peace", "rip", "peace", "comfort", "hug", "เหงา", "เสียใจ", "เข้าใจ", "ใจดี", "อ่อนโยน", "น่ารัก", "เป็นเพื่อน", "ไม่ต้องกลัว", "พักผ่อน", "หลับให้สบาย", "สงสาร", "กอด", "รัก"]),
  plead: compile(["please", "pls", "plz", "sorry", "forgive", "mercy", "spare", "leave me", "go away", "let me go", "don't hurt", "dont hurt", "stop", "help", "beg", "ขอร้อง", "ได้โปรด", "ขอโทษ", "ปล่อย", "อย่าทำร้าย", "ไปเถอะ", "ไปที", "หยุด", "ช่วย", "ขอ"]),
  fear: compile(["scared", "afraid", "terrified", "creepy", "fear", "กลัว", "น่ากลัว", "ขนลุก"]),
  question: compile(["who", "what", "why", "where", "how", "name", "are you", "?", "เป็นใคร", "ใคร", "อะไร", "ทำไม", "ที่ไหน", "ยังไง", "อย่างไร", "ชื่อ", "ไหม"]),
  greet: compile(["hi", "hello", "hey", "yo", "good evening", "สวัสดี", "หวัดดี", "ไง", "เฮ้"]),
};
function findKw(text, list) {
  for (const k of list) if (k.re ? k.re.test(text) : text.includes(k.w)) return k.w;
  return null;
}
const SAFE = new Set(["greet", "question", "who", "bye", "why", "want"]);      // talk only, nothing is rolled
const HOSTILE = new Set(["taunt", "threat"]);                    // can never succeed, still may enrage
const APPEAL = new Set(["plead", "bargain", "kind", "fear"]);
export function classify(raw) {
  const text = String(raw).toLowerCase().trim();
  if (!text) return { intent: "greet", item: null };
  const item = findKw(text, C.items);
  for (const name of ["taunt", "threat"]) if (findKw(text, C[name])) return { intent: name, item };
  if (item || findKw(text, C.bargain)) return { intent: "bargain", item };
  for (const name of ["who", "why", "want", "bye", "kind", "plead", "fear", "question", "greet"]) if (findKw(text, C[name])) return { intent: name, item };
  return { intent: "other", item };
}

const lastLine = new Map();
export function pickLine(lines, ctx, memo) {
  let pool = lines.filter((l) => ctx.item || !l.includes("{item}"));
  if (!pool.length) pool = lines.filter((l) => !l.includes("{item}"));
  const prev = lastLine.get(memo);
  const fresh = pool.filter((l) => l !== prev);
  if (fresh.length) pool = fresh;
  const line = pool[Math.floor(Math.random() * pool.length)];
  lastLine.set(memo, line);
  return line.replace(/\{n\}/g, ctx.name).replace(/\{item\}/g, ctx.item ?? "").replace(/\{echo\}/g, ctx.echo);
}

// ================= runtime =================
let api = null;
const lastTalk = new Map();     // ghostId:playerId -> tick
const memory = new Map();       // ghostId:playerId -> what this player has said to this ghost
const showToken = new Map();    // player.id -> token for the red text on screen
let tokenSeq = 0;

function findGhost(n) {
  for (const d of api.dims()) {
    try { const e = d.getEntities({ type: `ghost:ghost_${n}` }); if (e.length) return e[0]; } catch (e) {}
  }
  return null;
}

// What is going on around the player (the ghost reacts to it)
function situation(p) {
  const out = [];
  try { const h = p.getComponent("minecraft:health"); if (h && h.currentValue <= 8) out.push("hurt"); } catch (e) {}
  try { if (p.dimension.id === "minecraft:overworld" && p.location.y < 40) out.push("cave"); } catch (e) {}
  try { if (p.isInWater) out.push("water"); } catch (e) {}
  try { const t = world.getTimeOfDay(); if (p.dimension.id === "minecraft:overworld" && t >= 13000 && t <= 23000) out.push("night"); } catch (e) {}
  return out;
}

// ---- the ghost's voice (v1.6): synthesized whisper/moan "speech", one clip sized to the line ----
const rnd = (a, b) => a + Math.random() * (b - a);
function speakSound(g, p, n, line, kind) {
  let pos;
  try { pos = { x: g.location.x, y: g.location.y + 1.5, z: g.location.z }; } catch (e) { return; }
  const len = Array.from(String(line)).length;
  const size = kind === "rage" ? "rage" : len <= 14 ? "s" : len <= 32 ? "m" : "l";
  api.playAt(p, `ghost.voice.g${n}.${size}`, pos.x, pos.y, pos.z, 2.2, rnd(0.96, 1.04));
  system.runTimeout(() => {
    if (kind === "rage") api.playAt(p, "ghost.growl", pos.x, pos.y, pos.z, 2.2, 0.5);
    else if (kind === "success") api.playAt(p, "ghost.creak", pos.x, pos.y, pos.z, 1.4, 0.5);
  }, 30);
}

// Ghost reply (v1.6): blood-red text typed out at the bottom of the screen + its voice
export function showRed(p, info, line, g, kind) {
  const name = `§4§l${info.name}`;
  const chars = Array.from(String(line));
  const typeTicks = Math.min(60, Math.max(10, Math.ceil(chars.length * 0.9)));
  const hold = Math.min(140, 70 + chars.length * 2);
  const dur = typeTicks + hold;
  const token = ++tokenSeq;
  showToken.set(p.id, token);
  api.talkLock.set(p.id, system.currentTick + dur);
  try { p.sendMessage(`${name}§r§8 : §4${line}`); } catch (e) {}
  let t = 0;
  const id = system.runInterval(() => {
    t++;
    if (showToken.get(p.id) !== token || t >= dur) { system.clearRun(id); if (showToken.get(p.id) === token) showToken.delete(p.id); return; }
    const shown = Math.min(chars.length, Math.ceil((chars.length * t) / typeTicks));
    let txt = chars.slice(0, shown).join("");
    if (shown < chars.length) txt += "§k" + chars.slice(shown, shown + 2).join("") + "§r§4";
    else if (Math.random() < 0.035) {                       // หลังพิมพ์ครบ: ตัวอักษรกระตุกวูบเป็นพัก ๆ
      const i = Math.floor(Math.random() * Math.max(1, chars.length - 3));
      txt = chars.slice(0, i).join("") + "§k" + chars.slice(i, i + 3).join("") + "§r§4" + chars.slice(i + 3).join("");
    }
    try { p.onScreenDisplay.setActionBar(`${name}§r§8 : §4${txt}`); } catch (e) { system.clearRun(id); }
  }, 1);
  if (g) speakSound(g, p, info.n, line, kind);
}

// คำถามเฉพาะเรื่อง: ตอบตรงคำถาม พร้อมข้อมูลจริงในเกม (ทิศ ระยะ เวลา สิ่งที่ผีกำลังทำ)
const lastTopic = new Map();
function topicLine(n, topic, p, g) {
  const T = TOPIC_LINES[n];
  if (!T || !T[topic]) return null;
  let pool = T[topic];
  if (topic === "see") {
    let k = "unseen";
    try { if (isHidden(p)) k = "hidden"; else if (canSee(g, p, 48)) k = "seen"; } catch (e) {}
    pool = pool[k];
  }
  const memo = n + topic, prev = lastTopic.get(memo);
  const fresh = pool.filter((l) => l !== prev);
  const line = (fresh.length ? fresh : pool)[Math.floor(Math.random() * (fresh.length ? fresh : pool).length)];
  lastTopic.set(memo, line);
  let d = 0; try { d = Math.round(Math.hypot(g.location.x - p.location.x, g.location.y - p.location.y, g.location.z - p.location.z)); } catch (e) {}
  let tod = 18000; try { tod = world.getTimeOfDay(); } catch (e) {}
  const tpl = isEn() ? tx(line) : line;
  const filled = tpl.replace(/\{dir\}/g, dirText(p, g)).replace(/\{dist\}/g, String(d)).replace(/\{time\}/g, timeText(tod))
    .replace(/\{doing\}/g, doingText(g, api.meta.get(g.id))).replace(/\{n\}/g, p.name);
  return isEn() ? cap(filled) : filled;
}

function enrage(g, p, info, ctx) {
  try { g.triggerEvent("ghost:enrage"); } catch (e) {}
  try { p.runCommand("camerashake add @s 0.8 1.2 positional"); } catch (e) {}
  try { p.onScreenDisplay.setTitle(L.angry, { fadeInDuration: 0, stayDuration: 30, fadeOutDuration: 10 }); } catch (e) {}
  system.runTimeout(() => showRed(p, info, pickLine(LN[info.n].rage, ctx, "rage" + info.n), g, "rage"), 5);
}

// Success: the ghost sinks into the ground, fading in smoke, then disappears
function farewell(g, p) {
  api.leaving.add(g.id);
  const m = api.meta.get(g.id); if (m) m.stalking = false;
  try { g.triggerEvent("ghost:farewell"); } catch (e) {}
  let { x, y, z } = g.location;
  const startY = y, dim = g.dimension, id0 = g.id;
  let t = 0;
  const finish = () => {
    try { dim.runCommand(`playsound mob.warden.nearby_close @a ${Math.floor(x)} ${Math.floor(startY)} ${Math.floor(z)} 1 0.5`); } catch (e) {}
    try { g.remove(); } catch (e) {}
    api.leaving.delete(id0);
  };
  const id = system.runInterval(() => {
    t++;
    try {
      const loc = { x, y: startY - t * 0.09, z };
      try { g.teleport(loc, { facingLocation: p.location }); } catch (e) { g.teleport(loc); }
      if (t === 1) api.playAt(p, "mob.ghast.moan", x, startY, z, 1.2, 0.5);
      if (t === 18) api.playAt(p, "ghost.creak", x, startY, z, 1.2, 0.6);
      if (t === 34) api.playAt(p, "ambient.cave", x, startY, z, 1, 0.7);
      if (t % 2 === 0) {
        api.puff(dim, "minecraft:basic_smoke_particle", { x, y: startY, z }, 0.7);
        api.puff(dim, "minecraft:totem_particle", { x, y: startY, z }, 0.6);
      }
    } catch (e) { t = 999; }
    if (t >= 50) { system.clearRun(id); finish(); }
  }, 1);
}

// Pick what the ghost says: rage > repeat > grudge > situation > normal reply
function chooseLine(n, intent, raged, mem, text, p, ctx) {
  const P = LN[n];
  if (raged) return pickLine(P.rage, ctx, "rage" + n);
  if (text && text === mem.last && P.repeat) return pickLine(P.repeat, ctx, "repeat" + n);
  if (HOSTILE.has(intent) && mem.hostile >= 2 && Math.random() < 0.5) return pickLine(P.taunt_many, ctx, "tm" + n);
  if (APPEAL.has(intent) && mem.appeal >= 3 && Math.random() < 0.5) return pickLine(P.plead_many, ctx, "pm" + n);
  const s = situation(p);
  if (s.length && Math.random() < 0.3) { const k = s[Math.floor(Math.random() * s.length)]; return pickLine(P[k], ctx, k + n); }
  return pickLine(P[intent] ?? P.other, ctx, intent + n);
}

// ================= real offers: the item is taken from the player's inventory =================
const FOODS = new Set(["minecraft:bread", "minecraft:cooked_beef", "minecraft:cooked_porkchop", "minecraft:cooked_chicken", "minecraft:cooked_mutton", "minecraft:cooked_salmon", "minecraft:cooked_cod", "minecraft:apple", "minecraft:golden_apple", "minecraft:baked_potato", "minecraft:carrot", "minecraft:pumpkin_pie", "minecraft:cookie"]);
const OFFERS = {
  diamond: { ids: ["minecraft:diamond"], bonus: 0.20, en: "diamond", th: "เพชร" },
  gold: { ids: ["minecraft:gold_ingot"], bonus: 0.10, en: "gold ingot", th: "ทองคำแท่ง" },
  emerald: { ids: ["minecraft:emerald"], bonus: 0.15, en: "emerald", th: "มรกต" },
  netherite: { ids: ["minecraft:netherite_ingot"], bonus: 0.30, en: "netherite ingot", th: "นีเธอไรต์แท่ง" },
  iron: { ids: ["minecraft:iron_ingot"], bonus: 0.05, en: "iron ingot", th: "เหล็กแท่ง" },
  bread: { ids: ["minecraft:bread"], bonus: 0.05, en: "bread", th: "ขนมปัง" },
  food: { foods: true, bonus: 0.05, en: "food", th: "อาหาร" },
};
const OFFER_KEY = { diamond: "diamond", "เพชร": "diamond", gold: "gold", "ทอง": "gold", emerald: "emerald", "มรกต": "emerald", netherite: "netherite", "นีเธอไรต์": "netherite", iron: "iron", "เหล็ก": "iron", bread: "bread", "ขนมปัง": "bread", food: "food", "อาหาร": "food" };
function takeItem(p, offer) {
  try {
    const inv = p.getComponent("minecraft:inventory").container;
    for (let i = 0; i < inv.size; i++) {
      const it = inv.getItem(i);
      if (!it) continue;
      if (!(offer.foods ? FOODS.has(it.typeId) : offer.ids.includes(it.typeId))) continue;
      if (it.amount > 1) { it.amount -= 1; inv.setItem(i, it); } else inv.setItem(i, undefined);
      return true;
    }
  } catch (e) {}
  return false;
}

// Returns false when that ghost is not in the world (so nothing answers)
export function talkTo(p, n, rawText) {
  const info = GHOSTS[n - 1];
  const g = info && findGhost(n);
  if (!g) return false;
  if (api.leaving.has(g.id)) return true;
  const now = system.currentTick;
  const key = g.id + ":" + p.id;
  if (now - (lastTalk.get(key) ?? -1e9) < COOLDOWN) { try { p.sendMessage("§7(รอ 2 วินาทีก่อนพูดต่อ)"); p.onScreenDisplay.setActionBar(L.quiet); } catch (e) {} return true; }
  lastTalk.set(key, now);
  let mem = memory.get(key);
  if (!mem) { mem = { last: "", hostile: 0, appeal: 0 }; memory.set(key, mem); }
  const text = String(rawText).trim().slice(0, 120);
  if (api.tryAsk) { let handled = false; try { handled = api.tryAsk(p, text); } catch (e) { report(p, e); } if (handled) return true; }
  const raged = g.getProperty("ghost:rage") === true;
  const topic = findTopic(text.toLowerCase());
  if (topic && !(raged && Math.random() < 0.5)) {                    // คำถามเฉพาะเรื่อง = ตอบตรง ๆ (ไม่สุ่มต่อรอง)
    const tl = topicLine(n, topic, p, g);
    if (tl) { showRed(p, info, tl, g, raged ? "rage" : "talk"); mem.last = text.toLowerCase(); return true; }
  }
  const { intent, item } = classify(text);
  const ctx = { name: p.name, item, echo: text.length > 22 ? text.slice(0, 22) : text };
  const norm = text.toLowerCase();
  const done = () => { mem.last = norm; if (HOSTILE.has(intent)) mem.hostile++; else if (APPEAL.has(intent) || intent === "other") mem.appeal++; };

  if (SAFE.has(intent)) {                                       // safe talk, nothing is rolled
    showRed(p, info, chooseLine(n, intent, raged, mem, norm, p, ctx), g, raged ? "rage" : "talk");
    done(); return true;
  }
  // ---- offering a real item: it is taken from the inventory and raises the chance ----
  let chance = SUCCESS_CHANCE, lie = false, offered = null;
  const offer = intent === "bargain" && item ? OFFERS[OFFER_KEY[item]] : null;
  if (offer) {
    ctx.item = offer[getLang()] ?? offer.en;
    if (takeItem(p, offer)) {
      offered = offer; chance += offer.bonus;
      try { p.sendMessage(L.took(ctx.item)); } catch (e) {}
    } else lie = true;
  }
  if (!HOSTILE.has(intent) && !lie && Math.random() < chance) {   // taunts, threats and lies can never succeed
    showRed(p, info, pickLine(LN[n].success, ctx, "success" + n), g, "success");
    farewell(g, p);
    return true;
  }
  const refusal = () => pickLine(LN[n].refuse, ctx, "refuse" + n);   // a refused offer always mentions the item
  if (!raged && Math.random() < RAGE_CHANCE) {
    done();
    if (offered) { showRed(p, info, refusal(), g, "talk"); system.runTimeout(() => enrage(g, p, info, ctx), 60); }
    else enrage(g, p, info, ctx);
    return true;
  }
  const line = lie ? pickLine(LN[n].nothave, ctx, "nothave" + n) : offered ? refusal() : chooseLine(n, intent, raged, mem, norm, p, ctx);
  showRed(p, info, line, g, raged ? "rage" : "talk");
  done();
  return true;
}

// ================= chat (only exists when the Beta APIs are used) =================
const CHAT_RE = /^\s*(wail|widow|ash|void|stag)\b[\s:,>\-.!?]*([\s\S]*)$/i;
export function parseChat(msg) {
  const m = CHAT_RE.exec(msg);
  if (!m) return null;
  return { n: GHOSTS.findIndex((x) => x.key === m[1].toLowerCase()) + 1, text: m[2] ?? "" };
}
const chatAvailable = () => { try { return !!(world.beforeEvents && world.beforeEvents.chatSend); } catch (e) { return false; } };

// ================= guide book + talk window =================
export async function showForm(p, form) {
  for (let i = 0; i < 20; i++) {
    const r = await form.show(p);
    if (r.cancelationReason !== "UserBusy") return r;
    await new Promise((res) => system.runTimeout(res, 10));
  }
  return { canceled: true };
}

function report(p, e) {   // never fail silently: show the error in chat so it can be fixed
  try { p.sendMessage(`§c[Ghost Guide] ${e && e.message ? e.message : e}`); } catch (_) {}
}

async function openTalk(p, pre) {
  let def = 0;
  const here = GHOSTS.map((g) => !!findGhost(g.n));
  if (pre) def = pre - 1;
  else for (const g of GHOSTS) if (here[g.n - 1]) { def = g.n - 1; break; }
  const form = new ModalFormData().title("§4§l§k|§r §4§lคุยกับผี §r§4§l§k|");
  const names = GHOSTS.map((g, i) => `${here[i] ? "§c● " : "§8○ "}${g.color}${g.name} §8(${g.key})`);
  const who = "§7เลือกผีที่จะคุยด้วย\n§8● = ตื่นอยู่ตอนนี้  ○ = หลับอยู่ (ไม่มีใครตอบ)";
  const say = "§7พิมพ์สิ่งที่อยากพูด\n§8ลองถาม: อยู่ไหน · ตายยังไง · จะฆ่าฉันไหม · กี่โมงแล้ว · เห็นฉันไหม · หนียังไง";
  if (UI2) { form.dropdown(who, names, { defaultValueIndex: def }); form.textField(say, "§8...", { defaultValue: "" }); }
  else { form.dropdown(who, names, def); form.textField(say, "§8...", ""); }
  try { form.submitButton("§4§lพูด"); } catch (e) {}
  const r = await showForm(p, form);
  if (r.canceled || !r.formValues) return;
  const n = Number(r.formValues[0]) + 1, text = String(r.formValues[1] ?? "");
  try {
    if (!talkTo(p, n, text)) { try { p.sendMessage(L.none); p.onScreenDisplay.setActionBar(L.none); } catch (e) {} }
  } catch (e) { report(p, e); }
}

// ================= v1.6 สมุด: หน้าเมนูใหม่ (ไอคอน รูปผี สถานะ) และหน้าอ่านที่จัดรูปแบบใหม่ =================
const ICON = {
  talk: "textures/ghost_ui/talk", how: "textures/ghost_ui/scroll", house: "textures/items/board_wood",
  hide: "textures/items/closet", soul: "textures/ghost_ui/soul", eye: "textures/ghost_ui/eye", back: "textures/ghost_ui/back",
  ghost: (n) => `textures/ghost_ui/g${n}`,
};
const RULE = "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬";
const EPIGRAPHS = [
  "อย่าอ่านออกเสียง... มันได้ยิน", "หน้าที่หายไป มีคนฉีกออกไปเอง", "ถ้าได้ยินเสียงเรียกชื่อ อย่าขานรับ",
  "เล่มนี้เคยเป็นของคนที่อยู่ที่นี่ก่อนคุณ", "มันอ่านหนังสือเล่มนี้ได้เหมือนกัน", "นับให้ครบห้า... แล้วอย่าหันไปมอง",
  "ได้ยินเสียงเคาะ อย่าเปิดประตู", "หมึกยังไม่แห้ง... ทั้งที่ไม่มีใครเขียน", "มีรอยนิ้วมือเปื้อนเลือดที่ไม่ใช่ของคุณ",
];
// ตราคำสาปของผีแต่ละตน (ชื่อเดียวกับใน curse.js)
const MARK_NAME = { 1: "เสียงร่ำไห้", 2: "น้ำตาเจ้าสาว", 3: "เถ้าถ่าน", 4: "รอยยิ้ม", 5: "ความหิวกระหาย" };
function distTo(p, g) { try { return Math.round(Math.hypot(g.location.x - p.location.x, g.location.y - p.location.y, g.location.z - p.location.z)); } catch (e) { return -1; } }
function awake(p) {
  const out = [];
  for (const g of GHOSTS) {
    const e = findGhost(g.n);
    if (!e) continue;
    let same = false; try { same = e.dimension.id === p.dimension.id; } catch (er) {}
    out.push({ g, d: same ? distTo(p, e) : -1 });
  }
  return out;
}
// จัดหน้าข้อความเดิมให้อ่านง่าย: หัวข้อ = ✝ สีเลือด, รายการ = ▸
function styled(text) {
  const lines = String(text).split("\n");
  const out = [];
  for (const raw0 of lines) {
    const raw = tx(raw0);   // แปลทั้งบรรทัดก่อนจัดหน้า ไม่งั้น • และ §r ที่ถูกแก้จะทำให้ไม่ตรงพจนานุกรม
    if (raw.startsWith("§l§c")) {
      if (out.length) { if (out[out.length - 1] !== "") out.push(""); out.push(RULE); }
      out.push("§c§l✝ " + raw.slice(4).replace(/§r$/, "") + "§r");
    } else if (raw.startsWith("• ")) out.push("§8▸ §7" + raw.slice(2).replace(/§r/g, "§r§7"));
    else if (raw === "") { if (out.length && out[out.length - 1] !== "") out.push(""); }
    else out.push("§7" + raw);
  }
  return out.join("\n");
}
const EN_TIER = ["normal", "shaken", "hollow", "almost gone"];
function statusBlock(p) {
  const L2 = [];
  const v = getSoul(p), tier = soulTier(v);
  L2.push(`§7วิญญาณ   ${soulBar(v)} §f${Math.round(v)}% §8(${isEn() ? EN_TIER[tier] : TIER_NAME[tier].replace(/§./g, "")})`);
  const aw = awake(p);
  if (!aw.length) L2.push("§7ผีที่ตื่นอยู่   §8ไม่มี... ตอนนี้");
  else for (const a of aw) L2.push(`§7ผีที่ตื่นอยู่   §c● ${a.g.name} §8${a.d >= 0 ? `ห่าง ${a.d} บล็อก` : "อยู่อีกมิติ"}`);
  const f = flashStatus(p);
  if (f.has) {
    const frac = f.full ? f.charge / f.full : 0, col = frac <= 0.15 ? "§c" : frac <= 0.4 ? "§e" : "§a";
    L2.push(`§7ไฟฉาย      ${f.charge > 0 ? col + fmtCharge(f.charge) : "§cแบตหมด"}${f.on ? " §e(เปิดอยู่)" : ""} §8· แบตสำรอง ${f.batteries} ก้อน`);
  } else L2.push(`§7ไฟฉาย      §8ไม่มี${f.batteries ? ` · แบตสำรอง ${f.batteries} ก้อน` : ""}`);
  return L2.join("\n");
}
function menuBody(p) {
  const epi = EPIGRAPHS[Math.floor(Math.random() * EPIGRAPHS.length)];
  return [RULE, `§c§o   "${epi}"`, RULE, "", statusBlock(p), "", "§8ผีหลอน 5 ตนล่าอยู่ในโลกนี้ คุยกับมันได้เฉพาะตอนที่มันตื่นอยู่"].join("\n");
}
function ghostPage(p, n) {
  const g = GHOSTS[n - 1], T = TOPIC_LINES[n] || {};
  const e = findGhost(n);
  const sz = SIZE[n] || [1, 3], pr = PROFILE[n] || { up: 2 };
  const P_NAME = isEn() ? "Short name" : "ชื่อย่อ", P_BODY = isEn() ? "§7Body" : "§7รูปร่าง";
  const desc = String(L.pages[n] || "").split("\n\n").filter((x) => !x.startsWith(P_NAME) && !x.startsWith(P_BODY));
  const shape = String(L.pages[n] || "").split("\n\n").find((x) => x.startsWith(P_BODY)) || "";
  const story = (T.death && T.death[0]) || "";
  const weak = (T.escape && T.escape[0]) || "";
  const out = [
    `§c§l✝ ${g.name} ✝`,
    `§8ชื่อย่อ §7${g.key} §8· สูงราว §7${sz[1] < 2.5 ? "ตัวคน" : Math.round(sz[1]) + " บล็อก"} §8· ปีนได้ §7${pr.up} บล็อก`,
    RULE, "",
    "§c§lเรื่องของมัน", `§7"${story}"`, "",
    "§c§lนิสัย", ...desc.map((x) => "§7" + x.replace(/§r/g, "§r§7")), "",
    "§c§lรูปร่าง", "§7" + shape.replace(/^§7(?:รูปร่าง|Body):\s*/, ""), "",
    "§c§lจุดอ่อน (มันบอกเอง)", `§7"${weak}"`, "",
    "§c§lตราคำสาป", `§7ของต้องสาปจากตนนี้มีตรา §d${MARK_NAME[n]}`, "",
    RULE,
    e ? `§c● ตื่นอยู่ตอนนี้ §8${(() => { const d = distTo(p, e); return d >= 0 ? `(ห่าง ${d} บล็อก)` : "(อยู่อีกมิติ)"; })()}` : "§8○ หลับอยู่... คุยไม่ได้จนกว่ามันจะมา",
  ];
  return out.join("\n");
}
const FLASH_PAGE = [
  "§l§cไฟฉาย§r",
  "• คราฟไฟฉาย (โต๊ะคราฟ): §fกระจก§r ช่องบนกลาง · §fเหล็กแท่ง§r ซ้าย-ขวา-ล่าง · §fคบเพลิงเรดสโตน§r ตรงกลาง",
  "• คราฟแบต: §fเศษเหล็ก§r บน · §fเรดสโตน§r กลาง · §fทองแดงแท่ง§r ล่าง (เรียงแนวตั้ง) ได้ 1 ก้อน",
  "• คลิกขวา = เปิด/ปิด · ย่อ + คลิกขวา = เปลี่ยนแบต · ถือแบตแล้วคลิกขวา = ใส่ให้ไฟฉายในมือรอง",
  "• แบต 1 ก้อนส่องได้ §e5 นาที§r (นับเฉพาะตอนเปิด) แถบใต้ไอคอนไฟฉายคือแบตที่เหลือ",
  "• แบตใกล้หมดไฟจะกะพริบ และถ้าผีอยู่ใกล้ ไฟจะกะพริบวูบวาบ",
  "• ระวัง: ผีเห็นแสงไฟฉายได้ไกลกว่าปกติ ถ้าจะซ่อนตัว ให้ปิดไฟก่อน",
  "",
  "§l§cวิญญาณ§r",
  "• ของต้องสาปค่อย ๆ ดูดวิญญาณ ทั้งตอนพกไว้ในตัว และทุกครั้งที่คำสาปทำงาน",
  "• §dต่ำกว่า 70%§r: โดนตีเจ็บขึ้น 25% · หิวง่าย",
  "• §5ต่ำกว่า 40%§r: เจ็บขึ้น 60% · เดินช้า · อ่อนแรง · หิวเร็วมาก",
  "• §4ต่ำกว่า 15%§r: เจ็บเป็น 2 เท่า · ของในมือและชุดเกราะผุพังจนแตก",
  "• วิญญาณหมดเมื่อไหร่... มันจะมาเอาส่วนที่เหลือ",
  "• ฟื้นได้: รอเฉย ๆ (+1 ทุก 30 วินาที ถ้าไม่ได้พกของต้องสาป) · แอปเปิลทอง +15 · แอปเปิลทองอาบมนตร์ +50 · นอนหลับจนเช้า +35 · ตายแล้วเกิดใหม่ได้อย่างน้อย 40",
  "",
  "§l§cของต้องสาป§r",
  "• ดีกว่าของธรรมดา แต่ทุกครั้งที่ใช้ คำสาปของมันจะทำงาน",
  "• ของแต่ละอย่างมีคำสาปเป็นของตัวเอง (ดูได้ในคำอธิบายใต้ชื่อไอเทม) และมีตราของผีที่มอบให้",
  "• ของยิ่งดี คำสาปยิ่งแรง",
].join("\n");

async function readPage(p, title, body, extra) {
  const page = new ActionFormData().title(title).body(body);
  if (extra) page.button(extra.label, extra.icon);
  page.button("§8§lกลับ", ICON.back);
  const r = await showForm(p, page);
  if (r.canceled) return "close";
  if (extra && r.selection === 0) return "extra";
  return "back";
}

async function openGuide(p) {
  for (let guard = 0; guard < 30; guard++) {
    const f = new ActionFormData().title(glitchName()).body(menuBody(p));
    const here = GHOSTS.map((g) => !!findGhost(g.n));
    const items = [];
    const add = (label, icon, act) => { f.button(label, icon); items.push(act); };
    add("§4§lคุยกับผี\n§r§8ถามอะไรก็ได้ มันจะตอบ", ICON.talk, "talk");
    add("§0§lวิธีคุยและต่อรอง\n§r§8ถามตรง ๆ · ยื่นของ · อย่ายั่วมัน", ICON.how, "how");
    add("§0§lประตู หน้าต่าง และไม้กั้น\n§r§8ตอกไม้ · ซ่อมของ · ผีพังอะไรได้", ICON.house, "house");
    add("§0§lการหนีและแอบซ่อน\n§r§8วิ่งสุดแรง · ตู้ · ใต้เตียง", ICON.hide, "hide");
    add("§0§lไฟฉาย วิญญาณ และคำสาป\n§r§8แบต 5 นาที · วิญญาณที่ถูกสูบ", ICON.soul, "soul");
    for (const g of GHOSTS) add(`${here[g.n - 1] ? "§4" : "§0"}§l${g.name}\n§r${here[g.n - 1] ? "§c● ตื่นอยู่" : "§8○ หลับอยู่"} §8· ${g.key}`, ICON.ghost(g.n), "g" + g.n);
    if (api.openAsk && api.askLabel) add(api.askLabel(), ICON.eye, "ask");
    add("§0§l🌐 ภาษา / Language\n§r§8ไทย · English", ICON.how, "lang");
    const r = await showForm(p, f);
    if (r.canceled) return;
    const act = items[r.selection];
    if (act === "talk") { await openTalk(p); return; }
    if (act === "ask") { await api.openAsk(p); return; }
    if (act === "lang") { await openLangMenu(p); continue; }
    let res;
    if (act === "how") res = await readPage(p, "§4§lวิธีคุยและต่อรอง", styled(L.how(chatAvailable())), { label: "§4§lลองคุยเลย", icon: ICON.talk });
    else if (act === "house") res = await readPage(p, "§4§lประตู หน้าต่าง และไม้กั้น", styled(L.house));
    else if (act === "hide") res = await readPage(p, "§4§lการหนีและแอบซ่อน", styled(L.hide));
    else if (act === "soul") res = await readPage(p, "§4§lไฟฉาย วิญญาณ และคำสาป", styled(FLASH_PAGE));
    else if (act && act[0] === "g") {
      const n = Number(act.slice(1));
      res = await readPage(p, `§4§l${GHOSTS[n - 1].name}`, ghostPage(p, n), { label: `§4§lคุยกับ ${GHOSTS[n - 1].name}`, icon: ICON.ghost(n) });
      if (res === "extra") { await openTalk(p, n); return; }
    }
    if (res === "extra") { await openTalk(p); return; }
    if (res === "close") return;
  }
}

export function initTalk(a) {
  api = a;
  const lastUse = new Map();
  world.afterEvents.itemUse.subscribe((ev) => {
    try {
      if (!ev.itemStack || ev.itemStack.typeId !== GUIDE_ID) return;
      const p = ev.source, now = system.currentTick;
      if (now - (lastUse.get(p.id) ?? -1e9) < 20) return;
      lastUse.set(p.id, now);
      openGuide(p).catch((e) => report(p, e));
    } catch (e) {}
  });
  world.afterEvents.playerSpawn.subscribe((ev) => {
    try {
      if (!ev.initialSpawn) return;
      const p = ev.player;
      if (p.hasTag("ghost_guide_given")) return;
      system.runTimeout(() => {
        try {
          const book = new ItemStack(GUIDE_ID, 1);
          try { book.keepOnDeath = true; } catch (_) {}
          p.getComponent("minecraft:inventory").container.addItem(book);
          p.addTag("ghost_guide_given");
        } catch (e) {}
      }, 60);
    } catch (e) {}
  });
  if (chatAvailable()) {
    world.beforeEvents.chatSend.subscribe((ev) => {
      try {
        const parsed = parseChat(ev.message);
        if (!parsed) return;
        const p = ev.sender;
        system.run(() => { try { talkTo(p, parsed.n, parsed.text); } catch (e) {} });
      } catch (e) {}
    });
  }
}
