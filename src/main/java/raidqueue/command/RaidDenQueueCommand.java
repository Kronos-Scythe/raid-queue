package raidqueue.command;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
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
import raidqueue.gui.QueueViewScreen;
import raidqueue.raiddens.RaidTierSupport;

import java.util.List;

/**
 * Command handler for the raid queue system.
 * Provides the /rqueue command for accessing raid matchmaking.
 */
public class RaidDenQueueCommand {

    private static final int ROW_START = 9;
    private static final int ROW_LENGTH = 9;

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

        List<Integer> tiers = RaidTierSupport.availableTiers();
        // Centre the star row within the middle inventory row, however many tiers are on offer.
        int startSlot = ROW_START + Math.max(0, (ROW_LENGTH - tiers.size()) / 2);

        // Create a 3-row chest (27 slots)
        SimpleInventory inv = new SimpleInventory(27);

        // Fill with gray glass panes for decoration
        ItemStack glassPane = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        glassPane.set(DataComponentTypes.CUSTOM_NAME, Text.literal(" "));
        for (int i = 0; i < 27; i++) {
            inv.setStack(i, glassPane.copy());
        }

        // Place a star item for each available tier, centred in the middle row.
        for (int i = 0; i < tiers.size(); i++) {
            inv.setStack(startSlot + i, star(tiers.get(i)));
        }

        // Add a title item in the top center
        ItemStack title = new ItemStack(Items.NETHER_STAR);
        title.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§e§lRaid Den Queue"));
        title.set(DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(
            List.of(
                Text.literal("§7Select a difficulty tier below"),
                Text.literal("§7to join the queue"),
                Text.literal(""),
                Text.literal("§e1★ §7= Easy §8| §e" + tiers.getLast() + "★ §7= Hard")
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

                        // Only react to left-clicks on the star slots
                        int tierIndex = slot - startSlot;
                        if (actionType == SlotActionType.PICKUP && tierIndex >= 0 && tierIndex < tiers.size()) {
                            int difficulty = tiers.get(tierIndex);

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
        // Using ultra ball for visual representation, but conceptually represents nether star raid tiers
        ItemStack stack = new ItemStack(Registries.ITEM.get(new Identifier("cobblemon", "ultra_ball")));
        stack.set(
                DataComponentTypes.CUSTOM_NAME,
                Text.literal("§e" + level + "★ Raid")
        );
        return stack;
    }

}
