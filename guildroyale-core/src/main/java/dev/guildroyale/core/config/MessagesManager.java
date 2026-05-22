package dev.guildroyale.core.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads {@code messages.yml} and deserialises message strings via MiniMessage.
 *
 * <p>The prefix is prepended automatically when {@link #prefixed(String, TagResolver...)}
 * is used. Use {@link #get(String, TagResolver...)} for unprefixed messages.
 */
public final class MessagesManager {

    private final MiniMessage mm = MiniMessage.miniMessage();
    private final JavaPlugin plugin;
    private final Map<String, String> cache = new HashMap<>();
    private String rawPrefix = "";

    public MessagesManager(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        cache.clear();
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        // Merge defaults from the bundled resource
        InputStream defaultStream = plugin.getResource("messages.yml");
        if (defaultStream != null) {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
            config.setDefaults(defaults);
        }

        for (String key : config.getKeys(true)) {
            if (!config.isConfigurationSection(key)) {
                cache.put(key, config.getString(key, ""));
            }
        }

        rawPrefix = cache.getOrDefault("prefix", "");
    }

    /**
     * Returns the deserialized {@link Component} for {@code key}, with optional
     * MiniMessage tag resolvers applied.
     */
    public Component get(String key, TagResolver... resolvers) {
        String raw = cache.getOrDefault(key, "<red>Missing message: " + key);
        return mm.deserialize(raw, resolvers);
    }

    /**
     * Same as {@link #get(String, TagResolver...)} but prepends the configured prefix.
     */
    public Component prefixed(String key, TagResolver... resolvers) {
        String raw = rawPrefix + cache.getOrDefault(key, "<red>Missing message: " + key);
        return mm.deserialize(raw, resolvers);
    }

    /** Returns the raw (un-deserialized) string for a key. Useful for logging. */
    public String getRaw(String key) {
        return cache.getOrDefault(key, "Missing message: " + key);
    }
}
