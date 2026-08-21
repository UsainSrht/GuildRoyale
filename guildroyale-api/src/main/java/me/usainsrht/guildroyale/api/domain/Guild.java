package me.usainsrht.guildroyale.api.domain;

import java.time.Instant;
import java.util.*;

/**
 * Represents a Guild — the central domain object.
 * This class is pure Java with no Minecraft API dependencies.
 */
public final class Guild {

    private final UUID id;
    private String name;
    private String shortname;
    private SerializableItemStack icon;
    private int level;
    private long xp;
    private final List<GuildMember> members;
    private final List<GuildRole> roles;
    private final Instant createdAt;
    private final Set<String> ownedBadges;
    private String activeBadgeId;
    private final Map<Integer, SerializableItemStack> storage;
    private boolean friendlyFire;
    private boolean glow;
    private final Map<UUID, Long> contributions;

    public Guild(UUID id, String name, String shortname, SerializableItemStack icon,
                 int level, long xp, List<GuildMember> members, List<GuildRole> roles,
                 Instant createdAt) {
        this(id, name, shortname, icon, level, xp, members, roles, createdAt,
                Set.of(), null, Map.of(), false, false, Map.of());
    }

    public Guild(UUID id, String name, String shortname, SerializableItemStack icon,
                 int level, long xp, List<GuildMember> members, List<GuildRole> roles,
                 Instant createdAt, Set<String> ownedBadges, String activeBadgeId,
                 Map<Integer, SerializableItemStack> storage) {
        this(id, name, shortname, icon, level, xp, members, roles, createdAt,
                ownedBadges, activeBadgeId, storage, false, false, Map.of());
    }

    public Guild(UUID id, String name, String shortname, SerializableItemStack icon,
                 int level, long xp, List<GuildMember> members, List<GuildRole> roles,
                 Instant createdAt, Set<String> ownedBadges, String activeBadgeId,
                 Map<Integer, SerializableItemStack> storage, boolean friendlyFire) {
        this(id, name, shortname, icon, level, xp, members, roles, createdAt,
                ownedBadges, activeBadgeId, storage, friendlyFire, false, Map.of());
    }

    public Guild(UUID id, String name, String shortname, SerializableItemStack icon,
                 int level, long xp, List<GuildMember> members, List<GuildRole> roles,
                 Instant createdAt, Set<String> ownedBadges, String activeBadgeId,
                 Map<Integer, SerializableItemStack> storage, boolean friendlyFire,
                 Map<UUID, Long> contributions) {
        this(id, name, shortname, icon, level, xp, members, roles, createdAt,
                ownedBadges, activeBadgeId, storage, friendlyFire, false, contributions);
    }

