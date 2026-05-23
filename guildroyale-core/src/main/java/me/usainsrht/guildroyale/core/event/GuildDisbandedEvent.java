package me.usainsrht.guildroyale.core.event;

import me.usainsrht.guildroyale.api.domain.Guild;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/** Fired after a guild has been disbanded. */
public final class GuildDisbandedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Guild guild;
    private final UUID disbanderId;

    public GuildDisbandedEvent(Guild guild, UUID disbanderId) {
        this.guild = guild;
        this.disbanderId = disbanderId;
    }

    public Guild getGuild() { return guild; }
    public UUID getDisbanderId() { return disbanderId; }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
