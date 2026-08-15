package me.usainsrht.guildroyale.core.service;

import me.usainsrht.guildroyale.api.domain.*;
import me.usainsrht.guildroyale.api.permission.GuildPermissionKey;
import me.usainsrht.guildroyale.api.service.ActionResult;
import me.usainsrht.guildroyale.api.service.GuildService;
import me.usainsrht.guildroyale.api.storage.GuildRepository;
import me.usainsrht.guildroyale.core.config.BadgeDefinition;
import me.usainsrht.guildroyale.core.config.ConfigManager;
import me.usainsrht.guildroyale.core.config.ItemRequirement;
import me.usainsrht.guildroyale.core.event.EventDispatcher;
import me.usainsrht.guildroyale.core.event.GuildCreatedEvent;
import me.usainsrht.guildroyale.core.event.GuildDisbandedEvent;
import me.usainsrht.guildroyale.core.event.GuildLevelUpEvent;
import me.usainsrht.guildroyale.core.event.GuildXpGainedEvent;
import me.usainsrht.guildroyale.core.feature.FeatureGate;
import me.usainsrht.guildroyale.core.feature.GuildFeature;
import me.usainsrht.guildroyale.core.scheduler.FoliaScheduler;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

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
    private final FoliaScheduler scheduler;
    private final EventDispatcher events;
    private final FeatureGate featureGate;
    private final PermissionEvaluatorImpl evaluator = new PermissionEvaluatorImpl();

    public GuildServiceImpl(GuildRepository repo, ConfigManager config,
                            EconomyProvider economy, FoliaScheduler scheduler,
                            EventDispatcher events) {
        this.repo = repo;
        this.config = config;
        this.economy = economy;
        this.scheduler = scheduler;
        this.events = events;
        this.featureGate = new FeatureGate(config);
    }

    public FeatureGate featureGate() { return featureGate; }
    public EconomyProvider economy() { return economy; }

    // ── Creation ──────────────────────────────────────────────────────────────

    @Override
    public CompletableFuture<ActionResult> createGuild(UUID ownerPlayerId, String name, String shortname) {
        if (!NAME_PATTERN.matcher(name).matches()) {
            return done(ActionResult.failure("guild-name-invalid"));
        }
        boolean hasShortnameInput = shortname != null && !shortname.isBlank();
        if (hasShortnameInput && !SHORTNAME_PATTERN.matcher(shortname).matches()) {
            return done(ActionResult.failure("guild-shortname-invalid"));
        }

        Player online = Bukkit.getPlayer(ownerPlayerId);
        if (online == null || !online.isOnline()) {
            return done(ActionResult.failure("player-not-found"));
        }

        if (config.isCreationPermissionEnabled()
                && !online.hasPermission(config.getCreationPermissionNode())) {
            return done(ActionResult.failure("guild-creation-no-permission"));
        }

        double cost = config.isCreationMoneyEnabled() ? config.getCreationMoneyCost() : 0;
        if (config.isCreationMoneyEnabled() && !economy.has(ownerPlayerId, cost)) {
            return done(ActionResult.failure("guild-creation-insufficient-funds"));
        }

        return repo.isPlayerInAnyGuild(ownerPlayerId).thenCompose(inGuild -> {
            if (inGuild) return done(ActionResult.failure("already-in-guild"));
            return repo.existsByName(name).thenCompose(nameTaken -> {
                if (nameTaken) return done(ActionResult.failure("guild-name-taken"));
                return repo.findAll().thenCompose(all -> {
                    String finalShortname;
                    if (hasShortnameInput) {
                        boolean shortnameUsed = all.stream()
                                .anyMatch(g -> g.getShortname().equalsIgnoreCase(shortname));
                        if (shortnameUsed) return done(ActionResult.failure("guild-shortname-taken"));
                        finalShortname = shortname;
                    } else {
                        finalShortname = generateDefaultShortname(name, all);
                    }

                    return consumeCreationItems(online).thenCompose(itemsOk -> {
                        if (!itemsOk) return done(ActionResult.failure("guild-creation-missing-items"));

                        if (config.isCreationMoneyEnabled() && !economy.withdraw(ownerPlayerId, cost)) {
                            refundCreationItems(online);
                            return done(ActionResult.failure("guild-creation-insufficient-funds"));
                        }

                        UUID guildId = UUID.randomUUID();
                        Instant now = Instant.now();
                        GuildRole leaderRole = GuildRole.createLeader();
                        GuildRole coLeaderRole = GuildRole.createCoLeader();
                        GuildRole helperRole = GuildRole.createHelper();
                        GuildRole memberRole = GuildRole.createMember();

                        GuildMember owner = new GuildMember(ownerPlayerId, leaderRole, now, 0L);
                        Guild guild = new Guild(guildId, name, finalShortname, SerializableItemStack.EMPTY,
                                GuildLevel.MIN_LEVEL, 0L,
                                List.of(owner),
                                List.of(leaderRole, coLeaderRole, helperRole, memberRole),
                                now);

                        return repo.save(guild).thenApply(v -> {
                            economy.createGuildAccount(guildId, name);
                            events.fire(new GuildCreatedEvent(guild, ownerPlayerId));
                            return ActionResult.success();
                        });
                    });
                });
            });
        });
    }

    private String generateDefaultShortname(String name, List<Guild> allGuilds) {
        String clean = name.replaceAll("[^a-zA-Z0-9]", "");
        String base;
        if (clean.length() < 2) {
            base = "G" + clean;
            while (base.length() < 2) base += "X";
        } else if (clean.length() > 6) {
            base = clean.substring(0, 6);
        } else {
            base = clean;
        }

        Set<String> existing = new HashSet<>();
        for (Guild g : allGuilds) {
            existing.add(g.getShortname().toLowerCase(Locale.ROOT));
        }

        if (!existing.contains(base.toLowerCase(Locale.ROOT)) && SHORTNAME_PATTERN.matcher(base).matches()) {
            return base;
        }

        for (int i = 1; i <= 9999; i++) {
            String suffix = String.valueOf(i);
            int maxBaseLen = 6 - suffix.length();
            String prefix = base.length() > maxBaseLen ? base.substring(0, maxBaseLen) : base;
            String candidate = prefix + suffix;
            if (!existing.contains(candidate.toLowerCase(Locale.ROOT)) && SHORTNAME_PATTERN.matcher(candidate).matches()) {
                return candidate;
            }
        }
        return UUID.randomUUID().toString().replaceAll("[^a-zA-Z0-9]", "").substring(0, 6);
    }

    private CompletableFuture<Boolean> consumeCreationItems(Player player) {
        if (!config.isCreationItemsEnabled()) {
            return done(true);
        }
        List<ItemRequirement> requirements = config.getCreationItemRequirements();
        if (requirements.isEmpty()) return done(true);

        CompletableFuture<Boolean> future = new CompletableFuture<>();
        scheduler.runForEntity(player, () -> {
            if (!hasItems(player, requirements)) {
                future.complete(false);
                return;
            }
            removeItems(player, requirements);
            future.complete(true);
        });
        return future;
    }

    private void refundCreationItems(Player player) {
        if (!config.isCreationItemsEnabled()) return;
        List<ItemRequirement> requirements = config.getCreationItemRequirements();
        if (requirements.isEmpty() || !player.isOnline()) return;
        scheduler.runForEntity(player, () -> giveItems(player, requirements));
    }

    private static boolean hasItems(Player player, List<ItemRequirement> requirements) {
        PlayerInventory inv = player.getInventory();
        for (ItemRequirement req : requirements) {
            Material mat = Material.matchMaterial(req.material());
            if (mat == null) return false;
            if (!inv.containsAtLeast(new ItemStack(mat), req.amount())) return false;
        }
        return true;
    }

    private static void removeItems(Player player, List<ItemRequirement> requirements) {
        PlayerInventory inv = player.getInventory();
        for (ItemRequirement req : requirements) {
            Material mat = Material.matchMaterial(req.material());
            if (mat == null) continue;
            inv.removeItem(new ItemStack(mat, req.amount()));
        }
    }

    private static void giveItems(Player player, List<ItemRequirement> requirements) {
        for (ItemRequirement req : requirements) {
            Material mat = Material.matchMaterial(req.material());
            if (mat == null) continue;
            player.getInventory().addItem(new ItemStack(mat, req.amount()));
        }
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
                events.fire(new GuildDisbandedEvent(guild, requesterId));
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

            events.fire(new GuildXpGainedEvent(guild, amount));

            int levelCap = config.getLevelCap();
            int levelsGained = 0;
            while (!new GuildLevel(guild.getLevel()).isMaxLevel(levelCap)) {
                long required = xpRequiredForLevel(guild.getLevel() + 1);
                if (guild.getXp() < required) break;
                guild.setXp(guild.getXp() - required);
                guild.setLevel(guild.getLevel() + 1);
                levelsGained++;
                events.fire(new GuildLevelUpEvent(guild, guild.getLevel() - 1, guild.getLevel()));
            }

            if (levelsGained > 0) {
                me.usainsrht.guildroyale.core.GuildRoyalePlugin mainPlugin = me.usainsrht.guildroyale.core.GuildRoyalePlugin.getInstance();
                if (mainPlugin != null && mainPlugin.getGuildStorageManager() != null) {
                    mainPlugin.getScheduler().runOnMainThread(
                            () -> mainPlugin.getGuildStorageManager().refreshGuildStorage(guild)
                    );
                }
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
        return repo.findById(guildId).thenCompose(opt -> {
            if (opt.isEmpty()) return done(ActionResult.failure("invalid-guild"));
            Guild guild = opt.get();
            if (!featureGate.isUnlocked(guild, GuildFeature.SHORTNAME)) {
                return done(ActionResult.failure("feature-locked"));
            }
            double cost = config.getShortnameChangeCost();
            if (!economy.has(requesterId, cost)) {
                return done(ActionResult.failure("shortname-insufficient-funds"));
            }
            return repo.findAll().thenCompose(all -> {
                boolean taken = all.stream().anyMatch(g -> !g.getId().equals(guildId)
                        && g.getShortname().equalsIgnoreCase(shortname));
                if (taken) return done(ActionResult.failure("guild-shortname-taken"));
                return mutateGuild(guildId, requesterId, GuildPermissionKey.SHORTNAME_CHANGE, g -> {
                    economy.withdraw(requesterId, cost);
                    g.setShortname(shortname);
                });
            });
        });
    }

    @Override
    public CompletableFuture<ActionResult> setName(UUID guildId, UUID requesterId, String name) {
        if (!NAME_PATTERN.matcher(name).matches()) {
            return done(ActionResult.failure("guild-name-invalid"));
        }
        // A guild keeping its own name (or only changing its casing) is not a clash.
        return repo.findByName(name).thenCompose(existing -> {
            if (existing.isPresent() && !existing.get().getId().equals(guildId)) {
                return done(ActionResult.failure("guild-name-taken"));
            }
            return mutateGuild(guildId, requesterId, GuildPermissionKey.GUILD_SETTINGS, guild -> {
                guild.setName(name);
            });
        });
    }

    @Override
    public CompletableFuture<ActionResult> setFriendlyFire(UUID guildId, UUID requesterId, boolean enabled) {
        return mutateGuild(guildId, requesterId, GuildPermissionKey.GUILD_SETTINGS, guild -> {
            guild.setFriendlyFire(enabled);
        });
    }

    @Override
    public CompletableFuture<ActionResult> toggleFriendlyFire(UUID guildId, UUID requesterId) {
        return repo.findById(guildId).thenCompose(opt -> {
            if (opt.isEmpty()) return done(ActionResult.failure("invalid-guild"));
            return setFriendlyFire(guildId, requesterId, !opt.get().isFriendlyFire());
        });
    }

    @Override
    public boolean canHit(UUID player1, UUID player2) {
        if (player1 == null || player2 == null) return true;
        if (player1.equals(player2)) return true;

        Optional<Guild> g1Opt = repo.findByMember(player1).join();
        if (g1Opt.isEmpty()) return true;

        Optional<Guild> g2Opt = repo.findByMember(player2).join();
        if (g2Opt.isEmpty()) return true;

        Guild g1 = g1Opt.get();
        Guild g2 = g2Opt.get();

        if (!g1.getId().equals(g2.getId())) return true;

        return g1.isFriendlyFire();
    }

    public boolean canHit(Player player1, Player player2) {
        if (player1 == null || player2 == null) return true;
        return canHit(player1.getUniqueId(), player2.getUniqueId());
    }

    // ── Badges ────────────────────────────────────────────────────────────────

    @Override
    public CompletableFuture<ActionResult> buyBadge(UUID guildId, UUID requesterId, String badgeId) {
        Optional<BadgeDefinition> badgeOpt = config.getBadge(badgeId);
        if (badgeOpt.isEmpty()) return done(ActionResult.failure("badge-not-found"));
        BadgeDefinition badge = badgeOpt.get();
        if (!badge.isBuyable()) return done(ActionResult.failure("badge-not-buyable"));

        return repo.findById(guildId).thenCompose(opt -> {
            if (opt.isEmpty()) return done(ActionResult.failure("invalid-guild"));
            Guild guild = opt.get();
            if (!featureGate.isUnlocked(guild, GuildFeature.BADGE)) {
                return done(ActionResult.failure("feature-locked"));
            }
            if (guild.ownsBadge(badgeId)) return done(ActionResult.failure("badge-already-owned"));
            if (!economy.has(requesterId, badge.cost())) {
                return done(ActionResult.failure("badge-insufficient-funds"));
            }
            return mutateGuild(guildId, requesterId, GuildPermissionKey.BADGE_MANAGE, g -> {
                if (!economy.withdraw(requesterId, badge.cost())) {
                    throw new IllegalStateException("badge-insufficient-funds");
                }
                g.addBadge(badgeId);
            });
        });
    }

    @Override
    public CompletableFuture<ActionResult> equipBadge(UUID guildId, UUID requesterId, String badgeId) {
        return repo.findById(guildId).thenCompose(opt -> {
            if (opt.isEmpty()) return done(ActionResult.failure("invalid-guild"));
            Guild guild = opt.get();
            if (!featureGate.isUnlocked(guild, GuildFeature.BADGE)) {
                return done(ActionResult.failure("feature-locked"));
            }
            if (!guild.ownsBadge(badgeId)) return done(ActionResult.failure("badge-not-owned"));
            return mutateGuild(guildId, requesterId, GuildPermissionKey.BADGE_MANAGE, g -> {
                g.setActiveBadgeId(badgeId);
            });
        });
    }

    @Override
    public CompletableFuture<ActionResult> adminGrantBadge(UUID guildId, String badgeId) {
        Optional<BadgeDefinition> badgeOpt = config.getBadge(badgeId);
        if (badgeOpt.isEmpty()) return done(ActionResult.failure("badge-not-found"));
        if (!badgeOpt.get().grantable()) return done(ActionResult.failure("badge-not-grantable"));

        return repo.findById(guildId).thenCompose(opt -> {
            if (opt.isEmpty()) return done(ActionResult.failure("invalid-guild"));
            Guild guild = opt.get();
            if (guild.ownsBadge(badgeId)) return done(ActionResult.failure("badge-already-owned"));
            guild.addBadge(badgeId);
            return repo.save(guild).thenApply(v -> ActionResult.success());
        });
    }

    @Override
    public CompletableFuture<ActionResult> adminRevokeBadge(UUID guildId, String badgeId) {
        return repo.findById(guildId).thenCompose(opt -> {
            if (opt.isEmpty()) return done(ActionResult.failure("invalid-guild"));
            Guild guild = opt.get();
            if (!guild.removeBadge(badgeId)) return done(ActionResult.failure("badge-not-owned"));
            return repo.save(guild).thenApply(v -> ActionResult.success());
        });
    }

    // ── Storage ───────────────────────────────────────────────────────────────

    @Override
    public CompletableFuture<ActionResult> saveStorage(UUID guildId, UUID requesterId,
                                                       Map<Integer, SerializableItemStack> contents) {
        return repo.findById(guildId).thenCompose(opt -> {
            if (opt.isEmpty()) return done(ActionResult.failure("invalid-guild"));
            Guild guild = opt.get();
            if (!featureGate.isUnlocked(guild, GuildFeature.STORAGE)) {
                return done(ActionResult.failure("feature-locked"));
            }
            return mutateGuild(guildId, requesterId, GuildPermissionKey.STORAGE_ACCESS, g -> {
                int maxSlots = config.getStorageSlotsForLevel(g.getLevel());
                Map<Integer, SerializableItemStack> clipped = new TreeMap<>();
                contents.forEach((slot, item) -> {
                    if (slot >= 0 && slot < maxSlots && item != null && !item.isEmpty()) {
                        clipped.put(slot, item);
                    }
                });
                g.setStorageContents(clipped);
            });
        });
    }

    // ── Bank ──────────────────────────────────────────────────────────────────

    @Override
    public CompletableFuture<ActionResult> bankDeposit(UUID guildId, UUID requesterId, double amount) {
        if (amount <= 0) return done(ActionResult.failure("bank-invalid-amount"));
        return repo.findById(guildId).thenCompose(opt -> {
            if (opt.isEmpty()) return done(ActionResult.failure("invalid-guild"));
            Guild guild = opt.get();
            if (!featureGate.isUnlocked(guild, GuildFeature.BANK)) {
                return done(ActionResult.failure("feature-locked"));
            }
            Optional<GuildMember> memberOpt = guild.getMember(requesterId);
            if (memberOpt.isEmpty()) return done(ActionResult.failure("not-in-guild"));
            if (!evaluator.canAct(memberOpt.get(), GuildPermissionKey.BANK_DEPOSIT)) {
                return done(ActionResult.failure("no-permission"));
            }
            if (!economy.has(requesterId, amount)) {
                return done(ActionResult.failure("bank-insufficient-funds"));
            }
            if (!economy.withdraw(requesterId, amount)) {
                return done(ActionResult.failure("bank-insufficient-funds"));
            }
            if (!economy.deposit(guildId, amount)) {
                economy.deposit(requesterId, amount);
                return done(ActionResult.failure("bank-transfer-failed"));
            }
            return done(ActionResult.success());
        });
    }

    @Override
    public CompletableFuture<ActionResult> bankWithdraw(UUID guildId, UUID requesterId, double amount) {
        if (amount <= 0) return done(ActionResult.failure("bank-invalid-amount"));
        return repo.findById(guildId).thenCompose(opt -> {
            if (opt.isEmpty()) return done(ActionResult.failure("invalid-guild"));
            Guild guild = opt.get();
            if (!featureGate.isUnlocked(guild, GuildFeature.BANK)) {
                return done(ActionResult.failure("feature-locked"));
            }
            Optional<GuildMember> memberOpt = guild.getMember(requesterId);
            if (memberOpt.isEmpty()) return done(ActionResult.failure("not-in-guild"));
            if (!evaluator.canAct(memberOpt.get(), GuildPermissionKey.BANK_WITHDRAW)) {
                return done(ActionResult.failure("no-permission"));
            }
            if (!economy.has(guildId, amount)) {
                return done(ActionResult.failure("bank-insufficient-guild-funds"));
            }
            if (!economy.withdraw(guildId, amount)) {
                return done(ActionResult.failure("bank-insufficient-guild-funds"));
            }
            if (!economy.deposit(requesterId, amount)) {
                economy.deposit(guildId, amount);
                return done(ActionResult.failure("bank-transfer-failed"));
            }
            return done(ActionResult.success());
        });
    }

    // ── Admin ─────────────────────────────────────────────────────────────────

    @Override
    public CompletableFuture<Void> adminSetLevel(UUID guildId, int level) {
        int cap = config.getLevelCap();
        final int clamped;
        if (level < GuildLevel.MIN_LEVEL) {
            clamped = GuildLevel.MIN_LEVEL;
        } else if (cap > 0 && level > cap) {
            clamped = cap;
        } else {
            clamped = level;
        }
        return repo.findById(guildId).thenCompose(opt -> {
            if (opt.isEmpty()) return CompletableFuture.completedFuture(null);
            Guild guild = opt.get();
            guild.setLevel(clamped);
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
            try {
                mutator.mutate(guild);
            } catch (IllegalStateException ex) {
                return done(ActionResult.failure(ex.getMessage()));
            }
            return repo.save(guild).thenApply(v -> ActionResult.success());
        });
    }

    private static <T> CompletableFuture<T> done(T value) {
        return CompletableFuture.completedFuture(value);
    }
}
