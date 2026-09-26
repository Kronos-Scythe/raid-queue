package raidqueue;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import raidqueue.command.RaidDenQueueCommand;
import raidqueue.command.RaidTeleportBackCommand;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import raidqueue.config.RaidQueueConfig;
import raidqueue.network.ViewNetworking;
import raidqueue.raiddens.RaidDenLauncher;
import raidqueue.ui.Menu;
import raidqueue.ui.ViewManager;

public class RaidDenQueue implements ModInitializer {
    public static final String MOD_ID = "raid-den-queue";

    // This logger is used to write text to the console and the log file.
    // It is considered best practice to use your mod id as the logger's name.
    // That way, it's clear which mod wrote info, warnings, and errors.
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        RaidQueueConfig.get();

        ViewNetworking.init();

        RaidDenQueueCommand.register();
        RaidTeleportBackCommand.register();

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            RaidDenQueueManager.onPlayerDisconnect(handler.player);
            ViewManager.clear(handler.player);
        });
        ServerTickEvents.END_SERVER_TICK.register(RaidDenLauncher::serverTick);
        ServerTickEvents.END_SERVER_TICK.register(server -> Menu.serverTick());
    }
}