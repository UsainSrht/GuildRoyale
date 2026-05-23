package me.usainsrht.guildroyale.api.domain;

import me.usainsrht.guildroyale.api.permission.GuildPermissionKey;

import java.util.*;

/**
 * Represents a role within a {@link Guild}.
 *
 * <p>Role hierarchy is determined by the {@link #index}:
 * <ul>
 *   <li>Index 0 — Leader (unique per guild, all permissions)</li>
 *   <li>Index 1 — Co-Leader</li>
 *   <li>Index 2 — Helper</li>
 *   <li>Index 3 — Member (default for new joins, highest index)</li>
 * </ul>
 * A user may only perform permission-gated actions against members with a
 * <em>strictly higher</em> index (lower rank).
 */
public final class GuildRole {

    private String name;
    private final int index;
    private final Set<GuildPermissionKey> permissions;
    private SerializableItemStack icon;

    public GuildRole(String name, int index, Set<GuildPermissionKey> permissions, SerializableItemStack icon) {
        this.name = Objects.requireNonNull(name, "name");
        if (index < 0) throw new IllegalArgumentException("Role index must be >= 0");
        this.index = index;
        this.permissions = new HashSet<>(Objects.requireNonNull(permissions, "permissions"));
        this.icon = icon != null ? icon : SerializableItemStack.EMPTY;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = Objects.requireNonNull(name); }

    public int getIndex() { return index; }

    public Set<GuildPermissionKey> getPermissions() { return Collections.unmodifiableSet(permissions); }

    public boolean hasPermission(GuildPermissionKey key) {
        return permissions.contains(Objects.requireNonNull(key));
    }

    public void addPermission(GuildPermissionKey key) { permissions.add(Objects.requireNonNull(key)); }
    public void removePermission(GuildPermissionKey key) { permissions.remove(Objects.requireNonNull(key)); }
    public void setPermissions(Set<GuildPermissionKey> keys) {
        permissions.clear();
        permissions.addAll(Objects.requireNonNull(keys));
    }

    public SerializableItemStack getIcon() { return icon; }
    public void setIcon(SerializableItemStack icon) { this.icon = icon != null ? icon : SerializableItemStack.EMPTY; }

    /** Default Leader role with all permissions. */
    public static GuildRole createLeader() {
        return new GuildRole("Leader", 0, EnumSet.allOf(GuildPermissionKey.class), SerializableItemStack.EMPTY);
    }

    /** Default Co-Leader role. */
    public static GuildRole createCoLeader() {
        return new GuildRole("Co-Leader", 1, EnumSet.of(
                GuildPermissionKey.MEMBER_MANAGEMENT,
                GuildPermissionKey.INVITE,
                GuildPermissionKey.KICK,
                GuildPermissionKey.GUILD_SETTINGS,
                GuildPermissionKey.ICON_CHANGE
        ), SerializableItemStack.EMPTY);
    }

    /** Default Helper role. */
    public static GuildRole createHelper() {
        return new GuildRole("Helper", 2, EnumSet.of(GuildPermissionKey.INVITE), SerializableItemStack.EMPTY);
    }

    /** Default Member role with no special permissions. */
    public static GuildRole createMember() {
        return new GuildRole("Member", 3, EnumSet.noneOf(GuildPermissionKey.class), SerializableItemStack.EMPTY);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GuildRole r)) return false;
        return index == r.index && name.equals(r.name);
    }

    @Override
    public int hashCode() { return Objects.hash(name, index); }

    @Override
    public String toString() { return "GuildRole{name='" + name + "', index=" + index + '}'; }
}
