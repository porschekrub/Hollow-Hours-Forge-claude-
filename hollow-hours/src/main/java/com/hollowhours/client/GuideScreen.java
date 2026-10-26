package com.hollowhours.client;

import com.hollowhours.HollowHours;
import com.hollowhours.net.C2SGuideRequest;
import com.hollowhours.net.C2STalk;
import com.hollowhours.net.HHNet;
import com.hollowhours.net.S2CGuide;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The Ghost Guide: who is awake, how to survive, the five ghosts' pages, and the two text boxes: talk to a ghost
 * (Thai or English, anything) and ask the nearest ghost for an item (it may give it... cursed).
 */
public class GuideScreen extends Screen {
    static final ResourceLocation BG = HollowHours.id("textures/gui/guide_bg.png");
    static final String[] PAGES = {"menu", "talk", "ask", "how", "house", "hide", "soul", "g1", "g2", "g3", "g4", "g5"};
    static S2CGuide last;
    static final List<Component> LOG = new ArrayList<>();

    String page;
    int talkTo;
    EditBox box;
    double scroll;
    int px, py, pw = 340, ph = 224;
    final int epigraph = (int) (Math.random() * 9);

    public GuideScreen(S2CGuide data) {
        super(Component.translatable("item.hollowhours.ghost_guide"));
        last = data;
        page = switch (data.page()) {
            case 1 -> "talk";
            case 2 -> "ask";
            default -> "menu";
        };
        talkTo = data.preferGhost() >= 1 ? data.preferGhost() : 1;
    }

    /** Called when fresh data arrives while the screen is open. */
    void refresh(S2CGuide data) {
        last = data;
    }

    static void logSpeech(Component c) {
        LOG.add(c);
        while (LOG.size() > 8) LOG.remove(0);
    }

    @Override
    protected void init() {
        clearWidgets();
        pw = Math.min(360, width - 20);
        ph = Math.min(230, height - 20);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        int bx = px + 8, by = py + 26, bw = 96;
        String[] menu = {"menu", "talk", "ask", "how", "house", "hide", "soul"};
        for (String m : menu) {
            addRenderableWidget(Button.builder(Component.translatable("hollowhours.guide.tab." + m), b -> go(m)).bounds(bx, by, bw, 16).build());
            by += 18;
        }
        by += 4;
        for (int n = 1; n <= 5; n++) {
            int nn = n;
            boolean awake = last != null && last.awake().length >= n && last.awake()[n - 1] != -1;
            MutableComponent lab = Component.translatable("entity.hollowhours.ghost_" + n).withStyle(awake ? ChatFormatting.RED : ChatFormatting.GRAY);
            addRenderableWidget(Button.builder(lab, b -> go("g" + nn)).bounds(bx, by, bw, 16).build());
            by += 18;
        }
        int cx = px + 112, cw = pw - 120;
        box = null;
        if (page.equals("talk") || page.equals("ask")) {
            box = new EditBox(font, cx, py + ph - 30, cw - 56, 18, Component.translatable("hollowhours.guide.type"));
            box.setMaxLength(160);
            box.setHint(Component.translatable(page.equals("talk") ? "hollowhours.guide.talk.hint" : "hollowhours.guide.ask.hint").withStyle(ChatFormatting.DARK_GRAY));
            addRenderableWidget(box);
            setInitialFocus(box);
            addRenderableWidget(Button.builder(Component.translatable("hollowhours.guide.send"), b -> send()).bounds(cx + cw - 52, py + ph - 30, 52, 18).build());
            if (page.equals("talk")) {
                for (int n = 1; n <= 5; n++) {
                    int nn = n;
                    Component nm = Component.translatable("hollowhours.ghost.short." + n).withStyle(talkTo == n ? ChatFormatting.DARK_RED : ChatFormatting.GRAY);
                    addRenderableWidget(Button.builder(nm, b -> {
                        talkTo = nn;
                        init();
                    }).bounds(cx + (n - 1) * (cw / 5), py + 40, cw / 5 - 2, 16).build());
                }
            }
        }
        if (page.startsWith("g")) {
            int n = page.charAt(1) - '0';
            addRenderableWidget(Button.builder(Component.translatable("hollowhours.guide.talkto", Component.translatable("entity.hollowhours.ghost_" + n)), b -> {
                talkTo = n;
                go("talk");
            }).bounds(cx, py + ph - 26, cw, 18).build());
        }
    }

    void go(String p) {
        page = p;
        scroll = 0;
        init();
        if (p.equals("menu") || p.equals("ask")) HHNet.toServer(new C2SGuideRequest(p.equals("ask") ? 2 : 0));
    }

