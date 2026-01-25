package raidqueue.raiddens;

import com.necro.raid.dens.common.CobblemonRaidDens;
import com.necro.raid.dens.common.blocks.block.RaidCrystalBlock;
import com.necro.raid.dens.common.blocks.entity.RaidCrystalBlockEntity;
import com.necro.raid.dens.common.data.raid.RaidBoss;
import com.necro.raid.dens.common.data.raid.RaidCycleMode;
import com.necro.raid.dens.common.data.raid.RaidTier;
import com.necro.raid.dens.common.data.raid.RaidType;
import com.necro.raid.dens.common.registry.RaidRegistry;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class RaidDenLauncher {

    private RaidDenLauncher() {}

    private static final Map<UUID, PlayerLocation> PLAYER_POSITIONS = new HashMap<>();

    private static class PlayerLocation {
        final RegistryKey<World> world;
        final double x, y, z;
        final float yaw, pitch;

        PlayerLocation(ServerPlayerEntity player) {
            this.world = player.getWorld().getRegistryKey();
            this.x = player.getX();
            this.y = player.getY();
            this.z = player.getZ();
            this.yaw = player.getYaw();
            this.pitch = player.getPitch();
        }
    }

    public static void teleportBack(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        PlayerLocation loc = PLAYER_POSITIONS.remove(uuid);

        if (loc == null) {
            player.sendMessage(Text.literal("§eNo previous location saved."), false);
            return;
        }

        MinecraftServer server = player.getServer();
        if (server == null) return;

        ServerWorld world = server.getWorld(loc.world);
        if (world == null) {
            player.sendMessage(Text.literal("§cCouldn't find your previous world!"), false);
            return;
        }

        player.teleport(world, loc.x, loc.y, loc.z, loc.yaw, loc.pitch);
        player.sendMessage(Text.literal("§a✓ Teleported back to your previous location!"), false);
    }

    /**
     * Places a raid crystal block at the player's feet and configures it with the selected tier.
     * The player then right-clicks it to start the raid (normal raid dens behavior).
     */
    public static void tryLaunch(
            ServerWorld world,
            List<ServerPlayerEntity> players,
            int difficulty
    ) {
        if (players.isEmpty()) {
            return;
        }

        if (players.size() > 4) {
            for (ServerPlayerEntity player : players) {
                player.sendMessage(Text.literal("§cToo many players! Maximum is 4."), false);
            }
            return;
        }

        ServerPlayerEntity leader = players.get(0);
        MinecraftServer server = world.getServer();

        for (ServerPlayerEntity player : players) {
            PLAYER_POSITIONS.put(player.getUuid(), new PlayerLocation(player));
        }

        Block raidCrystalBlock = Registries.BLOCK.get(new Identifier("cobblemonraiddens", "raid_crystal_block"));
        if (raidCrystalBlock == null || raidCrystalBlock == Blocks.AIR) {
            for (ServerPlayerEntity player : players) {
                player.sendMessage(Text.literal("§c✗ Raid crystal block not found!"), false);
            }
            return;
        }

        BlockPos crystalPos = new BlockPos(leader.getBlockX(), -64, leader.getBlockZ());
        world.setBlockState(crystalPos, Blocks.AIR.getDefaultState());

        RaidTier raidTier = switch (difficulty) {
            case 1 -> RaidTier.TIER_ONE;
            case 2 -> RaidTier.TIER_TWO;
            case 3 -> RaidTier.TIER_THREE;
            case 4 -> RaidTier.TIER_FOUR;
            case 5 -> RaidTier.TIER_FIVE;
            default -> RaidTier.TIER_ONE;
        };

        RaidType[] allTypes = RaidType.values();
        RaidType randomType = allTypes[world.getRandom().nextInt(allTypes.length)];

        BlockState crystalState = raidCrystalBlock.getDefaultState()
            .with(RaidCrystalBlock.ACTIVE, true)
            .with(RaidCrystalBlock.CYCLE_MODE, RaidCycleMode.CONFIG)
            .with(RaidCrystalBlock.CAN_RESET, true)
            .with(RaidCrystalBlock.RAID_TIER, raidTier)
            .with(RaidCrystalBlock.RAID_TYPE, randomType);

        world.setBlockState(crystalPos, crystalState);

        server.execute(() -> {
            try {
                var blockEntity = world.getBlockEntity(crystalPos);

                if (!(blockEntity instanceof RaidCrystalBlockEntity crystalEntity)) {
                    for (ServerPlayerEntity player : players) {
                        player.sendMessage(Text.literal("§c✗ Failed to create raid crystal!"), false);
                    }
                    return;
                }

                Identifier bossId = RaidRegistry.getRandomRaidBoss(world.getRandom(), world, raidTier, null, null);
                if (bossId == null) {
                    for (ServerPlayerEntity player : players) {
                        player.sendMessage(Text.literal("§c✗ No raid boss found for this tier!"), false);
                    }
                    return;
                }

                RaidBoss boss = RaidRegistry.getRaidBoss(bossId);
                if (boss == null) {
                    for (ServerPlayerEntity player : players) {
                        player.sendMessage(Text.literal("§c✗ Failed to load raid boss!"), false);
                    }
                    return;
                }

                crystalEntity.setRaidBoss(bossId, world.getTime());

                BlockState updatedState = world.getBlockState(crystalPos)
                    .with(RaidCrystalBlock.RAID_TIER, boss.getTier())
                    .with(RaidCrystalBlock.RAID_TYPE, boss.getType());
                world.setBlockState(crystalPos, updatedState, 2);

                crystalEntity.markDirty();

                server.execute(() -> {
                    try {
                        var hitResult = new net.minecraft.util.hit.BlockHitResult(
                            new net.minecraft.util.math.Vec3d(
                                crystalPos.getX() + 0.5,
                                crystalPos.getY() + 0.5,
                                crystalPos.getZ() + 0.5
                            ),
                            net.minecraft.util.math.Direction.UP,
                            crystalPos,
                            false
                        );

                        var interactionManager = leader.interactionManager;
                        var hand = net.minecraft.util.Hand.MAIN_HAND;

                        interactionManager.interactBlock(leader, world, leader.getStackInHand(hand), hand, hitResult);

                        leader.sendMessage(Text.literal("§a✓ Starting " + difficulty + "★ raid..."), false);

                        for (ServerPlayerEntity player : players) {
                            if (player != leader) {
                                player.sendMessage(Text.literal("§e" + leader.getName().getString() + " §7started the raid!"), false);
                            }
                        }

                    } catch (Exception e) {
                        leader.sendMessage(Text.literal("§c✗ Failed to start raid: " + e.getMessage()), false);
                        CobblemonRaidDens.LOGGER.error("Failed to auto-start raid", e);
                    }
                });

            } catch (Exception e) {
                for (ServerPlayerEntity player : players) {
                    player.sendMessage(Text.literal("§c✗ Failed to configure raid: " + e.getMessage()), false);
                }
                CobblemonRaidDens.LOGGER.error("Raid configuration failed", e);
            }
        });
    }
}
