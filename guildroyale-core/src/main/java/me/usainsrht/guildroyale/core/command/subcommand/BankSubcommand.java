package me.usainsrht.guildroyale.core.command.subcommand;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.usainsrht.guildroyale.api.domain.Guild;
import me.usainsrht.guildroyale.api.domain.GuildMember;
import me.usainsrht.guildroyale.api.permission.GuildPermissionKey;
import me.usainsrht.guildroyale.api.service.ActionResult;
import me.usainsrht.guildroyale.core.GuildRoyalePlugin;
import me.usainsrht.guildroyale.core.command.CommandNodes;
import me.usainsrht.guildroyale.core.config.CommandConfig;
import me.usainsrht.guildroyale.core.feature.GuildFeature;
import me.usainsrht.guildroyale.core.service.GuildServiceImpl;
import me.usainsrht.guildroyale.core.service.PermissionEvaluatorImpl;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;

/** {@code /guild bank balance|deposit|withdraw} */
@SuppressWarnings("UnstableApiUsage")
public final class BankSubcommand {

    private BankSubcommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> node(CommandConfig.Spec spec) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(spec.name())
                .requires(src -> src.getSender().hasPermission(CommandConfig.PERM_BANK))
                .executes(ctx -> {
                    GuildRoyalePlugin plugin = GuildRoyalePlugin.getInstance();
                    if (plugin != null) {
                        plugin.getMessages().send(ctx.getSource().getSender(), "bank-usage");
                    }
                    return 0;
                });

        CommandConfig.Spec balance = spec.child("balance", "balance");
        CommandConfig.Spec deposit = spec.child("deposit", "deposit");
        CommandConfig.Spec withdraw = spec.child("withdraw", "withdraw");

        CommandNodes.attachChild(root, balance, child -> child.executes(BankSubcommand::balance));
        CommandNodes.attachChild(root, deposit, child -> child
                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01))
                        .executes(BankSubcommand::deposit)));
        CommandNodes.attachChild(root, withdraw, child -> child
                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01))
                        .executes(BankSubcommand::withdraw)));

        return root;
    }

    public static void showBalance(Player player) {
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
                            if (!ensureBank(player, plugin, guild, GuildPermissionKey.BANK_VIEW)) return;
                            GuildServiceImpl service = (GuildServiceImpl) plugin.getGuildService();
                            double bal = service.economy().getBalance(guild.getId());
                            plugin.getMessages().send(player, "bank-balance",
                                    Placeholder.unparsed("cost", service.economy().format(bal)));
                        })
                )
        );
    }

    private static int balance(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getSender() instanceof Player player)) return 0;
        showBalance(player);
        return 1;
    }

    private static int deposit(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getSender() instanceof Player player)) return 0;
        GuildRoyalePlugin plugin = GuildRoyalePlugin.getInstance();
        if (plugin == null) return 0;
        double amount = DoubleArgumentType.getDouble(ctx, "amount");

        plugin.getScheduler().runAsync(() ->
                plugin.getGuildService().getGuildByMember(player.getUniqueId()).thenCompose(opt -> {
                    if (opt.isEmpty()) {
                        plugin.getScheduler().runForEntity(player, () ->
                                plugin.getMessages().send(player, "not-in-guild"));
                        return java.util.concurrent.CompletableFuture.completedFuture(null);
                    }
                    return plugin.getGuildService().bankDeposit(opt.get().getId(), player.getUniqueId(), amount)
                            .thenAccept(result -> plugin.getScheduler().runForEntity(player, () -> {
                                switch (result) {
                                    case ActionResult.Success s -> {
                                        GuildServiceImpl service = (GuildServiceImpl) plugin.getGuildService();
                                        plugin.getMessages().send(player, "bank-deposit",
                                                Placeholder.unparsed("cost", service.economy().format(amount)));
                                    }
                                    case ActionResult.Failure f ->
                                            sendFailure(player, plugin, f.reason(), GuildFeature.BANK);
                                }
                            }));
                })
        );
        return 1;
    }

    private static int withdraw(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getSender() instanceof Player player)) return 0;
        GuildRoyalePlugin plugin = GuildRoyalePlugin.getInstance();
        if (plugin == null) return 0;
        double amount = DoubleArgumentType.getDouble(ctx, "amount");

        plugin.getScheduler().runAsync(() ->
                plugin.getGuildService().getGuildByMember(player.getUniqueId()).thenCompose(opt -> {
                    if (opt.isEmpty()) {
                        plugin.getScheduler().runForEntity(player, () ->
                                plugin.getMessages().send(player, "not-in-guild"));
                        return java.util.concurrent.CompletableFuture.completedFuture(null);
                    }
                    return plugin.getGuildService().bankWithdraw(opt.get().getId(), player.getUniqueId(), amount)
                            .thenAccept(result -> plugin.getScheduler().runForEntity(player, () -> {
                                switch (result) {
                                    case ActionResult.Success s -> {
                                        GuildServiceImpl service = (GuildServiceImpl) plugin.getGuildService();
                                        plugin.getMessages().send(player, "bank-withdraw",
                                                Placeholder.unparsed("cost", service.economy().format(amount)));
                                    }
                                    case ActionResult.Failure f ->
                                            sendFailure(player, plugin, f.reason(), GuildFeature.BANK);
                                }
                            }));
                })
        );
        return 1;
    }

    private static boolean ensureBank(Player player, GuildRoyalePlugin plugin, Guild guild,
                                      GuildPermissionKey key) {
        GuildServiceImpl service = (GuildServiceImpl) plugin.getGuildService();
        if (!service.featureGate().isUnlocked(guild, GuildFeature.BANK)) {
            plugin.getMessages().send(player, "feature-locked",
                    Placeholder.unparsed("feature", "bank"),
                    Placeholder.unparsed("level", String.valueOf(
                            service.featureGate().unlockLevel(GuildFeature.BANK))));
            return false;
        }
        GuildMember member = guild.getMember(player.getUniqueId()).orElse(null);
        if (member == null || !new PermissionEvaluatorImpl().canAct(member, key)) {
            plugin.getMessages().send(player, "no-permission");
            return false;
        }
        return true;
    }

    private static void sendFailure(Player player, GuildRoyalePlugin plugin, String reason, GuildFeature feature) {
        if ("feature-locked".equals(reason)) {
            GuildServiceImpl service = (GuildServiceImpl) plugin.getGuildService();
            plugin.getMessages().send(player, "feature-locked",
                    Placeholder.unparsed("feature", feature.configKey()),
                    Placeholder.unparsed("level", String.valueOf(service.featureGate().unlockLevel(feature))));
            return;
        }
        plugin.getMessages().send(player, reason);
    }
}
