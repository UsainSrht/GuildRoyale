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

/** {@code /guild kick <player>} */
@SuppressWarnings("UnstableApiUsage")
public final class KickSubcommand {

    private KickSubcommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("kick")
                .then(Commands.argument("player", StringArgumentType.word())
                        .executes(KickSubcommand::execute));
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
                    return plugin.getMemberService().kick(opt.get().getId(), player.getUniqueId(), target.getUniqueId())
                            .thenAccept(result -> plugin.getScheduler().runForEntity(player, () -> {
                                switch (result) {
                                    case ActionResult.Success s -> {
                                        player.sendMessage(plugin.getMessages().prefixed("member-kicked",
                                                Placeholder.unparsed("player", targetName)));
                                        plugin.getScheduler().runForEntity(target, () ->
                                                target.sendMessage(plugin.getMessages().prefixed("member-kicked-self",
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
