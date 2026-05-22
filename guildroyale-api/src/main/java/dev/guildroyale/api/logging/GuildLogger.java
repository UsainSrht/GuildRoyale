package dev.guildroyale.api.logging;

import java.util.UUID;

/**
 * Writes structured log entries for guild plugin events.
 *
 * <p>Entries are written to rotating files — there is no in-game visibility.
 * The implementation lives in {@code dev.guildroyale.core.logging.GuildLogWriter}.
 */
public interface GuildLogger {

    void log(LogEntry entry);

    default void logGlobal(String message) {
        log(LogEntry.global(message));
    }

    default void logGuild(UUID guildId, String message) {
        log(LogEntry.guild(guildId.toString(), message));
    }

    default void logPlayer(UUID playerId, String message) {
        log(LogEntry.player(playerId.toString(), message));
    }
}
