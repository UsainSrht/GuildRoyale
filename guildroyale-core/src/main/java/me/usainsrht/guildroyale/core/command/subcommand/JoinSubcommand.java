package me.usainsrht.guildroyale.core.command.subcommand;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.usainsrht.guildroyale.api.service.ActionResult;
import me.usainsrht.guildroyale.core.GuildRoyalePlugin;
import me.usainsrht.guildroyale.core.config.CommandConfig;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;

/** {@code /guild join <guildName>} */
@SuppressWarnings("UnstableApiUsage")
public final class JoinSubcommand {

    private JoinSubcommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> node(String name, String mainCmd) {
        return Commands.literal(name)
                .requires(src -> src.getSender().hasPermission(CommandConfig.PERM_JOIN))
                .executes(ctx -> {
                    ctx.getSource().getSender().sendMessage(
                            net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                                    .deserialize("<red>Usage: <yellow>/" + mainCmd + " " + name + " <guildName>"));
                    return 0;
                })
                .then(Commands.argument("guildName", StringArgumentType.greedyString())
                        .executes(JoinSubcommand::execute));
    }

    private static int execute(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getSender() instanceof Player player)) return 0;
        GuildRoyalePlugin plugin = GuildRoyalePlugin.getInstance();
        if (plugin == null) return 0;
        String guildName = StringArgumentType.getString(ctx, "guildName");

        plugin.getScheduler().runAsync(() ->
                plugin.getGuildService().getGuildByName(guildName).thenCompose(opt -> {
                    if (opt.isEmpty()) {
                        plugin.getScheduler().runForEntity(player, () ->
                                plugin.getMessages().send(player, "invalid-guild"));
                        return java.util.concurrent.CompletableFuture.completedFuture(null);
                    }
                    return plugin.getMemberService().join(opt.get().getId(), player.getUniqueId())
                            .thenAccept(result -> plugin.getScheduler().runForEntity(player, () -> {
                                switch (result) {
                                    case ActionResult.Success s ->
                                            plugin.getMessages().send(player, "member-joined-self",
                                                    plugin.getTagService().guildResolver(opt.get(), player));
                                    case ActionResult.Failure f ->
                                            plugin.getMessages().send(player, f.reason());
                                }
                            }));
                })
        );
        return 1;
    }
}
