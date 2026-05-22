package dev.guildroyale.core.command.subcommand;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.entity.Player;

/** {@code /guild menu} — opens the main guild hub GUI. */
@SuppressWarnings("UnstableApiUsage")
public final class MenuSubcommand {

    private MenuSubcommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("menu").executes(MenuSubcommand::execute);
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
                            var guild = opt.get();
                            var memberOpt = guild.getMember(player.getUniqueId());
                            if (memberOpt.isEmpty()) return;
                            new dev.guildroyale.core.gui.impl.GuildMainGui(guild, memberOpt.get(), plugin.getGuiManager())
                                    .open(player);
                        })
                )
        );
        return 1;
    }
}
