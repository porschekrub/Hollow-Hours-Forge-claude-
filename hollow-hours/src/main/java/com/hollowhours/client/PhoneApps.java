package com.hollowhours.client;

import com.hollowhours.net.C2SChat;
import com.hollowhours.net.HHNet;
import com.hollowhours.net.S2CPhoneInfo;
import com.hollowhours.phone.PhoneChat;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** The phone's chat, map (location / navigation) and rescue apps. */
final class PhoneApps {
    private PhoneApps() {}

    /** something tappable drawn this frame */
    record Hit(int x, int y, int w, int h, Runnable action) {
        boolean in(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    static final List<Hit> HITS = new ArrayList<>();

    static boolean click(double mx, double my) {
        for (Hit h : new ArrayList<>(HITS))
            if (h.in(mx, my)) {
                h.action.run();
                return true;
            }
        return false;
    }

    // ================================================================== chat
    /** the open conversation (null = the contact list) */
    static String chatWith;
    static final StringBuilder typed = new StringBuilder();
    static int scroll;

    static List<String[]> contacts() {
        List<String[]> out = new ArrayList<>();
        for (String n : PhoneChat.NPCS) out.add(new String[] {n, Component.translatable("hollowhours.chat.name." + n).getString()});
        Minecraft mc = Minecraft.getInstance();
        List<String> seen = new ArrayList<>();
        if (mc.getConnection() != null)
            for (PlayerInfo pi : mc.getConnection().getOnlinePlayers()) {
                UUID id = pi.getProfile().getId();
                if (mc.player != null && id.equals(mc.player.getUUID())) continue;
                out.add(new String[] {"p:" + id, pi.getProfile().getName()});
                seen.add("p:" + id);
            }
        for (var e : ClientPhone.CHATS.entrySet())
            if (e.getKey().startsWith("p:") && !seen.contains(e.getKey())) out.add(new String[] {e.getKey(), e.getValue().name.isEmpty() ? "?" : e.getValue().name});
        return out;
    }

    static boolean dead(String contact) {
        return ClientPhone.info != null && ClientPhone.info.dead().contains(contact);
    }

    static boolean online(String contact) {
        if (PhoneChat.isNpc(contact)) return !lost();
        if (dead(contact)) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return false;
        for (PlayerInfo pi : mc.getConnection().getOnlinePlayers()) if (contact.equals("p:" + pi.getProfile().getId())) return true;
        return false;
    }

    /** after night 15 (the last messages came) Mom, Dad and your friend don't answer */
    static boolean lost() {
        for (String n : PhoneChat.NPCS) {
            ClientPhone.Chat c = ClientPhone.CHATS.get(n);
            if (c != null) for (String l : c.lines) if (l.endsWith(".final")) return true;
        }
        return false;
    }

    static void renderChat(PhoneScreen s, GuiGraphics g, Font f, int mx, int my, int top) {
        if (chatWith == null) renderContacts(s, g, f, mx, my, top);
        else renderConversation(s, g, f, mx, my, top);
    }

    static void renderContacts(PhoneScreen s, GuiGraphics g, Font f, int mx, int my, int top) {
        ClientPhone.open = null;
        s.header(g, f, top, Component.translatable("hollowhours.phone.app.chat"));
        int y = top + 16;
        for (String[] c : contacts()) {
            if (y > s.sy + s.sh - 22) break;
            String id = c[0];
            boolean npc = PhoneChat.isNpc(id), on = online(id);
            boolean hover = mx >= s.sx && mx < s.sx + s.sw && my >= y && my < y + 22;
            if (hover) g.fill(s.sx, y, s.sx + s.sw, y + 22, 0x30FFFFFF);
            int av = npc ? switch (id) {
                case "mom" -> 0xFFE46A9A;
                case "dad" -> 0xFF4A86D8;
                default -> 0xFFF0A030;
            } : 0xFF3FB27F;
            if (!on) av = 0xFF555560;
            g.fill(s.sx + 4, y + 3, s.sx + 20, y + 19, av);
            String letter = c[1].isEmpty() ? "?" : c[1].substring(0, 1).toUpperCase();
            g.drawCenteredString(f, letter, s.sx + 12, y + 7, 0xFFFFFFFF);
            g.drawString(f, c[1], s.sx + 24, y + 2, on ? 0xFFFFFFFF : 0xFF9090A0, false);
            ClientPhone.Chat ch = ClientPhone.CHATS.get(id);
            Component sub = !on ? Component.translatable(npc || dead(id) ? "hollowhours.chat.nosignal" : "hollowhours.chat.offline")
                    : ch == null || ch.lines.isEmpty() ? Component.translatable("hollowhours.chat.empty") : preview(ch.lines.get(ch.lines.size() - 1));
            String line = f.plainSubstrByWidth(sub.getString(), s.sw - 34);
            g.drawString(f, line, s.sx + 24, y + 12, 0xFF8090A8, false);
            int un = ClientPhone.unread(id);
            if (un > 0) {
                g.fill(s.sx + s.sw - 14, y + 6, s.sx + s.sw - 4, y + 16, 0xFFFF3B30);
                g.drawCenteredString(f, String.valueOf(Math.min(9, un)), s.sx + s.sw - 9, y + 7, 0xFFFFFFFF);
            }
            HITS.add(new Hit(s.sx, y, s.sw, 22, () -> {
                chatWith = id;
                scroll = 0;
                typed.setLength(0);
                s.sound("phone.click", 1.2f);
            }));
            y += 23;
        }
    }

    static Component preview(String line) {
        String[] p = line.split("\\|", 3);
        if (p.length < 3) return Component.empty();
        Component body = body(p);
        return p[0].equals("me") ? Component.translatable("hollowhours.chat.you", body) : body;
    }

    static Component body(String[] p) {
        return switch (p[1]) {
            case "k" -> Component.translatable(p[2]);
            case "l" -> location(p[2]);
            case "x", "n" -> p[2].matches("-?\\d+,-?\\d+,-?\\d+,.*") ? location(p[2]) : Component.literal(p[2]);
            default -> Component.literal(p[2]);
        };
    }

    static Component location(String payload) {
        String[] c = payload.split(",");
        return c.length < 3 ? Component.literal("◉") : Component.translatable("hollowhours.chat.location", c[0], c[1], c[2]);
    }

    static void renderConversation(PhoneScreen s, GuiGraphics g, Font f, int mx, int my, int top) {
        ClientPhone.Chat ch = ClientPhone.chat(chatWith);
        ClientPhone.open = chatWith;
        ch.seen = ch.lines.size();
        String name = chatWith;
        for (String[] c : contacts()) if (c[0].equals(chatWith)) name = c[1];
        s.header(g, f, top, Component.literal(name));
        HITS.add(new Hit(s.sx, top, 30, 14, () -> {
            chatWith = null;
            s.sound("phone.click", 1.0f);
        }));
        int inputTop = s.sy + s.sh - 18, areaTop = top + 16;
        // messages, newest at the bottom
        int wrap = s.sw - 34, y = inputTop - 4;
        boolean typing = PhoneChat.isNpc(chatWith) && !lost() && !ch.lines.isEmpty() && ch.lines.get(ch.lines.size() - 1).startsWith("me|")
                && !ch.lines.get(ch.lines.size() - 1).startsWith("me|x|") && !ch.lines.get(ch.lines.size() - 1).startsWith("me|n|") && System.currentTimeMillis() - ch.lastSentAt < 12000;
        if (typing) {
            String dots = ".".repeat(1 + (s.ticks / 6) % 3);
            g.drawString(f, Component.translatable("hollowhours.chat.typing", dots), s.sx + 6, y - 9, 0xFF8090A8, false);
            y -= 12;
        }
        g.enableScissor(s.sx, areaTop, s.sx + s.sw, inputTop - 2);
        for (int i = ch.lines.size() - 1 - scroll; i >= 0 && y > areaTop; i--) {
            String[] p = ch.lines.get(i).split("\\|", 3);
            if (p.length < 3) continue;
            boolean me = p[0].equals("me"), nosig = p[1].equals("n"), failed = p[1].equals("x") || nosig, loc = p[1].equals("l") || failed && p[2].matches("-?\\d+,-?\\d+,-?\\d+,.*");
            List<FormattedCharSequence> rows = f.split(body(p), wrap);
            int w = 0;
            for (FormattedCharSequence r : rows) w = Math.max(w, f.width(r));
            int h = rows.size() * 10 + 4 + (failed ? 9 : 0);
            y -= h;
            int bx = me ? s.sx + s.sw - w - 10 : s.sx + 4;
            int col = failed ? 0xFF4A3036 : loc ? 0xFF1F7A5A : me ? 0xFF2F6FD0 : 0xFF3A3F4A;
            g.fill(bx, y, bx + w + 6, y + h, col);
            int ty = y + 2;
            for (FormattedCharSequence r : rows) {
                g.drawString(f, r, bx + 3, ty, 0xFFFFFFFF, false);
                ty += 10;
            }
            if (failed) g.drawString(f, Component.translatable(nosig ? "hollowhours.chat.failed.nosignal" : "hollowhours.chat.failed"), bx + 3, ty, 0xFFFF8080, false);
            if (loc && !failed) {
                String[] c = p[2].split(",");
                final String who = me ? Component.translatable("hollowhours.chat.you.short").getString() : name;
                final int yy = y, hh = h, xx = bx, ww = w + 6;
                if (c.length >= 4) HITS.add(new Hit(xx, Math.max(yy, areaTop), ww, hh, () -> {
                    try {
                        ClientPhone.navigate(new ClientPhone.Nav("pin", who, Integer.parseInt(c[0]), Integer.parseInt(c[1]), Integer.parseInt(c[2]), c[3]));
                    } catch (NumberFormatException ignored) {}
                }));
            }
            y -= 3;
        }
        g.disableScissor();
        // the input: [location] [text...] [send]
        boolean can = online(chatWith) || PhoneChat.isNpc(chatWith);
        g.fill(s.sx, inputTop - 1, s.sx + s.sw, s.sy + s.sh, 0xC0101018);
        g.fill(s.sx + 2, inputTop + 1, s.sx + 16, inputTop + 15, 0xFF1F7A5A);
        g.drawCenteredString(f, "◉", s.sx + 9, inputTop + 4, 0xFFFFFFFF);
        g.fill(s.sx + 18, inputTop + 1, s.sx + s.sw - 18, inputTop + 15, 0xFF22262E);
        String shown = typed.length() == 0 ? Component.translatable(can ? "hollowhours.chat.hint" : dead(chatWith) ? "hollowhours.chat.nosignal" : "hollowhours.chat.offline").getString() : typed.toString();
        String fit = typed.length() == 0 ? f.plainSubstrByWidth(shown, s.sw - 40) : tail(f, shown, s.sw - 42);
        g.drawString(f, fit + (typed.length() > 0 && (s.ticks / 8) % 2 == 0 ? "_" : ""), s.sx + 21, inputTop + 4, typed.length() == 0 ? 0xFF707888 : 0xFFFFFFFF, false);
        g.fill(s.sx + s.sw - 16, inputTop + 1, s.sx + s.sw - 2, inputTop + 15, 0xFF2F6FD0);
        g.drawCenteredString(f, "➤", s.sx + s.sw - 9, inputTop + 4, 0xFFFFFFFF);
        HITS.add(new Hit(s.sx + 2, inputTop + 1, 14, 14, () -> sendLocation(s)));
        HITS.add(new Hit(s.sx + s.sw - 16, inputTop + 1, 14, 14, () -> sendTyped(s)));
    }

    static String tail(Font f, String s, int w) {
        while (s.length() > 1 && f.width(s) > w) s = s.substring(1);
        return s;
    }

    static void sendTyped(PhoneScreen s) {
        if (chatWith == null || typed.toString().isBlank()) return;
        HHNet.toServer(new C2SChat(chatWith, PhoneChat.TEXT, typed.toString()));
        ClientPhone.chat(chatWith).lastSentAt = System.currentTimeMillis();
        typed.setLength(0);
        scroll = 0;
        s.sound("phone.click", 1.6f);
    }

    static void sendLocation(PhoneScreen s) {
        if (chatWith == null) return;
        HHNet.toServer(new C2SChat(chatWith, PhoneChat.LOCATION, ""));
        ClientPhone.chat(chatWith).lastSentAt = System.currentTimeMillis();
        scroll = 0;
        s.sound("phone.click", 1.4f);
    }

    static boolean chatKey(PhoneScreen s, int key) {
        if (chatWith == null) return false;
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            sendTyped(s);
            return true;
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            if (typed.length() > 0) typed.setLength(typed.length() - 1);
            return true;
        }
        if (net.minecraft.client.gui.screens.Screen.isPaste(key)) {
            String clip = Minecraft.getInstance().keyboardHandler.getClipboard().replace('\n', ' ');
            typed.append(clip, 0, Math.min(clip.length(), 150 - typed.length()));
            return true;
        }
        // typing letters must not trigger game keys (the inventory, dropping items...)
        return key != GLFW.GLFW_KEY_ESCAPE;
    }

