package raidqueue.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Server to client: replace whatever's on screen with this menu. */
public record OpenViewPayload(View view) implements CustomPayload {
    public static final CustomPayload.Id<OpenViewPayload> ID =
        new CustomPayload.Id<>(new Identifier("raid-den-queue", "open_view"));

    public static final PacketCodec<RegistryByteBuf, OpenViewPayload> CODEC =
        PacketCodec.tuple(View.PACKET_CODEC, OpenViewPayload::view, OpenViewPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
