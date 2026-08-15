package me.usainsrht.guildroyale.core.command.subcommand;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.usainsrht.guildroyale.api.service.ActionResult;
import me.usainsrht.guildroyale.core.GuildRoyalePlugin;
import me.usainsrht.guildroyale.core.config.CommandConfig;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** {@code /guild kick <player>} */
@SuppressWarnings("UnstableApiUsage")
public final class KickSubcommand {

    private KickSubcommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> node(String name, String mainCmd) {
        return Commands.literal(name)
                .requires(src -> src.getSender().hasPermission(CommandConfig.PERM_KICK))
                .executes(ctx -> {
                    ctx.getSource().getSender().sendMessage(
                            net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                                    .deserialize("<red>Usage: <yellow>/" + mainCmd + " " + name + " <player>"));
                    return 0;
                })
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
            plugin.getMessages().send(player, "player-not-found",
                    plugin.getTagService().playerResolver(null, targetName, player));
            return 0;
        }

        plugin.getScheduler().runAsync(() ->
                plugin.getGuildService().getGuildByMember(player.getUniqueId()).thenCompose(opt -> {
                    if (opt.isEmpty()) {
                        plugin.getScheduler().runForEntity(player, () ->
                                plugin.getMessages().send(player, "not-in-guild"));
                        return java.util.concurrent.CompletableFuture.completedFuture(null);
                    }
                    var targetMember = opt.get().getMember(target.getUniqueId()).orElse(null);
                    return plugin.getMemberService().kick(opt.get().getId(), player.getUniqueId(), target.getUniqueId())
                            .thenAccept(result -> plugin.getScheduler().runForEntity(player, () -> {
                                switch (result) {
                                    case ActionResult.Success s -> {
                                        plugin.getMessages().send(player, "member-kicked",
                                                plugin.getTagService().memberResolver(targetMember, opt.get(), player));
                                        plugin.getScheduler().runForEntity(target, () ->
                                                plugin.getMessages().send(target, "member-kicked-self",
                                                        plugin.getTagService().guildResolver(opt.get(), target)));
                                    }
                                    case ActionResult.Failure f ->
                                            plugin.getMessages().send(player, f.reason());
                                }
                            }));
                })
        );
        return 1;
    }
}
