package raidqueue.gui;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import raidqueue.RaidDenQueueManager;
import raidqueue.config.RaidQueueConfig;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class QueueViewScreen {

    // Track which players have the queue screen open and for which difficulty
    private static final Map<UUID, Integer> OPEN_QUEUE_SCREENS = new ConcurrentHashMap<>();

    /**
     * Called when a player joins a queue - refreshes all open queue screens for that difficulty
     */
    public static void refreshQueueScreens(MinecraftServer server, int difficulty) {
        OPEN_QUEUE_SCREENS.forEach((playerUuid, openDifficulty) -> {
            if (openDifficulty == difficulty) {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerUuid);
                if (player != null) {
                    // Re-open the screen to refresh it
                    server.execute(() -> open(player, difficulty));
                }
            }
        });
    }

    /**
     * Closes the queue screen tracking for a player
     */
    public static void closeQueueScreen(UUID playerUuid) {
        OPEN_QUEUE_SCREENS.remove(playerUuid);
    }

    /**
     * Opens a GUI showing all players in the queue for a specific difficulty.
     * Shows player heads in the middle and confirm/back buttons.
     */
    public static void open(ServerPlayerEntity player, int difficulty) {
        // Register that this player has the queue screen open
        OPEN_QUEUE_SCREENS.put(player.getUuid(), difficulty);

        List<ServerPlayerEntity> queuedPlayers = RaidDenQueueManager.getQueuedPlayers(
                player.getServer(),
                difficulty
        );
        int maxPartySize = RaidQueueConfig.get().maxPartySize;

        // Use 3 rows (27 slots) to have room for player heads and buttons
        SimpleInventory inv = new SimpleInventory(27);

        // Fill with glass panes for decoration
        ItemStack glassPane = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        glassPane.set(DataComponentTypes.CUSTOM_NAME, Text.literal(" "));
        for (int i = 0; i < 27; i++) {
            inv.setStack(i, glassPane.copy());
        }

        // Display ALL queued player heads in the middle row, centred for the party size
        int headStartSlot = 10;
        boolean currentPlayerInQueue = false;

        // First, show all players already in the queue
        for (int i = 0; i < Math.min(queuedPlayers.size(), maxPartySize); i++) {
            ServerPlayerEntity queuedPlayer = queuedPlayers.get(i);
            if (queuedPlayer.getUuid().equals(player.getUuid())) {
                currentPlayerInQueue = true;
            }
            ItemStack head = createPlayerHead(queuedPlayer);
            inv.setStack(headStartSlot + i, head);
        }

        // If current player is not queued yet, show their head with a "Join" indicator
        if (!currentPlayerInQueue && queuedPlayers.size() < maxPartySize) {
            ItemStack yourHead = createPlayerHead(player);
            yourHead.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§e⭐ You §7(Click green to join)"));
            inv.setStack(headStartSlot + queuedPlayers.size(), yourHead);
        }

        // Queue info in top center
        ItemStack info = new ItemStack(Registries.ITEM.get(new Identifier("cobblemon", "ultra_ball")));
        String status = queuedPlayers.isEmpty() ? "§7Empty" : "§a" + queuedPlayers.size() + " Ready";
        info.set(DataComponentTypes.CUSTOM_NAME,
            Text.literal("§e" + difficulty + "★ Raid Queue")
        );
        info.set(DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(
            List.of(
                Text.literal("§7Players Ready: " + status),
                Text.literal("§7Maximum: §e" + maxPartySize + " players"),
                Text.literal(""),
                Text.literal("§aClick green wool to start!")
            )
        ));
        inv.setStack(4, info);

        // Confirm button (green wool) - bottom row
        ItemStack confirm = new ItemStack(Items.LIME_WOOL);
        boolean isQueued = RaidDenQueueManager.isQueued(player.getUuid());

        if (isQueued) {
            // Player is in queue - button starts the raid
            confirm.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§a§l✔ START RAID NOW!"));
            confirm.set(DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(
                List.of(
                    Text.literal("§7Click to begin the raid"),
                    Text.literal("§7with all queued players"),
                    Text.literal(""),
                    Text.literal("§a" + queuedPlayers.size() + " player(s) ready!")
                )
            ));
        } else {
            // Player not in queue - button joins the queue
            confirm.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§e§l⭐ JOIN QUEUE"));
            confirm.set(DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(
                List.of(
                    Text.literal("§7Click to join this queue"),
                    Text.literal("§7Wait for others or start"),
                    Text.literal(""),
                    queuedPlayers.isEmpty()
                        ? Text.literal("§7Be the first to join!")
                        : Text.literal("§e" + queuedPlayers.size() + " player(s) waiting")
                )
            ));
        }
        inv.setStack(21, confirm);

        // Back button (red wool) - bottom row right side
        ItemStack back = new ItemStack(Items.RED_WOOL);
        if (isQueued) {
            back.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§c✖ Leave Queue & Back"));
            back.set(DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(
                List.of(
                    Text.literal("§7You will leave this queue"),
                    Text.literal("§7and return to main menu")
                )
            ));
        } else {
            back.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§c✖ Back to Menu"));
        }
        inv.setStack(23, back);

        player.openHandledScreen(new NamedScreenHandlerFactory() {
            @Override
            public Text getDisplayName() {
                return Text.literal("§e" + difficulty + "★ Raid Queue");
            }

            @Override
            public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity playerEntity) {
                return new GenericContainerScreenHandler(
                        ScreenHandlerType.GENERIC_9X3,
                        syncId,
                        playerInventory,
                        inv,
                        3
                ) {
                    @Override
                    public boolean canUse(PlayerEntity player) {
                        return true;
                    }

                    @Override
                    public void onClosed(PlayerEntity player) {
                        super.onClosed(player);
                        // Remove from tracking when screen is closed
                        closeQueueScreen(player.getUuid());
                    }

                    @Override
                    public void onSlotClick(int slot, int button, SlotActionType actionType, PlayerEntity playerEntity) {
                        if (!(playerEntity instanceof ServerPlayerEntity serverPlayer)) return;
                        if (actionType != SlotActionType.PICKUP) return;

                        // Prevent item pickup
                        serverPlayer.playerScreenHandler.setCursorStack(ItemStack.EMPTY);

                        // Green Wool - Join queue OR start raid if already in queue
                        if (slot == 21) {
                            // Check if player is already in the queue
                            if (!RaidDenQueueManager.isQueued(serverPlayer.getUuid())) {
                                // Not in queue - add them and keep screen open
                                RaidDenQueueManager.join(serverPlayer, difficulty);

                                // Reopen the screen to show updated state
                                serverPlayer.closeHandledScreen();
                                MinecraftServer server = serverPlayer.getServer();
                                if (server != null) {
                                    server.execute(() -> open(serverPlayer, difficulty));
                                }
                                return;
                            } else {
                                // Already in queue - start the raid
                                serverPlayer.closeHandledScreen();

                                // Get all players currently in this queue
                                List<ServerPlayerEntity> queuedPlayers = RaidDenQueueManager.getQueuedPlayers(
                                    serverPlayer.getServer(),
                                    difficulty
                                );

                                // Validate we have players
                                if (queuedPlayers.isEmpty()) {
                                    serverPlayer.sendMessage(
                                        Text.literal("§c✗ No players in queue! Something went wrong."),
                                        false
                                    );
                                    return;
                                }

                                // Start the raid with whoever is in the queue
                                serverPlayer.sendMessage(
                                    Text.literal("§a§l✓ Starting raid with " + queuedPlayers.size() + " player(s)!"),
                                    false
                                );

                                // Notify all players in queue
                                for (ServerPlayerEntity queuedPlayer : queuedPlayers) {
                                    if (!queuedPlayer.equals(serverPlayer)) {
                                        queuedPlayer.sendMessage(
                                            Text.literal("§a§l✓ " + serverPlayer.getName().getString() + " started the raid!"),
                                            false
                                        );
                                    }
                                }

                                // Launch the raid with the selected difficulty
                                raidqueue.raiddens.RaidDenLauncher.tryLaunch(
                                    serverPlayer.getServerWorld(),
                                    queuedPlayers,
                                    difficulty  // Raid tier (1-7 stars, gated by RaidTierSupport)
                                );

                                // Clear the queue after starting
                                RaidDenQueueManager.clear(difficulty);
                                return;
                            }
                        }

                        // Back - Leave queue if in one, then return to main menu
                        if (slot == 23) {
                            // If player is in this queue, remove them
                            if (RaidDenQueueManager.isQueued(serverPlayer.getUuid())) {
                                RaidDenQueueManager.leave(serverPlayer);
                                serverPlayer.sendMessage(
                                    Text.literal("§e✓ Left " + difficulty + "★ raid queue"),
                                    false
                                );
                            }

                            serverPlayer.closeHandledScreen();
                            // Re-open the main queue selection menu
                            MinecraftServer server = player.getServer();
                            if (server != null) {
                                server.execute(() ->
                                    raidqueue.command.RaidDenQueueCommand.openQueue(serverPlayer.getCommandSource())
                                );
                            }
                        }

                        // Block all other interactions
                    }
                };
            }
        });
    }

    /**
     * Creates a player head item with the player's skin.
     */
    private static ItemStack createPlayerHead(ServerPlayerEntity player) {
        ItemStack head = new ItemStack(Items.PLAYER_HEAD);

        // Set the player's profile to show their skin
        head.set(DataComponentTypes.PROFILE, new ProfileComponent(player.getGameProfile()));

        // Set the display name
        head.set(DataComponentTypes.CUSTOM_NAME,
            Text.literal("§b" + player.getName().getString())
        );

        return head;
    }
}
