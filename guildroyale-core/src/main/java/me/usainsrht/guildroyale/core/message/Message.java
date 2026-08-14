package me.usainsrht.guildroyale.core.message;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.title.Title;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * A configurable feedback payload that can include chat lines, sounds,
 * an action bar, and a title — any of which may be absent.
 *
 * <p>YAML supports a plain string (single chat line) or a map:
 * <pre>
 * not-found: "&lt;red&gt;player &lt;player&gt; not found"
 *
 * guild-created:
 *   chat:
 *     - "&lt;green&gt;Guild created!"
 *   sound:
 *     - entity.player.levelup
 *   actionbar: "&lt;green&gt;Success"
 *   title:
 *     title: "&lt;gold&gt;Created"
 *     subtitle: "&lt;yellow&gt;&lt;guild&gt;"
 *     fade-in: 10
 *     stay: 40
 *     fade-out: 10
 * </pre>
 */
public final class Message {

    private final @Nullable List<String> chat;
    private final @Nullable List<SoundEntry> sounds;
    private final @Nullable String actionbar;
    private final @Nullable TitleEntry title;

    private Message(
            @Nullable List<String> chat,
            @Nullable List<SoundEntry> sounds,
            @Nullable String actionbar,
            @Nullable TitleEntry title) {
        this.chat = chat;
        this.sounds = sounds;
        this.actionbar = actionbar;
        this.title = title;
    }

    public static Message empty() {
        return new Message(null, null, null, null);
    }

    public static Message chat(String line) {
        return new Message(List.of(line), null, null, null);
    }

    /** Parses a YAML leaf or section into a {@link Message}. */
    public static Message parse(@Nullable Object raw) {
        if (raw == null) {
            return empty();
        }
        if (raw instanceof String s) {
            return chat(s);
        }
        if (raw instanceof ConfigurationSection section) {
            return parseSection(section);
        }
        if (raw instanceof Map<?, ?> map) {
            return parseMap(map);
        }
        return chat(String.valueOf(raw));
    }

    private static Message parseSection(ConfigurationSection section) {
        List<String> chat = readStringList(section, "chat");
        if (chat == null && section.contains("message")) {
            chat = readStringList(section, "message");
        }
        // Bare string under an unexpected structure — treat first string value as chat
        if (chat == null) {
            String single = section.getString("chat");
            if (single != null) {
                chat = List.of(single);
            }
        }

        List<SoundEntry> sounds = readSounds(section.get("sound"));
        if (sounds == null) {
            sounds = readSounds(section.get("sounds"));
        }

        String actionbar = section.getString("actionbar");
        if (actionbar == null) {
            actionbar = section.getString("action-bar");
        }

        TitleEntry title = parseTitle(section.get("title"));
        return new Message(chat, sounds, actionbar, title);
    }

    @SuppressWarnings("unchecked")
    private static Message parseMap(Map<?, ?> map) {
        List<String> chat = null;
        Object chatRaw = map.get("chat");
        if (chatRaw == null) {
            chatRaw = map.get("message");
        }
        if (chatRaw instanceof String s) {
            chat = List.of(s);
        } else if (chatRaw instanceof List<?> list) {
            chat = new ArrayList<>();
            for (Object o : list) {
                chat.add(String.valueOf(o));
            }
        }

        List<SoundEntry> sounds = readSounds(map.get("sound"));
        if (sounds == null) {
            sounds = readSounds(map.get("sounds"));
        }

        Object ab = map.get("actionbar");
        if (ab == null) {
            ab = map.get("action-bar");
        }
        String actionbar = ab != null ? String.valueOf(ab) : null;

        TitleEntry title = parseTitle(map.get("title"));
        return new Message(chat, sounds, actionbar, title);
    }

    private static @Nullable List<String> readStringList(ConfigurationSection section, String key) {
        if (!section.contains(key)) {
            return null;
        }
        if (section.isList(key)) {
            List<String> list = section.getStringList(key);
            return list.isEmpty() ? List.of() : List.copyOf(list);
        }
        String single = section.getString(key);
        return single == null ? null : List.of(single);
    }

