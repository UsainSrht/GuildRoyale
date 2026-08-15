package me.usainsrht.guildroyale.core.gui.impl;

import me.usainsrht.guildroyale.api.domain.Guild;
import me.usainsrht.guildroyale.api.domain.SerializableItemStack;
import me.usainsrht.guildroyale.core.GuildRoyalePlugin;
import me.usainsrht.guildroyale.core.adapter.ItemStackAdapter;
import me.usainsrht.guildroyale.core.config.GuiConfig;
import me.usainsrht.guildroyale.core.gui.AbstractGui;
import me.usainsrht.guildroyale.core.gui.GuiItems;
import me.usainsrht.guildroyale.core.gui.GuildStorageManager;
import me.usainsrht.guildroyale.core.message.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * Shared multi-page guild storage container GUI.
 *
 * <p>Preserves standard GUI row size (default 54/6-row), keeping the bottom row strictly for buttons
 * (previous page, back, next page, fillers). Unearned slots are populated with locked item placeholders
 * displaying level requirements.
 */
public final class GuildStorageGui extends AbstractGui {

    private final GuildRoyalePlugin plugin;
    private final Guild guild;
    private final int page;
    private final GuildStorageManager storageManager;

    private final int slotsPerPage;
    private final int previousSlot;
    private final int backSlot;
    private final int nextSlot;

    public GuildStorageGui(GuildRoyalePlugin plugin, Guild guild, int page, GuildStorageManager storageManager) {
        super(guiSize(), guiTitle(guild, page, plugin.getConfigManager().getStoragePageCount(calculateSlotsPerPage())));
        this.plugin = plugin;
        this.guild = guild;
        this.page = page;
        this.storageManager = storageManager;

        this.slotsPerPage = calculateSlotsPerPage();
        GuiConfig gui = GuiItems.config();
        this.previousSlot = gui != null ? gui.slot("storage.slots.previous", size - 9) : size - 9;
        this.backSlot = gui != null ? gui.slot("storage.slots.back", size - 5) : size - 5;
        this.nextSlot = gui != null ? gui.slot("storage.slots.next", size - 1) : size - 1;

        // Pre-create the shared Inventory instance
        this.inventory = plugin.getServer().createInventory(this, size, title);
        build();
    }

    private static int guiSize() {
        GuiConfig gui = GuiItems.config();
        return gui != null ? gui.size("storage.size", 54) : 54;
    }

    private static int calculateSlotsPerPage() {
        int totalSize = guiSize();
        int rows = Math.max(2, totalSize / 9);
        return (rows - 1) * 9;
    }

    private static Component guiTitle(Guild guild, int page, int maxPage) {
        GuiConfig gui = GuiItems.config();
        if (gui == null) {
            return Component.text("Guild Storage");
        }
        if (maxPage > 1) {
            return gui.title("storage.title-paged",
                    Placeholder.unparsed("guild", guild.getName()),
                    Placeholder.unparsed("page", String.valueOf(page + 1)),
                    Placeholder.unparsed("max_page", String.valueOf(maxPage)));
        }
        return gui.title("storage.title", Placeholder.unparsed("guild", guild.getName()));
    }

    public Guild getGuild() {
        return guild;
    }

    public int getPage() {
        return page;
    }

    public int getSlotsPerPage() {
        return slotsPerPage;
    }

    @Override
    protected void build() {
        refreshUnlockedSlots();

        // Build bottom row (navigation controls and fillers)
        int navStart = slotsPerPage;
        ItemStack filler = GuiItems.get("gui-border-filler");
        if (filler.getType().isAir()) {
            filler = GuiItems.filler();
        }
        for (int i = navStart; i < size; i++) {
            setSlot(i, filler.clone());
        }

        int maxPages = plugin.getConfigManager().getStoragePageCount(slotsPerPage);

        if (page > 0) {
            setSlot(previousSlot, GuiItems.get("gui-previous"));
        }

        setSlot(backSlot, navBackItem());

        if (page < maxPages - 1) {
            setSlot(nextSlot, GuiItems.get("gui-next"));
        }
    }

