package com.helperbuddy.brain;

import com.helperbuddy.entity.HelperEntity;
import com.helperbuddy.entity.Mode;
import com.helperbuddy.talk.Nlu;
import com.helperbuddy.talk.Say;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Multi-step orders: "ตัดไม้ 20 ท่อน แล้วขุดทราย 64 ก้อน แล้วเอามาให้" / "chop 20 logs then dig 64 sand then bring it to me"
 * becomes a list of steps run one after another, with progress counting and a timeout for each step.
 */
public final class Plan {
    static final int STEP_TIMEOUT = 20 * 60 * 10;
    public static final java.util.Map<String, Integer> DEFAULT_N = java.util.Map.of("chop", 32, "mine", 16, "gather", 64, "food", 8);

    /** One step. Work steps count what they collect. */
    public static final class Step {
        public String k;
        public String th = "", en = "";
        @Nullable
        public Integer n;
        public boolean full;
        public int got;
        public long t0 = -1;
        public List<String> ores = new ArrayList<>();
        @Nullable
        public String[] ids;
        public String name = "";
        public boolean hunt = true, crops = true;
        @Nullable
        public String animal;
        // dig
        @Nullable
        public Nlu.MineOpts mine;
        public Direction dir = Direction.NORTH;
        @Nullable
        public BlockPos at;
        // house
        @Nullable
        public CompoundTag house;
        // goto / wait / fight / task
        @Nullable
        public BlockPos pos;
        public String dim = "";
        public int secs;
        public boolean on = true;
        @Nullable
        public CompoundTag task;

        public Step(String k) {
            this.k = k;
        }

        boolean work() {
            return k.equals("chop") || k.equals("mine") || k.equals("gather") || k.equals("food");
        }

        public String label(boolean th) {
            return th ? this.th : this.en;
        }
    }

    final Brain b;
    final HelperEntity h;
    final List<Step> steps = new ArrayList<>();
    int i;
    boolean dirty;

    Plan(Brain b) {
        this.b = b;
        this.h = b.h;
    }

    public boolean active() {
        return !steps.isEmpty();
    }

    public void clear() {
        steps.clear();
        i = 0;
        b.work.setOreOnly(null);
    }

    @Nullable
    public Step current() {
        return i < steps.size() ? steps.get(i) : null;
    }

    public List<Step> steps() {
        return steps;
    }

    public int index() {
        return i;
    }

    // ------------------------------------------------------------------ labels
    static String unit(Step s, boolean th) {
        if (s.k.equals("chop")) return th ? "ท่อน" : "logs";
        if (s.k.equals("food")) return s.animal != null ? (th ? "ตัว" : "") : (th ? "ชิ้น" : "pieces");
        return th ? "ก้อน" : "blocks";
    }

    public static String progress(Step s, boolean th) {
        if (!s.work()) return "";
        if (s.full) return th ? "(" + s.got + " จนกว่ากระเป๋าจะเต็ม)" : "(" + s.got + " until the bag is full)";
        if (s.n == null) return "";
        return "(" + Math.min(s.got, s.n) + "/" + s.n + " " + unit(s, th) + ")";
    }

