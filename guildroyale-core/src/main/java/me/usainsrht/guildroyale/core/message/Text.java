package me.usainsrht.guildroyale.core.message;

import me.usainsrht.guildroyale.core.integration.MiniPlaceholdersHook;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.jetbrains.annotations.Nullable;

/**
 * Central MiniMessage deserialization for GuildRoyale.
 *
 * <p>When MiniPlaceholders is installed, every parse includes global placeholders;
 * when an {@link Audience} is provided (or pushed via {@link #push(Audience)}),
 * audience placeholders are included as well and the audience is passed to
 * {@link MiniMessage#deserialize(String, Object, TagResolver...)}.
 */
public final class Text {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final ThreadLocal<Audience> CURRENT = new ThreadLocal<>();

    private Text() {}

    public static MiniMessage miniMessage() {
        return MM;
    }

    /**
     * Sets the audience used by {@link #parse(String, TagResolver...)} for the
     * duration of the returned scope (typically a GUI {@code open}/{@code build}).
     */
    public static Scope push(@Nullable Audience audience) {
        Audience previous = CURRENT.get();
        if (audience == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(audience);
        }
        return () -> {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        };
    }

    /** Non-throwing closeable for try-with-resources. */
    @FunctionalInterface
    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }

    /** Parses with the current {@link #push(Audience) pushed} audience, if any. */
    public static Component parse(String input, TagResolver... resolvers) {
        return parse(input, CURRENT.get(), resolvers);
    }

    /**
     * Parses MiniMessage text with optional MiniPlaceholders and extra resolvers.
     *
     * @param audience when non-null, audience + global MiniPlaceholders are applied;
     *                 when null, only global MiniPlaceholders are applied
     */
    public static Component parse(String input, @Nullable Audience audience, TagResolver... resolvers) {
        TagResolver mini = MiniPlaceholdersHook.resolver(audience != null);
        TagResolver combined = combine(mini, resolvers);
        if (audience != null) {
            return MM.deserialize(input, audience, combined);
        }
        return MM.deserialize(input, combined);
    }

    private static TagResolver combine(TagResolver mini, TagResolver... resolvers) {
        if (resolvers.length == 0) {
            return mini;
        }
        if (resolvers.length == 1 && isEmpty(mini)) {
            return resolvers[0];
        }
        TagResolver extras = TagResolver.resolver(resolvers);
        if (isEmpty(mini)) {
            return extras;
        }
        return TagResolver.resolver(mini, extras);
    }

    private static boolean isEmpty(TagResolver resolver) {
        return resolver == TagResolver.empty();
    }
}
