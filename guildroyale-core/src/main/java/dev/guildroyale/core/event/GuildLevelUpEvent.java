package dev.guildroyale.core.event;

import dev.guildroyale.api.domain.Guild;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** Fired when a guild's level increases. */
public final class GuildLevelUpEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Guild guild;
    private final int oldLevel;
    private final int newLevel;

    public GuildLevelUpEvent(Guild guild, int oldLevel, int newLevel) {
        this.guild = guild;
        this.oldLevel = oldLevel;
        this.newLevel = newLevel;
    }

    public Guild getGuild() { return guild; }
    public int getOldLevel() { return oldLevel; }
    public int getNewLevel() { return newLevel; }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