    static boolean chatChar(char c) {
        if (chatWith == null || c < ' ' || typed.length() >= 150) return false;
        typed.append(c);
        return true;
    }

    static void chatScroll(double delta) {
        if (chatWith == null) return;
        ClientPhone.Chat ch = ClientPhone.chat(chatWith);
        scroll = Mth.clamp(scroll + (delta > 0 ? 1 : -1), 0, Math.max(0, ch.lines.size() - 1));
    }

    // ================================================================== map
    static final int CELLS = 40, BLOCKS = 2;
    static final int[] map = new int[CELLS * CELLS];
    static int mapX = Integer.MIN_VALUE, mapZ, mapAt = -100;

    static void sampleMap(PhoneScreen s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        int px = mc.player.getBlockX(), pz = mc.player.getBlockZ();
        if (s.ticks - mapAt < 20 && px == mapX && pz == mapZ) return;
        mapAt = s.ticks;
        mapX = px;
        mapZ = pz;
        var level = mc.level;
        int[] h = new int[CELLS * CELLS];
        for (int j = 0; j < CELLS; j++)
            for (int i = 0; i < CELLS; i++) {
                int x = px + (i - CELLS / 2) * BLOCKS, z = pz + (j - CELLS / 2) * BLOCKS;
                if (!level.hasChunk(x >> 4, z >> 4)) {
                    map[j * CELLS + i] = 0xFF101014;
                    h[j * CELLS + i] = Integer.MIN_VALUE;
                    continue;
                }
                int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                BlockPos pos = new BlockPos(x, y, z);
                MapColor mc2 = level.getBlockState(pos).getMapColor(level, pos);
                map[j * CELLS + i] = mc2 == MapColor.NONE ? 0xFF202024 : 0xFF000000 | mc2.col;
                h[j * CELLS + i] = y;
            }
        // shade the slopes like a paper map (lit from the north-west)
        for (int j = 1; j < CELLS; j++)
            for (int i = 0; i < CELLS; i++) {
                int a = h[j * CELLS + i], b = h[(j - 1) * CELLS + i];
                if (a == Integer.MIN_VALUE || b == Integer.MIN_VALUE) continue;
                float k = a > b ? 1.12f : a < b ? 0.78f : 0.94f;
                int c = map[j * CELLS + i];
                int r = Math.min(255, (int) (((c >> 16) & 255) * k)), gg = Math.min(255, (int) (((c >> 8) & 255) * k)), bb = Math.min(255, (int) ((c & 255) * k));
                map[j * CELLS + i] = 0xFF000000 | r << 16 | gg << 8 | bb;
            }
    }

