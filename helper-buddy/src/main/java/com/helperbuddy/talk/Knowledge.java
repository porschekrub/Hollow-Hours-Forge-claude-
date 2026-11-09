package com.helperbuddy.talk;

import com.helperbuddy.compat.Compat;
import com.helperbuddy.entity.HelperEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What the helper can see and tell about anything in the game, other mods included: the thing you look at or hold, the
 * creatures around, the installed mods. Also a short summary of the world for the AI.
 */
public final class Knowledge {
    private Knowledge() {}

    static String modName(String ns) {
        if (ns.equals("minecraft")) return "Minecraft";
        //#if NEOFORGE
        //$$ return net.neoforged.fml.ModList.get().getModContainerById(ns).map(c -> c.getModInfo().getDisplayName()).orElse(ns);
        //#else
        return net.minecraftforge.fml.ModList.get().getModContainerById(ns).map(c -> c.getModInfo().getDisplayName()).orElse(ns);
        //#endif
    }

    /** "Hollow Hours, Helper Buddy, GeckoLib, ..." (without the loader's own parts). */
    public static List<String> mods() {
        List<String> out = new ArrayList<>();
        //#if NEOFORGE
        //$$ for (var m : net.neoforged.fml.ModList.get().getMods()) {
        //#else
        for (var m : net.minecraftforge.fml.ModList.get().getMods()) {
        //#endif
            String id = m.getModId();
            if (id.equals("minecraft") || id.equals("forge") || id.equals("neoforge") || id.equals("java") || id.startsWith("fabric") || id.equals("mixinextras")) continue;
            out.add(m.getDisplayName());
        }
        return out;
    }

    // ------------------------------------------------------------------ what is this?
    /** The entity or block the player looks at (20 blocks), else null. */
    static HitResult looked(ServerPlayer p) {
        Vec3 eye = p.getEyePosition();
        Vec3 end = eye.add(p.getViewVector(1f).scale(20));
        BlockHitResult bh = p.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
        double max = bh.getType() == HitResult.Type.MISS ? 400 : bh.getLocation().distanceToSqr(eye);
        EntityHitResult eh = ProjectileUtil.getEntityHitResult(p, eye, end, new AABB(eye, end).inflate(1), e -> !e.isSpectator() && e.isPickable(), max);
        if (eh != null) return eh;
        return bh.getType() == HitResult.Type.BLOCK ? bh : null;
    }

    public static String whatIs(ServerPlayer p, HelperEntity h, boolean th) {
        HitResult hit = looked(p);
        if (hit instanceof EntityHitResult eh) return entity(eh.getEntity(), th);
        ItemStack held = p.getMainHandItem();
        if (!held.isEmpty()) return item(p, held, th);
        if (hit instanceof BlockHitResult bh) return block(p.level().getBlockState(bh.getBlockPos()), bh.getBlockPos(), p, th);
        return th ? "ไม่เห็นว่าเอ็งถือหรือมองอะไรอยู่เลย ถือของไว้ในมือ หรือหันไปมองสิ่งที่อยากรู้ แล้วถามใหม่"
                : "You're not holding or looking at anything. Hold it, or look at it, and ask again";
    }

