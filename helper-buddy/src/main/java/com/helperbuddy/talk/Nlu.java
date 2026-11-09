package com.helperbuddy.talk;

import com.helperbuddy.gen.ExtraGen;
import com.helperbuddy.gen.Gen;
import com.helperbuddy.gen.StatueGen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The built-in brain that understands Thai and English orders (no AI needed). Pure Java, no Minecraft classes.
 * Typos are tolerated, Thai/English number words are read, several orders can be chained with "แล้ว / then".
 */
public final class Nlu {
    private Nlu() {}

    /** A bilingual text. */
    public record L(String th, String en) {
        public String get(boolean thai) {
            return thai ? th : en;
        }
    }

    // ================================================================== regex helpers
    static final String WB = "(?:(?<=[a-z0-9_])(?![a-z0-9_])|(?<![a-z0-9_])(?=[a-z0-9_]))";
    private static final Map<String, Pattern> CACHE = new HashMap<>();

    /** Compiles a JavaScript-style pattern: \b only counts ASCII letters/digits as word characters (Thai never is). */
    public static Pattern re(String js) {
        synchronized (CACHE) {
            return CACHE.computeIfAbsent(js, k -> Pattern.compile(k.replace("\\b", WB)));
        }
    }

    public static boolean test(String js, String t) {
        return re(js).matcher(t).find();
    }

    static Matcher match(String js, String t) {
        Matcher m = re(js).matcher(t);
        return m.find() ? m : null;
    }

    /** Thai words match anywhere (no spaces in Thai); English words must be whole words. */
    public static boolean has(String t, String... words) {
        for (String w : words) {
            if (!w.matches("[a-z' ]+")) {
                if (t.contains(w)) return true;
                continue;
            }
            if (re("(^|[^a-z])" + w.replace("'", "'?") + "([^a-z]|$)").matcher(t).find()) return true;
        }
        return false;
    }

    // ================================================================== normalising
    static final String PARTICLES = "(?:^|(?<=\\s))(?:ครับผม|ครับ|ค่ะ|คะ|นะครับ|นะคะ|นะจ๊ะ|นะจ้ะ|จ้า|จ้ะ|จ๊ะ|ด้วยนะ|หน่อยนะ|หน่อย|เถอะ|เถิด|ซิ|สิ(?!บ)|นะ|เลยนะ|ละ|ล่ะ)(?=\\s|$)"
            + "|(?:ครับผม|ครับ|ค่ะ|คะ|นะครับ|นะคะ|นะจ๊ะ|จ้า|จ้ะ|จ๊ะ|หน่อยนะ|หน่อย|เถอะ|ซิ|นะ|ล่ะ)$";

    public static String norm(String s) {
        String t = s.replaceAll("§.", "").toLowerCase(Locale.ROOT);
        StringBuilder b = new StringBuilder();
        for (char ch : t.toCharArray()) b.append(ch >= '๐' && ch <= '๙' ? (char) ('0' + (ch - '๐')) : ch);
        t = b.toString().replaceAll("(.)\\1{2,}", "$1$1").replaceAll("[!！?？~…]+", " ").replaceAll("\\s+", " ").trim();
        // words that merely end like a particle ("สถานะ" = status) are protected
        t = t.replace("สถานะ", "\uE000");
        for (int i = 0; i < 2; i++) t = t.replaceAll(PARTICLES, "").replaceAll("\\s+", " ").trim();
        t = t.replace("\uE000", "สถานะ");
        // common English contractions and fillers
        t = t.replace("can't", "cannot").replace("don't", "dont").replace("won't", "wont").replace("i'm", "im").replace("let's", "lets");
        t = t.replaceAll("^(please|pls|plz|hey buddy|buddy|hey)\\s+", "").replaceAll("\\s+(please|pls|plz)$", "");
        if (!t.startsWith("ช่วยด้วย") && !t.startsWith("ช่วยฉัน") && !t.startsWith("ช่วยเรา") && !t.equals("ช่วย"))
            t = t.replaceAll("^(ช่วยไป|ช่วย|รบกวน|ฝาก|ขอให้|อยากให้|ไหว้วาน)\\s*(?=\\S)", "");
        return t.trim();
    }

    static final Map<String, Integer> TH_D = Map.of("หนึ่ง", 1, "เอ็ด", 1, "สอง", 2, "ยี่", 2, "สาม", 3, "สี่", 4, "ห้า", 5, "หก", 6, "เจ็ด", 7, "แปด", 8);
    static final Map<String, Integer> EN_D = new HashMap<>();

    static {
        String[] w = {"zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen",
                "sixteen", "seventeen", "eighteen", "nineteen", "twenty"};
        for (int i = 0; i < w.length; i++) EN_D.put(w[i], i);
        EN_D.put("thirty", 30);
        EN_D.put("forty", 40);
        EN_D.put("fifty", 50);
        EN_D.put("sixty", 60);
        EN_D.put("hundred", 100);
        EN_D.put("dozen", 12);
        EN_D.put("a couple of", 2);
        EN_D.put("a few", 3);
        EN_D.put("several", 5);
    }

    static int thDigit(String s) {
        if (s == null) return -1;
        if (s.equals("เก้า")) return 9;
        Integer v = TH_D.get(s);
        return v == null ? -1 : v;
    }

    /** Turns number words into digits (only used to read numbers, never for matching keywords). */
    public static String numify(String t) {
        String s = t.replaceAll("ติดลบ\\s*", "-").replaceAll("ลบ\\s*(?=\\d|หนึ่ง|สอง|สาม|สี่|ห้า|หก|เจ็ด|แปด|เก้า|สิบ)", "-");
        // ร้อย (hundreds)
        Matcher m = Pattern.compile("(หนึ่ง|สอง|สาม|สี่|ห้า|หก|เจ็ด|แปด|เก้า)?ร้อย").matcher(s);
        StringBuilder sb = new StringBuilder();
        while (m.find()) m.appendReplacement(sb, String.valueOf((m.group(1) != null ? thDigit(m.group(1)) : 1) * 100) + "+");
        m.appendTail(sb);
        s = sb.toString();
        m = Pattern.compile("(หนึ่ง|สอง|ยี่|สาม|สี่|ห้า|หก|เจ็ด|แปด|เก้า)?สิบ(เอ็ด|หนึ่ง|สอง|สาม|สี่|ห้า|หก|เจ็ด|แปด|เก้า)?").matcher(s);
        sb = new StringBuilder();
        while (m.find()) {
            int v = (m.group(1) != null ? thDigit(m.group(1)) : 1) * 10 + (m.group(2) != null ? thDigit(m.group(2)) : 0);
            m.appendReplacement(sb, String.valueOf(v));
        }
        m.appendTail(sb);
        s = sb.toString();
        m = Pattern.compile("(หนึ่ง|สอง|สาม|สี่|ห้า|หก|เจ็ด|แปด|เก้า)").matcher(s);
        sb = new StringBuilder();
        while (m.find()) m.appendReplacement(sb, String.valueOf(thDigit(m.group(1))));
        m.appendTail(sb);
        s = sb.toString();
        // "100+20" -> "120"
        m = Pattern.compile("(\\d+)\\+(\\d+)").matcher(s);
        sb = new StringBuilder();
        while (m.find()) m.appendReplacement(sb, String.valueOf(Integer.parseInt(m.group(1)) + Integer.parseInt(m.group(2))));
        m.appendTail(sb);
        s = sb.toString().replace("+", "");
        // english: "twenty five" -> 25, "two hundred" -> 200
        m = re("\\b(twenty|thirty|forty|fifty|sixty)[ -](one|two|three|four|five|six|seven|eight|nine)\\b").matcher(s);
        sb = new StringBuilder();
        while (m.find()) m.appendReplacement(sb, String.valueOf(EN_D.get(m.group(1)) + EN_D.get(m.group(2))));
        m.appendTail(sb);
        s = sb.toString();
        m = re("\\b(a couple of|a few|several|dozen|zero|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|thirteen|fourteen|fifteen|sixteen|seventeen|eighteen|nineteen|twenty|thirty|forty|fifty|sixty)\\b").matcher(s);
        sb = new StringBuilder();
        while (m.find()) m.appendReplacement(sb, String.valueOf(EN_D.get(m.group(1))));
        m.appendTail(sb);
        s = sb.toString();
        s = s.replaceAll("(\\d+) hundred", "$100").replace("เดียว", "1");
        return s;
    }

    // ================================================================== typo tolerance
    static int lev(String a, String b, int max) {
        if (Math.abs(a.length() - b.length()) > max) return max + 1;
        int[] prev = new int[b.length() + 1];
        for (int i = 0; i <= b.length(); i++) prev[i] = i;
        for (int i = 1; i <= a.length(); i++) {
            int[] cur = new int[b.length() + 1];
            cur[0] = i;
            int rowMin = i;
            for (int j = 1; j <= b.length(); j++) {
                int v = Math.min(Math.min(prev[j] + 1, cur[j - 1] + 1), prev[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1));
                cur[j] = v;
                rowMin = Math.min(rowMin, v);
            }
            if (rowMin > max) return max + 1;
            prev = cur;
        }
        return prev[b.length()];
    }

    static int tol(int len) {
        return len >= 11 ? 2 : len >= 6 ? 1 : 0;
    }

    static int[] fuzzyFind(String text, String word) {
        int i = text.indexOf(word);
        if (i >= 0) return new int[] {i, i + word.length()};
        int m = tol(word.length());
        if (m == 0) return null;
        int[] best = null;
        int bd = m + 1;
        for (int len = word.length() - 1; len <= word.length() + 1; len++) {
            if (len < 3) continue;
            for (int s = 0; s + len <= text.length(); s++) {
                String piece = text.substring(s, s + len);
                if (piece.charAt(0) != word.charAt(0)) continue;
                int d = lev(piece, word, m);
                if (d < bd) {
                    bd = d;
                    best = new int[] {s, s + len};
                }
            }
        }
        return bd <= m ? best : null;
    }

    public static String fixTypos(String text, String[] keys) {
        String t = text;
        for (String k : keys) {
            if (t.contains(k)) continue;
            int[] r = fuzzyFind(t, k);
            if (r != null) t = t.substring(0, r[0]) + k + t.substring(r[1]);
        }
        return t;
    }

    static final String[] ORDER_KEYS = {
            "ขุดแร่", "ตัดไม้", "ตัดต้นไม้", "สร้างบ้าน", "สร้างเหมือง", "หาอาหาร", "ทำอาหาร", "เก็บเข้าหีบ", "เอาของมา", "ยกเลิก", "สถานะ",
            "เลเวล", "กลับบ้าน", "ตามมา", "รอตรงนี้", "ช่วยด้วย", "ช่วยสู้", "ปราสาท", "ปิรามิด", "โรงแรม", "โรงเรียน", "โรงพยาบาล", "ห้องสมุด", "รูปปั้น",
            "ประตูเนเธอร์", "เรือนจำ", "โรงนา", "อิกลู", "หอคอย", "คฤหาสน์", "ประภาคาร", "ใต้ดิน", "บังเกอร์", "บ้านหรู", "หลบภัย", "ต้นไม้", "ทะเลทราย",
            "เนเธอร์", "แบล็กสโตน", "ดีพสเลท", "ควอตซ์", "เพชร", "เหล็ก", "ทองแดง", "เรดสโตน", "มรกต", "โมเดิร์น", "ยุคกลาง", "สระ", "ปล่องไฟ", "ระเบียง",
            "ชั้น", "ล่าสัตว์", "เก็บเกี่ยว", "คราฟต์", "ถลุงแร่", "ปลูกผัก", "คบเพลิง",
            "castle", "pyramid", "igloo", "tower", "mansion", "lighthouse", "underground", "bunker", "treehouse", "shelter", "villa", "luxury",
            "diamond", "build", "house", "chop", "mine", "follow", "status", "cancel", "come", "stay", "craft", "smelt", "harvest", "hospital", "library",
            "school", "prison", "church", "market", "statue", "portal",
    };

