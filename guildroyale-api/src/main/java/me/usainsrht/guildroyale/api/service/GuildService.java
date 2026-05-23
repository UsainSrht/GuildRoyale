package me.usainsrht.guildroyale.api.service;

import me.usainsrht.guildroyale.api.domain.Guild;
import me.usainsrht.guildroyale.api.domain.SerializableItemStack;
import me.usainsrht.guildroyale.api.permission.GuildPermissionKey;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * High-level business logic for guild management.
 * All methods are non-blocking and return {@link CompletableFuture}.
 */
public interface GuildService {

    /**
     * Creates a new guild with the given owner, name, and shortname.
     * Default roles (Leader, Co-Leader, Helper, Member) are created automatically.
     * The owner is added as the Leader.
     */
    CompletableFuture<ActionResult> createGuild(UUID ownerPlayerId, String name, String shortname);

    /** Disbands a guild — removes all members and deletes all data. */
    CompletableFuture<ActionResult> disbandGuild(UUID guildId, UUID requesterId);

    /** Fetches a guild by its ID (may return empty if not found). */
    CompletableFuture<Optional<Guild>> getGuild(UUID guildId);

    /** Fetches the guild a player belongs to. */
    CompletableFuture<Optional<Guild>> getGuildByMember(UUID playerId);

    /** Fetches a guild by its name (case-insensitive). */
    CompletableFuture<Optional<Guild>> getGuildByName(String name);

    /**
     * Adds XP to the guild and handles level-up if the XP threshold is crossed.
     * Returns the number of levels gained (0 if none).
     */
    CompletableFuture<Integer> addXp(UUID guildId, long amount);

    /** Sets the guild icon. Requires {@link GuildPermissionKey#ICON_CHANGE}. */
    CompletableFuture<ActionResult> setIcon(UUID guildId, UUID requesterId, SerializableItemStack icon);

    /** Sets a new shortname. May have an economy cost if configured. */
    CompletableFuture<ActionResult> setShortname(UUID guildId, UUID requesterId, String shortname);

    /** Sets a new name. Requires {@link GuildPermissionKey#GUILD_SETTINGS}. */
    CompletableFuture<ActionResult> setName(UUID guildId, UUID requesterId, String name);

    /**
     * Admin: force-set level, bypassing normal XP progression.
     */
    CompletableFuture<Void> adminSetLevel(UUID guildId, int level);

    /**
     * Admin: force-add XP without firing level-up events.
     */
    CompletableFuture<Void> adminAddXp(UUID guildId, long amount);

    /**
     * Computes the XP required to reach {@code nextLevel} from the current level.
     * Uses the formula configured in {@code config.yml}: {@code base * multiplier^(level-1)}.
     */
    long xpRequiredForLevel(int level);
}
