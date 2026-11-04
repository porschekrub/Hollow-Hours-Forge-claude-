package com.helperbuddy.client.screen;

import com.helperbuddy.brain.BuildJob;
import com.helperbuddy.entity.Settings;
import com.helperbuddy.gen.ExtraGen;
import com.helperbuddy.gen.Gen;
import com.helperbuddy.gen.HouseGen;
import com.helperbuddy.net.C2SAction;
import com.helperbuddy.net.C2STalk;
import com.helperbuddy.net.HBNet;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** The helper's menu: status, quick orders, jobs, the building designer, settings and a talk box. */
public class HelperScreen extends Screen {
    public static final String[] TABS = {"main", "jobs", "build", "settings", "talk"};
    CompoundTag d;
    String tab;
    int left, top;
    static final int W = 320, H = 222;
    EditBox talkBox, nameBox;
    // designer choices (index 0 = "auto / random")
    static int dType, dVariant, dSize, dFloors, dWood, dRoof, dMat, dSpeed = 0;
    static boolean dPool, dPorch = true, dChimney = true;
    static int gatherIdx, digDepth = 2, digLength = 1;

    public HelperScreen(CompoundTag data, String tab) {
        super(Component.literal(data.getString("name")));
        this.d = data;
        this.tab = tab;
    }

    boolean th() {
        return d.getBoolean("thai");
    }

    String t(String thai, String en) {
        return th() ? thai : en;
    }

    public CompoundTag data() {
        return d;
    }

    public String tab() {
        return tab;
    }

    public int helperId() {
        return d.getInt("id");
    }

    public UUID helperUuid() {
        return d.getUUID("uuid");
    }

    public void update(CompoundTag data) {
        this.d = data;
        rebuildWidgets();
    }

    void act(String action) {
        act(action, new CompoundTag());
    }

    void act(String action, CompoundTag args) {
        HBNet.toServer(new C2SAction(helperId(), helperUuid(), action, args));
    }

    Button btn(int x, int y, int w, String th, String en, Button.OnPress press) {
        Button b = Button.builder(Component.literal(t(th, en)), press).bounds(left + x, top + y, w, 20).build();
        addRenderableWidget(b);
        return b;
    }

    Button actBtn(int x, int y, int w, String th, String en, String action) {
        return btn(x, y, w, th, en, b -> act(action));
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        String[][] names = {{"หลัก", "Main"}, {"งาน", "Jobs"}, {"สร้าง", "Build"}, {"ตั้งค่า", "Settings"}, {"คุย", "Talk"}};
        int tw = W / TABS.length;
        for (int i = 0; i < TABS.length; i++) {
            final String tb = TABS[i];
            Button b = Button.builder(Component.literal((tb.equals(tab) ? "§e" : "") + t(names[i][0], names[i][1])), x -> {
                tab = tb;
                rebuildWidgets();
            }).bounds(left + i * tw, top - 22, tw - 2, 20).build();
            addRenderableWidget(b);
        }
        switch (tab) {
            case "jobs" -> initJobs();
            case "build" -> initBuild();
            case "settings" -> initSettings();
            case "talk" -> initTalk();
            default -> initMain();
        }
    }

    // ------------------------------------------------------------------ main
    void initMain() {
        int y = 78;
        actBtn(8, y, 98, "เดินตาม", "Follow me", "follow");
        actBtn(110, y, 98, "รอตรงนี้", "Stay here", "stay");
        actBtn(212, y, 100, "มานี่", "Come here", "come");
        y += 24;
        btn(8, y, 98, "เปิดกระเป๋า", "Open bag", b -> act("bag"));
        actBtn(110, y, 98, "ให้ของที่ถือ", "Give held item", "give_held");
        actBtn(212, y, 100, "รับของทั้งหมด", "Take all items", "take_all");
        y += 24;
        actBtn(8, y, 98, "ยกเลิกงาน", "Cancel job", "cancel");
        actBtn(110, y, 98, "ทำงานต่อ", "Resume job", "resume");
        actBtn(212, y, 100, "รายงานสถานะ", "Status report", "status");
        y += 24;
        actBtn(8, y, 98, "เปลี่ยนชุด", "Change look", "skin");
        nameBox = new EditBox(font, left + 110, top + y, 140, 20, Component.literal("name"));
        nameBox.setMaxLength(24);
        nameBox.setValue(d.getString("name"));
        addRenderableWidget(nameBox);
        btn(254, y, 58, "ตั้งชื่อ", "Rename", b -> {
            CompoundTag a = new CompoundTag();
            a.putString("name", nameBox.getValue());
            act("rename", a);
        });
        y += 24;
        btn(8, y, 304, "ปิด", "Close", b -> onClose());
    }