    // ================================================================== ores / directions
    public static final String[][] ORE_WORDS = {
            {"copper", "(ทองแดง|copper)"}, {"gold", "(ทอง(?!แดง)|gold)"}, {"diamond", "(เพชร|diamond)"}, {"iron", "(เหล็ก|iron)"},
            {"coal", "(ถ่าน|coal)"}, {"redstone", "(เรดสโตน|หินแดง|redstone)"}, {"lapis", "(ลาพิส|ลาปิส|lapis)"}, {"emerald", "(มรกต|emerald)"},
            {"nether", "(ควอตซ์|ควอทซ์|quartz)"}, {"debris", "(เศษโบราณ|debris|netherite|เนเธอร์ไรท์|นีเธอไรต์)"},
    };

    public static List<String> oresIn(String t) {
        List<String> out = new ArrayList<>();
        for (String[] o : ORE_WORDS) if (test(o[1], t)) out.add(o[0]);
        return out;
    }

    static String dirIn(String t) {
        if (test("(ตะวันออก|\\beast\\b)", t)) return "east";
        if (test("(ตะวันตก|\\bwest\\b)", t)) return "west";
        if (test("((ทิศ|ทาง|ไป)\\s*เหนือ|\\bnorth\\b)", t)) return "north";
        if (test("((ทิศ|ทาง|ไป)\\s*ใต้(?!ดิน)|\\bsouth\\b)", t)) return "south";
        return null;
    }

    // ================================================================== mine description
    public static final class MineOpts {
        public Integer y, depth, length, branch;
        public boolean flat, atHelper;
        public String dir;
    }

    static final Map<String, Integer> ORE_LEVEL = Map.of("diamond", -54, "redstone", -54, "gold", -16, "iron", 16, "copper", 48, "lapis", 0, "coal", 48, "debris", 15, "emerald", 100);

    public record MineReq(MineOpts o, List<L> said) {}

    public static MineReq parseMine(String t) {
        String n = numify(t);
        MineOpts o = new MineOpts();
        List<L> said = new ArrayList<>();
        Matcher m = match("(?:\\by\\b|ระดับ|ความสูง|\\blevel\\b)\\s*[=:]?\\s*(-?\\d+)", n);
        if (m != null) {
            o.y = Integer.parseInt(m.group(1));
            said.add(new L("ระดับ Y=" + o.y, "down to Y=" + o.y));
        }
        m = match("(?:ลึก|depth|deep)\\s*(?:ลง)?\\s*(\\d+)", n);
        if (m == null) m = match("(?:ลง(?:ไป)?)\\s*(\\d+)\\s*(?:บล็อก|blocks?)", n);
        if (m == null) m = match("(\\d+)\\s*(?:บล็อก|blocks?)?\\s*(?:down|deep)", n);
        if (m != null && o.y == null) {
            o.depth = Math.abs(Integer.parseInt(m.group(1)));
            said.add(new L("ลึก " + o.depth + " บล็อก", o.depth + " blocks deep"));
        }
        m = match("(?:ยาว|length|long)\\s*(\\d+)", n);
        if (m == null) m = match("(\\d+)\\s*(?:บล็อก|blocks?)?\\s*long", n);
        if (m != null) {
            o.length = Integer.parseInt(m.group(1));
            said.add(new L("ยาว " + o.length, o.length + " long"));
        }
        m = match("(?:กิ่ง|แขนง|ทางแยก|branch(?:es)?)\\s*(?:ยาว|length|long)?\\s*(\\d+)", n);
        if (m != null) {
            o.branch = Integer.parseInt(m.group(1));
            said.add(new L("กิ่งยาว " + o.branch, "branches " + o.branch + " long"));
        }
        if (test("(ไม่(ต้อง)?(มี|เอา)?(กิ่ง|แขนง|ทางแยก)|no branch|without branch)", t)) {
            o.branch = 0;
            said.add(new L("ไม่มีกิ่ง", "no branches"));
        }
        if (test("(แนวนอน|ไม่ต้องลง|ไม่ต้องขุดลง|ระดับนี้|ตรงระดับนี้|\\bflat\\b|horizontal|this level|same level|straight)", t)) {
            o.flat = true;
            said.add(new L("แนวนอนที่ระดับนี้", "straight at this level"));
        }
        String d = dirIn(t);
        if (d != null) {
            o.dir = d;
            said.add(new L(switch (d) {
                case "north" -> "ไปทิศเหนือ";
                case "south" -> "ไปทิศใต้";
                case "east" -> "ไปทิศตะวันออก";
                default -> "ไปทิศตะวันตก";
            }, "heading " + d));
        }
        if (o.y == null && o.depth == null && !o.flat) {
            for (String g : oresIn(t)) {
                Integer lv = ORE_LEVEL.get(g);
                if (lv != null) {
                    o.y = lv;
                    said.add(new L("ลงไปที่ Y=" + lv + " (ดีสำหรับ" + oreName(g, true) + ")", "down to Y=" + lv + " (best for " + oreName(g, false) + ")"));
                    break;
                }
            }
        }
        if (test("(ตรงนั้น|ที่นั่น|ตรงที่นาย|ตรงที่อยู่|where you are|\\bthere\\b)", t)) o.atHelper = true;
        return new MineReq(o, said);
    }

    public static String oreName(String g, boolean th) {
        return switch (g) {
            case "coal" -> th ? "ถ่านหิน" : "coal";
            case "copper" -> th ? "ทองแดง" : "copper";
            case "iron" -> th ? "เหล็ก" : "iron";
            case "gold" -> th ? "ทอง" : "gold";
            case "redstone" -> th ? "เรดสโตน" : "redstone";
            case "lapis" -> th ? "ลาพิส" : "lapis";
            case "diamond" -> th ? "เพชร" : "diamonds";
            case "emerald" -> th ? "มรกต" : "emeralds";
            case "nether" -> th ? "ควอตซ์/ทองเนเธอร์" : "nether quartz/gold";
            case "debris" -> th ? "เศษโบราณ" : "ancient debris";
            default -> g;
        };
    }

    // ================================================================== house description
    static final Object[][] NEW_TYPES = {
            {"underground", "(ห้องนิรภัย|นิรภัย|\\bvault\\b|ไซโล|\\bsilo\\b)", true},
            {"statue", "(รูปปั้น|ปั้น|ประติมากรรม|โมเดล|statue|sculpture|\\bmodel\\b|pixel ?art|พิกเซล)", true},
            {"portal", "(ประตูเนเธอร์|ประตูนรก|ประตูมิติ|พอร์ทัล|พอร์ตัล|nether ?portal|\\bportal\\b)", true},
            {"prison", "(คุก|เรือนจำ|ที่คุมขัง|ห้องขัง|สถานีตำรวจ|\\bprison\\b|\\bjail\\b|\\bcell ?block\\b|police)", true},
            {"hospital", "(โรงพยาบาล|สถานพยาบาล|คลินิก|สถานีอนามัย|\\bhospital\\b|\\bclinic\\b)", true},
            {"hotel", "(โรงแรม|โฮเทล|โฮสเทล|เกสต์เฮ้าส์|เกสต์เฮาส์|โรงเตี๊ยม|ที่พักแรม|อพาร์ทเมนท์|คอนโด|\\bhotel\\b|\\bhostel\\b|\\bmotel\\b|\\binn\\b|guest ?house|apartment|condo)", true},
            {"school", "(โรงเรียน|วิทยาลัย|มหาวิทยาลัย|อนุบาล|ห้องเรียน|\\bschool\\b|\\bcollege\\b|\\buniversity\\b|\\bacademy\\b|\\bclassroom\\b)", true},
            {"library", "(ห้องสมุด|หอสมุด|พิพิธภัณฑ์|\\blibrary\\b|\\bmuseum\\b)", true},
            {"church", "(โบสถ์|มหาวิหาร|ศาลเจ้า|มัสยิด|\\bchurch\\b|\\bchapel\\b|\\bcathedral\\b|\\btemple\\b|\\bshrine\\b|\\bmosque\\b|วัด)", false},
            {"barn", "(โรงนา|คอกม้า|คอกสัตว์|เล้าไก่|คอกวัว|คอกหมู|โรงม้า|โกดัง|คลังสินค้า|โรงงาน|โรงจอดรถ|โรงรถ|\\bbarn\\b|\\bstable\\b|\\bcoop\\b|\\bwarehouse\\b|\\bgarage\\b|\\bfactory\\b)", true},
            {"market", "(ตลาด|ร้านค้า|ร้านขาย|ร้านอาหาร|ร้านกาแฟ|คาเฟ่|ซุปเปอร์|\\bmarket\\b|\\bshop\\b|\\bbazaar\\b|\\bstore\\b|\\bcafe\\b|\\brestaurant\\b)", false},
    };
    static final String BUILD_VERB = "(สร้าง|ทำ|ก่อ|ปั้น|อยากได้|ขอ|ตั้ง|เอา|build|make|create|construct|put up|want|need)";

    public static String matchNewType(String t) {
        for (Object[] e : NEW_TYPES)
            if (test((String) e[1], t) && ((Boolean) e[2] || test(BUILD_VERB, t))) return (String) e[0];
        return null;
    }

