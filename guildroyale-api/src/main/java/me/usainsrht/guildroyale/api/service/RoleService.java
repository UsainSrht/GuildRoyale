package me.usainsrht.guildroyale.api.service;

import me.usainsrht.guildroyale.api.domain.SerializableItemStack;
import me.usainsrht.guildroyale.api.permission.GuildPermissionKey;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Manages in-guild roles: creation, deletion, rename, icon, and permission assignment.
 */
public interface RoleService {

    /**
     * Creates a new role with the given name at the next available index.
     * Requester must have {@code ROLE_MANAGEMENT}.
     */
    CompletableFuture<ActionResult> createRole(UUID guildId, UUID requesterId, String roleName);

    /**
     * Deletes a role by index. Index 0 (Leader) cannot be deleted.
     * Members holding the deleted role are moved to the default (highest-index) role.
     * Requester must have {@code ROLE_MANAGEMENT}.
     */
    CompletableFuture<ActionResult> deleteRole(UUID guildId, UUID requesterId, int roleIndex);

    /**
     * Renames an existing role.
     * Requester must have {@code ROLE_MANAGEMENT}.
     */
    CompletableFuture<ActionResult> renameRole(UUID guildId, UUID requesterId, int roleIndex, String newName);

    /**
     * Sets the icon of a role.
     * Requester must have {@code ROLE_MANAGEMENT}.
     */
    CompletableFuture<ActionResult> setRoleIcon(UUID guildId, UUID requesterId, int roleIndex, SerializableItemStack icon);

    /**
     * Replaces the full permission set of a role.
     * Requester must have {@code ROLE_MANAGEMENT}.
     * The Leader role (index 0) always retains all permissions regardless.
     */
    CompletableFuture<ActionResult> setRolePermissions(UUID guildId, UUID requesterId, int roleIndex, Set<GuildPermissionKey> permissions);

    /**
     * Toggles a single permission on a role.
     * Requester must have {@code ROLE_MANAGEMENT}.
     */
    CompletableFuture<ActionResult> togglePermission(UUID guildId, UUID requesterId, int roleIndex, GuildPermissionKey key);
}
