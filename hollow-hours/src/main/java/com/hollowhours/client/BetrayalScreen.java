package com.hollowhours.client;

import com.hollowhours.net.C2SBetrayChoice;
import com.hollowhours.net.HHNet;
import com.hollowhours.net.S2CBetrayal;
import com.hollowhours.registry.HHSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.List;
import java.util.UUID;

/**
 * "Tell me where your friend is... and I might spare your life."
 * Pick a friend to sell (the ghost goes after them), or refuse. Ten seconds. Refusing or running out of time = death.
 */
public class BetrayalScreen extends Screen {
    final int kind, seconds;
    final List<UUID> ids;
    final List<String> names;
    final long openedAt;
    int page;
    boolean answered;
    int lastSecond = -1;

    public BetrayalScreen(S2CBetrayal m) {
        super(Component.translatable("hollowhours.betray.title"));
        this.kind = m.kind();
        this.seconds = Math.max(1, m.seconds());
        this.ids = m.ids();
        this.names = m.names();
        this.openedAt = ClientFx.now();
    }

    static final int PER_PAGE = 6;

    @Override
    protected void init() {
        clearWidgets();
        int cx = width / 2, y = height / 2 - 34;
        int from = page * PER_PAGE, to = Math.min(ids.size(), from + PER_PAGE);
        for (int i = from; i < to; i++) {
            UUID id = ids.get(i);
            String nm = names.get(i);
            int col = (i - from) % 2, row = (i - from) / 2;
            addRenderableWidget(Button.builder(Component.literal(nm).withStyle(ChatFormatting.RED), b -> choose(id))
                    .bounds(cx - 152 + col * 156, y + row * 24, 148, 20).build());
        }
        int rows = (to - from + 1) / 2;
        int by = y + rows * 24 + 8;
        if (ids.size() > PER_PAGE) {
            addRenderableWidget(Button.builder(Component.literal("<"), b -> {
                page = (page - 1 + pages()) % pages();
                init();
            }).bounds(cx - 152, by, 20, 20).build());
            addRenderableWidget(Button.builder(Component.literal(">"), b -> {
                page = (page + 1) % pages();
                init();
            }).bounds(cx + 132, by, 20, 20).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("hollowhours.betray.refuse").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD), b -> choose(null))
                .bounds(cx - 100, by, 200, 20).build());
    }

    int pages() {
        return Math.max(1, (ids.size() + PER_PAGE - 1) / PER_PAGE);
    }

    void choose(UUID friend) {
        if (answered) return;
        answered = true;
        HHNet.toServer(new C2SBetrayChoice(friend));
        onClose();
    }

    float left(float partial) {
        return seconds - (ClientFx.now() - openedAt + partial) / 20f;
    }

    @Override
    public void tick() {
        float l = left(0);
        int s = (int) Math.ceil(l);
        if (s != lastSecond && s >= 0) {
            lastSecond = s;
            if (HHSounds.has("ghost.betray.tick"))
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(HHSounds.get("ghost.betray.tick"), s <= 3 ? 1.3f : 1f, 0.9f));
            if (s <= 3) ClientFx.addShake(0.25f, 6);
        }
        // the server decides what happens at zero; the screen just closes
        if (l < -1.5f) onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        float l = Math.max(0, left(partial));
        // dark red veil, heavier as time runs out
        float k = 1 - l / seconds;
        g.fill(0, 0, width, height, ClientFx.argb(0.55f + 0.3f * k, 0x120000));
        if (l < 3 && ((int) (l * 4)) % 2 == 0) g.fill(0, 0, width, height, ClientFx.argb(0.18f, 0x5A0000));
        int cx = width / 2;
        g.pose().pushPose();
        float jitter = (float) (Math.random() - 0.5) * (1 + 3 * k);
        g.pose().translate(cx + jitter, height / 2f - 92, 0);
        g.pose().scale(1.6f, 1.6f, 1);
        Component t = Component.translatable("hollowhours.betray.offer." + Mth.clamp(kind, 1, 5));
        List<net.minecraft.util.FormattedCharSequence> lines = font.split(t, Math.max(120, (int) (width / 1.7f) - 30));
        int ly = 0;
        for (var line : lines) {
            g.drawString(font, line, -font.width(line) / 2, ly, 0xFFB00000, true);
            ly += 11;
        }
        g.pose().popPose();
        g.drawCenteredString(font, Component.translatable("hollowhours.betray.hint").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), cx, height / 2 - 52, 0xFFAAAAAA);
        // timer bar + number
        int bw = 220, bx = cx - bw / 2, by = height / 2 + 64 + Math.min(3, (ids.size() + 1) / 2) * 24 - 24;
        g.fill(bx - 1, by - 1, bx + bw + 1, by + 6, 0xFF000000);
        g.fill(bx, by, bx + Math.round(bw * (l / seconds)), by + 5, l < 3 ? 0xFFFF2020 : 0xFFB00000);
        g.pose().pushPose();
        g.pose().translate(cx, by + 12, 0);
        float sc = 2.2f + (l < 3 ? 0.6f * (float) Math.abs(Math.sin(l * Math.PI)) : 0);
        g.pose().scale(sc, sc, 1);
        String num = String.valueOf((int) Math.ceil(l));
        g.drawString(font, num, -font.width(num) / 2, 0, 0xFFFF3030, true);
        g.pose().popPose();
        super.render(g, mouseX, mouseY, partial);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    //#if MC>=12100
    //$$ public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {}
    //#else
    public void renderBackground(GuiGraphics g) {}
    //#endif
}
