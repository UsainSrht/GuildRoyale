package me.usainsrht.guildroyale.core.command.subcommand;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.usainsrht.guildroyale.api.domain.Guild;
import me.usainsrht.guildroyale.api.service.ActionResult;
import me.usainsrht.guildroyale.core.GuildRoyalePlugin;
import me.usainsrht.guildroyale.core.command.CommandNodes;
import me.usainsrht.guildroyale.core.config.BadgeDefinition;
import me.usainsrht.guildroyale.core.config.CommandConfig;
import me.usainsrht.guildroyale.core.feature.GuildFeature;
import me.usainsrht.guildroyale.core.service.GuildServiceImpl;
import me.usainsrht.guildroyale.core.message.Text;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;

/** {@code /guild badge list|buy|equip} */
@SuppressWarnings("UnstableApiUsage")
public final class BadgeSubcommand {

    private BadgeSubcommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> node(CommandConfig.Spec spec) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(spec.name())
                .requires(src -> src.getSender().hasPermission(CommandConfig.PERM_BADGE))
                .executes(ctx -> {
                    GuildRoyalePlugin plugin = GuildRoyalePlugin.getInstance();
                    if (plugin != null) {
                        plugin.getMessages().send(ctx.getSource().getSender(), "badge-usage");
                    }
                    return 0;
                });

        CommandNodes.attachChild(root, spec.child("list", "list"), child -> child.executes(BadgeSubcommand::list));
        CommandNodes.attachChild(root, spec.child("buy", "buy"), child -> child
                .then(Commands.argument("id", StringArgumentType.word()).executes(BadgeSubcommand::buy)));
        CommandNodes.attachChild(root, spec.child("equip", "equip"), child -> child
                .then(Commands.argument("id", StringArgumentType.word()).executes(BadgeSubcommand::equip)));

        return root;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
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
                            Guild guild = opt.get();
                            GuildServiceImpl service = (GuildServiceImpl) plugin.getGuildService();
                            if (!service.featureGate().isUnlocked(guild, GuildFeature.BADGE)) {
                                plugin.getMessages().send(player, "feature-locked",
                                        Placeholder.unparsed("feature", "badge"),
                                        Placeholder.unparsed("level", String.valueOf(
                                                service.featureGate().unlockLevel(GuildFeature.BADGE))));
                                return;
                            }
                            plugin.getMessages().send(player, "badge-list-header");
                            for (BadgeDefinition badge : plugin.getConfigManager().getBadges().values()) {
                                boolean owned = guild.ownsBadge(badge.id());
                                boolean active = badge.id().equals(guild.getActiveBadgeId());
                                String status = active ? "<green>ACTIVE"
                                        : owned ? "<yellow>OWNED"
                                        : badge.isBuyable() ? "<gray>BUY " + service.economy().format(badge.cost())
                                        : "<dark_gray>GRANT-ONLY";
                                player.sendMessage(Text.parse(
                                        "<gray>- <white>" + badge.id() + "</white> "
                                                + badge.symbol() + " " + badge.displayName() + " <dark_gray>|</dark_gray> " + status,
                                        player));
                            }
                        })
                )
        );
        return 1;
    }

    private static int buy(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getSender() instanceof Player player)) return 0;
        GuildRoyalePlugin plugin = GuildRoyalePlugin.getInstance();
        if (plugin == null) return 0;
        String id = StringArgumentType.getString(ctx, "id");

        plugin.getScheduler().runAsync(() ->
                plugin.getGuildService().getGuildByMember(player.getUniqueId()).thenCompose(opt -> {
                    if (opt.isEmpty()) {
                        plugin.getScheduler().runForEntity(player, () ->
                                plugin.getMessages().send(player, "not-in-guild"));
                        return java.util.concurrent.CompletableFuture.completedFuture(null);
                    }
                    return plugin.getGuildService().buyBadge(opt.get().getId(), player.getUniqueId(), id)
                            .thenAccept(result -> plugin.getScheduler().runForEntity(player, () ->
                                    handleResult(player, plugin, result, id, "badge-bought")));
                })
        );
        return 1;
    }

    private static int equip(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getSender() instanceof Player player)) return 0;
        GuildRoyalePlugin plugin = GuildRoyalePlugin.getInstance();
        if (plugin == null) return 0;
        String id = StringArgumentType.getString(ctx, "id");

        plugin.getScheduler().runAsync(() ->
                plugin.getGuildService().getGuildByMember(player.getUniqueId()).thenCompose(opt -> {
                    if (opt.isEmpty()) {
                        plugin.getScheduler().runForEntity(player, () ->
                                plugin.getMessages().send(player, "not-in-guild"));
                        return java.util.concurrent.CompletableFuture.completedFuture(null);
                    }
                    return plugin.getGuildService().equipBadge(opt.get().getId(), player.getUniqueId(), id)
                            .thenAccept(result -> plugin.getScheduler().runForEntity(player, () ->
                                    handleResult(player, plugin, result, id, "badge-equipped")));
                })
        );
        return 1;
    }

    private static void handleResult(Player player, GuildRoyalePlugin plugin, ActionResult result,
                                     String id, String successKey) {
        switch (result) {
            case ActionResult.Success s ->
                    plugin.getMessages().send(player, successKey, Placeholder.unparsed("badge", id));
            case ActionResult.Failure f -> {
                if ("feature-locked".equals(f.reason())) {
                    GuildServiceImpl service = (GuildServiceImpl) plugin.getGuildService();
                    plugin.getMessages().send(player, "feature-locked",
                            Placeholder.unparsed("feature", "badge"),
                            Placeholder.unparsed("level", String.valueOf(
                                    service.featureGate().unlockLevel(GuildFeature.BADGE))));
                } else {
                    plugin.getMessages().send(player, f.reason());
                }
            }
        }
    }
}