    /**
     * Refreshes item slots in the storage grid (slots 0 to slotsPerPage - 1).
     */
    public void refreshUnlockedSlots() {
        int unlockedSlots = plugin.getConfigManager().getStorageUnlockedSlots(guild.getLevel());
        int maxSlots = plugin.getConfigManager().getStorageMaxSlots();
        Map<Integer, SerializableItemStack> storageData = guild.getStorage();

        for (int i = 0; i < slotsPerPage; i++) {
            int globalIndex = page * slotsPerPage + i;
            if (globalIndex >= maxSlots) {
                setSlot(i, GuiItems.get("gui-border-filler"));
            } else if (globalIndex >= unlockedSlots) {
                int reqLevel = plugin.getConfigManager().getRequiredLevelForSlot(globalIndex);
                ItemStack lockedItem = GuiItems.get("storage-locked-slot",
                        Placeholder.unparsed("required_level", String.valueOf(reqLevel)),
                        Placeholder.unparsed("slot", String.valueOf(globalIndex + 1)));
                setSlot(i, lockedItem);
            } else {
                SerializableItemStack serializable = storageData.get(globalIndex);
                if (serializable != null && !serializable.isEmpty()) {
                    setSlot(i, ItemStackAdapter.fromSerializable(serializable));
                } else {
                    setSlot(i, null);
                }
            }
        }
    }

    @Override
    public boolean onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return true;

        int rawSlot = event.getRawSlot();
        boolean isTopInventory = event.getClickedInventory() != null && event.getClickedInventory().equals(inventory);

        // Click in top inventory
        if (isTopInventory && rawSlot >= 0 && rawSlot < size) {
            // Bottom row navigation row
            if (rawSlot >= slotsPerPage) {
                int maxPages = plugin.getConfigManager().getStoragePageCount(slotsPerPage);
                if (rawSlot == previousSlot && page > 0) {
                    storageManager.openStorage(player, guild, page - 1, p -> navigateBack(p));
                } else if (rawSlot == nextSlot && page < maxPages - 1) {
                    storageManager.openStorage(player, guild, page + 1, p -> navigateBack(p));
                } else if (rawSlot == backSlot) {
                    navigateBack(player);
                }
                return true; // Cancel navigation button clicks
            }

            // Storage grid (slots 0 to slotsPerPage - 1)
            int globalIndex = page * slotsPerPage + rawSlot;
            int unlockedSlots = plugin.getConfigManager().getStorageUnlockedSlots(guild.getLevel());

            if (globalIndex >= unlockedSlots) {
                int reqLevel = plugin.getConfigManager().getRequiredLevelForSlot(globalIndex);
                plugin.getMessages().send(player, "storage-slot-locked",
                        Placeholder.unparsed("level", String.valueOf(reqLevel)));
                return true; // Cancel click on locked slot
            }

            // Unlocked slot interaction: allow standard chest click and schedule sync
            plugin.getScheduler().runForEntity(player, () -> storageManager.savePageContents(this, player));
            return false;
        }

        // Click in bottom inventory (player inventory)
        if (event.isShiftClick()) {
            // Check if top inventory has space in unlocked slots on this page
            int unlockedSlots = plugin.getConfigManager().getStorageUnlockedSlots(guild.getLevel());
            int unlockedSlotsOnPage = Math.max(0, Math.min(slotsPerPage, unlockedSlots - page * slotsPerPage));

            if (unlockedSlotsOnPage <= 0 || isStorageGridFull(unlockedSlotsOnPage)) {
                return true; // Cancel shift-click if no unlocked slots available
            }

            // Schedule sync after shift-click completes
            plugin.getScheduler().runForEntity(player, () -> storageManager.savePageContents(this, player));
        }

        return false;
    }

    private boolean isStorageGridFull(int unlockedCountOnPage) {
        if (inventory == null) return true;
        for (int i = 0; i < unlockedCountOnPage; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack == null || stack.getType().isAir()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean onDrag(InventoryDragEvent event) {
        int unlockedSlots = plugin.getConfigManager().getStorageUnlockedSlots(guild.getLevel());

        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot >= 0 && rawSlot < size) {
                if (rawSlot >= slotsPerPage) {
                    return true; // Dragged into navigation row
                }
                int globalIndex = page * slotsPerPage + rawSlot;
                if (globalIndex >= unlockedSlots) {
                    return true; // Dragged into locked slot
                }
            }
        }

        if (event.getWhoClicked() instanceof Player player) {
            plugin.getScheduler().runForEntity(player, () -> storageManager.savePageContents(this, player));
        }

        return false;
    }

    @Override
    public void onClose(Player player) {
        storageManager.handleClose(this, player);
    }
}
