package raidqueue.ui;

import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

/**
 * One row of the optional left-hand side panel. Named after its original use (showing
 * a Pokemon party's icon/level/HP/held item) but generic enough for anything roster-like
 * - the raid queue uses it to list queued players (icon = head, line1/line2 = status).
 */
public record PartyEntry(ItemStack icon, Text name, Text line1, Text line2) {
    public static PartyEntry of(ItemStack icon, Text name, Text line1) {
        return new PartyEntry(icon, name, line1, Text.empty());
    }
}
