package raidqueue.gui;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import raidqueue.RaidDenQueueManager;
import raidqueue.command.RaidDenQueueCommand;
import raidqueue.config.RaidQueueConfig;
import raidqueue.raiddens.RaidDenLauncher;
import raidqueue.ui.Layout;
import raidqueue.ui.Menu;
import raidqueue.ui.PartyEntry;
import raidqueue.ui.ProgressInfo;
import raidqueue.ui.StatLine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The per-tier queue lobby: shows a party panel of who's currently queued, plus a
 * single big action card that either joins the queue or - once you're already in it -
 * launches the raid immediately with whoever's waiting.
 */
public final class QueueLobbyMenu {
    private QueueLobbyMenu() {}

    // Who currently has a given tier's lobby open, purely so it can be refreshed
    // (re-sent) whenever someone else joins that queue.
    private static final Map<Integer, Set<UUID>> VIEWERS = new ConcurrentHashMap<>();

    public static void open(ServerPlayerEntity player, int tier) {
        build(player, tier).open(player);
        VIEWERS.computeIfAbsent(tier, t -> ConcurrentHashMap.newKeySet()).add(player.getUuid());
    }

    /** Called by RaidDenQueueManager whenever someone (re)joins a queue, to live-update anyone watching it. */
    public static void refreshViewers(MinecraftServer server, int tier) {
        Set<UUID> viewers = VIEWERS.get(tier);
        if (viewers == null || viewers.isEmpty()) return;
        for (UUID uuid : List.copyOf(viewers)) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player != null) open(player, tier);
        }
    }

    private static Menu build(ServerPlayerEntity player, int tier) {
        List<ServerPlayerEntity> queuedPlayers = RaidDenQueueManager.getQueuedPlayers(player.getServer(), tier);
        int maxPartySize = RaidQueueConfig.get().maxPartySize;
        boolean isQueued = RaidDenQueueManager.isQueued(player.getUuid());

        List<PartyEntry> party = new ArrayList<>();
        for (int i = 0; i < queuedPlayers.size(); i++) {
            ServerPlayerEntity queued = queuedPlayers.get(i);
            Text status = i == 0 ? Text.literal("§aHost") : Text.literal("§7Ready");
            party.add(new PartyEntry(playerHead(queued), Text.literal(queued.getName().getString()), status, Text.empty()));
        }

        ItemStack action = isQueued ? startRaidCard(queuedPlayers.size()) : joinQueueCard(queuedPlayers.size());

        return Menu.create(new Identifier("raid-den-queue", "lobby_" + tier))
            .title(Text.literal(tier + "★ Raid Queue"))
            .badge(Text.literal(queuedPlayers.size() + "/" + maxPartySize))
            .background(new Identifier("minecraft", "deepslate"))
            .layout(Layout.CARDS)
            .party(party)
            .stats(List.of(
                new StatLine(Text.literal("Tier"), Text.literal(tier + "★")),
                new StatLine(Text.literal("Party"), Text.literal(queuedPlayers.size() + "/" + maxPartySize))
            ))
            .progress(new ProgressInfo(Text.literal("Party Ready"), queuedPlayers.size(), maxPartySize))
            .option(13, action, (serverPlayer, rightClick) -> onAction(serverPlayer, tier))
            .back(26, "§c✖ Back", (serverPlayer, rightClick) -> onBack(serverPlayer, tier))
            .onClose(() -> untrack(player, tier));
    }

    private static ItemStack startRaidCard(int queuedCount) {
        ItemStack icon = new ItemStack(Items.LIME_WOOL);
        icon.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§a§l✔ START RAID NOW"));
        icon.set(DataComponentTypes.LORE, new LoreComponent(List.of(
            Text.literal("§7Click to begin the raid"),
            Text.literal("§7with everyone currently queued."),
            Text.literal(""),
            Text.literal("§a" + queuedCount + " player(s) ready!")
        )));
        return icon;
    }

    private static ItemStack joinQueueCard(int queuedCount) {
        ItemStack icon = new ItemStack(Items.EMERALD);
        icon.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§e§l⭐ JOIN QUEUE"));
        icon.set(DataComponentTypes.LORE, new LoreComponent(
            queuedCount == 0
                ? List.of(Text.literal("§7Click to join this queue"), Text.literal("§7Be the first to join!"))
                : List.of(Text.literal("§7Click to join this queue"), Text.literal("§e" + queuedCount + " player(s) waiting"))
        ));
        return icon;
    }

    private static void onAction(ServerPlayerEntity player, int tier) {
        if (!RaidDenQueueManager.isQueued(player.getUuid())) {
            // join() calls refreshViewers() internally, which re-sends this player's own
            // screen along with everyone else already watching this queue.
            RaidDenQueueManager.join(player, tier);
            return;
        }

        List<ServerPlayerEntity> queuedPlayers = RaidDenQueueManager.getQueuedPlayers(player.getServer(), tier);
        if (queuedPlayers.isEmpty()) {
            player.sendMessage(Text.literal("§c✗ No players in queue! Something went wrong."), false);
            return;
        }

        player.sendMessage(Text.literal("§a§l✓ Starting raid with " + queuedPlayers.size() + " player(s)!"), false);
        for (ServerPlayerEntity queuedPlayer : queuedPlayers) {
            if (!queuedPlayer.equals(player)) {
                queuedPlayer.sendMessage(Text.literal("§a§l✓ " + player.getName().getString() + " started the raid!"), false);
            }
            // They're about to be teleported into the raid - drop the now-stale lobby screen.
            Menu.close(queuedPlayer);
            untrack(queuedPlayer, tier);
        }

        RaidDenLauncher.tryLaunch(player.getServerWorld(), queuedPlayers, tier);
        RaidDenQueueManager.clear(tier);
    }

    private static void onBack(ServerPlayerEntity player, int tier) {
        if (RaidDenQueueManager.isQueued(player.getUuid())) {
            RaidDenQueueManager.leave(player);
            player.sendMessage(Text.literal("§e✓ Left " + tier + "★ raid queue"), false);
        }
        RaidDenQueueCommand.openQueue(player.getCommandSource());
    }

    private static void untrack(ServerPlayerEntity player, int tier) {
        Set<UUID> viewers = VIEWERS.get(tier);
        if (viewers != null) viewers.remove(player.getUuid());
    }

    private static ItemStack playerHead(ServerPlayerEntity player) {
        ItemStack head = new ItemStack(Items.PLAYER_HEAD);
        head.set(DataComponentTypes.PROFILE, new ProfileComponent(player.getGameProfile()));
        head.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§b" + player.getName().getString()));
        return head;
    }
}