    record Place(String kind, Component label, int x, int y, int z, String dim, int color, String glyph) {}

    static List<Place> places() {
        List<Place> out = new ArrayList<>();
        S2CPhoneInfo info = ClientPhone.info;
        if (info != null)
            for (S2CPhoneInfo.Target t : info.targets()) {
                int col = switch (t.kind()) {
                    case "home" -> 0xFF5BD06A;
                    case "heli" -> 0xFFFFD040;
                    default -> 0xFFB0B0B8;
                };
                String glyph = switch (t.kind()) {
                    case "home" -> "⌂";
                    case "heli" -> "H";
                    default -> "✝";
                };
                out.add(new Place(t.kind(), ClientPhone.targetName(t.kind()), t.x(), t.y(), t.z(), t.dim(), col, glyph));
            }
        // the latest location each friend sent you
        for (var e : ClientPhone.CHATS.entrySet()) {
            if (!e.getKey().startsWith("p:")) continue;
            List<String> l = e.getValue().lines;
            for (int i = l.size() - 1; i >= 0; i--) {
                String[] p = l.get(i).split("\\|", 3);
                if (p.length == 3 && p[0].equals("them") && p[1].equals("l")) {
                    String[] c = p[2].split(",");
                    try {
                        out.add(new Place("pin", Component.literal(e.getValue().name), Integer.parseInt(c[0]), Integer.parseInt(c[1]), Integer.parseInt(c[2]),
                                c.length > 3 ? c[3] : "minecraft:overworld", 0xFF4FA8FF, "◉"));
                    } catch (Exception ignored) {}
                    break;
                }
            }
        }
        return out;
    }

