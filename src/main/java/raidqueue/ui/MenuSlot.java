package raidqueue.ui;

import net.minecraft.item.ItemStack;

/**
 * One option in a {@link Menu}, before it's split into a {@link raidqueue.network.View}
 * (for mod clients) or vanilla chest slots (for everyone else). The item's custom name
 * and lore double as the option's label and description on both renderers.
 */
final class MenuSlot {
    final int slot;
    final SlotRole role;
    final ItemStack display;
    final ClickAction action;
    final boolean chestOnly;

    MenuSlot(int slot, SlotRole role, ItemStack display, ClickAction action, boolean chestOnly) {
        this.slot = slot;
        this.role = role;
        this.display = display;
        this.action = action;
        this.chestOnly = chestOnly;
    }
}
