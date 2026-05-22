package dev.guildroyale.api.storage;

import dev.guildroyale.api.domain.Guild;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Persistence contract for {@link Guild} objects.
 *
 * <p>All methods return {@link CompletableFuture} and must never block the
 * calling thread. Implementations are expected to perform I/O on a
 * dedicated thread pool.
 */
public interface GuildRepository {

    /** Initialises the storage backend (creates tables, files, etc.). */
    CompletableFuture<Void> init();

    /** Shuts down the storage backend gracefully. */
    void shutdown();

    /** Finds a guild by its unique ID. */
    CompletableFuture<Optional<Guild>> findById(UUID id);

    /** Finds the guild that a given player belongs to. */
    CompletableFuture<Optional<Guild>> findByMember(UUID playerId);

    /** Finds a guild by its exact name (case-insensitive). */
    CompletableFuture<Optional<Guild>> findByName(String name);

    /** Returns all guilds in the repository. */
    CompletableFuture<List<Guild>> findAll();

    /**
     * Returns the top {@code limit} guilds sorted by level (desc) then XP (desc).
     * Used for leaderboard queries.
     */
    CompletableFuture<List<Guild>> getLeaderboard(int limit);

    /**
     * Inserts or fully replaces the stored state for a guild.
     * Includes all members and roles.
     */
    CompletableFuture<Void> save(Guild guild);

    /** Deletes a guild and all associated data by ID. */
    CompletableFuture<Void> delete(UUID id);

    /** Returns {@code true} if a guild with the given name already exists. */
    CompletableFuture<Boolean> existsByName(String name);

    /** Returns {@code true} if a player is already a member of any guild. */
    CompletableFuture<Boolean> isPlayerInAnyGuild(UUID playerId);
}
