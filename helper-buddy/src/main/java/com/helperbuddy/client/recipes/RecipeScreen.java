package com.helperbuddy.client.recipes;

import com.helperbuddy.client.recipes.RecipeIndex.Shown;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The recipe book (like JEI): every item of the game on the right (search it, "@mod" for one mod's items), and on
 * the left how the chosen item is made or what it is used for. Left click an item = its recipes, right click (or U)
 * = what it is used in; click any item inside a recipe to follow it, Backspace / the arrow goes back.
 */
public class RecipeScreen extends Screen {
    /** Set by the helper screens: the player talks Thai to their helper. */
    @Nullable
    public static Boolean thaiHint;
    static String query = "";
    static int savedListPage;

    @Nullable
    final Screen parent;
    EditBox search;
    final List<ItemStack> filtered = new ArrayList<>();
    int listPage, cols, rows, listW, lx, ly;
    ItemStack focus = ItemStack.EMPTY;
    boolean uses;
    List<Shown> shown = List.of();
    int recPage, pages = 1;
    int[][] place = new int[0][];
    final Deque<Object[]> history = new ArrayDeque<>();
    int rx0, ry0, rx1, ry1;
    long frozen;

    record Hit(int x, int y, ItemStack stack) {}

    final List<Hit> hits = new ArrayList<>();

    public RecipeScreen(@Nullable Screen parent, ItemStack focus, boolean uses) {
        super(Component.literal("Recipes"));
        this.parent = parent;
        RecipeIndex.ensure();
        listPage = savedListPage;
        if (!focus.isEmpty()) show(focus, uses, false);
    }

    static boolean th() {
        if (thaiHint != null) return thaiHint;
        String l = Minecraft.getInstance().getLanguageManager().getSelected();
        return l != null && l.toLowerCase(Locale.ROOT).startsWith("th");
    }

    static String t(String thai, String en) {
        return th() ? thai : en;
    }

    // ------------------------------------------------------------------ state
    void show(ItemStack s, boolean useMode, boolean remember) {
        if (remember && !focus.isEmpty()) history.push(new Object[]{focus, uses, recPage});
        focus = s.copyWithCount(1);
        uses = useMode;
        shown = RecipeIndex.forItem(focus, uses);
        recPage = 0;
        if (minecraft != null) rebuildWidgets();
    }

    void back() {
        if (history.isEmpty()) return;
        Object[] o = history.pop();
        focus = (ItemStack) o[0];
        uses = (Boolean) o[1];
        shown = RecipeIndex.forItem(focus, uses);
        recPage = (Integer) o[2];
        rebuildWidgets();
    }

    void refilter() {
        filtered.clear();
        String q = query.trim().toLowerCase(Locale.ROOT);
        boolean mod = q.startsWith("@");
        if (mod) q = q.substring(1).trim();
        for (ItemStack s : RecipeIndex.items()) {
            if (q.isEmpty()) {
                filtered.add(s);
                continue;
            }
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(s.getItem());
            if (mod) {
                if (id.getNamespace().contains(q) || modName(id.getNamespace()).toLowerCase(Locale.ROOT).contains(q)) filtered.add(s);
            } else if (s.getHoverName().getString().toLowerCase(Locale.ROOT).contains(q) || id.getPath().contains(q.replace(' ', '_'))) {
                filtered.add(s);
            }
        }
    }

    static String modName(String ns) {
        if (ns.equals("minecraft")) return "Minecraft";
        return ModList.get().getModContainerById(ns).map(c -> c.getModInfo().getDisplayName()).orElse(ns);
    }

    int listPages() {
        return Math.max(1, (filtered.size() + cols * rows - 1) / (cols * rows));
    }

    // ------------------------------------------------------------------ layout
    @Override
    protected void init() {
        cols = Mth.clamp((width / 2 - 16) / 18, 4, 14);
        listW = cols * 18;
        lx = width - listW - 8;
        ly = 30;
        rows = Math.max(2, (height - ly - 30) / 18);
        search = new EditBox(font, lx, 8, listW, 16, Component.literal("search"));
        search.setMaxLength(60);
        search.setValue(query);
        search.setHint(Component.literal(t("ค้นหา... (@ชื่อมอด)", "Search... (@mod)")).withStyle(ChatFormatting.DARK_GRAY));
        search.setResponder(s -> {
            if (s.equals(query)) return;
            query = s;
            listPage = 0;
            refilter();
        });
        addRenderableWidget(search);
        refilter();
        listPage = Mth.clamp(listPage, 0, listPages() - 1);
        int by = ly + rows * 18 + 4;
        addRenderableWidget(Button.builder(Component.literal("<"), b -> listPage = Math.floorMod(listPage - 1, listPages())).bounds(lx, by, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> listPage = Math.floorMod(listPage + 1, listPages())).bounds(lx + listW - 20, by, 20, 20).build());

        rx0 = 10;
        rx1 = lx - 14;
        ry0 = 50;
        ry1 = height - 30;
        if (!history.isEmpty())
            addRenderableWidget(Button.builder(Component.literal("<"), b -> back()).bounds(rx0, 6, 18, 18).build());
        if (!focus.isEmpty()) {
            int w = Math.min(90, (rx1 - rx0 - 4) / 2);
            addRenderableWidget(Button.builder(Component.literal((uses ? "" : "§e") + t("สูตรคราฟ (R)", "Recipes (R)")), b -> show(focus, false, false))
                    .bounds(rx0, 28, w, 16).build());
            addRenderableWidget(Button.builder(Component.literal((uses ? "§e" : "") + t("ใช้ทำอะไร (U)", "Uses (U)")), b -> show(focus, true, false))
                    .bounds(rx0 + w + 4, 28, w, 16).build());
        }
        layout();
        recPage = Mth.clamp(recPage, 0, pages - 1);
        if (pages > 1) {
            addRenderableWidget(Button.builder(Component.literal("<"), b -> recPage = Math.floorMod(recPage - 1, pages)).bounds(rx0, height - 26, 20, 20).build());
            addRenderableWidget(Button.builder(Component.literal(">"), b -> recPage = Math.floorMod(recPage + 1, pages)).bounds(rx1 - 20, height - 26, 20, 20).build());
        }
    }

    int boxW(Shown s) {
        int inner = 6 + s.cols * 18 + 8 + 16 + 8 + 22 + 6;
        int label = 16 + font.width(s.label(th()) + (s.shapeless ? "  " + t("ไม่ต้องเรียง", "shapeless") : "")) + 6;
        return Math.min(rx1 - rx0, Math.max(inner, label));
    }

    int boxH(Shown s) {
        return 15 + Math.max(s.rows * 18, 22) + (s.time > 0 ? 11 : 0) + 5;
    }

    /** Flows the recipe boxes into columns and pages: place[i] = {page, x, y}. */
    void layout() {
        place = new int[shown.size()][];
        int page = 0, x = rx0, y = ry0, colW = 0;
        boolean pageEmpty = true;
        for (int i = 0; i < shown.size(); i++) {
            Shown s = shown.get(i);
            int w = boxW(s), h = boxH(s);
            if (!pageEmpty && y + h > ry1) {
                x += colW + 6;
                y = ry0;
                colW = 0;
            }
            if (!pageEmpty && x + w > rx1) {
                page++;
                x = rx0;
                y = ry0;
                colW = 0;
            }
            place[i] = new int[]{page, x, y, w, h};
            pageEmpty = false;
            y += h + 4;
            colW = Math.max(colW, w);
        }
        pages = page + 1;
    }

    // ------------------------------------------------------------------ drawing
    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        hits.clear();
        g.fill(lx - 5, 3, lx + listW + 5, height - 3, 0xD0101418);
        g.fill(4, 3, lx - 9, height - 3, 0xD0101418);
        super.render(g, mx, my, pt);
        long now = hasShiftDown() ? frozen : (frozen = Util.getMillis());
        int cycle = (int) (now / 1000L);

        // the item list
        int per = cols * rows, start = listPage * per;
        for (int i = 0; i < per && start + i < filtered.size(); i++) {
            ItemStack s = filtered.get(start + i);
            int x = lx + (i % cols) * 18, y = ly + (i / cols) * 18;
            item(g, s, x, y, mx, my, false);
        }
        g.drawCenteredString(font, (listPage + 1) + "/" + listPages(), lx + listW / 2, ly + rows * 18 + 10, 0xA0A0A0);
        if (filtered.isEmpty()) g.drawCenteredString(font, t("ไม่พบของ", "Nothing found"), lx + listW / 2, ly + 20, 0x808080);

        // the chosen item and its recipes
        if (focus.isEmpty()) {
            int y = 12;
            g.drawString(font, "§6§l" + t("สมุดสูตรคราฟ", "Recipe Book"), rx0, y, 0xFFFFFF);
            y += 16;
            String[] help = th() ? new String[]{"คลิกซ้ายที่ของ = ดูวิธีทำ", "คลิกขวาที่ของ = ดูว่าใช้ทำอะไรได้", "ในกระเป๋า: ชี้ของแล้วกด R หรือ U",
                    "ค้นหาด้วยชื่อ หรือ @ชื่อมอด", "คลิกของในสูตรเพื่อดูต่อ, Backspace = ย้อนกลับ", "กด Shift ค้างไว้ = หยุดสลับของ",
                    "", "§7" + RecipeIndex.items().size() + " ชิ้น  " + RecipeIndex.recipeCount() + " สูตร (รวมของมอดอื่น)"}
                    : new String[]{"Left click an item = how to make it", "Right click an item = what it is used for", "In inventories: hover an item, press R or U",
                    "Search by name, or @mod", "Click items inside a recipe to follow them, Backspace = back", "Hold Shift = stop cycling items",
                    "", "§7" + RecipeIndex.items().size() + " items, " + RecipeIndex.recipeCount() + " recipes (other mods included)"};
            for (String l : help) {
                g.drawString(font, font.plainSubstrByWidth(l, rx1 - rx0), rx0, y, 0xD0D0D0);
                y += 11;
            }
        } else {
            int hx = rx0 + (history.isEmpty() ? 0 : 22);
            item(g, focus, hx, 6, mx, my, true);
            String name = focus.getHoverName().getString();
            g.drawString(font, font.plainSubstrByWidth(name, rx1 - hx - 22), hx + 21, 7, 0xFFFFFF);
            g.drawString(font, font.plainSubstrByWidth("§7" + (uses ? t("ใช้ทำได้ ", "Used in ") : t("วิธีทำ ", "Made by ")) + shown.size() + t(" สูตร", " recipes")
                    + "  §9" + modName(BuiltInRegistries.ITEM.getKey(focus.getItem()).getNamespace()), rx1 - hx - 22), hx + 21, 17, 0xFFFFFF);
            for (int i = 0; i < shown.size(); i++) {
                int[] p = place[i];
                if (p[0] == recPage) box(g, shown.get(i), p[1], p[2], p[3], p[4], cycle, mx, my);
            }
            if (shown.isEmpty()) {
                String msg = uses ? t("ไม่ได้ใช้ทำอะไรในสูตรคราฟ", "Not used in any recipe")
                        : t("ไม่มีสูตรคราฟ (ได้จากการสำรวจ ขุด ตีมอนสเตอร์ หรือแลกเปลี่ยน)", "No recipe (found, mined, dropped or traded)");
                int y = ry0 + 4;
                for (var line : font.split(Component.literal(msg), rx1 - rx0)) {
                    g.drawString(font, line, rx0, y, 0xA0A0A0);
                    y += 11;
                }
            }
            if (pages > 1) g.drawCenteredString(font, (recPage + 1) + "/" + pages, (rx0 + rx1) / 2, height - 20, 0xA0A0A0);
        }

        // tooltip of whatever is under the mouse
        Hit h = hitAt(mx, my);
        if (h != null) {
            List<Component> tip = new ArrayList<>(Screen.getTooltipFromItem(minecraft, h.stack));
            tip.add(Component.literal(modName(BuiltInRegistries.ITEM.getKey(h.stack.getItem()).getNamespace())).withStyle(ChatFormatting.BLUE, ChatFormatting.ITALIC));
            g.renderTooltip(font, tip, Optional.empty(), mx, my);
        }
    }

    void item(GuiGraphics g, ItemStack s, int x, int y, int mx, int my, boolean count) {
        g.renderItem(s, x + 1, y + 1);
        if (count) g.renderItemDecorations(font, s, x + 1, y + 1);
        hits.add(new Hit(x, y, s));
        if (mx >= x && mx < x + 18 && my >= y && my < y + 18) AbstractContainerScreen.renderSlotHighlight(g, x + 1, y + 1, 0);
    }

    static void slot(GuiGraphics g, int x, int y, int size) {
        g.fill(x, y, x + size, y + size, 0xFF373737);
        g.fill(x + 1, y + 1, x + size, y + size, 0xFFFFFFFF);
        g.fill(x + 1, y + 1, x + size - 1, y + size - 1, 0xFF8B8B8B);
    }

    void box(GuiGraphics g, Shown s, int x, int y, int w, int h, int cycle, int mx, int my) {
        g.fill(x, y, x + w, y + h, 0xFF262B33);
        g.fill(x, y, x + w, y + 1, 0xFF4A5566);
        // header: the station and its name
        ItemStack st = s.station();
        int tx = x + 4;
        if (!st.isEmpty()) {
            g.pose().pushPose();
            g.pose().translate(x + 3, y + 2, 0);
            g.pose().scale(0.625f, 0.625f, 1);
            g.renderItem(st, 0, 0);
            g.pose().popPose();
            tx = x + 16;
        }
        String label = s.label(th()) + (s.shapeless ? "  §8" + t("ไม่ต้องเรียง", "shapeless") : "");
        g.drawString(font, font.plainSubstrByWidth(label, x + w - tx - 2), tx, y + 3, 0xC8C8C8);
        // inputs
        int gx = x + 6, gy = y + 15, gridH = s.rows * 18;
        if (gridH < 22) gy += (22 - gridH) / 2;
        for (int i = 0; i < s.in.size(); i++) {
            int cx = gx + (i % s.cols) * 18, cy = gy + (i / s.cols) * 18;
            slot(g, cx, cy, 18);
            List<ItemStack> alts = s.in.get(i);
            if (!alts.isEmpty()) item(g, alts.get(Math.floorMod(cycle, alts.size())), cx, cy, mx, my, true);
        }
        // the arrow
        int ax = gx + s.cols * 18 + 6, mid = gy + gridH / 2;
        g.fill(ax, mid - 1, ax + 11, mid + 1, 0xFFB0B0B0);
        for (int k = 0; k < 5; k++) g.fill(ax + 10 + k, mid - 4 + k, ax + 11 + k, mid + 4 - k, 0xFFB0B0B0);
        // the result
        int ox = ax + 24, oy = mid - 11;
        slot(g, ox, oy, 22);
        item(g, s.out, ox + 2, oy + 2, mx, my, true);
        if (s.time > 0) {
            String info = t("เวลา ", "Time ") + (s.time % 20 == 0 ? String.valueOf(s.time / 20) : String.format(Locale.ROOT, "%.1f", s.time / 20f)) + "s"
                    + (s.xp > 0 ? "  +" + String.format(Locale.ROOT, "%.1f", s.xp) + " XP" : "");
            g.drawString(font, info, x + 6, gy + Math.max(gridH, 22) + 2, 0x909090);
        }
    }

    @Nullable
    Hit hitAt(double mx, double my) {
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (mx >= h.x && mx < h.x + 18 && my >= h.y && my < h.y + 18) return h;
        }
        return null;
    }

    // ------------------------------------------------------------------ input
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (!search.isMouseOver(mx, my)) {
            search.setFocused(false);
            setFocused(null);
        }
        Hit h = hitAt(mx, my);
        if (h != null && (button == 0 || button == 1)) {
            show(h.stack, button == 1, true);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int d = delta > 0 ? -1 : 1;
        if (mx >= lx - 5) listPage = Math.floorMod(listPage + d, listPages());
        else if (pages > 1) recPage = Math.floorMod(recPage + d, pages);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (search.isFocused()) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                search.setFocused(false);
                setFocused(null);
                return true;
            }
            search.keyPressed(key, scan, mods);
            return true;
        }
        double mx = minecraft.mouseHandler.xpos() * width / minecraft.getWindow().getScreenWidth();
        double my = minecraft.mouseHandler.ypos() * height / minecraft.getWindow().getScreenHeight();
        Hit h = hitAt(mx, my);
        if (h != null && (key == GLFW.GLFW_KEY_R || key == GLFW.GLFW_KEY_U)) {
            show(h.stack, key == GLFW.GLFW_KEY_U, true);
            return true;
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            back();
            return true;
        }
        if (RecipeKeys.OPEN.matches(key, scan) || minecraft.options.keyInventory.matches(key, scan)) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean charTyped(char c, int mods) {
        return search.isFocused() ? search.charTyped(c, mods) : super.charTyped(c, mods);
    }

    @Override
    public void onClose() {
        savedListPage = listPage;
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
