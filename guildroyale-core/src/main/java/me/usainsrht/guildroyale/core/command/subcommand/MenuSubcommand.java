package me.usainsrht.guildroyale.core.command.subcommand;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.usainsrht.guildroyale.core.GuildRoyalePlugin;
import me.usainsrht.guildroyale.core.gui.impl.GuildMainGui;
import org.bukkit.entity.Player;

/** {@code /guild menu} — opens the main guild hub GUI. */
@SuppressWarnings("UnstableApiUsage")
public final class MenuSubcommand {

    private MenuSubcommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> node(String name) {
        return Commands.literal(name)
                .requires(src -> src.getSender().hasPermission(me.usainsrht.guildroyale.core.config.CommandConfig.PERM_MENU))
                .executes(MenuSubcommand::execute);
    }

    private static int execute(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getSender() instanceof Player player)) return 0;
        GuildRoyalePlugin plugin = GuildRoyalePlugin.getInstance();
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
                            new GuildMainGui(guild, memberOpt.get(), plugin.getGuiManager())
                                    .open(player);
                        })
                )
        );
        return 1;
    }
}
