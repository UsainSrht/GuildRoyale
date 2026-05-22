package dev.guildroyale.api.domain;

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

    public Guild(UUID id, String name, String shortname, SerializableItemStack icon,
                 int level, long xp, List<GuildMember> members, List<GuildRole> roles,
                 Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.shortname = Objects.requireNonNull(shortname, "shortname");
        this.icon = icon != null ? icon : SerializableItemStack.EMPTY;
        this.level = Math.max(GuildLevel.MIN_LEVEL, Math.min(GuildLevel.MAX_LEVEL, level));
        this.xp = Math.max(0, xp);
        this.members = new ArrayList<>(Objects.requireNonNull(members, "members"));
        this.roles = new ArrayList<>(Objects.requireNonNull(roles, "roles"));
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
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
        if (level < GuildLevel.MIN_LEVEL || level > GuildLevel.MAX_LEVEL) {
            throw new IllegalArgumentException("Level must be between " + GuildLevel.MIN_LEVEL + " and " + GuildLevel.MAX_LEVEL);
        }
        this.level = level;
    }

    public long getXp() { return xp; }
    public void setXp(long xp) { this.xp = Math.max(0, xp); }
    public void addXp(long amount) { this.xp = Math.max(0, this.xp + amount); }

    public Instant getCreatedAt() { return createdAt; }

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
