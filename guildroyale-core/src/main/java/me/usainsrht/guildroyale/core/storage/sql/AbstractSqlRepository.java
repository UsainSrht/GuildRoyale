package me.usainsrht.guildroyale.core.storage.sql;

import com.zaxxer.hikari.HikariDataSource;
import me.usainsrht.guildroyale.api.domain.Guild;
import me.usainsrht.guildroyale.api.domain.GuildMember;
import me.usainsrht.guildroyale.api.domain.GuildRole;
import me.usainsrht.guildroyale.api.domain.RoleColor;
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

    /** Widths of the indexed string columns; see {@link #keyText(int)}. */
    private static final int UUID_LENGTH = 36;
    private static final int GUILD_NAME_MAX = 32;
    private static final int SHORTNAME_MAX = 16;
    private static final int TIMESTAMP_MAX = 64;
    private static final int PERMISSION_MAX = 64;

    protected final FoliaScheduler scheduler;
    protected HikariDataSource dataSource;

    protected AbstractSqlRepository(FoliaScheduler scheduler) {
        this.scheduler = scheduler;
    }

    /** Subclasses configure and return their datasource. */
    protected abstract HikariDataSource buildDataSource();

    /**
     * Column type for indexed string keys (UUIDs, names, timestamps).
     *
     * <p>MySQL cannot index a {@code TEXT} column without a key length, so each
     * backend supplies a type that is valid in a {@code PRIMARY KEY} or
     * {@code UNIQUE} constraint.
     *
     * @param maxLength maximum number of characters the column must hold
     */
    protected abstract String keyText(int maxLength);

    /**
     * The dialect-specific tail of the {@code guilds} upsert, starting after the
     * {@code VALUES (...)} clause. It must update every mutable column.
     */
    protected abstract String guildUpsertSuffix();

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
        String uuid = keyText(UUID_LENGTH);
        String guildName = keyText(GUILD_NAME_MAX);
        String shortname = keyText(SHORTNAME_MAX);
        String timestamp = keyText(TIMESTAMP_MAX);
        String permission = keyText(PERMISSION_MAX);

        try (Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS guilds (
                    id           %s PRIMARY KEY,
                    name         %s NOT NULL UNIQUE,
                    shortname    %s NOT NULL UNIQUE,
                    icon_mat     TEXT,
                    icon_data    BLOB,
                    level        INTEGER NOT NULL DEFAULT 1,
                    xp           BIGINT NOT NULL DEFAULT 0,
                    created_at   %s NOT NULL,
                    owned_badges TEXT,
                    active_badge TEXT,
                    friendly_fire BOOLEAN NOT NULL DEFAULT 0
                )""".formatted(uuid, guildName, shortname, timestamp));

            tryAddColumn(stmt, "guilds", "owned_badges", "TEXT");
            tryAddColumn(stmt, "guilds", "active_badge", "TEXT");
            tryAddColumn(stmt, "guilds", "friendly_fire", "BOOLEAN NOT NULL DEFAULT 0");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS guild_roles (
                    guild_id   %s NOT NULL,
                    role_index INTEGER NOT NULL,
                    name       TEXT NOT NULL,
                    icon_mat   TEXT,
                    icon_data  BLOB,
                    color      TEXT,
                    PRIMARY KEY (guild_id, role_index),
                    FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE
                )""".formatted(uuid));

            tryAddColumn(stmt, "guild_roles", "color", "TEXT");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS guild_role_permissions (
                    guild_id    %s NOT NULL,
                    role_index  INTEGER NOT NULL,
                    permission  %s NOT NULL,
                    PRIMARY KEY (guild_id, role_index, permission),
                    FOREIGN KEY (guild_id, role_index) REFERENCES guild_roles(guild_id, role_index) ON DELETE CASCADE
                )""".formatted(uuid, permission));

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS guild_members (
                    guild_id     %s NOT NULL,
                    player_id    %s NOT NULL,
                    role_index   INTEGER NOT NULL,
                    joined_at    %s NOT NULL,
                    contribution BIGINT NOT NULL DEFAULT 0,
                    PRIMARY KEY (guild_id, player_id),
                    FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE
                )""".formatted(uuid, uuid, timestamp));

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS guild_storage (
                    guild_id  %s NOT NULL,
                    slot      INTEGER NOT NULL,
                    item_mat  TEXT,
                    item_data BLOB,
                    PRIMARY KEY (guild_id, slot),
                    FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE
                )""".formatted(uuid));

            // MySQL rejects CREATE INDEX IF NOT EXISTS, so tolerate "already exists".
            tryExecute(stmt, "CREATE INDEX idx_guild_members_player ON guild_members (player_id)");
        }
    }

    private static void tryAddColumn(Statement stmt, String table, String column, String type) {
        tryExecute(stmt, "ALTER TABLE " + table + " ADD COLUMN " + column + " " + type);
    }

    /** Runs a migration statement that is expected to fail once already applied. */
    private static void tryExecute(Statement stmt, String sql) {
        try {
            stmt.execute(sql);
        } catch (SQLException ignored) {
            // Already applied on a previous startup.
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
                    replaceStorage(conn, guild);
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
                Set<String> ownedBadges = parseBadges(rs.getString("owned_badges"));
                String activeBadge = rs.getString("active_badge");
                boolean friendlyFire = rs.getBoolean("friendly_fire");
                guild = new Guild(guildId, name, shortname, icon, level, xp,
                        new ArrayList<>(), new ArrayList<>(), createdAt,
                        ownedBadges, activeBadge, new HashMap<>(), friendlyFire);
            }
        }

        // Load roles
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM guild_roles WHERE guild_id = ? ORDER BY role_index")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int idx = rs.getInt("role_index");
                    String rName = rs.getString("name");
                    SerializableItemStack rIcon = new SerializableItemStack(
                            rs.getString("icon_mat"), rs.getBytes("icon_data"));
                    RoleColor rColor = RoleColor.fromString(rs.getString("color")).orElse(RoleColor.WHITE);
                    guild.addRole(new GuildRole(rName, idx, loadPermissions(conn, id, idx), rIcon, rColor));
                }
            }
        }

        // Members reference the guild's own role instances so that a permission
        // edit made through the guild is visible to every member holding it.
        Map<Integer, GuildRole> roleByIndex = new HashMap<>();
        guild.getRoles().forEach(r -> roleByIndex.put(r.getIndex(), r));

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
                    GuildRole role = roleByIndex.getOrDefault(rIdx, guild.getDefaultRole());
                    guild.addMember(new GuildMember(playerId, role, joinedAt, contribution));
                }
            }
        }

        Map<Integer, SerializableItemStack> storage = new TreeMap<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT slot, item_mat, item_data FROM guild_storage WHERE guild_id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    storage.put(rs.getInt("slot"),
                            new SerializableItemStack(rs.getString("item_mat"), rs.getBytes("item_data")));
                }
            }
        }
        guild.setStorageContents(storage);

        return Optional.of(guild);
    }

    private static Set<String> parseBadges(String raw) {
        Set<String> set = new LinkedHashSet<>();
        if (raw == null || raw.isBlank()) return set;
        for (String part : raw.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) set.add(trimmed);
        }
        return set;
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
            INSERT INTO guilds (id, name, shortname, icon_mat, icon_data, level, xp, created_at, owned_badges, active_badge, friendly_fire)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """ + guildUpsertSuffix();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, g.getId().toString());
            ps.setString(2, g.getName());
            ps.setString(3, g.getShortname());
            ps.setString(4, g.getIcon().getMaterial());
            ps.setBytes(5, g.getIcon().getRawData());
            ps.setInt(6, g.getLevel());
            ps.setLong(7, g.getXp());
            ps.setString(8, g.getCreatedAt().toString());
            ps.setString(9, String.join(",", g.getOwnedBadges()));
            ps.setString(10, g.getActiveBadgeId());
            ps.setBoolean(11, g.isFriendlyFire());
            ps.executeUpdate();
        }
    }

    private void replaceStorage(Connection conn, Guild g) throws SQLException {
        String guildId = g.getId().toString();
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM guild_storage WHERE guild_id = ?")) {
            ps.setString(1, guildId);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO guild_storage (guild_id, slot, item_mat, item_data) VALUES (?, ?, ?, ?)")) {
            for (Map.Entry<Integer, SerializableItemStack> entry : g.getStorage().entrySet()) {
                SerializableItemStack item = entry.getValue();
                if (item == null || item.isEmpty()) continue;
                ps.setString(1, guildId);
                ps.setInt(2, entry.getKey());
                ps.setString(3, item.getMaterial());
                ps.setBytes(4, item.getRawData());
                ps.addBatch();
            }
            ps.executeBatch();
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
                "INSERT INTO guild_roles (guild_id, role_index, name, icon_mat, icon_data, color) VALUES (?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, guildId);
            ps.setInt(2, role.getIndex());
            ps.setString(3, role.getName());
            ps.setString(4, role.getIcon().getMaterial());
            ps.setBytes(5, role.getIcon().getRawData());
            ps.setString(6, role.getColor().name());
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
