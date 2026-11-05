package com.helperbuddy.talk;

import com.helperbuddy.HelperBuddy;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.helperbuddy.Helpers;
import com.helperbuddy.brain.Bag;
import com.helperbuddy.brain.BuildJob;
import com.helperbuddy.brain.Crafter;
import com.helperbuddy.brain.Plan;
import com.helperbuddy.brain.Tasks;
import com.helperbuddy.config.HBConfig;
import com.helperbuddy.entity.HelperEntity;
import com.helperbuddy.entity.Mode;
import com.helperbuddy.entity.Settings;
import com.helperbuddy.gen.ExtraGen;
import com.helperbuddy.gen.Gen;
import com.helperbuddy.gen.HouseGen;
import com.helperbuddy.item.CommandBookItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What the player says to a helper: single orders, chains of orders ("... แล้ว ..." / "... then ..."), questions and
 * small talk. The built-in brain (Nlu) understands Thai and English; with a real AI configured, sentences it does not
 * understand are rewritten by the AI, chat gets AI answers and unknown buildings are designed by the AI.
 */
public final class Talk {
    private Talk() {}

    static final Map<UUID, String> LAST_ORDER = new HashMap<>();
    static final Map<UUID, UUID> LAST_HELPER = new HashMap<>();

    static boolean th(ServerPlayer p) {
        return Say.thai(p);
    }

    static void say(ServerPlayer p, HelperEntity h, String th, String en) {
        Say.to(p, h, th, en);
    }

    // ------------------------------------------------------------------ entry from chat / command / screen
    /** Picks which helper(s) the player is talking to ("ทุกคน ..." / "everyone ..." / "<name> ...", else the last or nearest). */
    public static void command(ServerPlayer p, String raw) {
        Say.noteLanguage(p, raw);
        List<HelperEntity> mine = Helpers.owned(p);
        if (mine.isEmpty()) {
            p.sendSystemMessage(Component.literal(Say.t(p, "§6[ผู้ช่วย]§r คุณยังไม่มีผู้ช่วยเลย ใช้ §eสัญญาจ้างผู้ช่วย§r คลิกที่พื้นเพื่อเรียกผู้ช่วยก่อนนะ",
                    "§6[Helper]§r You have no helper yet. Use a §eHelper Contract§r on the ground to call one")));
            return;
        }
        String text = raw.trim();
        String t = Nlu.norm(text);
        List<HelperEntity> targets = new ArrayList<>();
        java.util.regex.Matcher all = java.util.regex.Pattern.compile("^(ทุกคน|ทุกตัว|all|everyone|everybody)[\\s,:]*").matcher(t);
        if (all.find()) {
            targets.addAll(mine);
            text = text.replaceFirst("(?i)^\\s*(ทุกคน|ทุกตัว|all|everyone|everybody)[\\s,:]*", "");
        } else {
            for (HelperEntity h : mine) {
                String n = Nlu.norm(h.helperName());
                if (!n.isEmpty() && t.startsWith(n)) {
                    targets.add(h);
                    text = text.substring(Math.min(text.length(), h.helperName().length())).replaceFirst("^[\\s,:]+", "");
                    break;
                }
            }
        }
        if (targets.isEmpty()) {
            UUID last = LAST_HELPER.get(p.getUUID());
            for (HelperEntity h : mine) if (h.getUUID().equals(last)) targets.add(h);
            if (targets.isEmpty()) {
                mine.sort((a, b) -> Double.compare(score(p, a), score(p, b)));
                targets.add(mine.get(0));
            }
        }
        if (text.isBlank()) {
            help(p);
            return;
        }
        Nlu.Intent it = Nlu.parse(text);
        if (targets.size() > 1 && (it.k.equals("house") || it.k.equals("dig") || it.k.equals("name") || it.k.equals("aibuild"))) targets = List.of(targets.get(0));
        for (HelperEntity h : targets) handle(p, h, text);
    }

    static double score(ServerPlayer p, HelperEntity h) {
        if (h.level() != p.level()) return 1e9;
        return h.distanceTo(p);
    }

    /** Executes what the player said to one helper. */
    public static void handle(ServerPlayer p, HelperEntity h, String raw) {
        handle(p, h, raw, true);
    }

    /** `typed` = the player wrote it (echoed, language noted); false for menu buttons. */
    public static void handle(ServerPlayer p, HelperEntity h, String raw, boolean typed) {
        String text = raw.trim();
        if (text.isEmpty()) return;
        LAST_HELPER.put(p.getUUID(), h.getUUID());
        if (typed) {
            Say.noteLanguage(p, text);
            p.sendSystemMessage(Component.literal("§7[" + Say.t(p, "คุณ", "You") + " → " + h.helperName() + "]§f " + text));
        }
        if (!h.isOwner(p)) {
            say(p, h, "ผมเป็นผู้ช่วยของ " + h.ownerName() + " นะครับ", "I'm " + h.ownerName() + "'s helper");
            return;
        }
        // with a real AI set up, it reads every typed sentence first (orders, chat, questions); the built-in brain is the
        // fallback when the AI can't be reached. Menu buttons stay on the built-in brain (instant, exact).
        if (typed && AiBridge.enabled() && HBConfig.AI_FOR_COMMANDS.get()) {
            aiUnderstand(p, h, text, true);
            return;
        }
        local(p, h, text, true);
    }

    /** The built-in Thai/English brain. */
    static void local(ServerPlayer p, HelperEntity h, String text, boolean allowAi) {
        // questions first (knowledge / small talk)
        if (Nlu.isQuestion(text)) {
            String kb = SmallTalk.reply(Nlu.norm(text), ctx(h, p), th(p));
            if (kb != null) {
                say(p, h, kb, kb);
                return;
            }
        }
        Nlu.Intent first = Nlu.parse(text);
        if (first.k.equals("repeat")) {
            String prev = LAST_ORDER.get(h.getUUID());
            if (prev == null) {
                say(p, h, "ยังไม่มีงานก่อนหน้าให้ทำซ้ำเลยครับ สั่งงานใหม่ได้เลย", "There's no earlier order to repeat yet");
                return;
            }
            say(p, h, "ได้ครับ ทำซ้ำ: \"" + prev + "\"", "Sure, again: \"" + prev + "\"");
            handle(p, h, prev);
            return;
        }
        if (!NOT_ORDERS.contains(first.k) && !first.k.equals("unknown")) LAST_ORDER.put(h.getUUID(), text);
        if (tryPlan(p, h, text)) return;
        single(p, h, first, text, allowAi);
    }

    static final List<String> NOT_ORDERS = List.of("help", "status", "level", "hello", "thanks", "hungry", "book", "name", "repeat", "cancel", "resume");
    static final List<String> QUERY = List.of("help", "status", "level", "hello", "thanks", "hungry", "book", "name");
    static final List<String> PLAN_ONLY = List.of("goto", "wait", "deposit", "eat", "cook", "guard");

    static SmallTalk.Ctx ctx(HelperEntity h, ServerPlayer p) {
        long dt = h.level().getDayTime();
        double hour = ((dt / 1000.0 + 6) % 24 + 24) % 24;
        return new SmallTalk.Ctx(h.helperName(), (int) Math.ceil(h.shownHp()), h.maxHp(), h.helperLevel(), h.mode().label(th(p)), hour, dt / 24000L);
    }

