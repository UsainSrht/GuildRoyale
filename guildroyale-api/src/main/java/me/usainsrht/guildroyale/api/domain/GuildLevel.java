package me.usainsrht.guildroyale.api.domain;

/**
 * Value object representing a guild's current level (1–10).
 */
public record GuildLevel(int level) {

    public static final int MIN_LEVEL = 1;
    public static final int MAX_LEVEL = 10;

    public GuildLevel {
        if (level < MIN_LEVEL || level > MAX_LEVEL) {
            throw new IllegalArgumentException(
                    "Guild level must be between " + MIN_LEVEL + " and " + MAX_LEVEL + ", got " + level);
        }
    }

    public static GuildLevel of(int level) { return new GuildLevel(level); }

    public static GuildLevel min() { return new GuildLevel(MIN_LEVEL); }

    public boolean isMaxLevel() { return level >= MAX_LEVEL; }

    public GuildLevel next() {
        if (isMaxLevel()) throw new IllegalStateException("Cannot advance past max level " + MAX_LEVEL);
        return new GuildLevel(level + 1);
    }

    @Override
    public String toString() { return "Level " + level; }
}
