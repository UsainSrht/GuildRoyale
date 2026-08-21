package me.usainsrht.guildroyale.core.event;

import me.usainsrht.guildroyale.api.domain.Guild;
import me.usainsrht.guildroyale.api.domain.mission.ActiveMission;
import me.usainsrht.guildroyale.api.domain.mission.MissionTaskProgress;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** Fired when an individual task in a guild mission reaches 100% completion. */
public final class GuildMissionTaskCompleteEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Guild guild;
    private final ActiveMission mission;
    private final MissionTaskProgress task;

    public GuildMissionTaskCompleteEvent(Guild guild, ActiveMission mission, MissionTaskProgress task) {
        this.guild = Objects.requireNonNull(guild, "guild");
        this.mission = Objects.requireNonNull(mission, "mission");
        this.task = Objects.requireNonNull(task, "task");
    }

    public Guild getGuild() { return guild; }
    public ActiveMission getMission() { return mission; }
    public MissionTaskProgress getTask() { return task; }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
