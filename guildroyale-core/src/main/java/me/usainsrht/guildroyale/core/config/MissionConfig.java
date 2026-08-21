package me.usainsrht.guildroyale.core.config;

import me.usainsrht.guildroyale.api.domain.mission.MissionTaskDefinition;
import me.usainsrht.guildroyale.api.domain.mission.MissionTaskType;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Parses and provides configuration for the Guild Missions feature.
 */
public final class MissionConfig {

    public enum CooldownType {
        DELAY,
        DAILY,
        WEEKLY;

        public static CooldownType fromString(String str) {
            if (str == null) return DAILY;
            try {
                return valueOf(str.toUpperCase());
            } catch (IllegalArgumentException e) {
                return DAILY;
            }
        }
    }

    public enum TakeFrom {
        PLAYER,
        BANK;

        public static TakeFrom fromString(String str) {
            if (str == null) return BANK;
            try {
                return valueOf(str.toUpperCase());
            } catch (IllegalArgumentException e) {
                return BANK;
            }
        }
    }

    private final boolean enabled;
    private final long durationSeconds;
    private final CooldownType cooldownType;
    private final long delaySeconds;
    private final LocalTime dailyResetTime;
    private final DayOfWeek weeklyResetDay;
    private final LocalTime weeklyResetTime;
    private final ZoneId timezone;

    private final boolean permissionEnabled;
    private final String permissionNode;
    private final boolean moneyEnabled;
    private final double moneyCost;
    private final TakeFrom moneyTakeFrom;
    private final boolean itemsEnabled;
    private final List<ItemRequirement> itemRequirements;

    private final long rewardXp;
    private final double rewardMoney;
    private final long penaltyXp;
    private final double penaltyMoney;

    private final boolean bossbarEnabled;
    private final String bossbarTitle;
    private final BossBar.Color bossbarColor;
    private final BossBar.Overlay bossbarOverlay;

    private final List<MissionTaskDefinition> tasks;

