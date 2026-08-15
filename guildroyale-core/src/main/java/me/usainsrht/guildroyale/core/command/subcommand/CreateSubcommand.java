package me.usainsrht.guildroyale.core.command.subcommand;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.usainsrht.guildroyale.api.domain.Guild;
import me.usainsrht.guildroyale.api.service.ActionResult;
import me.usainsrht.guildroyale.core.GuildRoyalePlugin;
import me.usainsrht.guildroyale.core.config.CommandConfig;
import me.usainsrht.guildroyale.core.gui.CreateEligibility;
import me.usainsrht.guildroyale.core.gui.impl.GuildMainGui;
import me.usainsrht.guildroyale.core.service.GuildServiceImpl;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.concurrent.CompletableFuture;

/**
 * {@code /guild create} — opens the guild creation dialog.
 */
@SuppressWarnings("UnstableApiUsage")
public final class CreateSubcommand {

    private CreateSubcommand() {}

    private record CreateOutcome(ActionResult result, Guild guild) {}

    public static LiteralArgumentBuilder<CommandSourceStack> node(String name) {
        return Commands.literal(name)
                .requires(src -> src.getSender().hasPermission(CommandConfig.PERM_CREATE))
                .executes(CreateSubcommand::execute);
    }

    private static int execute(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getSender() instanceof Player player)) {
            GuildRoyalePlugin plugin = GuildRoyalePlugin.getInstance();
            if (plugin != null) {
                plugin.getMessages().send(ctx.getSource().getSender(), "player-only");
            }
            return 0;
        }
        openCreateFlow(player);
        return 1;
    }

    /** Shared entry point used by the command and the hub GUI create button. */
    public static void openCreateFlow(Player player) {
        GuildRoyalePlugin plugin = GuildRoyalePlugin.getInstance();
        if (plugin == null) return;

        plugin.getScheduler().runAsync(() ->
                plugin.getGuildService().getGuildByMember(player.getUniqueId()).thenAccept(opt ->
                        plugin.getScheduler().runForEntity(player, () -> {
                            if (opt.isPresent()) {
                                plugin.getMessages().send(player, "already-in-guild");
                                return;
                            }
                            CreateEligibility.Status live = CreateEligibility.evaluate(player, false);
                            if (!live.canCreate()) {
                                String key = switch (live) {
                                    case NO_PERMISSION -> "guild-creation-no-permission";
                                    case INSUFFICIENT_FUNDS -> "guild-creation-insufficient-funds";
                                    case MISSING_ITEMS -> "guild-creation-missing-items";
                                    default -> "unknown-error";
                                };
                                plugin.getMessages().send(player, key, CreateEligibility.costResolver(player));
                                return;
                            }

                            if (plugin.getConfigManager().isCreationMoneyEnabled()) {
                                double cost = plugin.getConfigManager().getCreationMoneyCost();
                                if (cost > 0) {
                                    GuildServiceImpl service = (GuildServiceImpl) plugin.getGuildService();
                                    plugin.getMessages().send(player, "guild-creation-cost",
                                            Placeholder.unparsed("cost", service.economy().format(cost)));
                                }
                            }

                            plugin.getDialogManager().openGuildCreateDialog(player, names -> {
                                String name = names[0];
                                String shortname = names[1];
                                plugin.getScheduler().runAsync(() ->
                                        plugin.getGuildService().createGuild(player.getUniqueId(), name, shortname)
                                                .thenCompose(result -> {
                                                    if (result instanceof ActionResult.Success) {
                                                        return plugin.getGuildService().getGuildByMember(player.getUniqueId())
                                                                .thenApply(optGuild -> new CreateOutcome(result, optGuild.orElse(null)));
                                                    }
                                                    return CompletableFuture.completedFuture(new CreateOutcome(result, null));
                                                })
                                                .thenAccept(outcome -> plugin.getScheduler().runForEntity(player, () -> {
                                                    switch (outcome.result()) {
                                                        case ActionResult.Success s -> {
                                                            Guild createdGuild = outcome.guild();
                                                            var guildRes = createdGuild != null ? plugin.getTagService().guildResolver(createdGuild, player) : Placeholder.unparsed("guild", name);
                                                            plugin.getMessages().send(player, "guild-created", guildRes);

                                                            var member = createdGuild != null ? createdGuild.getMember(player.getUniqueId()).orElse(null) : null;
                                                            var memberRes = (createdGuild != null && member != null) ? plugin.getTagService().memberResolver(member, createdGuild, null) : plugin.getTagService().playerResolver(player, null);
                                                            plugin.getMessages().send(Bukkit.getServer(), "guild-created-broadcast",
                                                                    guildRes, memberRes);
                                                            if (outcome.guild() != null) {
                                                                var memberOpt = outcome.guild().getMember(player.getUniqueId());
                                                                if (memberOpt.isPresent()) {
                                                                    new GuildMainGui(outcome.guild(), memberOpt.get(), plugin.getGuiManager()).open(player);
                                                                }
                                                            }
                                                        }
                                                        case ActionResult.Failure f ->
                                                                plugin.getMessages().send(player, f.reason(),
                                                                        Placeholder.unparsed("cost",
                                                                                ((GuildServiceImpl) plugin.getGuildService())
                                                                                        .economy()
                                                                                        .format(plugin.getConfigManager()
                                                                                                .getCreationMoneyCost())),
                                                                        Placeholder.unparsed("guild", name));
                                                    }
                                                }))
                                );
                            });
                        })
                )
        );
    }
}
