package me.usainsrht.guildroyale.core.integration;

import io.github.miniplaceholders.api.Expansion;
import io.github.miniplaceholders.api.MiniPlaceholders;
import io.github.miniplaceholders.api.utils.Tags;
import me.usainsrht.guildroyale.core.GuildRoyalePlugin;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Direct MiniPlaceholders v3 API usage — only loaded when MiniPlaceholders is installed.
 *
 * <h3>Placeholders</h3>
 * <ul>
 *   <li>{@code <guildroyale_guild_name>} — player's guild name</li>
 *   <li>{@code <guildroyale_guild_shortname>}</li>
 *   <li>{@code <guildroyale_guild_level>}</li>
 *   <li>{@code <guildroyale_guild_xp>}</li>
 *   <li>{@code <guildroyale_guild_members>}</li>
 *   <li>{@code <guildroyale_role>}</li>
 *   <li>{@code <guildroyale_badge>} — active badge MiniMessage (pre-processed)</li>
 *   <li>{@code <guildroyale_top_name:1>} / {@code <guildroyale_top_level:1>} /
 *       {@code <guildroyale_top_xp:1>} — leaderboard (1-based rank argument)</li>
 * </ul>
 */
final class MiniPlaceholdersAccess {

    private MiniPlaceholdersAccess() {}

    static TagResolver resolver(boolean withAudience) {
        return withAudience
                ? MiniPlaceholders.audienceGlobalPlaceholders()
                : MiniPlaceholders.globalPlaceholders();
    }

    static Expansion register(GuildRoyalePlugin plugin, GuildPlaceholderData data) {
        Expansion expansion = Expansion.builder("guildroyale")
                .author("GuildRoyale")
                .version(plugin.getPluginMeta().getVersion())
                .audiencePlaceholder(Player.class, "guild_name",
                        (player, queue, ctx) -> tag(data.resolve(player.getUniqueId(), "guild_name")))
                .audiencePlaceholder(Player.class, "guild_shortname",
                        (player, queue, ctx) -> tag(data.resolve(player.getUniqueId(), "guild_shortname")))
                .audiencePlaceholder(Player.class, "guild_level",
                        (player, queue, ctx) -> tag(data.resolve(player.getUniqueId(), "guild_level")))
                .audiencePlaceholder(Player.class, "guild_xp",
                        (player, queue, ctx) -> tag(data.resolve(player.getUniqueId(), "guild_xp")))
                .audiencePlaceholder(Player.class, "guild_members",
                        (player, queue, ctx) -> tag(data.resolve(player.getUniqueId(), "guild_members")))
                .audiencePlaceholder(Player.class, "role",
                        (player, queue, ctx) -> tag(data.resolve(player.getUniqueId(), "role")))
                .audiencePlaceholder(Player.class, "badge",
                        (player, queue, ctx) -> badgeTag(data.resolve(player.getUniqueId(), "badge")))
                .audiencePlaceholder(Player.class, "guild_tag", (player, queue, ctx) -> {
                    var guildOpt = plugin.getGuildService().getGuildByMember(player.getUniqueId()).join();
                    if (guildOpt.isEmpty()) return Tags.EMPTY_TAG;
                    return Tag.inserting(plugin.getTagService().renderGuildTag(guildOpt.get(), player));
                })
                .audiencePlaceholder(Player.class, "member_tag", (player, queue, ctx) -> {
                    var guildOpt = plugin.getGuildService().getGuildByMember(player.getUniqueId()).join();
                    if (guildOpt.isEmpty()) return Tags.EMPTY_TAG;
                    var memberOpt = guildOpt.get().getMember(player.getUniqueId());
                    if (memberOpt.isEmpty()) return Tags.EMPTY_TAG;
                    return Tag.inserting(plugin.getTagService().renderMemberTag(memberOpt.get(), guildOpt.get(), player));
                })
                .audiencePlaceholder(Player.class, "player_tag", (player, queue, ctx) ->
                        Tag.inserting(plugin.getTagService().renderPlayerTag(player, player)))
                .audiencePlaceholder(Player.class, "role_tag", (player, queue, ctx) -> {
                    var guildOpt = plugin.getGuildService().getGuildByMember(player.getUniqueId()).join();
                    if (guildOpt.isEmpty()) return Tags.EMPTY_TAG;
                    var memberOpt = guildOpt.get().getMember(player.getUniqueId());
                    if (memberOpt.isEmpty()) return Tags.EMPTY_TAG;
                    return Tag.inserting(plugin.getTagService().renderRoleTag(memberOpt.get().getRole(), guildOpt.get(), player));
                })
                .globalPlaceholder("top_name", (queue, ctx) -> {
                    int rank = queue.hasNext() ? queue.pop().asInt().orElse(1) : 1;
                    return tag(data.resolveTopField("name", rank));
                })
                .globalPlaceholder("top_level", (queue, ctx) -> {
                    int rank = queue.hasNext() ? queue.pop().asInt().orElse(1) : 1;
                    return tag(data.resolveTopField("level", rank));
                })
                .globalPlaceholder("top_xp", (queue, ctx) -> {
                    int rank = queue.hasNext() ? queue.pop().asInt().orElse(1) : 1;
                    return tag(data.resolveTopField("xp", rank));
                })
                .build();
        expansion.register();
        return expansion;
    }

    static void unregister(Object expansion) {
        if (expansion instanceof Expansion exp && exp.registered()) {
            exp.unregister();
        }
    }

    private static Tag tag(@Nullable String value) {
        if (value == null) {
            return Tags.NULL_TAG;
        }
        if (value.isEmpty()) {
            return Tags.EMPTY_TAG;
        }
        return Tag.preProcessParsed(value);
    }

    /** Badge display is MiniMessage — let it nest into the parse. */
    private static Tag badgeTag(@Nullable String value) {
        if (value == null || value.isEmpty()) {
            return Tags.EMPTY_TAG;
        }
        return Tag.preProcessParsed(value);
    }
}
