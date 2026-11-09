package com.helperbuddy.talk;

import com.helperbuddy.HelperBuddy;
import com.helperbuddy.config.HBConfig;
import com.helperbuddy.net.C2SVoiceIn;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Orders spoken into the microphone: the client sends the recording, the AI (Gemini) writes down what was said, and
 * the helpers take it like a line typed in chat.
 */
public final class VoiceIn {
    private VoiceIn() {}

    static final Map<UUID, ByteArrayOutputStream> PARTS = new HashMap<>();
    static final Map<UUID, Integer> CLIP = new HashMap<>();
    static final Set<UUID> BUSY = new HashSet<>();
    /** recordings that arrived whole (the client smoke test looks) */
    public static int received;
    /** at most 15 seconds at 16 kHz */
    static final int MAX = 16000 * 15;

    public static void receive(ServerPlayer p, C2SVoiceIn m) {
        UUID id = p.getUUID();
        if (m.part() == 0 || !Integer.valueOf(m.clip()).equals(CLIP.get(id))) {
            PARTS.put(id, new ByteArrayOutputStream());
            CLIP.put(id, m.clip());
        }
        ByteArrayOutputStream b = PARTS.get(id);
        if (b.size() + m.data().length > MAX) return;
        b.writeBytes(m.data());
        if (!m.last()) return;
        PARTS.remove(id);
        byte[] ulaw = b.toByteArray();
        received++;
        hear(p, ulaw, Math.max(8000, Math.min(48000, m.rate())));
    }

    static void tell(ServerPlayer p, String th, String en) {
        p.sendSystemMessage(Component.literal(Say.t(p, th, en)));
    }

    static void hear(ServerPlayer p, byte[] ulaw, int rate) {
        if (!HBConfig.LISTEN.get()) {
            tell(p, "§7(การสั่งผู้ช่วยด้วยเสียงถูกปิดไว้ในตั้งค่า: [voice] listen)", "§7(Talking to helpers by voice is turned off in the settings: [voice] listen)");
            return;
        }
        if (!AiBridge.canHear()) {
            tell(p, "§6[ผู้ช่วย]§r การสั่งด้วยเสียงต้องใช้ §eGemini AI§r: ใส่คีย์ใน §fconfig/helperbuddy-common.toml§r ([ai] key) ก่อนนะ (ตอนนี้พิมพ์สั่งในแชทได้)",
                    "§6[Helper]§r Voice orders need the §eGemini AI§r: put a key in §fconfig/helperbuddy-common.toml§r ([ai] key) first (typing in chat works now)");
            return;
        }
        if (!BUSY.add(p.getUUID())) {
            tell(p, "§7(ยังฟังอันเดิมอยู่ รอแป๊บ)", "§7(still listening to the last one, wait a moment)");
            return;
        }
        MinecraftServer server = p.server;
        UUID id = p.getUUID();
        AiBridge.transcribe(wav(ulaw, rate)).whenComplete((text, err) -> server.execute(() -> {
            BUSY.remove(id);
            ServerPlayer pl = server.getPlayerList().getPlayer(id);
            if (pl == null) return;
            if (text == null) {
                tell(pl, "§c(ฟังไม่ได้ ติดต่อ AI ไม่ได้ ลองใหม่อีกที)§r", "§c(Couldn't listen: the AI didn't answer. Try again)§r");
                return;
            }
            if (text.isBlank()) {
                tell(pl, "§7(ไม่ได้ยินอะไรเลย พูดดังๆ อีกทีนะ)", "§7(Didn't hear anything, say it again louder)");
                return;
            }
            String said = text.length() > 300 ? text.substring(0, 300) : text;
            HelperBuddy.LOG.info("[Helper Buddy] {} said: {}", pl.getGameProfile().getName(), said);
            pl.sendSystemMessage(Component.literal("§7🎤 \"" + said + "\""));
            Talk.command(pl, said);
        }));
    }

    /** 8-bit mu-law -> a 16-bit mono WAV file. */
    public static byte[] wav(byte[] ulaw, int rate) {
        int n = ulaw.length;
        byte[] out = new byte[44 + n * 2];
        java.nio.ByteBuffer b = java.nio.ByteBuffer.wrap(out).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes()).putInt(36 + n * 2).put("WAVE".getBytes()).put("fmt ".getBytes()).putInt(16).putShort((short) 1).putShort((short) 1)
                .putInt(rate).putInt(rate * 2).putShort((short) 2).putShort((short) 16).put("data".getBytes()).putInt(n * 2);
        for (byte u : ulaw) b.putShort(VoiceTts.fromUlaw(u));
        return out;
    }
}