    static void relabel(Step s) {
        String amtTh = s.full ? " จนกว่ากระเป๋าจะเต็ม" : s.n != null ? " " + s.n : "";
        String amtEn = s.full ? " until my bag is full" : s.n != null ? " " + s.n : "";
        switch (s.k) {
            case "chop" -> {
                s.th = "ตัดไม้" + amtTh + (s.n != null ? " ท่อน" : "");
                s.en = "chop wood" + amtEn + (s.n != null ? " logs" : "");
            }
            case "mine" -> {
                StringBuilder th = new StringBuilder(), en = new StringBuilder();
                for (String g : s.ores) {
                    if (th.length() > 0) {
                        th.append(", ");
                        en.append(", ");
                    }
                    th.append(Nlu.oreName(g, true));
                    en.append(Nlu.oreName(g, false));
                }
                s.th = "ขุดแร่" + (th.length() > 0 ? "(" + th + ")" : "") + amtTh + (s.n != null ? " ก้อน" : "");
                s.en = "mine " + (en.length() > 0 ? en.toString() : "ores") + amtEn;
            }
            case "gather" -> {
                s.th = "เก็บ" + (s.name.isEmpty() ? "บล็อก" : s.name) + amtTh + (s.n != null ? " ก้อน" : "");
                s.en = "collect " + (s.name.isEmpty() ? "blocks" : s.name) + amtEn;
            }
            case "food" -> {
                if (s.animal != null) {
                    s.th = "ล่า" + FoodJob.animalName(s.animal, true) + amtTh + (s.n != null ? " ตัว" : "");
                    s.en = "hunt " + FoodJob.animalName(s.animal, false) + amtEn;
                } else {
                    String dTh = s.hunt && s.crops ? "ล่าสัตว์และเก็บผักผลไม้" : s.hunt ? "ล่าสัตว์" : "เก็บผักผลไม้";
                    String dEn = s.hunt && s.crops ? "hunting and harvesting" : s.hunt ? "hunting" : "harvesting";
                    s.th = "หาอาหาร (" + dTh + ")" + amtTh + (s.n != null ? " ชิ้น" : "");
                    s.en = "find food (" + dEn + ")" + amtEn;
                }
            }
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ step builders
    public static Step work(String k, @Nullable Integer n, boolean full) {
        Step s = new Step(k);
        s.n = n;
        s.full = full;
        relabel(s);
        return s;
    }

    public static Step mine(List<String> ores, @Nullable Integer n, boolean full) {
        Step s = new Step("mine");
        if (ores != null) s.ores.addAll(ores);
        s.n = n;
        s.full = full;
        relabel(s);
        return s;
    }

    public static Step gather(String[] ids, String nameTh, String nameEn, @Nullable Integer n, boolean full, boolean th) {
        Step s = new Step("gather");
        s.ids = ids;
        s.name = th ? nameTh : nameEn;
        s.n = n;
        s.full = full;
        relabel(s);
        if (!th) s.th = "เก็บ" + nameTh + (s.full ? " จนกว่ากระเป๋าจะเต็ม" : s.n != null ? " " + s.n + " ก้อน" : "");
        else s.en = "collect " + nameEn + (s.full ? " until my bag is full" : s.n != null ? " " + s.n : "");
        return s;
    }

    public static Step food(boolean hunt, boolean crops, @Nullable String animal, @Nullable Integer n, boolean full) {
        Step s = new Step("food");
        s.hunt = hunt;
        s.crops = crops;
        s.animal = animal;
        s.n = n;
        s.full = full;
        relabel(s);
        return s;
    }

    /** "Build a mine": starts where the player stands when they are close, otherwise at the helper. */
    public static Step dig(ServerPlayer p, HelperEntity h, Nlu.MineOpts o, boolean first) {
        Step s = new Step("dig");
        boolean near = p.level() == h.level() && p.distanceTo(h) <= 48;
        s.mine = o;
        s.dir = near && !o.atHelper ? p.getDirection() : h.getDirection();
        if (!s.dir.getAxis().isHorizontal()) s.dir = Direction.NORTH;
        if (first && near && !o.atHelper) s.at = p.blockPosition();
        s.th = "สร้างเหมือง";
        s.en = "build a mine";
        return s;
    }

    /** A building planned right away (in front of the player), so the site does not depend on where they walk later. */
    @Nullable
    public static Step house(String nameTh, String nameEn, CompoundTag prepared) {
        Step s = new Step("house");
        s.house = prepared;
        s.th = "สร้าง" + nameTh;
        s.en = "build a " + nameEn;
        return s;
    }

    public static Step simple(String k, String th, String en) {
        Step s = new Step(k);
        s.th = th;
        s.en = en;
        return s;
    }

    public static Step gotoStep(BlockPos pos, String dim, String th, String en) {
        Step s = simple("goto", th, en);
        s.pos = pos;
        s.dim = dim;
        return s;
    }

    public static Step waitStep(int secs) {
        Step s = simple("wait", "รอ " + secs + " วินาที", "wait " + secs + " seconds");
        s.secs = secs;
        return s;
    }

    public static Step fight(boolean on) {
        Step s = simple("fight", on ? "เปิดโหมดต่อสู้" : "ปิดโหมดต่อสู้", on ? "fighting on" : "fighting off");
        s.on = on;
        return s;
    }

    public static Step task(CompoundTag spec, String th, String en) {
        Step s = simple("task", th, en);
        s.task = spec;
        return s;
    }

    // ------------------------------------------------------------------ running
    /** Replaces whatever it was doing with these steps. */
    public void start(List<Step> list) {
        b.leaveJob();
        steps.clear();
        steps.addAll(list);
        i = 0;
        for (int k = 0; k < steps.size(); k++) {
            Step s = steps.get(k);
            if (s.work() && s.n == null && !s.full && k < steps.size() - 1) {
                s.n = DEFAULT_N.get(s.k);
                String th = s.th, en = s.en;
                relabel(s);
                if (s.k.equals("gather")) {
                    s.th = th + " " + s.n + " ก้อน";
                    s.en = en + " " + s.n;
                }
            }
        }
        boolean th = Say.thai(h.owner());
        StringBuilder sb = new StringBuilder(th ? "§aรับทราบ! ผมจะทำตามนี้:§r" : "§aGot it! Here's the plan:§r");
        for (int k = 0; k < steps.size(); k++) sb.append("\n§e").append(k + 1).append(".§r ").append(steps.get(k).label(th));
        Say.owner(h, sb.toString(), sb.toString());
        begin();
    }

    void begin() {
        Step s = current();
        if (s == null) {
            finish();
            return;
        }
        s.t0 = h.level().getGameTime();
        s.got = 0;
        b.work.setOreOnly(null);
        b.work.end();
        b.work.roamFails = 0;
        ServerPlayer p = h.owner();
        boolean last = i == steps.size() - 1;
        boolean instant = false;
        boolean th = Say.thai(p);
        switch (s.k) {
            case "chop", "mine", "gather", "food" -> {
                if (s.k.equals("mine")) b.work.setOreOnly(s.ores);
                if (s.k.equals("gather")) b.work.setGather(s.ids);
                if (s.k.equals("food")) b.food.setOptions(s.hunt, s.crops, s.animal);
                h.setHome(h.blockPosition());
                h.setMode(switch (s.k) {
                    case "chop" -> Mode.CHOP;
                    case "mine" -> Mode.MINE;
                    case "gather" -> Mode.GATHER;
                    default -> Mode.FOOD;
                });
            }
            case "dig" -> {
                b.dig.cancel();
                if (s.at != null && s.at.distSqr(h.blockPosition()) > 64) b.mover().teleportNear(s.at);
                b.dig.start(s.mine, s.dir, s.at);
                h.setMode(Mode.DIG);
                h.setHome(b.dig.origin);
                s.th = "สร้างเหมือง: " + b.dig.describe(true);
                s.en = "build a mine: " + b.dig.describe(false);
            }
            case "house" -> {
                if (s.house != null) {
                    BuildJob.Prepared pr = BuildJob.loadPrepared(s.house);
                    if (!h.level().dimension().location().toString().equals(pr.dim()) || pr.origin().distSqr(h.blockPosition()) > 64 * 64) {
                        if (p != null) b.mover().bringTo(p);
                    }
                    b.build.start(pr);
                }
            }
            case "come" -> {
                if (p != null && (p.level() != h.level() || p.distanceTo(h) > 6)) b.mover().bringTo(p);
                h.setMode(Mode.FOLLOW);
                instant = true;
            }
            case "follow" -> {
                h.setMode(Mode.FOLLOW);
                instant = true;
            }
            case "stay" -> {
                h.setMode(Mode.STAY);
                instant = true;
            }
            case "guard" -> {
                h.settings.set("fight", true);
                h.setHome(h.blockPosition());
                h.setMode(Mode.GUARD);
                instant = true;
            }
            case "deliver" -> {
                if (p == null) {
                    s.t0 = -1; // waits until the owner is online
                    return;
                }
                if (p.level() != h.level() || p.distanceTo(h) > 6) b.mover().bringTo(p);
                int n = Bag.giveAllTo(h, p, st -> !Bag.isTool(st) && !st.is(Items.TORCH));
                Say.to(p, h, n > 0 ? "เอาของมาส่งให้แล้ว §e" + n + "§r ชิ้น!" : "ผมไม่มีของจะให้เลย", n > 0 ? "Here you go: §e" + n + "§r items!" : "I have nothing to give you");
                h.setMode(Mode.FOLLOW);
                instant = true;
            }
            case "deposit" -> {
                deposit(p);
                instant = true;
            }
            case "goto" -> {
                ResourceKey<net.minecraft.world.level.Level> dk = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(s.dim.isEmpty() ? h.level().dimension().location().toString() : s.dim));
                b.setGoto(s.pos, dk);
            }
            case "wait" -> h.setMode(Mode.STAY);
            case "eat" -> {
                if (!b.care.eatFromBag(true))
                    Say.owner(h, b.care.hasFood(true) ? "ยังอิ่มอยู่เลยครับ เลือดเต็มแล้ว" : "ในกระเป๋าผมไม่มีอาหารเลย",
                            b.care.hasFood(true) ? "I'm not hungry, my health is full" : "I have no food in my bag");
                instant = true;
            }
            case "cook" -> {
                int n = b.care.cookInBag(64);
                Say.owner(h, n > 0 ? "ทำอาหารเสร็จแล้ว §e" + n + "§r ชิ้น!" : "ทำอาหารไม่ได้: ต้องมีของสด (เนื้อ ปลา มันฝรั่ง) กับเชื้อเพลิง เช่น §eถ่านหิน§r หรือไม้ (หรือข้าวสาลี 3 อันเพื่อทำขนมปัง) ในกระเป๋าผม",
                        n > 0 ? "Cooked §e" + n + "§r pieces of food!" : "I can't cook: I need raw food (meat, fish, potatoes) and fuel like §ecoal§r or wood (or 3 wheat for bread) in my bag");
                instant = true;
            }
            case "fight" -> {
                h.settings.set("fight", s.on);
                if (!s.on) b.combat().clear();
                instant = true;
            }
            case "task" -> {
                if (s.task != null) b.tasks.start(s.task);
            }
            default -> instant = true;
        }
        if (instant) {
            advance(false);
            return;
        }
        // a work step without an amount at the end just keeps going: the plan is over
        if (last && s.work() && s.n == null && !s.full) {
            if (steps.size() > 1)
                Say.owner(h, "ขั้นสุดท้าย: §e" + s.th + "§r ผมจะทำไปเรื่อยๆ จนกว่าจะสั่งใหม่นะ", "Last step: §e" + s.en + "§r I'll keep doing it until you tell me otherwise");
            steps.clear();
            i = 0;
        }
        if (th) h.act("wave");
    }

    void deposit(@Nullable ServerPlayer p) {
        List<Bag.Store> cs = Bag.containersNear(h.level(), h.blockPosition(), 10);
        if (cs.isEmpty() && h.home() != null && h.home().distSqr(h.blockPosition()) < 48 * 48) cs = Bag.containersNear(h.level(), h.home(), 10);
        if (cs.isEmpty() && p != null && p.level() == h.level() && p.distanceTo(h) < 64) {
            cs = Bag.containersNear(h.level(), p.blockPosition(), 10);
            if (!cs.isEmpty() && p.distanceTo(h) > 10) b.mover().bringTo(p);
        }
        if (cs.isEmpty()) {
            Say.owner(h, "§eไม่เจอหีบแถวนี้เลย§r (ต้องอยู่ห่างไม่เกิน 10 บล็อก) ของยังอยู่ในกระเป๋าผมนะ", "§eNo chest around here§r (within 10 blocks). Everything is still in my bag");
            return;
        }
        int n = Bag.deposit(h, cs, st -> Bag.isTool(st) || st.is(Items.TORCH) || Care.foodValue(st) > 0);
        Say.owner(h, n > 0 ? "เก็บของเข้าหีบแล้ว §e" + n + "§r ชิ้น (เก็บเครื่องมือ คบเพลิง และอาหารไว้ใช้เอง)" : "หีบเต็มหมดแล้ว เก็บไม่ได้เลย",
                n > 0 ? "Put §e" + n + "§r items in the chest (I kept my tools, torches and food)" : "The chests are full");
    }

    void advance(boolean announce) {
        Step s = current();
        if (announce && s != null) {
            Step next = i + 1 < steps.size() ? steps.get(i + 1) : null;
            Say.owner(h, "§a✔ " + s.th + " เสร็จแล้ว!§r " + progress(s, true) + (next != null ? " ต่อไป: §e" + next.th : ""),
                    "§a✔ " + s.en + " done!§r " + progress(s, false) + (next != null ? " Next: §e" + next.en : ""));
        }
        i++;
        b.work.setOreOnly(null);
        if (i >= steps.size()) {
            finish();
            return;
        }
        begin();
    }

    void finish() {
        Step last = steps.isEmpty() ? null : steps.get(steps.size() - 1);
        int count = steps.size();
        steps.clear();
        i = 0;
        if (last != null && (last.work() || last.k.equals("wait") || last.k.equals("task"))) {
            b.work.end();
            h.setMode(Mode.STAY);
        }
        if (count > 1) {
            Say.owner(h, "§a§lทำครบทุกอย่างที่สั่งแล้วครับ!§r", "§a§lAll done!§r");
            h.act("cheer");
        }
    }

    /** Every tick: is the current step done? */
    void tick() {
        if (steps.isEmpty() || (h.tickCount + h.getId()) % 10 != 0) return;
        Step s = current();
        if (s == null) {
            finish();
            return;
        }
        if (s.t0 == -2) {
            s.t0 = h.level().getGameTime(); // after a reload: the job itself was saved, just restart the clock
            return;
        }
        if (s.t0 < 0) {
            begin(); // deliver waiting for the owner
            return;
        }
        if (h.isKO()) return;
        long age = h.level().getGameTime() - s.t0;
        if (age < 0) s.t0 = h.level().getGameTime();
        boolean done = false;
        switch (s.k) {
            case "chop", "mine", "gather", "food" -> {
                if (s.full) done = Bag.isFull(h);
                else if (s.n != null) done = s.got >= s.n;
                if (!done && Bag.isFull(h) && !s.full) {
                    Say.owner(h, "§eกระเป๋าเต็มแล้ว§r ได้ " + s.got + "/" + s.n + " เลยข้ามไปขั้นต่อไปนะ", "§eMy bag is full§r (" + s.got + "/" + s.n + "), moving on to the next step");
                    done = true;
                }
                if (!done && age > STEP_TIMEOUT) {
                    Say.owner(h, s.th + " ได้ไม่ครบ (ได้ " + s.got + "/" + s.n + ") ข้ามไปขั้นต่อไปนะ", "Couldn't finish \"" + s.en + "\" (" + s.got + "/" + s.n + "), moving on");
                    done = true;
                }
            }
            case "dig" -> done = age > 40 && !b.dig.active();
            case "house" -> done = age > 40 && !b.build.active();
            case "goto" -> done = !b.hasGoto() && age > 2;
            case "wait" -> done = age >= s.secs * 20L;
            case "task" -> done = age > 10 && !b.tasks.active();
            default -> done = true;
        }
        if (done) advance(true);
    }

    // ------------------------------------------------------------------ progress
    void onProgress(String ev, Object what) {
        Step s = current();
        if (s == null || s.t0 < 0) return;
        int add = 0;
        switch (s.k) {
            case "chop" -> {
                if (ev.equals("item") && what instanceof ItemStack st && st.is(ItemTags.LOGS)) add = st.getCount();
            }
            case "mine" -> {
                if (ev.equals("break") && what instanceof BlockState bs) {
                    String g = Jobs.oreGroup(bs);
                    if (g != null && (s.ores.isEmpty() || s.ores.contains(g))) add = 1;
                }
            }
            case "gather" -> {
                if (ev.equals("item") && what instanceof ItemStack st && b.work.wantsItem(st)) add = st.getCount();
            }
            case "food" -> {
                if (s.animal != null) {
                    if (ev.equals("hunt") && what instanceof LivingEntity le && FoodJob.matches(le, s.animal)) add = 1;
                } else if (ev.equals("item") && what instanceof ItemStack st && Care.foodValue(st) > 0) add = st.getCount();
            }
            default -> {
            }
        }
        if (add > 0) {
            s.got += add;
            dirty = true;
        }
    }

    @Nullable
    public String statusShort(boolean th) {
        Step s = current();
        if (s == null) return null;
        return (i + 1) + "/" + steps.size() + " " + s.label(th) + " " + progress(s, th);
    }

    public String summary(boolean th) {
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < steps.size(); k++) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(k == i ? "§e▶" : k < i ? "§a✔" : "§7•").append(' ').append(k + 1).append(". ").append(steps.get(k).label(th)).append("§r");
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ save
    static CompoundTag saveStep(Step s) {
        CompoundTag t = new CompoundTag();
        t.putString("k", s.k);
        t.putString("th", s.th);
        t.putString("en", s.en);
        if (s.n != null) t.putInt("n", s.n);
        t.putBoolean("full", s.full);
        t.putInt("got", s.got);
        t.putBoolean("started", s.t0 >= 0);
        ListTag o = new ListTag();
        for (String g : s.ores) o.add(StringTag.valueOf(g));
        t.put("ores", o);
        if (s.ids != null) {
            ListTag ids = new ListTag();
            for (String g : s.ids) ids.add(StringTag.valueOf(g));
            t.put("ids", ids);
        }
        t.putString("name", s.name);
        t.putBoolean("hunt", s.hunt);
        t.putBoolean("crops", s.crops);
        if (s.animal != null) t.putString("animal", s.animal);
        if (s.mine != null) {
            CompoundTag m = new CompoundTag();
            if (s.mine.y != null) m.putInt("y", s.mine.y);
            if (s.mine.depth != null) m.putInt("depth", s.mine.depth);
            if (s.mine.length != null) m.putInt("length", s.mine.length);
            if (s.mine.branch != null) m.putInt("branch", s.mine.branch);
            m.putBoolean("flat", s.mine.flat);
            m.putBoolean("atHelper", s.mine.atHelper);
            if (s.mine.dir != null) m.putString("dir", s.mine.dir);
            t.put("mine", m);
        }
        t.putInt("dir", s.dir.get2DDataValue());
        if (s.at != null) t.putLong("at", s.at.asLong());
        if (s.house != null) t.put("house", s.house);
        if (s.pos != null) t.putLong("pos", s.pos.asLong());
        t.putString("dim", s.dim);
        t.putInt("secs", s.secs);
        t.putBoolean("on", s.on);
        if (s.task != null) t.put("task", s.task);
        return t;
    }

    static Step loadStep(CompoundTag t) {
        Step s = new Step(t.getString("k"));
        s.th = t.getString("th");
        s.en = t.getString("en");
        s.n = t.contains("n") ? t.getInt("n") : null;
        s.full = t.getBoolean("full");
        s.got = t.getInt("got");
        s.t0 = -1;
        for (Tag x : t.getList("ores", Tag.TAG_STRING)) s.ores.add(x.getAsString());
        if (t.contains("ids")) {
            ListTag l = t.getList("ids", Tag.TAG_STRING);
            s.ids = new String[l.size()];
            for (int k = 0; k < l.size(); k++) s.ids[k] = l.getString(k);
        }
        s.name = t.getString("name");
        s.hunt = t.getBoolean("hunt");
        s.crops = t.getBoolean("crops");
        s.animal = t.contains("animal") ? t.getString("animal") : null;
        if (t.contains("mine")) {
            CompoundTag m = t.getCompound("mine");
            Nlu.MineOpts o = new Nlu.MineOpts();
            o.y = m.contains("y") ? m.getInt("y") : null;
            o.depth = m.contains("depth") ? m.getInt("depth") : null;
            o.length = m.contains("length") ? m.getInt("length") : null;
            o.branch = m.contains("branch") ? m.getInt("branch") : null;
            o.flat = m.getBoolean("flat");
            o.atHelper = m.getBoolean("atHelper");
            o.dir = m.contains("dir") ? m.getString("dir") : null;
            s.mine = o;
        }
        s.dir = Direction.from2DDataValue(t.getInt("dir"));
        s.at = t.contains("at") ? BlockPos.of(t.getLong("at")) : null;
        s.house = t.contains("house") ? t.getCompound("house") : null;
        s.pos = t.contains("pos") ? BlockPos.of(t.getLong("pos")) : null;
        s.dim = t.getString("dim");
        s.secs = t.getInt("secs");
        s.on = t.getBoolean("on");
        s.task = t.contains("task") ? t.getCompound("task") : null;
        return s;
    }

    void save(CompoundTag jobs) {
        if (steps.isEmpty()) {
            jobs.remove("plan");
            return;
        }
        CompoundTag t = new CompoundTag();
        ListTag l = new ListTag();
        for (Step s : steps) l.add(saveStep(s));
        t.put("steps", l);
        t.putInt("i", i);
        jobs.put("plan", t);
        dirty = false;
    }

    void load(CompoundTag jobs) {
        steps.clear();
        i = 0;
        if (!jobs.contains("plan")) return;
        CompoundTag t = jobs.getCompound("plan");
        for (Tag x : t.getList("steps", Tag.TAG_COMPOUND)) steps.add(loadStep((CompoundTag) x));
        i = t.getInt("i");
        Step s = current();
        if (s != null && s.k.equals("mine")) b.work.setOreOnly(s.ores);
        // after a reload the current step restarts its clock but keeps its count
        if (s != null) s.t0 = s.k.equals("deliver") ? -1 : -2;
    }
}
