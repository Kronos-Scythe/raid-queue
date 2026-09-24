package raidqueue.raiddens;

import com.necro.raid.dens.common.CobblemonRaidDens;
import com.necro.raid.dens.common.blocks.block.RaidCrystalBlock;
import com.necro.raid.dens.common.blocks.entity.RaidCrystalBlockEntity;
import com.necro.raid.dens.common.data.dimension.RaidRegion;
import com.necro.raid.dens.common.data.raid.RaidBoss;
import com.necro.raid.dens.common.data.raid.RaidCycleMode;
import com.necro.raid.dens.common.data.raid.RaidTier;
import com.necro.raid.dens.common.network.RaidDenNetworkMessages;
import com.necro.raid.dens.common.raids.RaidInstance;
import com.necro.raid.dens.common.raids.helpers.RaidHelper;
import com.necro.raid.dens.common.raids.helpers.RaidJoinHelper;
import com.necro.raid.dens.common.raids.helpers.RaidRegionHelper;
import com.necro.raid.dens.common.registry.RaidDenRegistry;
import com.necro.raid.dens.common.registry.RaidRegistry;
import com.necro.raid.dens.common.util.RaidUtils;
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
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import raidqueue.config.RaidQueueConfig;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Drives players straight into a Raid Dens encounter without any block-interaction
 * simulation. Earlier versions placed a crystal underneath the player and simulated a
 * right-click for every queued player - it worked, but the crystal had to be shoved at
 * Y=-64 (frequently clipping into bedrock/stone) and every follower needed a fake
 * interaction to "click" a block they never actually stood near.
 *
 * <p>Raid Dens itself starts a raid by driving a handful of public helpers
 * (RaidRegionHelper, RaidJoinHelper, RaidHelper, RaidUtils - see
 * RaidCrystalBlock#startRaid in the addon's own source) once it has a
 * RaidCrystalBlockEntity to hang the raid's state off. We call those same helpers
 * directly: one crystal is still required as the addon's bookkeeping anchor, but it
 * lives in a reserved pocket of the Overworld sky, far from any player-visited terrain,
 * and every queued player is registered as a participant and teleported into the raid
 * dimension in a single pass - no interaction simulation, no per-player proximity
 * requirement, and nothing left buried underground afterwards.
 */
public final class RaidDenLauncher {

    private RaidDenLauncher() {}

    private static final Identifier CRYSTAL_ID = new Identifier("cobblemonraiddens", "raid_crystal_block");

    // Reserved staging area: far enough from spawn that it will never overlap real
    // builds, high enough (but under the 320 build limit) to always be open air.
    private static final int STAGING_BASE_X = 3_000_000;
    private static final int STAGING_BASE_Z = 3_000_000;
    private static final int STAGING_SPACING = 32;
    private static final int STAGING_Y = 250;

    private static final AtomicInteger NEXT_SLOT = new AtomicInteger();
    private static final Deque<Integer> FREE_SLOTS = new ArrayDeque<>();

    private static final Map<UUID, ActiveLaunch> ACTIVE_LAUNCHES = new HashMap<>();
    private static int cleanupTickCounter = 0;

    private static final Map<UUID, PlayerLocation> PLAYER_POSITIONS = new HashMap<>();

    private record ActiveLaunch(ServerWorld world, BlockPos pos, int slot) {}

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
            player.sendMessage(Text.literal("§cNo previous location saved. Teleporting to world spawn."), false);
            teleportToWorldSpawn(player);
            return;
        }

        MinecraftServer server = player.getServer();
        if (server == null) return;

        ServerWorld world = server.getWorld(loc.world);
        if (world == null) {
            player.sendMessage(Text.literal("§cCouldn't find your previous world! Teleporting to world spawn."), false);
            teleportToWorldSpawn(player);
            return;
        }

        double safeY = loc.y < world.getBottomY() ? world.getSeaLevel() : loc.y;

        player.teleport(world, loc.x, safeY, loc.z, loc.yaw, loc.pitch);
        player.sendMessage(Text.literal("§a✓ Teleported back to your previous location!"), false);
    }

    private static void teleportToWorldSpawn(ServerPlayerEntity player) {
        ServerWorld overworld = player.getServer().getOverworld();
        BlockPos spawnPos = overworld.getSpawnPos();
        player.teleport(overworld, spawnPos.getX(), spawnPos.getY(), spawnPos.getZ(), 0, 0);
    }

    /**
     * Periodic maintenance: once a raid we launched is no longer tracked as active by
     * Raid Dens (it finished, was cleared, or timed out), remove its staging crystal
     * and hand the slot back to the pool. Call this from a server tick event; it only
     * does real work once every few seconds.
     */
    public static void serverTick(MinecraftServer server) {
        if (++cleanupTickCounter < 100) return;
        cleanupTickCounter = 0;
        if (ACTIVE_LAUNCHES.isEmpty()) return;

        ACTIVE_LAUNCHES.entrySet().removeIf(entry -> {
            if (RaidHelper.ACTIVE_RAIDS.containsKey(entry.getKey())) return false;
            ActiveLaunch launch = entry.getValue();
            launch.world.removeBlock(launch.pos, false);
            freeSlot(launch.slot);
            return true;
        });
    }

    private static BlockPos allocateSlot(int[] slotOut) {
        Integer reused = FREE_SLOTS.poll();
        int slot = reused != null ? reused : NEXT_SLOT.getAndIncrement();
        slotOut[0] = slot;
        return new BlockPos(STAGING_BASE_X + slot * STAGING_SPACING, STAGING_Y, STAGING_BASE_Z);
    }

    private static void freeSlot(int slot) {
        FREE_SLOTS.push(slot);
    }

    /**
     * Registers every queued player as a raid participant and teleports them into the
     * raid dimension together.
     *
     * @param world      the leader's current world; only used as a random source and
     *                   fallback message target, the staging crystal always lives in
     *                   the Overworld regardless of where the party is standing.
     * @param players    the queued players, host first.
     * @param tier       the raid tier (1-7); tiers above 5 must already be validated
     *                   against {@link RaidTierSupport#isTierAvailable(int)} by the caller.
     */
    public static void tryLaunch(ServerWorld world, List<ServerPlayerEntity> players, int tier) {
        if (players.isEmpty()) return;

        if (!RaidTierSupport.isTierAvailable(tier)) {
            for (ServerPlayerEntity player : players) {
                player.sendMessage(Text.literal("§c✗ " + tier + "★ raids aren't enabled on this server."), false);
            }
            return;
        }

        int maxPartySize = RaidQueueConfig.get().maxPartySize;
        if (players.size() > maxPartySize) {
            for (ServerPlayerEntity player : players) {
                player.sendMessage(Text.literal("§cToo many players! Maximum is " + maxPartySize + "."), false);
            }
            return;
        }

        ServerPlayerEntity leader = players.get(0);
        MinecraftServer server = world.getServer();

        Block raidCrystalBlock = Registries.BLOCK.get(CRYSTAL_ID);
        if (raidCrystalBlock == null || raidCrystalBlock == Blocks.AIR) {
            for (ServerPlayerEntity player : players) {
                player.sendMessage(Text.literal("§c✗ Raid crystal block not found! Is Raid Dens installed?"), false);
            }
            return;
        }

        RaidTier raidTier = RaidTierSupport.toRaidTier(tier);
        ServerWorld overworld = server.getOverworld();
        Random random = world.getRandom();

        Identifier bossId = RaidRegistry.getRandomRaidBoss(random, world, raidTier, null, null);
        if (bossId == null) {
            for (ServerPlayerEntity player : players) {
                player.sendMessage(Text.literal("§c✗ No raid boss is registered for " + tier + "★ raids!"), false);
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

        // Save return positions for ALL players before anything else can go wrong.
        for (ServerPlayerEntity player : players) {
            PLAYER_POSITIONS.put(player.getUuid(), new PlayerLocation(player));
        }

        int[] slotOut = new int[1];
        BlockPos crystalPos = allocateSlot(slotOut);

        BlockState crystalState = raidCrystalBlock.getDefaultState()
            .with(RaidCrystalBlock.ACTIVE, true)
            .with(RaidCrystalBlock.CYCLE_MODE, RaidCycleMode.LOCK_BOTH)
            .with(RaidCrystalBlock.CAN_RESET, false)
            .with(RaidCrystalBlock.IS_NATURAL, false)
            .with(RaidCrystalBlock.RAID_TIER, boss.getTier())
            .with(RaidCrystalBlock.RAID_TYPE, boss.getType());
        overworld.setBlockState(crystalPos, crystalState);

        server.execute(() -> launchInto(server, overworld, crystalPos, slotOut[0], leader, players, boss, random));
    }

    private static void launchInto(
            MinecraftServer server,
            ServerWorld overworld,
            BlockPos crystalPos,
            int slot,
            ServerPlayerEntity leader,
            List<ServerPlayerEntity> players,
            RaidBoss boss,
            Random random
    ) {
        if (!(overworld.getBlockEntity(crystalPos) instanceof RaidCrystalBlockEntity crystalEntity)) {
            failAndCleanup(players, overworld, crystalPos, slot, "§c✗ Failed to create raid crystal!");
            return;
        }

        try {
            crystalEntity.setRaidBoss(boss.getId(), overworld.getTime());

            Identifier structure = boss.getRandomDen(random);
            RaidRegion region = RaidRegionHelper.createRegion(crystalEntity.getUuid(), structure, random);
            if (region == null) {
                failAndCleanup(players, overworld, crystalPos, slot, "§c✗ No free raid dimension slot right now, try again shortly.");
                return;
            }

            boolean spawned = crystalEntity.spawnRaidBoss(leader.getUuid(), RaidDenRegistry.getScaleModifier(structure));
            if (!spawned) {
                failAndCleanup(players, overworld, crystalPos, slot, "§c✗ Failed to spawn the raid boss!");
                return;
            }

            RaidInstance raid = RaidHelper.ACTIVE_RAIDS.get(crystalEntity.getUuid());
            if (raid == null) {
                failAndCleanup(players, overworld, crystalPos, slot, "§c✗ Raid failed to start!");
                return;
            }

            ACTIVE_LAUNCHES.put(crystalEntity.getUuid(), new ActiveLaunch(overworld, crystalPos, slot));

            for (int i = 0; i < players.size(); i++) {
                ServerPlayerEntity player = players.get(i);
                boolean isHost = i == 0;

                if (!RaidJoinHelper.addParticipant(player, crystalEntity.getUuid(), isHost, false)) {
                    player.sendMessage(Text.literal("§c✗ You're already in another raid!"), false);
                    continue;
                }

                raid.addPlayer(player);
                if (!isHost) RaidDenNetworkMessages.JOIN_RAID.accept(player, true);
                RaidUtils.teleportPlayerToRaid(player, server, region);
                crystalEntity.syncAspects(player);

                player.sendMessage(Text.literal("§a✓ Entering " + boss.getTier().getStars() + " raid against "
                    + raid.getBossEntity().getDisplayName().getString() + "..."), false);
            }
        } catch (Exception e) {
            CobblemonRaidDens.LOGGER.error("Failed to launch queued raid", e);
            failAndCleanup(players, overworld, crystalPos, slot, "§c✗ Failed to start raid: " + e.getMessage());
        }
    }

    private static void failAndCleanup(List<ServerPlayerEntity> players, ServerWorld world, BlockPos pos, int slot, String message) {
        for (ServerPlayerEntity player : players) {
            player.sendMessage(Text.literal(message), false);
        }
        world.removeBlock(pos, false);
        freeSlot(slot);
    }
}
