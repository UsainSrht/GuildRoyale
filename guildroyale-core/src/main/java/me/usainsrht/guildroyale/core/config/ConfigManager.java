package me.usainsrht.guildroyale.core.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * Provides typed access to {@code config.yml} values.
 * Call {@link #reload()} after the plugin reloads configuration.
 */
public final class ConfigManager {

    private final JavaPlugin plugin;
    private FileConfiguration cfg;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        cfg = plugin.getConfig();
    }

    // ── Storage ─────────────────────────────────────────────────

    public String getStorageType() {
        return cfg.getString("storage.type", "JSON");
    }

    public String getSqliteFile() {
        return cfg.getString("storage.sqlite.file", "guildroyale.db");
    }

    public String getMysqlHost() { return cfg.getString("storage.mysql.host", "localhost"); }
    public int getMysqlPort() { return cfg.getInt("storage.mysql.port", 3306); }
    public String getMysqlDatabase() { return cfg.getString("storage.mysql.database", "guildroyale"); }
    public String getMysqlUsername() { return cfg.getString("storage.mysql.username", "root"); }
    public String getMysqlPassword() { return cfg.getString("storage.mysql.password", ""); }
    public int getMysqlPoolSize() { return cfg.getInt("storage.mysql.pool-size", 10); }

    // ── Creation ────────────────────────────────────────────────

    public double getCreationMoneyCost() {
        return cfg.getDouble("creation.money-cost", 0.0);
    }

    // ── Shortname ────────────────────────────────────────────────

    public double getShortnameChangeCost() {
        return cfg.getDouble("shortname-change-cost", 0.0);
    }

    // ── XP / levels ──────────────────────────────────────────────

    public long getXpBase() { return cfg.getLong("xp.base", 1000L); }
    public double getXpMultiplier() { return cfg.getDouble("xp.multiplier", 1.5); }
    public int getLevelCap() { return cfg.getInt("xp.level-cap", 10); }

    // ── Leaderboard ──────────────────────────────────────────────

    public int getLeaderboardPageSize() {
        return cfg.getInt("leaderboard.page-size", 10);
    }

    public long getLeaderboardCacheRefreshSeconds() {
        return cfg.getLong("leaderboard.cache-refresh-seconds", 60L);
    }

    // ── Invites ──────────────────────────────────────────────────

    public long getInviteExpireSeconds() {
        return cfg.getLong("invite.expire-seconds", 120L);
    }
}
