package me.usainsrht.guildroyale.core.storage.sql;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.usainsrht.guildroyale.core.scheduler.FoliaScheduler;

/**
 * MySQL / MariaDB-backed guild repository.
 *
 * <p>Uses {@code com.mysql:mysql-connector-j} (NOT relocated — SPI registration
 * requires the original package name).
 */
public final class MySQLGuildRepository extends AbstractSqlRepository {

    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    private final int poolSize;

    public MySQLGuildRepository(String host, int port, String database,
                                 String username, String password, int poolSize,
                                 FoliaScheduler scheduler) {
        super(scheduler);
        this.host = host;
        this.port = port;
        this.database = database;
        this.username = username;
        this.password = password;
        this.poolSize = poolSize;
    }

    @Override
    protected HikariDataSource buildDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true"
                + "&characterEncoding=utf8");
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(poolSize);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30_000L);
        config.setIdleTimeout(600_000L);
        config.setMaxLifetime(1_800_000L);
        config.setPoolName("GuildRoyale-MySQL");
        return new HikariDataSource(config);
    }

    @Override
    protected String keyText(int maxLength) {
        return "VARCHAR(" + maxLength + ")";
    }

    @Override
    protected String guildUpsertSuffix() {
        return """
            ON DUPLICATE KEY UPDATE
                name=VALUES(name), shortname=VALUES(shortname),
                icon_mat=VALUES(icon_mat), icon_data=VALUES(icon_data),
                level=VALUES(level), xp=VALUES(xp),
                owned_badges=VALUES(owned_badges), active_badge=VALUES(active_badge),
                friendly_fire=VALUES(friendly_fire)""";
    }
}
