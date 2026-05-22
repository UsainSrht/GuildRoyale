package dev.guildroyale.core.command.subcommand;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.entity.Player;

/** {@code /guild icon} — opens the icon selection GUI. */
@SuppressWarnings("UnstableApiUsage")
public final class IconSubcommand {

    private IconSubcommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("icon").executes(IconSubcommand::execute);
    }

    private static int execute(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getSender() instanceof Player player)) return 0;
        dev.guildroyale.core.GuildRoyalePlugin plugin = dev.guildroyale.core.GuildRoyalePlugin.getInstance();
        if (plugin == null) return 0;

        plugin.getScheduler().runAsync(() ->
                plugin.getGuildService().getGuildByMember(player.getUniqueId()).thenAccept(opt ->
                        plugin.getScheduler().runForEntity(player, () -> {
                            if (opt.isEmpty()) {
                                player.sendMessage(plugin.getMessages().prefixed("not-in-guild"));
                                return;
                            }
                            new dev.guildroyale.core.gui.impl.IconSelectionGui(player, icon -> {
                                plugin.getScheduler().runAsync(() ->
                                        plugin.getGuildService().setIcon(opt.get().getId(), player.getUniqueId(), icon)
                                                .thenAccept(result -> plugin.getScheduler().runForEntity(player, () -> {
                                                    switch (result) {
                                                        case dev.guildroyale.api.service.ActionResult.Success s ->
                                                                player.sendMessage(plugin.getMessages().prefixed("icon-updated"));
                                                        case dev.guildroyale.api.service.ActionResult.Failure f ->
                                                                player.sendMessage(plugin.getMessages().prefixed(f.reason()));
                                                    }
                                                }))
                                );
                            }).open(player);
                        })
                )
        );
        return 1;
    }
}
