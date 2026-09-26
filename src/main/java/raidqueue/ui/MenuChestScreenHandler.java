package raidqueue.ui;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * The vanilla-chest rendering of a {@link Menu}, used for players whose client doesn't
 * speak the raid-den-queue view protocol. A distinct (named, not anonymous) class so
 * {@code instanceof} can identify "a menu is open as a chest right now" if ever needed.
 */
final class MenuChestScreenHandler extends GenericContainerScreenHandler {
    private final Menu menu;

    MenuChestScreenHandler(ScreenHandlerType<?> type, int syncId, PlayerInventory playerInventory, Inventory inventory, int rows, Menu menu) {
        super(type, syncId, playerInventory, inventory, rows);
        this.menu = menu;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return true;
    }

    @Override
    public void onSlotClick(int slot, int button, SlotActionType actionType, PlayerEntity player) {
        // Every slot here is decorative/display-only - never let vanilla actually move items.
        this.setCursorStack(ItemStack.EMPTY);

        if (!(player instanceof ServerPlayerEntity serverPlayer)) return;
        if (actionType != SlotActionType.PICKUP || slot < 0) return;

        ViewManager.handleChestClick(serverPlayer, slot, button == 1);
    }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        if (player instanceof ServerPlayerEntity serverPlayer) {
            ViewManager.clear(serverPlayer);
        }
    }
}