    // ------------------------------------------------------------------ jobs
    static final String[][] GATHER = {{"ทราย", "sand"}, {"ไม้ไผ่", "bamboo"}, {"อ้อย", "sugar cane"}, {"ดิน", "dirt"}, {"หิน", "stone"}, {"กรวด", "gravel"},
            {"ดินเหนียว", "clay"}, {"กระบองเพชร", "cactus"}, {"ฟักทอง", "pumpkins"}, {"แตงโม", "melons"}, {"น้ำแข็ง", "ice"}, {"หิมะ", "snow"}, {"ใบไม้", "leaves"},
            {"ดอกไม้", "flowers"}, {"เห็ด", "mushrooms"}, {"ออบซิเดียน", "obsidian"}, {"เนเธอร์แร็ค", "netherrack"}};
    static final int[] DEPTHS = {0, 10, 20, 40, 60};
    static final int[] LENGTHS = {16, 32, 48, 64};

    void initJobs() {
        int y = 8;
        actBtn(8, y, 74, "ขุดแร่", "Mine ores", "mine");
        actBtn(84, y, 74, "ตัดไม้", "Chop wood", "chop");
        actBtn(160, y, 74, "หาอาหาร", "Find food", "food");
        actBtn(236, y, 76, "ทำฟาร์ม", "Farm", "farm");
        y += 24;
        actBtn(8, y, 74, "เฝ้าตรงนี้", "Guard here", "guard");
        actBtn(84, y, 74, "ปักคบเพลิง", "Torches", "torch");
        actBtn(160, y, 74, "ปรับพื้นที่", "Flatten", "flatten");
        actBtn(236, y, 76, "ตัดขนแกะ", "Shear sheep", "shear");
        y += 24;
        actBtn(8, y, 74, "ผสมพันธุ์", "Breed", "breed");
        actBtn(84, y, 74, "ถลุงแร่", "Smelt ores", "smelt");
        actBtn(160, y, 74, "ทำอาหาร", "Cook", "cook");
        actBtn(236, y, 76, "กิน", "Eat", "eat");
        y += 24;
        actBtn(8, y, 150, "เอาของมาให้", "Bring me the items", "deliver");
        actBtn(162, y, 150, "เก็บเข้าหีบใกล้ๆ", "Put in a nearby chest", "deposit");
        y += 28;
        btn(8, y, 150, "เก็บ: " + GATHER[gatherIdx][0], "Collect: " + GATHER[gatherIdx][1], b -> {
            gatherIdx = (gatherIdx + 1) % GATHER.length;
            rebuildWidgets();
        });
        btn(162, y, 150, "ไปเก็บ!", "Go collect!", b -> {
            CompoundTag a = new CompoundTag();
            a.putString("what", GATHER[gatherIdx][1]);
            act("gather", a);
        });
        y += 24;
        String dep = DEPTHS[digDepth] == 0 ? t("ระดับเดิม", "flat") : t("ลึก " + DEPTHS[digDepth], DEPTHS[digDepth] + " deep");
        btn(8, y, 74, dep, dep, b -> {
            digDepth = (digDepth + 1) % DEPTHS.length;
            rebuildWidgets();
        });
        btn(84, y, 74, "ยาว " + LENGTHS[digLength], LENGTHS[digLength] + " long", b -> {
            digLength = (digLength + 1) % LENGTHS.length;
            rebuildWidgets();
        });
        btn(160, y, 152, "สร้างเหมือง!", "Build a mine!", b -> {
            CompoundTag a = new CompoundTag();
            if (DEPTHS[digDepth] == 0) a.putBoolean("flat", true);
            else a.putInt("depth", DEPTHS[digDepth]);
            a.putInt("length", LENGTHS[digLength]);
            act("dig", a);
        });
    }

