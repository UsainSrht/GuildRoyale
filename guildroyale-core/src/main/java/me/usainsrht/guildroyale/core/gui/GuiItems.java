package me.usainsrht.guildroyale.core.gui;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
import me.usainsrht.guildroyale.core.GuildRoyalePlugin;
import me.usainsrht.guildroyale.core.config.GuiConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Helpers for loading YamlItem GUI buttons. */
public final class GuiItems {

    private GuiItems() {}

    public static @Nullable GuiConfig config() {
        GuildRoyalePlugin plugin = GuildRoyalePlugin.getInstance();
        return plugin != null ? plugin.getGuiConfig() : null;
    }

    public static ItemStack get(String key) {
        GuiConfig gui = config();
        if (gui == null) {
            return new ItemStack(Material.STONE);
        }
        ItemStack item = gui.item(key);
        return item != null ? item : new ItemStack(Material.STONE);
    }

    /** YamlItem with MiniMessage placeholders applied to name/lore. */
    public static ItemStack get(String key, TagResolver... resolvers) {
        GuiConfig gui = config();
        if (gui == null) {
            return new ItemStack(Material.STONE);
        }
        return gui.item(key, resolvers);
    }

    /** Clone with extra lore lines appended. */
    public static ItemStack withExtraLore(String key, @Nullable List<Component> extraLore) {
        ItemStack item = get(key);
        if (extraLore == null || extraLore.isEmpty()) {
            return item;
        }
        List<Component> lore = new ArrayList<>();
        ItemLore existing = item.getData(DataComponentTypes.LORE);
        if (existing != null) {
            lore.addAll(existing.lines());
        }
        lore.addAll(extraLore);
        item.setData(DataComponentTypes.LORE, ItemLore.lore(lore));
        return item;
    }

    public static ItemStack filler() {
        return get("gui-filler");
    }

    public static ItemStack borderFiller() {
        return get("gui-border-filler");
    }

    public static ItemStack innerFiller() {
        return get("gui-inner-filler");
    }
}
