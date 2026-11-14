package com.helperbuddy.talk;

import com.helperbuddy.config.HBConfig;
import com.helperbuddy.entity.HelperEntity;

import java.util.Locale;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * How a helper talks. Every line the helper says goes through here: a polite helper says it as written, a sassy one
 * talks like a cheeky friend (ข้า / เอ็ง, teasing, calls you names), a rude one swears at you (กู / มึง).
 * Pure Java (the style is passed in), so it can be tested without a game.
 */
public final class Persona {
    private Persona() {}

    public enum Style {
        POLITE, SASSY, RUDE;

        public static Style of(String s) {
            if (s == null) return SASSY;
            return switch (s.trim().toLowerCase(Locale.ROOT)) {
                case "polite", "nice", "สุภาพ" -> POLITE;
                case "rude", "vulgar", "หยาบ" -> RUDE;
                default -> SASSY;
            };
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final Random R = new Random();

    public static Style of(HelperEntity h) {
        String own = h.persona();
        String s = own == null || own.isEmpty() ? HBConfig.STYLE.get() : own;
        if (s.trim().equalsIgnoreCase("auto")) return mirror(h.owner());
        return Style.of(s);
    }

    // ------------------------------------------------------------------ talking the way the player does
    /** per player: {polite, rough} (0-10), from the words they use with their helpers */
    static final java.util.Map<java.util.UUID, int[]> REGISTER = new java.util.HashMap<>();
    static final Pattern POLITE_WORDS = Pattern.compile("(ครับ|ค่ะ|คะ(?!แนน)|นะคะ|กรุณา|รบกวน|ขอบคุณ|ขอบใจ|ขอโทษ|\\bplease\\b|\\bpls\\b|\\bthanks?\\b|\\bthank you\\b|could you|would you|\\bsorry\\b)", Pattern.CASE_INSENSITIVE);
    static final Pattern ROUGH_WORDS = Pattern.compile("(กู(?![้่])|มึง|เหี้ย|สัส|สัด|แม่ง|ควาย|ไอ้|อีดอก|เชี่ย|โว้ย|\\bfuck|\\bshit|\\bdamn|\\bidiot|\\bstupid|\\bdumbass|\\bwtf\\b|\\bbitch)", Pattern.CASE_INSENSITIVE);

    /** {polite, rough} found in a sentence (pure, for tests). */
    public static int[] markers(String text) {
        return new int[] {POLITE_WORDS.matcher(text).find() ? 1 : 0, ROUGH_WORDS.matcher(text).find() ? 1 : 0};
    }

    /** The style for a player whose words scored {polite, rough}. */
    public static Style styleFor(int polite, int rough) {
        if (rough >= 4 && rough > polite) return Style.RUDE;
        if (polite >= 2 && polite >= rough) return Style.POLITE;
        return Style.SASSY;
    }

    static int[] register(net.minecraft.server.level.ServerPlayer p) {
        return REGISTER.computeIfAbsent(p.getUUID(), k -> Memory.on() ? new int[] {Memory.of(p).polite, Memory.of(p).rough} : new int[2]);
    }

    /** Every sentence a player says to a helper: how they talk is how the helper talks back (config talkStyle = auto). */
    public static void observe(net.minecraft.server.level.ServerPlayer p, String text) {
        int[] m = markers(text), r = register(p);
        if (m[0] == 0 && m[1] == 0) return;
        if (m[0] > 0) {
            r[0] = Math.min(10, r[0] + 2);
            r[1] = Math.max(0, r[1] - 1);
        }
        if (m[1] > 0) {
            r[1] = Math.min(10, r[1] + 2);
            r[0] = Math.max(0, r[0] - 1);
        }
        if (Memory.on()) {
            Memory.of(p).polite = r[0];
            Memory.of(p).rough = r[1];
            Memory.dirty = true;
        }
    }

    static Style mirror(@javax.annotation.Nullable net.minecraft.server.level.ServerPlayer owner) {
        if (owner == null) return Style.SASSY;
        int[] r = register(owner);
        return styleFor(r[0], r[1]);
    }

    public static String name(Style s, boolean th) {
        return switch (s) {
            case POLITE -> th ? "สุภาพ" : "polite";
            case SASSY -> th ? "กวนตีน" : "sassy";
            case RUDE -> th ? "หยาบคาย" : "rude";
        };
    }

    static String pick(Random r, String... a) {
        return a[r.nextInt(a.length)];
    }

    // ------------------------------------------------------------------ word lists
    static final String[] TH_SASSY_OPEN = {"เออๆ", "ก็ได้ๆ", "จ้าาา", "ได้เลยเจ้านาย", "อืม ก็ได้", "จัดไป"};
    static final String[] TH_RUDE_OPEN = {"เออ!", "เออๆ รู้แล้ว", "สั่งเก่งจังนะ", "เหนื่อยโว้ย", "ได้ๆ"};
    static final String[] TH_SASSY_TAIL = {"เจ้าทึ่ม", "เจ้านายขี้สั่ง", "เจ้าเด็กน้อย", "นะจ๊ะ"};
    static final String[] TH_RUDE_TAIL = {"ไอ้บ้า", "ไอ้ทึ่ม", "ไอ้ขี้เกียจ", "ไอ้ควาย"};
    static final String[] EN_SASSY_OPEN = {"Fiiine.", "Sure, boss.", "Yeah yeah.", "If I must.", "On it."};
    static final String[] EN_RUDE_OPEN = {"Ugh, fine.", "Yeah, yeah.", "What now?", "Fine, I'm going."};
    static final String[] EN_SASSY_TAIL = {"genius", "boss", "champ"};
    static final String[] EN_RUDE_TAIL = {"you muppet", "you walnut", "slowpoke"};

    /** Lines that are just "OK, doing it" (they get an opener). */
    static final Pattern TH_ACK = Pattern.compile("^(?:§.)*(ได้(?:เลย)?ครับ|ได้ครับ|โอเค(?:ครับ)?|ไปแล้วนะ|ไป(?:หา|เก็บ|ขุด|ตัด|ล่า|สร้าง)|เดี๋ยว|รับทราบ)");
    static final Pattern EN_ACK = Pattern.compile("^(?:§.)*(sure|ok(?:ay)?|on my way|off to|i'll|got it|will do|alright)", Pattern.CASE_INSENSITIVE);

    // ------------------------------------------------------------------ restyling
    /** A Thai line in the given style. */
    public static String th(Style st, String s, Random r) {
        return th(st, s, r, false);
    }

    /** plain: its own way of saying I / you, but no teasing on top (bad news, danger, long explanations). */
    public static String th(Style st, String s, Random r, boolean plain) {
        if (st == Style.POLITE || s == null || s.isEmpty()) return s;
        boolean rude = st == Style.RUDE;
        boolean ack = TH_ACK.matcher(s).find();
        String t = s;
        t = t.replace("ขอโทษครับ", rude ? "เออ โทษที" : "โทษทีเว้ย").replace("ขอบคุณครับ", rude ? "เออ ขอบใจ" : "ขอบใจนะเว้ย")
                .replace("สวัสดีครับ", rude ? "ไง" : "ไง").replace("ได้เลยครับ", rude ? "เออ" : "ได้ดิ").replace("ได้ครับ", rude ? "เออ" : "ได้ดิ");
        t = replaceEach(t, "นะครับ", () -> rude ? pick(r, "นะโว้ย", "วะ", "นะมึง") : pick(r, "นะเว้ย", "นะ", "เว่ย", "นะจ๊ะ"));
        t = replaceEach(t, "ครับผม", () -> rude ? pick(r, "โว้ย", "วะ") : pick(r, "เว้ย", "จ้า"));
        t = replaceEach(t, "ครับ", () -> rude ? pick(r, "วะ", "โว้ย", "", "ว่ะ") : pick(r, "เว้ย", "", "ว่ะ", "จ้า", ""));
        t = t.replace("ค่ะ", rude ? "วะ" : "จ้า");
        // I / you
        t = t.replaceAll("ผม(?!ยาว|สั้น|ร่วง|หงอก)", rude ? "กู" : "ข้า");
        t = t.replaceAll("คุณ(?!ภาพ|ค่า|สมบัติ|ธรรม|ลักษณะ|ประโยชน์|นาย|ป้า|ลุง)", rude ? "มึง" : "เอ็ง");
        t = t.replace("เรา", rude ? "กู" : "ข้า");
        t = t.replaceAll("\\s{2,}", " ").replaceAll("\\s+([.!?)])", "$1").trim();
        // a little teasing now and then, never on every line
        if (plain) return t;
        if (ack && r.nextFloat() < (rude ? 0.35f : 0.22f)) t = pick(r, rude ? TH_RUDE_OPEN : TH_SASSY_OPEN) + " " + t;
        if (r.nextFloat() < (rude ? 0.12f : 0.05f) && !t.contains("\n") && t.length() < 90) t = withTail(t, pick(r, rude ? TH_RUDE_TAIL : TH_SASSY_TAIL));
        return t;
    }

    /** An English line in the given style. */
    public static String en(Style st, String s, Random r) {
        return en(st, s, r, false);
    }

    public static String en(Style st, String s, Random r, boolean plain) {
        if (st == Style.POLITE || s == null || s.isEmpty()) return s;
        boolean rude = st == Style.RUDE;
        String t = s;
        boolean ack = EN_ACK.matcher(t).find();
        t = t.replaceFirst("^(§.)*Sorry,? ", "$1" + (rude ? "Whatever, " : "Oops, my bad. "));
        t = t.replaceFirst("^(§.)*Thanks!?", "$1" + (rude ? "Yeah, thanks or whatever." : "Thanks, I guess."));
        if (plain) return t;
        if (ack && r.nextFloat() < (rude ? 0.35f : 0.22f)) t = pick(r, rude ? EN_RUDE_OPEN : EN_SASSY_OPEN) + " " + t;
        if (r.nextFloat() < (rude ? 0.12f : 0.05f) && !t.contains("\n") && t.length() < 90) t = withTail(t, pick(r, rude ? EN_RUDE_TAIL : EN_SASSY_TAIL));
        return t;
    }

    static String withTail(String t, String tail) {
        String end = "";
        Matcher m = Pattern.compile("([.!?]+)$").matcher(t);
        if (m.find()) {
            end = m.group(1);
            t = t.substring(0, m.start());
        }
        boolean thai = Nlu.looksThai(tail);
        return t + (thai ? " " : ", ") + tail + (end.isEmpty() ? (thai ? "" : ".") : end);
    }

    static String replaceEach(String t, String word, java.util.function.Supplier<String> with) {
        StringBuilder b = new StringBuilder();
        int i = 0, j;
        while ((j = t.indexOf(word, i)) >= 0) {
            b.append(t, i, j).append(with.get());
            i = j + word.length();
        }
        return b.append(t.substring(i)).toString();
    }

    // ------------------------------------------------------------------ things a helper says on its own
    /** A line for an event (null: this style says nothing). [th, en] */
    public static String[] quip(Style st, String event, Random r) {
        boolean rude = st == Style.RUDE;
        if (st == Style.POLITE) {
            return switch (event) {
                case "ownerDied" -> new String[] {"ไม่เป็นไรนะครับ ลุกขึ้นมาใหม่ได้!", "Don't worry, you'll get them next time!"};
                case "hitByOwner" -> null;
                case "ghostRun" -> new String[] {"ผีมาครับ! ระวังตัวด้วย!", "A ghost! Be careful!"};
                default -> null;
            };
        }
        return switch (event) {
            case "ownerDied" -> rude
                    ? pair(r, new String[][] {{"555 ตายอีกแล้วเหรอมึง", "Ha, dead again?"}, {"ลุกมา กูเฝ้าของให้อยู่", "Get up, I'm watching your stuff"},
                    {"ไม่เป็นไร รอบหน้าเอาใหม่โว้ย", "Never mind, get them next time"}})
                    : pair(r, new String[][] {{"555 ตายอีกแล้วเหรอ", "Haha, dead again?"}, {"ไม่เป็นไรนะ เดี๋ยวข้าเฝ้าของให้", "It's okay, I'll watch your stuff"},
                    {"ข้าว่าแล้วว่าตรงนั้นอันตราย", "I knew that spot was dangerous"}, {"ลุกขึ้นมาใหม่ได้ สู้ๆ", "Up you get, you've got this"}});
            case "hitByOwner" -> rude
                    ? pair(r, new String[][] {{"ตีกูทำไมวะ!", "Hey, what was that for?!"}, {"เจ็บนะเว้ย", "That hurt, you know"}})
                    : pair(r, new String[][] {{"โอ๊ย! ตีข้าทำไม!", "Ow! What was that for?!"}, {"จิ้มเล่นอยู่ได้ เบื่อแล้วเหรอ", "Poking me again? Bored?"},
                    {"อย่าตีสิ ข้าฟ้องแม่นะ", "Stop it or I'm telling mom"}});
            case "ghostRun" -> rude
                    ? pair(r, new String[][] {{"ผีมาโว้ย! มึงสู้ไปคนเดียวเลย กูขุดหลุมหลบละ", "Ghost! You fight it, I'm digging in"},
                    {"ช่างหัวมึง กูไปก่อน บาย!", "Screw this, bye!"}})
                    : pair(r, new String[][] {{"ผีมา! ข้าไปก่อนนะ บายย!", "A ghost! I'm out, byeee!"}, {"เอ็งสู้ไปคนเดียวนะ ข้าขุดหลุมหลบละ", "You handle it, I'll be in my hole"},
                    {"ช่วยด้วย! เอ้ย ไม่ใช่ ช่วยตัวเองนะ ข้าหลบก่อน", "Help! I mean... help yourself, I'm hiding"}});
            case "ghostBack" -> rude
                    ? pair(r, new String[][] {{"ไปแล้วเหรอ? กูช่วยอยู่ห่างๆ นะ 555", "Gone? I was helping from down there, lol"}})
                    : pair(r, new String[][] {{"มันไปแล้วเหรอ? ข้าก็ช่วยอยู่ห่างๆ นะ 555", "Is it gone? I was helping... from a distance"},
                    {"เห็นไหม ข้าล่อมันไปอีกทางแล้ว (โกหก)", "See? I lured it away (I didn't)"}});
            case "idle" -> rude
                    ? pair(r, new String[][] {{"จะยืนเฉยๆ อีกนานไหมวะ", "Are we just gonna stand here?"}, {"เบื่อโว้ย หางานให้ทำหน่อย", "Bored as hell, give me something to do"}})
                    : pair(r, new String[][] {{"เบื่อว่ะ หางานให้ทำหน่อย", "Bored... give me a job"}, {"ยืนเฉยๆ แบบนี้ เงินเดือนยังได้เท่าเดิมไหม", "Do I still get paid for standing here?"},
                    {"ถ้าไม่สั่งอะไร ข้าขอนอนนะ", "If you've got nothing, I'm taking a nap"}});
            case "insulted" -> rude
                    ? pair(r, new String[][] {{"เออ ด่าได้ แต่งานกูก็ทำให้อยู่นะ", "Sure, yell at me. I'm still doing your work"},
                    {"ปากดีนักนะ เดี๋ยวกูทำให้ดูเอง", "Big mouth. Watch me do it then"},
                    {"กูโง่ แต่มึงจ้างกูนะ คิดดูดีๆ", "Sure I'm dumb, but you hired me. Think about that"}})
                    : pair(r, new String[][] {{"พูดงี้ข้าก็เสียใจนะ... ล้อเล่น ไม่เสียใจหรอก", "Ouch... just kidding, I don't care"},
                    {"ข้าโง่ แต่เอ็งตายบ่อยกว่าข้านะ", "I may be dumb, but you die way more than I do"}, {"ว่าข้าอีกที ข้าจะขุดหลุมหนีเหมือนตอนผีมาเลย", "Say that again and I'll dig a hole and hide, like with the ghosts"},
                    {"โอเคๆ ข้าจะตั้งใจกว่านี้ก็ได้", "Okay okay, I'll try harder"}});
            case "levelUp" -> rude
                    ? pair(r, new String[][] {{"เลเวลอัพแล้วโว้ย เก่งกว่ามึงอีก", "Level up! Already better than you"}})
                    : pair(r, new String[][] {{"เลเวลอัพ! ตอนนี้ข้าเก่งกว่าเอ็งแล้วมั้ง", "Level up! Pretty sure I'm better than you now"}});
            default -> null;
        };
    }

    static String[] pair(Random r, String[][] options) {
        return options[r.nextInt(options.length)];
    }
}
