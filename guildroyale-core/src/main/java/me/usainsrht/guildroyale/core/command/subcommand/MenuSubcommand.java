package me.usainsrht.guildroyale.core.command.subcommand;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.usainsrht.guildroyale.core.command.GuildCommandRegistrar;
import me.usainsrht.guildroyale.core.config.CommandConfig;
import org.bukkit.entity.Player;

/** {@code /guild menu} — opens the hub or main guild GUI. */
@SuppressWarnings("UnstableApiUsage")
public final class MenuSubcommand {

    private MenuSubcommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> node(String name) {
        return Commands.literal(name)
                .requires(src -> src.getSender().hasPermission(CommandConfig.PERM_MENU))
                .executes(MenuSubcommand::execute);
    }

    private static int execute(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getSender() instanceof Player player)) return 0;
        GuildCommandRegistrar.openMenu(player);
        return 1;
    }
}