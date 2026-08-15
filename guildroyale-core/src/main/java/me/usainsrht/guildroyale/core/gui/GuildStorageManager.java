package me.usainsrht.guildroyale.core.gui;

import me.usainsrht.guildroyale.api.domain.Guild;
import me.usainsrht.guildroyale.api.domain.SerializableItemStack;
import me.usainsrht.guildroyale.core.GuildRoyalePlugin;
import me.usainsrht.guildroyale.core.adapter.ItemStackAdapter;
import me.usainsrht.guildroyale.core.gui.impl.GuildStorageGui;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Manages active shared {@link GuildStorageGui} inventory instances per guild and page.
 *
 * <p>Ensures all players viewing the same storage page open the exact same Bukkit {@link Inventory}
 * instance, guaranteeing real-time native container synchronization and preventing duplication exploits.
 */
public final class GuildStorageManager {

    private final GuildRoyalePlugin plugin;
    // Map<guildId, Map<page, GuildStorageGui>>
    private final Map<UUID, Map<Integer, GuildStorageGui>> activeGuis = new ConcurrentHashMap<>();

    public GuildStorageManager(GuildRoyalePlugin plugin) {
        this.plugin = plugin;
    }

    public void openStorage(Player player, Guild guild, int page, Consumer<Player> returnTo) {
        if (guild == null || player == null) return;
        UUID guildId = guild.getId();

        Map<Integer, GuildStorageGui> pages = activeGuis.computeIfAbsent(guildId, k -> new ConcurrentHashMap<>());
        GuildStorageGui gui = pages.computeIfAbsent(page, p -> new GuildStorageGui(plugin, guild, p, this));

        gui.returnTo(returnTo);
        gui.open(player);
    }

    public void openStorage(Player player, Guild guild, int page) {
        openStorage(player, guild, page, null);
    }

    /**
     * Synchronizes and saves the items of a storage GUI page back to the guild model and storage.
     */
    public void savePageContents(GuildStorageGui gui, Player player) {
        if (gui == null) return;
        Guild guild = gui.getGuild();
        Inventory inv = gui.getInventory();
        if (inv == null || guild == null) return;

        int page = gui.getPage();
        int slotsPerPage = gui.getSlotsPerPage();
        int unlockedSlots = plugin.getConfigManager().getStorageUnlockedSlots(guild.getLevel());

        Map<Integer, SerializableItemStack> currentStorage = new TreeMap<>(guild.getStorage());

        for (int i = 0; i < slotsPerPage; i++) {
            int globalIndex = page * slotsPerPage + i;
            if (globalIndex >= unlockedSlots) continue;

            ItemStack stack = inv.getItem(i);
            SerializableItemStack serializable = ItemStackAdapter.toSerializable(stack);
            if (serializable.isEmpty()) {
                currentStorage.remove(globalIndex);
            } else {
                currentStorage.put(globalIndex, serializable);
            }
        }

        guild.setStorageContents(currentStorage);

        UUID playerId = player != null ? player.getUniqueId() : guild.getId();
        plugin.getScheduler().runAsync(() ->
                plugin.getGuildService().saveStorage(guild.getId(), playerId, currentStorage));
    }

    /**
     * Called when a player closes a storage inventory.
     */
    public void handleClose(GuildStorageGui gui, Player player) {
        savePageContents(gui, player);

        // Check remaining viewers (excluding the player closing)
        Inventory inv = gui.getInventory();
        long viewerCount = inv != null ? inv.getViewers().stream().filter(v -> !v.equals(player)).count() : 0;

        if (viewerCount <= 0) {
            Map<Integer, GuildStorageGui> pages = activeGuis.get(gui.getGuild().getId());
            if (pages != null) {
                pages.remove(gui.getPage());
                if (pages.isEmpty()) {
                    activeGuis.remove(gui.getGuild().getId());
                }
            }
        }
    }

    /**
     * Refreshes active storage GUIs for a guild (e.g. after leveling up).
     */
    public void refreshGuildStorage(Guild guild) {
        if (guild == null) return;
        Map<Integer, GuildStorageGui> pages = activeGuis.get(guild.getId());
        if (pages != null) {
            pages.values().forEach(GuildStorageGui::refreshUnlockedSlots);
        }
    }

    /**
     * Clears all cached GUI references.
     */
    public void clear() {
        activeGuis.clear();
    }
}
