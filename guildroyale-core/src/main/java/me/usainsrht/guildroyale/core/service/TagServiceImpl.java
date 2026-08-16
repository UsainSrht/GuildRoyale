package me.usainsrht.guildroyale.core.service;

import me.usainsrht.guildroyale.api.domain.Guild;
import me.usainsrht.guildroyale.api.domain.GuildMember;
import me.usainsrht.guildroyale.api.domain.GuildRole;
import me.usainsrht.guildroyale.core.GuildRoyalePlugin;
import me.usainsrht.guildroyale.core.config.BadgeDefinition;
import me.usainsrht.guildroyale.core.config.MessagesManager;
import me.usainsrht.guildroyale.core.message.Text;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Service implementation for rendering customisable Kyori components and {@link TagResolver}s
 * for guilds, roles, members, and players based on configured MiniMessage tag formats.
 */
public final class TagServiceImpl implements TagService {

    private static final String DEFAULT_GUILD_TAG = "<hover:show_text:'<gradient:#f0c14b:#ffe08a><bold>Guild Information</bold></gradient><newline><gray>Level: <gold><guild_level></gold><newline><gray>Members: <gold><guild_members></gold> <dark_gray>(<green><guild_members_online> online</green>)</dark_gray><newline><gray>XP: <yellow><guild_xp></yellow><newline><gray>Leader: <gold><guild_leader></gold>'><yellow><guild_name></yellow></hover>";
    private static final String DEFAULT_ROLE_TAG = "<hover:show_text:'<gray>Role Rank: <gold>#<role_index></gold>'><role_color><role_name></hover>";
    private static final String DEFAULT_MEMBER_TAG = "<hover:show_text:'<role_color><bold><member_role_name></bold><newline><gray>Contribution: <gold><member_contribution> XP</gold>'><role_color><member_player_tag><guild_badge_symbol></hover>";
    private static final String DEFAULT_PLAYER_TAG = "<player_displayname>";

    private final MessagesManager messagesManager;

    public TagServiceImpl(MessagesManager messagesManager) {
        this.messagesManager = Objects.requireNonNull(messagesManager, "messagesManager");
    }

    @Override
    public Component renderGuildTag(Guild guild, @Nullable Audience viewer) {
        if (guild == null) {
            return Component.empty();
        }
        String template = messagesManager.tagTemplate("guild", DEFAULT_GUILD_TAG);
        TagResolver resolvers = buildGuildInnerResolvers(guild, viewer);
        return Text.parse(template, viewer, resolvers);
    }

    @Override
    public Component renderRoleTag(GuildRole role, @Nullable Guild guild, @Nullable Audience viewer) {
        if (role == null) {
            return Component.empty();
        }
        String template = messagesManager.tagTemplate("role", DEFAULT_ROLE_TAG);
        TagResolver resolvers = buildRoleInnerResolvers(role, guild, viewer);
        return Text.parse(template, viewer, resolvers);
    }

    @Override
    public Component renderPlayerTag(UUID playerId, String name, @Nullable Audience viewer) {
        if (playerId == null) {
            return Component.text(name != null ? name : "");
        }
        Player onlinePlayer = getOnlinePlayer(playerId);
        if (onlinePlayer != null) {
            return renderPlayerTag(onlinePlayer, viewer);
        }
        String displayName = name != null ? name : playerId.toString().substring(0, 8);
        String template = messagesManager.tagTemplate("player", DEFAULT_PLAYER_TAG);
        TagResolver resolvers = TagResolver.resolver(
                Placeholder.unparsed("player_name", displayName),
                Placeholder.unparsed("player_displayname", displayName)
        );
        return Text.parse(template, viewer, resolvers);
    }

    @Override
    public Component renderPlayerTag(Player player, @Nullable Audience viewer) {
        if (player == null) {
            return Component.empty();
        }
        String template = messagesManager.tagTemplate("player", DEFAULT_PLAYER_TAG);
        String rawName = player.getName();
        Component displayNameComp = player.displayName();

        TagResolver resolvers = TagResolver.resolver(
                Placeholder.unparsed("player_name", rawName),
                Placeholder.component("player_displayname", displayNameComp)
        );
        return Text.parse(template, player, resolvers);
    }

