package me.usainsrht.guildroyale.core.config;

import me.usainsrht.guildroyale.api.domain.RoleColor;
import me.usainsrht.guildroyale.api.permission.GuildPermissionKey;
import me.usainsrht.guildroyale.core.feature.GuildFeature;
import me.usainsrht.itemapi.yamlitem.YamlItem;
import me.usainsrht.itemapi.yamlitem.YamlParseException;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

/**
 * Provides typed access to {@code config.yml} values.
 * Call {@link #reload()} after the plugin reloads configuration.
 */
public final class ConfigManager {

    public record PermissionDisplay(String name, Material icon) {}

    private final JavaPlugin plugin;
    private FileConfiguration cfg;
    private Map<String, BadgeDefinition> badges = Map.of();
    private Map<GuildPermissionKey, PermissionDisplay> permissions = Map.of();
    private Map<RoleColor, String> roleColorNames = Map.of();

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        cfg = plugin.getConfig();
        badges = loadBadges();
        permissions = loadPermissions();
        roleColorNames = loadRoleColorNames();
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

    public int getStorageUnlockLevel() {
        return getFeatureUnlockLevel(GuildFeature.STORAGE);
    }

    public int getStorageSlotsPerLevel() {
        return Math.max(1, cfg.getInt("features.storage.slots-per-level", 9));
    }

    public int getStorageMaxSlots() {
        return Math.max(9, cfg.getInt("features.storage.max-slots", 135));
    }

    /**
     * Computes unlocked storage slots for a given guild level.
     */
    public int getStorageUnlockedSlots(int level) {
        int unlockLevel = getStorageUnlockLevel();
        if (level < unlockLevel) return 0;
        int raw = (level - unlockLevel + 1) * getStorageSlotsPerLevel();
        return Math.min(getStorageMaxSlots(), Math.max(0, raw));
    }

    /**
     * Computes the required guild level to unlock a specific storage slot index (0-indexed).
     */
    public int getRequiredLevelForSlot(int slotIndex) {
        int unlockLevel = getStorageUnlockLevel();
        int perLevel = getStorageSlotsPerLevel();
        return unlockLevel + (slotIndex / perLevel);
    }

    /**
     * Computes total storage pages required for the max storage capacity.
     */
    public int getStoragePageCount(int slotsPerPage) {
        if (slotsPerPage <= 0) slotsPerPage = 45;
        return (getStorageMaxSlots() + slotsPerPage - 1) / slotsPerPage;
    }

    /**
     * Legacy helper method for single-page slot calculation.
     */
    public int getStorageSlotsForLevel(int level) {
        return getStorageUnlockedSlots(level);
    }

    // ── Badges ───────────────────────────────────────────────────

    public Map<String, BadgeDefinition> getBadges() {
        return badges;
    }

    public Optional<BadgeDefinition> getBadge(String id) {
        return Optional.ofNullable(badges.get(id));
    }

    public Optional<String> getBadgeDisplay(String id) {
        return getBadge(id).map(BadgeDefinition::displayName);
    }

    public Optional<String> getBadgeSymbol(String id) {
        return getBadge(id).map(BadgeDefinition::symbol);
    }

    private Map<String, BadgeDefinition> loadBadges() {
        ConfigurationSection section = cfg.getConfigurationSection("badges");
        if (section == null) return Map.of();
        Map<String, BadgeDefinition> map = new LinkedHashMap<>();
        for (String id : section.getKeys(false)) {
            ConfigurationSection badge = section.getConfigurationSection(id);
            if (badge == null) continue;
            String display = badge.getString("display", badge.getString("display-name", id));
            String symbol = badge.getString("symbol", display);
            ItemStack icon = parseBadgeIcon(badge);
            double cost = badge.getDouble("cost", 0.0);
            boolean grantable = badge.getBoolean("grantable", true);
            int level = badge.getInt("level", badge.getInt("required-level", 1));
            map.put(id, new BadgeDefinition(id, display, symbol, icon, cost, grantable, level));
        }
        return Collections.unmodifiableMap(map);
    }

    private ItemStack parseBadgeIcon(ConfigurationSection section) {
        if (section.isConfigurationSection("icon")) {
            ConfigurationSection iconSec = section.getConfigurationSection("icon");
            if (iconSec != null) {
                try {
                    return YamlItem.parse(iconSec);
                } catch (YamlParseException ex) {
                    plugin.getLogger().warning("Failed to parse YamlItem icon for badge '" + section.getName() + "': " + ex.getMessage());
                }
            }
        } else if (section.isString("icon")) {
            String matStr = section.getString("icon");
            if (matStr != null) {
                Material mat = Material.matchMaterial(matStr);
                if (mat != null) {
                    return new ItemStack(mat);
                }
            }
        }
        return new ItemStack(Material.NETHER_STAR);
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

    // ── Permissions & Role Colors ───────────────────────────────

    public String getPermissionName(GuildPermissionKey key) {
        if (key == null) return "";
        PermissionDisplay display = permissions.get(key);
        if (display != null && display.name() != null && !display.name().isBlank()) {
            return display.name();
        }
        return prettyPermissionName(key);
    }

    public Material getPermissionIcon(GuildPermissionKey key) {
        if (key == null) return Material.PAPER;
        PermissionDisplay display = permissions.get(key);
        if (display != null && display.icon() != null) {
            return display.icon();
        }
        return Material.PAPER;
    }

    public String getRoleColorName(RoleColor color) {
        if (color == null) return "";
        return roleColorNames.getOrDefault(color, color.miniMessage() + color.name());
    }

    private Map<GuildPermissionKey, PermissionDisplay> loadPermissions() {
        ConfigurationSection section = cfg.getConfigurationSection("permissions");
        if (section == null) return Map.of();
        Map<GuildPermissionKey, PermissionDisplay> map = new EnumMap<>(GuildPermissionKey.class);
        for (String key : section.getKeys(false)) {
            ConfigurationSection perm = section.getConfigurationSection(key);
            if (perm == null) continue;
            try {
                GuildPermissionKey permKey = GuildPermissionKey.valueOf(key.toUpperCase(Locale.ROOT));
                String name = perm.getString("name");
                String iconRaw = perm.getString("icon");
                Material icon = iconRaw != null ? Material.matchMaterial(iconRaw) : null;
                map.put(permKey, new PermissionDisplay(name, icon));
            } catch (IllegalArgumentException ignored) {}
        }
        return Collections.unmodifiableMap(map);
    }

    private Map<RoleColor, String> loadRoleColorNames() {
        ConfigurationSection section = cfg.getConfigurationSection("role-colors");
        if (section == null) return Map.of();
        Map<RoleColor, String> map = new EnumMap<>(RoleColor.class);
        for (String key : section.getKeys(false)) {
            RoleColor.fromString(key).ifPresent(color -> {
                String val = section.getString(key);
                if (val != null && !val.isBlank()) {
                    map.put(color, val);
                }
            });
        }
        return Collections.unmodifiableMap(map);
    }

    private static String prettyPermissionName(GuildPermissionKey key) {
        String raw = key.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        String[] parts = raw.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }
}