    static final Object[][] BUNKER_WORDS = {
            {1, "(บังเกอร์ทหาร|ทหาร|ฐานทัพ|military|army|ทางเดินยาว|corridor)"},
            {2, "(โถง|ถ้ำ|ทรงกลม|วงกลม|cavern|cave|round|circular|dome)"},
            {3, "(นิรภัย|ห้องเก็บ|ของมีค่า|vault|treasure|safe room)"},
            {4, "(ไซโล|หลุมลึก|หลุมใหญ่|ปล่อง|silo|shaft|deep)"},
            {0, "(คลาสสิก|ธรรมดา|classic|basic|cellar|ห้องใต้ดิน)"},
    };
    static final String[][] TYPE_RE = {
            {"underground", "(ใต้ดิน|ใต้พื้น|ฝังดิน|ขุดบ้าน|บังเกอร์|bunker|ฐานลับ|ห้องลับ|บ้านถ้ำ|ในถ้ำ|underground|basement|dugout|ฐานทัพลับ|secret base)"},
            {"safehouse", "(หลบภัย|ที่หลบ|ปลอดภัย|กันมอน|กันซอมบี้|กันครีปเปอร์|กันสัตว์ประหลาด|เซฟเฮ|เซฟ ?เฮ้?าส์|safe ?house|shelter|refuge|panic ?room|หลบมอน|หนีมอน|zombie ?proof|monster ?proof)"},
            {"mansion", "(บ้านหรู|หรูหรา|หรู|คฤหาสน์|วิลล่า|วิลลา|villa|mansion|luxur|palace|พระราชวัง|วังหลวง|ไฮโซ|เศรษฐี|บ้านรวย|บ้านโอ่อ่า|โอ่อ่า|ระดับห้าดาว|รีสอร์ท|resort|rich)"},
            {"lighthouse", "(ประภาคาร|light ?house)"},
            {"igloo", "(อิกลู|igloo|บ้านหิมะ|บ้านน้ำแข็ง|snow ?house|ice ?house)"},
            {"pyramid", "(ปิรามิด|พีระมิด|พีรามิด|pyramid)"},
            {"treehouse", "(บ้านต้นไม้|ต้นไม้|tree ?house|บนต้นไม้|บนต้น|บ้านบนต้น)"},
            {"nether", "(เนเธอร์|nether|ป้อมนรก|นรก|แบล็กสโตน|แบลคสโตน|blackstone)"},
            {"castle", "(ปราสาท|castle|ป้อมปราการ|ป้อม|fortress|\\bkeep\\b|อัศวิน|kingdom)"},
            {"tower", "(หอคอย|หอสูง|หอสังเกต|หอนาฬิกา|tower|watchtower)"},
            {"desert", "(ทะเลทราย|desert|อาหรับ|arab)"},
    };
    static final String[] TYPE_KEYS = {"โรงแรม", "โรงเรียน", "โรงพยาบาล", "ห้องสมุด", "รูปปั้น", "ประตูเนเธอร์", "เรือนจำ", "โรงนา", "ปราสาท", "ปิรามิด", "อิกลู",
            "หอคอย", "คฤหาสน์", "ประภาคาร", "ใต้ดิน", "บังเกอร์", "บ้านหรู", "หลบภัย", "ต้นไม้", "ทะเลทราย", "เนเธอร์", "แบล็กสโตน", "ดีพสเลท", "ควอตซ์",
            "โมเดิร์น", "ยุคกลาง", "castle", "pyramid", "igloo", "tower", "mansion", "lighthouse", "underground", "bunker", "treehouse", "shelter", "villa",
            "luxury", "safehouse"};
    static final Object[][] MAT_RE = {
            {1, "(อิฐหิน|หินอิฐ|stone ?brick|หิน|stone|cobble|แกรนิต|granite)"},
            {3, "(อิฐแดง|อิฐ|brick)"},
            {4, "(หินทราย|ทราย|sand)"},
            {5, "(ดีพสเลท|ดีปสเลท|deepslate)"},
            {6, "(แบล็กสโตน|แบลคสโตน|หินดำ|blackstone)"},
            {7, "(ควอตซ์|ควอทซ์|quartz|ขาวล้วน|white marble|marble)"},
            {8, "(อิฐโคลน|โคลน|\\bmud\\b)"},
            {9, "(ปริซึม|prismarine)"},
    };

    /** House options read from a sentence + what was understood (for the reply). `rel` = changes to the last design. */
    public static final class HouseReq {
        public final Gen.Opts o = new Gen.Opts();
        public final List<L> said = new ArrayList<>();
        public Integer relSize, relFloors;
        public boolean same;
        public String typeAsked; // "ai" when nothing known matched but the player wants something built
        public String free;

        public boolean rel() {
            return relSize != null || relFloors != null || same;
        }
    }

    public static HouseReq parseHouse(String raw0) {
        String t0 = fixTypos(raw0, TYPE_KEYS);
        String t = t0;
        String n = numify(t0);
        HouseReq r = new HouseReq();
        Gen.Opts o = r.o;
        List<L> said = r.said;
        // ---- exact size
        Matcher m = match("(\\d+)\\s*(?:x|×|\\*|คูณ|by)\\s*(\\d+)", n);
        if (m != null) {
            o.w = Math.max(5, Math.min(41, Integer.parseInt(m.group(1))));
            o.d = Math.max(5, Math.min(41, Integer.parseInt(m.group(2))));
            said.add(new L("กว้าง " + o.w + " ยาว " + o.d, o.w + " x " + o.d));
        } else {
            m = match("(?:กว้าง|width|wide)\\s*(\\d+)", n);
            if (m == null) m = match("(\\d+)\\s*(?:blocks?\\s*)?wide", n);
            if (m != null) {
                o.w = Math.max(5, Math.min(41, Integer.parseInt(m.group(1))));
                said.add(new L("กว้าง " + o.w, o.w + " wide"));
            }
            m = match("(?:ยาว|ลึก|length|long|deep)\\s*(\\d+)", n);
            if (m == null) m = match("(\\d+)\\s*(?:blocks?\\s*)?(?:long|deep)", n);
            if (m != null) {
                o.d = Math.max(5, Math.min(41, Integer.parseInt(m.group(1))));
                said.add(new L("ยาว " + o.d, o.d + " long"));
            }
        }
        m = match("(?:สูง|height|tall)\\s*(\\d+)\\s*(?:บล็อก|บล็อค|เมตร|blocks?|m)?(?!\\s*ชั้น)", n);
        if (m == null) m = match("(\\d+)\\s*(?:blocks?|m)\\s*(?:tall|high)", n);
        if (m != null && !test("(สูง|height|tall)\\s*\\d+\\s*(ชั้น|floor)", n)) {
            o.height = Math.max(8, Math.min(60, Integer.parseInt(m.group(1))));
            said.add(new L("สูง " + o.height + " บล็อก", o.height + " blocks tall"));
        }
        // ---- relative changes
        if (test("(ใหญ่ขึ้น|ใหญ่กว่า|ใหญ่กว่าเดิม|ขยาย|ใหญ่อีก|bigger|larger|expand)", t0)) r.relSize = 1;
        else if (test("(เล็กลง|เล็กกว่า|ย่อ|smaller|shrink)", t0)) r.relSize = -1;
        if (test("(เพิ่มชั้น|อีกชั้น|สูงขึ้น|สูงกว่า|เพิ่มอีกชั้น|more floors|taller|one more floor|extra floor)", t0)) r.relFloors = 1;
        else if (test("(ลดชั้น|เตี้ยลง|น้อยชั้นลง|fewer floors|shorter)", t0)) r.relFloors = -1;
        if (test("(แบบเดิม|เหมือนเดิม|อันเดิม|หลังเดิม|เหมือนหลังที่แล้ว|อีกหลัง|same as before|same again|another one|อีกหลังนึง|same one)", t0)) r.same = true;
        if (r.rel()) {
            said.add(new L("ปรับจากแบบล่าสุด" + (r.relSize != null ? (r.relSize > 0 ? " ใหญ่ขึ้น" : " เล็กลง") : "") + (r.relFloors != null ? (r.relFloors > 0 ? " เพิ่มชั้น" : " ลดชั้น") : ""),
                    "like the last one" + (r.relSize != null ? (r.relSize > 0 ? ", bigger" : ", smaller") : "") + (r.relFloors != null ? (r.relFloors > 0 ? ", one more floor" : ", one floor less") : "")));
        }
        // ---- type
        String nt = matchNewType(t0);
        if (nt != null) o.type = nt;
        else
            for (String[] e : TYPE_RE)
                if (test(e[1], t0)) {
                    o.type = e[0];
                    break;
                }
        if (o.type != null) said.add(new L("ประเภท" + ExtraGen.typeName(o.type, true), ExtraGen.typeName(o.type, false)));
        // ---- variant / model
        m = match("(?:แบบที่|แบบ|variant|version|\\bv)\\s*(\\d{1,2})(?!\\s*(?:ชั้น|floor|x|×|คูณ))", n);
        if (m != null && Integer.parseInt(m.group(1)) >= 1 && o.type != null) {
            o.variant = Integer.parseInt(m.group(1)) - 1;
            said.add(new L("แบบที่ " + m.group(1), "variant " + m.group(1)));
        } else if ("underground".equals(o.type)) {
            for (Object[] e : BUNKER_WORDS)
                if (test((String) e[1], t0.replace("ใต้ดิน", " "))) {
                    o.variant = (Integer) e[0];
                    said.add(new L("แบบ" + com.helperbuddy.gen.BunkerGen.VARIANTS[o.variant][0], com.helperbuddy.gen.BunkerGen.VARIANTS[o.variant][1]));
                    break;
                }
        }
        if ("statue".equals(o.type)) {
            for (int i = 0; i < StatueGen.MODELS.length; i++)
                if (StatueGen.MODELS[i].re().matcher(t0).find()) {
                    o.model = i;
                    said.add(new L("โมเดล" + StatueGen.MODELS[i].th(), StatueGen.MODELS[i].en()));
                    break;
                }
        }
        if (o.variant == null && test("(สุ่ม|random|แบบอื่น|แบบใหม่|อีกแบบ|different|another style|surprise me)", t0)) {
            o.variant = (int) (Math.random() * 1000);
            said.add(new L("สุ่มแบบใหม่", "a random new design"));
        }
        // ---- roof (first, so "spruce roof" is not read as the wood of the house)
        String roofStr = "";
        Matcher rTh = match("หลังคา\\s*((?:สี\\s*)?[^\\s,]*)", t);
        Matcher rEn = match("((?:[a-z]+\\s)?[a-z]+)\\s+roof", t);
        if (rEn == null) rEn = match("roof\\s+(?:of\\s+|made of\\s+)?((?:[a-z]+\\s)?[a-z]+)", t);
        if (rTh != null) {
            roofStr = rTh.group(1);
            t = t.replace(rTh.group(0), " ");
        } else if (rEn != null) {
            roofStr = rEn.group(1);
            t = t.replace(rEn.group(0), " ");
        }
        if (!roofStr.isEmpty()) {
            int rr = -1;
            if (test("(อิฐ|brick|ส้ม|แดง|red|orange)", roofStr)) rr = 4;
            else if (test("(ดีพ|deepslate|slate|กระเบื้อง|tile|เทา|ดำ|gr[ae]y|black|dark)", roofStr)) rr = 5;
            else if (test("(หิน|stone)", roofStr)) rr = 6;
            else if (test("(โอ๊คเข้ม|ดาร์ค|dark oak)", roofStr)) rr = 3;
            else if (test("(สปรูซ|spruce|น้ำตาล|brown)", roofStr)) rr = 2;
            else if (test("(เดียวกัน|same|match)", roofStr)) rr = 1;
            if (rr >= 0) {
                o.roof = rr;
                said.add(new L("หลังคา" + com.helperbuddy.gen.HouseGen.ROOFS[rr][0], com.helperbuddy.gen.HouseGen.ROOFS[rr][1] + " roof"));
            }
        }
        if (test("(ใหญ่|large|\\bbig\\b|huge|giant|massive|ยักษ์|มหึมา|ใหญ่โต|กว้างขวาง|โอ่อ่า|สุดหรู|enormous)", t) && r.relSize == null) o.size = 2;
        else if (test("(กลาง|medium|\\bmid\\b|normal size|ขนาดปกติ)", t)) o.size = 1;
        else if (test("(เล็ก|small|tiny|little|จิ๋ว|มินิ|mini|กะทัดรัด|compact|cozy)", t) && r.relSize == null) o.size = 0;
        if (o.size != null) said.add(new L("ขนาด" + com.helperbuddy.gen.HouseGen.SIZE_NAMES[o.size][0], com.helperbuddy.gen.HouseGen.SIZE_NAMES[o.size][1]));
        m = match("(\\d+)\\s*(?:ชั้น|floors?|stor(?:e)?ys?|stories|levels?)", n);
        if (m == null) m = match("(?:ชั้น|floors?)\\s*(\\d+)", n);
        if (m != null) {
            o.floors = Math.max(1, Math.min(10, Integer.parseInt(m.group(1))));
            o.floorsExact = true;
            said.add(new L(o.floors + " ชั้น", o.floors + (o.floors == 1 ? " floor" : " floors")));
        } else if (test("(single.?stor|one.?stor|bungalow|ชั้นเดียว)", t)) {
            o.floors = 1;
            said.add(new L("1 ชั้น", "1 floor"));
        }
        if (test("(โมเดิร์น|โมเดิน|modern|ทันสมัย|มินิมอล|minimal|contemporary)", t)) o.style = 2;
        else if (test("(ยุคกลาง|medieval|อัศวิน|knight|tudor)", t)) o.style = 1;
        else if (test("(เรือนไทย|ไทย|thai)", t)) o.style = 3;
        else if (test("(คันทรี|country|cottage|กระท่อม|ชนบท|rustic|cabin)", t)) o.style = 0;
        if (o.style != null) said.add(new L(com.helperbuddy.gen.HouseGen.STYLES[o.style][0], com.helperbuddy.gen.HouseGen.STYLES[o.style][1]));
        Object[][] WOOD_RE = {
                {5, "(โอ๊คเข้ม|โอ๊กเข้ม|ดาร์คโอ๊ค|dark ?oak|ไม้เข้ม)"}, {8, "(โอ๊คซีด|โอ๊กซีด|pale ?oak|ไม้ซีด)"}, {0, "(โอ๊ค|โอ๊ก|\\boak\\b)"},
                {1, "(สปรูซ|spruce|ไม้สน|pine)"}, {2, "(เบิร์ช|เบิช|birch|ไม้ขาว)"}, {3, "(ป่าดิบ|จังเกิ้ล|จังเกิล|jungle)"},
                {4, "(อะเคเชีย|อาคาเซีย|อะคาเซีย|acacia)"}, {6, "(โกงกาง|แมงโกรฟ|mangrove)"}, {7, "(เชอร์รี่|เชอรี่|ซากุระ|cherry|sakura|ชมพู|pink)"},
        };
        for (Object[] w : WOOD_RE)
            if (test((String) w[1], t)) {
                o.wood = (Integer) w[0];
                said.add(new L(com.helperbuddy.gen.HouseGen.WOODS[o.wood][1], com.helperbuddy.gen.HouseGen.WOODS[o.wood][2] + " wood"));
                break;
            }
        // ---- features
        if (neg(t, "สระ") || test("(no pool|without (a )?pool)", t)) {
            o.pool = false;
            said.add(new L("ไม่มีสระ", "no pool"));
        } else if (test("(สระ|pool|swimming)", t)) {
            o.pool = true;
            said.add(new L("มีสระว่ายน้ำ", "with a pool"));
        }
        if (neg(t, "ปล่อง") || test("no chimney", t)) {
            o.chimney = false;
            said.add(new L("ไม่มีปล่องไฟ", "no chimney"));
        } else if (test("(ปล่อง|chimney)", t)) {
            o.chimney = true;
            said.add(new L("มีปล่องไฟ", "with a chimney"));
        }
        if (neg(t, "ระเบียง|เฉลียง|ทางเข้า|บันไดหน้าบ้าน") || test("no porch", t)) {
            o.porch = false;
            said.add(new L("ไม่มีทางเข้าหน้าบ้าน", "no porch"));
        } else if (test("(ระเบียง|เฉลียง|porch|veranda|balcony)", t)) {
            o.porch = true;
            said.add(new L("มีระเบียงหน้าบ้าน", "with a porch"));
        }
        if (neg(t, "ไฟ|ตะเกียง|โคม") || test("no lights?", t)) {
            o.light = false;
            said.add(new L("ไม่มีตะเกียง", "no lamps"));
        }
        if (neg(t, "ของใช้|เฟอร์|ของในบ้าน|ของ") || test("(no furniture|unfurnished|empty house|บ้านเปล่า)", t)) {
            o.furniture = false;
            said.add(new L("บ้านเปล่า ไม่มีของใช้", "empty, no furniture"));
        } else if (test("(ของใช้|เฟอร์นิเจอร์|furniture|furnished|เตา|หีบ|ครบครัน)", t)) {
            o.furniture = true;
            said.add(new L("มีของใช้ครบ", "fully furnished"));
        }
        // ---- material
        int matIdx = -1;
        if (test("(อิฐหิน|หินอิฐ|stone ?brick)", t)) matIdx = 1;
        if (matIdx < 0)
            for (int idx : new int[] {5, 6, 8, 9, 7, 4, 3, 1}) {
                for (Object[] e : MAT_RE)
                    if ((Integer) e[0] == idx && test((String) e[1], t)) {
                        matIdx = idx;
                        break;
                    }
                if (matIdx >= 0) break;
            }
        if (matIdx < 0 && o.type != null && test("(บ้านไม้|ไม้ล้วน|wooden|\\bwood\\b)", t)) matIdx = 2;
        if (matIdx >= 0 && !(matIdx == 4 && "desert".equals(o.type)) && !(matIdx == 6 && "nether".equals(o.type))) {
            o.mat = matIdx;
            said.add(new L("วัสดุ" + ExtraGen.MATS[matIdx][1], ExtraGen.MATS[matIdx][2]));
            if (o.type == null && o.style == null) {
                o.type = "box";
                said.add(new L("ประเภทบ้านทรงกล่อง", "box house"));
            }
        }
        if (o.type == null && o.style != null) o.type = "standard";
        if (Boolean.TRUE.equals(o.floorsExact) && o.floors > 3 && (o.type == null || o.type.equals("standard")))
            said.add(new L("(บ้านทั่วไปสูงสุด 3 ชั้น อยากได้สูงกว่านี้ลอง หอคอย หรือ คฤหาสน์)", "(normal houses have up to 3 floors: try a tower or a mansion for more)"));
        if (test("(เร็วมาก|เร็วที่สุด|very fast|super fast|fastest|ด่วนมาก|instant)", t)) o.speed = 2;
        else if (test("(เร็ว|\\bfast\\b|quick|ด่วน|hurry)", t)) o.speed = 1;
        else if (test("(ช้าๆ|ช้า|\\bslow\\b)", t)) o.speed = 0;
        if (o.speed != null) said.add(new L(new String[] {"สร้างแบบปกติ", "สร้างเร็ว", "สร้างเร็วมาก"}[o.speed], new String[] {"normal speed", "fast", "very fast"}[o.speed]));
        return r;
    }

