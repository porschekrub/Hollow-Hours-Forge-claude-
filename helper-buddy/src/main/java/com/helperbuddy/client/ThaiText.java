package com.helperbuddy.client;

/** Thai text for the Thai voice: numbers in words ("120" -> "หนึ่งร้อยยี่สิบ"), as the voice reads only Thai letters. */
public final class ThaiText {
    private ThaiText() {}

    static final String[] DIGIT = {"ศูนย์", "หนึ่ง", "สอง", "สาม", "สี่", "ห้า", "หก", "เจ็ด", "แปด", "เก้า"};
    static final String[] PLACE = {"", "สิบ", "ร้อย", "พัน", "หมื่น", "แสน"};

    public static String number(long n) {
        if (n == 0) return DIGIT[0];
        if (n < 0) return "ลบ" + number(-n);
        if (n >= 1_000_000) {
            long hi = n / 1_000_000, lo = n % 1_000_000;
            return number(hi) + "ล้าน" + (lo == 0 ? "" : below(lo, true));
        }
        return below(n, false);
    }

    /** under a million; tail: after "ล้าน" (a 1 at the end is "เอ็ด" there too) */
    static String below(long n, boolean tail) {
        StringBuilder sb = new StringBuilder();
        String s = Long.toString(n);
        int len = s.length();
        for (int i = 0; i < len; i++) {
            int d = s.charAt(i) - '0', place = len - 1 - i;
            if (d == 0) continue;
            if (place == 1 && d == 1) sb.append("สิบ");
            else if (place == 1 && d == 2) sb.append("ยี่สิบ");
            else if (place == 0 && d == 1 && (len > 1 || tail)) sb.append("เอ็ด");
            else sb.append(DIGIT[d]).append(PLACE[place]);
        }
        return sb.toString();
    }

    /** Every number in the text in words (a minus right before a number too). */
    public static String numbers(String text) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?<![\\d.])-?\\d{1,12}(?:\\.\\d+)?").matcher(text.replace(",", ""));
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String g = m.group();
            String said;
            int dot = g.indexOf('.');
            if (dot >= 0) {
                StringBuilder f = new StringBuilder(number(Long.parseLong(g.substring(0, dot))) + "จุด");
                for (char c : g.substring(dot + 1).toCharArray()) f.append(DIGIT[c - '0']);
                said = f.toString();
            } else said = number(Long.parseLong(g));
            m.appendReplacement(out, " " + said + " ");
        }
        m.appendTail(out);
        return out.toString();
    }
}
