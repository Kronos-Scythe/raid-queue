package raidqueue.command;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import raidqueue.gui.QueueLobbyMenu;
import raidqueue.raiddens.RaidTierSupport;
import raidqueue.ui.Layout;
import raidqueue.ui.Menu;

import java.util.List;

/**
 * Command handler for the raid queue system. Provides the /rqueue command, which opens
 * the tier-select menu (a big card per available raid tier).
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
        // Centre the cards within a 9-wide row, so the vanilla chest fallback still looks
        // like the old star-picker even though the custom screen ignores slot position for CARDS.
        int startSlot = ROW_START + Math.max(0, (ROW_LENGTH - tiers.size()) / 2);

        Menu menu = Menu.create(new Identifier("raid-den-queue", "tier_select"))
            .title(Text.literal("Raid Den Queue"))
            .badge(Text.literal("Up to " + tiers.getLast() + "★"))
            .background(new Identifier("minecraft", "deepslate"))
            .layout(Layout.CARDS);

        for (int i = 0; i < tiers.size(); i++) {
            int tier = tiers.get(i);
            menu.option(startSlot + i, star(tier), (serverPlayer, rightClick) -> QueueLobbyMenu.open(serverPlayer, tier));
        }

        menu.open(player);
        return 1;
    }

    private static ItemStack star(int level) {
        // Using ultra ball for visual representation, but conceptually represents nether star raid tiers
        ItemStack stack = new ItemStack(Registries.ITEM.get(new Identifier("cobblemon", "ultra_ball")));
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§e" + level + "★ Raid"));
        stack.set(DataComponentTypes.LORE, new LoreComponent(List.of(
            Text.literal("§7Queue for a " + level + "★ raid")
        )));
        return stack;
    }
}
