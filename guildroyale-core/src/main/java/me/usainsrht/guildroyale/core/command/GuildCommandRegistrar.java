package me.usainsrht.guildroyale.core.command;

import io.papermc.paper.command.brigadier.Commands;
import me.usainsrht.guildroyale.core.bootstrap.GuildRoyaleBootstrap;
import me.usainsrht.guildroyale.core.command.subcommand.*;
import me.usainsrht.guildroyale.core.config.CommandConfig;
import me.usainsrht.guildroyale.core.GuildRoyalePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registers the full {@code /guild} command tree via Brigadier.
 *
 * <p>Called from {@link GuildRoyaleBootstrap} during
 * the {@code COMMANDS} lifecycle event.
 */
@SuppressWarnings("UnstableApiUsage")
public final class GuildCommandRegistrar {

    private GuildCommandRegistrar() {}

    public static void register(@NotNull Commands commands, @NotNull CommandConfig cfg) {
        String cmd = cfg.guildName();

        var root = Commands.literal(cmd)
                // Show help when /guild is run with no subcommand
                .executes(ctx -> {
                    sendHelp(ctx.getSource().getSender(), cfg);
                    return 1;
                })
                .then(CreateSubcommand.node(cfg.guildSub("create")))
                .then(DisbandSubcommand.node(cfg.guildSub("disband")))
                .then(InfoSubcommand.node(cfg.guildSub("info")))
                .then(InviteSubcommand.node(cfg.guildSub("invite"), cmd))
                .then(JoinSubcommand.node(cfg.guildSub("join"), cmd))
                .then(LeaveSubcommand.node(cfg.guildSub("leave")))
                .then(KickSubcommand.node(cfg.guildSub("kick"), cmd))
                .then(RoleSubcommand.node(cfg.guildSub("role"), cmd))
                .then(IconSubcommand.node(cfg.guildSub("icon")))
                .then(ShortnameSubcommand.node(cfg.guildSub("shortname")))
                .then(LeaderboardSubcommand.node(cfg.guildSub("leaderboard")))
                .then(LeaderSubcommand.node(cfg.guildSub("leader"), cmd))
                .then(MenuSubcommand.node(cfg.guildSub("menu")))
                .build();

        commands.register(root, "Manage your guild", cfg.guildAliases());
    }

    // ── Help display ─────────────────────────────────────────────

    /** Subcommand keys → argument hint, in display order. */
    private static final Map<String, String> SUB_USAGE;
    static {
        SUB_USAGE = new LinkedHashMap<>();
        SUB_USAGE.put("create",      "");
        SUB_USAGE.put("disband",     "");
        SUB_USAGE.put("info",        "");
        SUB_USAGE.put("invite",      "<player>");
        SUB_USAGE.put("join",        "<guildName>");
        SUB_USAGE.put("leave",       "");
        SUB_USAGE.put("kick",        "<player>");
        SUB_USAGE.put("role",        "<create|delete|rename|setpermission>");
        SUB_USAGE.put("icon",        "");
        SUB_USAGE.put("shortname",   "");
        SUB_USAGE.put("leaderboard", "");
        SUB_USAGE.put("leader",      "<player>");
        SUB_USAGE.put("menu",        "");
    }

    private static void sendHelp(org.bukkit.command.CommandSender sender, CommandConfig cfg) {
        GuildRoyalePlugin plugin = GuildRoyalePlugin.getInstance();
        MiniMessage mm = MiniMessage.miniMessage();

        // Header
        Component header = (plugin != null)
                ? plugin.getMessages().prefixed("guild-help-header",
                        Placeholder.unparsed("cmd", cfg.guildName()))
                : mm.deserialize("<gold><bold>GuildRoyale</bold></gold> <dark_gray>—</dark_gray> <yellow>/"
                        + cfg.guildName() + " help</yellow>");
        sender.sendMessage(header);

        // One line per accessible subcommand
        for (var entry : SUB_USAGE.entrySet()) {
            String key  = entry.getKey();
            String args = entry.getValue();
            String perm = permFor(key);
            if (perm != null && !sender.hasPermission(perm)) continue;

            String label = cfg.guildSub(key);
            String line  = "  <gray>/" + cfg.guildName() + " <yellow>" + label
                    + (args.isEmpty() ? "" : " <white>" + args);
            sender.sendMessage(mm.deserialize(line));
        }
    }

    private static String permFor(String key) {
        return switch (key) {
            case "create"      -> CommandConfig.PERM_CREATE;
            case "disband"     -> CommandConfig.PERM_DISBAND;
            case "info"        -> CommandConfig.PERM_INFO;
            case "invite"      -> CommandConfig.PERM_INVITE;
            case "join"        -> CommandConfig.PERM_JOIN;
            case "leave"       -> CommandConfig.PERM_LEAVE;
            case "kick"        -> CommandConfig.PERM_KICK;
            case "role"        -> CommandConfig.PERM_ROLE;
            case "icon"        -> CommandConfig.PERM_ICON;
            case "shortname"   -> CommandConfig.PERM_SHORTNAME;
            case "leaderboard" -> CommandConfig.PERM_LEADERBOARD;
            case "leader"      -> CommandConfig.PERM_LEADER;
            case "menu"        -> CommandConfig.PERM_MENU;
            default            -> null;
        };
    }
}
