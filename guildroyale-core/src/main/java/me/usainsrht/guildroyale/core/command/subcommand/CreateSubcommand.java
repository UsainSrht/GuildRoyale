package me.usainsrht.guildroyale.core.command.subcommand;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.usainsrht.guildroyale.api.service.ActionResult;
import me.usainsrht.guildroyale.core.GuildRoyalePlugin;
import org.bukkit.entity.Player;

/**
 * {@code /guild create} — opens the guild creation dialog.
 * The actual creation is completed asynchronously in DialogManager.
 */
@SuppressWarnings("UnstableApiUsage")
public final class CreateSubcommand {

    private CreateSubcommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("create")
                .executes(CreateSubcommand::execute);
    }

    private static int execute(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getSender() instanceof Player player)) {
            ctx.getSource().getSender().sendMessage(
                    net.kyori.adventure.text.Component.text("Only players can create guilds.", net.kyori.adventure.text.format.NamedTextColor.RED));
            return 0;
        }
        // DialogManager and GuildService are accessed via plugin instance stored in a static holder
        GuildRoyalePlugin plugin = GuildRoyalePlugin.getInstance();
        if (plugin == null) return 0;

        plugin.getDialogManager().openGuildCreateDialog(player, names -> {
            String name = names[0];
            String shortname = names[1];
            plugin.getScheduler().runAsync(() ->
                    plugin.getGuildService().createGuild(player.getUniqueId(), name, shortname)
                            .thenAccept(result -> plugin.getScheduler().runForEntity(player, () -> {
                                switch (result) {
                                    case ActionResult.Success s ->
                                            player.sendMessage(plugin.getMessages().prefixed("guild-created",
                                                    net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("guild", name)));
                                    case ActionResult.Failure f ->
                                            player.sendMessage(plugin.getMessages().prefixed(f.reason()));
                                }
                            }))
            );
        });
        return 1;
    }
}
