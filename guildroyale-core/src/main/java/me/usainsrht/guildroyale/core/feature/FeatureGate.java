package me.usainsrht.guildroyale.core.feature;

import me.usainsrht.guildroyale.api.domain.Guild;
import me.usainsrht.guildroyale.core.config.ConfigManager;

/**
 * Checks whether a guild has unlocked a level-gated feature.
 */
public final class FeatureGate {

    private final ConfigManager config;

    public FeatureGate(ConfigManager config) {
        this.config = config;
    }

    public int unlockLevel(GuildFeature feature) {
        return config.getFeatureUnlockLevel(feature);
    }

    public boolean isUnlocked(Guild guild, GuildFeature feature) {
        return guild.getLevel() >= unlockLevel(feature);
    }
}
