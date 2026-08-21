package me.usainsrht.guildroyale.core.storage.json;

import com.google.gson.*;
import me.usainsrht.guildroyale.api.domain.mission.ActiveMission;
import me.usainsrht.guildroyale.api.domain.mission.GuildMissionData;
import me.usainsrht.guildroyale.api.domain.mission.MissionTaskProgress;
import me.usainsrht.guildroyale.api.storage.MissionRepository;
import me.usainsrht.guildroyale.core.scheduler.FoliaScheduler;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JSON file-based implementation of {@link MissionRepository}.
 * Files stored in {@code plugins/GuildRoyale/data/missions/<guildId>.json}.
 */
public final class JsonMissionRepository implements MissionRepository {

    private final File dataDir;
    private final FoliaScheduler scheduler;
    private final Gson gson;
    private final ConcurrentHashMap<UUID, GuildMissionData> cache = new ConcurrentHashMap<>();

    public JsonMissionRepository(File dataDir, FoliaScheduler scheduler) {
        this.dataDir = dataDir;
        this.scheduler = scheduler;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    @Override
    public CompletableFuture<Void> init() {
        Runnable task = () -> {
            dataDir.mkdirs();
            File[] files = dataDir.listFiles((d, n) -> n.endsWith(".json"));
            if (files == null) return;
            for (File f : files) {
                try {
                    GuildMissionData data = readFile(f);
                    if (data != null) {
                        cache.put(data.getGuildId(), data);
                    }
                } catch (Exception ignored) {
                }
            }
        };
        if (scheduler != null) {
            return CompletableFuture.runAsync(task);
        } else {
            task.run();
            return CompletableFuture.completedFuture(null);
        }
    }

    @Override
    public void shutdown() {
        // No open resources
    }

    @Override
    public CompletableFuture<Optional<GuildMissionData>> findByGuildId(UUID guildId) {
        if (guildId == null) return CompletableFuture.completedFuture(Optional.empty());
        return CompletableFuture.supplyAsync(() -> Optional.ofNullable(cache.get(guildId)));
    }

    @Override
    public CompletableFuture<List<GuildMissionData>> findAll() {
        return CompletableFuture.supplyAsync(() -> List.copyOf(cache.values()));
    }

    @Override
    public CompletableFuture<Void> save(GuildMissionData data) {
        if (data == null) return CompletableFuture.completedFuture(null);
        cache.put(data.getGuildId(), data);
        Runnable writeTask = () -> {
            File file = fileFor(data.getGuildId());
            try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
                gson.toJson(toJson(data), w);
            } catch (IOException e) {
                throw new RuntimeException("Failed to save mission data for " + data.getGuildId(), e);
            }
        };
        if (scheduler != null) {
            return CompletableFuture.runAsync(writeTask);
        } else {
            writeTask.run();
            return CompletableFuture.completedFuture(null);
        }
    }

    @Override
    public CompletableFuture<Void> delete(UUID guildId) {
        if (guildId == null) return CompletableFuture.completedFuture(null);
        cache.remove(guildId);
        Runnable delTask = () -> {
            File file = fileFor(guildId);
            if (file.exists()) file.delete();
        };
        if (scheduler != null) {
            return CompletableFuture.runAsync(delTask);
        } else {
            delTask.run();
            return CompletableFuture.completedFuture(null);
        }
    }

    private File fileFor(UUID id) {
        return new File(dataDir, id + ".json");
    }

    private GuildMissionData readFile(File file) throws IOException {
        try (Reader r = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            JsonObject obj = gson.fromJson(r, JsonObject.class);
            return fromJson(obj);
        }
    }

    public JsonObject toJson(GuildMissionData data) {
        JsonObject obj = new JsonObject();
        obj.addProperty("guildId", data.getGuildId().toString());
        data.getLastStartedAt().ifPresent(t -> obj.addProperty("lastStartedAt", t.toString()));
        data.getLastCompletedAt().ifPresent(t -> obj.addProperty("lastCompletedAt", t.toString()));

        if (data.hasActiveMission()) {
            ActiveMission mission = data.getActiveMission().get();
            JsonObject mObj = new JsonObject();
            mObj.addProperty("startedAt", mission.getStartedAt().toString());
            mObj.addProperty("expiresAt", mission.getExpiresAt().toString());

            JsonArray tasksArr = new JsonArray();
            for (MissionTaskProgress t : mission.getTasks().values()) {
                JsonObject tObj = new JsonObject();
                tObj.addProperty("taskId", t.getTaskId());
                tObj.addProperty("current", t.getCurrent());
                tObj.addProperty("target", t.getTarget());

                JsonObject cObj = new JsonObject();
                t.getContributions().forEach((k, v) -> cObj.addProperty(k.toString(), v));
                tObj.add("contributions", cObj);

                tasksArr.add(tObj);
            }
            mObj.add("tasks", tasksArr);
            obj.add("activeMission", mObj);
        }
        return obj;
    }

    public GuildMissionData fromJson(JsonObject obj) {
        UUID guildId = UUID.fromString(obj.get("guildId").getAsString());
        Instant lastStarted = obj.has("lastStartedAt") && !obj.get("lastStartedAt").isJsonNull()
                ? Instant.parse(obj.get("lastStartedAt").getAsString()) : null;
        Instant lastCompleted = obj.has("lastCompletedAt") && !obj.get("lastCompletedAt").isJsonNull()
                ? Instant.parse(obj.get("lastCompletedAt").getAsString()) : null;

        ActiveMission activeMission = null;
        if (obj.has("activeMission") && obj.get("activeMission").isJsonObject()) {
            JsonObject mObj = obj.getAsJsonObject("activeMission");
            Instant startedAt = Instant.parse(mObj.get("startedAt").getAsString());
            Instant expiresAt = Instant.parse(mObj.get("expiresAt").getAsString());

            List<MissionTaskProgress> tasks = new ArrayList<>();
            if (mObj.has("tasks") && mObj.get("tasks").isJsonArray()) {
                JsonArray tasksArr = mObj.getAsJsonArray("tasks");
                for (JsonElement el : tasksArr) {
                    if (!el.isJsonObject()) continue;
                    JsonObject tObj = el.getAsJsonObject();
                    String taskId = tObj.get("taskId").getAsString();
                    long current = tObj.get("current").getAsLong();
                    long target = tObj.get("target").getAsLong();

                    Map<UUID, Long> contribs = new HashMap<>();
                    if (tObj.has("contributions") && tObj.get("contributions").isJsonObject()) {
                        JsonObject cObj = tObj.getAsJsonObject("contributions");
                        for (Map.Entry<String, JsonElement> entry : cObj.entrySet()) {
                            try {
                                contribs.put(UUID.fromString(entry.getKey()), entry.getValue().getAsLong());
                            } catch (Exception ignored) {}
                        }
                    }
                    tasks.add(new MissionTaskProgress(taskId, current, target, contribs));
                }
            }
            activeMission = new ActiveMission(guildId, startedAt, expiresAt, tasks);
        }

        return new GuildMissionData(guildId, activeMission, lastStarted, lastCompleted);
    }
}
