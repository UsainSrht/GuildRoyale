package me.usainsrht.guildroyale.core.gui;

import me.usainsrht.guildroyale.core.config.GuiConfig;
import me.usainsrht.guildroyale.core.message.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Standard GUI template for listing elements (members, roles, settings, permissions, etc.).
 *
 * <p>Design Rules:
 * <ul>
 *   <li><b>No border slots:</b> Border slots are slots with fewer than 8 neighbour slots.
 *       Only inner slots (with 8 neighbours) are used for listing items.
 *       For example, a 3-row GUI (27 slots) has only 7 content slots available: 10..16.</li>
 *   <li><b>Action Buttons:</b> Placed on the last row:
 *       <ul>
 *         <li>Previous page: bottom left (column 0)</li>
 *         <li>Other action 2: bottom center - 2 (column 2)</li>
 *         <li>Back: bottom center (column 4)</li>
 *         <li>Other action 1: bottom center + 2 (column 6)</li>
 *         <li>Next page: bottom right (column 8)</li>
 *         <li>Top action: top row center (slot 4) if necessary when out of slots.</li>
 *       </ul>
 *   </li>
 *   <li><b>Background Fillers:</b>
 *       <ul>
 *         <li>Border filler: black stained glass pane, name " ", hide-tooltip: true.</li>
 *         <li>Inner filler: air (default).</li>
 *       </ul>
 *   </li>
 *   <li><b>Title:</b> Bold, gradient preferred, with {@code <shadow:#000000FF>}.</li>
 * </ul>
 *
 * @param <T> Type of element being listed
 */
public abstract class StandardListGui<T> extends AbstractGui {

    protected final List<T> items;
    protected final int page;
    protected final int rows;
    protected final List<Integer> innerSlots;
    protected final int pageSize;

    // Action slots
    protected final int previousSlot;
    protected final int nextSlot;
    protected final int backSlot;
    protected final int action1Slot;
    protected final int action2Slot;
    protected final int topActionSlot;

    // Fillers
    protected final ItemStack borderFillerItem;
    protected final ItemStack innerFillerItem;

    protected StandardListGui(int size, Component title, List<T> items, int page) {
        this(size, title, items, page, null);
    }

    protected StandardListGui(int size, Component title, List<T> items, int page, @Nullable String configPrefix) {
        super(normalizeSize(size), formatTitleComponent(title));
        this.items = items != null ? items : Collections.emptyList();
        this.rows = this.size / 9;
        this.innerSlots = calculateInnerSlots(this.rows);
        this.pageSize = Math.max(1, this.innerSlots.size());

        int lastRowStart = (this.rows - 1) * 9;
        GuiConfig config = GuiItems.config();

        if (configPrefix != null && config != null) {
            this.previousSlot = config.slot(configPrefix + ".slots.previous", lastRowStart + 0);
            this.nextSlot = config.slot(configPrefix + ".slots.next", lastRowStart + 8);
            this.backSlot = config.slot(configPrefix + ".slots.back", lastRowStart + 4);
            this.action1Slot = config.slot(configPrefix + ".slots.action1", lastRowStart + 6);
            this.action2Slot = config.slot(configPrefix + ".slots.action2", lastRowStart + 2);
            this.topActionSlot = config.slot(configPrefix + ".slots.top-action", 4);

            // Per-GUI overrides point at item keys under items: (same as shared fillers).
            String borderKey = config.string(configPrefix + ".border-filler", "gui-border-filler");
            this.borderFillerItem = GuiItems.getOr(borderKey, new ItemStack(Material.BLACK_STAINED_GLASS_PANE));

            String innerKey = config.string(configPrefix + ".inner-filler", "gui-inner-filler");
            this.innerFillerItem = GuiItems.getOr(innerKey, new ItemStack(Material.AIR));
        } else {
            this.previousSlot = lastRowStart + 0;
            this.nextSlot = lastRowStart + 8;
            this.backSlot = lastRowStart + 4;
            this.action1Slot = lastRowStart + 6;
            this.action2Slot = lastRowStart + 2;
            this.topActionSlot = 4;

            this.borderFillerItem = GuiItems.borderFiller();
            this.innerFillerItem = GuiItems.innerFiller();
        }

        int maxPages = Math.max(1, (int) Math.ceil(this.items.size() / (double) this.pageSize));
        this.page = Math.max(0, Math.min(page, maxPages - 1));
    }

    private static int normalizeSize(int size) {
        if (size < 27) size = 27; // Minimum 3 rows to have border + inner slots
        if (size > 54) size = 54;
        return (size / 9) * 9;
    }

    /**
     * Checks if a slot index is a border slot (has fewer than 8 neighbours).
     */
    public static boolean isBorderSlot(int slot, int rows) {
        int r = slot / 9;
        int c = slot % 9;
        return r == 0 || r == rows - 1 || c == 0 || c == 8;
    }

    /**
     * Calculates inner slots (slots with exactly 8 neighbours).
     */
    public static List<Integer> calculateInnerSlots(int rows) {
        List<Integer> slots = new ArrayList<>();
        for (int r = 1; r < rows - 1; r++) {
            for (int c = 1; c < 8; c++) {
                slots.add(r * 9 + c);
            }
        }
        return slots;
    }

    /**
     * Formats titles according to the standard GUI title style: bold, shadow, and gradient.
     */
    public static Component formatStandardTitle(String rawTitle, TagResolver... resolvers) {
        if (rawTitle == null || rawTitle.isEmpty()) {
            rawTitle = "GUI";
        }
        String formatted = rawTitle;
        if (!formatted.contains("<shadow:")) {
            formatted = "<shadow:#000000FF>" + formatted + "</shadow>";
        }
        if (!formatted.contains("<bold>") && !formatted.contains("<b>")) {
            formatted = "<bold>" + formatted + "</bold>";
        }
        if (!formatted.contains("<gradient:")) {
            formatted = "<gradient:#f0c14b:#ffe08a>" + formatted + "</gradient>";
        }
        return Text.parse(formatted, resolvers).decoration(TextDecoration.ITALIC, false);
    }

    private static Component formatTitleComponent(Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }

    @Override
    protected void build() {
        // 1. Fill background (border filler on border slots, inner filler on inner slots)
        for (int i = 0; i < size; i++) {
            if (isBorderSlot(i, rows)) {
                if (borderFillerItem != null && !borderFillerItem.getType().isAir()) {
                    setSlot(i, borderFillerItem.clone());
                }
            } else {
                if (innerFillerItem != null && !innerFillerItem.getType().isAir()) {
                    setSlot(i, innerFillerItem.clone());
                }
            }
        }

        // 2. Render Action Buttons
        // Previous/next only appear when that page exists.
        int maxPages = Math.max(1, (int) Math.ceil(items.size() / (double) pageSize));

        if (page > 0) {
            ItemStack prevItem = getPreviousPageItem();
            if (prevItem != null && !prevItem.getType().isAir()) {
                setSlot(previousSlot, prevItem);
            }
        }

        if (page < maxPages - 1) {
            ItemStack nextItem = getNextPageItem();
            if (nextItem != null && !nextItem.getType().isAir()) {
                setSlot(nextSlot, nextItem);
            }
        }

        ItemStack backItem = getBackItem();
        if (backItem != null && !backItem.getType().isAir()) {
            setSlot(backSlot, backItem);
        }

        ItemStack action1Item = getAction1Item();
        if (action1Item != null && !action1Item.getType().isAir()) {
            setSlot(action1Slot, action1Item);
        }

        ItemStack action2Item = getAction2Item();
        if (action2Item != null && !action2Item.getType().isAir()) {
            setSlot(action2Slot, action2Item);
        }

        ItemStack topActionItem = getTopActionItem();
        if (topActionItem != null && !topActionItem.getType().isAir()) {
            setSlot(topActionSlot, topActionItem);
        }

        // 3. Render Listed Items into Inner Slots ONLY
        int startIdx = page * pageSize;
        for (int i = 0; i < innerSlots.size() && (startIdx + i) < items.size(); i++) {
            int targetSlot = innerSlots.get(i);
            T itemData = items.get(startIdx + i);
            ItemStack stack = renderItem(itemData, startIdx + i);
            if (stack != null) {
                setSlot(targetSlot, stack);
            }
        }
    }

    @Override
    public boolean onClick(InventoryClickEvent event) {
        int rawSlot = event.getRawSlot();
        if (!(event.getWhoClicked() instanceof Player player)) return true;

        int maxPages = Math.max(1, (int) Math.ceil(items.size() / (double) pageSize));

        if (rawSlot == previousSlot && page > 0) {
            onPreviousPage(player);
            return true;
        }
        if (rawSlot == nextSlot && page < maxPages - 1) {
            onNextPage(player);
            return true;
        }
        if (rawSlot == backSlot) {
            onBack(player);
            return true;
        }
        if (rawSlot == action1Slot && getAction1Item() != null) {
            onAction1(player);
            return true;
        }
        if (rawSlot == action2Slot && getAction2Item() != null) {
            onAction2(player);
            return true;
        }
        if (rawSlot == topActionSlot && getTopActionItem() != null) {
            onTopAction(player);
            return true;
        }

        int innerIndex = innerSlots.indexOf(rawSlot);
        if (innerIndex != -1) {
            int itemIndex = page * pageSize + innerIndex;
            if (itemIndex < items.size()) {
                onItemClick(event, items.get(itemIndex), itemIndex);
            }
        }

        return true;
    }

    /** Renders an element into an ItemStack. */
    protected abstract ItemStack renderItem(T item, int index);

    /** Called when an inner element is clicked. */
    protected abstract void onItemClick(InventoryClickEvent event, T item, int index);

    /** Called when the previous page button is clicked. */
    protected void onPreviousPage(Player player) {}

    /** Called when the next page button is clicked. */
    protected void onNextPage(Player player) {}

    /** Called when the back/close button (bottom center) is clicked. */
    protected void onBack(Player player) {
        navigateBack(player);
    }

    /** Called when action 1 button (bottom center + 2) is clicked. */
    protected void onAction1(Player player) {}

    /** Called when action 2 button (bottom center - 2) is clicked. */
    protected void onAction2(Player player) {}

    /** Called when top action button (top row center: slot 4) is clicked. */
    protected void onTopAction(Player player) {}

    protected @Nullable ItemStack getPreviousPageItem() {
        return GuiItems.get("gui-previous");
    }

    protected @Nullable ItemStack getNextPageItem() {
        return GuiItems.get("gui-next");
    }

    protected @Nullable ItemStack getBackItem() {
        return navBackItem();
    }

    protected @Nullable ItemStack getAction1Item() {
        return null;
    }

    protected @Nullable ItemStack getAction2Item() {
        return null;
    }

    protected @Nullable ItemStack getTopActionItem() {
        return null;
    }

    public List<T> getItems() {
        return items;
    }

    public int getPage() {
        return page;
    }

    public int getPageSize() {
        return pageSize;
    }
}
