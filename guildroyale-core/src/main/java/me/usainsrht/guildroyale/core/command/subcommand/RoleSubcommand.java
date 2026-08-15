package me.usainsrht.guildroyale.core.command.subcommand;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.usainsrht.guildroyale.api.permission.GuildPermissionKey;
import me.usainsrht.guildroyale.api.service.ActionResult;
import me.usainsrht.guildroyale.core.GuildRoyalePlugin;
import me.usainsrht.guildroyale.core.command.CommandNodes;
import me.usainsrht.guildroyale.core.config.CommandConfig;
import org.bukkit.entity.Player;

/**
 * {@code /guild role} subtree with configurable child names/aliases.
 */
@SuppressWarnings("UnstableApiUsage")
public final class RoleSubcommand {

    private RoleSubcommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> node(CommandConfig.Spec spec, String mainCmd) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(spec.name())
                .requires(src -> src.getSender().hasPermission(CommandConfig.PERM_ROLE))
                .executes(ctx -> {
                    GuildRoyalePlugin plugin = GuildRoyalePlugin.getInstance();
                    if (plugin != null) {
                        plugin.getMessages().send(ctx.getSource().getSender(), "role-usage",
                                net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("cmd", mainCmd),
                                net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("sub", spec.name()));
                    }
                    return 0;
                });

