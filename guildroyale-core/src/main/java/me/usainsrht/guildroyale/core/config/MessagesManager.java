package me.usainsrht.guildroyale.core.config;

import me.usainsrht.guildroyale.core.message.Message;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads {@code messages.yml} into {@link Message} objects.
 *
 * <p>Each key may be a plain MiniMessage string (single chat line) or a full
 * map with {@code chat}, {@code sound}, {@code actionbar}, and {@code title}.
 */
public final class MessagesManager {

    private final MiniMessage mm = MiniMessage.miniMessage();
    private final JavaPlugin plugin;
    private final Map<String, Message> cache = new HashMap<>();
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

        InputStream defaultStream = plugin.getResource("messages.yml");
        if (defaultStream != null) {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
            config.setDefaults(defaults);
            config.options().copyDefaults(true);
        }

        rawPrefix = config.getString("prefix", "");

        for (String key : config.getKeys(true)) {
            if ("prefix".equals(key)) {
                continue;
            }
            if (config.isConfigurationSection(key)) {
                // Only parse top-level message sections (or nested leaves handled below)
                ConfigurationSection section = config.getConfigurationSection(key);
                if (section == null) {
                    continue;
                }
                // Skip intermediate parents that only contain child sections without message fields
                if (isMessageSection(section)) {
                    cache.put(key, Message.parse(section));
                }
                continue;
            }
            // Leaf string — but skip if parent was already parsed as a message section field
            String parent = parentKey(key);
            if (parent != null && cache.containsKey(parent)) {
                continue;
            }
            Object value = config.get(key);
            if (value != null) {
                cache.put(key, Message.parse(value));
            }
        }
    }

    private static boolean isMessageSection(ConfigurationSection section) {
        return section.contains("chat")
                || section.contains("message")
                || section.contains("sound")
                || section.contains("sounds")
                || section.contains("actionbar")
                || section.contains("action-bar")
                || section.contains("title");
    }

    private static @Nullable String parentKey(String key) {
        int idx = key.lastIndexOf('.');
        return idx < 0 ? null : key.substring(0, idx);
    }

    public Message message(String key) {
        return cache.getOrDefault(key, Message.chat("<red>Missing message: " + key));
    }

    /** Sends the message with the configured prefix applied to chat lines. */
    public void send(Audience audience, String key, TagResolver... resolvers) {
        message(key).send(audience, rawPrefix, resolvers);
    }

    /** Sends the message without the prefix. */
    public void sendRaw(Audience audience, String key, TagResolver... resolvers) {
        message(key).send(audience, null, resolvers);
    }

    /**
     * Returns the first chat line as a Component with prefix applied.
     * Prefer {@link #send(Audience, String, TagResolver...)} for full feedback.
     */
    public Component prefixed(String key, TagResolver... resolvers) {
        Message msg = message(key);
        var chat = msg.chat();
        String line = (chat == null || chat.isEmpty())
                ? "<red>Missing message: " + key
                : chat.getFirst();
        return mm.deserialize(rawPrefix + line, resolvers);
    }

    /** First chat line without prefix. */
    public Component get(String key, TagResolver... resolvers) {
        return message(key).firstChatComponent(resolvers);
    }

    public String getRaw(String key) {
        var chat = message(key).chat();
        if (chat == null || chat.isEmpty()) {
            return "Missing message: " + key;
        }
        return chat.getFirst();
    }

    public String prefix() {
        return rawPrefix;
    }
}
