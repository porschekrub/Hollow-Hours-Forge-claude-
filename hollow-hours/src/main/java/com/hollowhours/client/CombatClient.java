package com.hollowhours.client;

import com.hollowhours.HollowHours;
import com.hollowhours.combat.GunItem;
import com.hollowhours.net.C2SCombat;
import com.hollowhours.net.HHNet;
import com.hollowhours.net.S2CCombat;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ComputeFovModifierEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** Fighting on the client: dodge / reload keys, the struggle bar, recoil, aiming zoom, the ammo counter. */
@Mod.EventBusSubscriber(modid = HollowHours.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class CombatClient {
    private CombatClient() {}

    public static final KeyMapping DODGE = new KeyMapping("key.hollowhours.dodge", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V, "key.categories.hollowhours");
    public static final KeyMapping RELOAD = new KeyMapping("key.hollowhours.reload", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R, "key.categories.hollowhours");

    @Mod.EventBusSubscriber(modid = HollowHours.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Keys {
        private Keys() {}

        @SubscribeEvent
        public static void keys(RegisterKeyMappingsEvent e) {
            e.register(DODGE);
            e.register(RELOAD);
        }
    }

    public static float strength = 0;
    public static int meleeLvl, gunLvl;
    static float struggle = -1, struggleShown;
    static int struggleTicks, resultTicks, result, mashes;

    public static void packet(S2CCombat m) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        switch (m.type()) {
            case S2CCombat.STATS -> {
                strength = m.a();
                meleeLvl = (int) m.b();
                gunLvl = (int) m.c();
            }
            case S2CCombat.STRUGGLE -> {
                if (m.a() < 0) {
                    struggle = -1;
                    result = (int) m.c();
                    resultTicks = 30;
                } else {
                    if (struggle < 0) mashes = 0;
                    struggle = m.a();
                    struggleTicks = (int) m.b();
                }
            }
            case S2CCombat.RECOIL -> {
                if (p != null) {
                    p.setXRot(Mth.clamp(p.getXRot() - m.a(), -90, 90));
                    p.setYRot(p.getYRot() + m.b());
                    ClientFx.addShake(0.12f * m.a() / 3f, 3);
                }
            }
            case S2CCombat.DODGE -> {
                if (p != null) {
                    Vec3 v = p.getDeltaMovement();
                    p.setDeltaMovement(m.a(), p.onGround() ? 0.26 : Math.max(v.y, 0.05), m.b());
                }
            }
            default -> {}
        }
    }

    public static boolean struggling() {
        return struggle >= 0;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (resultTicks > 0) resultTicks--;
        struggleShown += (Math.max(0, struggle) - struggleShown) * 0.5f;
        if (mc.player == null) {
            struggle = -1;
            return;
        }
        if (struggling()) {
            if (struggleTicks > 0) struggleTicks--;
            int n = 0;
            while (mc.options.keyJump.consumeClick()) n++;
            while (mc.options.keyAttack.consumeClick()) n++;
            while (mc.options.keyUse.consumeClick()) n++;
            for (int i = 0; i < Math.min(n, 2); i++) HHNet.toServer(new C2SCombat(C2SCombat.STRUGGLE, 0, 0));
            mashes += n;
            return;
        }
        if (mc.screen != null) return;
        while (DODGE.consumeClick())
            HHNet.toServer(new C2SCombat(C2SCombat.DODGE, mc.player.input.forwardImpulse, mc.player.input.leftImpulse));
        while (RELOAD.consumeClick())
            if (mc.player.getMainHandItem().getItem() instanceof GunItem) HHNet.toServer(new C2SCombat(C2SCombat.RELOAD, 0, 0));
    }

    /** No swinging and no using items while wriggling in a ghost's grip. */
    @SubscribeEvent
    public static void interact(InputEvent.InteractionKeyMappingTriggered e) {
        if (struggling()) {
            e.setSwingHand(false);
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void fov(ComputeFovModifierEvent e) {
        ItemStack s = e.getPlayer().getMainHandItem();
        if (s.getItem() instanceof GunItem g && e.getPlayer().isShiftKeyDown()) e.setNewFovModifier(e.getNewFovModifier() * g.spec.zoom());
    }

    // ------------------------------------------------------------------ HUD
    public static void renderHud(GuiGraphics g, float partial, int sw, int sh) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        Font f = mc.font;
        if (struggling()) {
            int w = 220, h = 14;
            float t = (mc.level == null ? 0 : mc.level.getGameTime()) + partial;
            int x = (sw - w) / 2 + Math.round(Mth.sin(t * 2.7f) * 2 * (1 - struggleShown));
            int y = sh / 2 + 38;
            g.fill(x - 3, y - 3, x + w + 3, y + h + 3, 0xC0000000);
            g.fill(x, y, x + w, y + h, 0xFF3A1010);
            int fillW = Math.round(w * Mth.clamp(struggleShown, 0, 1));
            int col = struggleShown > 0.7f ? 0xFF6FE06F : struggleShown > 0.35f ? 0xFFE8C547 : 0xFFE0504A;
            g.fill(x, y, x + fillW, y + h, col);
            g.fill(x + fillW - 2, y, x + fillW, y + h, 0xFFFFFFFF);
            Component title = Component.translatable("hollowhours.struggle.title");
            g.drawCenteredString(f, title, sw / 2, y - 14, 0xFFFFE0E0);
            Component hint = Component.translatable("hollowhours.struggle.hint", mc.options.keyJump.getTranslatedKeyMessage(), mc.options.keyAttack.getTranslatedKeyMessage());
            g.drawCenteredString(f, hint, sw / 2, y + h + 6, 0xFFE8E8E8);
            int tw = Math.round(w * Math.min(1, struggleTicks / 90f));
            g.fill(x, y + h + 18, x + tw, y + h + 20, 0xFFB03030);
        } else if (resultTicks > 0) {
            Component c = Component.translatable(result == 1 ? "hollowhours.struggle.won" : "hollowhours.struggle.lost");
            g.pose().pushPose();
            g.pose().translate(sw / 2f, sh / 2f + 40, 0);
            g.pose().scale(2f, 2f, 1);
            g.drawCenteredString(f, c, 0, 0, result == 1 ? 0xFF7CFC7C : 0xFFFF5050);
            g.pose().popPose();
        }
        ItemStack s = mc.player.getMainHandItem();
        if (s.getItem() instanceof GunItem gun) {
            int ammo = GunItem.ammo(s);
            int spare = GunItem.ammoIn(mc.player, gun.spec.ammo().get());
            String txt = ammo + " / " + gun.spec.mag();
            int x = sw / 2 + 14, y = sh / 2 + 6;
            g.drawString(f, txt, x, y, ammo == 0 ? 0xFFFF6060 : 0xFFF0E6B0, true);
            g.drawString(f, "+" + spare, x + f.width(txt) + 4, y, 0xFF9A9A9A, true);
            if (GunItem.reloading(s)) g.drawString(f, Component.translatable("hollowhours.gun.reloading"), x, y + 10, 0xFFC0C0C0, true);
            else if (ammo == 0) g.drawString(f, Component.translatable("hollowhours.gun.pressreload", RELOAD.getTranslatedKeyMessage()), x, y + 10, 0xFFFF9090, true);
        }
    }
}
