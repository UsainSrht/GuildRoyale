package me.usainsrht.guildroyale.core.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads command-name configuration from {@code config.yml} at bootstrap time.
 *
 * <p>Supports both legacy string labels and the richer form:
 * <pre>
 * create: create
 * bank:
 *   name: bank
 *   aliases: [b]
 *   children:
 *     deposit:
 *       name: deposit
 *       aliases: [dep]
 * </pre>
 *
 * <p>Permission nodes are hardcoded and never change when labels are renamed.
 */
public final class CommandConfig {

    public static final String PERM_CREATE      = "guildroyale.command.create";
    public static final String PERM_DISBAND     = "guildroyale.command.disband";
    public static final String PERM_INFO        = "guildroyale.command.info";
    public static final String PERM_INVITE      = "guildroyale.command.invite";
    public static final String PERM_JOIN        = "guildroyale.command.join";
    public static final String PERM_LEAVE       = "guildroyale.command.leave";
    public static final String PERM_KICK        = "guildroyale.command.kick";
    public static final String PERM_ROLE        = "guildroyale.command.role";
    public static final String PERM_ICON        = "guildroyale.command.icon";
    public static final String PERM_SHORTNAME   = "guildroyale.command.shortname";
    public static final String PERM_LEADERBOARD = "guildroyale.command.leaderboard";
    public static final String PERM_LEADER      = "guildroyale.command.leader";
    public static final String PERM_MENU        = "guildroyale.command.menu";
    public static final String PERM_BADGE       = "guildroyale.command.badge";
    public static final String PERM_STORAGE     = "guildroyale.command.storage";
    public static final String PERM_BANK        = "guildroyale.command.bank";
    public static final String PERM_HELP        = "guildroyale.command.help";
    public static final String PERM_ADMIN       = "guildroyale.admin";

    /** A resolved command/subcommand label with optional aliases and nested children. */
    public record Spec(String name, List<String> aliases, Map<String, Spec> children) {
        public Spec {
            aliases = aliases == null ? List.of() : List.copyOf(aliases);
            children = children == null ? Map.of() : Map.copyOf(children);
        }

        public static Spec of(String name) {
            return new Spec(name, List.of(), Map.of());
        }

        public Spec child(String key, String defaultName) {
            Spec child = children.get(key);
            return child != null ? child : of(defaultName);
        }
    }

    private final String guildName;
    private final List<String> guildAliases;
    private final Map<String, Spec> guildSubcommands;

    private final String adminName;
    private final List<String> adminAliases;
    private final Map<String, Spec> adminSubcommands;

    private CommandConfig(
            String guildName, List<String> guildAliases, Map<String, Spec> guildSubcommands,
            String adminName, List<String> adminAliases, Map<String, Spec> adminSubcommands) {
        this.guildName = guildName;
        this.guildAliases = List.copyOf(guildAliases);
        this.guildSubcommands = Map.copyOf(guildSubcommands);
        this.adminName = adminName;
        this.adminAliases = List.copyOf(adminAliases);
        this.adminSubcommands = Map.copyOf(adminSubcommands);
    }

