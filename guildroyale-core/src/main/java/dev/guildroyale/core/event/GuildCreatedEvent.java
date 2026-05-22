package dev.guildroyale.core.event;

import dev.guildroyale.api.domain.Guild;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/** Fired after a guild is successfully created. */
public final class GuildCreatedEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Guild guild;
    private final UUID creatorId;
    private boolean cancelled;

    public GuildCreatedEvent(Guild guild, UUID creatorId) {
        this.guild = guild;
        this.creatorId = creatorId;
    }

    public Guild getGuild() { return guild; }
    public UUID getCreatorId() { return creatorId; }

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean c) { this.cancelled = c; }
    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
