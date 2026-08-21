package me.usainsrht.guildroyale.api.storage;

import me.usainsrht.guildroyale.api.domain.mission.GuildMissionData;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Persistence contract for guild mission states and cooldown history.
 */
public interface MissionRepository {

    /** Initialises the storage backend. */
    CompletableFuture<Void> init();

    /** Shuts down the storage backend. */
    void shutdown();

    /** Finds mission data for a guild by its ID. */
    CompletableFuture<Optional<GuildMissionData>> findByGuildId(UUID guildId);

    /** Returns all stored guild mission records. */
    CompletableFuture<List<GuildMissionData>> findAll();

    /** Inserts or updates the stored mission data for a guild. */
    CompletableFuture<Void> save(GuildMissionData data);

    /** Deletes mission data for a guild. */
    CompletableFuture<Void> delete(UUID guildId);
}