    @Override
    public Component renderMemberTag(GuildMember member, Guild guild, @Nullable Audience viewer) {
        if (member == null) {
            return Component.empty();
        }
        String template = messagesManager.tagTemplate("member", DEFAULT_MEMBER_TAG);

        Component playerTagComp = renderPlayerTag(member.getPlayerId(), null, viewer);
        GuildRole role = member.getRole();
        Component roleTagComp = renderRoleTag(role, guild, viewer);

        String name = getOfflinePlayerName(member.getPlayerId());

        String badgeSymbol = "";
        String badgeDisplay = "";
        if (guild != null && guild.getActiveBadgeId() != null) {
            GuildRoyalePlugin mainPlugin = GuildRoyalePlugin.getInstance();
            if (mainPlugin != null) {
                Optional<BadgeDefinition> badgeDef = mainPlugin.getConfigManager().getBadge(guild.getActiveBadgeId());
                if (badgeDef.isPresent()) {
                    badgeSymbol = badgeDef.get().symbol();
                    badgeDisplay = badgeDef.get().displayName();
                } else {
                    badgeSymbol = guild.getActiveBadgeId();
                    badgeDisplay = guild.getActiveBadgeId();
                }
            }
        }
        String badgeSymbolTag = !badgeSymbol.isBlank() ? " " + badgeSymbol : "";

        TagResolver resolvers = TagResolver.resolver(
                Placeholder.component("member_player_tag", playerTagComp),
                Placeholder.unparsed("member_name", name),
                Placeholder.component("member_displayname", playerTagComp),
                Placeholder.component("member_role", roleTagComp),
                Placeholder.unparsed("member_role_name", role.getName()),
                Placeholder.unparsed("member_contribution", String.valueOf(member.getContribution())),
                Placeholder.parsed("role_color", role.getColor().miniMessage()),
                Placeholder.parsed("guild_badge_symbol", badgeSymbolTag),
                Placeholder.parsed("guild_badge", badgeSymbolTag),
                Placeholder.parsed("guild_badge_display", badgeDisplay),
                Placeholder.parsed("badge_symbol", badgeSymbolTag)
        );

        return Text.parse(template, viewer, resolvers);
    }

    @Override
    public TagResolver guildResolver(Guild guild, @Nullable Audience viewer) {
        if (guild == null) {
            return TagResolver.empty();
        }
        Component guildTagComp = renderGuildTag(guild, viewer);
        TagResolver inner = buildGuildInnerResolvers(guild, viewer);
        return TagResolver.resolver(
                Placeholder.component("guild", guildTagComp),
                inner
        );
    }

    @Override
    public TagResolver roleResolver(GuildRole role, @Nullable Guild guild, @Nullable Audience viewer) {
        if (role == null) {
            return TagResolver.empty();
        }
        Component roleTagComp = renderRoleTag(role, guild, viewer);
        return TagResolver.resolver(
                Placeholder.component("role", roleTagComp),
                Placeholder.unparsed("role_name", role.getName()),
                Placeholder.parsed("role_color", role.getColor().miniMessage())
        );
    }

    @Override
    public TagResolver memberResolver(GuildMember member, Guild guild, @Nullable Audience viewer) {
        if (member == null) {
            return TagResolver.empty();
        }
        Component memberTagComp = renderMemberTag(member, guild, viewer);
        GuildRole role = member.getRole();
        Component roleTagComp = renderRoleTag(role, guild, viewer);
        String name = getOfflinePlayerName(member.getPlayerId());

        return TagResolver.resolver(
                Placeholder.component("member", memberTagComp),
                Placeholder.component("player", memberTagComp),
                Placeholder.component("leader", memberTagComp),
                Placeholder.component("role", roleTagComp),
                Placeholder.component("member_role", roleTagComp),
                Placeholder.unparsed("role_name", role.getName()),
                Placeholder.unparsed("member_role_name", role.getName()),
                Placeholder.parsed("role_color", role.getColor().miniMessage()),
                Placeholder.unparsed("member_name", name),
                Placeholder.unparsed("player_name", name),
                Placeholder.unparsed("leader_name", name),
                Placeholder.unparsed("member_contribution", String.valueOf(member.getContribution())),
                Placeholder.unparsed("contribution", String.valueOf(member.getContribution()))
        );
    }

