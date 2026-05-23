package me.usainsrht.guildroyale.api.logging;

/**
 * Categorises a log entry by its scope.
 */
public enum LogScope {
    /** Server-wide global log. */
    GLOBAL,
    /** Scoped to a specific guild (scopeId = guild UUID string). */
    GUILD,
    /** Scoped to a specific player (scopeId = player UUID string). */
    PLAYER
}
