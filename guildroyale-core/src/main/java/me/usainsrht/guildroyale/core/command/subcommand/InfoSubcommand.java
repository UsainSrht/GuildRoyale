package me.usainsrht.guildroyale.core.command.subcommand;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.usainsrht.guildroyale.api.domain.GuildMember;
import me.usainsrht.guildroyale.core.GuildRoyalePlugin;
import me.usainsrht.guildroyale.core.config.CommandConfig;
import me.usainsrht.guildroyale.core.gui.impl.GuildMainGui;
import org.bukkit.entity.Player;

/** {@code /guild info} — opens the main guild menu (info is shown on the info icon lore). */
@SuppressWarnings("UnstableApiUsage")
public final class InfoSubcommand {

    private InfoSubcommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> node(String name) {
        return Commands.literal(name)
                .requires(src -> src.getSender().hasPermission(CommandConfig.PERM_INFO))
                .executes(InfoSubcommand::execute);
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
                            var guild = opt.get();
                            GuildMember viewer = guild.getMember(player.getUniqueId()).orElse(null);
                            if (viewer == null) {
                                plugin.getMessages().send(player, "not-in-guild");
                                return;
                            }
                            new GuildMainGui(guild, viewer, plugin.getGuiManager()).open(player);
                        })
                )
        );
        return 1;
    }
}
