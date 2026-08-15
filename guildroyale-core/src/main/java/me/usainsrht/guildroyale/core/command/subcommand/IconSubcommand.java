package me.usainsrht.guildroyale.core.command.subcommand;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.usainsrht.guildroyale.api.domain.SerializableItemStack;
import me.usainsrht.guildroyale.api.service.ActionResult;
import me.usainsrht.guildroyale.core.GuildRoyalePlugin;
import me.usainsrht.guildroyale.core.adapter.ItemStackAdapter;
import me.usainsrht.guildroyale.core.config.CommandConfig;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** {@code /guild icon} — sets the guild icon to the item in hand. */
@SuppressWarnings("UnstableApiUsage")
public final class IconSubcommand {

    private IconSubcommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> node(String name) {
        return Commands.literal(name)
                .requires(src -> src.getSender().hasPermission(CommandConfig.PERM_ICON))
                .executes(IconSubcommand::execute);
    }

    private static int execute(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getSender() instanceof Player player)) return 0;
        GuildRoyalePlugin plugin = GuildRoyalePlugin.getInstance();
        if (plugin == null) return 0;

        ItemStack handItem = player.getInventory().getItemInMainHand();
        if (handItem.getType().isAir()) {
            plugin.getMessages().send(player, "icon-invalid");
            return 0;
        }

        SerializableItemStack icon = ItemStackAdapter.toSerializableItemType(handItem);

        plugin.getScheduler().runAsync(() ->
                plugin.getGuildService().getGuildByMember(player.getUniqueId()).thenAccept(opt ->
                        plugin.getScheduler().runForEntity(player, () -> {
                            if (opt.isEmpty()) {
                                plugin.getMessages().send(player, "not-in-guild");
                                return;
                            }
                            plugin.getScheduler().runAsync(() ->
                                    plugin.getGuildService().setIcon(opt.get().getId(), player.getUniqueId(), icon)
                                            .thenAccept(result -> plugin.getScheduler().runForEntity(player, () -> {
                                                switch (result) {
                                                    case ActionResult.Success s ->
                                                            plugin.getMessages().send(player, "icon-updated");
                                                    case ActionResult.Failure f ->
                                                            plugin.getMessages().send(player, f.reason());
                                                }
                                            }))
                            );
                        })
                )
        );
        return 1;
    }
}
