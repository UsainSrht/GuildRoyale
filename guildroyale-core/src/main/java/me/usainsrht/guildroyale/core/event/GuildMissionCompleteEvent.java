package me.usainsrht.guildroyale.core.event;

import me.usainsrht.guildroyale.api.domain.Guild;
import me.usainsrht.guildroyale.api.domain.mission.ActiveMission;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** Fired when all tasks in a guild mission are successfully completed before expiration. */
public final class GuildMissionCompleteEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Guild guild;
    private final ActiveMission mission;
    private final long xpReward;
    private final double moneyReward;

    public GuildMissionCompleteEvent(Guild guild, ActiveMission mission, long xpReward, double moneyReward) {
        this.guild = Objects.requireNonNull(guild, "guild");
        this.mission = Objects.requireNonNull(mission, "mission");
        this.xpReward = xpReward;
        this.moneyReward = moneyReward;
    }

    public Guild getGuild() { return guild; }
    public ActiveMission getMission() { return mission; }
    public long getXpReward() { return xpReward; }
    public double getMoneyReward() { return moneyReward; }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
