import { cmdCancel, cmdCome, cmdFight, cmdFood, cmdGather, cmdGiveBack, cmdMine, cmdRename, cmdResume, cmdStop, cmdWork, giveBook, say, statusLines, } from "./actions.js";
import { houseStep, mineStep, ownerHome, startPlan, workStep } from "./plan.js";
import { getLastOpts } from "./builder.js";
import { LEVEL_HOWTO, describeHealth, effHp, getLevel, isFullHp, isKO, levelText, maxHp } from "./combat.js";
import { ORE_GROUPS } from "./data.js";
import { MATS, ROOFS, SIZES, STYLES, TYPES, WOODS, houseName, variantName } from "./house_gen.js";
import { MODELS } from "./house_statue.js";
import { lastHelper, ownedHelpers, rememberHelper } from "./registry.js";
import { getHome, getMode, getName, getSettings, modeLabel, saveSettings } from "./state.js";
import { dist } from "./util.js";
import { world } from "@minecraft/server";
import { VERSION } from "./version.js";
import { ORDER_KEYS, fixTypos } from "./nlu.js";
import { smallTalk } from "./chat.js";
import { attackOf, speedOf, cmdAttack, cmdSpeed } from "./attack.js";
// ------------------------------------------------------------------ text helpers
const PARTICLES = /(?:^|(?<=\s))(?:ครับผม|ครับ|ค่ะ|คะ|นะครับ|นะคะ|นะจ๊ะ|นะจ้ะ|จ้า|จ้ะ|จ๊ะ|ด้วยนะ|หน่อยนะ|หน่อย|เถอะ|เถิด|ซิ|สิ(?!บ)|นะ|เลยนะ|ละ|ล่ะ)(?=\s|$)|(?:ครับผม|ครับ|ค่ะ|คะ|นะครับ|นะคะ|นะจ๊ะ|จ้า|จ้ะ|จ๊ะ|หน่อยนะ|หน่อย|เถอะ|ซิ|นะ|ล่ะ)$/g;
export function norm(s) {
    let t = s
        .replace(/§./g, "")
        .toLowerCase()
        .replace(/[๐-๙]/g, (d) => String(d.charCodeAt(0) - 0x0e50))
        .replace(/(.)\1{2,}/g, "$1$1") // "มาาาา" -> "มาา"
        .replace(/[!！?？~…]+/g, " ")
        .replace(/\s+/g, " ")
        .trim();
    for (let i = 0; i < 2; i++)
        t = t.replace(PARTICLES, "").replace(/\s+/g, " ").trim();
    return t;
}
const TH_D = {
    หนึ่ง: 1, เอ็ด: 1, สอง: 2, ยี่: 2, สาม: 3, สี่: 4, ห้า: 5, หก: 6, เจ็ด: 7, แปด: 8, เก้า: 9,
};
const EN_D = {
    one: 1, two: 2, three: 3, four: 4, five: 5, six: 6, seven: 7, eight: 8, nine: 9, ten: 10, twenty: 20, thirty: 30,
};
/** Turns number words into digits (only used to read numbers, never for matching keywords). */
export function numify(t) {
    let s = t.replace(/ลบ\s*/g, "-").replace(/ติดลบ\s*/g, "-");
    s = s.replace(/(หนึ่ง|สอง|ยี่|สาม|สี่|ห้า|หก|เจ็ด|แปด|เก้า)?สิบ(เอ็ด|หนึ่ง|สอง|สาม|สี่|ห้า|หก|เจ็ด|แปด|เก้า)?/g, (_m, a, b) => String((a ? TH_D[a] : 1) * 10 + (b ? TH_D[b] : 0)));
    s = s.replace(/(หนึ่ง|สอง|สาม|สี่|ห้า|หก|เจ็ด|แปด|เก้า)/g, (m) => String(TH_D[m]));
    s = s.replace(/\b(one|two|three|four|five|six|seven|eight|nine|ten|twenty|thirty)\b/g, (m) => String(EN_D[m]));
    s = s.replace(/เดียว/g, "1");
    return s;
}
const WORD_CACHE = new Map();
/** Thai words match anywhere (no spaces in Thai); English words must be whole words. */
const has = (t, ...words) => words.some((w) => {
    if (!/^[a-z' ]+$/.test(w))
        return t.includes(w);
    let re = WORD_CACHE.get(w);
    if (!re) {
        re = new RegExp(`(^|[^a-z])${w.replace(/'/g, "'?")}([^a-z]|$)`);
        WORD_CACHE.set(w, re);
    }
    return re.test(t);
});
const ORE_WORDS = [
    ["copper", /(ทองแดง|copper)/],
    ["gold", /(ทอง(?!แดง)|gold)/],
    ["diamond", /(เพชร|diamond)/],
    ["iron", /(เหล็ก|iron)/],
    ["coal", /(ถ่าน|coal)/],
    ["redstone", /(เรดสโตน|หินแดง|redstone)/],
    ["lapis", /(ลาพิส|ลาปิส|lapis)/],
    ["emerald", /(มรกต|emerald)/],
    ["nether", /(ควอตซ์|ควอทซ์|quartz)/],
    ["debris", /(เศษโบราณ|debris|netherite|เนเธอร์ไรท์)/],
];
function oresIn(t) {
    const out = [];
    for (const [g, re] of ORE_WORDS)
        if (re.test(t))
            out.push(g);
    return out;
}
function dirIn(t) {
    if (/(ตะวันออก|\beast\b)/.test(t))
        return "east";
    if (/(ตะวันตก|\bwest\b)/.test(t))
        return "west";
    if (/((ทิศ|ทาง|ไป)\s*เหนือ|\bnorth\b)/.test(t))
        return "north";
    if (/((ทิศ|ทาง|ไป)\s*ใต้(?!ดิน)|\bsouth\b)/.test(t))
        return "south";
    return undefined;
}
// ------------------------------------------------------------------ mine description
const ORE_LEVEL = {
    diamond: -54, redstone: -54, gold: -16, iron: 16, copper: 48, lapis: 0, coal: 48, debris: 15,
};
export function parseMine(t) {
    const n = numify(t);
    const o = {};
    const said = [];
    let m = n.match(/(?:\by\b|ระดับ|ความสูง|\blevel\b)\s*[=:]?\s*(-?\d+)/);
    if (m) {
        o.y = Number(m[1]);
        said.push(`ระดับ Y=${o.y}`);
    }
    m = n.match(/(?:ลึก|depth|deep)\s*(?:ลง)?\s*(\d+)/) ?? n.match(/(?:ลง(?:ไป)?)\s*(\d+)\s*(?:บล็อก|blocks?)/) ??
        n.match(/(\d+)\s*(?:บล็อก|blocks?)?\s*(?:down|deep)/);
    if (m && o.y === undefined) {
        o.depth = Math.abs(Number(m[1]));
        said.push(`ลึก ${o.depth} บล็อก`);
    }
    m = n.match(/(?:ยาว|length|long)\s*(\d+)/) ?? n.match(/(\d+)\s*(?:บล็อก|blocks?)?\s*long/);
    if (m) {
        o.length = Number(m[1]);
        said.push(`ยาว ${o.length}`);
    }
    m = n.match(/(?:กิ่ง|แขนง|ทางแยก|branch(?:es)?)\s*(?:ยาว|length|long)?\s*(\d+)/);
    if (m) {
        o.branch = Number(m[1]);
        said.push(`กิ่งยาว ${o.branch}`);
    }
    if (/(ไม่(ต้อง)?(มี|เอา)?(กิ่ง|แขนง|ทางแยก)|no branch|without branch)/.test(t)) {
        o.branch = 0;
        said.push("ไม่มีกิ่ง");
    }
    if (/(แนวนอน|ไม่ต้องลง|ไม่ต้องขุดลง|ระดับนี้|ตรงระดับนี้|\bflat\b|horizontal|this level|same level|straight)/.test(t)) {
        o.flat = true;
        said.push("แนวนอนที่ระดับนี้");
    }
    const d = dirIn(t);
    if (d) {
        o.dir = d;
        said.push({ north: "ไปทิศเหนือ", south: "ไปทิศใต้", east: "ไปทิศตะวันออก", west: "ไปทิศตะวันตก" }[d]);
    }
    if (o.y === undefined && o.depth === undefined && !o.flat) {
        const ores = oresIn(t);
        for (const g of ores) {
            const lv = ORE_LEVEL[g];
            if (lv !== undefined) {
                o.y = lv;
                said.push(`ลงไปที่ Y=${lv} (ดีสำหรับ${ORE_GROUPS.find((x) => x.id === g)?.th ?? g})`);
                break;
            }
        }
    }
    if (/(ตรงนั้น|ที่นั่น|ตรงที่นาย|ตรงที่อยู่|where you are|\bthere\b)/.test(t))
        o.atHelper = true;
    return { o, said };
}
// ------------------------------------------------------------------ house description
// v1.6: new building types. "strong" words are enough on their own, "weak" words need a build verb.
const NEW_TYPES = [
    ["underground", /(ห้องนิรภัย|นิรภัย|\bvault\b|ไซโล|\bsilo\b)/, true],
    ["statue", /(รูปปั้น|ปั้น|ประติมากรรม|โมเดล|statue|sculpture|\bmodel\b|pixel ?art|พิกเซล)/, true],
    ["portal", /(ประตูเนเธอร์|ประตูนรก|ประตูมิติ|พอร์ทัล|พอร์ตัล|nether ?portal|\bportal\b)/, true],
    ["prison", /(คุก|เรือนจำ|ที่คุมขัง|ห้องขัง|สถานีตำรวจ|\bprison\b|\bjail\b|\bcell ?block\b)/, true],
    ["hospital", /(โรงพยาบาล|สถานพยาบาล|คลินิก|สถานีอนามัย|\bhospital\b|\bclinic\b)/, true],
    ["hotel", /(โรงแรม|โฮเทล|โฮสเทล|เกสต์เฮ้าส์|เกสต์เฮาส์|โรงเตี๊ยม|ที่พักแรม|\bhotel\b|\bhostel\b|\bmotel\b|\binn\b|guest ?house)/, true],
    ["school", /(โรงเรียน|วิทยาลัย|มหาวิทยาลัย|อนุบาล|ห้องเรียน|\bschool\b|\bcollege\b|\buniversity\b|\bacademy\b|\bclassroom\b)/, true],
    ["library", /(ห้องสมุด|หอสมุด|พิพิธภัณฑ์|\blibrary\b|\bmuseum\b)/, true],
    ["church", /(โบสถ์|มหาวิหาร|ศาลเจ้า|มัสยิด|\bchurch\b|\bchapel\b|\bcathedral\b|\btemple\b|\bshrine\b|\bmosque\b|วัด)/, false],
    ["barn", /(โรงนา|คอกม้า|คอกสัตว์|เล้าไก่|คอกวัว|คอกหมู|โรงม้า|โกดัง|คลังสินค้า|โรงงาน|โรงจอดรถ|โรงรถ|\bbarn\b|\bstable\b|\bcoop\b|\bwarehouse\b|\bgarage\b|\bfactory\b)/, true],
    ["market", /(ตลาด|ร้านค้า|ร้านขาย|ร้านอาหาร|ร้านกาแฟ|คาเฟ่|ซุปเปอร์|\bmarket\b|\bshop\b|\bbazaar\b|\bstore\b|\bcafe\b|\brestaurant\b)/, false],
];
const BUILD_VERB = /(สร้าง|ทำ|ก่อ|ปั้น|อยากได้|ขอ|ตั้ง|เอา|build|make|create|construct|put up|want)/;
export function matchNewType(t) {
    for (const [id, re, strong] of NEW_TYPES)
        if (re.test(t) && (strong || BUILD_VERB.test(t)))
            return id;
    return undefined;
}
const BUNKER_WORDS = [
    [1, /(บังเกอร์ทหาร|ทหาร|ฐานทัพ|military|army|ทางเดินยาว|corridor)/],
    [2, /(โถง|ถ้ำ|ทรงกลม|วงกลม|cavern|cave|round|circular|dome)/],
    [3, /(นิรภัย|ห้องเก็บ|ของมีค่า|vault|treasure|safe room)/],
    [4, /(ไซโล|หลุมลึก|หลุมใหญ่|ปล่อง|silo|shaft|deep)/],
    [0, /(คลาสสิก|ธรรมดา|classic|basic|cellar|ห้องใต้ดิน)/],
];
const TYPE_RE = [
    ["underground", /(ใต้ดิน|ใต้พื้น|ฝังดิน|ขุดบ้าน|บังเกอร์|bunker|ฐานลับ|ห้องลับ|บ้านถ้ำ|ในถ้ำ|underground|basement|dugout|ฐานทัพลับ)/],
    ["safehouse", /(หลบภัย|ที่หลบ|ปลอดภัย|กันมอน|กันซอมบี้|กันครีปเปอร์|กันสัตว์ประหลาด|เซฟเฮ|เซฟ ?เฮ้?าส์|safe ?house|shelter|refuge|panic ?room|หลบมอน|หนีมอน)/],
    ["mansion", /(บ้านหรู|หรูหรา|หรู|คฤหาสน์|วิลล่า|วิลลา|villa|mansion|luxur|palace|พระราชวัง|วังหลวง|ไฮโซ|เศรษฐี|บ้านรวย|บ้านโอ่อ่า|โอ่อ่า|ระดับห้าดาว|รีสอร์ท|resort)/],
    ["lighthouse", /(ประภาคาร|light ?house)/],
    ["igloo", /(อิกลู|igloo|บ้านหิมะ|บ้านน้ำแข็ง|snow ?house)/],
    ["pyramid", /(ปิรามิด|พีระมิด|พีรามิด|pyramid)/],
    ["treehouse", /(บ้านต้นไม้|ต้นไม้|tree ?house|บนต้นไม้|บนต้น|บ้านบนต้น)/],
    ["nether", /(เนเธอร์|nether|ป้อมนรก|นรก|แบล็กสโตน|แบลคสโตน|blackstone)/],
    ["castle", /(ปราสาท|castle|ป้อมปราการ|ป้อม|fortress|keep|อัศวิน)/],
    ["tower", /(หอคอย|หอสูง|หอสังเกต|หอนาฬิกา|tower|watchtower)/],
    ["desert", /(ทะเลทราย|desert|อาหรับ|arab)/],
];
const TYPE_KEYS = ["โรงแรม", "โรงเรียน", "โรงพยาบาล", "ห้องสมุด", "รูปปั้น", "ประตูเนเธอร์", "เรือนจำ", "โรงนา", "ปราสาท", "ปิรามิด", "อิกลู", "หอคอย", "คฤหาสน์", "ประภาคาร", "ใต้ดิน", "บังเกอร์", "บ้านหรู", "หลบภัย", "ต้นไม้", "ทะเลทราย", "เนเธอร์", "แบล็กสโตน", "ดีพสเลท", "ควอตซ์", "โมเดิร์น", "ยุคกลาง", "castle", "pyramid", "igloo", "tower", "mansion", "lighthouse", "underground", "bunker", "treehouse", "shelter", "villa", "luxury", "safehouse"];
const MAT_RE = [
    [1, /(อิฐหิน|หินอิฐ|stone ?brick|หิน|stone|cobble|แกรนิต|granite)/],
    [3, /(อิฐแดง|อิฐ|brick)/],
    [4, /(หินทราย|ทราย|sand)/],
    [5, /(ดีพสเลท|ดีปสเลท|deepslate)/],
    [6, /(แบล็กสโตน|แบลคสโตน|หินดำ|blackstone)/],
    [7, /(ควอตซ์|ควอทซ์|quartz|ขาวล้วน)/],
    [8, /(อิฐโคลน|โคลน|mud)/],
    [9, /(ปริซึม|prismarine)/],
];
export function parseHouse(raw0) {
    const t0 = fixTypos(raw0, TYPE_KEYS);
    let t = t0;
    const n = numify(t0);
    const o = {};
    const said = [];
    // ---- exact size ("กว้าง 12 ยาว 16", "12x16", "สูง 30 บล็อก")
    {
        let m = n.match(/(\d+)\s*(?:x|×|\*|คูณ|by)\s*(\d+)/);
        if (m) {
            o.w = Math.max(5, Math.min(31, Number(m[1])));
            o.d = Math.max(5, Math.min(31, Number(m[2])));
            said.push(`กว้าง ${o.w} ยาว ${o.d}`);
        }
        else {
            m = n.match(/(?:กว้าง|width|wide)\s*(\d+)/);
            if (m) {
                o.w = Math.max(5, Math.min(31, Number(m[1])));
                said.push(`กว้าง ${o.w}`);
            }
            m = n.match(/(?:ยาว|ลึก|length|long|deep)\s*(\d+)/);
            if (m) {
                o.d = Math.max(5, Math.min(31, Number(m[1])));
                said.push(`ยาว ${o.d}`);
            }
        }
        m = n.match(/(?:สูง|height|tall)\s*(\d+)\s*(?:บล็อก|บล็อค|เมตร|blocks?|m)?(?!\s*ชั้น)/);
        if (m && !/(สูง|height|tall)\s*\d+\s*(ชั้น|floor)/.test(n)) {
            o.height = Math.max(8, Math.min(60, Number(m[1])));
            said.push(`สูง ${o.height} บล็อก`);
        }
    }
    // ---- relative changes to the last design ("ใหญ่ขึ้น", "เพิ่มอีกชั้น", "แบบเดิม")
    {
        const rel = {};
        if (/(ใหญ่ขึ้น|ใหญ่กว่า|ใหญ่กว่าเดิม|ขยาย|ใหญ่อีก|bigger|larger|expand)/.test(t0))
            rel.size = 1;
        else if (/(เล็กลง|เล็กกว่า|ย่อ|smaller|shrink)/.test(t0))
            rel.size = -1;
        if (/(เพิ่มชั้น|อีกชั้น|สูงขึ้น|สูงกว่า|เพิ่มอีกชั้น|more floors|taller|one more floor)/.test(t0))
            rel.floors = 1;
        else if (/(ลดชั้น|เตี้ยลง|น้อยชั้นลง|fewer floors|shorter)/.test(t0))
            rel.floors = -1;
        if (/(แบบเดิม|เหมือนเดิม|อันเดิม|หลังเดิม|เหมือนหลังที่แล้ว|อีกหลัง|same as before|same again|another one|อีกหลังนึง)/.test(t0))
            rel.same = true;
        if (Object.keys(rel).length) {
            o.rel = rel;
            said.push("ปรับจากแบบล่าสุด" + (rel.size ? (rel.size > 0 ? " ใหญ่ขึ้น" : " เล็กลง") : "") + (rel.floors ? (rel.floors > 0 ? " เพิ่มชั้น" : " ลดชั้น") : ""));
        }
    }
    // ---- house type (underground, castle, tower ...)
    const nt = matchNewType(t0);
    if (nt) {
        o.type = nt;
        said.push(`ประเภท${TYPES.find((x) => x.id === nt)?.th.split(" (")[0] ?? nt}`);
    }
    for (const [id, re] of nt ? [] : TYPE_RE) {
        if (re.test(t0)) {
            o.type = id;
            said.push(`ประเภท${TYPES.find((x) => x.id === id)?.th.split(" (")[0] ?? id}`);
            break;
        }
    }
    // ---- v1.6: choose a variant / model, or ask for a random new one
    {
        const vm = n.match(/(?:แบบที่|แบบ|variant|version|v)\s*(\d{1,2})(?!\s*(?:ชั้น|floor|x|×|คูณ))/);
        if (vm && Number(vm[1]) >= 1 && o.type) {
            o.variant = Number(vm[1]) - 1;
            said.push(`แบบที่ ${vm[1]}`);
        }
        else if (o.type === "underground") {
            for (const [vi, re] of BUNKER_WORDS)
                if (re.test(t0.replace(/ใต้ดิน/g, " "))) {
                    o.variant = vi;
                    said.push(`แบบ${["ห้องใต้ดินคลาสสิก", "บังเกอร์ทหาร", "โถงถ้ำทรงกลม", "ห้องนิรภัย", "ไซโลหลุมลึก"][vi]}`);
                    break;
                }
        }
        if (o.type === "statue") {
            const mi = MODELS.findIndex((m) => m.re.test(t0));
            if (mi >= 0) {
                o.model = mi;
                said.push(`โมเดล${MODELS[mi].th}`);
            }
        }
        if (o.variant === undefined && /(สุ่ม|random|แบบอื่น|แบบใหม่|อีกแบบ|different|another style|surprise me)/.test(t0)) {
            o.variant = Math.floor(Math.random() * 1000);
            said.push("สุ่มแบบใหม่");
        }
    }
    // roof first, so "spruce roof" is not read as the wood of the house
    let roofStr = "";
    const rTh = t.match(/หลังคา\s*((?:สี\s*)?[^\s,]*)/);
    const rEn = t.match(/((?:[a-z]+\s)?[a-z]+)\s+roof/) ?? t.match(/roof\s+(?:of\s+|made of\s+)?((?:[a-z]+\s)?[a-z]+)/);
    if (rTh) {
        roofStr = rTh[1];
        t = t.replace(rTh[0], " ");
    }
    else if (rEn) {
        roofStr = rEn[1];
        t = t.replace(rEn[0], " ");
    }
    if (roofStr) {
        let r = -1;
        if (/(อิฐ|brick|ส้ม|แดง|red|orange)/.test(roofStr))
            r = 4;
        else if (/(ดีพ|deepslate|slate|กระเบื้อง|tile|เทา|ดำ|gr[ae]y|black)/.test(roofStr))
            r = 5;
        else if (/(หิน|stone)/.test(roofStr))
            r = 6;
        else if (/(โอ๊คเข้ม|ดาร์ค|dark)/.test(roofStr))
            r = 3;
        else if (/(สปรูซ|spruce|น้ำตาล|brown)/.test(roofStr))
            r = 2;
        else if (/(เดียวกัน|same|match)/.test(roofStr))
            r = 1;
        if (r >= 0) {
            o.roof = r;
            said.push(`หลังคา${ROOFS[r].split(" (")[0]}`);
        }
    }
    if (/(ใหญ่|large|\bbig\b|huge|giant|massive|ยักษ์|มหึมา|ใหญ่โต|กว้างขวาง|โอ่อ่า|สุดหรู)/.test(t) && !(o.rel?.size))
        o.size = 2;
    else if (/(กลาง|medium|\bmid\b|normal size|ขนาดปกติ)/.test(t))
        o.size = 1;
    else if (/(เล็ก|small|tiny|little|จิ๋ว|มินิ|mini|กะทัดรัด|compact)/.test(t) && !(o.rel?.size))
        o.size = 0;
    if (o.size !== undefined)
        said.push(`ขนาด${SIZES[o.size].th}`);
    const fm = n.match(/(\d+)\s*(?:ชั้น|floors?|stor(?:e)?ys?|stories|levels?)/) ??
        n.match(/(?:ชั้น|floors?)\s*(\d+)/);
    if (fm) {
        const f = Number(fm[1]);
        o.floors = Math.max(1, Math.min(10, f));
        o.floorsExact = true;
        said.push(`${o.floors} ชั้น`);
    }
    else if (/(single.?stor|one.?stor|bungalow)/.test(t)) {
        o.floors = 1;
        said.push("1 ชั้น");
    }
    if (/(โมเดิร์น|โมเดิน|modern|ทันสมัย|มินิมอล|minimal|contemporary|หรูหรา|luxury)/.test(t))
        o.style = 2;
    else if (/(ยุคกลาง|medieval|ปราสาท|castle|อัศวิน|knight)/.test(t))
        o.style = 1;
    else if (/(เรือนไทย|ไทย|thai)/.test(t))
        o.style = 3;
    else if (/(คันทรี|country|cottage|กระท่อม|ชนบท|ฟาร์ม|farm|rustic|cabin)/.test(t))
        o.style = 0;
    if (o.style !== undefined)
        said.push(STYLES[o.style]);
    const WOOD_RE = [
        [5, /(โอ๊คเข้ม|โอ๊กเข้ม|ดาร์คโอ๊ค|dark ?oak|ไม้เข้ม)/],
        [8, /(โอ๊คซีด|โอ๊กซีด|pale ?oak|ไม้ซีด)/],
        [0, /(โอ๊ค|โอ๊ก|\boak\b)/],
        [1, /(สปรูซ|spruce|ไม้สน)/],
        [2, /(เบิร์ช|เบิช|birch|ไม้ขาว)/],
        [3, /(ป่าดิบ|จังเกิ้ล|จังเกิล|jungle)/],
        [4, /(อะเคเชีย|อาคาเซีย|อะคาเซีย|acacia)/],
        [6, /(โกงกาง|แมงโกรฟ|mangrove)/],
        [7, /(เชอร์รี่|เชอรี่|ซากุระ|cherry|sakura|ชมพู|pink)/],
    ];
    for (const [w, re] of WOOD_RE) {
        if (re.test(t)) {
            o.wood = w;
            said.push(WOODS[w].th);
            break;
        }
    }
    const neg = (words) => new RegExp(`(ไม่(ต้อง)?(เอา|มี|ใส่)?(${words}))`).test(t);
    if (neg("สระ") || /(no pool|without (a )?pool)/.test(t)) {
        o.pool = false;
        said.push("ไม่มีสระ");
    }
    else if (/(สระ|pool|swimming)/.test(t)) {
        o.pool = true;
        said.push("มีสระว่ายน้ำ");
    }
    if (neg("ปล่อง") || /no chimney/.test(t)) {
        o.chimney = false;
        said.push("ไม่มีปล่องไฟ");
    }
    else if (/(ปล่อง|chimney)/.test(t)) {
        o.chimney = true;
        said.push("มีปล่องไฟ");
    }
    if (neg("ระเบียง|เฉลียง|ทางเข้า|บันไดหน้าบ้าน") || /no porch/.test(t)) {
        o.porch = false;
        said.push("ไม่มีทางเข้าหน้าบ้าน");
    }
    else if (/(ระเบียง|เฉลียง|porch|veranda)/.test(t)) {
        o.porch = true;
        said.push("มีระเบียงหน้าบ้าน");
    }
    if (neg("ไฟ|ตะเกียง|โคม") || /no lights?/.test(t)) {
        o.light = false;
        said.push("ไม่มีตะเกียง");
    }
    if (neg("ของใช้|เฟอร์|ของในบ้าน|ของ") || /(no furniture|unfurnished|empty house|บ้านเปล่า)/.test(t)) {
        o.furniture = false;
        said.push("บ้านเปล่า ไม่มีของใช้");
    }
    else if (/(ของใช้|เฟอร์นิเจอร์|furniture|furnished|เตา|หีบ|ครบครัน)/.test(t)) {
        o.furniture = true;
        said.push("มีของใช้ครบ");
    }
    // ---- material (stone, brick, sand ... ). Higher-priority words first (deepslate/blackstone before plain stone)
    {
        let matIdx = -1;
        if (/(อิฐหิน|หินอิฐ|stone ?brick)/.test(t))
            matIdx = 1;
        for (const idx of matIdx >= 0 ? [] : [5, 6, 8, 9, 7, 4, 3, 1]) {
            const re = MAT_RE.find((m) => m[0] === idx)[1];
            if (re.test(t)) {
                matIdx = idx;
                break;
            }
        }
        if (matIdx < 0 && o.type && /(บ้านไม้|ไม้ล้วน|wooden|\bwood\b)/.test(t))
            matIdx = 2;
        if (matIdx >= 0 && !(matIdx === 4 && o.type === "desert") && !(matIdx === 6 && o.type === "nether")) {
            o.mat = matIdx;
            said.push(`วัสดุ${MATS[matIdx].th}`);
            if (!o.type && o.style === undefined) {
                o.type = "box";
                said.push("ประเภทบ้านทรงกล่อง");
            }
        }
    }
    if (o.type === undefined && o.style !== undefined)
        o.type = "standard";
    if (o.floorsExact && o.floors > 3 && (o.type === undefined || o.type === "standard"))
        said.push("(บ้านทั่วไปสูงสุด 3 ชั้น อยากได้สูงกว่านี้ลอง หอคอย หรือ คฤหาสน์)");
    if (/(เร็วมาก|เร็วที่สุด|very fast|super fast|fastest|ด่วนมาก)/.test(t))
        o.speed = 2;
    else if (/(เร็ว|\bfast\b|quick|ด่วน)/.test(t))
        o.speed = 1;
    else if (/(ช้าๆ|ช้า|\bslow\b)/.test(t))
        o.speed = 0;
    if (o.speed !== undefined)
        said.push(["สร้างแบบปกติ", "สร้างเร็ว", "สร้างเร็วมาก"][o.speed]);
    return { o, said };
}
// ------------------------------------------------------------------ blocks, animals, amounts
const BLOCK_WORDS = [
    { ids: ["bamboo"], th: "ไม้ไผ่", re: /(ไม้ไผ่|ไผ่|bamboo)/ },
    { ids: ["clay"], th: "ดินเหนียว", re: /(ดินเหนียว|\bclay\b)/ },
    { ids: ["soul_sand", "soul_soil"], th: "ทรายวิญญาณ", re: /(ทรายวิญญาณ|ดินวิญญาณ|soul sand|soul soil)/ },
    { ids: ["sandstone"], th: "หินทราย", re: /(หินทราย|sandstone)/ },
    { ids: ["red_sand"], th: "ทรายแดง", re: /(ทรายแดง|red sand)/ },
    { ids: ["sand", "red_sand"], th: "ทราย", re: /(ทราย|\bsand\b)/ },
    { ids: ["gravel"], th: "กรวด", re: /(กรวด|gravel)/ },
    { ids: ["granite"], th: "หินแกรนิต", re: /(แกรนิต|granite)/ },
    { ids: ["diorite"], th: "หินไดออไรต์", re: /(ไดออไรต์|ไดโอไรต์|ไดออไรท์|diorite)/ },
    { ids: ["andesite"], th: "หินแอนดีไซต์", re: /(แอนดีไซต์|แอนดีไซท์|แอนดิไซต์|andesite)/ },
    { ids: ["tuff"], th: "หินทัฟฟ์", re: /(ทัฟฟ์|ทัฟ|\btuff\b)/ },
    { ids: ["deepslate"], th: "ดีพสเลท", re: /(ดีพสเลท|ดีปสเลท|deepslate)/ },
    { ids: ["netherrack"], th: "เนเธอร์แร็ค", re: /(เนเธอร์แร็ค|เนเธอร์แรค|netherrack)/ },
    { ids: ["mud"], th: "โคลน", re: /(โคลน|\bmud\b)/ },
    { ids: ["moss_block"], th: "บล็อกมอส", re: /(มอส|\bmoss\b)/ },
    { ids: ["dirt", "grass_block", "coarse_dirt", "podzol", "rooted_dirt"], th: "ดิน", re: /(ดิน|\bdirt\b|\bsoil\b)/ },
    { ids: ["stone"], th: "หิน", re: /(หิน|\bstones?\b|cobblestone|cobble)/ },
];
function blockIn(t) {
    const s = t.replace(/ใต้ดิน|underground/g, " ");
    for (const b of BLOCK_WORDS)
        if (b.re.test(s))
            return { ids: b.ids.map((x) => "minecraft:" + x), th: b.th };
    return undefined;
}
const ANIMAL_WORDS = [
    ["minecraft:cow", /(วัว|\bcows?\b|\bbeef\b)/],
    ["minecraft:pig", /(หมู|\bpigs?\b|porkchop|\bpork\b)/],
    ["minecraft:chicken", /(ไก่|chickens?)/],
    ["minecraft:sheep", /(แกะ|\bsheep\b|mutton)/],
    ["minecraft:rabbit", /(กระต่าย|rabbits?)/],
];
function animalIn(t) {
    for (const [id, re] of ANIMAL_WORDS)
        if (re.test(t))
            return id;
    return undefined;
}
/** "20 ท่อน", "2 กอง", "จนเต็ม"... */
export function extractQty(t) {
    if (/(จน(กว่า)?(กระเป๋า)?(จะ)?เต็ม|ให้เต็ม|เต็มกระเป๋า|until (the |your )?(bag |inventory )?(is )?full|fill (up )?(your |the )?(bag|inventory))/.test(t)) {
        return { full: true };
    }
    const n = numify(t);
    const clampN = (v) => Math.max(1, Math.min(2304, Math.round(v)));
    let m = n.match(/(\d+)\s*(?:กอง|stacks?)/);
    if (m)
        return { n: clampN(Number(m[1]) * 64) };
    if (/(ครึ่งกอง|half a stack)/.test(t))
        return { n: 32 };
    if (/(?:^|[^\d])(กอง|a stack)/.test(n))
        return { n: 64 };
    m = n.match(/(\d+)\s*(?:ต้น|trees?)/);
    if (m)
        return { n: clampN(Number(m[1]) * 5), trees: true };
    m = n.match(/(\d+)\s*(?:ท่อน|ก้อน|อัน|ชิ้น|บล็อก|บล็อค|ตัว|ลูก|หัว|ใบ|เม็ด|blocks?|logs?|pieces?|items?|ores?|animals?)/);
    if (m)
        return { n: clampN(Number(m[1])) };
    m = n.match(/(?:^|[^\d\-.])(\d{1,4})(?![\d.]|\s*(?:ชั้น|วินาที|วิ|นาที|seconds?|minutes?|floors?))/);
    if (m)
        return { n: clampN(Number(m[1])) };
    return {};
}
// ------------------------------------------------------------------ intent
const NAME_RE = /(?:เปลี่ยนชื่อ(?:เป็น)?|ตั้งชื่อ(?:ว่า|เป็น|ให้)?|ต่อไปนี้(?:นาย)?ชื่อ|เรียกนายว่า|นายชื่อ|your name is|rename(?: you)?(?: to)?|i(?:'ll| will) call you|call you)\s*[:"'“]?\s*(.+?)["'”]?\s*$/i;
export function parseTalk(raw, noFix = false) {
    const t = norm(raw);
    if (!t)
        return { k: "unknown" };
    const nm = raw.replace(/§./g, "").trim().match(NAME_RE);
    if (nm && nm[1].trim())
        return { k: "name", name: nm[1].trim() };
    if (has(t, "ช่วยด้วย", "help me", "save me", "ช่วยฉันด้วย", "ช่วยเราด้วย"))
        return { k: "rescue" };
    if (t === "?" || t === "ช่วย" || has(t, "help", "ทำอะไรได้", "คำสั่ง", "วิธีใช้", "สอนใช้", "ใช้ยังไง", "สั่งอะไรได้"))
        return { k: "help" };
    if (has(t, "ยกเลิก", "cancel", "abort"))
        return { k: "cancel" };
    if (has(t, "อีกที", "อีกรอบ", "อีกครั้ง", "ซ้ำ", "ทำอีก", "again", "repeat", "one more time"))
        return { k: "repeat" };
    if (has(t, "ทำต่อ", "สร้างต่อ", "ขุดต่อ", "ทำงานต่อ", "continue", "resume", "กลับไปทำงาน"))
        return { k: "resume" };
    if (has(t, "ไม่ต้องสู้", "อย่าสู้", "เลิกสู้", "หยุดสู้", "ไม่สู้", "ห้ามสู้", "อย่าตี", "ห้ามตี", "ไม่ต้องตี", "สันติ", "peace", "don't fight", "dont fight", "no fight", "stop fighting", "stop attacking"))
        return { k: "fight", on: false };
    // 2.0: the work speed, and "go for that one" / a creature by name
    const sp = speedOf(t);
    if (sp)
        return { k: "speed", sp };
    const at = attackOf(t, extractQty(t).n);
    if (at)
        return { k: "attack", ...at };
    if (has(t, "ไม่ต้องตาม", "อย่าตาม", "don't follow", "dont follow"))
        return { k: "stay" };
    const nt = numify(t);
    const wm = nt.match(/(?:รอ|wait|พัก)\s*(?:ก่อน|for)?\s*(\d+)\s*(วินาที|วิ|seconds?|secs?|s\b|นาที|minutes?|mins?)/);
    if (wm)
        return { k: "wait", secs: Math.min(3600, Number(wm[1]) * (/^(นาที|min)/.test(wm[2]) ? 60 : 1)) };
    const gm = nt.match(/(?:ไปที่|ไป|เดินไป|พิกัด|go to|goto|walk to|travel to|move to)\s*(?:ที่|พิกัด)?\s*x?\s*(-?\d+)[\s,]+y?\s*(-?\d+)[\s,]+z?\s*(-?\d+)/);
    if (gm)
        return { k: "goto", pos: { x: Number(gm[1]), y: Number(gm[2]), z: Number(gm[3]) } };
    if (has(t, "กลับบ้าน", "ไปบ้าน", "กลับไปบ้าน", "go home", "return home") && !has(t, "สร้าง", "build"))
        return { k: "goto", home: true };
    if (has(t, "กลับไปที่ทำงาน", "กลับไปที่เดิม", "go back to work", "back to work"))
        return { k: "goto", work: true };
    if (has(t, "หยุด", "เลิก", "พอแล้ว", "พอก่อน", "stop", "quit") && !has(t, "สู้", "fight"))
        return { k: "stay" };
    if (has(t, "เข้าหีบ", "ใส่หีบ", "ลงหีบ", "ในหีบ", "ไว้ในหีบ", "เก็บเข้า", "into the chest", "in the chest", "in a chest", "to the chest", "put away", "store it", "store them", "store items"))
        return { k: "deposit" };
    const mineBuild = has(t, "สร้างเหมือง", "ทำเหมือง", "ขุดเหมือง", "เหมืองใหม่", "อุโมงค์", "ขุดบันได", "ขุดลง", "ทางลงเหมือง", "mine shaft", "mineshaft", "build a mine", "build mine", "make a mine", "dig a mine", "branch mine", "strip mine", "tunnel", "staircase") || (has(t, "เหมือง") && has(t, "สร้าง", "ทำ", "เจาะ"));
    if (mineBuild) {
        const r = parseMine(t);
        return { k: "dig", o: r.o, said: r.said };
    }
    if (matchNewType(t) && !has(t, "ขุดแร่", "ตัดไม้", "กลับบ้าน") || (has(t, "บ้าน", "house", "home", "ปราสาท", "castle", "หอคอย", "tower", "อิกลู", "igloo", "ปิรามิด", "pyramid", "บังเกอร์", "bunker", "ฐานลับ", "ห้องลับ", "ป้อมปราการ", "fortress", "ที่พักอาศัย", "ที่อยู่อาศัย", "treehouse", "ป้อม", "nether fortress", "ประภาคาร", "lighthouse", "คฤหาสน์", "mansion", "villa", "วิลล่า", "หลบภัย", "safe house", "safehouse", "เซฟเฮ", "shelter", "อีกหลัง", "ที่หลบภัย", "ที่พัก") && !has(t, "กลับบ้าน", "go home", "กลับไปบ้าน", "ไปบ้าน", "ถึงบ้าน"))) {
        const r = parseHouse(t);
        return { k: "house", o: r.o, said: r.said };
    }
    // food: cooking, eating, finding
    if (has(t, "ทำอาหาร", "ทำกับข้าว", "ปิ้ง", "ย่าง", "ทอด", "ทำขนมปัง", "cook", "bake", "grill", "roast"))
        return { k: "cook" };
    const animal = animalIn(t);
    const cropsOnly = has(t, "เก็บเกี่ยว", "เก็บผัก", "เก็บผลไม้", "เก็บข้าวสาลี", "เก็บแครอท", "เก็บมัน", "ทำฟาร์ม", "เก็บเบอร์รี่", "harvest", "farm", "berries", "crops");
    const huntWords = has(t, "ล่า", "ฆ่า", "จับ", "หาเนื้อ", "เอาเนื้อ", "hunt", "kill", "slaughter", "get meat", "meat");
    if (has(t, "หาอาหาร", "หาของกิน", "หาอะไรกิน", "หาข้าว", "หาเสบียง", "หาเนื้อ", "ล่าสัตว์", "find food", "get food", "gather food", "look for food") || cropsOnly || (animal && (huntWords || has(t, "หา", "เอา", "get", "find")))) {
        const o = { hunt: !cropsOnly, crops: !huntWords && !animal };
        if (animal)
            o.animal = animal;
        return { k: "findFood", o };
    }
    if (has(t, "หิว", "hungry", "starving"))
        return { k: "hungry" };
    if (has(t, "กิน", "eat", "ทานข้าว"))
        return { k: "eat" };
    if (has(t, "เฝ้า", "guard", "patrol", "keep watch"))
        return { k: "guard" };
    if (has(t, "ไม้ไผ่", "ไผ่", "bamboo"))
        return { k: "gather", ids: ["minecraft:bamboo"], name: "ไม้ไผ่" };
    if (has(t, "ตัดไม้", "ตัดต้นไม้", "หาไม้", "เก็บไม้", "ไม้", "chop", "wood", "lumber", "tree", "log"))
        return { k: "chop" };
    const ores = oresIn(t);
    const blk = blockIn(t);
    if (!ores.length && blk && has(t, "ขุด", "เก็บ", "หา", "เอา", "ตัก", "ทำ", "dig", "get", "collect", "gather", "mine", "grab", "bring")) {
        return { k: "gather", ids: blk.ids, name: blk.th };
    }
    if (has(t, "ขุด", "แร่", "mine", "ore", "dig") || ores.length) {
        return { k: "mine", ores, all: has(t, "ทุกแร่", "ทุกอย่าง", "ทุกชนิด", "all ore", "every ore", "everything") };
    }
    if (has(t, "สู้", "โจมตี", "ฆ่า", "ปกป้อง", "คุ้มกัน", "ป้องกัน", "ตีมัน", "ตีมอน", "มอนสเตอร์", "ซอมบี้", "fight", "attack", "defend", "protect", "kill"))
        return { k: "fight", on: true };
    if (t === "มา" || has(t, "มานี่", "มาหา", "มาตรงนี้", "กลับมา", "มาเร็ว", "มาที่นี่", "come", "here", "teleport", "วาร์ป", "tp")) {
        return { k: "come" };
    }
    if (has(t, "ตาม", "follow"))
        return { k: "follow" };
    if (has(t, "รอ", "หยุด", "อยู่ตรงนั้น", "อยู่นิ่ง", "นั่ง", "พัก", "stay", "wait", "stop", "sit", "hold"))
        return { k: "stay" };
    if (has(t, "เลเวล", "เลเวิล", "เลเวล", "อัพเลเวล", "อัปเลเวล", "ค่าประสบการณ์", "level", "lv", "exp", "xp", "เก่งขึ้น")) {
        return { k: "level" };
    }
    if (has(t, "สถานะ", "เป็นไง", "เป็นยังไง", "อยู่ไหน", "ที่ไหน", "เลือด", "status", "where", "hp", "health", "ทำอะไรอยู่", "report", "รายงาน"))
        return { k: "status" };
    if (has(t, "เอาของมา", "ส่งของมา", "คืนของ", "ขอของ", "ส่งของคืน", "เอาของคืน", "give me", "give back", "my items", "ของมา", "เอามาให้", "เอามาส่ง", "ส่งมาให้", "เอามาคืน", "bring it", "bring them", "bring me", "bring back")) {
        return { k: "give" };
    }
    if (has(t, "หนังสือ", "book"))
        return { k: "book" };
    if (has(t, "อาหาร", "food", "ป้อน"))
        return { k: "hungry" };
    if (has(t, "สวัสดี", "หวัดดี", "ฮัลโหล", "ฮาโหล", "โย่ว", "เฮ้", "hello", "hi", "hey", "yo", "ดีจ้า", "ว่าไง"))
        return { k: "hello" };
    if (has(t, "ขอบคุณ", "ขอบใจ", "thank", "เก่ง", "good job", "ดีมาก", "สุดยอด", "nice"))
        return { k: "thanks" };
    if (!noFix) {
        // maybe a typo ("ปราสาด", "ขุดแล่", "castel"): fix the keywords and try once more
        const fx = fixTypos(t, ORDER_KEYS);
        if (fx !== t) {
            const r = parseTalk(fx, true);
            if (r.k !== "unknown")
                return r;
        }
    }
    return { k: "unknown" };
}
let hooks;
export function setHouseHooks(h) {
    hooks = h;
}
const NEW_FLOORS = { hotel: 3, hospital: 3, prison: 1, school: 2, library: 2, church: 1, barn: 1, market: 1, statue: 1, portal: 1 };
const DEFAULT_HOUSE = {
    size: 1, floors: 2, style: 0, wood: 0, roof: 0, light: true, furniture: true, porch: true, chimney: true, pool: true, speed: 1,
    type: "standard", mat: 0,
};
/** Last design + what the player said now (a new type/style replaces the old one; the material resets). */
export function applyHouseRel(base, said) {
    const o = { ...said };
    const rel = o.rel;
    delete o.rel;
    const out = { ...base, ...o };
    if (rel) {
        if (rel.size && o.size === undefined)
            out.size = Math.max(0, Math.min(2, (base.size ?? 1) + rel.size));
        if (rel.floors && o.floors === undefined)
            out.floors = Math.max(1, Math.min(10, (base.floors ?? 2) + rel.floors));
    }
    return out;
}
function mergeHouse(h, said) {
    const last = getLastOpts(h) ?? {};
    const base = { ...DEFAULT_HOUSE, ...last };
    if (said.type !== undefined && said.mat === undefined)
        base.mat = 0;
    if (said.type === undefined && said.style !== undefined)
        base.type = "standard";
    // v1.6: every order gets a fresh random variant (only "same as before" keeps the old one)
    if (!said.rel?.same) {
        delete base.variant;
        delete base.model;
    }
    if (said.type !== undefined && said.floors === undefined && NEW_FLOORS[said.type] !== undefined)
        base.floors = NEW_FLOORS[said.type];
    if (said.type !== undefined || said.style !== undefined || said.size !== undefined) {
        // a new design: forget the old exact size
        delete base.w;
        delete base.d;
        delete base.height;
    }
    return applyHouseRel(base, said);
}
export const HELP_TEXT = [
    `§l§6สิ่งที่สั่งผมได้ (พิมพ์เป็นประโยคธรรมดาได้เลย):§r §8v${VERSION}`,
    "§e• ไปขุดแร่ §7| ขุดเฉพาะเพชรกับเหล็ก | ขุดเหล็ก 10 ก้อน | ขุดทุกแร่",
    "§e• ไปตัดไม้ §7| ตัดไม้ 30 ท่อน | ตัดไม้ 5 ต้น",
    "§e• เก็บบล็อก §7| ขุดทราย 2 กอง | เก็บดิน 64 | ขุดหิน | เก็บกรวด | ดินเหนียว",
    "§e• หาอาหาร §7| ล่าวัว 3 ตัว | เก็บเกี่ยวผัก | ทำอาหาร (ใช้ถ่านหิน) | กินอาหาร",
    "§e• สร้างเหมือง §7เช่น \"สร้างเหมือง ลึก 40 ยาว 30\" \"สร้างเหมืองหาเหล็ก\"",
    "§e• สร้างบ้าน §7เช่น \"สร้างบ้านโมเดิร์นหลังใหญ่ 3 ชั้น มีสระ\" \"บ้านใต้ดิน 2 ชั้น\" \"ปราสาทหินใหญ่\" \"บ้านต้นไม้\" \"หอคอย\" \"อิกลู\" \"ปิรามิด\" \"บ้านทะเลทราย\" \"บ้านหินสีเทา\"",
    "§e• §lใหม่ v1.6§r§e สร้างได้หลายแบบ (สุ่มแบบให้ทุกครั้ง): §7บ้านใต้ดิน/บังเกอร์ (ทหาร โถงถ้ำ ห้องนิรภัย ไซโล) • คุก • โรงแรม • โรงเรียน • โรงพยาบาล • ห้องสมุด • โบสถ์/วัด • โรงนา/คอกม้า • ตลาด/ร้านค้า\n§7รูปปั้น: \"สร้างรูปปั้นครีปเปอร์\" (ครีปเปอร์ สตีฟ ซอมบี้ หมู แมว หัวใจ ดาบ ต้นไม้ เพชร เห็ด ดาว มนุษย์หิมะ) • \"สร้างประตูเนเธอร์\" (ต้องส่งออบซิเดียน 10 + เหล็กไฟให้)\n§7เลือกแบบเอง: \"บังเกอร์ทหาร\" \"บ้านใต้ดินแบบถ้ำ\" \"ห้องนิรภัย\" หรือ \"แบบที่ 2\" • ขุดไม้ไผ่: \"เก็บไม้ไผ่ 64\"",
    "§e• มานี่ §7| §eตามมา §7| §eรอตรงนั้น §7| §eรอ 30 วินาที §7| §eไปที่ 100 64 -50 §7| §eกลับบ้าน",
    "§e• เอาของมาให้ §7| §eเก็บเข้าหีบ §7| §eช่วยสู้ §7| §eไม่ต้องสู้ §7| §eเฝ้าตรงนี้ §7| §eช่วยด้วย!",
    "§e• สถานะ §7| §eเลเวล §7| §eทำต่อ §7| §eยกเลิก §7| §eเปลี่ยนชื่อเป็น ...",
    "§6§lสั่งหลายอย่างต่อกันได้§r ด้วยคำว่า §eแล้ว / จากนั้น / then§r เช่น",
    "§7\"ตัดไม้ 20 ท่อน แล้วขุดหิน 64 ก้อน แล้วเอามาให้\"  \"หาอาหาร แล้วกลับมาหาเรา\"",
    "§7\"ขุดเพชรจนกระเป๋าเต็ม แล้วเก็บเข้าหีบ\"  \"ไปที่ 120 70 -30 แล้วเก็บทราย 64\"",
].join("\n");
const LAST_ORDER = new Map();
const NOT_ORDERS = ["help", "status", "level", "hello", "thanks", "hungry", "book", "name", "repeat", "cancel", "resume"];
const THANKS = ["ยินดีครับ!", "เพื่อคุณ ผมทำได้ทุกอย่าง!", "ขอบคุณที่ชมครับ อิอิ", "งานสบายมากครับ!"];
/** Executes what the player said to one helper. */
export async function handleTalk(p, h, raw) {
    const text = raw.trim();
    if (!text)
        return;
    rememberHelper(p, h);
    p.sendMessage(`§7[คุณ → ${getName(h)}]§f ${text}`);
    if (/(ตรงไหน|ที่ไหน|ยังไง|อย่างไร|ทำไม|เท่าไหร่|เท่าไร|ได้อย่างไร|where|how |what |why|\?)/.test(norm(text)) || /\?\s*$/.test(text)) {
        const kb = smallTalk(norm(text), chatContext(h));
        if (kb) {
            say(p, h, kb);
            return;
        }
    }
    const first = parseTalk(text);
    if (first.k === "repeat") {
        const prev = LAST_ORDER.get(h.id);
        if (!prev) {
            say(p, h, "ยังไม่มีงานก่อนหน้าให้ทำซ้ำเลยครับ สั่งงานใหม่ได้เลย");
            return;
        }
        say(p, h, `ได้ครับ ทำซ้ำ: "${prev}"`);
        await handleTalk(p, h, prev);
        return;
    }
    if (!NOT_ORDERS.includes(first.k) && first.k !== "unknown")
        LAST_ORDER.set(h.id, text);
    if (await tryPlan(p, h, text))
        return;
    const it = first;
    switch (it.k) {
        case "gather":
            cmdGather(p, h, it.ids, it.name);
            return;
        case "findFood":
            cmdFood(p, h, it.o);
            return;
        case "help":
            p.sendMessage(HELP_TEXT);
            return;
        case "come":
            cmdCome(p, h);
            return;
        case "rescue":
            cmdCome(p, h);
            cmdFight(p, h, true);
            return;
        case "follow":
            cmdStop(p, h, "follow");
            return;
        case "stay":
            cmdStop(p, h, "stay");
            return;
        case "mine": {
            const s = getSettings(h);
            if (it.all) {
                for (const g of ORE_GROUPS)
                    s.ores[g.id] = true;
                saveSettings(h, s);
                say(p, h, "ตั้งให้ขุด §eทุกแร่§r แล้ว");
            }
            else if (it.ores.length) {
                for (const g of ORE_GROUPS)
                    s.ores[g.id] = it.ores.includes(g.id);
                saveSettings(h, s);
                const names = it.ores.map((g) => ORE_GROUPS.find((x) => x.id === g)?.th ?? g).join(", ");
                say(p, h, `ตั้งให้ขุด §eเฉพาะ ${names}§r แล้ว §7(พิมพ์ "ขุดทุกแร่" เพื่อกลับไปขุดทุกอย่าง)`);
            }
            cmdWork(p, h, "mine");
            return;
        }
        case "chop":
            cmdWork(p, h, "chop");
            return;
        case "dig":
            if (it.said.length)
                say(p, h, `รับทราบ! สร้างเหมือง: ${it.said.join(", ")}`);
            cmdMine(p, h, it.o);
            return;
        case "house": {
            if (!hooks)
                return;
            if (!it.said.length) {
                say(p, h, "อยากได้บ้านแบบไหนครับ? เลือกในหน้าต่างได้เลย (หรือพิมพ์บอก เช่น \"บ้านโมเดิร์นหลังใหญ่ 2 ชั้น มีสระ\")");
                await hooks.design(p, h);
                return;
            }
            const o = mergeHouse(h, it.o);
            if (o.type === "portal")
                say(p, h, "ประตูเนเธอร์ต้องใช้ของจริง: §eออบซิเดียน 10 ก้อน + เหล็กไฟ (Flint and Steel) 1 อัน§r ส่งให้ผมก่อนนะ (เมนูสร้างบ้าน > ส่งบล็อก)");
            const vn = o.variant !== undefined ? variantName(o) : undefined;
            say(p, h, `รับทราบ! ${houseName(o)}${vn ? ` (แบบ${vn})` : ""} • ${SIZES[o.size].th} • ${o.floors} ชั้น • ${WOODS[o.wood].th}` +
                `\n§7(ที่คุณบอก: ${it.said.join(", ")}) บ้านจะสร้างตรงหน้าคุณ ดูกรอบสีเขียวแล้วกดยืนยันนะ`);
            await hooks.confirm(p, h, o);
            return;
        }
        case "fight":
            cmdFight(p, h, it.on);
            return;
        case "attack":
            cmdAttack(p, h, it);
            return;
        case "speed":
            cmdSpeed(p, h, it.sp);
            return;
        case "level":
            say(p, h, `${levelText(h)}\n§f${describeHealth(h)}\n§7${LEVEL_HOWTO}`);
            return;
        case "status":
            p.sendMessage(`§6[§e${getName(h)}§6]§r\n${statusLines(h, p).join("\n")}`);
            return;
        case "give":
            cmdGiveBack(p, h);
            return;
        case "resume":
            if (!cmdResume(p, h))
                say(p, h, "ไม่มีงานค้างไว้เลยครับ สั่งงานใหม่ได้เลย (พิมพ์ \"ช่วย\" เพื่อดูคำสั่ง)");
            return;
        case "cancel":
            cmdCancel(p, h);
            return;
        case "name":
            cmdRename(p, h, it.name);
            return;
        case "book":
            if (!giveBook(p, true))
                say(p, h, "กระเป๋าคุณเต็มอยู่ ทำที่ว่างก่อนนะ");
            return;
        case "hungry": {
            const hp = effHp(h);
            if (isKO(h))
                say(p, h, "§cผมสลบอยู่ หิวมาก...§r ส่งอาหารมาให้หน่อยนะ (หนังสือสั่งงาน > ให้อาหาร)");
            else if (!isFullHp(h))
                say(p, h, `เลือดผม ${Math.ceil(hp)}/${maxHp(h)} ถ้าได้กินอะไรหน่อยจะดีมาก (อะไรก็ได้ยกเว้นเนื้อเน่า)`);
            else
                say(p, h, "ยังอิ่มอยู่ครับ เลือดเต็มแล้ว");
            return;
        }
        case "hello":
            say(p, h, `สวัสดีครับ! ตอนนี้ผมกำลัง ${modeLabel(getMode(h))} อยู่ เลือด ${Math.ceil(effHp(h))}/${maxHp(h)} (เลเวล ${getLevel(h)}) มีอะไรให้ช่วยไหม?`);
            return;
        case "thanks":
            say(p, h, THANKS[Math.floor(Math.random() * THANKS.length)]);
            return;
        default: {
            const reply = smallTalk(norm(text), chatContext(h));
            if (reply) {
                say(p, h, reply);
                return;
            }
            say(p, h, `ขอโทษครับ ผมไม่แน่ใจว่า "§f${text}§r" หมายถึงอะไร เลือกจากรายการที่ใกล้เคียงได้เลย (หรือพิมพ์ "§eช่วย§r" เพื่อดูคำสั่งทั้งหมด)`);
            if (hooks?.fallback)
                await hooks.fallback(p, h, text);
        }
    }
}
function chatContext(h) {
    let hour;
    let day;
    try {
        hour = ((world.getTimeOfDay() / 1000 + 6) % 24 + 24) % 24;
        day = world.getDay();
    }
    catch {
        /* ignore */
    }
    return { name: getName(h), hp: Math.ceil(effHp(h)), max: maxHp(h), level: getLevel(h), mode: modeLabel(getMode(h)), hour, day };
}
/** Chat command entry: picks which helper(s) the player is talking to. */
export async function handleTalkCommand(p, raw) {
    const mine = ownedHelpers(p);
    if (!mine.length) {
        p.sendMessage("§6[ผู้ช่วย]§r คุณยังไม่มีผู้ช่วยเลย วาง §eสัญญาจ้างผู้ช่วย§r บนพื้นเพื่อเรียกผู้ช่วยก่อนนะ (หรือผู้ช่วยอยู่ไกลเกินไปจนโลกไม่ได้โหลด)");
        return;
    }
    let text = raw.trim();
    const t = norm(text);
    let targets = [];
    const all = t.match(/^(ทุกคน|ทุกตัว|all|everyone)[\s,:]*/);
    if (all) {
        text = text.slice(all[0].length).trim();
        targets = mine;
    }
    else {
        for (const h of mine) {
            const n = norm(getName(h));
            if (n && t.startsWith(n)) {
                targets = [h];
                text = text.slice(getName(h).length).replace(/^[\s,:]+/, "");
                break;
            }
        }
    }
    if (!targets.length) {
        const last = lastHelper(p);
        if (last && mine.some((h) => h.id === last.id))
            targets = [last];
        else {
            const near = [...mine].sort((a, b) => score(p, a) - score(p, b));
            targets = [near[0]];
        }
    }
    if (!text) {
        p.sendMessage(HELP_TEXT);
        return;
    }
    const it = parseTalk(text);
    if (targets.length > 1 && (it.k === "house" || it.k === "dig" || it.k === "name"))
        targets = [targets[0]];
    for (const h of targets)
        await handleTalk(p, h, text);
}
function score(p, h) {
    if (h.dimension.id !== p.dimension.id)
        return 1e9;
    return dist(h.location, p.location);
}
// ------------------------------------------------------------------ several orders in one sentence
const STRONG = /\s*(?:และหลังจากนั้น|หลังจากนั้นก็|หลังจากนั้น|จากนั้นก็|จากนั้น|ต่อจากนั้น|ต่อด้วย|เสร็จแล้วก็|เสร็จแล้ว|พอเสร็จแล้ว|พอเสร็จ|แล้วค่อย|แล้วก็|แล้ว|\band then\b|\bthen\b|\bafter that\b|\bafterwards\b|;|\n|,(?!\s*-?\d)|ต่อไป)\s*/i;
const WEAK = /\s*(?:\band\b|และ|กับก็|พร้อมกับ)\s*/i;
const VERB = /(ตัด|ขุด|ไป|มา|เก็บ|หา|สร้าง|รอ|ตาม|ฆ่า|ล่า|ทำ|เอา|ส่ง|กิน|ปิ้ง|ย่าง|เฝ้า|หยุด|ใส่|chop|mine|dig|\bgo\b|come|\bget\b|collect|gather|build|make|wait|follow|kill|hunt|bring|give|\beat\b|cook|guard|stay|stop|put|store)/;
export function splitChain(raw) {
    const out = [];
    for (const part of raw.split(STRONG)) {
        const p = part.trim();
        if (!p)
            continue;
        // "and" only splits when the next piece is a new order ("chop wood and mine iron"),
        // not a detail of the same one ("a house with a pool and a chimney")
        const pieces = p.split(WEAK).map((x) => x.trim()).filter(Boolean);
        let cur = pieces[0] ?? "";
        for (let i = 1; i < pieces.length; i++) {
            const prevK = parseTalk(cur).k;
            if (VERB.test(norm(pieces[i])) && prevK !== "house" && parseTalk(pieces[i]).k !== "unknown") {
                out.push(cur);
                cur = pieces[i];
            }
            else
                cur += " " + pieces[i];
        }
        if (cur)
            out.push(cur);
    }
    // a piece that means nothing on its own belongs to the order before it
    const merged = [];
    for (const piece of out) {
        if (merged.length && parseTalk(piece).k === "unknown")
            merged[merged.length - 1] += " " + piece;
        else
            merged.push(piece);
    }
    return merged;
}
const QUERY = ["help", "status", "level", "hello", "thanks", "hungry", "book", "name"];
const PLAN_ONLY = ["goto", "wait", "deposit", "eat", "cook", "guard"];
/** Turns one order into a plan step (or undefined when it is not something to do). */
function toStep(p, h, piece, it, index) {
    const t = norm(piece);
    const q = extractQty(t);
    const amount = { n: q.n, full: q.full };
    switch (it.k) {
        case "chop":
            return workStep("chop", amount);
        case "mine":
            return workStep("mine", { ...amount, ores: it.ores.length ? it.ores : undefined });
        case "gather":
            return workStep("gather", { ...amount, ids: it.ids, idsName: it.name });
        case "findFood":
            return workStep("food", { ...amount, food: it.o });
        case "dig":
            return mineStep(p, h, it.o, index === 0);
        case "house": {
            return houseStep(p, mergeHouse(h, it.o));
        }
        case "come":
        case "rescue":
            return { k: "come", label: "มาหาคุณ" };
        case "follow":
            return { k: "follow", label: "เดินตามคุณ" };
        case "stay":
            return { k: "stay", label: "รออยู่ตรงนั้น" };
        case "give":
            return { k: "deliver", label: "เอาของมาให้คุณ" };
        case "deposit":
            return { k: "deposit", label: "เก็บของเข้าหีบ" };
        case "eat":
            return { k: "eat", label: "กินอาหารในกระเป๋า" };
        case "cook":
            return { k: "cook", label: "ทำอาหาร" };
        case "guard":
            return { k: "guard", label: "เฝ้าอยู่ตรงนั้น (สู้กับมอนสเตอร์)" };
        case "fight":
            return { k: "fight", label: it.on ? "เปิดโหมดช่วยต่อสู้" : "ปิดโหมดต่อสู้", on: it.on };
        case "wait":
            return { k: "wait", label: `รอ ${it.secs >= 60 && it.secs % 60 === 0 ? `${it.secs / 60} นาที` : `${it.secs} วินาที`}`, secs: it.secs };
        case "goto": {
            if (it.pos)
                return { k: "goto", label: `ไปที่ ${it.pos.x}, ${it.pos.y}, ${it.pos.z}`, pos: it.pos, dim: h.dimension.id };
            if (it.work) {
                const home = getHome(h);
                if (!home)
                    return "ผมยังไม่มีที่ทำงานเลย";
                return { k: "goto", label: "กลับไปที่ทำงาน", pos: home, dim: h.dimension.id };
            }
            const hm = ownerHome(p);
            return { k: "goto", label: hm.bed ? "กลับบ้าน (เตียงของคุณ)" : "กลับจุดเกิดของโลก", pos: hm.pos, dim: hm.dim };
        }
        default:
            return undefined;
    }
}
/** Handles sentences with several orders, amounts ("20 ท่อน") or plan-only orders. Returns true when handled. */
export async function tryPlan(p, h, text) {
    const pieces = splitChain(text);
    if (!pieces.length)
        return false;
    const intents = pieces.map((x) => parseTalk(x));
    if (pieces.length === 1) {
        const it = intents[0];
        const q = extractQty(norm(pieces[0]));
        const isWork = it.k === "chop" || it.k === "mine" || it.k === "gather" || it.k === "findFood";
        if (!PLAN_ONLY.includes(it.k) && !(isWork && (q.n !== undefined || q.full)))
            return false;
    }
    if (intents.every((it) => QUERY.includes(it.k) || it.k === "unknown"))
        return false;
    const steps = [];
    for (let i = 0; i < pieces.length; i++) {
        const it = intents[i];
        if (QUERY.includes(it.k)) {
            await handleTalkSingle(p, h, it);
            continue;
        }
        const st = toStep(p, h, pieces[i], it, steps.length);
        if (typeof st === "string")
            say(p, h, `§eข้าม "${pieces[i]}":§r ${st}`);
        else if (st)
            steps.push(st);
        else if (it.k === "unknown")
            say(p, h, `ไม่เข้าใจ "§f${pieces[i]}§r" เลยข้ามไปนะ`);
    }
    if (!steps.length)
        return true;
    cmdStop(p, h, getMode(h) === "follow" ? "follow" : "stay", true); // drop the current job first
    startPlan(p, h, steps);
    return true;
}
async function handleTalkSingle(p, h, it) {
    switch (it.k) {
        case "status":
            p.sendMessage(`§6[§e${getName(h)}§6]§r\n${statusLines(h, p).join("\n")}`);
            return;
        case "level":
            say(p, h, `${levelText(h)}\n§f${describeHealth(h)}\n§7${LEVEL_HOWTO}`);
            return;
        case "help":
            p.sendMessage(HELP_TEXT);
            return;
        case "name":
            cmdRename(p, h, it.name);
            return;
        case "hello":
        case "thanks":
            say(p, h, THANKS[Math.floor(Math.random() * THANKS.length)]);
            return;
        default:
            return;
    }
}
