package dev.guildroyale.core.command.subcommand;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;

/** {@code /guild join <guildName>} — accepts a pending invite. */
@SuppressWarnings("UnstableApiUsage")
public final class JoinSubcommand {

    private JoinSubcommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("join")
                .then(Commands.argument("guild", StringArgumentType.greedyString())
                        .executes(JoinSubcommand::execute));
    }

    private static int execute(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getSender() instanceof Player player)) return 0;
        dev.guildroyale.core.GuildRoyalePlugin plugin = dev.guildroyale.core.GuildRoyalePlugin.getInstance();
        if (plugin == null) return 0;

        String guildName = StringArgumentType.getString(ctx, "guild");

        plugin.getScheduler().runAsync(() ->
                plugin.getGuildService().getGuildByName(guildName).thenCompose(opt -> {
                    if (opt.isEmpty()) {
                        plugin.getScheduler().runForEntity(player, () ->
                                player.sendMessage(plugin.getMessages().prefixed("invalid-guild")));
                        return java.util.concurrent.CompletableFuture.completedFuture(null);
                    }
                    return plugin.getMemberService().join(opt.get().getId(), player.getUniqueId())
                            .thenAccept(result -> plugin.getScheduler().runForEntity(player, () -> {
                                switch (result) {
                                    case dev.guildroyale.api.service.ActionResult.Success s ->
                                            player.sendMessage(plugin.getMessages().prefixed("member-joined-self",
                                                    Placeholder.unparsed("guild", opt.get().getName())));
                                    case dev.guildroyale.api.service.ActionResult.Failure f ->
                                            player.sendMessage(plugin.getMessages().prefixed(f.reason()));
                                }
                            }));
                })
        );
        return 1;
    }
}
