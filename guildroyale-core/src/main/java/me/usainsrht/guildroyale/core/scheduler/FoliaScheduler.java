package me.usainsrht.guildroyale.core.scheduler;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;
import space.arim.morepaperlib.MorePaperLib;
import space.arim.morepaperlib.scheduling.GracefulScheduling;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Folia-safe scheduling facade backed by MorePaperLib.
 *
 * <p>All methods dispatch work to the correct scheduler so that the plugin
 * is compatible with both Paper and Folia out of the box.
 *
 * <p>Threading rules:
 * <ul>
 *   <li>Player inventory access must run on the player's region thread
 *       → use {@link #runForEntity(Entity, Runnable)}</li>
 *   <li>Location-sensitive work must run on the region owning that chunk
 *       → use {@link #runForRegion(Location, Runnable)}</li>
 *   <li>Database / file I/O must run asynchronously
 *       → use {@link #runAsync(Runnable)}</li>
 * </ul>
 */
public final class FoliaScheduler {

    private final GracefulScheduling scheduling;

    public FoliaScheduler(JavaPlugin plugin) {
        this.scheduling = new MorePaperLib(plugin).scheduling();
    }

    /** Runs {@code task} on the async thread pool immediately. */
    public void runAsync(Runnable task) {
        scheduling.asyncScheduler().run(task);
    }

    /** Runs {@code task} on the global region / main thread. */
    public void runOnMainThread(Runnable task) {
        scheduling.globalRegionalScheduler().run(task);
    }

    /** Runs {@code task} on the region thread that owns the entity. */
    public void runForEntity(Entity entity, Runnable task) {
        scheduling.entitySpecificScheduler(entity).run(task, null);
    }

    /** Runs {@code task} on the region thread that owns the chunk at {@code location}. */
    public void runForRegion(Location location, Runnable task) {
        scheduling.regionSpecificScheduler(location).run(task);
    }

    /**
     * Schedules {@code task} to run asynchronously at a fixed rate.
     *
     * @param task         the task to execute
     * @param initialDelay delay before first run (in the given unit)
     * @param period       interval between subsequent runs
     * @param unit         time unit for {@code initialDelay} and {@code period}
     */
    public void scheduleAsyncRepeating(Runnable task, long initialDelay, long period, TimeUnit unit) {
        scheduling.asyncScheduler().runAtFixedRate(
                task,
                Duration.of(initialDelay, unit.toChronoUnit()),
                Duration.of(period, unit.toChronoUnit())
        );
    }
}
