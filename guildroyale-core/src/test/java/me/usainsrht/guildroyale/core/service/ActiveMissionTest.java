package me.usainsrht.guildroyale.core.service;

import me.usainsrht.guildroyale.api.domain.Guild;
import me.usainsrht.guildroyale.api.domain.GuildMember;
import me.usainsrht.guildroyale.api.domain.GuildRole;
import me.usainsrht.guildroyale.api.domain.SerializableItemStack;
import me.usainsrht.guildroyale.api.domain.mission.ActiveMission;
import me.usainsrht.guildroyale.api.domain.mission.GuildMissionData;
import me.usainsrht.guildroyale.api.domain.mission.MissionTaskProgress;
import me.usainsrht.guildroyale.core.storage.json.JsonMissionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ActiveMissionTest {

    @Test
    @DisplayName("ActiveMission calculates overall percentage based on task completion average")
    void testProgressCalculation() {
        UUID guildId = UUID.randomUUID();
        Instant startedAt = Instant.now();
        Instant expiresAt = startedAt.plusSeconds(7200);

        MissionTaskProgress t1 = new MissionTaskProgress("break_stone", 500L, 1000L); // 50%
        MissionTaskProgress t2 = new MissionTaskProgress("kill_zombie", 100L, 100L);  // 100%

        ActiveMission mission = new ActiveMission(guildId, startedAt, expiresAt, List.of(t1, t2));

        assertFalse(mission.isAllCompleted());
        assertEquals(1, mission.getCompletedTaskCount());
        assertEquals(2, mission.getTotalTaskCount());

        // Average: (0.5 + 1.0) / 2 = 0.75 -> 75%
        assertEquals(0.75, mission.getOverallProgressFraction(), 0.001);
        assertEquals(75, mission.getOverallProgressPercent());

        // Complete t1
        t1.addProgress(UUID.randomUUID(), 500L);
        assertTrue(mission.isAllCompleted());
        assertEquals(1.0, mission.getOverallProgressFraction(), 0.001);
        assertEquals(100, mission.getOverallProgressPercent());
    }

    @Test
    @DisplayName("Member contributions are accurately tracked and sorted")
    void testMemberContributions() {
        MissionTaskProgress progress = new MissionTaskProgress("break_stone", 0L, 1000L);
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        UUID p3 = UUID.randomUUID();

        progress.addProgress(p1, 100L);
        progress.addProgress(p2, 250L);
        progress.addProgress(p1, 50L);
        progress.addProgress(p3, 500L);

        assertEquals(900L, progress.getCurrent());
        assertEquals(150L, progress.getContribution(p1));
        assertEquals(250L, progress.getContribution(p2));
        assertEquals(500L, progress.getContribution(p3));

        List<Map.Entry<UUID, Long>> top = progress.getTopContributors(2);
        assertEquals(2, top.size());
        assertEquals(p3, top.get(0).getKey());
        assertEquals(500L, top.get(0).getValue());
        assertEquals(p2, top.get(1).getKey());
        assertEquals(250L, top.get(1).getValue());
    }

    @Test
    @DisplayName("Guild allows negative XP without reducing level")
    void testGuildNegativeXp() {
        GuildRole leaderRole = GuildRole.createLeader();
        UUID playerId = UUID.randomUUID();
        GuildMember leaderMember = new GuildMember(playerId, leaderRole, Instant.now(), 0L);

        Guild guild = new Guild(
                UUID.randomUUID(),
                "AlphaGuild",
                "ALPHA",
                SerializableItemStack.EMPTY,
                1,
                100L,
                List.of(leaderMember),
                List.of(leaderRole),
                Instant.now(),
                null,
                null,
                null,
                false
        );

        assertEquals(100L, guild.getXp());
        assertEquals(1, guild.getLevel());

        // Deduct 500 XP (penalty) -> XP becomes -400, level stays 1
        guild.setXp(guild.getXp() - 500L);
        assertEquals(-400L, guild.getXp());
        assertEquals(1, guild.getLevel());

        guild.addXp(100L);
        assertEquals(-300L, guild.getXp());
        assertEquals(1, guild.getLevel());
    }

    @Test
    @DisplayName("JsonMissionRepository persists and retrieves ActiveMission")
    void testJsonMissionPersistence(@TempDir Path tempDir) {
        JsonMissionRepository repo = new JsonMissionRepository(tempDir.toFile(), null);
        repo.init().join();

        UUID guildId = UUID.randomUUID();
        Instant startedAt = Instant.now();
        Instant expiresAt = startedAt.plusSeconds(7200);

        MissionTaskProgress tp = new MissionTaskProgress("task_1", 250L, 500L);
        UUID p1 = UUID.randomUUID();
        tp.addProgress(p1, 100L);

        ActiveMission activeMission = new ActiveMission(guildId, startedAt, expiresAt, List.of(tp));
        GuildMissionData data = new GuildMissionData(guildId, activeMission, startedAt, null);

        repo.save(data).join();

        GuildMissionData loaded = repo.findByGuildId(guildId).join().orElse(null);
        assertNotNull(loaded);
        assertEquals(guildId, loaded.getGuildId());
        assertEquals(startedAt.getEpochSecond(), loaded.getLastStartedAt().get().getEpochSecond());
        assertNotNull(loaded.getActiveMission());

        ActiveMission loadedMission = loaded.getActiveMission().orElse(null);
        assertNotNull(loadedMission);
        assertEquals(guildId, loadedMission.getGuildId());
        assertEquals(expiresAt.getEpochSecond(), loadedMission.getExpiresAt().getEpochSecond());
        assertEquals(1, loadedMission.getTasks().size());
        assertEquals(350L, loadedMission.getTasks().get("task_1").getCurrent());
        assertEquals(100L, loadedMission.getTasks().get("task_1").getContribution(p1));
    }
}
