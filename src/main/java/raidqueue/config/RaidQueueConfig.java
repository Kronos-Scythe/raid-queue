package raidqueue.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import raidqueue.RaidDenQueue;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Server-side settings for the raid queue. Tier 6/7 are only ever offered when BOTH
 * the matching flag here is enabled AND Raid Dens itself reports raid bosses registered
 * for that tier (RaidTier#isPresent) - i.e. a datapack actually adds tier 6/7 content.
 */
public class RaidQueueConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("raid-den-queue.json");

    private static RaidQueueConfig instance;

    public boolean enableTierSix = false;
    public boolean enableTierSeven = false;
    public int maxPartySize = 4;

    public static RaidQueueConfig get() {
        if (instance == null) instance = load();
        return instance;
    }

    private static RaidQueueConfig load() {
        if (Files.exists(PATH)) {
            try (Reader reader = Files.newBufferedReader(PATH, StandardCharsets.UTF_8)) {
                RaidQueueConfig loaded = GSON.fromJson(reader, RaidQueueConfig.class);
                if (loaded != null) return loaded.sanitize();
            } catch (IOException | RuntimeException e) {
                RaidDenQueue.LOGGER.error("Failed to load raid-den-queue.json, using defaults", e);
            }
        }

        RaidQueueConfig defaults = new RaidQueueConfig();
        defaults.save();
        return defaults;
    }

    private RaidQueueConfig sanitize() {
        if (this.maxPartySize < 1) this.maxPartySize = 1;
        if (this.maxPartySize > 4) this.maxPartySize = 4;
        return this;
    }

    public void save() {
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(PATH, StandardCharsets.UTF_8)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            RaidDenQueue.LOGGER.error("Failed to save raid-den-queue.json", e);
        }
    }
}
