package me.usainsrht.guildroyale.core.listener;

import me.usainsrht.guildroyale.core.gui.GuiManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Cleans up per-player state when a player disconnects.
 */
public final class PlayerQuitListener implements Listener {

    private final GuiManager guiManager;

    public PlayerQuitListener(GuiManager guiManager) {
        this.guiManager = guiManager;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        guiManager.unregister(event.getPlayer());
    }
}
