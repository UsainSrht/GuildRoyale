package me.usainsrht.guildroyale.core.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.usainsrht.guildroyale.core.config.CommandConfig;

import java.util.List;
import java.util.function.Consumer;

/**
 * Helpers for attaching Brigadier literals with configurable aliases.
 */
@SuppressWarnings("UnstableApiUsage")
public final class CommandNodes {

    private CommandNodes() {}

    /**
     * Builds {@code node}, attaches it under {@code root}, then registers each alias
     * as a redirect to the same node.
     */
    public static void attach(
            LiteralArgumentBuilder<CommandSourceStack> root,
            LiteralArgumentBuilder<CommandSourceStack> node,
            List<String> aliases) {
        LiteralCommandNode<CommandSourceStack> built = node.build();
        root.then(built);
        if (aliases == null) return;
        for (String alias : aliases) {
            if (alias == null || alias.isBlank()) continue;
            if (alias.equalsIgnoreCase(built.getLiteral())) continue;
            root.then(Commands.literal(alias).redirect(built));
        }
    }

    public static void attach(
            LiteralArgumentBuilder<CommandSourceStack> root,
            CommandConfig.Spec spec,
            LiteralArgumentBuilder<CommandSourceStack> node) {
        attach(root, node, spec.aliases());
    }

    /** Attaches child literals (with aliases) under a parent builder. */
    public static void attachChild(
            LiteralArgumentBuilder<CommandSourceStack> parent,
            CommandConfig.Spec childSpec,
            Consumer<LiteralArgumentBuilder<CommandSourceStack>> configurator) {
        LiteralArgumentBuilder<CommandSourceStack> child = Commands.literal(childSpec.name());
        configurator.accept(child);
        attach(parent, child, childSpec.aliases());
    }
}
