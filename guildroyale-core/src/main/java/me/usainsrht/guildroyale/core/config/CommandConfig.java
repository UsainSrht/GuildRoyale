package me.usainsrht.guildroyale.core.config;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Reads command-name configuration from {@code config.yml} at bootstrap time,
 * before the plugin instance is available.
 *
 * <p><b>Note:</b> command names (and aliases) are registered with Brigadier
 * at server start-up. Changes require a full server restart to take effect.
 *
 * <p>Permission nodes are <em>hardcoded</em> and never change regardless of
 * the configured label:
 * <ul>
 *   <li>{@code guildroyale.command.create}</li>
 *   <li>{@code guildroyale.command.disband}</li>
 *   <li>{@code guildroyale.command.info}</li>
 *   <li>{@code guildroyale.command.invite}</li>
 *   <li>{@code guildroyale.command.join}</li>
 *   <li>{@code guildroyale.command.leave}</li>
 *   <li>{@code guildroyale.command.kick}</li>
 *   <li>{@code guildroyale.command.role}</li>
 *   <li>{@code guildroyale.command.icon}</li>
 *   <li>{@code guildroyale.command.shortname}</li>
 *   <li>{@code guildroyale.command.leaderboard}</li>
 *   <li>{@code guildroyale.command.leader}</li>
 *   <li>{@code guildroyale.command.menu}</li>
 *   <li>{@code guildroyale.admin} — guards the entire admin command tree</li>
 * </ul>
 */
public final class CommandConfig {

    // ── Hardcoded permission nodes ───────────────────────────────
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
    public static final String PERM_ADMIN       = "guildroyale.admin";

    // ── Resolved names ───────────────────────────────────────────
    private final String guildName;
    private final List<String> guildAliases;
    private final Map<String, String> guildSubcommands;

    private final String adminName;
    private final List<String> adminAliases;
    private final Map<String, String> adminSubcommands;

    private CommandConfig(
            String guildName, List<String> guildAliases, Map<String, String> guildSubcommands,
            String adminName, List<String> adminAliases, Map<String, String> adminSubcommands) {
        this.guildName = guildName;
        this.guildAliases = guildAliases;
        this.guildSubcommands = guildSubcommands;
        this.adminName = adminName;
        this.adminAliases = adminAliases;
        this.adminSubcommands = adminSubcommands;
    }

    /**
     * Reads command configuration from {@code config.yml} inside the plugin data directory.
     * Falls back to bundled defaults when the file does not yet exist.
     *
     * @param dataDirectory the plugin data folder path (from {@code BootstrapContext.getDataDirectory()})
     * @param resourceLoader class-loader used to locate the bundled {@code config.yml}
     */
    public static CommandConfig load(Path dataDirectory, ClassLoader resourceLoader) {
        YamlConfiguration cfg = new YamlConfiguration();

        File file = dataDirectory.resolve("config.yml").toFile();
        if (file.exists()) {
            cfg = YamlConfiguration.loadConfiguration(file);
        }

        // Merge bundled defaults so every key has a value
        InputStream defaultStream = resourceLoader.getResourceAsStream("config.yml");
        if (defaultStream != null) {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
            cfg.setDefaults(defaults);
        }

        // ── Guild ────────────────────────────────────────────────
        String guildName = cfg.getString("commands.guild.name", "guild");

        List<String> guildAliases = cfg.getStringList("commands.guild.aliases");
        if (guildAliases.isEmpty()) guildAliases = List.of("g");

        Map<String, String> guildSubs = Map.ofEntries(
                entry(cfg, "commands.guild.subcommands.create",      "create"),
                entry(cfg, "commands.guild.subcommands.disband",     "disband"),
                entry(cfg, "commands.guild.subcommands.info",        "info"),
                entry(cfg, "commands.guild.subcommands.invite",      "invite"),
                entry(cfg, "commands.guild.subcommands.join",        "join"),
                entry(cfg, "commands.guild.subcommands.leave",       "leave"),
                entry(cfg, "commands.guild.subcommands.kick",        "kick"),
                entry(cfg, "commands.guild.subcommands.role",        "role"),
                entry(cfg, "commands.guild.subcommands.icon",        "icon"),
                entry(cfg, "commands.guild.subcommands.shortname",   "shortname"),
                entry(cfg, "commands.guild.subcommands.leaderboard", "leaderboard"),
                entry(cfg, "commands.guild.subcommands.leader",      "leader"),
                entry(cfg, "commands.guild.subcommands.menu",        "menu")
        );

        // ── Admin ────────────────────────────────────────────────
        String adminName = cfg.getString("commands.admin.name", "guildadmin");

        List<String> adminAliases = cfg.getStringList("commands.admin.aliases");
        if (adminAliases.isEmpty()) adminAliases = List.of("ga");

        Map<String, String> adminSubs = Map.ofEntries(
                entry(cfg, "commands.admin.subcommands.reload",   "reload"),
                entry(cfg, "commands.admin.subcommands.addxp",    "addxp"),
                entry(cfg, "commands.admin.subcommands.setlevel", "setlevel"),
                entry(cfg, "commands.admin.subcommands.delete",   "delete")
        );

        return new CommandConfig(guildName, guildAliases, guildSubs, adminName, adminAliases, adminSubs);
    }

    // ── Accessors ────────────────────────────────────────────────

    /** The configured main {@code /guild}-style command name. */
    public String guildName()           { return guildName; }
    /** Aliases for the guild command. */
    public List<String> guildAliases()  { return guildAliases; }
    /** Resolved label for a guild subcommand key (e.g. {@code "create"}). */
    public String guildSub(String key)  { return guildSubcommands.getOrDefault(key, key); }

    /** The configured main {@code /guildadmin}-style command name. */
    public String adminName()           { return adminName; }
    /** Aliases for the admin command. */
    public List<String> adminAliases()  { return adminAliases; }
    /** Resolved label for an admin subcommand key (e.g. {@code "reload"}). */
    public String adminSub(String key)  { return adminSubcommands.getOrDefault(key, key); }

    // ── Helpers ──────────────────────────────────────────────────

    private static Map.Entry<String, String> entry(YamlConfiguration cfg, String path, String def) {
        return Map.entry(def, cfg.getString(path, def));
    }
}

