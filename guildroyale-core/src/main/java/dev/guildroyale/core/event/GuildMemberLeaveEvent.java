package dev.guildroyale.core.event;

import dev.guildroyale.api.domain.Guild;
import dev.guildroyale.api.domain.GuildMember;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** Fired when a player voluntarily leaves a guild. */
public final class GuildMemberLeaveEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Guild guild;
    private final GuildMember member;

    public GuildMemberLeaveEvent(Guild guild, GuildMember member) {
        this.guild = guild;
        this.member = member;
    }

    public Guild getGuild() { return guild; }
    public GuildMember getMember() { return member; }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
