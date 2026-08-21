package me.usainsrht.guildroyale.core.event;

import me.usainsrht.guildroyale.api.domain.Guild;
import me.usainsrht.guildroyale.api.domain.mission.ActiveMission;
import me.usainsrht.guildroyale.api.domain.mission.MissionTaskProgress;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/** Fired when progress is added to a task in a guild mission. */
public final class GuildMissionTaskProgressEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Guild guild;
    private final ActiveMission mission;
    private final MissionTaskProgress task;
    private final UUID contributorId;
    private final long amountGained;

    public GuildMissionTaskProgressEvent(Guild guild, ActiveMission mission,
                                         MissionTaskProgress task, @Nullable UUID contributorId,
                                         long amountGained) {
        this.guild = Objects.requireNonNull(guild, "guild");
        this.mission = Objects.requireNonNull(mission, "mission");
        this.task = Objects.requireNonNull(task, "task");
        this.contributorId = contributorId;
        this.amountGained = amountGained;
    }

    public Guild getGuild() { return guild; }
    public ActiveMission getMission() { return mission; }
    public MissionTaskProgress getTask() { return task; }
    public @Nullable UUID getContributorId() { return contributorId; }
    public long getAmountGained() { return amountGained; }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
