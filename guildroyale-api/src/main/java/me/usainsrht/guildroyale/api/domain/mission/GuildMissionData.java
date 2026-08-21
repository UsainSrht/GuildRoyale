package me.usainsrht.guildroyale.api.domain.mission;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistent mission state for a guild, including active mission and historical timestamps.
 */
public final class GuildMissionData {

    private final UUID guildId;
    private ActiveMission activeMission;
    private Instant lastStartedAt;
    private Instant lastCompletedAt;

    public GuildMissionData(UUID guildId, ActiveMission activeMission,
                            Instant lastStartedAt, Instant lastCompletedAt) {
        this.guildId = Objects.requireNonNull(guildId, "guildId");
        this.activeMission = activeMission;
        this.lastStartedAt = lastStartedAt;
        this.lastCompletedAt = lastCompletedAt;
    }

    public UUID getGuildId() {
        return guildId;
    }

    public Optional<ActiveMission> getActiveMission() {
        return Optional.ofNullable(activeMission);
    }

    public void setActiveMission(ActiveMission activeMission) {
        this.activeMission = activeMission;
    }

    public boolean hasActiveMission() {
        return activeMission != null;
    }

    public Optional<Instant> getLastStartedAt() {
        return Optional.ofNullable(lastStartedAt);
    }

    public void setLastStartedAt(Instant lastStartedAt) {
        this.lastStartedAt = lastStartedAt;
    }

    public Optional<Instant> getLastCompletedAt() {
        return Optional.ofNullable(lastCompletedAt);
    }

    public void setLastCompletedAt(Instant lastCompletedAt) {
        this.lastCompletedAt = lastCompletedAt;
    }
}
