package com.hollowhours.client;

import com.hollowhours.HollowHours;
import com.hollowhours.config.HHClientConfig;
import com.hollowhours.net.S2CHud;
import com.hollowhours.net.S2CScreenFx;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Client screen effects and the small horror HUD: camera shake, colour fades, blood vignette, TV static, the
 * breath bar while hiding, stamina, and the ghost's blood-red typed speech.
 */
public final class ClientFx {
    private ClientFx() {}

    static final ResourceLocation BLOOD = HollowHours.id("textures/gui/blood.png");
    static final ResourceLocation VIGNETTE = HollowHours.id("textures/gui/vignette.png");
    static final ResourceLocation[] STATIC = {HollowHours.id("textures/gui/static_0.png"), HollowHours.id("textures/gui/static_1.png"),
            HollowHours.id("textures/gui/static_2.png"), HollowHours.id("textures/gui/static_3.png")};
    static final RandomSource RNG = RandomSource.create();

    // ------------------------------------------------------------------ state
    static float shake;
    static int shakeLeft, shakeTotal;
    static float blood;
    static int bloodLeft, bloodTotal;
    static float statik;
    static int staticLeft, staticTotal;
    static int darkLeft, darkTotal;
    static int glitchVariant = -1, glitchLeft, glitchTotal;
    static int heartLeft;

    record Fade(int rgb, int in, int hold, int out, long t0) {}

    static final List<Fade> FADES = new ArrayList<>();

    // hud
    static S2CHud hud = new S2CHud(0, 0, -1, 0, -1, false, false, 100);
    static long hudAt;

    // speech
    static int speechKind;
    static String speechText = "";
    static int speechStyle;
    static long speechT0 = -1;
    static int speechType, speechHold;

    static long ticks;

    static long now() {
        return ticks;
    }

    // ------------------------------------------------------------------ packets
    static void apply(S2CScreenFx m) {
        switch (m.type()) {
            case S2CScreenFx.SHAKE -> addShake(m.a(), m.b());
            case S2CScreenFx.FADE -> FADES.add(new Fade(m.b(), m.c(), m.d(), m.e(), ticks));
            case S2CScreenFx.BLOOD -> addBlood(m.a(), m.b());
            case S2CScreenFx.STATIC -> addStatic(m.a(), m.b());
            case S2CScreenFx.DARK_PULSE -> {
                darkLeft = Math.max(darkLeft, m.b());
                darkTotal = Math.max(1, darkLeft);
            }
            case S2CScreenFx.GLITCH_TEXT -> {
                glitchVariant = m.b();
                glitchLeft = glitchTotal = Math.max(1, m.c());
            }
            case S2CScreenFx.HEARTBEAT -> heartLeft = Math.max(heartLeft, m.b());
            default -> {}
        }
    }

    public static void addShake(float strength, int t) {
        float s = (float) (strength * HHClientConfig.SHAKE.get());
        if (s <= 0) return;
        if (s >= shake * (shakeLeft / (float) Math.max(1, shakeTotal))) {
            shake = s;
            shakeLeft = shakeTotal = Math.max(1, t);
        }
    }

    public static void addBlood(float strength, int t) {
        blood = Math.max(blood * (bloodLeft / (float) Math.max(1, bloodTotal)), strength);
        bloodLeft = bloodTotal = Math.max(bloodLeft, t);
    }

    public static void addStatic(float strength, int t) {
        statik = Math.max(statik * (staticLeft / (float) Math.max(1, staticTotal)), strength);
        staticLeft = staticTotal = Math.max(staticLeft, t);
    }

    public static void fade(int rgb, int in, int hold, int out) {
        FADES.add(new Fade(rgb, in, hold, out, ticks));
    }

    static void hud(S2CHud m) {
        hud = m;
        hudAt = ticks;
    }

    static void speech(int kind, Component text, int style) {
        speechKind = kind;
        speechText = text.getString();
        speechStyle = style;
        speechT0 = ticks;
        int len = speechText.codePointCount(0, speechText.length());
        speechType = Mth.clamp((int) Math.ceil(len * 0.9), 10, 60);
        speechHold = Math.min(140, 70 + len * 2);
        if (style == 3) {
            speechType = 4;
            speechHold = 40;
        }
    }

    // ------------------------------------------------------------------ tick
    static void tick() {
        ticks++;
        if (shakeLeft > 0) shakeLeft--;
        if (bloodLeft > 0) bloodLeft--;
        if (staticLeft > 0) staticLeft--;
        if (darkLeft > 0) darkLeft--;
        if (glitchLeft > 0) glitchLeft--;
        if (heartLeft > 0) heartLeft--;
        for (Iterator<Fade> it = FADES.iterator(); it.hasNext(); ) {
            Fade f = it.next();
            if (ticks - f.t0 > f.in + f.hold + f.out) it.remove();
        }
        // stale hud (server stopped sending) disappears
        if (ticks - hudAt > 60 && hud.hide() != 0) hud = new S2CHud(0, 0, -1, 0, hud.stamina(), false, false, hud.soul());
    }