    static boolean neg(String t, String words) {
        return re("(ไม่(ต้อง)?(เอา|มี|ใส่)?(" + words + "))").matcher(t).find();
    }

    // ================================================================== blocks, animals, amounts
    public record BlockWord(String[] ids, String th, String en, String re) {}

    public static final BlockWord[] BLOCK_WORDS = {
            new BlockWord(new String[] {"bamboo"}, "ไม้ไผ่", "bamboo", "(ไม้ไผ่|ไผ่|bamboo)"),
            new BlockWord(new String[] {"sugar_cane"}, "อ้อย", "sugar cane", "(อ้อย|sugar ?cane|\\bcanes?\\b|\\breeds?\\b)"),
            new BlockWord(new String[] {"cactus"}, "กระบองเพชร", "cactus", "(กระบองเพชร|แคคตัส|cactus|cacti)"),
            new BlockWord(new String[] {"pumpkin"}, "ฟักทอง", "pumpkins", "(ฟักทอง|pumpkins?)"),
            new BlockWord(new String[] {"melon"}, "แตงโม", "melons", "(แตงโม|melons?)"),
            new BlockWord(new String[] {"clay"}, "ดินเหนียว", "clay", "(ดินเหนียว|\\bclay\\b)"),
            new BlockWord(new String[] {"soul_sand", "soul_soil"}, "ทรายวิญญาณ", "soul sand", "(ทรายวิญญาณ|ดินวิญญาณ|soul sand|soul soil)"),
            new BlockWord(new String[] {"sandstone"}, "หินทราย", "sandstone", "(หินทราย|sandstone)"),
            new BlockWord(new String[] {"red_sand"}, "ทรายแดง", "red sand", "(ทรายแดง|red sand)"),
            new BlockWord(new String[] {"sand", "red_sand"}, "ทราย", "sand", "(ทราย|\\bsand\\b)"),
            new BlockWord(new String[] {"gravel"}, "กรวด", "gravel", "(กรวด|gravel)"),
            new BlockWord(new String[] {"granite"}, "หินแกรนิต", "granite", "(แกรนิต|granite)"),
            new BlockWord(new String[] {"diorite"}, "หินไดออไรต์", "diorite", "(ไดออไรต์|ไดโอไรต์|ไดออไรท์|diorite)"),
            new BlockWord(new String[] {"andesite"}, "หินแอนดีไซต์", "andesite", "(แอนดีไซต์|แอนดีไซท์|แอนดิไซต์|andesite)"),
            new BlockWord(new String[] {"tuff"}, "หินทัฟฟ์", "tuff", "(ทัฟฟ์|ทัฟ|\\btuff\\b)"),
            new BlockWord(new String[] {"deepslate", "cobbled_deepslate"}, "ดีพสเลท", "deepslate", "(ดีพสเลท|ดีปสเลท|deepslate)"),
            new BlockWord(new String[] {"netherrack"}, "เนเธอร์แร็ค", "netherrack", "(เนเธอร์แร็ค|เนเธอร์แรค|netherrack)"),
            new BlockWord(new String[] {"obsidian"}, "ออบซิเดียน", "obsidian", "(ออบซิเดียน|โอบซิเดียน|obsidian)"),
            new BlockWord(new String[] {"glowstone"}, "กลาวสโตน", "glowstone", "(กลาวสโตน|glowstone|หินเรืองแสง)"),
            new BlockWord(new String[] {"ice", "packed_ice", "blue_ice"}, "น้ำแข็ง", "ice", "(น้ำแข็ง|\\bice\\b)"),
            new BlockWord(new String[] {"snow_block", "snow"}, "หิมะ", "snow", "(หิมะ|\\bsnow\\b)"),
            new BlockWord(new String[] {"mud"}, "โคลน", "mud", "(โคลน|\\bmud\\b)"),
            new BlockWord(new String[] {"moss_block"}, "บล็อกมอส", "moss", "(มอส|\\bmoss\\b)"),
            new BlockWord(new String[] {"red_mushroom", "brown_mushroom"}, "เห็ด", "mushrooms", "(เห็ด|mushrooms?)"),
            new BlockWord(new String[] {"dandelion", "poppy", "blue_orchid", "allium", "azure_bluet", "red_tulip", "orange_tulip", "white_tulip", "pink_tulip", "oxeye_daisy", "cornflower", "lily_of_the_valley", "sunflower", "lilac", "rose_bush", "peony"},
                    "ดอกไม้", "flowers", "(ดอกไม้|flowers?)"),
            new BlockWord(new String[] {"oak_leaves", "spruce_leaves", "birch_leaves", "jungle_leaves", "acacia_leaves", "dark_oak_leaves", "mangrove_leaves", "cherry_leaves", "azalea_leaves"},
                    "ใบไม้", "leaves", "(ใบไม้|leaves|\\bleaf\\b)"),
            new BlockWord(new String[] {"dirt", "grass_block", "coarse_dirt", "podzol", "rooted_dirt"}, "ดิน", "dirt", "(ดิน|\\bdirt\\b|\\bsoil\\b)"),
            new BlockWord(new String[] {"stone", "cobblestone"}, "หิน", "stone", "(หิน|\\bstones?\\b|cobblestone|cobble|\\brocks?\\b)"),
    };

