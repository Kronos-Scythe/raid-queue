package raidqueue.ui;

import com.cobblemon.mod.common.battles.BattleRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import raidqueue.network.CloseViewPayload;
import raidqueue.network.OpenViewPayload;
import raidqueue.network.View;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Describes one screen: a server builds a Menu, calls {@link #open}, and it renders as
 * a custom view for clients that have this mod, or falls back to an ordinary chest for
 * everyone else - same options, same click behaviour either way.
 *
 * <p>Each option is a slot holding an {@link ItemStack} (its custom name/lore are the
 * label/description) plus an optional {@link ClickAction}. Slots are grouped into
 * {@link SlotRole}s that only affect layout, and the whole thing picks one
 * {@link Layout} for how CONTENT slots are arranged on the custom screen.
 */
public final class Menu {
    private static final AtomicLong NEXT_VIEW_SEQ = new AtomicLong();
    private static final Identifier DEFAULT_BACKGROUND = new Identifier("minecraft", "gray_concrete");

    private final Identifier id;
    private final Map<Integer, MenuSlot> slots = new LinkedHashMap<>();
    private Text title = Text.empty();
    private Text customTitle;
    private Text badge = Text.empty();
    private Identifier background = DEFAULT_BACKGROUND;
    private Layout layout = Layout.GRID;
    private List<PartyEntry> party = List.of();
    private List<StatLine> stats = List.of();
    private ProgressInfo progress = ProgressInfo.NONE;
    private Runnable onClose;

    private Menu(Identifier id) {
        this.id = id;
    }

    /** {@code id} only needs to be unique enough for logging/debugging - the wire protocol mints its own view ids. */
    public static Menu create(Identifier id) {
        return new Menu(id);
    }

    public Identifier id() {
        return this.id;
    }

    public Menu title(Text title) {
        this.title = title;
        return this;
    }

    /** Overrides the header shown on the custom screen only; the chest title still uses {@link #title}. */
    public Menu screenTitle(Text title) {
        this.customTitle = title;
        return this;
    }

    public Menu badge(Text badge) {
        this.badge = badge;
        return this;
    }

    public Menu background(Identifier blockId) {
        this.background = blockId;
        return this;
    }

    public Menu layout(Layout layout) {
        this.layout = layout;
        return this;
    }

    /**
     * Runs once this menu stops being the player's current one, for any reason - closed
     * outright, or replaced by another menu (including this same menu being re-sent by
     * {@link #open}, e.g. to refresh it). Use it to stop tracking "is still watching this
     * screen" state; it is not a substitute for {@link ClickAction} (it never fires from
     * a player's own click).
     */
    public Menu onClose(Runnable onClose) {
        this.onClose = onClose;
        return this;
    }

    public Menu party(List<PartyEntry> entries) {
        this.party = List.copyOf(entries);
        return this;
    }

    public Menu stats(List<StatLine> lines) {
        this.stats = List.copyOf(lines);
        return this;
    }

    public Menu progress(ProgressInfo info) {
        this.progress = info;
        return this;
    }

    /** A CONTENT option. */
    public Menu option(int slot, ItemStack display, ClickAction action) {
        this.slots.put(slot, new MenuSlot(slot, SlotRole.CONTENT, display, action, false));
        return this;
    }

    /** A read-only INFO box; has no click action. */
    public Menu info(int slot, ItemStack display) {
        this.slots.put(slot, new MenuSlot(slot, SlotRole.INFO, display, null, false));
        return this;
    }

    /** A FOOTER button, e.g. a page arrow or a shortcut into another menu. */
    public Menu footer(int slot, ItemStack icon, ClickAction action) {
        this.slots.put(slot, new MenuSlot(slot, SlotRole.FOOTER, icon, action, false));
        return this;
    }

    /** The single BACK button. */
    public Menu back(int slot, String label, ClickAction action) {
        ItemStack icon = new ItemStack(Items.ARROW);
        icon.set(DataComponentTypes.CUSTOM_NAME, Text.literal(label));
        this.slots.put(slot, new MenuSlot(slot, SlotRole.BACK, icon, action, false));
        return this;
    }

    /** Marks an already-added slot as hidden on the custom screen (chest fallback only). */
    public Menu chestOnly(int slot) {
        MenuSlot existing = this.slots.get(slot);
        if (existing != null) {
            this.slots.put(slot, new MenuSlot(existing.slot, existing.role, existing.display, existing.action, true));
        }
        return this;
    }

    // Opening any screen (custom or chest) right after a Cobblemon battle ends fails
    // while the battle is still registered - retry once a second until it clears.
    private static final Map<UUID, RetryEntry> PENDING_OPENS = new ConcurrentHashMap<>();
    private static int retryTickCounter = 0;

    private record RetryEntry(ServerPlayerEntity player, Menu menu) {}

    public void open(ServerPlayerEntity player) {
        if (BattleRegistry.getBattleByParticipatingPlayer(player) != null) {
            PENDING_OPENS.put(player.getUuid(), new RetryEntry(player, this));
            return;
        }
        reallyOpen(player);
    }

    /** Call once a second from a server tick event; retries any opens deferred by a still-active battle. */
    public static void serverTick() {
        if (PENDING_OPENS.isEmpty()) return;
        if (++retryTickCounter < 20) return;
        retryTickCounter = 0;

        for (RetryEntry entry : List.copyOf(PENDING_OPENS.values())) {
            if (BattleRegistry.getBattleByParticipatingPlayer(entry.player) != null) continue;
            PENDING_OPENS.remove(entry.player.getUuid());
            entry.menu.reallyOpen(entry.player);
        }
    }

    private void reallyOpen(ServerPlayerEntity player) {
        if (ServerPlayNetworking.canSend(player, OpenViewPayload.ID)) {
            openCustomScreen(player);
        } else {
            openChestFallback(player);
        }
    }

    private void openCustomScreen(ServerPlayerEntity player) {
        Identifier viewId = new Identifier("raid-den-queue", "view_" + NEXT_VIEW_SEQ.incrementAndGet());
        View view = buildView(viewId);
        ViewManager.trackCustomView(player, viewId, this, this.onClose);
        ServerPlayNetworking.send(player, new OpenViewPayload(view));
    }

    private View buildView(Identifier viewId) {
        List<View.Entry> info = new ArrayList<>();
        List<View.Entry> content = new ArrayList<>();
        List<View.Entry> footer = new ArrayList<>();
        List<View.Entry> back = new ArrayList<>();

        for (MenuSlot slot : this.slots.values()) {
            if (slot.chestOnly) continue;
            View.Entry entry = new View.Entry(slot.slot, slot.display, slot.action != null);
            switch (slot.role) {
                case INFO -> info.add(entry);
                case CONTENT -> content.add(entry);
                case FOOTER -> footer.add(entry);
                case BACK -> back.add(entry);
            }
        }

        Text headerTitle = this.customTitle != null ? this.customTitle : this.title;
        View.Header header = new View.Header(viewId, headerTitle, this.badge, this.background, this.layout.ordinal());
        View.Entries entries = new View.Entries(info, content, footer, back);
        View.SidePanels panels = new View.SidePanels(this.party, this.stats, this.progress);
        return new View(header, entries, panels);
    }

    private void openChestFallback(ServerPlayerEntity player) {
        int maxSlot = this.slots.keySet().stream().mapToInt(Integer::intValue).max().orElse(-1);
        int size = chestSizeFor(maxSlot + 1);
        int rows = size / 9;

        SimpleInventory inv = new SimpleInventory(size);
        ItemStack filler = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        filler.set(DataComponentTypes.CUSTOM_NAME, Text.literal(" "));
        for (int i = 0; i < size; i++) inv.setStack(i, filler.copy());
        // Chest fallback shows every slot, including ones marked chestOnly() - those exist
        // specifically for cases the custom screen can't (or shouldn't) represent.
        for (MenuSlot slot : this.slots.values()) {
            if (slot.slot >= 0 && slot.slot < size) inv.setStack(slot.slot, slot.display);
        }

        Menu self = this;
        ScreenHandlerType<GenericContainerScreenHandler> type = handlerTypeFor(rows);

        player.openHandledScreen(new NamedScreenHandlerFactory() {
            @Override
            public Text getDisplayName() {
                return self.title;
            }

            @Override
            public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity playerEntity) {
                MenuChestScreenHandler handler = new MenuChestScreenHandler(type, syncId, playerInventory, inv, rows, self);
                if (playerEntity instanceof ServerPlayerEntity serverPlayer) {
                    ViewManager.trackChestOpen(serverPlayer, self, self.onClose);
                }
                return handler;
            }
        });
    }

    private static int chestSizeFor(int minSlots) {
        int rows = Math.max(3, (int) Math.ceil(minSlots / 9.0));
        rows = Math.min(rows, 6);
        return rows * 9;
    }

    private static ScreenHandlerType<GenericContainerScreenHandler> handlerTypeFor(int rows) {
        return switch (rows) {
            case 4 -> ScreenHandlerType.GENERIC_9X4;
            case 5 -> ScreenHandlerType.GENERIC_9X5;
            case 6 -> ScreenHandlerType.GENERIC_9X6;
            default -> ScreenHandlerType.GENERIC_9X3;
        };
    }

    /** Invoked by {@link ViewManager} once a click (from either renderer) has been routed here. */
    void click(ServerPlayerEntity player, int slotIndex, boolean rightClick) {
        MenuSlot slot = this.slots.get(slotIndex);
        if (slot == null || slot.action == null) return;
        slot.action.onClick(player, rightClick);
    }

    /**
     * Forcibly closes whatever menu screen a player has open, e.g. because something
     * else (another queued player starting the raid, a disconnect elsewhere) made it
     * stale. Safe to call even if nothing is open.
     */
    public static void close(ServerPlayerEntity player) {
        ViewManager.clear(player);
        if (ServerPlayNetworking.canSend(player, CloseViewPayload.ID)) {
            ServerPlayNetworking.send(player, new CloseViewPayload());
        } else if (player.currentScreenHandler instanceof MenuChestScreenHandler) {
            player.closeHandledScreen();
        }
    }
}