    // ------------------------------------------------------------------ building designer
    String[] types() {
        String[] out = new String[ExtraGen.TYPES.length + 1];
        out[0] = t("สุ่ม", "random");
        for (int i = 0; i < ExtraGen.TYPES.length; i++) out[i + 1] = th() ? ExtraGen.TYPES[i][1] : ExtraGen.TYPES[i][2];
        return out;
    }

    String typeId() {
        return dType == 0 ? null : ExtraGen.TYPES[dType - 1][0];
    }

    Button cycle(int x, int y, int w, String label, String[] vals, int idx, java.util.function.IntConsumer set) {
        String v = vals[Math.floorMod(idx, vals.length)];
        return btn(x, y, w, label + ": " + v, label + ": " + v, b -> {
            set.accept((idx + 1) % vals.length);
            rebuildWidgets();
        });
    }

    void initBuild() {
        if (d.contains("build")) {
            CompoundTag b = d.getCompound("build");
            int y = 120;
            if (!b.getBoolean("free")) {
                actBtn(8, y, 150, "ส่งบล็อกที่ขาดให้", "Give the missing blocks", "build_take");
                actBtn(162, y, 150, "หยุดสร้าง", "Stop building", "build_cancel");
            } else actBtn(8, y, 304, "หยุดสร้าง", "Stop building", "build_cancel");
            return;
        }
        int y = 8;
        String[] ty = types();
        cycle(8, y, 304, t("ประเภท", "Type"), ty, dType, v -> {
            dType = v;
            dVariant = 0;
        });
        y += 24;
        String tid = typeId();
        int vc = tid == null ? 1 : ExtraGen.variantCount(tid);
        String[] variants = new String[vc + 1];
        variants[0] = t("สุ่ม", "random");
        for (int i = 0; i < vc; i++) {
            Gen.Opts o = new Gen.Opts();
            o.type = tid;
            o.variant = i;
            o.model = i;
            String n = tid == null ? null : ExtraGen.variantName(o, th());
            variants[i + 1] = n == null ? t("แบบ ", "style ") + (i + 1) : n;
        }
        if (tid == null || tid.equals("standard")) {
            String[] styles = new String[HouseGen.STYLES.length + 1];
            styles[0] = t("สุ่ม", "random");
            for (int i = 0; i < HouseGen.STYLES.length; i++) styles[i + 1] = HouseGen.STYLES[i][th() ? 0 : 1];
            cycle(8, y, 304, t("สไตล์", "Style"), styles, dVariant, v -> dVariant = v);
        } else cycle(8, y, 304, t("แบบ", "Variant"), variants, dVariant, v -> dVariant = v);
        y += 24;
        String[] sizes = {t("สุ่ม", "random"), t("เล็ก", "small"), t("กลาง", "medium"), t("ใหญ่", "large")};
        cycle(8, y, 150, t("ขนาด", "Size"), sizes, dSize, v -> dSize = v);
        String[] floors = {t("อัตโนมัติ", "auto"), "1", "2", "3", "4", "5"};
        cycle(162, y, 150, t("ชั้น", "Floors"), floors, dFloors, v -> dFloors = v);
        y += 24;
        String[] woods = new String[HouseGen.WOODS.length + 1];
        woods[0] = t("สุ่ม", "random");
        for (int i = 0; i < HouseGen.WOODS.length; i++) woods[i + 1] = HouseGen.WOODS[i][th() ? 1 : 2];
        cycle(8, y, 150, t("ไม้", "Wood"), woods, dWood, v -> dWood = v);
        String[] mats = new String[ExtraGen.MATS.length + 1];
        mats[0] = t("สุ่ม", "random");
        for (int i = 0; i < ExtraGen.MATS.length; i++) mats[i + 1] = ExtraGen.MATS[i][th() ? 1 : 2];
        if (tid == null || tid.equals("standard")) {
            String[] roofs = new String[HouseGen.ROOFS.length + 1];
            roofs[0] = t("สุ่ม", "random");
            for (int i = 0; i < HouseGen.ROOFS.length; i++) roofs[i + 1] = HouseGen.ROOFS[i][th() ? 0 : 1];
            cycle(162, y, 150, t("หลังคา", "Roof"), roofs, dRoof, v -> dRoof = v);
        } else cycle(162, y, 150, t("วัสดุ", "Material"), mats, dMat, v -> dMat = v);
        y += 24;
        btn(8, y, 98, (dPool ? "§a" : "§7") + "สระว่ายน้ำ", (dPool ? "§a" : "§7") + "Pool", b -> {
            dPool = !dPool;
            rebuildWidgets();
        });
        btn(110, y, 98, (dPorch ? "§a" : "§7") + "ระเบียง", (dPorch ? "§a" : "§7") + "Porch", b -> {
            dPorch = !dPorch;
            rebuildWidgets();
        });
        btn(212, y, 100, (dChimney ? "§a" : "§7") + "ปล่องไฟ", (dChimney ? "§a" : "§7") + "Chimney", b -> {
            dChimney = !dChimney;
            rebuildWidgets();
        });
        y += 24;
        String[] speeds = {t("ปกติ", "normal"), t("เร็ว", "fast"), t("เร็วมาก", "very fast"), t("ทันใจ", "instant")};
        cycle(8, y, 150, t("ความเร็ว", "Speed"), speeds, dSpeed, v -> dSpeed = v);
        btn(162, y, 150, "§aสร้างตรงหน้าฉัน!", "§aBuild in front of me!", b -> {
            Gen.Opts o = new Gen.Opts();
            o.type = typeId();
            if (o.type == null) {
                String[] pool = {"standard", "standard", "standard", "hotel", "castle", "tower", "treehouse", "mansion", "igloo", "desert", "barn", "church", "school", "library", "market", "box", "lighthouse"};
                o.type = pool[new java.util.Random().nextInt(pool.length)];
            }
            if (dVariant > 0) {
                if (o.type.equals("standard")) o.style = dVariant - 1;
                else {
                    o.variant = dVariant - 1;
                    o.model = dVariant - 1;
                }
            }
            if (dSize > 0) o.size = dSize - 1;
            if (dFloors > 0) o.floors = dFloors;
            if (dWood > 0) o.wood = dWood - 1;
            if (dRoof > 0 && o.type.equals("standard")) o.roof = dRoof - 1;
            if (dMat > 0 && !o.type.equals("standard")) o.mat = dMat - 1;
            o.pool = dPool;
            o.porch = dPorch;
            o.chimney = dChimney;
            o.light = true;
            o.furniture = true;
            o.speed = dSpeed;
            CompoundTag a = new CompoundTag();
            CompoundTag ot = BuildJob.saveOpts(o);
            ot.putInt("speed", dSpeed);
            a.put("opts", ot);
            act("build", a);
            onClose();
        });
    }

