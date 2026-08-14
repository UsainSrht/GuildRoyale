package me.usainsrht.guildroyale.core.integration;

import me.usainsrht.guildroyale.api.domain.Guild;
import me.usainsrht.guildroyale.api.service.GuildService;
import me.usainsrht.guildroyale.api.service.LeaderboardService;
import me.usainsrht.guildroyale.core.GuildRoyalePlugin;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Shared placeholder resolution used by PlaceholderAPI and MiniPlaceholders.
 *
 * <p>Identifiers match the PAPI params (without the {@code guildroyale_} prefix), e.g.
 * {@code guild_name}, {@code top_name_1}.
 */
public final class GuildPlaceholderData {

    private final GuildRoyalePlugin plugin;
    private final GuildService guildService;
    private final LeaderboardService leaderboardService;

    public GuildPlaceholderData(GuildRoyalePlugin plugin, GuildService guildService,
                                LeaderboardService leaderboardService) {
        this.plugin = plugin;
        this.guildService = guildService;
        this.leaderboardService = leaderboardService;
    }

    /**
     * Resolves a placeholder identifier.
     *
     * @param memberId player UUID for audience/player placeholders; ignored for {@code top_*}
     * @param params   identifier such as {@code guild_name} or {@code top_level_1}
     * @return resolved value, empty string when the player has no guild / rank is out of range,
     *         or {@code null} when the identifier is unknown
     */
    public @Nullable String resolve(@Nullable UUID memberId, String params) {
        if (params.startsWith("top_")) {
            return resolveTop(params);
        }
        if (memberId == null) {
            return "";
        }

        Optional<Guild> guildOpt = guildService.getGuildByMember(memberId).join();
        if (guildOpt.isEmpty()) {
            return "";
        }

        Guild guild = guildOpt.get();
        return switch (params) {
            case "guild_name" -> guild.getName();
            case "guild_shortname" -> guild.getShortname();
            case "guild_level" -> String.valueOf(guild.getLevel());
            case "guild_xp" -> String.valueOf(guild.getXp());
            case "guild_members" -> String.valueOf(guild.getMembers().size());
            case "badge" -> {
                String id = guild.getActiveBadgeId();
                if (id == null) {
                    yield "";
                }
                yield plugin.getConfigManager().getBadgeDisplay(id).orElse("");
            }
            case "role" -> guild.getMember(memberId)
                    .map(m -> m.getRole().getName())
                    .orElse("");
            default -> null;
        };
    }

    /** Resolves {@code top_name_N} / {@code top_level_N} / {@code top_xp_N} (1-based rank). */
    public @Nullable String resolveTop(String params) {
        String[] parts = params.split("_");
        if (parts.length < 3) {
            return null;
        }
        try {
            int rank = Integer.parseInt(parts[parts.length - 1]);
            List<Guild> top = leaderboardService.getCachedLeaderboard(rank);
            if (rank < 1 || rank > top.size()) {
                return "";
            }
            Guild guild = top.get(rank - 1);
            return switch (params.substring(4, params.lastIndexOf('_'))) {
                case "name" -> guild.getName();
                case "level" -> String.valueOf(guild.getLevel());
                case "xp" -> String.valueOf(guild.getXp());
                default -> null;
            };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Resolves a top placeholder by field and 1-based rank (MiniPlaceholders argument form).
     *
     * @param field {@code name}, {@code level}, or {@code xp}
     */
    public @Nullable String resolveTopField(String field, int rank) {
        if (rank < 1) {
            return "";
        }
        List<Guild> top = leaderboardService.getCachedLeaderboard(rank);
        if (rank > top.size()) {
            return "";
        }
        Guild guild = top.get(rank - 1);
        return switch (field) {
            case "name" -> guild.getName();
            case "level" -> String.valueOf(guild.getLevel());
            case "xp" -> String.valueOf(guild.getXp());
            default -> null;
        };
    }
}
