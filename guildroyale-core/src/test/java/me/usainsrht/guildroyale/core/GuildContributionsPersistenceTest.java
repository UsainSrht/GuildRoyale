package me.usainsrht.guildroyale.core;

import me.usainsrht.guildroyale.api.domain.Guild;
import me.usainsrht.guildroyale.api.domain.GuildMember;
import me.usainsrht.guildroyale.api.domain.GuildRole;
import me.usainsrht.guildroyale.api.domain.SerializableItemStack;
import me.usainsrht.guildroyale.core.gui.impl.GuildLevelGui;
import me.usainsrht.guildroyale.core.storage.json.JsonGuildRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class GuildContributionsPersistenceTest {

    @Test
    public void testContributionPreservedAfterMemberLeaves(@TempDir File tempDir) {
        JsonGuildRepository repo = new JsonGuildRepository(tempDir, null);

        UUID guildId = UUID.randomUUID();
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();

        GuildRole leaderRole = GuildRole.createLeader();
        GuildRole memberRole = GuildRole.createMember();

        GuildMember m1 = new GuildMember(player1, leaderRole, Instant.now(), 0L);
        GuildMember m2 = new GuildMember(player2, memberRole, Instant.now(), 0L);

        Guild guild = new Guild(guildId, "RoyaleLegends", "LEGEND", SerializableItemStack.EMPTY,
                1, 0L, List.of(m1, m2), List.of(leaderRole, memberRole), Instant.now());

        // Add contributions
        guild.addContribution(player1, 500L);
        guild.addContribution(player2, 1250L);

        assertEquals(500L, guild.getContribution(player1));
        assertEquals(1250L, guild.getContribution(player2));
        assertEquals(500L, m1.getContribution());
        assertEquals(1250L, m2.getContribution());

        // Player 2 leaves the guild
        guild.removeMember(player2);
        assertFalse(guild.hasMember(player2));
        assertEquals(1, guild.getMemberCount());
        assertEquals(1250L, guild.getContribution(player2));

        // Save and reload
        repo.save(guild).join();

        JsonGuildRepository reloadedRepo = new JsonGuildRepository(tempDir, null);
        reloadedRepo.init().join();

        Guild reloaded = reloadedRepo.findById(guildId).join().orElseThrow();

        assertEquals(1, reloaded.getMemberCount());
        assertTrue(reloaded.hasMember(player1));
        assertFalse(reloaded.hasMember(player2));

        // Contributions must be preserved for both active and former members
        assertEquals(500L, reloaded.getContribution(player1));
        assertEquals(1250L, reloaded.getContribution(player2));
        assertEquals(2, reloaded.getContributions().size());
    }

    @Test
    public void testProgressBarFormatting() {
        String bar0 = GuildLevelGui.formatProgressBar(0, 1000, 10);
        assertEquals("<green></green><dark_gray>■■■■■■■■■■</dark_gray>", bar0);

        String bar50 = GuildLevelGui.formatProgressBar(500, 1000, 10);
        assertEquals("<green>■■■■■</green><dark_gray>■■■■■</dark_gray>", bar50);

        String bar100 = GuildLevelGui.formatProgressBar(1000, 1000, 10);
        assertEquals("<green>■■■■■■■■■■</green><dark_gray></dark_gray>", bar100);
    }
}
