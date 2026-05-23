package me.usainsrht.guildroyale.core.storage.sql;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.usainsrht.guildroyale.core.scheduler.FoliaScheduler;

import java.io.File;

/**
 * SQLite-backed guild repository.
 *
 * <p>Uses the {@code org.xerial:sqlite-jdbc} driver via HikariCP.
 * WAL mode is enabled for improved concurrent read performance.
 */
public final class SQLiteGuildRepository extends AbstractSqlRepository {

    private final File dbFile;

    public SQLiteGuildRepository(File dbFile, FoliaScheduler scheduler) {
        super(scheduler);
        this.dbFile = dbFile;
    }

    @Override
    protected HikariDataSource buildDataSource() {
        dbFile.getParentFile().mkdirs();
        HikariConfig config = new HikariConfig();
        config.setDriverClassName("org.sqlite.JDBC");
        config.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
        config.setMaximumPoolSize(1); // SQLite supports only one writer at a time
        config.setMinimumIdle(1);
        config.setConnectionTestQuery("SELECT 1");
        config.addDataSourceProperty("journal_mode", "WAL");
        config.addDataSourceProperty("synchronous", "NORMAL");
        config.setPoolName("GuildRoyale-SQLite");
        return new HikariDataSource(config);
    }
}