    // ------------------------------------------------------------------ one order
    static void single(ServerPlayer p, HelperEntity h, Nlu.Intent it, String text, boolean allowAi) {
        boolean th = th(p);
        switch (it.k) {
            case "gather" -> cmdGather(p, h, it);
            case "findFood" -> cmdFood(p, h, it);
            case "help" -> help(p);
            case "come" -> cmdCome(p, h);
            case "rescue" -> {
                cmdCome(p, h);
                cmdFight(p, h, true);
            }
            case "follow" -> cmdStop(p, h, Mode.FOLLOW, false);
            case "stay" -> cmdStop(p, h, Mode.STAY, false);
            case "mine" -> cmdMine(p, h, it);
            case "chop" -> cmdWork(p, h, Mode.CHOP);
            case "dig" -> cmdDig(p, h, it);
            case "house" -> cmdHouse(p, h, it, text);
            case "aibuild" -> aiBuild(p, h, it.text != null ? it.text : text);
            case "fight" -> cmdFight(p, h, it.on);
            case "fightback" -> {
                h.settings.set("fightBack", it.on);
                say(p, h, it.on ? "โอเค ถ้ามีใครตีผม ผมจะสู้กลับ" : "โอเค ผมจะไม่สู้กลับแล้ว (แต่ยังหลบได้นะ)",
                        it.on ? "OK, if anything hits me I'll fight back" : "OK, I won't fight back anymore");
            }
            case "level" -> say(p, h, levelText(h, true), levelText(h, false));
            case "status" -> status(p, h);
            case "give" -> cmdGive(p, h, it);
            case "resume" -> {
                if (!cmdResume(p, h)) say(p, h, "ไม่มีงานค้างไว้เลยครับ สั่งงานใหม่ได้เลย (พิมพ์ \"ช่วย\" เพื่อดูคำสั่ง)", "Nothing to resume. Give me a new order (type \"help\")");
            }
            case "cancel" -> cmdCancel(p, h);
            case "name" -> {
                String old = h.helperName();
                h.setHelperName(it.name);
                say(p, h, "จากนี้ไปเรียกผมว่า §e" + h.helperName() + "§r นะครับ (ชื่อเดิม " + old + ")", "From now on call me §e" + h.helperName() + "§r (was " + old + ")");
            }
            case "book" -> {
                if (!CommandBookItem.give(p)) say(p, h, "กระเป๋าคุณเต็มอยู่ ทำที่ว่างก่อนนะ", "Your inventory is full");
                else say(p, h, "นี่ครับ หนังสือสั่งงาน ใช้สั่งผมได้จากทุกที่", "Here's the command book: order me from anywhere");
            }
            case "hungry" -> {
                if (h.isKO()) say(p, h, "§cผมสลบอยู่ หิวมาก...§r ส่งอาหารมาให้หน่อยนะ", "§cI'm knocked out and starving...§r Please feed me");
                else if (h.getHealth() < h.getMaxHealth() - 0.5f)
                    say(p, h, "เลือดผม " + (int) Math.ceil(h.shownHp()) + "/" + h.maxHp() + " ถ้าได้กินอะไรหน่อยจะดีมาก (หรือสั่ง \"หาอาหาร\" ให้ผมหาเอง)",
                            "My health is " + (int) Math.ceil(h.shownHp()) + "/" + h.maxHp() + ". Some food would help (or tell me \"find food\")");
                else say(p, h, "ยังอิ่มอยู่ครับ เลือดเต็มแล้ว", "I'm full, health is at max");
            }
            case "hello" -> say(p, h, "สวัสดีครับ! ตอนนี้ผมกำลัง" + h.mode().label(true) + " เลือด " + (int) Math.ceil(h.shownHp()) + "/" + h.maxHp() + " (เลเวล " + h.helperLevel() + ") มีอะไรให้ช่วยไหม?",
                    "Hi! I'm " + h.mode().label(false) + " right now, health " + (int) Math.ceil(h.shownHp()) + "/" + h.maxHp() + " (level " + h.helperLevel() + "). What can I do for you?");
            case "thanks" -> {
                String[] a = {"ยินดีครับ!", "เพื่อคุณ ผมทำได้ทุกอย่าง!", "ขอบคุณที่ชมครับ อิอิ", "งานสบายมากครับ!"};
                String[] b = {"You're welcome!", "Anything for you!", "Thanks, that means a lot!", "Easy work!"};
                int i = h.getRandom().nextInt(a.length);
                say(p, h, a[i], b[i]);
                h.act("cheer");
            }
            case "craft" -> {
                int n = it.n == null ? 1 : it.n;
                Item item = BuiltInRegistries.ITEM.get(HelperBuddy.rl(it.item));
                String name = new ItemStack(item).getHoverName().getString();
                say(p, h, "ได้ครับ จะคราฟต์ §e" + name + "§r " + n + " ชิ้นให้", "Sure, crafting §e" + n + " " + name);
                h.brain().tasks().start(Tasks.craft(it.item, n));
            }
            case "smelt" -> {
                say(p, h, "ได้ครับ จะถลุงของในกระเป๋าให้ (ต้องมีเชื้อเพลิงด้วยนะ)", "Sure, I'll smelt what's in my bag (I need fuel too)");
                h.brain().tasks().start(Tasks.smelt(it.text, it.n));
            }
            case "fetch" -> h.brain().tasks().start(Tasks.fetch(it.text, it.n));
            case "breed" -> {
                say(p, h, "ได้ครับ จะให้อาหารสัตว์ให้มันออกลูก", "Sure, I'll feed the animals so they breed");
                h.brain().tasks().start(Tasks.breed(it.animal == null ? null : it.animal[0]));
            }
            case "shear" -> {
                say(p, h, "ได้ครับ จะตัดขนแกะแถวนี้ให้", "Sure, shearing the sheep around here");
                h.brain().tasks().start(Tasks.spec("shear"));
            }
            case "torch" -> {
                say(p, h, "ได้ครับ จะปักคบเพลิงตรงที่มืดๆ แถวนี้ให้", "Sure, I'll put torches in the dark spots around here");
                h.brain().tasks().start(Tasks.spec("torch"));
            }
            case "flatten" -> {
                say(p, h, "ได้ครับ จะปรับพื้นที่รอบๆ ตรงนี้ให้เรียบ", "Sure, flattening the ground around here");
                if (h.distanceTo(p) > 8 && h.level() == p.level()) h.brain().mover().bringTo(p);
                h.brain().tasks().start(Tasks.spec("flatten"));
            }
            case "farm" -> {
                h.brain().leaveJob();
                h.setHome(p.level() == h.level() && p.distanceTo(h) < 24 ? p.blockPosition() : h.blockPosition());
                h.setMode(Mode.FARM);
                say(p, h, "§aทำฟาร์มแล้วนะ!§r ผมจะเก็บเกี่ยวพืชที่สุก ปลูกคืน หว่านเมล็ดบนแปลงว่าง" + (Bag.hasKind(h, Bag.Kind.HOE) ? " และไถดินข้างน้ำทำแปลงใหม่" : " (ให้§eจอบ§rผมด้วย จะไถแปลงใหม่ให้)"),
                        "§aFarming now!§r I'll harvest ripe crops, replant and sow empty farmland" + (Bag.hasKind(h, Bag.Kind.HOE) ? " and till new fields next to water" : " (give me a §ehoe§r for new fields)"));
            }
            case "goto", "wait", "deposit", "eat", "cook", "guard" -> tryPlanForced(p, h, text);
            default -> unknown(p, h, text, allowAi);
        }
    }

    static void unknown(ServerPlayer p, HelperEntity h, String text, boolean allowAi) {
        boolean th = th(p);
        String reply = SmallTalk.reply(Nlu.norm(text), ctx(h, p), th);
        if (reply != null) {
            say(p, h, reply, reply);
            return;
        }
        if (allowAi && AiBridge.enabled() && (HBConfig.AI_FOR_COMMANDS.get() || HBConfig.AI_FOR_CHAT.get())) {
            aiUnderstand(p, h, text, false);
            return;
        }
        say(p, h, "ขอโทษครับ ผมไม่แน่ใจว่า \"§f" + text + "§r\" หมายถึงอะไร ลองพูดแบบอื่น หรือพิมพ์ \"§eช่วย§r\" เพื่อดูตัวอย่างคำสั่ง",
                "Sorry, I'm not sure what \"§f" + text + "§r\" means. Try saying it another way, or type \"§ehelp§r\" for examples");
    }

    // ------------------------------------------------------------------ commands
    static void cmdStop(ServerPlayer p, HelperEntity h, Mode m, boolean quiet) {
        h.brain().leaveJob();
        h.setMode(m);
        if (!quiet) say(p, h, m == Mode.FOLLOW ? "หยุดงานแล้ว จะเดินตามคุณไปนะครับ" : "โอเค ผมจะหยุดรออยู่ตรงนี้", m == Mode.FOLLOW ? "OK, I'll follow you" : "OK, I'll wait here");
    }

    static void cmdCome(ServerPlayer p, HelperEntity h) {
        h.brain().leaveJob();
        h.setMode(Mode.FOLLOW);
        if (h.level() != p.level() || h.distanceTo(p) > 6) h.brain().mover().bringTo(p);
        say(p, h, "มาแล้วครับ! จะเดินตามคุณไปนะ", "Coming! I'll follow you");
    }

