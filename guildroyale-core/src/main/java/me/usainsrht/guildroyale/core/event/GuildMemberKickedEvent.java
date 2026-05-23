package me.usainsrht.guildroyale.core.event;

import me.usainsrht.guildroyale.api.domain.Guild;
import me.usainsrht.guildroyale.api.domain.GuildMember;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/** Fired when a member is kicked from a guild. */
public final class GuildMemberKickedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Guild guild;
    private final GuildMember kickedMember;
    private final UUID kickerId;

    public GuildMemberKickedEvent(Guild guild, GuildMember kickedMember, UUID kickerId) {
        this.guild = guild;
        this.kickedMember = kickedMember;
        this.kickerId = kickerId;
    }

    public Guild getGuild() { return guild; }
    public GuildMember getKickedMember() { return kickedMember; }
    public UUID getKickerId() { return kickerId; }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