        CommandNodes.attachChild(root, spec.child("create", "create"), child -> child
                .then(Commands.argument("name", StringArgumentType.greedyString())
                        .executes(ctx -> executeCreate(ctx, StringArgumentType.getString(ctx, "name")))));
        CommandNodes.attachChild(root, spec.child("delete", "delete"), child -> child
                .then(Commands.argument("index", IntegerArgumentType.integer(1))
                        .executes(ctx -> executeDelete(ctx, IntegerArgumentType.getInteger(ctx, "index")))));
        CommandNodes.attachChild(root, spec.child("rename", "rename"), child -> child
                .then(Commands.argument("index", IntegerArgumentType.integer(1))
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> executeRename(ctx,
                                        IntegerArgumentType.getInteger(ctx, "index"),
                                        StringArgumentType.getString(ctx, "name"))))));
        CommandNodes.attachChild(root, spec.child("setpermission", "setpermission"), child -> child
                .then(Commands.argument("index", IntegerArgumentType.integer(1))
                        .then(Commands.argument("permission", StringArgumentType.word())
                                .executes(ctx -> executeTogglePerm(ctx,
                                        IntegerArgumentType.getInteger(ctx, "index"),
                                        StringArgumentType.getString(ctx, "permission"))))));
        return root;
    }

    private static int executeCreate(CommandContext<CommandSourceStack> ctx, String name) {
        if (!(ctx.getSource().getSender() instanceof Player player)) return 0;
        GuildRoyalePlugin plugin = GuildRoyalePlugin.getInstance();
        if (plugin == null) return 0;

        plugin.getScheduler().runAsync(() ->
                plugin.getGuildService().getGuildByMember(player.getUniqueId()).thenCompose(opt -> {
                    if (opt.isEmpty()) return java.util.concurrent.CompletableFuture.completedFuture(null);
                    return plugin.getRoleService().createRole(opt.get().getId(), player.getUniqueId(), name)
                            .thenAccept(result -> plugin.getScheduler().runForEntity(player, () -> {
                                switch (result) {
                                    case ActionResult.Success s -> {
                                        var role = opt.get().getRoles().stream().filter(r -> r.getName().equalsIgnoreCase(name)).findFirst().orElse(null);
                                        plugin.getMessages().send(player, "role-created",
                                                role != null ? plugin.getTagService().roleResolver(role, opt.get(), player)
                                                        : net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("role", name));
                                    }
                                    case ActionResult.Failure f ->
                                            plugin.getMessages().send(player, f.reason());
                                }
                            }));
                })
        );
        return 1;
    }

    private static int executeDelete(CommandContext<CommandSourceStack> ctx, int index) {
        if (!(ctx.getSource().getSender() instanceof Player player)) return 0;
        GuildRoyalePlugin plugin = GuildRoyalePlugin.getInstance();
        if (plugin == null) return 0;

        plugin.getScheduler().runAsync(() ->
                plugin.getGuildService().getGuildByMember(player.getUniqueId()).thenCompose(opt -> {
                    if (opt.isEmpty()) return java.util.concurrent.CompletableFuture.completedFuture(null);
                    var role = opt.get().getRole(index).orElse(null);
                    return plugin.getRoleService().deleteRole(opt.get().getId(), player.getUniqueId(), index)
                            .thenAccept(result -> plugin.getScheduler().runForEntity(player, () -> {
                                switch (result) {
                                    case ActionResult.Success s ->
                                            plugin.getMessages().send(player, "role-deleted",
                                                    role != null ? plugin.getTagService().roleResolver(role, opt.get(), player)
                                                            : net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("role", String.valueOf(index)));
                                    case ActionResult.Failure f ->
                                            plugin.getMessages().send(player, f.reason());
                                }
                            }));
                })
        );
        return 1;
    }

    private static int executeRename(CommandContext<CommandSourceStack> ctx, int index, String name) {
        if (!(ctx.getSource().getSender() instanceof Player player)) return 0;
        GuildRoyalePlugin plugin = GuildRoyalePlugin.getInstance();
        if (plugin == null) return 0;

        plugin.getScheduler().runAsync(() ->
                plugin.getGuildService().getGuildByMember(player.getUniqueId()).thenCompose(opt -> {
                    if (opt.isEmpty()) return java.util.concurrent.CompletableFuture.completedFuture(null);
                    var role = opt.get().getRole(index).orElse(null);
                    return plugin.getRoleService().renameRole(opt.get().getId(), player.getUniqueId(), index, name)
                            .thenAccept(result -> plugin.getScheduler().runForEntity(player, () -> {
                                switch (result) {
                                    case ActionResult.Success s ->
                                            plugin.getMessages().send(player, "role-renamed",
                                                    role != null ? plugin.getTagService().roleResolver(role, opt.get(), player)
                                                            : net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("role", name));
                                    case ActionResult.Failure f ->
                                            plugin.getMessages().send(player, f.reason());
                                }
                            }));
                })
        );
        return 1;
    }

    private static int executeTogglePerm(CommandContext<CommandSourceStack> ctx, int index, String permName) {
        if (!(ctx.getSource().getSender() instanceof Player player)) return 0;
        GuildRoyalePlugin plugin = GuildRoyalePlugin.getInstance();
        if (plugin == null) return 0;

        GuildPermissionKey key;
        try {
            key = GuildPermissionKey.valueOf(permName.toUpperCase());
        } catch (IllegalArgumentException e) {
            player.sendMessage(net.kyori.adventure.text.Component.text("Unknown permission: " + permName,
                    net.kyori.adventure.text.format.NamedTextColor.RED));
            return 0;
        }

        plugin.getScheduler().runAsync(() ->
                plugin.getGuildService().getGuildByMember(player.getUniqueId()).thenCompose(opt -> {
                    if (opt.isEmpty()) return java.util.concurrent.CompletableFuture.completedFuture(null);
                    var role = opt.get().getRole(index).orElse(null);
                    return plugin.getRoleService().togglePermission(opt.get().getId(), player.getUniqueId(), index, key)
                            .thenAccept(result -> plugin.getScheduler().runForEntity(player, () -> {
                                switch (result) {
                                    case ActionResult.Success s ->
                                            plugin.getMessages().send(player, "permission-toggled",
                                                    role != null ? plugin.getTagService().roleResolver(role, opt.get(), player)
                                                            : net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("role", String.valueOf(index)));
                                    case ActionResult.Failure f ->
                                            plugin.getMessages().send(player, f.reason());
                                }
                            }));
                })
        );
        return 1;
    }
}