    public Guild(UUID id, String name, String shortname, SerializableItemStack icon,
                 int level, long xp, List<GuildMember> members, List<GuildRole> roles,
                 Instant createdAt, Set<String> ownedBadges, String activeBadgeId,
                 Map<Integer, SerializableItemStack> storage, boolean friendlyFire,
                 boolean glow, Map<UUID, Long> contributions) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.shortname = Objects.requireNonNull(shortname, "shortname");
        this.icon = icon != null ? icon : SerializableItemStack.EMPTY;
        if (level < GuildLevel.MIN_LEVEL) {
            throw new IllegalArgumentException("Level must be at least " + GuildLevel.MIN_LEVEL);
        }
        this.level = level;
        this.xp = xp;
        this.members = new ArrayList<>(Objects.requireNonNull(members, "members"));
        this.roles = new ArrayList<>(Objects.requireNonNull(roles, "roles"));
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.ownedBadges = new LinkedHashSet<>(ownedBadges != null ? ownedBadges : Set.of());
        this.activeBadgeId = activeBadgeId;
        this.storage = new TreeMap<>();
        if (storage != null) {
            storage.forEach((slot, item) -> {
                if (item != null && !item.isEmpty()) {
                    this.storage.put(slot, item);
                }
            });
        }
        this.friendlyFire = friendlyFire;
        this.glow = glow;
        this.contributions = new HashMap<>();
        if (contributions != null) {
            contributions.forEach((pid, val) -> {
                if (pid != null && val != null && val > 0) {
                    this.contributions.put(pid, val);
                }
            });
        }
        for (GuildMember m : this.members) {
            if (m.getContribution() > 0 && !this.contributions.containsKey(m.getPlayerId())) {
                this.contributions.put(m.getPlayerId(), m.getContribution());
            } else if (this.contributions.containsKey(m.getPlayerId())) {
                m.setContribution(this.contributions.get(m.getPlayerId()));
            }
        }
    }

    // ── Identity ────────────────────────────────────────────────────────────

    public UUID getId() { return id; }

    // ── Mutable properties ───────────────────────────────────────────────────

    public String getName() { return name; }
    public void setName(String name) { this.name = Objects.requireNonNull(name); }

    public String getShortname() { return shortname; }
    public void setShortname(String shortname) { this.shortname = Objects.requireNonNull(shortname); }

    public SerializableItemStack getIcon() { return icon; }
    public void setIcon(SerializableItemStack icon) { this.icon = icon != null ? icon : SerializableItemStack.EMPTY; }

    public int getLevel() { return level; }
    public void setLevel(int level) {
        if (level < GuildLevel.MIN_LEVEL) {
            throw new IllegalArgumentException("Level must be at least " + GuildLevel.MIN_LEVEL);
        }
        this.level = level;
    }

    public long getXp() { return xp; }
    public void setXp(long xp) { this.xp = xp; }
    public void addXp(long amount) { this.xp = this.xp + amount; }

    public Instant getCreatedAt() { return createdAt; }

    public boolean isFriendlyFire() { return friendlyFire; }
    public void setFriendlyFire(boolean friendlyFire) { this.friendlyFire = friendlyFire; }

    public boolean isGlow() { return glow; }
    public void setGlow(boolean glow) { this.glow = glow; }

    // ── Badges ───────────────────────────────────────────────────────────────

    public Set<String> getOwnedBadges() { return Collections.unmodifiableSet(ownedBadges); }

    public boolean ownsBadge(String badgeId) {
        return ownedBadges.contains(badgeId);
    }

    public void addBadge(String badgeId) {
        ownedBadges.add(Objects.requireNonNull(badgeId));
    }

    public boolean removeBadge(String badgeId) {
        boolean removed = ownedBadges.remove(badgeId);
        if (removed && badgeId.equals(activeBadgeId)) {
            activeBadgeId = null;
        }
        return removed;
    }

    public String getActiveBadgeId() { return activeBadgeId; }

    public void setActiveBadgeId(String activeBadgeId) {
        if (activeBadgeId != null && !ownedBadges.contains(activeBadgeId)) {
            throw new IllegalArgumentException("Guild does not own badge: " + activeBadgeId);
        }
        this.activeBadgeId = activeBadgeId;
    }

    // ── Storage ──────────────────────────────────────────────────────────────

    public Map<Integer, SerializableItemStack> getStorage() {
        return Collections.unmodifiableMap(storage);
    }

    public void setStorageContents(Map<Integer, SerializableItemStack> contents) {
        storage.clear();
        if (contents == null) return;
        contents.forEach((slot, item) -> {
            if (item != null && !item.isEmpty()) {
                storage.put(slot, item);
            }
        });
    }

    // ── Members ──────────────────────────────────────────────────────────────

    public List<GuildMember> getMembers() { return Collections.unmodifiableList(members); }

    public Optional<GuildMember> getMember(UUID playerId) {
        return members.stream().filter(m -> m.getPlayerId().equals(playerId)).findFirst();
    }

    public boolean hasMember(UUID playerId) {
        return members.stream().anyMatch(m -> m.getPlayerId().equals(playerId));
    }

    public void addMember(GuildMember member) {
        Objects.requireNonNull(member, "member");
        members.add(member);
    }

    public boolean removeMember(UUID playerId) {
        return members.removeIf(m -> m.getPlayerId().equals(playerId));
    }

    public int getMemberCount() { return members.size(); }

    // ── Contributions ────────────────────────────────────────────────────────

    public Map<UUID, Long> getContributions() {
        return Collections.unmodifiableMap(contributions);
    }

    public long getContribution(UUID playerId) {
        if (playerId == null) return 0L;
        return contributions.getOrDefault(playerId, 0L);
    }

    public void addContribution(UUID playerId, long amount) {
        if (playerId == null || amount <= 0) return;
        long newTotal = contributions.merge(playerId, amount, Long::sum);
        getMember(playerId).ifPresent(m -> m.setContribution(newTotal));
    }

    public void setContribution(UUID playerId, long amount) {
        if (playerId == null) return;
        long val = Math.max(0, amount);
        if (val == 0) {
            contributions.remove(playerId);
        } else {
            contributions.put(playerId, val);
        }
        getMember(playerId).ifPresent(m -> m.setContribution(val));
    }

    // ── Roles ────────────────────────────────────────────────────────────────

    public List<GuildRole> getRoles() { return Collections.unmodifiableList(roles); }

    public Optional<GuildRole> getRole(int index) {
        return roles.stream().filter(r -> r.getIndex() == index).findFirst();
    }

    public GuildRole getLeaderRole() {
        return getRole(0).orElseThrow(() -> new IllegalStateException("Guild has no leader role"));
    }

    public GuildRole getDefaultRole() {
        return roles.stream()
                .max(Comparator.comparingInt(GuildRole::getIndex))
                .orElseThrow(() -> new IllegalStateException("Guild has no roles"));
    }

    public void addRole(GuildRole role) {
        Objects.requireNonNull(role, "role");
        roles.add(role);
    }

    public boolean removeRole(int index) {
        if (index == 0) throw new IllegalArgumentException("Cannot remove leader role");
        return roles.removeIf(r -> r.getIndex() == index);
    }

    // ── Utility ──────────────────────────────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Guild g)) return false;
        return id.equals(g.id);
    }

    @Override
    public int hashCode() { return id.hashCode(); }

    @Override
    public String toString() {
        return "Guild{id=" + id + ", name='" + name + "', level=" + level + '}';
    }
}
