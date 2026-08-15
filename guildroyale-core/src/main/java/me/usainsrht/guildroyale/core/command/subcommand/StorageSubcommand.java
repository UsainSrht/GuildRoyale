package me.usainsrht.guildroyale.core.command.subcommand;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.usainsrht.guildroyale.api.domain.Guild;
import me.usainsrht.guildroyale.api.domain.GuildMember;
import me.usainsrht.guildroyale.api.permission.GuildPermissionKey;
import me.usainsrht.guildroyale.core.GuildRoyalePlugin;
import me.usainsrht.guildroyale.core.config.CommandConfig;
import me.usainsrht.guildroyale.core.feature.GuildFeature;
import me.usainsrht.guildroyale.core.gui.impl.GuildStorageGui;
import me.usainsrht.guildroyale.core.service.GuildServiceImpl;
import me.usainsrht.guildroyale.core.service.PermissionEvaluatorImpl;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;

/** {@code /guild storage} — opens the shared guild chest. */
@SuppressWarnings("UnstableApiUsage")
public final class StorageSubcommand {

    private StorageSubcommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> node(String name) {
        return Commands.literal(name)
                .requires(src -> src.getSender().hasPermission(CommandConfig.PERM_STORAGE))
                .executes(StorageSubcommand::execute);
    }

    private static int execute(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getSender() instanceof Player player)) return 0;
        openStorage(player);
        return 1;
    }

    public static void openStorage(Player player, java.util.function.Consumer<Player> returnTo) {
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
                            GuildServiceImpl service = (GuildServiceImpl) plugin.getGuildService();
                            if (!service.featureGate().isUnlocked(guild, GuildFeature.STORAGE)) {
                                plugin.getMessages().send(player, "feature-locked",
                                        Placeholder.unparsed("feature", "storage"),
                                        Placeholder.unparsed("level", String.valueOf(
                                                service.featureGate().unlockLevel(GuildFeature.STORAGE))));
                                return;
                            }
                            GuildMember member = guild.getMember(player.getUniqueId()).orElse(null);
                            if (member == null || !new PermissionEvaluatorImpl()
                                    .canAct(member, GuildPermissionKey.STORAGE_ACCESS)) {
                                plugin.getMessages().send(player, "no-permission");
                                return;
                            }
                            plugin.getGuildStorageManager().openStorage(player, guild, 0, returnTo);
                        })
                )
        );
    }

    public static void openStorage(Player player) {
        openStorage(player, null);
    }
}
