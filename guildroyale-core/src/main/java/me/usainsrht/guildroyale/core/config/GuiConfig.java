package me.usainsrht.guildroyale.core.config;

import me.usainsrht.guildroyale.core.message.Text;
import me.usainsrht.itemapi.yamlitem.YamlItem;
import me.usainsrht.itemapi.yamlitem.YamlParseException;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

/**
 * Loads {@code guis.yml} — titles, slots, and YamlItem definitions for GUIs.
 */
public final class GuiConfig {

    private final JavaPlugin plugin;
    private YamlConfiguration config;
    private final Map<String, ItemStack> items = new HashMap<>();

    public GuiConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        items.clear();
        File file = new File(plugin.getDataFolder(), "guis.yml");
        if (!file.exists()) {
            plugin.saveResource("guis.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(file);

        InputStream defaultStream = plugin.getResource("guis.yml");
        if (defaultStream != null) {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
            config.setDefaults(defaults);
            config.options().copyDefaults(true);
        }

        ConfigurationSection itemsSection = config.getConfigurationSection("items");
        if (itemsSection != null) {
            for (String key : itemsSection.getKeys(false)) {
                ConfigurationSection section = itemsSection.getConfigurationSection(key);
                if (section == null) {
                    continue;
                }
                // Air is a valid empty filler; YamlItem cannot build AIR stacks.
                if (isAirMaterial(section.getString("material"))) {
                    items.put(key.toLowerCase(Locale.ROOT), new ItemStack(Material.AIR));
                    continue;
                }
                try {
                    ItemStack parsed = YamlItem.parse(section);
                    if (section.getBoolean("hide-tooltip", false) || section.getBoolean("hide_tooltip", false)) {
                        applyHideTooltip(parsed);
                    }
                    items.put(key.toLowerCase(Locale.ROOT), parsed);
                } catch (YamlParseException ex) {
                    plugin.getLogger().log(Level.WARNING, "Failed to parse GUI item '" + key + "': " + ex.getMessage());
                }
            }
        }
        plugin.getLogger().info("Loaded " + items.size() + " GUI item(s) from guis.yml");
    }

    public Component title(String path, TagResolver... resolvers) {
        return component(path, "<red>Missing title: " + path, resolvers);
    }

    /**
     * Resolves a GUI title, using {@code prefix.title-paged} when there is more
     * than one page so the non-bold {@code page (current/max)} suffix can appear.
     */
    public Component listTitle(String prefix, int currentPage, int maxPage, TagResolver... resolvers) {
        String path = maxPage > 1 ? prefix + ".title-paged" : prefix + ".title";
        String fallback = maxPage > 1
                ? "<shadow:#000000FF><gradient:#f0c14b:#ffe08a><bold>" + prefix
                + "</bold> page (<page>/<max_page>)</gradient></shadow>"
                : "<shadow:#000000FF><gradient:#f0c14b:#ffe08a><bold>" + prefix + "</bold></gradient></shadow>";
        return component(path, fallback, resolvers);
    }

    public Component component(String path, String def, TagResolver... resolvers) {
        String raw = config.getString(path, def);
        return plain(Text.parse(raw != null ? raw : def, resolvers));
    }

    public Component component(@Nullable Audience audience, String path, String def, TagResolver... resolvers) {
        String raw = config.getString(path, def);
        return plain(Text.parse(raw != null ? raw : def, audience, resolvers));
    }

    public Component component(String path, TagResolver... resolvers) {
        return component(path, "<red>Missing: " + path, resolvers);
    }

    public List<Component> lore(String path, TagResolver... resolvers) {
        List<String> lines = config.getStringList(path);
        List<Component> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            out.add(plain(Text.parse(line, resolvers)));
        }
        return out;
    }

    public String string(String path, String def) {
        return config.getString(path, def);
    }

    public int size(String path, int def) {
        int value = config.getInt(path, def);
        if (value < 9) value = 9;
        if (value > 54) value = 54;
        return (value / 9) * 9;
    }

    public int slot(String path, int def) {
        return config.getInt(path, def);
    }

    public int pageSize(String path, int def) {
        return Math.max(1, config.getInt(path, def));
    }

    public Material material(String path, Material def) {
        String raw = config.getString(path);
        if (raw == null || raw.isBlank()) {
            return def;
        }
        Material mat = Material.matchMaterial(raw);
        return mat != null ? mat : def;
    }

    public @Nullable ItemStack item(String key) {
        ItemStack stack = items.get(key.toLowerCase(Locale.ROOT));
        return stack == null ? null : stack.clone();
    }

    /**
     * Clones a YamlItem and re-applies {@code name}/{@code lore} from guis.yml
     * with MiniMessage {@link TagResolver}s (e.g. {@code <player>}, {@code <role>}).
     */
    public ItemStack item(String key, TagResolver... resolvers) {
        ItemStack stack = item(key);
        if (stack == null) {
            return new ItemStack(Material.STONE);
        }
        if (resolvers.length == 0) {
            return stack;
        }
        ConfigurationSection section = config.getConfigurationSection("items." + key);
        if (section == null) {
            // try lowercase path match
            ConfigurationSection itemsSection = config.getConfigurationSection("items");
            if (itemsSection != null) {
                for (String k : itemsSection.getKeys(false)) {
                    if (k.equalsIgnoreCase(key)) {
                        section = itemsSection.getConfigurationSection(k);
                        break;
                    }
                }
            }
        }
        if (section == null) {
            return stack;
        }
        applyNameLore(stack, section, resolvers);
        return stack;
    }

    public ItemStack itemOr(String key, ItemStack fallback) {
        ItemStack stack = item(key);
        return stack != null ? stack : fallback;
    }

    public Map<String, ItemStack> items() {
        return Collections.unmodifiableMap(items);
    }

    public YamlConfiguration raw() {
        return config;
    }

    private void applyNameLore(ItemStack stack, ConfigurationSection section, TagResolver... resolvers) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        String name = section.getString("name");
        if (name != null) {
            meta.displayName(plain(Text.parse(name, resolvers)));
        }
        if (section.contains("lore")) {
            List<String> lines = section.getStringList("lore");
            List<Component> lore = new ArrayList<>(lines.size());
            for (String line : lines) {
                lore.add(plain(Text.parse(line, resolvers)));
            }
            meta.lore(lore);
        }
        stack.setItemMeta(meta);
    }

    public static void applyHideTooltip(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return;
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setHideTooltip(true);
            stack.setItemMeta(meta);
        }
    }

    private static boolean isAirMaterial(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        Material mat = Material.matchMaterial(raw.trim());
        return mat != null && mat.isAir();
    }

    private static Component plain(Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }
}
