package me.usainsrht.guildroyale.core.command;

import dev.guildroyale.core.command.subcommand.*;
import io.papermc.paper.command.brigadier.Commands;
import me.usainsrht.guildroyale.core.bootstrap.GuildRoyaleBootstrap;
import me.usainsrht.guildroyale.core.command.subcommand.*;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Registers the full {@code /guild} command tree via Brigadier.
 *
 * <p>Called from {@link GuildRoyaleBootstrap} during
 * the {@code COMMANDS} lifecycle event.
 */
@SuppressWarnings("UnstableApiUsage")
public final class GuildCommandRegistrar {

    private GuildCommandRegistrar() {}

    public static void register(@NotNull Commands commands) {
        var root = Commands.literal("guild")
                .then(CreateSubcommand.node())
                .then(DisbandSubcommand.node())
                .then(InfoSubcommand.node())
                .then(InviteSubcommand.node())
                .then(JoinSubcommand.node())
                .then(LeaveSubcommand.node())
                .then(KickSubcommand.node())
                .then(RoleSubcommand.node())
                .then(IconSubcommand.node())
                .then(ShortnameSubcommand.node())
                .then(LeaderboardSubcommand.node())
                .then(LeaderSubcommand.node())
                .then(MenuSubcommand.node())
                .build();

        commands.register(root, "Manage your guild", List.of("g"));
    }
}
