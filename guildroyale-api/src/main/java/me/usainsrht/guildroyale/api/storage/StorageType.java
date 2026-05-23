package me.usainsrht.guildroyale.api.storage;

/**
 * Supported storage backends.
 */
public enum StorageType {
    /** JSON files — one {@code <guildId>.json} per guild. */
    JSON,
    /** Local SQLite database file. */
    SQLITE,
    /** Remote MySQL / MariaDB database. */
    MYSQL;

    public static StorageType fromString(String value) {
        if (value == null) return JSON;
        return switch (value.toUpperCase()) {
            case "SQLITE" -> SQLITE;
            case "MYSQL", "MARIADB" -> MYSQL;
            default -> JSON;
        };
    }
}
