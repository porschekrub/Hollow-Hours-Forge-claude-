package com.hollowhours.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.hollowhours.HollowHours;
import com.hollowhours.registry.HHSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * The emergency alert on the day the haunting starts: the phone buzzes, the alert tone screams, then a voice reads the
 * warning (Thai or English, by the game language) with subtitles at the bottom of the screen and the alert on the
 * phone in the corner. Without a working phone: sirens, and the warning comes in chat.
 */
public final class PhoneAlert {
    private PhoneAlert() {}

    static final int LINES = 6, VOICE_START = 96;
    static boolean active, phone;
    static int t;
    static String lang = "en";
    static int[] starts = new int[LINES], lens = new int[LINES];
    static int end;

    public static boolean active() {
        return active;
    }

    public static void start(boolean hasPhone) {
        Minecraft mc = Minecraft.getInstance();
        lang = mc.options.languageCode.startsWith("th") ? "th" : "en";
        phone = hasPhone;
        float[] secs = durations(lang);
        int at = VOICE_START;
        for (int i = 0; i < LINES; i++) {
            starts[i] = at;
            lens[i] = Math.max(20, Math.round(secs[i] * 20));
            at += lens[i] + 8;
        }
        end = at + 50;
        t = 0;
        active = true;
    }

    static float[] durations(String lang) {
        float[] d = {2.4f, 4.2f, 2.2f, 3.0f, 2.8f, 3.4f};
        try {
            var res = Minecraft.getInstance().getResourceManager().getResource(HollowHours.id("phone/alert_" + lang + ".json"));
            if (res.isPresent()) {
                try (var r = new InputStreamReader(res.get().open(), StandardCharsets.UTF_8)) {
                    JsonArray a = JsonParser.parseReader(r).getAsJsonArray();
                    for (int i = 0; i < Math.min(LINES, a.size()); i++) d[i] = a.get(i).getAsJsonObject().get("seconds").getAsFloat();
                }
            }
        } catch (Exception e) {
            HollowHours.LOG.warn("[Hollow Hours] alert timings could not be read", e);
        }
        return d;
    }

    static void play(String sound, float vol, float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(HHSounds.get(sound), pitch, vol));
    }

    static void tick() {
        if (!active) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            active = false;
            return;
        }
        if (phone) {
            if (t == 0 || t == 9) play("phone.notify", 1f, 1f);
            if (t == 22) play("phone.eas", 0.9f, 1f);
        } else if (t == 10) play("phone.siren", 0.8f, 1f);
        for (int i = 0; i < LINES; i++) if (t == starts[i]) play("phone.voice." + lang + "." + i, phone ? 1f : 0.55f, 1f);
        if (++t >= end) active = false;
    }

    /** Subtitle + the phone in the corner (drawn above everything). */
    public static void render(ForgeGui gui, GuiGraphics g, float partial, int w, int h) {
        if (!active) return;
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        float time = t + partial;
        // ---- the phone sliding up in the bottom-right corner
        if (phone) {
            float in = Mth.clamp(time / 10f, 0, 1), out = Mth.clamp((end - time) / 12f, 0, 1);
            float k = Math.min(in, out);
            k = k * k * (3 - 2 * k);
            int pw = 92, ph = 150, px = w - pw - 10, py = h - Math.round(ph * k) - 6;
            boolean flash = time < VOICE_START && (int) (time / 6) % 2 == 0;
            PhoneScreen.drawBody(g, px, py, pw, ph);
            int sx = px + 5, sy = py + 12, sw = pw - 10, sh = ph - 24;
            g.fill(sx, sy, sx + sw, sy + sh, flash ? 0xFFB01010 : 0xFF1A0505);
            g.fill(sx, sy, sx + sw, sy + 16, 0xFFD01818);
            g.drawCenteredString(font, Component.translatable("hollowhours.alert.title"), sx + sw / 2, sy + 4, 0xFFFFFFFF);
            List<FormattedCharSequence> body = font.split(Component.translatable("hollowhours.alert.short"), sw - 6);
            int yy = sy + 22;
            for (FormattedCharSequence l : body) {
                if (yy > sy + sh - 10) break;
                g.drawString(font, l, sx + 3, yy, 0xFFF0D0D0, false);
                yy += 10;
            }
        }
        // ---- subtitles
        Component sub = null;
        if (time < VOICE_START) sub = Component.translatable(phone ? "hollowhours.alert.sub.tone" : "hollowhours.alert.sub.siren");
        else for (int i = 0; i < LINES; i++) if (time >= starts[i] && time < starts[i] + lens[i] + 8) sub = Component.translatable("hollowhours.alert.line." + i);
        if (sub != null) {
            List<FormattedCharSequence> lines = font.split(sub, Math.min(w - 40, 320));
            int lh = 11, boxH = lines.size() * lh + 6, top = h - 72 - boxH;
            int maxW = 0;
            for (FormattedCharSequence l : lines) maxW = Math.max(maxW, font.width(l));
            g.fill(w / 2 - maxW / 2 - 6, top, w / 2 + maxW / 2 + 6, top + boxH, 0xB0000000);
            int y = top + 4;
            for (FormattedCharSequence l : lines) {
                g.drawString(font, l, w / 2 - font.width(l) / 2, y, time < VOICE_START ? 0xFFFF8080 : 0xFFFFFFFF, false);
                y += lh;
            }
        }
    }

    static void reset() {
        active = false;
    }
}
