package raidqueue.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Server to client: close the menu screen (if one is open) without opening another. */
public record CloseViewPayload() implements CustomPayload {
    public static final CustomPayload.Id<CloseViewPayload> ID =
        new CustomPayload.Id<>(new Identifier("raid-den-queue", "close_view"));

    public static final PacketCodec<RegistryByteBuf, CloseViewPayload> CODEC =
        PacketCodec.unit(new CloseViewPayload());

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
