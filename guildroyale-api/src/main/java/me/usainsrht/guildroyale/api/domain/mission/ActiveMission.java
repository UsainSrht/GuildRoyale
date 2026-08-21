package me.usainsrht.guildroyale.api.domain.mission;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents an ongoing, active mission for a guild.
 */
public final class ActiveMission {

    private final UUID guildId;
    private final Instant startedAt;
    private final Instant expiresAt;
    private final Map<String, MissionTaskProgress> tasks;

    public ActiveMission(UUID guildId, Instant startedAt, Instant expiresAt,
                         List<MissionTaskProgress> taskList) {
        this.guildId = Objects.requireNonNull(guildId, "guildId");
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        this.tasks = new LinkedHashMap<>();
        if (taskList != null) {
            for (MissionTaskProgress t : taskList) {
                if (t != null) {
                    this.tasks.put(t.getTaskId(), t);
                }
            }
        }
    }

    public UUID getGuildId() {
        return guildId;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Map<String, MissionTaskProgress> getTasks() {
        return Collections.unmodifiableMap(tasks);
    }

    public Optional<MissionTaskProgress> getTask(String taskId) {
        if (taskId == null) return Optional.empty();
        return Optional.ofNullable(tasks.get(taskId));
    }

    public int getTotalTaskCount() {
        return tasks.size();
    }

    public int getCompletedTaskCount() {
        return (int) tasks.values().stream().filter(MissionTaskProgress::isCompleted).count();
    }

    public boolean isAllCompleted() {
        if (tasks.isEmpty()) return false;
        return tasks.values().stream().allMatch(MissionTaskProgress::isCompleted);
    }

    /**
     * Calculates the overall completion fraction across all tasks (0.0 to 1.0).
     */
    public double getOverallProgressFraction() {
        if (tasks.isEmpty()) return 0.0;
        double sum = 0.0;
        for (MissionTaskProgress p : tasks.values()) {
            sum += p.getProgressFraction();
        }
        return Math.min(1.0, Math.max(0.0, sum / (double) tasks.size()));
    }

    /**
     * Calculates the overall completion percent (0 to 100).
     */
    public int getOverallProgressPercent() {
        return (int) Math.round(getOverallProgressFraction() * 100.0);
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public long getRemainingSeconds() {
        long sec = Duration.between(Instant.now(), expiresAt).getSeconds();
        return Math.max(0L, sec);
    }

    public Duration getRemainingDuration() {
        return Duration.ofSeconds(getRemainingSeconds());
    }

    /**
     * Returns total aggregated contributions by player across all tasks.
     */
    public Map<UUID, Long> getTotalContributions() {
        Map<UUID, Long> total = new HashMap<>();
        for (MissionTaskProgress p : tasks.values()) {
            p.getContributions().forEach((pid, amt) -> total.merge(pid, amt, Long::sum));
        }
        return Collections.unmodifiableMap(total);
    }

    public List<Map.Entry<UUID, Long>> getTopContributors(int limit) {
        return getTotalContributions().entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .sorted(Map.Entry.<UUID, Long>comparingByValue().reversed())
                .limit(limit)
                .toList();
    }
}