    public static CommandConfig load(Path dataDirectory, ClassLoader resourceLoader) {
        YamlConfiguration cfg = new YamlConfiguration();

        File file = dataDirectory.resolve("config.yml").toFile();
        if (file.exists()) {
            cfg = YamlConfiguration.loadConfiguration(file);
        }

        InputStream defaultStream = resourceLoader.getResourceAsStream("config.yml");
        if (defaultStream != null) {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
            cfg.setDefaults(defaults);
        }

        String guildName = cfg.getString("commands.guild.name", "guild");
        List<String> guildAliases = cfg.getStringList("commands.guild.aliases");
        if (guildAliases.isEmpty()) guildAliases = List.of("g");

        Map<String, Spec> guildSubs = new HashMap<>();
        guildSubs.put("create",      readSpec(cfg, "commands.guild.subcommands.create", "create"));
        guildSubs.put("disband",     readSpec(cfg, "commands.guild.subcommands.disband", "disband"));
        guildSubs.put("info",        readSpec(cfg, "commands.guild.subcommands.info", "info"));
        guildSubs.put("invite",      readSpec(cfg, "commands.guild.subcommands.invite", "invite"));
        guildSubs.put("join",        readSpec(cfg, "commands.guild.subcommands.join", "join"));
        guildSubs.put("leave",       readSpec(cfg, "commands.guild.subcommands.leave", "leave"));
        guildSubs.put("kick",        readSpec(cfg, "commands.guild.subcommands.kick", "kick"));
        guildSubs.put("role",        readSpec(cfg, "commands.guild.subcommands.role", "role"));
        guildSubs.put("icon",        readSpec(cfg, "commands.guild.subcommands.icon", "icon"));
        guildSubs.put("shortname",   readSpec(cfg, "commands.guild.subcommands.shortname", "shortname"));
        guildSubs.put("leaderboard", readSpec(cfg, "commands.guild.subcommands.leaderboard", "leaderboard"));
        guildSubs.put("leader",      readSpec(cfg, "commands.guild.subcommands.leader", "leader"));
        guildSubs.put("menu",        readSpec(cfg, "commands.guild.subcommands.menu", "menu"));
        guildSubs.put("badge",       readSpec(cfg, "commands.guild.subcommands.badge", "badge"));
        guildSubs.put("storage",     readSpec(cfg, "commands.guild.subcommands.storage", "storage"));
        guildSubs.put("bank",        readSpec(cfg, "commands.guild.subcommands.bank", "bank"));
        guildSubs.put("help",        readSpec(cfg, "commands.guild.subcommands.help", "help"));

        String adminName = cfg.getString("commands.admin.name", "guildadmin");
        List<String> adminAliases = cfg.getStringList("commands.admin.aliases");
        if (adminAliases.isEmpty()) adminAliases = List.of("ga");

        Map<String, Spec> adminSubs = new HashMap<>();
        adminSubs.put("reload",   readSpec(cfg, "commands.admin.subcommands.reload", "reload"));
        adminSubs.put("addxp",    readSpec(cfg, "commands.admin.subcommands.addxp", "addxp"));
        adminSubs.put("setlevel", readSpec(cfg, "commands.admin.subcommands.setlevel", "setlevel"));
        adminSubs.put("delete",   readSpec(cfg, "commands.admin.subcommands.delete", "delete"));
        adminSubs.put("badge",    readSpec(cfg, "commands.admin.subcommands.badge", "badge"));

        return new CommandConfig(guildName, guildAliases, guildSubs, adminName, adminAliases, adminSubs);
    }

    private static Spec readSpec(YamlConfiguration cfg, String path, String defaultName) {
        if (cfg.isConfigurationSection(path)) {
            ConfigurationSection section = cfg.getConfigurationSection(path);
            if (section == null) {
                return Spec.of(defaultName);
            }
            String name = section.getString("name", defaultName);
            List<String> aliases = section.getStringList("aliases");
            Map<String, Spec> children = new HashMap<>();
            ConfigurationSection childSection = section.getConfigurationSection("children");
            if (childSection != null) {
                for (String key : childSection.getKeys(false)) {
                    children.put(key, readSpecFromSection(childSection, key, key));
                }
            }
            return new Spec(name, aliases, children);
        }
        return Spec.of(cfg.getString(path, defaultName));
    }

    private static Spec readSpecFromSection(ConfigurationSection parent, String key, String defaultName) {
        String path = key;
        if (parent.isConfigurationSection(path)) {
            ConfigurationSection section = parent.getConfigurationSection(path);
            if (section == null) {
                return Spec.of(defaultName);
            }
            String name = section.getString("name", defaultName);
            List<String> aliases = section.getStringList("aliases");
            Map<String, Spec> children = new HashMap<>();
            ConfigurationSection childSection = section.getConfigurationSection("children");
            if (childSection != null) {
                for (String childKey : childSection.getKeys(false)) {
                    children.put(childKey, readSpecFromSection(childSection, childKey, childKey));
                }
            }
            return new Spec(name, aliases, children);
        }
        return Spec.of(parent.getString(path, defaultName));
    }

    public String guildName() { return guildName; }
    public List<String> guildAliases() { return guildAliases; }

    public Spec guildSubSpec(String key) {
        return guildSubcommands.getOrDefault(key, Spec.of(key));
    }

    /** Resolved primary label for a guild subcommand key. */
    public String guildSub(String key) {
        return guildSubSpec(key).name();
    }

    public List<String> guildSubAliases(String key) {
        return guildSubSpec(key).aliases();
    }

    public String adminName() { return adminName; }
    public List<String> adminAliases() { return adminAliases; }

    public Spec adminSubSpec(String key) {
        return adminSubcommands.getOrDefault(key, Spec.of(key));
    }

    public String adminSub(String key) {
        return adminSubSpec(key).name();
    }

    public List<String> adminSubAliases(String key) {
        return adminSubSpec(key).aliases();
    }

    public Map<String, Spec> guildSubcommands() {
        return Collections.unmodifiableMap(guildSubcommands);
    }
}
