package com.helperbuddy.brain;

import com.helperbuddy.HelperBuddy;
import com.helperbuddy.entity.HelperEntity;
import com.helperbuddy.entity.Mode;
import com.helperbuddy.talk.Say;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Errands that take the helper far from its owner, on its own: going to another dimension through a nether portal,
 * and hunting a kind of monster or animal (for what it drops, or just to get rid of them).
 */
final class Errands {
    private Errands() {}

    // ================================================================== to another dimension, through a portal
    static String dimName(String dim, boolean th) {
        return switch (dim) {
            case Sources.NETHER -> th ? "เนเธอร์" : "the Nether";
            case Sources.END -> th ? "ดิเอนด์" : "the End";
            case Sources.OVERWORLD -> th ? "โลกปกติ" : "the Overworld";
            default -> dim;
        };
    }

    /** Can a helper there get to `want` by itself? (Overworld and Nether, through a nether portal.) */
    static boolean portalWay(String here, String want) {
        return here.equals(Sources.OVERWORLD) && want.equals(Sources.NETHER) || here.equals(Sources.NETHER) && want.equals(Sources.OVERWORLD);
    }

    /** The nether portal a helper would take from where it is (near itself, else near its owner), or null. */
    @Nullable
    static BlockPos portalFor(HelperEntity h) {
        if (!(h.level() instanceof ServerLevel level)) return null;
        BlockPos portal = Navigator.findPortal(level, h.blockPosition(), 128);
        ServerPlayer o = h.owner();
        if (portal == null && o != null && o.level() == level) portal = Navigator.findPortal(level, o.blockPosition(), 128);
        if (portal == null || !level.getBlockState(portal).is(Blocks.NETHER_PORTAL)) return null;
        // the bottom block of the portal: that is where its feet go
        while (level.getBlockState(portal.below()).is(Blocks.NETHER_PORTAL)) portal = portal.below();
        return portal;
    }