    public static BlockWord blockIn(String t) {
        String s = t.replaceAll("ใต้ดิน|underground|ประตูเนเธอร์|หินเหล็กไฟ|หินแดง|หินทราย(?!$)", " ");
        for (BlockWord b : BLOCK_WORDS) if (test(b.re, b.re.contains("หินทราย") ? t : s)) return b;
        return null;
    }

    public static final String[][] ANIMAL_WORDS = {
            {"cow", "(วัว|\\bcows?\\b|\\bbeef\\b|\\bcattle\\b)", "วัว", "cows"}, {"pig", "(หมู|\\bpigs?\\b|porkchop|\\bpork\\b)", "หมู", "pigs"},
            {"chicken", "(ไก่|chickens?)", "ไก่", "chickens"}, {"sheep", "(แกะ|\\bsheep\\b|mutton|lamb)", "แกะ", "sheep"},
            {"rabbit", "(กระต่าย|rabbits?|bunny|bunnies)", "กระต่าย", "rabbits"},
    };

    public static String[] animalIn(String t) {
        for (String[] a : ANIMAL_WORDS) if (test(a[1], t)) return a;
        return null;
    }

    public record Qty(Integer n, boolean full, boolean trees) {}

    /** "20 ท่อน", "2 กอง", "จนเต็ม", "a stack", "until your bag is full" ... */
    public static Qty extractQty(String t) {
        if (test("(จน(กว่า)?(กระเป๋า)?(จะ)?เต็ม|ให้เต็ม|เต็มกระเป๋า|until (the |your )?(bag |inventory )?(is )?full|fill (up )?(your |the )?(bag|inventory)|as (much|many) as (you can|possible))", t))
            return new Qty(null, true, false);
        String n = numify(t);
        Matcher m = match("(\\d+)\\s*(?:กอง|แถว|stacks?)", n);
        if (m != null) return new Qty(clampN(Integer.parseInt(m.group(1)) * 64), false, false);
        if (test("(ครึ่งกอง|half a stack)", t)) return new Qty(32, false, false);
        if (test("(?:^|[^\\d])(กอง|a stack|one stack)", n)) return new Qty(64, false, false);
        m = match("(\\d+)\\s*(?:ต้น|trees?)", n);
        if (m != null) return new Qty(clampN(Integer.parseInt(m.group(1)) * 5), false, true);
        m = match("(\\d+)\\s*(?:ท่อน|ก้อน|อัน|ชิ้น|บล็อก|บล็อค|ตัว|ลูก|หัว|ใบ|เม็ด|แท่ง|ฟอง|เล่ม|blocks?|logs?|pieces?|items?|ores?|animals?|pcs)", n);
        if (m != null) return new Qty(clampN(Integer.parseInt(m.group(1))), false, false);
        m = match("(?:^|[^\\d\\-.])(\\d{1,4})(?![\\d.]|\\s*(?:ชั้น|วินาที|วิ|นาที|seconds?|minutes?|floors?|x|×|\\*|คูณ|by))", n);
        if (m != null) return new Qty(clampN(Integer.parseInt(m.group(1))), false, false);
        return new Qty(null, false, false);
    }

    static int clampN(int v) {
        return Math.max(1, Math.min(2304, v));
    }

    // ================================================================== places: "where is the village?", "take me to a desert"
    /** kind: structure / biome / bed / death / spawn / me / helper / work / coords / search; ids: registry ids ("#" = a tag). */
    public record Place(String kind, String[] ids, String th, String en, String text, int[] pos) {}

    /** {kind, Thai regex, English regex, Thai name, English name, ids...} */
    static final String[][] PLACES = {
            {"structure", "หมู่บ้าน|ชุมชน|ชาวบ้าน", "\\bvillages?\\b|\\btowns?\\b", "หมู่บ้าน", "a village", "#minecraft:village"},
            {"structure", "เหมืองร้าง|เหมืองเก่า|เหมืองทิ้ง", "mineshafts?|abandoned mines?", "เหมืองร้าง", "a mineshaft", "#minecraft:mineshaft"},
            {"structure", "สตรองโฮลด์|ป้อมใต้ดิน|ประตูเอนด์|พอร์ทัลเอนด์|ประตูไปเอนด์|ป้อมปราการใต้ดิน", "strongholds?|end portal", "สตรองโฮลด์ (ประตูไปเอนด์)", "the stronghold", "minecraft:stronghold"},
            {"structure", "ประตูเนเธอร์พัง|พอร์ทัลพัง|ประตูร้าง|ซากประตู|พอร์ทัลร้าง", "ruined portals?", "ซากประตูเนเธอร์", "a ruined portal", "#minecraft:ruined_portal"},
            {"structure", "เรืออับปาง|ซากเรือ|เรือจม|เรือแตก|เรือโจรสลัด|เรือผี|เรือร้าง", "shipwrecks?|sunken ships?|\\bwrecks?\\b|pirate ships?", "ซากเรือ", "a shipwreck", "#minecraft:shipwreck"},
            {"structure", "ซากใต้น้ำ|ซากปรักหักพังใต้ทะเล|ซากเมืองใต้น้ำ", "ocean ruins?|underwater ruins?", "ซากใต้น้ำ", "ocean ruins", "#minecraft:ocean_ruin"},
            {"structure", "วิหารใต้ทะเล|วิหารใต้น้ำ|มอนูเมนต์|ปราสาทใต้น้ำ", "monuments?|guardian temple", "วิหารใต้ทะเล", "an ocean monument", "minecraft:monument"},
            {"structure", "คฤหาสน์|แมนชั่น", "mansions?", "คฤหาสน์", "a woodland mansion", "minecraft:mansion"},
            {"structure", "พีระมิด|ปิรามิด|วิหาร(ใน|กลาง)?ทะเลทราย", "pyramids?|desert temples?", "พีระมิดทะเลทราย", "a desert pyramid", "minecraft:desert_pyramid"},
            {"structure", "วิหาร(ใน)?ป่า", "jungle temples?|jungle pyramids?", "วิหารป่า", "a jungle temple", "minecraft:jungle_pyramid"},
            {"structure", "บ้านแม่มด|กระท่อมแม่มด", "witch huts?|swamp huts?", "บ้านแม่มด", "a witch hut", "minecraft:swamp_hut"},
            {"structure", "อิกลู|บ้านน้ำแข็ง", "igloos?", "อิกลู", "an igloo", "minecraft:igloo"},
            {"structure", "ป้อมโจร|ป้อมพิลเลเจอร์|ค่ายโจร|หอโจร", "outposts?", "ป้อมโจร", "a pillager outpost", "minecraft:pillager_outpost"},
            {"structure", "เมืองโบราณ|นครโบราณ|เมืองวอร์เดน", "ancient cit(y|ies)", "เมืองโบราณ", "an ancient city", "minecraft:ancient_city"},
            {"structure", "ซากโบราณสถาน|ซากเมืองเก่า", "trail ruins?", "ซากโบราณสถาน", "trail ruins", "minecraft:trail_ruins"},
            {"structure", "สมบัติ|หีบสมบัติ", "(buried )?treasure", "สมบัติที่ฝังไว้", "buried treasure", "minecraft:buried_treasure"},
            {"structure", "ห้องทดสอบ|ห้องทดลอง", "trial chambers?", "ห้องทดสอบ", "trial chambers", "minecraft:trial_chambers"},
            {"structure", "ป้อมเนเธอร์|ป้อมนรก|ฟอร์ทเรส", "fortress(es)?", "ป้อมเนเธอร์", "a nether fortress", "minecraft:fortress"},
            {"structure", "บาสเตียน|ป้อมพิกลิน|ป้อมหมูทอง", "bastions?", "บาสเตียน", "a bastion", "minecraft:bastion_remnant"},
            {"structure", "เมืองเอนด์|เอนด์ซิตี้", "end cit(y|ies)", "เมืองเอนด์", "an end city", "minecraft:end_city"},
            {"biome", "ทะเลทราย", "\\bdeserts?\\b", "ทะเลทราย", "a desert", "minecraft:desert"},
            {"biome", "ป่าดิบ|ป่าดงดิบ|ป่าฝน|จังเกิล", "jungles?", "ป่าดงดิบ", "a jungle", "#minecraft:is_jungle"},
            {"biome", "ป่าไผ่", "bamboo", "ป่าไผ่", "a bamboo jungle", "minecraft:bamboo_jungle"},
            {"biome", "ป่าสน|ไทกา", "taigas?|spruce", "ป่าสน", "a taiga", "#minecraft:is_taiga"},
            {"biome", "ป่ามืด|ป่ารกทึบ", "dark forests?|roofed forests?", "ป่ามืด", "a dark forest", "minecraft:dark_forest"},
            {"biome", "ซากุระ|เชอร์รี่|ป่าชมพู", "cherry", "ป่าซากุระ", "a cherry grove", "minecraft:cherry_grove"},
            {"biome", "ทุ่งดอกไม้|ป่าดอกไม้", "flowers?|meadows?", "ทุ่งดอกไม้", "flowers", "minecraft:flower_forest", "minecraft:meadow", "minecraft:sunflower_plains"},
            {"biome", "เกาะเห็ด|ทุ่งเห็ด|มูชรูม", "mushroom", "เกาะเห็ด", "a mushroom island", "minecraft:mushroom_fields"},
            {"biome", "ทะเล(?!ทราย)|มหาสมุทร", "\\boceans?\\b|\\bseas?\\b", "ทะเล", "the ocean", "#minecraft:is_ocean"},
            {"biome", "ชายหาด|หาด", "beach(es)?", "ชายหาด", "a beach", "#minecraft:is_beach"},
            {"biome", "แม่น้ำ|ลำธาร", "rivers?", "แม่น้ำ", "a river", "#minecraft:is_river"},
            {"biome", "ภูเขา|ยอดเขา|เขาสูง", "mountains?|peaks?", "ภูเขา", "mountains", "#minecraft:is_mountain"},
            {"biome", "เมซา|แบดแลนด์|ดินแดง", "badlands|mesa", "เมซา (ดินแดง)", "the badlands", "#minecraft:is_badlands"},
            {"biome", "ซาวันนา|สะวันนา", "savannas?", "ซาวันนา", "a savanna", "#minecraft:is_savanna"},
            {"biome", "หนองน้ำ|หนอง|บึง|ป่าชายเลน", "swamps?|marsh|mangroves?", "หนองน้ำ", "a swamp", "minecraft:swamp", "minecraft:mangrove_swamp"},
            {"biome", "หิมะ|ทุ่งหิมะ|น้ำแข็ง", "snowy|\\bsnow\\b|ice spikes|frozen", "ทุ่งหิมะ", "snow", "minecraft:snowy_plains", "minecraft:ice_spikes", "minecraft:snowy_taiga", "minecraft:grove"},
            {"biome", "ถ้ำเขียว|ถ้ำต้นไม้", "lush caves?", "ถ้ำเขียว", "lush caves", "minecraft:lush_caves"},
            {"biome", "ทุ่งหญ้า|ที่ราบ", "\\bplains\\b|grasslands?", "ทุ่งหญ้า", "plains", "minecraft:plains", "minecraft:sunflower_plains"},
            {"biome", "ป่า(?!ดิบ|ดง|ฝน|สน|ไผ่|มืด|รก|ชมพู|ดอก|ชายเลน)", "\\bforests?\\b|\\bwoods\\b", "ป่า", "a forest", "#minecraft:is_forest"},
            {"death", "ที่ตาย|จุดตาย|ที่ฉันตาย|ที่เราตาย|ศพ|ของที่ตก|ของที่หล่น|หลุมศพ|ที่ตายล่าสุด", "where (did )?i (die|died)|death (point|spot|place)|my grave|my (stuff|items|loot)", "ที่คุณตายล่าสุด", "where you died", ""},
            {"spawn", "จุดเกิดโลก|สปอน|สปอว์น|จุดเกิดของโลก", "\\bspawn\\b", "จุดเกิดของโลก", "the world spawn", ""},
            {"me", "(ฉัน|กู|ข้า|ผม|หนู|เรา|เค้า)(ตอนนี้)?อยู่(ที่|ตรง)?ไหน|ตอนนี้(ฉัน|กู|ข้า|ผม|เรา)?อยู่(ที่|ตรง)?ไหน|พิกัด(ของ)?(ฉัน|กู|ข้า|ผม|เรา|ตอนนี้)|ตำแหน่ง(ของ)?(ฉัน|กู|ข้า|ผม|เรา)", "where am i|where are we|my (coords|coordinates|position)", "ตำแหน่งของคุณ", "where you are", ""},
            {"helper", "(แก|เอ็ง|นาย|ตัวเอง|มึง|เธอ)อยู่(ที่|ตรง)?ไหน", "where are you", "ตำแหน่งของผม", "where I am", ""},
            {"work", "ที่ทำงาน|จุดทำงาน", "work ?(spot|place)", "ที่ทำงานของผม", "my work spot", ""},
            {"bed", "(?<!หมู่)บ้าน|เตียง|ฐานทัพ|ฐาน(?!ะ)", "\\bhome\\b|\\bhouse\\b|\\bbed\\b|\\bbase\\b", "บ้านของคุณ (เตียง)", "your home (bed)", ""},
    };

