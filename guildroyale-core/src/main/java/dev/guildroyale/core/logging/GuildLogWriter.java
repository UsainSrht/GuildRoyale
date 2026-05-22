package dev.guildroyale.core.logging;

import dev.guildroyale.api.logging.GuildLogger;
import dev.guildroyale.api.logging.LogEntry;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Writes {@link LogEntry} instances to rotating daily log files:
 * <ul>
 *   <li>{@code plugins/GuildRoyale/logs/global.log}</li>
 *   <li>{@code plugins/GuildRoyale/logs/guilds/<guildId>.log}</li>
 *   <li>{@code plugins/GuildRoyale/logs/players/<playerId>.log}</li>
 * </ul>
 *
 * <p>All writes are dispatched to a single-threaded async executor to avoid
 * blocking the calling (scheduler) thread.
 */
public final class GuildLogWriter implements GuildLogger, AutoCloseable {

    private static final DateTimeFormatter TIMESTAMP_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

    private final Path logsDir;
    private final ExecutorService writer = Executors.newSingleThreadExecutor(
            r -> new Thread(r, "GuildRoyale-LogWriter"));

    public GuildLogWriter(JavaPlugin plugin) {
        this.logsDir = plugin.getDataFolder().toPath().resolve("logs");
    }

    @Override
    public void log(LogEntry entry) {
        writer.submit(() -> writeEntry(entry));
    }

    private void writeEntry(LogEntry entry) {
        try {
            Path file = switch (entry.scope()) {
                case GLOBAL -> logsDir.resolve("global.log");
                case GUILD  -> logsDir.resolve("guilds").resolve(entry.scopeId() + ".log");
                case PLAYER -> logsDir.resolve("players").resolve(entry.scopeId() + ".log");
            };
            Files.createDirectories(file.getParent());
            String line = TIMESTAMP_FMT.format(entry.timestamp()) + " " + entry.message() + System.lineSeparator();
            Files.writeString(file, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // Best-effort logging — never crash the main thread
        }
    }

    @Override
    public void close() {
        writer.shutdown();
    }
}
