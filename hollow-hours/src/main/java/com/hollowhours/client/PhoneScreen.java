package com.hollowhours.client;

import com.hollowhours.ghost.PhoneUse;
import com.hollowhours.item.PhoneItem;
import com.hollowhours.net.C2SPhone;
import com.hollowhours.net.HHNet;
import com.hollowhours.registry.HHItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * The phone (right-click it). The world keeps going while you look at it. Apps: messages, Snake, Ghost Tap (games
 * calm you down but eat the battery), and health (sanity, thirst, battery health, the day).
 * The arcade machine opens the same games on its own screen: no battery, and a bit more fun.
 */
public class PhoneScreen extends Screen {
    enum App { HOME, MESSAGES, CHAT, MAP, RESCUE, SNAKE, TAP, HEALTH }

    final InteractionHand hand;
    /** the arcade machine being played, or null for the phone */
    @Nullable
    final BlockPos arcade;
    App app = App.HOME;
    int ticks;
    final RandomSource rng = RandomSource.create();

    // screen area (set every frame)
    int sx, sy, sw, sh;

    public static void open(InteractionHand hand) {
        Minecraft.getInstance().setScreen(new PhoneScreen(hand));
    }

    public static void openArcade(BlockPos pos) {
        Minecraft.getInstance().setScreen(new PhoneScreen(InteractionHand.MAIN_HAND, pos));
    }

    PhoneScreen(InteractionHand hand) {
        this(hand, null);
    }

    PhoneScreen(InteractionHand hand, @Nullable BlockPos arcade) {
        super(Component.translatable(arcade == null ? "item.hollowhours.phone" : "block.hollowhours.arcade"));
        this.hand = hand;
        this.arcade = arcade;
    }

