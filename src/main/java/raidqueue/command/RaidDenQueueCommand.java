package raidqueue.command;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.NamedScreenHandlerFactory;
import raidqueue.RaidDenQueueManager;
import raidqueue.gui.QueueViewScreen;

import java.util.List;

/**
 * Command handler for the raid queue system.
 * Provides the /rqueue command for accessing raid matchmaking.
 */
public class RaidDenQueueCommand {

    /**
     * Registers the /rqueue command.
     */
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(
                        CommandManager.literal("rqueue")
                                .executes(ctx -> openQueue(ctx.getSource()))
                )
        );
    }

    public static int openQueue(ServerCommandSource source) {
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("Only players can use this command."));
            return 0;
        }

        // Create a 3-row chest (27 slots)
        SimpleInventory inv = new SimpleInventory(27);

        // Fill with gray glass panes for decoration
        ItemStack glassPane = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        glassPane.set(DataComponentTypes.CUSTOM_NAME, Text.literal(" "));
        for (int i = 0; i < 27; i++) {
            inv.setStack(i, glassPane.copy());
        }

        // Place the 5 difficulty stars in the center row (slots 11-15)
        // Middle row is slots 9-17, so we center at 11-15
        inv.setStack(11, star(1));
        inv.setStack(12, star(2));
        inv.setStack(13, star(3));
        inv.setStack(14, star(4));
        inv.setStack(15, star(5));

        // Add a title item in the top center
        ItemStack title = new ItemStack(Items.NETHER_STAR);
        title.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§e§lSelect Raid Difficulty"));
        title.set(DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(
            List.of(
                Text.literal("§7Click a star to join that raid tier"),
                Text.literal("§71★ = Easy | 5★ = Hard")
            )
        ));
        inv.setStack(4, title);

        player.openHandledScreen(new NamedScreenHandlerFactory() {

            @Override
            public Text getDisplayName() {
                return Text.literal("§e§lRaid Den Queue");
            }

            @Override
            public ScreenHandler createMenu(
                    int syncId,
                    PlayerInventory playerInventory,
                    PlayerEntity playerEntity
            ) {
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
                    public void onSlotClick(
                            int slot,
                            int button,
                            SlotActionType actionType,
                            PlayerEntity player
                    ) {
                        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
                            return;
                        }

                        // Only react to left-clicks on the star slots (11-15)
                        if (actionType == SlotActionType.PICKUP && slot >= 11 && slot <= 15) {
                            int difficulty = slot - 10; // 11->1, 12->2, ..., 15->5

                            // Prevent item pickup
                            serverPlayer.playerScreenHandler.setCursorStack(ItemStack.EMPTY);

                            serverPlayer.closeHandledScreen();

                            // Open the queue view screen showing who's in queue
                            QueueViewScreen.open(serverPlayer, difficulty);
                            return;
                        }

                        // Block all other slot interactions
                        serverPlayer.playerScreenHandler.setCursorStack(ItemStack.EMPTY);
                    }
                };
            }
        });


        return 1;
    }

    private static ItemStack star(int level) {
        ItemStack stack = new ItemStack(Items.NETHER_STAR);
        stack.set(
                DataComponentTypes.CUSTOM_NAME,
                Text.literal("§e" + level + "★ Raid")
        );
        return stack;
    }

    private static void handleQueueJoin(ServerPlayerEntity player, int difficulty) {
        player.sendMessage(
                Text.literal("§aJoined " + difficulty + "★ Raid Queue"),
                false
        );

        // TODO:
        // RaidDenQueueManager.join(player, difficulty);
        // Hook into cobblemon-raiddens here
    }
    private static void openConfirmScreen(ServerPlayerEntity player, int difficulty) {

        SimpleInventory inv = new SimpleInventory(9);

        // Player head
        ItemStack head = new ItemStack(Items.PLAYER_HEAD);
        head.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§e" + player.getName().getString()));
        inv.setStack(3, head);

        // Confirm button (green wool)
        ItemStack confirm = new ItemStack(Items.GREEN_WOOL);
        confirm.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§aConfirm"));
        inv.setStack(5, confirm);

        // Back button (red wool)
        ItemStack back = new ItemStack(Items.RED_WOOL);
        back.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§cBack"));
        inv.setStack(7, back);

        player.openHandledScreen(new NamedScreenHandlerFactory() {

            @Override
            public Text getDisplayName() {
                return Text.literal("Confirm Raid Queue");
            }

            @Override
            public ScreenHandler createMenu(
                    int syncId,
                    PlayerInventory playerInventory,
                    PlayerEntity playerEntity
            ) {
                return new GenericContainerScreenHandler(
                        ScreenHandlerType.GENERIC_9X1,
                        syncId,
                        playerInventory,
                        inv,
                        1
                ) {

                    @Override
                    public boolean canUse(PlayerEntity player) {
                        return true;
                    }

                    @Override
                    public void onSlotClick(
                            int slot,
                            int button,
                            SlotActionType actionType,
                            PlayerEntity playerEntity
                    ) {
                        if (!(playerEntity instanceof ServerPlayerEntity serverPlayer)) return;

                        if (actionType != SlotActionType.PICKUP) return;

                        // Confirm
                        if (slot == 5) {
                            serverPlayer.playerScreenHandler.setCursorStack(ItemStack.EMPTY);
                            serverPlayer.closeHandledScreen();
                            RaidDenQueueManager.join(serverPlayer, difficulty);
                            return;
                        }

                        // Back
                        if (slot == 7) {
                            serverPlayer.playerScreenHandler.setCursorStack(ItemStack.EMPTY);
                            serverPlayer.closeHandledScreen();
                            openQueue(serverPlayer.getCommandSource());
                            return;
                        }

                        // Block item movement
                        return;
                    }
                };
            }
        });
    }

}
