package me.usainsrht.guildroyale.core.integration;

import me.usainsrht.guildroyale.api.domain.Guild;
import me.usainsrht.guildroyale.api.service.GuildService;
import me.usainsrht.guildroyale.api.service.LeaderboardService;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

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
 *   <li>{@code %guildroyale_top_name_<n>%} — name of the n-th guild on the leaderboard (1-based)</li>
 *   <li>{@code %guildroyale_top_level_<n>%} — level of the n-th guild on the leaderboard</li>
 * </ul>
 *
 * <p>All lookups against the guild service are <em>synchronous</em> using the in-memory
 * cache. Database round-trips are avoided.
 */
public final class GuildRoyalePlaceholderExpansion extends PlaceholderExpansion {

    private final GuildService guildService;
    private final LeaderboardService leaderboardService;

    public GuildRoyalePlaceholderExpansion(GuildService guildService, LeaderboardService leaderboardService) {
        this.guildService = guildService;
        this.leaderboardService = leaderboardService;
    }

    @Override public @NotNull String getIdentifier() { return "guildroyale"; }
    @Override public @NotNull String getAuthor() { return "GuildRoyale"; }
    @Override public @NotNull String getVersion() { return "1.0.0"; }
    @Override public boolean persist() { return true; }
    @Override public boolean canRegister() { return true; }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (params.startsWith("top_")) {
            return resolveTop(params);
        }

        // Per-player lookups — use the blocking join only for < 1ms in-memory lookup
        Optional<Guild> guildOpt = guildService.getGuildByMember(player.getUniqueId()).join();
        if (guildOpt.isEmpty()) return params.startsWith("role") ? "" : "";

        Guild guild = guildOpt.get();
        return switch (params) {
            case "guild_name"      -> guild.getName();
            case "guild_shortname" -> guild.getShortname();
            case "guild_level"     -> String.valueOf(guild.getLevel());
            case "guild_xp"        -> String.valueOf(guild.getXp());
            case "guild_members"   -> String.valueOf(guild.getMembers().size());
            case "role" -> guild.getMember(player.getUniqueId())
                    .map(m -> m.getRole().getName()).orElse("");
            default -> null;
        };
    }

    private @Nullable String resolveTop(String params) {
        // top_name_1, top_level_1 etc.
        String[] parts = params.split("_");
        if (parts.length < 3) return null;
        try {
            int rank = Integer.parseInt(parts[parts.length - 1]);
            List<Guild> top = leaderboardService.getCachedLeaderboard(rank);
            if (rank < 1 || rank > top.size()) return "";
            Guild guild = top.get(rank - 1);
            return switch (params.substring(4, params.lastIndexOf('_'))) {
                case "name"  -> guild.getName();
                case "level" -> String.valueOf(guild.getLevel());
                case "xp"    -> String.valueOf(guild.getXp());
                default -> null;
            };
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