    /** A place to stand next to the portal it came out of (staying inside would send it back). */
    @Nullable
    static BlockPos outOfPortal(ServerLevel level, BlockPos at) {
        for (int r = 1; r <= 4; r++)
            for (int dx = -r; dx <= r; dx++)
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    for (int dy = 1; dy >= -2; dy--) {
                        BlockPos p = at.offset(dx, dy, dz);
                        if (Mover.standable(level, p) && !level.getBlockState(p).is(Blocks.NETHER_PORTAL) && !level.getBlockState(p.above()).is(Blocks.NETHER_PORTAL)) return p;
                    }
                }
        return null;
    }

    static void travel(Tasks t) {
        HelperEntity h = t.h;
        Brain b = t.b;
        ServerLevel level = b.level();
        String want = t.spec.getString("dim");
        String here = level.dimension().location().toString();
        if (here.equals(want)) {
            if (level.getBlockState(h.blockPosition()).is(Blocks.NETHER_PORTAL) || level.getBlockState(h.blockPosition().above()).is(Blocks.NETHER_PORTAL)) {
                BlockPos out = outOfPortal(level, h.blockPosition());
                if (out != null) {
                    com.helperbuddy.Helpers.hold(level, out);
                    h.teleportTo(out.getX() + 0.5, out.getY(), out.getZ() + 0.5);
                }
            }
            Say.owner(h, "ถึง" + dimName(want, true) + "แล้ว (" + Navigator.xyz(h.blockPosition()) + "§r)", "I'm in " + dimName(want, false) + " now (" + Navigator.xyz(h.blockPosition()) + "§r)");
            t.done(true);
            return;
        }
        if (!portalWay(here, want)) {
            Say.owner(h, "จากที่นี่ผมไป" + dimName(want, true) + "เองไม่ได้ พาผมไปด้วยนะ แล้วสั่งใหม่ที่นั่น", "I can't get to " + dimName(want, false) + " from here by myself. Take me along and ask again there");
            t.fail();
            return;
        }
        BlockPos portal = t.spec.contains("portal") ? BlockPos.of(t.spec.getLong("portal")) : null;
        if (portal == null || !level.getBlockState(portal).is(Blocks.NETHER_PORTAL)) {
            if (h.tickCount % 20 != 0 && t.spec.contains("looked")) return;
            t.spec.putBoolean("looked", true);
            portal = portalFor(h);
            if (portal == null) {
                Say.owner(h, "§eไม่เจอประตูเนเธอร์ในระยะ 128 บล็อก§r ผมเลยไป" + dimName(want, true) + "ไม่ได้ สั่ง \"สร้างประตูเนเธอร์\" (ส่งออบซิเดียน 10 ก้อน + หินเหล็กไฟให้ผมก่อน) หรือพาผมไปเองก็ได้",
                        "§eNo nether portal within 128 blocks§r, so I can't get to " + dimName(want, false) + ". Say \"build a nether portal\" (give me 10 obsidian and a flint and steel first), or take me there yourself");
                t.fail();
                return;
            }
            t.spec.putLong("portal", portal.asLong());
            t.phase = 0;
        }
        Vec3 c = Vec3.atBottomCenterOf(portal);
        double d = h.position().distanceTo(c);
        if (d > 1.2) {
            if (d > 40) {
                b.mover().teleportNear(portal);
                return;
            }
            Mover.Res r = b.mover().moveTo(portal, 1.2, 0.8);
            if (r == Mover.Res.NO_PATH || b.mover().stuck(60) || r == Mover.Res.ARRIVED) {
                com.helperbuddy.Helpers.hold(level, portal);
                h.teleportTo(c.x, c.y, c.z);
            }
            return;
        }
        // standing in the portal: the game takes it through (a portal on the other side is found or made, like for a player)
        b.mover().stop();
        if (!level.getBlockState(h.blockPosition()).is(Blocks.NETHER_PORTAL)) h.teleportTo(c.x, c.y, c.z);
        if (++t.phase > 20 * 20) {
            Say.owner(h, "ประตูเนเธอร์ไม่พาผมไปไหนเลย (เซิร์ฟเวอร์อาจปิดเนเธอร์ไว้) ขอเลิกก่อนนะ", "The portal isn't taking me anywhere (the Nether may be turned off on this server). I'll stop here");
            t.fail();
        }
    }

    // ================================================================== hunting a kind of mob
    static List<EntityType<?>> types(Tasks t) {
        List<EntityType<?>> out = new ArrayList<>();
        for (Tag x : t.spec.getList("mobs", Tag.TAG_STRING)) {
            String id = x.getAsString();
            EntityType.byString(id.contains(":") ? id : "minecraft:" + id).ifPresent(out::add);
        }
        return out;
    }

    static String mobNames(List<EntityType<?>> types) {
        StringBuilder sb = new StringBuilder();
        for (EntityType<?> ty : types) {
            if (sb.length() > 0) sb.append(" / ");
            sb.append(ty.getDescription().getString());
        }
        return sb.toString();
    }

    static void slay(Tasks t) {
        HelperEntity h = t.h;
        Brain b = t.b;
        ServerLevel level = b.level();
        List<EntityType<?>> types = types(t);
        Item want = t.spec.contains("item") ? BuiltInRegistries.ITEM.get(HelperBuddy.rl(t.spec.getString("item"))) : null;
        if (want == Items.AIR) want = null;
        if (types.isEmpty()) {
            Say.owner(h, "ผมไม่รู้จักตัวที่ให้ไปล่าเลย", "I don't know the creature you want me to hunt");
            t.fail();
            return;
        }
        if (!t.spec.contains("had")) t.spec.putInt("had", want == null ? 0 : Bag.count(h, want));
        int n = t.spec.contains("n") ? t.spec.getInt("n") : want != null ? 8 : 5;
        // what they dropped
        if (h.tickCount % 10 == 0) {
            final Item w = want;
            b.work.pickup(st -> w == null || st.is(w) || !Bag.isFull(h), 9);
        }
        int have = want != null ? Bag.count(h, want) - t.spec.getInt("had") : t.count;
        String names = mobNames(types);
        if (have >= n) {
            if (want != null) {
                Say.owner(h, "ได้ §e" + Skills.name(want) + "§r ครบ " + have + " ชิ้นแล้ว! (ล่า" + names + "ไป " + t.count + " ตัว)", "Got §e" + have + " " + Skills.name(want) + "§r! (from " + t.count + " " + names + ")");
                if (!b.plan.active()) t.deliver(want, have);
            } else Say.owner(h, "จัดการ" + names + "ไปแล้ว §e" + t.count + "§r ตัว เรียบร้อย!", "Took care of §e" + t.count + "§r " + names + ". Done!");
            h.act("cheer");
            t.done(false);
            return;
        }
        LivingEntity cur = b.combat().target();
        if (cur != null && cur.isAlive()) return; // (the fight itself is the combat code's job; a monster that attacks it comes first)
        if (h.tickCount % 10 != 3) {
            b.work.roamTick(Mode.TASK, where(t, want));
            return;
        }
        int r = Brain.radius();
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(h.blockPosition()).inflate(r, 40, r), e -> e.isAlive() && types.contains(e.getType()))) {
            if (t.skip.contains(e.getUUID()) || !b.combat().canTarget(e, true) || e.hasCustomName()) continue;
            double d = e.distanceToSqr(h);
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        if (best != null) {
            // too far to walk: next to it in one hop
            if (bd > 48 * 48) {
                BlockPos land = Mover.landAround(level, best.blockPosition().getX(), best.blockPosition().getZ(), best.blockPosition().getY(), false);
                if (land != null) b.mover().jumpTo(land);
            }
            b.combat().setTarget(best, true, false);
            if (b.combat().target() != best) t.skip.add(best.getUUID());
            b.work.roam = null;
            b.work.roamFails = 0;
            return;
        }
        if (level.getDifficulty() == Difficulty.PEACEFUL && types.stream().anyMatch(ty -> ty.getCategory() == MobCategory.MONSTER)) {
            Say.owner(h, "เกมตั้งความยากเป็น Peaceful อยู่ มอนสเตอร์ไม่เกิดเลย ผมเลยล่า" + names + "ไม่ได้", "The game is on Peaceful: no monsters spawn, so I can't hunt " + names);
            t.fail();
            return;
        }
        Sources.Where w = where(t, want);
        Say.notify(h, "slay:none", 20 * 120, "ยังไม่เจอ" + names + "แถวนี้เลย" + (w != null ? " (มันอยู่แถว" + w.th() + ")" : "") + " ผมจะเดินหาต่อไปเรื่อยๆ นะ",
                "No " + names + " around yet" + (w != null ? " (they live in " + w.en() + ")" : "") + ". I'll keep looking");
    }

    @Nullable
    static Sources.Where where(Tasks t, @Nullable Item want) {
        if (want == null) return null;
        Sources.Drop d = Sources.dropOf(want);
        if (d == null) return null;
        Sources.Where w = d.where();
        // "night" places are no place to go to: only real biomes and structures are
        return w.biomes().length > 0 || w.structure() != null ? w : null;
    }

    static ListTag list(String[] ids) {
        ListTag l = new ListTag();
        for (String s : ids) l.add(StringTag.valueOf(s));
        return l;
    }
}
