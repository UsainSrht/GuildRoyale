package me.usainsrht.guildroyale.api.logging;

import java.time.Instant;
import java.util.Objects;

/**
 * An immutable log entry produced by guild plugin activity.
 */
public record LogEntry(Instant timestamp, LogScope scope, String scopeId, String message) {

    public LogEntry {
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(scopeId, "scopeId");
        Objects.requireNonNull(message, "message");
    }

    public static LogEntry global(String message) {
        return new LogEntry(Instant.now(), LogScope.GLOBAL, "global", message);
    }

    public static LogEntry guild(String guildId, String message) {
        return new LogEntry(Instant.now(), LogScope.GUILD, guildId, message);
    }

    public static LogEntry player(String playerId, String message) {
        return new LogEntry(Instant.now(), LogScope.PLAYER, playerId, message);
    }
}
