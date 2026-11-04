package com.helperbuddy.net;

import com.helperbuddy.Helpers;
import com.helperbuddy.brain.Bag;
import com.helperbuddy.brain.BuildJob;
import com.helperbuddy.entity.HelperEntity;
import com.helperbuddy.entity.Mode;
import com.helperbuddy.gen.Gen;
import com.helperbuddy.talk.Say;
import com.helperbuddy.talk.Talk;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/** Buttons of the helper screens, run on the server for the helper's owner only. */
public final class Actions {
    private Actions() {}

    static void say(ServerPlayer p, HelperEntity h, String text) {
        Talk.handle(p, h, text, false);
    }

    public static void run(ServerPlayer p, int entity, UUID uuid, String action, CompoundTag a) {
        HelperEntity h = Helpers.byId(p.server, entity, uuid);
        if (h == null) {
            p.sendSystemMessage(net.minecraft.network.chat.Component.literal(Say.t(p, "§6[ผู้ช่วย]§r หาผู้ช่วยตัวนี้ไม่เจอ (อาจอยู่ในพื้นที่ที่ยังไม่โหลด)", "§6[Helper]§r That helper can't be found (its area may not be loaded)")));
            return;
        }
        if (!h.isOwner(p)) return;
        switch (action) {
            case "refresh" -> {
            }
            case "follow" -> say(p, h, "follow me");
            case "stay" -> say(p, h, "stay");
            case "come" -> say(p, h, "come");
            case "mine" -> say(p, h, "go mining");
            case "chop" -> say(p, h, "chop wood");
            case "food" -> say(p, h, "find food");
            case "farm" -> say(p, h, "farm");
            case "guard" -> say(p, h, "guard here");
            case "cancel" -> say(p, h, "cancel");
            case "resume" -> say(p, h, "resume");
            case "status" -> say(p, h, "status");
            case "open" -> {
                HBNet.openMenu(p, h);
                return;
            }
            case "torch" -> say(p, h, "place torches");
            case "flatten" -> say(p, h, "flatten the ground");
            case "deliver" -> say(p, h, "bring me the items");
            case "deposit" -> say(p, h, "put things in the chest");
            case "cook" -> say(p, h, "cook");
            case "eat" -> say(p, h, "eat");
            case "shear" -> say(p, h, "shear sheep");
            case "breed" -> say(p, h, "breed animals");
            case "smelt" -> say(p, h, "smelt ores");
            case "dig" -> {
                int depth = a.getInt("depth"), length = a.getInt("length"), branch = a.getInt("branch");
                String txt = "build a mine" + (a.getBoolean("flat") ? " flat" : depth > 0 ? " " + depth + " deep" : "") + (length > 0 ? " " + length + " long" : "") + (a.contains("branch") ? " branch " + branch : "");
                say(p, h, txt);
            }
            case "gather" -> say(p, h, "collect " + a.getString("what"));
            case "talk" -> Talk.handle(p, h, a.getString("text"));
            case "toggle" -> {
                String k = a.getString("k");
                h.settings.set(k, !h.settings.get(k));
                if ((k.equals("fight") || k.equals("hitPlayers")) && !h.settings.get(k)) h.brain().combat().setTarget(null, false, false);
            }
            case "ore" -> {
                String k = a.getString("k");
                h.settings.setOre(k, !h.settings.ore(k));
            }
            case "bag" -> {
                h.openBag(p);
                return;
            }
            case "give_held" -> giveHeld(p, h);
            case "take_all" -> {
                int n = Bag.giveAllTo(h, p, s -> true);
                Say.to(p, h, n > 0 ? "ส่งของคืนให้แล้ว " + n + " ชิ้น" : "กระเป๋าผมว่างอยู่", n > 0 ? "Gave you " + n + " items" : "My bag is empty");
            }
            case "build" -> {
                Gen.Opts o = BuildJob.loadOpts(a.getCompound("opts"));
                if (a.getCompound("opts").contains("speed")) o.speed = Math.max(0, Math.min(3, a.getCompound("opts").getInt("speed")));
                Talk.buildFromMenu(p, h, o);
            }
            case "build_take" -> {
                int n = h.brain().build().takeFrom(p);
                Say.to(p, h, n > 0 ? "รับบล็อกไปแล้ว " + n + " ชิ้น ขอบคุณครับ" : "ไม่มีบล็อกที่ต้องใช้ในกระเป๋าคุณเลย", n > 0 ? "Took " + n + " blocks, thanks!" : "You don't carry any of the blocks I need");
            }
            case "build_cancel" -> {
                h.brain().build().cancel();
                h.setMode(Mode.STAY);
                Say.to(p, h, "หยุดสร้างแล้ว ส่วนที่สร้างไปแล้วยังอยู่นะ", "Stopped building, what's built stays");
            }
            case "rename" -> {
                String n = a.getString("name").trim();
                if (!n.isEmpty()) h.setHelperName(n);
            }
            case "skin" -> h.setSkin(h.skin() + 1);
            default -> {
            }
        }
        HBNet.refresh(p, h);
    }

    static void giveHeld(ServerPlayer p, HelperEntity h) {
        ItemStack held = p.getItemInHand(InteractionHand.MAIN_HAND);
        if (held.isEmpty()) {
            Say.to(p, h, "ถือของที่จะให้ไว้ในมือก่อนนะ", "Hold the item you want to give me first");
            return;
        }
        ItemStack st = held.copy();
        if (Bag.isTool(st) && h.tool().isEmpty()) {
            h.setTool(st);
            st = ItemStack.EMPTY;
        } else st = Bag.add(h, st);
        int given = held.getCount() - st.getCount();
        p.setItemInHand(InteractionHand.MAIN_HAND, st);
        Say.to(p, h, given > 0 ? "ได้รับ " + held.getHoverName().getString() + " " + given + " ชิ้นแล้ว ขอบคุณครับ!" : "กระเป๋าผมเต็มแล้ว",
                given > 0 ? "Got " + given + " " + held.getHoverName().getString() + ", thanks!" : "My bag is full");
    }
}
