package me.usainsrht.guildroyale.core.event;

import me.usainsrht.guildroyale.api.domain.Guild;
import me.usainsrht.guildroyale.api.domain.mission.ActiveMission;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** Fired when a guild starts a mission. */
public final class GuildMissionStartEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Guild guild;
    private final ActiveMission mission;

    public GuildMissionStartEvent(Guild guild, ActiveMission mission) {
        this.guild = Objects.requireNonNull(guild, "guild");
        this.mission = Objects.requireNonNull(mission, "mission");
    }

    public Guild getGuild() {
        return guild;
    }

    public ActiveMission getMission() {
        return mission;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
