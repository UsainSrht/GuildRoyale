package me.usainsrht.guildroyale.core.storage.sql;

import com.zaxxer.hikari.HikariDataSource;
import me.usainsrht.guildroyale.api.domain.Guild;
import me.usainsrht.guildroyale.api.domain.GuildMember;
import me.usainsrht.guildroyale.api.domain.GuildRole;
import me.usainsrht.guildroyale.api.domain.SerializableItemStack;
import me.usainsrht.guildroyale.api.permission.GuildPermissionKey;
import me.usainsrht.guildroyale.api.storage.GuildRepository;
import me.usainsrht.guildroyale.core.scheduler.FoliaScheduler;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Abstract SQL-backed {@link GuildRepository} using HikariCP.
 *
 * <p>Concrete subclasses supply a configured {@link HikariDataSource} via
 * {@link #buildDataSource()}. Schema creation and migrations are handled here.
 */
public abstract class AbstractSqlRepository implements GuildRepository {

    protected final FoliaScheduler scheduler;
    protected HikariDataSource dataSource;

    protected AbstractSqlRepository(FoliaScheduler scheduler) {
        this.scheduler = scheduler;
    }

    /** Subclasses configure and return their datasource. */
    protected abstract HikariDataSource buildDataSource();

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public CompletableFuture<Void> init() {
        return CompletableFuture.runAsync(() -> {
            dataSource = buildDataSource();
            try (Connection conn = dataSource.getConnection()) {
                createSchema(conn);
            } catch (SQLException e) {
                throw new RuntimeException("Failed to initialise database schema", e);
            }
        });
    }

    @Override
    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    // ── Schema ────────────────────────────────────────────────────────────────

    private void createSchema(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS guilds (
                    id         TEXT PRIMARY KEY,
                    name       TEXT NOT NULL UNIQUE,
                    shortname  TEXT NOT NULL UNIQUE,
                    icon_mat   TEXT,
                    icon_data  BLOB,
                    level      INTEGER NOT NULL DEFAULT 1,
                    xp         INTEGER NOT NULL DEFAULT 0,
                    created_at TEXT NOT NULL
                )""");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS guild_roles (
                    guild_id   TEXT NOT NULL,
                    role_index INTEGER NOT NULL,
                    name       TEXT NOT NULL,
                    icon_mat   TEXT,
                    icon_data  BLOB,
                    PRIMARY KEY (guild_id, role_index),
                    FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE
                )""");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS guild_role_permissions (
                    guild_id    TEXT NOT NULL,
                    role_index  INTEGER NOT NULL,
                    permission  TEXT NOT NULL,
                    PRIMARY KEY (guild_id, role_index, permission),
                    FOREIGN KEY (guild_id, role_index) REFERENCES guild_roles(guild_id, role_index) ON DELETE CASCADE
                )""");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS guild_members (
                    guild_id     TEXT NOT NULL,
                    player_id    TEXT NOT NULL,
                    role_index   INTEGER NOT NULL,
                    joined_at    TEXT NOT NULL,
                    contribution INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY (guild_id, player_id),
                    FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE
                )""");
        }
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    @Override
    public CompletableFuture<Optional<Guild>> findById(UUID id) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection()) {
                return loadGuild(conn, id.toString());
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Optional<Guild>> findByMember(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT guild_id FROM guild_members WHERE player_id = ?")) {
                ps.setString(1, playerId.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return loadGuild(conn, rs.getString("guild_id"));
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return Optional.empty();
        });
    }

    @Override
    public CompletableFuture<Optional<Guild>> findByName(String name) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT id FROM guilds WHERE LOWER(name) = LOWER(?)")) {
                ps.setString(1, name);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return loadGuild(conn, rs.getString("id"));
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return Optional.empty();
        });
    }

    @Override
    public CompletableFuture<List<Guild>> findAll() {
        return CompletableFuture.supplyAsync(() -> {
            List<Guild> result = new ArrayList<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT id FROM guilds");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    loadGuild(conn, rs.getString("id")).ifPresent(result::add);
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return result;
        });
    }

    @Override
    public CompletableFuture<List<Guild>> getLeaderboard(int limit) {
        return CompletableFuture.supplyAsync(() -> {
            List<Guild> result = new ArrayList<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT id FROM guilds ORDER BY level DESC, xp DESC LIMIT ?")) {
                ps.setInt(1, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        loadGuild(conn, rs.getString("id")).ifPresent(result::add);
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return result;
        });
    }

    @Override
    public CompletableFuture<Boolean> existsByName(String name) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT 1 FROM guilds WHERE LOWER(name) = LOWER(?)")) {
                ps.setString(1, name);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> isPlayerInAnyGuild(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT 1 FROM guild_members WHERE player_id = ?")) {
                ps.setString(1, playerId.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    // ── Mutations ─────────────────────────────────────────────────────────────

    @Override
    public CompletableFuture<Void> save(Guild guild) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    upsertGuild(conn, guild);
                    deleteRolesAndMembers(conn, guild.getId().toString());
                    for (GuildRole role : guild.getRoles()) upsertRole(conn, guild.getId().toString(), role);
                    for (GuildMember member : guild.getMembers()) upsertMember(conn, guild.getId().toString(), member);
                    conn.commit();
                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(true);
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to save guild " + guild.getId(), e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> delete(UUID id) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("DELETE FROM guilds WHERE id = ?")) {
                ps.setString(1, id.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Optional<Guild> loadGuild(Connection conn, String id) throws SQLException {
        Guild guild;
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM guilds WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                UUID guildId = UUID.fromString(rs.getString("id"));
                String name = rs.getString("name");
                String shortname = rs.getString("shortname");
                int level = rs.getInt("level");
                long xp = rs.getLong("xp");
                Instant createdAt = Instant.parse(rs.getString("created_at"));
                SerializableItemStack icon = new SerializableItemStack(
                        rs.getString("icon_mat"), rs.getBytes("icon_data"));
                guild = new Guild(guildId, name, shortname, icon, level, xp,
                        new ArrayList<>(), new ArrayList<>(), createdAt);
            }
        }

        // Load roles
        List<GuildRole> roles = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM guild_roles WHERE guild_id = ? ORDER BY role_index")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int idx = rs.getInt("role_index");
                    String rName = rs.getString("name");
                    SerializableItemStack rIcon = new SerializableItemStack(
                            rs.getString("icon_mat"), rs.getBytes("icon_data"));
                    Set<GuildPermissionKey> perms = loadPermissions(conn, id, idx);
                    roles.add(new GuildRole(rName, idx, perms, rIcon));
                    guild.addRole(new GuildRole(rName, idx, perms, rIcon));
                }
            }
        }

        // Build index map for members
        Map<Integer, GuildRole> roleByIndex = new HashMap<>();
        roles.forEach(r -> roleByIndex.put(r.getIndex(), r));
        // Re-add roles to clean guild object
        guild.getRoles().forEach(r -> {}); // roles already added above via addRole

        // Load members
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM guild_members WHERE guild_id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID playerId = UUID.fromString(rs.getString("player_id"));
                    int rIdx = rs.getInt("role_index");
                    Instant joinedAt = Instant.parse(rs.getString("joined_at"));
                    long contribution = rs.getLong("contribution");
                    GuildRole role = roleByIndex.getOrDefault(rIdx,
                            roles.stream().max(Comparator.comparingInt(GuildRole::getIndex)).orElseThrow());
                    guild.addMember(new GuildMember(playerId, role, joinedAt, contribution));
                }
            }
        }

        return Optional.of(guild);
    }

    private Set<GuildPermissionKey> loadPermissions(Connection conn, String guildId, int roleIndex) throws SQLException {
        Set<GuildPermissionKey> perms = new HashSet<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT permission FROM guild_role_permissions WHERE guild_id = ? AND role_index = ?")) {
            ps.setString(1, guildId);
            ps.setInt(2, roleIndex);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    try { perms.add(GuildPermissionKey.valueOf(rs.getString("permission"))); } catch (Exception ignored) {}
                }
            }
        }
        return perms;
    }

    private void upsertGuild(Connection conn, Guild g) throws SQLException {
        String sql = """
            INSERT INTO guilds (id, name, shortname, icon_mat, icon_data, level, xp, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                name=excluded.name, shortname=excluded.shortname,
                icon_mat=excluded.icon_mat, icon_data=excluded.icon_data,
                level=excluded.level, xp=excluded.xp""";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, g.getId().toString());
            ps.setString(2, g.getName());
            ps.setString(3, g.getShortname());
            ps.setString(4, g.getIcon().getMaterial());
            ps.setBytes(5, g.getIcon().getRawData());
            ps.setInt(6, g.getLevel());
            ps.setLong(7, g.getXp());
            ps.setString(8, g.getCreatedAt().toString());
            ps.executeUpdate();
        }
    }

    private void deleteRolesAndMembers(Connection conn, String guildId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM guild_members WHERE guild_id = ?")) {
            ps.setString(1, guildId); ps.executeUpdate();
        }
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM guild_role_permissions WHERE guild_id = ?")) {
            ps.setString(1, guildId); ps.executeUpdate();
        }
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM guild_roles WHERE guild_id = ?")) {
            ps.setString(1, guildId); ps.executeUpdate();
        }
    }

    private void upsertRole(Connection conn, String guildId, GuildRole role) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO guild_roles (guild_id, role_index, name, icon_mat, icon_data) VALUES (?, ?, ?, ?, ?)")) {
            ps.setString(1, guildId);
            ps.setInt(2, role.getIndex());
            ps.setString(3, role.getName());
            ps.setString(4, role.getIcon().getMaterial());
            ps.setBytes(5, role.getIcon().getRawData());
            ps.executeUpdate();
        }
        for (GuildPermissionKey perm : role.getPermissions()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO guild_role_permissions (guild_id, role_index, permission) VALUES (?, ?, ?)")) {
                ps.setString(1, guildId);
                ps.setInt(2, role.getIndex());
                ps.setString(3, perm.name());
                ps.executeUpdate();
            }
        }
    }

    private void upsertMember(Connection conn, String guildId, GuildMember member) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO guild_members (guild_id, player_id, role_index, joined_at, contribution) VALUES (?, ?, ?, ?, ?)")) {
            ps.setString(1, guildId);
            ps.setString(2, member.getPlayerId().toString());
            ps.setInt(3, member.getRole().getIndex());
            ps.setString(4, member.getJoinedAt().toString());
            ps.setLong(5, member.getContribution());
            ps.executeUpdate();
        }
    }
}
