package me.usainsrht.guildroyale.api.domain.mission;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks the progress of a specific task within an active mission.
 */
public final class MissionTaskProgress {

    private final String taskId;
    private long current;
    private final long target;
    private final Map<UUID, Long> contributions;

    public MissionTaskProgress(String taskId, long current, long target) {
        this(taskId, current, target, Map.of());
    }

    public MissionTaskProgress(String taskId, long current, long target, Map<UUID, Long> contributions) {
        this.taskId = Objects.requireNonNull(taskId, "taskId");
        this.target = Math.max(1, target);
        this.current = Math.max(0, Math.min(this.target, current));
        this.contributions = new ConcurrentHashMap<>();
        if (contributions != null) {
            contributions.forEach((k, v) -> {
                if (k != null && v != null && v > 0) {
                    this.contributions.put(k, v);
                }
            });
        }
    }

    public String getTaskId() {
        return taskId;
    }

    public synchronized long getCurrent() {
        return current;
    }

    public long getTarget() {
        return target;
    }

    public synchronized void setCurrent(long amount) {
        this.current = Math.max(0, Math.min(target, amount));
    }

    public synchronized boolean isCompleted() {
        return current >= target;
    }

    public synchronized double getProgressFraction() {
        return Math.min(1.0, (double) current / (double) target);
    }

    public synchronized int getProgressPercent() {
        return (int) Math.round(getProgressFraction() * 100.0);
    }

    /**
     * Adds progress for this task and attributes it to a contributor if provided.
     *
     * @param contributorId UUID of contributing member (may be null for console/admin)
     * @param amount amount to add (must be > 0)
     * @return actual amount added before hitting target
     */
    public synchronized long addProgress(UUID contributorId, long amount) {
        if (amount <= 0) return 0;
        long old = current;
        current = Math.min(target, current + amount);
        long gained = current - old;
        if (gained > 0 && contributorId != null) {
            contributions.merge(contributorId, gained, Long::sum);
        }
        return gained;
    }

    /**
     * Removes progress from this task.
     *
     * @param contributorId UUID of member to deduct contribution from (may be null)
     * @param amount amount to remove
     * @return actual amount removed
     */
    public synchronized long removeProgress(UUID contributorId, long amount) {
        if (amount <= 0) return 0;
        long old = current;
        current = Math.max(0, current - amount);
        long lost = old - current;
        if (lost > 0 && contributorId != null) {
            contributions.computeIfPresent(contributorId, (k, v) -> {
                long nv = v - lost;
                return nv > 0 ? nv : null;
            });
        }
        return lost;
    }

    public Map<UUID, Long> getContributions() {
        return Collections.unmodifiableMap(new HashMap<>(contributions));
    }

    public long getContribution(UUID contributorId) {
        if (contributorId == null) return 0L;
        return contributions.getOrDefault(contributorId, 0L);
    }

    public List<Map.Entry<UUID, Long>> getTopContributors(int limit) {
        return contributions.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .sorted(Map.Entry.<UUID, Long>comparingByValue().reversed())
                .limit(limit)
                .toList();
    }
}