    // ------------------------------------------------------------------ settings
    static final String[][] FLAG_NAMES = {
            {"fightBack", "สู้กลับเมื่อโดนตี", "Fight back when hit"}, {"fight", "ช่วยสู้กับมอนสเตอร์", "Fight monsters"},
            {"dig", "ขุดทะลุดิน/หิน", "Dig through ground"}, {"replant", "ปลูกต้นไม้/พืชคืน", "Replant"},
            {"pickup", "เก็บของที่ตก", "Pick up drops"}, {"chest", "เก็บของเข้าหีบเมื่อเต็ม", "Use chests when full"},
            {"needBlocks", "สร้างด้วยบล็อกจริง", "Build with real blocks"}, {"chestBuild", "เอาบล็อกจากหีบ", "Take blocks from chests"},
            {"keepJunk", "เก็บหิน/ดินทั้งหมด", "Keep all rubble"}, {"selfCare", "ดูแลตัวเอง (กิน/ทำอาหาร)", "Look after itself"},
            {"torches", "ปักคบเพลิงในเหมือง", "Torches in mines"}, {"teleport", "วาร์ปตามเมื่อไกล", "Teleport when far"}, {"hitPlayers", "ตีผู้เล่นคนอื่นได้", "May hit other players"}};
    static final String[][] ORE_NAMES = {{"coal", "ถ่านหิน", "Coal"}, {"copper", "ทองแดง", "Copper"}, {"iron", "เหล็ก", "Iron"}, {"gold", "ทอง", "Gold"},
            {"redstone", "เรดสโตน", "Redstone"}, {"lapis", "ลาพิส", "Lapis"}, {"diamond", "เพชร", "Diamond"}, {"emerald", "มรกต", "Emerald"},
            {"nether", "แร่เนเธอร์", "Nether ores"}, {"debris", "เศษโบราณ", "Ancient debris"}};

