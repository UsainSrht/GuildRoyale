package me.usainsrht.guildroyale.core.event;

import me.usainsrht.guildroyale.core.scheduler.FoliaScheduler;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;

/**
 * Publishes plugin events on a thread where Bukkit listeners may safely run.
 *
 * <p>Service logic completes on asynchronous repository threads, but listeners
 * routinely touch players, inventories and worlds. Calling them from those
 * threads is unsafe on Paper and throws outright on Folia, so every event is
 * handed to the global region scheduler first.
 */
public final class EventDispatcher {

    private final FoliaScheduler scheduler;

    public EventDispatcher(FoliaScheduler scheduler) {
        this.scheduler = scheduler;
    }

    /** Fires {@code event} on the global region / main thread. */
    public void fire(Event event) {
        scheduler.runOnMainThread(() -> Bukkit.getPluginManager().callEvent(event));
    }
}
