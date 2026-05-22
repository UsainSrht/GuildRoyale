package dev.guildroyale.core.service;

import dev.guildroyale.api.domain.Guild;
import dev.guildroyale.api.domain.GuildMember;
import dev.guildroyale.api.service.LeaderboardService;
import dev.guildroyale.api.storage.GuildRepository;
import dev.guildroyale.core.config.ConfigManager;
import dev.guildroyale.core.scheduler.FoliaScheduler;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * Leaderboard service with a TTL-based cache refreshed on an async repeating schedule.
 */
public final class LeaderboardServiceImpl implements LeaderboardService {

    private static final int CACHE_LIMIT = 100;

    private final GuildRepository repo;
    private final ConfigManager config;
    private final FoliaScheduler scheduler;
    private final CopyOnWriteArrayList<Guild> cache = new CopyOnWriteArrayList<>();

    public LeaderboardServiceImpl(GuildRepository repo, ConfigManager config, FoliaScheduler scheduler) {
        this.repo = repo;
        this.config = config;
        this.scheduler = scheduler;
    }

    /** Starts the background cache-refresh task. Call once from plugin onEnable. */
    public void startRefreshTask() {
        long period = config.getLeaderboardCacheRefreshSeconds();
        scheduler.scheduleAsyncRepeating(this::refreshCacheSync, 0L, period, TimeUnit.SECONDS);
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
        List<Guild> snap = cache;
        return Collections.unmodifiableList(snap.subList(0, Math.min(limit, snap.size())));
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
        scheduler.runAsync(this::refreshCacheSync);
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private void refreshCacheSync() {
        repo.getLeaderboard(CACHE_LIMIT).thenAccept(result -> {
            cache.clear();
            cache.addAll(result);
        });
    }
}
