package raidqueue.ui;

/**
 * How the client should arrange a menu's CONTENT slots. Sent as an ordinal over the
 * wire, so existing values must keep their position - append new layouts at the end.
 */
public enum Layout {
    /** A handful of big cards side by side (e.g. picking a raid tier). */
    CARDS,
    /** Icon tiles at their chest positions, nine wide, exactly like a real chest. */
    GRID,
    /** Rows with a name and one summary line each (e.g. a player queue/lobby list). */
    LIST,
    /** Text sections with their full descriptions, for content that's mostly reading. */
    PAGE
}