    void send() {
        if (box == null) return;
        String t = box.getValue().trim();
        if (t.isEmpty()) return;
        String lang = Minecraft.getInstance().getLanguageManager().getSelected();
        HHNet.toServer(new C2STalk(page.equals("ask") ? 1 : 0, page.equals("ask") ? 0 : talkTo, t, lang));
        logSpeech(Component.literal("> " + t).withStyle(ChatFormatting.GRAY));
        box.setValue("");
        if (page.equals("ask")) HHNet.toServer(new C2SGuideRequest(2));
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && box != null && box.isFocused()) {
            send();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll = Math.max(0, scroll - delta * 12);
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        RenderSystem.enableBlend();
        g.blit(BG, px, py, 0, 0, pw, ph, pw, ph);
        // a title that won't stay still
        String title = Component.translatable("hollowhours.guide.title").getString();
        int tx = px + pw / 2 - font.width(title) / 2;
        g.drawString(font, title, tx + (Math.random() < 0.05 ? 1 : 0), py + 8, 0xFF7A0000, false);
        int cx = px + 112, cw = pw - 120, cy = py + 26, chH = ph - 34;
        List<FormattedCharSequence> lines = new ArrayList<>();
        switch (page) {
            case "menu" -> menuText(lines, cw);
            case "talk" -> {
                cy = py + 60;
                chH = ph - 96;
                wrap(lines, Component.translatable("hollowhours.guide.talk.info", Component.translatable("entity.hollowhours.ghost_" + talkTo)), cw);
                lines.add(FormattedCharSequence.EMPTY);
                for (Component c : LOG) wrap(lines, c, cw);
            }
            case "ask" -> {
                chH = ph - 64;
                int left = last == null ? 3 : last.asksLeft(), lim = last == null ? 3 : last.askLimit();
                wrap(lines, Component.translatable("hollowhours.guide.ask.info", left, lim), cw);
                lines.add(FormattedCharSequence.EMPTY);
                for (Component c : LOG) wrap(lines, c, cw);
            }
            default -> {
                if (page.startsWith("g")) {
                    int n = page.charAt(1) - '0';
                    ghostText(lines, n, cw);
                    chH = ph - 64;
                } else wrap(lines, Component.translatable("hollowhours.guide.page." + page), cw);
            }
        }
        int maxScroll = Math.max(0, lines.size() * 10 - chH);
        scroll = Mth.clamp(scroll, 0, maxScroll);
        g.enableScissor(cx, cy, cx + cw, cy + chH);
        int y = cy - (int) scroll;
        for (FormattedCharSequence l : lines) {
            if (y > cy - 10 && y < cy + chH) g.drawString(font, l, cx, y, 0xFF2A1A12, false);
            y += 10;
        }
        g.disableScissor();
        if (maxScroll > 0) {
            int sh = Math.max(12, chH * chH / (lines.size() * 10));
            int sy = cy + (int) ((chH - sh) * (scroll / maxScroll));
            g.fill(cx + cw + 2, sy, cx + cw + 4, sy + sh, 0x885A0000);
        }
        super.render(g, mouseX, mouseY, partial);
    }

    void wrap(List<FormattedCharSequence> out, Component c, int w) {
        String s = c.getString();
        if (s.indexOf('\n') < 0) {
            out.addAll(font.split(c, w));
            return;
        }
        for (String para : s.split("\n")) {
            if (para.isEmpty()) {
                out.add(FormattedCharSequence.EMPTY);
                continue;
            }
            out.addAll(font.split(Component.literal(para).withStyle(c.getStyle()), w));
        }
    }

    void menuText(List<FormattedCharSequence> lines, int w) {
        wrap(lines, Component.translatable("hollowhours.guide.epigraph." + epigraph).withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC), w);
        lines.add(FormattedCharSequence.EMPTY);
        if (last != null) {
            float soul = last.soul();
            int tier = soul < 15 ? 3 : soul < 40 ? 2 : soul < 70 ? 1 : 0;
            int nbar = Math.round(soul / 10);
            String bar = "§4" + "▮".repeat(nbar) + "§8" + "▮".repeat(10 - nbar);
            wrap(lines, Component.translatable("hollowhours.guide.soul", bar, Math.round(soul), Component.translatable("hollowhours.soul.tiername." + tier)), w);
            boolean any = false;
            for (int i = 0; i < last.awake().length; i++) {
                float d = last.awake()[i];
                if (d == -1) continue;
                any = true;
                Component where = d == -2 ? Component.translatable("hollowhours.guide.otherdim") : Component.translatable("hollowhours.guide.blocks", Math.round(d));
                wrap(lines, Component.translatable("hollowhours.guide.awake", Component.translatable("entity.hollowhours.ghost_" + (i + 1)), where), w);
            }
            if (!any) wrap(lines, Component.translatable("hollowhours.guide.noneawake"), w);
            if (last.hasFlash()) {
                int sec = last.flashTicks() / 20;
                String ch = last.flashTicks() > 0 ? String.format("%d:%02d", sec / 60, sec % 60) : "-";
                wrap(lines, Component.translatable(last.flashOn() ? "hollowhours.guide.flash.on" : "hollowhours.guide.flash", ch, last.batteries()), w);
            } else wrap(lines, Component.translatable("hollowhours.guide.flash.none", last.batteries()), w);
        }
        lines.add(FormattedCharSequence.EMPTY);
        wrap(lines, Component.translatable("hollowhours.guide.menu.footer"), w);
    }

    void ghostText(List<FormattedCharSequence> lines, int n, int w) {
        wrap(lines, Component.translatable("entity.hollowhours.ghost_" + n).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), w);
        lines.add(FormattedCharSequence.EMPTY);
        wrap(lines, Component.translatable("hollowhours.guide.ghost." + n), w);
        lines.add(FormattedCharSequence.EMPTY);
        if (last != null && last.awake().length >= n && last.awake()[n - 1] != -1) {
            float d = last.awake()[n - 1];
            Component where = d == -2 ? Component.translatable("hollowhours.guide.otherdim") : Component.translatable("hollowhours.guide.blocks", Math.round(d));
            wrap(lines, Component.translatable("hollowhours.guide.isawake", where), w);
        } else wrap(lines, Component.translatable("hollowhours.guide.asleep"), w);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
