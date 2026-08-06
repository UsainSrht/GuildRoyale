package me.usainsrht.guildroyale.core.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Base class for all GuildRoyale GUIs.
 *
 * <p>Subclasses implement {@link #build()} to populate items and
 * {@link #onClick(InventoryClickEvent)} to handle clicks.
 *
 * <p>Optional {@linkplain #returnTo(Consumer) return navigation} records whether this
 * GUI was opened from another menu (back) or directly (close).
 */
public abstract class AbstractGui implements InventoryHolder {

    protected final int size;
    protected final Component title;
    protected Inventory inventory;
    private @Nullable Consumer<Player> returnTo;

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

    /**
     * Sets where Back should go. {@code null} means this GUI was opened directly
     * (e.g. via command) and Back should close the inventory instead.
     *
     * @return {@code this} for chaining before {@link #open(Player)}
     */
    public AbstractGui returnTo(@Nullable Consumer<Player> returnTo) {
        this.returnTo = returnTo;
        return this;
    }

    /** Copies return navigation from another GUI (e.g. when changing pages). */
    public AbstractGui returnTo(@Nullable AbstractGui from) {
        this.returnTo = from != null ? from.returnTo : null;
        return this;
    }

    /** Whether a previous GUI should be reopened on Back. */
    protected boolean hasReturn() {
        return returnTo != null;
    }

    /** Reopens the previous GUI, or closes if this was opened directly. */
    protected void navigateBack(Player player) {
        if (returnTo != null) {
            returnTo.accept(player);
        } else {
            player.closeInventory();
        }
    }

    /** Back arrow when nested; close barrier when opened directly. */
    protected ItemStack navBackItem() {
        return GuiItems.get(hasReturn() ? "gui-back" : "gui-close");
    }

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