    public MissionConfig(FileConfiguration cfg) {
        ConfigurationSection sec = cfg.getConfigurationSection("missions");
        if (sec == null) {
            // Default configuration
            this.enabled = true;
            this.durationSeconds = 7200L;
            this.cooldownType = CooldownType.DAILY;
            this.delaySeconds = 86400L;
            this.dailyResetTime = LocalTime.of(12, 0);
            this.weeklyResetDay = DayOfWeek.MONDAY;
            this.weeklyResetTime = LocalTime.MIDNIGHT;
            this.timezone = ZoneId.of("UTC");

            this.permissionEnabled = false;
            this.permissionNode = "guildroyale.mission.start";
            this.moneyEnabled = true;
            this.moneyCost = 1000.0;
            this.moneyTakeFrom = TakeFrom.BANK;
            this.itemsEnabled = false;
            this.itemRequirements = List.of();

            this.rewardXp = 5000L;
            this.rewardMoney = 2500.0;
            this.penaltyXp = 2500L;
            this.penaltyMoney = 1000.0;

            this.bossbarEnabled = true;
            this.bossbarTitle = "<gradient:#55c57a:#a8e6c1><bold>Guild Mission</bold></gradient> <dark_gray>•</dark_gray> <green><percent>%</green> <dark_gray>(<white><completed_tasks>/<total_tasks> tasks<dark_gray>)</dark_gray> <dark_gray>•</dark_gray> <yellow><time_remaining></yellow>";
            this.bossbarColor = BossBar.Color.GREEN;
            this.bossbarOverlay = BossBar.Overlay.PROGRESS;
            this.tasks = defaultTasks();
            return;
        }

        this.enabled = sec.getBoolean("enabled", true);
        this.durationSeconds = sec.getLong("duration", 7200L);

        // Cooldown
        ConfigurationSection cdSec = sec.getConfigurationSection("cooldown");
        if (cdSec != null) {
            this.cooldownType = CooldownType.fromString(cdSec.getString("type", "DAILY"));
            this.delaySeconds = cdSec.getLong("delay-seconds", 86400L);
            this.dailyResetTime = parseTime(cdSec.getString("daily-reset-time", "12:00"), LocalTime.of(12, 0));
            this.weeklyResetDay = parseDayOfWeek(cdSec.getString("weekly-reset-day", "MONDAY"), DayOfWeek.MONDAY);
            this.weeklyResetTime = parseTime(cdSec.getString("weekly-reset-time", "00:00"), LocalTime.MIDNIGHT);
            this.timezone = parseZone(cdSec.getString("timezone", "UTC"));
        } else {
            this.cooldownType = CooldownType.DAILY;
            this.delaySeconds = 86400L;
            this.dailyResetTime = LocalTime.of(12, 0);
            this.weeklyResetDay = DayOfWeek.MONDAY;
            this.weeklyResetTime = LocalTime.MIDNIGHT;
            this.timezone = ZoneId.of("UTC");
        }

        // Requirements
        ConfigurationSection reqSec = sec.getConfigurationSection("requirements");
        if (reqSec != null) {
            this.permissionEnabled = reqSec.getBoolean("permission.enabled", false);
            this.permissionNode = reqSec.getString("permission.node", "guildroyale.mission.start");
            this.moneyEnabled = reqSec.getBoolean("money.enabled", true);
            this.moneyCost = reqSec.getDouble("money.cost", 1000.0);
            this.moneyTakeFrom = TakeFrom.fromString(reqSec.getString("money.take-from", "BANK"));
            this.itemsEnabled = reqSec.getBoolean("items.enabled", false);
            this.itemRequirements = parseItemRequirements(reqSec.getMapList("items.requirements"));
        } else {
            this.permissionEnabled = false;
            this.permissionNode = "guildroyale.mission.start";
            this.moneyEnabled = true;
            this.moneyCost = 1000.0;
            this.moneyTakeFrom = TakeFrom.BANK;
            this.itemsEnabled = false;
            this.itemRequirements = List.of();
        }

        // Rewards & Penalties
        this.rewardXp = sec.getLong("rewards.xp", 5000L);
        this.rewardMoney = sec.getDouble("rewards.money", 2500.0);
        this.penaltyXp = sec.getLong("penalties.xp", 2500L);
        this.penaltyMoney = sec.getDouble("penalties.money", 1000.0);

        // Bossbar
        ConfigurationSection bbSec = sec.getConfigurationSection("bossbar");
        if (bbSec != null) {
            this.bossbarEnabled = bbSec.getBoolean("enabled", true);
            this.bossbarTitle = bbSec.getString("title", "<gradient:#55c57a:#a8e6c1><bold>Guild Mission</bold></gradient> <dark_gray>•</dark_gray> <green><percent>%</green> <dark_gray>(<white><completed_tasks>/<total_tasks> tasks<dark_gray>)</dark_gray> <dark_gray>•</dark_gray> <yellow><time_remaining></yellow>");
            this.bossbarColor = parseBossBarColor(bbSec.getString("color", "GREEN"));
            this.bossbarOverlay = parseBossBarOverlay(bbSec.getString("overlay", "PROGRESS"));
        } else {
            this.bossbarEnabled = true;
            this.bossbarTitle = "<gradient:#55c57a:#a8e6c1><bold>Guild Mission</bold></gradient> <dark_gray>•</dark_gray> <green><percent>%</green> <dark_gray>(<white><completed_tasks>/<total_tasks> tasks<dark_gray>)</dark_gray> <dark_gray>•</dark_gray> <yellow><time_remaining></yellow>";
            this.bossbarColor = BossBar.Color.GREEN;
            this.bossbarOverlay = BossBar.Overlay.PROGRESS;
        }

        // Tasks
        ConfigurationSection tasksSec = sec.getConfigurationSection("tasks");
        if (tasksSec != null) {
            List<MissionTaskDefinition> taskList = new ArrayList<>();
            for (String key : tasksSec.getKeys(false)) {
                ConfigurationSection tSec = tasksSec.getConfigurationSection(key);
                if (tSec == null) continue;
                taskList.add(parseTask(key, tSec));
            }
            this.tasks = Collections.unmodifiableList(taskList.isEmpty() ? defaultTasks() : taskList);
        } else {
            this.tasks = defaultTasks();
        }
    }

