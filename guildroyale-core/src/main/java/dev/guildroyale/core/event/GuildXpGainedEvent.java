package dev.guildroyale.core.event;

import dev.guildroyale.api.domain.Guild;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** Fired when XP is added to a guild. */
public final class GuildXpGainedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Guild guild;
    private final long amount;

    public GuildXpGainedEvent(Guild guild, long amount) {
        this.guild = guild;
        this.amount = amount;
    }

    public Guild getGuild() { return guild; }
    public long getAmount() { return amount; }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
