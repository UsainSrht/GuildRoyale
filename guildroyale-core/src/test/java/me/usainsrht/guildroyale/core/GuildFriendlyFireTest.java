package me.usainsrht.guildroyale.core;

import me.usainsrht.guildroyale.api.domain.Guild;
import me.usainsrht.guildroyale.api.domain.GuildMember;
import me.usainsrht.guildroyale.api.domain.GuildRole;
import me.usainsrht.guildroyale.api.domain.SerializableItemStack;
import me.usainsrht.guildroyale.core.service.GuildServiceImpl;
import me.usainsrht.guildroyale.core.storage.json.JsonGuildRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class GuildFriendlyFireTest {

    @Test
    public void testGuildFriendlyFire(@TempDir File tempDir) {
        JsonGuildRepository repo = new JsonGuildRepository(tempDir, null);

        UUID guild1Id = UUID.randomUUID();
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();
        UUID player3 = UUID.randomUUID();

        GuildRole role = GuildRole.createLeader();
        GuildMember m1 = new GuildMember(player1, role, Instant.now(), 0);
        GuildMember m2 = new GuildMember(player2, role, Instant.now(), 0);

        Guild guild1 = new Guild(guild1Id, "GuildOne", "ONE", SerializableItemStack.EMPTY,
                1, 0, List.of(m1, m2), List.of(role), Instant.now(), null, null, null, false);

        repo.save(guild1).join();

        GuildServiceImpl service = new GuildServiceImpl(repo, null, null, null, null);

        // Self hit
        assertTrue(service.canHit(player1, player1), "Self hit should be allowed");

        // Same guild, friendlyFire = false
        assertFalse(service.canHit(player1, player2), "Same guild hit should be blocked when friendlyFire is false");

        // Player 3 is not in any guild
        assertTrue(service.canHit(player1, player3), "Hit should be allowed when target is not in a guild");
        assertTrue(service.canHit(player3, player1), "Hit should be allowed when attacker is not in a guild");

        // Enable friendly fire
        guild1.setFriendlyFire(true);
        repo.save(guild1).join();

        assertTrue(service.canHit(player1, player2), "Same guild hit should be allowed when friendlyFire is true");
    }
}