    static boolean sameNav(Place p) {
        ClientPhone.Nav n = ClientPhone.nav;
        return n != null && n.kind().equals(p.kind) && (!p.kind.equals("pin") || n.x() == p.x && n.z() == p.z);
    }

    static void renderMap(PhoneScreen s, GuiGraphics g, Font f, int mx, int my, int top) {
        Minecraft mc = Minecraft.getInstance();
        s.header(g, f, top, Component.translatable("hollowhours.phone.app.map"));
        if (mc.player == null || mc.level == null) return;
        sampleMap(s);
        String dim = mc.level.dimension().location().toString();
        g.drawString(f, String.format("X %d  Y %d  Z %d", mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ()), s.sx + 4, top + 17, 0xFFFFFFFF, false);
        g.drawString(f, Component.translatable("hollowhours.map.facing", facing(mc.player.getYRot())), s.sx + 4, top + 27, 0xFF9AB0D0, false);
        int cell = Math.max(2, Math.min((s.sw - 8) / CELLS, 3)), size = cell * CELLS;
        int ox = s.sx + (s.sw - size) / 2, oy = top + 39;
        for (int j = 0; j < CELLS; j++)
            for (int i = 0; i < CELLS; i++) g.fill(ox + i * cell, oy + j * cell, ox + i * cell + cell, oy + j * cell + cell, map[j * CELLS + i]);
        g.renderOutline(ox - 1, oy - 1, size + 2, size + 2, 0xFF606878);
        g.drawCenteredString(f, "N", ox + size / 2, oy + 1, 0xFFFF6060);
        // places on the map (at the edge when farther)
        List<Place> places = places();
        double scale = cell / (double) BLOCKS;
        for (Place p : places) {
            if (!p.dim.equals(dim)) continue;
            double dx = (p.x - mc.player.getX()) * scale, dz = (p.z - mc.player.getZ()) * scale;
            double lim = size / 2.0 - 4;
            double m = Math.max(Math.abs(dx), Math.abs(dz));
            if (m > lim) {
                dx *= lim / m;
                dz *= lim / m;
            }
            int cx = (int) (ox + size / 2.0 + dx), cy = (int) (oy + size / 2.0 + dz);
            g.drawCenteredString(f, p.glyph, cx, cy - 4, p.color);
        }
        // you
        g.pose().pushPose();
        g.pose().translate(ox + size / 2f, oy + size / 2f, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(mc.player.getYRot() + 180));
        g.drawString(f, "▲", -f.width("▲") / 2, -4, 0xFFFFFFFF, false);
        g.pose().popPose();
        // the list: tap to navigate there (tap again to stop)
        int y = oy + size + 4;
        for (Place p : places) {
            if (y > s.sy + s.sh - 12) break;
            boolean sel = sameNav(p);
            if (sel) g.fill(s.sx + 2, y - 1, s.sx + s.sw - 2, y + 10, 0x50FFFFFF);
            String dist = p.dim.equals(dim) ? Math.round(Math.sqrt(sq(p.x - mc.player.getX()) + sq(p.z - mc.player.getZ()))) + " m"
                    : Component.translatable("hollowhours.map.elsewhere").getString();
            g.drawString(f, p.glyph, s.sx + 5, y, p.color, false);
            g.drawString(f, f.plainSubstrByWidth(p.label.getString(), s.sw - 60), s.sx + 15, y, 0xFFFFFFFF, false);
            g.drawString(f, dist, s.sx + s.sw - 4 - f.width(dist), y, 0xFFB0C0D8, false);
            final Place pl = p;
            HITS.add(new Hit(s.sx, y - 1, s.sw, 11, () -> ClientPhone.navigate(sameNav(pl) ? null
                    : new ClientPhone.Nav(pl.kind, pl.label.getString(), pl.x, pl.y, pl.z, pl.dim))));
            y += 12;
        }
        if (places.isEmpty()) s.drawWrapped(g, f, Component.translatable("hollowhours.map.none"), s.sx + 4, y, s.sw - 8, 0xFF8090A8, false);
    }

