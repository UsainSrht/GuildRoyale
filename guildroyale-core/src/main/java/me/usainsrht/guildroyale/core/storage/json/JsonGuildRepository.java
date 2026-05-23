package me.usainsrht.guildroyale.core.storage.json;

import com.google.gson.*;
import me.usainsrht.guildroyale.api.domain.Guild;
import me.usainsrht.guildroyale.api.domain.GuildMember;
import me.usainsrht.guildroyale.api.domain.GuildRole;
import me.usainsrht.guildroyale.api.domain.SerializableItemStack;
import me.usainsrht.guildroyale.api.permission.GuildPermissionKey;
import me.usainsrht.guildroyale.api.storage.GuildRepository;
import me.usainsrht.guildroyale.core.scheduler.FoliaScheduler;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JSON file-based {@link GuildRepository}. One {@code <guildId>.json} per guild,
 * stored in the {@code plugins/GuildRoyale/data/guilds/} directory.
 *
 * <p>All blocking I/O is dispatched through {@link FoliaScheduler#runAsync(Runnable)}.
 * An in-memory write-through cache keeps reads fast.
 */
public final class JsonGuildRepository implements GuildRepository {

    private final File dataDir;
    private final FoliaScheduler scheduler;
    private final Gson gson;
    private final ConcurrentHashMap<UUID, Guild> cache = new ConcurrentHashMap<>();

    public JsonGuildRepository(File dataDir, FoliaScheduler scheduler) {
        this.dataDir = dataDir;
        this.scheduler = scheduler;
        this.gson = buildGson();
    }

    // ── Init / shutdown ────────────────────────────────────────────────────────

    @Override
    public CompletableFuture<Void> init() {
        return CompletableFuture.runAsync(() -> {
            dataDir.mkdirs();
            // Pre-load all guilds into cache
            File[] files = dataDir.listFiles((d, n) -> n.endsWith(".json"));
            if (files == null) return;
            for (File file : files) {
                try {
                    Guild guild = readFile(file);
                    if (guild != null) cache.put(guild.getId(), guild);
                } catch (Exception e) {
                    // Skip corrupt files
                }
            }
        });
    }

    @Override
    public void shutdown() {
        // Nothing to close for JSON files
    }

    // ── Queries ────────────────────────────────────────────────────────────────

    @Override
    public CompletableFuture<Optional<Guild>> findById(UUID id) {
        return CompletableFuture.supplyAsync(() -> Optional.ofNullable(cache.get(id)));
    }

    @Override
    public CompletableFuture<Optional<Guild>> findByMember(UUID playerId) {
        return CompletableFuture.supplyAsync(() ->
                cache.values().stream()
                        .filter(g -> g.hasMember(playerId))
                        .findFirst()
        );
    }

    @Override
    public CompletableFuture<Optional<Guild>> findByName(String name) {
        return CompletableFuture.supplyAsync(() ->
                cache.values().stream()
                        .filter(g -> g.getName().equalsIgnoreCase(name))
                        .findFirst()
        );
    }

    @Override
    public CompletableFuture<List<Guild>> findAll() {
        return CompletableFuture.supplyAsync(() -> List.copyOf(cache.values()));
    }

    @Override
    public CompletableFuture<List<Guild>> getLeaderboard(int limit) {
        return CompletableFuture.supplyAsync(() ->
                cache.values().stream()
                        .sorted(Comparator.comparingInt(Guild::getLevel).reversed()
                                .thenComparingLong(Guild::getXp).reversed())
                        .limit(limit)
                        .toList()
        );
    }

    @Override
    public CompletableFuture<Boolean> existsByName(String name) {
        return CompletableFuture.supplyAsync(() ->
                cache.values().stream().anyMatch(g -> g.getName().equalsIgnoreCase(name))
        );
    }

    @Override
    public CompletableFuture<Boolean> isPlayerInAnyGuild(UUID playerId) {
        return CompletableFuture.supplyAsync(() ->
                cache.values().stream().anyMatch(g -> g.hasMember(playerId))
        );
    }

    // ── Mutations ──────────────────────────────────────────────────────────────

    @Override
    public CompletableFuture<Void> save(Guild guild) {
        cache.put(guild.getId(), guild);
        return CompletableFuture.runAsync(() -> {
            File file = fileFor(guild.getId());
            try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
                gson.toJson(toJson(guild), w);
            } catch (IOException e) {
                throw new RuntimeException("Failed to save guild " + guild.getId(), e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> delete(UUID id) {
        cache.remove(id);
        return CompletableFuture.runAsync(() -> {
            File file = fileFor(id);
            if (file.exists()) file.delete();
        });
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private File fileFor(UUID id) { return new File(dataDir, id + ".json"); }

    private Guild readFile(File file) throws IOException {
        try (Reader r = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            JsonObject obj = gson.fromJson(r, JsonObject.class);
            return fromJson(obj);
        }
    }

    private JsonObject toJson(Guild g) {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", g.getId().toString());
        obj.addProperty("name", g.getName());
        obj.addProperty("shortname", g.getShortname());
        obj.addProperty("level", g.getLevel());
        obj.addProperty("xp", g.getXp());
        obj.addProperty("createdAt", g.getCreatedAt().toString());

        // Icon
        obj.add("icon", serializeIcon(g.getIcon()));

        // Members
        JsonArray members = new JsonArray();
        for (GuildMember m : g.getMembers()) {
            JsonObject mo = new JsonObject();
            mo.addProperty("playerId", m.getPlayerId().toString());
            mo.addProperty("roleIndex", m.getRole().getIndex());
            mo.addProperty("joinedAt", m.getJoinedAt().toString());
            mo.addProperty("contribution", m.getContribution());
            members.add(mo);
        }
        obj.add("members", members);

        // Roles
        JsonArray roles = new JsonArray();
        for (GuildRole r : g.getRoles()) {
            JsonObject ro = new JsonObject();
            ro.addProperty("name", r.getName());
            ro.addProperty("index", r.getIndex());
            ro.add("icon", serializeIcon(r.getIcon()));
            JsonArray perms = new JsonArray();
            r.getPermissions().forEach(p -> perms.add(p.name()));
            ro.add("permissions", perms);
            roles.add(ro);
        }
        obj.add("roles", roles);
        return obj;
    }

    private Guild fromJson(JsonObject obj) {
        UUID id = UUID.fromString(obj.get("id").getAsString());
        String name = obj.get("name").getAsString();
        String shortname = obj.get("shortname").getAsString();
        int level = obj.get("level").getAsInt();
        long xp = obj.get("xp").getAsLong();
        Instant createdAt = Instant.parse(obj.get("createdAt").getAsString());
        SerializableItemStack icon = deserializeIcon(obj.getAsJsonObject("icon"));

        // Roles first (needed by member reconstruction)
        List<GuildRole> roles = new ArrayList<>();
        for (JsonElement el : obj.getAsJsonArray("roles")) {
            JsonObject ro = el.getAsJsonObject();
            String rName = ro.get("name").getAsString();
            int rIndex = ro.get("index").getAsInt();
            SerializableItemStack rIcon = deserializeIcon(ro.getAsJsonObject("icon"));
            Set<GuildPermissionKey> perms = new HashSet<>();
            for (JsonElement pe : ro.getAsJsonArray("permissions")) {
                try { perms.add(GuildPermissionKey.valueOf(pe.getAsString())); } catch (Exception ignored) {}
            }
            roles.add(new GuildRole(rName, rIndex, perms, rIcon));
        }

        Map<Integer, GuildRole> roleByIndex = new HashMap<>();
        roles.forEach(r -> roleByIndex.put(r.getIndex(), r));

        // Members
        List<GuildMember> members = new ArrayList<>();
        for (JsonElement el : obj.getAsJsonArray("members")) {
            JsonObject mo = el.getAsJsonObject();
            UUID pid = UUID.fromString(mo.get("playerId").getAsString());
            int rIndex = mo.get("roleIndex").getAsInt();
            Instant joinedAt = Instant.parse(mo.get("joinedAt").getAsString());
            long contribution = mo.get("contribution").getAsLong();
            GuildRole role = roleByIndex.getOrDefault(rIndex, roles.stream()
                    .max(Comparator.comparingInt(GuildRole::getIndex)).orElseThrow());
            members.add(new GuildMember(pid, role, joinedAt, contribution));
        }

        return new Guild(id, name, shortname, icon, level, xp, members, roles, createdAt);
    }

    private JsonObject serializeIcon(SerializableItemStack icon) {
        JsonObject o = new JsonObject();
        if (icon == null || icon.isEmpty()) return o;
        if (icon.getMaterial() != null) o.addProperty("material", icon.getMaterial());
        if (icon.getRawData() != null) o.addProperty("rawData", Base64.getEncoder().encodeToString(icon.getRawData()));
        return o;
    }

    private SerializableItemStack deserializeIcon(JsonObject o) {
        if (o == null || o.size() == 0) return SerializableItemStack.EMPTY;
        String material = o.has("material") ? o.get("material").getAsString() : null;
        byte[] rawData = null;
        if (o.has("rawData")) {
            rawData = Base64.getDecoder().decode(o.get("rawData").getAsString());
        }
        return new SerializableItemStack(material, rawData);
    }

    private static Gson buildGson() {
        return new GsonBuilder().setPrettyPrinting().create();
    }
}
