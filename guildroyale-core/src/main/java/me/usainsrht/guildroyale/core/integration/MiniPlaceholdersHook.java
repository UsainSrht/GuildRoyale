package me.usainsrht.guildroyale.core.integration;

import me.usainsrht.guildroyale.api.service.GuildService;
import me.usainsrht.guildroyale.api.service.LeaderboardService;
import me.usainsrht.guildroyale.core.GuildRoyalePlugin;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.Nullable;

/**
 * Soft-load bridge for MiniPlaceholders v3. Classes that reference the MiniPlaceholders
 * API are only loaded when the plugin is present, so GuildRoyale runs without it.
 */
public final class MiniPlaceholdersHook {

    private static final boolean AVAILABLE = detect();

    private static @Nullable Object expansion;

    private MiniPlaceholdersHook() {}

    private static boolean detect() {
        try {
            if (Bukkit.getPluginManager().getPlugin("MiniPlaceholders") == null) {
                return false;
            }
            Class.forName("io.github.miniplaceholders.api.MiniPlaceholders");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean isAvailable() {
        return AVAILABLE;
    }

    /**
     * @param withAudience {@code true} for {@code audienceGlobalPlaceholders()},
     *                     {@code false} for {@code globalPlaceholders()} only
     */
    public static TagResolver resolver(boolean withAudience) {
        if (!AVAILABLE) {
            return TagResolver.empty();
        }
        return MiniPlaceholdersAccess.resolver(withAudience);
    }

    public static void register(GuildRoyalePlugin plugin, GuildService guildService,
                                LeaderboardService leaderboardService) {
        if (!AVAILABLE) {
            return;
        }
        GuildPlaceholderData data = new GuildPlaceholderData(plugin, guildService, leaderboardService);
        expansion = MiniPlaceholdersAccess.register(plugin, data);
        plugin.getSLF4JLogger().info("MiniPlaceholders integration enabled.");
    }

    public static void unregister() {
        if (!AVAILABLE || expansion == null) {
            return;
        }
        MiniPlaceholdersAccess.unregister(expansion);
        expansion = null;
    }
}
