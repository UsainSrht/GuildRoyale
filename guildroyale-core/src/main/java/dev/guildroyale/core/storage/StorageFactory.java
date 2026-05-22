package dev.guildroyale.core.storage;

import dev.guildroyale.api.storage.GuildRepository;
import dev.guildroyale.api.storage.StorageType;
import dev.guildroyale.core.config.ConfigManager;
import dev.guildroyale.core.scheduler.FoliaScheduler;
import dev.guildroyale.core.storage.json.JsonGuildRepository;
import dev.guildroyale.core.storage.sql.MySQLGuildRepository;
import dev.guildroyale.core.storage.sql.SQLiteGuildRepository;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

/**
 * Reads the storage type from {@link ConfigManager} and returns the appropriate
 * {@link GuildRepository} implementation.
 */
public final class StorageFactory {

    private StorageFactory() {}

    public static GuildRepository create(JavaPlugin plugin, ConfigManager config, FoliaScheduler scheduler) {
        StorageType type = StorageType.fromString(config.getStorageType());
        return switch (type) {
            case SQLITE -> {
                File dbFile = new File(plugin.getDataFolder(), config.getSqliteFile());
                yield new SQLiteGuildRepository(dbFile, scheduler);
            }
            case MYSQL -> new MySQLGuildRepository(
                    config.getMysqlHost(),
                    config.getMysqlPort(),
                    config.getMysqlDatabase(),
                    config.getMysqlUsername(),
                    config.getMysqlPassword(),
                    config.getMysqlPoolSize(),
                    scheduler
            );
            case JSON -> {
                File dataDir = new File(plugin.getDataFolder(), "data/guilds");
                yield new JsonGuildRepository(dataDir, scheduler);
            }
        };
    }
}
