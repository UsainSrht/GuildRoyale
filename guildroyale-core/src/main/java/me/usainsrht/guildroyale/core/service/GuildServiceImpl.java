package me.usainsrht.guildroyale.core.service;

import me.usainsrht.guildroyale.api.domain.*;
import me.usainsrht.guildroyale.api.service.ActionResult;
import me.usainsrht.guildroyale.api.service.GuildService;
import me.usainsrht.guildroyale.api.storage.GuildRepository;
import me.usainsrht.guildroyale.api.permission.GuildPermissionKey;
import me.usainsrht.guildroyale.core.config.ConfigManager;
import dev.guildroyale.core.event.*;
import me.usainsrht.guildroyale.core.event.GuildCreatedEvent;
import me.usainsrht.guildroyale.core.event.GuildDisbandedEvent;
import me.usainsrht.guildroyale.core.event.GuildLevelUpEvent;
import me.usainsrht.guildroyale.core.event.GuildXpGainedEvent;
import org.bukkit.Bukkit;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * Production implementation of {@link GuildService}.
 */
public final class GuildServiceImpl implements GuildService {

    private static final Pattern NAME_PATTERN = Pattern.compile("^[\\w\\s\\-]{3,32}$");
    private static final Pattern SHORTNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9]{2,6}$");

    private final GuildRepository repo;
    private final ConfigManager config;
    private final EconomyProvider economy;
    private final PermissionEvaluatorImpl evaluator = new PermissionEvaluatorImpl();

    public GuildServiceImpl(GuildRepository repo, ConfigManager config, EconomyProvider economy) {
        this.repo = repo;
        this.config = config;
        this.economy = economy;
    }

    // ── Creation ──────────────────────────────────────────────────────────────

    @Override
    public CompletableFuture<ActionResult> createGuild(UUID ownerPlayerId, String name, String shortname) {
        if (!NAME_PATTERN.matcher(name).matches()) {
            return done(ActionResult.failure("guild-name-invalid"));
        }
        if (!SHORTNAME_PATTERN.matcher(shortname).matches()) {
            return done(ActionResult.failure("guild-shortname-invalid"));
        }

        double cost = config.getCreationMoneyCost();
        if (!economy.has(ownerPlayerId, cost)) {
            return done(ActionResult.failure("guild-creation-insufficient-funds"));
        }

        return repo.isPlayerInAnyGuild(ownerPlayerId).thenCompose(inGuild -> {
            if (inGuild) return done(ActionResult.failure("already-in-guild"));
            return repo.existsByName(name).thenCompose(nameTaken -> {
                if (nameTaken) return done(ActionResult.failure("guild-name-taken"));
                return repo.findByName(shortname).thenCompose(shortTaken -> {
                    // Check shortname taken by finding any guild with that shortname
                    return repo.findAll().thenCompose(all -> {
                        boolean shortnameUsed = all.stream().anyMatch(g -> g.getShortname().equalsIgnoreCase(shortname));
                        if (shortnameUsed) return done(ActionResult.failure("guild-shortname-taken"));

                        if (!economy.withdraw(ownerPlayerId, cost)) {
                            return done(ActionResult.failure("guild-creation-insufficient-funds"));
                        }

                        UUID guildId = UUID.randomUUID();
                        Instant now = Instant.now();
                        GuildRole leaderRole = GuildRole.createLeader();
                        GuildRole coLeaderRole = GuildRole.createCoLeader();
                        GuildRole helperRole = GuildRole.createHelper();
                        GuildRole memberRole = GuildRole.createMember();

                        GuildMember owner = new GuildMember(ownerPlayerId, leaderRole, now, 0L);
                        Guild guild = new Guild(guildId, name, shortname, SerializableItemStack.EMPTY,
                                GuildLevel.MIN_LEVEL, 0L,
                                List.of(owner),
                                List.of(leaderRole, coLeaderRole, helperRole, memberRole),
                                now);

                        return repo.save(guild).thenApply(v -> {
                            GuildCreatedEvent event = new GuildCreatedEvent(guild, ownerPlayerId);
                            Bukkit.getPluginManager().callEvent(event);
                            return ActionResult.success();
                        });
                    });
                });
            });
        });
    }

    // ── Disband ───────────────────────────────────────────────────────────────

    @Override
    public CompletableFuture<ActionResult> disbandGuild(UUID guildId, UUID requesterId) {
        return repo.findById(guildId).thenCompose(opt -> {
            if (opt.isEmpty()) return done(ActionResult.failure("invalid-guild"));
            Guild guild = opt.get();
            Optional<GuildMember> memberOpt = guild.getMember(requesterId);
            if (memberOpt.isEmpty()) return done(ActionResult.failure("not-in-guild"));
            if (!evaluator.canAct(memberOpt.get(), GuildPermissionKey.DISBANDMENT)) {
                return done(ActionResult.failure("no-permission"));
            }
            return repo.delete(guildId).thenApply(v -> {
                GuildDisbandedEvent event = new GuildDisbandedEvent(guild, requesterId);
                Bukkit.getPluginManager().callEvent(event);
                return ActionResult.success();
            });
        });
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    @Override
    public CompletableFuture<Optional<Guild>> getGuild(UUID guildId) {
        return repo.findById(guildId);
    }

    @Override
    public CompletableFuture<Optional<Guild>> getGuildByMember(UUID playerId) {
        return repo.findByMember(playerId);
    }

    @Override
    public CompletableFuture<Optional<Guild>> getGuildByName(String name) {
        return repo.findByName(name);
    }

    // ── XP / Level ────────────────────────────────────────────────────────────

    @Override
    public CompletableFuture<Integer> addXp(UUID guildId, long amount) {
        return repo.findById(guildId).thenCompose(opt -> {
            if (opt.isEmpty()) return CompletableFuture.completedFuture(0);
            Guild guild = opt.get();
            guild.addXp(amount);

            GuildXpGainedEvent xpEvent = new GuildXpGainedEvent(guild, amount);
            Bukkit.getPluginManager().callEvent(xpEvent);

            int levelsGained = 0;
            while (!new GuildLevel(guild.getLevel()).isMaxLevel()) {
                long required = xpRequiredForLevel(guild.getLevel() + 1);
                if (guild.getXp() < required) break;
                guild.setXp(guild.getXp() - required);
                guild.setLevel(guild.getLevel() + 1);
                levelsGained++;
                GuildLevelUpEvent levelEvent = new GuildLevelUpEvent(guild, guild.getLevel() - 1, guild.getLevel());
                Bukkit.getPluginManager().callEvent(levelEvent);
            }

            final int gained = levelsGained;
            return repo.save(guild).thenApply(v -> gained);
        });
    }

    @Override
    public long xpRequiredForLevel(int level) {
        long base = config.getXpBase();
        double mult = config.getXpMultiplier();
        return (long) (base * Math.pow(mult, level - 1));
    }

    // ── Settings ──────────────────────────────────────────────────────────────

    @Override
    public CompletableFuture<ActionResult> setIcon(UUID guildId, UUID requesterId, SerializableItemStack icon) {
        return mutateGuild(guildId, requesterId, GuildPermissionKey.ICON_CHANGE, guild -> {
            guild.setIcon(icon);
        });
    }

    @Override
    public CompletableFuture<ActionResult> setShortname(UUID guildId, UUID requesterId, String shortname) {
        if (!SHORTNAME_PATTERN.matcher(shortname).matches()) {
            return done(ActionResult.failure("guild-shortname-invalid"));
        }
        double cost = config.getShortnameChangeCost();
        if (!economy.has(requesterId, cost)) {
            return done(ActionResult.failure("shortname-insufficient-funds"));
        }
        return repo.findAll().thenCompose(all -> {
            boolean taken = all.stream().anyMatch(g -> !g.getId().equals(guildId)
                    && g.getShortname().equalsIgnoreCase(shortname));
            if (taken) return done(ActionResult.failure("guild-shortname-taken"));
            return mutateGuild(guildId, requesterId, GuildPermissionKey.SHORTNAME_CHANGE, guild -> {
                economy.withdraw(requesterId, cost);
                guild.setShortname(shortname);
            });
        });
    }

    @Override
    public CompletableFuture<ActionResult> setName(UUID guildId, UUID requesterId, String name) {
        if (!NAME_PATTERN.matcher(name).matches()) {
            return done(ActionResult.failure("guild-name-invalid"));
        }
        return repo.existsByName(name).thenCompose(taken -> {
            if (taken) return done(ActionResult.failure("guild-name-taken"));
            return mutateGuild(guildId, requesterId, GuildPermissionKey.GUILD_SETTINGS, guild -> {
                guild.setName(name);
            });
        });
    }

    // ── Admin ─────────────────────────────────────────────────────────────────

    @Override
    public CompletableFuture<Void> adminSetLevel(UUID guildId, int level) {
        return repo.findById(guildId).thenCompose(opt -> {
            if (opt.isEmpty()) return CompletableFuture.completedFuture(null);
            Guild guild = opt.get();
            guild.setLevel(level);
            guild.setXp(0);
            return repo.save(guild);
        });
    }

    @Override
    public CompletableFuture<Void> adminAddXp(UUID guildId, long amount) {
        return repo.findById(guildId).thenCompose(opt -> {
            if (opt.isEmpty()) return CompletableFuture.completedFuture(null);
            Guild guild = opt.get();
            guild.addXp(amount);
            return repo.save(guild);
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    @FunctionalInterface
    private interface GuildMutator {
        void mutate(Guild guild);
    }

    private CompletableFuture<ActionResult> mutateGuild(UUID guildId, UUID requesterId,
                                                         GuildPermissionKey key, GuildMutator mutator) {
        return repo.findById(guildId).thenCompose(opt -> {
            if (opt.isEmpty()) return done(ActionResult.failure("invalid-guild"));
            Guild guild = opt.get();
            Optional<GuildMember> memberOpt = guild.getMember(requesterId);
            if (memberOpt.isEmpty()) return done(ActionResult.failure("not-in-guild"));
            if (!evaluator.canAct(memberOpt.get(), key)) {
                return done(ActionResult.failure("no-permission"));
            }
            mutator.mutate(guild);
            return repo.save(guild).thenApply(v -> ActionResult.success());
        });
    }

    private static <T> CompletableFuture<T> done(T value) {
        return CompletableFuture.completedFuture(value);
    }
}
