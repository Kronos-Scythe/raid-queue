package raidqueue.ui;

import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Runs on the server thread when a player clicks a slot, for both the custom screen
 * (via the Click packet) and the vanilla chest fallback (via the screen handler).
 */
@FunctionalInterface
public interface ClickAction {
    void onClick(ServerPlayerEntity player, boolean rightClick);
}