    @Override
    public TagResolver playerResolver(UUID playerId, String name, @Nullable Audience viewer) {
        Component playerTagComp = renderPlayerTag(playerId, name, viewer);
        String resolvedName = name != null ? name : (playerId != null ? playerId.toString().substring(0, 8) : "");
        return TagResolver.resolver(
                Placeholder.component("player", playerTagComp),
                Placeholder.component("target", playerTagComp),
                Placeholder.unparsed("player_name", resolvedName),
                Placeholder.unparsed("target_name", resolvedName)
        );
    }

    @Override
    public TagResolver playerResolver(Player player, @Nullable Audience viewer) {
        if (player == null) {
            return TagResolver.empty();
        }
        Component playerTagComp = renderPlayerTag(player, viewer);
        return TagResolver.resolver(
                Placeholder.component("player", playerTagComp),
                Placeholder.component("target", playerTagComp),
                Placeholder.unparsed("player_name", player.getName()),
                Placeholder.unparsed("target_name", player.getName())
        );
    }

    @Override
    public void reload() {
    }

    private TagResolver buildGuildInnerResolvers(Guild guild, @Nullable Audience viewer) {
        long onlineCount = guild.getMembers().stream()
                .filter(m -> getOnlinePlayer(m.getPlayerId()) != null)
                .count();

        String leaderName = "Unknown";
        try {
            GuildMember leader = guild.getMembers().stream()
                    .filter(m -> m.getRole().getIndex() == 0)
                    .findFirst().orElse(null);
            if (leader != null) {
                leaderName = getOfflinePlayerName(leader.getPlayerId());
            }
        } catch (Exception ignored) {}

        String badgeId = guild.getActiveBadgeId();
        String badgeSymbol = "";
        String badgeDisplay = "";
        if (badgeId != null) {
            GuildRoyalePlugin mainPlugin = GuildRoyalePlugin.getInstance();
            if (mainPlugin != null) {
                Optional<BadgeDefinition> badgeDef = mainPlugin.getConfigManager().getBadge(badgeId);
                if (badgeDef.isPresent()) {
                    badgeSymbol = badgeDef.get().symbol();
                    badgeDisplay = badgeDef.get().displayName();
                } else {
                    badgeSymbol = badgeId;
                    badgeDisplay = badgeId;
                }
            }
        }
        String badgeTag = !badgeSymbol.isBlank() ? badgeSymbol : badgeDisplay;

        return TagResolver.resolver(
                Placeholder.unparsed("guild_name", guild.getName()),
                Placeholder.unparsed("guild_shortname", guild.getShortname()),
                Placeholder.unparsed("guild_level", String.valueOf(guild.getLevel())),
                Placeholder.unparsed("guild_xp", String.valueOf(guild.getXp())),
                Placeholder.unparsed("guild_members", String.valueOf(guild.getMemberCount())),
                Placeholder.unparsed("guild_members_online", String.valueOf(onlineCount)),
                Placeholder.unparsed("guild_leader", leaderName),
                Placeholder.unparsed("leader", leaderName),
                Placeholder.parsed("guild_badge", badgeTag),
                Placeholder.parsed("guild_badge_symbol", badgeSymbol),
                Placeholder.parsed("guild_badge_display", badgeDisplay)
        );
    }

    private TagResolver buildRoleInnerResolvers(GuildRole role, @Nullable Guild guild, @Nullable Audience viewer) {
        return TagResolver.resolver(
                Placeholder.unparsed("role_name", role.getName()),
                Placeholder.parsed("role_color", role.getColor().miniMessage()),
                Placeholder.unparsed("role_index", String.valueOf(role.getIndex()))
        );
    }

    private Player getOnlinePlayer(UUID playerId) {
        try {
            if (Bukkit.getServer() != null) {
                return Bukkit.getPlayer(playerId);
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private String getOfflinePlayerName(UUID playerId) {
        try {
            if (Bukkit.getServer() != null) {
                OfflinePlayer op = Bukkit.getOfflinePlayer(playerId);
                if (op != null && op.getName() != null) {
                    return op.getName();
                }
            }
        } catch (Throwable ignored) {}
        return playerId.toString().substring(0, 8);
    }
}
