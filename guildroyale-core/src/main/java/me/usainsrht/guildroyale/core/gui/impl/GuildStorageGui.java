package me.usainsrht.guildroyale.core.gui.impl;

import me.usainsrht.guildroyale.api.domain.Guild;
import me.usainsrht.guildroyale.api.domain.SerializableItemStack;
import me.usainsrht.guildroyale.core.GuildRoyalePlugin;
import me.usainsrht.guildroyale.core.adapter.ItemStackAdapter;
import me.usainsrht.guildroyale.core.config.GuiConfig;
import me.usainsrht.guildroyale.core.gui.AbstractGui;
import me.usainsrht.guildroyale.core.gui.GuiItems;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Shared guild chest. Clicks are allowed; contents are saved on close.
 */
public final class GuildStorageGui extends AbstractGui {

    private final GuildRoyalePlugin plugin;
    private final UUID guildId;
    private final Guild guild;

    public GuildStorageGui(GuildRoyalePlugin plugin, Guild guild, int size) {
        super(size, title(guild));
        this.plugin = plugin;
        this.guildId = guild.getId();
        this.guild = guild;
    }

    private static Component title(Guild guild) {
        GuiConfig gui = GuiItems.config();
        if (gui == null) {
            return Component.text("Guild Storage");
        }
        return gui.title("storage.title", Placeholder.unparsed("guild", guild.getName()));
    }

    @Override
    protected void build() {
        for (Map.Entry<Integer, SerializableItemStack> entry : guild.getStorage().entrySet()) {
            int slot = entry.getKey();
            if (slot < 0 || slot >= size) continue;
            ItemStack stack = ItemStackAdapter.fromSerializable(entry.getValue());
            if (stack.getType().isAir()) continue;
            setSlot(slot, stack);
        }
    }

    @Override
    public boolean onClick(InventoryClickEvent event) {
        // Allow normal chest interaction
        return false;
    }

    @Override
    public void onClose(Player player) {
        if (inventory == null) return;
        Map<Integer, SerializableItemStack> contents = new TreeMap<>();
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            SerializableItemStack serializable = ItemStackAdapter.toSerializable(stack);
            if (!serializable.isEmpty()) {
                contents.put(i, serializable);
            }
        }
        plugin.getScheduler().runAsync(() ->
                plugin.getGuildService().saveStorage(guildId, player.getUniqueId(), contents));
    }
}
