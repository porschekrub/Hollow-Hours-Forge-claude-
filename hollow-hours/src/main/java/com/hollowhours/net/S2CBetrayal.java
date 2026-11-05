package com.hollowhours.net;

import com.hollowhours.client.ClientPackets;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** "Tell me where your friend is... and I might spare you." Opens (or closes when open=false) the choice screen. */
public record S2CBetrayal(boolean open, int kind, int seconds, List<UUID> ids, List<String> names) {
    public static S2CBetrayal close() {
        return new S2CBetrayal(false, 0, 0, List.of(), List.of());
    }

    public static void encode(S2CBetrayal m, FriendlyByteBuf buf) {
        buf.writeBoolean(m.open);
        buf.writeVarInt(m.kind);
        buf.writeVarInt(m.seconds);
        buf.writeVarInt(m.ids.size());
        for (int i = 0; i < m.ids.size(); i++) {
            buf.writeUUID(m.ids.get(i));
            buf.writeUtf(m.names.get(i), 64);
        }
    }

    public static S2CBetrayal decode(FriendlyByteBuf buf) {
        boolean open = buf.readBoolean();
        int kind = buf.readVarInt(), sec = buf.readVarInt(), n = Math.min(64, buf.readVarInt());
        List<UUID> ids = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            ids.add(buf.readUUID());
            names.add(buf.readUtf(64));
        }
        return new S2CBetrayal(open, kind, sec, ids, names);
    }

    public static void handle(S2CBetrayal m) {
        ClientPackets.betrayal(m);
    }
}
