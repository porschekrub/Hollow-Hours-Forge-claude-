package com.helperbuddy.client.screen;

import com.helperbuddy.net.C2SAction;
import com.helperbuddy.net.C2STalk;
import com.helperbuddy.net.HBNet;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.UUID;

/** The command book: all your helpers wherever they are, their menus, and a box to talk to all of them. */
public class BookScreen extends Screen {
    final CompoundTag d;
    int left, top;
    static final int W = 320;
    EditBox box;

    public BookScreen(CompoundTag data) {
        super(Component.literal("Helper Buddy"));
        this.d = data;
    }

    boolean th() {
        return d.getBoolean("thai");
    }

    String t(String a, String b) {
        return th() ? a : b;
    }

    ListTag helpers() {
        return d.getList("helpers", Tag.TAG_COMPOUND);
    }

    @Override
    protected void init() {
        ListTag l = helpers();
        int rows = Math.min(7, l.size());
        int h = 60 + rows * 24 + 30;
        left = (width - W) / 2;
        top = (height - h) / 2;
        for (int i = 0; i < rows; i++) {
            CompoundTag c = l.getCompound(i);
            int y = top + 30 + i * 24;
            int id = c.getInt("id");
            UUID uuid = c.getUUID("uuid");
            addRenderableWidget(Button.builder(Component.literal(t("เมนู", "Menu")), b -> HBNet.toServer(new C2SAction(id, uuid, "open", new CompoundTag())))
                    .bounds(left + W - 108, y, 52, 20).build());
            addRenderableWidget(Button.builder(Component.literal(t("เรียก", "Call")), b -> HBNet.toServer(new C2SAction(id, uuid, "come", new CompoundTag())))
                    .bounds(left + W - 54, y, 50, 20).build());
        }
        box = new EditBox(font, left + 4, top + 34 + rows * 24, W - 66, 20, Component.literal("talk"));
        box.setMaxLength(300);
        addRenderableWidget(box);
        addRenderableWidget(Button.builder(Component.literal(t("ส่ง", "Send")), b -> send()).bounds(left + W - 58, top + 34 + rows * 24, 54, 20).build());
    }

    void send() {
        if (box.getValue().isBlank()) return;
        HBNet.toServer(new C2STalk(-1, new UUID(0, 0), box.getValue()));
        box.setValue("");
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (box != null && box.isFocused() && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)) {
            send();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    //#if MC>=12100
    //$$ @Override
    //$$ public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {} // drawn once, by render()
    //$$
    //#endif
    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        //#if MC>=12100
        //$$ renderTransparentBackground(g);
        //#else
        renderBackground(g);
        //#endif
        ListTag l = helpers();
        int rows = Math.min(7, l.size());
        g.fill(left - 4, top - 4, left + W + 4, top + 60 + rows * 24 + 4, 0xE0101418);
        g.drawString(font, t("§6§lหนังสือสั่งงาน", "§6§lCommand Book"), left + 4, top + 4, 0xFFFFFF);
        g.drawString(font, l.isEmpty() ? t("§7ยังไม่มีผู้ช่วย (หรืออยู่ในพื้นที่ที่ยังไม่โหลด)", "§7No helpers (or they are in unloaded areas)")
                : t("§7พิมพ์ด้านล่างเพื่อสั่งงาน (ขึ้นต้นด้วยชื่อ หรือ \"ทุกคน\")", "§7Type below to give orders (start with a name or \"everyone\")"), left + 4, top + 16, 0xFFFFFF);
        for (int i = 0; i < rows; i++) {
            CompoundTag c = l.getCompound(i);
            int y = top + 30 + i * 24;
            g.drawString(font, "§e" + c.getString("name") + " §b" + t("Lv", "Lv") + c.getInt("level") + " §a❤" + (int) Math.ceil(c.getFloat("hp")) + "/" + c.getInt("max"), left + 4, y + 1, 0xFFFFFF);
            g.drawString(font, "§7" + c.getString("modeLabel") + " • " + c.getString("dim") + (c.getInt("dist") >= 0 ? " • " + c.getInt("dist") + "m" : ""), left + 4, y + 11, 0xFFFFFF);
        }
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
