package dev.guildroyale.api.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a single member of a {@link Guild}.
 * Holds role assignment, join timestamp, and XP contribution.
 */
public final class GuildMember {

    private final UUID playerId;
    private GuildRole role;
    private final Instant joinedAt;
    private long contribution;

    public GuildMember(UUID playerId, GuildRole role, Instant joinedAt, long contribution) {
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        this.role = Objects.requireNonNull(role, "role");
        this.joinedAt = Objects.requireNonNull(joinedAt, "joinedAt");
        this.contribution = Math.max(0, contribution);
    }

    public UUID getPlayerId() { return playerId; }

    public GuildRole getRole() { return role; }
    public void setRole(GuildRole role) { this.role = Objects.requireNonNull(role, "role"); }

    public Instant getJoinedAt() { return joinedAt; }

    public long getContribution() { return contribution; }
    public void addContribution(long amount) { this.contribution = Math.max(0, this.contribution + amount); }
    public void setContribution(long contribution) { this.contribution = Math.max(0, contribution); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GuildMember m)) return false;
        return playerId.equals(m.playerId);
    }

    @Override
    public int hashCode() { return playerId.hashCode(); }

    @Override
    public String toString() {
        return "GuildMember{playerId=" + playerId + ", roleIndex=" + role.getIndex() + '}';
    }
}
