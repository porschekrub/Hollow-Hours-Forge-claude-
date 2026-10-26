package com.hollowhours.ghost;

import com.hollowhours.config.HHConfig;
import com.hollowhours.entity.GhostEntity;
import com.hollowhours.entity.GhostKind;
import com.hollowhours.util.Fx;
import com.hollowhours.util.Later;
import com.hollowhours.util.R;
import com.hollowhours.util.W;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Asking ghosts for items, and the cursed items they give (port of curse.js).
 * - 3 requests per player per ghost visit, 50% it gives. Every request makes the NEXT ghost stronger.
 * - What you get is cursed: 12.5% better than normal, but every use triggers its curse (each item has its own),
 *   drains your soul, and items crafted from cursed items are cursed too.
 */
public final class Curses {
    private Curses() {}

    public record Buff(double dur, double vis, double dmg, double spd) {}

    static final String TAG = "hollowhours_curse";

    // ------------------------------------------------------------------ the buff pool for the next ghost
    public static final class BuffData extends SavedData {
        double pool;

        static BuffData load(CompoundTag t) {
            BuffData d = new BuffData();
            d.pool = t.getDouble("pool");
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag t) {
            t.putDouble("pool", pool);
            return t;
        }
    }

    static BuffData buffData(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(BuffData::load, BuffData::new, "hollowhours_buff");
    }

    static final String[] RARE = {"diamond", "netherite", "emerald", "nether_star", "elytra", "totem", "beacon", "dragon", "trident", "heart_of_the_sea",
            "ancient_debris", "enchanted_golden_apple", "shulker", "echo_shard", "wither", "conduit", "smithing_template", "end_crystal", "sponge", "spawn_egg"};
    static final String[] VERY_RARE = {"netherite", "nether_star", "elytra", "totem", "beacon", "dragon", "enchanted_golden_apple", "heart_of_the_sea", "conduit", "end_crystal"};

    static boolean isRare(String id) {
        for (String r : RARE) if (id.contains(r)) return true;
        return false;
    }

    static int potency(String id) {
        for (String r : VERY_RARE) if (id.contains(r)) return 3;
        return isRare(id) ? 2 : 1;
    }

    static void addBuff(ServerLevel level, String id) {
        BuffData d = buffData(level);
        d.pool = Math.min(2.0, d.pool + (isRare(id) ? 0.10 : 0.05));
        d.setDirty();
    }

    @Nullable
    public static Buff takeBuff(ServerLevel level) {
        BuffData d = buffData(level);
        double x = d.pool;
        if (!(x > 0)) return null;
        d.pool = 0;
        d.setDirty();
        double m = Math.min(x, 2.0);
        return new Buff(1 + m, 1 + m, m, 1 + m);
    }

    // ------------------------------------------------------------------ cursed item data
    public static boolean isCursed(ItemStack it) {
        return !it.isEmpty() && it.getTag() != null && it.getTag().contains(TAG);
    }

    static CompoundTag cdata(ItemStack it) {
        return it.getOrCreateTag().getCompound(TAG);
    }