    void initSettings() {
        CompoundTag f = d.getCompound("flags"), o = d.getCompound("ores");
        int y = 6;
        for (int i = 0; i < FLAG_NAMES.length; i++) {
            String[] n = FLAG_NAMES[i];
            boolean on = f.getBoolean(n[0]);
            int x = 8 + (i % 2) * 152;
            final String key = n[0];
            btn(x, y, 150, (on ? "§a✔ " : "§7✘ ") + n[1], (on ? "§a✔ " : "§7✘ ") + n[2], b -> {
                CompoundTag a = new CompoundTag();
                a.putString("k", key);
                act("toggle", a);
            });
            if (i % 2 == 1) y += 22;
        }
        if (FLAG_NAMES.length % 2 == 1) y += 22;
        y += 6;
        for (int i = 0; i < ORE_NAMES.length; i++) {
            String[] n = ORE_NAMES[i];
            boolean on = o.getBoolean(n[0]);
            int x = 8 + (i % 5) * 61;
            final String key = n[0];
            btn(x, y, 59, (on ? "§a" : "§7") + n[1], (on ? "§a" : "§7") + n[2], b -> {
                CompoundTag a = new CompoundTag();
                a.putString("k", key);
                act("ore", a);
            });
            if (i % 5 == 4) y += 22;
        }
    }

    // ------------------------------------------------------------------ talk
    void initTalk() {
        talkBox = new EditBox(font, left + 8, top + 160, 250, 20, Component.literal("talk"));
        talkBox.setMaxLength(300);
        addRenderableWidget(talkBox);
        setInitialFocus(talkBox);
        btn(262, 160, 50, "ส่ง", "Send", b -> sendTalk());
        String[][] ex = {{"ตัดไม้ 20 ท่อน แล้วเอามาให้", "chop 20 logs then bring them to me"}, {"สร้างโรงแรม 3 ชั้น", "build a hotel with 3 floors"},
                {"ขุดเพชรจนกระเป๋าเต็ม", "mine diamonds until your bag is full"}, {"คราฟต์คบเพลิง 16 อัน", "craft 16 torches"}};
        int y = 40;
        for (String[] e : ex) {
            final String s = th() ? e[0] : e[1];
            btn(8, y, 304, "§7\"" + s + "\"", "§7\"" + s + "\"", b -> {
                talkBox.setValue(s);
                setFocused(talkBox);
            });
            y += 22;
        }
    }

    void sendTalk() {
        if (talkBox == null || talkBox.getValue().isBlank()) return;
        HBNet.toServer(new C2STalk(helperId(), helperUuid(), talkBox.getValue()));
        talkBox.setValue("");
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (tab.equals("talk") && talkBox != null && talkBox.isFocused() && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)) {
            sendTalk();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    // ------------------------------------------------------------------ drawing
    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.fill(left - 4, top - 26, left + W + 4, top + H + 4, 0xE0101418);
        g.fill(left - 4, top - 26, left + W + 4, top - 25, 0xFF3A7BD5);
        super.render(g, mx, my, pt);
        switch (tab) {
            case "main" -> drawMain(g);
            case "build" -> drawBuild(g);
            case "talk" -> drawTalk(g);
            case "settings" -> {
            }
            default -> {
            }
        }
    }

