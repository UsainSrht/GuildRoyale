package me.usainsrht.guildroyale.core.command.subcommand;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.usainsrht.guildroyale.api.domain.Guild;
import me.usainsrht.guildroyale.api.domain.GuildMember;
import me.usainsrht.guildroyale.core.GuildRoyalePlugin;
import me.usainsrht.guildroyale.core.config.CommandConfig;
import me.usainsrht.guildroyale.core.gui.impl.GuildMissionGui;
import org.bukkit.entity.Player;

/** {@code /guild mission} — opens the guild missions GUI. */
@SuppressWarnings("UnstableApiUsage")
public final class MissionSubcommand {

    private MissionSubcommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> node(String name) {
        return Commands.literal(name)
                .requires(src -> src.getSender().hasPermission(CommandConfig.PERM_MISSION)
                        || src.getSender().hasPermission(CommandConfig.PERM_MENU))
                .executes(MissionSubcommand::execute);
    }

    private static int execute(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getSender() instanceof Player player)) return 0;
        openMissions(player);
        return 1;
    }

    public static void openMissions(Player player) {
        GuildRoyalePlugin plugin = GuildRoyalePlugin.getInstance();
        if (plugin == null) return;

        plugin.getScheduler().runAsync(() ->
                plugin.getGuildService().getGuildByMember(player.getUniqueId()).thenAccept(opt ->
                        plugin.getScheduler().runForEntity(player, () -> {
                            if (opt.isEmpty()) {
                                plugin.getMessages().send(player, "not-in-guild");
                                return;
                            }
                            Guild guild = opt.get();
                            GuildMember member = guild.getMember(player.getUniqueId()).orElse(null);
                            if (member == null) {
                                plugin.getMessages().send(player, "not-in-guild");
                                return;
                            }
                            new GuildMissionGui(guild, member, plugin.getGuiManager()).open(player);
                        })
                )
        );
    }
}