    static final String WHERE = "(อยู่(ที่|ตรง)?ไหน|อยู่ทางไหน|ทางไหน|ทิศไหน|ไปยังไง|ไปไง|ไปทางไหน|บอกทาง|ใกล้สุด|ใกล้ที่สุด|หาให้หน่อย|ช่วยหา|อยู่แถวไหน|^ไป.+(ยังไง|ไง)$|(แถวนี้|ใกล้ๆ(นี้)?|รอบๆ(นี้)?)\\s*(มี)?\\s*(ไหม|มั้ย|ป่ะ|ปะ|รึเปล่า|หรือเปล่า|บ้างไหม|บ้าง)"
            + "|where('s| is| are| can i find| do i find)?\\b|which way|how (do i|can i|to) get( to)?|\\bnearest\\b|\\bclosest\\b|\\blocate\\b|direction to|way to|is there an?)";
    static final String LEAD = "(พา(ฉัน|เรา|ผม|กู|ข้า|หนู)?ไป|นำทาง|พาทาง|ไปด้วยกัน|take me|lead me|guide me|bring me to|show me the way|escort me|walk me to|lead the way)";

    /** The place named in t, or null. */
    public static Place placeIn(String t) {
        String nt = numify(t);
        Matcher c = match("(-?\\d+)[\\s,]+(-?\\d+)[\\s,]+(-?\\d+)", nt);
        if (c != null) return new Place("coords", new String[0], "พิกัดนั้น", "those coordinates", t,
                new int[] {Integer.parseInt(c.group(1)), Integer.parseInt(c.group(2)), Integer.parseInt(c.group(3))});
        String[] p = placeRow(t);
        if (p == null) return null;
        String[] ids = java.util.Arrays.copyOfRange(p, 5, p.length);
        if (ids.length == 1 && ids[0].isEmpty()) ids = new String[0];
        return new Place(p[0], ids, p[3], p[4], t, null);
    }

    /** The PLACES row for t, or null. */
    static String[] placeRow(String t) {
        for (String[] p : PLACES) if (test("(" + p[1] + ")", t) || test("(" + p[2] + ")", t)) return p;
        return null;
    }

    /** Whatever is left of a where-question once the question words are gone ("dungeon" in "where is the dungeon"). */
    static String placeWords(String t) {
        return t.replaceAll(WHERE.replace("\\b", ""), " ").replaceAll(LEAD, " ").replaceAll("(the|a|an|to|me|ที่|หน่อย|ให้|ฉัน|เรา)\\b|^มี", " ").replaceAll("\\s+", " ").trim();
    }

    /** Navigation, memory, personality and "what is this" (null: none of those). */
    static Intent parseExtra(String t, String raw) {
        // personality ("อย่าหยาบ" before "หยาบ")
        if (has(t, "พูดสุภาพ", "พูดดีๆ", "สุภาพหน่อย", "เลิกกวน", "อย่ากวน", "อย่าหยาบ", "เลิกหยาบ", "เลิกด่า", "อย่าด่า", "be polite", "be nice", "stop being rude", "no swearing", "stop swearing", "stop teasing")) {
            Intent i = new Intent("persona");
            i.text = "polite";
            return i;
        }
        if (has(t, "พูดหยาบ", "หยาบได้", "ด่าได้", "ด่าเลย", "ด่าแรง", "พูดกูมึง", "กูมึงได้", "be rude", "you can swear", "swear at me", "insult me", "be mean")) {
            Intent i = new Intent("persona");
            i.text = "rude";
            return i;
        }
        if (has(t, "กวนตีน", "กวนๆ", "กวนได้", "ขี้เล่นหน่อย", "แซวได้", "be sassy", "be cheeky", "tease me", "be funny")) {
            Intent i = new Intent("persona");
            i.text = "sassy";
            return i;
        }
        // memory
        Matcher rm = match("^(?:จำไว้(?:นะ)?(?:ว่า)?|จำให้ดี(?:ว่า)?|ช่วยจำ(?:ว่า)?|remember(?: that)?|don'?t forget(?: that)?|note that)\\s*(.+)$", t);
        if (rm != null && !test("(จำอะไร|จำได้ไหม|จำ.*ได้บ้าง|remember me|what do you remember)", t)) {
            Intent i = new Intent("remember");
            i.text = raw.replaceAll("§.", "").trim().replaceFirst("(?iu)^.*?(จำไว้(นะ)?(ว่า)?|จำให้ดี(ว่า)?|ช่วยจำ(ว่า)?|remember( that)?|don'?t forget( that)?|note that)\\s*", "");
            return i;
        }
        if (has(t, "จำอะไรได้บ้าง", "จำอะไรได้", "จำฉันได้ไหม", "จำได้ไหม", "รู้อะไรเกี่ยวกับฉัน", "รู้อะไรเกี่ยวกับเรา", "ความจำ", "what do you remember", "do you remember me",
                "what do you know about me", "your memory"))
            return new Intent("recall");
        Matcher fm = match("^(?:ลืม|forget)\\s*(?:เรื่อง|about|that)?\\s*(.*)$", t);
        if (fm != null && !has(t, "forget it")) {
            Intent i = new Intent("forget");
            String w = fm.group(1).trim();
            i.all = w.isEmpty() || has(w, "ทุกอย่าง", "หมด", "ทั้งหมด", "everything", "all");
            i.text = i.all ? "" : w;
            return i;
        }
        // what is this / what is around / which mods
        if (has(t, "นี่คืออะไร", "อันนี้คืออะไร", "นี่อะไร", "อันนี้อะไร", "ตัวนี้คืออะไร", "ตัวนี้อะไร", "บล็อกนี้คืออะไร", "บล็อกนี้อะไร", "ของในมือ", "ถืออะไรอยู่", "มองอะไรอยู่",
                "นั่นคืออะไร", "นั่นอะไร", "ตัวนั้นคืออะไร", "ของมอดอะไร", "มาจากมอดไหน", "what is this", "whats this", "what is that", "whats that", "what am i holding",
                "what am i looking at", "which mod"))
            return new Intent("whatis");
        if (has(t, "มีมอดอะไร", "ลงมอดอะไร", "มอดอะไรบ้าง", "มอดที่ลง", "what mods", "which mods", "mod list", "installed mods"))
            return new Intent("mods");
        if (has(t, "แถวนี้มีอะไร", "รอบๆ มีอะไร", "รอบตัวมีอะไร", "มีอะไรอยู่ใกล้", "เห็นอะไรบ้าง", "มีมอนอะไร", "มีสัตว์อะไร", "มีอะไรแถวนี้", "what's around", "whats around",
                "what is around", "what do you see", "anything nearby", "what mobs", "whats nearby", "what's nearby", "what is nearby", "scan the area"))
            return new Intent("around");
        // where is ... / take me to ...
        boolean lead = test(LEAD, t);
        boolean where = test(WHERE, t);
        // "หาหมู่บ้านให้", "find a village": finding a place is a where-question too
        if (!lead && !where && test("^(ไป)?หา|^find\\b|^look for\\b|^search for\\b", t)) {
            Place pl = placeIn(t);
            if (pl != null && (pl.kind().equals("structure") || pl.kind().equals("biome"))) {
                Intent i = new Intent("locate");
                i.place = pl;
                return i;
            }
        }
        Matcher gm = lead || where ? null : match("^(?:ไป|เดินไป|ออกเดินทางไป|go to|head to|let'?s go to|travel to)\\s*(.+)$", t);
        if (gm != null) {
            // only a place and filler words after it ("ไปหมู่บ้านกัน"), not "ไปสร้างบ้านในป่า"
            String[] row = placeRow(gm.group(1));
            String left = row == null ? "x" : gm.group(1).replaceAll("(" + row[1] + ")|(" + row[2] + ")", " ")
                    .replaceAll("(ที่|ยัง|กัน|เลย|หน่อย|ด้วย|ดิ|ดีกว่า|นะ|ใกล้ๆ|ใกล้สุด|ใกล้ที่สุด|สักที่|ซักที่|ไหนก็ได้|the|an?|nearest|closest|now|together|please|pls)", " ").trim();
            Place pl = left.isEmpty() ? placeIn(gm.group(1)) : null;
            if (pl != null && (pl.kind().equals("structure") || pl.kind().equals("biome"))) {
                Intent i = new Intent("locate");
                i.place = pl;
                i.lead = true;
                return i;
            }
        }
        if (lead || where) {
            Place pl = placeIn(t);
            if (pl == null && where && !lead) {
                // "where is <something we have no word for>": maybe a structure or biome of another mod, by name
                String w = placeWords(t);
                if (w.isEmpty() || w.length() > 40) return null;
                pl = new Place("search", new String[0], w, w, w, null);
            } else if (pl == null) {
                String w = placeWords(t);
                // "พาไปเลย", "take me there": the place asked about last
                if (w.isEmpty() || test("^(เลย|ที่นั่น|ตรงนั้น|ตรงนั้นเลย|there|now|that place|it)$", w)) pl = new Place("last", new String[0], "", "", w, null);
                else pl = new Place("search", new String[0], w, w, w, null);
            }
            Intent i = new Intent("locate");
            i.place = pl;
            // "ไปหมู่บ้านที่ใกล้ที่สุด", "go to the nearest village": go there, not just tell
            i.lead = lead || (test("^(ไป|เดินไป|go to|head to|let'?s go to|travel to)", t) && !test("(ทางไหน|ยังไง|ไปไง|ทิศไหน|อยู่(ที่|ตรง)?ไหน|which way|how)", t));
            return i;
        }
        return null;
    }

