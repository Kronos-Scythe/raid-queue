package raidqueue.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import raidqueue.ui.ViewManager;

/**
 * Registers the menu system's packets. Payload types are registered here, in the
 * common initializer, so both physical sides agree on the id/codec; the actual
 * client-side packet handlers live in raidqueue.ui.ClientViewNetworking (client-only
 * code can't be touched from here without crashing a dedicated server).
 */
public final class ViewNetworking {
    private ViewNetworking() {}

    public static void init() {
        PayloadTypeRegistry.playS2C().register(OpenViewPayload.ID, OpenViewPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(CloseViewPayload.ID, CloseViewPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ClickPayload.ID, ClickPayload.CODEC);

        // Fabric's networking API already dispatches play packet handlers on the
        // server thread, so this can touch world/player state directly.
        ServerPlayNetworking.registerGlobalReceiver(ClickPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            if (payload.isClose()) {
                ViewManager.handleClose(player, payload.viewId());
            } else {
                ViewManager.handleCustomClick(player, payload.viewId(), payload.slot(), payload.isRightClick());
            }
        });
    }
}
