package dev.guildroyale.core.gui.impl;

import dev.guildroyale.api.domain.SerializableItemStack;
import dev.guildroyale.core.adapter.ItemStackAdapter;
import dev.guildroyale.core.gui.AbstractGui;
import dev.guildroyale.core.gui.GuiManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.function.Consumer;

/**
 * Opens the player's own inventory as a selection GUI.
 * When the player clicks an item, it is captured as the new icon and the
 * {@code callback} is invoked with the serialised form.
 *
 * <p>The inventory is NOT a new chest — it mirrors the player's contents.
 * We achieve this by showing the actual player inventory and treating item
 * clicks as selections.
 */
public final class IconSelectionGui extends AbstractGui {

    private final Player targetPlayer;
    private final Consumer<SerializableItemStack> callback;

    public IconSelectionGui(Player targetPlayer, Consumer<SerializableItemStack> callback) {
        super(54, "Select an icon — click any item");
        this.targetPlayer = targetPlayer;
        this.callback = callback;
    }

    @Override
    protected void build() {
        // Mirror player inventory contents into the top 36 slots
        ItemStack[] contents = targetPlayer.getInventory().getContents();
        for (int i = 0; i < Math.min(contents.length, 36); i++) {
            if (contents[i] != null) setSlot(i, contents[i].clone());
        }

        // Instruction row
        for (int i = 45; i < 54; i++) {
            ItemStack pane = new ItemStack(org.bukkit.Material.CYAN_STAINED_GLASS_PANE);
            ItemMeta pm = pane.getItemMeta();
            pm.displayName(i == 49
                    ? Component.text("Click any item above to select it as the icon", NamedTextColor.AQUA)
                            .decoration(TextDecoration.ITALIC, false)
                    : Component.text(" ").decoration(TextDecoration.ITALIC, false));
            pane.setItemMeta(pm);
            setSlot(i, pane);
        }
    }

    @Override
    public boolean onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return true;
        ItemStack clicked = event.getCurrentItem();
        if (clicked != null && !clicked.getType().isAir() && event.getRawSlot() < 36) {
            SerializableItemStack icon = ItemStackAdapter.toSerializable(clicked);
            player.closeInventory();
            callback.accept(icon);
        }
        return true;
    }
}