    ItemStack phone() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return ItemStack.EMPTY;
        ItemStack s = mc.player.getItemInHand(hand);
        return s.is(HHItems.PHONE.get()) ? s : ItemStack.EMPTY;
    }

    boolean dead() {
        return arcade == null && PhoneItem.battery(phone()) <= 0;
    }

    @Override
    protected void init() {
        HHNet.toServer(new C2SPhone(arcade == null ? PhoneUse.OPEN : PhoneUse.ARCADE));
        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                com.hollowhours.registry.HHSounds.get("phone.click"), 1.1f, 0.6f));
    }

    @Override
    public void removed() {
        HHNet.toServer(new C2SPhone(PhoneUse.CLOSE));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        ticks++;
        Minecraft mc = Minecraft.getInstance();
        if (arcade == null ? phone().isEmpty() : mc.player == null || mc.player.distanceToSqr(Vec3.atCenterOf(arcade)) > 36) {
            onClose();
            return;
        }
        boolean gaming = (app == App.SNAKE && snakeAlive) || (app == App.TAP && tapLeft > 0);
        if (dead()) {
            if (app != App.HOME) app = App.HOME;
            gaming = false;
        }
        if (ticks % 20 == 0) HHNet.toServer(new C2SPhone(arcade != null ? (gaming ? PhoneUse.ARCADE_GAME : PhoneUse.ARCADE) : gaming ? PhoneUse.GAME : PhoneUse.BEAT));
        if (app == App.SNAKE) snakeTick();
        if (app == App.TAP) tapTick();
    }

    // ------------------------------------------------------------------ drawing
    /** The phone's body (also used by the alert in the corner). */
    static void drawBody(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF3A3A44);
        g.fill(x, y, x + w, y + h, 0xFF0D0D10);
        g.fill(x + w / 2 - 10, y + 4, x + w / 2 + 10, y + 6, 0xFF2A2A30);
        g.fill(x + w / 2 - 14, y + h - 7, x + w / 2 + 14, y + h - 5, 0xFF505058);
    }

    /** The arcade cabinet around the screen: purple side art, marquee on top, buttons below. */
    void drawCabinet(GuiGraphics g, Font f, int x, int y, int w, int h) {
        g.fill(x - 8, y - 22, x + w + 8, y + h + 30, 0xFF2A1240);
        g.fill(x - 6, y - 20, x + w + 6, y - 4, 0xFF120818);
        int glow = (ticks / 10) % 2 == 0 ? 0xFFFFD040 : 0xFFFF7AD8;
        g.drawCenteredString(f, Component.translatable("block.hollowhours.arcade"), x + w / 2, y - 16, glow);
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF6A3A9A);
        g.fill(x, y, x + w, y + h, 0xFF000000);
        int by = y + h + 10;
        g.fill(x + 10, by + 4, x + 14, by + 16, 0xFF202020);
        g.fill(x + 7, by, x + 17, by + 6, 0xFFD02020);
        for (int i = 0; i < 3; i++) g.fill(x + w - 40 + i * 12, by + 4, x + w - 32 + i * 12, by + 12, new int[] {0xFF30C0FF, 0xFFFFD040, 0xFF40E060}[i]);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        renderBackground(g);
        Minecraft mc = Minecraft.getInstance();
        Font f = mc.font;
        if (arcade != null) {
            int ah = Math.min(220, height - 70), aw = Math.round(ah * 0.8f);
            sx = width / 2 - aw / 2;
            sy = height / 2 - ah / 2 + 4;
            sw = aw;
            sh = ah;
            drawCabinet(g, f, sx, sy, sw, sh);
            g.fillGradient(sx, sy, sx + sw, sy + sh, 0xFF14082A, 0xFF050210);
            int top = sy + 2;
            switch (app) {
                case SNAKE -> renderSnake(g, f, top);
                case TAP -> renderTap(g, f, top, partial);
                default -> renderHome(g, f, mx, my, top);
            }
            return;
        }
        int ph = Math.min(300, height - 16), pw = Math.round(ph * 0.56f);
        int px = width / 2 - pw / 2, py = height / 2 - ph / 2;
        drawBody(g, px, py, pw, ph);
        sx = px + 6;
        sy = py + 12;
        sw = pw - 12;
        sh = ph - 26;
        if (dead()) {
            g.fill(sx, sy, sx + sw, sy + sh, 0xFF000000);
            int bx = sx + sw / 2 - 14, by = sy + sh / 2 - 8;
            g.fill(bx, by, bx + 28, by + 14, 0xFF606060);
            g.fill(bx + 1, by + 1, bx + 27, by + 13, 0xFF000000);
            g.fill(bx + 28, by + 4, bx + 30, by + 10, 0xFF606060);
            g.fill(bx + 2, by + 2, bx + 4, by + 12, 0xFFC02020);
            drawWrapped(g, f, Component.translatable("hollowhours.phone.dead"), sx + 6, by + 22, sw - 12, 0xFF909090, true);
            return;
        }
        // wallpaper + status bar
        g.fillGradient(sx, sy, sx + sw, sy + sh, 0xFF1B2A44, 0xFF0B0F1A);
        g.fill(sx, sy, sx + sw, sy + 11, 0x60000000);
        g.drawString(f, clock(), sx + 3, sy + 2, 0xFFFFFFFF, false);
        float bat = PhoneItem.battery(phone());
        String bs = Math.round(bat) + "%";
        int bw = f.width(bs);
        g.drawString(f, bs, sx + sw - bw - 18, sy + 2, bat < 20 ? 0xFFFF6060 : 0xFFFFFFFF, false);
        int bx = sx + sw - 15, by = sy + 3;
        g.fill(bx, by, bx + 12, by + 6, 0xFFFFFFFF);
        g.fill(bx + 1, by + 1, bx + 11, by + 5, 0xFF000000);
        g.fill(bx + 12, by + 2, bx + 13, by + 4, 0xFFFFFFFF);
        g.fill(bx + 1, by + 1, bx + 1 + Math.round(10 * bat / 100f), by + 5, bat < 20 ? 0xFFFF4040 : 0xFF60E060);
        int top = sy + 13;
        PhoneApps.HITS.clear();
        if (app != App.CHAT) ClientPhone.open = null;
        switch (app) {
            case HOME -> renderHome(g, f, mx, my, top);
            case MESSAGES -> renderMessages(g, f, top);
            case CHAT -> PhoneApps.renderChat(this, g, f, mx, my, top);
            case MAP -> PhoneApps.renderMap(this, g, f, mx, my, top);
            case RESCUE -> PhoneApps.renderRescue(this, g, f, top);
            case SNAKE -> renderSnake(g, f, top);
            case TAP -> renderTap(g, f, top, partial);
            case HEALTH -> renderHealth(g, f, top);
        }
    }

    String clock() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return "";
        long t = (mc.level.getDayTime() + 6000) % 24000;
        int hh = (int) (t / 1000), mm = (int) (t % 1000 * 60 / 1000);
        return String.format("%02d:%02d", hh, mm);
    }

    long day() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 1 : mc.level.getDayTime() / 24000 + 1;
    }

    static final App[] ICONS = {App.MESSAGES, App.CHAT, App.MAP, App.RESCUE, App.SNAKE, App.TAP, App.HEALTH}, ARCADE_ICONS = {App.SNAKE, App.TAP};

    App[] icons() {
        return arcade == null ? ICONS : ARCADE_ICONS;
    }

    static int iconColor(App a) {
        return switch (a) {
            case MESSAGES -> 0xFF35C759;
            case CHAT -> 0xFF2E9BF0;
            case MAP -> 0xFF3AAE6E;
            case RESCUE -> 0xFFE07A20;
            case SNAKE -> 0xFF2FA84F;
            case TAP -> 0xFF7A3FD1;
            default -> 0xFFE0425A;
        };
    }

    static String iconGlyph(App a) {
        return switch (a) {
            case MESSAGES -> "✉";
            case CHAT -> "☺";
            case MAP -> "⌖";
            case RESCUE -> "✈";
            case SNAKE -> "§";
            case TAP -> "☠";
            default -> "♥";
        };
    }

    int[] iconRect(int i, int top) {
        // three to a row on the phone, two on the arcade
        int cols = arcade == null ? 3 : 2;
        int size = Math.min(arcade == null ? 30 : 34, (sw - 12 * (cols + 1)) / cols), gap = (sw - size * cols) / (cols + 1);
        int col = i % cols, row = i / cols;
        int x = sx + gap + col * (size + gap), y = top + 40 + row * (size + 20);
        return new int[] {x, y, size};
    }

    void renderHome(GuiGraphics g, Font f, int mx, int my, int top) {
        String big = arcade != null ? Component.translatable("hollowhours.arcade.play").getString() : clock();
        g.pose().pushPose();
        g.pose().translate(sx + sw / 2f, top + 6, 0);
        g.pose().scale(2, 2, 1);
        g.drawString(f, big, -f.width(big) / 2, 0, 0xFFFFFFFF, false);
        g.pose().popPose();
        Component sub = arcade != null ? Component.translatable("hollowhours.arcade.free") : Component.translatable("hollowhours.phone.day", day());
        g.drawCenteredString(f, sub, sx + sw / 2, top + 26, 0xFFB0C0E0);
        App[] icons = icons();
        for (int i = 0; i < icons.length; i++) {
            int[] r = iconRect(i, top);
            boolean hover = mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[2];
            int col = iconColor(icons[i]);
            g.fill(r[0], r[1], r[0] + r[2], r[1] + r[2], hover ? brighten(col) : col);
            String glyph = iconGlyph(icons[i]);
            g.pose().pushPose();
            g.pose().translate(r[0] + r[2] / 2f, r[1] + r[2] / 2f - 6, 0);
            g.pose().scale(1.6f, 1.6f, 1);
            g.drawString(f, glyph, -f.width(glyph) / 2, 0, 0xFFFFFFFF, false);
            g.pose().popPose();
            Component name = Component.translatable("hollowhours.phone.app." + icons[i].name().toLowerCase(java.util.Locale.ROOT));
            // the name shrinks to fit under its icon
            float k = Math.min(1f, (r[2] + 14f) / Math.max(1, f.width(name)));
            g.pose().pushPose();
            g.pose().translate(r[0] + r[2] / 2f, r[1] + r[2] + 3, 0);
            g.pose().scale(k, k, 1);
            g.drawCenteredString(f, name, 0, 0, 0xFFFFFFFF);
            g.pose().popPose();
            if (icons[i] == App.MESSAGES && unread() || icons[i] == App.CHAT && ClientPhone.unreadAll() > 0)
                g.fill(r[0] + r[2] - 6, r[1] - 2, r[0] + r[2] + 2, r[1] + 6, 0xFFFF3030);
        }
    }

    boolean unread() {
        return PhoneItem.messages(phone()).contains("hollowhours.phone.msg.alert") && !readAlert;
    }

    static boolean readAlert;

    static int brighten(int c) {
        int r = Math.min(255, ((c >> 16) & 255) + 40), gg = Math.min(255, ((c >> 8) & 255) + 40), b = Math.min(255, (c & 255) + 40);
        return 0xFF000000 | r << 16 | gg << 8 | b;
    }

    void header(GuiGraphics g, Font f, int top, Component title) {
        g.fill(sx, top, sx + sw, top + 14, 0x70000000);
        g.drawString(f, "‹", sx + 4, top + 3, 0xFFFFFFFF, false);
        g.drawCenteredString(f, title, sx + sw / 2, top + 3, 0xFFFFFFFF);
    }

    int drawWrapped(GuiGraphics g, Font f, Component text, int x, int y, int w, int col, boolean center) {
        for (FormattedCharSequence l : f.split(text, w)) {
            if (center) g.drawString(f, l, x + w / 2 - f.width(l) / 2, y, col, false);
            else g.drawString(f, l, x, y, col, false);
            y += 10;
        }
        return y;
    }

    void renderMessages(GuiGraphics g, Font f, int top) {
        readAlert = true;
        header(g, f, top, Component.translatable("hollowhours.phone.app.messages"));
        List<String> msgs = PhoneItem.messages(phone());
        int y = top + 18;
        for (int i = msgs.size() - 1; i >= 0 && y < sy + sh - 20; i--) {
            String[] parts = msgs.get(i).split("\\|");
            String key = parts[0];
            boolean alert = key.endsWith(".alert") || key.endsWith(".missed") || key.endsWith(".invasion") || key.endsWith(".home");
            Component from = Component.translatable(key + ".from");
            g.drawString(f, from, sx + 4, y, alert ? 0xFFFF5050 : 0xFF8FD8EA, false);
            y += 10;
            int y0 = y;
            Object[] args = java.util.Arrays.copyOfRange(parts, 1, parts.length);
            y = drawWrapped(g, f, Component.translatable(key, args), sx + 6, y + 2, sw - 12, 0xFFE8E8E8, false);
            g.fill(sx + 3, y0, sx + 4, y, alert ? 0xFFFF5050 : 0xFF406080);
            y += 6;
        }
    }

    void renderHealth(GuiGraphics g, Font f, int top) {
        header(g, f, top, Component.translatable("hollowhours.phone.app.health"));
        int y = top + 22;
        float san = SurvivalHud.sanity, th = SurvivalHud.thirst;
        if (san >= 0) {
            g.drawString(f, Component.translatable("hollowhours.phone.sanity", Math.round(san)), sx + 6, y, 0xFFFFFFFF, false);
            bar(g, sx + 6, y + 11, sw - 12, san / 100f, san > 60 ? 0xFF8FD8EA : san > 35 ? 0xFFB592E6 : 0xFFD0507A);
            y += 26;
        }
        if (th >= 0) {
            g.drawString(f, Component.translatable("hollowhours.phone.thirst", Math.round(th)), sx + 6, y, 0xFFFFFFFF, false);
            bar(g, sx + 6, y + 11, sw - 12, th / 20f, 0xFF3A8FE0);
            y += 26;
        }
        g.drawString(f, Component.translatable("hollowhours.phone.strength", Math.round(CombatClient.strength)), sx + 6, y, 0xFFFFFFFF, false);
        bar(g, sx + 6, y + 11, sw - 12, CombatClient.strength / 100f, 0xFFE8A040);
        y += 24;
        g.drawString(f, Component.translatable("hollowhours.phone.skills", CombatClient.meleeLvl, CombatClient.gunLvl), sx + 6, y, 0xFFC0C0C0, false);
        y += 14;
        g.drawString(f, Component.translatable("hollowhours.phone.batteryhealth", PhoneItem.health(phone())), sx + 6, y, 0xFFC0C0C0, false);
        y += 14;
        g.drawString(f, Component.translatable("hollowhours.phone.day", day()), sx + 6, y, 0xFFC0C0C0, false);
        y += 16;
        drawWrapped(g, f, Component.translatable("hollowhours.phone.health.tip"), sx + 6, y, sw - 12, 0xFF8090A0, false);
    }

    static void bar(GuiGraphics g, int x, int y, int w, float k, int col) {
        g.fill(x, y, x + w, y + 5, 0xFF202028);
        g.fill(x, y, x + Math.round(w * Mth.clamp(k, 0, 1)), y + 5, col);
    }

    // ------------------------------------------------------------------ Snake
    static final int GRID = 14;
    final Deque<int[]> snake = new ArrayDeque<>();
    int dirX = 1, dirY = 0, nextDX = 1, nextDY = 0, foodX, foodY, score, best;
    boolean snakeAlive;

    void snakeStart() {
        snake.clear();
        snake.add(new int[] {5, 7});
        snake.add(new int[] {4, 7});
        snake.add(new int[] {3, 7});
        dirX = nextDX = 1;
        dirY = nextDY = 0;
        score = 0;
        snakeAlive = true;
        placeFood();
    }

    void placeFood() {
        for (int i = 0; i < 100; i++) {
            foodX = rng.nextInt(GRID);
            foodY = rng.nextInt(GRID);
            boolean on = false;
            for (int[] c : snake) if (c[0] == foodX && c[1] == foodY) on = true;
            if (!on) return;
        }
    }

    void snakeTick() {
        if (!snakeAlive || ticks % 4 != 0) return;
        if (nextDX != -dirX || nextDY != -dirY) {
            dirX = nextDX;
            dirY = nextDY;
        }
        int[] head = snake.peekFirst();
        int nx = head[0] + dirX, ny = head[1] + dirY;
        boolean hit = nx < 0 || ny < 0 || nx >= GRID || ny >= GRID;
        for (int[] c : snake) if (c[0] == nx && c[1] == ny) hit = true;
        if (hit) {
            snakeAlive = false;
            best = Math.max(best, score);
            sound("phone.game", 0.6f);
            return;
        }
        snake.addFirst(new int[] {nx, ny});
        if (nx == foodX && ny == foodY) {
            score++;
            sound("phone.click", 1.6f);
            placeFood();
        } else snake.removeLast();
    }

    void renderSnake(GuiGraphics g, Font f, int top) {
        header(g, f, top, Component.translatable("hollowhours.phone.app.snake"));
        int cell = Math.max(3, (sw - 8) / GRID), gw = cell * GRID, gx = sx + sw / 2 - gw / 2, gy = top + 30;
        g.drawString(f, Component.translatable("hollowhours.phone.score", score, best), sx + 5, top + 18, 0xFFFFFFFF, false);
        g.fill(gx - 1, gy - 1, gx + gw + 1, gy + gw + 1, 0xFF3A5A3A);
        g.fill(gx, gy, gx + gw, gy + gw, 0xFF0E1A0E);
        g.fill(gx + foodX * cell + 1, gy + foodY * cell + 1, gx + foodX * cell + cell - 1, gy + foodY * cell + cell - 1, 0xFFE04040);
        boolean first = true;
        for (int[] c : snake) {
            g.fill(gx + c[0] * cell, gy + c[1] * cell, gx + c[0] * cell + cell - 1, gy + c[1] * cell + cell - 1, first ? 0xFFB0FF70 : 0xFF60C040);
            first = false;
        }
        if (!snakeAlive) drawWrapped(g, f, Component.translatable("hollowhours.phone.snake.start"), sx + 4, gy + gw + 6, sw - 8, 0xFFFFFFA0, true);
    }

    // ------------------------------------------------------------------ Ghost Tap (whack-a-ghost)
    final int[] holeUntil = new int[9];
    int tapLeft, tapScore, tapBest;

    void tapStart() {
        tapLeft = 30 * 20;
        tapScore = 0;
        java.util.Arrays.fill(holeUntil, 0);
    }

    void tapTick() {
        if (tapLeft <= 0) return;
        tapLeft--;
        if (tapLeft == 0) {
            tapBest = Math.max(tapBest, tapScore);
            sound("phone.game", 0.6f);
        }
        if (rng.nextFloat() < 0.09f) {
            int i = rng.nextInt(9);
            if (holeUntil[i] < ticks) holeUntil[i] = ticks + 14 + rng.nextInt(12);
        }
    }

    int[] holeRect(int i, int top) {
        int size = (sw - 16) / 3, x = sx + 4 + (i % 3) * (size + 4), y = top + 34 + (i / 3) * (size + 4);
        return new int[] {x, y, size};
    }

    void renderTap(GuiGraphics g, Font f, int top, float partial) {
        header(g, f, top, Component.translatable("hollowhours.phone.app.tap"));
        g.drawString(f, Component.translatable("hollowhours.phone.score", tapScore, tapBest), sx + 5, top + 18, 0xFFFFFFFF, false);
        if (tapLeft > 0) {
            String s = (tapLeft / 20 + 1) + "s";
            g.drawString(f, s, sx + sw - f.width(s) - 5, top + 18, 0xFFFFD060, false);
        }
        for (int i = 0; i < 9; i++) {
            int[] r = holeRect(i, top);
            g.fill(r[0], r[1], r[0] + r[2], r[1] + r[2], 0xFF241A30);
            g.fill(r[0] + 3, r[1] + r[2] - 8, r[0] + r[2] - 3, r[1] + r[2] - 3, 0xFF0A0610);
            if (tapLeft > 0 && holeUntil[i] >= ticks) {
                int c = r[0] + r[2] / 2, gy = r[1] + 5;
                g.fill(c - 7, gy, c + 7, gy + r[2] - 12, 0xFFE8E8F0);
                g.fill(c - 4, gy + 4, c - 1, gy + 8, 0xFF000000);
                g.fill(c + 1, gy + 4, c + 4, gy + 8, 0xFF000000);
                g.fill(c - 2, gy + 11, c + 2, gy + 14, 0xFF300000);
            }
        }
        if (tapLeft <= 0) drawWrapped(g, f, Component.translatable("hollowhours.phone.tap.start"), sx + 4, holeRect(8, top)[1] + holeRect(8, top)[2] + 8, sw - 8, 0xFFFFFFA0, true);
    }

    // ------------------------------------------------------------------ input
    void sound(String s, float pitch) {
        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                com.hollowhours.registry.HHSounds.get(s), pitch, 0.5f));
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (app == App.CHAT && PhoneApps.chatChar(c)) return true;
        return super.charTyped(c, mods);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (app == App.CHAT) PhoneApps.chatScroll(delta);
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (dead()) return super.mouseClicked(mx, my, button);
        int top = arcade != null ? sy + 2 : sy + 13;
        if ((app == App.CHAT || app == App.MAP || app == App.RESCUE) && PhoneApps.click(mx, my)) return true;
        if (app != App.HOME && my >= top && my < top + 14 && mx < sx + 30) {
            app = App.HOME;
            sound("phone.click", 1.0f);
            return true;
        }
        if (app == App.HOME) {
            App[] icons = icons();
            for (int i = 0; i < icons.length; i++) {
                int[] r = iconRect(i, top);
                if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[2]) {
                    app = icons[i];
                    sound("phone.click", 1.2f);
                    if (app == App.SNAKE) snakeAlive = false;
                    if (app == App.TAP) tapLeft = 0;
                    return true;
                }
            }
        } else if (app == App.TAP) {
            if (tapLeft <= 0) {
                tapStart();
                return true;
            }
            for (int i = 0; i < 9; i++) {
                int[] r = holeRect(i, top);
                if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[2]) {
                    if (holeUntil[i] >= ticks) {
                        holeUntil[i] = 0;
                        tapScore++;
                        sound("phone.click", 1.5f);
                    } else sound("phone.click", 0.6f);
                    return true;
                }
            }
        } else if (app == App.SNAKE && !snakeAlive) {
            snakeStart();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (app == App.CHAT && key == GLFW.GLFW_KEY_ESCAPE && PhoneApps.chatWith != null) {
            PhoneApps.chatWith = null;
            return true;
        }
        if (app == App.CHAT && PhoneApps.chatKey(this, key)) return true;
        if (key == GLFW.GLFW_KEY_ESCAPE && app != App.HOME) {
            app = App.HOME;
            return true;
        }
        if (app == App.SNAKE) {
            switch (key) {
                case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_W -> {
                    nextDX = 0;
                    nextDY = -1;
                    return true;
                }
                case GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_S -> {
                    nextDX = 0;
                    nextDY = 1;
                    return true;
                }
                case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_A -> {
                    nextDX = -1;
                    nextDY = 0;
                    return true;
                }
                case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_D -> {
                    nextDX = 1;
                    nextDY = 0;
                    return true;
                }
                case GLFW.GLFW_KEY_SPACE, GLFW.GLFW_KEY_ENTER -> {
                    if (!snakeAlive && !dead()) snakeStart();
                    return true;
                }
                default -> {}
            }
        }
        return super.keyPressed(key, scan, mods);
    }
}
