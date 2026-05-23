package me.usainsrht.guildroyale.api.service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Manages guild membership: join, leave, invite, kick, and role assignment.
 */
public interface MemberService {

    /** Invites a player to a guild. The requester must have {@code INVITE} permission. */
    CompletableFuture<ActionResult> invite(UUID guildId, UUID requesterId, UUID targetPlayerId);

    /** Accepts a pending invitation and joins the guild. */
    CompletableFuture<ActionResult> join(UUID guildId, UUID playerId);

    /** Leaves a guild voluntarily. The leader may not leave without transferring first. */
    CompletableFuture<ActionResult> leave(UUID guildId, UUID playerId);

    /** Kicks a member. Requester must have {@code KICK} and outrank the target. */
    CompletableFuture<ActionResult> kick(UUID guildId, UUID requesterId, UUID targetPlayerId);

    /**
     * Changes a member's role.
     * Requester must have {@code ROLE_MANAGEMENT} and outrank the target.
     */
    CompletableFuture<ActionResult> changeRole(UUID guildId, UUID requesterId, UUID targetPlayerId, int newRoleIndex);

    /** Transfers the leader role to another member. Only the current leader may do this. */
    CompletableFuture<ActionResult> transferLeader(UUID guildId, UUID currentLeaderId, UUID newLeaderId);

    /** Checks whether a player currently has a pending invite to the given guild. */
    boolean hasPendingInvite(UUID guildId, UUID playerId);

    /** Revokes a pending invite. */
    void revokeInvite(UUID guildId, UUID playerId);
}
