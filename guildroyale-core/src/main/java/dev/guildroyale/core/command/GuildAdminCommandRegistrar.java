package dev.guildroyale.core.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Registers the {@code /guildadmin} command tree.
 *
 * <p>Requires the {@code guildroyale.admin} permission.
 */
@SuppressWarnings("UnstableApiUsage")
public final class GuildAdminCommandRegistrar {

    private GuildAdminCommandRegistrar() {}

    public static void register(@NotNull Commands commands) {
        var root = Commands.literal("guildadmin")
                .requires(src -> src.getSender().hasPermission("guildroyale.admin"))
                .then(Commands.literal("reload")
                        .executes(ctx -> {
                            dev.guildroyale.core.GuildRoyalePlugin plugin =
                                    dev.guildroyale.core.GuildRoyalePlugin.getInstance();
                            if (plugin == null) return 0;
                            plugin.getConfigManager().reload();
                            plugin.getMessages().reload();
                            ctx.getSource().getSender().sendMessage(
                                    plugin.getMessages().prefixed("admin-reload"));
                            return 1;
                        }))
                .then(Commands.literal("addxp")
                        .then(Commands.argument("guild", StringArgumentType.word())
                                .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                        .executes(ctx -> {
                                            dev.guildroyale.core.GuildRoyalePlugin plugin =
                                                    dev.guildroyale.core.GuildRoyalePlugin.getInstance();
                                            if (plugin == null) return 0;
                                            String guildName = StringArgumentType.getString(ctx, "guild");
                                            long amount = LongArgumentType.getLong(ctx, "amount");
                                            plugin.getScheduler().runAsync(() ->
                                                    plugin.getGuildService().getGuildByName(guildName).thenCompose(opt -> {
                                                        if (opt.isEmpty()) return java.util.concurrent.CompletableFuture.completedFuture(null);
                                                        return plugin.getGuildService().adminAddXp(opt.get().getId(), amount)
                                                                .thenRun(() -> ctx.getSource().getSender().sendMessage(
                                                                        plugin.getMessages().prefixed("admin-addxp",
                                                                                Placeholder.unparsed("guild", guildName),
                                                                                Placeholder.unparsed("xp", String.valueOf(amount)))));
                                                    })
                                            );
                                            return 1;
                                        }))))
                .then(Commands.literal("setlevel")
                        .then(Commands.argument("guild", StringArgumentType.word())
                                .then(Commands.argument("level", IntegerArgumentType.integer(1, 10))
                                        .executes(ctx -> {
                                            dev.guildroyale.core.GuildRoyalePlugin plugin =
                                                    dev.guildroyale.core.GuildRoyalePlugin.getInstance();
                                            if (plugin == null) return 0;
                                            String guildName = StringArgumentType.getString(ctx, "guild");
                                            int level = IntegerArgumentType.getInteger(ctx, "level");
                                            plugin.getScheduler().runAsync(() ->
                                                    plugin.getGuildService().getGuildByName(guildName).thenCompose(opt -> {
                                                        if (opt.isEmpty()) return java.util.concurrent.CompletableFuture.completedFuture(null);
                                                        return plugin.getGuildService().adminSetLevel(opt.get().getId(), level)
                                                                .thenRun(() -> ctx.getSource().getSender().sendMessage(
                                                                        plugin.getMessages().prefixed("admin-setlevel",
                                                                                Placeholder.unparsed("guild", guildName),
                                                                                Placeholder.unparsed("level", String.valueOf(level)))));
                                                    })
                                            );
                                            return 1;
                                        }))))
                .then(Commands.literal("delete")
                        .then(Commands.argument("guild", StringArgumentType.word())
                                .executes(ctx -> {
                                    dev.guildroyale.core.GuildRoyalePlugin plugin =
                                            dev.guildroyale.core.GuildRoyalePlugin.getInstance();
                                    if (plugin == null) return 0;
                                    String guildName = StringArgumentType.getString(ctx, "guild");
                                    plugin.getScheduler().runAsync(() ->
                                            plugin.getGuildService().getGuildByName(guildName).thenCompose(opt -> {
                                                if (opt.isEmpty()) return java.util.concurrent.CompletableFuture.completedFuture(null);
                                                return plugin.getRepository().delete(opt.get().getId())
                                                        .thenRun(() -> ctx.getSource().getSender().sendMessage(
                                                                plugin.getMessages().prefixed("admin-delete",
                                                                        Placeholder.unparsed("guild", guildName))));
                                            })
                                    );
                                    return 1;
                                })))
                .build();

        commands.register(root, "GuildRoyale admin commands", List.of("ga"));
    }
}
