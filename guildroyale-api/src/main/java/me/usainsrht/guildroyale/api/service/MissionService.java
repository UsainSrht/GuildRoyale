package me.usainsrht.guildroyale.api.service;

import me.usainsrht.guildroyale.api.domain.mission.ActiveMission;
import me.usainsrht.guildroyale.api.domain.mission.MissionTaskDefinition;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Service managing guild mission lifecycles, progression, task definitions, and cooldowns.
 */
public interface MissionService {

    /** Returns currently defined tasks loaded from configuration. */
    List<MissionTaskDefinition> getTaskDefinitions();

    /** Checks whether a guild currently has an active in-progress mission. */
    boolean isMissionActive(UUID guildId);

    /** Retrieves the active mission for the specified guild, if one is ongoing. */
    CompletableFuture<Optional<ActiveMission>> getActiveMission(UUID guildId);

    /** Retrieves the timestamp when the guild last started a mission. */
    CompletableFuture<Optional<Instant>> getLastStartedAt(UUID guildId);

    /** Returns remaining cooldown in seconds for starting a new mission (0 if ready). */
    long getCooldownRemainingSeconds(UUID guildId);

    /**
     * Attempts to start a mission for the guild by a player requester.
     * Validates cooldown, permissions, money, and item costs.
     */
    CompletableFuture<ActionResult> startMission(UUID guildId, UUID requesterId);

    /**
     * Cancels an ongoing mission for the guild by a requester with permissions.
     */
    CompletableFuture<ActionResult> cancelMission(UUID guildId, UUID requesterId);

    /**
     * Adds progress for a task to the player's guild mission.
     *
     * @param playerId UUID of the player contributing
     * @param taskId ID of the task
     * @param amount amount of progress to add
     * @return future completed with true if progress was added to an active mission
     */
    CompletableFuture<Boolean> addProgress(UUID playerId, String taskId, long amount);

    /**
     * Adds progress for a task to a specific guild's active mission.
     *
     * @param guildId UUID of the target guild
     * @param playerId UUID of the contributing player (may be null)
     * @param taskId ID of the task
     * @param amount amount of progress to add
     * @return future completed with true if progress was added to an active mission
     */
    CompletableFuture<Boolean> addProgress(UUID guildId, UUID playerId, String taskId, long amount);

    /**
     * Adds progress to tasks matching a custom 3rd-party identifier for the player's guild.
     *
     * @param playerId UUID of the player contributing
     * @param customId custom identifier configured on tasks
     * @param amount amount of progress to add
     * @return future completed with true if progress was added to matching tasks
     */
    CompletableFuture<Boolean> addProgressByCustomId(UUID playerId, String customId, long amount);

    // ── Admin Operations ──────────────────────────────────────────────────────

    /** Forces a mission to start for a guild, ignoring costs and cooldowns. */
    CompletableFuture<Void> adminStartMission(UUID guildId);

    /** Forces an active mission to stop without penalties or rewards. */
    CompletableFuture<Void> adminStopMission(UUID guildId);

    /** Instantly completes an active mission with full rewards. */
    CompletableFuture<Void> adminCompleteMission(UUID guildId);

    /** Adds progress to a task on behalf of an admin. */
    CompletableFuture<Void> adminAddProgress(UUID guildId, String taskId, long amount, UUID contributorId);

    /** Removes progress from a task on behalf of an admin. */
    CompletableFuture<Void> adminRemoveProgress(UUID guildId, String taskId, long amount, UUID contributorId);

    /** Resets the cooldown timer for a guild. */
    CompletableFuture<Void> adminResetCooldown(UUID guildId);
}
