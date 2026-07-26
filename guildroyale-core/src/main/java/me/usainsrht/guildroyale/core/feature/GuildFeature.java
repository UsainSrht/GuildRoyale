package me.usainsrht.guildroyale.core.feature;

/**
 * Features that can be gated behind a minimum guild level.
 */
public enum GuildFeature {
    SHORTNAME("shortname"),
    BADGE("badge"),
    STORAGE("storage"),
    BANK("bank");

    private final String configKey;

    GuildFeature(String configKey) {
        this.configKey = configKey;
    }

    public String configKey() { return configKey; }
}
