package me.usainsrht.guildroyale.core.command.subcommand;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.usainsrht.guildroyale.core.GuildRoyalePlugin;
import me.usainsrht.guildroyale.core.gui.impl.LeaderboardGui;
import org.bukkit.entity.Player;

/** {@code /guild leaderboard} — opens the leaderboard GUI. */
@SuppressWarnings("UnstableApiUsage")
public final class LeaderboardSubcommand {

    private LeaderboardSubcommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("leaderboard").executes(LeaderboardSubcommand::execute);
    }

    private static int execute(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getSender() instanceof Player player)) return 0;
        GuildRoyalePlugin plugin = GuildRoyalePlugin.getInstance();
        if (plugin == null) return 0;

        plugin.getScheduler().runAsync(() ->
                plugin.getLeaderboardService().getGlobalLeaderboard(0, 36).thenAccept(guilds ->
                        plugin.getScheduler().runForEntity(player, () -> {
                            var gui = new LeaderboardGui(
                                    plugin.getGuiManager(), plugin.getLeaderboardService(), 0);
                            gui.setGuilds(guilds);
                            gui.open(player);
                        })
                )
        );
        return 1;
    }
}
