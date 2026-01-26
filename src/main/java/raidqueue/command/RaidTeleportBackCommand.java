package raidqueue.command;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import raidqueue.raiddens.RaidDenLauncher;

public class RaidTeleportBackCommand {

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(
                        CommandManager.literal("rqueue")
                                .then(CommandManager.literal("back")
                                        .executes(ctx -> teleportBack(ctx.getSource()))
                                )
                )
        );
    }

    private static int teleportBack(ServerCommandSource source) {
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("Only players can use this command."));
            return 0;
        }

        RaidDenLauncher.teleportBack(player);
        return 1;
    }
}
