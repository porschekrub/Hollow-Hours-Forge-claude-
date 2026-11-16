package com.helperbuddy.client.screen;

import com.helperbuddy.compat.Compat;
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
import com.helperbuddy.entity.HelperEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** The helper's menu: status, quick orders, jobs, the building designer, settings and a talk box. */
public class HelperScreen extends Screen {
    public static final String[] TABS = {"main", "jobs", "build", "settings", "talk", "ai", "voice"};
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
        String[][] names = {{"หลัก", "Main"}, {"งาน", "Jobs"}, {"สร้าง", "Build"}, {"ตั้งค่า", "Settings"}, {"คุย", "Talk"}, {"AI", "AI"}, {"เสียง", "Voice"}};
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
            case "ai" -> initAi();
            case "voice" -> initVoice();
            case "ollama" -> initOllama();
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
        int skin = Math.floorMod(d.getInt("skin"), HelperEntity.OUTFIT_NAMES.length);
        actBtn(8, y, 98, "ชุด: " + HelperEntity.OUTFITS[skin], "Look: " + HelperEntity.OUTFITS[skin], "skin");
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
        int sp = Math.max(0, Math.min(3, d.getInt("speed")));
        actBtn(8, y, 150, "ความเร็ว: " + HelperEntity.SPEED_NAMES[sp][0], "Work speed: " + HelperEntity.SPEED_NAMES[sp][1], "speed");
        String talk = d.getString("talk");
        actBtn(162, y, 150, "การพูด: " + talk, "Talks: " + (talk.equals("auto") ? "like you do" : talk), "talkstyle");
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
            {"torches", "ปักคบเพลิงในเหมือง", "Torches in mines"}, {"teleport", "วาร์ปตามเมื่อไกล", "Teleport when far"}, {"hitPlayers", "ตีผู้เล่นคนอื่นได้", "May hit other players"}, {"chatty", "พูดเล่น / คุยเอง", "Small talk"}};
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

    // ------------------------------------------------------------------ AI: which one, its key, the language and the way of talking
    static final String[][] PROVIDER_NAMES = {{"gemini", "Gemini (Google)"}, {"openai", "OpenAI (ChatGPT)"}, {"anthropic", "Claude (Anthropic)"}, {"openrouter", "OpenRouter"},
            {"groq", "Groq"}, {"ollama", "Ollama (on this PC)"}, {"custom", "Custom URL"}};
    /** what is typed on the AI page (kept while the page is rebuilt; sent with "Save") */
    String fProvider, fModel, fKey = "", fUrl, fStyle;
    boolean fOn, showKey;
    EditBox modelBox, keyBox, urlBox, styleBox;

    static String providerName(String id) {
        for (String[] p : PROVIDER_NAMES) if (p[0].equals(id)) return p[1];
        return id;
    }

    boolean needsUrl() {
        return "custom".equals(fProvider);
    }

    EditBox box(int x, int y, int w, String value, int max, String hint, java.util.function.Consumer<String> to, boolean editable) {
        EditBox b = new EditBox(font, left + x, top + y, w, 20, Component.literal(hint));
        b.setMaxLength(max);
        b.setValue(value == null ? "" : value);
        b.setHint(Component.literal("§8" + hint));
        b.setResponder(to);
        b.setEditable(editable);
        addRenderableWidget(b);
        return b;
    }

    boolean aiDirty() {
        CompoundTag ai = d.getCompound("aiSetup");
        return fOn != ai.getBoolean("on") || !fProvider.equals(ai.getString("provider")) || !fModel.trim().equals(ai.getString("model")) || !fKey.isBlank()
                || needsUrl() && !fUrl.trim().equals(ai.getString("url"));
    }

    void initAi() {
        CompoundTag ai = d.getCompound("aiSetup");
        boolean admin = ai.getBoolean("admin");
        if (fProvider == null) {
            fProvider = ai.getString("provider");
            fModel = ai.getString("model");
            fUrl = ai.getString("url");
            fOn = ai.getBoolean("on");
            fStyle = ai.getString("style");
        }
        int y = 18;
        btn(8, y, 98, "AI: " + (fOn ? "§aON" : "§7OFF"), "AI: " + (fOn ? "§aON" : "§7OFF"), b -> {
            fOn = !fOn;
            rebuildWidgets();
        }).active = admin;
        btn(110, y, 202, "Provider: §b" + providerName(fProvider), "Provider: §b" + providerName(fProvider), b -> {
            int i = 0;
            for (int k = 0; k < PROVIDER_NAMES.length; k++) if (PROVIDER_NAMES[k][0].equals(fProvider)) i = k;
            fProvider = PROVIDER_NAMES[(i + 1) % PROVIDER_NAMES.length][0];
            // another provider: its own models and its own key
            fModel = "";
            fKey = "";
            fOn = true;
            rebuildWidgets();
        }).active = admin;
        y += 24;
        boolean same = fProvider.equals(ai.getString("provider"));
        modelBox = box(52, y, 196, fModel, 120, same ? "default: " + ai.getString("modelUsed") : "empty = the provider's default", s -> fModel = s, admin);
        List<String> models = new ArrayList<>();
        if (same) for (net.minecraft.nbt.Tag m : ai.getList("models", 8)) models.add(m.getAsString());
        for (int dir = -1; dir <= 1; dir += 2) {
            final int step = dir;
            btn(dir < 0 ? 252 : 284, y, 28, dir < 0 ? "<" : ">", dir < 0 ? "<" : ">", b -> {
                if (models.isEmpty()) return;
                int i = models.indexOf(fModel.trim());
                fModel = models.get(Math.floorMod(i < 0 ? (step > 0 ? 0 : -1) : i + step, models.size()));
                rebuildWidgets();
            }).active = admin && !models.isEmpty();
        }
        y += 24;
        String tail = ai.getString("keyTail");
        keyBox = null;
        if ("ollama".equals(fProvider)) {
            // a local AI: no key, but it has to be installed and have a model
            btn(8, y, 304, "§bLocal AI setup§r: install Ollama, download a model >", "§bLocal AI setup§r: install Ollama, download a model >", b -> {
                tab = "ollama";
                act("ollama", tagOf("what", "refresh"));
                rebuildWidgets();
            }).active = admin;
        } else {
            keyBox = box(52, y, 196, fKey, 400, "ollama".equals(fProvider) ? "no key needed" : same && !tail.isEmpty() ? "saved .." + tail + " - type a new one" : "paste your API key here", s -> fKey = s, admin);
            if (!showKey) keyBox.setFormatter((text, pos) -> net.minecraft.util.FormattedCharSequence.forward("*".repeat(text.length()), net.minecraft.network.chat.Style.EMPTY));
            btn(252, y, 60, showKey ? "Hide" : "Show", showKey ? "Hide" : "Show", b -> {
                showKey = !showKey;
                rebuildWidgets();
            });
        }
        y += 24;
        urlBox = null;
        if (needsUrl()) urlBox = box(52, y, 260, fUrl, 300, "ollama".equals(fProvider) ? "http://localhost:11434" : "https://.../v1", s -> fUrl = s, admin);
        y += 24;
        btn(8, y, 304, (aiDirty() ? "§e● " : "") + "Save and test the AI", (aiDirty() ? "§e● " : "") + "Save and test the AI", b -> {
            CompoundTag a = new CompoundTag();
            a.putBoolean("on", fOn);
            a.putString("provider", fProvider);
            a.putString("model", fModel.trim());
            a.putString("url", fUrl == null ? "" : fUrl.trim());
            if (!fKey.isBlank()) a.putString("key", fKey.trim());
            fKey = "";
            act("ai_save", a);
        }).active = admin;
        y += 26;
        // the language it speaks to you, and its voice
        String lang = ai.getString("lang");
        int li = 0;
        for (int k = 0; k < com.helperbuddy.net.AiSetup.LANGUAGES.length; k++) if (com.helperbuddy.net.AiSetup.LANGUAGES[k][0].equals(lang)) li = k;
        final int cur = li;
        for (int dir = -1; dir <= 1; dir += 2) {
            final int step = dir;
            btn(dir < 0 ? 8 : 138, y, 20, dir < 0 ? "<" : ">", dir < 0 ? "<" : ">", b -> {
                CompoundTag a = new CompoundTag();
                a.putString("code", com.helperbuddy.net.AiSetup.LANGUAGES[Math.floorMod(cur + step, com.helperbuddy.net.AiSetup.LANGUAGES.length)][0]);
                act("ai_lang", a);
            });
        }
        btn(30, y, 106, "Speaks: §b" + com.helperbuddy.net.AiSetup.LANGUAGES[li][1], "Speaks: §b" + com.helperbuddy.net.AiSetup.LANGUAGES[li][1], b -> {
            CompoundTag a = new CompoundTag();
            a.putString("code", com.helperbuddy.net.AiSetup.LANGUAGES[(cur + 1) % com.helperbuddy.net.AiSetup.LANGUAGES.length][0]);
            act("ai_lang", a);
        });
        btn(162, y, 150, "Voice: §b" + nameOf(SOURCE_NAMES, ai.getString("voice")).replace(" (free, on this PC)", "") + " §7>", "Voice: §b" + nameOf(SOURCE_NAMES, ai.getString("voice")).replace(" (free, on this PC)", "") + " §7>", b -> {
            tab = "voice";
            rebuildWidgets();
        });
        y += 24;
        styleBox = box(52, y, 260, fStyle, 200, "normal - or type e.g. \"sweet and polite\"", s -> fStyle = s, true);
        y += 24;
        btn(8, y, 150, "Save the way it talks", "Save the way it talks", b -> {
            CompoundTag a = new CompoundTag();
            a.putString("text", fStyle == null ? "" : fStyle.trim());
            act("ai_style", a);
        });
        btn(162, y, 150, "Back to its normal way", "Back to its normal way", b -> {
            fStyle = "";
            CompoundTag a = new CompoundTag();
            a.putString("text", "");
            act("ai_style", a);
        });
    }

    void drawAi(GuiGraphics g) {
        CompoundTag ai = d.getCompound("aiSetup");
        int x = left + 8;
        String state = !ai.getBoolean("admin") ? "§7Only the world's host can change the AI"
                : ai.getBoolean("ready") ? "§aAI on§7: " + ai.getString("provider") + " · " + ai.getString("modelUsed")
                : ai.getBoolean("on") ? "§eThe AI is on but has no API key yet" : "§7The AI is off (built-in brain: English and Thai)";
        g.drawString(font, state, x, top + 5, 0xFFFFFF);
        g.drawString(font, "§7Model", x, top + 48, 0xFFFFFF);
        if (!"ollama".equals(fProvider)) g.drawString(font, "§7API key", x, top + 72, 0xFFFFFF);
        if (needsUrl()) g.drawString(font, "§7URL", x, top + 96, 0xFFFFFF);
        else g.drawString(font, "gemini".equals(fProvider) ? "§8A free Gemini key: §7aistudio.google.com/apikey" : "§8Get the key from your account on the provider's website", x, top + 96, 0xFFFFFF);
        g.drawString(font, "§7Style", x, top + 170, 0xFFFFFF);
        g.drawString(font, "§8A style of your own needs the AI to be on.", x, top + 212, 0xFFFFFF);
    }

    static CompoundTag tagOf(String... kv) {
        CompoundTag t = new CompoundTag();
        for (int i = 0; i + 1 < kv.length; i += 2) t.putString(kv[i], kv[i + 1]);
        return t;
    }

    int ticks;

    @Override
    public void tick() {
        super.tick();
        ticks++;
        // the local AI page follows what Ollama is doing
        if ("ollama".equals(tab) && ticks % (d.getCompound("ollama").getBoolean("busy") ? 10 : 40) == 0) act("ollama", tagOf("what", "refresh"));
    }

    // ------------------------------------------------------------------ voice: where it comes from, this helper's voice, the AI voice
    static final String[][] SOURCE_NAMES = {{"mod", "Mod voice (free, on this PC)"}, {"ai", "AI voice for answers"}, {"cartoon", "AI voice, cartoon"},
            {"babble", "Babble sounds"}, {"off", "No voice"}};
    static final String[][] VOICE_PROVIDERS = {{"same", "Same as the AI"}, {"gemini", "Gemini"}, {"openai", "OpenAI"}, {"openrouter", "OpenRouter"},
            {"groq", "Groq (English)"}, {"ollama", "Ollama (Mac)"}, {"custom", "Custom URL"}};
    String vProvider, vKey = "", vModel, vName;
    boolean vShow;

    static String nameOf(String[][] list, String id) {
        for (String[] r : list) if (r[0].equals(id)) return r[1];
        return id;
    }

    void initVoice() {
        CompoundTag v = d.getCompound("voiceSetup");
        boolean admin = v.getBoolean("admin");
        if (vProvider == null) {
            vProvider = v.getString("provider").isEmpty() ? "same" : v.getString("provider");
            vModel = v.getString("model");
            vName = v.getString("name");
        }
        int y = 16;
        btn(8, y, 304, "Voice: §b" + nameOf(SOURCE_NAMES, v.getString("source")), "Voice: §b" + nameOf(SOURCE_NAMES, v.getString("source")), b -> act("voice_source")).active = admin;
        y += 24;
        for (int dir = -1; dir <= 1; dir += 2) {
            final int step = dir;
            btn(dir < 0 ? 8 : 182, y, 20, dir < 0 ? "<" : ">", dir < 0 ? "<" : ">", b -> {
                CompoundTag a = new CompoundTag();
                a.putInt("step", step);
                act("voice_pick", a);
            });
        }
        btn(30, y, 150, "This helper: §b" + v.getString("modVoiceName"), "This helper: §b" + v.getString("modVoiceName"), b -> {
            CompoundTag a = new CompoundTag();
            a.putInt("step", 1);
            act("voice_pick", a);
        });
        btn(206, y, 106, "Listen", "Listen", b -> act("voice_test", new CompoundTag()));
        y += 24;
        boolean thaiReady = com.helperbuddy.client.LocalVoice.thaiReady();
        btn(8, y, 304, thaiReady ? "§aThai voice: ready" : "Thai voice: download now (108 MB, once)", thaiReady ? "§aThai voice: ready" : "Thai voice: download now (108 MB, once)", b -> {
            com.helperbuddy.client.LocalVoice.startThaiDownload();
            rebuildWidgets();
        }).active = !thaiReady;
        y += 30;
        // the AI voice
        btn(52, y, 260, "§b" + nameOf(VOICE_PROVIDERS, vProvider) + ("same".equals(vProvider) ? "§7 (" + v.getString("brain") + ")" : ""),
                "§b" + nameOf(VOICE_PROVIDERS, vProvider) + ("same".equals(vProvider) ? "§7 (" + v.getString("brain") + ")" : ""), b -> {
                    int i = 0;
                    for (int k = 0; k < VOICE_PROVIDERS.length; k++) if (VOICE_PROVIDERS[k][0].equals(vProvider)) i = k;
                    vProvider = VOICE_PROVIDERS[(i + 1) % VOICE_PROVIDERS.length][0];
                    vKey = "";
                    vModel = "";
                    vName = "";
                    rebuildWidgets();
                }).active = admin;
        y += 24;
        boolean sameP = vProvider.equals(v.getString("provider").isEmpty() ? "same" : v.getString("provider"));
        String tail = v.getString("keyTail");
        EditBox kb = box(52, y, 196, vKey, 400, sameP && !tail.isEmpty() ? "saved .." + tail + " - type a new one" : "empty = the AI's key", s -> vKey = s, admin);
        if (!vShow) kb.setFormatter((text, pos) -> net.minecraft.util.FormattedCharSequence.forward("*".repeat(text.length()), net.minecraft.network.chat.Style.EMPTY));
        btn(252, y, 60, vShow ? "Hide" : "Show", vShow ? "Hide" : "Show", b -> {
            vShow = !vShow;
            rebuildWidgets();
        });
        y += 24;
        box(52, y, 126, vModel, 120, "model: default", s -> vModel = s, admin);
        box(186, y, 126, vName, 60, "voice: one per helper", s -> vName = s, admin);
        y += 24;
        btn(8, y, 304, "Save and try the AI voice", "Save and try the AI voice", b -> {
            CompoundTag a = new CompoundTag();
            a.putString("provider", vProvider);
            a.putString("key", vKey.trim());
            a.putString("model", vModel.trim());
            a.putString("name", vName.trim());
            vKey = "";
            act("voice_save", a);
        }).active = admin;
    }

    void drawVoice(GuiGraphics g) {
        CompoundTag v = d.getCompound("voiceSetup");
        int x = left + 8;
        String src = v.getString("source");
        String state = switch (src) {
            case "mod" -> "§aIts own voice§7: made on this computer, free, no key";
            case "ai", "cartoon" -> v.getBoolean("ready") ? "§aAI voice§7 (" + v.getString("used") + ") for answers, the rest in its own voice" : "§eAI voice picked, but not set up§7: it uses its own voice";
            case "babble" -> "§7Babble sounds";
            default -> "§7No voice";
        };
        g.drawString(font, state, x, top + 4, 0xFFFFFF);
        g.drawString(font, "§7AI voice (needs a key; for answers when Voice is an AI voice)", x, top + 104, 0xFFFFFF);
        g.drawString(font, "§7Service", x, top + 120, 0xFFFFFF);
        g.drawString(font, "§7Key", x, top + 144, 0xFFFFFF);
        g.drawString(font, "§7Model", x, top + 168, 0xFFFFFF);
        String last = v.getString("last");
        g.drawString(font, last.isEmpty() ? "§8Claude and Mistral have no voice. Groq / Ollama speak no Thai." : last.equals("ok") ? "§8Last try: it spoke" : "§8Last try: " + last,
                x, top + 214, 0xFFFFFF);
    }

    // ------------------------------------------------------------------ local AI (Ollama)
    int recPage;

    void initOllama() {
        CompoundTag o = d.getCompound("ollama");
        boolean admin = d.getCompound("aiSetup").getBoolean("admin");
        boolean busy = o.getBoolean("busy"), running = o.getBoolean("running"), installed = !o.getString("exe").isEmpty();
        int y = 16;
        if (!installed) btn(8, y, 110, "Install Ollama", "Install Ollama", b -> act("ollama", tagOf("what", "install"))).active = admin && !busy && o.getBoolean("canInstall");
        else if (!running) btn(8, y, 110, "Start Ollama", "Start Ollama", b -> act("ollama", tagOf("what", "start"))).active = admin && !busy;
        else btn(8, y, 110, "Stop Ollama", "Stop Ollama", b -> act("ollama", tagOf("what", "stop"))).active = admin && !busy;
        btn(122, y, 110, "Remove Ollama", "Remove Ollama", b -> act("ollama", tagOf("what", "remove"))).active = admin && !busy && o.getBoolean("ours");
        btn(236, y, 76, "< Back", "< Back", b -> {
            tab = "ai";
            rebuildWidgets();
        });
        if (busy) btn(252, 36, 60, "Cancel", "Cancel", b -> act("ollama", tagOf("what", "cancel")));
        // the models it has
        net.minecraft.nbt.ListTag ms = o.getList("models", 10);
        String using = o.getString("using");
        int yy = 66;
        for (int i = 0; i < Math.min(3, ms.size()); i++) {
            CompoundTag m = ms.getCompound(i);
            String name = m.getString("name");
            boolean on = name.equals(using);
            btn(178, yy, 66, on ? "§aIn use" : "Use", on ? "§aIn use" : "Use", b -> act("ollama", tagOf("what", "use", "model", name))).active = admin && !on && !busy;
            btn(248, yy, 64, "Delete", "Delete", b -> act("ollama", tagOf("what", "delete", "model", name))).active = admin && !busy;
            yy += 20;
        }
        // models to download
        int per = 3, pages = (Ollama_RECOMMENDED.length + per - 1) / per;
        recPage = Math.floorMod(recPage, pages);
        yy = 142;
        for (int i = recPage * per; i < Math.min(Ollama_RECOMMENDED.length, recPage * per + per); i++) {
            String name = Ollama_RECOMMENDED[i][0];
            boolean have = false;
            for (int k = 0; k < ms.size(); k++) if (ms.getCompound(k).getString("name").equals(name) || ms.getCompound(k).getString("name").equals(name + ":latest")) have = true;
            btn(232, yy, 80, have ? "§aDownloaded" : "Download", have ? "§aDownloaded" : "Download", b -> act("ollama", tagOf("what", "pull", "model", name))).active = admin && !busy && !have;
            yy += 22;
        }
        btn(8, 208, 40, "<", "<", b -> {
            recPage--;
            rebuildWidgets();
        });
        btn(52, 208, 40, ">", ">", b -> {
            recPage++;
            rebuildWidgets();
        });
    }

    static final String[][] Ollama_RECOMMENDED = com.helperbuddy.ai.Ollama.RECOMMENDED;

    void drawOllama(GuiGraphics g) {
        CompoundTag o = d.getCompound("ollama");
        int x = left + 8;
        boolean running = o.getBoolean("running"), installed = !o.getString("exe").isEmpty();
        String state = running ? "§aOllama is running§7 " + o.getString("version") + (o.getBoolean("ours") ? " (installed by Helper Buddy)" : "")
                : installed ? "§eOllama is installed but not running" : o.getBoolean("canInstall") ? "§7Ollama is not installed (about 1.5 GB on Windows)" : "§7Linux: install Ollama from ollama.com/download, then come back";
        g.drawString(font, state, x, top + 4, 0xFFFFFF);
        String job = o.getString("job"), err = o.getString("error"), done = o.getString("done");
        float pr = o.getFloat("progress");
        if (!job.isEmpty()) {
            g.drawString(font, "§e" + job, x, top + 40, 0xFFFFFF);
            if (pr >= 0) {
                g.fill(x, top + 51, x + 236, top + 55, 0xFF303840);
                g.fill(x, top + 51, x + (int) (236 * Math.min(1f, pr)), top + 55, 0xFF40C060);
            }
        } else if (!err.isEmpty()) g.drawString(font, "§c" + (err.length() > 52 ? err.substring(0, 52) + "..." : err), x, top + 40, 0xFFFFFF);
        else if (!done.isEmpty()) g.drawString(font, "§a" + done, x, top + 40, 0xFFFFFF);
        net.minecraft.nbt.ListTag ms = o.getList("models", 10);
        g.drawString(font, "§7Your models" + (ms.isEmpty() ? (running ? ": none yet, download one below" : "") : ""), x, top + 58, 0xFFFFFF);
        for (int i = 0; i < Math.min(3, ms.size()); i++) {
            CompoundTag m = ms.getCompound(i);
            g.drawString(font, "§f" + m.getString("name") + " §8" + m.getString("size"), x, top + 72 + i * 20, 0xFFFFFF);
        }
        g.drawString(font, "§7Download a model (small ones answer faster, big ones are smarter)", x, top + 132, 0xFFFFFF);
        int per = 3;
        for (int i = recPage * per, row = 0; i < Math.min(Ollama_RECOMMENDED.length, recPage * per + per); i++, row++) {
            String note = Ollama_RECOMMENDED[i][1];
            g.drawString(font, "§f" + Ollama_RECOMMENDED[i][0], x, top + 144 + row * 22, 0xFFFFFF);
            g.drawString(font, "§8" + font.plainSubstrByWidth(note, 222), x, top + 154 + row * 22, 0xFFFFFF);
        }
        g.drawString(font, "§8Runs on this computer: no key, no limits, needs a good graphics card", x + 92, top + 214, 0xFFFFFF);
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
    //#if MC>=12100
    //$$ @Override
    //$$ public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {} // drawn once, by render()
    //$$
    //#endif
    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        //#if MC>=12100
        //$$ renderTransparentBackground(g);
        //#else
        renderBackground(g);
        //#endif
        g.fill(left - 4, top - 26, left + W + 4, top + H + 4, 0xE0101418);
        g.fill(left - 4, top - 26, left + W + 4, top - 25, 0xFF3A7BD5);
        super.render(g, mx, my, pt);
        switch (tab) {
            case "main" -> drawMain(g);
            case "build" -> drawBuild(g);
            case "talk" -> drawTalk(g);
            case "ai" -> drawAi(g);
            case "voice" -> drawVoice(g);
            case "ollama" -> drawOllama(g);
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
        ItemStack tool = Compat.loadStack(d.getCompound("tool"));
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
