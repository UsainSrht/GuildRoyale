package me.usainsrht.guildroyale.core.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.Commands;
import me.usainsrht.guildroyale.core.GuildRoyalePlugin;
import me.usainsrht.guildroyale.core.command.subcommand.*;
import me.usainsrht.guildroyale.core.config.CommandConfig;
import me.usainsrht.guildroyale.core.gui.impl.GuildHubGui;
import me.usainsrht.guildroyale.core.gui.impl.GuildMainGui;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registers the full {@code /guild} command tree via Brigadier.
 *
 * <p>Bare {@code /guild} opens the hub/main menu. Use {@code /guild help} for usage.
 */
@SuppressWarnings("UnstableApiUsage")
public final class GuildCommandRegistrar {

    private GuildCommandRegistrar() {}

    public static void register(@NotNull Commands commands, @NotNull CommandConfig cfg) {
        String cmd = cfg.guildName();

        LiteralArgumentBuilder<io.papermc.paper.command.brigadier.CommandSourceStack> root =
                Commands.literal(cmd)
                        .executes(ctx -> {
                            if (!(ctx.getSource().getSender() instanceof Player player)) {
                                sendHelp(ctx.getSource().getSender(), cfg);
                                return 1;
                            }
                            openMenu(player);
                            return 1;
                        });

        CommandNodes.attach(root, CreateSubcommand.node(cfg.guildSub("create")), cfg.guildSubAliases("create"));
        CommandNodes.attach(root, DisbandSubcommand.node(cfg.guildSub("disband")), cfg.guildSubAliases("disband"));
        CommandNodes.attach(root, InfoSubcommand.node(cfg.guildSub("info")), cfg.guildSubAliases("info"));
        CommandNodes.attach(root, InviteSubcommand.node(cfg.guildSub("invite"), cmd), cfg.guildSubAliases("invite"));
        CommandNodes.attach(root, JoinSubcommand.node(cfg.guildSub("join"), cmd), cfg.guildSubAliases("join"));
        CommandNodes.attach(root, LeaveSubcommand.node(cfg.guildSub("leave")), cfg.guildSubAliases("leave"));
        CommandNodes.attach(root, KickSubcommand.node(cfg.guildSub("kick"), cmd), cfg.guildSubAliases("kick"));
        CommandNodes.attach(root, RoleSubcommand.node(cfg.guildSubSpec("role"), cmd), cfg.guildSubAliases("role"));
        CommandNodes.attach(root, IconSubcommand.node(cfg.guildSub("icon")), cfg.guildSubAliases("icon"));
        CommandNodes.attach(root, ShortnameSubcommand.node(cfg.guildSub("shortname")), cfg.guildSubAliases("shortname"));
        CommandNodes.attach(root, LeaderboardSubcommand.node(cfg.guildSub("leaderboard")), cfg.guildSubAliases("leaderboard"));
        CommandNodes.attach(root, LeaderSubcommand.node(cfg.guildSub("leader"), cmd), cfg.guildSubAliases("leader"));
        CommandNodes.attach(root, MenuSubcommand.node(cfg.guildSub("menu")), cfg.guildSubAliases("menu"));
        CommandNodes.attach(root, BadgeSubcommand.node(cfg.guildSubSpec("badge")), cfg.guildSubAliases("badge"));
        CommandNodes.attach(root, StorageSubcommand.node(cfg.guildSub("storage")), cfg.guildSubAliases("storage"));
        CommandNodes.attach(root, BankSubcommand.node(cfg.guildSubSpec("bank")), cfg.guildSubAliases("bank"));
        CommandNodes.attach(root, MissionSubcommand.node(cfg.guildSub("mission")), cfg.guildSubAliases("mission"));

        // help
        LiteralCommandNode<io.papermc.paper.command.brigadier.CommandSourceStack> helpNode =
                Commands.literal(cfg.guildSub("help"))
                        .requires(src -> src.getSender().hasPermission(CommandConfig.PERM_HELP)
                                || src.getSender().hasPermission(CommandConfig.PERM_MENU))
                        .executes(ctx -> {
                            sendHelp(ctx.getSource().getSender(), cfg);
                            return 1;
                        })
                        .build();
        root.then(helpNode);
        for (String alias : cfg.guildSubAliases("help")) {
            if (!alias.equalsIgnoreCase(helpNode.getLiteral())) {
                root.then(Commands.literal(alias).redirect(helpNode));
            }
        }

        commands.register(root.build(), "Manage your guild", cfg.guildAliases());
    }

