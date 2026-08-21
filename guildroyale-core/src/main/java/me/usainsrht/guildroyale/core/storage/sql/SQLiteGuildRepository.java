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
        // SQLite ignores ON DELETE CASCADE unless foreign keys are enabled per connection.
        config.setConnectionInitSql("PRAGMA foreign_keys = ON");
        config.setPoolName("GuildRoyale-SQLite");
        return new HikariDataSource(config);
    }

    @Override
    protected String keyText(int maxLength) {
        return "TEXT";
    }

    @Override
    protected String guildUpsertSuffix() {
        return """
            ON CONFLICT(id) DO UPDATE SET
                name=excluded.name, shortname=excluded.shortname,
                icon_mat=excluded.icon_mat, icon_data=excluded.icon_data,
                level=excluded.level, xp=excluded.xp,
                owned_badges=excluded.owned_badges, active_badge=excluded.active_badge,
                friendly_fire=excluded.friendly_fire, glow=excluded.glow""";
    }
}
