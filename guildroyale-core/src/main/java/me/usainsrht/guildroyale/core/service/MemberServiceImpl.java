package me.usainsrht.guildroyale.core.service;

import me.usainsrht.guildroyale.api.domain.Guild;
import me.usainsrht.guildroyale.api.domain.GuildMember;
import me.usainsrht.guildroyale.api.domain.GuildRole;
import me.usainsrht.guildroyale.api.permission.GuildPermissionKey;
import me.usainsrht.guildroyale.api.service.ActionResult;
import me.usainsrht.guildroyale.api.service.MemberService;
import me.usainsrht.guildroyale.api.storage.GuildRepository;
import me.usainsrht.guildroyale.core.config.ConfigManager;
import me.usainsrht.guildroyale.core.event.*;
import me.usainsrht.guildroyale.core.event.GuildMemberJoinEvent;
import me.usainsrht.guildroyale.core.event.GuildMemberKickedEvent;
import me.usainsrht.guildroyale.core.event.GuildMemberLeaveEvent;
import me.usainsrht.guildroyale.core.event.GuildRoleChangedEvent;
import org.bukkit.Bukkit;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Manages guild membership operations.
 *
 * <p>Pending invites are held in-memory with automatic expiry.
 */
public final class MemberServiceImpl implements MemberService {

