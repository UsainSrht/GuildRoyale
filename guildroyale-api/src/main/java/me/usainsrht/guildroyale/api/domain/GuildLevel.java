package me.usainsrht.guildroyale.api.domain;

/**
 * Value object representing a guild's current level (minimum 1).
 * The maximum level is enforced by configuration, not by this type.
 */
public record GuildLevel(int level) {

    public static final int MIN_LEVEL = 1;

    public GuildLevel {
        if (level < MIN_LEVEL) {
            throw new IllegalArgumentException(
                    "Guild level must be at least " + MIN_LEVEL + ", got " + level);
        }
    }

    public static GuildLevel of(int level) { return new GuildLevel(level); }

    public static GuildLevel min() { return new GuildLevel(MIN_LEVEL); }

    /**
     * @param maxLevel configured cap; {@code <= 0} means unlimited
     */
    public boolean isMaxLevel(int maxLevel) {
        return maxLevel > 0 && level >= maxLevel;
    }

    /**
     * @param maxLevel configured cap; {@code <= 0} means unlimited
     */
    public GuildLevel next(int maxLevel) {
        if (isMaxLevel(maxLevel)) {
            throw new IllegalStateException("Cannot advance past max level " + maxLevel);
        }
        return new GuildLevel(level + 1);
    }

    @Override
    public String toString() { return "Level " + level; }
}
