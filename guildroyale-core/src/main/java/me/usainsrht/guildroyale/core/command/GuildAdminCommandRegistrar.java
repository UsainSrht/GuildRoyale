package me.usainsrht.guildroyale.core.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.Commands;
import me.usainsrht.guildroyale.core.GuildRoyalePlugin;
import me.usainsrht.guildroyale.core.config.CommandConfig;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.jetbrains.annotations.NotNull;

/**
 * Registers the {@code /guildadmin} command tree.
 *
 * <p>The entire tree is guarded by {@link CommandConfig#PERM_ADMIN}
 * ({@code guildroyale.admin}) via the root {@code .requires()} predicate.
 */
@SuppressWarnings("UnstableApiUsage")
public final class GuildAdminCommandRegistrar {

    private GuildAdminCommandRegistrar() {}

    public static void register(@NotNull Commands commands, @NotNull CommandConfig cfg) {
        String cmd      = cfg.adminName();
        MiniMessage mm  = MiniMessage.miniMessage();

        var root = Commands.literal(cmd)
                .requires(src -> src.getSender().hasPermission(CommandConfig.PERM_ADMIN))
                // Show help when /guildadmin is run with no subcommand
                .executes(ctx -> {
                    var sender  = ctx.getSource().getSender();
                    GuildRoyalePlugin plugin = GuildRoyalePlugin.getInstance();

                    if (plugin != null) {
                        plugin.getMessages().send(sender, "guildadmin-help-header",
                                Placeholder.unparsed("cmd", cmd));
                    } else {
                        sender.sendMessage(mm.deserialize(
                                "<gold><bold>GuildRoyale Admin</bold></gold> <dark_gray>—</dark_gray> <yellow>/"
                                        + cmd + " help</yellow>"));
                    }
                    sender.sendMessage(mm.deserialize(
                            "  <gray>/" + cmd + " <yellow>" + cfg.adminSub("reload")));
                    sender.sendMessage(mm.deserialize(
                            "  <gray>/" + cmd + " <yellow>" + cfg.adminSub("addxp")
                                    + " <white><guild> <amount>"));
                    sender.sendMessage(mm.deserialize(
                            "  <gray>/" + cmd + " <yellow>" + cfg.adminSub("setlevel")
                                    + " <white><guild> <level>"));
                    sender.sendMessage(mm.deserialize(
                            "  <gray>/" + cmd + " <yellow>" + cfg.adminSub("delete")
                                    + " <white><guild>"));
                    sender.sendMessage(mm.deserialize(
                            "  <gray>/" + cmd + " <yellow>" + cfg.adminSub("badge")
                                    + " <white><grant|revoke> <guild> <id>"));
                    return 1;
                })
                .then(Commands.literal(cfg.adminSub("reload"))
                        .executes(ctx -> {
                            GuildRoyalePlugin plugin = GuildRoyalePlugin.getInstance();
                            if (plugin == null) return 0;
                            plugin.getConfigManager().reload();
                            plugin.getMessages().reload();
                            plugin.getGuiConfig().reload();
                            plugin.getMessages().send(ctx.getSource().getSender(), "admin-reload");
                            return 1;
                        }))
                .then(Commands.literal(cfg.adminSub("addxp"))
                        .executes(ctx -> {
                            ctx.getSource().getSender().sendMessage(mm.deserialize(
                                    "<red>Usage: <yellow>/" + cmd + " " + cfg.adminSub("addxp") + " <guild> <amount>"));
                            return 0;
                        })
                        .then(Commands.argument("guild", StringArgumentType.word())
                                .executes(ctx -> {
                                    ctx.getSource().getSender().sendMessage(mm.deserialize(
                                            "<red>Usage: <yellow>/" + cmd + " " + cfg.adminSub("addxp") + " <guild> <amount>"));
                                    return 0;
                                })
                                .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                        .executes(ctx -> {
                                            GuildRoyalePlugin plugin = GuildRoyalePlugin.getInstance();
                                            if (plugin == null) return 0;
                                            String guildName = StringArgumentType.getString(ctx, "guild");
                                            long amount = LongArgumentType.getLong(ctx, "amount");
                                            plugin.getScheduler().runAsync(() ->
                                                    plugin.getGuildService().getGuildByName(guildName).thenCompose(opt -> {
                                                        if (opt.isEmpty()) return java.util.concurrent.CompletableFuture.completedFuture(null);
                                                        return plugin.getGuildService().adminAddXp(opt.get().getId(), amount)
                                                                .thenRun(() -> plugin.getMessages().send(ctx.getSource().getSender(), "admin-addxp",
                                                                                Placeholder.unparsed("guild", guildName),
                                                                                Placeholder.unparsed("xp", String.valueOf(amount))));
                                                    })
                                            );
                                            return 1;
                                        }))))
                .then(Commands.literal(cfg.adminSub("setlevel"))
                        .executes(ctx -> {
                            ctx.getSource().getSender().sendMessage(mm.deserialize(
                                    "<red>Usage: <yellow>/" + cmd + " " + cfg.adminSub("setlevel") + " <guild> <level>"));
                            return 0;
                        })
                        .then(Commands.argument("guild", StringArgumentType.word())
                                .executes(ctx -> {
                                    ctx.getSource().getSender().sendMessage(mm.deserialize(
                                            "<red>Usage: <yellow>/" + cmd + " " + cfg.adminSub("setlevel") + " <guild> <level>"));
                                    return 0;
                                })
                                .then(Commands.argument("level", IntegerArgumentType.integer(1))
                                        .executes(ctx -> {
                                            GuildRoyalePlugin plugin = GuildRoyalePlugin.getInstance();
                                            if (plugin == null) return 0;
                                            String guildName = StringArgumentType.getString(ctx, "guild");
                                            int level = IntegerArgumentType.getInteger(ctx, "level");
                                            int cap = plugin.getConfigManager().getLevelCap();
                                            if (cap > 0 && level > cap) {
                                                ctx.getSource().getSender().sendMessage(mm.deserialize(
                                                        "<red>Level must be between 1 and " + cap + "."));
                                                return 0;
                                            }
                                            plugin.getScheduler().runAsync(() ->
                                                    plugin.getGuildService().getGuildByName(guildName).thenCompose(opt -> {
                                                        if (opt.isEmpty()) return java.util.concurrent.CompletableFuture.completedFuture(null);
                                                        return plugin.getGuildService().adminSetLevel(opt.get().getId(), level)
                                                                .thenRun(() -> plugin.getMessages().send(ctx.getSource().getSender(), "admin-setlevel",
                                                                                Placeholder.unparsed("guild", guildName),
                                                                                Placeholder.unparsed("level", String.valueOf(level))));
                                                    })
                                            );
                                            return 1;
                                        }))))
                .then(Commands.literal(cfg.adminSub("badge"))
                        .then(Commands.literal("grant")
                                .then(Commands.argument("guild", StringArgumentType.word())
                                        .then(Commands.argument("id", StringArgumentType.word())
                                                .executes(ctx -> adminBadge(ctx, true)))))
                        .then(Commands.literal("revoke")
                                .then(Commands.argument("guild", StringArgumentType.word())
                                        .then(Commands.argument("id", StringArgumentType.word())
                                                .executes(ctx -> adminBadge(ctx, false)))))
                        .executes(ctx -> {
                            ctx.getSource().getSender().sendMessage(mm.deserialize(
                                    "<red>Usage: <yellow>/" + cmd + " " + cfg.adminSub("badge")
                                            + " <grant|revoke> <guild> <id>"));
                            return 0;
                        }))
                .then(Commands.literal(cfg.adminSub("delete"))
                        .executes(ctx -> {
                            ctx.getSource().getSender().sendMessage(mm.deserialize(
                                    "<red>Usage: <yellow>/" + cmd + " " + cfg.adminSub("delete") + " <guild>"));
                            return 0;
                        })
                        .then(Commands.argument("guild", StringArgumentType.word())
                                .executes(ctx -> {
                                    GuildRoyalePlugin plugin = GuildRoyalePlugin.getInstance();
                                    if (plugin == null) return 0;
                                    String guildName = StringArgumentType.getString(ctx, "guild");
                                    plugin.getScheduler().runAsync(() ->
                                            plugin.getGuildService().getGuildByName(guildName).thenCompose(opt -> {
                                                if (opt.isEmpty()) return java.util.concurrent.CompletableFuture.completedFuture(null);
                                                return plugin.getRepository().delete(opt.get().getId())
                                                        .thenRun(() -> plugin.getMessages().send(ctx.getSource().getSender(), "admin-delete",
                                                                        Placeholder.unparsed("guild", guildName)));
                                            })
                                    );
                                    return 1;
                                })))
                .build();