    /** Key: guildId → Set of invited playerIds */
    private final ConcurrentHashMap<UUID, Set<UUID>> pendingInvites = new ConcurrentHashMap<>();
    private final ScheduledExecutorService expireExecutor =
            Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "GuildRoyale-InviteExpiry"));

    private final GuildRepository repo;
    private final ConfigManager config;
    private final PermissionEvaluatorImpl evaluator = new PermissionEvaluatorImpl();

    public MemberServiceImpl(GuildRepository repo, ConfigManager config) {
        this.repo = repo;
        this.config = config;
    }

    // ── Invite ────────────────────────────────────────────────────────────────

    @Override
    public CompletableFuture<ActionResult> invite(UUID guildId, UUID requesterId, UUID targetPlayerId) {
        return repo.isPlayerInAnyGuild(targetPlayerId).thenCompose(inGuild -> {
            if (inGuild) return done(ActionResult.failure("already-in-guild"));
            return repo.findById(guildId).thenCompose(opt -> {
                if (opt.isEmpty()) return done(ActionResult.failure("invalid-guild"));
                Guild guild = opt.get();
                Optional<GuildMember> requesterOpt = guild.getMember(requesterId);
                if (requesterOpt.isEmpty()) return done(ActionResult.failure("not-in-guild"));
                if (!evaluator.canAct(requesterOpt.get(), GuildPermissionKey.INVITE)) {
                    return done(ActionResult.failure("no-permission"));
                }
                Set<UUID> invites = pendingInvites.computeIfAbsent(guildId, k -> ConcurrentHashMap.newKeySet());
                if (invites.contains(targetPlayerId)) return done(ActionResult.failure("invite-already-sent"));
                invites.add(targetPlayerId);

                long expireSecs = config.getInviteExpireSeconds();
                expireExecutor.schedule(() -> revokeInvite(guildId, targetPlayerId), expireSecs, TimeUnit.SECONDS);

                return done(ActionResult.success());
            });
        });
    }

    // ── Join ──────────────────────────────────────────────────────────────────

    @Override
    public CompletableFuture<ActionResult> join(UUID guildId, UUID playerId) {
        Set<UUID> invites = pendingInvites.get(guildId);
        if (invites == null || !invites.contains(playerId)) {
            return done(ActionResult.failure("no-invite"));
        }

        return repo.isPlayerInAnyGuild(playerId).thenCompose(inGuild -> {
            if (inGuild) return done(ActionResult.failure("already-in-guild"));
            return repo.findById(guildId).thenCompose(opt -> {
                if (opt.isEmpty()) return done(ActionResult.failure("invalid-guild"));
                Guild guild = opt.get();
                GuildRole defaultRole = guild.getDefaultRole();
                GuildMember newMember = new GuildMember(playerId, defaultRole, Instant.now(), 0L);
                guild.addMember(newMember);
                revokeInvite(guildId, playerId);

                return repo.save(guild).thenApply(v -> {
                    GuildMemberJoinEvent event = new GuildMemberJoinEvent(guild, newMember);
                    Bukkit.getPluginManager().callEvent(event);
                    return ActionResult.success();
                });
            });
        });
    }

    // ── Leave ─────────────────────────────────────────────────────────────────

    @Override
    public CompletableFuture<ActionResult> leave(UUID guildId, UUID playerId) {
        return repo.findById(guildId).thenCompose(opt -> {
            if (opt.isEmpty()) return done(ActionResult.failure("invalid-guild"));
            Guild guild = opt.get();
            Optional<GuildMember> memberOpt = guild.getMember(playerId);
            if (memberOpt.isEmpty()) return done(ActionResult.failure("not-in-guild"));
            if (memberOpt.get().getRole().getIndex() == 0) {
                return done(ActionResult.failure("must-be-leader")); // must transfer first
            }
            guild.removeMember(playerId);
            return repo.save(guild).thenApply(v -> {
                GuildMemberLeaveEvent event = new GuildMemberLeaveEvent(guild, memberOpt.get());
                Bukkit.getPluginManager().callEvent(event);
                return ActionResult.success();
            });
        });
    }

    // ── Kick ──────────────────────────────────────────────────────────────────

    @Override
    public CompletableFuture<ActionResult> kick(UUID guildId, UUID requesterId, UUID targetPlayerId) {
        if (requesterId.equals(targetPlayerId)) return done(ActionResult.failure("cannot-kick-self"));
        return repo.findById(guildId).thenCompose(opt -> {
            if (opt.isEmpty()) return done(ActionResult.failure("invalid-guild"));
            Guild guild = opt.get();
            Optional<GuildMember> requesterOpt = guild.getMember(requesterId);
            Optional<GuildMember> targetOpt = guild.getMember(targetPlayerId);
            if (requesterOpt.isEmpty()) return done(ActionResult.failure("not-in-guild"));
            if (targetOpt.isEmpty()) return done(ActionResult.failure("member-not-found"));
            if (!evaluator.canActOn(requesterOpt.get(), targetOpt.get(), GuildPermissionKey.KICK)) {
                return done(ActionResult.failure("target-outranks-you"));
            }
            GuildMember target = targetOpt.get();
            guild.removeMember(targetPlayerId);
            return repo.save(guild).thenApply(v -> {
                GuildMemberKickedEvent event = new GuildMemberKickedEvent(guild, target, requesterId);
                Bukkit.getPluginManager().callEvent(event);
                return ActionResult.success();
            });
        });
    }

    // ── Role change ───────────────────────────────────────────────────────────

    @Override
    public CompletableFuture<ActionResult> changeRole(UUID guildId, UUID requesterId, UUID targetPlayerId, int newRoleIndex) {
        return repo.findById(guildId).thenCompose(opt -> {
            if (opt.isEmpty()) return done(ActionResult.failure("invalid-guild"));
            Guild guild = opt.get();
            Optional<GuildMember> requesterOpt = guild.getMember(requesterId);
            Optional<GuildMember> targetOpt = guild.getMember(targetPlayerId);
            if (requesterOpt.isEmpty()) return done(ActionResult.failure("not-in-guild"));
            if (targetOpt.isEmpty()) return done(ActionResult.failure("member-not-found"));
            if (!evaluator.canActOn(requesterOpt.get(), targetOpt.get(), GuildPermissionKey.ROLE_MANAGEMENT)) {
                return done(ActionResult.failure("target-outranks-you"));
            }
            Optional<GuildRole> newRoleOpt = guild.getRole(newRoleIndex);
            if (newRoleOpt.isEmpty()) return done(ActionResult.failure("role-not-found"));
            if (newRoleIndex == 0) return done(ActionResult.failure("cannot-kick-leader")); // can't assign leader role

            GuildMember target = targetOpt.get();
            GuildRole oldRole = target.getRole();
            target.setRole(newRoleOpt.get());
            return repo.save(guild).thenApply(v -> {
                GuildRoleChangedEvent event = new GuildRoleChangedEvent(guild, target, oldRole, newRoleOpt.get());
                Bukkit.getPluginManager().callEvent(event);
                return ActionResult.success();
            });
        });
    }

    // ── Leader transfer ───────────────────────────────────────────────────────

    @Override
    public CompletableFuture<ActionResult> transferLeader(UUID guildId, UUID currentLeaderId, UUID newLeaderId) {
        return repo.findById(guildId).thenCompose(opt -> {
            if (opt.isEmpty()) return done(ActionResult.failure("invalid-guild"));
            Guild guild = opt.get();
            Optional<GuildMember> leaderOpt = guild.getMember(currentLeaderId);
            Optional<GuildMember> targetOpt = guild.getMember(newLeaderId);
            if (leaderOpt.isEmpty() || leaderOpt.get().getRole().getIndex() != 0) {
                return done(ActionResult.failure("must-be-leader"));
            }
            if (targetOpt.isEmpty()) return done(ActionResult.failure("member-not-found"));

            GuildRole leaderRole = guild.getLeaderRole();
            GuildRole coLeaderRole = guild.getRole(1).orElse(GuildRole.createCoLeader());

            leaderOpt.get().setRole(coLeaderRole);
            targetOpt.get().setRole(leaderRole);

            return repo.save(guild).thenApply(v -> ActionResult.success());
        });
    }

    // ── Invite state ─────────────────────────────────────────────────────────

    @Override
    public boolean hasPendingInvite(UUID guildId, UUID playerId) {
        Set<UUID> invites = pendingInvites.get(guildId);
        return invites != null && invites.contains(playerId);
    }

    @Override
    public void revokeInvite(UUID guildId, UUID playerId) {
        Set<UUID> invites = pendingInvites.get(guildId);
        if (invites != null) invites.remove(playerId);
    }

    public void shutdown() {
        expireExecutor.shutdown();
    }

    private static <T> CompletableFuture<T> done(T value) {
        return CompletableFuture.completedFuture(value);
    }
}