    private static @Nullable List<SoundEntry> readSounds(@Nullable Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof String s) {
            return List.of(SoundEntry.of(s));
        }
        if (raw instanceof List<?> list) {
            List<SoundEntry> out = new ArrayList<>();
            for (Object entry : list) {
                if (entry instanceof String s) {
                    out.add(SoundEntry.of(s));
                } else if (entry instanceof Map<?, ?> map) {
                    Object sound = map.get("sound");
                    if (sound == null) {
                        sound = map.get("name");
                    }
                    if (sound == null) {
                        continue;
                    }
                    SoundEntry base = SoundEntry.of(String.valueOf(sound));
                    float volume = map.containsKey("volume") ? number(map.get("volume"), base.volume()) : base.volume();
                    float pitch = map.containsKey("pitch") ? number(map.get("pitch"), base.pitch()) : base.pitch();
                    Sound.Source source = (map.containsKey("source") || map.containsKey("category"))
                            ? parseSource(map.get("source") != null ? map.get("source") : map.get("category"))
                            : base.source();
                    out.add(new SoundEntry(base.sound(), volume, pitch, source));
                } else if (entry instanceof ConfigurationSection sec) {
                    String sound = sec.getString("sound", sec.getString("name"));
                    if (sound == null) {
                        continue;
                    }
                    SoundEntry base = SoundEntry.of(sound);
                    float volume = sec.contains("volume") ? (float) sec.getDouble("volume", base.volume()) : base.volume();
                    float pitch = sec.contains("pitch") ? (float) sec.getDouble("pitch", base.pitch()) : base.pitch();
                    Sound.Source source = (sec.contains("source") || sec.contains("category"))
                            ? parseSource(sec.getString("source", sec.getString("category")))
                            : base.source();
                    out.add(new SoundEntry(base.sound(), volume, pitch, source));
                }
            }
            return out.isEmpty() ? List.of() : List.copyOf(out);
        }
        if (raw instanceof ConfigurationSection section) {
            // sound: { sound: x, volume: 1 }
            String sound = section.getString("sound", section.getString("name"));
            if (sound != null) {
                SoundEntry base = SoundEntry.of(sound);
                float volume = section.contains("volume") ? (float) section.getDouble("volume", base.volume()) : base.volume();
                float pitch = section.contains("pitch") ? (float) section.getDouble("pitch", base.pitch()) : base.pitch();
                Sound.Source source = (section.contains("source") || section.contains("category"))
                        ? parseSource(section.getString("source", section.getString("category")))
                        : base.source();
                return List.of(new SoundEntry(base.sound(), volume, pitch, source));
            }
        }
        return null;
    }

    private static Sound.Source parseSource(@Nullable Object raw) {
        if (raw == null) {
            return Sound.Source.MASTER;
        }
        String str = String.valueOf(raw).trim().toUpperCase(Locale.ROOT);
        if (str.isEmpty()) {
            return Sound.Source.MASTER;
        }
        try {
            return Sound.Source.valueOf(str);
        } catch (IllegalArgumentException ignored) {
            return Sound.Source.MASTER;
        }
    }

    private static @Nullable TitleEntry parseTitle(@Nullable Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof String s) {
            return new TitleEntry(s, null, 10, 40, 10);
        }
        if (raw instanceof ConfigurationSection section) {
            String title = section.getString("title", section.getString("main"));
            String subtitle = section.getString("subtitle", section.getString("sub"));
            if (title == null && subtitle == null) {
                return null;
            }
            return new TitleEntry(
                    title,
                    subtitle,
                    section.getInt("fade-in", section.getInt("fadeIn", 10)),
                    section.getInt("stay", 40),
                    section.getInt("fade-out", section.getInt("fadeOut", 10))
            );
        }
        if (raw instanceof Map<?, ?> map) {
            Object t = map.get("title");
            if (t == null) {
                t = map.get("main");
            }
            Object st = map.get("subtitle");
            if (st == null) {
                st = map.get("sub");
            }
            String title = t != null ? String.valueOf(t) : null;
            String subtitle = st != null ? String.valueOf(st) : null;
            if (title == null && subtitle == null) {
                return null;
            }
            return new TitleEntry(
                    title,
                    subtitle,
                    (int) number(map.get("fade-in"), number(map.get("fadeIn"), 10)),
                    (int) number(map.get("stay"), 40),
                    (int) number(map.get("fade-out"), number(map.get("fadeOut"), 10))
            );
        }
        return new TitleEntry(String.valueOf(raw), null, 10, 40, 10);
    }

    private static float number(@Nullable Object value, float def) {
        if (value instanceof Number n) {
            return n.floatValue();
        }
        if (value != null) {
            try {
                return Float.parseFloat(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return def;
            }
        }
        return def;
    }

    public boolean isEmpty() {
        return (chat == null || chat.isEmpty())
                && (sounds == null || sounds.isEmpty())
                && actionbar == null
                && title == null;
    }

    public @Nullable List<String> chat() {
        return chat == null ? null : Collections.unmodifiableList(chat);
    }

    public @Nullable List<SoundEntry> sounds() {
        return sounds == null ? null : Collections.unmodifiableList(sounds);
    }

    public @Nullable String actionbar() {
        return actionbar;
    }

    public @Nullable TitleEntry title() {
        return title;
    }

    /**
     * Sends this message to {@code audience}.
     *
     * @param prefix MiniMessage prefix prepended to each chat line when non-null/non-blank
     */
    public void send(Audience audience, @Nullable String prefix, TagResolver... resolvers) {
        Objects.requireNonNull(audience, "audience");

        if (chat != null) {
            for (String line : chat) {
                String raw = (prefix != null && !prefix.isEmpty()) ? prefix + line : line;
                audience.sendMessage(Text.parse(raw, audience, resolvers));
            }
        }

        if (actionbar != null) {
            audience.sendActionBar(Text.parse(actionbar, audience, resolvers));
        }

        if (title != null) {
            Component main = title.title() != null
                    ? Text.parse(title.title(), audience, resolvers)
                    : Component.empty();
            Component sub = title.subtitle() != null
                    ? Text.parse(title.subtitle(), audience, resolvers)
                    : Component.empty();
            Title.Times times = Title.Times.times(
                    Duration.ofMillis(title.fadeIn() * 50L),
                    Duration.ofMillis(title.stay() * 50L),
                    Duration.ofMillis(title.fadeOut() * 50L)
            );
            audience.showTitle(Title.title(main, sub, times));
        }

        if (sounds != null) {
            for (SoundEntry entry : sounds) {
                try {
                    String rawSound = entry.sound().trim().toLowerCase(Locale.ROOT);
                    Key key = Key.key(rawSound);
                    Sound.Source source = entry.source() != null ? entry.source() : Sound.Source.MASTER;
                    audience.playSound(Sound.sound(key, source, entry.volume(), entry.pitch()));
                } catch (Exception ignored) {
                    // Invalid sound keys are skipped so bad config never breaks feedback
                }
            }
        }
    }

    /**
     * Deserializes the first chat line (or empty) — useful for rare Component-only APIs.
     * Prefer {@link #firstChatComponent(Audience, TagResolver...)} when an audience is available.
     */
    public Component firstChatComponent(TagResolver... resolvers) {
        return firstChatComponent(null, resolvers);
    }

    public Component firstChatComponent(@Nullable Audience audience, TagResolver... resolvers) {
        if (chat == null || chat.isEmpty()) {
            return Component.empty();
        }
        return Text.parse(chat.getFirst(), audience, resolvers);
    }

    public record SoundEntry(String sound, float volume, float pitch, Sound.Source source) {
        public SoundEntry(String sound, float volume, float pitch) {
            this(sound, volume, pitch, Sound.Source.MASTER);
        }

        public static SoundEntry of(String s) {
            if (s == null || s.isBlank()) {
                return new SoundEntry("", 1f, 1f, Sound.Source.MASTER);
            }
            String[] parts = s.split(",");
            String sound = parts[0].trim();
            float volume = parts.length > 1 ? number(parts[1].trim(), 1f) : 1f;
            float pitch = parts.length > 2 ? number(parts[2].trim(), 1f) : 1f;
            Sound.Source source = parts.length > 3 ? parseSource(parts[3].trim()) : Sound.Source.MASTER;
            return new SoundEntry(sound, volume, pitch, source);
        }
    }

    public record TitleEntry(
            @Nullable String title,
            @Nullable String subtitle,
            int fadeIn,
            int stay,
            int fadeOut
    ) {}
}
