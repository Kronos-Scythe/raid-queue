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
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import raidqueue.RaidDenQueueManager;

import java.util.List;

public class QueueViewScreen {

    /**
     * Opens a GUI showing all players in the queue for a specific difficulty.
     * Shows player heads in the middle and confirm/back buttons.
     */
    public static void open(ServerPlayerEntity player, int difficulty) {
        List<ServerPlayerEntity> queuedPlayers = RaidDenQueueManager.getQueuedPlayers(
                player.getServer(),
                difficulty
        );

        // Use 3 rows (27 slots) to have room for player heads and buttons
        SimpleInventory inv = new SimpleInventory(27);

        // Fill with glass panes for decoration
        ItemStack glassPane = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        glassPane.set(DataComponentTypes.CUSTOM_NAME, Text.literal(" "));
        for (int i = 0; i < 27; i++) {
            inv.setStack(i, glassPane.copy());
        }

        // Display player heads in the middle row (slots 10-15)
        int headStartSlot = 10; // Start of second row, leave room for 4 players
        for (int i = 0; i < Math.min(queuedPlayers.size(), 4); i++) {
            ServerPlayerEntity queuedPlayer = queuedPlayers.get(i);
            ItemStack head = createPlayerHead(queuedPlayer);
            inv.setStack(headStartSlot + i, head);
        }

        // If current player is not queued yet, show their head with a "Join" indicator
        if (!RaidDenQueueManager.isQueued(player.getUuid())) {
            ItemStack yourHead = createPlayerHead(player);
            yourHead.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§e⭐ You §7(Click green to join)"));
            inv.setStack(headStartSlot + Math.min(queuedPlayers.size(), 3), yourHead);
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
                Text.literal("§7Maximum: §e4 players"),
                Text.literal(""),
                Text.literal("§aClick green wool to start!")
            )
        ));
        inv.setStack(4, info);

        // Confirm button (green wool) - bottom row
        ItemStack confirm = new ItemStack(Items.LIME_WOOL);
        boolean isQueued = RaidDenQueueManager.isQueued(player.getUuid());
        String confirmText = isQueued ? "§a§l✔ START RAID NOW!" : "§a§l✔ JOIN & START RAID!";
        confirm.set(DataComponentTypes.CUSTOM_NAME, Text.literal(confirmText));
        confirm.set(DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(
            List.of(
                Text.literal("§7Click to begin the raid"),
                Text.literal("§7with current players"),
                Text.literal(""),
                queuedPlayers.isEmpty()
                    ? Text.literal("§e⚠ You'll raid solo!")
                    : Text.literal("§a" + (isQueued ? queuedPlayers.size() : queuedPlayers.size() + 1) + " player(s) will participate")
            )
        ));
        inv.setStack(21, confirm);

        // Back button (red wool) - bottom row right side
        ItemStack back = new ItemStack(Items.RED_WOOL);
        back.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§c✖ Back to Menu"));
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
                    public void onSlotClick(int slot, int button, SlotActionType actionType, PlayerEntity playerEntity) {
                        if (!(playerEntity instanceof ServerPlayerEntity serverPlayer)) return;
                        if (actionType != SlotActionType.PICKUP) return;

                        // Prevent item pickup
                        serverPlayer.playerScreenHandler.setCursorStack(ItemStack.EMPTY);

                        // Confirm - Start the raid immediately with current queued players
                        if (slot == 21) {
                            serverPlayer.closeHandledScreen();

                            // Get all players currently in this queue
                            List<ServerPlayerEntity> queuedPlayers = RaidDenQueueManager.getQueuedPlayers(
                                serverPlayer.getServer(),
                                difficulty
                            );

                            // Add the current player if they're not already queued
                            if (!RaidDenQueueManager.isQueued(serverPlayer.getUuid())) {
                                RaidDenQueueManager.join(serverPlayer, difficulty);
                                // Refresh the list to include the new player
                                queuedPlayers = RaidDenQueueManager.getQueuedPlayers(
                                    serverPlayer.getServer(),
                                    difficulty
                                );
                            }

                            // Validate we have players
                            if (queuedPlayers.isEmpty()) {
                                serverPlayer.sendMessage(
                                    Text.literal("§c✗ No players in queue! Something went wrong."),
                                    false
                                );
                                return;
                            }

                            // Start the raid immediately with whoever is in the queue
                            serverPlayer.sendMessage(
                                Text.literal("§a§l✓ Starting raid with " + queuedPlayers.size() + " player(s)!"),
                                false
                            );

                            // Launch the raid with the selected difficulty
                            raidqueue.raiddens.RaidDenLauncher.tryLaunch(
                                serverPlayer.getServerWorld(),
                                queuedPlayers,
                                difficulty  // Pass the difficulty (1-5 stars)
                            );

                            // Clear the queue after starting
                            RaidDenQueueManager.clear(difficulty);
                            return;
                        }

                        // Back - Return to main queue selection
                        if (slot == 23) {
                            serverPlayer.closeHandledScreen();
                            // Re-open the main queue selection menu
                            player.getServer().execute(() ->
                                raidqueue.command.RaidDenQueueCommand.openQueue(serverPlayer.getCommandSource())
                            );
                            return;
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