    // ================================================================== intents
    public static final class Intent {
        public String k = "unknown";
        /** where-is / take-me-to: the place, and whether to lead the way */
        public Place place;
        public boolean lead, meat;
        public String name, text, item;
        public Integer secs, n;
        public int[] pos;
        public boolean home, work, all, on = true, full;
        public List<String> ores = new ArrayList<>();
        public String[] ids;
        public BlockWord block;
        public String[] animal;
        public boolean hunt = true, crops = true;
        public MineReq mine;
        public HouseReq house;

        Intent(String k) {
            this.k = k;
        }

        @Override
        public String toString() {
            return k + (item != null ? "(" + item + ")" : "") + (ids != null ? java.util.Arrays.toString(ids) : "") + (ores.isEmpty() ? "" : ores.toString())
                    + (house != null && house.o.type != null ? "[" + house.o.type + "]" : "") + (n != null ? " n=" + n : "") + (k.equals("fight") ? " on=" + on : "");
        }
    }

    static final String NAME_RE = "(?:เปลี่ยนชื่อ(?:เป็น)?|ตั้งชื่อ(?:ว่า|เป็น|ให้)?|ต่อไปนี้(?:นาย)?ชื่อ|เรียกนายว่า|นายชื่อ|your name is|rename(?: you)?(?: to)?|i(?:'ll| will) call you|call you|from now on you are)\\s*[:\"'“]?\\s*(.+?)[\"'”]?\\s*$";

    static final String CRAFT_VERB = "(คราฟต์|คราฟท์|คราฟ|ประดิษฐ์|สร้างไอเทม|ทำให้หน่อย|\\bcraft\\b|\\bcrafting\\b|\\bmake me\\b|\\bmake\\b|ทำ)";
    static final String SMELT_VERB = "(ถลุง|หลอม|เผาแร่|เผา|\\bsmelt\\b|\\bsmelting\\b|\\bmelt\\b|\\bforge\\b|\\bbake\\b|\\bburn\\b)";

    public static Intent parse(String raw) {
        return parse(raw, false);
    }

    public static Intent parse(String raw, boolean noFix) {
        String t = norm(raw);
        if (t.isEmpty()) return new Intent("unknown");
        Matcher nm = Pattern.compile(NAME_RE, Pattern.CASE_INSENSITIVE).matcher(raw.replaceAll("§.", "").trim());
        if (nm.find() && !nm.group(1).trim().isEmpty()) {
            Intent i = new Intent("name");
            i.name = nm.group(1).trim();
            return i;
        }
        if (has(t, "ช่วยด้วย", "help me", "save me", "ช่วยฉันด้วย", "ช่วยเราด้วย", "i'm dying", "im dying", "i am dying", "protect me", "ปกป้องฉัน"))
            return new Intent("rescue");
        if (t.equals("?") || t.equals("ช่วย") || has(t, "help", "ทำอะไรได้", "คำสั่ง", "วิธีใช้", "สอนใช้", "ใช้ยังไง", "สั่งอะไรได้", "what can you do", "commands"))
            return new Intent("help");
        if (has(t, "ยกเลิก", "cancel", "abort", "nevermind", "never mind", "forget it")) return new Intent("cancel");
        if (has(t, "อีกที", "อีกรอบ", "อีกครั้ง", "ซ้ำ", "ทำอีก", "again", "repeat", "one more time", "do it again")) return new Intent("repeat");
        if (has(t, "ทำต่อ", "สร้างต่อ", "ขุดต่อ", "ทำงานต่อ", "continue", "resume", "กลับไปทำงาน", "keep going", "carry on")) return new Intent("resume");
        if (has(t, "ไม่ต้องสู้กลับ", "ไม่ต้องตีกลับ", "อย่าตีกลับ", "don't fight back", "dont fight back", "never fight back")) {
            Intent i = new Intent("fightback");
            i.on = false;
            return i;
        }
        if (has(t, "สู้กลับ", "ตีกลับ", "fight back", "defend yourself", "ป้องกันตัว")) {
            Intent i = new Intent("fightback");
            i.on = true;
            return i;
        }
        if (has(t, "ไม่ต้องสู้", "อย่าสู้", "เลิกสู้", "หยุดสู้", "ไม่สู้", "ห้ามสู้", "อย่าตี", "ห้ามตี", "ไม่ต้องตี", "สันติ", "peace", "don't fight", "dont fight",
                "no fight", "stop fighting", "stop attacking", "be peaceful", "passive")) {
            Intent i = new Intent("fight");
            i.on = false;
            return i;
        }
        if (has(t, "ไม่ต้องตาม", "อย่าตาม", "don't follow", "dont follow", "stop following")) return new Intent("stay");
        Intent extra = parseExtra(t, raw);
        if (extra != null) return extra;
        String nt = numify(t);
        Matcher wm = match("(?:รอ|wait|พัก)\\s*(?:ก่อน|for)?\\s*(\\d+)\\s*(วินาที|วิ|seconds?|secs?|s\\b|นาที|minutes?|mins?)", nt);
        if (wm != null) {
            Intent i = new Intent("wait");
            i.secs = Math.min(3600, Integer.parseInt(wm.group(1)) * (wm.group(2).startsWith("นาที") || wm.group(2).startsWith("min") ? 60 : 1));
            return i;
        }
        Matcher gm = match("(?:ไปที่|ไป|เดินไป|พิกัด|go to|goto|walk to|travel to|move to|head to|teleport to)\\s*(?:ที่|พิกัด|the)?\\s*x?\\s*(-?\\d+)[\\s,]+y?\\s*(-?\\d+)[\\s,]+z?\\s*(-?\\d+)", nt);
        if (gm != null) {
            Intent i = new Intent("goto");
            i.pos = new int[] {Integer.parseInt(gm.group(1)), Integer.parseInt(gm.group(2)), Integer.parseInt(gm.group(3))};
            return i;
        }
        if (has(t, "กลับบ้าน", "ไปบ้าน", "กลับไปบ้าน", "go home", "return home", "back home") && !has(t, "สร้าง", "build")) {
            Intent i = new Intent("goto");
            i.home = true;
            return i;
        }
        if (has(t, "กลับไปที่ทำงาน", "กลับไปที่เดิม", "go back to work", "back to work", "go back there")) {
            Intent i = new Intent("goto");
            i.work = true;
            return i;
        }
        if (has(t, "หยุด", "เลิก", "พอแล้ว", "พอก่อน", "stop", "quit", "enough") && !has(t, "สู้", "fight")) return new Intent("stay");
        // take something out of a chest
        if (has(t, "จากหีบ", "ในหีบมา", "from the chest", "from chest", "out of the chest", "from a chest")) {
            Intent i = new Intent("fetch");
            i.text = t;
            Qty q = extractQty(t);
            i.n = q.n();
            return i;
        }
        if (has(t, "เข้าหีบ", "ใส่หีบ", "ลงหีบ", "ในหีบ", "ไว้ในหีบ", "เก็บเข้า", "into the chest", "in the chest", "in a chest", "to the chest", "put away",
                "store it", "store them", "store items", "store everything", "empty your bag", "deposit")) return new Intent("deposit");
        boolean mineBuild = has(t, "สร้างเหมือง", "ทำเหมือง", "ขุดเหมือง", "เหมืองใหม่", "อุโมงค์", "ขุดบันได", "ขุดลง", "ทางลงเหมือง", "mine shaft", "mineshaft",
                "build a mine", "build mine", "make a mine", "dig a mine", "branch mine", "strip mine", "tunnel", "staircase", "dig down")
                || (has(t, "เหมือง") && has(t, "สร้าง", "ทำ", "เจาะ"));
        if (mineBuild) {
            Intent i = new Intent("dig");
            i.mine = parseMine(t);
            return i;
        }
        boolean houseWords = has(t, "บ้าน", "house", "home", "ปราสาท", "castle", "หอคอย", "tower", "อิกลู", "igloo", "ปิรามิด", "pyramid", "บังเกอร์", "bunker",
                "ฐานลับ", "ห้องลับ", "ป้อมปราการ", "fortress", "ที่พักอาศัย", "ที่อยู่อาศัย", "treehouse", "ป้อม", "nether fortress", "ประภาคาร", "lighthouse",
                "คฤหาสน์", "mansion", "villa", "วิลล่า", "หลบภัย", "safe house", "safehouse", "เซฟเฮ", "shelter", "อีกหลัง", "ที่หลบภัย", "ที่พัก", "base", "cabin",
                "cottage", "hut", "กระท่อม", "building", "อาคาร", "ตึก")
                && !has(t, "กลับบ้าน", "go home", "กลับไปบ้าน", "ไปบ้าน", "ถึงบ้าน", "home base");
        if ((matchNewType(t) != null && !has(t, "ขุดแร่", "ตัดไม้", "กลับบ้าน")) || houseWords) {
            Intent i = new Intent("house");
            i.house = parseHouse(t);
            return i;
        }
        // cooking and smelting
        if (has(t, "ทำอาหาร", "ทำกับข้าว", "ปิ้ง", "ย่าง", "ทอด", "ทำขนมปัง", "cook", "grill", "roast", "make food", "make bread", "bake bread"))
            return new Intent("cook");
        if (test(SMELT_VERB, t) && (oresIn(t).size() > 0 || has(t, "แร่", "ore", "ores", "raw", "ดิบ", "ทราย", "sand", "glass", "กระจก", "หิน", "stone", "ถ่าน", "charcoal", "อิฐ", "brick")
                || has(t, "ถลุง", "smelt"))) {
            Intent i = new Intent("smelt");
            i.text = t;
            i.n = extractQty(t).n();
            return i;
        }
        String[] animal = animalIn(t);
        boolean cropsOnly = has(t, "เก็บเกี่ยว", "เก็บผัก", "เก็บผลไม้", "เก็บข้าวสาลี", "เก็บแครอท", "เก็บมัน", "เก็บเบอร์รี่", "harvest", "berries", "crops", "pick vegetables");
        boolean huntWords = has(t, "ล่า", "ฆ่า", "จับ", "หาเนื้อ", "เอาเนื้อ", "hunt", "kill", "slaughter", "get meat", "meat", "butcher");
        boolean meatWords = has(t, "เนื้อ", "meat", "beef", "pork", "porkchop", "porkchops", "mutton", "steak", "steaks", "drumstick", "raw chicken");
        // farming: plant seeds, breed, shear
        if (has(t, "ปลูก", "หว่าน", "ทำไร่", "ทำนา", "ทำสวน", "ทำฟาร์ม", "plant", "sow", "farm", "farming", "grow crops", "grow food", "make a farm")) {
            Intent i = new Intent("farm");
            i.text = t;
            return i;
        }
        if (has(t, "ผสมพันธุ์", "ให้อาหารสัตว์", "เพาะพันธุ์", "breed", "feed the animals", "feed animals", "make babies")) {
            Intent i = new Intent("breed");
            i.animal = animal;
            return i;
        }
        if (has(t, "ตัดขน", "เล็มขน", "shear", "wool")) return new Intent("shear");
        if (has(t, "หาอาหาร", "หาของกิน", "หาอะไรกิน", "หาข้าว", "หาเสบียง", "หาเนื้อ", "ล่าสัตว์", "find food", "get food", "gather food", "look for food", "go hunting", "collect food")
                || cropsOnly || (animal != null && (huntWords || has(t, "หา", "เอา", "get", "find")))
                || (meatWords && has(t, "หา", "เอา", "ล่า", "get", "find", "bring", "fetch", "collect", "gather", "hunt"))) {
            Intent i = new Intent("findFood");
            i.hunt = !cropsOnly;
            i.crops = !huntWords && animal == null;
            i.animal = animal;
            // "64 meat", "20 beef", "เนื้อ 64 ชิ้น": count the meat, not the animals ("3 cows", "วัว 3 ตัว" count animals)
            // "หมู 20 ชิ้น" / "20 pieces of pork": pieces are meat too
            i.meat = !cropsOnly && (meatWords || test("\\d+\\s*(ชิ้น|อัน|pieces?|pcs)", numify(t)))
                    && !test("\\d+\\s*(ตัว|animals?|cows|pigs|sheep|chickens|rabbits)", numify(t));
            return i;
        }
        if (has(t, "หิว", "hungry", "starving")) return new Intent("hungry");
        if (has(t, "กิน", "eat", "ทานข้าว", "heal yourself")) return new Intent("eat");
        if (has(t, "เฝ้า", "guard", "patrol", "keep watch", "protect this", "defend this")) return new Intent("guard");
        if (has(t, "ปักคบเพลิง", "ปักไฟ", "วางคบเพลิง", "จุดไฟให้สว่าง", "ทำให้สว่าง", "light up", "place torches", "put torches", "light the area")) return new Intent("torch");
        if (has(t, "ปรับพื้น", "ปรับพื้นที่", "ถางป่า", "ถางพื้นที่", "เคลียร์พื้นที่", "ทำให้เรียบ", "flatten", "level the ground", "clear the area", "clear area", "clear land"))
            return new Intent("flatten");
        if (has(t, "ไม้ไผ่", "ไผ่", "bamboo")) {
            Intent i = new Intent("gather");
            i.block = BLOCK_WORDS[0];
            i.ids = i.block.ids;
            return i;
        }
        // crafting ("craft a table", "ทำโต๊ะคราฟ 2 อัน") is checked before chopping so "ทำไม้กระดาน" is a craft
        if (test(CRAFT_VERB, t) && ItemWords.resolve(t) != null && !has(t, "ตัดไม้", "chop") && !(has(t, "ทำ") && has(t, "เหมือง"))) {
            Intent i = new Intent("craft");
            i.item = ItemWords.resolve(t);
            i.n = extractQty(t).n();
            i.text = t;
            return i;
        }
        if (has(t, "ตัดไม้", "ตัดต้นไม้", "หาไม้", "เก็บไม้", "ไม้", "chop", "wood", "lumber", "tree", "trees", "log", "logs", "timber", "cut down"))
            return new Intent("chop");
        // "ขอดาบเพชร" / "give me a diamond sword": a request for one item, not mining
        if (test("^(ขอ(?!บ)|เอา.+มาให้|ส่ง.+มาให้)|give me|bring me|hand me|can i have|i need|i want", t) && ItemWords.resolve(t) != null
                && !has(t, "ขุด", "mine", "dig", "ตัด", "chop")) {
            Intent i = new Intent("give");
            i.item = ItemWords.resolve(t);
            i.n = extractQty(t).n();
            return i;
        }
        List<String> ores = oresIn(t);
        BlockWord blk = blockIn(t);
        if (ores.isEmpty() && blk != null && has(t, "ขุด", "เก็บ", "หา", "เอา", "ตัก", "ทำ", "dig", "get", "collect", "gather", "mine", "grab", "bring", "farm", "pick", "harvest")) {
            Intent i = new Intent("gather");
            i.block = blk;
            i.ids = blk.ids;
            return i;
        }
        if (has(t, "ขุด", "แร่", "mine", "ore", "ores", "dig", "mining") || !ores.isEmpty()) {
            Intent i = new Intent("mine");
            i.ores = ores;
            i.all = has(t, "ทุกแร่", "ทุกอย่าง", "ทุกชนิด", "all ore", "all ores", "every ore", "everything", "anything");
            return i;
        }
        if (has(t, "สู้", "โจมตี", "ฆ่า", "ปกป้อง", "คุ้มกัน", "ป้องกัน", "ตีมัน", "ตีมอน", "มอนสเตอร์", "ซอมบี้", "fight", "attack", "defend", "protect", "kill", "monsters", "zombies", "help me fight"))
            return new Intent("fight");
        if (t.equals("มา") || has(t, "มานี่", "มาหา", "มาตรงนี้", "กลับมา", "มาเร็ว", "มาที่นี่", "come", "here", "teleport", "วาร์ป", "tp", "over here", "get over here"))
            return new Intent("come");
        if (has(t, "ตาม", "follow", "with me")) return new Intent("follow");
        if (has(t, "รอ", "หยุด", "อยู่ตรงนั้น", "อยู่นิ่ง", "นั่ง", "พัก", "stay", "wait", "stop", "sit", "hold", "don't move", "dont move", "stay there"))
            return new Intent("stay");
        if (has(t, "เลเวล", "เลเวิล", "อัพเลเวล", "อัปเลเวล", "ค่าประสบการณ์", "level", "lv", "exp", "xp", "เก่งขึ้น")) return new Intent("level");
        if (has(t, "สถานะ", "เป็นไง", "เป็นยังไง", "อยู่ไหน", "ที่ไหน", "เลือด", "status", "where", "hp", "health", "ทำอะไรอยู่", "report", "รายงาน", "what are you doing"))
            return new Intent("status");
        if (has(t, "เอาของมา", "ส่งของมา", "คืนของ", "ขอของ", "ส่งของคืน", "เอาของคืน", "give me", "give back", "my items", "ของมา", "เอามาให้", "เอามาส่ง", "ส่งมาให้",
                "เอามาคืน", "bring it", "bring them", "bring me", "bring back", "hand over", "give it", "ขอเอา")) {
            Intent i = new Intent("give");
            String it = ItemWords.resolve(t);
            if (it != null && !has(t, "ทั้งหมด", "everything", "all your", "all of it", "all items", "ของทั้งหมด")) {
                i.item = it;
                i.n = extractQty(t).n();
            }
            return i;
        }
        if (has(t, "หนังสือ", "book")) return new Intent("book");
        if (has(t, "อาหาร", "food", "ป้อน")) return new Intent("hungry");
        if (has(t, "สวัสดี", "หวัดดี", "ฮัลโหล", "ฮาโหล", "โย่ว", "เฮ้", "hello", "hi", "hey", "yo", "ดีจ้า", "ว่าไง", "good morning", "good evening", "sup", "what's up", "whats up"))
            return new Intent("hello");
        if (has(t, "ขอบคุณ", "ขอบใจ", "thank", "thanks", "thx", "ty", "เก่ง", "good job", "ดีมาก", "สุดยอด", "nice", "well done", "great job", "awesome"))
            return new Intent("thanks");
        if (test(BUILD_VERB, t) && has(t, "สร้าง", "build", "construct", "create")) {
            Intent i = new Intent("aibuild");
            i.text = raw.trim();
            return i;
        }
        if (!noFix) {
            String fx = fixTypos(t, ORDER_KEYS);
            if (!fx.equals(t)) {
                Intent r = parse(fx, true);
                if (!r.k.equals("unknown")) return r;
            }
        }
        Intent u = new Intent("unknown");
        u.text = raw.trim();
        return u;
    }