    static String key(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).getPath();
    }

    static final Map<String, Integer> CAT_USES = Map.ofEntries(Map.entry("sword", 30), Map.entry("axe", 30), Map.entry("pickaxe", 40), Map.entry("shovel", 35),
            Map.entry("hoe", 35), Map.entry("bow", 25), Map.entry("crossbow", 25), Map.entry("trident", 20), Map.entry("shears", 30), Map.entry("flint", 20),
            Map.entry("rod", 25), Map.entry("shield", 25), Map.entry("helmet", 25), Map.entry("chest", 25), Map.entry("legs", 25), Map.entry("boots", 25), Map.entry("elytra", 25));

    static String category(String s) {
        if (s.endsWith("_sword")) return "sword";
        if (s.endsWith("_pickaxe")) return "pickaxe";
        if (s.endsWith("_axe")) return "axe";
        if (s.endsWith("_shovel")) return "shovel";
        if (s.endsWith("_hoe")) return "hoe";
        if (s.endsWith("_helmet")) return "helmet";
        if (s.endsWith("_chestplate")) return "chest";
        if (s.endsWith("_leggings")) return "legs";
        if (s.endsWith("_boots")) return "boots";
        if (s.equals("bow") || s.equals("crossbow") || s.equals("trident") || s.equals("shears") || s.equals("shield") || s.equals("elytra")) return s;
        if (s.equals("flint_and_steel")) return "flint";
        if (s.equals("fishing_rod") || s.endsWith("_on_a_stick")) return "rod";
        if (s.endsWith("bucket")) return "bucket";
        if (s.endsWith("_bed")) return "bed";
        return "misc";
    }

    static final Map<String, Map<String, String>> TOOL_CURSE = Map.of(
            "sword", Map.of("wooden", "rot", "stone", "stun", "iron", "blood", "golden", "xp", "diamond", "hollow", "netherite", "pact"),
            "axe", Map.of("wooden", "web", "stone", "tremor", "iron", "bleed", "golden", "thief", "diamond", "freeze", "netherite", "pursuit"),
            "pickaxe", Map.of("wooden", "worms", "stone", "starve", "iron", "rust", "golden", "foodrot", "diamond", "eatlight", "netherite", "doom"),
            "shovel", Map.of("wooden", "blind", "stone", "web", "iron", "weight", "golden", "scramble", "diamond", "echo", "netherite", "summon"),
            "hoe", Map.of("wooden", "rot", "stone", "worms", "iron", "coldhands", "golden", "xp", "diamond", "phantoms", "netherite", "hollow"));
    static final Map<String, Map<String, String>> ARMOR_CURSE = Map.of(
            "helmet", Map.of("leather", "turn", "chainmail", "echo", "iron", "blind", "golden", "ghostlight", "diamond", "nightmare", "netherite", "hollow", "turtle", "drown"),
            "chest", Map.of("leather", "freeze", "chainmail", "fragile", "iron", "weight", "golden", "pact", "diamond", "bleed", "netherite", "doom"),
            "legs", Map.of("leather", "stun", "chainmail", "tremor", "iron", "weight", "golden", "scramble", "diamond", "fragile", "netherite", "pursuit"),
            "boots", Map.of("leather", "sky", "chainmail", "web", "iron", "stun", "golden", "warp", "diamond", "sky", "netherite", "call"));
    static final Map<String, String> SIGNATURE = Map.ofEntries(Map.entry("bow", "burn"), Map.entry("crossbow", "recoil"), Map.entry("trident", "lightning"),
            Map.entry("shield", "scramble"), Map.entry("fishing_rod", "thief"), Map.entry("carrot_on_a_stick", "thief"), Map.entry("warped_fungus_on_a_stick", "burn"),
            Map.entry("flint_and_steel", "wither"), Map.entry("shears", "xp"), Map.entry("elytra", "ghostlight"), Map.entry("totem_of_undying", "doom"),
            Map.entry("ender_pearl", "warp"), Map.entry("ender_eye", "echo"), Map.entry("golden_apple", "hollow"), Map.entry("enchanted_golden_apple", "pact"),
            Map.entry("bucket", "drown"), Map.entry("water_bucket", "drown"), Map.entry("lava_bucket", "burn"), Map.entry("milk_bucket", "hollow"),
            Map.entry("powder_snow_bucket", "freeze"), Map.entry("torch", "eatlight"), Map.entry("soul_torch", "hollow"), Map.entry("lantern", "eatlight"),
            Map.entry("compass", "echo"), Map.entry("recovery_compass", "doom"), Map.entry("clock", "doom"), Map.entry("spyglass", "nightmare"),
            Map.entry("firework_rocket", "sky"), Map.entry("snowball", "freeze"), Map.entry("egg", "worms"), Map.entry("experience_bottle", "xp"),
            Map.entry("fire_charge", "burn"), Map.entry("bread", "starve"), Map.entry("cooked_beef", "rot"), Map.entry("beef", "rot"),
            Map.entry("cooked_porkchop", "worms"), Map.entry("cooked_chicken", "worms"), Map.entry("apple", "worms"), Map.entry("golden_carrot", "blind"),
            Map.entry("carrot", "starve"), Map.entry("baked_potato", "starve"), Map.entry("cookie", "foodrot"), Map.entry("pumpkin_pie", "nightmare"),
            Map.entry("melon_slice", "rot"), Map.entry("sweet_berries", "bleed"), Map.entry("rotten_flesh", "rot"), Map.entry("spider_eye", "wither"),
            Map.entry("cooked_salmon", "drown"), Map.entry("cooked_cod", "drown"), Map.entry("mushroom_stew", "hollow"), Map.entry("honey_bottle", "web"),
            Map.entry("dried_kelp", "drown"), Map.entry("potion", "nightmare"), Map.entry("tnt", "recoil"), Map.entry("obsidian", "doom"),
            Map.entry("crafting_table", "scramble"), Map.entry("chest", "thief"), Map.entry("furnace", "burn"), Map.entry("anvil", "weight"),
            Map.entry("jukebox", "echo"), Map.entry("ladder", "sky"), Map.entry("oak_door", "call"), Map.entry("enchanting_table", "pact"),
            Map.entry("name_tag", "pursuit"), Map.entry("lead", "web"), Map.entry("saddle", "warp"), Map.entry("map", "echo"), Map.entry("closet_item", "nightmare"));
    static final String[] MISC_POOL = {"call", "foodrot", "summon", "strip", "scramble", "echo", "worms", "web", "ghostlight", "tremor", "eatlight", "bleed",
            "phantoms", "vex", "coldhands", "warp", "weight", "fragile", "pursuit", "hollow"};

    static int hash(String s) {
        int h = 7;
        for (int i = 0; i < s.length(); i++) h = h * 31 + s.charAt(i);
        return Math.abs(h);
    }

    static String curseFor(String s, String cat) {
        if (SIGNATURE.containsKey(s)) return SIGNATURE.get(s);
        Matcher m = Pattern.compile("^(wooden|stone|iron|golden|diamond|netherite)_(sword|axe|pickaxe|shovel|hoe)$").matcher(s);
        if (m.find() && TOOL_CURSE.containsKey(m.group(2))) return TOOL_CURSE.get(m.group(2)).get(m.group(1));
        m = Pattern.compile("^(leather|chainmail|iron|golden|diamond|netherite|turtle)_(helmet|chestplate|leggings|boots)$").matcher(s);
        if (m.find()) {
            String part = switch (m.group(2)) {
                case "chestplate" -> "chest";
                case "leggings" -> "legs";
                default -> m.group(2);
            };
            String c = ARMOR_CURSE.get(part).get(m.group(1));
            if (c != null) return c;
        }
        if (s.endsWith("_bed")) return "nightmare";
        if (s.matches(".*(_log|_planks|_wood)$")) return new String[]{"web", "worms", "call"}[hash(s) % 3];
        if (s.matches(".*(ore|ingot|_block)$") || s.startsWith("raw_")) return new String[]{"rust", "weight", "doom", "thief"}[hash(s) % 4];
        return MISC_POOL[hash(s) % MISC_POOL.length];
    }

    static final String GIB = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ#$%&*@!?+=-_<>~^";

    public static String gib(int len, long seed) {
        java.util.Random r = new java.util.Random(seed);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) sb.append(GIB.charAt(r.nextInt(GIB.length())));
        return sb.toString();
    }

    public static ItemStack makeCursed(Item item, int count, int ghostN) {
        ItemStack it = new ItemStack(item, count);
        curse(it, ghostN);
        return it;
    }

    /** Turns any stack into a cursed one (keeps its count). */
    public static void curse(ItemStack it, int ghostN) {
        String s = key(it.getItem());
        String cat = category(s);
        CompoundTag c = new CompoundTag();
        String dr = curseFor(s, cat);
        int uses = CAT_USES.getOrDefault(cat, it.isDamageableItem() ? 30 : 0);
        c.putString("dr", dr);
        c.putInt("n", uses);
        c.putInt("N", uses);
        c.putInt("mark", ghostN);
        it.getOrCreateTag().put(TAG, c);
        it.setHoverName(Component.literal(gib(12, s.hashCode())).withStyle(ChatFormatting.RED, ChatFormatting.OBFUSCATED));
        writeLore(it);
    }

    static void writeLore(ItemStack it) {
        CompoundTag c = cdata(it);
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("hollowhours.curse.mark").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
        if (c.getInt("N") > 0) lines.add(Component.translatable("hollowhours.curse.uses", c.getInt("n"), c.getInt("N")).withStyle(ChatFormatting.DARK_GRAY));
        lines.add(Component.translatable("hollowhours.curse.name", Component.translatable("hollowhours.curse." + c.getString("dr"))).withStyle(ChatFormatting.DARK_PURPLE));
        int g = c.getInt("mark");
        if (g >= 1 && g <= 5) lines.add(Component.translatable("hollowhours.curse.ghostmark", Component.translatable("hollowhours.mark." + g),
                Component.translatable(GhostKind.byN(g).nameKey())).withStyle(ChatFormatting.DARK_GRAY));
        lines.add(Component.literal(gib(16, key(it.getItem()).hashCode() + 7)).withStyle(ChatFormatting.DARK_RED, ChatFormatting.OBFUSCATED));
        CompoundTag display = it.getOrCreateTagElement("display");
        ListTag lore = new ListTag();
        for (Component l : lines) lore.add(StringTag.valueOf(Component.Serializer.toJson(l)));
        display.put("Lore", lore);
    }

    public static int countCursed(Player p) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (isCursed(s)) n++;
        for (ItemStack s : p.getInventory().armor) if (isCursed(s)) n++;
        for (ItemStack s : p.getInventory().offhand) if (isCursed(s)) n++;
        return n;
    }

    // ------------------------------------------------------------------ drawbacks
    private static int POT = 1;

    static void eff(ServerPlayer p, MobEffect e, int ticks, double amp) {
        int base = Math.max(0, (int) Math.floor(amp)), a = base, t = ticks;
        if (POT > 1 && e != MobEffects.LEVITATION) {
            int cap = e == MobEffects.MOVEMENT_SLOWDOWN || e == MobEffects.DIG_SLOWDOWN ? 4 : e == MobEffects.WITHER ? 1 : e == MobEffects.POISON ? 5 : 6;
            a = Math.max(base, Math.min(cap, base * POT + (POT - 1)));
            t = Math.min(ticks * POT, e == MobEffects.WITHER ? 200 : 3600);
        }
        Fx.effect(p, e, t, a);
    }

    static void hurt(ServerPlayer p, double amount) {
        float a = (float) Math.min(amount * POT, Math.max(0, p.getHealth() - 1));
        if (a > 0) p.hurt(GhostDamage.curse(p.level()), a);
    }

    static void dropNear(ServerPlayer p, ItemStack it, double r) {
        if (it.isEmpty()) return;
        var e = new net.minecraft.world.entity.item.ItemEntity(p.level(), p.getX() + R.rand(-r, r), p.getY() + 0.5, p.getZ() + R.rand(-r, r), it);
        e.setPickUpDelay(40);
        p.level().addFreshEntity(e);
    }

    static boolean warpNear(ServerPlayer p, double r0, double r1) {
        for (int i = 0; i < 12; i++) {
            double a = R.rand(0, Math.PI * 2), r = R.rand(r0, r1);
            BlockPos c = W.standCell(p.level(), p.getX() + Math.cos(a) * r, p.getZ() + Math.sin(a) * r, (int) p.getY() + 3, 6, 2);
            if (c != null) {
                p.teleportTo(c.getX() + 0.5, c.getY(), c.getZ() + 0.5);
                return true;
            }
        }
        return false;
    }

    static final Map<UUID, Long> CALL_CD = new HashMap<>();

    static void runDrawback(ServerPlayer p, String key, double frac, String id, ItemStack item) {
        POT = potency(id);
        double f = Math.min(1, frac * (1 + (POT - 1) * 0.5));
        Fx.actionbar(p, Component.translatable("hollowhours.curse.msg." + key));
        Fx.playSelf(p, "mob.ghast.moan", 1f, 0.5f);
        ServerLevel level = p.serverLevel();
        Vec3 l = p.position();
        switch (key) {
            case "soul" -> {
                int a = (int) Math.min(3, 1 + Math.floor(f * 3));
                eff(p, MobEffects.WEAKNESS, 700, a);
                eff(p, MobEffects.MOVEMENT_SLOWDOWN, 600, Math.min(2, a));
                eff(p, MobEffects.DIG_SLOWDOWN, 600, a);
                if (f >= 0.6) {
                    eff(p, MobEffects.BLINDNESS, 80, 0);
                    eff(p, MobEffects.HUNGER, 300, 2);
                }
            }
            case "blood" -> {
                hurt(p, 5 + Math.floor(f * 5));
                eff(p, MobEffects.BLINDNESS, 40, 0);
            }
            case "starve" -> {
                eff(p, MobEffects.HUNGER, 600, 3 + f * 3);
                eff(p, MobEffects.CONFUSION, 160, 0);
            }
            case "blind" -> {
                eff(p, MobEffects.DARKNESS, 400, 0);
                eff(p, MobEffects.BLINDNESS, (int) ((6 + f * 8) * 20), 0);
            }
            case "rot" -> {
                eff(p, MobEffects.POISON, 320, 1 + f * 2);
                eff(p, MobEffects.CONFUSION, 200, 0);
                eff(p, MobEffects.WEAKNESS, 280, 1);
            }
            case "drown" -> {
                hurt(p, 5 + f * 5);
                eff(p, MobEffects.MOVEMENT_SLOWDOWN, 240, 2);
                eff(p, MobEffects.CONFUSION, 120, 0);
            }
            case "nightmare" -> {
                Jumpscares.scare(p, R.randInt(1, 5), 1);
                eff(p, MobEffects.CONFUSION, 280, 0);
                eff(p, MobEffects.DARKNESS, 600, 0);
                eff(p, MobEffects.BLINDNESS, 120, 0);
                eff(p, MobEffects.WEAKNESS, 500, 1);
            }
            case "burn" -> p.setSecondsOnFire((int) Math.min(14, (7 + Math.floor(f * 7)) * POT));
            case "lightning" -> {
                LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
                if (bolt != null) {
                    bolt.moveTo(l);
                    level.addFreshEntity(bolt);
                }
            }
            case "recoil" -> {
                double a = R.rand(0, Math.PI * 2);
                p.knockback(2.8 * Math.min(POT, 1.6), Math.cos(a), Math.sin(a));
                p.setDeltaMovement(p.getDeltaMovement().add(0, 0.9, 0));
                p.hurtMarked = true;
                hurt(p, 4 + f * 4);
            }
            case "xp" -> p.giveExperienceLevels(-(int) (4 + Math.floor(f * 6)) * POT);
            case "wither" -> {
                eff(p, MobEffects.WITHER, 160, f > 0.6 ? 1 : 0);
                eff(p, MobEffects.BLINDNESS, 50, 0);
            }
            case "thief" -> {
                List<Integer> idx = new ArrayList<>();
                for (int i = 0; i < p.getInventory().items.size(); i++) {
                    ItemStack s = p.getInventory().items.get(i);
                    if (!s.isEmpty() && !isCursed(s)) idx.add(i);
                }
                for (int k = 0; k < POT + 1 && !idx.isEmpty(); k++) {
                    int i = idx.remove(R.randInt(0, idx.size() - 1));
                    ItemStack s = p.getInventory().items.get(i);
                    p.getInventory().items.set(i, ItemStack.EMPTY);
                    dropNear(p, s, 9);
                }
            }
            case "turn" -> {
                p.connection.teleport(p.getX(), p.getY(), p.getZ(), p.getYRot() + 180, p.getXRot());
                eff(p, MobEffects.BLINDNESS, 30, 0);
                eff(p, MobEffects.CONFUSION, 120, 0);
            }
            case "freeze" -> {
                hurt(p, 4 + f * 4);
                p.setTicksFrozen(Math.max(p.getTicksFrozen(), 200));
                eff(p, MobEffects.MOVEMENT_SLOWDOWN, 200, 3);
                eff(p, MobEffects.DIG_SLOWDOWN, 200, 2);
            }
            case "stun" -> {
                eff(p, MobEffects.MOVEMENT_SLOWDOWN, (int) ((3 + f * 2) * 20), 9);
                eff(p, MobEffects.BLINDNESS, 50, 0);
                eff(p, MobEffects.WEAKNESS, 240, 1);
            }
            case "sky" -> eff(p, MobEffects.LEVITATION, (p.getHealth() <= 12 ? 2 : 4) * 20, 3 + (f > 0.6 ? 1 : 0));
            case "call" -> {
                long now = level.getGameTime();
                if (now - CALL_CD.getOrDefault(p.getUUID(), -100000L) < 8 * 60 * 20) {
                    eff(p, MobEffects.DARKNESS, 200, 0);
                } else {
                    CALL_CD.put(p.getUUID(), now);
                    Director.summonGhost(p);
                }
            }
            case "foodrot" -> {
                List<Integer> idx = new ArrayList<>();
                for (int i = 0; i < p.getInventory().items.size(); i++) {
                    ItemStack s = p.getInventory().items.get(i);
                    if (s.isEdible() && !s.is(Items.ROTTEN_FLESH)) idx.add(i);
                }
                if (idx.isEmpty()) eff(p, MobEffects.HUNGER, 300, 2);
                for (int k = 0; k < POT + 1 && !idx.isEmpty(); k++) {
                    int i = idx.remove(R.randInt(0, idx.size() - 1));
                    p.getInventory().items.set(i, new ItemStack(Items.ROTTEN_FLESH, p.getInventory().items.get(i).getCount()));
                }
            }
            case "summon" -> {
                for (int k = 0; k < 3 + (POT - 1) * 2; k++) spawnMob(level, EntityType.ZOMBIE, l.add(R.rand(-4, 4), 0, R.rand(-4, 4)));
            }
            case "strip" -> {
                List<EquipmentSlot> worn = new ArrayList<>();
                for (EquipmentSlot s : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET})
                    if (!p.getItemBySlot(s).isEmpty()) worn.add(s);
                if (worn.isEmpty()) eff(p, MobEffects.WEAKNESS, 400, 1);
                for (int k = 0; k < POT && !worn.isEmpty(); k++) {
                    EquipmentSlot s = worn.remove(R.randInt(0, worn.size() - 1));
                    ItemStack it = p.getItemBySlot(s);
                    p.setItemSlot(s, ItemStack.EMPTY);
                    dropNear(p, it, 4);
                }
            }
            case "scramble" -> {
                for (int k = 0; k < 8 * POT; k++) {
                    int a = R.randInt(0, 8), b = R.randInt(9, 35);
                    ItemStack x = p.getInventory().getItem(a), y = p.getInventory().getItem(b);
                    p.getInventory().setItem(a, y);
                    p.getInventory().setItem(b, x);
                }
                eff(p, MobEffects.CONFUSION, 120, 0);
            }
            case "bleed" -> {
                int n = 5 + POT * 2;
                for (int i = 1; i <= n; i++) Later.run(i * 30, () -> {
                    if (p.isAlive() && p.getHealth() > 2) {
                        p.hurt(GhostDamage.curse(p.level()), 1);
                        level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState()),
                                p.getX(), p.getY() + 1, p.getZ(), 6, 0.2, 0.3, 0.2, 0.05);
                    }
                });
            }
            case "weight" -> {
                eff(p, MobEffects.MOVEMENT_SLOWDOWN, (int) ((12 + f * 8) * 20), 3);
                eff(p, MobEffects.DIG_SLOWDOWN, 240, 1);
                Fx.playSelf(p, "ghost.breath", 0.8f, 0.6f);
            }
            case "hollow" -> {
                Soul.drain(p, 8 * POT, true);
                eff(p, MobEffects.CONFUSION, 160, 0);
                eff(p, MobEffects.DARKNESS, 120, 0);
                level.sendParticles(ParticleTypes.SOUL, l.x, l.y + 1, l.z, 12, 0.5, 0.6, 0.5, 0.02);
                Fx.playSelf(p, "mob.ghast.moan", 1f, 0.35f);
            }
            case "echo" -> {
                eff(p, MobEffects.DARKNESS, 240, 0);
                String[] ids = {"ghost.creak", "ghost.sniff", "ghost.breath", "mob.ghast.moan", "ghost.growl"};
                for (int i = 0; i < 6; i++) Later.run(10 + i * 25 + R.randInt(0, 14), () -> {
                    double a = R.rand(0, Math.PI * 2), d = R.rand(3, 7);
                    Fx.playAt(p, R.pick(ids), p.getX() + Math.cos(a) * d, p.getY() + 1, p.getZ() + Math.sin(a) * d, 0.9f, R.pitch(0.5, 0.8));
                });
            }
            case "fragile" -> {
                for (EquipmentSlot s : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                    ItemStack it = p.getItemBySlot(s);
                    if (it.isEmpty() || isCursed(it) || !it.isDamageableItem()) continue;
                    int nd = it.getDamageValue() + Math.max(1, (int) Math.ceil(it.getMaxDamage() * 0.08 * POT));
                    if (nd >= it.getMaxDamage()) {
                        p.setItemSlot(s, ItemStack.EMPTY);
                        Fx.playSelf(p, "random.break", 1f, 0.6f);
                    } else it.setDamageValue(nd);
                }
            }
            case "rust" -> {
                List<Integer> idx = new ArrayList<>();
                for (int i = 0; i < 9; i++) {
                    ItemStack it = p.getInventory().getItem(i);
                    if (!it.isEmpty() && !isCursed(it) && it.isDamageableItem()) idx.add(i);
                }
                for (int k = 0; k < 1 + POT && !idx.isEmpty(); k++) {
                    int i = idx.remove(R.randInt(0, idx.size() - 1));
                    ItemStack it = p.getInventory().getItem(i);
                    int nd = it.getDamageValue() + Math.max(1, (int) Math.ceil(it.getMaxDamage() * 0.12));
                    if (nd >= it.getMaxDamage()) {
                        p.getInventory().setItem(i, ItemStack.EMPTY);
                        Fx.playSelf(p, "random.break", 1f, 0.7f);
                    } else it.setDamageValue(nd);
                }
            }
            case "ghostlight" -> {
                int T = 30 * POT;
                p.addTag("ghost_marked");
                for (int s = 1; s <= T; s++) {
                    int ss = s;
                    Later.run(s * 20, () -> {
                        if (!p.isAlive()) return;
                        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, p.getX(), p.getY() + 1, p.getZ(), 4, 0.3, 0.6, 0.3, 0.01);
                        if (ss == T) p.removeTag("ghost_marked");
                    });
                }
            }
            case "coldhands" -> {
                ItemStack it = p.getMainHandItem();
                if (!it.isEmpty()) {
                    p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    dropNear(p, it, 3);
                }
                eff(p, MobEffects.DIG_SLOWDOWN, 300, 2);
                eff(p, MobEffects.WEAKNESS, 300, 1);
            }
            case "eatlight" -> {
                eff(p, MobEffects.DARKNESS, 400, 0);
                eff(p, MobEffects.BLINDNESS, 60, 0);
                Flashlight.drain(p, 45 * 20 * POT);
            }
            case "doom" -> {
                for (int i = 3; i >= 1; i--) {
                    int n = i;
                    Later.run((4 - i) * 20, () -> {
                        Fx.title(p, Component.literal(String.valueOf(n)).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), null, 0, 18, 4);
                        Fx.playSelf(p, "random.click", 1f, 0.5f);
                    });
                }
                Later.run(80, () -> {
                    if (!p.isAlive()) return;
                    LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
                    if (bolt != null) {
                        bolt.moveTo(p.position());
                        level.addFreshEntity(bolt);
                    }
                    p.hurt(GhostDamage.curse(level), Math.min(6, p.getHealth() - 1));
                    Fx.effect(p, MobEffects.WITHER, 120, 1);
                });
            }
            case "pact" -> {
                p.heal(6);
                Soul.drain(p, 7 * POT, true);
                eff(p, MobEffects.WITHER, 100, 0);
            }
            case "pursuit" -> {
                GhostEntity g = null;
                double bd = 80;
                for (GhostEntity e : GhostBrain.ghostsNear(level, p.position(), 80)) {
                    double d = e.position().distanceTo(p.position());
                    if (d < bd) {
                        bd = d;
                        g = e;
                    }
                }
                if (g != null) {
                    g.setRaged(true);
                    Fx.playSelf(p, "mob.ghast.scream", 1f, 0.45f);
                } else runDrawback(p, "call", f, id, item);
            }
            case "worms" -> {
                for (int k = 0; k < 2 + POT; k++) spawnMob(level, EntityType.SILVERFISH, l.add(R.rand(-2, 2), 0, R.rand(-2, 2)));
            }
            case "phantoms" -> {
                for (int k = 0; k < POT; k++) spawnMob(level, EntityType.PHANTOM, l.add(R.rand(-6, 6), 12, R.rand(-6, 6)));
            }
            case "vex" -> {
                for (int k = 0; k < 1 + POT; k++) spawnMob(level, EntityType.VEX, l.add(R.rand(-3, 3), 2, R.rand(-3, 3)));
            }
            case "web" -> {
                int[][] cells = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                for (int i = 0; i < Math.min(cells.length, 1 + POT * 2); i++) {
                    BlockPos pos = p.blockPosition().offset(cells[i][0], 0, cells[i][1]);
                    if (level.getBlockState(pos).isAir()) {
                        level.setBlock(pos, Blocks.COBWEB.defaultBlockState(), 3);
                        Later.run(18 * 20, () -> {
                            if (level.getBlockState(pos).is(Blocks.COBWEB)) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                        });
                    }
                }
            }
            case "warp" -> {
                warpNear(p, 6, 11);
                eff(p, MobEffects.CONFUSION, 160, 0);
                eff(p, MobEffects.DARKNESS, 60, 0);
            }
            case "tremor" -> {
                Fx.shake(p, 0.6f, 120);
                eff(p, MobEffects.DIG_SLOWDOWN, 300, 2);
                eff(p, MobEffects.WEAKNESS, 300, 1);
            }
            default -> {}
        }
        // the ghost who gave it leaves its own mark now and then
        int gm = item.isEmpty() || !isCursed(item) ? 0 : cdata(item).getInt("mark");
        if (gm >= 1 && gm <= 5 && R.chance(0.35)) Later.run(40, () -> markEffect(p, gm));
        Soul.drain(p, new int[]{0, 3, 5, 8}[POT], false);
        POT = 1;
    }

    static void spawnMob(ServerLevel level, EntityType<?> type, Vec3 at) {
        var e = type.create(level);
        if (e == null) return;
        e.moveTo(at.x, at.y, at.z, R.pitch(0, 360), 0);
        level.addFreshEntity(e);
    }

    static void markEffect(ServerPlayer p, int g) {
        Fx.actionbar(p, Component.translatable("hollowhours.mark.msg." + g));
        switch (g) {
            case 1 -> {
                Fx.effect(p, MobEffects.CONFUSION, 140, 0);
                for (int i = 0; i < 3; i++) Later.run(i * 20, () -> Fx.playAt(p, "mob.ghast.moan", p.getX() + R.rand(-4, 4), p.getY(), p.getZ() + R.rand(-4, 4), 0.8f, R.pitch(0.7, 0.95)));
                Soul.drain(p, 2, true);
            }
            case 2 -> {
                hurt(p, p.isInWater() ? 5 : 3);
                Fx.effect(p, MobEffects.MOVEMENT_SLOWDOWN, 120, 1);
            }
            case 3 -> {
                p.setSecondsOnFire(4);
                Fx.effect(p, MobEffects.HUNGER, 200, 1);
            }
            case 4 -> {
                warpNear(p, 3, 7);
                Fx.effect(p, MobEffects.DARKNESS, 80, 0);
                Fx.playAt(p, "mob.witch.ambient", p.getX() + 2, p.getY() + 1, p.getZ(), 1f, 1.5f);
            }
            default -> {
                Fx.effect(p, MobEffects.HUNGER, 300, 2);
                Soul.drain(p, 2, true);
            }
        }
    }

    // ------------------------------------------------------------------ detecting use
    private static final Map<String, Long> LAST_USE = new HashMap<>();

    /** kind: hit, break, use, useon, release, consume */
    public static void handleUse(ServerPlayer p, String kind, @Nullable ItemStack evItem) {
        ItemStack held = p.getMainHandItem();
        ItemStack it = evItem != null && isCursed(evItem) ? evItem : isCursed(held) ? held : ItemStack.EMPTY;
        if (it.isEmpty()) return;
        String id = key(it.getItem());
        String cat = category(id);
        if (it.getItem() instanceof ArmorItem || cat.equals("elytra")) return;
        boolean ok = switch (cat) {
            case "sword", "axe", "pickaxe", "shovel", "hoe" -> kind.equals("hit") || kind.equals("break") || kind.equals("useon");
            case "bucket" -> kind.equals("use") || kind.equals("useon");
            case "bed" -> kind.equals("useon");
            case "bow", "crossbow" -> kind.equals("release");
            case "trident" -> kind.equals("release") || kind.equals("hit");
            case "shears" -> kind.equals("use") || kind.equals("useon") || kind.equals("break");
            case "flint" -> kind.equals("useon");
            case "rod", "shield" -> kind.equals("use");
            default -> it.isDamageableItem() ? !kind.equals("consume") : (kind.equals("consume") || kind.equals("useon") || kind.equals("use"));
        };
        if (!ok) return;
        long now = p.serverLevel().getGameTime();
        String lk = p.getUUID() + "|" + id;
        if (now - LAST_USE.getOrDefault(lk, -99L) < 4) return;
        LAST_USE.put(lk, now);
        CompoundTag c = cdata(it);
        double frac = 0.5;
        boolean broke = false;
        if (c.getInt("N") > 0 && it == held) {
            int n = c.getInt("n") - 1;
            frac = Math.min(1, 1 - n / (double) c.getInt("N"));
            if (n <= 0) {
                p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                broke = true;
                frac = 1;
            } else {
                c.putInt("n", n);
                writeLore(it);
            }
        }
        runDrawback(p, c.getString("dr"), frac, id, it);
        if (broke) breakFx(p);
    }

    static void breakFx(ServerPlayer p) {
        Fx.playSelf(p, "random.break", 1.2f, 0.6f);
        Fx.title(p, Component.translatable("hollowhours.curse.shatter"), null, 0, 25, 10);
        p.serverLevel().sendParticles(ParticleTypes.TOTEM_OF_UNDYING, p.getX(), p.getY() + 1, p.getZ(), 20, 0.4, 0.6, 0.4, 0.2);
    }

    private static final Map<String, Long> ARMOR_LAST = new HashMap<>();

    /** Wearing cursed armor: each time you are attacked its curse may fire; also heals back 12.5% per piece. */
    public static float onPlayerHurt(ServerPlayer p, float dmg, boolean melee) {
        int pieces = 0;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack it = p.getItemBySlot(slot);
            if (!isCursed(it)) continue;
            pieces++;
            if (!melee) continue;
            long now = p.serverLevel().getGameTime();
            String k = p.getUUID() + "|" + slot;
            if (now - ARMOR_LAST.getOrDefault(k, -99L) < 20) continue;
            ARMOR_LAST.put(k, now);
            CompoundTag c = cdata(it);
            double frac = 0.5;
            boolean broke = false;
            if (c.getInt("N") > 0) {
                int n = c.getInt("n") - 1;
                frac = Math.min(1, 1 - n / (double) c.getInt("N"));
                if (n <= 0) {
                    p.setItemSlot(slot, ItemStack.EMPTY);
                    broke = true;
                    frac = 1;
                } else {
                    c.putInt("n", n);
                    writeLore(it);
                }
            }
            runDrawback(p, c.getString("dr"), frac, key(it.getItem()), it);
            if (broke) breakFx(p);
        }
        return pieces > 0 ? dmg * (1 - 0.125f * pieces) : dmg;
    }

    /** Hitting with a cursed weapon/tool deals 12.5% more. */
    public static float attackBonus(ServerPlayer attacker, float dmg) {
        ItemStack held = attacker.getMainHandItem();
        if (!isCursed(held)) return dmg;
        String cat = category(key(held.getItem()));
        boolean bonus = List.of("sword", "axe", "pickaxe", "shovel", "hoe", "bow", "crossbow", "trident").contains(cat);
        handleUse(attacker, "hit", null);
        return bonus ? dmg * 1.125f : dmg;
    }

    /** Every second: cursed digging tools give Haste I. */
    public static void hasteTick(ServerPlayer p) {
        ItemStack h = p.getMainHandItem();
        if (isCursed(h)) {
            String cat = category(key(h.getItem()));
            if (cat.equals("pickaxe") || cat.equals("shovel") || cat.equals("hoe") || cat.equals("axe")) Fx.effect(p, MobEffects.DIG_SPEED, 60, 0);
        }
    }

    /** Crafting with a cursed ingredient makes the result cursed. */
    public static void onCrafted(ItemStack result, Container grid) {
        int mark = 0;
        boolean any = false;
        for (int i = 0; i < grid.getContainerSize(); i++) {
            ItemStack s = grid.getItem(i);
            if (isCursed(s)) {
                any = true;
                mark = cdata(s).getInt("mark");
            }
        }
        if (any && !isCursed(result)) curse(result, mark);
    }

    // ------------------------------------------------------------------ asking a ghost for an item
    static final Map<String, String> TOOLS_TH = new LinkedHashMap<>();
    static final Map<String, String> MATS_TH = new LinkedHashMap<>();
    static final Map<String, String> ITEMS_TH = new LinkedHashMap<>();

    static {
        String[][] tools = {{"ดาบ", "sword"}, {"ขวาน", "axe"}, {"อีเต้อ", "pickaxe"}, {"อีเตอร์", "pickaxe"}, {"ที่ขุดแร่", "pickaxe"}, {"ที่ขุด", "pickaxe"},
                {"พิคแอ็กซ์", "pickaxe"}, {"พลั่ว", "shovel"}, {"เสียม", "shovel"}, {"จอบ", "hoe"}, {"หมวกเกราะ", "helmet"}, {"หมวก", "helmet"},
                {"เสื้อเกราะ", "chestplate"}, {"เกราะอก", "chestplate"}, {"กางเกง", "leggings"}, {"เกราะขา", "leggings"}, {"รองเท้า", "boots"}, {"บูท", "boots"}, {"เกราะ", "chestplate"}};
        for (String[] t : tools) TOOLS_TH.put(t[0], t[1]);
        String[][] mats = {{"ไม้", "wooden"}, {"หิน", "stone"}, {"เหล็ก", "iron"}, {"ทองคำ", "golden"}, {"ทอง", "golden"}, {"เพชร", "diamond"},
                {"นีเธอไรต์", "netherite"}, {"หนัง", "leather"}, {"โซ่", "chainmail"}};
        for (String[] m : mats) MATS_TH.put(m[0], m[1]);
        String[][] items = {{"เพชร", "diamond"}, {"ทองคำแท่ง", "gold_ingot"}, {"ทองคำ", "gold_ingot"}, {"ทอง", "gold_ingot"}, {"เหล็กแท่ง", "iron_ingot"},
                {"เหล็กดิบ", "raw_iron"}, {"เหล็ก", "iron_ingot"}, {"ถ่านหิน", "coal"}, {"ถ่าน", "coal"}, {"มรกต", "emerald"}, {"นีเธอไรต์แท่ง", "netherite_ingot"},
                {"นีเธอไรต์", "netherite_ingot"}, {"ไม้แท่ง", "stick"}, {"แท่งไม้", "stick"}, {"ท่อนไม้", "oak_log"}, {"ไม้กระดาน", "oak_planks"}, {"ไม้", "oak_planks"},
                {"ก้อนหิน", "cobblestone"}, {"หินเหล็กไฟ", "flint"}, {"หิน", "cobblestone"}, {"เตียง", "red_bed"}, {"ถังน้ำ", "water_bucket"}, {"ถังลาวา", "lava_bucket"},
                {"ถัง", "bucket"}, {"ลูกธนู", "arrow"}, {"ลูกศร", "arrow"}, {"ธนู", "bow"}, {"หน้าไม้", "crossbow"}, {"เบ็ด", "fishing_rod"}, {"โล่", "shield"},
                {"กรรไกร", "shears"}, {"คบไฟ", "torch"}, {"คบเพลิง", "torch"}, {"ขนมปัง", "bread"}, {"แอปเปิลทอง", "golden_apple"}, {"แอปเปิล", "apple"},
                {"แอปเปิ้ล", "apple"}, {"สเต็ก", "cooked_beef"}, {"สเต๊ก", "cooked_beef"}, {"เนื้อหมู", "cooked_porkchop"}, {"เนื้อไก่", "cooked_chicken"},
                {"เนื้อ", "cooked_beef"}, {"ไข่", "egg"}, {"เอนเดอร์เพิร์ล", "ender_pearl"}, {"หีบ", "chest"}, {"โต๊ะคราฟ", "crafting_table"}, {"เตาเผา", "furnace"},
                {"ทีเอ็นที", "tnt"}, {"ระเบิด", "tnt"}, {"เรดสโตน", "redstone"}, {"ผ้าขนสัตว์", "white_wool"}, {"ขนสัตว์", "white_wool"}, {"ด้าย", "string"},
                {"เชือกจูง", "lead"}, {"เชือก", "string"}, {"กระดูก", "bone"}, {"ดินปืน", "gunpowder"}, {"ตรีศูล", "trident"}, {"ทริเดนท์", "trident"},
                {"ปีก", "elytra"}, {"โทเท็ม", "totem_of_undying"}, {"ลาวา", "lava_bucket"}, {"น้ำ", "water_bucket"}, {"หนัง", "leather"}, {"ดวงดาว", "nether_star"},
                {"เนเธอร์สตาร์", "nether_star"}, {"บีคอน", "beacon"}, {"หนังสือ", "book"}, {"ทองแดง", "copper_ingot"}, {"ดิน", "dirt"}, {"ทราย", "sand"},
                {"กระจก", "glass"}, {"แครอททอง", "golden_carrot"}, {"แครอท", "carrot"}, {"มันฝรั่ง", "potato"}, {"ปลาแซลมอน", "cooked_salmon"}, {"ปลา", "cooked_cod"},
                {"น้ำผึ้ง", "honey_bottle"}, {"ขวด", "glass_bottle"}, {"ออบซิเดียน", "obsidian"}, {"โอบซิเดียน", "obsidian"}, {"ประตู", "oak_door"},
                {"บันได", "ladder"}, {"รถราง", "minecart"}, {"เค้ก", "cake"}, {"คุกกี้", "cookie"}, {"แตงโม", "melon_slice"}, {"ฟักทอง", "pumpkin"},
                {"เข็มทิศ", "compass"}, {"นาฬิกา", "clock"}, {"แผนที่", "map"}, {"อานม้า", "saddle"}, {"ป้ายชื่อ", "name_tag"}, {"ตาเอนเดอร์", "ender_eye"},
                {"ขวดประสบการณ์", "experience_bottle"}, {"บล็อกเพชร", "diamond_block"}, {"บล็อกเหล็ก", "iron_block"}, {"บล็อกทอง", "gold_block"},
                {"บล็อกมรกต", "emerald_block"}, {"ลูกสูบ", "piston"}, {"ตะเกียง", "lantern"}};
        for (String[] i : items) ITEMS_TH.put(i[0], i[1]);
    }

    static final java.util.Set<String> DENY = java.util.Set.of("air", "command_block", "repeating_command_block", "chain_command_block", "command_block_minecart",
            "structure_block", "structure_void", "jigsaw", "barrier", "light", "debug_stick", "knowledge_book", "bedrock");
    static final java.util.Set<String> EN_FILL = java.util.Set.of("give", "me", "i", "want", "need", "please", "pls", "plz", "some", "a", "an", "the", "of", "piece",
            "pieces", "pcs", "pc", "x", "can", "could", "have", "get", "would", "like", "id", "to", "you", "my", "ghost", "ask", "and");
    static final Map<String, Integer> NUM_EN = Map.of("one", 1, "two", 2, "three", 3, "four", 4, "five", 5, "six", 6, "seven", 7, "eight", 8, "nine", 9, "ten", 10);
    static final String[][] NUM_TH = {{"ยี่สิบ", "20"}, {"สามสิบ", "30"}, {"สี่สิบ", "40"}, {"ห้าสิบ", "50"}, {"หกสิบ", "60"}, {"สิบ", "10"}, {"หนึ่ง", "1"}, {"สอง", "2"},
            {"สาม", "3"}, {"สี่", "4"}, {"ห้า", "5"}, {"หก", "6"}, {"เจ็ด", "7"}, {"แปด", "8"}, {"เก้า", "9"}};

    public static int parseQty(String raw) {
        String t = raw.toLowerCase();
        StringBuilder norm = new StringBuilder();
        for (char ch : t.toCharArray()) norm.append(ch >= '๐' && ch <= '๙' ? (char) ('0' + (ch - '๐')) : ch);
        t = norm.toString();
        Matcher m = Pattern.compile("(\\d{1,3})").matcher(t);
        int q = 1;
        if (m.find()) q = Integer.parseInt(m.group(1));
        else {
            boolean found = false;
            for (String[] n : NUM_TH) if (t.contains(n[0])) {
                q = Integer.parseInt(n[1]);
                found = true;
                break;
            }
            if (!found) for (String w : t.split("[^a-z]+")) if (NUM_EN.containsKey(w)) {
                q = NUM_EN.get(w);
                break;
            }
        }
        return Math.max(1, Math.min(64, q));
    }

    @Nullable
    static String thaiLookup(String t) {
        String bestTool = null;
        int ti = -1;
        for (String k : TOOLS_TH.keySet()) {
            int i = t.indexOf(k);
            if (i >= 0 && (bestTool == null || k.length() > bestTool.length())) {
                bestTool = k;
                ti = i;
            }
        }
        if (bestTool != null) {
            String rest = t.substring(ti + bestTool.length());
            String mat = null;
            for (String k : MATS_TH.keySet()) if (rest.contains(k) && (mat == null || k.length() > mat.length())) mat = k;
            String part = TOOLS_TH.get(bestTool);
            String material = mat != null ? MATS_TH.get(mat) : "iron";
            if (material.equals("leather") && !part.matches("helmet|chestplate|leggings|boots")) material = "iron";
            return material + "_" + part;
        }
        String best = null;
        for (String k : ITEMS_TH.keySet()) if (t.contains(k) && (best == null || k.length() > best.length())) best = k;
        return best == null ? null : ITEMS_TH.get(best);
    }

    /** Finds a real item in any sentence (Thai or English). Returns null if unknown, "!" if forbidden. */
    @Nullable
    public static String resolveItem(String raw) {
        String t = raw.trim().toLowerCase().replace("minecraft:", "");
        if (t.isEmpty()) return null;
        List<String> cands = new ArrayList<>();
        String th = thaiLookup(t);
        if (th != null) cands.add(th);
        StringBuilder en = new StringBuilder();
        for (String w : t.split("[^a-z_]+")) {
            if (w.isEmpty() || EN_FILL.contains(w) || NUM_EN.containsKey(w)) continue;
            if (en.length() > 0) en.append('_');
            en.append(w);
        }
        String e = en.toString();
        if (!e.isEmpty()) {
            cands.add(e.replaceFirst("^gold_(?=sword|pickaxe|axe|shovel|hoe|helmet|chestplate|leggings|boots)", "golden_")
                    .replaceFirst("^wood_(?=sword|pickaxe|axe|shovel|hoe)", "wooden_").replace("pick_axe", "pickaxe"));
            cands.add(e);
            if (e.length() > 3 && e.endsWith("s")) cands.add(e.substring(0, e.length() - 1));
            switch (e) {
                case "gold" -> cands.add("gold_ingot");
                case "iron" -> cands.add("iron_ingot");
                case "wood" -> cands.add("oak_planks");
                case "copper" -> cands.add("copper_ingot");
                case "netherite" -> cands.add("netherite_ingot");
                default -> {}
            }
        }
        for (String c : cands) {
            if (DENY.contains(c)) return "!";
            ResourceLocation rl = ResourceLocation.tryParse("minecraft:" + c);
            if (rl != null && BuiltInRegistries.ITEM.containsKey(rl)) return rl.toString();
        }
        return null;
    }

    static final Map<UUID, Long> LAST_ASK = new HashMap<>();

    @Nullable
    static GhostEntity nearestGhost(ServerPlayer p) {
        GhostEntity best = null;
        double bd = Double.MAX_VALUE;
        for (GhostEntity g : Director.allGhosts(p.server)) {
            if (g.level() != p.level() || g.brain().isLeaving()) continue;
            double d = g.position().distanceTo(p.position());
            if (d < bd) {
                bd = d;
                best = g;
            }
        }
        return best;
    }

    public static int asksLeft(ServerPlayer p) {
        GhostEntity g = nearestGhost(p);
        int lim = HHConfig.ASK_LIMIT.get();
        if (g == null) return lim;
        return Math.max(0, lim - g.brain().asks.getOrDefault(p.getUUID(), 0));
    }

    public static void askGhost(ServerPlayer p, String text) {
        GhostEntity g = nearestGhost(p);
        if (g == null) {
            p.sendSystemMessage(Component.translatable("hollowhours.ask.noghost").withStyle(ChatFormatting.GRAY));
            return;
        }
        long now = p.serverLevel().getGameTime();
        if (now - LAST_ASK.getOrDefault(p.getUUID(), -100000L) < 40) {
            p.sendSystemMessage(Component.translatable("hollowhours.ask.wait").withStyle(ChatFormatting.GRAY));
            return;
        }
        LAST_ASK.put(p.getUUID(), now);
        GhostKind kind = g.kind;
        GhostBrain b = g.brain();
        String id = resolveItem(text);
        int qty = parseQty(text);
        if (id == null || id.equals("!")) {
            GhostTalk.say(p, g, "ask.unknown", 0);
            return;
        }
        int used = b.asks.getOrDefault(p.getUUID(), 0);
        int limit = HHConfig.ASK_LIMIT.get();
        if (used >= limit) {
            GhostTalk.say(p, g, "ask.limit", 1);
            return;
        }
        b.asks.put(p.getUUID(), used + 1);
        addBuff(p.serverLevel(), id);
        int left = limit - used - 1;
        Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(id));
        if (R.chance(HHConfig.ASK_GIVE_CHANCE.get())) {
            int max = item.getMaxStackSize();
            int n = Math.max(1, Math.min(qty, max > 1 ? max : 8));
            if (max > 1) give(p, makeCursed(item, n, kind.n));
            else for (int i = 0; i < n; i++) give(p, makeCursed(item, 1, kind.n));
            GhostTalk.say(p, g, "ask.give", 0);
            Fx.effect(p, MobEffects.DARKNESS, 50, 0);
            MutableComponent nm = item.getDescription().copy();
            Fx.title(p, Component.translatable("hollowhours.ask.sealed"), nm.append(" ×" + n).withStyle(ChatFormatting.RED), 4, 40, 16);
            Fx.playSelf(p, "mob.ghast.moan", 0.8f, 0.4f);
            p.serverLevel().sendParticles(ParticleTypes.SOUL, p.getX(), p.getY() + 1, p.getZ(), 14, 0.5, 0.6, 0.5, 0.02);
            p.sendSystemMessage(Component.translatable("hollowhours.ask.got", item.getDescription(), n, left).withStyle(ChatFormatting.DARK_RED));
        } else {
            GhostTalk.say(p, g, "ask.refuse", 0);
            p.sendSystemMessage(Component.translatable("hollowhours.ask.refused", left).withStyle(ChatFormatting.DARK_GRAY));
        }
        // asking makes it hesitate a moment, then it hunts again
        b.appeaseUntil = Math.max(b.appeaseUntil, now + 30 * 20);
    }

    static void give(ServerPlayer p, ItemStack it) {
        if (!p.getInventory().add(it)) p.drop(it, false);
    }

    /** Talk text that is really a request ("give me 5 diamonds") goes to the ask system. */
    public static boolean tryAsk(ServerPlayer p, String text) {
        String t = text.toLowerCase();
        boolean req = Pattern.compile("(ขอ(?!ร้อง|โทษ|บคุณ|บใจ)|อยากได้|ต้องการ|เอา.+มา|give me|gimme|i want|i need|can i have|could i have|i'd like)").matcher(t).find();
        boolean offer = Pattern.compile("(จะให้|ขอให้|แลก|ข้อเสนอ|give you|i give|i'll give|ill give|offer|trade|deal)").matcher(t).find();
        if (!req || offer) return false;
        String id = resolveItem(t);
        if (id == null || id.equals("!") || nearestGhost(p) == null) return false;
        askGhost(p, t);
        return true;
    }
}
