package dev.guildroyale.core.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks open GUIs per player and dispatches inventory click events to the correct
 * {@link AbstractGui} instance.
 *
 * <p>GuiManager is accessed from the {@link dev.guildroyale.core.listener.GuiListener}
 * which runs on the player's region thread — no synchronisation is required for
 * per-player access, but we use a {@link ConcurrentHashMap} for safety across
 * different region threads on Folia.
 */
public final class GuiManager {

    private final Map<UUID, AbstractGui> openGuis = new ConcurrentHashMap<>();

    /** Records that a player opened a GUI. Called from {@link AbstractGui#open(Player)}. */
    public void register(Player player, AbstractGui gui) {
        openGuis.put(player.getUniqueId(), gui);
    }

    /** Called when a player closes an inventory. */
    public void unregister(Player player) {
        openGuis.remove(player.getUniqueId());
    }

    /**
     * Dispatches a click event. Returns {@code true} if the event was handled by a
     * registered GUI (caller should then cancel the event).
     */
    public boolean handleClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return false;
        AbstractGui gui = openGuis.get(player.getUniqueId());
        if (gui == null) return false;
        // Verify the clicked inventory belongs to this GUI (not the player's own inventory)
        if (event.getClickedInventory() == null) return false;
        InventoryHolder holder = event.getClickedInventory().getHolder();
        if (holder != gui) return false;
        return gui.onClick(event);
    }

    /** Returns the currently-open GUI for a player, or {@code null}. */
    public AbstractGui getOpenGui(UUID playerId) {
        return openGuis.get(playerId);
    }

    /** Removes all tracked GUIs (called on plugin disable). */
    public void clear() {
        openGuis.clear();
    }
}