    // ================================================================== several orders in one sentence
    static final String STRONG = "\\s*(?:และหลังจากนั้น|หลังจากนั้นก็|หลังจากนั้น|จากนั้นก็|จากนั้น|ต่อจากนั้น|ต่อด้วย|เสร็จแล้วก็|เสร็จแล้ว|พอเสร็จแล้ว|พอเสร็จ|แล้วค่อย|แล้วก็|แล้ว|"
            + "\\band then\\b|\\bthen\\b|\\bafter that\\b|\\bafterwards\\b|\\bwhen you'?re done\\b|\\bonce done\\b|;|\\n|,(?!\\s*-?\\d)|ต่อไป)\\s*";
    static final String WEAK = "\\s*(?:\\band\\b|และ|กับก็|พร้อมกับ|\\balso\\b)\\s*";
    static final String VERB = "(ตัด|ขุด|ไป|มา|เก็บ|หา|สร้าง|รอ|ตาม|ฆ่า|ล่า|ทำ|เอา|ส่ง|กิน|ปิ้ง|ย่าง|เฝ้า|หยุด|ใส่|คราฟ|ถลุง|ปลูก|ปัก|chop|mine|dig|\\bgo\\b|come|\\bget\\b|collect|gather|build|make|wait|follow|kill|hunt|bring|give|\\beat\\b|cook|guard|stay|stop|put|store|craft|smelt|plant|farm|breed|shear|light)";

    public static List<String> splitChain(String raw) {
        List<String> out = new ArrayList<>();
        for (String part : re(STRONG).split(raw)) {
            String p = part.trim();
            if (p.isEmpty()) continue;
            String[] pieces = re(WEAK).split(p);
            List<String> ps = new ArrayList<>();
            for (String x : pieces) if (!x.trim().isEmpty()) ps.add(x.trim());
            if (ps.isEmpty()) continue;
            String cur = ps.get(0);
            for (int i = 1; i < ps.size(); i++) {
                String prevK = parse(cur).k;
                if (test(VERB, norm(ps.get(i))) && !prevK.equals("house") && !parse(ps.get(i)).k.equals("unknown")) {
                    out.add(cur);
                    cur = ps.get(i);
                } else cur += " " + ps.get(i);
            }
            out.add(cur);
        }
        List<String> merged = new ArrayList<>();
        for (String piece : out) {
            if (!merged.isEmpty() && parse(piece).k.equals("unknown")) merged.set(merged.size() - 1, merged.get(merged.size() - 1) + " " + piece);
            else merged.add(piece);
        }
        return merged;
    }

    /** True when the text looks like a question. */
    public static boolean isQuestion(String raw) {
        String n = norm(raw);
        return test("(ตรงไหน|ที่ไหน|ยังไง|อย่างไร|ทำไม|เท่าไหร่|เท่าไร|ได้อย่างไร|ไหม$|มั้ย$|หรือเปล่า|รึเปล่า|where|how |what |why|which|who|when|\\?)", n) || raw.trim().endsWith("?");
    }

    /** Thai text in the message? (used to answer in the same language). */
    public static boolean looksThai(String s) {
        for (char c : s.toCharArray()) if (c >= 0x0E00 && c <= 0x0E7F) return true;
        return false;
    }
}