    public static String item(ServerPlayer p, ItemStack s, boolean th) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(s.getItem());
        StringBuilder b = new StringBuilder();
        b.append("§e").append(s.getHoverName().getString()).append("§r §7(").append(id).append(th ? ", จากมอด " : ", from ").append(modName(id.getNamespace())).append(")§r");
        List<String> facts = new ArrayList<>();
        if (s.isDamageableItem()) facts.add(th ? "ทนทาน " + (s.getMaxDamage() - s.getDamageValue()) + "/" + s.getMaxDamage() : "durability " + (s.getMaxDamage() - s.getDamageValue()) + "/" + s.getMaxDamage());
        double dmg = Compat.mainHand(s, Attributes.ATTACK_DAMAGE);
        if (dmg > 0) facts.add(th ? "ตีแรง +" + fmt(dmg) : "attack +" + fmt(dmg));
        int food = Compat.nutrition(s);
        if (food > 0) facts.add(th ? "กินได้ อิ่ม " + food : "food, fills " + food);
        int burn = Compat.burnTime(s);
        if (burn > 0) facts.add(th ? "ใช้เป็นเชื้อเพลิงได้ (เผาได้ " + burn / 200f + " ชิ้น)" : "fuel (smelts " + burn / 200f + " items)");
        if (s.getItem() instanceof BlockItem bi) facts.add(th ? "เป็นบล็อก วางได้" : "a block you can place");
        facts.add(th ? "ซ้อนได้ " + s.getMaxStackSize() : "stacks to " + s.getMaxStackSize());
        String how = howToMake(p, s, th);
        b.append("\n§7").append(String.join(", ", facts));
        if (how != null) b.append("\n§7").append(how);
        return b.toString();
    }

    static String howToMake(ServerPlayer p, ItemStack s, boolean th) {
        for (var r : Compat.recipesFor(p.server, s.getItem())) {
            List<String> parts = new ArrayList<>();
            Map<String, Integer> count = new LinkedHashMap<>();
            for (var ing : r.getIngredients()) {
                ItemStack[] alts = ing.getItems();
                if (alts.length == 0) continue;
                count.merge(alts[0].getHoverName().getString(), 1, Integer::sum);
            }
            count.forEach((k, v) -> parts.add(v + " " + k));
            if (parts.isEmpty()) continue;
            return (th ? "ทำจาก: " : "made from: ") + String.join(" + ", parts) + (th ? " (สั่งข้าว่า \"คราฟ...\" ได้)" : " (tell me \"craft ...\")");
        }
        return null;
    }

    public static String block(BlockState st, BlockPos pos, ServerPlayer p, boolean th) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(st.getBlock());
        String tool = st.is(BlockTags.MINEABLE_WITH_PICKAXE) ? (th ? "อีเต้อ" : "a pickaxe") : st.is(BlockTags.MINEABLE_WITH_AXE) ? (th ? "ขวาน" : "an axe")
                : st.is(BlockTags.MINEABLE_WITH_SHOVEL) ? (th ? "พลั่ว" : "a shovel") : st.is(BlockTags.MINEABLE_WITH_HOE) ? (th ? "จอบ" : "a hoe") : (th ? "มือเปล่าก็ได้" : "any tool");
        float hard = st.getDestroySpeed(p.level(), pos);
        return "§e" + st.getBlock().getName().getString() + "§r §7(" + id + (th ? ", จากมอด " : ", from ") + modName(id.getNamespace()) + ")§r\n§7"
                + (th ? "ความแข็ง " + (hard < 0 ? "ทุบไม่ได้" : fmt(hard)) + ", ขุดด้วย" + tool + ", อยู่ที่ " + pos.toShortString()
                : "hardness " + (hard < 0 ? "unbreakable" : fmt(hard)) + ", dig it with " + tool + ", at " + pos.toShortString());
    }

    public static String entity(Entity e, boolean th) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
        StringBuilder b = new StringBuilder("§e").append(e.getName().getString()).append("§r §7(").append(id).append(th ? ", จากมอด " : ", from ").append(modName(id.getNamespace())).append(")§r");
        List<String> facts = new ArrayList<>();
        if (e instanceof LivingEntity le) facts.add(th ? "เลือด " + fmt(le.getHealth()) + "/" + fmt(le.getMaxHealth()) : "health " + fmt(le.getHealth()) + "/" + fmt(le.getMaxHealth()));
        MobCategory c = e.getType().getCategory();
        facts.add(switch (c) {
            case MONSTER -> th ? "§cศัตรู อันตราย§7" : "§chostile§7";
            case CREATURE, AMBIENT, WATER_CREATURE, WATER_AMBIENT, UNDERGROUND_WATER_CREATURE, AXOLOTLS -> th ? "สัตว์ ไม่ทำร้ายใคร" : "a peaceful creature";
            default -> th ? "อื่นๆ" : "other";
        });
        if (id.getNamespace().equals("hollowhours") && c == MobCategory.MONSTER) facts.add(th ? "§4ผี! วิ่ง!§7" : "§4a ghost! RUN§7");
        return b.append("\n§7").append(String.join(", ", facts)).toString();
    }

    /** The creatures around the helper (32 blocks), grouped. */
    public static String around(ServerPlayer p, HelperEntity h, boolean th) {
        Map<String, int[]> seen = new LinkedHashMap<>();
        int hostile = 0;
        for (Entity e : h.level().getEntities(h, h.getBoundingBox().inflate(32), e -> e instanceof LivingEntity && e != p && !(e instanceof HelperEntity))) {
            String n = e.getName().getString();
            String ns = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getNamespace();
            String key = n + (ns.equals("minecraft") ? "" : " §8[" + modName(ns) + "]§r");
            seen.computeIfAbsent(key, k -> new int[1])[0]++;
            if (e.getType().getCategory() == MobCategory.MONSTER) hostile++;
        }
        if (seen.isEmpty()) return th ? "แถวนี้เงียบมาก ไม่มีใครเลย (นอกจากเอ็งกับข้า)" : "Nothing around but you and me";
        List<String> l = new ArrayList<>();
        seen.forEach((k, v) -> l.add(v[0] > 1 ? k + " x" + v[0] : k));
        return (th ? "แถวนี้ (32 บล็อก) มี: " : "Around here (32 blocks): ") + String.join(", ", l)
                + (hostile > 0 ? (th ? "\n§cมีตัวอันตราย " + hostile + " ตัว ระวังด้วย" : "\n§c" + hostile + " hostile, careful") : "");
    }

    /** The world around the player for the AI (English, short). */
    public static String forAi(ServerPlayer p, HelperEntity h) {
        StringBuilder b = new StringBuilder();
        BlockPos at = p.blockPosition();
        var biome = p.level().getBiome(at).unwrapKey().map(k -> k.location().toString()).orElse("?");
        long dt = p.level().getDayTime() % 24000;
        b.append("The player is at ").append(at.toShortString()).append(" in ").append(p.level().dimension().location()).append(", biome ").append(biome)
                .append(", facing ").append(com.helperbuddy.brain.Navigator.compass((p.getYRot() + 180 + 360) % 360, false))
                .append(", time ").append(dt < 12500 ? "day" : "night").append(p.level().isRaining() ? ", raining" : "")
                .append(", health ").append((int) p.getHealth()).append("/").append((int) p.getMaxHealth()).append(", food ").append(p.getFoodData().getFoodLevel()).append(". ");
        ItemStack held = p.getMainHandItem();
        if (!held.isEmpty()) b.append("Holding ").append(held.getCount()).append(" ").append(BuiltInRegistries.ITEM.getKey(held.getItem())).append(". ");
        HitResult hit = looked(p);
        if (hit instanceof EntityHitResult eh) b.append("Looking at ").append(BuiltInRegistries.ENTITY_TYPE.getKey(eh.getEntity().getType())).append(". ");
        else if (hit instanceof BlockHitResult bh) b.append("Looking at ").append(BuiltInRegistries.BLOCK.getKey(p.level().getBlockState(bh.getBlockPos()).getBlock())).append(". ");
        Map<String, Integer> near = new LinkedHashMap<>();
        for (Entity e : p.level().getEntities(p, p.getBoundingBox().inflate(32), e -> e instanceof LivingEntity && !(e instanceof HelperEntity)))
            near.merge(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString(), 1, Integer::sum);
        if (!near.isEmpty()) b.append("Nearby creatures: ").append(near).append(". ");
        List<String> inv = new ArrayList<>();
        for (int i = 0; i < p.getInventory().getContainerSize() && inv.size() < 20; i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (!s.isEmpty()) inv.add(s.getCount() + " " + BuiltInRegistries.ITEM.getKey(s.getItem()).getPath());
        }
        if (!inv.isEmpty()) b.append("Player inventory: ").append(String.join(", ", inv)).append(". ");
        List<String> bag = new ArrayList<>();
        for (int i = 0; i < h.bag.getContainerSize() && bag.size() < 20; i++) {
            ItemStack s = h.bag.getItem(i);
            if (!s.isEmpty()) bag.add(s.getCount() + " " + BuiltInRegistries.ITEM.getKey(s.getItem()).getPath());
        }
        if (!bag.isEmpty()) b.append("Your (the helper's) bag: ").append(String.join(", ", bag)).append(". ");
        b.append("Installed mods: ").append(String.join(", ", mods())).append(".");
        return b.toString();
    }

    static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.format("%.1f", v);
    }
}
