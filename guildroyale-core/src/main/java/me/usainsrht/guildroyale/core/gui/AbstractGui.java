package me.usainsrht.guildroyale.core.gui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/**
 * Base class for all GuildRoyale GUIs.
 *
 * <p>Subclasses implement {@link #build()} to populate items and
 * {@link #onClick(InventoryClickEvent)} to handle clicks.
 *
 * <p>Since {@link InventoryClickEvent} fires on the player's region thread in Folia,
 * it is safe to read and modify the player's inventory directly inside
 * {@link #onClick(InventoryClickEvent)}.
 */
public abstract class AbstractGui implements InventoryHolder {

    protected final int size;
    protected final String title;
    protected Inventory inventory;

    protected AbstractGui(int size, String title) {
        this.size = size;
        this.title = title;
    }

    /** Populates the inventory with items. Called once before opening. */
    protected abstract void build();

    /**
     * Handles a click inside this GUI.
     *
     * @return {@code true} if the event should be cancelled (prevents item pickup)
     */
    public abstract boolean onClick(InventoryClickEvent event);

    /** Opens this GUI for the player. Always call on the player's region thread. */
    public void open(Player player) {
        this.inventory = Bukkit.createInventory(this, size, net.kyori.adventure.text.Component.text(title));
        build();
        player.openInventory(inventory);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    protected void setSlot(int slot, org.bukkit.inventory.ItemStack item) {
        if (inventory != null && slot >= 0 && slot < inventory.getSize()) {
            inventory.setItem(slot, item);
        }
    }
}