    static void cmdWork(ServerPlayer p, HelperEntity h, Mode m) {
        h.brain().leaveJob();
        if (h.isKO()) say(p, h, "§cผมยังสลบอยู่...§r ให้อาหารผมก่อนนะ แล้วผมจะไปทำงานต่อ", "§cI'm still knocked out...§r Feed me first, then I'll go");
        h.setMode(m);
        h.setHome(h.blockPosition());
        int r = HBConfig.SEARCH_RADIUS.get();
        if (m == Mode.MINE) {
            boolean has = Bag.hasKind(h, Bag.Kind.PICKAXE);
            say(p, h, has ? "§6ไปหาแร่แล้วนะ!§r ผมจะหาแร่ที่มองเห็นในระยะ " + r + " บล็อก แล้วเดินหรือขุดทางไปเอง" + (Bag.hasKind(h, Bag.Kind.SHOVEL) ? "" : "\n§7ถ้าส่ง§eพลั่ว§7มาด้วย ขุดดิน/ทราย/กรวดจะเร็วขึ้น")
                            : "§6โหมดขุดแร่!§r แต่ผมยังไม่มี§eอีเต้อ§rเลย ส่งให้ผมหน่อยนะ",
                    has ? "§6Off to mine!§r I'll look for ores within " + r + " blocks and walk or dig my way there" : "§6Mining mode!§r But I have no §epickaxe§r yet. Please give me one");
        } else {
            boolean has = Bag.hasKind(h, Bag.Kind.AXE);
            say(p, h, "§2ไปตัดไม้แล้วนะ!§r ผมจะหาต้นไม้ในระยะ " + r + " บล็อก ตัดทั้งต้น เก็บกิ่งกับต้นกล้า แล้วปลูกคืนให้" + (has ? "" : "\n§7(ยังไม่มีขวาน จะใช้มือไปก่อน ส่ง§eขวาน§7มาจะเร็วขึ้นมาก)"),
                    "§2Off to chop wood!§r I'll find trees within " + r + " blocks, fell them whole, collect saplings and replant" + (has ? "" : "\n§7(no axe yet: by hand for now, an §eaxe§7 makes it much faster)"));
        }
    }

    static void cmdMine(ServerPlayer p, HelperEntity h, Nlu.Intent it) {
        Settings s = h.settings;
        if (it.all) {
            for (String g : Settings.ORES) s.setOre(g, true);
            say(p, h, "ตั้งให้ขุด §eทุกแร่§r แล้ว", "I'll mine §eevery ore§r");
        } else if (!it.ores.isEmpty()) {
            StringBuilder th = new StringBuilder(), en = new StringBuilder();
            for (String g : Settings.ORES) s.setOre(g, it.ores.contains(g));
            for (String g : it.ores) {
                th.append(th.length() > 0 ? ", " : "").append(Nlu.oreName(g, true));
                en.append(en.length() > 0 ? ", " : "").append(Nlu.oreName(g, false));
            }
            say(p, h, "ตั้งให้ขุด §eเฉพาะ " + th + "§r แล้ว §7(พิมพ์ \"ขุดทุกแร่\" เพื่อกลับไปขุดทุกอย่าง)", "I'll mine §eonly " + en + "§r §7(say \"mine all ores\" to mine everything again)");
        }
        cmdWork(p, h, Mode.MINE);
    }

    static void cmdGather(ServerPlayer p, HelperEntity h, Nlu.Intent it) {
        h.brain().leaveJob();
        h.brain().work().setGather(it.ids);
        h.setMode(Mode.GATHER);
        h.setHome(h.blockPosition());
        String th = it.block == null ? "บล็อก" : it.block.th(), en = it.block == null ? "blocks" : it.block.en();
        say(p, h, "§eไปเก็บ" + th + "แล้วนะ!§r ผมจะหา" + th + "ในระยะ " + HBConfig.SEARCH_RADIUS.get() + " บล็อกแถวนี้" + (isPlant(it) ? " (เหลือโคนไว้ให้มันงอกใหม่ด้วย)" : ""),
                "§eOff to collect " + en + "!§r I'll look within " + HBConfig.SEARCH_RADIUS.get() + " blocks" + (isPlant(it) ? " (leaving the roots so they grow back)" : ""));
    }

    static boolean isPlant(Nlu.Intent it) {
        if (it.ids == null) return false;
        for (String id : it.ids) if (id.equals("bamboo") || id.equals("sugar_cane") || id.equals("cactus")) return true;
        return false;
    }

    static void cmdFood(ServerPlayer p, HelperEntity h, Nlu.Intent it) {
        h.brain().leaveJob();
        String animal = it.animal == null ? null : it.animal[0];
        h.brain().food().setOptions(it.hunt, it.crops, animal);
        h.setMode(Mode.FOOD);
        h.setHome(h.blockPosition());
        say(p, h, "§cไปหาอาหารแล้วนะ!§r (" + h.brain().food().describe(true) + ") ในระยะ " + HBConfig.SEARCH_RADIUS.get() + " บล็อก\n§7ไม่ล่าลูกสัตว์ สัตว์เลี้ยง หรือสัตว์ที่มีชื่อ เก็บผักที่สุกแล้วปลูกคืนให้ (มีถ่านหินในกระเป๋า ผมจะย่างเนื้อให้สุกเอง)",
                "§cOff to find food!§r (" + h.brain().food().describe(false) + ") within " + HBConfig.SEARCH_RADIUS.get() + " blocks\n§7I never hunt babies, pets or named animals, and I replant crops (with coal in my bag I cook the meat too)");
    }

    static void cmdDig(ServerPlayer p, HelperEntity h, Nlu.Intent it) {
        h.brain().leaveJob();
        Nlu.MineOpts o = it.mine == null ? new Nlu.MineOpts() : it.mine.o();
        boolean near = p.level() == h.level() && p.distanceTo(h) <= 48;
        BlockPos at = null;
        net.minecraft.core.Direction dir = h.getDirection();
        if (!o.atHelper && near) {
            at = p.blockPosition();
            dir = p.getDirection();
            if (p.distanceTo(h) > 8) h.brain().mover().bringTo(p);
        }
        h.brain().dig().start(o, dir, at);
        h.setMode(Mode.DIG);
        h.setHome(at != null ? at : h.blockPosition());
        boolean th = th(p);
        StringBuilder lack = new StringBuilder();
        if (!Bag.hasKind(h, Bag.Kind.PICKAXE)) lack.append(th ? "§eอีเต้อ§r (ขุดหิน)" : "a §epickaxe§r");
        say(p, h, "§8§lสร้างเหมือง!§r " + h.brain().dig().describe(true) + (lack.length() > 0 ? "\n§cต้องใช้ " + lack + "§r ส่งมาให้ผมด้วยนะ ผมจะรอจนกว่าจะได้เครื่องมือ" : "")
                        + "\n§7(ใส่คบเพลิงในกระเป๋าผม ผมจะปักไฟให้ / เจอแร่ข้างทางผมจะขุดเก็บให้ด้วย)",
                "§8§lBuilding a mine!§r " + h.brain().dig().describe(false) + (lack.length() > 0 ? "\n§cI need " + lack + "§r, I'll wait until I get one" : "")
                        + "\n§7(put torches in my bag and I'll light it / I mine the ores I pass too)");
    }

    static void cmdCancel(ServerPlayer p, HelperEntity h) {
        boolean did = h.brain().plan().active() || h.brain().dig().active() || h.brain().build().active() || h.brain().tasks().active();
        h.brain().leaveJob();
        h.brain().dig().cancel();
        h.brain().build().cancel();
        h.setMode(Mode.STAY);
        say(p, h, did ? "ยกเลิกงานแล้ว ผมรออยู่ตรงนี้นะ (ส่วนที่สร้างไปแล้วยังอยู่)" : "โอเค ไม่มีงานค้างอยู่ ผมรออยู่ตรงนี้นะ",
                did ? "Cancelled. I'll wait here (whatever was built stays)" : "OK, nothing to cancel. I'll wait here");
    }

    static boolean cmdResume(ServerPlayer p, HelperEntity h) {
        if (h.brain().dig().active()) {
            h.setMode(Mode.DIG);
            say(p, h, "ไปสร้างเหมืองต่อละนะ!", "Back to the mine!");
            return true;
        }
        if (h.brain().build().active()) {
            h.setMode(Mode.BUILD);
            say(p, h, "ไปสร้างต่อละนะ!", "Back to building!");
            return true;
        }
        return false;
    }

