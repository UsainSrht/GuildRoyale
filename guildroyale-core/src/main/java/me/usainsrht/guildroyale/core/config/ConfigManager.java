package me.usainsrht.guildroyale.core.config;

import me.usainsrht.guildroyale.api.domain.GuildRole;
import me.usainsrht.guildroyale.api.domain.RoleColor;
import me.usainsrht.guildroyale.api.domain.SerializableItemStack;
import me.usainsrht.guildroyale.api.permission.GuildPermissionKey;
import me.usainsrht.guildroyale.core.feature.GuildFeature;
import me.usainsrht.itemapi.yamlitem.YamlItem;
import me.usainsrht.itemapi.yamlitem.YamlParseException;
import me.usainsrht.guildroyale.core.adapter.ItemStackAdapter;
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

    public record PermissionDisplay(String name, Material icon, int defaultIndex) {
        public PermissionDisplay(String name, Material icon) {
            this(name, icon, 0);
        }
    }

    public record DefaultRoleDefinition(String name, int index, RoleColor color, RoleColor glowColor, SerializableItemStack icon) {
        public DefaultRoleDefinition(String name, int index, RoleColor color, SerializableItemStack icon) {
            this(name, index, color, color != null ? color : RoleColor.WHITE, icon);
        }
    }

    private final JavaPlugin plugin;
    private FileConfiguration cfg;
    private Map<String, BadgeDefinition> badges = Map.of();
    private Map<GuildPermissionKey, PermissionDisplay> permissions = Map.of();
    private Map<RoleColor, String> roleColorNames = Map.of();
    private List<DefaultRoleDefinition> defaultRoles = List.of();
    private MissionConfig missionConfig;

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
        defaultRoles = loadDefaultRoles();
        missionConfig = new MissionConfig(cfg);
    }

    public MissionConfig getMissionConfig() {
        return missionConfig;
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

    public int getMaxMembersForLevel(int level) {
        int base = cfg.getInt("features.members.base", 10);
        int perLevel = cfg.getInt("features.members.per-level", 2);
        int max = cfg.getInt("features.members.max", 50);
        int computed = base + (Math.max(1, level) - 1) * perLevel;
        return max > 0 ? Math.min(max, computed) : computed;
    }

    public int getMaxRolesForLevel(int level) {
        int base = cfg.getInt("features.roles.base", 4);
        int perLevel = cfg.getInt("features.roles.per-level", 1);
        int max = cfg.getInt("features.roles.max", 15);
        int computed = base + (Math.max(1, level) - 1) * perLevel;
        return max > 0 ? Math.min(max, computed) : computed;
    }

    public List<String> getCustomPerksForLevel(int level) {
        List<String> list = cfg.getStringList("xp.perks." + level);
        if (list.isEmpty()) {
            list = cfg.getStringList("features.perks." + level);
        }
        return list;
    }

    public List<String> getNextLevelAdvantages(int currentLevel) {
        int nextLevel = currentLevel + 1;
        int cap = getLevelCap();
        if (cap > 0 && currentLevel >= cap) {
            return List.of();
        }
        List<String> advantages = new ArrayList<>();

        int currentMembers = getMaxMembersForLevel(currentLevel);
        int nextMembers = getMaxMembersForLevel(nextLevel);
        if (nextMembers > currentMembers) {
            advantages.add(" <green>+ <white>" + (nextMembers - currentMembers) + " Max Members <dark_gray>(" + nextMembers + " max)</dark_gray>");
        }

        int currentRoles = getMaxRolesForLevel(currentLevel);
        int nextRoles = getMaxRolesForLevel(nextLevel);
        if (nextRoles > currentRoles) {
            advantages.add(" <green>+ <white>" + (nextRoles - currentRoles) + " Max Custom Roles <dark_gray>(" + nextRoles + " max)</dark_gray>");
        }

        int currentSlots = getStorageUnlockedSlots(currentLevel);
        int nextSlots = getStorageUnlockedSlots(nextLevel);
        if (nextLevel == getStorageUnlockLevel() && currentSlots == 0 && nextSlots > 0) {
            advantages.add(" <green>+ <white>Guild Storage Unlocked <dark_gray>(" + nextSlots + " slots)</dark_gray>");
        } else if (nextSlots > currentSlots) {
            advantages.add(" <green>+ <white>" + (nextSlots - currentSlots) + " Storage Slots <dark_gray>(" + nextSlots + " total)</dark_gray>");
        }

        if (nextLevel == getFeatureUnlockLevel(GuildFeature.ICON)) {
            advantages.add(" <green>+ <white>Custom Guild Icon Unlocked");
        }
        if (nextLevel == getFeatureUnlockLevel(GuildFeature.SHORTNAME)) {
            advantages.add(" <green>+ <white>Guild Shortname Tag Unlocked");
        }
        if (nextLevel == getFeatureUnlockLevel(GuildFeature.BANK)) {
            advantages.add(" <green>+ <white>Guild Bank Unlocked");
        }
        if (nextLevel == getFeatureUnlockLevel(GuildFeature.BADGE)) {
            advantages.add(" <green>+ <white>Guild Badges Unlocked");
        }

        for (String perk : getCustomPerksForLevel(nextLevel)) {
            advantages.add(" <green>+ <white>" + perk);
        }

        if (advantages.isEmpty()) {
            advantages.add(" <gray>+ Guild progression level " + nextLevel);
        }

        return advantages;
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

    public int getPermissionDefaultIndex(GuildPermissionKey key) {
        if (key == null) return 0;
        PermissionDisplay display = permissions.get(key);
        if (display != null) {
            return display.defaultIndex();
        }
        return fallbackDefaultPermissionIndex(key);
    }

    public List<DefaultRoleDefinition> getDefaultRoleDefinitions() {
        return defaultRoles;
    }

    /**
     * Creates the initial list of {@link GuildRole}s for a new guild based on config settings.
     * Roles carry permissions based on whether the role's index is <= the permission's default index.
     */
    public List<GuildRole> createDefaultRoles() {
        List<DefaultRoleDefinition> defs = defaultRoles;
        if (defs.isEmpty()) {
            defs = defaultFallbackRoles();
        }
        List<GuildRole> roles = new ArrayList<>();
        for (DefaultRoleDefinition def : defs) {
            Set<GuildPermissionKey> rolePerms = EnumSet.noneOf(GuildPermissionKey.class);
            for (GuildPermissionKey permKey : GuildPermissionKey.values()) {
                if (def.index() == 0 || def.index() <= getPermissionDefaultIndex(permKey)) {
                    rolePerms.add(permKey);
                }
            }
            roles.add(new GuildRole(def.name(), def.index(), rolePerms, def.icon(), def.color(), def.glowColor()));
        }
        return roles;
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
                int defaultIndex = perm.getInt("index", fallbackDefaultPermissionIndex(permKey));
                map.put(permKey, new PermissionDisplay(name, icon, defaultIndex));
            } catch (IllegalArgumentException ignored) {}
        }
        return Collections.unmodifiableMap(map);
    }

    private List<DefaultRoleDefinition> loadDefaultRoles() {
        List<Map<?, ?>> list = cfg.getMapList("default-roles");
        if (list.isEmpty()) {
            return defaultFallbackRoles();
        }
        List<DefaultRoleDefinition> result = new ArrayList<>();
        for (Map<?, ?> map : list) {
            Object nameObj = map.get("name");
            Object indexObj = map.get("index");
            if (nameObj == null || indexObj == null) continue;

            String name = nameObj.toString();
            int index;
            if (indexObj instanceof Number n) {
                index = n.intValue();
            } else {
                try {
                    index = Integer.parseInt(indexObj.toString());
                } catch (NumberFormatException ignored) {
                    continue;
                }
            }

            RoleColor color = RoleColor.WHITE;
            Object colorObj = map.get("color");
            if (colorObj != null) {
                color = RoleColor.fromString(colorObj.toString()).orElse(RoleColor.WHITE);
            }

            RoleColor glowColor = color;
            Object glowColorObj = map.get("glow-color");
            if (glowColorObj != null) {
                glowColor = RoleColor.fromString(glowColorObj.toString()).orElse(color);
            }

            SerializableItemStack icon = SerializableItemStack.EMPTY;
            Object iconObj = map.get("icon");
            if (iconObj != null) {
                Material mat = Material.matchMaterial(iconObj.toString());
                if (mat != null) {
                    icon = ItemStackAdapter.toSerializable(new ItemStack(mat));
                }
            }

            result.add(new DefaultRoleDefinition(name, index, color, glowColor, icon));
        }
        if (result.isEmpty()) {
            return defaultFallbackRoles();
        }
        return Collections.unmodifiableList(result);
    }

    private List<DefaultRoleDefinition> defaultFallbackRoles() {
        return List.of(
                new DefaultRoleDefinition("Leader", 0, RoleColor.YELLOW, RoleColor.YELLOW, SerializableItemStack.EMPTY),
                new DefaultRoleDefinition("Co-Leader", 1, RoleColor.ORANGE, RoleColor.ORANGE, SerializableItemStack.EMPTY),
                new DefaultRoleDefinition("Helper", 2, RoleColor.LIME, RoleColor.LIME, SerializableItemStack.EMPTY),
                new DefaultRoleDefinition("Member", 3, RoleColor.GRAY, RoleColor.GRAY, SerializableItemStack.EMPTY)
        );
    }

    private static int fallbackDefaultPermissionIndex(GuildPermissionKey key) {
        return switch (key) {
            case ROLE_MANAGEMENT, DISBANDMENT -> 0;
            case MEMBER_MANAGEMENT, KICK, GUILD_SETTINGS, ICON_CHANGE, SHORTNAME_CHANGE, BADGE_MANAGE, BANK_WITHDRAW, MISSION_START -> 1;
            case INVITE, STORAGE_ACCESS, BANK_VIEW, BANK_DEPOSIT -> 2;
        };
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