    /** Opens hub (no guild) or main menu (in guild). */
    public static void openMenu(Player player) {
        GuildRoyalePlugin plugin = GuildRoyalePlugin.getInstance();
        if (plugin == null) return;

        plugin.getScheduler().runAsync(() ->
                plugin.getGuildService().getGuildByMember(player.getUniqueId()).thenAccept(opt ->
                        plugin.getScheduler().runForEntity(player, () -> {
                            if (opt.isEmpty()) {
                                new GuildHubGui(player, false, plugin.getGuiManager()).open(player);
                                return;
                            }
                            var guild = opt.get();
                            var memberOpt = guild.getMember(player.getUniqueId());
                            if (memberOpt.isEmpty()) {
                                new GuildHubGui(player, false, plugin.getGuiManager()).open(player);
                                return;
                            }
                            new GuildMainGui(guild, memberOpt.get(), plugin.getGuiManager()).open(player);
                        })
                )
        );
    }

    private static final Map<String, String> SUB_USAGE;
    static {
        SUB_USAGE = new LinkedHashMap<>();
        SUB_USAGE.put("create", "");
        SUB_USAGE.put("disband", "");
        SUB_USAGE.put("info", "");
        SUB_USAGE.put("invite", "<player>");
        SUB_USAGE.put("join", "<guildName>");
        SUB_USAGE.put("leave", "");
        SUB_USAGE.put("kick", "<player>");
        SUB_USAGE.put("role", "<create|delete|rename|setpermission>");
        SUB_USAGE.put("icon", "");
        SUB_USAGE.put("shortname", "");
        SUB_USAGE.put("leaderboard", "");
        SUB_USAGE.put("leader", "<player>");
        SUB_USAGE.put("menu", "");
        SUB_USAGE.put("badge", "<list|buy|equip>");
        SUB_USAGE.put("storage", "");
        SUB_USAGE.put("bank", "<balance|deposit|withdraw>");
        SUB_USAGE.put("mission", "");
        SUB_USAGE.put("help", "");
    }

    private static void sendHelp(org.bukkit.command.CommandSender sender, CommandConfig cfg) {
        GuildRoyalePlugin plugin = GuildRoyalePlugin.getInstance();
        if (plugin != null) {
            plugin.getMessages().send(sender, "guild-help-header",
                    Placeholder.unparsed("cmd", cfg.guildName()));
        }

        for (var entry : SUB_USAGE.entrySet()) {
            String key = entry.getKey();
            String args = entry.getValue();
            String perm = permFor(key);
            if (perm != null && !sender.hasPermission(perm) && !key.equals("help")) continue;

            String label = cfg.guildSub(key);
            StringBuilder line = new StringBuilder("  <gray>/")
                    .append(cfg.guildName())
                    .append(" <yellow>")
                    .append(label);
            if (!args.isEmpty()) {
                line.append(" <white>").append(args);
            }
            var aliases = cfg.guildSubAliases(key);
            if (!aliases.isEmpty()) {
                line.append(" <dark_gray>(").append(String.join(", ", aliases)).append(")");
            }
            sender.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                    .deserialize(line.toString()));
        }
    }

    private static String permFor(String key) {
        return switch (key) {
            case "create" -> CommandConfig.PERM_CREATE;
            case "disband" -> CommandConfig.PERM_DISBAND;
            case "info" -> CommandConfig.PERM_INFO;
            case "invite" -> CommandConfig.PERM_INVITE;
            case "join" -> CommandConfig.PERM_JOIN;
            case "leave" -> CommandConfig.PERM_LEAVE;
            case "kick" -> CommandConfig.PERM_KICK;
            case "role" -> CommandConfig.PERM_ROLE;
            case "icon" -> CommandConfig.PERM_ICON;
            case "shortname" -> CommandConfig.PERM_SHORTNAME;
            case "leaderboard" -> CommandConfig.PERM_LEADERBOARD;
            case "leader" -> CommandConfig.PERM_LEADER;
            case "menu" -> CommandConfig.PERM_MENU;
            case "badge" -> CommandConfig.PERM_BADGE;
            case "storage" -> CommandConfig.PERM_STORAGE;
            case "bank" -> CommandConfig.PERM_BANK;
            case "mission" -> CommandConfig.PERM_MISSION;
            case "help" -> CommandConfig.PERM_HELP;
            default -> null;
        };
    }
}
