package dev.guildroyale.core.event;

import dev.guildroyale.api.domain.Guild;
import dev.guildroyale.api.domain.GuildMember;
import dev.guildroyale.api.domain.GuildRole;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** Fired when a member's role changes within a guild. */
public final class GuildRoleChangedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Guild guild;
    private final GuildMember member;
    private final GuildRole oldRole;
    private final GuildRole newRole;

    public GuildRoleChangedEvent(Guild guild, GuildMember member, GuildRole oldRole, GuildRole newRole) {
        this.guild = guild;
        this.member = member;
        this.oldRole = oldRole;
        this.newRole = newRole;
    }

    public Guild getGuild() { return guild; }
    public GuildMember getMember() { return member; }
    public GuildRole getOldRole() { return oldRole; }
    public GuildRole getNewRole() { return newRole; }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
