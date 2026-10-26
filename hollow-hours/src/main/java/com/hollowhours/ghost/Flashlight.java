package com.hollowhours.ghost;

import com.hollowhours.item.FlashlightItem;
import com.hollowhours.registry.HHItems;
import com.hollowhours.util.Fx;
import com.hollowhours.util.R;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Flashlight (port of flashlight.js): a real light beam made of invisible light blocks where you point it.
 * One battery = 5 minutes of light (only counts while on). Low battery or a ghost close by = flicker.
 * Ghosts see a lit flashlight from much further away, and it leaks out of hiding spots.
 */
public final class Flashlight {
    private Flashlight() {}

    public static final int FULL = 5 * 60 * 20;
    static final int RANGE = 28;
    private static final Map<UUID, Set<Long>> LIT = new HashMap<>();

    // ------------------------------------------------------------------ item data
    public static int charge(ItemStack it) {
        CompoundTag t = it.getTag();
        return t == null || !t.contains("charge") ? FULL : t.getInt("charge");
    }

    public static boolean on(ItemStack it) {
        CompoundTag t = it.getTag();
        return t != null && t.getBoolean("on");
    }

    public static void stamp(ItemStack it, int charge, boolean on) {
        CompoundTag t = it.getOrCreateTag();
        t.putInt("charge", Math.max(0, charge));
        t.putBoolean("on", on);
        int dmg = (int) Math.round(FlashlightItem.DUR_SPAN * (1 - Math.max(0, charge) / (double) FULL));
        it.setDamageValue(Math.min(FlashlightItem.DUR_SPAN - 1, Math.max(0, dmg)));
    }

    public static InteractionHand heldHand(ServerPlayer p) {
        if (p.getMainHandItem().is(HHItems.FLASHLIGHT.get())) return InteractionHand.MAIN_HAND;
        if (p.getOffhandItem().is(HHItems.FLASHLIGHT.get())) return InteractionHand.OFF_HAND;
        return null;
    }

    public static boolean isOn(ServerPlayer p) {
        InteractionHand h = heldHand(p);
        if (h == null) return false;
        ItemStack it = p.getItemInHand(h);
        return on(it) && charge(it) > 0;
    }

