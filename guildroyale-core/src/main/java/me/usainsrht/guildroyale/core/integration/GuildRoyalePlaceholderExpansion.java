package me.usainsrht.guildroyale.core.integration;

import me.usainsrht.guildroyale.api.service.GuildService;
import me.usainsrht.guildroyale.api.service.LeaderboardService;
import me.usainsrht.guildroyale.core.GuildRoyalePlugin;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * PlaceholderAPI integration — registers the {@code %guildroyale_<identifier>%} namespace.
 *
 * <h3>Available placeholders</h3>
 * <ul>
 *   <li>{@code %guildroyale_guild_name%} — the player's guild name, or empty</li>
 *   <li>{@code %guildroyale_guild_shortname%} — the guild shortname</li>
 *   <li>{@code %guildroyale_guild_level%} — the guild level</li>
 *   <li>{@code %guildroyale_guild_xp%} — the guild XP</li>
 *   <li>{@code %guildroyale_guild_members%} — member count</li>
 *   <li>{@code %guildroyale_role%} — the player's role name within their guild</li>
 *   <li>{@code %guildroyale_badge%} — the guild's active badge MiniMessage text, or empty</li>
 *   <li>{@code %guildroyale_top_name_<n>%} — name of the n-th guild on the leaderboard (1-based)</li>
 *   <li>{@code %guildroyale_top_level_<n>%} — level of the n-th guild on the leaderboard</li>
 *   <li>{@code %guildroyale_top_xp_<n>%} — XP of the n-th guild on the leaderboard</li>
 * </ul>
 *
 * <p>{@code top_*} placeholders are served from the leaderboard cache. Per-player
 * placeholders resolve the guild through the repository and therefore block; on a
 * SQL backend that is a database round-trip, so avoid placing them in
 * high-frequency contexts such as scoreboard or action-bar refresh loops.
 */
public final class GuildRoyalePlaceholderExpansion extends PlaceholderExpansion {

    private final GuildRoyalePlugin plugin;
    private final GuildPlaceholderData data;

    public GuildRoyalePlaceholderExpansion(GuildRoyalePlugin plugin, GuildService guildService,
                                           LeaderboardService leaderboardService) {
        this.plugin = plugin;
        this.data = new GuildPlaceholderData(plugin, guildService, leaderboardService);
    }

    @Override public @NotNull String getIdentifier() { return "guildroyale"; }
    @Override public @NotNull String getAuthor() { return "GuildRoyale"; }
    @Override public @NotNull String getVersion() { return plugin.getPluginMeta().getVersion(); }
    @Override public boolean persist() { return true; }
    @Override public boolean canRegister() { return true; }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        return data.resolve(player != null ? player.getUniqueId() : null, params);
    }
}
