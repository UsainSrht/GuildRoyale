package me.usainsrht.guildroyale.core.command.subcommand;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.usainsrht.guildroyale.api.service.ActionResult;
import me.usainsrht.guildroyale.core.GuildRoyalePlugin;
import me.usainsrht.guildroyale.core.config.CommandConfig;
import me.usainsrht.guildroyale.core.gui.impl.IconSelectionGui;
import org.bukkit.entity.Player;

/** {@code /guild icon} — opens the icon selection GUI. */
@SuppressWarnings("UnstableApiUsage")
public final class IconSubcommand {

    private IconSubcommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> node(String name) {
        return Commands.literal(name)
                .requires(src -> src.getSender().hasPermission(CommandConfig.PERM_ICON))
                .executes(IconSubcommand::execute);
    }

    private static int execute(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getSender() instanceof Player player)) return 0;
        GuildRoyalePlugin plugin = GuildRoyalePlugin.getInstance();
        if (plugin == null) return 0;

        plugin.getScheduler().runAsync(() ->
                plugin.getGuildService().getGuildByMember(player.getUniqueId()).thenAccept(opt ->
                        plugin.getScheduler().runForEntity(player, () -> {
                            if (opt.isEmpty()) {
                                plugin.getMessages().send(player, "not-in-guild");
                                return;
                            }
                            new IconSelectionGui(player, item ->
                                    plugin.getScheduler().runAsync(() ->
                                            plugin.getGuildService().setIcon(opt.get().getId(), player.getUniqueId(), item)
                                                    .thenAccept(result -> plugin.getScheduler().runForEntity(player, () -> {
                                                        switch (result) {
                                                            case ActionResult.Success s ->
                                                                    plugin.getMessages().send(player, "icon-updated");
                                                            case ActionResult.Failure f ->
                                                                    plugin.getMessages().send(player, f.reason());
                                                        }
                                                    }))
                                    )
                            ).open(player);
                        })
                )
        );
        return 1;
    }
}
