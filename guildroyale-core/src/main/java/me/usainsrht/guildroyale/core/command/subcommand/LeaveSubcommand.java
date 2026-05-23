package me.usainsrht.guildroyale.core.command.subcommand;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.usainsrht.guildroyale.api.service.ActionResult;
import me.usainsrht.guildroyale.core.GuildRoyalePlugin;
import org.bukkit.entity.Player;

/** {@code /guild leave} */
@SuppressWarnings("UnstableApiUsage")
public final class LeaveSubcommand {

    private LeaveSubcommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("leave").executes(LeaveSubcommand::execute);
    }

    private static int execute(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getSender() instanceof Player player)) return 0;
        GuildRoyalePlugin plugin = GuildRoyalePlugin.getInstance();
        if (plugin == null) return 0;

        plugin.getScheduler().runAsync(() ->
                plugin.getGuildService().getGuildByMember(player.getUniqueId()).thenCompose(opt -> {
                    if (opt.isEmpty()) {
                        plugin.getScheduler().runForEntity(player, () ->
                                player.sendMessage(plugin.getMessages().prefixed("not-in-guild")));
                        return java.util.concurrent.CompletableFuture.completedFuture(null);
                    }
                    return plugin.getMemberService().leave(opt.get().getId(), player.getUniqueId())
                            .thenAccept(result -> plugin.getScheduler().runForEntity(player, () -> {
                                switch (result) {
                                    case ActionResult.Success s ->
                                            player.sendMessage(plugin.getMessages().prefixed("member-left-self",
                                                    net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("guild", opt.get().getName())));
                                    case ActionResult.Failure f ->
                                            player.sendMessage(plugin.getMessages().prefixed(f.reason()));
                                }
                            }));
                })
        );
        return 1;
    }
}
