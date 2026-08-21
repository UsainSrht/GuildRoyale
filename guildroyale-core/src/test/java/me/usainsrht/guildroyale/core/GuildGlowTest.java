package me.usainsrht.guildroyale.core;

import me.usainsrht.guildroyale.api.domain.Guild;
import me.usainsrht.guildroyale.api.domain.GuildMember;
import me.usainsrht.guildroyale.api.domain.GuildRole;
import me.usainsrht.guildroyale.api.domain.RoleColor;
import me.usainsrht.guildroyale.api.domain.SerializableItemStack;
import me.usainsrht.guildroyale.core.glow.GlowManager;
import me.usainsrht.guildroyale.core.storage.json.JsonGuildRepository;
import me.usainsrht.guildroyale.core.storage.sql.SQLiteGuildRepository;
import org.bukkit.ChatColor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class GuildGlowTest {

    @Test
    public void testRoleColorToChatColorMapping() {
        for (RoleColor color : RoleColor.values()) {
            ChatColor chatColor = GlowManager.toChatColor(color);
            assertNotNull(chatColor, "ChatColor must not be null for RoleColor: " + color);
        }

        assertEquals(ChatColor.WHITE, GlowManager.toChatColor(RoleColor.WHITE));
        assertEquals(ChatColor.GOLD, GlowManager.toChatColor(RoleColor.ORANGE));
        assertEquals(ChatColor.LIGHT_PURPLE, GlowManager.toChatColor(RoleColor.MAGENTA));
        assertEquals(ChatColor.AQUA, GlowManager.toChatColor(RoleColor.LIGHT_BLUE));
        assertEquals(ChatColor.YELLOW, GlowManager.toChatColor(RoleColor.YELLOW));
        assertEquals(ChatColor.GREEN, GlowManager.toChatColor(RoleColor.LIME));
        assertEquals(ChatColor.LIGHT_PURPLE, GlowManager.toChatColor(RoleColor.PINK));
        assertEquals(ChatColor.DARK_GRAY, GlowManager.toChatColor(RoleColor.GRAY));
        assertEquals(ChatColor.GRAY, GlowManager.toChatColor(RoleColor.LIGHT_GRAY));
        assertEquals(ChatColor.DARK_AQUA, GlowManager.toChatColor(RoleColor.CYAN));
        assertEquals(ChatColor.DARK_PURPLE, GlowManager.toChatColor(RoleColor.PURPLE));
        assertEquals(ChatColor.BLUE, GlowManager.toChatColor(RoleColor.BLUE));
        assertEquals(ChatColor.GOLD, GlowManager.toChatColor(RoleColor.BROWN));
        assertEquals(ChatColor.DARK_GREEN, GlowManager.toChatColor(RoleColor.GREEN));
        assertEquals(ChatColor.RED, GlowManager.toChatColor(RoleColor.RED));
        assertEquals(ChatColor.BLACK, GlowManager.toChatColor(RoleColor.BLACK));
        assertEquals(ChatColor.WHITE, GlowManager.toChatColor(null));
    }

    @Test
    public void testJsonGuildRepositoryGlowPersistence(@TempDir File tempDir) {
        JsonGuildRepository repo = new JsonGuildRepository(tempDir, null);
        repo.init().join();

        UUID guildId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();

        GuildRole leaderRole = GuildRole.createLeader();
        leaderRole.setGlowColor(RoleColor.CYAN);

        GuildMember member = new GuildMember(playerId, leaderRole, Instant.now(), 0L);

        Guild guild = new Guild(guildId, "GlowGuild", "GLOW", SerializableItemStack.EMPTY,
                1, 0L, List.of(member), List.of(leaderRole), Instant.now());
        guild.setGlow(true);

        assertTrue(guild.isGlow());
        assertEquals(RoleColor.CYAN, leaderRole.getGlowColor());

        repo.save(guild).join();

        JsonGuildRepository reloadedRepo = new JsonGuildRepository(tempDir, null);
        reloadedRepo.init().join();

        Guild loaded = reloadedRepo.findById(guildId).join().orElseThrow();
        assertTrue(loaded.isGlow(), "Guild glow setting should persist as true");

        GuildRole loadedRole = loaded.getRole(0).orElseThrow();
        assertEquals(RoleColor.CYAN, loadedRole.getGlowColor(), "Role glow color should persist as CYAN");
    }

    @Test
    public void testSqliteGuildRepositoryGlowPersistence(@TempDir File tempDir) {
        File dbFile = new File(tempDir, "guilds.db");
        SQLiteGuildRepository repo = new SQLiteGuildRepository(dbFile, null);
        repo.init().join();

        UUID guildId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();

        GuildRole leaderRole = GuildRole.createLeader();
        leaderRole.setGlowColor(RoleColor.PURPLE);

        GuildMember member = new GuildMember(playerId, leaderRole, Instant.now(), 0L);

        Guild guild = new Guild(guildId, "SqlGlowGuild", "SGLOW", SerializableItemStack.EMPTY,
                1, 0L, List.of(member), List.of(leaderRole), Instant.now());
        guild.setGlow(true);

        repo.save(guild).join();
        repo.shutdown();

        SQLiteGuildRepository reloadedRepo = new SQLiteGuildRepository(dbFile, null);
        reloadedRepo.init().join();

        Guild loaded = reloadedRepo.findById(guildId).join().orElseThrow();
        assertTrue(loaded.isGlow(), "SQLite guild glow setting should persist as true");

        GuildRole loadedRole = loaded.getRole(0).orElseThrow();
        assertEquals(RoleColor.PURPLE, loadedRole.getGlowColor(), "SQLite role glow color should persist as PURPLE");

        // Now toggle glow off and update role glow color
        loaded.setGlow(false);
        loadedRole.setGlowColor(RoleColor.LIME);
        reloadedRepo.save(loaded).join();
        reloadedRepo.shutdown();

        SQLiteGuildRepository secondReloadRepo = new SQLiteGuildRepository(dbFile, null);
        secondReloadRepo.init().join();

        Guild secondLoaded = secondReloadRepo.findById(guildId).join().orElseThrow();
        assertFalse(secondLoaded.isGlow(), "Guild glow setting should persist as false after update");
        assertEquals(RoleColor.LIME, secondLoaded.getRole(0).orElseThrow().getGlowColor(), "Role glow color should be updated to LIME");
        secondReloadRepo.shutdown();
    }
}
