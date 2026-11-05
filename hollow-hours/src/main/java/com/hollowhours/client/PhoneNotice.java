package com.hollowhours.client;

import com.hollowhours.registry.HHSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/** A message popping up on the phone in the corner for a few seconds (the rescue story): green news or red news. */
public final class PhoneNotice {
    private PhoneNotice() {}

    record Notice(Component title, Component body, boolean danger) {}

    static final int SHOW = 200;
    static final Deque<Notice> QUEUE = new ArrayDeque<>();
    static Notice now;
    static int t;

    public static void show(String key, String[] args, boolean danger) {
        QUEUE.add(new Notice(Component.translatable(key + ".title"), Component.translatable(key, (Object[]) args), danger));
    }

    static void tick() {
        if (now == null) {
            if (QUEUE.isEmpty() || PhoneAlert.active()) return;
            now = QUEUE.poll();
            t = 0;
        }
        if (t == 0 || t == 8 || now.danger && t == 16)
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(HHSounds.get("phone.notify"), now.danger ? 0.8f : 1.1f, 1f));
        if (++t >= SHOW) now = null;
    }

    public static void render(GuiGraphics g, float partial) {
        int w = g.guiWidth(), h = g.guiHeight();
        if (now == null) return;
        Font font = Minecraft.getInstance().font;
        float time = t + partial;
        int pw = 112, ph = 150, px = w - pw - 10;
        float k = Math.min(Mth.clamp(time / 10f, 0, 1), Mth.clamp((SHOW - time) / 12f, 0, 1));
        k = k * k * (3 - 2 * k);
        int py = h - Math.round(ph * k) - 6;
        PhoneScreen.drawBody(g, px, py, pw, ph);
        int sx = px + 5, sy = py + 12, sw = pw - 10, sh = ph - 24;
        int head = now.danger ? 0xFFC01818 : 0xFF1E8C4A;
        g.fill(sx, sy, sx + sw, sy + sh, now.danger ? 0xFF1A0606 : 0xFF061A10);
        g.fill(sx, sy, sx + sw, sy + 16, head);
        float fit = Math.min(1f, (sw - 4) / (float) font.width(now.title));
        g.pose().pushPose();
        g.pose().translate(sx + sw / 2f, sy + 8 - 4 * fit, 0);
        g.pose().scale(fit, fit, 1);
        g.drawCenteredString(font, now.title, 0, 0, 0xFFFFFFFF);
        g.pose().popPose();
        // small text so the whole message fits on the screen
        float sc = 0.75f;
        List<FormattedCharSequence> lines = font.split(now.body, (int) ((sw - 6) / sc));
        g.pose().pushPose();
        g.pose().translate(sx + 3, sy + 20, 0);
        g.pose().scale(sc, sc, 1);
        int yy = 0;
        for (FormattedCharSequence l : lines) {
            if ((yy + 10) * sc > sh - 22) break;
            g.drawString(font, l, 0, yy, now.danger ? 0xFFF0D0D0 : 0xFFD8F0E0, false);
            yy += 10;
        }
        g.pose().popPose();
    }

    static void reset() {
        QUEUE.clear();
        now = null;
    }
}
