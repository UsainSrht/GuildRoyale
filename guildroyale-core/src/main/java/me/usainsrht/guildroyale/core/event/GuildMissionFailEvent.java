package me.usainsrht.guildroyale.core.event;

import me.usainsrht.guildroyale.api.domain.Guild;
import me.usainsrht.guildroyale.api.domain.mission.ActiveMission;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** Fired when an active guild mission fails due to timer expiration. */
public final class GuildMissionFailEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Guild guild;
    private final ActiveMission mission;
    private final long xpPenalty;
    private final double moneyPenalty;

    public GuildMissionFailEvent(Guild guild, ActiveMission mission, long xpPenalty, double moneyPenalty) {
        this.guild = Objects.requireNonNull(guild, "guild");
        this.mission = Objects.requireNonNull(mission, "mission");
        this.xpPenalty = xpPenalty;
        this.moneyPenalty = moneyPenalty;
    }

    public Guild getGuild() { return guild; }
    public ActiveMission getMission() { return mission; }
    public long getXpPenalty() { return xpPenalty; }
    public double getMoneyPenalty() { return moneyPenalty; }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
