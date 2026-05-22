package dev.guildroyale.core.command.subcommand;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

/** {@code /guild disband} — disbands the player's guild. */
@SuppressWarnings("UnstableApiUsage")
public final class DisbandSubcommand {

    private DisbandSubcommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("disband")
                .executes(DisbandSubcommand::execute);
    }

    private static int execute(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getSender() instanceof Player player)) return 0;
        dev.guildroyale.core.GuildRoyalePlugin plugin = dev.guildroyale.core.GuildRoyalePlugin.getInstance();
        if (plugin == null) return 0;

        plugin.getScheduler().runAsync(() ->
                plugin.getGuildService().getGuildByMember(player.getUniqueId()).thenCompose(opt -> {
                    if (opt.isEmpty()) {
                        plugin.getScheduler().runForEntity(player, () ->
                                player.sendMessage(plugin.getMessages().prefixed("not-in-guild")));
                        return java.util.concurrent.CompletableFuture.completedFuture(null);
                    }
                    return plugin.getGuildService().disbandGuild(opt.get().getId(), player.getUniqueId())
                            .thenAccept(result -> plugin.getScheduler().runForEntity(player, () -> {
                                switch (result) {
                                    case dev.guildroyale.api.service.ActionResult.Success s ->
                                            player.sendMessage(plugin.getMessages().prefixed("guild-disbanded",
                                                    net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("guild", opt.get().getName())));
                                    case dev.guildroyale.api.service.ActionResult.Failure f ->
                                            player.sendMessage(plugin.getMessages().prefixed(f.reason()));
                                }
                            }));
                })
        );
        return 1;
    }
}
