package raidqueue.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Client to server: the player clicked {@code slot} in the menu identified by
 * {@code viewId}, with mouse button {@code button} (0 = left, 1 = right). A
 * {@code button} of -1 means "the player closed the screen" - {@code slot} is
 * meaningless in that case.
 */
public record ClickPayload(Identifier viewId, int slot, int button) implements CustomPayload {
    public static final CustomPayload.Id<ClickPayload> ID =
        new CustomPayload.Id<>(new Identifier("raid-den-queue", "click"));

    public static final PacketCodec<RegistryByteBuf, ClickPayload> CODEC = PacketCodec.tuple(
        Identifier.PACKET_CODEC, ClickPayload::viewId,
        PacketCodecs.VAR_INT, ClickPayload::slot,
        PacketCodecs.VAR_INT, ClickPayload::button,
        ClickPayload::new
    );

    public boolean isClose() {
        return this.button == -1;
    }

    public boolean isRightClick() {
        return this.button == 1;
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
