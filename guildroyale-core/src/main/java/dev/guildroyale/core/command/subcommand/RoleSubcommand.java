package dev.guildroyale.core.command.subcommand;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.entity.Player;

/**
 * {@code /guild role} subtree:
 * <ul>
 *   <li>{@code /guild role create <name>}</li>
 *   <li>{@code /guild role delete <index>}</li>
 *   <li>{@code /guild role rename <index> <name>}</li>
 *   <li>{@code /guild role setpermission <index> <permission> <true|false>}</li>
 *   <li>{@code /guild role seticon <index>}</li>
 * </ul>
 */
@SuppressWarnings("UnstableApiUsage")
public final class RoleSubcommand {

    private RoleSubcommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("role")
                .then(Commands.literal("create")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> executeCreate(ctx, StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("delete")
                        .then(Commands.argument("index", IntegerArgumentType.integer(1))
                                .executes(ctx -> executeDelete(ctx, IntegerArgumentType.getInteger(ctx, "index")))))
                .then(Commands.literal("rename")
                        .then(Commands.argument("index", IntegerArgumentType.integer(1))
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(ctx -> executeRename(ctx,
                                                IntegerArgumentType.getInteger(ctx, "index"),
                                                StringArgumentType.getString(ctx, "name"))))))
                .then(Commands.literal("setpermission")
                        .then(Commands.argument("index", IntegerArgumentType.integer(1))
                                .then(Commands.argument("permission", StringArgumentType.word())
                                        .executes(ctx -> executeTogglePerm(ctx,
                                                IntegerArgumentType.getInteger(ctx, "index"),
                                                StringArgumentType.getString(ctx, "permission"))))));
    }

    private static int executeCreate(CommandContext<CommandSourceStack> ctx, String name) {
        if (!(ctx.getSource().getSender() instanceof Player player)) return 0;
        dev.guildroyale.core.GuildRoyalePlugin plugin = dev.guildroyale.core.GuildRoyalePlugin.getInstance();
        if (plugin == null) return 0;

        plugin.getScheduler().runAsync(() ->
                plugin.getGuildService().getGuildByMember(player.getUniqueId()).thenCompose(opt -> {
                    if (opt.isEmpty()) return java.util.concurrent.CompletableFuture.completedFuture(null);
                    return plugin.getRoleService().createRole(opt.get().getId(), player.getUniqueId(), name)
                            .thenAccept(result -> plugin.getScheduler().runForEntity(player, () -> {
                                switch (result) {
                                    case dev.guildroyale.api.service.ActionResult.Success s ->
                                            player.sendMessage(plugin.getMessages().prefixed("role-created",
                                                    net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("role", name)));
                                    case dev.guildroyale.api.service.ActionResult.Failure f ->
                                            player.sendMessage(plugin.getMessages().prefixed(f.reason()));
                                }
                            }));
                })
        );
        return 1;
    }

    private static int executeDelete(CommandContext<CommandSourceStack> ctx, int index) {
        if (!(ctx.getSource().getSender() instanceof Player player)) return 0;
        dev.guildroyale.core.GuildRoyalePlugin plugin = dev.guildroyale.core.GuildRoyalePlugin.getInstance();
        if (plugin == null) return 0;

        plugin.getScheduler().runAsync(() ->
                plugin.getGuildService().getGuildByMember(player.getUniqueId()).thenCompose(opt -> {
                    if (opt.isEmpty()) return java.util.concurrent.CompletableFuture.completedFuture(null);
                    return plugin.getRoleService().deleteRole(opt.get().getId(), player.getUniqueId(), index)
                            .thenAccept(result -> plugin.getScheduler().runForEntity(player, () -> {
                                switch (result) {
                                    case dev.guildroyale.api.service.ActionResult.Success s ->
                                            player.sendMessage(plugin.getMessages().prefixed("role-deleted",
                                                    net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("role", String.valueOf(index))));
                                    case dev.guildroyale.api.service.ActionResult.Failure f ->
                                            player.sendMessage(plugin.getMessages().prefixed(f.reason()));
                                }
                            }));
                })
        );
        return 1;
    }

    private static int executeRename(CommandContext<CommandSourceStack> ctx, int index, String name) {
        if (!(ctx.getSource().getSender() instanceof Player player)) return 0;
        dev.guildroyale.core.GuildRoyalePlugin plugin = dev.guildroyale.core.GuildRoyalePlugin.getInstance();
        if (plugin == null) return 0;

        plugin.getScheduler().runAsync(() ->
                plugin.getGuildService().getGuildByMember(player.getUniqueId()).thenCompose(opt -> {
                    if (opt.isEmpty()) return java.util.concurrent.CompletableFuture.completedFuture(null);
                    return plugin.getRoleService().renameRole(opt.get().getId(), player.getUniqueId(), index, name)
                            .thenAccept(result -> plugin.getScheduler().runForEntity(player, () -> {
                                switch (result) {
                                    case dev.guildroyale.api.service.ActionResult.Success s ->
                                            player.sendMessage(plugin.getMessages().prefixed("role-renamed",
                                                    net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("role", name)));
                                    case dev.guildroyale.api.service.ActionResult.Failure f ->
                                            player.sendMessage(plugin.getMessages().prefixed(f.reason()));
                                }
                            }));
                })
        );
        return 1;
    }

    private static int executeTogglePerm(CommandContext<CommandSourceStack> ctx, int index, String permName) {
        if (!(ctx.getSource().getSender() instanceof Player player)) return 0;
        dev.guildroyale.core.GuildRoyalePlugin plugin = dev.guildroyale.core.GuildRoyalePlugin.getInstance();
        if (plugin == null) return 0;

        dev.guildroyale.api.permission.GuildPermissionKey key;
        try {
            key = dev.guildroyale.api.permission.GuildPermissionKey.valueOf(permName.toUpperCase());
        } catch (IllegalArgumentException e) {
            player.sendMessage(net.kyori.adventure.text.Component.text("Unknown permission: " + permName,
                    net.kyori.adventure.text.format.NamedTextColor.RED));
            return 0;
        }

        plugin.getScheduler().runAsync(() ->
                plugin.getGuildService().getGuildByMember(player.getUniqueId()).thenCompose(opt -> {
                    if (opt.isEmpty()) return java.util.concurrent.CompletableFuture.completedFuture(null);
                    return plugin.getRoleService().togglePermission(opt.get().getId(), player.getUniqueId(), index, key)
                            .thenAccept(result -> plugin.getScheduler().runForEntity(player, () -> {
                                switch (result) {
                                    case dev.guildroyale.api.service.ActionResult.Success s ->
                                            player.sendMessage(plugin.getMessages().prefixed("permission-toggled",
                                                    net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("role", String.valueOf(index))));
                                    case dev.guildroyale.api.service.ActionResult.Failure f ->
                                            player.sendMessage(plugin.getMessages().prefixed(f.reason()));
                                }
                            }));
                })
        );
        return 1;
    }
}
