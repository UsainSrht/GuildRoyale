package me.usainsrht.guildroyale.core.service;

import me.usainsrht.guildroyale.core.config.MissionConfig;
import net.kyori.adventure.bossbar.BossBar;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class MissionCooldownTest {

    @Test
    @DisplayName("Delay cooldown calculates remaining seconds correctly")
    void testDelayCooldown() {
        MissionConfig config = new MissionConfig(
                true,
                7200,
                MissionConfig.CooldownType.DELAY,
                3600, // 1 hour delay
                LocalTime.of(12, 0),
                DayOfWeek.MONDAY,
                LocalTime.of(0, 0),
                ZoneId.of("UTC"),
                false, "perm",
                false, 0, MissionConfig.TakeFrom.BANK,
                false, Collections.emptyList(),
                5000, 2500,
                2500, 1000,
                true, "title", BossBar.Color.GREEN, BossBar.Overlay.PROGRESS,
                Collections.emptyList()
        );

        Instant now = Instant.parse("2026-08-21T12:00:00Z");
        Instant lastStarted = Instant.parse("2026-08-21T11:30:00Z"); // 30 mins ago

        long remaining = MissionServiceImpl.calculateCooldownRemainingSeconds(config, lastStarted, now);
        assertEquals(1800, remaining); // 30 minutes remaining

        Instant expiredStarted = Instant.parse("2026-08-21T10:00:00Z"); // 2 hours ago
        long remainingExpired = MissionServiceImpl.calculateCooldownRemainingSeconds(config, expiredStarted, now);
        assertEquals(0, remainingExpired);
    }

    @Test
    @DisplayName("Daily cooldown resets at 12:00 PM UTC")
    void testDailyCooldown() {
        MissionConfig config = new MissionConfig(
                true,
                7200,
                MissionConfig.CooldownType.DAILY,
                86400,
                LocalTime.of(12, 0),
                DayOfWeek.MONDAY,
                LocalTime.of(0, 0),
                ZoneId.of("UTC"),
                false, "perm",
                false, 0, MissionConfig.TakeFrom.BANK,
                false, Collections.emptyList(),
                5000, 2500,
                2500, 1000,
                true, "title", BossBar.Color.GREEN, BossBar.Overlay.PROGRESS,
                Collections.emptyList()
        );

        // Started before today's 12:00 PM (e.g. at 11:00 AM on Aug 21)
        Instant lastStartedBeforeReset = Instant.parse("2026-08-21T11:00:00Z");
        // Checked after today's 12:00 PM (e.g. at 12:05 PM on Aug 21)
        Instant nowAfterReset = Instant.parse("2026-08-21T12:05:00Z");
        long remainingAfterReset = MissionServiceImpl.calculateCooldownRemainingSeconds(config, lastStartedBeforeReset, nowAfterReset);
        assertEquals(0, remainingAfterReset); // Cooldown has elapsed because 12:00 passed

        // Started after today's 12:00 PM (e.g. at 12:30 PM on Aug 21)
        Instant lastStartedAfterReset = Instant.parse("2026-08-21T12:30:00Z");
        // Checked at 1:30 PM on Aug 21
        Instant nowDuringCooldown = Instant.parse("2026-08-21T13:30:00Z");
        long remaining = MissionServiceImpl.calculateCooldownRemainingSeconds(config, lastStartedAfterReset, nowDuringCooldown);
        // Next reset is tomorrow at 12:00 PM (22.5 hours = 81000 seconds)
        assertEquals(81000, remaining);
    }

    @Test
    @DisplayName("Weekly cooldown resets on Monday 00:00 UTC")
    void testWeeklyCooldown() {
        MissionConfig config = new MissionConfig(
                true,
                7200,
                MissionConfig.CooldownType.WEEKLY,
                86400,
                LocalTime.of(12, 0),
                DayOfWeek.MONDAY,
                LocalTime.of(0, 0),
                ZoneId.of("UTC"),
                false, "perm",
                false, 0, MissionConfig.TakeFrom.BANK,
                false, Collections.emptyList(),
                5000, 2500,
                2500, 1000,
                true, "title", BossBar.Color.GREEN, BossBar.Overlay.PROGRESS,
                Collections.emptyList()
        );

        // 2026-08-17 is Monday 00:00 UTC.
        // Started on Sunday Aug 16 at 20:00 UTC.
        Instant lastStarted = Instant.parse("2026-08-16T20:00:00Z");
        // Checked on Monday Aug 17 at 01:00 UTC.
        Instant now = Instant.parse("2026-08-17T01:00:00Z");

        long remaining = MissionServiceImpl.calculateCooldownRemainingSeconds(config, lastStarted, now);
        assertEquals(0, remaining);
    }
}