    /** Camera shake offsets: {yaw, pitch, roll}. */
    static float[] shakeOffsets(float partial) {
        if (shakeLeft <= 0 || shake <= 0) return new float[]{0, 0, 0};
        float k = (shakeLeft - partial) / (float) Math.max(1, shakeTotal);
        float a = shake * Math.max(0, k) * 6f;
        float t = (ticks + partial) * 1.7f;
        return new float[]{(Mth.sin(t * 3.1f) + RNG.nextFloat() - 0.5f) * a, (Mth.cos(t * 2.3f) + RNG.nextFloat() - 0.5f) * a * 0.8f, Mth.sin(t * 1.3f) * a * 0.6f};
    }

    // ------------------------------------------------------------------ rendering
    static void render(GuiGraphics g, float partial) {
        Minecraft mc = Minecraft.getInstance();
        int w = g.guiWidth(), h = g.guiHeight();
        renderHudBars(g, w, h);
        renderSpeech(g, w, h, partial);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // dark pulse (hiding, ghost close)
        if (darkLeft > 0) {
            float k = darkLeft / (float) Math.max(1, darkTotal);
            g.fill(0, 0, w, h, argb(k * 0.55f, 0));
        }
        // breath vignette while hiding
        if (hud.hide() != 0) {
            float b = hud.breath() / 100f;
            texture(g, VIGNETTE, w, h, 0.45f + 0.5f * b, 0f, 0f, 0f);
            if (b > 0.55f) texture(g, BLOOD, w, h, (b - 0.55f) * 0.9f * (0.75f + 0.25f * Mth.sin((ticks + partial) * 0.6f)), 1f, 1f, 1f);
        }
        if (bloodLeft > 0) {
            float k = (bloodLeft - partial) / (float) Math.max(1, bloodTotal);
            texture(g, BLOOD, w, h, Mth.clamp(blood * k, 0, 1), 1f, 1f, 1f);
        }
        if (staticLeft > 0) {
            float k = (staticLeft - partial) / (float) Math.max(1, staticTotal);
            renderStatic(g, w, h, statik * Math.max(0, k));
        }
        for (Fade f : FADES) {
            float t = ticks - f.t0 + partial, a;
            if (t < f.in) a = f.in <= 0 ? 1 : t / f.in;
            else if (t < f.in + f.hold) a = 1;
            else a = 1 - (t - f.in - f.hold) / Math.max(1, f.out);
            if (HHClientConfig.REDUCE_FLASHING.get() && (f.rgb & 0xFFFFFF) == 0xFFFFFF) a *= 0.35f;
            g.fill(0, 0, w, h, argb(Mth.clamp(a, 0, 1), f.rgb));
        }
        if (glitchLeft > 0 && glitchVariant >= 0) renderGlitchText(g, mc.font, w, h, Component.translatable("hollowhours.glitch." + glitchVariant).getString());
        RenderSystem.disableBlend();
    }

