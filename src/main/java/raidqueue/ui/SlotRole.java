package raidqueue.ui;

/**
 * What a {@link MenuSlot} is for. Purely a layout/rendering hint - every role still
 * behaves the same way when clicked (fires its {@link ClickAction}, if any).
 */
public enum SlotRole {
    /** The actual options a player is choosing between. */
    CONTENT,
    /** Read-only description boxes shown above the options. */
    INFO,
    /** Buttons along the bottom, e.g. page arrows or a shortcut into another menu. */
    FOOTER,
    /** The single "back to the previous menu" button, bottom-right on the custom screen. */
    BACK
}
