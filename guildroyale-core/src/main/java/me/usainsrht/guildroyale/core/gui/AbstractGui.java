package me.usainsrht.guildroyale.core.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Base class for all GuildRoyale GUIs.
 *
 * <p>Subclasses implement {@link #build()} to populate items and
 * {@link #onClick(InventoryClickEvent)} to handle clicks.
 */
public abstract class AbstractGui implements InventoryHolder {

    protected final int size;
    protected final Component title;
    protected Inventory inventory;

    protected AbstractGui(int size, Component title) {
        this.size = size;
        this.title = title;
    }

    /** @deprecated prefer {@link #AbstractGui(int, Component)} with MiniMessage titles */
    @Deprecated
    protected AbstractGui(int size, String title) {
        this(size, Component.text(title));
    }

    protected abstract void build();

    /**
     * Handles a click inside this GUI.
     *
     * @return {@code true} if the event should be cancelled (prevents item pickup)
     */
    public abstract boolean onClick(InventoryClickEvent event);

    public void onClose(Player player) {}

    public void open(Player player) {
        this.inventory = Bukkit.createInventory(this, size, title);
        build();
        player.openInventory(inventory);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    protected void setSlot(int slot, ItemStack item) {
        if (inventory != null && slot >= 0 && slot < inventory.getSize() && item != null) {
            inventory.setItem(slot, item);
        }
    }

    protected void fillBorder(ItemStack pane) {
        if (inventory == null) return;
        int rows = size / 9;
        for (int i = 0; i < 9; i++) {
            setSlot(i, pane.clone());
            setSlot((rows - 1) * 9 + i, pane.clone());
        }
        for (int row = 1; row < rows - 1; row++) {
            setSlot(row * 9, pane.clone());
            setSlot(row * 9 + 8, pane.clone());
        }
    }
}
