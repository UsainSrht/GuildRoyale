package me.usainsrht.guildroyale.core.event;

import me.usainsrht.guildroyale.api.domain.Guild;
import me.usainsrht.guildroyale.api.domain.GuildMember;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** Fired when a player joins a guild. */
public final class GuildMemberJoinEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Guild guild;
    private final GuildMember member;
    private boolean cancelled;

    public GuildMemberJoinEvent(Guild guild, GuildMember member) {
        this.guild = guild;
        this.member = member;
    }

    public Guild getGuild() { return guild; }
    public GuildMember getMember() { return member; }

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean c) { this.cancelled = c; }
    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
