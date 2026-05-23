package me.usainsrht.guildroyale.api.service;

import me.usainsrht.guildroyale.api.domain.Guild;
import me.usainsrht.guildroyale.api.domain.GuildMember;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Provides sorted leaderboard data for guilds and per-guild member rankings.
 */
public interface LeaderboardService {

    /**
     * Returns a page of guilds ranked by level (desc) then XP (desc).
     *
     * @param page  zero-based page number
     * @param size  number of entries per page
     */
    CompletableFuture<List<Guild>> getGlobalLeaderboard(int page, int size);

    /**
     * Returns the top {@code limit} guilds, cached and refreshed on a configurable TTL.
     * This is the fast path used by PlaceholderAPI.
     */
    List<Guild> getCachedLeaderboard(int limit);

    /** Returns all members of a guild sorted by contribution (desc). */
    CompletableFuture<List<GuildMember>> getMemberLeaderboard(UUID guildId);

    /** Forces a refresh of the cached leaderboard. */
    void invalidateCache();
}