        commands.register(root, "GuildRoyale admin commands", cfg.adminAliases());
    }

    private static int adminBadge(com.mojang.brigadier.context.CommandContext<
            io.papermc.paper.command.brigadier.CommandSourceStack> ctx, boolean grant) {
        GuildRoyalePlugin plugin = GuildRoyalePlugin.getInstance();
        if (plugin == null) return 0;
        String guildName = StringArgumentType.getString(ctx, "guild");
        String badgeId = StringArgumentType.getString(ctx, "id");
        plugin.getScheduler().runAsync(() ->
                plugin.getGuildService().getGuildByName(guildName).thenCompose(opt -> {
                    if (opt.isEmpty()) {
                        plugin.getMessages().send(ctx.getSource().getSender(), "invalid-guild");
                        return java.util.concurrent.CompletableFuture.completedFuture(null);
                    }
                    var future = grant
                            ? plugin.getGuildService().adminGrantBadge(opt.get().getId(), badgeId)
                            : plugin.getGuildService().adminRevokeBadge(opt.get().getId(), badgeId);
                    return future.thenAccept(result -> {
                        if (result.isSuccess()) {
                            plugin.getMessages().send(ctx.getSource().getSender(),
                                    grant ? "admin-badge-grant" : "admin-badge-revoke",
                                    Placeholder.unparsed("guild", guildName),
                                    Placeholder.unparsed("badge", badgeId));
                        } else {
                            plugin.getMessages().send(ctx.getSource().getSender(), result.failureReasonOrEmpty());
                        }
                    });
                })
        );
        return 1;
    }
}
