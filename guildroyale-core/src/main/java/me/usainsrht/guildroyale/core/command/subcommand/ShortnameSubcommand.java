package me.usainsrht.guildroyale.core.command.subcommand;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.usainsrht.guildroyale.api.service.ActionResult;
import me.usainsrht.guildroyale.core.GuildRoyalePlugin;
import me.usainsrht.guildroyale.core.config.CommandConfig;
import me.usainsrht.guildroyale.core.feature.GuildFeature;
import me.usainsrht.guildroyale.core.service.GuildServiceImpl;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;

/** {@code /guild shortname} — opens the shortname change dialog. */
@SuppressWarnings("UnstableApiUsage")
public final class ShortnameSubcommand {

    private ShortnameSubcommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> node(String name) {
        return Commands.literal(name)
                .requires(src -> src.getSender().hasPermission(CommandConfig.PERM_SHORTNAME))
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
                                plugin.getMessages().send(player, "not-in-guild");
                                return;
                            }
                            GuildServiceImpl service = (GuildServiceImpl) plugin.getGuildService();
                            GuildFeature feature = GuildFeature.SHORTNAME;
                            if (!service.featureGate().isUnlocked(opt.get(), feature)) {
                                plugin.getMessages().send(player, "feature-locked",
                                        Placeholder.unparsed("feature", "shortname"),
                                        Placeholder.unparsed("level", String.valueOf(
                                                service.featureGate().unlockLevel(feature))));
                                return;
                            }
                            plugin.getDialogManager().openShortnameDialog(player, shortname ->
                                    plugin.getScheduler().runAsync(() ->
                                            plugin.getGuildService().setShortname(
                                                            opt.get().getId(), player.getUniqueId(), shortname)
                                                    .thenAccept(result -> plugin.getScheduler().runForEntity(player, () -> {
                                                        switch (result) {
                                                            case ActionResult.Success s ->
                                                                    plugin.getMessages().send(player, "shortname-changed",
                                                                            Placeholder.unparsed("guild", shortname));
                                                            case ActionResult.Failure f -> {
                                                                if ("feature-locked".equals(f.reason())) {
                                                                    plugin.getMessages().send(player, "feature-locked",
                                                                            Placeholder.unparsed("feature", "shortname"),
                                                                            Placeholder.unparsed("level", String.valueOf(
                                                                                    service.featureGate().unlockLevel(feature))));
                                                                } else {
                                                                    plugin.getMessages().send(player, f.reason());
                                                                }
                                                            }
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
