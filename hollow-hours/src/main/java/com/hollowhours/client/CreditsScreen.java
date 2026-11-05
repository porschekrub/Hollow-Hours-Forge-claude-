package com.hollowhours.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The end credits: white fading to black, the story's epilogue, the five as they were, everyone who made it, and a
 * last line. Rolls by itself (hold space to speed up, Esc to skip); the world is quiet when it closes.
 */
public class CreditsScreen extends Screen {
    record Line(Component text, float scale, int color, int gapAfter) {}

    final List<String> names;
    final List<Line> lines = new ArrayList<>();
    float scroll, white = 1f;
    int ticks, total, endHold;  // (package access: the CI tour scrolls it)
    boolean fast;
    final long seed = new Random().nextLong();

    public CreditsScreen(List<String> names) {
        super(Component.translatable("hollowhours.credits.title"));
        this.names = names;
    }

    @Override
    protected void init() {
        lines.clear();
        add("hollowhours.credits.title", 3.2f, 0xFFF2E3B3, 6);
        add("hollowhours.credits.end", 1.6f, 0xFFD0C8B8, 46);
        for (int i = 1; i <= 5; i++) add("hollowhours.credits.epilogue." + i, 1.1f, 0xFFB8B4AC, i == 5 ? 48 : 10);
        add("hollowhours.credits.five", 1.4f, 0xFFE8C547, 18);
        for (int i = 1; i <= 5; i++) {
            add("hollowhours.credits.p" + i + ".name", 1.25f, 0xFFFFFFFF, 3);
            add("hollowhours.credits.p" + i + ".was", 0.95f, 0xFF9A968E, 16);
        }
        lines.add(new Line(Component.empty(), 1, 0, 30));
        add("hollowhours.credits.boss", 1.15f, 0xFFE8C547, 4);
        add("hollowhours.credits.boss.was", 0.95f, 0xFF9A968E, 48);
        add("hollowhours.credits.survivors", 1.4f, 0xFFE8C547, 14);
        for (String n : names) lines.add(new Line(Component.literal(n), 1.2f, 0xFFFFFFFF, 6));
        lines.add(new Line(Component.empty(), 1, 0, 42));
        add("hollowhours.credits.made", 1.1f, 0xFF9A968E, 6);
        lines.add(new Line(Component.literal("porschekrub"), 1.4f, 0xFFFFFFFF, 6));
        add("hollowhours.credits.team", 1.0f, 0xFFB8B4AC, 48);
        add("hollowhours.credits.thanks", 1.6f, 0xFFF2E3B3, 60);
        add("hollowhours.credits.peace", 1.1f, 0xFFB8D8B0, 0);
        total = 0;
        for (Line l : lines) total += lineHeight(l) + l.gapAfter;
        if (ticks == 0) HorrorMusic.set("credits");
    }

    void add(String key, float scale, int color, int gap) {
        lines.add(new Line(Component.translatable(key), scale, color, gap));
    }

    int lineHeight(Line l) {
        if (l.text.getString().isEmpty()) return 0;
        int wrapW = (int) ((width * 0.8f) / l.scale);
        int n = Math.max(1, font.split(l.text, wrapW).size());
        return Math.round(n * (font.lineHeight + 2) * l.scale);
    }

    @Override
    public void tick() {
        ticks++;
        white = Math.max(0, white - 0.025f);
        long win = GLFW.glfwGetWindowAttrib(Minecraft.getInstance().getWindow().getWindow(), GLFW.GLFW_FOCUSED);
        fast = win != 0 && GLFW.glfwGetKey(Minecraft.getInstance().getWindow().getWindow(), GLFW.GLFW_KEY_SPACE) == GLFW.GLFW_PRESS;
        if (ticks > 30) scroll += fast ? 6f : 0.75f;
        if (scroll > total + height * 0.5f) {
            if (++endHold > 80) onClose();
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        g.fill(0, 0, width, height, 0xFF000000);
        // drifting motes of gold dust
        Random r = new Random(seed);
        float t = ticks + partial;
        for (int i = 0; i < 70; i++) {
            float x = r.nextFloat() * width, sp = 0.15f + r.nextFloat() * 0.5f;
            float y = (r.nextFloat() * height - t * sp) % height;
            if (y < 0) y += height;
            float tw = 0.5f + 0.5f * Mth.sin(t * 0.07f + i);
            int a = (int) (40 + 90 * tw);
            g.fill((int) x, (int) y, (int) x + 1, (int) y + 1, (a << 24) | 0xE8C547);
        }
        Font f = font;
        float y = height - (scroll + (ticks > 30 ? (fast ? 6f : 0.75f) * partial : 0));
        for (Line l : lines) {
            int h = lineHeight(l);
            if (y + h > -20 && y < height + 20 && !l.text.getString().isEmpty()) {
                int wrapW = (int) ((width * 0.8f) / l.scale);
                List<FormattedCharSequence> parts = f.split(l.text, wrapW);
                float yy = y;
                for (FormattedCharSequence part : parts) {
                    g.pose().pushPose();
                    g.pose().translate(width / 2f, yy, 0);
                    g.pose().scale(l.scale, l.scale, 1);
                    int w = f.width(part);
                    // fade in at the bottom, out at the top
                    float edge = Math.min(1, Math.min((yy + 10) / 60f, (height - yy) / 60f));
                    int alpha = (int) (255 * Mth.clamp(edge, 0.02f, 1));
                    g.drawString(f, part, -w / 2, 0, (alpha << 24) | (l.color & 0xFFFFFF), false);
                    g.pose().popPose();
                    yy += (f.lineHeight + 2) * l.scale;
                }
            }
            y += h + l.gapAfter;
        }
        if (ticks > 60 && ticks < 260) {
            int a = (int) (120 * Math.min(1, (ticks - 60) / 40f) * Math.min(1, (260 - ticks) / 40f));
            Component hint = Component.translatable("hollowhours.credits.hint").withStyle(ChatFormatting.GRAY);
            g.drawString(f, hint, width - f.width(hint) - 6, height - 12, (Math.max(4, a) << 24) | 0xAAAAAA, false);
        }
        if (white > 0) g.fill(0, 0, width, height, ((int) (255 * white) << 24) | 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        HorrorMusic.set("");
        super.onClose();
    }
}
