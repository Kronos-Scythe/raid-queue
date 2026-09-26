package raidqueue.ui;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tracks which {@link Menu} (if any) each player currently has open, across both the
 * custom screen and the vanilla chest fallback, so click routing and "is a menu open
 * right now" checks behave the same regardless of which renderer a given client uses.
 *
 * <p>Also fires each menu's onClose callback exactly once, whenever it stops being the
 * player's current menu for any reason - closed outright, or replaced by another menu.
 */
public final class ViewManager {
    private ViewManager() {}

    private record OpenMenu(Identifier viewId, Menu menu, AtomicBoolean busy, Runnable onClose) {}

    private static final Map<UUID, OpenMenu> OPEN = new ConcurrentHashMap<>();

    static void trackCustomView(ServerPlayerEntity player, Identifier viewId, Menu menu, Runnable onClose) {
        remove(player.getUuid());
        OPEN.put(player.getUuid(), new OpenMenu(viewId, menu, new AtomicBoolean(false), onClose));
    }

    static void trackChestOpen(ServerPlayerEntity player, Menu menu, Runnable onClose) {
        remove(player.getUuid());
        OPEN.put(player.getUuid(), new OpenMenu(null, menu, new AtomicBoolean(false), onClose));
    }

    /** Call when a menu closes for any reason (screen closed, chest closed, player left). */
    public static void clear(ServerPlayerEntity player) {
        remove(player.getUuid());
    }

    /** True if the player has either kind of menu screen open right now. */
    public static boolean isOpen(UUID playerId) {
        return OPEN.containsKey(playerId);
    }

    public static void handleClose(ServerPlayerEntity player, Identifier viewId) {
        OpenMenu open = OPEN.get(player.getUuid());
        if (open == null || (open.viewId != null && !open.viewId.equals(viewId))) return;
        remove(player.getUuid());
    }

    public static void handleCustomClick(ServerPlayerEntity player, Identifier viewId, int slot, boolean rightClick) {
        OpenMenu open = OPEN.get(player.getUuid());
        // Ignore clicks whose viewId isn't the player's current view (a stale/late packet
        // from a menu that has since been replaced or closed).
        if (open == null || open.viewId == null || !open.viewId.equals(viewId)) return;
        dispatch(player, open, slot, rightClick);
    }

    static void handleChestClick(ServerPlayerEntity player, int slot, boolean rightClick) {
        OpenMenu open = OPEN.get(player.getUuid());
        if (open == null) return;
        dispatch(player, open, slot, rightClick);
    }

    private static void dispatch(ServerPlayerEntity player, OpenMenu open, int slot, boolean rightClick) {
        // Ignore a second click until the first one's action has finished running.
        if (!open.busy.compareAndSet(false, true)) return;
        try {
            open.menu.click(player, slot, rightClick);
        } finally {
            open.busy.set(false);
        }
    }

    private static void remove(UUID playerId) {
        OpenMenu removed = OPEN.remove(playerId);
        if (removed != null && removed.onClose() != null) removed.onClose().run();
    }
}
