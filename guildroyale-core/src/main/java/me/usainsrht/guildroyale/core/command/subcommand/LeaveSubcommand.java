package me.usainsrht.guildroyale.core.command.subcommand;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.usainsrht.guildroyale.api.service.ActionResult;
import me.usainsrht.guildroyale.core.GuildRoyalePlugin;
import me.usainsrht.guildroyale.core.config.CommandConfig;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;

/** {@code /guild leave} */
@SuppressWarnings("UnstableApiUsage")
public final class LeaveSubcommand {

    private LeaveSubcommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> node(String name) {
        return Commands.literal(name)
                .requires(src -> src.getSender().hasPermission(CommandConfig.PERM_LEAVE))
                .executes(LeaveSubcommand::execute);
    }

    private static int execute(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getSender() instanceof Player player)) return 0;
        GuildRoyalePlugin plugin = GuildRoyalePlugin.getInstance();
        if (plugin == null) return 0;

        plugin.getScheduler().runAsync(() ->
                plugin.getGuildService().getGuildByMember(player.getUniqueId()).thenCompose(opt -> {
                    if (opt.isEmpty()) {
                        plugin.getScheduler().runForEntity(player, () ->
                                plugin.getMessages().send(player, "not-in-guild"));
                        return java.util.concurrent.CompletableFuture.completedFuture(null);
                    }
                    String guildName = opt.get().getName();
                    return plugin.getMemberService().leave(opt.get().getId(), player.getUniqueId())
                            .thenAccept(result -> plugin.getScheduler().runForEntity(player, () -> {
                                switch (result) {
                                    case ActionResult.Success s ->
                                            plugin.getMessages().send(player, "member-left-self",
                                                    Placeholder.unparsed("guild", guildName));
                                    case ActionResult.Failure f ->
                                            plugin.getMessages().send(player, f.reason());
                                }
                            }));
                })
        );
        return 1;
    }
}
