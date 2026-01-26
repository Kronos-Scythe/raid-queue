package raidqueue;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import raidqueue.raiddens.RaidDenLauncher;
import java.util.*;

public class RaidDenQueueManager {

    // 1★ → 5★ queues
    private static final Map<Integer, Set<UUID>> QUEUES = new HashMap<>();

    static {
        for (int i = 1; i <= 5; i++) {
            QUEUES.put(i, new LinkedHashSet<>());
        }
    }

    /**
     * Adds a player to a raid queue.
     */
    public static void join(ServerPlayerEntity player, int difficulty) {
        if (!QUEUES.containsKey(difficulty)) {
            player.sendMessage(Text.literal("§cInvalid raid difficulty."), false);
            return;
        }

        Set<UUID> queue = QUEUES.get(difficulty);
        UUID uuid = player.getUuid();

        // Already queued anywhere
        if (isQueued(uuid)) {
            player.sendMessage(Text.literal("§eYou are already in a raid queue."), false);
            return;
        }

        queue.add(uuid);

        player.sendMessage(
                Text.literal("§a✓ Joined " + difficulty + "★ raid queue (§e"
                        + queue.size() + "/4§a players ready)"),
                false
        );
        player.sendMessage(
                Text.literal("§7Wait for others or start now with §a/rqueue"),
                false
        );

        // Notify all other players in this queue
        notifyQueuePlayers(player.getServer(), difficulty,
            Text.literal("§a✓ " + player.getName().getString() + " §7is ready! (§e" + queue.size() + "/4§7 players in queue)")
        );

        // If queue has players, remind them they can start
        if (queue.size() > 1) {
            notifyQueuePlayers(player.getServer(), difficulty,
                Text.literal("§eℹ Ready to start? Use §a/rqueue §eand click the green wool!")
            );
        }

        // Refresh all open queue screens for this difficulty
        raidqueue.gui.QueueViewScreen.refreshQueueScreens(player.getServer(), difficulty);
    }


    /**
     * Removes a player from all queues.
     */
    public static void leave(ServerPlayerEntity player) {
        onPlayerDisconnect(player);

        player.sendMessage(Text.literal("§eYou left the raid queue."), false);
    }

    /**
     * Removes a player when they disconnect.
     */
    public static void onPlayerDisconnect(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        for (Set<UUID> queue : QUEUES.values()) {
            queue.remove(uuid);
        }
    }

    /**
     * Returns true if player is queued anywhere.
     */
    public static boolean isQueued(UUID uuid) {
        return QUEUES.values().stream().anyMatch(q -> q.contains(uuid));
    }

    /**
     * Gets queued players for a difficulty.
     */
    public static List<ServerPlayerEntity> getQueuedPlayers(
            MinecraftServer server,
            int difficulty
    ) {
        Set<UUID> uuids = QUEUES.get(difficulty);
        if (uuids == null) return List.of();

        List<ServerPlayerEntity> players = new ArrayList<>();
        for (UUID uuid : uuids) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player != null) {
                players.add(player);
            }
        }
        return players;
    }

    /**
     * Clears a difficulty queue (after raid starts).
     */
    public static void clear(int difficulty) {
        Set<UUID> queue = QUEUES.get(difficulty);
        if (queue != null) {
            queue.clear();
        }
    }

    /**
     * Sends a message to all players in a specific queue.
     */
    private static void notifyQueuePlayers(MinecraftServer server, int difficulty, Text message) {
        List<ServerPlayerEntity> players = getQueuedPlayers(server, difficulty);
        for (ServerPlayerEntity player : players) {
            player.sendMessage(message, false);
        }
    }
}
