package me.usainsrht.guildroyale.core.service;

import me.usainsrht.guildroyale.api.domain.Guild;
import me.usainsrht.guildroyale.api.domain.GuildMember;
import me.usainsrht.guildroyale.api.domain.GuildRole;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Service for rendering customizable Kyori components and {@link TagResolver}s for
 * guilds, roles, members, and players based on configured MiniMessage tag formats.
 */
public interface TagService {

    /** Renders the guild tag component for a guild. */
    Component renderGuildTag(Guild guild, @Nullable Audience viewer);

    /** Renders the role tag component for a role. */
    Component renderRoleTag(GuildRole role, @Nullable Guild guild, @Nullable Audience viewer);

    /** Renders the guild member tag component for a member. */
    Component renderMemberTag(GuildMember member, Guild guild, @Nullable Audience viewer);

    /** Renders the player tag component for a player by UUID and name. */
    Component renderPlayerTag(UUID playerId, String name, @Nullable Audience viewer);

    /** Renders the player tag component for an online player. */
    Component renderPlayerTag(Player player, @Nullable Audience viewer);

    /** Creates a TagResolver populating {@code <guild>}, {@code <guild_name>}, {@code <guild_level>}, etc. */
    TagResolver guildResolver(Guild guild, @Nullable Audience viewer);

    /** Creates a TagResolver populating {@code <role>}, {@code <role_name>}, {@code <role_color>}, etc. */
    TagResolver roleResolver(GuildRole role, @Nullable Guild guild, @Nullable Audience viewer);

    /** Creates a TagResolver populating {@code <member>}, {@code <player>}, {@code <member_name>}, etc. */
    TagResolver memberResolver(GuildMember member, Guild guild, @Nullable Audience viewer);

    /** Creates a TagResolver populating {@code <player>}, {@code <target>}, {@code <player_name>}, etc. */
    TagResolver playerResolver(UUID playerId, String name, @Nullable Audience viewer);

    /** Creates a TagResolver populating {@code <player>}, {@code <target>}, {@code <player_name>}, etc. */
    TagResolver playerResolver(Player player, @Nullable Audience viewer);

    /** Reloads tags configuration. */
    void reload();
}
