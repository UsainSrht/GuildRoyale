package me.usainsrht.guildroyale.core.gui.impl;

import me.usainsrht.guildroyale.api.domain.SerializableItemStack;
import me.usainsrht.guildroyale.core.adapter.ItemStackAdapter;
import me.usainsrht.guildroyale.core.config.GuiConfig;
import me.usainsrht.guildroyale.core.gui.AbstractGui;
import me.usainsrht.guildroyale.core.gui.GuiItems;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.function.Consumer;

/**
 * Opens the player's own inventory as a selection GUI.
 * When the player clicks an item, it is captured as the new icon and the
 * {@code callback} is invoked with the serialised form.
 */
public final class IconSelectionGui extends AbstractGui {

    private final Player targetPlayer;
    private final Consumer<SerializableItemStack> callback;
    private final int contentSlots;
    private final int instructionSlot;
    private final int instructionRowStart;
    private final int instructionRowEnd;

    public IconSelectionGui(Player targetPlayer, Consumer<SerializableItemStack> callback) {
        super(size(), title());
        this.targetPlayer = targetPlayer;
        this.callback = callback;

        GuiConfig gui = GuiItems.config();
        this.contentSlots = gui != null ? gui.pageSize("icon-selection.content-slots", 36) : 36;
        this.instructionSlot = gui != null ? gui.slot("icon-selection.instruction-slot", 49) : 49;
        this.instructionRowStart = gui != null ? gui.slot("icon-selection.instruction-row-start", 45) : 45;
        this.instructionRowEnd = gui != null ? gui.slot("icon-selection.instruction-row-end", 54) : 54;
    }

    private static int size() {
        GuiConfig gui = GuiItems.config();
        return gui != null ? gui.size("icon-selection.size", 54) : 54;
    }

    private static Component title() {
        GuiConfig gui = GuiItems.config();
        if (gui == null) {
            return Component.text("Select an icon");
        }
        return gui.title("icon-selection.title");
    }

    @Override
    protected void build() {
        ItemStack[] contents = targetPlayer.getInventory().getContents();
        for (int i = 0; i < Math.min(contents.length, contentSlots); i++) {
            if (contents[i] != null) setSlot(i, contents[i].clone());
        }

        ItemStack pane = GuiItems.get("icon-selection-pane");
        ItemStack instruction = GuiItems.get("icon-selection-instruction");
        for (int i = instructionRowStart; i < instructionRowEnd; i++) {
            setSlot(i, i == instructionSlot ? instruction : pane.clone());
        }
    }

    @Override
    public boolean onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return true;
        ItemStack clicked = event.getCurrentItem();
        if (clicked != null && !clicked.getType().isAir() && event.getRawSlot() < contentSlots) {
            SerializableItemStack icon = ItemStackAdapter.toSerializable(clicked);
            player.closeInventory();
            callback.accept(icon);
        }
        return true;
    }
}