    public MissionConfig(
            boolean enabled,
            long durationSeconds,
            CooldownType cooldownType,
            long delaySeconds,
            LocalTime dailyResetTime,
            DayOfWeek weeklyResetDay,
            LocalTime weeklyResetTime,
            ZoneId timezone,
            boolean permissionEnabled,
            String permissionNode,
            boolean moneyEnabled,
            double moneyCost,
            TakeFrom moneyTakeFrom,
            boolean itemsEnabled,
            List<ItemRequirement> itemRequirements,
            long rewardXp,
            double rewardMoney,
            long penaltyXp,
            double penaltyMoney,
            boolean bossbarEnabled,
            String bossbarTitle,
            BossBar.Color bossbarColor,
            BossBar.Overlay bossbarOverlay,
            List<MissionTaskDefinition> tasks
    ) {
        this.enabled = enabled;
        this.durationSeconds = durationSeconds;
        this.cooldownType = cooldownType != null ? cooldownType : CooldownType.DAILY;
        this.delaySeconds = delaySeconds;
        this.dailyResetTime = dailyResetTime != null ? dailyResetTime : LocalTime.of(12, 0);
        this.weeklyResetDay = weeklyResetDay != null ? weeklyResetDay : DayOfWeek.MONDAY;
        this.weeklyResetTime = weeklyResetTime != null ? weeklyResetTime : LocalTime.MIDNIGHT;
        this.timezone = timezone != null ? timezone : ZoneId.of("UTC");
        this.permissionEnabled = permissionEnabled;
        this.permissionNode = permissionNode != null ? permissionNode : "guildroyale.mission.start";
        this.moneyEnabled = moneyEnabled;
        this.moneyCost = moneyCost;
        this.moneyTakeFrom = moneyTakeFrom != null ? moneyTakeFrom : TakeFrom.BANK;
        this.itemsEnabled = itemsEnabled;
        this.itemRequirements = itemRequirements != null ? List.copyOf(itemRequirements) : List.of();
        this.rewardXp = rewardXp;
        this.rewardMoney = rewardMoney;
        this.penaltyXp = penaltyXp;
        this.penaltyMoney = penaltyMoney;
        this.bossbarEnabled = bossbarEnabled;
        this.bossbarTitle = bossbarTitle != null ? bossbarTitle : "";
        this.bossbarColor = bossbarColor != null ? bossbarColor : BossBar.Color.GREEN;
        this.bossbarOverlay = bossbarOverlay != null ? bossbarOverlay : BossBar.Overlay.PROGRESS;
        this.tasks = tasks != null ? List.copyOf(tasks) : defaultTasks();
    }

    private static List<MissionTaskDefinition> defaultTasks() {
        return List.of(
                new MissionTaskDefinition("break_stones", MissionTaskType.BLOCK_BREAK,
                        "<gray>Break Stones", "STONE", 500000L,
                        Map.of("blocks", List.of("STONE", "COBBLESTONE", "DEEPSLATE", "COBBLED_DEEPSLATE"))),
                new MissionTaskDefinition("trade_villagers", MissionTaskType.VILLAGER_TRADE,
                        "<gray>Trade with Villagers", "VILLAGER_SPAWN_EGG", 100000L,
                        Map.of()),
                new MissionTaskDefinition("trade_emeralds", MissionTaskType.VILLAGER_TRADE_ITEM,
                        "<green>Trade Emeralds", "EMERALD", 50000L,
                        Map.of("item", "EMERALD", "mode", "ANY")),
                new MissionTaskDefinition("kill_zombies", MissionTaskType.KILL_ENTITY,
                        "<red>Slay Zombies", "ZOMBIE_HEAD", 10000L,
                        Map.of("entities", List.of("ZOMBIE", "ZOMBIE_VILLAGER", "HUSK", "DROWNED"))),
                new MissionTaskDefinition("craft_pistons", MissionTaskType.CRAFT_ITEM,
                        "<yellow>Craft Pistons", "PISTON", 5000L,
                        Map.of("items", List.of("PISTON", "STICKY_PISTON"))),
                new MissionTaskDefinition("upgrade_claims", MissionTaskType.CUSTOM,
                        "<aqua>Upgrade Claims", "BEACON", 5L,
                        Map.of("custom_id", "upgrade_claim"))
        );
    }