    public static void renderStatic(GuiGraphics g, int w, int h, float alpha) {
        if (alpha <= 0.01f) return;
        ResourceLocation tex = STATIC[(int) (ticks % STATIC.length)];
        float ox = RNG.nextFloat() * 64, oy = RNG.nextFloat() * 64;
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1, 1, 1, Mth.clamp(alpha, 0, 1));
        g.blit(tex, 0, 0, ox, oy, w, h, 128, 128);
        RenderSystem.setShaderColor(1, 1, 1, 1);
    }

    static void texture(GuiGraphics g, ResourceLocation tex, int w, int h, float alpha, float r, float gr, float b) {
        if (alpha <= 0.01f) return;
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(r, gr, b, Mth.clamp(alpha, 0, 1));
        g.blit(tex, 0, 0, 0, 0, w, h, w, h);
        RenderSystem.setShaderColor(1, 1, 1, 1);
    }

    public static int argb(float a, int rgb) {
        return ((int) (Mth.clamp(a, 0, 1) * 255) << 24) | (rgb & 0xFFFFFF);
    }

    static void renderGlitchText(GuiGraphics g, Font font, int w, int h, String text) {
        float s = 3f;
        g.pose().pushPose();
        g.pose().translate(w / 2f + (RNG.nextFloat() - 0.5f) * 6, h / 2f - 20 + (RNG.nextFloat() - 0.5f) * 4, 0);
        g.pose().scale(s, s, 1);
        int tw = font.width(text);
        g.drawString(font, text, -tw / 2 + 1, 0, 0x55000000, false);
        g.drawString(font, text, -tw / 2 - (RNG.nextInt(3) - 1), RNG.nextInt(3) - 1, 0xFF8B0000, false);
        if (RNG.nextFloat() < 0.3f) g.drawString(font, text, -tw / 2 + 2, 1, 0x66FFFFFF, false);
        g.pose().popPose();
    }

    static void renderHudBars(GuiGraphics g, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || KillCam.active()) return;
        int y = h - 59;
        // stamina (dash)
        if (hud.stamina() >= 0) {
            int bw = 90, x = w / 2 - bw / 2;
            g.fill(x - 1, y - 1, x + bw + 1, y + 3, 0xAA000000);
            int col = hud.exhausted() ? 0xFF8B0000 : hud.turbo() ? 0xFFE0E0E0 : 0xFF9A9A9A;
            g.fill(x, y, x + Math.round(bw * Mth.clamp(hud.stamina(), 0, 1)), y + 2, col);
            y -= 6;
        }
        // breath while hiding
        if (hud.hide() != 0) {
            int bw = 120, x = w / 2 - bw / 2, yy = h / 2 + 30;
            float b = Mth.clamp(hud.breath() / 100f, 0, 1);
            g.fill(x - 1, yy - 1, x + bw + 1, yy + 4, 0xAA000000);
            int col = b > 0.55f ? 0xFFB00000 : b > 0.3f ? 0xFFC0A060 : 0xFF8FB8C8;
            g.fill(x, yy, x + Math.round(bw * b), yy + 3, col);
            Component lab = Component.translatable(hud.hide() == 1 ? "hollowhours.hud.closet" : "hollowhours.hud.bed");
            g.drawCenteredString(mc.font, lab, w / 2, yy + 7, 0x80FFFFFF);
            if (hud.ghostDist() >= 0 && hud.ghostDist() < 12) {
                String dots = ".".repeat(Math.max(1, 4 - (int) (hud.ghostDist() / 3)));
                g.drawCenteredString(mc.font, Component.literal(dots).withStyle(ChatFormatting.DARK_RED), w / 2, yy - 11, 0xFFFFFFFF);
            }
        }
    }

    static void renderSpeech(GuiGraphics g, int w, int h, float partial) {
        if (speechT0 < 0) return;
        float t = ticks - speechT0 + partial;
        if (t > speechType + speechHold) {
            speechT0 = -1;
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        int[] cps = speechText.codePoints().toArray();
        int shown = Math.min(cps.length, (int) Math.ceil(cps.length * Math.min(1, t / Math.max(1, speechType))));
        MutableComponent line = Component.empty();
        String nameKey = speechKind >= 1 && speechKind <= 5 ? "entity.hollowhours.ghost_" + speechKind : "hollowhours.speech.unknown";
        if (speechStyle != 3) line.append(Component.translatable(nameKey).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)).append(Component.literal(" : ").withStyle(ChatFormatting.DARK_GRAY));
        Style red = Style.EMPTY.withColor(speechStyle == 1 ? 0xC00000 : speechStyle == 2 ? 0x8899AA : 0x9B0000);
        StringBuilder done = new StringBuilder();
        for (int i = 0; i < shown; i++) done.appendCodePoint(cps[i]);
        String typed = done.toString();
        if (shown < cps.length) {
            line.append(Component.literal(typed).withStyle(red));
            StringBuilder nx = new StringBuilder();
            for (int i = shown; i < Math.min(cps.length, shown + 2); i++) nx.appendCodePoint(cps[i]);
            line.append(Component.literal(nx.toString()).withStyle(red.withObfuscated(true)));
        } else if (RNG.nextFloat() < 0.035f && cps.length > 3) {
            int i = RNG.nextInt(cps.length - 3);
            StringBuilder a = new StringBuilder(), b = new StringBuilder(), c = new StringBuilder();
            for (int k = 0; k < cps.length; k++) (k < i ? a : k < i + 3 ? b : c).appendCodePoint(cps[k]);
            line.append(Component.literal(a.toString()).withStyle(red)).append(Component.literal(b.toString()).withStyle(red.withObfuscated(true)))
                    .append(Component.literal(c.toString()).withStyle(red));
        } else line.append(Component.literal(typed).withStyle(red));
        float fadeOut = t > speechType + speechHold - 20 ? (speechType + speechHold - t) / 20f : 1f;
        int alpha = Mth.clamp((int) (fadeOut * 255), 8, 255);
        int y = h - 82;
        if (speechStyle == 1) {
            g.pose().pushPose();
            g.pose().translate((RNG.nextFloat() - 0.5f) * 2, (RNG.nextFloat() - 0.5f) * 2, 0);
        }
        List<net.minecraft.util.FormattedCharSequence> lines = mc.font.split(line, Math.min(w - 40, 340));
        int ly = y - (lines.size() - 1) * 10;
        for (var l : lines) {
            int lw = mc.font.width(l);
            g.fill(w / 2 - lw / 2 - 3, ly - 2, w / 2 + lw / 2 + 3, ly + 9, (Math.min(alpha, 150) << 24));
            g.drawString(mc.font, l, w / 2 - lw / 2, ly, 0xFFFFFF | (alpha << 24), false);
            ly += 10;
        }
        if (speechStyle == 1) g.pose().popPose();
    }

    static void reset() {
        shakeLeft = bloodLeft = staticLeft = darkLeft = glitchLeft = heartLeft = 0;
        FADES.clear();
        speechT0 = -1;
        hud = new S2CHud(0, 0, -1, 0, -1, false, false, 100);
    }
}
