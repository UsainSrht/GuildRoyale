package me.usainsrht.guildroyale.core.gui;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
import me.usainsrht.guildroyale.api.domain.Guild;
import me.usainsrht.guildroyale.core.GuildRoyalePlugin;
import me.usainsrht.guildroyale.core.config.GuiConfig;
import me.usainsrht.guildroyale.core.feature.FeatureGate;
import me.usainsrht.guildroyale.core.feature.GuildFeature;
import me.usainsrht.guildroyale.core.service.GuildServiceImpl;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Helpers for loading YamlItem GUI buttons. */
public final class GuiItems {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

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

    /**
     * Unlocked item when the guild meets the feature level; otherwise the locked
     * variant with {@code <level>} set to the unlock requirement.
     */
    public static ItemStack featureItem(Guild guild, GuildFeature feature,
                                        String unlockedKey, String lockedKey) {
        GuildRoyalePlugin plugin = GuildRoyalePlugin.getInstance();
        if (plugin == null) {
            return get(unlockedKey);
        }
        FeatureGate gate = ((GuildServiceImpl) plugin.getGuildService()).featureGate();
        if (gate.isUnlocked(guild, feature)) {
            return get(unlockedKey);
        }
        return get(lockedKey, Placeholder.unparsed("level", String.valueOf(gate.unlockLevel(feature))));
    }

    public static boolean isFeatureUnlocked(Guild guild, GuildFeature feature) {
        GuildRoyalePlugin plugin = GuildRoyalePlugin.getInstance();
        if (plugin == null) {
            return true;
        }
        return ((GuildServiceImpl) plugin.getGuildService()).featureGate().isUnlocked(guild, feature);
    }

    /** Clone with extra lore lines inserted before a trailing empty line when present. */
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
        int insertAt = lore.size();
        if (insertAt > 0 && isBlankLine(lore.get(insertAt - 1))) {
            insertAt--;
        }
        lore.addAll(insertAt, extraLore);
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

    private static boolean isBlankLine(Component component) {
        return PLAIN.serialize(component).isEmpty();
    }
}