    static void cmdFight(ServerPlayer p, HelperEntity h, boolean on) {
        h.settings.set("fight", on);
        if (!on) {
            h.brain().combat().setTarget(null, false, false);
            h.brain().mover().stop();
            say(p, h, "โอเค ผมจะไม่เข้าไปสู้กับมอนสเตอร์แล้ว (แต่ถ้าโดนตี ผมยังสู้กลับนะ ถ้าไม่อยากให้สู้กลับ พิมพ์ \"ไม่ต้องสู้กลับ\")",
                    "OK, I won't attack monsters (I still fight back when hit; say \"don't fight back\" to stop that too)");
            return;
        }
        if (h.isKO()) {
            say(p, h, "ผมสลบอยู่ ยังสู้ไม่ได้ ให้อาหารผมก่อนนะ", "I'm knocked out, feed me first");
            return;
        }
        LivingEntity best = null;
        if (p.level() == h.level()) {
            double bd = 99;
            for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(16), e -> e instanceof Enemy && e.isAlive())) {
                double d = e.distanceTo(p);
                if (d < bd) {
                    bd = d;
                    best = e;
                }
            }
        }
        if (best != null) {
            if (h.distanceTo(p) > 24) h.brain().mover().bringTo(p);
            h.brain().combat().setTarget(best, true, false);
            say(p, h, "§cลุยเลย!§r จัดการ " + best.getName().getString() + " ให้เอง", "§cOn it!§r I'll deal with the " + best.getName().getString());
        } else say(p, h, "พร้อมสู้ครับ! ถ้ามีมอนสเตอร์เข้าใกล้คุณหรือผม ผมจะเข้าไปจัดการเอง (ครีปเปอร์ผมตีแล้วถอย)",
                "Ready to fight! Any monster near you or me gets dealt with (creepers: hit and back off)");
    }

    static void cmdGive(ServerPlayer p, HelperEntity h, Nlu.Intent it) {
        if (it.item == null) {
            if (h.level() != p.level() || h.distanceTo(p) > 8) h.brain().mover().bringTo(p);
            int n = Bag.giveAllTo(h, p, s -> true);
            say(p, h, n > 0 ? "ส่งของคืนให้แล้ว " + n + " ชิ้น" : "กระเป๋าผมว่างอยู่ ไม่มีของจะให้เลย", n > 0 ? "Gave you " + n + " items" : "My bag is empty");
            return;
        }
        Item item = BuiltInRegistries.ITEM.get(HelperBuddy.rl(it.item));
        int want = it.n == null ? 1 : it.n;
        String name = new ItemStack(item).getHoverName().getString();
        int have = Bag.count(h, item);
        if (have > 0) {
            if (h.level() != p.level() || h.distanceTo(p) > 8) h.brain().mover().bringTo(p);
            final Item fi = item;
            int left = Math.min(want, have);
            int given = 0;
            if (h.tool().is(fi) && left > 0) {
                Bag.giveTo(p, h.tool().copy());
                given += h.tool().getCount();
                left -= h.tool().getCount();
                h.setTool(ItemStack.EMPTY);
            }
            for (int i = 0; i < h.bag.getContainerSize() && left > 0; i++) {
                ItemStack s = h.bag.getItem(i);
                if (!s.is(fi)) continue;
                int t = Math.min(left, s.getCount());
                Bag.giveTo(p, s.split(t));
                left -= t;
                given += t;
            }
            h.bag.setChanged();
            say(p, h, "นี่ครับ §e" + name + "§r " + given + " ชิ้น", "Here you go: §e" + given + " " + name);
            return;
        }
        if (Crafter.craftable(h.level(), item)) {
            say(p, h, "ผมไม่มี " + name + " อยู่ เดี๋ยวลองคราฟต์ให้นะ", "I don't have " + name + ", let me try to craft it");
            h.brain().tasks().start(Tasks.craft(it.item, want));
            return;
        }
        say(p, h, "ผมไม่มี §e" + name + "§r เลยครับ และคราฟต์เองไม่ได้ (ลองสั่ง \"หยิบ" + name + "จากหีบ\" ถ้ามีในหีบ)",
                "I don't have any §e" + name + "§r and can't craft it (try \"get " + name + " from the chest\")");
    }

    // ------------------------------------------------------------------ buildings
    static final Map<String, Integer> NEW_FLOORS = Map.of("hotel", 3, "hospital", 3, "prison", 1, "school", 2, "library", 2, "church", 1, "barn", 1, "market", 1, "statue", 1, "portal", 1);

    /** Last design + what the player said now; everything not said is chosen at random (so every building is different). */
    public static Gen.Opts mergeHouse(HelperEntity h, Nlu.HouseReq r) {
        Gen.Opts said = r.o;
        Gen.Opts last = h.brain().build().lastOpts();
        Gen.Opts o;
        if ((r.same || r.rel()) && last != null) {
            o = last.copy();
            if (!r.same) {
                o.variant = null;
                o.model = null;
            }
        } else {
            o = new Gen.Opts();
            o.light = true;
            o.furniture = true;
        }
        String type = said.type != null ? said.type : o.type;
        if (said.type != null && said.mat == null) o.mat = null;
        if (said.type != null || said.style != null || said.size != null) {
            o.w = null;
            o.d = null;
            o.height = null;
        }
        // what was said wins
        if (said.type != null) o.type = said.type;
        if (said.style != null) o.style = said.style;
        if (said.size != null) o.size = said.size;
        if (said.floors != null) o.floors = said.floors;
        if (said.wood != null) o.wood = said.wood;
        if (said.roof != null) o.roof = said.roof;
        if (said.mat != null) o.mat = said.mat;
        if (said.variant != null) o.variant = said.variant;
        if (said.model != null) o.model = said.model;
        if (said.w != null) o.w = said.w;
        if (said.d != null) o.d = said.d;
        if (said.height != null) o.height = said.height;
        if (said.light != null) o.light = said.light;
        if (said.furniture != null) o.furniture = said.furniture;
        if (said.porch != null) o.porch = said.porch;
        if (said.chimney != null) o.chimney = said.chimney;
        if (said.pool != null) o.pool = said.pool;
        if (said.floorsExact != null) o.floorsExact = said.floorsExact;
        if (said.speed != null) o.speed = said.speed == 0 ? 0 : said.speed == 1 ? 2 : 3;
        if (said.type == null && said.style != null) o.type = "standard";
        if (o.type == null) o.type = type == null ? "standard" : type;
        if (r.relSize != null && said.size == null) o.size = Math.max(0, Math.min(2, (o.size == null ? 1 : o.size) + r.relSize));
        if (r.relFloors != null && said.floors == null) o.floors = Math.max(1, Math.min(10, (o.floors == null ? 2 : o.floors) + r.relFloors));
        if (o.floors == null) o.floors = NEW_FLOORS.getOrDefault(o.type, o.type.equals("standard") ? 1 + h.getRandom().nextInt(2) : null);
        if (o.size == null && o.type.equals("standard")) o.size = h.getRandom().nextInt(3) == 0 ? 2 : 1;
        BuildJob.randomize(o, h.getRandom());
        return o;
    }

    public static String houseName(Gen.Opts o, boolean th) {
        String n = ExtraGen.typeName(o.type == null ? "standard" : o.type, th);
        String v = ExtraGen.variantName(o, th);
        if ("standard".equals(o.type) && o.style != null) n = HouseGen.STYLES[Math.max(0, Math.min(3, o.style))][th ? 0 : 1];
        return v == null ? n : n + " (" + v + ")";
    }

    static void cmdHouse(ServerPlayer p, HelperEntity h, Nlu.Intent it, String text) {
        Nlu.HouseReq r = it.house;
        Gen.Opts o = mergeHouse(h, r);
        if (p.level() != h.level() || p.distanceTo(h) > 48) h.brain().mover().bringTo(p);
        BuildJob.Site s = h.brain().build().begin(p, o, null, "");
        boolean th = th(p);
        if (!s.ok()) {
            say(p, h, "§cสร้างตรงนี้ไม่ได้:§r " + s.th(), "§cI can't build here:§r " + s.en());
            return;
        }
        StringBuilder saidTh = new StringBuilder(), saidEn = new StringBuilder();
        for (Nlu.L l : r.said) {
            saidTh.append(saidTh.length() > 0 ? ", " : "").append(l.th());
            saidEn.append(saidEn.length() > 0 ? ", " : "").append(l.en());
        }
        String size = o.size == null ? "" : " • " + HouseGen.SIZE_NAMES[Math.max(0, Math.min(2, o.size))][th ? 0 : 1];
        String floors = o.floors == null ? "" : th ? " • " + o.floors + " ชั้น" : " • " + o.floors + (o.floors == 1 ? " floor" : " floors");
        String wood = o.wood == null ? "" : " • " + HouseGen.WOODS[Math.max(0, Math.min(8, o.wood))][th ? 1 : 2];
        boolean free = h.brain().build().free();
        String need = free ? "" : th ? "\n§eใช้บล็อกจริงจากกระเป๋าผม/หีบใกล้ๆ§r ยังขาด:" + h.brain().build().missingText(true, 6) : "\n§eUses real blocks from my bag / nearby chests.§r Missing:" + h.brain().build().missingText(false, 6);
        say(p, h, "§aรับทราบ!§r กำลังสร้าง §d" + houseName(o, true) + "§r" + size + floors + wood + (saidTh.length() > 0 ? "\n§7(ที่คุณบอก: " + saidTh + ")" : "\n§7(ไม่ได้บอกรายละเอียด เลยสุ่มแบบให้ อยากได้แบบไหนบอกได้เลย เช่น \"บ้านโมเดิร์นหลังใหญ่ 3 ชั้น มีสระ\")") + need,
                "§aGot it!§r Building a §d" + houseName(o, false) + "§r" + size + floors + wood + (saidEn.length() > 0 ? "\n§7(you asked for: " + saidEn + ")" : "\n§7(no details given, so I picked a random design. Tell me what you like, e.g. \"a big modern house with 3 floors and a pool\")") + need);
        if ("portal".equals(o.type))
            say(p, h, "ประตูเนเธอร์ต้องใช้ของจริง: §eออบซิเดียน 10 ก้อน + หินเหล็กไฟ (Flint and Steel)§r ใส่ในกระเป๋าผมนะ", "A nether portal needs real items: §e10 obsidian + flint and steel§r in my bag");
        h.act("wave");
    }

    /** The building designer of the menu: what was chosen there, the rest random. */
    public static void buildFromMenu(ServerPlayer p, HelperEntity h, Gen.Opts chosen) {
        Gen.Opts o = chosen.copy();
        if (o.type == null) o.type = "standard";
        if (o.floors == null) o.floors = NEW_FLOORS.getOrDefault(o.type, 2);
        if (o.light == null) o.light = true;
        if (o.furniture == null) o.furniture = true;
        BuildJob.randomize(o, h.getRandom());
        if (p.level() != h.level() || p.distanceTo(h) > 48) h.brain().mover().bringTo(p);
        BuildJob.Site s = h.brain().build().begin(p, o, null, "");
        if (!s.ok()) {
            say(p, h, "§cสร้างตรงนี้ไม่ได้:§r " + s.th(), "§cI can't build here:§r " + s.en());
            return;
        }
        boolean free = h.brain().build().free();
        say(p, h, "§aรับทราบ!§r กำลังสร้าง §d" + houseName(o, true) + "§r ตรงหน้าคุณ (ดูกรอบสีเขียว)" + (free ? "" : "\n§eใช้บล็อกจริง§r ยังขาด:" + h.brain().build().missingText(true, 6)),
                "§aGot it!§r Building a §d" + houseName(o, false) + "§r in front of you (see the green outline)" + (free ? "" : "\n§eUses real blocks.§r Missing:" + h.brain().build().missingText(false, 6)));
        h.act("wave");
    }

    // ------------------------------------------------------------------ chains of orders
    static boolean tryPlanForced(ServerPlayer p, HelperEntity h, String text) {
        return tryPlan(p, h, text, true);
    }

    static boolean tryPlan(ServerPlayer p, HelperEntity h, String text) {
        return tryPlan(p, h, text, false);
    }

    static boolean tryPlan(ServerPlayer p, HelperEntity h, String text, boolean force) {
        List<String> pieces = Nlu.splitChain(text);
        if (pieces.isEmpty()) return false;
        List<Nlu.Intent> intents = new ArrayList<>();
        for (String x : pieces) intents.add(Nlu.parse(x));
        if (pieces.size() == 1 && !force) {
            Nlu.Intent it = intents.get(0);
            Nlu.Qty q = Nlu.extractQty(Nlu.norm(pieces.get(0)));
            boolean work = it.k.equals("chop") || it.k.equals("mine") || it.k.equals("gather") || it.k.equals("findFood");
            if (!PLAN_ONLY.contains(it.k) && !(work && (q.n() != null || q.full()))) return false;
        }
        boolean allQuery = true;
        for (Nlu.Intent it : intents) if (!QUERY.contains(it.k) && !it.k.equals("unknown")) allQuery = false;
        if (allQuery) return false;
        List<Plan.Step> steps = new ArrayList<>();
        boolean th = th(p);
        for (int i = 0; i < pieces.size(); i++) {
            Nlu.Intent it = intents.get(i);
            if (QUERY.contains(it.k)) {
                single(p, h, it, pieces.get(i), false);
                continue;
            }
            Object st = toStep(p, h, pieces.get(i), it, steps.size());
            if (st instanceof String[] err) say(p, h, "§eข้าม \"" + pieces.get(i) + "\":§r " + err[0], "§eSkipping \"" + pieces.get(i) + "\":§r " + err[1]);
            else if (st instanceof Plan.Step s) steps.add(s);
            else if (it.k.equals("unknown")) say(p, h, "ไม่เข้าใจ \"§f" + pieces.get(i) + "§r\" เลยข้ามไปนะ", "I didn't understand \"§f" + pieces.get(i) + "§r\", skipping it");
        }
        if (steps.isEmpty()) return true;
        h.brain().plan().start(steps);
        return true;
    }

    /** One order as a plan step: a Plan.Step, a String[]{th, en} error, or null. */
    @Nullable
    static Object toStep(ServerPlayer p, HelperEntity h, String piece, Nlu.Intent it, int index) {
        Nlu.Qty q = Nlu.extractQty(Nlu.norm(piece));
        boolean th = th(p);
        switch (it.k) {
            case "chop":
                return Plan.work("chop", q.n(), q.full());
            case "mine":
                return Plan.mine(it.ores, q.n(), q.full());
            case "gather":
                return Plan.gather(it.ids, it.block == null ? "บล็อก" : it.block.th(), it.block == null ? "blocks" : it.block.en(), q.n(), q.full(), th);
            case "findFood":
                return Plan.food(it.hunt, it.crops, it.animal == null ? null : it.animal[0], q.n(), q.full());
            case "dig":
                return Plan.dig(p, h, it.mine == null ? new Nlu.MineOpts() : it.mine.o(), index == 0);
            case "house": {
                Gen.Opts o = mergeHouse(h, it.house);
                BuildJob.Site s = h.brain().build().prepare(p, o, null, "");
                if (!s.ok() || s.job() == null) return new String[] {s.th(), s.en()};
                return Plan.house(houseName(o, true), houseName(o, false), BuildJob.savePrepared(s.job()));
            }
            case "come":
            case "rescue":
                return Plan.simple("come", "มาหาคุณ", "come to you");
            case "follow":
                return Plan.simple("follow", "เดินตามคุณ", "follow you");
            case "stay":
                return Plan.simple("stay", "รออยู่ตรงนั้น", "wait there");
            case "give":
                return Plan.simple("deliver", "เอาของมาให้คุณ", "bring you the items");
            case "deposit":
                return Plan.simple("deposit", "เก็บของเข้าหีบ", "put things in a chest");
            case "eat":
                return Plan.simple("eat", "กินอาหารในกระเป๋า", "eat something");
            case "cook":
                return Plan.simple("cook", "ทำอาหาร", "cook");
            case "guard":
                return Plan.simple("guard", "เฝ้าอยู่ตรงนั้น (สู้กับมอนสเตอร์)", "guard the spot (fight monsters)");
            case "fight":
                return Plan.fight(it.on);
            case "wait":
                return Plan.waitStep(it.secs == null ? 10 : it.secs);
            case "craft":
                return Plan.task(Tasks.craft(it.item, it.n == null ? 1 : it.n), "คราฟต์ " + itemName(it.item), "craft " + itemName(it.item));
            case "smelt":
                return Plan.task(Tasks.smelt(it.text, it.n), "ถลุงของ", "smelt");
            case "fetch":
                return Plan.task(Tasks.fetch(it.text, it.n), "หยิบของจากหีบ", "fetch from the chest");
            case "breed":
                return Plan.task(Tasks.breed(it.animal == null ? null : it.animal[0]), "ให้อาหารสัตว์ผสมพันธุ์", "breed animals");
            case "shear":
                return Plan.task(Tasks.spec("shear"), "ตัดขนแกะ", "shear sheep");
            case "torch":
                return Plan.task(Tasks.spec("torch"), "ปักคบเพลิง", "place torches");
            case "flatten":
                return Plan.task(Tasks.spec("flatten"), "ปรับพื้นที่", "flatten the ground");
            case "goto": {
                if (it.pos != null)
                    return Plan.gotoStep(new BlockPos(it.pos[0], it.pos[1], it.pos[2]), h.level().dimension().location().toString(),
                            "ไปที่ " + it.pos[0] + ", " + it.pos[1] + ", " + it.pos[2], "go to " + it.pos[0] + ", " + it.pos[1] + ", " + it.pos[2]);
                if (it.work) {
                    if (h.home() == null) return new String[] {"ผมยังไม่มีที่ทำงานเลย", "I have no work spot yet"};
                    return Plan.gotoStep(h.home(), h.level().dimension().location().toString(), "กลับไปที่ทำงาน", "go back to work");
                }
                BlockPos bed = p.getRespawnPosition();
                if (bed != null) return Plan.gotoStep(bed, p.getRespawnDimension().location().toString(), "กลับบ้าน (เตียงของคุณ)", "go home (your bed)");
                ServerLevel ow = p.server.overworld();
                return Plan.gotoStep(ow.getSharedSpawnPos(), ow.dimension().location().toString(), "กลับจุดเกิดของโลก", "go to the world spawn");
            }
            default:
                return null;
        }
    }

    static String itemName(String id) {
        if (id == null) return "?";
        return new ItemStack(BuiltInRegistries.ITEM.get(HelperBuddy.rl(id))).getHoverName().getString();
    }

    // ------------------------------------------------------------------ texts
    public static String levelText(HelperEntity h, boolean th) {
        int lv = h.helperLevel();
        float xp = h.xp();
        int[] need = {0, 150, 450, 1000};
        String next = lv >= 4 ? (th ? "เลเวลสูงสุดแล้ว" : "max level") : (th ? "อีก " + (int) Math.ceil(need[lv] - xp) + " XP ถึงเลเวล " + (lv + 1) : (int) Math.ceil(need[lv] - xp) + " XP to level " + (lv + 1));
        return th ? "§bเลเวล " + lv + "§r (XP " + (int) xp + ", " + next + ") เลือดสูงสุด " + h.maxHp() + "\n§7ได้ XP จากการขุด ตัดไม้ สร้าง และสู้ ยิ่งเลเวลสูงยิ่งเลือดเยอะ ทำงานเร็ว ตีแรง"
                : "§bLevel " + lv + "§r (XP " + (int) xp + ", " + next + ") max health " + h.maxHp() + "\n§7I get XP from mining, chopping, building and fighting. Higher levels: more health, faster work, harder hits";
    }

    public static void status(ServerPlayer p, HelperEntity h) {
        boolean th = th(p);
        List<String> l = new ArrayList<>();
        l.add((th ? "§fสถานะ: " : "§fDoing: ") + h.mode().label(th) + (h.isKO() ? (th ? " §c(สลบ)" : " §c(knocked out)") : "") + (h.status().isEmpty() ? "" : " §7" + h.status()));
        l.add((th ? "§fเลือด: " : "§fHealth: ") + (int) Math.ceil(h.shownHp()) + "/" + h.maxHp() + (th ? " §bเลเวล " : " §bLevel ") + h.helperLevel());
        BlockPos b = h.blockPosition();
        String dim = h.level().dimension().location().getPath();
        String where = (th ? "§fอยู่ที่: §7" : "§fAt: §7") + b.getX() + ", " + b.getY() + ", " + b.getZ() + " (" + dim + ")";
        if (p.level() == h.level()) where += th ? " ห่างจากคุณ " + Math.round(p.distanceTo(h)) + " บล็อก" : ", " + Math.round(p.distanceTo(h)) + " blocks from you";
        else where += th ? " §e(คนละมิติกับคุณ)" : " §e(another dimension)";
        l.add(where);
        ItemStack tool = h.tool();
        l.add((th ? "§fถืออยู่: " : "§fHolding: ") + (tool.isEmpty() ? (th ? "§7ไม่มี" : "§7nothing") : "§b" + tool.getHoverName().getString()
                + (tool.isDamageableItem() ? " §7" + (tool.getMaxDamage() - tool.getDamageValue()) + "/" + tool.getMaxDamage() : "")));
        int used = Bag.usedSlots(h);
        l.add((th ? "§fกระเป๋า: " : "§fBag: ") + used + "/" + h.bag.getContainerSize() + (used >= h.bag.getContainerSize() ? (th ? " §c(เต็ม)" : " §c(full)") : ""));
        if (h.brain().build().active())
            l.add((th ? "§fงานสร้าง: §d" : "§fBuilding: §d") + h.brain().build().name(th) + "§r " + h.brain().build().progress() + "%" + (h.mode() != Mode.BUILD ? (th ? " §7(หยุดไว้)" : " §7(paused)") : ""));
        if (h.brain().dig().active()) l.add((th ? "§fงานสร้างเหมือง: §8" : "§fMine: §8") + h.brain().dig().statusShort() + (h.mode() != Mode.DIG ? (th ? " §7(หยุดไว้)" : " §7(paused)") : ""));
        if (h.brain().plan().active()) l.add((th ? "§fงานที่สั่งไว้:\n" : "§fOrders:\n") + h.brain().plan().summary(th));
        l.add((th ? "§fช่วยต่อสู้: " : "§fFighting: ") + (h.settings.get("fight") ? (th ? "§aเปิด" : "§aon") : (th ? "§7ปิด" : "§7off"))
                + (th ? " §fสู้กลับ: " : " §fFight back: ") + (h.settings.get("fightBack") ? (th ? "§aเปิด" : "§aon") : (th ? "§7ปิด" : "§7off")));
        if (h.brain().combat().fighting()) l.add((th ? "§cกำลังสู้กับ " : "§cFighting ") + h.brain().combat().target().getName().getString());
        p.sendSystemMessage(Component.literal("§6[§e" + h.helperName() + "§6]§r\n" + String.join("\n", l)));
    }

    public static void help(ServerPlayer p) {
        boolean th = th(p);
        String[] lines = th ? new String[] {
                "§l§6สิ่งที่สั่งผมได้ (พิมพ์เป็นประโยคธรรมดาได้เลย ขึ้นต้นด้วย ! หรือชื่อผม หรือใช้ /helper):§r",
                "§e• ไปขุดแร่ §7| ขุดเฉพาะเพชรกับเหล็ก | ขุดเหล็ก 10 ก้อน | ขุดทุกแร่",
                "§e• ไปตัดไม้ §7| ตัดไม้ 30 ท่อน | ตัดไม้ 5 ต้น",
                "§e• เก็บบล็อก §7| ขุดทราย 2 กอง | เก็บไม้ไผ่ 64 | เก็บอ้อย | ขุดหิน | ดินเหนียว",
                "§e• หาอาหาร §7| ล่าวัว 3 ตัว | เก็บเกี่ยวผัก | ทำฟาร์ม | ผสมพันธุ์วัว | ตัดขนแกะ | ทำอาหาร",
                "§e• คราฟต์ / ถลุง §7| คราฟต์โต๊ะคราฟ | ทำคบเพลิง 16 อัน | ถลุงเหล็ก | ขอดาบเพชร | หยิบเหล็กจากหีบ",
                "§e• สร้างเหมือง §7เช่น \"สร้างเหมือง ลึก 40 ยาว 30\"",
                "§e• สร้าง §7บ้าน (สุ่มแบบใหม่ทุกครั้ง) โรงแรม โรงเรียน โรงพยาบาล คุก ห้องสมุด โบสถ์ โรงนา ตลาด ปราสาท หอคอย บ้านต้นไม้ อิกลู ปิรามิด บังเกอร์ รูปปั้น ประตูเนเธอร์"
                        + (AiBridge.enabled() && HBConfig.AI_FOR_BUILDING.get() ? " และอะไรก็ได้ที่ AI ออกแบบให้ (เช่น \"สร้างรถ\")" : ""),
                "§e• มานี่ §7| §eตามมา §7| §eรอตรงนั้น §7| §eรอ 30 วินาที §7| §eไปที่ 100 64 -50 §7| §eกลับบ้าน §7| §eปักคบเพลิง §7| §eปรับพื้นที่",
                "§e• เอาของมาให้ §7| §eเก็บเข้าหีบ §7| §eช่วยสู้ §7| §eไม่ต้องสู้ §7| §eไม่ต้องสู้กลับ §7| §eเฝ้าตรงนี้ §7| §eช่วยด้วย!",
                "§e• สถานะ §7| §eเลเวล §7| §eทำต่อ §7| §eยกเลิก §7| §eเปลี่ยนชื่อเป็น ...",
                "§6§lสั่งหลายอย่างต่อกันได้§r ด้วย §eแล้ว / จากนั้น§r เช่น §7\"ตัดไม้ 20 ท่อน แล้วขุดหิน 64 ก้อน แล้วเอามาให้\""}
                : new String[] {
                "§l§6What you can tell me (plain sentences; start with ! or my name, or use /helper):§r",
                "§e• go mining §7| mine only diamonds and iron | mine 10 iron | mine all ores",
                "§e• chop wood §7| chop 30 logs | chop 5 trees",
                "§e• collect blocks §7| dig 2 stacks of sand | get 64 bamboo | sugar cane | stone | clay",
                "§e• find food §7| hunt 3 cows | harvest crops | farm | breed cows | shear sheep | cook",
                "§e• craft / smelt §7| craft a crafting table | make 16 torches | smelt iron | give me a diamond sword | get iron from the chest",
                "§e• build a mine §7e.g. \"build a mine 40 deep 30 long\"",
                "§e• build §7a house (random design every time), hotel, school, hospital, prison, library, church, barn, market, castle, tower, tree house, igloo, pyramid, bunker, statue, nether portal"
                        + (AiBridge.enabled() && HBConfig.AI_FOR_BUILDING.get() ? " and anything the AI can design (e.g. \"build a car\")" : ""),
                "§e• come §7| §efollow me §7| §estay §7| §ewait 30 seconds §7| §ego to 100 64 -50 §7| §ego home §7| §eplace torches §7| §eflatten the ground",
                "§e• bring me the items §7| §eput them in the chest §7| §efight §7| §edon't fight §7| §edon't fight back §7| §eguard here §7| §ehelp me!",
                "§e• status §7| §elevel §7| §eresume §7| §ecancel §7| §eyour name is ...",
                "§6§lChain orders§r with §ethen§r: §7\"chop 20 logs then mine 64 stone then bring them to me\""};
        p.sendSystemMessage(Component.literal(String.join("\n", lines)));
    }

    // ------------------------------------------------------------------ AI: understanding, chatting, designing
    static final String AI_COMMANDS = "You turn a Minecraft player's message to their helper into commands the helper understands. "
            + "Reply with JSON only: {\"commands\":[\"...\"],\"reply\":\"...\"}. Commands must be short English orders from this list (fill in the details): "
            + "\"mine\", \"mine diamonds\", \"mine 10 iron\", \"chop wood\", \"chop 20 logs\", \"collect 64 sand\" (any block: sand, gravel, clay, stone, dirt, bamboo, sugar cane, cactus, flowers, leaves, snow, ice, obsidian...), "
            + "\"find food\", \"hunt 3 cows\", \"harvest crops\", \"farm\", \"breed cows\", \"shear sheep\", \"cook\", \"eat\", \"craft <item> <n>\", \"smelt iron\", \"get <item> from the chest\", "
            + "\"give me <item>\", \"bring me the items\", \"put things in the chest\", \"build a mine 40 deep\", "
            + "\"build a <house|hotel|school|hospital|prison|library|church|barn|market|castle|tower|tree house|igloo|pyramid|bunker|mansion|lighthouse|desert house|nether fort|safe house|box house|nether portal|creeper statue> [big|small] [3 floors] [with a pool] [modern|medieval|thai]\", "
            + "\"build <anything else>\" (the helper designs it), \"come\", \"follow me\", \"stay\", \"wait 30 seconds\", \"go to x y z\", \"go home\", \"guard\", \"fight\", \"don't fight\", \"don't fight back\", "
            + "\"place torches\", \"flatten the ground\", \"status\", \"cancel\". "
            + "Several commands run one after another. If the message is just chat or a question, give no commands and answer in \"reply\" in the player's language "
            + "(Thai if they wrote Thai), as a cheerful helper, one or two short sentences.";

    /** Recent conversation per helper, so the AI understands follow-ups ("more", "the other one", "yes"). */
    static final Map<UUID, List<AiBridge.Msg>> HISTORY = new java.util.HashMap<>();
    static final java.util.Set<UUID> AI_FAIL_TOLD = new java.util.HashSet<>();

    /** fallbackLocal: if the AI can't be reached, let the built-in brain handle the sentence instead. */
    static void aiUnderstand(ServerPlayer p, HelperEntity h, String text, boolean fallbackLocal) {
        MinecraftServer server = p.server;
        UUID hid = h.getUUID();
        say(p, h, "§7(กำลังคิด...)", "§7(thinking...)");
        String sys = AI_COMMANDS + " The helper is called " + h.helperName() + ". Helper status: " + h.mode().label(false) + ", health " + (int) h.shownHp() + "/" + h.maxHp()
                + ", level " + h.helperLevel() + ". Use the earlier messages to understand follow-ups.";
        List<AiBridge.Msg> hist = HISTORY.computeIfAbsent(hid, k -> new ArrayList<>());
        List<AiBridge.Msg> msgs = new ArrayList<>(hist);
        msgs.add(new AiBridge.Msg("user", text));
        AiBridge.send(sys, msgs, 1500, 0.2).thenAccept(res -> server.execute(() -> {
            ServerPlayer pl = server.getPlayerList().getPlayer(p.getUUID());
            HelperEntity hh = Helpers.byUuid(server, hid);
            if (pl == null || hh == null) return;
            JsonObject o = AiBridge.jsonIn(res);
            if (o == null) {
                if (res == null && AI_FAIL_TOLD.add(pl.getUUID()))
                    pl.sendSystemMessage(Component.literal(Say.t(pl, "§7[Helper Buddy] เชื่อมต่อ AI ไม่ได้ (เช็ก apiKey ในไฟล์ config หรือดูรายละเอียดใน logs/latest.log) ตอนนี้ใช้สมองในตัวแทน",
                            "§7[Helper Buddy] The AI could not be reached (check apiKey in the config file, details in logs/latest.log). Using the built-in brain")));
                if (fallbackLocal) {
                    local(pl, hh, text, false);
                    return;
                }
                say(pl, hh, "ขอโทษครับ ผมไม่แน่ใจว่าหมายถึงอะไร ลองพูดแบบอื่น หรือพิมพ์ \"§eช่วย§r\" ดูตัวอย่างนะ", "Sorry, I'm not sure what you mean. Try another way, or type \"§ehelp§r\"");
                return;
            }
            List<String> cmds = new ArrayList<>();
            if (o.has("commands") && o.get("commands").isJsonArray())
                for (JsonElement e : o.getAsJsonArray("commands")) if (e.isJsonPrimitive()) cmds.add(e.getAsString());
            String reply = o.has("reply") && o.get("reply").isJsonPrimitive() ? AiBridge.clean(o.get("reply").getAsString()) : null;
            if (reply != null && !reply.isEmpty()) say(pl, hh, reply, reply);
            hist.add(new AiBridge.Msg("user", text));
            hist.add(new AiBridge.Msg("assistant", o.toString()));
            while (hist.size() > 10) hist.remove(0);
            if (cmds.isEmpty()) return;
            String joined = String.join(" then ", cmds);
            boolean any = false;
            for (String c : cmds) if (!Nlu.parse(c).k.equals("unknown")) any = true;
            if (!any) {
                // the AI answered with orders the helper doesn't know: try the player's own words with the built-in brain
                if (reply == null || reply.isEmpty()) local(pl, hh, text, false);
                return;
            }
            LAST_ORDER.put(hid, joined);
            if (cmds.size() == 1) {
                Nlu.Intent it = Nlu.parse(cmds.get(0));
                if (tryPlan(pl, hh, cmds.get(0))) return;
                single(pl, hh, it, cmds.get(0), false);
            } else tryPlan(pl, hh, joined, true);
        }));
    }

    static final String AI_BUILD = "You design small Minecraft (Java 1.20.1) builds for a helper to place. Reply with JSON only: "
            + "{\"name\":\"short name in the player's language\",\"boxes\":[{\"from\":[x,y,z],\"to\":[x,y,z],\"block\":\"minecraft:block_id\",\"hollow\":false}]}. "
            + "Coordinates are blocks: x = left/right, y = up (0 = ground level, nothing below 0), z = away from the player (the front faces z = -1). "
            + "Every coordinate must be between 0 and MAX-1. Use real Java block ids (stone_bricks, oak_planks, glass, white_wool, red_concrete, black_concrete, quartz_block, iron_block, ...). "
            + "Boxes are filled; \"hollow\":true leaves the inside empty (walls only). Later boxes overwrite earlier ones, so carve doors and windows with \"minecraft:air\" boxes. "
            + "Use 5 to 60 boxes. Make it recognisable and detailed.";

    public static void aiBuild(ServerPlayer p, HelperEntity h, String text) {
        if (!AiBridge.enabled() || !HBConfig.AI_FOR_BUILDING.get()) {
            say(p, h, "ผมยังไม่รู้จักวิธีสร้าง \"" + text + "\" ครับ ที่สร้างได้: บ้าน โรงแรม โรงเรียน โรงพยาบาล คุก ห้องสมุด โบสถ์ โรงนา ตลาด ปราสาท หอคอย บ้านต้นไม้ อิกลู ปิรามิด บังเกอร์ รูปปั้น (ครีปเปอร์ สตีฟ ซอมบี้ หมู แมว หัวใจ ดาบ ต้นไม้ เพชร เห็ด ดาว มนุษย์หิมะ) และประตูเนเธอร์\n§7(ถ้าตั้งค่า AI ไว้ในไฟล์ config ผมจะออกแบบอะไรก็ได้ให้)",
                    "I don't know how to build \"" + text + "\" yet. I can build: houses, hotels, schools, hospitals, prisons, libraries, churches, barns, markets, castles, towers, tree houses, igloos, pyramids, bunkers, statues (creeper, Steve, zombie, pig, cat, heart, sword, tree, diamond, mushroom, star, snowman) and nether portals\n§7(set up an AI in the config file and I can design anything)");
            return;
        }
        int max = Math.max(8, Math.min(48, HBConfig.AI_MAX_BUILD.get()));
        MinecraftServer server = p.server;
        UUID hid = h.getUUID();
        say(p, h, "§7(กำลังออกแบบ \"" + text + "\"...)", "§7(designing \"" + text + "\"...)");
        AiBridge.send(AI_BUILD.replace("MAX", String.valueOf(max)), List.of(new AiBridge.Msg("user", "Build: " + text)), 4000, 0.4).thenAccept(res -> server.execute(() -> {
            ServerPlayer pl = server.getPlayerList().getPlayer(p.getUUID());
            HelperEntity hh = Helpers.byUuid(server, hid);
            if (pl == null || hh == null) return;
            JsonObject o = AiBridge.jsonIn(res);
            List<BuildJob.CustomBlock> blocks = o == null ? null : designToBlocks(o, max);
            if (blocks == null || blocks.isEmpty()) {
                say(pl, hh, "ขอโทษครับ ออกแบบไม่สำเร็จ ลองอธิบายใหม่อีกทีนะ", "Sorry, the design didn't work out. Try describing it again");
                return;
            }
            String name = o.has("name") ? AiBridge.clean(o.get("name").getAsString()) : text;
            if (pl.level() != hh.level() || pl.distanceTo(hh) > 48) hh.brain().mover().bringTo(pl);
            BuildJob.Site s = hh.brain().build().begin(pl, null, blocks, name == null ? text : name);
            if (!s.ok()) say(pl, hh, "§cสร้างตรงนี้ไม่ได้:§r " + s.th(), "§cI can't build here:§r " + s.en());
            else say(pl, hh, "§aออกแบบเสร็จแล้ว!§r กำลังสร้าง §d" + name + "§r (" + blocks.size() + " บล็อก) ตรงหน้าคุณ", "§aDesigned!§r Building §d" + name + "§r (" + blocks.size() + " blocks) in front of you");
        }));
    }

    /** Expands the AI's boxes (or raw blocks) into a block list, keeping only real blocks inside the size limit. */
    static List<BuildJob.CustomBlock> designToBlocks(JsonObject o, int max) {
        Map<Long, BuildJob.CustomBlock> cells = new java.util.LinkedHashMap<>();
        int total = 0;
        if (o.has("boxes") && o.get("boxes").isJsonArray()) {
            for (JsonElement e : o.getAsJsonArray("boxes")) {
                if (!e.isJsonObject()) continue;
                JsonObject b = e.getAsJsonObject();
                int[] f = vec(b.get("from")), t = vec(b.get("to"));
                if (f == null) continue;
                if (t == null) t = f;
                String id = b.has("block") ? b.get("block").getAsString() : "minecraft:stone";
                boolean air = id.endsWith(":air") || id.equals("air");
                if (!air && !blockExists(id)) id = "minecraft:stone";
                boolean hollow = b.has("hollow") && b.get("hollow").getAsBoolean();
                int x0 = clamp(Math.min(f[0], t[0]), max), x1 = clamp(Math.max(f[0], t[0]), max);
                int y0 = clamp(Math.min(f[1], t[1]), max), y1 = clamp(Math.max(f[1], t[1]), max);
                int z0 = clamp(Math.min(f[2], t[2]), max), z1 = clamp(Math.max(f[2], t[2]), max);
                for (int x = x0; x <= x1; x++)
                    for (int y = y0; y <= y1; y++)
                        for (int z = z0; z <= z1; z++) {
                            if (hollow && x > x0 && x < x1 && y > y0 && y < y1 && z > z0 && z < z1) continue;
                            long k = Gen.key(x, y, z);
                            if (air) cells.remove(k);
                            else cells.put(k, new BuildJob.CustomBlock(x, y, z, id, null, null, null));
                            if (++total > 200000) return new ArrayList<>(cells.values());
                        }
            }
        }
        if (o.has("blocks") && o.get("blocks").isJsonArray()) {
            for (JsonElement e : o.getAsJsonArray("blocks")) {
                if (!e.isJsonArray()) continue;
                JsonArray a = e.getAsJsonArray();
                if (a.size() < 4) continue;
                int x = clamp(a.get(0).getAsInt(), max), y = clamp(a.get(1).getAsInt(), max), z = clamp(a.get(2).getAsInt(), max);
                String id = a.get(3).getAsString();
                if (!blockExists(id)) id = "minecraft:stone";
                cells.put(Gen.key(x, y, z), new BuildJob.CustomBlock(x, y, z, id, null, null, null));
            }
        }
        return new ArrayList<>(cells.values());
    }

    static int clamp(int v, int max) {
        return Math.max(0, Math.min(max - 1, v));
    }

    @Nullable
    static int[] vec(JsonElement e) {
        if (e == null || !e.isJsonArray() || e.getAsJsonArray().size() < 3) return null;
        JsonArray a = e.getAsJsonArray();
        try {
            return new int[] {a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt()};
        } catch (Exception ex) {
            return null;
        }
    }

    static boolean blockExists(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id.contains(":") ? id : "minecraft:" + id);
        return rl != null && BuiltInRegistries.BLOCK.containsKey(rl);
    }

    /** AI chat when the built-in brain had nothing to say (used by the talk screen's chat). */
    public static void aiChat(ServerPlayer p, HelperEntity h, String text) {
        MinecraftServer server = p.server;
        UUID hid = h.getUUID();
        String sys = "You are " + h.helperName() + ", a cheerful, loyal Minecraft helper of " + p.getGameProfile().getName() + ". Answer in the player's language (Thai if they wrote Thai), "
                + "one or two short sentences, no markdown. You are " + h.mode().label(false) + ", level " + h.helperLevel() + ".";
        AiBridge.chat(sys, List.of(new AiBridge.Msg("user", text)), 800, 0.8).thenAccept(r -> server.execute(() -> {
            ServerPlayer pl = server.getPlayerList().getPlayer(p.getUUID());
            HelperEntity hh = Helpers.byUuid(server, hid);
            if (pl != null && hh != null && r != null) say(pl, hh, r, r);
        }));
    }
}
