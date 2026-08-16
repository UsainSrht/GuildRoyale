package me.usainsrht.guildroyale.core.service;

import me.usainsrht.guildroyale.api.domain.Guild;
import me.usainsrht.guildroyale.api.domain.GuildMember;
import me.usainsrht.guildroyale.api.domain.GuildRole;
import me.usainsrht.guildroyale.api.domain.SerializableItemStack;
import me.usainsrht.guildroyale.core.config.MessagesManager;
import me.usainsrht.guildroyale.core.message.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TagServicePlaceholderTest {

    private TagServiceImpl tagService;
    private Guild sampleGuild;
    private GuildRole leaderRole;
    private GuildMember leaderMember;

    @BeforeEach
    void setUp() {
        MessagesManager messagesManager = new MessagesManager(null);
        tagService = new TagServiceImpl(messagesManager);

        leaderRole = GuildRole.createLeader();
        UUID playerId = UUID.randomUUID();
        leaderMember = new GuildMember(playerId, leaderRole, Instant.now(), 1500L);

        sampleGuild = new Guild(
                UUID.randomUUID(),
                "RoyaleGuild",
                "ROYAL",
                SerializableItemStack.EMPTY,
                10,
                50000L,
                List.of(leaderMember),
                List.of(leaderRole),
                Instant.now(),
                null,
                null,
                null,
                false
        );
    }

    @Test
    void testRenderRoleTagDoesNotContainClosingRoleColor() {
        Component roleComp = tagService.renderRoleTag(leaderRole, sampleGuild, null);
        String serialized = MiniMessage.miniMessage().serialize(roleComp);
        assertFalse(serialized.contains("</role_color>"), "Serialized role tag should not contain raw </role_color>: " + serialized);
        assertFalse(serialized.contains("<role_color>"), "Serialized role tag should resolve <role_color>: " + serialized);
    }

    @Test
    void testRenderMemberTagDoesNotContainRawTags() {
        Component memberComp = tagService.renderMemberTag(leaderMember, sampleGuild, null);
        String serialized = MiniMessage.miniMessage().serialize(memberComp);
        assertFalse(serialized.contains("</role_color>"), "Serialized member tag should not contain raw </role_color>: " + serialized);
        assertFalse(serialized.contains("<role_color>"), "Serialized member tag should resolve <role_color>: " + serialized);
    }

    @Test
    void testMemberResolverResolvesLeaderAndRole() {
        TagResolver memberResolver = tagService.memberResolver(leaderMember, sampleGuild, null);

        Component parsedLeader = Text.parse("Leader is <leader> with role <role>", memberResolver);
        String serialized = MiniMessage.miniMessage().serialize(parsedLeader);

        assertFalse(serialized.contains("<leader>"), "Member resolver should resolve <leader>: " + serialized);
        assertFalse(serialized.contains("<role>"), "Member resolver should resolve <role>: " + serialized);
    }

    @Test
    void testGuildResolverResolvesLeaderAndGuild() {
        TagResolver guildResolver = tagService.guildResolver(sampleGuild, null);

        Component parsedGuild = Text.parse("Guild: <guild> Leader: <leader>", guildResolver);
        String serialized = MiniMessage.miniMessage().serialize(parsedGuild);

        assertFalse(serialized.contains("<guild>"), "Guild resolver should resolve <guild>: " + serialized);
        assertFalse(serialized.contains("<leader>"), "Guild resolver should resolve <leader>: " + serialized);
    }
}
