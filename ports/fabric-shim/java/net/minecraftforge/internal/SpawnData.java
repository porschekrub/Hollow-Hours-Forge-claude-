package net.minecraftforge.internal;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.entity.IEntityAdditionalSpawnData;
import net.minecraftforge.fml.ModLoadingContext;

/** IEntityAdditionalSpawnData on Fabric: the data goes right after the spawn packet, in the same bundle. */
public final class SpawnData {
    private SpawnData() {}

    static Net net;

    /** The channel (from the mod's common init). */
    public static void register() {
        if (net != null) return;
        net = new Net(ModLoadingContext.MOD_ID);
        net.s2c(Msg.class, Msg::encode, Msg::decode, m -> SpawnDataClient.handle(m));
    }

    public record Msg(int entityId, byte[] data) {
        void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(entityId);
            buf.writeByteArray(data);
        }

        static Msg decode(FriendlyByteBuf buf) {
            return new Msg(buf.readVarInt(), buf.readByteArray());
        }
    }

    @SuppressWarnings("unchecked")
    public static Packet<ClientGamePacketListener> packet(Entity e) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        ((IEntityAdditionalSpawnData) e).writeSpawnData(buf);
        byte[] data = new byte[buf.readableBytes()];
        buf.readBytes(data);
        return (Packet<ClientGamePacketListener>) (Packet<?>) net.packet(new Msg(e.getId(), data));
    }
}
