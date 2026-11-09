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
        return Style.of(own == null || own.isEmpty() ? HBConfig.STYLE.get() : own);
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
    static final String[] TH_SASSY_OPEN = {"เออๆ", "ก็ได้ๆ", "เฮ้อ...", "สั่งจังนะ", "ขี้เกียจชะมัด แต่ก็ได้", "จ้าาา", "ได้เลยท่านเจ้านาย (ประชด)", "อืม ก็ได้"};
    static final String[] TH_RUDE_OPEN = {"เออ!", "ทำเองไม่เป็นเหรอวะ", "สั่งเก่งจังนะมึง", "เหนื่อยโว้ย", "จะเอาอะไรอีกวะ", "เออๆ ไอ้ขี้เกียจ", "หนวกหูว่ะ"};
    static final String[] TH_SASSY_TAIL = {"ไอ้โง่", "เจ้าทึ่ม", "ไอ้บ้า", "ไอ้ขี้เกียจ", "เจ้าหัวขี้เลื่อย", "ไอ้เต่า", "เจ้าเด็กน้อย", "ไอ้หน้าจืด"};
    static final String[] TH_RUDE_TAIL = {"ไอ้ควาย", "ไอ้เวร", "ไอ้สัส", "ไอ้เหี้ย", "ไอ้ควายเอ๊ย", "ไอ้ห่า", "ไอ้บ้าเอ๊ย"};
    static final String[] EN_SASSY_OPEN = {"Ugh, fine.", "Fiiine.", "Oh great, more work.", "Sure, boss... whatever.", "Yeah yeah.", "If I must."};
    static final String[] EN_RUDE_OPEN = {"Fine, dumbass.", "Do it yourself next time.", "Ugh, you again.", "What now, idiot?", "Shut up, I'm going."};
    static final String[] EN_SASSY_TAIL = {"genius", "slowpoke", "noob", "champ", "Einstein"};
    static final String[] EN_RUDE_TAIL = {"dumbass", "idiot", "you muppet", "moron", "you absolute walnut"};

    /** Lines that are just "OK, doing it" (they get an opener). */
    static final Pattern TH_ACK = Pattern.compile("^(?:§.)*(ได้(?:เลย)?ครับ|ได้ครับ|โอเค(?:ครับ)?|ไปแล้วนะ|ไป(?:หา|เก็บ|ขุด|ตัด|ล่า|สร้าง)|เดี๋ยว|รับทราบ)");
    static final Pattern EN_ACK = Pattern.compile("^(?:§.)*(sure|ok(?:ay)?|on my way|off to|i'll|got it|will do|alright)", Pattern.CASE_INSENSITIVE);

    // ------------------------------------------------------------------ restyling
    /** A Thai line in the given style. */
    public static String th(Style st, String s, Random r) {
        if (st == Style.POLITE || s == null || s.isEmpty()) return s;
        boolean rude = st == Style.RUDE;
        boolean ack = TH_ACK.matcher(s).find();
        String t = s;
        t = t.replace("ขอโทษครับ", rude ? "เออ โทษที" : "โทษทีเว้ย").replace("ขอบคุณครับ", rude ? "เออ ขอบใจ" : "ขอบใจนะเว้ย")
                .replace("สวัสดีครับ", rude ? "ไง ไอ้หน้าโง่" : "ไง").replace("ได้เลยครับ", rude ? "เออ" : "ได้ดิ").replace("ได้ครับ", rude ? "เออ" : "ได้ดิ");
        t = replaceEach(t, "นะครับ", () -> rude ? pick(r, "นะโว้ย", "วะ", "นะมึง") : pick(r, "นะเว้ย", "นะ", "เว่ย", "นะจ๊ะ"));
        t = replaceEach(t, "ครับผม", () -> rude ? pick(r, "โว้ย", "วะ") : pick(r, "เว้ย", "จ้า"));
        t = replaceEach(t, "ครับ", () -> rude ? pick(r, "วะ", "โว้ย", "", "ว่ะ") : pick(r, "เว้ย", "", "ว่ะ", "จ้า", ""));
        t = t.replace("ค่ะ", rude ? "วะ" : "จ้า");
        // I / you
        t = t.replaceAll("ผม(?!ยาว|สั้น|ร่วง|หงอก)", rude ? "กู" : "ข้า");
        t = t.replaceAll("คุณ(?!ภาพ|ค่า|สมบัติ|ธรรม|ลักษณะ|ประโยชน์|นาย|ป้า|ลุง)", rude ? "มึง" : "เอ็ง");
        t = t.replace("เรา", rude ? "กู" : "ข้า");
        t = t.replaceAll("\\s{2,}", " ").replaceAll("\\s+([.!?)])", "$1").trim();
        if (ack && r.nextFloat() < (rude ? 0.7f : 0.5f)) t = pick(r, rude ? TH_RUDE_OPEN : TH_SASSY_OPEN) + " " + t;
        else if (r.nextFloat() < (rude ? 0.15f : 0.08f)) t = pick(r, rude ? TH_RUDE_OPEN : TH_SASSY_OPEN) + " " + t;
        if (r.nextFloat() < (rude ? 0.4f : 0.22f) && !t.contains("\n") && t.length() < 160) t = withTail(t, pick(r, rude ? TH_RUDE_TAIL : TH_SASSY_TAIL));
        return t;
    }

    /** An English line in the given style. */
    public static String en(Style st, String s, Random r) {
        if (st == Style.POLITE || s == null || s.isEmpty()) return s;
        boolean rude = st == Style.RUDE;
        String t = s;
        boolean ack = EN_ACK.matcher(t).find();
        t = t.replaceFirst("^(§.)*Sorry,? ", "$1" + (rude ? "Whatever, " : "Oops, my bad. "));
        t = t.replaceFirst("^(§.)*Thanks!?", "$1" + (rude ? "Yeah, thanks or whatever." : "Thanks, I guess."));
        if (ack && r.nextFloat() < (rude ? 0.7f : 0.5f)) t = pick(r, rude ? EN_RUDE_OPEN : EN_SASSY_OPEN) + " " + t;
        if (r.nextFloat() < (rude ? 0.4f : 0.22f) && !t.contains("\n") && t.length() < 160) t = withTail(t, pick(r, rude ? EN_RUDE_TAIL : EN_SASSY_TAIL));
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
                    ? pair(r, new String[][] {{"555+ ตายห่าอีกแล้วมึง", "LMAO you died again, dumbass"}, {"ตายเก่งจังนะมึง ไอ้ควาย", "Dying is your only talent, huh"},
                    {"เล่นเกมเป็นปะเนี่ย ไอ้เวร", "Do you even know how to play?"}})
                    : pair(r, new String[][] {{"555 ตายอีกแล้วเหรอ", "Haha, dead again?"}, {"เก่งมาก ตายเก่งที่สุดในโลกเลย", "Wow, world champion of dying"},
                    {"ข้าว่าแล้วว่าเอ็งต้องตาย", "Called it. You were going to die"}, {"เกิดใหม่แล้วอย่าโง่ซ้ำนะ", "Respawn and try being less silly"}});
            case "hitByOwner" -> rude
                    ? pair(r, new String[][] {{"ตีกูทำไมวะ!", "Why the hell did you hit me?!"}, {"อีกทีกูไม่ทำงานให้นะเว้ย", "Do that again and I quit"}})
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
