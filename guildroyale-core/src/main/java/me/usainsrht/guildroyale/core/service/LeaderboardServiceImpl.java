package me.usainsrht.guildroyale.core.service;

import me.usainsrht.guildroyale.api.domain.Guild;
import me.usainsrht.guildroyale.api.domain.GuildMember;
import me.usainsrht.guildroyale.api.service.LeaderboardService;
import me.usainsrht.guildroyale.api.storage.GuildRepository;
import me.usainsrht.guildroyale.core.config.ConfigManager;
import me.usainsrht.guildroyale.core.scheduler.FoliaScheduler;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Leaderboard service with a TTL-based cache refreshed on an async repeating schedule.
 */
public final class LeaderboardServiceImpl implements LeaderboardService {

    private static final int CACHE_LIMIT = 100;

    private final GuildRepository repo;
    private final ConfigManager config;
    private final FoliaScheduler scheduler;
    /** Replaced wholesale on refresh so readers never observe a half-filled list. */
    private volatile List<Guild> cache = List.of();

    public LeaderboardServiceImpl(GuildRepository repo, ConfigManager config, FoliaScheduler scheduler) {
        this.repo = repo;
        this.config = config;
        this.scheduler = scheduler;
    }

    /** Starts the background cache-refresh task. Call once from plugin onEnable. */
    public void startRefreshTask() {
        long period = config.getLeaderboardCacheRefreshSeconds();
        scheduler.scheduleAsyncRepeating(this::refreshCache, 0L, period, TimeUnit.SECONDS);
    }

    // ── Interface ─────────────────────────────────────────────────────────────

    @Override
    public CompletableFuture<List<Guild>> getGlobalLeaderboard(int page, int size) {
        int offset = page * size;
        return repo.getLeaderboard(offset + size).thenApply(all -> {
            if (offset >= all.size()) return List.of();
            return Collections.unmodifiableList(all.subList(offset, Math.min(offset + size, all.size())));
        });
    }

    @Override
    public List<Guild> getCachedLeaderboard(int limit) {
        List<Guild> snapshot = cache;
        return snapshot.subList(0, Math.min(Math.max(limit, 0), snapshot.size()));
    }

    @Override
    public CompletableFuture<List<GuildMember>> getMemberLeaderboard(UUID guildId) {
        return repo.findById(guildId).thenApply(opt ->
                opt.map(guild -> guild.getMembers().stream()
                        .sorted(Comparator.comparingLong(GuildMember::getContribution).reversed())
                        .toList()
                ).orElse(List.of())
        );
    }

    @Override
    public void invalidateCache() {
        scheduler.runAsync(this::refreshCache);
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private void refreshCache() {
        repo.getLeaderboard(CACHE_LIMIT)
                .thenAccept(result -> cache = List.copyOf(result));
    }
}
