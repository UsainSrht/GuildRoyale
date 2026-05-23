package me.usainsrht.guildroyale.core.service;

import me.usainsrht.guildroyale.api.domain.Guild;
import me.usainsrht.guildroyale.api.domain.GuildMember;
import me.usainsrht.guildroyale.api.domain.GuildRole;
import me.usainsrht.guildroyale.api.domain.SerializableItemStack;
import me.usainsrht.guildroyale.api.permission.GuildPermissionKey;
import me.usainsrht.guildroyale.api.service.ActionResult;
import me.usainsrht.guildroyale.api.service.RoleService;
import me.usainsrht.guildroyale.api.storage.GuildRepository;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * Manages guild roles — creation, deletion, rename, icon, and permissions.
 */
public final class RoleServiceImpl implements RoleService {

    private static final Pattern ROLE_NAME_PATTERN = Pattern.compile("^.{2,20}$");
    private static final int MAX_ROLES = 10;

    private final GuildRepository repo;
    private final PermissionEvaluatorImpl evaluator = new PermissionEvaluatorImpl();

    public RoleServiceImpl(GuildRepository repo) {
        this.repo = repo;
    }

    @Override
    public CompletableFuture<ActionResult> createRole(UUID guildId, UUID requesterId, String roleName) {
        if (!ROLE_NAME_PATTERN.matcher(roleName).matches()) {
            return done(ActionResult.failure("role-name-invalid"));
        }
        return requireGuildAndPermission(guildId, requesterId, GuildPermissionKey.ROLE_MANAGEMENT, guild -> {
            if (guild.getRoles().size() >= MAX_ROLES) return ActionResult.failure("role-max-reached");
            int nextIndex = guild.getRoles().stream()
                    .mapToInt(GuildRole::getIndex).max().orElse(0) + 1;
            GuildRole newRole = new GuildRole(roleName, nextIndex, EnumSet.noneOf(GuildPermissionKey.class),
                    SerializableItemStack.EMPTY);
            guild.addRole(newRole);
            return null; // save triggered by caller
        });
    }

    @Override
    public CompletableFuture<ActionResult> deleteRole(UUID guildId, UUID requesterId, int roleIndex) {
        if (roleIndex == 0) return done(ActionResult.failure("role-leader-protected"));
        return requireGuildAndPermission(guildId, requesterId, GuildPermissionKey.ROLE_MANAGEMENT, guild -> {
            Optional<GuildRole> roleOpt = guild.getRole(roleIndex);
            if (roleOpt.isEmpty()) return ActionResult.failure("role-not-found");
            GuildRole defaultRole = guild.getDefaultRole();
            // Reassign members holding deleted role
            for (GuildMember m : guild.getMembers()) {
                if (m.getRole().getIndex() == roleIndex) m.setRole(defaultRole);
            }
            guild.removeRole(roleIndex);
            return null;
        });
    }

    @Override
    public CompletableFuture<ActionResult> renameRole(UUID guildId, UUID requesterId, int roleIndex, String newName) {
        if (!ROLE_NAME_PATTERN.matcher(newName).matches()) {
            return done(ActionResult.failure("role-name-invalid"));
        }
        return requireGuildAndPermission(guildId, requesterId, GuildPermissionKey.ROLE_MANAGEMENT, guild -> {
            Optional<GuildRole> roleOpt = guild.getRole(roleIndex);
            if (roleOpt.isEmpty()) return ActionResult.failure("role-not-found");
            roleOpt.get().setName(newName);
            return null;
        });
    }

    @Override
    public CompletableFuture<ActionResult> setRoleIcon(UUID guildId, UUID requesterId, int roleIndex, SerializableItemStack icon) {
        return requireGuildAndPermission(guildId, requesterId, GuildPermissionKey.ROLE_MANAGEMENT, guild -> {
            Optional<GuildRole> roleOpt = guild.getRole(roleIndex);
            if (roleOpt.isEmpty()) return ActionResult.failure("role-not-found");
            roleOpt.get().setIcon(icon);
            return null;
        });
    }

    @Override
    public CompletableFuture<ActionResult> setRolePermissions(UUID guildId, UUID requesterId, int roleIndex, Set<GuildPermissionKey> permissions) {
        if (roleIndex == 0) return done(ActionResult.failure("role-leader-protected"));
        return requireGuildAndPermission(guildId, requesterId, GuildPermissionKey.ROLE_MANAGEMENT, guild -> {
            Optional<GuildRole> roleOpt = guild.getRole(roleIndex);
            if (roleOpt.isEmpty()) return ActionResult.failure("role-not-found");
            roleOpt.get().setPermissions(permissions);
            return null;
        });
    }

    @Override
    public CompletableFuture<ActionResult> togglePermission(UUID guildId, UUID requesterId, int roleIndex, GuildPermissionKey key) {
        if (roleIndex == 0) return done(ActionResult.failure("role-leader-protected"));
        return requireGuildAndPermission(guildId, requesterId, GuildPermissionKey.ROLE_MANAGEMENT, guild -> {
            Optional<GuildRole> roleOpt = guild.getRole(roleIndex);
            if (roleOpt.isEmpty()) return ActionResult.failure("role-not-found");
            GuildRole role = roleOpt.get();
            if (role.hasPermission(key)) role.removePermission(key);
            else role.addPermission(key);
            return null;
        });
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    @FunctionalInterface
    private interface GuildMutation {
        /** Return non-null ActionResult to short-circuit (failure). Return null to continue to save. */
        ActionResult apply(Guild guild);
    }

    private CompletableFuture<ActionResult> requireGuildAndPermission(UUID guildId, UUID requesterId,
                                                                        GuildPermissionKey key, GuildMutation mutation) {
        return repo.findById(guildId).thenCompose(opt -> {
            if (opt.isEmpty()) return done(ActionResult.failure("invalid-guild"));
            Guild guild = opt.get();
            Optional<GuildMember> memberOpt = guild.getMember(requesterId);
            if (memberOpt.isEmpty()) return done(ActionResult.failure("not-in-guild"));
            if (!evaluator.canAct(memberOpt.get(), key)) return done(ActionResult.failure("no-permission"));
            ActionResult failure = mutation.apply(guild);
            if (failure != null) return done(failure);
            return repo.save(guild).thenApply(v -> ActionResult.success());
        });
    }

    private static <T> CompletableFuture<T> done(T value) {
        return CompletableFuture.completedFuture(value);
    }
}