    private MissionTaskDefinition parseTask(String id, ConfigurationSection sec) {
        MissionTaskType type = MissionTaskType.fromString(sec.getString("type", "CUSTOM"));
        String displayName = sec.getString("display-name", sec.getString("name", id));
        String icon = sec.getString("icon", "PAPER");
        long target = sec.getLong("target", 1000L);

        Map<String, Object> props = new HashMap<>();
        for (String k : sec.getKeys(false)) {
            if ("type".equals(k) || "display-name".equals(k) || "name".equals(k)
                    || "icon".equals(k) || "target".equals(k)) {
                continue;
            }
            props.put(k, sec.get(k));
        }
        return new MissionTaskDefinition(id, type, displayName, icon, target, props);
    }

    private static List<ItemRequirement> parseItemRequirements(List<Map<?, ?>> list) {
        if (list == null) return List.of();
        List<ItemRequirement> out = new ArrayList<>();
        for (Map<?, ?> m : list) {
            String mat = Objects.toString(m.get("material"), "");
            int amount = 1;
            Object a = m.get("amount");
            if (a instanceof Number n) amount = n.intValue();
            if (!mat.isBlank() && amount > 0) {
                out.add(new ItemRequirement(mat, amount));
            }
        }
        return Collections.unmodifiableList(out);
    }

    private static LocalTime parseTime(String str, LocalTime def) {
        if (str == null) return def;
        try {
            return LocalTime.parse(str);
        } catch (DateTimeParseException e) {
            return def;
        }
    }

    private static DayOfWeek parseDayOfWeek(String str, DayOfWeek def) {
        if (str == null) return def;
        try {
            return DayOfWeek.valueOf(str.toUpperCase());
        } catch (IllegalArgumentException e) {
            return def;
        }
    }

    private static ZoneId parseZone(String str) {
        if (str == null) return ZoneId.of("UTC");
        try {
            return ZoneId.of(str);
        } catch (Exception e) {
            return ZoneId.of("UTC");
        }
    }

    private static BossBar.Color parseBossBarColor(String str) {
        if (str == null) return BossBar.Color.GREEN;
        try {
            return BossBar.Color.valueOf(str.toUpperCase());
        } catch (IllegalArgumentException e) {
            return BossBar.Color.GREEN;
        }
    }

    private static BossBar.Overlay parseBossBarOverlay(String str) {
        if (str == null) return BossBar.Overlay.PROGRESS;
        try {
            return BossBar.Overlay.valueOf(str.toUpperCase());
        } catch (IllegalArgumentException e) {
            return BossBar.Overlay.PROGRESS;
        }
    }

    public boolean isEnabled() { return enabled; }
    public long getDurationSeconds() { return durationSeconds; }
    public CooldownType getCooldownType() { return cooldownType; }
    public long getDelaySeconds() { return delaySeconds; }
    public LocalTime getDailyResetTime() { return dailyResetTime; }
    public DayOfWeek getWeeklyResetDay() { return weeklyResetDay; }
    public LocalTime getWeeklyResetTime() { return weeklyResetTime; }
    public ZoneId getTimezone() { return timezone; }

    public boolean isPermissionEnabled() { return permissionEnabled; }
    public String getPermissionNode() { return permissionNode; }
    public boolean isMoneyEnabled() { return moneyEnabled; }
    public double getMoneyCost() { return moneyCost; }
    public TakeFrom getMoneyTakeFrom() { return moneyTakeFrom; }
    public boolean isItemsEnabled() { return itemsEnabled; }
    public List<ItemRequirement> getItemRequirements() { return itemRequirements; }

    public long getRewardXp() { return rewardXp; }
    public double getRewardMoney() { return rewardMoney; }
    public long getPenaltyXp() { return penaltyXp; }
    public double getPenaltyMoney() { return penaltyMoney; }

    public boolean isBossbarEnabled() { return bossbarEnabled; }
    public String getBossbarTitle() { return bossbarTitle; }
    public BossBar.Color getBossbarColor() { return bossbarColor; }
    public BossBar.Overlay getBossbarOverlay() { return bossbarOverlay; }

    public List<MissionTaskDefinition> getTasks() { return tasks; }
}
