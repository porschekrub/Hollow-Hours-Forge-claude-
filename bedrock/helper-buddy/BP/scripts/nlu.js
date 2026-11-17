// Small language helpers (pure, no Minecraft imports): typo tolerance for Thai/English keywords.
/** Edit distance with an early exit once it is above `max`. */
export function lev(a, b, max = 2) {
    if (Math.abs(a.length - b.length) > max)
        return max + 1;
    let prev = Array.from({ length: b.length + 1 }, (_, i) => i);
    for (let i = 1; i <= a.length; i++) {
        const cur = [i];
        let rowMin = i;
        for (let j = 1; j <= b.length; j++) {
            const v = Math.min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (a[i - 1] === b[j - 1] ? 0 : 1));
            cur.push(v);
            if (v < rowMin)
                rowMin = v;
        }
        if (rowMin > max)
            return max + 1;
        prev = cur;
    }
    return prev[b.length];
}
/** Allowed number of typos for a keyword of this length. */
function tol(len) {
    return len >= 11 ? 2 : len >= 6 ? 1 : 0;
}
/** Finds the part of `text` that is a near match of `word` (typo tolerant). Returns [start, end] or undefined. */
export function fuzzyFind(text, word) {
    const i = text.indexOf(word);
    if (i >= 0)
        return [i, i + word.length];
    const m = tol(word.length);
    if (m === 0)
        return undefined;
    let best;
    let bd = m + 1;
    for (let len = word.length - 1; len <= word.length + 1; len++) {
        if (len < 3)
            continue;
        for (let s = 0; s + len <= text.length; s++) {
            const piece = text.slice(s, s + len);
            // the first character must agree: avoids silly matches
            if (piece[0] !== word[0])
                continue;
            const d = lev(piece, word, m);
            if (d < bd) {
                bd = d;
                best = [s, s + len];
            }
        }
    }
    return bd <= m ? best : undefined;
}
/** Replaces misspelled keywords in `text` by the correct keyword (only for words in `keys`). */
export function fixTypos(text, keys) {
    let t = text;
    for (const k of keys) {
        if (t.includes(k))
            continue;
        const r = fuzzyFind(t, k);
        if (r)
            t = t.slice(0, r[0]) + k + t.slice(r[1]);
    }
    return t;
}
// Keywords worth correcting (long enough to be safe)
export const ORDER_KEYS = [
    "ขุดแร่", "ตัดไม้", "ตัดต้นไม้", "สร้างบ้าน", "สร้างเหมือง", "หาอาหาร", "ทำอาหาร", "เก็บเข้าหีบ", "เอาของมา", "ยกเลิก", "สถานะ",
    "เลเวล", "กลับบ้าน", "ตามมา", "รอตรงนี้", "ช่วยด้วย", "ช่วยสู้", "ปราสาท", "ปิรามิด", "โรงแรม", "โรงเรียน", "โรงพยาบาล", "ห้องสมุด", "รูปปั้น", "ประตูเนเธอร์", "เรือนจำ", "โรงนา", "อิกลู", "หอคอย", "คฤหาสน์", "ประภาคาร",
    "ใต้ดิน", "บังเกอร์", "บ้านหรู", "หลบภัย", "ต้นไม้", "ทะเลทราย", "เนเธอร์", "แบล็กสโตน", "ดีพสเลท", "ควอตซ์", "เพชร", "เหล็ก",
    "ทองแดง", "เรดสโตน", "มรกต", "โมเดิร์น", "ยุคกลาง", "สระ", "ปล่องไฟ", "ระเบียง", "ชั้น", "ล่าสัตว์", "เก็บเกี่ยว",
    "castle", "pyramid", "igloo", "tower", "mansion", "lighthouse", "underground", "bunker", "treehouse", "shelter", "villa", "luxury",
    "diamond", "build", "house", "chop", "mine", "follow", "status", "cancel", "come", "stay",
];