    void drawMain(GuiGraphics g) {
        int x = left + 8, y = top + 6;
        g.drawString(font, "§e§l" + d.getString("name") + " §r§b" + t("เลเวล ", "Level ") + d.getInt("level"), x, y, 0xFFFFFF);
        y += 12;
        float hp = d.getFloat("hp");
        int max = Math.max(1, d.getInt("max"));
        int bw = 140;
        g.fill(x, y, x + bw, y + 6, 0xFF400000);
        g.fill(x, y, x + (int) (bw * Math.min(1, hp / max)), y + 6, hp < max * 0.35 ? 0xFFE03030 : 0xFF40D040);
        g.drawString(font, (int) Math.ceil(hp) + "/" + max + (d.getBoolean("ko") ? t(" §cสลบ!", " §cknocked out!") : ""), x + bw + 6, y - 1, 0xFFFFFF);
        y += 12;
        g.drawString(font, "§f" + d.getString("modeLabel") + (d.getString("status").isEmpty() ? "" : " §7• " + d.getString("status")), x, y, 0xFFFFFF);
        y += 12;
        ItemStack tool = ItemStack.of(d.getCompound("tool"));
        g.drawString(font, "§7" + t("ถือ: ", "Holding: ") + (tool.isEmpty() ? t("ไม่มี", "nothing") : "§b" + tool.getHoverName().getString())
                + "   §7" + t("กระเป๋า ", "Bag ") + d.getInt("bagUsed") + "/" + d.getInt("bagSize"), x, y, 0xFFFFFF);
        y += 12;
        int dist = d.getInt("dist");
        g.drawString(font, "§8" + d.getInt("x") + ", " + d.getInt("y") + ", " + d.getInt("z") + " (" + d.getString("dim") + ")"
                + (dist >= 0 ? t(" ห่าง " + dist + " บล็อก", " " + dist + " blocks away") : ""), x, y, 0xFFFFFF);
        if (!tool.isEmpty()) g.renderItem(tool, left + W - 26, top + 6);
        if (d.contains("plan")) {
            List<String> lines = new ArrayList<>(List.of(d.getString("plan").split("\n")));
            int py = top + 176 + 24;
            for (int i = 0; i < Math.min(2, lines.size()); i++) g.drawString(font, lines.get(i), left + 8, py + i * 10, 0xFFFFFF);
        }
    }

    void drawBuild(GuiGraphics g) {
        if (!d.contains("build")) {
            g.drawString(font, t("§7จะสร้างตรงหน้าคุณ หันไปทางที่อยากให้ประตูหันมา แล้วกดสร้าง", "§7Builds in front of you, door facing you. Face the spot and press build"), left + 8, top + 176, 0xFFFFFF);
            g.drawString(font, t("§7หรือพิมพ์ในแชต เช่น §f!สร้างบ้านโมเดิร์นใหญ่ 3 ชั้นมีสระ", "§7Or type in chat, e.g. §f!build a big modern house with a pool"), left + 8, top + 188, 0xFFFFFF);
            if (d.getBoolean("aiBuild"))
                g.drawString(font, t("§bAI เปิดอยู่: §7สั่ง \"สร้าง...\" อะไรก็ได้ เช่น §f!สร้างรถสีแดง", "§bAI is on: §7ask for anything, e.g. §f!build a red car"), left + 8, top + 200, 0xFFFFFF);
            return;
        }
        CompoundTag b = d.getCompound("build");
        int x = left + 8, y = top + 10;
        g.drawString(font, "§d§l" + b.getString("name"), x, y, 0xFFFFFF);
        y += 14;
        int p = b.getInt("progress");
        g.fill(x, y, x + 300, y + 8, 0xFF202020);
        g.fill(x, y, x + 3 * p, y + 8, 0xFF40A0FF);
        g.drawString(font, p + "%", x + 304 - font.width(p + "%"), y + 10, 0xFFFFFF);
        y += 22;
        if (b.getBoolean("free")) g.drawString(font, t("§aสร้างฟรี (ไม่ใช้บล็อกจริง)", "§aFree building (no real blocks used)"), x, y, 0xFFFFFF);
        else {
            g.drawString(font, t("§eใช้บล็อกจริง ยังขาด " + b.getInt("missing") + " ชิ้น:", "§eUses real blocks, " + b.getInt("missing") + " missing:"), x, y, 0xFFFFFF);
            int yy = y + 12;
            for (String line : b.getString("missingText").split("\n")) {
                if (line.isBlank()) continue;
                g.drawString(font, line, x + 6, yy, 0xFFFFFF);
                yy += 10;
                if (yy > top + 112) break;
            }
        }
    }

    void drawTalk(GuiGraphics g) {
        g.drawString(font, t("§fพิมพ์คุยหรือสั่งงานเป็นประโยคธรรมดา (ไทยหรืออังกฤษ)", "§fTalk or give orders in plain sentences (Thai or English)"), left + 8, top + 8, 0xFFFFFF);
        g.drawString(font, t("§7สั่งหลายอย่างต่อกันได้ด้วย \"แล้ว\"  • ตัวอย่าง:", "§7Chain orders with \"then\"  • Examples:"), left + 8, top + 22, 0xFFFFFF);
        if (d.getBoolean("ai")) g.drawString(font, t("§bAI เปิดอยู่: ผมเข้าใจได้แทบทุกประโยค", "§bAI is on: I understand almost anything"), left + 8, top + 184, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
