package raidqueue.ui;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import raidqueue.network.CloseViewPayload;
import raidqueue.network.OpenViewPayload;

/**
 * Client-side handlers for the menu view protocol. Registered from
 * {@code RaidDenQueueClient.onInitializeClient()} - this must never be touched from
 * common (server-safe) code, since the classes it references don't exist on a
 * dedicated server.
 */
public final class ClientViewNetworking {
    private ClientViewNetworking() {}

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(OpenViewPayload.ID, (payload, context) -> {
            MinecraftClient client = context.client();
            client.execute(() -> {
                // Tell the outgoing screen (if any) not to report itself as closed - it's
                // being replaced, not dismissed by the player.
                if (client.currentScreen instanceof MenuScreen current) {
                    current.markReplaced();
                }
                client.setScreen(new MenuScreen(payload.view()));
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(CloseViewPayload.ID, (payload, context) -> {
            MinecraftClient client = context.client();
            client.execute(() -> {
                if (client.currentScreen instanceof MenuScreen current) {
                    current.markReplaced();
                    client.setScreen(null);
                }
            });
        });
    }
}
