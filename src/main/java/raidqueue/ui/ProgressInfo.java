package raidqueue.ui;

import net.minecraft.text.Text;

/**
 * A "tower" progress bar shown on the right side panel, e.g. how many of a raid's
 * party slots are filled. {@code max <= 0} means "no progress bar" - use
 * {@link #NONE} rather than constructing that by hand.
 */
public record ProgressInfo(Text title, int current, int max) {
    public static final ProgressInfo NONE = new ProgressInfo(Text.empty(), 0, 0);

    public boolean isPresent() {
        return this.max > 0;
    }
}
