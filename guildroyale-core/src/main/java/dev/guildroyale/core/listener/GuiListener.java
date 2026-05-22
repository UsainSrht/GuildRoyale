package dev.guildroyale.core.listener;

import dev.guildroyale.core.gui.AbstractGui;
import dev.guildroyale.core.gui.GuiManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;

/**
 * Routes inventory click events to the correct {@link dev.guildroyale.core.gui.AbstractGui}.
 *
 * <p>Inventory events fire on the player's region thread in Folia — safe to access
 * the player and its inventory directly.
 */
public final class GuiListener implements Listener {

    private final GuiManager guiManager;

    public GuiListener(GuiManager guiManager) {
        this.guiManager = guiManager;
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getInventory().getHolder() instanceof AbstractGui gui
                && event.getPlayer() instanceof Player player) {
            guiManager.register(player, gui);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (guiManager.handleClick(event)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof org.bukkit.entity.Player player) {
            guiManager.unregister(player);
        }
    }
}
