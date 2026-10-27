import com.helperbuddy.talk.Nlu;

public class NluTest {
    public static void main(String[] a) {
        String[] tests = {
                "ไปขุดแร่", "ขุดเฉพาะเพชรกับเหล็ก", "ขุดเหล็ก 10 ก้อน", "ขุดทุกแร่", "ไปตัดไม้", "ตัดไม้ 30 ท่อน", "ตัดไม้ 5 ต้น", "ขุดทราย 2 กอง", "เก็บดิน 64",
                "เก็บไม้ไผ่ 64", "หาอาหาร", "ล่าวัว 3 ตัว", "เก็บเกี่ยวผัก", "ทำอาหาร", "สร้างเหมือง ลึก 40 ยาว 30", "สร้างเหมืองหาเหล็ก",
                "สร้างบ้านโมเดิร์นหลังใหญ่ 3 ชั้น มีสระ", "บ้านใต้ดิน 2 ชั้น", "ปราสาทหินใหญ่", "บ้านต้นไม้", "สร้างคุก", "สร้างโรงแรม", "สร้างรูปปั้นครีปเปอร์",
                "สร้างประตูเนเธอร์", "บังเกอร์ทหาร", "มานี่", "ตามมา", "รอตรงนั้น", "รอ 30 วินาที", "ไปที่ 100 64 -50", "กลับบ้าน", "เอาของมาให้", "เก็บเข้าหีบ",
                "ช่วยสู้", "ไม่ต้องสู้", "เฝ้าตรงนี้", "ช่วยด้วย!", "สถานะ", "เลเวล", "ทำต่อ", "ยกเลิก", "เปลี่ยนชื่อเป็น บัดดี้",
                "ตัดไม้ 20 ท่อน แล้วขุดหิน 64 ก้อน แล้วเอามาให้", "ขุดเพชรจนกระเป๋าเต็ม แล้วเก็บเข้าหีบ", "ทำโต๊ะคราฟ", "คราฟคบเพลิง 16 อัน", "ถลุงเหล็กดิบ",
                "ปลูกผัก", "ผสมพันธุ์วัว", "ตัดขนแกะ", "ปักคบเพลิงรอบๆ", "ขอดาบเพชร", "สร้างรถไฟ", "ขอบคุณมาก", "สวัสดีครับ", "ปราสาด", "ขุดแล่",
                "go mine some ores", "mine diamonds and iron", "chop 30 logs", "chop wood then bring it to me", "collect 2 stacks of sand", "get bamboo",
                "find food", "hunt 3 cows", "harvest crops", "cook the meat", "build a mine 40 deep 30 long", "build a big modern house with a pool, 3 floors",
                "build an underground bunker", "build a castle", "make a treehouse", "build a prison", "build a creeper statue", "build a nether portal",
                "come here", "follow me", "stay there", "wait 2 minutes", "go to 100 64 -50", "go home", "give me everything", "put everything in the chest",
                "help me fight", "stop fighting", "guard this place", "help me!", "status", "what level are you", "continue", "cancel that",
                "your name is Bob", "craft a crafting table", "make 16 torches", "smelt the raw iron", "plant some seeds", "breed the cows", "shear the sheep",
                "light up the area", "give me a diamond sword", "build a dragon", "thanks buddy", "hello", "castel", "fight back when attacked", "don't fight back",
                "take 10 iron from the chest", "flatten the area", "bring me 5 bread",
        };
        for (String t : tests) {
            Nlu.Intent i = Nlu.parse(t);
            var chain = Nlu.splitChain(t);
            System.out.println(String.format("%-55s -> %s%s", t, i, chain.size() > 1 ? "   CHAIN " + chain : ""));
        }
        System.out.println(Nlu.numify("ยี่สิบห้า สามร้อยสิบ twenty five"));
        System.out.println(Nlu.extractQty("ตัดไม้สองกอง").n() + " " + Nlu.extractQty("a stack of sand").n() + " " + Nlu.extractQty("until full").full());
    }
}