    public static int batteries(ServerPlayer p) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (s.is(HHItems.BATTERY.get())) n += s.getCount();
        if (p.getOffhandItem().is(HHItems.BATTERY.get())) n += p.getOffhandItem().getCount();
        return n;
    }

    // ------------------------------------------------------------------ using it
    public static void use(ServerPlayer p, ItemStack it) {
        if (p.isShiftKeyDown()) {
            reload(p, it);
            return;
        }
        int c = charge(it);
        if (c <= 0) {
            Fx.sound(p.level(), "ghost.flashlight.dead", p.position(), 0.8f, 1f, 10);
            Fx.actionbar(p, Component.translatable("hollowhours.flash.dead"));
            stamp(it, 0, false);
            return;
        }
        boolean now = !on(it);
        stamp(it, c, now);
        Fx.sound(p.level(), now ? "ghost.flashlight.on" : "ghost.flashlight.off", p.position(), 0.8f, R.pitch(0.95, 1.1), 10);
        if (!now) clearLights(p);
    }

    public static boolean reload(ServerPlayer p, ItemStack flash) {
        if (charge(flash) >= FULL) {
            Fx.actionbar(p, Component.translatable("hollowhours.flash.full"));
            return false;
        }
        if (!p.getAbilities().instabuild) {
            boolean took = false;
            for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
                ItemStack s = p.getInventory().getItem(i);
                if (s.is(HHItems.BATTERY.get())) {
                    s.shrink(1);
                    took = true;
                    break;
                }
            }
            if (!took) {
                Fx.actionbar(p, Component.translatable("hollowhours.flash.nobattery"));
                Fx.sound(p.level(), "ghost.flashlight.click", p.position(), 0.7f, 1f, 8);
                return false;
            }
        }
        stamp(flash, FULL, on(flash));
        Fx.sound(p.level(), "ghost.flashlight.reload", p.position(), 0.9f, 1f, 10);
        Fx.actionbar(p, Component.translatable("hollowhours.flash.reloaded"));
        return true;
    }

    public static void drain(ServerPlayer p, int ticks) {
        InteractionHand h = heldHand(p);
        ItemStack it = h != null ? p.getItemInHand(h) : ItemStack.EMPTY;
        if (it.isEmpty()) {
            for (ItemStack s : p.getInventory().items) if (s.is(HHItems.FLASHLIGHT.get())) {
                it = s;
                break;
            }
        }
        if (it.isEmpty()) return;
        stamp(it, charge(it) - ticks, on(it));
    }

    // ------------------------------------------------------------------ per tick: battery + beam
    public static void tick(ServerPlayer p, long now) {
        InteractionHand h = heldHand(p);
        ItemStack it = h == null ? ItemStack.EMPTY : p.getItemInHand(h);
        if (it.isEmpty() || !on(it)) {
            if (LIT.containsKey(p.getUUID())) clearLights(p);
            return;
        }
        int c = charge(it) - 1;
        if (now % 100 == 0 || c <= 0) stamp(it, c, c > 0);
        else it.getOrCreateTag().putInt("charge", c);
        if (c <= 0) {
            Fx.sound(p.level(), "ghost.flashlight.dead", p.position(), 0.8f, 1f, 10);
            Fx.actionbar(p, Component.translatable("hollowhours.flash.dead"));
            clearLights(p);
            return;
        }
        if (now % 2 != 0) return;
        // flicker: low battery, or a ghost close by
        double near = 99;
        for (var g : GhostBrain.ghostsNear(p.serverLevel(), p.position(), 16)) near = Math.min(near, g.position().distanceTo(p.position()));
        boolean low = c < FULL * 0.15;
        if ((low && R.chance(0.08)) || (near < 16 && R.chance(0.25 * (1 - near / 16)))) {
            clearLights(p);
            if (R.chance(0.3)) Fx.sound(p.level(), "ghost.flashlight.flicker", p.position(), 0.7f, R.pitch(0.9, 1.2), 8);
            return;
        }
        placeBeam(p);
    }

    static void placeBeam(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        Vec3 eye = p.getEyePosition(), dir = p.getViewVector(1f);
        BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(dir.scale(RANGE)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        Set<Long> want = new HashSet<>();
        double len = hit.getType() == HitResult.Type.MISS ? RANGE : hit.getLocation().distanceTo(eye);
        // a pool of light where it hits + a couple along the way
        Vec3 end = eye.add(dir.scale(Math.max(1, len - 0.6)));
        want.add(BlockPos.containing(end).asLong());
        if (len > 8) want.add(BlockPos.containing(eye.add(dir.scale(len * 0.5))).asLong());
        want.add(BlockPos.containing(eye.add(dir.scale(Math.min(3, len - 0.5)))).asLong());
        Set<Long> cur = LIT.computeIfAbsent(p.getUUID(), k -> new HashSet<>());
        for (Long l : new HashSet<>(cur)) if (!want.contains(l)) {
            removeLight(level, BlockPos.of(l));
            cur.remove(l);
        }
        int i = 0;
        for (Long l : want) {
            BlockPos pos = BlockPos.of(l);
            BlockState s = level.getBlockState(pos);
            int lv = i == 0 ? 14 : 11;
            i++;
            if (s.isAir()) {
                level.setBlock(pos, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, lv), 3);
                cur.add(l);
                data(level).add(l);
            } else if (s.is(Blocks.LIGHT)) cur.add(l);
        }
    }

    static void removeLight(ServerLevel level, BlockPos pos) {
        if (level.getBlockState(pos).is(Blocks.LIGHT)) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        data(level).remove(pos.asLong());
    }

    public static void clearLights(ServerPlayer p) {
        Set<Long> cur = LIT.remove(p.getUUID());
        if (cur == null) return;
        for (Long l : cur) removeLight(p.serverLevel(), BlockPos.of(l));
    }

    // ------------------------------------------------------------------ saved list so a crash never leaves light blocks behind
    public static final class Data extends SavedData {
        final Set<Long> placed = new HashSet<>();

        static Data load(CompoundTag tag) {
            Data d = new Data();
            ListTag l = tag.getList("lights", Tag.TAG_LONG);
            for (Tag t : l) d.placed.add(((LongTag) t).getAsLong());
            return d;
        }

        void add(long l) {
            if (placed.add(l)) setDirty();
        }

        void remove(long l) {
            if (placed.remove(l)) setDirty();
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            ListTag l = new ListTag();
            for (long x : placed) l.add(LongTag.valueOf(x));
            tag.put("lights", l);
            return tag;
        }
    }

    static Data data(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(Data::load, Data::new, "hollowhours_flashlights");
    }

    /** On world load: remove light blocks left behind. */
    public static void cleanupOld(ServerLevel level) {
        Data d = data(level);
        for (Long l : new HashSet<>(d.placed)) {
            BlockPos pos = BlockPos.of(l);
            if (level.isLoaded(pos) && level.getBlockState(pos).is(Blocks.LIGHT)) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            if (level.isLoaded(pos)) d.remove(l);
        }
    }
}
