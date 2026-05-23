package me.usainsrht.guildroyale.core.command.subcommand;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.usainsrht.guildroyale.api.service.ActionResult;
import me.usainsrht.guildroyale.core.GuildRoyalePlugin;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** {@code /guild leader <player>} — transfer leadership. */
@SuppressWarnings("UnstableApiUsage")
public final class LeaderSubcommand {

    private LeaderSubcommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> node(String name, String mainCmd) {
        return Commands.literal(name)
                .requires(src -> src.getSender().hasPermission(me.usainsrht.guildroyale.core.config.CommandConfig.PERM_LEADER))
                .executes(ctx -> {
                    ctx.getSource().getSender().sendMessage(
                            net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                                    .deserialize("<red>Usage: <yellow>/" + mainCmd + " " + name + " <player>"));
                    return 0;
                })
                .then(Commands.argument("player", StringArgumentType.word())
                        .executes(LeaderSubcommand::execute));
    }

    private static int execute(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getSender() instanceof Player player)) return 0;
        GuildRoyalePlugin plugin = GuildRoyalePlugin.getInstance();
        if (plugin == null) return 0;

        String targetName = StringArgumentType.getString(ctx, "player");
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            player.sendMessage(plugin.getMessages().prefixed("player-not-found",
                    Placeholder.unparsed("player", targetName)));
            return 0;
        }

        plugin.getScheduler().runAsync(() ->
                plugin.getGuildService().getGuildByMember(player.getUniqueId()).thenCompose(opt -> {
                    if (opt.isEmpty()) {
                        plugin.getScheduler().runForEntity(player, () ->
                                player.sendMessage(plugin.getMessages().prefixed("not-in-guild")));
                        return java.util.concurrent.CompletableFuture.completedFuture(null);
                    }
                    return plugin.getMemberService().transferLeader(
                                    opt.get().getId(), player.getUniqueId(), target.getUniqueId())
                            .thenAccept(result -> plugin.getScheduler().runForEntity(player, () -> {
                                switch (result) {
                                    case ActionResult.Success s -> {
                                        player.sendMessage(plugin.getMessages().prefixed("leader-transferred",
                                                Placeholder.unparsed("guild", opt.get().getName()),
                                                Placeholder.unparsed("player", targetName)));
                                        plugin.getScheduler().runForEntity(target, () ->
                                                target.sendMessage(plugin.getMessages().prefixed("leader-transferred-self",
                                                        Placeholder.unparsed("guild", opt.get().getName()))));
                                    }
                                    case ActionResult.Failure f ->
                                            player.sendMessage(plugin.getMessages().prefixed(f.reason()));
                                }
                            }));
                })
        );
        return 1;
    }
}
