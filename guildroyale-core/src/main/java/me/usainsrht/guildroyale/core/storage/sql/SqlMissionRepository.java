package me.usainsrht.guildroyale.core.storage.sql;

import com.google.gson.*;
import com.zaxxer.hikari.HikariDataSource;
import me.usainsrht.guildroyale.api.domain.mission.ActiveMission;
import me.usainsrht.guildroyale.api.domain.mission.GuildMissionData;
import me.usainsrht.guildroyale.api.domain.mission.MissionTaskProgress;
import me.usainsrht.guildroyale.api.storage.MissionRepository;
import me.usainsrht.guildroyale.core.scheduler.FoliaScheduler;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * SQL-backed implementation of {@link MissionRepository} for SQLite and MySQL.
 */
public final class SqlMissionRepository implements MissionRepository {

    private final HikariDataSource dataSource;
    private final FoliaScheduler scheduler;
    private final boolean isMysql;
    private final Gson gson;

    public SqlMissionRepository(HikariDataSource dataSource, FoliaScheduler scheduler, boolean isMysql) {
        this.dataSource = dataSource;
        this.scheduler = scheduler;
        this.isMysql = isMysql;
        this.gson = new Gson();
    }

    @Override
    public CompletableFuture<Void> init() {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void shutdown() {
        // Datasource lifecycle is managed by AbstractSqlRepository
    }

    @Override
    public CompletableFuture<Optional<GuildMissionData>> findByGuildId(UUID guildId) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT * FROM guild_missions WHERE guild_id = ?")) {
                ps.setString(1, guildId.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(readMissionData(rs));
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to find mission data for " + guildId, e);
            }
            return Optional.empty();
        });
    }

    @Override
    public CompletableFuture<List<GuildMissionData>> findAll() {
        return CompletableFuture.supplyAsync(() -> {
            List<GuildMissionData> list = new ArrayList<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT * FROM guild_missions");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(readMissionData(rs));
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to find all mission data", e);
            }
            return list;
        });
    }

    @Override
    public CompletableFuture<Void> save(GuildMissionData data) {
        return CompletableFuture.runAsync(() -> {
            String suffix = isMysql
                    ? "ON DUPLICATE KEY UPDATE has_active=VALUES(has_active), started_at=VALUES(started_at), expires_at=VALUES(expires_at), last_started_at=VALUES(last_started_at), last_completed_at=VALUES(last_completed_at), tasks_data=VALUES(tasks_data)"
                    : "ON CONFLICT(guild_id) DO UPDATE SET has_active=excluded.has_active, started_at=excluded.started_at, expires_at=excluded.expires_at, last_started_at=excluded.last_started_at, last_completed_at=excluded.last_completed_at, tasks_data=excluded.tasks_data";

            String sql = "INSERT INTO guild_missions (guild_id, has_active, started_at, expires_at, last_started_at, last_completed_at, tasks_data) VALUES (?, ?, ?, ?, ?, ?, ?) " + suffix;

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, data.getGuildId().toString());
                ps.setBoolean(2, data.hasActiveMission());

                if (data.hasActiveMission()) {
                    ActiveMission mission = data.getActiveMission().get();
                    ps.setString(3, mission.getStartedAt().toString());
                    ps.setString(4, mission.getExpiresAt().toString());
                    ps.setString(7, serializeTasks(mission.getTasks().values()));
                } else {
                    ps.setNull(3, Types.VARCHAR);
                    ps.setNull(4, Types.VARCHAR);
                    ps.setNull(7, Types.VARCHAR);
                }

                ps.setString(5, data.getLastStartedAt().map(Instant::toString).orElse(null));
                ps.setString(6, data.getLastCompletedAt().map(Instant::toString).orElse(null));

                ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to save mission data for " + data.getGuildId(), e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> delete(UUID guildId) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("DELETE FROM guild_missions WHERE guild_id = ?")) {
                ps.setString(1, guildId.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to delete mission data for " + guildId, e);
            }
        });
    }

    private GuildMissionData readMissionData(ResultSet rs) throws SQLException {
        UUID guildId = UUID.fromString(rs.getString("guild_id"));
        boolean hasActive = rs.getBoolean("has_active");
        String startedStr = rs.getString("started_at");
        String expiresStr = rs.getString("expires_at");
        String lastStartedStr = rs.getString("last_started_at");
        String lastCompletedStr = rs.getString("last_completed_at");
        String tasksJson = rs.getString("tasks_data");

        Instant lastStarted = lastStartedStr != null ? Instant.parse(lastStartedStr) : null;
        Instant lastCompleted = lastCompletedStr != null ? Instant.parse(lastCompletedStr) : null;

        ActiveMission activeMission = null;
        if (hasActive && startedStr != null && expiresStr != null) {
            Instant startedAt = Instant.parse(startedStr);
            Instant expiresAt = Instant.parse(expiresStr);
            List<MissionTaskProgress> taskList = deserializeTasks(tasksJson);
            activeMission = new ActiveMission(guildId, startedAt, expiresAt, taskList);
        }

        return new GuildMissionData(guildId, activeMission, lastStarted, lastCompleted);
    }

    private String serializeTasks(Collection<MissionTaskProgress> tasks) {
        JsonArray arr = new JsonArray();
        for (MissionTaskProgress t : tasks) {
            JsonObject obj = new JsonObject();
            obj.addProperty("taskId", t.getTaskId());
            obj.addProperty("current", t.getCurrent());
            obj.addProperty("target", t.getTarget());
            JsonObject cObj = new JsonObject();
            t.getContributions().forEach((k, v) -> cObj.addProperty(k.toString(), v));
            obj.add("contributions", cObj);
            arr.add(obj);
        }
        return gson.toJson(arr);
    }

    private List<MissionTaskProgress> deserializeTasks(String json) {
        if (json == null || json.isBlank()) return List.of();
        List<MissionTaskProgress> list = new ArrayList<>();
        try {
            JsonArray arr = JsonParser.parseString(json).getAsJsonArray();
            for (JsonElement el : arr) {
                if (!el.isJsonObject()) continue;
                JsonObject obj = el.getAsJsonObject();
                String taskId = obj.get("taskId").getAsString();
                long current = obj.get("current").getAsLong();
                long target = obj.get("target").getAsLong();
                Map<UUID, Long> contribs = new HashMap<>();
                if (obj.has("contributions") && obj.get("contributions").isJsonObject()) {
                    JsonObject cObj = obj.getAsJsonObject("contributions");
                    for (Map.Entry<String, JsonElement> entry : cObj.entrySet()) {
                        try {
                            contribs.put(UUID.fromString(entry.getKey()), entry.getValue().getAsLong());
                        } catch (Exception ignored) {}
                    }
                }
                list.add(new MissionTaskProgress(taskId, current, target, contribs));
            }
        } catch (Exception ignored) {}
        return list;
    }
}