    static double sq(double d) {
        return d * d;
    }

    static Component facing(float yaw) {
        String[] dirs = {"s", "sw", "w", "nw", "n", "ne", "e", "se"};
        int i = Math.floorMod(Math.round(Mth.wrapDegrees(yaw) / 45f), 8);
        return Component.translatable("hollowhours.map.dir." + dirs[i]);
    }

    // ================================================================== rescue
    static void renderRescue(PhoneScreen s, GuiGraphics g, Font f, int top) {
        Minecraft mc = Minecraft.getInstance();
        s.header(g, f, top, Component.translatable("hollowhours.phone.app.rescue"));
        S2CPhoneInfo info = ClientPhone.info;
        int y = top + 22, w = s.sw - 12, x = s.sx + 6;
        if (info == null || mc.level == null) {
            s.drawWrapped(g, f, Component.translatable("hollowhours.rescue.app.nosignal"), x, y, w, 0xFF8090A8, true);
            return;
        }
        long now = mc.level.getDayTime();
        if (info.phase() == 0) {
            if (info.pickupLeft() >= 0) {
                big(g, f, s, y, Component.translatable("hollowhours.rescue.app.here"), 0xFFFF5050);
                y += 24;
                int sec = info.pickupLeft() / 20;
                big(g, f, s, y, Component.literal(String.format("%d:%02d", sec / 60, sec % 60)), 0xFFFFFFFF);
                y += 26;
                y = s.drawWrapped(g, f, Component.translatable("hollowhours.rescue.app.board"), x, y, w, 0xFFFFD0D0, true);
            } else if (info.arriveAt() > now) {
                y = s.drawWrapped(g, f, Component.translatable("hollowhours.rescue.app.arrives"), x, y, w, 0xFFB0E0C0, true) + 4;
                long t = info.arriveAt() - now;
                long d = t / 24000, h = t % 24000 / 1000, m = t % 1000 * 60 / 1000;
                big(g, f, s, y, Component.translatable("hollowhours.rescue.app.left", d, String.format("%02d", h), String.format("%02d", m)), 0xFFFFFFFF);
                y += 26;
                y = s.drawWrapped(g, f, Component.translatable("hollowhours.rescue.app.when", info.arriveAt() / 24000 + 1,
                        com.hollowhours.rescue.Rescue.clock(info.arriveAt())), x, y, w, 0xFFB0C0D8, true) + 4;
                y = s.drawWrapped(g, f, Component.translatable("hollowhours.rescue.app.ready"), x, y, w, 0xFF8090A8, true);
            } else y = s.drawWrapped(g, f, Component.translatable("hollowhours.rescue.app.nonews"), x, y, w, 0xFF8090A8, true);
            if (info.missed() > 0)
                s.drawWrapped(g, f, Component.translatable("hollowhours.rescue.app.harsh", fmt(info.ghostMult()), fmt(info.bloodMult())), x, y + 6, w, 0xFFFF7070, true);
        } else if (info.phase() == 1) {
            if (info.invaded()) {
                big(g, f, s, y, Component.translatable("hollowhours.rescue.app.run"), 0xFFFF5050);
                s.drawWrapped(g, f, Component.translatable("hollowhours.rescue.app.runmap"), x, y + 26, w, 0xFFFFD0D0, true);
            } else {
                big(g, f, s, y, Component.translatable("hollowhours.rescue.arrived.title"), 0xFF7CE08A);
                s.drawWrapped(g, f, Component.translatable("hollowhours.rescue.app.safe"), x, y + 26, w, 0xFFC0E8C8, true);
            }
        } else {
            big(g, f, s, y, Component.translatable("hollowhours.rescue.home.title"), 0xFFFF7070);
            y = s.drawWrapped(g, f, Component.translatable("hollowhours.rescue.app.nobody"), x, y + 26, w, 0xFFE0C0C0, true);
            s.drawWrapped(g, f, Component.translatable("hollowhours.rescue.app.harsh", fmt(info.ghostMult()), fmt(info.bloodMult())), x, y + 6, w, 0xFFFF7070, true);
        }
        // a quick way to the helicopter
        for (Place p : places())
            if (p.kind.equals("heli")) {
                int by = s.sy + s.sh - 18;
                boolean sel = sameNav(p);
                g.fill(s.sx + 10, by, s.sx + s.sw - 10, by + 14, sel ? 0xFF8A6A10 : 0xFFC09018);
                g.drawCenteredString(f, Component.translatable(sel ? "hollowhours.map.stop" : "hollowhours.rescue.app.navigate"), s.sx + s.sw / 2, by + 3, 0xFFFFFFFF);
                HITS.add(new Hit(s.sx + 10, by, s.sw - 20, 14, () -> ClientPhone.navigate(sel ? null : new ClientPhone.Nav(p.kind, p.label.getString(), p.x, p.y, p.z, p.dim))));
            }
    }

    static void big(GuiGraphics g, Font f, PhoneScreen s, int y, Component c, int col) {
        float k = Math.min(1.8f, (s.sw - 8) / (float) Math.max(1, f.width(c)));
        g.pose().pushPose();
        g.pose().translate(s.sx + s.sw / 2f, y, 0);
        g.pose().scale(k, k, 1);
        g.drawCenteredString(f, c, 0, 0, col);
        g.pose().popPose();
    }

    static String fmt(float v) {
        return v == Math.floor(v) ? String.valueOf((int) v) : String.valueOf(v);
    }
}
