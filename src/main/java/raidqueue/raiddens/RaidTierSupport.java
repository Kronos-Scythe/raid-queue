package raidqueue.raiddens;

import com.necro.raid.dens.common.data.raid.RaidTier;
import raidqueue.config.RaidQueueConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * Decides which raid tiers the queue may offer. Tiers 1-5 ship with Raid Dens itself,
 * so they're always available. Tiers 6-7 only exist when a datapack registers raid
 * bosses for them (RaidTier#isPresent becomes true once Raid Dens finishes loading raid
 * data) - so on top of that we also require the server operator to opt in via config,
 * since not every modpack with a tier 6/7 datapack necessarily wants it queueable.
 */
public final class RaidTierSupport {

    private RaidTierSupport() {}

    public static final int MIN_TIER = 1;
    public static final int BASE_MAX_TIER = 5;
    public static final int MAX_TIER = 7;

    public static List<Integer> availableTiers() {
        RaidQueueConfig config = RaidQueueConfig.get();
        List<Integer> tiers = new ArrayList<>();
        for (int tier = MIN_TIER; tier <= BASE_MAX_TIER; tier++) tiers.add(tier);
        if (config.enableTierSix && RaidTier.TIER_SIX.isPresent()) tiers.add(6);
        if (config.enableTierSeven && RaidTier.TIER_SEVEN.isPresent()) tiers.add(7);
        return tiers;
    }

    public static boolean isTierAvailable(int tier) {
        return availableTiers().contains(tier);
    }

    public static RaidTier toRaidTier(int tier) {
        return switch (tier) {
            case 1 -> RaidTier.TIER_ONE;
            case 2 -> RaidTier.TIER_TWO;
            case 3 -> RaidTier.TIER_THREE;
            case 4 -> RaidTier.TIER_FOUR;
            case 5 -> RaidTier.TIER_FIVE;
            case 6 -> RaidTier.TIER_SIX;
            case 7 -> RaidTier.TIER_SEVEN;
            default -> RaidTier.TIER_ONE;
        };
    }
}
