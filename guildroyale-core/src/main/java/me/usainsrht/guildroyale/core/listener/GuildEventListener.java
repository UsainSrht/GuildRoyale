package me.usainsrht.guildroyale.core.listener;

import me.usainsrht.guildroyale.api.logging.GuildLogger;
import me.usainsrht.guildroyale.core.event.*;
import me.usainsrht.guildroyale.core.event.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Listens to GuildRoyale domain events and writes structured log entries.
 */
public final class GuildEventListener implements Listener {

    private final GuildLogger logger;

    public GuildEventListener(GuildLogger logger) {
        this.logger = logger;
    }

    @EventHandler
    public void onGuildCreated(GuildCreatedEvent event) {
        logger.logGuild(event.getGuild().getId(),
                "Guild created by player " + event.getCreatorId() + " — name: " + event.getGuild().getName());
    }

    @EventHandler
    public void onGuildDisbanded(GuildDisbandedEvent event) {
        logger.logGuild(event.getGuild().getId(),
                "Guild disbanded by " + event.getDisbanderId());
    }

    @EventHandler
    public void onMemberJoin(GuildMemberJoinEvent event) {
        logger.logGuild(event.getGuild().getId(),
                "Member joined: " + event.getMember().getPlayerId());
    }

    @EventHandler
    public void onMemberLeave(GuildMemberLeaveEvent event) {
        logger.logGuild(event.getGuild().getId(),
                "Member left: " + event.getMember().getPlayerId());
    }

    @EventHandler
    public void onMemberKicked(GuildMemberKickedEvent event) {
        logger.logGuild(event.getGuild().getId(),
                "Member kicked: " + event.getKickedMember().getPlayerId() + " by " + event.getKickerId());
    }

    @EventHandler
    public void onRoleChanged(GuildRoleChangedEvent event) {
        logger.logGuild(event.getGuild().getId(),
                "Role changed for " + event.getMember().getPlayerId()
                        + ": " + event.getOldRole().getName() + " → " + event.getNewRole().getName());
    }

    @EventHandler
    public void onLevelUp(GuildLevelUpEvent event) {
        logger.logGuild(event.getGuild().getId(),
                "Level up! " + event.getOldLevel() + " → " + event.getNewLevel());
    }
}
