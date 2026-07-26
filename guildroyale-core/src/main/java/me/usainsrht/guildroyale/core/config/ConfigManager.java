package me.usainsrht.guildroyale.core.config;

import me.usainsrht.guildroyale.core.feature.GuildFeature;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

/**
 * Provides typed access to {@code config.yml} values.
 * Call {@link #reload()} after the plugin reloads configuration.
 */
public final class ConfigManager {

    private final JavaPlugin plugin;
    private FileConfiguration cfg;
    private Map<String, BadgeDefinition> badges = Map.of();

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        cfg = plugin.getConfig();
        badges = loadBadges();
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

    public boolean isCreationPermissionEnabled() {
        return cfg.getBoolean("creation.permission.enabled", true);
    }

    public String getCreationPermissionNode() {
        return cfg.getString("creation.permission.node", "guildroyale.create");
    }

    public boolean isCreationMoneyEnabled() {
        return cfg.getBoolean("creation.money.enabled", true);
    }

    public double getCreationMoneyCost() {
        return cfg.getDouble("creation.money.cost", cfg.getDouble("creation.money-cost", 0.0));
    }

    public boolean isCreationItemsEnabled() {
        return cfg.getBoolean("creation.items.enabled", false);
    }

    public List<ItemRequirement> getCreationItemRequirements() {
        List<ItemRequirement> result = new ArrayList<>();
        List<Map<?, ?>> list = cfg.getMapList("creation.items.requirements");
        if (list.isEmpty()) {
            list = cfg.getMapList("creation.item-requirements");
        }
        for (Map<?, ?> map : list) {
            Object mat = map.get("material");
            Object amt = map.get("amount");
            if (mat == null) continue;
            int amount = amt instanceof Number n ? n.intValue() : 1;
            result.add(new ItemRequirement(mat.toString(), Math.max(1, amount)));
        }
        return result;
    }

    // ── Shortname ────────────────────────────────────────────────

    public double getShortnameChangeCost() {
        return cfg.getDouble("shortname-change-cost", 0.0);
    }

    // ── XP / levels ──────────────────────────────────────────────

    public long getXpBase() { return cfg.getLong("xp.base", 1000L); }
    public double getXpMultiplier() { return cfg.getDouble("xp.multiplier", 1.5); }

    /**
     * Maximum guild level. {@code <= 0} means unlimited.
     */
    public int getLevelCap() { return cfg.getInt("xp.level-cap", 10); }

    public boolean hasLevelCap() { return getLevelCap() > 0; }

    // ── Features ─────────────────────────────────────────────────

    public int getFeatureUnlockLevel(GuildFeature feature) {
        return cfg.getInt("features." + feature.configKey() + ".unlock-level", 1);
    }

    public int getStorageSlotsPerLevel() {
        return Math.max(1, cfg.getInt("features.storage.slots-per-level", 9));
    }

    public int getStorageMaxSlots() {
        return Math.min(54, Math.max(9, cfg.getInt("features.storage.max-slots", 54)));
    }

    /**
     * Computes chest slot count for a guild level (multiple of 9, 9–54).
     */
    public int getStorageSlotsForLevel(int level) {
        int raw = Math.min(getStorageMaxSlots(), Math.max(0, level) * getStorageSlotsPerLevel());
        if (raw < 9) return 9;
        int rounded = ((raw + 8) / 9) * 9;
        return Math.min(54, rounded);
    }

    // ── Badges ───────────────────────────────────────────────────

    public Map<String, BadgeDefinition> getBadges() {
        return badges;
    }

    public Optional<BadgeDefinition> getBadge(String id) {
        return Optional.ofNullable(badges.get(id));
    }

    public Optional<String> getBadgeDisplay(String id) {
        return getBadge(id).map(BadgeDefinition::display);
    }

    private Map<String, BadgeDefinition> loadBadges() {
        ConfigurationSection section = cfg.getConfigurationSection("badges");
        if (section == null) return Map.of();
        Map<String, BadgeDefinition> map = new LinkedHashMap<>();
        for (String id : section.getKeys(false)) {
            ConfigurationSection badge = section.getConfigurationSection(id);
            if (badge == null) continue;
            String display = badge.getString("display", id);
            double cost = badge.getDouble("cost", 0.0);
            boolean grantable = badge.getBoolean("grantable", true);
            map.put(id, new BadgeDefinition(id, display, cost, grantable));
        }
        return Collections.unmodifiableMap(map);
    }

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
