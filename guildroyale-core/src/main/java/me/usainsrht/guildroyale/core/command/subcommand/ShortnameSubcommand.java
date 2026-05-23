package me.usainsrht.guildroyale.core.command.subcommand;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.usainsrht.guildroyale.api.service.ActionResult;
import me.usainsrht.guildroyale.core.GuildRoyalePlugin;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;

/** {@code /guild shortname} — opens the shortname change dialog. */
@SuppressWarnings("UnstableApiUsage")
public final class ShortnameSubcommand {

    private ShortnameSubcommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> node(String name) {
        return Commands.literal(name)
                .requires(src -> src.getSender().hasPermission(me.usainsrht.guildroyale.core.config.CommandConfig.PERM_SHORTNAME))
                .executes(ShortnameSubcommand::execute);
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
                            plugin.getDialogManager().openShortnameDialog(player, shortname ->
                                    plugin.getScheduler().runAsync(() ->
                                            plugin.getGuildService().setShortname(
                                                            opt.get().getId(), player.getUniqueId(), shortname)
                                                    .thenAccept(result -> plugin.getScheduler().runForEntity(player, () -> {
                                                        switch (result) {
                                                            case ActionResult.Success s ->
                                                                    player.sendMessage(plugin.getMessages().prefixed("shortname-changed",
                                                                            Placeholder.unparsed("guild", shortname)));
                                                            case ActionResult.Failure f ->
                                                                    player.sendMessage(plugin.getMessages().prefixed(f.reason()));
                                                        }
                                                    }))
                                    )
                            );
                        })
                )
        );
        return 1;
    }
}
