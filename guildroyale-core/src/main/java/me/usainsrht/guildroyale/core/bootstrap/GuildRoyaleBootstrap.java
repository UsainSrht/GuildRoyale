package me.usainsrht.guildroyale.core.bootstrap;

import me.usainsrht.guildroyale.core.command.GuildAdminCommandRegistrar;
import me.usainsrht.guildroyale.core.command.GuildCommandRegistrar;
import me.usainsrht.guildroyale.core.config.CommandConfig;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.jetbrains.annotations.NotNull;

/**
 * Registered in {@code paper-plugin.yml} as the plugin bootstrapper.
 *
 * <p>Brigadier commands must be registered during the bootstrap phase via the
 * {@link LifecycleEventManager}, not in {@code onEnable}.
 */
@SuppressWarnings("UnstableApiUsage")
public final class GuildRoyaleBootstrap implements PluginBootstrap {

    @Override
    public void bootstrap(@NotNull BootstrapContext context) {
        // Load command names from config.yml before the plugin instance exists.
        // Changes to command names/aliases require a server restart.
        CommandConfig cmdCfg = CommandConfig.load(
                context.getDataDirectory(),
                getClass().getClassLoader());

        LifecycleEventManager<BootstrapContext> manager = context.getLifecycleManager();

        manager.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            GuildCommandRegistrar.register(event.registrar(), cmdCfg);
            GuildAdminCommandRegistrar.register(event.registrar(), cmdCfg);
        });
    }
}
