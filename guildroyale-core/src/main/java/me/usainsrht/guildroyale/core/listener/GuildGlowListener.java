package me.usainsrht.guildroyale.core.listener;

import me.usainsrht.guildroyale.api.domain.GuildMember;
import me.usainsrht.guildroyale.core.event.GuildDisbandedEvent;
import me.usainsrht.guildroyale.core.event.GuildMemberJoinEvent;
import me.usainsrht.guildroyale.core.event.GuildMemberKickedEvent;
import me.usainsrht.guildroyale.core.event.GuildMemberLeaveEvent;
import me.usainsrht.guildroyale.core.event.GuildRoleChangedEvent;
import me.usainsrht.guildroyale.core.glow.GlowManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * Listens for player and guild lifecycle events to maintain synchronized glow effects.
 */
public final class GuildGlowListener implements Listener {

    private final GlowManager glowManager;

    public GuildGlowListener(GlowManager glowManager) {
        this.glowManager = glowManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        glowManager.updatePlayerGlow(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        // GlowingEntities automatically unhooks the quitting player receiver,
        // but we ensure state consistency.
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        glowManager.updatePlayerGlow(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        glowManager.updatePlayerGlow(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMemberJoin(GuildMemberJoinEvent event) {
        Player player = Bukkit.getPlayer(event.getMember().getPlayerId());
        if (player != null && player.isOnline()) {
            glowManager.updatePlayerGlow(player);
        } else {
            glowManager.updateGuildGlow(event.getGuild());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMemberLeave(GuildMemberLeaveEvent event) {
        glowManager.removePlayerGlow(event.getMember().getPlayerId(), event.getGuild());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMemberKicked(GuildMemberKickedEvent event) {
        glowManager.removePlayerGlow(event.getKickedMember().getPlayerId(), event.getGuild());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRoleChanged(GuildRoleChangedEvent event) {
        glowManager.updateGuildGlow(event.getGuild());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onGuildDisbanded(GuildDisbandedEvent event) {
        for (GuildMember m : event.getGuild().getMembers()) {
            Player p = Bukkit.getPlayer(m.getPlayerId());
            if (p != null) {
                glowManager.removePlayerGlow(p, event.getGuild());
            }
        }
    }
}
